// Tokkip Fit's gym: the exercises (and the move Tokkip does for each), the
// plans to start from, a set (weight × reps), personal records (the heaviest
// lift and the estimated one-rep max, Epley) and the rest between sets. Pure,
// so it's tested without a phone.

package mx.loperstack.tokkip.fit

import mx.loperstack.tokkip.character.Exercise
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** One exercise; its id is stored with every set: keep them. */
data class Lift(val id: String, val name: String, val muscle: String, val move: Exercise, val bodyweight: Boolean = false)

val LIFTS = listOf(
    Lift("sentadilla", "Sentadilla", "Piernas", Exercise.SQUAT),
    Lift("peso_muerto", "Peso muerto", "Espalda y piernas", Exercise.SQUAT),
    Lift("zancadas", "Zancadas", "Piernas", Exercise.SQUAT),
    Lift("prensa", "Prensa de piernas", "Piernas", Exercise.SQUAT),
    Lift("press_banca", "Press de banca", "Pecho", Exercise.LIFT),
    Lift("press_militar", "Press militar", "Hombros", Exercise.LIFT),
    Lift("lagartijas", "Lagartijas", "Pecho", Exercise.PLANK, bodyweight = true),
    Lift("fondos", "Fondos", "Tríceps", Exercise.LIFT, bodyweight = true),
    Lift("dominadas", "Dominadas", "Espalda", Exercise.LIFT, bodyweight = true),
    Lift("remo", "Remo con barra", "Espalda", Exercise.LIFT),
    Lift("jalon", "Jalón al pecho", "Espalda", Exercise.LIFT),
    Lift("curl", "Curl de bíceps", "Bíceps", Exercise.LIFT),
    Lift("triceps", "Extensión de tríceps", "Tríceps", Exercise.LIFT),
    Lift("elevaciones", "Elevaciones laterales", "Hombros", Exercise.LIFT),
    Lift("plancha", "Plancha", "Abdomen", Exercise.PLANK, bodyweight = true),
    Lift("crunch", "Abdominales", "Abdomen", Exercise.PLANK, bodyweight = true),
)

fun lift(id: String): Lift? = LIFTS.firstOrNull { it.id == id }

/** A plan to start from: the exercises, how many sets of how many reps, and the rest. */
data class GymPlan(val id: String, val name: String, val about: String, val lifts: List<String>, val sets: Int = 3, val reps: Int = 10, val restSeconds: Int = 90)

val GYM_PLANS = listOf(
    GymPlan("pierna", "Pierna", "Sentadilla, peso muerto, zancadas y prensa.", listOf("sentadilla", "peso_muerto", "zancadas", "prensa"), 4, 8, 120),
    GymPlan("empuje", "Empuje", "Pecho, hombros y tríceps.", listOf("press_banca", "press_militar", "fondos", "elevaciones", "triceps")),
    GymPlan("jalon", "Jalón", "Espalda y bíceps.", listOf("dominadas", "remo", "jalon", "curl")),
    GymPlan("completo", "Cuerpo completo", "Un poco de todo, para dos o tres días por semana.", listOf("sentadilla", "press_banca", "remo", "press_militar", "plancha"), 3, 10, 75),
)

fun gymPlan(id: String): GymPlan? = GYM_PLANS.firstOrNull { it.id == id }

/** One set: kilos (0 = your own weight) × reps, and when. */
data class GymSet(val lift: String, val kg: Double, val reps: Int, val at: Long = 0) {
    /** Estimated one-rep max (Epley); for bodyweight sets, the reps. */
    val oneRm: Double get() = if (kg <= 0) reps.toDouble() else if (reps <= 1) kg else kg * (1 + reps / 30.0)
    val volume: Double get() = kg * reps

    fun toJson(): JSONObject = JSONObject().put("lift", lift).put("kg", kg).put("reps", reps).put("at", at)

    companion object {
        fun of(o: JSONObject) = GymSet(o.optString("lift"), o.optDouble("kg", 0.0), o.optInt("reps", 0), o.optLong("at", 0))
        fun list(raw: String?): List<GymSet> = runCatching {
            val a = JSONArray(raw ?: return emptyList())
            (0 until a.length()).map { of(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        fun json(list: List<GymSet>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }
}

/** Your best for one exercise: the heaviest set and the best estimated max. */
data class Record(val lift: String, val heaviest: GymSet, val best: GymSet)

fun records(history: List<GymSet>): Map<String, Record> =
    history.filter { it.reps > 0 }.groupBy { it.lift }.mapValues { (id, sets) ->
        Record(id, sets.maxWith(compareBy<GymSet> { it.kg }.thenBy { it.reps }), sets.maxBy { it.oneRm })
    }

/** Does this set beat what was there? (For Tokkip's celebration.) */
fun isRecord(set: GymSet, before: List<GymSet>): Boolean {
    val old = before.filter { it.lift == set.lift && it.reps > 0 }
    if (old.isEmpty() || set.reps <= 0) return false
    return set.oneRm > old.maxOf { it.oneRm } + 1e-6
}

/** The weight to suggest for the next set: the last one used for that exercise. */
fun lastWeight(lift: String, history: List<GymSet>): Double = history.lastOrNull { it.lift == lift }?.kg ?: 0.0

/** 62.5 → "62.5", 60.0 → "60". */
fun kg(v: Double): String = if (v == v.roundToInt().toDouble()) "${v.roundToInt()}" else String.format(java.util.Locale.US, "%.1f", v)

/** A gym session's active calories: about 3.5 METs above rest while it lasts. */
fun gymKcal(seconds: Int, kgBody: Double = 70.0): Double = 3.5 * 3.5 * kgBody / 200.0 * (seconds / 60.0)
