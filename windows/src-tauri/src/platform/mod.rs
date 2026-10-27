// Everything that differs between operating systems, behind one set of names.
//
// The rest of the app calls `platform::…` and never touches Win32 or a Linux
// API directly. Each OS file exposes the same functions; the compiler picks one.

use std::path::PathBuf;

#[cfg(windows)]
mod windows;
#[cfg(windows)]
pub use self::windows::*;

#[cfg(target_os = "linux")]
mod linux;
#[cfg(target_os = "linux")]
pub use self::linux::*;

/// Wall-clock time in the user's time zone, for log lines and backup names.
pub struct LocalTime {
    pub year: u32,
    pub month: u32,
    pub day: u32,
    pub hour: u32,
    pub minute: u32,
    pub second: u32,
}

/// Tokkip PC's app identifier: its webview's storage and the installer go by it.
pub const IDENTIFIER: &str = "mx.loperstack.tokkippc";
/// The identifier versions before 1.2 had. Their webview storage moves once.
pub const OLD_IDENTIFIER: &str = "fr.louisraille.coucou";

/// Older versions kept their settings, inbox and log in folders under the
/// app's first name, and their webview's storage under the old identifier;
/// they move once into Tokkip's own (merging, if Tokkip's already has
/// something, without overwriting it), before any window opens. Nothing is
/// written under the old names again.
pub fn move_old_dirs() {
    for (old, new) in old_dirs() {
        merge_move(&old, &new);
    }
}

fn merge_move(old: &std::path::Path, new: &std::path::Path) {
    if !old.is_dir() || old == new {
        return;
    }
    if !new.exists() && std::fs::rename(old, new).is_ok() {
        return;
    }
    if std::fs::create_dir_all(new).is_err() {
        return;
    }
    if let Ok(entries) = std::fs::read_dir(old) {
        for e in entries.flatten() {
            let target = new.join(e.file_name());
            if !target.exists() {
                let _ = std::fs::rename(e.path(), target);
            }
        }
    }
    let _ = std::fs::remove_dir(old); // only goes if everything moved
}

/// The user's home directory, where `.claude/settings.json` lives.
pub fn home_dir() -> PathBuf {
    std::env::var_os(HOME_VAR)
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("."))
}

/// How the desktop Tokkip's window can be placed and clicked on this system.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum DesktopMode {
    /// Windows: placed anywhere, and a cursor poll drives click-through, the
    /// eyes and the drag, exactly like the island.
    Poll,
    /// Linux on X11: an ordinary window the window manager lets us place; the
    /// input region is the click-through and the page drives the drag.
    Window,
    /// Linux on a layer-shell compositor (KDE, COSMIC, wlroots): a layer
    /// surface anchored to the top-left corner of the island's display, placed
    /// with margins.
    Layer,
    /// Wayland without layer-shell (GNOME): no way to put a window where the
    /// user dropped it, so Tokkip stays in the island.
    Off,
}

impl DesktopMode {
    pub fn as_str(self) -> &'static str {
        match self {
            DesktopMode::Poll => "poll",
            DesktopMode::Window => "window",
            DesktopMode::Layer => "layer",
            DesktopMode::Off => "off",
        }
    }

    /// The space a saved spot is measured in. A spot from another space (the
    /// same machine once on X11, once on Wayland) is not trusted.
    pub fn space(self) -> &'static str {
        match self {
            // Physical pixels of the whole desktop, as the OS reports monitors.
            DesktopMode::Poll | DesktopMode::Window => "screen",
            // Logical pixels from the top-left corner of the island's display.
            DesktopMode::Layer | DesktopMode::Off => "layer",
        }
    }
}

/// Which part of the desktop Tokkip's window takes the mouse.
#[derive(Clone, Copy, PartialEq, Debug)]
#[cfg_attr(windows, allow(dead_code))]
pub enum MouseShape {
    /// Nothing: every click goes to what is underneath (during a flight).
    Empty,
    /// The whole window (while he is being dragged across the screen).
    Whole,
    /// A disc, in window-logical pixels: Tokkip's body.
    Disc { cx: f64, cy: f64, r: f64 },
}

#[cfg(test)]
mod move_tests {
    #[test]
    fn old_folders_merge_without_overwriting() {
        let base = std::env::temp_dir().join(format!("tokkip-move-{}", std::process::id()));
        let (old, new) = (base.join("old"), base.join("new"));
        std::fs::create_dir_all(old.join("inbox")).unwrap();
        std::fs::write(old.join("settings.json"), "viejo").unwrap();
        std::fs::write(old.join("inbox/a.txt"), "a").unwrap();
        std::fs::create_dir_all(&new).unwrap();
        std::fs::write(new.join("settings.json"), "nuevo").unwrap();
        super::merge_move(&old, &new);
        assert_eq!(std::fs::read_to_string(new.join("settings.json")).unwrap(), "nuevo");
        assert_eq!(std::fs::read_to_string(new.join("inbox/a.txt")).unwrap(), "a");
        // A fresh install: the whole folder just moves.
        let (o2, n2) = (base.join("o2"), base.join("n2"));
        std::fs::create_dir_all(&o2).unwrap();
        std::fs::write(o2.join("x"), "1").unwrap();
        super::merge_move(&o2, &n2);
        assert!(n2.join("x").exists() && !o2.exists());
        let _ = std::fs::remove_dir_all(&base);
    }
}
