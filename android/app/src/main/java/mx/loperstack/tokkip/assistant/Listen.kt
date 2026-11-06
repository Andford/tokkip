package mx.loperstack.tokkip.assistant

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Tap to talk: the phone's own speech recognition. Tries the on-device
 * recognizer first (works without internet on Pixels), then the phone's
 * usual one. The recognizer is kept between turns (making and dropping one
 * every turn is what makes Google's service answer "busy"), and a hiccup is
 * retried quietly. Only if neither will start at all does [onDone] say
 * `broken = true`, so the screen can fall back to the system's dictation
 * window. [onPartial] shows the words as you say them.
 */
class Listen(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onDone: (said: String?, broken: Boolean, why: String?) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var recognizerStage = -1
    private var stage = ON_DEVICE
    private var tries = 0
    private var startedAt = 0L
    private var spoke = false
    private var active = false

    companion object {
        /** Neither recognizer works on this phone (until the app restarts): tap-to-talk uses the dictation window. */
        @Volatile var broken = false
            private set

        /** Which recognizer worked last time, to start there. */
        @Volatile private var good = ON_DEVICE

        private const val ON_DEVICE = 0
        private const val USUAL = 1

        fun intent(prompt: String? = null): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .apply { if (prompt != null) putExtra(RecognizerIntent.EXTRA_PROMPT, prompt) }

        /** This recognizer can't work here: try the next one. */
        private val HARD = setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
        )

        /** A hiccup: try the same one again in a moment. */
        private val SOFT = setOf(
            SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS, SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        )

        fun describe(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "el micrófono está ocupado"
            SpeechRecognizer.ERROR_CLIENT -> "el reconocimiento de voz no respondió"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "falta permiso de micrófono"
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "necesita internet"
            SpeechRecognizer.ERROR_NO_MATCH -> "no entendí"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "el reconocimiento de voz está ocupado"
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "el servicio de voz se desconectó"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no oí nada"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "falta el español sin conexión"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "demasiadas peticiones"
            else -> "error $error"
        }
    }

    fun start() {
        main.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        stage = good
        tries = 0
        active = true
        attempt()
    }

    private fun usable(s: Int) = when (s) {
        ON_DEVICE -> Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        else -> SpeechRecognizer.isRecognitionAvailable(context)
    }

    private fun recognizerFor(s: Int): SpeechRecognizer? {
        recognizer?.takeIf { recognizerStage == s }?.let { return it }
        drop()
        val r = runCatching {
            if (s == ON_DEVICE && Build.VERSION.SDK_INT >= 33) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull() ?: return null
        r.setRecognitionListener(Listener(r))
        recognizer = r
        recognizerStage = s
        return r
    }

    private fun attempt() {
        if (!active) return
        while (stage <= USUAL && !usable(stage)) stage++
        if (stage > USUAL) return giveUp("no hay reconocimiento de voz")
        val r = recognizerFor(stage) ?: return next("no se pudo abrir")
        spoke = false
        startedAt = SystemClock.elapsedRealtime()
        runCatching { r.startListening(intent()) }.onFailure { retry(it.message ?: "no arrancó") }
    }

    private inner class Listener(private val mine: SpeechRecognizer) : RecognitionListener {
        private fun current() = active && mine === recognizer

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() { spoke = true }
        override fun onPartialResults(partialResults: Bundle?) {
            if (!current()) return
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                spoke = true
                onPartial(it)
            }
        }
        override fun onResults(results: Bundle?) {
            if (!current()) return
            good = stage
            active = false
            onDone(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }, false, null)
        }
        override fun onError(error: Int) {
            if (!current()) return
            val quick = SystemClock.elapsedRealtime() - startedAt < 600
            when {
                spoke -> finishWith(error)
                // Some recognizers say "no match" the instant they start when the last turn just ended: a hiccup.
                error in SOFT || (quick && error == SpeechRecognizer.ERROR_NO_MATCH) -> retry(describe(error))
                error in HARD -> {
                    if (stage == ON_DEVICE && Build.VERSION.SDK_INT >= 33 && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        // Spanish isn't on the phone yet: ask it to fetch it for next time.
                        runCatching { mine.triggerModelDownload(intent()) }
                    }
                    next(describe(error))
                }
                else -> finishWith(error)
            }
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun finishWith(error: Int) {
        good = stage
        active = false
        onDone(null, false, describe(error))
    }

    /** Same recognizer, fresh, in a moment; after two hiccups, the next one. */
    private fun retry(why: String) {
        if (tries < 2) {
            tries++
            drop()
            main.postDelayed({ attempt() }, 350L * tries)
        } else next(why)
    }

    private fun next(why: String) {
        tries = 0
        if (stage == ON_DEVICE) {
            stage = USUAL
            main.post { attempt() }
        } else giveUp(why)
    }

    private fun giveUp(why: String) {
        active = false
        broken = true
        onDone(null, true, why)
    }

    private fun drop() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        recognizerStage = -1
    }

    /** Stop listening and get what was said. */
    fun finish() {
        recognizer?.stopListening()
    }

    /** Stop listening and let the recognizer go (leaving the screen, hanging up). */
    fun stop() {
        active = false
        main.removeCallbacksAndMessages(null)
        drop()
    }
}
