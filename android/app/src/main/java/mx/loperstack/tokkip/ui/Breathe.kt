// Respirar: the session (with the watch's pulse when it is on your wrist) and
// the full history — the week's minutes, the pulse before and after, and
// every session, from the phone and the watch.

package mx.loperstack.tokkip.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import mx.loperstack.tokkip.character.MShapes

import mx.loperstack.tokkip.character.Squircle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.breathing
import mx.loperstack.tokkip.data.WearSync
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.wellness.BREATHE_MINUTES
import mx.loperstack.tokkip.wellness.BreathSession
import mx.loperstack.tokkip.wellness.breathFrame
import mx.loperstack.tokkip.wellness.cue
import mx.loperstack.tokkip.wellness.rhythm
import mx.loperstack.tokkip.wellness.calmDrop
import mx.loperstack.tokkip.wellness.clock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val MX = Locale("es", "MX")
private val Pink = Color(0xFFFF7896)

@Composable
fun BreatheScreen(color: Color, actions: Actions, sessions: List<BreathSession> = emptyList()) {
    val b = actions.breathing
    val active = b.startedAt != null
    // Tokkip animates himself; the words only need a few updates a second.
    val now = rememberNow(if (active) 200L else 60_000L)
    val f = breathFrame(b, now)
    LaunchedEffect(f.done) { if (f.done && active) actions.stopBreathing(completed = true) }
    // The guide in your hand: pulses swell as you breathe in, fade as you breathe out.
    val context = androidx.compose.ui.platform.LocalContext.current
    val breathHaptics = Store.state.collectAsState().value.breathHaptics
    LaunchedEffect(f.phase, active) {
        if (active && breathHaptics && !f.done) {
            when (f.phase) {
                mx.loperstack.tokkip.wellness.BreathPhase.IN -> mx.loperstack.tokkip.character.Haptics.breath(context, true, b.inhale, guide = true)
                mx.loperstack.tokkip.wellness.BreathPhase.OUT -> mx.loperstack.tokkip.character.Haptics.breath(context, false, b.exhale, guide = true)
                else -> { mx.loperstack.tokkip.character.Haptics.stop(context); mx.loperstack.tokkip.character.Haptics.tick(context) }
            }
        }
    }
    // A soft flute for each turn: up as you breathe in, down as you breathe out.
    val breathFlute = Store.state.collectAsState().value.breathFlute
    LaunchedEffect(f.phase, active) {
        if (active && !f.done && breathFlute) when (f.phase) {
            mx.loperstack.tokkip.wellness.BreathPhase.IN -> mx.loperstack.tokkip.character.Sounds.play("breath_in")
            mx.loperstack.tokkip.wellness.BreathPhase.OUT -> mx.loperstack.tokkip.character.Sounds.play("breath_out")
            else -> Unit
        }
    }
    val hr by WearSync.heartRate.collectAsState()
    val liveBpm = if (active && hr.at >= (b.startedAt ?: Long.MAX_VALUE) && now - hr.at < 15_000) hr.bpm else 0
    LaunchedEffect(hr) { if (active && hr.at >= (b.startedAt ?: Long.MAX_VALUE)) actions.heartRate(hr.bpm) }
    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }

    if (active) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Ink.VioletDeep, Ink.Ground), radius = 1400f))) {
            Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(30.dp))
                Label("Sesión de ${b.minutes} min", Ink.Dim, 14.sp, FontWeight.SemiBold)
                Spacer(Modifier.weight(0.6f))
                Box(contentAlignment = Alignment.Center) {
                    MorphingBlob(Ink.Violet.copy(alpha = 0.16f), Modifier.size(280.dp), listOf(MShapes.Cookie12, MShapes.Flower, MShapes.Sunny, MShapes.Clover8))
                    Tokkip(Modifier.width(230.dp).height(190.dp), color = color, breathing = b, jump = actions.jump)
                }
                Spacer(Modifier.height(40.dp))
                Text(f.phase.cue(), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                Label(b.rhythm())
                Spacer(Modifier.height(14.dp))
                when {
                    liveBpm > 0 -> IconLabel(Icons.Rounded.Favorite, "$liveBpm lpm · tu reloj", Pink, size = 18.sp)
                    now - (b.startedAt ?: now) < 20_000 -> Label("Abriendo Tokkip en tu reloj para medir el pulso…", Ink.Dim2)
                    else -> Unit
                }
                Spacer(Modifier.height(20.dp))
                WavyProgress(f.progress, Modifier.width(280.dp), color = Ink.Violet)
                Row(Modifier.width(280.dp).padding(top = 8.dp)) {
                    Label("${f.breaths} respiraciones", modifier = Modifier.weight(1f))
                    Label("Quedan ${clock(f.left)}")
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton("Terminar") { actions.stopBreathing(completed = false) }
                Spacer(Modifier.height(16.dp))
            }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenTitle("Respira con Tokkip", Modifier.fillMaxWidth().padding(top = 18.dp, start = 6.dp), size = 36.sp)
        Spacer(Modifier.height(36.dp))
        Tokkip(Modifier.width(190.dp).height(156.dp), color = color, jump = actions.jump, interactive = true, sigh = true)
        Spacer(Modifier.height(10.dp))
        BreathPicker(actions)
        Spacer(Modifier.height(26.dp))
        History(sessions)
        Spacer(Modifier.height(24.dp))
    }
}

// ── Historial ─────────────────────────────────────────────────────────────────

private fun weekStart(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) + 5) % 7)) // Monday
}.timeInMillis

private fun dayLabel(at: Long, now: Long): String {
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val c = Calendar.getInstance().apply { timeInMillis = at }
    val sameYear = today.get(Calendar.YEAR) == c.get(Calendar.YEAR)
    val diff = today.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && diff == 0 -> "Hoy"
        sameYear && diff == 1 -> "Ayer"
        else -> SimpleDateFormat("EEEE d 'de' MMMM", MX).format(Date(at)).replaceFirstChar { it.uppercase(MX) }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun History(sessions: List<BreathSession>) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<BreathSession?>(null) }
    open?.let { s ->
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { open = null }, containerColor = Ink.CardHigh) { SessionDetail(s) }
    }
    val now = System.currentTimeMillis()
    val start = weekStart(now)
    val perDay = IntArray(7)
    val week = sessions.filter { it.at >= start }
    for (s in week) {
        val d = ((s.at - start) / 86_400_000L).toInt()
        if (d in 0..6) perDay[d] += s.minutes
    }
    val withHr = sessions.filter { it.hrStart > 0 && it.hrEnd > 0 }

    Text("Historial", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 10.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("${perDay.sum()}", "min esta semana", Ink.VioletSoft, Modifier.weight(1f))
        Stat("${week.size}", if (week.size == 1) "sesión" else "sesiones", Ink.Text, Modifier.weight(1f))
        val avgDrop = withHr.takeLast(10).map { it.hrStart - it.hrEnd }.takeIf { it.isNotEmpty() }?.average()
        Stat(avgDrop?.let { (if (it >= 0) "−" else "+") + "%.0f".format(kotlin.math.abs(it)) } ?: "–", "lpm al terminar", Pink, Modifier.weight(1f))
    }

    Spacer(Modifier.height(12.dp))
    Panel(Modifier.fillMaxWidth()) {
        Label("Minutos por día", Ink.Dim, weight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        WeekBars(perDay, now, start)
    }

    if (withHr.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth()) {
            Label("Tu pulso: al empezar → al terminar", Ink.Dim, weight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            PulseChart(withHr.takeLast(8))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF8E939C)))
                Label("  al empezar     ", Ink.Dim2, 12.sp)
                Box(Modifier.size(8.dp).clip(CircleShape).background(Pink))
                Label("  al terminar", Ink.Dim2, 12.sp)
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    if (sessions.isEmpty()) {
        Label("Aún no hay sesiones: elige 1, 5 o 10 minutos para empezar.", Ink.Dim2)
        return
    }
    var lastDay = ""
    for (s in sessions.sortedByDescending { it.at }.take(60)) {
        val day = dayLabel(s.at, now)
        if (day != lastDay) {
            lastDay = day
            Text(day.uppercase(MX), color = Ink.Dim2, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp, bottom = 6.dp))
        }
        SessionRow(s) { open = s }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(Squircle(20.dp)).background(Ink.Card).border(1.dp, Ink.Hairline, Squircle(20.dp)).padding(14.dp),
    ) {
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = color)
        Label(label, Ink.Dim2, 12.sp)
    }
}

@Composable
private fun WeekBars(perDay: IntArray, now: Long, start: Long) {
    val today = ((now - start) / 86_400_000L).toInt()
    val maxMin = (perDay.maxOrNull() ?: 0).coerceAtLeast(5)
    val days = listOf("L", "M", "M", "J", "V", "S", "D")
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        for (i in 0..6) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (perDay[i] > 0) "${perDay[i]}" else "", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink.VioletSoft)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.width(26.dp).height((6 + 100f * perDay[i] / maxMin).dp)
                        .clip(Squircle(8.dp))
                        .background(if (perDay[i] > 0) Ink.Violet else Ink.Track),
                )
                Spacer(Modifier.height(6.dp))
                Text(days[i], fontSize = 12.sp, fontWeight = if (i == today) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (i == today) Ink.Text else Ink.Dim2)
            }
        }
    }
}

/** One line per session: grey dot where the pulse started, pink where it ended. */
@Composable
private fun PulseChart(list: List<BreathSession>) {
    val lo = (list.minOf { minOf(it.hrEnd, it.hrMin.takeIf { m -> m > 0 } ?: it.hrEnd) } - 5).coerceAtLeast(30)
    val hi = list.maxOf { it.hrStart } + 5
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (s in list.reversed()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(SimpleDateFormat("EEE d", MX).format(Date(s.at)), fontSize = 12.sp, color = Ink.Dim2, modifier = Modifier.width(54.dp))
                Canvas(Modifier.weight(1f).height(18.dp)) {
                    fun x(bpm: Int) = size.width * (bpm - lo).toFloat() / (hi - lo).coerceAtLeast(1)
                    val y = size.height / 2
                    drawRoundRect(Ink.Track, Offset(0f, y - 2f), Size(size.width, 4f), CornerRadius(2f, 2f))
                    drawLine(Brush.horizontalGradient(listOf(Pink, Color(0xFF8E939C)), x(s.hrEnd), x(s.hrStart)), Offset(x(s.hrEnd), y), Offset(x(s.hrStart), y), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawCircle(Color(0xFF8E939C), radius = 7f, center = Offset(x(s.hrStart), y))
                    drawCircle(Pink, radius = 7f, center = Offset(x(s.hrEnd), y))
                }
                Text("${s.hrStart}→${s.hrEnd}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink.Text, modifier = Modifier.width(64.dp).padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SessionRow(s: BreathSession, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Squircle(18.dp)).background(Ink.Card).border(1.dp, Ink.Hairline, Squircle(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(SimpleDateFormat("HH:mm", MX).format(Date(s.at)), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("  ·  ${s.minutes} min · ${s.breaths} respiraciones", fontSize = 14.sp, color = Ink.Dim)
            }
            Spacer(Modifier.height(4.dp))
            if (s.hrStart > 0 && s.hrEnd > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconLabel(Icons.Rounded.Favorite, "${s.hrStart} → ${s.hrEnd} lpm", Pink, size = 15.sp)
                    if (s.hrMin > 0) Text("   mín ${s.hrMin}", fontSize = 13.sp, color = Ink.Dim2)
                }
            } else {
                Label("Sin pulso (sin reloj)", Ink.Dim2, 13.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val drop = s.calmDrop()
            if (drop >= 0.02f) {
                Text(
                    "−${(drop * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink.Green,
                    modifier = Modifier.clip(Squircle(10.dp)).background(Ink.Green.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                if (s.device == "watch") "Reloj" else "Teléfono", fontSize = 12.sp, color = Ink.VioletSoft,
                modifier = Modifier.clip(Squircle(10.dp)).background(Ink.VioletDeep).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

// ── Detalle de una sesión: cardiograma, pulso y variabilidad ──────────────────

@Composable
fun SessionDetail(s: BreathSession) {
    val stats = mx.loperstack.tokkip.wellness.hrvStats(s)
    val rows = mx.loperstack.tokkip.wellness.minuteTable(s)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 32.dp)) {
        Text(
            SimpleDateFormat("EEEE d 'de' MMMM · HH:mm", MX).format(Date(s.at)).replaceFirstChar { it.uppercase(MX) },
            fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
        )
        Label("${s.minutes} min · ${s.breaths} respiraciones · ${if (s.device == "watch") "con el reloj" else "en el teléfono"}", Ink.Dim)
        Spacer(Modifier.height(14.dp))
        if (stats == null) {
            Panel(Modifier.fillMaxWidth()) {
                Text("Esta sesión no tiene pulso", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Label("Respira con el Pixel Watch puesto (desde el reloj o desde aquí) y verás tu cardiograma y la variabilidad.", Ink.Dim)
            }
            return@Column
        }
        // The monitor.
        Box(Modifier.fillMaxWidth().height(150.dp).clip(Squircle(20.dp)).background(Color(0xFF07110B))) {
            mx.loperstack.tokkip.charts.Cardiogram(mx.loperstack.tokkip.charts.beatIntervals(s), Modifier.matchParentSize().padding(vertical = 10.dp))
            Text(
                "${stats.avgBpm} lpm", color = Color(0xFF4ADE80), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        }
        Label(
            if (stats.beatToBeat) "Trazo ilustrativo con tus intervalos latido a latido. No es un electrocardiograma."
            else "Trazo ilustrativo con tu pulso medido. No es un electrocardiograma.",
            Ink.Dim2, 11.sp,
        )
        Spacer(Modifier.height(16.dp))
        Label("Pulso durante la sesión", Ink.Dim, weight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        mx.loperstack.tokkip.charts.PulseCurve(s, Modifier.fillMaxWidth().height(120.dp), Color(0xFFFF7896), Ink.Track)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Label("Inicio ${s.hrStart} lpm", Ink.Dim2, 11.sp)
            Spacer(Modifier.weight(1f))
            Label("Final ${s.hrEnd} lpm", Ink.Dim2, 11.sp)
        }
        Spacer(Modifier.height(16.dp))
        Label("Variabilidad del pulso", Ink.Dim, weight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell("${stats.rmssd} ms", "RMSSD · ${mx.loperstack.tokkip.wellness.rmssdLabel(stats.rmssd)}", Modifier.weight(1f))
            StatCell("${stats.sdnn} ms", "SDNN", Modifier.weight(1f))
            StatCell("${stats.minBpm}–${stats.maxBpm}", "rango lpm", Modifier.weight(1f))
        }
        Label(
            if (stats.beatToBeat) "Medida latido a latido por el sensor del reloj."
            else "Estimada a partir de lecturas de pulso cada 2 s: sirve para comparar tus sesiones, no como medida clínica.",
            Ink.Dim2, 11.sp,
        )
        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Label("Minuto a minuto", Ink.Dim, weight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Panel(Modifier.fillMaxWidth(), padding = 12.dp) {
                TableRow("Min", "Prom.", "Mín", "Máx", "Var.", header = true)
                for (r in rows) TableRow("${r.minute}", "${r.avg}", "${r.min}", "${r.max}", if (r.rmssd > 0) "${r.rmssd} ms" else "–")
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(Squircle(18.dp)).background(Ink.Card).border(1.dp, Ink.Hairline, Squircle(18.dp)).padding(12.dp)) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Ink.Text)
        Label(label, Ink.Dim2, 11.sp)
    }
}

@Composable
private fun TableRow(vararg cells: String, header: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        cells.forEachIndexed { i, c ->
            Text(
                c, modifier = Modifier.weight(if (i == 0) 0.7f else 1f), fontSize = 13.sp,
                fontWeight = if (header) FontWeight.Bold else FontWeight.SemiBold,
                color = if (header) Ink.Dim2 else if (i == 0) Ink.VioletSoft else Ink.Text,
            )
        }
    }
}


// ── Tu respiración: ritmo y duración ──────────────────────────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BreathPicker(actions: Actions) {
    val data by Store.state.collectAsState()
    var custom by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val preview = data.breathing(null, data.breathMinutes)
    val pattern = mx.loperstack.tokkip.wellness.BREATH_PATTERNS.firstOrNull { it.id == data.breathPattern }
    Panel(Modifier.fillMaxWidth()) {
        Label("Ritmo", Ink.Dim, 12.sp, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (p in mx.loperstack.tokkip.wellness.BREATH_PATTERNS) {
                Segment(p.name, data.breathPattern == p.id) { Store.setBreath(p.id, data.breathCustom, data.breathMinutes) }
            }
            Pill("A tu medida", data.breathPattern == "custom", Icons.Rounded.Edit) { Store.setBreath("custom", data.breathCustom, data.breathMinutes); custom = true }
        }
        Spacer(Modifier.height(10.dp))
        RhythmBar(preview)
        Spacer(Modifier.height(6.dp))
        Label(pattern?.note ?: preview.rhythm(), Ink.Dim2, 12.sp)
        Spacer(Modifier.height(14.dp))
        Label("Duración", Ink.Dim, 12.sp, FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in listOf(1, 3, 5, 10, 15, 20)) Segment("$m min", data.breathMinutes == m) { Store.setBreath(data.breathPattern, data.breathCustom, m) }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Empezar · ${data.breathMinutes} min", Modifier.fillMaxWidth(), Ink.VioletSoft, Ink.OnViolet) { actions.startBreathing(data.breathMinutes) }
        Spacer(Modifier.height(8.dp))
        Label("Tokkip crece al inhalar y se encoge al exhalar. Con el reloj puesto mide tu pulso y vibra en cada fase.", Ink.Dim2, 12.sp)
    }
    if (custom) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { custom = false }, containerColor = Ink.CardHigh) {
            CustomRhythm(data.breathCustom) { Store.setBreath("custom", it, data.breathMinutes) }
        }
    }
}

/** The cycle drawn to scale: in, hold, out, pause. */
@Composable
private fun RhythmBar(b: mx.loperstack.tokkip.wellness.Breathing) {
    val parts = listOf(b.inhale to Ink.Violet, b.holdIn to Ink.VioletSoft.copy(alpha = 0.55f), b.exhale to Ink.Green, b.holdOut to Ink.Dim2.copy(alpha = 0.5f))
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
        var x = 0f
        val total = b.cycle.toFloat()
        for ((sec, c) in parts) {
            if (sec <= 0) continue
            val w = size.width * sec.toFloat() / total
            drawRect(c, Offset(x, 0f), Size(w - 3f, size.height))
            x += w
        }
    }
}

@Composable
private fun CustomRhythm(start: List<Double>, save: (List<Double>) -> Unit) {
    var v by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(start) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text("Tu ritmo", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        val rows = listOf(Triple("Inhala", 1f, 10f), Triple("Sostén", 0f, 10f), Triple("Exhala", 1f, 12f), Triple("Pausa", 0f, 10f))
        rows.forEachIndexed { i, (label, lo, hi) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp))
                androidx.compose.material3.Slider(
                    value = v[i].toFloat(), onValueChange = { nv -> v = v.toMutableList().also { it[i] = kotlin.math.round(nv * 2) / 2.0 } },
                    valueRange = lo..hi, modifier = Modifier.weight(1f),
                    onValueChangeFinished = { save(v) },
                )
                Text("${v[i]} s", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        RhythmBar(mx.loperstack.tokkip.wellness.Breathing(null, 1, v[0], v[1], v[2], v[3]))
        Spacer(Modifier.height(8.dp))
        Label("${"%.1f".format(60.0 / (v.sum().coerceAtLeast(1.0)))} respiraciones por minuto", Ink.Dim)
    }
}
