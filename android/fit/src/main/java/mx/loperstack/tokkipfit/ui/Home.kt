// En casa: guided routines with Tokkip doing each move with you. The list of
// routines; the player (3-2-1, each move with its countdown ring, the next one
// coming, pause, skip); and the summary, where Tokkip celebrates and the
// workout is saved (here and in Health Connect).

package mx.loperstack.tokkipfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.fit.Done
import mx.loperstack.tokkip.fit.ROUTINES
import mx.loperstack.tokkip.fit.Routine
import mx.loperstack.tokkip.fit.at
import mx.loperstack.tokkip.fit.kcal
import mx.loperstack.tokkipfit.data.HealthData
import mx.loperstack.tokkipfit.data.Prefs
import kotlin.math.roundToInt

/** What a level looks like: its name and the scheme colour of its card. */
@Composable
private fun levelOf(level: Int): Triple<String, Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (level) {
        1 -> Triple("Suave", cs.secondaryContainer, cs.onSecondaryContainer)
        2 -> Triple("Medio", cs.tertiaryContainer, cs.onTertiaryContainer)
        else -> Triple("Intenso", cs.primaryContainer, cs.onPrimaryContainer)
    }
}

@Composable
fun HomeScreen(bottom: PaddingValues, onStart: (Routine) -> Unit, onEdit: (String?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val done = remember { Prefs.workouts() }
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottom.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.statusBarsPadding(),
    ) {
        item {
            Appear(0) {
                Column(Modifier.padding(top = 18.dp)) {
                    Title("En casa", size = 44.sp)
                    Text(
                        if (done.isEmpty()) "Elige una rutina y Tokkip la hace contigo." else "Llevas ${done.size} ${if (done.size == 1) "entrenamiento" else "entrenamientos"} con Tokkip.",
                        style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(ROUTINES, key = { _, r -> r.id }) { i, r ->
            Appear(1 + i) { RoutineCard(r) { onStart(r) } }
        }
        item {
            Appear(ROUTINES.size + 1) {
                Text("Tuyas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
            }
        }
        itemsIndexed(Prefs.myRoutines, key = { _, m -> m.id }) { i, m ->
            val r = m.routine()
            Appear(ROUTINES.size + 2 + i) { RoutineCard(r, onEdit = { onEdit(m.id) }) { onStart(r) } }
        }
        item {
            Appear(ROUTINES.size + 2 + Prefs.myRoutines.size) {
                CreateCard("Crear rutina", "Elige tus movimientos, el descanso y las rondas.") { onEdit(null) }
            }
        }
        item {
            Appear(ROUTINES.size + 3 + Prefs.myRoutines.size) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainerHigh).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Watch, null, tint = cs.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("También desde tu reloj: abre Tokkip Fit en él, con tu pulso en vivo.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(r: Routine, onEdit: (() -> Unit)? = null, onClick: () -> Unit) {
    val (level, bg, fg) = levelOf(r.level)
    FitCard(color = bg, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (onEdit != null) "Tuya · " else "") + level.uppercase(), style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.75f))
                    if (onEdit != null) {
                        androidx.compose.material3.IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.Edit, "Editar", tint = fg, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(r.name, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFont), color = fg)
                Spacer(Modifier.height(4.dp))
                Text(r.about, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(Icons.Rounded.Timer, "${r.minutes} min", fg)
                    Chip(Icons.Rounded.LocalFireDepartment, "~${r.kcal(r.seconds * 1000L).roundToInt()} kcal", fg)
                }
            }
            Spacer(Modifier.width(8.dp))
            // Tokkip already doing the routine's first move.
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(84.dp).clip(mx.loperstack.tokkip.character.PolygonShape(MShapes.Cookie9)).background(fg.copy(alpha = 0.1f)))
                Tokkip(Modifier.size(92.dp, 82.dp), color = Color(Prefs.color), exercise = r.steps.first().move, tempo = 0.8f)
            }
        }
    }
}

@Composable
private fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, fg: Color) {
    Row(Modifier.clip(CircleShape).background(fg.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

// ── The player ───────────────────────────────────────────────────────────────

@Composable
fun PlayerScreen(r: Routine, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val cs = MaterialTheme.colorScheme
    val fit = LocalFit.current
    var countdown by remember { mutableIntStateOf(3) }
    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var finished by remember { mutableStateOf<Done?>(null) }
    var skipBy by remember { mutableLongStateOf(0L) }

    // The screen stays on while you train.
    androidx.compose.runtime.DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { onClose() }

    // 3, 2, 1… then the clock runs (paused stops it).
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            Sounds.play("tick"); Haptics.tick(context)
            delay(1000); countdown--
        }
        Sounds.play("focus_start")
        var last = withFrameMillis { it }
        while (finished == null) {
            val now = withFrameMillis { it }
            if (!paused) elapsed += now - last
            last = now
        }
    }

    val pos = r.at(elapsed + skipBy)
    val step = r.steps[pos.index]
    // Each change of step: a sound and a buzz; the last three seconds tick.
    LaunchedEffect(pos.index) {
        if (countdown == 0 && elapsed > 0) {
            Sounds.play(if (step.rest) "pop" else "blip")
            Haptics.success(context)
        }
    }
    LaunchedEffect(pos.left, pos.index) {
        if (countdown == 0 && pos.left in 1..3 && !pos.done && !paused) Sounds.play("tick")
    }
    LaunchedEffect(pos.done) {
        if (pos.done && finished == null) {
            val d = Done(System.currentTimeMillis(), r.id, r.name, r.seconds, r.kcal((elapsed + skipBy).coerceAtMost(r.seconds * 1000L)), kind = r.kind)
            finished = d
            Prefs.addWorkout(d)
            Sounds.play("level_up"); Haptics.success(context)
            runCatching { HealthData.write(context, d) }
        }
    }

    Box(Modifier.fillMaxSize().background(cs.surface)) {
        val done = finished
        if (done != null) {
            SummaryScreen(r, done, onClose)
        } else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                FilledTonalIconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Terminar") }
                Spacer(Modifier.width(12.dp))
                Text(r.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("${pos.index + 1}/${r.steps.size}", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Segments(r, pos.index, pos.stepFraction)
            Spacer(Modifier.height(10.dp))

            // Tokkip doing the move, inside the step's countdown ring.
            val ringColor = if (step.rest) fit.minutes else fit.steps
            val sweep by animateFloatAsState(1f - pos.stepFraction, tween(250), label = "ring")
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(320.dp)) {
                    val w = 16.dp.toPx()
                    val side = size.minDimension - w
                    val tl = Offset((size.width - side) / 2, (size.height - side) / 2)
                    drawArc(fit.track, 0f, 360f, false, tl, Size(side, side), style = Stroke(w))
                    drawArc(ringColor, -90f, 360f * sweep, false, tl, Size(side, side), style = Stroke(w, cap = StrokeCap.Round))
                }
                MorphingBlob(if (step.rest) cs.secondaryContainer else cs.primaryContainer, Modifier.size(250.dp))
                Tokkip(
                    Modifier.size(220.dp, 196.dp),
                    color = Color(Prefs.color),
                    mood = if (countdown > 0) Mood.FOCUS else Mood.IDLE,
                    exercise = if (countdown > 0 || paused) null else step.move,
                    face = if (paused) TokkipEngine.Eye.DOT else null,
                    tempo = if (r.level >= 3) 1.25f else 1f,
                    interactive = true,
                )
                if (countdown > 0) {
                    AnimatedContent(countdown, transitionSpec = { (scaleIn(spring(0.5f)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "count") { n ->
                        Text("$n", style = MaterialTheme.typography.displayLarge.copy(fontFamily = DisplayFont, fontSize = 120.sp), color = cs.primary)
                    }
                }
            }

            // The move, its seconds, and what's next.
            AnimatedContent(
                pos.index,
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                label = "move",
            ) { i ->
                val s = r.steps[i]
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Title(if (countdown > 0) "¿Listo?" else s.move.label, size = 38.sp, color = if (s.rest) fit.minutes else cs.onSurface)
                    Text(s.tip, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (countdown > 0) "" else "${pos.left}",
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = DisplayFont, fontSize = 72.sp),
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
            val next = r.steps.getOrNull(pos.index + 1)
            Text(
                if (next == null) "¡La última!" else "Sigue: ${next.move.label} · ${next.seconds} s",
                style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = { paused = !paused; Sounds.play(if (paused) "close" else "pop"); Haptics.click(context) },
                    modifier = Modifier.size(84.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.primary),
                ) { Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (paused) "Seguir" else "Pausa", modifier = Modifier.size(40.dp)) }
                Spacer(Modifier.width(18.dp))
                FilledTonalIconButton(
                    onClick = {
                        // Straight to the next step.
                        val p = r.at(elapsed + skipBy)
                        skipBy += p.left * 1000L
                        Haptics.click(context)
                    },
                    modifier = Modifier.size(60.dp),
                ) { Icon(Icons.Rounded.SkipNext, "Saltar", modifier = Modifier.size(30.dp)) }
            }
        }
    }
}

/** The whole routine as segments: done, the current one filling, the rest to come. */
@Composable
private fun Segments(r: Routine, index: Int, fraction: Float) {
    val fit = LocalFit.current
    Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        r.steps.forEachIndexed { i, s ->
            val color = if (s.rest) fit.minutes else fit.steps
            Box(Modifier.weight(s.seconds.toFloat()).fillMaxSize().clip(CircleShape).background(fit.track)) {
                val f = when { i < index -> 1f; i == index -> fraction; else -> 0f }
                if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(color))
            }
        }
    }
}

// ── The summary ──────────────────────────────────────────────────────────────

@Composable
private fun SummaryScreen(r: Routine, d: Done, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fit = LocalFit.current
    val scope = rememberCoroutineScope()
    var jump by remember { mutableIntStateOf(0) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        grow.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        repeat(3) { delay(900); jump++ }
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Title("¡Lo lograste!", size = 44.sp)
        Text("${r.name}, completa", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            MorphingBlob(cs.primaryContainer, Modifier.size((260 * grow.value).dp))
            Tokkip(Modifier.size(220.dp, 196.dp), color = Color(Prefs.color), mood = Mood.HAPPY, face = TokkipEngine.Eye.STAR, dancing = true, jump = jump, interactive = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Big(Modifier.weight(1f), "${(d.seconds / 60.0).roundToInt()}", "minutos", fit.minutes)
            Big(Modifier.weight(1f), "${d.kcal.roundToInt()}", "kcal activas", fit.kcal)
            Big(Modifier.weight(1f), "${r.moves}", "ejercicios", fit.steps)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Guardado en tu historial y, si diste permiso, en Health Connect.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { scope.launch { onClose() } }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Listo", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Big(modifier: Modifier, value: String, label: String, color: Color) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(color.copy(alpha = 0.14f)).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = DisplayFont), color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
