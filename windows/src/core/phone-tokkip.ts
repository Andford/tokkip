// Tokkip shared with your phone: the same species, colours and outfit on
// both, and how you feel today (from the phone's diary and sleep). Tokkip
// Phone carries a small JSON each way (Andford/tokkiphone,
// docs/protocol/tokkip.md); src-tauri/src/phone_tokkip.rs reads and writes
// the files, and src/island/phone-tokkip.ts decides who is newer.

import { parseLook, type TokkipLook } from "../tokkip/tokkip";
import { parseOutfit, type OutfitSelection } from "../tokkip/wardrobe";
import { N_ } from "../i18n/i18n";

/** Today on the phone: the diary's mood (1 bad … 5 great, 0 none) and last night's rest. */
export interface Feeling {
  /** YYYY-MM-DD, the phone's day. */
  day: string;
  mood: number;
  rest: "tired" | "ok" | "great" | "";
}

export interface SharedTokkip {
  /** When the look last changed, Unix ms: the newer side wins. */
  changedAt: number;
  look: TokkipLook;
  outfit: OutfitSelection;
  feeling: Feeling | null;
}

const DAY = /^\d{4}-\d{2}-\d{2}$/;

/** The other side's Tokkip made safe, or null when it isn't one. */
export function parseShared(raw: string | null | undefined): SharedTokkip | null {
  if (!raw) return null;
  let o: Record<string, unknown>;
  try {
    o = JSON.parse(raw);
  } catch {
    return null;
  }
  if (!o || typeof o !== "object" || o.v !== 1) return null;
  const changedAt = typeof o.changedAt === "number" && Number.isFinite(o.changedAt) ? o.changedAt : NaN;
  if (!(changedAt >= 0) || !o.look || typeof o.look !== "object") return null;
  const look = o.look as Record<string, unknown>;
  return { changedAt, look: parseLook(look), outfit: parseOutfit(look.outfit), feeling: parseFeeling(o.feeling) };
}

function parseFeeling(raw: unknown): Feeling | null {
  if (!raw || typeof raw !== "object") return null;
  const f = raw as Record<string, unknown>;
  if (typeof f.day !== "string" || !DAY.test(f.day)) return null;
  const mood = typeof f.mood === "number" && f.mood >= 1 && f.mood <= 5 ? Math.round(f.mood) : 0;
  const rest = f.rest === "tired" || f.rest === "ok" || f.rest === "great" ? f.rest : "";
  return mood === 0 && rest === "" ? null : { day: f.day, mood, rest };
}

/** This island's Tokkip, for the phone. */
export function sharedJson(look: TokkipLook, outfit: OutfitSelection, changedAt: number): string {
  return JSON.stringify({ v: 1, from: "pc", changedAt, look: { ...look, outfit } });
}

/** What tells one look from another (the feeling and the time aside). */
export function lookKey(look: TokkipLook, outfit: OutfitSelection): string {
  return JSON.stringify([look.species, look.skin, look.body, look.accent, look.ink, look.cheek, outfit]);
}

/** The phone's feeling, if it is today's. */
export function todaysFeeling(shared: SharedTokkip | null, today: string): Feeling | null {
  return shared?.feeling && shared.feeling.day === today ? shared.feeling : null;
}

/** What Tokkip says about today's feeling from the phone (an English key of src/i18n). */
export function feelingLine(f: Feeling): string {
  if (f.mood >= 1 && f.mood <= 2) return N_("Today is a hard day: I'm here with you.");
  if (f.rest === "tired") return N_("Your phone says you slept little: take it easy today.");
  if (f.mood >= 4) return N_("You're feeling good today. Me too!");
  if (f.rest === "great") return N_("You slept well: let's make the most of today.");
  return N_("Today is a calm day.");
}

/** How Tokkip reacts the first time he hears today's feeling. */
export function feelingEmote(f: Feeling): "love" | "yawn" | "happy" | null {
  if (f.mood >= 1 && f.mood <= 2) return "love";
  if (f.rest === "tired") return "yawn";
  if (f.mood >= 4 || f.rest === "great") return "happy";
  return null;
}
