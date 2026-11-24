// Tokkip Fit's challenges and achievements, all worked out from what you did
// (the workouts and the days): this week's challenge, and the badges you've
// earned. Pure, so it's tested without a phone.

package mx.loperstack.tokkip.fit

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A badge: earned or not, and how far along (0…1) when not. */
data class Badge(val id: String, val name: String, val about: String, val icon: String, val progress: Float) {
    val earned get() = progress >= 1f
}

/** What Fit knows to work them out with. */
data class Effort(
    val done: List<Done>,
    /** Steps per day, oldest first, today last (what Health Connect told). */
    val steps: List<Long> = emptyList(),
    val stepGoal: Long = 8_000,
    val streak: Int = 0,
    /** Personal records beaten so far. */
    val records: Int = 0,
)

fun badges(e: Effort): List<Badge> {
    val n = e.done.size
    val km = e.done.sumOf { it.meters } / 1000.0
    val minutes = e.done.sumOf { it.seconds } / 60.0
    val breathe = e.done.count { it.kind == "breathe" }
    val gym = e.done.count { it.kind == "gym" }
    val watch = e.done.count { it.from == "watch" }
    val best = e.steps.maxOrNull() ?: 0L
    val kinds = e.done.map { group(it.kind) }.toSet().size
    val early = e.done.count { Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).hour < 8 }
    fun f(v: Double, goal: Double) = (v / goal).toFloat().coerceIn(0f, 1f)
    return listOf(
        Badge("primero", "El primero", "Tu primer entrenamiento con Tokkip", "bolt", f(n.toDouble(), 1.0)),
        Badge("diez", "Constante", "10 entrenamientos", "repeat", f(n.toDouble(), 10.0)),
        Badge("cincuenta", "Imparable", "50 entrenamientos", "trophy", f(n.toDouble(), 50.0)),
        Badge("racha7", "Semana redonda", "7 días seguidos cumpliendo tus pasos", "fire", f(e.streak.toDouble(), 7.0)),
        Badge("diezmil", "Diez mil", "10,000 pasos en un día", "walk", f(best.toDouble(), 10_000.0)),
        Badge("cinco_k", "Mis primeros 5 km", "5 km en total de cardio", "route", f(km, 5.0)),
        Badge("cien_k", "Cien kilómetros", "100 km de cardio", "route", f(km, 100.0)),
        Badge("hora", "Una hora", "60 minutos de entrenamiento", "timer", f(minutes, 60.0)),
        Badge("diez_horas", "Diez horas", "600 minutos de entrenamiento", "timer", f(minutes, 600.0)),
        Badge("record", "Récord", "Tu primer récord personal en el gym", "trophy", f(e.records.toDouble(), 1.0)),
        Badge("gym10", "Del gym", "10 sesiones de gym", "gym", f(gym.toDouble(), 10.0)),
        Badge("zen", "Zen", "10 sesiones de respiración", "breathe", f(breathe.toDouble(), 10.0)),
        Badge("reloj", "Desde la muñeca", "Un entrenamiento desde el reloj", "watch", f(watch.toDouble(), 1.0)),
        Badge("todoterreno", "Todoterreno", "Prueba En casa, Gym, Cardio y Respira", "star", f(kinds.toDouble(), 4.0)),
        Badge("madrugador", "Madrugador", "5 entrenamientos antes de las 8", "sun", f(early.toDouble(), 5.0)),
    )
}

/** En casa, Gym, Cardio or Respira. */
fun group(kind: String) = when (kind) {
    "gym" -> "gym"
    "run", "walk", "bike" -> "cardio"
    "breathe" -> "breathe"
    else -> "home"
}

/** This week's challenge (Monday to Sunday): the same all week, and how far you are. */
data class Challenge(val id: String, val title: String, val about: String, val icon: String, val value: Double, val goal: Double, val unit: String) {
    val progress get() = (value / goal).toFloat().coerceIn(0f, 1f)
    val done get() = value >= goal
}

fun weekChallenge(e: Effort, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Challenge {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = e.done.filter { !Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate().isBefore(monday) }
    val daysIn = (today.toEpochDay() - monday.toEpochDay() + 1).toInt().coerceIn(1, 7)
    val weekSteps = e.steps.takeLast(daysIn).sum()
    val pick = Math.floorMod(monday.toEpochDay() / 7, 5L).toInt()
    return when (pick) {
        0 -> Challenge("w_workouts", "Cuatro entrenamientos", "Cualquiera cuenta: casa, gym, cardio o respirar.", "bolt", week.size.toDouble(), 4.0, "entrenamientos")
        1 -> Challenge("w_minutes", "150 minutos activos", "Lo que recomiendan a la semana. Tokkip te acompaña.", "timer", week.sumOf { it.seconds } / 60.0, 150.0, "min")
        2 -> Challenge("w_km", "10 km de cardio", "Corriendo, caminando o en bici.", "route", week.sumOf { it.meters } / 1000.0, 10.0, "km")
        3 -> Challenge("w_steps", "50,000 pasos", "Unos 7,000 al día y lo tienes.", "walk", weekSteps.toDouble(), 50_000.0, "pasos")
        else -> Challenge("w_mix", "Prueba tres cosas", "Haz al menos tres tipos de entrenamiento distintos.", "star", week.map { group(it.kind) }.toSet().size.toDouble(), 3.0, "tipos")
    }
}
