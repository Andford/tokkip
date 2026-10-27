// Material 3 Expressive shapes as CSS clip-paths, in the same order as the
// Android app's MShapes.ALL, so a page's `shape` number looks the same on the
// phone, the tablet and here. Each is a polygon of 96 points in percentages.

type Radius = (a: number) => number;

/** A circle with `n` soft scallops. */
const cookie = (n: number, depth: number): Radius => (a) => 1 - depth / 2 + (depth / 2) * Math.cos(n * a);
/** A star with `n` points; `inner` is how deep the valleys go; `soft` rounds them. */
const star = (n: number, inner: number, soft = 0.7): Radius => (a) => inner + (1 - inner) * Math.pow(Math.abs(Math.cos((n * a) / 2)), soft);
/** A regular polygon with `n` sides, its corners rounded by `round` (0…1). */
const poly = (n: number, round: number, turn = 0): Radius => (a) => {
  const seg = (2 * Math.PI) / n;
  const x = ((((a + turn) % seg) + seg) % seg) - seg / 2;
  const sharp = Math.cos(seg / 2) / Math.cos(x);
  return sharp * (1 - round) + round * Math.cos(seg / 2) * 1.08;
};

const ARCH: Radius = (a) => {
  // Round on top, square-ish at the base (screen y grows down; a = 0 is up).
  const y = -Math.cos(a);
  if (y <= 0) return 1;
  const s = Math.abs(Math.sin(a));
  const box = Math.min(1 / Math.max(s, 1e-6), 1 / Math.max(Math.abs(Math.cos(a)), 1e-6));
  return Math.min(box, 1.18) * 0.96;
};

export const SHAPES: Radius[] = [
  cookie(9, 0.14), // Cookie9
  star(4, 0.55, 0.6), // Clover4
  cookie(8, 0.1), // Sunny
  cookie(6, 0.2), // Cookie6
  star(6, 0.68, 0.9), // Flower
  star(10, 0.82, 0.5), // SoftBurst
  poly(5, 0.35), // Pentagon
  star(8, 0.72, 0.5), // Clover8
  poly(6, 0.3), // Gem
  cookie(7, 0.16), // Puffy
  ARCH, // Arch
  cookie(12, 0.12), // Cookie12
  poly(3, 0.38), // Triangle
  star(8, 0.78, 0.25), // VerySunny
  cookie(4, 0.26), // Cookie4
  poly(4, 0.4), // Diamond
];

export const SHAPE_NAMES = ["Galleta", "Trébol", "Sol", "Flor de 6", "Flor", "Estallido", "Pentágono", "Trébol de 8", "Gema", "Nube", "Arco", "Galleta fina", "Triángulo", "Sol fuerte", "Cojín", "Rombo"];

const cache = new Map<number, string>();

/** `clip-path` value for a shape number (wraps around like Android's floorMod). */
export function clipFor(shape: number): string {
  const i = ((Math.trunc(shape) % SHAPES.length) + SHAPES.length) % SHAPES.length;
  const hit = cache.get(i);
  if (hit) return hit;
  const r = SHAPES[i];
  const n = 96;
  const raw: [number, number][] = [];
  let maxX = 0;
  let maxY = 0;
  for (let k = 0; k < n; k++) {
    const a = (k / n) * Math.PI * 2;
    const rr = r(a);
    const x = Math.sin(a) * rr;
    const y = -Math.cos(a) * rr;
    raw.push([x, y]);
    maxX = Math.max(maxX, Math.abs(x));
    maxY = Math.max(maxY, Math.abs(y));
  }
  const m = Math.max(maxX, maxY);
  const value = `polygon(${raw.map(([x, y]) => `${(50 + (x / m) * 50).toFixed(2)}% ${(50 + (y / m) * 50).toFixed(2)}%`).join(", ")})`;
  cache.set(i, value);
  return value;
}

export const CIRCLE = "circle(50% at 50% 50%)";
