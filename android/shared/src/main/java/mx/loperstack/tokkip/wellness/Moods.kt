// The diary's five moods, each with Tokkip's face for it — shared by the
// phone's Diario and the watch's quick "¿Cómo te sientes?".

package mx.loperstack.tokkip.wellness

import mx.loperstack.tokkip.character.TokkipEngine

data class MoodLevel(val value: Int, val label: String, val color: Long, val face: TokkipEngine.Eye)

val MOODS = listOf(
    MoodLevel(5, "Genial", 0xFF4ADE80, TokkipEngine.Eye.STAR),
    MoodLevel(4, "Bien", 0xFF2DD4BF, TokkipEngine.Eye.HAPPY),
    MoodLevel(3, "Normal", 0xFFA5A9B1, TokkipEngine.Eye.OPEN),
    MoodLevel(2, "Bajón", 0xFF818CF8, TokkipEngine.Eye.SLEEPY),
    MoodLevel(1, "Mal", 0xFFF4505E, TokkipEngine.Eye.LINE),
)

fun moodOf(value: Int): MoodLevel? = MOODS.firstOrNull { it.value == value }
