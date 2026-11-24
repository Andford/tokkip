// Tokkip Fit's guided routines (En casa), shared by the phone and the watch:
// each one a list of steps (a move for so many seconds, or a rest), where you
// are in it at any moment, and roughly what it burned. Pure, so it's tested
// without a phone.

package mx.loperstack.tokkip.fit

import mx.loperstack.tokkip.character.Exercise
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class Step(val move: Exercise, val seconds: Int, val tip: String = TIPS[move] ?: "") {
    val rest get() = move == Exercise.REST
}

data class Routine(
    /** Stored with each workout: keep them. */
    val id: String,
    val name: String,
    val about: String,
    /** 1 easy, 2 medium, 3 hard. */
    val level: Int,
    /** "hiit", "strength" or "stretch": also what Health Connect is told. */
    val kind: String,
    val steps: List<Step>,
) {
    val seconds get() = steps.sumOf { it.seconds }
    val minutes get() = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    val moves get() = steps.count { !it.rest }
}

/** Where you are: the step, its seconds left, and how far into the whole routine (0…1). */
data class Position(val index: Int, val left: Int, val stepFraction: Float, val fraction: Float, val done: Boolean)

fun Routine.at(elapsedMs: Long): Position {
    var t = (elapsedMs / 1000.0).coerceAtLeast(0.0)
    val total = seconds.toDouble().coerceAtLeast(1.0)
    for ((i, s) in steps.withIndex()) {
        if (t < s.seconds) {
            val left = kotlin.math.ceil(s.seconds - t).toInt()
            return Position(i, left, (t / s.seconds).toFloat(), ((elapsedMs / 1000.0) / total).toFloat().coerceIn(0f, 1f), false)
        }
        t -= s.seconds
    }
    return Position(steps.lastIndex.coerceAtLeast(0), 0, 1f, 1f, true)
}

/** What each move burns (METs: how many times your resting energy). */
private val MET = mapOf(
    Exercise.SQUAT to 5.0, Exercise.JUMP to 8.0, Exercise.PLANK to 4.0, Exercise.RUN to 8.0,
    Exercise.PUNCH to 7.0, Exercise.STRETCH to 2.5, Exercise.REST to 1.5,
)

/** Active kilocalories for `seconds` of each step done, for someone of `kg`. */
fun Routine.kcal(doneMs: Long, kg: Double = 70.0): Double {
    var left = doneMs / 1000.0
    var kcal = 0.0
    for (s in steps) {
        if (left <= 0) break
        val sec = minOf(left, s.seconds.toDouble())
        // Active only: what's above resting (1 MET).
        kcal += ((MET[s.move] ?: 3.0) - 1.0) * 3.5 * kg / 200.0 * (sec / 60.0)
        left -= sec
    }
    return kcal
}

/** One workout done (phone or watch). */
data class Done(
    val at: Long,
    val routine: String,
    val name: String,
    val seconds: Int,
    val kcal: Double,
    /** Average heart rate from the watch, 0 if none. */
    val bpm: Int = 0,
    /** hiit, strength, stretch (En casa); gym; run, walk, bike (Cardio); breathe (Respira). */
    val kind: String = "hiit",
    /** "phone" or "watch". */
    val from: String = "phone",
    /** Cardio: metres covered. */
    val meters: Double = 0.0,
    /** Gym: sets done, and the weight moved (kg × reps). */
    val sets: Int = 0,
    val volume: Double = 0.0,
) {
    val minutes get() = (seconds / 60.0)

    fun toJson(): JSONObject = JSONObject().put("at", at).put("routine", routine).put("name", name).put("seconds", seconds)
        .put("kcal", kcal).put("bpm", bpm).put("kind", kind).put("from", from)
        .put("meters", meters).put("sets", sets).put("volume", volume)

    companion object {
        fun of(o: JSONObject) = Done(
            o.optLong("at"), o.optString("routine"), o.optString("name"), o.optInt("seconds"),
            o.optDouble("kcal", 0.0), o.optInt("bpm", 0), o.optString("kind", "hiit"), o.optString("from", "phone"),
            o.optDouble("meters", 0.0), o.optInt("sets", 0), o.optDouble("volume", 0.0),
        )

        fun list(raw: String?): List<Done> = runCatching {
            val a = JSONArray(raw ?: return emptyList())
            (0 until a.length()).map { of(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())

        fun json(list: List<Done>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }
}

/** The Data Layer path the watch sends a finished workout on (a contract between the two). */
const val FIT_DONE_PATH = "/tokkipfit/done"

private val TIPS = mapOf(
    Exercise.SQUAT to "Pies al ancho de hombros, baja como si te sentaras",
    Exercise.JUMP to "Abre y cierra brazos y piernas, aterriza suave",
    Exercise.PLANK to "Abdomen firme, espalda recta, respira",
    Exercise.RUN to "Rodillas arriba, brazos al ritmo",
    Exercise.PUNCH to "Golpes rápidos, guardia arriba",
    Exercise.STRETCH to "Muévete lento, sin rebotar",
    Exercise.REST to "Respira hondo. Ya viene lo siguiente",
)

private fun s(m: Exercise, sec: Int) = Step(m, sec)
private val R = Exercise.REST

/** The routines, easy to hard. */
val ROUTINES = listOf(
    Routine(
        "despierta", "Despierta", "Siete minutos suaves para arrancar el día con energía.", 1, "hiit",
        listOf(
            s(Exercise.STRETCH, 40), s(Exercise.SQUAT, 30), s(R, 15), s(Exercise.RUN, 30), s(R, 15),
            s(Exercise.PUNCH, 30), s(R, 15), s(Exercise.JUMP, 30), s(R, 15), s(Exercise.SQUAT, 30),
            s(R, 15), s(Exercise.PUNCH, 30), s(R, 15), s(Exercise.STRETCH, 60),
        ),
    ),
    Routine(
        "fuerza", "Fuerza sin pesas", "Sentadillas y plancha, tres rondas. Piernas y abdomen.", 2, "strength",
        List(3) { listOf(s(Exercise.SQUAT, 45), s(R, 15), s(Exercise.PLANK, 30), s(R, 20)) }.flatten() + s(Exercise.STRETCH, 45),
    ),
    Routine(
        "hiit", "HIIT exprés", "Diez minutos intensos: 40 segundos a tope y 20 de descanso.", 3, "hiit",
        listOf(s(Exercise.RUN, 30), s(R, 10)) +
            List(3) { listOf(s(Exercise.JUMP, 40), s(R, 20), s(Exercise.SQUAT, 40), s(R, 20), s(Exercise.PUNCH, 40), s(R, 20)) }.flatten() +
            s(Exercise.STRETCH, 40),
    ),
    Routine(
        "box", "Cardio boxeo", "Golpes y rodillas arriba para subir el pulso sin brincar mucho.", 2, "hiit",
        List(4) { listOf(s(Exercise.PUNCH, 45), s(R, 15), s(Exercise.RUN, 30), s(R, 15)) }.flatten() + s(Exercise.STRETCH, 40),
    ),
    Routine(
        "estira", "Estira y relaja", "Cinco minutos para soltar el cuerpo, ideal al final del día.", 1, "stretch",
        listOf(s(Exercise.STRETCH, 60), s(R, 10), s(Exercise.STRETCH, 60), s(R, 10), s(Exercise.STRETCH, 60), s(R, 10), s(Exercise.STRETCH, 60)),
    ),
)

fun routine(id: String): Routine? = ROUTINES.firstOrNull { it.id == id }
