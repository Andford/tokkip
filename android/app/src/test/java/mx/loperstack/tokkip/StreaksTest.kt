package mx.loperstack.tokkip

import mx.loperstack.tokkip.data.DayMark
import mx.loperstack.tokkip.data.Habit
import mx.loperstack.tokkip.data.best
import mx.loperstack.tokkip.data.dayKey
import mx.loperstack.tokkip.data.habitsJson
import mx.loperstack.tokkip.data.habitsOf
import mx.loperstack.tokkip.data.scheduleText
import mx.loperstack.tokkip.data.streak
import mx.loperstack.tokkip.data.toggle
import mx.loperstack.tokkip.data.week
import mx.loperstack.tokkip.wellness.BreathPhase
import mx.loperstack.tokkip.wellness.Breathing
import mx.loperstack.tokkip.wellness.breathFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StreaksTest {
    private fun day(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis
    private val DAY = 86_400_000L

    @Test fun dailyStreakCountsBackAndTodayDoesNotBreakIt() {
        val now = day(2026, 10, 21)
        var h = Habit("a", "Agua", createdAt = now - 30 * DAY)
        for (i in 1..4) h = h.toggle(now - i * DAY)
        assertEquals(4, h.streak(now)) // today still to do
        h = h.toggle(now)
        assertEquals(5, h.streak(now))
        h = h.toggle(now - 2 * DAY) // a gap two days ago
        assertEquals(2, h.streak(now))
        assertEquals(2, h.best(now))
    }

    @Test fun offDaysDoNotBreakTheStreak() {
        // Wed 21 Oct 2026; only Mon, Wed, Fri.
        val now = day(2026, 10, 21)
        var h = Habit("b", "Correr", days = setOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY), createdAt = now - 30 * DAY)
        h = h.toggle(now - 2 * DAY).toggle(now - 5 * DAY).toggle(now)
        assertEquals(3, h.streak(now))
        val w = h.week(now)
        assertEquals(7, w.size)
        assertEquals(DayMark.DONE, w.last())
        assertEquals(DayMark.OFF, w[5]) // Tuesday
        assertEquals("L X V", h.scheduleText())
    }

    @Test fun habitsRoundTrip() {
        val h = Habit("c", "Leer", "book", setOf(2, 4), 7 * 60 + 30, true, 0xFF60A5FA, setOf(dayKey(day(2026, 10, 20))), 5L)
        assertEquals(listOf(h), habitsOf(habitsJson(listOf(h))))
        assertTrue(habitsOf("nope").isEmpty())
        assertEquals("L X · 7:30", h.scheduleText())
    }

    @Test fun boxBreathingHolds() {
        val t0 = 1_000_000L
        val b = Breathing(t0, 1, 4.0, 4.0, 4.0, 4.0)
        fun at(s: Double) = breathFrame(b, t0 + (s * 1000).toLong())
        assertEquals(BreathPhase.IN, at(1.0).phase)
        assertEquals(BreathPhase.HOLD_IN, at(5.0).phase)
        assertTrue(at(5.0).inhaling)
        assertEquals(1f, at(5.0).fill, 1e-6f)
        assertEquals(BreathPhase.OUT, at(9.0).phase)
        assertFalse(at(9.0).inhaling)
        assertEquals(BreathPhase.HOLD_OUT, at(13.0).phase)
        assertEquals(0f, at(13.0).fill, 1e-6f)
        assertEquals(BreathPhase.IN, at(17.0).phase)
    }
}
