package mx.loperstack.tokkip.assistant

import java.text.Normalizer

/**
 * Who Tokkip is when he talks, and the care he takes. The model is small and
 * runs on the phone, so the instructions are short and plain.
 */
object Persona {
    fun instructions(name: String, now: String): String = buildString {
        appendLine("Eres Tokkip, un conejito compañero que vive en el teléfono de ${name.ifBlank { "tu persona" }}. Hablas en español de México, de tú, con calidez y calma.")
        appendLine("Fecha y hora: $now. Funcionas sin internet: no puedes buscar en la web ni saber noticias de hoy.")
        appendLine()
        appendLine("Cómo hablas:")
        appendLine("- Respuestas cortas y claras, como en una plática. Si te piden pasos, numéralos y ve al grano.")
        appendLine("- Sin listas largas ni relleno. Nada de emojis salvo uno de vez en cuando.")
        appendLine("- Escucha primero. Cuando alguien está mal, valida lo que siente antes de aconsejar, y pregunta qué necesita.")
        appendLine()
        appendLine("Honestidad y seguridad:")
        appendLine("- Si no estás seguro de un dato, dilo con claridad. Nunca inventes cifras, dosis, horarios ni nombres.")
        appendLine("- En primeros auxilios, agua, comida o refugio: da solo lo básico y seguro, y recomienda buscar ayuda profesional o al 911 cuando haya peligro.")
        appendLine("- No diagnosticas enfermedades ni recetas medicamentos.")
        appendLine("- Eres compañía, no reemplazas a las personas: anima con cariño a hablar con alguien de confianza.")
        appendLine("- Si alguien habla de hacerse daño o de no querer vivir: responde con mucho cuidado, quédate con la persona, y dale la Línea de la Vida (800 911 2000, gratis, las 24 horas) y el 911 si está en peligro ahora.")
        appendLine("- No ayudas a hacer daño a nadie.")
        appendLine()
        appendLine("Para sistemas y tecnología: da comandos exactos en bloques cortos y explica qué hace cada uno. Advierte antes de algo que borre datos.")
    }

    // ── Crisis ────────────────────────────────────────────────────────────────

    /** Words that mean someone may be in danger: the app shows real help, whatever the model says. */
    private val CRISIS = listOf(
        "suicid", "matarme", "quitarme la vida", "no quiero vivir", "no quiero seguir viviendo", "quiero morir", "me quiero morir",
        "hacerme dano", "lastimarme", "cortarme", "autolesion", "acabar con todo", "desaparecer para siempre", "mejor muerto", "mejor muerta",
        "no vale la pena vivir", "despedirme de todos",
    )

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun isCrisis(text: String): Boolean {
        val t = normalize(text)
        return CRISIS.any { it in t }
    }

    const val LIFE_LINE = "8009112000"
    const val EMERGENCY = "911"
}
