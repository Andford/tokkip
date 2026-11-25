// A gym plan from the wrist: each lift with your last weight and reps (sent
// by the phone), - and + to change them, "Hecha" to log the set and start the
// rest (with a buzz near the end), and on to the next lift. At the end the
// workout and every set go to the phone, which counts them for your records
// and writes the session to Health Connect.

package mx.loperstack.tokkipfit.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import mx.loperstack.tokkip.character.Exercise
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.fit.Done
import mx.loperstack.tokkip.fit.GymPlan
import mx.loperstack.tokkip.fit.GymSet
import mx.loperstack.tokkip.fit.gymKcal
import mx.loperstack.tokkip.fit.kg
import mx.loperstack.tokkip.fit.lift
import mx.loperstack.tokkip.fit.startOf
import kotlin.math.roundToInt

private val GymCoral = Color(0xFFFF3D8B)
private val GymLime = Color(0xFFD7FF3A)
private val Pill = Color(0xFF2A2D33)

@Composable
fun GymWatch(plan: GymPlan, onEnd: () -> Unit) {
    val context = LocalContext.current
    val started = remember { System.currentTimeMillis() }
    val sets = remember { mutableStateListOf<GymSet>() }
    var at by remember { mutableIntStateOf(0) }            // which lift
    var setNo by remember { mutableIntStateOf(0) }         // sets done of this lift
    val first = remember { startOf(plan.lifts.first(), plan, WatchMine.last) }
    var weight by remember { mutableDoubleStateOf(first.kg) }
    var reps by remember { mutableIntStateOf(first.reps) }
    var left by remember { mutableIntStateOf(0) }          // rest seconds left
    var done by remember { mutableStateOf<Done?>(null) }
    val beats = remember { ArrayList<Int>() }
    val bpm = watchHeartRate(done == null)
    LaunchedEffect(bpm) { if (bpm > 0) beats += bpm }
    LaunchedEffect(left > 0) {
        while (left > 0) { delay(1000); left--; if (left in 1..3) buzzOnce(context, 40); if (left == 0) buzzOnce(context, 400, 255) }
    }
    BackHandler { }

    fun goTo(i: Int) {
        at = i; setNo = 0
        val s = startOf(plan.lifts[i], plan, WatchMine.last + sets)
        weight = s.kg; reps = s.reps
    }

    fun finish() {
        left = 0
        val secs = ((System.currentTimeMillis() - started) / 1000).toInt()
        if (sets.isEmpty()) { onEnd(); return }
        val d = Done(
            at = System.currentTimeMillis(), routine = plan.id, name = plan.name, seconds = secs, kcal = gymKcal(secs),
            bpm = if (beats.isEmpty()) 0 else beats.average().roundToInt(), kind = "gym", from = "watch",
            sets = sets.size, volume = sets.sumOf { it.volume },
        )
        WatchMine.remember(context, sets.toList())
        sendDone(context, d, sets = sets.toList())
        Sounds.play("level_up")
        done = d
    }

    done?.let { Summary(it, onEnd); return }
    val lift = lift(plan.lifts[at])
    val body = lift?.bodyweight == true

    if (left > 0) {
        // Resting: the time left, and a tap to skip it.
        Box(Modifier.fillMaxSize().clickable { left = 0 }, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = left / plan.restSeconds.toFloat(), modifier = Modifier.fillMaxSize().padding(3.dp),
                startAngle = 290f, endAngle = 250f, indicatorColor = GymLime, trackColor = Color.White.copy(alpha = 0.12f), strokeWidth = 7.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Descansa", fontSize = 12.sp, color = GymLime, fontWeight = FontWeight.Bold)
                Tokkip(Modifier.size(70.dp, 62.dp), color = Color(WatchLook.look.color), exercise = Exercise.REST)
                Text("$left s", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                Text(
                    if (setNo < plan.sets) "Sigue: serie ${setNo + 1} de ${plan.sets}" else "Sigue: ${lift(plan.lifts.getOrNull(at + 1) ?: "")?.name ?: "terminar"}",
                    fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // How far into the plan you are.
        val total = plan.lifts.size * plan.sets
        CircularProgressIndicator(
            progress = (at * plan.sets + setNo) / total.toFloat(), modifier = Modifier.fillMaxSize().padding(3.dp),
            startAngle = 290f, endAngle = 250f, indicatorColor = GymCoral, trackColor = Color.White.copy(alpha = 0.12f), strokeWidth = 5.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(lift?.name ?: plan.lifts[at], fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 1)
            Text("Serie ${(setNo + 1).coerceAtMost(plan.sets)} de ${plan.sets} · ${at + 1}/${plan.lifts.size}", fontSize = 10.sp, color = GymCoral)
            if (!body) Stepper("${kg(weight)} kg", { weight = (weight - 2.5).coerceAtLeast(0.0) }, { weight += 2.5 })
            Stepper("$reps reps", { reps = (reps - 1).coerceAtLeast(1) }, { reps = (reps + 1).coerceAtMost(50) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Round(Icons.Rounded.Stop, "Terminar", Color(0xFF3A2020), Color.White, 30) { finish() }
                Box(
                    Modifier.clip(CircleShape).background(GymLime).clickable {
                        sets += GymSet(plan.lifts[at], if (body) 0.0 else weight, reps, System.currentTimeMillis())
                        setNo++
                        buzzOnce(context)
                        Sounds.play("tick")
                        when {
                            setNo < plan.sets -> left = plan.restSeconds
                            at < plan.lifts.lastIndex -> { goTo(at + 1); left = plan.restSeconds }
                            else -> finish()
                        }
                    }.padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Hecha", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Round(Icons.Rounded.SkipNext, "Siguiente ejercicio", Pill, Color.White, 30) {
                    if (at < plan.lifts.lastIndex) goTo(at + 1) else finish()
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun Stepper(value: String, minus: () -> Unit, plus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Round(Icons.Rounded.Remove, "Menos", Pill, Color.White, 28, minus)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.width(86.dp))
        Round(Icons.Rounded.Add, "Más", Pill, Color.White, 28, plus)
    }
}

@Composable
private fun Round(icon: ImageVector, label: String, bg: Color, fg: Color, size: Int, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = fg, modifier = Modifier.size((size * 0.55f).dp))
    }
}
