// "Ciclo": your period and fertile days, an optional screen of Bienestar
// (Ajustes → Funciones opcionales). Today's card says where you are in your
// cycle and what's next; the month shows marked days and the estimates; a day
// opens to mark it and note the flow and how you felt. Everything stays
// encrypted on this phone (data/CycleStore.kt).

package mx.loperstack.tokkip.ui

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
fun CycleScreen() {
    val context = LocalContext.current
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
        TodayCard(d.period, f, today)
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
                PrimaryButton("Terminó hoy", Modifier.weight(1f), Ink.VioletSoft, Ink.OnViolet) { CycleStore.endPeriod(context, today) }
            } else {
                PrimaryButton("Empezó mi periodo", Modifier.weight(1f), PeriodRed, Color.White) { CycleStore.startPeriod(context, today, f.periodLen) }
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
            DaySheet(opened, opened in d.period, d.notes[opened] ?: CycleDay(), future = opened > today)
        }
    }
}

@Composable
private fun TodayCard(period: Set<Int>, f: CycleMath.Forecast, today: Int) {
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
    Panel(Modifier.fillMaxWidth(), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(
                    when {
                        f.inPeriod -> PeriodRed
                        mark == CycleMath.Mark.FERTILE || mark == CycleMath.Mark.OVULATION -> FertileTeal
                        else -> Ink.VioletDeep
                    },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(f.day?.toString() ?: "–", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (f.inPeriod || mark != CycleMath.Mark.NONE) Color.White else Ink.OnVioletDeep)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(2.dp))
                Text(line, fontSize = 14.sp, color = Ink.Dim)
            }
        }
        if (fertile != null) {
            Spacer(Modifier.height(12.dp))
            Text(fertile, fontSize = 14.sp, color = FertileTeal, fontWeight = FontWeight.SemiBold)
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaySheet(day: Int, isPeriod: Boolean, note: CycleDay, future: Boolean) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        Text(nice(day).replaceFirstChar { it.uppercase() }, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(14.dp))
        if (future) {
            Text("Los días que aún no llegan no se pueden marcar.", color = Ink.Dim)
            return@Column
        }
        ToggleRow("Tuve periodo este día", "Márcalo aunque haya sido poquito", isPeriod) { CycleStore.toggle(context, day) }
        Spacer(Modifier.height(12.dp))
        Text("Flujo", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        PillRow {
            for ((i, label) in listOf("Nada", "Ligero", "Medio", "Abundante").withIndex()) {
                Pill(label, note.flow == i) { CycleStore.note(context, day, note.copy(flow = i)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Choice("Relaciones", listOf("No", "Con protección", "Sin protección"), note.sex) { CycleStore.note(context, day, note.copy(sex = it)) }
        Choice("Prueba de ovulación", listOf("No me la hice", "Negativa", "Positiva"), note.ovulationTest) { CycleStore.note(context, day, note.copy(ovulationTest = it)) }
        Choice("Prueba de embarazo", listOf("No me la hice", "Negativa", "Positiva"), note.pregnancyTest) { CycleStore.note(context, day, note.copy(pregnancyTest = it)) }
        Choice("Moco cervical", listOf("Sin notar", "Seco", "Pegajoso", "Cremoso", "Clara de huevo"), note.mucus) { CycleStore.note(context, day, note.copy(mucus = it)) }
        Label("Clara de huevo (elástico y transparente) suele aparecer en tus días más fértiles.", Ink.Dim2, 12.sp)
        Spacer(Modifier.height(14.dp))
        var temp by remember(day) { mutableStateOf(if (note.temp > 0) "%.2f".format(java.util.Locale.US, note.temp) else "") }
        OutlinedTextField(
            value = temp,
            onValueChange = { v ->
                temp = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5)
                val t = temp.replace(',', '.').toDoubleOrNull()
                if (temp.isEmpty()) CycleStore.note(context, day, note.copy(temp = 0.0))
                else if (t != null && t in 35.0..38.5) CycleStore.note(context, day, note.copy(temp = t))
            },
            label = { Text("Temperatura basal (°C)") }, placeholder = { Text("36.45") }, singleLine = true,
            supportingText = { Text("Al despertar, antes de levantarte. Con varios días, Tokkip detecta tu ovulación.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = Squircle(16.dp), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Text("Cómo te sentiste", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in CycleStore.SYMPTOMS) {
                val on = s in note.symptoms
                Pill(s, on) { CycleStore.note(context, day, note.copy(symptoms = if (on) note.symptoms - s else note.symptoms + s)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        var text by remember(day) { mutableStateOf(note.note) }
        OutlinedTextField(
            value = text,
            onValueChange = { v -> text = v.take(300); CycleStore.note(context, day, note.copy(note = text)) },
            label = { Text("Nota (opcional)") }, minLines = 2,
            shape = Squircle(16.dp), modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One choice among a few, as pills. */
@Composable
private fun Choice(title: String, options: List<String>, selected: Int, pick: (Int) -> Unit) {
    Text(title, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    PillRow {
        options.forEachIndexed { i, label -> Pill(label, selected == i) { pick(i) } }
    }
    Spacer(Modifier.height(12.dp))
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
