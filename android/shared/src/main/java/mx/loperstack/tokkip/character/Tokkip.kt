// Tokkip — the app's own companion, in four species (bunny, kitten, puppy and
// a little robot) and two skins (plain and "Ensueño", pastel patches with
// sparkles and moons). Drawn flat in a 200-unit box (the body spans x -100…100,
// y -90…120; the face sits at y +20), the same geometry as the design studio.
// TokkipEngine keeps all the motion (squash, hops, dizzy spells, hugs, particles)
// and calls these pieces in its own transform.

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class Species(val label: String) { CONEJO("Conejito"), GATO("Gatito"), PERRO("Perrito"), ROBOT("Robotito"), MAPACHE("Mapache") ;
    companion object { fun of(name: String?) = entries.firstOrNull { it.name == name } ?: CONEJO }
}

enum class Skin(val label: String) { LISA("Lisa"), ENSUENO("Ensueño") ;
    companion object { fun of(name: String?) = entries.firstOrNull { it.name == name } ?: LISA }
}

/** What Tokkip's mouth does, picked from the eyes he's making. */
enum class Mouth { SMILE, BIG, OPEN, O, SLEEP_O, FLAT, FROWN, WAVY, CAT, TONGUE }

/** Everything the drawing needs for one frame, in studio units. */
class TokkipPose(
    val species: Species, val skin: Skin, val body: Color, val accent: Color, val t: Float,
    /** Extra ear angle in degrees (left, right), e.g. a wave or a wiggle. */
    val earL: Float, val earR: Float,
    /** + droops the ears (sad, sleepy), − perks them up (surprised, happy). */
    val droop: Float,
    val happy: Boolean,
    /** Seconds into a wave, or −1. */
    val wave: Float,
    /** Vertical speed of the body (studio units/frame), for the floppy bits. */
    val bounce: Float,
    /** Your own colours for the eyes and mouth, and the cheeks (Unspecified = automatic). */
    val ink: Color = Color.Unspecified,
    val cheek: Color = Color.Unspecified,
)

object TokkipArt {
    fun shade(c: Color, k: Float): Color = if (k < 0) lerp(c, Color.Black, -k) else lerp(c, Color.White, k)

    /** The face ink: the accent, a touch darker (or glowing, on the robot). */
    fun ink(p: TokkipPose): Color = when {
        p.ink != Color.Unspecified -> p.ink
        p.species == Species.ROBOT -> shade(p.accent, 0.55f)
        p.species == Species.MAPACHE -> Color(0xFF1B1616)
        else -> shade(p.accent, -0.12f)
    }

    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) = Path().apply {
        addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(r, r)))
    }

    fun bodyPath(s: Species): Path = when (s) {
        Species.ROBOT -> rrect(-104f, -84f, 208f, 198f, 62f)
        Species.MAPACHE -> rrect(-108f, -84f, 216f, 204f, 92f)
        Species.PERRO -> rrect(-106f, -78f, 212f, 198f, 90f)
        Species.GATO -> rrect(-108f, -88f, 216f, 208f, 94f)
        Species.CONEJO -> rrect(-100f, -90f, 200f, 210f, 54f)
    }

    /** Where hats sit: the head's top, and how wide a hat may be between the ears. */
    fun hatTop(s: Species) = when (s) { Species.ROBOT, Species.MAPACHE -> 84f; Species.PERRO -> 76f; Species.GATO -> 88f; Species.CONEJO -> 90f }
    fun hatHalfWidth(s: Species) = when (s) { Species.CONEJO, Species.GATO, Species.MAPACHE -> 70f; else -> 92f }

    // ── Behind the body: ears (bunny, kitten) and tails ───────────────────────

    private val dogEar = floatArrayOf(0f, 0f)

    fun DrawScope.back(p: TokkipPose) {
        val dream = p.skin == Skin.ENSUENO
        if (true) tail(p, dream)
        when (p.species) {
            Species.CONEJO -> for (sd in intArrayOf(-1, 1)) {
                val ang = sd * 10f + sd * p.droop + if (sd < 0) p.earL else p.earR
                withTransform({ translate(sd * 50f, -84f); rotate(ang, Offset.Zero) }) {
                    val fill = if (dream) (if (sd < 0) Color(0xFFF2906F) else Color(0xFFF7D35B)) else p.body
                    val inner = if (dream) (if (sd < 0) Color(0xFF9FE0C2) else Color(0xFFB98AD8)) else p.accent
                    bunnyEar(fill, inner)
                }
            }
            Species.GATO -> for (sd in intArrayOf(-1, 1)) {
                val ang = sd * 8f + sd * p.droop * 0.35f + (if (sd < 0) p.earL else p.earR) * 0.45f
                withTransform({ translate(sd * 58f, -72f); rotate(ang, Offset.Zero) }) {
                    catEar(p.body, if (dream) (if (sd < 0) Color(0xFFF7B79E) else Color(0xFFD9C2F0)) else shade(p.accent, 0.55f))
                }
            }
            Species.MAPACHE -> for (sd in intArrayOf(-1, 1)) {
                val ang = sd * 6f + sd * p.droop * .3f + (if (sd < 0) p.earL else p.earR) * .4f
                withTransform({ translate(sd * 64f, -70f); rotate(ang, Offset.Zero) }) { coonEar(p.body) }
            }
            else -> Unit
        }
    }

    /** Pointed with a soft tip: a pale rim, the grey ear, a dark inside. */
    private fun DrawScope.coonEar(body: Color) {
        fun ear(k: Float, dy: Float) = Path().apply {
            moveTo(-34f * k, 22f + dy); quadraticTo(-30f * k, -26f * k + dy, -6f * k, -54f * k + dy)
            quadraticTo(0f, -60f * k + dy, 6f * k, -54f * k + dy); quadraticTo(30f * k, -26f * k + dy, 34f * k, 22f + dy); close()
        }
        drawPath(ear(1.06f, 0f), shade(body, .6f))
        drawPath(ear(.94f, 4f), shade(body, -.18f))
        drawPath(ear(.56f, 12f), shade(body, -.52f))
    }

    private fun DrawScope.bunnyEar(fill: Color, inner: Color) {
        fun ear(len: Float, w: Float) = Path().apply {
            moveTo(-w * .45f, 6f)
            cubicTo(-w * 1.05f, -len * .45f, -w * .65f, -len, 0f, -len)
            cubicTo(w * .65f, -len, w * 1.05f, -len * .45f, w * .45f, 6f)
            close()
        }
        drawPath(ear(128f, 46f), fill)
        withTransform({ translate(0f, -8f) }) { drawPath(ear(128f * .78f, 46f * .52f), inner) }
    }

    private fun DrawScope.catEar(fill: Color, inner: Color) {
        fun shape(k: Float) = Path().apply {
            moveTo(-40f * k, 34f)
            quadraticTo(-30f * k, -40f * k, -2f, -78f * k)
            quadraticTo(6f, -84f * k, 12f * k, -76f * k)
            quadraticTo(36f * k, -36f * k, 42f * k, 34f)
            close()
        }
        drawPath(shape(1f), fill)
        withTransform({ translate(2f, 10f) }) { drawPath(shape(.6f), inner) }
    }

    private fun DrawScope.tail(p: TokkipPose, dream: Boolean) {
        when (p.species) {
            Species.CONEJO -> drawCircle(if (dream) Color(0xFF2433A8) else p.accent, 26f, Offset(96f + sin(p.t * 3f) * 2f, 74f))
            Species.GATO -> {
                val w = sin(p.t * 2.2f) * 8f
                val path = Path().apply {
                    moveTo(70f, 104f)
                    cubicTo(150f, 110f, 170f + w, 40f, 150f + w, -6f)
                    cubicTo(140f + w, -30f, 116f + w, -24f, 122f + w, -6f)
                }
                drawPath(path, p.body, style = Stroke(34f, cap = StrokeCap.Round))
                if (dream) {
                    drawCircle(Color(0xE6CDB4EB), 17f, Offset(152f + w, -4f))
                    drawCircle(Color(0xD9F8CEA0), 14f, Offset(150f + w * .7f, 62f))
                }
            }
            Species.PERRO -> {
                val a = sin(p.t * (if (p.happy) 18f else 5f)) * (if (p.happy) .5f else .25f)
                withTransform({ translate(92f, 84f); rotate(((-.7f + a) * 180f / PI).toFloat(), Offset.Zero) }) {
                    drawOval(if (dream) Color(0xFFF7D35B) else p.body, Offset(-15f, -56f), Size(30f, 60f))
                }
            }
            Species.MAPACHE -> {
                // A bushy ringed tail.
                val w = sin(p.t * (if (p.happy) 7f else 2.2f)) * (if (p.happy) 10f else 6f)
                for (i in 8 downTo 0) {
                    val u = i / 8f
                    val x = 76f + 22f + u * 74f + w * u * u
                    val y = 96f - u * 96f - sin(u * 2.4f) * 10f
                    val rx = 24f - u * 4f; val ry = 20f - u * 2f
                    withTransform({ rotate(((-.9f + u * .5f) * 180f / PI).toFloat(), Offset(x, y)) }) {
                        drawOval(if (i % 2 == 1) shade(p.body, -.5f) else shade(p.body, -.08f), Offset(x - rx, y - ry), Size(rx * 2, ry * 2))
                    }
                }
            }
            Species.ROBOT -> {
                // A wind-up key at the back.
                val k = sin(p.t * (if (p.happy) 9f else 3f))
                withTransform({ translate(104f, 70f) }) {
                    drawRoundRect(Color(0xFFB9C2CE), Offset(0f, -5f), Size(26f, 10f), CornerRadius(4f, 4f))
                    withTransform({ translate(30f, 0f); scale(1f, max(.15f, abs(k)), Offset.Zero) }) {
                        for (sd in intArrayOf(-1, 1)) drawOval(Color(0xFFF2C14E), Offset(-2f, sd * 14f - 13f), Size(20f, 26f))
                        drawCircle(Color(0xFFC29A3E), 6f, Offset(2f, 0f))
                    }
                }
            }
        }
    }

    // ── The body and what lies on it ──────────────────────────────────────────

    fun DrawScope.body(p: TokkipPose) {
        val path = bodyPath(p.species)
        drawPath(path, p.body)
        clipPath(path) {
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .18f), Color.Black.copy(alpha = .05f)), -90f, 120f), Offset(-120f, -100f), Size(240f, 230f))
            if (p.skin == Skin.ENSUENO) dreamPatches(p.t)
            else if (p.species == Species.PERRO) dogPatch(p.body)
        }
        when (p.species) {
            Species.PERRO -> dogEars(p)
            Species.ROBOT -> visor(p)
            else -> Unit
        }
    }

    private fun DrawScope.sparkle(x: Float, y: Float, r: Float, c: Color, t: Float, ph: Float) {
        val k = .8f + .2f * sin(t * 2.4f + ph)
        val s = r * k
        val path = Path().apply {
            moveTo(x, y - s); quadraticTo(x, y, x + s, y); quadraticTo(x, y, x, y + s); quadraticTo(x, y, x - s, y); quadraticTo(x, y, x, y - s); close()
        }
        drawPath(path, c)
    }

    private fun DrawScope.moon(x: Float, y: Float, r: Float, c: Color, bg: Color) {
        drawCircle(c, r, Offset(x, y)); drawCircle(bg, r * .85f, Offset(x + r * .45f, y - r * .3f))
    }

    private fun DrawScope.dreamPatches(t: Float) {
        drawCircle(Color(0xD9F9D6B0), 88f, Offset(95f, -95f))
        drawCircle(Color(0xBFF7E296), 40f, Offset(-104f, 30f))
        drawCircle(Color(0xE6DCC6F0), 40f, Offset(98f, 72f))
        drawCircle(Color(0xE6DCC6F0), 10f, Offset(96f, 14f))
        drawCircle(Color(0xD9F6CAC4), 56f, Offset(12f, 152f))
        drawPath(Path().apply { moveTo(-104f, 42f); lineTo(8f, 42f); lineTo(62f, 92f); lineTo(8f, 142f); lineTo(-104f, 142f); close() }, Color(0xD9F8D6A5))
        drawCircle(Color(0xE6F6D2B0), 9f, Offset(-30f, -64f))
        drawCircle(Color(0xD9FFFFFF), 6f, Offset(84f, -24f))
        drawCircle(Color(0xE6FFF4D6), 9f, Offset(-70f, 70f))
        moon(68f, -58f, 11f, Color(0xF2FFFAF0), Color(0xFFF9D6B0))
        moon(-24f, 100f, 12f, Color(0xF2F6C896), Color(0xFFF8D6A5))
        sparkle(-68f, -48f, 13f, Color(0xFFF2C59A), t, 0f)
        sparkle(-44f, -24f, 7f, Color(0xFFF2C14E), t, 1.3f)
        sparkle(56f, 96f, 12f, Color(0xFFBFA8EC), t, 2.1f)
        sparkle(-60f, 110f, 9f, Color(0xFFFFF7E8), t, 3.4f)
    }

    private fun DrawScope.dogPatch(body: Color) {
        drawPath(Path().apply {
            moveTo(18f, 0f); cubicTo(14f, -36f, 64f, -48f, 84f, -22f); cubicTo(100f, 2f, 86f, 46f, 54f, 46f); cubicTo(26f, 46f, 20f, 24f, 18f, 0f)
        }, shade(body, -.2f))
    }

    private fun DrawScope.dogEars(p: TokkipPose) {
        // A little pendulum: they lag when he hops and swing back.
        dogEar[1] += (-.12f * dogEar[0] - .14f * dogEar[1]) + p.bounce * .02f
        dogEar[0] += dogEar[1]
        val perk = (-p.droop / 20f).coerceIn(-1f, 1f)
        val dream = p.skin == Skin.ENSUENO
        val fill = if (dream) Color(0xFFF2906F) else shade(p.body, -.24f)
        val inner = if (dream) Color(0xFFF7D35B) else shade(p.body, -.12f)
        for (sd in intArrayOf(-1, 1)) {
            val ang = (-sd * (.32f + perk * .5f + dogEar[0] * .9f) + sin(p.t * 1.6f + sd) * .03f) * 180f / PI.toFloat()
            withTransform({ translate(sd * 96f, -54f); rotate(ang, Offset.Zero) }) {
                drawPath(Path().apply {
                    moveTo(-22f, 0f); cubicTo(-34f, 50f, -26f, 118f, 6f, 122f); cubicTo(36f, 120f, 40f, 50f, 22f, -4f); quadraticTo(0f, -14f, -22f, 0f); close()
                }, fill)
                drawPath(Path().apply {
                    moveTo(-10f, 20f); cubicTo(-16f, 60f, -10f, 104f, 6f, 106f); cubicTo(22f, 104f, 24f, 60f, 14f, 18f); close()
                }, inner)
            }
        }
    }

    private fun DrawScope.visor(p: TokkipPose) {
        val v = rrect(-84f, -48f, 168f, 116f, 40f)
        drawPath(v, Color(0xFF1C2230))
        clipPath(v) {
            drawRect(Color(0x5050DCE6).copy(alpha = .05f + .05f * (1 + sin(p.t * 2f))), Offset(-84f, -48f), Size(168f, 116f))
            withTransform({ rotate(-14f, Offset(-36f, -34f)) }) { drawOval(Color.White.copy(alpha = .1f), Offset(-76f, -46f), Size(80f, 24f)) }
        }
    }

    // ── The face's extras ─────────────────────────────────────────────────────

    /** The raccoon's bandit mask and light muzzle, under the eyes. Face space. */
    fun DrawScope.faceUnder(p: TokkipPose) {
        if (p.species != Species.MAPACHE) return
        drawPath(Path().apply {
            moveTo(-92f, -2f); cubicTo(-90f, -36f, -58f, -34f, -30f, -20f); quadraticTo(0f, -8f, 30f, -20f)
            cubicTo(58f, -34f, 90f, -36f, 92f, -2f); cubicTo(88f, 22f, 58f, 26f, 34f, 14f); quadraticTo(0f, 2f, -34f, 14f)
            cubicTo(-58f, 26f, -88f, 22f, -92f, -2f); close()
        }, shade(p.body, -.52f))
        drawOval(shade(p.body, .65f), Offset(-46f, 8f), Size(92f, 60f))
    }

    /** A white glint so dark eyes still sparkle (the raccoon's sit on his mask). */
    fun DrawScope.glint(p: TokkipPose, x: Float, y: Float) {
        if (p.species != Species.MAPACHE) return
        drawCircle(Color.White.copy(alpha = .9f), 3.6f, Offset(x - 4f, y - 5f))
    }

    /** Cheeks: soft ovals, or two pink lights on the robot. Face space (eyes at y −6). */
    fun DrawScope.blush(p: TokkipPose, amount: Float) {
        if (amount < .02f) return
        val base = if (p.cheek != Color.Unspecified) p.cheek else Color(0xFFF0788C)
        val c = base.copy(alpha = min(.7f, amount))
        for (sd in intArrayOf(-1, 1)) {
            if (p.species == Species.ROBOT) {
                drawRoundRect((if (p.cheek != Color.Unspecified) p.cheek else Color(0xFFF37A9A)).copy(alpha = .25f * amount * 1.4f), Offset(sd * 60f - 14f, 18f), Size(28f, 16f), CornerRadius(8f, 8f))
                drawRoundRect((if (p.cheek != Color.Unspecified) p.cheek else Color(0xFFF37A9A)).copy(alpha = min(1f, amount * 1.4f)), Offset(sd * 60f - 10f, 22f), Size(20f, 8f), CornerRadius(4f, 4f))
            } else drawOval(c, Offset(sd * 70f - 17f, 9f), Size(34f, 18f))
        }
    }

    fun DrawScope.nose(p: TokkipPose) {
        if (p.species != Species.PERRO && p.species != Species.MAPACHE) return
        val ink = if (p.species == Species.MAPACHE) Color(0xFF2A2222) else ink(p)
        drawPath(Path().apply { moveTo(-13f, 6f); quadraticTo(0f, 0f, 13f, 6f); quadraticTo(12f, 16f, 0f, 19f); quadraticTo(-12f, 16f, -13f, 6f); close() }, ink)
        drawOval(Color.White.copy(alpha = .45f), Offset(-8f, 5f), Size(8f, 4f))
    }

    fun DrawScope.mouth(p: TokkipPose, kind: Mouth) {
        val ink = ink(p)
        val y = when (p.species) { Species.PERRO -> 34f; Species.MAPACHE -> 38f; else -> 30f }
        val st = Stroke(5.5f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        fun arc(cx: Float, cy: Float, r: Float, a0: Float, a1: Float) =
            drawArc(ink, a0 * 180f, (a1 - a0) * 180f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = st)
        when (kind) {
            Mouth.SMILE -> arc(0f, y - 12f, 18f, .2f, .8f)
            Mouth.BIG -> arc(0f, y - 14f, 22f, .12f, .88f)
            Mouth.OPEN -> {
                drawPath(Path().apply { moveTo(-18f, y - 4f); quadraticTo(0f, y + 26f, 18f, y - 4f); close() }, ink)
                drawOval(Color(0xFFE98A97), Offset(-8f, y + 3f), Size(16f, 10f))
            }
            Mouth.O -> drawOval(ink, Offset(-8f, y - 10f), Size(16f, 20f))
            Mouth.SLEEP_O -> { val h = 5f + sin(p.t * 1.4f) * 1.5f; drawOval(ink, Offset(-5f, y - h), Size(10f, h * 2)) }
            Mouth.FLAT -> drawLine(ink, Offset(-12f, y), Offset(12f, y), 5.5f, StrokeCap.Round)
            Mouth.FROWN -> arc(0f, y + 16f, 16f, 1.22f, 1.78f)
            Mouth.WAVY -> drawPath(Path().apply {
                var x = -18f; moveTo(x, y)
                while (x <= 18f) { lineTo(x, y + sin(x * .45f + p.t * 9f) * 3.5f); x += 2f }
            }, ink, style = st)
            Mouth.CAT -> drawPath(Path().apply { moveTo(-14f, y - 4f); quadraticTo(-7f, y + 6f, 0f, y - 2f); quadraticTo(7f, y + 6f, 14f, y - 4f) }, ink, style = st)
            Mouth.TONGUE -> { arc(0f, y - 12f, 18f, .2f, .8f); drawOval(Color(0xFFE98A97), Offset(0f, y), Size(12f, 14f)) }
        }
        // The puppy pants when he's happy.
        if (p.species == Species.PERRO && p.happy && kind in setOf(Mouth.SMILE, Mouth.BIG, Mouth.OPEN, Mouth.CAT)) {
            val b = sin(p.t * 12f) * 2f
            drawPath(Path().apply { moveTo(-9f, y + 4f); lineTo(9f, y + 4f); quadraticTo(10f, y + 22f + b, 0f, y + 24f + b); quadraticTo(-10f, y + 22f + b, -9f, y + 4f); close() }, Color(0xFFEF8C9C))
        }
    }

    /** A soft glow behind the robot's eyes. Face space. */
    fun DrawScope.eyeGlow(p: TokkipPose, x: Float, y: Float) {
        if (p.species != Species.ROBOT) return
        drawCircle(p.accent.copy(alpha = .22f), 22f, Offset(x, y))
    }

    // ── In front: the robot's floating hands ──────────────────────────────────

    fun DrawScope.front(p: TokkipPose, hug: Float) {
        if (p.species != Species.ROBOT) return
        var lx = -118f; var ly = 12f + sin(p.t * 2f) * 4f
        var rx = 118f; var ry = 12f + sin(p.t * 2f + 1.6f) * 4f
        if (p.wave >= 0f) {
            val inOut = min(1f, p.wave / .25f) * min(1f, max(0f, (1.6f - p.wave) / .3f))
            rx += (6f + sin(p.wave * 13f) * 16f) * inOut
            ry += (-92f + cos(p.wave * 13f) * 4f) * inOut
        }
        if (p.happy) { ly -= 8f; ry -= 8f }
        lx += 50f * hug; rx -= 50f * hug; ly += 46f * hug; ry += 46f * hug
        for ((x, y) in listOf(lx to ly, rx to ry)) {
            drawCircle(Color.Black.copy(alpha = .08f), 20f, Offset(x + 2f, y + 3f))
            drawCircle(shade(p.body, -.16f), 20f, Offset(x, y))
            drawCircle(shade(p.body, -.1f), 13f, Offset(x, y))
        }
    }
}
