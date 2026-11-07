// The video chat with Tokkip: the camera fills the screen, you talk like in a
// call, and each time you ask something Tokkip looks at what the camera sees
// right then (one frame, sent to Gemma on the phone; nothing is saved or sent
// anywhere). "¿Qué ves?" asks without talking.

package mx.loperstack.tokkip.ui

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.character.Squircle
import mx.loperstack.tokkip.character.Tokkip
import java.io.ByteArrayOutputStream

/** What the camera sees right now, as a small JPEG for Gemma. */
class Eyes {
    var view: PreviewView? = null

    /** The current frame, its longest side at most [max] px. Call on the main thread. */
    fun grab(max: Int = 768): ByteArray? {
        val frame = view?.bitmap ?: return null
        val scale = max.toFloat() / maxOf(frame.width, frame.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true) else frame
        return ByteArrayOutputStream().use { out ->
            small.compress(Bitmap.CompressFormat.JPEG, 82, out)
            out.toByteArray()
        }
    }
}

@Composable
fun VideoPanel(
    eyes: Eyes,
    state: String,
    last: String?,
    color: Color,
    speaking: Boolean,
    sees: Boolean,
    modifier: Modifier,
    look: () -> Unit,
    interrupt: () -> Unit,
    hangUp: () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var front by remember { mutableStateOf(false) }
    val preview = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // A TextureView underneath, so the frame can be read back for Tokkip.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(front) {
        eyes.view = preview
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            provider = runCatching { future.get() }.getOrNull() ?: return@addListener
            val p = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            runCatching {
                provider?.unbindAll()
                provider?.bindToLifecycle(owner, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, p)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { provider?.unbindAll() }
            eyes.view = null
        }
    }

    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(Squircle(30.dp)).background(Color.Black)) {
        AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())

        // Tokkip in the corner, like the small window of a video call.
        Box(
            Modifier.align(Alignment.TopStart).padding(12.dp).clip(Squircle(22.dp)).background(Color(0x99101018)).padding(8.dp),
        ) {
            Tokkip(Modifier.size(78.dp, 64.dp), color = color, mood = if (speaking) Mood.HAPPY else Mood.IDLE, interactive = true)
        }
        if (!sees) {
            Text(
                "Mi vista no cargó en este teléfono: te escucho, pero no veo la cámara.",
                fontSize = 13.sp, color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).width(180.dp).clip(Squircle(14.dp)).background(Color(0xCC8A1C2B)).padding(10.dp),
            )
        }

        // What's happening and her last answer, over a shade so it reads on any picture.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000), Color(0xEE000000))))
                .padding(horizontal = 18.dp).padding(top = 40.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (last != null) {
                Text(last.takeLast(260), fontSize = 16.sp, color = Color.White, lineHeight = 21.sp, maxLines = 5)
                Spacer(Modifier.height(10.dp))
            }
            Text(
                state, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD9CCFF),
                modifier = Modifier.clip(Squircle(12.dp)).pointerInput(Unit) {
                    awaitEachGesture { awaitFirstDown(); if (waitForUpOrCancellation() != null) interrupt() }
                }.padding(6.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CircleButton(Icons.Rounded.Cameraswitch, "Cambiar cámara", size = 52.dp, container = Color(0x33FFFFFF), tint = Color.White) { front = !front }
                ExpressiveButton("¿Qué ves?", icon = Icons.Rounded.Visibility, onClick = look)
                CircleButton(Icons.Rounded.CallEnd, "Colgar", size = 52.dp, container = Ink.Red, tint = Color.White, onClick = hangUp)
            }
        }
    }
}
