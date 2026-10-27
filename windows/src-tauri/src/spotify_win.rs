// The Spotify pill on Windows: the system media controls (SMTC).
//
// Windows keeps one "media session" per app that plays something — the same
// ones the volume flyout and the lock screen show. Spotify publishes its
// track, cover, position, shuffle and repeat there and takes play/pause,
// next, previous, seek, shuffle and repeat from there. So does any other
// player (a browser tab with YouTube Music, Apple Music, VLC…): Spotify's
// session is followed when there is one, else the one Windows calls current,
// so Tokkip dances to whatever is playing.
//
// Nothing runs on a timer. One thread owns every WinRT object (in the MTA)
// and sleeps on a channel; the sessions' own events (media properties,
// playback info, timeline, sessions coming and going) wake it, and the
// commands reach it through the same channel. Turning the pill off ends it.
//
// What it reads is turned into the MPRIS properties Linux gets, and goes
// through the same PlayerState::apply, so the island sees one format.
//
// SMTC has no volume. For Spotify itself the volume is its own entry in the
// Windows volume mixer (WASAPI's per-app volume); for other players the
// volume control does nothing, rather than turning a whole browser down.

use super::*;
use std::path::PathBuf;
use std::sync::mpsc::{self, Receiver, RecvTimeoutError, Sender};
use std::sync::{LazyLock, Mutex};
use std::time::Duration;

use tauri::Emitter;
use windows::Foundation::TypedEventHandler;
use windows::Media::Control::{
    GlobalSystemMediaTransportControlsSession as Session, GlobalSystemMediaTransportControlsSessionManager as Manager,
    GlobalSystemMediaTransportControlsSessionPlaybackStatus as Status,
};
use windows::Media::MediaPlaybackAutoRepeatMode as RepeatMode;
use windows::Storage::Streams::DataReader;

/// Where "Get Spotify" leads when it isn't installed.
const DOWNLOAD_URL: &str = "https://www.spotify.com/download/windows/";
const SPOTIFY_EXE: &str = "Spotify.exe";
/// SMTC events come in bursts (a new track fires all three): gathered for this long.
const BATCH: Duration = Duration::from_millis(80);
/// How long a command waits for the thread before giving up.
const REPLY: Duration = Duration::from_secs(4);
/// A position that drifted less than this from the clock the island runs is not news.
const DRIFT: f64 = 1.5;
/// 100 ns ticks between 1601-01-01 (WinRT DateTime) and 1970-01-01.
const EPOCH_TICKS: i64 = 116_444_736_000_000_000;

enum Msg {
    /// Something in the followed session changed: read it again.
    Changed,
    /// Sessions came or went, or Windows changed its current one.
    Sessions,
    Refresh(Sender<Option<PlayerState>>),
    Control(String, Option<f64>, Sender<bool>),
    Stop,
}

/// The thread's mailbox while the pill is declared.
static WORKER: LazyLock<Mutex<Option<Sender<Msg>>>> = LazyLock::new(|| Mutex::new(None));

// ── Start / stop ──────────────────────────────────────────────────────────────

pub fn sync(app: &AppHandle, on: bool) {
    let mut slot = WORKER.lock().unwrap();
    if on {
        if slot.is_some() {
            return;
        }
        let (tx, rx) = mpsc::channel();
        let (app2, tx2) = (app.clone(), tx.clone());
        let spawned = std::thread::Builder::new().name("tokkip-spotify".into()).spawn(move || run(app2, tx2, rx));
        match spawned {
            Ok(_) => *slot = Some(tx),
            Err(err) => crate::log::line(format!("spotify: no listener thread: {err}")),
        }
    } else if let Some(tx) = slot.take() {
        let _ = tx.send(Msg::Stop);
        drop(slot);
        emit_state(app, &PlayerState { installed: installed(), ..PlayerState::default() });
    }
}

fn ask<T>(make: impl FnOnce(Sender<T>) -> Msg) -> Option<T> {
    let tx = WORKER.lock().unwrap().clone()?;
    let (reply, answer) = mpsc::channel();
    tx.send(make(reply)).ok()?;
    answer.recv_timeout(REPLY).ok()
}

pub fn refresh() -> Option<PlayerState> {
    ask(Msg::Refresh).flatten()
}

pub fn control(action: String, value: Option<f64>) -> bool {
    ask(|reply| Msg::Control(action, value, reply)).unwrap_or(false)
}

// ── The thread ────────────────────────────────────────────────────────────────

fn run(app: AppHandle, tx: Sender<Msg>, rx: Receiver<Msg>) {
    use windows::Win32::System::Com::{CoInitializeEx, CoUninitialize, COINIT_MULTITHREADED};
    // WinRT objects and the audio mixer live in this thread's apartment.
    let com = unsafe { CoInitializeEx(None, COINIT_MULTITHREADED) }.is_ok();
    match Manager::RequestAsync().and_then(|op| op.get()) {
        Ok(manager) => follow(app, manager, tx, rx),
        Err(err) => {
            crate::log::line(format!("spotify: no media sessions: {err}"));
            // Answer whatever is waiting, then let the pill be turned off.
            while let Ok(msg) = rx.recv() {
                match msg {
                    Msg::Refresh(reply) => drop(reply.send(None)),
                    Msg::Control(_, _, reply) => drop(reply.send(false)),
                    Msg::Stop => break,
                    _ => {}
                }
            }
        }
    }
    if com {
        unsafe { CoUninitialize() };
    }
}

fn follow(app: AppHandle, manager: Manager, tx: Sender<Msg>, rx: Receiver<Msg>) {
    let sessions_token = manager.SessionsChanged(&waker(&tx, || Msg::Sessions)).ok();
    let current_token = manager.CurrentSessionChanged(&waker(&tx, || Msg::Sessions)).ok();

    let mut f = Follower {
        app,
        tx: tx.clone(),
        session: None,
        tokens: None,
        spotify: false,
        state: PlayerState { installed: installed(), ..PlayerState::default() },
        sent: None,
        art: None,
    };
    f.pick(&manager);
    f.read();
    f.emit(true);

    'outer: while let Ok(first) = rx.recv() {
        let mut batch = vec![first];
        if matches!(batch[0], Msg::Changed | Msg::Sessions) {
            loop {
                match rx.recv_timeout(BATCH) {
                    Ok(msg) => {
                        let urgent = !matches!(msg, Msg::Changed | Msg::Sessions);
                        batch.push(msg);
                        if urgent || batch.len() >= 64 {
                            break;
                        }
                    }
                    Err(RecvTimeoutError::Timeout) => break,
                    Err(RecvTimeoutError::Disconnected) => break,
                }
            }
        }
        let (mut sessions, mut changed) = (false, false);
        for msg in batch {
            match msg {
                Msg::Stop => break 'outer,
                Msg::Sessions => sessions = true,
                Msg::Changed => changed = true,
                Msg::Refresh(reply) => {
                    f.pick(&manager);
                    f.read();
                    f.emit(true);
                    let _ = reply.send(Some(f.state.clone()));
                    (sessions, changed) = (false, false);
                }
                Msg::Control(action, value, reply) => {
                    let _ = reply.send(f.control(&action, value));
                }
            }
        }
        if sessions {
            f.pick(&manager);
        }
        if sessions || changed {
            f.read();
            f.emit(false);
        }
    }

    f.unbind();
    if let Some(t) = sessions_token {
        let _ = manager.RemoveSessionsChanged(t);
    }
    if let Some(t) = current_token {
        let _ = manager.RemoveCurrentSessionChanged(t);
    }
}

/// An event handler that only wakes the thread.
fn waker<S, R>(tx: &Sender<Msg>, msg: fn() -> Msg) -> TypedEventHandler<S, R>
where
    S: windows::core::RuntimeType + 'static,
    R: windows::core::RuntimeType + 'static,
{
    let tx = tx.clone();
    TypedEventHandler::new(move |_, _| {
        let _ = tx.send(msg());
        Ok(())
    })
}

struct Follower {
    app: AppHandle,
    tx: Sender<Msg>,
    session: Option<Session>,
    /// Media properties, playback info, timeline: to unregister on the way out.
    tokens: Option<(i64, i64, i64)>,
    /// The followed session is Spotify's (its volume can be set).
    spotify: bool,
    state: PlayerState,
    /// The last state the island got.
    sent: Option<PlayerState>,
    /// The current cover: its key and its data URL.
    art: Option<(String, String)>,
}

impl Follower {
    /// Spotify's session when there is one, else Windows' current one.
    fn pick(&mut self, manager: &Manager) {
        let sessions: Vec<Session> = manager.GetSessions().map(|v| v.into_iter().collect()).unwrap_or_default();
        let spotify = sessions.into_iter().find(|s| is_spotify(&app_id(s)));
        let chosen = spotify.or_else(|| manager.GetCurrentSession().ok());
        self.unbind();
        self.spotify = chosen.as_ref().is_some_and(|s| is_spotify(&app_id(s)));
        if let Some(s) = &chosen {
            let media = s.MediaPropertiesChanged(&waker(&self.tx, || Msg::Changed));
            let playback = s.PlaybackInfoChanged(&waker(&self.tx, || Msg::Changed));
            let timeline = s.TimelinePropertiesChanged(&waker(&self.tx, || Msg::Changed));
            if let (Ok(a), Ok(b), Ok(c)) = (media, playback, timeline) {
                self.tokens = Some((a, b, c));
            }
        }
        self.session = chosen;
    }

    fn unbind(&mut self) {
        if let (Some(s), Some((a, b, c))) = (&self.session, self.tokens.take()) {
            let _ = s.RemoveMediaPropertiesChanged(a);
            let _ = s.RemovePlaybackInfoChanged(b);
            let _ = s.RemoveTimelinePropertiesChanged(c);
        }
        self.session = None;
    }

    /// The session, as the MPRIS properties Linux would get.
    fn read(&mut self) {
        let now = now_ms();
        self.state.installed = installed();
        let Some(s) = &self.session else {
            self.state.running = false;
            self.state.clear(now);
            return;
        };
        self.state.running = true;
        let props = read_session(s, self.spotify, now);
        self.state.apply(&props, now);
    }

    /// Tells the island, when something it shows moved (or always, `force`).
    fn emit(&mut self, force: bool) {
        let news = force
            || match &self.sent {
                None => true,
                Some(old) => {
                    let now = now_ms();
                    old.running != self.state.running
                        || old.installed != self.state.installed
                        || old.track != self.state.track
                        || old.playing != self.state.playing
                        || old.shuffle != self.state.shuffle
                        || old.repeat != self.state.repeat
                        || old.volume != self.state.volume
                        || (old.position_at_time(now) - self.state.position_at_time(now)).abs() > DRIFT
                }
            };
        if !news {
            return;
        }
        emit_state(&self.app, &self.state);
        self.sent = Some(self.state.clone());
        self.artwork();
    }

    /// The cover, read from the session's thumbnail stream.
    fn artwork(&mut self) {
        let Some(key) = self.state.track.as_ref().and_then(|t| t.art_url.clone()) else { return };
        if self.art.as_ref().is_none_or(|(k, _)| *k != key) {
            let data = self.session.as_ref().and_then(read_thumbnail).as_deref().and_then(data_url);
            let Some(data) = data else { return };
            self.art = Some((key.clone(), data));
        }
        if let Some((key, data)) = &self.art {
            let _ = self.app.emit_to(
                crate::island::WINDOW_LABEL,
                ARTWORK_EVENT,
                Artwork { art_url: key.clone(), data_url: data.clone() },
            );
        }
    }

    fn control(&mut self, action: &str, value: Option<f64>) -> bool {
        let Some(s) = self.session.clone() else { return false };
        let v = value.unwrap_or(0.0);
        let done = match action {
            "playPause" => s.TryTogglePlayPauseAsync().and_then(|op| op.get()),
            "next" => s.TrySkipNextAsync().and_then(|op| op.get()),
            "previous" => s.TrySkipPreviousAsync().and_then(|op| op.get()),
            "seek" => {
                let Some(track) = self.state.track.as_ref() else { return false };
                let target = v.clamp(0.0, (track.duration - 1.0).max(0.0));
                let start = s.GetTimelineProperties().and_then(|t| t.StartTime()).map(|t| t.Duration).unwrap_or(0);
                s.TryChangePlaybackPositionAsync(start + (target * 10_000_000.0) as i64).and_then(|op| op.get())
            }
            "shuffle" => s.TryChangeShuffleActiveAsync(v != 0.0).and_then(|op| op.get()),
            "repeat" => {
                let mode = if v != 0.0 { RepeatMode::List } else { RepeatMode::None };
                s.TryChangeAutoRepeatModeAsync(mode).and_then(|op| op.get())
            }
            "volume" => Ok(self.spotify && mixer::set_volume((v / 100.0).clamp(0.0, 1.0) as f32)),
            _ => Ok(false),
        }
        .unwrap_or(false);
        if !done {
            return false;
        }
        // Not every player signals these at once: show them now, as on Linux.
        let optimistic: Option<(String, Value)> = match action {
            "seek" => self.state.track.as_ref().map(|t| {
                let target = v.clamp(0.0, (t.duration - 1.0).max(0.0));
                ("Position".into(), Value::Int((target * 1_000_000.0) as i64))
            }),
            "shuffle" => Some(("Shuffle".into(), Value::Bool(v != 0.0))),
            "repeat" => Some(("LoopStatus".into(), Value::Str(if v != 0.0 { "Playlist" } else { "None" }.into()))),
            "volume" => Some(("Volume".into(), Value::Float((v / 100.0).clamp(0.0, 1.0)))),
            _ => None,
        };
        if let Some(prop) = optimistic {
            self.state.apply(&[prop], now_ms());
            self.emit(true);
        }
        true
    }
}

// ── Reading a session ─────────────────────────────────────────────────────────

fn app_id(s: &Session) -> String {
    s.SourceAppUserModelId().map(|h| h.to_string()).unwrap_or_default()
}

/// "Spotify.exe" for the installer's build, "SpotifyAB.SpotifyMusic_…!Spotify"
/// for the Microsoft Store's.
fn is_spotify(app_id: &str) -> bool {
    app_id.to_ascii_lowercase().contains("spotify")
}

fn text(r: windows::core::Result<windows::core::HSTRING>) -> String {
    r.map(|h| h.to_string().trim().to_string()).unwrap_or_default()
}

/// Everything SMTC says about a session, as MPRIS properties.
fn read_session(s: &Session, spotify: bool, now_ms: f64) -> Vec<(String, Value)> {
    let mut props: Vec<(String, Value)> = Vec::new();
    let app = app_id(s);

    // Metadata. Without a title nothing is loaded (as MPRIS's NoTrack).
    let media = s.TryGetMediaPropertiesAsync().and_then(|op| op.get()).ok();
    let title = media.as_ref().map(|m| text(m.Title())).unwrap_or_default();
    let artist = media.as_ref().map(|m| text(m.Artist())).unwrap_or_default();
    let album = media.as_ref().map(|m| text(m.AlbumTitle())).unwrap_or_default();
    let has_art = media.as_ref().is_some_and(|m| m.Thumbnail().is_ok());

    let timeline = s.GetTimelineProperties().ok();
    let start = timeline.as_ref().and_then(|t| t.StartTime().ok()).map(|t| t.Duration).unwrap_or(0);
    let end = timeline.as_ref().and_then(|t| t.EndTime().ok()).map(|t| t.Duration).unwrap_or(0);
    let length_us = ((end - start).max(0)) / 10;

    let mut meta: Vec<(String, Value)> = Vec::new();
    if !title.is_empty() {
        let id = track_id(&app, &title, &artist, &album);
        meta.push(("mpris:trackid".into(), Value::Str(id.clone())));
        meta.push(("xesam:title".into(), Value::Str(title)));
        meta.push(("xesam:artist".into(), Value::Str(artist)));
        meta.push(("xesam:album".into(), Value::Str(album)));
        meta.push(("mpris:length".into(), Value::Int(length_us)));
        if has_art {
            // Not a URL: the key the cover is sent under (see Follower::artwork).
            meta.push(("mpris:artUrl".into(), Value::Str(format!("smtc-art:{id}"))));
        }
    }
    props.push(("Metadata".into(), Value::Map(meta)));

    // Playback.
    let info = s.GetPlaybackInfo().ok();
    let status = info.as_ref().and_then(|i| i.PlaybackStatus().ok()).unwrap_or(Status::Closed);
    let playing = status == Status::Playing;
    let status_name = match status {
        Status::Playing => "Playing",
        Status::Paused | Status::Changing | Status::Opened => "Paused",
        _ => "Stopped",
    };
    props.push(("PlaybackStatus".into(), Value::Str(status_name.into())));
    if let Some(on) = info.as_ref().and_then(|i| i.IsShuffleActive().ok()).and_then(|r| r.Value().ok()) {
        props.push(("Shuffle".into(), Value::Bool(on)));
    }
    if let Some(mode) = info.as_ref().and_then(|i| i.AutoRepeatMode().ok()).and_then(|r| r.Value().ok()) {
        let name = match mode {
            RepeatMode::Track => "Track",
            RepeatMode::List => "Playlist",
            _ => "None",
        };
        props.push(("LoopStatus".into(), Value::Str(name.into())));
    }

    // Position: where it was when the player last said, run on to now.
    if let Some(t) = &timeline {
        let at = t.Position().map(|p| p.Duration).unwrap_or(0) - start;
        let updated = t.LastUpdatedTime().map(|d| d.UniversalTime).unwrap_or(0);
        let position = position_now(at, updated, playing, now_ms);
        props.push(("Position".into(), Value::Int((position * 1_000_000.0) as i64)));
    }

    if spotify {
        if let Some(v) = mixer::volume() {
            props.push(("Volume".into(), Value::Float(v as f64)));
        }
    }
    props
}

/// A stable id for one track of one app (SMTC gives none).
pub fn track_id(app: &str, title: &str, artist: &str, album: &str) -> String {
    format!("smtc:{app}:{title}\u{1f}{artist}\u{1f}{album}")
}

/// Seconds into the track at `now_ms`, from what the player last reported:
/// `at` (100 ns ticks from the start) when its clock said `updated` (WinRT
/// DateTime). Only run on while playing, and only from a believable stamp —
/// some players never set it.
pub fn position_now(at: i64, updated: i64, playing: bool, now_ms: f64) -> f64 {
    let at = at.max(0) as f64 / 10_000_000.0;
    if !playing || updated <= EPOCH_TICKS {
        return at;
    }
    let updated_ms = (updated - EPOCH_TICKS) as f64 / 10_000.0;
    let elapsed = (now_ms - updated_ms) / 1000.0;
    if (0.0..6.0 * 3600.0).contains(&elapsed) {
        at + elapsed
    } else {
        at
    }
}

fn read_thumbnail(s: &Session) -> Option<Vec<u8>> {
    let media = s.TryGetMediaPropertiesAsync().and_then(|op| op.get()).ok()?;
    let stream = media.Thumbnail().ok()?.OpenReadAsync().ok()?.get().ok()?;
    let size = stream.Size().ok()? as usize;
    if size == 0 || size > ARTWORK_LIMIT {
        return None;
    }
    let reader = DataReader::CreateDataReader(&stream).ok()?;
    let loaded = reader.LoadAsync(size as u32).ok()?.get().ok()? as usize;
    let mut bytes = vec![0u8; loaded];
    reader.ReadBytes(&mut bytes).ok()?;
    Some(bytes)
}

// ── Spotify's volume: its entry in the Windows volume mixer ───────────────────

mod mixer {
    use windows::core::Interface;
    use windows::Win32::Media::Audio::{
        eMultimedia, eRender, IAudioSessionControl2, IAudioSessionManager2, IMMDeviceEnumerator, ISimpleAudioVolume,
        MMDeviceEnumerator,
    };
    use windows::Win32::System::Com::{CoCreateInstance, CLSCTX_ALL};

    /// Spotify's audio sessions on the default output (it may hold several).
    fn spotify_volumes() -> Vec<ISimpleAudioVolume> {
        let pids = crate::platform::pids_named(super::SPOTIFY_EXE);
        if pids.is_empty() {
            return Vec::new();
        }
        let mut out = Vec::new();
        unsafe {
            let Ok(devices) = CoCreateInstance::<_, IMMDeviceEnumerator>(&MMDeviceEnumerator, None, CLSCTX_ALL) else {
                return out;
            };
            let Ok(device) = devices.GetDefaultAudioEndpoint(eRender, eMultimedia) else { return out };
            let Ok(manager) = device.Activate::<IAudioSessionManager2>(CLSCTX_ALL, None) else { return out };
            let Ok(sessions) = manager.GetSessionEnumerator() else { return out };
            let count = sessions.GetCount().unwrap_or(0);
            for i in 0..count {
                let Ok(control) = sessions.GetSession(i) else { continue };
                let Ok(control2) = control.cast::<IAudioSessionControl2>() else { continue };
                let Ok(pid) = control2.GetProcessId() else { continue };
                if pids.contains(&pid) {
                    if let Ok(volume) = control.cast::<ISimpleAudioVolume>() {
                        out.push(volume);
                    }
                }
            }
        }
        out
    }

    /// 0…1, or None when Spotify has no audio session yet.
    pub fn volume() -> Option<f32> {
        spotify_volumes().first().and_then(|v| unsafe { v.GetMasterVolume() }.ok())
    }

    pub fn set_volume(level: f32) -> bool {
        let volumes = spotify_volumes();
        let mut done = false;
        for v in &volumes {
            done |= unsafe { v.SetMasterVolume(level, std::ptr::null()) }.is_ok();
        }
        done
    }
}

// ── Launching ─────────────────────────────────────────────────────────────────

/// Where the two Spotify builds put their executable.
fn spotify_paths() -> Vec<PathBuf> {
    let mut paths = Vec::new();
    if let Some(roaming) = std::env::var_os("APPDATA") {
        paths.push(PathBuf::from(roaming).join("Spotify").join(SPOTIFY_EXE));
    }
    if let Some(local) = std::env::var_os("LOCALAPPDATA") {
        // The Microsoft Store build's execution alias.
        paths.push(PathBuf::from(local).join("Microsoft").join("WindowsApps").join(SPOTIFY_EXE));
    }
    paths
}

pub fn installed() -> bool {
    spotify_paths().iter().any(|p| p.is_file()) || !crate::platform::pids_named(SPOTIFY_EXE).is_empty()
}

/// Brings Spotify forward, or starts it; without it, its download page.
pub fn open() -> bool {
    if crate::platform::focus_app(SPOTIFY_EXE) {
        return true;
    }
    if installed() {
        // Both builds register the spotify: scheme.
        crate::platform::open_url("spotify:");
        return true;
    }
    crate::platform::open_url(DOWNLOAD_URL);
    false
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn position_runs_on_only_while_playing_from_a_believable_stamp() {
        let now_ms = 1_700_000_000_000.0;
        let updated = EPOCH_TICKS + (now_ms as i64 - 2_000) * 10_000; // two seconds ago
        let at = 30 * 10_000_000; // 30 s
        assert!((position_now(at, updated, true, now_ms) - 32.0).abs() < 1e-6);
        assert_eq!(position_now(at, updated, false, now_ms), 30.0);
        // No stamp, or one from the future: what the player said, as is.
        assert_eq!(position_now(at, 0, true, now_ms), 30.0);
        let future = EPOCH_TICKS + (now_ms as i64 + 60_000) * 10_000;
        assert_eq!(position_now(at, future, true, now_ms), 30.0);
    }

    #[test]
    fn smtc_reads_become_the_same_player_state_as_mpris() {
        let id = track_id("Spotify.exe", "Never Gonna Give You Up - Remastered 2022", "Rick Astley", "Whenever");
        let props = vec![
            (
                "Metadata".to_string(),
                Value::Map(vec![
                    ("mpris:trackid".into(), Value::Str(id.clone())),
                    ("xesam:title".into(), Value::Str("Never Gonna Give You Up - Remastered 2022".into())),
                    ("xesam:artist".into(), Value::Str("Rick Astley feat. Nobody".into())),
                    ("xesam:album".into(), Value::Str("Whenever".into())),
                    ("mpris:length".into(), Value::Int(213_000_000)),
                    ("mpris:artUrl".into(), Value::Str(format!("smtc-art:{id}"))),
                ]),
            ),
            ("PlaybackStatus".to_string(), Value::Str("Playing".into())),
            ("LoopStatus".to_string(), Value::Str("Playlist".into())),
            ("Position".to_string(), Value::Int(12_500_000)),
        ];
        let mut state = PlayerState::default();
        state.apply(&props, 1_000.0);
        let track = state.track.clone().unwrap();
        assert_eq!(track.id, id);
        assert_eq!(track.title, "Never Gonna Give You Up");
        assert_eq!(track.artist, "Rick Astley");
        assert_eq!(track.duration, 213.0);
        assert!(state.playing && state.repeat && !state.shuffle);
        assert_eq!(state.position, 12.5);
    }

    #[test]
    fn both_spotify_builds_are_recognised() {
        assert!(is_spotify("Spotify.exe"));
        assert!(is_spotify("SpotifyAB.SpotifyMusic_zpdnekdrzrea0!Spotify"));
        assert!(!is_spotify("MSEdge"));
        assert!(!is_spotify("Chrome"));
    }
}
