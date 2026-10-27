// Names older versions used for your saved settings, moved once to Tokkip's
// own. Only here so nothing you had is lost when updating; nothing new is
// ever written under them.

package mx.loperstack.tokkip.wellness

import android.content.Context

object Legacy {
    /** The phone's settings file, the watch's, and the two keys that changed name. */
    const val PHONE_PREFS = "mochi"
    const val WATCH_PREFS = "mochi-watch"
    val KEYS = mapOf("mochiColor" to "tokkipColor", "mochiFlat" to "tokkipFlat")
    /** The theme id that's now "tokkip". */
    const val THEME = "mochi"
    /** What a backup .zip from an older version calls itself. */
    const val BACKUP_APP = "mochi"
    const val BACKUP_ENTRY = "mochi-backup.json"

    fun theme(id: String?): String? = if (id == THEME) "tokkip" else id

    /** Moves every setting from `from` to `to` (renaming the old keys), once; then the old file goes. */
    fun movePrefs(context: Context, from: String, to: String) {
        val src = context.getSharedPreferences(from, Context.MODE_PRIVATE)
        val all = src.all
        if (all.isEmpty()) return
        val dst = context.getSharedPreferences(to, Context.MODE_PRIVATE)
        val e = dst.edit()
        for ((k0, v) in all) {
            val k = KEYS[k0] ?: k0
            if (dst.contains(k)) continue
            when (v) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is String -> e.putString(k, if (k == "theme") theme(v) else v)
                is Set<*> -> e.putStringSet(k, v.map { it.toString() }.toSet())
            }
        }
        if (e.commit()) {
            src.edit().clear().commit()
            runCatching { context.deleteSharedPreferences(from) }
        }
    }
}
