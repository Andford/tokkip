// Tokkip's alarm clock, the ringing half (the alarms themselves are in
// data/Alarms.kt). Only the next alarm is handed to Android, with
// setAlarmClock(): it rings on the minute even in Doze, and the system shows
// the little alarm clock in the status bar. When it rings, WakeAlarmService
// plays Tokkip's wake-up tune on the alarm stream (it sounds in silent mode and
// through Do Not Disturb, as alarms do), growing louder over a minute, and
// shows AlarmActivity over the lock screen.

package mx.loperstack.tokkip.wellness

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.loperstack.tokkip.MainActivity
import mx.loperstack.tokkip.data.Snooze
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.WakeAlarm as Alarm
import mx.loperstack.tokkip.data.hhmm
import mx.loperstack.tokkip.data.nextOf
import mx.loperstack.tokkip.ui.AlarmActivity

/** What is ringing now, for the alarm screen. */
data class Ringing(
    val id: String, val minute: Int, val label: String, val breathe: Boolean, val snoozeMin: Int, val since: Long,
    /** A test from the edit sheet: putting it off or turning it off changes nothing. */
    val preview: Boolean = false,
)

object WakeAlarm {
    const val ACTION_RING = "mx.loperstack.tokkip.alarm.RING"
    const val ACTION_SNOOZE = "mx.loperstack.tokkip.alarm.SNOOZE"
    const val ACTION_DISMISS = "mx.loperstack.tokkip.alarm.DISMISS"
    const val ACTION_QUIET = "mx.loperstack.tokkip.alarm.QUIET"
    const val EXTRA_ID = "alarmId"
    internal const val CHANNEL = "alarm_v1"
    internal const val NOTIFICATION = 41

    private val _ringing = MutableStateFlow<Ringing?>(null)
    val ringing: StateFlow<Ringing?> = _ringing.asStateFlow()
    internal fun setRinging(r: Ringing?) { _ringing.value = r }

    private fun ringPending(context: Context, id: String) = PendingIntent.getBroadcast(
        context, NOTIFICATION, Intent(context, WakeAlarmReceiver::class.java).setAction(ACTION_RING).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Tapping the alarm clock in the status bar opens Sueño, where the alarms are. */
    private fun showPending(context: Context) = PendingIntent.getActivity(
        context, NOTIFICATION + 1,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "sleep")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Hands the next alarm (or snooze) to Android, or cancels it when there is none. */
    fun sync(context: Context) {
        Store.init(context)
        val d = Store.state.value
        val am = context.getSystemService(AlarmManager::class.java)
        val next = nextOf(d.alarms, d.snooze, System.currentTimeMillis())
        if (next == null) {
            am.cancel(PendingIntent.getBroadcast(
                context, NOTIFICATION, Intent(context, WakeAlarmReceiver::class.java).setAction(ACTION_RING),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ))
            return
        }
        val (id, at) = next
        val ring = ringPending(context, id)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showPending(context)), ring)
        } catch (_: SecurityException) {
            // Exact alarms switched off for Tokkip: as close as Android allows.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, ring)
        }
    }

    /** Android 14+: the alarm may cover the lock screen only with this permission. */
    fun canFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun fullScreenSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
        else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun notificationsOn(context: Context): Boolean = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun snooze(context: Context) = context.startService(Intent(context, WakeAlarmService::class.java).setAction(ACTION_SNOOZE))
    fun dismiss(context: Context) = context.startService(Intent(context, WakeAlarmService::class.java).setAction(ACTION_DISMISS))
    /** While you breathe to turn it off: the tune goes soft and the phone stops buzzing. */
    fun quiet(context: Context) = context.startService(Intent(context, WakeAlarmService::class.java).setAction(ACTION_QUIET))

    /** A test from the edit sheet: rings now, as the real thing would, with the sheet's settings. */
    fun preview(context: Context, a: Alarm) {
        context.startForegroundService(
            Intent(context, WakeAlarmService::class.java).setAction(ACTION_RING).putExtra(EXTRA_ID, a.id).putExtra(EXTRA_PREVIEW, true)
                .putExtra("minute", a.minute).putExtra("label", a.label).putExtra("gentle", a.gentle)
                .putExtra("vibrate", a.vibrate).putExtra("breathe", a.breathe).putExtra("snoozeMin", a.snoozeMin),
        )
    }

    internal const val EXTRA_PREVIEW = "preview"

    /**
     * It rang: a one-time alarm switches itself off and a snooze is used up,
     * then the next one is scheduled. Returns the alarm, or null if it is gone.
     */
    internal fun rang(context: Context, id: String): Alarm? {
        Store.init(context)
        val d = Store.state.value
        val alarm = d.alarms.firstOrNull { it.id == id }
        if (d.snooze?.id == id) Store.setSnooze(null)
        if (alarm != null && alarm.days.isEmpty() && d.snooze?.id != id) {
            Store.setAlarms(d.alarms.map { if (it.id == id) it.copy(enabled = false) else it })
        } else sync(context)
        return alarm
    }
}

/** The ring itself, a reboot, a change of time or time zone, or an update: all end in the right next alarm. */
class WakeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WakeAlarm.ACTION_RING -> {
                val id = intent.getStringExtra(WakeAlarm.EXTRA_ID) ?: return
                context.startForegroundService(
                    Intent(context, WakeAlarmService::class.java).setAction(WakeAlarm.ACTION_RING).putExtra(WakeAlarm.EXTRA_ID, id),
                )
            }
            else -> WakeAlarm.sync(context)
        }
    }
}

/** Plays the tune, vibrates, and keeps the alarm's notification (with its full-screen face) while it rings. */
class WakeAlarmService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var volume = 1f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            WakeAlarm.ACTION_RING -> {
                val id = intent.getStringExtra(WakeAlarm.EXTRA_ID) ?: return stopNow()
                val preview = intent.getBooleanExtra(WakeAlarm.EXTRA_PREVIEW, false)
                val alarm = if (preview) {
                    Alarm(
                        id, intent.getIntExtra("minute", 0), label = intent.getStringExtra("label") ?: "",
                        gentle = intent.getBooleanExtra("gentle", true), vibrate = intent.getBooleanExtra("vibrate", true),
                        breathe = intent.getBooleanExtra("breathe", false), snoozeMin = intent.getIntExtra("snoozeMin", 9),
                    )
                } else WakeAlarm.rang(this, id)
                if (alarm == null) return stopNow()
                start(alarm, preview)
            }
            WakeAlarm.ACTION_SNOOZE -> {
                WakeAlarm.ringing.value?.takeIf { !it.preview }?.let { r ->
                    Store.init(this)
                    Store.setSnooze(Snooze(r.id, System.currentTimeMillis() + r.snoozeMin * 60_000L))
                }
                return stopNow()
            }
            WakeAlarm.ACTION_DISMISS -> return stopNow()
            WakeAlarm.ACTION_QUIET -> {
                // Keep the ten-minute stop; drop the growing volume and the buzz.
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({ stopNow() }, 10 * 60_000L)
                volume = 0.15f
                player?.let { runCatching { it.setVolume(volume, volume) } }
                runCatching { getSystemService(VibratorManager::class.java)?.defaultVibrator?.cancel() }
            }
            else -> if (WakeAlarm.ringing.value == null) return stopNow()
        }
        return START_NOT_STICKY
    }

    private fun start(a: Alarm, preview: Boolean) {
        stopSound()
        val r = Ringing(a.id, a.minute, a.label, a.breathe, a.snoozeMin, System.currentTimeMillis(), preview)
        WakeAlarm.setRinging(r)
        val n = notification(r)
        if (Build.VERSION.SDK_INT >= 29) startForeground(WakeAlarm.NOTIFICATION, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(WakeAlarm.NOTIFICATION, n)
        // Also straight to the screen when the phone is in use (a full-screen
        // notification only takes over a locked or idle screen).
        runCatching {
            startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION))
        }
        play(a.gentle)
        if (a.vibrate) vibrate()
        // Ten minutes is enough: it stops by itself, as other alarm clocks do.
        handler.postDelayed({ stopNow() }, 10 * 60_000L)
    }

    private fun notification(r: Ringing): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(WakeAlarm.CHANNEL, "Despertador", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tus alarmas, cuando suenan"
                setSound(null, null) // the service plays the tune itself
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        val face = PendingIntent.getActivity(
            this, WakeAlarm.NOTIFICATION + 2, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(act: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, WakeAlarmService::class.java).setAction(act), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = Notification.Builder(this, WakeAlarm.CHANNEL)
            .setSmallIcon(mx.loperstack.tokkip.shared.R.drawable.ic_stat_tokkip)
            .setContentTitle(if (r.label.isBlank()) "Despertador · ${hhmm(r.minute)}" else "${r.label} · ${hhmm(r.minute)}")
            .setContentText(if (r.breathe) "Respira con Tokkip para apagarla" else "Tokkip te está despertando")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(face)
            .setFullScreenIntent(face, true)
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Posponer ${r.snoozeMin} min", action(WakeAlarm.ACTION_SNOOZE, WakeAlarm.NOTIFICATION + 3)).build())
        // With "breathe to stop" on, the notification can only put it off: turning it off takes the breaths.
        if (!r.breathe) b.addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Apagar", action(WakeAlarm.ACTION_DISMISS, WakeAlarm.NOTIFICATION + 4)).build())
        return b.build()
    }

    private fun play(gentle: Boolean) {
        val uri = Uri.parse("android.resource://$packageName/${mx.loperstack.tokkip.shared.R.raw.alarm_wake}")
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(this@WakeAlarmService, uri)
                setWakeMode(this@WakeAlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                isLooping = true
                prepare()
                volume = if (gentle) 0.08f else 1f
                setVolume(volume, volume)
                start()
            }
        }.getOrNull()
        // Gentle: from a whisper to full over a minute.
        if (gentle) {
            val step = object : Runnable {
                override fun run() {
                    val p = player ?: return
                    volume = (volume + 0.92f / 60f).coerceAtMost(1f)
                    runCatching { p.setVolume(volume, volume) }
                    if (volume < 1f) handler.postDelayed(this, 1000)
                }
            }
            handler.postDelayed(step, 1000)
        }
    }

    private fun vibrate() {
        val v = getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        @Suppress("DEPRECATION")
        v.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500, 1200), 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
        )
    }

    private fun stopSound() {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        runCatching { getSystemService(VibratorManager::class.java)?.defaultVibrator?.cancel() }
    }

    private fun stopNow(): Int {
        stopSound()
        WakeAlarm.setRinging(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSound()
        WakeAlarm.setRinging(null)
        super.onDestroy()
    }
}
