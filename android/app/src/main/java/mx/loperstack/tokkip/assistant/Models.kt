package mx.loperstack.tokkip.assistant

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The model Tokkip thinks with, kept on the phone. It's too big for the APK
 * (2.6 GB), so it comes once: downloaded when you ask (from Hugging Face, the
 * only network call here) or copied from a file you already have, which also
 * works with no internet at all. It lives in the app's own folder: no
 * permission needed, and it goes away with the app.
 */
data class ModelSpec(val id: String, val name: String, val file: String, val url: String, val bytes: Long, val note: String)

object Models {
    /** Gemma 4 E2B (Google, Apache 2.0), for LiteRT-LM: under 1.5 GB of memory while it talks. */
    val GEMMA_E2B = ModelSpec(
        id = "gemma4-e2b",
        name = "Gemma 4 E2B",
        file = "gemma-4-E2B-it.litertlm",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        bytes = 2_590_000_000L,
        note = "Ligero y rápido. El recomendado para 8 GB de RAM.",
    )

    private const val DIR = "modelos"

    fun dir(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, DIR).apply { mkdirs() }

    /** The model file to use: the recommended one, or any other .litertlm you copied in. */
    fun installed(context: Context): File? {
        val files = dir(context).listFiles { f -> f.isFile && f.name.endsWith(".litertlm") && f.length() > 100_000_000L }.orEmpty()
        return files.firstOrNull { it.name == GEMMA_E2B.file } ?: files.maxByOrNull { it.lastModified() }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    private const val PREFS = "tokkip_asistente"
    private const val KEY_DOWNLOAD = "descarga"

    /** Starts the download with Android's own manager: it resumes, survives restarts and shows its progress. */
    fun download(context: Context, spec: ModelSpec = GEMMA_E2B, wifiOnly: Boolean = true): Long {
        val dm = context.getSystemService(DownloadManager::class.java)
        val part = File(dir(context), spec.file + ".part").apply { delete() }
        val request = DownloadManager.Request(Uri.parse(spec.url))
            .setTitle("Cerebro de Tokkip")
            .setDescription(spec.name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(!wifiOnly)
            .setAllowedOverRoaming(false)
            .setDestinationUri(Uri.fromFile(part))
        val id = dm.enqueue(request)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_DOWNLOAD, id).apply()
        return id
    }

    sealed interface Progress {
        data object None : Progress
        data class Running(val done: Long, val total: Long, val waiting: Boolean) : Progress
        data object Failed : Progress
    }

    /** How the download goes. A finished one is moved into place here. */
    fun progress(context: Context, spec: ModelSpec = GEMMA_E2B): Progress {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_DOWNLOAD, -1L)
        if (id < 0) return Progress.None
        val dm = context.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) {
                prefs.edit().remove(KEY_DOWNLOAD).apply()
                return Progress.None
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: spec.bytes
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val part = File(dir(context), spec.file + ".part")
                    if (part.exists()) part.renameTo(File(dir(context), spec.file))
                    prefs.edit().remove(KEY_DOWNLOAD).apply()
                    Progress.None
                }
                DownloadManager.STATUS_FAILED -> {
                    prefs.edit().remove(KEY_DOWNLOAD).apply()
                    Progress.Failed
                }
                DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_PENDING -> Progress.Running(done, total, waiting = true)
                else -> Progress.Running(done, total, waiting = false)
            }
        }
        return Progress.None
    }

    fun cancel(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_DOWNLOAD, -1L)
        if (id >= 0) context.getSystemService(DownloadManager::class.java).remove(id)
        prefs.edit().remove(KEY_DOWNLOAD).apply()
    }

    /** Copies a .litertlm you picked (from Downloads, a USB drive, another phone…). */
    suspend fun import(context: Context, uri: Uri, name: String, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = name.substringAfterLast('/').ifBlank { "modelo.litertlm" }
            require(clean.endsWith(".litertlm")) { "Ese archivo no es un modelo .litertlm" }
            val target = File(dir(context), clean)
            val part = File(dir(context), "$clean.part")
            val total = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }?.takeIf { it > 0 } ?: -1L
            context.contentResolver.openInputStream(uri)!!.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        copied += n
                        if (total > 0) onProgress(copied.toFloat() / total)
                    }
                }
            }
            check(part.renameTo(target)) { "No se pudo guardar el modelo" }
            target
        }
    }

    fun delete(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    /** Space left where the model goes. */
    fun freeBytes(context: Context): Long = dir(context).usableSpace
}
