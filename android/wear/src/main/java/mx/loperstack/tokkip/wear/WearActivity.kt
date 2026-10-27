// Tokkip on the wrist: Tokkip himself (tap him, as on the PC), the tasks (check
// them off, dictate new ones), breathing with a gentle buzz on each turn and
// your pulse — started here or from the phone — and the batteries.
//
// Kept light for the watch: Tokkip animates on his own Canvas only while he
// moves, clocks tick only where a number shows, and the breathing loop
// recomposes on each inhale/exhale and once a second, not every frame.

package mx.loperstack.tokkip.wear

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.delay
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.rememberMusicPlaying
import mx.loperstack.tokkip.wellness.MOODS
import mx.loperstack.tokkip.wellness.DOOM_LEVELS
import mx.loperstack.tokkip.wellness.moodOf
import mx.loperstack.tokkip.wellness.BREATHE_MINUTES
import mx.loperstack.tokkip.wellness.BreathSession
import mx.loperstack.tokkip.wellness.Breathing
import mx.loperstack.tokkip.wellness.Phase
import mx.loperstack.tokkip.wellness.Pomodoro
import mx.loperstack.tokkip.wellness.Snapshot
import mx.loperstack.tokkip.wellness.breathFrame
import mx.loperstack.tokkip.wellness.cue
import mx.loperstack.tokkip.wellness.clock
import mx.loperstack.tokkip.wellness.left
import mx.loperstack.tokkip.wellness.minutesUntil
import mx.loperstack.tokkip.wellness.nextEvents
import mx.loperstack.tokkip.wellness.orderedTasks
import mx.loperstack.tokkip.wellness.pendingCount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Violet = Color(0xFFA78BFA)
private val VioletSoft = Color(0xFFC4B5FD)
private val Green = Color(0xFF4ADE80)
private val Dim = Color(0xFFA5A9B1)
private val Pink = Color(0xFFFF7896)
private val HM = SimpleDateFormat("HH:mm", Locale("es", "MX"))

class WearActivity : ComponentActivity() {
    companion object {
        /** A screen to open: "mood" (from the mood complication). */
        const val EXTRA_SCREEN = "screen"
    }

    private val askOnce = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchStore.init(this)
        Sounds.init(this)
        handle(intent)
        val missing = buildList {
            add(Manifest.permission.BODY_SENSORS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty() && savedInstanceState == null) askOnce.launch(missing.toTypedArray())
        setContent { WearApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** tokkip://breathe?at=…&min=… — the phone started a session. */
    private fun handle(intent: Intent?) {
        intent?.getStringExtra(EXTRA_SCREEN)?.let { WatchStore.openScreen.value = it }
        val uri = intent?.data ?: return
        if (uri.scheme != "tokkip" || uri.host != "breathe") return
        val at = uri.getQueryParameter("at")?.toLongOrNull() ?: return
        val min = uri.getQueryParameter("min")?.toIntOrNull() ?: return
        val p = uri.getQueryParameter("p")?.split(",")?.mapNotNull { it.toDoubleOrNull() }
        val b = if (p != null && p.size == 4) Breathing(at, min, p[0], p[1], p[2], p[3]) else Breathing(at, min)
        if (System.currentTimeMillis() - at < min * 60_000L) WatchStore.breathRequest.value = b
        WatchStore.clearBreatheNotice(this)
    }

    override fun onResume() {
        super.onResume()
        WatchStore.foreground = true
        WatchStore.pull()
    }

    override fun onPause() {
        WatchStore.foreground = false
        super.onPause()
    }
}

@Composable
fun WearApp() {
    val nav = rememberSwipeDismissableNavController()
    val snap by WatchStore.state.collectAsState()
    val request by WatchStore.breathRequest.collectAsState()
    var jump by remember { mutableIntStateOf(0) }
    LaunchedEffect(request) {
        if (request != null && nav.currentDestination?.route != "breathe") nav.navigate("breathe")
    }
    val open by WatchStore.openScreen.collectAsState()
    LaunchedEffect(open) {
        val screen = open ?: return@LaunchedEffect
        if (nav.currentDestination?.route != screen) nav.navigate(screen)
        WatchStore.openScreen.value = null
    }
    MaterialTheme(colors = Colors(primary = Violet, onPrimary = Color(0xFF14102A), secondary = Green)) {
        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
            composable("home") { Home(snap, jump) { nav.navigate(it) } }
            composable("tasks") { Tasks(snap) { jump++ } }
            composable("breathe") { Breathe(snap) { nav.navigate(it) } }
            composable("history") { BreathHistory(snap) { nav.navigate("session/$it") } }
            composable("session/{at}") { entry -> SessionScreen(entry.arguments?.getString("at")?.toLongOrNull() ?: 0L, snap) }
            composable("sleep") { SleepReport(snap) { nav.navigate(it) } }
            composable("night/{day}") { entry -> NightReport(snap, entry.arguments?.getString("day") ?: "") }
            composable("battery") { Batteries(snap) }
            composable("mood") { MoodPicker(snap) { nav.popBackStack() } }
            composable("pet") { WatchPet(snap) }
            composable("screen") { ScreenReport(snap) }
        }
    }
}

// ── Clocks ────────────────────────────────────────────────────────────────────

/** The time, refreshed on each whole `periodMs`. */
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

/** Only this text ticks each second, not the screen around it. */
@Composable
fun PomodoroLabel(p: Pomodoro) {
    val now = rememberNow(1000)
    Text("${if (p.phase == Phase.FOCUS) "Enfoque" else "Descanso"} · ${clock(p.left(now))}${if (p.endsAt == null) " ⏸" else ""}")
}

// ── Tokkip ─────────────────────────────────────────────────────────────────────

@Composable
fun Home(snap: Snapshot, jump: Int, go: (String) -> Unit) {
    val listState = rememberScalingLazyListState()
    val focusing = snap.pomodoro.phase == Phase.FOCUS
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Tokkip(
                    Modifier.width(130.dp).height(108.dp),
                    color = Color(snap.tokkipColor),
                    mood = if (focusing) Mood.FOCUS else Mood.IDLE,
                    jump = jump,
                    interactive = true,
                    greet = true,
                    dancing = rememberMusicPlaying(),
                    // Lots of scrolling on the phone: tired or dizzy eyes, like there.
                    face = doomNow(snap).takeIf { it >= 2 }?.let { doomEye(it) },
                )
            }
            if (snap.name.isNotBlank()) {
                item { Text("Hola, ${snap.name}", color = VioletSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
            item { NextEvent(snap) }
            if (snap.doomAt > 0) {
                item {
                    val lvl = DOOM_LEVELS[doomNow(snap)]
                    Chip(
                        onClick = { go("screen") },
                        icon = { ShapedIcon(Icons.Rounded.PhoneAndroid, 1, DOOM_TINTS[lvl.level]) },
                        label = { Text("Pantalla · ${lvl.name}") },
                        secondaryLabel = { Text("${screenMins(snap.scrollMin)} de scroll de ${screenMins(snap.scrollGoal)}") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (snap.pomodoro.phase != Phase.IDLE) {
                item {
                    Chip(
                        onClick = { WatchStore.pomodoro() },
                        label = { PomodoroLabel(snap.pomodoro) },
                        secondaryLabel = { Text("Toca para pausar o seguir") },
                        colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF2D2547)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Chip(
                    onClick = { go("pet") },
                    icon = { ShapedIcon(Icons.Rounded.Favorite, 2) },
                    label = { Text("Mimos") },
                    secondaryLabel = { Text("Juega y apapacha a Tokkip") },
                    colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF3B2A63)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { go("tasks") },
                    icon = { ShapedIcon(Icons.Rounded.TaskAlt, 3) },
                    label = { Text("Pendientes · ${pendingCount(snap.tasks)}") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { go("breathe") },
                    icon = { ShapedIcon(Icons.Rounded.Air, 4) },
                    label = { Text("Respirar") },
                    secondaryLabel = { Text("Con vibración y pulso") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (snap.sleepMin > 0) {
                item {
                    val h = snap.sleepMin / 60
                    val m = snap.sleepMin % 60
                    Chip(
                        onClick = { go("sleep") },
                    icon = { ShapedIcon(Icons.Rounded.Bedtime, 5) },
                        label = { Text("Dormiste $h h ${"%02d".format(m)}") },
                        secondaryLabel = { Text(if (snap.sleepNight == java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())) "Anoche" else "Última noche registrada") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Chip(
                    onClick = { go("mood") },
                    icon = { ShapedIcon(Icons.Rounded.Mood, 6) },
                    label = { Text("¿Cómo te sientes?") },
                    secondaryLabel = { Text(moodOf(WatchStore.todayMood())?.let { "Hoy: ${it.label}" } ?: "Anótalo en tu diario") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { go("battery") },
                    icon = { ShapedIcon(Icons.Rounded.BatteryChargingFull, 7) },
                    label = { Text("Baterías") },
                    secondaryLabel = { Text(if (snap.phoneBattery >= 0) "Teléfono ${snap.phoneBattery}%" else "Sin datos del teléfono") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (snap.pomodoro.phase == Phase.IDLE) {
                item {
                    Chip(
                        onClick = { WatchStore.pomodoro() },
                        icon = { ShapedIcon(Icons.Rounded.Timer, 8) },
                        label = { Text("Iniciar Pomodoro") },
                        secondaryLabel = { Text("${mx.loperstack.tokkip.wellness.PomodoroConfig.current.focusMin} min de enfoque") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun NextEvent(snap: Snapshot) {
    val now = rememberNow(30_000)
    val next = remember(snap.events, now / 60_000) { nextEvents(snap.events, now).firstOrNull { !it.allDay } }
    if (next != null) {
        val min = minutesUntil(next.begin, now)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (min <= 0) "Ahora" else if (min < 60) "En $min min" else HM.format(Date(next.begin)),
                color = VioletSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
            Text(next.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    } else {
        Text(if (snap.at == 0L) "Abre Tokkip en tu teléfono" else "Nada más por hoy", color = Dim, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

// ── Pendientes ────────────────────────────────────────────────────────────────

@Composable
fun Tasks(snap: Snapshot, onDone: () -> Unit) {
    val listState = rememberScalingLazyListState()
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { WatchStore.addTask(it) }
    }
    val tasks = remember(snap.tasks) { orderedTasks(snap.tasks).take(30) }
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { Text("Pendientes · ${pendingCount(snap.tasks)}", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            items(tasks, key = { it.id }) { t ->
                ToggleChip(
                    checked = t.done,
                    onCheckedChange = {
                        WatchStore.toggleTask(t.id)
                        if (!t.done) onDone()
                    },
                    label = { Text(t.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    toggleControl = { Icon(imageVector = ToggleChipDefaults.checkboxIcon(checked = t.done), contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = {
                        dictate.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué hay que hacer?"),
                        )
                    },
                    label = { Text("Dictar tarea") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Respirar ──────────────────────────────────────────────────────────────────

/** Live heart rate from the watch's sensor while `active`; 0 until it reads. */
@Composable
fun rememberHeartRate(active: Boolean): Int {
    val context = LocalContext.current
    var bpm by remember { mutableIntStateOf(0) }
    DisposableEffect(active) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val v = e.values.firstOrNull()?.toInt() ?: 0
                if (v in 30..220 && v != bpm) bpm = v
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (!active) bpm = 0
        if (active && granted && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm?.unregisterListener(listener) }
    }
    return bpm
}

private fun buzz(context: Context, inhale: Boolean) {
    val vm = context.getSystemService(VibratorManager::class.java) ?: return
    val effect = if (inhale) VibrationEffect.createOneShot(70, 120)
    else VibrationEffect.createWaveform(longArrayOf(0, 40, 90, 40), intArrayOf(0, 90, 0, 90), -1)
    runCatching { vm.defaultVibrator.vibrate(effect) }
}

@Composable
fun Breathe(snap: Snapshot, go: (String) -> Unit = {}) {
    val context = LocalContext.current
    var b by remember { mutableStateOf(Breathing()) }
    var linked by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<BreathSession?>(null) }
    val active = b.startedAt != null
    val bpm = rememberHeartRate(active)
    val bpmNow by rememberUpdatedState(bpm)
    val beats = rememberHeartBeats(active)
    var inhaling by remember { mutableStateOf(true) }
    var cue by remember { mutableStateOf("Inhala…") }
    var left by remember { mutableIntStateOf(0) }
    val askSensors = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun start(session: Breathing, fromPhone: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            askSensors.launch(Manifest.permission.BODY_SENSORS)
        }
        summary = null
        linked = fromPhone
        b = session
        Sounds.play("open")
    }

    // The phone asks: open its session, in step with it.
    val request by WatchStore.breathRequest.collectAsState()
    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        if (r.startedAt != b.startedAt) start(r, fromPhone = true)
        WatchStore.breathRequest.value = null
    }
    val stop by WatchStore.stopRequest.collectAsState()
    LaunchedEffect(stop) {
        if (stop != null && linked && active) { Haptics.stop(context); b = Breathing(); linked = false; Sounds.play("close") }
    }

    // The session's clock: buzz on each turn, the pulse to the phone, the end.
    LaunchedEffect(b) {
        val start = b.startedAt ?: return@LaunchedEffect
        var hrStart = 0
        var hrMin = 0
        var lastSent = 0L
        val samples = ArrayList<Int>()
        var nextSample = start
        var lastPhase: mx.loperstack.tokkip.wellness.BreathPhase? = null
        while (true) {
            val now = System.currentTimeMillis()
            val f = breathFrame(b, now)
            if (f.phase != lastPhase && !f.done) {
                lastPhase = f.phase
                inhaling = f.inhaling
                cue = f.phase.cue()
                when (f.phase) {
                    mx.loperstack.tokkip.wellness.BreathPhase.IN -> Haptics.breath(context, true, b.inhale, guide = WatchStore.breathGuide)
                    mx.loperstack.tokkip.wellness.BreathPhase.OUT -> Haptics.breath(context, false, b.exhale, guide = WatchStore.breathGuide)
                    else -> { Haptics.stop(context); Haptics.tick(context) }
                }
            }
            if (f.left != left) left = f.left
            val hr = bpmNow
            while (now >= nextSample && !f.done) {
                samples += hr
                nextSample += mx.loperstack.tokkip.wellness.HR_STEP_S * 1000L
            }
            if (hr > 0) {
                if (hrStart == 0) hrStart = hr
                hrMin = if (hrMin == 0) hr else minOf(hrMin, hr)
                if (now - lastSent >= 3000) { lastSent = now; WatchStore.heartRate(hr) }
            }
            if (f.done) {
                Haptics.stop(context)
                val s = BreathSession(
                    start, b.minutes, f.breaths, hrStart, hr, hrMin, device = "watch",
                    hr = samples.toList(), rr = synchronized(beats) { beats.toList() },
                )
                WatchStore.session(s)
                summary = s
                b = Breathing()
                linked = false
                Sounds.play("love")
                break
            }
            delay(100)
        }
    }

    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }

    if (active) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            BreathRing(b, Modifier.fillMaxSize().padding(4.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${if (linked) "Con tu teléfono · " else ""}quedan ${clock(left)}", color = Dim, fontSize = 11.sp)
                Tokkip(Modifier.width(120.dp).height(98.dp), color = Color(snap.tokkipColor), breathing = b)
                Text(cue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(if (bpm > 0) "♥ $bpm lpm" else "Midiendo pulso…", color = if (bpm > 0) Pink else Dim, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Terminar", color = Dim, fontSize = 12.sp,
                    modifier = Modifier.clip(CircleShape).clickable { Haptics.stop(context); b = Breathing(); linked = false; Sounds.play("close") }.padding(8.dp),
                )
            }
        }
        return
    }

    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            summary?.let { s ->
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("¡Bien hecho!", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${s.minutes} min · ${s.breaths} respiraciones", color = Dim, fontSize = 13.sp)
                        if (s.hrStart > 0 && s.hrEnd > 0) {
                            Text("♥ ${s.hrStart} → ${s.hrEnd} lpm", color = Pink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Guardado en tu teléfono", color = Dim, fontSize = 11.sp)
                    }
                }
                if (s.hr.any { it > 0 }) {
                    item {
                        Chip(
                            onClick = { go("session/${s.at}") },
                            label = { Text("Ver cardiograma") },
                            secondaryLabel = { Text("Pulso y variabilidad") },
                            colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF14361F)),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item { Text("Respira con Tokkip", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            item {
                Chip(
                    onClick = { go("history") },
                    label = { Text("Historial") },
                    secondaryLabel = { Text("Sesiones, pulso y variabilidad") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                var guide by remember { mutableStateOf(WatchStore.breathGuide) }
                ToggleChip(
                    checked = guide,
                    onCheckedChange = {
                        guide = it
                        WatchStore.breathGuide = it
                        Haptics.breath(context, true, 2.0, guide = it)
                    },
                    label = { Text("Vibración guía") },
                    secondaryLabel = { Text(if (guide) "Pulsos al inhalar y exhalar" else "Solo en cada cambio") },
                    toggleControl = { Icon(imageVector = ToggleChipDefaults.switchIcon(checked = guide), contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(BREATHE_MINUTES) { m ->
                Chip(
                    onClick = { start(Breathing(System.currentTimeMillis(), m), fromPhone = false) },
                    label = { Text("$m min") },
                    secondaryLabel = { Text("4 s inhalar · 6 s exhalar") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The session's ring, smooth: it redraws each frame without recomposing. */
@Composable
fun BreathRing(b: Breathing, modifier: Modifier) {
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(b) { while (true) withFrameMillis { frame = it } }
    Canvas(modifier) {
        if (frame < 0) return@Canvas
        val p = breathFrame(b, System.currentTimeMillis()).progress
        val stroke = 5.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(Color(0x14FFFFFF), 290f, 320f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(Violet, 290f, 320f * p, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

// ── Baterías ──────────────────────────────────────────────────────────────────

@Composable
fun Batteries(snap: Snapshot) {
    val context = LocalContext.current
    val now = rememberNow(30_000)
    val watch = remember(now) { context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("Baterías", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BatteryRing(if (snap.phoneBattery >= 0) snap.phoneBattery else null, if (snap.phoneCharging) "Teléfono ⚡" else "Teléfono", Green)
                    BatteryRing(watch, "Reloj", Violet)
                }
            }
            // Your headphones, from the phone (Ajustes → Tus dispositivos).
            if (snap.buds.isEmpty()) {
                item { Text("Audífonos: conéctalos al teléfono y aparecen aquí", color = Color(0xFF6B7079), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp)) }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (b in snap.buds.take(2)) BatteryRing(b.level, b.name.take(12), Color(0xFFFDBA74))
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryRing(level: Int?, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = (level ?: 0) / 100f, modifier = Modifier.fillMaxSize(),
                indicatorColor = if ((level ?: 100) <= 15) Color(0xFFF4505E) else color,
                trackColor = Color(0x14FFFFFF), strokeWidth = 5.dp,
            )
            Text(level?.toString() ?: "–", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = Dim, fontSize = 11.sp)
    }
}

// ── Ánimo ─────────────────────────────────────────────────────────────────────

@Composable
fun MoodPicker(snap: Snapshot, done: () -> Unit) {
    val listState = rememberScalingLazyListState()
    val today = remember(snap) { WatchStore.todayMood() }
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { Text("¿Cómo te sientes?", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            items(MOODS) { m ->
                Chip(
                    onClick = { WatchStore.mood(m.value); done() },
                    label = { Text(m.label, fontWeight = if (today == m.value) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Tokkip(Modifier.width(34.dp).height(28.dp), color = Color(m.color), face = m.face) },
                    colors = if (today == m.value) ChipDefaults.primaryChipColors(backgroundColor = Color(m.color).copy(alpha = 0.35f))
                    else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Latidos (intervalos RR) ───────────────────────────────────────────────────

/**
 * Beat-to-beat intervals (ms) from the heartbeat sensor while `active`, when
 * the watch offers one; empty otherwise (the variability is then estimated).
 */
@Composable
fun rememberHeartBeats(active: Boolean): MutableList<Int> {
    val context = LocalContext.current
    val beats = remember { ArrayList<Int>() }
    DisposableEffect(active) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_HEART_BEAT)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.values.firstOrNull()?.let { it < 0.5f } == true) return // low confidence
                if (last > 0) {
                    val rr = ((e.timestamp - last) / 1_000_000L).toInt()
                    if (rr in 300..2000) synchronized(beats) { beats += rr }
                }
                last = e.timestamp
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (active) synchronized(beats) { beats.clear() }
        if (active && granted && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        onDispose { sm?.unregisterListener(listener) }
    }
    return beats
}

// ── Historial de respiración ──────────────────────────────────────────────────

private val DayHM = SimpleDateFormat("EEE d · HH:mm", Locale("es", "MX"))

@Composable
fun BreathHistory(snap: Snapshot, open: (Long) -> Unit) {
    val listState = rememberScalingLazyListState()
    val list = remember(snap) { WatchStore.allSessions().take(10) }
    val weekStart = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -((get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7))
        }.timeInMillis
    }
    val weekMin = list.filter { it.at >= weekStart }.sumOf { it.minutes }
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("Respiración", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            item { Text("$weekMin min esta semana", color = VioletSoft, fontSize = 13.sp) }
            if (list.isEmpty()) item { Text("Aún no hay sesiones", color = Dim, fontSize = 13.sp) }
            items(list, key = { it.at }) { s ->
                Chip(
                    onClick = { open(s.at) },
                    label = { Text("${s.minutes} min · ${DayHM.format(Date(s.at))}", maxLines = 1) },
                    secondaryLabel = {
                        Text(
                            if (s.hrStart > 0 && s.hrEnd > 0) "♥ ${s.hrStart} → ${s.hrEnd} lpm" else "Sin pulso",
                            color = if (s.hrEnd > 0) Pink else Dim,
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** One session: the trace, the pulse curve, the variability and the minutes. */
@Composable
fun SessionScreen(at: Long, snap: Snapshot) {
    val s = remember(at, snap) { WatchStore.allSessions().firstOrNull { it.at == at } }
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (s == null) {
                item { Text("Sesión no encontrada", color = Dim) }
                return@ScalingLazyColumn
            }
            val stats = mx.loperstack.tokkip.wellness.hrvStats(s)
            item { Text(DayHM.format(Date(s.at)), fontSize = 13.sp, color = Dim) }
            if (stats == null) {
                item { Text("Esta sesión no tiene pulso", fontSize = 14.sp, textAlign = TextAlign.Center) }
                return@ScalingLazyColumn
            }
            item {
                Box(Modifier.fillMaxWidth().height(70.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(Color(0xFF07110B))) {
                    mx.loperstack.tokkip.charts.Cardiogram(mx.loperstack.tokkip.charts.beatIntervals(s), Modifier.fillMaxSize().padding(vertical = 6.dp), windowMs = 3000)
                }
            }
            item { Text("♥ ${stats.avgBpm} lpm · ${stats.minBpm}–${stats.maxBpm}", color = Pink, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            item {
                mx.loperstack.tokkip.charts.PulseCurve(s, Modifier.fillMaxWidth().height(48.dp), Pink, Color(0x22FFFFFF))
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Variabilidad", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "RMSSD ${stats.rmssd} ms · ${mx.loperstack.tokkip.wellness.rmssdLabel(stats.rmssd)}",
                        color = VioletSoft, fontSize = 13.sp,
                    )
                    Text("SDNN ${stats.sdnn} ms", color = Dim, fontSize = 12.sp)
                    Text(if (stats.beatToBeat) "Latido a latido" else "Estimada del pulso", color = Dim, fontSize = 10.sp)
                }
            }
            item { Text("Min · prom · mín–máx", color = Dim, fontSize = 11.sp) }
            items(mx.loperstack.tokkip.wellness.minuteTable(s)) { r ->
                Text("${r.minute}  ·  ${r.avg}  ·  ${r.min}–${r.max}", fontSize = 13.sp)
            }
            item { Text("Trazo ilustrativo, no es un electrocardiograma", color = Dim, fontSize = 10.sp, textAlign = TextAlign.Center) }
        }
    }
}

// ── Sueño ─────────────────────────────────────────────────────────────────────

@Composable
fun SleepReport(snap: Snapshot, open: (String) -> Unit = {}) {
    val listState = rememberScalingLazyListState()
    val nights = snap.nights
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1033), Color(0xFF1E1B4B))))) {
        NightSky(Modifier.fillMaxSize())
        Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
            ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (nights.isEmpty()) {
                    item { Tokkip(Modifier.width(96.dp).height(80.dp), color = Color(snap.tokkipColor), mood = Mood.SLEEPY) }
                    item { Text("Registra tus noches en el teléfono", color = Color(0xFFC7D2FE), fontSize = 13.sp, textAlign = TextAlign.Center) }
                    return@ScalingLazyColumn
                }
                val last = nights.last()
                item { Text(if (last.night == java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(Date())) "ANOCHE" else "ÚLTIMA NOCHE", color = Color(0xFFA5B4FC), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                item { NightSummary(last, Color(snap.tokkipColor)) }
                item {
                    val maxMin = maxOf(9 * 60, nights.maxOf { it.minutes })
                    Canvas(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp)) {
                        val n = nights.size
                        val slot = size.width / maxOf(n, 7)
                        val bw = slot * 0.6f
                        val goalY = size.height * (1 - 8 * 60f / maxMin)
                        drawLine(Color(0x55FFFFFF), Offset(0f, goalY), Offset(size.width, goalY), 1.5f)
                        nights.forEachIndexed { i, e ->
                            val h = size.height * e.minutes / maxMin
                            val c = moodOf(e.quality)?.let { Color(it.color) } ?: Violet
                            val x = slot * (i + (maxOf(n, 7) - n)) + (slot - bw) / 2
                            drawRoundRect(c, Offset(x, size.height - h), Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 2))
                        }
                    }
                }
                val avg = nights.map { it.minutes }.average().toInt()
                item { Text("Promedio ${avg / 60} h ${"%02d".format(avg % 60)} · ${nights.size} noches", color = Color(0xFFC7D2FE), fontSize = 12.sp) }
                items(nights.reversed()) { e ->
                    Chip(
                        onClick = { open("night/${e.night}") },
                        icon = { ShapedIcon(Icons.Rounded.Bedtime, nights.indexOf(e)) },
                        label = { Text("${e.minutes / 60} h ${"%02d".format(e.minutes % 60)}") },
                        secondaryLabel = { Text(java.text.SimpleDateFormat("EEE d MMM", java.util.Locale("es", "MX")).format(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(e.night) ?: Date())) },
                        colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0x332A2460)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** One night, under the stars, like the phone's Sueño. */
@Composable
fun NightReport(snap: Snapshot, night: String) {
    val e = snap.nights.firstOrNull { it.night == night } ?: return
    val listState = rememberScalingLazyListState()
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1033), Color(0xFF1E1B4B))))) {
        NightSky(Modifier.fillMaxSize())
        Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
            ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                item {
                    Text(
                        java.text.SimpleDateFormat("EEEE d 'de' MMMM", java.util.Locale("es", "MX")).format(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(e.night) ?: Date()).uppercase(),
                        color = Color(0xFFA5B4FC), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    )
                }
                item { NightSummary(e, Color(snap.tokkipColor)) }
            }
        }
    }
}

/** Tokkip resting (or with tired eyes), the hours, bed → wake, how rested, and the stages. */
@Composable
fun NightSummary(e: mx.loperstack.tokkip.wellness.NightLite, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Tokkip(
            Modifier.width(86.dp).height(70.dp), color = color,
            face = when { e.quality in 1..2 -> mx.loperstack.tokkip.character.TokkipEngine.Eye.SLEEPY; e.quality >= 4 -> mx.loperstack.tokkip.character.TokkipEngine.Eye.HAPPY; else -> null },
            mood = if (e.quality in 1..2) Mood.SLEEPY else Mood.IDLE,
        )
        Text("${e.minutes / 60} h ${"%02d".format(e.minutes % 60)}", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text("${HM.format(Date(e.bed))} → ${HM.format(Date(e.wake))}", color = Color(0xFFC7D2FE), fontSize = 13.sp)
        moodOf(e.quality)?.let { m ->
            val label = mapOf(5 to "Descansado", 4 to "Bien", 3 to "Regular", 2 to "Cansado", 1 to "Agotado")[e.quality] ?: m.label
            Text(label, color = Color(m.color), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (e.deep + e.rem > 0) {
            val total = ((e.wake - e.bed) / 60_000L).toInt().coerceAtLeast(1)
            val light = (total - e.deep - e.rem - e.awake).coerceAtLeast(0)
            val parts = listOf(e.deep to Color(0xFF6366F1), e.rem to Color(0xFF22D3EE), light to Color(0xFFA5B4FC), e.awake to Color(0xFFF472B6))
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.width(130.dp).height(8.dp).clip(CircleShape)) {
                var x = 0f
                for ((m, c) in parts) { val w = size.width * m / total; drawRect(c, Offset(x, 0f), Size(w, size.height)); x += w }
            }
            Text("Profundo ${e.deep} · REM ${e.rem} · despierto ${e.awake} min", fontSize = 10.sp, color = Color(0xFFC7D2FE), textAlign = TextAlign.Center)
        }
    }
}

/** Twinkling stars, a shooting star every 7 s, and a moon. */
@Composable
fun NightSky(modifier: Modifier) {
    val stars = remember { List(34) { floatArrayOf(kotlin.random.Random.nextFloat(), kotlin.random.Random.nextFloat() * 0.8f, 0.6f + kotlin.random.Random.nextFloat() * 1.4f, kotlin.random.Random.nextFloat() * 6.28f, 0.6f + kotlin.random.Random.nextFloat() * 1.6f) } }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val t0 = withFrameMillis { it }
        while (true) withFrameMillis { t = (it - t0) / 1000f }
    }
    val star = Color(0xFFFDF4FF)
    Canvas(modifier) {
        for (st in stars) {
            val tw = 0.5f + 0.5f * kotlin.math.sin(t * st[4] + st[3])
            drawCircle(star.copy(alpha = ((0.15f + 0.75f * tw) * (0.5f + st[2] / 4f)).coerceIn(0f, 1f)), st[2] * density * (0.8f + 0.4f * tw), Offset(st[0] * size.width, st[1] * size.height))
        }
        val k = t % 7f
        if (k < 1f) {
            val sx = size.width * (0.2f + 0.5f * k); val sy = size.height * (0.15f + 0.25f * k)
            drawLine(star.copy(alpha = 0.8f * (1 - k)), Offset(sx - 40f, sy - 20f), Offset(sx, sy), strokeWidth = 2.5f)
        }
        drawCircle(Color(0xFFFDE68A), 12.dp.toPx(), Offset(size.width * 0.72f, size.height * 0.2f))
        drawCircle(Color(0xFF0B1033), 10.dp.toPx(), Offset(size.width * 0.72f + 6.dp.toPx(), size.height * 0.2f - 4.dp.toPx()))
    }
}

// ── Pantalla ──────────────────────────────────────────────────────────────────

val DOOM_TINTS = listOf(Color(0xFF4ADE80), Color(0xFFFBBF24), Color(0xFFF97316), Color(0xFFF4505E))

fun doomEye(level: Int): mx.loperstack.tokkip.character.TokkipEngine.Eye? =
    runCatching { mx.loperstack.tokkip.character.TokkipEngine.Eye.valueOf(DOOM_LEVELS[level.coerceIn(0, 3)].eye) }.getOrNull()

/** The phone's last reading, if it's from the last hour (an old one says nothing). */
fun doomNow(snap: Snapshot): Int =
    if (System.currentTimeMillis() - snap.doomAt < 60 * 60_000L) snap.doom.coerceIn(0, 3) else 0

fun screenMins(m: Int) = if (m < 60) "$m min" else "${m / 60} h${if (m % 60 > 0) " ${m % 60}" else ""}"

/** Tokkip's face for your scrolling, the scroll against your goal, and what's on now. */
@Composable
fun ScreenReport(snap: Snapshot) {
    val level = doomNow(snap)
    val lvl = DOOM_LEVELS[level]
    val tint = DOOM_TINTS[level]
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Box(contentAlignment = Alignment.Center) {
                    val frac = if (snap.scrollGoal <= 0) 0f else (snap.scrollMin.toFloat() / snap.scrollGoal).coerceIn(0f, 1f)
                    androidx.compose.foundation.Canvas(Modifier.size(132.dp)) {
                        val sw = 10.dp.toPx()
                        val inset = sw / 2
                        val arc = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
                        drawArc(Color(0xFF2A2440), -90f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                        drawArc(tint, -90f, 360f * frac, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                    Tokkip(Modifier.width(96.dp).height(80.dp), color = Color(snap.tokkipColor), face = doomEye(level), interactive = true)
                }
            }
            item { Text("Tokkip ${lvl.name.lowercase()}", color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            item { Text(lvl.line, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp)) }
            item { Text("${screenMins(snap.scrollMin)} de scroll · meta ${screenMins(snap.scrollGoal)}", fontSize = 12.sp, color = VioletSoft) }
            item { Text("${screenMins(snap.screenMin)} de pantalla hoy", fontSize = 12.sp, color = Color(0xFFA5A9B1)) }
            if (snap.doomTop.isNotBlank()) item { Text("Lo que más: ${snap.doomTop}", fontSize = 12.sp, color = Color(0xFFA5A9B1)) }
            if (level >= 2) item {
                Chip(
                    onClick = { WatchStore.openScreen.value = "breathe" },
                    icon = { ShapedIcon(Icons.Rounded.Air, 9) },
                    label = { Text("Respira 1 minuto") },
                    colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF3B2A63)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Expressive bits ───────────────────────────────────────────────────────────

/** A chip's icon in a Material 3 Expressive shape (cookie, clover, sunny…), a different one per chip. */
@Composable
fun ShapedIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, index: Int, tint: Color = Color(0xFF14102A)) {
    val shapes = listOf(
        mx.loperstack.tokkip.character.MShapes.Cookie9, mx.loperstack.tokkip.character.MShapes.Clover4, mx.loperstack.tokkip.character.MShapes.Sunny,
        mx.loperstack.tokkip.character.MShapes.Flower, mx.loperstack.tokkip.character.MShapes.Cookie6, mx.loperstack.tokkip.character.MShapes.SoftBurst,
        mx.loperstack.tokkip.character.MShapes.Puffy, mx.loperstack.tokkip.character.MShapes.Gem, mx.loperstack.tokkip.character.MShapes.Clover8,
    )
    val fills = listOf(Color(0xFFC4B5FD), Color(0xFF86EFAC), Color(0xFFFDBA74), Color(0xFF93C5FD), Color(0xFFF9A8D4), Color(0xFFFDE68A))
    Box(
        Modifier.size(30.dp).clip(mx.loperstack.tokkip.character.PolygonShape(shapes[index % shapes.size])).background(fills[index % fills.size]),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = if (tint == Color(0xFF14102A)) tint else tint) }
}

