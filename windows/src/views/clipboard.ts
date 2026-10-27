// The clipboard view (Tokkip): the shelf of what you copied and dropped, as a
// row of tiles next to Tokkip. Click a tile to copy it again, drag it out to
// drop it in another app, pin what you want to keep. Built once, updated in
// place; the tiles are only rebuilt when the shelf itself changed.

import { h, svg, clear } from "./dom";
import { ICONS } from "./icons";
import { Bridge, OWN_DRAG_TYPE } from "../core/bridge";
import {
  Clipboard, ago, clipCounts, colorOf, filesLabel, isLink, visibleClips, type Clip, type ClipFilter,
} from "../core/clipboard";
import { Sound } from "../core/sound";
import { State } from "../core/state";
import type { ViewActions, ViewHost } from "./views";
import { N_, language, t, tl } from "../i18n/i18n";

const S = {
  title: N_("Clipboard"),
  all: N_("All"),
  text: N_("Text"),
  images: N_("Images"),
  files: N_("Files"),
  pinned: N_("Pinned"),
  emptyTitle: N_("Copy something, or drop it here"),
  emptyHint: N_("Text, images and files you copy land on this shelf. Click to copy again, drag to drop in another app."),
  emptyFilter: N_("Nothing here yet."),
  paused: N_("Paused: what you copy is not kept"),
  pause: N_("Pause capture"),
  resume: N_("Resume capture"),
  clear: N_("Clear (keeps pinned)"),
  copied: N_("Copied"),
  pin: N_("Pin"),
  unpin: N_("Unpin"),
  ask: N_("Ask the AI about it"),
  open: N_("Show"),
  remove: N_("Remove"),
  dropHere: N_("Drop it here"),
  gone: N_("The file is no longer there."),
  hint: N_("Click to copy · drag to drop it in another app"),
};

const FILTERS: { id: ClipFilter; label: string }[] = [
  { id: "all", label: S.all },
  { id: "text", label: S.text },
  { id: "image", label: S.images },
  { id: "files", label: S.files },
  { id: "pinned", label: S.pinned },
];

/** Big text a long item's drag hands over: fetched when the pointer reaches it. */
const fullText = new Map<number, string>();

function iconButton(icon: string, title: string, onClick: () => void, cls = ""): HTMLElement {
  return h("button", {
    class: `clip-act ${cls}`,
    title: tl(title),
    onclick: (e: Event) => {
      e.stopPropagation();
      onClick();
    },
  }, svg(icon, 11));
}

function fileIcon(c: Clip): string {
  const f = c.files[0];
  if (!f) return ICONS.fileDoc;
  if (f.dir) return ICONS.folder;
  if (["png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "svg"].includes(f.ext)) return ICONS.image;
  return ICONS.fileDoc;
}

export function buildClipboard(actions: ViewActions): ViewHost {
  const chips = h("div", { class: "clip-chips" });
  const captureBtn = h("button", { class: "clip-head-btn" });
  const clearBtn = h("button", {
    class: "clip-head-btn",
    title: tl(S.clear),
    onclick: () => {
      actions.blip();
      void Bridge.clipboardClear();
    },
  }, svg(ICONS.trash, 11));
  const head = h(
    "div",
    { class: "clip-head" },
    h("span", { class: "clip-title" }, svg(ICONS.clipboard, 11), h("span", { text: tl(S.title) })),
    chips,
    h("span", { class: "clip-spacer" }),
    captureBtn,
    clearBtn,
  );
  const shelf = h("div", { class: "clip-shelf" });
  const empty = h("div", { class: "clip-empty" });
  const toast = h("div", { class: "clip-toast" });
  const foot = h("div", { class: "clip-foot" }, h("span", { class: "clip-hint", text: tl(S.hint) }), toast);
  const wrap = h("div", { class: "clip-wrap" }, head, shelf, empty, foot);
  const cardEl = h("div", { class: "card wash clip-card" }, wrap);
  cardEl.style.setProperty("--wash", "rgba(94,200,192,0.45)");
  const el = h("div", { class: "view clip-view" }, cardEl);

  // A wheel scrolls the row sideways: there is nothing below to scroll to.
  shelf.addEventListener("wheel", (e) => {
    if (Math.abs(e.deltaY) > Math.abs(e.deltaX)) {
      shelf.scrollLeft += e.deltaY;
      e.preventDefault();
    }
  }, { passive: false });

  let toastTimer: number | null = null;
  function say(text: string, ok = true) {
    toast.textContent = text;
    toast.classList.toggle("err", !ok);
    toast.classList.add("on");
    if (toastTimer != null) window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => toast.classList.remove("on"), 1600);
  }

  async function copy(c: Clip, tile: HTMLElement) {
    try {
      await Bridge.clipboardCopy(c.id);
      tile.classList.remove("flash");
      void tile.offsetWidth;
      tile.classList.add("flash");
      Sound.play("pop");
      say(t(S.copied));
    } catch (err) {
      Sound.play("error");
      say(String(err).replace(/^Error:\s*/, ""), false);
    }
  }

  async function ask(c: Clip) {
    try {
      const file = await Bridge.clipboardForChat(c.id);
      State.droppedFile = { name: file.name, path: file.path };
      State.promptContext = { kind: "file", name: file.name, path: file.path };
      State.chatHistory = [];
      void Bridge.chatReset();
      actions.blip();
      actions.setView("prompt");
    } catch (err) {
      Sound.play("error");
      say(String(err).replace(/^Error:\s*/, ""), false);
    }
  }

  function tile(c: Clip): HTMLElement {
    const body = h("div", { class: "ct-body" });
    if (c.kind === "image" && c.thumb) {
      body.append(h("img", { class: "ct-img", src: c.thumb, alt: "", draggable: "false" }));
    } else if (c.kind === "files") {
      const missing = c.files.every((f) => !f.exists);
      body.append(
        h("div", { class: "ct-file" + (missing ? " missing" : "") },
          svg(fileIcon(c), 26),
          h("span", { class: "ct-name", text: filesLabel(c.files) }),
          c.files[0]?.ext ? h("b", { class: "ct-ext", text: c.files[0].ext.toUpperCase() }) : h("span"),
        ),
      );
    } else {
      const swatch = colorOf(c.text);
      if (swatch) {
        body.append(h("div", { class: "ct-swatch", style: `background:${swatch}` }), h("div", { class: "ct-code", text: swatch }));
      } else {
        body.append(h("div", { class: "ct-text" + (isLink(c.text) ? " link" : ""), text: c.text }));
      }
    }

    const kindIcon = c.kind === "image" ? ICONS.image : c.kind === "files" ? ICONS.folder : ICONS.lines;
    const meta = h(
      "div",
      { class: "ct-meta" },
      svg(kindIcon, 8),
      h("span", { text: ago(c.created, Date.now(), language()) }),
      c.kind === "image" && c.width ? h("span", { class: "ct-dim", text: `${c.width}×${c.height}` }) : h("span"),
    );

    const acts = h("div", { class: "ct-acts" });
    acts.append(iconButton(ICONS.pin, c.pinned ? S.unpin : S.pin, () => {
      actions.blip();
      void Bridge.clipboardPin(c.id, !c.pinned);
    }, c.pinned ? "on" : ""));
    acts.append(iconButton(ICONS.sparkle, S.ask, () => void ask(c)));
    if (c.kind !== "text" || isLink(c.text)) {
      acts.append(iconButton(ICONS.launch, S.open, () => void Bridge.clipboardOpen(c.id)));
    }
    acts.append(iconButton(ICONS.xmark, S.remove, () => {
      actions.blip();
      void Bridge.clipboardDelete(c.id);
    }, "danger"));

    const el = h(
      "div",
      { class: `clip-tile k-${c.kind}${c.pinned ? " pinned" : ""}`, draggable: "true", title: "" },
      body,
      meta,
      acts,
      c.pinned ? h("i", { class: "ct-pin" }, svg(ICONS.pin, 8)) : h("i"),
    );
    el.addEventListener("click", () => void copy(c, el));
    if (c.kind === "text" && c.long) {
      el.addEventListener("mouseenter", () => {
        if (fullText.has(c.id)) return;
        void Bridge.clipboardText(c.id).then((full) => full && fullText.set(c.id, full));
      });
    }
    el.addEventListener("dragstart", (e) => {
      const dt = e.dataTransfer;
      if (c.kind === "text") {
        // The page can drag text anywhere on its own.
        dt?.setData("text/plain", fullText.get(c.id) ?? c.text);
        dt?.setData(OWN_DRAG_TYPE, String(c.id));
        if (dt) dt.effectAllowed = "copy";
        return;
      }
      // Files and images leave as real files: Windows starts the drag.
      e.preventDefault();
      void Bridge.clipboardDrag(c.id).then((ok) => {
        if (!ok) say(t(S.gone), false);
      });
    });
    return el;
  }

  let shownKey = "";

  function drawChips() {
    const counts = clipCounts(Clipboard.items);
    clear(chips);
    for (const f of FILTERS) {
      if (f.id !== "all" && counts[f.id] === 0 && Clipboard.filter !== f.id) continue;
      chips.append(h("button", {
        class: "clip-chip" + (Clipboard.filter === f.id ? " on" : ""),
        onclick: () => {
          actions.blip();
          Clipboard.filter = f.id;
          shownKey = "";
          sync();
        },
      }, h("span", { text: tl(f.label) }), h("b", { text: String(counts[f.id]) })));
    }
  }

  function sync() {
    const capture = Clipboard.capture;
    clear(captureBtn);
    captureBtn.append(svg(capture ? ICONS.pause : ICONS.play, 10));
    captureBtn.title = t(capture ? S.pause : S.resume);
    captureBtn.classList.toggle("off", !capture);
    captureBtn.onclick = () => {
      actions.blip();
      void Bridge.clipboardSetCapture(!capture);
    };
    wrap.classList.toggle("dragging", State.fileDragOver || State.textDragOver);

    const items = visibleClips(Clipboard.items, Clipboard.filter);
    const key = [
      Clipboard.filter, capture, language(), Math.floor(Date.now() / 60_000),
      ...items.map((c) => `${c.id}:${c.created}:${c.pinned}:${c.files.map((f) => f.exists).join("")}`),
    ].join("|");
    if (key === shownKey) return;
    shownKey = key;
    drawChips();

    clear(shelf);
    clear(empty);
    if (items.length === 0) {
      shelf.style.display = "none";
      empty.style.display = "";
      if (Clipboard.items.length === 0) {
        empty.append(
          h("div", { class: "clip-drop" }, svg(ICONS.clipboard, 18),
            h("div", {},
              h("b", { text: tl(capture ? S.emptyTitle : S.paused) }),
              h("p", { text: tl(S.emptyHint) }),
            ),
          ),
        );
      } else {
        empty.append(h("div", { class: "clip-drop small", text: tl(S.emptyFilter) }));
      }
      return;
    }
    shelf.style.display = "";
    empty.style.display = "none";
    for (const c of items) shelf.append(tile(c));
    if (!capture) shelf.prepend(h("div", { class: "clip-paused", text: tl(S.paused) }));
  }

  void Clipboard.start(() => {
    shownKey = "";
    State.notify();
  });

  return {
    el,
    sync,
    focus() {
      shelf.scrollLeft = 0;
    },
  };
}
