// "Hora de prepararte para dormir": a nudge 30 minutes before your bedtime
// goal, with the tip of the day and a 5-minute breathing session one tap away.
// Rescheduled each time it rings and whenever the app opens or the goal changes.

package mx.loperstack.tokkip.wellness

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import mx.loperstack.tokkip.MainActivity
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.dailyTip
import mx.loperstack.tokkip.data.hhmm
import java.util.Calendar

object BedReminder {
    // v2: with Tokkip's music-box tone.
    private const val CHANNEL = "bedtime_v2"
    private const val ID = 31

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 31, Intent(context, BedReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The next time 30 min before bedtime, after `now`. */
    fun nextAt(bedMin: Int, now: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            val m = ((bedMin - 30) + 1440) % 1440
            set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun sync(context: Context) {
        Store.init(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context)
        val d = Store.state.value
        if (!d.bedReminder) {
            am.cancel(pi)
            return
        }
        val at = nextAt(d.sleepGoal.bedMin, System.currentTimeMillis())
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun ring(context: Context) {
        Store.init(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        runCatching { nm.deleteNotificationChannel("bedtime") }
        nm.createNotificationChannel(
            TokkipNotifier.channel(context, CHANNEL, "Hora de dormir", "Media hora antes de tu hora de dormir", NotificationManager.IMPORTANCE_DEFAULT, mx.loperstack.tokkip.shared.R.raw.notif_night),
        )
        val tip = dailyTip(System.currentTimeMillis())
        val open = PendingIntent.getActivity(
            context, 32,
            Intent(context, MainActivity::class.java).setData(Uri.parse("tokkip://sleep")).putExtra(MainActivity.EXTRA_TAB, "sleep")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val breathe = PendingIntent.getActivity(
            context, 33,
            Intent(context, MainActivity::class.java).setData(Uri.parse("tokkip://breathe/5"))
                .putExtra(MainActivity.EXTRA_TAB, "breathe").putExtra(MainActivity.EXTRA_BREATHE_MIN, 5)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val name = Store.state.value.name
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(mx.loperstack.tokkip.shared.R.drawable.ic_stat_tokkip)
            .setContentTitle("${if (name.isBlank()) "" else "$name, "}en 30 min es hora de dormir (${hhmm(Store.state.value.sleepGoal.bedMin)})")
            .setContentText("${tip.icon} ${tip.title}")
            .setStyle(Notification.BigTextStyle().bigText("${tip.icon} ${tip.title}\n${tip.text}"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Respirar 5 min", breathe).build())
            .build()
        runCatching { nm.notify(ID, n) }
        sync(context)
    }
}

class BedReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = BedReminder.ring(context)
}
