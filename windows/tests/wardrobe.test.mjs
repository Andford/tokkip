// Tokkip's wardrobe: the seasons that dress him ("auto") and the stored choice
// (src/tokkip/wardrobe.ts).

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  OUTFIT_SELECTIONS, OUTFIT_LABELS, SeasonCache, parseOutfit, resolveOutfit, seasonalOutfit, wardrobeHeader,
} from "../src/tokkip/wardrobe.ts";
import { DEFAULT_SETTINGS } from "../src/core/state.ts";

/** A local calendar day, at noon so no time zone can push it into the next one. */
const day = (y, m, d) => new Date(y, m - 1, d, 12);
const season = (y, m, d) => seasonalOutfit(day(y, m, d));

test("a year of seasons", () => {
  const year = [
    [1, 1, "partyHat"], [1, 2, "partyHat"], [1, 3, "crown"], [1, 6, "crown"], [1, 7, "beanie"],
    [2, 9, "beanie"], [2, 10, "hearts"], [2, 14, "hearts"], [2, 15, "none"],
    [3, 19, "none"], [3, 20, "flowerCrown"], [4, 30, "flowerCrown"], [5, 1, "none"],
    [6, 20, "none"], [6, 21, "sunglasses"], [8, 31, "sunglasses"],
    [9, 1, "tricolor"], [9, 16, "tricolor"], [9, 17, "none"],
    [10, 14, "none"], [10, 15, "witchHat"], [10, 31, "witchHat"],
    [11, 1, "marigolds"], [11, 2, "marigolds"], [11, 3, "scarf"], [11, 30, "scarf"],
    [12, 1, "antlers"], [12, 15, "antlers"], [12, 16, "santaHat"], [12, 30, "santaHat"], [12, 31, "partyHat"],
  ];
  for (const [m, d, outfit] of year) assert.equal(season(2026, m, d), outfit, `${d}/${m}`);
});

test("midnight and late evening count as the same day", () => {
  assert.equal(seasonalOutfit(new Date(2026, 10, 1, 0, 0)), "marigolds");
  assert.equal(seasonalOutfit(new Date(2026, 9, 31, 23, 59)), "witchHat");
});

test("auto dresses for the season, any other choice is worn as is", () => {
  const d = day(2026, 10, 27);
  assert.equal(resolveOutfit("auto", d), "witchHat");
  assert.equal(resolveOutfit("none", d), "none");
  assert.equal(resolveOutfit("headphones", d), "headphones");
});

test("every choice has a name, and every season's outfit is in the wardrobe", () => {
  for (const sel of OUTFIT_SELECTIONS) assert.ok(OUTFIT_LABELS[sel], `${sel} has a label`);
  for (let m = 1; m <= 12; m++) {
    for (let d = 1; d <= 31; d++) {
      const o = seasonalOutfit(new Date(2026, m - 1, d, 12));
      assert.ok(OUTFIT_SELECTIONS.includes(o), o);
    }
  }
});

test("a stored choice is read back as it was saved", () => {
  for (const sel of OUTFIT_SELECTIONS) assert.equal(parseOutfit(sel), sel);
});

test("outfits that are gone, unknown or missing mean auto", () => {
  for (const raw of ["pumpkin", "bunnyEars", "topHat", "totallyUnknown", "", "Beanie"]) {
    assert.equal(parseOutfit(raw), "auto", JSON.stringify(raw));
  }
  for (const raw of [undefined, null, 42, true, {}, ["beanie"]]) {
    assert.equal(parseOutfit(raw), "auto", String(raw));
  }
});

test("preferences from before the wardrobe start on auto", () => {
  assert.equal(DEFAULT_SETTINGS.tokkipOutfit, "auto");
  const merged = { ...DEFAULT_SETTINGS, ...{ soundEnabled: false } };
  assert.equal(parseOutfit(merged.tokkipOutfit), "auto");
});

test("the wardrobe's title names the outfit under the pointer, else the one chosen", () => {
  const d = day(2026, 10, 27);
  assert.equal(wardrobeHeader(null, "auto", d), "Auto · Witch hat");
  assert.equal(wardrobeHeader(null, "crown", d), "Crown");
  assert.equal(wardrobeHeader("auto", "crown", d), "Auto · follows the seasons (now: Witch hat)");
  assert.equal(wardrobeHeader("scarf", "auto", d), "Scarf");
  assert.equal(wardrobeHeader(null, "auto", day(2026, 5, 10)), "Auto · None");
});

test("the season asked every frame follows the calendar day", () => {
  const cache = new SeasonCache();
  assert.equal(cache.get("auto", day(2026, 10, 14)), "none");
  assert.equal(cache.get("auto", new Date(2026, 9, 15, 0, 0, 1)), "witchHat");
  assert.equal(cache.get("bow", day(2026, 10, 15)), "bow");
  assert.equal(cache.get("auto", day(2026, 12, 20)), "santaHat");
});
