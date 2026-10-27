// Diario → Kit para journaling: your diary as printable cut-outs for a paper
// journal. A letter-size PDF (print at 100 %, cut along the dashed lines):
//
//  1. Stickers: the month in colours, Tokkip's growth seal, what he noticed as
//     a quote, Tokkip in each mood, your streaks as stamps, washi tape strips
//     and little tags (hoy, gracias, logré…).
//  2. Your days: one card per day with washi tape on top, the date, Tokkip's
//     face for your mood, your lines in handwriting and photos as polaroids.
//  3. To write by hand: your week with lined prompts, prompt cards from what
//     Tokkip noticed, and a habit tracker for the month to colour in.
//
// Everything is drawn here, on the phone. The handwriting is Caveat (SIL Open
// Font License, android/licenses/OFL-Caveat.txt).

package mx.loperstack.tokkip.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.core.content.res.ResourcesCompat
import mx.loperstack.tokkip.R
import mx.loperstack.tokkip.character.TokkipEngine
import mx.loperstack.tokkip.character.tokkipBitmap
import mx.loperstack.tokkip.wellness.MOODS
import mx.loperstack.tokkip.wellness.moodOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Which days go in the kit. */
enum class KitRange(val label: String) { WEEK("Esta semana"), MONTH("Este mes"), LAST_MONTH("Mes pasado"), ALL("Todo el diario") }

/** The first and last day key of a range, ending today. */
fun kitDays(range: KitRange, now: Long): Pair<String, String> {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    return when (range) {
        KitRange.WEEK -> dayKey(Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -6) }.timeInMillis) to dayKey(now)
        KitRange.MONTH -> dayKey(c.apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis) to dayKey(now)
        KitRange.LAST_MONTH -> {
            c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, -1)
            val a = dayKey(c.timeInMillis)
            c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
            a to dayKey(c.timeInMillis)
        }
        KitRange.ALL -> "0000-00-00" to dayKey(now)
    }
}

object JournalKit {
    private val MX = Locale("es", "MX")
    private const val W = 612f
    private const val H = 792f
    private const val M = 30f

    // Print-friendly colours.
    private const val INK = 0xFF3A3330.toInt()
    private const val SOFT = 0xFF8A817B.toInt()
    private const val CUT = 0xFFB9B0A8.toInt()
    private val TAPES = listOf(0xFFF6BFC4, 0xFFB5E2D3, 0xFFFBE0A0, 0xFFCAC3F3, 0xFFA9D7F1, 0xFFF8CBA6).map { it.toInt() }

    private fun tint(argb: Long, amount: Float): Int {
        val c = argb.toInt()
        fun ch(shift: Int) = (((c shr shift) and 0xFF) * amount + 255 * (1 - amount)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private class Ctx(context: Context) {
        val hand: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.caveat) }.getOrNull() ?: Typeface.SERIF
        val handBold: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.caveat_bold) }.getOrNull() ?: Typeface.create(Typeface.SERIF, Typeface.BOLD)
        fun text(size: Float, face: Typeface, color: Int = INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = face; this.color = color }
        val sans = Typeface.create("sans-serif", Typeface.NORMAL)
        val sansBold = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    /** Pages, with a cursor that starts a new page when something doesn't fit. */
    private class Pages(val doc: PdfDocument, val k: Ctx, val footer: String) {
        var n = 0
        var page: PdfDocument.Page? = null
        lateinit var c: Canvas
        fun next(): Canvas {
            page?.let { doc.finishPage(it) }
            n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), n).create())
            c = page!!.canvas
            val p = k.text(8f, k.sans, SOFT)
            c.drawText(footer, M, H - 16f, p)
            p.textAlign = Paint.Align.RIGHT
            c.drawText("$n", W - M, H - 16f, p)
            return c
        }
        fun finish() { page?.let { doc.finishPage(it) }; page = null }
    }

    // ── Drawing bits ─────────────────────────────────────────────────────────

    private val cutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.7f; color = CUT; pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
    }

    /** The dashed line to cut along, with a little pair of scissors on the top edge. */
    private fun cut(c: Canvas, r: RectF, radius: Float = 10f) {
        c.drawRoundRect(r, radius, radius, cutPaint)
        scissors(c, r.left + 18f, r.top)
    }

    private fun scissors(c: Canvas, x: Float, y: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SOFT; style = Paint.Style.STROKE; strokeWidth = 0.9f }
        val bg = Paint().apply { color = 0xFFFFFFFF.toInt() }
        c.drawRect(x - 7f, y - 4f, x + 9f, y + 4f, bg)
        c.drawCircle(x - 3.5f, y - 2.2f, 1.9f, p)
        c.drawCircle(x - 3.5f, y + 2.2f, 1.9f, p)
        c.drawLine(x - 1.8f, y - 1.3f, x + 7f, y + 2.2f, p)
        c.drawLine(x - 1.8f, y + 1.3f, x + 7f, y - 2.2f, p)
    }

    /** A strip of washi tape, with torn ends and a little pattern. */
    private fun washi(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, angle: Float, color: Int, pattern: Int) {
        c.save()
        c.rotate(angle, cx, cy)
        val l = cx - w / 2; val r = cx + w / 2; val t = cy - h / 2; val b = cy + h / 2
        val path = Path().apply {
            moveTo(l, t); lineTo(r, t)
            var y = t; var i = 0
            while (y < b) { y = min(b, y + h / 6); lineTo(if (i++ % 2 == 0) r - 2.5f else r, y) }
            lineTo(l, b)
            y = b; i = 0
            while (y > t) { y = max(t, y - h / 6); lineTo(if (i++ % 2 == 0) l + 2.5f else l, y) }
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 215 }
        c.drawPath(path, fill)
        c.save(); c.clipPath(path)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt(); alpha = 140; strokeWidth = 2f }
        when (pattern % 3) {
            0 -> { var x = l + 5f; var row = 0; while (x < r) { var y = t + 5f + (row % 2) * 4f; while (y < b) { c.drawCircle(x, y, 1.3f, dot); y += 8f }; x += 7f; row++ } }
            1 -> { var x = l - h; while (x < r) { c.drawLine(x, b, x + h, t, dot); x += 7f } }
            else -> { var x = l + 4f; while (x < r) { c.drawLine(x, t, x, b, dot.apply { strokeWidth = 1f }); x += 9f }; c.drawLine(l, cy, r, cy, dot) }
        }
        c.restore()
        c.restore()
    }

    private fun layout(t: CharSequence, p: TextPaint, w: Int, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, spacing: Float = 1f): StaticLayout =
        StaticLayout.Builder.obtain(t, 0, t.length, p, max(1, w)).setAlignment(align).setLineSpacing(0f, spacing).build()

    private fun draw(c: Canvas, l: StaticLayout, x: Float, y: Float) { c.save(); c.translate(x, y); l.draw(c); c.restore() }

    /** Tokkip's face for a mood, on a coloured round sticker. */
    private fun moodSticker(c: Canvas, k: Ctx, cx: Float, cy: Float, d: Float, value: Int, tokkipColor: Long, label: Boolean = true) {
        val mood = moodOf(value) ?: return
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint(mood.color, 0.35f) }
        c.drawCircle(cx, cy, d / 2, disc)
        c.drawCircle(cx, cy, d / 2 + 4f, cutPaint)
        val bmp = tokkipBitmap(240, 240, Color(tokkipColor), face = mood.face, headroom = 0.16f)
        val s = d * 0.78f
        c.drawBitmap(bmp, null, RectF(cx - s / 2, cy - s / 2 - 2f, cx + s / 2, cy + s / 2 - 2f), Paint(Paint.FILTER_BITMAP_FLAG))
        if (label) {
            val p = k.text(15f, k.handBold, INK).apply { textAlign = Paint.Align.CENTER }
            c.drawText(mood.label, cx, cy + d / 2 + 20f, p)
        }
    }

    /** A photo as a polaroid, slightly turned. */
    private fun polaroid(c: Canvas, k: Ctx, bmp: Bitmap, x: Float, y: Float, w: Float, angle: Float, caption: String): Float {
        val inner = w - 14f
        val ih = min(inner * 1.1f, inner * bmp.height / bmp.width)
        val h = ih + 7f + 24f
        c.save()
        c.rotate(angle, x + w / 2, y + h / 2)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 }
        c.drawRect(x + 1.5f, y + 2f, x + w + 1.5f, y + h + 2f, shadow)
        c.drawRect(x, y, x + w, y + h, Paint().apply { color = 0xFFFFFFFF.toInt() })
        c.drawRect(x, y, x + w, y + h, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFE4DED8.toInt(); strokeWidth = 0.6f })
        // Crop to fill the frame.
        val frame = inner / ih
        val src = if (bmp.width.toFloat() / bmp.height > frame) {
            val sw = (bmp.height * frame).toInt(); android.graphics.Rect((bmp.width - sw) / 2, 0, (bmp.width + sw) / 2, bmp.height)
        } else {
            val sh = (bmp.width / frame).toInt(); android.graphics.Rect(0, (bmp.height - sh) / 2, bmp.width, (bmp.height + sh) / 2)
        }
        c.drawBitmap(bmp, src, RectF(x + 7f, y + 7f, x + 7f + inner, y + 7f + ih), Paint(Paint.FILTER_BITMAP_FLAG))
        val p = k.text(13f, k.hand, SOFT).apply { textAlign = Paint.Align.CENTER }
        c.drawText(caption, x + w / 2, y + 7f + ih + 17f, p)
        c.restore()
        return h + 6f
    }

    private fun flame(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        val p = Path().apply {
            moveTo(x, y + s * 0.5f)
            cubicTo(x - s * 0.45f, y + s * 0.5f, x - s * 0.5f, y, x - s * 0.15f, y - s * 0.45f)
            cubicTo(x - s * 0.1f, y - s * 0.15f, x + s * 0.05f, y - s * 0.1f, x + s * 0.05f, y - s * 0.3f)
            cubicTo(x + s * 0.35f, y - s * 0.1f, x + s * 0.5f, y + s * 0.15f, x + s * 0.4f, y + s * 0.3f)
            cubicTo(x + s * 0.32f, y + s * 0.45f, x + s * 0.2f, y + s * 0.5f, x, y + s * 0.5f)
            close()
        }
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private fun dateLong(day: String) = SimpleDateFormat("EEEE d", MX).format(Date(dayStart(day))).replaceFirstChar { it.uppercase(MX) }
    private fun monthOf(day: String) = SimpleDateFormat("MMMM", MX).format(Date(dayStart(day)))
    private fun monthYear(ms: Long) = SimpleDateFormat("MMMM yyyy", MX).format(Date(ms)).replaceFirstChar { it.uppercase(MX) }

    // ── The kit ──────────────────────────────────────────────────────────────

    /**
     * The kit for `range`, or — with `day` (yyyy-MM-dd) — just that day: its
     * card, its stickers and a card to write by hand, on one page if it fits.
     */
    fun pdf(context: Context, data: AppData, range: KitRange, out: Uri, now: Long = System.currentTimeMillis(), day: String? = null): Boolean = runCatching {
        if (day != null) return@runCatching oneDay(context, data, day, out, now)
        val k = Ctx(context)
        val (from, to) = kitDays(range, now)
        val days = data.logs.filter { it.day in from..to && (it.notes.isNotEmpty() || it.averageMood > 0) }
        val routine = data.routine()
        val grow = growth(routine, now)
        val note = noteOfTheDay(routine, now)
        val notes = tokkipNotes(routine, now)
        // The month shown: the one the range ends in.
        val monthEnd = if (days.isNotEmpty()) days.last().day else to
        val doc = PdfDocument()
        val title = monthYear(dayStart(monthEnd))
        val pages = Pages(doc, k, "Tokkip · kit para journaling · imprime en hoja carta al 100 % y recorta por la línea punteada")

        stickers(context, pages, k, data, days, monthEnd, grow, note, title)
        dayCards(context, pages, k, data, days)
        handwriting(pages, k, data, notes, monthEnd)

        pages.finish()
        context.contentResolver.openOutputStream(out)?.use { doc.writeTo(it) } ?: return false
        doc.close()
        true
    }.getOrDefault(false)

    private fun oneDay(context: Context, data: AppData, day: String, out: Uri, now: Long): Boolean {
        val k = Ctx(context)
        val doc = PdfDocument()
        val pages = Pages(doc, k, "Tokkip · tu día para el journal · imprime en hoja carta al 100 % y recorta por la línea punteada")
        val log = data.logs.firstOrNull { it.day == day }
        val title = SimpleDateFormat("EEEE d 'de' MMMM", MX).format(Date(dayStart(day))).replaceFirstChar { it.uppercase(MX) }
        var bottom = if (log != null) dayCards(context, pages, k, data, listOf(log), title) else 0f
        if (bottom == 0f) {
            val c = pages.next()
            c.drawText(title, M, M + 22f, k.text(28f, k.handBold))
            bottom = M + 40f
        }
        val routine = data.routine()
        val flow = Flow(pages, bottom + 4f)
        flow.heading(k, "Stickers del día")
        // Tokkip with that day's mood (or happy), his seal, and what he noticed.
        val mood = log?.averageMood?.takeIf { it > 0 } ?: 4
        val m = flow.place(110f, 120f)
        moodSticker(pages.c, k, m.centerX(), m.top + 48f, 86f, mood, data.tokkipColor)
        val seal = flow.place(128f, 128f)
        growthSeal(pages.c, k, seal, growth(routine, now), data.tokkipColor)
        val q = flow.place(W - 2 * M - 110f - 128f - 32f, 120f)
        quote(pages.c, k, q, noteOfTheDay(routine, now))
        flow.newRow(14f)
        for (i in 0 until 2) {
            val r = flow.place(266f, 30f, 20f)
            washi(pages.c, r.centerX(), r.centerY(), r.width(), 24f, 0f, TAPES[(i + 2) % TAPES.size], i)
            pages.c.drawRect(r.left - 3f, r.top - 1f, r.right + 3f, r.bottom + 1f, cutPaint)
        }
        flow.newRow(12f)
        for ((i, t) in listOf("hoy", "gracias", "logré", "me sentí").withIndex()) {
            val r = flow.place(124f, 34f, 12f)
            tag(pages.c, k, r, t, TAPES[i % TAPES.size])
        }
        flow.newRow(16f)
        // A card to finish the day by hand.
        val card = flow.place(W - 2 * M, 150f)
        pages.c.drawRoundRect(card, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (TAPES[1] and 0x00FFFFFF) or 0x30000000 })
        cut(pages.c, card)
        pages.c.drawText("Hoy agradezco…", card.left + 14f, card.top + 30f, k.text(20f, k.handBold))
        lines(pages.c, card.left + 14f, card.right - 14f, card.top + 58f, 4)
        pages.finish()
        context.contentResolver.openOutputStream(out)?.use { doc.writeTo(it) } ?: return false
        doc.close()
        return true
    }

    /** Places boxes left to right, row by row, page by page. */
    private class Flow(val pages: Pages, var y: Float) {
        var x = M
        var row = 0f
        fun place(w: Float, h: Float, gap: Float = 16f): RectF {
            if (x + w > W - M + 0.5f) { x = M; y += row + gap; row = 0f }
            if (y + h > H - 40f) { pages.next(); x = M; y = M + 6f; row = 0f }
            val r = RectF(x, y, x + w, y + h)
            x += w + gap
            row = max(row, h)
            return r
        }
        fun newRow(gap: Float = 16f) { if (row > 0) { y += row + gap; x = M; row = 0f } }
        fun heading(k: Ctx, text: String) {
            newRow(10f)
            if (y + 60f > H - 40f) { pages.next(); y = M + 6f }
            val p = k.text(22f, k.handBold, INK)
            pages.c.drawText(text, M, y + 18f, p)
            y += 30f
        }
    }

    private fun stickers(
        context: Context, pages: Pages, k: Ctx, data: AppData, days: List<DayLog>, monthEnd: String,
        grow: Growth, note: TokkipNote, title: String,
    ) {
        val c = pages.next()
        c.drawText("$title con Tokkip", M, M + 26f, k.text(34f, k.handBold))
        val who = if (data.name.isBlank()) "Recortables para tu journal" else "Recortables para el journal de ${data.name}"
        c.drawText(who, M, M + 46f, k.text(11f, k.sans, SOFT))
        washi(c, W - M - 70f, M + 18f, 120f, 22f, 6f, TAPES[0], 0)
        val flow = Flow(pages, M + 66f)

        // The month in colours.
        val box = flow.place(300f, 250f)
        cut(pages.c, box)
        monthCard(pages.c, k, box, data.logs, monthEnd)

        // Tokkip's seal and what he noticed.
        val seal = flow.place(236f, 128f)
        growthSeal(pages.c, k, RectF(seal.left, seal.top, seal.left + 128f, seal.bottom), grow, data.tokkipColor)
        val q = RectF(seal.left + 136f, seal.top + 4f, seal.right, seal.bottom - 4f)
        pages.c.drawText("Tokkip", q.left, q.top + 14f, k.text(13f, k.handBold, SOFT))
        val stage = layout("${grow.title}\n${grow.points} de cariño", k.text(15f, k.hand), q.width().toInt())
        draw(pages.c, stage, q.left, q.top + 20f)
        flow.x = seal.left; flow.y = seal.top + 136f
        val quoteBox = RectF(seal.left, flow.y, seal.left + 236f, flow.y + 106f)
        quote(pages.c, k, quoteBox, note)
        flow.y = box.top; flow.x = M; flow.row = 250f

        // Tokkip in each mood.
        flow.heading(k, "Así me sentí")
        for (m in MOODS) {
            val r = flow.place(96f, 112f, 12f)
            moodSticker(pages.c, k, r.centerX(), r.top + 44f, 80f, m.value, data.tokkipColor)
        }

        // Streaks as stamps.
        val now = System.currentTimeMillis()
        val habits = data.habits.map { it to max(it.streak(now), it.best(now)) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(6)
        if (habits.isNotEmpty()) {
            flow.heading(k, "Mis rachas")
            for ((h, n) in habits) {
                val r = flow.place(176f, 58f, 12f)
                streakStamp(pages.c, k, r, h, n)
            }
        }

        // Washi tape and tags.
        flow.heading(k, "Cintas y etiquetas")
        for (i in 0 until 4) {
            val r = flow.place(266f, 30f, 20f)
            washi(pages.c, r.centerX(), r.centerY(), r.width(), 24f, 0f, TAPES[(i + 1) % TAPES.size], i)
            pages.c.drawRect(r.left - 3f, r.top - 1f, r.right + 3f, r.bottom + 1f, cutPaint)
        }
        flow.newRow(14f)
        for ((i, t) in listOf("hoy", "gracias", "logré", "me sentí", "para recordar", "esta semana", "lo mejor", "nota").withIndex()) {
            val r = flow.place(124f, 34f, 12f)
            tag(pages.c, k, r, t, TAPES[i % TAPES.size])
        }
    }

    private fun monthCard(c: Canvas, k: Ctx, r: RectF, logs: List<DayLog>, monthEnd: String) {
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart(monthEnd); set(Calendar.DAY_OF_MONTH, 1) }
        val name = SimpleDateFormat("MMMM", MX).format(cal.time).replaceFirstChar { it.uppercase(MX) }
        c.drawText(name, r.left + 16f, r.top + 30f, k.text(26f, k.handBold))
        c.drawText("mi mes en colores", r.left + 16f, r.top + 46f, k.text(13f, k.hand, SOFT))
        val cellW = (r.width() - 32f) / 7
        val cellH = 22f
        val top = r.top + 58f
        val head = k.text(9f, k.sansBold, SOFT).apply { textAlign = Paint.Align.CENTER }
        for ((i, l) in listOf("L", "M", "X", "J", "V", "S", "D").withIndex()) c.drawText(l, r.left + 16f + cellW * i + cellW / 2, top + 8f, head)
        val offset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val count = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val num = k.text(8f, k.sans, SOFT)
        val byDay = logs.associateBy { it.day }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFE1DAD3.toInt(); strokeWidth = 0.7f }
        for (d in 1..count) {
            cal.set(Calendar.DAY_OF_MONTH, d)
            val i = offset + d - 1
            val x = r.left + 16f + cellW * (i % 7)
            val y = top + 14f + (i / 7) * (cellH + 3f)
            val cell = RectF(x + 1.5f, y, x + cellW - 1.5f, y + cellH)
            val mood = byDay[dayKey(cal.timeInMillis)]?.averageMood?.let { moodOf(it) }
            if (mood != null) c.drawRoundRect(cell, 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint(mood.color, 0.75f) })
            else c.drawRoundRect(cell, 5f, 5f, outline)
            c.drawText("$d", cell.left + 3f, cell.top + 9f, num)
        }
        // The colours' key.
        var x = r.left + 16f
        val y = r.bottom - 14f
        val key = k.text(12f, k.hand, INK)
        for (m in MOODS.reversed()) {
            c.drawCircle(x + 4f, y - 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint(m.color, 0.75f) })
            c.drawText(m.label, x + 11f, y, key)
            x += 13f + key.measureText(m.label) + 8f
        }
    }

    private fun growthSeal(c: Canvas, k: Ctx, r: RectF, g: Growth, tokkipColor: Long) {
        val cx = r.centerX(); val cy = r.centerY(); val rad = min(r.width(), r.height()) / 2 - 6f
        c.drawCircle(cx, cy, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF6E5.toInt() })
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFC99A2E.toInt(); strokeWidth = 1.6f }
        c.drawCircle(cx, cy, rad, ring)
        c.drawCircle(cx, cy, rad - 15f, ring.apply { strokeWidth = 0.8f })
        val arc = Path().apply { addArc(RectF(cx - rad + 9f, cy - rad + 9f, cx + rad - 9f, cy + rad - 9f), 180f, 180f) }
        val p = k.text(9f, k.sansBold, 0xFFA0761C.toInt()).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.12f }
        c.drawTextOnPath("TOKKIP · ${g.stage.name.uppercase(MX)}", arc, 0f, 3f, p)
        val bmp = tokkipBitmap(220, 220, Color(tokkipColor), face = TokkipEngine.Eye.STAR, headroom = 0.16f)
        val s = rad * 1.05f
        c.drawBitmap(bmp, null, RectF(cx - s / 2, cy - s / 2 + 2f, cx + s / 2, cy + s / 2 + 2f), Paint(Paint.FILTER_BITMAP_FLAG))
        val lv = k.text(14f, k.handBold, 0xFFA0761C.toInt()).apply { textAlign = Paint.Align.CENTER }
        c.drawText("nivel ${g.stage.level}" + if (g.loops > 0) " ★${g.loops}" else "", cx, cy + rad - 5f, lv)
        c.drawCircle(cx, cy, rad + 4f, cutPaint)
    }

    private fun quote(c: Canvas, k: Ctx, r: RectF, n: TokkipNote) {
        c.drawRoundRect(r, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4F1FB.toInt() })
        cut(c, r, 8f)
        washi(c, r.centerX(), r.top, 70f, 16f, -3f, TAPES[3], 1)
        val body = layout("“${n.text}”", k.text(16f, k.hand), (r.width() - 24f).toInt(), spacing = 0.95f)
        val h = min(body.height.toFloat(), r.height() - 34f)
        c.save(); c.clipRect(r.left, r.top, r.right, r.top + 14f + h)
        draw(c, body, r.left + 12f, r.top + 14f)
        c.restore()
        val sign = k.text(13f, k.handBold, SOFT).apply { textAlign = Paint.Align.RIGHT }
        c.drawText("— Tokkip", r.right - 12f, r.bottom - 9f, sign)
    }

    private fun streakStamp(c: Canvas, k: Ctx, r: RectF, h: Habit, n: Int) {
        val color = h.color.toInt()
        val path = Path().apply {
            addRoundRect(r, 8f, 8f, Path.Direction.CW)
            // The ticket's notches.
            val notch = Path().apply { addCircle(r.left, r.centerY(), 7f, Path.Direction.CW); addCircle(r.right, r.centerY(), 7f, Path.Direction.CW) }
            op(notch, Path.Op.DIFFERENCE)
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = tint(h.color, 0.18f) })
        c.drawPath(path, cutPaint)
        flame(c, r.left + 24f, r.centerY(), 22f, color)
        c.drawText(if (n == 1) "1 día" else "$n días", r.left + 42f, r.centerY() + 1f, k.text(20f, k.handBold))
        val name = TextUtilsEllipsize(h.name, k.text(13f, k.hand, SOFT), r.width() - 52f)
        c.drawText(name, r.left + 42f, r.centerY() + 17f, k.text(13f, k.hand, SOFT))
    }

    private fun TextUtilsEllipsize(t: String, p: TextPaint, w: Float): String =
        android.text.TextUtils.ellipsize(t, p, w, android.text.TextUtils.TruncateAt.END).toString()

    private fun tag(c: Canvas, k: Ctx, r: RectF, text: String, color: Int) {
        val path = Path().apply {
            moveTo(r.left + 12f, r.top); lineTo(r.right, r.top); lineTo(r.right, r.bottom); lineTo(r.left + 12f, r.bottom)
            lineTo(r.left, r.centerY()); close()
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 150 })
        c.drawPath(path, cutPaint)
        c.drawCircle(r.left + 13f, r.centerY(), 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() })
        c.drawCircle(r.left + 13f, r.centerY(), 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = SOFT; strokeWidth = 0.6f })
        val p = k.text(17f, k.handBold).apply { textAlign = Paint.Align.CENTER }
        c.drawText(text, r.centerX() + 6f, r.centerY() + 6f, p)
    }

    // ── Your days ────────────────────────────────────────────────────────────

    /** A piece of a day card: a line of text or a photo, already measured. */
    private sealed class Piece(val h: Float) {
        class Line(val time: String, val layout: StaticLayout) : Piece(layout.height + 8f)
        class Photo(val bmp: Bitmap, val time: String, h: Float) : Piece(h)
    }

    /** The days as cards in two columns; returns how far down the last page they reach. */
    private fun dayCards(context: Context, pages: Pages, k: Ctx, data: AppData, days: List<DayLog>, title: String = "Mis días"): Float {
        if (days.isEmpty()) return 0f
        val colW = (W - 2 * M - 20f) / 2
        val inner = colW - 28f
        val headH = 64f
        val maxH = H - 40f - M - 8f
        val textPaint = k.text(15f, k.hand)
        val cols = floatArrayOf(0f, 0f)
        var started = false

        fun begin() {
            val c = pages.next()
            c.drawText(title, M, M + 22f, k.text(28f, k.handBold))
            cols[0] = M + 40f; cols[1] = M + 40f
            started = true
        }

        for ((i, d) in days.withIndex()) {
            val pieces = ArrayList<Piece>()
            for (n in d.notes) {
                val time = SimpleDateFormat("HH:mm", MX).format(Date(n.at))
                if (n.text.isNotBlank()) pieces += Piece.Line(time, layout(n.text, textPaint, (inner - 36f).toInt(), spacing = 0.95f))
                n.image?.let { DiaryImages.load(context, it, 700) }?.let { bmp ->
                    val w = min(190f, inner - 20f)
                    val ih = min((w - 14f) * 1.1f, (w - 14f) * bmp.height / bmp.width)
                    pieces += Piece.Photo(bmp, time, ih + 7f + 24f + 14f)
                }
            }
            // Cut the day into cards that fit a page.
            val chunks = ArrayList<List<Piece>>()
            var cur = ArrayList<Piece>(); var curH = 0f
            for (p in pieces) {
                if (cur.isNotEmpty() && headH + curH + p.h + 16f > maxH) { chunks += cur; cur = ArrayList(); curH = 0f }
                cur += p; curH += p.h
            }
            chunks += cur
            for ((part, chunk) in chunks.withIndex()) {
                val cardH = headH + chunk.sumOf { it.h.toDouble() }.toFloat() + 16f
                if (!started) begin()
                var col = if (cols[0] <= cols[1]) 0 else 1
                if (cols[col] + cardH > H - 40f) {
                    col = 1 - col
                    if (cols[col] + cardH > H - 40f) { begin(); col = 0 }
                }
                val x = M + col * (colW + 20f)
                val r = RectF(x, cols[col], x + colW, cols[col] + cardH)
                dayCard(pages.c, k, r, d, chunk, part > 0, data.tokkipColor, i + part)
                cols[col] += cardH + 22f
            }
        }
        return maxOf(cols[0], cols[1])
    }

    private fun dayCard(c: Canvas, k: Ctx, r: RectF, d: DayLog, pieces: List<Piece>, more: Boolean, tokkipColor: Long, seed: Int) {
        val mood = moodOf(d.averageMood)
        c.drawRoundRect(r, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (mood != null) tint(mood.color, 0.10f) else 0xFFFBF8F3.toInt() })
        cut(c, r)
        washi(c, r.centerX() + ((seed % 3) - 1) * 30f, r.top + 1f, 92f, 20f, ((seed * 37) % 9 - 4).toFloat(), TAPES[seed % TAPES.size], seed)
        c.drawText(dateLong(d.day) + if (more) " (sigue)" else "", r.left + 14f, r.top + 38f, k.text(24f, k.handBold))
        c.drawText(monthOf(d.day), r.left + 14f, r.top + 54f, k.text(14f, k.hand, SOFT))
        if (mood != null) {
            val bmp = tokkipBitmap(144, 144, Color(tokkipColor), face = mood.face, headroom = 0.16f)
            c.drawCircle(r.right - 32f, r.top + 36f, 21f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint(mood.color, 0.35f) })
            c.drawBitmap(bmp, null, RectF(r.right - 52f, r.top + 15f, r.right - 12f, r.top + 55f), Paint(Paint.FILTER_BITMAP_FLAG))
            val p = k.text(13f, k.hand, SOFT).apply { textAlign = Paint.Align.RIGHT }
            c.drawText(mood.label, r.right - 60f, r.top + 54f, p)
        }
        var y = r.top + 64f
        val time = k.text(7.5f, k.sans, SOFT)
        for ((i, p) in pieces.withIndex()) when (p) {
            is Piece.Line -> {
                c.drawText(p.time, r.left + 14f, y + 13f, time)
                draw(c, p.layout, r.left + 46f, y)
                y += p.h
            }
            is Piece.Photo -> {
                val w = min(190f, r.width() - 48f)
                val x = r.left + (r.width() - w) / 2
                polaroid(c, k, p.bmp, x, y + 6f, w, if ((seed + i) % 2 == 0) -2.5f else 2f, p.time)
                y += p.h
            }
        }
    }

    // ── To write by hand ─────────────────────────────────────────────────────

    private fun handwriting(pages: Pages, k: Ctx, data: AppData, notes: List<TokkipNote>, monthEnd: String) {
        val c = pages.next()
        c.drawText("Para escribir a mano", M, M + 26f, k.text(30f, k.handBold))
        val flow = Flow(pages, M + 44f)

        // My week, with lines.
        val week = flow.place(W - 2 * M, 250f)
        cut(pages.c, week)
        washi(pages.c, week.left + 70f, week.top, 100f, 20f, -4f, TAPES[2], 2)
        pages.c.drawText("Mi semana", week.left + 16f, week.top + 36f, k.text(24f, k.handBold))
        var y = week.top + 60f
        for (prompt in listOf("Lo mejor fue…", "Aprendí que…", "Me sentí…", "Para la próxima semana…")) {
            pages.c.drawText(prompt, week.left + 16f, y, k.text(16f, k.hand, SOFT))
            lines(pages.c, week.left + 16f, week.right - 16f, y + 20f, 2)
            y += 48f
        }

        // Prompt cards: two of always, and what Tokkip noticed as questions.
        val prompts = ArrayList<Pair<String, String>>()
        prompts += "Hoy agradezco…" to "tres cosas, aunque sean pequeñas"
        prompts += "Algo que me hizo sonreír…" to "¿quién estaba?, ¿dónde?"
        for (n in notes.filter { it.weight >= 20 }.take(2)) prompts += n.text to "Tokkip notó esto. ¿Tú qué crees?"
        if (prompts.size < 4) prompts += "Una cosa que quiero soltar…" to "escríbela y déjala aquí"
        for ((i, pr) in prompts.take(4).withIndex()) {
            val r = flow.place((W - 2 * M - 16f) / 2, 160f)
            pages.c.drawRoundRect(r, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (TAPES[(i + 2) % TAPES.size] and 0x00FFFFFF) or 0x30000000 })
            cut(pages.c, r)
            val head = layout(pr.first, k.text(17f, k.handBold), (r.width() - 28f).toInt(), spacing = 0.9f)
            draw(pages.c, head, r.left + 14f, r.top + 12f)
            pages.c.drawText(pr.second, r.left + 14f, r.top + 18f + head.height + 6f, k.text(12f, k.hand, SOFT))
            val from = r.top + 30f + head.height + 14f
            lines(pages.c, r.left + 14f, r.right - 14f, from, ((r.bottom - 12f - from) / 22f).toInt())
        }

        // A habit tracker for the month, to colour in.
        if (data.habits.isNotEmpty()) {
            val cal = Calendar.getInstance().apply { timeInMillis = dayStart(monthEnd) }
            val count = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val hs = data.habits.take(8)
            val r = flow.place(W - 2 * M, 54f + hs.size * 22f)
            cut(pages.c, r)
            pages.c.drawText("Mis rachas de ${monthOf(monthEnd)}", r.left + 16f, r.top + 28f, k.text(20f, k.handBold))
            val nameW = 120f
            val step = (r.width() - 32f - nameW) / count
            val num = k.text(6.5f, k.sans, SOFT).apply { textAlign = Paint.Align.CENTER }
            for (d in 1..count) pages.c.drawText("$d", r.left + 16f + nameW + step * (d - 1) + step / 2, r.top + 44f, num)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f; color = 0xFFCFC6BE.toInt() }
            for ((i, h) in hs.withIndex()) {
                val yy = r.top + 60f + i * 22f
                pages.c.drawText(TextUtilsEllipsize(h.name, k.text(14f, k.hand), nameW - 8f), r.left + 16f, yy + 4f, k.text(14f, k.hand))
                for (d in 1..count) {
                    cal.set(Calendar.DAY_OF_MONTH, d)
                    val cx = r.left + 16f + nameW + step * (d - 1) + step / 2
                    val rad = min(step / 2 - 1f, 6f)
                    if (dayKey(cal.timeInMillis) in h.done) pages.c.drawCircle(cx, yy, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint(h.color, 0.7f) })
                    else pages.c.drawCircle(cx, yy, rad, dot)
                }
            }
        }
    }

    private fun lines(c: Canvas, l: Float, r: Float, y: Float, n: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9D2CB.toInt(); strokeWidth = 0.6f; pathEffect = DashPathEffect(floatArrayOf(1f, 2.5f), 0f) }
        for (i in 0 until n) c.drawLine(l, y + i * 22f, r, y + i * 22f, p)
    }
}
