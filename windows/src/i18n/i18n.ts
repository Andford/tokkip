// Tokkip PC speaks Spanish and English, Spanish first — the way Tokkip talks
// everywhere, de tú.
//
// Texts are looked up by their English wording: `t("Allow")`,
// `t("Uploading {name}", { name })`. English is the key itself; es.json has
// the Spanish for each one, written by hand. A text missing from es.json shows
// in English (the tests make sure none is missing).
//
// The Rust side (src-tauri/src/i18n.rs) embeds the same es.json and looks up
// the same way, for the tray menu, notifications and the errors it returns.
//
// Changing the language never reloads a window: `setLanguage` tells whoever
// listens (`onLanguageChange`), views/dom.ts relabels the text it was given
// as `tl(…)`, and the views redraw the rest on their next sync.

import ES from "./es.json";

export const LANGUAGE_CODES = ["es", "en"] as const;
export type Language = (typeof LANGUAGE_CODES)[number];

/** The picker's entries, each in its own language. */
export const LANGUAGES: readonly { code: Language; name: string }[] = [
  { code: "es", name: "Español" },
  { code: "en", name: "English" },
];

export type Vars = Record<string, string | number>;
type Plural = Partial<Record<Intl.LDMLPluralRule, string>>;
type Table = Record<string, string | Plural>;

const SPANISH: Table = (ES as { strings: Table }).strings;

let current: Language = "en";
const listeners = new Set<(lang: Language) => void>();

export function isLanguage(code: unknown): code is Language {
  return typeof code === "string" && (LANGUAGE_CODES as readonly string[]).includes(code);
}

/**
 * The language to show: the one picked in Settings when it is Spanish or
 * English (an older build's other languages read as "System"), else the first
 * of the system's languages that is one of the two, else English.
 */
export function resolveLanguage(picked: string | undefined | null, system: readonly string[] = []): Language {
  if (isLanguage(picked)) return picked;
  for (const raw of system) {
    const base = String(raw ?? "").replace(/_/g, "-").toLowerCase().split("-")[0];
    if (isLanguage(base)) return base;
  }
  return "en";
}

/** The system's languages, as the webview knows them. */
export function systemLanguages(): string[] {
  if (typeof navigator === "undefined") return [];
  const list = navigator.languages?.length ? [...navigator.languages] : [];
  if (navigator.language && !list.includes(navigator.language)) list.push(navigator.language);
  return list;
}

export function language(): Language {
  return current;
}

/** Switches the language. Returns true when it changed (listeners were told). */
export function setLanguage(lang: Language): boolean {
  if (!isLanguage(lang) || lang === current) return false;
  current = lang;
  applyDocumentLanguage();
  for (const listener of [...listeners]) listener(lang);
  return true;
}

/** Called after every language change; returns the unsubscribe. */
export function onLanguageChange(listener: (lang: Language) => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** <html lang> follows the language, so the webview hyphenates and picks fonts for it. */
export function applyDocumentLanguage() {
  if (typeof document === "undefined" || !document.documentElement) return;
  document.documentElement.lang = current;
}

function interpolate(text: string, vars?: Vars): string {
  if (!vars) return text;
  return text.replace(/\{(\w+)\}/g, (m, name: string) => (name in vars ? String(vars[name]) : m));
}

function pickPlural(forms: Plural, lang: Language, count: number): string | undefined {
  let rule: Intl.LDMLPluralRule;
  try {
    rule = new Intl.PluralRules(lang).select(count);
  } catch {
    rule = count === 1 ? "one" : "other";
  }
  return forms[rule] ?? forms.other ?? Object.values(forms)[0];
}

/** The raw translation of `key` in `lang`, or undefined (English is the key). */
export function lookup(key: string, lang: Language = current): string | Plural | undefined {
  return lang === "es" ? SPANISH[key] : undefined;
}

/**
 * `key` (English text) in the current language, its `{placeholders}` filled
 * from `vars`. Falls back to the English text.
 */
export function t(key: string, vars?: Vars): string {
  const found = lookup(key);
  let text: string;
  if (typeof found === "string") text = found;
  else if (found) text = pickPlural(found, current, Number(vars?.count ?? 0)) ?? key;
  else text = key;
  return interpolate(text, vars);
}

/**
 * A count with its words: `tn("{count} file", "{count} files", n)`. The
 * Spanish forms come from the entry keyed by the English plural; `{count}` is
 * filled in.
 */
export function tn(one: string, other: string, count: number, vars?: Vars): string {
  const all = { ...vars, count };
  const found = lookup(other);
  if (found && typeof found !== "string") {
    const text = pickPlural(found, current, count);
    if (text) return interpolate(text, all);
  }
  if (typeof found === "string") return interpolate(found, all);
  return interpolate(count === 1 ? one : other, all);
}

/**
 * Marks a literal as a key without translating it, for tables built at load
 * time (pill categories, outfit names…): the code translates it with `t()`
 * where it is shown. The i18n test finds the keys through it.
 */
export function N_<T extends string>(key: T): T {
  return key;
}

/**
 * A table of labels whose every read is translated: `labels({ a: N_("Bow") }).a`
 * is "Bow" in English, "Moño" in Spanish. For tables other modules index into.
 */
export function labels<K extends string>(keys: Record<K, string>): Record<K, string> {
  const out = {} as Record<K, string>;
  for (const k of Object.keys(keys) as K[]) {
    Object.defineProperty(out, k, { enumerable: true, get: () => t(keys[k]) });
  }
  return out;
}

/**
 * A string that follows the language by itself once it is on screen. Given to
 * `h()` (views/dom.ts) as a text, a title, a placeholder or a child, it is
 * redrawn in place when the language changes.
 */
export class Msg {
  readonly key: string;
  readonly vars?: Vars;
  constructor(key: string, vars?: Vars) {
    this.key = key;
    this.vars = vars;
  }
  toString(): string {
    return t(this.key, this.vars);
  }
}

/** `t()` for text built once: see `Msg`. */
export function tl(key: string, vars?: Vars): Msg {
  return new Msg(key, vars);
}

// ── Dates ─────────────────────────────────────────────────────────────────────
// English keeps short fixed abbreviations ("Oct 9"); Spanish asks Intl ("9 oct").

const EN_MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const EN_WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

function intl(options: Intl.DateTimeFormatOptions, date: Date): string | null {
  try {
    return new Intl.DateTimeFormat(current, { ...options, timeZone: "UTC" }).format(date);
  } catch {
    return null;
  }
}

/** "Oct" for 9 (months from 0). */
export function monthShort(month: number): string {
  if (current === "en") return EN_MONTHS[month] ?? "";
  return intl({ month: "short" }, new Date(Date.UTC(2024, month, 15))) ?? EN_MONTHS[month] ?? "";
}

/** "Mon" for 1 (Sunday is 0). */
export function weekdayShort(day: number): string {
  if (current === "en") return EN_WEEKDAYS[day] ?? "";
  // 7 January 2024 was a Sunday.
  return intl({ weekday: "short" }, new Date(Date.UTC(2024, 0, 7 + day))) ?? EN_WEEKDAYS[day] ?? "";
}

/** "Oct 9" (months from 0), in the language's own order. */
export function dayMonth(month: number, day: number): string {
  if (current === "en") return `${EN_MONTHS[month] ?? ""} ${day}`;
  return intl({ month: "short", day: "numeric" }, new Date(Date.UTC(2024, month, day))) ?? `${EN_MONTHS[month] ?? ""} ${day}`;
}

/** Every key es.json has, for the tests. */
export function allKeys(): string[] {
  return Object.keys(SPANISH);
}

/** True when `key` has its Spanish. */
export function isComplete(key: string): boolean {
  return SPANISH[key] !== undefined;
}
