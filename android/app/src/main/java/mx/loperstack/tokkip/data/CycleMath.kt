// The arithmetic behind "Ciclo": periods from the days you marked, the
// average cycle (recent cycles weigh more), and the estimates that follow
// (next period with a range, ovulation, fertile window). When you log signs —
// an ovulation test, cervical mucus, basal temperature — the ovulation of this
// cycle comes from them, and the days from ovulation to period are learned
// from your own cycles. Days are epoch days (LocalDate.toEpochDay) so this
// stays plain and testable. Estimates, never a contraceptive method.

package mx.loperstack.tokkip.data

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One day's notes. [flow] 0 none … 3 heavy; [sex] 0 none, 1 protected,
 * 2 unprotected; tests 0 not taken, 1 negative, 2 positive; [mucus] 0 not
 * noted, 1 dry, 2 sticky, 3 creamy, 4 egg white; [temp] basal temperature
 * in °C (0 = not taken).
 */
data class CycleDay(
    val flow: Int = 0,
    val symptoms: Set<String> = emptySet(),
    val sex: Int = 0,
    val ovulationTest: Int = 0,
    val pregnancyTest: Int = 0,
    val mucus: Int = 0,
    val temp: Double = 0.0,
    val note: String = "",
)

object CycleMath {
    const val DEFAULT_CYCLE = 28
    const val DEFAULT_PERIOD = 5

    val SYMPTOMS = listOf("Cólicos", "Dolor de cabeza", "Hinchazón", "Cansancio", "Sensibilidad", "Ánimo bajo", "Irritabilidad", "Ansiedad", "Antojos", "Acné", "Dolor de espalda", "Náuseas", "Insomnio", "Más energía", "Más deseo")

    /** Days from ovulation to the next period, when your own aren't known yet. */
    const val DEFAULT_LUTEAL = 14

    data class Period(val start: Int, val end: Int) {
        val length get() = end - start + 1
    }

    enum class Mark { NONE, PERIOD, PREDICTED, FERTILE, OVULATION }

    data class Forecast(
        val cycleLen: Int,
        val periodLen: Int,
        /** Cycles measured (between two marked periods) behind the averages; 0 = still the usual 28 days. */
        val measured: Int,
        val irregular: Boolean,
        val lastStart: Int?,
        val nextStart: Int?,
        /** How sure: the next period most likely falls between [nextStart] - spread and + spread. */
        val spread: Int,
        val ovulation: Int?,
        /** This cycle's ovulation comes from your signs (test, mucus, temperature), not only from the calendar. */
        val ovulationFromSigns: Boolean,
        val luteal: Int,
        val fertileFrom: Int?,
        val fertileTo: Int?,
        val day: Int?,
        val late: Int,
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

    /** An average where the latest values count the most (the newest twice as much as the oldest of six, and so on). */
    private fun weighted(values: List<Int>): Double {
        val w = values.indices.map { it + 1.0 }
        return values.indices.sumOf { values[it] * w[it] } / w.sum()
    }

    /**
     * The ovulation your signs show between [from] and [to], if they show one:
     * the day after a positive ovulation test; else the day the basal
     * temperature rose to stay (three days above the six before); else the last
     * day of egg-white mucus.
     */
    fun ovulationFromSigns(notes: Map<Int, CycleDay>, from: Int, to: Int): Int? {
        val inCycle = notes.filterKeys { it in from..to }
        inCycle.filter { it.value.ovulationTest == 2 }.keys.minOrNull()?.let { return it + 1 }
        val temps = inCycle.filter { it.value.temp in 35.0..38.5 }.toSortedMap()
        val days = temps.keys.toList()
        for (i in 6..days.size - 3) {
            val before = days.subList(i - 6, i).maxOf { temps[it]!! }
            if ((0..2).all { temps[days[i + it]]!! >= before + 0.2 }) return days[i] - 1
        }
        inCycle.filter { it.value.mucus == 4 }.keys.maxOrNull()?.let { return it }
        return null
    }

    fun forecast(days: Collection<Int>, today: Int, notes: Map<Int, CycleDay> = emptyMap()): Forecast {
        val ps = periods(days.filter { it <= today })
        val lengths = ps.zipWithNext { a, b -> b.start - a.start }.filter { it in 18..45 }.takeLast(6)
        val cycleLen = if (lengths.isEmpty()) DEFAULT_CYCLE else weighted(lengths).roundToInt()
        val finished = ps.filter { it.end < today - 1 }.takeLast(6).map { it.length }
        val periodLen = if (finished.isEmpty()) DEFAULT_PERIOD else weighted(finished).roundToInt().coerceIn(2, 10)
        val irregular = lengths.size >= 3 && (lengths.max() - lengths.min()) > 7
        // How much your cycles vary: the range around the estimate.
        val spread = if (lengths.size < 2) 3 else {
            val mean = lengths.average()
            ceil(sqrt(lengths.sumOf { (it - mean) * (it - mean) } / lengths.size)).toInt().coerceIn(1, 7)
        }
        // Your own days from ovulation to period, from past cycles whose signs showed the ovulation.
        val lutealSeen = ps.zipWithNext { a, b ->
            ovulationFromSigns(notes, a.start + 6, b.start - 1)?.let { b.start - it }
        }.filterNotNull().filter { it in 9..17 }.takeLast(6)
        val luteal = if (lutealSeen.isEmpty()) DEFAULT_LUTEAL else weighted(lutealSeen).roundToInt()
        val last = ps.lastOrNull()
            ?: return Forecast(cycleLen, periodLen, lengths.size, irregular, null, null, spread, null, false, luteal, null, null, null, 0, false)
        // This cycle: if your signs show the ovulation, the period follows it by your luteal days.
        val signs = ovulationFromSigns(notes, last.start + 6, today)
        val ovulation = signs ?: (last.start + cycleLen - luteal).coerceAtLeast(last.start + 6)
        val next = if (signs != null) signs + luteal else last.start + cycleLen
        return Forecast(
            cycleLen = cycleLen,
            periodLen = periodLen,
            measured = lengths.size,
            irregular = irregular,
            lastStart = last.start,
            nextStart = next,
            spread = if (signs != null) minOf(spread, 2) else spread,
            ovulation = ovulation,
            ovulationFromSigns = signs != null,
            luteal = luteal,
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
            val ov = if (k == 0) f.ovulation ?: (start - f.luteal) else start - f.luteal
            if (day == ov) return Mark.OVULATION
            if (day in (ov - 5)..(ov + 1)) return Mark.FERTILE
        }
        return Mark.NONE
    }

    /**
     * What Tokkip notices in your cycle, most useful first: a test to take,
     * what usually comes before your period, and things worth asking a doctor
     * about. Plain sentences for the Ciclo screen.
     */
    fun insights(days: Set<Int>, notes: Map<Int, CycleDay>, f: Forecast, today: Int): List<String> {
        val out = mutableListOf<String>()
        val last = f.lastStart ?: return out
        val thisCycle = notes.filterKeys { it in last..today }

        // A positive pregnancy test this cycle comes first.
        if (thisCycle.values.any { it.pregnancyTest == 2 }) {
            out += "Registraste una prueba de embarazo positiva. Confírmalo con tu médico; mientras, las fechas de periodo no aplican."
            return out
        }
        val unprotectedFertile = thisCycle.any { (d, n) -> n.sex == 2 && f.fertileFrom != null && d in f.fertileFrom..f.fertileTo!! }
        val testedSinceLate = f.nextStart != null && thisCycle.any { (d, n) -> n.pregnancyTest != 0 && d >= f.nextStart }
        when {
            f.late >= 1 && unprotectedFertile && !testedSinceLate ->
                out += "Tu periodo se retrasa y hubo relaciones sin protección en tus días fértiles: una prueba de embarazo ya es confiable desde hoy."
            f.late >= 5 && !testedSinceLate ->
                out += "Llevas ${f.late} días de retraso. Si hubo relaciones, una prueba de embarazo te saca de dudas; el estrés o los cambios de rutina también lo retrasan."
            unprotectedFertile && f.late == 0 ->
                out += "Hubo relaciones sin protección en tus días fértiles. Si tu periodo no llega el día estimado, una prueba de embarazo es confiable desde ese día."
        }

        // What usually comes before your period: symptoms you noted in the 5 days before it, in most of your cycles.
        val ps = periods(days.filter { it <= today })
        val pastStarts = ps.drop(1).map { it.start }.takeLast(6)
        if (pastStarts.size >= 2 && f.nextStart != null) {
            val usual = SYMPTOMS.filter { s ->
                pastStarts.count { start -> (start - 5 until start).any { s in (notes[it]?.symptoms ?: emptySet()) } } * 2 > pastStarts.size
            }
            if (usual.isNotEmpty()) {
                val list = usual.take(3).joinToString(", ").lowercase()
                val left = f.nextStart - today
                out += if (left in 0..5) "Se acerca tu periodo: antes de él sueles tener $list. Tómatelo con calma estos días."
                else "Antes de tu periodo sueles tener $list."
            }
        }
        if (f.ovulationFromSigns) out += "Tu ovulación de este ciclo viene de tus señales (prueba, moco o temperatura), así que las fechas son más precisas."
        if (f.luteal != DEFAULT_LUTEAL) out += "De tu ovulación a tu periodo pasan unos ${f.luteal} días (lo aprendí de tus ciclos)."

        // Worth mentioning to a doctor, gently.
        val longPeriods = ps.takeLast(3).count { it.length > 8 }
        if (longPeriods >= 2) out += "Tus últimos periodos han durado más de 8 días. Vale la pena comentarlo con tu ginecóloga o ginecólogo."
        if (f.measured >= 3 && (f.cycleLen < 21 || f.cycleLen > 35)) out += "Tus ciclos duran ${f.cycleLen} días en promedio, fuera de lo más común (21 a 35). Coméntalo en tu próxima consulta."
        return out
    }
}
