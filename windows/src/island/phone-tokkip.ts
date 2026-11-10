// Keeps this island's Tokkip and the phone's the same (src/core/phone-tokkip.ts).
// Every few seconds: if the phone's Tokkip changed after ours, ours becomes
// his; if ours changed (here, in Settings or in the wardrobe), the phone gets
// it. Today's feeling from the phone shows in the Wellness card, and Tokkip
// reacts to it once a day.

import { Bridge } from "../core/bridge";
import { State } from "../core/state";
import { dayKey } from "../core/wellness";
import { feelingEmote, lookKey, parseShared, sharedJson, todaysFeeling, type Feeling } from "../core/phone-tokkip";
import { loadLook, saveLook } from "../tokkip/tokkip";
import { parseOutfit } from "../tokkip/wardrobe";
import type { BotEmoteName } from "../core/layout";

export interface PhoneTokkipHost {
  emote(name: BotEmoteName): void;
  applySettings(): void;
}

const STORE = { changedAt: "tokkip.look.changedAt", reacted: "tokkip.phone.reactedDay" };

/** Today's feeling from the phone, for the Wellness card (null when there's none). */
export const PhoneTokkip: { feeling: Feeling | null } = { feeling: null };

let host: PhoneTokkipHost | null = null;
let lastKey: string | null = null;

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}
function write(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* private mode: it just isn't kept */
  }
}

export function registerPhoneTokkip(h: PhoneTokkipHost) {
  host = h;
  setInterval(() => void sync(), 5_000);
  void sync();
}

async function sync() {
  let changedAt = Number(read(STORE.changedAt) ?? "0") || 0;
  const phone = parseShared(await Bridge.phoneTokkip().catch(() => null));

  // The phone's look is newer: it becomes ours.
  if (phone && phone.changedAt > changedAt) {
    saveLook(phone.look);
    if (parseOutfit(State.settings.tokkipOutfit) !== phone.outfit) {
      State.settings.tokkipOutfit = phone.outfit;
      void Bridge.saveSettings(State.settings);
      host?.applySettings();
    }
    changedAt = phone.changedAt;
    write(STORE.changedAt, String(changedAt));
    lastKey = null;
  }

  // Ours changed since we last told the phone: tell it, with the time it changed.
  const look = loadLook();
  const outfit = parseOutfit(State.settings.tokkipOutfit);
  const key = lookKey(look, outfit);
  if (key !== lastKey) {
    const fromPhone = phone != null && lookKey(phone.look, phone.outfit) === key;
    if (lastKey != null && !fromPhone) {
      changedAt = Date.now();
      write(STORE.changedAt, String(changedAt));
    }
    lastKey = key;
    void Bridge.setPcTokkip(sharedJson(look, outfit, changedAt)).catch(() => {});
  }

  // How you feel today, from the phone.
  const today = dayKey(Date.now());
  const feeling = todaysFeeling(phone, today);
  const changed = JSON.stringify(feeling) !== JSON.stringify(PhoneTokkip.feeling);
  PhoneTokkip.feeling = feeling;
  if (feeling && read(STORE.reacted) !== today && !State.paused) {
    write(STORE.reacted, today);
    const emote = feelingEmote(feeling);
    if (emote) host?.emote(emote);
  }
  if (changed) State.notify();
}
