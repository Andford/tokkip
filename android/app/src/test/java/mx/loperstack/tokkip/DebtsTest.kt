package mx.loperstack.tokkip

import mx.loperstack.tokkip.data.Budget
import mx.loperstack.tokkip.data.DEBT_CATEGORY
import mx.loperstack.tokkip.data.Debt
import mx.loperstack.tokkip.data.MoneyEntry
import mx.loperstack.tokkip.data.MoneyKind
import mx.loperstack.tokkip.data.PayEvery
import mx.loperstack.tokkip.data.debtNotes
import mx.loperstack.tokkip.data.debtsJson
import mx.loperstack.tokkip.data.debtsOf
import mx.loperstack.tokkip.data.dueText
import mx.loperstack.tokkip.data.duesInMonth
import mx.loperstack.tokkip.data.finished
import mx.loperstack.tokkip.data.monthlyLoad
import mx.loperstack.tokkip.data.nextDue
import mx.loperstack.tokkip.data.nextReminder
import mx.loperstack.tokkip.data.pay
import mx.loperstack.tokkip.data.paymentsLeft
import mx.loperstack.tokkip.data.remaining
import mx.loperstack.tokkip.data.unpay
import mx.loperstack.tokkip.data.weekSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DebtsTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        Calendar.getInstance().apply { set(y, m, d, h, min, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    /** Saturday 24 October 2026, noon. */
    private val now = at(2026, Calendar.OCTOBER, 24)
    private val card = Debt("t", "Tarjeta", 1_500_00, PayEvery.QUINCENAL, 15, 0, total = 6_000_00, createdAt = at(2026, Calendar.OCTOBER, 1))
    private val rent = Debt("r", "Renta", 5_000_00, PayEvery.MENSUAL, 5, createdAt = at(2026, Calendar.OCTOBER, 1))

    @Test fun dueDates() {
        assertEquals(listOf("2026-10-15", "2026-10-31"), card.duesInMonth(now))
        assertEquals(listOf("2026-11-15", "2026-11-30"), card.duesInMonth(at(2026, Calendar.NOVEMBER, 3)))
        assertEquals(listOf("2027-02-28"), card.copy(every = PayEvery.MENSUAL, day = 31).duesInMonth(at(2027, Calendar.FEBRUARY, 3)))
    }

    @Test fun nextDueAndLate() {
        // The 15th went unpaid: it's late.
        assertEquals("2026-10-15", card.nextDue(now))
        assertEquals("Hace 9 días", dueText("2026-10-15", now))
        val paid = card.copy(paidDues = setOf("2026-10-15"))
        assertEquals("2026-10-31", paid.nextDue(now))
        assertEquals("2026-11-05", rent.copy(paidDues = setOf("2026-10-05")).nextDue(now))
    }

    @Test fun payingAndProgress() {
        val (paid, entry) = card.pay("2026-10-15", now, "e1")
        assertEquals(1_500_00L, paid.paid)
        assertEquals(4_500_00L, paid.remaining)
        assertEquals(3, paid.paymentsLeft)
        assertEquals(DEBT_CATEGORY, entry.category)
        assertEquals(1_500_00L, entry.cents)
        assertEquals(card, paid.unpay("2026-10-15"))
        var d = card
        for (due in listOf("2026-10-15", "2026-10-31", "2026-11-15", "2026-11-30")) d = d.pay(due, now, due).first
        assertTrue(d.finished)
        assertNull(d.nextDue(now))
        assertNull(rent.paymentsLeft)
    }

    @Test fun reminders() {
        // Rent on Nov 5, reminded a day before at 9:00.
        val r = rent.copy(paidDues = setOf("2026-10-05"))
        assertEquals(at(2026, Calendar.NOVEMBER, 4, 9), r.nextReminder(now))
        // The card's 15th is long late (no more nagging): the 31st's, a day before.
        assertEquals(at(2026, Calendar.OCTOBER, 30, 9), card.nextReminder(now))
        // Late by one day: reminded again the next morning.
        assertEquals(at(2026, Calendar.OCTOBER, 25, 9), rent.copy(day = 23, paidDues = emptySet()).nextReminder(now))
        assertEquals(at(2026, Calendar.OCTOBER, 31, 9), card.copy(paidDues = setOf("2026-10-15"), daysBefore = 0).nextReminder(now))
        assertNull(rent.copy(remind = false).nextReminder(now))
    }

    @Test fun fixedPaymentsStayOutOfTheWeek() {
        val list = listOf(
            MoneyEntry("a", now, 300_00, MoneyKind.GASTO, "comida"),
            MoneyEntry("b", now, 1_500_00, MoneyKind.GASTO, DEBT_CATEGORY),
        )
        assertEquals(300_00L, weekSummary(list, Budget(weekly = 1_000_00), now).spent)
    }

    @Test fun notesAndLoad() {
        assertEquals(1_500_00L * 2 + 5_000_00L, monthlyLoad(listOf(card, rent)))
        val ids = debtNotes(listOf(card, rent.copy(paidDues = setOf("2026-10-05"), day = 26)), 10_000_00, now).map { it.id }
        assertTrue(ids.toString(), "debt-late-t" in ids)
        assertTrue(ids.toString(), "debt-soon-r" in ids)
        assertTrue(ids.toString(), "debt-load" in ids)
    }

    @Test fun json() {
        val list = listOf(card.copy(paidDues = setOf("2026-10-15"), paid = 1_500_00), rent)
        assertEquals(list, debtsOf(debtsJson(list)))
    }
}
