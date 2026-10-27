// Small pieces every screen uses: a page's icon in its Expressive shape, the
// icon set (same keys as Android), modals, menus and the toast.

import type { ComponentChildren, JSX } from "preact";
import type { LucideIcon } from "lucide-preact";
import { useEffect, useLayoutEffect, useRef, useState } from "preact/hooks";
import {
  BookOpen,
  Briefcase,
  Camera,
  Car,
  Check,
  CircleCheckBig,
  Code,
  Dumbbell,
  FileText,
  Flag,
  Gamepad2,
  GraduationCap,
  Heart,
  House,
  Inbox,
  Kanban,
  Lightbulb,
  Music,
  Paintbrush,
  PawPrint,
  PiggyBank,
  Plane,
  Rocket,
  ShoppingCart,
  Sparkles,
  Star,
  Table2,
  TreePine,
  Clapperboard,
  CalendarDays,
  UsersRound,
  UtensilsCrossed,
  Wrench,
} from "lucide-preact";
import { type Page, css } from "../data/model";
import { clipFor } from "../tokkip/shapes";
import { Toast } from "./state";

type Icon = LucideIcon;

/** Every icon a page can have, by the same keys as the Android app. */
export const ICONS: [string, Icon][] = [
  ["doc", FileText],
  ["board", Kanban],
  ["table", Table2],
  ["inbox", Inbox],
  ["idea", Lightbulb],
  ["rocket", Rocket],
  ["art", Paintbrush],
  ["code", Code],
  ["book", BookOpen],
  ["study", GraduationCap],
  ["work", Briefcase],
  ["home", House],
  ["travel", Plane],
  ["gym", Dumbbell],
  ["music", Music],
  ["photo", Camera],
  ["food", UtensilsCrossed],
  ["shop", ShoppingCart],
  ["money", PiggyBank],
  ["pet", PawPrint],
  ["heart", Heart],
  ["spark", Sparkles],
  ["people", UsersRound],
  ["star", Star],
  ["flag", Flag],
  ["calendar", CalendarDays],
  ["check", CircleCheckBig],
  ["leaf", TreePine],
  ["game", Gamepad2],
  ["movie", Clapperboard],
  ["car", Car],
  ["tools", Wrench],
];

const ICON_MAP = new Map(ICONS);
export const iconOf = (key: string): Icon => ICON_MAP.get(key) ?? FileText;

/** A page's icon, in its colour and shape. */
export function PageIcon({ p, size = 28 }: { p: Pick<Page, "icon" | "color" | "shape">; size?: number }) {
  const I = iconOf(p.icon);
  return (
    <span class="page-icon" style={{ width: size, height: size }}>
      <span class="page-icon-bg" style={{ background: css(p.color), clipPath: clipFor(p.shape) }} />
      <I size={Math.round(size * 0.52)} strokeWidth={2.2} color="rgba(0,0,0,.72)" />
    </span>
  );
}

/** An icon in an Expressive shape, in the accent colour. */
export function ShapeIcon({ icon: I, shape = 0, size = 34, tone = "accent" }: { icon: Icon; shape?: number; size?: number; tone?: "accent" | "soft" }) {
  return (
    <span class={`shape-icon ${tone}`} style={{ width: size, height: size }}>
      <span class="shape-icon-bg" style={{ clipPath: clipFor(shape) }} />
      <I size={Math.round(size * 0.5)} strokeWidth={2.2} />
    </span>
  );
}

// ── Modal ─────────────────────────────────────────────────────────────────────

export function Modal({ title, onClose, children, wide, footer }: { title?: string; onClose: () => void; children: ComponentChildren; wide?: boolean; footer?: ComponentChildren }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.stopPropagation();
        onClose();
      }
    };
    window.addEventListener("keydown", onKey, true);
    return () => window.removeEventListener("keydown", onKey, true);
  }, [onClose]);
  return (
    <div class="scrim" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div class={`modal ${wide ? "wide" : ""}`} role="dialog" aria-modal="true" aria-label={title}>
        {title && <h2 class="modal-title">{title}</h2>}
        <div class="modal-body">{children}</div>
        {footer && <div class="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function Confirm({ title, text, ok, danger, onOk, onClose }: { title: string; text?: string; ok: string; danger?: boolean; onOk: () => void; onClose: () => void }) {
  return (
    <Modal
      title={title}
      onClose={onClose}
      footer={
        <>
          <button class="btn ghost" onClick={onClose}>
            Cancelar
          </button>
          <button
            class={`btn ${danger ? "danger" : "primary"}`}
            ref={focusOnMount}
            onClick={() => {
              onClose();
              onOk();
            }}
          >
            {ok}
          </button>
        </>
      }
    >
      {text && <p class="dim">{text}</p>}
    </Modal>
  );
}

/** A dialog that asks for a name. */
export function NameDialog({ title, initial = "", placeholder, onDone }: { title: string; initial?: string; placeholder?: string; onDone: (name: string | null) => void }) {
  const [name, setName] = useState(initial);
  return (
    <Modal
      title={title}
      onClose={() => onDone(null)}
      footer={
        <>
          <button class="btn ghost" onClick={() => onDone(null)}>
            Cancelar
          </button>
          <button class="btn primary" disabled={!name.trim()} onClick={() => onDone(name.trim())}>
            Listo
          </button>
        </>
      }
    >
      <input
        class="field"
        ref={focusOnMount}
        value={name}
        placeholder={placeholder}
        onInput={(e) => setName(e.currentTarget.value)}
        onKeyDown={(e) => e.key === "Enter" && name.trim() && onDone(name.trim())}
      />
    </Modal>
  );
}

// ── Menu (a popover anchored to an element or a point) ───────────────────────

export interface MenuItem {
  label: string;
  icon?: Icon;
  danger?: boolean;
  hint?: string;
  onClick: () => void;
}

export type Anchor = { x: number; y: number } | HTMLElement;

export function Popover({ at, onClose, children, width = 240, cls = "" }: { at: Anchor; onClose: () => void; children: ComponentChildren; width?: number; cls?: string }) {
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState<{ left: number; top: number } | null>(null);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    let x: number;
    let y: number;
    if (at instanceof HTMLElement) {
      const r = at.getBoundingClientRect();
      x = r.left;
      y = r.bottom + 6;
    } else {
      x = at.x;
      y = at.y;
    }
    const h = el.offsetHeight;
    const w = el.offsetWidth;
    if (x + w > window.innerWidth - 8) x = Math.max(8, window.innerWidth - w - 8);
    if (y + h > window.innerHeight - 8) {
      const above = at instanceof HTMLElement ? at.getBoundingClientRect().top - h - 6 : y - h;
      y = Math.max(8, above);
    }
    setPos((p) => (p && p.left === x && p.top === y ? p : { left: x, top: y }));
  });
  useEffect(() => {
    const down = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose();
    };
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.stopPropagation();
        onClose();
      }
    };
    const t = setTimeout(() => window.addEventListener("mousedown", down), 0);
    window.addEventListener("keydown", key, true);
    return () => {
      clearTimeout(t);
      window.removeEventListener("mousedown", down);
      window.removeEventListener("keydown", key, true);
    };
  }, [onClose]);
  return (
    <div ref={ref} class={`popover ${cls}`} style={{ width, left: pos?.left ?? -9999, top: pos?.top ?? -9999 }}>
      {children}
    </div>
  );
}

export function Menu({ at, items, onClose }: { at: Anchor; items: (MenuItem | null)[]; onClose: () => void }) {
  return (
    <Popover at={at} onClose={onClose}>
      {items.map((it, i) =>
        it ? (
          <button
            key={i}
            class={`menu-item ${it.danger ? "danger" : ""}`}
            onClick={() => {
              onClose();
              it.onClick();
            }}
          >
            {it.icon && <it.icon size={16} />}
            <span>{it.label}</span>
            {it.hint && <kbd>{it.hint}</kbd>}
          </button>
        ) : (
          <div key={i} class="menu-sep" />
        ),
      )}
    </Popover>
  );
}

export function ToastView() {
  const t = Toast.use();
  if (!t) return null;
  return (
    <div class="toast" key={t.at}>
      <Check size={16} />
      <span>{t.text}</span>
      {t.undo && (
        <button
          onClick={() => {
            t.undo?.();
            Toast.close();
          }}
        >
          Deshacer
        </button>
      )}
    </div>
  );
}

export function SectionLabel({ children, right }: { children: ComponentChildren; right?: ComponentChildren }) {
  return (
    <div class="section-label">
      <span>{children}</span>
      {right}
    </div>
  );
}

export function greeting(now: number): string {
  const h = new Date(now).getHours();
  if (h >= 5 && h < 12) return "Buenos días";
  if (h >= 12 && h < 19) return "Buenas tardes";
  return "Buenas noches";
}

/** The time, refreshed every minute. */
export function useNow(period = 60_000): number {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), period);
    return () => clearInterval(id);
  }, [period]);
  return now;
}

/** Focuses an element once it's on screen (Preact's autoFocus only works on page load). */
export function focusOnMount(el: HTMLElement | null) {
  if (!el || el.dataset.focused) return;
  el.dataset.focused = "1";
  el.focus({ preventScroll: true });
  if (document.activeElement !== el) requestAnimationFrame(() => el.focus({ preventScroll: true }));
}

export type Ev<T extends EventTarget> = JSX.TargetedEvent<T, Event>;
