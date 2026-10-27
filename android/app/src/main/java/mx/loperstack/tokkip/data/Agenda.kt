// Events from the calendars synced on this device (today's and tomorrow's for
// Inicio, any range for the Agenda screen) —
// Google Calendar included — through Android's own calendar provider. No sign-in:
// the phone already has your account; it only needs READ_CALENDAR.

package mx.loperstack.tokkip.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar

data class CalEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int?,
    val meetUrl: String?,
    val calendarId: Long = 0,
    val location: String? = null,
)

private val MEET = Regex("""https://meet\.google\.com/[a-z]{3}-[a-z]{4}-[a-z]{3}""")

/** The first Google Meet link in an event's text, if any. */
fun findMeet(vararg text: String?): String? = text.firstNotNullOfOrNull { t -> t?.let { MEET.find(it)?.value } }

/** Midnight today and midnight after tomorrow, local. */
fun agendaWindow(now: Long): Pair<Long, Long> {
    val c = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = c.timeInMillis
    c.add(Calendar.DAY_OF_YEAR, 2)
    return start to c.timeInMillis
}

/** Events not over yet, soonest first; all-day ones lead their day. */
fun upcoming(events: List<CalEvent>, now: Long) =
    events.filter { it.end > now }.sortedWith(compareBy<CalEvent> { it.begin }.thenBy { !it.allDay })

fun nextEvent(events: List<CalEvent>, now: Long) = upcoming(events, now).firstOrNull { !it.allDay && it.begin > now }

fun isTomorrow(e: CalEvent, now: Long): Boolean {
    val (_, end) = agendaWindow(now)
    return e.begin >= end - 24 * 3600_000L
}

object Agenda {
    /** Today's and tomorrow's events. */
    fun load(context: Context, now: Long = System.currentTimeMillis()): List<CalEvent> {
        val (start, end) = agendaWindow(now)
        return load(context, start, end)
    }

    /** Every event between `start` and `end` (the Agenda screen: a month, the next weeks). */
    fun load(context: Context, start: Long, end: Long): List<CalEvent> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.VISIBLE,
            CalendarContract.Instances.CALENDAR_ID,
        )
        val out = mutableListOf<CalEvent>()
        try {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(9) == 0) continue // a calendar hidden in the Calendar app
                    if (c.getInt(6) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                    val allDay = c.getInt(4) == 1
                    var begin = c.getLong(2)
                    var finish = c.getLong(3)
                    if (allDay) {
                        // All-day instances are stored in UTC midnights: move them to local days.
                        val tz = java.util.TimeZone.getDefault()
                        begin -= tz.getOffset(begin)
                        finish -= tz.getOffset(finish)
                    }
                    out += CalEvent(
                        id = c.getLong(0),
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(sin título)",
                        begin = begin,
                        end = finish,
                        allDay = allDay,
                        color = if (c.isNull(5)) null else c.getInt(5),
                        meetUrl = findMeet(c.getString(7), c.getString(8)),
                        calendarId = c.getLong(10),
                        location = c.getString(7)?.takeIf { it.isNotBlank() },
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return out
    }
}
