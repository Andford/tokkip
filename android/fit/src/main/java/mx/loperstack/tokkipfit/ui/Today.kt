// Hoy: your day at a glance. Tokkip in the middle of the three rings, showing
// how it's going; what you've done, rolling up; the body (sleep, resting heart
// rate); your week with its streak; and the day's challenge.

package mx.loperstack.tokkipfit.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkipfit.data.Prefs
import mx.loperstack.tokkipfit.data.Today
import mx.loperstack.tokkipfit.data.Vibe
import mx.loperstack.tokkipfit.data.challengeOf
import mx.loperstack.tokkipfit.data.distance
import mx.loperstack.tokkipfit.data.fmt
import mx.loperstack.tokkipfit.data.hours
import mx.loperstack.tokkipfit.data.lineOf
import mx.loperstack.tokkipfit.data.vibeOf
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val Spanish = Locale.forLanguageTag("es-MX")

@Composable
fun TodayScreen(today: Today, bottom: PaddingValues, onConnect: () -> Unit, onSettings: () -> Unit, onRefresh: () -> Unit, onBadges: () -> Unit = {}) {
    val fit = LocalFit.current
    val cs = MaterialTheme.colorScheme
    val vibe = vibeOf(today, LocalTime.now().hour)
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottom.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.statusBarsPadding(),
    ) {
        item {
            Appear(0) {
                Column(Modifier.padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            today.today.date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Spanish)).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(onClick = onRefresh) { Icon(Icons.Rounded.Sync, "Actualizar") }
                        Spacer(Modifier.width(6.dp))
                        FilledTonalIconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Ajustes") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Title(lineOf(vibe, today), size = 32.sp)
                }
            }
        }
        item { Appear(1) { Hero(today, vibe) } }
        item { Appear(2) { Legend(today) } }
        if (today.sample) item { Appear(3) { ConnectCard(onConnect) } }
        item {
            Appear(4) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat(Modifier.weight(1f), Icons.Rounded.Route, fit.steps, MShapes.Cookie9, "Distancia", distance(today.today.meters))
                        Stat(Modifier.weight(1f), Icons.Rounded.LocalFireDepartment, fit.kcal, MShapes.Sunny, "Calorías activas", "${fmt(today.today.kcal.roundToInt().toLong())} kcal")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat(Modifier.weight(1f), Icons.Rounded.Bedtime, fit.sleep, MShapes.Clover4, "Dormiste", today.body.sleepMin?.let(::hours) ?: "—")
                        Stat(Modifier.weight(1f), Icons.Rounded.Favorite, fit.heart, MShapes.Flower, "Pulso en reposo", today.body.restingBpm?.let { "$it lpm" } ?: "—")
                    }
                }
            }
        }
        item { Appear(5) { Week(today) } }
        item { Appear(6) { ChallengeCard(today) } }
        item { Appear(7) { WeekCard(today, onBadges) } }
    }
}

/** Tokkip inside the rings, on a slowly morphing shape. */
@Composable
private fun Hero(today: Today, vibe: Vibe) {
    val context = LocalContext.current
    var jump by remember { mutableIntStateOf(0) }
    var cheered by rememberSaveable { mutableIntStateOf(0) }
    // Closing the three rings is a party, once per opening.
    LaunchedEffect(vibe) {
        if (vibe == Vibe.CHAMPION && cheered == 0) {
            cheered = 1
            delay(900)
            Sounds.play("level_up")
            Haptics.success(context)
            jump++
        }
    }
    val (mood, face) = when (vibe) {
        Vibe.CHAMPION -> Mood.HAPPY to TokkipEngine.Eye.STAR
        Vibe.GREAT -> Mood.HAPPY to null
        Vibe.MOVING -> Mood.IDLE to null
        Vibe.LAZY -> Mood.SLEEPY to null
        Vibe.STARTING -> Mood.IDLE to null
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Rings(today.stepsRing, today.minutesRing, today.kcalRing, Modifier.size(290.dp), stroke = 20.dp, gap = 6.dp) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                MorphingBlob(MaterialTheme.colorScheme.primaryContainer, Modifier.size(150.dp))
                Tokkip(
                    Modifier.size(126.dp, 112.dp),
                    color = Color(Prefs.color),
                    mood = mood,
                    face = face,
                    jump = jump,
                    interactive = true,
                    greet = true,
                    dancing = vibe == Vibe.CHAMPION,
                )
            }
        }
    }
}

/** What each ring means, with the numbers rolling up. */
@Composable
private fun Legend(today: Today) {
    val fit = LocalFit.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LegendItem(Modifier.weight(1f), fit.steps, "Pasos", today.today.steps, today.goals.steps)
        LegendItem(Modifier.weight(1f), fit.minutes, "Minutos", today.today.exerciseMin, today.goals.minutes)
        LegendItem(Modifier.weight(1f), fit.kcal, "Calorías", today.today.kcal.roundToInt().toLong(), today.goals.kcal)
    }
}

@Composable
private fun LegendItem(modifier: Modifier, color: Color, label: String, value: Long, goal: Long) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        RollingNumber(value, MaterialTheme.typography.titleLarge.copy(fontFamily = DisplayFont, fontSize = 22.sp))
        Text("de ${fmt(goal)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun ConnectCard(onConnect: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    FitCard(color = cs.tertiaryContainer, onClick = onConnect) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(Icons.Rounded.Sync, cs.tertiary, cs.onTertiary, 48.dp, MShapes.SoftBurst)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Estos son datos de ejemplo", style = MaterialTheme.typography.titleMedium, color = cs.onTertiaryContainer)
                Text(
                    "Conecta Health Connect y Tokkip verá lo que registran Samsung Health, Fitbit y tu reloj.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onTertiaryContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, shape: androidx.graphics.shapes.RoundedPolygon, label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    FitCard(modifier) {
        ShapeIcon(icon, color.copy(alpha = 0.18f), color, 42.dp, shape)
        Spacer(Modifier.height(12.dp))
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFamily = DisplayFont, fontSize = 20.sp), color = cs.onSurface, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
    }
}

/** The week's steps as bars that grow one after another, the goal as a dashed line, and the streak. */
@Composable
private fun Week(today: Today) {
    val fit = LocalFit.current
    val cs = MaterialTheme.colorScheme
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tu semana", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            val streak = today.streak
            Row(
                Modifier.clip(CircleShape).background(if (streak > 0) fit.kcal.copy(alpha = 0.16f) else fit.track).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Whatshot, null, tint = if (streak > 0) fit.kcal else cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    when (streak) { 0 -> "Sin racha"; 1 -> "1 día"; else -> "$streak días" },
                    style = MaterialTheme.typography.labelLarge, color = if (streak > 0) fit.kcal else cs.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val days = today.week
        val top = maxOf(days.maxOfOrNull { it.steps } ?: 0L, today.goals.steps).toFloat() * 1.12f
        val grow = days.map { remember(it.date) { Animatable(0f) } }
        days.forEachIndexed { i, d ->
            LaunchedEffect(d.date, d.steps) {
                delay(300L + i * 70L)
                grow[i].animateTo((d.steps / top).coerceIn(0f, 1f), spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
            }
        }
        val goalFrac = today.goals.steps / top
        val line = cs.onSurfaceVariant.copy(alpha = 0.45f)
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val n = days.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.56f
            days.forEachIndexed { i, d ->
                val h = size.height * grow[i].value
                val x = slot * i + (slot - bw) / 2
                val met = d.steps >= today.goals.steps
                val color = when {
                    i == n - 1 -> fit.steps
                    met -> fit.steps.copy(alpha = 0.65f)
                    else -> fit.steps.copy(alpha = 0.28f)
                }
                drawRoundRect(fit.track, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2))
                if (h > 0f) drawRoundRect(color, Offset(x, size.height - h), Size(bw, h.coerceAtLeast(bw)), CornerRadius(bw / 2))
            }
            val y = size.height * (1f - goalFrac)
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                Text(
                    d.date.format(DateTimeFormatter.ofPattern("EEEEE", Spanish)).uppercase(),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (i == days.size - 1) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (i == days.size - 1) fit.steps else cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChallengeCard(today: Today) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val c = challengeOf(today)
    FitCard(color = cs.secondaryContainer, onClick = { Sounds.play("pop"); Haptics.tick(context) }) {
        Text("Reto del día", style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer.copy(alpha = 0.8f))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapeIcon(challengeIcon(c.icon), cs.secondary, cs.onSecondary, 56.dp, MShapes.VerySunny)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleLarge, color = cs.onSecondaryContainer)
                Text(c.body, style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer.copy(alpha = 0.85f))
            }
        }
    }
}
