// Interface language for what Rust shows: the tray menu, the settings window
// title and the errors the webviews display. Same table and same lookup as
// src/i18n/i18n.ts: texts keyed by their English wording, with their Spanish
// in es.json. English is the key itself; a text es.json lacks shows in English.
//
// The language is Settings.language when it is Spanish or English; "" follows
// the system: the languages the island's webview reports at launch, else
// (Linux) the LANGUAGE / LC_ALL / LC_MESSAGES / LANG variables, else English.

use serde_json::{Map, Value};
use std::sync::{OnceLock, RwLock};

pub const LANGUAGES: [&str; 2] = ["es", "en"];

static SPANISH: &str = include_str!("../../src/i18n/es.json");

fn table() -> &'static Map<String, Value> {
    static TABLE: OnceLock<Map<String, Value>> = OnceLock::new();
    TABLE.get_or_init(|| match serde_json::from_str::<Value>(SPANISH) {
        Ok(Value::Object(mut root)) => match root.remove("strings") {
            Some(Value::Object(strings)) => strings,
            _ => Map::new(),
        },
        _ => Map::new(),
    })
}

struct Current {
    picked: String,
    system: Vec<String>,
    resolved: &'static str,
}

fn state() -> &'static RwLock<Current> {
    static STATE: OnceLock<RwLock<Current>> = OnceLock::new();
    STATE.get_or_init(|| {
        let system = env_languages();
        let resolved = resolve("", &system);
        RwLock::new(Current { picked: String::new(), system, resolved })
    })
}

/// LANGUAGE (a list), then LC_ALL, LC_MESSAGES and LANG, as gettext reads them.
fn env_languages() -> Vec<String> {
    let mut out = Vec::new();
    if let Ok(list) = std::env::var("LANGUAGE") {
        out.extend(list.split(':').filter(|s| !s.is_empty()).map(str::to_string));
    }
    for name in ["LC_ALL", "LC_MESSAGES", "LANG"] {
        if let Ok(v) = std::env::var(name) {
            if !v.is_empty() && v != "C" && v != "POSIX" {
                out.push(v);
            }
        }
    }
    out
}

/// The language to show: `picked` when it is Spanish or English (an older
/// build's other languages read as "System"), else the first of the system's
/// languages that is one of the two, else English. Same rules as
/// resolveLanguage in src/i18n/i18n.ts.
pub fn resolve(picked: &str, system: &[String]) -> &'static str {
    if let Some(code) = LANGUAGES.iter().find(|c| **c == picked) {
        return code;
    }
    for raw in system {
        // "es_MX.UTF-8" → "es"
        let tag = raw.split('.').next().unwrap_or("").replace('_', "-").to_lowercase();
        let base = tag.split('-').next().unwrap_or("");
        if let Some(code) = LANGUAGES.iter().find(|c| **c == base) {
            return code;
        }
    }
    "en"
}

/// Records Settings.language. Returns true when the shown language changed.
pub fn set_picked(picked: &str) -> bool {
    let mut s = state().write().unwrap_or_else(|e| e.into_inner());
    s.picked = picked.to_string();
    let next = resolve(&s.picked, &s.system);
    std::mem::replace(&mut s.resolved, next) != next
}

/// Records the system's languages as the webview sees them. Returns true when
/// the shown language changed.
pub fn set_system(languages: Vec<String>) -> bool {
    let mut s = state().write().unwrap_or_else(|e| e.into_inner());
    if !languages.is_empty() {
        s.system = languages;
    }
    let next = resolve(&s.picked, &s.system);
    std::mem::replace(&mut s.resolved, next) != next
}

/// The language shown now.
pub fn current() -> &'static str {
    #[cfg(test)]
    return TEST_LANG.with(|c| c.get());
    #[cfg(not(test))]
    state().read().unwrap_or_else(|e| e.into_inner()).resolved
}

// Tests run in parallel and many check English text: each test thread has its
// own language, English unless the test picks another.
#[cfg(test)]
thread_local! {
    static TEST_LANG: std::cell::Cell<&'static str> = const { std::cell::Cell::new("en") };
}

fn interpolate(text: &str, vars: &[(&str, &str)]) -> String {
    let mut out = text.to_string();
    for (name, value) in vars {
        out = out.replace(&format!("{{{name}}}"), value);
    }
    out
}

fn lookup_in(lang: &str, key: &str) -> Option<&'static Value> {
    if lang != "es" {
        return None;
    }
    table().get(key)
}

/// Plural category of `n` (whole numbers): Spanish and English both say
/// "one" for 1 and "other" for the rest.
fn plural_category(n: u64) -> &'static str {
    if n == 1 { "one" } else { "other" }
}

fn pick_plural(forms: &Map<String, Value>, category: &str) -> Option<String> {
    forms
        .get(category)
        .or_else(|| forms.get("other"))
        .or_else(|| forms.values().next())
        .and_then(Value::as_str)
        .map(str::to_string)
}

/// Marks an English key for a table built before it is shown (the tray's
/// labels): the code translates it with `t` when it shows it. The i18n test
/// (tests/i18n.test.mjs) finds the keys through it.
pub const fn n_(key: &'static str) -> &'static str {
    key
}

/// `key` (English text) in the shown language, else the English text.
pub fn t(key: &str) -> String {
    tf(key, &[])
}

/// `t` with `{name}` placeholders filled in.
pub fn tf(key: &str, vars: &[(&str, &str)]) -> String {
    let lang = current();
    let text = match lookup_in(lang, key) {
        Some(Value::String(s)) => s.clone(),
        Some(Value::Object(forms)) => pick_plural(forms, "other").unwrap_or_else(|| key.to_string()),
        _ => key.to_string(),
    };
    interpolate(&text, vars)
}

/// A count with its words: `tn("{count} file", "{count} files", n, &[])`.
#[allow(dead_code)]
pub fn tn(one: &str, other: &str, count: u64, vars: &[(&str, &str)]) -> String {
    let lang = current();
    let n = count.to_string();
    let mut all: Vec<(&str, &str)> = vars.to_vec();
    all.push(("count", &n));
    let text = match lookup_in(lang, other) {
        Some(Value::Object(forms)) => pick_plural(forms, plural_category(count)),
        Some(Value::String(s)) => Some(s.clone()),
        _ => None,
    };
    let text = text.unwrap_or_else(|| if count == 1 { one.to_string() } else { other.to_string() });
    interpolate(&text, &all)
}

#[cfg(test)]
pub(crate) fn set_for_test(lang: &str) {
    TEST_LANG.with(|c| c.set(resolve(lang, &[])));
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resolves_like_the_webview() {
        let sys = |v: &[&str]| v.iter().map(|s| s.to_string()).collect::<Vec<_>>();
        assert_eq!(resolve("es", &sys(&["en-US"])), "es");
        assert_eq!(resolve("", &sys(&["es_MX.UTF-8"])), "es");
        assert_eq!(resolve("", &sys(&["de-DE", "es-MX"])), "es");
        assert_eq!(resolve("", &sys(&["en-GB"])), "en");
        assert_eq!(resolve("", &sys(&["de-DE"])), "en");
        // A language an older build offered reads as "System".
        assert_eq!(resolve("fr", &sys(&["es-MX"])), "es");
        assert_eq!(resolve("xx", &[]), "en");
    }

    #[test]
    fn looks_up_spanish_and_falls_back_to_english() {
        set_for_test("es");
        assert_eq!(t("Allow"), "Permitir");
        assert_eq!(t("Open Tokkip"), "Abrir Tokkip");
        assert_eq!(t("A string nobody translated"), "A string nobody translated");
        assert_eq!(tf("Open {name}", &[("name", "Vercel")]), "Abrir Vercel");
        set_for_test("en");
        assert_eq!(t("Allow"), "Allow");
        assert_eq!(tf("Open {name}", &[("name", "Tokkip")]), "Open Tokkip");
    }

    #[test]
    fn plurals_follow_the_language() {
        set_for_test("en");
        assert_eq!(tn("{count} model", "{count} models", 1, &[]), "1 model");
        assert_eq!(tn("{count} model", "{count} models", 3, &[]), "3 models");
        set_for_test("es");
        assert_eq!(tn("{count} model", "{count} models", 1, &[]), "1 modelo");
        assert_eq!(tn("{count} model", "{count} models", 4, &[]), "4 modelos");
        set_for_test("en");
    }
}
