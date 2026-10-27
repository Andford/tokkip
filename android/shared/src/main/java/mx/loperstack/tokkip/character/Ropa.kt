// Tokkip's clothes: the list and its calendar (what "auto" puts on him each
// season), and the drawings — the same outfits as Tokkip PC
// (windows/src/tokkip/ropa.ts), in studio units (Tokkip.kt): hats sit on the
// top of his head, glasses follow his face.

package mx.loperstack.tokkip.character

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Tokkip's wardrobe. The names are stored in the preferences: keep them. */
enum class Outfit(val label: String) {
    NONE("Ninguno"),
    PARTY("Gorrito de fiesta"),
    CROWN("Corona"),
    BEANIE("Gorro de lana"),
    HEARTS("Diadema de corazones"),
    SPRING("Corona de flores"),
    SUNGLASSES("Lentes de sol"),
    STRAW("Sombrero de paja"),
    TRICOLOR("Moño tricolor"),
    WITCH("Sombrero de bruja"),
    FLOWERS("Corona de cempasúchil"),
    SCARF("Bufanda"),
    ANTLERS("Astas de reno"),
    SANTA("Gorro navideño"),
    GLASSES("Lentes redondos"),
    BOW("Moño"),
    HEADPHONES("Audífonos");

    companion object {
        /** A stored name → an outfit; anything gone (the first versions' "BUNNY") or unknown → null. */
        fun of(name: String?): Outfit? = entries.firstOrNull { it.name == name }

        /** [month, first day, last day, outfit], checked in order: the same calendar as Tokkip PC. */
        private val SEASONS = listOf(
            Season(1, 1, 2, PARTY), Season(1, 3, 6, CROWN), Season(1, 7, 31, BEANIE), Season(2, 1, 9, BEANIE),
            Season(2, 10, 14, HEARTS), Season(3, 20, 31, SPRING), Season(4, 1, 30, SPRING),
            Season(6, 21, 30, SUNGLASSES), Season(7, 1, 31, SUNGLASSES), Season(8, 1, 31, SUNGLASSES),
            Season(9, 1, 16, TRICOLOR), Season(10, 15, 31, WITCH), Season(11, 1, 2, FLOWERS), Season(11, 3, 30, SCARF),
            Season(12, 1, 15, ANTLERS), Season(12, 16, 30, SANTA), Season(12, 31, 31, PARTY),
        )

        /** What he wears for the season, read in your own calendar. */
        fun seasonal(cal: java.util.Calendar): Outfit {
            val m = cal.get(java.util.Calendar.MONTH) + 1
            val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
            return SEASONS.firstOrNull { it.month == m && d >= it.from && d <= it.to }?.outfit ?: NONE
        }

        /** "auto" → the season's; a name → that one; anything else → none. */
        fun resolve(selection: String, cal: java.util.Calendar = java.util.Calendar.getInstance()): Outfit =
            if (selection == "auto") seasonal(cal) else of(selection) ?: NONE
    }
}

private class Season(val month: Int, val from: Int, val to: Int, val outfit: Outfit)

/** How the clothes are drawn this frame. */
class Wear(
    val species: Species,
    val t: Float,
    /** 0 = not on yet, 1 = on: hats drop in from above, glasses fade in. */
    val k: Float,
    /** Soft parts lagging behind his moves, −1…1. */
    val sway: Float,
    /** His face's offset (glasses go with it), studio units. */
    val fx: Float = 0f,
    val fy: Float = 0f,
)

object Ropa {
    private val FACE_WORN = setOf(Outfit.SUNGLASSES, Outfit.GLASSES)

    fun headTop(s: Species) = when (s) { Species.PERRO -> -78f; Species.ROBOT, Species.MAPACHE -> -84f; Species.GATO -> -88f; Species.CONEJO -> -90f }

    /** Pieces behind him (the headphones' band); everything else is in front. */
    fun DrawScope.outfitBack(o: Outfit, w: Wear) {
        if (o != Outfit.HEADPHONES || w.k <= 0.01f) return
        val a = min(1f, w.k * 1.6f)
        withTransform({ translate(0f, -(1f - min(1f, w.k)) * 90f) }) {
            val top = headTop(w.species)
            drawPath(Path().apply { moveTo(-100f, 0f); cubicTo(-100f, top - 40f, 100f, top - 40f, 100f, 0f) }, Color(0xFF4A4F5E), alpha = a, style = Stroke(12f, cap = StrokeCap.Round))
        }
    }

    fun DrawScope.outfitFront(o: Outfit, w: Wear) {
        if (o == Outfit.NONE || w.k <= 0.01f) return
        val k = min(1f, w.k)
        val top = headTop(w.species)
        val a = if (o in FACE_WORN) k else min(1f, k * 1.6f)
        val dy = if (o in FACE_WORN) 0f else -(1f - k) * 90f
        withTransform({ translate(0f, dy) }) {
            val p = Pen(this, a)
            when (o) {
                Outfit.PARTY -> p.partyHat(w, top)
                Outfit.CROWN -> p.crown(w, top)
                Outfit.BEANIE -> p.beanie(w, top)
                Outfit.STRAW -> p.strawHat(w, top)
                Outfit.WITCH -> p.witchHat(w, top)
                Outfit.SANTA -> p.santaHat(w, top)
                Outfit.ANTLERS -> p.antlers(w, top)
                Outfit.HEARTS -> p.hearts(w, top)
                Outfit.SPRING -> p.flowerCrown(w, top)
                Outfit.FLOWERS -> p.marigolds(w, top)
                Outfit.TRICOLOR -> p.tricolor(w, top)
                Outfit.BOW -> p.bow(w, top)
                Outfit.HEADPHONES -> p.headphones(w)
                Outfit.SCARF -> p.scarf(w)
                Outfit.SUNGLASSES -> p.sunglasses(w)
                Outfit.GLASSES -> p.roundGlasses(w)
                Outfit.NONE -> Unit
            }
        }
    }
}

/** A brush with one alpha for a whole outfit, and the few shapes they are made of. */
private class Pen(val ds: DrawScope, val a: Float) {
    fun fill(p: Path, c: Color) = ds.drawPath(p, c, alpha = a)
    fun line(p: Path, c: Color, w: Float, cap: StrokeCap = StrokeCap.Round) =
        ds.drawPath(p, c, alpha = a, style = Stroke(w, cap = cap, join = StrokeJoin.Round))
    fun circle(x: Float, y: Float, r: Float, c: Color) = ds.drawCircle(c, r, Offset(x, y), alpha = a)
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, c: Color, rot: Float = 0f) {
        if (rot == 0f) ds.drawOval(c, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), alpha = a)
        else ds.withTransform({ translate(cx, cy); rotate(deg(rot), Offset.Zero) }) { drawOval(c, Offset(-rx, -ry), Size(rx * 2, ry * 2), alpha = a) }
    }
    fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color) =
        ds.drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r), alpha = a)
    fun rrectLine(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color, sw: Float) =
        ds.drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r, r), alpha = a, style = Stroke(sw))
    fun at(x: Float, y: Float, rot: Float = 0f, s: Float = 1f, f: Pen.() -> Unit) = ds.withTransform({
        translate(x, y)
        if (rot != 0f) rotate(deg(rot), Offset.Zero)
        if (s != 1f) scale(s, s, Offset.Zero)
    }) { f() }

    fun pompom(cx: Float, cy: Float, r: Float, c: Color) {
        circle(cx, cy, r, c)
        circle(cx - r * 0.3f, cy - r * 0.35f, r * 0.45f, Color.White.copy(alpha = 0.35f))
    }

    fun flower(cx: Float, cy: Float, r: Float, petal: Color, heart: Color, petals: Int = 5, turn: Float = 0f) {
        for (i in 0 until petals) {
            val ang = turn + i * PI.toFloat() * 2f / petals
            oval(cx + cos(ang) * r * 0.62f, cy + sin(ang) * r * 0.62f, r * 0.55f, r * 0.4f, petal, ang)
        }
        circle(cx, cy, r * 0.38f, heart)
    }

    fun leaf(cx: Float, cy: Float, len: Float, ang: Float, c: Color) = at(cx, cy, ang) {
        fill(Path().apply { moveTo(0f, 0f); quadraticTo(len * 0.5f, -len * 0.38f, len, 0f); quadraticTo(len * 0.5f, len * 0.38f, 0f, 0f) }, c)
    }

    // ── Hats ──────────────────────────────────────────────────────────────────

    fun partyHat(w: Wear, top: Float) = at(-8f, top + 10f, -0.16f + w.sway * 0.12f) {
        val h = 82f; val b = 36f
        val cone = Path().apply {
            moveTo(-b, 0f); quadraticTo(-b * 0.2f, -h * 0.5f, 0f, -h); quadraticTo(b * 0.2f, -h * 0.5f, b, 0f); quadraticTo(0f, 9f, -b, 0f); close()
        }
        fill(cone, Color(0xFF7C5CFF))
        ds.clipPath(cone) {
            for (i in -4 until 6) {
                fill(Path().apply { moveTo(-60f + i * 22f, 10f); lineTo(-48f + i * 22f, 10f); lineTo(12f + i * 22f, -h - 10f); lineTo(i * 22f, -h - 10f); close() }, Color(0xFFFFD447))
            }
            ds.drawRect(Color.White.copy(alpha = 0.18f), Offset(-b, -h), Size(b * 0.7f, h), alpha = a)
        }
        for (i in 0 until 7) circle(-b + 4f + i * ((b * 2 - 8f) / 6f), 2f, 6.5f, Color(0xFFFF6FA8))
        pompom(sin(w.t * 5f) * 1.5f + w.sway * 5f, -h - 4f, 11f, Color(0xFFFF6FA8))
    }

    fun crown(w: Wear, top: Float) = at(0f, top + 14f, w.sway * 0.06f, 1.25f) {
        val wd = 46f; val h = 40f
        val gold = Color(0xFFF5B83D)
        fill(Path().apply {
            moveTo(-wd, 0f); lineTo(-wd - 4f, -h); quadraticTo(-wd * 0.55f, -h * 0.45f, -wd * 0.45f, -h * 0.5f)
            lineTo(-wd * 0.22f, -h - 6f); quadraticTo(0f, -h * 0.4f, wd * 0.22f, -h - 6f); lineTo(wd * 0.45f, -h * 0.5f)
            quadraticTo(wd * 0.55f, -h * 0.45f, wd + 4f, -h); lineTo(wd, 0f); quadraticTo(0f, 7f, -wd, 0f); close()
        }, gold)
        ds.drawRect(Color(0xFFE39A1B), Offset(-wd, -9f), Size(wd * 2, 7f), alpha = a)
        oval(-wd * 0.55f, -h * 0.55f, 5f, 12f, Color.White.copy(alpha = 0.35f), 0.2f)
        for ((gx, c) in listOf(-wd * 0.62f to Color(0xFF4FC3F7), 0f to Color(0xFFE5486F), wd * 0.62f to Color(0xFF66D19E))) {
            circle(gx, -5f, 5.5f, c)
            circle(gx - 1.6f, -6.6f, 1.6f, Color.White.copy(alpha = 0.8f))
        }
        for ((px, py) in listOf(-wd - 4f to -h, -wd * 0.22f to -h - 6f, wd * 0.22f to -h - 6f, wd + 4f to -h)) circle(px, py, 4.5f, Color(0xFFFFE08A))
    }

    fun beanie(w: Wear, top: Float) = at(0f, top + 30f, w.sway * 0.05f) {
        val wd = 90f
        val knit = Color(0xFFE8574F)
        fill(Path().apply { moveTo(-wd, 0f); cubicTo(-wd, -62f, wd, -62f, wd, 0f); close() }, knit)
        for (i in -3..3) line(Path().apply { moveTo(i * 22f, -2f); quadraticTo(i * 20f, -26f, i * 12f, -44f) }, TokkipArt.shade(knit, -0.12f), 3f)
        rrect(-wd - 4f, -12f, wd * 2 + 8f, 24f, 12f, Color(0xFFF48B85))
        var i = -wd + 8f
        while (i < wd) { line(Path().apply { moveTo(i, -9f); lineTo(i, 9f) }, Color.Black.copy(alpha = 0.08f), 2f, StrokeCap.Butt); i += 10f }
        pompom(w.sway * 6f, -50f, 15f, Color(0xFFFFF4E6))
    }

    fun strawHat(w: Wear, top: Float) = at(0f, top + 12f, -0.06f + w.sway * 0.08f) {
        val straw = Color(0xFFE9C46A)
        oval(0f, 0f, 112f, 20f, TokkipArt.shade(straw, -0.08f))
        fill(Path().apply { moveTo(-50f, 0f); cubicTo(-50f, -52f, 50f, -52f, 50f, 0f); close() }, straw)
        fill(Path().apply { moveTo(-50f, -2f); quadraticTo(0f, 4f, 50f, -2f); lineTo(48f, -14f); quadraticTo(0f, -8f, -48f, -14f); close() }, Color(0xFF3F9E9A))
        var r = 64f
        while (r < 112f) {
            ds.drawArc(Color(0x40784014), 9f, 162f, false, Offset(-r, -r * 0.18f), Size(r * 2, r * 0.36f), alpha = a, style = Stroke(2f))
            r += 12f
        }
        flower(40f, -12f, 10f, Color(0xFFFF8FB1), Color(0xFFFFD447))
    }

    fun witchHat(w: Wear, top: Float) = at(4f, top + 14f, 0.08f + w.sway * 0.1f) {
        val dark = Color(0xFF3B2A55)
        oval(0f, 0f, 92f, 15f, TokkipArt.shade(dark, -0.2f))
        val bend = w.sway * 10f + sin(w.t * 1.7f) * 3f
        fill(Path().apply {
            moveTo(-46f, -2f); cubicTo(-34f, -50f, -10f, -86f, 18f + bend, -112f)
            quadraticTo(40f + bend, -118f, 48f + bend, -100f); quadraticTo(26f, -92f, 18f, -70f)
            cubicTo(30f, -40f, 44f, -16f, 46f, -2f); quadraticTo(0f, 8f, -46f, -2f); close()
        }, dark)
        fill(Path().apply { moveTo(-44f, -6f); quadraticTo(0f, 4f, 44f, -6f); lineTo(40f, -20f); quadraticTo(0f, -10f, -40f, -20f); close() }, Color(0xFFF59E0B))
        rrectLine(-8f, -19f, 16f, 12f, 1f, Color(0xFFFFE08A), 3f)
        at(-18f, -44f, sin(w.t * 2f) * 0.2f) { fill(star(8f, 3.4f), Color(0xFFFFE08A)) }
    }

    fun santaHat(w: Wear, top: Float) = at(0f, top + 22f, w.sway * 0.06f) {
        val flop = w.sway * 14f + sin(w.t * 2.2f) * 3f
        fill(Path().apply {
            moveTo(-74f, 0f); cubicTo(-70f, -64f, 10f, -86f, 64f + flop, -52f)
            quadraticTo(84f + flop, -36f, 80f + flop, -14f); quadraticTo(54f, -40f, 20f, -46f)
            cubicTo(56f, -30f, 72f, -14f, 74f, 0f); close()
        }, Color(0xFFD9363E))
        oval(-30f, -40f, 18f, 8f, Color.White.copy(alpha = 0.14f), -0.4f)
        var i = -78f
        while (i <= 78f) { circle(i, sin(i * 0.3f) * 1.5f, 11f, Color(0xFFFFF8F0)); i += 13f }
        pompom(82f + flop, -10f, 14f, Color(0xFFFFF8F0))
    }

    fun antlers(w: Wear, top: Float) {
        val brown = Color(0xFF9A6B45)
        for (s in intArrayOf(-1, 1)) at(s * 30f, top + 8f, s * (0.22f + w.sway * 0.05f * s)) {
            line(Path().apply { moveTo(0f, 0f); quadraticTo(s * 4f, -30f, s * 2f, -58f) }, brown, 10f)
            line(Path().apply {
                moveTo(s * 2f, -26f); quadraticTo(s * 14f, -34f, s * 24f, -48f)
                moveTo(s * 2f, -44f); quadraticTo(s * -10f, -54f, s * -14f, -66f)
            }, brown, 8f)
        }
        line(Path().apply { moveTo(-44f, top + 16f); quadraticTo(0f, top - 2f, 44f, top + 16f) }, Color(0xFF2E7D5B), 7f)
        at(0f, top + 8f, sin(w.t * 6f) * 0.12f) {
            fill(Path().apply { moveTo(-8f, 8f); quadraticTo(-8f, -6f, 0f, -7f); quadraticTo(8f, -6f, 8f, 8f); close() }, Color(0xFFF5B83D))
            circle(0f, 9f, 2.6f, Color(0xFFB7791F))
        }
    }

    // ── Headwear that is not a hat ────────────────────────────────────────────

    fun hearts(w: Wear, top: Float) {
        line(Path().apply { moveTo(-80f, top + 30f); quadraticTo(0f, top - 10f, 80f, top + 30f) }, Color(0xFFE5486F), 6f)
        for (s in intArrayOf(-1, 1)) {
            val bob = sin(w.t * 6f + s) * 4f + w.sway * 8f * s
            val bx = s * 30f + bob * 0.5f
            val by = top - 34f + abs(bob) * 0.3f
            line(Path().apply { moveTo(s * 22f, top + 10f); quadraticTo(s * 26f, top - 10f, bx, by + 10f) }, Color(0xFFB8344F), 2.5f)
            at(bx, by, bob * 0.03f) {
                fill(heart(24f), Color(0xFFFF5C8A))
                oval(-5f, -5f, 3f, 2f, Color.White.copy(alpha = 0.6f), -0.5f)
            }
        }
    }

    private fun arcY(px: Float, half: Float, top: Float, lift: Float) = top + 22f - cos((px / half) * PI.toFloat() * 0.5f) * 22f + lift

    fun flowerCrown(w: Wear, top: Float) {
        val pts = floatArrayOf(-70f, -42f, -14f, 14f, 42f, 70f)
        val colors = listOf(Color(0xFFFF8FB1), Color(0xFFFFD447), Color(0xFFB39DFF), Color(0xFFFF8FB1), Color(0xFF7FD1F7), Color(0xFFFFD447))
        line(Path().apply { moveTo(-82f, top + 30f); quadraticTo(0f, top - 6f, 82f, top + 30f) }, Color(0xFF5DAA68), 5f)
        pts.forEachIndexed { i, px -> leaf(px, arcY(px, 82f, top, 6f), 18f, (if (i % 2 == 1) -0.6f else -2.5f) + w.sway * 0.2f, Color(0xFF6CBF76)) }
        pts.forEachIndexed { i, px -> flower(px, arcY(px, 82f, top, 2f), 13f + (i % 2) * 2f, colors[i], Color(0xFFFFF4D6), 5, w.t * 0.3f + i) }
    }

    fun marigolds(w: Wear, top: Float) {
        val pts = floatArrayOf(-76f, -50f, -24f, 2f, 28f, 54f, 78f)
        line(Path().apply { moveTo(-88f, top + 32f); quadraticTo(0f, top - 6f, 88f, top + 32f) }, Color(0xFF3E8E4A), 5f)
        pts.forEachIndexed { i, px -> leaf(px - 6f, arcY(px, 88f, top, 4f) + 6f, 16f, 2.4f + i * 0.3f, Color(0xFF4FA35B)) }
        pts.forEachIndexed { i, px ->
            val py = arcY(px, 88f, top, 0f) + sin(w.t * 2f + i) * 1.2f
            val r = 14f + if (i % 3 == 1) 3f else 0f
            for (ring in 0 until 3) {
                val rr = r * (1f - ring * 0.28f)
                val c = when (ring) { 0 -> Color(0xFFF28C18); 1 -> Color(0xFFF9A826); else -> Color(0xFFFFC24B) }
                for (p in 0 until 9) {
                    val ang = p / 9f * PI.toFloat() * 2f + ring * 0.35f
                    circle(px + cos(ang) * rr * 0.55f, py + sin(ang) * rr * 0.55f, rr * 0.5f, c)
                }
            }
            circle(px, py, r * 0.22f, Color(0xFFE06C00))
        }
    }

    fun tricolor(w: Wear, top: Float) = at(56f, top + 26f, 0.2f + w.sway * 0.1f) {
        for ((dx, c, ang) in listOf(Triple(-1f, Color(0xFF006847), 0.35f), Triple(1f, Color(0xFFCE1126), -0.35f))) {
            at(0f, 0f, ang + sin(w.t * 2.4f + dx) * 0.06f + w.sway * 0.2f) {
                fill(Path().apply { moveTo(-6f, 0f); lineTo(6f, 0f); lineTo(8f, 40f); lineTo(0f, 33f); lineTo(-8f, 40f); close() }, c)
            }
        }
        for ((r, c) in listOf(24f to Color(0xFF006847), 17f to Color.White, 10f to Color(0xFFCE1126))) {
            fill(Path().apply {
                for (i in 0..24) {
                    val ang = i / 24f * PI.toFloat() * 2f
                    val rr = r + if (i % 2 == 1) 2f else 0f
                    if (i == 0) moveTo(cos(ang) * rr, sin(ang) * rr) else lineTo(cos(ang) * rr, sin(ang) * rr)
                }
                close()
            }, c)
        }
        circle(0f, 0f, 4f, Color(0xFFF5B83D))
    }

    fun bow(w: Wear, top: Float) = at(46f, top + 18f, 0.3f + w.sway * 0.12f) {
        val pink = Color(0xFFFF6FA8)
        for (s in intArrayOf(-1, 1)) {
            fill(Path().apply { moveTo(0f, 0f); cubicTo(s * 20f, -26f, s * 44f, -18f, s * 40f, 0f); cubicTo(s * 44f, 18f, s * 20f, 26f, 0f, 0f) }, pink)
            fill(Path().apply { moveTo(s * 6f, 0f); quadraticTo(s * 22f, -10f, s * 30f, 0f); quadraticTo(s * 22f, 8f, s * 6f, 0f) }, TokkipArt.shade(pink, -0.18f))
        }
        rrect(-9f, -10f, 18f, 20f, 7f, TokkipArt.shade(pink, -0.1f))
    }

    fun headphones(w: Wear) {
        val beat = maxOf(0f, sin(w.t * 12f)) * 1.5f
        for (s in intArrayOf(-1, 1)) at(s * 102f, 6f) {
            rrect(-16f, -30f, 32f, 60f, 14f, Color(0xFF2B2F3A))
            rrect(-13f - beat, -26f - beat, 26f + beat * 2, 52f + beat * 2, 12f, Color(0xFF7C5CFF))
            oval(-4f, -12f, 4f, 9f, Color.White.copy(alpha = 0.25f))
        }
    }

    fun scarf(w: Wear) = at(0f, 66f) {
        val red = Color(0xFFE0703A)
        val stripe = Color(0xFFFFE3C2)
        fill(Path().apply { moveTo(-104f, -14f); quadraticTo(0f, 6f, 104f, -14f); lineTo(104f, 12f); quadraticTo(0f, 32f, -104f, 12f); close() }, red)
        for (dy in floatArrayOf(-4f, 6f)) line(Path().apply { moveTo(-104f, dy); quadraticTo(0f, dy + 20f, 104f, dy) }, stripe, 4f, StrokeCap.Butt)
        at(46f, 14f, 0.12f + w.sway * 0.3f + sin(w.t * 2f) * 0.04f) {
            rrect(-14f, 0f, 28f, 58f, 6f, TokkipArt.shade(red, -0.08f))
            for (sy in floatArrayOf(16f, 30f)) line(Path().apply { moveTo(-14f, sy); lineTo(14f, sy) }, stripe, 4f, StrokeCap.Butt)
            var fx = -10f
            while (fx <= 10f) { line(Path().apply { moveTo(fx, 58f); lineTo(fx, 66f) }, TokkipArt.shade(red, -0.2f), 3f); fx += 5f }
        }
    }

    // ── Glasses (on his face) ─────────────────────────────────────────────────

    fun sunglasses(w: Wear) = at(w.fx, 14f + w.fy) {
        for (s in intArrayOf(-1, 1)) {
            val lens = Path().apply {
                addRoundRect(RoundRect(Rect(s * 46f - 27f, -18f, s * 46f + 27f, 18f), CornerRadius(12f), CornerRadius(12f), CornerRadius(18f), CornerRadius(18f)))
            }
            ds.drawPath(lens, Brush.verticalGradient(listOf(Color(0xFF2A2F45), Color(0xFF11131C)), -20f, 18f), alpha = a)
            fill(Path().apply { moveTo(s * 46f - 18f, -12f); lineTo(s * 46f - 6f, -12f); lineTo(s * 46f - 16f, 6f); lineTo(s * 46f - 24f, 6f); close() }, Color.White.copy(alpha = 0.22f))
        }
        line(Path().apply {
            moveTo(-19f, -8f); quadraticTo(0f, -14f, 19f, -8f)
            moveTo(-73f, -10f); lineTo(-96f, -16f); moveTo(73f, -10f); lineTo(96f, -16f)
        }, Color(0xFF11131C), 5f)
    }

    fun roundGlasses(w: Wear) = at(w.fx, 14f + w.fy) {
        val frame = Color(0xFFB7791F)
        for (s in intArrayOf(-1, 1)) {
            circle(s * 46f, 0f, 23f, Color(0x2EC8E6FF))
            ds.drawCircle(frame, 23f, Offset(s * 46f, 0f), alpha = a, style = Stroke(4f))
            ds.drawArc(Color.White.copy(alpha = 0.55f), 198f, 63f, false, Offset(s * 46f - 16f, -16f), Size(32f, 32f), alpha = a, style = Stroke(2.5f, cap = StrokeCap.Round))
        }
        line(Path().apply {
            moveTo(-23f, -2f); quadraticTo(0f, -12f, 23f, -2f)
            moveTo(-69f, -4f); lineTo(-94f, -10f); moveTo(69f, -4f); lineTo(94f, -10f)
        }, frame, 4f)
    }
}

/** A heart of size `s`, centred on (0, 0). */
internal fun heart(s: Float) = Path().apply {
    moveTo(0f, s * 0.42f)
    cubicTo(-s * 0.2f, s * 0.26f, -s, -s * 0.02f, -s * 0.62f, -s * 0.5f)
    cubicTo(-s * 0.38f, -s * 0.78f, -s * 0.06f, -s * 0.66f, 0f, -s * 0.36f)
    cubicTo(s * 0.06f, -s * 0.66f, s * 0.38f, -s * 0.78f, s * 0.62f, -s * 0.5f)
    cubicTo(s, -s * 0.02f, s * 0.2f, s * 0.26f, 0f, s * 0.42f)
    close()
}

/** A star with `points` points. */
internal fun star(ro: Float, ri: Float, points: Int = 5) = Path().apply {
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 1) ri else ro
        val ang = -PI.toFloat() / 2f + i * PI.toFloat() / points
        if (i == 0) moveTo(cos(ang) * r, sin(ang) * r) else lineTo(cos(ang) * r, sin(ang) * r)
    }
    close()
}

/** A four-pointed twinkle. */
internal fun twinkle(r: Float) = Path().apply {
    moveTo(0f, -r)
    quadraticTo(r * 0.16f, -r * 0.16f, r, 0f)
    quadraticTo(r * 0.16f, r * 0.16f, 0f, r)
    quadraticTo(-r * 0.16f, r * 0.16f, -r, 0f)
    quadraticTo(-r * 0.16f, -r * 0.16f, 0f, -r)
    close()
}

internal fun deg(rad: Float) = rad * 180f / PI.toFloat()
