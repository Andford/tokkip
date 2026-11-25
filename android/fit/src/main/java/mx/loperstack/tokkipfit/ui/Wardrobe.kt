// Tu Tokkip: the same customising as in the Tokkip app. Follow the Tokkip
// app's look (when it's installed), or give him Fit's own: species, skin, body
// colour (Tokkip's colours, your Pixel's, or any one), accent, eyes and mouth,
// cheeks, and what he wears (the season's, or always the same).

package mx.loperstack.tokkipfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.CrueltyFree
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.Look
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Outfit
import mx.loperstack.tokkip.character.Skin
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Species
import mx.loperstack.tokkip.character.TOKKIP_PALETTE
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkipfit.data.Prefs

private fun argb(c: Color) = c.toArgb().toLong() and 0xFFFFFFFFL

// The same choices as the Tokkip app's (ui/Settings.kt there).
private val ACCENTS = listOf(0xFF3F9E9A, 0xFFF4505E, 0xFFF29B38, 0xFF4ADE80, 0xFF38BDF8, 0xFF818CF8, 0xFFC084FC, 0xFFFB7185, 0xFF8A5A2B, 0xFF1B1616)
private val FACE_INKS = listOf(0xFF1B1616, 0xFF3A2E2C, 0xFF16807C, 0xFF3F72C2, 0xFF6D4BB8, 0xFFC2416B, 0xFF2F6B3A, 0xFF8A5A2B)
private val CHEEKS = listOf(0xFFF0788C, 0xFFF7A1B5, 0xFFFF9A76, 0xFFF4B860, 0xFFC4A1F0, 0xFF8FD0F0, 0xFF9BE0B0, 0xFFE0E0E0)

private fun outfitIcon(o: Outfit): ImageVector = when (o) {
    Outfit.NONE -> Icons.Rounded.Block
    Outfit.PARTY -> Icons.Rounded.Celebration
    Outfit.CROWN -> Icons.Rounded.EmojiEvents
    Outfit.BEANIE -> Icons.Rounded.Checkroom
    Outfit.HEARTS -> Icons.Rounded.Favorite
    Outfit.SPRING -> Icons.Rounded.LocalFlorist
    Outfit.SUNGLASSES -> Icons.Rounded.WbSunny
    Outfit.STRAW -> Icons.Rounded.BeachAccess
    Outfit.TRICOLOR -> Icons.Rounded.Flag
    Outfit.WITCH -> Icons.Rounded.AutoFixHigh
    Outfit.FLOWERS -> Icons.Rounded.Spa
    Outfit.SCARF -> Icons.Rounded.Air
    Outfit.ANTLERS -> Icons.Rounded.Forest
    Outfit.SANTA -> Icons.Rounded.AcUnit
    Outfit.GLASSES -> Icons.Rounded.Visibility
    Outfit.BOW -> Icons.Rounded.CardGiftcard
    Outfit.HEADPHONES -> Icons.Rounded.Headphones
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WardrobeScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val look = Prefs.look
    val tokkipThere = remember { Prefs.tokkipAround() }
    var jump by remember { mutableIntStateOf(0) }
    fun set(l: Look, loud: Boolean = true) {
        Prefs.changeLook(l)
        if (loud) { jump++; Sounds.play("pop"); Haptics.tick(context) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
            Spacer(Modifier.width(12.dp))
            Title("Tu Tokkip", size = 30.sp)
        }
        // Tokkip, big, trying each change on.
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
            MorphingBlob(cs.primaryContainer, Modifier.size(210.dp))
            Tokkip(Modifier.size(180.dp, 160.dp), color = Color(look.color), mood = Mood.HAPPY, jump = jump, interactive = true, greet = true)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (tokkipThere) {
                FitCard(color = cs.tertiaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Igual que en Tokkip", style = MaterialTheme.typography.titleMedium, color = cs.onTertiaryContainer)
                            Text(
                                if (Prefs.follow) "Lleva el mismo look que en la app de Tokkip, y cambia cuando lo cambies allá."
                                else "Fit tiene su propio Tokkip. Préndelo para usar el de la app de Tokkip.",
                                style = MaterialTheme.typography.bodyMedium, color = cs.onTertiaryContainer.copy(alpha = 0.85f),
                            )
                        }
                        Switch(Prefs.follow, { Prefs.followTokkip(it); jump++; Sounds.play("pop") })
                    }
                }
            }

            Section("Especie")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (sp in Species.entries) {
                    val icon = when (sp) { Species.CONEJO -> Icons.Rounded.CrueltyFree; Species.ROBOT -> Icons.Rounded.SmartToy; else -> Icons.Rounded.Pets }
                    Pill(sp.label, look.species == sp.name, icon) { set(look.copy(species = sp.name)) }
                }
            }
            Section("Piel")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (sk in Skin.entries) Pill(sk.label, look.skin == sk.name, if (sk == Skin.ENSUENO) Icons.Rounded.AutoAwesome else Icons.Rounded.Circle) { set(look.copy(skin = sk.name)) }
            }

            Section("Color")
            BodyColor(look, { set(look.copy(color = it)) }, { set(look.copy(color = it), loud = false) })

            Section("Acento · orejas y cola")
            Swatches(ACCENTS, look.accent) { set(look.copy(accent = it)) }
            Section("Ojos y boca")
            Swatches(listOf(0L) + FACE_INKS, look.ink) { set(look.copy(ink = it)) }
            Section("Mejillas")
            Swatches(listOf(0L) + CHEEKS, look.cheek) { set(look.copy(cheek = it)) }

            Section("Gorritos y accesorios")
            val season = remember { Outfit.seasonal(java.util.Calendar.getInstance()) }
            Text(
                if (look.outfit == "auto") "Según la temporada · hoy: ${season.label}" else "Siempre: ${Outfit.resolve(look.outfit).label}",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Temporada", look.outfit == "auto", Icons.Rounded.CalendarMonth) { set(look.copy(outfit = "auto")) }
                for (o in Outfit.entries) Pill(o.label, look.outfit == o.name, outfitIcon(o)) { set(look.copy(outfit = o.name)) }
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Pill(label: String, on: Boolean, icon: ImageVector, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.then(if (on) Modifier.clip(CircleShape).background(Brush.linearGradient(listOf(LocalFit.current.steps, LocalFit.current.stepsTo))) else Modifier.glass(CircleShape)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) cs.onPrimary else cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) cs.onPrimary else cs.onSurface)
    }
}

/** Round swatches; 0 is "automatic" (shown as a ring). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(colors: List<Long>, current: Long, onPick: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (c in colors) {
            val on = c == current
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(if (c == 0L) cs.surfaceContainerHigh else Color(c))
                    .border(if (on) 3.dp else 1.dp, if (on) cs.primary else cs.outlineVariant, CircleShape)
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (c == 0L) Icon(Icons.Rounded.AutoAwesome, "Automático", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Tokkip's colours, your Pixel's, or any one (hue, colour and light). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BodyColor(look: Look, onPick: (Long) -> Unit, onDrag: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var custom by remember { mutableStateOf(false) }
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(look.color.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    fun pickHsv() = onDrag(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)).toLong() and 0xFFFFFFFFL)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Colores", !custom, Icons.Rounded.Circle) { custom = false }
        Pill("A tu gusto", custom, Icons.Rounded.AutoAwesome) { custom = true }
        Pill("De mi Pixel", false, Icons.Rounded.WbSunny) { onPick(argb(cs.primary)) }
    }
    if (!custom) {
        Swatches(TOKKIP_PALETTE.map { argb(it) }, look.color, onPick)
    } else {
        Text("Tono", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Slider(hue, { hue = it; pickHsv() }, valueRange = 0f..360f)
        Text("Color", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Slider(sat, { sat = it; pickHsv() })
        Text("Luz", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Slider(value, { value = it; pickHsv() }, valueRange = 0.2f..1f)
    }
}
