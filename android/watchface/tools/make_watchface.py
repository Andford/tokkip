#!/usr/bin/env python3
"""Builds Tokkip's watch face (Watch Face Format 2) — res/raw/watchface.xml,
the Material 3 Expressive shapes, the fonts' option icons and the preview.
Run from android/watchface:
    python3 tools/make_watchface.py
Tokkip is drawn with WFF shapes (flat, like the app's 2D look); the shapes
and the preview are rendered here with Pillow.

What you can change in the watch's editor:
  · Color and Tokkip's colour,
  · the font of the time and of the date (system, rounded, serif, mono,
    handwritten — the four bundled ones are SIL Open Font License,
    android/licenses/),
  · the shape of the three buttons (a mix, cookie, clover, flower, sun or
    circle) and what each one shows or opens,
  · the steps ring, on or off."""
import math, os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'res')
FONT_DIR = os.path.join(RES, 'font')

# ── Colours (Material You-ish pairs: accent, dark container) ─────────────────
THEMES = [("lavanda", "#C4B5FD", "#2A2440"), ("rosa", "#F9A8D4", "#3A1F2E"), ("menta", "#86EFAC", "#15301F"),
          ("cielo", "#93C5FD", "#172A40"), ("durazno", "#FDBA74", "#3A2615"), ("limon", "#D9F99D", "#23300F"),
          ("coral", "#FCA5A5", "#3A1818")]
# Tokkip: body colour and its ear/eye accent.
TOKKIPS = [("crema", "#F7EAD3", "#3F9E9A"), ("blanco", "#F5F6F8", "#3F72C2"), ("lila", "#E4DAFB", "#8A6CD1"),
          ("menta", "#CDEFD9", "#4C9A5F"), ("cielo", "#CFE3F8", "#3F72C2"), ("durazno", "#FBD9BF", "#E46F5C")]

# ── Fonts: (option id, string, WFF family, time size, date size, weight) ─────
FONTS = [
    ("sistema", "font_system", "SYNC_TO_DEVICE", 112, 22, "BLACK"),
    ("redonda", "font_round", "tk_round", 112, 22, "NORMAL"),
    ("elegante", "font_serif", "tk_serif", 120, 24, "NORMAL"),
    ("mono", "font_mono", "tk_mono", 92, 20, "NORMAL"),
    ("mano", "font_hand", "tk_hand", 132, 28, "NORMAL"),
]
# Preview files for the icons and the preview image (Pillow can't use "system").
PIL_FONTS = {
    "SYNC_TO_DEVICE": "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "tk_round": os.path.join(FONT_DIR, "tk_round.ttf"),
    "tk_serif": os.path.join(FONT_DIR, "tk_serif.ttf"),
    "tk_mono": os.path.join(FONT_DIR, "tk_mono.ttf"),
    "tk_hand": os.path.join(FONT_DIR, "tk_hand.ttf"),
}

# ── Layout (450 × 450) ────────────────────────────────────────────────────────
CX, CY = 225, 290                 # the cookie and the steps ring
COOKIE = 164
RING = 186
SLOTS = [  # id, x, y, size, name, default provider, its type
    (0, 26, 248, 86, "left_label", "HEART_RATE", "SHORT_TEXT"),
    (1, 338, 248, 86, "right_label", "WATCH_BATTERY", "RANGED_VALUE"),
    (2, 189, 366, 72, "bottom_label", "STEP_COUNT", "SHORT_TEXT"),
]
# Shapes per option: (option id, string, shape for left, right, bottom).
SHAPE_SETS = [
    ("mezcla", "shape_mix", "clover", "flower", "cookie"),
    ("galleta", "shape_cookie", "cookie", "cookie", "cookie"),
    ("trebol", "shape_clover", "clover", "clover", "clover"),
    ("flor", "shape_flower", "flower", "flower", "flower"),
    ("sol", "shape_sun", "sun", "sun", "sun"),
    ("circulo", "shape_circle", "circle", "circle", "circle"),
]

# ── Tokkip geometry (drawn at 450 × 450, then scaled into place) ─────────────
MX, MY, MW, MH = 157, 252, 136, 136          # body box (Tokkip, the bunny)
EYE_W, EYE_H = 20, 20
EYE_L, EYE_R, EYE_Y = MX + 26, MX + MW - 26 - EYE_W, MY + 52
TK_SCALE = 0.66
TK_PIVOT_Y = (MY + MH / 2) / 450.0           # scale around his middle
TK_SHIFT = CY + 12 - (MY + MH / 2)           # his middle a little below the ring's centre

C = lambda k, i: f"[CONFIGURATION.{k}.{i}]"
INK = "[CONFIGURATION.tokkip.1]"
BOB = f"{TK_SHIFT} + 4 * sin(6.28318 * ((([SECOND] % 4) * 1000 + [MILLISECOND]) / 4000.0))"
SPIN = "(((([MINUTE] % 2) * 60 + [SECOND]) * 1000 + [MILLISECOND]) / 120000.0) * 360"


def text(x, y, w, h, tmpl, params, size, weight="BOLD", color="#FFFFFF", align="CENTER", family="SYNC_TO_DEVICE", spacing=None):
    ps = ''.join(f'<Parameter expression="{p}"/>' for p in params)
    ls = f' letterSpacing="{spacing}"' if spacing is not None else ''
    return (f'<PartText x="{x}" y="{y}" width="{w}" height="{h}"><Text align="{align}" ellipsis="TRUE">'
            f'<Font family="{family}" size="{size}" weight="{weight}" color="{color}"{ls}>'
            f'<Template><![CDATA[{tmpl}]]>{ps}</Template></Font></Text></PartText>')


def eyes(kind):
    """open | closed (sleepy lines) | happy (^ ^)"""
    out = []
    for ex in (EYE_L, EYE_R):
        if kind == "open":
            out.append(f'<Ellipse x="{ex}" y="{EYE_Y}" width="{EYE_W}" height="{EYE_H}"><Fill color="{INK}"/></Ellipse>')
        elif kind == "closed":
            out.append(f'<Line startX="{ex - 2}" startY="{EYE_Y + 16}" endX="{ex + EYE_W + 2}" endY="{EYE_Y + 16}"><Stroke color="{INK}" thickness="5" cap="ROUND"/></Line>')
        else:
            out.append(f'<Arc centerX="{ex + EYE_W / 2}" centerY="{EYE_Y + 18}" width="{EYE_W + 8}" height="{EYE_W + 8}" startAngle="-70" endAngle="70"><Stroke color="{INK}" thickness="5" cap="ROUND"/></Arc>')
    return f'<PartDraw x="0" y="0" width="450" height="450">{"".join(out)}</PartDraw>'


def complication(slot, x, y, s, name, default, dtype):
    """A button: the shape behind it is drawn by the scene (it follows the shape setting)."""
    acc = C("theme", 0)
    small = s < 80
    def t(yy, expr, size):
        return text(6, yy, s - 12, 26, "%s", [expr], size, "SEMI_BOLD", acc)
    img = lambda yy, sz: f'<PartImage x="{(s - sz) // 2}" y="{yy}" width="{sz}" height="{sz}" tintColor="{acc}"><Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]"/></PartImage>'
    r = s - 16
    ranged = (f'<PartDraw x="8" y="8" width="{r}" height="{r}">'
              f'<Arc centerX="{r / 2}" centerY="{r / 2}" width="{r}" height="{r}" startAngle="-140" endAngle="140"><Stroke color="#33FFFFFF" thickness="6" cap="ROUND"/></Arc>'
              f'<Arc centerX="{r / 2}" centerY="{r / 2}" width="{r}" height="{r}" startAngle="-140" endAngle="140"><Stroke color="{acc}" thickness="6" cap="ROUND"/>'
              f'<Transform target="endAngle" value="-140 + (clamp([COMPLICATION.RANGED_VALUE_VALUE], [COMPLICATION.RANGED_VALUE_MIN], [COMPLICATION.RANGED_VALUE_MAX]) - [COMPLICATION.RANGED_VALUE_MIN]) / ([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]) * 280"/></Arc></PartDraw>')
    ty = s // 2 - (2 if small else 0)
    return f'''<ComplicationSlot x="{x}" y="{y}" width="{s}" height="{s}" slotId="{slot}" displayName="{name}" supportedTypes="SHORT_TEXT RANGED_VALUE MONOCHROMATIC_IMAGE SMALL_IMAGE EMPTY">
  <DefaultProviderPolicy defaultSystemProvider="{default}" defaultSystemProviderType="{dtype}"/>
  <BoundingOval x="0" y="0" width="{s}" height="{s}"/>
  <Complication type="SHORT_TEXT">{img(ty - 26 if small else ty - 30, 22 if small else 26)}{t(ty - 2, "[COMPLICATION.TEXT]", 18 if small else 22)}</Complication>
  <Complication type="RANGED_VALUE">{ranged}{img(ty - 22, 20)}{t(ty - 2, "[COMPLICATION.TEXT]", 17 if small else 20)}</Complication>
  <Complication type="MONOCHROMATIC_IMAGE">{img((s - (34 if small else 42)) // 2, 34 if small else 42)}</Complication>
  <Complication type="SMALL_IMAGE"><PartImage x="{s // 5}" y="{s // 5}" width="{s - 2 * (s // 5)}" height="{s - 2 * (s // 5)}"><Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage></Complication>
</ComplicationSlot>'''


def shapes_scene():
    """The buttons' shapes, one set per option of the shape setting."""
    opts = []
    for oid, _, *kinds in SHAPE_SETS:
        parts = []
        for (slot, x, y, s, *_), kind in zip(SLOTS, kinds):
            if kind == "circle":
                parts.append(f'<PartDraw x="{x}" y="{y}" width="{s}" height="{s}"><Ellipse x="0" y="0" width="{s}" height="{s}"><Fill color="{C("theme", 1)}"/></Ellipse></PartDraw>')
            else:
                parts.append(f'<PartImage x="{x}" y="{y}" width="{s}" height="{s}" tintColor="{C("theme", 1)}"><Image resource="shape_{kind}"/></PartImage>')
        opts.append(f'<ListOption id="{oid}"><Group name="shapes_{oid}" x="0" y="0" width="450" height="450">{"".join(parts)}</Group></ListOption>')
    return f'''<Group name="buttons" x="0" y="0" width="450" height="450">
      <Variant mode="AMBIENT" target="alpha" value="70"/>
      <ListConfiguration id="shape">{"".join(opts)}</ListConfiguration>
    </Group>'''


def clock_scene():
    """The time, in the font you pick: hour in the accent, minutes in white."""
    opts = []
    for oid, _, family, size, _, weight in FONTS:
        h = int(size * 1.12)
        y = 60 + (118 - h) // 2
        # Mono is wide: a tighter gap.
        gap = 12 if oid == "mono" else 13
        opts.append(f'''<ListOption id="{oid}"><DigitalClock x="0" y="0" width="450" height="450">
        <TimeText format="hh" hourFormat="SYNC_TO_DEVICE" align="END" x="10" y="{y}" width="{225 - gap - 10}" height="{h}">
          <Font family="{family}" size="{size}" weight="{weight}" color="{C("theme", 0)}"/>
        </TimeText>
        <TimeText format="mm" align="START" x="{225 + gap}" y="{y}" width="{225 - gap - 10}" height="{h}">
          <Font family="{family}" size="{size}" weight="{weight}" color="#FFFFFFFF"/>
        </TimeText>
      </DigitalClock></ListOption>''')
    return f'<ListConfiguration id="timefont">{"".join(opts)}</ListConfiguration>'


def date_scene():
    """The date in a pill, in its own font."""
    opts = []
    for oid, _, family, _, dsize, weight in FONTS:
        w = "BOLD" if family == "SYNC_TO_DEVICE" else weight
        opts.append(f'<ListOption id="{oid}"><Group name="date_{oid}" x="0" y="0" width="450" height="450">'
                    + text(150, 28, 150, 36, "%s", ['icuText(&quot;EEE d&quot;)'], dsize, w, C("theme", 0), family=family)
                    + '</Group></ListOption>')
    pill = (f'<PartDraw x="150" y="28" width="150" height="36">'
            f'<RoundRectangle x="0" y="0" width="150" height="36" cornerRadiusX="18" cornerRadiusY="18"><Fill color="{C("theme", 1)}"/></RoundRectangle></PartDraw>')
    return f'''<Group name="date" x="0" y="0" width="450" height="450">
      <Group name="date_pill" x="0" y="0" width="450" height="450"><Variant mode="AMBIENT" target="alpha" value="0"/>{pill}</Group>
      <ListConfiguration id="datefont">{"".join(opts)}</ListConfiguration>
    </Group>'''


def tokkip_scene():
    body = (f'<PartDraw x="0" y="0" width="450" height="450">'
            f'<RoundRectangle x="{MX}" y="{MY}" width="{MW}" height="{MH}" cornerRadiusX="38" cornerRadiusY="38"><Fill color="{C("tokkip", 0)}"/></RoundRectangle>'
            f'<Ellipse x="{EYE_L - 22}" y="{EYE_Y + 26}" width="26" height="13"><Fill color="#66F07A8C"/></Ellipse>'
            f'<Ellipse x="{EYE_R + 16}" y="{EYE_Y + 26}" width="26" height="13"><Fill color="#66F07A8C"/></Ellipse>'
            f'<Arc centerX="{MX + MW / 2}" centerY="{EYE_Y + 22}" width="28" height="28" startAngle="125" endAngle="235"><Stroke color="{INK}" thickness="5" cap="ROUND"/></Arc>'
            f'</PartDraw>')
    ears = ""
    for sd in (-1, 1):
        ex = MX + MW / 2 + sd * 34 - 16
        ears += (f'<PartDraw x="{int(ex)}" y="{MY - 84}" width="32" height="104" angle="{sd * 10}" pivotX="0.5" pivotY="1">'
                 f'<Ellipse x="0" y="0" width="32" height="104"><Fill color="{C("tokkip", 0)}"/></Ellipse>'
                 f'<Ellipse x="9" y="12" width="14" height="74"><Fill color="{INK}"/></Ellipse></PartDraw>')
    ambient_body = (f'<PartDraw x="0" y="0" width="450" height="450">'
                    f'<RoundRectangle x="{MX}" y="{MY}" width="{MW}" height="{MH}" cornerRadiusX="38" cornerRadiusY="38"><Stroke color="#9A9AA6" thickness="5"/></RoundRectangle>'
                    f'</PartDraw>')
    face = f'''<Condition>
          <Expressions>
            <Expression name="sleepy"><![CDATA[([HOUR_0_23] >= 23) || ([HOUR_0_23] < 6)]]></Expression>
            <Expression name="happy"><![CDATA[[STEP_PERCENT] >= 100]]></Expression>
            <Expression name="blink"><![CDATA[(([SECOND] % 6) == 0) && ([MILLISECOND] < 160)]]></Expression>
          </Expressions>
          <Compare expression="sleepy">{eyes("closed")}</Compare>
          <Compare expression="happy">{eyes("happy")}</Compare>
          <Compare expression="blink">{eyes("closed")}</Compare>
          <Default>{eyes("open")}</Default>
        </Condition>'''
    return f'''<Group name="tokkip" x="0" y="0" width="450" height="450" pivotX="0.5" pivotY="{TK_PIVOT_Y:.4f}" scaleX="{TK_SCALE}" scaleY="{TK_SCALE}">
      <Launch target="mx.loperstack.tokkip/mx.loperstack.tokkip.wear.WearActivity"/>
      <Transform target="y" value="{BOB}"/>
      <Group name="tokkip_color" x="0" y="0" width="450" height="450">
        <Variant mode="AMBIENT" target="alpha" value="0"/>
        {ears}
        {body}
      </Group>
      <Group name="tokkip_ambient" x="0" y="0" width="450" height="450" alpha="0">
        <Variant mode="AMBIENT" target="alpha" value="255"/>
        {ambient_body}
      </Group>
      <Group name="tokkip_face" x="0" y="0" width="450" height="450">
        {face}
      </Group>
    </Group>'''


def steps_ring():
    """Steps around the cookie, open at the bottom where the third button sits."""
    x, y = CX - RING // 2, CY - RING // 2
    a0, a1 = -138, 138
    arc = (f'<PartDraw x="{x}" y="{y}" width="{RING}" height="{RING}">'
           f'<Arc centerX="{RING / 2}" centerY="{RING / 2}" width="{RING - 10}" height="{RING - 10}" startAngle="{a0}" endAngle="{a1}"><Stroke color="{C("theme", 1)}" thickness="9" cap="ROUND"/></Arc>'
           f'<Arc centerX="{RING / 2}" centerY="{RING / 2}" width="{RING - 10}" height="{RING - 10}" startAngle="{a0}" endAngle="{a1}"><Stroke color="{C("theme", 0)}" thickness="9" cap="ROUND"/>'
           f'<Transform target="endAngle" value="{a0} + clamp([STEP_PERCENT], 0, 100) / 100.0 * {a1 - a0}"/></Arc>'
           f'</PartDraw>')
    return f'''<BooleanConfiguration id="steps"><BooleanOption id="TRUE"><Group name="steps" x="0" y="0" width="450" height="450">
      <Variant mode="AMBIENT" target="alpha" value="0"/>
      {arc}
    </Group></BooleanOption></BooleanConfiguration>'''


def watchface():
    color_opts = lambda arr: ''.join(f'<ColorOption id="{i}" displayName="{n}" colors="{a} {b}"/>' for i, (n, a, b) in enumerate(arr))
    font_opts = ''.join(f'<ListOption id="{oid}" displayName="{name}" icon="font_{oid}"/>' for oid, name, *_ in FONTS)
    shape_opts = ''.join(f'<ListOption id="{oid}" displayName="{name}" icon="icon_{oid}"/>' for oid, name, *_ in SHAPE_SETS)
    cookie = (f'<PartImage x="{CX - COOKIE // 2}" y="{CY - COOKIE // 2}" width="{COOKIE}" height="{COOKIE}" tintColor="{C("theme", 1)}">'
              f'<Variant mode="AMBIENT" target="alpha" value="0"/><Transform target="angle" value="{SPIN}"/><Image resource="shape_cookie12"/></PartImage>')
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<!-- Tokkip · Material 3 Expressive. Generated by tools/make_watchface.py — edit that, not this. -->
<WatchFace width="450" height="450">
  <Metadata key="CLOCK_TYPE" value="DIGITAL"/>
  <Metadata key="PREVIEW_TIME" value="10:08:32"/>
  <UserConfigurations>
    <ColorConfiguration id="theme" displayName="theme_label" defaultValue="0">{color_opts(THEMES)}</ColorConfiguration>
    <ColorConfiguration id="tokkip" displayName="tokkip_label" defaultValue="0">{color_opts(TOKKIPS)}</ColorConfiguration>
    <ListConfiguration id="timefont" displayName="timefont_label" defaultValue="redonda">{font_opts}</ListConfiguration>
    <ListConfiguration id="datefont" displayName="datefont_label" defaultValue="redonda">{font_opts}</ListConfiguration>
    <ListConfiguration id="shape" displayName="shape_label" defaultValue="mezcla">{shape_opts}</ListConfiguration>
    <BooleanConfiguration id="steps" displayName="steps_label" defaultValue="TRUE"/>
  </UserConfigurations>
  <Scene backgroundColor="#FF000000">
    {date_scene()}
    {clock_scene()}
    <!-- The cookie behind Tokkip, turning slowly, and your steps around it. -->
    {cookie}
    {steps_ring()}
    <!-- Tokkip, breathing; tap him to open the app. -->
    {tokkip_scene()}
    <!-- Three buttons in Material 3 Expressive shapes: what they show or open is yours to pick. -->
    {shapes_scene()}
    {"".join(complication(*s) for s in SLOTS)}
  </Scene>
</WatchFace>
'''

# ── Shapes (white on transparent; the face tints them) ───────────────────────

def polar_shape(path, r_of, size=200, k=4):
    s = size * k
    img = Image.new("L", (s, s), 0)
    pts = []
    for i in range(1440):
        a = 2 * math.pi * i / 1440
        r = r_of(a) * s / 2
        pts.append((s / 2 + r * math.cos(a), s / 2 + r * math.sin(a)))
    ImageDraw.Draw(img).polygon(pts, fill=255)
    img = img.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    out.putalpha(img)
    out.save(path)
    return out


def clover(path, size=200, k=4):
    """Four round leaves, like Material's Clover4Leaf."""
    s = size * k
    img = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(img)
    lobe = s * 0.27
    off = s * 0.225
    for ang in (45, 135, 225, 315):
        cx = s / 2 + off * math.cos(math.radians(ang)) * 1.0
        cy = s / 2 + off * math.sin(math.radians(ang)) * 1.0
        d.ellipse([cx - lobe, cy - lobe, cx + lobe, cy + lobe], fill=255)
    d.ellipse([s * 0.3, s * 0.3, s * 0.7, s * 0.7], fill=255)
    img = img.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    out.putalpha(img)
    out.save(path)
    return out


SHAPES = {
    "cookie": lambda a: 0.97 * (1 - 0.08 + 0.08 * math.cos(9 * a)),
    "cookie12": lambda a: 0.96 * (1 - 0.045 + 0.045 * math.cos(12 * a)),
    "flower": lambda a: 0.99 * (0.74 + 0.26 * abs(math.cos(4 * a)) ** 0.55),
    "sun": lambda a: 0.99 * (0.82 + 0.18 * (0.5 + 0.5 * math.cos(10 * a)) ** 2.4),
}


def make_shapes():
    out = {}
    for name, f in SHAPES.items():
        out[name] = polar_shape(os.path.join(RES, "drawable", f"shape_{name}.png"), f)
    out["clover"] = clover(os.path.join(RES, "drawable", "shape_clover.png"))
    return out


def option_icons(shapes):
    """The editor's icons: each shape set, and each font showing 10:08."""
    acc, cont = "#C4B5FD", "#2A2440"
    for oid, _, *kinds in SHAPE_SETS:
        img = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
        pos = [(4, 30, 36), (56, 30, 36), (30, 58, 34)] if oid == "mezcla" else [(18, 18, 60)]
        for (x, y, sz), kind in zip(pos, kinds):
            if kind == "circle":
                d = ImageDraw.Draw(img); d.ellipse([x, y, x + sz, y + sz], fill=acc)
            else:
                sh = shapes[kind].resize((sz, sz), Image.LANCZOS)
                tint = Image.new("RGBA", sh.size, acc); tint.putalpha(sh.getchannel("A"))
                img.alpha_composite(tint, (x, y))
        img.save(os.path.join(RES, "drawable", f"icon_{oid}.png"))
    for oid, _, family, *_ in FONTS:
        img = Image.new("RGBA", (160, 96), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        f = ImageFont.truetype(PIL_FONTS[family], 44 if oid != "mano" else 54)
        d.text((76, 48), "10", fill=acc, font=f, anchor="rm")
        d.text((84, 48), "08", fill="#FFFFFF", font=f, anchor="lm")
        img.save(os.path.join(RES, "drawable", f"font_{oid}.png"))


def preview(path, shapes, font="redonda", shape_set="mezcla", out_size=450):
    s = 450 * 2
    k = 2
    img = Image.new("RGBA", (s, s), "#000000")
    d = ImageDraw.Draw(img)
    acc, cont = THEMES[0][1], THEMES[0][2]
    body, line = TOKKIPS[0][1], TOKKIPS[0][2]
    fid, _, family, size, dsize, _ = next(f for f in FONTS if f[0] == font)
    big = ImageFont.truetype(PIL_FONTS[family], int(size * k * 0.92))
    small = ImageFont.truetype(PIL_FONTS[family], int(dsize * k * 0.95))
    # Date pill.
    d.rounded_rectangle([150 * k, 28 * k, 300 * k, 64 * k], radius=18 * k, fill=cont)
    d.text((225 * k, 46 * k), "sáb 24", fill=acc, font=small, anchor="mm")
    d.text((212 * k, 119 * k), "10", fill=acc, font=big, anchor="rm")
    d.text((238 * k, 119 * k), "08", fill="#FFFFFF", font=big, anchor="lm")
    def paste_shape(name, x, y, sz, color):
        sh = shapes[name].resize((sz * k, sz * k), Image.LANCZOS)
        tint = Image.new("RGBA", sh.size, color); tint.putalpha(sh.getchannel("A"))
        img.alpha_composite(tint, (x * k, y * k))
    paste_shape("cookie12", CX - COOKIE // 2, CY - COOKIE // 2, COOKIE, cont)
    d = ImageDraw.Draw(img)
    rb = [(CX - RING / 2 + 5) * k, (CY - RING / 2 + 5) * k, (CX + RING / 2 - 5) * k, (CY + RING / 2 - 5) * k]
    # PIL angles: 0 = 3 o'clock; WFF: 0 = 12 o'clock.
    d.arc(rb, -138 - 90, 138 - 90, fill=cont, width=9 * k)
    d.arc(rb, -138 - 90, -138 - 90 + 276 * 0.62, fill=acc, width=9 * k)
    # Tokkip, scaled around his middle.
    tk = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    td = ImageDraw.Draw(tk)
    for sd in (-1, 1):
        ear = Image.new("RGBA", (32 * k, 208 * k), (0, 0, 0, 0)); ed = ImageDraw.Draw(ear)
        ed.ellipse([0, 0, 32 * k, 104 * k], fill=body); ed.ellipse([9 * k, 12 * k, 23 * k, 86 * k], fill=line)
        r = ear.rotate(-sd * 10, resample=Image.BICUBIC, center=(16 * k, 104 * k))
        ex = MX + MW / 2 + sd * 34 - 16
        tk.alpha_composite(r, (int(ex * k), int((MY - 84) * k)))
    td = ImageDraw.Draw(tk)
    td.rounded_rectangle([MX * k, MY * k, (MX + MW) * k, (MY + MH) * k], radius=38 * k, fill=body)
    for ex in (EYE_L, EYE_R):
        td.ellipse([ex * k, EYE_Y * k, (ex + EYE_W) * k, (EYE_Y + EYE_H) * k], fill=line)
    td.ellipse([(EYE_L - 22) * k, (EYE_Y + 26) * k, (EYE_L + 4) * k, (EYE_Y + 39) * k], fill="#F5B8C2")
    td.ellipse([(EYE_R + 16) * k, (EYE_Y + 26) * k, (EYE_R + 42) * k, (EYE_Y + 39) * k], fill="#F5B8C2")
    cxm = MX + MW / 2
    td.arc([(cxm - 14) * k, (EYE_Y + 8) * k, (cxm + 14) * k, (EYE_Y + 36) * k], 35, 145, fill=line, width=5 * k)
    w2 = int(s * TK_SCALE)
    small_tk = tk.resize((w2, w2), Image.LANCZOS)
    py = TK_PIVOT_Y * s
    ox = int(s / 2 - s / 2 * TK_SCALE)
    oy = int(py - py * TK_SCALE + TK_SHIFT * k)
    img.alpha_composite(small_tk, (ox, oy))
    # Buttons.
    kinds = next(x for x in SHAPE_SETS if x[0] == shape_set)[2:]
    labels = ["72", "84%", "6.2k"]
    d = ImageDraw.Draw(img)
    for (slot, x, y, sz, *_), kind, lab in zip(SLOTS, kinds, labels):
        if kind == "circle":
            d.ellipse([x * k, y * k, (x + sz) * k, (y + sz) * k], fill=cont)
        else:
            paste_shape(kind, x, y, sz, cont)
        d = ImageDraw.Draw(img)
        f = ImageFont.truetype(PIL_FONTS["SYNC_TO_DEVICE"], (20 if sz > 80 else 17) * k)
        d.text(((x + sz / 2) * k, (y + sz / 2 + 8) * k), lab, fill=acc, font=f, anchor="mm")
        # A little icon dot above the value.
        d.ellipse([(x + sz / 2 - 7) * k, (y + sz / 2 - 22) * k, (x + sz / 2 + 7) * k, (y + sz / 2 - 8) * k], outline=acc, width=3 * k)
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, s, s], fill=255)
    out = Image.new("RGB", (s, s), "#000000")
    out.paste(img.convert("RGB"), (0, 0), mask)
    out.resize((out_size, out_size), Image.LANCZOS).save(path)


if __name__ == "__main__":
    import sys
    os.makedirs(os.path.join(RES, "raw"), exist_ok=True)
    os.makedirs(os.path.join(RES, "drawable"), exist_ok=True)
    shapes = make_shapes()
    option_icons(shapes)
    open(os.path.join(RES, "raw", "watchface.xml"), "w").write(watchface())
    preview(os.path.join(RES, "drawable", "preview.png"), shapes)
    # Extra previews for the README / a quick look: python3 make_watchface.py <dir>
    if len(sys.argv) > 1:
        for font, shape in [("redonda", "mezcla"), ("elegante", "flor"), ("mono", "galleta"), ("mano", "trebol"), ("sistema", "sol")]:
            preview(os.path.join(sys.argv[1], f"wf-{font}-{shape}.png"), shapes, font, shape, 450)
    print("ok")
