// The launch greeting: the island opens and Tokkip drops in out of the top of
// the screen, lands with a bounce and a burst of twinkles, looks left and
// right, hops, waves with a "¡Hola!" bubble, gives a little bow and a wink,
// then settles. When the island folds he shrinks into his spot in the compact
// island while the pills' little Tokkips pop up beside him.
//
// Everything is laid out in a 640 × 150 space, the expanded island's.

import { DEFAULT_LOOK, loadLook, type TokkipLook } from "./tokkip";
import { drawFigure, figure, restPaw, twinkleShape, type EyeShape, type FigMouth } from "./figure";
import { closeCurve } from "../core/anim";
import { Sound } from "../core/sound";
import { COMPACT_W, NOTCH_H } from "../core/layout";
import { t } from "../i18n/i18n";
import { palette } from "../core/theme";

// ── Timing (seconds) ──────────────────────────────────────────────────────────

const T = {
  drop0: 0.2,
  land: 0.62,
  look0: 0.95,
  hop0: 1.55,
  wave0: 1.75,
  wave1: 3.05,
  bow0: 3.15,
  bow1: 3.6,
  wink: 3.7,
  end: 4.6,
  /** Left alone, it folds this long after the start. */
  autoLeave: 4.9,
  /** How long he takes to shrink into the compact island (the island's own curve). */
  fold: 0.34,
};

export const GREETING_END = T.end;

/** The greeting's tune, played from the start. */
const GREETING_SOUND = "greeting";

// ── Geometry ──────────────────────────────────────────────────────────────────

export const GREETING_W = 640;
export const GREETING_H = 150;
/** His size (body height) and resting place in the open island. */
const SIZE = 64;
const REST = { x: 320, y: 98 };
const CARD = { x: 10, y: 36, w: 620, h: 104, r: 20 };
/** Studio units per body height: the body is 210 tall (−90 … 120), centred at y = 15. */
const BODY_H = 210;
const BODY_MID = 15;

/** Where Tokkip and the minis sit in the compact island. */
const COMPACT = {
  x: GREETING_W / 2 - COMPACT_W / 2 + 40,
  y: NOTCH_H / 2,
  size: 20,
  minisX: GREETING_W / 2 + COMPACT_W / 2 - 40,
  minisScale: Math.min(1, Math.max(0, NOTCH_H - 4) / 28),
};

// ── Little maths ──────────────────────────────────────────────────────────────

const clamp = (v: number, a: number, b: number) => Math.max(a, Math.min(b, v));
const lerp = (a: number, b: number, k: number) => a + (b - a) * k;
const seg = (t: number, a: number, b: number) => clamp((t - a) / (b - a), 0, 1);
const bump = (t: number, a: number, b: number) => {
  const k = seg(t, a, b);
  return k > 0 && k < 1 ? Math.sin(Math.PI * k) : 0;
};
const smooth = (k: number) => k * k * (3 - 2 * k);
const outBack = (k: number) => 1 + 2.4 * Math.pow(k - 1, 3) + 1.4 * Math.pow(k - 1, 2);
const easeIn = (k: number) => k * k;

// ── Pose ──────────────────────────────────────────────────────────────────────

export interface Pose {
  /** Centre of his body, and his body height, in the 640 × 150 space. */
  x: number;
  y: number;
  size: number;
  sx: number;
  sy: number;
  tilt: number;
  eye: EyeShape;
  mouth: FigMouth;
  /** Eyelids, 1 open. */
  open: number;
  turn: number;
  nod: number;
  earL: number;
  earR: number;
  droop: number;
  /** His right paw up waving, 0…1, and how long it has waved. */
  wave: number;
  waveT: number;
  /** The "¡Hola!" bubble, 0…1. */
  bubble: number;
  /** The card behind him and the twinkles, 0…1. */
  card: number;
  fx: number;
  /** The compact island's little Tokkips, 0…1. */
  minis: number;
}

function openPose(t: number): Pose {
  // Size: grows in as he drops, so he never sticks out of the opening island.
  const size = t < T.drop0 ? 0 : lerp(SIZE * 0.3, SIZE, outBack(seg(t, T.drop0, T.land)));

  // Falls from the top of the screen, lands, bounces once, settles; a hop later.
  let y: number;
  if (t < T.drop0) y = COMPACT.y;
  else if (t < T.land) y = lerp(COMPACT.y, REST.y, easeIn(seg(t, T.drop0, T.land)));
  else y = REST.y - 14 * bump(t, T.land, T.land + 0.3) - 26 * bump(t, T.hop0, T.hop0 + 0.38);
  // A bow: a little dip forward.
  y += 4 * bump(t, T.bow0, T.bow1);

  // Squash on each landing, stretch on the way up.
  const land1 = bump(t, T.land - 0.04, T.land + 0.12);
  const land2 = bump(t, T.land + 0.28, T.land + 0.4);
  const up = bump(t, T.land + 0.08, T.land + 0.24) + bump(t, T.hop0 + 0.02, T.hop0 + 0.2);
  const land3 = bump(t, T.hop0 + 0.34, T.hop0 + 0.48);
  const fall = t >= T.drop0 && t < T.land ? seg(t, T.drop0, T.land) * 0.12 : 0;
  const sx = 1 + 0.16 * land1 + 0.08 * land2 + 0.12 * land3 - 0.08 * up - fall * 0.5;
  const sy = 1 - 0.16 * land1 - 0.08 * land2 - 0.12 * land3 + 0.1 * up + fall;

  // Looks left, then right, then at you.
  let turn = 0;
  if (t >= T.look0 && t < T.hop0) {
    const k = (t - T.look0) / (T.hop0 - T.look0);
    turn = k < 0.45 ? -0.6 * smooth(seg(k, 0, 0.2)) : k < 0.9 ? lerp(-0.6, 0.6, smooth(seg(k, 0.45, 0.62))) : 0.6 * (1 - smooth(seg(k, 0.9, 1)));
  }
  const nod = 0.1 * bump(t, T.look0, T.hop0) - 0.35 * bump(t, T.bow0, T.bow1);
  const tilt = -0.06 * smooth(seg(t, T.wave0, T.wave0 + 0.2)) * (1 - smooth(seg(t, T.wave1 - 0.2, T.wave1)));

  // Eyes: wide as he falls, happy on landing and while waving, a wink at the end.
  let eye: EyeShape = "open";
  let mouth: FigMouth = "smile";
  if (t >= T.drop0 && t < T.land) { eye = "wide"; mouth = "o"; }
  else if (t >= T.land && t < T.land + 0.3) { eye = "smile"; mouth = "grin"; }
  else if (t >= T.hop0 && t < T.wave1) { eye = "smile"; mouth = "grin"; }
  else if (t >= T.bow0 && t < T.bow1) { eye = "smile"; mouth = "smile"; }
  else if (t >= T.wink && t < T.wink + 0.45) { eye = "wink"; mouth = "tongue"; }
  const blink = (at: number) => {
    const k = seg(t, at, at + 0.15);
    return k > 0 && k < 1 ? 1 - Math.sin(Math.PI * k) * 0.95 : 1;
  };
  const open = Math.min(blink(1.32), blink(4.25));

  const wave = smooth(seg(t, T.wave0, T.wave0 + 0.15)) * (1 - smooth(seg(t, T.wave1 - 0.15, T.wave1)));

  return {
    x: REST.x, y, size, sx, sy, tilt, eye, mouth, open, turn, nod,
    earL: -12 * bump(t, T.land, T.land + 0.4) + 10 * Math.sin(t * 13) * wave,
    earR: 12 * bump(t, T.land, T.land + 0.4) + 14 * Math.sin(t * 13 + 1) * wave,
    droop: t >= T.drop0 && t < T.land ? -16 : 8 * bump(t, T.bow0, T.bow1),
    wave, waveT: t - T.wave0,
    bubble: outBack(seg(t, T.wave0 + 0.1, T.wave0 + 0.4)) * (1 - seg(t, T.wave1 - 0.1, T.wave1 + 0.1)),
    card: seg(t, 0.12, 0.4),
    fx: 1,
    minis: 0,
  };
}

function compactPose(): Pose {
  return {
    x: COMPACT.x, y: COMPACT.y, size: COMPACT.size, sx: 1, sy: 1, tilt: 0,
    eye: "open", mouth: "smile", open: 1, turn: 0, nod: 0, earL: 0, earR: 0, droop: 0,
    wave: 0, waveT: 0, bubble: 0, card: 0, fx: 0, minis: 1,
  };
}

/** His pose `t` seconds in; `tc` is when the island began to fold (∞ if not yet). */
export function greetingPose(t: number, tc = Number.POSITIVE_INFINITY): Pose {
  if (t < tc) return openPose(Math.min(t, T.end + 10));
  const a = openPose(Math.min(tc, T.end + 10));
  const b = compactPose();
  // The island's own folding curve and time, so it never cuts him off.
  const k = seg(t, tc, tc + T.fold);
  const e = k >= 1 ? 1 : closeCurve(k);
  const f = 1 - seg(t, tc, tc + 0.15);
  return {
    ...a,
    x: lerp(a.x, b.x, e),
    y: lerp(a.y, b.y, e),
    size: lerp(a.size, b.size, e),
    sx: lerp(a.sx, 1, e),
    sy: lerp(a.sy, 1, e),
    tilt: a.tilt * (1 - e),
    turn: a.turn * (1 - e),
    nod: a.nod * (1 - e),
    eye: "open",
    mouth: "smile",
    open: 1,
    wave: a.wave * f,
    bubble: a.bubble * f,
    card: a.card * (1 - seg(t, tc, tc + 0.18)),
    fx: 1 - seg(t, tc, tc + 0.2),
    minis: outBack(seg(t, tc + 0.22, tc + 0.42)),
  };
}

/** Studio units → greeting pixels for pose `p`. */
const unit = (p: Pose) => p.size / BODY_H;

/** Where his waving paw is, in studio units. */
function wavePaw(look: TokkipLook, p: Pose) {
  const rest = restPaw(look, 1);
  const up = { x: 118, y: -50, a: -0.3 + Math.sin(p.waveT * 14) * 0.5 };
  return {
    x: lerp(rest.x, up.x, p.wave),
    y: lerp(rest.y, up.y, p.wave),
    a: lerp(rest.a, up.a, p.wave),
  };
}

/**
 * The box his body and paws take in the 640 × 150 space — what must stay inside
 * the island so no part of him is cut off. His ears are left out: he drops in
 * from the top of the screen, so they are meant to come out of it.
 */
export function tokkipBounds(p: Pose): { left: number; right: number; top: number; bottom: number } | null {
  const u = unit(p);
  if (p.size < 1) return null;
  let left = -100;
  let right = 100;
  const top = -90 - BODY_MID;
  let bottom = 120 - BODY_MID;
  const paw = wavePaw(DEFAULT_LOOK, p);
  right = Math.max(right, paw.x + 21);
  bottom = Math.max(bottom, paw.y - BODY_MID + 21);
  left = Math.min(left, -80 - 21);
  return {
    left: p.x + left * u * p.sx,
    right: p.x + right * u * p.sx,
    top: p.y + top * u * p.sy,
    bottom: p.y + bottom * u * p.sy,
  };
}

// ── Drawing ───────────────────────────────────────────────────────────────────

/** Twinkles of the landing, always the same ones. */
const TWINKLES = Array.from({ length: 14 }, (_, i) => {
  const a = (i / 14) * Math.PI * 2 + (i % 3) * 0.3;
  return { a, d: 60 + ((i * 37) % 50), s: 4 + (i % 4) * 1.5, c: ["#FFE08A", "#FF8FB1", "#B39DFF", "#7FD1F7"][i % 4]! };
});

function drawCard(x: CanvasRenderingContext2D, p: Pose) {
  if (p.card <= 0) return;
  x.save();
  x.globalAlpha = p.card;
  x.fillStyle = palette().surfaceContainer;
  x.beginPath();
  x.roundRect(CARD.x, CARD.y, CARD.w, CARD.h, CARD.r);
  x.fill();
  x.restore();
}

function drawGlow(x: CanvasRenderingContext2D, p: Pose, look: TokkipLook) {
  if (p.fx <= 0 || p.size < 2) return;
  const g = x.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.size * 1.6);
  const c = look.accent;
  const n = parseInt(c.slice(1), 16);
  const rgb = `${(n >> 16) & 255},${(n >> 8) & 255},${n & 255}`;
  g.addColorStop(0, `rgba(${rgb},${0.22 * p.fx * p.card})`);
  g.addColorStop(1, `rgba(${rgb},0)`);
  x.fillStyle = g;
  x.beginPath();
  x.arc(p.x, p.y, p.size * 1.6, 0, Math.PI * 2);
  x.fill();
}

function drawTwinkles(x: CanvasRenderingContext2D, t: number, p: Pose) {
  const k = seg(t, T.land, T.land + 0.9);
  if (k <= 0 || k >= 1 || p.fx <= 0) return;
  for (const tw of TWINKLES) {
    const d = tw.d * (0.4 + 0.6 * (1 - Math.pow(1 - k, 3)));
    x.save();
    x.translate(REST.x + Math.cos(tw.a) * d * 1.5, REST.y + 10 + Math.sin(tw.a) * d * 0.45);
    x.rotate(k * 3);
    x.globalAlpha = (1 - k) * p.fx;
    x.fillStyle = tw.c;
    twinkleShape(x, tw.s * (1 - k * 0.4));
    x.fill();
    x.restore();
  }
}

function drawBubble(x: CanvasRenderingContext2D, p: Pose) {
  if (p.bubble <= 0.01) return;
  const text = t("Hello!");
  x.save();
  x.font = `600 17px "Fredoka", system-ui, "Segoe UI", sans-serif`;
  const w = Math.max(64, x.measureText(text).width + 26);
  const h = 32;
  const bx = p.x + p.size * 1.25;
  const by = p.y - p.size * 0.62;
  x.translate(bx, by + h / 2);
  x.scale(p.bubble, p.bubble);
  x.translate(0, -h / 2);
  x.fillStyle = palette().inverseSurface;
  x.beginPath();
  x.roundRect(0, 0, w, h, h / 2);
  x.moveTo(10, h - 6);
  x.lineTo(-6, h + 8);
  x.lineTo(22, h - 2);
  x.fill();
  x.fillStyle = palette().inverseOnSurface;
  x.textAlign = "center";
  x.textBaseline = "middle";
  x.fillText(text, w / 2, h / 2 + 1);
  x.restore();
}

function drawTokkip(x: CanvasRenderingContext2D, p: Pose, look: TokkipLook, t: number) {
  if (p.size < 1) return;
  const u = unit(p);
  const f = figure(look, t);
  f.eye = p.eye;
  f.mouth = p.mouth;
  f.open = p.open;
  f.turn = p.turn;
  f.nod = p.nod;
  f.earL = p.earL;
  f.earR = p.earR;
  f.droop = p.droop;
  f.happy = p.eye === "smile";
  f.pawR = wavePaw(look, p);
  f.blush = p.eye === "smile" || p.eye === "wink" ? 0.6 : 0.2;
  // Tiny, in the compact island: no paws or brows to fuss over.
  f.paws = p.size > 30;
  x.save();
  x.translate(p.x, p.y + (120 - BODY_MID) * u * p.sy);
  x.rotate(p.tilt);
  x.scale(p.sx, p.sy);
  x.translate(0, -(120 - BODY_MID) * u);
  x.scale(u, u);
  x.translate(0, -BODY_MID);
  drawFigure(x, look, f);
  x.restore();
}

const MINI_COLORS = ["#E86A6A", "#3E86E0", "#EFAE5A", "#8C73F2"];

/** The compact island's little Tokkips, popping up as he arrives. */
function drawMinis(x: CanvasRenderingContext2D, k: number) {
  if (k <= 0.01) return;
  const sp = 6 * COMPACT.minisScale;
  const spots: [number, number][] = [[-sp, -sp], [sp, -sp], [-sp, sp], [sp, sp]];
  spots.forEach(([dx, dy], i) => {
    x.save();
    x.translate(COMPACT.minisX + dx, COMPACT.y + dy);
    const s = k * COMPACT.minisScale;
    x.scale(s, s);
    x.fillStyle = MINI_COLORS[i]!;
    x.beginPath();
    x.roundRect(-5, -4, 10, 8, 3);
    x.fill();
    x.beginPath();
    x.ellipse(-2.6, -5, 1.2, 2.4, -0.2, 0, Math.PI * 2);
    x.ellipse(2.6, -5, 1.2, 2.4, 0.2, 0, Math.PI * 2);
    x.fill();
    x.restore();
  });
}

// ── Controller ────────────────────────────────────────────────────────────────

/**
 * Plays the greeting on its own canvas. `onComplete` fires once, at the end
 * (or right away when it is cut short), so the island can fold.
 */
export class Greeting {
  private startMs = 0;
  /** When the island began to fold, seconds in (∞ while open). */
  private tc = Number.POSITIVE_INFINITY;
  private fired = false;
  private timers: number[] = [];

  onComplete: (() => void) | null = null;

  start() {
    this.startMs = performance.now();
    this.tc = Number.POSITIVE_INFINITY;
    this.fired = false;
    this.clearTimers();
    Sound.play(GREETING_SOUND);
    this.timers.push(window.setTimeout(() => this.fire(), (T.end + 0.05) * 1000));
  }

  /** The pointer came into the island: it stays open while it is there. */
  hover() {
    if (this.tc >= T.autoLeave) this.tc = Number.POSITIVE_INFINITY;
  }

  /** The pointer left (or the island folds): he heads for the compact island now. */
  interrupt() {
    const t = this.elapsed;
    if (!Number.isFinite(this.tc) || this.tc > t) this.tc = t;
    this.clearTimers();
    Sound.fadeOut(GREETING_SOUND, 0.25);
  }

  /** The greeting view is gone: let its tune fade. */
  leave() {
    this.clearTimers();
    Sound.fadeOut(GREETING_SOUND, 0.2);
  }

  get elapsed(): number {
    return (performance.now() - this.startMs) / 1000;
  }

  get done(): boolean {
    return this.fired;
  }

  private fire() {
    if (this.fired) return;
    this.fired = true;
    this.clearTimers();
    this.onComplete?.();
  }

  private clearTimers() {
    for (const id of this.timers) window.clearTimeout(id);
    this.timers = [];
  }

  draw(x: CanvasRenderingContext2D) {
    const t = this.elapsed;
    if (!this.fired && t >= T.end && this.tc >= T.autoLeave) this.fire();
    const p = greetingPose(t, this.tc);
    const look = loadLook();
    x.clearRect(0, 0, GREETING_W, GREETING_H);
    drawCard(x, p);
    x.save();
    if (p.card > 0) {
      x.beginPath();
      x.roundRect(CARD.x, CARD.y, CARD.w, CARD.h, CARD.r);
      x.rect(0, 0, GREETING_W, CARD.y);
      x.clip();
    }
    drawGlow(x, p, look);
    drawTwinkles(x, t, p);
    x.restore();
    drawMinis(x, p.minis);
    drawTokkip(x, p, look, performance.now() / 1000);
    drawBubble(x, p);
  }
}
