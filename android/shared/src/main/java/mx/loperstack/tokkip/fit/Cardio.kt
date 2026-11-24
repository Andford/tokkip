// Tokkip Fit's cardio (GPS): running, walking or cycling. Distance from the
// points (haversine), dropping the jumps a weak signal makes; the pace; the
// kilometres as they're done; calories by speed. Pure, so it's tested without
// a phone.

package mx.loperstack.tokkip.fit

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class Sport(val id: String, val label: String, val verb: String) {
    RUN("run", "Correr", "Corriste"),
    WALK("walk", "Caminar", "Caminaste"),
    BIKE("bike", "Bici", "Pedaleaste");

    /** Faster than this (m/s) is a GPS jump, not you. */
    val maxSpeed get() = when (this) { RUN -> 9.0; WALK -> 4.0; BIKE -> 25.0 }

    companion object { fun of(id: String) = entries.firstOrNull { it.id == id } ?: RUN }
}

/** A point of the route: where, when (ms), and how sure the GPS was (m). */
data class Point(val lat: Double, val lon: Double, val at: Long, val accuracy: Float = 5f, val alt: Double? = null)

/** Metres between two points (haversine). */
fun meters(a: Point, b: Point): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

/** Keeps the points worth keeping: accurate enough, and not an impossible jump from the last one kept. */
class Track(val sport: Sport) {
    private val kept = ArrayList<Point>()
    val points: List<Point> get() = kept
    var distance = 0.0
        private set
    /** When each kilometre was done (ms since the start). */
    val splits = ArrayList<Long>()

    fun add(p: Point, startedAt: Long): Boolean {
        if (p.accuracy > 30f) return false
        val last = kept.lastOrNull()
        if (last != null) {
            val d = meters(last, p)
            val dt = (p.at - last.at) / 1000.0
            if (dt <= 0 || d / dt > sport.maxSpeed) return false
            if (d < 2.0) return false
            val before = distance
            distance += d
            if ((distance / 1000).toInt() > (before / 1000).toInt()) splits += p.at - startedAt
        }
        kept += p
        return true
    }
}

/** Seconds per kilometre → "5'12\"". */
fun pace(secondsPerKm: Double): String {
    if (secondsPerKm <= 0 || secondsPerKm.isInfinite() || secondsPerKm.isNaN() || secondsPerKm > 60 * 60) return "--"
    val s = secondsPerKm.roundToInt()
    return "${s / 60}'${(s % 60).toString().padStart(2, '0')}\""
}

/** km/h for bikes. */
fun speed(meters: Double, seconds: Double): String =
    if (seconds <= 0) "--" else String.format(java.util.Locale.US, "%.1f km/h", meters / seconds * 3.6)

/** 2:05:09 or 25:09. */
fun clock(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

/** Active kilocalories: METs by sport and speed, above rest (1 MET), for someone of `kg`. */
fun cardioKcal(sport: Sport, meters: Double, seconds: Double, kg: Double = 70.0): Double {
    if (seconds <= 0) return 0.0
    val kmh = meters / seconds * 3.6
    val met = when (sport) {
        Sport.WALK -> when { kmh < 4 -> 2.8; kmh < 5.5 -> 3.5; else -> 5.0 }
        Sport.RUN -> (kmh * 1.0).coerceIn(6.0, 16.0)
        Sport.BIKE -> when { kmh < 16 -> 4.0; kmh < 20 -> 6.8; kmh < 25 -> 8.0; else -> 10.0 }
    }
    return (met - 1.0) * 3.5 * kg / 200.0 * (seconds / 60.0)
}
