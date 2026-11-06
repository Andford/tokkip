package mx.loperstack.tokkip.assistant

import java.text.Normalizer
import java.util.Calendar

/**
 * Times and days as Tokkip's tools receive them from the model. Kept apart
 * from TokkipTools so it can be tested without the model's library.
 */
object Spoken {
    /** "6:30", "06:30", "18:00", "7" into minutes after midnight. */
    fun minuteOf(text: String): Int? {
        val m = Regex("""^\s*(\d{1,2})(?:\s*[:.h]\s*(\d{2}))?\s*$""").find(text) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    /** "lunes, miércoles", "entre semana", "diario"… into Calendar days. */
    fun daysOf(text: String): Set<Int> {
        val t = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        if (t.isBlank()) return emptySet()
        if ("diario" in t || "todos los dias" in t || "cada dia" in t) return (1..7).toSet()
        val out = mutableSetOf<Int>()
        if ("entre semana" in t || "laborable" in t) out += listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        if ("fin de semana" in t || "fines de semana" in t) out += listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        val names = mapOf(
            "lunes" to Calendar.MONDAY, "martes" to Calendar.TUESDAY, "miercoles" to Calendar.WEDNESDAY, "jueves" to Calendar.THURSDAY,
            "viernes" to Calendar.FRIDAY, "sabado" to Calendar.SATURDAY, "domingo" to Calendar.SUNDAY,
        )
        for ((n, d) in names) if (n in t) out += d
        return out
    }

    /** Lowercase, no accents, single spaces: "Mamá  Lupe" → "mama lupe". */
    fun plain(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9ñ ]"), " ").replace(Regex("\\s+"), " ").trim()

    /**
     * The contact names that best fit what you said: the exact name if there
     * is one; else the names where a word starts with it; else the names that
     * contain it.
     */
    fun bestNames(said: String, names: List<String>): List<String> {
        val want = plain(said)
        if (want.isEmpty()) return emptyList()
        val all = names.distinct().map { it to plain(it) }
        val exact = all.filter { it.second == want }
        if (exact.isNotEmpty()) return exact.map { it.first }
        val starts = all.filter { (_, p) -> p.startsWith(want) || p.split(" ").any { it.startsWith(want) } || " $p".contains(" $want ") }
        if (starts.isNotEmpty()) return starts.map { it.first }
        return all.filter { it.second.contains(want) }.map { it.first }
    }
}
