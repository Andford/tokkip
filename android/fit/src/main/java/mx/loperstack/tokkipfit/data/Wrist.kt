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
        val done = runCatching { Done.of(JSONObject(String(event.data))) }.getOrNull() ?: return
        Prefs.init(this)
        if (Prefs.workouts().any { it.at == done.at && it.routine == done.routine }) return
        Prefs.addWorkout(done)
        runBlocking { runCatching { HealthData.write(this@FitWristService, done) } }
    }
}
