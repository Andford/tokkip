// The drop scene, drawn (the numbers come from sequence.ts): the card with its
// dotted drop zone, Tokkip with his basket, the file falling in, the path he
// walks while it is copied, and the card that asks what to do with it.
//
// While the scene runs this canvas is the island's whole body; the island's
// own Tokkip waits hidden.

import { State } from "../core/state";
import { loadLook, type TokkipLook } from "../tokkip/tokkip";
import { drawFigure, drawPaw, figure, twinkleShape, type EyeShape, type FigMouth } from "../tokkip/figure";
import { SCRIPT_FONTS } from "../core/fonts";
import { N_, isRtl, t } from "../i18n/i18n";
import { SCENE, lerp, seg, type SceneFrame } from "./sequence";

const FONT = `system-ui, "Segoe UI Variable Text", "Segoe UI", ${SCRIPT_FONTS}, sans-serif`;
const C = SCENE.CARD;
const TEXT_X = 220;
const TEXT_RIGHT = C.x + C.w - 18;
const CHIPS = [N_("PDF"), N_("Images"), N_("Code"), N_("Docs")];
const TEAL = "#2FBF8A";

/** The choose card's two buttons, drawn on the canvas and caught by invisible ones. */
const BUTTON_Y = 118;
const BUTTON_H = 28;
const ASK = { x: 130, w: 178 };
const CANCEL = { x: 316, w: 116 };

/** Studio units in a body height (tokkip.ts: −90 … 120). */
const BODY_H = 210;

function label(
  x: CanvasRenderingContext2D, s: string, px: number, py: number, font: string, color: string,
  align: CanvasTextAlign = "left",
) {
  x.font = font;
  x.fillStyle = color;
  x.textAlign = align;
  x.direction = isRtl() ? "rtl" : "ltr";
  x.textBaseline = "middle";
  x.fillText(s, px, py);
}

/** The font that fits `s` in `maxW`, shrinking from `size` to `size × 0.75`. */
export function fitFont(x: CanvasRenderingContext2D, s: string, maxW: number, weight: number, size: number, minSize = size * 0.75): string {
  let px = size;
  x.font = `${weight} ${px}px ${FONT}`;
  while (x.measureText(s).width > maxW && px > minSize) {
    px = Math.max(minSize, px - 0.5);
    x.font = `${weight} ${px}px ${FONT}`;
  }
  return x.font;
}

/** `s`, cut short with an ellipsis so it fits `maxW` in `font`. */
export function ellipsize(x: CanvasRenderingContext2D, s: string, maxW: number, font: string): string {
  x.font = font;
  if (x.measureText(s).width <= maxW) return s;
  let cut = s;
  while (cut.length > 1 && x.measureText(`${cut}…`).width > maxW) cut = cut.slice(0, -1);
  return `${cut}…`;
}

function fitted(x: CanvasRenderingContext2D, s: string, maxW: number, weight: number, size: number) {
  const font = fitFont(x, s, maxW, weight, size);
  return { font, s: ellipsize(x, s, maxW, font) };
}

export interface UploadCanvasActions {
  /** Ask about the file in the chat. */
  ask(): void;
  cancel(): void;
}

export class UploadCanvas {
  readonly el: HTMLElement;
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D | null;
  private overlay: HTMLElement;
  private sizedFor = 0;

  constructor(actions: UploadCanvasActions) {
    this.canvas = document.createElement("canvas");
    this.canvas.id = "upload-canvas";
    const hit = (bx: number, w: number, onclick: () => void) => {
      const b = document.createElement("button");
      b.className = "upload-hit";
      b.style.left = `${bx}px`;
      b.style.top = `${BUTTON_Y}px`;
      b.style.width = `${w}px`;
      b.style.height = `${BUTTON_H}px`;
      b.addEventListener("click", onclick);
      return b;
    };
    this.overlay = document.createElement("div");
    this.overlay.id = "upload-overlay";
    this.overlay.append(hit(ASK.x, ASK.w, actions.ask), hit(CANCEL.x, CANCEL.w, actions.cancel));
    this.el = document.createElement("div");
    this.el.id = "upload-layer";
    this.el.append(this.canvas, this.overlay);
    this.ctx = this.canvas.getContext("2d");
  }

  draw(f: SceneFrame, wallTime: number) {
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    if (this.sizedFor !== dpr) {
      this.sizedFor = dpr;
      this.canvas.width = Math.round(SCENE.W * dpr);
      this.canvas.height = Math.round(SCENE.H * dpr);
      this.canvas.style.width = `${SCENE.W}px`;
      this.canvas.style.height = `${SCENE.H}px`;
    }
    const x = this.ctx;
    if (!x) return;
    x.setTransform(dpr, 0, 0, dpr, 0, 0);
    x.clearRect(0, 0, SCENE.W, SCENE.H);
    const look = loadLook();

    x.fillStyle = "#000000";
    x.fillRect(0, 0, SCENE.W, SCENE.H);
    x.fillStyle = "#0D0E10";
    x.beginPath();
    x.roundRect(C.x, C.y, C.w, C.h, C.r);
    x.fill();

    if (f.zone > 0) this.drawZone(x, f, wallTime);
    if (f.bar > 0) this.drawPath(x, f);
    if (f.choose > 0) this.drawChoose(x, f);
    this.drawTokkip(x, f, look, wallTime);
    if (f.fileAlpha > 0) drawSheet(x, f.fileX, f.fileY, f.fileScale, f.fileAlpha, f.near > 0.5 ? Math.sin(wallTime * 14) * 0.08 : 0);
    if (f.cheer >= 0) drawConfetti(x, f.cheer);

    this.overlay.style.display = f.choose > 0.5 ? "block" : "none";
  }

  // ── The drop zone ─────────────────────────────────────────────────────────

  private drawZone(x: CanvasRenderingContext2D, f: SceneFrame, wallTime: number) {
    x.save();
    x.globalAlpha = f.zone;
    // A border of round dots that drift around the card.
    x.strokeStyle = f.near > 0.3 ? "rgba(47,191,138,.75)" : "rgba(255,255,255,.18)";
    x.lineWidth = 3;
    x.lineCap = "round";
    x.setLineDash([0.1, 9]);
    x.lineDashOffset = -wallTime * 12;
    x.beginPath();
    x.roundRect(C.x + 4, C.y + 4, C.w - 8, C.h - 8, C.r - 3);
    x.stroke();
    x.setLineDash([]);

    if (f.zoneText > 0) {
      x.globalAlpha = f.zone * f.zoneText;
      const title = fitted(x, t("Drop your files here"), TEXT_RIGHT - TEXT_X, 600, 14);
      label(x, title.s, TEXT_X, 82, title.font, "#E3E5E9");
      const labels = CHIPS.map((c) => t(c));
      let size = 11;
      const widths = () => {
        x.font = `500 ${size}px ${FONT}`;
        return labels.map((l) => Math.ceil(x.measureText(l).width) + 18);
      };
      let ws = widths();
      while (ws.reduce((a, b) => a + b, 0) + 6 * (ws.length - 1) > TEXT_RIGHT - TEXT_X && size > 8.5) {
        size -= 0.5;
        ws = widths();
      }
      let cx = TEXT_X;
      labels.forEach((l, i) => {
        x.fillStyle = "rgba(255,255,255,.08)";
        x.beginPath();
        x.roundRect(cx, 98, ws[i]!, 20, 10);
        x.fill();
        label(x, l, cx + 9, 108, `500 ${size}px ${FONT}`, "#B9BDC4");
        cx += ws[i]! + 6;
      });
    }
    x.restore();
  }

  // ── The path he walks while the file is copied ────────────────────────────

  private drawPath(x: CanvasRenderingContext2D, f: SceneFrame) {
    const y = SCENE.FLOOR_Y + SCENE.SIZE / 2 + 3;
    const x0 = SCENE.PATH_X0 - 26;
    const x1 = SCENE.PATH_X1 + 26;
    const fill = lerp(x0, x1, f.progress);
    x.save();
    x.globalAlpha = f.bar;
    const name = State.droppedFile?.name ?? t("file");
    const right = C.x + C.w - 22;
    const head = fitted(x, t("Uploading {name}", { name }), right - x0 - 60, 500, 12.5);
    label(x, head.s, x0, 60, head.font, "#A9ADB5");
    if (f.check <= 0) label(x, `${Math.round(f.progress * 100)} %`, right, 60, `600 12.5px ${FONT}`, "#A9ADB5", "right");

    // Stepping stones still to walk, then the ground he has covered.
    x.fillStyle = "rgba(255,255,255,.14)";
    for (let px = x0; px <= x1; px += 12) {
      x.beginPath();
      x.arc(px, y, 2, 0, Math.PI * 2);
      x.fill();
    }
    if (fill > x0 + 1) {
      const g = x.createLinearGradient(x0, 0, fill, 0);
      g.addColorStop(0, "rgba(47,191,138,.35)");
      g.addColorStop(1, TEAL);
      x.strokeStyle = g;
      x.lineWidth = 4;
      x.lineCap = "round";
      x.beginPath();
      x.moveTo(x0, y);
      x.lineTo(fill, y);
      x.stroke();
    }
    if (f.check > 0) {
      x.save();
      x.translate(right - 9, 60);
      x.scale(f.check, f.check);
      x.fillStyle = TEAL;
      x.beginPath();
      x.arc(0, 0, 9, 0, Math.PI * 2);
      x.fill();
      x.strokeStyle = "#07130E";
      x.lineWidth = 2.2;
      x.lineCap = "round";
      x.lineJoin = "round";
      x.beginPath();
      x.moveTo(-4, 0.3);
      x.lineTo(-1.2, 3.2);
      x.lineTo(4.2, -3);
      x.stroke();
      x.restore();
    }
    x.restore();
  }

  // ── What now? ─────────────────────────────────────────────────────────────

  private drawChoose(x: CanvasRenderingContext2D, f: SceneFrame) {
    x.save();
    x.globalAlpha = f.choose;
    x.translate(0, (1 - f.choose) * 5);
    const name = State.droppedFile?.name ?? t("file");
    const maxW = TEXT_RIGHT - ASK.x;
    const ready = fitted(x, t("{name} is ready.", { name }), maxW, 600, 14);
    label(x, ready.s, ASK.x, 78, ready.font, "#F5F6F8");
    const what = fitted(x, t("What do you want to do with it?"), maxW, 400, 12.5);
    label(x, what.s, ASK.x, 98, what.font, "#9398A1");

    x.fillStyle = "#F5F6F8";
    x.beginPath();
    x.roundRect(ASK.x, BUTTON_Y, ASK.w, BUTTON_H, BUTTON_H / 2);
    x.fill();
    const ask = fitted(x, t("Ask a question about it"), ASK.w - 16, 600, 12.5);
    label(x, ask.s, ASK.x + ASK.w / 2, BUTTON_Y + BUTTON_H / 2, ask.font, "#0B0C0E", "center");

    x.fillStyle = "rgba(255,255,255,.1)";
    x.beginPath();
    x.roundRect(CANCEL.x, BUTTON_Y, CANCEL.w, BUTTON_H, BUTTON_H / 2);
    x.fill();
    const cancel = fitted(x, t("Cancel"), CANCEL.w - 16, 500, 12.5);
    label(x, cancel.s, CANCEL.x + CANCEL.w / 2, BUTTON_Y + BUTTON_H / 2, cancel.font, "#F1F2F4", "center");
    x.restore();
  }

  // ── Tokkip and his basket ─────────────────────────────────────────────────

  private drawTokkip(x: CanvasRenderingContext2D, f: SceneFrame, look: TokkipLook, wallTime: number) {
    const u = f.size / BODY_H;
    const fig = figure(look, wallTime);
    const faces: Record<SceneFrame["face"], [EyeShape, FigMouth, number]> = {
      eager: [f.near > 0.5 ? "sparkle" : "open", f.near > 0.5 ? "gape" : "o", 0.3],
      hug: ["smile", "grin", 0.8],
      happy: ["smile", "smile", 0.5],
      proud: ["star", "grin", 0.6],
      calm: ["open", "smile", 0.3],
    };
    const [eye, mouth, blush] = faces[f.face];
    fig.eye = eye;
    fig.mouth = mouth;
    fig.gape = 0.4 + f.near * 0.4;
    fig.blush = blush;
    fig.turn = f.lookX * 0.6;
    fig.nod = f.lookY * 0.5;
    fig.happy = f.face !== "eager" && f.face !== "calm";
    fig.droop = f.face === "eager" ? -14 : 0;
    fig.earL = f.cheer >= 0 && f.cheer < 0.5 ? -20 : 0;
    fig.earR = f.cheer >= 0 && f.cheer < 0.5 ? 20 : 0;
    const holding = f.basket > 0.01;
    const up = holding ? 1 : f.cheer >= 0 && f.cheer < 0.55 ? Math.sin((Math.PI * f.cheer) / 0.55) : 0;
    const lift = (side: -1 | 1) => {
      const rest = fig[side < 0 ? "pawL" : "pawR"];
      const k = holding ? f.basket : up;
      const target = holding ? { x: side * 66, y: -118, a: side * -0.4 } : { x: side * 110, y: -60, a: side * 0.3 };
      return { x: lerp(rest.x, target.x, k), y: lerp(rest.y, target.y, k), a: lerp(rest.a, target.a, k) };
    };
    const pawL = lift(-1);
    const pawR = lift(1);
    fig.paws = false;

    const bottom = 105 * u;
    x.save();
    x.translate(f.x, f.y + bottom);
    x.rotate(f.lean);
    x.scale(f.sx, f.sy);
    x.translate(0, -bottom);
    x.scale(u, u);
    x.translate(0, -15);
    drawFigure(x, look, fig);
    if (holding) {
      x.save();
      x.globalAlpha *= f.basket;
      drawBasket(x, f.basketBounce, f.sinceDrop >= SCENE.FALL - 0.05, wallTime);
      x.restore();
    }
    drawPaw(x, look, pawL);
    drawPaw(x, look, pawR);
    x.restore();

    // Twinkles when the file lands.
    const k = seg(f.sinceDrop, SCENE.FALL - 0.02, SCENE.FALL + 0.5);
    if (k > 0 && k < 1) {
      for (let i = 0; i < 6; i++) {
        const a = -Math.PI / 2 + (i - 2.5) * 0.45;
        const d = 18 + 30 * k;
        x.save();
        x.translate(f.x + Math.cos(a) * d, f.y - f.size * 0.95 + Math.sin(a) * d * 0.7);
        x.globalAlpha = 1 - k;
        x.fillStyle = i % 2 ? "#FFE08A" : "#B9F5DC";
        twinkleShape(x, 5);
        x.fill();
        x.restore();
      }
    }
  }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

/** A woven basket, held over his head (studio units); `full` shows the file's corner. */
function drawBasket(x: CanvasRenderingContext2D, bounce: number, full: boolean, t: number) {
  x.save();
  x.translate(0, -150 + bounce * 10);
  x.scale(1 + bounce * 0.06, 1 - bounce * 0.06);
  const straw = "#D9A35B";
  const dark = "#B07A36";
  if (full) {
    x.save();
    x.translate(6, -30);
    x.rotate(0.15 + Math.sin(t * 3) * 0.03);
    x.fillStyle = "#F4F4F6";
    x.beginPath();
    x.roundRect(-22, -26, 44, 40, 5);
    x.fill();
    x.fillStyle = "#4C8DF6";
    x.fillRect(-14, -14, 28, 6);
    x.fillStyle = "#C9CBD2";
    x.fillRect(-14, -4, 22, 4);
    x.restore();
  }
  // Handle.
  x.strokeStyle = dark;
  x.lineWidth = 8;
  x.lineCap = "round";
  x.beginPath();
  x.moveTo(-58, -26);
  x.bezierCurveTo(-56, -96, 56, -96, 58, -26);
  x.stroke();
  // Body.
  const body = new Path2D();
  body.moveTo(-76, -30);
  body.lineTo(76, -30);
  body.lineTo(58, 30);
  body.quadraticCurveTo(0, 40, -58, 30);
  body.closePath();
  x.fillStyle = straw;
  x.fill(body);
  x.save();
  x.clip(body);
  x.strokeStyle = dark;
  x.lineWidth = 3;
  for (let i = -90; i <= 90; i += 14) {
    x.beginPath();
    x.moveTo(i, -32);
    x.lineTo(i * 0.78, 40);
    x.stroke();
  }
  for (let j = -18; j <= 30; j += 12) {
    x.beginPath();
    x.moveTo(-80, j);
    x.quadraticCurveTo(0, j + 6, 80, j);
    x.stroke();
  }
  x.restore();
  // Rim.
  x.fillStyle = dark;
  x.beginPath();
  x.roundRect(-82, -38, 164, 16, 8);
  x.fill();
  x.fillStyle = "rgba(255,255,255,.18)";
  x.beginPath();
  x.roundRect(-76, -35, 120, 5, 3);
  x.fill();
  x.restore();
}

/** The file being dragged: a sheet with a folded corner. */
function drawSheet(x: CanvasRenderingContext2D, cx: number, cy: number, scale: number, alpha: number, wobble: number) {
  const w = 32 * scale;
  const h = 40 * scale;
  const fold = 9 * scale;
  x.save();
  x.globalAlpha = alpha;
  x.translate(cx, cy);
  x.rotate(wobble);
  x.translate(-w / 2, -h / 2);
  x.shadowColor = "rgba(0,0,0,.45)";
  x.shadowBlur = 8;
  x.shadowOffsetY = 3;
  x.fillStyle = "#F4F4F6";
  x.beginPath();
  x.moveTo(3, 0);
  x.lineTo(w - fold, 0);
  x.lineTo(w, fold);
  x.lineTo(w, h - 3);
  x.quadraticCurveTo(w, h, w - 3, h);
  x.lineTo(3, h);
  x.quadraticCurveTo(0, h, 0, h - 3);
  x.lineTo(0, 3);
  x.quadraticCurveTo(0, 0, 3, 0);
  x.fill();
  x.shadowColor = "transparent";
  x.fillStyle = "#D5D6DB";
  x.beginPath();
  x.moveTo(w - fold, 0);
  x.lineTo(w - fold, fold);
  x.lineTo(w, fold);
  x.fill();
  x.fillStyle = "#4C8DF6";
  x.fillRect(w * 0.18, h * 0.32, w * 0.64, h * 0.12);
  x.fillStyle = "#C9CBD2";
  for (const r of [0.54, 0.68]) x.fillRect(w * 0.18, h * r, w * 0.5, h * 0.07);
  x.restore();
}

const CONFETTI = Array.from({ length: 26 }, (_, i) => ({
  x: SCENE.PATH_X1 - 60 + ((i * 53) % 130),
  vx: ((i * 29) % 21) - 10,
  vy: -60 - ((i * 17) % 50),
  c: ["#FF5C8A", "#FFD447", "#7C5CFF", TEAL, "#4C8DF6"][i % 5]!,
  r: (i * 0.7) % 3,
}));

function drawConfetti(x: CanvasRenderingContext2D, s: number) {
  if (s > 1.4) return;
  for (const p of CONFETTI) {
    const px = p.x + p.vx * s * 2;
    const py = SCENE.FLOOR_Y - 20 + p.vy * s + 140 * s * s;
    x.save();
    x.translate(px, py);
    x.rotate(p.r + s * 8);
    x.globalAlpha = 1 - seg(s, 0.8, 1.4);
    x.fillStyle = p.c;
    x.fillRect(-3.5, -1.8, 7, 3.6);
    x.restore();
  }
}
