// Fit's look: Material 3 Expressive with your wallpaper's colours (Material
// You, light or dark as the phone is), Google Sans with its real weights, wide
// display titles from Google Sans Flex, and three ring colours that stay the
// same everywhere (steps follow your colours; minutes are lime, calories coral).

package mx.loperstack.tokkipfit.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
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
    val sleep: Color,
    val heart: Color,
    val track: Color,
    val dark: Boolean,
)

val LocalFit = staticCompositionLocalOf { FitColors(Color.Cyan, Color.Green, Color.Red, Color.Blue, Color.Red, Color.Gray, true) }

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
    val scheme = remember(dark, context.resources.configuration) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    val fit = FitColors(
        steps = scheme.primary,
        minutes = if (dark) Color(0xFFB9F35A) else Color(0xFF4F8A10),
        kcal = if (dark) Color(0xFFFF7A6B) else Color(0xFFD93A2B),
        sleep = if (dark) Color(0xFFA7B4FF) else Color(0xFF4655C4),
        heart = if (dark) Color(0xFFFF8FB1) else Color(0xFFC2185B),
        track = scheme.onSurface.copy(alpha = 0.09f),
        dark = dark,
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
