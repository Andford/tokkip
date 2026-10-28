// Interface language (src/i18n): Spanish and English. Lookup, fallback,
// interpolation and plurals; es.json complete, with the key's placeholders;
// every key the code uses translated and none left over; and no known
// user-facing English left outside t() in the views.

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { installFakeDom } from "./fakedom.mjs";

installFakeDom();

const {
  LANGUAGES, N_, dayMonth, isComplete, labels, language, lookup, monthShort,
  onLanguageChange, resolveLanguage, setLanguage, t, tl, tn, weekdayShort,
} = await import("../src/i18n/i18n.ts");
const { h, relabel, liveTextCount } = await import("../src/views/dom.ts");

const here = dirname(fileURLToPath(import.meta.url));
const WINDOWS = join(here, "..");
const ES = JSON.parse(readFileSync(join(WINDOWS, "src/i18n/es.json"), "utf8")).strings;

/** Runs `fn` in `lang`, then puts English back for the other tests. */
function inLanguage(lang, fn) {
  setLanguage(lang);
  try {
    fn();
  } finally {
    setLanguage("en");
  }
}

// ── Lookup ────────────────────────────────────────────────────────────────────

test("English is the key itself, Spanish comes from es.json", () => {
  assert.equal(language(), "en");
  assert.equal(t("Allow"), "Allow");
  inLanguage("es", () => {
    assert.equal(t("Allow"), "Permitir");
    assert.equal(t("Open the chat"), "Abrir el chat");
    assert.equal(t("Cancel"), "Cancelar");
  });
});

test("a string nobody translated falls back to its English text", () => {
  inLanguage("es", () => {
    assert.equal(t("Some brand new sentence."), "Some brand new sentence.");
    assert.equal(t("Hello {name}", { name: "Tokkip" }), "Hello Tokkip");
  });
});

test("placeholders are filled in both languages, unknown ones are left as they are", () => {
  assert.equal(t("Uploading {name}", { name: "a.pdf" }), "Uploading a.pdf");
  assert.equal(t("Uploading {name}"), "Uploading {name}");
  inLanguage("es", () => {
    assert.equal(t("Uploading {name}", { name: "a.pdf" }), "Subiendo a.pdf");
    assert.equal(t("{time} ago", { time: "5 min" }), "hace 5 min");
  });
  assert.equal(t("{used}/{max} slots in use — the main tool doesn't take one.", { used: 2, max: 4 }),
    "2/4 slots in use — the main tool doesn't take one.");
});

test("plurals: one and other, in both languages", () => {
  const repos = (n) => tn("{count} repo", "{count} repos", n);
  assert.equal(repos(1), "1 repo");
  assert.equal(repos(3), "3 repos");
  inLanguage("es", () => {
    assert.equal(repos(1), "1 repositorio");
    assert.equal(repos(0), "0 repositorios");
    assert.equal(repos(5), "5 repositorios");
  });
});

test("labels() tables and dates read in the current language", () => {
  const table = labels({ bow: N_("Bow"), hat: N_("Party hat") });
  assert.equal(table.bow, "Bow");
  inLanguage("es", () => {
    assert.equal(table.bow, "Moño");
    assert.deepEqual(Object.keys(table), ["bow", "hat"]);
    assert.match(monthShort(9), /^oct/);
    assert.match(dayMonth(8, 28), /^28 sept?/);
  });
  assert.equal(monthShort(9), "Oct");
  assert.equal(weekdayShort(1), "Mon");
  assert.equal(dayMonth(8, 28), "Sep 28");
});

// ── Choosing the language ─────────────────────────────────────────────────────

test("System follows the system's language when it is Spanish or English, else English", () => {
  assert.equal(resolveLanguage("", ["es-MX", "en-US"]), "es");
  assert.equal(resolveLanguage("", ["de-DE", "es-MX"]), "es");
  assert.equal(resolveLanguage("", ["en-GB"]), "en");
  assert.equal(resolveLanguage("", ["fr-FR"]), "en");
  assert.equal(resolveLanguage("", []), "en");
  // A picked language wins; one an older build offered ("fr"…) means System.
  assert.equal(resolveLanguage("en", ["es-MX"]), "en");
  assert.equal(resolveLanguage("fr", ["es-MX"]), "es");
});

test("the picker offers Spanish, then English", () => {
  assert.deepEqual(LANGUAGES.map((l) => l.code), ["es", "en"]);
});

test("a language change relabels what was built, in place, and says so once", () => {
  let told = 0;
  const off = onLanguageChange(() => told++);
  const button = h("button", { title: tl("Settings") }, tl("Cancel"));
  const label = h("span", { text: tl("Uploading {name}", { name: "x.png" }) });
  const replaced = h("span", { text: tl("Allow") });
  replaced.textContent = "dynamic text"; // some code wrote over it since
  assert.ok(liveTextCount() >= 3);

  setLanguage("es");
  assert.equal(told, 1);
  assert.equal(button.getAttribute("title"), "Ajustes");
  assert.equal(button.textContent, "Cancelar");
  assert.equal(label.textContent, "Subiendo x.png");
  assert.equal(replaced.textContent, "dynamic text");
  setLanguage("es"); // same language: nothing to do
  assert.equal(told, 1);

  setLanguage("en");
  assert.equal(button.textContent, "Cancel");
  relabel();
  assert.equal(button.getAttribute("title"), "Settings");
  off();
});

// ── es.json ───────────────────────────────────────────────────────────────────

const placeholders = (s) => [...new Set(s.match(/\{\w+\}/g) ?? [])].sort();

test("es.json: every text with the key's placeholders", () => {
  for (const [key, value] of Object.entries(ES)) {
    const forms = typeof value === "string" ? [value] : Object.values(value);
    for (const form of forms) {
      const got = placeholders(form);
      const ok = typeof value === "string"
        ? JSON.stringify(got) === JSON.stringify(placeholders(key))
        : got.every((p) => placeholders(key).includes(p));
      assert.ok(ok, `${JSON.stringify(form)} for ${JSON.stringify(key)}`);
    }
  }
});

// ── Every key the code uses is translated ─────────────────────────────────────

function files(dir, ext) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) out.push(...files(path, ext));
    else if (path.endsWith(ext)) out.push(path);
  }
  return out;
}

/** Source without comments (a doc example is not a key) or Rust test modules. */
function code(path) {
  // Windows checkouts have CRLF line endings.
  let text = readFileSync(path, "utf8").replace(/\r\n/g, "\n");
  const tests = text.indexOf("#[cfg(test)]\nmod tests");
  if (tests >= 0) text = text.slice(0, tests);
  return text.split("\n").filter((line) => !/^\s*(\/\/|\*|\/\*)/.test(line)).join("\n");
}

const STR = String.raw`"((?:[^"\\]|\\.)*)"`;
const unquote = (s) => JSON.parse(`"${s}"`);

function usedKeys() {
  const keys = new Map();
  const add = (key, path) => keys.set(key, relative(WINDOWS, path));
  for (const path of files(join(WINDOWS, "src"), ".ts")) {
    const text = code(path);
    for (const m of text.matchAll(new RegExp(String.raw`\b(?:t|tl|N_)\(\s*` + STR, "g"))) add(unquote(m[1]), path);
    // tn(one, other, n): the English plural is the key.
    for (const m of text.matchAll(new RegExp(String.raw`\btn\(\s*` + STR + String.raw`\s*,\s*` + STR, "g"))) add(unquote(m[2]), path);
  }
  for (const path of files(join(WINDOWS, "src-tauri/src"), ".rs")) {
    const text = code(path);
    for (const m of text.matchAll(new RegExp(String.raw`\b(?:t|tf|n_)\(\s*` + STR, "g"))) add(unquote(m[1]), path);
    for (const m of text.matchAll(new RegExp(String.raw`\btn\(\s*` + STR + String.raw`\s*,\s*` + STR, "g"))) add(unquote(m[2]), path);
  }
  return keys;
}

test("every string the code shows has its Spanish, and es.json has nothing more", () => {
  const keys = usedKeys();
  assert.ok(keys.size > 300, `only ${keys.size} keys found — has the scan broken?`);
  const missing = [...keys].filter(([key]) => !isComplete(key)).map(([key, path]) => `${path}: ${JSON.stringify(key)}`);
  assert.deepEqual(missing, []);
  // A key the code still writes somewhere (a table of labels, a Rust array)
  // counts as used even when t() gets it from a variable.
  const source = [...files(join(WINDOWS, "src"), ".ts"), ...files(join(WINDOWS, "src-tauri/src"), ".rs")].map(code).join("\n");
  const unused = Object.keys(ES).filter((key) => !keys.has(key) && !source.includes(JSON.stringify(key)));
  assert.deepEqual(unused, []);
});

test("lookup() answers Spanish, and nothing for English", () => {
  assert.equal(lookup("Allow", "en"), undefined);
  assert.equal(lookup("Allow", "es"), "Permitir");
  assert.equal(typeof lookup("{count} repos", "es"), "object");
});

// ── No English left outside t() ───────────────────────────────────────────────
//
// Pragmatic: a literal in the views, the settings window, the island, the
// upload canvas or the recap that is exactly a translated string, and is not
// the argument of t() / tl() / tn() / N_(), is English that would stay English.

const CHECKED = ["src/views", "src/settings", "src/island", "src/upload", "src/tokkip/wardrobe.ts", "src/main.ts"];
/** Literals that are a translated word but are values in the code, not text on screen. */
const NOT_TEXT = new Set([
  "file", // the kind of a chat context: { kind: "file" }
  "files", // the kind of a clipboard item (Tokkip)
  "finished", // a task state and a pill badge
  "unknown", // a CI state
  "Resend", // the service's name (the "Resend" button sends again)
]);

test("no known user-facing English literal outside t() in the views and settings", () => {
  const translated = new Set(
    Object.entries(ES).filter(([key, value]) => typeof value !== "string" || value !== key).map(([key]) => key),
  );
  const offenders = [];
  for (const target of CHECKED) {
    const full = join(WINDOWS, target);
    const list = statSync(full).isDirectory() ? files(full, ".ts") : [full];
    for (const path of list) {
      const text = code(path);
      for (const m of text.matchAll(new RegExp(STR, "g"))) {
        let value;
        try {
          value = unquote(m[1]);
        } catch {
          continue;
        }
        if (!translated.has(value) || NOT_TEXT.has(value)) continue;
        const before = text.slice(Math.max(0, m.index - 400), m.index);
        if (/\b(?:t|tl|N_)\(\s*$/.test(before)) continue;
        if (/\btn\(\s*"(?:[^"\\]|\\.)*"\s*,\s*$/.test(before) || /\btn\(\s*$/.test(before)) continue;
        offenders.push(`${relative(WINDOWS, path)}: ${JSON.stringify(value)}`);
      }
    }
  }
  assert.deepEqual(offenders, []);
});
