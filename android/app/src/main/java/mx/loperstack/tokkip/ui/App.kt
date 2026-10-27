// The app: a phone layout with five tabs (Inicio, Tareas, Enfoque, Respirar,
// PC) and, on a wide landscape screen (the Legion Tab), the desk mode.
// Everything reads Store.state; actions go through Actions below.

package mx.loperstack.tokkip.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.AutoStories as OAutoStories
import androidx.compose.material.icons.outlined.FavoriteBorder as OFavoriteBorder
import androidx.compose.material.icons.outlined.Home as OHome
import androidx.compose.material.icons.outlined.Spa as OSpa
import androidx.compose.material.icons.outlined.TaskAlt as OTaskAlt
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.Squircle
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.BatteryManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import mx.loperstack.tokkip.data.Agenda
import mx.loperstack.tokkip.data.CalEvent
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.NowPlaying
import mx.loperstack.tokkip.data.breathing
import mx.loperstack.tokkip.data.TaskSync
import mx.loperstack.tokkip.data.WearSync
import mx.loperstack.tokkip.data.isTomorrow
import mx.loperstack.tokkip.data.nextEvent
import mx.loperstack.tokkip.data.upcoming
import mx.loperstack.tokkip.character.TOKKIP_PALETTE
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.rememberMusicPlaying
import mx.loperstack.tokkip.data.GoogleTasks
import android.app.Activity
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.wellness.BreathSession
import mx.loperstack.tokkip.wellness.calmDrop
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import mx.loperstack.tokkip.wellness.BREATHE_MINUTES
import mx.loperstack.tokkip.wellness.Breathing
import mx.loperstack.tokkip.wellness.Phase
import mx.loperstack.tokkip.wellness.PomodoroScheduler
import mx.loperstack.tokkip.wellness.Task
import mx.loperstack.tokkip.wellness.addTask
import mx.loperstack.tokkip.wellness.breathFrame
import mx.loperstack.tokkip.wellness.clearDone
import mx.loperstack.tokkip.wellness.clock
import mx.loperstack.tokkip.wellness.finishPhase
import mx.loperstack.tokkip.wellness.isPaused
import mx.loperstack.tokkip.wellness.isRunning
import mx.loperstack.tokkip.wellness.left
import mx.loperstack.tokkip.wellness.orderedTasks
import mx.loperstack.tokkip.wellness.pause
import mx.loperstack.tokkip.wellness.pendingCount
import mx.loperstack.tokkip.wellness.progress
import mx.loperstack.tokkip.wellness.removeTask
import mx.loperstack.tokkip.wellness.reset
import mx.loperstack.tokkip.wellness.resume
import mx.loperstack.tokkip.wellness.startFocus
import mx.loperstack.tokkip.wellness.toggleTask
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Inicio", Icons.Rounded.Home), AGENDA("Agenda", Icons.Rounded.CalendarMonth), TASKS("Tareas", Icons.Rounded.TaskAlt), FOCUS("Enfoque", Icons.Rounded.Timer),
    STREAKS("Rachas", Icons.Rounded.LocalFireDepartment), BREATHE("Respirar", Icons.Rounded.Air), DIARY("Diario", Icons.Rounded.AutoStories), MONEY("Dinero", Icons.Rounded.Savings),
    SLEEP("Sueño", Icons.Rounded.Bedtime), SCREEN("Pantalla", Icons.Rounded.PhoneAndroid), PET("Mimos", Icons.Rounded.Favorite),
}

/** The floating bar's five places, filled when selected. */
private val NAV_ITEMS = listOf(
    NavItem("Inicio", Icons.Outlined.OHome, Icons.Rounded.Home),
    NavItem("Tareas", Icons.Outlined.OTaskAlt, Icons.Rounded.TaskAlt),
    NavItem("Diario", Icons.Outlined.OAutoStories, Icons.Rounded.AutoStories),
    NavItem("Bienestar", Icons.Outlined.OSpa, Icons.Rounded.Spa),
    NavItem("Tokkip", Icons.Outlined.OFavoriteBorder, Icons.Rounded.Favorite),
)

/** The bottom bar's five places (Material 3: three to five); two of them hold two screens each. */
enum class Group(val label: String, val tabs: List<Tab>) {
    HOME("Inicio", listOf(Tab.HOME, Tab.AGENDA)),
    TASKS("Tareas", listOf(Tab.TASKS, Tab.FOCUS, Tab.STREAKS)),
    DIARY("Diario", listOf(Tab.DIARY, Tab.MONEY)),
    CALM("Bienestar", listOf(Tab.BREATHE, Tab.SLEEP, Tab.SCREEN)),
    PET("Tokkip", listOf(Tab.PET));

    companion object {
        fun of(tab: Tab) = entries.first { tab in it.tabs }
    }
}

private val ES = Locale("es", "MX")

// ── Actions ───────────────────────────────────────────────────────────────────

/** Everything the screens can do; Tokkip hops through `jump`. */
class Actions(private val context: Context) {
    var jump by mutableIntStateOf(0)
    var breathing by mutableStateOf(Breathing())
    /** Music is playing on the phone: Tokkip dances. */
    var music by mutableStateOf(false)

    // The watch's pulse during the session (0 = none yet), and every reading with its time.
    private val hrReadings = mutableListOf<Pair<Long, Int>>()
    var hrStart = 0
    var hrMin = 0
    var hrLast = 0

    fun addTask(text: String): Boolean {
        if (!TaskSync.add(context, text)) return false
        Sounds.play("pop")
        return true
    }

    fun toggle(task: Task) {
        TaskSync.toggle(context, task.id)
        if (!task.done) {
            jump++
            Sounds.play("task_done")
            Haptics.success(context)
        } else {
            Sounds.play("blip")
            Haptics.click(context)
        }
    }

    fun remove(task: Task) {
        TaskSync.remove(context, task.id)
        Sounds.play("blip")
    }

    fun clearDone() {
        TaskSync.clearDone(context)
        Sounds.play("blip")
    }

    /** Start, pause or resume — the main button. */
    fun playPause() {
        val now = System.currentTimeMillis()
        val p = Store.state.value.pomodoro
        val next = when {
            p.phase == Phase.IDLE -> p.startFocus(now).also { jump++; Sounds.play("focus_start") }
            p.isPaused() -> p.resume(now)
            else -> p.pause(now)
        }
        if (p.phase != Phase.IDLE) Sounds.play("blip")
        Store.setPomodoro(next)
        PomodoroScheduler.sync(context, next, now)
    }

    fun skip() {
        val p = Store.state.value.pomodoro
        if (p.phase == Phase.IDLE) return
        val (next, _) = p.finishPhase(System.currentTimeMillis(), counted = false)
        Store.setPomodoro(next)
        PomodoroScheduler.sync(context, next)
    }

    fun resetPomodoro() {
        val next = Store.state.value.pomodoro.reset()
        Store.setPomodoro(next)
        PomodoroScheduler.sync(context, next)
    }

    fun startBreathing(minutes: Int) {
        val b = Store.state.value.breathing(System.currentTimeMillis(), minutes)
        breathing = b
        hrStart = 0; hrMin = 0; hrLast = 0
        hrReadings.clear()
        Sounds.play("open")
        WearSync.breatheTogether(context, b)
    }

    fun heartRate(bpm: Int) {
        if (bpm <= 0) return
        if (hrStart == 0) hrStart = bpm
        hrMin = if (hrMin == 0) bpm else minOf(hrMin, bpm)
        hrLast = bpm
        hrReadings += System.currentTimeMillis() to bpm
    }

    /** The readings as the session's pulse: one value every HR_STEP_S seconds, the latest one known. */
    private fun hrSeries(start: Long, minutes: Int): List<Int> {
        if (hrReadings.isEmpty()) return emptyList()
        val n = minutes * 60 / mx.loperstack.tokkip.wellness.HR_STEP_S
        return List(n) { i ->
            val t = start + i * mx.loperstack.tokkip.wellness.HR_STEP_S * 1000L
            hrReadings.lastOrNull { it.first <= t + 1500 }?.second ?: 0
        }
    }

    fun stopBreathing(completed: Boolean) {
        val b = breathing
        val start = b.startedAt
        breathing = Breathing()
        if (completed && start != null) {
            jump++
            Sounds.play("love")
            val withWatch = hrLast > 0
            Store.addSession(
                BreathSession(
                    start, b.minutes, b.minutes * 6, hrStart, hrLast, hrMin,
                    device = if (withWatch) "watch" else "phone", hr = hrSeries(start, b.minutes),
                ),
            )
            Haptics.stop(context)
            Haptics.success(context)
        } else {
            Sounds.play("close")
            Haptics.stop(context)
            WearSync.stopTogether(context)
        }
    }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

// ── Clocks ────────────────────────────────────────────────────────────────────

/** The time, refreshed on each whole second. */
@Composable
fun rememberNow(periodMs: Long = 1000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs - now % periodMs)
        }
    }
    return now
}

/** The time on every frame, while `active`. */
@Composable
fun rememberFrameNow(active: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        while (active) {
            withFrameMillis { now = System.currentTimeMillis() }
        }
    }
    return now
}

fun hourMinute(ms: Long): String = SimpleDateFormat("HH:mm", ES).format(Date(ms))

fun greeting(now: Long): String = when (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}

fun longDate(now: Long): String =
    SimpleDateFormat("EEEE d 'de' MMMM", ES).format(Date(now)).replaceFirstChar { it.uppercase(ES) }

// ── Root ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokkipApp(startTab: Tab = Tab.HOME, breatheNow: Pair<Int, Long> = 0 to 0L, composeNow: Long = 0L, moneyAdd: Pair<String, Long> = "" to 0L) {
    val context = LocalContext.current
    val data by Store.state.collectAsState()
    val actions = remember { Actions(context) }
    var tab by remember { mutableStateOf(startTab) }
    // Each place remembers its last screen (Tareas ↔ Enfoque, Respirar ↔ Sueño).
    val lastIn = remember { mutableMapOf<Group, Tab>() }
    SideEffect { lastIn[Group.of(tab)] = tab }
    LaunchedEffect(startTab) { tab = startTab }
    // The Respirar widget's 1 · 5 · 10 buttons.
    // The Dinero widget's "Anotar": straight to the sheet.
    LaunchedEffect(moneyAdd) { if (moneyAdd.second != 0L) tab = Tab.MONEY }
    LaunchedEffect(breatheNow) {
        val (min, key) = breatheNow
        if (min > 0 && key != 0L) {
            tab = Tab.BREATHE
            if (actions.breathing.startedAt == null) actions.startBreathing(min)
        }
    }
    var showColors by remember { mutableStateOf(false) }
    var showNewEvent by remember { mutableStateOf(false) }
    var agendaKey by remember { mutableIntStateOf(0) }
    val now = rememberNow()
    val music = rememberMusicPlaying()
    SideEffect { actions.music = music }

    // The agenda, with READ_CALENDAR.
    var calendarOk by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
    }
    var events by remember { mutableStateOf(emptyList<CalEvent>()) }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calendarOk = it }
    val minute = now / 60_000
    LaunchedEffect(calendarOk, minute / 5, agendaKey) { if (calendarOk) events = Agenda.load(context) }

    // New events go to the calendar: READ and WRITE_CALENDAR.
    val askWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        calendarOk = r[Manifest.permission.READ_CALENDAR] == true || calendarOk
        if (r[Manifest.permission.WRITE_CALENDAR] == true) showNewEvent = true
    }
    val newEvent = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) showNewEvent = true
        else askWrite.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }

    // Google Tasks: Google's consent screen comes back here.
    val googleTasks by GoogleTasks.status.collectAsState()
    val googleConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        (context as? Activity)?.let { GoogleTasks.onResult(it, r.data) }
    }
    val connectGoogle: () -> Unit = {
        (context as? Activity)?.let { a -> GoogleTasks.connect(a) { req -> googleConsent.launch(req) } }
    }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val startPomodoro = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        actions.playPause()
    }

    val color = Color(data.tokkipColor)
    val config = LocalConfiguration.current
    val desk = config.screenWidthDp >= 840 && config.orientation == Configuration.ORIENTATION_LANDSCAPE

    TokkipTheme(data.theme) {
        if (!data.onboarded) {
            Welcome(data, googleTasks, actions, connectGoogle) {
                Store.setOnboarded(true)
                calendarOk = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            }
            return@TokkipTheme
        }
        // Every screen, by tab (portrait and landscape share them).
        val screen: @Composable (Tab) -> Unit = { shown ->
                    when (shown) {
                        Tab.HOME -> HomeScreen(
                            data.tasks, data.pomodoro, events, now, color, actions, calendarOk,
                            askCalendar = { askCalendar.launch(Manifest.permission.READ_CALENDAR) },
                            openColors = { showColors = true },
                            goFocus = { tab = Tab.FOCUS }, goBreathe = { tab = Tab.BREATHE }, goTasks = { tab = Tab.TASKS },
                            goMoney = { tab = Tab.MONEY }, goAgenda = { tab = Tab.AGENDA }, goScreen = { tab = Tab.SCREEN }, goStreaks = { tab = Tab.STREAKS },
                            agendaChanged = { agendaKey++ },
                            newEvent = newEvent,
                        )
                        Tab.TASKS -> TasksScreen(data.tasks, actions)
                        Tab.FOCUS -> FocusScreen(data.tasks, data.pomodoro, now, color, actions, startPomodoro)
                        Tab.BREATHE -> BreatheScreen(color, actions, data.sessions)
                        Tab.DIARY -> DiaryScreen(data.logs, color, actions, composeNow) { tab = Tab.BREATHE }
                        Tab.SLEEP -> SleepScreen(data, color, actions)
                        Tab.PET -> PetScreen(color, actions)
                        Tab.STREAKS -> StreaksScreen(data.habits, color, actions)
                        Tab.AGENDA -> AgendaScreen(
                            calendarOk, askCalendar = { askCalendar.launch(Manifest.permission.READ_CALENDAR) }, actions,
                            newEvent = newEvent, reloadKey = agendaKey,
                        )
                        Tab.SCREEN -> ScreenTimeScreen(data, color, actions)
                        Tab.MONEY -> MoneyScreen(data, color, actions, moneyAdd)
                    }
        }
        val pickGroup: (Int) -> Unit = { i ->
            val current = Group.of(tab)
            val g = Group.entries[i]
            if (current != g) Haptics.tick(context)
            tab = if (current == g) tab else lastIn[g] ?: g.tabs.first()
        }
        if (desk) {
            // Landscape: a rail on the side; Inicio is the desk, the rest keep a readable width.
            Row(Modifier.fillMaxSize().background(Ink.Ground)) {
                NavRail(NAV_ITEMS, Group.entries.indexOf(Group.of(tab)), pickGroup)
                Column(Modifier.weight(1f).fillMaxHeight().statusBarsPadding()) {
                    if (tab == Tab.HOME) {
                        DeskScreen(
                            data, events, now, color, actions, startPomodoro, calendarOk,
                            askCalendar = { askCalendar.launch(Manifest.permission.READ_CALENDAR) },
                            goAgenda = { tab = Tab.AGENDA }, goMoney = { tab = Tab.MONEY }, goScreen = { tab = Tab.SCREEN },
                            agendaChanged = { agendaKey++ },
                        )
                    } else {
                        val group = Group.of(tab)
                        if (group.tabs.size > 1) SubTabs(group.tabs, tab) { Haptics.tick(context); tab = it }
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) { screen(tab) }
                        }
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = Ink.Ground,
                bottomBar = { FloatingNavBar(NAV_ITEMS, Group.entries.indexOf(Group.of(tab)), pickGroup) },
            ) { pad ->
              Column(Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize()) {
                val group = Group.of(tab)
                // Inicio keeps its own top; the pills show from Agenda, to go back.
                if (group.tabs.size > 1 && tab != Tab.HOME) {
                    SubTabs(group.tabs, tab) { Haptics.tick(context); tab = it }
                }
                AnimatedContent(
                    targetState = tab, modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(tween(240)) + slideInHorizontally(tween(320)) { it / 8 * dir }) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { -it / 8 * dir })
                    },
                    label = "tab",
                ) { shown -> screen(shown) }
              }
            }
        }
        if (showNewEvent) {
            ModalBottomSheet(onDismissRequest = { showNewEvent = false }, containerColor = Ink.CardHigh) {
                NewEventSheet(actions) {
                    showNewEvent = false
                    calendarOk = true
                    agendaKey++
                }
            }
        }
        if (showColors) {
            ModalBottomSheet(onDismissRequest = { showColors = false }, containerColor = Ink.CardHigh) {
                SettingsSheet(
                    data, color, googleTasks, connectGoogle, syncGoogle = { TaskSync.refresh(context) },
                    disconnectGoogle = { GoogleTasks.disconnect(context) },
                    replayWelcome = { showColors = false; Store.setOnboarded(false) },
                )
            }
        }
    }
}

// ── Shared pieces ─────────────────────────────────────────────────────────────

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(Squircle(30.dp))
            .background(Ink.Card)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Label(text: String, color: Color = Ink.Dim, size: TextUnit = 13.sp, weight: FontWeight = FontWeight.SemiBold) =
    Text(text, color = color, fontSize = size, fontWeight = weight)

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, color: Color = Ink.Text, textColor: Color = Ink.Ground, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) =
    ExpressiveButton(text, modifier, icon = icon, container = color, content = textColor, onClick = onClick)

@Composable
fun RoundIcon(glyph: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) =
    CircleButton(glyph, description, size = 52.dp, onClick = onClick)

@Composable
fun TaskRow(task: Task, actions: Actions, showDelete: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { actions.toggle(task) }.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.done, onCheckedChange = { actions.toggle(task) },
            colors = CheckboxDefaults.colors(checkedColor = Ink.Violet, uncheckedColor = Ink.Dim2, checkmarkColor = Ink.Ground),
        )
        Text(
            task.text, modifier = Modifier.weight(1f), fontSize = 15.sp,
            color = if (task.done) Ink.Dim2 else Ink.Text,
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (showDelete) {
            IconButton(onClick = { actions.remove(task) }) {
                Icon(Glyph.Close, contentDescription = "Eliminar", tint = Ink.Dim2, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun NewTaskField(actions: Actions, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    val submit = { if (actions.addTask(text)) text = "" }
    OutlinedTextField(
        value = text, onValueChange = { text = it.take(200) },
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Nueva tarea…", color = Ink.Dim2) },
        singleLine = true,
        shape = Squircle(18.dp),
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            IconButton(onClick = { submit() }) { Icon(Glyph.Plus, contentDescription = "Agregar", tint = Ink.Violet) }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink.Violet, unfocusedBorderColor = Ink.Hairline,
            focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = Ink.Violet,
        ),
    )
}

@Composable
fun EventRow(e: CalEvent, highlight: Boolean, now: Long, actions: Actions, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(Squircle(14.dp))
            .background(if (highlight) Ink.Violet.copy(alpha = 0.14f) else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 52.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(e.color?.let { Color(it) } ?: Ink.Violet))
        val started = !e.allDay && e.begin <= now
        Text(
            if (e.allDay) "Todo el día" else if (started) "Ahora" else hourMinute(e.begin),
            color = if (started) Ink.Green else Ink.VioletSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(76.dp),
        )
        Text(e.title, modifier = Modifier.weight(1f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (e.meetUrl != null) {
            Text(
                "Unirse", color = Ink.Green, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(Squircle(12.dp)).background(Ink.Green.copy(alpha = 0.18f))
                    .clickable { actions.open(e.meetUrl) }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun AgendaList(events: List<CalEvent>, now: Long, actions: Actions, max: Int = 20, open: ((CalEvent) -> Unit)? = null) {
    val list = upcoming(events, now).take(max)
    val next = nextEvent(events, now)
    var tomorrowShown = false
    if (list.isEmpty()) Label("Nada más por hoy — disfrútalo.", Ink.Dim2)
    for (e in list) {
        if (!tomorrowShown && isTomorrow(e, now)) {
            tomorrowShown = true
            Text("MAÑANA", color = Ink.Dim2, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp))
        }
        EventRow(e, e == next, now, actions, open?.let { o -> { o(e) } })
    }
}

/** "En 12 min", "En 2 h", or the start time. */
fun untilText(e: CalEvent, now: Long): String {
    val min = ((e.begin - now) / 60_000).toInt()
    return when {
        min < 1 -> "Ahora"
        min < 60 -> "En $min min"
        else -> "A las ${hourMinute(e.begin)}"
    }
}

// ── Inicio ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tasks: List<Task>, pomodoro: mx.loperstack.tokkip.wellness.Pomodoro, events: List<CalEvent>, now: Long, color: Color,
    actions: Actions, calendarOk: Boolean, askCalendar: () -> Unit, openColors: () -> Unit,
    goFocus: () -> Unit, goBreathe: () -> Unit, goTasks: () -> Unit, newEvent: () -> Unit = {}, goMoney: () -> Unit = {},
    goAgenda: () -> Unit = {}, agendaChanged: () -> Unit = {}, goScreen: () -> Unit = {}, goStreaks: () -> Unit = {},
) {
    var openEvent by remember { mutableStateOf<CalEvent?>(null) }
    var customize by remember { mutableStateOf(false) }
    // Reaching a stage: the party, wherever Tokkip y tú is (or even hidden).
    GrowthCelebration(rememberGrowth(now).first)
    // Bienestar → Pantalla's last reading, if it's from the last half hour.
    val doomData = Store.state.collectAsState().value
    val doomNow = if (now - doomData.doomAt < 30 * 60_000L) doomData.doom else 0
    val next = nextEvent(events, now)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Label(longDate(now))
                Spacer(Modifier.height(4.dp))
                ScreenTitle(greeting(now), size = 38.sp)
                val who = Store.state.value.name
                if (who.isNotBlank()) ScreenTitle(who, size = 38.sp, color = Ink.VioletSoft)
            }
            CircleButton(Icons.Rounded.Settings, "Ajustes", onClick = openColors)
        }
        // Inicio, your way: the sections in your order, the hidden ones left out (Personalizar Inicio).
        val home = Store.state.collectAsState().value
        for (section in homeSections(home.homeLayout, home.homeHidden)) when (section) {
            "tokkip" -> {
                Spacer(Modifier.height(16.dp))
                Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
                  Box {
                    Aurora(color, Modifier.matchParentSize())
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    // The cookie sits right behind his body (his centre is a bit below the canvas's).
                    Box(Modifier.align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
                    MorphingBlob(Ink.VioletDeep.copy(alpha = 0.9f), Modifier.padding(top = 18.dp).size(150.dp))
                    // He grows with you (data/Growth.kt): a little one at first, full size at Sabio.
                    val grown = rememberGrowth(now).first.scale
                    Tokkip(
                        Modifier.width(170.dp).height(140.dp).graphicsLayer {
                            scaleX = grown; scaleY = grown
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.8f)
                        },
                        color = color,
                        mood = if (pomodoro.phase == Phase.FOCUS) Mood.FOCUS else Mood.IDLE,
                        // Lots of scrolling (Bienestar → Pantalla, read in the last half hour): tired or dizzy eyes.
                        face = doomNow.takeIf { it >= 2 }?.let { doomEye(it) },
                        jump = actions.jump,
                        interactive = true,
                        greet = true,
                        dancing = actions.music,
                    )
                    }
                    if (actions.music) IconLabel(Icons.Rounded.MusicNote, "Tokkip baila con tu música", Ink.VioletSoft, Modifier.align(Alignment.CenterHorizontally))
                    if (doomNow >= 2) IconLabel(
                        Icons.Rounded.PhoneAndroid,
                        if (doomNow >= 3) "Mucho scroll: Tokkip anda mareado" else "Llevas un buen rato de scroll: Tokkip está cansado",
                        DOOM_COLORS[doomNow], Modifier.align(Alignment.CenterHorizontally).clickable(onClick = goScreen),
                    ) else when (mx.loperstack.tokkip.character.TokkipStyle.rest) {
                        mx.loperstack.tokkip.character.Rest.TIRED -> IconLabel(Icons.Rounded.Bedtime, "Dormiste poco: Tokkip anda con ojeras", Ink.Dim, Modifier.align(Alignment.CenterHorizontally))
                        mx.loperstack.tokkip.character.Rest.GREAT -> IconLabel(Icons.Rounded.AutoAwesome, "Descansaste: Tokkip está de muy buen humor", Ink.GreenSoft, Modifier.align(Alignment.CenterHorizontally))
                        else -> Unit
                    }
                    Spacer(Modifier.height(10.dp))
                    when {
                        !calendarOk -> {
                            Text("Tu agenda aquí", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                            Label("Tokkip lee los calendarios de este teléfono, Google Calendar incluido.", Ink.Dim)
                            Spacer(Modifier.height(10.dp))
                            PrimaryButton("Ver mi agenda", Modifier.align(Alignment.CenterHorizontally), onClick = askCalendar)
                        }
                        next != null -> {
                            Text(
                                "SIGUIENTE · ${untilText(next, now).uppercase(ES)}", color = Ink.VioletSoft, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                            Text(next.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Label("${hourMinute(next.begin)} – ${hourMinute(next.end)}", modifier = Modifier.align(Alignment.CenterHorizontally))
                            if (next.meetUrl != null) {
                                Spacer(Modifier.height(12.dp))
                                PrimaryButton("Unirse", Modifier.align(Alignment.CenterHorizontally), Ink.Green, Ink.GreenDeep) { actions.open(next.meetUrl) }
                            }
                        }
                        else -> Text("Nada más en tu agenda hoy", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    }
                  }
                }
            }
            "music" -> {
                val track = rememberNowPlaying()
                if (track != null && NowPlaying.granted(LocalContext.current)) {
                    Spacer(Modifier.height(12.dp))
                    NowPlayingCard(track)
                }
            }
            "growth" -> { Spacer(Modifier.height(12.dp)); GrowthCard(now) }
            "money" -> MoneyHomeCard(now, goMoney)
            "streaks" -> StreaksHomeCard(now, goStreaks)
            "screen" -> ScreenHomeCard(now, goScreen)
            "agenda" -> {
                Spacer(Modifier.height(20.dp))
                if (calendarOk) {
                    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clip(Squircle(12.dp)).clickable(onClick = goAgenda), verticalAlignment = Alignment.CenterVertically) {
                            Text("Agenda", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "Ver la agenda", tint = Ink.Dim)
                        }
                        Pill("Evento", true, Icons.Rounded.Add, onClick = newEvent)
                    }
                    Spacer(Modifier.height(8.dp))
                    Panel(Modifier.fillMaxWidth(), padding = 8.dp) {
                        AgendaList(events, now, actions, max = 5) { openEvent = it }
                        Text(
                            "Ver toda la agenda", color = Ink.VioletSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.clip(Squircle(12.dp)).clickable(onClick = goAgenda).padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            "shortcuts" -> {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile("Enfoque", if (pomodoro.phase == Phase.IDLE) "%d:00".format(mx.loperstack.tokkip.wellness.PomodoroConfig.current.focusMin) else clock(pomodoro.left(now)), Ink.VioletDeep, Ink.VioletLine, Ink.OnVioletDeep, Modifier.weight(1f), goFocus, icon = Icons.Rounded.Timer, shape = MShapes.Cookie9)
                    Tile("Respirar", "Toma un respiro", Ink.Green.copy(alpha = 0.12f), Ink.Green.copy(alpha = 0.22f), Ink.GreenSoft, Modifier.weight(1f), goBreathe, small = true, icon = Icons.Rounded.Air, shape = MShapes.Clover4)
                }
            }
            "tasks" -> {
                Spacer(Modifier.height(20.dp))
                Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Tareas", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Pill("${pendingCount(tasks)} pendientes", false, Icons.Rounded.ChevronRight, onClick = goTasks)
                }
                Spacer(Modifier.height(8.dp))
                Panel(Modifier.fillMaxWidth(), padding = 8.dp) {
                    for (t in orderedTasks(tasks).filter { !it.done }.take(4)) TaskRow(t, actions)
                    NewTaskField(actions, Modifier.padding(6.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Personalizar Inicio", color = Ink.VioletSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(Squircle(14.dp)).clickable { customize = true }.padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(20.dp))
    }
    openEvent?.let { e ->
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { openEvent = null; agendaChanged() }, containerColor = Ink.CardHigh,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) { EventSheet(e, actions) { changed -> openEvent = null; if (changed) agendaChanged() } }
    }
    if (customize) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { customize = false }, containerColor = Ink.CardHigh,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) { HomeCustomizer() }
    }
}

@Composable
fun Label(text: String, color: Color = Ink.Dim, modifier: Modifier) =
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)

@Composable
fun Tile(
    title: String, value: String, bg: Color, line: Color, accent: Color, modifier: Modifier, onClick: () -> Unit, small: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null, shape: androidx.graphics.shapes.RoundedPolygon = MShapes.Cookie9,
) {
    Column(
        modifier.height(132.dp).clip(Squircle(28.dp)).background(bg)
            .clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        if (icon != null) ShapeIcon(icon, shape = shape, container = accent.copy(alpha = 0.22f), tint = accent, size = 42.dp)
        Column {
            Text(title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(value, fontSize = if (small) 16.sp else 28.sp, fontWeight = FontWeight.ExtraBold, color = if (accent == Ink.OnVioletDeep) accent else Ink.Text, maxLines = 1)
        }
    }
}

/** A small label with its icon in front. */
@Composable
fun IconLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color = Ink.Dim, modifier: Modifier = Modifier, size: TextUnit = 13.sp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = size, fontWeight = FontWeight.SemiBold)
    }
}

// ── Tareas ────────────────────────────────────────────────────────────────────

@Composable
fun TasksScreen(tasks: List<Task>, actions: Actions) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 6.dp), verticalAlignment = Alignment.Bottom) {
            ScreenTitle("Tareas", Modifier.weight(1f))
            val pending = pendingCount(tasks)
            Label(if (tasks.isEmpty()) "" else if (pending == 0) "¡Todo listo!" else "$pending pendientes")
        }
        Spacer(Modifier.height(12.dp))
        NewTaskField(actions)
        Spacer(Modifier.height(10.dp))
        if (tasks.isEmpty()) {
            Label("Nada pendiente — agrega tu primera tarea.", Ink.Dim2)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(orderedTasks(tasks), key = { it.id }) { t -> TaskRow(t, actions, showDelete = true) }
        }
        if (tasks.any { it.done }) {
            OutlinedButton(onClick = { actions.clearDone() }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)) {
                Text("Borrar hechas", color = Ink.Dim)
            }
        }
    }
}

// ── Enfoque ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(tasks: List<Task>, p: mx.loperstack.tokkip.wellness.Pomodoro, now: Long, color: Color, actions: Actions, start: () -> Unit) {
    val track = rememberNowPlaying()
    // A break starting while you look: the kalimba goes down.
    var lastPhase by remember { mutableStateOf(p.phase) }
    LaunchedEffect(p.phase) {
        if (lastPhase == Phase.FOCUS && (p.phase == Phase.BREAK || p.phase == Phase.LONG_BREAK)) Sounds.play("focus_break")
        lastPhase = p.phase
    }
    var aod by remember { mutableStateOf(false) }
    var tune by remember { mutableStateOf(false) }
    val cfg = Store.state.collectAsState().value.pomodoroConfig
    if (aod) FocusAod(p, color, track) { aod = false }
    if (tune) {
        ModalBottomSheet(onDismissRequest = { tune = false }, containerColor = Ink.CardHigh) { PomodoroSettings(cfg) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Label("Pomodoro · ${p.today} hoy")
            ScreenTitle(PomodoroScheduler.phaseTitle(p.phase), size = 36.sp)
        }
        Spacer(Modifier.height(18.dp))
        FocusRing(p, now, color, Modifier.size(260.dp), dancing = actions.music)
        val current = orderedTasks(tasks).firstOrNull { !it.done }
        if (current != null && p.phase == Phase.FOCUS) {
            Spacer(Modifier.height(14.dp))
            IconLabel(
                Icons.Rounded.TaskAlt, current.text, Ink.OnVioletDeep,
                Modifier.clip(CircleShape).background(Ink.VioletDeep).padding(horizontal = 14.dp, vertical = 9.dp), 14.sp,
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ExpressiveButton(
                if (p.phase == Phase.IDLE) "Iniciar" else if (p.isPaused()) "Reanudar" else "Pausa",
                icon = if (p.phase != Phase.IDLE && !p.isPaused()) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, big = true, onClick = start,
            )
            if (p.phase != Phase.IDLE) {
                RoundIcon(Glyph.Skip, "Saltar fase") { actions.skip() }
                RoundIcon(Glyph.Reset, "Reiniciar") { actions.resetPomodoro() }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Personalizar", false, Icons.Rounded.Tune) { tune = true }
            Pill("Modo AOD", false, Icons.Rounded.DarkMode) { aod = true }
        }
        Spacer(Modifier.height(16.dp))
        NowPlayingCard(track)
        Spacer(Modifier.height(14.dp))
        Label("${cfg.focusMin} min de enfoque · ${cfg.breakMin} de descanso · uno largo de ${cfg.longBreakMin} cada ${cfg.cyclesBeforeLong} bloques. Te avisa aunque la app esté cerrada.", Ink.Dim2)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun FocusRing(p: mx.loperstack.tokkip.wellness.Pomodoro, now: Long, color: Color, modifier: Modifier, tokkip: Boolean = true, dancing: Boolean = false, textSize: TextUnit = 46.sp) {
    val ring = if (p.phase == Phase.BREAK || p.phase == Phase.LONG_BREAK) Ink.Green else Ink.Violet
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.05f
            val inset = stroke / 2
            drawArc(Ink.Track, 0f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
            // The done part waves, like Android 16's progress rings.
            val sweep = 360f * p.progress(now)
            if (sweep > 0.5f) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r0 = (size.minDimension - stroke) / 2f - stroke * 0.35f
                val amp = stroke * 0.32f
                val waves = 14
                val path = androidx.compose.ui.graphics.Path()
                var a = 0f
                while (a <= sweep) {
                    val rad = Math.toRadians((a - 90f).toDouble())
                    val rr = r0 + amp * kotlin.math.sin(Math.toRadians((a * waves).toDouble())).toFloat()
                    val x = c.x + rr * kotlin.math.cos(rad).toFloat()
                    val y = c.y + rr * kotlin.math.sin(rad).toFloat()
                    if (a == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    a += 1.5f
                }
                drawPath(path, ring, style = Stroke(stroke * 0.8f, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (tokkip) {
                Tokkip(Modifier.width(90.dp).height(72.dp), color = color, mood = if (p.phase == Phase.FOCUS) Mood.FOCUS else Mood.IDLE, dancing = dancing)
            }
            Text(clock(p.left(now)), fontSize = textSize, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

// ── Respirar: BreatheScreen and the history live in Breathe.kt ─────────────

// ── PC ────────────────────────────────────────────────────────────────────────

fun batteryLevel(context: Context): Pair<Int, Boolean> {
    val bm = context.getSystemService(BatteryManager::class.java)
    return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) to bm.isCharging
}

@Composable
fun PcScreen(now: Long) {
    val context = LocalContext.current
    val (level, charging) = remember(now / 30_000) { batteryLevel(context) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Column(Modifier.padding(top = 18.dp, start = 6.dp)) {
            Label("Fase 2 · próximamente")
            ScreenTitle("Conexión con la PC", size = 32.sp)
        }
        Spacer(Modifier.height(16.dp))
        Panel(Modifier.fillMaxWidth()) {
            Label("Este teléfono", weight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$level", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Label("%", modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            }
            LinearProgressIndicator(
                progress = { level / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = if (level <= 15) Ink.Red else Ink.Green, trackColor = Ink.Track,
            )
            Spacer(Modifier.height(8.dp))
            Label(if (charging) "Cargando" else "Con batería", if (charging) Ink.GreenSoft else Ink.Dim)
        }
        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth()) {
            Text("Lo que viene", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            for (line in listOf(
                "Ver la batería del teléfono y de la tablet en la isla de la PC",
                "Portapapeles compartido: textos e imágenes entre PC y teléfono",
                "Aprobar los permisos de Claude Code desde aquí",
                "Emparejar con un código QR, cifrado y solo en tu red",
            )) {
                Row(Modifier.padding(vertical = 6.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(Ink.Violet))
                    Text(line, fontSize = 15.sp, color = Ink.Dim, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

// ── Color de Tokkip: ColorPicker.kt ─────────────────────────────────────────────

// ── Tablet: modo escritorio ───────────────────────────────────────────────────

/**
 * Inicio lying down (a phone on its side, a tablet): the clock and Tokkip,
 * what's playing; today and tomorrow with Tokkip's growth and notes and your
 * money; focus and tasks. Sizes follow the height so nothing spills.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeskScreen(
    data: mx.loperstack.tokkip.data.AppData, events: List<CalEvent>, now: Long, color: Color,
    actions: Actions, start: () -> Unit, calendarOk: Boolean, askCalendar: () -> Unit,
    goAgenda: () -> Unit, goMoney: () -> Unit, goScreen: () -> Unit, agendaChanged: () -> Unit,
) {
    val p = data.pomodoro
    var openEvent by remember { mutableStateOf<CalEvent?>(null) }
    val doomNow = if (now - data.doomAt < 30 * 60_000L) data.doom else 0
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val short = maxHeight < 520.dp
        val gap = if (short) 12.dp else 20.dp
        Row(Modifier.fillMaxSize().padding(gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
            // The clock and Tokkip.
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(Squircle(30.dp))
                    .background(Brush.radialGradient(listOf(Ink.VioletDeep, Ink.Card), radius = 1100f))
                    .border(1.dp, Ink.Hairline, Squircle(30.dp)).padding(if (short) 18.dp else 28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Label(longDate(now), Ink.Dim, if (short) 13.sp else 16.sp, FontWeight.SemiBold)
                    Text(hourMinute(now), fontSize = if (short) 64.sp else 96.sp, fontWeight = FontWeight.ExtraBold, lineHeight = if (short) 64.sp else 96.sp)
                }
                Tokkip(
                    Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(1.3f), color = color,
                    mood = if (p.phase == Phase.FOCUS) Mood.FOCUS else Mood.IDLE, jump = actions.jump,
                    interactive = true, greet = true, dancing = actions.music,
                    face = doomNow.takeIf { it >= 2 }?.let { doomEye(it) },
                )
                val track = rememberNowPlaying()
                if (track != null && NowPlaying.granted(LocalContext.current)) NowPlayingCard(track)
                else if (doomNow >= 2) IconLabel(Icons.Rounded.PhoneAndroid, "Mucho scroll: Tokkip ${if (doomNow >= 3) "mareado" else "cansado"}", DOOM_COLORS[doomNow], Modifier.clickable(onClick = goScreen))
                else Label("Toca a Tokkip", Ink.Dim2, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            // Today, Tokkip and your money.
            Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
                    Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Hoy y mañana", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        Text("Agenda", color = Ink.VioletSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clip(Squircle(12.dp)).clickable(onClick = goAgenda).padding(8.dp))
                    }
                    if (!calendarOk) PrimaryButton("Ver mi agenda", onClick = askCalendar) else AgendaList(events, now, actions, max = 6) { openEvent = it }
                }
                Spacer(Modifier.height(gap))
                GrowthCard(now)
                MoneyHomeCard(now, goMoney)
            }
            // Focus and tasks.
            Column(Modifier.weight(0.95f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FocusRing(p, now, color, Modifier.size(if (short) 84.dp else 104.dp), tokkip = false, textSize = if (short) 20.sp else 24.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Label(PomodoroScheduler.phaseTitle(p.phase), Ink.VioletSoft, weight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            PrimaryButton(if (p.phase == Phase.IDLE) "Iniciar" else if (p.isPaused()) "Reanudar" else "Pausa", onClick = start)
                        }
                    }
                }
                Panel(Modifier.fillMaxWidth().weight(1f), padding = 12.dp) {
                    Text("Tareas", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        for (t in orderedTasks(data.tasks)) TaskRow(t, actions)
                    }
                    NewTaskField(actions)
                }
            }
        }
    }
    openEvent?.let { e ->
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { openEvent = null; agendaChanged() }, containerColor = Ink.CardHigh,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) { EventSheet(e, actions) { changed -> openEvent = null; if (changed) agendaChanged() } }
    }
}

// ── Google Tasks (in the settings sheet) ──────────────────────────────────────

@Composable
fun GoogleTasksRow(st: GoogleTasks.Status, connect: () -> Unit, sync: () -> Unit, disconnect: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Google Tasks", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Label(
                    when {
                        st.busy -> "Sincronizando…"
                        st.connected -> "Conectado · la misma lista que en tu PC"
                        else -> "Tus tareas, también en Google y en la PC"
                    },
                    if (st.connected) Ink.GreenSoft else Ink.Dim2,
                )
            }
            if (st.connected) {
                OutlinedButton(onClick = sync) { Text("Sincronizar", color = Ink.Text) }
            } else {
                PrimaryButton("Conectar", color = Ink.VioletSoft, onClick = connect)
            }
        }
        if (st.error.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Label(st.error, Ink.Red)
        }
        if (!st.connected) GoogleSetupHelp()
        if (st.connected) {
            Text(
                "Desconectar", color = Ink.Dim2, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp).clickable(onClick = disconnect),
            )
        }
        Spacer(Modifier.height(10.dp))
        Label("Los eventos nuevos se guardan en tu Google Calendar de este teléfono, sin conectar nada.", Ink.Dim2, 12.sp)
    }
}


/** A place's screens, as pill chips with icons (PixelPlayer's "Songs · Albums"). */
@Composable
fun SubTabs(tabs: List<Tab>, current: Tab, pick: (Tab) -> Unit) {
    PillRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp)) {
        for (t in tabs) Pill(t.label, t == current, t.icon) { if (t != current) pick(t) }
    }
}
