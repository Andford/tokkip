// The moving pieces every screen shares: cards that arrive with a spring and
// squish when pressed, numbers that roll, the three rings, icons set in
// Expressive shapes, and a big wide title.

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

/** A card: big corners, the surface's container colour, squishes when pressed. */
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
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(28.dp))
            .background(color)
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
 * The three rings, outside in: steps, exercise minutes, calories. They fill up
 * on a spring when they first show and whenever the numbers change; past the
 * goal a ring keeps going round, a shade deeper.
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
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val g = gap.toPx()
            listOf(fit.steps to a.value, fit.minutes to b.value, fit.kcal to c.value).forEachIndexed { i, (color, p) ->
                val inset = w / 2 + i * (w + g)
                val side = size.minDimension - inset * 2
                if (side <= 0f) return@forEachIndexed
                val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2)
                val arc = Size(side, side)
                drawArc(fit.track, 0f, 360f, false, topLeft, arc, style = Stroke(w))
                drawArc(color, -90f, 360f * p.coerceAtMost(1f), false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
                if (p > 1f) {
                    val deeper = Color(color.red * 0.78f, color.green * 0.78f, color.blue * 0.78f, 1f)
                    drawArc(deeper, -90f, 360f * (p - 1f), false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
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
    Box(modifier.graphicsLayer { rotationZ = t * 40f }.clip(MorphShape(morphs[i], p)).background(color))
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
