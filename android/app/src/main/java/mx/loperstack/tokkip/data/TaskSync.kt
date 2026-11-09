// Every change to the tasks goes through here — from the app, the watch, the
// widget and Tokkip's voice — so they all change the same list the same way.

package mx.loperstack.tokkip.data

import android.content.Context
import mx.loperstack.tokkip.wellness.addTask
import mx.loperstack.tokkip.wellness.clearDone
import mx.loperstack.tokkip.wellness.removeTask
import mx.loperstack.tokkip.wellness.toggleTask

object TaskSync {
    /** Adds a task; false if the text was empty. */
    fun add(context: Context, text: String, idSuffix: String = ""): Boolean {
        Store.init(context)
        val now = System.currentTimeMillis()
        val localId = "${now.toString(36)}-${(0..9999).random()}$idSuffix"
        val before = Store.state.value.tasks
        val after = addTask(before, text, now, localId)
        if (after.size == before.size) return false
        Store.setTasks(after)
        return true
    }

    fun toggle(context: Context, id: String) {
        Store.init(context)
        Store.setTasks(toggleTask(Store.state.value.tasks, id))
    }

    fun remove(context: Context, id: String) {
        Store.init(context)
        Store.setTasks(removeTask(Store.state.value.tasks, id))
    }

    fun clearDone(context: Context) {
        Store.init(context)
        Store.setTasks(clearDone(Store.state.value.tasks))
    }
}
