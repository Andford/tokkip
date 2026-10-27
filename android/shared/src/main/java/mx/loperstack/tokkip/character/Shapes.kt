// Material 3 Expressive shapes, built with androidx.graphics.shapes: the
// cookies, clovers and suns of Android 16, a squircle with smooth (G2-like)
// corners for every card, and morphs between any two of them. Shared by the
// phone and the watch.

package mx.loperstack.tokkip.character

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kotlin.math.PI

/** The shape library, each normalized to a 1×1 box. */
object MShapes {
    private fun r(x: Float, smooth: Float = 0f) = CornerRounding(x, smooth)

    /** A circle with `n` soft scallops. */
    private fun cookie(n: Int, depth: Float): RoundedPolygon {
        val seg = (PI / n).toFloat()
        return RoundedPolygon.star(n, 1f, 1f - depth, rounding = r(seg * 0.95f), innerRounding = r(seg * 0.95f)).normalized()
    }

    val Circle = RoundedPolygon.circle(12).normalized()
    val Cookie4 = cookie(4, 0.2f)
    val Cookie6 = cookie(6, 0.16f)
    val Cookie9 = cookie(9, 0.11f)
    val Cookie12 = cookie(12, 0.09f)
    val Clover4 = RoundedPolygon.star(4, 1f, 0.42f, rounding = r(0.75f), innerRounding = r(0.08f)).normalized()
    val Clover8 = RoundedPolygon.star(8, 1f, 0.66f, rounding = r(0.36f), innerRounding = r(0.04f)).normalized()
    val Flower = RoundedPolygon.star(6, 1f, 0.6f, rounding = r(0.5f), innerRounding = r(0.12f)).normalized()
    val Sunny = RoundedPolygon.star(8, 1f, 0.84f, rounding = r(0.14f), innerRounding = r(0.14f)).normalized()
    val VerySunny = RoundedPolygon.star(8, 1f, 0.7f, rounding = r(0.12f), innerRounding = r(0.1f)).normalized()
    val SoftBurst = RoundedPolygon.star(10, 1f, 0.76f, rounding = r(0.1f), innerRounding = r(0.03f)).normalized()
    val Burst = RoundedPolygon.star(12, 1f, 0.72f, rounding = r(0.05f), innerRounding = r(0.02f)).normalized()
    val Puffy = cookie(7, 0.12f)
    val Triangle = RoundedPolygon(3, rounding = r(0.4f, 0.3f)).normalized()
    val Pentagon = RoundedPolygon(5, rounding = r(0.32f, 0.4f)).normalized()
    val Gem = RoundedPolygon(6, rounding = r(0.28f, 0.4f)).normalized()
    val Square = RoundedPolygon.rectangle(2f, 2f, rounding = r(0.6f, 0.8f)).normalized()
    val Diamond = RoundedPolygon(4, rounding = r(0.45f, 0.5f)).normalized()
    /** Round on top, square-ish at the base. */
    val Arch = RoundedPolygon(
        floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f),
        perVertexRounding = listOf(r(1f), r(1f), r(0.3f, 0.6f), r(0.3f, 0.6f)),
    ).normalized()

    /** For decorating: a different one per place. */
    val ALL = listOf(Cookie9, Clover4, Sunny, Cookie6, Flower, SoftBurst, Pentagon, Clover8, Gem, Puffy, Arch, Cookie12, Triangle, VerySunny, Cookie4, Diamond)
}

/** Any RoundedPolygon (normalized) as a Compose shape, optionally turned. */
@Immutable
class PolygonShape(private val polygon: RoundedPolygon, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = polygon.toPath().asComposePath()
        path.transform(fitMatrix(size, rotation))
        return Outline.Generic(path)
    }
}

/** A morph between two shapes at `progress` (0 = start, 1 = end). */
@Immutable
class MorphShape(private val morph: Morph, private val progress: Float, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        path.transform(fitMatrix(size, rotation))
        return Outline.Generic(path)
    }
}

private fun fitMatrix(size: Size, rotation: Float) = Matrix().apply {
    if (rotation != 0f) {
        translate(size.width / 2f, size.height / 2f)
        rotateZ(rotation)
        translate(-size.width / 2f, -size.height / 2f)
    }
    scale(size.width, size.height)
}

/** A path for drawing a shape at a given size, e.g. on a Canvas. */
fun RoundedPolygon.pathFor(size: Size, rotation: Float = 0f): Path =
    toPath().asComposePath().apply { transform(fitMatrix(size, rotation)) }

/**
 * Smooth corners, as on Pixel cards: the curve eases into the straight edge
 * instead of meeting it at a hard tangent. Corners clamp to the box.
 */
@Immutable
class Squircle(
    private val topStart: Dp, private val topEnd: Dp = topStart,
    private val bottomEnd: Dp = topStart, private val bottomStart: Dp = topStart,
    private val smoothing: Float = 0.6f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
        val max = minOf(w, h) / 2f
        fun px(d: Dp) = with(density) { d.toPx() }.coerceIn(0f, max)
        val rtl = layoutDirection == LayoutDirection.Rtl
        val tl = px(if (rtl) topEnd else topStart)
        val tr = px(if (rtl) topStart else topEnd)
        val br = px(if (rtl) bottomStart else bottomEnd)
        val bl = px(if (rtl) bottomEnd else bottomStart)
        val polygon = RoundedPolygon(
            floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h),
            perVertexRounding = listOf(
                CornerRounding(tl, smoothing), CornerRounding(tr, smoothing),
                CornerRounding(br, smoothing), CornerRounding(bl, smoothing),
            ),
        )
        return Outline.Generic(polygon.toPath().asComposePath())
    }
}

/**
 * A grouped list's item (Pixel settings style): big outer corners on the
 * first and last rows, small ones where rows meet.
 */
fun groupShape(index: Int, count: Int, outer: Dp = 26.dp, inner: Dp = 6.dp): Shape = when {
    count <= 1 -> Squircle(outer)
    index == 0 -> Squircle(outer, outer, inner, inner)
    index == count - 1 -> Squircle(inner, inner, outer, outer)
    else -> Squircle(inner)
}
