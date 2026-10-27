// "El color de Tokkip": 36 ready colours, your Pixel's own colour, and any
// colour at all with the hue, vividness and brightness sliders.

package mx.loperstack.tokkip.ui

import mx.loperstack.tokkip.character.Squircle
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.loperstack.tokkip.character.TOKKIP_PALETTE
import mx.loperstack.tokkip.character.Tokkip

fun argbOf(c: Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL

internal fun hsv(c: Color): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }
internal fun fromHsv(h: Float, s: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

/** A rounded gradient track with a round thumb; drag or tap anywhere on it. */
@Composable
fun GradientSlider(value: Float, brush: Brush, thumb: Color, onChange: (Float) -> Unit) {
    Canvas(
        Modifier.fillMaxWidth().height(34.dp)
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> onChange((change.position.x / size.width).coerceIn(0f, 1f)) }
            },
    ) {
        val h = size.height * 0.42f
        val top = (size.height - h) / 2
        drawRoundRect(brush, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, h), CornerRadius(h / 2, h / 2))
        val r = size.height / 2 - 2f
        val x = (value * size.width).coerceIn(r, size.width - r)
        drawCircle(Color.White, r, Offset(x, size.height / 2))
        drawCircle(thumb, r - 5f, Offset(x, size.height / 2))
        drawCircle(Color.Black.copy(alpha = 0.15f), r, Offset(x, size.height / 2), style = Stroke(1.5f))
    }
}

@Composable
fun ColorPicker(current: Long, color: Color, onPick: (Long) -> Unit) {
    val start = remember { hsv(Color(current)) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    var custom by remember { mutableStateOf(false) }
    val shown by animateColorAsState(color, label = "tokkip")
    val pixel = MaterialTheme.colorScheme.primary

    fun pickHsv() = onPick(argbOf(fromHsv(hue, sat, value)))

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("El color de Tokkip", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.width(170.dp).height(140.dp).clip(Squircle(36.dp))
                .background(Brush.radialGradient(listOf(shown.copy(alpha = 0.35f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) { Tokkip(Modifier.width(150.dp).height(124.dp), color = color, interactive = true) }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segment("Colores", !custom) { custom = false }
            Segment("A tu gusto", custom) { custom = true }
            Segment("De mi Pixel", false) {
                onPick(argbOf(pixel))
                hsv(pixel).let { hue = it[0]; sat = it[1]; value = it[2] }
            }
        }
        Spacer(Modifier.height(14.dp))

        if (!custom) {
            for (row in TOKKIP_PALETTE.chunked(6)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in row) {
                        val argb = argbOf(c)
                        val on = argb == current
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(c)
                                .border(if (on) 3.dp else 1.dp, if (on) Ink.Text else Ink.Hairline, CircleShape)
                                .clickable {
                                    onPick(argb)
                                    mx.loperstack.tokkip.character.Sounds.play("pop")
                                    hsv(c).let { hue = it[0]; sat = it[1]; value = it[2] }
                                },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        } else {
            Label("Tono", Ink.Dim, 12.sp, FontWeight.Bold)
            GradientSlider(
                hue / 360f,
                Brush.horizontalGradient((0..6).map { fromHsv(it * 60f, 1f, 1f) }),
                fromHsv(hue, 1f, 1f),
            ) { hue = it * 360f; pickHsv() }
            Spacer(Modifier.height(10.dp))
            Label("Intensidad", Ink.Dim, 12.sp, FontWeight.Bold)
            GradientSlider(sat, Brush.horizontalGradient(listOf(fromHsv(hue, 0f, value), fromHsv(hue, 1f, value))), fromHsv(hue, sat, value)) {
                sat = it; pickHsv()
            }
            Spacer(Modifier.height(10.dp))
            Label("Brillo", Ink.Dim, 12.sp, FontWeight.Bold)
            GradientSlider(value, Brush.horizontalGradient(listOf(Color.Black, fromHsv(hue, sat, 1f))), fromHsv(hue, sat, value)) {
                value = it.coerceAtLeast(0.15f); pickHsv()
            }
            Spacer(Modifier.height(10.dp))
            Text("#%06X".format(argbOf(color) and 0xFFFFFF), color = Ink.Dim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Label("Blanco es el Tokkip de la PC; los demás colores lo tiñen.", Ink.Dim2)
    }
}

@Composable
fun Segment(text: String, on: Boolean, onClick: () -> Unit) = Pill(text, on, onClick = onClick)
