// Dinero → Pagos y deudas: what you pay every quincena or every month (a card,
// a loan, rent, a subscription), on the days you set, with how much is left
// when it's a debt with an end. Tokkip reminds you before the date (and on
// the day, and while it's late), with "Ya pagué" right in the notification;
// paying records the expense under "Deudas y pagos fijos", which counts in
// the month but not in the weekly budget.
// Pure Kotlin + org.json: tested in app/src/test.

package mx.loperstack.tokkip.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

enum class PayEvery(val label: String) { QUINCENAL("Cada quincena"), MENSUAL("Cada mes") }

const val DEBT_CATEGORY = "deudas"

data class Debt(
    val id: String,
    val name: String,
    /** Each payment, in cents. */
    val payment: Long,
    val every: PayEvery = PayEvery.MENSUAL,
    /** Day of the month to pay, 1…31; 0 = the last day. A quincena also pays on `day2`. */
    val day: Int = 15,
    val day2: Int = 0,
    /** What you owe in all, in cents; 0 = a payment with no end (rent, a subscription). */
    val total: Long = 0,
    /** What you've paid through Tokkip, in cents, and the due dates paid (yyyy-MM-dd). */
    val paid: Long = 0,
    val paidDues: Set<String> = emptySet(),
    val remind: Boolean = true,
    /** Days ahead for the reminder (0 = only on the day), and its time (minutes of the day). */
    val daysBefore: Int = 1,
    val remindAt: Int = 9 * 60,
    val color: Long = 0xFF60A5FA,
    val createdAt: Long = 0,
)

val DEBT_COLORS = listOf(0xFF60A5FA, 0xFFF472B6, 0xFF34D399, 0xFFFBBF24, 0xFFA78BFA, 0xFFF97316, 0xFF22D3EE, 0xFFF87171)

val Debt.remaining: Long get() = if (total <= 0) 0 else (total - paid).coerceAtLeast(0)
val Debt.finished: Boolean get() = total > 0 && remaining == 0L
/** Payments left until it's paid off; null for one with no end. */
val Debt.paymentsLeft: Int? get() = if (total <= 0 || payment <= 0) null else ((remaining + payment - 1) / payment).toInt()
/** What it costs a month. */
val Debt.monthly: Long get() = if (every == PayEvery.QUINCENAL) payment * 2 else payment

private fun dueIn(year: Int, month: Int, day: Int): Calendar {
    val c = Calendar.getInstance().apply { clear(); set(year, month, 1) }
    val last = c.getActualMaximum(Calendar.DAY_OF_MONTH)
    c.set(Calendar.DAY_OF_MONTH, if (day <= 0 || day > last) last else day)
    return c
}

/** The due dates of the month holding `ms`, in order. */
fun Debt.duesInMonth(ms: Long): List<String> {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH)
    val days = if (every == PayEvery.QUINCENAL) listOf(day, day2) else listOf(day)
    return days.map { dayKey(dueIn(y, m, it).timeInMillis) }.distinct().sorted()
}

/** Due dates from the month of `from`, for `months` months. */
fun Debt.dues(from: Long, months: Int = 3): List<String> {
    val c = Calendar.getInstance().apply { timeInMillis = from; set(Calendar.DAY_OF_MONTH, 1) }
    return (0 until months).flatMap { duesInMonth(c.timeInMillis).also { c.add(Calendar.MONTH, 1) } }
}

/** The first due date not paid yet, from the day you added it (it can be in the past: late). */
fun Debt.nextDue(now: Long): String? {
    if (finished) return null
    // From the day you added it, looking back at most two months for late ones.
    val start = if (createdAt > 0) maxOf(minOf(createdAt, now), now - 62L * 86_400_000) else now
    val since = dayKey(start)
    return dues(start, 5).firstOrNull { it >= since && it !in paidDues }
}

/** Whole days from today to `due` (negative when late). */
fun daysUntil(due: String, now: Long): Int = Math.round((dayStart(due) - dayStart(dayKey(now))) / 86_400_000.0).toInt()

/** "Hoy", "Mañana", "En 3 días", "Hace 2 días". */
fun dueText(due: String, now: Long): String = when (val d = daysUntil(due, now)) {
    0 -> "Hoy"
    1 -> "Mañana"
    -1 -> "Ayer"
    in 2..Int.MAX_VALUE -> "En $d días"
    else -> "Hace ${-d} días"
}

/** Paying `due`: the debt with it marked, and the expense to record. */
fun Debt.pay(due: String, now: Long, entryId: String): Pair<Debt, MoneyEntry> {
    val amount = if (total > 0) minOf(payment, remaining).coerceAtLeast(0) else payment
    return copy(paid = paid + amount, paidDues = (paidDues + due).sorted().takeLast(120).toSet()) to
        MoneyEntry(entryId, now, amount, MoneyKind.GASTO, DEBT_CATEGORY, name.take(MONEY_NOTE_LEN))
}

/** Undo a payment (from the list, if you tapped by mistake). */
fun Debt.unpay(due: String): Debt =
    if (due !in paidDues) this else copy(paid = (paid - payment).coerceAtLeast(0), paidDues = paidDues - due)

/**
 * When to remind next: `daysBefore` ahead, then on the day, then each morning
 * while it's late (up to 3 days) — for each unpaid date, the earliest to come.
 */
fun Debt.nextReminder(now: Long): Long? {
    if (!remind || finished) return null
    val start = if (createdAt > 0) maxOf(minOf(createdAt, now), now - 62L * 86_400_000) else now
    val since = dayKey(start)
    val unpaid = dues(start, 5).filter { it >= since && it !in paidDues }.take(4)
    fun at(due: String, dayOffset: Int) = Calendar.getInstance().apply {
        timeInMillis = dayStart(due); add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, remindAt / 60); set(Calendar.MINUTE, remindAt % 60)
    }.timeInMillis
    val offsets = listOf(-daysBefore.coerceIn(0, 7), 0, 1, 2, 3).distinct()
    return unpaid.flatMap { due -> offsets.map { at(due, it) } }.filter { it > now }.minOrNull()
}

/** The date a reminder ringing now is about: unpaid, from 3 days late to `daysBefore` ahead. */
fun Debt.dueToRemind(now: Long): String? {
    val start = if (createdAt > 0) maxOf(minOf(createdAt, now), now - 62L * 86_400_000) else now
    val since = dayKey(start)
    return dues(start, 5).filter { it >= since && it !in paidDues }
        .firstOrNull { daysUntil(it, now) in -3..daysBefore.coerceIn(0, 7) }
}

/** Everything you pay in a month, for the ones still running. */
fun monthlyLoad(debts: List<Debt>): Long = debts.filter { !it.finished }.sumOf { it.monthly }

/** Tokkip's notes on your payments. */
fun debtNotes(debts: List<Debt>, monthIncome: Long, now: Long): List<TokkipNote> {
    val out = ArrayList<TokkipNote>()
    for (d in debts) {
        val due = d.nextDue(now) ?: continue
        val days = daysUntil(due, now)
        when {
            days < 0 -> out += TokkipNote("debt-late-${d.id}", "card", "Se pasó la fecha de «${d.name}» (${money(d.payment)}).",
                "Págalo en cuanto puedas y márcalo en Dinero; si no se puede, llama para acordar una fecha.", 68)
            days == 0 -> out += TokkipNote("debt-today-${d.id}", "card", "Hoy toca pagar «${d.name}»: ${money(d.payment)}.",
                "Cuando lo pagues, márcalo como pagado.", 64)
            days <= 3 -> out += TokkipNote("debt-soon-${d.id}", "card", "${dueText(due, now)} toca pagar «${d.name}»: ${money(d.payment)}.",
                "Aparta ese dinero desde hoy para que no se vaya en otra cosa.", 57)
        }
        val left = d.paymentsLeft
        if (left != null && left in 1..2 && d.paid > 0) out += TokkipNote("debt-almost-${d.id}", "piggy",
            "Te ${if (left == 1) "falta 1 pago" else "faltan 2 pagos"} para terminar «${d.name}».", "¡Ya casi! Cuando acabes, ese dinero puede ir a tu ahorro.", 42)
    }
    debts.filter { it.finished && it.paidDues.maxOrNull()?.let { last -> daysUntil(last, now) >= -14 } == true }.forEach { d ->
        out += TokkipNote("debt-done-${d.id}", "sparkle", "¡Terminaste de pagar «${d.name}»!", "Celébralo, y si puedes, sigue apartando ese monto para ti.", 50)
    }
    val load = monthlyLoad(debts)
    if (monthIncome > 0 && load > monthIncome * 0.4) out += TokkipNote("debt-load", "wallet",
        "Tus pagos fijos (${money(load)} al mes) se llevan más del 40 % de lo que te entra.",
        "Antes de sumar otro pago a meses, termina alguno de los que tienes.", 46)
    return out
}

// ── JSON ──────────────────────────────────────────────────────────────────────

fun debtsJson(list: List<Debt>): String = JSONArray().apply {
    for (d in list) put(
        JSONObject().put("id", d.id).put("name", d.name).put("pay", d.payment).put("every", d.every.name)
            .put("day", d.day).put("day2", d.day2).put("total", d.total).put("paid", d.paid)
            .put("dues", JSONArray(d.paidDues.toList())).put("remind", d.remind).put("before", d.daysBefore)
            .put("at", d.remindAt).put("color", d.color).put("created", d.createdAt),
    )
}.toString()

fun debtsOf(raw: String?): List<Debt> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id", ""); val name = o.optString("name", "").trim()
            if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
            val dues = o.optJSONArray("dues")
            Debt(
                id, name.take(40), o.optLong("pay").coerceAtLeast(0),
                runCatching { PayEvery.valueOf(o.optString("every")) }.getOrDefault(PayEvery.MENSUAL),
                o.optInt("day", 15).coerceIn(0, 31), o.optInt("day2", 0).coerceIn(0, 31),
                o.optLong("total").coerceAtLeast(0), o.optLong("paid").coerceAtLeast(0),
                (0 until (dues?.length() ?: 0)).map { dues!!.optString(it) }.filter { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) }.toSet(),
                o.optBoolean("remind", true), o.optInt("before", 1).coerceIn(0, 7),
                o.optInt("at", 540).takeIf { it in 0 until 1440 } ?: 540, o.optLong("color", 0xFF60A5FA), o.optLong("created"),
            )
        }
    }.getOrDefault(emptyList())
}
