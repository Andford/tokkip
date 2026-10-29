package mx.loperstack.tokkip

import mx.loperstack.tokkip.data.Snooze
import mx.loperstack.tokkip.data.WEEKDAYS
import mx.loperstack.tokkip.data.WEEKEND
import mx.loperstack.tokkip.data.WakeAlarm
import mx.loperstack.tokkip.data.alarmsJson
import mx.loperstack.tokkip.data.alarmsOf
import mx.loperstack.tokkip.data.daysText
import mx.loperstack.tokkip.data.inText
import mx.loperstack.tokkip.data.nextOf
import mx.loperstack.tokkip.data.nextRing
import mx.loperstack.tokkip.data.snoozeJson
import mx.loperstack.tokkip.data.snoozeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class AlarmsTest {
    /** Local time: year, month (1–12), day, hour, minute. */
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, mo - 1, d, h, mi, 0) }.timeInMillis

    // Thursday 29 October 2026.
    private val thu0630 = at(2026, 10, 29, 6, 30)

    @Test fun a_one_time_alarm_rings_today_if_its_hour_is_still_to_come_else_tomorrow() {
        assertEquals(at(2026, 10, 29, 7, 0), nextRing(WakeAlarm("a", 7 * 60), thu0630))
        assertEquals(at(2026, 10, 30, 6, 0), nextRing(WakeAlarm("a", 6 * 60), thu0630))
        // Right on the minute: that one has rung, the next is tomorrow.
        assertEquals(at(2026, 10, 30, 6, 30), nextRing(WakeAlarm("a", 6 * 60 + 30), thu0630))
    }

    @Test fun repeating_alarms_skip_to_their_next_day() {
        // Weekdays at 6:00, asked Thursday 6:30: Friday.
        assertEquals(at(2026, 10, 30, 6, 0), nextRing(WakeAlarm("a", 6 * 60, WEEKDAYS), thu0630))
        // Weekends at 9:00: Saturday.
        assertEquals(at(2026, 10, 31, 9, 0), nextRing(WakeAlarm("a", 9 * 60, WEEKEND), thu0630))
        // Friday at 6:00 asked Friday 7:00: next week.
        assertEquals(at(2026, 11, 6, 6, 0), nextRing(WakeAlarm("a", 6 * 60, setOf(Calendar.FRIDAY)), at(2026, 10, 30, 7, 0)))
    }

    @Test fun an_alarm_off_never_rings_and_the_soonest_one_or_a_snooze_wins() {
        assertNull(nextRing(WakeAlarm("a", 7 * 60, enabled = false), thu0630))
        val list = listOf(WakeAlarm("late", 8 * 60), WakeAlarm("early", 7 * 60), WakeAlarm("off", 6 * 60 + 45, enabled = false))
        assertEquals("early" to at(2026, 10, 29, 7, 0), nextOf(list, null, thu0630))
        assertEquals("late" to at(2026, 10, 29, 6, 39), nextOf(list, Snooze("late", at(2026, 10, 29, 6, 39)), thu0630))
        // A snooze already past counts for nothing.
        assertEquals("early" to at(2026, 10, 29, 7, 0), nextOf(list, Snooze("late", at(2026, 10, 29, 6, 0)), thu0630))
        assertNull(nextOf(emptyList(), null, thu0630))
    }

    @Test fun days_and_time_left_read_naturally() {
        assertEquals("Una vez", daysText(emptySet()))
        assertEquals("Entre semana", daysText(WEEKDAYS))
        assertEquals("Fin de semana", daysText(WEEKEND))
        assertEquals("Todos los días", daysText(WEEKDAYS + WEEKEND))
        assertEquals("L Mi V", daysText(setOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY)))
        assertEquals("Suena en 30 min", inText(thu0630 + 30 * 60_000, thu0630))
        assertEquals("Suena en 7 h 20 min", inText(thu0630 + (7 * 60 + 20) * 60_000L, thu0630))
        assertEquals("Suena en 1 día 2 h", inText(thu0630 + (26 * 60 + 10) * 60_000L, thu0630))
        assertEquals("Suena en menos de un minuto", inText(thu0630 + 20_000, thu0630))
    }

    @Test fun alarms_and_the_snooze_survive_a_round_trip() {
        val list = listOf(
            WakeAlarm("a", 6 * 60 + 15, WEEKDAYS, label = "Gym", gentle = false, breathe = true, snoozeMin = 5),
            WakeAlarm("b", 9 * 60, enabled = false),
        )
        assertEquals(list, alarmsOf(alarmsJson(list)))
        assertEquals(emptyList<WakeAlarm>(), alarmsOf("not json"))
        // A snooze length nobody offers falls back to 9 minutes.
        assertEquals(9, alarmsOf("""[{"id":"x","minute":420,"snoozeMin":7}]""").single().snoozeMin)
        val s = Snooze("a", thu0630)
        assertEquals(s, snoozeOf(snoozeJson(s)))
        assertNull(snoozeOf(snoozeJson(null)))
    }
}
