# Sincronizar Tokkip Notas con tu hosting

`sync.php` es un solo archivo que subes a tu hosting (Hostinger, o cualquiera
con PHP 8 y MySQL). Con él, Tokkip Notas en el teléfono, la tablet y la PC se
mandan los cambios entre sí.

**Tu hosting no puede leer tus notas.** Cada página sale del aparato cifrada
(AES-256-GCM) con una llave que sale de tu **frase secreta**, y esa frase nunca
sale de tus aparatos. En el servidor solo quedan el id de cada página, cuándo
cambió, si se borró y el texto cifrado.

## Instalarlo en Hostinger (hPanel)

Los nombres de los menús pueden cambiar un poco, pero los pasos son estos:

1. **La base de datos.** En hPanel, entra a tu sitio → **Bases de datos** →
   **Administración**. Crea una base de datos MySQL con su usuario y
   contraseña (Hostinger les pone un prefijo, por ejemplo
   `u123456789_notas`). Anota los tres datos.
2. **Un subdominio (recomendado).** En **Dominios** → **Subdominios**, crea
   uno como `notas.tudominio.com`. Revisa en **Seguridad** → **SSL** que tenga
   su certificado (Hostinger lo pone gratis). Si prefieres, también sirve una
   carpeta de tu sitio, como `tudominio.com/notas/`.
3. **Sube el archivo.** En **Archivos** → **Administrador de archivos**, entra
   a la carpeta del subdominio y sube `sync.php`.
4. **Ábrelo en el navegador**: `https://notas.tudominio.com/sync.php`. Te pide
   los datos de la base de datos; al conectar, crea sus tablas y te muestra la
   **dirección** y la **clave de sincronización**. Cópialas: la clave se
   muestra una sola vez.
5. **En cada aparato**: Tokkip Notas → Ajustes → **Sincronizar**. Pon la
   dirección, la clave y tu frase secreta (la misma en todos). En el primer
   aparato, la frase que escribas queda como la frase.

PHP 8.1 o más nuevo (hPanel → **Avanzado** → **Configuración de PHP**).

## Cómo se comportan los cambios

- Se sincroniza al abrir la app, unos segundos después de que cambias algo,
  cada pocos minutos mientras está abierta y con el botón «Ahora».
- Sin internet todo sigue funcionando; los cambios esperan y se mandan después.
- Si cambias una página en dos aparatos antes de que se sincronicen:
  - si en uno cambiaste el texto y en el otro solo lo demás (moverla, su
    ícono, un valor, favoritos), se juntan las dos cosas;
  - si cambiaste el texto en los dos, gana el cambio más reciente y el otro se
    queda junto a ella como **«… (conflicto)»**, para que no se pierda nada.
- Lo que borras se borra en todos; pero si en otro aparato escribiste en esa
  página, lo escrito vuelve.

## Si algo pasa

- **¿Perdiste la clave?** En el Administrador de archivos borra
  `sync-config.php` y vuelve a abrir `sync.php`: te da una clave nueva y tus
  notas del servidor se quedan. Luego pon la nueva clave en cada aparato
  (Ajustes → Sincronizar → Dejar de sincronizar, y conectar otra vez).
- **¿Olvidaste la frase?** Tus notas siguen completas en tus aparatos. Para
  empezar de nuevo: en hPanel vacía las tablas `tn_pages` y `tn_meta` (desde
  phpMyAdmin) o crea otra base de datos, borra `sync-config.php`, instala otra
  vez y conecta cada aparato con la frase nueva.
- `sync-config.php` guarda los datos de la base de datos y solo la *huella* de
  la clave; un `.htaccess` evita que se pueda descargar.

## Cómo habla (para quien quiera revisarlo)

`POST` con JSON y la cabecera `X-Tokkip-Key`:

| Acción | Envía | Recibe |
|---|---|---|
| `hello` | — | la sal para la llave, la prueba de la frase, la última revisión |
| `check` | la prueba de la frase (solo la primera vez) | la prueba guardada |
| `sync` | `since` y las páginas cambiadas `{id, ver, deleted, data}` | las aceptadas y lo nuevo desde `since` (de 300 en 300) |

Una página se acepta si su versión es más nueva que la guardada. La llave es
PBKDF2-HMAC-SHA256 (150 000 vueltas) de la frase en NFC con la sal del
servidor; `data` es `base64(iv ‖ AES-256-GCM(JSON de la página))`. Las reglas
de cada aparato están en `notas-pc/src-tauri/src/sync.rs` y en
`android/notas/.../data/SyncCore.kt`, y se prueban juntas.
