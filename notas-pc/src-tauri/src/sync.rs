// Sync with your own server (notas-sync/sync.php on your hosting).
//
// Every page that changed here goes up, encrypted; what changed on your other
// devices comes down. The server only sees ids, versions and ciphertext.
//   key  = PBKDF2-HMAC-SHA256(your phrase in NFC, the server's salt, 150 000, 32 bytes)
//   data = base64(iv[12] ‖ AES-256-GCM(JSON of the page))
// The page JSON is the copy format (without "opened", which stays on each device).
//
// The newest change wins. If a page's text was edited here and also somewhere
// else before this device caught up, the other version wins and this one is
// kept as a copy beside it, "(conflicto)", so nothing written is lost.
// The same rules run on Android (android/notas/.../data/Sync.kt).

use crate::store::{now_ms, Db, Row};
use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes256Gcm, Nonce};
use base64::engine::general_purpose::STANDARD as B64;
use base64::Engine as _;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::sync::Mutex;
use unicode_normalization::UnicodeNormalization;

pub const ROUNDS: u32 = 150_000;
pub const CHECK: &str = "tokkip-notas:check";
const INBOX: &str = "inbox";
const PUSH_BATCH: usize = 200;

// ── Crypto ────────────────────────────────────────────────────────────────────

pub fn derive(phrase: &str, salt_b64: &str) -> Result<[u8; 32], String> {
    let salt = B64.decode(salt_b64.trim()).map_err(|_| "El servidor mandó una sal inválida".to_string())?;
    let phrase: String = phrase.nfc().collect();
    let mut key = [0u8; 32];
    pbkdf2::pbkdf2_hmac::<Sha256>(phrase.as_bytes(), &salt, ROUNDS, &mut key);
    Ok(key)
}

pub fn seal(key: &[u8; 32], plain: &[u8]) -> Result<String, String> {
    let cipher = Aes256Gcm::new_from_slice(key).map_err(|e| e.to_string())?;
    let mut iv = [0u8; 12];
    getrandom::getrandom(&mut iv).map_err(|e| e.to_string())?;
    let ct = cipher.encrypt(Nonce::from_slice(&iv), plain).map_err(|_| "No se pudo cifrar".to_string())?;
    let mut out = Vec::with_capacity(12 + ct.len());
    out.extend_from_slice(&iv);
    out.extend_from_slice(&ct);
    Ok(B64.encode(out))
}

pub fn open(key: &[u8; 32], data: &str) -> Option<Vec<u8>> {
    let raw = B64.decode(data.trim()).ok()?;
    if raw.len() < 12 + 16 {
        return None;
    }
    let cipher = Aes256Gcm::new_from_slice(key).ok()?;
    cipher.decrypt(Nonce::from_slice(&raw[..12]), &raw[12..]).ok()
}

/// A fingerprint of a page's words, to tell "edited here" from "only moved/renamed elsewhere".
pub fn fingerprint(title: &str, body: &str) -> String {
    let mut h = Sha256::new();
    h.update(title.as_bytes());
    h.update([0u8]);
    h.update(body.as_bytes());
    h.finalize().iter().map(|b| format!("{b:02x}")).collect()
}

// ── A page as it travels ──────────────────────────────────────────────────────

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Plain {
    pub id: String,
    pub parent: Option<String>,
    #[serde(default)]
    pub title: String,
    #[serde(default)]
    pub icon: String,
    #[serde(default)]
    pub color: i64,
    #[serde(default)]
    pub shape: i64,
    #[serde(default)]
    pub kind: String,
    #[serde(default)]
    pub ord: f64,
    #[serde(default)]
    pub created: i64,
    #[serde(default)]
    pub updated: i64,
    #[serde(default)]
    pub favorite: bool,
    #[serde(default)]
    pub trashed: i64,
    #[serde(default)]
    pub props: String,
    #[serde(default)]
    pub schema: String,
    #[serde(default)]
    pub body: String,
}

impl Plain {
    pub fn of(r: &Row) -> Plain {
        Plain {
            id: r.id.clone(),
            parent: r.parent.clone(),
            title: r.title.clone(),
            icon: r.icon.clone(),
            color: r.color,
            shape: r.shape,
            kind: r.kind.clone(),
            ord: r.ord,
            created: r.created,
            updated: r.updated,
            favorite: r.favorite,
            trashed: r.trashed,
            props: r.props.clone(),
            schema: r.schema.clone(),
            body: r.body.clone().unwrap_or_default(),
        }
    }

    pub fn row(&self) -> Row {
        let (preview, steps, done) = facts(&self.body);
        Row {
            id: self.id.clone(),
            parent: self.parent.clone(),
            title: self.title.clone(),
            icon: if self.icon.is_empty() { "doc".into() } else { self.icon.clone() },
            color: self.color,
            shape: self.shape,
            kind: if self.kind == "COLLECTION" || self.kind == "INK" { self.kind.clone() } else { "DOC".into() },
            ord: self.ord,
            created: self.created,
            updated: self.updated,
            opened: 0,
            favorite: self.favorite,
            trashed: self.trashed,
            props: self.props.clone(),
            schema: self.schema.clone(),
            preview,
            steps,
            done,
            body: Some(self.body.clone()),
        }
    }
}

/// The preview, steps and ticked steps of a body (Blocks.kt / blocks.ts factsOf).
/// A handwritten page's body is a drawing: no words to show.
pub fn facts(body: &str) -> (String, i64, i64) {
    let mut texts: Vec<String> = Vec::new();
    let (mut steps, mut done) = (0, 0);
    if !body.is_empty() && !body.starts_with("{\"v\":") {
        for l in body.split('\n') {
            if l == "---" || l.starts_with("@@") || l.starts_with("%%") {
                continue;
            }
            let text = if l.starts_with("[x] ") || l.starts_with("[X] ") {
                steps += 1;
                done += 1;
                &l[4..]
            } else if let Some(t) = l.strip_prefix("[ ] ") {
                steps += 1;
                t
            } else if let Some(t) = l.strip_prefix("## ") {
                t
            } else if let Some(t) = l.strip_prefix("# ").or_else(|| l.strip_prefix("> ")).or_else(|| l.strip_prefix("! ")) {
                t
            } else if let Some(t) = l.strip_prefix("• ").or_else(|| l.strip_prefix("- ")).or_else(|| l.strip_prefix("* ")) {
                t
            } else if let Some(rest) = numbered(l) {
                rest
            } else {
                l
            };
            if texts.len() < 3 && !text.trim().is_empty() {
                texts.push(text.trim().to_string());
            }
        }
    }
    let preview: String = texts.join(" · ").chars().take(140).collect();
    (preview, steps, done)
}

fn numbered(l: &str) -> Option<&str> {
    let digits = l.bytes().take_while(|b| b.is_ascii_digit()).count();
    if (1..=3).contains(&digits) && l[digits..].starts_with(". ") {
        Some(&l[digits + 2..])
    } else {
        None
    }
}

// ── The conversation with the server ──────────────────────────────────────────

#[derive(Debug, Clone)]
pub struct Creds {
    pub url: String,
    pub key: String,
    pub phrase: String,
}

#[derive(Debug, Default, Clone, Serialize)]
pub struct Report {
    pub pushed: usize,
    pub pulled: usize,
    pub conflicts: usize,
    /// Pages that changed here because of the server (to refresh the window).
    pub changed: Vec<String>,
    pub at: i64,
}

/// The derived key, kept while the salt and phrase stay the same (PBKDF2 is slow on purpose).
#[derive(Default)]
pub struct KeyCache(Mutex<Option<(String, String, [u8; 32])>>);

impl KeyCache {
    fn get(&self, salt: &str, phrase: &str) -> Result<[u8; 32], String> {
        if let Some((s, p, k)) = self.0.lock().unwrap_or_else(|e| e.into_inner()).as_ref() {
            if s == salt && p == phrase {
                return Ok(*k);
            }
        }
        let k = derive(phrase, salt)?;
        *self.0.lock().unwrap_or_else(|e| e.into_inner()) = Some((salt.to_string(), phrase.to_string(), k));
        Ok(k)
    }
}

/// How requests reach the server: real HTTP in the app, a fake in tests.
pub trait Transport {
    fn post(&self, creds: &Creds, body: Value) -> Result<Value, String>;
}

pub struct Http(pub reqwest::blocking::Client);

impl Http {
    pub fn new() -> Http {
        Http(
            reqwest::blocking::Client::builder()
                .timeout(std::time::Duration::from_secs(40))
                .user_agent(concat!("TokkipNotas/", env!("CARGO_PKG_VERSION")))
                .build()
                .unwrap_or_else(|_| reqwest::blocking::Client::new()),
        )
    }
}

impl Transport for Http {
    fn post(&self, creds: &Creds, body: Value) -> Result<Value, String> {
        let url = creds.url.trim();
        if !url.starts_with("https://") && !url.starts_with("http://localhost") && !url.starts_with("http://127.0.0.1") {
            return Err("La dirección tiene que empezar con https://".into());
        }
        let resp = self
            .0
            .post(url)
            .header("X-Tokkip-Key", creds.key.trim())
            .json(&body)
            .send()
            .map_err(|e| if e.is_timeout() { "Tu servidor tardó demasiado en contestar".to_string() } else { "No pude conectar con tu servidor".to_string() })?;
        let status = resp.status();
        let text = resp.text().map_err(|_| "Tu servidor cortó la respuesta".to_string())?;
        let v: Value = serde_json::from_str(&text).map_err(|_| {
            if status.as_u16() == 404 {
                "No encontré sync.php en esa dirección".to_string()
            } else {
                "Tu servidor no contestó como Tokkip Notas espera (¿es la dirección de sync.php?)".to_string()
            }
        })?;
        if v["ok"].as_bool() != Some(true) {
            return Err(v["error"].as_str().unwrap_or("Tu servidor rechazó la sincronización").to_string());
        }
        Ok(v)
    }
}

/// Checks the phrase against the server's proof (writing the proof if this is the first device).
pub fn unlock(t: &dyn Transport, creds: &Creds, cache: &KeyCache) -> Result<[u8; 32], String> {
    if creds.phrase.trim().chars().count() < 6 {
        return Err("La frase secreta necesita al menos 6 letras".into());
    }
    let hello = t.post(creds, json!({ "action": "hello" }))?;
    let salt = hello["salt"].as_str().ok_or("Tu servidor no tiene sal; vuelve a instalar sync.php")?;
    let key = cache.get(salt, &creds.phrase)?;
    match hello["check"].as_str() {
        Some(check) => {
            if open(&key, check).as_deref() != Some(CHECK.as_bytes()) {
                return Err("La frase secreta no es la misma que en tus otros aparatos".into());
            }
        }
        None => {
            let proof = seal(&key, CHECK.as_bytes())?;
            let back = t.post(creds, json!({ "action": "check", "check": proof }))?;
            // Two devices may race to write it: whoever won, it has to open with this phrase.
            let stored = back["check"].as_str().unwrap_or_default();
            if open(&key, stored).as_deref() != Some(CHECK.as_bytes()) {
                return Err("La frase secreta no es la misma que en tus otros aparatos".into());
            }
        }
    }
    Ok(key)
}

/// One full sync: first everything new from the server (so nothing here is
/// pushed over a change it hasn't seen), then everything waiting here.
pub fn run(db: &Db, t: &dyn Transport, creds: &Creds, cache: &KeyCache) -> Result<Report, String> {
    let key = unlock(t, creds, cache)?;
    let mut report = Report::default();
    let mut since: i64 = db.meta("sync.rev").and_then(|v| v.parse().ok()).unwrap_or(0);
    let e = |e: rusqlite::Error| e.to_string();

    let handle = |resp: &Value, since: &mut i64, report: &mut Report| -> Result<bool, String> {
        for r in resp["rows"].as_array().map(|a| a.as_slice()).unwrap_or_default() {
            apply(db, &key, r, report)?;
        }
        let rev = resp["rev"].as_i64().unwrap_or(*since);
        if rev > *since {
            *since = rev;
            db.set_meta("sync.rev", Some(&since.to_string())).map_err(e)?;
        }
        Ok(resp["more"].as_bool().unwrap_or(false))
    };

    // 1. Pull.
    for _ in 0..1000 {
        let resp = t.post(creds, json!({ "action": "sync", "since": since, "push": [] }))?;
        if !handle(&resp, &mut since, &mut report)? {
            break;
        }
    }

    // 2. Push (what lost on the server comes back and is settled by `apply`).
    for _ in 0..200 {
        let dirty = db.dirty(PUSH_BATCH).map_err(e)?;
        if dirty.is_empty() {
            break;
        }
        let mut push = Vec::with_capacity(dirty.len());
        let mut sent: Vec<(String, i64, Option<String>)> = Vec::with_capacity(dirty.len());
        for (id, ver, deleted, row) in &dirty {
            match (deleted, row) {
                (false, Some(r)) => {
                    let plain = Plain::of(r);
                    let data = seal(&key, &serde_json::to_vec(&plain).map_err(|e| e.to_string())?)?;
                    push.push(json!({ "id": id, "ver": ver, "deleted": false, "data": data }));
                    sent.push((id.clone(), *ver, Some(fingerprint(&plain.title, &plain.body))));
                }
                _ => {
                    push.push(json!({ "id": id, "ver": ver, "deleted": true }));
                    sent.push((id.clone(), *ver, None));
                }
            }
        }
        let resp = t.post(creds, json!({ "action": "sync", "since": since, "push": push }))?;
        let accepted: std::collections::HashSet<&str> =
            resp["accepted"].as_array().map(|a| a.iter().filter_map(|v| v.as_str()).collect()).unwrap_or_default();
        for (id, ver, base) in &sent {
            if accepted.contains(id.as_str()) {
                db.mark_clean(id, *ver, base.as_deref()).map_err(e)?;
                report.pushed += 1;
            }
        }
        let mut more = handle(&resp, &mut since, &mut report)?;
        while more {
            let resp = t.post(creds, json!({ "action": "sync", "since": since, "push": [] }))?;
            more = handle(&resp, &mut since, &mut report)?;
        }
    }
    report.at = now_ms();
    db.set_meta("sync.last", Some(&report.at.to_string())).map_err(e)?;
    Ok(report)
}

/// Settles one page from the server against this device.
///
/// When this device's copy is waiting to go up too, the words (title and text)
/// and the rest (place, icon, values, favourite…) are weighed apart, against
/// what both last agreed on (`base`):
///   - only one side changed the words → those words, with the newer side's rest;
///   - both changed the words → the newer wins, the other is kept as a copy
///     "(conflicto)" (only for pages of text);
///   - a delete loses against new words, and wins against anything else.
fn apply(db: &Db, key: &[u8; 32], r: &Value, report: &mut Report) -> Result<(), String> {
    let e = |e: rusqlite::Error| e.to_string();
    let Some(id) = r["id"].as_str() else { return Ok(()) };
    let ver = r["ver"].as_i64().unwrap_or(0);
    let state = db.sync_state(id).map_err(e)?;
    if let Some(st) = &state {
        if !st.dirty && ver <= st.ver {
            return Ok(()); // already here
        }
    }
    let remote: Option<Plain> = if r["deleted"].as_bool().unwrap_or(false) {
        None
    } else {
        let bytes = open(key, r["data"].as_str().unwrap_or_default()).ok_or("Una página del servidor no abre con tu frase secreta")?;
        let p: Plain = serde_json::from_slice(&bytes).map_err(|_| "Una página del servidor está dañada".to_string())?;
        if p.id != id {
            return Err("Una página del servidor no coincide".into());
        }
        Some(p)
    };
    let remote_fp = remote.as_ref().map(|p| fingerprint(&p.title, &p.body));

    let take = |report: &mut Report| -> Result<(), String> {
        db.apply_remote(remote.as_ref().map(|p| p.row()).as_ref(), id, ver, remote_fp.as_deref()).map_err(e)?;
        report.pulled += 1;
        report.changed.push(id.to_string());
        Ok(())
    };

    let st = match state {
        Some(st) if st.dirty => st,
        _ => return take(report),
    };
    if id == INBOX {
        return if ver >= st.ver { take(report) } else { Ok(()) };
    }
    let local = db.row(id).map_err(e)?;
    let mine_fp = local.as_ref().map(|l| fingerprint(&l.title, l.body.as_deref().unwrap_or_default()));
    let remote_newer = ver >= st.ver;
    let edited_here = mine_fp != st.base;
    let changed_there = remote_fp != st.base;

    if remote_fp == mine_fp {
        // The same words on both sides.
        return if remote_newer { take(report) } else { db.keep(None, id, st.ver, remote_fp.as_deref(), local.is_none()).map_err(e) };
    }
    if !edited_here {
        // Only the rest changed here: their words always come in.
        return match (&local, &remote) {
            (Some(l), Some(p)) if !remote_newer => {
                let merged = with_words(l, &p.title, &p.body);
                db.keep(Some(&merged), id, st.ver, remote_fp.as_deref(), false).map_err(e)?;
                report.pulled += 1;
                report.changed.push(id.to_string());
                Ok(())
            }
            _ => take(report),
        };
    }
    if !changed_there {
        // Only the rest changed there: our words (or our delete) stay.
        if remote_newer {
            match (&local, &remote) {
                (Some(l), Some(p)) => {
                    let mut merged = p.row();
                    merged.opened = l.opened;
                    let merged = with_words(&merged, &l.title, l.body.as_deref().unwrap_or_default());
                    db.keep(Some(&merged), id, ver + 1, st.base.as_deref(), false).map_err(e)?;
                    report.pulled += 1;
                    report.changed.push(id.to_string());
                }
                _ => db.keep(None, id, ver + 1, st.base.as_deref(), local.is_none()).map_err(e)?,
            }
        }
        return Ok(());
    }
    // The words changed on both sides.
    match (&local, &remote) {
        (None, Some(_)) => take(report), // new words beat our delete
        (Some(_), None) => db.keep(None, id, st.ver.max(ver + 1), None, false).map_err(e), // ours beat their delete
        (Some(l), Some(p)) => {
            if remote_newer {
                if l.kind != "COLLECTION" {
                    keep_copy(db, l, report)?;
                }
                take(report)
            } else {
                if p.kind != "COLLECTION" {
                    keep_copy(db, &p.row(), report)?;
                }
                db.keep(None, id, st.ver, remote_fp.as_deref(), false).map_err(e)
            }
        }
        (None, None) => Ok(()),
    }
}

/// A page with other words (and its preview and steps to match).
fn with_words(r: &Row, title: &str, body: &str) -> Row {
    let (preview, steps, done) = facts(body);
    Row { title: title.to_string(), body: Some(body.to_string()), preview, steps, done, ..r.clone() }
}

/// The losing words, kept beside the page as "… (conflicto)".
fn keep_copy(db: &Db, r: &Row, report: &mut Report) -> Result<(), String> {
    let body = r.body.clone().unwrap_or_default();
    if r.title.trim().is_empty() && body.trim().is_empty() {
        return Ok(());
    }
    let mut copy = r.clone();
    copy.id = new_id();
    copy.title = format!("{} (conflicto)", if r.title.trim().is_empty() { "Sin título" } else { r.title.trim() });
    copy.ord = r.ord + 0.001;
    copy.updated = now_ms();
    copy.favorite = false;
    copy.trashed = 0;
    copy.opened = 0;
    db.upsert(&[copy.clone()], false).map_err(|e| e.to_string())?;
    report.conflicts += 1;
    report.changed.push(copy.id);
    Ok(())
}

pub fn new_id() -> String {
    let mut b = [0u8; 8];
    let _ = getrandom::getrandom(&mut b);
    b.iter().map(|x| format!("{x:02x}")).collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::cell::RefCell;
    use std::collections::BTreeMap;

    /// The server's rules (sync.php), in memory.
    #[derive(Default)]
    struct Fake {
        rows: RefCell<BTreeMap<String, (i64, bool, String, i64)>>,
        rev: RefCell<i64>,
        check: RefCell<Option<String>>,
    }

    impl Transport for Fake {
        fn post(&self, _c: &Creds, body: Value) -> Result<Value, String> {
            match body["action"].as_str().unwrap() {
                "hello" => Ok(json!({"ok": true, "salt": "c2FsdHNhbHRzYWx0c2FsdA==", "check": *self.check.borrow(), "rev": *self.rev.borrow()})),
                "check" => {
                    if self.check.borrow().is_none() {
                        *self.check.borrow_mut() = Some(body["check"].as_str().unwrap().to_string());
                    }
                    Ok(json!({"ok": true, "check": *self.check.borrow()}))
                }
                _ => {
                    let since = body["since"].as_i64().unwrap();
                    let mut accepted = vec![];
                    let mut rejected = vec![];
                    for p in body["push"].as_array().unwrap() {
                        let id = p["id"].as_str().unwrap().to_string();
                        let ver = p["ver"].as_i64().unwrap();
                        let stored = self.rows.borrow().get(&id).map(|r| r.0);
                        if stored.is_some_and(|s| s >= ver) {
                            rejected.push(id);
                            continue;
                        }
                        *self.rev.borrow_mut() += 1;
                        let del = p["deleted"].as_bool().unwrap();
                        let data = if del { String::new() } else { p["data"].as_str().unwrap().to_string() };
                        self.rows.borrow_mut().insert(id.clone(), (ver, del, data, *self.rev.borrow()));
                        accepted.push(id);
                    }
                    let rows = self.rows.borrow();
                    let mut out: Vec<_> = rows.iter().filter(|r| r.1 .3 > since).collect();
                    out.sort_by_key(|r| r.1 .3);
                    let rev = out.last().map(|r| r.1 .3).unwrap_or(since);
                    let mut list: Vec<Value> = out.iter().map(|(id, r)| json!({"id": id, "ver": r.0, "deleted": r.1, "data": r.2, "rev": r.3})).collect();
                    for id in rejected {
                        if !out.iter().any(|r| *r.0 == id) {
                            let r = &rows[&id];
                            list.push(json!({"id": id, "ver": r.0, "deleted": r.1, "data": r.2, "rev": r.3}));
                        }
                    }
                    Ok(json!({"ok": true, "accepted": accepted, "rows": list, "rev": rev, "more": false}))
                }
            }
        }
    }

    fn creds(phrase: &str) -> Creds {
        Creds { url: "https://x".into(), key: "k".into(), phrase: phrase.into() }
    }

    fn page(id: &str, title: &str, body: &str, updated: i64) -> Row {
        Plain { id: id.into(), parent: None, title: title.into(), icon: "doc".into(), color: 1, shape: 0, kind: "DOC".into(), ord: 0.0, created: 1, updated, favorite: false, trashed: 0, props: String::new(), schema: String::new(), body: body.into() }.row()
    }

    #[test]
    fn crypto_round_trip_and_facts() {
        let k = derive("mi frase secreta", "c2FsdHNhbHRzYWx0c2FsdA==").unwrap();
        let sealed = seal(&k, b"hola").unwrap();
        assert_eq!(open(&k, &sealed).unwrap(), b"hola");
        let other = derive("otra frase", "c2FsdHNhbHRzYWx0c2FsdA==").unwrap();
        assert!(open(&other, &sealed).is_none());
        // NFC: the same phrase typed with a combining accent gives the same key.
        assert_eq!(derive("canción", "c2FsdHNhbHRzYWx0c2FsdA==").unwrap(), derive("cancio\u{301}n", "c2FsdHNhbHRzYWx0c2FsdA==").unwrap());
        assert_eq!(facts("Hola\n[ ] uno\n[x] dos\n@@kid\n---\n%%{\"v\":1}\n12. tres"), ("Hola · uno · dos".to_string(), 2, 1));
        assert_eq!(facts("{\"v\":1,\"bg\":\"lines\",\"h\":1414,\"s\":[]}"), (String::new(), 0, 0));
        // The same vector as Android's SyncCoreTest: both sides derive the same key.
        let k = derive("canción secreta", "c2FsdHNhbHRzYWx0c2FsdA==").unwrap();
        assert_eq!(k.iter().map(|b| format!("{b:02x}")).collect::<String>(), "d06abd18ac1ee5945d614aacf04a8bff3adf1a4d4dbf7728caac48f3e69d36f8");
        assert_eq!(fingerprint("Título", "uno\ndos"), "ccb41c9f1731c30fb44030e16847abdd466092d3093768aa37293be77eb5dfea");
    }

    #[test]
    fn two_devices_meet_on_the_server() {
        let server = Fake::default();
        let (pc, phone) = (Db::memory(), Db::memory());
        let (kc1, kc2) = (KeyCache::default(), KeyCache::default());
        let c = creds("frase de prueba");
        pc.upsert(&[page("a", "Lista del súper", "[ ] pan", 100)], false).unwrap();
        phone.upsert(&[page("b", "Idea", "una app", 100)], false).unwrap();
        let r1 = run(&pc, &server, &c, &kc1).unwrap();
        assert_eq!(r1.pushed, 1);
        let r2 = run(&phone, &server, &c, &kc2).unwrap();
        assert_eq!((r2.pushed, r2.pulled), (1, 1));
        assert_eq!(phone.body("a").unwrap(), "[ ] pan");
        run(&pc, &server, &c, &kc1).unwrap();
        assert_eq!(pc.body("b").unwrap(), "una app");
        assert_eq!(pc.row("b").unwrap().unwrap().preview, "una app");

        // An edit on the phone, a delete on the PC.
        phone.upsert(&[page("a", "Lista del súper", "[x] pan", 200)], false).unwrap();
        pc.remove(&["b".into()]).unwrap();
        run(&phone, &server, &c, &kc2).unwrap();
        run(&pc, &server, &c, &kc1).unwrap();
        run(&phone, &server, &c, &kc2).unwrap();
        assert_eq!(pc.body("a").unwrap(), "[x] pan");
        assert!(phone.row("b").unwrap().is_none());
        assert_eq!(pc.dirty_count() + phone.dirty_count(), 0);

        // Both edit the same page offline: the later wins, the other stays as a copy.
        pc.upsert(&[page("a", "Lista del súper", "[x] pan\n[ ] leche", 300)], false).unwrap();
        std::thread::sleep(std::time::Duration::from_millis(5));
        phone.upsert(&[page("a", "Lista del súper", "[x] pan\n[ ] huevos", 300)], false).unwrap();
        run(&phone, &server, &c, &kc2).unwrap();
        let r = run(&pc, &server, &c, &kc1).unwrap();
        assert_eq!(r.conflicts, 1);
        assert_eq!(pc.body("a").unwrap(), "[x] pan\n[ ] huevos");
        let all = pc.dump().unwrap();
        let copy = all.iter().find(|p| p.title == "Lista del súper (conflicto)").unwrap();
        assert_eq!(copy.body.as_deref(), Some("[x] pan\n[ ] leche"));
        run(&phone, &server, &c, &kc2).unwrap();
        assert!(phone.dump().unwrap().iter().any(|p| p.title.ends_with("(conflicto)")));

        // A favourite toggled here after the text changed there: both arrive, no copy.
        phone.upsert(&[page("a", "Lista del súper", "[x] pan\n[x] huevos", 400)], false).unwrap();
        run(&phone, &server, &c, &kc2).unwrap();
        std::thread::sleep(std::time::Duration::from_millis(5));
        let mut fav = pc.row("a").unwrap().unwrap();
        fav.favorite = true;
        pc.upsert(&[fav], false).unwrap();
        let before = pc.dump().unwrap().len();
        let r = run(&pc, &server, &c, &kc1).unwrap();
        assert_eq!(r.conflicts, 0);
        assert_eq!(pc.dump().unwrap().len(), before);
        assert_eq!(pc.body("a").unwrap(), "[x] pan\n[x] huevos");
        run(&phone, &server, &c, &kc2).unwrap();
        assert!(phone.row("a").unwrap().unwrap().favorite);
        assert_eq!(phone.body("a").unwrap(), "[x] pan\n[x] huevos");

        // Text edited here, the page moved there (later): our words in its new place.
        pc.upsert(&[page("a", "Lista del súper", "[x] pan\n[x] huevos\n[ ] sal", 500)], false).unwrap();
        std::thread::sleep(std::time::Duration::from_millis(5));
        let mut moved = phone.row("a").unwrap().unwrap();
        moved.ord = 9.0;
        phone.upsert(&[moved], false).unwrap();
        run(&phone, &server, &c, &kc2).unwrap();
        let r = run(&pc, &server, &c, &kc1).unwrap();
        assert_eq!(r.conflicts, 0);
        run(&phone, &server, &c, &kc2).unwrap();
        for db in [&pc, &phone] {
            let a = db.row("a").unwrap().unwrap();
            assert_eq!((a.ord, a.body.as_deref()), (9.0, Some("[x] pan\n[x] huevos\n[ ] sal")));
        }

        // Deleted here, written there: the writing comes back.
        pc.remove(&["a".into()]).unwrap();
        std::thread::sleep(std::time::Duration::from_millis(5));
        phone.upsert(&[page("a", "Lista del súper", "nueva lista", 600)], false).unwrap();
        run(&phone, &server, &c, &kc2).unwrap();
        run(&pc, &server, &c, &kc1).unwrap();
        assert_eq!(pc.body("a").unwrap(), "nueva lista");
        assert_eq!(pc.dirty_count() + phone.dirty_count(), 0);
    }

    #[test]
    fn a_wrong_phrase_is_refused() {
        let server = Fake::default();
        let db = Db::memory();
        run(&db, &server, &creds("la buena frase"), &KeyCache::default()).unwrap();
        let err = run(&Db::memory(), &server, &creds("otra frase mala"), &KeyCache::default()).unwrap_err();
        assert!(err.contains("frase secreta"));
        assert!(run(&db, &server, &creds("corta"), &KeyCache::default()).is_err());
    }

    #[test]
    fn joining_sends_what_was_already_here() {
        let server = Fake::default();
        let c = creds("frase de prueba");
        let db = Db::memory();
        db.upsert(&[page("x", "Vieja", "texto", 50)], true).unwrap();
        assert_eq!(db.dirty_count(), 0);
        db.mark_all_for_join().unwrap();
        let r = run(&db, &server, &c, &KeyCache::default()).unwrap();
        assert_eq!(r.pushed, 1);
        // The same copy imported on another device: no conflict, nothing duplicated.
        let other = Db::memory();
        other.upsert(&[page("x", "Vieja", "texto", 50)], true).unwrap();
        other.mark_all_for_join().unwrap();
        let r = run(&other, &server, &c, &KeyCache::default()).unwrap();
        assert_eq!((r.pushed, r.conflicts), (0, 0));
        assert_eq!(other.dirty_count(), 0);
        assert_eq!(other.dump().unwrap().len(), 1);
    }

    /// Against a real sync.php: TOKKIP_SYNC_URL and TOKKIP_SYNC_KEY (cargo test -- --ignored).
    #[test]
    #[ignore]
    fn against_a_real_server() {
        let url = std::env::var("TOKKIP_SYNC_URL").unwrap();
        let key = std::env::var("TOKKIP_SYNC_KEY").unwrap();
        let c = Creds { url, key, phrase: "frase de prueba real".into() };
        let http = Http::new();
        let (a, b) = (Db::memory(), Db::memory());
        let (ka, kb) = (KeyCache::default(), KeyCache::default());
        a.upsert(&[page("real-1", "Desde la PC", "[ ] probar sync", 10)], false).unwrap();
        b.upsert(&[page("real-2", "Desde el teléfono", "hola", 10)], false).unwrap();
        run(&a, &http, &c, &ka).unwrap();
        run(&b, &http, &c, &kb).unwrap();
        run(&a, &http, &c, &ka).unwrap();
        assert_eq!(a.body("real-2").unwrap(), "hola");
        assert_eq!(b.body("real-1").unwrap(), "[ ] probar sync");
        a.remove(&["real-1".into()]).unwrap();
        run(&a, &http, &c, &ka).unwrap();
        run(&b, &http, &c, &kb).unwrap();
        assert!(b.row("real-1").unwrap().is_none());
        let wrong = Creds { phrase: "otra frase cualquiera".into(), ..c.clone() };
        assert!(run(&Db::memory(), &http, &wrong, &KeyCache::default()).unwrap_err().contains("frase"));
        let bad = Creds { key: "mala".into(), ..c };
        assert!(run(&Db::memory(), &http, &bad, &KeyCache::default()).unwrap_err().contains("clave"));
    }
}

