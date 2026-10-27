<?php
// Tokkip Notas — sincronización en tu propio hosting.
//
// Un solo archivo para subir a tu hosting (Hostinger, cPanel… cualquiera con
// PHP 8.1 y MySQL). La primera vez que lo abres en el navegador te pide los
// datos de la base de datos, crea sus tablas y te da la clave de sincronización
// (se muestra una sola vez). Desde ahí, Tokkip Notas en el teléfono, la tablet
// y la PC lo usan para mandar y recibir lo que cambió.
//
// El servidor nunca ve tus notas: cada página llega cifrada (AES-256-GCM, con
// una llave que sale de tu frase y que nunca sale de tus aparatos). Aquí solo
// se guardan el id de cada página, su versión (cuándo cambió), si se borró, y
// el texto cifrado.
//
// Protocolo (POST, JSON, cabecera X-Tokkip-Key con la clave):
//   {"action":"hello"}                     → {ok, salt, check, rev}
//   {"action":"check","check":"…"}          → guarda la prueba de la frase (solo la primera vez)
//   {"action":"sync","since":N,"push":[…]}  → {ok, accepted, rows, rev, more}
// Cada página empujada: {id, ver, deleted, data}. Se acepta si su ver es mayor
// que la guardada (gana el cambio más reciente); las que no, regresan en rows.

declare(strict_types=1);

const TN_VERSION = 1;
const TN_PULL_LIMIT = 300;
const TN_PUSH_LIMIT = 500;
const TN_MAX_DATA = 8 * 1024 * 1024;

$configFile = __DIR__ . '/sync-config.php';

if (!is_file($configFile)) {
    setup($configFile);
    exit;
}
require $configFile;

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    page('Tokkip Notas', '<p>La sincronización está lista. Pon esta dirección en Tokkip Notas → Ajustes → Sincronizar:</p><p class="mono">' . htmlspecialchars(selfUrl()) . '</p>');
    exit;
}

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

$key = $_SERVER['HTTP_X_TOKKIP_KEY'] ?? '';
if (!is_string($key) || $key === '' || !hash_equals(TN_KEY_HASH, hash('sha256', $key))) {
    usleep(300000); // a wrong key waits a little
    fail(401, 'La clave de sincronización no es correcta');
}

$raw = file_get_contents('php://input');
$req = json_decode($raw === false ? '' : $raw, true);
if (!is_array($req)) fail(400, 'Petición inválida');

try {
    $db = connect();
    switch ($req['action'] ?? '') {
        case 'hello':
            out([
                'ok' => true,
                'version' => TN_VERSION,
                'salt' => meta($db, 'salt'),
                'check' => meta($db, 'check'),
                'rev' => (int) (meta($db, 'rev') ?? 0),
            ]);
        case 'check':
            $check = $req['check'] ?? '';
            if (!is_string($check) || $check === '' || strlen($check) > 1024) fail(400, 'Prueba inválida');
            if (meta($db, 'check') === null) setMeta($db, 'check', $check);
            out(['ok' => true, 'check' => meta($db, 'check')]);
        case 'sync':
            out(sync($db, $req));
        default:
            fail(400, 'Acción desconocida');
    }
} catch (Throwable $e) {
    error_log('[tokkip-notas] ' . $e->getMessage());
    fail(500, 'Error del servidor');
}

// ── Sync ──────────────────────────────────────────────────────────────────────

function sync(PDO $db, array $req): array
{
    $since = (int) ($req['since'] ?? 0);
    $push = $req['push'] ?? [];
    if (!is_array($push) || count($push) > TN_PUSH_LIMIT) fail(400, 'Demasiados cambios en una sola vez');

    $accepted = [];
    $rejected = [];
    if ($push) {
        $db->beginTransaction();
        try {
            $find = $db->prepare('SELECT ver FROM tn_pages WHERE id = ?');
            $insert = $db->prepare('INSERT INTO tn_pages (id, ver, deleted, data, rev) VALUES (?, ?, ?, ?, ?)');
            $update = $db->prepare('UPDATE tn_pages SET ver = ?, deleted = ?, data = ?, rev = ? WHERE id = ?');
            foreach ($push as $p) {
                if (!is_array($p)) continue;
                $id = $p['id'] ?? '';
                $ver = $p['ver'] ?? null;
                $deleted = !empty($p['deleted']) ? 1 : 0;
                $data = $deleted ? '' : ($p['data'] ?? '');
                if (!is_string($id) || !preg_match('/^[A-Za-z0-9_-]{1,64}$/', $id)) continue;
                if (!is_int($ver) && !(is_float($ver) && floor($ver) === $ver)) continue;
                $ver = (int) $ver;
                if (!is_string($data) || strlen($data) > TN_MAX_DATA) continue;
                $find->execute([$id]);
                $stored = $find->fetchColumn();
                $find->closeCursor();
                if ($stored !== false && (int) $stored >= $ver) {
                    $rejected[] = $id;
                    continue;
                }
                $rev = nextRev($db);
                if ($stored === false) $insert->execute([$id, $ver, $deleted, $data, $rev]);
                else $update->execute([$ver, $deleted, $data, $rev, $id]);
                $accepted[] = $id;
            }
            $db->commit();
        } catch (Throwable $e) {
            $db->rollBack();
            throw $e;
        }
    }

    // What changed since the client's last pull, a page at a time.
    $q = $db->prepare('SELECT id, ver, deleted, data, rev FROM tn_pages WHERE rev > ? ORDER BY rev LIMIT ' . (TN_PULL_LIMIT + 1));
    $q->execute([$since]);
    $rows = $q->fetchAll(PDO::FETCH_ASSOC);
    $more = count($rows) > TN_PULL_LIMIT;
    if ($more) array_pop($rows);
    $rev = $rows ? (int) end($rows)['rev'] : $since;

    // The pages that lost (someone changed them later) come back as they are now.
    $seen = array_flip(array_column($rows, 'id'));
    $back = array_values(array_filter($rejected, fn ($id) => !isset($seen[$id])));
    if ($back) {
        $in = implode(',', array_fill(0, count($back), '?'));
        $q = $db->prepare("SELECT id, ver, deleted, data, rev FROM tn_pages WHERE id IN ($in)");
        $q->execute($back);
        $rows = array_merge($rows, $q->fetchAll(PDO::FETCH_ASSOC));
    }

    return [
        'ok' => true,
        'accepted' => $accepted,
        'rows' => array_map(fn ($r) => [
            'id' => $r['id'],
            'ver' => (int) $r['ver'],
            'deleted' => (bool) (int) $r['deleted'],
            'data' => $r['data'],
            'rev' => (int) $r['rev'],
        ], $rows),
        'rev' => $rev,
        'more' => $more,
    ];
}

function nextRev(PDO $db): int
{
    $sqlite = $db->getAttribute(PDO::ATTR_DRIVER_NAME) === 'sqlite';
    $row = $db->query("SELECT v FROM tn_meta WHERE k = 'rev'" . ($sqlite ? '' : ' FOR UPDATE'))->fetchColumn();
    $rev = (int) ($row === false ? 0 : $row) + 1;
    if ($row === false) $db->prepare("INSERT INTO tn_meta (k, v) VALUES ('rev', ?)")->execute([(string) $rev]);
    else $db->prepare("UPDATE tn_meta SET v = ? WHERE k = 'rev'")->execute([(string) $rev]);
    return $rev;
}

// ── Database ──────────────────────────────────────────────────────────────────

function connect(): PDO
{
    $db = new PDO(TN_DSN, TN_USER, TN_PASS, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
    if ($db->getAttribute(PDO::ATTR_DRIVER_NAME) === 'sqlite') $db->exec('PRAGMA busy_timeout = 5000');
    return $db;
}

function createTables(PDO $db): void
{
    $mysql = $db->getAttribute(PDO::ATTR_DRIVER_NAME) === 'mysql';
    $tail = $mysql ? ' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4' : '';
    $db->exec('CREATE TABLE IF NOT EXISTS tn_pages (
        id VARCHAR(64) NOT NULL PRIMARY KEY,
        ver BIGINT NOT NULL,
        deleted TINYINT NOT NULL DEFAULT 0,
        data ' . ($mysql ? 'MEDIUMTEXT' : 'TEXT') . ' NOT NULL,
        rev BIGINT NOT NULL
    )' . $tail);
    try {
        $db->exec('CREATE INDEX tn_pages_rev ON tn_pages (rev)');
    } catch (PDOException $e) {
        // already there
    }
    $db->exec('CREATE TABLE IF NOT EXISTS tn_meta (k VARCHAR(32) NOT NULL PRIMARY KEY, v TEXT)' . $tail);
}

function meta(PDO $db, string $k): ?string
{
    $q = $db->prepare('SELECT v FROM tn_meta WHERE k = ?');
    $q->execute([$k]);
    $v = $q->fetchColumn();
    return $v === false || $v === null ? null : (string) $v;
}

function setMeta(PDO $db, string $k, string $v): void
{
    if (meta($db, $k) === null) $db->prepare('INSERT INTO tn_meta (k, v) VALUES (?, ?)')->execute([$k, $v]);
    else $db->prepare('UPDATE tn_meta SET v = ? WHERE k = ?')->execute([$v, $k]);
}

// ── Output ────────────────────────────────────────────────────────────────────

function out(array $data): never
{
    echo json_encode($data, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function fail(int $code, string $message): never
{
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['ok' => false, 'error' => $message], JSON_UNESCAPED_UNICODE);
    exit;
}

function selfUrl(): string
{
    $https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https';
    $host = $_SERVER['HTTP_HOST'] ?? 'tu-dominio';
    $path = strtok($_SERVER['REQUEST_URI'] ?? '/sync.php', '?');
    return ($https ? 'https' : 'http') . '://' . $host . $path;
}

// ── First run: connect the database and make the key ──────────────────────────

function setup(string $configFile): void
{
    $error = '';
    if ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $host = trim((string) ($_POST['host'] ?? 'localhost')) ?: 'localhost';
        $name = trim((string) ($_POST['name'] ?? ''));
        $user = trim((string) ($_POST['user'] ?? ''));
        $pass = (string) ($_POST['pass'] ?? '');
        $sqlite = ($_POST['driver'] ?? '') === 'sqlite' && getenv('TOKKIP_SYNC_TEST') === '1';
        $dsn = $sqlite ? 'sqlite:' . __DIR__ . '/' . basename($name ?: 'notas-sync.db') : 'mysql:host=' . $host . ';dbname=' . $name . ';charset=utf8mb4';
        try {
            if (!$sqlite && ($name === '' || $user === '')) throw new RuntimeException('Falta el nombre de la base de datos o el usuario');
            $db = new PDO($dsn, $sqlite ? null : $user, $sqlite ? null : $pass, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
            createTables($db);
            if (meta($db, 'salt') === null) setMeta($db, 'salt', base64_encode(random_bytes(16)));
            $key = rtrim(strtr(base64_encode(random_bytes(24)), '+/', '-_'), '=');
            $config = "<?php\n// Tokkip Notas: datos de la base de datos y la huella de la clave. No lo compartas.\n"
                . 'const TN_DSN = ' . var_export($dsn, true) . ";\n"
                . 'const TN_USER = ' . var_export($sqlite ? null : $user, true) . ";\n"
                . 'const TN_PASS = ' . var_export($sqlite ? null : $pass, true) . ";\n"
                . 'const TN_KEY_HASH = ' . var_export(hash('sha256', $key), true) . ";\n";
            if (@file_put_contents($configFile, $config, LOCK_EX) === false) throw new RuntimeException('No pude escribir sync-config.php en esta carpeta');
            @chmod($configFile, 0600);
            // Keep the config, the database file and dotfiles away from the web.
            @file_put_contents(__DIR__ . '/.htaccess', "<FilesMatch \"^(sync-config\\.php|.*\\.db)$\">\n  Require all denied\n</FilesMatch>\nOptions -Indexes\n");
            page('¡Listo!', '<p>Tu sincronización quedó lista. Copia estos dos datos; la clave <b>no se vuelve a mostrar</b>.</p>'
                . '<label>Dirección</label><p class="mono">' . htmlspecialchars(selfUrl()) . '</p>'
                . '<label>Clave de sincronización</label><p class="mono big">' . htmlspecialchars($key) . '</p>'
                . '<p>En cada aparato: Tokkip Notas → Ajustes → Sincronizar. Pon la dirección, la clave y una <b>frase secreta</b> (la misma en todos). La frase cifra tus notas antes de que salgan del aparato: ni este servidor ni Hostinger pueden leerlas. Si la olvidas, tus notas siguen en tus aparatos, pero hay que empezar la sincronización de nuevo.</p>'
                . '<p class="dim">¿Perdiste la clave? Borra <span class="mono">sync-config.php</span> en el Administrador de archivos y vuelve a abrir esta página: tus notas en el servidor se quedan.</p>');
            return;
        } catch (Throwable $e) {
            $error = $e instanceof PDOException ? 'No me pude conectar a la base de datos: revisa el nombre, el usuario y la contraseña.' : $e->getMessage();
        }
    }
    page('Sincronizar Tokkip Notas', ($error ? '<p class="error">' . htmlspecialchars($error) . '</p>' : '')
        . '<p>Primero crea una base de datos MySQL en hPanel (Sitios web → Administrar → Bases de datos → Administración). Luego pon aquí sus datos:</p>'
        . '<form method="post">'
        . '<label>Nombre de la base de datos</label><input name="name" placeholder="u123456789_notas" required>'
        . '<label>Usuario</label><input name="user" placeholder="u123456789_tokkip" required>'
        . '<label>Contraseña</label><input name="pass" type="password">'
        . '<label>Servidor</label><input name="host" value="localhost">'
        . '<button>Conectar</button></form>');
}

function page(string $title, string $body): void
{
    header('Content-Type: text/html; charset=utf-8');
    header('Cache-Control: no-store');
    header("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'");
    echo '<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>' . htmlspecialchars($title) . '</title><style>'
        . 'body{margin:0;background:#0b0c0e;color:#f5f6f8;font:15px/1.55 system-ui,"Segoe UI",sans-serif;display:grid;place-items:center;min-height:100vh;padding:24px;box-sizing:border-box}'
        . 'main{width:min(520px,100%);background:#141518;border:1px solid #1f2126;border-radius:28px;padding:28px}'
        . 'h1{margin:0 0 12px;font-size:28px}label{display:block;margin:14px 0 6px;color:#c4b5fd;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:.05em}'
        . 'input{width:100%;box-sizing:border-box;height:42px;border-radius:14px;border:1px solid #2a2d34;background:#0b0c0e;color:inherit;padding:0 14px;font:inherit}'
        . 'button{margin-top:20px;height:42px;padding:0 22px;border:0;border-radius:999px;background:#a78bfa;color:#14102a;font:600 15px system-ui;cursor:pointer}'
        . '.mono{font-family:ui-monospace,Consolas,monospace;background:#0b0c0e;border-radius:12px;padding:10px 12px;word-break:break-all}.big{font-size:17px;color:#c4b5fd}'
        . '.error{background:#3a1419;color:#ffb3bb;border-radius:12px;padding:10px 12px}.dim{color:#8e939c;font-size:13px}'
        . '</style></head><body><main><h1>' . htmlspecialchars($title) . '</h1>' . $body . '</main></body></html>';
}
