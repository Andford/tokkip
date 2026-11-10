// Tokkip shared with the phone through Tokkip Phone (src/core/phone-tokkip.ts).

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  feelingEmote, feelingLine, lookKey, parseShared, sharedJson, todaysFeeling,
} from "../src/core/phone-tokkip.ts";
import { DEFAULT_LOOK } from "../src/tokkip/tokkip.ts";

const phone = JSON.stringify({
  v: 1, from: "phone", changedAt: 1_700_000_000_000,
  look: { species: "gato", skin: "ensueno", body: "#FFFFFF", accent: "#E91E63", ink: "", cheek: "", outfit: "beanie" },
  feeling: { day: "2026-11-10", mood: 2, rest: "tired" },
});

test("the phone's Tokkip comes in whole", () => {
  const s = parseShared(phone);
  assert.equal(s.changedAt, 1_700_000_000_000);
  assert.equal(s.look.species, "gato");
  assert.equal(s.look.skin, "ensueno");
  assert.equal(s.look.accent, "#E91E63");
  assert.equal(s.outfit, "beanie");
  assert.deepEqual(s.feeling, { day: "2026-11-10", mood: 2, rest: "tired" });
});

test("anything odd falls back instead of breaking him", () => {
  assert.equal(parseShared(null), null);
  assert.equal(parseShared("not json"), null);
  assert.equal(parseShared(JSON.stringify({ v: 2, changedAt: 1, look: {} })), null);
  assert.equal(parseShared(JSON.stringify({ v: 1, look: {} })), null);
  const odd = parseShared(JSON.stringify({ v: 1, changedAt: 5, look: { species: "dragon", body: "red", outfit: "cape" }, feeling: { day: "today", mood: 9 } }));
  assert.equal(odd.look.species, "conejo");
  assert.equal(odd.look.body, DEFAULT_LOOK.body);
  assert.equal(odd.outfit, "auto");
  assert.equal(odd.feeling, null);
});

test("ours goes out in the same shape, and its key ignores the time", () => {
  const out = JSON.parse(sharedJson(DEFAULT_LOOK, "crown", 42));
  assert.equal(out.v, 1);
  assert.equal(out.from, "pc");
  assert.equal(out.look.outfit, "crown");
  const back = parseShared(JSON.stringify(out));
  assert.equal(lookKey(back.look, back.outfit), lookKey(DEFAULT_LOOK, "crown"));
  assert.notEqual(lookKey(DEFAULT_LOOK, "crown"), lookKey(DEFAULT_LOOK, "none"));
});

test("only today's feeling counts, and a hard day comes first", () => {
  const s = parseShared(phone);
  assert.equal(todaysFeeling(s, "2026-11-11"), null);
  const f = todaysFeeling(s, "2026-11-10");
  assert.equal(feelingLine(f), "Today is a hard day: I'm here with you.");
  assert.equal(feelingEmote(f), "love");
  assert.equal(feelingLine({ day: "x", mood: 0, rest: "tired" }), "Your phone says you slept little: take it easy today.");
  assert.equal(feelingEmote({ day: "x", mood: 5, rest: "" }), "happy");
  assert.equal(feelingEmote({ day: "x", mood: 3, rest: "ok" }), null);
});
