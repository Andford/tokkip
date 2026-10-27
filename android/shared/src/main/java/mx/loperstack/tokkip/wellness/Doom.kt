// Tokkip's doomscrolling indicator, shared by the phone (Bienestar → Pantalla)
// and the watch: four levels, each with its name, a line, and Tokkip's eyes.

package mx.loperstack.tokkip.wellness

/** The indicator's four faces; `eye` names a TokkipEngine.Eye. */
data class DoomLevel(val level: Int, val name: String, val line: String, val eye: String)

val DOOM_LEVELS = listOf(
    DoomLevel(0, "Tranquilo", "Vas muy bien: el teléfono no te ha atrapado hoy.", "HAPPY"),
    DoomLevel(1, "Atento", "Llevas un rato en redes. Tokkip te echa un ojo.", "WIDE"),
    DoomLevel(2, "Cansado", "Muchos minutos de scroll: tus ojos (y los de Tokkip) piden pausa.", "TIRED"),
    DoomLevel(3, "Mareado", "¡Doomscrolling! Suelta el teléfono un ratito.", "SPIRAL"),
)
