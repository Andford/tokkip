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
    /** How it vibrates when [vibrate] is on: one of [VIBRATIONS]. */
    val vibration: String = VIBRATION_NORMAL,
    /** To turn it off you breathe with Tokkip (three breaths) instead of one tap. */
    val breathe: Boolean = false,
    val snoozeMin: Int = 9,
    /** What it can ring with; with more than one, a different one each morning. */
    val tunes: List<Tune> = listOf(Tune(TUNE_DEFAULT)),
)

/**
 * A sound for an alarm: one of Tokkip's own (`ref` is its key in [ALARM_TUNES])
 * or a song from the phone (`ref` is its content:// address, `name` its file name).
 */
data class Tune(val ref: String, val name: String = "") {
    val isFile get() = ref.startsWith("content:")
}

const val TUNE_DEFAULT = "tokkip"
const val TUNE_LIMIT = 12

/** Tokkip's own wake-up tunes, in the order the editor shows them. */
val ALARM_TUNES = listOf(
    TUNE_DEFAULT to "Tokkip",
    "lofi_cafe" to "Café lo-fi",
    "lofi_lluvia" to "Lluvia lo-fi",
    "lofi_amanecer" to "Amanecer lo-fi",
    "lofi_jardin" to "Jardín lo-fi",
)

/** The tune for this ring: one of the chosen, at random. */
fun pickTune(tunes: List<Tune>, random: kotlin.random.Random = kotlin.random.Random): Tune =
    tunes.takeIf { it.isNotEmpty() }?.random(random) ?: Tune(TUNE_DEFAULT)

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

const val VIBRATION_NORMAL = "normal"

/** A way to vibrate: its key (stored), its name and what it's for. */
data class Vibration(val key: String, val name: String, val about: String)

val VIBRATIONS = listOf(
    Vibration("suave", "Suave", "Toquecitos ligeros, para sueño sensible"),
    Vibration("latido", "Latido", "Como un corazón: tranquila pero se siente"),
    Vibration("creciente", "Creciente", "Empieza muy suave y sube en un minuto"),
    Vibration(VIBRATION_NORMAL, "Normal", "Dos pulsos firmes y una pausa"),
    Vibration("olas", "Olas", "Cada vez más fuerte, y vuelve a empezar"),
    Vibration("intensa", "Intensa", "Larga y al máximo, para sueño pesado"),
)

/**
 * One round of [key]'s vibration, to repeat while the alarm rings: the
 * timings (off, on, off, on… in ms) and how strong each part is (0–255).
 * [level] scales the strength (Creciente grows it from 0.15 to 1).
 */
fun vibrationWave(key: String, level: Float = 1f): Pair<LongArray, IntArray> {
    fun amp(a: Int) = if (a == 0) 0 else (a * level).toInt().coerceIn(1, 255)
    val (t, a) = when (key) {
        "suave" -> longArrayOf(0, 120, 250, 120, 1800) to intArrayOf(0, 70, 0, 70, 0)
        "latido" -> longArrayOf(0, 90, 120, 160, 1000) to intArrayOf(0, 170, 0, 255, 0)
        "creciente" -> longArrayOf(0, 450, 550) to intArrayOf(0, 255, 0)
        "olas" -> longArrayOf(0, 160, 160, 300, 160, 600, 900) to intArrayOf(0, 90, 0, 170, 0, 255, 0)
        "intensa" -> longArrayOf(0, 1100, 150) to intArrayOf(0, 255, 0)
        else -> longArrayOf(0, 500, 300, 500, 1200) to intArrayOf(0, 220, 0, 220, 0)
    }
    return t to IntArray(a.size) { amp(a[it]) }
}

fun alarmsJson(list: List<WakeAlarm>): String = JSONArray().apply {
    for (a in list) put(
        JSONObject()
            .put("id", a.id).put("minute", a.minute).put("days", JSONArray(a.days.sorted()))
            .put("enabled", a.enabled).put("label", a.label).put("gentle", a.gentle)
            .put("vibrate", a.vibrate).put("vibration", a.vibration).put("breathe", a.breathe).put("snoozeMin", a.snoozeMin)
            .put("tunes", JSONArray().apply { for (t in a.tunes) put(JSONObject().put("ref", t.ref).put("name", t.name)) }),
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
                vibration = o.optString("vibration", VIBRATION_NORMAL).takeIf { v -> VIBRATIONS.any { it.key == v } } ?: VIBRATION_NORMAL,
                breathe = o.optBoolean("breathe", false),
                snoozeMin = o.optInt("snoozeMin", 9).takeIf { it in SNOOZE_CHOICES } ?: 9,
                tunes = o.optJSONArray("tunes")?.let { t ->
                    (0 until t.length()).mapNotNull { k ->
                        val x = t.optJSONObject(k) ?: return@mapNotNull null
                        val ref = x.optString("ref")
                        Tune(ref, x.optString("name").take(80)).takeIf { it.isFile || ALARM_TUNES.any { (key, _) -> key == ref } }
                    }.distinctBy { it.ref }.take(TUNE_LIMIT)
                }?.takeIf { it.isNotEmpty() } ?: listOf(Tune(TUNE_DEFAULT)),
            )
        }.take(ALARM_LIMIT)
    }.getOrDefault(emptyList())
}

fun snoozeJson(s: Snooze?): String = if (s == null) "" else JSONObject().put("id", s.id).put("at", s.at).toString()

fun snoozeOf(json: String?): Snooze? = runCatching {
    if (json.isNullOrBlank()) null else JSONObject(json).let { Snooze(it.getString("id"), it.getLong("at")) }
}.getOrNull()
