// What Fit remembers: your goals, how Tokkip looks (his own here, or the same
// as in the Tokkip app), the sounds, and the workouts you've done.
// SharedPreferences "fit" (a contract: renaming a key needs a migration; 0.1's
// "color" and "species" are read once into "look").

package mx.loperstack.tokkipfit.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mx.loperstack.tokkip.character.Look
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.fit.Done

object Prefs {
    var goals by mutableStateOf(Goals())
    /** How Tokkip looks in Fit (his own, or the Tokkip app's while `follow`). */
    var look by mutableStateOf(Look())
    /** Wear the Tokkip app's look (when it's installed). */
    var follow by mutableStateOf(true)
    var sounds by mutableStateOf(true)
    val color: Long get() = look.color
    private var ctx: Context? = null

    private fun prefs(c: Context) = c.getSharedPreferences("fit", Context.MODE_PRIVATE)

    fun init(context: Context) {
        ctx = context.applicationContext
        val p = prefs(context)
        goals = Goals(p.getLong("goal_steps", 8_000), p.getLong("goal_minutes", 30), p.getLong("goal_kcal", 400)).clean()
        look = Look.parse(p.getString("look", null))
            ?: Look(color = p.getLong("color", 0xFFF5F6F8), species = p.getString("species", null) ?: "CONEJO")
        follow = p.getBoolean("follow", true)
        sounds = p.getBoolean("sounds", true)
        Sounds.enabled = sounds
        refreshLook()
    }

    /** Takes the Tokkip app's look again, if Fit follows it (on start and each time Fit comes back). */
    fun refreshLook() {
        val c = ctx ?: return
        if (follow) Look.fromTokkip(c)?.let { look = it }
        look.apply()
        Wrist.pushLook(c, look)
    }

    /** Whether the Tokkip app is there to follow. */
    fun tokkipAround(): Boolean = ctx?.let { Look.fromTokkip(it) } != null

    fun set(goals: Goals = this.goals, sounds: Boolean = this.sounds) {
        this.goals = goals.clean(); this.sounds = sounds
        Sounds.enabled = sounds
        ctx?.let { prefs(it).edit().putLong("goal_steps", this.goals.steps).putLong("goal_minutes", this.goals.minutes).putLong("goal_kcal", this.goals.kcal).putBoolean("sounds", sounds).apply() }
    }

    /** A change of look made here: Fit keeps its own from now on. */
    fun changeLook(l: Look) {
        look = l; follow = false
        l.apply()
        ctx?.let { prefs(it).edit().putString("look", l.json()).putBoolean("follow", false).apply(); Wrist.pushLook(it, l) }
    }

    fun followTokkip(on: Boolean) {
        follow = on
        ctx?.let { prefs(it).edit().putBoolean("follow", on).apply() }
        refreshLook()
    }

    /** Every gym set, oldest first (the last 4,000): records and the weight to suggest come from here. */
    fun gymSets(): List<mx.loperstack.tokkip.fit.GymSet> = ctx?.let { mx.loperstack.tokkip.fit.GymSet.list(prefs(it).getString("gym_sets", null)) } ?: emptyList()

    fun addGymSet(s: mx.loperstack.tokkip.fit.GymSet) {
        val c = ctx ?: return
        val list = (gymSets() + s).takeLast(4_000)
        prefs(c).edit().putString("gym_sets", mx.loperstack.tokkip.fit.GymSet.json(list)).apply()
    }

    /** Records beaten so far (for the "Récord" badge). */
    var recordsBeaten: Int
        get() = ctx?.let { prefs(it).getInt("records_beaten", 0) } ?: 0
        set(v) { ctx?.let { prefs(it).edit().putInt("records_beaten", v).apply() } }

    /** The routes of the last cardio sessions, by when they ended (for their summaries). */
    fun saveRoute(at: Long, points: List<mx.loperstack.tokkip.fit.Point>) {
        val c = ctx ?: return
        val a = org.json.JSONArray()
        // Every other point past 2,000 is plenty for a drawing.
        val step = if (points.size > 2_000) points.size / 2_000 + 1 else 1
        points.filterIndexed { i, _ -> i % step == 0 }.forEach { a.put(org.json.JSONArray().put(it.lat).put(it.lon)) }
        val p = prefs(c)
        val keys = (p.getString("routes", "") ?: "").split(',').filter { it.isNotBlank() }
        val keep = (keys + "$at").takeLast(20)
        val e = p.edit()
        for (k in keys - keep.toSet()) e.remove("route_$k")
        e.putString("route_$at", a.toString()).putString("routes", keep.joinToString(",")).apply()
    }

    fun route(at: Long): List<Pair<Double, Double>> = ctx?.let { c ->
        runCatching {
            val a = org.json.JSONArray(prefs(c).getString("route_$at", null) ?: return@let emptyList())
            (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getDouble(0) to p.getDouble(1) } }
        }.getOrDefault(emptyList())
    } ?: emptyList()

    /** Hoy as last read, for the widgets and the watch (they can't always ask Health Connect). */
    fun saveToday(json: String) { ctx?.let { prefs(it).edit().putString("today", json).apply() } }
    fun savedToday(): String? = ctx?.let { prefs(it).getString("today", null) }

    /** The workouts done in Fit, newest first (at most 200): they feed the history and Hoy. */
    fun workouts(): List<Done> = ctx?.let { Done.list(prefs(it).getString("done", null)) } ?: emptyList()

    fun addWorkout(d: Done) {
        val c = ctx ?: return
        val list = (listOf(d) + workouts()).take(200)
        prefs(c).edit().putString("done", Done.json(list)).apply()
    }
}
