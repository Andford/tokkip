package mx.loperstack.tokkip.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A Mexican Spanish Piper voice, packed for sherpa-onnx. */
data class VoiceSpec(val id: String, val name: String, val note: String) {
    val url get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-$id.tar.bz2"
    val folder get() = "vits-piper-$id"
}

/**
 * Tokkip's own voice, made on the phone with Piper (through sherpa-onnx): no
 * internet, the same on every phone. The voice (about 65 MB) comes once when
 * you ask. He speaks sentence by sentence while the answer is still coming,
 * a little higher than the voice's own pitch so it sounds like him.
 */
object Voice {
    val VOICES = listOf(
        VoiceSpec("es_MX-claude-high", "Claude (México)", "Clara y natural"),
        VoiceSpec("es_MX-ald-medium", "Ald (México)", "Más grave"),
    )

    private const val PREFS = "tokkip_asistente"

    fun chosen(context: Context): VoiceSpec {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("voz", VOICES.first().id)
        return VOICES.firstOrNull { it.id == id } ?: VOICES.first()
    }

    fun choose(context: Context, v: VoiceSpec) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("voz", v.id).apply()
        release()
    }

    /** Talk out loud (on by default once a voice is here). */
    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("hablar", true)
    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("hablar", on).apply()
        if (!on) stop()
    }

    /** Tokkip's pitch on top of the voice: 1 is the voice as it is. */
    fun pitch(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat("tono", 1.12f)
    fun setPitch(context: Context, p: Float) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("tono", p.coerceIn(0.8f, 1.4f)).apply()

    private fun root(context: Context) = File(context.filesDir, "voz").apply { mkdirs() }
    fun dirOf(context: Context, v: VoiceSpec) = File(root(context), v.folder)
    fun installed(context: Context, v: VoiceSpec) = File(dirOf(context, v), "${v.id}.onnx").exists() && File(dirOf(context, v), "espeak-ng-data").isDirectory

    /** Downloads and unpacks a voice. [onProgress] goes 0..1. */
    suspend fun download(context: Context, v: VoiceSpec, onProgress: (Float) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val root = root(context)
            val archive = File(root, "${v.folder}.tar.bz2.part")
            var url = URL(v.url)
            var conn: HttpURLConnection
            var hops = 0
            while (true) {
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                val code = conn.responseCode
                if (code in 300..399 && hops++ < 5) {
                    url = URL(url, conn.getHeaderField("Location"))
                    conn.disconnect()
                    continue
                }
                check(code == 200) { "La descarga respondió $code" }
                break
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                archive.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(0.9f * done / total)
                    }
                }
            }
            val target = dirOf(context, v)
            target.deleteRecursively()
            TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    val out = File(root, entry.name).canonicalFile
                    // Only inside the voice folder.
                    check(out.path.startsWith(root.canonicalPath + File.separator)) { "Archivo de voz inesperado" }
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { tar.copyTo(it) }
                    }
                }
            }
            archive.delete()
            onProgress(1f)
            check(installed(context, v)) { "La voz llegó incompleta" }
        }
    }

    fun delete(context: Context, v: VoiceSpec) {
        release()
        dirOf(context, v).deleteRecursively()
    }

    // ── Speaking ──────────────────────────────────────────────────────────────

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tts: OfflineTts? = null
    private var ttsId: String? = null
    private var queue = Channel<String>(Channel.UNLIMITED)
    private var worker: Job? = null
    private var track: AudioTrack? = null

    private fun engine(context: Context, v: VoiceSpec): OfflineTts? {
        if (tts != null && ttsId == v.id) return tts
        release()
        if (!installed(context, v)) return null
        val d = dirOf(context, v)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(d, "${v.id}.onnx").path,
                    tokens = File(d, "tokens.txt").path,
                    dataDir = File(d, "espeak-ng-data").path,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        )
        tts = runCatching { OfflineTts(config = config) }.getOrNull()
        ttsId = v.id
        return tts
    }

    /** Says [text] after whatever is already being said. */
    fun say(context: Context, text: String) {
        val clean = speakable(text)
        if (clean.isBlank() || !enabled(context)) return
        val app = context.applicationContext
        queue.trySend(clean)
        if (worker?.isActive != true) worker = scope.launch {
            val v = chosen(app)
            val engine = engine(app, v) ?: return@launch
            for (sentence in queue) {
                _speaking.value = true
                val audio = runCatching { engine.generate(sentence, 0, 1.0f) }.getOrNull() ?: continue
                play(audio.samples, audio.sampleRate, pitch(app))
                if (queue.isEmpty) _speaking.value = false
            }
        }
    }

    private fun play(samples: FloatArray, rate: Int, pitch: Float) {
        val t = track?.takeIf { it.sampleRate == rate } ?: run {
            track?.release()
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 4)
                .build().also { track = it }
        }
        runCatching { t.playbackParams = PlaybackParams().setPitch(pitch).setSpeed(1f) }
        t.play()
        t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
    }

    /** Stops talking now and drops what was waiting. */
    fun stop() {
        worker?.cancel()
        worker = null
        queue.close()
        queue = Channel(Channel.UNLIMITED)
        runCatching { track?.pause(); track?.flush() }
        _speaking.value = false
    }

    /** Lets the voice go from memory (when you leave the chat). */
    fun release() {
        stop()
        runCatching { track?.release() }
        track = null
        runCatching { tts?.release() }
        tts = null
        ttsId = null
    }

    /** Markdown and code read badly out loud: say the words, skip the symbols and the commands. */
    fun speakable(text: String): String = text
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("`[^`]*`"), " ")
        .replace(Regex("[*_#>|]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/**
 * Cuts a streaming answer into sentences, so Tokkip can start talking before
 * the model is done. Feed it the text as it grows; it hands back each finished
 * sentence once.
 */
class Sentences {
    private var said = 0

    fun feed(full: String, done: Boolean): List<String> {
        val out = mutableListOf<String>()
        var start = said
        var i = said
        var inCode = false
        while (i < full.length) {
            if (full.startsWith("```", i)) inCode = !inCode
            val c = full[i]
            val end = !inCode && (c == '.' || c == '!' || c == '?' || c == '\n' || c == ':') &&
                (i + 1 >= full.length || full[i + 1].isWhitespace())
            if (end && i + 1 - start >= 8) {
                out += full.substring(start, i + 1).trim()
                start = i + 1
            }
            i++
        }
        if (done && start < full.length) {
            out += full.substring(start).trim()
            start = full.length
        }
        said = start
        return out.filter { it.isNotBlank() }
    }
}
