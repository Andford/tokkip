// The Wellness pill's clock and its moments: what happens when
// a Pomodoro block or a breathing session ends, and every action the card
// offers. The rules are in core/wellness.ts, the card in views/wellness.ts.
//
// The clock only runs while something is moving (a running Pomodoro, a
// breathing session): one tick a second, and the page is told only while the
// island is open, so a hidden island costs nothing.

import {
  Wellness, WELLNESS_ID, addTask, agendaWindow, breathFrame, clearDone, dueReminders, finishPhase, fromGoogle,
  isPaused, isRunning, newTaskId, pause, reminderKey, removeTask, resetPomodoro, resume, startBreathing, startFocus,
  tickPomodoro, toggleTask, wellnessBusy, type PomodoroEvent, type WellnessTab, parsePcNote, pcNoteIsNews,
} from "../core/wellness";
import { State } from "../core/state";
import { Sound } from "../core/sound";
import { Bridge } from "../core/bridge";
import type { BotEmoteName, IslandViewName } from "../core/layout";

export interface WellnessHost {
  emote(name: BotEmoteName): void;
  /** Opens the island on this view (an alert). */
  alert(view: IslandViewName): void;
  setPinned(on: boolean): void;
  ensureRunning(): void;
}

let host: WellnessHost | null = null;
let timer: ReturnType<typeof setInterval> | null = null;
/** The breathing session pinned the island open, and must let it go. */
let pinnedForBreath = false;

export function registerWellness(h: WellnessHost) {
  host = h;
  // Google and the reminders: once a minute, and only the minute's work.
  setInterval(minute, 60_000);
  // Tokkip Healthy PC's note: a tiny file, read every 15 s.
  setInterval(() => void readPcNote(), 15_000);
  void readPcNote();
  void refreshGoogle(true);
  // A Pomodoro that ran while Tokkip was closed: catch up, quietly.
  const now = Date.now();
  if (isRunning(Wellness.pomodoro) && (Wellness.pomodoro.endsAt ?? 0) <= now) {
    Wellness.pomodoro = resetPomodoro(Wellness.pomodoro);
    Wellness.savePomodoro();
  }
  syncClock();
}

function syncClock() {
  const busy = wellnessBusy(Date.now());
  if (busy && timer == null) timer = setInterval(tick, 1000);
  else if (!busy && timer != null) {
    clearInterval(timer);
    timer = null;
  }
}

function tick() {
  const now = Date.now();
  const { next, event } = tickPomodoro(Wellness.pomodoro, now);
  if (event) {
    Wellness.pomodoro = next;
    Wellness.savePomodoro();
    announce(event);
  }
  const b = breathFrame(Wellness.breathing, now);
  if (b.done) finishBreathing(true);
  syncClock();
  if (State.mode === "expanded" || event) State.notify();
}

/** A phase ended on its own: Tokkip says so, and the island opens on the card. */
function announce(event: PomodoroEvent) {
  if (!event) return;
  Sound.play(event === "focusDone" ? "finish" : "greet");
  showCard(Wellness.breathing.startedAt != null ? "breathe" : "focus");
  host?.emote(event === "focusDone" ? "proud" : "happy");
}

function showCard(tab: WellnessTab) {
  if (State.paused) return;
  Wellness.tab = Wellness.breathing.startedAt != null ? "breathe" : tab;
  if (State.tasks.some((t) => t.id === WELLNESS_ID)) State.setFocus(WELLNESS_ID);
  host?.alert("overview");
}

function finishBreathing(completed: boolean) {
  Wellness.breathing = { ...Wellness.breathing, startedAt: null };
  if (pinnedForBreath) {
    pinnedForBreath = false;
    host?.setPinned(false);
  }
  if (completed) {
    Sound.play("love");
    host?.emote("love");
  }
  syncClock();
  State.notify();
}

// ── Tokkip Healthy PC ─────────────────────────────────────────────────────────

/**
 * Reads the note Tokkip Healthy PC leaves. A new clean-up or a "needs a
 * breather" is said once: a sound, Tokkip's face and the card opening with
 * the line on top. A plain status only updates the line.
 */
async function readPcNote() {
  const note = parsePcNote(await Bridge.healthyPcNote());
  if (!note) return;
  const changed = Wellness.pc?.seq !== note.seq;
  Wellness.pc = note;
  // The first note we ever see is old news: just remember it.
  if (Wellness.pcSeq < 0) {
    Wellness.pcSeq = note.seq;
    Wellness.savePcSeq();
  } else if (pcNoteIsNews(note, Wellness.pcSeq, Date.now())) {
    Wellness.pcSeq = note.seq;
    Wellness.savePcSeq();
    if (!State.paused) {
      Sound.play(note.kind === "clean" ? "finish" : "question");
      host?.emote(note.kind === "clean" ? "proud" : "yawn");
      showCard(Wellness.tab);
    }
  }
  if (changed) State.notify();
}

// ── Google Calendar & Tasks ───────────────────────────────────────────────────

const declared = () => State.settings.activeIntegrations.includes(WELLNESS_ID) || State.mainPillId === WELLNESS_ID;
const connected = () => Wellness.google?.connected === true;
/** Reminders already shown, by event and start time. */
const announced = new Set<string>();
let lastTasksAt = 0;
let syncing = false;
let minutes = 0;

function minute() {
  minutes += 1;
  checkReminders();
  // The calendar every 5 minutes, the tasks every 2, and only for a declared pill.
  if (!declared() || !connected() || State.paused) return;
  if (minutes % 5 === 0) void refreshEvents();
  if (minutes % 2 === 0) void refreshTasks();
}

function checkReminders() {
  const due = dueReminders(Wellness.events, Date.now(), announced);
  if (due.length === 0) return;
  for (const e of due) announced.add(reminderKey(e));
  if (!declared() || State.paused) return;
  Sound.play("approval");
  showCard("agenda");
  host?.emote("surprised");
}

function googleFailed(err: unknown) {
  const msg = String((err as Error)?.message ?? err ?? "");
  Wellness.googleError = msg || "error";
  if (/signed out|not connected/.test(msg) && Wellness.google) Wellness.google = { ...Wellness.google, connected: false };
  State.notify();
}

async function refreshEvents() {
  try {
    const { timeMin, timeMax } = agendaWindow(Date.now());
    Wellness.events = await Bridge.googleEvents(timeMin, timeMax);
    Wellness.eventsAt = Date.now();
    Wellness.googleError = "";
    checkReminders();
    State.notify();
  } catch (err) {
    googleFailed(err);
  }
}

async function refreshTasks() {
  lastTasksAt = Date.now();
  try {
    const list = await Bridge.googleTasks();
    Wellness.tasks = fromGoogle(list);
    Wellness.saveTasks();
    Wellness.googleError = "";
    State.notify();
  } catch (err) {
    googleFailed(err);
  }
}

/** The tasks made before Google was connected go up to it, once. */
async function uploadLocalTasks() {
  const local = Wellness.tasks.filter((t) => !t.remote && !t.done);
  for (const t of local) {
    try {
      await Bridge.googleTaskAdd(t.text);
      Wellness.tasks = Wellness.tasks.filter((x) => x.id !== t.id);
    } catch (err) {
      googleFailed(err);
      return;
    }
  }
  Wellness.tasks = Wellness.tasks.filter((t) => t.remote);
}

/** Asks Google again: the status, then — signed in — the calendar and the tasks. */
export async function refreshGoogle(quiet = false) {
  if (syncing) return;
  syncing = true;
  try {
    Wellness.google = (await Bridge.googleStatus()) ?? null;
    if (!connected()) {
      // Signed out: the card goes back to the tasks kept on this PC.
      if (Wellness.tasks.some((t) => t.remote)) {
        Wellness.tasks = Wellness.tasks.filter((t) => !t.remote);
        Wellness.saveTasks();
      }
      Wellness.events = [];
      if (!quiet) State.notify();
      return;
    }
    if (State.paused) return;
    await uploadLocalTasks();
    await Promise.all([refreshEvents(), refreshTasks()]);
  } finally {
    syncing = false;
    State.notify();
  }
}

/** Runs a Google call after the card already shows its result; on failure, reads Google again. */
function remote(op: () => Promise<unknown>) {
  if (!connected()) return;
  void op().then(
    () => {
      Wellness.googleError = "";
    },
    (err) => {
      googleFailed(err);
      void refreshTasks();
    },
  );
}

// ── What the card can do ──────────────────────────────────────────────────────

export const WellnessControl = {
  setTab(tab: WellnessTab) {
    if (Wellness.tab === tab) return;
    Wellness.tab = tab;
    Wellness.saveTab();
    Sound.play("blip");
    State.notify();
  },

  /** The card is on screen: a list older than a minute is read again. */
  shown() {
    if (!connected() || State.paused || Date.now() - lastTasksAt < 60_000) return;
    void refreshTasks();
    if (Date.now() - Wellness.eventsAt > 60_000) void refreshEvents();
  },

  openUrl(url: string) {
    if (url.startsWith("https://")) void Bridge.openUrl(url);
  },

  addTask(text: string): boolean {
    const now = Date.now();
    const before = Wellness.tasks.length;
    const id = newTaskId(now);
    Wellness.tasks = addTask(Wellness.tasks, text, now, id);
    if (Wellness.tasks.length === before) return false;
    Wellness.saveTasks();
    Sound.play("pop");
    State.notify();
    if (connected()) {
      const added = Wellness.tasks.find((t) => t.id === id)!;
      void Bridge.googleTaskAdd(added.text).then(
        (g) => {
          // Swap the stand-in for Google's own task.
          Wellness.tasks = Wellness.tasks.map((t) => (t.id === id ? { ...t, id: g.id, remote: true } : t));
          Wellness.saveTasks();
          State.notify();
        },
        (err) => googleFailed(err),
      );
    }
    return true;
  },

  toggleTask(id: string) {
    const was = Wellness.tasks.find((t) => t.id === id);
    if (!was) return;
    Wellness.tasks = toggleTask(Wellness.tasks, id);
    Wellness.saveTasks();
    if (was.remote) remote(() => Bridge.googleTaskSetDone(id, !was.done));
    if (!was.done) {
      // Done: Tokkip's little jump.
      Sound.play("approve");
      host?.emote("happy");
    } else {
      Sound.play("blip");
    }
    State.notify();
  },

  removeTask(id: string) {
    const was = Wellness.tasks.find((t) => t.id === id);
    if (was?.remote) remote(() => Bridge.googleTaskDelete(id));
    Wellness.tasks = removeTask(Wellness.tasks, id);
    Wellness.saveTasks();
    Sound.play("blip");
    State.notify();
  },

  clearDone() {
    if (Wellness.tasks.some((t) => t.remote && t.done)) remote(() => Bridge.googleTasksClearDone());
    Wellness.tasks = clearDone(Wellness.tasks);
    Wellness.saveTasks();
    Sound.play("blip");
    State.notify();
  },

  /** Start, pause or resume — the card's main button. */
  playPause() {
    const now = Date.now();
    const p = Wellness.pomodoro;
    if (p.phase === "idle") {
      Wellness.pomodoro = startFocus(p, now);
      Sound.play("work");
      host?.emote("proud");
    } else if (isPaused(p)) {
      Wellness.pomodoro = resume(p, now);
      Sound.play("blip");
    } else {
      Wellness.pomodoro = pause(p, now);
      Sound.play("blip");
    }
    Wellness.savePomodoro();
    syncClock();
    State.notify();
  },

  /** Ends the phase now; a skipped focus block does not count. */
  skip() {
    if (Wellness.pomodoro.phase === "idle") return;
    const { next } = finishPhase(Wellness.pomodoro, Date.now(), undefined, false);
    Wellness.pomodoro = next;
    Wellness.savePomodoro();
    Sound.play("blip");
    syncClock();
    State.notify();
  },

  reset() {
    Wellness.pomodoro = resetPomodoro(Wellness.pomodoro);
    Wellness.savePomodoro();
    Sound.play("blip");
    syncClock();
    State.notify();
  },

  startBreathing(minutes: number) {
    Wellness.breathing = startBreathing(minutes, Date.now());
    Sound.play("open");
    // The island stays open for the whole session, as a watch's would.
    if (!State.isPinned) {
      pinnedForBreath = true;
      host?.setPinned(true);
    }
    host?.ensureRunning();
    syncClock();
    State.notify();
  },

  stopBreathing() {
    if (Wellness.breathing.startedAt == null) return;
    finishBreathing(false);
    Sound.play("close");
  },
};
