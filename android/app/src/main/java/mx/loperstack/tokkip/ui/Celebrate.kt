// When Tokkip (and you) reach a new stage: a full-screen party. A Material 3
// Expressive cookie pops in, spins and morphs into a clover while little
// shapes burst out of it; confetti rains; Tokkip dances on top with starry
// eyes; and a few words to celebrate yourself — he is your reflection, so
// his growth is the order and care you gave your own days.

package mx.loperstack.tokkip.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.loperstack.tokkip.data.Growth
import mx.loperstack.tokkip.character.Haptics
import mx.loperstack.tokkip.character.MShapes
import mx.loperstack.tokkip.character.Tokkip
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.character.MorphShape
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.character.pathFor
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val CONFETTI = listOf(
    Color(0xFFF472B6), Color(0xFFFBBF24), Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFA78BFA), Color(0xFFF97316), Color(0xFF22D3EE),
)

/** Words to celebrate yourself, one per stage (and for the turns past Leyenda). */
private fun cheer(g: Growth): String = when (g.stage.level) {
    2 -> "Empezaste a escucharte. Cada nota y cada racha fueron cariño para ti."
    3 -> "Ya hay ritmo en tus días. Eso no es suerte: es que te estás cuidando."
    4 -> "Seguiste aun en los días pesados. Tokkip lo vio, y tú también deberías."
    5 -> "Conoces tus ritmos y les das orden. Esa calma la construiste tú."
    6 -> if (g.loops > 0) "Otra vuelta al ciclo. Cuidarte ya es parte de quien eres." else "Leyenda: lo que haces por ti ya es costumbre. Celébralo en grande."
    else -> "Cada vez que te cuidas, Tokkip crece. Él es tu reflejo."
}

private class Bit(
    val x: Float, val y: Float, val vx: Float, val vy: Float, val spin: Float, val size: Float,
    val color: Color, val kind: Int, val delay: Float,
)

/**
 * The party. `first` = the first time growth is shown (he already grew with
 * what you'd done before). Tap anywhere or "¡A celebrar!" to close.
 */
@Composable
fun LevelUpCelebration(g: Growth, color: Color, first: Boolean, close: () -> Unit) = Celebration(
    seed = g.points, color = color,
    title = if (first) "Tokkip crece contigo" else "¡Subieron de nivel!",
    badge = "NIVEL ${g.stage.level}${if (g.loops > 0) " · ★${g.loops}" else ""} · ${g.stage.name.uppercase()}",
    body = if (first) "Por todo lo que ya hiciste por ti, hoy es Tokkip ${g.title}. Cada vez que te cuidas, él crece: es tu reflejo." else cheer(g),
    foot = "${g.points} de cariño en total", button = "¡A celebrar!", close = close,
)

/** The party itself: the shape, the confetti, Tokkip dancing and your words. Also for a finished project. */
@Composable
fun Celebration(seed: Int, color: Color, title: String, badge: String, body: String, foot: String, button: String, close: () -> Unit) {
    val context = LocalContext.current
    val pop = remember { Animatable(0f) }
    val spinA = remember { Animatable(0f) }
    val morph = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    var start by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var hop by remember { mutableIntStateOf(0) }
    val shapeMorph = remember { Morph(MShapes.Cookie12, MShapes.Clover4) }
    val rnd = remember { Random(seed) }
    // Little shapes bursting from the middle, and confetti falling from above.
    val burst = remember {
        List(14) { i ->
            val a = (i / 14f) * 6.283f + rnd.nextFloat() * 0.3f
            val sp = 520f + rnd.nextFloat() * 380f
            Bit(0f, 0f, cos(a) * sp, sin(a) * sp, rnd.nextFloat() * 360f, 18f + rnd.nextFloat() * 18f, CONFETTI[i % CONFETTI.size], i % 4, 0.25f)
        }
    }
    val confetti = remember {
        List(110) { i ->
            Bit(
                rnd.nextFloat(), -0.1f - rnd.nextFloat() * 0.6f, (rnd.nextFloat() - 0.5f) * 0.12f, 0.18f + rnd.nextFloat() * 0.22f,
                rnd.nextFloat() * 360f, 7f + rnd.nextFloat() * 9f, CONFETTI[i % CONFETTI.size], i % 3, 0.35f + rnd.nextFloat() * 0.5f,
            )
        }
    }

    LaunchedEffect(Unit) {
        Sounds.play("level_up", 0.9f)
        Haptics.success(context)
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        launch { spinA.animateTo(360f, tween(2400, easing = FastOutSlowInEasing)) }
        launch { delay(500); morph.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        launch { delay(650); words.animateTo(1f, tween(600)) }
        launch { delay(350); Haptics.spin(context) }
        // A hop now and then while he dances.
        launch { repeat(6) { delay(650); hop++ } }
        start = withFrameMillis { it }
        while (true) withFrameMillis { now = it }
    }
    val t = ((now - start) / 1000f).coerceAtLeast(0f)

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xEE2A2048), Color(0xF20B0C0E)), radius = 1600f))
                .clickable(MutableInteractionSource(), indication = null, onClick = close),
            contentAlignment = Alignment.Center,
        ) {
            // Confetti and the burst, behind everything else.
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height * 0.4f)
                for (b in burst) {
                    val tt = (t - b.delay).coerceAtLeast(0f)
                    if (tt <= 0f || tt > 1.6f) continue
                    val k = 1f - kotlin.math.exp(-tt * 2.6f)
                    val x = c.x + b.vx * k * 0.6f
                    val y = c.y + b.vy * k * 0.6f + 120f * tt * tt
                    val alpha = (1f - tt / 1.6f).coerceIn(0f, 1f)
                    val shape = when (b.kind) { 0 -> MShapes.Cookie9; 1 -> MShapes.Clover4; 2 -> MShapes.Sunny; else -> MShapes.Flower }
                    val s = b.size * density * (1f - tt / 3f)
                    translate(x - s / 2, y - s / 2) {
                        rotate(b.spin + tt * 220f, Offset(s / 2, s / 2)) {
                            drawPath(shape.pathFor(Size(s, s)), b.color.copy(alpha = alpha))
                        }
                    }
                }
                for (p in confetti) {
                    val tt = (t - p.delay).coerceAtLeast(0f)
                    if (tt <= 0f) continue
                    val y = (p.y + p.vy * tt) * size.height
                    if (y > size.height + 40f) continue
                    val x = (p.x + p.vx * tt + 0.03f * sin(tt * 3f + p.spin)) * size.width
                    val s = p.size * density * 0.6f
                    rotate(p.spin + tt * 260f, Offset(x, y)) {
                        when (p.kind) {
                            0 -> drawRect(p.color, Offset(x - s / 2, y - s / 4), Size(s, s / 2))
                            1 -> drawCircle(p.color, s / 3, Offset(x, y))
                            else -> drawPath(MShapes.Clover4.pathFor(Size(s, s)).also { it.translate(Offset(x - s / 2, y - s / 2)) }, p.color)
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
                // The expressive shape popping and morphing, with Tokkip dancing on it.
                Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(240.dp).graphicsLayer {
                            scaleX = pop.value; scaleY = pop.value; rotationZ = spinA.value
                        }.clip(MorphShape(shapeMorph, morph.value)).background(
                            Brush.linearGradient(listOf(Ink.VioletSoft, Color(0xFFF472B6), Color(0xFFFBBF24))),
                        ),
                    )
                    Box(Modifier.size(200.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)))
                    Tokkip(
                        Modifier.width(190.dp).height(160.dp).graphicsLayer { scaleX = 0.6f + 0.4f * pop.value; scaleY = 0.6f + 0.4f * pop.value },
                        color = color, face = TokkipEngine.Eye.STAR, dancing = true, jump = hop,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Column(Modifier.alpha(words.value).graphicsLayer { translationY = (1f - words.value) * 40f }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        badge,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1530),
                        modifier = Modifier.clip(CircleShape).background(Color(0xFFFBBF24)).padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        body,
                        fontSize = 17.sp, color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center, lineHeight = 24.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(foot, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.height(22.dp))
                    ExpressiveButton(button, big = true, container = Color(0xFFFBBF24), content = Color(0xFF1B1530), onClick = close)
                }
            }
        }
    }
}
