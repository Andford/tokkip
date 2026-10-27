// Writing by hand with the pen (the Legion Tab's, or any Android stylus): a
// surface that draws a Drawing (data/Ink.kt), the toolbar (pluma,
// marcatextos, borrador, colours, sizes, undo), a whole handwritten page and a
// drawing inside a page of text — the same as Windows (notas-pc/src/ink).
//
// The pen's pressure sets the width; its eraser end or its side button
// erases. Fingers scroll the page (so the palm never draws) unless
// "Con el dedo" is on; then they draw too.

package mx.loperstack.tokkipnotas.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.RequestDisallowInterceptTouchEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mx.loperstack.tokkip.character.Squircle
import mx.loperstack.tokkipnotas.data.Drawing
import mx.loperstack.tokkipnotas.data.HL_COLORS
import mx.loperstack.tokkipnotas.data.HL_SIZE
import mx.loperstack.tokkipnotas.data.INK_W
import mx.loperstack.tokkipnotas.data.PEN_COLORS
import mx.loperstack.tokkipnotas.data.PEN_SIZES
import mx.loperstack.tokkipnotas.data.Page
import mx.loperstack.tokkipnotas.data.Store
import mx.loperstack.tokkipnotas.data.Stroke
import mx.loperstack.tokkipnotas.data.Sync
import mx.loperstack.tokkipnotas.data.drawingJson
import mx.loperstack.tokkipnotas.data.drawingOf
import mx.loperstack.tokkipnotas.data.lowest
import mx.loperstack.tokkipnotas.data.strokesHit
import mx.loperstack.tokkipnotas.data.widthAt

enum class Tool { PEN, HL, ERASER }

/** The pen's settings, remembered between pages (the eraser is only for a moment). */
object InkTools {
    var tool by mutableStateOf(Tool.PEN)
    var pen by mutableStateOf(0L)
    var hl by mutableStateOf(HL_COLORS[0])
    var size by mutableStateOf(PEN_SIZES[1])
    var finger by mutableStateOf(false)
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences("notas", Context.MODE_PRIVATE)
        tool = if (p.getString("ink.tool", "pen") == "hl") Tool.HL else Tool.PEN
        pen = p.getLong("ink.pen", 0L)
        hl = p.getLong("ink.hl", HL_COLORS[0])
        size = p.getFloat("ink.size", PEN_SIZES[1])
        finger = p.getBoolean("ink.finger", false)
    }

    fun save(context: Context) {
        context.getSharedPreferences("notas", Context.MODE_PRIVATE).edit()
            .putString("ink.tool", if (tool == Tool.HL) "hl" else "pen").putLong("ink.pen", pen).putLong("ink.hl", hl)
            .putFloat("ink.size", size).putBoolean("ink.finger", finger).apply()
    }
}

/** Paper, rules and ink for the theme. */
private data class PaperColors(val paper: Color, val rule: Color, val ink: Color)

private fun paperColors() = if (Ink.Dark) PaperColors(Color(0xFF1A1B20), Color(0x22C4B5FD), Color(0xFFF5F6F8))
else PaperColors(Color(0xFFFFFDF8), Color(0x29505AA0), Color(0xFF1B1530))

private fun strokeColor(s: Stroke, ink: Color) = if (s.color == 0L) ink else Color(s.color)

private fun DrawScope.background(bg: String, scale: Float, c: PaperColors) {
    drawRect(c.paper)
    val step = 40f * scale
    val line = (1.2f * scale).coerceAtLeast(1f)
    when (bg) {
        "lines" -> { var y = step * 2; while (y < size.height) { drawLine(c.rule, Offset(0f, y), Offset(size.width, y), line); y += step } }
        "grid" -> {
            var y = step; while (y < size.height) { drawLine(c.rule, Offset(0f, y), Offset(size.width, y), line); y += step }
            var x = step; while (x < size.width) { drawLine(c.rule, Offset(x, 0f), Offset(x, size.height), line); x += step }
        }
        "dots" -> {
            val r = (1.6f * scale).coerceAtLeast(1f)
            var y = step
            while (y < size.height) { var x = step; while (x < size.width) { drawCircle(c.rule, r, Offset(x, y)); x += step }; y += step }
        }
    }
}

/** One stroke, smoothed through the midpoints; a pen's width follows the pressure. */
private fun DrawScope.stroke(s: Stroke, scale: Float, ink: Color) {
    val n = s.count
    if (n == 0) return
    val color = strokeColor(s, ink)
    if (s.highlighter) {
        val path = Path().apply { moveTo(s.x(0) * scale, s.y(0) * scale); for (i in 1 until n) lineTo(s.x(i) * scale, s.y(i) * scale) }
        drawPath(path, color.copy(alpha = 0.38f), style = DrawStroke(width = s.width * scale, cap = StrokeCap.Butt, join = StrokeJoin.Round))
        return
    }
    if (n == 1) { drawCircle(color, widthAt(s.width, s.pressure(0)) * scale / 2, Offset(s.x(0) * scale, s.y(0) * scale)); return }
    var mx = s.x(0) * scale
    var my = s.y(0) * scale
    for (i in 1 until n) {
        val nx = if (i < n - 1) (s.x(i) + s.x(i + 1)) / 2 * scale else s.x(i) * scale
        val ny = if (i < n - 1) (s.y(i) + s.y(i + 1)) / 2 * scale else s.y(i) * scale
        val path = Path().apply { moveTo(mx, my); quadraticBezierTo(s.x(i) * scale, s.y(i) * scale, nx, ny) }
        drawPath(path, color, style = DrawStroke(width = (widthAt(s.width, s.pressure(i)) * scale).coerceAtLeast(0.8f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        mx = nx; my = ny
    }
}

// ── The surface ───────────────────────────────────────────────────────────────

/**
 * Draws `drawing` and lets you write on it. `onChange` gets the drawing after
 * each stroke or erase; with `grow`, the page gets longer as you write near the bottom.
 */
@OptIn(ExperimentalComposeUiApi::class)
@SuppressLint("ClickableViewAccessibility")
@Composable
fun InkSurface(drawing: Drawing, onChange: (Drawing) -> Unit, modifier: Modifier = Modifier, grow: Boolean = false) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val scale = widthPx / INK_W
        val heightDp = with(density) { (drawing.h * scale).toDp() }
        val colors = paperColors()
        // The stroke in progress (or what the eraser is about to take).
        var current by remember { mutableStateOf<Stroke?>(null) }
        val pending = remember { mutableStateListOf<Int>() }
        val live = remember { mutableListOf<Int>() }
        var erasing by remember { mutableStateOf(false) }
        var scrolling by remember { mutableStateOf(false) }
        val latest = remember { mutableStateOf(drawing) }
        latest.value = drawing
        // Without this the page around (a verticalScroll) sees the move first,
        // takes it as a scroll and cancels the stroke: the sheet slid instead of
        // drawing. Asked on every stroke's first touch; Compose lets go on lift.
        val keep = remember { RequestDisallowInterceptTouchEvent() }

        Box(Modifier.fillMaxWidth().height(heightDp)) {
            // The paper and every finished stroke, kept in their own layer.
            Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                background(drawing.bg, scale, colors)
                for (s in drawing.strokes) stroke(s, scale, colors.ink)
            }
            Canvas(
                Modifier.fillMaxSize().pointerInteropFilter(keep) { e ->
                    val drawing = handle(e, scale, latest, onChange, grow, { current = it }, { current }, live, pending, { erasing = it }, { erasing }, { scrolling = it }, { scrolling })
                    if (drawing && e.actionMasked == MotionEvent.ACTION_DOWN) keep(true)
                    drawing
                },
            ) {
                current?.let { stroke(it, scale, colors.ink) }
                if (erasing) for (i in pending) drawing.strokes.getOrNull(i)?.let { stroke(it.copy(color = 0L), scale, colors.paper) }
            }
        }
    }
}

/** The switch alone decides: fingers draw with "Con el dedo" on, and scroll with it off. */
internal fun fingerScrolls(finger: Boolean = InkTools.finger) = !finger

private fun handle(
    e: MotionEvent,
    scale: Float,
    latest: MutableState<Drawing>,
    onChange: (Drawing) -> Unit,
    grow: Boolean,
    setCurrent: (Stroke?) -> Unit,
    getCurrent: () -> Stroke?,
    points: MutableList<Int>,
    pending: MutableList<Int>,
    setErasing: (Boolean) -> Unit,
    isErasing: () -> Boolean,
    setScrolling: (Boolean) -> Unit,
    isScrolling: () -> Boolean,
): Boolean {
    val tool = e.getToolType(0)
    val stylus = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
    when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            // A finger (or a palm) scrolls the page instead, unless "Con el dedo" is on.
            if (!stylus && fingerScrolls()) { setScrolling(true); return false }
            setScrolling(false)
            val eraser = InkTools.tool == Tool.ERASER || tool == MotionEvent.TOOL_TYPE_ERASER ||
                (stylus && (e.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY)) != 0)
            val x = e.x / scale
            val y = e.y / scale
            points.clear()
            pending.clear()
            if (eraser) {
                setErasing(true)
                pending.addAll(strokesHit(latest.value, x, y, 10f))
            } else {
                setErasing(false)
                points.add(Math.round(x * 2)); points.add(Math.round(y * 2)); points.add(pressureOf(e.pressure, stylus))
                setCurrent(strokeNow(points))
            }
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            if (isScrolling()) return false
            for (h in 0..e.historySize) {
                val x = (if (h < e.historySize) e.getHistoricalX(h) else e.x) / scale
                val y = (if (h < e.historySize) e.getHistoricalY(h) else e.y) / scale
                val p = if (h < e.historySize) e.getHistoricalPressure(h) else e.pressure
                if (isErasing()) {
                    for (i in strokesHit(latest.value, x, y, 10f)) if (i !in pending) pending.add(i)
                } else if (points.size >= 3) {
                    val nx = Math.round(x * 2)
                    val ny = Math.round(y * 2)
                    if (Math.abs(nx - points[points.size - 3]) + Math.abs(ny - points[points.size - 2]) < 2) continue
                    points.add(nx); points.add(ny); points.add(pressureOf(p, stylus))
                }
            }
            if (!isErasing()) setCurrent(strokeNow(points))
            return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (isScrolling()) { setScrolling(false); return false }
            val d = latest.value
            var next: Drawing? = null
            if (isErasing()) {
                if (pending.isNotEmpty() && e.actionMasked == MotionEvent.ACTION_UP) {
                    val gone = pending.toSet()
                    next = d.copy(strokes = d.strokes.filterIndexed { i, _ -> i !in gone })
                }
                pending.clear()
                setErasing(false)
            } else {
                val s = getCurrent()
                if (s != null && e.actionMasked == MotionEvent.ACTION_UP) next = d.copy(strokes = d.strokes + s)
                setCurrent(null)
            }
            if (next != null) {
                if (grow && lowest(next) > next.h - 260f) next = next.copy(h = next.h + 700f)
                onChange(next)
            }
            return true
        }
    }
    return true
}

private fun pressureOf(p: Float, stylus: Boolean) = if (stylus) Math.round((p.coerceIn(0.05f, 1f)) * 100) else 55

private fun strokeNow(points: List<Int>): Stroke = if (InkTools.tool == Tool.HL) {
    // A highlighter keeps an even width; pressure isn't used.
    Stroke("hl", InkTools.hl, HL_SIZE, points.toIntArray())
} else Stroke("pen", InkTools.pen, InkTools.size, points.toIntArray())

// ── Toolbar ───────────────────────────────────────────────────────────────────

/** An eraser, drawn (Material's icons have none). */
private val EraserIcon: ImageVector by lazy {
    ImageVector.Builder("Eraser", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(16.24f, 3.56f); lineTo(21.19f, 8.5f); curveTo(21.97f, 9.29f, 21.97f, 10.55f, 21.19f, 11.34f); lineTo(12f, 20.53f)
            curveTo(10.44f, 22.09f, 7.91f, 22.09f, 6.34f, 20.53f); lineTo(2.81f, 17f); curveTo(2.03f, 16.21f, 2.03f, 14.95f, 2.81f, 14.16f)
            lineTo(13.41f, 3.56f); curveTo(14.2f, 2.78f, 15.46f, 2.78f, 16.24f, 3.56f); close()
            moveTo(4.22f, 15.58f); lineTo(7.76f, 19.11f); curveTo(8.54f, 19.9f, 9.8f, 19.9f, 10.59f, 19.11f); lineTo(14.12f, 15.58f); lineTo(9.17f, 10.63f); close()
        }
    }.build()
}

@Composable
private fun ToolChip(icon: ImageVector, label: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(if (on) Ink.VioletDeep else Color.Transparent).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = when { on -> Ink.OnVioletDeep; enabled -> Ink.Text; else -> Ink.Dim2 }, modifier = Modifier.size(21.dp)) }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Row(Modifier.clip(CircleShape).background(Ink.CardHigh).padding(3.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
fun InkToolbar(
    undo: () -> Unit, redo: () -> Unit, canUndo: Boolean, canRedo: Boolean,
    bg: String? = null, setBg: ((String) -> Unit)? = null, compact: Boolean = false, extra: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { InkTools.load(context) }
    val set = { f: () -> Unit -> f(); InkTools.save(context) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Group {
            ToolChip(Icons.Rounded.Edit, "Pluma", InkTools.tool == Tool.PEN) { set { InkTools.tool = Tool.PEN } }
            ToolChip(Icons.Rounded.BorderColor, "Marcatextos", InkTools.tool == Tool.HL) { set { InkTools.tool = Tool.HL } }
            ToolChip(EraserIcon, "Borrador", InkTools.tool == Tool.ERASER) { InkTools.tool = Tool.ERASER }
        }
        if (InkTools.tool != Tool.ERASER) Group {
            val hl = InkTools.tool == Tool.HL
            for (c in if (hl) HL_COLORS else PEN_COLORS) {
                val on = c == if (hl) InkTools.hl else InkTools.pen
                Box(Modifier.size(38.dp).clip(CircleShape).clickable { set { if (hl) InkTools.hl = c else InkTools.pen = c } }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(if (on) 24.dp else 20.dp).clip(CircleShape).background(if (c == 0L) paperColors().ink else Color(c)).border(2.dp, if (on) Ink.Violet else Ink.Hairline, CircleShape))
                }
            }
        }
        if (InkTools.tool == Tool.PEN && !compact) Group {
            for (s in PEN_SIZES) {
                val on = s == InkTools.size
                Box(Modifier.size(38.dp).clip(CircleShape).background(if (on) Ink.VioletDeep else Color.Transparent).clickable { set { InkTools.size = s } }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size((4 + s * 2).dp).clip(CircleShape).background(if (on) Ink.OnVioletDeep else Ink.Dim))
                }
            }
        }
        Group {
            ToolChip(Icons.Rounded.Undo, "Deshacer", false, canUndo, undo)
            ToolChip(Icons.Rounded.Redo, "Rehacer", false, canRedo, redo)
        }
        if (bg != null && setBg != null) Group {
            for ((b, label) in listOf("lines" to "Renglones", "grid" to "Cuadrícula", "dots" to "Puntos", "plain" to "Lisa")) {
                Text(
                    label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (bg == b) Ink.OnVioletDeep else Ink.Dim,
                    modifier = Modifier.clip(CircleShape).background(if (bg == b) Ink.VioletDeep else Color.Transparent).clickable { setBg(b) }.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        extra()
        if (!compact) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Con el dedo", fontSize = 13.sp, color = Ink.Dim)
            Spacer(Modifier.width(6.dp))
            Switch(InkTools.finger, { v -> set { InkTools.finger = v } }, colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet))
        }
    }
}

/** A drawing with its own undo history. */
class InkHistory(initial: Drawing?, private val save: (Drawing) -> Unit) {
    var d by mutableStateOf(initial)
        private set
    private val past = ArrayList<Drawing>()
    private val future = ArrayList<Drawing>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private fun flags() { canUndo = past.isNotEmpty(); canRedo = future.isNotEmpty() }
    fun change(next: Drawing) {
        d?.let { past += it; if (past.size > 200) past.removeAt(0) }
        future.clear(); d = next; flags(); save(next)
    }
    fun undo() { val prev = past.removeLastOrNull() ?: return; d?.let { future += it }; d = prev; flags(); save(prev) }
    fun redo() { val next = future.removeLastOrNull() ?: return; d?.let { past += it }; d = next; flags(); save(next) }
    fun reset(x: Drawing?) { past.clear(); future.clear(); d = x; flags() }
}

// ── A page written by hand ────────────────────────────────────────────────────

@Composable
fun InkScreen(p: Page, ix: mx.loperstack.tokkipnotas.data.Index) {
    val id = p.id
    var dirty by remember(id) { mutableStateOf(false) }
    var title by remember(id) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(p.title)) }
    var titleVersion by remember(id) { mutableStateOf(0) }
    var version by remember(id) { mutableStateOf(0) }
    val history = remember(id) { InkHistory(null) { dirty = true; version++ } }
    fun flush() {
        val d = history.d ?: return
        if (!dirty) return
        dirty = false
        Store.saveBody(id, title.text.trim(), drawingJson(d))
    }
    LaunchedEffect(id) {
        val body = withContext(Dispatchers.IO) { Store.body(id) }
        history.reset(drawingOf(body, Drawing("lines")))
    }
    LaunchedEffect(version) { if (version > 0) { delay(800); flush() } }
    LaunchedEffect(titleVersion) {
        if (titleVersion > 0) {
            delay(600)
            Store.page(id)?.let { if (it.title != title.text.trim()) Store.update(it.copy(title = title.text.trim())) }
        }
    }
    LaunchedEffect(id) {
        Sync.remote.collect { ids ->
            if (id !in ids || dirty) return@collect
            val body = withContext(Dispatchers.IO) { Store.body(id) }
            if (!dirty) history.reset(drawingOf(body, Drawing("lines")))
        }
    }
    DisposableEffect(id) { onDispose { flush() } }

    Column(Modifier.fillMaxSize()) {
        PageTop(p, ix)
        val d = history.d
        Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            InkToolbar(
                history::undo, history::redo, history.canUndo, history.canRedo,
                bg = d?.bg, setBg = { b -> history.d?.let { history.change(it.copy(bg = b)) } },
                extra = { Group { ToolChip(Icons.Rounded.Add, "Más hoja", false) { history.d?.let { history.change(it.copy(h = it.h + 700f)) } } } },
            )
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            PageHeader(p, title, onTitle = { title = it; titleVersion++ })
            if (d != null) {
                Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 80.dp).clip(Squircle(20.dp)).border(1.dp, Ink.Hairline, Squircle(20.dp))) {
                    InkSurface(d, { history.change(it) }, grow = true)
                }
            }
        }
    }
}

// ── A drawing inside a page of text ───────────────────────────────────────────

@Composable
fun DrawBlockView(json: String, onChange: (String) -> Unit, onRemove: () -> Unit) {
    val history = remember { InkHistory(drawingOf(json, Drawing("plain", 450f))) { onChange(drawingJson(it)) } }
    val d = history.d ?: return
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(Squircle(18.dp)).border(1.dp, Ink.Hairline, Squircle(18.dp))) {
        Box(Modifier.fillMaxWidth().background(Ink.Card).padding(6.dp)) {
            InkToolbar(
                history::undo, history::redo, history.canUndo, history.canRedo, compact = true,
                extra = {
                    Group {
                        ToolChip(Icons.Rounded.Remove, "Más pequeño", false, d.h > 250f) { history.change(d.copy(h = (d.h - 150f).coerceAtLeast(250f))) }
                        ToolChip(Icons.Rounded.Add, "Más grande", false) { history.change(d.copy(h = d.h + 150f)) }
                        ToolChip(Icons.Rounded.DeleteOutline, "Quitar el dibujo", false, onClick = onRemove)
                    }
                },
            )
        }
        InkSurface(d, { history.change(it) })
    }
}
