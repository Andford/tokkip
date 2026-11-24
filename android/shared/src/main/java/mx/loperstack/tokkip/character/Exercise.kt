// Tokkip's workout moves (Tokkip Fit): he does them with you, in a loop,
// drawn by TokkipEngine.exercise(). Fit's own, not part of the moods and acts
// Tokkip PC shares.

package mx.loperstack.tokkip.character

enum class Exercise(val label: String) {
    SQUAT("Sentadillas"),
    JUMP("Saltos de tijera"),
    PLANK("Plancha"),
    RUN("Rodillas arriba"),
    PUNCH("Golpes al aire"),
    STRETCH("Estiramiento"),
    LIFT("Levantar"),
    REST("Descanso"),
}
