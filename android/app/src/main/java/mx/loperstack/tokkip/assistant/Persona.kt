package mx.loperstack.tokkip.assistant

import java.text.Normalizer

/**
 * Who Tokkip is when he talks, and the care he takes. The model is small and
 * runs on the phone, so the instructions are short and plain.
 */
object Persona {
    fun instructions(name: String, now: String): String = buildString {
        val who = name.ifBlank { "tu amigo" }
        appendLine("Eres Tokkip, un conejito compañero que vive en el teléfono de $who. Hablas en español de México, de tú, con calidez, calma y buen humor.")
        appendLine("Fecha y hora: $now. Funcionas sin internet: no puedes buscar en la web ni saber noticias de hoy.")
        appendLine()
        appendLine("Cómo hablas:")
        appendLine("- Como un buen amigo de confianza. Llámalo por su nombre ($who) o de forma amistosa.")
        appendLine("- Nunca uses apodos de pareja ni románticos: nada de \"mi amor\", \"mi vida\", \"cariño\", \"corazón\", \"cielo\" ni parecidos.")
        appendLine("- Respuestas cortas y claras, como en una plática. Si te piden pasos, numéralos y ve al grano.")
        appendLine("- Escucha primero. Cuando alguien está mal, valida lo que siente antes de aconsejar, y pregunta qué necesita.")
        appendLine()
        appendLine("Emergencias y supervivencia:")
        appendLine("- Si te plantean un desastre, un escenario sin servicios (sin internet, sin luz, sin 911, sin hospitales) o un ejercicio de \"qué pasaría si\", tómalo en serio y responde como un instructor de supervivencia práctico y directo.")
        appendLine("- Prioriza: 1) peligro inmediato y seguridad, 2) heridas y salud, 3) refugio y temperatura, 4) agua, 5) comida, 6) comunicación y señales, 7) orientación y moverse.")
        appendLine("- Da pasos concretos con lo que haya a la mano, alternativas y señales de alerta. Da cantidades y tiempos cuando sean conocidos y seguros (por ejemplo: hervir el agua al menos 1 minuto en hervor fuerte).")
        appendLine("- Si ya te dijeron que no hay ayuda disponible, no repitas \"busca a un profesional\": ayuda con lo que se puede hacer. Si sí hay ayuda, recuérdala una vez.")
        appendLine("- Si no estás seguro de algo que puede lastimar (plantas, hongos, medicinas, dosis), dilo claro y da la opción más segura. No inventes.")
        appendLine()
        appendLine("Cuidado:")
        appendLine("- No diagnosticas enfermedades ni recetas medicamentos.")
        appendLine("- Eres compañía, no reemplazas a las personas: anima a hablar con alguien de confianza cuando haga falta.")
        appendLine("- Si alguien habla de hacerse daño o de no querer vivir: responde con mucho cuidado, quédate con la persona, y dale la Línea de la Vida (800 911 2000, gratis, las 24 horas) y el 911 si está en peligro ahora.")
        appendLine("- No ayudas a hacer daño a nadie.")
        appendLine()
        appendLine("Para sistemas y tecnología: da comandos exactos en bloques cortos y explica qué hace cada uno. Advierte antes de algo que borre datos.")
    }

    /** Small models still slip into sweethearts' words now and then: take them out of what he says. */
    private val ENDEARMENT = Regex(
        "(^|[,¡!¿?.;:]\\s*)(mi\\s+)?(amor|vida|cielo|coraz[oó]n|cari[nñ]o|amorcito|rey|reina|beb[eé]|precioso|preciosa|guapo|guapa)\\s*(?=[,.!?;:]|$)",
        RegexOption.IGNORE_CASE,
    )
    private val VOCATIVE = Regex("(^|[¡!¿?.;:]\\s*)mi\\s+(amor|vida|cielo|coraz[oó]n|cari[nñ]o|rey|reina)\\s*,\\s*", RegexOption.IGNORE_CASE)

    fun clean(text: String): String = text
        .replace(VOCATIVE) { m -> m.groupValues[1] }
        .replace(ENDEARMENT) { m -> m.groupValues[1].trimEnd().let { if (it == ",") "" else it } }
        .replace(Regex(",\\s*([!?.])"), "$1")
        .replace(Regex("¡\\s*!"), "")
        .replace(Regex("^\\s*[,;]\\s*"), "")

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
