#!/usr/bin/env python3
"""Tokkip's launcher icons, one per species (adaptive: background, foreground,
monochrome for themed icons). Same geometry as shared/…/tokkip/Tokkip.kt.
Run from android/:  python3 tools/make_icons.py"""
import math, os
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res')

SPECIES = {
    "conejo": dict(bg="#F29A6B", body="#F7EAD3", accent="#3F9E9A"),
    "gato":   dict(bg="#2B3FC4", body="#F7EAD3", accent="#16807C"),
    "perro":  dict(bg="#9FBF9F", body="#F7EAD3", accent="#3F7E6E"),
    "robot":  dict(bg="#F3E5D3", body="#E9ECEF", accent="#2BB3C0"),
    "mapache": dict(bg="#B9A6E0", body="#CFC8C0", accent="#3F9E9A"),
}

def shade(hexc, k):
    r, g, b = int(hexc[1:3], 16), int(hexc[3:5], 16), int(hexc[5:7], 16)
    f = (lambda c: c * (1 + k)) if k < 0 else (lambda c: c + (255 - c) * k)
    return "#%02X%02X%02X" % tuple(max(0, min(255, round(f(c)))) for c in (r, g, b))

class Tx:
    """Studio units → icon dp: scale, then a translate, with optional local rotation."""
    def __init__(self, s, ox, oy): self.s, self.ox, self.oy = s, ox, oy
    def p(self, x, y, rot=None):
        if rot:
            (cx, cy, a) = rot; ca, sa = math.cos(a), math.sin(a)
            x, y = cx + x * ca - y * sa, cy + x * sa + y * ca
        return (self.ox + x * self.s, self.oy + y * self.s)

def f(v): return ("%.2f" % v).rstrip('0').rstrip('.')

def rrect(T, x, y, w, h, r):
    k = .5523
    pts = []
    def P(px, py): a = T.p(px, py); return f"{f(a[0])},{f(a[1])}"
    d = f"M{P(x + r, y)} L{P(x + w - r, y)} C{P(x + w - r + r * k, y)} {P(x + w, y + r - r * k)} {P(x + w, y + r)} "
    d += f"L{P(x + w, y + h - r)} C{P(x + w, y + h - r + r * k)} {P(x + w - r + r * k, y + h)} {P(x + w - r, y + h)} "
    d += f"L{P(x + r, y + h)} C{P(x + r - r * k, y + h)} {P(x, y + h - r + r * k)} {P(x, y + h - r)} "
    d += f"L{P(x, y + r)} C{P(x, y + r - r * k)} {P(x + r - r * k, y)} {P(x + r, y)} Z"
    return d

def ellipse(T, cx, cy, rx, ry, rot=None):
    k = .5523
    def P(px, py): a = T.p(px, py, rot); return f"{f(a[0])},{f(a[1])}"
    return (f"M{P(cx + rx, cy)} C{P(cx + rx, cy + ry * k)} {P(cx + rx * k, cy + ry)} {P(cx, cy + ry)} "
            f"C{P(cx - rx * k, cy + ry)} {P(cx - rx, cy + ry * k)} {P(cx - rx, cy)} "
            f"C{P(cx - rx, cy - ry * k)} {P(cx - rx * k, cy - ry)} {P(cx, cy - ry)} "
            f"C{P(cx + rx * k, cy - ry)} {P(cx + rx, cy - ry * k)} {P(cx + rx, cy)} Z")

def bez(T, cmds, rot=None):
    """cmds: list of ('M',x,y) ('L',x,y) ('C',x1,y1,x2,y2,x,y) ('Q',x1,y1,x,y) ('Z',)"""
    out = []
    for c in cmds:
        k = c[0]
        pts = [T.p(c[i], c[i + 1], rot) for i in range(1, len(c), 2)]
        out.append(k + " ".join(f"{f(a)},{f(b)}" for a, b in pts))
    return " ".join(out)

def smile_band(T, cx, cy, r, a0, a1, w):
    """A stroked arc as a filled band (works in monochrome masks)."""
    n = 18
    outer = [T.p(cx + (r + w / 2) * math.cos(a), cy + (r + w / 2) * math.sin(a)) for a in [a0 + (a1 - a0) * i / n for i in range(n + 1)]]
    inner = [T.p(cx + (r - w / 2) * math.cos(a), cy + (r - w / 2) * math.sin(a)) for a in [a1 - (a1 - a0) * i / n for i in range(n + 1)]]
    pts = outer + inner
    return "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts) + " Z"

def parts(sp, c, T):
    """[(pathData, fill)] back to front, plus the silhouette and face holes for monochrome."""
    body, acc = c["body"], c["accent"]
    ink = shade(acc, .55) if sp == "robot" else shade(acc, -.12)
    L, sil, holes = [], [], []
    rad = lambda d: d * math.pi / 180
    if sp == "conejo":
        for sd in (-1, 1):
            rot = (sd * 50, -84, rad(sd * 10))
            def ear(ln, w, dy=0): return bez(T, [('M', -w * .45, 6 + dy), ('C', -w * 1.05, -ln * .45 + dy, -w * .65, -ln + dy, 0, -ln + dy), ('C', w * .65, -ln + dy, w * 1.05, -ln * .45 + dy, w * .45, 6 + dy), ('Z',)], rot)
            L += [(ear(128, 46), body), (ear(128 * .78, 46 * .52, -8), acc)]; sil.append(ear(128, 46))
        b = rrect(T, -100, -90, 200, 210, 54)
    elif sp == "gato":
        for sd in (-1, 1):
            rot = (sd * 58, -72, rad(sd * 8))
            def ear(k, dx=0, dy=0): return bez(T, [('M', -40 * k + dx, 34 + dy), ('Q', -30 * k + dx, -40 * k + dy, -2 + dx, -78 * k + dy), ('Q', 6 + dx, -84 * k + dy, 12 * k + dx, -76 * k + dy), ('Q', 36 * k + dx, -36 * k + dy, 42 * k + dx, 34 + dy), ('Z',)], rot)
            L += [(ear(1), body), (ear(.6, 2, 10), shade(acc, .55))]; sil.append(ear(1))
        b = rrect(T, -108, -88, 216, 208, 94)
    elif sp == "perro":
        b = rrect(T, -106, -78, 212, 198, 90)
    elif sp == "mapache":
        for sd in (-1, 1):
            rot = (sd * 64, -70, rad(sd * 6))
            def ear(k, dy): return bez(T, [('M', -34 * k, 22 + dy), ('Q', -30 * k, -26 * k + dy, -6 * k, -54 * k + dy), ('Q', 0, -60 * k + dy, 6 * k, -54 * k + dy), ('Q', 30 * k, -26 * k + dy, 34 * k, 22 + dy), ('Z',)], rot)
            L += [(ear(1.06, 0), shade(body, .6)), (ear(.94, 4), shade(body, -.18)), (ear(.56, 12), shade(body, -.52))]; sil.append(ear(1.06, 0))
        b = rrect(T, -108, -84, 216, 204, 92)
    else:
        b = rrect(T, -104, -84, 208, 198, 62)
    L.append((b, body)); body_path = b
    if sp == "perro":
        L.append((bez(T, [('M', 18, 0), ('C', 14, -36, 64, -48, 84, -22), ('C', 100, 2, 86, 46, 54, 46), ('C', 26, 46, 20, 24, 18, 0), ('Z',)]), shade(body, -.2)))
        for sd in (-1, 1):
            rot = (sd * 96, -54, -sd * .32)
            e = bez(T, [('M', -22, 0), ('C', -34, 50, -26, 118, 6, 122), ('C', 36, 120, 40, 50, 22, -4), ('Q', 0, -14, -22, 0), ('Z',)], rot)
            L += [(e, shade(body, -.24)), (bez(T, [('M', -10, 20), ('C', -16, 60, -10, 104, 6, 106), ('C', 22, 104, 24, 60, 14, 18), ('Z',)], rot), shade(body, -.12))]
            sil.append(e)
    if sp == "mapache":
        L.append((bez(T, [('M', -92, 18), ('C', -90, -16, -58, -14, -30, 0), ('Q', 0, 12, 30, 0), ('C', 58, -14, 90, -16, 92, 18), ('C', 88, 42, 58, 46, 34, 34), ('Q', 0, 22, -34, 34), ('C', -58, 46, -88, 42, -92, 18), ('Z',)]), shade(body, -.52)))
        L.append((ellipse(T, 0, 58, 46, 30), shade(body, .65)))
    if sp == "robot":
        L.append((rrect(T, -84, -48, 168, 116, 40), "#1C2230"))
        for sd in (-1, 1):
            L += [(ellipse(T, sd * 118, 12, 20, 20), shade(body, -.16)), (ellipse(T, sd * 118, 12, 13, 13), shade(body, -.1))]
            sil.append(ellipse(T, sd * 118, 12, 20, 20))
    # Face (eyes at y 14, mouth at y 50; the puppy's mouth sits a bit lower).
    for sd in (-1, 1):
        if sp == "robot": L.append((ellipse(T, sd * 46, 14, 20, 20), "#44" + acc[1:]))
        e = ellipse(T, sd * 46, 14, 12, 12)
        L.append((e, "#1B1616" if sp == "mapache" else ink)); holes.append(e)
        if sp == "mapache": L.append((ellipse(T, sd * 46 - 4, 9, 3.6, 3.6), "#FFFFFF"))
        L.append((ellipse(T, sd * (60 if sp == "robot" else 70), 47 if sp == "robot" else 38, 14 if sp == "robot" else 17, 5 if sp == "robot" else 9), "#F37A9A" if sp == "robot" else "#F2A0A8"))
    my = {"perro": 54, "mapache": 58}.get(sp, 50)
    m = smile_band(T, 0, my - 12, 18, math.pi * .2, math.pi * .8, 5.5)
    L.append((m, ink)); holes.append(m)
    if sp == "mapache":
        n = bez(T, [('M', -13, 26), ('Q', 0, 20, 13, 26), ('Q', 12, 36, 0, 39), ('Q', -12, 36, -13, 26), ('Z',)])
        L.append((n, "#2A2222")); holes.append(n)
    if sp == "perro":
        n = bez(T, [('M', -13, 30), ('Q', 0, 24, 13, 30), ('Q', 12, 40, 0, 43), ('Q', -12, 40, -13, 30), ('Z',)])
        L.append((n, ink)); holes.append(n)
    return L, sil, holes, body_path

def write(name, xml):
    os.makedirs(os.path.dirname(name), exist_ok=True)
    open(name, "w").write(xml)

for sp, c in SPECIES.items():
    top = {"conejo": -222, "gato": -162, "perro": -90, "robot": -90, "mapache": -138}[sp]
    bottom = {"conejo": 120, "gato": 120, "perro": 120, "robot": 114, "mapache": 120}[sp]
    width = {"conejo": 200, "gato": 216, "perro": 290, "robot": 276, "mapache": 216}[sp]
    s = min(72 / (bottom - top), 74 / width)
    T = Tx(s, 54, 54 - (top + bottom) / 2 * s)
    L, sil, holes, body_path = parts(sp, c, T)
    paths = "\n".join(f'    <path android:fillColor="{col}" android:pathData="{d}"/>' for d, col in L)
    write(f"{OUT}/drawable/ic_tokkip_{sp}_fg.xml",
          f'<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/make_icons.py -->\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n{paths}\n</vector>\n')
    mono = " ".join(sil) + " " + " ".join(holes)
    write(f"{OUT}/drawable/ic_tokkip_{sp}_mono.xml",
          f'<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/make_icons.py -->\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
          + "".join(f'    <path android:fillColor="#FFFFFFFF" android:pathData="{d}"/>\n' for d in sil)
          + f'    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{body_path} {" ".join(holes)}"/>\n' + "</vector>\n")
    write(f"{OUT}/drawable/ic_tokkip_{sp}_bg.xml",
          f'<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="http://schemas.android.com/apk/res/android"><solid android:color="{c["bg"]}"/></shape>\n')
    write(f"{OUT}/mipmap-anydpi-v26/ic_tokkip_{sp}.xml",
          f'<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@drawable/ic_tokkip_{sp}_bg"/>\n    <foreground android:drawable="@drawable/ic_tokkip_{sp}_fg"/>\n    <monochrome android:drawable="@drawable/ic_tokkip_{sp}_mono"/>\n</adaptive-icon>\n')
print("ok")
