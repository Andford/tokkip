// Tokkip Fit on the Pixel Watch: pick a routine, and Tokkip does each move with
// you while the ring round the screen counts it down and your heart beats on
// it. When you finish, the workout goes to the phone (it writes it to Health
// Connect). Tap to pause; the button at the bottom ends it.

package mx.loperstack.tokkipfit.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import mx.loperstack.tokkip.character.Look
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.fit.Done
import mx.loperstack.tokkip.fit.FIT_DONE_PATH
import mx.loperstack.tokkip.fit.ROUTINES
import mx.loperstack.tokkip.fit.Sport
import mx.loperstack.tokkip.fit.Routine
import mx.loperstack.tokkip.fit.at
import mx.loperstack.tokkip.fit.kcal
import kotlin.math.roundToInt

private val Lime = Color(0xFFB9F35A)
private val Teal = Color(0xFF5EEAD4)
private val Coral = Color(0xFFFF7A6B)

private val FitColors = Colors(
    primary = Teal, primaryVariant = Color(0xFF2DD4BF), secondary = Lime, secondaryVariant = Lime,
    background = Color.Black, surface = Color(0xFF1C1E22), error = Coral,
    onPrimary = Color(0xFF00201C), onSecondary = Color(0xFF122000), onBackground = Color.White, onSurface = Color.White, onError = Color.Black,
)

/** Tokkip's look on the watch: the last one the phone sent. */
object WatchLook {
    var look by mutableStateOf(Look())

    fun load(context: Context) {
        Look.parse(context.getSharedPreferences("fitwear", Context.MODE_PRIVATE).getString("look", null))?.let { look = it }
        look.apply()
    }

    fun save(context: Context, l: Look) {
        look = l
        l.apply()
        context.getSharedPreferences("fitwear", Context.MODE_PRIVATE).edit().putString("look", l.json()).apply()
    }

    /** Whatever the phone has put there since (when the watch was away). */
    suspend fun fetch(context: Context) {
        runCatching {
            val items = Wearable.getDataClient(context).getDataItems(Uri.parse("wear://*${Look.FIT_LOOK_PATH}")).await()
            val raw = items.firstOrNull()?.let { DataMapItem.fromDataItem(it).dataMap.getString("json") }
            items.release()
            Look.parse(raw)?.let { save(context, it) }
        }
    }
}

/** Your own routines, made on the phone (shared fit/Custom.kt). */
object WatchMine {
    var routines by mutableStateOf<List<mx.loperstack.tokkip.fit.MyRoutine>>(emptyList())
    var plans by mutableStateOf<List<mx.loperstack.tokkip.fit.GymPlan>>(emptyList())
    /** Your last set of each lift, from the phone: where the watch's weights start. */
    var last by mutableStateOf<List<mx.loperstack.tokkip.fit.GymSet>>(emptyList())

    private fun prefs(context: Context) = context.getSharedPreferences("fitwear", Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = prefs(context)
        routines = mx.loperstack.tokkip.fit.MyRoutine.list(p.getString("mine", null))
        plans = mx.loperstack.tokkip.fit.MyPlans.list(p.getString("mine_plans", null))
        last = mx.loperstack.tokkip.fit.GymSet.list(p.getString("mine_last", null))
    }

    fun save(context: Context, map: com.google.android.gms.wearable.DataMap) {
        prefs(context).edit()
            .putString("mine", map.getString("json") ?: "[]")
            .putString("mine_plans", map.getString("plans") ?: "[]")
            .putString("mine_last", map.getString("last") ?: "[]")
            .apply()
        load(context)
    }

    /** The sets done on the watch count as your last ones until the phone answers. */
    fun remember(context: Context, sets: List<mx.loperstack.tokkip.fit.GymSet>) {
        last = mx.loperstack.tokkip.fit.lastSets(last + sets)
        prefs(context).edit().putString("mine_last", mx.loperstack.tokkip.fit.GymSet.json(last)).apply()
    }

    suspend fun fetch(context: Context) {
        runCatching {
            val items = Wearable.getDataClient(context).getDataItems(Uri.parse("wear://*${mx.loperstack.tokkip.fit.FIT_MINE_PATH}")).await()
            val map = items.firstOrNull()?.let { DataMapItem.fromDataItem(it).dataMap }
            items.release()
            map?.let { save(context, it) }
        }
    }
}

class LookListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (e in events) {
            if (e.type != DataEvent.TYPE_CHANGED) continue
            val map = DataMapItem.fromDataItem(e.dataItem).dataMap
            val json = map.getString("json") ?: continue
            when (e.dataItem.uri.path) {
                Look.FIT_LOOK_PATH -> Look.parse(json)?.let { WatchLook.save(this, it) }
                "/tokkipfit/today" -> Rings.save(this, json)
                mx.loperstack.tokkip.fit.FIT_MINE_PATH -> WatchMine.save(this, map)
            }
        }
    }
}

class FitWearActivity : ComponentActivity() {
    private val ask = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchLook.load(this)
        WatchMine.load(this)
        Sounds.init(this)
        setContent {
            LaunchedEffect(Unit) { WatchLook.fetch(this@FitWearActivity); WatchMine.fetch(this@FitWearActivity) }
            MaterialTheme(colors = FitColors) { FitWatch(onTrain = { on -> keepOn(on); if (on) askHeart() }, askGps = { askGps() }) }
        }
    }

    private fun keepOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun askGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun askHeart() {
        val want = listOf(Manifest.permission.BODY_SENSORS, "android.permission.health.READ_HEART_RATE")
        if (want.none { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) ask.launch(want.toTypedArray())
    }
}

/** What the watch is doing: choosing, a routine, cardio, breathing or a rest timer. */
private sealed interface Mode {
    data object Menu : Mode
    data class Play(val r: Routine) : Mode
    data class Run(val sport: Sport) : Mode
    data object Breathe : Mode
    data object Rest : Mode
    data class Gym(val plan: mx.loperstack.tokkip.fit.GymPlan) : Mode
}

@Composable
private fun FitWatch(onTrain: (Boolean) -> Unit, askGps: () -> Unit) {
    var mode by remember { mutableStateOf<Mode>(Mode.Menu) }
    fun go(m: Mode) { mode = m; onTrain(m != Mode.Menu); if (m is Mode.Run) askGps() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val m = mode) {
            Mode.Menu -> Routines(onPick = { go(Mode.Play(it)) }, onCardio = { go(Mode.Run(it)) }, onBreathe = { go(Mode.Breathe) }, onRest = { go(Mode.Rest) }, onGym = { go(Mode.Gym(it)) })
            is Mode.Gym -> GymWatch(m.plan) { go(Mode.Menu) }
            is Mode.Play -> Player(m.r) { go(Mode.Menu) }
            is Mode.Run -> CardioWatch(m.sport) { go(Mode.Menu) }
            Mode.Breathe -> BreatheWatch { go(Mode.Menu) }
            Mode.Rest -> RestWatch { go(Mode.Menu) }
        }
    }
}

@Composable
private fun Routines(onPick: (Routine) -> Unit, onCardio: (Sport) -> Unit, onBreathe: () -> Unit, onRest: () -> Unit, onGym: (mx.loperstack.tokkip.fit.GymPlan) -> Unit) {
    val state = rememberScalingLazyListState()
    ScalingLazyColumn(Modifier.fillMaxSize(), state = state, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Tokkip(Modifier.size(78.dp, 66.dp), color = Color(WatchLook.look.color), mood = Mood.HAPPY, greet = true, interactive = true)
                Text("Tokkip Fit", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text("¿Qué hacemos hoy?", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }
        }
        item { Text("Cardio con GPS", fontSize = 12.sp, color = Teal, fontWeight = FontWeight.Bold) }
        items(Sport.entries) { sp ->
            Chip(
                onClick = { onCardio(sp) },
                label = { Text(sp.label, fontWeight = FontWeight.Bold) },
                secondaryLabel = { Text("Ruta, ritmo y pulso") },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF15302C)),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(onClick = onBreathe, label = { Text("Respira", fontWeight = FontWeight.Bold) }, secondaryLabel = { Text("Calma 4·6, 3 min") },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF242A44)), modifier = Modifier.fillMaxWidth())
        }
        item { Text("Gym", fontSize = 12.sp, color = Coral, fontWeight = FontWeight.Bold) }
        items(WatchMine.plans + mx.loperstack.tokkip.fit.GYM_PLANS) { p ->
            Chip(
                onClick = { onGym(p) },
                label = { Text(p.name, fontWeight = FontWeight.Bold) },
                secondaryLabel = { Text("${p.lifts.size} ejercicios · ${p.sets}×${p.reps}") },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF33231F)),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(onClick = onRest, label = { Text("Solo descansos", fontWeight = FontWeight.Bold) }, secondaryLabel = { Text("Cuenta tus series y descansos") },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF2A2D33)), modifier = Modifier.fillMaxWidth())
        }
        if (WatchMine.routines.isNotEmpty()) {
            item { Text("Tuyas", fontSize = 12.sp, color = Lime, fontWeight = FontWeight.Bold) }
            items(WatchMine.routines.map { it.routine() }) { r ->
                Chip(
                    onClick = { onPick(r) },
                    label = { Text(r.name, fontWeight = FontWeight.Bold) },
                    secondaryLabel = { Text("${r.minutes} min · ${r.moves} movimientos") },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1E2A1C)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { Text("En casa", fontSize = 12.sp, color = Lime, fontWeight = FontWeight.Bold) }
        items(ROUTINES) { r ->
            Chip(
                onClick = { onPick(r) },
                label = { Text(r.name, fontWeight = FontWeight.Bold) },
                secondaryLabel = { Text("${r.minutes} min · ${when (r.level) { 1 -> "suave"; 2 -> "medio"; else -> "intenso" }}") },
                colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF1C1E22)),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Your heart rate from the watch's sensor while `on`; 0 until it reads one. */
@Composable
private fun heartRate(on: Boolean): Int {
    val context = LocalContext.current
    var bpm by remember { mutableIntStateOf(0) }
    DisposableEffect(on) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val v = e.values.firstOrNull()?.toInt() ?: 0
                if (v in 30..230) bpm = v
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (on && sensor != null) runCatching { sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL) }
        onDispose { sm?.unregisterListener(listener) }
    }
    return bpm
}

private fun buzz(context: Context, strong: Boolean) {
    val vm = context.getSystemService(VibratorManager::class.java) ?: return
    val effect = if (strong) VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), intArrayOf(0, 200, 0, 200), -1) else VibrationEffect.createOneShot(40, 120)
    runCatching { vm.defaultVibrator.vibrate(effect) }
}

@Composable
private fun Player(r: Routine, onEnd: () -> Unit) {
    val context = LocalContext.current
    var elapsed by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<Done?>(null) }
    var countdown by remember { mutableIntStateOf(3) }
    val bpm = heartRate(done == null)
    val beats = remember { ArrayList<Int>() }
    BackHandler { onEnd() }

    LaunchedEffect(Unit) {
        while (countdown > 0) { buzz(context, false); delay(1000); countdown-- }
        buzz(context, true)
        var last = withFrameMillis { it }
        while (done == null) {
            val now = withFrameMillis { it }
            if (!paused) elapsed += now - last
            last = now
        }
    }
    LaunchedEffect(bpm) { if (bpm > 0 && !paused && countdown == 0) beats += bpm }

    val pos = r.at(elapsed)
    val step = r.steps[pos.index]
    LaunchedEffect(pos.index) { if (elapsed > 0) buzz(context, true) }
    LaunchedEffect(pos.left) { if (countdown == 0 && pos.left in 1..3 && !paused) buzz(context, false) }
    LaunchedEffect(pos.done) {
        if (pos.done && done == null) {
            val avg = if (beats.isEmpty()) 0 else beats.average().roundToInt()
            val d = Done(System.currentTimeMillis(), r.id, r.name, r.seconds, r.kcal(elapsed), avg, r.kind, "watch")
            done = d
            Sounds.play("level_up")
            sendToPhone(context, d)
        }
    }

    val finished = done
    if (finished != null) {
        Summary(finished, onEnd)
        return
    }
    Box(Modifier.fillMaxSize().clickable { if (countdown == 0) { paused = !paused; buzz(context, false) } }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = 1f - pos.stepFraction,
            modifier = Modifier.fillMaxSize().padding(3.dp),
            startAngle = 290f, endAngle = 250f,
            indicatorColor = if (step.rest) Lime else Teal,
            trackColor = Color.White.copy(alpha = 0.12f),
            strokeWidth = 7.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (countdown > 0) "¿Listo?" else if (paused) "En pausa" else step.move.label,
                fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = if (step.rest) Lime else Color.White, textAlign = TextAlign.Center,
            )
            Tokkip(
                Modifier.size(96.dp, 84.dp), color = Color(WatchLook.look.color),
                mood = if (countdown > 0) Mood.FOCUS else Mood.IDLE,
                exercise = if (countdown > 0 || paused) null else step.move,
                face = if (paused) TokkipEngine.Eye.DOT else null,
                tempo = if (r.level >= 3) 1.25f else 1f,
            )
            Text(if (countdown > 0) "$countdown" else "${pos.left}", fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, null, tint = Coral, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text(if (bpm > 0) "$bpm" else "--", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Ends it early: what was done still counts.
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(30.dp).clip(CircleShape).background(Color(0xFF2A2D33))
                .clickable {
                    val avg = if (beats.isEmpty()) 0 else beats.average().roundToInt()
                    val secs = (elapsed / 1000).toInt()
                    if (secs < 30) onEnd() else {
                        val d = Done(System.currentTimeMillis(), r.id, r.name, secs, r.kcal(elapsed), avg, r.kind, "watch")
                        done = d
                        sendToPhone(context, d)
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Stop, "Terminar", modifier = Modifier.size(16.dp)) }
    }
}

@Composable
internal fun Summary(d: Done, onEnd: () -> Unit) {
    var jump by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { repeat(3) { delay(900); jump++ } }
    Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("¡Lo lograste!", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Tokkip(Modifier.size(90.dp, 78.dp), color = Color(WatchLook.look.color), mood = Mood.HAPPY, face = TokkipEngine.Eye.STAR, dancing = true, jump = jump)
        Text("${(d.seconds / 60.0).roundToInt()} min · ${d.kcal.roundToInt()} kcal", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (d.bpm > 0) Text("Pulso medio ${d.bpm}", fontSize = 12.sp, color = Coral)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Teal).clickable { onEnd() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, "Listo", tint = Color(0xFF00201C)) }
    }
}

/** Sends the workout to every phone around (the phone keeps it and writes it to Health Connect). */
private fun sendToPhone(context: Context, d: Done) {
    val app = context.applicationContext
    Wearable.getNodeClient(app).connectedNodes.addOnSuccessListener { nodes ->
        val bytes = d.toJson().toString().toByteArray()
        for (n in nodes) Wearable.getMessageClient(app).sendMessage(n.id, FIT_DONE_PATH, bytes)
    }
}
