// Ajustes: you (your name), Tokkip's colour, the theme, sounds, Google Tasks,
// your devices — the little ecosystem — and the welcome again. Also the
// pieces the welcome reuses (theme cards, the devices list), and the aurora
// that softly moves behind Tokkip.

package mx.loperstack.tokkip.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.Squircle
import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import mx.loperstack.tokkip.data.AppData
import mx.loperstack.tokkip.data.GoogleTasks
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.character.Sounds
import kotlin.math.cos
import kotlin.math.sin

// ── Aurora ────────────────────────────────────────────────────────────────────

/** Three soft lights in Tokkip's colour and the theme's, drifting slowly. */
@Composable
fun Aurora(color: Color, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, (Math.PI * 2).toFloat(), infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    val accent = Ink.Violet
    val alpha = if (Ink.Dark) 0.30f else 0.22f
    Canvas(modifier.blur(40.dp)) {
        val w = size.width
        val h = size.height
        val lights = listOf(
            Triple(color, Offset(w * (0.30f + 0.12f * cos(t)), h * (0.35f + 0.10f * sin(t))), w * 0.55f),
            Triple(accent, Offset(w * (0.75f + 0.10f * sin(t * 1.3f)), h * (0.30f + 0.12f * cos(t))), w * 0.45f),
            Triple(color, Offset(w * (0.55f + 0.15f * sin(t + 2f)), h * (0.80f + 0.08f * cos(t * 0.7f))), w * 0.50f),
        )
        for ((c, center, r) in lights) {
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), center, r), r, center)
        }
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Ink.VioletSoft, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp, start = 4.dp))
}

@Composable
fun NameField(name: String, onName: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = name, onValueChange = { onName(it.take(30)) }, modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Tu nombre", color = Ink.Dim2) }, singleLine = true, shape = Squircle(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink.Violet, unfocusedBorderColor = Ink.Hairline,
            focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = Ink.Violet,
        ),
    )
}

/** Three cards, each painted in the colours it would give the app. */
@Composable
fun ThemeCards(current: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val light = remember { dynamicLightColorScheme(context) }
    val dark = remember { dynamicDarkColorScheme(context) }
    val options = listOf(
        Triple("pixel", "Como mi Pixel", listOf(light.surface, light.primary, dark.surface)),
        Triple("pixelDark", "Pixel oscuro", listOf(dark.surface, dark.primary, dark.primaryContainer)),
        Triple("tokkip", "Tokkip clásico", listOf(Color(0xFF0B0C0E), Color(0xFFA78BFA), Color(0xFF17181C))),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((id, label, colors) in options) {
            val on = id == current
            Column(
                Modifier.weight(1f).clip(Squircle(20.dp))
                    .border(if (on) 2.dp else 1.dp, if (on) Ink.Violet else Ink.Hairline, Squircle(20.dp))
                    .clickable { onPick(id); Sounds.play("pop") }.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(64.dp).clip(Squircle(14.dp))
                        .background(Brush.linearGradient(listOf(colors[0], colors[2]))),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(22.dp).clip(CircleShape).background(colors[1])) }
                Spacer(Modifier.height(8.dp))
                Text(label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = if (on) Ink.Text else Ink.Dim)
            }
        }
    }
}

/** The watch's name if one is linked right now, null if not. */
suspend fun linkedWatch(context: Context): String? = withContext(Dispatchers.IO) {
    runCatching { Tasks.await(Wearable.getNodeClient(context).connectedNodes).firstOrNull()?.displayName }.getOrNull()
}

@Composable
fun DeviceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, status: String, ok: Boolean, shape: androidx.graphics.shapes.RoundedPolygon = MShapes.Cookie9) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(Squircle(18.dp)).background(Ink.Card)
            .border(1.dp, Ink.Hairline, Squircle(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapeIcon(icon, shape = shape, size = 42.dp, container = if (ok) Ink.VioletDeep else Ink.CardHigh, tint = if (ok) Ink.VioletSoft else Ink.Dim)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Label(status, Ink.Dim, 12.sp)
        }
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) Ink.Green else Ink.Dim2))
    }
}

/** Tokkip on all your devices: the watch, this phone, the tablet and the laptop. */
@Composable
fun Devices(google: GoogleTasks.Status) {
    val context = LocalContext.current
    var watch by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { watch = linkedWatch(context); checked = true }
    val tablet = context.resources.configuration.smallestScreenWidthDp >= 600
    DeviceRow(
        Icons.Rounded.Watch, watch ?: "Reloj",
        when { !checked -> "Buscando…"; watch != null -> "Conectado · pendientes, respirar, ánimo y pulso"; else -> "Sin reloj ahora · abre Tokkip en tu Pixel Watch" },
        watch != null, MShapes.Cookie9,
    )
    DeviceRow(
        if (tablet) Icons.Rounded.TabletAndroid else Icons.Rounded.PhoneAndroid, if (tablet) "Esta tablet" else "Este teléfono",
        "Aquí vive tu diario, tu historial y tus widgets", true, MShapes.Clover4,
    )
    DeviceRow(
        if (tablet) Icons.Rounded.PhoneAndroid else Icons.Rounded.TabletAndroid, if (tablet) "Tu teléfono" else "Tu tablet",
        if (google.connected) "Las mismas tareas por Google Tasks" else "Conecta Google Tasks para compartir tareas", google.connected, MShapes.Sunny,
    )
    // Headphones: their battery for the watch's Baterías (data/Headphones.kt).
    var btOk by remember { mutableStateOf(mx.loperstack.tokkip.data.Headphones.granted(context)) }
    val askBt = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        btOk = it
        if (it) mx.loperstack.tokkip.data.WearSync.push(context)
    }
    val buds = remember(btOk) { mx.loperstack.tokkip.data.Headphones.levels(context) }
    Box(Modifier.clickable(enabled = !btOk) { askBt.launch(android.Manifest.permission.BLUETOOTH_CONNECT) }) {
        DeviceRow(
            Icons.Rounded.Headphones, buds.firstOrNull()?.first ?: "Tus audífonos",
            when {
                !btOk -> "Toca para ver su batería en el reloj (permiso de dispositivos cercanos)"
                buds.isEmpty() -> "Conéctalos y su batería aparece en Baterías del reloj"
                else -> buds.joinToString(" · ") { "${it.first} ${it.second} %" }
            },
            btOk && buds.isNotEmpty(), MShapes.Sunny,
        )
    }
    DeviceRow(
        Icons.Rounded.Laptop, "Tu laptop (Tokkip PC)",
        if (google.connected) "Mismas tareas que la isla · el puente directo llega pronto" else "Con Google Tasks, la isla y Tokkip comparten tareas",
        google.connected, MShapes.Flower,
    )
}

// ── Ajustes ───────────────────────────────────────────────────────────────────

@Composable
fun SettingsSheet(
    data: AppData, color: Color, google: GoogleTasks.Status,
    connectGoogle: () -> Unit, syncGoogle: () -> Unit, disconnectGoogle: () -> Unit, replayWelcome: () -> Unit,
) {
    var name by remember { mutableStateOf(data.name) }
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            ScreenTitle("Ajustes", size = 34.sp)
            SectionTitle("Tú")
            NameField(name, { name = it; Store.setName(it) })
        }
        Spacer(Modifier.height(18.dp))
        ColorPicker(data.tokkipColor, color) { Store.setColor(it) }
        Column(Modifier.padding(horizontal = 24.dp)) {
            SectionTitle("Tokkip")
            TokkipPicker(data)
            SectionTitle("Gorritos y accesorios")
            val season = remember { mx.loperstack.tokkip.character.Outfit.seasonal(java.util.Calendar.getInstance()) }
            Label(
                if (data.outfit == "auto") "Según la temporada · hoy: ${season.label}" else "Siempre: ${mx.loperstack.tokkip.character.Outfit.resolve(data.outfit).label}",
                Ink.Dim2, 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutfitChips(data.outfit)
            SectionTitle("Tema")
            ThemeCards(data.theme) { Store.setTheme(it) }
            SectionTitle("Sonidos")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sonidos de Tokkip", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Label("Los mismos de la PC, en el teléfono y el reloj", Ink.Dim2)
                }
                Switch(
                    checked = data.soundOn, onCheckedChange = { Store.setSound(it); if (it) Sounds.play("pop") },
                    colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet),
                )
            }
            if (data.soundOn) {
                Spacer(Modifier.height(10.dp))
                ToggleRow("Flautita al respirar", "Una nota suave en cada inhalación y exhalación de tus sesiones", data.breathFlute) {
                    Store.setBreathFlute(it)
                    if (it) Sounds.play("breath_in")
                }
            }
            SectionTitle("Vibración")
            ToggleRow("Vibración háptica", "Al tocar a Tokkip, los botones y al cambiar de pestaña (también en el reloj)", data.haptics) {
                Store.setHaptics(it)
                mx.loperstack.tokkip.character.Haptics.enabled = it
                if (it) mx.loperstack.tokkip.character.Haptics.click(context)
            }
            if (data.haptics) {
                Spacer(Modifier.height(6.dp))
                HapticStrength(data.hapticStrength)
            }
            Spacer(Modifier.height(10.dp))
            ToggleRow("Respirar con vibración", "Pulsos que crecen al inhalar y se apagan al exhalar, en el teléfono", data.breathHaptics) {
                Store.setBreathHaptics(it)
                if (it) mx.loperstack.tokkip.character.Haptics.breath(context, true, 2.0, guide = true)
            }
            SectionTitle("Notificaciones")
            NotificationSettings(data.notify)
            SectionTitle("Copia de seguridad")
            BackupRow()
            NotebookMoveRow()
            SectionTitle("Tus dispositivos")
            Devices(google)
        }
        Spacer(Modifier.height(10.dp))
        GoogleTasksRow(google, connectGoogle, syncGoogle, disconnectGoogle)
        Text(
            "Ver la bienvenida otra vez", color = Ink.VioletSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 28.dp).clickable(onClick = replayWelcome),
        )
        Column(Modifier.padding(horizontal = 24.dp)) {
            SectionTitle("Empezar de cero")
            WipeRow()
        }
    }
}

@Composable
fun ToggleRow(title: String, why: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Label(why, Ink.Dim2)
        }
        Switch(checked = on, onCheckedChange = set, colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet))
    }
}

/** This build's signing fingerprint — what Google Cloud's Android client must have. */
fun signingSha1(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
    val cert = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray() ?: return "?"
    java.security.MessageDigest.getInstance("SHA-1").digest(cert).joinToString(":") { "%02X".format(it) }
}.getOrDefault("?")

/** The two values for the Android OAuth client, with copy buttons. */
@Composable
fun GoogleSetupHelp() {
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val sha1 = remember { signingSha1(context) }
    @Composable
    fun Value(label: String, value: String) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Squircle(14.dp)).background(Ink.Card)
                .clickable {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(value))
                    android.widget.Toast.makeText(context, "$label copiado", android.widget.Toast.LENGTH_SHORT).show()
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Label(label, Ink.Dim2, 11.sp, FontWeight.Bold)
                Text(value, fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Ink.Text)
            }
            Text("Copiar", color = Ink.VioletSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Label("En Google Cloud → Credenciales → ID de cliente OAuth → Android:", Ink.Dim, 12.sp)
        Value("Nombre del paquete", context.packageName)
        Value("Huella SHA-1", sha1)
    }
}


/** Auto (the season's, like Tokkip PC) or any one of the wardrobe. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OutfitChips(current: String) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill("Temporada", current == "auto", Icons.Rounded.CalendarMonth) { Store.setOutfit("auto") }
        for (o in mx.loperstack.tokkip.character.Outfit.entries) {
            val icon = when (o) {
                mx.loperstack.tokkip.character.Outfit.NONE -> Icons.Rounded.Block
                mx.loperstack.tokkip.character.Outfit.PARTY -> Icons.Rounded.Celebration
                mx.loperstack.tokkip.character.Outfit.CROWN -> Icons.Rounded.EmojiEvents
                mx.loperstack.tokkip.character.Outfit.BEANIE -> Icons.Rounded.Checkroom
                mx.loperstack.tokkip.character.Outfit.HEARTS -> Icons.Rounded.Favorite
                mx.loperstack.tokkip.character.Outfit.SPRING -> Icons.Rounded.LocalFlorist
                mx.loperstack.tokkip.character.Outfit.SUNGLASSES -> Icons.Rounded.WbSunny
                mx.loperstack.tokkip.character.Outfit.STRAW -> Icons.Rounded.BeachAccess
                mx.loperstack.tokkip.character.Outfit.TRICOLOR -> Icons.Rounded.Flag
                mx.loperstack.tokkip.character.Outfit.WITCH -> Icons.Rounded.AutoFixHigh
                mx.loperstack.tokkip.character.Outfit.FLOWERS -> Icons.Rounded.Spa
                mx.loperstack.tokkip.character.Outfit.SCARF -> Icons.Rounded.Air
                mx.loperstack.tokkip.character.Outfit.ANTLERS -> Icons.Rounded.Forest
                mx.loperstack.tokkip.character.Outfit.SANTA -> Icons.Rounded.AcUnit
                mx.loperstack.tokkip.character.Outfit.GLASSES -> Icons.Rounded.Visibility
                mx.loperstack.tokkip.character.Outfit.BOW -> Icons.Rounded.CardGiftcard
                mx.loperstack.tokkip.character.Outfit.HEADPHONES -> Icons.Rounded.Headphones
            }
            Pill(o.label, current == o.name, icon) { Store.setOutfit(o.name) }
        }
    }
}

/** Ajustes → Vibración → Intensidad: scales every tap, thud and purr (phone and watch). */
@Composable
fun HapticStrength(current: Float) {
    val context = LocalContext.current
    var v by remember { mutableStateOf(current) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Intensidad", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 12.dp))
        androidx.compose.material3.Slider(
            value = v, onValueChange = { v = it }, valueRange = 0.2f..1f, steps = 7,
            onValueChangeFinished = { Store.setHapticStrength(v); mx.loperstack.tokkip.character.Haptics.thud(context) },
            modifier = Modifier.weight(1f),
        )
        Text(
            when { v < 0.4f -> "Suave"; v < 0.75f -> "Media"; else -> "Fuerte" },
            fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** Ajustes → Copia de seguridad: all of Tokkip in one .zip, and back. */
@Composable
fun BackupRow() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var confirm by remember { mutableStateOf<android.net.Uri?>(null) }
    fun toast(t: String) = android.widget.Toast.makeText(context, t, android.widget.Toast.LENGTH_LONG).show()
    val save = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) scope.launch {
            val got = withContext(Dispatchers.IO) { mx.loperstack.tokkip.data.Backup.export(context, uri) }
            toast(if (got != null) "Copia guardada ✓ ${got.text()}" else "No se pudo guardar la copia")
            if (got != null) mx.loperstack.tokkip.character.Haptics.success(context)
        }
    }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) confirm = uri }
    Label("Todo Tokkip en un archivo .zip: ajustes, tareas, diario con fotos, ánimo, sueño, respiraciones y rachas. Guárdalo en Drive o donde quieras.", Ink.Dim2, 12.sp)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ExpressiveButton("Crear copia", icon = Icons.Rounded.Backup) {
            val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            save.launch("Tokkip-copia-$day.zip")
        }
        ExpressiveButton("Restaurar", icon = Icons.Rounded.SettingsBackupRestore, container = Ink.CardHigh, content = Ink.Text) {
            open.launch(arrayOf("application/zip", "application/octet-stream"))
        }
    }
    confirm?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("¿Restaurar la copia?") },
            text = { Text("Lo que tienes ahora en Tokkip se reemplaza por lo de la copia.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        val got = withContext(Dispatchers.IO) { mx.loperstack.tokkip.data.Backup.restore(context, uri) }
                        toast(if (got != null) "Copia restaurada ✓ ${got.text()}" else "Ese archivo no es una copia de Tokkip")
                        if (got != null) mx.loperstack.tokkip.character.Haptics.success(context)
                    }
                }) { Text("Restaurar") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = null }) { Text("Cancelar") } },
        )
    }
}

/** Ajustes → Empezar de cero: everything erased, after a copy if you like and typing BORRAR. */
@Composable
fun WipeRow() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var wiping by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) scope.launch {
            val got = withContext(Dispatchers.IO) { mx.loperstack.tokkip.data.Backup.export(context, uri) }
            saved = got != null
            android.widget.Toast.makeText(context, if (got != null) "Copia guardada ✓ ${got.text()}" else "No se pudo guardar la copia", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    Label("Borra todo lo que Tokkip guarda en este teléfono: diario y fotos, rachas, tareas, sueño, respiraciones, ajustes y su crecimiento. Queda como recién instalado.", Ink.Dim2, 12.sp)
    Spacer(Modifier.height(10.dp))
    ExpressiveButton("Restablecer Tokkip", icon = Icons.Rounded.DeleteForever, container = Color(0x33F4505E), content = Color(0xFFF4505E)) {
        typed = ""; saved = false; asking = true
    }
    if (asking) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!wiping) asking = false },
            icon = { androidx.compose.material3.Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = Color(0xFFF4505E)) },
            title = { Text("¿Borrar todo?") },
            text = {
                Column {
                    Text("Esto no se puede deshacer. Tus tareas de Google Tasks siguen en Google; lo demás solo existe aquí.")
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedButton(onClick = {
                        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                        save.launch("Tokkip-copia-$day.zip")
                    }, enabled = !wiping) {
                        androidx.compose.material3.Icon(if (saved) Icons.Rounded.CheckCircle else Icons.Rounded.Backup, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (saved) "Copia guardada" else "Guardar una copia antes")
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Escribe BORRAR para confirmar", fontSize = 13.sp, color = Ink.Dim2)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = typed, onValueChange = { typed = it.take(10) }, singleLine = true, enabled = !wiping,
                        placeholder = { Text("BORRAR") },
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = typed.trim().equals("BORRAR", ignoreCase = true) && !wiping,
                    onClick = {
                        wiping = true
                        mx.loperstack.tokkip.character.Haptics.thud(context)
                        scope.launch(Dispatchers.IO) { mx.loperstack.tokkip.data.Wipe.everything(context) }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Color(0xFFF4505E)),
                ) { Text(if (wiping) "Borrando…" else "Borrar todo") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = false }, enabled = !wiping) { Text("Cancelar") } },
        )
    }
}

private val ACCENTS = listOf(0xFF3F9E9A, 0xFFE46F5C, 0xFF8A6CD1, 0xFF4C9A5F, 0xFF3F72C2, 0xFFC99A2E, 0xFFD2577F, 0xFF2BB3C0)

/** Ajustes → Tokkip: species (the app icon follows it), skin and accent colour. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TokkipPicker(data: AppData) {
    val context = LocalContext.current
    Label("Especie · el ícono de la app cambia con ella", Ink.Dim2, 12.sp)
    Spacer(Modifier.height(8.dp))
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (sp in mx.loperstack.tokkip.character.Species.entries) {
            val icon = when (sp) {
                mx.loperstack.tokkip.character.Species.CONEJO -> Icons.Rounded.CrueltyFree
                mx.loperstack.tokkip.character.Species.GATO -> Icons.Rounded.Pets
                mx.loperstack.tokkip.character.Species.PERRO -> Icons.Rounded.Pets
                mx.loperstack.tokkip.character.Species.ROBOT -> Icons.Rounded.SmartToy
                mx.loperstack.tokkip.character.Species.MAPACHE -> Icons.Rounded.Pets
            }
            Pill(sp.label, data.species == sp.name, icon) { Store.setTokkip(species = sp.name); mx.loperstack.tokkip.character.Haptics.success(context) }
        }
    }
    Spacer(Modifier.height(12.dp))
    Label("Piel", Ink.Dim2, 12.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (sk in mx.loperstack.tokkip.character.Skin.entries) {
            Pill(sk.label, data.skin == sk.name, if (sk == mx.loperstack.tokkip.character.Skin.ENSUENO) Icons.Rounded.AutoAwesome else Icons.Rounded.Circle) { Store.setTokkip(skin = sk.name) }
        }
    }
    Spacer(Modifier.height(12.dp))
    TokkipColors(data)
}

private val FACE_INKS = listOf(0xFF1B1616, 0xFF3A2E2C, 0xFF16807C, 0xFF3F72C2, 0xFF6D4BB8, 0xFFC2416B, 0xFF2F6B3A, 0xFF8A5A2B)
private val CHEEKS = listOf(0xFFF0788C, 0xFFF7A1B5, 0xFFFF9A76, 0xFFF4B860, 0xFFC4A1F0, 0xFF8FD0F0, 0xFF9BE0B0, 0xFFE0E0E0)

/** Every one of Tokkip's colours: accent (ears, tail), eyes and mouth, cheeks. The body has its own picker above. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TokkipColors(data: AppData) {
    val context = LocalContext.current
    var part by remember { mutableStateOf(0) }
    val parts = listOf("Acento", "Ojos y boca", "Mejillas")
    Label("Colores", Ink.Dim2, 12.sp)
    Spacer(Modifier.height(8.dp))
    PillRow { parts.forEachIndexed { i, n -> Pill(n, part == i) { part = i } } }
    Spacer(Modifier.height(10.dp))
    val (palette, current, auto) = when (part) {
        0 -> Triple(ACCENTS, data.accent, false)
        1 -> Triple(FACE_INKS, data.faceInk, true)
        else -> Triple(CHEEKS, data.cheek, true)
    }
    fun pick(c: Long) {
        mx.loperstack.tokkip.character.Haptics.tick(context)
        when (part) { 0 -> Store.setTokkip(accent = c); 1 -> Store.setFaceColors(faceInk = c); else -> Store.setFaceColors(cheek = c) }
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (auto) Pill("Auto", current == 0L, Icons.Rounded.AutoAwesome) { pick(0L) }
        for (c in palette) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                    .border(3.dp, if (current == c) Ink.Text else Ink.Hairline, CircleShape)
                    .clickable { pick(c) },
            )
        }
    }
    // Any colour at all: hue, intensity and brightness.
    var custom by remember(part) { mutableStateOf(false) }
    Spacer(Modifier.height(8.dp))
    Text(
        if (custom) "Ocultar colores a tu gusto" else "A tu gusto…", color = Ink.VioletSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.clickable { custom = !custom }.padding(vertical = 6.dp),
    )
    if (custom) {
        val start = remember(part) { hsv(Color(if (current == 0L) palette.first() else current)) }
        var hue by remember(part) { mutableStateOf(start[0]) }
        var sat by remember(part) { mutableStateOf(start[1]) }
        var value by remember(part) { mutableStateOf(start[2]) }
        fun apply() = pick(argbOf(fromHsv(hue, sat, value)))
        Label("Tono", Ink.Dim, 12.sp, FontWeight.Bold)
        GradientSlider(hue / 360f, Brush.horizontalGradient((0..6).map { fromHsv(it * 60f, 1f, 1f) }), fromHsv(hue, 1f, 1f)) { hue = it * 360f; apply() }
        Spacer(Modifier.height(8.dp))
        Label("Intensidad", Ink.Dim, 12.sp, FontWeight.Bold)
        GradientSlider(sat, Brush.horizontalGradient(listOf(fromHsv(hue, 0f, value), fromHsv(hue, 1f, value))), fromHsv(hue, sat, value)) { sat = it; apply() }
        Spacer(Modifier.height(8.dp))
        Label("Brillo", Ink.Dim, 12.sp, FontWeight.Bold)
        GradientSlider(value, Brush.horizontalGradient(listOf(Color.Black, fromHsv(hue, sat, 1f))), fromHsv(hue, sat, value)) { value = it; apply() }
    }
}

/**
 * The Cuaderno moved out to its own app, Tokkip Notas. While Tokkip still has
 * notes from it, this hands them over (Tokkip Notas brings them in as pages)
 * and, once they're there, lets you clear them from here.
 */
@Composable
fun NotebookMoveRow() {
    val context = LocalContext.current
    val nb by mx.loperstack.tokkip.data.Notebook.index.collectAsState()
    if (nb.notes.isEmpty() && nb.projects.isEmpty()) return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var clearing by remember { mutableStateOf(false) }
    SectionTitle("Tu Cuaderno se mudó")
    Label(
        "Las notas y proyectos ahora viven en Tokkip Notas, su propia app. Aquí quedan ${nb.notes.size} notas y ${nb.projects.size} proyectos: pásalos y luego bórralos de Tokkip.",
        Ink.Dim2, 12.sp,
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ExpressiveButton("Pasar a Tokkip Notas", icon = Icons.Rounded.Send) {
            scope.launch {
                val uri = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
                        val f = java.io.File(dir, "cuaderno.json")
                        f.writeText(mx.loperstack.tokkip.data.Notebook.exportJson().toString())
                        androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", f)
                    }.getOrNull()
                }
                if (uri == null) {
                    android.widget.Toast.makeText(context, "No se pudo preparar el Cuaderno", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val intent = android.content.Intent("mx.loperstack.tokkipnotas.IMPORT_TOKKIP")
                    .setPackage("mx.loperstack.tokkipnotas")
                    .setDataAndType(uri, "application/json")
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(intent) }.onFailure {
                    android.widget.Toast.makeText(context, "Primero instala Tokkip Notas", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        ExpressiveButton("Borrar de aquí", container = Ink.CardHigh, content = Ink.Red) { clearing = true }
    }
    if (clearing) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("¿Ya están en Tokkip Notas?") },
            text = { Text("Se borran de Tokkip. Si todavía no los pasas, se pierden (salvo que estén en una copia .zip).") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    clearing = false
                    scope.launch { withContext(Dispatchers.IO) { mx.loperstack.tokkip.data.Notebook.wipe() } }
                }) { Text("Borrar", color = Ink.Red) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { clearing = false }) { Text("Cancelar") } },
        )
    }
}
