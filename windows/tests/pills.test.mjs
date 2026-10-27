// The pill catalog (src/core/pills.ts) and the declared pills in the app state.
// Tokkip keeps its day, the chat providers, GitHub and Spotify: no coding
// agents and no other services. The clipboard is a view of its own, not a pill.

import { beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import {
  DEFAULT_MAIN_PILL, MAX_DECLARED, PILL_CATALOG, availablePills, chooseMainPill,
  isHookPill, mainPillChoices, orderPills, pillDefinition, sanitizeDeclared, toggleDeclared,
} from "../src/core/pills.ts";
import { DEFAULT_SETTINGS, State } from "../src/core/state.ts";

const DAY = "integration_wellness";

test("Tokkip's catalog: the day, the chat providers, GitHub and Spotify", () => {
  assert.deepEqual(PILL_CATALOG.map((p) => p.id), [
    DAY, "ai_anthropic", "ai_google", "ai_openai", "ai_ollama", "ai_lmstudio",
    "integration_github", "integration_spotify",
  ]);
  // Stored settings, tasks and keys depend on these IDs.
  assert.equal(pillDefinition("integration_github").color, "#F4505E");
  assert.equal(pillDefinition("integration_spotify").color, "#1DB954");
});

test("IDs are unique", () => {
  const ids = PILL_CATALOG.map((p) => p.id);
  assert.equal(new Set(ids).size, ids.length);
});

test("no pill is fed by agent hooks any more", () => {
  for (const def of PILL_CATALOG) assert.equal(isHookPill(def.id), false, def.id);
});

test("every pill works on both systems", () => {
  assert.deepEqual(availablePills("windows").map((p) => p.id), PILL_CATALOG.map((p) => p.id));
  assert.deepEqual(availablePills("linux").map((p) => p.id), PILL_CATALOG.map((p) => p.id));
});

test("the day is the main pill, and the only one that can be", () => {
  assert.equal(DEFAULT_MAIN_PILL, DAY);
  assert.deepEqual(mainPillChoices("windows").map((p) => p.id), [DAY]);
});

test("an old Tokkip settings file gets the day as its main pill and keeps what still exists", () => {
  const old = { mainPill: "integration_claude", activeIntegrations: ["integration_github", "integration_vercel", "agent_codex"] };
  assert.deepEqual(sanitizeDeclared(old, "windows"), { mainPill: DAY, activeIntegrations: ["integration_github"] });
});

test("up to four pills next to the main one, never the main one itself", () => {
  let d = { mainPill: DAY, activeIntegrations: [] };
  for (const id of ["integration_github", "integration_spotify", "ai_anthropic", "ai_google"]) {
    d = { ...d, activeIntegrations: toggleDeclared(d, id, "windows") };
  }
  assert.equal(d.activeIntegrations.length, MAX_DECLARED);
  assert.equal(toggleDeclared(d, "ai_openai", "windows"), null, "a fifth one is refused");
  assert.equal(toggleDeclared(d, DAY, "windows"), null, "the main pill is never toggled");
  assert.equal(toggleDeclared(d, "agent_codex", "windows"), null, "a removed pill is never declared");
});

test("only the day can be picked as the main pill", () => {
  const d = { mainPill: DAY, activeIntegrations: ["integration_github"] };
  assert.equal(chooseMainPill(d, "integration_github", "windows"), null);
  assert.deepEqual(chooseMainPill(d, DAY, "windows"), d);
});

test("pills are ordered main first, then the catalog's order", () => {
  const tasks = ["integration_spotify", "integration_github", "ai_google", DAY].map((id) => ({ id }));
  assert.deepEqual(orderPills(tasks, DAY).map((t) => t.id), [DAY, "ai_google", "integration_github", "integration_spotify"]);
});

beforeEach(() => {
  State.settings = { ...DEFAULT_SETTINGS };
  State.tasks = [];
});

test("the main pill always loads, the declared ones join it, and focus starts on it", () => {
  State.settings = { ...DEFAULT_SETTINGS, mainPill: DAY, activeIntegrations: ["integration_spotify", "integration_github"] };
  State.loadIntegrationTasks();
  assert.deepEqual(State.tasks.map((t) => t.id), [DAY, "integration_github", "integration_spotify"]);
  assert.equal(State.focusId, DAY);
});
