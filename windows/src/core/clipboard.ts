// The smart clipboard's state on the island side: the shelf Rust keeps
// (src-tauri/src/clipboard.rs), read again whenever it says it changed, and the
// filter the card shows. Pure helpers are exported for the tests.

import { Bridge, onEvent } from "./bridge";

export type ClipKind = "text" | "image" | "files";
export type ClipFilter = "all" | ClipKind | "pinned";

export interface ClipFile {
  name: string;
  path: string;
  dir: boolean;
  ext: string;
  exists: boolean;
}

export interface Clip {
  id: number;
  kind: ClipKind;
  /** The start of a text item. */
  text: string;
  long: boolean;
  files: ClipFile[];
  /** A data: URL, for images. */
  thumb: string | null;
  width: number;
  height: number;
  /** When it was last copied, ms since the epoch. */
  created: number;
  pinned: boolean;
  source: "copy" | "drop" | string;
}

export interface ClipState {
  capture: boolean;
  items: Clip[];
}

export const CLIPBOARD_COLOR = "#5EC8C0";

export const Clipboard = {
  capture: true,
  items: [] as Clip[],
  filter: "all" as ClipFilter,
  loaded: false,
  listeners: new Set<() => void>(),

  async refresh() {
    const s = (await Bridge.clipboardState()) ?? demoShelf();
    if (s) {
      this.capture = s.capture;
      this.items = s.items;
    }
    this.loaded = true;
    for (const l of this.listeners) l();
  },

  /** Listens to Rust once; `onChange` runs after every refresh. */
  async start(onChange: () => void) {
    this.listeners.add(onChange);
    await onEvent<null>("clipboard-changed", () => void this.refresh());
    await this.refresh();
  },
};

/** The items a filter shows, pinned ones first, then newest first. */
export function visibleClips(items: readonly Clip[], filter: ClipFilter): Clip[] {
  const shown = items.filter((c) =>
    filter === "all" ? true : filter === "pinned" ? c.pinned : c.kind === filter,
  );
  return [...shown].sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.created - a.created);
}

/** How many of each kind, for the filter chips. */
export function clipCounts(items: readonly Clip[]): Record<ClipFilter, number> {
  const n: Record<ClipFilter, number> = { all: items.length, text: 0, image: 0, files: 0, pinned: 0 };
  for (const c of items) {
    n[c.kind] += 1;
    if (c.pinned) n.pinned += 1;
  }
  return n;
}

/** A text item that is just a web address. */
export function isLink(text: string): boolean {
  const t = text.trim();
  return /^https?:\/\/\S+$/.test(t);
}

/** A hex colour (#RRGGBB) a text item is, if it is one: shown as a swatch. */
export function colorOf(text: string): string | null {
  const t = text.trim();
  return /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(t) ? t : null;
}

/** "ahora", "hace 5 min", "hace 2 h", "ayer", or the date. */
export function ago(created: number, nowMs: number, lang = "es"): string {
  const s = Math.max(0, Math.round((nowMs - created) / 1000));
  const es = lang.startsWith("es");
  if (s < 45) return es ? "ahora" : "now";
  const m = Math.round(s / 60);
  if (m < 60) return es ? `hace ${m} min` : `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return es ? `hace ${h} h` : `${h} h ago`;
  if (h < 48) return es ? "ayer" : "yesterday";
  try {
    return new Date(created).toLocaleDateString(lang, { day: "numeric", month: "short" });
  } catch {
    return new Date(created).toDateString();
  }
}

/** A short label for a files item: the name, or "name +2". */
export function filesLabel(files: readonly ClipFile[]): string {
  if (files.length === 0) return "";
  return files.length === 1 ? files[0].name : `${files[0].name} +${files.length - 1}`;
}

/** `npm run dev` with ?demo in the address: a few tiles to look at the card
 *  without the app around it. Never in the app itself. */
function demoShelf(): ClipState | null {
  if (typeof location === "undefined" || !location.search.includes("demo")) return null;
  const now = Date.now();
  const file = (name: string, ext: string): ClipFile => ({ name, path: `C:\\Users\\andy\\${name}`, dir: false, ext, exists: true });
  const swatch = "data:image/svg+xml;utf8," + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="180" height="120"><defs><linearGradient id="g"><stop offset="0" stop-color="#7FE0D6"/><stop offset="1" stop-color="#B39DDB"/></linearGradient></defs><rect width="180" height="120" fill="url(#g)"/><circle cx="60" cy="70" r="26" fill="#fff8"/></svg>');
  return {
    capture: true,
    items: [
      { id: 1, kind: "text", text: "Lista del súper: leche, pan, café y fresas para el domingo.", long: false, files: [], thumb: null, width: 0, height: 0, created: now - 20_000, pinned: true, source: "copy" },
      { id: 2, kind: "image", text: "", long: false, files: [], thumb: swatch, width: 1920, height: 1080, created: now - 5 * 60_000, pinned: false, source: "copy" },
      { id: 3, kind: "files", text: "", long: false, files: [file("Propuesta.pdf", "pdf")], thumb: null, width: 0, height: 0, created: now - 40 * 60_000, pinned: false, source: "drop" },
      { id: 4, kind: "text", text: "https://example.com/agenda", long: false, files: [], thumb: null, width: 0, height: 0, created: now - 3 * 3600_000, pinned: false, source: "copy" },
      { id: 5, kind: "text", text: "#5EC8C0", long: false, files: [], thumb: null, width: 0, height: 0, created: now - 26 * 3600_000, pinned: false, source: "copy" },
      { id: 6, kind: "files", text: "", long: false, files: [file("logo.png", "png"), file("fondo.png", "png")], thumb: null, width: 0, height: 0, created: now - 50 * 3600_000, pinned: false, source: "copy" },
    ],
  };
}
