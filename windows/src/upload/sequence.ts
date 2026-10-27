// The drop scene, as numbers: while you drag a file over the island Tokkip
// runs under it holding up a little basket; when you let go the file drops in,
// he hugs it, then carries it along the bottom of the card while it is copied
// (the progress), cheers at the end and goes back to his corner to ask you
// what to do with it.
//
// This file draws nothing: frame() hands the canvas (canvas.ts) everything one
// frame needs. Coordinates are island points (640 × 176).

/** The scene's layout and timings. */
export const SCENE = {
  W: 640,
  H: 176,
  CARD: { x: 10, y: 42, w: 620, h: 124, r: 20 },
  /** Where Tokkip stands (his body's centre) and how big he is. */
  HOME_X: 120,
  FLOOR_Y: 122,
  SIZE: 66,
  /** How far he may run under the file. */
  MIN_X: 70,
  MAX_X: 570,
  /** The path he walks while the file is copied. */
  PATH_X0: 90,
  PATH_X1: 520,
  /** The choose card: Tokkip on the left, smaller. */
  CHOOSE_X: 66,
  CHOOSE_Y: 112,
  CHOOSE_SIZE: 54,
  /** After the drop: the file falls (FALL), he hugs it (HUG), then the walk starts. */
  FALL: 0.4,
  HUG: 0.5,
  /** After the walk: a cheer, then back to his corner. */
  CHEER: 0.55,
  BACK: 0.45,
} as const;

/** Seconds from the drop to the start of the progress. */
export const PRE_PROGRESS = SCENE.FALL + SCENE.HUG;

const clamp = (v: number, a: number, b: number) => Math.max(a, Math.min(b, v));
export const lerp = (a: number, b: number, k: number) => a + (b - a) * k;
export const seg = (t: number, a: number, b: number) => clamp((t - a) / (b - a), 0, 1);
const smooth = (k: number) => k * k * (3 - 2 * k);
const bump = (t: number, a: number, b: number) => {
  const k = seg(t, a, b);
  return k > 0 && k < 1 ? Math.sin(Math.PI * k) : 0;
};
const outBack = (k: number) => 1 + 2.4 * Math.pow(k - 1, 3) + 1.4 * Math.pow(k - 1, 2);

/** How the bar fills over `u` (0…1): brisk, then steady, then a last push. */
export function progressCurve(u: number): number {
  const k = clamp(u, 0, 1);
  if (k < 0.3) return 0.5 * smooth(k / 0.3);
  if (k < 0.85) return 0.5 + 0.42 * ((k - 0.3) / 0.55);
  return 0.92 + 0.08 * smooth((k - 0.85) / 0.15);
}

export interface SceneFrame {
  /** Seconds since the drop, or −1 while still dragging. */
  sinceDrop: number;
  cursorX: number;
  cursorY: number;
  /** Tokkip: centre, size, squash, lean and gaze. */
  x: number;
  y: number;
  size: number;
  sx: number;
  sy: number;
  lean: number;
  lookX: number;
  lookY: number;
  face: "eager" | "happy" | "hug" | "proud" | "calm";
  /** The basket over his head, 0…1, and its bounce when something lands in it. */
  basket: number;
  basketBounce: number;
  /** The file: where it is, how big, whether it shows. */
  fileX: number;
  fileY: number;
  fileScale: number;
  fileAlpha: number;
  /** The file is right over the basket. */
  near: number;
  /** The drop zone (border, title, chips), 0…1; the title dims when he's under it. */
  zone: number;
  zoneText: number;
  /** The walk: progress 0…1, the bar and its label, the tick at the end. */
  progress: number;
  bar: number;
  check: number;
  /** Seconds since the cheer began (for confetti), or −1. */
  cheer: number;
  /** The choose card, 0…1. */
  choose: number;
}

class Follow {
  v: number;
  vel = 0;
  constructor(v: number) {
    this.v = v;
  }
  step(target: number, hz: number, damp: number, dt: number) {
    const w = 2 * Math.PI * hz;
    this.vel += (w * w * (target - this.v) - 2 * damp * w * this.vel) * dt;
    this.v += this.vel * dt;
  }
}

class Scene {
  isActive = false;
  uploadDuration = 2.4;
  cursorX = 600;
  cursorY = 280;

  private enteredAt = 0;
  private droppedAt: number | null = null;
  private lastStep = 0;
  private tx = new Follow(SCENE.HOME_X);
  /** Where the file was when it was let go. */
  private dropFrom = { x: 0, y: 0 };
  private dropX: number = SCENE.HOME_X;

  private now(): number {
    return performance.now() / 1000;
  }

  /** A file came into the island (island points). */
  enterZone(x: number, y: number) {
    this.cursorX = x;
    this.cursorY = y;
    this.tx = new Follow(SCENE.HOME_X);
    this.enteredAt = this.now();
    this.lastStep = this.enteredAt;
    this.droppedAt = null;
    this.isActive = true;
  }

  updateCursor(x: number, y: number) {
    this.cursorX = x;
    this.cursorY = y;
  }

  /** The island stays open while the drag goes on, so leaving changes nothing here. */
  exitZone() {}

  performDrop(uploadDuration: number) {
    this.uploadDuration = uploadDuration;
    this.step();
    this.droppedAt = this.now();
    this.dropFrom = { x: this.cursorX, y: this.cursorY };
    this.dropX = this.tx.v;
  }

  deactivate() {
    this.isActive = false;
    this.droppedAt = null;
  }

  get dropped(): boolean {
    return this.droppedAt != null;
  }

  /** Seconds since the drop, or null while still dragging. */
  sinceDrop(): number | null {
    return this.droppedAt == null ? null : this.now() - this.droppedAt;
  }

  /** Runs his chase of the cursor up to now (only while dragging). */
  private step() {
    const t = this.now();
    let dt = Math.min(0.5, t - this.lastStep);
    this.lastStep = t;
    const target = clamp(this.cursorX, SCENE.MIN_X, SCENE.MAX_X);
    while (dt > 1e-6) {
      const h = Math.min(dt, 1 / 120);
      this.tx.step(target, 1.6, 0.75, h);
      dt -= h;
    }
  }

  frame(): SceneFrame {
    if (!this.isActive) return sceneFrame(-1, SCENE.HOME_X, 600, 280, 0, 0, this.uploadDuration);
    if (this.droppedAt == null) {
      this.step();
      return sceneFrame(-1, this.tx.v, this.cursorX, this.cursorY, this.now() - this.enteredAt, this.tx.vel, this.uploadDuration);
    }
    return afterDrop(this.now() - this.droppedAt, this.dropX, this.dropFrom, this.uploadDuration);
  }
}

/** The scene while dragging: he runs under the file with the basket up. */
export function sceneFrame(
  sinceDrop: number, x: number, cursorX: number, cursorY: number, sinceEnter: number, speed: number, duration: number,
): SceneFrame {
  void duration;
  const size: number = SCENE.SIZE;
  const basketY = SCENE.FLOOR_Y - size * 0.82;
  const near = clamp(1 - Math.hypot(cursorX - x, cursorY - basketY) / 90, 0, 1);
  const enter = smooth(seg(sinceEnter, 0, 0.3));
  // Eager little hops when the file is right above him.
  const hop = near > 0.5 ? Math.abs(Math.sin(sinceEnter * 9)) * 6 * near : 0;
  return {
    sinceDrop,
    cursorX, cursorY,
    x, y: SCENE.FLOOR_Y - hop, size,
    sx: 1 + 0.04 * near, sy: 1 - 0.04 * near,
    lean: clamp(speed * 0.0012, -0.16, 0.16),
    lookX: clamp((cursorX - x) / 160, -1, 1),
    lookY: clamp((SCENE.FLOOR_Y - cursorY) / 120, -1, 1),
    face: "eager",
    basket: enter, basketBounce: 0,
    fileX: cursorX, fileY: cursorY + 12, fileScale: 1, fileAlpha: 0.95,
    near,
    zone: 1,
    zoneText: x > 230 && x < 600 ? 0.3 : 1,
    progress: 0, bar: 0, check: 0, cheer: -1, choose: 0,
  };
}

/** The scene `t` seconds after the drop. */
export function afterDrop(t: number, dropX: number, from: { x: number; y: number }, duration: number): SceneFrame {
  const size: number = SCENE.SIZE;
  const walk0 = PRE_PROGRESS;
  const walk1 = walk0 + duration;
  const cheer1 = walk1 + SCENE.CHEER;
  const back1 = cheer1 + SCENE.BACK;

  // The file falls into the basket in an arc.
  const k = seg(t, 0, SCENE.FALL);
  const basketTop = SCENE.FLOOR_Y - size * 0.95;
  const fileX = lerp(from.x, dropX, smooth(k));
  const fileY = lerp(from.y, basketTop, k * k) - 30 * Math.sin(Math.PI * k) * (1 - k);

  // Where he is: under the drop, then walking the path, then home.
  const p = progressCurve(seg(t, walk0, walk1));
  let x = dropX;
  if (t >= SCENE.FALL) x = lerp(dropX, SCENE.PATH_X0, smooth(seg(t, SCENE.FALL + 0.15, walk0)));
  if (t >= walk0) x = lerp(SCENE.PATH_X0, SCENE.PATH_X1, p);
  let y: number = SCENE.FLOOR_Y;
  let sz: number = size;
  if (t >= cheer1) {
    const b = smooth(seg(t, cheer1, back1));
    x = lerp(SCENE.PATH_X1, SCENE.CHOOSE_X, b);
    y = lerp(SCENE.FLOOR_Y, SCENE.CHOOSE_Y, b) - 16 * Math.sin(Math.PI * b);
    sz = lerp(size, SCENE.CHOOSE_SIZE, b);
  }
  // Little steps while walking, a big jump to cheer.
  if (t >= walk0 && t < walk1) y -= Math.abs(Math.sin(Math.PI * p * 14)) * 7;
  y -= 22 * bump(t, walk1, walk1 + SCENE.CHEER * 0.8);

  const landed = bump(t, SCENE.FALL - 0.03, SCENE.FALL + 0.16);
  const sx = 1 + 0.14 * landed + 0.1 * bump(t, walk1 + SCENE.CHEER * 0.7, cheer1);
  const sy = 1 - 0.14 * landed - 0.1 * bump(t, walk1 + SCENE.CHEER * 0.7, cheer1);
  const lean = t >= walk0 && t < walk1 ? 0.06 + Math.sin(Math.PI * p * 14) * 0.04 : 0;

  let face: SceneFrame["face"] = "eager";
  if (t >= SCENE.FALL) face = "hug";
  if (t >= walk0) face = "happy";
  if (t >= walk1) face = "proud";
  if (t >= back1) face = "calm";

  return {
    sinceDrop: t,
    cursorX: from.x, cursorY: from.y,
    x, y, size: sz, sx, sy, lean,
    lookX: t < SCENE.FALL ? clamp((fileX - x) / 160, -1, 1) : t < walk1 ? 0.4 : 0.2,
    lookY: t < SCENE.FALL ? 0.6 : t < walk0 ? 0.3 : 0,
    face,
    basket: 1 - smooth(seg(t, cheer1, cheer1 + 0.25)),
    basketBounce: bump(t, SCENE.FALL - 0.02, SCENE.FALL + 0.3),
    fileX, fileY,
    fileScale: lerp(1, 0.55, k),
    fileAlpha: t < SCENE.FALL ? 0.95 : 0,
    near: 1 - k,
    zone: 1 - seg(t, SCENE.FALL, SCENE.FALL + 0.3),
    zoneText: 0.3 * (1 - seg(t, 0, 0.2)),
    progress: p,
    bar: smooth(seg(t, walk0 - 0.3, walk0)) * (1 - seg(t, cheer1, cheer1 + 0.25)),
    check: t >= walk1 ? outBack(seg(t, walk1, walk1 + 0.3)) : 0,
    cheer: t >= walk1 ? t - walk1 : -1,
    choose: seg(t, cheer1 + 0.15, back1 + 0.1),
  };
}

export const UploadSeq = new Scene();
