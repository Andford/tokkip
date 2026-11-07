package mx.loperstack.tokkip.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import mx.loperstack.tokkip.data.ALARM_LIMIT
import mx.loperstack.tokkip.data.Agenda
import mx.loperstack.tokkip.data.CalendarWrite
import mx.loperstack.tokkip.data.CycleMath
import mx.loperstack.tokkip.data.CycleStore
import mx.loperstack.tokkip.data.Notebook
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.TaskSync
import mx.loperstack.tokkip.data.WakeAlarm
import mx.loperstack.tokkip.data.daysText
import mx.loperstack.tokkip.data.nextRing
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * What Tokkip can do in the app when you ask her: set alarms, add tasks, take
 * notes, put reminders in your calendar, tell you what's on today and call
 * someone from your contacts. Only things that add, read or call: changing or
 * deleting stays in your hands, in the app.
 *
 * Each tool is plain JSON (name, description, parameters), not reflection, so
 * the release build's shrinking can't hide them from the model. [onDone] gets
 * a line for the chat with what happened; [onNeed] gets the permissions a tool
 * was missing, so the screen can ask for them.
 */
class TokkipTools(
    private val context: Context,
    private val onDone: (String) -> Unit,
    private val onNeed: (List<String>) -> Unit,
    private val onLeave: () -> Unit = {},
) {
    private val es = Locale.forLanguageTag("es-MX")

    init {
        Store.init(context)
    }

    fun providers(): List<ToolProvider> = tools.map { tool(it) }

    /** One tool: its description for the model and what it does with the arguments. */
    private inner class Op(
        val name: String,
        val description: String,
        val params: List<Triple<String, String, Boolean>>,
        val run: (JSONObject) -> Map<String, Any?>,
    ) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String {
            val props = JSONObject()
            for ((p, d, _) in params) props.put(p, JSONObject().put("type", "string").put("description", d))
            val o = JSONObject().put("name", name).put("description", description)
            o.put("parameters", JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(params.filter { it.third }.map { it.first })))
            return o.toString()
        }

        override fun execute(paramsJsonString: String): String {
            val result = runCatching { run(runCatching { JSONObject(paramsJsonString) }.getOrDefault(JSONObject())) }
                .getOrElse { e ->
                    onDone("⚠️ No pude ($name): ${e.message ?: e.javaClass.simpleName}")
                    mapOf("ok" to false, "error" to (e.message ?: "error"))
                }
            return JSONObject(result.mapValues { (_, v) -> json(v) }).toString()
        }
    }

    private fun json(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject(v.entries.associate { (k, x) -> k.toString() to json(x) })
        is List<*> -> JSONArray(v.map { json(it) })
        else -> v
    }

    /** A string argument, whatever type the model sent it as. */
    private fun JSONObject.text(key: String): String = opt(key)?.takeIf { it != JSONObject.NULL }?.toString()?.trim().orEmpty()

    private fun fail(why: String) = mapOf("ok" to false, "error" to why)

    private fun granted(vararg perms: String): Boolean {
        val missing = perms.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) onNeed(missing)
        return missing.isEmpty()
    }

    private val tools = listOf(
        Op(
            "crear_alarma",
            "Crea una alarma que suena a una hora (para despertar o avisar). Úsala cuando pidan una alarma o despertarse.",
            listOf(
                Triple("hora", "Hora en formato 24 horas HH:MM, por ejemplo 06:30 o 18:00", true),
                Triple("dias", "Días que se repite: lunes, martes, miercoles, jueves, viernes, sabado, domingo, 'entre semana', 'fin de semana' o 'diario'. Vacío si suena una sola vez.", false),
                Triple("etiqueta", "Nombre corto de la alarma, opcional", false),
            ),
        ) { a ->
            val minute = Spoken.minuteOf(a.text("hora")) ?: return@Op fail("Hora no válida: usa HH:MM")
            val list = Store.state.value.alarms
            if (list.size >= ALARM_LIMIT) return@Op fail("Ya hay $ALARM_LIMIT alarmas, el máximo")
            val days = Spoken.daysOf(a.text("dias"))
            val alarm = WakeAlarm(id = "tk" + System.currentTimeMillis().toString(36), minute = minute, days = days, label = a.text("etiqueta").take(40))
            Store.setAlarms(list + alarm)
            val at = "%d:%02d".format(minute / 60, minute % 60)
            onDone("⏰ Alarma a las $at · ${daysText(days)}")
            mapOf("ok" to true, "alarma" to at, "repite" to daysText(days))
        },
        Op("ver_alarmas", "Lista las alarmas que hay y cuándo suena la próxima.", emptyList()) {
            val now = System.currentTimeMillis()
            val alarms = Store.state.value.alarms
            mapOf(
                "alarmas" to alarms.map { a -> mapOf("hora" to "%d:%02d".format(a.minute / 60, a.minute % 60), "repite" to daysText(a.days), "activa" to a.enabled, "nombre" to a.label) },
                "proxima" to (alarms.mapNotNull { nextRing(it, now) }.minOrNull()?.let { fmt(it) } ?: "ninguna"),
            )
        },
        Op("agregar_tarea", "Agrega un pendiente a la lista de tareas.", listOf(Triple("texto", "El pendiente, corto y claro", true))) { a ->
            val t = a.text("texto")
            if (t.isEmpty()) return@Op fail("Falta el texto del pendiente")
            val ok = TaskSync.add(context, t)
            if (ok) onDone("✅ Pendiente: ${t.take(80)}")
            mapOf("ok" to ok)
        },
        Op("ver_tareas", "Lista los pendientes que faltan por hacer.", emptyList()) {
            mapOf("pendientes" to Store.state.value.tasks.filter { !it.done }.take(30).map { it.text })
        },
        Op("tomar_nota", "Guarda una nota en el cuaderno. La primera línea es el título.", listOf(Triple("texto", "El texto de la nota", true))) { a ->
            val t = a.text("texto")
            if (t.isEmpty()) return@Op fail("Falta el texto de la nota")
            Notebook.init(context)
            val id = Notebook.capture(t)
            if (id != null) onDone("📝 Nota: ${t.lineSequence().first().take(60)}")
            mapOf("ok" to (id != null))
        },
        Op(
            "crear_recordatorio",
            "Pone un recordatorio en el calendario del teléfono, con aviso a esa hora. Úsalo para 'recuérdame…' con un día u hora.",
            listOf(
                Triple("titulo", "De qué es el recordatorio", true),
                Triple("fecha", "Fecha en formato AAAA-MM-DD", true),
                Triple("hora", "Hora en formato 24 horas HH:MM", true),
            ),
        ) { a ->
            if (!granted(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) {
                return@Op fail("Falta el permiso de calendario. Se da en el teléfono: Platicar, Ajustes, Permitir acciones. Luego pídemelo otra vez.")
            }
            val minute = Spoken.minuteOf(a.text("hora")) ?: return@Op fail("Hora no válida: usa HH:MM")
            val day = runCatching { SimpleDateFormat("yyyy-MM-dd", es).apply { isLenient = false }.parse(a.text("fecha"))!! }.getOrNull()
                ?: return@Op fail("Fecha no válida: usa AAAA-MM-DD")
            val begin = Calendar.getInstance().apply {
                time = day
                set(Calendar.HOUR_OF_DAY, minute / 60)
                set(Calendar.MINUTE, minute % 60)
            }.timeInMillis
            if (begin < System.currentTimeMillis() - 60_000L) return@Op fail("Esa fecha y hora ya pasaron")
            val cal = CalendarWrite.calendars(context).let { all -> all.firstOrNull { it.primary && it.google } ?: all.firstOrNull { it.primary } ?: all.firstOrNull() }
                ?: return@Op fail("No hay un calendario en el teléfono")
            val title = a.text("titulo").ifEmpty { "Recordatorio" }.take(120)
            val id = CalendarWrite.insert(context, cal.id, title, begin, begin + 15 * 60_000L, false, listOf(0))
            if (id != null) onDone("🔔 Recordatorio: ${title.take(60)} · ${fmt(begin)}")
            mapOf("ok" to (id != null), "cuando" to fmt(begin))
        },
        Op("agenda_de_hoy", "Dice lo que hay hoy y mañana: eventos del calendario, pendientes y la próxima alarma.", emptyList()) {
            val now = System.currentTimeMillis()
            val events = if (granted(Manifest.permission.READ_CALENDAR)) runCatching { Agenda.load(context, now) }.getOrDefault(emptyList()) else emptyList()
            mapOf(
                "ahora" to fmt(now),
                "eventos" to events.take(15).map { e -> mapOf("titulo" to e.title, "cuando" to if (e.allDay) "todo el día ${day(e.begin)}" else fmt(e.begin)) },
                "pendientes" to Store.state.value.tasks.filter { !it.done }.take(15).map { it.text },
                "proximaAlarma" to (Store.state.value.alarms.mapNotNull { nextRing(it, now) }.minOrNull()?.let { fmt(it) } ?: "ninguna"),
            )
        },
        Op(
            "reproducir",
            "Pone música, un artista, una canción, un álbum, un podcast o un video en Spotify, YouTube Music o YouTube.",
            listOf(
                Triple("que", "Lo que hay que poner, por ejemplo 'Bad Bunny', 'música lofi para estudiar' o 'Hoy no me puedo levantar'", true),
                Triple("app", "Solo si la pidieron: 'spotify', 'youtube music' o 'youtube'. Vacío si no dijeron.", false),
            ),
        ) { a ->
            val what = a.text("que")
            if (what.isEmpty()) return@Op fail("Falta qué poner")
            if (!Apps.inFront()) return@Op fail(notInFront)
            val app = Apps.play(context, what, Apps.appOf(a.text("app")))
                ?: return@Op fail("No pude abrir Spotify ni YouTube")
            onDone("🎵 $what en $app")
            onLeave()
            mapOf("ok" to true, "app" to app, "nota" to if (app == "YouTube") "Abrí los resultados en YouTube para que elijas" else "Ya está sonando")
        },
        Op(
            "buscar_en_web",
            "Abre una búsqueda de Google en el navegador. Úsala para cosas de hoy (clima, noticias, precios, horarios, resultados) o cuando pidan buscar en internet.",
            listOf(Triple("consulta", "Lo que hay que buscar", true)),
        ) { a ->
            val q = a.text("consulta")
            if (q.isEmpty()) return@Op fail("Falta qué buscar")
            if (!Apps.inFront()) return@Op fail(notInFront)
            if (!Apps.search(context, q)) return@Op fail("No hay un navegador para abrir la búsqueda")
            onDone("🔎 Busqué: $q")
            onLeave()
            mapOf("ok" to true, "nota" to "Abrí la búsqueda en el navegador")
        },
        Op(
            "consultar_wikipedia",
            "Lee en Wikipedia un resumen sobre una persona, lugar, cosa o tema, para platicarlo. No sirve para noticias, clima ni precios.",
            listOf(Triple("tema", "El tema, por ejemplo 'Torreón', 'Frida Kahlo' o 'fotosíntesis'", true)),
        ) { a ->
            val topic = a.text("tema")
            if (topic.isEmpty()) return@Op fail("Falta el tema")
            if (!Web.allowed(context)) {
                return@Op fail("Buscar en internet está apagado. Se prende en Platicar, Ajustes, Permitir buscar en internet. O puedo abrir la búsqueda en el navegador.")
            }
            val found = Web.wikipedia(topic) ?: return@Op fail("No encontré '$topic' en Wikipedia")
            onDone("📖 Wikipedia: ${found.first}")
            mapOf("ok" to true, "titulo" to found.first, "resumen" to found.second.take(1500), "fuente" to "Wikipedia")
        },
        Op(
            "estado_del_ciclo",
            "Dice cómo va el ciclo menstrual: día del ciclo, cuándo se espera el periodo, la ventana fértil y lo que Tokkip notó. Úsala si preguntan por su periodo, ciclo, días fértiles u ovulación.",
            emptyList(),
        ) {
            CycleStore.init(context)
            val d = CycleStore.state.value
            if (!d.enabled) return@Op fail("Ciclo está apagado; se prende en Ajustes, Funciones opcionales.")
            val today = CycleStore.today()
            val f = CycleMath.forecast(d.period, today, d.notes)
            if (f.lastStart == null) return@Op fail("Aún no hay periodos marcados en Ciclo.")
            fun day(x: Int?) = x?.let { fmtDay(it) } ?: "sin dato"
            mapOf(
                "diaDelCiclo" to f.day,
                "enPeriodo" to f.inPeriod,
                "proximoPeriodo" to day(f.nextStart),
                "retrasoDias" to f.late,
                "ventanaFertil" to "${day(f.fertileFrom)} al ${day(f.fertileTo)}",
                "ovulacion" to day(f.ovulation),
                "notas" to CycleMath.insights(d.period, d.notes, f, today),
                "aviso" to "Son estimaciones, no un método anticonceptivo.",
            )
        },
        Op(
            "registrar_periodo",
            "Registra en Ciclo que el periodo empezó o terminó hoy. Úsala cuando digan 'me bajó', 'empezó mi periodo' o 'ya terminó mi periodo'.",
            listOf(Triple("que", "'empezo' o 'termino'", true)),
        ) { a ->
            CycleStore.init(context)
            val d = CycleStore.state.value
            if (!d.enabled) return@Op fail("Ciclo está apagado; se prende en Ajustes, Funciones opcionales.")
            val today = CycleStore.today()
            val ended = Spoken.plain(a.text("que")).startsWith("termin")
            if (ended) CycleStore.endPeriod(context, today)
            else CycleStore.startPeriod(context, today, CycleMath.forecast(d.period, today, d.notes).periodLen)
            onDone(if (ended) "🩸 Ciclo: terminó tu periodo" else "🩸 Ciclo: empezó tu periodo")
            mapOf("ok" to true)
        },
        Op(
            "llamar",
            "Hace una llamada telefónica a un contacto o a un número. Úsala cuando pidan llamar o marcarle a alguien.",
            listOf(Triple("persona", "Nombre del contacto como lo dijeron (por ejemplo 'mamá', 'Luis Pérez') o un número", true)),
        ) { a ->
            val who = a.text("persona")
            if (who.isEmpty()) return@Op fail("Falta a quién llamar")
            val digits = who.filter { it.isDigit() || it == '+' }
            if (digits.count { it.isDigit() } >= 7 && who.none { it.isLetter() }) {
                Caller.schedule(context, digits, digits)
                onDone("📞 Llamando a $digits…")
                return@Op mapOf("ok" to true, "llamando" to digits)
            }
            if (!granted(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)) {
                return@Op fail("Faltan los permisos de contactos y llamadas. Se dan en el teléfono: Platicar, Ajustes, Permitir acciones. Luego pídemelo otra vez.")
            }
            val found = Contacts.find(context, who)
            when {
                found.isEmpty() -> fail("No encontré a '$who' en los contactos")
                found.size > 1 -> mapOf("ok" to false, "error" to "Hay varios contactos así; pregunta a cuál", "opciones" to found.take(5).map { it.first })
                else -> {
                    val (name, number) = found.first()
                    Caller.schedule(context, name, number)
                    onDone("📞 Llamando a $name…")
                    mapOf("ok" to true, "llamando" to name)
                }
            }
        },
    )

    private fun fmt(t: Long) = SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", es).format(t)

    private val notInFront = "Android solo me deja abrir apps cuando tienes el teléfono en la mano con Tokkip abierto."
    private fun fmtDay(epochDay: Int) = java.time.LocalDate.ofEpochDay(epochDay.toLong())
        .format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es))

    private fun day(t: Long) = SimpleDateFormat("EEEE d", es).format(t)
}

/** Your contacts, found by the name you say. */
object Contacts {
    /** (name, number) of the best matches for [said]: exact names first, then names that start or contain it. */
    fun find(context: Context, said: String): List<Pair<String, String>> {
        val want = Spoken.plain(said).removePrefix("mi ").trim()
        if (want.isEmpty()) return emptyList()
        val all = mutableListOf<Triple<Long, String, String>>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.IS_PRIMARY),
            null, null, ContactsContract.CommonDataKinds.Phone.IS_PRIMARY + " DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                val number = c.getString(2) ?: continue
                all += Triple(c.getLong(0), name, number)
            }
        }
        return Spoken.bestNames(want, all.map { it.second }).flatMap { n ->
            // One number per person: the primary one, else the first.
            all.filter { it.second == n }.distinctBy { it.first }.map { n to it.third }
        }.distinctBy { it.first }
    }
}
