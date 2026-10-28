// Tokkip's motion engine on Android: the same ideas as Tokkip PC's
// (windows/src/tokkip/motion.ts). Every part of him that moves is a spring
// chasing a target; his mood sets the targets and his face; *acts* — short
// choreographies written as functions of time (a hop, a flip, a wave, a sneeze,
// a hug) — add on top while they play. Emotes, mimos, greetings and the little
// things he does on his own are all acts.
//
// Plain Kotlin: no Compose state is touched per frame. The Tokkip composable
// calls update() on a frame clock only while `busy`, and draw() in its Canvas;
// widgets call draw() once into a bitmap. The figure's pieces (ears, body,
// cheeks, nose, mouth) are TokkipArt's; the clothes are Ropa's.

package mx.loperstack.tokkip.character

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.channels.Channel
import mx.loperstack.tokkip.wellness.Breathing
import mx.loperstack.tokkip.wellness.breathFrame
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * How Tokkip looks everywhere (app, watch, widgets, tiles). State, so a change
 * repaints every Tokkip on screen.
 */
object TokkipStyle {
    var flat by mutableStateOf(true)
    /** What he wears today (the season's, or the one you picked). */
    var outfit by mutableStateOf(Outfit.NONE)
    /** How you slept last night: he shows it. */
    var rest by mutableStateOf(Rest.UNKNOWN)
    /** Tokkip's species, skin and accent (ears, eyes, mouth, tail). */
    var species by mutableStateOf(Species.CONEJO)
    var skin by mutableStateOf(Skin.LISA)
    var accent by mutableStateOf(Color(0xFF3F9E9A))
    /** Your own eye/mouth and cheek colours (Unspecified = automatic). */
    var ink by mutableStateOf(Color.Unspecified)
    var cheek by mutableStateOf(Color.Unspecified)
}

/** Last night, as Tokkip wears it: bags under his eyes, or bouncing with joy. */
enum class Rest { UNKNOWN, TIRED, OK, GREAT }

// ── Little maths ──────────────────────────────────────────────────────────────

private val PI_F = PI.toFloat()
private val TWO_PI = PI_F * 2f
private const val INF = Double.POSITIVE_INFINITY
private val SLEEPING = Color(0.58f, 0.635f, 0.722f)
private val DIZZY = Color(0.957f, 0.447f, 0.714f)

private fun seg(u: Float, a: Float, b: Float) = ((u - a) / (b - a)).coerceIn(0f, 1f)
private fun bump(u: Float, a: Float, b: Float): Float {
    val k = seg(u, a, b)
    return if (k > 0f && k < 1f) sin(PI_F * k) else 0f
}
private fun arc(u: Float, a: Float, b: Float): Float {
    val k = seg(u, a, b)
    return 4f * k * (1f - k)
}
private fun smooth(k: Float) = k * k * (3f - 2f * k)
private fun mix(a: Float, b: Float, k: Float) = a + (b - a) * k
private fun rand(a: Double, b: Double) = a + Random.nextDouble() * (b - a)
private fun randF(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

/** A damped spring: `hz` how lively, `damp` how fast it settles (1 = no overshoot). */
private class Spring(var v: Float, val hz: Float = 3f, val damp: Float = 0.7f) {
    var vel = 0f
    var target = v

    fun step(dt: Float) {
        val w = TWO_PI * hz
        var left = dt
        while (left > 1e-6f) {
            val h = min(left, 1f / 120f)
            val a = w * w * (target - v) - 2f * damp * w * vel
            vel += a * h
            v += vel * h
            left -= h
        }
    }

    val moving: Boolean get() = abs(target - v) > 0.002f || abs(vel) > 0.01f

    fun snap(x: Float) { v = x; target = x; vel = 0f }
}

/** A paw: where it is (studio units) and how it's turned (radians). */
internal class Paw(val x: Float, val y: Float, val a: Float)

private fun mixPaw(a: Paw, b: Paw, k: Float) = Paw(mix(a.x, b.x, k), mix(a.y, b.y, k), mix(a.a, b.a, k))

/** What acts and moods add this frame: positions in body radii, angles in radians. */
private class Delta {
    var x = 0f; var y = 0f; var sx = 0f; var sy = 0f; var lean = 0f; var roll = 0f
    var turn = 0f; var nod = 0f; var earL = 0f; var earR = 0f; var droop = 0f; var eyeScale = 0f
    var pawL: Paw? = null; var pawLw = 0f
    var pawR: Paw? = null; var pawRw = 0f
}

/** His mouth: TokkipArt's shapes plus a few of the engine's own. */
internal enum class Lips { SMILE, BIG, OPEN, O, SLEEP_O, FLAT, FROWN, WAVY, CAT, TONGUE, GRIN, GAPE, CHEW, POUT }

private class Face(
    val eye: TokkipEngine.Eye? = null,
    val lips: Lips? = null,
    val brow: Float = 0f,
    val browUp: Float = 0f,
    val blush: Float = 0f,
)

private class Cue(val at: Float, val f: () -> Unit) { var fired = false }

private class Act(
    val name: String,
    val start: Double,
    val dur: Float,
    val face: Face? = null,
    val cues: List<Cue> = emptyList(),
    val done: (() -> Unit)? = null,
    /** Adds the act at progress `u` (0…1), `s` seconds in. */
    val play: (u: Float, s: Float, d: Delta) -> Unit,
)

private enum class Kind { HEART, STAR, TWINKLE, NOTE, Z, DROP, CONFETTI, STEAM }

private class Particle(
    val kind: Kind, var x: Float, var y: Float, val vx: Float, var vy: Float, val g: Float,
    var age: Float, val life: Float, var rot: Float, val spin: Float, val size: Float, val color: Color,
)

private val CONFETTI = listOf(Color(0xFFFF5C8A), Color(0xFFFFD447), Color(0xFF7C5CFF), Color(0xFF2FBF8A), Color(0xFF4C8DF6), Color(0xFFF7913D))

private fun P(x: Float, y: Float, a: Float = 0f) = Paw(x, y, a)

// ── Dancing ───────────────────────────────────────────────────────────────────

private const val DANCE_BPM = 116f

private class Move(val dx: Float, val dy: Float, val rotate: Float, val sx: Float, val sy: Float, val pawL: Float, val pawR: Float, val ears: Float)

/** Four moves of eight beats each (bounce, sway, twist, pump), blending into the next. */
private fun danceMove(seconds: Float): Move {
    val b = seconds * DANCE_BPM / 60f
    fun moveAt(i: Int): Move {
        val ph = b - floor(b)
        val up = sin(PI_F * ph)
        val land = (1f - up).pow(5)
        val odd = floor(b).toInt() % 2 == 1
        return when (((i % 4) + 4) % 4) {
            0 -> Move(0f, -0.15f * up, 0f, 1f + 0.05f * land, 1f - 0.07f * land, if (odd) 0f else up, if (odd) up else 0f, if (odd) 10f else -10f)
            1 -> {
                val sw = sin(PI_F * b)
                Move(0.09f * sw, -0.05f * abs(sin(PI_F * b * 2f)), 0.13f * sw, 1f, 1f, 0.5f + 0.5f * sw, 0.5f - 0.5f * sw, 18f * sw)
            }
            2 -> {
                val tw = sin(TWO_PI * b)
                Move(0f, -0.03f * up, 0.17f * tw, 1f + 0.04f * tw, 1f - 0.03f * tw, 0.7f, 0.7f, -14f * tw)
            }
            else -> {
                val pump = max(0f, cos(TWO_PI * b))
                Move(0f, -0.08f * up, 0f, 1f + 0.03f * land, 1f - 0.05f * land, pump, pump, 12f * pump)
            }
        }
    }
    val bar = floor(b / 8f).toInt()
    val into = b - bar * 8f
    val a = moveAt(bar)
    if (into < 7.5f) return a
    val n = moveAt(bar + 1)
    val k = smooth((into - 7.5f) / 0.5f)
    return Move(
        mix(a.dx, n.dx, k), mix(a.dy, n.dy, k), mix(a.rotate, n.rotate, k), mix(a.sx, n.sx, k),
        mix(a.sy, n.sy, k), mix(a.pawL, n.pawL, k), mix(a.pawR, n.pawR, k), mix(a.ears, n.ears, k),
    )
}

// ── The engine ────────────────────────────────────────────────────────────────

class TokkipEngine {
    enum class Eye { OPEN, WIDE, DOT, HAPPY, CLOSED, LINE, FLAT, SQUINT, SPIRAL, HEART, STAR, SLEEPY, WINK, SPARKLE, TEARY }
    enum class Emote { LOVE, SURPRISED, PROUD, WINK, YAWN, HAPPY, ANNOYED }

    // Springs: position (body radii), squash, lean, gaze, ears, eyes, cheeks, a hug, clothes.
    private val px = Spring(0f, 3.2f, 0.7f)
    private val py = Spring(0f, 3.2f, 0.62f)
    private val psx = Spring(1f, 4f, 0.45f)
    private val psy = Spring(1f, 4f, 0.45f)
    private val lean = Spring(0f, 2.6f, 0.6f)
    private val turn = Spring(0f, 2.2f, 0.8f)
    private val nod = Spring(0f, 2.2f, 0.8f)
    private val droop = Spring(0f, 2f, 0.6f)
    private val es = Spring(1f, 4f, 0.55f)
    private val blushS = Spring(0f, 1.5f, 0.9f)
    private val sway = Spring(0f, 1.6f, 0.35f)
    private val hugS = Spring(0f, 2.5f, 0.8f)
    private val outfitK = Spring(1f, 2.6f, 0.5f)
    private val springs = listOf(px, py, psx, psy, lean, turn, nod, droop, es, blushS, sway, hugS, outfitK)

    /** Eye size he grows towards (a finger on him). */
    var tgEs = 1f

    /** Pinched between two fingers: his stretch (> 1) or squash (< 1), or null. */
    private var held: Float? = null

    private var col = Color(0xFFE6E9EE)
    private var colT = col
    private var tint = 0f
    private var tintT = 0f

    private var eyeOverride: Eye? = null
    private var eyeOverrideUntil = 0.0
    private var emoteFace: Face? = null
    private var permanentEye: Eye? = null

    private val t0 = now() - Random.nextDouble() * 5
    private var blinkAt = -10.0
    private var nextBlink = now() + rand(1.5, 3.5)
    private var nextWander = now() + 2.0
    private var nextFidget = now() + rand(6.0, 10.0)
    private var nextAmbient = 0.0
    private var nextRestBeat = now() + 4.0
    private val pokes = ArrayList<Double>()
    private var lastHeart = 0.0

    // Where he looks: a finger on him, or a glance now and then. −1…1, y up.
    private var lookX = 0f
    private var lookY = 0f
    private var touchLook: Offset? = null

    // What the app asks for.
    private var configured = false
    private var userColor = Color(0xFFF5F6F8)
    var mood = Mood.IDLE
        private set
    private var session: Breathing? = null
    private var isBreathing = false
    private var breathFill = 0f
    private var breathLevel = 0f
    private var face: Eye? = null
    /** Wears TokkipStyle.outfit (off for the diary's little faces). */
    private var wear = true
    /** What he has on right now: it changes once the old one has lifted off. */
    private var worn: Outfit? = null
    private var landed = true

    private var dancing = false
    private var danceLevel = 0f
    private var danceT0 = now()

    private val acts = ArrayList<Act>()
    private val particles = ArrayList<Particle>()
    private var pawL = P(-80f, 96f)
    private var pawR = P(80f, 96f)
    private var lastLean = 0f
    private var lastX = 0f
    private var prevOy = 0f

    /** True for the 3.3 s of a dizzy spell (the app shows the "too many hits" bubble). */
    val dizzy = mutableStateOf(false)

    /** Lets the frame loop sleep while Tokkip is still, and wake on any input. */
    val wakeups = Channel<Unit>(Channel.CONFLATED)
    private fun wake() { wakeups.trySend(Unit) }

    private fun now() = System.nanoTime() / 1e9

    private val timers = ArrayList<Pair<Double, () -> Unit>>()
    private fun after(ms: Int, f: () -> Unit) { timers += (now() + ms / 1000.0) to f }

    /** Says hello; each screen can have its own sound (a lullaby in Sueño). */
    var greetSound = "greet"

    // ── What the app sets ─────────────────────────────────────────────────────

    /** The body colour and mood, and the breathing session (if any). Cheap to call on every composition. */
    fun configure(color: Color, mood: Mood, breathing: Breathing?, dancing: Boolean = false, face: Eye? = null, wear: Boolean = true) {
        this.wear = wear
        val s = breathing?.takeIf { it.startedAt != null }
        session = s
        if (dancing != this.dancing) {
            if (dancing && danceLevel < 0.01f) danceT0 = now()
            this.dancing = dancing
            wake()
        }
        val changed = !configured || color != userColor || mood != this.mood || face != this.face
        this.face = face
        userColor = color
        this.mood = mood
        val on = s != null || mood == Mood.BREATHING
        if (on != isBreathing) {
            isBreathing = on
            applyBase()
            wake()
        } else if (changed) {
            applyBase()
            wake()
        }
        if (!configured) {
            col = colT
            tint = tintT
            configured = true
        }
    }

    /** White is his own colour; any other colour tints him. */
    private fun tintFor(c: Color) = if (c.luminance() > 0.8f) 0f else 0.72f

    private fun applyBase() {
        if (dizzy.value) return
        val sleeping = mood == Mood.SLEEPY
        colT = if (sleeping) SLEEPING else userColor
        tintT = if (sleeping) 0.32f else tintFor(userColor)
        permanentEye = when {
            isBreathing || sleeping -> Eye.CLOSED
            face != null -> face
            mood == Mood.HAPPY -> Eye.HAPPY
            else -> null
        }
        eyeOverride = permanentEye
        eyeOverrideUntil = if (permanentEye != null) INF else 0.0
        emoteFace = null
    }

    /** A finger on Tokkip (−1…1, y up), or null when it lifts. */
    fun lookAt(target: Offset?) {
        touchLook = target
        tgEs = if (target != null) 1.08f else 1f
        wake()
    }

    // ── Acts ──────────────────────────────────────────────────────────────────

    private fun play(act: Act) {
        acts.removeAll { it.name == act.name }
        acts += act
        wake()
    }

    private fun emote(face: Face, seconds: Float) {
        emoteFace = face
        eyeOverride = face.eye
        eyeOverrideUntil = now() + seconds
    }

    private fun actHop(h: Float, dur: Float) = Act("hop", now(), dur) { u, _, d ->
        d.y -= h * arc(u, 0.15f, 0.85f)
        d.sy += -0.14f * bump(u, 0f, 0.18f) + 0.1f * bump(u, 0.15f, 0.5f) - 0.12f * bump(u, 0.82f, 1f)
        d.sx += 0.12f * bump(u, 0f, 0.18f) - 0.06f * bump(u, 0.15f, 0.5f) + 0.1f * bump(u, 0.82f, 1f)
        d.earL -= 14f * bump(u, 0.15f, 0.6f)
        d.earR += 14f * bump(u, 0.15f, 0.6f)
    }

    private fun actSpin(dur: Float, turns: Int) = Act("spin", now(), dur) { u, _, d -> d.roll += TWO_PI * turns * smooth(u) }

    private fun actShake(dur: Float) = Act("shake", now(), dur) { u, _, d ->
        val fade = 1f - u
        d.x += sin(u * PI_F * 9f) * 0.06f * fade
        d.lean += sin(u * PI_F * 9f + 0.6f) * 0.08f * fade
        d.turn += sin(u * PI_F * 9f) * 0.3f * fade
    }

    private fun actWave(dur: Float) = Act("wave", now(), dur) { u, s, d ->
        val up = seg(u, 0f, 0.14f) * (1f - seg(u, 0.86f, 1f))
        d.pawR = P(116f, -48f, -0.3f + sin(s * 14f) * 0.5f); d.pawRw = up
        d.lean -= 0.07f * up
        d.y -= 0.1f * arc(u, 0f, 0.22f)
        d.earR += sin(s * 14f) * 10f * up
        d.turn += 0.15f * up
    }

    private fun actLove(dur: Float) = Act(
        "love", now(), dur,
        cues = listOf(Cue(0.05f) { emit(Kind.HEART, 3, -0.1f) }, Cue(0.7f) { emit(Kind.HEART, 2, -0.1f) }),
    ) { u, s, d ->
        val on = seg(u, 0f, 0.1f) * (1f - seg(u, 0.85f, 1f))
        d.lean += sin(s * 3.2f) * 0.08f * on
        d.y -= 0.08f * arc(u, 0f, 0.18f)
        d.pawL = P(-30f, 66f, 0.5f); d.pawLw = on
        d.pawR = P(30f, 66f, -0.5f); d.pawRw = on
    }

    private fun actSurprise() = Act("surprise", now(), 0.8f, cues = listOf(Cue(0.05f) { emit(Kind.TWINKLE, 3, -0.4f) })) { u, _, d ->
        d.y -= 0.32f * arc(u, 0f, 0.45f)
        d.sy += 0.14f * bump(u, 0f, 0.3f) - 0.1f * bump(u, 0.4f, 0.6f)
        d.sx -= 0.08f * bump(u, 0f, 0.3f)
        d.droop -= 22f * bump(u, 0f, 0.9f)
        d.eyeScale += 0.25f * bump(u, 0f, 0.7f)
        val up = bump(u, 0f, 0.6f)
        d.pawL = P(-112f, -20f); d.pawLw = up
        d.pawR = P(112f, -20f); d.pawRw = up
    }

    private fun actProud(dur: Float) = Act("proud", now(), dur, cues = listOf(Cue(0.1f) { emit(Kind.STAR, 5, -0.2f) })) { u, _, d ->
        val on = smooth(seg(u, 0f, 0.15f)) * (1f - smooth(seg(u, 0.8f, 1f)))
        d.sy += 0.08f * on
        d.sx += 0.04f * on
        d.lean -= 0.1f * on
        d.nod += 0.2f * on
        d.pawL = P(-108f, 44f, 0.8f); d.pawLw = on
        d.pawR = P(108f, 44f, -0.8f); d.pawRw = on
    }

    private fun actWink() = Act("wink", now(), 1f, cues = listOf(Cue(0.15f) { emit(Kind.TWINKLE, 1, 0.3f) })) { u, _, d ->
        val on = bump(u, 0f, 1f)
        d.lean += 0.12f * on
        d.y -= 0.05f * arc(u, 0f, 0.3f)
        d.pawR = P(96f, 10f, -0.4f); d.pawRw = on * 0.8f
    }

    private fun actYawn(dur: Float) = Act(
        "yawn", now(), dur, Face(Eye.SQUINT, Lips.GAPE),
        cues = listOf(Cue(0.75f) { emit(Kind.Z, 2, 0.4f) }),
        done = { emote(Face(Eye.SLEEPY, Lips.FLAT), 1.2f) },
    ) { u, _, d ->
        val st = bump(u, 0.05f, 0.75f)
        d.sy += 0.14f * st
        d.sx -= 0.07f * st
        d.nod += 0.25f * st
        d.pawL = P(-64f, -118f, -0.3f); d.pawLw = st
        d.pawR = P(64f, -118f, 0.3f); d.pawRw = st
        d.droop += 20f * seg(u, 0.6f, 1f)
    }

    private fun actHappy() = Act("happy", now(), 1f, cues = listOf(Cue(0.3f) { emit(Kind.TWINKLE, 3, -0.2f) })) { u, _, d ->
        d.y -= 0.16f * arc(u, 0f, 0.4f) + 0.12f * arc(u, 0.45f, 0.8f)
        d.sy += -0.1f * bump(u, 0.36f, 0.48f) - 0.1f * bump(u, 0.78f, 0.92f)
        d.sx += 0.08f * bump(u, 0.36f, 0.48f) + 0.08f * bump(u, 0.78f, 0.92f)
        d.lean += sin(u * PI_F * 4f) * 0.08f
        val up = bump(u, 0f, 1f)
        d.pawL = P(-100f, -40f); d.pawLw = up
        d.pawR = P(100f, -40f); d.pawRw = up
    }

    private fun actHuff() = Act("huff", now(), 1.1f, cues = listOf(Cue(0.2f) { emit(Kind.STEAM, 2, -0.6f) })) { u, _, d ->
        d.sx += 0.07f * bump(u, 0f, 0.35f)
        d.sy -= 0.04f * bump(u, 0f, 0.35f)
        d.y += 0.04f * (bump(u, 0.4f, 0.55f) + bump(u, 0.65f, 0.8f))
        d.lean += 0.05f * sin(u * PI_F * 6f) * seg(u, 0.35f, 0.5f)
        d.pawL = P(-100f, 50f, 1f); d.pawLw = bump(u, 0f, 1f)
        d.pawR = P(100f, 50f, -1f); d.pawRw = bump(u, 0f, 1f)
    }

    private fun actWiggle() = Act("wiggle", now(), 1.2f, Face(Eye.HAPPY, Lips.GRIN)) { u, _, d ->
        val on = bump(u, 0f, 1f)
        d.lean += sin(u * PI_F * 8f) * 0.1f * on
        d.sx += sin(u * PI_F * 16f) * 0.03f * on
        d.earL += sin(u * PI_F * 8f) * 18f * on
        d.earR += sin(u * PI_F * 8f) * 18f * on
    }

    // What he does when nobody asks anything of him.

    private fun fidget() {
        val r = Random.nextFloat()
        when {
            r < 0.2f -> play(Act("lookAround", now(), 2.4f) { u, _, d ->
                d.turn += -0.7f * smooth(seg(u, 0f, 0.15f)) * (1f - smooth(seg(u, 0.35f, 0.5f))) +
                    0.7f * smooth(seg(u, 0.4f, 0.55f)) * (1f - smooth(seg(u, 0.8f, 0.95f)))
                d.nod += 0.1f * bump(u, 0f, 1f)
            })
            r < 0.36f -> {
                val side = if (Random.nextBoolean()) -1f else 1f
                play(Act("earFlick", now(), 0.7f) { u, _, d ->
                    val k = 26f * (bump(u, 0f, 0.25f) + bump(u, 0.35f, 0.6f))
                    if (side < 0) d.earL -= k else d.earR += k
                })
            }
            r < 0.5f -> play(actHop(0.12f, 0.45f))
            r < 0.64f -> play(Act(
                "hum", now(), 2.6f, Face(Eye.HAPPY, Lips.O),
                cues = listOf(Cue(0.15f) { emit(Kind.NOTE, 1, 0.5f) }, Cue(0.5f) { emit(Kind.NOTE, 1, 0.5f) }, Cue(0.8f) { emit(Kind.NOTE, 1, 0.5f) }),
            ) { u, s, d ->
                val on = seg(u, 0f, 0.1f) * (1f - seg(u, 0.9f, 1f))
                d.lean += sin(s * 4f) * 0.07f * on
                d.y -= abs(sin(s * 4f)) * 0.03f * on
                d.earL += sin(s * 4f) * 10f * on
                d.earR += sin(s * 4f) * 10f * on
            })
            r < 0.76f -> play(Act("stretch", now(), 1.5f, Face(Eye.SQUINT, Lips.SMILE)) { u, _, d ->
                val st = bump(u, 0f, 0.85f)
                d.sy += 0.1f * st
                d.sx -= 0.05f * st
                d.lean += sin(u * PI_F * 2f) * 0.06f * st
                d.pawL = P(-58f, -116f); d.pawLw = st
                d.pawR = P(58f, -116f); d.pawRw = st
            })
            r < 0.88f -> play(actWiggle())
            else -> play(Act(
                "sneeze", now(), 1.7f, Face(Eye.SQUINT, Lips.O),
                cues = listOf(Cue(0.52f) { emit(Kind.TWINKLE, 4, 0.2f); Sounds.play("pop") }),
            ) { u, _, d ->
                // Ah… ah… (leaning back, nose up) — CHOO! (thrown forward) — a shake.
                val build = smooth(seg(u, 0f, 0.45f)) * (1f - seg(u, 0.5f, 0.55f))
                d.lean -= 0.12f * build
                d.nod += 0.3f * build
                d.sy += 0.07f * build
                val choo = bump(u, 0.5f, 0.68f)
                d.lean += 0.16f * choo
                d.sy -= 0.16f * choo
                d.sx += 0.12f * choo
                d.nod -= 0.3f * choo
                d.turn += sin(u * PI_F * 14f) * 0.25f * seg(u, 0.7f, 0.75f) * (1f - seg(u, 0.9f, 1f))
            })
        }
    }

    // ── Moves the app asks for ────────────────────────────────────────────────

    fun blink() {
        if (now() - blinkAt < 0.2) return
        blinkAt = now()
        wake()
    }

    /** A quick squash, as when something lands on him. */
    fun squash() {
        psy.vel -= 2.2f
        psx.vel += 1.8f
        wake()
    }

    /** A tap: he flinches. Three inside 1.7 s and he gets dizzy. */
    fun slap() {
        interruptGreet()
        if (dizzy.value) return
        val t = now()
        pokes.removeAll { t - it >= 1.7 }
        pokes += t
        Sounds.play("slap")
        psy.vel -= 3f
        psx.vel += 2.5f
        val side = if (Random.nextBoolean()) -1f else 1f
        lean.vel += side * 3f
        px.vel += side * 0.6f
        sway.vel += side * 4f
        emit(Kind.TWINKLE, 3, -0.2f)
        if (pokes.size >= 3) {
            pokes.clear()
            goDizzy()
        } else {
            emote(Face(Eye.SQUINT, Lips.O, brow = 0.6f), 0.7f)
            after(60) { Sounds.play("annoyed") }
        }
        wake()
    }

    // ── Mimos: the stress-relief pet ──────────────────────────────────────────

    /** A stroke across him: rosy cheeks, happy eyes, a heart now and then. */
    fun stroke() {
        if (dizzy.value) return
        val n = now()
        emote(Face(Eye.HAPPY, Lips.BIG, blush = 1f), 0.8f)
        if (n - lastHeart > 1.1) { lastHeart = n; emit(Kind.HEART, 1, 0f) }
        if (acts.none { it.name == "stroke" }) play(Act("stroke", now(), 0.6f) { u, _, d ->
            d.lean += sin(u * PI_F * 2f) * 0.05f
            d.droop += 10f * bump(u, 0f, 1f)
        })
        wake()
    }

    /** Back-and-forth rubbing: he laughs and wiggles. */
    fun tickle() {
        if (dizzy.value) return
        emote(Face(Eye.HAPPY, Lips.GRIN, blush = 0.6f), 1.6f)
        play(actWiggle())
        emit(Kind.TWINKLE, 3, 0f)
        Sounds.play("wink")
        wake()
    }

    /** A hug while held: heart eyes and his paws round you; let go and he smiles. */
    fun hug(on: Boolean) {
        if (dizzy.value) return
        if (on) {
            eyeOverride = Eye.HEART
            eyeOverrideUntil = INF
            emoteFace = Face(Eye.HEART, Lips.CAT, blush = 1f)
            hugS.target = 1f
            emit(Kind.HEART, 3, 0f)
            Sounds.play("love")
        } else {
            emote(Face(Eye.HAPPY, Lips.BIG, blush = 0.6f), 1.2f)
            hugS.target = 0f
            psy.vel += 1.5f
        }
        wake()
    }

    /** A long sigh: he fills up with his eyes closed, then lets it all out. */
    fun sigh() {
        emote(Face(Eye.CLOSED, Lips.O), 2f)
        play(Act("sigh", now(), 2.4f) { u, _, d ->
            val inhale = smooth(seg(u, 0f, 0.34f)) * (1f - smooth(seg(u, 0.44f, 0.8f)))
            val exhale = bump(u, 0.5f, 1f)
            d.sy += 0.14f * inhale - 0.07f * exhale
            d.sx += 0.08f * inhale + 0.04f * exhale
            d.y -= 0.05f * inhale - 0.03f * exhale
            d.nod += 0.2f * inhale - 0.15f * exhale
            d.droop += 18f * exhale
        })
        // He breathes in and out on a little flute.
        Sounds.play("flute_sigh")
        wake()
    }

    /** Double tap: a jump with a flip. */
    fun flip() {
        if (dizzy.value) return
        emote(Face(Eye.STAR, Lips.GRIN), 1f)
        play(Act("flip", now(), 0.75f, cues = listOf(Cue(0.4f) { emit(Kind.STAR, 3, 0f) })) { u, _, d ->
            d.y -= 0.55f * arc(u, 0.05f, 0.85f)
            d.roll += TWO_PI * smooth(seg(u, 0.1f, 0.75f))
            d.sy += -0.12f * bump(u, 0f, 0.12f) + 0.12f * bump(u, 0.08f, 0.3f) - 0.12f * bump(u, 0.82f, 1f)
            d.sx += 0.1f * bump(u, 0f, 0.12f) + 0.1f * bump(u, 0.82f, 1f)
        })
        Sounds.play("proud")
        wake()
    }

    /** Pinched between two fingers: `amount` > 1 stretches him, < 1 squashes. */
    fun squeeze(amount: Float) {
        val a = amount.coerceIn(0.6f, 1.6f)
        held = a
        when {
            a < 0.85f -> emote(Face(Eye.SQUINT, Lips.POUT), 0.4f)
            a > 1.2f -> emote(Face(Eye.DOT, Lips.O), 0.4f)
        }
        wake()
    }

    /** Let go: he wobbles back to shape. */
    fun unsqueeze() {
        val a = held ?: return
        held = null
        psy.v = a
        psx.v = 1f / sqrt(a)
        psy.vel = 0f
        psx.vel = 0f
        wake()
    }

    /** Thrown against a wall: a squash and a surprised face. */
    fun bump() {
        squash()
        emote(Face(Eye.DOT, Lips.O, browUp = 0.8f), 0.5f)
        wake()
    }

    /** The phone shaken: straight to dizzy. */
    fun shake() {
        if (!dizzy.value) { goDizzy(); wake() }
    }

    /** Pink, spiral eyes, two spins and wobbling for 3.3 s, then a happy hop. */
    private fun goDizzy() {
        dizzy.value = true
        colT = DIZZY
        tintT = 0.7f
        eyeOverride = null
        eyeOverrideUntil = 0.0
        play(actSpin(1.3f, 2))
        Sounds.play("dizzy")
        after(3300) {
            dizzy.value = false
            applyBase()
            triggerEmote(Emote.HAPPY)
        }
    }

    /** "¡Hola!": a little hop and a wave. */
    fun greet() {
        emote(Face(Eye.HAPPY, Lips.GRIN, blush = 0.4f), 1.9f)
        play(actWave(1.8f))
        after(250) { Sounds.play(greetSound) }
        wake()
    }

    private fun interruptGreet() {
        acts.removeAll { it.name == "wave" }
    }

    fun triggerEmote(e: Emote, duration: Float = 1.8f) {
        val d = max(0.8f, duration)
        when (e) {
            Emote.LOVE -> { emote(Face(Eye.HEART, Lips.CAT, blush = 1f), d); play(actLove(d)) }
            Emote.SURPRISED -> { emote(Face(Eye.WIDE, Lips.O, browUp = 1f), d); play(actSurprise()) }
            Emote.PROUD -> { emote(Face(Eye.STAR, Lips.GRIN, blush = 0.6f), d); play(actProud(d)) }
            Emote.WINK -> { emote(Face(Eye.WINK, Lips.TONGUE), min(d, 1.2f)); play(actWink()) }
            Emote.YAWN -> play(actYawn(max(1.6f, d)))
            Emote.HAPPY -> { emote(Face(Eye.HAPPY, Lips.GRIN, blush = 0.5f), d); play(actHappy()) }
            Emote.ANNOYED -> {
                emote(Face(Eye.SQUINT, Lips.FROWN, brow = 1f), min(d, 1.2f))
                play(actHuff())
                after(60) { Sounds.play("annoyed") }
            }
        }
        wake()
    }

    /** A task done: a jump with his paws up and confetti. */
    fun cheer() {
        interruptGreet()
        if (dizzy.value) return
        emote(Face(Eye.HAPPY, Lips.GRIN, blush = 0.5f), 1.4f)
        play(Act("cheer", now(), 1f, cues = listOf(Cue(0.4f) { emit(Kind.CONFETTI, 12, 0f) })) { u, _, d ->
            d.y -= 0.34f * arc(u, 0.1f, 0.75f)
            d.sy += -0.16f * bump(u, 0f, 0.14f) + 0.12f * bump(u, 0.1f, 0.35f) - 0.14f * bump(u, 0.72f, 0.9f)
            d.sx += 0.14f * bump(u, 0f, 0.14f) + 0.12f * bump(u, 0.72f, 0.9f)
            val up = bump(u, 0.05f, 0.95f)
            d.pawL = P(-104f, -64f); d.pawLw = up
            d.pawR = P(104f, -64f); d.pawRw = up
        })
        wake()
    }

    // ── Particles ─────────────────────────────────────────────────────────────

    private fun emit(kind: Kind, count: Int, side: Float) {
        for (i in 0 until count) {
            val fall = kind == Kind.CONFETTI
            particles += Particle(
                kind,
                x = (Random.nextFloat() - 0.5f) * 0.9f + side * 0.5f,
                y = if (kind == Kind.STEAM) -0.55f else -0.8f - Random.nextFloat() * 0.2f,
                vx = (Random.nextFloat() - 0.5f) * (if (fall) 1.6f else 0.4f) + side * 0.25f + if (kind == Kind.STEAM) (if (i % 2 == 1) 0.5f else -0.5f) else 0f,
                vy = if (fall) -randF(1.2f, 1.9f) else -randF(0.4f, 0.75f),
                g = if (fall) 2.6f else if (kind == Kind.DROP) 1.2f else 0f,
                age = -i * (if (fall) 0.02f else 0.13f),
                life = if (fall) randF(1.1f, 1.6f) else randF(1.1f, 1.7f),
                rot = Random.nextFloat() * TWO_PI,
                spin = (Random.nextFloat() - 0.5f) * (if (fall) 12f else 2f),
                size = if (fall) randF(0.05f, 0.08f) else randF(0.13f, 0.2f),
                color = if (fall) CONFETTI[Random.nextInt(CONFETTI.size)] else Color.Unspecified,
            )
        }
    }

    // ── Clock ─────────────────────────────────────────────────────────────────

    /** Last night's rest, when it applies: plain moods only, no fixed face. */
    private val rest: Rest
        get() = if (face == null && !isBreathing && (mood == Mood.IDLE || mood == Mood.FOCUS)) TokkipStyle.rest else Rest.UNKNOWN

    private fun outfitTarget(): Outfit = if (wear) TokkipStyle.outfit else Outfit.NONE

    /** Anything still moving? When not, the frame loop sleeps until idleWaitMs(). */
    val busy: Boolean
        get() = acts.isNotEmpty() || particles.isNotEmpty() || held != null ||
            isBreathing || breathLevel > 0.001f || dizzy.value || mood == Mood.SLEEPY ||
            dancing || danceLevel > 0.001f ||
            now() - blinkAt < 0.2 ||
            springs.any { it.moving } ||
            abs(tint - tintT) > 0.003f ||
            abs(col.red - colT.red) > 0.003f || abs(col.green - colT.green) > 0.003f || abs(col.blue - colT.blue) > 0.003f ||
            (worn != null && worn != outfitTarget())

    /** How long the loop may sleep: until the next blink, glance, fidget or timer (at most a second). */
    fun idleWaitMs(): Long {
        var next = min(nextBlink, nextWander)
        if (mood == Mood.IDLE && face == null) next = min(next, nextFidget)
        if (rest == Rest.TIRED || rest == Rest.GREAT) next = min(next, nextRestBeat)
        for ((at, _) in timers) next = min(next, at)
        return ((next - now()) * 1000).toLong().coerceIn(16, 1000)
    }

    fun update(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.05f)
        val n = now()

        if (timers.isNotEmpty()) {
            val due = timers.filter { it.first <= n }
            if (due.isNotEmpty()) {
                timers.removeAll(due.toSet())
                for ((_, f) in due) f()
            }
        }

        val sleeping = mood == Mood.SLEEPY

        // Blinks, now and then (twice in a row sometimes).
        if (n > nextBlink) {
            if (!sleeping && !dizzy.value && !isBreathing) {
                blink()
                if (Random.nextFloat() < 0.2f) after(260) { blink() }
            }
            nextBlink = n + rand(2.4, 5.8)
        }

        // A glance somewhere now and then, back to you more often than not.
        if (touchLook == null && n > nextWander) {
            if (Random.nextFloat() < 0.45f) { lookX = 0f; lookY = 0f } else {
                lookX = (Random.nextFloat() - 0.5f) * 1.4f
                lookY = (Random.nextFloat() - 0.5f) * 0.8f
            }
            nextWander = n + rand(2.5, 5.5)
        }

        // On his own: fidgets when idle; after a good or a short night, a hop or a yawn.
        val free = acts.isEmpty() && !dancing && !isBreathing && !dizzy.value && held == null
        if (n > nextFidget) {
            if (free && mood == Mood.IDLE && face == null && rest != Rest.TIRED) fidget()
            nextFidget = n + rand(6.0, 12.0)
        }
        if (n > nextRestBeat && free) {
            when (rest) {
                Rest.GREAT -> {
                    emote(Face(Eye.HAPPY, Lips.BIG, blush = 0.5f), 1.2f)
                    play(actHop(0.16f, 0.5f))
                    if (Random.nextBoolean()) emit(Kind.TWINKLE, 2, 0f)
                    nextRestBeat = n + rand(6.0, 12.0)
                }
                Rest.TIRED -> {
                    play(actYawn(2.2f))
                    nextRestBeat = n + rand(14.0, 26.0)
                }
                else -> nextRestBeat = n + 10.0
            }
        }

        if (n > nextAmbient) {
            nextAmbient = n + 1.4
            if (sleeping) emit(Kind.Z, 1, 0.5f)
            if (dancing && danceLevel > 0.5f) emit(Kind.NOTE, 1, Random.nextFloat() - 0.5f)
        }

        // Emote faces run out.
        if (eyeOverride != null && n > eyeOverrideUntil) {
            eyeOverride = permanentEye
            eyeOverrideUntil = if (permanentEye != null) INF else 0.0
            emoteFace = null
        }

        // Acts: cues, then the end.
        if (acts.isNotEmpty()) {
            for (a in acts.toList()) {
                val el = (n - a.start).toFloat()
                for (c in a.cues) if (!c.fired && el >= c.at * a.dur) { c.fired = true; c.f() }
                if (el >= a.dur) {
                    acts.remove(a)
                    a.done?.invoke()
                }
            }
        }

        // Where he looks.
        val look = touchLook ?: Offset(lookX, lookY)
        var tx = look.x * 0.62f
        var ty = look.y * 0.5f
        if (sleeping || isBreathing) { tx = 0f; ty = -0.14f * if (sleeping) 1f else 0.4f }
        if (mood == Mood.FOCUS && touchLook == null) ty = ty * 0.4f - 0.15f
        turn.target = tx
        nod.target = ty

        val fc = faceNow()
        droop.target = baseDroop(fc.eye ?: Eye.OPEN) + (if (isBreathing) 10f - breathFill * 18f else 0f)
        es.target = tgEs
        blushS.target = max(fc.blush, if (rest == Rest.GREAT) 0.4f else 0f)
        px.target = 0f
        py.target = 0f
        lean.target = 0f
        val h = held
        if (h != null) {
            psy.snap(h)
            psx.snap(1f / sqrt(h))
        } else {
            psx.target = 1f - 0.08f * hugS.v
            psy.target = 1f - 0.1f * hugS.v
        }

        // What he wears changes once the old one has lifted off; a landing squashes him.
        val target = outfitTarget()
        if (worn == null) {
            worn = target
            outfitK.snap(1f)
        } else if (worn != target) {
            outfitK.target = 0f
            if (outfitK.v < 0.05f || worn == Outfit.NONE) {
                worn = target
                outfitK.snap(0f)
                outfitK.target = 1f
                landed = false
            }
        }
        if (!landed && outfitK.v > 0.92f) {
            landed = true
            squash()
        }

        // Soft parts lag behind his moves.
        if (dt > 0f) {
            val leanVel = (lean.v - lastLean) / dt
            val xVel = (px.v - lastX) / dt
            sway.target = (-leanVel * 0.25f - xVel * 0.6f - lean.v * 1.5f).coerceIn(-1f, 1f)
        }
        lastLean = lean.v
        lastX = px.v
        for (s in springs) s.step(dt)

        val kCol = (1 - 0.002.pow(dt.toDouble())).toFloat()
        col = lerp(col, colT, kCol)
        tint += (tintT - tint) * kCol

        if (particles.isNotEmpty()) {
            for (p in particles) {
                p.age += dt
                if (p.age < 0f) continue
                p.vy += p.g * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.rot += p.spin * dt
            }
            particles.removeAll { it.age >= it.life }
        }

        danceLevel = if (dancing) min(1f, danceLevel + dt / 0.25f) else max(0f, danceLevel - dt / 0.4f)
        val s = session
        breathFill = if (s != null) breathFrame(s, System.currentTimeMillis()).fill else 0f
        breathLevel = if (isBreathing) min(1f, breathLevel + dt / 0.6f) else max(0f, breathLevel - dt / 0.8f)
    }

    /** Skips every animation: the still pose, for widgets. */
    fun settle() {
        acts.clear(); particles.clear(); timers.clear()
        col = colT; tint = tintT
        breathLevel = if (isBreathing) 1f else 0f
        for (s in listOf(px, py, lean, turn, nod, sway, hugS)) s.snap(0f)
        psx.snap(1f); psy.snap(1f); es.snap(tgEs)
        droop.snap(baseDroop(faceNow().eye ?: Eye.OPEN))
        blushS.snap(if (rest == Rest.GREAT) 0.4f else 0f)
        worn = outfitTarget()
        outfitK.snap(1f)
        landed = true
        blinkAt = -10.0
        held = null
    }

    /** A still pose (widget frames): squash 1 = rest; lift in body radii, up. */
    fun pose(squash: Float, lift: Float) {
        psy.snap(squash)
        psx.snap(1f + (1f - squash) * 0.8f)
        py.snap(-lift)
    }

    // ── His face ──────────────────────────────────────────────────────────────

    private fun lipsFor(eye: Eye): Lips = when (eye) {
        Eye.OPEN, Eye.DOT -> if (rest == Rest.GREAT) Lips.BIG else Lips.SMILE
        Eye.HAPPY -> if (danceLevel > 0.15f) Lips.GRIN else Lips.BIG
        Eye.WIDE -> Lips.O
        Eye.CLOSED -> if (isBreathing) Lips.FLAT else Lips.SLEEP_O
        Eye.SPIRAL -> Lips.WAVY
        Eye.HEART -> Lips.CAT
        Eye.STAR, Eye.SPARKLE -> Lips.GRIN
        Eye.SLEEPY, Eye.FLAT -> Lips.FLAT
        Eye.LINE, Eye.SQUINT, Eye.TEARY -> Lips.FROWN
        Eye.WINK -> Lips.TONGUE
    }

    private fun baseDroop(eye: Eye): Float = when {
        dizzy.value -> 30f
        mood == Mood.SLEEPY -> 46f
        else -> when (eye) {
            Eye.CLOSED -> if (isBreathing) 10f else 46f
            Eye.SLEEPY -> 34f; Eye.SPIRAL -> 30f; Eye.LINE, Eye.SQUINT -> 16f; Eye.TEARY -> 26f
            Eye.WIDE, Eye.DOT -> -14f; Eye.HAPPY -> -8f; Eye.STAR, Eye.SPARKLE -> -12f; Eye.HEART -> -4f
            else -> 0f
        }
    }

    /** His face this frame, by priority: breathing, dizzy, an act, an emote, a fixed face, dancing, his mood. */
    private fun faceNow(): Face {
        if (isBreathing || breathLevel > 0.5f) return Face(Eye.CLOSED, if (breathFill > 0.5f) Lips.O else Lips.FLAT, blush = 0.2f)
        if (dizzy.value) return Face(Eye.SPIRAL, Lips.WAVY)
        for (i in acts.indices.reversed()) {
            val f = acts[i].face
            if (f?.eye != null) return f
        }
        val ov = eyeOverride
        if (ov != null) {
            val f = emoteFace
            return if (f != null && f.eye == ov) f else Face(ov, lipsFor(ov))
        }
        if (danceLevel > 0.2f && mood == Mood.IDLE) return Face(Eye.HAPPY, Lips.GRIN, blush = 0.3f)
        if (rest == Rest.TIRED) return Face(Eye.SLEEPY, Lips.FLAT)
        return when (mood) {
            Mood.FOCUS -> Face(Eye.OPEN, Lips.SMILE, brow = 0.3f)
            Mood.HAPPY -> Face(Eye.HAPPY, Lips.BIG, blush = 0.3f)
            Mood.SLEEPY -> Face(Eye.CLOSED, Lips.SLEEP_O)
            else -> Face(Eye.OPEN, lipsFor(Eye.OPEN), blush = if (rest == Rest.GREAT) 0.4f else 0f)
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    private val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(209, 219, 235)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    /** The body's colour: his own, or the one the app picked (white is his own). */
    private fun bodyColor(): Color = lerp(Color(0xFFF2F3F5), col, (tint / 0.72f).coerceIn(0f, 1f))

    private fun restPaw(side: Float): Paw =
        if (TokkipStyle.species == Species.ROBOT) P(side * 118f, 14f) else P(side * 80f, 96f, side * 0.2f)

    /** Draws Tokkip to fill the scope's width (keep it about 1.2 : 1). */
    fun draw(ds: DrawScope) = with(ds) {
        val w = size.width
        val h = size.height
        val r = min(w * 0.3f, h * 0.36f)
        val u = r * 0.0082f // studio units → pixels
        val n = now()
        val t = (n - t0).toFloat()

        // What the mood and the acts add this frame.
        val d = Delta()
        if (dizzy.value) {
            d.x += sin(t * 5f) * 0.05f
            d.lean += sin(t * 5f + 1.2f) * 0.12f
            d.turn += sin(t * 9f) * 0.25f
        } else if (mood == Mood.SLEEPY) {
            val b = sin(t * 1.6f)
            d.sy += b * 0.035f
            d.sx -= b * 0.02f
            d.lean += 0.05f
        }
        for (a in acts) {
            val el = max(0f, (n - a.start).toFloat())
            a.play((el / a.dur).coerceIn(0f, 1f), el, d)
        }

        val cx = w / 2f + (px.v + d.x) * r
        val cy = h / 2f + (py.v + d.y) * r + r * 0.16f
        val feet = cy + (120f - 15f) * u
        val dance = if (danceLevel > 0.001f) danceMove((n - danceT0).toFloat()) else null
        val f = (breathFill.coerceIn(0f, 1f) * breathLevel.coerceIn(0f, 1f))
        val face = faceNow()
        val eye = face.eye ?: Eye.OPEN
        val open = run {
            val k = ((n - blinkAt) / 0.16).toFloat()
            if (k > 0f && k < 1f) 1f - 0.95f * sin(PI_F * k) else 1f
        }
        val happy = eye == Eye.HAPPY || eye == Eye.HEART || eye == Eye.STAR || danceLevel > 0.15f || blushS.v > 0.4f
        val oyNow = (py.v + d.y) * 100f
        val bounce = oyNow - prevOy
        prevOy = oyNow
        val ears = (dance?.ears ?: 0f) * danceLevel
        val pose = TokkipPose(
            TokkipStyle.species, TokkipStyle.skin, bodyColor(), TokkipStyle.accent, t,
            earL = d.earL + ears, earR = d.earR - ears,
            droop = droop.v + d.droop, happy = happy, wave = -1f, bounce = bounce,
            ink = TokkipStyle.ink, cheek = TokkipStyle.cheek,
        )

        // Paws: resting, a hug, the dance, and any act on top; smoothed so they never jump.
        fun target(side: Float): Paw {
            var p = restPaw(side)
            if (hugS.v > 0.001f) p = mixPaw(p, P(side * 42f, 44f, side * -0.6f), hugS.v)
            if (dance != null) p = mixPaw(p, P(side * 106f, -46f, side * 0.3f), (if (side < 0) dance.pawL else dance.pawR) * danceLevel)
            val act = if (side < 0) d.pawL else d.pawR
            val wgt = if (side < 0) d.pawLw else d.pawRw
            if (act != null && wgt > 0f) p = mixPaw(p, act, wgt.coerceIn(0f, 1f))
            return p
        }
        pawL = mixPaw(pawL, target(-1f), 0.35f)
        pawR = mixPaw(pawR, target(1f), 0.35f)

        withTransform({
            if (dance != null) {
                translate(cx + dance.dx * r * danceLevel, feet + dance.dy * r * danceLevel)
                rotate(deg(dance.rotate * danceLevel), Offset.Zero)
                scale(1f + (dance.sx - 1f) * danceLevel, 1f + (dance.sy - 1f) * danceLevel, Offset.Zero)
                translate(-cx, -feet)
            }
            if (f > 0.001f) scale(1f + 0.12f * f, 1f + 0.18f * f, Offset(cx, feet))
        }) {
            withTransform({
                if (abs(d.roll) > 0.001f) rotate(deg(d.roll), Offset(cx, cy))
                translate(cx, feet)
                rotate(deg(lean.v + d.lean), Offset.Zero)
                scale(psx.v + d.sx, psy.v + d.sy, Offset.Zero)
                translate(0f, cy - feet)
                scale(u, u, Offset.Zero)
                translate(0f, -15f)
            }) {
                drawFigure(pose, face, eye, open, es.v + d.eyeScale, (turn.v + d.turn).coerceIn(-1f, 1f), (nod.v + d.nod).coerceIn(-1f, 1f), t)
            }
        }
        if (dizzy.value) drawOrbit(r, cx, cy, t)
        drawParticles(r, cx, cy - 20f * u)
    }

    private fun DrawScope.drawFigure(pose: TokkipPose, face: Face, eye: Eye, open: Float, eyeScale: Float, turnNow: Float, nodNow: Float, t: Float) {
        val species = pose.species
        val worn = this@TokkipEngine.worn ?: Outfit.NONE
        val fx = sin(turnNow) * 30f
        val fy = -sin(nodNow) * 20f
        val wearNow = Wear(species, t, outfitK.v, sway.v.coerceIn(-1f, 1f), fx, fy)
        with(Ropa) { outfitBack(worn, wearNow) }
        with(TokkipArt) {
            back(pose)
            body(pose)
            clipPath(bodyPath(species)) {
                withTransform({ translate(fx, 20f + fy) }) {
                    faceUnder(pose)
                    blush(pose, max(0.35f, 0.3f + blushS.v * 0.5f).coerceAtMost(0.8f))
                    val ink = ink(pose)
                    for (sd in intArrayOf(-1, 1)) {
                        eyeGlow(pose, sd * 46f, -6f)
                        withTransform({ translate(sd * 46f, -6f) }) {
                            withTransform({ scale(eyeScale, eyeScale, Offset.Zero) }) {
                                drawEye(eye, sd.toFloat(), ink, open, t)
                                if (eye == Eye.OPEN && open >= 0.35f) glint(pose, 0f, 0f)
                            }
                        }
                    }
                    nose(pose)
                    drawLips(pose, face.lips ?: lipsFor(eye), ink, t)
                }
            }
        }
        // Reading glasses for a focus block (unless he already wears glasses).
        if (mood == Mood.FOCUS && !isBreathing && !dizzy.value && worn != Outfit.SUNGLASSES && worn != Outfit.GLASSES) {
            with(Ropa) { outfitFront(Outfit.GLASSES, Wear(species, t, 1f, 0f, fx, fy)) }
        }
        drawPaw(pose, pawL)
        drawPaw(pose, pawR)
        with(Ropa) { outfitFront(worn, wearNow) }
    }

    private fun DrawScope.drawPaw(pose: TokkipPose, p: Paw) = withTransform({
        translate(p.x, p.y)
        rotate(deg(p.a), Offset.Zero)
    }) {
        if (pose.species == Species.ROBOT) {
            drawCircle(Color.Black.copy(alpha = 0.08f), 20f, Offset(2f, 3f))
            drawCircle(TokkipArt.shade(pose.body, -0.18f), 20f, Offset.Zero)
            drawCircle(TokkipArt.shade(pose.body, -0.06f), 12f, Offset.Zero)
        } else {
            drawOval(pose.body, Offset(-20f, -18f), Size(40f, 36f))
            drawOval(TokkipArt.shade(pose.body, -0.2f), Offset(-20f, -18f), Size(40f, 36f), style = Stroke(3.5f))
            val toes = Path().apply { for (tx in floatArrayOf(-6f, 0f, 6f)) { moveTo(tx, -16f); lineTo(tx * 0.9f, -9f) } }
            drawPath(toes, TokkipArt.shade(pose.body, -0.2f), style = Stroke(2.6f, cap = StrokeCap.Round))
        }
    }

    // Tokkip's first face, the one that is his: round ink eyes with no shine that
    // blink to a line; happy and closed eyes are simple arcs. No brows. The PC
    // draws the same face (windows/src/tokkip/figure.ts).

    private val eyeLine = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    private fun DrawScope.eyeLineShut(ink: Color) = drawLine(ink, Offset(-12f, 0f), Offset(12f, 0f), 6f, StrokeCap.Round)

    /** A round eye of radius `r`; less than a third open, it is a line. */
    private fun DrawScope.roundEye(open: Float, ink: Color, r: Float = 12f) {
        if (open < 0.35f) return eyeLineShut(ink)
        val h = r * min(1f, open)
        drawOval(ink, Offset(-r, -h), Size(r * 2f, h * 2f))
    }

    /** A happy eye: an arc bent upwards (^). */
    private fun DrawScope.happyArc(ink: Color) =
        drawArc(ink, 207f, 126f, false, Offset(-13f, -5f), Size(26f, 26f), style = eyeLine)

    /** One eye, centred on (0, 0); `side` −1 is the screen's left. */
    private fun DrawScope.drawEye(eye: Eye, side: Float, ink: Color, open: Float, t: Float) {
        when (eye) {
            Eye.OPEN -> roundEye(open, ink)
            Eye.WIDE, Eye.SPARKLE -> if (open < 0.35f) eyeLineShut(ink) else {
                drawCircle(ink, 15f, Offset.Zero)
                drawCircle(Color.White, 5f, Offset(-4f, -5f))
            }
            Eye.DOT -> roundEye(open, ink, 10f)
            Eye.HAPPY -> happyArc(ink)
            Eye.CLOSED -> drawArc(ink, 27f, 126f, false, Offset(-12f, -14f), Size(24f, 24f), style = eyeLine)
            Eye.LINE, Eye.FLAT, Eye.SQUINT -> eyeLineShut(ink)
            Eye.SPIRAL -> {
                val p = Path()
                var a = 0f
                val spin = t * 8f * side
                while (a < 14f) {
                    val rr = a * 1.05f
                    if (a == 0f) p.moveTo(cos(a + spin) * rr, sin(a + spin) * rr) else p.lineTo(cos(a + spin) * rr, sin(a + spin) * rr)
                    a += 0.25f
                }
                drawPath(p, ink, style = Stroke(3.5f, cap = StrokeCap.Round))
            }
            Eye.HEART -> withTransform({ translate(0f, 2f); scale(1.35f, 1.35f, Offset.Zero) }) {
                drawPath(Path().apply {
                    moveTo(0f, 6f); cubicTo(-14f, -4f, -8f, -16f, 0f, -8f); cubicTo(8f, -16f, 14f, -4f, 0f, 6f); close()
                }, Color(0xFFE2466E))
            }
            Eye.STAR -> withTransform({ rotate(deg(t * 2f * side), Offset.Zero) }) {
                drawPath(star(15f, 15f * 0.45f), Color(0xFFF2B42B))
            }
            Eye.SLEEPY -> {
                drawArc(ink, 0f, 180f, true, Offset(-11f, -9f), Size(22f, 22f))
                drawLine(ink, Offset(-13f, 1f), Offset(13f, 1f), 4f, StrokeCap.Round)
            }
            Eye.WINK -> if (side > 0) happyArc(ink) else roundEye(1f, ink)
            Eye.TEARY -> {
                roundEye(open, ink)
                // A tear runs down one side now and then.
                val k = (t * 0.7f + if (side > 0) 0.5f else 0f) % 1f
                if (k < 0.6f) {
                    val y = 14f + k * 40f
                    drawPath(Path().apply {
                        moveTo(side * 8f, y - 6f); quadraticTo(side * 12f, y + 2f, side * 8f, y + 4f); quadraticTo(side * 4f, y + 2f, side * 8f, y - 6f)
                    }, Color(0xD978BEFF), alpha = 1f - k / 0.6f)
                }
            }
        }
    }

    private fun DrawScope.drawLips(pose: TokkipPose, lips: Lips, ink: Color, t: Float) {
        val y = when (pose.species) { Species.PERRO -> 34f; Species.MAPACHE -> 38f; else -> 30f }
        val pink = Color(0xFFE98A97)
        val mouthInk = if (pose.species == Species.MAPACHE) Color(0xFF3A2E2C) else ink
        when (lips) {
            Lips.GAPE -> {
                drawOval(mouthInk, Offset(-14f, y - 6f), Size(28f, 30f))
                drawOval(pink, Offset(-9f, y + 13f), Size(18f, 10f))
            }
            Lips.CHEW -> if (sin(t * 22f) > 0f) drawOval(mouthInk, Offset(-7f, y - 5f), Size(14f, 12f))
                else drawPath(Path().apply { moveTo(-10f, y); quadraticTo(0f, y + 5f, 10f, y) }, mouthInk, style = Stroke(5.5f, cap = StrokeCap.Round))
            Lips.POUT -> drawCircle(mouthInk, 5.5f, Offset(5f, y + 2f))
            else -> with(TokkipArt) {
                mouth(pose, when (lips) {
                    Lips.SMILE -> Mouth.SMILE; Lips.BIG, Lips.GRIN -> Mouth.BIG; Lips.OPEN -> Mouth.OPEN; Lips.O -> Mouth.O
                    Lips.SLEEP_O -> Mouth.SLEEP_O; Lips.FLAT -> Mouth.FLAT; Lips.FROWN -> Mouth.FROWN; Lips.WAVY -> Mouth.WAVY
                    Lips.CAT -> Mouth.CAT; else -> Mouth.TONGUE
                })
            }
        }
    }

    /** Little stars going round his head while he's dizzy. */
    private fun DrawScope.drawOrbit(r: Float, cx: Float, cy: Float, t: Float) {
        for (i in 0 until 3) {
            val a = t * 4f + i * TWO_PI / 3f
            withTransform({
                translate(cx + cos(a) * r * 0.6f, cy - r * 1.05f + sin(a) * r * 0.14f)
                rotate(deg(t * 3f), Offset.Zero)
            }) { drawPath(star(r * 0.11f, r * 0.05f), Color(0xFFF5B83D), alpha = if (sin(a) > -0.2f) 1f else 0.55f) }
        }
    }

    private fun DrawScope.drawParticles(r: Float, cx: Float, cy: Float) {
        for (p in particles) {
            if (p.age < 0f) continue
            val life = p.age / p.life
            val alpha = (min(1f, p.age / 0.12f) * (1f - life * life)).coerceIn(0f, 1f)
            val sz = p.size * r * (if (p.kind == Kind.CONFETTI) 1f else 0.8f + 0.4f * min(1f, p.age / 0.25f))
            val swing = p.kind == Kind.NOTE || p.kind == Kind.Z || p.kind == Kind.STEAM
            withTransform({
                translate(cx + p.x * r, cy + p.y * r)
                rotate(deg(if (swing) sin(p.age * 4f) * 0.2f else p.rot), Offset.Zero)
            }) {
                when (p.kind) {
                    Kind.HEART -> drawPath(heart(sz), Color(0xFFFF5C8A), alpha = alpha)
                    Kind.STAR -> drawPath(star(sz, sz * 0.45f), Color(0xFFF5B83D), alpha = alpha)
                    Kind.TWINKLE -> drawPath(twinkle(sz * 0.9f), Color(0xFFFFE08A), alpha = alpha)
                    Kind.NOTE -> {
                        val c = Color(0xFFB9A6FF)
                        withTransform({ rotate(-23f, Offset(-sz * 0.2f, sz * 0.4f)) }) {
                            drawOval(c, Offset(-sz * 0.52f, sz * 0.16f), Size(sz * 0.64f, sz * 0.48f), alpha = alpha)
                        }
                        drawPath(Path().apply {
                            moveTo(sz * 0.1f, sz * 0.38f); lineTo(sz * 0.1f, -sz * 0.6f); quadraticTo(sz * 0.5f, -sz * 0.4f, sz * 0.45f, -sz * 0.05f)
                        }, c, alpha = alpha, style = Stroke(sz * 0.16f, cap = StrokeCap.Round))
                    }
                    Kind.Z -> {
                        zPaint.textSize = sz * 1.5f
                        zPaint.alpha = (alpha * 255).toInt()
                        drawContext.canvas.nativeCanvas.drawText("z", 0f, sz * 0.5f, zPaint)
                    }
                    Kind.DROP -> drawPath(Path().apply {
                        moveTo(0f, -sz * 0.6f); quadraticTo(sz * 0.5f, sz * 0.1f, 0f, sz * 0.45f); quadraticTo(-sz * 0.5f, sz * 0.1f, 0f, -sz * 0.6f)
                    }, Color(0xFF7EC8FF), alpha = alpha)
                    Kind.CONFETTI -> drawRect(p.color, Offset(-sz, -sz * 0.45f), Size(sz * 2f, sz * 0.9f), alpha = alpha)
                    Kind.STEAM -> {
                        for ((ox, oy, rr) in listOf(Triple(-0.3f, 0f, 0.38f), Triple(0.15f, -0.18f, 0.42f), Triple(0.4f, 0.12f, 0.3f))) {
                            drawCircle(Color(0x80788296), sz * rr + sz * 0.04f, Offset(sz * ox, sz * oy), alpha = alpha)
                        }
                        for ((ox, oy, rr) in listOf(Triple(-0.3f, 0f, 0.38f), Triple(0.15f, -0.18f, 0.42f), Triple(0.4f, 0.12f, 0.3f))) {
                            drawCircle(Color.White.copy(alpha = 0.9f), sz * rr, Offset(sz * ox, sz * oy), alpha = alpha)
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** An ARGB int, for widgets and notifications. */
        fun argb(c: Color) = c.toArgb()
    }
}
