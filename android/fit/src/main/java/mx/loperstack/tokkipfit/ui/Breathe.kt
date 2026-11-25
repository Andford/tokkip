// Respira: breathing with Tokkip. Pick a rhythm (the same ones as the Tokkip
// app: calma 4·6, 4·7·8, caja, coherencia) and how long, and Tokkip fills up
// and empties with you, a soft note on each breath. Saved as guided breathing.

package mx.loperstack.tokkipfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.fit.Done
import mx.loperstack.tokkip.wellness.BREATH_PATTERNS
import mx.loperstack.tokkip.wellness.BreathPattern
import mx.loperstack.tokkip.wellness.Breathing
import mx.loperstack.tokkip.wellness.breathFrame
import mx.loperstack.tokkipfit.data.HealthData
import mx.loperstack.tokkipfit.data.Prefs

@Composable
fun BreatheScreen(bottom: PaddingValues, onStart: (BreathPattern, Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var minutes by remember { mutableIntStateOf(3) }
    val sessions = remember { Prefs.workouts().count { it.kind == "breathe" } }
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottom.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.statusBarsPadding(),
    ) {
        item {
            Appear(0) {
                Column(Modifier.padding(top = 18.dp)) {
                    Title("Respira", size = 44.sp)
                    Text(
                        if (sessions == 0) "Tokkip respira contigo. Elige un ritmo." else "Llevas $sessions ${if (sessions == 1) "sesión" else "sesiones"} respirando con Tokkip.",
                        style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Appear(1) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    MorphingBlob(cs.tertiaryContainer, Modifier.size(190.dp), listOf(MShapes.Cookie9, MShapes.Flower, MShapes.Clover4))
                    Tokkip(Modifier.size(160.dp, 140.dp), color = Color(Prefs.color), mood = Mood.BREATHING)
                }
            }
        }
        item {
            Appear(2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in listOf(1, 3, 5, 10)) {
                        val on = m == minutes
                        Text(
                            "$m min", style = MaterialTheme.typography.labelLarge, color = if (on) cs.onPrimary else cs.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).then(if (on) Modifier.clip(CircleShape).background(Brush.linearGradient(listOf(LocalFit.current.steps, LocalFit.current.stepsTo))) else Modifier.glass(CircleShape))
                                .clickable { minutes = m; Sounds.play("tick") }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }
        itemsIndexed(BREATH_PATTERNS) { i, p ->
            Appear(3 + i) {
                FitCard(onClick = { onStart(p, minutes) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShapeIcon(Icons.Rounded.Air, cs.tertiary, cs.onTertiary, 48.dp, listOf(MShapes.Cookie9, MShapes.Flower, MShapes.Clover4, MShapes.Sunny)[i % 4])
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(p.name, style = MaterialTheme.typography.titleLarge)
                            Text(p.note, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BreatheSessionScreen(p: BreathPattern, minutes: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val cs = MaterialTheme.colorScheme
    val fit = LocalFit.current
    val session = remember { Breathing(startedAt = System.currentTimeMillis(), minutes = minutes).with(p) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var saved by remember { mutableStateOf<Done?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    BackHandler { onClose() }
    LaunchedEffect(Unit) { while (saved == null) { withFrameMillis { }; now = System.currentTimeMillis() } }
    val f = breathFrame(session, now)
    LaunchedEffect(f.inhaling) {
        if (!f.done) { Sounds.play(if (f.inhaling) "breath_in" else "breath_out"); Haptics.breath(context, f.inhaling, if (f.inhaling) p.inhale else p.exhale, guide = true) }
    }
    LaunchedEffect(f.done) {
        if (f.done && saved == null) {
            val d = Done(System.currentTimeMillis(), "breathe_${p.id}", p.name, minutes * 60, 0.0, kind = "breathe")
            Prefs.addWorkout(d)
            saved = d
            Sounds.play("finish")
            runCatching { HealthData.write(context, d) }
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Salir") }
            Spacer(Modifier.width(12.dp))
            Text(p.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(300.dp)) {
                val w = 10.dp.toPx(); val side = size.minDimension - w
                val tl = Offset((size.width - side) / 2, (size.height - side) / 2)
                drawArc(fit.track, 0f, 360f, false, tl, Size(side, side), style = Stroke(w))
                drawArc(fit.sleep, -90f, 360f * f.progressOrDone(), false, tl, Size(side, side), style = Stroke(w, cap = StrokeCap.Round))
            }
            val grow = 0.8f + 0.25f * f.fill
            MorphingBlob(cs.tertiaryContainer, Modifier.size((230 * grow).dp), listOf(MShapes.Cookie9, MShapes.Flower, MShapes.Clover4))
            Tokkip(
                Modifier.size(200.dp, 176.dp), color = Color(Prefs.color),
                mood = if (saved != null) Mood.HAPPY else Mood.IDLE,
                breathing = if (saved == null) session else null,
                face = if (saved != null) TokkipEngine.Eye.HAPPY else null,
            )
        }
        val word = when {
            saved != null -> "Listo"
            f.phase == mx.loperstack.tokkip.wellness.BreathPhase.IN -> "Inhala"
            f.phase == mx.loperstack.tokkip.wellness.BreathPhase.OUT -> "Exhala"
            else -> "Sostén"
        }
        AnimatedContent(word, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "phase") { t ->
            Title(t, size = 44.sp, color = if (t == "Inhala") fit.sleep else cs.onSurface)
        }
        Text(
            if (saved != null) "Respiraste $minutes ${if (minutes == 1) "minuto" else "minutos"} con Tokkip." else "${f.breaths} respiraciones · quedan ${f.left / 60}:${(f.left % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        if (saved != null) Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Listo", style = MaterialTheme.typography.titleMedium) }
        else Spacer(Modifier.height(56.dp))
    }
}

/** How far into the session (the frame's own, or full once done). */
private fun mx.loperstack.tokkip.wellness.BreathFrame.progressOrDone(): Float = if (done) 1f else progress
