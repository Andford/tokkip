// Paints Tokkip's car screen on Android Auto's surface, about 30 times a second
// while it shows: the breathing edge glow, Tokkip, and either the ambient clock
// or the panel (time, music, events and tasks). Colours follow the hour, or
// the album cover while music plays, always softened so nothing flashes.

package mx.loperstack.tokkip.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Surface
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import mx.loperstack.tokkip.data.Agenda
import mx.loperstack.tokkip.data.CalEvent
import mx.loperstack.tokkip.data.NowPlaying
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.Weather
import mx.loperstack.tokkip.data.nextEvent
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.character.Mood
import mx.loperstack.tokkip.wellness.orderedTasks

class CarPainter(private val ctx: CarContext, private val onState: () -> Unit) : SurfaceCallback {
    /**
     * Ambient mode (glow + Tokkip + time) or the panel. With the map template the
     * panel's information is the list card beside the surface, so the surface
     * stays ambient; the panel drawing remains for hosts without that template.
     */
    var ambient = true
        private set
    /** True where the surface is the whole screen (no list card next to it). */
    var panelOnSurface = false
    /** Called once when you've been driving for two hours. */
    var onBreak: (() -> Unit)? = null

    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var density = 1f
    private val visible = Rect()

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastFrame = 0L
    private var lastTouch = SystemClock.elapsedRealtime()
    private val startedAt = SystemClock.elapsedRealtime()
    private var breakDue = false

    private val engine = TokkipEngine()
    private val drawScope = CanvasDrawScope()
    private val tokkipBox = RectF()

    private val audio = ctx.getSystemService(AudioManager::class.java)
    private var music = false
    private var musicCheckedAt = 0L
    private var lastPlaying: Boolean? = null
    private var lastKey = ""

    private var events: List<CalEvent> = emptyList()
    private var eventsAt = 0L

    // The glow's colours: where they're heading, and where they are now (eased).
    private val glow = FloatArray(9)
    private var glowReady = false
    private var artSeen: Bitmap? = null
    private var artColors: IntArray? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val light = Typeface.create(Typeface.DEFAULT, 300, false)
    private val regular = Typeface.create(Typeface.DEFAULT, 500, false)
    private val bold = Typeface.create(Typeface.DEFAULT, 800, false)
    private val matrix = Matrix()
    private val es = Locale("es", "MX")

    // ── Android Auto's surface ───────────────────────────────────────────

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        surface = container.surface
        width = container.width
        height = container.height
        density = container.dpi / 160f
        if (visible.isEmpty) visible.set(0, 0, width, height)
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) { surface = null }

    override fun onVisibleAreaChanged(visibleArea: Rect) { visible.set(visibleArea) }

    override fun onStableAreaChanged(stableArea: Rect) {}

    override fun onClick(x: Float, y: Float) {
        touch()
        when {
            ambient && panelOnSurface -> { ambient = false; onState() }
            tokkipBox.contains(x, y) -> engine.tickle()
        }
    }

    override fun onScroll(distanceX: Float, distanceY: Float) = touch()

    // ── Loop ─────────────────────────────────────────────────────────────

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            render()
            handler.postDelayed(this, 33)
        }
    }

    fun start() {
        if (running) return
        running = true
        lastFrame = SystemClock.elapsedRealtime()
        touch()
        handler.post(frame)
        handler.postDelayed({ if (running) engine.greet() }, 700)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(frame)
    }

    fun toggle() { if (panelOnSurface) ambient = !ambient; touch() }

    /** Today's events, for the list card. */
    fun upcoming() = events

    fun touch() { lastTouch = SystemClock.elapsedRealtime() }

    private fun render() {
        val now = SystemClock.elapsedRealtime()
        val dt = (now - lastFrame) / 1000f
        lastFrame = now

        // Quiet for 20 s on the panel: back to the ambient glow.
        if (!ambient && now - lastTouch > 20_000) { ambient = true; onState() }
        if (!breakDue && now - startedAt > 2 * 3600_000L) { breakDue = true; onBreak?.invoke() }

        val track = NowPlaying.track.value
        val key = "${track?.playing}|${track?.title}|${Store.state.value.tasks.count { !it.done }}"
        if (key != lastKey) { lastKey = key; lastPlaying = track?.playing; onState() }
        if (now - musicCheckedAt > 2000) {
            musicCheckedAt = now
            music = runCatching { audio?.isMusicActive == true }.getOrDefault(false)
        }
        val wall = System.currentTimeMillis()
        if (wall - eventsAt > 60_000) {
            eventsAt = wall
            events = runCatching { Agenda.load(ctx, wall) }.getOrDefault(emptyList())
        }
        Weather.refresh(ctx)

        val d = Store.state.value
        engine.configure(Color(d.tokkipColor), if (breakDue) Mood.SLEEPY else Mood.IDLE, null, music, null, true)
        engine.update(dt)
        stepGlow(track?.art)

        val s = surface ?: return
        if (!s.isValid || width == 0) return
        val canvas = runCatching { s.lockHardwareCanvas() }.getOrNull() ?: return
        try {
            paint(canvas, track, wall)
        } finally {
            runCatching { s.unlockCanvasAndPost(canvas) }
        }
    }

    // ── Colours ──────────────────────────────────────────────────────────

    private fun hourPalette(hour: Int): IntArray = when (hour) {
        in 5..10 -> intArrayOf(0xFFFFB38A.toInt(), 0xFFFFD59E.toInt(), 0xFFF7A1C4.toInt())  // amanecer
        in 11..16 -> intArrayOf(0xFF7FE0D6.toInt(), 0xFF8EC5FF.toInt(), 0xFFA8E6A1.toInt()) // día
        in 17..19 -> intArrayOf(0xFFFF8A80.toInt(), 0xFFB39DDB.toInt(), 0xFFFFCC80.toInt()) // atardecer
        else -> intArrayOf(0xFF7986CB.toInt(), 0xFF9575CD.toInt(), 0xFF4DB6AC.toInt())      // noche
    }

    /** Three soft, distinct colours from an album cover. */
    private fun coverColors(art: Bitmap): IntArray? = runCatching {
        val soft = art.copy(Bitmap.Config.ARGB_8888, false)
        val small = Bitmap.createScaledBitmap(soft, 8, 8, true)
        val hsv = FloatArray(3)
        val picks = (0 until 64).map { small.getPixel(it % 8, it / 8) }
            .map { c -> AColor.colorToHSV(c, hsv); Triple(hsv[0], hsv[1], hsv[2]) }
            .sortedByDescending { it.second * it.third }
        val chosen = ArrayList<Float>()
        for (p in picks) {
            if (p.second < 0.18f || p.third < 0.25f) continue
            if (chosen.all { hueGap(it, p.first) > 35f }) chosen += p.first
            if (chosen.size == 3) break
        }
        if (chosen.isEmpty()) return@runCatching null
        while (chosen.size < 3) chosen += (chosen.last() + 40f) % 360f
        // Pastel and calm, whatever the cover.
        IntArray(3) { AColor.HSVToColor(floatArrayOf(chosen[it], 0.45f, 0.98f)) }
    }.getOrNull()

    private fun hueGap(a: Float, b: Float): Float { val g = abs(a - b) % 360f; return min(g, 360f - g) }

    private fun stepGlow(art: Bitmap?) {
        if (art !== artSeen) { artSeen = art; artColors = art?.let { coverColors(it) } }
        val target = if (music) artColors ?: hourPalette(hour()) else hourPalette(hour())
        val k = if (glowReady) 0.02f else 1f // a few seconds to drift to new colours
        for (i in 0 until 3) {
            val c = target[i]
            val rgb = floatArrayOf(AColor.red(c).toFloat(), AColor.green(c).toFloat(), AColor.blue(c).toFloat())
            for (j in 0 until 3) glow[i * 3 + j] += (rgb[j] - glow[i * 3 + j]) * k
        }
        glowReady = true
    }

    private fun glowColor(i: Int, alpha: Float = 1f) =
        AColor.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), glow[i * 3].toInt(), glow[i * 3 + 1].toInt(), glow[i * 3 + 2].toInt())

    private fun hour() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    private fun night() = hour() >= 21 || hour() < 6 || ctx.isDarkMode && hour() >= 19

    // ── Drawing ──────────────────────────────────────────────────────────

    private fun paint(c: Canvas, track: NowPlaying.Track?, wall: Long) {
        val t = (SystemClock.elapsedRealtime() - startedAt) / 1000f
        val dim = if (night()) 0.55f else 1f
        val breath = 0.5f + 0.5f * sin(2f * PI.toFloat() * t / 8f) // one slow breath every 8 s

        // Background: deep night, warmed by the glow's first colour around Tokkip.
        c.drawColor(0xFF0B0D16.toInt())
        val v = RectF(visible)
        fill.shader = RadialGradient(
            v.centerX(), v.centerY(), maxOf(v.width(), v.height()) * 0.7f,
            glowColor(0, 0.13f * dim * (0.7f + 0.3f * breath)), AColor.TRANSPARENT, Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.shader = null

        drawGlow(c, t, breath, dim * if (ambient) 1f else 0.55f)
        if (ambient) drawAmbient(c, v, track, wall, dim) else drawPanel(c, v, track, wall)
    }

    /** The edge light: layered strokes of a slowly turning sweep of three colours. */
    private fun drawGlow(c: Canvas, t: Float, breath: Float, strength: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sweep = SweepGradient(w / 2f, h / 2f, intArrayOf(glowColor(0), glowColor(1), glowColor(2), glowColor(0)), null)
        matrix.setRotate((t * 5f) % 360f, w / 2f, h / 2f) // a full turn every 72 s
        sweep.setLocalMatrix(matrix)
        stroke.shader = sweep
        val corner = 26f * density
        val layers = 12
        for (i in layers - 1 downTo 0) {
            val f = i / layers.toFloat()
            stroke.strokeWidth = (3f + i * 6f) * density
            stroke.alpha = ((1f - f) * (1f - f) * 0.42f * (0.55f + 0.45f * breath) * strength * 255).toInt()
            c.drawRoundRect(0f, 0f, w, h, corner, corner, stroke)
        }
        stroke.strokeWidth = 2.5f * density
        stroke.alpha = ((0.55f + 0.35f * breath) * strength * 255).toInt()
        c.drawRoundRect(1f, 1f, w - 1f, h - 1f, corner, corner, stroke)
        stroke.shader = null
    }

    private fun drawTokkip(c: Canvas, left: Float, top: Float, h: Float) {
        val w = h * 1.2f
        tokkipBox.set(left, top, left + w, top + h)
        c.save()
        c.translate(left, top)
        drawScope.draw(Density(density), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(c), Size(w, h)) {
            engine.draw(this)
        }
        c.restore()
    }

    // ── Weather icons, drawn soft and round like Tokkip ─────────────────

    private fun drawSky(c: Canvas, cx: Float, cy: Float, s: Float, code: Int, day: Boolean) {
        val sun = 0xFFFFD59E.toInt()
        val moon = 0xFFC5CAE9.toInt()
        val drop = 0xFF8EC5FF.toInt()
        when (code) {
            0, 1 -> if (day) sunIcon(c, cx, cy, s, sun) else moonIcon(c, cx, cy, s, moon)
            2 -> {
                if (day) sunIcon(c, cx - 0.16f * s, cy - 0.16f * s, s * 0.72f, sun) else moonIcon(c, cx - 0.16f * s, cy - 0.16f * s, s * 0.72f, moon)
                cloudIcon(c, cx + 0.06f * s, cy + 0.1f * s, s * 0.85f, white(0.9f))
            }
            45, 48 -> {
                stroke.color = white(0.75f); stroke.strokeWidth = 0.08f * s; stroke.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 3) {
                    val y = cy - 0.22f * s + i * 0.22f * s
                    val off = if (i == 1) 0.08f * s else 0f
                    c.drawLine(cx - 0.36f * s + off, y, cx + 0.36f * s - off, y, stroke)
                }
            }
            in 51..67, in 80..82 -> {
                cloudIcon(c, cx, cy - 0.12f * s, s, white(0.9f))
                stroke.color = drop; stroke.strokeWidth = 0.07f * s; stroke.strokeCap = Paint.Cap.ROUND
                for (i in -1..1) c.drawLine(cx + i * 0.2f * s + 0.04f * s, cy + 0.22f * s, cx + i * 0.2f * s - 0.04f * s, cy + 0.38f * s, stroke)
            }
            in 71..77, 85, 86 -> {
                cloudIcon(c, cx, cy - 0.12f * s, s, white(0.9f))
                fill.color = white(0.95f)
                for (i in -1..1) c.drawCircle(cx + i * 0.2f * s, cy + 0.3f * s + (if (i == 0) 0.06f * s else 0f), 0.05f * s, fill)
            }
            in 95..99 -> {
                cloudIcon(c, cx, cy - 0.12f * s, s, white(0.85f))
                fill.color = 0xFFFFD54F.toInt()
                c.drawPath(Path().apply {
                    moveTo(cx + 0.04f * s, cy + 0.1f * s); lineTo(cx - 0.1f * s, cy + 0.3f * s); lineTo(cx, cy + 0.3f * s)
                    lineTo(cx - 0.06f * s, cy + 0.46f * s); lineTo(cx + 0.12f * s, cy + 0.22f * s); lineTo(cx + 0.02f * s, cy + 0.22f * s); close()
                }, fill)
            }
            else -> cloudIcon(c, cx, cy, s, white(0.88f))
        }
        stroke.strokeCap = Paint.Cap.BUTT
    }

    private fun sunIcon(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill.color = color
        c.drawCircle(cx, cy, 0.2f * s, fill)
        stroke.color = color; stroke.strokeWidth = 0.065f * s; stroke.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 8) {
            val a = i * PI.toFloat() / 4f
            c.drawLine(cx + cos(a) * 0.31f * s, cy + sin(a) * 0.31f * s, cx + cos(a) * 0.42f * s, cy + sin(a) * 0.42f * s, stroke)
        }
    }

    private fun moonIcon(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        val body = Path().apply { addCircle(cx, cy, 0.3f * s, Path.Direction.CW) }
        val bite = Path().apply { addCircle(cx + 0.16f * s, cy - 0.12f * s, 0.25f * s, Path.Direction.CW) }
        body.op(bite, Path.Op.DIFFERENCE)
        fill.color = color
        c.drawPath(body, fill)
    }

    private fun cloudIcon(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        fill.color = color
        c.drawCircle(cx - 0.17f * s, cy + 0.04f * s, 0.16f * s, fill)
        c.drawCircle(cx + 0.03f * s, cy - 0.06f * s, 0.22f * s, fill)
        c.drawCircle(cx + 0.22f * s, cy + 0.06f * s, 0.14f * s, fill)
        c.drawRoundRect(cx - 0.33f * s, cy + 0.02f * s, cx + 0.36f * s, cy + 0.2f * s, 0.09f * s, 0.09f * s, fill)
    }

    private fun clock(wall: Long): String =
        SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm", es).format(Date(wall))

    private fun dateLine(wall: Long): String =
        SimpleDateFormat("EEEE d 'de' MMMM", es).format(Date(wall)).replaceFirstChar { it.uppercase(es) }

    private fun greeting(): String = when (hour()) {
        in 5..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }

    private fun write(
        c: Canvas, s: String, x: Float, y: Float, size: Float, face: Typeface, color: Int,
        maxWidth: Float = Float.MAX_VALUE, align: Paint.Align = Paint.Align.LEFT,
    ) {
        text.textSize = size
        text.typeface = face
        text.color = color
        text.textAlign = align
        val shown = if (maxWidth < Float.MAX_VALUE) TextUtils.ellipsize(s, text, maxWidth, TextUtils.TruncateAt.END).toString() else s
        c.drawText(shown, x, y, text)
    }

    private fun white(a: Float) = AColor.argb((a * 255).toInt(), 255, 255, 255)

    private fun drawAmbient(c: Canvas, v: RectF, track: NowPlaying.Track?, wall: Long, dim: Float) {
        val k = v.height() / 100f
        val th = v.height() * 0.5f
        drawTokkip(c, v.centerX() - th * 0.6f, v.top + 5f * k, th)

        val timeY = v.top + 5f * k + th + 17f * k
        write(c, clock(wall), v.centerX(), timeY, 19f * k, light, white(0.92f * (0.4f + 0.6f * dim)), align = Paint.Align.CENTER)
        val sky = Weather.now
        val sub = when {
            breakDue -> "¿Qué tal una pausa? Llevas un buen rato manejando"
            sky != null -> dateLine(wall) + "  ·  ${sky.temp}°"
            else -> dateLine(wall)
        }
        write(c, sub, v.centerX(), timeY + 8f * k, 4.6f * k, regular,
            if (breakDue) glowColor(0, 0.9f) else white(0.55f), v.width() * 0.8f, Paint.Align.CENTER)
        if (track != null && track.playing && track.title.isNotBlank()) {
            val line = "♪  " + track.title + if (track.artist.isNotBlank()) " · ${track.artist}" else ""
            write(c, line, v.centerX(), v.bottom - 6f * k, 4f * k, regular, white(0.45f), v.width() * 0.7f, Paint.Align.CENTER)
        }
    }

    private fun card(c: Canvas, r: RectF, k: Float) {
        fill.color = white(0.065f)
        c.drawRoundRect(r, 5f * k, 5f * k, fill)
    }

    private fun drawPanel(c: Canvas, v: RectF, track: NowPlaying.Track?, wall: Long) {
        val k = v.height() / 100f
        val pad = 6f * k
        val left = v.left + pad
        val colW = v.width() * 0.38f

        // Left: Tokkip, the time, the day.
        drawTokkip(c, left - 2f * k, v.top + pad, 30f * k)
        write(c, greeting(), left, v.top + pad + 40f * k, 4.6f * k, regular, glowColor(0, 0.95f), colW)
        write(c, clock(wall), left - 0.6f * k, v.top + pad + 62f * k, 21f * k, light, white(0.95f), colW)
        write(c, dateLine(wall), left, v.top + pad + 71f * k, 4.2f * k, regular, white(0.6f), colW)
        val sky = Weather.now
        if (sky != null) {
            val iy = v.top + pad + 80f * k
            drawSky(c, left + 4f * k, iy, 8.5f * k, sky.code, sky.day)
            write(c, "${sky.temp}°", left + 10f * k, iy + 2.6f * k, 7.2f * k, light, white(0.95f))
            write(c, Weather.label(sky.code), left + 22f * k, iy - 0.6f * k, 3.8f * k, bold, white(0.8f), colW - 22f * k)
            write(c, "${sky.max}° / ${sky.min}°", left + 22f * k, iy + 4.4f * k, 3.4f * k, regular, white(0.5f), colW - 22f * k)
        } else if (!Weather.granted(ctx)) {
            write(c, "Clima: permite la ubicación", left, v.top + pad + 81f * k, 3.4f * k, regular, white(0.4f), colW)
        }
        if (breakDue) write(c, "Llevas 2 h manejando · ¿pausa?", left, v.top + pad + 90f * k, 3.8f * k, bold, glowColor(2, 0.95f), colW)

        // Right: music, then what's next.
        val rx = v.left + colW + pad * 1.5f
        val rw = v.right - pad - rx
        val music = RectF(rx, v.top + pad, rx + rw, v.top + pad + 36f * k)
        card(c, music, k)
        val ip = 4f * k
        val art = track?.art
        val artSize = music.height() - ip * 2
        if (art != null && !art.isRecycled) {
            c.save()
            val ar = RectF(music.left + ip, music.top + ip, music.left + ip + artSize, music.top + ip + artSize)
            val clip = android.graphics.Path().apply { addRoundRect(ar, 3f * k, 3f * k, android.graphics.Path.Direction.CW) }
            c.clipPath(clip)
            runCatching { c.drawBitmap(art, null, ar, fill) }
            c.restore()
        } else {
            fill.color = glowColor(1, 0.25f)
            c.drawRoundRect(music.left + ip, music.top + ip, music.left + ip + artSize, music.top + ip + artSize, 3f * k, 3f * k, fill)
            write(c, "♪", music.left + ip + artSize / 2f, music.top + ip + artSize * 0.66f, artSize * 0.45f, regular, white(0.7f), align = Paint.Align.CENTER)
        }
        val tx = music.left + ip * 2 + artSize
        val tw = music.right - ip - tx
        write(c, "SONANDO", tx, music.top + 9f * k, 3.2f * k, bold, glowColor(1, 0.9f))
        when {
            track != null && track.title.isNotBlank() -> {
                write(c, track.title, tx, music.top + 18.5f * k, 5.4f * k, bold, white(0.95f), tw)
                write(c, track.artist, tx, music.top + 25.5f * k, 4.2f * k, regular, white(0.65f), tw)
                write(c, (if (track.playing) "" else "En pausa · ") + track.app, tx, music.top + 31.5f * k, 3.4f * k, regular, white(0.4f), tw)
            }
            !NowPlaying.granted(ctx) -> {
                write(c, "Sin acceso a la música", tx, music.top + 18.5f * k, 4.8f * k, bold, white(0.85f), tw)
                write(c, "Actívalo en Tokkip → Enfoque", tx, music.top + 25.5f * k, 3.8f * k, regular, white(0.55f), tw)
            }
            else -> write(c, "Nada sonando", tx, music.top + 19f * k, 5f * k, bold, white(0.7f), tw)
        }

        val todo = RectF(rx, music.bottom + 4f * k, rx + rw, v.bottom - pad)
        card(c, todo, k)
        write(c, "PENDIENTES", todo.left + ip, todo.top + 9f * k, 3.2f * k, bold, glowColor(2, 0.9f))
        var y = todo.top + 18f * k
        val line = 8.6f * k
        val lineW = todo.width() - ip * 2 - 6f * k
        val now = System.currentTimeMillis()
        nextEvent(events, now)?.let { e ->
            fill.color = e.color ?: glowColor(0)
            c.drawRoundRect(todo.left + ip, y - 4f * k, todo.left + ip + 1.6f * k, y + 1.2f * k, k, k, fill)
            val at = SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a", es).format(Date(e.begin))
            write(c, "$at  ${e.title}", todo.left + ip + 4f * k, y, 4.6f * k, bold, white(0.92f), lineW)
            y += line
        }
        val tasks = orderedTasks(Store.state.value.tasks).filter { !it.done }
        val room = ((todo.bottom - ip - y) / line).toInt().coerceAtLeast(0) + 1
        for (task in tasks.take(room.coerceAtMost(4))) {
            if (y > todo.bottom - 2f * k) break
            stroke.color = white(0.6f)
            stroke.strokeWidth = 0.5f * k
            c.drawCircle(todo.left + ip + 1.4f * k, y - 1.5f * k, 1.4f * k, stroke)
            write(c, task.text, todo.left + ip + 5f * k, y, 4.4f * k, regular, white(0.85f), lineW)
            y += line
        }
        if (tasks.isEmpty() && nextEvent(events, now) == null) {
            write(c, "Todo al día", todo.left + ip, y, 4.8f * k, bold, white(0.75f))
            write(c, "Disfruta el camino", todo.left + ip, y + 6.5f * k, 3.8f * k, regular, white(0.5f))
        } else if (tasks.size > room.coerceAtMost(4)) {
            write(c, "y ${tasks.size - room.coerceAtMost(4)} más", todo.left + ip + 5f * k, minOf(y, todo.bottom - ip), 3.4f * k, regular, white(0.45f))
        }
    }
}
