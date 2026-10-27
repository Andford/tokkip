// Older versions kept a few things in this window's storage under the app's
// first name (the wellness tasks, the Pomodoro, the last tab). They move once
// to Tokkip's own keys; nothing is written under the old names again.

const OLD_PREFIXES = ["coucou.", "mochi."];

export function moveOldStorage(storage: Storage | undefined = globalThis.localStorage): number {
  if (!storage) return 0;
  let moved = 0;
  try {
    const keys: string[] = [];
    for (let i = 0; i < storage.length; i++) {
      const k = storage.key(i);
      if (k && OLD_PREFIXES.some((p) => k.startsWith(p))) keys.push(k);
    }
    for (const k of keys) {
      const p = OLD_PREFIXES.find((x) => k.startsWith(x))!;
      const next = "tokkip." + k.slice(p.length);
      const v = storage.getItem(k);
      if (v !== null && storage.getItem(next) === null) {
        storage.setItem(next, v);
        moved++;
      }
      storage.removeItem(k);
    }
  } catch {
    /* storage unavailable: nothing to move */
  }
  return moved;
}

moveOldStorage();
