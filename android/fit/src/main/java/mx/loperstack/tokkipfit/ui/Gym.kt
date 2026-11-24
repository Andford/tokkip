// Gym: plans to start from (or a free session), your records, and the session
// itself: each exercise with Tokkip lifting, its sets (weight × reps, the last
// weight you used already filled in), the rest between sets with its ring, and
// a party when a set beats your record. The session is saved with its sets and
// written to Health Connect as strength training.

package mx.loperstack.tokkipfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import mx.loperstack.tokkip.character.Exercise
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.PolygonShape
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.fit.Done
import mx.loperstack.tokkip.fit.GYM_PLANS
import mx.loperstack.tokkip.fit.GymPlan
import mx.loperstack.tokkip.fit.GymSet
import mx.loperstack.tokkip.fit.LIFTS
import mx.loperstack.tokkip.fit.clock
import mx.loperstack.tokkip.fit.gymKcal
import mx.loperstack.tokkip.fit.isRecord
import mx.loperstack.tokkip.fit.kg
import mx.loperstack.tokkip.fit.lastWeight
import mx.loperstack.tokkip.fit.lift
import mx.loperstack.tokkip.fit.records
import mx.loperstack.tokkipfit.data.HealthData
import mx.loperstack.tokkipfit.data.Prefs
import kotlin.math.roundToInt

/** A free session: no plan, add exercises as you go. */
val FREE_PLAN = GymPlan("libre", "Sesión libre", "Agrega los ejercicios que vayas haciendo.", emptyList())

@Composable
fun GymScreen(bottom: PaddingValues, onStart: (GymPlan) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val recs = remember { records(Prefs.gymSets()).values.sortedByDescending { it.best.at }.take(8) }
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottom.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.statusBarsPadding(),
    ) {
        item {
            Appear(0) {
                Column(Modifier.padding(top = 18.dp)) {
                    Title("Gym", size = 44.sp)
                    Text("Elige un plan, o arma tu sesión. Tokkip lleva la cuenta.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                }
            }
        }
        itemsIndexed(GYM_PLANS, key = { _, p -> p.id }) { i, p ->
            Appear(1 + i) {
                FitCard(color = if (i % 2 == 0) cs.primaryContainer else cs.tertiaryContainer, onClick = { onStart(p) }) {
                    val fg = if (i % 2 == 0) cs.onPrimaryContainer else cs.onTertiaryContainer
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFont), color = fg)
                            Text(p.about, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f))
                            Spacer(Modifier.height(8.dp))
                            Text("${p.lifts.size} ejercicios · ${p.sets}×${p.reps} · descanso ${p.restSeconds} s", style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.8f))
                        }
                        Box(Modifier.size(90.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(80.dp).clip(PolygonShape(MShapes.Clover4)).background(fg.copy(alpha = 0.1f)))
                            Tokkip(Modifier.size(86.dp, 76.dp), color = Color(Prefs.color), exercise = lift(p.lifts.first())?.move ?: Exercise.LIFT, tempo = 0.8f)
                        }
                    }
                }
            }
        }
        item {
            Appear(GYM_PLANS.size + 1) {
                FitCard(onClick = { onStart(FREE_PLAN) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShapeIcon(Icons.Rounded.Add, cs.primary, cs.onPrimary, 46.dp, MShapes.Cookie6)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(FREE_PLAN.name, style = MaterialTheme.typography.titleLarge)
                            Text(FREE_PLAN.about, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (recs.isNotEmpty()) {
            item { Appear(GYM_PLANS.size + 2) { Text("Tus récords", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp)) } }
            items(recs, key = { it.lift }) { r ->
                val l = lift(r.lift)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainer).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.EmojiEvents, null, tint = LocalFit.current.kcal)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l?.name ?: r.lift, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (l?.bodyweight == true && r.heaviest.kg <= 0) "${r.best.reps} repeticiones"
                            else "${kg(r.heaviest.kg)} kg × ${r.heaviest.reps} · máx. estimado ${kg(r.best.oneRm.roundToInt().toDouble())} kg",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** One exercise in the session, and its sets (planned and done). */
private class Entry(val lift: String, planned: Int, reps: Int, kgStart: Double) {
    val sets = mutableStateListOf<SetRow>().apply { repeat(planned.coerceAtLeast(1)) { add(SetRow(kgStart, reps)) } }
}

private class SetRow(kg: Double, reps: Int) {
    var kg by mutableStateOf(kg)
    var reps by mutableIntStateOf(reps)
    var done by mutableStateOf(false)
    var record by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymSessionScreen(plan: GymPlan, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val cs = MaterialTheme.colorScheme
    val fit = LocalFit.current
    val history = remember { Prefs.gymSets().toMutableList() }
    val entries = remember {
        mutableStateListOf<Entry>().apply {
            for (id in plan.lifts) add(Entry(id, plan.sets, plan.reps, lastWeight(id, history)))
        }
    }
    var current by remember { mutableIntStateOf(0) }
    var started by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var restLeft by remember { mutableIntStateOf(0) }
    var restTotal by remember { mutableIntStateOf(plan.restSeconds) }
    var picking by remember { mutableStateOf(plan.lifts.isEmpty()) }
    var party by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf<Done?>(null) }
    var confirmClose by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    BackHandler { if (entries.any { e -> e.sets.any { it.done } }) confirmClose = true else onClose() }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis(); if (restLeft > 0) { restLeft--; if (restLeft in 1..3) Sounds.play("tick"); if (restLeft == 0) { Sounds.play("focus_start"); Haptics.success(context) } } } }

    fun finish() {
        val done = entries.flatMap { e -> e.sets.filter { it.done }.map { GymSet(e.lift, it.kg, it.reps) } }
        val secs = ((System.currentTimeMillis() - started) / 1000).toInt()
        val d = Done(System.currentTimeMillis(), plan.id, plan.name, secs, gymKcal(secs), kind = "gym", sets = done.size, volume = done.sumOf { it.volume })
        Prefs.addWorkout(d)
        finished = d
        Sounds.play("level_up"); Haptics.success(context)
    }
    LaunchedEffect(finished) { finished?.let { runCatching { HealthData.write(context, it) } } }

    val done = finished
    Box(Modifier.fillMaxSize().background(cs.surface)) {
        if (done != null) {
            GymSummary(plan, done, entries.sumOf { e -> e.sets.count { it.record } }, onClose)
        } else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { if (entries.any { e -> e.sets.any { it.done } }) confirmClose = true else onClose() }) { Icon(Icons.Rounded.Close, "Salir") }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(plan.name, style = MaterialTheme.typography.titleLarge)
                    Text(clock((now - started) / 1000), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                Button(onClick = { finish() }, enabled = entries.any { e -> e.sets.any { it.done } }) { Text("Terminar") }
            }
            // The exercises as chips: tap one to jump to it.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.forEachIndexed { i, e ->
                    val all = e.sets.all { it.done }
                    Text(
                        lift(e.lift)?.name ?: e.lift,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (i == current) cs.onPrimary else cs.onSurface,
                        modifier = Modifier.clip(CircleShape).background(if (i == current) cs.primary else if (all) fit.minutes.copy(alpha = 0.25f) else cs.surfaceContainerHigh)
                            .clickable { current = i }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                Text(
                    "+ Ejercicio", style = MaterialTheme.typography.labelLarge, color = cs.primary,
                    modifier = Modifier.clip(CircleShape).background(cs.primaryContainer).clickable { picking = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            val e = entries.getOrNull(current)
            if (e == null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Tokkip(Modifier.size(170.dp, 150.dp), color = Color(Prefs.color), mood = Mood.FOCUS, interactive = true)
                        Text("Agrega tu primer ejercicio", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                val l = lift(e.lift)
                Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
                    val sweep by animateFloatAsState(if (restLeft > 0) restLeft / restTotal.toFloat() else 0f, tween(900), label = "rest")
                    if (restLeft > 0) Canvas(Modifier.size(220.dp)) {
                        val w = 12.dp.toPx(); val side = size.minDimension - w
                        val tl = Offset((size.width - side) / 2, (size.height - side) / 2)
                        drawArc(fit.track, 0f, 360f, false, tl, Size(side, side), style = Stroke(w))
                        drawArc(fit.minutes, -90f, 360f * sweep, false, tl, Size(side, side), style = Stroke(w, cap = StrokeCap.Round))
                    }
                    MorphingBlob(if (restLeft > 0) cs.secondaryContainer else cs.primaryContainer, Modifier.size(180.dp))
                    Tokkip(
                        Modifier.size(170.dp, 150.dp), color = Color(Prefs.color),
                        exercise = if (restLeft > 0) Exercise.REST else l?.move ?: Exercise.LIFT,
                        face = if (party > 0 && restLeft > plan.restSeconds - 4) TokkipEngine.Eye.STAR else null,
                        jump = party, interactive = true,
                    )
                    if (restLeft > 0) {
                        Row(
                            Modifier.align(Alignment.BottomCenter).clip(CircleShape).background(cs.secondary).clickable { restLeft = 0 }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Descanso ${restLeft} s", color = cs.onSecondary, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.SkipNext, "Saltar descanso", tint = cs.onSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text(l?.name ?: e.lift, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = DisplayFont), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text(l?.muscle ?: "", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                    itemsIndexed(e.sets) { i, s ->
                        SetLine(i + 1, s, l?.bodyweight == true) {
                            // A set done: kept, checked against your record, and the rest starts.
                            val set = GymSet(e.lift, s.kg, s.reps, System.currentTimeMillis())
                            s.record = isRecord(set, history)
                            history += set
                            Prefs.addGymSet(set)
                            s.done = true
                            if (s.record) {
                                party++
                                Prefs.recordsBeaten = Prefs.recordsBeaten + 1
                                Sounds.play("proud"); Haptics.success(context)
                            } else { Sounds.play("task_done"); Haptics.click(context) }
                            // The next set starts from this one's weight.
                            e.sets.getOrNull(i + 1)?.takeIf { !it.done }?.kg = s.kg
                            restTotal = plan.restSeconds.takeIf { it > 0 } ?: 90
                            restLeft = restTotal
                            if (e.sets.all { it.done } && current < entries.lastIndex) current++
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { e.sets.add(SetRow(e.sets.lastOrNull()?.kg ?: 0.0, e.sets.lastOrNull()?.reps ?: 10)) }) { Text("+ Serie") }
                            if (current < entries.lastIndex) FilledTonalButton(onClick = { current++ }) { Text("Siguiente ejercicio") }
                        }
                    }
                }
            }
        }
    }
    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }, containerColor = cs.surfaceContainerLow) {
            LazyColumn(Modifier.padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                item { Title("Agregar ejercicio", size = 26.sp) }
                items(LIFTS) { l ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable {
                            entries.add(Entry(l.id, 3, if (l.bodyweight) 12 else 10, lastWeight(l.id, history)))
                            current = entries.lastIndex
                            picking = false
                            Sounds.play("pop")
                        }.padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.FitnessCenter, null, tint = cs.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(l.name, style = MaterialTheme.typography.titleMedium)
                            Text(l.muscle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (confirmClose) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("¿Terminar la sesión?") },
            text = { Text("Tus series ya están guardadas. Termina para que cuente como entrenamiento.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmClose = false; finish() }) { Text("Terminar") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmClose = false; onClose() }) { Text("Salir sin terminar") } },
        )
    }
}

/** One set: its number, the weight and reps with − and +, and the check. */
@Composable
private fun SetLine(n: Int, s: SetRow, bodyweight: Boolean, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fit = LocalFit.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(if (s.done) (if (s.record) fit.kcal.copy(alpha = 0.18f) else fit.minutes.copy(alpha = 0.16f)) else cs.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$n", style = MaterialTheme.typography.titleLarge.copy(fontFamily = DisplayFont), modifier = Modifier.width(28.dp))
        if (!bodyweight || s.kg > 0) {
            Mini(if (s.kg <= 0) "Sin peso" else "${kg(s.kg)} kg", !s.done, { s.kg = (s.kg - 2.5).coerceAtLeast(0.0) }, { s.kg += 2.5 })
        } else {
            Mini("Tu peso", !s.done, {}, { s.kg = 2.5 })
        }
        Spacer(Modifier.width(6.dp))
        Mini("${s.reps} rep", !s.done, { s.reps = (s.reps - 1).coerceAtLeast(1) }, { s.reps += 1 })
        Spacer(Modifier.weight(1f))
        if (s.record) Icon(Icons.Rounded.EmojiEvents, "Récord", tint = fit.kcal)
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (s.done) fit.minutes else cs.primary).clickable(enabled = !s.done, onClick = onDone),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, "Hecha", tint = if (s.done) Color.Black else cs.onPrimary) }
    }
}

@Composable
private fun Mini(text: String, enabled: Boolean, minus: () -> Unit, plus: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (enabled) Icon(Icons.Rounded.Remove, "Menos", modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = minus).padding(4.dp), tint = cs.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 2.dp))
        if (enabled) Icon(Icons.Rounded.Add, "Más", modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = plus).padding(4.dp), tint = cs.onSurfaceVariant)
    }
}

@Composable
private fun GymSummary(plan: GymPlan, d: Done, records: Int, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fit = LocalFit.current
    var jump by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { repeat(3) { delay(900); jump++ } }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Title(if (records > 0) "¡Récord!" else "¡Buena sesión!", size = 44.sp)
        Text(plan.name, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            MorphingBlob(cs.primaryContainer, Modifier.size(250.dp))
            Tokkip(Modifier.size(210.dp, 186.dp), color = Color(Prefs.color), mood = Mood.HAPPY, face = TokkipEngine.Eye.STAR, dancing = true, jump = jump, interactive = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat3(Modifier.weight(1f), "${d.sets}", "series", fit.steps)
            Stat3(Modifier.weight(1f), "${(d.volume).roundToInt()}", "kg movidos", fit.kcal)
            Stat3(Modifier.weight(1f), clock(d.seconds.toLong()), "tiempo", fit.minutes)
        }
        if (records > 0) Text("$records ${if (records == 1) "récord nuevo" else "récords nuevos"}", style = MaterialTheme.typography.titleMedium, color = fit.kcal, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Listo", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
fun Stat3(modifier: Modifier, value: String, label: String, color: Color) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(color.copy(alpha = 0.14f)).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFont), color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
