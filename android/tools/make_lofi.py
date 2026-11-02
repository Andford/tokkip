#!/usr/bin/env python3
"""Tokkip's lo-fi wake-up tunes, synthesised here (no samples): an electric
piano on jazzy chords, a lazy beat, vinyl crackle and a kalimba melody on top.
Each one loops seamlessly (every tail wraps round to the start).
Writes shared/src/main/res/raw/wake_lofi_*.wav. Run from android/: python3 tools/make_lofi.py"""
import numpy as np, wave, os

SR = 22050
OUT = os.path.join(os.path.dirname(__file__), '..', 'shared', 'src', 'main', 'res', 'raw')
rng = np.random.default_rng(7)  # the same files every run


def hz(n):
    """MIDI note to Hz."""
    return 440.0 * 2 ** ((n - 69) / 12)


def place(o, start, s):
    idx = (int(SR * start) + np.arange(len(s))) % len(o)
    np.add.at(o, idx, s)


def lowpass(x, cut):
    a = np.exp(-2 * np.pi * cut / SR)
    y = np.empty_like(x); acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc; y[i] = acc
    return y


def rhodes(f, dur):
    """A warm electric piano: sine body, a soft bell on the attack, slow tremolo."""
    t = np.arange(int(SR * dur)) / SR
    s = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t * 3) + 0.2 * np.sin(2 * np.pi * 7 * f * t) * np.exp(-t * 25)
    return s * np.exp(-t * 0.9) * np.clip(t / .01, 0, 1) * (1 - 0.18 * (1 - np.cos(2 * np.pi * 4.2 * t)) / 2)


def kalimba(f, dur=1.2):
    t = np.arange(int(SR * dur)) / SR
    s = np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * f * 6.1 * t) * np.exp(-t * 30) + 0.12 * np.sin(2 * np.pi * f * 2 * t) * np.exp(-t * 6)
    return s * np.exp(-t * 4) * np.clip(t / .002, 0, 1)


def bass(f, dur):
    t = np.arange(int(SR * dur)) / SR
    return np.sin(2 * np.pi * f * t) * np.exp(-t * 1.6) * np.clip(t / .02, 0, 1)


def kick():
    t = np.arange(int(SR * 0.35)) / SR
    f = 50 + 70 * np.exp(-t * 30)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9)


def brush(dur=0.18, cut=3500):
    n = int(SR * dur); t = np.arange(n) / SR
    return lowpass(rng.standard_normal(n), cut) * np.exp(-t * 22)


def hat():
    n = int(SR * 0.05); t = np.arange(n) / SR
    x = rng.standard_normal(n); x = x - lowpass(x, 6000)
    return x * np.exp(-t * 80)


def crackle(total):
    o = np.zeros(int(SR * total))
    hiss = lowpass(rng.standard_normal(len(o)), 2500) * 0.012
    for p in rng.integers(0, len(o), int(total * 7)):
        o[p] += rng.uniform(-1, 1) * 0.25
    return lowpass(o, 4000) + hiss


def rain(total):
    x = rng.standard_normal(int(SR * total))
    return (lowpass(x, 1800) - lowpass(x, 300)) * 0.18


def bird(f):
    t = np.arange(int(SR * 0.16)) / SR
    sweep = f * (1 + 0.35 * np.sin(2 * np.pi * 9 * t))
    return np.sin(2 * np.pi * np.cumsum(sweep) / SR) * np.sin(np.pi * t / 0.16) ** 2


def song(name, bpm, chords, roots, melody, extra=None, vol=0.6):
    """chords: four voicings (MIDI notes), one per bar; melody: (beat, MIDI) over 16 beats."""
    beat = 60 / bpm; total = 16 * beat
    o = np.zeros(int(SR * total))
    for bar, (ch, root) in enumerate(zip(chords, roots)):
        t0 = bar * 4 * beat
        for k, n in enumerate(ch):  # a slow strum
            place(o, t0 + k * 0.025, rhodes(hz(n), 4 * beat + 0.6) * 0.22)
        place(o, t0 + 2.5 * beat, rhodes(hz(ch[-1]), 1.2) * 0.08)  # a little push
        place(o, t0, bass(hz(root), 2 * beat) * 0.55)
        place(o, t0 + 2.5 * beat, bass(hz(root + 7), 1.5 * beat) * 0.4)
    for b in range(16):  # swung, lazy beat: kick 1 and the "and" of 3, brush on 2 and 4
        t = b * beat
        if b % 4 == 0: place(o, t, kick() * 0.7)
        if b % 4 == 2: place(o, t + beat * 0.55, kick() * 0.45)
        if b % 2 == 1: place(o, t, brush() * 0.35)
        place(o, t, hat() * 0.18); place(o, t + beat * 0.62, hat() * 0.11)
    for at_beat, n in melody:
        place(o, at_beat * beat, kalimba(hz(n)) * 0.32)
    if extra is not None:
        extra(o, total)
    o = lowpass(o, 5200) + crackle(total)
    o = o / (np.max(np.abs(o)) + 1e-9) * vol
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((o * 32767).astype(np.int16).tobytes())
    print(name, f"{total:.1f}s")


# Café: Dm9 – G13 – Cmaj9 – A7, a slow morning coffee.
song("wake_lofi_cafe", 74,
     [[50, 53, 57, 60, 64], [43, 53, 57, 59, 64], [48, 52, 55, 59, 62], [45, 49, 55, 59, 64]], [38, 31, 36, 33],
     [(0, 76), (1.5, 74), (2, 72), (4, 74), (5.5, 71), (8, 72), (9, 74), (10, 76), (12, 73), (13.5, 76), (14, 79)])

# Lluvia: Fmaj7 – Em7 – Dm7 – Cmaj7, with rain on the window.
song("wake_lofi_lluvia", 70,
     [[41, 52, 57, 60, 64], [40, 50, 55, 59, 62], [38, 48, 53, 57, 60], [36, 47, 52, 55, 59]], [29, 28, 26, 24],
     [(0, 72), (2, 69), (3, 67), (4, 71), (6, 67), (8, 69), (9.5, 65), (12, 67), (13, 71), (14, 72)],
     extra=lambda o, total: o.__iadd__(rain(total)))

# Amanecer: Cmaj7 – Am9 – Fmaj7 – G6, brighter, the sun coming up.
song("wake_lofi_amanecer", 82,
     [[48, 55, 59, 64, 67], [45, 55, 59, 60, 64], [41, 52, 57, 60, 64], [43, 52, 55, 59, 62]], [36, 33, 29, 31],
     [(0, 72), (1, 76), (2, 79), (4, 81), (5, 79), (6, 76), (8, 77), (9, 76), (10, 72), (12, 74), (13, 79), (14, 83)])


def birds(o, total):
    for t, f in [(0.7, 3200), (0.95, 3600), (5.3, 2900), (5.5, 3400), (5.7, 3100), (9.1, 3700), (12.4, 3000), (12.6, 3500)]:
        place(o, t, bird(f) * 0.12)


# Jardín: Ebmaj7 – Cm9 – Abmaj7 – Bb7sus, with birds in the garden.
song("wake_lofi_jardin", 78,
     [[51, 55, 58, 62, 67], [48, 55, 58, 62, 63], [44, 55, 60, 63, 67], [46, 51, 56, 60, 63]], [39, 36, 32, 34],
     [(0, 75), (1, 79), (2, 82), (4, 79), (6, 77), (8, 75), (9, 74), (10, 79), (12, 77), (13.5, 75), (14, 74)],
     extra=birds)
