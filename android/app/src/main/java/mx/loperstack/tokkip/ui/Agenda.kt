// Inicio → Agenda: your calendar beyond today. As a list (the next five weeks,
// day by day) or as a month with coloured dots; tap a day to see it. Tap an
// event to see it, open it in your calendar app, join its Meet, or give it a
// colour from Google Calendar's own palette (it syncs to the web and the PC).

package mx.loperstack.tokkip.ui

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FormatColorReset
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.loperstack.tokkip.data.Agenda
import mx.loperstack.tokkip.data.CalEvent
import mx.loperstack.tokkip.data.CalendarWrite
import mx.loperstack.tokkip.data.EventColor
import mx.loperstack.tokkip.data.dayKey
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Squircle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val MX = Locale("es", "MX")

private fun startOfDay(ms: Long) = Calendar.getInstance().apply {
    timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun monthStart(ms: Long) = Calendar.getInstance().apply { timeInMillis = startOfDay(ms); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

/** The day keys an event touches (an all-day or multi-day event shows on each). */
private fun daysOf(e: CalEvent): List<String> {
    val out = mutableListOf<String>()
    val c = Calendar.getInstance().apply { timeInMillis = startOfDay(e.begin) }
    val last = (e.end - 1).coerceAtLeast(e.begin)
    var guard = 0
    while (c.timeInMillis <= last && guard++ < 62) { out += dayKey(c.timeInMillis); c.add(Calendar.DAY_OF_YEAR, 1) }
    return out
}

private fun dayTitle(day: String, now: Long): String {
    val today = dayKey(now)
    val tomorrow = dayKey(now + 86_400_000)
    val ms = mx.loperstack.tokkip.data.dayStart(day)
    val long = SimpleDateFormat("EEEE d 'de' MMMM", MX).format(Date(ms)).replaceFirstChar { it.uppercase(MX) }
    return when (day) { today -> "Hoy · $long"; tomorrow -> "Mañana · $long"; else -> long }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(calendarOk: Boolean, askCalendar: () -> Unit, actions: Actions, newEvent: () -> Unit, reloadKey: Int) {
    val context = LocalContext.current
    val now = rememberNow(60_000)
    var month by remember { mutableStateOf(false) }
    var shownMonth by remember { mutableLongStateOf(monthStart(now)) }
    var selected by remember { mutableStateOf(dayKey(now)) }
    var events by remember { mutableStateOf(emptyList<CalEvent>()) }
    var open by remember { mutableStateOf<CalEvent?>(null) }
    var bump by remember { mutableIntStateOf(0) }
    // The list: from today, five weeks. The month: the shown month, with its edges.
    val range = if (month) {
        val c = Calendar.getInstance().apply { timeInMillis = shownMonth; add(Calendar.DAY_OF_YEAR, -7) }
        val s = c.timeInMillis
        c.timeInMillis = shownMonth; c.add(Calendar.MONTH, 1); c.add(Calendar.DAY_OF_YEAR, 7)
        s to c.timeInMillis
    } else startOfDay(now) to startOfDay(now) + 35L * 86_400_000
    LaunchedEffect(calendarOk, range, reloadKey, bump, now / 300_000) {
        if (calendarOk) events = withContext(Dispatchers.IO) { Agenda.load(context, range.first, range.second) }
    }
    val byDay = remember(events) {
        val m = HashMap<String, MutableList<CalEvent>>()
        for (e in events) for (k in daysOf(e)) m.getOrPut(k) { mutableListOf() } += e
        m.mapValues { (_, v) -> v.sortedWith(compareBy<CalEvent> { !it.allDay }.thenBy { it.begin }) }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Agenda", Modifier.weight(1f))
                Pill("Evento", true, Icons.Rounded.Add, onClick = newEvent)
            }
            Spacer(Modifier.height(10.dp))
            PillRow(Modifier.padding(horizontal = 6.dp)) {
                Pill("Lista", !month, Icons.Rounded.ViewAgenda) { month = false }
                Pill("Mes", month, Icons.Rounded.CalendarMonth) { month = true; shownMonth = monthStart(now); selected = dayKey(now) }
            }
            Spacer(Modifier.height(12.dp))
            if (!calendarOk) {
                Panel(Modifier.fillMaxWidth()) {
                    Text("Tu agenda aquí", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Label("Tokkip lee los calendarios de este teléfono, Google Calendar incluido.", Ink.Dim)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Ver mi agenda", onClick = askCalendar)
                }
            }
        }
        if (calendarOk && month) {
            item {
                MonthGrid(shownMonth, selected, byDay, now, pick = { selected = it }) { delta ->
                    shownMonth = Calendar.getInstance().apply { timeInMillis = shownMonth; add(Calendar.MONTH, delta) }.timeInMillis
                    Haptics.tick(context)
                }
                Spacer(Modifier.height(14.dp))
                Text(dayTitle(selected, now), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 6.dp))
                Spacer(Modifier.height(6.dp))
                val list = byDay[selected].orEmpty()
                Panel(Modifier.fillMaxWidth(), padding = 8.dp) {
                    if (list.isEmpty()) Label("Nada este día.", Ink.Dim2, modifier = Modifier.padding(12.dp))
                    for (e in list) EventRow(e, false, now, actions) { open = e }
                }
            }
        }
        if (calendarOk && !month) {
            val days = byDay.keys.filter { it >= dayKey(now) }.sorted()
            if (days.isEmpty()) item { Label("Nada en las próximas cinco semanas. Disfrútalo.", Ink.Dim2, modifier = Modifier.padding(horizontal = 6.dp)) }
            items(days, key = { it }) { day ->
                Text(dayTitle(day, now), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (day == dayKey(now)) Ink.VioletSoft else Ink.Dim,
                    modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 6.dp))
                Panel(Modifier.fillMaxWidth(), padding = 8.dp) {
                    for (e in byDay[day].orEmpty()) EventRow(e, false, now, actions) { open = e }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    open?.let { e ->
        ModalBottomSheet(
            onDismissRequest = { open = null; bump++ }, containerColor = Ink.CardHigh,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) { EventSheet(e, actions) { changed -> open = null; if (changed) bump++ } }
    }
}

/** The month: weekday initials, the days with up to three dots each, today ringed, the selected filled. */
@Composable
private fun MonthGrid(month: Long, selected: String, byDay: Map<String, List<CalEvent>>, now: Long, pick: (String) -> Unit, move: (Int) -> Unit) {
    val name = SimpleDateFormat("MMMM yyyy", MX).format(Date(month)).replaceFirstChar { it.uppercase(MX) }
    val today = dayKey(now)
    Panel(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleButton(Icons.Rounded.ChevronLeft, "Mes anterior", size = 40.dp, container = Color.Transparent) { move(-1) }
            Text(name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            CircleButton(Icons.Rounded.ChevronRight, "Mes siguiente", size = 40.dp, container = Color.Transparent) { move(1) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            for (l in listOf("L", "M", "X", "J", "V", "S", "D")) Text(l, fontSize = 12.sp, color = Ink.Dim2, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
        val c = Calendar.getInstance().apply { timeInMillis = month }
        val offset = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val count = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = offset + count
        for (row in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val n = row * 7 + col - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(0.9f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..count) {
                            val key = dayKey(Calendar.getInstance().apply { timeInMillis = month; set(Calendar.DAY_OF_MONTH, n) }.timeInMillis)
                            val on = key == selected
                            val list = byDay[key].orEmpty()
                            Column(
                                Modifier.fillMaxSize().clip(Squircle(14.dp))
                                    .background(if (on) Ink.VioletSoft else Color.Transparent)
                                    .then(if (key == today && !on) Modifier.border(1.5.dp, Ink.VioletSoft, Squircle(14.dp)) else Modifier)
                                    .clickable { pick(key) },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text("$n", fontSize = 14.sp, fontWeight = if (key == today || on) FontWeight.ExtraBold else FontWeight.Medium, color = if (on) Ink.OnViolet else if (key < today) Ink.Dim2 else Ink.Text)
                                Spacer(Modifier.height(3.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    for (e in list.take(3)) Box(Modifier.size(5.dp).clip(CircleShape).background(if (on) Ink.OnViolet else e.color?.let { Color(it) } ?: Ink.Violet))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A colour palette: tap one; the first chip puts the calendar's colour back. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventColorRow(colors: List<EventColor>, selected: Int?, pick: (EventColor?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Ink.Card).clickable { pick(null) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.FormatColorReset, "El del calendario", tint = Ink.Dim, modifier = Modifier.size(18.dp)) }
        for (c in colors) Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color(c.color)).clickable { pick(c) },
            contentAlignment = Alignment.Center,
        ) { if (selected == c.color) Icon(Icons.Rounded.Check, c.name, tint = Color.White, modifier = Modifier.size(20.dp)) }
    }
}

/** An event: when, where, its colour, edit it, open it in your calendar app, join its Meet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(e: CalEvent, actions: Actions, close: (changed: Boolean) -> Unit) {
    val context = LocalContext.current
    val colors = remember(e.calendarId) { CalendarWrite.colors(context, e.calendarId) }
    var color by remember { mutableStateOf(e.color) }
    var name by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<EventColor?>(null) }
    var resetPending by remember { mutableStateOf(false) }
    var changed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editPending by remember { mutableStateOf(false) }
    fun apply(c: EventColor?) {
        if (CalendarWrite.setColor(context, e.id, c)) {
            color = c?.color; name = c?.name ?: "El del calendario"; changed = true
            Haptics.success(context); Sounds.play("pop")
        } else android.widget.Toast.makeText(context, "Ese calendario no deja cambiar el color", android.widget.Toast.LENGTH_SHORT).show()
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { if (editPending) editing = true else if (resetPending) apply(null) else pending?.let { apply(it) } }
        pending = null; resetPending = false; editPending = false
    }
    val f = SimpleDateFormat("EEEE d 'de' MMMM", MX)
    val when_ = if (e.allDay) f.format(Date(e.begin)).replaceFirstChar { it.uppercase(MX) } + " · todo el día"
    else f.format(Date(e.begin)).replaceFirstChar { it.uppercase(MX) } + " · ${hourMinute(e.begin)} – ${hourMinute(e.end)}"
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(color?.let { Color(it) } ?: Ink.Violet))
            Spacer(Modifier.width(12.dp))
            Text(e.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
        }
        Spacer(Modifier.height(6.dp))
        Label(when_, Ink.Dim, 14.sp)
        e.location?.let {
            Spacer(Modifier.height(8.dp))
            IconLabel(Icons.Rounded.Place, it, Ink.Dim)
        }
        SectionTitle("Color")
        EventColorRow(colors, color) { c ->
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) apply(c)
            else { pending = c; resetPending = c == null; ask.launch(Manifest.permission.WRITE_CALENDAR) }
        }
        name?.let { Spacer(Modifier.height(6.dp)); Label(it, Ink.Dim2, 12.sp) }
        Label("Se guarda en tu calendario: lo verás igual en Google Calendar.", Ink.Dim2, 12.sp)
        Spacer(Modifier.height(20.dp))
        ExpressiveButton("Editar: nombre, hora y avisos", icon = Icons.Rounded.Edit, modifier = Modifier.fillMaxWidth()) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) editing = true
            else { editPending = true; ask.launch(Manifest.permission.WRITE_CALENDAR) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (e.meetUrl != null) ExpressiveButton("Unirse", icon = Icons.Rounded.Videocam, container = Ink.Green, content = Ink.GreenDeep) { actions.open(e.meetUrl) }
            ExpressiveButton("Abrir en Calendar", icon = Icons.Rounded.OpenInNew, container = Ink.Card, content = Ink.Text) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.TextButton(onClick = { close(changed) }) { Text("Listo") }
    }
    if (editing) {
        ModalBottomSheet(
            onDismissRequest = { editing = false }, containerColor = Ink.CardHigh,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) { NewEventSheet(actions, editing = e) { editing = false; close(true) } }
    }
}
