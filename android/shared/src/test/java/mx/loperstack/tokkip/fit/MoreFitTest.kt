package mx.loperstack.tokkip.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class GymTest {
    @Test fun oneRepMaxAndRecords() {
        assertEquals(100.0 * (1 + 5 / 30.0), GymSet("press_banca", 100.0, 5).oneRm, 1e-9)
        assertEquals(12.0, GymSet("dominadas", 0.0, 12).oneRm, 0.0)
        val history = listOf(GymSet("press_banca", 60.0, 10), GymSet("press_banca", 70.0, 5), GymSet("sentadilla", 80.0, 8))
        val r = records(history)
        assertEquals(70.0, r["press_banca"]!!.heaviest.kg, 0.0)
        assertEquals(81.67, r["press_banca"]!!.best.oneRm, 0.01) // 70 × (1 + 5/30)
        assertTrue(isRecord(GymSet("press_banca", 72.5, 5), history))
        assertFalse(isRecord(GymSet("press_banca", 60.0, 8), history))
        assertFalse("the first time isn't a record", isRecord(GymSet("curl", 10.0, 10), history))
        assertEquals(70.0, lastWeight("press_banca", history), 0.0)
        assertEquals(0.0, lastWeight("curl", history), 0.0)
    }

    @Test fun plansUseKnownLifts() {
        for (p in GYM_PLANS) for (id in p.lifts) assertTrue("$id in ${p.id}", lift(id) != null)
        assertEquals(LIFTS.size, LIFTS.map { it.id }.toSet().size)
    }

    @Test fun setsRoundTripAndKg() {
        val s = listOf(GymSet("curl", 12.5, 10, 5L), GymSet("plancha", 0.0, 1, 6L))
        assertEquals(s, GymSet.list(GymSet.json(s)))
        assertEquals("62.5", kg(62.5))
        assertEquals("60", kg(60.0))
    }
}

class CardioTest {
    private val start = 1_000_000L

    @Test fun distanceAndJumps() {
        val a = Point(25.5428, -103.4068, start)
        val b = Point(25.5518, -103.4068, start + 300_000) // ~1 km north, 5 min
        assertEquals(1000.8, meters(a, b), 2.0)
        val t = Track(Sport.RUN)
        assertTrue(t.add(a, start))
        assertFalse("a 1 km jump in a second is the GPS", t.add(Point(25.5518, -103.4068, start + 1_000), start))
        assertFalse("too inaccurate", t.add(Point(25.5430, -103.4068, start + 20_000, accuracy = 80f), start))
        assertTrue(t.add(b, start))
        assertEquals(1, t.splits.size)
        assertEquals(300_000L, t.splits[0])
    }

    @Test fun formats() {
        assertEquals("5'00\"", pace(300.0))
        assertEquals("--", pace(0.0))
        assertEquals("25:09", clock(1509))
        assertEquals("2:05:09", clock(7509))
        assertEquals("12.0 km/h", speed(1000.0, 300.0))
    }

    @Test fun runningBurnsMoreThanWalking() {
        assertTrue(cardioKcal(Sport.RUN, 5000.0, 1800.0) > cardioKcal(Sport.WALK, 3000.0, 1800.0))
        assertEquals(0.0, cardioKcal(Sport.BIKE, 0.0, 0.0), 0.0)
    }
}

class AchievementsTest {
    private val zone = ZoneId.of("America/Monterrey")

    @Test fun badgesFromWhatYouDid() {
        val done = listOf(Done(1L, "hiit", "HIIT", 600, 80.0), Done(2L, "run", "Correr", 1800, 300.0, kind = "run", from = "watch", meters = 5200.0))
        val b = badges(Effort(done, steps = listOf(4_000, 11_000), streak = 2)).associateBy { it.id }
        assertTrue(b["primero"]!!.earned)
        assertTrue(b["cinco_k"]!!.earned)
        assertTrue(b["reloj"]!!.earned)
        assertTrue(b["diezmil"]!!.earned)
        assertFalse(b["diez"]!!.earned)
        assertEquals(0.2f, b["diez"]!!.progress, 0.001f)
        assertEquals(0.5f, b["todoterreno"]!!.progress, 0.001f)
    }

    @Test fun weeklyChallengeIsStableAllWeek() {
        val mon = LocalDate.of(2026, 11, 23)
        val e = Effort(emptyList())
        assertEquals(weekChallenge(e, mon, zone).id, weekChallenge(e, mon.plusDays(6), zone).id)
        assertEquals(0f, weekChallenge(e, mon, zone).progress, 0f)
    }
}
