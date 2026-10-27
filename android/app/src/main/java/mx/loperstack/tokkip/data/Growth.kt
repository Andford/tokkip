// Tokkip grows with you: every day you look after yourself gives him cariño
// (care points) — ticking a streak, writing in the diary, logging your mood,
// breathing, sleeping what you meant to, keeping track of your money. Enough cariño and he reaches the next
// stage; past the last one the cycle goes round again (Leyenda ★1, ★2…).
//
// Nothing is stored: the points are worked out from your own history every
// time, so a restored copy brings his growth back and starting over resets it.
// Each kind has a daily ceiling, so ticking everything twice doesn't count.
// Pure Kotlin: tested in app/src/test.

package mx.loperstack.tokkip.data

import java.util.Calendar

/** A night's sleep, plain: the morning's day key, minutes asleep and how rested (0…5). */
data class Night(val day: String, val minutes: Int, val quality: Int = 0)

/** Everything Tokkip's growth and his notes read. */
data class Routine(
    val logs: List<DayLog> = emptyList(),
    val habits: List<Habit> = emptyList(),
    /** Breathing sessions' start times. */
    val breaths: List<Long> = emptyList(),
    val nights: List<Night> = emptyList(),
    val sleepGoalMin: Int = 8 * 60,
    /** Dinero: what you spent and earned, and your budget (data/Money.kt). */
    val money: List<MoneyEntry> = emptyList(),
    val budget: Budget = Budget(),
    val debts: List<Debt> = emptyList(),
    /** Pantalla: a summary per day, and your daily scroll goal (minutes). */
    val screenDays: List<ScreenDay> = emptyList(),
    val screenGoal: Int = 60,
)

data class Stage(val level: Int, val name: String, val from: Int, val blurb: String)

val STAGES = listOf(
    Stage(1, "Recién llegado", 0, "Acaba de llegar y te está conociendo."),
    Stage(2, "Curioso", 40, "Ya reconoce tu rutina y te sigue a todos lados."),
    Stage(3, "Juguetón", 150, "Se siente en casa: juega, baila y te echa porras."),
    Stage(4, "Valiente", 350, "Te acompaña en los días pesados sin soltarte."),
    Stage(5, "Sabio", 700, "Conoce tus ritmos y te da buenos consejos."),
    Stage(6, "Leyenda", 1200, "Juntos ya hicieron historia. Cada 600 de cariño, una vuelta más al ciclo."),
)

/** Past Leyenda, a new turn of the cycle every LOOP points. */
const val LOOP = 600

/** The kinds of care, in the order they're shown, with their daily ceiling. */
enum class Care(val label: String, val cap: Int) {
    RACHAS("Rachas", 9), DIARIO("Diario", 7), ANIMO("Ánimo", 2), RESPIRAR("Respirar", 6), SUENO("Sueño", 3), FINANZAS("Finanzas", 2), PANTALLA("Pantalla", 3),
}

data class Growth(
    val points: Int,
    val stage: Stage,
    /** Turns of the cycle past Leyenda. */
    val loops: Int,
    /** Points into this stage (or turn), and the stage's span. */
    val into: Int,
    val span: Int,
    val today: Int,
    val week: Int,
    val byCare: Map<Care, Int>,
) {
    val progress: Float get() = if (span <= 0) 1f else (into.toFloat() / span).coerceIn(0f, 1f)
    val toNext: Int get() = (span - into).coerceAtLeast(0)
    val title: String get() = stage.name + if (loops > 0) " ★$loops" else ""
    /** What comes next: the next stage's name, or the next turn. */
    val nextName: String get() = STAGES.getOrNull(stage.level)?.name ?: "${stage.name} ★${loops + 1}"
    /** How big he's drawn on Inicio: a little one grows into his full size. */
    val scale: Float get() = 0.80f + 0.04f * (stage.level - 1)
}

private fun words(text: String) = text.split(Regex("""\s+""")).count { it.any(Char::isLetter) }

/** Cariño per day and kind, up to (and including) `today`. */
fun carePerDay(r: Routine, today: String): Map<String, Map<Care, Int>> {
    val raw = HashMap<String, MutableMap<Care, Int>>()
    fun add(day: String, c: Care, n: Int) {
        if (day > today || n <= 0) return
        val m = raw.getOrPut(day) { HashMap() }
        m[c] = (m[c] ?: 0) + n
    }
    for (h in r.habits) for (d in h.done) add(d, Care.RACHAS, 3)
    for (l in r.logs) {
        val texts = l.notes.filter { it.text.isNotBlank() }
        if (texts.isNotEmpty()) add(l.day, Care.DIARIO, 4)
        if (texts.sumOf { words(it.text) } >= 40) add(l.day, Care.DIARIO, 2)
        if (l.notes.any { it.image != null }) add(l.day, Care.DIARIO, 1)
        if (l.mood > 0 || l.moods.isNotEmpty()) add(l.day, Care.ANIMO, 2)
    }
    for (b in r.breaths) add(dayKey(b), Care.RESPIRAR, 3)
    for (n in r.nights) {
        add(n.day, Care.SUENO, 1)
        if (n.minutes >= r.sleepGoalMin - 30) add(n.day, Care.SUENO, 2)
    }
    for (m in r.money) add(dayKey(m.at), Care.FINANZAS, 2)
    // A whole day within your scroll goal (today counts once it's over).
    for (d in r.screenDays) if (d.day < today && d.totalMin > 0 && d.scrollMin <= r.screenGoal) add(d.day, Care.PANTALLA, 3)
    return raw.mapValues { (_, m) -> m.mapValues { (c, v) -> minOf(v, c.cap) } }
}

fun stageFor(points: Int): Stage = STAGES.last { points >= it.from }

fun growth(r: Routine, now: Long): Growth {
    val today = dayKey(now)
    val weekFrom = dayKey(Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -6) }.timeInMillis)
    val per = carePerDay(r, today)
    val byCare = Care.entries.associateWith { c -> per.values.sumOf { it[c] ?: 0 } }
    val points = byCare.values.sum()
    val stage = stageFor(points)
    val next = STAGES.getOrNull(stage.level)
    val (loops, into, span) = if (next != null) Triple(0, points - stage.from, next.from - stage.from)
    else {
        val past = points - stage.from
        Triple(past / LOOP, past % LOOP, LOOP)
    }
    return Growth(
        points, stage, loops, into, span,
        today = per[today]?.values?.sum() ?: 0,
        week = per.filterKeys { it >= weekFrom }.values.sumOf { it.values.sum() },
        byCare = byCare,
    )
}
