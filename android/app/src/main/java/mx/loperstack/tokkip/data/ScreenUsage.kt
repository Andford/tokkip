// Pantalla, from Android: the usage log (UsageStatsManager, with "Acceso al
// uso" that you turn on in Ajustes), read here on the phone and never sent
// anywhere. Turns it into spans (ScreenTime.kt), keeps a small summary per
// day so Tokkip can compare days, and checks every ~10 minutes while you're on
// the phone so it can nudge you after a long scroll session.

package mx.loperstack.tokkip.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import android.provider.Settings
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/** The latest reading: today so far, Tokkip's level, and when it was taken. */
data class ScreenNow(val today: ScreenToday, val level: Int, val at: Long)

object ScreenUsage {
    private val labels = ConcurrentHashMap<String, String>()
    private val kinds = ConcurrentHashMap<String, AppKind>()
    private val icons = ConcurrentHashMap<String, Bitmap>()
    private var launchers: Set<String>? = null

    /** The last reading (the app's screen, Inicio's face and the watch read it). */
    @Volatile var last: ScreenNow? = null
        private set

    fun granted(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java)
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun label(context: Context, pkg: String): String = labels.getOrPut(pkg) {
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg.substringAfterLast('.'))
    }

    fun kind(context: Context, pkg: String): AppKind = kinds.getOrPut(pkg) {
        val cat = runCatching { context.packageManager.getApplicationInfo(pkg, 0).category }.getOrDefault(ApplicationInfo.CATEGORY_UNDEFINED)
        kindOf(pkg, cat)
    }

    fun icon(context: Context, pkg: String, px: Int = 96): Bitmap? = icons[pkg] ?: runCatching {
        val d = context.packageManager.getApplicationIcon(pkg)
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, px, px)
        d.draw(Canvas(b))
        icons[pkg] = b
        b
    }.getOrNull()

    /** Launchers, the system UI and Tokkip itself don't count as "using" an app. */
    private fun ignored(context: Context): Set<String> = launchers ?: run {
        val home = runCatching {
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        (home + listOf("com.android.systemui", "android", context.packageName, "com.google.android.permissioncontroller")).toSet().also { launchers = it }
    }

    private fun startOf(ms: Long) = Calendar.getInstance().apply {
        timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** The log between two times, and how many times the phone was unlocked. */
    @Suppress("DEPRECATION")
    private fun read(context: Context, start: Long, end: Long): Pair<List<UseEvent>, Int> {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList<UseEvent>() to 0
        val out = ArrayList<UseEvent>()
        var unlocks = 0
        val list = runCatching { usm.queryEvents(start, end) }.getOrNull() ?: return emptyList<UseEvent>() to 0
        val e = UsageEvents.Event()
        while (list.hasNextEvent()) {
            list.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> out += UseEvent(e.timeStamp, e.packageName, true)
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> out += UseEvent(e.timeStamp, e.packageName, false)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> out += UseEvent(e.timeStamp, null, false)
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
            }
        }
        return out to unlocks
    }

    /** One day (from its midnight, up to `until`). Blocking: call off the main thread. */
    fun day(context: Context, dayStart: Long, until: Long, s: ScreenSettings): ScreenToday {
        // From an hour before midnight, so an app open at 0:00 is counted from then.
        val (events, unlocks) = read(context, dayStart - 3_600_000, until)
        val ignore = ignored(context)
        return summarize(
            spansOf(events, until), dayStart, until, s, unlocks,
            label = { label(context, it) }, kind = { kind(context, it) }, ignore = { it in ignore },
        )
    }

    /** Today so far, saved for Tokkip and the watch. Blocking: call off the main thread. */
    fun refresh(context: Context, now: Long = System.currentTimeMillis()): ScreenNow? {
        if (!granted(context)) return null
        Store.init(context)
        val s = Store.state.value.screen
        val today = day(context, startOf(now), now, s)
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val level = doomLevel(today.scrollMin, s.goal, today.sessionMin, hour)
        val reading = ScreenNow(today, level, now)
        val changed = last?.level != level
        last = reading
        Store.saveScreenNow(reading)
        // The days before today, once each (Android keeps about a week of detail).
        val have = Store.state.value.screenDays.map { it.day }.toSet()
        val missing = (1..7).map { back -> startOf(Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, -back) }.timeInMillis) }
            .filter { dayKey(it) !in have }
        if (missing.isNotEmpty()) {
            val past = missing.map { start ->
                val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                val d = day(context, start, end, s)
                ScreenDay(dayKey(start), d.totalMin, d.scrollMin, d.lateMin, d.unlocks)
            }.filter { it.totalMin > 0 }
            Store.addScreenDays(past)
        }
        WearSync.push(context)
        if (changed) runCatching { mx.loperstack.tokkip.widget.Widgets.refresh(context) }
        return reading
    }
}
