// Fit's look: neon on glass. A night-sky background with slow aurora lights
// (Kit.kt's Aurora), see-through cards with a bright edge (Modifier.glass),
// and the rings in neon gradients (shared fit/Neon.kt) that glow. Its own
// colours, light or dark as the phone is; Google Sans with its real weights
// and wide display titles from Google Sans Flex.

package mx.loperstack.tokkipfit.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import mx.loperstack.tokkip.fit.Neon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import java.io.File

/** The rings' colours, and a few for the stats. */
@Immutable
data class FitColors(
    val steps: Color,
    val minutes: Color,
    val kcal: Color,
    /** Where each ring's gradient ends. */
    val stepsTo: Color,
    val minutesTo: Color,
    val kcalTo: Color,
    val sleep: Color,
    val heart: Color,
    val track: Color,
    val dark: Boolean,
)

val LocalFit = staticCompositionLocalOf { FitColors(Color.Cyan, Color.Green, Color.Red, Color.Blue, Color.Green, Color.Red, Color.Blue, Color.Red, Color.Gray, true) }

private val NightScheme = darkColorScheme(
    primary = Color(0xFF3DF0FF), onPrimary = Color(0xFF001E24),
    primaryContainer = Color(0xFF00C8E8), onPrimaryContainer = Color(0xFFE8FDFF),
    secondary = Color(0xFFD7FF3A), onSecondary = Color(0xFF1A2400),
    secondaryContainer = Color(0xFF8FE000), onSecondaryContainer = Color(0xFFF6FFE3),
    tertiary = Color(0xFFFF4D94), onTertiary = Color(0xFF2E0014),
    tertiaryContainer = Color(0xFFFF3D8B), onTertiaryContainer = Color(0xFFFFEDF4),
    background = Color(Neon.NIGHT), onBackground = Color(0xFFF3F6FF),
    surface = Color(Neon.NIGHT), onSurface = Color(0xFFF3F6FF),
    surfaceVariant = Color(0xFF1B2133), onSurfaceVariant = Color(0xFFA9B4D0),
    surfaceContainerLowest = Color(0xFF080B14), surfaceContainerLow = Color(0xFF0E1322),
    surfaceContainer = Color(0xFF131A2C), surfaceContainerHigh = Color(0xFF1A2237), surfaceContainerHighest = Color(0xFF222B43),
    outline = Color(0xFF4A5677), outlineVariant = Color(0xFF2A3350),
    error = Color(0xFFFF6B6B), onError = Color(0xFF2A0000),
    inverseSurface = Color(0xFFE9EDFA), inverseOnSurface = Color(0xFF131A2C), inversePrimary = Color(0xFF006A7A),
)

private val DayScheme = lightColorScheme(
    primary = Color(0xFF0061F2), onPrimary = Color.White,
    primaryContainer = Color(0xFF00B4E0), onPrimaryContainer = Color(0xFF002331),
    secondary = Color(0xFF3F8F00), onSecondary = Color.White,
    secondaryContainer = Color(0xFF9BE600), onSecondaryContainer = Color(0xFF142100),
    tertiary = Color(0xFFE0115F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFF4D94), onTertiaryContainer = Color(0xFF3A0018),
    background = Color(0xFFF1F4FF), onBackground = Color(0xFF0B1020),
    surface = Color(0xFFF1F4FF), onSurface = Color(0xFF0B1020),
    surfaceVariant = Color(0xFFE2E7F7), onSurfaceVariant = Color(0xFF4A5470),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8FAFF),
    surfaceContainer = Color(0xFFEEF2FF), surfaceContainerHigh = Color(0xFFE6EBFA), surfaceContainerHighest = Color(0xFFDDE3F5),
    outline = Color(0xFF8A93AD), outlineVariant = Color(0xFFC9D0E4),
)

/** The first Google Sans this phone has (every Pixel does), or null for the system font. */
private fun pixelFamilyName(): String? {
    for (family in listOf("google-sans-flex", "google-sans", "google-sans-text")) {
        val base = android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL)
        if (base != android.graphics.Typeface.DEFAULT) return family
    }
    return null
}

/** One font per weight, so Bold is really bold (a single-Typeface family draws them all Regular). */
private val PixelSans: FontFamily = pixelFamilyName()?.let { name ->
    FontFamily((3..9).map { w -> Font(DeviceFontFamilyName(name), weight = FontWeight(w * 100)) })
} ?: FontFamily.Default

/** Wide and heavy, rounded ends: Google Sans Flex read from the Pixel's own font file; elsewhere extra bold. */
@OptIn(ExperimentalTextApi::class)
val DisplayFont: FontFamily by lazy {
    val file = listOf("/system/fonts", "/product/fonts", "/system/product/fonts")
        .flatMap { d -> File(d).listFiles()?.toList() ?: emptyList() }
        .firstOrNull { it.name.startsWith("GoogleSansFlex", ignoreCase = true) && it.name.endsWith(".ttf", true) }
    runCatching {
        if (file != null) FontFamily(
            Font(
                file, FontWeight(800),
                variationSettings = FontVariation.Settings(FontVariation.weight(800), FontVariation.width(125f), FontVariation.Setting("ROND", 100f)),
            ),
        ) else null
    }.getOrNull() ?: PixelSans
}

private val FitType = Typography().let { t ->
    fun TextStyle.p(w: Int) = copy(fontFamily = PixelSans, fontWeight = FontWeight(w))
    t.copy(
        displayLarge = t.displayLarge.p(800), displayMedium = t.displayMedium.p(800), displaySmall = t.displaySmall.p(800),
        headlineLarge = t.headlineLarge.p(800), headlineMedium = t.headlineMedium.p(800), headlineSmall = t.headlineSmall.p(700),
        titleLarge = t.titleLarge.p(700), titleMedium = t.titleMedium.p(700), titleSmall = t.titleSmall.p(600),
        bodyLarge = t.bodyLarge.p(500), bodyMedium = t.bodyMedium.p(500), bodySmall = t.bodySmall.p(500),
        labelLarge = t.labelLarge.p(600), labelMedium = t.labelMedium.p(600), labelSmall = t.labelSmall.p(600),
    )
}

private val FitShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun FitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) NightScheme else DayScheme
    val fit = if (dark) FitColors(
        steps = Color(Neon.STEPS), minutes = Color(Neon.MINUTES), kcal = Color(Neon.KCAL),
        stepsTo = Color(Neon.STEPS_TO), minutesTo = Color(Neon.MINUTES_TO), kcalTo = Color(Neon.KCAL_TO),
        sleep = Color(0xFFA78BFF), heart = Color(0xFFFF5C8A),
        track = Color.White.copy(alpha = 0.08f), dark = true,
    ) else FitColors(
        steps = Color(Neon.STEPS_LIGHT), minutes = Color(Neon.MINUTES_LIGHT), kcal = Color(Neon.KCAL_LIGHT),
        stepsTo = Color(Neon.STEPS_TO_LIGHT), minutesTo = Color(Neon.MINUTES_TO_LIGHT), kcalTo = Color(Neon.KCAL_TO_LIGHT),
        sleep = Color(0xFF6A4CF5), heart = Color(0xFFE0115F),
        track = Color(0xFF0B1020).copy(alpha = 0.07f), dark = false,
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            val bars = WindowCompat.getInsetsController(window, view)
            bars.isAppearanceLightStatusBars = !dark
            bars.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalFit provides fit) {
        MaterialTheme(colorScheme = scheme, typography = FitType, shapes = FitShapes, content = content)
    }
}
