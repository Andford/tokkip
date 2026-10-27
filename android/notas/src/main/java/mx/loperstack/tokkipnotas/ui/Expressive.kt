// Material 3 Expressive pieces, in the spirit of Pixel apps (and of the
// PixelPlayer look): big wide display titles, rounded Material Symbols set
// inside cookie/clover/sun shapes instead of emoji, pill chips, tonal circle
// buttons, a floating pill navigation bar, buttons that squish when pressed,
// a slowly morphing shape behind Tokkip, and wavy progress lines.

package mx.loperstack.tokkipnotas.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.MorphShape
import mx.loperstack.tokkip.character.PolygonShape
import mx.loperstack.tokkip.character.Squircle
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

// ── Type ──────────────────────────────────────────────────────────────────────

/**
 * Display titles: Google Sans Flex, wide and heavy with rounded ends, read
 * straight from the Pixel's own font file (Android 16); elsewhere the regular
 * typeface, extra bold.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val DisplayFont: FontFamily by lazy {
    val file = listOf("/system/fonts", "/product/fonts", "/system/product/fonts")
        .flatMap { d -> File(d).listFiles()?.toList() ?: emptyList() }
        .firstOrNull { it.name.startsWith("GoogleSansFlex", ignoreCase = true) && it.name.endsWith(".ttf", true) }
    runCatching {
        if (file != null) FontFamily(
            androidx.compose.ui.text.font.Font(
                file, FontWeight(800),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(800), FontVariation.width(125f), FontVariation.Setting("ROND", 100f),
                ),
            ),
        ) else null
    }.getOrNull() ?: FontFamily.Default
}

/** A screen's big title, PixelPlayer-style ("Your Mix", "Library"). */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, size: TextUnit = 40.sp, color: Color = Ink.Text) {
    Text(
        text, modifier = modifier, color = color,
        style = androidx.compose.material3.MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight(800), fontSize = size, lineHeight = size * 1.05f, letterSpacing = (-0.5).sp),
    )
}

// ── Icons, instead of emoji ───────────────────────────────────────────────────

/** Every icon the app uses, by a stable key (habits store the key). */
object MIcon {
    val habit: Map<String, ImageVector> = linkedMapOf(
        "star" to Icons.Rounded.Star, "water" to Icons.Rounded.WaterDrop, "book" to Icons.Rounded.MenuBook,
        "run" to Icons.Rounded.DirectionsRun, "yoga" to Icons.Rounded.SelfImprovement, "food" to Icons.Rounded.Restaurant,
        "sleep" to Icons.Rounded.Bedtime, "pill" to Icons.Rounded.Medication, "music" to Icons.Rounded.MusicNote,
        "write" to Icons.Rounded.EditNote, "clean" to Icons.Rounded.CleaningServices, "plant" to Icons.Rounded.Eco,
        "walk" to Icons.Rounded.DirectionsWalk, "gym" to Icons.Rounded.FitnessCenter, "fruit" to Icons.Rounded.LunchDining,
        "art" to Icons.Rounded.Palette, "mind" to Icons.Rounded.Psychology, "nophone" to Icons.Rounded.PhonelinkOff,
        "sun" to Icons.Rounded.WbSunny, "thanks" to Icons.Rounded.VolunteerActivism, "money" to Icons.Rounded.Savings,
        "talk" to Icons.Rounded.RecordVoiceOver, "pet" to Icons.Rounded.Pets, "heart" to Icons.Rounded.Favorite,
        "code" to Icons.Rounded.Code, "bike" to Icons.Rounded.DirectionsBike, "coffee" to Icons.Rounded.LocalCafe,
        "game" to Icons.Rounded.SportsEsports,
    )

    fun habitIcon(key: String): ImageVector = habit[key] ?: Icons.Rounded.Star

    val sleepFactor: Map<String, ImageVector> = mapOf(
        "cafe" to Icons.Rounded.LocalCafe, "pantalla" to Icons.Rounded.PhoneAndroid, "cena" to Icons.Rounded.DinnerDining,
        "alcohol" to Icons.Rounded.WineBar, "estres" to Icons.Rounded.Psychology, "ejercicio" to Icons.Rounded.DirectionsRun,
        "siesta" to Icons.Rounded.Hotel, "ruido" to Icons.Rounded.VolumeUp,
    )

    /** Sleep tips carry an icon key. */
    val tip: Map<String, ImageVector> = mapOf(
        "start" to Icons.Rounded.EditNote, "short" to Icons.Rounded.Alarm, "irregular" to Icons.Rounded.Schedule,
        "cafe" to Icons.Rounded.LocalCafe, "pantalla" to Icons.Rounded.PhonelinkOff, "cena" to Icons.Rounded.DinnerDining,
        "alcohol" to Icons.Rounded.WineBar, "estres" to Icons.Rounded.Air, "siesta" to Icons.Rounded.Hotel,
        "ruido" to Icons.Rounded.VolumeOff, "ejercicio" to Icons.Rounded.DirectionsRun, "debt" to Icons.Rounded.Bedtime,
        "good" to Icons.Rounded.Nightlight, "light" to Icons.Rounded.WbSunny, "cool" to Icons.Rounded.AcUnit,
        "dark" to Icons.Rounded.DarkMode, "breathe" to Icons.Rounded.Air, "bed" to Icons.Rounded.Bed,
        "winddown" to Icons.Rounded.PhonelinkOff, "mind" to Icons.Rounded.Psychology, "shower" to Icons.Rounded.Shower,
        "routine" to Icons.Rounded.HourglassBottom, "water" to Icons.Rounded.WaterDrop, "sound" to Icons.Rounded.Headphones,
        "relax" to Icons.Rounded.SelfImprovement,
    )

    fun tipIcon(key: String): ImageVector = tip[key] ?: Icons.Rounded.Lightbulb
}

/**
 * A rounded Material Symbol inside an expressive shape (cookie, clover, sun…),
 * as in Pixel settings. The shape can turn slowly when `spin` is on.
 */
@Composable
fun ShapeIcon(
    icon: ImageVector, modifier: Modifier = Modifier, shape: RoundedPolygon = MShapes.Cookie9,
    container: Color = Ink.VioletDeep, tint: Color = Ink.OnVioletDeep, size: Dp = 44.dp, iconSize: Dp = size * 0.5f,
    spin: Boolean = false,
) {
    val angle = if (spin) {
        val t by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "a")
        t
    } else 0f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer { rotationZ = angle }.clip(PolygonShape(shape)).background(container))
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

// ── Chips and buttons ─────────────────────────────────────────────────────────

/** A pill chip ("SONGS · ALBUMS"): filled when on, tonal when off, with an optional icon. */
@Composable
fun Pill(text: String, on: Boolean, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val bg by animateColorAsState(if (on) Ink.VioletSoft else Ink.CardHigh, label = "pill")
    val fg by animateColorAsState(if (on) Ink.OnViolet else Ink.Text, label = "pillFg")
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squish by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "sq")
    Row(
        modifier.scale(squish).heightIn(min = 44.dp).clip(CircleShape).background(bg)
            .clickable(source, null) { Haptics.tick(context); onClick() }
            .padding(horizontal = if (icon != null) 14.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** A row of pills that scrolls sideways when it doesn't fit. */
@Composable
fun PillRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** A tonal circle button (PixelPlayer's top-bar buttons). */
@Composable
fun CircleButton(icon: ImageVector, description: String, modifier: Modifier = Modifier, size: Dp = 48.dp, container: Color = Ink.CardHigh, tint: Color = Ink.Text, onClick: () -> Unit) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squish by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f), label = "cb")
    Box(
        modifier.size(size).scale(squish).clip(CircleShape).background(container)
            .clickable(source, null) { Haptics.click(context); onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.46f)) }
}

/**
 * The expressive button: a pill that turns into a squircle while you press it,
 * with a little spring. `big` for the main action of a screen.
 */
@Composable
fun ExpressiveButton(
    text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, container: Color = Ink.VioletSoft,
    content: Color = Ink.OnViolet, big: Boolean = false, onClick: () -> Unit,
) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val h = if (big) 64.dp else 52.dp
    val corner by animateDpAsState(if (pressed) h * 0.28f else h / 2, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), label = "corner")
    Row(
        modifier.heightIn(min = h).clip(Squircle(corner)).background(container)
            .clickable(source, null) { Haptics.click(context); onClick() }
            .padding(PaddingValues(horizontal = if (big) 30.dp else 24.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(if (big) 26.dp else 20.dp))
        Text(text, color = content, fontWeight = FontWeight.Bold, fontSize = if (big) 18.sp else 15.sp, maxLines = 1)
    }
}

// ── Navigation ────────────────────────────────────────────────────────────────

/** One place on the floating bar. */
data class NavItem(val label: String, val icon: ImageVector, val iconOn: ImageVector = icon)

/** The same places as the bottom bar, down the side (landscape). */
@Composable
fun NavRail(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit) {
    Column(
        Modifier.fillMaxHeight().systemBarsPadding().padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
            .clip(Squircle(30.dp)).background(Ink.CardHigh).padding(vertical = 10.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items.forEachIndexed { i, item ->
            val on = i == selected
            Column(
                Modifier.clip(Squircle(18.dp)).clickable { onSelect(i) }.padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.width(52.dp).height(32.dp).clip(CircleShape).background(if (on) Ink.VioletDeep else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (on) item.iconOn else item.icon, contentDescription = item.label, tint = if (on) Ink.OnVioletDeep else Ink.Dim, modifier = Modifier.size(22.dp)) }
                Text(item.label, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Ink.Text else Ink.Dim)
            }
        }
    }
}

/** The bottom bar, floating: a rounded slab with pill indicators (PixelPlayer's). */
@Composable
fun FloatingNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 10.dp, top = 4.dp)
            .clip(Squircle(30.dp)).background(Ink.CardHigh).padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEachIndexed { i, item ->
            val on = i == selected
            val pillW by animateDpAsState(if (on) 60.dp else 36.dp, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "pw")
            val pillBg by animateColorAsState(if (on) Ink.VioletDeep else Color.Transparent, label = "pb")
            Column(
                Modifier.weight(1f).clip(Squircle(18.dp)).clickable { onSelect(i) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(pillW).height(34.dp).clip(CircleShape).background(pillBg), contentAlignment = Alignment.Center) {
                    Icon(if (on) item.iconOn else item.icon, contentDescription = item.label, tint = if (on) Ink.OnVioletDeep else Ink.Dim, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(item.label, fontSize = 12.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (on) Ink.Text else Ink.Dim, maxLines = 1)
            }
        }
    }
}

// ── Living shapes ─────────────────────────────────────────────────────────────

/**
 * A big shape that slowly turns and morphs from one form to the next (cookie →
 * clover → sun…), as a backdrop for Tokkip.
 */
@Composable
fun MorphingBlob(color: Color, modifier: Modifier = Modifier, shapes: List<RoundedPolygon> = listOf(MShapes.Cookie9, MShapes.Clover4, MShapes.Sunny, MShapes.Flower, MShapes.Cookie6)) {
    val morphs = remember(shapes) { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val t by rememberInfiniteTransition(label = "blob").animateFloat(
        0f, shapes.size.toFloat(), infiniteRepeatable(tween(6_000 * shapes.size, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    val i = t.toInt().coerceIn(0, morphs.size - 1)
    val f = t - i
    // Hold each shape, then morph quickly with an ease.
    val p = ((f - 0.6f) / 0.4f).coerceIn(0f, 1f).let { x -> x * x * (3 - 2 * x) }
    Box(modifier.graphicsLayer { rotationZ = t * 40f }.clip(MorphShape(morphs[i], p)).background(color))
}

/**
 * A wavy progress line (Android 16's media player): the done part waves, the
 * rest is a flat track. Waves move while `moving`.
 */
@Composable
fun WavyProgress(progress: Float, modifier: Modifier = Modifier, color: Color = Ink.VioletSoft, track: Color = Ink.Track, moving: Boolean = true) {
    val phase = if (moving) {
        val x by rememberInfiniteTransition(label = "wave").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "ph")
        x
    } else 0f
    Canvas(modifier.height(18.dp)) {
        val stroke = size.height * 0.28f
        val mid = size.height / 2f
        val amp = size.height * 0.24f
        val end = size.width * progress.coerceIn(0f, 1f)
        val wavelength = size.height * 2.2f
        val path = Path()
        var x = 0f
        path.moveTo(0f, mid)
        while (x <= end) {
            path.lineTo(x, mid + amp * sin(x / wavelength * 2 * PI.toFloat() - phase))
            x += 2f
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
        if (end < size.width - stroke) {
            drawLine(track, Offset(end + stroke * 1.6f, mid), Offset(size.width, mid), stroke, StrokeCap.Round)
        }
        drawCircle(color, stroke * 1.1f, Offset(end, mid + amp * sin(end / wavelength * 2 * PI.toFloat() - phase)))
    }
}

/** A grouped list, Pixel settings style: each row its own surface with rounded joins. */
@Composable
fun GroupedList(modifier: Modifier = Modifier, gap: Dp = 3.dp, rows: List<@Composable (Shape) -> Unit>) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEachIndexed { i, row -> row(mx.loperstack.tokkip.character.groupShape(i, rows.size)) }
    }
}

/** A card surface with smooth corners. */
@Composable
fun SmoothCard(modifier: Modifier = Modifier, shape: Shape = Squircle(28.dp), color: Color = Ink.Card, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(shape).background(color), content = content)
}
