// Cardio: running, walking or cycling with GPS. Pick the sport, and Tokkip
// runs with you while your route draws itself; time, distance, pace and each
// kilometre as it's done. At the end, the route is drawn again, and the
// session (with its route and distance) goes to Health Connect.

package mx.loperstack.tokkipfit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import mx.loperstack.tokkipfit.data.Tile
import mx.loperstack.tokkipfit.data.Tiles
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.loperstack.tokkip.character.Exercise
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.fit.Done
import mx.loperstack.tokkip.fit.Sport
import mx.loperstack.tokkip.fit.cardioKcal
import mx.loperstack.tokkip.fit.clock
import mx.loperstack.tokkip.fit.pace
import mx.loperstack.tokkip.fit.speed
import mx.loperstack.tokkipfit.data.CardioLive
import mx.loperstack.tokkipfit.data.CardioService
import mx.loperstack.tokkipfit.data.HealthData
import mx.loperstack.tokkipfit.data.Prefs
import kotlin.math.max

fun sportIcon(s: Sport): ImageVector = when (s) {
    Sport.RUN -> Icons.AutoMirrored.Rounded.DirectionsRun
    Sport.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
    Sport.BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike
}

@Composable
fun CardioScreen(bottom: PaddingValues, onStart: (Sport) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val past = remember { Prefs.workouts().filter { it.meters > 0 } }
    var pick by remember { mutableStateOf<Sport?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) pick?.let(onStart)
    }
    fun go(s: Sport) {
        pick = s
        val want = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) onStart(s)
        else ask.launch(want.toTypedArray())
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottom.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.statusBarsPadding(),
    ) {
        item {
            Appear(0) {
                Column(Modifier.padding(top = 18.dp)) {
                    Title("Cardio", size = 44.sp)
                    Text(
                        if (past.isEmpty()) "Con GPS: tu ruta, tu ritmo y cada kilómetro." else "Llevas ${String.format(java.util.Locale.US, "%.1f", past.sumOf { it.meters } / 1000)} km con Tokkip.",
                        style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(Sport.entries) { i, s ->
            Appear(1 + i) {
                val bg = listOf(cs.primaryContainer, cs.secondaryContainer, cs.tertiaryContainer)[i]
                val fg = listOf(cs.onPrimaryContainer, cs.onSecondaryContainer, cs.onTertiaryContainer)[i]
                FitCard(color = bg, onClick = { go(s) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShapeIcon(sportIcon(s), fg, bg, 52.dp, mx.loperstack.tokkip.character.MShapes.Cookie9)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.label, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFont), color = fg)
                            Text(
                                when (s) { Sport.RUN -> "Ritmo por kilómetro y aviso en cada uno"; Sport.WALK -> "Para tus caminatas, largas o cortas"; Sport.BIKE -> "Velocidad y distancia en bici" },
                                style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.85f),
                            )
                        }
                        Icon(Icons.Rounded.PlayArrow, "Empezar", tint = fg, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
        if (past.isNotEmpty()) {
            item { Text("Lo último", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp)) }
            itemsIndexed(past.take(6)) { _, d ->
                val route = remember(d.at) { Prefs.route(d.at) }
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(64.dp).glass(RoundedCornerShape(16.dp))) {
                        RouteDrawing(route, Modifier.fillMaxSize(), LocalFit.current.steps, 1f, 3.dp, pad = 8.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${String.format(java.util.Locale.US, "%.2f", d.meters / 1000)} km · ${clock(d.seconds.toLong())}" +
                                if (d.kind == "bike") "" else " · ${pace(d.seconds / (d.meters / 1000))}/km",
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Where the route sits in a box: Web Mercator (so it lines up with map tiles), fit and centred. */
internal class RouteFrame(route: List<Pair<Double, Double>>, val w: Float, val h: Float, pad: Float) {
    val xs = route.map { (it.second + 180.0) / 360.0 }
    val ys = route.map { val r = Math.toRadians(it.first.coerceIn(-85.0, 85.0)); (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 }
    val minX = xs.min(); val minY = ys.min()
    // At least ~150 m across, so a short route isn't blown up.
    private val span = max(max(xs.max() - minX, ys.max() - minY), 4e-6)
    val scale = (min(w, h) - 2 * pad).coerceAtLeast(1f) / span
    val ox = (w - (xs.max() - minX) * scale) / 2
    val oy = (h - (ys.max() - minY) * scale) / 2
    fun at(i: Int) = Offset((ox + (xs[i] - minX) * scale).toFloat(), (oy + (ys[i] - minY) * scale).toFloat())

    /** The tile zoom where a tile is drawn 256–512 px wide, and the tiles that cover the box. */
    val zoom = floor(log2(scale / 256.0)).toInt().coerceIn(2, 19)
    val tiles: List<Tile> = run {
        val n = 1 shl zoom
        val x0 = minX - ox / scale; val x1 = x0 + w / scale
        val y0 = minY - oy / scale; val y1 = y0 + h / scale
        val tx = (floor(x0 * n).toInt()..floor(x1 * n).toInt()).filter { it in 0 until n }
        val ty = (floor(y0 * n).toInt()..floor(y1 * n).toInt()).filter { it in 0 until n }
        tx.flatMap { x -> ty.map { y -> Tile(zoom, x, y) } }.take(40)
    }

    fun tileAt(t: Tile): Pair<IntOffset, IntSize> {
        val n = (1 shl t.z).toDouble()
        val left = ox + (t.x / n - minX) * scale
        val top = oy + (t.y / n - minY) * scale
        val size = scale / n
        return IntOffset(left.roundToInt(), top.roundToInt()) to IntSize((left + size).roundToInt() - left.roundToInt(), (top + size).roundToInt() - top.roundToInt())
    }
}

@Composable
fun RouteDrawing(
    route: List<Pair<Double, Double>>, modifier: Modifier, color: Color, upTo: Float,
    width: androidx.compose.ui.unit.Dp = 6.dp, dot: Boolean = false, pad: androidx.compose.ui.unit.Dp = 0.dp, map: Boolean = false,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val pulse by rememberInfiniteTransition(label = "dot").animateFloat(0.6f, 1f, infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    var box by remember { mutableStateOf(IntSize.Zero) }
    val padPx = with(LocalDensity.current) { pad.toPx() }
    val frame = remember(route, box, padPx) { if (route.size < 2 || box.width == 0) null else RouteFrame(route, box.width.toFloat(), box.height.toFloat(), padPx) }
    val wanted = if (map && frame != null) frame.tiles else emptyList()
    // Tiles come in as they load; the ones already here show at once.
    val tiles by produceState(wanted.associateWith { Tiles.cached(it) }.filterValues { it != null }.mapValues { it.value!! }, wanted) {
        val got = value.toMutableMap()
        for (t in wanted) if (t !in got) Tiles.get(context, t)?.let { got[t] = it; value = got.toMap() }
    }
    val dark = cs.surface.luminance() < 0.5f
    val filter = remember(dark) {
        ColorFilter.colorMatrix(
            if (dark) ColorMatrix(floatArrayOf(-0.55f, -0.3f, -0.15f, 0f, 235f, -0.25f, -0.6f, -0.15f, 0f, 235f, -0.25f, -0.3f, -0.45f, 0f, 235f, 0f, 0f, 0f, 1f, 0f))
            else ColorMatrix().apply { setToSaturation(0.35f) },
        )
    }
    Box(modifier.onSizeChanged { box = it }) {
        Canvas(Modifier.fillMaxSize()) {
            val f = frame ?: return@Canvas
            for ((t, img) in tiles) {
                if (t.z != f.zoom) continue
                val (o, s) = f.tileAt(t)
                drawImage(img, dstOffset = o, dstSize = s, alpha = 0.9f, colorFilter = filter)
            }
            val path = Path().apply { moveTo(f.at(0).x, f.at(0).y); for (i in 1 until route.size) lineTo(f.at(i).x, f.at(i).y) }
            val shown = if (upTo >= 1f) path else Path().also { out ->
                val m = PathMeasure().apply { setPath(path, false) }
                m.getSegment(0f, m.length * upTo.coerceIn(0f, 1f), out, true)
            }
            drawPath(shown, color.copy(alpha = 0.25f), style = Stroke(width.toPx() * 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(shown, color, style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(Color.White, width.toPx() * 1.1f, f.at(0))
            drawCircle(color, width.toPx() * 0.7f, f.at(0))
            if (dot) {
                val end = f.at(route.lastIndex)
                drawCircle(color.copy(alpha = 0.3f), width.toPx() * 3f * pulse, end)
                drawCircle(color, width.toPx() * 1.3f, end)
            }
        }
        if (tiles.isNotEmpty()) {
            Text(
                "© OpenStreetMap", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(cs.surface.copy(alpha = 0.7f)).padding(horizontal = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardioLiveScreen(sport: Sport, onClose: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val fit = LocalFit.current
    val scope = rememberCoroutineScope()
    var finished by remember { mutableStateOf<Done?>(null) }
    var route by remember { mutableStateOf(emptyList<Pair<Double, Double>>()) }
    var lastKm by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (!CardioLive.running) { CardioLive.reset(sport); CardioService.start(context); Sounds.play("focus_start"); Haptics.success(context) }
    }
    // The route redraws as you go; a sound and a buzz at each kilometre.
    LaunchedEffect(CardioLive.points) { route = CardioLive.track.points.map { it.lat to it.lon } }
    LaunchedEffect(CardioLive.meters) {
        val km = (CardioLive.meters / 1000).toInt()
        if (km > lastKm) { lastKm = km; Sounds.play("level_up"); Haptics.success(context) }
    }
    LaunchedEffect(Unit) { while (true) { delay(1000); CardioLive.tick() } }

    fun finish() {
        CardioLive.tick()
        val secs = (CardioLive.moving / 1000).toInt()
        val m = CardioLive.meters
        val points = CardioLive.track.points.toList()
        CardioLive.running = false
        CardioService.stop(context)
        if (secs < 20 && m < 50) { onClose(); return }
        val d = Done(System.currentTimeMillis(), sport.id, sport.label, secs, cardioKcal(sport, m, secs.toDouble()), kind = sport.id, meters = m)
        Prefs.addWorkout(d)
        Prefs.saveRoute(d.at, points)
        finished = d
        Sounds.play("level_up"); Haptics.success(context)
        scope.launch { runCatching { HealthData.write(context.applicationContext, d, points) } }
    }

    val done = finished
    Box(Modifier.fillMaxSize()) {
        if (done != null) {
            CardioSummary(done, route, onClose)
        } else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(sportIcon(sport), null, tint = fit.steps)
                Spacer(Modifier.width(8.dp))
                Text(sport.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Icon(if (CardioLive.hasFix) Icons.Rounded.GpsFixed else Icons.Rounded.GpsNotFixed, null, tint = if (CardioLive.hasFix) fit.minutes else cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (CardioLive.hasFix) "GPS listo" else "Buscando GPS…", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 10.dp).glass(RoundedCornerShape(32.dp)), contentAlignment = Alignment.Center) {
                if (route.size >= 2) RouteDrawing(route, Modifier.fillMaxSize(), fit.steps, 1f, 6.dp, dot = true, pad = 28.dp, map = Prefs.map)
                else Text("Tu ruta aparece aquí en cuanto te muevas", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Tokkip(
                    Modifier.align(Alignment.BottomEnd).padding(10.dp).size(110.dp, 98.dp), color = Color(Prefs.color),
                    exercise = if (CardioLive.paused) null else Exercise.RUN,
                    tempo = when (sport) { Sport.WALK -> 0.55f; Sport.RUN -> 1.1f; Sport.BIKE -> 0.9f },
                    face = if (CardioLive.paused) TokkipEngine.Eye.DOT else null,
                )
            }
            val secs = CardioLive.moving / 1000.0
            Text(clock(CardioLive.moving / 1000), style = MaterialTheme.typography.displayLarge.copy(fontFamily = DisplayFont, fontSize = 64.sp), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat3(Modifier.weight(1f), String.format(java.util.Locale.US, "%.2f", CardioLive.meters / 1000), "km", fit.steps)
                if (sport == Sport.BIKE) Stat3(Modifier.weight(1f), speed(CardioLive.meters, secs).removeSuffix(" km/h"), "km/h", fit.minutes)
                else Stat3(Modifier.weight(1f), pace(if (CardioLive.meters > 20) secs / (CardioLive.meters / 1000) else 0.0), "ritmo /km", fit.minutes)
                Stat3(Modifier.weight(1f), "${cardioKcal(sport, CardioLive.meters, secs).toInt()}", "kcal", fit.kcal)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(cs.primary)
                        .combinedClickable(onClick = { CardioLive.tick(); CardioLive.paused = !CardioLive.paused; Sounds.play(if (CardioLive.paused) "close" else "pop"); Haptics.click(context) }),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (CardioLive.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (CardioLive.paused) "Seguir" else "Pausa", tint = cs.onPrimary, modifier = Modifier.size(40.dp)) }
                Spacer(Modifier.width(18.dp))
                // Finishing needs a long press, so a bump in your pocket doesn't end it.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(cs.errorContainer)
                            .combinedClickable(onClick = { Sounds.play("tick"); Haptics.tick(context) }, onLongClick = { finish() }),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Stop, "Terminar (mantén presionado)", tint = cs.onErrorContainer, modifier = Modifier.size(30.dp)) }
                    Text("Mantén para terminar", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CardioSummary(d: Done, route: List<Pair<Double, Double>>, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fit = LocalFit.current
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(2200)) }
    var jump by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { repeat(3) { delay(900); jump++ } }
    val sport = Sport.of(d.kind)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Title("¡${sport.verb}!", size = 42.sp)
        Text(String.format(java.util.Locale.US, "%.2f km en %s", d.meters / 1000, clock(d.seconds.toLong())), style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp).glass(RoundedCornerShape(32.dp)), contentAlignment = Alignment.Center) {
            if (route.size >= 2) RouteDrawing(route, Modifier.fillMaxSize(), fit.steps, draw.value, 6.dp, pad = 28.dp, map = Prefs.map)
            Tokkip(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(120.dp, 106.dp), color = Color(Prefs.color), mood = Mood.HAPPY, face = TokkipEngine.Eye.STAR, dancing = true, jump = jump)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat3(Modifier.weight(1f), String.format(java.util.Locale.US, "%.2f", d.meters / 1000), "km", fit.steps)
            if (sport == Sport.BIKE) Stat3(Modifier.weight(1f), speed(d.meters, d.seconds.toDouble()).removeSuffix(" km/h"), "km/h", fit.minutes)
            else Stat3(Modifier.weight(1f), pace(d.seconds / (d.meters / 1000).coerceAtLeast(0.001)), "ritmo /km", fit.minutes)
            Stat3(Modifier.weight(1f), "${d.kcal.toInt()}", "kcal", fit.kcal)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Listo", style = MaterialTheme.typography.titleMedium) }
    }
}
