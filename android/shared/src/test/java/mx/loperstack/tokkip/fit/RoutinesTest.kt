package mx.loperstack.tokkip.fit

import mx.loperstack.tokkip.character.Exercise
import mx.loperstack.tokkip.character.Look
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinesTest {
    private val r = Routine("t", "T", "", 1, "hiit", listOf(Step(Exercise.SQUAT, 30), Step(Exercise.REST, 10), Step(Exercise.JUMP, 20)))

    @Test fun whereYouAre() {
        assertEquals(Position(0, 30, 0f, 0f, false), r.at(0))
        val p = r.at(35_000)
        assertEquals(1, p.index)
        assertEquals(5, p.left)
        assertEquals(2, r.at(45_500).index)
        assertEquals(15, r.at(45_500).left)
        assertTrue(r.at(60_000).done)
        assertTrue(r.at(99_000).done)
    }

    @Test fun burnsMoreWorkingThanResting() {
        val squat = Routine("a", "", "", 1, "", listOf(Step(Exercise.SQUAT, 60))).kcal(60_000)
        val rest = Routine("b", "", "", 1, "", listOf(Step(Exercise.REST, 60))).kcal(60_000)
        assertEquals(4.9, squat, 0.01) // (5 − 1) × 3.5 × 70 / 200
        assertTrue(rest < squat)
        assertEquals(0.0, r.kcal(0), 0.0)
        assertTrue(r.kcal(30_000) < r.kcal(60_000))
    }

    @Test fun everyRoutineIsSensible() {
        assertEquals(ROUTINES.size, ROUTINES.map { it.id }.toSet().size)
        for (x in ROUTINES) {
            assertTrue(x.name, x.seconds in 120..1_200)
            assertTrue(x.name, x.steps.first().move != Exercise.REST)
            assertTrue(x.name, x.steps.all { it.tip.isNotBlank() })
        }
    }

    @Test fun workoutsRoundTrip() {
        val d = listOf(Done(1L, "hiit", "HIIT exprés", 600, 80.5, 132, "hiit", "watch"), Done(2L, "estira", "Estira", 300, 10.0))
        assertEquals(d, Done.list(Done.json(d)))
        assertEquals(emptyList<Done>(), Done.list("nope"))
    }

    @Test fun lookRoundTrip() {
        val l = Look(color = 0xFF2DD4BF, species = "GATO", skin = "ENSUENO", accent = 0xFF112233, ink = 0, cheek = 0xFFF0788C, outfit = "CROWN")
        assertEquals(l, Look.parse(l.json()))
        assertEquals(null, Look.parse("{"))
    }
}
