// Inicio, made so you never have to think where to start: Tokkip says hi,
// one field gets a thought out of your head (it waits in the Bandeja), then
// what's due today on your boards, where you left off, your favourites and
// all your pages.

import { useState } from "preact/hooks";
import { ChevronRight, CornerDownLeft, Inbox, Plus } from "lucide-preact";
import { type Index, INBOX_ID, css, dayKey, dueSoon, groupOf, shortDate, shownTitle } from "../data/model";
import { Store } from "../data/store";
import { TEMPLATES, build } from "../data/templates";
import { Tokkip } from "../tokkip/Tokkip";
import { play } from "../tokkip/sound";
import { PageIcon, SectionLabel, ShapeIcon, greeting, useNow } from "./kit";
import { Hop, Nav, Templates } from "./state";

export function CaptureCard({ placeholder, onCapture }: { placeholder: string; onCapture: (text: string) => void }) {
  const [text, setText] = useState("");
  const [saved, setSaved] = useState(0);
  const send = () => {
    if (!text.trim()) return;
    onCapture(text);
    setText("");
    setSaved(Date.now());
    setTimeout(() => setSaved(0), 1800);
  };
  return (
    <div class="capture">
      <textarea
        rows={1}
        value={text}
        placeholder={placeholder}
        onInput={(e) => {
          const el = e.currentTarget;
          setText(el.value);
          el.style.height = "0px";
          el.style.height = `${el.scrollHeight}px`;
        }}
        onKeyDown={(e) => {
          if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            send();
            (e.currentTarget as HTMLTextAreaElement).style.height = "";
          }
        }}
      />
      <span class="capture-hint">{saved ? <span class="ok">Guardado ✓</span> : <>Enter <CornerDownLeft size={12} /> · Shift+Enter otra línea</>}</span>
      <button class="send" disabled={!text.trim()} onClick={send} title="Guardar en la Bandeja">
        <Plus size={18} />
      </button>
    </div>
  );
}

export function Home({ ix }: { ix: Index }) {
  const now = useNow();
  const hop = Hop.use();
  const inbox = ix.inbox;
  const due = dueSoon(ix, now);
  const recent = ix.recent.filter((p) => p.parent !== INBOX_ID).slice(0, 6);
  const favs = ix.favorites;
  const roots = ix.roots;
  const today = new Date(now).toLocaleDateString("es-MX", { weekday: "long", day: "numeric", month: "long" });

  return (
    <div class="home">
      <div class="home-inner">
        <header class="hello">
          <Tokkip mood={due.length > 3 ? "worried" : "happy"} size={96} jump={hop} />
          <div>
            <span class="eyebrow">{today}</span>
            <h1 class="screen-title">{greeting(now)}</h1>
            <p class="dim">
              {due.length
                ? due.length === 1
                  ? "Tienes 1 cosa para hoy"
                  : `Tienes ${due.length} cosas para hoy`
                : inbox.length >= 6
                  ? `${inbox.length} ideas esperando en la Bandeja`
                  : ix.live.length === 0
                    ? "Empecemos con una página"
                    : "¿En qué andamos?"}
            </p>
          </div>
        </header>

        <CaptureCard
          placeholder="¿Qué traes en la cabeza? Se guarda en la Bandeja"
          onCapture={(text) => {
            if (Store.capture(text)) {
              play("pop");
              Hop.now();
            }
          }}
        />

        {inbox.length > 0 && (
          <button class="inbox-strip" onClick={() => Nav.page(INBOX_ID)}>
            <ShapeIcon icon={Inbox} shape={10} size={36} />
            <span class="inbox-text">
              <b>Bandeja</b>
              <span class="dim2">{inbox.slice(0, 3).map(shownTitle).join(" · ")}</span>
            </span>
            <span class="count">{inbox.length}</span>
            <ChevronRight size={18} class="dim" />
          </button>
        )}

        {due.length > 0 && (
          <>
            <SectionLabel>Para hoy</SectionLabel>
            <div class="due-list">
              {due.slice(0, 8).map((d) => {
                const g = d.collection.schema ? groupOf(d.collection.schema) : undefined;
                const opt = g?.options.find((o) => o.id === d.card.props[g.id]);
                const late = d.day < dayKey(now);
                return (
                  <button class="due-row" key={d.card.id} onClick={() => Nav.page(d.card.id)}>
                    <PageIcon p={d.collection} size={26} />
                    <span class="due-title">{shownTitle(d.card)}</span>
                    {opt && (
                      <span class="chip" style={{ background: css(opt.color) + "33" }}>
                        <i style={{ background: css(opt.color) }} />
                        {opt.name}
                      </span>
                    )}
                    <span class={`chip ${late ? "late" : ""}`}>{shortDate(d.day, now)}</span>
                    <span class="dim2 small">{shownTitle(d.collection)}</span>
                  </button>
                );
              })}
            </div>
          </>
        )}

        {recent.length > 0 && (
          <>
            <SectionLabel>Seguir donde te quedaste</SectionLabel>
            <div class="recent-grid">
              {recent.map((p) => (
                <button class="recent-card" key={p.id} onClick={() => Nav.page(p.id)}>
                  <PageIcon p={p} size={34} />
                  <b>{shownTitle(p)}</b>
                  <span class="dim2 small clamp">
                    {p.kind === "COLLECTION" ? `${ix.children(p.id).length} tarjetas` : p.preview || (p.steps ? "" : "Vacía")}
                  </span>
                  {p.steps > 0 && (
                    <span class="progress">
                      <i style={{ width: `${(p.done / p.steps) * 100}%`, background: css(p.color) }} />
                    </span>
                  )}
                </button>
              ))}
            </div>
          </>
        )}

        {favs.length > 0 && (
          <>
            <SectionLabel>Favoritos</SectionLabel>
            <div class="chip-row">
              {favs.map((p) => (
                <button class="page-chip" key={p.id} onClick={() => Nav.page(p.id)}>
                  <PageIcon p={p} size={22} />
                  {shownTitle(p)}
                </button>
              ))}
            </div>
          </>
        )}

        <SectionLabel
          right={
            <button class="link" onClick={() => Templates.open(null)}>
              Ver plantillas
            </button>
          }
        >
          {roots.length ? "Tus páginas" : "Empieza con una plantilla"}
        </SectionLabel>
        {roots.length > 0 ? (
          <div class="chip-row">
            {roots.map((p) => (
              <button class="page-chip" key={p.id} onClick={() => Nav.page(p.id)}>
                <PageIcon p={p} size={22} />
                {shownTitle(p)}
                {ix.children(p.id).length > 0 && <span class="dim2 small">{ix.children(p.id).length}</span>}
              </button>
            ))}
            <button class="page-chip add" onClick={() => Templates.open(null)}>
              <Plus size={16} /> Nueva página
            </button>
          </div>
        ) : (
          <div class="template-grid">
            {TEMPLATES.slice(0, 6).map((t) => (
              <button
                key={t.id}
                class="template-tile"
                onClick={() => {
                  const p = Store.create(build(t, null, Date.now(), 0));
                  play("pop");
                  Nav.page(p.id);
                }}
              >
                <PageIcon p={t} size={40} />
                <b>{t.name}</b>
                <span class="dim2 small">{t.about}</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
