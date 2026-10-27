import { describe, expect, it } from "vitest";
import { blockOf, blocksOf, factsOf, plainOf } from "../src/data/blocks";
import { drawingJson, emptyDrawing, lowest, parseDrawing, strokesHit, widthAt } from "../src/ink/ink";
import { TEMPLATES, build } from "../src/data/templates";

describe("ink", () => {
  const d = parseDrawing('{"v":1,"bg":"grid","h":900,"s":[{"k":"pen","c":0,"w":3,"p":[100,100,50,300,100,60]},{"k":"hl","c":4294829127,"w":18,"p":[100,600,50,900,600,50]}]}');

  it("reads and writes the shared format", () => {
    expect(d.bg).toBe("grid");
    expect(d.s).toHaveLength(2);
    expect(parseDrawing(drawingJson(d))).toEqual(d);
    expect(parseDrawing("basura").s).toEqual([]);
    expect(parseDrawing('{"v":2}').bg).toBe("lines");
    expect(lowest(d)).toBe(300);
    expect(widthAt(4, 100)).toBeGreaterThan(widthAt(4, 10));
  });

  it("finds the strokes the eraser touches, also between points", () => {
    expect(strokesHit(d, 100, 50, 5)).toEqual([0]); // on the segment from (50,50) to (150,50)
    expect(strokesHit(d, 300, 300, 5)).toEqual([1]);
    expect(strokesHit(d, 800, 800, 5)).toEqual([]);
  });

  it("keeps drawings out of previews and search", () => {
    const body = `Hola\n%%${drawingJson(d)}\n[ ] algo`;
    expect(blocksOf(body)[1].kind).toBe("DRAW");
    expect(blockOf("%%{}").text).toBe("{}");
    expect(factsOf(body).preview).toBe("Hola · algo");
    expect(plainOf(body)).toBe("Hola\nalgo");
    const page = drawingJson(emptyDrawing());
    expect(factsOf(page)).toEqual({ preview: "", steps: 0, done: 0, links: [] });
    expect(plainOf(page)).toBe("");
  });

  it("makes a handwritten page from its template", () => {
    const dr = build(TEMPLATES.find((t) => t.id === "ink")!, null, 1);
    expect(dr.page.kind).toBe("INK");
    expect(parseDrawing(dr.body).bg).toBe("lines");
  });
});
