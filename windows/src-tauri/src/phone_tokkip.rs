// Tokkip shared with your phone, through Tokkip Phone: the phone app's
// Tokkip (species, colours, outfit, how you feel today) arrives in
// %APPDATA%\Tokkip\shared\phone-tokkip.json (~/.config/Tokkip/shared on
// Linux), written by Tokkip Phone's PC app, and this island's goes back the
// other way in pc-tokkip.json. The page decides which is newer; this side
// only reads and writes the small files (Andford/tokkipphone,
// docs/protocol/tokkip.md).

use crate::platform;
use std::path::PathBuf;

/// The most either file may be (Tokkip Phone carries no more).
const MAX_BYTES: usize = 4096;

fn shared_dir() -> Option<PathBuf> {
    Some(
        platform::config_dir()
            .parent()?
            .join("Tokkip")
            .join("shared"),
    )
}

/// What the phone's Tokkip is like, or nothing when Tokkip Phone hasn't said.
#[tauri::command]
pub fn phone_tokkip() -> Option<String> {
    let path = shared_dir()?.join("phone-tokkip.json");
    let meta = std::fs::metadata(&path).ok()?;
    if !meta.is_file() || meta.len() > MAX_BYTES as u64 {
        return None;
    }
    std::fs::read_to_string(path).ok()
}

/// Leaves this island's Tokkip for Tokkip Phone to carry to the phone.
#[tauri::command]
pub fn set_pc_tokkip(json: String) -> Result<(), String> {
    check(&json)?;
    let dir = shared_dir().ok_or("no folder")?;
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    // Whole or not at all: a reader never sees half a file.
    let tmp = dir.join("pc-tokkip.json.tmp");
    std::fs::write(&tmp, json.as_bytes()).map_err(|e| e.to_string())?;
    std::fs::rename(&tmp, dir.join("pc-tokkip.json")).map_err(|e| e.to_string())
}

/// A JSON object of the size Tokkip Phone carries.
fn check(json: &str) -> Result<(), String> {
    if json.len() > MAX_BYTES {
        return Err("too big".into());
    }
    match serde_json::from_str::<serde_json::Value>(json) {
        Ok(serde_json::Value::Object(_)) => Ok(()),
        _ => Err("not a Tokkip".into()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_files_live_where_tokkip_phone_looks() {
        let dir = shared_dir().expect("a folder");
        assert!(dir.ends_with("Tokkip/shared") || dir.ends_with("Tokkip\\shared"));
    }

    #[test]
    fn only_a_small_object_is_written() {
        assert!(check(r#"{"v":1}"#).is_ok());
        assert!(check("[1,2]").is_err());
        assert!(check("no").is_err());
        assert!(check(&format!(r#"{{"x":"{}"}}"#, "a".repeat(MAX_BYTES))).is_err());
    }
}
