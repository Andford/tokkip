// Sueño: last night under a starry sky, the numbers that matter (average,
// regularity, debt, a score), two weeks of nights, tips that fit you and one
// for today, your bedtime goal with a reminder, and the log itself — typed in
// or brought from your Pixel Watch through Health Connect.

package mx.loperstack.tokkip.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.*
import mx.loperstack.tokkip.character.MShapes

import mx.loperstack.tokkip.character.Squircle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import mx.loperstack.tokkip.data.AppData
import mx.loperstack.tokkip.data.HealthSleep
import mx.loperstack.tokkip.data.SLEEP_FACTORS
import mx.loperstack.tokkip.data.SLEEP_QUALITY
import mx.loperstack.tokkip.data.SleepEntry
import mx.loperstack.tokkip.data.SleepGoal
import mx.loperstack.tokkip.data.SleepTip
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.dailyTip
import mx.loperstack.tokkip.data.dayKey
import mx.loperstack.tokkip.data.dayStart
import mx.loperstack.tokkip.data.durationText
import mx.loperstack.tokkip.data.goalNight
import mx.loperstack.tokkip.data.hhmm
import mx.loperstack.tokkip.data.mergeHealth
import mx.loperstack.tokkip.data.minutes
import mx.loperstack.tokkip.data.personalTips
import mx.loperstack.tokkip.data.sleepEntry
import mx.loperstack.tokkip.data.sleepStats
import mx.loperstack.tokkip.data.upsertSleep
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.wellness.BedReminder
import mx.loperstack.tokkip.wellness.moodOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private val MX = Locale("es", "MX")
private val Night1 = Color(0xFF0D1230)
private val Night2 = Color(0xFF221647)
private val Moon = Color(0xFFFDE68A)
private val Star = Color(0xFFE0E7FF)

private fun timeOf(ms: Long) = SimpleDateFormat("HH:mm", MX).format(Date(ms))
private fun minuteOfDay(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(data: AppData, color: Color, actions: Actions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logging by remember { mutableStateOf<SleepEntry?>(null) }
    var showLog by remember { mutableStateOf(false) }
    // A night you tapped (in the chart or the list): its own details.
    var viewing by remember { mutableStateOf<SleepEntry?>(null) }
    val now = rememberNow(60_000)
    val goal = data.sleepGoal
    val last = data.sleep.lastOrNull()
    val stats = sleepStats(data.sleep, goal)

    suspend fun importHealth() {
        val nights = runCatching { HealthSleep.nights(context) }.getOrElse {
            Toast.makeText(context, "Health Connect no respondió", Toast.LENGTH_SHORT).show(); return
        }
        Store.setSleep(mergeHealth(Store.state.value.sleep, nights))
        Toast.makeText(context, if (nights.isEmpty()) "Health Connect no tiene noches recientes" else "${nights.size} noches del reloj", Toast.LENGTH_SHORT).show()
        if (nights.isNotEmpty()) { Haptics.success(context); Sounds.play("approve") }
    }
    val askHealth = rememberLauncherForActivityResult(HealthSleep.permissionContract()) { granted ->
        if (granted.containsAll(HealthSleep.PERMISSIONS)) scope.launch { importHealth() }
        else Toast.makeText(context, "Sin permiso de sueño en Health Connect", Toast.LENGTH_SHORT).show()
    }
    val fromWatch: () -> Unit = {
        Haptics.click(context)
        if (!HealthSleep.available(context)) {
            Toast.makeText(context, "Health Connect no está disponible en este teléfono", Toast.LENGTH_LONG).show()
        } else scope.launch { if (HealthSleep.granted(context)) importHealth() else askHealth.launch(HealthSleep.PERMISSIONS) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Sueño", Modifier.weight(1f))
            Label("Meta ${durationText(goal.minutes)}", Ink.Dim)
        }
        Spacer(Modifier.height(14.dp))
        NightHero(last, stats?.score, color, now)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Registrar noche", Modifier.weight(1f), Ink.VioletSoft, Ink.OnViolet) {
                Haptics.click(context)
                logging = null
                showLog = true
            }
            PrimaryButton("Del reloj", Modifier.weight(1f), Ink.CardHigh, Ink.Text, onClick = fromWatch)
        }
        if (stats != null) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(durationText(stats.avgMin), "promedio · ${stats.nights} noches", Modifier.weight(1f))
                StatBox("±${stats.spread} min", "constancia al acostarte", Modifier.weight(1f))
                StatBox(if (stats.debt == 0) "0" else durationText(stats.debt), "deuda de la semana", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth()) {
                Label("Últimas noches", Ink.Dim, weight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                NightsChart(data.sleep.takeLast(14), goal) { viewing = it }
                Label("Toca una barra para ver esa noche", Ink.Dim2, 11.sp)
            }
        }

        Text("Consejos para ti", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp))
        for (t in personalTips(data.sleep, goal)) TipCard(t, highlight = true)
        TipCard(dailyTip(now).let { it.copy(title = "Consejo del día · ${it.title}") }, highlight = false)

        Text("Despertador", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp))
        AlarmsPanel(data)

        Text("Tu horario", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp))
        GoalPanel(goal, data.bedReminder)

        if (data.sleep.isNotEmpty()) {
            Text("Registro", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp))
            for (e in data.sleep.sortedByDescending { it.night }.take(30)) {
                NightRow(e) { viewing = e }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    viewing?.let { e ->
        ModalBottomSheet(
            onDismissRequest = { viewing = null }, containerColor = Ink.CardHigh,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            NightDetail(e, goal, color, now) { viewing = null; logging = e; showLog = true }
        }
    }
    if (showLog) {
        ModalBottomSheet(onDismissRequest = { showLog = false }, containerColor = Ink.CardHigh) {
            LogNightSheet(logging, goal) { e ->
                showLog = false
                if (e != null) {
                    Store.setSleep(upsertSleep(Store.state.value.sleep.filter { it.night != logging?.night }, e))
                    actions.jump++
                    Haptics.success(context)
                    Sounds.play("approve")
                }
            }
        }
    }
}

/** Last night under the stars: Tokkip asleep, the hours, the times, the stages and the score. */
@Composable
private fun NightHero(last: SleepEntry?, score: Int?, color: Color, now: Long, label: String? = null) {
    val stars = remember { List(46) { floatArrayOf(Random.nextFloat(), Random.nextFloat() * 0.75f, 0.6f + Random.nextFloat() * 1.6f, Random.nextFloat() * 6.28f, 0.6f + Random.nextFloat() * 1.6f) } }
    // The sky's clock: stars twinkle, a shooting star crosses now and then.
    var t by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val t0 = androidx.compose.runtime.withFrameMillis { it }
        while (true) androidx.compose.runtime.withFrameMillis { t = (it - t0) / 1000f }
    }
    val fresh = last != null && last.night == dayKey(now)
    Box(Modifier.fillMaxWidth().clip(Squircle(28.dp)).background(Brush.verticalGradient(listOf(Night1, Night2)))) {
        Canvas(Modifier.matchParentSize()) {
            for (st in stars) {
                val tw = 0.5f + 0.5f * kotlin.math.sin(t * st[4] + st[3])
                val a = (0.15f + 0.75f * tw) * (0.5f + st[2] / 4f)
                drawCircle(Star.copy(alpha = a.coerceIn(0f, 1f)), st[2] * density * (0.8f + 0.4f * tw), Offset(st[0] * size.width, st[1] * size.height))
            }
            // A shooting star every 7 s, for one second.
            val cycle = t % 7f
            if (cycle < 1f) {
                val k = cycle
                val sx = size.width * (0.15f + 0.5f * k)
                val sy = size.height * (0.12f + 0.25f * k)
                drawLine(
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.Transparent, Star.copy(alpha = 0.9f * (1 - k))), Offset(sx - 60f, sy - 30f), Offset(sx, sy)),
                    Offset(sx - 60f, sy - 30f), Offset(sx, sy), strokeWidth = 3f, cap = StrokeCap.Round,
                )
                drawCircle(Star.copy(alpha = 1 - k), 2.5f * density, Offset(sx, sy))
            }
            drawCircle(Moon, 18.dp.toPx(), Offset(size.width - 44.dp.toPx(), 40.dp.toPx()))
            drawCircle(Night1, 15.dp.toPx(), Offset(size.width - 36.dp.toPx(), 34.dp.toPx()))
        }
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(label ?: if (fresh) "ANOCHE" else "ÚLTIMA NOCHE REGISTRADA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA5B4FC))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (last == null) {
                        Text("Sin registros aún", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Anota tu noche o tráela de tu Pixel Watch.", fontSize = 13.sp, color = Color(0xFFC7D2FE))
                    } else {
                        Text(durationText(last.minutes), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("${timeOf(last.bed)} → ${timeOf(last.wake)}", fontSize = 15.sp, color = Color(0xFFC7D2FE), fontWeight = FontWeight.SemiBold)
                        SLEEP_QUALITY.firstOrNull { it.first == last.quality }?.let { (q, label) ->
                            val c = moodOf(q)?.color?.let { Color(it) } ?: Color.White
                            Text(
                                label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c,
                                modifier = Modifier.padding(top = 6.dp).clip(Squircle(10.dp)).background(c.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Tokkip(Modifier.width(120.dp).height(100.dp), color = color, mood = if (last == null) Mood.SLEEPY else Mood.IDLE, greet = true, greetSound = "lullaby")
            }
            if (last != null && (last.deepMin + last.remMin) > 0) {
                Spacer(Modifier.height(12.dp))
                Stages(last)
            }
            if (score != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.matchParentSize()) {
                            val s = 4.dp.toPx()
                            drawArc(Color(0x26FFFFFF), -90f, 360f, false, Offset(s / 2, s / 2), Size(size.width - s, size.height - s), style = Stroke(s))
                            drawArc(Moon, -90f, 3.6f * score, false, Offset(s / 2, s / 2), Size(size.width - s, size.height - s), style = Stroke(s, cap = StrokeCap.Round))
                        }
                        Text("$score", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Text(
                        "  " + when { score >= 85 -> "Puntaje de la semana: excelente"; score >= 70 -> "Puntaje de la semana: bien"; score >= 50 -> "Puntaje de la semana: mejorable"; else -> "Puntaje de la semana: a cuidarte" },
                        fontSize = 13.sp, color = Color(0xFFC7D2FE),
                    )
                }
            }
        }
    }
}

/** One night, on its own: under the stars as Anoche, against your goal, what played a part, your note. */
@Composable
private fun NightDetail(e: SleepEntry, goal: SleepGoal, color: Color, now: Long, edit: () -> Unit) {
    val title = SimpleDateFormat("EEEE d 'de' MMMM", MX).format(Date(dayStart(e.night))).uppercase(MX)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
        NightHero(e, null, color, now, label = "LA NOCHE DEL $title")
        Spacer(Modifier.height(12.dp))
        val diff = e.minutes - goal.minutes
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox(durationText(e.minutes), "dormido", Modifier.weight(1f))
            StatBox(
                if (diff >= 0) "+${durationText(diff)}" else "−${durationText(-diff)}",
                if (diff >= 0) "sobre tu meta" else "bajo tu meta", Modifier.weight(1f),
            )
            StatBox(if (e.awakeMin > 0) durationText(e.awakeMin) else "—", "despierto", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox(timeOf(e.bed), "te acostaste", Modifier.weight(1f))
            StatBox(timeOf(e.wake), "despertaste", Modifier.weight(1f))
            StatBox(if (e.source == "health") "Reloj" else "A mano", "registro", Modifier.weight(1f))
        }
        if (e.factors.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Label("Lo que influyó", Ink.Dim, weight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (id in e.factors) {
                    val name = SLEEP_FACTORS.firstOrNull { it.first == id }?.second ?: id
                    Row(
                        Modifier.clip(Squircle(12.dp)).background(Ink.Card).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MIcon.sleepFactor[id]?.let { Icon(it, null, tint = Ink.Dim, modifier = Modifier.size(16.dp)) }
                        Text(" $name", fontSize = 13.sp)
                    }
                }
            }
        }
        if (e.note.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
                Label("Tu nota", Ink.Dim, weight = FontWeight.Bold)
                Text(e.note, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Editar esta noche", Modifier.fillMaxWidth(), Ink.CardHigh, Ink.Text, onClick = edit)
    }
}

/** Deep, REM, light and awake as one bar, from the watch's stages. */
@Composable
private fun Stages(e: SleepEntry) {
    val total = ((e.wake - e.bed) / 60_000L).toInt().coerceAtLeast(1)
    val light = (total - e.deepMin - e.remMin - e.awakeMin).coerceAtLeast(0)
    val parts = listOf(
        Triple("Profundo", e.deepMin, Color(0xFF6366F1)), Triple("REM", e.remMin, Color(0xFF22D3EE)),
        Triple("Ligero", light, Color(0xFFA5B4FC)), Triple("Despierto", e.awakeMin, Color(0xFFF472B6)),
    )
    Canvas(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
        var x = 0f
        for ((_, m, c) in parts) {
            val w = size.width * m / total
            drawRect(c, Offset(x, 0f), Size(w, size.height))
            x += w
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((label, m, c) in parts) {
            if (m <= 0) continue
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                Text(" $label ${durationText(m)}", fontSize = 11.sp, color = Color(0xFFC7D2FE))
            }
        }
    }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(Squircle(20.dp)).background(Ink.Card).border(1.dp, Ink.Hairline, Squircle(20.dp)).padding(12.dp)) {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Ink.Text)
        Label(label, Ink.Dim2, 11.sp)
    }
}

/** Bars per night (height = hours, colour = how rested), and the goal as a dashed line. */
@Composable
private fun NightsChart(nights: List<SleepEntry>, goal: SleepGoal, pick: (SleepEntry) -> Unit = {}) {
    val maxMin = maxOf(goal.minutes + 60, nights.maxOfOrNull { it.minutes } ?: 0)
    val dim = Ink.Track
    val goalColor = Ink.Dim2
    val accent = Ink.Violet
    Canvas(
        Modifier.fillMaxWidth().height(140.dp).pointerInput(nights) {
            detectTapGestures { o ->
                val n = maxOf(nights.size, 7)
                val i = (o.x / (size.width.toFloat() / n)).toInt() - (n - nights.size)
                nights.getOrNull(i)?.let(pick)
            }
        },
    ) {
        val n = maxOf(nights.size, 7)
        val slot = size.width / n
        val bw = slot * 0.6f
        val gy = size.height * (1 - goal.minutes.toFloat() / maxMin)
        drawLine(goalColor, Offset(0f, gy), Offset(size.width, gy), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        nights.forEachIndexed { i, e ->
            val h = size.height * e.minutes / maxMin
            val c = moodOf(e.quality)?.let { Color(it.color) } ?: accent
            val x = slot * (i + (n - nights.size)) + (slot - bw) / 2
            drawRoundRect(dim, Offset(x, 0f), Size(bw, size.height), androidx.compose.ui.geometry.CornerRadius(bw / 2))
            drawRoundRect(c, Offset(x, size.height - h), Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 2))
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth()) {
        Label(nights.firstOrNull()?.let { SimpleDateFormat("d MMM", MX).format(Date(dayStart(it.night))) } ?: "", Ink.Dim2, 11.sp)
        Spacer(Modifier.weight(1f))
        Label("- - meta ${durationText(goal.minutes)}", Ink.Dim2, 11.sp)
    }
}

@Composable
private fun TipCard(t: SleepTip, highlight: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(Squircle(20.dp))
            .background(if (highlight) Ink.VioletDeep else Ink.Card).border(1.dp, if (highlight) Ink.VioletLine else Ink.Hairline, Squircle(20.dp))
            .padding(14.dp),
    ) {
        ShapeIcon(MIcon.tipIcon(t.icon), shape = if (highlight) MShapes.Sunny else MShapes.Cookie9, size = 42.dp, container = if (highlight) Ink.Violet.copy(alpha = 0.25f) else Ink.VioletDeep)
        Column(Modifier.padding(start = 12.dp)) {
            Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink.Text)
            Spacer(Modifier.height(2.dp))
            Text(t.text, fontSize = 13.sp, color = Ink.Dim)
        }
    }
}

@Composable
private fun GoalPanel(goal: SleepGoal, reminder: Boolean) {
    val context = LocalContext.current
    fun pick(start: Int, set: (Int) -> Unit) =
        TimePickerDialog(context, { _, h, m -> set(h * 60 + m) }, start / 60, start % 60, true).show()
    fun save(g: SleepGoal, r: Boolean) {
        Store.setSleepGoal(g, r)
        BedReminder.sync(context)
        Haptics.click(context)
    }
    Panel(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeChip("Dormir", hhmm(goal.bedMin), Modifier.weight(1f), Icons.Rounded.Bedtime) { pick(goal.bedMin) { save(goal.copy(bedMin = it), reminder) } }
            TimeChip("Despertar", hhmm(goal.wakeMin), Modifier.weight(1f), Icons.Rounded.WbSunny) { pick(goal.wakeMin) { save(goal.copy(wakeMin = it), reminder) } }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Recordatorio para dormir", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Label("30 min antes, con un consejo y respiración de 5 min", Ink.Dim2, 12.sp)
            }
            Switch(checked = reminder, onCheckedChange = { save(goal, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet))
        }
    }
}

@Composable
private fun TimeChip(label: String, value: String, modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Column(modifier.clip(Squircle(22.dp)).background(Ink.CardHigh).clickable(onClick = onClick).padding(14.dp)) {
        if (icon != null) IconLabel(icon, label, Ink.Dim, size = 12.sp) else Label(label, Ink.Dim, 12.sp)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink.Text)
    }
}

@Composable
private fun NightRow(e: SleepEntry, edit: () -> Unit) {
    val q = moodOf(e.quality)
    Row(
        Modifier.fillMaxWidth().clip(Squircle(18.dp)).background(Ink.Card).border(1.dp, Ink.Hairline, Squircle(18.dp))
            .clickable(onClick = edit).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                SimpleDateFormat("EEE d MMM", MX).format(Date(dayStart(e.night))).replaceFirstChar { it.uppercase(MX) },
                fontSize = 13.sp, color = Ink.Dim, fontWeight = FontWeight.SemiBold,
            )
            Text("${durationText(e.minutes)}  ·  ${timeOf(e.bed)} → ${timeOf(e.wake)}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (e.factors.isNotEmpty()) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (id in e.factors) MIcon.sleepFactor[id]?.let { Icon(it, null, tint = Ink.Dim, modifier = Modifier.size(16.dp)) }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (q != null) Text(SLEEP_QUALITY.first { it.first == e.quality }.second, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(q.color))
            Text(if (e.source == "health") "Reloj" else "A mano", fontSize = 11.sp, color = Ink.Dim2)
        }
    }
}

/** Typing a night in (or fixing one): its morning, the times, how it felt, what played a part. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogNightSheet(editing: SleepEntry?, goal: SleepGoal, done: (SleepEntry?) -> Unit) {
    val context = LocalContext.current
    val (gBed, gWake) = remember { goalNight(goal, System.currentTimeMillis()) }
    var morning by remember { mutableLongStateOf(editing?.wake ?: gWake) }
    var bedMin by remember { mutableIntStateOf(editing?.let { minuteOfDay(it.bed) } ?: minuteOfDay(gBed)) }
    var wakeMin by remember { mutableIntStateOf(editing?.let { minuteOfDay(it.wake) } ?: minuteOfDay(gWake)) }
    var quality by remember { mutableIntStateOf(editing?.quality ?: 0) }
    var factors by remember { mutableStateOf(editing?.factors?.toSet() ?: emptySet()) }
    var note by remember { mutableStateOf(editing?.note ?: "") }

    fun times(): Pair<Long, Long> {
        val day = Calendar.getInstance().apply {
            timeInMillis = morning
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val wake = day + wakeMin * 60_000L
        // A bedtime later in the day than the wake-up was the evening before.
        val bed = day + bedMin * 60_000L - if (bedMin > wakeMin) 24 * 3600_000L else 0L
        return bed to wake
    }
    val (bed, wake) = times()

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text(if (editing == null) "¿Cómo dormiste?" else "Editar noche", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeChip("Mañana del", SimpleDateFormat("EEE d", MX).format(Date(morning)), Modifier.weight(1f)) {
                val c = Calendar.getInstance().apply { timeInMillis = morning }
                DatePickerDialog(context, { _, y, m, d ->
                    morning = Calendar.getInstance().apply { timeInMillis = morning; set(y, m, d) }.timeInMillis
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }
            TimeChip("Me dormí", hhmm(bedMin), Modifier.weight(1f)) {
                TimePickerDialog(context, { _, h, m -> bedMin = h * 60 + m }, bedMin / 60, bedMin % 60, true).show()
            }
            TimeChip("Desperté", hhmm(wakeMin), Modifier.weight(1f)) {
                TimePickerDialog(context, { _, h, m -> wakeMin = h * 60 + m }, wakeMin / 60, wakeMin % 60, true).show()
            }
        }
        Text(
            "${durationText(((wake - bed) / 60_000L).toInt())} de sueño", color = Ink.VioletSoft, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.height(16.dp))
        Label("¿Cómo despertaste?", Ink.Dim, 12.sp, FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            for ((q, label) in SLEEP_QUALITY) {
                val m = moodOf(q)!!
                val on = quality == q
                Column(
                    Modifier.weight(1f).clip(Squircle(14.dp)).background(if (on) Color(m.color).copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { quality = if (on) 0 else q; Haptics.click(context) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Tokkip(Modifier.width(46.dp).height(38.dp), color = Color(m.color), face = m.face)
                    Text(label, fontSize = 10.sp, textAlign = TextAlign.Center, color = if (on) Color(m.color) else Ink.Dim, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Label("¿Qué influyó?", Ink.Dim, 12.sp, FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, label) in SLEEP_FACTORS) {
                Pill(label, id in factors, MIcon.sleepFactor[id]) {
                    factors = if (id in factors) factors - id else factors + id
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = note, onValueChange = { note = it.take(300) }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Una nota (sueños, despertares…)", color = Ink.Dim2) }, shape = Squircle(16.dp), maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink.Violet, unfocusedBorderColor = Ink.Hairline,
                focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = Ink.Violet,
            ),
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Guardar noche", Modifier.fillMaxWidth(), Ink.VioletSoft, Ink.OnViolet) {
            if (wake - bed < 30 * 60_000L) {
                Toast.makeText(context, "Revisa las horas: menos de 30 min", Toast.LENGTH_SHORT).show()
                return@PrimaryButton
            }
            done(
                sleepEntry(bed, wake, quality, factors.toList(), note, editing?.source ?: "manual")
                    .copy(deepMin = editing?.deepMin ?: 0, remMin = editing?.remMin ?: 0, awakeMin = editing?.awakeMin ?: 0),
            )
        }
        if (editing != null) {
            Text(
                "Borrar esta noche", color = Ink.Red, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp).clickable {
                    Store.setSleep(Store.state.value.sleep.filter { it.night != editing.night })
                    done(null)
                },
            )
        }
    }
}
