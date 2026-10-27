// The welcome: Tokkip says hello, asks your name, you dress him in your colour,
// pick the theme, see your devices and allow what he needs. Shown once (and
// again from Ajustes).

package mx.loperstack.tokkip.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import mx.loperstack.tokkip.character.MShapes

import mx.loperstack.tokkip.character.Squircle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import mx.loperstack.tokkip.data.AppData
import mx.loperstack.tokkip.data.GoogleTasks
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.character.Sounds

private const val STEPS = 6

@Composable
fun Welcome(data: AppData, google: GoogleTasks.Status, actions: Actions, connectGoogle: () -> Unit, done: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(data.name) }
    val color = Color(data.tokkipColor)
    fun next() {
        Sounds.play("blip")
        if (step < STEPS) step++ else done()
    }
    // A gentle tune the first time: it fades in, and fades out when you're done.
    var music by rememberSaveable { mutableStateOf(data.soundOn) }
    androidx.compose.runtime.DisposableEffect(music) {
        if (music) WelcomeMusic.start(context)
        onDispose { WelcomeMusic.stop() }
    }

    Box(Modifier.fillMaxSize().background(Ink.Ground)) {
        Aurora(color, Modifier.fillMaxSize())
        CircleButton(
            if (music) Icons.Rounded.MusicNote else Icons.Rounded.MusicOff, if (music) "Silenciar la música" else "Poner música",
            Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(16.dp), size = 44.dp,
        ) { music = !music }
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 24.dp)) {
            // Progress: a dot per step, the current one long.
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                for (i in 0..STEPS) {
                    val w by animateDpAsState(if (i == step) 22.dp else 7.dp, label = "dot")
                    val c by animateColorAsState(if (i <= step) Ink.Violet else Ink.Hairline, label = "dotc")
                    Box(Modifier.height(7.dp).width(w).clip(CircleShape).background(c))
                }
            }
            AnimatedContent(
                targetState = step, modifier = Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(380)) { it / 3 * dir } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(320)) { -it / 3 * dir } + fadeOut(tween(200)))
                },
                label = "step",
            ) { s ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (s) {
                        0 -> {
                            Spacer(Modifier.height(40.dp))
                            Tokkip(Modifier.width(240.dp).height(200.dp), color = color, interactive = true, greet = true)
                            Spacer(Modifier.height(24.dp))
                            Title("¡Hola! Soy Tokkip")
                            Body("Tu compañero en el reloj, el teléfono, la tablet y la laptop. Vamos a dejarlo a tu gusto: toma un minuto.")
                        }
                        1 -> {
                            Tokkip(Modifier.width(170.dp).height(140.dp), color = color, face = if (name.isBlank()) null else TokkipEngine.Eye.HAPPY)
                            Spacer(Modifier.height(16.dp))
                            Title("¿Cómo te llamas?")
                            Body("Así te saludo en el teléfono, el reloj y los widgets.")
                            Spacer(Modifier.height(18.dp))
                            NameField(name, { name = it })
                        }
                        2 -> {
                            Title("Tu Tokkip, tu color")
                            Body("Elige uno de los 36, el de tu Pixel o cualquiera a tu gusto.")
                            Spacer(Modifier.height(8.dp))
                            ColorPicker(data.tokkipColor, color) { Store.setColor(it) }
                        }
                        3 -> {
                            Tokkip(Modifier.width(150.dp).height(124.dp), color = color)
                            Spacer(Modifier.height(10.dp))
                            Title("El tema")
                            Body("\"Como mi Pixel\" toma los colores de tu fondo de pantalla y cambia con el modo claro u oscuro.")
                            Spacer(Modifier.height(18.dp))
                            ThemeCards(data.theme) { Store.setTheme(it) }
                        }
                        4 -> {
                            Title("Tu mini ecosistema")
                            Body("Tokkip te acompaña en todos tus dispositivos.")
                            Spacer(Modifier.height(14.dp))
                            Devices(google)
                            Spacer(Modifier.height(14.dp))
                            if (google.connected) {
                                IconLabel(Icons.Rounded.CheckCircle, "Google Tasks conectado", Ink.GreenSoft, size = 14.sp)
                            } else {
                                PrimaryButton("Conectar Google Tasks", color = Ink.VioletSoft, textColor = Ink.OnViolet, onClick = connectGoogle)
                                if (google.error.isNotEmpty()) Label(google.error, Ink.Red)
                                Label("Opcional · necesita el paso de android/GOOGLE.md", Ink.Dim2, 12.sp)
                            }
                        }
                        5 -> Permissions()
                        else -> {
                            Spacer(Modifier.height(40.dp))
                            Tokkip(Modifier.width(240.dp).height(200.dp), color = color, jump = actions.jump, face = TokkipEngine.Eye.STAR)
                            Spacer(Modifier.height(24.dp))
                            Title(if (name.isBlank()) "¡Todo listo!" else "¡Todo listo, ${name.trim()}!")
                            Body("Toca a Tokkip cuando quieras. Tres veces seguidas… mejor no.")
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step in 1 until STEPS) {
                    Text(
                        "Atrás", color = Ink.Dim, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).clickable { step-- }.padding(12.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton(
                    when (step) { 0 -> "Empezar"; STEPS -> "Entrar"; 1 -> if (name.isBlank()) "Omitir" else "Seguir"; else -> "Seguir" },
                    color = Ink.VioletSoft, textColor = Ink.OnViolet,
                ) {
                    if (step == 1) Store.setName(name)
                    if (step == STEPS - 1) actions.jump++
                    next()
                }
            }
        }
    }
}

@Composable private fun Title(text: String) =
    Text(text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = Ink.Text)

@Composable private fun Body(text: String) =
    Text(text, fontSize = 16.sp, color = Ink.Dim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Permissions() {
    val context = LocalContext.current
    fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var calendar by remember { mutableStateOf(has(Manifest.permission.READ_CALENDAR) && has(Manifest.permission.WRITE_CALENDAR)) }
    var notify by remember { mutableStateOf(has(Manifest.permission.POST_NOTIFICATIONS)) }
    val askCal = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> calendar = r.values.all { it } }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notify = it }

    Text("Lo que Tokkip necesita", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
    Text("Puedes cambiarlo cuando quieras.", fontSize = 16.sp, color = Ink.Dim, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
    PermissionRow(Icons.Rounded.CalendarMonth, "Tu calendario", "Tu agenda en Inicio y eventos nuevos en Google Calendar", calendar) {
        askCal.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }
    PermissionRow(Icons.Rounded.NotificationsActive, "Avisos", "El Pomodoro y el fin de cada bloque, aunque la app esté cerrada", notify) {
        askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun PermissionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, why: String, granted: Boolean, ask: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(Squircle(20.dp))
            .background(Ink.Card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapeIcon(icon, shape = MShapes.Cookie9, size = 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Label(why, Ink.Dim, 12.sp)
        }
        if (granted) androidx.compose.material3.Icon(Icons.Rounded.CheckCircle, "Listo", tint = Ink.Green, modifier = Modifier.size(26.dp))
        else OutlinedButton(onClick = ask) { Text("Permitir", color = Ink.Text) }
    }
}

/** The welcome tune (tools/make_music.py): loops softly, fades in and out. */
object WelcomeMusic {
    private var player: android.media.MediaPlayer? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var vol = 0f

    fun start(context: Context) {
        if (player != null) return
        val p = runCatching { android.media.MediaPlayer.create(context.applicationContext, mx.loperstack.tokkip.shared.R.raw.welcome_lofi) }.getOrNull() ?: return
        p.isLooping = true
        vol = 0f; p.setVolume(0f, 0f); p.start()
        player = p
        ramp(0.55f)
    }

    fun stop() {
        val p = player ?: return
        player = null
        handler.removeCallbacksAndMessages(null)
        // Fade out over a second, then let go.
        var v = vol
        val step = object : Runnable {
            override fun run() {
                v -= 0.05f
                runCatching { p.setVolume(v.coerceAtLeast(0f), v.coerceAtLeast(0f)) }
                if (v > 0f) handler.postDelayed(this, 50) else runCatching { p.stop(); p.release() }
            }
        }
        handler.post(step)
    }

    private fun ramp(target: Float) {
        val step = object : Runnable {
            override fun run() {
                val p = player ?: return
                vol = (vol + 0.02f).coerceAtMost(target)
                runCatching { p.setVolume(vol, vol) }
                if (vol < target) handler.postDelayed(this, 60)
            }
        }
        handler.post(step)
    }
}
