import test from "node:test";
import assert from "node:assert/strict";
import { parsePcNote, pcNoteIsNews } from "../src/core/wellness.ts";

const note = (o = {}) => JSON.stringify({ app: "tokkip-healthy-pc", version: 1, seq: 3, at: 1_000_000, kind: "clean", score: 92, mood: "party", message: "Liberé  2 GB\nen tu PC", bytes: 2e9, ...o });

test("a Tokkip Healthy PC note is read and cleaned up", () => {
  const n = parsePcNote(note());
  assert.equal(n.seq, 3);
  assert.equal(n.kind, "clean");
  assert.equal(n.message, "Liberé 2 GB en tu PC");
  assert.equal(n.bytes, 2e9);
});

test("anything else isn't a note", () => {
  assert.equal(parsePcNote(null), null);
  assert.equal(parsePcNote("{"), null);
  assert.equal(parsePcNote(note({ app: "other" })), null);
  assert.equal(parsePcNote(note({ score: 140 })), null);
  assert.equal(parsePcNote(note({ kind: "shout" })), null);
});

test("only a new, recent clean-up or care note is said out loud", () => {
  const n = parsePcNote(note());
  assert.equal(pcNoteIsNews(n, 2, 1_000_000 + 60_000), true);
  assert.equal(pcNoteIsNews(n, 3, 1_000_000 + 60_000), false);
  assert.equal(pcNoteIsNews(n, 2, 1_000_000 + 11 * 60_000), false);
  assert.equal(pcNoteIsNews(parsePcNote(note({ kind: "status" })), 2, 1_000_000), false);
});
