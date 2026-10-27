// Tokkip Notas for Windows: one window, your pages in a local SQLite database
// (store.rs). No accounts: copies are JSON files you save where you like (the
// same format as the Android app's), and sync (sync.rs) talks only to the
// server you set up on your own hosting.

mod secrets;
mod store;
mod sync;

use serde::Serialize;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use store::{Db, Row};
use tauri::{Manager, State};
use tauri_plugin_dialog::DialogExt;

type Shared = Arc<Db>;

fn err<E: std::fmt::Display>(e: E) -> String {
    e.to_string()
}

/// Database work off the window's thread.
async fn on_db<T: Send + 'static>(db: &Shared, job: impl FnOnce(&Db) -> rusqlite::Result<T> + Send + 'static) -> Result<T, String> {
    let db = db.clone();
    tauri::async_runtime::spawn_blocking(move || job(&db)).await.map_err(err)?.map_err(err)
}

#[tauri::command]
async fn notas_load(db: State<'_, Shared>) -> Result<Vec<Row>, String> {
    on_db(&db, |d| d.load(store::now_ms())).await
}

#[tauri::command]
async fn notas_body(db: State<'_, Shared>, id: String) -> Result<String, String> {
    on_db(&db, move |d| d.body(&id)).await
}

#[tauri::command]
async fn notas_upsert(db: State<'_, Shared>, rows: Vec<Row>, quiet: Option<bool>) -> Result<(), String> {
    on_db(&db, move |d| d.upsert(&rows, quiet.unwrap_or(false))).await
}

#[tauri::command]
async fn notas_remove(db: State<'_, Shared>, ids: Vec<String>) -> Result<(), String> {
    on_db(&db, move |d| d.remove(&ids)).await
}

#[tauri::command]
async fn notas_dump(db: State<'_, Shared>) -> Result<Vec<Row>, String> {
    on_db(&db, |d| d.dump()).await
}

#[tauri::command]
async fn notas_bulk(db: State<'_, Shared>, rows: Vec<Row>, replace: bool) -> Result<(), String> {
    on_db(&db, move |d| d.bulk(&rows, replace)).await
}

#[tauri::command]
fn notas_where(db: State<'_, Shared>) -> String {
    db.path.display().to_string()
}

/// Asks where to save a copy and writes it there. The file's name, or None if cancelled.
#[tauri::command]
async fn notas_save_copy(app: tauri::AppHandle, json: String, name: String) -> Result<Option<String>, String> {
    let picked = app
        .dialog()
        .file()
        .set_title("Guardar una copia de tus notas")
        .set_file_name(&name)
        .add_filter("Copia de Tokkip Notas", &["json"])
        .blocking_save_file();
    let Some(path) = picked.and_then(|p| p.into_path().ok()) else { return Ok(None) };
    std::fs::write(&path, json).map_err(err)?;
    Ok(path.file_name().map(|n| n.to_string_lossy().into_owned()))
}

/// Asks for a copy and reads it (up to 64 MB). None if cancelled.
#[tauri::command]
async fn notas_open_copy(app: tauri::AppHandle) -> Result<Option<String>, String> {
    let picked = app
        .dialog()
        .file()
        .set_title("Abrir una copia de Tokkip Notas")
        .add_filter("Copia de Tokkip Notas", &["json"])
        .blocking_pick_file();
    let Some(path) = picked.and_then(|p| p.into_path().ok()) else { return Ok(None) };
    let size = std::fs::metadata(&path).map_err(err)?.len();
    if size > 64 * 1024 * 1024 {
        return Err("La copia es demasiado grande".into());
    }
    std::fs::read_to_string(&path).map(Some).map_err(err)
}

// ── Sync ──────────────────────────────────────────────────────────────────────

struct SyncState {
    dir: PathBuf,
    keys: sync::KeyCache,
    running: Mutex<()>,
    /// Made on first use, off the window's thread (reqwest's blocking client can't be built inside the runtime).
    http: std::sync::OnceLock<sync::Http>,
    error: Mutex<Option<String>>,
}

const URL: &str = "sync.url";
const KEY: &str = "sync-key";
const PHRASE: &str = "sync-phrase";

#[derive(Serialize)]
struct SyncStatus {
    enabled: bool,
    url: String,
    last: i64,
    pending: i64,
    error: Option<String>,
}

fn creds(db: &Db, s: &SyncState) -> Option<sync::Creds> {
    let url = db.meta(URL)?;
    Some(sync::Creds { url, key: secrets::get(KEY, &s.dir)?, phrase: secrets::get(PHRASE, &s.dir)? })
}

#[tauri::command]
async fn sync_status(db: State<'_, Shared>, st: State<'_, Arc<SyncState>>) -> Result<SyncStatus, String> {
    let (db, st) = (db.inner().clone(), st.inner().clone());
    tauri::async_runtime::spawn_blocking(move || SyncStatus {
        enabled: creds(&db, &st).is_some(),
        url: db.meta(URL).unwrap_or_default(),
        last: db.meta("sync.last").and_then(|v| v.parse().ok()).unwrap_or(0),
        pending: db.dirty_count(),
        error: st.error.lock().unwrap_or_else(|e| e.into_inner()).clone(),
    })
    .await
    .map_err(err)
}

/// Joins a server: checks it and the phrase, sends what's here, brings what's there.
#[tauri::command]
async fn sync_connect(db: State<'_, Shared>, st: State<'_, Arc<SyncState>>, url: String, key: String, phrase: String) -> Result<sync::Report, String> {
    let (db, st) = (db.inner().clone(), st.inner().clone());
    tauri::async_runtime::spawn_blocking(move || {
        let _busy = st.running.lock().unwrap_or_else(|e| e.into_inner());
        let c = sync::Creds { url: url.trim().to_string(), key: key.trim().to_string(), phrase };
        if c.url.is_empty() || c.key.is_empty() {
            return Err("Falta la dirección o la clave".to_string());
        }
        sync::unlock(st.http.get_or_init(sync::Http::new), &c, &st.keys)?;
        // A new server (or the first time): everything here goes up.
        if db.meta(URL).as_deref() != Some(c.url.as_str()) {
            db.forget_sync().map_err(err)?;
            db.mark_all_for_join().map_err(err)?;
        }
        db.set_meta(URL, Some(&c.url)).map_err(err)?;
        secrets::set(KEY, Some(&c.key), &st.dir)?;
        secrets::set(PHRASE, Some(&c.phrase), &st.dir)?;
        let r = sync::run(&db, st.http.get_or_init(sync::Http::new), &c, &st.keys);
        *st.error.lock().unwrap_or_else(|e| e.into_inner()) = r.as_ref().err().cloned();
        r
    })
    .await
    .map_err(err)?
}

/// One sync now (nothing if it isn't set up, or one is already running).
#[tauri::command]
async fn sync_now(db: State<'_, Shared>, st: State<'_, Arc<SyncState>>) -> Result<Option<sync::Report>, String> {
    let (db, st) = (db.inner().clone(), st.inner().clone());
    tauri::async_runtime::spawn_blocking(move || {
        let Ok(_busy) = st.running.try_lock() else { return Ok(None) };
        let Some(c) = creds(&db, &st) else { return Ok(None) };
        let r = sync::run(&db, st.http.get_or_init(sync::Http::new), &c, &st.keys);
        *st.error.lock().unwrap_or_else(|e| e.into_inner()) = r.as_ref().err().cloned();
        r.map(Some)
    })
    .await
    .map_err(err)?
}

/// Stops syncing: your notes stay here; the key and phrase are forgotten.
#[tauri::command]
async fn sync_disconnect(db: State<'_, Shared>, st: State<'_, Arc<SyncState>>) -> Result<(), String> {
    let (db, st) = (db.inner().clone(), st.inner().clone());
    tauri::async_runtime::spawn_blocking(move || {
        let _busy = st.running.lock().unwrap_or_else(|e| e.into_inner());
        db.forget_sync().map_err(err)?;
        db.set_meta(URL, None).map_err(err)?;
        secrets::set(KEY, None, &st.dir)?;
        secrets::set(PHRASE, None, &st.dir)?;
        *st.error.lock().unwrap_or_else(|e| e.into_inner()) = None;
        Ok(())
    })
    .await
    .map_err(err)?
}

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            // A second launch brings the window you already have.
            if let Some(w) = app.get_webview_window("main") {
                let _ = w.unminimize();
                let _ = w.show();
                let _ = w.set_focus();
            }
        }))
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let dir = app.path().app_data_dir()?;
            std::fs::create_dir_all(&dir)?;
            let path = dir.join("notas.db");
            store::daily_backup(&path, store::now_ms());
            let db = Db::open(&path).map_err(|e| Box::new(e) as Box<dyn std::error::Error>)?;
            app.manage(Arc::new(db));
            app.manage(Arc::new(SyncState {
                dir,
                keys: sync::KeyCache::default(),
                running: Mutex::new(()),
                http: std::sync::OnceLock::new(),
                error: Mutex::new(None),
            }));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            notas_load,
            notas_body,
            notas_upsert,
            notas_remove,
            notas_dump,
            notas_bulk,
            notas_where,
            notas_save_copy,
            notas_open_copy,
            sync_status,
            sync_connect,
            sync_now,
            sync_disconnect
        ])
        .run(tauri::generate_context!())
        .expect("Tokkip Notas could not start");
}
