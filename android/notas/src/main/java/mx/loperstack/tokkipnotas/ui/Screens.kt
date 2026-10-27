// The other screens: templates for a new page, search, the Papelera and
// Ajustes (Tokkip's look, the theme, sounds, and copies of your notes).

package mx.loperstack.tokkipnotas.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipStyle
import mx.loperstack.tokkip.character.PolygonShape
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Species
import mx.loperstack.tokkip.character.Squircle
import mx.loperstack.tokkipnotas.data.Index
import mx.loperstack.tokkipnotas.data.PALETTE
import mx.loperstack.tokkipnotas.data.Page
import mx.loperstack.tokkipnotas.data.Store
import mx.loperstack.tokkipnotas.data.Sync
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.math.roundToInt
import mx.loperstack.tokkipnotas.data.TEMPLATES
import mx.loperstack.tokkipnotas.data.Template
import mx.loperstack.tokkipnotas.data.between
import mx.loperstack.tokkipnotas.data.build
import org.json.JSONObject

// ── New page ──────────────────────────────────────────────────────────────────

@Composable
fun TemplateSheet(parent: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    Sheet(onClose) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            ScreenTitle("Nueva página", size = 28.sp)
            Label(
                if (parent == null) "Escoge una plantilla y empieza: todo se puede cambiar después." else "Dentro de «${Store.page(parent)?.shownTitle}»",
                Ink.Dim2, 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            for (rowT in TEMPLATES.chunked(2)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (t in rowT) TemplateTile(t, Modifier.weight(1f)) {
                        val last = Store.index.value.children(parent).maxOfOrNull { it.ord }
                        val made = Store.create(build(t, parent, System.currentTimeMillis(), between(last, null)))
                        Sounds.play("pop"); Haptics.success(context); Hop.now()
                        onClose()
                        parent?.let { Nav.open[it] = true }
                        Nav.page(made.id)
                    }
                    if (rowT.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TemplateTile(t: Template, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(136.dp).clip(Squircle(24.dp)).background(Ink.Card).clickable(onClick = onClick).padding(14.dp)) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().clip(PolygonShape(shapeOf(t.shape))).background(Color(t.color)))
            Icon(iconOf(t.icon), null, tint = INK_ON, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(t.name, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Label(t.about, Ink.Dim2, 12.sp, FontWeight.Normal, maxLines = 2)
    }
}

// ── Search ────────────────────────────────────────────────────────────────────

@Composable
fun SearchScreen(ix: Index) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Pair<Page, String>>()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { delay(100); focus.requestFocus() } }
    LaunchedEffect(query, ix) {
        delay(180)
        results = withContext(Dispatchers.IO) { Store.search(query) }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.Rounded.ArrowBack, "Volver", size = 44.dp) { Nav.back() }
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(80) }, singleLine = true,
                    placeholder = { Text("Buscar en todo", color = Ink.Dim2) },
                    trailingIcon = { if (query.isNotEmpty()) Icon(Icons.Rounded.Close, "Borrar", modifier = Modifier.clickable { query = "" }) },
                    shape = Squircle(18.dp), modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (query.isNotBlank() && results.isEmpty()) Label("Nada con «$query».", Ink.Dim2, 14.sp)
        }
        items(results, key = { it.first.id }) { (p, snip) ->
            val where = ix.path(p.id).joinToString(" › ") { it.shownTitle }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Squircle(18.dp)).background(Ink.Card).clickable { Nav.page(p.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageIcon(p, 32.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.shownTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (snip.isNotBlank()) Text(snip, color = Ink.Dim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (where.isNotBlank()) Label(where, Ink.Dim2, 11.sp, maxLines = 1)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Papelera ──────────────────────────────────────────────────────────────────

@Composable
fun TrashScreen(ix: Index) {
    val context = LocalContext.current
    var emptying by remember { mutableStateOf(false) }
    val list = ix.trash
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.Rounded.ArrowBack, "Volver", size = 44.dp) { Nav.back() }
                Spacer(Modifier.weight(1f))
                if (list.isNotEmpty()) Pill("Vaciar", false, Icons.Rounded.DeleteForever) { emptying = true }
            }
            Spacer(Modifier.height(10.dp))
            ScreenTitle("Papelera", size = 32.sp)
            Label("Lo que tiras se queda aquí 30 días, con todo lo que tenía dentro.", Ink.Dim2, 13.sp)
            Spacer(Modifier.height(12.dp))
            if (list.isEmpty()) Label("Vacía.", Ink.Dim2, 14.sp, modifier = Modifier.padding(8.dp))
        }
        items(list, key = { it.id }) { p ->
            PageRow(p, ix, onClick = {}, trailing = {
                Row {
                    CircleButton(Icons.Rounded.Restore, "Recuperar", size = 40.dp) { Store.restore(p.id); Sounds.play("pop"); Haptics.tick(context) }
                    Spacer(Modifier.width(6.dp))
                    CircleButton(Icons.Rounded.DeleteForever, "Borrar para siempre", size = 40.dp, tint = Ink.Red) { Store.purge(p.id) }
                }
            })
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
    if (emptying) AlertDialog(
        onDismissRequest = { emptying = false }, containerColor = Ink.CardHigh,
        title = { Text("¿Vaciar la Papelera?") }, text = { Text("Se borra para siempre.") },
        confirmButton = { TextButton(onClick = { emptying = false; Store.emptyTrash() }) { Text("Vaciar", color = Ink.Red) } },
        dismissButton = { TextButton(onClick = { emptying = false }) { Text("Cancelar", color = Ink.Dim) } },
    )
}

// ── Ajustes ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var restoring by remember { mutableStateOf<Uri?>(null) }
    fun toast(t: String) = android.widget.Toast.makeText(context, t, android.widget.Toast.LENGTH_LONG).show()
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(Store.exportJson().toString().toByteArray()) } != null }.getOrDefault(false)
            }
            toast(if (ok) "Copia guardada" else "No se pudo guardar la copia")
        }
    }
    val importFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoring = uri }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(Icons.Rounded.ArrowBack, "Volver", size = 44.dp) { Nav.back() }
        }
        Spacer(Modifier.height(10.dp))
        ScreenTitle("Ajustes", size = 32.sp)

        SectionLabel("TOKKIP")
        Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tokkip(Modifier.size(84.dp, 72.dp), color = Color(Prefs.color), jump = Hop.count)
                Spacer(Modifier.width(12.dp))
                Label("Te acompaña mientras escribes. El mismo de Tokkip, a tu gusto.", Ink.Dim, 13.sp, FontWeight.Normal)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (sp in Species.entries) Pill(sp.label, TokkipStyle.species == sp) { Prefs.set(species = sp); Hop.now() }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in listOf(0xFFF5F6F8) + PALETTE) {
                    Box(Modifier.size(38.dp).clip(PolygonShape(if (c == Prefs.color) MShapes.Cookie9 else MShapes.Circle)).background(Color(c)).clickable { Prefs.set(color = c) })
                }
            }
        }

        SectionLabel("ASPECTO")
        PillRow {
            Pill("Tu Pixel", Prefs.theme == "pixel") { Prefs.set(theme = "pixel") }
            Pill("Pixel oscuro", Prefs.theme == "pixelDark") { Prefs.set(theme = "pixelDark") }
            Pill("Tokkip", Prefs.theme == "tokkip") { Prefs.set(theme = "tokkip") }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(Squircle(18.dp)).background(Ink.Card).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sonidos", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Switch(Prefs.sounds, { Prefs.set(sounds = it); if (it) Sounds.play("pop") }, colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet))
        }

        SyncSection()

        SectionLabel("TUS NOTAS")
        Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
            Label("Todo vive en este teléfono. Guarda una copia donde quieras (Drive, tu PC…) y restáurala aquí o en otro teléfono.", Ink.Dim, 13.sp, FontWeight.Normal)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpressiveButton("Guardar copia", Modifier.weight(1f)) { exportTo.launch("tokkip-notas-${mx.loperstack.tokkipnotas.data.dayKey(System.currentTimeMillis())}.json") }
                ExpressiveButton("Restaurar", container = Ink.CardHigh, content = Ink.Text) { importFrom.launch(arrayOf("application/json", "*/*")) }
            }
            Spacer(Modifier.height(12.dp))
            Label("¿Tenías notas en el Cuaderno de Tokkip? En Tokkip → Ajustes → «Pasar a Tokkip Notas».", Ink.Dim2, 12.sp, FontWeight.Normal)
        }
        Spacer(Modifier.height(24.dp))
        Label("Tokkip Notas ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""} · sin cuentas; solo habla con tu propio servidor.", Ink.Dim2, 12.sp, FontWeight.Normal)
        Spacer(Modifier.height(60.dp))
    }

    restoring?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoring = null }, containerColor = Ink.CardHigh,
            title = { Text("¿Cómo la traigo?") },
            text = { Text("«Reemplazar» deja solo lo de la copia. «Juntar» suma la copia a lo que ya tienes.") },
            confirmButton = {
                TextButton(onClick = {
                    restoring = null
                    scope.launch { toast(restore(context, uri, replace = false)) }
                }) { Text("Juntar", color = Ink.VioletSoft) }
            },
            dismissButton = {
                TextButton(onClick = {
                    restoring = null
                    scope.launch { toast(restore(context, uri, replace = true)) }
                }) { Text("Reemplazar", color = Ink.Red) }
            },
        )
    }
}

// ── Sincronizar ───────────────────────────────────────────────────────────────

fun ago(t: Long, now: Long = System.currentTimeMillis()): String {
    if (t == 0L) return "nunca"
    val m = ((now - t) / 60_000.0).roundToInt()
    return when {
        m < 1 -> "hace un momento"
        m < 60 -> "hace $m min"
        m < 60 * 24 -> "hace ${(m / 60.0).roundToInt()} h"
        m < 60 * 48 -> "ayer"
        else -> "hace ${(m / 1440.0).roundToInt()} días"
    }
}

@Composable
private fun SyncSection() {
    val s by Sync.status.collectAsState()
    val scope = rememberCoroutineScope()
    val now = rememberNow(30_000)
    SectionLabel("SINCRONIZAR")
    if (s.enabled) {
        var leaving by remember { mutableStateOf(false) }
        Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(if (s.error != null) Icons.Rounded.CloudOff else Icons.Rounded.Cloud, shape = MShapes.Cookie9, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when { s.error != null -> "No se pudo sincronizar"; s.running -> "Sincronizando…"; else -> "Tus notas viajan entre tus aparatos" },
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (s.error != null) Ink.Red else Ink.Text,
                    )
                    Label(
                        s.error ?: ("Última vez ${ago(s.last, now)}" + if (s.pending > 0) " · ${s.pending} ${if (s.pending == 1) "cambio espera" else "cambios esperan"}" else ""),
                        Ink.Dim2, 12.sp, FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Label(s.url, Ink.Dim2, 12.sp, FontWeight.Normal, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ExpressiveButton(if (s.running) "Sincronizando…" else "Sincronizar ahora", icon = Icons.Rounded.Sync) { Sync.now() }
                Text("Dejar", color = Ink.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.clip(Squircle(12.dp)).clickable { leaving = true }.padding(12.dp))
            }
        }
        if (leaving) AlertDialog(
            onDismissRequest = { leaving = false }, containerColor = Ink.CardHigh,
            title = { Text("¿Dejar de sincronizar aquí?") },
            text = { Text("Tus notas se quedan en este aparato y en tu servidor; este aparato solo deja de mandar y recibir cambios, y olvida la clave y la frase.") },
            confirmButton = { TextButton(onClick = { leaving = false; scope.launch { Sync.disconnect() } }) { Text("Dejar de sincronizar", color = Ink.Red) } },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text("Cancelar", color = Ink.Dim) } },
        )
        return
    }
    var url by remember { mutableStateOf(s.url) }
    var key by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Ink.Violet, unfocusedBorderColor = Ink.Hairline,
        focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = Ink.Violet,
    )
    Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
        Label("Con tu propio servidor (sync.php en tu hosting), lo que escribes aquí aparece en tu PC y tu tablet, y al revés. Viaja cifrado con tu frase: tu hosting no puede leerlo.", Ink.Dim, 13.sp, FontWeight.Normal)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            url, { url = it.trim() }, label = { Text("Dirección de sync.php") }, placeholder = { Text("https://notas.tudominio.com/sync.php", color = Ink.Dim2) },
            singleLine = true, shape = Squircle(16.dp), colors = colors, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            key, { key = it.trim() }, label = { Text("Clave de sincronización") }, singleLine = true, shape = Squircle(16.dp), colors = colors,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            phrase, { phrase = it }, label = { Text("Frase secreta") }, singleLine = true, shape = Squircle(16.dp), colors = colors,
            modifier = Modifier.fillMaxWidth(), visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Ocultar" else "Mostrar", tint = Ink.Dim, modifier = Modifier.clickable { show = !show }) },
        )
        Spacer(Modifier.height(6.dp))
        Label("La misma frase en todos tus aparatos. Si es el primero, la que escribas será la frase: guárdala bien.", Ink.Dim2, 12.sp, FontWeight.Normal)
        error?.let {
            Spacer(Modifier.height(10.dp))
            Label(it, Ink.Red, 13.sp, FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        ExpressiveButton(if (busy) "Conectando…" else "Conectar", icon = Icons.Rounded.Cloud) {
            if (busy || url.isBlank() || key.isBlank() || phrase.length < 6) {
                error = if (phrase.isNotEmpty() && phrase.length < 6) "La frase secreta necesita al menos 6 letras" else "Llena la dirección, la clave y la frase"
                return@ExpressiveButton
            }
            busy = true; error = null
            scope.launch {
                try {
                    val r = Sync.connect(url, key, phrase)
                    key = ""; phrase = ""
                    Sounds.play("level_up", 0.6f); Hop.now()
                    android.widget.Toast.makeText(context, if (r.pulled > 0) "Conectado: llegaron ${r.pulled} páginas" else "Conectado. Tus notas ya viajan", android.widget.Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    error = e.message ?: "No se pudo conectar"
                } finally {
                    busy = false
                }
            }
        }
    }
}

private suspend fun restore(context: android.content.Context, uri: Uri, replace: Boolean): String = withContext(Dispatchers.IO) {
    val n = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return@runCatching -1
        Store.importJson(JSONObject(text), replace)
    }.getOrDefault(-1)
    if (n < 0) "Ese archivo no es una copia de Tokkip Notas" else "Listo: $n páginas"
}
