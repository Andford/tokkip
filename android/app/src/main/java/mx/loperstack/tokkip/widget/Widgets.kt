// Home screen widgets (Glance): Tokkip with your day, Compañero (everything at hand), Respirar (1·5·10 min,
// the watch joins in), the Pomodoro with its live countdown, Pendientes you
// can tick off, Sueño, ¿Cómo estás? (one tap logs your mood) and Rachas.
// Each has its own cozy gradient, and Tokkip moves: Glance can't animate, so
// his frames go in a ViewFlipper (a few small bitmaps — RemoteViews have a
// size limit). They redraw on every Store change.

package mx.loperstack.tokkip.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.loperstack.tokkip.MainActivity
import mx.loperstack.tokkip.R
import mx.loperstack.tokkip.data.Agenda
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.nextEvent
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.data.addMood
import mx.loperstack.tokkip.data.averageMood
import mx.loperstack.tokkip.data.routine
import mx.loperstack.tokkip.data.nextDue
import mx.loperstack.tokkip.data.dayKey
import mx.loperstack.tokkip.data.doneOn
import mx.loperstack.tokkip.data.hhmm
import mx.loperstack.tokkip.data.minutes
import mx.loperstack.tokkip.data.scheduledOn
import mx.loperstack.tokkip.data.streak
import mx.loperstack.tokkip.data.toggle
import mx.loperstack.tokkip.wellness.MOODS
import mx.loperstack.tokkip.character.tokkipBitmap
import mx.loperstack.tokkip.wellness.Phase
import mx.loperstack.tokkip.wellness.PomodoroScheduler
import mx.loperstack.tokkip.wellness.clock
import mx.loperstack.tokkip.wellness.isPaused
import mx.loperstack.tokkip.wellness.left
import mx.loperstack.tokkip.wellness.orderedTasks
import mx.loperstack.tokkip.wellness.pause
import mx.loperstack.tokkip.wellness.pendingCount
import mx.loperstack.tokkip.wellness.resume
import mx.loperstack.tokkip.wellness.startFocus
import mx.loperstack.tokkip.wellness.toggleTask
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Solid Material You colours that follow the phone's light or dark mode.
private val White: ColorProvider @Composable get() = GlanceTheme.colors.onSurface
private val Dim: ColorProvider @Composable get() = GlanceTheme.colors.onSurfaceVariant
private val Violet: ColorProvider @Composable get() = GlanceTheme.colors.primary
private val GreenText: ColorProvider @Composable get() = GlanceTheme.colors.tertiary
private val Amber: ColorProvider @Composable get() = GlanceTheme.colors.secondary
private val Chip: ColorProvider @Composable get() = GlanceTheme.colors.secondaryContainer

/** Redraws every widget, at most every half second. */
object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    fun refresh(context: Context) {
        val app = context.applicationContext
        pending?.cancel()
        pending = scope.launch {
            delay(500)
            // Each widget by its own receiver's ids, so one kind never draws over another's.
            update(app, TokkipWidget(), TokkipWidgetReceiver::class.java)
            update(app, BreatheWidget(), BreatheWidgetReceiver::class.java)
            update(app, PomodoroWidget(), PomodoroWidgetReceiver::class.java)
            update(app, TasksWidget(), TasksWidgetReceiver::class.java)
            update(app, SleepWidget(), SleepWidgetReceiver::class.java)
            update(app, MoodWidget(), MoodWidgetReceiver::class.java)
            update(app, StreaksWidget(), StreaksWidgetReceiver::class.java)
            update(app, CompanionWidget(), CompanionWidgetReceiver::class.java)
            update(app, MoneyWidget(), MoneyWidgetReceiver::class.java)
        }
    }

    private suspend fun update(app: Context, widget: GlanceAppWidget, receiver: Class<*>) {
        runCatching {
            val ids = android.appwidget.AppWidgetManager.getInstance(app).getAppWidgetIds(android.content.ComponentName(app, receiver))
            val glance = androidx.glance.appwidget.GlanceAppWidgetManager(app)
            for (id in ids) runCatching { widget.update(app, glance.getGlanceIdBy(id)) }
        }
    }
}

private fun openApp(context: Context, tab: String, minutes: Int = 0) =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("tokkip://widget/$tab/$minutes"))
            .putExtra(MainActivity.EXTRA_TAB, tab)
            .putExtra(MainActivity.EXTRA_BREATHE_MIN, minutes)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )

private fun color() = Color(Store.state.value.tokkipColor)

private fun tokkip(context: Context, mood: Mood = Mood.IDLE, w: Int = 300, h: Int = 250, face: TokkipEngine.Eye? = null) =
    ImageProvider(tokkipBitmap(w, h, color(), mood, face, wear = true, headroom = 0.32f))

// ── Cozy backgrounds ──────────────────────────────────────────────────────────

/** Each section's two tones, top-left to bottom-right. */
private enum class Mood2(val a: Int, val b: Int) {
    TOKKIP(0xFF3B2A63.toInt(), 0xFF1C1733.toInt()),
    BREATHE(0xFF0F4A4A.toInt(), 0xFF182A48.toInt()),
    FOCUS(0xFF4A2338.toInt(), 0xFF22193D.toInt()),
    TASKS(0xFF1F3A5A.toInt(), 0xFF1A1E33.toInt()),
    SLEEP(0xFF1E1B4B.toInt(), 0xFF070B1F.toInt()),
    FEEL(0xFF5A3A2A.toInt(), 0xFF2E1F42.toInt()),
    STREAK(0xFF5C2E12.toInt(), 0xFF2E1A33.toInt()),
}

private val bgCache = HashMap<Mood2, ImageProvider>()

private fun bg(m: Mood2): ImageProvider = bgCache.getOrPut(m) {
    val w = 160
    val h = 160
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), m.a, m.b, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    p.shader = null
    // A soft glow, top right.
    p.shader = android.graphics.RadialGradient(w * 0.85f, h * 0.1f, w * 0.6f, 0x33FFFFFF, 0, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    p.shader = null
    if (m == Mood2.SLEEP) {
        val r = java.util.Random(11)
        repeat(26) {
            p.color = ((0x55 + r.nextInt(0x99)) shl 24) or 0xFFFFFF
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * 0.7f, 0.4f + r.nextFloat() * 1.1f, p)
        }
    }
    ImageProvider(bmp)
}

@Composable
private fun card(m: Mood2) = GlanceModifier.fillMaxSize().cornerRadius(26.dp).background(
    when (m) {
        Mood2.TOKKIP, Mood2.FEEL -> GlanceTheme.colors.widgetBackground
        Mood2.BREATHE -> GlanceTheme.colors.tertiaryContainer
        Mood2.FOCUS -> GlanceTheme.colors.primaryContainer
        Mood2.STREAK -> GlanceTheme.colors.secondaryContainer
        else -> GlanceTheme.colors.surface
    },
)

// ── Tokkip, moving ─────────────────────────────────────────────────────────────

/** How he moves: each is a smooth tween (res/anim) the launcher plays on every flip. */
private enum class Motion(val layout: Int, val headroom: Float) {
    HOP(R.layout.widget_flip_hop, 0.4f),
    BOB(R.layout.widget_flip_bob, 0.32f),
    BREATH(R.layout.widget_flip_breath, 0.32f),
    SNOOZE(R.layout.widget_flip_snooze, 0.3f),
}

/**
 * Tokkip moving, for `AndroidRemoteViews`: a ViewFlipper whose frames are the
 * same picture (sent once — RemoteViews share a repeated bitmap) plus, if
 * given, one with other eyes (a blink, a smile). Each flip plays the motion's
 * tween, so it is fluid at the screen's own rate, not a slideshow.
 */
private fun moving(context: Context, motion: Motion, mood: Mood, w: Int, h: Int, face: TokkipEngine.Eye? = null, alt: TokkipEngine.Eye? = null): RemoteViews {
    val flip = RemoteViews(context.packageName, motion.layout)
    val col = color()
    val main = tokkipBitmap(w, h, col, mood, face, wear = true, headroom = motion.headroom)
    val other = alt?.let { tokkipBitmap(w, h, col, mood, it, wear = true, headroom = motion.headroom) }
    for (b in listOfNotNull(main, main, main, other ?: main)) {
        val frame = RemoteViews(context.packageName, R.layout.widget_frame)
        frame.setImageViewBitmap(R.id.frame, b)
        flip.addView(R.id.flip, frame)
    }
    return flip
}

// ── Tokkip ─────────────────────────────────────────────────────────────────────

class TokkipWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        val now = System.currentTimeMillis()
        val next = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            runCatching { nextEvent(Agenda.load(context, now), now) }.getOrNull()
        } else null
        val data = Store.state.value
        val focusing = data.pomodoro.phase == Phase.FOCUS
        Store.applyLook()
        val image = moving(context, if (pending0(data) == 0) Motion.HOP else Motion.BOB, if (focusing) Mood.FOCUS else Mood.IDLE, 240, 220, alt = TokkipEngine.Eye.HAPPY)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hello = when (hour) { in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches" }.let { if (data.name.isBlank()) it else "$it, ${data.name}" }
        val pending = pendingCount(data.tasks)
        provideContent { GlanceTheme {
            Column(
                card(Mood2.TOKKIP).padding(12.dp).clickable(openApp(context, "home")),
                horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
            ) {
                AndroidRemoteViews(image, GlanceModifier.size(124.dp, 114.dp))
                Text(hello, style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                if (next != null) {
                    val min = ((next.begin - now) / 60_000).toInt()
                    val whenText = if (min < 60) "En $min min" else SimpleDateFormat("HH:mm", Locale("es", "MX")).format(Date(next.begin))
                    Text("$whenText · ${next.title}", style = TextStyle(color = Violet, fontSize = 12.sp), maxLines = 1)
                }
                Text(
                    if (pending == 0) "Sin pendientes" else if (pending == 1) "1 pendiente" else "$pending pendientes",
                    style = TextStyle(color = if (pending == 0) GreenText else Dim, fontSize = 12.sp),
                )
            }
        } }
    }
}

private fun pending0(d: mx.loperstack.tokkip.data.AppData) = pendingCount(d.tasks)

class TokkipWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TokkipWidget()
}

// ── Respirar ──────────────────────────────────────────────────────────────────

class BreatheWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        Store.applyLook()
        val image = moving(context, Motion.BREATH, Mood.BREATHING, 160, 146)
        val week = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
        }.timeInMillis
        val minutes = Store.state.value.sessions.filter { it.at >= week }.sumOf { it.minutes }
        provideContent { GlanceTheme {
            Column(card(Mood2.BREATHE).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.clickable(openApp(context, "breathe"))) {
                    AndroidRemoteViews(image, GlanceModifier.size(72.dp, 66.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    Column {
                        Text("Respirar", style = TextStyle(color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        Text("$minutes min esta semana · inhala con él", style = TextStyle(color = Dim, fontSize = 12.sp), maxLines = 1)
                    }
                }
                Spacer(GlanceModifier.height(10.dp))
                Row {
                    for ((i, m) in listOf(1, 5, 10).withIndex()) {
                        if (i > 0) Spacer(GlanceModifier.width(8.dp))
                        Box(
                            GlanceModifier.width(58.dp).height(40.dp).background(Chip).cornerRadius(20.dp).clickable(openApp(context, "breathe", m)),
                            contentAlignment = Alignment.Center,
                        ) { Text("$m min", style = TextStyle(color = GlanceTheme.colors.onTertiaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Bold)) }
                    }
                }
            }
        } }
    }
}

class BreatheWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BreatheWidget()
}

// ── Pomodoro ──────────────────────────────────────────────────────────────────

class PomodoroWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        val p = Store.state.value.pomodoro
        val now = System.currentTimeMillis()
        Store.applyLook()
        val image = moving(context, if (p.phase == Phase.FOCUS && !p.isPaused()) Motion.SNOOZE else Motion.BOB, if (p.phase == Phase.FOCUS) Mood.FOCUS else Mood.IDLE, 160, 146)
        val running = p.phase != Phase.IDLE && !p.isPaused() && p.endsAt != null
        val countdown = RemoteViews(context.packageName, R.layout.widget_chrono).apply {
            val base = SystemClock.elapsedRealtime() + ((p.endsAt ?: now) - now)
            setChronometer(R.id.chrono, base, null, true)
            setChronometerCountDown(R.id.chrono, true)
        }
        provideContent { GlanceTheme {
            Row(card(Mood2.FOCUS).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(72.dp, 66.dp).clickable(openApp(context, "focus"))) { AndroidRemoteViews(image, GlanceModifier.fillMaxSize()) }
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.defaultWeight().clickable(openApp(context, "focus"))) {
                    Text(
                        PomodoroScheduler.phaseTitle(p.phase) + if (p.isPaused()) " · en pausa" else "",
                        style = TextStyle(color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1,
                    )
                    if (running) AndroidRemoteViews(countdown)
                    else Text(if (p.phase == Phase.IDLE) "%d:00".format(mx.loperstack.tokkip.wellness.PomodoroConfig.current.focusMin) else clock(p.left(now)), style = TextStyle(color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                    Text("${p.today} hoy", style = TextStyle(color = Dim, fontSize = 11.sp))
                }
                Box(
                    GlanceModifier.size(48.dp).background(Chip).cornerRadius(24.dp).clickable(actionRunCallback<PomodoroToggle>()),
                    contentAlignment = Alignment.Center,
                ) { Text(if (running) "❚❚" else "▶", style = TextStyle(color = Violet, fontSize = 18.sp, fontWeight = FontWeight.Bold)) }
            }
        } }
    }
}

class PomodoroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PomodoroWidget()
}

/** ▶ / ❚❚ on the widget: the same as the app's main button. */
class PomodoroToggle : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.init(context)
        PomodoroScheduler.channels(context)
        val now = System.currentTimeMillis()
        val p = Store.state.value.pomodoro
        val next = when {
            p.phase == Phase.IDLE -> p.startFocus(now)
            p.isPaused() -> p.resume(now)
            else -> p.pause(now)
        }
        Store.setPomodoro(next)
        PomodoroScheduler.sync(context, next, now)
    }
}

// ── Pendientes ────────────────────────────────────────────────────────────────

private val TaskId = ActionParameters.Key<String>("taskId")

class TasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        val tasks = orderedTasks(Store.state.value.tasks).filter { !it.done }.take(20)
        Store.applyLook()
        val image = tokkip(context, Mood.IDLE, 120, 100)
        provideContent { GlanceTheme { TasksContent(context, tasks.map { it.id to it.text }, image) } }
    }
}

@Composable
private fun TasksContent(context: Context, tasks: List<Pair<String, String>>, image: ImageProvider) {
    Column(card(Mood2.TASKS).padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(image, contentDescription = "Tokkip", modifier = GlanceModifier.size(30.dp, 25.dp))
            Spacer(GlanceModifier.width(6.dp))
            Text(
                "Pendientes · ${tasks.size}", modifier = GlanceModifier.defaultWeight().clickable(openApp(context, "tasks")),
                style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            Box(
                GlanceModifier.size(34.dp).background(Chip).cornerRadius(17.dp).clickable(openApp(context, "tasks")),
                contentAlignment = Alignment.Center,
            ) { Text("+", style = TextStyle(color = Violet, fontSize = 20.sp, fontWeight = FontWeight.Bold)) }
        }
        Spacer(GlanceModifier.height(6.dp))
        if (tasks.isEmpty()) {
            Text("Nada pendiente", style = TextStyle(color = GreenText, fontSize = 13.sp), modifier = GlanceModifier.padding(top = 8.dp))
        } else {
            LazyColumn {
                items(tasks, itemId = { it.first.hashCode().toLong() }) { (taskId, text) ->
                    CheckBox(
                        checked = false,
                        onCheckedChange = actionRunCallback<TaskDone>(actionParametersOf(TaskId to taskId)),
                        text = text,
                        style = TextStyle(color = White, fontSize = 14.sp),
                        maxLines = 2,
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}

/** Ticking a task on the widget. */
class TaskDone : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskId] ?: return
        mx.loperstack.tokkip.data.TaskSync.toggle(context, taskId)
    }
}

// ── Sueño ─────────────────────────────────────────────────────────────────────

class SleepWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        Store.applyLook()
        val d = Store.state.value
        val now = System.currentTimeMillis()
        val last = d.sleep.maxByOrNull { it.wake }
        val lastToday = last?.takeIf { it.night == dayKey(now) }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val night = hour >= 20 || hour < 6
        val image = moving(context, Motion.SNOOZE, Mood.SLEEPY, 190, 172)
        val title = when {
            lastToday != null -> "${lastToday.minutes / 60} h ${"%02d".format(lastToday.minutes % 60)} anoche"
            night -> "Hora de descansar"
            else -> "¿Cómo dormiste?"
        }
        val sub = when {
            night -> "Meta: dormir a las ${hhmm(d.sleepGoal.bedMin)}"
            lastToday != null && lastToday.minutes >= 420 -> "¡Descansaste!"
            lastToday != null && lastToday.minutes < 360 -> "Hoy ve con calma"
            else -> "Toca para registrar tu noche"
        }
        provideContent { GlanceTheme {
            Row(card(Mood2.SLEEP).padding(12.dp).clickable(openApp(context, "sleep")), verticalAlignment = Alignment.CenterVertically) {
                AndroidRemoteViews(image, GlanceModifier.size(92.dp, 84.dp))
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text("Sueño", style = TextStyle(color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Text(title, style = TextStyle(color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(sub, style = TextStyle(color = Dim, fontSize = 12.sp), maxLines = 2)
                }
            }
        } }
    }
}

class SleepWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SleepWidget()
}

// ── ¿Cómo estás? ──────────────────────────────────────────────────────────────

private val MoodValue = ActionParameters.Key<Int>("mood")

class MoodWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        Store.applyLook()
        val now = System.currentTimeMillis()
        val today = Store.state.value.logs.firstOrNull { it.day == dayKey(now) }
        val latest = today?.moods?.lastOrNull()?.value ?: today?.mood?.takeIf { it > 0 }
        val col = color()
        val faces = MOODS.reversed().map { m -> m to ImageProvider(tokkipBitmap(96, 80, col, Mood.IDLE, m.face, wear = false)) }
        val label = latest?.let { v -> MOODS.firstOrNull { it.value == v }?.label }
        val count = today?.moods?.size ?: 0
        provideContent { GlanceTheme {
            Column(card(Mood2.FEEL).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (label == null) "¿Cómo estás?" else "Ahora: $label · $count hoy",
                    style = TextStyle(color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1,
                    modifier = GlanceModifier.padding(start = 4.dp).clickable(openApp(context, "diary")),
                )
                Spacer(GlanceModifier.height(4.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    for ((m, img) in faces) {
                        Box(
                            GlanceModifier.defaultWeight().height(48.dp).cornerRadius(16.dp)
                                .background(if (m.value == latest) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant)
                                .clickable(actionRunCallback<MoodLog>(actionParametersOf(MoodValue to m.value))),
                            contentAlignment = Alignment.Center,
                        ) { Image(img, contentDescription = m.label, modifier = GlanceModifier.size(44.dp, 37.dp)) }
                        if (m != faces.last().first) Spacer(GlanceModifier.width(4.dp))
                    }
                }
            }
        } }
    }
}

class MoodWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoodWidget()
}

/** A face on the widget: logs that mood now, as the diary does. */
class MoodLog : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val v = parameters[MoodValue] ?: return
        Store.init(context)
        val now = System.currentTimeMillis()
        Store.setLogs(addMood(Store.state.value.logs, dayKey(now), now, v))
        mx.loperstack.tokkip.character.Haptics.success(context)
    }
}

// ── Rachas ────────────────────────────────────────────────────────────────────

private val HabitId = ActionParameters.Key<String>("habitId")

class StreaksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        Store.applyLook()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val today = Store.state.value.habits.filter { it.scheduledOn(cal) }
            .map { Triple(it.id, it.name, it.streak(now)) to it.doneOn(now) }
        val left = today.count { !it.second }
        val image = moving(context, if (today.isNotEmpty() && left == 0) Motion.HOP else Motion.BOB, Mood.IDLE, 110, 100)
        provideContent { GlanceTheme {
            Column(card(Mood2.STREAK).padding(12.dp)) {
                Row(GlanceModifier.fillMaxWidth().clickable(openApp(context, "streaks")), verticalAlignment = Alignment.CenterVertically) {
                    AndroidRemoteViews(image, GlanceModifier.size(42.dp, 38.dp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        when {
                            today.isEmpty() -> "Rachas"
                            left == 0 -> "¡Todo listo hoy!"
                            else -> "Rachas · faltan $left"
                        },
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                if (today.isEmpty()) {
                    Text("Toca para crear tu primera racha", style = TextStyle(color = Amber, fontSize = 13.sp), modifier = GlanceModifier.clickable(openApp(context, "streaks")))
                } else {
                    LazyColumn {
                        items(today, itemId = { it.first.first.hashCode().toLong() }) { (h, done) ->
                            Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                CheckBox(
                                    checked = done,
                                    onCheckedChange = actionRunCallback<HabitToggle>(actionParametersOf(HabitId to h.first)),
                                    text = h.second,
                                    style = TextStyle(color = White, fontSize = 14.sp),
                                    maxLines = 1,
                                    modifier = GlanceModifier.defaultWeight(),
                                )
                                Text(if (h.third > 0) "racha ${h.third}" else "", style = TextStyle(color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        } }
    }
}

class StreaksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreaksWidget()
}

/** Ticking a habit on the widget. */
class HabitToggle : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val hid = parameters[HabitId] ?: return
        Store.init(context)
        val now = System.currentTimeMillis()
        Store.setHabits(Store.state.value.habits.map { if (it.id == hid) it.toggle(now) else it })
        mx.loperstack.tokkip.character.Haptics.success(context)
    }
}

// ── Compañero: everything at hand, changing through the day ───────────────────
//
// Tokkip in a ring that fills with your day (habits and tasks done) — or, with
// nothing due, with his way to the next growth stage — the next thing on your
// calendar, your habits as icons you tick in place with your longest streak,
// and — when the widget is tall enough — his level and your week's money as
// chips, two tasks to tick, the five moods and, if there's room, what Tokkip
// noticed. Light buttons for Respirar, Enfoque and Diario. Glance can't
// draw vectors, so the ring, the habit dots and the icons are small bitmaps in
// the phone's own Material You colours.

/** The phone's dynamic colours, light or dark, as plain ints for bitmaps. */
private class Pal(val primary: Int, val onPrimary: Int, val track: Int, val dim: Int)

private fun pal(context: Context): Pal {
    val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    fun c(id: Int) = context.getColor(id)
    return if (night) Pal(c(android.R.color.system_accent1_200), c(android.R.color.system_accent1_800), c(android.R.color.system_neutral2_700), c(android.R.color.system_neutral2_200))
    else Pal(c(android.R.color.system_accent1_600), c(android.R.color.system_accent1_0), c(android.R.color.system_neutral2_200), c(android.R.color.system_neutral2_700))
}

/** A Material icon (an ImageVector) drawn into `canvas`, `size` px wide at (x, y). */
private fun drawVector(canvas: android.graphics.Canvas, v: androidx.compose.ui.graphics.vector.ImageVector, x: Float, y: Float, size: Float, color: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    canvas.save()
    canvas.translate(x, y)
    canvas.scale(size / v.viewportWidth, size / v.viewportHeight)
    fun walk(group: androidx.compose.ui.graphics.vector.VectorGroup) {
        for (node in group) {
            when (node) {
                is androidx.compose.ui.graphics.vector.VectorGroup -> walk(node)
                is androidx.compose.ui.graphics.vector.VectorPath -> {
                    @Suppress("DEPRECATION")
                    val path = androidx.compose.ui.graphics.vector.PathParser().addPathNodes(node.pathData).toPath()
                    val ap = path.asAndroidPath()
                    ap.fillType = if (node.pathFillType == androidx.compose.ui.graphics.PathFillType.EvenOdd)
                        android.graphics.Path.FillType.EVEN_ODD else android.graphics.Path.FillType.WINDING
                    canvas.drawPath(ap, paint)
                }
            }
        }
    }
    walk(v.root)
    canvas.restore()
}

private fun iconBitmap(v: androidx.compose.ui.graphics.vector.ImageVector, color: Int, px: Int = 48): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    drawVector(android.graphics.Canvas(bmp), v, 0f, 0f, px.toFloat(), color)
    return bmp
}

/** A habit: filled in your colour when done today, an outline when not. */
private fun habitDot(v: androidx.compose.ui.graphics.vector.ImageVector, done: Boolean, p: Pal, px: Int = 84): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = px / 2f
    if (done) {
        paint.color = p.primary
        c.drawCircle(r, r, r, paint)
    } else {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = px * 0.06f
        paint.color = p.track
        c.drawCircle(r, r, r - paint.strokeWidth / 2, paint)
    }
    val icon = px * 0.5f
    drawVector(c, v, r - icon / 2, r - icon / 2, icon, if (done) p.onPrimary else p.dim)
    return bmp
}

/** A task's round check. */
private fun taskDot(p: Pal, px: Int = 48): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = px * 0.1f; color = p.dim }
    android.graphics.Canvas(bmp).drawCircle(px / 2f, px / 2f, px / 2f - paint.strokeWidth, paint)
    return bmp
}

/** The ring around Tokkip: how much of today's habits and tasks is done. */
private fun ringBitmap(frac: Float, p: Pal, px: Int = 168): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val stroke = px * 0.07f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
    val box = android.graphics.RectF(stroke, stroke, px - stroke, px - stroke)
    paint.color = p.track
    c.drawArc(box, 0f, 360f, false, paint)
    if (frac > 0f) {
        paint.color = p.primary
        c.drawArc(box, -90f, 360f * frac.coerceIn(0f, 1f), false, paint)
    }
    return bmp
}

class CompanionWidget : GlanceAppWidget() {
    // The layout follows the widget's real size: 4×2 keeps the essentials.
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        Store.applyLook()
        val d = Store.state.value
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val es = Locale("es", "MX")
        val next = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            runCatching { nextEvent(Agenda.load(context, now), now) }.getOrNull()
        } else null
        val p = d.pomodoro
        val focusing = p.phase != Phase.IDLE && !p.isPaused() && p.endsAt != null
        val allTasks = d.tasks
        val tasks = orderedTasks(allTasks).filter { !it.done }
        val habits = d.habits.filter { it.scheduledOn(cal) }
        val habitsDone = habits.count { it.doneOn(now) }
        val best = d.habits.maxOfOrNull { it.streak(now) } ?: 0
        val today = d.logs.firstOrNull { it.day == dayKey(now) }
        val moodNow = today?.moods?.lastOrNull()?.value ?: today?.mood?.takeIf { it > 0 }
        val hello = when (hour) { in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches" }
            .let { if (d.name.isBlank()) it else "$it, ${d.name}" }

        // The day so far: habits due today and today's tasks.
        val doneToday = allTasks.count { it.done }
        val total = habits.size + allTasks.size
        val frac = if (total == 0) 0f else (habitsDone + doneToday).toFloat() / total
        val left = habits.size - habitsDone + tasks.size
        val allDone = total > 0 && left == 0

        // Weather, when the car screen (or anything else) already looked it up.
        mx.loperstack.tokkip.data.Weather.refresh(context)
        val sky = mx.loperstack.tokkip.data.Weather.now
        val date = SimpleDateFormat("EEEE d", es).format(Date(now)).replaceFirstChar { it.uppercase(es) }
        val info = buildList {
            add(date)
            if (sky != null) add("${sky.temp}° ${mx.loperstack.tokkip.data.Weather.label(sky.code).lowercase(es)}")
            add(if (total == 0) "un paso a la vez" else if (allDone) "¡todo listo!" else "faltan $left")
        }.joinToString(" · ")

        // A payment due today or tomorrow (or late), from Dinero → Pagos y deudas.
        val payDue = d.debts.mapNotNull { debt -> debt.nextDue(now)?.let { debt to it } }
            .filter { mx.loperstack.tokkip.data.daysUntil(it.second, now) <= 1 }.minByOrNull { it.second }

        // The line that matters most right now.
        val headline: Pair<String, Int?> = when {
            focusing -> PomodoroScheduler.phaseTitle(p.phase) to null
            next != null && next.begin - now < 12 * 3_600_000L ->
                "${next.title} · ${SimpleDateFormat("h:mm a", es).format(Date(next.begin))}" to (next.color ?: 0xFF3D8BD9.toInt())
            payDue != null -> "Pago de ${payDue.first.name} · ${mx.loperstack.tokkip.data.dueText(payDue.second, now).lowercase(es)}" to payDue.first.color.toInt()
            hour >= 20 -> "¿Qué fue lo mejor de tu día?" to null
            allDone -> "Tokkip está orgulloso de ti" to null
            else -> "Un paso a la vez" to null
        }

        // Tokkip's growth, what he noticed, and your week's money (data/Growth.kt, Insights.kt, Money.kt).
        val r = d.routine()
        val grow = mx.loperstack.tokkip.data.growth(r, now)
        val noted = mx.loperstack.tokkip.data.noteOfTheDay(r, now)
        val week = mx.loperstack.tokkip.data.weekSummary(d.money, d.budget, now)
        val hasMoney = week.hasBudget || d.money.isNotEmpty()

        val colors = pal(context)
        // With nothing due today, the ring shows his way to the next stage.
        val ring = ImageProvider(ringBitmap(if (total == 0) grow.progress else frac, colors))
        val starIcon = ImageProvider(iconBitmap(Icons.Rounded.Star, colors.primary))
        val walletIcon = ImageProvider(iconBitmap(Icons.Rounded.AccountBalanceWallet, colors.primary))
        val notedIcon = ImageProvider(iconBitmap(mx.loperstack.tokkip.ui.noteIcon(noted.icon), colors.onPrimary))
        val moneyText = when {
            week.hasBudget && week.left >= 0 -> "${mx.loperstack.tokkip.data.money(week.left)} libres"
            week.hasBudget -> "Te pasaste ${mx.loperstack.tokkip.data.money(-week.left)}"
            else -> "${mx.loperstack.tokkip.data.money(week.spent)} esta semana"
        }
        val image = moving(
            context, if (allDone) Motion.HOP else if (focusing) Motion.SNOOZE else Motion.BOB,
            if (focusing) Mood.FOCUS else if (hour >= 22 || hour < 6) Mood.SLEEPY else Mood.IDLE, 150, 136, alt = TokkipEngine.Eye.HAPPY,
        )
        val countdown = RemoteViews(context.packageName, R.layout.widget_chrono).apply {
            setChronometer(R.id.chrono, SystemClock.elapsedRealtime() + ((p.endsAt ?: now) - now), null, true)
            setChronometerCountDown(R.id.chrono, true)
        }
        val dots = habits.take(6).map { h ->
            Triple(h.id, h.name, ImageProvider(habitDot(mx.loperstack.tokkip.ui.MIcon.habitIcon(mx.loperstack.tokkip.data.habitIconKey(h.emoji)), h.doneOn(now), colors)))
        }
        val flame = ImageProvider(iconBitmap(Icons.Rounded.LocalFireDepartment, if (best > 0) 0xFFE0661F.toInt() else colors.dim))
        val check = ImageProvider(taskDot(colors))
        val col = color()
        val faces = MOODS.reversed().map { m -> m to ImageProvider(tokkipBitmap(84, 70, col, Mood.IDLE, m.face, wear = false)) }
        val actionIcons = listOf(
            Icons.Rounded.Air,
            if (focusing) Icons.Rounded.Pause else Icons.Rounded.Timer,
            Icons.Rounded.EditNote,
        )

        provideContent {
            GlanceTheme {
                val size = androidx.glance.LocalSize.current
                val full = size.height >= 250.dp
                val onTonal = GlanceTheme.colors.onSecondaryContainer
                val tonalIcons = actionIcons.map { ImageProvider(iconBitmap(it, onTonal.getColor(context).toArgb())) }
                Column(GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(GlanceTheme.colors.widgetBackground).padding(14.dp)) {
                    // Tokkip in his ring, hello, what's next.
                    Row(GlanceModifier.fillMaxWidth().clickable(openApp(context, "home")), verticalAlignment = Alignment.CenterVertically) {
                        Box(GlanceModifier.size(76.dp), contentAlignment = Alignment.Center) {
                            Image(ring, contentDescription = if (total == 0) "Camino de Tokkip" else "Progreso de tu día", modifier = GlanceModifier.size(76.dp))
                            AndroidRemoteViews(image, GlanceModifier.size(60.dp, 56.dp))
                        }
                        Spacer(GlanceModifier.width(12.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(hello, style = TextStyle(color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                headline.second?.let { bar ->
                                    Box(GlanceModifier.size(4.dp, 14.dp).cornerRadius(2.dp).background(ColorProvider(Color(bar)))) {}
                                    Spacer(GlanceModifier.width(6.dp))
                                }
                                if (focusing) AndroidRemoteViews(countdown)
                                else Text(headline.first, style = TextStyle(color = Dim, fontSize = 13.sp), maxLines = 1)
                            }
                            Text(info, style = TextStyle(color = Dim, fontSize = 12.sp), maxLines = 1)
                        }
                    }
                    Spacer(GlanceModifier.height(10.dp))

                    // Habits you tick in place, and the longest streak.
                    Row(
                        GlanceModifier.fillMaxWidth().cornerRadius(20.dp).background(GlanceTheme.colors.surfaceVariant).padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dots.isEmpty()) {
                            Text(
                                "Crea tu primera racha", style = TextStyle(color = Dim, fontSize = 13.sp),
                                modifier = GlanceModifier.defaultWeight().clickable(openApp(context, "streaks")),
                            )
                        } else {
                            for ((hid, name, img) in dots) {
                                Image(
                                    img, contentDescription = name,
                                    modifier = GlanceModifier.size(34.dp).clickable(actionRunCallback<HabitToggle>(actionParametersOf(HabitId to hid))),
                                )
                                Spacer(GlanceModifier.width(6.dp))
                            }
                            Spacer(GlanceModifier.defaultWeight())
                        }
                        Row(
                            GlanceModifier.cornerRadius(14.dp).background(GlanceTheme.colors.widgetBackground).padding(horizontal = 9.dp, vertical = 5.dp)
                                .clickable(openApp(context, "streaks")),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(flame, contentDescription = null, modifier = GlanceModifier.size(16.dp))
                            Spacer(GlanceModifier.width(3.dp))
                            Text(if (best == 1) "1 día" else "$best días", style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        }
                    }

                    if (full) {
                        Spacer(GlanceModifier.height(8.dp))
                        // Tokkip's level and the week's money, side by side.
                        Row(GlanceModifier.fillMaxWidth()) {
                            @Composable
                            fun Chip(icon: ImageProvider, text: String, open: androidx.glance.action.Action, modifier: GlanceModifier) {
                                Row(
                                    modifier.height(34.dp).cornerRadius(17.dp).background(GlanceTheme.colors.surfaceVariant).padding(horizontal = 10.dp).clickable(open),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Image(icon, contentDescription = null, modifier = GlanceModifier.size(16.dp))
                                    Spacer(GlanceModifier.width(6.dp))
                                    Text(text, style = TextStyle(color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                                }
                            }
                            Chip(
                                starIcon,
                                if (hasMoney) "${grow.title} · nv ${grow.stage.level}" else "${grow.title} · faltan ${grow.toNext} para ${grow.nextName}",
                                openApp(context, "home"), GlanceModifier.defaultWeight(),
                            )
                            if (hasMoney) {
                                Spacer(GlanceModifier.width(6.dp))
                                Chip(walletIcon, moneyText, openMoney(context, null), GlanceModifier.defaultWeight())
                            }
                        }
                        Spacer(GlanceModifier.height(6.dp))
                        // Two tasks to tick off.
                        if (tasks.isEmpty()) {
                            Text(
                                "Sin pendientes · toca para agregar", style = TextStyle(color = Dim, fontSize = 13.sp),
                                modifier = GlanceModifier.padding(horizontal = 4.dp, vertical = 4.dp).clickable(openApp(context, "tasks")),
                            )
                        } else for (t in tasks.take(if (size.height >= 330.dp) 3 else 2)) {
                            Row(
                                GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                                    .clickable(actionRunCallback<TaskDone>(actionParametersOf(TaskId to t.id))),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(check, contentDescription = "Marcar como hecha", modifier = GlanceModifier.size(20.dp))
                                Spacer(GlanceModifier.width(10.dp))
                                Text(t.text, style = TextStyle(color = White, fontSize = 14.sp), maxLines = 1)
                            }
                        }
                        Spacer(GlanceModifier.height(6.dp))
                        // How are you: one tap logs it.
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("¿Cómo estás?", style = TextStyle(color = Dim, fontSize = 13.sp), modifier = GlanceModifier.defaultWeight())
                            for ((m, img) in faces) {
                                Box(
                                    GlanceModifier.size(36.dp, 32.dp).cornerRadius(16.dp)
                                        .background(if (m.value == moodNow) GlanceTheme.colors.secondaryContainer else GlanceTheme.colors.widgetBackground)
                                        .clickable(actionRunCallback<MoodLog>(actionParametersOf(MoodValue to m.value))),
                                    contentAlignment = Alignment.Center,
                                ) { Image(img, contentDescription = m.label, modifier = GlanceModifier.size(30.dp, 25.dp)) }
                                Spacer(GlanceModifier.width(2.dp))
                            }
                        }
                        // What Tokkip noticed, when there's room for it.
                        val taskRows = if (tasks.isEmpty()) 1 else minOf(tasks.size, if (size.height >= 330.dp) 3 else 2)
                        val usedDp = 76 + 10 + 48 + 8 + 34 + 6 + taskRows * 28 + 6 + 32 + 42 + 28 + 10
                        if (size.height.value - usedDp >= 56) {
                            Spacer(GlanceModifier.height(8.dp))
                            Row(
                                GlanceModifier.fillMaxWidth().cornerRadius(20.dp).background(GlanceTheme.colors.secondaryContainer).padding(10.dp)
                                    .clickable(openApp(context, "home")),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(GlanceModifier.size(30.dp).cornerRadius(15.dp).background(GlanceTheme.colors.primary), contentAlignment = Alignment.Center) {
                                    Image(notedIcon, contentDescription = null, modifier = GlanceModifier.size(17.dp))
                                }
                                Spacer(GlanceModifier.width(10.dp))
                                Column(GlanceModifier.defaultWeight()) {
                                    Text("Tokkip notó", style = TextStyle(color = onTonal, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                                    Text(noted.text, style = TextStyle(color = onTonal, fontSize = 13.sp), maxLines = 2)
                                }
                            }
                        }
                    }

                    Spacer(GlanceModifier.defaultWeight())
                    // Light buttons: an icon and a word each.
                    Row(GlanceModifier.fillMaxWidth()) {
                        @Composable
                        fun Action(text: String, icon: ImageProvider, modifier: GlanceModifier, last: Boolean = false) {
                            Row(
                                modifier.defaultWeight().height(42.dp).cornerRadius(21.dp).background(GlanceTheme.colors.secondaryContainer),
                                verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Image(icon, contentDescription = null, modifier = GlanceModifier.size(18.dp))
                                Spacer(GlanceModifier.width(6.dp))
                                Text(text, style = TextStyle(color = onTonal, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            }
                            if (!last) Spacer(GlanceModifier.width(6.dp))
                        }
                        Action("Respirar", tonalIcons[0], GlanceModifier.clickable(openApp(context, "breathe", 5)))
                        Action(if (focusing) "Pausa" else "Enfoque", tonalIcons[1], GlanceModifier.clickable(actionRunCallback<PomodoroToggle>()))
                        Action("Diario", tonalIcons[2], GlanceModifier.clickable(openApp(context, "diary")), last = true)
                    }
                }
            }
        }
    }
}

class CompanionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompanionWidget()
}

// ── Dinero ────────────────────────────────────────────────────────────────────
//
// What's left of the week's budget in a ring, about how much per day, the
// weekdays and the weekend as bars, what Tokkip noticed (when tall enough),
// and "Anotar" with your three most used categories one tap away.

private fun openMoney(context: Context, add: String?) =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("tokkip://widget/money/${add ?: "open"}"))
            .putExtra(MainActivity.EXTRA_TAB, "money")
            .apply { if (add != null) putExtra(MainActivity.EXTRA_MONEY_ADD, add) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )

/** A rounded progress bar; over budget turns red. */
private fun barBitmap(frac: Float, p: Pal, w: Int = 480, h: Int = 18): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = h / 2f
    paint.color = p.track
    c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, paint)
    if (frac > 0f) {
        paint.color = when {
            frac > 1f -> 0xFFE5484D.toInt()
            frac > 0.85f -> 0xFFE8A317.toInt()
            else -> p.primary
        }
        c.drawRoundRect(0f, 0f, maxOf(h.toFloat(), w * frac.coerceAtMost(1f)), h.toFloat(), r, r, paint)
    }
    return bmp
}

/** The week's ring: used against the budget, with a tick where the week is. */
private fun budgetRing(used: Float, elapsed: Float, p: Pal, px: Int = 168): Bitmap {
    val bmp = ringBitmap(0f, p, px)
    val c = android.graphics.Canvas(bmp)
    val stroke = px * 0.07f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
    val box = android.graphics.RectF(stroke, stroke, px - stroke, px - stroke)
    if (used > 0f) {
        paint.color = when {
            used > 1f -> 0xFFE5484D.toInt()
            used - elapsed >= 0.15f -> 0xFFE8A317.toInt()
            else -> p.primary
        }
        c.drawArc(box, -90f, 360f * used.coerceAtMost(1f), false, paint)
    }
    val a = Math.toRadians((-90 + 360 * elapsed).toDouble())
    val rIn = px / 2f - stroke * 1.9f
    val rOut = px / 2f - stroke * 0.2f
    paint.color = p.dim
    paint.strokeWidth = stroke * 0.45f
    c.drawLine(
        px / 2f + (rIn * Math.cos(a)).toFloat(), px / 2f + (rIn * Math.sin(a)).toFloat(),
        px / 2f + (rOut * Math.cos(a)).toFloat(), px / 2f + (rOut * Math.sin(a)).toFloat(), paint,
    )
    return bmp
}

/** A category's round button: its icon in its colour. */
private fun categoryDot(c: mx.loperstack.tokkip.data.MoneyCategory, px: Int = 96): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (c.color.toInt() and 0x00FFFFFF) or 0x38000000 }
    canvas.drawCircle(px / 2f, px / 2f, px / 2f, paint)
    val icon = px * 0.52f
    drawVector(canvas, mx.loperstack.tokkip.ui.categoryIcon(c.icon), (px - icon) / 2, (px - icon) / 2, icon, c.color.toInt())
    return bmp
}

class MoneyWidget : GlanceAppWidget() {
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        val d = Store.state.value
        val now = System.currentTimeMillis()
        val w = mx.loperstack.tokkip.data.weekSummary(d.money, d.budget, now)
        val fresh = d.money.isEmpty() && d.budget.weekly <= 0
        val colors = pal(context)
        val ring = ImageProvider(budgetRing(w.used, w.elapsed, colors))
        val weekdayBar = ImageProvider(barBitmap(w.weekdayUsed, colors))
        val weekendBar = ImageProvider(barBitmap(w.weekendUsed, colors))
        // Your three most used categories of the last month (or the usual ones).
        val since = now - 30L * 86_400_000
        val used = d.money.filter { it.kind == mx.loperstack.tokkip.data.MoneyKind.GASTO && it.at >= since }
            .groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        val quick = (used + listOf("comida", "super", "cafe", "transporte")).distinct().take(3).map { mx.loperstack.tokkip.data.categoryOf(it) }
        val quickIcons = quick.map { it to ImageProvider(categoryDot(it)) }
        val note = mx.loperstack.tokkip.data.moneyNotes(
            d.money, d.budget, d.logs.filter { it.averageMood > 0 }.associate { it.day to it.averageMood }, now,
        ).maxByOrNull { it.weight }
        val noteIcon = note?.let { ImageProvider(iconBitmap(mx.loperstack.tokkip.ui.noteIcon(it.icon), colors.primary)) }
        val pct = if (w.hasBudget) "${(w.used * 100).toInt()}%" else "—"
        // The next payment due in a week (or late), from Pagos y deudas.
        val nextPay = d.debts.mapNotNull { debt -> debt.nextDue(now)?.let { debt to it } }
            .filter { mx.loperstack.tokkip.data.daysUntil(it.second, now) <= 7 }.minByOrNull { it.second }
        val payText = nextPay?.let { (debt, due) ->
            "${mx.loperstack.tokkip.data.dueText(due, now)}: ${debt.name} · ${mx.loperstack.tokkip.data.money(debt.payment)}"
        }
        val payLate = nextPay?.let { mx.loperstack.tokkip.data.daysUntil(it.second, now) < 0 } == true
        val cardIcon = ImageProvider(iconBitmap(Icons.Rounded.CreditCard, if (payLate) 0xFFE5484D.toInt() else colors.primary))
        val m: (Long, Boolean) -> String = { c, s -> mx.loperstack.tokkip.data.money(c, s) }

        provideContent {
            GlanceTheme {
                val size = androidx.glance.LocalSize.current
                val onTonal = GlanceTheme.colors.onSecondaryContainer
                val addIcon = ImageProvider(iconBitmap(Icons.Rounded.Add, onTonal.getColor(context).toArgb()))
                Column(GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(GlanceTheme.colors.widgetBackground).padding(14.dp)) {
                    Row(GlanceModifier.fillMaxWidth().clickable(openMoney(context, null)), verticalAlignment = Alignment.CenterVertically) {
                        Box(GlanceModifier.size(64.dp), contentAlignment = Alignment.Center) {
                            Image(ring, contentDescription = "Presupuesto usado", modifier = GlanceModifier.size(64.dp))
                            Text(pct, style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        }
                        Spacer(GlanceModifier.width(12.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text("DINERO · ESTA SEMANA", style = TextStyle(color = Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            when {
                                fresh -> {
                                    Text("Lleva tus cuentas", style = TextStyle(color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                                    Text("Toca para poner tu presupuesto", style = TextStyle(color = Dim, fontSize = 12.sp), maxLines = 1)
                                }
                                w.hasBudget -> {
                                    Text(
                                        if (w.left >= 0) "Te quedan ${m(w.left, false)}" else "Te pasaste por ${m(-w.left, false)}",
                                        style = TextStyle(color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 1,
                                    )
                                    Text(
                                        if (w.left > 0) "Unos ${m(w.perDay, false)} por día · de ${m(w.budget.weekly, false)}" else "Gastaste ${m(w.spent, false)} de ${m(w.budget.weekly, false)}",
                                        style = TextStyle(color = Dim, fontSize = 12.sp), maxLines = 1,
                                    )
                                }
                                else -> {
                                    Text("Gastaste ${m(w.spent, false)}", style = TextStyle(color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                                    Text("Ponle presupuesto a tu semana", style = TextStyle(color = Dim, fontSize = 12.sp), maxLines = 1)
                                }
                            }
                        }
                    }
                    if (w.hasBudget && size.height >= 170.dp) {
                        Spacer(GlanceModifier.height(10.dp))
                        @Composable
                        fun Split(title: String, spent: Long, budget: Long, bar: ImageProvider) {
                            Row(GlanceModifier.fillMaxWidth()) {
                                Text(title, style = TextStyle(color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                                Text("${m(spent, false)} de ${m(budget, false)}", style = TextStyle(color = Dim, fontSize = 12.sp), maxLines = 1)
                            }
                            Spacer(GlanceModifier.height(3.dp))
                            Image(bar, contentDescription = null, modifier = GlanceModifier.fillMaxWidth().height(7.dp), contentScale = androidx.glance.layout.ContentScale.FillBounds)
                        }
                        Split("Entre semana", w.weekdaySpent, w.weekdayBudget, weekdayBar)
                        if (w.budget.weekend > 0) {
                            Spacer(GlanceModifier.height(7.dp))
                            Split("Fin de semana", w.weekendSpent, w.budget.weekend, weekendBar)
                        }
                    }
                    if (payText != null && size.height >= 200.dp) {
                        Spacer(GlanceModifier.height(8.dp))
                        Row(GlanceModifier.fillMaxWidth().clickable(openMoney(context, null)), verticalAlignment = Alignment.CenterVertically) {
                            Image(cardIcon, contentDescription = null, modifier = GlanceModifier.size(16.dp))
                            Spacer(GlanceModifier.width(6.dp))
                            Text(payText, style = TextStyle(color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        }
                    }
                    if (note != null && noteIcon != null && size.height >= 250.dp) {
                        Spacer(GlanceModifier.height(10.dp))
                        Row(
                            GlanceModifier.fillMaxWidth().cornerRadius(18.dp).background(GlanceTheme.colors.surfaceVariant).padding(10.dp)
                                .clickable(openMoney(context, null)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(noteIcon, contentDescription = null, modifier = GlanceModifier.size(20.dp))
                            Spacer(GlanceModifier.width(8.dp))
                            Text(note.text, style = TextStyle(color = White, fontSize = 12.sp), maxLines = 2)
                        }
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            GlanceModifier.defaultWeight().height(42.dp).cornerRadius(21.dp).background(GlanceTheme.colors.secondaryContainer)
                                .clickable(openMoney(context, "")),
                            verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(addIcon, contentDescription = null, modifier = GlanceModifier.size(18.dp))
                            Spacer(GlanceModifier.width(6.dp))
                            Text("Anotar", style = TextStyle(color = onTonal, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        }
                        for ((c, img) in quickIcons) {
                            Spacer(GlanceModifier.width(6.dp))
                            Image(img, contentDescription = c.label, modifier = GlanceModifier.size(42.dp).clickable(openMoney(context, c.id)))
                        }
                    }
                }
            }
        }
    }
}

class MoneyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoneyWidget()
}
