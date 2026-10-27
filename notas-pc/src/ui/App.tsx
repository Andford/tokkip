// Tokkip Notas' window: the tree of your pages on the left (drag a page onto
// another to put it inside, or between two to reorder; right-click for more),
// and on the right Inicio or the page you opened. Ctrl+K searches, Ctrl+N
// makes a new page, Alt+← / Alt+→ go back and forward.

import type { JSX } from "preact";
import { useEffect, useState } from "preact/hooks";
import {
  ArrowLeft,
  ArrowRight,
  ArrowRightLeft,
  ChevronRight,
  CloudOff,
  FilePlus2,
  House,
  Inbox,
  Palette,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  RefreshCw,
  Search,
  Settings,
  Star,
  Trash2,
} from "lucide-preact";
import { type Index, type Page, INBOX_ID, shownTitle } from "../data/model";
import { Store } from "../data/store";
import { Tokkip } from "../tokkip/Tokkip";
import { play } from "../tokkip/sound";
import { Home } from "./Home";
import { Menu, PageIcon, ToastView, useNow } from "./kit";
import { MoveDialog, PageView, StyleDialog } from "./PageView";
import { SearchView, SettingsView, TemplateSheet, TrashView, ago, useSync } from "./Screens";
import { Hop, Nav, Templates, Toast, useIndex } from "./state";

const SIDEBAR_KEY = "notas.sidebar";

export function App() {
  const ix = useIndex();
  const route = Nav.use();
  const picker = Templates.use();
  const [searching, setSearching] = useState(false);
  const [side, setSide] = useState(() => {
    try {
      return localStorage.getItem(SIDEBAR_KEY) !== "closed";
    } catch {
      return true;
    }
  });
  const toggleSide = () =>
    setSide((s) => {
      try {
        localStorage.setItem(SIDEBAR_KEY, s ? "closed" : "open");
      } catch {
        /* not kept */
      }
      return !s;
    });

  // A page that's gone (to the Papelera) leaves the way back.
  useEffect(() => {
    const gone = new Set<string>();
    for (const r of [route]) if (r.startsWith("p:") && (ix.page(r.slice(2))?.trashed ?? 1) !== 0) gone.add(r.slice(2));
    if (gone.size) Nav.forget(gone);
  }, [ix, route]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const mod = e.ctrlKey || e.metaKey;
      if (mod && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setSearching(true);
      } else if (mod && e.key.toLowerCase() === "n") {
        e.preventDefault();
        Templates.open(null);
      } else if (mod && e.key === "\\") {
        e.preventDefault();
        toggleSide();
      } else if (e.altKey && e.key === "ArrowLeft") {
        e.preventDefault();
        Nav.back();
      } else if (e.altKey && e.key === "ArrowRight") {
        e.preventDefault();
        Nav.forward();
      }
    };
    const onMouse = (e: MouseEvent) => {
      if (e.button === 3) Nav.back();
      if (e.button === 4) Nav.forward();
    };
    window.addEventListener("keydown", onKey);
    window.addEventListener("mouseup", onMouse);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("mouseup", onMouse);
    };
  }, []);

  let main;
  if (route.startsWith("p:")) {
    const p = ix.page(route.slice(2));
    main = p && !p.trashed ? <PageView p={p} ix={ix} /> : <Home ix={ix} />;
  } else if (route === "trash") main = <TrashView ix={ix} />;
  else if (route === "settings") main = <SettingsView />;
  else main = <Home ix={ix} />;

  return (
    <div class={`app ${side ? "" : "no-side"}`}>
      {side && <Sidebar ix={ix} route={route} onSearch={() => setSearching(true)} onHide={toggleSide} />}
      <main class="main">
        <div class="history">
          {!side && (
            <button class="icon-btn" title="Mostrar el panel (Ctrl+\)" onClick={toggleSide}>
              <PanelLeftOpen size={17} />
            </button>
          )}
          <button class="icon-btn" title="Atrás (Alt+←)" disabled={!Nav.canBack()} onClick={() => Nav.back()}>
            <ArrowLeft size={17} />
          </button>
          <button class="icon-btn" title="Adelante (Alt+→)" disabled={!Nav.canForward()} onClick={() => Nav.forward()}>
            <ArrowRight size={17} />
          </button>
        </div>
        {main}
      </main>
      {picker && <TemplateSheet parent={picker.parent} />}
      {searching && <SearchView onClose={() => setSearching(false)} />}
      <ToastView />
    </div>
  );
}

function Sidebar({ ix, route, onSearch, onHide }: { ix: Index; route: string; onSearch: () => void; onHide: () => void }) {
  const hop = Hop.use();
  const inbox = ix.inbox.length;
  const favs = ix.favorites;
  return (
    <aside class="side">
      <div class="side-head">
        <Tokkip mood="calm" size={40} jump={hop} />
        <b class="brand">Tokkip Notas</b>
        <button class="icon-btn" title="Ocultar el panel (Ctrl+\)" onClick={onHide}>
          <PanelLeftClose size={16} />
        </button>
      </div>
      <button class="side-search" onClick={onSearch}>
        <Search size={15} />
        <span>Buscar</span>
        <kbd>Ctrl K</kbd>
      </button>
      <nav class="side-nav">
        <SideItem icon={House} label="Inicio" on={route === "home"} onClick={() => Nav.go("home")} />
        <SideItem icon={Inbox} label="Bandeja" count={inbox || undefined} on={route === `p:${INBOX_ID}`} onClick={() => Nav.page(INBOX_ID)} />
      </nav>
      <div class="side-scroll">
        {favs.length > 0 && (
          <>
            <div class="side-label">Favoritos</div>
            {favs.map((p) => (
              <button key={p.id} class={`tree-row ${route === `p:${p.id}` ? "on" : ""}`} style={{ paddingLeft: 10 }} onClick={() => Nav.page(p.id)}>
                <Star size={12} class="fav-mark" fill="currentColor" />
                <PageIcon p={p} size={20} />
                <span class="tree-title">{shownTitle(p)}</span>
              </button>
            ))}
          </>
        )}
        <div class="side-label">
          Páginas
          <button class="icon-btn tiny" title="Nueva página (Ctrl+N)" onClick={() => Templates.open(null)}>
            <Plus size={14} />
          </button>
        </div>
        <Tree ix={ix} route={route} />
      </div>
      <div class="side-foot">
        <SideItem icon={Trash2} label="Papelera" count={ix.trash.length || undefined} on={route === "trash"} onClick={() => Nav.go("trash")} />
        <SideItem icon={Settings} label="Ajustes" on={route === "settings"} onClick={() => Nav.go("settings")} />
        <SyncChip />
      </div>
    </aside>
  );
}

/** Sync, at a glance: syncing, all good, or something's wrong (click: Ajustes). */
function SyncChip() {
  const s = useSync();
  useNow(30_000);
  if (!s.enabled) return null;
  const label = s.error ? "Sin sincronizar" : s.running ? "Sincronizando…" : `Sincronizado ${ago(s.last)}`;
  return (
    <button class={`sync-chip ${s.error ? "bad" : ""}`} title={s.error ?? s.url} onClick={() => (s.error ? Nav.go("settings") : void Store.syncNow())}>
      {s.error ? <CloudOff size={14} /> : <RefreshCw size={14} class={s.running ? "spin" : ""} />}
      <span>{label}</span>
    </button>
  );
}

function SideItem({ icon: I, label, on, count, onClick }: { icon: typeof House; label: string; on: boolean; count?: number; onClick: () => void }) {
  return (
    <button class={`side-item ${on ? "on" : ""}`} onClick={onClick}>
      <I size={16} />
      <span>{label}</span>
      {count !== undefined && <span class="count">{count}</span>}
    </button>
  );
}

type DropAt = { id: string; where: "before" | "inside" | "after" } | null;

function Tree({ ix, route }: { ix: Index; route: string }) {
  const open = Nav.useOpen();
  const [drag, setDrag] = useState<string | null>(null);
  const [over, setOver] = useState<DropAt>(null);
  const [menu, setMenu] = useState<{ p: Page; at: { x: number; y: number } } | null>(null);
  const [styling, setStyling] = useState<Page | null>(null);
  const [moving, setMoving] = useState<Page | null>(null);

  const drop = () => {
    const from = drag;
    const to = over;
    setDrag(null);
    setOver(null);
    if (!from || !to || from === to.id) return;
    const target = ix.page(to.id);
    if (!target) return;
    let ok: boolean;
    if (to.where === "inside") {
      ok = Store.move(from, target.id);
      if (ok) Nav.toggle(target.id, true);
    } else if (to.where === "before") ok = Store.placeBefore(from, target.id);
    else {
      const sibs = ix.children(target.parent).filter((s) => s.id !== from);
      const i = sibs.findIndex((s) => s.id === target.id);
      const next = sibs[i + 1];
      ok = next ? Store.placeBefore(from, next.id) : Store.move(from, target.parent);
    }
    if (ok) play("tick", 0.35);
    else Toast.show("Una página no puede ir dentro de sí misma");
  };

  const rows: JSX.Element[] = [];
  const walk = (parent: string | null, depth: number) => {
    const kids = parent === null ? ix.roots : ix.children(parent);
    for (const p of kids) {
      const kidCount = ix.children(p.id).length;
      const expandable = p.kind === "DOC" && kidCount > 0;
      const isOpen = !!open[p.id];
      const cls = over?.id === p.id ? `drop-${over.where}` : "";
      rows.push(
        <div
          key={p.id}
          class={`tree-row ${route === `p:${p.id}` ? "on" : ""} ${drag === p.id ? "dragging" : ""} ${cls}`}
          style={{ paddingLeft: 6 + depth * 16 }}
          draggable
          role="button"
          tabIndex={0}
          onClick={() => Nav.page(p.id)}
          onKeyDown={(e) => e.key === "Enter" && Nav.page(p.id)}
          onContextMenu={(e) => {
            e.preventDefault();
            setMenu({ p, at: { x: e.clientX, y: e.clientY } });
          }}
          onDragStart={(e) => {
            e.dataTransfer?.setData("text/plain", shownTitle(p));
            if (e.dataTransfer) e.dataTransfer.effectAllowed = "move";
            setDrag(p.id);
          }}
          onDragEnd={() => {
            setDrag(null);
            setOver(null);
          }}
          onDragOver={(e) => {
            if (!drag || drag === p.id) return;
            e.preventDefault();
            const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
            const y = (e.clientY - r.top) / r.height;
            const where = y < 0.28 ? "before" : y > 0.72 ? "after" : "inside";
            if (over?.id !== p.id || over.where !== where) setOver({ id: p.id, where });
          }}
          onDrop={(e) => {
            e.preventDefault();
            drop();
          }}
        >
          <button
            class={`tree-caret ${expandable ? "" : "hidden"} ${isOpen ? "open" : ""}`}
            tabIndex={-1}
            onClick={(e) => {
              e.stopPropagation();
              if (expandable) Nav.toggle(p.id);
            }}
          >
            <ChevronRight size={14} />
          </button>
          <PageIcon p={p} size={20} />
          <span class="tree-title">{shownTitle(p)}</span>
          {p.kind === "COLLECTION" && <span class="tree-count">{kidCount}</span>}
          {p.kind === "DOC" && (
            <button
              class="tree-add"
              title="Nueva página dentro"
              tabIndex={-1}
              onClick={(e) => {
                e.stopPropagation();
                Templates.open(p.id);
              }}
            >
              <Plus size={14} />
            </button>
          )}
        </div>,
      );
      if (expandable && isOpen) walk(p.id, depth + 1);
    }
  };
  walk(null, 0);

  return (
    <div class="tree" onDragLeave={(e) => e.currentTarget === e.target && setOver(null)}>
      {rows.length ? rows : <p class="dim2 small pad">Aún no hay páginas. Crea una con el +.</p>}
      {menu && (
        <Menu
          at={menu.at}
          onClose={() => setMenu(null)}
          items={[
            ...(menu.p.kind === "DOC" ? [{ label: "Nueva página dentro", icon: FilePlus2, onClick: () => Templates.open(menu.p.id) }] : []),
            { label: "Ícono, color y forma", icon: Palette, onClick: () => setStyling(menu.p) },
            { label: "Mover a…", icon: ArrowRightLeft, onClick: () => setMoving(menu.p) },
            {
              label: menu.p.favorite ? "Quitar de favoritos" : "A favoritos",
              icon: Star,
              onClick: () => Store.update({ ...menu.p, favorite: !menu.p.favorite }, false),
            },
            null,
            {
              label: "A la papelera",
              icon: Trash2,
              danger: true,
              onClick: () => {
                const p = menu.p;
                Store.trash(p.id);
                Toast.show(`«${shownTitle(p)}» se fue a la Papelera`, () => Store.restore(p.id));
              },
            },
          ]}
        />
      )}
      {styling && <StyleDialog p={styling} onClose={() => setStyling(null)} />}
      {moving && <MoveDialog pages={[moving]} ix={ix} onClose={() => setMoving(null)} />}
    </div>
  );
}

