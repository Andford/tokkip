// Despertador, in Sueño: your alarms, when the next one rings, and a sheet to
// make or change one (time, days, name, how it wakes you). The ringing is
// wellness/WakeAlarm.kt; the screen when it rings, AlarmActivity.kt.

package mx.loperstack.tokkip.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.Squircle
import mx.loperstack.tokkip.data.ALARM_LIMIT
import mx.loperstack.tokkip.data.AppData
import mx.loperstack.tokkip.data.SNOOZE_CHOICES
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.WEEK
import mx.loperstack.tokkip.data.WEEKDAYS
import mx.loperstack.tokkip.data.WakeAlarm
import mx.loperstack.tokkip.data.daysText
import mx.loperstack.tokkip.data.hhmm
import mx.loperstack.tokkip.data.inText
import mx.loperstack.tokkip.data.nextOf
import mx.loperstack.tokkip.data.nextRing
import java.util.Calendar
import java.util.UUID
import mx.loperstack.tokkip.wellness.WakeAlarm as Ringer

/** The Despertador section: next ring, a heads-up if Android would hide it, and the alarms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsPanel(data: AppData) {
    val context = LocalContext.current
    val now = rememberNow(15_000)
    var editing by remember { mutableStateOf<WakeAlarm?>(null) }
    val next = nextOf(data.alarms, data.snooze, now)

    fun save(list: List<WakeAlarm>) {
        Store.setAlarms(list)
        Haptics.click(context)
    }

    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        next == null -> "Sin alarmas activas"
                        next.first == data.snooze?.id -> "Pospuesta · " + inText(next.second, now).replaceFirstChar { it.lowercase() }
                        else -> inText(next.second, now)
                    },
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink.Text,
                )
                Label("Tokkip te despierta con su melodía, aunque tengas el teléfono en silencio", Ink.Dim2, 12.sp)
            }
            if (data.alarms.size < ALARM_LIMIT) {
                Icon(
                    Icons.Rounded.Add, "Nueva alarma", tint = Ink.OnViolet,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Ink.VioletSoft)
                        .clickable {
                            Haptics.click(context)
                            editing = WakeAlarm(UUID.randomUUID().toString().take(8), data.sleepGoal.wakeMin, WEEKDAYS)
                        }.padding(10.dp),
                )
            }
        }

        // Android would keep it from covering the lock screen, or hide it altogether.
        val needsNotifs = !Ringer.notificationsOn(context)
        val needsFull = !Ringer.canFullScreen(context)
        if (data.alarms.any { it.enabled } && (needsNotifs || needsFull)) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().clip(Squircle(18.dp)).background(Ink.VioletDeep).padding(14.dp),
            ) {
                Text(
                    if (needsNotifs) "Activa las notificaciones de Tokkip para que la alarma se vea"
                    else "Deja que Tokkip use la pantalla completa para que la alarma aparezca con el teléfono bloqueado",
                    fontSize = 13.sp, color = Ink.OnVioletDeep,
                )
                Spacer(Modifier.height(8.dp))
                ExpressiveButton("Permitir", container = Ink.VioletSoft, content = Ink.OnViolet) {
                    runCatching {
                        context.startActivity(
                            if (needsNotifs) android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            else Ringer.fullScreenSettings(context),
                        )
                    }
                }
            }
        }

        for (a in data.alarms.sortedBy { it.minute }) {
            Spacer(Modifier.height(10.dp))
            AlarmRow(a, now, onToggle = { on -> save(data.alarms.map { if (it.id == a.id) it.copy(enabled = on) else it }) }) { editing = a }
        }
        if (data.alarms.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Label("Toca + para poner tu primera alarma. Empieza con tu hora de despertar.", Ink.Dim, 13.sp)
        }
    }

    editing?.let { a ->
        ModalBottomSheet(
            onDismissRequest = { editing = null }, containerColor = Ink.CardHigh,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AlarmSheet(
                a,
                isNew = data.alarms.none { it.id == a.id },
                onSave = { saved ->
                    editing = null
                    save(if (data.alarms.any { it.id == saved.id }) data.alarms.map { if (it.id == saved.id) saved else it } else data.alarms + saved)
                    nextRing(saved, System.currentTimeMillis())?.let {
                        android.widget.Toast.makeText(context, inText(it, System.currentTimeMillis()), android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = {
                    editing = null
                    save(data.alarms.filter { it.id != a.id })
                    if (data.snooze?.id == a.id) Store.setSnooze(null)
                },
            )
        }
    }
}

@Composable
private fun AlarmRow(a: WakeAlarm, now: Long, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Squircle(20.dp)).background(Ink.CardHigh).border(1.dp, Ink.Hairline, Squircle(20.dp))
            .clickable(onClick = onEdit).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(hhmm(a.minute), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = if (a.enabled) Ink.Text else Ink.Dim2)
            Text(
                listOf(a.label, daysText(a.days)).filter { it.isNotBlank() }.joinToString("  ·  "),
                fontSize = 13.sp, color = if (a.enabled) Ink.Dim else Ink.Dim2,
            )
            if (a.breathe && a.enabled) Label("Se apaga respirando con Tokkip", Ink.Dim2, 11.sp)
        }
        Switch(checked = a.enabled, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmSheet(start: WakeAlarm, isNew: Boolean, onSave: (WakeAlarm) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var a by remember { mutableStateOf(start.copy(enabled = true)) }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Text(if (isNew) "Nueva alarma" else "Editar alarma", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink.Text)
        Spacer(Modifier.height(14.dp))

        // The time: a big, friendly number; tap it for the clock.
        Text(
            hhmm(a.minute), fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = Ink.Text, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(Squircle(26.dp)).background(Ink.Card).clickable {
                TimePickerDialog(context, { _, h, m -> a = a.copy(minute = h * 60 + m) }, a.minute / 60, a.minute % 60, true).show()
            }.padding(vertical = 10.dp),
        )
        Spacer(Modifier.height(14.dp))

        Label("Se repite", Ink.Dim, 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for ((day, letter) in WEEK) {
                val on = day in a.days
                Text(
                    if (day == Calendar.WEDNESDAY) "Mi" else letter,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    color = if (on) Ink.OnViolet else Ink.Text,
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(if (on) Ink.VioletSoft else Ink.Card)
                        .clickable { Haptics.click(context); a = a.copy(days = if (on) a.days - day else a.days + day) }
                        .padding(top = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Label(daysText(a.days).let { if (a.days.isEmpty()) "Una vez: suena la próxima vez que sea esa hora" else it }, Ink.Dim2, 12.sp)
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = a.label, onValueChange = { a = a.copy(label = it.take(40)) }, singleLine = true,
            label = { Text("Nombre (opcional)") }, placeholder = { Text("Gym, Escuela…") },
            shape = Squircle(16.dp), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        SheetSwitch("Despertar suave", "La melodía empieza bajito y sube en un minuto", a.gentle) { a = a.copy(gentle = it) }
        SheetSwitch("Vibrar", "También vibra mientras suena", a.vibrate) { a = a.copy(vibrate = it) }
        SheetSwitch("Apagar respirando", "Para apagarla respiras tres veces con Tokkip; así no la apagas dormido", a.breathe) { a = a.copy(breathe = it) }

        Spacer(Modifier.height(8.dp))
        Label("Posponer", Ink.Dim, 13.sp)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in SNOOZE_CHOICES) {
                val on = a.snoozeMin == m
                Text(
                    "$m min", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Ink.OnViolet else Ink.Text,
                    modifier = Modifier.clip(Squircle(14.dp)).background(if (on) Ink.VioletSoft else Ink.Card)
                        .clickable { a = a.copy(snoozeMin = m) }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExpressiveButton("Probar", Modifier.weight(1f), icon = Icons.Rounded.Alarm, container = Ink.Card, content = Ink.Text) {
                Haptics.click(context)
                Ringer.preview(context, a)
            }
            ExpressiveButton("Guardar", Modifier.weight(1f), container = Ink.VioletSoft, content = Ink.OnViolet) { onSave(a) }
        }
        if (!isNew) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Eliminar alarma", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink.Red, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(Squircle(16.dp)).clickable { onDelete() }.padding(12.dp),
            )
        }
    }
}

@Composable
private fun SheetSwitch(title: String, why: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink.Text)
            Label(why, Ink.Dim2, 12.sp)
        }
        Switch(checked = on, onCheckedChange = set, colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet))
    }
}
