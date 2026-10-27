// Dinero: what you spend and what comes in, with a weekly budget and a
// weekend one inside it, both yours to set. Amounts are kept in cents.
// The week starts the day you choose; the weekend can start on Saturday,
// Friday afternoon or Friday. Optional weekly limits per category.
//
// Tokkip reads it too (moneyNotes): how fast the week is going, weekends that
// run over, categories that grew, the little purchases that add up, your
// savings, and whether you spend more on your low days. Only on the phone.
// Pure Kotlin + org.json: tested in app/src/test.

package mx.loperstack.tokkip.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class MoneyKind { GASTO, INGRESO }

data class MoneyCategory(val id: String, val label: String, val icon: String, val color: Long, val kind: MoneyKind = MoneyKind.GASTO)

val EXPENSE_CATEGORIES = listOf(
    MoneyCategory("comida", "Comida fuera", "food", 0xFFF97316),
    MoneyCategory("super", "Súper", "cart", 0xFF34D399),
    MoneyCategory("cafe", "Café y antojos", "coffee", 0xFFC08457),
    MoneyCategory("transporte", "Transporte", "car", 0xFF60A5FA),
    MoneyCategory("casa", "Casa y servicios", "home", 0xFF94A3B8),
    MoneyCategory("ocio", "Salidas y ocio", "party", 0xFFF472B6),
    MoneyCategory("compras", "Compras", "bag", 0xFFA78BFA),
    MoneyCategory("suscripciones", "Suscripciones", "sub", 0xFF22D3EE),
    MoneyCategory("salud", "Salud", "health", 0xFFF87171),
    MoneyCategory("educacion", "Educación", "school", 0xFFFBBF24),
    MoneyCategory("regalos", "Regalos", "gift", 0xFFFB7185),
    MoneyCategory(DEBT_CATEGORY, "Deudas y pagos fijos", "card", 0xFF64748B),
    MoneyCategory("otros", "Otros", "dots", 0xFFA5A9B1),
)

val INCOME_CATEGORIES = listOf(
    MoneyCategory("sueldo", "Sueldo", "work", 0xFF4ADE80, MoneyKind.INGRESO),
    MoneyCategory("clientes", "Clientes y ventas", "store", 0xFF2DD4BF, MoneyKind.INGRESO),
    MoneyCategory("extra", "Extra", "spark", 0xFFFBBF24, MoneyKind.INGRESO),
    MoneyCategory("regalo_in", "Regalo", "gift", 0xFFF472B6, MoneyKind.INGRESO),
    MoneyCategory("otros_in", "Otros", "dots", 0xFFA5A9B1, MoneyKind.INGRESO),
)

fun categoryOf(id: String): MoneyCategory =
    (EXPENSE_CATEGORIES + INCOME_CATEGORIES).firstOrNull { it.id == id } ?: EXPENSE_CATEGORIES.last()

data class MoneyEntry(
    val id: String,
    val at: Long,
    /** Always positive; the kind says which way it goes. */
    val cents: Long,
    val kind: MoneyKind = MoneyKind.GASTO,
    val category: String = "otros",
    val note: String = "",
)

/** When the weekend starts. */
enum class WeekendFrom(val label: String) { SABADO("Sábado"), VIERNES_TARDE("Viernes 6 pm"), VIERNES("Viernes") }

data class Budget(
    /** The whole week, weekend included; 0 = not set. */
    val weekly: Long = 0,
    /** The part of the week kept for the weekend; 0 = not set. */
    val weekend: Long = 0,
    /** Calendar.MONDAY or Calendar.SUNDAY. */
    val weekStart: Int = Calendar.MONDAY,
    val weekendFrom: WeekendFrom = WeekendFrom.SABADO,
    /** Weekly limits per expense category, in cents. */
    val limits: Map<String, Long> = emptyMap(),
    /** How much of what comes in each month you'd like to keep, 0…90 %. */
    val saveGoal: Int = 0,
)

const val MONEY_LIMIT = 6000
const val MONEY_NOTE_LEN = 80

// ── Money as text ─────────────────────────────────────────────────────────────

private val MXN = Locale("es", "MX")

/** "$1,250" or "$89.50". */
fun money(cents: Long, sign: Boolean = false): String {
    val f = NumberFormat.getNumberInstance(MXN).apply {
        minimumFractionDigits = if (abs(cents) % 100 == 0L) 0 else 2
        maximumFractionDigits = 2
    }
    val s = "$" + f.format(abs(cents) / 100.0)
    return when {
        cents < 0 -> "−$s"
        sign && cents > 0 -> "+$s"
        else -> s
    }
}

/** "1,250.5" or "89,50" → cents; null if it isn't an amount. */
fun parseMoney(text: String): Long? {
    var t = text.trim().replace("$", "").replace(" ", "")
    if (t.isEmpty()) return null
    // A comma followed by 1–2 digits at the end is a decimal comma; otherwise it groups thousands.
    t = if (Regex(""",\d{1,2}$""").containsMatchIn(t) && !t.contains('.')) t.replace(",", ".") else t.replace(",", "")
    val v = t.toDoubleOrNull() ?: return null
    if (v <= 0 || v > 100_000_000) return null
    return (v * 100).roundToLong()
}

// ── Weeks ─────────────────────────────────────────────────────────────────────

private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

/** The start (local midnight) of the week holding `ms`. */
fun weekStartOf(ms: Long, b: Budget): Long {
    val c = cal(ms).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    while (c.get(Calendar.DAY_OF_WEEK) != b.weekStart) c.add(Calendar.DAY_OF_YEAR, -1)
    return c.timeInMillis
}

fun weekEndOf(ms: Long, b: Budget): Long = cal(weekStartOf(ms, b)).apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis

/** True when `ms` falls in the weekend, as you set it. */
fun isWeekend(ms: Long, b: Budget): Boolean {
    val c = cal(ms)
    return when (c.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY, Calendar.SUNDAY -> true
        Calendar.FRIDAY -> when (b.weekendFrom) {
            WeekendFrom.VIERNES -> true
            WeekendFrom.VIERNES_TARDE -> c.get(Calendar.HOUR_OF_DAY) >= 18
            WeekendFrom.SABADO -> false
        }
        else -> false
    }
}

/** The days left in the week, today included, split into weekdays and weekend days. */
private fun daysLeft(now: Long, b: Budget): Pair<Int, Int> {
    val end = weekEndOf(now, b)
    val c = cal(now)
    var weekdays = 0; var weekend = 0
    while (c.timeInMillis < end) {
        val noon = cal(c.timeInMillis).apply { set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis
        val evening = cal(c.timeInMillis).apply { set(Calendar.HOUR_OF_DAY, 20) }.timeInMillis
        if (isWeekend(noon, b) || isWeekend(evening, b)) weekend++ else weekdays++
        c.add(Calendar.DAY_OF_YEAR, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
    }
    return weekdays to weekend
}

data class WeekSummary(
    val start: Long,
    val end: Long,
    val spent: Long,
    val weekdaySpent: Long,
    val weekendSpent: Long,
    val income: Long,
    val budget: Budget,
    /** What's left of the week's budget (negative when over). */
    val left: Long,
    /** What's left for weekdays and for the weekend. */
    val weekdayLeft: Long,
    val weekendLeft: Long,
    /** About how much you can spend per day from now on, keeping the weekend's part. */
    val perDay: Long,
    /** How far through the week we are, 0…1. */
    val elapsed: Float,
    val byCategory: Map<String, Long>,
    val daysWithout: Int,
) {
    val hasBudget get() = budget.weekly > 0
    val used: Float get() = if (budget.weekly <= 0) 0f else spent.toFloat() / budget.weekly
    val weekdayBudget get() = (budget.weekly - budget.weekend).coerceAtLeast(0)
    val weekendUsed: Float get() = if (budget.weekend <= 0) 0f else weekendSpent.toFloat() / budget.weekend
    val weekdayUsed: Float get() = if (weekdayBudget <= 0) 0f else weekdaySpent.toFloat() / weekdayBudget
}

fun weekSummary(list: List<MoneyEntry>, b: Budget, now: Long): WeekSummary {
    val start = weekStartOf(now, b)
    val end = weekEndOf(now, b)
    val week = list.filter { it.at in start until end }
    // Fixed payments (Pagos y deudas) count in the month, not in the week's budget.
    val out = week.filter { it.kind == MoneyKind.GASTO && it.category != DEBT_CATEGORY }
    val spent = out.sumOf { it.cents }
    val weekendSpent = out.filter { isWeekend(it.at, b) }.sumOf { it.cents }
    val weekdaySpent = spent - weekendSpent
    val weekdayBudget = (b.weekly - b.weekend).coerceAtLeast(0)
    val weekdayLeft = weekdayBudget - weekdaySpent
    val weekendLeft = b.weekend - weekendSpent
    val (wd, we) = daysLeft(now, b)
    val inWeekend = isWeekend(now, b)
    // From now on: weekdays share the weekday part, weekend days the weekend part.
    val perDay = when {
        b.weekly <= 0 -> 0L
        inWeekend || wd == 0 -> if (b.weekend > 0) (weekendLeft / maxOf(we, 1)) else ((b.weekly - spent) / maxOf(we, 1))
        b.weekend > 0 -> weekdayLeft / maxOf(wd, 1)
        else -> (b.weekly - spent) / maxOf(wd + we, 1)
    }.coerceAtLeast(0)
    val spentDays = out.map { dayKey(it.at) }.toSet()
    val today = dayKey(now)
    var without = 0
    val c = cal(start)
    while (dayKey(c.timeInMillis) <= today && c.timeInMillis < end) {
        val k = dayKey(c.timeInMillis)
        if (k !in spentDays && (k < today)) without++
        c.add(Calendar.DAY_OF_YEAR, 1)
    }
    return WeekSummary(
        start, end, spent, weekdaySpent, weekendSpent, week.filter { it.kind == MoneyKind.INGRESO }.sumOf { it.cents }, b,
        b.weekly - spent, weekdayLeft, weekendLeft, perDay,
        ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f),
        out.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.cents } },
        without,
    )
}

data class MonthSummary(
    val start: Long,
    val income: Long,
    val spent: Long,
    val byCategory: List<Pair<String, Long>>,
    val incomeBy: List<Pair<String, Long>>,
    /** Spent per day of the month (index 0 = day 1), for the chart. */
    val daily: List<Long>,
) {
    val balance get() = income - spent
    /** Share of the income kept, or null with no income. */
    val saved: Float? get() = if (income <= 0) null else (balance.toFloat() / income)
}

fun monthStartOf(ms: Long): Long = cal(ms).apply {
    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun monthSummary(list: List<MoneyEntry>, anyDayIn: Long): MonthSummary {
    val start = monthStartOf(anyDayIn)
    val end = cal(start).apply { add(Calendar.MONTH, 1) }.timeInMillis
    val month = list.filter { it.at in start until end }
    val out = month.filter { it.kind == MoneyKind.GASTO }
    val inc = month.filter { it.kind == MoneyKind.INGRESO }
    val days = cal(start).getActualMaximum(Calendar.DAY_OF_MONTH)
    val daily = LongArray(days)
    for (e in out) daily[(cal(e.at).get(Calendar.DAY_OF_MONTH) - 1).coerceIn(0, days - 1)] += e.cents
    return MonthSummary(
        start, inc.sumOf { it.cents }, out.sumOf { it.cents },
        out.groupBy { it.category }.map { (k, v) -> k to v.sumOf { it.cents } }.sortedByDescending { it.second },
        inc.groupBy { it.category }.map { (k, v) -> k to v.sumOf { it.cents } }.sortedByDescending { it.second },
        daily.toList(),
    )
}

/** The last `n` weeks' spending, oldest first, with each week's start. */
fun recentWeeks(list: List<MoneyEntry>, b: Budget, now: Long, n: Int = 6): List<Pair<Long, Long>> =
    (n - 1 downTo 0).map { back ->
        val s = cal(weekStartOf(now, b)).apply { add(Calendar.DAY_OF_YEAR, -7 * back) }.timeInMillis
        val e = cal(s).apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis
        s to list.filter { it.kind == MoneyKind.GASTO && it.category != DEBT_CATEGORY && it.at in s until e }.sumOf { it.cents }
    }

// ── What Tokkip notices ───────────────────────────────────────────────────────

/** Tokkip's notes on your money; `moods` is your mood per day (yyyy-MM-dd → 1…5). */
fun moneyNotes(list: List<MoneyEntry>, b: Budget, moods: Map<String, Int>, now: Long): List<TokkipNote> {
    if (list.isEmpty()) return emptyList()
    val out = ArrayList<TokkipNote>()
    val w = weekSummary(list, b, now)
    val expenses = list.filter { it.kind == MoneyKind.GASTO && it.category != DEBT_CATEGORY }

    // The week's pace.
    if (w.hasBudget) {
        when {
            w.spent > b.weekly -> out += TokkipNote("money-over", "wallet", "Esta semana ya pasaste tu presupuesto por ${money(w.spent - b.weekly)}.",
                "Lo que queda de la semana, solo lo necesario. El lunes empiezas de cero.", 60)
            w.used - w.elapsed >= 0.2f && w.elapsed < 0.85f -> out += TokkipNote("money-fast", "wallet",
                "Vas rápido: llevas ${(w.used * 100).toInt()} % del presupuesto y la semana va en ${(w.elapsed * 100).toInt()} %.",
                "Te quedan ${money(w.left)}: unos ${money(w.perDay)} por día.", 56)
            w.elapsed >= 0.4f && w.used <= w.elapsed - 0.15f -> out += TokkipNote("money-calm", "piggy",
                "Vas muy bien con tu presupuesto: llevas ${(w.used * 100).toInt()} % y la semana va en ${(w.elapsed * 100).toInt()} %.",
                "Si te sobra, apártalo para tu ahorro antes de que se vaya.", 34)
        }
        if (b.weekend > 0 && w.weekendSpent > b.weekend) out += TokkipNote("money-weekend-now", "party",
            "Este fin de semana ya gastaste ${money(w.weekendSpent)} de ${money(b.weekend)}.", "Hay planes sin costo: un paseo, una película en casa.", 52)
    }

    // Weekends that run over, the last four.
    if (b.weekend > 0) {
        var over = 0; var seen = 0
        for (back in 1..4) {
            val s = cal(w.start).apply { add(Calendar.DAY_OF_YEAR, -7 * back) }.timeInMillis
            val e = cal(s).apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis
            val inWeek = expenses.filter { it.at in s until e }
            if (inWeek.isEmpty()) continue
            seen++
            if (inWeek.filter { isWeekend(it.at, b) }.sumOf { it.cents } > b.weekend) over++
        }
        if (seen >= 2 && over * 2 >= seen + 1) out += TokkipNote("money-weekends", "party",
            "En $over de tus últimos $seen fines de semana te pasaste del presupuesto de fin de semana.",
            "Súbelo un poco para que sea realista, o decide el plan del sábado desde el jueves.", 48)
    }

    // Weekly category limits.
    for ((cat, limit) in b.limits) {
        val spent = w.byCategory[cat] ?: 0
        if (limit > 0 && spent > limit) out += TokkipNote("money-limit-$cat", "wallet",
            "En ${categoryOf(cat).label} llevas ${money(spent)} esta semana; tu límite es ${money(limit)}.", "Deja esa categoría en pausa hasta el lunes.", 50)
    }

    // Categories that grew: this month against the two before, at the same point of the month.
    val mStart = monthStartOf(now)
    val dayOfMonth = cal(now).get(Calendar.DAY_OF_MONTH)
    fun upTo(monthsBack: Int): Map<String, Long> {
        val s = cal(mStart).apply { add(Calendar.MONTH, -monthsBack) }.timeInMillis
        val lastDay = cal(s).getActualMaximum(Calendar.DAY_OF_MONTH)
        val e = cal(s).apply { add(Calendar.DAY_OF_YEAR, minOf(dayOfMonth, lastDay)) }.timeInMillis
        return expenses.filter { it.at in s until e }.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.cents } }
    }
    val thisMonth = upTo(0)
    val before = listOf(upTo(1), upTo(2)).filter { it.isNotEmpty() }
    if (before.isNotEmpty()) {
        for ((cat, cents) in thisMonth) {
            val avg = before.map { it[cat] ?: 0L }.average()
            if (cents >= 30_000 && avg > 0 && cents > avg * 1.3) {
                out += TokkipNote("money-up-$cat", "trend", "Este mes llevas ${money(cents)} en ${categoryOf(cat).label}, más que otros meses a estas alturas (${money(avg.toLong())}).",
                    "Ponle un límite semanal a ${categoryOf(cat).label} en los ajustes de Dinero.", 44)
                break
            }
        }
    }

    // The little purchases that add up.
    val small = expenses.filter { it.at >= mStart && it.cents <= 8_000 }
    if (small.size >= 12) out += TokkipNote("money-small", "coffee", "Tus gastos pequeños de este mes ya suman ${money(small.sumOf { it.cents })} (${small.size} compras).",
        "Elige un día sin antojos a la semana y ve cuánto ahorras.", 40)

    // Savings this month.
    val m = monthSummary(list, now)
    val saved = m.saved
    if (saved != null && dayOfMonth >= 10) {
        when {
            m.balance < 0 -> out += TokkipNote("money-red", "wallet", "Este mes has gastado ${money(-m.balance)} más de lo que ha entrado.",
                "Revisa tus 3 categorías más grandes: ahí está el ajuste.", 55)
            b.saveGoal > 0 && saved * 100 >= b.saveGoal -> out += TokkipNote("money-goal", "piggy",
                "Vas guardando ${(saved * 100).toInt()} % de lo que te entró este mes; tu meta es ${b.saveGoal} %.", "¡Lo estás logrando! Mueve ese dinero a tu ahorro.", 38)
            b.saveGoal > 0 -> out += TokkipNote("money-goal-low", "piggy",
                "Este mes vas guardando ${(saved * 100).toInt()} % de lo que te entró; tu meta es ${b.saveGoal} %.", "Aparta tu ahorro apenas te paguen, no al final.", 42)
            saved >= 0.2f -> out += TokkipNote("money-saving", "piggy", "Este mes vas guardando ${(saved * 100).toInt()} % de lo que te entró.",
                "Ponte una meta de ahorro en los ajustes de Dinero.", 30)
        }
    }

    // Your low days and your spending.
    val perDay = expenses.filter { it.at >= now - 60L * 86_400_000 }.groupBy { dayKey(it.at) }.mapValues { (_, v) -> v.sumOf { it.cents } }
    val low = moods.filter { it.value <= 2 }.keys.map { perDay[it] ?: 0L }
    val good = moods.filter { it.value >= 4 }.keys.map { perDay[it] ?: 0L }
    if (low.size >= 3 && good.size >= 3) {
        val a = low.average(); val g = good.average()
        if (a >= 10_000 && a > g * 1.4) out += TokkipNote("money-mood", "heart",
            "Los días de bajón gastas más: ${money(a.toLong())} contra ${money(g.toLong())} en tus días buenos.",
            "En un mal día, antes de comprar, respira cinco minutos con Tokkip y decide después.", 58)
    }

    // Days without spending, and keeping the record.
    if (w.daysWithout >= 2) out += TokkipNote("money-nospend", "star", "Llevas ${w.daysWithout} días sin gastar esta semana.", "Los días en cero también son una racha.", 28)
    val last = list.maxOf { it.at }
    val gap = ((now - last) / 86_400_000L).toInt()
    if (gap >= 3 && list.size >= 5) out += TokkipNote("money-gap", "wallet", "Hace $gap días que no anotas un gasto.",
        "Anota lo de hoy en Dinero: con el número a la vista es más fácil decidir.", 45)
    return out
}

// ── JSON ──────────────────────────────────────────────────────────────────────

fun moneyJson(list: List<MoneyEntry>): String = JSONArray().apply {
    for (e in list) put(JSONObject().put("id", e.id).put("at", e.at).put("c", e.cents).put("k", e.kind.name).put("cat", e.category).put("n", e.note))
}.toString()

fun moneyOf(raw: String?): List<MoneyEntry> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id", "")
            val c = o.optLong("c", 0)
            if (id.isEmpty() || c <= 0) return@mapNotNull null
            val kind = runCatching { MoneyKind.valueOf(o.optString("k")) }.getOrDefault(MoneyKind.GASTO)
            MoneyEntry(id, o.optLong("at"), c, kind, o.optString("cat", "otros"), o.optString("n", "").take(MONEY_NOTE_LEN))
        }.sortedBy { it.at }.takeLast(MONEY_LIMIT)
    }.getOrDefault(emptyList())
}

fun budgetJson(b: Budget): String = JSONObject()
    .put("weekly", b.weekly).put("weekend", b.weekend).put("weekStart", b.weekStart).put("weekendFrom", b.weekendFrom.name)
    .put("limits", JSONObject().apply { b.limits.forEach { (k, v) -> put(k, v) } }).put("saveGoal", b.saveGoal)
    .toString()

fun budgetOf(raw: String?): Budget = runCatching {
    val o = JSONObject(raw ?: return Budget())
    val l = o.optJSONObject("limits")
    Budget(
        o.optLong("weekly", 0).coerceAtLeast(0), o.optLong("weekend", 0).coerceAtLeast(0),
        o.optInt("weekStart", Calendar.MONDAY).takeIf { it == Calendar.MONDAY || it == Calendar.SUNDAY } ?: Calendar.MONDAY,
        runCatching { WeekendFrom.valueOf(o.optString("weekendFrom")) }.getOrDefault(WeekendFrom.SABADO),
        l?.keys()?.asSequence()?.associateWith { l.optLong(it) }?.filterValues { it > 0 } ?: emptyMap(),
        o.optInt("saveGoal", 0).coerceIn(0, 90),
    )
}.getOrDefault(Budget())
