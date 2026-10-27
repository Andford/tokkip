package mx.loperstack.tokkip

import mx.loperstack.tokkip.data.CalEvent
import mx.loperstack.tokkip.data.findMeet
import mx.loperstack.tokkip.data.nextEvent
import mx.loperstack.tokkip.data.parsePomodoro
import mx.loperstack.tokkip.data.parseTasks
import mx.loperstack.tokkip.data.pomodoroJson
import mx.loperstack.tokkip.data.tasksJson
import mx.loperstack.tokkip.data.upcoming
import mx.loperstack.tokkip.wellness.Breathing
import mx.loperstack.tokkip.data.DayLog
import mx.loperstack.tokkip.data.dayKey
import mx.loperstack.tokkip.data.dayNumber
import mx.loperstack.tokkip.data.fromGoogle
import mx.loperstack.tokkip.data.gTaskOf
import mx.loperstack.tokkip.data.logsJson
import mx.loperstack.tokkip.data.logsOf
import mx.loperstack.tokkip.data.streak
import mx.loperstack.tokkip.data.withMood
import mx.loperstack.tokkip.data.withNote
import mx.loperstack.tokkip.data.withoutNote
import org.json.JSONObject
import mx.loperstack.tokkip.data.addMood
import mx.loperstack.tokkip.data.averageMood
import mx.loperstack.tokkip.data.removeMood
import mx.loperstack.tokkip.character.Outfit
import mx.loperstack.tokkip.wellness.BreathSession
import mx.loperstack.tokkip.wellness.hrvStats
import mx.loperstack.tokkip.wellness.minuteTable
import mx.loperstack.tokkip.wellness.rmssd
import mx.loperstack.tokkip.wellness.sessionOf
import mx.loperstack.tokkip.wellness.toJson
import mx.loperstack.tokkip.data.SleepGoal
import mx.loperstack.tokkip.data.mergeHealth
import mx.loperstack.tokkip.data.minutes
import mx.loperstack.tokkip.data.personalTips
import mx.loperstack.tokkip.data.sleepEntry
import mx.loperstack.tokkip.data.sleepJson
import mx.loperstack.tokkip.data.sleepOf
import mx.loperstack.tokkip.data.sleepStats
import mx.loperstack.tokkip.wellness.breathingOf
import mx.loperstack.tokkip.wellness.toWire
import mx.loperstack.tokkip.wellness.Phase
import mx.loperstack.tokkip.wellness.PomodoroEvent
import mx.loperstack.tokkip.wellness.Pomodoro
import mx.loperstack.tokkip.wellness.TASK_LIMIT
import mx.loperstack.tokkip.wellness.addTask
import mx.loperstack.tokkip.wellness.breathFrame
import mx.loperstack.tokkip.wellness.clearDone
import mx.loperstack.tokkip.wellness.clock
import mx.loperstack.tokkip.wellness.finishPhase
import mx.loperstack.tokkip.wellness.isPaused
import mx.loperstack.tokkip.wellness.isRunning
import mx.loperstack.tokkip.wellness.left
import mx.loperstack.tokkip.wellness.orderedTasks
import mx.loperstack.tokkip.wellness.pause
import mx.loperstack.tokkip.wellness.pendingCount
import mx.loperstack.tokkip.wellness.resume
import mx.loperstack.tokkip.wellness.startFocus
import mx.loperstack.tokkip.wellness.tick
import mx.loperstack.tokkip.wellness.toggleTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val T0 = 1_760_000_000_000L
private const val MIN = 60_000L

class LogicTest {
    @Test fun tasksAreTrimmedEmptyOnesRefused() {
        val t = addTask(emptyList(), "  Llamar a   Bunday ", T0, "a")
        assertEquals("Llamar a Bunday", t.single().text)
        assertEquals(1, addTask(t, "   ", T0, "b").size)
    }

    @Test fun doneTasksSinkAndClear() {
        var t = listOf("uno", "dos", "tres").foldIndexed(emptyList<mx.loperstack.tokkip.wellness.Task>()) { i, acc, s -> addTask(acc, s, T0 + i, "$i") }
        t = toggleTask(t, "0")
        assertEquals(listOf("dos", "tres", "uno"), orderedTasks(t).map { it.text })
        assertEquals(2, pendingCount(t))
        assertEquals(listOf("1", "2"), clearDone(t).map { it.id })
    }

    @Test fun aFullListDropsOldestDoneNeverPending() {
        var t = emptyList<mx.loperstack.tokkip.wellness.Task>()
        for (i in 0 until TASK_LIMIT) t = addTask(t, "t$i", T0, "$i")
        t = toggleTask(t, "5")
        t = addTask(t, "nueva", T0, "new")
        assertEquals(TASK_LIMIT, t.size)
        assertFalse(t.any { it.id == "5" })
    }

    @Test fun focusPausesAndResumesWhereItWas() {
        var p = Pomodoro().startFocus(T0)
        assertTrue(p.isRunning())
        assertEquals(15 * 60, p.left(T0 + 10 * MIN))
        p = p.pause(T0 + 10 * MIN)
        assertTrue(p.isPaused())
        assertEquals(15 * 60, p.left(T0 + 60 * MIN))
        p = p.resume(T0 + 60 * MIN)
        assertEquals(14 * 60, p.left(T0 + 61 * MIN))
    }

    @Test fun fourthFocusLeadsToALongBreak() {
        var p = Pomodoro().startFocus(T0)
        val seen = mutableListOf<String>()
        repeat(8) {
            val (n, e) = p.tick(p.endsAt!!)
            seen += "$e:${n.phase}"
            p = n
        }
        assertEquals(
            listOf(
                "FOCUS_DONE:BREAK", "BREAK_DONE:FOCUS", "FOCUS_DONE:BREAK", "BREAK_DONE:FOCUS",
                "FOCUS_DONE:BREAK", "BREAK_DONE:FOCUS", "FOCUS_DONE:LONG_BREAK", "BREAK_DONE:FOCUS",
            ),
            seen,
        )
        assertEquals(4, p.today)
    }

    @Test fun nothingBeforeTimeSkippedFocusDoesNotCount() {
        val p = Pomodoro().startFocus(T0)
        assertNull(p.tick(T0 + MIN).second)
        val (skipped, event) = p.finishPhase(T0 + MIN, counted = false)
        assertEquals(PomodoroEvent.FOCUS_DONE, event)
        assertEquals(Phase.BREAK, skipped.phase)
        assertEquals(0, skipped.today)
    }

    @Test fun breathIsFourInSixOut() {
        val b = Breathing(T0, 1)
        fun at(s: Double) = breathFrame(b, T0 + (s * 1000).toLong())
        assertTrue(at(0.0).inhaling)
        assertEquals(0f, at(0.0).fill, 1e-6f)
        assertEquals(0.5f, at(2.0).fill, 1e-6f)
        assertFalse(at(4.0).inhaling)
        assertEquals(1f, at(4.0).fill, 1e-6f)
        assertEquals(2, at(25.0).breaths)
        assertEquals(35, at(25.0).left)
        assertTrue(at(60.0).done)
        assertFalse(breathFrame(Breathing(), T0).active)
    }

    @Test fun storeRoundTrips() {
        val tasks = addTask(emptyList(), "Propuesta", T0, "a")
        assertEquals(tasks, parseTasks(tasksJson(tasks)))
        assertEquals(emptyList<Any>(), parseTasks("nope"))
        val p = Pomodoro().startFocus(T0)
        assertEquals(p, parsePomodoro(pomodoroJson(p)))
        assertEquals(Pomodoro(), parsePomodoro("{bad"))
    }

    @Test fun agendaFindsMeetAndTheNextEvent() {
        assertEquals("https://meet.google.com/abc-defg-hij", findMeet(null, "Únete: https://meet.google.com/abc-defg-hij ok"))
        assertNull(findMeet("Sala 3", null))
        val past = CalEvent(1, "Desayuno", T0 - 2 * 3600_000, T0 - 3600_000, false, null, null)
        val allDay = CalEvent(2, "Cumple", T0 - 3600_000, T0 + 20 * 3600_000, true, null, null)
        val lunch = CalEvent(3, "Comida", T0 + 3600_000, T0 + 2 * 3600_000, false, null, null)
        assertEquals(listOf(2L, 3L), upcoming(listOf(lunch, past, allDay), T0).map { it.id })
        assertEquals(3L, nextEvent(listOf(allDay, lunch), T0)?.id)
    }

    @Test fun clockFormats() {
        assertEquals("25:00", clock(1500))
        assertEquals("0:05", clock(5))
    }

    @Test fun breathingTravelsToTheWatch() {
        val b = Breathing(1_700_000_000_000, 5)
        assertEquals(b, breathingOf(b.toWire()))
        assertEquals(null, breathingOf("nope"))
        assertEquals(null, breathingOf("1700000000000,0"))
    }

    @Test fun diaryKeepsMoodAndLines() {
        var logs = withMood(emptyList(), "2026-10-13", 4)
        logs = withNote(logs, "2026-10-13", 2000, "segunda")
        logs = withNote(logs, "2026-10-13", 1000, "primera")
        logs = withNote(logs, "2026-10-13", 3000, "   ")
        val day = logs.single()
        assertEquals(4, day.mood)
        assertEquals(listOf("primera", "segunda"), day.notes.map { it.text })
        assertEquals(logs, logsOf(logsJson(logs)))
        // A day with nothing left disappears.
        var gone = withMood(withNote(emptyList(), "2026-10-12", 1, "x"), "2026-10-12", 0)
        gone = withoutNote(gone, "2026-10-12", 1)
        assertEquals(emptyList<DayLog>(), gone)
    }

    @Test fun diaryCountsDaysAndStreaks() {
        val logs = listOf(DayLog("2026-10-01", 3), DayLog("2026-10-12", 4), DayLog("2026-10-13", 5))
        assertEquals(1, dayNumber(logs, "2026-10-01"))
        assertEquals(13, dayNumber(logs, "2026-10-13"))
        val noon13 = java.util.Calendar.getInstance().apply { set(2026, 9, 13, 12, 0) }.timeInMillis
        assertEquals("2026-10-13", dayKey(noon13))
        assertEquals(2, streak(logs, noon13))
    }

    @Test fun googleTasksBecomeRemoteTasks() {
        val a = gTaskOf(JSONObject("""{"id":"abc","title":" Comprar pan ","status":"needsAction","position":"002"}"""))!!
        val b = gTaskOf(JSONObject("""{"id":"def","title":"Llamar","status":"completed","position":"001"}"""))!!
        assertEquals(null, gTaskOf(JSONObject("""{"id":"x","title":"Borrada","deleted":true}""")))
        assertEquals(null, gTaskOf(JSONObject("""{"id":"y","title":"  "}""")))
        val tasks = fromGoogle(listOf(a, b))
        assertEquals(listOf("Llamar", "Comprar pan"), tasks.map { it.text })
        assertEquals(true, tasks.all { it.remote })
        assertEquals(true, tasks[0].done)
    }

    private fun at(day: Int, h: Int, m: Int = 0) = java.util.Calendar.getInstance().apply { set(2026, 9, day, h, m, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis

    @Test fun sleepNightsAndNumbers() {
        val a = sleepEntry(at(12, 23, 30), at(13, 7, 0), quality = 4)
        assertEquals("2026-10-13", a.night)
        assertEquals(450, a.minutes)
        val list = listOf(sleepEntry(at(10, 23, 0), at(11, 6, 0), 2, listOf("cafe")), sleepEntry(at(11, 22, 50), at(12, 6, 10), 2, listOf("cafe")), a)
        assertEquals(list, sleepOf(sleepJson(list)))
        val s = sleepStats(list, SleepGoal(23 * 60, 7 * 60))!!
        assertEquals(3, s.nights)
        assertEquals(true, s.debt > 0)
        assertEquals(true, s.score in 0..100)
        // Short nights: the first tip is about sleeping longer.
        assertEquals("short", personalTips(list, SleepGoal(22 * 60, 7 * 60)).first().icon)
    }

    @Test fun watchNightsKeepYourRating() {
        val mine = sleepEntry(at(12, 23, 0), at(13, 7, 0), quality = 5, note = "bien")
        val watch = sleepEntry(at(12, 23, 20), at(13, 6, 40), source = "health").copy(deepMin = 80)
        val merged = mergeHealth(listOf(mine), listOf(watch)).single()
        assertEquals(5, merged.quality)
        assertEquals("bien", merged.note)
        assertEquals(80, merged.deepMin)
        assertEquals("health", merged.source)
    }

    @Test fun heartRateVariability() {
        assertEquals(0, rmssd(listOf(800, 800)))
        assertEquals(20, rmssd(listOf(800, 820, 800, 820, 800)))
        // Pulse readings only: an estimate, minute by minute.
        val s = BreathSession(1L, 1, 6, 72, 64, 62, "watch", hr = List(30) { if (it < 15) 72 else 64 })
        val st = hrvStats(s)!!
        assertEquals(false, st.beatToBeat)
        assertEquals(62, st.minBpm.coerceAtMost(62))
        assertEquals(1, minuteTable(s).size)
        // Real beats win, and survive the trip to the phone.
        val withBeats = s.copy(rr = List(40) { if (it % 2 == 0) 800 else 840 })
        assertEquals(true, hrvStats(withBeats)!!.beatToBeat)
        assertEquals(withBeats, sessionOf(withBeats.toJson()))
    }

    @Test fun moodsThroughTheDay() {
        var logs = addMood(emptyList(), "2026-10-19", 8 * 3600_000L, 5)
        logs = addMood(logs, "2026-10-19", 18 * 3600_000L, 2)
        val d = logs.single()
        assertEquals(2, d.mood)
        assertEquals(listOf(5, 2), d.moods.map { it.value })
        assertEquals(4, d.averageMood)
        assertEquals(logs, logsOf(logsJson(logs)))
        assertEquals(5, removeMood(logs, "2026-10-19", 18 * 3600_000L).single().mood)
    }

    @Test fun seasonalHats() {
        fun on(m: Int, d: Int) = Outfit.seasonal(java.util.Calendar.getInstance().apply { clear(); set(2026, m - 1, d) })
        assertEquals(Outfit.WITCH, on(10, 27))
        assertEquals(Outfit.FLOWERS, on(11, 2))
        assertEquals(Outfit.SCARF, on(11, 3))
        assertEquals(Outfit.ANTLERS, on(12, 1))
        assertEquals(Outfit.SANTA, on(12, 20))
        assertEquals(Outfit.PARTY, on(1, 1))
        assertEquals(Outfit.CROWN, on(1, 6))
        assertEquals(Outfit.HEARTS, on(2, 14))
        assertEquals(Outfit.SPRING, on(4, 5))
        assertEquals(Outfit.SUNGLASSES, on(7, 4))
        assertEquals(Outfit.TRICOLOR, on(9, 16))
        assertEquals(Outfit.NONE, on(5, 10))
        // The first versions' bunny ears are gone: a stored "BUNNY" means nothing on.
        assertEquals(Outfit.NONE, Outfit.resolve("BUNNY"))
        assertEquals(Outfit.CROWN, Outfit.resolve("CROWN"))
    }
}
