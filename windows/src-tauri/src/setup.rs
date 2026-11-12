// Tokkip's own setup window, instead of the installer's classic pages: the
// same idea as Tokkip Phone's. The installer (NSIS, see nsis/hooks.nsh)
// unpacks tokkip.exe to a temporary folder as "Tokkip Setup.exe" and runs it
// with `--setup --installer <setup.exe>`; Install runs that setup.exe again
// with /S, which installs for this user with no prompts (no administrator
// needed), and the window follows until it's done. Removing works the same
// way: the uninstaller runs a copy of the installed app with
// `--remove --uninstaller <uninstall.exe>`.
//
// The window is a normal Tauri page (setup.html, src/setup/); nothing else of
// Tokkip starts in this mode. If the window can't open (no WebView2), the
// process exits with WINDOW_FAILED and the installer shows its own pages.

use std::path::{Path, PathBuf};
use std::process::Command;
use tauri::{AppHandle, WebviewUrl, WebviewWindowBuilder};

pub const SETUP_ARG: &str = "--setup";
pub const REMOVE_ARG: &str = "--remove";
const INSTALLER_ARG: &str = "--installer";
const UNINSTALLER_ARG: &str = "--uninstaller";

/// The installer's own pages take over when the window can't open.
pub const WINDOW_FAILED: i32 = 3;

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Mode {
    /// Installing from `installer`, the setup .exe the user ran.
    Install { installer: PathBuf },
    /// Removing with `uninstaller`, the installed copy's uninstall.exe.
    Remove { uninstaller: PathBuf },
}

/// `--setup --installer <exe>` or `--remove --uninstaller <exe>`.
pub fn from_args(args: &[String]) -> Option<Mode> {
    let value = |flag: &str| {
        args.iter()
            .position(|a| a == flag)
            .and_then(|i| args.get(i + 1))
            .map(PathBuf::from)
    };
    if args.iter().any(|a| a == SETUP_ARG) {
        return value(INSTALLER_ARG).map(|installer| Mode::Install { installer });
    }
    if args.iter().any(|a| a == REMOVE_ARG) {
        return value(UNINSTALLER_ARG).map(|uninstaller| Mode::Remove { uninstaller });
    }
    None
}

/// What the window shows: what it's for, and what is on this PC already.
#[derive(serde::Serialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct SetupInfo {
    /// "install", "update", "reinstall", "downgrade" or "remove".
    pub action: String,
    pub version: String,
    pub installed_version: String,
}

/// The version on this PC, from its Apps entry (empty when there is none).
fn installed_version() -> String {
    reg_value("DisplayVersion").unwrap_or_default()
}

/// Where it's installed (the Apps entry keeps it in quotes).
fn installed_dir() -> Option<PathBuf> {
    reg_value("InstallLocation").map(|s| PathBuf::from(s.trim_matches('"')))
}

/// A value of Tokkip's entry in Apps (HKCU: it installs for this user).
#[cfg(windows)]
fn reg_value(name: &str) -> Option<String> {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;
    let out = Command::new("reg")
        .args([
            "query",
            r"HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\Tokkip",
            "/v",
            name,
        ])
        .creation_flags(CREATE_NO_WINDOW)
        .output()
        .ok()?;
    if !out.status.success() {
        return None;
    }
    parse_reg_query(&String::from_utf8_lossy(&out.stdout), name)
}

#[cfg(not(windows))]
fn reg_value(_name: &str) -> Option<String> {
    None
}

#[cfg_attr(not(windows), allow(dead_code))]
/// The value in `reg query`'s answer: `    DisplayVersion    REG_SZ    1.4.0`.
fn parse_reg_query(out: &str, name: &str) -> Option<String> {
    out.lines().find_map(|line| {
        let rest = line.trim_start().strip_prefix(name)?;
        let rest = rest
            .trim_start()
            .strip_prefix("REG_SZ")
            .or_else(|| rest.trim_start().strip_prefix("REG_EXPAND_SZ"))?;
        Some(rest.trim().to_owned()).filter(|v| !v.is_empty())
    })
}

/// Compares two `x.y.z` versions (missing parts are 0, anything else counts as 0).
fn version_order(a: &str, b: &str) -> std::cmp::Ordering {
    let parts = |v: &str| -> Vec<u64> {
        v.split('.')
            .map(|p| p.trim().parse().unwrap_or(0))
            .collect()
    };
    let (a, b) = (parts(a), parts(b));
    for i in 0..a.len().max(b.len()) {
        let o = a.get(i).unwrap_or(&0).cmp(b.get(i).unwrap_or(&0));
        if o.is_ne() {
            return o;
        }
    }
    std::cmp::Ordering::Equal
}

pub fn action_for(mode: &Mode, version: &str, installed: &str) -> &'static str {
    match mode {
        Mode::Remove { .. } => "remove",
        Mode::Install { .. } if installed.is_empty() => "install",
        Mode::Install { .. } => match version_order(version, installed) {
            std::cmp::Ordering::Greater => "update",
            std::cmp::Ordering::Equal => "reinstall",
            std::cmp::Ordering::Less => "downgrade",
        },
    }
}

struct SetupState {
    mode: Mode,
}

#[tauri::command]
fn setup_info(state: tauri::State<'_, SetupState>) -> SetupInfo {
    let version = env!("CARGO_PKG_VERSION").to_owned();
    let installed = installed_version();
    SetupInfo {
        action: action_for(&state.mode, &version, &installed).to_owned(),
        version,
        installed_version: installed,
    }
}

/// Installs or removes, and waits until it's done. The error is for the window.
#[tauri::command]
async fn setup_run(state: tauri::State<'_, SetupState>) -> Result<(), String> {
    let mode = state.mode.clone();
    tauri::async_runtime::spawn_blocking(move || run_blocking(&mode))
        .await
        .map_err(|e| e.to_string())?
}

fn run_blocking(mode: &Mode) -> Result<(), String> {
    match mode {
        Mode::Install { installer } => {
            let status = Command::new(installer)
                .arg("/S")
                .status()
                .map_err(|e| e.to_string())?;
            if !status.success() {
                return Err(format!("setup exited with {}", status.code().unwrap_or(-1)));
            }
            // Done is when Apps lists this version.
            if installed_version() != env!("CARGO_PKG_VERSION") {
                return Err("the new version isn't listed in Apps".into());
            }
            Ok(())
        }
        Mode::Remove { uninstaller } => {
            let dir = uninstaller
                .parent()
                .map(Path::to_path_buf)
                .ok_or("no folder")?;
            // `_?=` keeps the uninstaller where it is, so this waits for it.
            let status = Command::new(uninstaller)
                .arg("/S")
                .arg(format!("_?={}", dir.display()))
                .status()
                .map_err(|e| e.to_string())?;
            if !status.success() {
                return Err(format!(
                    "uninstall exited with {}",
                    status.code().unwrap_or(-1)
                ));
            }
            // What `_?=` leaves behind: the uninstaller itself and, if empty, its folder.
            let _ = std::fs::remove_file(uninstaller);
            let _ = std::fs::remove_dir(&dir);
            Ok(())
        }
    }
}

/// Opens the installed Tokkip, as the user (this window already runs as them).
#[tauri::command]
fn setup_open_app(app: AppHandle) {
    if let Some(exe) = installed_dir()
        .map(|d| d.join("tokkip.exe"))
        .filter(|e| e.is_file())
    {
        let _ = Command::new(exe).spawn();
    }
    app.exit(0);
}

#[tauri::command]
fn setup_close(app: AppHandle) {
    app.exit(0);
}

/// Runs the setup window and nothing else of Tokkip.
pub fn run(mode: Mode, mut context: tauri::Context<tauri::Wry>) {
    // The island and the other windows in tauri.conf.json aren't made here.
    context.config_mut().app.windows.clear();
    let title = match mode {
        Mode::Install { .. } => crate::i18n::t("Tokkip Setup"),
        Mode::Remove { .. } => crate::i18n::t("Uninstall Tokkip"),
    };
    let result = tauri::Builder::default()
        .manage(SetupState { mode })
        .invoke_handler(tauri::generate_handler![
            setup_info,
            setup_run,
            setup_open_app,
            setup_close
        ])
        .setup(move |app| {
            let url = page_url(app.handle());
            WebviewWindowBuilder::new(app, "setup", url)
                // The same as Tokkip's windows: a WebView2 folder in use by a
                // running Tokkip only opens with the same options.
                .additional_browser_args(crate::BROWSER_ARGS)
                .title(title)
                .inner_size(460.0, 600.0)
                .resizable(false)
                .maximizable(false)
                .decorations(false)
                .shadow(true)
                .center()
                .build()?;
            Ok(())
        })
        .run(context);
    if result.is_err() {
        std::process::exit(WINDOW_FAILED);
    }
}

fn page_url(app: &AppHandle) -> WebviewUrl {
    #[cfg(dev)]
    if let Some(mut base) = app.config().build.dev_url.clone() {
        base.set_path("/setup.html");
        return WebviewUrl::External(base);
    }
    let _ = app;
    WebviewUrl::App("setup.html".into())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn args(a: &[&str]) -> Vec<String> {
        a.iter().map(|s| s.to_string()).collect()
    }

    #[test]
    fn the_installer_and_the_uninstaller_say_what_they_want() {
        assert_eq!(
            from_args(&args(&[
                "Tokkip Setup.exe",
                "--setup",
                "--installer",
                r"C:\Downloads\Tokkip-setup.exe"
            ])),
            Some(Mode::Install {
                installer: PathBuf::from(r"C:\Downloads\Tokkip-setup.exe")
            })
        );
        assert_eq!(
            from_args(&args(&[
                "x",
                "--remove",
                "--uninstaller",
                r"C:\T\uninstall.exe"
            ])),
            Some(Mode::Remove {
                uninstaller: PathBuf::from(r"C:\T\uninstall.exe")
            })
        );
        assert_eq!(from_args(&args(&["tokkip.exe"])), None);
        assert_eq!(from_args(&args(&["x", "--setup"])), None);
    }

    #[test]
    fn the_window_knows_whether_it_installs_updates_or_repairs() {
        let install = Mode::Install {
            installer: PathBuf::new(),
        };
        assert_eq!(action_for(&install, "1.5.0", ""), "install");
        assert_eq!(action_for(&install, "1.5.0", "1.4.0"), "update");
        assert_eq!(action_for(&install, "1.5.0", "1.5.0"), "reinstall");
        assert_eq!(action_for(&install, "1.5.0", "1.10.0"), "downgrade");
        assert_eq!(
            action_for(
                &Mode::Remove {
                    uninstaller: PathBuf::new()
                },
                "1.5.0",
                "1.5.0"
            ),
            "remove"
        );
    }

    #[test]
    fn reads_the_apps_entry() {
        let out = "\r\nHKEY_CURRENT_USER\\Software\\...\\Tokkip\r\n    DisplayVersion    REG_SZ    1.4.0\r\n\r\n";
        assert_eq!(
            parse_reg_query(out, "DisplayVersion").as_deref(),
            Some("1.4.0")
        );
        let out = "    InstallLocation    REG_SZ    \"C:\\Users\\a\\AppData\\Local\\Tokkip\"\r\n";
        assert_eq!(
            parse_reg_query(out, "InstallLocation").as_deref(),
            Some("\"C:\\Users\\a\\AppData\\Local\\Tokkip\"")
        );
        assert_eq!(parse_reg_query("nothing", "DisplayVersion"), None);
    }
}
