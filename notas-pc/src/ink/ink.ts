// Handwriting: the drawing format shared with Android (data/Ink.kt), and how
// it's drawn. A drawing is JSON:
//   {"v":1,"bg":"lines","h":1414,"s":[{"k":"pen","c":0,"w":3,"p":[x,y,pr, x,y,pr…]}]}
// in page units: the page is 1000 wide and `h` tall; points are stored ×2 as
// whole numbers (half-unit precision) with the pen's pressure 0…100.
// Kinds: "pen" (width follows pressure) and "hl" (marcatextos, see-through).
// Colour 0 is "tinta": the theme's ink (dark on light paper, light on dark).

export const INK_W = 1000;
export const A4_H = 1414;

export type Bg = "plain" | "lines" | "grid" | "dots";
export type StrokeKind = "pen" | "hl";

export interface Stroke {
  k: StrokeKind;
  /** ARGB; 0 = the theme's ink. */
  c: number;
  /** Base width in page units. */
  w: number;
  /** x2, y2, pressure (0…100), repeated. */
  p: number[];
}

export interface Drawing {
  v: 1;
  bg: Bg;
  h: number;
  s: Stroke[];
}

export function emptyDrawing(bg: Bg = "lines", h = A4_H): Drawing {
  return { v: 1, bg, h, s: [] };
}

const BGS = new Set<Bg>(["plain", "lines", "grid", "dots"]);

export function parseDrawing(raw: string, fallback: () => Drawing = () => emptyDrawing()): Drawing {
  try {
    const o = JSON.parse(raw);
    if (!o || typeof o !== "object" || o.v !== 1) return fallback();
    const s: Stroke[] = Array.isArray(o.s)
      ? o.s
          .filter((x: unknown) => x && typeof x === "object")
          .map((x: Record<string, unknown>) => ({
            k: x.k === "hl" ? "hl" : "pen",
            c: typeof x.c === "number" ? x.c : 0,
            w: typeof x.w === "number" && x.w > 0 ? x.w : 3,
            p: Array.isArray(x.p) ? (x.p as unknown[]).map((n) => (typeof n === "number" ? Math.round(n) : 0)) : [],
          }))
          .filter((x: Stroke) => x.p.length >= 3)
      : [];
    return { v: 1, bg: BGS.has(o.bg) ? o.bg : "plain", h: typeof o.h === "number" && o.h > 50 ? o.h : A4_H, s };
  } catch {
    return fallback();
  }
}

export function drawingJson(d: Drawing): string {
  return JSON.stringify({ v: 1, bg: d.bg, h: Math.round(d.h), s: d.s.map((x) => ({ k: x.k, c: x.c, w: x.w, p: x.p })) });
}

// ── Colours and sizes ─────────────────────────────────────────────────────────

/** The pen's colours: tinta (follows the theme), then blue, red, green, violet, orange. */
export const PEN_COLORS = [0, 0xff2563eb, 0xffdc2626, 0xff16a34a, 0xff7c3aed, 0xffea580c];
/** Marcatextos: yellow, green, pink, blue. */
export const HL_COLORS = [0xfffde047, 0xff86efac, 0xfff9a8d4, 0xff93c5fd];
export const PEN_SIZES = [2, 3.5, 6];
export const HL_SIZE = 18;

export function strokeColor(s: Stroke, ink: string): string {
  if (s.c === 0) return ink;
  const hex = "#" + (s.c % 0x1000000).toString(16).padStart(6, "0");
  return hex;
}

// ── Drawing ───────────────────────────────────────────────────────────────────

export interface Paper {
  paper: string;
  rule: string;
  ink: string;
}

/** The page's lines, grid or dots. `scale` = px per page unit. */
export function drawBackground(x: CanvasRenderingContext2D, bg: Bg, w: number, h: number, scale: number, p: Paper) {
  x.fillStyle = p.paper;
  x.fillRect(0, 0, w, h);
  if (bg === "plain") return;
  x.strokeStyle = p.rule;
  x.fillStyle = p.rule;
  x.lineWidth = Math.max(1, scale * 1.2);
  const step = 40 * scale;
  if (bg === "lines") {
    x.beginPath();
    for (let y = step * 2; y < h; y += step) {
      x.moveTo(0, Math.round(y) + 0.5);
      x.lineTo(w, Math.round(y) + 0.5);
    }
    x.stroke();
  } else if (bg === "grid") {
    x.beginPath();
    for (let y = step; y < h; y += step) {
      x.moveTo(0, Math.round(y) + 0.5);
      x.lineTo(w, Math.round(y) + 0.5);
    }
    for (let xx = step; xx < w; xx += step) {
      x.moveTo(Math.round(xx) + 0.5, 0);
      x.lineTo(Math.round(xx) + 0.5, h);
    }
    x.stroke();
  } else {
    const r = Math.max(1, scale * 1.6);
    for (let y = step; y < h; y += step)
      for (let xx = step; xx < w; xx += step) {
        x.beginPath();
        x.arc(xx, y, r, 0, Math.PI * 2);
        x.fill();
      }
  }
}

/** Width of a pen at a pressure (0…100). */
export function widthAt(base: number, pressure: number): number {
  return base * (0.45 + 1.1 * (pressure / 100));
}

/** One stroke, smoothed through the midpoints; a pen's width follows the pressure. */
export function drawStroke(x: CanvasRenderingContext2D, s: Stroke, scale: number, ink: string) {
  const p = s.p;
  const n = p.length / 3;
  if (n < 1) return;
  const X = (i: number) => (p[i * 3] / 2) * scale;
  const Y = (i: number) => (p[i * 3 + 1] / 2) * scale;
  x.save();
  x.lineCap = "round";
  x.lineJoin = "round";
  x.strokeStyle = strokeColor(s, ink);
  x.fillStyle = x.strokeStyle;
  if (s.k === "hl") {
    x.globalAlpha = 0.38;
    x.lineCap = "butt";
    x.lineWidth = s.w * scale;
    x.beginPath();
    x.moveTo(X(0), Y(0));
    for (let i = 1; i < n; i++) x.lineTo(X(i), Y(i));
    x.stroke();
    x.restore();
    return;
  }
  if (n === 1) {
    x.beginPath();
    x.arc(X(0), Y(0), (widthAt(s.w, p[2]) * scale) / 2, 0, Math.PI * 2);
    x.fill();
    x.restore();
    return;
  }
  // Short pieces from midpoint to midpoint, each with its own width.
  let mx = X(0);
  let my = Y(0);
  for (let i = 1; i < n; i++) {
    const nx = i < n - 1 ? (X(i) + X(i + 1)) / 2 : X(i);
    const ny = i < n - 1 ? (Y(i) + Y(i + 1)) / 2 : Y(i);
    x.lineWidth = Math.max(0.6, widthAt(s.w, p[i * 3 + 2]) * scale);
    x.beginPath();
    x.moveTo(mx, my);
    x.quadraticCurveTo(X(i), Y(i), nx, ny);
    x.stroke();
    mx = nx;
    my = ny;
  }
  x.restore();
}

/** Strokes the eraser at (x, y) touches (page units, radius r). */
export function strokesHit(d: Drawing, x: number, y: number, r: number): number[] {
  const out: number[] = [];
  const x2 = x * 2;
  const y2 = y * 2;
  for (let i = 0; i < d.s.length; i++) {
    const s = d.s[i];
    const reach = (r + s.w) * 2;
    const p = s.p;
    for (let j = 0; j < p.length; j += 3) {
      const dx = p[j] - x2;
      const dy = p[j + 1] - y2;
      if (dx * dx + dy * dy <= reach * reach) {
        out.push(i);
        break;
      }
      // Between two points too (fast strokes leave gaps).
      if (j >= 3) {
        const ax = p[j - 3];
        const ay = p[j - 2];
        const bx = p[j];
        const by = p[j + 1];
        const len = (bx - ax) ** 2 + (by - ay) ** 2;
        if (len > 0) {
          const t = Math.max(0, Math.min(1, ((x2 - ax) * (bx - ax) + (y2 - ay) * (by - ay)) / len));
          const cx = ax + t * (bx - ax) - x2;
          const cy = ay + t * (by - ay) - y2;
          if (cx * cx + cy * cy <= reach * reach) {
            out.push(i);
            break;
          }
        }
      }
    }
  }
  return out;
}

/** The lowest point drawn (page units), to grow the page as you write. */
export function lowest(d: Drawing): number {
  let m = 0;
  for (const s of d.s) for (let j = 1; j < s.p.length; j += 3) m = Math.max(m, s.p[j] / 2);
  return m;
}
