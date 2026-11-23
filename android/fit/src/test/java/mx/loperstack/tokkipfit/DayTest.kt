package mx.loperstack.tokkipfit

import mx.loperstack.tokkipfit.data.Day
import mx.loperstack.tokkipfit.data.Goals
import mx.loperstack.tokkipfit.data.Today
import mx.loperstack.tokkipfit.data.Vibe
import mx.loperstack.tokkipfit.data.challengeOf
import mx.loperstack.tokkipfit.data.distance
import mx.loperstack.tokkipfit.data.fmt
import mx.loperstack.tokkipfit.data.hours
import mx.loperstack.tokkipfit.data.sampleWeek
import mx.loperstack.tokkipfit.data.vibeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayTest {
    private val today = LocalDate.of(2026, 11, 23)

    private fun week(vararg steps: Long, minutes: Long = 0, kcal: Double = 0.0) =
        Today(steps.mapIndexed { i, s -> Day(today.minusDays((steps.size - 1 - i).toLong()), s, 0.0, kcal, minutes) })

    @Test fun streakCountsBackFromYesterdayAndTodayOnceMet() {
        assertEquals(2, week(1_000, 9_000, 8_500, 3_000).streak)
        assertEquals(3, week(1_000, 9_000, 8_500, 8_000).streak)
        assertEquals(0, week(9_000, 1_000, 2_000).streak)
        assertEquals(0, Today(emptyList()).streak)
    }

    @Test fun ringsAndClosed() {
        val t = week(8_000, minutes = 45, kcal = 200.0)
        assertEquals(1f, t.stepsRing, 0.001f)
        assertEquals(1.5f, t.minutesRing, 0.001f)
        assertEquals(0.5f, t.kcalRing, 0.001f)
        assertEquals(2, t.closed)
        assertEquals((1f + 1f + 0.5f) / 3f, t.overall, 0.001f)
    }

    @Test fun tokkipsVibe() {
        assertEquals(Vibe.CHAMPION, vibeOf(week(9_000, minutes = 40, kcal = 500.0), 12))
        assertEquals(Vibe.STARTING, vibeOf(week(0), 8))
        assertEquals(Vibe.LAZY, vibeOf(week(500), 20))
        assertEquals(Vibe.MOVING, vibeOf(week(4_000, minutes = 10), 13))
        assertEquals(Vibe.GREAT, vibeOf(week(8_000, minutes = 25, kcal = 150.0), 13))
    }

    @Test fun challengeSaysWhatsMissing() {
        val c = challengeOf(week(6_500))
        assertEquals("Te faltan 1,500 pasos", c.title)
        assertTrue(c.body.contains("15 minutos"))
        assertEquals("10 minutos más de ejercicio", challengeOf(week(9_000, minutes = 20)).title)
        // Far from the goal, or done: one for fun, the same all day.
        assertEquals(challengeOf(week(100)), challengeOf(week(200)))
    }

    @Test fun formats() {
        assertEquals("12,345", fmt(12_345))
        assertEquals("999", fmt(999))
        assertEquals("1,000,000", fmt(1_000_000))
        assertEquals("7 h 22 min", hours(442))
        assertEquals("45 min", hours(45))
        assertEquals("5.2 km", distance(5_230.0))
        assertEquals("830 m", distance(830.0))
    }

    @Test fun goalsStayReasonable() {
        assertEquals(Goals(1_000, 5, 50), Goals(0, 0, 0).clean())
    }

    @Test fun sampleIsAFullWeek() {
        val s = sampleWeek(today)
        assertEquals(7, s.week.size)
        assertEquals(today, s.today.date)
        assertTrue(s.sample)
    }
}
