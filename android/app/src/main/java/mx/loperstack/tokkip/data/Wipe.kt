// Ajustes → Empezar de cero: erases everything Tokkip keeps on the phone, as
// if it had just been installed. Tells the watch first (it gets an empty
// Tokkip), puts the rabbit icon back, then asks Android to clear the app's
// data, which also cancels its alarms and notifications and closes the app.

package mx.loperstack.tokkip.data

import android.app.ActivityManager
import android.content.Context
import java.io.File

object Wipe {
    /** Blocking (it waits a moment for the watch): call off the main thread. Doesn't return when it works. */
    fun everything(context: Context) {
        val app = context.applicationContext
        // What the watch and the widgets show: empty, before the app goes away.
        runCatching {
            app.getSharedPreferences(Store.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
            app.getSharedPreferences("tokkip_car", Context.MODE_PRIVATE).edit().clear().commit()
            Store.reload()
        }
        runCatching { AppIcon.apply(app, "CONEJO") }
        runCatching { androidx.core.app.NotificationManagerCompat.from(app).cancelAll() }
        // The watch's update leaves 300 ms after the change; give it time to go.
        Thread.sleep(1500)
        val cleared = runCatching { app.getSystemService(ActivityManager::class.java).clearApplicationUserData() }.getOrDefault(false)
        if (!cleared) {
            // Rare: Android said no. Erase our own files at least; the settings are already empty.
            runCatching { File(app.filesDir, "diary").deleteRecursively() }
            runCatching { Notebook.wipe() }
            runCatching { app.cacheDir.deleteRecursively() }
        }
    }
}
