// Where pages are kept. In the app: a small SQLite database (notas.db) in
// your AppData, through the Rust side (src-tauri/src/store.rs), with the same
// table as Android. In the browser preview (npm run dev without Tauri): in
// memory, saved to localStorage so a reload keeps your test pages.

import { type CopyRow, type Page, kindOf, page, propsJson, propsOf, schemaJson, schemaOf } from "./model";

/** A page as the database row has it: props and schema as JSON text. */
export interface Row {
  id: string;
  parent: string | null;
  title: string;
  icon: string;
  color: number;
  shape: number;
  kind: string;
  ord: number;
  created: number;
  updated: number;
  opened: number;
  favorite: boolean;
  trashed: number;
  props: string;
  schema: string;
  preview: string;
  steps: number;
  done: number;
  /** Only when it's being written or copied; undefined keeps the stored one. */
  body?: string | null;
}

export function rowOf(p: Page, body?: string): Row {
  return {
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
    preview: p.preview,
    steps: p.steps,
    done: p.done,
    body: body ?? null,
  };
}

export function pageOfRow(r: Row): Page {
  return page({
    id: r.id,
    parent: r.parent ?? null,
    title: r.title ?? "",
    icon: r.icon || "doc",
    color: r.color,
    shape: r.shape,
    kind: kindOf(r.kind),
    ord: r.ord,
    created: r.created,
    updated: r.updated,
    opened: r.opened,
    favorite: !!r.favorite,
    trashed: r.trashed,
    props: propsOf(r.props),
    schema: schemaOf(r.schema),
    preview: r.preview ?? "",
    steps: r.steps ?? 0,
    done: r.done ?? 0,
  });
}

export interface Backend {
  /** Every page without its body (and the Papelera's old things gone). */
  load(): Promise<Row[]>;
  body(id: string): Promise<string>;
  /** Inserts or replaces; a row whose body is null keeps its stored body. `quiet` changes stay on this device (not synced). */
  upsert(rows: Row[], quiet?: boolean): Promise<void>;
  remove(ids: string[]): Promise<void>;
  /** Everything, with bodies (for a copy and for search). */
  dump(): Promise<Row[]>;
  /** Puts in a copy's rows, after emptying everything when `replace`. */
  bulk(rows: Row[], replace: boolean): Promise<void>;
  /** Asks where to save a copy and writes it; the file's name, or null. */
  saveCopy(json: string, name: string): Promise<string | null>;
  /** Asks for a copy to open; its text, or null. */
  openCopy(): Promise<string | null>;
  /** Where the database lives, to show in Ajustes. */
  where(): Promise<string>;
  /** Sync with your own server (null where there's none, as in the browser preview). */
  sync: SyncApi | null;
}

export interface SyncStatus {
  enabled: boolean;
  url: string;
  last: number;
  pending: number;
  error: string | null;
}

export interface SyncReport {
  pushed: number;
  pulled: number;
  conflicts: number;
  changed: string[];
  at: number;
}

export interface SyncApi {
  status(): Promise<SyncStatus>;
  connect(url: string, key: string, phrase: string): Promise<SyncReport>;
  /** null when it isn't set up or one is already running. */
  now(): Promise<SyncReport | null>;
  disconnect(): Promise<void>;
}

// ── Tauri ─────────────────────────────────────────────────────────────────────

export function isTauri(): boolean {
  return typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;
}

async function call<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
  const { invoke } = await import("@tauri-apps/api/core");
  return invoke<T>(cmd, args);
}

export const tauriBackend: Backend = {
  load: () => call<Row[]>("notas_load"),
  body: (id) => call<string>("notas_body", { id }),
  upsert: (rows, quiet) => call("notas_upsert", { rows, quiet: !!quiet }),
  remove: (ids) => call("notas_remove", { ids }),
  dump: () => call<Row[]>("notas_dump"),
  bulk: (rows, replace) => call("notas_bulk", { rows, replace }),
  saveCopy: (json, name) => call<string | null>("notas_save_copy", { json, name }),
  openCopy: () => call<string | null>("notas_open_copy"),
  where: () => call<string>("notas_where"),
  sync: {
    status: () => call<SyncStatus>("sync_status"),
    connect: (url, key, phrase) => call<SyncReport>("sync_connect", { url, key, phrase }),
    now: () => call<SyncReport | null>("sync_now"),
    disconnect: () => call("sync_disconnect"),
  },
};

// ── Browser preview ───────────────────────────────────────────────────────────

const KEY = "tokkipnotas.preview";

export function memoryBackend(persist = true): Backend {
  const rows = new Map<string, Row & { body: string }>();
  const save = () => {
    if (!persist) return;
    try {
      localStorage.setItem(KEY, JSON.stringify([...rows.values()]));
    } catch {
      /* not kept */
    }
  };
  if (persist) {
    try {
      for (const r of JSON.parse(localStorage.getItem(KEY) ?? "[]") as (Row & { body: string })[]) rows.set(r.id, r);
    } catch {
      /* starts empty */
    }
  }
  const put = (r: Row) => {
    const old = rows.get(r.id);
    rows.set(r.id, { ...r, body: r.body ?? old?.body ?? "" });
  };
  return {
    async load() {
      const cut = Date.now() - 30 * 86_400_000;
      for (const [id, r] of rows) if (r.trashed > 0 && r.trashed < cut) rows.delete(id);
      return [...rows.values()].map(({ body: _b, ...r }) => ({ ...r, body: null }));
    },
    async body(id) {
      return rows.get(id)?.body ?? "";
    },
    async upsert(list) {
      list.forEach(put);
      save();
    },
    async remove(ids) {
      ids.forEach((i) => rows.delete(i));
      save();
    },
    async dump() {
      return [...rows.values()].map((r) => ({ ...r }));
    },
    async bulk(list, replace) {
      if (replace) rows.clear();
      list.forEach(put);
      save();
    },
    async saveCopy(json, name) {
      const a = document.createElement("a");
      a.href = URL.createObjectURL(new Blob([json], { type: "application/json" }));
      a.download = name;
      a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 2000);
      return name;
    },
    openCopy() {
      return new Promise((resolve) => {
        const input = document.createElement("input");
        input.type = "file";
        input.accept = ".json,application/json";
        input.onchange = () => {
          const f = input.files?.[0];
          if (!f) return resolve(null);
          f.text().then(resolve, () => resolve(null));
        };
        input.click();
      });
    },
    async where() {
      return "este navegador (vista previa)";
    },
    sync: null,
  };
}

export function copyRows(list: CopyRow[]): Row[] {
  return list.map(({ page: p, body }) => rowOf(p, body));
}
