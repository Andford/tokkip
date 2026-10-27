// The Pomodoro outside the app: an exact alarm at the end of each phase, an
// ongoing notification with a live countdown (the system ticks it, nothing of
// ours runs meanwhile) and Pause / Resume / Stop from the notification.

package mx.loperstack.tokkip.wellness

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.app.Notification
import mx.loperstack.tokkip.MainActivity
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.WearSync
import android.media.AudioAttributes
import android.net.Uri

private val NO_ICON: android.graphics.drawable.Icon? = null

object PomodoroScheduler {
    private const val CHANNEL_RUNNING = "pomodoro_running"
    // v3: with Tokkip's own kalimba tone.
    private const val CHANNEL_DONE = "pomodoro_done_v3"
    private const val ID_RUNNING = 1
    private const val ID_DONE = 2

    const val ACTION_END = "mx.loperstack.tokkip.POMODORO_END"
    const val ACTION_PAUSE = "mx.loperstack.tokkip.POMODORO_PAUSE"
    const val ACTION_RESUME = "mx.loperstack.tokkip.POMODORO_RESUME"
    const val ACTION_STOP = "mx.loperstack.tokkip.POMODORO_STOP"

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, "Pomodoro en curso", NotificationManager.IMPORTANCE_LOW).apply {
                description = "La cuenta regresiva del bloque actual"
                setShowBadge(false)
            },
        )
        nm.deleteNotificationChannel("pomodoro_done")
        nm.deleteNotificationChannel("pomodoro_done_v2")
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Fin de bloque", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Cuando termina un bloque de enfoque o un descanso"
                enableVibration(true)
                lightColor = Color.parseColor("#A78BFA")
                setSound(
                    Uri.parse("android.resource://${context.packageName}/${mx.loperstack.tokkip.shared.R.raw.notif_focus}"),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
                )
            },
        )
    }

    private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, PomodoroReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 10,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "focus")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Puts the alarm and the notification in line with the Pomodoro as it is now. */
    fun sync(context: Context, p: Pomodoro, now: Long = System.currentTimeMillis()) {
        val am = context.getSystemService(AlarmManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        val end = broadcast(context, ACTION_END, 1)
        am.cancel(end)

        if (p.phase == Phase.IDLE) {
            nm.cancel(ID_RUNNING)
            return
        }
        val title = phaseTitle(p.phase)
        val builder = Notification.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(mx.loperstack.tokkip.shared.R.drawable.ic_stat_tokkip)
            .setColor(Color.parseColor("#A78BFA"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(openApp(context))
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (p.isRunning()) {
            val endsAt = p.endsAt ?: return
            builder.setContentTitle(title)
                .setContentText(if (p.phase == Phase.FOCUS) "Tokkip se puso los lentes" else "Estírate, toma agua")
                .setWhen(endsAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(Notification.Action.Builder(NO_ICON, "Pausar", broadcast(context, ACTION_PAUSE, 2)).build())
                .addAction(Notification.Action.Builder(NO_ICON, "Terminar", broadcast(context, ACTION_STOP, 4)).build())
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, end)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, end)
            }
        } else {
            builder.setContentTitle("$title · en pausa")
                .setContentText("Quedan ${clock(p.left(now))}")
                .setShowWhen(false)
                .addAction(Notification.Action.Builder(NO_ICON, "Reanudar", broadcast(context, ACTION_RESUME, 3)).build())
                .addAction(Notification.Action.Builder(NO_ICON, "Terminar", broadcast(context, ACTION_STOP, 4)).build())
        }
        runCatching { nm.notify(ID_RUNNING, builder.build()) }
    }

    /** A phase ran out: say so out loud (the running one is replaced by sync()). */
    fun announce(context: Context, event: PomodoroEvent, next: Pomodoro) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val (title, text) = when (event) {
            PomodoroEvent.FOCUS_DONE -> "¡Bloque terminado!" to
                (if (next.phase == Phase.LONG_BREAK) "Te ganaste 15 minutos de descanso" else "5 minutos de descanso · llevas ${next.today} hoy")
            PomodoroEvent.BREAK_DONE -> "De vuelta al enfoque" to "25 minutos más, tú puedes"
        }
        val n = Notification.Builder(context, CHANNEL_DONE)
            .setSmallIcon(mx.loperstack.tokkip.shared.R.drawable.ic_stat_tokkip)
            .setColor(Color.parseColor("#A78BFA"))
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { nm.notify(ID_DONE, n) }
    }

    fun phaseTitle(phase: Phase) = when (phase) {
        Phase.FOCUS -> "Enfoque"
        Phase.BREAK -> "Descanso corto"
        Phase.LONG_BREAK -> "Descanso largo"
        Phase.IDLE -> "Listo para enfocarte"
    }
}

class PomodoroReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        PomodoroScheduler.channels(context)
        val now = System.currentTimeMillis()
        val p = Store.state.value.pomodoro
        val next = when (intent.action) {
            PomodoroScheduler.ACTION_END -> {
                val (n, event) = p.tick(now + 1500)
                if (event != null) PomodoroScheduler.announce(context, event, n)
                n
            }
            PomodoroScheduler.ACTION_PAUSE -> p.pause(now)
            PomodoroScheduler.ACTION_RESUME -> p.resume(now)
            PomodoroScheduler.ACTION_STOP -> p.reset()
            else -> p
        }
        Store.setPomodoro(next)
        PomodoroScheduler.sync(context, next, now)
        WearSync.push(context)
    }
}
