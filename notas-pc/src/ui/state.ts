// Where you are and what every screen listens to: the index of pages, the
// route ("home", "p:<id>", "search", "trash", "settings"), the folded tree,
// Tokkip's hops and a little toast.

import { useEffect, useState } from "preact/hooks";
import { Index } from "../data/model";
import { Store } from "../data/store";
import { play } from "../tokkip/sound";

export function useIndex(): Index {
  const [ix, setIx] = useState<Index>(Store.index);
  useEffect(() => {
    setIx(Store.index);
    return Store.subscribe(setIx);
  }, []);
  return ix;
}

type Fn = () => void;

function signal<T>(initial: T) {
  let value = initial;
  const subs = new Set<Fn>();
  return {
    get: () => value,
    set(v: T) {
      value = v;
      subs.forEach((f) => f());
    },
    use(): T {
      const [, force] = useState(0);
      useEffect(() => {
        const f = () => force((n) => n + 1);
        subs.add(f);
        return () => {
          subs.delete(f);
        };
      }, []);
      return value;
    },
  };
}

// ── Navigation ────────────────────────────────────────────────────────────────

const stack = signal<string[]>([]);
const forward = signal<string[]>([]);

const FOLD_KEY = "notas.open";
function loadOpen(): Record<string, boolean> {
  try {
    return JSON.parse(localStorage.getItem(FOLD_KEY) ?? "{}");
  } catch {
    return {};
  }
}
const open = signal<Record<string, boolean>>(loadOpen());

export const Nav = {
  use: () => {
    const s = stack.use();
    return s[s.length - 1] ?? "home";
  },
  get current() {
    const s = stack.get();
    return s[s.length - 1] ?? "home";
  },
  page(id: string) {
    Store.opened(id);
    play("page", 0.3);
    Nav.go(`p:${id}`);
    // Its parents unfold so you see where it is.
    const o = { ...open.get() };
    let changed = false;
    for (const p of Store.index.path(id)) {
      if (!o[p.id]) {
        o[p.id] = true;
        changed = true;
      }
    }
    if (changed) Nav.setOpen(o);
  },
  go(route: string) {
    if (Nav.current === route) return;
    stack.set([...stack.get(), route]);
    forward.set([]);
  },
  back(): boolean {
    const s = stack.get();
    if (!s.length) return false;
    forward.set([s[s.length - 1], ...forward.get()]);
    stack.set(s.slice(0, -1));
    return true;
  },
  forward(): boolean {
    const f = forward.get();
    if (!f.length) return false;
    stack.set([...stack.get(), f[0]]);
    forward.set(f.slice(1));
    return true;
  },
  canBack: () => stack.use().length > 0,
  canForward: () => forward.use().length > 0,
  home() {
    if (stack.get().length) {
      stack.set([...stack.get(), "home"]);
      forward.set([]);
    }
  },
  /** Pages that are gone leave the way back. */
  forget(ids: Set<string>) {
    const keep = (r: string) => !(r.startsWith("p:") && ids.has(r.slice(2)));
    stack.set(stack.get().filter(keep));
    forward.set(forward.get().filter(keep));
  },
  useOpen: () => open.use(),
  isOpen: (id: string) => !!open.get()[id],
  toggle(id: string, v?: boolean) {
    Nav.setOpen({ ...open.get(), [id]: v ?? !open.get()[id] });
  },
  setOpen(o: Record<string, boolean>) {
    open.set(o);
    try {
      localStorage.setItem(FOLD_KEY, JSON.stringify(o));
    } catch {
      /* not kept */
    }
  },
};

// ── Tokkip's hops (a step ticked, a card done, an idea caught) ────────────────

const hops = signal(0);
export const Hop = {
  use: () => hops.use(),
  now: () => hops.set(hops.get() + 1),
};

// ── Toast ─────────────────────────────────────────────────────────────────────

const toastSig = signal<{ text: string; at: number; undo?: Fn } | null>(null);
let toastTimer: ReturnType<typeof setTimeout> | undefined;
export const Toast = {
  use: () => toastSig.use(),
  show(text: string, undo?: Fn) {
    toastSig.set({ text, at: Date.now(), undo });
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toastSig.set(null), undo ? 6000 : 3200);
  },
  close: () => toastSig.set(null),
};

// ── The templates sheet (opened from many places) ────────────────────────────

const picker = signal<{ parent: string | null } | null>(null);
export const Templates = {
  use: () => picker.use(),
  open: (parent: string | null) => picker.set({ parent }),
  close: () => picker.set(null),
};

// ── Theme ─────────────────────────────────────────────────────────────────────

export type Theme = "system" | "dark" | "light";
const THEME_KEY = "notas.theme";
function loadTheme(): Theme {
  try {
    const t = localStorage.getItem(THEME_KEY);
    return t === "dark" || t === "light" ? t : "system";
  } catch {
    return "system";
  }
}
const theme = signal<Theme>(loadTheme());
export const ThemeState = {
  use: () => theme.use(),
  get: () => theme.get(),
  set(t: Theme) {
    theme.set(t);
    try {
      localStorage.setItem(THEME_KEY, t);
    } catch {
      /* not kept */
    }
    applyTheme();
  },
};

export function applyTheme() {
  const t = theme.get();
  if (t === "system") document.documentElement.removeAttribute("data-theme");
  else document.documentElement.setAttribute("data-theme", t);
}
