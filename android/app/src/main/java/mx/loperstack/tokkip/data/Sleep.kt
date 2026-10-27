// The sleep log: one entry per night (keyed by the morning you woke up),
// typed in or brought from Health Connect (your Pixel Watch, through Fitbit),
// with how it felt and what might have played a part. Plus the numbers that
// matter — duration, regularity, debt — and tips drawn from them.
// Pure Kotlin + org.json: tested in app/src/test.

package mx.loperstack.tokkip.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SleepEntry(
    /** yyyy-MM-dd of the morning. */
    val night: String,
    val bed: Long,
    val wake: Long,
    /** 1…5 like the diary's moods (5 = rested), 0 = not rated. */
    val quality: Int = 0,
    val factors: List<String> = emptyList(),
    val note: String = "",
    /** "manual" or "health" (Health Connect). */
    val source: String = "manual",
    val deepMin: Int = 0,
    val remMin: Int = 0,
    val awakeMin: Int = 0,
)

/** Minutes asleep: in bed minus the time awake in between. */
val SleepEntry.minutes: Int get() = (((wake - bed) / 60_000L).toInt() - awakeMin).coerceAtLeast(0)

/** What can get in the way of a night, as chips. */
val SLEEP_FACTORS = listOf(
    "cafe" to "Café tarde", "pantalla" to "Pantalla", "cena" to "Cena pesada", "alcohol" to "Alcohol",
    "estres" to "Estrés", "ejercicio" to "Ejercicio", "siesta" to "Siesta", "ruido" to "Ruido",
)

val SLEEP_QUALITY = listOf(5 to "Descansado", 4 to "Bien", 3 to "Regular", 2 to "Cansado", 1 to "Agotado")

data class SleepGoal(val bedMin: Int = 23 * 60, val wakeMin: Int = 7 * 60) {
    /** How long the goal sleeps, in minutes. */
    val minutes: Int get() = ((wakeMin - bedMin + 24 * 60) % (24 * 60)).let { if (it == 0) 24 * 60 else it }
}

/** How last night shows on Tokkip: under 6 h tired (eye bags until noon), 7 h or more rested; only on that morning's day. */
fun restLevel(list: List<SleepEntry>, now: Long): mx.loperstack.tokkip.character.Rest {
    val last = list.lastOrNull() ?: return mx.loperstack.tokkip.character.Rest.UNKNOWN
    if (last.night != dayKey(now)) return mx.loperstack.tokkip.character.Rest.UNKNOWN
    return when {
        last.minutes < 6 * 60 ->
            if (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY) < 12) mx.loperstack.tokkip.character.Rest.TIRED
            else mx.loperstack.tokkip.character.Rest.OK
        last.minutes >= 7 * 60 -> mx.loperstack.tokkip.character.Rest.GREAT
        else -> mx.loperstack.tokkip.character.Rest.OK
    }
}

fun hhmm(min: Int): String = "%d:%02d".format(((min % 1440) + 1440) % 1440 / 60, ((min % 60) + 60) % 60)

fun durationText(min: Int): String = if (min % 60 == 0) "${min / 60} h" else "${min / 60} h ${min % 60}"

fun upsertSleep(list: List<SleepEntry>, e: SleepEntry): List<SleepEntry> =
    (list.filter { it.night != e.night } + e).sortedBy { it.night }.takeLast(730)

/**
 * Nights from Health Connect join the log: the watch's times and stages win,
 * your own rating, factors and note stay.
 */
fun mergeHealth(list: List<SleepEntry>, health: List<SleepEntry>): List<SleepEntry> {
    var out = list
    for (h in health) {
        val mine = out.firstOrNull { it.night == h.night }
        out = upsertSleep(out, if (mine == null) h else h.copy(quality = mine.quality, factors = mine.factors, note = mine.note))
    }
    return out
}

// ── Numbers ───────────────────────────────────────────────────────────────────

/** Minutes after 18:00, so a bedtime around midnight averages well. */
private fun eveningMinute(ms: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    return (m - 18 * 60 + 1440) % 1440
}

data class SleepStats(
    val nights: Int,
    val avgMin: Int,
    /** Average bedtime, minutes of the day. */
    val avgBed: Int,
    /** Spread of bedtimes, ± minutes (lower = steadier). */
    val spread: Int,
    /** Minutes short of the goal over these nights (0 if none). */
    val debt: Int,
    val avgQuality: Float,
    /** 0…100: duration against the goal, regularity and how rested you felt. */
    val score: Int,
)

fun sleepStats(list: List<SleepEntry>, goal: SleepGoal, nights: Int = 7): SleepStats? {
    val last = list.sortedBy { it.night }.takeLast(nights)
    if (last.isEmpty()) return null
    val avg = last.map { it.minutes }.average()
    val beds = last.map { eveningMinute(it.bed) }
    val meanBed = beds.average()
    val spread = sqrt(beds.map { (it - meanBed) * (it - meanBed) }.average()).roundToInt()
    val debt = last.sumOf { (goal.minutes - it.minutes).coerceAtLeast(0) }
    val rated = last.filter { it.quality > 0 }
    val quality = if (rated.isEmpty()) 0f else rated.map { it.quality }.average().toFloat()
    val durScore = (avg / goal.minutes).coerceAtMost(1.0) * 50
    val regScore = (1 - (spread / 90.0)).coerceIn(0.0, 1.0) * 25
    val qScore = if (rated.isEmpty()) 18.0 else (quality - 1) / 4.0 * 25
    return SleepStats(
        nights = last.size,
        avgMin = avg.roundToInt(),
        avgBed = ((meanBed.roundToInt() + 18 * 60) % 1440),
        spread = spread,
        debt = debt,
        avgQuality = quality,
        score = (durScore + regScore + qScore).roundToInt().coerceIn(0, 100),
    )
}

// ── Consejos ──────────────────────────────────────────────────────────────────

/** `icon` is a key (ui/Expressive.kt MIcon.tip). */
data class SleepTip(val icon: String, val title: String, val text: String)

/** Tips that fit your last nights (at most three), most useful first. */
fun personalTips(list: List<SleepEntry>, goal: SleepGoal): List<SleepTip> {
    val s = sleepStats(list, goal) ?: return listOf(
        SleepTip("start", "Empieza tu registro", "Anota cómo dormiste unas noches y Tokkip te dirá qué ajustar."),
    )
    val out = mutableListOf<SleepTip>()
    if (s.avgMin < goal.minutes - 30) {
        out += SleepTip(
            "short", "Te faltan ${durationText(goal.minutes - s.avgMin)} por noche",
            "Acuéstate 15–20 minutos antes cada semana hasta llegar a tu meta, en lugar de compensar el fin de semana.",
        )
    }
    if (s.spread > 45) {
        out += SleepTip(
            "irregular", "Horario irregular (±${s.spread} min)",
            "Tu reloj interno ama la constancia: misma hora para dormir y despertar, también sábado y domingo (±30 min).",
        )
    }
    // A factor that keeps showing up on your worse nights.
    val rated = list.takeLast(30).filter { it.quality > 0 }
    if (rated.size >= 4) {
        val base = rated.map { it.quality }.average()
        val worst = SLEEP_FACTORS.mapNotNull { (id, label) ->
            val with = rated.filter { id in it.factors }
            if (with.size < 2) null else Triple(id, label, base - with.map { it.quality }.average())
        }.maxByOrNull { it.third }
        if (worst != null && worst.third >= 0.6) {
            out += when (worst.first) {
                "cafe" -> SleepTip("cafe", "El café te pasa factura", "La cafeína dura 6–8 h en el cuerpo: tu último café, antes de las 2 de la tarde.")
                "pantalla" -> SleepTip("pantalla", "La pantalla te quita descanso", "Deja el teléfono 30–60 min antes de dormir; si lo usas, brillo bajo y modo oscuro.")
                "cena" -> SleepTip("cena", "Cenas pesadas = peor noche", "Cena 2–3 h antes de acostarte y algo ligero.")
                "alcohol" -> SleepTip("alcohol", "El alcohol fragmenta tu sueño", "Aunque ayuda a dormirse, rompe el sueño profundo de la segunda mitad de la noche.")
                "estres" -> SleepTip("breathe", "El estrés llega contigo a la cama", "Escribe en el diario lo que te ronda y haz 5 min de respiración con Tokkip antes de dormir.")
                "siesta" -> SleepTip("siesta", "Las siestas te restan sueño", "Si la necesitas, que sea de 20 min y antes de las 3 de la tarde.")
                "ruido" -> SleepTip("ruido", "El ruido te despierta", "Prueba tapones, ruido blanco o un ventilador; un cuarto fresco y oscuro ayuda.")
                else -> SleepTip("ejercicio", "El ejercicio tarde te activa", "Muévete, pero termina el ejercicio intenso 3 h antes de dormir.")
            }
        }
    }
    if (s.debt > 120 && out.none { it.icon == "short" }) {
        out += SleepTip("debt", "Deuda de sueño: ${durationText(s.debt)}", "Esta semana, una o dos noches 30 min más largas te ponen al día.")
    }
    if (out.isEmpty()) out += SleepTip("good", "¡Vas muy bien!", "Duermes ${durationText(s.avgMin)} de promedio con un horario constante. Sigue así.")
    return out.take(3)
}

private val DAILY_TIPS = listOf(
    SleepTip("light", "Luz por la mañana", "10 min de luz natural al despertar ajustan tu reloj interno para tener sueño a tiempo por la noche."),
    SleepTip("cool", "Cuarto fresco", "Entre 18 y 21 °C se duerme mejor: el cuerpo necesita bajar su temperatura para dormir."),
    SleepTip("dark", "Oscuridad total", "Hasta un poco de luz frena la melatonina. Cortinas gruesas o antifaz."),
    SleepTip("breathe", "Respira 4-7-8", "Inhala 4 s, sostén 7, exhala 8. Tres o cuatro rondas calman el sistema nervioso."),
    SleepTip("bed", "La cama, solo para dormir", "Si a los 20 min no te duermes, levántate, luz tenue y algo tranquilo hasta tener sueño."),
    SleepTip("winddown", "Modo descanso", "Activa el modo hora de dormir del Pixel: pantalla en grises y notificaciones en silencio."),
    SleepTip("mind", "Vacía la mente", "Escribe 3 cosas pendientes para mañana: tu cerebro deja de repasarlas."),
    SleepTip("shower", "Ducha tibia", "Una ducha 1–2 h antes de dormir ayuda a que la temperatura baje después."),
    SleepTip("routine", "Rutina de 30 min", "Las mismas señales cada noche (luz baja, lectura, respiración) le dicen al cuerpo que ya toca."),
    SleepTip("water", "Ojo con los líquidos", "Bebe agua durante el día y menos en la última hora para no despertarte."),
    SleepTip("sound", "Sonido de fondo", "Ruido blanco o lluvia tapan los ruidos sueltos que te despiertan."),
    SleepTip("relax", "Relaja el cuerpo", "Tensa y suelta cada grupo de músculos de los pies a la cara, despacio."),
)

/** The tip of the day, the same all day. */
fun dailyTip(now: Long): SleepTip {
    val day = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_YEAR)
    return DAILY_TIPS[day % DAILY_TIPS.size]
}

// ── JSON ──────────────────────────────────────────────────────────────────────

fun sleepJson(list: List<SleepEntry>): String = JSONArray().apply {
    for (e in list) put(
        JSONObject().put("night", e.night).put("bed", e.bed).put("wake", e.wake).put("quality", e.quality)
            .put("factors", JSONArray(e.factors)).put("note", e.note).put("source", e.source)
            .put("deep", e.deepMin).put("rem", e.remMin).put("awake", e.awakeMin),
    )
}.toString()

fun sleepOf(raw: String?): List<SleepEntry> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val a = JSONArray(raw)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val night = o.optString("night", "")
            val bed = o.optLong("bed")
            val wake = o.optLong("wake")
            if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(night) || wake <= bed || wake - bed > 20 * 3600_000L) return@mapNotNull null
            val f = o.optJSONArray("factors")
            SleepEntry(
                night, bed, wake, o.optInt("quality").coerceIn(0, 5),
                (0 until (f?.length() ?: 0)).mapNotNull { f?.optString(it) }.filter { id -> SLEEP_FACTORS.any { it.first == id } },
                o.optString("note", "").take(300), if (o.optString("source") == "health") "health" else "manual",
                o.optInt("deep"), o.optInt("rem"), o.optInt("awake"),
            )
        }.sortedBy { it.night }
    } catch (_: Exception) {
        emptyList()
    }
}

/** A night from bed and wake times: keyed by the wake-up day. */
fun sleepEntry(bed: Long, wake: Long, quality: Int = 0, factors: List<String> = emptyList(), note: String = "", source: String = "manual") =
    SleepEntry(dayKey(wake), bed, wake, quality, factors, note.trim().take(300), source)

/** Last night's bed and wake times from the goal: yesterday at bedtime, today at wake time. */
fun goalNight(goal: SleepGoal, now: Long): Pair<Long, Long> {
    val wake = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, goal.wakeMin / 60); set(Calendar.MINUTE, goal.wakeMin % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return (wake - goal.minutes * 60_000L) to wake
}
