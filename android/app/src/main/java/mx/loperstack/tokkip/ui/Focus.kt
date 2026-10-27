// Enfoque's extras: what's playing (with its controls), your own block
// lengths, and the AOD mode — a black screen with a dim clock that drifts a
// little every minute, so an OLED panel can show it for long without wear.

package mx.loperstack.tokkip.ui

import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.graphicsLayer
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.PolygonShape

import mx.loperstack.tokkip.character.Squircle
import android.view.WindowManager
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.delay
import mx.loperstack.tokkip.data.NowPlaying
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.wellness.Phase
import mx.loperstack.tokkip.wellness.Pomodoro
import mx.loperstack.tokkip.wellness.PomodoroConfig
import mx.loperstack.tokkip.wellness.PomodoroScheduler
import mx.loperstack.tokkip.wellness.clock
import mx.loperstack.tokkip.wellness.left
import kotlin.random.Random

/** Listens to media sessions while the screen shows. */
@Composable
fun rememberNowPlaying(): NowPlaying.Track? {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        NowPlaying.start(context)
        onDispose { NowPlaying.stop() }
    }
    return NowPlaying.track.collectAsState().value
}

/** The track with its cover and controls; or a way to allow it. */
@Composable
fun NowPlayingCard(track: NowPlaying.Track?) {
    val context = LocalContext.current
    if (!NowPlaying.granted(context)) {
        Row(
            Modifier.fillMaxWidth().clip(Squircle(24.dp)).background(Ink.Card)
                .clickable { NowPlaying.openSettings(context) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShapeIcon(Icons.Rounded.MusicNote, shape = MShapes.Cookie12, size = 44.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Ver lo que suena", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Label("Permite a Tokkip ver la música (acceso a notificaciones → Tokkip)", Ink.Dim2, 12.sp)
            }
            Text("Permitir", color = Ink.VioletSoft, fontWeight = FontWeight.Bold)
        }
        return
    }
    if (track == null) return
    Row(
        Modifier.fillMaxWidth().clip(Squircle(24.dp)).background(Ink.Card).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The cover in a turning cookie, like an expressive player.
        val spin by androidx.compose.animation.core.rememberInfiniteTransition(label = "art").animateFloat(
            0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(20_000, easing = androidx.compose.animation.core.LinearEasing)), label = "r",
        )
        // Tap the cover (or the title): straight to the app it's playing in.
        Box(
            Modifier.size(60.dp).graphicsLayer { rotationZ = if (track.playing) spin else 0f }
                .clip(PolygonShape(MShapes.Cookie9)).background(Ink.VioletDeep)
                .clickable { NowPlaying.openSource(context) },
            contentAlignment = Alignment.Center,
        ) {
            val art = track.art
            if (art != null) Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Rounded.MusicNote, null, tint = Ink.VioletSoft, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.padding(horizontal = 12.dp).weight(1f).clickable { NowPlaying.openSource(context) }) {
            Text(track.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(track.artist, track.app).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.sp, color = Ink.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CircleButton(Icons.Rounded.SkipPrevious, "Anterior", size = 38.dp, container = Color.Transparent) { NowPlaying.previous() }
        ExpressiveButton(
            "", icon = if (track.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            modifier = Modifier.padding(horizontal = 2.dp), container = Ink.VioletSoft, content = Ink.OnViolet,
        ) { NowPlaying.playPause() }
        CircleButton(Icons.Rounded.SkipNext, "Siguiente", size = 38.dp, container = Color.Transparent) { NowPlaying.next() }
    }
}

// ── Personalizar ──────────────────────────────────────────────────────────────

private val POMODORO_PRESETS = listOf(
    "Clásico 25/5" to PomodoroConfig(25, 5, 15, 4),
    "Profundo 50/10" to PomodoroConfig(50, 10, 20, 3),
    "Corto 15/3" to PomodoroConfig(15, 3, 10, 4),
    "Ultradiano 90/20" to PomodoroConfig(90, 20, 30, 2),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PomodoroSettings(current: PomodoroConfig) {
    var c by remember { mutableStateOf(current) }
    fun save(n: PomodoroConfig) { c = n; Store.setPomodoroConfig(n) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        ScreenTitle("Tus bloques", size = 28.sp)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((name, preset) in POMODORO_PRESETS) Segment(name, c == preset) { save(preset) }
        }
        Spacer(Modifier.height(16.dp))
        @Composable
        fun row(label: String, value: Int, range: IntRange, step: Int, unit: String, set: (Int) -> Unit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp))
                Slider(
                    value = value.toFloat(), onValueChange = { set((it / step).toInt() * step) },
                    valueRange = range.first.toFloat()..range.last.toFloat(), modifier = Modifier.weight(1f),
                    onValueChangeFinished = { Store.setPomodoroConfig(c) },
                )
                Text("$value $unit", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
            }
        }
        row("Enfoque", c.focusMin, 5..120, 5, "min") { c = c.copy(focusMin = it.coerceAtLeast(5)) }
        row("Descanso", c.breakMin, 1..30, 1, "min") { c = c.copy(breakMin = it.coerceAtLeast(1)) }
        row("Descanso largo", c.longBreakMin, 5..45, 5, "min") { c = c.copy(longBreakMin = it.coerceAtLeast(5)) }
        row("Bloques", c.cyclesBeforeLong, 2..8, 1, "") { c = c.copy(cyclesBeforeLong = it.coerceAtLeast(2)) }
        Spacer(Modifier.height(8.dp))
        Label("Un descanso largo cada ${c.cyclesBeforeLong} bloques. Los cambios valen desde el siguiente bloque.", Ink.Dim2, 12.sp)
    }
}

// ── Modo AOD ──────────────────────────────────────────────────────────────────

/** Black, dim, drifting: the block's countdown for an OLED screen left on. Tap to leave. */
@Composable
fun FocusAod(p: Pomodoro, color: Color, track: NowPlaying.Track?, close: () -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let {
                it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.attributes = it.attributes.apply { screenBrightness = 0.04f }
                WindowCompat.getInsetsController(it, view).hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        val now = rememberNow(1000)
        // Burn-in care: everything moves a little each minute.
        var shift by remember { mutableStateOf(0 to 0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000)
                shift = Random.nextInt(-24, 25) to Random.nextInt(-40, 41)
            }
        }
        val dim = Color(0xFF8A8A8A)
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = close), contentAlignment = Alignment.Center) {
            Column(Modifier.offset(shift.first.dp, shift.second.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Tokkip(Modifier.width(110.dp).height(92.dp), color = Color(0xFF3C3C40), mood = if (p.phase == Phase.FOCUS) Mood.FOCUS else Mood.IDLE)
                Spacer(Modifier.height(12.dp))
                Text(if (p.phase == Phase.IDLE) "Sin bloque" else clock(p.left(now)), fontSize = 76.sp, fontWeight = FontWeight.Light, color = dim)
                Text(PomodoroScheduler.phaseTitle(p.phase), fontSize = 16.sp, color = Color(0xFF5A5A5A), fontWeight = FontWeight.SemiBold)
                if (track != null) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "${track.title}${if (track.artist.isBlank()) "" else " — ${track.artist}"}", fontSize = 13.sp, color = Color(0xFF555555),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(260.dp),
                    )
                }
            }
        }
    }
}
