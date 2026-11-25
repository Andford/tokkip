// The moving pieces every screen shares: the aurora behind everything, glass
// cards that arrive with a spring and squish when pressed, numbers that roll,
// the three neon rings, icons set in Expressive shapes, and a big wide title.

package mx.loperstack.tokkipfit.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.Stairs
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.delay
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.MorphShape
import mx.loperstack.tokkip.character.PolygonShape
import mx.loperstack.tokkipfit.data.fmt

/** Bouncy, but not silly. */
val Bounce = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)

/** Arrives after the ones before it: rises, grows and fades in, on a spring. */
@Composable
fun Appear(index: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(70L * index)
        t.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
    }
    Box(
        modifier.graphicsLayer {
            val v = t.value
            alpha = v.coerceIn(0f, 1f)
            translationY = (1f - v) * 60.dp.toPx()
            scaleX = 0.92f + 0.08f * v
            scaleY = 0.92f + 0.08f * v
        },
        content = content,
    )
}

/**
 * Glass: a see-through pane with a bright top edge, over the aurora. Plain
 * (white frost) or tinted with a colour; `solid` for what floats over moving
 * content (the bar), so what scrolls under it doesn't show through too much.
 */
fun Modifier.glass(shape: Shape, tint: Color? = null, solid: Boolean = false): Modifier = composed {
    val dark = LocalFit.current.dark
    val base = tint ?: if (dark) Color.White else Color.White
    val (top, bottom) = when {
        solid && dark -> Color(0xFF1A2135).copy(alpha = 0.92f) to Color(0xFF0E1322).copy(alpha = 0.92f)
        solid -> Color.White.copy(alpha = 0.9f) to Color.White.copy(alpha = 0.78f)
        tint != null && dark -> base.copy(alpha = 0.30f) to base.copy(alpha = 0.10f)
        tint != null -> base.copy(alpha = 0.34f) to base.copy(alpha = 0.16f)
        dark -> base.copy(alpha = 0.11f) to base.copy(alpha = 0.035f)
        else -> base.copy(alpha = 0.70f) to base.copy(alpha = 0.45f)
    }
    val edge = if (dark) listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.04f), (tint ?: Color.White).copy(alpha = 0.18f))
    else listOf(Color.White, Color.White.copy(alpha = 0.5f), (tint ?: Color.White).copy(alpha = 0.4f))
    this
        .shadow(if (dark) 0.dp else 10.dp, shape, ambientColor = (tint ?: Color(0xFF3D6BFF)).copy(alpha = 0.25f), spotColor = (tint ?: Color(0xFF3D6BFF)).copy(alpha = 0.25f))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .border(1.dp, Brush.linearGradient(edge), shape)
}

/**
 * The night sky (or a bright morning, in light mode) with four big soft
 * lights in the rings' colours drifting slowly behind everything: what the
 * glass shows through.
 */
@Composable
fun Aurora(modifier: Modifier = Modifier) {
    val fit = LocalFit.current
    val cs = MaterialTheme.colorScheme
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(28_000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    val strength = if (fit.dark) 0.42f else 0.30f
    Canvas(modifier.fillMaxSize()) {
        drawRect(cs.surface)
        val w = size.width; val h = size.height
        val lights = listOf(
            Triple(fit.steps, Offset(w * (0.15f + 0.12f * cos(t)), h * (0.12f + 0.06f * sin(t * 2))), w * 0.85f),
            Triple(fit.kcal, Offset(w * (0.95f + 0.08f * sin(t)), h * (0.30f + 0.08f * cos(t))), w * 0.75f),
            Triple(fit.minutesTo, Offset(w * (0.20f + 0.10f * sin(t + 1f)), h * (0.78f + 0.06f * cos(t * 2))), w * 0.8f),
            Triple(fit.stepsTo, Offset(w * (0.85f + 0.10f * cos(t + 2f)), h * (0.95f + 0.05f * sin(t))), w * 0.9f),
        )
        for ((c, at, r) in lights) {
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = strength), c.copy(alpha = strength * 0.35f), Color.Transparent), center = at, radius = r), r, at)
        }
        // A faint sheen over the top, like light on glass.
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (fit.dark) 0.03f else 0.25f), Color.Transparent), endY = h * 0.4f))
    }
}

/** A card: big corners, glass (plain or tinted with `color`), squishes when pressed. */
@Composable
fun FitCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "squish")
    val context = LocalContext.current
    val plain = color == MaterialTheme.colorScheme.surfaceContainer
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(RoundedCornerShape(28.dp), tint = if (plain) null else color)
            .then(if (onClick != null) Modifier.clickable(source, null) { Haptics.click(context); onClick() } else Modifier)
            .padding(18.dp),
        content = content,
    )
}

/** A number that rolls up to its value. */
@Composable
fun RollingNumber(value: Long, style: TextStyle, color: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier, suffix: String = "") {
    val shown by animateIntAsState(value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), tween(1100, easing = FastOutSlowInEasing), label = "roll")
    Text(fmt(shown.toLong()) + suffix, style = style, color = color, modifier = modifier)
}

/** A big wide title. */
@Composable
fun Title(text: String, modifier: Modifier = Modifier, size: TextUnit = 40.sp, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text, modifier = modifier, color = color,
        style = MaterialTheme.typography.displayLarge.copy(fontFamily = DisplayFont, fontWeight = FontWeight(800), fontSize = size, lineHeight = size * 1.05f, letterSpacing = (-0.5).sp),
    )
}

/** An icon inside an Expressive shape. */
@Composable
fun ShapeIcon(icon: ImageVector, color: Color, onColor: Color, size: Dp = 44.dp, shape: RoundedPolygon = MShapes.Cookie9) {
    Box(Modifier.size(size).clip(PolygonShape(shape)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = onColor, modifier = Modifier.size(size * 0.52f))
    }
}

/**
 * The three rings, outside in: steps, exercise minutes, calories, in neon
 * gradients that glow, with a bright head where each one is. They fill up on
 * a spring when they first show and whenever the numbers change; past the
 * goal a ring keeps going round, brighter.
 */
@Composable
fun Rings(steps: Float, minutes: Float, kcal: Float, modifier: Modifier = Modifier, stroke: Dp = 18.dp, gap: Dp = 6.dp, content: @Composable BoxScope.() -> Unit = {}) {
    val fit = LocalFit.current
    val a = remember { Animatable(0f) }
    val b = remember { Animatable(0f) }
    val c = remember { Animatable(0f) }
    LaunchedEffect(steps) { a.animateTo(steps.coerceIn(0f, 2f), spring(0.62f, Spring.StiffnessVeryLow)) }
    LaunchedEffect(minutes) { delay(120); b.animateTo(minutes.coerceIn(0f, 2f), spring(0.62f, Spring.StiffnessVeryLow)) }
    LaunchedEffect(kcal) { delay(240); c.animateTo(kcal.coerceIn(0f, 2f), spring(0.62f, Spring.StiffnessVeryLow)) }
    // The glow breathes a little, so the rings feel alive.
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(0.55f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g")
    val rings = listOf(Triple(fit.steps, fit.stepsTo, a.value), Triple(fit.minutes, fit.minutesTo, b.value), Triple(fit.kcal, fit.kcalTo, c.value))
    fun DrawScope.ring(i: Int, from: Color, to: Color, p: Float, glowing: Boolean) {
        val w = stroke.toPx()
        val g = gap.toPx()
        val inset = w / 2 + i * (w + g)
        val side = size.minDimension - inset * 2
        if (side <= 0f) return
        val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2)
        val arc = Size(side, side)
        val r = side / 2
        val centre = Offset(size.width / 2, size.height / 2)
        if (!glowing) drawArc(from.copy(alpha = if (fit.dark) 0.13f else 0.16f), 0f, 360f, false, topLeft, arc, style = Stroke(w))
        val shown = p.coerceAtLeast(0.001f)
        // The gradient runs from the top round to where the ring is (all of it once past the goal).
        val end = shown.coerceAtMost(1f)
        val brush = Brush.sweepGradient(0f to from, end to to, 1f to (if (shown >= 1f) from else to), center = centre)
        rotate(-90f, centre) {
            val width = if (glowing) w * 1.9f else w
            val alpha = if (glowing) 0.55f * glow else 1f
            drawArc(brush, 0f, 360f * end, false, topLeft, arc, alpha = alpha, style = Stroke(width, cap = StrokeCap.Round))
            if (shown > 1f) drawArc(Brush.sweepGradient(listOf(to, from, to), centre), 0f, 360f * (shown - 1f), false, topLeft, arc, alpha = alpha, style = Stroke(width, cap = StrokeCap.Round))
        }
        if (!glowing && p > 0.02f) {
            // The bright head, where the ring is now.
            val angle = Math.toRadians((360.0 * (shown % 1f).let { if (it == 0f) 1f else it }) - 90.0)
            val head = Offset(centre.x + r * cos(angle).toFloat(), centre.y + r * sin(angle).toFloat())
            drawCircle(Color.White.copy(alpha = 0.85f), w * 0.22f, head)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        // The glow: the same rings, wider and blurred, behind (Android 12+ blurs; before, a soft halo).
        Canvas(Modifier.matchParentSize().blur(14.dp, BlurredEdgeTreatment.Unbounded)) {
            rings.forEachIndexed { i, (from, to, p) -> if (p > 0.01f) ring(i, from, to, p, glowing = true) }
        }
        Canvas(Modifier.matchParentSize()) {
            rings.forEachIndexed { i, (from, to, p) -> ring(i, from, to, p, glowing = false) }
        }
        content()
    }
}

/**
 * A big shape that slowly turns and morphs from one form to the next (cookie →
 * clover → sun…), as a backdrop for Tokkip (the same as Tokkip's and Notas').
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
    Box(
        modifier.graphicsLayer { rotationZ = t * 40f }.clip(MorphShape(morphs[i], p))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.75f), color.copy(alpha = 0.22f))))
            .border(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.05f))), MorphShape(morphs[i], p)),
    )
}

/** The challenges' icons, by key. */
fun challengeIcon(key: String): ImageVector = when (key) {
    "walk" -> Icons.AutoMirrored.Rounded.DirectionsWalk
    "bolt" -> Icons.Rounded.Bolt
    "stairs" -> Icons.Rounded.Stairs
    "squat" -> Icons.Rounded.FitnessCenter
    "stretch" -> Icons.Rounded.SelfImprovement
    "water" -> Icons.Rounded.WaterDrop
    "breathe" -> Icons.Rounded.Air
    "plank" -> Icons.Rounded.SportsGymnastics
    else -> Icons.Rounded.Bolt
}
