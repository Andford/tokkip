// Tokkip Notas' model — the same as on Android (android/notas/.../data/Model.kt),
// so a copy made on the phone opens here and the other way around.
//
// Everything is a page. A page can hold other pages (as many levels as you
// like), and is one of two kinds:
//   DOC        — text in lines (blocks): headings, bullets, steps to tick…
//   COLLECTION — a set of cards (its child pages) with columns: the same cards
//                seen as a board (grouped by their status) or as a table.
// A card is an ordinary page with values for its collection's columns.
//
// Only the index (titles, columns, values, a preview) lives in memory; a
// page's body is read from disk when you open it.

/** DOC: lines of text. COLLECTION: cards as a board or table. INK: a page written by hand. */
export type PageKind = "DOC" | "COLLECTION" | "INK";

export function kindOf(raw: unknown): PageKind {
  return raw === "COLLECTION" || raw === "INK" ? raw : "DOC";
}
export type PropType = "TEXT" | "NUMBER" | "SELECT" | "MULTI" | "DATE" | "CHECK";

export const PROP_LABEL: Record<PropType, string> = {
  TEXT: "Texto",
  NUMBER: "Número",
  SELECT: "Estado",
  MULTI: "Etiquetas",
  DATE: "Fecha",
  CHECK: "Casilla",
};

export interface Opt {
  id: string;
  name: string;
  /** ARGB, as on Android (0xFFA78BFA). */
  color: number;
}

export interface Prop {
  id: string;
  name: string;
  type: PropType;
  options: Opt[];
}

/** A collection's columns and how it's shown. `groupBy` is the SELECT column the board groups by. */
export interface Schema {
  props: Prop[];
  view: "board" | "table" | string;
  groupBy: string;
  sortBy: string;
  sortDesc: boolean;
}

/** A page in the index: everything except its body. */
export interface Page {
  id: string;
  parent: string | null;
  title: string;
  icon: string;
  color: number;
  shape: number;
  kind: PageKind;
  ord: number;
  created: number;
  updated: number;
  opened: number;
  favorite: boolean;
  /** When it went to the Papelera (0 = it didn't). */
  trashed: number;
  props: Record<string, string>;
  schema: Schema | null;
  preview: string;
  steps: number;
  done: number;
}

export const INBOX_ID = "inbox";
export const DAY_MS = 86_400_000;

export const PALETTE = [0xffa78bfa, 0xff60a5fa, 0xff34d399, 0xfffbbf24, 0xfff97316, 0xfff472b6, 0xff22d3ee, 0xfff87171, 0xff94a3b8];
export const GREY = 0xff94a3b8;

/** ARGB → #rrggbb. */
export function css(argb: number): string {
  return "#" + (argb % 0x1000000).toString(16).padStart(6, "0");
}

export function page(p: Partial<Page> & { id: string }): Page {
  return {
    parent: null,
    title: "",
    icon: "doc",
    color: 0xffa78bfa,
    shape: 0,
    kind: "DOC",
    ord: 0,
    created: 0,
    updated: 0,
    opened: 0,
    favorite: false,
    trashed: 0,
    props: {},
    schema: null,
    preview: "",
    steps: 0,
    done: 0,
    ...p,
  };
}

export function shownTitle(p: Page): string {
  if (p.title.trim()) return p.title;
  return p.preview.slice(0, 40).trim() || "Sin título";
}

export function schemaProp(s: Schema, id: string): Prop | undefined {
  return s.props.find((p) => p.id === id);
}

/** The SELECT column the board groups by (or the first one). */
export function groupOf(s: Schema): Prop | undefined {
  const g = schemaProp(s, s.groupBy);
  if (g && g.type === "SELECT") return g;
  return s.props.find((p) => p.type === "SELECT");
}

/** Is this option the "finished" one? The last option of the status column, by convention (Hecho). */
export function isDone(s: Schema, optId: string | undefined): boolean {
  const g = groupOf(s);
  if (!g || !optId) return false;
  return g.options[g.options.length - 1]?.id === optId;
}

/** The columns a new collection starts with: Estado (Por hacer · Haciendo · Hecho), Fecha and Etiquetas. */
export function defaultSchema(view = "board"): Schema {
  return {
    props: [
      {
        id: "status",
        name: "Estado",
        type: "SELECT",
        options: [
          { id: "todo", name: "Por hacer", color: GREY },
          { id: "doing", name: "Haciendo", color: 0xfffbbf24 },
          { id: "done", name: "Hecho", color: 0xff34d399 },
        ],
      },
      { id: "date", name: "Fecha", type: "DATE", options: [] },
      { id: "tags", name: "Etiquetas", type: "MULTI", options: [] },
    ],
    view,
    groupBy: "status",
    sortBy: "",
    sortDesc: false,
  };
}

// ── The tree ──────────────────────────────────────────────────────────────────

const byOrder = (a: Page, b: Page) => a.ord - b.ord || a.created - b.created;

export class Index {
  readonly pages: Page[];
  private byId = new Map<string, Page>();
  private kids = new Map<string | null, Page[]>();

  constructor(pages: Page[] = []) {
    this.pages = pages;
    for (const p of pages) {
      this.byId.set(p.id, p);
      if (p.trashed === 0) {
        const list = this.kids.get(p.parent);
        if (list) list.push(p);
        else this.kids.set(p.parent, [p]);
      }
    }
    for (const list of this.kids.values()) list.sort(byOrder);
  }

  page(id: string | null | undefined): Page | undefined {
    return id == null ? undefined : this.byId.get(id);
  }

  /** A page's living children, in order. */
  children(id: string | null): Page[] {
    return this.kids.get(id) ?? [];
  }

  get roots(): Page[] {
    return this.children(null).filter((p) => p.id !== INBOX_ID);
  }
  get inbox(): Page[] {
    return this.children(INBOX_ID);
  }
  get live(): Page[] {
    return this.pages.filter((p) => p.trashed === 0 && p.id !== INBOX_ID);
  }
  /** What's in the Papelera (only the tops: what went with them stays inside). */
  get trash(): Page[] {
    return this.pages
      .filter((p) => p.trashed > 0 && (this.page(p.parent)?.trashed ?? 0) !== p.trashed)
      .sort((a, b) => b.trashed - a.trashed);
  }
  get recent(): Page[] {
    return this.live.filter((p) => p.opened > 0).sort((a, b) => b.opened - a.opened);
  }
  get favorites(): Page[] {
    return this.live.filter((p) => p.favorite).sort((a, b) => a.title.toLowerCase().localeCompare(b.title.toLowerCase()));
  }

  /** From the top down to the page's parent. */
  path(id: string): Page[] {
    const out: Page[] = [];
    let p = this.page(this.page(id)?.parent);
    let guard = 0;
    while (p && guard++ < 64) {
      out.unshift(p);
      p = this.page(p.parent);
    }
    return out;
  }

  /** The page and everything under it (trashed or not). */
  subtree(id: string): Page[] {
    const all = new Map<string | null, Page[]>();
    for (const p of this.pages) {
      const l = all.get(p.parent);
      if (l) l.push(p);
      else all.set(p.parent, [p]);
    }
    const out: Page[] = [];
    const walk = (i: string, depth: number) => {
      const p = this.page(i);
      if (p) out.push(p);
      if (depth < 64) for (const c of all.get(i) ?? []) walk(c.id, depth + 1);
    };
    walk(id, 0);
    return out;
  }

  /** Is `a` the same as, or inside, `b`? (A page can't move into itself.) */
  isInside(a: string, b: string): boolean {
    return a === b || this.path(a).some((p) => p.id === b);
  }

  /** The collection a card belongs to, if it is one. */
  collectionOf(p: Page): Page | undefined {
    const c = this.page(p.parent);
    return c && c.kind === "COLLECTION" ? c : undefined;
  }
}

/** An order value that places a page after `before` and before `after` (either may be missing). */
export function between(before: number | undefined, after: number | undefined): number {
  if (before === undefined && after === undefined) return 0;
  if (before === undefined) return after! - 1;
  if (after === undefined) return before + 1;
  return (before + after) / 2;
}

// ── Board ─────────────────────────────────────────────────────────────────────

/** One board column: an option of the status column (or null for the cards without one). */
export interface Lane {
  opt: Opt | null;
  cards: Page[];
}

export function board(ix: Index, c: Page): Lane[] {
  const s = c.schema;
  if (!s) return [];
  const cards = ix.children(c.id);
  const g = groupOf(s);
  if (!g) return [{ opt: null, cards }];
  const known = new Set(g.options.map((o) => o.id));
  const lanes = g.options.map((o) => ({ opt: o, cards: cards.filter((k) => k.props[g.id] === o.id) }));
  const loose = cards.filter((k) => !known.has(k.props[g.id]));
  return loose.length ? [{ opt: null, cards: loose }, ...lanes] : lanes;
}

/** What a card becomes when dropped in `lane` before the card at `at` (or at the end). */
export function moveCard(card: Page, group: Prop, lane: Lane, at: number): Page {
  const others = lane.cards.filter((k) => k.id !== card.id);
  const i = Math.max(0, Math.min(at, others.length));
  const ord = between(others[i - 1]?.ord, others[i]?.ord);
  const props = { ...card.props };
  if (lane.opt) props[group.id] = lane.opt.id;
  else delete props[group.id];
  return { ...card, ord, props };
}

// ── Table ─────────────────────────────────────────────────────────────────────

export function rows(ix: Index, c: Page): Page[] {
  const list = ix.children(c.id);
  const s = c.schema;
  if (!s || !s.sortBy) return list;
  const prop = schemaProp(s, s.sortBy);
  let key: (p: Page) => number | string | null;
  if (s.sortBy === "title") key = (p) => fold(shownTitle(p));
  else if (!prop) return list;
  else if (prop.type === "NUMBER") key = (p) => (p.props[prop.id] ? Number(p.props[prop.id]) : null);
  else if (prop.type === "SELECT")
    key = (p) => {
      const i = prop.options.findIndex((o) => o.id === p.props[prop.id]);
      return i < 0 ? Number.MAX_SAFE_INTEGER : i;
    };
  else if (prop.type === "CHECK") key = (p) => (p.props[prop.id] === "1" ? 0 : 1);
  else key = (p) => (p.props[prop.id]?.trim() ? fold(p.props[prop.id]) : null);
  const sorted = [...list].sort((a, b) => {
    const x = key(a);
    const y = key(b);
    if (x === null && y === null) return 0;
    if (x === null) return 1; // empty ones last, whichever way
    if (y === null) return -1;
    const c = x < y ? -1 : x > y ? 1 : 0;
    return s.sortDesc ? -c : c;
  });
  return sorted;
}

export function multiOf(raw: string | undefined): string[] {
  return (raw ?? "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}

/** How a value reads in a cell. */
export function display(prop: Prop, raw: string | undefined, now = Date.now()): string {
  if (!raw || !raw.trim()) return "";
  switch (prop.type) {
    case "SELECT":
      return prop.options.find((o) => o.id === raw)?.name ?? "";
    case "MULTI":
      return multiOf(raw)
        .map((id) => prop.options.find((o) => o.id === id)?.name)
        .filter(Boolean)
        .join(", ");
    case "DATE":
      return shortDate(raw, now);
    case "CHECK":
      return raw === "1" ? "✓" : "";
    default:
      return raw;
  }
}

// ── Dates (day keys, yyyy-MM-dd, local time) ──────────────────────────────────

const pad = (n: number) => String(n).padStart(2, "0");

export function dayKey(ms: number): string {
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function dayStart(day: string): number {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day);
  if (!m) return 0;
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])).getTime();
}

const DAYS = ["dom", "lun", "mar", "mié", "jue", "vie", "sáb"];
const MONTHS = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];

/** "hoy", "mañana", "ayer", "vie 31 oct". */
export function shortDate(day: string, now = Date.now()): string {
  const t = dayStart(day);
  if (!t) return day;
  const today = dayStart(dayKey(now));
  const diff = Math.round((t - today) / DAY_MS);
  if (diff === 0) return "hoy";
  if (diff === 1) return "mañana";
  if (diff === -1) return "ayer";
  const d = new Date(t);
  return `${DAYS[d.getDay()]} ${d.getDate()} ${MONTHS[d.getMonth()]}`;
}

export interface Due {
  card: Page;
  collection: Page;
  day: string;
}

/** Cards with a date up to `days` ahead (and the late ones) that aren't done: "Para hoy". */
export function dueSoon(ix: Index, now: number, days = 1): Due[] {
  const limit = dayKey(now + days * DAY_MS);
  const out: Due[] = [];
  for (const c of ix.live) {
    if (c.kind !== "COLLECTION" || !c.schema) continue;
    const s = c.schema;
    const dateProp = s.props.find((p) => p.type === "DATE");
    if (!dateProp) continue;
    const g = groupOf(s);
    for (const r of ix.children(c.id)) {
      const d = r.props[dateProp.id]?.trim();
      if (!d || d > limit) continue;
      if (g && isDone(s, r.props[g.id])) continue;
      out.push({ card: r, collection: c, day: d });
    }
  }
  return out.sort((a, b) => (a.day < b.day ? -1 : a.day > b.day ? 1 : 0));
}

// ── Search ────────────────────────────────────────────────────────────────────

export function fold(s: string): string {
  return s.toLowerCase().normalize("NFD").replace(/\p{Mn}+/gu, "");
}

/** Every word of the query must appear (accents and case don't matter); titles weigh more. */
export function searchScore(query: string, title: string, body: string): number {
  const words = fold(query).split(/\s+/).filter(Boolean);
  if (!words.length) return 0;
  const t = fold(title);
  const b = fold(body);
  let score = 0;
  for (const w of words) {
    if (t.startsWith(w)) score += 6;
    else if (t.includes(w)) score += 4;
    else if (b.includes(w)) score += 1;
    else return 0;
  }
  return score;
}

export function snippet(query: string, body: string, width = 80): string {
  const flat = body.replace(/\n/g, " ");
  const w = fold(query).split(/\s+/).find(Boolean);
  if (!w) return flat.slice(0, width);
  const i = fold(flat).indexOf(w);
  if (i < 0) return flat.slice(0, width);
  const from = Math.max(0, i - Math.floor(width / 3));
  const to = Math.min(flat.length, from + width);
  return (from > 0 ? "…" : "") + flat.slice(from, to).trim() + (to < flat.length ? "…" : "");
}

// ── JSON (as Android stores and copies them) ──────────────────────────────────

export function schemaJson(s: Schema | null): string {
  if (!s) return "";
  return JSON.stringify({
    view: s.view,
    groupBy: s.groupBy,
    sortBy: s.sortBy,
    sortDesc: s.sortDesc,
    props: s.props.map((p) => ({
      id: p.id,
      name: p.name,
      type: p.type,
      options: p.options.map((o) => ({ id: o.id, name: o.name, color: o.color })),
    })),
  });
}

const TYPES = new Set<PropType>(["TEXT", "NUMBER", "SELECT", "MULTI", "DATE", "CHECK"]);
const str = (v: unknown, d = "") => (typeof v === "string" ? v : v == null ? d : String(v));
const num = (v: unknown, d = 0) => (typeof v === "number" && Number.isFinite(v) ? v : typeof v === "string" && v.trim() && Number.isFinite(Number(v)) ? Number(v) : d);

export function schemaOf(raw: string | null | undefined): Schema | null {
  if (!raw || !raw.trim()) return null;
  try {
    const o = JSON.parse(raw);
    if (!o || typeof o !== "object") return null;
    const props: Prop[] = Array.isArray(o.props)
      ? o.props
          .filter((p: unknown) => p && typeof p === "object")
          .map((p: Record<string, unknown>) => ({
            id: str(p.id),
            name: str(p.name),
            type: TYPES.has(p.type as PropType) ? (p.type as PropType) : "TEXT",
            options: Array.isArray(p.options)
              ? p.options
                  .filter((x: unknown) => x && typeof x === "object")
                  .map((x: Record<string, unknown>) => ({ id: str(x.id), name: str(x.name), color: num(x.color, GREY) }))
              : [],
          }))
      : [];
    return { props, view: str(o.view, "board") || "board", groupBy: str(o.groupBy), sortBy: str(o.sortBy), sortDesc: o.sortDesc === true };
  } catch {
    return null;
  }
}

export function propsJson(m: Record<string, string>): string {
  return Object.keys(m).length ? JSON.stringify(m) : "";
}

export function propsOf(raw: string | null | undefined): Record<string, string> {
  if (!raw || !raw.trim()) return {};
  try {
    const o = JSON.parse(raw);
    if (!o || typeof o !== "object" || Array.isArray(o)) return {};
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(o)) out[k] = str(v);
    return out;
  } catch {
    return {};
  }
}

/** A short random id. */
export function newId(): string {
  const a = new Uint8Array(8);
  crypto.getRandomValues(a);
  return Array.from(a, (b) => b.toString(16).padStart(2, "0")).join("");
}

// ── Copies (the same JSON as Android's Ajustes → Guardar una copia) ───────────

export interface CopyRow {
  page: Page;
  body: string;
}

export function copyJson(list: CopyRow[], now = Date.now()): string {
  return JSON.stringify({
    app: "tokkipnotas",
    version: 1,
    at: now,
    pages: list.map(({ page: p, body }) => ({
      id: p.id,
      parent: p.parent,
      title: p.title,
      icon: p.icon,
      color: p.color,
      shape: p.shape,
      kind: p.kind,
      ord: p.ord,
      created: p.created,
      updated: p.updated,
      opened: p.opened,
      favorite: p.favorite,
      trashed: p.trashed,
      props: propsJson(p.props),
      schema: schemaJson(p.schema),
      body,
    })),
  });
}

/** The pages of a copy, or null when it isn't one of Tokkip Notas. */
export function copyOf(text: string): CopyRow[] | null {
  let root: unknown;
  try {
    root = JSON.parse(text);
  } catch {
    return null;
  }
  if (!root || typeof root !== "object") return null;
  const r = root as Record<string, unknown>;
  if (r.app !== "tokkipnotas" || !Array.isArray(r.pages)) return null;
  const out: CopyRow[] = [];
  for (const raw of r.pages) {
    if (!raw || typeof raw !== "object") continue;
    const o = raw as Record<string, unknown>;
    const id = str(o.id);
    if (!id) continue;
    out.push({
      page: page({
        id,
        parent: o.parent == null ? null : str(o.parent),
        title: str(o.title),
        icon: str(o.icon, "doc") || "doc",
        color: num(o.color, 0xffa78bfa),
        shape: Math.trunc(num(o.shape)),
        kind: kindOf(o.kind),
        ord: num(o.ord),
        created: num(o.created),
        updated: num(o.updated),
        opened: num(o.opened),
        favorite: o.favorite === true,
        trashed: num(o.trashed),
        props: typeof o.props === "object" && o.props ? propsOf(JSON.stringify(o.props)) : propsOf(str(o.props)),
        schema: typeof o.schema === "object" && o.schema ? schemaOf(JSON.stringify(o.schema)) : schemaOf(str(o.schema)),
      }),
      body: str(o.body),
    });
  }
  return out;
}
