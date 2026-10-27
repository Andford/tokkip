// Google Calendar and Google Tasks for the Bienestar pill.
//
// Signing in is Google's flow for installed apps: the browser opens Google's
// consent page, which sends the code back to a one-shot listener on
// 127.0.0.1, protected by PKCE (S256) and a random state. The OAuth client
// (a "Desktop app" client the user creates in their own Google Cloud project)
// and the refresh token live in the credential store, never on disk and never
// in the page. Access tokens are kept in memory only.
//
// Scopes: Calendar read-only, Tasks read/write, and the account's email to
// show who is signed in. Nothing reaches the network while Tokkip is paused.

use std::sync::Mutex;
use std::time::{Duration, Instant};

use reqwest::Url;
use serde::Serialize;
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;

use crate::secrets;

pub const CLIENT_ID_KEY: &str = "google-oauth-client-id";
pub const CLIENT_SECRET_KEY: &str = "google-oauth-client-secret";
pub const REFRESH_KEY: &str = "google-refresh-token";
pub const EMAIL_KEY: &str = "google-account-email";

const AUTH_URL: &str = "https://accounts.google.com/o/oauth2/v2/auth";
const TOKEN_URL: &str = "https://oauth2.googleapis.com/token";
const REVOKE_URL: &str = "https://oauth2.googleapis.com/revoke";
const USERINFO_URL: &str = "https://openidconnect.googleapis.com/v1/userinfo";
const CALENDAR: &str = "https://www.googleapis.com/calendar/v3";
const TASKS: &str = "https://tasks.googleapis.com/tasks/v1";
const SCOPES: &str = "openid email https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/tasks";

/// How long the browser has to come back with the code.
const SIGN_IN_WAIT: Duration = Duration::from_secs(300);
const TIMEOUT: Duration = Duration::from_secs(15);

static ACCESS: Mutex<Option<(String, Instant)>> = Mutex::new(None);

fn client() -> reqwest::Client {
    reqwest::Client::builder().timeout(TIMEOUT).build().unwrap_or_default()
}

fn paused() -> Result<(), String> {
    if crate::integrations::PAUSED.load(std::sync::atomic::Ordering::Relaxed) {
        Err("paused".into())
    } else {
        Ok(())
    }
}

// ── Status ────────────────────────────────────────────────────────────────────

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GoogleStatus {
    /// A client ID and secret are saved.
    pub configured: bool,
    /// Signed in: a refresh token is saved.
    pub connected: bool,
    pub email: Option<String>,
}

#[tauri::command]
pub fn google_status() -> GoogleStatus {
    GoogleStatus {
        configured: secrets::present(CLIENT_ID_KEY) && secrets::present(CLIENT_SECRET_KEY),
        connected: secrets::present(REFRESH_KEY),
        email: secrets::get(EMAIL_KEY),
    }
}

// ── PKCE and helpers ──────────────────────────────────────────────────────────

/// RFC 4648 §5 base64url, no padding.
pub fn base64url(bytes: &[u8]) -> String {
    const A: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    let mut out = String::with_capacity(bytes.len().div_ceil(3) * 4);
    for chunk in bytes.chunks(3) {
        let b = [chunk[0], *chunk.get(1).unwrap_or(&0), *chunk.get(2).unwrap_or(&0)];
        let n = (u32::from(b[0]) << 16) | (u32::from(b[1]) << 8) | u32::from(b[2]);
        out.push(A[(n >> 18) as usize & 63] as char);
        out.push(A[(n >> 12) as usize & 63] as char);
        if chunk.len() > 1 {
            out.push(A[(n >> 6) as usize & 63] as char);
        }
        if chunk.len() > 2 {
            out.push(A[n as usize & 63] as char);
        }
    }
    out
}

fn random_token(bytes: usize) -> Result<String, String> {
    let mut buf = vec![0u8; bytes];
    getrandom::getrandom(&mut buf).map_err(|e| e.to_string())?;
    Ok(base64url(&buf))
}

/// The S256 challenge for a PKCE verifier.
pub fn challenge(verifier: &str) -> String {
    base64url(&Sha256::digest(verifier.as_bytes()))
}

/// The `code` and `state` of the redirect's request line, if it is one.
pub fn parse_redirect(request: &str) -> Option<(Option<String>, Option<String>, Option<String>)> {
    let line = request.lines().next()?;
    let mut parts = line.split_whitespace();
    if parts.next()? != "GET" {
        return None;
    }
    let target = parts.next()?;
    let url = Url::parse(&format!("http://127.0.0.1{target}")).ok()?;
    let get = |k: &str| url.query_pairs().find(|(key, _)| key == k).map(|(_, v)| v.into_owned());
    Some((get("code"), get("state"), get("error")))
}

fn page(title: &str, body: &str) -> String {
    let html = format!(
        "<!doctype html><meta charset=utf-8><title>Tokkip</title>\
         <body style=\"font-family:system-ui,sans-serif;background:#0b0c0e;color:#f5f6f8;display:grid;place-items:center;height:100vh;margin:0\">\
         <div style=\"text-align:center\"><div style=\"font-size:42px\">🫧</div><h2>{title}</h2><p style=\"color:#8e939c\">{body}</p></div>"
    );
    format!(
        "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{html}",
        html.len()
    )
}

// ── Sign in / out ─────────────────────────────────────────────────────────────

#[tauri::command]
pub async fn google_connect() -> Result<GoogleStatus, String> {
    paused()?;
    let client_id = secrets::get(CLIENT_ID_KEY).ok_or("missing client id")?;
    let client_secret = secrets::get(CLIENT_SECRET_KEY).ok_or("missing client secret")?;

    let listener = TcpListener::bind(("127.0.0.1", 0)).await.map_err(|e| e.to_string())?;
    let port = listener.local_addr().map_err(|e| e.to_string())?.port();
    let redirect = format!("http://127.0.0.1:{port}");
    let verifier = random_token(48)?;
    let state = random_token(24)?;

    let mut auth = Url::parse(AUTH_URL).map_err(|e| e.to_string())?;
    auth.query_pairs_mut()
        .append_pair("client_id", &client_id)
        .append_pair("redirect_uri", &redirect)
        .append_pair("response_type", "code")
        .append_pair("scope", SCOPES)
        .append_pair("access_type", "offline")
        .append_pair("prompt", "consent")
        .append_pair("code_challenge", &challenge(&verifier))
        .append_pair("code_challenge_method", "S256")
        .append_pair("state", &state);
    crate::platform::open_url(auth.as_str());

    // Wait for the browser to come back (stray requests, a favicon, are answered and skipped).
    let code = tokio::time::timeout(SIGN_IN_WAIT, async {
        loop {
            let (mut stream, _) = listener.accept().await.map_err(|e| e.to_string())?;
            let mut buf = vec![0u8; 8192];
            let n = stream.read(&mut buf).await.unwrap_or(0);
            let request = String::from_utf8_lossy(&buf[..n]).to_string();
            let Some((code, got_state, error)) = parse_redirect(&request) else { continue };
            if code.is_none() && error.is_none() {
                let _ = stream.write_all(b"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").await;
                continue;
            }
            let ok = error.is_none() && got_state.as_deref() == Some(state.as_str());
            let reply = if ok {
                page(&crate::i18n::t("Connected to Google"), &crate::i18n::t("You can close this tab and go back to Tokkip."))
            } else {
                page(&crate::i18n::t("Google sign-in was cancelled"), &crate::i18n::t("You can close this tab and try again from Tokkip."))
            };
            let _ = stream.write_all(reply.as_bytes()).await;
            let _ = stream.shutdown().await;
            if let Some(err) = error {
                return Err(err);
            }
            if got_state.as_deref() != Some(state.as_str()) {
                return Err("state mismatch".to_string());
            }
            return code.ok_or_else(|| "no code".to_string());
        }
    })
    .await
    .map_err(|_| "timed out".to_string())??;

    let response = client()
        .post(TOKEN_URL)
        .form(&[
            ("code", code.as_str()),
            ("client_id", client_id.as_str()),
            ("client_secret", client_secret.as_str()),
            ("redirect_uri", redirect.as_str()),
            ("grant_type", "authorization_code"),
            ("code_verifier", verifier.as_str()),
        ])
        .send()
        .await
        .map_err(|e| e.to_string())?;
    let status = response.status();
    let body: Value = response.json().await.unwrap_or(json!({}));
    if !status.is_success() {
        return Err(body.get("error_description").or(body.get("error")).and_then(Value::as_str).unwrap_or("token error").to_string());
    }
    let refresh = body.get("refresh_token").and_then(Value::as_str).ok_or("no refresh token")?;
    secrets::set(REFRESH_KEY, refresh)?;
    if let (Some(access), Some(expires)) = (body.get("access_token").and_then(Value::as_str), body.get("expires_in").and_then(Value::as_u64)) {
        *ACCESS.lock().unwrap() = Some((access.to_string(), Instant::now() + Duration::from_secs(expires.saturating_sub(60))));
    }
    // Who signed in, for Settings.
    if let Ok(token) = access_token().await {
        if let Ok(r) = client().get(USERINFO_URL).bearer_auth(token).send().await {
            if let Ok(info) = r.json::<Value>().await {
                if let Some(email) = info.get("email").and_then(Value::as_str) {
                    let _ = secrets::set(EMAIL_KEY, email);
                }
            }
        }
    }
    crate::log::line("google: connected");
    Ok(google_status())
}

#[tauri::command]
pub async fn google_disconnect() -> Result<GoogleStatus, String> {
    if let Some(token) = secrets::get(REFRESH_KEY) {
        if paused().is_ok() {
            let _ = client().post(REVOKE_URL).form(&[("token", token.as_str())]).send().await;
        }
    }
    *ACCESS.lock().unwrap() = None;
    secrets::clear(REFRESH_KEY)?;
    secrets::clear(EMAIL_KEY)?;
    Ok(google_status())
}

/// A valid access token, refreshed when needed.
async fn access_token() -> Result<String, String> {
    paused()?;
    if let Some((token, until)) = ACCESS.lock().unwrap().clone() {
        if Instant::now() < until {
            return Ok(token);
        }
    }
    let refresh = secrets::get(REFRESH_KEY).ok_or("not connected")?;
    let client_id = secrets::get(CLIENT_ID_KEY).ok_or("missing client id")?;
    let client_secret = secrets::get(CLIENT_SECRET_KEY).ok_or("missing client secret")?;
    let response = client()
        .post(TOKEN_URL)
        .form(&[
            ("refresh_token", refresh.as_str()),
            ("client_id", client_id.as_str()),
            ("client_secret", client_secret.as_str()),
            ("grant_type", "refresh_token"),
        ])
        .send()
        .await
        .map_err(|e| e.to_string())?;
    let status = response.status();
    let body: Value = response.json().await.unwrap_or(json!({}));
    if !status.is_success() {
        // Revoked or expired: sign in again.
        if body.get("error").and_then(Value::as_str) == Some("invalid_grant") {
            let _ = secrets::clear(REFRESH_KEY);
            return Err("signed out".into());
        }
        return Err(body.get("error").and_then(Value::as_str).unwrap_or("token error").to_string());
    }
    let token = body.get("access_token").and_then(Value::as_str).ok_or("no access token")?.to_string();
    let expires = body.get("expires_in").and_then(Value::as_u64).unwrap_or(3600);
    *ACCESS.lock().unwrap() = Some((token.clone(), Instant::now() + Duration::from_secs(expires.saturating_sub(60))));
    Ok(token)
}

async fn api(method: reqwest::Method, url: &str, body: Option<Value>) -> Result<Value, String> {
    let token = access_token().await?;
    let mut req = client().request(method, url).bearer_auth(token);
    if let Some(b) = body {
        req = req.json(&b);
    }
    let response = req.send().await.map_err(|e| e.to_string())?;
    let status = response.status();
    if status == reqwest::StatusCode::NO_CONTENT {
        return Ok(Value::Null);
    }
    let value: Value = response.json().await.unwrap_or(Value::Null);
    if !status.is_success() {
        let msg = value.pointer("/error/message").and_then(Value::as_str).unwrap_or("request failed");
        return Err(format!("{} {msg}", status.as_u16()));
    }
    Ok(value)
}

// ── Calendar ──────────────────────────────────────────────────────────────────

#[derive(Serialize, Debug, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct CalEvent {
    pub id: String,
    pub title: String,
    /// RFC 3339, or a date (YYYY-MM-DD) for an all-day event.
    pub start: String,
    pub end: String,
    pub all_day: bool,
    pub meet_url: Option<String>,
    pub color: Option<String>,
}

/// One event of the API's list, or None for a cancelled or declined one.
pub fn parse_event(e: &Value, color: Option<&str>) -> Option<CalEvent> {
    if e.get("status").and_then(Value::as_str) == Some("cancelled") {
        return None;
    }
    let declined = e.get("attendees").and_then(Value::as_array).is_some_and(|list| {
        list.iter().any(|a| {
            a.get("self").and_then(Value::as_bool) == Some(true)
                && a.get("responseStatus").and_then(Value::as_str) == Some("declined")
        })
    });
    if declined {
        return None;
    }
    let (start, all_day) = match e.pointer("/start/dateTime").and_then(Value::as_str) {
        Some(s) => (s.to_string(), false),
        None => (e.pointer("/start/date").and_then(Value::as_str)?.to_string(), true),
    };
    let end = e
        .pointer("/end/dateTime")
        .or_else(|| e.pointer("/end/date"))
        .and_then(Value::as_str)
        .unwrap_or(&start)
        .to_string();
    let meet_url = e
        .get("hangoutLink")
        .and_then(Value::as_str)
        .or_else(|| {
            e.pointer("/conferenceData/entryPoints").and_then(Value::as_array).and_then(|eps| {
                eps.iter()
                    .find(|ep| ep.get("entryPointType").and_then(Value::as_str) == Some("video"))
                    .and_then(|ep| ep.get("uri").and_then(Value::as_str))
            })
        })
        .filter(|u| u.starts_with("https://"))
        .map(str::to_string);
    Some(CalEvent {
        id: e.get("id").and_then(Value::as_str).unwrap_or_default().to_string(),
        title: e.get("summary").and_then(Value::as_str).unwrap_or("(sin título)").to_string(),
        start,
        end,
        all_day,
        meet_url,
        color: color.map(str::to_string),
    })
}

/// Events from now until the end of tomorrow, on every calendar shown in Google
/// Calendar. `time_min` / `time_max` are RFC 3339, from the page (its time zone).
#[tauri::command]
pub async fn google_events(time_min: String, time_max: String) -> Result<Vec<CalEvent>, String> {
    let calendars = api(reqwest::Method::GET, &format!("{CALENDAR}/users/me/calendarList?minAccessRole=reader"), None).await?;
    let mut out = Vec::new();
    let list = calendars.get("items").and_then(Value::as_array).cloned().unwrap_or_default();
    for cal in list.iter().filter(|c| c.get("selected").and_then(Value::as_bool) == Some(true) || c.get("primary").and_then(Value::as_bool) == Some(true)) {
        let Some(id) = cal.get("id").and_then(Value::as_str) else { continue };
        let color = cal.get("backgroundColor").and_then(Value::as_str);
        let mut url = Url::parse(&format!("{CALENDAR}/calendars/x/events")).map_err(|e| e.to_string())?;
        url.path_segments_mut().map_err(|_| "bad url")?.pop().pop().push(id).push("events");
        url.query_pairs_mut()
            .append_pair("timeMin", &time_min)
            .append_pair("timeMax", &time_max)
            .append_pair("singleEvents", "true")
            .append_pair("orderBy", "startTime")
            .append_pair("maxResults", "50");
        let Ok(events) = api(reqwest::Method::GET, url.as_str(), None).await else { continue };
        for e in events.get("items").and_then(Value::as_array).into_iter().flatten() {
            if let Some(ev) = parse_event(e, color) {
                out.push(ev);
            }
        }
    }
    out.sort_by(|a, b| a.start.cmp(&b.start));
    Ok(out)
}

// ── Tasks ─────────────────────────────────────────────────────────────────────

#[derive(Serialize, Debug, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct GTask {
    pub id: String,
    pub title: String,
    pub done: bool,
    /// RFC 3339.
    pub updated: String,
}

pub fn parse_task(t: &Value) -> Option<GTask> {
    if t.get("deleted").and_then(Value::as_bool) == Some(true) {
        return None;
    }
    let title = t.get("title").and_then(Value::as_str).unwrap_or("").trim().to_string();
    if title.is_empty() {
        return None;
    }
    Some(GTask {
        id: t.get("id").and_then(Value::as_str)?.to_string(),
        title,
        done: t.get("status").and_then(Value::as_str) == Some("completed"),
        updated: t.get("updated").and_then(Value::as_str).unwrap_or("").to_string(),
    })
}

const LIST: &str = "@default";

#[tauri::command]
pub async fn google_tasks() -> Result<Vec<GTask>, String> {
    let url = format!("{TASKS}/lists/{LIST}/tasks?showCompleted=true&showHidden=false&maxResults=100");
    let v = api(reqwest::Method::GET, &url, None).await?;
    Ok(v.get("items").and_then(Value::as_array).into_iter().flatten().filter_map(parse_task).collect())
}

#[tauri::command]
pub async fn google_task_add(title: String) -> Result<GTask, String> {
    let title: String = title.trim().chars().take(200).collect();
    if title.is_empty() {
        return Err("empty".into());
    }
    let v = api(reqwest::Method::POST, &format!("{TASKS}/lists/{LIST}/tasks"), Some(json!({ "title": title }))).await?;
    parse_task(&v).ok_or_else(|| "bad task".into())
}

fn task_url(id: &str) -> Result<String, String> {
    // Task ids are base64-ish; anything else is refused rather than put in a path.
    if id.is_empty() || !id.chars().all(|c| c.is_ascii_alphanumeric() || "-_".contains(c)) {
        return Err("bad id".into());
    }
    Ok(format!("{TASKS}/lists/{LIST}/tasks/{id}"))
}

#[tauri::command]
pub async fn google_task_set_done(id: String, done: bool) -> Result<(), String> {
    let body = if done {
        json!({ "status": "completed" })
    } else {
        json!({ "status": "needsAction", "completed": null })
    };
    api(reqwest::Method::PATCH, &task_url(&id)?, Some(body)).await.map(|_| ())
}

#[tauri::command]
pub async fn google_task_delete(id: String) -> Result<(), String> {
    api(reqwest::Method::DELETE, &task_url(&id)?, None).await.map(|_| ())
}

/// Hides the completed tasks of the list (Google's "Delete all completed").
#[tauri::command]
pub async fn google_tasks_clear_done() -> Result<(), String> {
    api(reqwest::Method::POST, &format!("{TASKS}/lists/{LIST}/clear"), None).await.map(|_| ())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn base64url_matches_rfc4648() {
        assert_eq!(base64url(b""), "");
        assert_eq!(base64url(b"f"), "Zg");
        assert_eq!(base64url(b"fo"), "Zm8");
        assert_eq!(base64url(b"foo"), "Zm9v");
        assert_eq!(base64url(&[0xfb, 0xff]), "-_8");
    }

    #[test]
    fn pkce_challenge_is_rfc7636s_example() {
        assert_eq!(challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"), "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    #[test]
    fn the_redirect_gives_its_code_state_or_error() {
        let (code, state, error) = parse_redirect("GET /?state=abc&code=4%2F0Ab&scope=x HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n").unwrap();
        assert_eq!(code.as_deref(), Some("4/0Ab"));
        assert_eq!(state.as_deref(), Some("abc"));
        assert_eq!(error, None);
        let (_, _, error) = parse_redirect("GET /?error=access_denied&state=abc HTTP/1.1\r\n").unwrap();
        assert_eq!(error.as_deref(), Some("access_denied"));
        assert!(parse_redirect("POST / HTTP/1.1").is_none());
        let (code, _, error) = parse_redirect("GET /favicon.ico HTTP/1.1").unwrap();
        assert!(code.is_none() && error.is_none());
    }

    #[test]
    fn events_skip_cancelled_and_declined_and_find_the_meet_link() {
        let e = json!({
            "id": "1", "summary": "Junta Bunday",
            "start": { "dateTime": "2026-10-13T10:00:00-06:00" }, "end": { "dateTime": "2026-10-13T11:00:00-06:00" },
            "hangoutLink": "https://meet.google.com/abc-defg-hij"
        });
        let ev = parse_event(&e, Some("#9fe1e7")).unwrap();
        assert_eq!(ev.title, "Junta Bunday");
        assert!(!ev.all_day);
        assert_eq!(ev.meet_url.as_deref(), Some("https://meet.google.com/abc-defg-hij"));
        let all_day = json!({ "id": "2", "summary": "Cumple", "start": { "date": "2026-10-13" }, "end": { "date": "2026-10-14" } });
        assert!(parse_event(&all_day, None).unwrap().all_day);
        assert!(parse_event(&json!({ "status": "cancelled", "start": { "date": "2026-10-13" } }), None).is_none());
        let declined = json!({ "id": "3", "start": { "date": "2026-10-13" },
            "attendees": [{ "self": true, "responseStatus": "declined" }] });
        assert!(parse_event(&declined, None).is_none());
        let bad_link = json!({ "id": "4", "start": { "date": "2026-10-13" }, "hangoutLink": "javascript:alert(1)" });
        assert_eq!(parse_event(&bad_link, None).unwrap().meet_url, None);
    }

    #[test]
    fn tasks_parse_and_ids_are_checked() {
        let t = parse_task(&json!({ "id": "MTIz", "title": " Llamar ", "status": "completed", "updated": "x" })).unwrap();
        assert_eq!(t, GTask { id: "MTIz".into(), title: "Llamar".into(), done: true, updated: "x".into() });
        assert!(parse_task(&json!({ "id": "a", "title": "" })).is_none());
        assert!(parse_task(&json!({ "id": "a", "title": "x", "deleted": true })).is_none());
        assert!(task_url("abc_DEF-1").is_ok());
        assert!(task_url("../lists").is_err());
        assert!(task_url("").is_err());
    }
}
