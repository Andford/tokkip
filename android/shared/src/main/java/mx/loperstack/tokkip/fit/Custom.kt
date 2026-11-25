// Your own routines (En casa) and plans (Gym), made in Fit: a routine is the
// moves you pick (each for so many seconds), a rest between them and how many
// rounds; a plan is the lifts you pick with their sets, reps and rest. Kept as
// that recipe (so it can be edited again) and turned into a Routine or a
// GymPlan to play. The phone sends yours to the watch on FIT_MINE_PATH.
// Pure, so it's tested without a phone.

package mx.loperstack.tokkip.fit

import mx.loperstack.tokkip.character.Exercise
import org.json.JSONArray
import org.json.JSONObject

/** The Data Layer path the phone sends your own routines on (a contract between the two). */
const val FIT_MINE_PATH = "/tokkipfit/mine"

/** The moves a routine of yours can have (the gym's lift and the rest have their own place). */
val HOME_MOVES = listOf(Exercise.SQUAT, Exercise.JUMP, Exercise.PLANK, Exercise.RUN, Exercise.PUNCH, Exercise.STRETCH)

data class Move(val move: Exercise, val seconds: Int)

data class MyRoutine(
    /** "mia_<when it was made>": stored with each workout, so it stays. */
    val id: String,
    val name: String,
    val moves: List<Move>,
    /** Seconds of rest after each move (0: none). */
    val rest: Int = 15,
    val rounds: Int = 2,
) {
    fun clean() = copy(
        name = name.trim().take(40).ifBlank { "Mi rutina" },
        moves = moves.filter { it.move in HOME_MOVES }.map { it.copy(seconds = it.seconds.coerceIn(10, 300)) }.take(12),
        rest = rest.coerceIn(0, 120),
        rounds = rounds.coerceIn(1, 10),
    )

    /** What it plays: every move of every round, with the rest between (none after the very last). */
    fun routine(): Routine {
        val c = clean()
        val steps = buildList {
            repeat(c.rounds) { r ->
                c.moves.forEachIndexed { i, m ->
                    add(Step(m.move, m.seconds))
                    val last = r == c.rounds - 1 && i == c.moves.lastIndex
                    if (c.rest > 0 && !last) add(Step(Exercise.REST, c.rest))
                }
            }
        }.ifEmpty { listOf(Step(Exercise.STRETCH, 60)) }
        val work = steps.filter { !it.rest }
        val kind = when {
            work.all { it.move == Exercise.STRETCH } -> "stretch"
            work.count { it.move == Exercise.SQUAT || it.move == Exercise.PLANK } * 2 >= work.size -> "strength"
            else -> "hiit"
        }
        // Harder the more of it is work, and the longer it goes.
        val workShare = work.sumOf { it.seconds }.toDouble() / steps.sumOf { it.seconds }.coerceAtLeast(1)
        val minutes = steps.sumOf { it.seconds } / 60.0
        val level = when {
            kind == "stretch" -> 1
            workShare >= 0.7 && minutes >= 8 -> 3
            workShare >= 0.6 || minutes >= 12 -> 2
            else -> 1
        }
        val about = c.moves.map { it.move.label }.distinct().joinToString(", ") +
            if (c.rounds > 1) " · ${c.rounds} rondas" else ""
        return Routine(c.id, c.name, about, level, kind, steps)
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("rest", rest).put("rounds", rounds)
        .put("moves", JSONArray().apply { moves.forEach { put(JSONArray().put(it.move.name).put(it.seconds)) } })

    companion object {
        fun new(now: Long) = MyRoutine("mia_$now", "", listOf(Move(Exercise.SQUAT, 40), Move(Exercise.JUMP, 30), Move(Exercise.PLANK, 30)))

        fun of(o: JSONObject): MyRoutine? = runCatching {
            val a = o.getJSONArray("moves")
            MyRoutine(
                o.getString("id"), o.optString("name"),
                (0 until a.length()).mapNotNull { i ->
                    val m = a.getJSONArray(i)
                    runCatching { Move(Exercise.valueOf(m.getString(0)), m.getInt(1)) }.getOrNull()
                },
                o.optInt("rest", 15), o.optInt("rounds", 2),
            ).clean()
        }.getOrNull()

        fun list(raw: String?): List<MyRoutine> = runCatching {
            val a = JSONArray(raw ?: return emptyList())
            (0 until a.length()).mapNotNull { of(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())

        fun json(list: List<MyRoutine>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }
}

/** A gym plan of yours ("mio_<when>"): the same GymPlan, kept as JSON. */
fun GymPlan.clean() = copy(
    name = name.trim().take(40).ifBlank { "Mi plan" },
    lifts = lifts.filter { lift(it) != null }.distinct().take(12),
    sets = sets.coerceIn(1, 10), reps = reps.coerceIn(1, 50), restSeconds = restSeconds.coerceIn(15, 600),
).let { p -> p.copy(about = p.lifts.mapNotNull { lift(it)?.muscle }.distinct().joinToString(", ")) }

fun GymPlan.toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("sets", sets).put("reps", reps)
    .put("rest", restSeconds).put("lifts", JSONArray().apply { lifts.forEach { put(it) } })

object MyPlans {
    fun new(now: Long) = GymPlan("mio_$now", "", "", emptyList())

    fun of(o: JSONObject): GymPlan? = runCatching {
        val a = o.getJSONArray("lifts")
        GymPlan(
            o.getString("id"), o.optString("name"), "", (0 until a.length()).map { a.getString(it) },
            o.optInt("sets", 3), o.optInt("reps", 10), o.optInt("rest", 90),
        ).clean()
    }.getOrNull()

    fun list(raw: String?): List<GymPlan> = runCatching {
        val a = JSONArray(raw ?: return emptyList())
        (0 until a.length()).mapNotNull { of(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun json(list: List<GymPlan>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
}

/** The last set of each lift (the watch starts from those weights). */
fun lastSets(history: List<GymSet>): List<GymSet> = history.groupBy { it.lift }.map { it.value.last() }

/** What a lift starts at on the watch: your last weight and reps, or the plan's reps. */
fun startOf(lift: String, plan: GymPlan, last: List<GymSet>): GymSet =
    last.lastOrNull { it.lift == lift }?.let { GymSet(lift, it.kg, it.reps) } ?: GymSet(lift, 0.0, plan.reps)
