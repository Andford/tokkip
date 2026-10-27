import { test } from "node:test";
import assert from "node:assert/strict";
import { moveOldStorage } from "../src/core/legacy.ts";

class Mem {
  constructor(o) { this.m = new Map(Object.entries(o)); }
  get length() { return this.m.size; }
  key(i) { return [...this.m.keys()][i] ?? null; }
  getItem(k) { return this.m.has(k) ? this.m.get(k) : null; }
  setItem(k, v) { this.m.set(k, String(v)); }
  removeItem(k) { this.m.delete(k); }
}

test("old storage keys move to Tokkip's, without overwriting", () => {
  const s = new Mem({ "coucou.wellness.tasks": "[1]", "coucou.wellness.tab": "tasks", "tokkip.wellness.tab": "focus", other: "x" });
  assert.equal(moveOldStorage(s), 1);
  assert.equal(s.getItem("tokkip.wellness.tasks"), "[1]");
  assert.equal(s.getItem("tokkip.wellness.tab"), "focus");
  assert.equal(s.getItem("coucou.wellness.tasks"), null);
  assert.equal(s.getItem("other"), "x");
});
