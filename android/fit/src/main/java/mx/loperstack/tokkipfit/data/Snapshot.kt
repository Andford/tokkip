// Hoy in a few numbers, for the widgets and the watch's tile (they can't always
// ask Health Connect): saved each time Fit reads it, sent to the watch.

package mx.loperstack.tokkipfit.data

import org.json.JSONObject
import kotlin.math.roundToLong

data class Snapshot(
    val steps: Long, val goalSteps: Long, val minutes: Long, val goalMinutes: Long,
    val kcal: Long, val goalKcal: Long, val streak: Int, val at: Long, val sample: Boolean,
) {
    val stepsRing get() = Today.ring(steps, goalSteps)
    val minutesRing get() = Today.ring(minutes, goalMinutes)
    val kcalRing get() = Today.ring(kcal, goalKcal)

    fun json(): String = JSONObject().put("steps", steps).put("goalSteps", goalSteps).put("minutes", minutes).put("goalMinutes", goalMinutes)
        .put("kcal", kcal).put("goalKcal", goalKcal).put("streak", streak).put("at", at).put("sample", sample).toString()

    companion object {
        /** The path the phone sends it to the watch on. */
        const val PATH = "/tokkipfit/today"

        fun of(t: Today) = Snapshot(
            t.today.steps, t.goals.steps, t.today.exerciseMin, t.goals.minutes,
            t.today.kcal.roundToLong(), t.goals.kcal, t.streak, System.currentTimeMillis(), t.sample,
        )

        fun parse(raw: String?): Snapshot? = runCatching {
            val o = JSONObject(raw ?: return null)
            Snapshot(
                o.optLong("steps"), o.optLong("goalSteps", 8_000), o.optLong("minutes"), o.optLong("goalMinutes", 30),
                o.optLong("kcal"), o.optLong("goalKcal", 400), o.optInt("streak"), o.optLong("at"), o.optBoolean("sample", true),
            )
        }.getOrNull()
    }
}
