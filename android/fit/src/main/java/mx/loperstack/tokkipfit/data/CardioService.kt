// Cardio with GPS on the phone: a foreground service (its notification shows
// the time and the distance) that follows the GPS while you run, walk or
// cycle, even with the screen off. What it's following lives in CardioLive,
// which the screen watches.

package mx.loperstack.tokkipfit.data

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import mx.loperstack.tokkip.fit.Point
import mx.loperstack.tokkip.fit.Sport
import mx.loperstack.tokkip.fit.Track
import mx.loperstack.tokkip.fit.clock

/** The session being followed (one at a time). */
object CardioLive {
    var sport by mutableStateOf(Sport.RUN)
    var running by mutableStateOf(false)
    var paused by mutableStateOf(false)
    /** ms counted (pauses left out). */
    var moving by mutableLongStateOf(0L)
    var meters by mutableDoubleStateOf(0.0)
    var points by mutableIntStateOf(0)
    var hasFix by mutableStateOf(false)
    var startedAt = 0L
    var track = Track(Sport.RUN)
        private set
    private var lastTick = 0L

    fun reset(s: Sport) {
        sport = s; track = Track(s); running = true; paused = false; moving = 0; meters = 0.0; points = 0; hasFix = false
        startedAt = System.currentTimeMillis(); lastTick = startedAt
    }

    fun tick(now: Long = System.currentTimeMillis()) {
        if (running && !paused) moving += now - lastTick
        lastTick = now
    }

    fun add(p: Point) {
        hasFix = true
        if (!running || paused) return
        if (track.add(p, startedAt)) { meters = track.distance; points = track.points.size }
    }
}

class CardioService : Service(), LocationListener {
    private var lm: LocationManager? = null
    private val main = android.os.Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            CardioLive.tick()
            notifyNow()
            main.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        channel()
        startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        if (lm == null) {
            lm = getSystemService(LocationManager::class.java)
            runCatching { lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper()) }
            main.post(ticker)
        }
        return START_STICKY
    }

    override fun onLocationChanged(l: Location) {
        CardioLive.add(Point(l.latitude, l.longitude, l.time, if (l.hasAccuracy()) l.accuracy else 10f, if (l.hasAltitude()) l.altitude else null))
    }

    override fun onDestroy() {
        runCatching { lm?.removeUpdates(this) }
        main.removeCallbacks(ticker)
        super.onDestroy()
    }

    private fun channel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Cardio en curso", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private var lastShown = 0L
    private fun notifyNow() {
        val now = System.currentTimeMillis()
        if (now - lastShown < 5000) return
        lastShown = now
        getSystemService(NotificationManager::class.java).notify(ID, notification())
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, mx.loperstack.tokkipfit.MainActivity::class.java).setData(android.net.Uri.parse("tokkipfit://cardio")).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val km = String.format(java.util.Locale.US, "%.2f km", CardioLive.meters / 1000)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("${CardioLive.sport.label} con Tokkip")
            .setContentText("${clock(CardioLive.moving / 1000)} · $km${if (CardioLive.paused) " · en pausa" else ""}")
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL = "cardio"
        private const val ID = 41
        private const val STOP = "stop"

        fun start(context: Context) {
            androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, CardioService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CardioService::class.java))
        }
    }
}
