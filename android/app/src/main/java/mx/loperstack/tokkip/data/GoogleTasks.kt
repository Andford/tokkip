// Google Tasks on the phone — the same list the PC's Bienestar pill uses
// (@default, windows/src-tauri/src/google.rs), so a task made on one shows on
// the other. Sign-in is Google Play services' own authorization (no password,
// no client secret in the app): it needs an "Android" OAuth client in your
// Google Cloud project with this app's package and signing SHA-1 (GOOGLE.md).

package mx.loperstack.tokkip.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.loperstack.tokkip.wellness.Task
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One task as Google has it. */
data class GTask(val id: String, val title: String, val done: Boolean, val position: String)

/** Google's JSON → a task, or null for deleted / empty ones (google.rs parse_task). */
fun gTaskOf(o: JSONObject?): GTask? {
    if (o == null || o.optBoolean("deleted")) return null
    val title = o.optString("title", "").trim()
    val id = o.optString("id", "")
    if (title.isEmpty() || id.isEmpty()) return null
    return GTask(id, title, o.optString("status") == "completed", o.optString("position", ""))
}

/** Google's tasks as Tokkip's: in Google's order, marked remote. */
fun fromGoogle(list: List<GTask>): List<Task> =
    list.sortedBy { it.position }.mapIndexed { i, g -> Task(g.id, g.title, g.done, i.toLong(), remote = true) }

object GoogleTasks {
    private const val SCOPE = "https://www.googleapis.com/auth/tasks"
    private const val LIST = "https://tasks.googleapis.com/tasks/v1/lists/@default"

    data class Status(val connected: Boolean = false, val busy: Boolean = false, val error: String = "", val lastSync: Long = 0)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()
    val connected get() = _status.value.connected

    fun init(context: Context) {
        val on = context.getSharedPreferences(Store.PREFS, Context.MODE_PRIVATE).getBoolean("googleTasks", false)
        if (on != _status.value.connected) _status.value = _status.value.copy(connected = on)
    }

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    private fun setConnected(context: Context, on: Boolean) {
        context.getSharedPreferences(Store.PREFS, Context.MODE_PRIVATE).edit().putBoolean("googleTasks", on).apply()
        _status.value = _status.value.copy(connected = on, error = "")
    }

    internal fun setBusy(busy: Boolean) { _status.value = _status.value.copy(busy = busy) }
    internal fun setError(msg: String) { _status.value = _status.value.copy(error = msg, busy = false) }
    internal fun synced() { _status.value = _status.value.copy(error = "", busy = false, lastSync = System.currentTimeMillis()) }

    fun describe(e: Throwable): String = when ((e as? ApiException)?.statusCode) {
        10 -> "Falta el cliente OAuth de Android en Google Cloud (paquete y SHA-1). Mira android/GOOGLE.md."
        16, 12501 -> "Cancelado."
        7 -> "Sin conexión."
        else -> e.message?.take(140) ?: "No se pudo conectar."
    }

    /** "Conectar Google Tasks": Google's consent screen, then the first sync. */
    fun connect(activity: Activity, launch: (IntentSenderRequest) -> Unit) {
        _status.value = _status.value.copy(busy = true, error = "")
        Identity.getAuthorizationClient(activity).authorize(request())
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) launch(IntentSenderRequest.Builder(pi.intentSender).build())
                else granted(activity)
            }
            .addOnFailureListener { setError(describe(it)) }
    }

    /** What the consent screen answered. */
    fun onResult(activity: Activity, data: Intent?) {
        runCatching { Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data) }
            .onSuccess { if (it.accessToken != null) granted(activity) else setError("Google no dio acceso.") }
            .onFailure { setError(describe(it)) }
    }

    private fun granted(context: Context) {
        setConnected(context, true)
        TaskSync.refresh(context)
    }

    fun disconnect(context: Context) {
        setConnected(context, false)
        // The tasks stay on the phone, now as local ones.
        Store.setTasks(Store.state.value.tasks.map { it.copy(remote = false) })
    }

    /** A fresh token without any screen, or null if Google wants the consent again. Blocking: call off the main thread. */
    fun token(context: Context): String? {
        val r = Tasks.await(Identity.getAuthorizationClient(context.applicationContext).authorize(request()))
        return if (r.hasResolution()) null else r.accessToken
    }

    // ── REST (google.rs, the same calls) ──────────────────────────────────────

    private fun call(token: String, method: String, url: String, body: JSONObject? = null): JSONObject? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            // HttpURLConnection has no PATCH; Google takes the override header.
            c.requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            c.setRequestProperty("Authorization", "Bearer $token")
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            if (code == 401) throw IllegalStateException("Google pidió iniciar sesión otra vez.")
            if (code !in 200..299) throw IllegalStateException("Google respondió $code.")
            val text = c.inputStream.bufferedReader().use { it.readText() }
            return if (text.isBlank()) null else JSONObject(text)
        } finally {
            c.disconnect()
        }
    }

    fun list(token: String): List<GTask> {
        val o = call(token, "GET", "$LIST/tasks?showCompleted=true&showHidden=false&maxResults=100")
        val items = o?.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { gTaskOf(items.optJSONObject(it)) }
    }

    fun add(token: String, title: String): GTask? =
        gTaskOf(call(token, "POST", "$LIST/tasks", JSONObject().put("title", title.trim().take(200))))

    private fun taskUrl(id: String): String {
        require(id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "bad id" }
        return "$LIST/tasks/$id"
    }

    fun setDone(token: String, id: String, done: Boolean) {
        val body = if (done) JSONObject().put("status", "completed")
        else JSONObject().put("status", "needsAction").put("completed", JSONObject.NULL)
        call(token, "PATCH", taskUrl(id), body)
    }

    fun delete(token: String, id: String) { call(token, "DELETE", taskUrl(id)) }

    fun clearDone(token: String) { call(token, "POST", "$LIST/clear") }
}
