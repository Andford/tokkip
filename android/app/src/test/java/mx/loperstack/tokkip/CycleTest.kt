package mx.loperstack.tokkip

import mx.loperstack.tokkip.data.CycleMath
import mx.loperstack.tokkip.data.MorningBrief
import mx.loperstack.tokkip.data.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleTest {
    /** Five-day periods starting on these days. */
    private fun periodsFrom(vararg starts: Int) = starts.flatMap { s -> (s until s + 5) }.toSet()

    @Test
    fun periodsJoinAForgottenDay() {
        val ps = CycleMath.periods(setOf(10, 11, 13, 14, 40, 41))
        assertEquals(listOf(CycleMath.Period(10, 14), CycleMath.Period(40, 41)), ps)
    }

    @Test
    fun forecastFromOwnCycles() {
        // Cycles of 30 and 30 days: the next period 30 days after the last start.
        val days = periodsFrom(1000, 1030, 1060)
        val f = CycleMath.forecast(days, today = 1070)
        assertEquals(30, f.cycleLen)
        assertEquals(5, f.periodLen)
        assertEquals(2, f.measured)
        assertEquals(1090, f.nextStart)
        assertEquals(1076, f.ovulation)
        assertEquals(1071, f.fertileFrom)
        assertEquals(1077, f.fertileTo)
        assertEquals(11, f.day)
        assertEquals(0, f.late)
        assertFalse(f.irregular)
    }

    @Test
    fun usualCycleUntilThereAreTwo() {
        val f = CycleMath.forecast(periodsFrom(2000), today = 2003)
        assertEquals(28, f.cycleLen)
        assertEquals(0, f.measured)
        assertTrue(f.inPeriod)
        assertEquals(2028, f.nextStart)
    }

    @Test
    fun lateAndMarks() {
        val days = periodsFrom(3000, 3028)
        val f = CycleMath.forecast(days, today = 3060)
        assertEquals(4, f.late)
        assertEquals(CycleMath.Mark.PERIOD, CycleMath.mark(3029, days, f, 3060))
        // A late period: the estimate starts today, never in the past.
        assertEquals(CycleMath.Mark.PREDICTED, CycleMath.mark(3060, days, f, 3060))
        assertEquals(CycleMath.Mark.NONE, CycleMath.mark(3057, days, f, 3060))
        val g = CycleMath.forecast(days, today = 3035)
        assertEquals(CycleMath.Mark.OVULATION, CycleMath.mark(g.ovulation!!, days, g, 3035))
        assertEquals(CycleMath.Mark.FERTILE, CycleMath.mark(g.ovulation!! - 3, days, g, 3035))
        assertEquals(CycleMath.Mark.PREDICTED, CycleMath.mark(g.nextStart!!, days, g, 3035))
    }

    @Test
    fun outlierCyclesDontCount() {
        // A missed month (60 days) is left out of the average.
        val f = CycleMath.forecast(periodsFrom(4000, 4028, 4088, 4116), today = 4120)
        assertEquals(28, f.cycleLen)
        assertEquals(2, f.measured)
    }

    @Test
    fun morningSpeech() {
        val w = Weather.Now(temp = 18, code = 0, day = true, max = 28, min = 12, rain = 50)
        val s = MorningBrief.speech("Andy", w, listOf(MorningBrief.Item("Junta", "10:00"), MorningBrief.Item("Cumple de Ana", null)), listOf("Comprar café", "Pagar la luz"))
        assertEquals(
            "¡Buenos días, Andy! Afuera hay 18 grados y está despejado; la máxima será de 28 y la mínima de 12. " +
                "Hay 50 por ciento de probabilidad de lluvia, así que lleva paraguas. " +
                "Hoy tienes 2 eventos: Junta a las 10:00, Cumple de Ana, todo el día. " +
                "Y 2 pendientes, empezando por Comprar café. Que tengas un gran día.",
            s,
        )
        assertEquals(
            "¡Buenos días! Hoy no tienes eventos en tu agenda. Y no tienes pendientes. ¡Qué buen día! Que tengas un gran día.",
            MorningBrief.speech("", null, emptyList(), emptyList()),
        )
    }
}
