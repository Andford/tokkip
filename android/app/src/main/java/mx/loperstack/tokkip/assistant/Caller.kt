package mx.loperstack.tokkip.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Calls Tokkip places for you. A call waits a few seconds first, with a
 * "Cancelar" on screen, so a misheard name never rings anyone by surprise.
 */
object Caller {
    data class Pending(val name: String, val number: String, val at: Long)

    const val WAIT_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()
    private val _placed = MutableSharedFlow<Pending>(extraBufferCapacity = 4)
    /** A call just went out: the chat stops listening and talking. */
    val placed: SharedFlow<Pending> = _placed

    fun schedule(context: Context, name: String, number: String) {
        val app = context.applicationContext
        val p = Pending(name, number, System.currentTimeMillis() + WAIT_MS)
        job?.cancel()
        _pending.value = p
        job = scope.launch {
            delay(WAIT_MS)
            place(app, p)
        }
    }

    fun now(context: Context) {
        val p = _pending.value ?: return
        job?.cancel()
        place(context.applicationContext, p)
    }

    fun cancel() {
        job?.cancel()
        job = null
        _pending.value = null
    }

    private fun place(context: Context, p: Pending) {
        _pending.value = null
        Voice.stop()
        Brain.stop()
        val uri = Uri.fromParts("tel", p.number, null)
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED &&
            runCatching { context.getSystemService(TelecomManager::class.java).placeCall(uri, Bundle()) }.isSuccess
        // Without the permission: the dialer with the number, one tap away.
        if (!ok) runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        _placed.tryEmit(p)
    }
}
