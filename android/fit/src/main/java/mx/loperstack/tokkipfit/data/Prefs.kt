// What Fit remembers: your goals, Tokkip's species and colour, and the sounds.
// SharedPreferences "fit" (a contract: renaming a key needs a migration).

package mx.loperstack.tokkipfit.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Species
import mx.loperstack.tokkip.character.TokkipStyle

object Prefs {
    var goals by mutableStateOf(Goals())
    var color by mutableLongStateOf(0xFFF5F6F8)
    var sounds by mutableStateOf(true)
    private var ctx: Context? = null

    fun init(context: Context) {
        ctx = context.applicationContext
        val p = context.getSharedPreferences("fit", Context.MODE_PRIVATE)
        goals = Goals(p.getLong("goal_steps", 8_000), p.getLong("goal_minutes", 30), p.getLong("goal_kcal", 400)).clean()
        color = p.getLong("color", 0xFFF5F6F8)
        sounds = p.getBoolean("sounds", true)
        TokkipStyle.species = Species.of(p.getString("species", null))
        Sounds.enabled = sounds
    }

    fun set(goals: Goals = this.goals, color: Long = this.color, sounds: Boolean = this.sounds, species: Species = TokkipStyle.species) {
        this.goals = goals.clean(); this.color = color; this.sounds = sounds; TokkipStyle.species = species
        Sounds.enabled = sounds
        ctx?.getSharedPreferences("fit", Context.MODE_PRIVATE)?.edit()
            ?.putLong("goal_steps", this.goals.steps)?.putLong("goal_minutes", this.goals.minutes)?.putLong("goal_kcal", this.goals.kcal)
            ?.putLong("color", color)?.putBoolean("sounds", sounds)?.putString("species", species.name)?.apply()
    }
}
