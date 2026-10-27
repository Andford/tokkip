// The phone's side of the watch link (Wearable Data Layer): it publishes a
// Snapshot whenever something changes, and acts on what the watch asks —
// even with the app closed, through PhoneListenerService.

package mx.loperstack.tokkip.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.wellness.Breathing
import mx.loperstack.tokkip.wellness.EventLite
import mx.loperstack.tokkip.wellness.NightLite
import mx.loperstack.tokkip.wellness.Phase
import mx.loperstack.tokkip.wellness.PomodoroScheduler
import mx.loperstack.tokkip.wellness.Snapshot
import mx.loperstack.tokkip.wellness.SyncPaths
import mx.loperstack.tokkip.wellness.addTask
import mx.loperstack.tokkip.wellness.isPaused
import mx.loperstack.tokkip.wellness.pause
import mx.loperstack.tokkip.wellness.resume
import mx.loperstack.tokkip.wellness.sessionOf
import mx.loperstack.tokkip.wellness.startFocus
import mx.loperstack.tokkip.wellness.toJson
import mx.loperstack.tokkip.wellness.toWire
import mx.loperstack.tokkip.wellness.toggleTask

/** The watch's pulse, as it last sent it, and when. */
data class LiveHeartRate(val bpm: Int = 0, val at: Long = 0)

object WearSync {
    private val _heartRate = MutableStateFlow(LiveHeartRate())
    val heartRate: StateFlow<LiveHeartRate> = _heartRate.asStateFlow()
    private val io = Executors.newSingleThreadExecutor()

    /**
     * Breathing on the phone: the watch opens the same session (same start, so
     * they stay in step), vibrates on each turn and sends the pulse back.
     */
    fun breatheTogether(context: Context, b: Breathing) {
        val app = context.applicationContext
        _heartRate.value = LiveHeartRate()
        io.execute {
            runCatching {
                val nodes = com.google.android.gms.tasks.Tasks.await(Wearable.getNodeClient(app).connectedNodes)
                val helper = RemoteActivityHelper(app, io)
                val uri = Uri.parse("tokkip://breathe?at=${b.startedAt}&min=${b.minutes}&p=${b.inhale},${b.holdIn},${b.exhale},${b.holdOut}")
                for (n in nodes) {
                    // Opens Tokkip on the watch, even with its screen off…
                    runCatching {
                        helper.startRemoteActivity(
                            Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_BROWSABLE).setData(uri),
                            n.id,
                        )
                    }
                    // …and the message covers the case where it is already open.
                    Wearable.getMessageClient(app).sendMessage(n.id, SyncPaths.BREATHE_START, b.toWire().toByteArray())
                }
            }
        }
    }

    fun stopTogether(context: Context) {
        val app = context.applicationContext
        io.execute {
            runCatching {
                val nodes = com.google.android.gms.tasks.Tasks.await(Wearable.getNodeClient(app).connectedNodes)
                for (n in nodes) Wearable.getMessageClient(app).sendMessage(n.id, SyncPaths.BREATHE_STOP, ByteArray(0))
            }
        }
    }

    fun snapshot(context: Context, now: Long = System.currentTimeMillis()): Snapshot {
        val d = Store.state.value
        val bm = context.getSystemService(BatteryManager::class.java)
        val events = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            upcoming(Agenda.load(context, now), now).take(6).map { EventLite(it.title, it.begin, it.end, it.allDay, it.meetUrl) }
        } else emptyList()
        return Snapshot(
            tasks = d.tasks,
            tokkipColor = d.tokkipColor,
            phoneBattery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            phoneCharging = bm.isCharging,
            events = events,
            pomodoro = d.pomodoro,
            soundOn = d.soundOn,
            name = d.name,
            haptics = d.haptics,
            hapticStrength = d.hapticStrength,
            sleepMin = d.sleep.lastOrNull()?.minutes ?: 0,
            sleepNight = d.sleep.lastOrNull()?.night ?: "",
            flat = d.tokkipFlat,
            focusMin = d.pomodoroConfig.focusMin, breakMin = d.pomodoroConfig.breakMin,
            outfit = mx.loperstack.tokkip.character.Outfit.resolve(d.outfit).name,
            species = d.species, skin = d.skin, accent = d.accent, faceInk = d.faceInk, cheek = d.cheek,
            sessions = d.sessions.takeLast(7),
            nights = d.sleep.takeLast(7).map { NightLite(it.night, it.minutes, it.quality, it.bed, it.wake, it.deepMin, it.remMin, it.awakeMin) },
            buds = Headphones.levels(context).map { mx.loperstack.tokkip.wellness.BudLite(it.first, it.second) },
            moodToday = d.logs.firstOrNull { it.day == dayKey(now) }?.mood ?: 0,
            screenMin = d.screenDays.firstOrNull { it.day == dayKey(now) }?.totalMin ?: 0,
            scrollMin = d.screenDays.firstOrNull { it.day == dayKey(now) }?.scrollMin ?: 0,
            scrollGoal = d.screen.goal,
            doom = if (dayKey(d.doomAt) == dayKey(now)) d.doom else 0,
            doomTop = d.doomTop, doomSession = d.doomSession, doomAt = d.doomAt,
            at = now,
        )
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var pushApp: Context? = null
    private val pushLater = Runnable { pushApp?.let { pushNow(it) } }

    /** Publishes the state for the watch, at most every 300 ms (a colour drag sends many changes). */
    fun push(context: Context) {
        pushApp = context.applicationContext
        main.removeCallbacks(pushLater)
        main.postDelayed(pushLater, 300)
    }

    /** A phone without a watch just drops it. */
    private fun pushNow(context: Context) {
        val app = context.applicationContext
        Thread {
            runCatching {
                val req = PutDataMapRequest.create(SyncPaths.STATE).apply {
                    dataMap.putString("json", snapshot(app).toJson())
                    dataMap.putLong("t", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(app).putDataItem(req)
            }
        }.start()
    }

    /** What the watch asked for. */
    fun handle(context: Context, path: String, data: ByteArray) {
        Store.init(context)
        val text = data.toString(Charsets.UTF_8)
        when (path) {
            SyncPaths.TOGGLE_TASK -> TaskSync.toggle(context, text)
            SyncPaths.ADD_TASK -> TaskSync.add(context, text, "-w")
            SyncPaths.MOOD -> text.trim().toIntOrNull()?.takeIf { it in 1..5 }?.let {
                val now = System.currentTimeMillis()
                Store.setLogs(addMood(Store.state.value.logs, dayKey(now), now, it))
            }
            SyncPaths.SESSION -> sessionOf(text)?.let { Store.addSession(it) }
            SyncPaths.POMODORO -> {
                val now = System.currentTimeMillis()
                val p = Store.state.value.pomodoro
                val next = when {
                    p.phase == Phase.IDLE -> p.startFocus(now)
                    p.isPaused() -> p.resume(now)
                    else -> p.pause(now)
                }
                Store.setPomodoro(next)
                PomodoroScheduler.sync(context, next, now)
            }
            SyncPaths.HEART_RATE -> {
                text.trim().toIntOrNull()?.let { _heartRate.value = LiveHeartRate(it, System.currentTimeMillis()) }
                return
            }
            SyncPaths.REQUEST -> Unit
        }
        push(context)
    }
}

/** Messages from the watch arrive here, app open or not. */
class PhoneListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        Sounds.init(this)
        WearSync.handle(this, event.path, event.data ?: ByteArray(0))
    }
}
