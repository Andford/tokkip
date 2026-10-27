// The Wellness pill and card: three tabs in the overview's
// left card — Tasks, Focus (Pomodoro) and Breathe — next to Tokkip, who does
// the breathing. The card is built once and updated in place: a text field or
// a button must never be rebuilt under the pointer.

import { h, svg, clear } from "./dom";
import { ICONS } from "./icons";
import type { AgentTask } from "../core/state";
import {
  BREATHE_MINUTES, Wellness, WELLNESS_COLOR, WELLNESS_ID, breathFrame, clock, eventDay, eventMs, isPaused, isRunning,
  nextEvent, orderedTasks, pendingCount, pomodoroLeft, pomodoroProgress, upcomingEvents, type CalEvent, type WellnessTab,
} from "../core/wellness";
import { WellnessControl, refreshGoogle } from "../island/wellness";
import { Bridge } from "../core/bridge";
import { createMiniBot } from "../tokkip/minibots";
import { language, t, tl } from "../i18n/i18n";

/** calendar, on the 24×24 grid of views/icons.ts. */
const CALENDAR_ICON = "M7 2h2v2h6V2h2v2h2a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2V2zm12 8H5v9h14v-9z";

export interface WellnessHost {
  el: HTMLElement;
  sync(): void;
  /** Every frame while on screen; true while the breath ring moves. */
  tick(nowMs: number): boolean;
}

// ── Pill ──────────────────────────────────────────────────────────────────────

/** The pill's label: the clock while a block runs, else the pill's name. */
export function wellnessPillLabel(name: string, nowMs: number): string {
  const b = breathFrame(Wellness.breathing, nowMs);
  if (b.active) return `${t("Breathe")} ${clock(b.left)}`;
  const p = Wellness.pomodoro;
  if (p.phase !== "idle") return clock(pomodoroLeft(p, nowMs));
  // An event within the hour: how long until it.
  const next = nextEvent(Wellness.events, nowMs);
  if (next) {
    const min = Math.ceil((eventMs(next.start) - nowMs) / 60_000);
    if (min <= 60) return t("In {minutes} min", { minutes: min });
  }
  return name;
}

/** "10:30", in the island's language. */
export function eventTime(e: CalEvent): string {
  if (e.allDay) return t("All day");
  try {
    return new Date(eventMs(e.start)).toLocaleTimeString(language(), { hour: "numeric", minute: "2-digit" });
  } catch {
    return new Date(eventMs(e.start)).toTimeString().slice(0, 5);
  }
}

export function buildWellnessPill(task: AgentTask, onTap: () => void): { el: HTMLElement; sync(): void } {
  const color = task.color;
  const mini = createMiniBot(task, 24);
  const label = h("span", { class: "lbl" });
  const pill = h("div", { class: "pill wl-pill", onclick: onTap }, mini, label);
  pill.style.borderColor = `${color}24`;
  pill.addEventListener("mouseenter", () => {
    pill.style.background = `${color}2e`;
    pill.style.borderColor = `${color}8c`;
    pill.style.boxShadow = `0 2px 10px ${color}59`;
  });
  pill.addEventListener("mouseleave", () => {
    pill.style.background = "";
    pill.style.borderColor = `${color}24`;
    pill.style.boxShadow = "";
  });
  const sync = () => {
    const text = wellnessPillLabel(task.name, Date.now());
    if (label.textContent !== text) label.textContent = text;
    pill.classList.toggle("wl-live", isRunning(Wellness.pomodoro) || Wellness.breathing.startedAt != null);
  };
  sync();
  return { el: pill, sync };
}

// ── Card ──────────────────────────────────────────────────────────────────────

export function buildWellnessCard(): WellnessHost {
  // Tabs
  const badge = h("b", { class: "wl-badge" });
  const tabs: Record<WellnessTab, HTMLElement> = {
    tasks: h("button", { class: "wl-tab", onclick: () => WellnessControl.setTab("tasks") }, svg(ICONS.check, 9, { stroke: 2.6 }), h("span", { text: tl("Tasks") }), badge),
    focus: h("button", { class: "wl-tab", onclick: () => WellnessControl.setTab("focus") }, svg(ICONS.timer, 9), h("span", { text: tl("Focus") })),
    breathe: h("button", { class: "wl-tab", onclick: () => WellnessControl.setTab("breathe") }, h("i", { class: "wl-tab-dot" }), h("span", { text: tl("Breathe") })),
    agenda: h("button", { class: "wl-tab", onclick: () => WellnessControl.setTab("agenda") }, svg(CALENDAR_ICON, 9), h("span", { text: tl("Agenda") })),
  };
  tabs.tasks.title = t("Tasks");
  tabs.focus.title = t("Focus");
  tabs.breathe.title = t("Breathe");
  tabs.agenda.title = t("Agenda");
  const tabRow = h("div", { class: "wl-tabs" }, tabs.tasks, tabs.focus, tabs.breathe, tabs.agenda);

  // ── Tasks ──
  const input = h("input", {
    class: "wl-input", type: "text", maxlength: "200", autocomplete: "off", spellcheck: "false",
  }) as HTMLInputElement;
  input.placeholder = t("New task…");
  const addBtn = h("button", { class: "wl-add", title: tl("Add") }, svg(ICONS.plus, 9, { stroke: 2.6 }));
  const submit = () => {
    if (WellnessControl.addTask(input.value)) input.value = "";
  };
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      submit();
    }
    // Escape still closes the island; nothing else leaks out of the field.
    if (e.key !== "Escape") e.stopPropagation();
  });
  addBtn.addEventListener("click", submit);
  const list = h("div", { class: "wl-list" });
  const clearBtn = h("button", { class: "wl-icon wl-clear", title: tl("Clear done"), onclick: () => WellnessControl.clearDone() },
    svg(ICONS.xmark, 7, { stroke: 2.6 }));
  const tasksPane = h("div", { class: "wl-pane wl-tasks" },
    h("div", { class: "wl-new" }, input, addBtn, clearBtn),
    list,
  );
  let listKey = "";

  function syncTasks() {
    const tasks = orderedTasks(Wellness.tasks);
    const key = tasks.map((x) => `${x.id}:${x.done ? 1 : 0}:${x.text}`).join("|");
    if (key !== listKey) {
      listKey = key;
      clear(list);
      if (tasks.length === 0) {
        list.append(h("div", { class: "wl-empty", text: tl("Nothing to do yet — add your first task.") }));
      }
      for (const task of tasks) {
        const box = h("button", { class: task.done ? "wl-check on" : "wl-check", title: task.done ? t("Mark as pending") : t("Mark as done") },
          task.done ? svg(ICONS.check, 7, { stroke: 3 }) : null);
        box.addEventListener("click", () => WellnessControl.toggleTask(task.id));
        const del = h("button", { class: "wl-del", title: t("Delete") }, svg(ICONS.xmark, 6, { stroke: 2.6 }));
        del.addEventListener("click", () => WellnessControl.removeTask(task.id));
        const text = h("span", { class: "wl-text", text: task.text, title: task.text });
        list.append(h("div", { class: task.done ? "wl-row done" : "wl-row" }, box, text, del));
      }
    }
    clearBtn.style.display = Wellness.tasks.some((x) => x.done) ? "" : "none";
  }

  /** The Tasks tab says how many are left, on every tab. */
  function syncBadge() {
    const pending = pendingCount(Wellness.tasks);
    const text = Wellness.tasks.length === 0 ? "" : pending === 0 ? "✓" : String(pending);
    if (badge.textContent !== text) badge.textContent = text;
    badge.style.display = text ? "" : "none";
    badge.title = pending === 0 ? t("All done!") : t("Pending: {count}", { count: pending });
  }

  // ── Focus ──
  const { ring, fill: ringFill } = buildRing(46);
  const time = h("div", { class: "wl-time" });
  const phaseLabel = h("div", { class: "wl-phase" });
  const working = h("div", { class: "wl-working" });
  const mainLabel = h("span");
  const mainBtn = h("button", { class: "btn primary wl-main", onclick: () => WellnessControl.playPause() }, mainLabel);
  const skipBtn = h("button", { class: "wl-icon", title: tl("Skip"), onclick: () => WellnessControl.skip() }, svg(ICONS.forward, 9));
  const resetBtn = h("button", { class: "wl-icon", title: tl("Reset"), onclick: () => WellnessControl.reset() }, svg(ICONS.arrowClockwise, 9, { stroke: 2.4 }));
  // One row: the ring, then the time, the phase and the buttons stacked.
  const focusPane = h("div", { class: "wl-pane wl-focus" },
    h("div", { class: "wl-clock" }, ring, h("div", { class: "wl-clock-text" },
      h("div", { class: "wl-time-row" }, time, phaseLabel),
      working,
      h("div", { class: "wl-actions" }, mainBtn, skipBtn, resetBtn),
    )),
  );

  function syncFocus(now: number) {
    const p = Wellness.pomodoro;
    time.textContent = clock(pomodoroLeft(p, now));
    const phase = p.phase === "focus" ? t("Focus") : p.phase === "break" ? t("Short break")
      : p.phase === "longBreak" ? t("Long break") : t("Ready to focus");
    phaseLabel.textContent = `${phase}${isPaused(p) ? ` · ${t("Paused")}` : ""}`;
    const c = 2 * Math.PI * RING_R;
    ringFill.style.strokeDasharray = `${c}`;
    ringFill.style.strokeDashoffset = `${c * (1 - pomodoroProgress(p, now))}`;
    ringFill.style.stroke = p.phase === "focus" || p.phase === "idle" ? WELLNESS_COLOR : "#4ADE80";
    const next = orderedTasks(Wellness.tasks).find((x) => !x.done);
    const today = p.today > 0 ? t("{count} today", { count: p.today }) : "";
    working.textContent = next && p.phase === "focus" ? t("On: {task}", { task: next.text }) : today;
    working.title = working.textContent;
    const label = p.phase === "idle" ? t("Start") : isPaused(p) ? t("Resume") : t("Pause");
    if (mainLabel.textContent !== label) mainLabel.textContent = label;
    skipBtn.style.visibility = p.phase === "idle" ? "hidden" : "";
    resetBtn.style.visibility = p.phase === "idle" ? "hidden" : "";
  }

  // ── Breathe ──
  const pick = h("div", { class: "wl-durations" });
  for (const m of BREATHE_MINUTES) {
    pick.append(h("button", { class: "btn secondary wl-dur", onclick: () => WellnessControl.startBreathing(m) },
      h("span", { text: t("{minutes} min", { minutes: m }) })));
  }
  const breatheIdle = h("div", { class: "wl-breathe-idle" },
    h("div", { class: "wl-title", text: tl("Breathe with Tokkip"), title: tl("Inhale as Tokkip grows, exhale as it shrinks.") }),
    h("div", { class: "wl-sub", text: tl("Inhale as Tokkip grows, exhale as it shrinks.") }),
    pick,
  );
  const orb = h("div", { class: "wl-orb" });
  const cue = h("div", { class: "wl-cue" });
  const left = h("div", { class: "wl-sub" });
  const stopBtn = h("button", { class: "link-btn wl-stop", text: tl("End session"), onclick: () => WellnessControl.stopBreathing() });
  const breatheActive = h("div", { class: "wl-breathe-active" },
    h("div", { class: "wl-breathe-row" }, orb, h("div", { class: "wl-breathe-text" }, cue, left)),
    stopBtn,
  );
  const breathePane = h("div", { class: "wl-pane wl-breathe" }, breatheIdle, breatheActive);
  let lastCue = "";

  function syncBreathe(now: number) {
    const b = breathFrame(Wellness.breathing, now);
    breatheIdle.style.display = b.active ? "none" : "";
    breatheActive.style.display = b.active ? "" : "none";
    if (!b.active) return;
    const text = b.phase === "in" ? t("Breathe in…") : t("Breathe out…");
    if (text !== lastCue) {
      lastCue = text;
      cue.textContent = text;
    }
    left.textContent = t("{time} left · {count} breaths", { time: clock(b.left), count: b.breaths });
    orb.style.transform = `scale(${0.55 + 0.45 * b.fill})`;
  }

  // ── Agenda ──
  const agendaList = h("div", { class: "wl-list wl-agenda" });
  const agendaMsg = h("div", { class: "wl-agenda-msg" });
  const agendaPane = h("div", { class: "wl-pane" }, agendaList, agendaMsg);
  let agendaKey = "";

  function syncAgenda(now: number) {
    const g = Wellness.google;
    const minuteNow = Math.floor(now / 60_000);
    const key = [g?.configured, g?.connected, Wellness.eventsAt, Wellness.googleError, minuteNow, language()].join("~");
    if (key === agendaKey) return;
    agendaKey = key;
    clear(agendaList);
    clear(agendaMsg);
    if (!g?.connected) {
      agendaList.style.display = "none";
      agendaMsg.append(
        h("div", { class: "wl-title", text: tl("Your Google Calendar here") }),
        h("div", { class: "wl-sub", text: g?.configured
          ? tl("Sign in once; Tokkip reminds you 10 minutes before each event.")
          : tl("Set up the Google connection in Settings → Integrations.") }),
        g?.configured
          ? h("button", { class: "btn primary wl-main", onclick: () => connectGoogle() }, h("span", { text: tl("Connect Google") }))
          : h("button", { class: "btn secondary wl-main", onclick: () => void Bridge.openSettingsWindow() }, h("span", { text: tl("Settings…") })),
      );
      return;
    }
    agendaList.style.display = "";
    const events = upcomingEvents(Wellness.events, now);
    const next = nextEvent(Wellness.events, now);
    let shownDay = "today";
    for (const e of events) {
      const day = eventDay(e, now);
      if (day !== shownDay) {
        shownDay = day;
        agendaList.append(h("div", { class: "wl-day", text: day === "tomorrow" ? tl("Tomorrow") : tl("Later") }));
      }
      const started = !e.allDay && eventMs(e.start) <= now;
      const row = h("div", { class: e === next ? "wl-event next" : started ? "wl-event now" : "wl-event" },
        h("i", { class: "wl-event-dot", style: `background:${e.color ?? WELLNESS_COLOR}` }),
        h("span", { class: "wl-event-time", text: started ? t("Now") : eventTime(e) }),
        h("span", { class: "wl-text", text: e.title, title: e.title }),
      );
      if (e.meetUrl) {
        const url = e.meetUrl;
        row.append(h("button", { class: "link-btn wl-join", text: tl("Join"), onclick: () => WellnessControl.openUrl(url) }));
      }
      agendaList.append(row);
    }
    if (events.length === 0) {
      agendaList.append(h("div", { class: "wl-empty", text: tl("Nothing else today — enjoy it.") }));
    }
    if (Wellness.googleError) {
      agendaMsg.append(h("div", { class: "wl-error", text: t("Google didn't answer — retrying soon."), title: Wellness.googleError }));
    }
  }

  function connectGoogle() {
    Bridge.googleConnect().then(
      () => void refreshGoogle(),
      (err) => {
        Wellness.googleError = String(err?.message ?? err);
        void refreshGoogle();
      },
    );
  }

  // ── Root ──
  const panes: Record<WellnessTab, HTMLElement> = { tasks: tasksPane, focus: focusPane, breathe: breathePane, agenda: agendaPane };
  const head = h("div", { class: "wl-head" }, tabRow);
  // Tokkip Healthy PC's line: how the PC feels (when that app is installed).
  const pcScore = h("b", { class: "wl-pc-score" });
  const pcText = h("span", { class: "wl-pc-text" });
  const pcLine = h("div", { class: "wl-pc" }, pcScore, pcText);
  pcLine.style.display = "none";
  let pcKey = "";
  function syncPc() {
    const note = Wellness.pc;
    const show = !!note && Date.now() - note.at < 3 * 86_400_000;
    pcLine.style.display = show ? "" : "none";
    if (!show || !note) return;
    const key = `${note.seq}`;
    if (key === pcKey) return;
    pcKey = key;
    pcScore.textContent = String(note.score);
    pcScore.dataset.level = note.score >= 80 ? "good" : note.score >= 60 ? "ok" : note.score >= 40 ? "meh" : "bad";
    pcText.textContent = note.message;
    pcLine.title = note.message;
  }
  const el = h("div", { class: "wl-card" }, head, pcLine, tasksPane, focusPane, breathePane, agendaPane);
  let shownTab: WellnessTab | null = null;

  function sync() {
    const now = Date.now();
    // A running breath always shows its tab.
    const tab: WellnessTab = Wellness.breathing.startedAt != null ? "breathe" : Wellness.tab;
    if (tab !== shownTab) {
      shownTab = tab;
      for (const [k, pane] of Object.entries(panes)) pane.style.display = k === tab ? "" : "none";
      for (const [k, b] of Object.entries(tabs)) b.classList.toggle("on", k === tab);
    }
    syncBadge();
    syncPc();
    if (tab === "tasks") syncTasks();
    else if (tab === "focus") syncFocus(now);
    else if (tab === "agenda") syncAgenda(now);
    else syncBreathe(now);
    if (el.isConnected !== false) WellnessControl.shown();
    tabs.focus.classList.toggle("live", isRunning(Wellness.pomodoro));
  }

  return {
    el,
    sync,
    tick(nowMs: number) {
      if (shownTab !== "breathe" || Wellness.breathing.startedAt == null) return false;
      void nowMs;
      syncBreathe(Date.now());
      return true;
    },
  };
}

const RING_R = 20;

/** The Pomodoro ring: a faint track and, over it, the part gone by. */
function buildRing(size: number): { ring: SVGSVGElement; fill: SVGCircleElement } {
  const NS = "http://www.w3.org/2000/svg";
  const ring = document.createElementNS(NS, "svg");
  ring.setAttribute("viewBox", "0 0 46 46");
  ring.setAttribute("width", String(size));
  ring.setAttribute("height", String(size));
  ring.setAttribute("aria-hidden", "true");
  ring.setAttribute("class", "wl-ring");
  const circle = (cls: string) => {
    const c = document.createElementNS(NS, "circle");
    c.setAttribute("cx", "23");
    c.setAttribute("cy", "23");
    c.setAttribute("r", String(RING_R));
    c.setAttribute("fill", "none");
    c.setAttribute("stroke-width", "3.5");
    c.setAttribute("class", cls);
    return c;
  };
  const track = circle("track");
  track.setAttribute("stroke", "rgba(255,255,255,0.08)");
  const fill = circle("fill");
  fill.setAttribute("stroke-linecap", "round");
  fill.setAttribute("transform", "rotate(-90 23 23)");
  ring.append(track, fill);
  return { ring, fill };
}

export { WELLNESS_ID };
