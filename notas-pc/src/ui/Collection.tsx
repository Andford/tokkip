// A collection: its cards seen as a board (columns by status, drag a card to
// move it) or as a table (columns you add, values you edit right in the cell,
// sort by any column). A card is a page: click it to open its notes, and its
// values show at the top of that page (PropsPanel).

import { Fragment } from "preact";
import { useEffect, useRef, useState } from "preact/hooks";
import {
  ArrowDownAZ,
  ArrowUpZA,
  Calendar,
  CheckSquare,
  CircleDot,
  Hash,
  Kanban,
  MoreHorizontal,
  Palette,
  Pencil,
  Plus,
  SquareArrowOutUpRight,
  Table2,
  Tags,
  Trash2,
  Type,
} from "lucide-preact";
import {
  type Index,
  type Lane,
  type Opt,
  type Page,
  type Prop,
  type PropType,
  type Schema,
  PALETTE,
  PROP_LABEL,
  board,
  css,
  dayKey,
  display,
  groupOf,
  isDone,
  moveCard,
  multiOf,
  newId,
  rows,
  shortDate,
  shownTitle,
} from "../data/model";
import { Store } from "../data/store";
import { play } from "../tokkip/sound";
import { Confirm, Menu, type MenuItem, Modal, NameDialog, Popover, focusOnMount } from "./kit";
import { Hop, Nav, Toast } from "./state";

const TYPE_ICON: Record<PropType, typeof Type> = { TEXT: Type, NUMBER: Hash, SELECT: CircleDot, MULTI: Tags, DATE: Calendar, CHECK: CheckSquare };

function setSchema(c: Page, s: Schema) {
  const now = Store.page(c.id);
  if (now) Store.update({ ...now, schema: s }, false);
}

/** Puts a value on a card; cheers when the card lands on "done". */
function putValue(cardId: string, collection: Page, prop: Prop, value: string | null) {
  const card = Store.page(cardId);
  if (!card) return;
  const props = { ...card.props };
  if (value == null || value === "") delete props[prop.id];
  else props[prop.id] = value;
  Store.update({ ...card, props });
  const s = collection.schema;
  if (s && prop.type === "SELECT" && groupOf(s)?.id === prop.id && isDone(s, value ?? undefined) && !isDone(s, card.props[prop.id])) cheer();
}

function cheer() {
  play("task_done", 0.55);
  Hop.now();
  Toast.show("¡Uno menos! 🎉");
}

function nextColor(opts: Opt[]): number {
  return PALETTE[opts.length % PALETTE.length];
}

export function CollectionView({ c, ix }: { c: Page; ix: Index }) {
  const s = c.schema;
  if (!s) return null;
  const view = s.view === "table" ? "table" : "board";
  const selects = s.props.filter((p) => p.type === "SELECT");
  const g = groupOf(s);
  return (
    <div class="collection">
      <div class="coll-bar">
        <div class="seg">
          <button class={view === "board" ? "on" : ""} onClick={() => setSchema(c, { ...s, view: "board" })}>
            <Kanban size={15} /> Tablero
          </button>
          <button class={view === "table" ? "on" : ""} onClick={() => setSchema(c, { ...s, view: "table" })}>
            <Table2 size={15} /> Tabla
          </button>
        </div>
        {view === "board" && selects.length > 1 && g && (
          <label class="dim small group-by">
            Agrupar por
            <select value={g.id} onChange={(e) => setSchema(c, { ...s, groupBy: e.currentTarget.value })}>
              {selects.map((p) => (
                <option value={p.id}>{p.name}</option>
              ))}
            </select>
          </label>
        )}
        <span class="grow" />
        <span class="dim small">{ix.children(c.id).length} tarjetas</span>
        <button
          class="btn primary small"
          onClick={() => {
            const lane = view === "board" ? board(ix, c).find((l) => l.opt) : undefined;
            const card = Store.newPage(c.id, { props: lane?.opt && g ? { [g.id]: lane.opt.id } : {} });
            Nav.page(card.id);
          }}
        >
          <Plus size={15} /> Nueva tarjeta
        </button>
      </div>
      {view === "board" ? <Board c={c} s={s} ix={ix} /> : <Table c={c} s={s} ix={ix} />}
    </div>
  );
}

// ── Board ─────────────────────────────────────────────────────────────────────

function Board({ c, s, ix }: { c: Page; s: Schema; ix: Index }) {
  const lanes = board(ix, c);
  const g = groupOf(s);
  const [drag, setDrag] = useState<{ id: string; lane: number; at: number } | null>(null);
  const [optMenu, setOptMenu] = useState<{ opt: Opt; el: HTMLElement } | null>(null);
  const [rename, setRename] = useState<Opt | null>(null);
  const [colour, setColour] = useState<{ opt: Opt; el: HTMLElement } | null>(null);
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<Opt | null>(null);

  function drop() {
    if (!drag || !g) return setDrag(null);
    const fresh = board(Store.index, Store.page(c.id) ?? c);
    const lane = fresh[drag.lane];
    const card = Store.page(drag.id);
    setDrag(null);
    if (!lane || !card) return;
    const moved = moveCard(card, g, lane, drag.at);
    const wasDone = isDone(s, card.props[g.id]);
    Store.update(moved);
    if (!wasDone && isDone(s, moved.props[g.id])) cheer();
    else play("tick", 0.35);
  }

  function updateGroup(next: Prop) {
    setSchema(c, { ...s, props: s.props.map((p) => (p.id === next.id ? next : p)) });
  }

  return (
    <div class="board">
      {lanes.map((lane, li) => (
        <div
          key={lane.opt?.id ?? "none"}
          class={`lane ${drag?.lane === li ? "over" : ""}`}
          onDragOver={(e) => {
            if (!drag) return;
            e.preventDefault();
            const cards = [...(e.currentTarget as HTMLElement).querySelectorAll<HTMLElement>(".card")];
            let at = cards.length;
            for (let k = 0; k < cards.length; k++) {
              const r = cards[k].getBoundingClientRect();
              if (e.clientY < r.top + r.height / 2) {
                at = k;
                break;
              }
            }
            const others = lane.cards.filter((x) => x.id !== drag.id);
            const idx = lane.cards.slice(0, at).filter((x) => x.id !== drag.id).length;
            const clamped = Math.min(idx, others.length);
            if (drag.lane !== li || drag.at !== clamped) setDrag({ ...drag, lane: li, at: clamped });
          }}
          onDrop={(e) => {
            e.preventDefault();
            drop();
          }}
        >
          <div class="lane-head">
            <span class="lane-dot" style={{ background: lane.opt ? css(lane.opt.color) : "var(--dim2)" }} />
            <b>{lane.opt?.name ?? "Sin estado"}</b>
            <span class="dim">{lane.cards.length}</span>
            <span class="grow" />
            {lane.opt && (
              <button class="icon-btn" title="Opciones de la columna" onClick={(e) => setOptMenu({ opt: lane.opt!, el: e.currentTarget as HTMLElement })}>
                <MoreHorizontal size={16} />
              </button>
            )}
          </div>
          <div class="lane-cards">
            {lane.cards.map((card, k) => {
              const dragging = drag?.id === card.id;
              const showGap = drag && drag.lane === li && !dragging && lane.cards.filter((x) => x.id !== drag.id).indexOf(card) === drag.at;
              return (
                <Fragment key={card.id}>
                  {showGap && <div class="card-gap" />}
                  <CardView
                    key={card.id}
                    card={card}
                    s={s}
                    dragging={dragging}
                    onDragStart={() => setDrag({ id: card.id, lane: li, at: k })}
                    onDragEnd={() => setDrag(null)}
                  />
                </Fragment>
              );
            })}
            {drag && drag.lane === li && drag.at >= lane.cards.filter((x) => x.id !== drag.id).length && <div class="card-gap" />}
          </div>
          <QuickAdd
            onAdd={(title) => {
              Store.newPage(c.id, { title, props: lane.opt && g ? { [g.id]: lane.opt.id } : {} });
              play("pop", 0.4);
            }}
          />
        </div>
      ))}
      {g && (
        <button class="lane add-lane" onClick={() => setAdding(true)}>
          <Plus size={16} /> Columna
        </button>
      )}
      {optMenu && g && (
        <Menu
          at={optMenu.el}
          onClose={() => setOptMenu(null)}
          items={[
            { label: "Cambiar nombre", icon: Pencil, onClick: () => setRename(optMenu.opt) },
            { label: "Color", icon: Palette, onClick: () => setColour({ opt: optMenu.opt, el: optMenu.el }) },
            null,
            { label: "Quitar columna", icon: Trash2, danger: true, onClick: () => setRemoving(optMenu.opt) },
          ]}
        />
      )}
      {colour && g && (
        <Popover at={colour.el} onClose={() => setColour(null)} width={220}>
          <div class="swatches pad">
            {PALETTE.map((col) => (
              <button
                key={col}
                class={`swatch ${col === colour.opt.color ? "on" : ""}`}
                style={{ background: css(col) }}
                onClick={() => {
                  updateGroup({ ...g, options: g.options.map((o) => (o.id === colour.opt.id ? { ...o, color: col } : o)) });
                  setColour(null);
                }}
              />
            ))}
          </div>
        </Popover>
      )}
      {rename && g && (
        <NameDialog
          title="Nombre de la columna"
          initial={rename.name}
          onDone={(name) => {
            if (name) updateGroup({ ...g, options: g.options.map((o) => (o.id === rename.id ? { ...o, name } : o)) });
            setRename(null);
          }}
        />
      )}
      {adding && g && (
        <NameDialog
          title="Nueva columna"
          placeholder="Ej. En revisión"
          onDone={(name) => {
            if (name) {
              // New columns go before the last one ("Hecho" stays the finish line).
              const opts = [...g.options];
              const o = { id: newId(), name, color: nextColor(opts) };
              opts.splice(Math.max(0, opts.length - 1), 0, o);
              updateGroup({ ...g, options: opts });
            }
            setAdding(false);
          }}
        />
      )}
      {removing && g && (
        <Confirm
          title={`¿Quitar «${removing.name}»?`}
          text="Sus tarjetas pasan a «Sin estado»; no se borra ninguna."
          ok="Quitar"
          danger
          onClose={() => setRemoving(null)}
          onOk={() => updateGroup({ ...g, options: g.options.filter((o) => o.id !== removing.id) })}
        />
      )}
    </div>
  );
}

function CardView({ card, s, dragging, onDragStart, onDragEnd }: { card: Page; s: Schema; dragging: boolean; onDragStart: () => void; onDragEnd: () => void }) {
  const g = groupOf(s);
  const chips: { text: string; color?: number; late?: boolean }[] = [];
  const today = dayKey(Date.now());
  for (const p of s.props) {
    if (p.id === g?.id) continue;
    const raw = card.props[p.id];
    if (!raw) continue;
    if (p.type === "MULTI") for (const id of multiOf(raw)) {
      const o = p.options.find((x) => x.id === id);
      if (o) chips.push({ text: o.name, color: o.color });
    }
    else if (p.type === "SELECT") {
      const o = p.options.find((x) => x.id === raw);
      if (o) chips.push({ text: o.name, color: o.color });
    } else if (p.type === "DATE") chips.push({ text: shortDate(raw), late: raw < today && !isDone(s, g ? card.props[g.id] : undefined) });
    else if (p.type === "CHECK") {
      if (raw === "1") chips.push({ text: `✓ ${p.name}` });
    } else chips.push({ text: display(p, raw) });
  }
  return (
    <div
      class={`card ${dragging ? "dragging" : ""}`}
      draggable
      onDragStart={(e) => {
        e.dataTransfer?.setData("text/plain", shownTitle(card));
        if (e.dataTransfer) e.dataTransfer.effectAllowed = "move";
        onDragStart();
      }}
      onDragEnd={onDragEnd}
      onClick={() => Nav.page(card.id)}
    >
      <div class={`card-title ${!card.title.trim() ? "dim" : ""}`}>{shownTitle(card)}</div>
      {card.preview && card.title.trim() && <div class="card-preview">{card.preview}</div>}
      {(chips.length > 0 || card.steps > 0) && (
        <div class="chips">
          {card.steps > 0 && (
            <span class="chip">
              {card.done}/{card.steps} pasos
            </span>
          )}
          {chips.map((ch) => (
            <span class={`chip ${ch.late ? "late" : ""}`} style={ch.color ? { background: css(ch.color) + "33", color: "var(--text)" } : undefined}>
              {ch.color && <i style={{ background: css(ch.color) }} />}
              {ch.text}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

function QuickAdd({ onAdd }: { onAdd: (title: string) => void }) {
  const [text, setText] = useState("");
  return (
    <div class="quick-add">
      <Plus size={15} />
      <input
        value={text}
        placeholder="Nueva tarjeta"
        onInput={(e) => setText(e.currentTarget.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter" && text.trim()) {
            onAdd(text.trim());
            setText("");
          }
        }}
      />
    </div>
  );
}

// ── Table ─────────────────────────────────────────────────────────────────────

function Table({ c, s, ix }: { c: Page; s: Schema; ix: Index }) {
  const list = rows(ix, c);
  const [head, setHead] = useState<{ id: string; el: HTMLElement } | null>(null);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<Prop | null>(null);
  const [removing, setRemoving] = useState<Prop | null>(null);
  const sortIcon = (id: string) => (s.sortBy === id ? (s.sortDesc ? <ArrowUpZA size={13} /> : <ArrowDownAZ size={13} />) : null);

  const headItems = (id: string): (MenuItem | null)[] => {
    const prop = s.props.find((p) => p.id === id);
    const items: (MenuItem | null)[] = [
      { label: "Ordenar A → Z", icon: ArrowDownAZ, onClick: () => setSchema(c, { ...s, sortBy: id, sortDesc: false }) },
      { label: "Ordenar Z → A", icon: ArrowUpZA, onClick: () => setSchema(c, { ...s, sortBy: id, sortDesc: true }) },
    ];
    if (s.sortBy) items.push({ label: "Sin ordenar", onClick: () => setSchema(c, { ...s, sortBy: "", sortDesc: false }) });
    if (prop) {
      items.push(null, { label: "Editar columna", icon: Pencil, onClick: () => setEditing(prop) }, { label: "Quitar columna", icon: Trash2, danger: true, onClick: () => setRemoving(prop) });
    }
    return items;
  };

  return (
    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th class="title-col">
              <button onClick={(e) => setHead({ id: "title", el: e.currentTarget as HTMLElement })}>
                <Type size={13} /> Título {sortIcon("title")}
              </button>
            </th>
            {s.props.map((p) => {
              const I = TYPE_ICON[p.type];
              return (
                <th key={p.id}>
                  <button onClick={(e) => setHead({ id: p.id, el: e.currentTarget as HTMLElement })}>
                    <I size={13} /> {p.name} {sortIcon(p.id)}
                  </button>
                </th>
              );
            })}
            <th class="add-col">
              <button title="Agregar columna" onClick={() => setAdding(true)}>
                <Plus size={15} />
              </button>
            </th>
          </tr>
        </thead>
        <tbody>
          {list.map((r) => (
            <tr key={r.id}>
              <td class="title-col">
                <div class="title-cell">
                  <TitleInput card={r} />
                  <button class="icon-btn open" title="Abrir como página" onClick={() => Nav.page(r.id)}>
                    <SquareArrowOutUpRight size={14} />
                  </button>
                </div>
              </td>
              {s.props.map((p) => (
                <td key={p.id} class={`cell t-${p.type.toLowerCase()}`}>
                  <Value card={r} collection={c} prop={p} />
                </td>
              ))}
              <td />
            </tr>
          ))}
          <tr class="new-row">
            <td colSpan={s.props.length + 2}>
              <QuickAdd onAdd={(title) => Store.newPage(c.id, { title })} />
            </td>
          </tr>
        </tbody>
      </table>
      {head && <Menu at={head.el} items={headItems(head.id)} onClose={() => setHead(null)} />}
      {adding && <PropDialog c={c} s={s} onClose={() => setAdding(false)} />}
      {editing && <PropDialog c={c} s={s} prop={editing} onClose={() => setEditing(null)} />}
      {removing && (
        <Confirm
          title={`¿Quitar «${removing.name}»?`}
          text="Sus valores se borran de todas las tarjetas."
          ok="Quitar"
          danger
          onClose={() => setRemoving(null)}
          onOk={() => {
            for (const card of ix.children(c.id)) {
              if (card.props[removing.id] === undefined) continue;
              const props = { ...card.props };
              delete props[removing.id];
              Store.update({ ...card, props }, false);
            }
            setSchema(c, {
              ...s,
              props: s.props.filter((p) => p.id !== removing.id),
              sortBy: s.sortBy === removing.id ? "" : s.sortBy,
              groupBy: s.groupBy === removing.id ? "" : s.groupBy,
            });
          }}
        />
      )}
    </div>
  );
}

function TitleInput({ card }: { card: Page }) {
  const [v, setV] = useState(card.title);
  const focused = useRef(false);
  useEffect(() => {
    if (!focused.current) setV(card.title);
  }, [card.title]);
  const save = () => {
    const now = Store.page(card.id);
    if (now && now.title !== v) Store.update({ ...now, title: v });
  };
  return (
    <input
      class="cell-input"
      value={v}
      placeholder="Sin título"
      onFocus={() => (focused.current = true)}
      onBlur={() => {
        focused.current = false;
        save();
      }}
      onInput={(e) => setV(e.currentTarget.value)}
      onKeyDown={(e) => e.key === "Enter" && (e.currentTarget as HTMLInputElement).blur()}
    />
  );
}

/** A column with its name and kind: new, or the one being edited. */
function PropDialog({ c, s, prop, onClose }: { c: Page; s: Schema; prop?: Prop; onClose: () => void }) {
  const [name, setName] = useState(prop?.name ?? "");
  const [type, setType] = useState<PropType>(prop?.type ?? "TEXT");
  const types: PropType[] = ["TEXT", "NUMBER", "SELECT", "MULTI", "DATE", "CHECK"];
  const save = () => {
    if (!name.trim()) return;
    if (prop) {
      // Changing between kinds keeps the options (SELECT ↔ MULTI) and drops values that wouldn't fit.
      setSchema(c, { ...s, props: s.props.map((p) => (p.id === prop.id ? { ...p, name: name.trim(), type } : p)) });
      if (type !== prop.type) {
        for (const card of Store.index.children(c.id)) {
          const raw = card.props[prop.id];
          if (raw === undefined) continue;
          let keep: string | null = raw;
          if (type === "NUMBER" && !Number.isFinite(Number(raw))) keep = null;
          if (type === "DATE" && !/^\d{4}-\d{2}-\d{2}$/.test(raw)) keep = null;
          if (type === "CHECK" && raw !== "1") keep = null;
          if (type === "SELECT") keep = prop.type === "MULTI" ? (multiOf(raw)[0] ?? null) : prop.options.some((o) => o.id === raw) ? raw : null;
          if (type === "MULTI" && prop.type !== "SELECT") keep = null;
          if (keep !== raw) {
            const props = { ...card.props };
            if (keep == null) delete props[prop.id];
            else props[prop.id] = keep;
            Store.update({ ...card, props }, false);
          }
        }
      }
    } else {
      setSchema(c, { ...s, props: [...s.props, { id: newId(), name: name.trim(), type, options: [] }] });
    }
    onClose();
  };
  return (
    <Modal
      title={prop ? "Editar columna" : "Nueva columna"}
      onClose={onClose}
      footer={
        <>
          <button class="btn ghost" onClick={onClose}>
            Cancelar
          </button>
          <button class="btn primary" disabled={!name.trim()} onClick={save}>
            {prop ? "Guardar" : "Agregar"}
          </button>
        </>
      }
    >
      <input class="field" ref={focusOnMount} value={name} placeholder="Ej. Prioridad, Cliente, Precio" onInput={(e) => setName(e.currentTarget.value)} onKeyDown={(e) => e.key === "Enter" && save()} />
      <div class="section-label">
        <span>Guarda…</span>
      </div>
      <div class="type-grid">
        {types.map((t) => {
          const I = TYPE_ICON[t];
          return (
            <button key={t} class={`type-tile ${type === t ? "on" : ""}`} onClick={() => setType(t)}>
              <I size={18} />
              {t === "SELECT" ? "Opción" : PROP_LABEL[t]}
            </button>
          );
        })}
      </div>
    </Modal>
  );
}

// ── Values ────────────────────────────────────────────────────────────────────

/** A card's value for one column, editable in place. */
export function Value({ card, collection, prop }: { card: Page; collection: Page; prop: Prop }) {
  const raw = card.props[prop.id] ?? "";
  const [pick, setPick] = useState<HTMLElement | null>(null);
  switch (prop.type) {
    case "CHECK":
      return (
        <input
          type="checkbox"
          class="check"
          checked={raw === "1"}
          onChange={(e) => {
            putValue(card.id, collection, prop, e.currentTarget.checked ? "1" : null);
            if (e.currentTarget.checked) play("tick", 0.4);
          }}
        />
      );
    case "DATE":
      return (
        <label class={`date-cell ${raw ? "" : "empty"}`}>
          {raw ? <span class="date-label">{shortDate(raw)}</span> : <span class="date-label dim2">Vacío</span>}
          <input type="date" class="cell-input date" value={raw} onChange={(e) => putValue(card.id, collection, prop, e.currentTarget.value || null)} />
        </label>
      );
    case "TEXT":
    case "NUMBER":
      return <TextValue card={card} collection={collection} prop={prop} />;
    default: {
      const ids = prop.type === "MULTI" ? multiOf(raw) : raw ? [raw] : [];
      const opts = ids.map((id) => prop.options.find((o) => o.id === id)).filter(Boolean) as Opt[];
      return (
        <>
          <button class="opt-cell" onClick={(e) => setPick(e.currentTarget as HTMLElement)}>
            {opts.length ? (
              opts.map((o) => (
                <span class="chip" style={{ background: css(o.color) + "33" }}>
                  <i style={{ background: css(o.color) }} />
                  {o.name}
                </span>
              ))
            ) : (
              <span class="dim2">Vacío</span>
            )}
          </button>
          {pick && <OptionPicker at={pick} card={card} collection={collection} prop={prop} onClose={() => setPick(null)} />}
        </>
      );
    }
  }
}

function TextValue({ card, collection, prop }: { card: Page; collection: Page; prop: Prop }) {
  const raw = card.props[prop.id] ?? "";
  const [v, setV] = useState(raw);
  const focused = useRef(false);
  useEffect(() => {
    if (!focused.current) setV(raw);
  }, [raw]);
  return (
    <input
      class="cell-input"
      type={prop.type === "NUMBER" ? "number" : "text"}
      value={v}
      placeholder="Vacío"
      onFocus={() => (focused.current = true)}
      onBlur={() => {
        focused.current = false;
        if (v.trim() !== raw) putValue(card.id, collection, prop, v.trim() || null);
      }}
      onInput={(e) => setV(e.currentTarget.value)}
      onKeyDown={(e) => e.key === "Enter" && (e.currentTarget as HTMLInputElement).blur()}
    />
  );
}

function OptionPicker({ at, card, collection, prop, onClose }: { at: HTMLElement; card: Page; collection: Page; prop: Prop; onClose: () => void }) {
  const [q, setQ] = useState("");
  const live = Store.page(card.id) ?? card;
  const multi = prop.type === "MULTI";
  const chosen = new Set(multi ? multiOf(live.props[prop.id]) : live.props[prop.id] ? [live.props[prop.id]] : []);
  const shown = prop.options.filter((o) => o.name.toLowerCase().includes(q.trim().toLowerCase()));
  const exact = prop.options.some((o) => o.name.toLowerCase() === q.trim().toLowerCase());

  const choose = (id: string) => {
    if (multi) {
      const next = new Set(chosen);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      putValue(card.id, collection, prop, [...next].join(",") || null);
    } else {
      putValue(card.id, collection, prop, chosen.has(id) ? null : id);
      onClose();
    }
  };

  const create = () => {
    const name = q.trim();
    if (!name) return;
    const s = Store.page(collection.id)?.schema;
    if (!s) return;
    const o: Opt = { id: newId(), name, color: nextColor(prop.options) };
    const nextProp = { ...prop, options: [...prop.options, o] };
    setSchema(collection, { ...s, props: s.props.map((p) => (p.id === prop.id ? nextProp : p)) });
    putValue(card.id, collection, nextProp, multi ? [...chosen, o.id].join(",") : o.id);
    setQ("");
    if (!multi) onClose();
  };

  return (
    <Popover at={at} onClose={onClose} width={250}>
      <input
        class="field small"
        ref={focusOnMount}
        value={q}
        placeholder={multi ? "Busca o crea una etiqueta" : "Busca o crea una opción"}
        onInput={(e) => setQ(e.currentTarget.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            if (shown.length === 1 && q.trim()) choose(shown[0].id);
            else if (!exact) create();
          }
        }}
      />
      <div class="opt-list">
        {shown.map((o) => (
          <button key={o.id} class={`menu-item ${chosen.has(o.id) ? "on" : ""}`} onClick={() => choose(o.id)}>
            <span class="chip" style={{ background: css(o.color) + "33" }}>
              <i style={{ background: css(o.color) }} />
              {o.name}
            </span>
            <span class="grow" />
            {chosen.has(o.id) && <span class="accent">✓</span>}
          </button>
        ))}
        {q.trim() && !exact && (
          <button class="menu-item" onClick={create}>
            <Plus size={15} /> Crear «{q.trim()}»
          </button>
        )}
        {!shown.length && !q.trim() && <div class="dim small pad">Escribe para crear la primera.</div>}
      </div>
    </Popover>
  );
}

/** A card's values at the top of its page. */
export function PropsPanel({ card, collection }: { card: Page; collection: Page }) {
  const s = collection.schema;
  if (!s || !s.props.length) return null;
  return (
    <div class="props-panel">
      {s.props.map((p) => {
        const I = TYPE_ICON[p.type];
        return (
          <div class="prop-row" key={p.id}>
            <span class="prop-name">
              <I size={14} /> {p.name}
            </span>
            <div class={`prop-value t-${p.type.toLowerCase()}`}>
              <Value card={card} collection={collection} prop={p} />
            </div>
          </div>
        );
      })}
    </div>
  );
}

export type { Lane };
