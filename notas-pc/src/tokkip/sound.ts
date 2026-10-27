// Tokkip's sounds (synthesised for Tokkip, android/tools/make_sounds.py): a
// pop when you catch an idea, a page turning, the check of a step, a hello and
// the level-up jingle. Off in Ajustes.

import pop from "../assets/sounds/pop.wav";
import pageTurn from "../assets/sounds/page.wav";
import taskDone from "../assets/sounds/task_done.wav";
import greet from "../assets/sounds/greet.wav";
import levelUp from "../assets/sounds/level_up.wav";
import tick from "../assets/sounds/tick.wav";

const FILES = { pop, page: pageTurn, task_done: taskDone, greet, level_up: levelUp, tick };
export type TokkipSound = keyof typeof FILES;

const KEY = "tokkip.sounds";

export function soundsOn(): boolean {
  try {
    return localStorage.getItem(KEY) !== "off";
  } catch {
    return true;
  }
}

export function setSoundsOn(on: boolean) {
  try {
    localStorage.setItem(KEY, on ? "on" : "off");
  } catch {
    /* not kept */
  }
}

const cache = new Map<string, HTMLAudioElement>();

export function play(name: TokkipSound, volume = 0.6) {
  if (!soundsOn()) return;
  try {
    let a = cache.get(name);
    if (!a) {
      a = new Audio(FILES[name]);
      cache.set(name, a);
    }
    a.volume = volume;
    a.currentTime = 0;
    void a.play().catch(() => {});
  } catch {
    /* no audio: fine */
  }
}
