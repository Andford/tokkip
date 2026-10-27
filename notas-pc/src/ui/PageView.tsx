// A page on screen: where it is (the path above it), its icon and title, and
// then its text, or its board/table when it's a collection. A card shows its
// values above its notes. The Bandeja has its own simple view.

import { Fragment } from "preact";
import { useEffect, useRef, useState } from "preact/hooks";
import { ArrowRightLeft, ChevronRight, ClipboardCopy, FilePlus2, Inbox, MoreHorizontal, Palette, Star, Trash2 } from "lucide-preact";
import { blocksOf } from "../data/blocks";
import { type Index, type Page, INBOX_ID, PALETTE, css, shownTitle } from "../data/model";
import { Store } from "../data/store";
import { SHAPE_NAMES, SHAPES, clipFor } from "../tokkip/shapes";
import { play } from "../tokkip/sound";
import { CollectionView, PropsPanel } from "./Collection";
import { DocEditor, PageLink } from "./Editor";
import { InkPage } from "../ink/InkView";
import { CaptureCard } from "./Home";
import { ICONS, Menu, Modal, PageIcon, SectionLabel, focusOnMount } from "./kit";
import { Hop, Nav, Toast } from "./state";

export function PageView({ p, ix }: { p: Page; ix: Index }) {
  if (p.id === INBOX_ID) return <InboxView ix={ix} />;
  const collection = ix.collectionOf(p);
  return (
    <div class="page-view">
      <PageTop p={p} ix={ix} />
      <div class="page-scroll">
        <div class={`page-inner ${p.kind === "COLLECTION" ? "wide" : p.kind === "INK" ? "ink" : ""}`}>
          <PageHeader key={`h-${p.id}`} p={p} />
          {collection && <PropsPanel card={p} collection={collection} />}
          {p.kind === "COLLECTION" ? <CollectionView c={p} ix={ix} /> : p.kind === "INK" ? <InkPage key={p.id} id={p.id} /> : <DocEditor key={p.id} p={p} ix={ix} />}
        </div>
      </div>
    </div>
  );
}

function PageTop({ p, ix }: { p: Page; ix: Index }) {
  const path = ix.path(p.id);
  const [menu, setMenu] = useState<HTMLElement | null>(null);
  const [styling, setStyling] = useState(false);
  const [moving, setMoving] = useState(false);

  const copyText = async () => {
    const body = await Store.body(p.id);
    const text = [
      p.title,
      blocksOf(body)
        .filter((b) => b.kind !== "PAGE")
        .map((b) => (b.kind === "TODO" ? "☐ " : b.kind === "DONE" ? "☑ " : b.kind === "BULLET" ? "• " : b.kind === "DIVIDER" ? "———" : "") + b.text)
        .join("\n"),
    ]
      .filter((s) => s.trim())
      .join("\n\n");
    try {
      await navigator.clipboard.writeText(text);
      Toast.show("Copiada como texto");
    } catch {
      Toast.show("No se pudo copiar");
    }
  };

  return (
    <div class="page-top">
      <nav class="crumbs">
        {path.map((c) => (
          <Fragment key={c.id}>
            <button class="crumb" onClick={() => Nav.page(c.id)}>
              <PageIcon p={c} size={18} />
              <span>{shownTitle(c)}</span>
            </button>
            <ChevronRight size={14} class="dim2" />
          </Fragment>
        ))}
        <span class="crumb here">
          <PageIcon p={p} size={18} />
          <span>{shownTitle(p)}</span>
        </span>
      </nav>
      <span class="grow" />
      <span class="dim2 small edited">Editada {ago(p.updated)}</span>
      <button
        class={`icon-btn ${p.favorite ? "fav" : ""}`}
        title={p.favorite ? "Quitar de favoritos" : "A favoritos"}
        onClick={() => {
          Store.update({ ...p, favorite: !p.favorite }, false);
          if (!p.favorite) play("tick", 0.4);
        }}
      >
        <Star size={17} fill={p.favorite ? "currentColor" : "none"} />
      </button>
      <button class="icon-btn" title="Más" onClick={(e) => setMenu(e.currentTarget as HTMLElement)}>
        <MoreHorizontal size={18} />
      </button>
      {menu && (
        <Menu
          at={menu}
          onClose={() => setMenu(null)}
          items={[
            { label: "Ícono, color y forma", icon: Palette, onClick: () => setStyling(true) },
            p.kind === "DOC" ? { label: "Nueva subpágina", icon: FilePlus2, onClick: () => Nav.page(Store.newPage(p.id).id) } : null,
            { label: "Mover a…", icon: ArrowRightLeft, onClick: () => setMoving(true) },
            p.kind === "DOC" ? { label: "Copiar como texto", icon: ClipboardCopy, onClick: copyText } : null,
            null,
            {
              label: "A la papelera",
              icon: Trash2,
              danger: true,
              onClick: () => {
                Store.trash(p.id);
                Nav.back() || Nav.home();
                Toast.show(`«${shownTitle(p)}» se fue a la Papelera`, () => Store.restore(p.id));
              },
            },
          ].filter((x, i) => x !== null || i === 4)}
        />
      )}
      {styling && <StyleDialog p={p} onClose={() => setStyling(false)} />}
      {moving && <MoveDialog pages={[p]} ix={ix} onClose={() => setMoving(false)} />}
    </div>
  );
}

function ago(t: number): string {
  if (!t) return "";
  const m = Math.round((Date.now() - t) / 60_000);
  if (m < 1) return "ahora";
  if (m < 60) return `hace ${m} min`;
  const h = Math.round(m / 60);
  if (h < 24) return `hace ${h} h`;
  const d = Math.round(h / 24);
  return d === 1 ? "ayer" : `hace ${d} días`;
}

function PageHeader({ p }: { p: Page }) {
  const [title, setTitle] = useState(p.title);
  const [styling, setStyling] = useState(false);
  const focused = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => {
    if (!focused.current) setTitle(p.title);
  }, [p.id, p.title]);
  const save = (v: string) => {
    clearTimeout(timer.current);
    const now = Store.page(p.id);
    if (now && now.title !== v) Store.update({ ...now, title: v });
  };
  useEffect(() => () => clearTimeout(timer.current), [p.id]);
  return (
    <div class="page-header">
      <button class="big-icon" title="Ícono, color y forma" onClick={() => setStyling(true)}>
        <PageIcon p={p} size={64} />
      </button>
      <input
        class="page-title"
        value={title}
        placeholder="Sin título"
        ref={(el) => {
          // A page just made (blank or from a template): its title is ready to type over.
          if (el && Date.now() - p.created < 3000 && !el.dataset.focused) {
            el.dataset.focused = "1";
            el.focus({ preventScroll: true });
            el.select();
          }
        }}
        onFocus={() => (focused.current = true)}
        onBlur={() => {
          focused.current = false;
          save(title);
        }}
        onInput={(e) => {
          const v = e.currentTarget.value;
          setTitle(v);
          clearTimeout(timer.current);
          timer.current = setTimeout(() => save(v), 600);
        }}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === "ArrowDown") {
            e.preventDefault();
            save(title);
            (document.querySelector(".editor textarea") as HTMLElement | null)?.focus();
          }
        }}
      />
      {styling && <StyleDialog p={p} onClose={() => setStyling(false)} />}
    </div>
  );
}

export function StyleDialog({ p, onClose }: { p: Page; onClose: () => void }) {
  const [icon, setIcon] = useState(p.icon);
  const [color, setColor] = useState(p.color);
  const [shape, setShape] = useState(p.shape);
  const save = (o: Partial<Page>) => {
    const now = Store.page(p.id);
    if (now) Store.update({ ...now, ...o }, false);
  };
  return (
    <Modal title="Ícono, color y forma" onClose={onClose} wide footer={<button class="btn primary" onClick={onClose}>Listo</button>}>
      <div class="style-preview">
        <PageIcon p={{ icon, color, shape }} size={84} />
      </div>
      <SectionLabel>Color</SectionLabel>
      <div class="swatches">
        {PALETTE.map((c) => (
          <button
            key={c}
            class={`swatch ${c === color ? "on" : ""}`}
            style={{ background: css(c), clipPath: c === color ? clipFor(0) : undefined }}
            onClick={() => {
              setColor(c);
              save({ color: c });
            }}
          />
        ))}
      </div>
      <SectionLabel>Forma</SectionLabel>
      <div class="shape-grid">
        {SHAPES.map((_, i) => (
          <button
            key={i}
            title={SHAPE_NAMES[i]}
            class={`shape-tile ${i === shape ? "on" : ""}`}
            onClick={() => {
              setShape(i);
              save({ shape: i });
            }}
          >
            <span style={{ background: css(color), clipPath: clipFor(i) }} />
          </button>
        ))}
      </div>
      <SectionLabel>Ícono</SectionLabel>
      <div class="icon-grid">
        {ICONS.map(([key, I]) => (
          <button
            key={key}
            class={`icon-tile ${key === icon ? "on" : ""}`}
            onClick={() => {
              setIcon(key);
              save({ icon: key });
            }}
          >
            <I size={20} />
          </button>
        ))}
      </div>
    </Modal>
  );
}

/** Where to move pages: anywhere in the tree except inside themselves; into a collection they become cards. */
export function MoveDialog({ pages, ix, onClose }: { pages: Page[]; ix: Index; onClose: () => void }) {
  const [q, setQ] = useState("");
  const ids = new Set(pages.map((p) => p.id));
  const rows: { p: Page; depth: number }[] = [];
  const walk = (parent: string | null, depth: number) => {
    for (const c of ix.children(parent)) {
      if (c.id === INBOX_ID || ids.has(c.id)) continue;
      rows.push({ p: c, depth });
      if (c.kind === "DOC" && depth < 12) walk(c.id, depth + 1);
    }
  };
  walk(null, 0);
  const f = q.trim().toLowerCase();
  const shown = f ? rows.filter((r) => shownTitle(r.p).toLowerCase().includes(f)) : rows;
  const go = (parent: string | null) => {
    let n = 0;
    for (const p of pages) if (Store.move(p.id, parent)) n++;
    onClose();
    if (n) {
      play("pop", 0.4);
      const where = parent ? shownTitle(ix.page(parent)!) : "arriba del todo";
      Toast.show(n === 1 ? `Movida a ${where}` : `${n} movidas a ${where}`);
    }
  };
  return (
    <Modal title={pages.length === 1 ? `Mover «${shownTitle(pages[0])}»` : `Mover ${pages.length} páginas`} onClose={onClose}>
      <input class="field" ref={focusOnMount} placeholder="Buscar una página" value={q} onInput={(e) => setQ(e.currentTarget.value)} />
      <div class="move-list">
        {!f && (
          <button class="move-row" onClick={() => go(null)}>
            <span class="move-top">↑</span>
            <b>Arriba del todo</b>
          </button>
        )}
        {shown.map(({ p, depth }) => (
          <button key={p.id} class="move-row" style={{ paddingLeft: 10 + (f ? 0 : depth * 18) }} onClick={() => go(p.id)}>
            <PageIcon p={p} size={22} />
            <span>{shownTitle(p)}</span>
            {p.kind === "COLLECTION" && <span class="dim2 small">como tarjeta</span>}
          </button>
        ))}
      </div>
    </Modal>
  );
}

// ── Bandeja ───────────────────────────────────────────────────────────────────

function InboxView({ ix }: { ix: Index }) {
  const list = ix.inbox;
  const [moving, setMoving] = useState<Page[] | null>(null);
  return (
    <div class="page-view">
      <div class="page-top">
        <nav class="crumbs">
          <span class="crumb here">
            <Inbox size={16} />
            <span>Bandeja</span>
          </span>
        </nav>
      </div>
      <div class="page-scroll">
        <div class="page-inner">
          <h1 class="screen-title">Bandeja</h1>
          <p class="dim">Lo que anotas de prisa llega aquí. Cuando tengas un rato, acomódalo.</p>
          <CaptureCard
            placeholder="¿Qué traes en la cabeza?"
            onCapture={(text) => {
              if (Store.capture(text)) {
                play("pop");
                Hop.now();
              }
            }}
          />
          {list.length === 0 ? (
            <p class="dim2 empty">Vacía. Tu cabeza, ligera.</p>
          ) : (
            <>
              <SectionLabel
                right={
                  list.length > 1 && (
                    <button class="link" onClick={() => setMoving(list)}>
                      Mover todas
                    </button>
                  )
                }
              >
                {list.length === 1 ? "1 idea" : `${list.length} ideas`}
              </SectionLabel>
              <div class="inbox-list">
                {list.map((p) => (
                  <div class="inbox-row" key={p.id}>
                    <PageLink p={p} ix={ix} />
                    <button class="btn ghost small" onClick={() => setMoving([p])}>
                      <ArrowRightLeft size={14} /> Acomodar
                    </button>
                    <button
                      class="icon-btn"
                      title="A la papelera"
                      onClick={() => {
                        Store.trash(p.id);
                        Toast.show("A la Papelera", () => Store.restore(p.id));
                      }}
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      </div>
      {moving && <MoveDialog pages={moving} ix={ix} onClose={() => setMoving(null)} />}
    </div>
  );
}
