// Sync with your own server (notas-sync/sync.php on your hosting): what
// changed here goes up encrypted, what changed on your other devices comes
// down. It runs when the app opens or comes back, a few seconds after you
// change something, every few minutes while it's open, and on demand.
// The rules live in SyncCore.kt; the server only sees ids, versions and ciphertext.

package mx.loperstack.tokkipnotas.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

data class SyncStatus(
    val enabled: Boolean = false,
    val url: String = "",
    val last: Long = 0,
    val pending: Int = 0,
    val error: String? = null,
    val running: Boolean = false,
)

data class SyncReport(val pushed: Int = 0, val pulled: Int = 0, val conflicts: Int = 0, val changed: Set<String> = emptySet())

class SyncError(message: String) : Exception(message)

private class Creds(val url: String, val key: String, val phrase: String)

object Sync {
    private const val URL_KEY = "sync.url"
    private const val PUSH_BATCH = 200
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var app: Context? = null
    private var soonJob: Job? = null
    private var derived: Triple<String, String, ByteArray>? = null

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Pages that changed because of the server (an open page reloads itself). */
    private val _remote = MutableSharedFlow<Set<String>>(extraBufferCapacity = 8)
    val remote: SharedFlow<Set<String>> = _remote.asSharedFlow()

    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        Secrets.init(context)
        Store.onChange = { soon() }
        scope.launch { refresh() }
    }

    private fun creds(): Creds? {
        val url = Store.onDb { meta(this, URL_KEY) } ?: return null
        return Creds(url, Secrets.get("sync-key") ?: return null, Secrets.get("sync-phrase") ?: return null)
    }

    private suspend fun refresh(error: String? = _status.value.error) = withContext(Dispatchers.IO) {
        val c = runCatching { creds() }.getOrNull()
        val last = Store.onDb { meta(this, "sync.last") }?.toLongOrNull() ?: 0L
        val pending = Store.onDb { rawQuery("SELECT COUNT(*) FROM sync_rows WHERE dirty=1", null).use { if (it.moveToFirst()) it.getInt(0) else 0 } }
        _status.value = _status.value.copy(enabled = c != null, url = Store.onDb { meta(this, URL_KEY) } ?: "", last = last, pending = pending, error = if (c == null) null else error)
    }

    /** A sync a few seconds after the last change. */
    fun soon() {
        if (!_status.value.enabled) return
        soonJob?.cancel()
        soonJob = scope.launch { delay(4000); now() }
    }

    /** One sync now (nothing if it isn't set up or one is running). */
    fun now(): Job = scope.launch {
        if (lock.isLocked) return@launch
        val c = creds() ?: return@launch
        lock.withLock { runAndReport(c) }
    }

    private suspend fun runAndReport(c: Creds): SyncReport? {
        _status.value = _status.value.copy(running = true)
        return try {
            val r = run(c)
            if (r.changed.isNotEmpty()) { Store.reload(); _remote.tryEmit(r.changed) }
            refresh(null)
            r
        } catch (e: Exception) {
            refresh(e.message ?: "No se pudo sincronizar")
            null
        } finally {
            _status.value = _status.value.copy(running = false)
        }
    }

    /** Joins a server: checks it and the phrase, sends what's here, brings what's there. Throws SyncError. */
    suspend fun connect(url: String, key: String, phrase: String): SyncReport = withContext(Dispatchers.IO) {
        lock.withLock {
            val c = Creds(url.trim(), key.trim(), phrase)
            if (c.url.isEmpty() || c.key.isEmpty()) throw SyncError("Falta la dirección o la clave")
            unlock(c)
            Store.onDb {
                if (meta(this, URL_KEY) != c.url) {
                    forget(this)
                    // Everything here goes up, each page with the version of its last edit.
                    execSQL("UPDATE sync_rows SET dirty=1")
                    execSQL("INSERT OR IGNORE INTO sync_rows (id, ver, dirty, deleted) SELECT id, MAX(updated, created, 1), 1, 0 FROM pages")
                }
                setMeta(this, URL_KEY, c.url)
            }
            Secrets.put("sync-key", c.key)
            Secrets.put("sync-phrase", c.phrase)
            _status.value = _status.value.copy(enabled = true, url = c.url)
            val r = runAndReport(c)
            r ?: throw SyncError(_status.value.error ?: "No se pudo sincronizar")
        }
    }

    /** Stops syncing here: notes stay; the key and phrase are forgotten. */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        lock.withLock {
            Store.onDb { forget(this); setMeta(this, URL_KEY, null) }
            Secrets.put("sync-key", null)
            Secrets.put("sync-phrase", null)
            derived = null
            refresh(null)
        }
    }

    // ── The conversation ──────────────────────────────────────────────────────

    private fun post(c: Creds, body: JSONObject): JSONObject {
        val url = c.url.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://10.0.2.2") && !url.startsWith("http://127.0.0.1")) throw SyncError("La dirección tiene que empezar con https://")
        val conn = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 40_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Tokkip-Key", c.key.trim())
                setRequestProperty("User-Agent", "TokkipNotas-Android")
            }
        } catch (e: Exception) {
            throw SyncError("La dirección no es válida")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code >= 400) conn.errorStream else conn.inputStream)?.use { it.readBytes().decodeToString() } ?: ""
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: throw SyncError(if (code == 404) "No encontré sync.php en esa dirección" else "Tu servidor no contestó como Tokkip Notas espera (¿es la dirección de sync.php?)")
            if (!json.optBoolean("ok")) throw SyncError(json.optString("error").ifEmpty { "Tu servidor rechazó la sincronización" })
            return json
        } catch (e: SocketTimeoutException) {
            throw SyncError("Tu servidor tardó demasiado en contestar")
        } catch (e: IOException) {
            throw SyncError("No pude conectar con tu servidor")
        } finally {
            conn.disconnect()
        }
    }

    private fun unlock(c: Creds): ByteArray {
        if (c.phrase.trim().length < 6) throw SyncError("La frase secreta necesita al menos 6 letras")
        val hello = post(c, JSONObject().put("action", "hello"))
        val salt = hello.optString("salt").ifEmpty { throw SyncError("Tu servidor no tiene sal; vuelve a instalar sync.php") }
        val key = derived?.takeIf { it.first == salt && it.second == c.phrase }?.third
            ?: SyncCrypto.derive(c.phrase, salt).also { derived = Triple(salt, c.phrase, it) }
        val wrong = SyncError("La frase secreta no es la misma que en tus otros aparatos")
        val check = if (hello.isNull("check")) null else hello.optString("check")
        val stored = check ?: post(c, JSONObject().put("action", "check").put("check", SyncCrypto.seal(key, SyncCrypto.CHECK.toByteArray()))).optString("check")
        if (SyncCrypto.open(key, stored)?.decodeToString() != SyncCrypto.CHECK) throw wrong
        return key
    }

    private fun run(c: Creds): SyncReport {
        val key = unlock(c)
        var since = Store.onDb { meta(this, "sync.rev") }?.toLongOrNull() ?: 0L
        var pushed = 0
        var pulled = 0
        var conflicts = 0
        val changed = HashSet<String>()

        fun handle(resp: JSONObject): Boolean {
            val rows = resp.optJSONArray("rows") ?: JSONArray()
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val res = apply(key, r)
                pulled += res.first
                conflicts += res.second
                changed += res.third
            }
            val rev = resp.optLong("rev", since)
            if (rev > since) {
                since = rev
                Store.onDb { setMeta(this, "sync.rev", since.toString()) }
            }
            return resp.optBoolean("more")
        }

        // 1. Pull everything new first, so nothing here goes up over a change it hasn't seen.
        for (round in 0 until 1000) {
            if (!handle(post(c, JSONObject().put("action", "sync").put("since", since).put("push", JSONArray())))) break
        }
        // 2. Push what waits here.
        for (round in 0 until 200) {
            val dirty = Store.onDb {
                val out = ArrayList<Triple<String, Long, Pair<Page, String>?>>()
                rawQuery("SELECT id, ver, deleted FROM sync_rows WHERE dirty=1 LIMIT $PUSH_BATCH", null).use { cur ->
                    while (cur.moveToNext()) {
                        val id = cur.getString(0)
                        out += Triple(id, cur.getLong(1), if (cur.getInt(2) != 0) null else Store.rowOf(this, id))
                    }
                }
                out
            }
            if (dirty.isEmpty()) break
            val push = JSONArray()
            val sent = ArrayList<Triple<String, Long, String?>>()
            for ((id, ver, row) in dirty) {
                if (row == null) {
                    push.put(JSONObject().put("id", id).put("ver", ver).put("deleted", true))
                    sent += Triple(id, ver, null)
                } else {
                    val data = SyncCrypto.seal(key, travelJson(row.first, row.second).toString().toByteArray())
                    push.put(JSONObject().put("id", id).put("ver", ver).put("deleted", false).put("data", data))
                    sent += Triple(id, ver, SyncCrypto.fingerprint(row.first.title, row.second))
                }
            }
            val resp = post(c, JSONObject().put("action", "sync").put("since", since).put("push", push))
            val accepted = resp.optJSONArray("accepted")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
            Store.onDb {
                for ((id, ver, base) in sent) if (id in accepted) {
                    execSQL("UPDATE sync_rows SET dirty=0, base=? WHERE id=? AND ver=?", arrayOf<Any?>(base, id, ver))
                    pushed++
                }
            }
            var more = handle(resp)
            while (more) more = handle(post(c, JSONObject().put("action", "sync").put("since", since).put("push", JSONArray())))
        }
        val at = System.currentTimeMillis()
        Store.onDb { setMeta(this, "sync.last", at.toString()) }
        return SyncReport(pushed, pulled, conflicts, changed)
    }

    /** One page from the server: (pulled, conflicts, changed ids). */
    private fun apply(key: ByteArray, r: JSONObject): Triple<Int, Int, Set<String>> {
        val id = r.optString("id").ifEmpty { return Triple(0, 0, emptySet()) }
        val ver = r.optLong("ver")
        val remote: Pair<Page, String>? = if (r.optBoolean("deleted")) null else {
            val bytes = SyncCrypto.open(key, r.optString("data")) ?: throw SyncError("Una página del servidor no abre con tu frase secreta")
            val p = runCatching { travelOf(JSONObject(bytes.decodeToString())) }.getOrNull() ?: throw SyncError("Una página del servidor está dañada")
            if (p.first.id != id) throw SyncError("Una página del servidor no coincide")
            p
        }
        return Store.onDb {
            val state = rawQuery("SELECT ver, dirty, base FROM sync_rows WHERE id=?", arrayOf(id)).use { c ->
                if (c.moveToFirst()) SyncState(c.getLong(0), c.getInt(1) != 0, if (c.isNull(2)) null else c.getString(2)) else null
            }
            val local = if (state?.dirty == true) Store.rowOf(this, id) else null
            val copies = ArrayList<String>()
            val res = settle(id, ver, remote, state, local) { p, body ->
                if (p.title.isBlank() && body.isBlank()) return@settle
                val copy = p.copy(
                    id = newId(), title = "${p.title.trim().ifEmpty { "Sin título" }} (conflicto)", ord = p.ord + 0.001,
                    updated = System.currentTimeMillis(), favorite = false, trashed = 0, opened = 0,
                )
                Store.putRaw(this, copy, body)
                Store.mark(this, copy.id, false)
                copies += copy.id
            }
            val theirs = remote?.let { SyncCrypto.fingerprint(it.first.title, it.second) }
            when (res) {
                Settle.Skip -> Triple(0, copies.size, copies.toSet())
                Settle.Take -> {
                    if (remote != null) Store.putRaw(this, remote.first, remote.second) else delete("pages", "id=?", arrayOf(id))
                    execSQL("INSERT OR REPLACE INTO sync_rows (id, ver, dirty, deleted, base) VALUES (?, ?, 0, ?, ?)", arrayOf<Any?>(id, ver, if (remote == null) 1 else 0, theirs))
                    Triple(1, copies.size, copies.toSet() + id)
                }
                is Settle.Keep -> {
                    if (res.page != null && res.body != null) Store.putRaw(this, res.page, res.body)
                    execSQL("INSERT OR REPLACE INTO sync_rows (id, ver, dirty, deleted, base) VALUES (?, ?, 1, ?, ?)", arrayOf<Any?>(id, res.ver, if (res.deleted) 1 else 0, res.base))
                    Triple(if (res.page != null) 1 else 0, copies.size, copies.toSet() + (if (res.page != null) setOf(id) else emptySet()))
                }
            }
        }
    }

    // ── Small helpers on the database ─────────────────────────────────────────

    private fun meta(db: SQLiteDatabase, k: String): String? =
        db.rawQuery("SELECT v FROM meta WHERE k=?", arrayOf(k)).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun setMeta(db: SQLiteDatabase, k: String, v: String?) {
        if (v == null) db.delete("meta", "k=?", arrayOf(k)) else db.execSQL("INSERT OR REPLACE INTO meta (k, v) VALUES (?, ?)", arrayOf(k, v))
    }

    private fun forget(db: SQLiteDatabase) {
        db.execSQL("DELETE FROM sync_rows WHERE deleted=1")
        db.execSQL("DELETE FROM meta WHERE k LIKE 'sync.%'")
    }
}
