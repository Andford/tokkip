// "Ciclo"'s reminders: a check every morning at 9 that says, if you asked for
// it, that your period may come in two days, or that your fertile window starts
// today. On the lock screen it only says "Tokkip: recordatorio de Ciclo".

package mx.loperstack.tokkip.wellness

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import mx.loperstack.tokkip.MainActivity
import mx.loperstack.tokkip.data.CycleMath
import mx.loperstack.tokkip.data.CycleStore
import java.util.Calendar

object CycleReminder {
    private const val CHANNEL = "ciclo"
    private const val ID = 41

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 41, Intent(context, CycleReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun nextNine(now: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun sync(context: Context) {
        CycleStore.init(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context)
        val d = CycleStore.state.value
        if (!d.enabled || (!d.remindPeriod && !d.remindFertile)) {
            am.cancel(pi)
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextNine(System.currentTimeMillis()), pi)
    }

    /** What to say this morning, if anything. */
    fun message(today: Int): String? {
        val d = CycleStore.state.value
        if (!d.enabled || d.period.isEmpty()) return null
        val f = CycleMath.forecast(d.period, today, d.notes)
        val next = f.nextStart ?: return null
        return when {
            d.remindPeriod && !f.inPeriod && next - today == 2 -> "Tu periodo podría llegar en 2 días. Ten a la mano lo que necesites."
            d.remindPeriod && !f.inPeriod && f.late == 3 -> "Tu periodo lleva 3 días de retraso según tus ciclos. Si te preocupa, consulta a tu médico."
            d.remindFertile && f.fertileFrom == today -> "Hoy empieza tu ventana fértil estimada (hasta dentro de 6 días)."
            else -> null
        }
    }

    fun ring(context: Context) {
        CycleStore.init(context)
        message(CycleStore.today())?.let { text ->
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                TokkipNotifier.channel(context, CHANNEL, "Ciclo", "Avisos de tu periodo y ventana fértil", NotificationManager.IMPORTANCE_DEFAULT, mx.loperstack.tokkip.shared.R.raw.notif_task),
            )
            val open = PendingIntent.getActivity(
                context, 42,
                Intent(context, MainActivity::class.java).setData(Uri.parse("tokkip://cycle")).putExtra(MainActivity.EXTRA_TAB, "cycle")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val public = Notification.Builder(context, CHANNEL)
                .setSmallIcon(mx.loperstack.tokkip.shared.R.drawable.ic_stat_tokkip)
                .setContentTitle("Tokkip: recordatorio de Ciclo")
                .build()
            val n = Notification.Builder(context, CHANNEL)
                .setSmallIcon(mx.loperstack.tokkip.shared.R.drawable.ic_stat_tokkip)
                .setContentTitle("Ciclo")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(ID, n) }
        }
        sync(context)
    }
}

class CycleReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = CycleReminder.ring(context)
}
