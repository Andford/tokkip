package mx.loperstack.tokkip.assistant

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.loperstack.tokkip.data.Store
import mx.loperstack.tokkip.wellness.SyncPaths
import mx.loperstack.tokkip.wellness.TalkReply
import mx.loperstack.tokkip.wellness.TalkWire

/**
 * Talking to Tokkip from the watch. The watch hears you and sends the words;
 * here Gemma thinks, the tools do what you asked, and her answer goes back to
 * the wrist as it grows. Her voice sounds in your earbuds when the phone has
 * some connected, and on the watch otherwise. Same memory as the Platicar tab.
 */
object WatchTalk {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var idle: Job? = null

    /** True while the Platicar tab is open: then it looks after the model. */
    @Volatile var chatOpen = false

    /** After this long without a question from the wrist, the model leaves memory. */
    private const val IDLE_MS = 3 * 60_000L

    fun handle(context: Context, node: String, path: String, data: ByteArray) {
        val app = context.applicationContext
        when (path) {
            SyncPaths.TALK_STOP -> stop()
            SyncPaths.TALK_ASK -> ask(app, node, data.toString(Charsets.UTF_8).trim().take(2000))
        }
    }

    private fun stop() {
        Caller.cancel()
        job?.cancel()
        Brain.stop()
        Voice.stop()
    }

    private fun ask(app: Context, node: String, text: String) {
        if (text.isEmpty()) return
        stop()
        idle?.cancel()
        job = scope.launch {
            Store.init(app)
            val model = Models.installed(app)
            if (model == null) {
                reply(app, node, TalkReply("Primero instala mi modelo en Platicar, en tu teléfono.", done = true, error = true))
                return@launch
            }
            val where = if (earbuds(app)) "buds" else "watch"
            reply(app, node, TalkReply("", done = false, where = where))
            val history = Memory.load(app)
            Brain.load(app, model, Store.state.value.name, history)
            if (Brain.state.value !is Brain.State.Ready) {
                reply(app, node, TalkReply("No pude despertar. Ábreme en el teléfono.", done = true, error = true))
                return@launch
            }
            val acts = mutableListOf<String>()
            val heard = launch(start = CoroutineStart.UNDISPATCHED) { Brain.done.collect { acts += it } }
            val sink = if (where == "watch") Voice.Sink { s, rate, pitch -> sendAudio(app, node, s, rate, pitch) } else null
            val cut = Sentences()
            val out = StringBuilder()
            var sentAt = 0L
            try {
                Brain.ask(text).collect { piece ->
                    out.append(piece)
                    val shown = Persona.clean(out.toString())
                    for (s in cut.feed(shown, done = false)) Voice.say(app, s, sink)
                    val now = System.currentTimeMillis()
                    if (now - sentAt > 700) {
                        sentAt = now
                        reply(app, node, TalkReply(withActs(acts, shown), done = false, where = where))
                    }
                }
            } catch (e: CancellationException) {
                heard.cancel()
                throw e
            } catch (e: Exception) {
                if (out.isEmpty()) out.append("Uy, me trabé pensando. ¿Me lo repites?")
            }
            heard.cancel()
            val final = Persona.clean(out.toString()).trim()
            for (s in cut.feed(final, done = true)) Voice.say(app, s, sink)
            val said = withActs(acts, final)
            reply(app, node, TalkReply(said, done = true, crisis = Persona.isCrisis(text), where = where))
            Memory.save(app, history + Line(true, text) + Line(false, said))
            rest()
        }
    }

    private fun withActs(acts: List<String>, text: String): String =
        if (acts.isEmpty()) text else (acts.joinToString("\n") + "\n\n" + text).trim()

    /** Lets the model go a while after the last question, unless the chat on the phone is open. */
    private fun rest() {
        idle?.cancel()
        idle = scope.launch {
            delay(IDLE_MS)
            if (!chatOpen) {
                Brain.unload()
                Voice.release()
            }
        }
    }

    private fun earbuds(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        val kinds = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in kinds }
    }

    private fun reply(context: Context, node: String, r: TalkReply) {
        runCatching {
            Tasks.await(Wearable.getMessageClient(context).sendMessage(node, SyncPaths.TALK_TEXT, TalkWire.text(r).toByteArray(Charsets.UTF_8)))
        }
    }

    private fun sendAudio(context: Context, node: String, samples: FloatArray, rate: Int, pitch: Float) {
        val client = Wearable.getMessageClient(context)
        for (piece in TalkWire.audio(samples, rate, pitch)) {
            runCatching { Tasks.await(client.sendMessage(node, SyncPaths.TALK_AUDIO, piece)) }
        }
    }
}
