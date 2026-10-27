// Diario: how you feel today (five Tokkip faces) and the day's log — short
// lines with their time, like an anime's daily log — with photos. The day can
// be saved as an image card (gallery or share); the whole diary exports as a
// PDF or a .zip copy you can restore. Plus the month in colours.

package mx.loperstack.tokkip.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import mx.loperstack.tokkip.character.MShapes

import mx.loperstack.tokkip.character.Squircle
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.loperstack.tokkip.data.DayLog
import mx.loperstack.tokkip.data.DiaryExport
import mx.loperstack.tokkip.data.DiaryImages
import mx.loperstack.tokkip.data.LogNote
import mx.loperstack.tokkip.data.averageMood
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.dayKey
import mx.loperstack.tokkip.data.dayNumber
import mx.loperstack.tokkip.data.dayStart
import mx.loperstack.tokkip.data.mergeLogs
import mx.loperstack.tokkip.data.streak
import mx.loperstack.tokkip.data.withMood
import mx.loperstack.tokkip.data.withNote
import mx.loperstack.tokkip.data.withoutNote
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.wellness.MOODS
import mx.loperstack.tokkip.wellness.moodOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val MX = Locale("es", "MX")
private val Mono = FontFamily.Monospace

private fun moodLine(mood: Int) = when (mood) {
    5 -> "¡Día brillante! Guárdalo bien."
    4 -> "Qué bueno. Tokkip está contento contigo."
    3 -> "Un día tranquilo también cuenta."
    2 -> "Ánimo. Escribir lo que pesa ayuda."
    1 -> "Lo siento. ¿Respiramos un minuto juntos?"
    else -> "¿Cómo va tu día?"
}

private fun moodSound(mood: Int) = when (mood) {
    5 -> "proud"; 4 -> "love"; 3 -> "blip"; 2 -> "yawn"; else -> "annoyed"
}

private fun firstOfMonth(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun longDay(day: String) =
    SimpleDateFormat("EEEE d 'de' MMMM", MX).format(Date(dayStart(day))).replaceFirstChar { it.uppercase(MX) }

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

/** A diary photo, loaded off the main thread. */
@Composable
fun rememberDiaryImage(name: String?, maxPx: Int): Bitmap? {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { mx.loperstack.tokkip.character.Sounds.play("page") }
    val state = produceState<Bitmap?>(null, name, maxPx) {
        value = if (name == null) null else withContext(Dispatchers.IO) { DiaryImages.load(context, name, maxPx) }
    }
    return state.value
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(logs: List<DayLog>, color: Color, actions: Actions, composeNow: Long = 0L, goBreathe: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val now = rememberNow(60_000)
    val today = dayKey(now)
    var selected by remember { mutableStateOf(today) }
    var month by remember { mutableLongStateOf(firstOfMonth(now)) }
    var menu by remember { mutableStateOf(false) }
    var card by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val log = logs.firstOrNull { it.day == selected } ?: DayLog(selected)
    val run = streak(logs, now)
    val data = Store.state.value

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { DiaryExport.pdf(context, Store.state.value.logs, data.name, data.tokkipColor, uri) }
            toast(context, if (ok) "Diario exportado en PDF" else "No se pudo exportar")
        }
    }
    // The printable kit for a paper journal (data/JournalKit.kt): pick the days, then where to save it.
    var kitRange by remember { mutableStateOf<mx.loperstack.tokkip.data.KitRange?>(null) }
    var kitDay by remember { mutableStateOf<String?>(null) }
    var askKit by remember { mutableStateOf(false) }
    val saveKit = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val range = kitRange
        val one = kitDay
        if (uri == null || (range == null && one == null)) return@rememberLauncherForActivityResult
        scope.launch {
            toast(context, "Armando tu kit…")
            val ok = withContext(Dispatchers.IO) {
                mx.loperstack.tokkip.data.JournalKit.pdf(context, Store.state.value, range ?: mx.loperstack.tokkip.data.KitRange.WEEK, uri, day = one)
            }
            if (ok) {
                Sounds.play("approve")
                // Straight to it, to look at it or print it.
                runCatching {
                    context.startActivity(
                        android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            "Abrir tu kit",
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure { toast(context, "Kit listo: imprímelo en hoja carta al 100 % y recorta ✂") }
            } else toast(context, "No se pudo armar el kit")
        }
    }
    if (askKit) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askKit = false },
            title = { Text("Kit para journaling") },
            text = {
                Column {
                    Text("Un PDF para imprimir y recortar: tus días como tarjetas con cinta washi y fotos tipo polaroid, stickers de Tokkip, tus rachas, tu mes en colores y hojas para escribir a mano. Al terminar se abre para que lo veas o lo imprimas.")
                    Spacer(Modifier.height(12.dp))
                    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                    // Just the day you're looking at.
                    val dayName = java.text.SimpleDateFormat("EEEE d 'de' MMMM", java.util.Locale("es", "MX"))
                        .format(java.util.Date(mx.loperstack.tokkip.data.dayStart(selected))).replaceFirstChar { it.uppercase() }
                    androidx.compose.material3.TextButton(onClick = {
                        askKit = false; kitRange = null; kitDay = selected
                        saveKit.launch("Tokkip-dia-$selected.pdf")
                    }) { Text("Solo este día · $dayName", fontWeight = FontWeight.Bold) }
                    for (r in mx.loperstack.tokkip.data.KitRange.entries) {
                        androidx.compose.material3.TextButton(onClick = {
                            askKit = false
                            kitRange = r; kitDay = null
                            saveKit.launch("Tokkip-journal-$stamp.pdf")
                        }) { Text(r.label, fontWeight = FontWeight.Bold) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton(onClick = { askKit = false }) { Text("Cancelar") } },
        )
    }

    // The same full copy as Ajustes (the diary, and the streaks, tasks, sleep…).
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val got = withContext(Dispatchers.IO) { mx.loperstack.tokkip.data.Backup.export(context, uri) }
            toast(context, if (got != null) "Copia guardada ✓ ${got.text()}" else "No se pudo guardar la copia")
        }
    }
    var restoring by remember { mutableStateOf<Uri?>(null) }
    val openZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> restoring = uri }
    restoring?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text("¿Restaurar la copia?") },
            text = { Text("Lo que tienes ahora en Tokkip se reemplaza por lo de la copia. Si es una copia antigua solo del diario, sus días se suman a los tuyos.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    restoring = null
                    scope.launch {
                        val got = withContext(Dispatchers.IO) { mx.loperstack.tokkip.data.Backup.restore(context, uri) }
                        toast(context, if (got != null) "Copia restaurada ✓ ${got.text()}" else "Ese archivo no es una copia de Tokkip")
                        if (got != null) Sounds.play("approve")
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { restoring = null }) { Text("Cancelar") } },
        )
    }

    var compose by remember { mutableStateOf(false) }
    // From the night notification: straight to writing.
    androidx.compose.runtime.LaunchedEffect(composeNow) { if (composeNow != 0L) compose = true }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Diario", Modifier.weight(1f))
            if (run > 0) {
                IconLabel(
                    Icons.Rounded.LocalFireDepartment, "$run", Color(0xFFF97316),
                    Modifier.clip(CircleShape).background(Color(0x26F97316)).padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                "Imagen", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink.OnVioletDeep,
                modifier = Modifier.clip(Squircle(12.dp)).background(Ink.VioletDeep).clickable { card = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Box {
                IconButton(onClick = { menu = true }) { Text("⋯", fontSize = 22.sp, color = Ink.Dim) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Kit para journaling (imprimir)") }, onClick = { menu = false; askKit = true })
                    DropdownMenuItem(text = { Text("Diario completo en PDF") }, onClick = { menu = false; savePdf.launch("Tokkip-diario-$today.pdf") })
                    DropdownMenuItem(text = { Text("Copia de seguridad de todo (.zip)") }, onClick = { menu = false; saveZip.launch("Tokkip-copia-$today.zip") })
                    DropdownMenuItem(text = { Text("Restaurar una copia") }, onClick = { menu = false; openZip.launch(arrayOf("application/zip", "application/octet-stream")) })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LogCard(log, dayNumber(logs, selected), selected == today, actions, goBreathe) { viewing = it }
        Spacer(Modifier.height(16.dp))
        MonthGrid(logs, month, selected, today, onSelect = { selected = it }, onMonth = { delta ->
            month = Calendar.getInstance().apply { timeInMillis = month; add(Calendar.MONTH, delta) }.timeInMillis
        })
        Spacer(Modifier.height(96.dp))
    }
    androidx.compose.material3.ExtendedFloatingActionButton(
        onClick = { mx.loperstack.tokkip.character.Haptics.click(context); compose = true },
        icon = { Icon(Glyph.Plus, contentDescription = null) },
        text = { Text("Escribir", fontWeight = FontWeight.Bold) },
        containerColor = Ink.VioletDeep, contentColor = Ink.Text,
        shape = Squircle(20.dp),
        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
    )
    }
    if (compose) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { compose = false }, containerColor = Ink.CardHigh) {
            Composer(today, actions) { compose = false }
        }
    }

    if (card) DayCardDialog(log, dayNumber(logs, selected), color, data.name) { card = false }
    viewing?.let { PhotoViewer(it) { viewing = null } }
}

@Composable
private fun LogCard(log: DayLog, number: Int, isToday: Boolean, actions: Actions, goBreathe: () -> Unit, view: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember(log.day) { mutableStateOf("") }
    fun stamp() = if (isToday) System.currentTimeMillis() else dayStart(log.day) + 12 * 3600_000L + (System.currentTimeMillis() % 3_600_000L)
    val submit = {
        if (text.isNotBlank()) {
            Store.setLogs(withNote(Store.state.value.logs, log.day, stamp(), text))
            text = ""
            actions.jump++
            Sounds.play("pop")
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val caption = text
        text = ""
        scope.launch {
            val names = withContext(Dispatchers.IO) { uris.mapNotNull { DiaryImages.import(context, it) } }
            var logs = Store.state.value.logs
            val base = stamp()
            names.forEachIndexed { i, n -> logs = withNote(logs, log.day, base + i, if (i == 0) caption else "", n) }
            Store.setLogs(logs)
            if (names.isNotEmpty()) { Sounds.play("gulp"); actions.jump++ }
            if (names.size < uris.size) toast(context, "Algunas fotos no se pudieron leer")
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(Squircle(24.dp)).background(Ink.Card)
            .border(1.dp, Ink.VioletLine, Squircle(24.dp))
            .drawBehind {
                val step = 30.dp.toPx()
                var y = 96.dp.toPx()
                while (y < size.height - 8.dp.toPx()) {
                    drawLine(Ink.Track, Offset(16.dp.toPx(), y), Offset(size.width - 16.dp.toPx(), y), 1f)
                    y += step
                }
            }
            .animateContentSize()
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◆ REGISTRO DIARIO", fontFamily = Mono, fontSize = 11.sp, color = Ink.Dim2, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("DÍA %03d".format(number), fontFamily = Mono, fontSize = 13.sp, color = Ink.VioletSoft, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(longDay(log.day), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(14.dp))
        Text(
            if (isToday) "¿Cómo te sientes ahora?" else "¿Cómo te sentiste?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink.Text,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (m in MOODS) {
                val on = log.mood == m.value
                Column(
                    Modifier.weight(1f).alpha(if (log.mood == 0 || on) 1f else 0.55f).clip(Squircle(18.dp))
                        .background(if (on) Color(m.color).copy(alpha = 0.18f) else Color.Transparent)
                        .clickable {
                            // Each tap is a moment of the day: 08:10 Genial, 18:05 Bajón…
                            val at = if (isToday) System.currentTimeMillis() else dayStart(log.day) + 12 * 3600_000L + log.moods.size * 60_000L
                            Store.setLogs(mx.loperstack.tokkip.data.addMood(Store.state.value.logs, log.day, at, m.value))
                            Sounds.play(moodSound(m.value)); if (m.value >= 4) actions.jump++
                            mx.loperstack.tokkip.character.Haptics.click(context)
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Tokkip(Modifier.width(52.dp).height(44.dp), color = Color(m.color), face = m.face)
                    Text(m.label, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) Color(m.color) else Ink.Dim)
                }
            }
        }
        if (log.moods.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MoodTimeline(log)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            moodLine(log.mood), fontSize = 13.sp, color = moodOf(log.mood)?.let { Color(it.color) } ?: Ink.Dim,
            modifier = if (log.mood == 1) Modifier.clickable(onClick = goBreathe) else Modifier,
        )
        Spacer(Modifier.height(14.dp))
        if (log.notes.isEmpty()) {
            Text(if (isToday) "> sin entradas todavía_" else "> sin entradas", fontFamily = Mono, fontSize = 13.sp, color = Ink.Dim2)
        }
        for (n in log.notes) NoteRow(log.day, n, view)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(500) }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(if (isToday) "Escribe una línea del día…" else "Agregar a este día…", color = Ink.Dim2) },
            shape = Squircle(16.dp), maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            leadingIcon = {
                IconButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Foto", tint = Ink.VioletSoft)
                }
            },
            trailingIcon = { IconButton(onClick = { submit() }) { Icon(Glyph.Plus, contentDescription = "Guardar", tint = Ink.Violet) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink.Violet, unfocusedBorderColor = Ink.Hairline,
                focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = Ink.Violet,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text("Agrega fotos (el texto escrito queda como su pie). Solo se guarda en este teléfono.", fontSize = 11.sp, color = Ink.Dim2)
    }
}

@Composable
private fun NoteRow(day: String, n: LogNote, view: (String) -> Unit) {
    val context = LocalContext.current
    val remove = {
        Store.setLogs(withoutNote(Store.state.value.logs, day, n.at))
        DiaryImages.delete(context, n.image)
        Sounds.play("blip")
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(SimpleDateFormat("HH:mm", MX).format(Date(n.at)), fontFamily = Mono, fontSize = 13.sp, color = Ink.VioletSoft, fontWeight = FontWeight.Bold)
        Text(" ▸ ", fontFamily = Mono, fontSize = 13.sp, color = Ink.Dim2)
        Column(Modifier.weight(1f)) {
            if (n.image != null) {
                val bmp = rememberDiaryImage(n.image, 900)
                Box(
                    Modifier.fillMaxWidth().height(190.dp).clip(Squircle(16.dp)).background(Ink.Track)
                        .clickable { view(n.image) },
                ) {
                    if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (n.text.isNotEmpty()) Spacer(Modifier.height(4.dp))
            }
            if (n.text.isNotEmpty()) Text(n.text, fontSize = 15.sp, color = Ink.Text)
        }
        Text(
            "×", fontSize = 16.sp, color = Ink.Dim2,
            modifier = Modifier.clip(CircleShape).clickable(onClick = remove).padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun PhotoViewer(name: String, close: () -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val bmp = rememberDiaryImage(name, 2000)
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = close), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ── El día como imagen ────────────────────────────────────────────────────────

@Composable
private fun DayCardDialog(log: DayLog, number: Int, color: Color, name: String, close: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }
    val title = "Tokkip-${log.day}"

    fun render(then: (Bitmap) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val bmp = runCatching { layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            busy = false
            if (bmp == null) toast(context, "No se pudo crear la imagen") else then(bmp)
        }
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color(0xE6000000)).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
            ) { DayCard(log, number, color, name) }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Guardar en Galería", color = Color.White, textColor = Color(0xFF0B0C0E)) {
                    render { b ->
                        val ok = DiaryExport.saveToGallery(context, b, title)
                        toast(context, if (ok) "Guardada en Imágenes › Tokkip" else "No se pudo guardar")
                        if (ok) Sounds.play("approve")
                    }
                }
                PrimaryButton("Compartir", color = Color(0xFFC4B5FD), textColor = Color(0xFF0B0C0E)) {
                    render { b -> DiaryExport.share(context, b, title) }
                }
            }
            Text("Cerrar", color = Color(0xFFA5A9B1), modifier = Modifier.padding(16.dp).clickable(onClick = close))
        }
    }
}

/** The card itself: always the same look, whatever the app's theme. */
@Composable
private fun DayCard(log: DayLog, number: Int, color: Color, name: String) {
    val mood = moodOf(log.mood)
    val accent = mood?.let { Color(it.color) } ?: color
    val photos = log.notes.mapNotNull { it.image }.take(4)
    val lines = log.notes.filter { it.text.isNotEmpty() }.take(8)
    Column(
        Modifier.width(340.dp).clip(Squircle(30.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF15121F), Color(0xFF0B0C0E), accent.copy(alpha = 0.35f))))
            .border(1.dp, Color(0x33FFFFFF), Squircle(30.dp))
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◆ REGISTRO DIARIO", fontFamily = Mono, fontSize = 10.sp, color = Color(0xFF8E939C), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("DÍA %03d".format(number), fontFamily = Mono, fontSize = 12.sp, color = Color(0xFFC4B5FD), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(longDay(log.day), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                if (mood != null) {
                    Text(
                        mood.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent,
                        modifier = Modifier.padding(top = 6.dp).clip(Squircle(10.dp)).background(accent.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            Tokkip(Modifier.width(96.dp).height(80.dp), color = mood?.let { Color(it.color) } ?: color, face = mood?.face)
        }
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            for (row in photos.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    for (p in row) {
                        val bmp = rememberDiaryImage(p, 700)
                        Box(Modifier.weight(1f).aspectRatio(if (photos.size == 1) 1.5f else 1f).clip(Squircle(14.dp)).background(Color(0x14FFFFFF))) {
                            if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            for (n in lines) {
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(SimpleDateFormat("HH:mm", MX).format(Date(n.at)), fontFamily = Mono, fontSize = 12.sp, color = Color(0xFFC4B5FD), fontWeight = FontWeight.Bold)
                    Text("  ${n.text}", fontSize = 13.sp, color = Color(0xFFE5E7EB), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (name.isBlank()) "Tokkip" else "El diario de $name · Tokkip", fontSize = 11.sp, color = Color(0xFF6B7079),
            textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── El mes ────────────────────────────────────────────────────────────────────

@Composable
private fun MonthGrid(logs: List<DayLog>, month: Long, selected: String, today: String, onSelect: (String) -> Unit, onMonth: (Int) -> Unit) {
    val byDay = logs.associateBy { it.day }
    val cal = Calendar.getInstance().apply { timeInMillis = month }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val lead = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday first
    val prefix = SimpleDateFormat("yyyy-MM-", Locale.US).format(Date(month))
    val counts = IntArray(6)
    for (d in 1..daysInMonth) byDay["$prefix%02d".format(d)]?.let { counts[it.averageMood]++ }

    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 22.sp, color = Ink.Dim, modifier = Modifier.clip(CircleShape).clickable { onMonth(-1) }.padding(horizontal = 12.dp))
            Text(
                SimpleDateFormat("MMMM yyyy", MX).format(Date(month)).replaceFirstChar { it.uppercase(MX) },
                fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            Text("›", fontSize = 22.sp, color = Ink.Dim, modifier = Modifier.clip(CircleShape).clickable { onMonth(1) }.padding(horizontal = 12.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            for (d in listOf("L", "M", "M", "J", "V", "S", "D")) {
                Text(d, fontSize = 11.sp, color = Ink.Dim2, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(6.dp))
        val cells = lead + daysInMonth
        for (week in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (wd in 0..6) {
                    val d = week * 7 + wd - lead + 1
                    if (d < 1 || d > daysInMonth) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                        continue
                    }
                    val key = "$prefix%02d".format(d)
                    val entry = byDay[key]
                    val mood = moodOf(entry?.averageMood ?: 0)
                    val future = key > today
                    val hasPhoto = entry?.notes?.any { it.image != null } == true
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(Squircle(9.dp))
                            .background(mood?.let { Color(it.color).copy(alpha = 0.75f) } ?: Ink.Track)
                            .border(
                                if (key == selected) 2.dp else if (key == today) 1.dp else 0.dp,
                                if (key == selected) Ink.Text else if (key == today) Ink.VioletSoft else Color.Transparent,
                                Squircle(9.dp),
                            )
                            .clickable(enabled = !future) { onSelect(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$d", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (future) Ink.Dim2.copy(alpha = 0.5f) else if (mood != null) Color(0xFF0B0C0E) else Ink.Dim)
                        if (entry != null && entry.notes.isNotEmpty()) {
                            Text(
                                if (hasPhoto) "▪" else "•", fontSize = 9.sp,
                                color = if (mood != null) Color(0xAA0B0C0E) else Ink.VioletSoft,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (m in MOODS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(m.color)))
                    Text(" ${counts[m.value]}", fontSize = 13.sp, color = Ink.Dim, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}


/** The day's moods in order: a small curve and one chip per moment (tap × to remove it). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MoodTimeline(log: DayLog) {
    val context = LocalContext.current
    if (log.moods.size >= 2) {
        val pts = log.moods
        val line = Ink.Violet
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val t0 = pts.first().at
            val span = (pts.last().at - t0).coerceAtLeast(1L).toFloat()
            fun x(at: Long) = 8f + (size.width - 16f) * ((at - t0) / span)
            fun y(v: Int) = size.height - 6f - (size.height - 12f) * ((v - 1) / 4f)
            for (v in 1..5) drawLine(Ink.Track, Offset(0f, y(v)), Offset(size.width, y(v)), 1f)
            val path = androidx.compose.ui.graphics.Path()
            pts.forEachIndexed { i, e -> if (i == 0) path.moveTo(x(e.at), y(e.value)) else path.lineTo(x(e.at), y(e.value)) }
            drawPath(path, line, style = androidx.compose.ui.graphics.drawscope.Stroke(5f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            for (e in pts) {
                val c = moodOf(e.value)?.let { Color(it.color) } ?: line
                drawCircle(c, 9f, Offset(x(e.at), y(e.value)))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (e in log.moods) {
            val m = moodOf(e.value) ?: continue
            Row(
                Modifier.clip(Squircle(16.dp)).background(Color(m.color).copy(alpha = 0.16f)).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(SimpleDateFormat("HH:mm", MX).format(Date(e.at)), fontFamily = Mono, fontSize = 12.sp, color = Ink.Dim, fontWeight = FontWeight.Bold)
                Text("  ${m.label}", fontSize = 13.sp, color = Color(m.color), fontWeight = FontWeight.Bold)
                Text(
                    "×", fontSize = 15.sp, color = Ink.Dim2,
                    modifier = Modifier.clip(CircleShape).clickable {
                        Store.setLogs(mx.loperstack.tokkip.data.removeMood(Store.state.value.logs, log.day, e.at))
                        mx.loperstack.tokkip.character.Haptics.tick(context)
                    }.padding(horizontal = 8.dp),
                )
            }
        }
    }
}


/** Write the moment, Journal-style: how you feel, what happened, photos. Goes to today. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Composer(day: String, actions: Actions, done: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf(0) }
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { photos = (photos + it).take(6) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
        Text("Nueva entrada", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(SimpleDateFormat("EEEE d 'de' MMMM · HH:mm", MX).format(Date()).replaceFirstChar { it.uppercase(MX) }, color = Ink.Dim, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            for (m in MOODS) {
                val on = mood == m.value
                Column(
                    Modifier.weight(1f).clip(Squircle(18.dp)).background(if (on) Color(m.color).copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { mood = if (on) 0 else m.value; mx.loperstack.tokkip.character.Haptics.tick(context) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Tokkip(Modifier.width(48.dp).height(40.dp), color = Color(m.color), face = m.face)
                    Text(m.label, fontSize = 11.sp, color = if (on) Color(m.color) else Ink.Dim, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(500) }, modifier = Modifier.fillMaxWidth().height(170.dp),
            placeholder = { Text("¿Qué pasó? ¿Qué sientes?", color = Ink.Dim2) }, shape = Squircle(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink.Violet, unfocusedBorderColor = Ink.Hairline,
                focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = Ink.Violet,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("Fotos${if (photos.isEmpty()) "" else " · ${photos.size}"}", false, Icons.Rounded.AddPhotoAlternate) {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Guardar", Modifier.fillMaxWidth(), Ink.VioletSoft, Ink.OnViolet) {
            if (text.isBlank() && mood == 0 && photos.isEmpty()) { done(); return@PrimaryButton }
            val now = System.currentTimeMillis()
            val caption = text
            val chosenMood = mood
            val chosen = photos
            scope.launch {
                val names = withContext(Dispatchers.IO) { chosen.mapNotNull { DiaryImages.import(context, it) } }
                var logs = Store.state.value.logs
                if (chosenMood > 0) logs = mx.loperstack.tokkip.data.addMood(logs, day, now, chosenMood)
                if (names.isEmpty()) logs = withNote(logs, day, now, caption)
                names.forEachIndexed { i, n -> logs = withNote(logs, day, now + i, if (i == 0) caption else "", n) }
                Store.setLogs(logs)
                actions.jump++
                Sounds.play("pop")
                mx.loperstack.tokkip.character.Haptics.success(context)
                done()
            }
        }
    }
}
