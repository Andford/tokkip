// The little Tokkips in the pills and the compact island's grid. Each canvas
// has its own TokkipMotion; the island's frame loop ticks every live one.

import { TokkipMotion, hexToRGB } from "./motion";
import type { AgentTask } from "../core/state";

interface MiniBot {
  canvas: HTMLCanvasElement;
  motion: TokkipMotion;
  cssSize: number;
  taskId: string;
  /** Asked every frame: whether this one dances (the music pill's). */
  dancing?: () => boolean;
}

const live = new Map<HTMLCanvasElement, MiniBot>();

/**
 * A mini Tokkip whose body is `bodySize` CSS pixels across. The motion engine
 * draws the body at 60 % of its canvas, so the canvas is `bodySize / 0.6`,
 * centred on a `bodySize` slot and spilling out of it.
 */
export function createMiniBot(task: AgentTask, bodySize: number, dancing?: () => boolean): HTMLElement {
  const slot = document.createElement("span");
  slot.className = "mini";
  slot.style.width = `${bodySize}px`;
  slot.style.height = `${bodySize}px`;

  const canvas = document.createElement("canvas");
  const size = bodySize / 0.6;
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  canvas.width = Math.round(size * dpr);
  canvas.height = Math.round(size * dpr);
  canvas.style.width = `${size}px`;
  canvas.style.height = `${size}px`;
  slot.append(canvas);

  const motion = new TokkipMotion();
  motion.isMini = true;
  motion.bodyColor = hexToRGB(task.color);
  motion.setState(task.state, true);
  if (task.emote) motion.setPermanentEmote(task.emote);
  if (task.miniEye) {
    motion.permanentEye = task.miniEye;
    motion.eyeOverride = task.miniEye;
    motion.eyeOverrideUntil = Number.POSITIVE_INFINITY;
  }

  live.set(canvas, { canvas, motion, cssSize: size, taskId: task.id, dancing });
  return slot;
}

export function releaseMiniBot(canvas: HTMLCanvasElement) {
  live.delete(canvas);
}

/** Forgets every canvas no longer in the document (views are rebuilt wholesale). */
export function pruneMiniBots() {
  for (const [canvas] of live) {
    if (!canvas.isConnected) live.delete(canvas);
  }
}

export function syncMiniBotStates(tasks: AgentTask[]) {
  for (const mb of live.values()) {
    const task = tasks.find((t) => t.id === mb.taskId);
    if (!task) continue;
    mb.motion.setState(task.state);
    mb.motion.bodyColor = hexToRGB(task.color);
  }
}

export function tickMiniBots(dt: number) {
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  for (const mb of live.values()) {
    const ctx = mb.canvas.getContext("2d");
    if (!ctx) continue;
    mb.motion.setDancing(mb.dancing?.() ?? false);
    mb.motion.update(dt);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, mb.cssSize, mb.cssSize);
    ctx.save();
    mb.motion.applyDance(ctx, mb.cssSize, mb.cssSize);
    mb.motion.draw(ctx, mb.cssSize, mb.cssSize);
    ctx.restore();
  }
}

export const miniBotCount = () => live.size;
