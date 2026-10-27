// The Wellness pill: tasks, the Pomodoro and guided breathing
// in src/core/wellness.ts, Tokkip's breath in src/tokkip/motion.ts, and the card
// in src/views/wellness.ts.

import { beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import { installFakeDom } from "./fakedom.mjs";
import {
  DEFAULT_POMODORO, IDLE_BREATHING, IDLE_POMODORO, TASK_LIMIT, Wellness, addTask, breathFrame, clearDone, clock,
  dayKey, finishPhase, isPaused, isRunning, orderedTasks, parsePomodoro, parseTasks, pause, pendingCount,
  pomodoroLeft, pomodoroProgress, removeTask, resetPomodoro, resume, startBreathing, startFocus, tickPomodoro,
  toggleTask, agendaWindow, dueReminders, eventDay, eventMs, fromGoogle, localIso, nextEvent, reminderKey,
  upcomingEvents,
} from "../src/core/wellness.ts";
import { TokkipMotion, breathTransform } from "../src/tokkip/motion.ts";
import { buildWellnessCard, wellnessPillLabel } from "../src/views/wellness.ts";
import { setLanguage } from "../src/i18n/i18n.ts";

installFakeDom();

const T0 = new Date(2026, 9, 10, 9, 0, 0).getTime();
const MIN = 60_000;

beforeEach(() => {
  setLanguage("en");
  Wellness.tasks = [];
  Wellness.pomodoro = { ...IDLE_POMODORO };
  Wellness.breathing = { ...IDLE_BREATHING };
  Wellness.tab = "tasks";
  Wellness.google = null;
  Wellness.events = [];
  Wellness.eventsAt = 0;
  Wellness.googleError = "";
});

// ── Tasks ─────────────────────────────────────────────────────────────────────

test("a task is added trimmed, an empty one is not", () => {
  let tasks = addTask([], "  Llamar a   Bunday  ", T0, "a");
  assert.deepEqual(tasks, [{ id: "a", text: "Llamar a Bunday", done: false, createdAt: T0 }]);
  assert.equal(addTask(tasks, "   ", T0, "b").length, 1);
  tasks = addTask(tasks, "x".repeat(500), T0, "c");
  assert.equal(tasks[1].text.length, 200);
});

test("done tasks sink, can be undone, removed or cleared", () => {
  let tasks = ["one", "two", "three"].reduce((acc, text, i) => addTask(acc, text, T0 + i, String(i)), []);
  tasks = toggleTask(tasks, "0");
  assert.deepEqual(orderedTasks(tasks).map((t) => t.text), ["two", "three", "one"]);
  assert.equal(pendingCount(tasks), 2);
  assert.equal(pendingCount(toggleTask(tasks, "0")), 3);
  assert.deepEqual(removeTask(tasks, "1").map((t) => t.id), ["0", "2"]);
  assert.deepEqual(clearDone(tasks).map((t) => t.id), ["1", "2"]);
});

test("a full list drops its oldest finished tasks, never a pending one", () => {
  let tasks = [];
  for (let i = 0; i < TASK_LIMIT; i++) tasks = addTask(tasks, `t${i}`, T0, String(i));
  tasks = toggleTask(tasks, "5");
  tasks = addTask(tasks, "new", T0, "new");
  assert.equal(tasks.length, TASK_LIMIT);
  assert.ok(!tasks.some((t) => t.id === "5"));
  // Nothing finished to drop: the list grows rather than lose work.
  tasks = addTask(tasks, "newer", T0, "newer");
  assert.equal(tasks.length, TASK_LIMIT + 1);
});

test("a stored list keeps only well-formed tasks", () => {
  assert.deepEqual(parseTasks(null), []);
  assert.deepEqual(parseTasks("nope"), []);
  assert.deepEqual(
    parseTasks([{ id: "a", text: "ok", done: true, createdAt: 5 }, { id: 2, text: "bad" }, { id: "c", text: "  " }, null]),
    [{ id: "a", text: "ok", done: true, createdAt: 5 }],
  );
});

// ── Pomodoro ──────────────────────────────────────────────────────────────────

test("a focus block runs 25 minutes, pauses and resumes where it was", () => {
  let p = startFocus(IDLE_POMODORO, T0);
  assert.ok(isRunning(p));
  assert.equal(pomodoroLeft(p, T0), 25 * 60);
  assert.equal(pomodoroLeft(p, T0 + 10 * MIN), 15 * 60);
  assert.ok(Math.abs(pomodoroProgress(p, T0 + 5 * MIN) - 0.2) < 1e-9);
  p = pause(p, T0 + 10 * MIN);
  assert.ok(isPaused(p) && !isRunning(p));
  assert.equal(pomodoroLeft(p, T0 + 60 * MIN), 15 * 60, "paused, the clock stands still");
  p = resume(p, T0 + 60 * MIN);
  assert.equal(pomodoroLeft(p, T0 + 61 * MIN), 14 * 60);
});

test("focus leads to a break, every fourth to a long one, and breaks lead back to focus", () => {
  let p = startFocus(IDLE_POMODORO, T0);
  let now = T0;
  const phases = [];
  for (let i = 0; i < 8; i++) {
    now = p.endsAt;
    const r = tickPomodoro(p, now);
    phases.push(`${r.event}:${r.next.phase}`);
    p = r.next;
  }
  assert.deepEqual(phases, [
    "focusDone:break", "breakDone:focus", "focusDone:break", "breakDone:focus",
    "focusDone:break", "breakDone:focus", "focusDone:longBreak", "breakDone:focus",
  ]);
  assert.equal(p.today, 4);
  assert.equal(p.completed, 0, "the count starts over after a long break");
});

test("nothing happens before the time is up; a skipped focus block does not count", () => {
  const p = startFocus(IDLE_POMODORO, T0);
  assert.deepEqual(tickPomodoro(p, T0 + MIN), { next: p, event: null });
  const skipped = finishPhase(p, T0 + MIN, DEFAULT_POMODORO, false).next;
  assert.equal(skipped.phase, "break");
  assert.equal(skipped.today, 0);
  assert.equal(resetPomodoro(skipped).phase, "idle");
});

test("the day's count starts over on a new day", () => {
  let p = finishPhase(startFocus(IDLE_POMODORO, T0), T0 + 25 * MIN).next;
  assert.equal(p.today, 1);
  assert.equal(p.day, dayKey(T0));
  p = startFocus(resetPomodoro(p), T0 + 24 * 60 * MIN);
  assert.equal(p.today, 0);
});

test("a stored Pomodoro is read back safely", () => {
  assert.deepEqual(parsePomodoro(null), IDLE_POMODORO);
  assert.deepEqual(parsePomodoro({ phase: "weird", endsAt: 5 }).phase, "idle");
  const p = parsePomodoro({ phase: "focus", endsAt: T0, pausedLeft: null, completed: 2, today: 3, day: "x" });
  assert.equal(p.endsAt, T0);
  assert.equal(p.today, 3);
});

// ── Breathing ─────────────────────────────────────────────────────────────────

test("a breath is 4 s in and 6 s out, Tokkip fills and empties with it", () => {
  const b = startBreathing(1, T0);
  const at = (s) => breathFrame(b, T0 + s * 1000);
  assert.equal(at(0).phase, "in");
  assert.equal(at(0).fill, 0);
  assert.equal(at(2).phase, "in");
  assert.ok(Math.abs(at(2).fill - 0.5) < 1e-9);
  assert.equal(at(4).phase, "out");
  assert.equal(at(4).fill, 1);
  assert.ok(Math.abs(at(7).fill - 0.5) < 1e-9);
  assert.equal(at(25).breaths, 2);
  assert.equal(at(25).left, 35);
  assert.ok(at(59).active);
  assert.ok(at(60).done && !at(60).active);
  assert.ok(!breathFrame(IDLE_BREATHING, T0).active);
});

test("Tokkip grows from its base with the breath, and only during a session", () => {
  assert.deepEqual(breathTransform(0, 1), { sx: 1, sy: 1 });
  assert.deepEqual(breathTransform(1, 1), { sx: 1.12, sy: 1.18 });
  assert.deepEqual(breathTransform(1, 0), { sx: 1, sy: 1 });
  const engine = new TokkipMotion();
  engine.setBreathing(true, 0.5);
  assert.equal(engine.eyeOverride, "closed");
  assert.ok(engine.busy);
  engine.setBreathing(false);
  assert.equal(engine.eyeOverride, null);
});

// ── The card and the pill ─────────────────────────────────────────────────────

test("the pill shows the clock while a block or a breath runs", () => {
  assert.equal(wellnessPillLabel("Bienestar", T0), "Bienestar");
  Wellness.pomodoro = startFocus(IDLE_POMODORO, T0);
  assert.equal(wellnessPillLabel("Bienestar", Date.now()), clock(pomodoroLeft(Wellness.pomodoro, Date.now())));
  Wellness.breathing = startBreathing(5, Date.now());
  assert.match(wellnessPillLabel("Bienestar", Date.now()), /^Breathe \d:\d\d$/);
});

test("the card shows the tab picked, and a running breath above all", () => {
  const card = buildWellnessCard();
  Wellness.tasks = addTask([], "Propuesta Bunday", T0, "a");
  card.sync();
  assert.ok(card.el.textContent.includes("Propuesta Bunday"));
  assert.ok(card.el.find(".wl-badge")[0].textContent === "1");

  Wellness.tab = "focus";
  card.sync();
  assert.ok(card.el.textContent.includes("25:00"));
  assert.ok(card.el.textContent.includes("Ready to focus"));

  Wellness.tab = "tasks";
  Wellness.breathing = startBreathing(1, Date.now());
  card.sync();
  assert.ok(card.el.textContent.includes("Breathe in…"));
  assert.equal(card.tick(0), true, "the orb moves every frame during a session");

  setLanguage("es");
  Wellness.breathing = { ...IDLE_BREATHING };
  Wellness.tab = "breathe";
  card.sync();
  assert.ok(card.el.textContent.includes("Respira con Tokkip"));
  assert.ok(card.el.textContent.includes("5 min"));
});

// ── Agenda (Google Calendar) ──────────────────────────────────────────────────

const at = (h, m = 0, dayOffset = 0) => new Date(2026, 9, 13 + dayOffset, h, m).getTime();
const ev = (id, startMs, endMs, over = {}) => ({
  id, title: id, start: localIso(startMs), end: localIso(endMs), allDay: false, meetUrl: null, color: null, ...over,
});

test("the agenda asks for today and tomorrow, in local time", () => {
  const { timeMin, timeMax } = agendaWindow(at(15, 30));
  assert.equal(eventMs(timeMin), at(0));
  assert.equal(eventMs(timeMax), at(0, 0, 2));
  assert.match(timeMin, /^2026-10-13T00:00:00[+-]\d\d:\d\d$/);
});

test("all-day events are local days; past events drop off; tomorrow is told apart", () => {
  const now = at(12);
  const allDay = { ...ev("cumple", 0, 0), allDay: true, start: "2026-10-13", end: "2026-10-14" };
  const past = ev("desayuno", at(8), at(9));
  const lunch = ev("comida", at(14), at(15));
  const tomorrow = ev("junta", at(10, 0, 1), at(11, 0, 1));
  assert.equal(eventMs("2026-10-13"), at(0));
  assert.deepEqual(upcomingEvents([tomorrow, past, lunch, allDay], now).map((e) => e.id), ["cumple", "comida", "junta"]);
  assert.equal(eventDay(lunch, now), "today");
  assert.equal(eventDay(tomorrow, now), "tomorrow");
  assert.equal(nextEvent([allDay, lunch, tomorrow], now).id, "comida");
});

test("a reminder comes 10 minutes before, once, and again if the event moves", () => {
  const meeting = ev("bunday", at(10), at(11));
  const announced = new Set();
  assert.deepEqual(dueReminders([meeting], at(9, 45), announced), []);
  assert.deepEqual(dueReminders([meeting], at(9, 50), announced).map((e) => e.id), ["bunday"]);
  announced.add(reminderKey(meeting));
  assert.deepEqual(dueReminders([meeting], at(9, 55), announced), []);
  const moved = ev("bunday", at(10, 30), at(11, 30));
  assert.deepEqual(dueReminders([moved], at(10, 25), announced).map((e) => e.id), ["bunday"]);
  // All-day events and long-gone ones never ring.
  assert.deepEqual(dueReminders([{ ...meeting, allDay: true }], at(9, 55), new Set()), []);
  assert.deepEqual(dueReminders([meeting], at(10, 5), new Set()), []);
});

test("Google's tasks become the card's, flagged as remote", () => {
  assert.deepEqual(fromGoogle([{ id: "g1", title: "Llamar", done: false, updated: "" }]), [
    { id: "g1", text: "Llamar", done: false, createdAt: 0, remote: true },
  ]);
  assert.equal(parseTasks([{ id: "g1", text: "x", done: false, createdAt: 0, remote: true }])[0].remote, true);
});

test("the pill counts down to an event within the hour", () => {
  const now = Date.now();
  Wellness.events = [ev("junta", now + 12 * MIN - 1000, now + 60 * MIN)];
  assert.equal(wellnessPillLabel("Bienestar", now), "In 12 min");
  Wellness.events = [ev("junta", now + 90 * MIN, now + 120 * MIN)];
  assert.equal(wellnessPillLabel("Bienestar", now), "Bienestar");
});

test("the Agenda tab: set up first, then sign in, then the day's events", () => {
  const card = buildWellnessCard();
  Wellness.tab = "agenda";
  Wellness.google = { configured: false, connected: false, email: null };
  card.sync();
  assert.ok(card.el.textContent.includes("Set up the Google connection"));
  Wellness.google = { configured: true, connected: false, email: null };
  Wellness.eventsAt = 1;
  card.sync();
  assert.ok(card.el.textContent.includes("Connect Google"));
  const now = Date.now();
  Wellness.google = { configured: true, connected: true, email: "andy@example.com" };
  Wellness.events = [
    ev("Junta Bunday", now + 20 * MIN, now + 80 * MIN, { meetUrl: "https://meet.google.com/x" }),
  ];
  Wellness.eventsAt = 2;
  card.sync();
  assert.ok(card.el.textContent.includes("Junta Bunday"));
  assert.ok(card.el.textContent.includes("Join"));
  Wellness.events = [];
  Wellness.eventsAt = 3;
  card.sync();
  assert.ok(card.el.textContent.includes("Nothing else today"));
});
