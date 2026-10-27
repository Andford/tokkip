// Tokkip in one pose: everything that makes up his figure (ears and tail, body,
// face, brows, eyes, mouth, paws and what he wears), drawn in studio units
// (tokkip.ts: the body is about 200 wide, its top at y = −90, its bottom at
// 120). Whoever moves him — the motion engine (motion.ts), the launch greeting
// (greeting.ts), the drop scene (upload/) — fills a Figure and calls
// drawFigure() after placing and scaling the canvas.

import {
  bodyPath, cheekOf, drawBack, drawBody, drawFaceUnder, drawMouth, drawNose, inkOf, mouthY, shade,
  type MouthKind, type TokkipLook, type TokkipPose,
} from "./tokkip";
import { drawOutfit } from "./ropa";
import type { Outfit } from "./wardrobe";

/** How his eyes look. */
export type EyeShape =
  | "open" | "wide" | "dot" | "smile" | "closed" | "line" | "squint" | "spiral"
  | "heart" | "star" | "sleepy" | "wink" | "sparkle" | "teary";

/** His mouth: the studio's shapes plus a few of the engine's own. */
export type FigMouth = MouthKind | "grin" | "gape" | "chew" | "pout";

export interface Paw {
  /** Where the paw is, studio units. */
  x: number;
  y: number;
  /** Turned, radians (a wave rocks it). */
  a: number;
}

export interface Figure {
  /** Seconds, for everything that idles on its own (tail, spirals, twinkles). */
  t: number;
  eye: EyeShape;
  /** Eyelids: 1 open, 0 shut (a blink). */
  open: number;
  /** Eye size: 1 usual, more when amazed. */
  eyeScale: number;
  /** Where his face points, −1…1 each: right and up are positive. */
  turn: number;
  nod: number;
  /** Brows: +1 cross, −1 worried, 0 none; `browUp` raises them (surprise), 0…1. */
  brow: number;
  browUp: number;
  mouth: FigMouth;
  /** How open a "gape" mouth is, 0…1. */
  gape: number;
  /** Rosy cheeks, 0…1 (they always show a little). */
  blush: number;
  /** Ear angles in degrees, and how much they hang (+) or perk up (−). */
  earL: number;
  earR: number;
  droop: number;
  /** Tail wagging fast. */
  happy: boolean;
  /** Vertical speed proxy for the puppy's swinging ears, studio units. */
  oy: number;
  pawL: Paw;
  pawR: Paw;
  /** Paws drawn at all (the pills' tiny Tokkips have none). */
  paws: boolean;
  outfit: Outfit;
  /** The outfit dropping on (0 → 1) or lifting off (1 → 0). */
  outfitIn: number;
  /** How far soft parts (a pompom, a scarf's end) lag behind, −1…1. */
  sway: number;
  /** Fades the details (ears, visor, paws) out, as when he turns into something else. */
  alpha: number;
  /** A pill's little Tokkip: no brows, no paws, no outfit. */
  mini: boolean;
}

/** The paws where they rest: low on his sides (the robot's float beside him). */
export function restPaw(look: TokkipLook, side: -1 | 1): Paw {
  return look.species === "robot" ? { x: side * 118, y: 14, a: 0 } : { x: side * 80, y: 96, a: side * 0.2 };
}

export function figure(look: TokkipLook, t = 0): Figure {
  return {
    t, eye: "open", open: 1, eyeScale: 1, turn: 0, nod: 0, brow: 0, browUp: 0,
    mouth: "smile", gape: 0, blush: 0, earL: 0, earR: 0, droop: 0, happy: false, oy: 0,
    pawL: restPaw(look, -1), pawR: restPaw(look, 1), paws: true,
    outfit: "none", outfitIn: 0, sway: 0, alpha: 1, mini: false,
  };
}

// ── Shapes ────────────────────────────────────────────────────────────────────

export function heartShape(x: CanvasRenderingContext2D, s: number) {
  x.beginPath();
  x.moveTo(0, s * 0.42);
  x.bezierCurveTo(-s * 0.2, s * 0.26, -s, -s * 0.02, -s * 0.62, -s * 0.5);
  x.bezierCurveTo(-s * 0.38, -s * 0.78, -s * 0.06, -s * 0.66, 0, -s * 0.36);
  x.bezierCurveTo(s * 0.06, -s * 0.66, s * 0.38, -s * 0.78, s * 0.62, -s * 0.5);
  x.bezierCurveTo(s, -s * 0.02, s * 0.2, s * 0.26, 0, s * 0.42);
  x.closePath();
}

export function starShape(x: CanvasRenderingContext2D, ro: number, ri: number, points = 5) {
  x.beginPath();
  for (let i = 0; i < points * 2; i++) {
    const r = i % 2 ? ri : ro;
    const a = -Math.PI / 2 + (i * Math.PI) / points;
    x.lineTo(Math.cos(a) * r, Math.sin(a) * r);
  }
  x.closePath();
}

/** A four-pointed twinkle. */
export function twinkleShape(x: CanvasRenderingContext2D, r: number) {
  x.beginPath();
  x.moveTo(0, -r);
  x.quadraticCurveTo(r * 0.16, -r * 0.16, r, 0);
  x.quadraticCurveTo(r * 0.16, r * 0.16, 0, r);
  x.quadraticCurveTo(-r * 0.16, r * 0.16, -r, 0);
  x.quadraticCurveTo(-r * 0.16, -r * 0.16, 0, -r);
  x.closePath();
}

// ── Eyes ──────────────────────────────────────────────────────────────────────

const HEART_RED = "#E5486F";
const STAR_GOLD = "#F5B83D";
const TEAR_BLUE = "rgba(120,190,255,.85)";

function shine(x: CanvasRenderingContext2D, big: number, small: number, dx = 0) {
  x.fillStyle = "rgba(255,255,255,.92)";
  x.beginPath();
  x.arc(-3.5 + dx, -5, big, 0, Math.PI * 2);
  x.fill();
  x.beginPath();
  x.arc(4 + dx, 4.5, small, 0, Math.PI * 2);
  x.fill();
}

function openEye(x: CanvasRenderingContext2D, open: number, ink: string) {
  if (open < 0.22) {
    x.beginPath();
    x.moveTo(-11, 1);
    x.quadraticCurveTo(0, 4, 11, 1);
    x.stroke();
    return;
  }
  x.fillStyle = ink;
  x.beginPath();
  x.ellipse(0, 0, 11, 13.5 * open, 0, 0, Math.PI * 2);
  x.fill();
  if (open > 0.6) shine(x, 3.6, 1.7);
}

/** A happy closed eye: a little crescent, thick in the middle. */
function crescent(x: CanvasRenderingContext2D, ink: string) {
  x.fillStyle = ink;
  x.beginPath();
  x.moveTo(-13, 4);
  x.quadraticCurveTo(0, -16, 13, 4);
  x.quadraticCurveTo(0, -6, -13, 4);
  x.closePath();
  x.fill();
}

/** One eye, centred on (0, 0). `side` is −1 for his right (screen left), +1 for the other. */
function drawEye(x: CanvasRenderingContext2D, f: Figure, side: -1 | 1, ink: string) {
  x.strokeStyle = ink;
  x.fillStyle = ink;
  x.lineCap = "round";
  x.lineJoin = "round";
  x.lineWidth = 5.5;
  const t = f.t;
  switch (f.eye) {
    case "open":
      openEye(x, f.open, ink);
      return;
    case "wide":
      if (f.open < 0.3) return openEye(x, f.open, ink);
      x.beginPath();
      x.arc(0, 0, 15, 0, Math.PI * 2);
      x.fill();
      shine(x, 5, 2.4, -1);
      return;
    case "dot":
      x.beginPath();
      x.arc(0, 1, 6.5, 0, Math.PI * 2);
      x.fill();
      x.fillStyle = "rgba(255,255,255,.9)";
      x.beginPath();
      x.arc(-2, -1, 1.8, 0, Math.PI * 2);
      x.fill();
      return;
    case "smile":
      crescent(x, ink);
      return;
    case "closed":
      x.beginPath();
      x.moveTo(-12, 0);
      x.quadraticCurveTo(0, 8, 12, 0);
      x.stroke();
      x.lineWidth = 3;
      for (const lx of [-7, 0, 7]) {
        x.beginPath();
        x.moveTo(lx, lx === 0 ? 4.5 : 3);
        x.lineTo(lx * 1.25, lx === 0 ? 10 : 8.5);
        x.stroke();
      }
      return;
    case "line":
      x.beginPath();
      x.moveTo(-12, 0);
      x.lineTo(12, 0);
      x.stroke();
      return;
    case "squint":
      // ">" and "<": eyes screwed shut.
      x.beginPath();
      x.moveTo(-side * 9, -9);
      x.lineTo(side * 8, 0);
      x.lineTo(-side * 9, 9);
      x.stroke();
      return;
    case "spiral": {
      x.lineWidth = 3.2;
      x.beginPath();
      const spin = t * 7 * side;
      for (let a = 0; a <= Math.PI * 4.2; a += 0.2) {
        const r = 1 + a * 1.05;
        x.lineTo(Math.cos(a + spin) * r, Math.sin(a + spin) * r);
      }
      x.stroke();
      return;
    }
    case "heart": {
      const k = 1.3 * (1 + 0.08 * Math.sin(t * 10));
      x.save();
      x.scale(k, k);
      x.fillStyle = HEART_RED;
      heartShape(x, 14);
      x.fill();
      x.fillStyle = "rgba(255,255,255,.75)";
      x.beginPath();
      x.ellipse(-5, -5, 2.6, 1.8, -0.5, 0, Math.PI * 2);
      x.fill();
      x.restore();
      return;
    }
    case "star":
      x.save();
      x.rotate(Math.sin(t * 3) * 0.25 * side);
      x.fillStyle = STAR_GOLD;
      starShape(x, 16, 7);
      x.fill();
      x.fillStyle = "rgba(255,255,255,.7)";
      x.beginPath();
      x.arc(-3, -4, 2.4, 0, Math.PI * 2);
      x.fill();
      x.restore();
      return;
    case "sleepy":
      x.save();
      x.beginPath();
      x.rect(-16, -1, 32, 20);
      x.clip();
      x.beginPath();
      x.ellipse(0, 0, 11, 12, 0, 0, Math.PI * 2);
      x.fill();
      x.restore();
      x.lineWidth = 4.5;
      x.beginPath();
      x.moveTo(-13, -1);
      x.lineTo(13, -1);
      x.stroke();
      return;
    case "wink":
      if (side > 0) crescent(x, ink);
      else openEye(x, f.open, ink);
      return;
    case "sparkle": {
      openEye(x, Math.max(0.7, f.open), ink);
      const k = 0.75 + 0.25 * Math.sin(t * 9 + side);
      x.save();
      x.translate(-3, -5);
      x.scale(k, k);
      x.fillStyle = "#fff";
      twinkleShape(x, 6.5);
      x.fill();
      x.restore();
      return;
    }
    case "teary": {
      openEye(x, f.open, ink);
      x.fillStyle = TEAR_BLUE;
      x.beginPath();
      x.ellipse(0, 11, 11, 4, 0, 0, Math.PI * 2);
      x.fill();
      // A tear runs down one side now and then.
      const k = ((t * 0.7 + (side > 0 ? 0.5 : 0)) % 1);
      if (k < 0.6) {
        const y = 14 + k * 40;
        x.globalAlpha *= 1 - k / 0.6;
        x.beginPath();
        x.moveTo(side * 8, y - 6);
        x.quadraticCurveTo(side * 12, y + 2, side * 8, y + 4);
        x.quadraticCurveTo(side * 4, y + 2, side * 8, y - 6);
        x.fill();
      }
      return;
    }
  }
}

function drawBrow(x: CanvasRenderingContext2D, f: Figure, side: -1 | 1, ink: string) {
  if (Math.abs(f.brow) < 0.05 && f.browUp < 0.05) return;
  x.save();
  x.translate(0, -27 - f.browUp * 9);
  x.rotate(-side * 0.42 * f.brow);
  x.strokeStyle = ink;
  x.globalAlpha *= Math.min(1, Math.abs(f.brow) * 2 + f.browUp * 2);
  x.lineWidth = 5;
  x.lineCap = "round";
  x.beginPath();
  x.moveTo(-11, 1);
  x.quadraticCurveTo(0, -3, 11, 1);
  x.stroke();
  x.restore();
}

// ── Mouth ─────────────────────────────────────────────────────────────────────

function drawFigMouth(x: CanvasRenderingContext2D, f: Figure, ink: string, y: number) {
  const pink = "#E98A97";
  x.save();
  x.fillStyle = ink;
  x.strokeStyle = ink;
  x.lineWidth = 5.5;
  x.lineCap = "round";
  x.lineJoin = "round";
  switch (f.mouth) {
    case "grin":
      x.beginPath();
      x.moveTo(-21, y - 8);
      x.quadraticCurveTo(0, y - 4, 21, y - 8);
      x.quadraticCurveTo(18, y + 20, 0, y + 20);
      x.quadraticCurveTo(-18, y + 20, -21, y - 8);
      x.closePath();
      x.fill();
      x.fillStyle = pink;
      x.beginPath();
      x.ellipse(0, y + 12, 10, 5.5, 0, 0, Math.PI * 2);
      x.fill();
      break;
    case "gape": {
      const g = Math.max(0, Math.min(1, f.gape));
      x.beginPath();
      x.ellipse(0, y + 2 + g * 6, 9 + g * 7, 4 + g * 15, 0, 0, Math.PI * 2);
      x.fill();
      if (g > 0.25) {
        x.fillStyle = pink;
        x.beginPath();
        x.ellipse(0, y + 6 + g * 15, 6 + g * 4, 3 + g * 3, 0, 0, Math.PI * 2);
        x.fill();
      }
      break;
    }
    case "chew":
      if (Math.sin(f.t * 22) > 0) {
        x.beginPath();
        x.ellipse(0, y + 1, 7, 6, 0, 0, Math.PI * 2);
        x.fill();
      } else {
        x.beginPath();
        x.moveTo(-10, y);
        x.quadraticCurveTo(0, y + 5, 10, y);
        x.stroke();
      }
      break;
    case "pout":
      x.beginPath();
      x.arc(5, y + 2, 5.5, 0, Math.PI * 2);
      x.fill();
      break;
    default:
      x.restore();
      drawMouth(x, f.mouth, ink, f.t, y);
      return;
  }
  x.restore();
}

// ── Paws ──────────────────────────────────────────────────────────────────────

export function drawPaw(x: CanvasRenderingContext2D, look: TokkipLook, p: Paw) {
  x.save();
  x.translate(p.x, p.y);
  x.rotate(p.a);
  if (look.species === "robot") {
    x.fillStyle = "rgba(0,0,0,.08)";
    x.beginPath(); x.arc(2, 3, 20, 0, Math.PI * 2); x.fill();
    x.fillStyle = shade(look.body, -0.18);
    x.beginPath(); x.arc(0, 0, 20, 0, Math.PI * 2); x.fill();
    x.fillStyle = shade(look.body, -0.06);
    x.beginPath(); x.arc(0, 0, 12, 0, Math.PI * 2); x.fill();
  } else {
    // A soft round paw in his own colour, with a darker rim and three toe lines.
    x.fillStyle = look.body;
    x.strokeStyle = shade(look.body, -0.2);
    x.lineWidth = 3.5;
    x.beginPath();
    x.ellipse(0, 0, 20, 18, 0, 0, Math.PI * 2);
    x.fill();
    x.stroke();
    x.lineWidth = 2.6;
    x.lineCap = "round";
    for (const tx of [-6, 0, 6]) {
      x.beginPath();
      x.moveTo(tx, -16);
      x.lineTo(tx * 0.9, -9);
      x.stroke();
    }
  }
  x.restore();
}

// ── The whole figure ──────────────────────────────────────────────────────────

/** Draws Tokkip in pose `f`, in studio units around (0, 0). */
export function drawFigure(x: CanvasRenderingContext2D, look: TokkipLook, f: Figure) {
  const pose: TokkipPose = {
    t: f.t, earL: f.earL, earR: f.earR, droop: f.droop, happy: f.happy, oy: f.oy,
    wave: 0, waveT: 0, alpha: f.alpha,
  };
  // The face follows where he looks; glasses go with it.
  const fx = Math.sin(f.turn) * 62;
  const fy = -Math.sin(f.nod) * 40;
  const dressed = !f.mini && f.outfit !== "none" && f.outfitIn > 0.01;
  const wear = { look, t: f.t, k: f.outfitIn, sway: f.sway, fx, fy };
  if (dressed) drawOutfit(x, f.outfit, "back", wear);

  drawBack(x, look, pose);
  drawBody(x, look, pose);

  x.save();
  x.clip(bodyPath(look.species));
  x.translate(fx, 20 + fy);
  drawFaceUnder(x, look);

  const robot = look.species === "robot";
  x.fillStyle = cheekOf(look, Math.min(0.8, 0.3 + f.blush * 0.5));
  if (robot) {
    x.shadowColor = "#F37A9A";
    x.shadowBlur = 10;
  }
  for (const sd of [-1, 1]) {
    x.beginPath();
    if (robot) x.roundRect(sd * 60 - 10, 22, 20, 8, 4);
    else x.ellipse(sd * 70, 18, 17 + f.blush * 3, 9 + f.blush * 2, 0, 0, Math.PI * 2);
    x.fill();
  }
  x.shadowBlur = 0;

  const ink = inkOf(look);
  if (robot) {
    x.shadowColor = ink;
    x.shadowBlur = 14;
  }
  for (const sd of [-1, 1] as const) {
    x.save();
    x.translate(sd * 46, -6);
    if (!f.mini) drawBrow(x, f, sd, ink);
    x.scale(f.eyeScale, f.eyeScale);
    drawEye(x, f, sd, ink);
    x.restore();
  }
  drawNose(x, look);
  drawFigMouth(x, f, look.species === "mapache" ? "#3A2E2C" : ink, mouthY(look.species));
  x.shadowBlur = 0;
  x.restore();

  if (f.paws && !f.mini && f.alpha > 0.01) {
    x.save();
    x.globalAlpha *= f.alpha;
    drawPaw(x, look, f.pawL);
    drawPaw(x, look, f.pawR);
    x.restore();
  }
  if (dressed) drawOutfit(x, f.outfit, "front", wear);
}
