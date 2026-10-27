// Tokkip's smart clipboard: what you copy (text, images, files) and what you
// drop on the island, kept on a shelf you can drag things back out of.
//
// Everything stays on this PC, in %LOCALAPPDATA%\Tokkip\clipboard: an index
// (index.json) and the images as PNG. Nothing reaches the network.
//
// What password managers mark as private (ExcludeClipboardContentFromMonitor-
// Processing, CanIncludeInClipboardHistory, Clipboard Viewer Ignore) is never
// read, and capture pauses with the tray's Pause or the switch on the card.

use std::path::{Path, PathBuf};
use std::sync::atomic::Ordering;
use std::sync::{LazyLock, Mutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter};

use crate::files::{self, DroppedFile};
use crate::island::WINDOW_LABEL;
use crate::settings;

/// Unpinned items kept; the oldest go first. Pinned ones never go on their own.
const MAX_ITEMS: usize = 80;
/// Characters of a text item kept (a whole document copied by mistake stays small).
const MAX_TEXT: usize = 20_000;
/// Characters of a text item sent to the island for its tile.
const PREVIEW: usize = 600;
/// Thumbnails fit in this square.
const THUMB: u32 = 180;
/// Bigger images (a 8K screenshot is 33 Mpx) are left alone.
const MAX_IMAGE_PIXELS: u64 = 40_000_000;

#[cfg(windows)]
const POLL: Duration = Duration::from_millis(600);
/// Without a sequence number to watch, Linux reads the clipboard each time.
#[cfg(not(windows))]
const POLL: Duration = Duration::from_millis(1500);

#[derive(Serialize, Deserialize, Clone, Default)]
#[serde(rename_all = "camelCase")]
struct Clip {
    id: u64,
    /// "text", "image" or "files".
    kind: String,
    #[serde(default)]
    text: String,
    #[serde(default)]
    files: Vec<String>,
    /// The PNG's file name in the clipboard folder.
    #[serde(default)]
    image: String,
    #[serde(default)]
    width: u32,
    #[serde(default)]
    height: u32,
    #[serde(default)]
    hash: u64,
    /// When it was last copied (ms since the epoch): the shelf is in this order.
    created: u64,
    #[serde(default)]
    pinned: bool,
    /// "copy" or "drop".
    #[serde(default)]
    source: String,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Shelf {
    #[serde(default = "yes")]
    capture: bool,
    #[serde(default)]
    next_id: u64,
    #[serde(default)]
    items: Vec<Clip>,
}

fn yes() -> bool {
    true
}

impl Default for Shelf {
    fn default() -> Self {
        Shelf { capture: true, next_id: 0, items: Vec::new() }
    }
}

static SHELF: LazyLock<Mutex<Shelf>> = LazyLock::new(|| Mutex::new(load()));

fn dir() -> PathBuf {
    settings::local_dir().join("clipboard")
}

fn index_path() -> PathBuf {
    dir().join("index.json")
}

fn load() -> Shelf {
    std::fs::read(index_path())
        .ok()
        .and_then(|bytes| serde_json::from_slice::<Shelf>(&bytes).ok())
        .unwrap_or_default()
}

fn save(shelf: &Shelf) {
    let folder = dir();
    if crate::platform::ensure_private_dir(&settings::local_dir()).is_err() || std::fs::create_dir_all(&folder).is_err() {
        return;
    }
    let Ok(json) = serde_json::to_vec(shelf) else { return };
    let tmp = folder.join(format!("index.json.{}", std::process::id()));
    if std::fs::write(&tmp, json).is_ok() {
        let _ = std::fs::rename(&tmp, index_path());
    }
}

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or(0)
}

/// FNV-1a: enough to tell two copies apart, nothing more.
fn fnv(parts: &[&[u8]]) -> u64 {
    let mut h: u64 = 0xcbf29ce484222325;
    for part in parts {
        for b in *part {
            h ^= *b as u64;
            h = h.wrapping_mul(0x100000001b3);
        }
        h ^= 0xff;
        h = h.wrapping_mul(0x100000001b3);
    }
    h
}

fn truncate(text: &str, max: usize) -> String {
    match text.char_indices().nth(max) {
        Some((i, _)) => text[..i].to_string(),
        None => text.to_string(),
    }
}

// ── What the island sees ──────────────────────────────────────────────────────

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ClipFile {
    name: String,
    path: String,
    dir: bool,
    ext: String,
    exists: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ClipView {
    id: u64,
    kind: String,
    /// The start of a text item.
    text: String,
    /// More text than `text` shows.
    long: bool,
    files: Vec<ClipFile>,
    /// A small PNG as a data: URL, for images.
    thumb: Option<String>,
    width: u32,
    height: u32,
    created: u64,
    pinned: bool,
    source: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ClipState {
    capture: bool,
    items: Vec<ClipView>,
}

fn base64(bytes: &[u8]) -> String {
    const T: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut out = String::with_capacity(bytes.len().div_ceil(3) * 4);
    for chunk in bytes.chunks(3) {
        let b = [chunk[0], *chunk.get(1).unwrap_or(&0), *chunk.get(2).unwrap_or(&0)];
        let n = (b[0] as u32) << 16 | (b[1] as u32) << 8 | b[2] as u32;
        out.push(T[(n >> 18) as usize & 63] as char);
        out.push(T[(n >> 12) as usize & 63] as char);
        out.push(if chunk.len() > 1 { T[(n >> 6) as usize & 63] as char } else { '=' });
        out.push(if chunk.len() > 2 { T[n as usize & 63] as char } else { '=' });
    }
    out
}

fn thumb_name(image: &str) -> String {
    image.replace(".png", "-t.png")
}

fn view(clip: &Clip) -> ClipView {
    let files = clip
        .files
        .iter()
        .map(|p| {
            let path = Path::new(p);
            let meta = std::fs::metadata(path).ok();
            ClipFile {
                name: path.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_else(|| p.clone()),
                path: p.clone(),
                dir: meta.as_ref().is_some_and(|m| m.is_dir()),
                ext: path.extension().map(|e| e.to_string_lossy().to_lowercase()).unwrap_or_default(),
                exists: meta.is_some(),
            }
        })
        .collect();
    let thumb = (clip.kind == "image")
        .then(|| std::fs::read(dir().join(thumb_name(&clip.image))).ok())
        .flatten()
        .map(|png| format!("data:image/png;base64,{}", base64(&png)));
    ClipView {
        id: clip.id,
        kind: clip.kind.clone(),
        text: truncate(&clip.text, PREVIEW),
        long: clip.text.chars().nth(PREVIEW).is_some(),
        files,
        thumb,
        width: clip.width,
        height: clip.height,
        created: clip.created,
        pinned: clip.pinned,
        source: clip.source.clone(),
    }
}

fn state() -> ClipState {
    let shelf = SHELF.lock().unwrap_or_else(|e| e.into_inner());
    ClipState { capture: shelf.capture, items: shelf.items.iter().map(view).collect() }
}

fn changed(app: &AppHandle) {
    let _ = app.emit_to(WINDOW_LABEL, "clipboard-changed", ());
}

// ── Reading and adding ────────────────────────────────────────────────────────

enum Captured {
    Text(String),
    Files(Vec<PathBuf>),
    Image { width: u32, height: u32, rgba: Vec<u8> },
}

/// What is on the clipboard now: files first (Explorer), then text (an app's
/// own copy often carries a picture of it too: Word, Excel), then an image.
fn read_clipboard() -> Option<Captured> {
    let mut cb = arboard::Clipboard::new().ok()?;
    if let Ok(files) = cb.get().file_list() {
        if !files.is_empty() {
            return Some(Captured::Files(files));
        }
    }
    if let Ok(text) = cb.get_text() {
        if !text.trim().is_empty() {
            return Some(Captured::Text(text));
        }
    }
    if let Ok(img) = cb.get_image() {
        let (w, h) = (img.width as u64, img.height as u64);
        if w > 0 && h > 0 && w * h <= MAX_IMAGE_PIXELS {
            return Some(Captured::Image { width: w as u32, height: h as u32, rgba: img.bytes.into_owned() });
        }
    }
    None
}

fn hash_of(c: &Captured) -> (String, u64) {
    match c {
        Captured::Text(t) => ("text".into(), fnv(&[b"text", t.as_bytes()])),
        Captured::Files(f) => {
            let joined = f.iter().map(|p| p.to_string_lossy()).collect::<Vec<_>>().join("\n");
            ("files".into(), fnv(&[b"files", joined.as_bytes()]))
        }
        Captured::Image { width, height, rgba } => (
            "image".into(),
            fnv(&[b"image", &width.to_le_bytes(), &height.to_le_bytes(), rgba]),
        ),
    }
}

/// Puts a capture on the shelf (or brings the same thing back to the top).
/// Returns false when nothing changed.
fn add(c: Captured, source: &str) -> bool {
    let (kind, hash) = hash_of(&c);
    let now = now_ms();
    {
        let mut shelf = SHELF.lock().unwrap_or_else(|e| e.into_inner());
        if let Some(pos) = shelf.items.iter().position(|i| i.kind == kind && i.hash == hash) {
            let mut item = shelf.items.remove(pos);
            item.created = now;
            shelf.items.insert(0, item);
            save(&shelf);
            return true;
        }
    }

    let mut clip = Clip { kind, hash, created: now, source: source.into(), ..Default::default() };
    match c {
        Captured::Text(t) => clip.text = truncate(&t, MAX_TEXT),
        Captured::Files(f) => clip.files = f.iter().map(|p| p.to_string_lossy().to_string()).collect(),
        Captured::Image { width, height, rgba } => {
            // Encoded outside the lock: a big screenshot takes a moment.
            let name = format!("img-{hash:016x}.png");
            if !write_png(&name, width, height, rgba) {
                return false;
            }
            clip.image = name;
            clip.width = width;
            clip.height = height;
        }
    }

    let mut shelf = SHELF.lock().unwrap_or_else(|e| e.into_inner());
    shelf.next_id += 1;
    clip.id = shelf.next_id;
    shelf.items.insert(0, clip);
    prune(&mut shelf);
    save(&shelf);
    true
}

fn write_png(name: &str, width: u32, height: u32, rgba: Vec<u8>) -> bool {
    let folder = dir();
    if crate::platform::ensure_private_dir(&settings::local_dir()).is_err() || std::fs::create_dir_all(&folder).is_err() {
        return false;
    }
    let Some(img) = image::RgbaImage::from_raw(width, height, rgba) else { return false };
    if img.save(folder.join(name)).is_err() {
        return false;
    }
    let scale = (THUMB as f32 / width.max(height) as f32).min(1.0);
    let (tw, th) = (((width as f32 * scale).round() as u32).max(1), ((height as f32 * scale).round() as u32).max(1));
    let thumb = image::imageops::thumbnail(&img, tw, th);
    thumb.save(folder.join(thumb_name(name))).is_ok()
}

/// Keeps MAX_ITEMS unpinned items, newest first; an image's files go with it.
fn prune(shelf: &mut Shelf) {
    let mut unpinned = 0;
    let mut gone = Vec::new();
    shelf.items.retain(|item| {
        if item.pinned {
            return true;
        }
        unpinned += 1;
        if unpinned > MAX_ITEMS {
            gone.push(item.image.clone());
            return false;
        }
        true
    });
    for image in gone {
        remove_image(&image);
    }
}

fn remove_image(image: &str) {
    if image.is_empty() || image.contains(['/', '\\']) {
        return;
    }
    let _ = std::fs::remove_file(dir().join(image));
    let _ = std::fs::remove_file(dir().join(thumb_name(image)));
}

// ── Watching the clipboard ────────────────────────────────────────────────────

#[cfg(windows)]
fn sequence() -> u32 {
    unsafe { windows::Win32::System::DataExchange::GetClipboardSequenceNumber() }
}

/// What a password manager (or any app) asked clipboard watchers to leave alone.
#[cfg(windows)]
fn private_copy() -> bool {
    use windows::core::w;
    use windows::Win32::System::DataExchange::{IsClipboardFormatAvailable, RegisterClipboardFormatW};
    unsafe {
        [
            w!("ExcludeClipboardContentFromMonitorProcessing"),
            w!("CanIncludeInClipboardHistory"),
            w!("Clipboard Viewer Ignore"),
        ]
        .into_iter()
        .any(|name| {
            let format = RegisterClipboardFormatW(name);
            format != 0 && IsClipboardFormatAvailable(format).is_ok()
        })
    }
}

/// Starts watching. Whatever is on the clipboard at launch is not taken.
pub fn start(app: AppHandle) {
    std::thread::spawn(move || {
        #[cfg(windows)]
        let mut last = sequence();
        #[cfg(not(windows))]
        let mut last: u64 = read_clipboard().map(|c| hash_of(&c).1).unwrap_or(0);
        loop {
            std::thread::sleep(POLL);
            if crate::integrations::PAUSED.load(Ordering::Relaxed) {
                continue;
            }
            if !SHELF.lock().unwrap_or_else(|e| e.into_inner()).capture {
                continue;
            }
            #[cfg(windows)]
            {
                let now = sequence();
                if now == last {
                    continue;
                }
                last = now;
                if private_copy() {
                    continue;
                }
            }
            let Some(captured) = read_clipboard() else { continue };
            #[cfg(not(windows))]
            {
                let h = hash_of(&captured).1;
                if h == last {
                    continue;
                }
                last = h;
            }
            if add(captured, "copy") {
                changed(&app);
            }
        }
    });
}

// ── Commands ──────────────────────────────────────────────────────────────────

fn find(id: u64) -> Option<Clip> {
    SHELF.lock().unwrap_or_else(|e| e.into_inner()).items.iter().find(|i| i.id == id).cloned()
}

#[tauri::command]
pub fn clipboard_state() -> ClipState {
    state()
}

#[tauri::command]
pub fn clipboard_set_capture(app: AppHandle, on: bool) {
    let mut shelf = SHELF.lock().unwrap_or_else(|e| e.into_inner());
    shelf.capture = on;
    save(&shelf);
    drop(shelf);
    changed(&app);
}

/// Puts an item back on the system clipboard (and at the top of the shelf).
#[tauri::command]
pub fn clipboard_copy(app: AppHandle, id: u64) -> Result<(), String> {
    let clip = find(id).ok_or("gone")?;
    let mut cb = arboard::Clipboard::new().map_err(|e| e.to_string())?;
    match clip.kind.as_str() {
        "text" => cb.set_text(clip.text.clone()).map_err(|e| e.to_string())?,
        "files" => {
            let paths: Vec<&String> = clip.files.iter().filter(|p| Path::new(p).exists()).collect();
            if paths.is_empty() {
                return Err(crate::i18n::t("The file is no longer there."));
            }
            cb.set().file_list(&paths).map_err(|e| e.to_string())?
        }
        "image" => {
            let img = image::open(dir().join(&clip.image)).map_err(|e| e.to_string())?.to_rgba8();
            let (width, height) = img.dimensions();
            cb.set_image(arboard::ImageData {
                width: width as usize,
                height: height as usize,
                bytes: std::borrow::Cow::Owned(img.into_raw()),
            })
            .map_err(|e| e.to_string())?
        }
        _ => return Err("unknown".into()),
    }
    {
        let mut shelf = SHELF.lock().unwrap_or_else(|e| e.into_inner());
        if let Some(pos) = shelf.items.iter().position(|i| i.id == id) {
            let mut item = shelf.items.remove(pos);
            item.created = now_ms();
            shelf.items.insert(0, item);
            save(&shelf);
        }
    }
    changed(&app);
    Ok(())
}

#[tauri::command]
pub fn clipboard_pin(app: AppHandle, id: u64, pinned: bool) {
    let mut shelf = SHELF.lock().unwrap_or_else(|e| e.into_inner());
    if let Some(item) = shelf.items.iter_mut().find(|i| i.id == id) {
        item.pinned = pinned;
    }
    prune(&mut shelf);
    save(&shelf);
    drop(shelf);
    changed(&app);
}

#[tauri::command]
pub fn clipboard_delete(app: AppHandle, id: u64) {
    let mut shelf = SHELF.lock().unwrap_or_else(|e| e.into_inner());
    if let Some(pos) = shelf.items.iter().position(|i| i.id == id) {
        let item = shelf.items.remove(pos);
        remove_image(&item.image);
    }
    save(&shelf);
    drop(shelf);
    changed(&app);
}

/// Empties the shelf except what is pinned.
#[tauri::command]
pub fn clipboard_clear(app: AppHandle) {
    let mut shelf = SHELF.lock().unwrap_or_else(|e| e.into_inner());
    let gone: Vec<String> = shelf.items.iter().filter(|i| !i.pinned).map(|i| i.image.clone()).collect();
    shelf.items.retain(|i| i.pinned);
    save(&shelf);
    drop(shelf);
    for image in gone {
        remove_image(&image);
    }
    changed(&app);
}

/// Files dropped on the island go on the shelf. Only paths a real drop just
/// delivered are taken (files.rs), never a path the page makes up.
#[tauri::command]
pub fn clipboard_add_dropped(app: AppHandle, paths: Vec<String>) -> usize {
    let ok: Vec<PathBuf> = paths
        .into_iter()
        .filter(|p| files::was_dropped(p) && Path::new(p).is_absolute() && Path::new(p).exists())
        .map(PathBuf::from)
        .collect();
    let n = ok.len();
    if n > 0 && add(Captured::Files(ok), "drop") {
        changed(&app);
    }
    n
}

/// Text dropped on the island (a selection dragged from a browser or an editor).
#[tauri::command]
pub fn clipboard_add_text(app: AppHandle, text: String) -> bool {
    if text.trim().is_empty() {
        return false;
    }
    let added = add(Captured::Text(text), "drop");
    if added {
        changed(&app);
    }
    added
}

/// The full text of a text item (the tile only has its start).
#[tauri::command]
pub fn clipboard_text(id: u64) -> Option<String> {
    find(id).filter(|c| c.kind == "text").map(|c| c.text)
}

/// Shows a file item in Explorer, an image in its folder, or opens a link.
#[tauri::command]
pub fn clipboard_open(id: u64) {
    let Some(clip) = find(id) else { return };
    match clip.kind.as_str() {
        "files" => {
            if let Some(first) = clip.files.first() {
                let p = Path::new(first);
                let folder = if p.is_dir() { p.to_path_buf() } else { p.parent().map(Path::to_path_buf).unwrap_or_default() };
                if folder.is_dir() {
                    crate::platform::reveal_folder(&folder.to_string_lossy());
                }
            }
        }
        "image" => crate::platform::reveal_folder(&dir().to_string_lossy()),
        "text" => {
            let t = clip.text.trim();
            if (t.starts_with("https://") || t.starts_with("http://")) && !t.contains(char::is_whitespace) {
                crate::platform::open_url(t);
            }
        }
        _ => {}
    }
}

/// An item handed to the chat as a file in the inbox: a copy of the file or
/// the image, or the text written out as a .txt.
#[tauri::command]
pub fn clipboard_for_chat(id: u64) -> Result<DroppedFile, String> {
    let clip = find(id).ok_or("gone")?;
    let path = match clip.kind.as_str() {
        "image" => dir().join(&clip.image),
        "files" => clip.files.iter().map(PathBuf::from).find(|p| p.is_file()).ok_or_else(|| crate::i18n::t("The file is no longer there."))?,
        "text" => {
            let folder = dir();
            crate::platform::ensure_private_dir(&settings::local_dir()).map_err(|e| e.to_string())?;
            std::fs::create_dir_all(&folder).map_err(|e| e.to_string())?;
            let note = folder.join(format!("texto-{id}.txt"));
            std::fs::write(&note, clip.text.as_bytes()).map_err(|e| e.to_string())?;
            note
        }
        _ => return Err("unknown".into()),
    };
    files::ingest_trusted(&path.to_string_lossy())
}

/// Starts dragging an item out of the island, as a real file drag: drop it in
/// Explorer, a chat app, an email… Windows only; text is dragged by the page.
#[tauri::command]
pub fn clipboard_drag(app: AppHandle, id: u64) -> bool {
    let Some(clip) = find(id) else { return false };
    let paths: Vec<PathBuf> = match clip.kind.as_str() {
        "files" => clip.files.iter().map(PathBuf::from).filter(|p| p.exists()).collect(),
        "image" => vec![dir().join(&clip.image)],
        _ => Vec::new(),
    };
    if paths.is_empty() {
        return false;
    }
    #[cfg(windows)]
    {
        let Some(win) = crate::island::window(&app) else { return false };
        let picture = if clip.kind == "image" {
            drag::Image::File(dir().join(thumb_name(&clip.image)))
        } else {
            drag::Image::Raw(include_bytes!("../icons/32x32.png").to_vec())
        };
        let started = app.run_on_main_thread(move || {
            if let Err(err) = drag::start_drag(&win, drag::DragItem::Files(paths), picture, |_, _| {}, drag::Options::default()) {
                crate::log::line(format!("clipboard drag failed: {err}"));
            }
        });
        started.is_ok()
    }
    #[cfg(not(windows))]
    {
        let _ = (app, paths);
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn base64_matches_the_standard() {
        assert_eq!(base64(b""), "");
        assert_eq!(base64(b"f"), "Zg==");
        assert_eq!(base64(b"fo"), "Zm8=");
        assert_eq!(base64(b"foo"), "Zm9v");
        assert_eq!(base64(b"foobar"), "Zm9vYmFy");
    }

    #[test]
    fn text_is_cut_on_a_character_boundary() {
        assert_eq!(truncate("añoñaño", 3), "año");
        assert_eq!(truncate("hi", 10), "hi");
    }

    #[test]
    fn the_shelf_keeps_its_pins_and_its_newest() {
        let mut shelf = Shelf::default();
        for i in 0..(MAX_ITEMS as u64 + 5) {
            shelf.items.push(Clip { id: i, kind: "text".into(), pinned: i == MAX_ITEMS as u64 + 4, ..Default::default() });
        }
        prune(&mut shelf);
        assert_eq!(shelf.items.iter().filter(|i| !i.pinned).count(), MAX_ITEMS);
        assert!(shelf.items.iter().any(|i| i.pinned), "the pinned one stays even at the end");
        assert_eq!(shelf.items[0].id, 0, "the newest stay");
    }

    #[test]
    fn the_same_copy_has_the_same_hash() {
        let a = hash_of(&Captured::Text("hola".into()));
        let b = hash_of(&Captured::Text("hola".into()));
        let c = hash_of(&Captured::Files(vec![PathBuf::from("hola")]));
        assert_eq!(a, b);
        assert_ne!(a.1, c.1);
    }
}
