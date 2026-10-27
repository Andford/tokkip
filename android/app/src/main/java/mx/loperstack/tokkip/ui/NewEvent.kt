// "Nuevo evento" (and editing one): a title, a day and a time, how long, up
// to five reminders (from "al empezar" to four weeks before, any you like), a
// colour and the calendar — saved straight into your Google calendar on this phone.

package mx.loperstack.tokkip.ui

import mx.loperstack.tokkip.character.Squircle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.loperstack.tokkip.data.CalendarWrite
import mx.loperstack.tokkip.data.MAX_REMINDER
import mx.loperstack.tokkip.data.MAX_REMINDERS
import mx.loperstack.tokkip.data.WritableCalendar
import mx.loperstack.tokkip.character.Sounds
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val MX = Locale("es", "MX")

/** The next whole half hour. */
private fun nextSlot(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    val m = get(Calendar.MINUTE)
    add(Calendar.MINUTE, if (m < 30) 30 - m else 60 - m)
}.timeInMillis

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        color = if (on) Ink.Ground else Ink.Text,
        modifier = Modifier.clip(Squircle(16.dp)).background(if (on) Ink.VioletSoft else Ink.Card)
            .border(1.dp, if (on) Ink.VioletSoft else Ink.Hairline, Squircle(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** The quick reminders (minutes before); any other with "Otro…". */
private val REMINDERS = listOf(0, 10, 30, 60, 1440, 2 * 1440, 7 * 1440)

/** "Al empezar", "10 min", "2 h", "1 día", "8 días", "1 semana"… */
fun reminderLabel(m: Int): String = when {
    m == 0 -> "Al empezar"
    m % (7 * 1440) == 0 -> (m / (7 * 1440)).let { if (it == 1) "1 semana" else "$it semanas" }
    m % 1440 == 0 -> (m / 1440).let { if (it == 1) "1 día" else "$it días" }
    m % 60 == 0 -> "${m / 60} h"
    m > 60 -> "${m / 60} h ${m % 60} min"
    else -> "$m min"
}

/** "Otro aviso": a number and a unit, up to four weeks. */
@Composable
private fun CustomReminderDialog(onDone: (Int?) -> Unit) {
    var amount by remember { mutableStateOf("8") }
    var unit by remember { mutableIntStateOf(1440) }
    val units = listOf(1 to "minutos", 60 to "horas", 1440 to "días", 7 * 1440 to "semanas")
    val n = amount.toIntOrNull()
    val total = n?.let { it * unit }
    val ok = total != null && total in 1..MAX_REMINDER
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onDone(null) }, containerColor = Ink.CardHigh,
        title = { Text("Otro aviso") },
        text = {
            Column {
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter(Char::isDigit).take(4) }, singleLine = true, shape = Squircle(16.dp),
                    modifier = Modifier.fillMaxWidth(), suffix = { Text("antes", color = Ink.Dim) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Ink.Violet, unfocusedBorderColor = Ink.Hairline, focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = Ink.Violet),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((u, label) in units) Pill(label, unit == u) { unit = u }
                }
                Spacer(Modifier.height(10.dp))
                Label(
                    when {
                        total == null || total <= 0 -> "Escribe cuánto antes."
                        total > MAX_REMINDER -> "Google Calendar avisa hasta 4 semanas antes."
                        else -> "Te avisa ${reminderLabel(total)} antes."
                    },
                    if (ok) Ink.Dim else Ink.Red, 13.sp,
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { if (ok) onDone(total) }, enabled = ok) { Text("Agregar", color = if (ok) Ink.VioletSoft else Ink.Dim2) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { onDone(null) }) { Text("Cancelar", color = Ink.Dim) } },
    )
}

/**
 * A new event, or `editing` one (its title, time, reminders and colour; a
 * repeating one keeps its time, changed in Calendar for the whole series).
 * @param onSaved told after the event is in the calendar (reload the agenda).
 */
@Composable
fun NewEventSheet(actions: Actions, editing: mx.loperstack.tokkip.data.CalEvent? = null, onSaved: () -> Unit) {
    val context = LocalContext.current
    val calendars = remember { CalendarWrite.calendars(context) }
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var begin by remember { mutableLongStateOf(editing?.begin ?: nextSlot(System.currentTimeMillis())) }
    var minutes by remember { mutableIntStateOf(editing?.let { ((it.end - it.begin) / 60_000L).toInt().coerceAtLeast(5) } ?: 60) }
    var allDay by remember { mutableStateOf(editing?.allDay ?: false) }
    var reminders by remember { mutableStateOf(editing?.let { CalendarWrite.reminders(context, it.id) } ?: listOf(10)) }
    val repeats = remember(editing?.id) { editing?.let { CalendarWrite.repeats(context, it.id) } ?: false }
    var calIndex by remember { mutableIntStateOf(editing?.let { e -> calendars.indexOfFirst { it.id == e.calendarId }.coerceAtLeast(0) } ?: 0) }
    var error by remember { mutableStateOf("") }
    var customReminder by remember { mutableStateOf(false) }
    val cal: WritableCalendar? = calendars.getOrNull(calIndex)
    // Its colour: the calendar's palette (Google's own names), or the calendar's colour.
    val palette = remember(cal?.id) { cal?.let { CalendarWrite.colors(context, it.id) } ?: emptyList() }
    var color by remember { mutableStateOf(editing?.color?.let { c -> palette.firstOrNull { it.color == c } }) }
    var colorTouched by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text(if (editing != null) "Editar evento" else "Nuevo evento", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        if (repeats) {
            Spacer(Modifier.height(4.dp))
            Label("Se repite: aquí cambias nombre, avisos y color; la hora, en Calendar.", Ink.Dim2, 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = title, onValueChange = { title = it.take(200) }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("¿Qué es?", color = Ink.Dim2) }, singleLine = true, shape = Squircle(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink.Violet, unfocusedBorderColor = Ink.Hairline,
                focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = Ink.Violet,
            ),
        )
        Spacer(Modifier.height(14.dp))
        if (!repeats) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(SimpleDateFormat("EEE d MMM", MX).format(Date(begin)).replaceFirstChar { it.uppercase(MX) }, false) {
                val c = Calendar.getInstance().apply { timeInMillis = begin }
                DatePickerDialog(context, { _, y, m, d ->
                    begin = Calendar.getInstance().apply { timeInMillis = begin; set(y, m, d) }.timeInMillis
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }
            if (!allDay) {
                Pill(SimpleDateFormat("HH:mm", MX).format(Date(begin)), false) {
                    val c = Calendar.getInstance().apply { timeInMillis = begin }
                    TimePickerDialog(context, { _, h, m ->
                        begin = Calendar.getInstance().apply { timeInMillis = begin; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }.timeInMillis
                    }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                }
            }
            Spacer(Modifier.weight(1f))
            Label("Todo el día", Ink.Dim)
            Switch(checked = allDay, onCheckedChange = { allDay = it }, colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet))
        }
        if (!allDay && !repeats) {
            Spacer(Modifier.height(14.dp))
            Label("Duración", Ink.Dim2, 12.sp, FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val lengths = listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", 90 to "1 h 30", 120 to "2 h", 180 to "3 h")
                for ((m, label) in lengths) Pill(label, minutes == m) { minutes = m }
                if (lengths.none { it.first == minutes }) Pill("${minutes / 60} h ${minutes % 60} min", true) {}
            }
        }
        Spacer(Modifier.height(14.dp))
        Label("Avisos · hasta $MAX_REMINDERS" + if (reminders.isEmpty()) " · sin aviso" else "", Ink.Dim2, 12.sp, FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        val toggle = { m: Int ->
            val on = m in reminders
            reminders = when {
                on -> reminders - m
                reminders.size >= MAX_REMINDERS -> { error = "Hasta $MAX_REMINDERS avisos por evento."; reminders }
                else -> { error = ""; (reminders + m).sortedDescending() }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The ones you chose that aren't quick ones (8 días, 3 h…) come first, so you see them.
            for (m in (reminders.filter { it !in REMINDERS } + REMINDERS).distinct()) Pill(reminderLabel(m), m in reminders) { toggle(m) }
            Pill("+ Otro…", false) {
                if (reminders.size >= MAX_REMINDERS) error = "Hasta $MAX_REMINDERS avisos por evento." else customReminder = true
            }
        }
        if (customReminder) CustomReminderDialog { m ->
            customReminder = false
            if (m != null && m !in reminders) { error = ""; reminders = (reminders + m).sortedDescending() }
        }
        Spacer(Modifier.height(14.dp))
        Label("Color" + (color?.let { " · ${it.name}" } ?: " · el del calendario"), Ink.Dim2, 12.sp, FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        EventColorRow(palette, color?.color ?: if (!colorTouched) editing?.color else null) { color = it; colorTouched = true }
        Spacer(Modifier.height(14.dp))
        if (cal != null) {
            Row(
                Modifier.fillMaxWidth().clip(Squircle(16.dp)).background(Ink.Card)
                    .clickable(enabled = calendars.size > 1 && editing == null) { calIndex = (calIndex + 1) % calendars.size; color = null }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(cal.color?.let { Color(it) } ?: Ink.Violet))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(cal.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Label(if (cal.google) "${cal.account} · Google Calendar" else cal.account, Ink.Dim2, 12.sp)
                }
                if (calendars.size > 1 && editing == null) Label("Cambiar", Ink.VioletSoft)
            }
        } else {
            Label("No hay calendarios donde escribir en este teléfono.", Ink.Red)
        }
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Label(error, Ink.Red)
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(if (editing != null) "Guardar cambios" else "Guardar en el calendario", Modifier.fillMaxWidth(), Ink.VioletSoft, Ink.OnViolet) {
            val c = cal ?: return@PrimaryButton
            if (title.isBlank()) { error = "Ponle un nombre."; return@PrimaryButton }
            if (editing != null) {
                if (!CalendarWrite.update(context, editing.id, title, begin, begin + minutes * 60_000L, allDay, timeToo = !repeats)) {
                    error = "El calendario no aceptó el cambio."
                    return@PrimaryButton
                }
                CalendarWrite.setReminders(context, editing.id, reminders)
                if (colorTouched) CalendarWrite.setColor(context, editing.id, color)
                actions.jump++
                Sounds.play("approve")
                onSaved()
                return@PrimaryButton
            }
            val id = CalendarWrite.insert(context, c.id, title, begin, begin + minutes * 60_000L, allDay, reminders, color)
            if (id == null) {
                error = "El calendario no aceptó el evento."
            } else {
                actions.jump++
                Sounds.play("approve")
                onSaved()
            }
        }
    }
}
