// Pulse and heart-rate variability for a breathing session.
//
// Two honest sources: beat-to-beat intervals (RR) when the watch's heartbeat
// sensor gives them — real HRV — or, failing that, the pulse readings, from
// which the variability is only an estimate (and labelled so). Neither is
// a medical measurement, and the "cardiogram" drawn from them is an
// illustration of your rhythm, not an ECG.

package mx.loperstack.tokkip.wellness

import kotlin.math.roundToInt
import kotlin.math.sqrt

data class HrvStats(
    /** Root mean square of successive differences, ms: the usual short-term HRV. */
    val rmssd: Int,
    /** Standard deviation of the intervals, ms. */
    val sdnn: Int,
    val avgBpm: Int,
    val minBpm: Int,
    val maxBpm: Int,
    /** True when it comes from real beat-to-beat intervals. */
    val beatToBeat: Boolean,
)

data class MinuteRow(val minute: Int, val avg: Int, val min: Int, val max: Int, val rmssd: Int)

fun rmssd(rr: List<Int>): Int {
    if (rr.size < 3) return 0
    val d = rr.zipWithNext { a, b -> (b - a).toDouble() }
    return sqrt(d.map { it * it }.average()).roundToInt()
}

fun sdnn(rr: List<Int>): Int {
    if (rr.size < 3) return 0
    val m = rr.average()
    return sqrt(rr.map { (it - m) * (it - m) }.average()).roundToInt()
}

/** Intervals to work with: the real ones, or the pulse readings turned into intervals. */
private fun intervals(s: BreathSession): Pair<List<Int>, Boolean> =
    if (s.rr.size >= 20) s.rr.filter { it in 300..2000 } to true
    else s.hr.filter { it > 0 }.map { 60_000 / it } to false

fun hrvStats(s: BreathSession): HrvStats? {
    val readings = s.hr.filter { it > 0 }
    val (rr, real) = intervals(s)
    if (readings.size < 3 && rr.size < 3) return null
    val bpm = if (readings.isNotEmpty()) readings else rr.map { 60_000 / it }
    return HrvStats(rmssd(rr), sdnn(rr), bpm.average().roundToInt(), bpm.min(), bpm.max(), real)
}

/** Minute by minute: average, lowest and highest pulse, and the variability. */
fun minuteTable(s: BreathSession): List<MinuteRow> {
    val per = 60 / HR_STEP_S
    return s.hr.chunked(per).mapIndexedNotNull { i, chunk ->
        val v = chunk.filter { it > 0 }
        if (v.isEmpty()) null else MinuteRow(i + 1, v.average().roundToInt(), v.min(), v.max(), rmssd(v.map { 60_000 / it }))
    }
}

/** How a RMSSD reads, roughly, for an adult at rest (it varies a lot with age). */
fun rmssdLabel(v: Int): String = when {
    v <= 0 -> "Sin datos"
    v < 20 -> "Baja"
    v < 50 -> "Moderada"
    else -> "Alta"
}
