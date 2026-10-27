// The sync key and your phrase: in Windows' Credential Manager. Elsewhere
// (only for development) in a file readable by you alone in the app's folder.

const SERVICE: &str = "Tokkip Notas";

#[cfg(windows)]
pub fn get(name: &str, _dir: &std::path::Path) -> Option<String> {
    keyring::Entry::new(SERVICE, name).ok()?.get_password().ok()
}

#[cfg(windows)]
pub fn set(name: &str, value: Option<&str>, _dir: &std::path::Path) -> Result<(), String> {
    let e = keyring::Entry::new(SERVICE, name).map_err(|e| e.to_string())?;
    match value {
        Some(v) => e.set_password(v).map_err(|e| e.to_string()),
        None => match e.delete_credential() {
            Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
            Err(err) => Err(err.to_string()),
        },
    }
}

#[cfg(not(windows))]
fn file(name: &str, dir: &std::path::Path) -> std::path::PathBuf {
    dir.join(format!(".secret-{}", name.replace(|c: char| !c.is_ascii_alphanumeric(), "_")))
}

#[cfg(not(windows))]
pub fn get(name: &str, dir: &std::path::Path) -> Option<String> {
    let _ = SERVICE;
    std::fs::read_to_string(file(name, dir)).ok()
}

#[cfg(not(windows))]
pub fn set(name: &str, value: Option<&str>, dir: &std::path::Path) -> Result<(), String> {
    let f = file(name, dir);
    match value {
        Some(v) => {
            std::fs::write(&f, v).map_err(|e| e.to_string())?;
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                let _ = std::fs::set_permissions(&f, std::fs::Permissions::from_mode(0o600));
            }
            Ok(())
        }
        None => {
            let _ = std::fs::remove_file(f);
            Ok(())
        }
    }
}
