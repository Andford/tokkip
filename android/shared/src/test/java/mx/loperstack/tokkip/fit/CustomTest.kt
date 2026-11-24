package mx.loperstack.tokkip.fit

import mx.loperstack.tokkip.character.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomTest {
    @Test fun routineExpandsRoundsWithoutATrailingRest() {
        val mine = MyRoutine("mia_1", "Piernas", listOf(Move(Exercise.SQUAT, 40), Move(Exercise.PLANK, 30)), rest = 15, rounds = 3)
        val r = mine.routine()
        // 3 rounds × 2 moves, a rest after each one but the very last.
        assertEquals(11, r.steps.size)
        assertTrue(!r.steps.last().rest)
        assertEquals(3 * (40 + 30) + 5 * 15, r.seconds)
        assertEquals("strength", r.kind)
        assertEquals("mia_1", r.id)
    }

    @Test fun kindAndLevel() {
        assertEquals("stretch", MyRoutine("a", "", listOf(Move(Exercise.STRETCH, 60)), rounds = 3).routine().kind)
        assertEquals(1, MyRoutine("a", "", listOf(Move(Exercise.STRETCH, 60)), rounds = 3).routine().level)
        val hard = MyRoutine("b", "", listOf(Move(Exercise.JUMP, 60), Move(Exercise.PUNCH, 60)), rest = 10, rounds = 5).routine()
        assertEquals("hiit", hard.kind)
        assertEquals(3, hard.level)
    }

    @Test fun cleanKeepsItSane() {
        val c = MyRoutine("x", "   ", listOf(Move(Exercise.REST, 30), Move(Exercise.JUMP, 2)), rest = 999, rounds = 0).clean()
        assertEquals("Mi rutina", c.name)
        assertEquals(listOf(Move(Exercise.JUMP, 10)), c.moves)
        assertEquals(120, c.rest)
        assertEquals(1, c.rounds)
        // Nothing to play still plays something.
        assertTrue(MyRoutine("y", "", emptyList()).routine().steps.isNotEmpty())
    }

    @Test fun roundTrips() {
        val list = listOf(MyRoutine.new(5).copy(name = "Mañana"), MyRoutine("mia_9", "Box", listOf(Move(Exercise.PUNCH, 45)), 20, 4))
        assertEquals(list.map { it.clean() }, MyRoutine.list(MyRoutine.json(list)))
        assertEquals(emptyList<MyRoutine>(), MyRoutine.list("no es json"))

        val plan = MyPlans.new(7).copy(name = "Brazo", lifts = listOf("curl", "triceps", "nada", "curl"), sets = 4, reps = 12, restSeconds = 60).clean()
        assertEquals(listOf("curl", "triceps"), plan.lifts)
        assertEquals("Bíceps, Tríceps", plan.about)
        val back = MyPlans.list(MyPlans.json(listOf(plan)))
        assertEquals(listOf(plan), back)
        assertNotNull(MyPlans.of(plan.toJson()))
    }
}
