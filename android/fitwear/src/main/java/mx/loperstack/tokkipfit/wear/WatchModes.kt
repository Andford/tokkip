// The watch's other workouts: cardio with its own GPS and your pulse (the
// route and the distance go to the phone, which writes them to Health
// Connect), breathing with Tokkip and the vibration, and a rest timer for the
// gym that counts your sets.

package mx.loperstack.tokkipfit.wear

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import mx.loperstack.tokkip.character.Exercise
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.fit.Done
import mx.loperstack.tokkip.fit.FIT_DONE_PATH
import mx.loperstack.tokkip.fit.Point
import mx.loperstack.tokkip.fit.Sport
import mx.loperstack.tokkip.fit.Track
import mx.loperstack.tokkip.fit.cardioKcal
import mx.loperstack.tokkip.fit.clock
import mx.loperstack.tokkip.fit.pace
import mx.loperstack.tokkip.wellness.BREATH_PATTERNS
import mx.loperstack.tokkip.wellness.Breathing
import mx.loperstack.tokkip.wellness.breathFrame
import kotlin.math.roundToInt

private val Teal = Color(0xFF5EEAD4)
private val Lime = Color(0xFFB9F35A)
private val Coral = Color(0xFFFF7A6B)

private fun buzzOnce(context: Context, ms: Long = 60, amp: Int = 160) {
    val vm = context.getSystemService(VibratorManager::class.java) ?: return
    runCatching { vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(ms, amp)) }
}

/** Sends a finished workout (and its route, as "route": [[lat, lon, t]…]) to the phone. */
fun sendDone(context: Context, d: Done, route: List<Point> = emptyList()) {
    val app = context.applicationContext
    val json = d.toJson()
    if (route.isNotEmpty()) json.put("route", org.json.JSONArray().apply { route.forEach { put(org.json.JSONArray().put(it.lat).put(it.lon).put(it.at)) } })
    Wearable.getNodeClient(app).connectedNodes.addOnSuccessListener { nodes ->
        for (n in nodes) Wearable.getMessageClient(app).sendMessage(n.id, FIT_DONE_PATH, json.toString().toByteArray())
    }
}

@SuppressLint("MissingPermission")
@Composable
fun CardioWatch(sport: Sport, onEnd: () -> Unit) {
    val context = LocalContext.current
    val track = remember { Track(sport) }
    val started = remember { System.currentTimeMillis() }
    var moving by remember { mutableLongStateOf(0L) }
    var meters by remember { mutableDoubleStateOf(0.0) }
    var paused by remember { mutableStateOf(false) }
    var fix by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<Done?>(null) }
    var km by remember { mutableIntStateOf(0) }
    val beats = remember { ArrayList<Int>() }
    val bpm = watchHeartRate(done == null)
    BackHandler { }
    DisposableEffect(Unit) {
        val lm = context.getSystemService(LocationManager::class.java)
        val l = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                fix = true
                if (paused || done != null) return
                if (track.add(Point(loc.latitude, loc.longitude, loc.time, if (loc.hasAccuracy()) loc.accuracy else 10f), started)) meters = track.distance
            }
        }
        runCatching { lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, l, Looper.getMainLooper()) }
        onDispose { runCatching { lm?.removeUpdates(l) } }
    }
    LaunchedEffect(Unit) {
        var last = System.currentTimeMillis()
        while (done == null) { delay(500); val now = System.currentTimeMillis(); if (!paused) moving += now - last; last = now }
    }
    LaunchedEffect(bpm) { if (bpm > 0 && !paused) beats += bpm }
    LaunchedEffect(meters) { val k = (meters / 1000).toInt(); if (k > km) { km = k; buzzOnce(context, 300, 255) } }

    val d = done
    if (d != null) {
        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("¡${sport.verb}!", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Tokkip(Modifier.size(86.dp, 76.dp), color = Color(WatchLook.look.color), mood = Mood.HAPPY, face = TokkipEngine.Eye.STAR, dancing = true)
            Text(String.format(java.util.Locale.US, "%.2f km · %s", d.meters / 1000, clock(d.seconds.toLong())), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (d.bpm > 0) Text("Pulso medio ${d.bpm}", fontSize = 12.sp, color = Coral)
            Spacer(Modifier.size(6.dp))
            Box(Modifier.size(40.dp).clip(CircleShape).background(Teal).clickable { onEnd() }, contentAlignment = Alignment.Center) { Text("OK", color = Color.Black, fontWeight = FontWeight.Bold) }
        }
        return
    }
    val secs = moving / 1000.0
    Box(Modifier.fillMaxSize().clickable { paused = !paused; buzzOnce(context) }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = ((meters % 1000) / 1000).toFloat(), modifier = Modifier.fillMaxSize().padding(3.dp),
            startAngle = 290f, endAngle = 250f, indicatorColor = Teal, trackColor = Color.White.copy(alpha = 0.12f), strokeWidth = 7.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (!fix) "Buscando GPS…" else if (paused) "En pausa" else sport.label, fontSize = 12.sp, color = if (fix) Teal else Color.White.copy(alpha = 0.6f))
            Text(clock(moving / 1000), fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text(String.format(java.util.Locale.US, "%.2f km", meters / 1000), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (sport == Sport.BIKE) mx.loperstack.tokkip.fit.speed(meters, secs) else "${pace(if (meters > 20) secs / (meters / 1000) else 0.0)}/km", fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Favorite, null, tint = Coral, modifier = Modifier.size(12.dp))
                Text(if (bpm > 0) " $bpm" else " --", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Tokkip(Modifier.size(52.dp, 46.dp), color = Color(WatchLook.look.color), exercise = if (paused) null else Exercise.RUN, tempo = if (sport == Sport.WALK) 0.55f else 1f)
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(30.dp).clip(CircleShape).background(Color(0xFF3A2020))
                .clickable {
                    val avg = if (beats.isEmpty()) 0 else beats.average().roundToInt()
                    val s = (moving / 1000).toInt()
                    if (s < 20 && meters < 50) onEnd() else {
                        val x = Done(System.currentTimeMillis(), sport.id, sport.label, s, cardioKcal(sport, meters, s.toDouble()), avg, sport.id, "watch", meters)
                        done = x
                        Sounds.play("level_up")
                        sendDone(context, x, track.points)
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Stop, "Terminar", modifier = Modifier.size(16.dp)) }
    }
}

@Composable
fun BreatheWatch(onEnd: () -> Unit) {
    val context = LocalContext.current
    val p = BREATH_PATTERNS.first()
    val session = remember { Breathing(startedAt = System.currentTimeMillis(), minutes = 3).with(p) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { withFrameMillis { }; now = System.currentTimeMillis() } }
    val f = breathFrame(session, now)
    LaunchedEffect(f.inhaling) { if (!f.done) buzzOnce(context, if (f.inhaling) 90 else 40, if (f.inhaling) 120 else 70) }
    LaunchedEffect(f.done) {
        if (f.done && !sent) { sent = true; sendDone(context, Done(System.currentTimeMillis(), "breathe_${p.id}", p.name, 180, 0.0, kind = "breathe", from = "watch")) }
    }
    Box(Modifier.fillMaxSize().clickable { if (f.done) onEnd() }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = if (f.done) 1f else f.progress, modifier = Modifier.fillMaxSize().padding(3.dp),
            startAngle = 290f, endAngle = 250f, indicatorColor = Color(0xFFA7B4FF), trackColor = Color.White.copy(alpha = 0.12f), strokeWidth = 6.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Tokkip(Modifier.size(110.dp, 96.dp), color = Color(WatchLook.look.color), breathing = if (f.done) null else session, mood = if (f.done) Mood.HAPPY else Mood.IDLE)
            Text(if (f.done) "Listo · toca para salir" else if (f.inhaling) "Inhala" else "Exhala", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
    }
    BackHandler { onEnd() }
}

@Composable
fun RestWatch(onEnd: () -> Unit) {
    val context = LocalContext.current
    var total by remember { mutableIntStateOf(90) }
    var left by remember { mutableIntStateOf(0) }
    var sets by remember { mutableIntStateOf(0) }
    LaunchedEffect(left > 0) {
        while (left > 0) { delay(1000); left--; if (left in 1..3) buzzOnce(context, 40); if (left == 0) buzzOnce(context, 400, 255) }
    }
    BackHandler { onEnd() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = if (left > 0) left / total.toFloat() else 0f, modifier = Modifier.fillMaxSize().padding(3.dp),
            startAngle = 290f, endAngle = 250f, indicatorColor = Lime, trackColor = Color.White.copy(alpha = 0.12f), strokeWidth = 7.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Serie ${sets + 1}", fontSize = 12.sp, color = Lime, fontWeight = FontWeight.Bold)
            Tokkip(Modifier.size(70.dp, 62.dp), color = Color(WatchLook.look.color), exercise = if (left > 0) Exercise.REST else Exercise.LIFT)
            Text(if (left > 0) "$left s" else "¡A darle!", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (s in listOf(60, 90, 120)) {
                    Box(
                        Modifier.clip(CircleShape).background(if (s == total) Lime else Color(0xFF2A2D33)).clickable { total = s; sets++; left = s; buzzOnce(context) }.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("${s}s", fontSize = 11.sp, color = if (s == total) Color.Black else Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            Text("Toca un tiempo al terminar la serie", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        }
    }
}

/** Your heart rate from the watch's sensor while `on`; 0 until it reads one. */
@Composable
fun watchHeartRate(on: Boolean): Int {
    val context = LocalContext.current
    var bpm by remember { mutableIntStateOf(0) }
    DisposableEffect(on) {
        val sm = context.getSystemService(android.hardware.SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_HEART_RATE)
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val v = e.values.firstOrNull()?.toInt() ?: 0
                if (v in 30..230) bpm = v
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, accuracy: Int) {}
        }
        if (on && sensor != null) runCatching { sm.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL) }
        onDispose { sm?.unregisterListener(listener) }
    }
    return bpm
}
