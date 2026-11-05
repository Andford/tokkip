package mx.loperstack.tokkip.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import mx.loperstack.tokkip.data.ALARM_LIMIT
import mx.loperstack.tokkip.data.Agenda
import mx.loperstack.tokkip.data.CalendarWrite
import mx.loperstack.tokkip.data.Notebook
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.TaskSync
import mx.loperstack.tokkip.data.WakeAlarm
import mx.loperstack.tokkip.data.daysText
import mx.loperstack.tokkip.data.nextRing
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * What Tokkip can do in the app when you ask her: set alarms, add tasks, take
 * notes, put reminders in your calendar, and tell you what's on today. Only
 * things that add or read: changing or deleting stays in your hands, in the
 * app. Each one tells [onDone] what it did, so the chat can show it.
 */
class TokkipTools(private val context: Context, private val onDone: (String) -> Unit) : ToolSet {
    private val es = Locale.forLanguageTag("es-MX")

    init {
        Store.init(context)
    }

    @Tool(description = "Crea una alarma para despertar o avisar a una hora. Úsala cuando pidan una alarma o despertarse a cierta hora.")
    fun crearAlarma(
        @ToolParam(description = "Hora en formato 24 horas HH:MM, por ejemplo 06:30 o 18:00") hora: String,
        @ToolParam(description = "Días que se repite, separados por comas: lunes, martes, miercoles, jueves, viernes, sabado, domingo, 'entre semana', 'fin de semana' o 'diario'. Vacío si es una sola vez.") dias: String = "",
        @ToolParam(description = "Nombre corto de la alarma, opcional") etiqueta: String = "",
    ): Map<String, Any> {
        val minute = Spoken.minuteOf(hora) ?: return mapOf("ok" to false, "error" to "Hora no válida")
        val list = Store.state.value.alarms
        if (list.size >= ALARM_LIMIT) return mapOf("ok" to false, "error" to "Ya hay $ALARM_LIMIT alarmas, el máximo")
        val days = Spoken.daysOf(dias)
        val alarm = WakeAlarm(id = "tk" + System.currentTimeMillis().toString(36), minute = minute, days = days, label = etiqueta.take(40))
        Store.setAlarms(list + alarm)
        val at = "%d:%02d".format(minute / 60, minute % 60)
        onDone("⏰ Alarma a las $at · ${daysText(days)}")
        return mapOf("ok" to true, "alarma" to at, "repite" to daysText(days))
    }

    @Tool(description = "Lista las alarmas que hay y cuándo suena la próxima.")
    fun verAlarmas(): Map<String, Any> {
        val now = System.currentTimeMillis()
        val alarms = Store.state.value.alarms
        return mapOf(
            "alarmas" to alarms.map { a ->
                mapOf("hora" to "%d:%02d".format(a.minute / 60, a.minute % 60), "repite" to daysText(a.days), "activa" to a.enabled, "nombre" to a.label)
            },
            "proxima" to (alarms.mapNotNull { nextRing(it, now) }.minOrNull()?.let { fmt(it) } ?: "ninguna"),
        )
    }

    @Tool(description = "Agrega un pendiente a la lista de tareas.")
    fun agregarTarea(@ToolParam(description = "El pendiente, corto y claro") texto: String): Map<String, Any> {
        val ok = TaskSync.add(context, texto)
        if (ok) onDone("✅ Pendiente: ${texto.trim().take(80)}")
        return mapOf("ok" to ok)
    }

    @Tool(description = "Lista los pendientes que faltan por hacer.")
    fun verTareas(): Map<String, Any> =
        mapOf("pendientes" to Store.state.value.tasks.filter { !it.done }.take(30).map { it.text })

    @Tool(description = "Guarda una nota en el cuaderno. La primera línea es el título.")
    fun tomarNota(@ToolParam(description = "El texto de la nota") texto: String): Map<String, Any> {
        val id = runCatching { Notebook.init(context); Notebook.capture(texto) }.getOrNull()
        if (id != null) onDone("📝 Nota guardada: ${texto.trim().lineSequence().first().take(60)}")
        return mapOf("ok" to (id != null))
    }

    @Tool(description = "Pone un recordatorio en el calendario del teléfono, con aviso a esa hora.")
    fun crearRecordatorio(
        @ToolParam(description = "De qué es el recordatorio") titulo: String,
        @ToolParam(description = "Fecha en formato AAAA-MM-DD") fecha: String,
        @ToolParam(description = "Hora en formato 24 horas HH:MM") hora: String,
    ): Map<String, Any> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return mapOf("ok" to false, "error" to "Falta permiso de calendario: se da en la pantalla Agenda de Tokkip")
        }
        val minute = Spoken.minuteOf(hora) ?: return mapOf("ok" to false, "error" to "Hora no válida")
        val day = runCatching { SimpleDateFormat("yyyy-MM-dd", es).apply { isLenient = false }.parse(fecha.trim())!!.time }.getOrNull()
            ?: return mapOf("ok" to false, "error" to "Fecha no válida")
        val begin = day + minute * 60_000L
        if (begin < System.currentTimeMillis() - 60_000L) return mapOf("ok" to false, "error" to "Esa fecha ya pasó")
        val cal = CalendarWrite.calendars(context).let { all -> all.firstOrNull { it.primary && it.google } ?: all.firstOrNull { it.primary } ?: all.firstOrNull() }
            ?: return mapOf("ok" to false, "error" to "No hay un calendario en el teléfono")
        val id = CalendarWrite.insert(context, cal.id, titulo.take(120), begin, begin + 15 * 60_000L, false, listOf(0))
        if (id != null) onDone("🔔 Recordatorio: ${titulo.take(60)} · ${fmt(begin)}")
        return mapOf("ok" to (id != null), "cuando" to fmt(begin))
    }

    @Tool(description = "Dice lo que hay hoy y mañana: eventos del calendario, pendientes y la próxima alarma.")
    fun agendaDeHoy(): Map<String, Any> {
        val now = System.currentTimeMillis()
        val events = runCatching {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) Agenda.load(context, now) else emptyList()
        }.getOrDefault(emptyList())
        return mapOf(
            "ahora" to fmt(now),
            "eventos" to events.take(15).map { e -> mapOf("titulo" to e.title, "cuando" to if (e.allDay) "todo el día ${day(e.begin)}" else fmt(e.begin)) },
            "pendientes" to Store.state.value.tasks.filter { !it.done }.take(15).map { it.text },
            "proximaAlarma" to (Store.state.value.alarms.mapNotNull { nextRing(it, now) }.minOrNull()?.let { fmt(it) } ?: "ninguna"),
        )
    }

    private fun fmt(t: Long) = SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", es).format(t)
    private fun day(t: Long) = SimpleDateFormat("EEEE d", es).format(t)
}
