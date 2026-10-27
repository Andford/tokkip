// A copy of all of Tokkip on the phone, in one .zip: your settings, tasks,
// diary (with its photos), moods, sleep, breathing sessions, streaks, money,
// the Cuaderno and Tokkip's look. Restoring it replaces what's here. Nothing leaves the phone
// unless you save the file somewhere yourself.
//
// The Diario screen used to make its own copy with only the diary in it, so
// a copy made there lost the streaks. Both places now make this full copy;
// an old diary-only copy can still be restored (its days join what's here).

package mx.loperstack.tokkip.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a copy holds, for the toast: so you can see the streaks went in. */
data class BackupSummary(
    val habits: Int = 0,
    val days: Int = 0,
    val tasks: Int = 0,
    val nights: Int = 0,
    val photos: Int = 0,
    val money: Int = 0,
    val debts: Int = 0,
    /** Cuaderno: notes and projects. */
    val notes: Int = 0,
    val projects: Int = 0,
    /** An old copy from the Diario screen: only the diary, joined to what's here. */
    val diaryOnly: Boolean = false,
) {
    fun text(): String {
        fun n(v: Int, one: String, many: String) = if (v == 1) "1 $one" else "$v $many"
        if (diaryOnly) return "solo el diario: " + n(days, "día", "días") + " (copia antigua, sin rachas)"
        return listOf(
            n(habits, "racha", "rachas"), n(days, "día de diario", "días de diario"),
            n(tasks, "tarea", "tareas"), n(nights, "noche", "noches"), n(money, "movimiento", "movimientos"), n(debts, "pago fijo", "pagos fijos"),
            n(notes, "nota", "notas"), n(projects, "proyecto", "proyectos"), n(photos, "foto", "fotos"),
        ).joinToString(" · ")
    }
}

object Backup {
    private const val PREFS = Store.PREFS
    private const val ENTRY = "tokkip-backup.json"
    private const val DIARY_ENTRY = "diary.json"
    private const val NOTEBOOK_ENTRY = "cuaderno.json"

    private fun summaryOf(prefs: Map<String, Any?>, photos: Int) = BackupSummary(
        habits = habitsOf(prefs["habits"] as? String).size,
        days = logsOf(prefs["logs"] as? String).size,
        tasks = parseTasks(prefs["tasks"] as? String).size,
        nights = sleepOf(prefs["sleep"] as? String).size,
        photos = photos,
        money = moneyOf(prefs["money"] as? String).size,
        debts = debtsOf(prefs["debts"] as? String).size,
    )

    /** Everything to `out`; what went in, or null if it failed. */
    fun export(context: Context, out: Uri): BackupSummary? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = prefs.all
        val values = JSONObject()
        for ((k, v) in all) {
            val typed = when (v) {
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is String -> JSONObject().put("t", "s").put("v", v)
                is Set<*> -> JSONObject().put("t", "ss").put("v", org.json.JSONArray(v.map { it.toString() }))
                else -> null
            }
            if (typed != null) values.put(k, typed)
        }
        val photos = DiaryImages.dir(context).listFiles()?.filter { isImageName(it.name) } ?: emptyList()
        val notebook = runCatching { Notebook.exportJson() }.getOrNull()
        val summary = summaryOf(all, photos.size).copy(
            notes = notebook?.optJSONArray("notes")?.length() ?: 0, projects = notebook?.optJSONArray("projects")?.length() ?: 0,
        )
        val root = JSONObject().put("app", "tokkip").put("version", 2).put("at", System.currentTimeMillis())
            .put("summary", JSONObject().put("habits", summary.habits).put("days", summary.days).put("tasks", summary.tasks).put("nights", summary.nights))
            .put("prefs", values)
        context.contentResolver.openOutputStream(out)?.use { os ->
            ZipOutputStream(os).use { zip ->
                zip.putNextEntry(ZipEntry(ENTRY))
                zip.write(root.toString().toByteArray())
                zip.closeEntry()
                if (notebook != null) {
                    zip.putNextEntry(ZipEntry(NOTEBOOK_ENTRY))
                    zip.write(notebook.toString().toByteArray())
                    zip.closeEntry()
                }
                photos.forEach { f ->
                    zip.putNextEntry(ZipEntry("diary/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: return null
        summary
    }.getOrNull()

    /**
     * Replaces everything with the copy at `src` (or, for an old diary-only
     * copy, adds its days to the diary). What came back, or null if the file
     * isn't a Tokkip copy.
     */
    fun restore(context: Context, src: Uri): BackupSummary? = runCatching {
        var json: JSONObject? = null
        var diary: List<DayLog>? = null
        var notebook: JSONObject? = null
        val photos = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        context.contentResolver.openInputStream(src)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    // Photos: only our own names, so a crafted entry can't write outside the folder.
                    val photo = when {
                        name.startsWith("diary/") -> name.removePrefix("diary/")
                        name.startsWith("images/") -> name.removePrefix("images/")
                        else -> null
                    }
                    when {
                        name == ENTRY || name == mx.loperstack.tokkip.wellness.Legacy.BACKUP_ENTRY -> json = JSONObject(zip.readBytes().decodeToString())
                        name == NOTEBOOK_ENTRY -> notebook = JSONObject(zip.readBytes().decodeToString())
                        name == DIARY_ENTRY -> diary = logsOf(zip.readBytes().decodeToString())
                        photo != null && isImageName(photo) -> FileOutputStream(File(photos, photo)).use { zip.copyTo(it) }
                    }
                }
            }
        }
        val dir = DiaryImages.dir(context)
        val root = json
        if (root == null) {
            // An old copy from the Diario screen: its days join the diary.
            val days = diary ?: return null
            photos.listFiles()?.forEach { f -> File(dir, f.name).let { if (!it.exists()) f.copyTo(it) } }
            photos.deleteRecursively()
            Store.setLogs(mergeLogs(Store.state.value.logs, days))
            return BackupSummary(days = days.size, diaryOnly = true)
        }
        if (root.optString("app") != "tokkip" && root.optString("app") != mx.loperstack.tokkip.wellness.Legacy.BACKUP_APP) return null
        val values = root.optJSONObject("prefs") ?: return null
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        val restored = HashMap<String, Any?>()
        for (k0 in values.keys()) {
            val o = values.optJSONObject(k0) ?: continue
            // A copy from an older version: its settings under today's names.
            val k = mx.loperstack.tokkip.wellness.Legacy.KEYS[k0] ?: k0
            when (o.optString("t")) {
                "b" -> e.putBoolean(k, o.optBoolean("v"))
                "i" -> e.putInt(k, o.optInt("v"))
                "l" -> e.putLong(k, o.optLong("v"))
                "f" -> e.putFloat(k, o.optDouble("v").toFloat())
                "s" -> o.optString("v").let { v -> (if (k == "theme") mx.loperstack.tokkip.wellness.Legacy.theme(v) ?: v else v).let { e.putString(k, it); restored[k] = it } }
                "ss" -> o.optJSONArray("v")?.let { a -> e.putStringSet(k, (0 until a.length()).map { a.optString(it) }.toSet()) }
            }
        }
        e.commit()
        val count = photos.listFiles()?.size ?: 0
        photos.listFiles()?.forEach { it.copyTo(File(dir, it.name), overwrite = true) }
        photos.deleteRecursively()
        // A copy from before the Cuaderno leaves yours as it is.
        val nb = notebook
        if (nb != null) Notebook.importJson(nb)
        Store.reload()
        summaryOf(restored, count).copy(notes = nb?.optJSONArray("notes")?.length() ?: 0, projects = nb?.optJSONArray("projects")?.length() ?: 0)
    }.getOrNull()
}
