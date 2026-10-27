// The diary: one entry per day — a mood (1…5) and short timestamped lines,
// the daily log, some of them photos. Kept only on this phone, never sent
// anywhere (the watch only learns today's mood); you can export it as a PDF,
// or a .zip copy you can restore.

package mx.loperstack.tokkip.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A line of the day; `image` is a photo's file name in the diary's folder (DiaryImages). */
data class LogNote(val at: Long, val text: String, val image: String? = null)

/** One "how do I feel" through the day: 08:10 Genial, 18:05 Bajón… */
data class MoodEntry(val at: Long, val value: Int)

/** `mood` is the latest of `moods` (or a day's single mood from before the timeline). */
data class DayLog(val day: String, val mood: Int = 0, val notes: List<LogNote> = emptyList(), val moods: List<MoodEntry> = emptyList())

/** The day's average mood, rounded (for the month's colours); 0 if none. */
val DayLog.averageMood: Int
    get() = if (moods.isEmpty()) mood else kotlin.math.round(moods.map { it.value }.average()).toInt().coerceIn(1, 5)

/** Logs a mood at `at`; the day's mood becomes the latest one. */
fun addMood(logs: List<DayLog>, day: String, at: Long, value: Int): List<DayLog> {
    if (value !in 1..5) return logs
    return upsert(logs, day) { d ->
        val list = (d.moods.filter { it.at != at } + MoodEntry(at, value)).sortedBy { it.at }.takeLast(48)
        d.copy(moods = list, mood = list.last().value)
    }
}

fun removeMood(logs: List<DayLog>, day: String, at: Long): List<DayLog> =
    upsert(logs, day) { d ->
        val list = d.moods.filter { it.at != at }
        d.copy(moods = list, mood = list.lastOrNull()?.value ?: 0)
    }.filter { it.mood > 0 || it.notes.isNotEmpty() || it.moods.isNotEmpty() }

const val NOTE_MAX_LEN = 500

/** Only the names DiaryImages makes: nothing that could point outside its folder. */
fun isImageName(name: String) = Regex("""img-[A-Za-z0-9_-]{1,60}\.jpg""").matches(name)

/** yyyy-MM-dd, local. */
fun dayKey(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

/** The local midnight of a day key. */
fun dayStart(day: String): Long = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day)!!.time }.getOrDefault(0L)

/** "Día 042": days since the first entry, the first being day 1. */
fun dayNumber(logs: List<DayLog>, day: String): Int {
    val first = logs.minOfOrNull { it.day } ?: day
    val a = Calendar.getInstance().apply { timeInMillis = dayStart(minOf(first, day)) }
    val b = Calendar.getInstance().apply { timeInMillis = dayStart(day) }
    var n = 1
    while (a.before(b) && n < 100_000) { a.add(Calendar.DAY_OF_YEAR, 1); n++ }
    return n
}

/** Days in a row with an entry, ending today (or yesterday, if today has none yet). */
fun streak(logs: List<DayLog>, now: Long): Int {
    val days = logs.filter { it.mood > 0 || it.notes.isNotEmpty() }.map { it.day }.toHashSet()
    val c = Calendar.getInstance().apply { timeInMillis = now }
    if (dayKey(c.timeInMillis) !in days) c.add(Calendar.DAY_OF_YEAR, -1)
    var n = 0
    while (dayKey(c.timeInMillis) in days) { n++; c.add(Calendar.DAY_OF_YEAR, -1) }
    return n
}

fun withMood(logs: List<DayLog>, day: String, mood: Int): List<DayLog> = upsert(logs, day) { it.copy(mood = mood.coerceIn(0, 5)) }

fun withNote(logs: List<DayLog>, day: String, at: Long, text: String, image: String? = null): List<DayLog> {
    val clean = text.trim().take(NOTE_MAX_LEN)
    if (clean.isEmpty() && image == null) return logs
    return upsert(logs, day) { it.copy(notes = (it.notes.filter { n -> n.at != at } + LogNote(at, clean, image)).sortedBy { n -> n.at }) }
}

/** A restored copy joins what is here: its days and lines are added, nothing is lost. */
fun mergeLogs(here: List<DayLog>, other: List<DayLog>): List<DayLog> {
    val days = (here.map { it.day } + other.map { it.day }).toSortedSet()
    return days.map { d ->
        val a = here.firstOrNull { it.day == d }
        val b = other.firstOrNull { it.day == d }
        val notes = ((a?.notes ?: emptyList()) + (b?.notes ?: emptyList())).distinctBy { it.at to it.text }.sortedBy { it.at }
        DayLog(d, a?.mood?.takeIf { it > 0 } ?: b?.mood ?: 0, notes)
    }
}

fun withoutNote(logs: List<DayLog>, day: String, at: Long): List<DayLog> =
    upsert(logs, day) { it.copy(notes = it.notes.filter { n -> n.at != at }) }.filter { it.mood > 0 || it.notes.isNotEmpty() }

private fun upsert(logs: List<DayLog>, day: String, f: (DayLog) -> DayLog): List<DayLog> {
    val old = logs.firstOrNull { it.day == day } ?: DayLog(day)
    return (logs.filter { it.day != day } + f(old)).sortedBy { it.day }.takeLast(1500)
}

fun logsJson(logs: List<DayLog>): String = JSONArray().apply {
    for (l in logs) put(
        JSONObject().put("day", l.day).put("mood", l.mood)
            .put("moods", JSONArray().apply { l.moods.forEach { put(JSONObject().put("at", it.at).put("v", it.value)) } })
            .put("notes", JSONArray().apply { l.notes.forEach { n ->
                put(JSONObject().put("at", n.at).put("text", n.text).apply { n.image?.let { put("image", it) } })
            } }),
    )
}.toString()

fun logsOf(raw: String?): List<DayLog> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val a = JSONArray(raw)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val day = o.optString("day", "")
            if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(day)) return@mapNotNull null
            val notes = o.optJSONArray("notes")?.let { n ->
                (0 until n.length()).mapNotNull { j ->
                    val x = n.optJSONObject(j) ?: return@mapNotNull null
                    val t = x.optString("text", "").trim()
                    val img = x.optString("image", "").takeIf { isImageName(it) }
                    if (t.isEmpty() && img == null) null else LogNote(x.optLong("at"), t.take(NOTE_MAX_LEN), img)
                }
            } ?: emptyList()
            val moods = o.optJSONArray("moods")?.let { m ->
                (0 until m.length()).mapNotNull { j ->
                    val x = m.optJSONObject(j) ?: return@mapNotNull null
                    val v = x.optInt("v")
                    if (v in 1..5) MoodEntry(x.optLong("at"), v) else null
                }
            } ?: emptyList()
            DayLog(day, o.optInt("mood", 0).coerceIn(0, 5), notes, moods)
        }.sortedBy { it.day }
    } catch (_: Exception) {
        emptyList()
    }
}
