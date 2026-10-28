// A colour of the user's own for each pill's Tokkip (src/core/pill-colors.ts),
// and how the app state paints with it, so every window reads the same
// preference the same way.

import { beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import { PILL_PALETTE, normalizeHex, parsePillColors, pillColor, withPillColor } from "../src/core/pill-colors.ts";
import { PILL_CATALOG, pillDefinition } from "../src/core/pills.ts";
import { DEFAULT_SETTINGS, State } from "../src/core/state.ts";

const VS_CODE = pillDefinition("integration_wellness").color;
const TEAL = "#2DD4BF";

// ── The pure half ─────────────────────────────────────────────────────────────

test("a colour is six hex digits, written #RRGGBB in upper case", () => {
  assert.equal(normalizeHex("#2dd4bf"), TEAL);
  assert.equal(normalizeHex("2DD4BF"), TEAL);
  assert.equal(normalizeHex("  #2DD4BF "), TEAL);
  for (const bad of ["", "#2DD4B", "#2DD4BFF", "#GGGGGG", "teal", "rgb(1,2,3)", null, undefined, 0x2dd4bf, {}]) {
    assert.equal(normalizeHex(bad), null, String(bad));
  }
});

test("a stored preference keeps what is a colour and drops the rest", () => {
  assert.deepEqual(
    parsePillColors({ integration_wellness: "#2dd4bf", agent_cursor: "blue", "": "#FFFFFF", agent_new: "#abcdef", n: 3 }),
    { integration_wellness: TEAL, agent_new: "#ABCDEF" },
  );
  for (const junk of [null, undefined, "x", 3, ["#2DD4BF"]]) assert.deepEqual(parsePillColors(junk), {});
});

test("a pill wears the user's colour when there is one, else the catalog's", () => {
  assert.equal(pillColor("integration_wellness", VS_CODE, {}), VS_CODE);
  assert.equal(pillColor("integration_wellness", VS_CODE, null), VS_CODE);
  assert.equal(pillColor("integration_wellness", VS_CODE, { integration_wellness: "#2dd4bf" }), TEAL);
  assert.equal(pillColor("integration_wellness", VS_CODE, { integration_wellness: "nope" }), VS_CODE);
  assert.equal(pillColor("agent_cursor", "#C0C4CC", { integration_wellness: TEAL }), "#C0C4CC");
});

test("picking a colour stores it; the default, nothing or junk clears it", () => {
  const before = { agent_cursor: "#F4505E" };
  const picked = withPillColor(before, "integration_wellness", "#2dd4bf", VS_CODE);
  assert.deepEqual(picked, { agent_cursor: "#F4505E", integration_wellness: TEAL });
  assert.deepEqual(before, { agent_cursor: "#F4505E" }, "the preference given is not changed");

  assert.deepEqual(withPillColor(picked, "integration_wellness", null, VS_CODE), before);
  assert.deepEqual(withPillColor(picked, "integration_wellness", VS_CODE.toLowerCase(), VS_CODE), before);
  assert.deepEqual(withPillColor(picked, "integration_wellness", "teal", VS_CODE), before);
});

// ── The app state ─────────────────────────────────────────────────────────────

beforeEach(() => {
  State.tasks = [];
  State.focusId = null;
  State.os = "windows";
  State.settings = { ...DEFAULT_SETTINGS };
});

const colorOf = (id) => State.tasks.find((t) => t.id === id)?.color;

test("with no preference every pill is painted as the catalog says", () => {
  assert.deepEqual(DEFAULT_SETTINGS.pillColors, {});
  State.loadIntegrationTasks();
  for (const task of State.tasks) assert.equal(task.color, pillDefinition(task.id).color, task.id);
});

test("a pill is created in the colour the user gave it", () => {
  State.settings.pillColors = { integration_wellness: TEAL };
  State.loadIntegrationTasks();
  assert.equal(colorOf("integration_wellness"), TEAL);
  assert.equal(colorOf("integration_github"), pillDefinition("integration_github").color);
});

test("a colour picked later reaches the pills already on the island, and back", () => {
  State.loadIntegrationTasks();
  assert.equal(colorOf("integration_wellness"), VS_CODE);

  State.settings = { ...State.settings, pillColors: { integration_wellness: TEAL } };
  State.loadIntegrationTasks();
  assert.equal(colorOf("integration_wellness"), TEAL);

  State.settings = { ...State.settings, pillColors: {} };
  State.loadIntegrationTasks();
  assert.equal(colorOf("integration_wellness"), VS_CODE);
});

test("an agent the catalog does not know keeps the colour it came with", () => {
  State.settings.pillColors = { agent_homemade: TEAL };
  State.loadIntegrationTasks();
  State.upsertExternalAgent("agent_homemade", "Homemade", "#60A5FA");
  assert.equal(colorOf("agent_homemade"), "#60A5FA");
  State.loadIntegrationTasks();
  assert.equal(colorOf("agent_homemade"), "#60A5FA");
});
