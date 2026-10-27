// Tokkip Healthy PC (its own app, Andford/tokkiphealthypc) leaves a small note
// about how this PC feels in %APPDATA%\Tokkip\shared\healthy-pc.json
// (~/.config/Tokkip/shared on Linux). The island reads it every few seconds
// and tells you: a clean-up that freed space, or that the PC needs a breather.
// Only the text is handed over; the page checks what's in it.

use crate::platform;
use std::path::PathBuf;

/// A note is a score, a mood and a sentence: anything bigger isn't one.
const MAX_BYTES: u64 = 16 * 1024;

fn note_path() -> Option<PathBuf> {
    let config = platform::config_dir();
    Some(config.parent()?.join("Tokkip").join("shared").join("healthy-pc.json"))
}

/// The note's text, or nothing when Tokkip Healthy PC hasn't written one.
#[tauri::command]
pub fn healthy_pc_note() -> Option<String> {
    let path = note_path()?;
    let meta = std::fs::metadata(&path).ok()?;
    if !meta.is_file() || meta.len() > MAX_BYTES {
        return None;
    }
    std::fs::read_to_string(path).ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_note_lives_next_to_our_config_folder() {
        let p = note_path().expect("a path");
        assert!(p.ends_with("Tokkip/shared/healthy-pc.json") || p.ends_with("Tokkip\\shared\\healthy-pc.json"));
        assert_eq!(p.parent().unwrap().parent().unwrap().parent(), platform::config_dir().parent());
    }
}
