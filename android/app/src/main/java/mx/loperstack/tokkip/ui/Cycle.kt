// "Ciclo": your period and fertile days, an optional screen of Bienestar
// (Ajustes → Funciones opcionales). Today's card says where you are in your
// cycle and what's next; the month shows marked days and the estimates; a day
// opens to mark it and note the flow and how you felt. Everything stays
// encrypted on this phone (data/CycleStore.kt).

package mx.loperstack.tokkip.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.loperstack.tokkip.character.Squircle
import mx.loperstack.tokkip.data.CycleDay
import mx.loperstack.tokkip.data.CycleMath
import mx.loperstack.tokkip.data.CycleStore
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PeriodRed = Color(0xFFF0587A)
private val FertileTeal = Color(0xFF2DD4BF)
private val ES = Locale.forLanguageTag("es-MX")
private val DAY = DateTimeFormatter.ofPattern("EEE d 'de' MMM", ES)
private val SHORT = DateTimeFormatter.ofPattern("d MMM", ES)

private fun date(day: Int): LocalDate = LocalDate.ofEpochDay(day.toLong())
private fun nice(day: Int) = date(day).format(DAY)
private fun short(day: Int) = date(day).format(SHORT)
private fun days(n: Int) = if (n == 1) "1 día" else "$n días"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleScreen(color: Color = Color(0xFFF5F6F8)) {
    val context = LocalContext.current
    // Tokkip's reaction on the main card: a jump, a sound, a line.
    var jump by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf<Pair<String, Long>?>(null) }
    fun react(text: String, sound: String) {
        jump++
        flash = text to System.currentTimeMillis()
        Haptics.success(context)
        Sounds.play(sound)
    }
    val d by CycleStore.state.collectAsState()
    val today = CycleStore.today()
    val f = remember(d.period, d.notes, today) { CycleMath.forecast(d.period, today, d.notes) }
    val insights = remember(d.period, d.notes, today) { CycleMath.insights(d.period, d.notes, f, today) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var opened by remember { mutableIntStateOf(Int.MIN_VALUE) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Ciclo", Modifier.weight(1f))
            Icon(Icons.Rounded.Lock, null, tint = Ink.Dim2, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Label("Solo en este teléfono", Ink.Dim2, 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        TodayCard(d.period, f, today, color, jump, flash?.first)
        LaunchedEffect(flash) { if (flash != null) { delay(4500); flash = null } }
        if (insights.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Ink.VioletSoft, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Lo que noto", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                }
                for (line in insights) {
                    Spacer(Modifier.height(8.dp))
                    Text(line, fontSize = 14.sp, color = Ink.Text)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (f.inPeriod) {
                PrimaryButton("Terminó hoy", Modifier.weight(1f), Ink.VioletSoft, Ink.OnViolet) {
                    CycleStore.endPeriod(context, today)
                    react("Listo, terminó tu periodo. ¡Bien hecho por cuidarte!", "approve")
                }
            } else {
                PrimaryButton("Empezó mi periodo", Modifier.weight(1f), PeriodRed, Color.White) {
                    CycleStore.startPeriod(context, today, f.periodLen)
                    react("Anotado: hoy empezó tu periodo. Tómatelo con calma, aquí estoy 💗", "love")
                }
            }
            PrimaryButton("Hoy", Modifier.weight(0.6f), Ink.Card, Ink.Text) { opened = today }
        }
        Spacer(Modifier.height(16.dp))
        Panel(Modifier.fillMaxWidth(), padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ChevronLeft, "Mes anterior", tint = Ink.Dim, modifier = Modifier.clip(CircleShape).clickable { month = month.minusMonths(1) }.padding(6.dp))
                Text(
                    month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", ES)).replaceFirstChar { it.uppercase() },
                    fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                Icon(Icons.Rounded.ChevronRight, "Mes siguiente", tint = Ink.Dim, modifier = Modifier.clip(CircleShape).clickable { month = month.plusMonths(1) }.padding(6.dp))
            }
            Spacer(Modifier.height(8.dp))
            MonthGrid(month, d.period, d.notes, f, today) { opened = it }
            Spacer(Modifier.height(10.dp))
            Legend()
        }
        Spacer(Modifier.height(12.dp))
        StatsCard(f)
        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
            Text("Avisos", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            ToggleRow("Antes de mi periodo", "Dos días antes de la fecha estimada", d.remindPeriod) { CycleStore.setReminders(context, it, d.remindFertile) }
            ToggleRow("Ventana fértil", "El día que empieza, según tus ciclos", d.remindFertile) { CycleStore.setReminders(context, d.remindPeriod, it) }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Son estimaciones a partir de tus propios ciclos: no sirven como método anticonceptivo ni para buscar un embarazo por sí solas. " +
                "Si tus ciclos cambian mucho, son muy dolorosos o algo te preocupa, consulta a tu ginecóloga o ginecólogo.",
            fontSize = 12.sp, color = Ink.Dim2, modifier = Modifier.padding(horizontal = 6.dp),
        )
        Spacer(Modifier.height(120.dp))
    }

    if (opened != Int.MIN_VALUE) {
        ModalBottomSheet(onDismissRequest = { opened = Int.MIN_VALUE }, containerColor = Ink.CardHigh) {
            DaySheet(opened, opened in d.period, d.notes[opened] ?: CycleDay(), future = opened > today, color = color) { opened = Int.MIN_VALUE }
        }
    }
}

@Composable
private fun TodayCard(period: Set<Int>, f: CycleMath.Forecast, today: Int, color: Color, jump: Int, flash: String?) {
    val mark = CycleMath.mark(today, period, f, today)
    val (title, line) = when {
        f.lastStart == null -> "Marca tu periodo" to "Toca «Empezó mi periodo» el primer día, o marca días pasados en el calendario. Con dos periodos marcados, Tokkip aprende tu ciclo."
        f.inPeriod -> "Día ${today - (CycleMath.periods(period).last().start) + 1} de tu periodo" to "Día ${f.day} de tu ciclo. Tómatelo con calma."
        f.late > 0 -> "Se retrasa ${days(f.late)}" to "Según tus ciclos, tu periodo debía empezar el ${nice(f.nextStart!!)}. Los ciclos varían; si te preocupa, consulta a tu médico."
        else -> {
            val left = f.nextStart!! - today
            val head = if (left == 0) "Tu periodo podría empezar hoy" else "Tu periodo, en ${days(left)}"
            val range = if (f.measured >= 2 || f.ovulationFromSigns) " (entre el ${short(f.nextStart - f.spread)} y el ${short(f.nextStart + f.spread)})" else ""
            head to "Día ${f.day} de tu ciclo · estimado para el ${nice(f.nextStart)}$range"
        }
    }
    val fertile = when {
        f.ovulation == null || f.inPeriod || f.late > 0 -> null
        mark == CycleMath.Mark.OVULATION -> "Hoy sería tu ovulación: probabilidad alta de embarazo."
        mark == CycleMath.Mark.FERTILE -> "Estás en tu ventana fértil (hasta el ${short(f.fertileTo!!)}): probabilidad alta de embarazo."
        f.fertileFrom!! > today -> "Ventana fértil: ${short(f.fertileFrom)} al ${short(f.fertileTo!!)} · ovulación ~${short(f.ovulation!!)}" +
            if (f.ovulationFromSigns) " (según tus señales)" else ""
        else -> "Fuera de tu ventana fértil: probabilidad baja (nunca cero)."
    }
    val fertileNow = mark == CycleMath.Mark.FERTILE || mark == CycleMath.Mark.OVULATION
    Panel(Modifier.fillMaxWidth(), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CycleRing(f, today, Modifier.size(128.dp)) {
                Tokkip(
                    Modifier.size(78.dp, 66.dp), color = color, jump = jump, interactive = true,
                    mood = if (f.inPeriod) Mood.SLEEPY else Mood.IDLE,
                    face = when {
                        f.inPeriod -> null
                        fertileNow -> TokkipEngine.Eye.HEART
                        f.late > 0 -> TokkipEngine.Eye.WIDE
                        else -> TokkipEngine.Eye.HAPPY
                    },
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (f.day != null) {
                    Text(
                        "DÍA ${f.day}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp,
                        color = when {
                            f.inPeriod -> PeriodRed
                            fertileNow -> FertileTeal
                            else -> Ink.VioletSoft
                        },
                    )
                }
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 24.sp)
                Spacer(Modifier.height(4.dp))
                Text(line, fontSize = 13.sp, color = Ink.Dim, lineHeight = 17.sp)
            }
        }
        if (fertile != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(Squircle(16.dp)).background(FertileTeal.copy(alpha = 0.14f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Spa, null, tint = FertileTeal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(fertile, fontSize = 13.sp, color = FertileTeal, fontWeight = FontWeight.SemiBold)
            }
        }
        AnimatedVisibility(
            visible = flash != null,
            enter = expandVertically() + fadeIn() + scaleIn(initialScale = 0.9f),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Bubble(flash.orEmpty(), Modifier.padding(top = 12.dp))
        }
    }
}

/**
 * The cycle as a ring: period days in red, the fertile window in teal with
 * ovulation as a dot, and where you are today as a white bead. Tokkip sits
 * in the middle.
 */
@Composable
private fun CycleRing(f: CycleMath.Forecast, today: Int, modifier: Modifier, content: @Composable () -> Unit) {
    val track = Ink.Track
    val sweep by animateFloatAsState(
        if (f.day == null) 0f else ((f.day - 1).toFloat() / f.cycleLen).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 60f), label = "ring",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.085f
            val inset = stroke / 2 + 4f
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            fun deg(day: Float) = -90f + 360f * day / f.cycleLen
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(stroke))
            if (f.lastStart != null) {
                drawArc(PeriodRed, deg(0f), 360f * f.periodLen / f.cycleLen, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                if (f.fertileFrom != null && f.ovulation != null) {
                    val from = (f.fertileFrom - f.lastStart).toFloat()
                    drawArc(FertileTeal.copy(alpha = 0.55f), deg(from), 360f * 7 / f.cycleLen, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    val ov = Math.toRadians(deg((f.ovulation - f.lastStart).toFloat() + 0.5f).toDouble())
                    val r = arc.width / 2
                    drawCircle(FertileTeal, stroke * 0.62f, Offset(center.x + r * kotlin.math.cos(ov).toFloat(), center.y + r * kotlin.math.sin(ov).toFloat()))
                }
                // Today: a bead that travels around as the days go by.
                val a = Math.toRadians((-90f + 360f * sweep).toDouble())
                val r = arc.width / 2
                val p = Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat())
                drawCircle(Color.White, stroke * 0.95f, p)
                drawCircle(if (f.inPeriod) PeriodRed else Color(0xFF6B5BD6), stroke * 0.55f, p)
            }
        }
        content()
    }
}

/** Tokkip's speech bubble. */
@Composable
private fun Bubble(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(Squircle(18.dp)).background(Ink.VioletSoft.copy(alpha = 0.18f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CheckCircle, null, tint = Ink.VioletSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = Ink.Text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MonthGrid(month: YearMonth, period: Set<Int>, notes: Map<Int, CycleDay>, f: CycleMath.Forecast, today: Int, open: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        for (w in listOf("L", "M", "M", "J", "V", "S", "D")) {
            Text(w, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = Ink.Dim2, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(4.dp))
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1
    val cells = lead + month.lengthOfMonth()
    for (row in 0 until (cells + 6) / 7) {
        Row(Modifier.fillMaxWidth()) {
            for (col in 0 until 7) {
                val i = row * 7 + col - lead
                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                    if (i in 0 until month.lengthOfMonth()) {
                        val day = first.plusDays(i.toLong()).toEpochDay().toInt()
                        DayCell(i + 1, CycleMath.mark(day, period, f, today), day == today, notes[day]) { open(day) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(n: Int, mark: CycleMath.Mark, today: Boolean, note: CycleDay?, onClick: () -> Unit) {
    val fill = when (mark) {
        CycleMath.Mark.PERIOD -> PeriodRed
        CycleMath.Mark.FERTILE -> FertileTeal.copy(alpha = 0.22f)
        CycleMath.Mark.OVULATION -> FertileTeal
        else -> Color.Transparent
    }
    var m = Modifier.fillMaxSize().clip(CircleShape).background(fill)
    if (mark == CycleMath.Mark.PREDICTED) m = m.border(2.dp, PeriodRed.copy(alpha = 0.7f), CircleShape)
    if (today) m = m.border(2.dp, Ink.Text, CircleShape)
    Box(m.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            n.toString(), fontSize = 14.sp, fontWeight = if (today) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = when (mark) {
                CycleMath.Mark.PERIOD, CycleMath.Mark.OVULATION -> Color.White
                else -> Ink.Text
            },
        )
        // A small heart for intimacy, a dot for any other note.
        if (note != null && note.sex != 0) {
            Icon(
                Icons.Rounded.Favorite, null, tint = if (note.sex == 2) Color(0xFFFF8FB1) else Ink.VioletSoft,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(9.dp),
            )
        }
        if (note != null && note != CycleDay(sex = note.sex)) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp).clip(CircleShape).background(Ink.VioletSoft))
        }
    }
}

@Composable
private fun Legend() {
    @Composable
    fun item(color: Color, ring: Boolean, text: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(12.dp).clip(CircleShape)
                    .then(if (ring) Modifier.border(2.dp, color, CircleShape) else Modifier.background(color)),
            )
            Spacer(Modifier.width(5.dp))
            Label(text, Ink.Dim, 12.sp)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        item(PeriodRed, false, "Periodo")
        item(PeriodRed, true, "Estimado")
        item(FertileTeal.copy(alpha = 0.5f), false, "Fértil")
        item(FertileTeal, false, "Ovulación")
    }
}

@Composable
private fun StatsCard(f: CycleMath.Forecast) {
    Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
        Text("Tu ciclo", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("${f.cycleLen} días", "ciclo promedio", Modifier.weight(1f))
            Stat("${f.periodLen} días", "periodo promedio", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                f.measured == 0 -> "Aún uso 28 días, lo más común. Con dos periodos marcados empiezo a usar los tuyos."
                f.irregular -> "Tus ciclos varían más de una semana entre sí, así que las fechas son menos seguras. Es común, pero si te inquieta, coméntalo con tu médico."
                else -> "Calculado con tus últimos ${f.measured} ${if (f.measured == 1) "ciclo" else "ciclos"}."
            },
            fontSize = 13.sp, color = Ink.Dim,
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(Squircle(18.dp)).background(Ink.Track).padding(12.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Label(label, Ink.Dim, 12.sp)
    }
}

private val Pink = Color(0xFFFF6FA3)
private val Shield = Color(0xFF8B7CF6)
private val Mucus = Color(0xFF5CC8E8)

/**
 * A day: Tokkip at the top reacts to everything you log (a jump, a sound, a
 * line, and "Guardado"), and each section has its colour and icons.
 * Everything saves as you tap; "Listo" closes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaySheet(day: Int, isPeriod: Boolean, note: CycleDay, future: Boolean, color: Color, close: () -> Unit) {
    val context = LocalContext.current
    var jump by remember { mutableIntStateOf(0) }
    var face by remember { mutableStateOf<TokkipEngine.Eye?>(TokkipEngine.Eye.HAPPY) }
    var said by remember(day) { mutableStateOf(if (future) "Ese día aún no llega." else "¿Cómo te fue este día? Toca lo que quieras anotar.") }
    var saved by remember { mutableLongStateOf(0L) }
    LaunchedEffect(saved) { if (saved > 0) { delay(1600); saved = 0 } }

    fun react(text: String, eye: TokkipEngine.Eye = TokkipEngine.Eye.HAPPY, sound: String = "pop") {
        jump++
        face = eye
        said = text
        saved = System.currentTimeMillis()
        Haptics.click(context)
        Sounds.play(sound, 0.6f)
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        // Tokkip and the date, with his line and "Guardado".
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tokkip(Modifier.size(84.dp, 70.dp), color = color, jump = jump, face = face, interactive = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(nice(day).replaceFirstChar { it.uppercase() }, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    AnimatedVisibility(saved > 0, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
                        Row(
                            Modifier.clip(CircleShape).background(FertileTeal.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Check, null, tint = FertileTeal, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Guardado", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FertileTeal)
                        }
                    }
                }
                AnimatedContent(said, transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith fadeOut() }, label = "said") { t ->
                    Text(t, fontSize = 14.sp, color = Ink.Dim, lineHeight = 18.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (future) {
            PrimaryButton("Listo", Modifier.fillMaxWidth(), Ink.VioletSoft, Ink.OnViolet, onClick = close)
            return@Column
        }

        // Period: one big card that turns red.
        val periodBg by animateColorAsState(if (isPeriod) PeriodRed else Ink.Card, label = "period")
        Row(
            Modifier.fillMaxWidth().clip(Squircle(22.dp)).background(periodBg)
                .clickable {
                    CycleStore.toggle(context, day)
                    if (isPeriod) react("Listo, quité el periodo de este día.", TokkipEngine.Eye.OPEN)
                    else react("Anotado tu periodo. Cuídate mucho, con calma 💗", TokkipEngine.Eye.HEART, "love")
                }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.WaterDrop, null, tint = if (isPeriod) Color.White else PeriodRed, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isPeriod) "Tuve periodo este día" else "¿Tuviste periodo?", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = if (isPeriod) Color.White else Ink.Text)
                Text(if (isPeriod) "Toca para quitarlo" else "Toca para marcarlo, aunque haya sido poquito", fontSize = 12.sp, color = if (isPeriod) Color(0xD9FFFFFF) else Ink.Dim)
            }
            AnimatedVisibility(isPeriod, enter = scaleIn(spring(dampingRatio = 0.4f)), exit = scaleOut()) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }

        if (isPeriod) {
            Section("Flujo", Icons.Rounded.WaterDrop, PeriodRed)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Ligero" to 1, "Medio" to 2, "Abundante" to 3).forEach { (label, level) ->
                    CycleChip(label, note.flow == level, PeriodRed, drops = level) {
                        val v = if (note.flow == level) 0 else level
                        CycleStore.note(context, day, note.copy(flow = v))
                        react(if (v == 0) "Quité el flujo." else "Flujo ${label.lowercase()}, anotado.", TokkipEngine.Eye.HAPPY)
                    }
                }
            }
        }

        Section("Intimidad", Icons.Rounded.Favorite, Pink)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigChoice("Con protección", Icons.Rounded.Shield, note.sex == 1, Shield, Modifier.weight(1f)) {
                val v = if (note.sex == 1) 0 else 1
                CycleStore.note(context, day, note.copy(sex = v))
                if (v == 0) react("Quité la relación de este día.", TokkipEngine.Eye.OPEN) else react("Anotado, con protección. ¡Bien cuidado! 💜", TokkipEngine.Eye.WINK, "love")
            }
            BigChoice("Sin protección", Icons.Rounded.Favorite, note.sex == 2, Pink, Modifier.weight(1f)) {
                val v = if (note.sex == 2) 0 else 2
                CycleStore.note(context, day, note.copy(sex = v))
                if (v == 0) react("Quité la relación de este día.", TokkipEngine.Eye.OPEN) else react("Anotado. Si fue en tus días fértiles, te digo qué sigue en «Lo que noto».", TokkipEngine.Eye.HEART, "love")
            }
        }

        Section("Pruebas", Icons.Rounded.Science, FertileTeal)
        TestRow("Ovulación", note.ovulationTest) { v ->
            CycleStore.note(context, day, note.copy(ovulationTest = v))
            when (v) {
                2 -> react("¡Ovulación a la vista! Ya ajusté tus fechas.", TokkipEngine.Eye.STAR, "proud")
                1 -> react("Prueba de ovulación negativa, anotada.")
                else -> react("Quité la prueba.", TokkipEngine.Eye.OPEN)
            }
        }
        Spacer(Modifier.height(8.dp))
        TestRow("Embarazo", note.pregnancyTest) { v ->
            CycleStore.note(context, day, note.copy(pregnancyTest = v))
            when (v) {
                2 -> react("¡Positiva! Confírmala con tu médico. Aquí estoy contigo.", TokkipEngine.Eye.SPARKLE, "proud")
                1 -> react("Prueba de embarazo negativa, anotada.")
                else -> react("Quité la prueba.", TokkipEngine.Eye.OPEN)
            }
        }

        Section("Moco cervical", Icons.Rounded.Opacity, Mucus)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Seco" to 1, "Pegajoso" to 2, "Cremoso" to 3, "Clara de huevo" to 4).forEach { (label, level) ->
                CycleChip(label, note.mucus == level, Mucus) {
                    val v = if (note.mucus == level) 0 else level
                    CycleStore.note(context, day, note.copy(mucus = v))
                    react(
                        when (v) {
                            4 -> "Clara de huevo: tus días más fértiles. ¡Anotado!"
                            0 -> "Quité el moco de este día."
                            else -> "${label}, anotado."
                        },
                        if (v == 4) TokkipEngine.Eye.STAR else TokkipEngine.Eye.HAPPY,
                    )
                }
            }
        }
        Label("Clara de huevo: elástico y transparente, suele salir en tus días más fértiles.", Ink.Dim2, 12.sp)

        Section("Temperatura basal", Icons.Rounded.Thermostat, Color(0xFFFFA45C))
        var temp by remember(day) { mutableStateOf(if (note.temp > 0) "%.2f".format(java.util.Locale.US, note.temp) else "") }
        LaunchedEffect(temp) {
            delay(900)
            val t = temp.replace(',', '.').toDoubleOrNull()
            when {
                temp.isEmpty() && note.temp > 0 -> { CycleStore.note(context, day, note.copy(temp = 0.0)); react("Quité la temperatura.", TokkipEngine.Eye.OPEN) }
                t != null && t in 35.0..38.5 && t != note.temp -> { CycleStore.note(context, day, note.copy(temp = t)); react("$temp °C anotados. Con varios días detecto tu ovulación.") }
            }
        }
        OutlinedTextField(
            value = temp,
            onValueChange = { v -> temp = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5) },
            placeholder = { Text("36.45 °C") }, singleLine = true,
            supportingText = { Text("Al despertar, antes de levantarte.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = Squircle(16.dp), modifier = Modifier.fillMaxWidth(),
        )

        Section("Cómo te sentiste", Icons.Rounded.SentimentSatisfied, Ink.VioletSoft)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in CycleStore.SYMPTOMS) {
                val on = s in note.symptoms
                CycleChip(s, on, Ink.VioletSoft) {
                    CycleStore.note(context, day, note.copy(symptoms = if (on) note.symptoms - s else note.symptoms + s))
                    when {
                        on -> react("Quité «${s.lowercase()}».", TokkipEngine.Eye.OPEN)
                        s == "Más energía" || s == "Más deseo" -> react("¡Qué bien! Anotado.", TokkipEngine.Eye.STAR)
                        else -> react("Anotado. Ojalá te sientas mejor pronto 💜", TokkipEngine.Eye.TEARY)
                    }
                }
            }
        }

        Section("Nota", Icons.Rounded.EditNote, Ink.Dim)
        var text by remember(day) { mutableStateOf(note.note) }
        LaunchedEffect(text) {
            delay(1200)
            if (text != note.note) { CycleStore.note(context, day, note.copy(note = text)); react("Nota guardada.") }
        }
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(300) },
            placeholder = { Text("Algo que quieras recordar de este día") }, minLines = 2,
            shape = Squircle(16.dp), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Listo", Modifier.fillMaxWidth(), Ink.VioletSoft, Ink.OnViolet) {
            Haptics.success(context)
            close()
        }
    }
}

/** A section title with its icon and colour. */
@Composable
private fun Section(title: String, icon: ImageVector, tint: Color) {
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
    Spacer(Modifier.height(10.dp))
}

/** A chip that fills with its colour and gives a little bounce when chosen. [drops] draws that many drops (flow). */
@Composable
private fun CycleChip(label: String, on: Boolean, tint: Color, drops: Int = 0, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) tint else Ink.Card, label = "chip")
    val scale by animateFloatAsState(if (on) 1.06f else 1f, spring(dampingRatio = 0.35f, stiffness = 500f), label = "bounce")
    Row(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(drops) {
            Icon(Icons.Rounded.WaterDrop, null, tint = if (on) Color.White else tint, modifier = Modifier.size(14.dp))
        }
        if (drops > 0) Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else Ink.Text)
    }
}

/** A big tile with an icon: for the choices that matter most. */
@Composable
private fun BigChoice(label: String, icon: ImageVector, on: Boolean, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) tint else Ink.Card, label = "big")
    val scale by animateFloatAsState(if (on) 1.04f else 1f, spring(dampingRatio = 0.35f, stiffness = 500f), label = "bigBounce")
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(Squircle(22.dp)).background(bg)
            .clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (on) Color.White else tint, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else Ink.Text, textAlign = TextAlign.Center)
    }
}

/** A test: tap Negativa or Positiva (tap again to clear). */
@Composable
private fun TestRow(name: String, value: Int, set: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(92.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CycleChip("Negativa", value == 1, Color(0xFF8A94A6)) { set(if (value == 1) 0 else 1) }
            CycleChip("Positiva", value == 2, FertileTeal) { set(if (value == 2) 0 else 2) }
        }
    }
}

/** Ajustes: turning "Ciclo" on or off, and wiping what's marked. */
@Composable
fun CycleSettingsRow() {
    val context = LocalContext.current
    val d by CycleStore.state.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    ToggleRow(
        "Ciclo menstrual y fertilidad",
        "Registra tu periodo y ve tus días fértiles estimados, en Bienestar. Cifrado y solo en este teléfono.",
        d.enabled,
    ) { CycleStore.setEnabled(context, it) }
    if (d.enabled && d.period.isNotEmpty()) {
        TextButton(onClick = { confirm = !confirm }) { Text(if (confirm) "¿Seguro? Toca otra vez para borrar" else "Borrar lo registrado en Ciclo", color = Ink.Red) }
        if (confirm) TextButton(onClick = { CycleStore.forget(context); confirm = false }) { Text("Sí, borrar todo", color = Ink.Red, fontWeight = FontWeight.Bold) }
    }
}
