// What the app keeps on the phone: the tasks, Tokkip's colour, the Pomodoro.
// SharedPreferences + JSON, read once; every change goes through here so the
// screens (which watch `state`) and the Pomodoro's alarm see the same thing.

package mx.loperstack.tokkip.data

import android.content.Context
import android.content.SharedPreferences
import mx.loperstack.tokkip.widget.Widgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.loperstack.tokkip.wellness.BreathSession
import mx.loperstack.tokkip.wellness.Phase
import mx.loperstack.tokkip.wellness.sessionsJson
import mx.loperstack.tokkip.wellness.sessionsOf
import mx.loperstack.tokkip.wellness.Pomodoro
import mx.loperstack.tokkip.wellness.Task
import mx.loperstack.tokkip.wellness.TASK_LIMIT
import mx.loperstack.tokkip.wellness.TASK_MAX_LEN
import org.json.JSONArray
import org.json.JSONObject

data class AppData(
    val tasks: List<Task> = emptyList(),
    /** ARGB; white is the PC's own Tokkip (the default), any other colour tints him. */
    val tokkipColor: Long = 0xFFF5F6F8,
    val pomodoro: Pomodoro = Pomodoro(),
    /** Breathing sessions from the phone and the watch, newest last (at most 200). */
    val sessions: List<BreathSession> = emptyList(),
    val soundOn: Boolean = true,
    /** The diary, oldest day first. */
    val logs: List<DayLog> = emptyList(),
    /** What Tokkip calls you ("" until the welcome). */
    val name: String = "",
    /** "pixel" (your wallpaper's colours, light or dark as the phone), "pixelDark", or "tokkip" (the island's). */
    val theme: String = "pixel",
    /** The welcome has been through once. */
    val onboarded: Boolean = false,
    /** Vibration on taps and navigation (phone and watch). */
    val haptics: Boolean = true,
    val hapticStrength: Float = 1f,
    /** The breathing guide also vibrates on the phone. */
    val breathHaptics: Boolean = true,
    /** A soft flute note on each breath during a session. */
    val breathFlute: Boolean = true,
    /** Nights, oldest first. */
    val sleep: List<SleepEntry> = emptyList(),
    val sleepGoal: SleepGoal = SleepGoal(),
    /** A nudge 30 min before bedtime. */
    val bedReminder: Boolean = false,
    /** Tokkip drawn flat (2D) or with the PC's gradient. */
    val tokkipFlat: Boolean = true,
    /** "auto" (the season's hat), "NONE", or an Outfit name. */
    val outfit: String = "auto",
    /** Tokkip: species, skin and accent colour. */
    val species: String = "CONEJO",
    val skin: String = "LISA",
    val accent: Long = 0xFF3F9E9A,
    /** Eyes and mouth, and cheeks: 0 = automatic. */
    val faceInk: Long = 0,
    val cheek: Long = 0,
    /** Respirar: a pattern id (BREATH_PATTERNS) or "custom", the custom rhythm and the usual length. */
    val breathPattern: String = "calma",
    val breathCustom: List<Double> = listOf(4.0, 2.0, 6.0, 0.0),
    val breathMinutes: Int = 5,
    /** Enfoque: your block lengths. */
    val pomodoroConfig: mx.loperstack.tokkip.wellness.PomodoroConfig = mx.loperstack.tokkip.wellness.PomodoroConfig(),
    val habits: List<Habit> = emptyList(),
    val notify: NotifySettings = NotifySettings(),
    /** Dinero: movements, oldest first, and your budget. */
    val money: List<MoneyEntry> = emptyList(),
    val budget: Budget = Budget(),
    /** Dinero → Pagos y deudas. */
    val debts: List<Debt> = emptyList(),
    /** Bienestar → Pantalla: your settings, a summary per day (today's kept up to date), and the last reading. */
    val screen: ScreenSettings = ScreenSettings(),
    val screenDays: List<ScreenDay> = emptyList(),
    val doom: Int = 0,
    val doomAt: Long = 0,
    /** Today's most used scroll app, and the scroll session going on (minutes) at the last reading. */
    val doomTop: String = "",
    val doomSession: Int = 0,
    /** Inicio, your way: the sections' order and the ones hidden (ui/HomeLayout.kt). */
    val homeLayout: List<String> = emptyList(),
    val homeHidden: Set<String> = emptySet(),
)

/** Tokkip's notifications (Ajustes → Notificaciones). Times in minutes of the day. */
data class NotifySettings(
    val morning: Boolean = true,
    val morningAt: Int = 8 * 60,
    val tasks: Boolean = true,
    val tasksAt: Int = 17 * 60,
    val events: Boolean = true,
    val eventLead: Int = 10,
    val night: Boolean = true,
    val nightAt: Int = 21 * 60 + 30,
    val streaks: Boolean = true,
)

fun notifyJson(n: NotifySettings): String = org.json.JSONObject()
    .put("morning", n.morning).put("morningAt", n.morningAt).put("tasks", n.tasks).put("tasksAt", n.tasksAt)
    .put("events", n.events).put("eventLead", n.eventLead).put("night", n.night).put("nightAt", n.nightAt).put("streaks", n.streaks)
    .toString()

fun notifyOf(raw: String?): NotifySettings = runCatching {
    val o = org.json.JSONObject(raw ?: return NotifySettings())
    fun t(k: String, d: Int) = o.optInt(k, d).takeIf { it in 0 until 1440 } ?: d
    NotifySettings(
        o.optBoolean("morning", true), t("morningAt", 480), o.optBoolean("tasks", true), t("tasksAt", 1020),
        o.optBoolean("events", true), o.optInt("eventLead", 10).coerceIn(0, 120), o.optBoolean("night", true), t("nightAt", 1290),
        o.optBoolean("streaks", true),
    )
}.getOrDefault(NotifySettings())

/** What Tokkip's growth and his notes read (data/Growth.kt, data/Insights.kt). */
fun AppData.routine(): Routine = Routine(
    logs = logs, habits = habits, breaths = sessions.map { it.at },
    nights = sleep.map { Night(it.night, it.minutes, it.quality) }, sleepGoalMin = sleepGoal.minutes,
    money = money, budget = budget, debts = debts, screenDays = screenDays, screenGoal = screen.goal,
)

/** The rhythm you picked, for a session of `minutes`. */
fun AppData.breathing(start: Long?, minutes: Int): mx.loperstack.tokkip.wellness.Breathing {
    val base = mx.loperstack.tokkip.wellness.Breathing(start, minutes)
    val p = mx.loperstack.tokkip.wellness.BREATH_PATTERNS.firstOrNull { it.id == breathPattern }
    return if (p != null) base.with(p)
    else base.copy(inhale = breathCustom[0], holdIn = breathCustom[1], exhale = breathCustom[2], holdOut = breathCustom[3])
}

object Store {
    private lateinit var prefs: SharedPreferences
    private lateinit var app: Context
    private val _state = MutableStateFlow(AppData())
    val state: StateFlow<AppData> = _state.asStateFlow()

    /** Told after every change (the app keeps its sound switch in step). */
    @Volatile var onChange: (() -> Unit)? = null

    /** Where your settings live (older versions used Legacy.PHONE_PREFS, moved on the first start). */
    const val PREFS = "tokkip"

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        app = context.applicationContext
        mx.loperstack.tokkip.wellness.Legacy.movePrefs(context.applicationContext, mx.loperstack.tokkip.wellness.Legacy.PHONE_PREFS, PREFS)
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Notebook.init(app)
        load()
    }

    /** After restoring a backup: read everything again, and tell the watch and widgets. */
    fun reload() {
        load()
        applyLook()
        changed()
        mx.loperstack.tokkip.wellness.TokkipNotifier.sync(app)
    }

    private fun load() {
        // Tokkip now looks like the PC's: the old violet default becomes white, once.
        if (!prefs.getBoolean("pcLook", false)) {
            val e = prefs.edit().putBoolean("pcLook", true)
            if (prefs.getLong("tokkipColor", 0xFFA78BFA) == 0xFFA78BFA) e.putLong("tokkipColor", 0xFFF5F6F8)
            e.apply()
        }
        _state.value = AppData(
            tasks = parseTasks(prefs.getString("tasks", null)),
            tokkipColor = prefs.getLong("tokkipColor", 0xFFF5F6F8),
            pomodoro = parsePomodoro(prefs.getString("pomodoro", null)),
            sessions = sessionsOf(prefs.getString("sessions", null)),
            soundOn = prefs.getBoolean("soundOn", true),
            logs = logsOf(prefs.getString("logs", null)),
            name = prefs.getString("name", "") ?: "",
            theme = mx.loperstack.tokkip.wellness.Legacy.theme(prefs.getString("theme", "pixel")) ?: "pixel",
            onboarded = prefs.getBoolean("onboarded", false),
            haptics = prefs.getBoolean("haptics", true),
            hapticStrength = prefs.getFloat("hapticStrength", 1f).coerceIn(0.2f, 1f),
            breathHaptics = prefs.getBoolean("breathHaptics", true),
            breathFlute = prefs.getBoolean("breathFlute", true),
            sleep = sleepOf(prefs.getString("sleep", null)),
            sleepGoal = SleepGoal(prefs.getInt("bedMin", 23 * 60), prefs.getInt("wakeMin", 7 * 60)),
            bedReminder = prefs.getBoolean("bedReminder", false),
            tokkipFlat = prefs.getBoolean("tokkipFlat", true),
            outfit = prefs.getString("outfit", "auto") ?: "auto",
            species = prefs.getString("species", "CONEJO") ?: "CONEJO",
            skin = prefs.getString("skin", "LISA") ?: "LISA",
            accent = prefs.getLong("accent", 0xFF3F9E9A),
            faceInk = prefs.getLong("faceInk", 0),
            cheek = prefs.getLong("cheek", 0),
            breathPattern = prefs.getString("breathPattern", "calma") ?: "calma",
            breathCustom = (prefs.getString("breathCustom", null) ?: "4,2,6,0").split(",").mapNotNull { it.toDoubleOrNull() }
                .takeIf { it.size == 4 } ?: listOf(4.0, 2.0, 6.0, 0.0),
            breathMinutes = prefs.getInt("breathMinutes", 5),
            pomodoroConfig = mx.loperstack.tokkip.wellness.PomodoroConfig(
                prefs.getInt("pFocus", 25), prefs.getInt("pBreak", 5), prefs.getInt("pLong", 15), prefs.getInt("pCycles", 4),
            ),
        )
        _state.value = _state.value.copy(habits = habitsOf(prefs.getString("habits", null)), notify = notifyOf(prefs.getString("notify", null)),
            money = moneyOf(prefs.getString("money", null)), budget = budgetOf(prefs.getString("budget", null)),
            debts = debtsOf(prefs.getString("debts", null)),
            screen = screenSettingsOf(prefs.getString("screen", null)), screenDays = screenDaysOf(prefs.getString("screenDays", null)),
            doom = prefs.getInt("doom", 0), doomAt = prefs.getLong("doomAt", 0), doomTop = prefs.getString("doomTop", "") ?: "",
            doomSession = prefs.getInt("doomSession", 0),
            homeLayout = (prefs.getString("homeLayout", "") ?: "").split(",").filter { it.isNotBlank() },
            homeHidden = (prefs.getString("homeHidden", "") ?: "").split(",").filter { it.isNotBlank() }.toSet())
        mx.loperstack.tokkip.wellness.PomodoroConfig.current = _state.value.pomodoroConfig
        mx.loperstack.tokkip.character.Haptics.strength = _state.value.hapticStrength
        mx.loperstack.tokkip.character.Haptics.enabled = _state.value.haptics
    }

    /** Every change reaches the watch and the home screen widgets. */
    private fun changed() {
        onChange?.invoke()
        WearSync.push(app)
        Widgets.refresh(app)
    }

    fun setTasks(tasks: List<Task>) {
        _state.value = _state.value.copy(tasks = tasks)
        prefs.edit().putString("tasks", tasksJson(tasks)).apply()
        changed()
    }

    fun setColor(argb: Long) {
        _state.value = _state.value.copy(tokkipColor = argb)
        prefs.edit().putLong("tokkipColor", argb).apply()
        changed()
    }

    fun setPomodoro(p: Pomodoro) {
        _state.value = _state.value.copy(pomodoro = p)
        prefs.edit().putString("pomodoro", pomodoroJson(p)).apply()
        changed()
    }

    /**
     * A session from the phone or the watch. Breathing together, both send the
     * same start: the one with the pulse (the watch's) wins.
     */
    fun addSession(session: BreathSession) {
        val same = _state.value.sessions.firstOrNull { it.at == session.at }
        // The one with the most pulse wins (the watch's readings beat the phone's relay).
        if (same != null && session.hr.size + session.rr.size <= same.hr.size + same.rr.size && (same.hrEnd > 0 || session.hrEnd <= 0)) return
        val list = (_state.value.sessions.filter { it.at != session.at } + session).sortedBy { it.at }.takeLast(200)
        _state.value = _state.value.copy(sessions = list)
        prefs.edit().putString("sessions", sessionsJson(list)).apply()
        changed()
    }

    fun setLogs(logs: List<DayLog>) {
        val moodBefore = _state.value.logs.firstOrNull { it.day == dayKey(System.currentTimeMillis()) }?.mood
        _state.value = _state.value.copy(logs = logs)
        prefs.edit().putString("logs", logsJson(logs)).apply()
        // Only today's mood leaves the phone (the watch shows it).
        if (logs.firstOrNull { it.day == dayKey(System.currentTimeMillis()) }?.mood != moodBefore) changed()
    }

    fun setName(name: String) {
        val clean = name.trim().take(30)
        _state.value = _state.value.copy(name = clean)
        prefs.edit().putString("name", clean).apply()
        changed()
    }

    fun setTheme(theme: String) {
        _state.value = _state.value.copy(theme = theme)
        prefs.edit().putString("theme", theme).apply()
    }

    fun setOnboarded(done: Boolean) {
        _state.value = _state.value.copy(onboarded = done)
        prefs.edit().putBoolean("onboarded", done).apply()
    }

    fun setHaptics(on: Boolean) {
        _state.value = _state.value.copy(haptics = on)
        prefs.edit().putBoolean("haptics", on).apply()
        changed()
    }

    fun setHapticStrength(x: Float) {
        val v = x.coerceIn(0.2f, 1f)
        _state.value = _state.value.copy(hapticStrength = v)
        prefs.edit().putFloat("hapticStrength", v).apply()
        mx.loperstack.tokkip.character.Haptics.strength = v
        changed()
    }

    fun setBreathFlute(on: Boolean) {
        _state.value = _state.value.copy(breathFlute = on)
        prefs.edit().putBoolean("breathFlute", on).apply()
    }

    fun setBreathHaptics(on: Boolean) {
        _state.value = _state.value.copy(breathHaptics = on)
        prefs.edit().putBoolean("breathHaptics", on).apply()
    }

    fun setSleep(list: List<SleepEntry>) {
        _state.value = _state.value.copy(sleep = list)
        prefs.edit().putString("sleep", sleepJson(list)).apply()
        applyLook()
        changed()
    }

    fun setSleepGoal(goal: SleepGoal, reminder: Boolean) {
        _state.value = _state.value.copy(sleepGoal = goal, bedReminder = reminder)
        prefs.edit().putInt("bedMin", goal.bedMin).putInt("wakeMin", goal.wakeMin).putBoolean("bedReminder", reminder).apply()
    }

    /** Mimos given today (the pet screen's counter). */
    fun petsToday(): Int = if (prefs.getString("petDay", "") == dayKey(System.currentTimeMillis())) prefs.getInt("petCount", 0) else 0

    fun addPet(): Int {
        val n = petsToday() + 1
        prefs.edit().putString("petDay", dayKey(System.currentTimeMillis())).putInt("petCount", n).apply()
        return n
    }

    /** The growth stage you've already celebrated (level × 100 + turns of the cycle); -1 before the first time. */
    fun growthSeen(): Int = prefs.getInt("growthSeen", -1)

    fun setGrowthSeen(v: Int) { prefs.edit().putInt("growthSeen", v).apply() }

    fun setHabits(list: List<Habit>) {
        _state.value = _state.value.copy(habits = list)
        prefs.edit().putString("habits", habitsJson(list)).apply()
        Widgets.refresh(app)
        mx.loperstack.tokkip.wellness.TokkipNotifier.sync(app)
    }

    fun setMoney(list: List<MoneyEntry>) {
        val clean = list.sortedBy { it.at }.takeLast(MONEY_LIMIT)
        _state.value = _state.value.copy(money = clean)
        prefs.edit().putString("money", moneyJson(clean)).apply()
        Widgets.refresh(app)
    }

    fun setBudget(b: Budget) {
        _state.value = _state.value.copy(budget = b)
        prefs.edit().putString("budget", budgetJson(b)).apply()
        Widgets.refresh(app)
    }

    fun setDebts(list: List<Debt>) {
        _state.value = _state.value.copy(debts = list)
        prefs.edit().putString("debts", debtsJson(list)).apply()
        Widgets.refresh(app)
        mx.loperstack.tokkip.wellness.TokkipNotifier.sync(app)
    }

    /** Pays a debt's due date: marks it and records the expense. */
    fun payDebt(id: String, due: String, now: Long = System.currentTimeMillis()) {
        val d = _state.value.debts.firstOrNull { it.id == id } ?: return
        if (due in d.paidDues) return
        val (paid, entry) = d.pay(due, now, java.util.UUID.randomUUID().toString().take(12))
        setMoney(_state.value.money + entry)
        setDebts(_state.value.debts.map { if (it.id == id) paid else it })
    }

    fun setHome(layout: List<String>, hidden: Set<String>) {
        _state.value = _state.value.copy(homeLayout = layout, homeHidden = hidden)
        prefs.edit().putString("homeLayout", layout.joinToString(",")).putString("homeHidden", hidden.joinToString(",")).apply()
    }

    fun setScreen(sc: ScreenSettings) {
        _state.value = _state.value.copy(screen = sc)
        prefs.edit().putString("screen", screenSettingsJson(sc)).apply()
        mx.loperstack.tokkip.wellness.TokkipNotifier.sync(app)
    }

    /** The last reading: today's line in the days, and Tokkip's level for Inicio and the watch. */
    fun saveScreenNow(r: ScreenNow) {
        val t = r.today
        val day = ScreenDay(dayKey(r.at), t.totalMin, t.scrollMin, t.lateMin, t.unlocks)
        val days = (_state.value.screenDays.filter { it.day != day.day } + day).sortedBy { it.day }.takeLast(120)
        val sc = _state.value.screen
        val top = t.apps.firstOrNull { it.kind in sc.scrollKinds }?.label ?: ""
        _state.value = _state.value.copy(screenDays = days, doom = r.level, doomAt = r.at, doomTop = top, doomSession = t.sessionMin)
        prefs.edit().putString("screenDays", screenDaysJson(days)).putInt("doom", r.level).putLong("doomAt", r.at)
            .putString("doomTop", top).putInt("doomSession", t.sessionMin).apply()
    }

    fun addScreenDays(list: List<ScreenDay>) {
        if (list.isEmpty()) return
        val keys = list.map { it.day }.toSet()
        val days = (_state.value.screenDays.filter { it.day !in keys } + list).sortedBy { it.day }.takeLast(120)
        _state.value = _state.value.copy(screenDays = days)
        prefs.edit().putString("screenDays", screenDaysJson(days)).apply()
    }

    /** The scroll session you were last nudged about (its start), so each long session gets one nudge. */
    fun nudgedAt(): Long = prefs.getLong("screenNudged", 0)
    fun setNudgedAt(v: Long) { prefs.edit().putLong("screenNudged", v).apply() }

    fun setNotify(n: NotifySettings) {
        _state.value = _state.value.copy(notify = n)
        prefs.edit().putString("notify", notifyJson(n)).apply()
        mx.loperstack.tokkip.wellness.TokkipNotifier.sync(app)
    }

    fun setBreath(pattern: String, custom: List<Double>, minutes: Int) {
        _state.value = _state.value.copy(breathPattern = pattern, breathCustom = custom, breathMinutes = minutes)
        prefs.edit().putString("breathPattern", pattern).putString("breathCustom", custom.joinToString(",")).putInt("breathMinutes", minutes).apply()
    }

    fun setPomodoroConfig(c: mx.loperstack.tokkip.wellness.PomodoroConfig) {
        _state.value = _state.value.copy(pomodoroConfig = c)
        mx.loperstack.tokkip.wellness.PomodoroConfig.current = c
        prefs.edit().putInt("pFocus", c.focusMin).putInt("pBreak", c.breakMin).putInt("pLong", c.longBreakMin).putInt("pCycles", c.cyclesBeforeLong).apply()
        changed()
    }

    fun setOutfit(selection: String) {
        _state.value = _state.value.copy(outfit = selection)
        prefs.edit().putString("outfit", selection).apply()
        applyLook()
        changed()
    }

    /** Tokkip's look everywhere: flat or not, today's outfit, last night's rest. */
    fun applyLook() {
        val d = _state.value
        mx.loperstack.tokkip.character.TokkipStyle.flat = d.tokkipFlat
        mx.loperstack.tokkip.character.TokkipStyle.outfit = mx.loperstack.tokkip.character.Outfit.resolve(d.outfit)
        mx.loperstack.tokkip.character.TokkipStyle.rest = restLevel(d.sleep, System.currentTimeMillis())
        mx.loperstack.tokkip.character.TokkipStyle.species = mx.loperstack.tokkip.character.Species.of(d.species)
        mx.loperstack.tokkip.character.TokkipStyle.skin = mx.loperstack.tokkip.character.Skin.of(d.skin)
        mx.loperstack.tokkip.character.TokkipStyle.accent = androidx.compose.ui.graphics.Color(d.accent)
        mx.loperstack.tokkip.character.TokkipStyle.ink = if (d.faceInk == 0L) androidx.compose.ui.graphics.Color.Unspecified else androidx.compose.ui.graphics.Color(d.faceInk)
        mx.loperstack.tokkip.character.TokkipStyle.cheek = if (d.cheek == 0L) androidx.compose.ui.graphics.Color.Unspecified else androidx.compose.ui.graphics.Color(d.cheek)
    }

    /** Eyes/mouth and cheek colours; 0 brings back the automatic ones. */
    fun setFaceColors(faceInk: Long = _state.value.faceInk, cheek: Long = _state.value.cheek) {
        _state.value = _state.value.copy(faceInk = faceInk, cheek = cheek)
        prefs.edit().putLong("faceInk", faceInk).putLong("cheek", cheek).apply()
        applyLook()
        changed()
    }

    /** Tokkip's look: species (also switches the app icon), skin and accent. */
    fun setTokkip(species: String = _state.value.species, skin: String = _state.value.skin, accent: Long = _state.value.accent) {
        val iconChanged = species != _state.value.species
        _state.value = _state.value.copy(species = species, skin = skin, accent = accent)
        prefs.edit().putString("species", species).putString("skin", skin).putLong("accent", accent).apply()
        applyLook()
        changed()
        if (iconChanged) AppIcon.apply(app, species)
    }

    fun setTokkipFlat(flat: Boolean) {
        _state.value = _state.value.copy(tokkipFlat = flat)
        prefs.edit().putBoolean("tokkipFlat", flat).apply()
        mx.loperstack.tokkip.character.TokkipStyle.flat = flat
        changed()
    }

    fun setSound(on: Boolean) {
        _state.value = _state.value.copy(soundOn = on)
        prefs.edit().putBoolean("soundOn", on).apply()
        changed()
    }
}

// ── JSON ──────────────────────────────────────────────────────────────────────

fun tasksJson(tasks: List<Task>): String = JSONArray().apply {
    for (t in tasks) put(JSONObject().put("id", t.id).put("text", t.text).put("done", t.done).put("createdAt", t.createdAt).put("remote", t.remote))
}.toString()

/** Only well-formed tasks survive a broken or old store. */
fun parseTasks(raw: String?): List<Task> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id", "")
            val text = o.optString("text", "").trim()
            if (id.isEmpty() || text.isEmpty()) null
            else Task(id, text.take(TASK_MAX_LEN), o.optBoolean("done", false), o.optLong("createdAt", 0), o.optBoolean("remote", false))
        }.takeLast(TASK_LIMIT)
    } catch (_: Exception) {
        emptyList()
    }
}

fun pomodoroJson(p: Pomodoro): String = JSONObject()
    .put("phase", p.phase.name)
    .put("endsAt", p.endsAt ?: JSONObject.NULL)
    .put("pausedLeft", p.pausedLeft ?: JSONObject.NULL)
    .put("completed", p.completed)
    .put("today", p.today)
    .put("day", p.day)
    .toString()

fun parsePomodoro(raw: String?): Pomodoro {
    if (raw.isNullOrBlank()) return Pomodoro()
    return try {
        val o = JSONObject(raw)
        val phase = runCatching { Phase.valueOf(o.optString("phase", "IDLE")) }.getOrDefault(Phase.IDLE)
        Pomodoro(
            phase = phase,
            endsAt = if (phase == Phase.IDLE || o.isNull("endsAt")) null else o.optLong("endsAt"),
            pausedLeft = if (phase == Phase.IDLE || o.isNull("pausedLeft")) null else o.optInt("pausedLeft"),
            completed = o.optInt("completed", 0).coerceAtLeast(0),
            today = o.optInt("today", 0).coerceAtLeast(0),
            day = o.optString("day", ""),
        )
    } catch (_: Exception) {
        Pomodoro()
    }
}
