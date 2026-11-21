// How the phone is doing, as Tokkip Health (Andford/tokkiphealth, android/)
// tells it: one small note, sent as an explicit broadcast whenever the phone's
// mood changes or after a clean. The same contract as Healthy PC's
// healthy-pc.json, which Tokkip PC reads: seq, at, score, mood, kind, message
// and, after a clean, bytes. Kept here, shown on Inicio ("Tu teléfono").

package mx.loperstack.tokkip.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

data class PhoneNote(
    val seq: Long,
    val at: Long,
    val score: Int,
    /** happy, calm, tired or dizzy. */
    val mood: String,
    /** clean or status. */
    val kind: String,
    /** What Tokkip says, already in Spanish. */
    val message: String,
    val bytes: Long? = null,
)

object PhoneHealth {
    const val TOKKIP_HEALTH = "mx.loperstack.tokkiphealth"
    const val ACTION = "mx.loperstack.tokkip.action.PHONE_HEALTH"
    const val EXTRA_JSON = "json"
    private const val PREFS = "phone_health"
    private val MOODS = setOf("happy", "calm", "tired", "dizzy")

    private val _note = MutableStateFlow<PhoneNote?>(null)
    val note: StateFlow<PhoneNote?> = _note

    /** The note made safe, or null when it isn't one. Pure, so it's tested without a phone. */
    fun parse(raw: String?): PhoneNote? {
        if (raw.isNullOrBlank() || raw.length > 4096) return null
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (o.optString("app") != "tokkip-health") return null
        val seq = o.optLong("seq", -1)
        val at = o.optLong("at", 0)
        val score = o.optInt("score", -1)
        val mood = o.optString("mood")
        val kind = o.optString("kind")
        val message = o.optString("message").replace(Regex("\\s+"), " ").trim().take(200)
        if (seq < 0 || at <= 0 || score !in 0..100 || mood !in MOODS || kind !in setOf("clean", "status") || message.isEmpty()) return null
        val bytes = if (o.has("bytes")) o.optLong("bytes", -1).takeIf { it >= 0 } else null
        return PhoneNote(seq, at, score, mood, kind, message, bytes)
    }

    fun load(context: Context) {
        if (_note.value == null) _note.value = parse(prefs(context).getString("note", null))
    }

    fun receive(context: Context, raw: String?) {
        val n = parse(raw) ?: return
        val p = prefs(context)
        val last = parse(p.getString("note", null))
        if (last != null && n.seq <= last.seq && n.at <= last.at) return
        p.edit().putString("note", raw).apply()
        _note.value = n
    }

    /** Opens Tokkip Health, when it's installed. */
    fun open(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(TOKKIP_HEALTH) ?: return false
        return runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Tokkip Health tells how the phone feels. */
class PhoneHealthReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PhoneHealth.ACTION) return
        PhoneHealth.receive(context, intent.getStringExtra(PhoneHealth.EXTRA_JSON))
    }
}
