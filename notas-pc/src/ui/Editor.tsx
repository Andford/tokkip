// A page's text, line by line (blocks). Type as in any editor: Enter makes a
// new line that keeps going as a list or steps, Backspace at the start joins
// lines, "/" opens the menu of what a line can be (and the pages, boards and
// tables you can put inside), and Markdown-ish starts turn a line into
// something else ("# ", "- ", "[] ", "> ", "! ", "1. ", "---"). Lines can be
// dragged by their handle. Everything is saved a moment after you stop.

import { Fragment } from "preact";
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "preact/hooks";
import {
  AlignLeft,
  Brush,
  CheckSquare,
  ChevronRight,
  FileText,
  GripVertical,
  Heading1,
  Heading2,
  Kanban,
  List,
  ListOrdered,
  Minus,
  PenLine,
  Plus,
  Quote,
  Sparkles,
  Table2,
} from "lucide-preact";
import { type Block, type BlockKind, blockOf, blocksOf, bodyOf, isFixed, nextKind, numberAt, shortcutOf } from "../data/blocks";
import { type Index, type Page, type PageKind, css, fold, shownTitle } from "../data/model";
import { Store } from "../data/store";
import { play } from "../tokkip/sound";
import { PageIcon, Popover, SectionLabel } from "./kit";
import { DrawBlock } from "../ink/InkView";
import { drawingJson, emptyDrawing } from "../ink/ink";
import { Hop, Nav } from "./state";

interface EBlock extends Block {
  key: number;
}

let keySeq = 1;
const eb = (b: Block): EBlock => ({ ...b, key: keySeq++ });

interface SlashItem {
  id: string;
  label: string;
  hint: string;
  icon: typeof FileText;
  kind: BlockKind;
  sub?: PageKind;
  view?: string;
  words: string;
}

const SLASH: SlashItem[] = [
  { id: "page", label: "Subpágina", hint: "Una página dentro de esta", icon: FileText, kind: "PAGE", sub: "DOC", words: "subpagina pagina page" },
  { id: "board", label: "Tablero", hint: "Por hacer · Haciendo · Hecho", icon: Kanban, kind: "PAGE", sub: "COLLECTION", view: "board", words: "tablero kanban board tareas" },
  { id: "table", label: "Tabla", hint: "Filas con columnas", icon: Table2, kind: "PAGE", sub: "COLLECTION", view: "table", words: "tabla table base datos" },
  { id: "ink", label: "Página a mano", hint: "Para escribir con la pluma", icon: PenLine, kind: "PAGE", sub: "INK", words: "mano pluma escribir lapiz ink" },
  { id: "draw", label: "Dibujo", hint: "Un boceto o una firma aquí mismo", icon: Brush, kind: "DRAW", words: "dibujo boceto firma pluma draw sketch" },
  { id: "text", label: "Texto normal", hint: "", icon: AlignLeft, kind: "TEXT", words: "texto parrafo text" },
  { id: "h1", label: "Título grande", hint: "#", icon: Heading1, kind: "H1", words: "titulo encabezado heading h1" },
  { id: "h2", label: "Subtítulo", hint: "##", icon: Heading2, kind: "H2", words: "subtitulo heading h2" },
  { id: "bullet", label: "Lista con viñetas", hint: "-", icon: List, kind: "BULLET", words: "lista vinetas bullet" },
  { id: "number", label: "Lista numerada", hint: "1.", icon: ListOrdered, kind: "NUMBER", words: "lista numerada numero" },
  { id: "todo", label: "Paso para marcar", hint: "[]", icon: CheckSquare, kind: "TODO", words: "paso tarea todo check casilla" },
  { id: "quote", label: "Cita", hint: ">", icon: Quote, kind: "QUOTE", words: "cita quote" },
  { id: "callout", label: "Aviso con Tokkip", hint: "!", icon: Sparkles, kind: "CALLOUT", words: "aviso nota callout tokkip" },
  { id: "divider", label: "Separador", hint: "---", icon: Minus, kind: "DIVIDER", words: "separador linea divider" },
];

const inside = (it: SlashItem) => !!it.sub || it.kind === "DRAW";

function slashMatches(q: string): SlashItem[] {
  const f = fold(q.trim());
  if (!f) return SLASH;
  return SLASH.filter((s) => fold(s.label).includes(f) || s.words.includes(f));
}

function autosize(el: HTMLTextAreaElement | null) {
  if (!el) return;
  el.style.height = "0px";
  el.style.height = `${el.scrollHeight}px`;
}

export function DocEditor({ p, ix }: { p: Page; ix: Index }) {
  const id = p.id;
  const [blocks, setBlocks] = useState<EBlock[] | null>(null);
  const list = useRef<EBlock[] | null>(null);
  const dirty = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  const areas = useRef(new Map<number, HTMLTextAreaElement>());
  const focusTo = useRef<{ key: number; pos: number | "end" } | null>(null);
  const [slash, setSlash] = useState<{ key: number; index: number } | null>(null);
  const [drag, setDrag] = useState<{ from: number; over: number | null } | null>(null);
  const accent = css(p.color);

  // Load the body when the page changes; save the one we leave.
  useEffect(() => {
    let alive = true;
    setBlocks(null);
    list.current = null;
    Store.body(id).then((body) => {
      if (!alive) return;
      const bs = blocksOf(body).map(eb);
      list.current = bs;
      setBlocks(bs);
    });
    return () => {
      alive = false;
      save();
    };
  }, [id]);

  // The page changed on another device: show it, unless you're in the middle of writing here.
  useEffect(
    () =>
      Store.onRemote((ids) => {
        if (!ids.has(id) || dirty.current) return;
        Store.body(id).then((body) => {
          if (dirty.current || !list.current) return;
          if (bodyOf(list.current) === body) return;
          const bs = blocksOf(body).map(eb);
          list.current = bs;
          setBlocks(bs);
        });
      }),
    [id],
  );

  // Save on the way out of the app too.
  useEffect(() => {
    const out = () => save();
    window.addEventListener("beforeunload", out);
    window.addEventListener("blur", out);
    return () => {
      window.removeEventListener("beforeunload", out);
      window.removeEventListener("blur", out);
    };
  }, [id]);

  function save() {
    clearTimeout(timer.current);
    if (!dirty.current || !list.current) return;
    dirty.current = false;
    const now = Store.page(id);
    if (now) Store.saveBody(id, now.title, bodyOf(list.current));
  }

  const commit = useCallback((next: EBlock[], focus?: { key: number; pos: number | "end" }) => {
    list.current = next;
    dirty.current = true;
    if (focus) focusTo.current = focus;
    setBlocks(next);
    clearTimeout(timer.current);
    timer.current = setTimeout(save, 700);
  }, []);

  useLayoutEffect(() => {
    for (const el of areas.current.values()) autosize(el);
    const f = focusTo.current;
    if (!f) return;
    focusTo.current = null;
    const el = areas.current.get(f.key);
    if (el) {
      el.focus();
      const pos = f.pos === "end" ? el.value.length : Math.min(f.pos, el.value.length);
      el.setSelectionRange(pos, pos);
    }
  }, [blocks]);

  // Lines rewrap when the window changes width.
  useEffect(() => {
    const ro = new ResizeObserver(() => areas.current.forEach(autosize));
    ro.observe(document.body);
    return () => ro.disconnect();
  }, []);

  if (!blocks) return <div class="editor loading" />;

  const kinds = blocks.map((b) => b.kind);
  const at = (key: number) => blocks.findIndex((b) => b.key === key);
  const replace = (i: number, b: Partial<EBlock>) => blocks.map((x, j) => (j === i ? { ...x, ...b } : x));

  function textTargets(from: number, dir: 1 | -1): number {
    for (let j = from; j >= 0 && j < blocks!.length; j += dir) if (!isFixed(blocks![j].kind)) return j;
    return -1;
  }

  function onInput(b: EBlock, el: HTMLTextAreaElement) {
    const i = at(b.key);
    if (i < 0) return;
    let text = el.value;
    // A paste with several lines becomes several blocks.
    if (text.includes("\n")) {
      const caret = el.selectionStart;
      const parts = text.split("\n");
      const first = parts[0];
      const added = parts.slice(1).map((part) => {
        const parsed = blockOf(part);
        return eb(parsed.kind === "PAGE" ? { kind: "TEXT", text: part } : parsed.kind !== "TEXT" ? parsed : { kind: nextKind(b.kind), text: parsed.text });
      });
      const next = [...blocks!];
      next.splice(i, 1, { ...b, text: first }, ...added);
      const last = added[added.length - 1];
      commit(next, { key: last.key, pos: Math.max(0, last.text.length - (text.length - caret)) });
      return;
    }
    if (slash && slash.key === b.key) {
      if (!text.startsWith("/")) setSlash(null);
      else setSlash({ ...slash, index: 0 });
    } else if (text === "/" && !slash) {
      setSlash({ key: b.key, index: 0 });
    }
    if (b.kind === "TEXT") {
      const sc = shortcutOf(text);
      if (sc) {
        const [kind, cut] = sc;
        text = text.slice(cut);
        if (kind === "DIVIDER") {
          const n = eb({ kind: "TEXT", text: "" });
          const next = replace(i, { kind, text: "" });
          next.splice(i + 1, 0, n);
          commit(next, { key: n.key, pos: 0 });
        } else commit(replace(i, { kind, text }), { key: b.key, pos: 0 });
        return;
      }
    }
    commit(replace(i, { text }));
  }

  function pick(item: SlashItem, key: number) {
    setSlash(null);
    const i = at(key);
    if (i < 0) return;
    const b = blocks![i];
    if (item.sub) {
      const child = Store.newPage(id, { kind: item.sub, view: item.view, title: item.sub === "COLLECTION" ? (item.view === "table" ? "Tabla" : "Tablero") : "", body: item.sub === "INK" ? drawingJson(emptyDrawing("lines")) : "" });
      const next = replace(i, { kind: "PAGE", text: child.id });
      if (i === next.length - 1) next.push(eb({ kind: "TEXT", text: "" }));
      commit(next);
      save();
      play("pop");
      Nav.page(child.id);
      return;
    }
    if (item.kind === "DRAW") {
      const n = eb({ kind: "TEXT", text: "" });
      const next = replace(i, { kind: "DRAW", text: drawingJson(emptyDrawing("plain", 450)) });
      if (i === next.length - 1 || next[i + 1].kind !== "TEXT") next.splice(i + 1, 0, n);
      commit(next);
      play("pop", 0.4);
      return;
    }
    if (item.kind === "DIVIDER") {
      const n = eb({ kind: "TEXT", text: "" });
      const next = replace(i, { kind: "DIVIDER", text: "" });
      next.splice(i + 1, 0, n);
      commit(next, { key: n.key, pos: 0 });
      return;
    }
    const text = b.text.startsWith("/") ? "" : b.text;
    commit(replace(i, { kind: item.kind, text }), { key, pos: "end" });
  }

  function onKeyDown(e: KeyboardEvent, b: EBlock) {
    const el = e.currentTarget as HTMLTextAreaElement;
    const i = at(b.key);
    if (slash && slash.key === b.key) {
      const items = slashMatches(b.text.slice(1));
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        e.preventDefault();
        const n = items.length || 1;
        setSlash({ ...slash, index: (slash.index + (e.key === "ArrowDown" ? 1 : n - 1)) % n });
        return;
      }
      if (e.key === "Enter") {
        e.preventDefault();
        const it = items[slash.index];
        if (it) pick(it, b.key);
        else setSlash(null);
        return;
      }
      if (e.key === "Escape") {
        e.preventDefault();
        setSlash(null);
        return;
      }
    }
    const start = el.selectionStart;
    const end = el.selectionEnd;
    if (e.key === "Enter" && (e.ctrlKey || e.metaKey) && (b.kind === "TODO" || b.kind === "DONE")) {
      e.preventDefault();
      toggle(b);
      return;
    }
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      if (!b.text && b.kind !== "TEXT") {
        // Enter on an empty list line: back to plain text.
        commit(replace(i, { kind: "TEXT" }), { key: b.key, pos: 0 });
        return;
      }
      const before = b.text.slice(0, start);
      const after = b.text.slice(end);
      // Enter at the very start of a heading/list keeps it and opens a plain line above.
      if (start === 0 && end === 0 && b.text) {
        const n = eb({ kind: b.kind === "H1" || b.kind === "H2" ? "TEXT" : nextKind(b.kind), text: "" });
        const next = [...blocks!];
        next.splice(i, 0, n);
        commit(next, { key: b.key, pos: 0 });
        return;
      }
      const n = eb({ kind: nextKind(b.kind), text: after });
      const next = replace(i, { text: before });
      next.splice(i + 1, 0, n);
      commit(next, { key: n.key, pos: 0 });
      return;
    }
    if (e.key === "Backspace" && start === 0 && end === 0) {
      if (b.kind !== "TEXT") {
        e.preventDefault();
        commit(replace(i, { kind: "TEXT" }), { key: b.key, pos: 0 });
        return;
      }
      if (i === 0) return;
      e.preventDefault();
      const prev = blocks![i - 1];
      const next = [...blocks!];
      if (prev.kind === "DIVIDER") {
        next.splice(i - 1, 1);
        commit(next, { key: b.key, pos: 0 });
      } else if (prev.kind === "PAGE" || prev.kind === "DRAW") {
        if (!b.text) {
          next.splice(i, 1);
          const j = textTargets(i - 1, -1);
          commit(next, j >= 0 ? { key: blocks![j].key, pos: "end" } : undefined);
        }
      } else {
        const join = prev.text.length;
        next.splice(i - 1, 2, { ...prev, text: prev.text + b.text });
        commit(next, { key: prev.key, pos: join });
      }
      return;
    }
    if (e.key === "Delete" && start === b.text.length && end === start && i < blocks!.length - 1) {
      const nb = blocks![i + 1];
      if (isFixed(nb.kind)) return;
      e.preventDefault();
      const next = [...blocks!];
      next.splice(i, 2, { ...b, text: b.text + nb.text });
      commit(next, { key: b.key, pos: start });
      return;
    }
    if (e.key === "ArrowUp" && !e.shiftKey) {
      const lineH = parseFloat(getComputedStyle(el).lineHeight) || 24;
      const caretTop = start === 0 || el.scrollHeight <= lineH * 1.6;
      if (caretTop) {
        const j = textTargets(i - 1, -1);
        if (j >= 0) {
          e.preventDefault();
          focusTo.current = { key: blocks![j].key, pos: "end" };
          setBlocks([...blocks!]);
        } else if (i === 0) {
          e.preventDefault();
          (document.querySelector(".page-title") as HTMLElement | null)?.focus();
        }
      }
      return;
    }
    if (e.key === "ArrowDown" && !e.shiftKey) {
      const lineH = parseFloat(getComputedStyle(el).lineHeight) || 24;
      if (start === el.value.length || el.scrollHeight <= lineH * 1.6) {
        const j = textTargets(i + 1, 1);
        if (j >= 0) {
          e.preventDefault();
          focusTo.current = { key: blocks![j].key, pos: Math.min(start, blocks![j].text.length) };
          setBlocks([...blocks!]);
        }
      }
    }
  }

  function toggle(b: EBlock) {
    const i = at(b.key);
    const checking = b.kind === "TODO";
    commit(replace(i, { kind: checking ? "DONE" : "TODO" }));
    if (checking) {
      play("task_done", 0.5);
      Hop.now();
    } else play("tick", 0.4);
  }

  function addAfter(i: number) {
    const n = eb({ kind: "TEXT", text: "" });
    const next = [...blocks!];
    next.splice(i + 1, 0, n);
    commit(next, { key: n.key, pos: 0 });
    setSlash({ key: n.key, index: 0 });
  }

  function clickBelow() {
    const last = blocks![blocks!.length - 1];
    if (!last || isFixed(last.kind) || last.text || last.kind !== "TEXT") {
      const n = eb({ kind: "TEXT", text: "" });
      commit([...blocks!, n], { key: n.key, pos: 0 });
    } else {
      focusTo.current = { key: last.key, pos: 0 };
      setBlocks([...blocks!]);
    }
  }

  function drop(to: number) {
    if (!drag) return;
    const from = drag.from;
    setDrag(null);
    if (from === to || from + 1 === to) return;
    const next = [...blocks!];
    const [moved] = next.splice(from, 1);
    next.splice(to > from ? to - 1 : to, 0, moved);
    commit(next);
  }

  const linked = new Set(blocks.filter((b) => b.kind === "PAGE").map((b) => b.text));
  const loose = ix.children(id).filter((c) => !linked.has(c.id));
  const slashBlock = slash ? blocks.find((b) => b.key === slash.key) : undefined;
  const slashEl = slash ? areas.current.get(slash.key) : undefined;

  return (
    <div class="editor" style={{ "--page": accent }}>
      {blocks.map((b, i) => (
        <div
          key={b.key}
          class={`block k-${b.kind.toLowerCase()} ${drag?.over === i ? "drop-before" : ""} ${blocks.length === 1 && !b.text ? "lonely" : ""}`}
          onDragOver={(e) => {
            if (!drag) return;
            e.preventDefault();
            const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
            const over = e.clientY > r.top + r.height / 2 ? i + 1 : i;
            if (over !== drag.over) setDrag({ ...drag, over });
          }}
          onDrop={(e) => {
            e.preventDefault();
            if (drag?.over != null) drop(drag.over);
          }}
        >
          <div class="block-gutter">
            <button class="gutter-btn" title="Insertar debajo" tabIndex={-1} onClick={() => addAfter(i)}>
              <Plus size={15} />
            </button>
            <span
              class="gutter-btn grip"
              title="Arrastra para mover"
              draggable
              onDragStart={(e) => {
                e.dataTransfer?.setData("text/plain", b.text);
                if (e.dataTransfer) e.dataTransfer.effectAllowed = "move";
                setDrag({ from: i, over: null });
              }}
              onDragEnd={() => setDrag(null)}
            >
              <GripVertical size={15} />
            </span>
          </div>
          <BlockBody
            b={b}
            ix={ix}
            number={b.kind === "NUMBER" ? numberAt(kinds, i) : 0}
            areaRef={(el) => {
              if (el) areas.current.set(b.key, el);
              else areas.current.delete(b.key);
            }}
            onInput={(el) => onInput(b, el)}
            onKeyDown={(e) => onKeyDown(e, b)}
            onToggle={() => toggle(b)}
            onRemove={() => {
              const next = blocks.filter((x) => x.key !== b.key);
              commit(next.length ? next : [eb({ kind: "TEXT", text: "" })]);
            }}
            onDraw={(json) => commit((list.current ?? blocks).map((x) => (x.key === b.key ? { ...x, text: json } : x)))}
            placeholder={i === 0 && blocks.length === 1 ? "Escribe, o «/» para escoger qué va aquí…" : undefined}
          />
        </div>
      ))}
      {drag?.over === blocks.length && <div class="drop-end" />}
      <div
        class="below"
        onClick={clickBelow}
        onDragOver={(e) => {
          if (!drag) return;
          e.preventDefault();
          if (drag.over !== blocks.length) setDrag({ ...drag, over: blocks.length });
        }}
        onDrop={(e) => {
          e.preventDefault();
          drop(blocks.length);
        }}
      />
      {loose.length > 0 && (
        <div class="loose">
          <SectionLabel>Dentro de esta página</SectionLabel>
          {loose.map((c) => (
            <PageLink key={c.id} p={c} ix={ix} />
          ))}
        </div>
      )}
      {slash && slashBlock && slashEl && (
        <SlashMenu
          anchor={slashEl}
          query={slashBlock.text.startsWith("/") ? slashBlock.text.slice(1) : ""}
          index={slash.index}
          onHover={(index) => setSlash({ ...slash, index })}
          onPick={(it) => pick(it, slash.key)}
          onClose={() => setSlash(null)}
        />
      )}
    </div>
  );
}

function BlockBody({
  b,
  ix,
  number,
  areaRef,
  onInput,
  onKeyDown,
  onToggle,
  onRemove,
  onDraw,
  placeholder,
}: {
  b: EBlock;
  ix: Index;
  number: number;
  areaRef: (el: HTMLTextAreaElement | null) => void;
  onInput: (el: HTMLTextAreaElement) => void;
  onKeyDown: (e: KeyboardEvent) => void;
  onToggle: () => void;
  onRemove: () => void;
  onDraw: (json: string) => void;
  placeholder?: string;
}) {
  if (b.kind === "DIVIDER") return <hr class="divider" />;
  if (b.kind === "DRAW") return <DrawBlock text={b.text} onChange={onDraw} onRemove={onRemove} />;
  if (b.kind === "PAGE") {
    const child = ix.page(b.text);
    if (!child || child.trashed) {
      return (
        <div class="page-link gone">
          <span>{child ? "Página en la Papelera" : "Esta página ya no está"}</span>
          <button class="link" onClick={onRemove}>
            Quitar
          </button>
        </div>
      );
    }
    return <PageLink p={child} ix={ix} />;
  }
  const ph =
    placeholder ??
    ({ H1: "Título", H2: "Subtítulo", TODO: "Un paso", DONE: "Un paso", BULLET: "Algo", NUMBER: "Algo", QUOTE: "Una cita", CALLOUT: "Algo que Tokkip te recuerde" } as Record<string, string>)[b.kind];
  return (
    <>
      {b.kind === "BULLET" && <span class="marker bullet">•</span>}
      {b.kind === "NUMBER" && <span class="marker number">{number}.</span>}
      {(b.kind === "TODO" || b.kind === "DONE") && (
        <button class={`step ${b.kind === "DONE" ? "done" : ""}`} onClick={onToggle} title={b.kind === "DONE" ? "Desmarcar" : "Marcar como hecho"} tabIndex={-1}>
          {b.kind === "DONE" && (
            <svg viewBox="0 0 24 24" width="14" height="14">
              <path d="M5 12.5l4.2 4.2L19 7" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          )}
        </button>
      )}
      {b.kind === "CALLOUT" && <Sparkles class="marker callout" size={18} />}
      <textarea
        ref={areaRef}
        class="line"
        rows={1}
        spellcheck
        value={b.text}
        placeholder={ph}
        onInput={(e) => onInput(e.currentTarget)}
        onKeyDown={onKeyDown}
      />
    </>
  );
}

export function PageLink({ p, ix }: { p: Page; ix: Index }) {
  const kids = ix.children(p.id).length;
  return (
    <button class="page-link" onClick={() => Nav.page(p.id)}>
      <PageIcon p={p} size={26} />
      <span class="page-link-title">{shownTitle(p)}</span>
      {p.kind === "COLLECTION" ? (
        <span class="dim small">{kids === 1 ? "1 tarjeta" : `${kids} tarjetas`}</span>
      ) : p.steps > 0 ? (
        <span class="dim small">
          {p.done}/{p.steps}
        </span>
      ) : kids > 0 ? (
        <span class="dim small">{kids}</span>
      ) : null}
      <ChevronRight size={16} class="dim" />
    </button>
  );
}

function SlashMenu({
  anchor,
  query,
  index,
  onHover,
  onPick,
  onClose,
}: {
  anchor: HTMLElement;
  query: string;
  index: number;
  onHover: (i: number) => void;
  onPick: (it: SlashItem) => void;
  onClose: () => void;
}) {
  const items = slashMatches(query);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    ref.current?.querySelector(".slash-item.on")?.scrollIntoView({ block: "nearest" });
  }, [index]);
  return (
    <Popover at={anchor} onClose={onClose} width={300} cls="slash">
      <div ref={ref} class="slash-list">
        {!items.length && <div class="dim small pad">Nada con «{query}»</div>}
        {items.map((it, i) => (
          <Fragment key={it.id}>
            {(i === 0 || inside(items[i - 1]) !== inside(it)) && <div class="slash-head">{inside(it) ? "Dentro de esta página" : "Esta línea es…"}</div>}
            <button
              key={it.id}
              class={`slash-item ${i === index ? "on" : ""}`}
              onMouseEnter={() => onHover(i)}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => onPick(it)}
            >
              <span class="slash-icon">
                <it.icon size={17} />
              </span>
              <span class="slash-text">
                <b>{it.label}</b>
                {it.hint && <small>{it.hint}</small>}
              </span>
            </button>
          </Fragment>
        ))}
      </div>
    </Popover>
  );
}
