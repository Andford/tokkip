// Tokkip's setup window (setup.html, src-tauri/src/setup.rs): install, update,
// repair or remove, with Tokkip there the whole time.

import "./setup.css";
import { invoke } from "@tauri-apps/api/core";
import { watchTheme } from "../core/theme";
import { h, clear } from "../views/dom";
import { TokkipMotion } from "../tokkip/motion";
import { loadLook } from "../tokkip/tokkip";
import { STATE_SHAPES, shapePath, type ShapeSpec } from "../tokkip/shapes";
import { N_, resolveLanguage, setLanguage, systemLanguages, t } from "../i18n/i18n";

watchTheme();
setLanguage(resolveLanguage(null, systemLanguages()));

type Action = "install" | "update" | "reinstall" | "downgrade" | "remove";
type Phase = "ask" | "working" | "done" | "failed";

interface SetupInfo {
  action: Action;
  version: string;
  installedVersion: string;
}

const BUTTON: Record<Action, string> = {
  install: N_("Install"),
  update: N_("Update"),
  reinstall: N_("Reinstall"),
  downgrade: N_("Install"),
  remove: N_("Uninstall"),
};
const WORKING: Record<Action, string> = {
  install: N_("Installing Tokkip…"),
  update: N_("Updating Tokkip…"),
  reinstall: N_("Installing Tokkip…"),
  downgrade: N_("Installing Tokkip…"),
  remove: N_("Uninstalling Tokkip…"),
};

const root = document.getElementById("setup-root")!;
const engine = new TokkipMotion();
engine.look = loadLook();

let info: SetupInfo = { action: "install", version: "", installedVersion: "" };
let phase: Phase = "ask";
let error = "";

/** The shape behind him, as in the island: it melts from one to the next. */
const shape = { from: STATE_SHAPES.idle as ShapeSpec, to: STATE_SHAPES.idle as ShapeSpec, k: 1, turn: 0 };

function shapeFor(p: Phase): ShapeSpec {
  if (p === "working") return STATE_SHAPES.working;
  if (p === "done") return STATE_SHAPES.finished;
  if (p === "failed") return STATE_SHAPES.error;
  return STATE_SHAPES.idle;
}

function setShape(next: ShapeSpec) {
  if (next === shape.to) return;
  shape.from = shape.to;
  shape.to = next;
  shape.k = 0;
}

/** Tokkip, drawn on his shape. */
function hero(): HTMLElement {
  const SVG = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(SVG, "svg");
  svg.setAttribute("viewBox", "0 0 100 100");
  svg.setAttribute("class", "su-shape");
  const path = document.createElementNS(SVG, "path");
  svg.append(path);
  const SIZE = 132;
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  const canvas = h("canvas") as HTMLCanvasElement;
  canvas.width = SIZE * dpr;
  canvas.height = SIZE * dpr;
  canvas.style.width = `${SIZE}px`;
  canvas.style.height = `${SIZE}px`;
  canvas.addEventListener("click", () => engine.greet());
  let last = performance.now();
  const frame = (now: number) => {
    if (!canvas.isConnected) return;
    const ctx = canvas.getContext("2d");
    if (ctx) {
      engine.update(Math.min(0.05, (now - last) / 1000));
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, SIZE, SIZE);
      engine.particleOverhang = 30;
      engine.draw(ctx, SIZE * 0.72, SIZE);
    }
    const dt = Math.min(0.05, (now - last) / 1000);
    shape.k = Math.min(1, shape.k + dt * 2.2);
    const k = 1 - Math.pow(1 - shape.k, 3);
    shape.turn = (shape.turn + (shape.from.spin * (1 - k) + shape.to.spin * k) * dt) % 360;
    path.setAttribute("d", shapePath(50, shape.from, shape.to, k, shape.turn));
    last = now;
    requestAnimationFrame(frame);
  };
  requestAnimationFrame(frame);
  return h("div", { class: "su-hero" }, svg, canvas);
}

function button(text: string, kind: string, onclick: () => void, disabled = false): HTMLElement {
  return h("button", { class: `su-btn ${kind}`, text, onclick, disabled });
}

function close() {
  void invoke("setup_close");
}

function title(): string {
  const removing = info.action === "remove";
  if (phase === "failed") return removing ? t("Couldn't uninstall Tokkip") : t("Setup couldn't finish");
  if (phase === "working") return t(WORKING[info.action]);
  if (phase === "done") return removing ? t("Tokkip is uninstalled") : t("Tokkip is ready");
  return removing ? t("Uninstall Tokkip?") : "Tokkip";
}

function text(): string {
  const v = { version: info.version, installed: info.installedVersion };
  if (phase === "failed") return t("Nothing changed. Try again, or restart the PC first.");
  if (phase === "working") return t("This takes a moment.");
  if (phase === "done") {
    return info.action === "remove"
      ? t("Thanks for giving him a home. Your settings stay on this PC in case you come back.")
      : t("He lives at the top of your screen. Open him to say hello.");
  }
  switch (info.action) {
    case "update": return t("Update from {installed} to {version}. Your settings and your Tokkip stay as they are.", v);
    case "reinstall": return t("Version {version} is already installed. Install it again to repair it.", v);
    case "downgrade": return t("This PC has a newer version ({installed}). Installing {version} replaces it.", v);
    case "remove": return t("Sorry to see you go. Your settings stay on this PC in case you come back.");
    default: return t("Your little friend at the top of your screen: reminders, tasks, your music and AI chat, without leaving what you're doing.");
  }
}

async function go() {
  phase = "working";
  error = "";
  engine.setState("working");
  render();
  try {
    await invoke("setup_run");
    phase = "done";
    engine.setState("idle");
    engine.triggerEmote(info.action === "remove" ? "love" : "happy", 3);
  } catch (e) {
    phase = "failed";
    error = String(e);
    engine.setState("idle");
    engine.triggerEmote("surprised", 2.5);
  }
  render();
}

function render() {
  setShape(shapeFor(phase));
  clear(root);
  const bar = h("div", { class: "su-bar", "data-tauri-drag-region": "" },
    phase === "working" ? h("span") : h("button", { class: "su-close", text: "✕", title: t("Close"), "aria-label": t("Close"), onclick: close }));

  const actions = h("div", { class: "su-actions" });
  if (phase === "working") {
    actions.append(h("div", { class: "su-progress", role: "progressbar", "aria-label": title() }));
  } else if (phase === "done") {
    if (info.action === "remove") actions.append(button(t("Close"), "primary", close));
    else actions.append(button(t("Open Tokkip"), "primary", () => void invoke("setup_open_app")), button(t("Close"), "text", close));
  } else {
    const removing = info.action === "remove";
    const main = phase === "failed" ? t("Try again") : t(BUTTON[info.action]);
    actions.append(
      button(main, removing ? "danger" : "primary", () => void go()),
      button(removing ? t("Keep Tokkip") : t("Close"), "text", close),
    );
  }

  const body = h("div", { class: "su-body" },
    hero(),
    h("h1", { text: title() }),
    h("p", { class: "su-text", text: text() }),
  );
  if (phase === "failed" && error) body.append(h("p", { class: "su-error", text: error }));
  if (phase === "ask" && info.action !== "remove") {
    body.append(h("details", { class: "su-details" },
      h("summary", { text: t("What this installs") }),
      h("ul", {},
        h("li", { text: t("Tokkip, just for you: no administrator needed") }),
        h("li", { text: t("Start menu and desktop shortcuts") }),
        h("li", { text: t("Nothing leaves your PC except what you connect yourself") }),
      )));
  }
  body.append(actions, h("p", { class: "su-maker", text: `${t("Made by LoperStack")}${info.version ? ` · ${info.version}` : ""}` }));
  root.append(bar, body);
}

render();
invoke<SetupInfo>("setup_info").then((i) => {
  info = i;
  render();
  engine.greet();
}).catch(() => render());
