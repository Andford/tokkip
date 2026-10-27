package mx.loperstack.tokkip.ui

import mx.loperstack.tokkip.character.Squircle
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*

/**
 * The app's colours. They follow the theme you pick: your Pixel's wallpaper
 * colours (Material You, light or dark as the phone is), or the island's own
 * dark palette (windows/src/style.css). State-backed, so a change of wallpaper
 * or theme repaints everything, Canvas drawings included.
 */
object Ink {
    var Ground by mutableStateOf(Color(0xFF0B0C0E))
    var Card by mutableStateOf(Color(0xFF141518))
    var CardHigh by mutableStateOf(Color(0xFF17181C))
    var Hairline by mutableStateOf(Color(0xFF1D1F24))
    var Text by mutableStateOf(Color(0xFFF5F6F8))
    var Dim by mutableStateOf(Color(0xFF8E939C))
    var Dim2 by mutableStateOf(Color(0xFF6B7079))
    var Violet by mutableStateOf(Color(0xFFA78BFA))
    var VioletSoft by mutableStateOf(Color(0xFFC4B5FD))
    var VioletDeep by mutableStateOf(Color(0xFF1A1726))
    /** Text and icons on VioletDeep / on VioletSoft (Material's on-colours, always readable). */
    var OnVioletDeep by mutableStateOf(Color(0xFFC4B5FD))
    var OnViolet by mutableStateOf(Color(0xFF14102A))
    var VioletLine by mutableStateOf(Color(0xFF2A2440))
    var Green by mutableStateOf(Color(0xFF4ADE80))
    var GreenSoft by mutableStateOf(Color(0xFF86EFAC))
    var GreenDeep by mutableStateOf(Color(0xFF06260F))
    var Amber by mutableStateOf(Color(0xFFF5A524))
    var Red by mutableStateOf(Color(0xFFF4505E))
    /** Empty tracks and faint fills: the text colour, barely there. */
    var Track by mutableStateOf(Color(0x14FFFFFF))
    var Dark by mutableStateOf(true)

    internal fun classic() {
        Ground = Color(0xFF0B0C0E); Card = Color(0xFF141518); CardHigh = Color(0xFF17181C); Hairline = Color(0xFF1D1F24)
        Text = Color(0xFFF5F6F8); Dim = Color(0xFF8E939C); Dim2 = Color(0xFF6B7079)
        Violet = Color(0xFFA78BFA); VioletSoft = Color(0xFFC4B5FD); VioletDeep = Color(0xFF1A1726); OnVioletDeep = Color(0xFFC4B5FD); OnViolet = Color(0xFF14102A); VioletLine = Color(0xFF2A2440)
        Green = Color(0xFF4ADE80); GreenSoft = Color(0xFF86EFAC); GreenDeep = Color(0xFF06260F); Red = Color(0xFFF4505E)
        Track = Color(0x14FFFFFF); Dark = true
    }

    internal fun from(cs: ColorScheme, dark: Boolean) {
        Ground = cs.surface; Card = cs.surfaceContainer; CardHigh = cs.surfaceContainerHigh; Hairline = cs.outlineVariant
        Text = cs.onSurface; Dim = cs.onSurfaceVariant; Dim2 = cs.outline
        Violet = cs.primary; VioletSoft = cs.primary; VioletDeep = cs.primaryContainer; OnVioletDeep = cs.onPrimaryContainer; OnViolet = cs.onPrimary; VioletLine = cs.primaryContainer
        Green = if (dark) Color(0xFF4ADE80) else Color(0xFF16A34A)
        GreenSoft = if (dark) Color(0xFF86EFAC) else Color(0xFF15803D)
        GreenDeep = if (dark) Color(0xFF06260F) else Color.White
        Red = cs.error
        Track = cs.onSurface.copy(alpha = 0.08f); Dark = dark
    }
}

private val ClassicScheme = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF14102A),
    secondary = Color(0xFF4ADE80),
    background = Color(0xFF0B0C0E),
    onBackground = Color(0xFFF5F6F8),
    surface = Color(0xFF141518),
    onSurface = Color(0xFFF5F6F8),
    surfaceVariant = Color(0xFF17181C),
    onSurfaceVariant = Color(0xFF8E939C),
    outline = Color(0xFF1D1F24),
    surfaceContainer = Color(0xFF141518),
    surfaceContainerHigh = Color(0xFF17181C),
)

/**
 * Google Sans, the Pixel's own typeface (the Diario/Journal look), when the
 * phone has it — every Pixel does; elsewhere the system font.
 */
private fun pixelFont(weight: Int): android.graphics.Typeface {
    for (family in listOf("google-sans-flex", "google-sans", "google-sans-text")) {
        val base = android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL)
        if (base != android.graphics.Typeface.DEFAULT) return android.graphics.Typeface.create(base, weight, false)
    }
    return android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, weight, false)
}

private val PixelSans = androidx.compose.ui.text.font.FontFamily(pixelFont(400))

/** A touch heavier than Material's defaults: body text Medium, titles Bold. */
private val TokkipType = androidx.compose.material3.Typography().let { t ->
    fun androidx.compose.ui.text.TextStyle.p(w: Int) = copy(fontFamily = PixelSans, fontWeight = androidx.compose.ui.text.font.FontWeight(w))
    t.copy(
        displayLarge = t.displayLarge.p(700), displayMedium = t.displayMedium.p(700), displaySmall = t.displaySmall.p(700),
        headlineLarge = t.headlineLarge.p(700), headlineMedium = t.headlineMedium.p(700), headlineSmall = t.headlineSmall.p(700),
        titleLarge = t.titleLarge.p(700), titleMedium = t.titleMedium.p(600), titleSmall = t.titleSmall.p(600),
        bodyLarge = t.bodyLarge.p(500), bodyMedium = t.bodyMedium.p(500), bodySmall = t.bodySmall.p(500),
        labelLarge = t.labelLarge.p(600), labelMedium = t.labelMedium.p(600), labelSmall = t.labelSmall.p(600),
    )
}

/** Material 3 Expressive: generous, friendly corners. */
private val TokkipShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
)

/**
 * @param theme "pixel" (wallpaper colours, follows the phone's light/dark),
 *   "pixelDark" (wallpaper colours, always dark) or "tokkip" (the island's).
 */
@Composable
fun TokkipTheme(theme: String = "pixel", content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = theme != "pixel" || systemDark
    val scheme = remember(theme, dark, context.resources.configuration) {
        when {
            theme == "tokkip" -> ClassicScheme
            dark -> dynamicDarkColorScheme(context)
            else -> dynamicLightColorScheme(context)
        }
    }
    if (theme == "tokkip") Ink.classic() else Ink.from(scheme, dark)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            val bars = WindowCompat.getInsetsController(window, view)
            bars.isAppearanceLightStatusBars = !dark
            bars.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, typography = TokkipType, shapes = TokkipShapes, content = content)
}

/** Stroke icons from the mockup, on a 24×24 grid. */
object Glyph {
    private fun stroke(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in paths) {
            b.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    // Material Symbols, rounded (Material 3 Expressive), in place of the mockup's strokes.
    val Home = Icons.Rounded.Home
    val Tasks = Icons.Rounded.TaskAlt
    val Focus = Icons.Rounded.Timer
    val Breathe = Icons.Rounded.SelfImprovement
    val Diary = Icons.Rounded.AutoStories
    val Heart = Icons.Rounded.Favorite
    val Moon = Icons.Rounded.Bedtime
    val Pc = Icons.Rounded.Laptop
    val Gear = Icons.Rounded.Settings
    val Video = Icons.Rounded.Videocam
    val Back = Icons.Rounded.ArrowBack
    val Skip = Icons.Rounded.SkipNext
    val Reset = Icons.Rounded.Replay
    val Plus = Icons.Rounded.Add
    val Close = Icons.Rounded.Close
    val Calendar = Icons.Rounded.CalendarMonth
}
