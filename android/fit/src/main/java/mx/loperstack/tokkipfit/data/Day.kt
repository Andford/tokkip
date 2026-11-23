// What a day of moving looks like, and what Tokkip makes of it: the three
// rings (steps, exercise minutes, active calories), the streak, the day's
// challenge and his mood. Pure, so it's tested without a phone.

package mx.loperstack.tokkipfit.data

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One day, as Health Connect (or the sample) tells it. */
data class Day(
    val date: LocalDate,
    val steps: Long = 0,
    val meters: Double = 0.0,
    val kcal: Double = 0.0,
    val exerciseMin: Long = 0,
)

/** Your goals (Ajustes → Metas). */
data class Goals(val steps: Long = 8_000, val minutes: Long = 30, val kcal: Long = 400) {
    fun clean() = Goals(steps.coerceIn(1_000, 50_000), minutes.coerceIn(5, 300), kcal.coerceIn(50, 3_000))
}

/** Last night and the heart, when there's any. */
data class Body(val sleepMin: Long? = null, val restingBpm: Long? = null)

/** Everything Hoy shows. */
data class Today(
    val week: List<Day>,
    val body: Body = Body(),
    val goals: Goals = Goals(),
    /** Sample data (Health Connect not connected yet). */
    val sample: Boolean = false,
) {
    val today: Day get() = week.lastOrNull() ?: Day(LocalDate.now())

    val stepsRing get() = ring(today.steps, goals.steps)
    val minutesRing get() = ring(today.exerciseMin, goals.minutes)
    val kcalRing get() = ring(today.kcal.roundToInt().toLong(), goals.kcal)

    /** How many rings are closed today (0–3). */
    val closed get() = listOf(stepsRing, minutesRing, kcalRing).count { it >= 1f }

    /** The three rings together, each counting up to its goal. */
    val overall get() = (stepsRing.coerceAtMost(1f) + minutesRing.coerceAtMost(1f) + kcalRing.coerceAtMost(1f)) / 3f

    /** Days in a row with the steps goal met: up to yesterday, plus today once it's met. */
    val streak: Int
        get() {
            var n = 0
            val days = week.dropLast(1).asReversed()
            for (d in days) if (d.steps >= goals.steps) n++ else break
            return n + if (today.steps >= goals.steps) 1 else 0
        }

    companion object {
        fun ring(value: Long, goal: Long): Float = if (goal <= 0) 0f else value.toFloat() / goal
    }
}

/** How Tokkip takes your day. */
enum class Vibe { STARTING, MOVING, GREAT, CHAMPION, LAZY }

fun vibeOf(t: Today, hour: Int): Vibe = when {
    t.closed == 3 -> Vibe.CHAMPION
    t.overall >= 0.66f -> Vibe.GREAT
    hour >= 18 && t.overall < 0.25f -> Vibe.LAZY
    t.overall >= 0.25f -> Vibe.MOVING
    else -> Vibe.STARTING
}

/** What he says about it (Spanish, de tú). */
fun lineOf(v: Vibe, t: Today): String = when (v) {
    Vibe.CHAMPION -> "¡Cerraste los tres anillos! Eres una máquina"
    Vibe.GREAT -> "¡Vas volando! Ya casi cierras todo"
    Vibe.MOVING -> "Buen ritmo. Sigamos moviéndonos"
    Vibe.LAZY -> "¿Y si damos una vueltecita antes de dormir?"
    Vibe.STARTING -> if (t.today.steps == 0L) "¡Hoy es un buen día para moverse!" else "Arrancando. Vamos poquito a poquito"
}

/** The day's challenge: what's missing if anything is, otherwise one for fun (the same all day). */
fun challengeOf(t: Today): Challenge {
    val missing = t.goals.steps - t.today.steps
    if (missing in 1..4_000) {
        val minutes = ceil(missing / 100.0).toInt().coerceAtLeast(1)
        return Challenge("walk", "Te faltan ${fmt(missing)} pasos", "Son como $minutes minutos caminando. ¿Vamos?")
    }
    val left = t.goals.minutes - t.today.exerciseMin
    if (t.today.steps >= t.goals.steps && left in 1..20) {
        return Challenge("bolt", "$left minutos más de ejercicio", "Una rutina corta en casa y cierras el anillo.")
    }
    val pick = FUN[Math.floorMod(t.today.date.toEpochDay(), FUN.size.toLong()).toInt()]
    return pick
}

data class Challenge(val icon: String, val title: String, val body: String)

private val FUN = listOf(
    Challenge("stairs", "Escaleras, no elevador", "Cada piso cuenta. Tokkip ya va subiendo."),
    Challenge("squat", "20 sentadillas", "En cualquier momento del día, sin prisa."),
    Challenge("stretch", "5 minutos de estiramiento", "Cuello, hombros y espalda: te lo vas a agradecer."),
    Challenge("walk", "Camina después de comer", "Diez minutos ayudan a la digestión y al ánimo."),
    Challenge("water", "Agua antes de entrenar", "Un vaso grande y a darle."),
    Challenge("breathe", "Respira un minuto", "Inhala 4, sostén 4, exhala 6. Tokkip respira contigo."),
    Challenge("plank", "Plancha de 30 segundos", "Abdomen firme y espalda recta. ¡Tú puedes!"),
)

/** 12345 → "12,345" (the way it's read in Mexico). */
fun fmt(n: Long): String {
    val s = kotlin.math.abs(n).toString()
    val out = StringBuilder()
    for ((i, c) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) out.append(',')
        out.append(c)
    }
    return (if (n < 0) "-" else "") + out
}

/** 450 → "7 h 30 min", 45 → "45 min". */
fun hours(min: Long): String = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"

/** 5230.0 → "5.2 km", 830.0 → "830 m". */
fun distance(meters: Double): String =
    if (meters >= 1000) String.format(java.util.Locale.US, "%.1f km", meters / 1000) else "${meters.roundToInt()} m"

/** A believable week, until Health Connect is connected: so the app is never empty. */
fun sampleWeek(today: LocalDate, goals: Goals = Goals()): Today {
    val steps = listOf(9_120L, 10_480L, 8_250L, 6_430L, 11_900L, 8_730L, 5_412L)
    val mins = listOf(35L, 42L, 30L, 12L, 55L, 31L, 18L)
    val week = steps.indices.map { i ->
        Day(today.minusDays((6 - i).toLong()), steps[i], steps[i] * 0.74, steps[i] * 0.045, mins[i])
    }
    return Today(week, Body(sleepMin = 442, restingBpm = 61), goals, sample = true)
}
