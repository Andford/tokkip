package mx.loperstack.tokkip.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.loperstack.tokkip.assistant.Brain
import mx.loperstack.tokkip.assistant.Caller
import mx.loperstack.tokkip.assistant.Line
import mx.loperstack.tokkip.assistant.Listen
import mx.loperstack.tokkip.assistant.Memory
import mx.loperstack.tokkip.assistant.Models
import mx.loperstack.tokkip.assistant.Persona
import mx.loperstack.tokkip.assistant.Sentences
import mx.loperstack.tokkip.assistant.Voice
import mx.loperstack.tokkip.assistant.Web
import mx.loperstack.tokkip.assistant.WatchTalk
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Squircle
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.data.Store

/**
 * Platicar: Tokkip's assistant, all on the phone. Company when you need it,
 * help to bring systems up, and plain safe advice when things go wrong — with
 * no internet. The model wakes when you open this and sleeps when you leave.
 */
@Composable
fun TalkScreen(color: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val brain by Brain.state.collectAsState()
    val speaking by Voice.speaking.collectAsState()
    var model by remember { mutableStateOf(Models.installed(context)) }
    var progress by remember { mutableStateOf(Models.progress(context)) }
    var importing by remember { mutableStateOf<Float?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val lines = remember { mutableStateListOf<Line>().apply { addAll(Memory.load(context)) } }
    var answering by remember { mutableStateOf<String?>(null) }
    // What Tokkip did in the app while answering (an alarm, a task…): shown on top of her answer.
    val acts = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) { Brain.done.collect { acts += it } }
    var thinking by remember { mutableStateOf<Job?>(null) }
    var crisis by remember { mutableStateOf(lines.lastOrNull { it.fromYou }?.let { Persona.isCrisis(it.text) } ?: false) }
    var jump by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    val name = Store.state.collectAsState().value.name

    // The download, while it runs: checked every second, and put in place when done.
    LaunchedEffect(progress is Models.Progress.Running) {
        while (progress is Models.Progress.Running) {
            delay(1000)
            progress = Models.progress(context)
            if (progress !is Models.Progress.Running) model = Models.installed(context)
        }
    }
    // Wake the model when there is one; let it (and the voice) go when you leave.
    LaunchedEffect(model) {
        val m = model ?: return@LaunchedEffect
        Brain.load(context, m, name, lines.toList())
    }
    DisposableEffect(Unit) {
        WatchTalk.chatOpen = true
        onDispose {
            WatchTalk.chatOpen = false
            Brain.stop()
            Voice.release()
            CoroutineScope(Dispatchers.IO).launch { Brain.unload() }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "modelo.litertlm"
        importing = 0f
        scope.launch {
            Models.import(context, uri, fileName) { importing = it }
                .onSuccess { model = it; problem = null }
                .onFailure { problem = it.message }
            importing = null
        }
    }

    // Talking: tap the mic for one question, or the phone for a whole conversation.
    var call by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    var misses by remember { mutableIntStateOf(0) }
    val view = LocalView.current
    lateinit var listen: Listen

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || brain !is Brain.State.Ready || answering != null) return
        Voice.stop()
        lines += Line(true, t)
        if (Persona.isCrisis(t)) crisis = true
        answering = ""
        acts.clear()
        val cut = Sentences()
        thinking = scope.launch {
            val out = StringBuilder()
            runCatching {
                Brain.ask(t).collect { piece ->
                    out.append(piece)
                    val shown = Persona.clean(out.toString())
                    answering = shown
                    for (s in cut.feed(shown, done = false)) Voice.say(context, s)
                }
            }.onFailure { if (out.isEmpty()) out.append("Uy, me trabé pensando. ¿Me lo repites?") }
            val final = Persona.clean(out.toString())
            for (s in cut.feed(final, done = true)) Voice.say(context, s)
            val did = acts.joinToString("\n")
            lines += Line(false, if (did.isEmpty()) final.trim() else "$did\n\n${final.trim()}".trim())
            acts.clear()
            answering = null
            jump++
            Memory.save(context, lines.toList())
        }
    }

    // The system's dictation window: for phones where the in-app recognizer won't start.
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        listening = false
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }
        if (said != null) { misses = 0; send(said) } else misses++
    }
    fun dictateNow() {
        listening = true
        runCatching { dictate.launch(Listen.intent("Platícame…")) }.onFailure {
            listening = false
            call = false
            Toast.makeText(context, "Este teléfono no tiene dictado por voz", Toast.LENGTH_LONG).show()
        }
    }
    listen = remember {
        Listen(context, onPartial = { heard = it }, onDone = { said, broken, why ->
            listening = false
            heard = ""
            when {
                said != null -> { misses = 0; send(said) }
                // In a call the dictation window would get in the way: hang up and say why.
                broken && call -> {
                    call = false
                    Toast.makeText(context, "El micrófono no arrancó ($why). Prueba otra vez en un momento.", Toast.LENGTH_LONG).show()
                }
                broken -> {
                    Toast.makeText(context, "El micrófono de la app no arrancó ($why). Uso el dictado del teléfono.", Toast.LENGTH_LONG).show()
                    dictateNow()
                }
                else -> {
                    misses++
                    if (!call && why != null) Toast.makeText(context, "No te oí: $why", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
    fun hear() {
        if (brain !is Brain.State.Ready || answering != null) return
        Voice.stop()
        heard = ""
        if (Listen.broken && !call) return dictateNow()
        listening = true
        listen.start()
    }
    // A tool needs a permission (calendar, contacts, calls): ask right away.
    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { got ->
        if (got.values.all { it }) Toast.makeText(context, "Listo. Pídeselo otra vez a Tokkip.", Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(Unit) { Brain.need.collect { runCatching { askPerms.launch(it.toTypedArray()) } } }
    // A call is going out: stop listening and talking.
    val pendingCall by Caller.pending.collectAsState()
    LaunchedEffect(Unit) {
        Caller.placed.collect {
            call = false
            listen.stop()
            listening = false
        }
    }
    // She opened Spotify or the browser: no more listening, or she'd hear the music.
    LaunchedEffect(Unit) {
        Brain.left.collect {
            call = false
            listen.stop()
            listening = false
        }
    }
    // In a call: when Tokkip is done talking, listen again; after a long silence, hang up.
    LaunchedEffect(call, answering == null, speaking, listening, misses) {
        if (!call) return@LaunchedEffect
        if (misses >= 4) { call = false; return@LaunchedEffect }
        if (answering == null && !speaking && !listening) {
            delay(500)
            hear()
        }
    }
    DisposableEffect(call) {
        view.keepScreenOn = call
        onDispose { view.keepScreenOn = false; listen.stop() }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Platicar", Modifier.weight(1f), size = 34.sp)
            if (model != null) {
                val voiceOn = Voice.enabled(context)
                CircleButton(if (voiceOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, if (voiceOn) "Silenciar a Tokkip" else "Que Tokkip hable") {
                    Voice.setEnabled(context, !voiceOn)
                    jump++
                }
                Spacer(Modifier.size(8.dp))
                CircleButton(Icons.Rounded.Tune, "Ajustes del asistente") { settings = !settings }
            }
        }

        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Tokkip(
                Modifier.size(170.dp, 140.dp), color = color, jump = jump,
                mood = when {
                    answering == "" || brain is Brain.State.Loading -> Mood.FOCUS
                    speaking || answering != null -> Mood.HAPPY
                    model == null -> Mood.SLEEPY
                    else -> Mood.IDLE
                },
            )
        }

        when {
            settings && model != null -> AssistantSettings(onForget = {
                Memory.forget(context)
                lines.clear()
                crisis = false
                scope.launch { Brain.restart(name, emptyList()) }
            }, onDeleteModel = {
                scope.launch {
                    Brain.unload()
                    Models.delete(context)
                    model = null
                    settings = false
                }
            })
            model == null -> Setup(
                progress = progress, importing = importing, problem = problem,
                download = { wifi -> Models.download(context, wifiOnly = wifi); progress = Models.progress(context) },
                cancel = { Models.cancel(context); progress = Models.Progress.None },
                pick = { pick.launch(arrayOf("*/*")) },
            )
            else -> {
                if (crisis) CrisisCard { crisis = false }
                pendingCall?.let { p -> CallingCard(p, cancel = { Caller.cancel() }, now = { Caller.now(context) }) }
                Brain.toolsProblem?.takeIf { brain is Brain.State.Ready }?.let { Hint("No pude cargar mis acciones (alarmas, llamadas…): $it") }
                if (call) {
                    CallPanel(
                        state = when {
                            listening -> if (heard.isBlank()) "Te escucho…" else heard
                            answering == "" -> "Pensando…"
                            speaking || answering != null -> "Hablando… (toca para interrumpir)"
                            else -> "…"
                        },
                        last = lines.lastOrNull { !it.fromYou }?.text,
                        modifier = Modifier.weight(1f),
                        interrupt = { Brain.stop(); Voice.stop(); thinking?.cancel() },
                        hangUp = { call = false; listen.stop(); listening = false; Brain.stop(); Voice.stop() },
                    )
                } else {
                    Chat(lines, answering, acts, brain, Modifier.weight(1f))
                    Composer(
                        enabled = brain is Brain.State.Ready,
                        busy = answering != null,
                        listening = listening,
                        heard = heard,
                        onSend = ::send,
                        onStop = { Brain.stop(); Voice.stop(); thinking?.cancel() },
                        onMic = { if (listening) listen.finish() else hear() },
                        onCall = { misses = 0; call = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun Chat(lines: List<Line>, answering: String?, acts: List<String>, brain: Brain.State, modifier: Modifier) {
    val list = rememberLazyListState()
    val all = lines.size + (if (answering != null) 1 else 0)
    LaunchedEffect(all, answering?.length) { if (all > 0) list.animateScrollToItem(all - 1) }
    LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lines.isEmpty() && answering == null) item {
            Hint(
                when (brain) {
                    is Brain.State.Ready -> "Aquí estoy. Platícame cómo estás, pídeme ayuda para levantar un sistema, o pregúntame qué hacer en una emergencia. Todo se queda en tu teléfono."
                    is Brain.State.Failed -> "No pude despertar: ${brain.why}"
                    else -> "Despertando a Tokkip… (tarda unos segundos)"
                },
            )
        }
        items(lines) { Bubble(it.text, it.fromYou) }
        if (answering != null) item {
            val did = acts.joinToString("\n")
            Bubble(if (did.isEmpty()) answering.ifEmpty { "…" } else "$did\n\n${answering.ifEmpty { "…" }}", false)
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text, fontSize = 16.sp, lineHeight = 22.sp, color = if (mine) Ink.OnViolet else Ink.Text,
            modifier = Modifier.widthIn(max = 320.dp).clip(Squircle(20.dp))
                .background(if (mine) Ink.VioletSoft else Ink.Card).padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** "Llamando a Mamá en 5…": time to cancel if she heard wrong. */
@Composable
private fun CallingCard(p: Caller.Pending, cancel: () -> Unit, now: () -> Unit) {
    var left by remember(p) { mutableIntStateOf(((p.at - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)) }
    LaunchedEffect(p) {
        while (left > 0) {
            delay(1000)
            left = ((p.at - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(Squircle(20.dp)).background(Ink.Card).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📞 Llamando a ${p.name} en $left…", fontSize = 16.sp, color = Ink.Text, modifier = Modifier.weight(1f))
        TextButton(onClick = cancel) { Text("Cancelar") }
        TextButton(onClick = now) { Text("Ya") }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 15.sp, color = Ink.Dim, modifier = Modifier.fillMaxWidth().clip(Squircle(20.dp)).background(Ink.Card).padding(16.dp))
}

/** Type, tap the mic for one question, or call him for a whole conversation. */
@Composable
private fun Composer(
    enabled: Boolean, busy: Boolean, listening: Boolean, heard: String,
    onSend: (String) -> Unit, onStop: () -> Unit, onMic: () -> Unit, onCall: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var micAllowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var then by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        micAllowed = ok
        if (ok) then?.invoke()
        then = null
    }
    fun withMic(action: () -> Unit) {
        if (micAllowed) action() else { then = action; askMic.launch(Manifest.permission.RECORD_AUDIO) }
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = if (listening) heard else text, onValueChange = { text = it.take(2000) }, enabled = enabled && !listening,
            placeholder = { Text(if (listening) "Te escucho…" else "Escríbele a Tokkip") },
            shape = Squircle(22.dp), modifier = Modifier.weight(1f), maxLines = 5,
        )
        Spacer(Modifier.size(8.dp))
        when {
            busy -> CircleButton(Icons.Rounded.Stop, "Detener", size = 52.dp, container = Ink.VioletSoft, tint = Ink.OnViolet, onClick = onStop)
            text.isNotBlank() && !listening -> CircleButton(Icons.Rounded.Send, "Enviar", size = 52.dp, container = Ink.VioletSoft, tint = Ink.OnViolet) {
                onSend(text); text = ""
            }
            else -> {
                CircleButton(
                    Icons.Rounded.Mic, if (listening) "Terminar de hablar" else "Hablarle",
                    size = 52.dp, container = if (listening) Ink.Violet else Ink.CardHigh, tint = if (listening) Ink.OnViolet else Ink.Text,
                ) { if (enabled) withMic(onMic) }
                Spacer(Modifier.size(8.dp))
                CircleButton(Icons.Rounded.Call, "Conversar con Tokkip", size = 52.dp, container = Ink.VioletSoft, tint = Ink.OnViolet) {
                    if (enabled) withMic(onCall)
                }
            }
        }
    }
}

/** A conversation like a call: Tokkip big, what's happening, and one button to hang up. */
@Composable
private fun CallPanel(state: String, last: String?, modifier: Modifier, interrupt: () -> Unit, hangUp: () -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(state, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink.Text, modifier = Modifier.clip(Squircle(16.dp)).pointerInput(Unit) {
            awaitEachGesture { awaitFirstDown(); if (waitForUpOrCancellation() != null) interrupt() }
        }.padding(8.dp))
        if (last != null) {
            Spacer(Modifier.height(12.dp))
            Text(last.takeLast(280), fontSize = 15.sp, color = Ink.Dim, lineHeight = 21.sp, maxLines = 6)
        }
        Spacer(Modifier.height(28.dp))
        ExpressiveButton("Colgar", icon = Icons.Rounded.CallEnd, container = Ink.Red, content = Color.White, onClick = hangUp)
    }
}

/** When someone may be in danger: real people to call, whatever the model says. */
@Composable
private fun CrisisCard(onClose: () -> Unit) {
    val context = LocalContext.current
    fun dial(number: String) = runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Squircle(24.dp)).background(Ink.VioletDeep).padding(16.dp)) {
        Text("No estás solo", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Ink.OnVioletDeep)
        Spacer(Modifier.height(4.dp))
        Text(
            "Aquí sigo contigo. Y hay personas listas para escucharte ahora mismo, gratis y a cualquier hora.",
            fontSize = 14.sp, color = Ink.Text,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveButton("Línea de la Vida", icon = Icons.Rounded.Call) { dial(Persona.LIFE_LINE) }
            ExpressiveButton("911", icon = Icons.Rounded.Call, container = Ink.Red, content = Color.White) { dial(Persona.EMERGENCY) }
        }
        Spacer(Modifier.height(6.dp))
        Text("Ocultar", fontSize = 13.sp, color = Ink.Dim, modifier = Modifier.clip(Squircle(8.dp)).padding(6.dp).pointerInput(Unit) {
            awaitEachGesture { awaitFirstDown(); if (waitForUpOrCancellation() != null) onClose() }
        })
    }
    Spacer(Modifier.height(8.dp))
}

/** No model yet: download it once, or copy it from a file you have. */
@Composable
private fun Setup(
    progress: Models.Progress, importing: Float?, problem: String?,
    download: (wifiOnly: Boolean) -> Unit, cancel: () -> Unit, pick: () -> Unit,
) {
    val context = LocalContext.current
    val spec = Models.GEMMA_E2B
    val free = remember { Models.freeBytes(context) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SmoothCard {
            Column(Modifier.padding(18.dp)) {
                Text("Despierta el cerebro de Tokkip", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tokkip piensa con ${spec.name}, un modelo que vive en tu teléfono: funciona sin internet y nada de lo que platiquen sale de aquí. " +
                        "Se descarga una sola vez (${gb(spec.bytes)}). ${spec.note}",
                    fontSize = 14.sp, color = Ink.Dim, lineHeight = 20.sp,
                )
                Spacer(Modifier.height(14.dp))
                when {
                    importing != null -> {
                        Text("Copiando el modelo… ${(importing * 100).toInt()} %", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        WavyProgress(importing, Modifier.fillMaxWidth())
                    }
                    progress is Models.Progress.Running -> {
                        val f = if (progress.total > 0) progress.done.toFloat() / progress.total else 0f
                        Text(
                            if (progress.waiting) "Esperando Wi-Fi… ${gb(progress.done)} de ${gb(progress.total)}"
                            else "Descargando… ${gb(progress.done)} de ${gb(progress.total)}",
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        WavyProgress(f, Modifier.fillMaxWidth(), moving = !progress.waiting)
                        Spacer(Modifier.height(10.dp))
                        ExpressiveButton("Cancelar", container = Ink.CardHigh, content = Ink.Text, onClick = cancel)
                    }
                    else -> {
                        if (free < spec.bytes + 500_000_000L) {
                            Text("Te quedan ${gb(free)} libres; hacen falta unos ${gb(spec.bytes + 500_000_000L)}.", color = Ink.Amber, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                        }
                        if (progress is Models.Progress.Failed) {
                            Text("La descarga no terminó. Inténtalo de nuevo.", color = Ink.Amber, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                        }
                        problem?.let { Text(it, color = Ink.Amber, fontSize = 14.sp); Spacer(Modifier.height(8.dp)) }
                        ExpressiveButton("Descargar con Wi-Fi", Modifier.fillMaxWidth(), icon = Icons.Rounded.Download) { download(true) }
                        Spacer(Modifier.height(8.dp))
                        ExpressiveButton("Descargar con datos también", Modifier.fillMaxWidth(), container = Ink.CardHigh, content = Ink.Text) { download(false) }
                        Spacer(Modifier.height(8.dp))
                        ExpressiveButton("Ya tengo el archivo (.litertlm)", Modifier.fillMaxWidth(), icon = Icons.Rounded.FolderOpen, container = Ink.CardHigh, content = Ink.Text, onClick = pick)
                    }
                }
            }
        }
    }
}

/** Voice, pitch, and forgetting. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantSettings(onForget: () -> Unit, onDeleteModel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(Voice.chosen(context)) }
    var voiceOn by remember { mutableStateOf(Voice.enabled(context)) }
    var pitch by remember { mutableStateOf(Voice.pitch(context)) }
    var pace by remember { mutableStateOf(Voice.pace(context)) }
    var webOk by remember { mutableStateOf(Web.allowed(context)) }
    var downloading by remember { mutableStateOf<Float?>(null) }
    var voiceProblem by remember { mutableStateOf<String?>(null) }
    var installedTick by remember { mutableIntStateOf(0) }
    val importVoice = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        downloading = 0.5f
        scope.launch {
            Voice.importOwn(context, uri)
                .onSuccess { voiceProblem = null; chosen = Voice.OWN; pitch = 1f; Voice.say(context, "¡Hola! Esta es mi voz nueva.") }
                .onFailure { voiceProblem = "No se pudo importar la voz: ${it.message}" }
            downloading = null
            installedTick++
        }
    }
    val actionPerms = arrayOf(
        Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE,
    )
    fun allowed() = actionPerms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var actionsOk by remember { mutableStateOf(allowed()) }
    val askActions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { actionsOk = allowed() }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SectionTitle("Acciones")
        Text(
            if (actionsOk) "Tokkip puede poner recordatorios en tu calendario y llamar a tus contactos."
            else "Para poner recordatorios en tu calendario y llamar a tus contactos, Tokkip necesita permiso.",
            fontSize = 14.sp, color = Ink.Dim,
        )
        if (!actionsOk) {
            Spacer(Modifier.height(8.dp))
            ExpressiveButton("Permitir acciones", Modifier.fillMaxWidth(), icon = Icons.Rounded.Call) { askActions.launch(actionPerms) }
        }
        Spacer(Modifier.height(16.dp))
        SectionTitle("Voz")
        ToggleRow("Que Tokkip hable", "Lee sus respuestas en voz alta, sin internet", voiceOn) { voiceOn = it; Voice.setEnabled(context, it) }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (v in remember(installedTick) { Voice.voices(context) }) Pill("${v.name} · ${v.note}", v == chosen) { chosen = v; Voice.choose(context, v) }
        }
        Spacer(Modifier.height(10.dp))
        val here = remember(chosen, installedTick) { Voice.installed(context, chosen) }
        when {
            downloading != null -> {
                Text("Bajando la voz… ${((downloading ?: 0f) * 100).toInt()} %", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                WavyProgress(downloading ?: 0f, Modifier.fillMaxWidth())
            }
            !here -> {
                voiceProblem?.let { Text(it, color = Ink.Amber, fontSize = 14.sp) }
                ExpressiveButton("Descargar esta voz (unos 65 MB)", Modifier.fillMaxWidth(), icon = Icons.Rounded.Download) {
                    downloading = 0f
                    val v = chosen
                    scope.launch {
                        Voice.download(context, v) { downloading = it }
                            .onSuccess { voiceProblem = null; Voice.say(context, "¡Hola! Así sueno yo.") }
                            .onFailure { voiceProblem = "No se pudo bajar la voz: ${it.message}" }
                        downloading = null
                        installedTick++
                    }
                }
            }
            else -> {
                Text("Tono de Tokkip", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                PillRow {
                    for ((label, p) in listOf("Natural" to 1.0f, "Tokkip" to 1.12f, "Más agudo" to 1.25f)) {
                        Pill(label, kotlin.math.abs(pitch - p) < 0.01f) {
                            pitch = p
                            Voice.setPitch(context, p)
                            Voice.stop()
                            Voice.say(context, "Así sueno ahora.")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Ritmo", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                PillRow {
                    for ((label, p) in listOf("Tranquilo" to 0.9f, "Normal" to 1.0f, "Ágil" to 1.1f)) {
                        Pill(label, kotlin.math.abs(pace - p) < 0.01f) {
                            pace = p
                            Voice.setPace(context, p)
                            Voice.stop()
                            Voice.say(context, "Así hablo ahora, a este ritmo.")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ExpressiveButton("Importar voz propia (.zip)", Modifier.fillMaxWidth(), icon = Icons.Rounded.FolderOpen, container = Ink.CardHigh, content = Ink.Text) {
            importVoice.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        }
        SectionTitle("Internet")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Permitir buscar en internet", fontWeight = FontWeight.Bold)
                Text(
                    "Tokkip lee Wikipedia cuando le preguntas de un tema y te lo platica. Apagado, no se conecta a nada.",
                    fontSize = 14.sp, color = Ink.Dim,
                )
            }
            Switch(checked = webOk, onCheckedChange = { webOk = it; Web.setAllowed(context, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet))
        }
        SectionTitle("Memoria")
        Text("Lo que platican se guarda cifrado solo en este teléfono.", fontSize = 14.sp, color = Ink.Dim)
        Spacer(Modifier.height(8.dp))
        ExpressiveButton("Olvidar todas las pláticas", Modifier.fillMaxWidth(), container = Ink.CardHigh, content = Ink.Text, onClick = onForget)
        SectionTitle("Cerebro")
        val m = Models.installed(context)
        Text("${m?.name ?: "—"} · ${gb(m?.length() ?: 0)}", fontSize = 14.sp, color = Ink.Dim)
        Spacer(Modifier.height(8.dp))
        ExpressiveButton("Borrar el modelo del teléfono", Modifier.fillMaxWidth(), container = Ink.CardHigh, content = Ink.Red, onClick = onDeleteModel)
        Spacer(Modifier.height(24.dp))
    }
}

private fun gb(bytes: Long): String = if (bytes >= 1_000_000_000L) "%.1f GB".format(bytes / 1e9) else "%d MB".format(bytes / 1_000_000L)
