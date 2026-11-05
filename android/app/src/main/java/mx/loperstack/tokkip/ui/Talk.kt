package mx.loperstack.tokkip.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import mx.loperstack.tokkip.assistant.Line
import mx.loperstack.tokkip.assistant.Listen
import mx.loperstack.tokkip.assistant.Memory
import mx.loperstack.tokkip.assistant.Models
import mx.loperstack.tokkip.assistant.Persona
import mx.loperstack.tokkip.assistant.Sentences
import mx.loperstack.tokkip.assistant.Voice
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
        onDispose {
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

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || brain !is Brain.State.Ready || answering != null) return
        Voice.stop()
        lines += Line(true, t)
        if (Persona.isCrisis(t)) crisis = true
        answering = ""
        val cut = Sentences()
        thinking = scope.launch {
            val out = StringBuilder()
            runCatching {
                Brain.ask(t).collect { piece ->
                    out.append(piece)
                    answering = out.toString()
                    for (s in cut.feed(out.toString(), done = false)) Voice.say(context, s)
                }
            }.onFailure { if (out.isEmpty()) out.append("Uy, me trabé pensando. ¿Me lo repites?") }
            for (s in cut.feed(out.toString(), done = true)) Voice.say(context, s)
            lines += Line(false, out.toString().trim())
            answering = null
            jump++
            Memory.save(context, lines.toList())
        }
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
                Chat(lines, answering, brain, Modifier.weight(1f))
                Composer(
                    enabled = brain is Brain.State.Ready,
                    busy = answering != null,
                    onSend = ::send,
                    onStop = { Brain.stop(); Voice.stop(); thinking?.cancel() },
                )
            }
        }
    }
}

@Composable
private fun Chat(lines: List<Line>, answering: String?, brain: Brain.State, modifier: Modifier) {
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
        if (answering != null) item { Bubble(answering.ifEmpty { "…" }, false) }
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

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 15.sp, color = Ink.Dim, modifier = Modifier.fillMaxWidth().clip(Squircle(20.dp)).background(Ink.Card).padding(16.dp))
}

/** Type, or hold the mic and talk; while Tokkip answers, the button stops him. */
@Composable
private fun Composer(enabled: Boolean, busy: Boolean, onSend: (String) -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    val listen = remember {
        Listen(context, onPartial = { text = it }, onDone = { heard ->
            listening = false
            if (heard != null) { onSend(heard); text = "" }
        })
    }
    DisposableEffect(Unit) { onDispose { listen.stop() } }
    var micAllowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micAllowed = it }

    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(2000) }, enabled = enabled,
            placeholder = { Text(if (listening) "Te escucho…" else "Escríbele a Tokkip") },
            shape = Squircle(22.dp), modifier = Modifier.weight(1f), maxLines = 5,
        )
        Spacer(Modifier.size(8.dp))
        when {
            busy -> CircleButton(Icons.Rounded.Stop, "Detener", size = 52.dp, container = Ink.VioletSoft, tint = Ink.OnViolet, onClick = onStop)
            text.isNotBlank() && !listening -> CircleButton(Icons.Rounded.Send, "Enviar", size = 52.dp, container = Ink.VioletSoft, tint = Ink.OnViolet) {
                onSend(text); text = ""
            }
            else -> Box(
                Modifier.size(52.dp).clip(Squircle(26.dp)).background(if (listening) Ink.Violet else Ink.CardHigh)
                    .pointerInput(enabled, micAllowed) {
                        awaitEachGesture {
                            awaitFirstDown()
                            if (!micAllowed) { askMic.launch(Manifest.permission.RECORD_AUDIO); return@awaitEachGesture }
                            if (!enabled) return@awaitEachGesture
                            Voice.stop()
                            listening = true
                            text = ""
                            listen.start()
                            waitForUpOrCancellation()
                            listen.finish()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(Icons.Rounded.Mic, "Mantén presionado para hablar", tint = if (listening) Ink.OnViolet else Ink.Text)
            }
        }
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
    var downloading by remember { mutableStateOf<Float?>(null) }
    var voiceProblem by remember { mutableStateOf<String?>(null) }
    var installedTick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SectionTitle("Voz")
        ToggleRow("Que Tokkip hable", "Lee sus respuestas en voz alta, sin internet", voiceOn) { voiceOn = it; Voice.setEnabled(context, it) }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (v in Voice.VOICES) Pill("${v.name} · ${v.note}", v == chosen) { chosen = v; Voice.choose(context, v) }
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
            }
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
