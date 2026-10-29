// Tokkip's alarm clock: what an alarm is, when it rings next, and how it is
// kept (JSON in Store, so backups carry it). The ringing itself lives in
// wellness/WakeAlarm.kt; the screens in ui/Alarms.kt and ui/AlarmActivity.kt.

package mx.loperstack.tokkip.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * One alarm. `days` holds the days it repeats on, as Calendar's DAY_OF_WEEK
 * (1 = Sunday … 7 = Saturday); empty means it rings once and switches off.
 */
data class WakeAlarm(
    val id: String,
    val minute: Int,
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val label: String = "",
    /** The sound starts soft and grows over a minute. */
    val gentle: Boolean = true,
    val vibrate: Boolean = true,
    /** To turn it off you breathe with Tokkip (three breaths) instead of one tap. */
    val breathe: Boolean = false,
    val snoozeMin: Int = 9,
)

/** An alarm put off: when it rings again. */
data class Snooze(val id: String, val at: Long)

const val ALARM_LIMIT = 20
val SNOOZE_CHOICES = listOf(5, 9, 10, 15)

/** Monday first, the way a Mexican week reads. */
val ALARM_WEEK = listOf(
    Calendar.MONDAY to "L", Calendar.TUESDAY to "M", Calendar.WEDNESDAY to "M", Calendar.THURSDAY to "J",
    Calendar.FRIDAY to "V", Calendar.SATURDAY to "S", Calendar.SUNDAY to "D",
)
val WEEKDAYS = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
val WEEKEND = setOf(Calendar.SATURDAY, Calendar.SUNDAY)

/** When `a` rings next after `now` (strictly later), or null when it is off. */
fun nextRing(a: WakeAlarm, now: Long): Long? {
    if (!a.enabled) return null
    val c = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, a.minute / 60); set(Calendar.MINUTE, a.minute % 60)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
    if (a.days.isEmpty()) return c.timeInMillis
    repeat(7) {
        if (c.get(Calendar.DAY_OF_WEEK) in a.days) return c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 1)
    }
    return null
}

/** The next thing to ring: a snooze, or the soonest alarm. */
fun nextOf(alarms: List<WakeAlarm>, snooze: Snooze?, now: Long): Pair<String, Long>? {
    val due = alarms.mapNotNull { a -> nextRing(a, now)?.let { a.id to it } }.toMutableList()
    if (snooze != null && snooze.at > now) due += snooze.id to snooze.at
    return due.minByOrNull { it.second }
}

/** "Una vez", "Todos los días", "Entre semana", "Fin de semana" or "L M J". */
fun daysText(days: Set<Int>): String = when {
    days.isEmpty() -> "Una vez"
    days.size == 7 -> "Todos los días"
    days == WEEKDAYS -> "Entre semana"
    days == WEEKEND -> "Fin de semana"
    else -> ALARM_WEEK.filter { it.first in days }.joinToString(" ") { (d, l) -> if (d == Calendar.WEDNESDAY) "Mi" else l }
}

/** "Suena en 7 h 20 min", "Suena en 25 min", "Suena en menos de un minuto". */
fun inText(at: Long, now: Long): String {
    val min = ((at - now + 59_999) / 60_000).toInt()
    if (min <= 1) return "Suena en menos de un minuto"
    val d = min / 1440
    val h = (min % 1440) / 60
    val m = min % 60
    val parts = buildList {
        if (d > 0) add(if (d == 1) "1 día" else "$d días")
        if (h > 0) add("$h h")
        if (m > 0 && d == 0) add("$m min")
    }
    return "Suena en " + parts.joinToString(" ")
}

fun alarmsJson(list: List<WakeAlarm>): String = JSONArray().apply {
    for (a in list) put(
        JSONObject()
            .put("id", a.id).put("minute", a.minute).put("days", JSONArray(a.days.sorted()))
            .put("enabled", a.enabled).put("label", a.label).put("gentle", a.gentle)
            .put("vibrate", a.vibrate).put("breathe", a.breathe).put("snoozeMin", a.snoozeMin),
    )
}.toString()

fun alarmsOf(json: String?): List<WakeAlarm> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val days = o.optJSONArray("days")?.let { d -> (0 until d.length()).map { d.optInt(it) }.filter { it in 1..7 }.toSet() } ?: emptySet()
            WakeAlarm(
                id = id,
                minute = o.optInt("minute", 7 * 60).coerceIn(0, 1439),
                days = days,
                enabled = o.optBoolean("enabled", true),
                label = o.optString("label").take(40),
                gentle = o.optBoolean("gentle", true),
                vibrate = o.optBoolean("vibrate", true),
                breathe = o.optBoolean("breathe", false),
                snoozeMin = o.optInt("snoozeMin", 9).takeIf { it in SNOOZE_CHOICES } ?: 9,
            )
        }.take(ALARM_LIMIT)
    }.getOrDefault(emptyList())
}

fun snoozeJson(s: Snooze?): String = if (s == null) "" else JSONObject().put("id", s.id).put("at", s.at).toString()

fun snoozeOf(json: String?): Snooze? = runCatching {
    if (json.isNullOrBlank()) null else JSONObject(json).let { Snooze(it.getString("id"), it.getLong("at")) }
}.getOrNull()
