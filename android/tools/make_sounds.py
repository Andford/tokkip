#!/usr/bin/env python3
"""Tokkip's own sounds, synthesised here (no samples): soft, short and cute.
Writes shared/src/main/res/raw/*.wav. Run from android/: python3 tools/make_sounds.py"""
import numpy as np, wave, os
SR = 22050
OUT = os.path.join(os.path.dirname(__file__), '..', 'shared', 'src', 'main', 'res', 'raw')
os.makedirs(OUT, exist_ok=True)

def env(n, a=0.006, d=None):
    t = np.arange(n) / SR
    att = np.clip(t / a, 0, 1)
    dec = np.exp(-t / (d if d else (n / SR) / 4))
    return att * dec

def tone(f0, f1, dur, shape="sine", d=None, vib=0.0):
    n = int(SR * dur)
    t = np.arange(n) / SR
    f = f0 * (f1 / f0) ** (t / dur)
    if vib: f = f * (1 + vib * np.sin(2 * np.pi * 9 * t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    if shape == "sine": w = np.sin(ph)
    elif shape == "tri": w = 2 / np.pi * np.arcsin(np.sin(ph))
    else: w = 0.6 * np.sin(ph) + 0.25 * np.sin(2 * ph) + 0.1 * np.sin(3 * ph)  # "soft"
    return w * env(n, d=d)

def seq(*parts, gap=0.02):
    out = []
    for p in parts:
        out.append(p); out.append(np.zeros(int(SR * gap)))
    return np.concatenate(out)

def mix(*xs):
    n = max(len(x) for x in xs); o = np.zeros(n)
    for x in xs: o[:len(x)] += x
    return o

def noise(dur, lo=0.0, d=0.03):
    n = int(SR * dur); w = np.random.RandomState(3).uniform(-1, 1, n)
    w = np.convolve(w, np.ones(6) / 6, mode="same")
    return w * env(n, a=0.002, d=d)

def save(name, x, vol=0.55):
    x = x / (np.max(np.abs(x)) + 1e-9) * vol
    tail = np.linspace(1, 0, min(len(x), 200)); x[-len(tail):] *= tail
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())

C5, E5, G5, A5, C6, D6, E6, G6 = 523.3, 659.3, 784.0, 880.0, 1046.5, 1174.7, 1318.5, 1568.0
save("pop", tone(700, 1500, 0.09, "sine", d=0.025))
save("blip", tone(1300, 1100, 0.06, "tri", d=0.02), 0.4)
save("tick", tone(2200, 2000, 0.03, "sine", d=0.008), 0.35)
save("open", seq(tone(G5, G5, 0.07, "soft"), tone(C6, C6, 0.12, "soft"), gap=0.01))
save("close", seq(tone(C6, C6, 0.07, "soft"), tone(G5, G5, 0.12, "soft"), gap=0.01))
save("approve", seq(tone(E5, E5, 0.08, "soft"), tone(G5, G5, 0.08, "soft"), tone(C6, C6, 0.2, "soft", d=0.08), gap=0.005))
save("approval", seq(tone(C6, C6, 0.07, "soft"), tone(E6, E6, 0.18, "soft", d=0.07), gap=0.005))
save("finish", seq(tone(C5, C5, 0.09, "soft"), tone(E5, E5, 0.09, "soft"), tone(G5, G5, 0.09, "soft"), tone(C6, C6, 0.3, "soft", d=0.12), gap=0.01))
save("greet", seq(tone(900, 1500, 0.1, "tri", d=0.05), tone(1100, 1700, 0.14, "tri", d=0.06), gap=0.04))
save("love", seq(tone(G5, G5, 0.12, "sine", d=0.06, vib=0.01), tone(E6, E6, 0.26, "sine", d=0.12, vib=0.012), gap=0.02))
save("proud", seq(tone(C6, C6, 0.06, "soft"), tone(E6, E6, 0.06, "soft"), tone(G6, G6, 0.22, "sine", d=0.1), gap=0.0))
save("wink", tone(1600, 2600, 0.08, "sine", d=0.03))
save("peek", tone(800, 1400, 0.12, "tri", d=0.05))
save("question", tone(700, 1250, 0.2, "tri", d=0.09))
save("annoyed", seq(tone(500, 380, 0.1, "soft", d=0.05), tone(470, 350, 0.14, "soft", d=0.06), gap=0.03))
save("error", seq(tone(420, 400, 0.12, "soft"), tone(320, 300, 0.18, "soft"), gap=0.03))
save("dizzy", tone(1400, 400, 0.6, "tri", d=0.3, vib=0.06))
save("slap", mix(noise(0.08, d=0.02), tone(260, 160, 0.09, "sine", d=0.03)), 0.6)
save("gulp", tone(300, 650, 0.14, "sine", d=0.06))
save("work", seq(tone(A5, A5, 0.06, "soft"), tone(D6, D6, 0.14, "soft", d=0.06), gap=0.02))
save("sleep", tone(600, 300, 0.7, "sine", d=0.35), 0.35)
save("yawn", tone(500, 260, 0.6, "soft", d=0.3, vib=0.02), 0.4)
print("ok")

# ── Section sounds: a little flute for Respirar, a music box for Sueño ──────
def flute(f0, f1, dur, vol=1.0):
    """A soft breathy flute note gliding from f0 to f1, swelling in and out."""
    n = int(SR * dur); t = np.arange(n) / SR
    glide = f0 * (f1 / f0) ** (np.clip(t / dur, 0, 1) ** 0.8)
    vib = 1 + 0.006 * np.sin(2 * np.pi * 5.2 * t) * np.clip((t - .15) / .3, 0, 1)
    ph = 2 * np.pi * np.cumsum(glide * vib) / SR
    tone_ = np.sin(ph) + 0.18 * np.sin(2 * ph) + 0.06 * np.sin(3 * ph)
    breath = np.random.RandomState(5).normal(0, 1, n)
    breath = np.convolve(breath, np.ones(4) / 4, mode="same") * 0.12
    swell = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.2
    return (tone_ + breath) * swell * vol

# Entering Respirar: Tokkip breathes in (rising) and out (falling).
save("flute_sigh", np.concatenate([flute(G5 / 2 * 2, C6, 0.95), np.zeros(int(SR * .05)), flute(C6, E5, 1.35)]), 0.4)
# During a session, a quiet note for each turn.
save("breath_in", flute(E5, A5, 1.1), 0.28)
save("breath_out", flute(A5, D5 if False else 587.3, 1.4), 0.25)

def musicbox(f, dur=1.2):
    n = int(SR * dur); t = np.arange(n) / SR
    s = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * f * 3.01 * t) * np.exp(-t * 9) + 0.15 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t * 14)
    return s * np.exp(-t * 3.2) * np.clip(t / .003, 0, 1)

def notes(seq_, step):
    total = int(SR * (step * len(seq_) + 1.4)); o = np.zeros(total)
    for i, f in enumerate(seq_):
        s = musicbox(f); a = int(SR * step * i); o[a:a + len(s)] += s
    return o

# Entering Sueño: a little lullaby on a music box.
save("lullaby", notes([G6, E6, C6, D6, G5 * 2 * .75 if False else 1174.7 * .75 * 1.333, C6], 0.2), 0.38)
# Entering Diario: a soft pluck, like turning a page.
save("page", notes([C6, G6], 0.08), 0.3)
# Entering Rachas: a twinkle.
save("spark", notes([E6, G6, C6 * 2], 0.06), 0.3)
print("ok+")

# ── More section sounds: Enfoque, Tareas, Dinero and a Game Boy-ish Pantalla ──
def kalimba(f, dur=0.9):
    """A thumb piano: a warm tone with a quick metallic overtone."""
    n = int(SR * dur); t = np.arange(n) / SR
    s = np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * f * 6.1 * t) * np.exp(-t * 30) + 0.12 * np.sin(2 * np.pi * f * 2 * t) * np.exp(-t * 6)
    return s * np.exp(-t * 4.5) * np.clip(t / .002, 0, 1)

def at(parts, total):
    o = np.zeros(int(SR * total))
    for start, s in parts:
        a = int(SR * start); b = min(len(o), a + len(s)); o[a:b] += s[:b - a]
    return o

def square(f, dur, duty=0.25, d=None, slide=None):
    """A chiptune pulse wave, like an old handheld's."""
    n = int(SR * dur); t = np.arange(n) / SR
    f_ = f if slide is None else f * (slide / f) ** (t / dur)
    ph = np.cumsum(np.full(n, 1.0) * f_) / SR % 1.0
    w = np.where(ph < duty, 1.0, -1.0)
    e = np.exp(-t / d) if d else np.ones(n)
    return w * e * np.clip(t / .002, 0, 1)

# Enfoque: start (two kalimba notes up) and break (down).
save("focus_start", at([(0, kalimba(D6 / 2 * 2 * 0.75 if False else 880.0)), (0.11, kalimba(1318.5))], 1.1), 0.42)
save("focus_break", at([(0, kalimba(1318.5)), (0.11, kalimba(988.0)), (0.22, kalimba(784.0))], 1.2), 0.4)
# Tareas: a bright check — a pluck that rises, with a tiny sparkle.
save("task_done", at([(0, tone(900, 1700, 0.07, "tri", d=0.03)), (0.06, musicbox(1568.0, 0.5) * 0.6), (0.1, musicbox(2093.0, 0.45) * 0.4)], 0.6), 0.42)
# Dinero: a coin for spending, a little cash register for money coming in.
save("coin", at([(0, square(988.0, 0.07, 0.5, d=0.05)), (0.06, square(1318.5, 0.32, 0.5, d=0.12))], 0.42), 0.3)
save("cash", at([(0, square(1318.5, 0.06, 0.5, d=0.04)), (0.06, square(1568.0, 0.06, 0.5, d=0.04)), (0.12, square(2093.0, 0.3, 0.5, d=0.12)),
                 (0.0, noise(0.05, d=0.015) * 0.4), (0.13, musicbox(2637.0, 0.4) * 0.5)], 0.5), 0.3)
# Pantalla: an original 8-bit jingle on pulse waves — "on" when you open it,
# "level up" for a calm day, "warn" when Tokkip gets dizzy.
save("gb_on", at([(0, square(523.3, 0.08, 0.125, d=0.06)), (0.09, square(784.0, 0.08, 0.125, d=0.06)), (0.18, square(1046.5, 0.08, 0.25, d=0.06)),
                  (0.27, square(1568.0, 0.35, 0.25, d=0.14))], 0.7), 0.26)
save("gb_level", at([(i * 0.07, square(f, 0.07, 0.25, d=0.05)) for i, f in enumerate([659.3, 784.0, 988.0, 1318.5])] + [(0.28, square(1568.0, 0.3, 0.5, d=0.12))], 0.65), 0.26)
save("gb_warn", at([(0, square(784.0, 0.1, 0.5, d=0.08)), (0.12, square(587.3, 0.1, 0.5, d=0.08)), (0.24, square(392.0, 0.3, 0.5, d=0.12, slide=330.0))], 0.6), 0.26)
print("ok++")

# ── Notification tones, one per kind (a bit longer and clearer than the taps) ──
C7, E7, G7 = 2093.0, 2637.0, 3136.0
save("notif_morning", at([(0, kalimba(1046.5, 1.2)), (0.13, kalimba(1318.5, 1.2)), (0.26, kalimba(1568.0, 1.3)), (0.42, musicbox(C7, 1.0) * 0.5)], 1.6), 0.6)
save("notif_event", at([(0, musicbox(1760.0, 1.2)), (0.22, musicbox(1318.5, 1.3)), (0.7, musicbox(1760.0, 0.9) * 0.45), (0.9, musicbox(1318.5, 0.9) * 0.45)], 1.9), 0.6)
save("notif_task", at([(0, tone(900, 1800, 0.08, "tri", d=0.04)), (0.07, musicbox(1568.0, 0.8)), (0.15, musicbox(C7, 0.9) * 0.7)], 1.1), 0.58)
save("notif_streak", at([(i * 0.07, musicbox(f, 0.7) * (0.9 - i * 0.1)) for i, f in enumerate([1318.5, 1568.0, C7, E7])] + [(0.3, tone(1800, 3200, 0.12, "sine", d=0.05) * 0.4)], 1.1), 0.55)
save("notif_money", at([(0, square(988.0, 0.08, 0.5, d=0.06)), (0.08, square(1318.5, 0.25, 0.5, d=0.1)), (0.34, square(1568.0, 0.07, 0.5, d=0.05)),
                        (0.41, square(2093.0, 0.35, 0.5, d=0.14)), (0.42, musicbox(2637.0, 0.6) * 0.5)], 1.0), 0.36)
save("notif_screen", at([(i * 0.09, square(f, 0.09, 0.25, d=0.07)) for i, f in enumerate([1046.5, 784.0, 1046.5, 1318.5])] + [(0.38, square(1046.5, 0.4, 0.125, d=0.15))], 0.9), 0.32)
save("notif_focus", at([(0, kalimba(1318.5)), (0.16, kalimba(1318.5)), (0.32, kalimba(1318.5)), (0.5, kalimba(1760.0, 1.1))], 1.6), 0.6)
save("notif_night", at([(0, musicbox(1568.0, 1.3)), (0.32, musicbox(1318.5, 1.3)), (0.64, musicbox(1046.5, 1.6))], 2.2), 0.45)
# Level up: a music-box arpeggio, an 8-bit sparkle on top, and a puff of confetti.
save("level_up", at([(i * 0.1, musicbox(f, 1.1)) for i, f in enumerate([1046.5, 1318.5, 1568.0, C7])]
                    + [(0.42, square(E7 / 2, 0.06, 0.25, d=0.04) * 0.5), (0.48, square(G7 / 2, 0.06, 0.25, d=0.04) * 0.5), (0.54, square(C7, 0.3, 0.25, d=0.12) * 0.5),
                       (0.4, noise(0.12, d=0.04) * 0.5), (0.44, kalimba(E7, 1.0) * 0.6)], 1.7), 0.6)
print("ok+++")

# ── For Tokkip PC: the island's little taps (attach a file, hover, send, think…)
# and the greeting when it opens. Tokkip's own, like the rest.
save("attach", seq(tone(1200, 1500, 0.05, "tri", d=0.02), tone(1500, 1900, 0.07, "tri", d=0.03), gap=0.01), 0.4)
save("hover", tone(1800, 1900, 0.025, "sine", d=0.008), 0.18)
save("send", tone(600, 1800, 0.14, "sine", d=0.05), 0.42)
save("search", seq(tone(1300, 1300, 0.05, "soft", d=0.03), tone(1700, 1700, 0.08, "soft", d=0.04), gap=0.02), 0.35)
save("rate", seq(tone(E5, E5, 0.06, "soft"), tone(A5, A5, 0.06, "soft"), tone(E6, E6, 0.16, "sine", d=0.07), gap=0.0), 0.45)
save("think", seq(tone(880, 940, 0.09, "sine", d=0.05, vib=0.01), tone(990, 1050, 0.09, "sine", d=0.05, vib=0.01), tone(1100, 1180, 0.12, "sine", d=0.06, vib=0.01), gap=0.05), 0.35)
save("greeting", at([(0, kalimba(784.0)), (0.12, kalimba(1046.5)), (0.24, kalimba(1318.5)), (0.36, tone(1500, 2400, 0.12, "tri", d=0.05) * 0.5)], 1.0), 0.5)
print("ok pc")
