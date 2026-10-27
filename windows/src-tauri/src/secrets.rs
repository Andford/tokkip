// API keys live in the Windows Credential Manager or, on Linux, the Secret
// Service (GNOME Keyring, KWallet) — never on disk and never in the front end — the island can only ask whether a key is present.

use keyring::Entry;

const SERVICE: &str = crate::platform::IDENTIFIER;
/// Where versions before 1.2 kept them: read once, moved, then forgotten.
const OLD_SERVICE: &str = crate::platform::OLD_IDENTIFIER;

/// Every key Tokkip may store. Anything outside this list is refused.
pub const KNOWN_KEYS: &[&str] = &[
    "anthropic-api-key",
    "openai-api-key",
    "google-api-key",
    "openrouter-api-key",
    "openai-compatible-key",
    "n8n-url",
    "n8n-api-key",
    "vercel-token",
    "github-token",
    "stripe-api-key",
    "resend-api-key",
    "notion-api-key",
    "calcom-api-key",
    // The Bienestar pill's Google Calendar and Tasks (google.rs).
    "google-oauth-client-id",
    "google-oauth-client-secret",
    "google-refresh-token",
    "google-account-email",
];

fn entry(key: &str) -> Option<Entry> {
    if !KNOWN_KEYS.contains(&key) {
        return None;
    }
    Entry::new(SERVICE, key).ok()
}

pub fn get(key: &str) -> Option<String> {
    let now = entry(key)?.get_password().ok().filter(|v| !v.is_empty());
    if now.is_some() {
        return now;
    }
    // Saved by an older version under the old identifier: move it over.
    let old = Entry::new(OLD_SERVICE, key).ok()?;
    let value = old.get_password().ok().filter(|v| !v.is_empty())?;
    if entry(key)?.set_password(&value).is_ok() {
        let _ = old.delete_credential();
    }
    Some(value)
}

pub fn set(key: &str, value: &str) -> Result<(), String> {
    let entry = entry(key).ok_or_else(|| format!("unknown key {key}"))?;
    if value.is_empty() {
        let _ = entry.delete_credential();
        return Ok(());
    }
    entry.set_password(value).map_err(|e| e.to_string())
}

pub fn clear(key: &str) -> Result<(), String> {
    let entry = entry(key).ok_or_else(|| format!("unknown key {key}"))?;
    if let Ok(old) = Entry::new(OLD_SERVICE, key) {
        let _ = old.delete_credential();
    }
    match entry.delete_credential() {
        Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
        Err(e) => Err(e.to_string()),
    }
}

pub fn present(key: &str) -> bool {
    get(key).is_some()
}
