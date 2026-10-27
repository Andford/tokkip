// Where your pages live on the PC: notas.db in the app's AppData folder, a
// SQLite database with the same `pages` table as Tokkip Notas on Android.
// The window keeps the index in memory and asks for a page's body when it
// opens it. Once a day, before opening, the database is copied to backups/
// (the last 7 are kept), and the Papelera forgets what's older than 30 days.
//
// For sync (sync.rs), every change is also noted in `sync_rows`: the page's
// version (when it last changed here) and whether the server has it yet.
// Deleting leaves a tombstone there so other devices delete it too. Opening a
// page ("quiet" writes) stays on this PC.

use rusqlite::{params, Connection, OptionalExtension};
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

const DAY_MS: i64 = 86_400_000;
const KEEP_BACKUPS: usize = 7;

const SCHEMA: &str = "CREATE TABLE IF NOT EXISTS pages (
    id TEXT PRIMARY KEY, parent TEXT, title TEXT NOT NULL DEFAULT '', icon TEXT NOT NULL DEFAULT 'doc',
    color INTEGER NOT NULL DEFAULT 0, shape INTEGER NOT NULL DEFAULT 0, kind TEXT NOT NULL DEFAULT 'DOC', ord REAL NOT NULL DEFAULT 0,
    created INTEGER NOT NULL DEFAULT 0, updated INTEGER NOT NULL DEFAULT 0, opened INTEGER NOT NULL DEFAULT 0,
    favorite INTEGER NOT NULL DEFAULT 0, trashed INTEGER NOT NULL DEFAULT 0, props TEXT NOT NULL DEFAULT '', schema TEXT NOT NULL DEFAULT '',
    preview TEXT NOT NULL DEFAULT '', steps INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, body TEXT NOT NULL DEFAULT '');
  CREATE INDEX IF NOT EXISTS pages_parent ON pages(parent);
  CREATE TABLE IF NOT EXISTS sync_rows (id TEXT PRIMARY KEY, ver INTEGER NOT NULL, dirty INTEGER NOT NULL DEFAULT 1, deleted INTEGER NOT NULL DEFAULT 0, base TEXT);
  CREATE INDEX IF NOT EXISTS sync_rows_dirty ON sync_rows(dirty);
  CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT NOT NULL);";

const META: &str = "id,parent,title,icon,color,shape,kind,ord,created,updated,opened,favorite,trashed,props,schema,preview,steps,done";

/// A page row. `body` is only filled when it's written or copied; `None` on a
/// write keeps the stored body.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Row {
    pub id: String,
    pub parent: Option<String>,
    pub title: String,
    pub icon: String,
    pub color: i64,
    pub shape: i64,
    pub kind: String,
    pub ord: f64,
    pub created: i64,
    pub updated: i64,
    pub opened: i64,
    pub favorite: bool,
    pub trashed: i64,
    pub props: String,
    pub schema: String,
    pub preview: String,
    pub steps: i64,
    pub done: i64,
    #[serde(default)]
    pub body: Option<String>,
}

fn row_of(r: &rusqlite::Row, with_body: bool) -> rusqlite::Result<Row> {
    Ok(Row {
        id: r.get(0)?,
        parent: r.get(1)?,
        title: r.get(2)?,
        icon: r.get(3)?,
        color: r.get(4)?,
        shape: r.get(5)?,
        kind: r.get(6)?,
        ord: r.get(7)?,
        created: r.get(8)?,
        updated: r.get(9)?,
        opened: r.get(10)?,
        favorite: r.get::<_, i64>(11)? != 0,
        trashed: r.get(12)?,
        props: r.get(13)?,
        schema: r.get(14)?,
        preview: r.get(15)?,
        steps: r.get(16)?,
        done: r.get(17)?,
        body: if with_body { Some(r.get(18)?) } else { None },
    })
}

#[derive(Debug, Clone, PartialEq)]
pub struct SyncState {
    pub ver: i64,
    pub dirty: bool,
    pub base: Option<String>,
}

pub fn now_ms() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as i64).unwrap_or(0)
}

pub struct Db {
    conn: Mutex<Connection>,
    pub path: PathBuf,
}

impl Db {
    pub fn open(path: &Path) -> rusqlite::Result<Db> {
        let conn = Connection::open(path)?;
        conn.execute_batch("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;")?;
        conn.execute_batch(SCHEMA)?;
        Ok(Db { conn: Mutex::new(conn), path: path.to_path_buf() })
    }

    #[cfg(test)]
    pub fn memory() -> Db {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch(SCHEMA).unwrap();
        Db { conn: Mutex::new(conn), path: PathBuf::new() }
    }

    fn c(&self) -> std::sync::MutexGuard<'_, Connection> {
        self.conn.lock().unwrap_or_else(|e| e.into_inner())
    }

    /// Every page without its body; the Papelera keeps things 30 days.
    pub fn load(&self, now: i64) -> rusqlite::Result<Vec<Row>> {
        let mut c = self.c();
        {
            let tx = c.transaction()?;
            let old: Vec<String> = {
                let mut q = tx.prepare("SELECT id FROM pages WHERE trashed > 0 AND trashed < ?1")?;
                let ids = q.query_map(params![now - 30 * DAY_MS], |r| r.get(0))?.collect::<rusqlite::Result<Vec<_>>>()?;
                ids
            };
            for id in &old {
                tx.execute("DELETE FROM pages WHERE id=?1", params![id])?;
                mark(&tx, id, true, now)?;
            }
            tx.commit()?;
        }
        let mut st = c.prepare(&format!("SELECT {META} FROM pages"))?;
        let rows = st.query_map([], |r| row_of(r, false))?.collect::<rusqlite::Result<Vec<_>>>()?;
        Ok(rows)
    }

    pub fn body(&self, id: &str) -> rusqlite::Result<String> {
        let c = self.c();
        Ok(c.query_row("SELECT body FROM pages WHERE id=?1", params![id], |r| r.get(0)).optional()?.unwrap_or_default())
    }

    /// Writes pages; unless `quiet`, they're marked to go to the other devices.
    pub fn upsert(&self, rows: &[Row], quiet: bool) -> rusqlite::Result<()> {
        let now = now_ms();
        let mut c = self.c();
        let tx = c.transaction()?;
        for r in rows {
            put(&tx, r)?;
            if !quiet {
                mark(&tx, &r.id, false, now)?;
            }
        }
        tx.commit()
    }

    pub fn remove(&self, ids: &[String]) -> rusqlite::Result<()> {
        let now = now_ms();
        let mut c = self.c();
        let tx = c.transaction()?;
        for id in ids {
            tx.execute("DELETE FROM pages WHERE id=?1", params![id])?;
            mark(&tx, id, true, now)?;
        }
        tx.commit()
    }

    pub fn dump(&self) -> rusqlite::Result<Vec<Row>> {
        let c = self.c();
        let mut st = c.prepare(&format!("SELECT {META},body FROM pages"))?;
        let rows = st.query_map([], |r| row_of(r, true))?.collect::<rusqlite::Result<Vec<_>>>()?;
        Ok(rows)
    }

    pub fn bulk(&self, rows: &[Row], replace: bool) -> rusqlite::Result<()> {
        let now = now_ms();
        let mut c = self.c();
        let tx = c.transaction()?;
        if replace {
            // What the copy doesn't have goes away everywhere.
            let keep: std::collections::HashSet<&str> = rows.iter().map(|r| r.id.as_str()).collect();
            let gone: Vec<String> = {
                let mut q = tx.prepare("SELECT id FROM pages")?;
                let ids = q.query_map([], |r| r.get::<_, String>(0))?.collect::<rusqlite::Result<Vec<_>>>()?;
                ids.into_iter().filter(|i| !keep.contains(i.as_str())).collect()
            };
            tx.execute("DELETE FROM pages", [])?;
            for id in &gone {
                mark(&tx, id, true, now)?;
            }
        }
        for r in rows {
            put(&tx, r)?;
            mark(&tx, &r.id, false, now)?;
        }
        tx.commit()
    }

    // ── For sync ──────────────────────────────────────────────────────────────

    pub fn meta(&self, k: &str) -> Option<String> {
        self.c().query_row("SELECT v FROM meta WHERE k=?1", params![k], |r| r.get(0)).optional().ok().flatten()
    }

    pub fn set_meta(&self, k: &str, v: Option<&str>) -> rusqlite::Result<()> {
        let c = self.c();
        match v {
            Some(v) => c.execute("INSERT OR REPLACE INTO meta (k, v) VALUES (?1, ?2)", params![k, v])?,
            None => c.execute("DELETE FROM meta WHERE k=?1", params![k])?,
        };
        Ok(())
    }

    /// Joining a server: everything here goes up, each page with the version of
    /// its last edit (so the same page on two devices keeps the newest).
    pub fn mark_all_for_join(&self) -> rusqlite::Result<()> {
        let mut c = self.c();
        let tx = c.transaction()?;
        tx.execute("UPDATE sync_rows SET dirty=1", [])?;
        tx.execute(
            "INSERT OR IGNORE INTO sync_rows (id, ver, dirty, deleted)
             SELECT id, MAX(updated, created, 1), 1, 0 FROM pages",
            [],
        )?;
        tx.commit()
    }

    /// Pages waiting to go up: (id, ver, deleted, row with body if it still exists).
    pub fn dirty(&self, limit: usize) -> rusqlite::Result<Vec<(String, i64, bool, Option<Row>)>> {
        let c = self.c();
        let mut q = c.prepare("SELECT id, ver, deleted FROM sync_rows WHERE dirty=1 LIMIT ?1")?;
        let list = q
            .query_map(params![limit as i64], |r| Ok((r.get::<_, String>(0)?, r.get::<_, i64>(1)?, r.get::<_, i64>(2)? != 0)))?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        let mut page = c.prepare(&format!("SELECT {META},body FROM pages WHERE id=?1"))?;
        let mut out = Vec::with_capacity(list.len());
        for (id, ver, deleted) in list {
            let row = if deleted { None } else { page.query_row(params![id], |r| row_of(r, true)).optional()? };
            let gone = row.is_none();
            out.push((id, ver, deleted || gone, row));
        }
        Ok(out)
    }

    pub fn dirty_count(&self) -> i64 {
        self.c().query_row("SELECT COUNT(*) FROM sync_rows WHERE dirty=1", [], |r| r.get(0)).unwrap_or(0)
    }

    /// The server took this version: clean, unless it changed again meanwhile.
    /// `base` is the fingerprint of the text the server now has.
    pub fn mark_clean(&self, id: &str, ver: i64, base: Option<&str>) -> rusqlite::Result<()> {
        self.c().execute("UPDATE sync_rows SET dirty=0, base=?3 WHERE id=?1 AND ver=?2", params![id, ver, base])?;
        Ok(())
    }

    /// What sync knows of a page: its version, whether it waits to go up, and
    /// the fingerprint of its text when it was last in step with the server.
    pub fn sync_state(&self, id: &str) -> rusqlite::Result<Option<SyncState>> {
        self.c()
            .query_row("SELECT ver, dirty, base FROM sync_rows WHERE id=?1", params![id], |r| {
                Ok(SyncState { ver: r.get(0)?, dirty: r.get::<_, i64>(1)? != 0, base: r.get(2)? })
            })
            .optional()
    }

    pub fn row(&self, id: &str) -> rusqlite::Result<Option<Row>> {
        self.c().query_row(&format!("SELECT {META},body FROM pages WHERE id=?1"), params![id], |r| row_of(r, true)).optional()
    }

    /// A page from another device: written as is (keeping when it was opened
    /// here), at its version, already in sync.
    pub fn apply_remote(&self, row: Option<&Row>, id: &str, ver: i64, base: Option<&str>) -> rusqlite::Result<()> {
        let mut c = self.c();
        let tx = c.transaction()?;
        match row {
            Some(r) => {
                let opened: i64 = tx.query_row("SELECT opened FROM pages WHERE id=?1", params![id], |x| x.get(0)).optional()?.unwrap_or(0);
                let mut r = r.clone();
                r.opened = opened;
                put(&tx, &r)?;
            }
            None => {
                tx.execute("DELETE FROM pages WHERE id=?1", params![id])?;
            }
        }
        tx.execute(
            "INSERT OR REPLACE INTO sync_rows (id, ver, dirty, deleted, base) VALUES (?1, ?2, 0, ?3, ?4)",
            params![id, ver, row.is_none() as i64, base],
        )?;
        tx.commit()
    }

    /// Settles a page in favour of this device (maybe merged with the server's):
    /// written if given, and waiting to go up with version `ver`.
    pub fn keep(&self, row: Option<&Row>, id: &str, ver: i64, base: Option<&str>, deleted: bool) -> rusqlite::Result<()> {
        let mut c = self.c();
        let tx = c.transaction()?;
        if let Some(r) = row {
            put(&tx, r)?;
        }
        tx.execute(
            "INSERT OR REPLACE INTO sync_rows (id, ver, dirty, deleted, base) VALUES (?1, ?2, 1, ?3, ?4)",
            params![id, ver, deleted as i64, base],
        )?;
        tx.commit()
    }

    /// Forget the server (sync turned off): next time, everything goes up again.
    pub fn forget_sync(&self) -> rusqlite::Result<()> {
        let c = self.c();
        c.execute("DELETE FROM sync_rows WHERE deleted=1", [])?;
        c.execute("DELETE FROM meta WHERE k LIKE 'sync.%'", [])?;
        Ok(())
    }
}

/// Notes a change for sync: a version newer than any before it, waiting to go up.
fn mark(c: &Connection, id: &str, deleted: bool, now: i64) -> rusqlite::Result<()> {
    let old: Option<i64> = c.query_row("SELECT ver FROM sync_rows WHERE id=?1", params![id], |r| r.get(0)).optional()?;
    let ver = old.map_or(now, |o| now.max(o + 1));
    // Keep `base` (what the server last had) so a clash can be told from a plain edit.
    c.execute(
        "INSERT INTO sync_rows (id, ver, dirty, deleted) VALUES (?1, ?2, 1, ?3)
         ON CONFLICT(id) DO UPDATE SET ver=excluded.ver, dirty=1, deleted=excluded.deleted",
        params![id, ver, deleted as i64],
    )?;
    Ok(())
}

/// Insert or replace one row; without a body, the stored body stays.
fn put(c: &Connection, r: &Row) -> rusqlite::Result<()> {
    let body: String = match &r.body {
        Some(b) => b.clone(),
        None => c
            .query_row("SELECT body FROM pages WHERE id=?1", params![r.id], |x| x.get(0))
            .optional()?
            .unwrap_or_default(),
    };
    c.execute(
        &format!("INSERT OR REPLACE INTO pages ({META},body) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,?12,?13,?14,?15,?16,?17,?18,?19)"),
        params![
            r.id, r.parent, r.title, r.icon, r.color, r.shape, r.kind, r.ord, r.created, r.updated, r.opened,
            r.favorite as i64, r.trashed, r.props, r.schema, r.preview, r.steps, r.done, body
        ],
    )?;
    Ok(())
}

/// Today's copy of the database in `backups/` (only one a day), keeping the last few.
pub fn daily_backup(db: &Path, now: i64) {
    if !db.exists() {
        return;
    }
    let Some(dir) = db.parent().map(|d| d.join("backups")) else { return };
    if std::fs::create_dir_all(&dir).is_err() {
        return;
    }
    let day = day_key(now);
    let target = dir.join(format!("notas-{day}.db"));
    if !target.exists() {
        let _ = std::fs::copy(db, &target);
    }
    let mut olds: Vec<PathBuf> = std::fs::read_dir(&dir)
        .map(|it| {
            it.filter_map(|e| e.ok().map(|e| e.path()))
                .filter(|p| p.file_name().and_then(|n| n.to_str()).is_some_and(|n| n.starts_with("notas-") && n.ends_with(".db")))
                .collect()
        })
        .unwrap_or_default();
    olds.sort();
    while olds.len() > KEEP_BACKUPS {
        let _ = std::fs::remove_file(olds.remove(0));
    }
}

/// yyyy-mm-dd (UTC) from milliseconds: civil-from-days, no date crate needed.
pub fn day_key(ms: i64) -> String {
    let z = ms.div_euclid(DAY_MS) + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let y = yoe + era * 400 + if m <= 2 { 1 } else { 0 };
    format!("{y:04}-{m:02}-{d:02}")
}

#[cfg(test)]
mod tests {
    use super::*;

    fn row(id: &str, parent: Option<&str>, body: Option<&str>) -> Row {
        Row {
            id: id.into(),
            parent: parent.map(Into::into),
            title: format!("Página {id}"),
            icon: "doc".into(),
            color: 0xFFA78BFA,
            shape: 0,
            kind: "DOC".into(),
            ord: 0.0,
            created: 1,
            updated: 1,
            opened: 0,
            favorite: false,
            trashed: 0,
            props: String::new(),
            schema: String::new(),
            preview: String::new(),
            steps: 0,
            done: 0,
            body: body.map(Into::into),
        }
    }

    #[test]
    fn a_write_without_body_keeps_the_body() {
        let db = Db::memory();
        db.upsert(&[row("a", None, Some("# Hola\n[ ] algo"))], false).unwrap();
        let mut r = row("a", None, None);
        r.title = "Otro".into();
        db.upsert(&[r], true).unwrap();
        assert_eq!(db.body("a").unwrap(), "# Hola\n[ ] algo");
        let all = db.load(now_ms()).unwrap();
        assert_eq!(all.len(), 1);
        assert_eq!(all[0].title, "Otro");
        assert_eq!(all[0].body, None);
    }

    #[test]
    fn the_papelera_forgets_after_30_days() {
        let db = Db::memory();
        let now = 100 * DAY_MS;
        let mut old = row("old", None, Some(""));
        old.trashed = now - 31 * DAY_MS;
        let mut recent = row("recent", None, Some(""));
        recent.trashed = now - 2 * DAY_MS;
        db.upsert(&[old, recent, row("live", None, Some(""))], false).unwrap();
        let ids: Vec<String> = db.load(now).unwrap().into_iter().map(|r| r.id).collect();
        assert!(!ids.contains(&"old".to_string()));
        assert!(ids.contains(&"recent".to_string()));
        assert!(ids.contains(&"live".to_string()));
    }

    #[test]
    fn bulk_replaces_or_joins() {
        let db = Db::memory();
        db.upsert(&[row("a", None, Some("uno"))], false).unwrap();
        db.bulk(&[row("b", None, Some("dos"))], false).unwrap();
        assert_eq!(db.dump().unwrap().len(), 2);
        db.bulk(&[row("c", Some("b"), Some("tres"))], true).unwrap();
        let all = db.dump().unwrap();
        assert_eq!(all.len(), 1);
        assert_eq!(all[0].body.as_deref(), Some("tres"));
        db.remove(&["c".into()]).unwrap();
        assert!(db.dump().unwrap().is_empty());
        assert_eq!(db.body("zzz").unwrap(), "");
    }

    #[test]
    fn changes_are_noted_for_sync() {
        let db = Db::memory();
        db.upsert(&[row("a", None, Some("uno")), row("b", None, Some("dos"))], false).unwrap();
        db.upsert(&[row("q", None, Some(""))], true).unwrap();
        assert_eq!(db.dirty_count(), 2);
        let st = db.sync_state("a").unwrap().unwrap();
        assert!(st.dirty);
        let ver = st.ver;
        db.mark_clean("a", ver, Some("h1")).unwrap();
        assert_eq!(db.sync_state("a").unwrap(), Some(SyncState { ver, dirty: false, base: Some("h1".into()) }));
        // A second change gets a newer version even within the same millisecond, and keeps the base.
        db.upsert(&[row("a", None, Some("uno!"))], false).unwrap();
        let st = db.sync_state("a").unwrap().unwrap();
        assert!(st.ver > ver && st.dirty && st.base.as_deref() == Some("h1"));
        db.remove(&["b".into()]).unwrap();
        let d = db.dirty(10).unwrap();
        let b = d.iter().find(|x| x.0 == "b").unwrap();
        assert!(b.2 && b.3.is_none());
        let a = d.iter().find(|x| x.0 == "a").unwrap();
        assert_eq!(a.3.as_ref().unwrap().body.as_deref(), Some("uno!"));
        // A page from another device lands clean and keeps when it was opened here.
        let mut r = row("a", None, Some("de lejos"));
        r.opened = 0;
        db.c().execute("UPDATE pages SET opened=77 WHERE id='a'", []).unwrap();
        db.apply_remote(Some(&r), "a", 10_000_000_000_000, Some("h2")).unwrap();
        assert_eq!(db.sync_state("a").unwrap(), Some(SyncState { ver: 10_000_000_000_000, dirty: false, base: Some("h2".into()) }));
        assert_eq!(db.row("a").unwrap().unwrap().opened, 77);
        assert_eq!(db.body("a").unwrap(), "de lejos");
        db.apply_remote(None, "a", 10_000_000_000_001, None).unwrap();
        assert!(db.row("a").unwrap().is_none());
    }

    #[test]
    fn day_keys() {
        assert_eq!(day_key(0), "1970-01-01");
        assert_eq!(day_key(1_761_436_800_000), "2025-10-26");
        assert_eq!(day_key(951_782_400_000), "2000-02-29");
    }

    #[test]
    fn backups_keep_the_last_ones() {
        let dir = std::env::temp_dir().join(format!("notas-test-{}", now_ms()));
        std::fs::create_dir_all(&dir).unwrap();
        let db = dir.join("notas.db");
        std::fs::write(&db, b"x").unwrap();
        for d in 0..10 {
            daily_backup(&db, d * DAY_MS);
        }
        let n = std::fs::read_dir(dir.join("backups")).unwrap().count();
        assert_eq!(n, KEEP_BACKUPS);
        assert!(dir.join("backups/notas-1970-01-10.db").exists());
        let _ = std::fs::remove_dir_all(&dir);
    }
}
