// Your own: a routine for En casa (the moves you pick, each for so many
// seconds, a rest between them and how many rounds, with Tokkip showing the
// move as you choose it) and a plan for the Gym (the lifts you pick, by
// muscle, with their sets, reps and rest). Saved in Fit; your routines reach
// the watch too.

package mx.loperstack.tokkipfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.loperstack.tokkip.character.Exercise
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.fit.GymPlan
import mx.loperstack.tokkip.fit.HOME_MOVES
import mx.loperstack.tokkip.fit.LIFTS
import mx.loperstack.tokkip.fit.Move
import mx.loperstack.tokkip.fit.MyPlans
import mx.loperstack.tokkip.fit.MyRoutine
import mx.loperstack.tokkip.fit.kcal
import mx.loperstack.tokkip.fit.lift
import mx.loperstack.tokkipfit.data.Prefs
import kotlin.math.roundToInt

// ── En casa ───────────────────────────────────────────────────────────────────

/** Makes a routine of yours, or changes one (`id`; null for a new one). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyRoutineEditor(id: String?, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val start = remember { Prefs.myRoutines.firstOrNull { it.id == id } ?: MyRoutine.new(System.currentTimeMillis()) }
    var name by remember { mutableStateOf(start.name) }
    val moves = remember { mutableStateListOf<Move>().apply { addAll(start.moves) } }
    var rest by remember { mutableIntStateOf(start.rest) }
    var rounds by remember { mutableIntStateOf(start.rounds) }
    var picked by remember { mutableIntStateOf(0) }
    var asking by remember { mutableStateOf(false) }
    val draft = MyRoutine(start.id, name, moves.toList(), rest, rounds)
    val preview = draft.routine()
    BackHandler { onClose() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
            Spacer(Modifier.weight(1f))
            if (id != null) IconButton(onClick = { asking = true }) { Icon(Icons.Rounded.Delete, "Borrar", tint = cs.error) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Title(if (id == null) "Tu rutina" else "Editar rutina", size = 36.sp)
            // Tokkip shows the move you're on.
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                val m = moves.getOrNull(picked)?.move ?: Exercise.STRETCH
                Tokkip(Modifier.size(150.dp, 132.dp), color = Color(Prefs.color), exercise = m, tempo = 0.9f)
                Text(
                    "${preview.minutes} min · ~${preview.kcal(preview.seconds * 1000L).roundToInt()} kcal",
                    style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                )
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                label = { Text("Nombre") }, placeholder = { Text("Mi rutina") }, modifier = Modifier.fillMaxWidth(),
            )

            Text("Movimientos", style = MaterialTheme.typography.titleMedium, color = cs.primary)
            moves.forEachIndexed { i, m ->
                MoveRow(
                    m, picked = i == picked, onPick = { picked = i },
                    onSeconds = { moves[i] = m.copy(seconds = it.coerceIn(10, 300)) },
                    onUp = if (i > 0) ({ moves.add(i - 1, moves.removeAt(i)); picked = i - 1 }) else null,
                    onDown = if (i < moves.lastIndex) ({ moves.add(i + 1, moves.removeAt(i)); picked = i + 1 }) else null,
                    onRemove = if (moves.size > 1) ({ moves.removeAt(i); picked = picked.coerceAtMost(moves.lastIndex) }) else null,
                )
            }
            if (moves.size < 12) {
                Text("Agregar", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (e in HOME_MOVES) {
                        FilterChip(
                            selected = false,
                            onClick = { moves.add(Move(e, if (e == Exercise.STRETCH) 45 else 30)); picked = moves.lastIndex; Sounds.play("tick") },
                            label = { Text(e.label) },
                            leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }

            Text("Ritmo", style = MaterialTheme.typography.titleMedium, color = cs.primary)
            Counter("Descanso entre movimientos", if (rest == 0) "Sin descanso" else "$rest s", { rest = (rest - 5).coerceAtLeast(0) }, { rest = (rest + 5).coerceAtMost(120) })
            Counter("Rondas", "$rounds", { rounds = (rounds - 1).coerceAtLeast(1) }, { rounds = (rounds + 1).coerceAtMost(10) })
            Spacer(Modifier.height(4.dp))
        }
        Button(
            onClick = { Prefs.saveRoutine(draft); Sounds.play("pop"); onClose() },
            modifier = Modifier.fillMaxWidth().padding(20.dp).height(56.dp),
        ) { Text("Guardar", style = MaterialTheme.typography.titleMedium) }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("¿Borrar “${start.name}”?") },
            text = { Text("Tus entrenamientos con ella se quedan en tu historial.") },
            confirmButton = { TextButton(onClick = { Prefs.deleteRoutine(start.id); asking = false; onClose() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MoveRow(
    m: Move, picked: Boolean, onPick: () -> Unit, onSeconds: (Int) -> Unit,
    onUp: (() -> Unit)?, onDown: (() -> Unit)?, onRemove: (() -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    FitCard(color = if (picked) cs.secondaryContainer else cs.surfaceContainer, onClick = onPick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.move.label, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSeconds(m.seconds - 5) }, Modifier.size(36.dp)) { Icon(Icons.Rounded.Remove, "Menos") }
                    Text("${m.seconds} s", style = MaterialTheme.typography.titleLarge.copy(fontFamily = DisplayFont), modifier = Modifier.width(64.dp))
                    IconButton(onClick = { onSeconds(m.seconds + 5) }, Modifier.size(36.dp)) { Icon(Icons.Rounded.Add, "Más") }
                }
            }
            Column {
                IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.KeyboardArrowUp, "Subir") }
                IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.KeyboardArrowDown, "Bajar") }
            }
            IconButton(onClick = { onRemove?.invoke() }, enabled = onRemove != null) { Icon(Icons.Rounded.Delete, "Quitar") }
        }
    }
}

// ── Gym ──────────────────────────────────────────────────────────────────────

/** Makes a gym plan of yours, or changes one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyPlanEditor(id: String?, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val start = remember { Prefs.myPlans.firstOrNull { it.id == id } ?: MyPlans.new(System.currentTimeMillis()) }
    var name by remember { mutableStateOf(start.name) }
    val lifts = remember { mutableStateListOf<String>().apply { addAll(start.lifts) } }
    var sets by remember { mutableIntStateOf(start.sets) }
    var reps by remember { mutableIntStateOf(start.reps) }
    var rest by remember { mutableIntStateOf(start.restSeconds) }
    var asking by remember { mutableStateOf(false) }
    BackHandler { onClose() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
            Spacer(Modifier.weight(1f))
            if (id != null) IconButton(onClick = { asking = true }) { Icon(Icons.Rounded.Delete, "Borrar", tint = cs.error) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Title(if (id == null) "Tu plan" else "Editar plan", size = 36.sp)
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                label = { Text("Nombre") }, placeholder = { Text("Mi plan") }, modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (lifts.isEmpty()) "Elige tus ejercicios, en el orden en que los haces."
                else lifts.mapIndexed { i, l -> "${i + 1}. ${lift(l)?.name ?: l}" }.joinToString("\n"),
                style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
            )
            for ((muscle, group) in LIFTS.groupBy { it.muscle }) {
                Text(muscle, style = MaterialTheme.typography.titleMedium, color = cs.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (l in group) {
                        val on = l.id in lifts
                        FilterChip(
                            selected = on,
                            onClick = { if (on) lifts.remove(l.id) else if (lifts.size < 12) lifts.add(l.id); Sounds.play("tick") },
                            label = { Text(l.name) },
                        )
                    }
                }
            }
            Text("Series", style = MaterialTheme.typography.titleMedium, color = cs.primary)
            Counter("Series por ejercicio", "$sets", { sets = (sets - 1).coerceAtLeast(1) }, { sets = (sets + 1).coerceAtMost(10) })
            Counter("Repeticiones", "$reps", { reps = (reps - 1).coerceAtLeast(1) }, { reps = (reps + 1).coerceAtMost(50) })
            Counter("Descanso entre series", "$rest s", { rest = (rest - 15).coerceAtLeast(15) }, { rest = (rest + 15).coerceAtMost(600) })
            Spacer(Modifier.height(4.dp))
        }
        Button(
            onClick = { Prefs.savePlan(GymPlan(start.id, name, "", lifts.toList(), sets, reps, rest)); Sounds.play("pop"); onClose() },
            enabled = lifts.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(20.dp).height(56.dp),
        ) { Text("Guardar", style = MaterialTheme.typography.titleMedium) }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("¿Borrar “${start.name}”?") },
            text = { Text("Tus series y récords se quedan.") },
            confirmButton = { TextButton(onClick = { Prefs.deletePlan(start.id); asking = false; onClose() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Counter(label: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(cs.surfaceContainerHigh).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = { minus(); Sounds.play("tick") }) { Icon(Icons.Rounded.Remove, "Menos") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFamily = DisplayFont))
            Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = { plus(); Sounds.play("tick") }) { Icon(Icons.Rounded.Add, "Más") }
    }
}

/** "Crear …": the card at the end of a list. */
@Composable
fun CreateCard(title: String, about: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    FitCard(color = cs.surfaceContainerHigh, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(Icons.Rounded.Add, cs.tertiary, cs.onTertiary, 46.dp, mx.loperstack.tokkip.character.MShapes.Flower)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(about, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}
