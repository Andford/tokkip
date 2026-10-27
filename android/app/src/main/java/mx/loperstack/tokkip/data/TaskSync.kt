// Every change to the tasks goes through here — from the app, the watch and
// the widget — so with Google Tasks connected each one also reaches Google
// (and from there the PC). The phone shows the change at once; Google's calls
// run one at a time, in order, in the background.

package mx.loperstack.tokkip.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mx.loperstack.tokkip.wellness.addTask
import mx.loperstack.tokkip.wellness.clearDone
import mx.loperstack.tokkip.wellness.removeTask
import mx.loperstack.tokkip.wellness.toggleTask

object TaskSync {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val queue = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Runs a Google call in order; any failure shows in Settings and the phone keeps its copy. */
    private fun remote(context: Context, block: (token: String) -> Unit) {
        if (!GoogleTasks.connected) return
        val app = context.applicationContext
        queue.launch {
            GoogleTasks.setBusy(true)
            try {
                val token = GoogleTasks.token(app)
                if (token == null) {
                    GoogleTasks.setError("Vuelve a conectar Google Tasks.")
                    return@launch
                }
                block(token)
                GoogleTasks.synced()
            } catch (e: Exception) {
                GoogleTasks.setError(GoogleTasks.describe(e))
            }
        }
    }

    /** Adds a task; false if the text was empty. */
    fun add(context: Context, text: String, idSuffix: String = ""): Boolean {
        Store.init(context)
        val now = System.currentTimeMillis()
        val localId = "${now.toString(36)}-${(0..9999).random()}$idSuffix"
        val before = Store.state.value.tasks
        val after = addTask(before, text, now, localId)
        if (after.size == before.size) return false
        Store.setTasks(after)
        val clean = after.last().text
        remote(context) { token ->
            val g = GoogleTasks.add(token, clean) ?: return@remote
            Store.setTasks(Store.state.value.tasks.map { if (it.id == localId) it.copy(id = g.id, remote = true) else it })
        }
        return true
    }

    fun toggle(context: Context, id: String) {
        Store.init(context)
        val was = Store.state.value.tasks.firstOrNull { it.id == id } ?: return
        Store.setTasks(toggleTask(Store.state.value.tasks, id))
        if (was.remote) remote(context) { GoogleTasks.setDone(it, id, !was.done) }
    }

    fun remove(context: Context, id: String) {
        Store.init(context)
        val was = Store.state.value.tasks.firstOrNull { it.id == id } ?: return
        Store.setTasks(removeTask(Store.state.value.tasks, id))
        if (was.remote) remote(context) { GoogleTasks.delete(it, id) }
    }

    fun clearDone(context: Context) {
        Store.init(context)
        val any = Store.state.value.tasks.any { it.remote && it.done }
        Store.setTasks(clearDone(Store.state.value.tasks))
        if (any) remote(context) { GoogleTasks.clearDone(it) }
    }

    /**
     * Google's list becomes the phone's. Tasks made here that never reached
     * Google (offline, or from before connecting) go up first, as the PC does.
     */
    fun refresh(context: Context) {
        Store.init(context)
        remote(context) { token ->
            for (t in Store.state.value.tasks.filter { !it.remote && !it.done }) {
                val g = GoogleTasks.add(token, t.text) ?: continue
                Store.setTasks(Store.state.value.tasks.map { if (it.id == t.id) it.copy(id = g.id, remote = true) else it })
            }
            val list = fromGoogle(GoogleTasks.list(token))
            val local = Store.state.value.tasks.filter { !it.remote }
            Store.setTasks(list + local)
        }
    }
}
