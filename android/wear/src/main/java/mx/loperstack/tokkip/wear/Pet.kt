// Mimos on the wrist: Tokkip fills the round screen and every gesture has its
// own feel, like on the phone —
//   tap: a poke and a hop · double tap: a flip · hold: a hug (heartbeats)
//   stroke: he purrs · fling: he bounces around the round screen like a ball
//   turn the crown: tickles · shake your wrist: dizzy
// A swipe from the left edge still goes back.

package mx.loperstack.tokkip.wear

import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.wellness.Snapshot
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

@Composable
fun WatchPet(snap: Snapshot) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val engine = remember { TokkipEngine() }
    val pos = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var said by remember { mutableStateOf("Tócalo") }
    var count by remember { mutableIntStateOf(0) }
    var physics by remember { mutableStateOf<Job?>(null) }
    var area by remember { mutableStateOf(Offset.Zero) }
    val tokkipW = with(density) { 128.dp.toPx() }
    val tokkipH = with(density) { 108.dp.toPx() }
    val color = Color(snap.tokkipColor)
    val focus = remember { FocusRequester() }

    fun pet(text: String) { said = text; count++ }

    /** A rubber ball in a round box: gravity, bounces off the rim, then back to the middle. */
    suspend fun bounce(v0: Offset) {
        val rim = ((min(area.x, area.y) - tokkipW * 0.8f) / 2f).coerceAtLeast(1f)
        val g = with(density) { 1500.dp.toPx() }
        var p = pos.value
        var v = v0
        var last = 0L
        var lastHit = 0L
        var slow = 0
        val start = System.nanoTime()
        while (true) {
            val nanos = withFrameNanos { it }
            if (last == 0L) { last = nanos; continue }
            val dt = ((nanos - last) / 1e9f).coerceAtMost(1 / 30f)
            last = nanos
            v = Offset(v.x, v.y + g * dt)
            p += v * dt
            val d = p.getDistance()
            if (d > rim) {
                val n = p / d
                val vn = v.x * n.x + v.y * n.y
                if (vn > 0) {
                    val speed = abs(vn)
                    v -= n * (vn * 1.72f) // restitution 0.72
                    v *= 0.97f
                    val t = System.currentTimeMillis()
                    if (speed > 180f && t - lastHit > 80) {
                        lastHit = t
                        engine.bump()
                        if (speed > 900f) { Haptics.thud(context); Sounds.play("pop") } else Haptics.tick(context)
                    }
                }
                p = n * rim
            }
            pos.snapTo(p)
            if (v.getDistance() < 120f && p.y > rim * 0.8f) slow++ else slow = 0
            if (slow > 12 || System.nanoTime() - start > 7_000_000_000L) break
        }
        delay(200)
        engine.cheer()
        Haptics.rise(context)
        pos.animateTo(Offset.Zero, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessLow))
    }

    // Shake your wrist: dizzy.
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val acc = sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        var lastShake = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val gForce = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                val t = System.currentTimeMillis()
                if (gForce > 22f && t - lastShake > 2500) {
                    lastShake = t
                    engine.shake()
                    Haptics.spin(context)
                    pet("¡Me mareaste!")
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (acc != null) sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(l) }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    var crown = 0f
    var lastCrownTick = 0L
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(color.copy(alpha = 0.28f), Color(0xFF0E0B1A), Color.Black)))
            .onRotaryScrollEvent { e ->
                // Turning the crown tickles him.
                crown += abs(e.verticalScrollPixels)
                engine.stroke()
                val t = System.currentTimeMillis()
                if (t - lastCrownTick > 90) { lastCrownTick = t; Haptics.purr(context, 0.4f) }
                if (crown > 900f) { crown = 0f; engine.tickle(); Haptics.giggle(context); pet("¡Jajaja!") }
                true
            }
            .focusRequester(focus).focusable()
            .pointerInput(Unit) {
                var lastTap = 0L
                awaitEachGesture {
                    area = Offset(size.width.toFloat(), size.height.toFloat())
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Leave the left edge to the system: swipe back.
                    if (down.position.x < size.width * 0.14f) return@awaitEachGesture
                    if (physics?.isActive == true) { physics?.cancel(); physics = null }
                    val downWall = System.currentTimeMillis()
                    val center = Offset(size.width / 2f, size.height / 2f) + pos.value
                    val onBody = (down.position - center).getDistance() < tokkipW * 0.5f
                    val vt = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    var last = down.position
                    var travel = 0f
                    var lastPurr = 0L
                    var lastDx = 0f
                    var flips = 0
                    var flipStart = down.uptimeMillis
                    var hug: Job? = null
                    while (true) {
                        val waiting = hug == null && travel < 10f && onBody
                        val left = 500L - (System.currentTimeMillis() - downWall)
                        val ev = if (waiting) withTimeoutOrNull(left.coerceAtLeast(1L)) { awaitPointerEvent() } else awaitPointerEvent()
                        if (ev == null) {
                            engine.hug(true)
                            said = "Aww…"
                            hug = scope.launch { while (isActive) { Haptics.heartbeat(context); delay(900) } }
                            continue
                        }
                        val c = ev.changes.firstOrNull { it.pressed } ?: break
                        vt.addPosition(c.uptimeMillis, c.position)
                        val d = c.position - last
                        last = c.position
                        travel += d.getDistance()
                        if (onBody && hug == null && travel > 10f) {
                            engine.stroke()
                            val t = c.uptimeMillis
                            if (t - lastPurr > 120) { lastPurr = t; Haptics.purr(context, min(1f, d.getDistance() / 20f + 0.3f)) }
                            if (abs(d.x) > 4f && sign(d.x) != sign(lastDx)) {
                                if (t - flipStart > 1200) { flips = 0; flipStart = t }
                                flips++
                            }
                            if (d.x != 0f) lastDx = d.x
                            if (flips >= 6) { flips = 0; engine.tickle(); Haptics.giggle(context); pet("¡Cosquillas!") }
                        }
                        c.consume()
                    }
                    val now = System.currentTimeMillis()
                    when {
                        hug != null -> { hug?.cancel(); engine.hug(false); pet("Gracias") }
                        travel < 10f && onBody -> {
                            if (now - lastTap < 320) {
                                engine.flip(); Haptics.rise(context); pet("¡Wiii!")
                            } else {
                                val wasDizzy = engine.dizzy.value
                                engine.slap()
                                if (!wasDizzy) { if (engine.dizzy.value) Haptics.spin(context) else Haptics.thud(context) }
                                pet(if (engine.dizzy.value) "¡Mareado!" else "¡Oye!")
                                if (!engine.dizzy.value) {
                                    val side = ((down.position.x - center.x) / (tokkipW * 0.5f)).coerceIn(-1f, 1f)
                                    physics = scope.launch { bounce(Offset(-side * 400f, -900f)) }
                                }
                            }
                            lastTap = now
                        }
                        travel >= 10f -> {
                            val v = vt.calculateVelocity()
                            if (hypot(v.x, v.y) > 700f) {
                                pet("¡Allá va!")
                                physics = scope.launch { bounce(Offset(v.x, v.y)) }
                            } else if (onBody) pet("Prrr…")
                        }
                    }
                    if (physics?.isActive != true && pos.value != Offset.Zero && !pos.isRunning) {
                        scope.launch { pos.animateTo(Offset.Zero, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // A slowly turning cookie behind him (Material 3 Expressive).
        val turn by androidx.compose.animation.core.rememberInfiniteTransition(label = "c").animateFloat(
            0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(24_000, easing = androidx.compose.animation.core.LinearEasing)), label = "t",
        )
        Box(
            Modifier.size(150.dp).graphicsLayer { rotationZ = turn }
                .clip(mx.loperstack.tokkip.character.PolygonShape(mx.loperstack.tokkip.character.MShapes.Cookie12)).background(color.copy(alpha = 0.16f)),
        )
        Tokkip(
            Modifier.offset { IntOffset(pos.value.x.roundToInt(), pos.value.y.roundToInt()) }.size(128.dp, 108.dp),
            color = color, engine = engine, greet = true,
        )
        Text(
            said, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp)
                .clip(RoundedCornerShape(14.dp)).background(Color(0x66221C38)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Text(
            if (count == 0) "Mimos" else "$count mimos", fontSize = 11.sp, color = Color(0xFFC4B5FD), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
    }
}
