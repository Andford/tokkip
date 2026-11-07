// The morning summary: after you turn off a wake-up alarm, the alarm screen
// becomes your morning — today's weather (Pixel style), your events and your
// pending tasks — and, if you like, Tokkip reads it to you. Two switches in
// Sueño → Alarmas: show it, and read it aloud.

package mx.loperstack.tokkip.data

import android.content.Context

object MorningBrief {
    private fun prefs(context: Context) = context.getSharedPreferences(Store.PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("resumenMatinal", true)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("resumenMatinal", on).apply()

    fun aloud(context: Context) = prefs(context).getBoolean("resumenEnVoz", true)
    fun setAloud(context: Context, on: Boolean) = prefs(context).edit().putBoolean("resumenEnVoz", on).apply()

    /** One event as it's said: "la junta a las 10" or "el cumpleaños de Ana, todo el día". */
    data class Item(val title: String, val time: String?)

    /**
     * What Tokkip says: a good morning, the weather if she has it, then today's
     * events and tasks, short. Times go as "9:30" (the voice reads them as
     * "9 y media").
     */
    fun speech(name: String, weather: Weather.Now?, events: List<Item>, tasks: List<String>): String = buildString {
        append(if (name.isBlank()) "¡Buenos días!" else "¡Buenos días, $name!")
        if (weather != null) {
            append(" Afuera hay ${weather.temp} grados y está ${sky(weather.code)}; ")
            append("la máxima será de ${weather.max} y la mínima de ${weather.min}.")
            if (weather.rain >= 40) append(" Hay ${weather.rain} por ciento de probabilidad de lluvia, así que lleva paraguas.")
        }
        when (events.size) {
            0 -> append(" Hoy no tienes eventos en tu agenda.")
            1 -> append(" Hoy tienes un evento: ${said(events[0])}.")
            else -> {
                append(" Hoy tienes ${events.size} eventos: ")
                append(events.take(3).joinToString(", ") { said(it) })
                if (events.size > 3) append(", y ${events.size - 3} más")
                append(".")
            }
        }
        when (tasks.size) {
            0 -> append(" Y no tienes pendientes. ¡Qué buen día!")
            1 -> append(" Y un pendiente: ${tasks[0]}.")
            else -> append(" Y ${tasks.size} pendientes, empezando por ${tasks[0]}.")
        }
        append(" Que tengas un gran día.")
    }

    /** The sky as you'd say it after "está": despejado, nublado, lloviendo… */
    fun sky(code: Int): String = when (code) {
        0 -> "despejado"
        1 -> "casi despejado"
        2 -> "parcialmente nublado"
        45, 48 -> "con niebla"
        in 51..57 -> "lloviznando"
        in 61..67, in 80..82 -> "lloviendo"
        in 71..77, 85, 86 -> "nevando"
        in 95..99 -> "con tormenta"
        else -> "nublado"
    }

    private fun said(e: Item) = if (e.time == null) "${e.title}, todo el día" else "${e.title} a las ${e.time}"
}
