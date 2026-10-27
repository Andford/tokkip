// The diary's files: photos (copied in, upright and at most 1600 px, so the
// diary does not depend on the gallery), the day as an image (to the gallery
// or to share) and the whole diary as a PDF. The .zip copy is data/Backup.kt;
// the printable kit for a paper journal is data/JournalKit.kt.

package mx.loperstack.tokkip.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import mx.loperstack.tokkip.character.tokkipBitmap
import mx.loperstack.tokkip.wellness.moodOf
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object DiaryImages {
    private const val MAX_PX = 1600
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun dir(context: Context) = File(context.filesDir, "diary").apply { mkdirs() }

    fun file(context: Context, name: String): File? = if (isImageName(name)) File(dir(context), name) else null

    /** Copies a picked photo in; returns its name, or null if it could not be read. */
    fun import(context: Context, uri: Uri): String? = runCatching {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PX) sample *= 2
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return null
        val rotation = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        val scale = min(1f, MAX_PX.toFloat() / max(raw.width, raw.height))
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        val name = "img-${System.currentTimeMillis().toString(36)}-${(1000..9999).random()}.jpg"
        FileOutputStream(File(dir(context), name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        name
    }.getOrNull()

    /** A photo at most `maxPx` on its long side, cached. Off the main thread. */
    fun load(context: Context, name: String, maxPx: Int): Bitmap? {
        val key = "$name@$maxPx"
        cache.get(key)?.let { return it }
        val f = file(context, name) ?: return null
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        cache.put(key, bmp)
        return bmp
    }

    fun delete(context: Context, name: String?) {
        name?.let { file(context, it)?.delete() }
    }
}

object DiaryExport {
    private val MX = Locale("es", "MX")

    // ── The day as an image ───────────────────────────────────────────────────

    /** Saves a picture to Pictures/Tokkip in the gallery; true if it worked. */
    fun saveToGallery(context: Context, bmp: Bitmap, title: String): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$title.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Tokkip")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val cr = context.contentResolver
        val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        cr.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        cr.update(uri, values, null, null)
        true
    }.getOrDefault(false)

    /** Opens Android's share sheet with the picture. */
    fun share(context: Context, bmp: Bitmap, title: String) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "$title.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Compartir el día").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    /** The whole diary as a PDF: a page title, then each day with its mood, lines and photos. */
    fun pdf(context: Context, logs: List<DayLog>, name: String, tokkipColor: Long, out: Uri): Boolean = runCatching {
        val doc = PdfDocument()
        val w = 595
        val h = 842
        val margin = 48f
        val width = (w - margin * 2).toInt()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF1A1412.toInt() }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF1A1412.toInt() }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; typeface = Typeface.MONOSPACE; color = 0xFF6B7079.toInt() }
        val time = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); color = 0xFF6D28D9.toInt() }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = 0xFF222222.toInt() }
        val rule = Paint().apply { color = 0xFFE5E7EB.toInt(); strokeWidth = 0.8f }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            canvas = page!!.canvas
            y = margin
            canvas!!.drawText("Tokkip · diario", margin, h - 24f, small)
            canvas!!.drawText("$pageNo", w - margin, h - 24f, small.apply { textAlign = Paint.Align.RIGHT })
            small.textAlign = Paint.Align.LEFT
        }
        fun need(space: Float) { if (y + space > h - margin - 20) newPage() }
        fun text(t: String, p: TextPaint, x: Float, wid: Int): Float {
            val layout = StaticLayout.Builder.obtain(t, 0, t.length, p, wid).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            need(layout.height.toFloat())
            canvas!!.save(); canvas!!.translate(x, y); layout.draw(canvas!!); canvas!!.restore()
            return layout.height.toFloat()
        }

        newPage()
        val cover = tokkipBitmap(260, 216, Color(tokkipColor))
        canvas!!.drawBitmap(cover, null, RectF(margin, y, margin + 130f, y + 108f), null)
        y += 120f
        y += text(if (name.isBlank()) "Mi diario" else "El diario de $name", title, margin, width) + 6f
        val days = logs.filter { it.mood > 0 || it.notes.isNotEmpty() }
        val range = if (days.isEmpty()) "" else "${dateOf(days.first().day)} — ${dateOf(days.last().day)}"
        y += text("${days.size} días · $range", body, margin, width) + 24f

        for (d in days) {
            need(70f)
            canvas!!.drawLine(margin, y, w - margin, y, rule)
            y += 12f
            val mood = moodOf(d.mood)
            if (mood != null) {
                val face = tokkipBitmap(96, 80, Color(mood.color), face = mood.face)
                canvas!!.drawBitmap(face, null, RectF(w - margin - 48f, y - 4f, w - margin, y + 36f), null)
            }
            canvas!!.drawText("DÍA %03d".format(dayNumber(logs, d.day)), margin, y + 8f, small)
            y += 14f
            y += text(dateOf(d.day) + (mood?.let { " · ${it.label}" } ?: ""), head, margin, width - 60) + 8f
            for (n in d.notes) {
                val stamp = SimpleDateFormat("HH:mm", MX).format(Date(n.at))
                if (n.text.isNotEmpty()) {
                    need(16f)
                    canvas!!.drawText(stamp, margin, y + 10f, time)
                    y += text(n.text, body, margin + 44f, width - 44) + 6f
                }
                val img = n.image?.let { DiaryImages.load(context, it, 900) }
                if (img != null) {
                    val iw = min(300f, width - 44f)
                    val ih = min(240f, iw * img.height / img.width)
                    val iwFit = ih * img.width / img.height
                    need(ih + 10f)
                    if (n.text.isEmpty()) canvas!!.drawText(stamp, margin, y + 10f, time)
                    canvas!!.drawBitmap(img, null, RectF(margin + 44f, y, margin + 44f + iwFit, y + ih), Paint(Paint.FILTER_BITMAP_FLAG))
                    y += ih + 10f
                }
            }
            y += 14f
        }
        page?.let { doc.finishPage(it) }
        context.contentResolver.openOutputStream(out)?.use { doc.writeTo(it) }
        doc.close()
        true
    }.getOrDefault(false)

    private fun dateOf(day: String) =
        SimpleDateFormat("EEEE d 'de' MMMM yyyy", MX).format(Date(dayStart(day))).replaceFirstChar { it.uppercase(MX) }
}
