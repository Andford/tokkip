// Tokkip in Jetpack Compose: TokkipEngine (the same motion as Tokkip PC's
// windows/src/tokkip/motion.ts), drawn on a Canvas.
//
// Smooth and cheap: the engine runs on the frame clock only while something
// moves, and between blinks the loop sleeps. A frame only redraws the Canvas,
// it never recomposes the screen around it.

package mx.loperstack.tokkip.character

import androidx.compose.ui.Alignment
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.withTimeoutOrNull
import mx.loperstack.tokkip.wellness.Breathing
import kotlin.math.max
import kotlin.math.tanh

/** What Tokkip is up to. */
enum class Mood { IDLE, HAPPY, FOCUS, BREATHING, SLEEPY }

/**
 * White first — the PC's own Tokkip — then the PC's pill colours, pastels and
 * deep tones. Any other colour can be picked by hand (the app's colour sheet).
 */
val TOKKIP_PALETTE = listOf(
    // The PC's own, and its pill colours.
    0xFFF5F6F8, 0xFFF4505E, 0xFFF29B38, 0xFFFACC15, 0xFF4ADE80, 0xFF2DD4BF,
    0xFF38BDF8, 0xFF818CF8, 0xFFA78BFA, 0xFFC084FC, 0xFFE879F9, 0xFFFB7185,
    // Pastels.
    0xFFFECACA, 0xFFFED7AA, 0xFFFEF08A, 0xFFBBF7D0, 0xFFA5F3FC, 0xFFBFDBFE,
    0xFFDDD6FE, 0xFFF5D0FE, 0xFFFBCFE8, 0xFFE7E5E4, 0xFFD6D3D1, 0xFFCBD5E1,
    // Deep tones.
    0xFFB91C1C, 0xFFC2410C, 0xFFA16207, 0xFF15803D, 0xFF0F766E, 0xFF0369A1,
    0xFF1D4ED8, 0xFF4338CA, 0xFF6D28D9, 0xFFA21CAF, 0xFFBE185D, 0xFF334155,
).map { Color(it) }

/**
 * Tokkip, drawn to fill the composable's width (keep it about 1.2 : 1).
 *
 * @param color white is the PC's Tokkip; any other colour tints him.
 * @param breathing a running session: he closes his eyes and grows with each inhale.
 * @param jump change it to make him hop (a task done).
 * @param interactive taps slap him, as on the PC — three quick ones and he gets
 *   dizzy and says so; a long press gets hearts.
 * @param greet wave hello when he first appears.
 * @param dancing music is playing: he bounces to it, with happy eyes.
 * @param face fixed eyes (the diary's moods).
 */
@Composable
fun Tokkip(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF5F6F8),
    mood: Mood = Mood.IDLE,
    breathing: Breathing? = null,
    jump: Int = 0,
    interactive: Boolean = false,
    greet: Boolean = false,
    dancing: Boolean = false,
    face: TokkipEngine.Eye? = null,
    wear: Boolean = face == null,
    /** Your own engine, to drive him from outside (the Mimos screen). */
    engine: TokkipEngine? = null,
    /** A long sigh when he appears (the breathing screen). */
    sigh: Boolean = false,
    /** The sound of his hello (each screen can have its own). */
    greetSound: String = "greet",
) {
    val own = remember { TokkipEngine() }
    val engine = engine ?: own
    var frame by remember { mutableLongStateOf(0L) }
    SideEffect { engine.configure(color, mood, breathing, dancing, face, wear) }

    LaunchedEffect(jump) { if (jump > 0) engine.cheer() }

    LaunchedEffect(engine) {
        engine.configure(color, mood, breathing, dancing, face, wear)
        engine.greetSound = greetSound
        if (greet) engine.greet()
        if (sigh) { kotlinx.coroutines.delay(350); engine.sigh() }
        var last = System.nanoTime()
        while (true) {
            if (engine.busy) {
                withFrameNanos { }
            } else {
                withTimeoutOrNull(engine.idleWaitMs()) { engine.wakeups.receive() }
                withFrameNanos { }
            }
            val n = System.nanoTime()
            engine.update((n - last) / 1e9f)
            last = n
            frame++
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val touch = if (interactive) {
        Modifier.pointerInput(engine) {
            fun toLook(o: Offset) = Offset(
                tanh((o.x - size.width / 2f) / (size.width * 0.5f)),
                -tanh((o.y - size.height / 2f) / (size.height * 0.5f)),
            )
            detectTapGestures(
                onPress = { o ->
                    engine.lookAt(toLook(o))
                    tryAwaitRelease()
                    engine.lookAt(null)
                },
                onTap = {
                    val wasDizzy = engine.dizzy.value
                    engine.slap()
                    if (!wasDizzy) { if (engine.dizzy.value) Haptics.spin(context) else Haptics.thud(context) }
                },
                onLongPress = {
                    engine.triggerEmote(TokkipEngine.Emote.LOVE)
                    Sounds.play("love")
                    Haptics.success(context)
                },
            )
        }
    } else Modifier

    Box(modifier) {
        Canvas(Modifier.matchParentSize().then(touch)) {
            if (frame < 0) return@Canvas // read: redraw on each engine frame
            engine.draw(this)
        }
        if (engine.dizzy.value) DizzyBubble()
    }
}

/** A little cloud above Tokkip, bobbing: he took too many hits and he's dizzy. */
@Composable
private fun DizzyBubble() {
    val small = LocalConfiguration.current.screenWidthDp < 300
    val bob by androidx.compose.animation.core.rememberInfiniteTransition(label = "cloud").animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400), androidx.compose.animation.core.RepeatMode.Reverse), label = "bob",
    )
    Popup(popupPositionProvider = AboveAnchor) {
        Box(
            Modifier.graphicsLayer { translationY = -6f * bob }
                .drawBehind { cloud(Color(0xFFFFF7FB), Color(0xFFF9A8D4)) }
                .padding(start = if (small) 18.dp else 26.dp, end = if (small) 18.dp else 26.dp, top = if (small) 14.dp else 20.dp, bottom = if (small) 22.dp else 30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(
                    "¡Me mareaste! 💫",
                    style = TextStyle(color = Color(0xFF3B2340), fontSize = if (small) 13.sp else 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                )
                BasicText(
                    if (small) "Dame un segundo…" else "Dame tres segunditos…",
                    style = TextStyle(color = Color(0xFFB0457E), fontSize = if (small) 11.sp else 13.sp, textAlign = TextAlign.Center),
                )
            }
        }
    }
}

/** A puffy cloud filling the box (its bottom part is the tail of little puffs), with a soft outline. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.cloud(fill: Color, line: Color) {
    val w = size.width; val h = size.height
    val body = h * 0.78f
    fun puffs(grow: Float, c: Color) {
        val r = body * 0.36f + grow
        drawRoundRect(c, androidx.compose.ui.geometry.Offset(w * 0.06f - grow, body * 0.34f - grow), androidx.compose.ui.geometry.Size(w * 0.88f + 2 * grow, body * 0.52f + 2 * grow), androidx.compose.ui.geometry.CornerRadius(body * 0.26f + grow))
        drawCircle(c, r, androidx.compose.ui.geometry.Offset(w * 0.3f, body * 0.42f))
        drawCircle(c, r * 1.12f, androidx.compose.ui.geometry.Offset(w * 0.55f, body * 0.36f))
        drawCircle(c, r * 0.9f, androidx.compose.ui.geometry.Offset(w * 0.78f, body * 0.48f))
        drawCircle(c, r * 0.8f, androidx.compose.ui.geometry.Offset(w * 0.16f, body * 0.6f))
        drawCircle(c, r * 0.8f, androidx.compose.ui.geometry.Offset(w * 0.86f, body * 0.66f))
        // The tail: two little puffs down toward Tokkip.
        drawCircle(c, body * 0.1f + grow, androidx.compose.ui.geometry.Offset(w * 0.46f, body + h * 0.06f))
        drawCircle(c, body * 0.06f + grow, androidx.compose.ui.geometry.Offset(w * 0.42f, body + h * 0.17f))
    }
    puffs(2.5f * density, line)
    puffs(0f, fill)
}

private object AboveAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, max(0, windowSize.width - popupContentSize.width))
        val y = (anchorBounds.top - popupContentSize.height + anchorBounds.height / 10).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/**
 * Whether music is playing on this device (any app: Spotify, YouTube Music…),
 * checked every 2 s while the screen shows — the frame clock pauses the loop
 * when the app is in the background.
 */
@Composable
fun rememberMusicPlaying(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    var playing by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        while (true) {
            withFrameNanos { }
            val now = runCatching { audio?.isMusicActive == true }.getOrDefault(false)
            if (now != playing) playing = now
            kotlinx.coroutines.delay(2000)
        }
    }
    return playing
}

/** Tokkip at rest as a bitmap — for home screen widgets. */
fun tokkipBitmap(
    widthPx: Int, heightPx: Int, color: Color, mood: Mood = Mood.IDLE, face: TokkipEngine.Eye? = null,
    wear: Boolean = face == null,
    /** A pose for animated widgets: vertical squash (1 = rest) and lift (px of the unit box). */
    squash: Float = 1f, lift: Float = 0f,
    /** Empty room above him, as a share of the height, so hats and ears (and a hop) fit. */
    headroom: Float = 0f,
): Bitmap {
    val image = ImageBitmap(widthPx, heightPx)
    val engine = TokkipEngine().apply {
        configure(color, mood, null, false, face, wear)
        settle()
        pose(squash, lift)
    }
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(widthPx.toFloat(), heightPx.toFloat())) {
        val top = heightPx * headroom.coerceIn(0f, 0.6f)
        val inner = heightPx - top
        val side = ((widthPx - inner * 1.2f) / 2f).coerceAtLeast(0f)
        inset(side, top, side, 0f) { engine.draw(this) }
    }
    return image.asAndroidBitmap()
}
