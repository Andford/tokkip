// Fit's phone and watch: the phone sends Tokkip's look to the watch (so he's
// the same on both), and the watch sends each finished workout to the phone,
// which keeps it and writes it to Health Connect (the watch has no Health
// Connect of its own).

package mx.loperstack.tokkipfit.data

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import mx.loperstack.tokkip.character.Look
import mx.loperstack.tokkip.fit.Done
import mx.loperstack.tokkip.fit.FIT_DONE_PATH
import org.json.JSONObject

object Wrist {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var pending: Runnable? = null

    /** Sends the look to the watch, once things settle (a colour being dragged sends only the last). */
    fun pushLook(context: Context, look: Look) {
        pending?.let { main.removeCallbacks(it) }
        val app = context.applicationContext
        pending = Runnable { send(app, look) }.also { main.postDelayed(it, 700) }
    }

    /** Hoy's numbers for the watch's tile and complication. */
    fun pushToday(context: Context, json: String) {
        runCatching {
            val req = PutDataMapRequest.create(Snapshot.PATH).apply { dataMap.putString("json", json) }.asPutDataRequest()
            Wearable.getDataClient(context.applicationContext).putDataItem(req)
        }
    }

    /** Your own routines and plans, and your last weights, so the watch can play and suggest them. */
    fun pushMine(context: Context) {
        runCatching {
            val req = PutDataMapRequest.create(mx.loperstack.tokkip.fit.FIT_MINE_PATH).apply {
                dataMap.putString("json", mx.loperstack.tokkip.fit.MyRoutine.json(Prefs.myRoutines))
                dataMap.putString("plans", mx.loperstack.tokkip.fit.MyPlans.json(Prefs.myPlans))
                dataMap.putString("last", mx.loperstack.tokkip.fit.GymSet.json(mx.loperstack.tokkip.fit.lastSets(Prefs.gymSets())))
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context.applicationContext).putDataItem(req)
        }
    }

    private fun send(context: Context, look: Look) {
        runCatching {
            val req = PutDataMapRequest.create(Look.FIT_LOOK_PATH).apply {
                dataMap.putString("json", look.json())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req)
        }
    }
}

/** A workout finished on the watch. */
class FitWristService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != FIT_DONE_PATH) return
        val o = runCatching { JSONObject(String(event.data)) }.getOrNull() ?: return
        val done = runCatching { Done.of(o) }.getOrNull() ?: return
        // Cardio from the watch brings its route: [[lat, lon, t]…].
        val route = runCatching {
            val a = o.optJSONArray("route") ?: return@runCatching emptyList()
            (0 until a.length()).map { a.getJSONArray(it).let { p -> mx.loperstack.tokkip.fit.Point(p.getDouble(0), p.getDouble(1), p.getLong(2)) } }
        }.getOrDefault(emptyList())
        Prefs.init(this)
        if (Prefs.workouts().any { it.at == done.at && it.routine == done.routine }) return
        Prefs.addWorkout(done)
        if (route.isNotEmpty()) Prefs.saveRoute(done.at, route)
        // Gym from the watch brings its sets: they count for your records like the phone's.
        val sets = mx.loperstack.tokkip.fit.GymSet.list(o.optJSONArray("sets")?.toString())
        for (s in sets) {
            if (mx.loperstack.tokkip.fit.isRecord(s, Prefs.gymSets())) Prefs.recordsBeaten = Prefs.recordsBeaten + 1
            Prefs.addGymSet(s)
        }
        runBlocking { runCatching { HealthData.write(this@FitWristService, done, route) } }
    }
}
