// The Wellness pill: a task list, a Pomodoro timer and guided
// breathing, all local. This file is the pure part — state, rules, storage —
// so it can be tested without a page. The card is views/wellness.ts, the
// clock and the alerts island/wellness.ts, Tokkip's breathing tokkip/engine.ts.
//
// Everything lives in this window's localStorage (as the plan usage does):
// nothing leaves the PC, and a missing or broken store just starts empty.

export const WELLNESS_ID = "integration_wellness";
export const WELLNESS_COLOR = "#A78BFA";

export type WellnessTab = "tasks" | "focus" | "breathe" | "agenda";

// ── Tasks ─────────────────────────────────────────────────────────────────────

export interface Task {
  id: string;
  text: string;
  done: boolean;
  /** Unix ms. */
  createdAt: number;
  /** A Google Tasks task (its id is Google's); local ones have none. */
  remote?: boolean;
}

/** Longest task text kept; the card shows one line anyway. */
export const TASK_MAX_LEN = 200;
/** A list that grows past this drops its oldest finished tasks. */
export const TASK_LIMIT = 100;

export function addTask(tasks: readonly Task[], text: string, now: number, id: string): Task[] {
  const clean = text.replace(/\s+/g, " ").trim().slice(0, TASK_MAX_LEN);
  if (!clean) return [...tasks];
  const next = [...tasks, { id, text: clean, done: false, createdAt: now }];
  while (next.length > TASK_LIMIT) {
    const i = next.findIndex((t) => t.done);
    if (i < 0) break;
    next.splice(i, 1);
  }
  return next;
}

export function toggleTask(tasks: readonly Task[], id: string): Task[] {
  return tasks.map((t) => (t.id === id ? { ...t, done: !t.done } : t));
}

export function removeTask(tasks: readonly Task[], id: string): Task[] {
  return tasks.filter((t) => t.id !== id);
}

export function clearDone(tasks: readonly Task[]): Task[] {
  return tasks.filter((t) => !t.done);
}

/** Pending first (oldest on top), then the finished ones. */
export function orderedTasks(tasks: readonly Task[]): Task[] {
  return [...tasks.filter((t) => !t.done), ...tasks.filter((t) => t.done)];
}

export function pendingCount(tasks: readonly Task[]): number {
  return tasks.filter((t) => !t.done).length;
}

/** What a stored value becomes: only well-formed tasks survive. */
export function parseTasks(raw: unknown): Task[] {
  if (!Array.isArray(raw)) return [];
  const out: Task[] = [];
  for (const t of raw) {
    if (!t || typeof t !== "object") continue;
    const o = t as Record<string, unknown>;
    if (typeof o.id !== "string" || typeof o.text !== "string" || !o.text.trim()) continue;
    out.push({
      id: o.id,
      text: o.text.slice(0, TASK_MAX_LEN),
      done: o.done === true,
      createdAt: typeof o.createdAt === "number" ? o.createdAt : 0,
      ...(o.remote === true ? { remote: true } : {}),
    });
  }
  return out.slice(-TASK_LIMIT);
}

// ── Pomodoro ──────────────────────────────────────────────────────────────────

export type PomodoroPhase = "idle" | "focus" | "break" | "longBreak";

export interface PomodoroConfig {
  focusMin: number;
  breakMin: number;
  longBreakMin: number;
  /** A long break after this many focus blocks. */
  cyclesBeforeLong: number;
}

export const DEFAULT_POMODORO: PomodoroConfig = { focusMin: 25, breakMin: 5, longBreakMin: 15, cyclesBeforeLong: 4 };

export interface Pomodoro {
  phase: PomodoroPhase;
  /** Unix ms the running phase ends; null while paused or idle. */
  endsAt: number | null;
  /** Seconds left, while paused. */
  pausedLeft: number | null;
  /** Focus blocks finished since the last long break. */
  completed: number;
  /** Focus blocks finished today (for the card). */
  today: number;
  /** The day `today` counts (YYYY-MM-DD, local). */
  day: string;
}

export const IDLE_POMODORO: Pomodoro = { phase: "idle", endsAt: null, pausedLeft: null, completed: 0, today: 0, day: "" };

export function dayKey(nowMs: number): string {
  const d = new Date(nowMs);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function phaseMinutes(phase: PomodoroPhase, cfg: PomodoroConfig = DEFAULT_POMODORO): number {
  switch (phase) {
    case "focus": return cfg.focusMin;
    case "break": return cfg.breakMin;
    case "longBreak": return cfg.longBreakMin;
    default: return cfg.focusMin;
  }
}

/** Seconds left in the phase (the full focus block while idle). */
export function pomodoroLeft(p: Pomodoro, nowMs: number, cfg: PomodoroConfig = DEFAULT_POMODORO): number {
  if (p.phase === "idle") return cfg.focusMin * 60;
  if (p.pausedLeft != null) return p.pausedLeft;
  if (p.endsAt == null) return phaseMinutes(p.phase, cfg) * 60;
  return Math.max(0, Math.ceil((p.endsAt - nowMs) / 1000));
}

/** 0…1 of the phase gone by. */
export function pomodoroProgress(p: Pomodoro, nowMs: number, cfg: PomodoroConfig = DEFAULT_POMODORO): number {
  if (p.phase === "idle") return 0;
  const total = phaseMinutes(p.phase, cfg) * 60;
  return Math.min(1, Math.max(0, 1 - pomodoroLeft(p, nowMs, cfg) / total));
}

export function isRunning(p: Pomodoro): boolean {
  return p.phase !== "idle" && p.endsAt != null && p.pausedLeft == null;
}

export function isPaused(p: Pomodoro): boolean {
  return p.phase !== "idle" && p.pausedLeft != null;
}

function withDay(p: Pomodoro, nowMs: number): Pomodoro {
  const day = dayKey(nowMs);
  return p.day === day ? p : { ...p, day, today: 0 };
}

export function startFocus(p: Pomodoro, nowMs: number, cfg: PomodoroConfig = DEFAULT_POMODORO): Pomodoro {
  return { ...withDay(p, nowMs), phase: "focus", endsAt: nowMs + cfg.focusMin * 60_000, pausedLeft: null };
}

export function pause(p: Pomodoro, nowMs: number, cfg: PomodoroConfig = DEFAULT_POMODORO): Pomodoro {
  if (!isRunning(p)) return p;
  return { ...p, pausedLeft: pomodoroLeft(p, nowMs, cfg), endsAt: null };
}

export function resume(p: Pomodoro, nowMs: number): Pomodoro {
  if (!isPaused(p)) return p;
  return { ...p, endsAt: nowMs + (p.pausedLeft ?? 0) * 1000, pausedLeft: null };
}

/** Back to idle; the day's count stays. */
export function resetPomodoro(p: Pomodoro): Pomodoro {
  return { ...p, phase: "idle", endsAt: null, pausedLeft: null, completed: 0 };
}

export type PomodoroEvent = "focusDone" | "breakDone" | null;

/**
 * Ends the phase now (its time ran out, or "skip"): a focus block leads to a
 * break — a long one every `cyclesBeforeLong` — and a break back to focus.
 * Only a focus block that ran out on its own counts as done.
 */
export function finishPhase(
  p: Pomodoro, nowMs: number, cfg: PomodoroConfig = DEFAULT_POMODORO, counted = true,
): { next: Pomodoro; event: PomodoroEvent } {
  const base = withDay(p, nowMs);
  if (p.phase === "focus") {
    const completed = counted ? base.completed + 1 : base.completed;
    const long = counted && completed >= cfg.cyclesBeforeLong;
    const phase: PomodoroPhase = long ? "longBreak" : "break";
    return {
      next: {
        ...base,
        phase,
        completed: long ? 0 : completed,
        today: counted ? base.today + 1 : base.today,
        endsAt: nowMs + phaseMinutes(phase, cfg) * 60_000,
        pausedLeft: null,
      },
      event: "focusDone",
    };
  }
  if (p.phase === "break" || p.phase === "longBreak") {
    return { next: { ...base, phase: "focus", endsAt: nowMs + cfg.focusMin * 60_000, pausedLeft: null }, event: "breakDone" };
  }
  return { next: p, event: null };
}

/** The clock's job: a running phase whose time is up ends. */
export function tickPomodoro(
  p: Pomodoro, nowMs: number, cfg: PomodoroConfig = DEFAULT_POMODORO,
): { next: Pomodoro; event: PomodoroEvent } {
  if (!isRunning(p) || (p.endsAt ?? 0) > nowMs) return { next: p, event: null };
  return finishPhase(p, nowMs, cfg);
}

export function parsePomodoro(raw: unknown): Pomodoro {
  if (!raw || typeof raw !== "object") return { ...IDLE_POMODORO };
  const o = raw as Record<string, unknown>;
  const phase = ["idle", "focus", "break", "longBreak"].includes(o.phase as string) ? (o.phase as PomodoroPhase) : "idle";
  const num = (v: unknown) => (typeof v === "number" && Number.isFinite(v) ? v : null);
  return {
    phase,
    endsAt: phase === "idle" ? null : num(o.endsAt),
    pausedLeft: phase === "idle" ? null : num(o.pausedLeft),
    completed: Math.max(0, num(o.completed) ?? 0),
    today: Math.max(0, num(o.today) ?? 0),
    day: typeof o.day === "string" ? o.day : "",
  };
}

// ── Breathing ─────────────────────────────────────────────────────────────────

/** The sessions offered, in minutes. */
export const BREATHE_MINUTES = [1, 5, 10] as const;
/** Seconds in, out: a calm 6 breaths a minute. */
export const INHALE_S = 4;
export const EXHALE_S = 6;
const CYCLE_S = INHALE_S + EXHALE_S;

export interface Breathing {
  /** Unix ms the session started; null when none runs. */
  startedAt: number | null;
  minutes: number;
}

export const IDLE_BREATHING: Breathing = { startedAt: null, minutes: 1 };

export interface BreathFrame {
  active: boolean;
  phase: "in" | "out";
  /** 0…1 through the current inhale or exhale. */
  phaseProgress: number;
  /** How full Tokkip is: 0 empty … 1 full, eased. */
  fill: number;
  /** Seconds left in the session. */
  left: number;
  /** Breaths taken so far. */
  breaths: number;
  done: boolean;
}

const easeInOut = (x: number) => 0.5 - 0.5 * Math.cos(Math.PI * Math.min(1, Math.max(0, x)));

export function startBreathing(minutes: number, nowMs: number): Breathing {
  return { startedAt: nowMs, minutes };
}

export function breathFrame(b: Breathing, nowMs: number): BreathFrame {
  if (b.startedAt == null) {
    return { active: false, phase: "in", phaseProgress: 0, fill: 0, left: 0, breaths: 0, done: false };
  }
  const total = b.minutes * 60;
  const elapsed = Math.max(0, (nowMs - b.startedAt) / 1000);
  const done = elapsed >= total;
  const t = Math.min(elapsed, total);
  const inCycle = t % CYCLE_S;
  const phase = inCycle < INHALE_S ? "in" : "out";
  const phaseProgress = phase === "in" ? inCycle / INHALE_S : (inCycle - INHALE_S) / EXHALE_S;
  const fill = done ? 0 : phase === "in" ? easeInOut(phaseProgress) : 1 - easeInOut(phaseProgress);
  return {
    active: !done,
    phase,
    phaseProgress,
    fill,
    left: Math.max(0, Math.ceil(total - elapsed)),
    breaths: Math.floor(t / CYCLE_S),
    done,
  };
}

// ── Google Calendar & Tasks ───────────────────────────────────────────────────

export interface GoogleStatus {
  /** A client ID and secret are saved. */
  configured: boolean;
  /** Signed in. */
  connected: boolean;
  email: string | null;
}

export interface CalEvent {
  id: string;
  title: string;
  /** RFC 3339, or YYYY-MM-DD for an all-day event. */
  start: string;
  end: string;
  allDay: boolean;
  meetUrl: string | null;
  color: string | null;
}

export interface GTask {
  id: string;
  title: string;
  done: boolean;
  updated: string;
}

/** Minutes before an event Tokkip comes to say so. */
export const REMINDER_LEAD_MIN = 10;

/** "2026-10-13" as local midnight; anything else as an instant. */
export function eventMs(when: string): number {
  const day = /^(\d{4})-(\d{2})-(\d{2})$/.exec(when);
  if (day) return new Date(Number(day[1]), Number(day[2]) - 1, Number(day[3])).getTime();
  return Date.parse(when);
}

/** RFC 3339 with the local offset, as Google's timeMin wants it. */
export function localIso(ms: number): string {
  const d = new Date(ms);
  const pad = (n: number) => String(Math.trunc(Math.abs(n))).padStart(2, "0");
  const off = -d.getTimezoneOffset();
  const sign = off >= 0 ? "+" : "-";
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}` +
    `${sign}${pad(off / 60)}:${pad(off % 60)}`;
}

/** What the agenda asks Google for: today from midnight to the end of tomorrow. */
export function agendaWindow(nowMs: number): { timeMin: string; timeMax: string } {
  const d = new Date(nowMs);
  const start = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const end = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 2).getTime();
  return { timeMin: localIso(start), timeMax: localIso(end) };
}

export type EventDay = "today" | "tomorrow" | "later";

export function eventDay(e: CalEvent, nowMs: number): EventDay {
  const d = new Date(nowMs);
  const tomorrow = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1).getTime();
  const after = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 2).getTime();
  const at = eventMs(e.start);
  return at < tomorrow ? "today" : at < after ? "tomorrow" : "later";
}

/** Events not over yet, soonest first; all-day ones lead their day. */
export function upcomingEvents(events: readonly CalEvent[], nowMs: number): CalEvent[] {
  return events
    .filter((e) => eventMs(e.end) > nowMs)
    .sort((a, b) => eventMs(a.start) - eventMs(b.start) || Number(b.allDay) - Number(a.allDay));
}

/** The next timed event that has not started, if any. */
export function nextEvent(events: readonly CalEvent[], nowMs: number): CalEvent | null {
  return upcomingEvents(events, nowMs).find((e) => !e.allDay && eventMs(e.start) > nowMs) ?? null;
}

/** Timed events starting within the lead time that Tokkip has not announced yet. */
export function dueReminders(
  events: readonly CalEvent[], nowMs: number, announced: ReadonlySet<string>, leadMin = REMINDER_LEAD_MIN,
): CalEvent[] {
  return events.filter((e) => {
    if (e.allDay || announced.has(reminderKey(e))) return false;
    const until = eventMs(e.start) - nowMs;
    return until > -60_000 && until <= leadMin * 60_000;
  });
}

/** An event moved to another time is announced again. */
export function reminderKey(e: CalEvent): string {
  return `${e.id}@${e.start}`;
}

/** Google's tasks as the card's: pending in Google's order, done after. */
export function fromGoogle(list: readonly GTask[]): Task[] {
  return list.map((g, i) => ({ id: g.id, text: g.title, done: g.done, createdAt: i, remote: true }));
}

// ── Formatting ────────────────────────────────────────────────────────────────

/** 1500 → "25:00". */
export function clock(seconds: number): string {
  const s = Math.max(0, Math.round(seconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

// ── The page's copy, and its storage ─────────────────────────────────────────

// ── Tokkip Healthy PC ─────────────────────────────────────────────────────────

/** The note Tokkip Healthy PC leaves about this PC (src-tauri/src/healthy_pc.rs). */
export interface PcNote {
  /** Grows with every new note: what tells news from an old one. */
  seq: number;
  /** Unix ms. */
  at: number;
  kind: "clean" | "care" | "status";
  /** 0…100. */
  score: number;
  mood: string;
  /** What Tokkip says, already in the user's language. */
  message: string;
  /** Bytes freed, for a clean-up. */
  bytes?: number;
}

/** The note's text made safe, or null when it isn't one. */
export function parsePcNote(raw: string | null | undefined): PcNote | null {
  if (!raw) return null;
  let o: Record<string, unknown>;
  try {
    o = JSON.parse(raw);
  } catch {
    return null;
  }
  if (!o || typeof o !== "object" || o.app !== "tokkip-healthy-pc") return null;
  const num = (v: unknown) => (typeof v === "number" && Number.isFinite(v) ? v : NaN);
  const seq = num(o.seq), at = num(o.at), score = num(o.score);
  if (!(seq >= 0) || !(at > 0) || !(score >= 0 && score <= 100)) return null;
  if (o.kind !== "clean" && o.kind !== "care" && o.kind !== "status") return null;
  if (typeof o.message !== "string" || typeof o.mood !== "string") return null;
  const bytes = num(o.bytes);
  return {
    seq, at, kind: o.kind, score: Math.round(score), mood: o.mood.slice(0, 16),
    message: o.message.replace(/\s+/g, " ").trim().slice(0, 200),
    ...(bytes >= 0 ? { bytes } : {}),
  };
}

/** Whether a note is news worth saying out loud: newer than the last one heard, and recent. */
export function pcNoteIsNews(note: PcNote, lastSeq: number, nowMs: number): boolean {
  return note.seq > lastSeq && note.kind !== "status" && nowMs - note.at < 10 * 60_000;
}

const STORE = { tasks: "tokkip.wellness.tasks", pomodoro: "tokkip.wellness.pomodoro", tab: "tokkip.wellness.tab", pcSeq: "tokkip.healthyPc.seq" };

function read(key: string): unknown {
  try {
    const raw = globalThis.localStorage?.getItem(key);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function write(key: string, value: unknown) {
  try {
    globalThis.localStorage?.setItem(key, JSON.stringify(value));
  } catch {
    /* storage is a nicety: the session goes on without it */
  }
}

function parseTab(raw: unknown): WellnessTab {
  return raw === "focus" || raw === "breathe" || raw === "agenda" ? raw : "tasks";
}

export const Wellness = {
  /** Google: whether it is set up and signed in (null until asked). */
  google: null as GoogleStatus | null,
  events: [] as CalEvent[],
  /** Unix ms of the last good calendar read; 0 = never. */
  eventsAt: 0,
  /** The last Google error, for the card ("" = fine). */
  googleError: "",
  tasks: parseTasks(read(STORE.tasks)),
  pomodoro: parsePomodoro(read(STORE.pomodoro)),
  breathing: { ...IDLE_BREATHING } as Breathing,
  tab: parseTab(read(STORE.tab)),
  /** Tokkip Healthy PC's latest note (null: not installed, or nothing yet). */
  pc: null as PcNote | null,
  /** The last note already said out loud. */
  pcSeq: typeof read(STORE.pcSeq) === "number" ? (read(STORE.pcSeq) as number) : -1,

  saveTasks() {
    write(STORE.tasks, this.tasks);
  },
  savePomodoro() {
    write(STORE.pomodoro, this.pomodoro);
  },
  saveTab() {
    write(STORE.tab, this.tab);
  },
  savePcSeq() {
    write(STORE.pcSeq, this.pcSeq);
  },
};

let seq = 0;
export function newTaskId(nowMs: number): string {
  seq = (seq + 1) % 1000;
  return `${nowMs.toString(36)}-${seq}-${Math.random().toString(36).slice(2, 6)}`;
}

/** Something on the card is moving: the clock must tick. */
export function wellnessBusy(nowMs: number): boolean {
  return isRunning(Wellness.pomodoro) || breathFrame(Wellness.breathing, nowMs).active;
}
