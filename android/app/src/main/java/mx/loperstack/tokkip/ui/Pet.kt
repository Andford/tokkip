// Mimos — Tokkip as a stress-relief pet. Every gesture has its own reaction
// and its own feel in your hand:
//   tap: a poke (thud) · double tap: a jump with a flip (quick rise)
//   hold: a hug (heartbeats) · stroke: he purrs (soft ticks)
//   rub fast back and forth: he laughs (giggle) · pinch: squish or stretch him
//   fling: he flies and bounces off the walls (thud) · shake the phone: dizzy (spin)

package mx.loperstack.tokkip.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import mx.loperstack.tokkip.character.MShapes

import mx.loperstack.tokkip.character.Squircle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.character.Sounds
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

private val GESTURES = listOf(
    Icons.Rounded.TouchApp to "Toca: piquete", Icons.Rounded.AutoAwesome to "Doble toque: salto",
    Icons.Rounded.VolunteerActivism to "Mantén: abrazo", Icons.Rounded.Favorite to "Acarícialo: ronronea",
    Icons.Rounded.Swipe to "Frota rápido: cosquillas", Icons.Rounded.Pinch to "Pellizca: apachúrralo",
    Icons.Rounded.RocketLaunch to "Lánzalo: rebota como pelota", Icons.Rounded.Vibration to "Agita el teléfono: se marea",
)

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PetScreen(color: Color, actions: Actions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val engine = remember { TokkipEngine() }
    val pos = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var count by remember { mutableIntStateOf(Store.petsToday()) }
    var said by remember { mutableStateOf("Tócalo, acarícialo, abrázalo…") }
    val tokkipW = with(density) { 250.dp.toPx() }
    val tokkipH = with(density) { 210.dp.toPx() }
    val music by rememberUpdatedState(actions.music)

    fun pet(text: String) {
        said = text
        count = Store.addPet()
    }

    var physics by remember { mutableStateOf<Job?>(null) }
    var area by remember { mutableStateOf(Offset.Zero) }

    /**
     * He flies like a rubber ball: gravity, bounces off the walls and the floor
     * (squash, a thud as strong as the hit), rolls to a stop on the floor, then
     * springs back to the middle.
     */
    suspend fun bounce(v0: Offset) {
        val bx = ((area.x - tokkipW) / 2f).coerceAtLeast(1f)
        val by = ((area.y - tokkipH) / 2f).coerceAtLeast(1f)
        val g = with(density) { 2600.dp.toPx() }
        var p = pos.value
        var v = v0
        var last = 0L
        var still = 0
        var lastHit = 0L
        val start = System.nanoTime()
        fun hit(speed: Float) {
            val t = System.currentTimeMillis()
            if (t - lastHit < 70) return
            lastHit = t
            engine.bump()
            when {
                speed > 2200f -> { Haptics.thud(context); Sounds.play("pop") }
                speed > 700f -> Haptics.thud(context)
                else -> Haptics.tick(context)
            }
        }
        while (true) {
            val nanos = androidx.compose.runtime.withFrameNanos { it }
            if (last == 0L) { last = nanos; continue }
            val dt = ((nanos - last) / 1e9f).coerceAtMost(1 / 30f)
            last = nanos
            v = Offset(v.x, v.y + g * dt)
            p += v * dt
            if (p.x > bx || p.x < -bx) {
                hit(abs(v.x)); p = Offset(p.x.coerceIn(-bx, bx), p.y); v = Offset(-v.x * 0.78f, v.y)
            }
            if (p.y < -by) { hit(abs(v.y)); p = Offset(p.x, -by); v = Offset(v.x, -v.y * 0.7f) }
            if (p.y > by) {
                val impact = abs(v.y)
                p = Offset(p.x, by)
                if (impact > 260f) { hit(impact); v = Offset(v.x * 0.9f, -impact * 0.68f) }
                else { v = Offset(v.x * 0.86f, 0f); still++ }
            }
            pos.snapTo(p)
            if ((still > 8 && abs(v.x) < 30f) || System.nanoTime() - start > 9_000_000_000L) break
        }
        delay(250)
        Haptics.rise(context)
        engine.cheer()
        pos.animateTo(Offset.Zero, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessLow))
    }

    // Shake the phone: dizzy.
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val acc = sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        var lastShake = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                val t = System.currentTimeMillis()
                if (g > 17f && t - lastShake > 2500) {
                    lastShake = t
                    engine.shake()
                    Haptics.spin(context)
                    pet("¡Me mareaste!")
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (acc != null) sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(l) }
    }

    var manual by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ScreenTitle("Mimos", size = 36.sp)
                Text("$count mimos hoy", color = Ink.Dim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Pill("¿Cómo jugar?", false, Icons.Rounded.HelpOutline) { manual = true }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(bottom = 12.dp).clip(Squircle(36.dp)).background(Ink.Card)
                .pointerInput(Unit) {
                    var lastTap = 0L
                    area = Offset(size.width.toFloat(), size.height.toFloat())
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Catch him mid-air.
                        area = Offset(size.width.toFloat(), size.height.toFloat())
                        if (physics?.isActive == true) { physics?.cancel(); physics = null }
                        val startT = down.uptimeMillis
                        val downWall = System.currentTimeMillis()
                        val center = Offset(size.width / 2f, size.height / 2f) + pos.value
                        val onBody = (down.position - center).getDistance() < tokkipW * 0.42f
                        val vt = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                        var last = down.position
                        var travel = 0f
                        var lastDx = 0f
                        var flips = 0
                        var flipStart = startT
                        var lastPurr = 0L
                        var pinchStart = 0f
                        var pinching = false
                        var lastStep = 0
                        var hug: Job? = null

                        while (true) {
                            val waiting = hug == null && !pinching && travel < 14f && onBody
                            val left = 450L - (System.currentTimeMillis() - downWall)
                            val ev = if (waiting) withTimeoutOrNull(left.coerceAtLeast(1L)) { awaitPointerEvent() } else awaitPointerEvent()
                            if (ev == null) {
                                // Held still on him: a hug, with heartbeats while you hold.
                                engine.hug(true)
                                said = "Aww…"
                                hug = scope.launch { while (isActive) { Haptics.heartbeat(context); delay(900) } }
                                continue
                            }
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                val d = (pressed[0].position - pressed[1].position).getDistance()
                                if (!pinching) { pinching = true; pinchStart = d.coerceAtLeast(1f); hug?.cancel(); hug = null }
                                val amount = (d / pinchStart).let { 1f + (it - 1f) * 0.8f }
                                engine.squeeze(amount)
                                val step = ((amount - 1f) * 10).roundToInt()
                                if (step != lastStep) { lastStep = step; Haptics.tick(context) }
                                ev.changes.forEach { it.consume() }
                                continue
                            }
                            val c = pressed.first()
                            vt.addPosition(c.uptimeMillis, c.position)
                            val d = c.position - last
                            last = c.position
                            travel += d.getDistance()
                            if (onBody && hug == null && travel > 14f) {
                                engine.stroke()
                                val t = c.uptimeMillis
                                if (t - lastPurr > 110) {
                                    lastPurr = t
                                    Haptics.purr(context, min(1f, d.getDistance() / 25f + 0.3f))
                                }
                                // Rubbing back and forth fast: a tickle.
                                if (abs(d.x) > 5f && sign(d.x) != sign(lastDx)) {
                                    if (t - flipStart > 1200) { flips = 0; flipStart = t }
                                    flips++
                                }
                                if (d.x != 0f) lastDx = d.x
                                if (flips >= 6) {
                                    flips = 0
                                    engine.tickle()
                                    Haptics.giggle(context)
                                    pet("¡Jajaja, cosquillas!")
                                }
                            }
                            c.consume()
                        }

                        val now = System.currentTimeMillis()
                        when {
                            hug != null -> {
                                hug?.cancel()
                                engine.hug(false)
                                pet("Gracias por el abrazo")
                            }
                            pinching -> {
                                engine.unsqueeze()
                                Haptics.click(context)
                                pet("¡Boing!")
                            }
                            travel < 14f && onBody -> {
                                if (now - lastTap < 320) {
                                    engine.flip()
                                    Haptics.rise(context)
                                    pet("¡Wiii!")
                                } else {
                                    val wasDizzy = engine.dizzy.value
                                    engine.slap()
                                    if (!wasDizzy) { if (engine.dizzy.value) Haptics.spin(context) else Haptics.thud(context) }
                                    pet(if (engine.dizzy.value) "Demasiados piquetes" else "¡Oye!")
                                    // …and he hops off the poke, bouncing on the floor.
                                    if (!engine.dizzy.value && physics?.isActive != true) {
                                        val side = ((down.position.x - center.x) / (tokkipW * 0.42f)).coerceIn(-1f, 1f)
                                        physics = scope.launch { bounce(Offset(-side * 700f, -1500f)) }
                                    }
                                }
                                lastTap = now
                            }
                            travel >= 14f -> {
                                val v = vt.calculateVelocity()
                                if (hypot(v.x, v.y) > 1100f) {
                                    pet("¡Allá va!")
                                    Sounds.play("open")
                                    physics?.cancel()
                                    physics = scope.launch { bounce(Offset(v.x, v.y)) }
                                } else if (onBody) {
                                    pet("Prrr…")
                                }
                            }
                        }
                        // Caught mid-air and let go: back to the middle, wobbling.
                        if (physics?.isActive != true && pos.value != Offset.Zero && !pos.isRunning) {
                            scope.launch { pos.animateTo(Offset.Zero, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow)) }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Aurora(color, Modifier.fillMaxSize())
            Tokkip(
                Modifier.offset { IntOffset(pos.value.x.roundToInt(), pos.value.y.roundToInt()) }.size(250.dp, 210.dp),
                color = color, engine = engine, dancing = music, greet = true,
            )
            Text(
                said, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink.Text,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
                    .clip(Squircle(20.dp)).background(Ink.CardHigh).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
    if (manual) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { manual = false }, containerColor = Ink.CardHigh) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                ScreenTitle("Cómo jugar", size = 30.sp)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((icon, g) in GESTURES) {
                        IconLabel(
                            icon, g, Ink.Text,
                            Modifier.clip(CircleShape).background(Ink.VioletDeep).padding(horizontal = 14.dp, vertical = 10.dp), 14.sp,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Cada gesto vibra distinto. Puedes apagar la vibración en Ajustes.", color = Ink.Dim, fontSize = 13.sp)
            }
        }
    }
}
