// The watch's copy of the phone's state, and its way back to the phone.
// The phone publishes a Snapshot (shared/…/Sync.kt); the watch keeps the last
// one (so the tile and complications have something with the phone away) and
// sends small messages for what you do on the wrist.

package mx.loperstack.tokkip.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.wellness.BreathSession
import mx.loperstack.tokkip.wellness.Breathing
import mx.loperstack.tokkip.wellness.breathingOf
import mx.loperstack.tokkip.wellness.Snapshot
import mx.loperstack.tokkip.wellness.SyncPaths
import mx.loperstack.tokkip.wellness.snapshotOf
import mx.loperstack.tokkip.wellness.toJson
import mx.loperstack.tokkip.wellness.toggleTask

object WatchStore {
    private lateinit var prefs: SharedPreferences
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    /** A session the phone started: the breathe screen opens it, in step. */
    val breathRequest = MutableStateFlow<Breathing?>(null)
    /** The phone cut its session short (its start time). */
    val stopRequest = MutableStateFlow<Long?>(null)
    /** The app is on screen (no notification needed). */
    @Volatile var foreground = false

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        app = context.applicationContext
        mx.loperstack.tokkip.wellness.Legacy.movePrefs(app, mx.loperstack.tokkip.wellness.Legacy.WATCH_PREFS, "tokkip-watch")
        prefs = app.getSharedPreferences("tokkip-watch", Context.MODE_PRIVATE)
        _state.value = snapshotOf(prefs.getString("snapshot", null))
        Sounds.enabled = _state.value.soundOn
        mx.loperstack.tokkip.character.Haptics.enabled = _state.value.haptics
        mx.loperstack.tokkip.character.Haptics.strength = _state.value.hapticStrength
        mx.loperstack.tokkip.character.TokkipStyle.flat = _state.value.flat
        applyLook()
    }

    /** A new state from the phone. */
    fun receive(json: String) {
        val s = snapshotOf(json)
        if (s.at < _state.value.at) return
        _state.value = s
        Sounds.enabled = s.soundOn
        mx.loperstack.tokkip.character.Haptics.enabled = s.haptics
        mx.loperstack.tokkip.character.Haptics.strength = s.hapticStrength
        mx.loperstack.tokkip.character.TokkipStyle.flat = s.flat
        applyLook()
        prefs.edit().putString("snapshot", json).apply()
        refreshSurfaces()
    }

    /** The tile and the complications show the new state too. */
    fun refreshSurfaces() {
        runCatching { TileService.getUpdater(app).requestUpdate(TokkipTileService::class.java) }
        runCatching { TileService.getUpdater(app).requestUpdate(MoodTileService::class.java) }
        for (cls in listOf(PendingComplicationService::class.java, PhoneBatteryComplicationService::class.java, MoodComplicationService::class.java, DoomComplicationService::class.java)) {
            runCatching { ComplicationDataSourceUpdateRequester.create(app, ComponentName(app, cls)).requestUpdateAll() }
        }
    }

    /** Reads the last state the phone published (opening the app). */
    fun pull() {
        scope.launch {
            runCatching {
                val items = Wearable.getDataClient(app).dataItems.await()
                try {
                    for (item in items) {
                        if (item.uri.path == SyncPaths.STATE) {
                            DataMapItem.fromDataItem(item).dataMap.getString("json")?.let { receive(it) }
                        }
                    }
                } finally {
                    items.release()
                }
            }
            send(SyncPaths.REQUEST, "")
        }
    }

    fun send(path: String, text: String) {
        scope.launch {
            runCatching {
                val nodes = Wearable.getNodeClient(app).connectedNodes.await()
                for (n in nodes) Wearable.getMessageClient(app).sendMessage(n.id, path, text.toByteArray(Charsets.UTF_8)).await()
            }
        }
    }

    // ── What the wrist can do (shown at once, then confirmed by the phone) ────

    fun toggleTask(id: String) {
        val s = _state.value
        val was = s.tasks.firstOrNull { it.id == id } ?: return
        _state.value = s.copy(tasks = toggleTask(s.tasks, id))
        Sounds.play(if (was.done) "blip" else "approve")
        send(SyncPaths.TOGGLE_TASK, id)
    }

    fun addTask(text: String) {
        if (text.isBlank()) return
        Sounds.play("pop")
        send(SyncPaths.ADD_TASK, text.trim())
    }

    fun pomodoro() {
        Sounds.play("work")
        send(SyncPaths.POMODORO, "")
    }

    fun session(s: BreathSession) {
        // Kept here too, so the report has it before the phone answers.
        val mine = (mx.loperstack.tokkip.wellness.sessionsOf(prefs.getString("sessions", null)).filter { it.at != s.at } + s).sortedBy { it.at }.takeLast(10)
        prefs.edit().putString("sessions", mx.loperstack.tokkip.wellness.sessionsJson(mine)).apply()
        send(SyncPaths.SESSION, s.toJson())
    }

    /** The phone's last sessions and the ones made here, newest first; the one with more pulse wins. */
    fun allSessions(): List<BreathSession> {
        val mine = mx.loperstack.tokkip.wellness.sessionsOf(prefs.getString("sessions", null))
        return (_state.value.sessions + mine).groupBy { it.at }
            .map { (_, l) -> l.maxBy { it.hr.size + it.rr.size } }
            .sortedByDescending { it.at }
    }

    fun heartRate(bpm: Int) = send(SyncPaths.HEART_RATE, bpm.toString())

    /** Today's mood for the diary on the phone (from the app, the tile or the complication). */
    fun mood(value: Int, sound: Boolean = true) {
        // "now" marks it as today's, so the tile and complication trust it.
        _state.value = _state.value.copy(moodToday = value, at = maxOf(_state.value.at, System.currentTimeMillis()))
        prefs.edit().putString("snapshot", _state.value.toJson()).apply()
        if (sound) Sounds.play(if (value >= 4) "love" else "pop")
        send(SyncPaths.MOOD, value.toString())
        refreshSurfaces()
    }

    /** Today's hat and last night's rest, as the phone says. */
    fun applyLook() {
        val s = _state.value
        mx.loperstack.tokkip.wellness.PomodoroConfig.current = mx.loperstack.tokkip.wellness.PomodoroConfig.current.copy(focusMin = s.focusMin, breakMin = s.breakMin)
        mx.loperstack.tokkip.character.TokkipStyle.outfit = mx.loperstack.tokkip.character.Outfit.of(s.outfit) ?: mx.loperstack.tokkip.character.Outfit.NONE
        mx.loperstack.tokkip.character.TokkipStyle.species = mx.loperstack.tokkip.character.Species.of(s.species)
        mx.loperstack.tokkip.character.TokkipStyle.skin = mx.loperstack.tokkip.character.Skin.of(s.skin)
        mx.loperstack.tokkip.character.TokkipStyle.accent = androidx.compose.ui.graphics.Color(s.accent)
        mx.loperstack.tokkip.character.TokkipStyle.ink = if (s.faceInk == 0L) androidx.compose.ui.graphics.Color.Unspecified else androidx.compose.ui.graphics.Color(s.faceInk)
        mx.loperstack.tokkip.character.TokkipStyle.cheek = if (s.cheek == 0L) androidx.compose.ui.graphics.Color.Unspecified else androidx.compose.ui.graphics.Color(s.cheek)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        mx.loperstack.tokkip.character.TokkipStyle.rest = when {
            s.sleepNight != today || s.sleepMin <= 0 -> mx.loperstack.tokkip.character.Rest.UNKNOWN
            s.sleepMin < 360 -> if (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) < 12) mx.loperstack.tokkip.character.Rest.TIRED else mx.loperstack.tokkip.character.Rest.OK
            s.sleepMin >= 420 -> mx.loperstack.tokkip.character.Rest.GREAT
            else -> mx.loperstack.tokkip.character.Rest.OK
        }
    }

    /** Today's mood, 0 if the last word from the phone is from another day. */
    fun todayMood(): Int {
        val s = _state.value
        val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        return if (fmt.format(java.util.Date(s.at)) == fmt.format(java.util.Date())) s.moodToday else 0
    }

    /** Breathing: pulses through each phase (true) or a buzz on each turn. */
    var breathGuide: Boolean
        get() = prefs.getBoolean("breathGuide", true)
        set(v) { prefs.edit().putBoolean("breathGuide", v).apply() }

    /** A screen to open (the mood complication asks for "mood"). */
    val openScreen = MutableStateFlow<String?>(null)

    /** The phone asked to breathe together. */
    fun breatheFromPhone(context: Context, b: Breathing) {
        val start = b.startedAt ?: return
        if (System.currentTimeMillis() - start > b.minutes * 60_000L) return
        breathRequest.value = b
        if (foreground) return
        // Not on screen: open it (or, if the system says no, a tap away).
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tokkip://breathe?at=$start&min=${b.minutes}"))
            .setClass(context, WearActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(context, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_BREATHE, "Respirar con el teléfono", NotificationManager.IMPORTANCE_HIGH))
        val n = NotificationCompat.Builder(context, CHANNEL_BREATHE)
            .setSmallIcon(mx.loperstack.tokkip.shared.R.drawable.ic_stat_tokkip)
            .setContentTitle("Respira con Tokkip")
            .setContentText("${b.minutes} min · toca para medir tu pulso")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setTimeoutAfter(b.minutes * 60_000L)
            .build()
        runCatching { nm.notify(NOTIFY_BREATHE, n) }
        runCatching { context.startActivity(intent) }
    }

    fun clearBreatheNotice(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(NOTIFY_BREATHE) }
    }

    private const val CHANNEL_BREATHE = "breathe_phone"
    private const val NOTIFY_BREATHE = 42
}

/** The phone's state and requests arrive here, app open or not. */
class WatchListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        WatchStore.init(this)
        val text = (event.data ?: ByteArray(0)).toString(Charsets.UTF_8)
        when (event.path) {
            SyncPaths.BREATHE_START -> breathingOf(text)?.let { WatchStore.breatheFromPhone(this, it) }
            SyncPaths.BREATHE_STOP -> {
                WatchStore.stopRequest.value = System.currentTimeMillis()
                WatchStore.breathRequest.value = null
                WatchStore.clearBreatheNotice(this)
            }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        WatchStore.init(this)
        for (e in events) {
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == SyncPaths.STATE) {
                DataMapItem.fromDataItem(e.dataItem).dataMap.getString("json")?.let { WatchStore.receive(it) }
            }
        }
    }
}
