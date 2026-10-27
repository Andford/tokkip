package mx.loperstack.tokkip

import mx.loperstack.tokkip.data.Budget
import mx.loperstack.tokkip.data.MoneyEntry
import mx.loperstack.tokkip.data.MoneyKind
import mx.loperstack.tokkip.data.WeekendFrom
import mx.loperstack.tokkip.data.budgetJson
import mx.loperstack.tokkip.data.budgetOf
import mx.loperstack.tokkip.data.dayKey
import mx.loperstack.tokkip.data.isWeekend
import mx.loperstack.tokkip.data.money
import mx.loperstack.tokkip.data.moneyJson
import mx.loperstack.tokkip.data.moneyNotes
import mx.loperstack.tokkip.data.moneyOf
import mx.loperstack.tokkip.data.monthSummary
import mx.loperstack.tokkip.data.parseMoney
import mx.loperstack.tokkip.data.weekStartOf
import mx.loperstack.tokkip.data.weekSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MoneyTest {
    /** Wednesday 21 October 2026, 13:00. */
    private val wed = at(2026, Calendar.OCTOBER, 21, 13)
    private fun at(y: Int, m: Int, d: Int, h: Int) = Calendar.getInstance().apply { set(y, m, d, h, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private var n = 0
    private fun spend(ms: Long, pesos: Int, cat: String = "comida") = MoneyEntry("e${n++}", ms, pesos * 100L, MoneyKind.GASTO, cat)
    private fun earn(ms: Long, pesos: Int) = MoneyEntry("i${n++}", ms, pesos * 100L, MoneyKind.INGRESO, "sueldo")

    @Test fun amounts() {
        assertEquals("$1,250", money(125_000))
        assertEquals("$89.50", money(8_950))
        assertEquals("−$20", money(-2_000))
        assertEquals(125_050L, parseMoney("1,250.50"))
        assertEquals(8_950L, parseMoney("89,50"))
        assertEquals(125_000L, parseMoney("$1,250"))
        assertNull(parseMoney("abc"))
        assertNull(parseMoney("0"))
    }

    @Test fun weeksAndWeekends() {
        val b = Budget(weekStart = Calendar.MONDAY)
        assertEquals(at(2026, Calendar.OCTOBER, 19, 0), weekStartOf(wed, b))
        assertEquals(at(2026, Calendar.OCTOBER, 18, 0), weekStartOf(wed, b.copy(weekStart = Calendar.SUNDAY)))
        val friEvening = at(2026, Calendar.OCTOBER, 23, 19)
        assertFalse(isWeekend(friEvening, b))
        assertTrue(isWeekend(friEvening, b.copy(weekendFrom = WeekendFrom.VIERNES_TARDE)))
        assertFalse(isWeekend(at(2026, Calendar.OCTOBER, 23, 10), b.copy(weekendFrom = WeekendFrom.VIERNES_TARDE)))
        assertTrue(isWeekend(at(2026, Calendar.OCTOBER, 24, 10), b))
    }

    @Test fun theWeekSoFar() {
        val b = Budget(weekly = 2_000_00, weekend = 800_00)
        val list = listOf(
            spend(at(2026, Calendar.OCTOBER, 19, 9), 300),
            spend(at(2026, Calendar.OCTOBER, 20, 9), 200, "super"),
            spend(at(2026, Calendar.OCTOBER, 18, 9), 999), // last week's Sunday
            earn(at(2026, Calendar.OCTOBER, 20, 9), 5000),
        )
        val w = weekSummary(list, b, wed)
        assertEquals(500_00L, w.spent)
        assertEquals(500_00L, w.weekdaySpent)
        assertEquals(0L, w.weekendSpent)
        assertEquals(1_500_00L, w.left)
        // Weekdays: 1,200 − 500 = 700 over Wed, Thu, Fri.
        assertEquals(700_00L, w.weekdayLeft)
        assertEquals(233_33L, w.perDay)
        assertEquals(5_000_00L, w.income)
        assertEquals(mapOf("comida" to 300_00L, "super" to 200_00L), w.byCategory)
    }

    @Test fun theMonth() {
        val list = listOf(spend(at(2026, Calendar.OCTOBER, 2, 9), 100), spend(at(2026, Calendar.OCTOBER, 2, 19), 50, "cafe"), earn(at(2026, Calendar.OCTOBER, 1, 9), 1000))
        val m = monthSummary(list, wed)
        assertEquals(150_00L, m.spent)
        assertEquals(850_00L, m.balance)
        assertEquals(0.85f, m.saved!!, 0.001f)
        assertEquals(150_00L, m.daily[1])
        assertEquals("comida", m.byCategory.first().first)
    }

    @Test fun json() {
        val list = listOf(spend(wed, 120, "cafe").copy(note = "latte"), earn(wed, 900))
        assertEquals(list, moneyOf(moneyJson(list)))
        val b = Budget(1500_00, 600_00, Calendar.SUNDAY, WeekendFrom.VIERNES_TARDE, mapOf("cafe" to 200_00), 20)
        assertEquals(b, budgetOf(budgetJson(b)))
        assertEquals(Budget(), budgetOf("roto"))
    }

    @Test fun tokkipSeesAFastWeek() {
        val b = Budget(weekly = 1_000_00)
        val list = listOf(spend(at(2026, Calendar.OCTOBER, 19, 10), 600), spend(at(2026, Calendar.OCTOBER, 20, 10), 200))
        val ids = moneyNotes(list, b, emptyMap(), wed).map { it.id }
        assertTrue(ids.toString(), "money-fast" in ids)
    }

    @Test fun tokkipSeesLowDaysSpending() {
        val moods = HashMap<String, Int>()
        val list = ArrayList<MoneyEntry>()
        for (d in 1..12) {
            val ms = at(2026, Calendar.OCTOBER, d, 12)
            val low = d % 2 == 0
            moods[dayKey(ms)] = if (low) 2 else 4
            list += spend(ms, if (low) 400 else 150, "compras")
        }
        val notes = moneyNotes(list, Budget(), moods, wed)
        assertTrue(notes.joinToString { it.id }, notes.any { it.id == "money-mood" })
    }

    @Test fun tokkipSeesTheLittleThings() {
        val list = (1..14).map { spend(at(2026, Calendar.OCTOBER, it, 8), 45, "cafe") }
        val ids = moneyNotes(list, Budget(), emptyMap(), wed).map { it.id }
        assertTrue(ids.toString(), "money-small" in ids)
    }
}
