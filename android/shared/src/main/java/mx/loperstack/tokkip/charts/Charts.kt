// Charts shared by the phone and the watch: the session's "cardiogram" — a
// monitor-style trace that replays your real beat intervals (an illustration
// of the rhythm, not an ECG) — and the pulse curve through the session.

package mx.loperstack.tokkip.charts

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import mx.loperstack.tokkip.wellness.BreathSession
import mx.loperstack.tokkip.wellness.HR_STEP_S
import kotlin.math.exp

/** The beats to draw: real intervals when there are, else built from the pulse readings. */
fun beatIntervals(s: BreathSession): List<Int> {
    if (s.rr.size >= 20) return s.rr.filter { it in 300..2000 }
    val out = ArrayList<Int>()
    for (bpm in s.hr) {
        if (bpm <= 0) continue
        val rr = 60_000 / bpm
        var left = HR_STEP_S * 1000
        while (left > 0) { out += rr; left -= rr }
    }
    return out
}

/** One heartbeat's shape, phase 0…1: P, Q, R, S and T waves. */
private fun ecg(p: Float): Float {
    fun g(c: Float, w: Float, a: Float) = a * exp(-((p - c) / w) * ((p - c) / w))
    return g(0.18f, 0.035f, 0.12f) + g(0.29f, 0.012f, -0.12f) + g(0.31f, 0.012f, 1f) + g(0.335f, 0.014f, -0.25f) + g(0.58f, 0.06f, 0.3f)
}

/**
 * A monitor trace replaying `intervals` (ms) in real time, `windowMs` wide.
 * Only the Canvas redraws each frame.
 */
@Composable
fun Cardiogram(intervals: List<Int>, modifier: Modifier, line: Color = Color(0xFF4ADE80), grid: Color = Color(0x1A4ADE80), windowMs: Int = 4000) {
    var now by remember { mutableLongStateOf(0L) }
    val beats = remember(intervals) { intervals.ifEmpty { listOf(1000) } }
    val starts = remember(beats) { IntArray(beats.size).also { var t = 0; for (i in beats.indices) { it[i] = t; t += beats[i] } } }
    val total = remember(beats) { beats.sum().coerceAtLeast(1) }
    LaunchedEffect(beats) {
        val t0 = withFrameMillis { it }
        while (true) withFrameMillis { now = it - t0 }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // The paper: small squares.
        val cell = h / 8
        var gx = 0f
        while (gx < w) { drawLine(grid, Offset(gx, 0f), Offset(gx, h), 1f); gx += cell }
        var gy = 0f
        while (gy < h) { drawLine(grid, Offset(0f, gy), Offset(w, gy), 1f); gy += cell }

        val tNow = (now + windowMs) % total
        val path = Path()
        val base = h * 0.68f
        val amp = h * 0.55f
        val step = 2f
        var x = 0f
        var first = true
        var lastY = base
        while (x <= w) {
            var t = (tNow - windowMs + (x / w) * windowMs) % total
            if (t < 0) t += total
            // Which beat: binary search over the starts.
            var lo = 0
            var hi = starts.size - 1
            while (lo < hi) { val mid = (lo + hi + 1) / 2; if (starts[mid] <= t) lo = mid else hi = mid - 1 }
            val phase = ((t - starts[lo]) / beats[lo].toFloat()).coerceIn(0f, 1f)
            val y = if (intervals.isEmpty()) base else base - ecg(phase) * amp
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            lastY = y
            x += step
        }
        drawPath(path, line, style = Stroke(width = h * 0.025f + 1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(line.copy(alpha = 0.35f), h * 0.07f, Offset(w, lastY))
        drawCircle(line, h * 0.03f, Offset(w, lastY))
    }
}

/** The pulse through the session: a line with a soft fill, start and end marked. */
@Composable
fun PulseCurve(s: BreathSession, modifier: Modifier, line: Color, track: Color) {
    val points = remember(s) { s.hr.mapIndexedNotNull { i, v -> if (v > 0) i to v else null } }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawLine(track, Offset(0f, h - 1f), Offset(w, h - 1f), 2f)
        if (points.size < 2) return@Canvas
        val lo = (points.minOf { it.second } - 4).toFloat()
        val hi = (points.maxOf { it.second } + 4).toFloat()
        val n = (s.hr.size - 1).coerceAtLeast(1).toFloat()
        fun px(i: Int) = w * i / n
        fun py(v: Int) = h - (v - lo) / (hi - lo) * h * 0.9f - h * 0.05f
        val p = Path()
        points.forEachIndexed { k, (i, v) -> if (k == 0) p.moveTo(px(i), py(v)) else p.lineTo(px(i), py(v)) }
        val fill = Path().apply {
            addPath(p)
            lineTo(px(points.last().first), h)
            lineTo(px(points.first().first), h)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), line.copy(alpha = 0f))))
        drawPath(p, line, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val (i0, v0) = points.first()
        val (i1, v1) = points.last()
        drawCircle(Color(0xFF8E939C), 5f, Offset(px(i0), py(v0)))
        drawCircle(line, 6f, Offset(px(i1), py(v1)))
    }
}
