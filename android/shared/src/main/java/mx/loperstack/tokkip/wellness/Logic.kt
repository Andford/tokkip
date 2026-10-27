// The Bienestar rules, ported from the PC's windows/src/core/wellness.ts so the
// phone and the island behave the same: tasks, the Pomodoro and the breath.
// Pure Kotlin, no Android: tested in app/src/test.

package mx.loperstack.tokkip.wellness

import java.util.Calendar
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// ── Tasks ─────────────────────────────────────────────────────────────────────

/** `remote`: a Google Tasks task (its id is Google's), as on the PC. */
data class Task(val id: String, val text: String, val done: Boolean, val createdAt: Long, val remote: Boolean = false)

const val TASK_MAX_LEN = 200
const val TASK_LIMIT = 100

fun addTask(tasks: List<Task>, text: String, now: Long, id: String): List<Task> {
    val clean = text.replace(Regex("\\s+"), " ").trim().take(TASK_MAX_LEN)
    if (clean.isEmpty()) return tasks
    val next = (tasks + Task(id, clean, false, now)).toMutableList()
    while (next.size > TASK_LIMIT) {
        val i = next.indexOfFirst { it.done }
        if (i < 0) break
        next.removeAt(i)
    }
    return next
}

fun toggleTask(tasks: List<Task>, id: String) = tasks.map { if (it.id == id) it.copy(done = !it.done) else it }
fun removeTask(tasks: List<Task>, id: String) = tasks.filter { it.id != id }
fun clearDone(tasks: List<Task>) = tasks.filter { !it.done }
fun orderedTasks(tasks: List<Task>) = tasks.filter { !it.done } + tasks.filter { it.done }
fun pendingCount(tasks: List<Task>) = tasks.count { !it.done }

// ── Pomodoro ──────────────────────────────────────────────────────────────────

enum class Phase { IDLE, FOCUS, BREAK, LONG_BREAK }

data class PomodoroConfig(val focusMin: Int = 25, val breakMin: Int = 5, val longBreakMin: Int = 15, val cyclesBeforeLong: Int = 4) {
    companion object {
        /** Yours (Enfoque → personalizar); every Pomodoro function uses it by default. */
        @Volatile var current = PomodoroConfig()
    }
}

data class Pomodoro(
    val phase: Phase = Phase.IDLE,
    /** Unix ms the running phase ends; null while paused or idle. */
    val endsAt: Long? = null,
    /** Seconds left while paused. */
    val pausedLeft: Int? = null,
    /** Focus blocks finished since the last long break. */
    val completed: Int = 0,
    /** Focus blocks finished today. */
    val today: Int = 0,
    /** The day `today` counts, yyyy-MM-dd (local). */
    val day: String = "",
)

enum class PomodoroEvent { FOCUS_DONE, BREAK_DONE }

fun dayKey(nowMs: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = nowMs }
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

fun phaseMinutes(phase: Phase, cfg: PomodoroConfig = PomodoroConfig.current) = when (phase) {
    Phase.FOCUS, Phase.IDLE -> cfg.focusMin
    Phase.BREAK -> cfg.breakMin
    Phase.LONG_BREAK -> cfg.longBreakMin
}

fun Pomodoro.isRunning() = phase != Phase.IDLE && endsAt != null && pausedLeft == null
fun Pomodoro.isPaused() = phase != Phase.IDLE && pausedLeft != null

/** Seconds left in the phase (the full focus block while idle). */
fun Pomodoro.left(nowMs: Long, cfg: PomodoroConfig = PomodoroConfig.current): Int = when {
    phase == Phase.IDLE -> cfg.focusMin * 60
    pausedLeft != null -> pausedLeft
    endsAt == null -> phaseMinutes(phase, cfg) * 60
    else -> max(0, ceil((endsAt - nowMs) / 1000.0).toInt())
}

/** 0…1 of the phase gone by. */
fun Pomodoro.progress(nowMs: Long, cfg: PomodoroConfig = PomodoroConfig.current): Float {
    if (phase == Phase.IDLE) return 0f
    val total = phaseMinutes(phase, cfg) * 60f
    return (1f - left(nowMs, cfg) / total).coerceIn(0f, 1f)
}

private fun Pomodoro.withDay(nowMs: Long): Pomodoro {
    val d = dayKey(nowMs)
    return if (day == d) this else copy(day = d, today = 0)
}

fun Pomodoro.startFocus(nowMs: Long, cfg: PomodoroConfig = PomodoroConfig.current) =
    withDay(nowMs).copy(phase = Phase.FOCUS, endsAt = nowMs + cfg.focusMin * 60_000L, pausedLeft = null)

fun Pomodoro.pause(nowMs: Long, cfg: PomodoroConfig = PomodoroConfig.current) =
    if (!isRunning()) this else copy(pausedLeft = left(nowMs, cfg), endsAt = null)

fun Pomodoro.resume(nowMs: Long) =
    if (!isPaused()) this else copy(endsAt = nowMs + (pausedLeft ?: 0) * 1000L, pausedLeft = null)

fun Pomodoro.reset() = copy(phase = Phase.IDLE, endsAt = null, pausedLeft = null, completed = 0)

/**
 * Ends the phase now: a focus block leads to a break — a long one every
 * `cyclesBeforeLong` — and a break back to focus. Only a block that ran out on
 * its own (`counted`) counts as done.
 */
fun Pomodoro.finishPhase(nowMs: Long, cfg: PomodoroConfig = PomodoroConfig.current, counted: Boolean = true): Pair<Pomodoro, PomodoroEvent?> {
    val base = withDay(nowMs)
    return when (phase) {
        Phase.FOCUS -> {
            val completed = if (counted) base.completed + 1 else base.completed
            val long = counted && completed >= cfg.cyclesBeforeLong
            val next = if (long) Phase.LONG_BREAK else Phase.BREAK
            base.copy(
                phase = next,
                completed = if (long) 0 else completed,
                today = if (counted) base.today + 1 else base.today,
                endsAt = nowMs + phaseMinutes(next, cfg) * 60_000L,
                pausedLeft = null,
            ) to PomodoroEvent.FOCUS_DONE
        }
        Phase.BREAK, Phase.LONG_BREAK ->
            base.copy(phase = Phase.FOCUS, endsAt = nowMs + cfg.focusMin * 60_000L, pausedLeft = null) to PomodoroEvent.BREAK_DONE
        Phase.IDLE -> this to null
    }
}

/** The alarm's job: a running phase whose time is up ends. */
fun Pomodoro.tick(nowMs: Long, cfg: PomodoroConfig = PomodoroConfig.current): Pair<Pomodoro, PomodoroEvent?> =
    if (!isRunning() || (endsAt ?: 0) > nowMs) this to null else finishPhase(nowMs, cfg)

// ── Breathing ─────────────────────────────────────────────────────────────────

val BREATHE_MINUTES = listOf(1, 5, 10)
const val INHALE_S = 4.0
const val EXHALE_S = 6.0

/** A breathing rhythm: in, hold full, out, hold empty (seconds). */
data class BreathPattern(val id: String, val name: String, val inhale: Double, val holdIn: Double, val exhale: Double, val holdOut: Double, val note: String)

val BREATH_PATTERNS = listOf(
    BreathPattern("calma", "Calma 4·6", 4.0, 0.0, 6.0, 0.0, "Exhalar más largo baja el pulso"),
    BreathPattern("478", "4·7·8 para dormir", 4.0, 7.0, 8.0, 0.0, "El clásico para conciliar el sueño"),
    BreathPattern("caja", "Caja 4·4·4·4", 4.0, 4.0, 4.0, 4.0, "Para recuperar el enfoque"),
    BreathPattern("coherencia", "Coherencia 5·5", 5.0, 0.0, 5.0, 0.0, "Seis respiraciones por minuto"),
)

enum class BreathPhase { IN, HOLD_IN, OUT, HOLD_OUT }

data class Breathing(
    val startedAt: Long? = null,
    val minutes: Int = 1,
    val inhale: Double = INHALE_S,
    val holdIn: Double = 0.0,
    val exhale: Double = EXHALE_S,
    val holdOut: Double = 0.0,
) {
    val cycle: Double get() = (inhale + holdIn + exhale + holdOut).coerceAtLeast(1.0)

    fun with(p: BreathPattern) = copy(inhale = p.inhale, holdIn = p.holdIn, exhale = p.exhale, holdOut = p.holdOut)
}

data class BreathFrame(
    val active: Boolean,
    /** Breathing in, or holding full. */
    val inhaling: Boolean,
    /** How full Tokkip is: 0 empty … 1 full, eased. */
    val fill: Float,
    /** Seconds left in the session. */
    val left: Int,
    val breaths: Int,
    val done: Boolean,
    /** 0…1 of the session gone by. */
    val progress: Float,
    val phase: BreathPhase = BreathPhase.IN,
    /** Seconds left in this phase. */
    val phaseLeft: Double = 0.0,
)

private fun easeInOut(x: Double) = 0.5 - 0.5 * cos(PI * x.coerceIn(0.0, 1.0))

fun breathFrame(b: Breathing, nowMs: Long): BreathFrame {
    val start = b.startedAt ?: return BreathFrame(false, true, 0f, 0, 0, false, 0f)
    val total = b.minutes * 60.0
    val elapsed = max(0.0, (nowMs - start) / 1000.0)
    val done = elapsed >= total
    val t = min(elapsed, total)
    val c = t % b.cycle
    val (phase, p, len) = when {
        c < b.inhale -> Triple(BreathPhase.IN, c / b.inhale, b.inhale)
        c < b.inhale + b.holdIn -> Triple(BreathPhase.HOLD_IN, (c - b.inhale) / b.holdIn, b.holdIn)
        c < b.inhale + b.holdIn + b.exhale -> Triple(BreathPhase.OUT, (c - b.inhale - b.holdIn) / b.exhale, b.exhale)
        else -> Triple(BreathPhase.HOLD_OUT, (c - b.inhale - b.holdIn - b.exhale) / b.holdOut.coerceAtLeast(0.001), b.holdOut)
    }
    val fill = if (done) 0.0 else when (phase) {
        BreathPhase.IN -> easeInOut(p)
        BreathPhase.HOLD_IN -> 1.0
        BreathPhase.OUT -> 1 - easeInOut(p)
        BreathPhase.HOLD_OUT -> 0.0
    }
    return BreathFrame(
        active = !done,
        inhaling = phase == BreathPhase.IN || phase == BreathPhase.HOLD_IN,
        fill = fill.toFloat(),
        left = max(0, ceil(total - elapsed).toInt()),
        breaths = floor(t / b.cycle).toInt(),
        done = done,
        progress = (t / total).toFloat(),
        phase = phase,
        phaseLeft = len * (1 - p),
    )
}

/** What Tokkip says for each phase. */
fun BreathPhase.cue(): String = when (this) {
    BreathPhase.IN -> "Inhala…"
    BreathPhase.HOLD_IN -> "Sostén…"
    BreathPhase.OUT -> "Exhala…"
    BreathPhase.HOLD_OUT -> "Pausa…"
}

/** "4 s inhalar · 7 sostener · 8 exhalar". */
fun Breathing.rhythm(): String = buildList {
    add("${inhale.fmt()} s inhalar")
    if (holdIn > 0) add("${holdIn.fmt()} sostener")
    add("${exhale.fmt()} exhalar")
    if (holdOut > 0) add("${holdOut.fmt()} pausa")
}.joinToString(" · ")

private fun Double.fmt() = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)

/** 1500 → "25:00". */
fun clock(seconds: Int): String {
    val s = max(0, seconds)
    return "%d:%02d".format(s / 60, s % 60)
}
