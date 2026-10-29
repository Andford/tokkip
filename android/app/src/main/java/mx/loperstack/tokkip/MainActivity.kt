package mx.loperstack.tokkip

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.data.GoogleTasks
import mx.loperstack.tokkip.data.TaskSync
import mx.loperstack.tokkip.data.WearSync
import mx.loperstack.tokkip.character.Sounds
import mx.loperstack.tokkip.ui.TokkipApp
import mx.loperstack.tokkip.ui.Tab
import mx.loperstack.tokkip.wellness.PomodoroScheduler
import mx.loperstack.tokkip.wellness.isRunning
import mx.loperstack.tokkip.wellness.tick

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TAB = "tab"
        /** From the Respirar widget: start a session of this many minutes. */
        const val EXTRA_BREATHE_MIN = "breatheMin"
        /** From the night notification: open the diary's composer. */
        const val EXTRA_COMPOSE = "compose"
        /** From the Dinero widget: open "Anotar", with this category picked ("" = none). */
        const val EXTRA_MONEY_ADD = "moneyAdd"
    }

    private val startTab = mutableStateOf(Tab.HOME)
    /** minutes to breathe, and a nonce so the same button works twice. */
    private val breatheNow = mutableStateOf(0 to 0L)
    private val composeNow = mutableStateOf(0L)
    private val moneyAdd = mutableStateOf("" to 0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        mx.loperstack.tokkip.data.AppIcon.apply(this, Store.state.value.species)
        GoogleTasks.init(this)
        Sounds.init(this)
        Sounds.enabled = Store.state.value.soundOn
        // Every change already reaches the watch and the widgets (Store).
        Store.onChange = {
            Sounds.enabled = Store.state.value.soundOn
            mx.loperstack.tokkip.character.Haptics.enabled = Store.state.value.haptics
        Store.applyLook()
        }
        mx.loperstack.tokkip.character.Haptics.enabled = Store.state.value.haptics
        mx.loperstack.tokkip.character.Haptics.strength = Store.state.value.hapticStrength
        PomodoroScheduler.channels(this)
        mx.loperstack.tokkip.wellness.BedReminder.sync(this)
        mx.loperstack.tokkip.wellness.WakeAlarm.sync(this)
        catchUp()
        startTab.value = tabOf(intent)
        breatheNow.value = breatheOf(intent)
        composeNow.value = composeOf(intent)
        moneyAdd.value = moneyOf(intent)
        mx.loperstack.tokkip.wellness.TokkipNotifier.channels(this)
        mx.loperstack.tokkip.wellness.TokkipNotifier.sync(this)
        setContent { TokkipApp(startTab.value, breatheNow.value, composeNow.value, moneyAdd.value) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startTab.value = tabOf(intent)
        breatheNow.value = breatheOf(intent)
        composeNow.value = composeOf(intent)
        moneyAdd.value = moneyOf(intent)
    }

    private fun moneyOf(intent: Intent?): Pair<String, Long> =
        intent?.getStringExtra(EXTRA_MONEY_ADD)?.let { it to System.nanoTime() } ?: ("" to 0L)

    private fun composeOf(intent: Intent?) = if (intent?.getBooleanExtra(EXTRA_COMPOSE, false) == true) System.nanoTime() else 0L

    override fun onResume() {
        super.onResume()
        catchUp()
        Store.applyLook()
        WearSync.push(this)
        mx.loperstack.tokkip.wellness.TokkipNotifier.sync(this)
        // A restored backup or a change of clock: the next alarm, again.
        mx.loperstack.tokkip.wellness.WakeAlarm.sync(this)
        // Tasks changed on the PC or in Google: bring them in.
        if (GoogleTasks.connected) TaskSync.refresh(this)
    }

    /** A phase that ended while the alarm could not run: move on, quietly. */
    private fun catchUp() {
        val now = System.currentTimeMillis()
        val p = Store.state.value.pomodoro
        if (p.isRunning() && (p.endsAt ?: 0) <= now) {
            val (next, _) = p.tick(now)
            Store.setPomodoro(next)
            PomodoroScheduler.sync(this, next, now)
        }
    }

    private fun breatheOf(intent: Intent?): Pair<Int, Long> {
        val m = intent?.getIntExtra(EXTRA_BREATHE_MIN, 0) ?: 0
        return if (m > 0) m to System.nanoTime() else 0 to 0L
    }

    private fun tabOf(intent: Intent?): Tab = when (intent?.getStringExtra(EXTRA_TAB)) {
        "focus" -> Tab.FOCUS
        "breathe" -> Tab.BREATHE
        "tasks" -> Tab.TASKS
        "diary" -> Tab.DIARY
        "agenda" -> Tab.AGENDA
        "screen" -> Tab.SCREEN
        "sleep" -> Tab.SLEEP
        "streaks" -> Tab.STREAKS
        "money" -> Tab.MONEY
        "pet" -> Tab.PET
        else -> Tab.HOME
    }
}
