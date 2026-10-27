// "Tokkip notó…": what he sees in your routine and a small suggestion, worked
// out on the phone from your own diary, moods, streaks, sleep and breathing.
// No AI and no network: word lists, counts and averages. He reads how and
// when you write (length, time of day, the tone of your words, what you
// write about) and how that lines up with your mood, your streaks and your
// sleep. Nothing is stored or sent; it's recomputed each time.
// Pure Kotlin: tested in app/src/test.

package mx.loperstack.tokkip.data

import java.text.Normalizer
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One thing Tokkip noticed: what he saw, and what he'd suggest. `icon` is a
 * key the screen turns into an icon; `weight` is how much it matters today.
 */
data class TokkipNote(val id: String, val icon: String, val text: String, val tip: String, val weight: Int)

// ── Words ─────────────────────────────────────────────────────────────────────

/** Lowercase, without accents, letters only: "¡Qué día!" → [que, dia]. */
fun wordsOf(text: String): List<String> =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")
        .split(Regex("""[^a-zñ]+""")).filter { it.isNotEmpty() }

/** A word list: whole words, or beginnings (at least four letters) that catch a word's family. */
class Lexicon(private val exact: Set<String>, private val stems: List<String>) {
    fun matches(w: String) = w in exact || stems.any { w.startsWith(it) }
    fun count(ws: List<String>) = ws.count(::matches)
}

private val POSITIVE = Lexicon(
    setOf("paz", "rei", "chido", "chida", "amor", "risa", "risas", "excelente", "genial", "gracias", "bonito", "bonita"),
    listOf("feliz", "felic", "content", "alegr", "increib", "hermos", "logr", "disfrut", "tranquil", "emocion", "orgull", "agradec", "divert", "relaj", "motivad", "maravill", "carino", "sonre"),
)
private val NEGATIVE = Lexicon(
    setOf("mal", "fatal", "harto", "harta", "miedo", "dificil", "pesado", "pesada", "llore", "llorar"),
    listOf("cansad", "agotad", "estres", "triste", "preocup", "ansied", "ansios", "enoj", "frustr", "molest", "abrum", "aburr", "desvel", "agobi"),
)
private val THANKS = Lexicon(setOf("gracias"), listOf("agradec"))

/** Something you write about, with what Tokkip suggests when it lifts or weighs on your mood. */
class Theme(val id: String, val label: String, val icon: String, val words: Lexicon, val up: String, val down: String)

val THEMES = listOf(
    Theme("trabajo", "el trabajo", "work",
        Lexicon(setOf("jefe", "jefa", "junta", "chamba", "oficina", "clientes", "cliente"), listOf("trabaj", "reunion", "proyect", "entreg")),
        "El trabajo te está llenando: cuida también tus pausas.", "Al terminar de trabajar, regálate cinco minutos solo para ti."),
    Theme("familia", "tu familia", "family",
        Lexicon(setOf("mama", "papa", "mami", "papi", "hijo", "hija", "hijos", "hijas", "tio", "tia", "tios", "primo", "prima"), listOf("famil", "herman", "abuel")),
        "Busca un ratito con ellos esta semana.", "Si algo pesa en casa, escribirlo ayuda a acomodarlo."),
    Theme("amigos", "tus amigos", "friends",
        Lexicon(setOf("cuates", "cuate", "carnal", "carnales", "compas", "compa"), listOf("amig")),
        "Agenda algo con tus amigos pronto.", "Un mensaje a alguien de confianza puede cambiar el día."),
    Theme("pareja", "tu pareja", "heart",
        Lexicon(setOf("pareja", "novio", "novia"), listOf("espos")),
        "Dense un tiempo juntos esta semana.", "Platicarlo con calma, y escribirlo antes, ayuda."),
    Theme("ejercicio", "moverte", "sport",
        Lexicon(setOf("gym", "yoga", "pesas", "bici", "correr", "corri", "camine", "caminar"), listOf("gimnas", "entren", "ejercic", "caminat", "nadar", "nade")),
        "Moverte te sienta bien: no lo sueltes.", "Quizá te estás exigiendo de más: descansar también cuenta."),
    Theme("estudio", "la escuela", "study",
        Lexicon(setOf("clase", "clases", "uni", "tarea", "tareas"), listOf("escuel", "examen", "estudi", "universid", "maestr")),
        "Aprender te prende: date crédito por eso.", "Divide lo pendiente en bloques de Enfoque de 25 minutos."),
    Theme("descanso", "descansar", "night",
        Lexicon(setOf("siesta", "dormi", "dormir"), listOf("descans", "relaj")),
        "Descansar te hace bien: protégelo.", "Cuando no descansas, todo pesa más: cuida tu hora de dormir."),
    Theme("dinero", "el dinero", "money",
        Lexicon(setOf("dinero", "pago", "pague", "pagar", "quincena", "deuda", "deudas"), listOf("gast", "ahorr")),
        "Vas bien con tus cuentas: sigue así.", "Anotar tus gastos una vez a la semana quita peso."),
    Theme("naturaleza", "salir al aire libre", "leaf",
        Lexicon(setOf("sol", "parque", "lluvia", "playa", "cielo", "atardecer", "arboles", "jardin"), listOf("montan", "natural", "paseo", "pasear")),
        "Salir te recarga: busca un rato al aire libre esta semana.", "Un paseo corto al sol puede ayudar a despejarte."),
    Theme("musica", "la música", "music",
        Lexicon(setOf("cancion", "canciones", "concierto", "guitarra", "piano"), listOf("musica")),
        "La música te alegra: ponla de fondo en tus pendientes.", "Prueba una lista tranquila para bajar el ritmo."),
)

// ── Small helpers ─────────────────────────────────────────────────────────────

private const val DAY = 86_400_000L
private fun daysBack(now: Long, n: Int): String =
    dayKey(Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -n) }.timeInMillis)

private fun Double.one(): String = "%.1f".format(java.util.Locale.US, this)
private fun List<Int>.avg(): Double = if (isEmpty()) 0.0 else average()
/** "7 h 20", like the Sueño screen. */
fun hoursText(min: Int): String = if (min % 60 == 0) "${min / 60} h" else "${min / 60} h ${min % 60}"
private fun hourOf(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)

/** The diary's text of a day, as words. */
private fun DayLog.words(): List<String> = notes.flatMap { wordsOf(it.text) }

private val WEEKDAY = mapOf(
    Calendar.MONDAY to "lunes", Calendar.TUESDAY to "martes", Calendar.WEDNESDAY to "miércoles",
    Calendar.THURSDAY to "jueves", Calendar.FRIDAY to "viernes", Calendar.SATURDAY to "sábado", Calendar.SUNDAY to "domingo",
)

/** Compares your mood on days with and without something; null when there's too little to say. */
private fun moodSplit(moods: Map<String, Int>, with: Set<String>, minEach: Int = 3): Pair<Double, Double>? {
    val a = moods.filterKeys { it in with }.values.toList()
    val b = moods.filterKeys { it !in with }.values.toList()
    if (a.size < minEach || b.size < minEach) return null
    return a.avg() to b.avg()
}

// ── What he notices ───────────────────────────────────────────────────────────

/** Everything Tokkip noticed, most important first. */
fun tokkipNotes(r: Routine, now: Long): List<TokkipNote> {
    val out = ArrayList<TokkipNote>()
    val today = dayKey(now)
    val d30 = daysBack(now, 30)
    val d14 = daysBack(now, 14)
    val d7 = daysBack(now, 7)
    val written = r.logs.filter { l -> l.notes.any { it.text.isNotBlank() } }
    val recent = written.filter { it.day >= d30 && it.day <= today }
    val moods = r.logs.filter { it.day >= daysBack(now, 60) && it.averageMood > 0 }.associate { it.day to it.averageMood }

    // How you write: when, how much, how often.
    val notes30 = recent.flatMap { l -> l.notes.filter { it.text.isNotBlank() } }
    if (notes30.size >= 6) {
        val hours = notes30.map { hourOf(it.at) }
        val night = hours.count { it >= 20 || it < 3 }.toDouble() / hours.size
        val morning = hours.count { it in 5..11 }.toDouble() / hours.size
        if (night >= 0.6) out += TokkipNote("write-night", "night", "Escribes casi siempre de noche, cuando el día ya se calmó.",
            "Si un día llegas sin ganas, una sola línea también cuenta.", 28)
        else if (morning >= 0.5) out += TokkipNote("write-morning", "sun", "Eres de escribir temprano: empiezas el día poniendo en orden tus ideas.",
            "En la noche, una línea de cómo terminó el día cierra el círculo.", 28)
    }
    val wordsPerDay = recent.associate { it.day to it.words().size }
    val last7 = wordsPerDay.filterKeys { it > d7 }.values.toList()
    val before = wordsPerDay.filterKeys { it <= d7 }.values.toList()
    if (last7.size >= 2 && before.size >= 3) {
        val a = last7.avg()
        val b = before.avg()
        if (b >= 15 && a < b * 0.5) out += TokkipNote("write-shorter", "write", "Últimamente escribes más corto que antes.",
            "No pasa nada. Prueba con tres palabras: cómo te fue, qué sentiste, qué agradeces.", 44)
        else if (a >= 15 && a > b * 1.5) out += TokkipNote("write-longer", "write", "Esta semana te has abierto más en el diario.",
            "Escribir así ayuda a entenderte. Sigue dándote ese espacio.", 38)
    }
    val lastWritten = written.lastOrNull { it.day <= today }?.day
    if (lastWritten != null && written.size >= 3) {
        val gap = ((dayStart(today) - dayStart(lastWritten)) / DAY).toInt()
        if (gap >= 3) out += TokkipNote("write-gap", "write", "Hace $gap días que no escribes en tu diario.",
            "¿Qué fue lo mejor de hoy? Con eso basta para retomar.", 58 + minOf(gap, 10))
    }
    val run = streak(r.logs.filter { l -> l.notes.any { it.text.isNotBlank() } }, now)
    if (run >= 3) {
        val big = run in listOf(7, 14, 21, 30, 60, 100)
        out += TokkipNote("write-streak", "flame", "Llevas $run días seguidos escribiendo.",
            if (big) "¡$run! Eso ya es un hábito de verdad." else "Tu constancia se nota. Mañana, una línea más.", if (big) 52 else 32)
    }

    // The tone of your words, the last two weeks.
    val words14 = written.filter { it.day >= d14 && it.day <= today }.flatMap { it.words() }
    val pos = POSITIVE.count(words14)
    val neg = NEGATIVE.count(words14)
    if (neg >= 4 && neg > pos * 1.5) out += TokkipNote("tone-heavy", "air", "En tus notas recientes aparecen más palabras de cansancio o estrés.",
        "Esta noche prueba Respirar cinco minutos antes de dormir.", 54)
    else if (pos >= 5 && pos >= neg * 2) out += TokkipNote("tone-light", "sparkle", "Tus notas de estos días están llenas de cosas buenas.",
        "Anota qué las hizo posibles, para repetirlo.", 34)
    if (THANKS.count(words14) >= 3) out += TokkipNote("thanks", "thanks", "Agradeces seguido en tu diario.",
        "Sigue así: la gratitud se vuelve costumbre y se nota en tu ánimo.", 30)

    // What you write about, and how it lines up with your mood.
    val themed = THEMES.map { t -> t to written.filter { it.day >= daysBack(now, 60) && it.day <= today && t.words.count(it.words()) > 0 }.map { it.day }.toSet() }
    var themeNoted = false
    for ((t, days) in themed.sortedByDescending { it.second.size }) {
        val split = moodSplit(moods, days) ?: continue
        val diff = split.first - split.second
        if (abs(diff) < 0.6) continue
        themeNoted = true
        out += if (diff > 0) TokkipNote("theme-up-${t.id}", t.icon,
            "Los días que escribes de ${t.label}, tu ánimo sube (${split.first.one()} contra ${split.second.one()}).", t.up, 48 + (diff * 10).roundToInt())
        else TokkipNote("theme-down-${t.id}", t.icon,
            "Los días que aparece ${t.label} en tu diario, tu ánimo baja un poco.", t.down, 46 + (-diff * 10).roundToInt())
    }
    if (!themeNoted) themed.filter { it.second.count { d -> d >= d30 } >= 4 }
        .maxByOrNull { it.second.size }?.let { (t, days) ->
            out += TokkipNote("theme-top-${t.id}", t.icon, "Lo que más aparece en tu diario este mes: ${t.label} (${days.count { it >= d30 }} días).",
                "Tu diario también es para lo que te hace bien: anótalo cuando pase.", 20)
        }

    // Your streaks: what helps your mood, what slipped, what's close.
    val cal = Calendar.getInstance()
    for (h in r.habits) {
        val scheduled = (1..60).map { back -> Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -back) } }
            .filter { it.timeInMillis >= h.createdAt - DAY && h.scheduledOn(it) }.map { dayKey(it.timeInMillis) }
        val doneDays = scheduled.filter { it in h.done }.toSet()
        moodSplit(moods.filterKeys { it in scheduled }, doneDays, 4)?.let { (a, b) ->
            if (a - b >= 0.5) out += TokkipNote("habit-mood-${h.id}", "heart",
                "Los días que cumples «${h.name}» te sientes mejor (${a.one()} contra ${b.one()}).",
                "En un mal día, empieza por esa.", 64)
        }
        val n = h.streak(now)
        val lastTwo = scheduled.take(2)
        val hadIt = scheduled.drop(2).take(12).count { it in h.done } >= 3
        if (n == 0 && lastTwo.size == 2 && lastTwo.none { it in h.done } && hadIt) out += TokkipNote("habit-slip-${h.id}", "flame",
            "«${h.name}» se te ha ido estos días.", "Hazla en versión mini hoy: dos minutos cuentan y la racha vuelve.", 57)
        val goal = listOf(7, 14, 30, 60, 100).firstOrNull { it > n && it - n <= 2 }
        if (goal != null && n >= 3) out += TokkipNote("habit-near-${h.id}", "star",
            "Estás a ${goal - n} ${if (goal - n == 1) "día" else "días"} de llegar a $goal con «${h.name}».", "¡No la sueltes ahora!", 50)
        else if (n >= 7) out += TokkipNote("habit-strong-${h.id}", "flame", "$n días seguidos con «${h.name}»: ya es parte de tu rutina.",
            "Cuando algo ya es costumbre, puedes sumar otra racha pequeña.", 26)
    }
    if (r.habits.isNotEmpty()) {
        // Weekdays against the weekend, the last four weeks.
        var wkDue = 0; var wkDone = 0; var weDue = 0; var weDone = 0
        val byDay = HashMap<Int, Pair<Int, Int>>()
        for (back in 1..28) {
            val c = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -back) }
            val k = dayKey(c.timeInMillis)
            val dow = c.get(Calendar.DAY_OF_WEEK)
            for (h in r.habits) {
                if (c.timeInMillis < h.createdAt - DAY || !h.scheduledOn(c)) continue
                val done = if (k in h.done) 1 else 0
                if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) { weDue++; weDone += done } else { wkDue++; wkDone += done }
                val p = byDay[dow] ?: (0 to 0)
                byDay[dow] = (p.first + 1) to (p.second + done)
            }
        }
        if (wkDue >= 8 && weDue >= 4) {
            val wk = wkDone.toDouble() / wkDue
            val we = weDone.toDouble() / weDue
            if (wk - we >= 0.3) out += TokkipNote("weekend", "calendar", "Entre semana cumples tus rachas, pero el fin de semana se te caen.",
                "Pon el recordatorio del fin de semana más tarde, o deja algunas solo de lunes a viernes.", 40)
        }
        byDay.filterValues { it.first >= 3 }.maxByOrNull { it.value.second.toDouble() / it.value.first }?.let { (dow, p) ->
            if (p.second.toDouble() / p.first >= 0.7) out += TokkipNote("best-day", "star", "Tu mejor día para las rachas es el ${WEEKDAY[dow]}.",
                "Si quieres empezar un hábito nuevo, arráncalo ese día.", 14)
        }
    }

    // Sleep.
    val week = r.nights.filter { it.day > d7 && it.day <= today && it.minutes > 0 }
    if (week.size >= 4) {
        val avg = week.map { it.minutes }.avg()
        if (avg < r.sleepGoalMin - 45) out += TokkipNote("sleep-short", "night",
            "Esta semana dormiste en promedio ${hoursText(avg.roundToInt())}, y tu meta es ${hoursText(r.sleepGoalMin)}.",
            "Esta noche intenta acostarte veinte minutos antes.", 55)
    }
    val goodNights = r.nights.filter { it.minutes >= r.sleepGoalMin - 30 }.map { it.day }.toSet()
    moodSplit(moods.filterKeys { k -> r.nights.any { it.day == k } }, goodNights)?.let { (a, b) ->
        if (a - b >= 0.5) out += TokkipNote("sleep-mood", "night", "Cuando duermes lo que te propones, tu día sale mejor (${a.one()} contra ${b.one()}).",
            "Tu hora de dormir también es autocuidado.", 52)
    }

    // Your mood, this week against before.
    val m7 = moods.filterKeys { it > d7 && it <= today }.values.toList()
    val mBefore = moods.filterKeys { it <= d7 && it > daysBack(now, 21) }.values.toList()
    if (m7.size >= 3 && mBefore.size >= 3) {
        val diff = m7.avg() - mBefore.avg()
        if (diff <= -0.7) out += TokkipNote("mood-down", "heart", "Tu ánimo ha estado más bajo esta semana.",
            "Escríbelo, respira un rato con Tokkip o platícalo con alguien de confianza.", 62)
        else if (diff >= 0.7) out += TokkipNote("mood-up", "sparkle", "Esta semana tu ánimo ha mejorado.",
            "¿Qué cambió? Anótalo, para acordarte de lo que te funciona.", 44)
    }

    // Breathing.
    val lowLately = moods.filterKeys { it >= daysBack(now, 3) && it <= today }.values.any { it <= 2 }
    val breathedLately = r.breaths.any { it >= now - 3 * DAY }
    if (lowLately && !breathedLately) out += TokkipNote("breathe-now", "air", "Tuviste días pesados y no has respirado con Tokkip.",
        "Cinco minutos en Respirar ayudan a soltar.", 60)
    moodSplit(moods, r.breaths.map { dayKey(it) }.toSet())?.let { (a, b) ->
        if (a - b >= 0.5) out += TokkipNote("breathe-mood", "air", "Los días que respiras con Tokkip, tu ánimo es mejor (${a.one()} contra ${b.one()}).",
            "Haz de Respirar una pausa fija, como después de comer.", 46)
    }

    // Your money (data/Money.kt), with your mood per day.
    out += moneyNotes(r.money, r.budget, r.logs.filter { it.averageMood > 0 }.associate { it.day to it.averageMood }, now)
    out += debtNotes(r.debts, monthSummary(r.money, now).income, now)
    // Your screen (data/ScreenTime.kt).
    out += screenNotes(r.screenDays, r.screenDays.firstOrNull { it.day == today }, r.screenGoal, r.nights, moods, now)

    if (r.logs.isEmpty() && r.habits.isEmpty() && r.money.isEmpty()) out += TokkipNote("hello", "sparkle", "Todavía te estoy conociendo.",
        "Cuéntame cómo te fue hoy en el Diario, o crea tu primera racha.", 20)
    return out.distinctBy { it.id }.sortedByDescending { it.weight }
}

/** Little words for any day, when there's nothing particular to say. */
val GENTLE = listOf(
    "Lo pequeño, repetido, se vuelve grande." to "Hoy basta con un paso.",
    "Hoy no tiene que ser perfecto, solo tuyo." to "Elige una cosa y hazla con calma.",
    "Celebra lo que sí hiciste." to "Anótalo en tu diario antes de dormir.",
    "Un vaso de agua, una respiración y seguimos." to "Toma una pausa de un minuto.",
    "Tu yo de mañana te va a agradecer lo de hoy." to "Escribe una línea para él.",
    "Descansar también es avanzar." to "Si hoy toca bajar el ritmo, está bien.",
    "Cada día que vuelves cuenta." to "Aquí sigo contigo.",
)

/**
 * The note of the day for Inicio: one of the three that matter most today,
 * changing day to day so it doesn't repeat; a gentle phrase if there's none.
 */
fun noteOfTheDay(r: Routine, now: Long): TokkipNote {
    val seed = dayKey(now).hashCode().let { if (it < 0) -it else it }
    val top = tokkipNotes(r, now).filter { it.weight >= 20 }.take(3)
    if (top.isNotEmpty()) {
        // The most pressing (weight 55+) always wins; otherwise they take turns.
        return if (top.first().weight >= 55) top.first() else top[seed % top.size]
    }
    val (text, tip) = GENTLE[seed % GENTLE.size]
    return TokkipNote("gentle", "sparkle", text, tip, 1)
}
