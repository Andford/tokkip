package mx.loperstack.tokkip

import mx.loperstack.tokkip.data.Care
import mx.loperstack.tokkip.data.DayLog
import mx.loperstack.tokkip.data.Habit
import mx.loperstack.tokkip.data.LOOP
import mx.loperstack.tokkip.data.LogNote
import mx.loperstack.tokkip.data.MoodEntry
import mx.loperstack.tokkip.data.Night
import mx.loperstack.tokkip.data.Routine
import mx.loperstack.tokkip.data.STAGES
import mx.loperstack.tokkip.data.dayKey
import mx.loperstack.tokkip.data.growth
import mx.loperstack.tokkip.data.noteOfTheDay
import mx.loperstack.tokkip.data.stageFor
import mx.loperstack.tokkip.data.tokkipNotes
import mx.loperstack.tokkip.data.wordsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GrowthTest {
    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 24, 21, 0, 0) }.timeInMillis
    private fun ago(days: Int, hour: Int = 21) = Calendar.getInstance().apply {
        timeInMillis = now; add(Calendar.DAY_OF_YEAR, -days); set(Calendar.HOUR_OF_DAY, hour)
    }.timeInMillis
    private fun day(days: Int) = dayKey(ago(days))
    private fun log(days: Int, mood: Int, text: String = "", hour: Int = 21) = DayLog(
        day(days), mood, if (text.isEmpty()) emptyList() else listOf(LogNote(ago(days, hour), text)), listOf(MoodEntry(ago(days, hour), mood)),
    )

    @Test fun nothingYet() {
        val g = growth(Routine(), now)
        assertEquals(0, g.points)
        assertEquals("Recién llegado", g.stage.name)
        assertEquals(40, g.toNext)
        assertEquals("Todavía te estoy conociendo.", noteOfTheDay(Routine(), now).text)
    }

    @Test fun eachKindHasADailyCeiling() {
        val habits = (1..5).map { Habit("h$it", "H$it", done = setOf(day(0))) }
        val g = growth(Routine(habits = habits, breaths = listOf(ago(0), ago(0, 8), ago(0, 9))), now)
        assertEquals(Care.RACHAS.cap, g.byCare[Care.RACHAS])
        assertEquals(Care.RESPIRAR.cap, g.byCare[Care.RESPIRAR])
        assertEquals(Care.RACHAS.cap + Care.RESPIRAR.cap, g.today)
    }

    @Test fun diarySleepAndMoodAddUp() {
        val long = (1..45).joinToString(" ") { "palabra" }
        val r = Routine(logs = listOf(log(1, 4, long)), nights = listOf(Night(day(1), 480)), sleepGoalMin = 480)
        val g = growth(r, now)
        assertEquals(6 + 2 + 3, g.points)
        assertEquals(11, g.week)
        assertEquals(0, g.today)
    }

    @Test fun futureDaysDontCount() {
        val r = Routine(habits = listOf(Habit("h", "H", done = setOf(day(-2)))))
        assertEquals(0, growth(r, now).points)
    }

    @Test fun stagesAndTheCycle() {
        assertEquals(1, stageFor(39).level)
        assertEquals(2, stageFor(40).level)
        assertEquals(6, stageFor(STAGES.last().from + 2 * LOOP + 100).level)
        // A year of ticking three habits a day: Leyenda, some turns into the cycle.
        val habits = (1..3).map { i -> Habit("h$i", "H$i", done = (0 until 365).map { day(it) }.toSet()) }
        val g = growth(Routine(habits = habits), now)
        assertEquals(365 * 9, g.points)
        assertEquals("Leyenda", g.stage.name)
        assertEquals((365 * 9 - 1200) / LOOP, g.loops)
        assertEquals("Leyenda ★${g.loops}", g.title)
    }

    @Test fun wordsAreNormalised() {
        assertEquals(listOf("que", "dia", "tan", "estresante", "manana"), wordsOf("¡Qué día tan ESTRESANTE! mañana"))
    }

    @Test fun noticesAHabitThatLiftsYourMood() {
        val habit = Habit("walk", "Caminar", done = (1..20 step 2).map { day(it) }.toSet(), createdAt = ago(40))
        val logs = (1..20).map { d -> log(d, if (d % 2 == 1) 5 else 2) }
        val notes = tokkipNotes(Routine(logs = logs, habits = listOf(habit)), now)
        assertTrue(notes.joinToString { it.id }, notes.any { it.id == "habit-mood-walk" })
    }

    @Test fun noticesAHeavyToneAndAGap() {
        val logs = (5..12).map { d -> log(d, 2, "Muy cansado y estresado, el trabajo me tiene agotado y preocupado") }
        val notes = tokkipNotes(Routine(logs = logs), now)
        val ids = notes.map { it.id }
        assertTrue(ids.toString(), "write-gap" in ids)
        assertTrue(ids.toString(), "tone-heavy" in ids)
        assertTrue(ids.toString(), "write-night" in ids)
        // The gap matters most today.
        assertEquals("write-gap", noteOfTheDay(Routine(logs = logs), now).id)
    }

    @Test fun noticesWhatYouWriteAbout() {
        val logs = (1..12).map { d -> if (d % 2 == 0) log(d, 5, "Comida con mi familia y mis hermanos") else log(d, 3, "Un día normal") }
        val ids = tokkipNotes(Routine(logs = logs), now).map { it.id }
        assertTrue(ids.toString(), "theme-up-familia" in ids)
    }

    @Test fun noticesShortSleep() {
        val nights = (1..6).map { Night(day(it), 330) }
        val ids = tokkipNotes(Routine(nights = nights, sleepGoalMin = 480), now).map { it.id }
        assertTrue(ids.toString(), "sleep-short" in ids)
    }
}
