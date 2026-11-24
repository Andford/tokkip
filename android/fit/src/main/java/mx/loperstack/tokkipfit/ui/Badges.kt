// Retos y logros: this week's challenge (on Hoy, with its bar), and the badges,
// each in its own Expressive shape: the ones earned in colour, popping in, the
// rest waiting with how far along you are. Tapping one earned makes Tokkip cheer.

package mx.loperstack.tokkipfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.fit.Badge
import mx.loperstack.tokkip.fit.Effort
import mx.loperstack.tokkip.fit.badges
import mx.loperstack.tokkip.fit.weekChallenge
import mx.loperstack.tokkipfit.data.Prefs
import mx.loperstack.tokkipfit.data.Today
import mx.loperstack.tokkipfit.data.fmt
import kotlin.math.roundToLong

fun effortOf(today: Today) = Effort(
    done = Prefs.workouts(),
    steps = if (today.sample) emptyList() else today.week.map { it.steps },
    stepGoal = today.goals.steps,
    streak = if (today.sample) 0 else today.streak,
    records = Prefs.recordsBeaten,
)

fun badgeIcon(key: String): ImageVector = when (key) {
    "bolt" -> Icons.Rounded.Bolt
    "repeat" -> Icons.Rounded.Repeat
    "trophy" -> Icons.Rounded.EmojiEvents
    "fire" -> Icons.Rounded.Whatshot
    "walk" -> Icons.AutoMirrored.Rounded.DirectionsWalk
    "route" -> Icons.Rounded.Route
    "timer" -> Icons.Rounded.Timer
    "gym" -> Icons.Rounded.FitnessCenter
    "breathe" -> Icons.Rounded.Air
    "watch" -> Icons.Rounded.Watch
    "sun" -> Icons.Rounded.WbSunny
    else -> Icons.Rounded.Star
}

private val SHAPES = listOf(MShapes.Cookie9, MShapes.Sunny, MShapes.Clover4, MShapes.Flower, MShapes.Cookie6, MShapes.VerySunny, MShapes.SoftBurst, MShapes.Clover8)

/** This week's challenge, for Hoy. */
@Composable
fun WeekCard(today: Today, onBadges: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fit = LocalFit.current
    val e = remember(today) { effortOf(today) }
    val c = remember(e) { weekChallenge(e) }
    val earned = remember(e) { badges(e).count { it.earned } }
    val bar by animateFloatAsState(c.progress, tween(1200), label = "week")
    FitCard(color = cs.tertiaryContainer, onClick = onBadges) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reto de la semana", style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
            Row(Modifier.clip(CircleShape).background(cs.tertiary).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEvents, null, tint = cs.onTertiary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("$earned logros", style = MaterialTheme.typography.labelLarge, color = cs.onTertiary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(badgeIcon(c.icon), cs.tertiary, cs.onTertiary, 52.dp, MShapes.SoftBurst)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (c.done) "¡Reto cumplido!" else c.title, style = MaterialTheme.typography.titleLarge, color = cs.onTertiaryContainer)
                Text(c.about, style = MaterialTheme.typography.bodyMedium, color = cs.onTertiaryContainer.copy(alpha = 0.85f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(cs.onTertiaryContainer.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(bar).height(12.dp).clip(CircleShape).background(if (c.done) fit.minutes else cs.tertiary))
        }
        Spacer(Modifier.height(6.dp))
        val v = if (c.unit == "km") String.format(java.util.Locale.US, "%.1f", c.value) else fmt(c.value.roundToLong())
        val g = if (c.unit == "km") String.format(java.util.Locale.US, "%.0f", c.goal) else fmt(c.goal.roundToLong())
        Text("$v de $g ${c.unit}", style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer)
    }
}

@Composable
fun BadgesScreen(today: Today, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val list = remember(today) { badges(effortOf(today)).sortedByDescending { it.progress } }
    var jump by remember { mutableIntStateOf(0) }
    BackHandler { onBack() }
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp),
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
                    Spacer(Modifier.width(12.dp))
                    Title("Logros", size = 34.sp)
                }
                Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                    MorphingBlob(cs.primaryContainer, Modifier.size(150.dp))
                    Tokkip(Modifier.size(130.dp, 114.dp), color = Color(Prefs.color), mood = Mood.HAPPY, jump = jump, interactive = true, greet = true)
                }
                Text(
                    "${list.count { it.earned }} de ${list.size} logros", style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                )
            }
        }
        itemsIndexed(list, key = { _, b -> b.id }) { i, b ->
            Appear(i) { BadgeTile(b, SHAPES[i % SHAPES.size]) { if (b.earned) { jump++; Sounds.play("proud"); Haptics.success(context) } } }
        }
    }
}

@Composable
private fun BadgeTile(b: Badge, shape: androidx.graphics.shapes.RoundedPolygon, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fit = LocalFit.current
    FitCard(color = if (b.earned) cs.secondaryContainer else cs.surfaceContainer, onClick = onClick) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ShapeIcon(if (b.earned) badgeIcon(b.icon) else Icons.Rounded.Lock, if (b.earned) cs.secondary else fit.track, if (b.earned) cs.onSecondary else cs.onSurfaceVariant, 64.dp, shape)
        }
        Spacer(Modifier.height(10.dp))
        Text(b.name, style = MaterialTheme.typography.titleMedium, color = if (b.earned) cs.onSecondaryContainer else cs.onSurface, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(b.about, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (!b.earned) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(fit.track)) {
                Box(Modifier.fillMaxWidth(b.progress).height(6.dp).clip(CircleShape).background(cs.primary))
            }
        }
    }
}
