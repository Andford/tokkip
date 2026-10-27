import { describe, expect, it } from "vitest";
import { blockOf, blocksOf, bodyOf, captureOf, factsOf, nextKind, numberAt, shortcutOf } from "../src/data/blocks";
import { INBOX_ID, Index, board, copyJson, copyOf, css, dayKey, defaultSchema, display, dueSoon, moveCard, page, rows, schemaJson, schemaOf, searchScore, shortDate, snippet } from "../src/data/model";
import { TEMPLATES, build, flatten } from "../src/data/templates";

const DAY = 86_400_000;

describe("blocks", () => {
  it("reads and writes the Android line format", () => {
    const body = "# Título\n## Sub\n• uno\n1. a\n2. b\n[ ] paso\n[x] hecho\n> cita\n! aviso\n---\n@@abc\ntexto";
    const bs = blocksOf(body);
    expect(bs.map((b) => b.kind)).toEqual(["H1", "H2", "BULLET", "NUMBER", "NUMBER", "TODO", "DONE", "QUOTE", "CALLOUT", "DIVIDER", "PAGE", "TEXT"]);
    expect(bodyOf(bs)).toBe(body.replace("2. b", "1. b"));
    expect(blockOf("- algo")).toEqual({ kind: "BULLET", text: "algo" });
    expect(numberAt(bs.map((b) => b.kind), 4)).toBe(2);
  });

  it("knows the facts of a page", () => {
    const f = factsOf("Hola\n[ ] uno\n[x] dos\n@@kid\n---");
    expect(f).toEqual({ preview: "Hola · uno · dos", steps: 2, done: 1, links: ["kid"] });
  });

  it("captures a title and a body", () => {
    expect(captureOf("Llamar a Luis\nsobre el logo")).toEqual(["Llamar a Luis", "sobre el logo"]);
    expect(captureOf("x".repeat(90))[0]).toBe("");
  });

  it("turns typed starts into kinds", () => {
    expect(shortcutOf("[] ")).toEqual(["TODO", 3]);
    expect(shortcutOf("# ")).toEqual(["H1", 2]);
    expect(shortcutOf("---")).toEqual(["DIVIDER", 3]);
    expect(shortcutOf("hola")).toBeNull();
    expect(nextKind("DONE")).toBe("TODO");
    expect(nextKind("H1")).toBe("TEXT");
  });
});

describe("tree", () => {
  const ix = new Index([
    page({ id: INBOX_ID, title: "Bandeja" }),
    page({ id: "a", title: "A", ord: 1 }),
    page({ id: "b", title: "B", ord: 0 }),
    page({ id: "a1", parent: "a", title: "A1" }),
    page({ id: "a11", parent: "a1", title: "A11" }),
    page({ id: "t", parent: "a", trashed: 5 }),
    page({ id: "n", parent: INBOX_ID, title: "idea" }),
  ]);

  it("orders, finds paths and keeps a page out of itself", () => {
    expect(ix.roots.map((p) => p.id)).toEqual(["b", "a"]);
    expect(ix.path("a11").map((p) => p.id)).toEqual(["a", "a1"]);
    expect(ix.isInside("a11", "a")).toBe(true);
    expect(ix.isInside("a", "a11")).toBe(false);
    expect(ix.inbox.map((p) => p.id)).toEqual(["n"]);
    expect(ix.trash.map((p) => p.id)).toEqual(["t"]);
    expect(ix.subtree("a").map((p) => p.id).sort()).toEqual(["a", "a1", "a11", "t"]);
  });
});

describe("collections", () => {
  const c = page({ id: "c", kind: "COLLECTION", schema: defaultSchema() });
  const cards = [
    page({ id: "1", parent: "c", title: "uno", ord: 0, props: { status: "todo" } }),
    page({ id: "2", parent: "c", title: "dos", ord: 1, props: { status: "doing", date: "2026-10-20" } }),
    page({ id: "3", parent: "c", title: "tres", ord: 2, props: { status: "done", date: "2026-10-20" } }),
    page({ id: "4", parent: "c", title: "cuatro", ord: 3 }),
  ];
  const ix = new Index([c, ...cards]);

  it("groups a board, with loose cards first", () => {
    const lanes = board(ix, c);
    expect(lanes.map((l) => l.opt?.id ?? null)).toEqual([null, "todo", "doing", "done"]);
    expect(lanes[0].cards.map((k) => k.id)).toEqual(["4"]);
  });

  it("moves a card between lanes", () => {
    const lanes = board(ix, c);
    const moved = moveCard(cards[0], c.schema!.props[0], lanes[3], 0);
    expect(moved.props.status).toBe("done");
    expect(moved.ord).toBe(1);
    const loose = moveCard(cards[1], c.schema!.props[0], lanes[0], 5);
    expect(loose.props.status).toBeUndefined();
  });

  it("sorts a table, empties last", () => {
    const sorted = rows(ix, { ...c, schema: { ...c.schema!, sortBy: "title" } });
    expect(sorted.map((r) => r.title)).toEqual(["cuatro", "dos", "tres", "uno"]);
    const byDate = rows(ix, { ...c, schema: { ...c.schema!, sortBy: "date", sortDesc: true } });
    expect(byDate.slice(2).map((r) => r.id).sort()).toEqual(["1", "4"]);
  });

  it("lists what's due and not done", () => {
    const now = new Date(2026, 9, 26, 12).getTime();
    expect(dueSoon(ix, now).map((d) => d.card.id)).toEqual(["2"]);
  });

  it("shows values", () => {
    const now = new Date(2026, 9, 26, 12).getTime();
    expect(display(c.schema!.props[0], "doing")).toBe("Haciendo");
    expect(shortDate("2026-10-26", now)).toBe("hoy");
    expect(shortDate("2026-10-27", now)).toBe("mañana");
    expect(shortDate("2026-10-30", now)).toBe("vie 30 oct");
    expect(dayKey(now + DAY)).toBe("2026-10-27");
  });
});

describe("search", () => {
  it("ignores accents and case, titles weigh more", () => {
    expect(searchScore("cafe", "Café Potss", "")).toBe(6);
    expect(searchScore("menu", "x", "el menú de bebidas")).toBe(1);
    expect(searchScore("menu nada", "x", "el menú")).toBe(0);
    expect(snippet("bebidas", "a".repeat(100) + " las bebidas frías")).toContain("bebidas");
  });
});

describe("copies", () => {
  it("round-trips the Android JSON (props and schema as text)", () => {
    const android = {
      app: "tokkipnotas",
      version: 1,
      at: 1,
      pages: [
        { id: "inbox", parent: null, title: "Bandeja", icon: "inbox", color: 0xff94a3b8, shape: 0, kind: "DOC", ord: 0, created: 1, updated: 1, opened: 0, favorite: false, trashed: 0, props: "", schema: "", body: "" },
        { id: "c", parent: null, title: "Tablero", icon: "board", color: 0xfffbbf24, shape: 3, kind: "COLLECTION", ord: 0, created: 1, updated: 1, opened: 0, favorite: true, trashed: 0, props: "", schema: schemaJson(defaultSchema()), body: "" },
        { id: "k", parent: "c", title: "Tarjeta", icon: "doc", color: 0xffa78bfa, shape: 0, kind: "DOC", ord: 0, created: 1, updated: 1, opened: 0, favorite: false, trashed: 0, props: '{"status":"doing"}', schema: "", body: "[ ] algo" },
      ],
    };
    const list = copyOf(JSON.stringify(android))!;
    expect(list).toHaveLength(3);
    expect(list[1].page.schema?.props[0].options[1].name).toBe("Haciendo");
    expect(list[1].page.favorite).toBe(true);
    expect(list[2].page.props).toEqual({ status: "doing" });
    expect(css(list[2].page.color)).toBe("#a78bfa");
    const again = JSON.parse(copyJson(list, 5));
    expect(again.pages[2].props).toBe('{"status":"doing"}');
    expect(typeof again.pages[1].schema).toBe("string");
    expect(schemaOf(again.pages[1].schema)).toEqual(defaultSchema());
    expect(copyOf('{"app":"otra"}')).toBeNull();
    expect(copyOf("no es json")).toBeNull();
  });
});

describe("templates", () => {
  it("builds every template, the project with its board", () => {
    for (const t of TEMPLATES) {
      const d = build(t, null, 1);
      expect(d.page.parent).toBeNull();
    }
    const project = flatten(build(TEMPLATES.find((t) => t.id === "project")!, null, 1));
    expect(project).toHaveLength(3);
    const boardPage = project[1].page;
    expect(boardPage.kind).toBe("COLLECTION");
    expect(project[0].body).toContain(`@@${boardPage.id}`);
    expect(project[2].page.parent).toBe(boardPage.id);
  });

  it("builds a second brain: PARA, linked from its first page", () => {
    const all = flatten(build(TEMPLATES.find((t) => t.id === "brain")!, null, 1));
    const titles = all.map((x) => x.page.title);
    expect(titles.slice(0, 5)).toEqual(["Segundo cerebro", "Proyectos", "Mi primer proyecto", "Áreas", "Trabajo"]);
    expect(titles).toContain("Recursos");
    expect(titles).toContain("Archivo");
    const top = all[0];
    for (const kid of all.filter((x) => x.page.parent === top.page.id)) expect(top.body).toContain(`@@${kid.page.id}`);
    expect(all.find((x) => x.page.title === "Proyectos")!.page.schema!.view).toBe("board");
  });
});
