// A page's text: one block per line, with a short mark in front — the same
// format as on Android (Blocks.kt), readable as is outside the app too.
//   "# "  título           "## " subtítulo
//   "• "  viñeta           "1. " lista numerada
//   "[ ] " paso por hacer  "[x] " paso hecho
//   "> "  cita             "! "  aviso (Tokkip te lo recuerda)
//   "---" separador        "@@<id>" una subpágina
//   "%%<json>" un dibujo hecho con la pluma (ink.ts)
// Anything else is a paragraph. A page written by hand (kind INK) keeps its
// whole drawing as its body instead (it starts with {"v":).

export type BlockKind = "TEXT" | "H1" | "H2" | "BULLET" | "NUMBER" | "TODO" | "DONE" | "QUOTE" | "CALLOUT" | "DIVIDER" | "PAGE" | "DRAW";

export const PREFIX: Record<BlockKind, string> = {
  TEXT: "",
  H1: "# ",
  H2: "## ",
  BULLET: "• ",
  NUMBER: "1. ",
  TODO: "[ ] ",
  DONE: "[x] ",
  QUOTE: "> ",
  CALLOUT: "! ",
  DIVIDER: "---",
  PAGE: "@@",
  DRAW: "%%",
};

export interface Block {
  kind: BlockKind;
  text: string;
}

export const isStep = (k: BlockKind) => k === "TODO" || k === "DONE";
/** Lines without a text field. */
export const isFixed = (k: BlockKind) => k === "DIVIDER" || k === "PAGE" || k === "DRAW";

/** A handwritten page's body (a drawing, not lines of text). */
export const isInkBody = (body: string) => body.startsWith('{"v":');

export function lineOf(b: Block): string {
  return b.kind === "DIVIDER" ? "---" : PREFIX[b.kind] + b.text;
}

const NUMBERED = /^\d{1,3}\. /;

export function blockOf(l: string): Block {
  if (l === "---") return { kind: "DIVIDER", text: "" };
  if (l.startsWith("@@")) return { kind: "PAGE", text: l.slice(2).trim() };
  if (l.startsWith("%%")) return { kind: "DRAW", text: l.slice(2) };
  if (l.startsWith("[x] ") || l.startsWith("[X] ")) return { kind: "DONE", text: l.slice(4) };
  if (l.startsWith("[ ] ")) return { kind: "TODO", text: l.slice(4) };
  if (l.startsWith("## ")) return { kind: "H2", text: l.slice(3) };
  if (l.startsWith("# ")) return { kind: "H1", text: l.slice(2) };
  if (l.startsWith("• ") || l.startsWith("- ") || l.startsWith("* ")) return { kind: "BULLET", text: l.slice(2) };
  if (l.startsWith("> ")) return { kind: "QUOTE", text: l.slice(2) };
  if (l.startsWith("! ")) return { kind: "CALLOUT", text: l.slice(2) };
  if (NUMBERED.test(l)) return { kind: "NUMBER", text: l.replace(NUMBERED, "") };
  return { kind: "TEXT", text: l };
}

export function blocksOf(body: string): Block[] {
  return body === "" ? [{ kind: "TEXT", text: "" }] : body.split("\n").map(blockOf);
}

export function bodyOf(blocks: Block[]): string {
  return blocks.map(lineOf).join("\n").replace(/\n+$/, "");
}

/** The number a numbered line shows: its place in its run of numbered lines. */
export function numberAt(kinds: BlockKind[], i: number): number {
  let n = 1;
  for (let j = i - 1; j >= 0 && kinds[j] === "NUMBER"; j--) n++;
  return n;
}

export interface Facts {
  preview: string;
  steps: number;
  done: number;
  links: string[];
}

export function factsOf(body: string): Facts {
  if (isInkBody(body)) return { preview: "", steps: 0, done: 0, links: [] };
  const blocks = blocksOf(body);
  const preview = blocks
    .filter((b) => !isFixed(b.kind) && b.text.trim())
    .slice(0, 3)
    .map((b) => b.text.trim())
    .join(" · ")
    .slice(0, 140);
  return {
    preview,
    steps: blocks.filter((b) => isStep(b.kind)).length,
    done: blocks.filter((b) => b.kind === "DONE").length,
    links: blocks.filter((b) => b.kind === "PAGE").map((b) => b.text),
  };
}

/** The text without marks, for search. */
export function plainOf(body: string): string {
  if (isInkBody(body)) return "";
  return blocksOf(body)
    .filter((b) => b.kind !== "PAGE" && b.kind !== "DRAW")
    .map((b) => b.text)
    .join("\n");
}

/** A capture: the first line is the title (when it's short), the rest the body. */
export function captureOf(text: string): [string, string] {
  const lines = text.trim().split("\n");
  const first = (lines[0] ?? "").trim();
  return first.length <= 80 ? [first, lines.slice(1).join("\n").trim()] : ["", text.trim()];
}

/** The line kind after Enter: lists and steps continue, the rest go back to text. */
export function nextKind(k: BlockKind): BlockKind {
  if (k === "TODO" || k === "DONE") return "TODO";
  if (k === "BULLET" || k === "NUMBER") return k;
  return "TEXT";
}

/** Markdown-ish shortcuts typed at the start of a plain line: the kind and how much to cut. */
export function shortcutOf(text: string): [BlockKind, number] | null {
  if (text.startsWith("[] ")) return ["TODO", 3];
  if (text.startsWith("[ ] ")) return ["TODO", 4];
  if (text.startsWith("[x] ")) return ["DONE", 4];
  if (text.startsWith("- ") || text.startsWith("* ") || text.startsWith("• ")) return ["BULLET", 2];
  if (text.startsWith("1. ")) return ["NUMBER", 3];
  if (text.startsWith("## ")) return ["H2", 3];
  if (text.startsWith("# ")) return ["H1", 2];
  if (text.startsWith("> ")) return ["QUOTE", 2];
  if (text.startsWith("! ")) return ["CALLOUT", 2];
  if (text === "---") return ["DIVIDER", 3];
  return null;
}
