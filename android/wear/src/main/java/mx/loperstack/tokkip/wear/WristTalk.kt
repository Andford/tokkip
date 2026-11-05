// Talking to Tokkip from the wrist: the watch hears you (the system's dictation),
// the phone thinks (Gemma, in Tokkip's Platicar) and sends her answer back as it
// grows, with her voice in small pieces when it should sound here
// (shared/…/TalkWire.kt). If the phone has earbuds on, she speaks there instead.

package mx.loperstack.tokkip.wear

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mx.loperstack.tokkip.wellness.SyncPaths
import mx.loperstack.tokkip.wellness.TalkWire
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object WristTalk {
    data class State(
        val heard: String = "",
        val answer: String = "",
        val busy: Boolean = false,
        val error: Boolean = false,
        val crisis: Boolean = false,
        val where: String = "",
    )

    private val _state = MutableStateFlow(State())
    /** Her voice is welcome from a question until you stop her. */
    @Volatile private var hearing = false
    val state: StateFlow<State> = _state.asStateFlow()

    fun ask(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        Player.stop()
        _state.value = State(heard = t, busy = true)
        hearing = true
        WatchStore.send(SyncPaths.TALK_ASK, t)
    }

    fun stop() {
        hearing = false
        WatchStore.send(SyncPaths.TALK_STOP, "")
        Player.stop()
        _state.update { it.copy(busy = false) }
    }

    fun onText(json: String) {
        val r = TalkWire.replyOf(json) ?: return
        _state.update {
            if (!it.busy && r.done.not()) it // a late piece after "stop"
            else it.copy(
                answer = r.text.ifEmpty { it.answer }, busy = !r.done, error = r.error,
                crisis = it.crisis || r.crisis, where = r.where.ifEmpty { it.where },
            )
        }
    }

    fun onAudio(bytes: ByteArray) {
        if (!hearing) return
        TalkWire.audioOf(bytes)?.let { Player.play(it.pcm, it.rate, it.pitch) }
    }

    /** Her voice on the watch's speaker, piece after piece, in the order they came. */
    private object Player {
        private val worker = Executors.newSingleThreadExecutor()
        private val turn = AtomicInteger()
        @Volatile private var track: AudioTrack? = null
        private var playing = -1

        fun play(pcm: ShortArray, rate: Int, pitch: Float) {
            val mine = turn.get()
            worker.execute {
                if (mine != turn.get()) return@execute
                val t = track?.takeIf { it.sampleRate == rate } ?: run {
                    track?.release()
                    AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4)
                        .build().also { track = it }
                }
                if (playing != mine) runCatching { t.pause(); t.flush() } // what was left of the last answer
                playing = mine
                runCatching { t.playbackParams = PlaybackParams().setPitch(pitch).setSpeed(1f) }
                runCatching {
                    t.play()
                    t.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                }
            }
        }

        fun stop() {
            turn.incrementAndGet()
            runCatching { track?.pause(); track?.flush() }
        }
    }
}
