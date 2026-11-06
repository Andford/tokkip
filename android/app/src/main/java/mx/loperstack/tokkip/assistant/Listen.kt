package mx.loperstack.tokkip.assistant

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Tap to talk: the phone's own speech recognition. Tries the on-device
 * recognizer first (works without internet on Pixels), then the phone's
 * usual one. If neither will even start here, [onDone] says so with
 * `broken = true` and the screen uses the system's dictation window instead,
 * which works on every phone. [onPartial] shows the words as you say them.
 */
class Listen(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onDone: (said: String?, broken: Boolean, why: String?) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var stage = 0

    companion object {
        /** Neither recognizer works on this phone (until the app restarts): use the dictation window. */
        @Volatile var broken = false
            private set

        /** Which recognizer worked last time, to start there. */
        @Volatile private var good = 0

        private const val ON_DEVICE = 0
        private const val USUAL = 1

        fun intent(prompt: String? = null): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .apply { if (prompt != null) putExtra(RecognizerIntent.EXTRA_PROMPT, prompt) }

        /** Errors that mean "this recognizer can't work here", not "you said nothing". */
        private val SETUP = setOf(
            SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS, SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_AUDIO,
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
        stop()
        stage = good
        attempt()
    }

    private fun attempt() {
        if (stage == ON_DEVICE && !(Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))) stage = USUAL
        if (stage == USUAL && !SpeechRecognizer.isRecognitionAvailable(context)) return giveUp("no hay reconocimiento de voz")
        val r = runCatching {
            if (stage == ON_DEVICE && Build.VERSION.SDK_INT >= 33) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrElse { return next(it.message ?: "no se pudo abrir") }
        recognizer = r
        var spoke = false
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { spoke = true }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                    spoke = true
                    onPartial(it)
                }
            }
            override fun onResults(results: Bundle?) {
                good = stage
                recognizer?.destroy()
                recognizer = null
                onDone(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }, false, null)
            }
            override fun onError(error: Int) {
                recognizer?.destroy()
                recognizer = null
                if (error in SETUP && !spoke) {
                    // Spanish isn't on the phone yet: ask it to fetch it for next time.
                    if (stage == ON_DEVICE && Build.VERSION.SDK_INT >= 33 &&
                        (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE || error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
                    ) runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply { triggerModelDownload(intent()); destroy() } }
                    next(describe(error))
                } else {
                    good = stage
                    onDone(null, false, describe(error))
                }
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        runCatching { r.startListening(intent()) }.onFailure { next(it.message ?: "no arrancó") }
    }

    private fun next(why: String) {
        if (stage == ON_DEVICE) {
            stage = USUAL
            attempt()
        } else giveUp(why)
    }

    private fun giveUp(why: String) {
        broken = true
        onDone(null, true, why)
    }

    /** Stop listening and get what was said. */
    fun finish() {
        recognizer?.stopListening()
    }

    fun stop() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }
}
