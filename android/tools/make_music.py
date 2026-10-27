#!/usr/bin/env python3
"""Tokkip's welcome tune: an original, gentle lo-fi bossa (e-piano, soft bass,
vibes, brushes) that plays the first time someone opens the app. Synthesised
here from scratch. Writes shared/src/main/res/raw/welcome_lofi.ogg
(needs ffmpeg with libvorbis).  Run from android/: python3 tools/make_music.py"""
import numpy as np, os, subprocess, wave, tempfile
SR = 32000
BPM = 92
BEAT = 60 / BPM
BAR = 4 * BEAT
OUT = os.path.join(os.path.dirname(__file__), '..', 'shared', 'src', 'main', 'res', 'raw', 'welcome_lofi.ogg')
rng = np.random.RandomState(11)

def hz(n): return 440 * 2 ** ((n - 69) / 12)
NOTE = {k: v for v, k in enumerate("C C# D D# E F F# G G# A A# B".split())}
def m(name, octave): return 12 * (octave + 1) + NOTE[name]

# Eight bars, two chords each (root, chord tones as MIDI).
PROG = [
    ("F", [m("F",3), m("A",3), m("C",4), m("E",4)]), ("F", [m("F",3), m("A",3), m("C",4), m("E",4)]),
    ("E", [m("E",3), m("G",3), m("B",3), m("D",4)]), ("A", [m("A",3), m("C#",4), m("E",4), m("G",4)]),
    ("D", [m("D",3), m("F",3), m("A",3), m("C",4)]), ("D", [m("D",3), m("F",3), m("A",3), m("C",4)]),
    ("C", [m("C",3), m("Eb" if False else "D#",3), m("G",3), m("A#",3)]), ("F", [m("F",3), m("A",3), m("C",4), m("D#",4)]),
    ("A#", [m("A#",2), m("D",3), m("F",3), m("A",3)]), ("A#", [m("A#",2), m("D",3), m("F",3), m("A",3)]),
    ("A#", [m("A#",2), m("C#",3), m("F",3), m("G",3)]), ("A#", [m("A#",2), m("C#",3), m("F",3), m("G",3)]),
    ("A", [m("A",2), m("C",3), m("E",3), m("G",3)]), ("D", [m("D",3), m("F#",3), m("A",3), m("C",4)]),
    ("G", [m("G",2), m("A#",2), m("D",3), m("F",3)]), ("C", [m("C",3), m("E",3), m("G",3), m("A#",3)]),
]
ROOT_OCT = {"F": 2, "E": 2, "A": 1, "D": 2, "C": 2, "A#": 1, "G": 1}

# An original melody, (beat offset in the 8-bar loop, MIDI, length in beats). Two takes.
MEL_A = [(0,72,1),(1,74,.5),(1.5,76,1.5),(4,77,.5),(4.5,76,.5),(5,74,1),(6,72,2),
         (8,69,1),(9,72,1),(10,74,2),(12,72,.5),(12.5,70,.5),(13,69,1),(14,67,2),
         (16,70,1),(17,74,1),(18,77,1.5),(20,76,.5),(20.5,74,.5),(21,73,1),(22,74,2),
         (24,76,1),(25,74,.5),(25.5,72,1.5),(27,69,1),(28,70,1),(29,67,1),(30,65,2)]
MEL_B = [(0,77,1.5),(1.5,76,.5),(2,74,1),(3,72,1),(4,74,2),(6,69,2),
         (8,72,1),(9,74,.5),(9.5,76,.5),(10,77,2),(12,76,1),(13,72,1),(14,74,2),
         (16,77,.5),(16.5,79,.5),(17,81,1.5),(18.5,79,.5),(19,77,1),(20,76,2),(22,74,2),
         (24,72,1),(25,76,1),(26,74,1),(27,70,1),(28,69,1.5),(29.5,67,.5),(30,65,2)]

LOOPS = 3
N = int(SR * BAR * 8 * LOOPS + SR * 2)
mixL = np.zeros(N); mixR = np.zeros(N)

def add(buf, start, sig, gain=1.0):
    i = int(start * SR)
    j = min(N, i + len(sig))
    if i < N: buf[i:j] += sig[:j - i] * gain

def adsr(n, a=0.01, d=0.2, s=0.6, r=0.2):
    t = np.arange(n) / SR; total = n / SR
    e = np.where(t < a, t / a, np.where(t < a + d, 1 - (1 - s) * (t - a) / d, s))
    rel = np.clip((total - t) / r, 0, 1)
    return e * rel

def epiano(f, dur, vel=0.5):
    n = int(SR * (dur + .6)); t = np.arange(n) / SR
    tine = np.sin(2 * np.pi * f * t + 0.6 * np.sin(2 * np.pi * f * 14 * t) * np.exp(-t * 18))
    body = 0.5 * np.sin(2 * np.pi * f * 2 * t) * np.exp(-t * 3)
    trem = 1 + 0.12 * np.sin(2 * np.pi * 4.5 * t)
    return (tine + body) * np.exp(-t * 1.6) * adsr(n, .005, .3, .7, .4) * trem * vel

def vibes(f, dur, vel=0.5):
    n = int(SR * (dur + 1.0)); t = np.arange(n) / SR
    s = np.sin(2 * np.pi * f * t) + 0.18 * np.sin(2 * np.pi * f * 4 * t) * np.exp(-t * 6)
    return s * np.exp(-t * 1.9) * (1 + 0.15 * np.sin(2 * np.pi * 5.5 * t)) * adsr(n, .004, .1, .9, .3) * vel

def bass(f, dur, vel=0.6):
    n = int(SR * dur); t = np.arange(n) / SR
    s = np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * f * 2 * t)
    return s * adsr(n, .01, .15, .7, .08) * vel

def brush(dur=0.12, vel=0.15):
    n = int(SR * dur); w = rng.uniform(-1, 1, n)
    w = w - np.convolve(w, np.ones(8) / 8, mode="same")  # high-pass-ish
    return w * np.exp(-np.arange(n) / SR * 30) * vel

def kick(vel=0.35):
    n = int(SR * .25); t = np.arange(n) / SR
    return np.sin(2 * np.pi * (55 + 60 * np.exp(-t * 30)) * t) * np.exp(-t * 14) * vel

for loop in range(LOOPS):
    base = loop * BAR * 8
    mel = MEL_A if loop != 1 else MEL_B
    for i, (root, chord) in enumerate(PROG):
        t0 = base + i * 2 * BEAT
        # E-piano: a bossa comp (on 1 and the "and" of 2), spread left-right.
        for k, (off, ln) in enumerate([(0, 1.2), (1.5, .45)]):
            for j, note in enumerate(chord):
                sig = epiano(hz(note + 12), ln * BEAT, 0.16)
                pan = 0.35 + 0.1 * j
                add(mixL, t0 + off * BEAT + j * 0.012, sig, 1 - pan * .5); add(mixR, t0 + off * BEAT + j * 0.012, sig, .5 + pan * .5)
        # Bass: root, then the fifth.
        r = m(root, ROOT_OCT[root])
        for off, note in [(0, r), (1.5, r + 7)]:
            b = bass(hz(note), .45 * BEAT * 2, 0.42)
            add(mixL, t0 + off * BEAT, b); add(mixR, t0 + off * BEAT, b)
        # Brushes on every eighth, a soft kick on the downbeat.
        if loop > 0 or i >= 4:
            for e in range(4):
                sw = 0.06 if e % 2 else 0  # a lazy swing
                br = brush(vel=0.05 + 0.03 * (e % 2))
                add(mixL, t0 + e * BEAT / 2 + sw, br, .7); add(mixR, t0 + e * BEAT / 2 + sw, br, 1)
            add(mixL, t0, kick()); add(mixR, t0, kick())
    if loop >= 0:
        for beat, note, ln in mel:
            v = vibes(hz(note), ln * BEAT, 0.22)
            add(mixL, base + beat * BEAT + 0.02, v, .9); add(mixR, base + beat * BEAT + 0.02, v, .75)

# Lo-fi: a gentle low-pass, a slow wobble, a whisper of vinyl.
def lowpass(x, a=0.32):
    y = np.zeros_like(x); acc = 0.0
    for i in range(len(x)):
        acc += a * (x[i] - acc); y[i] = acc
    return y
crackle = (rng.uniform(0, 1, N) > 0.9993) * rng.uniform(-.25, .25, N) + rng.normal(0, .004, N)
out = []
for ch in (mixL, mixR):
    ch = ch + crackle
    ch = lowpass(ch)
    out.append(ch)
L, R = out
peak = max(np.max(np.abs(L)), np.max(np.abs(R)))
L, R = L / peak * .6, R / peak * .6
fade = int(SR * 2)
L[-fade:] *= np.linspace(1, 0, fade); R[-fade:] *= np.linspace(1, 0, fade)
L[:int(SR * .3)] *= np.linspace(0, 1, int(SR * .3)); R[:int(SR * .3)] *= np.linspace(0, 1, int(SR * .3))
st = np.empty(2 * N, dtype=np.int16); st[0::2] = (L * 32767).astype(np.int16); st[1::2] = (R * 32767).astype(np.int16)
with tempfile.TemporaryDirectory() as d:
    w = os.path.join(d, "t.wav")
    with wave.open(w, "wb") as f:
        f.setnchannels(2); f.setsampwidth(2); f.setframerate(SR); f.writeframes(st.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", w, "-c:a", "libvorbis", "-q:a", "3", OUT], check=True)
print("ok", round(N / SR, 1), "s")
