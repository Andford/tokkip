# Tokkip — guide for AI coding agents

Tokkip is Andy's (LoperStack) personal companion app family: a character
(bunny, kitten, puppy, robot or raccoon) that lives on the phone, the watch, the
car and the PC, plus Tokkip Notas, a Notion-style notebook. Spanish first.

## Where things are
- `android/app` — Tokkip for the phone (Kotlin, Compose, Glance widgets, Android Auto). Package `mx.loperstack.tokkip`.
- `android/shared` — the character (`mx.loperstack.tokkip.character`), sounds (`res/raw`, synthesised by `android/tools/make_sounds.py`), wellness logic shared with the watch.
- `android/wear`, `android/watchface` — the watch app and the Watch Face Format face.
- `android/notas` — Tokkip Notas for Android (`mx.loperstack.tokkipnotas`).
- `windows/` — Tokkip PC, the island at the top of the screen (Tauri 2: Rust in `src-tauri/`, TypeScript in `src/`).
- `notas-pc/` — Tokkip Notas for Windows (Tauri 2, Preact). `notas-sync/` — `sync.php`, its sync server for the user's own hosting.
- Tokkip Healthy PC (the PC cleaner) lives in its own repo, Andford/tokkiphealthypc.

## Build
- Android: `gradle -p android assembleRelease` (CI: `.github/workflows/android.yml`).
- Tokkip PC: `cd windows && npm install && npm run tauri dev` (CI: `tokkip-pc.yml`).
- Tokkip Notas PC: `cd notas-pc && npm install && npm run tauri dev` (CI: `tokkip-notas-pc.yml`).

## Rules
- Keep the app identifiers: `mx.loperstack.tokkip` (phone and watch: they must match), `mx.loperstack.tokkip.watchface`, `mx.loperstack.tokkipnotas` and Tokkip PC's `mx.loperstack.tokkippc`. Changing one makes a new app and loses the user's data. Older names only survive where old data is read once: `wellness/Legacy.kt` (old backups), `windows/src/core/legacy.ts`, `platform::move_old_dirs` and `secrets.rs` (`OLD_IDENTIFIER`).
- Tokkip's motion and clothes are his own: `windows/src/tokkip/` (motion.ts, figure.ts, ropa.ts, wardrobe.ts, greeting.ts), `windows/src/upload/` and, on Android, `character/TokkipEngine.kt` and `Ropa.kt`. Both platforms share the same moods, acts, dance and calendar: change them together.
- The code started from Coucou (MIT, Louis Raillé): keep its notice in `LICENSE`. Tokkip uses its own name, character, sounds and art — never Coucou's or Mochi's.
- Secrets in the Keystore / Credential Manager, never on disk or in git. The signing keystore is private and never committed.
- No telemetry. Network calls only to services the user set up.
- Storage names and keys, sync paths and pill IDs are contracts: renaming one needs a migration.
- Commits as Andy (`soportemacrobit001@gmail.com`); never push to upstream.
