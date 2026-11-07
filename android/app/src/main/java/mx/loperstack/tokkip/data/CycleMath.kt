// The arithmetic behind "Ciclo": periods from the days you marked, the average
// cycle, and the estimates that follow (next period, ovulation, fertile
// window). Days are epoch days (LocalDate.toEpochDay) so this stays plain and
// testable. These are estimates from your own history, never a method to
// avoid or seek pregnancy on its own.

package mx.loperstack.tokkip.data

import kotlin.math.roundToInt

object CycleMath {
    const val DEFAULT_CYCLE = 28
    const val DEFAULT_PERIOD = 5

    /** The luteal phase: ovulation is usually about 14 days before the next period. */
    private const val LUTEAL = 14

    /** A period: its first and last day. */
    data class Period(val start: Int, val end: Int) {
        val length get() = end - start + 1
    }

    /** What the calendar paints on a day. */
    enum class Mark { NONE, PERIOD, PREDICTED, FERTILE, OVULATION }

    data class Forecast(
        val cycleLen: Int,
        val periodLen: Int,
        /** Cycles measured (between two marked periods) behind the averages; 0 = still the usual 28 days. */
        val measured: Int,
        val irregular: Boolean,
        val lastStart: Int?,
        val nextStart: Int?,
        val ovulation: Int?,
        val fertileFrom: Int?,
        val fertileTo: Int?,
        /** Day of the current cycle (day 1 = first day of the last period). */
        val day: Int?,
        /** Days the period is late (0 if not). */
        val late: Int,
        /** Today is a marked period day. */
        val inPeriod: Boolean,
    )

    /** Marked days → periods. Days a single gap apart still count as one period (a day you forgot to mark). */
    fun periods(days: Collection<Int>): List<Period> {
        val sorted = days.toSortedSet()
        val out = mutableListOf<Period>()
        var start = -1
        var end = -1
        for (d in sorted) {
            if (start < 0) { start = d; end = d; continue }
            if (d - end <= 2) end = d
            else { out += Period(start, end); start = d; end = d }
        }
        if (start >= 0) out += Period(start, end)
        return out
    }

    fun forecast(days: Collection<Int>, today: Int): Forecast {
        val ps = periods(days.filter { it <= today })
        // Cycle lengths between consecutive starts; very short or long ones (a missed month) don't count.
        val lengths = ps.zipWithNext { a, b -> b.start - a.start }.filter { it in 18..45 }.takeLast(6)
        val cycleLen = if (lengths.isEmpty()) DEFAULT_CYCLE else lengths.average().roundToInt()
        // Period length from finished periods (the current one may still be going).
        val finished = ps.filter { it.end < today - 1 }.takeLast(6)
        val periodLen = if (finished.isEmpty()) DEFAULT_PERIOD else finished.map { it.length }.average().roundToInt().coerceIn(2, 10)
        val irregular = lengths.size >= 3 && (lengths.max() - lengths.min()) > 7
        val last = ps.lastOrNull()
        if (last == null) {
            return Forecast(cycleLen, periodLen, lengths.size, irregular, null, null, null, null, null, null, 0, false)
        }
        val next = last.start + cycleLen
        val ovulation = (next - LUTEAL).coerceAtLeast(last.start + 1)
        return Forecast(
            cycleLen = cycleLen,
            periodLen = periodLen,
            measured = lengths.size,
            irregular = irregular,
            lastStart = last.start,
            nextStart = next,
            ovulation = ovulation,
            fertileFrom = ovulation - 5,
            fertileTo = ovulation + 1,
            day = today - last.start + 1,
            late = (today - next).coerceAtLeast(0),
            inPeriod = today in days,
        )
    }

    /**
     * What to paint on [day]: marked days first, then the estimates for this
     * cycle and the next two (the period to come, ovulation and the fertile
     * window before each). A late period moves the estimate to today, so the
     * past never fills with guesses.
     */
    fun mark(day: Int, days: Set<Int>, f: Forecast, today: Int): Mark {
        if (day in days) return Mark.PERIOD
        val next = f.nextStart ?: return Mark.NONE
        val lastStart = f.lastStart ?: return Mark.NONE
        if (day <= lastStart) return Mark.NONE
        val first = if (f.late > 0) today else next
        for (k in 0..2) {
            val start = first + k * f.cycleLen
            if (day >= today && day in start until start + f.periodLen) return Mark.PREDICTED
            // The ovulation before this period: this cycle's own estimate, then 14 days before each next one.
            val ov = if (k == 0) f.ovulation ?: (start - LUTEAL) else start - LUTEAL
            if (day == ov) return Mark.OVULATION
            if (day in (ov - 5)..(ov + 1)) return Mark.FERTILE
        }
        return Mark.NONE
    }
}
