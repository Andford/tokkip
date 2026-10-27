// Writing by hand with the pen (the Yoga's, or any Windows pen), a mouse or a
// finger: a surface that draws a Drawing (ink.ts), the toolbar (pluma,
// marcatextos, borrador, colours, sizes, undo), a whole handwritten page, and
// a drawing inside a page of text.
//
// The pen's pressure sets the width; its eraser end or side button erases.
// Fingers scroll the page (so the palm never draws) unless "Con el dedo" is
// on; then they draw too.

import type { ComponentChildren } from "preact";
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "preact/hooks";
import { Eraser, Highlighter, Minus, PenLine, Plus, Redo2, Trash2, Undo2 } from "lucide-preact";
import { Store } from "../data/store";
import { play } from "../tokkip/sound";
import {
  type Bg,
  type Drawing,
  type Stroke,
  HL_COLORS,
  HL_SIZE,
  INK_W,
  PEN_COLORS,
  PEN_SIZES,
  drawBackground,
  drawStroke,
  drawingJson,
  emptyDrawing,
  lowest,
  parseDrawing,
  strokesHit,
} from "./ink";

export type Tool = "pen" | "hl" | "eraser";

export interface ToolState {
  tool: Tool;
  pen: number;
  hl: number;
  size: number;
}

const TOOL_KEY = "notas.ink.tools";
const FINGER_KEY = "notas.ink.finger";

function loadTools(): ToolState {
  try {
    const t = JSON.parse(localStorage.getItem(TOOL_KEY) ?? "{}");
    return {
      // The eraser is for a moment: next time you start with the pen.
      tool: t.tool === "hl" ? "hl" : "pen",
      pen: typeof t.pen === "number" ? t.pen : 0,
      hl: typeof t.hl === "number" ? t.hl : HL_COLORS[0],
      size: typeof t.size === "number" ? t.size : PEN_SIZES[1],
    };
  } catch {
    return { tool: "pen", pen: 0, hl: HL_COLORS[0], size: PEN_SIZES[1] };
  }
}

export function useTools(): [ToolState, (t: Partial<ToolState>) => void] {
  const [t, setT] = useState<ToolState>(loadTools);
  const set = useCallback((n: Partial<ToolState>) => {
    setT((old) => {
      const next = { ...old, ...n };
      try {
        localStorage.setItem(TOOL_KEY, JSON.stringify(next));
      } catch {
        /* not kept */
      }
      return next;
    });
  }, []);
  return [t, set];
}

let fingerDraws = (() => {
  try {
    return localStorage.getItem(FINGER_KEY) === "1";
  } catch {
    return false;
  }
})();

function paperColors() {
  const cs = getComputedStyle(document.documentElement);
  return {
    paper: cs.getPropertyValue("--paper").trim() || "#fffdf8",
    rule: cs.getPropertyValue("--rule").trim() || "rgba(80,90,140,.18)",
    ink: cs.getPropertyValue("--ink").trim() || "#1b1530",
  };
}

/** Redraws when the theme changes (Ajustes, or Windows switching light/dark). */
function useThemeTick(): number {
  const [n, setN] = useState(0);
  useEffect(() => {
    const mq = window.matchMedia("(prefers-color-scheme: dark)");
    const bump = () => setN((x) => x + 1);
    mq.addEventListener("change", bump);
    const mo = new MutationObserver(bump);
    mo.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
    return () => {
      mq.removeEventListener("change", bump);
      mo.disconnect();
    };
  }, []);
  return n;
}

// ── The surface ───────────────────────────────────────────────────────────────

export function InkSurface({
  drawing,
  tools,
  onChange,
  grow = false,
}: {
  drawing: Drawing;
  tools: ToolState;
  /** A finished stroke or erase: the new drawing. */
  onChange: (d: Drawing) => void;
  /** A page: grows as you write near the bottom. */
  grow?: boolean;
}) {
  const wrap = useRef<HTMLDivElement>(null);
  const base = useRef<HTMLCanvasElement>(null);
  const live = useRef<HTMLCanvasElement>(null);
  const [width, setWidth] = useState(0);
  const theme = useThemeTick();
  const state = useRef({ drawing, tools, onChange });
  state.current = { drawing, tools, onChange };
  const scale = width / INK_W;
  const height = Math.round(drawing.h * scale);

  useLayoutEffect(() => {
    const el = wrap.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setWidth(el.clientWidth));
    ro.observe(el);
    setWidth(el.clientWidth);
    return () => ro.disconnect();
  }, []);

  // The paper and every finished stroke.
  useEffect(() => {
    const c = base.current;
    const l = live.current;
    if (!c || !l || !width) return;
    const dpr = window.devicePixelRatio || 1;
    for (const cv of [c, l]) {
      cv.width = Math.round(width * dpr);
      cv.height = Math.round(height * dpr);
    }
    const x = c.getContext("2d")!;
    x.setTransform(dpr, 0, 0, dpr, 0, 0);
    const pc = paperColors();
    drawBackground(x, drawing.bg, width, height, scale, pc);
    for (const s of drawing.s) drawStroke(x, s, scale, pc.ink);
  }, [drawing, width, height, theme]);

  // Drawing as it happens: the stroke in progress on the top canvas.
  useEffect(() => {
    const l = live.current;
    if (!l) return;
    let current: Stroke | null = null;
    let erasing: Set<number> | null = null;
    let pointer = -1;
    let scrollY: number | null = null;
    const lx = () => l.getContext("2d")!;
    const pos = (e: PointerEvent) => {
      const r = l.getBoundingClientRect();
      const sc = r.width / INK_W;
      return [(e.clientX - r.left) / sc, (e.clientY - r.top) / sc];
    };
    const pressure = (e: PointerEvent) => (e.pointerType === "pen" ? Math.round(Math.max(0.05, e.pressure || 0.5) * 100) : 55);
    const redrawLive = () => {
      const x = lx();
      const dpr = window.devicePixelRatio || 1;
      x.setTransform(1, 0, 0, 1, 0, 0);
      x.clearRect(0, 0, l.width, l.height);
      x.setTransform(dpr, 0, 0, dpr, 0, 0);
      const sc = l.clientWidth / INK_W;
      const pc = paperColors();
      if (current) drawStroke(x, current, sc, pc.ink);
      if (erasing && erasing.size) {
        // What's about to go, faded out.
        x.globalCompositeOperation = "source-over";
        for (const i of erasing) {
          const s = state.current.drawing.s[i];
          if (s) drawStroke(x, { ...s, c: 0, k: s.k }, sc, pc.paper);
        }
      }
    };
    const scroller = () => l.closest(".page-scroll") as HTMLElement | null;

    const down = (e: PointerEvent) => {
      if (pointer !== -1) return;
      if (e.pointerType === "touch" && !fingerDraws) {
        // A finger (or a palm) scrolls the page instead.
        scrollY = e.clientY;
        pointer = e.pointerId;
        l.setPointerCapture(e.pointerId);
        return;
      }
      if (e.pointerType === "mouse" && e.button !== 0) return;
      e.preventDefault();
      pointer = e.pointerId;
      l.setPointerCapture(e.pointerId);
      const { tools: t } = state.current;
      // The pen's eraser end (buttons 32) or its side button (2) erases.
      const eraserEnd = e.pointerType === "pen" && ((e.buttons & 32) !== 0 || (e.buttons & 2) !== 0 || e.button === 5);
      const [x, y] = pos(e);
      if (t.tool === "eraser" || eraserEnd) {
        erasing = new Set(strokesHit(state.current.drawing, x, y, 10));
      } else if (t.tool === "hl") {
        current = { k: "hl", c: t.hl, w: HL_SIZE, p: [Math.round(x * 2), Math.round(y * 2), 50] };
      } else {
        current = { k: "pen", c: t.pen, w: t.size, p: [Math.round(x * 2), Math.round(y * 2), pressure(e)] };
      }
      redrawLive();
    };
    const move = (e: PointerEvent) => {
      if (e.pointerId !== pointer) return;
      if (scrollY !== null) {
        scroller()?.scrollBy(0, scrollY - e.clientY);
        scrollY = e.clientY;
        return;
      }
      const events = typeof e.getCoalescedEvents === "function" ? e.getCoalescedEvents() : [e];
      for (const ev of events.length ? events : [e]) {
        const [x, y] = pos(ev);
        if (erasing) {
          for (const i of strokesHit(state.current.drawing, x, y, 10)) erasing.add(i);
        } else if (current) {
          const p = current.p;
          const lx2 = p[p.length - 3];
          const ly2 = p[p.length - 2];
          const nx = Math.round(x * 2);
          const ny = Math.round(y * 2);
          if (Math.abs(nx - lx2) + Math.abs(ny - ly2) < 2) continue;
          p.push(nx, ny, current.k === "hl" ? 50 : pressure(ev));
        }
      }
      redrawLive();
    };
    const up = (e: PointerEvent) => {
      if (e.pointerId !== pointer) return;
      pointer = -1;
      if (scrollY !== null) {
        scrollY = null;
        return;
      }
      const { drawing: d, onChange: done } = state.current;
      let next: Drawing | null = null;
      if (erasing) {
        if (erasing.size) next = { ...d, s: d.s.filter((_, i) => !erasing!.has(i)) };
        erasing = null;
      } else if (current) {
        next = { ...d, s: [...d.s, current] };
        current = null;
      }
      if (next) {
        if (grow && lowest(next) > next.h - 260) next = { ...next, h: next.h + 700 };
        done(next);
      }
      // The live canvas clears once the base one has the stroke.
      requestAnimationFrame(() => redrawLive());
    };
    l.addEventListener("pointerdown", down);
    l.addEventListener("pointermove", move);
    l.addEventListener("pointerup", up);
    l.addEventListener("pointercancel", up);
    return () => {
      l.removeEventListener("pointerdown", down);
      l.removeEventListener("pointermove", move);
      l.removeEventListener("pointerup", up);
      l.removeEventListener("pointercancel", up);
    };
  }, [grow]);

  return (
    <div ref={wrap} class="ink-surface" style={{ height: height || 200 }}>
      <canvas ref={base} style={{ width, height }} />
      <canvas ref={live} class={`ink-live tool-${tools.tool}`} style={{ width, height }} />
    </div>
  );
}

// ── Toolbar ───────────────────────────────────────────────────────────────────

const css = (c: number) => (c === 0 ? "var(--ink)" : "#" + (c % 0x1000000).toString(16).padStart(6, "0"));

export function InkToolbar({
  tools,
  setTools,
  undo,
  redo,
  canUndo,
  canRedo,
  bg,
  setBg,
  compact = false,
  extra,
}: {
  tools: ToolState;
  setTools: (t: Partial<ToolState>) => void;
  undo: () => void;
  redo: () => void;
  canUndo: boolean;
  canRedo: boolean;
  bg?: Bg;
  setBg?: (b: Bg) => void;
  compact?: boolean;
  extra?: ComponentChildren;
}) {
  const [finger, setFinger] = useState(fingerDraws);
  const colors = tools.tool === "hl" ? HL_COLORS : PEN_COLORS;
  const color = tools.tool === "hl" ? tools.hl : tools.pen;
  return (
    <div class={`ink-bar ${compact ? "compact" : ""}`}>
      <div class="ink-group">
        <button class={tools.tool === "pen" ? "on" : ""} title="Pluma" onClick={() => setTools({ tool: "pen" })}>
          <PenLine size={18} />
        </button>
        <button class={tools.tool === "hl" ? "on" : ""} title="Marcatextos" onClick={() => setTools({ tool: "hl" })}>
          <Highlighter size={18} />
        </button>
        <button class={tools.tool === "eraser" ? "on" : ""} title="Borrador (también el otro extremo o el botón de la pluma)" onClick={() => setTools({ tool: "eraser" })}>
          <Eraser size={18} />
        </button>
      </div>
      {tools.tool !== "eraser" && (
        <div class="ink-group">
          {colors.map((c) => (
            <button
              key={c}
              class={`ink-color ${c === color ? "on" : ""}`}
              title={c === 0 ? "Tinta" : undefined}
              onClick={() => setTools(tools.tool === "hl" ? { hl: c } : { pen: c })}
            >
              <span style={{ background: css(c) }} />
            </button>
          ))}
        </div>
      )}
      {tools.tool === "pen" && !compact && (
        <div class="ink-group">
          {PEN_SIZES.map((s) => (
            <button key={s} class={`ink-size ${s === tools.size ? "on" : ""}`} title="Grosor" onClick={() => setTools({ size: s })}>
              <span style={{ width: 4 + s * 2, height: 4 + s * 2 }} />
            </button>
          ))}
        </div>
      )}
      <div class="ink-group">
        <button title="Deshacer (Ctrl+Z)" disabled={!canUndo} onClick={undo}>
          <Undo2 size={18} />
        </button>
        <button title="Rehacer (Ctrl+Y)" disabled={!canRedo} onClick={redo}>
          <Redo2 size={18} />
        </button>
      </div>
      {bg && setBg && (
        <div class="ink-group bg">
          {(
            [
              ["lines", "Renglones"],
              ["grid", "Cuadrícula"],
              ["dots", "Puntos"],
              ["plain", "Lisa"],
            ] as [Bg, string][]
          ).map(([b, label]) => (
            <button key={b} class={bg === b ? "on" : ""} onClick={() => setBg(b)}>
              {label}
            </button>
          ))}
        </div>
      )}
      {!compact && (
        <label class="ink-finger" title="Si no tienes pluma a la mano">
          <input
            type="checkbox"
            checked={finger}
            onChange={(e) => {
              fingerDraws = e.currentTarget.checked;
              setFinger(fingerDraws);
              try {
                localStorage.setItem(FINGER_KEY, fingerDraws ? "1" : "0");
              } catch {
                /* not kept */
              }
            }}
          />
          Dibujar con el dedo
        </label>
      )}
      {extra}
    </div>
  );
}

/** A drawing with its own undo history. */
export function useHistory(initial: Drawing | null, save: (d: Drawing) => void) {
  const [d, setD] = useState<Drawing | null>(initial);
  const past = useRef<Drawing[]>([]);
  const future = useRef<Drawing[]>([]);
  const [, bump] = useState(0);
  const change = (next: Drawing) => {
    if (d) past.current.push(d);
    if (past.current.length > 200) past.current.shift();
    future.current = [];
    setD(next);
    save(next);
  };
  const undo = () => {
    const prev = past.current.pop();
    if (!prev || !d) return;
    future.current.push(d);
    setD(prev);
    save(prev);
    bump((n) => n + 1);
  };
  const redo = () => {
    const next = future.current.pop();
    if (!next || !d) return;
    past.current.push(d);
    setD(next);
    save(next);
    bump((n) => n + 1);
  };
  const reset = (x: Drawing | null) => {
    past.current = [];
    future.current = [];
    setD(x);
  };
  return { d, change, undo, redo, reset, canUndo: past.current.length > 0, canRedo: future.current.length > 0 };
}

/** Ctrl+Z / Ctrl+Y while this drawing is the one being worked on. */
function useUndoKeys(active: boolean, undo: () => void, redo: () => void) {
  useEffect(() => {
    if (!active) return;
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (t.closest("input, textarea")) return;
      if (!(e.ctrlKey || e.metaKey)) return;
      const k = e.key.toLowerCase();
      if (k === "z" && !e.shiftKey) {
        e.preventDefault();
        undo();
      } else if (k === "y" || (k === "z" && e.shiftKey)) {
        e.preventDefault();
        redo();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });
}

// ── A page written by hand ────────────────────────────────────────────────────

export function InkPage({ id }: { id: string }) {
  const [tools, setTools] = useTools();
  const dirty = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  const latest = useRef<Drawing | null>(null);
  const save = (d: Drawing) => {
    latest.current = d;
    dirty.current = true;
    clearTimeout(timer.current);
    timer.current = setTimeout(flush, 800);
  };
  const flush = () => {
    clearTimeout(timer.current);
    if (!dirty.current || !latest.current) return;
    dirty.current = false;
    const p = Store.page(id);
    if (p) Store.saveBody(id, p.title, drawingJson(latest.current));
  };
  const h = useHistory(null, save);

  useEffect(() => {
    let alive = true;
    Store.body(id).then((body) => {
      if (alive) h.reset(parseDrawing(body, () => emptyDrawing("lines")));
    });
    const off = Store.onRemote((ids) => {
      if (!ids.has(id) || dirty.current) return;
      Store.body(id).then((body) => alive && !dirty.current && h.reset(parseDrawing(body, () => emptyDrawing("lines"))));
    });
    window.addEventListener("blur", flush);
    return () => {
      alive = false;
      off();
      window.removeEventListener("blur", flush);
      flush();
    };
  }, [id]);
  useUndoKeys(true, h.undo, h.redo);

  if (!h.d) return <div class="ink-loading" />;
  const d = h.d;
  return (
    <div class="ink-page">
      <div class="ink-sticky">
        <InkToolbar
          tools={tools}
          setTools={setTools}
          undo={h.undo}
          redo={h.redo}
          canUndo={h.canUndo}
          canRedo={h.canRedo}
          bg={d.bg}
          setBg={(bg) => h.change({ ...d, bg })}
          extra={
            <div class="ink-group">
              <button title="Más hoja" onClick={() => h.change({ ...d, h: d.h + 700 })}>
                <Plus size={18} />
              </button>
            </div>
          }
        />
      </div>
      <div class="ink-paper">
        <InkSurface drawing={d} tools={tools} grow onChange={(n) => h.change(n)} />
      </div>
    </div>
  );
}

// ── A drawing inside a page of text ───────────────────────────────────────────

export function DrawBlock({ text, onChange, onRemove }: { text: string; onChange: (json: string) => void; onRemove: () => void }) {
  const [tools, setTools] = useTools();
  const [active, setActive] = useState(false);
  const h = useHistory(parseDrawing(text, () => emptyDrawing("plain", 450)), (d) => onChange(drawingJson(d)));
  useUndoKeys(active, h.undo, h.redo);
  const d = h.d!;
  return (
    <div
      class={`draw-block ${active ? "active" : ""}`}
      onPointerDown={() => setActive(true)}
      onMouseLeave={() => setActive(false)}
      onFocusCapture={() => setActive(true)}
      tabIndex={-1}
    >
      <InkToolbar
        compact
        tools={tools}
        setTools={setTools}
        undo={h.undo}
        redo={h.redo}
        canUndo={h.canUndo}
        canRedo={h.canRedo}
        extra={
          <div class="ink-group">
            <button title="Más pequeño" disabled={d.h <= 250} onClick={() => h.change({ ...d, h: Math.max(250, d.h - 150) })}>
              <Minus size={18} />
            </button>
            <button title="Más grande" onClick={() => h.change({ ...d, h: d.h + 150 })}>
              <Plus size={18} />
            </button>
            <button
              title="Quitar el dibujo"
              onClick={() => {
                onRemove();
                play("tick", 0.3);
              }}
            >
              <Trash2 size={17} />
            </button>
          </div>
        }
      />
      <InkSurface drawing={d} tools={tools} onChange={(n) => h.change(n)} />
    </div>
  );
}
