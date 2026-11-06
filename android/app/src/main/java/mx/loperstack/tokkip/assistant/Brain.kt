package mx.loperstack.tokkip.assistant

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tokkip's thinking: Gemma running on the phone through LiteRT-LM. Loaded when
 * you open the chat (a few seconds), let go when you leave it, so it only uses
 * memory and battery while you talk. Tries the GPU first (faster) and falls
 * back to the CPU, which works on every phone (Tensor, Snapdragon, MediaTek).
 */
object Brain {
    sealed interface State {
        data object Off : State
        data object Loading : State
        data class Ready(val backend: String) : State
        data class Failed(val why: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    private val lock = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedFrom: String? = null
    private var app: Context? = null

    private val _done = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** What Tokkip just did in the app from the chat ("⏰ Alarma a las 6:30…"), for the chat to show. */
    val done: SharedFlow<String> = _done

    private val _need = MutableSharedFlow<List<String>>(extraBufferCapacity = 4)
    /** Permissions a tool was missing (calendar, contacts, calls): the chat asks for them. */
    val need: SharedFlow<List<String>> = _need

    /** Why Tokkip can't do things in the app this time, if she can't (shown in the chat). */
    var toolsProblem: String? = null
        private set

    /** How many past lines the model sees at the start of a chat (the rest stays in Memory). */
    private const val CONTEXT_LINES = 12

    suspend fun load(context: Context, model: File, name: String, history: List<Line>) = lock.withLock {
        if (engine != null && loadedFrom == model.path && conversation != null) return@withLock
        withContext(Dispatchers.IO) {
            closeLocked()
            app = context.applicationContext
            _state.value = State.Loading
            val cache = context.cacheDir.path
            var lastError: Throwable? = null
            // If the GPU ever took the app down while loading, use the CPU from then on.
            val prefs = context.getSharedPreferences("tokkip_asistente", Context.MODE_PRIVATE)
            val gpuOk = !prefs.getBoolean("gpu_fallo", false)
            val backends = if (gpuOk) listOf<Backend>(Backend.GPU(), Backend.CPU()) else listOf<Backend>(Backend.CPU())
            for (backend in backends) {
                val gpu = backend is Backend.GPU
                if (gpu) prefs.edit().putBoolean("gpu_fallo", true).commit()
                val e = runCatching {
                    Engine(EngineConfig(modelPath = model.path, backend = backend, maxNumTokens = 4096, cacheDir = cache)).also { it.initialize() }
                }.onFailure { lastError = it }.getOrNull()
                if (gpu) prefs.edit().putBoolean("gpu_fallo", e == null).commit()
                if (e == null) continue
                engine = e
                loadedFrom = model.path
                conversation = converse(e, name, history)
                _state.value = State.Ready(backend.name)
                return@withContext
            }
            _state.value = State.Failed(lastError?.message ?: "No se pudo cargar el modelo")
        }
    }

    private fun tools() = runCatching {
        val c = app ?: return@runCatching emptyList()
        TokkipTools(c, onDone = { _done.tryEmit(it) }, onNeed = { _need.tryEmit(it) }).providers()
    }.getOrElse { toolsProblem = it.message; emptyList() }

    /** A conversation with the tools; without them if they won't load, so she can still talk. */
    private fun converse(e: Engine, name: String, history: List<Line>): Conversation {
        toolsProblem = null
        return runCatching { e.createConversation(config(name, history, withTools = true)) }
            .getOrElse {
                toolsProblem = it.message ?: it.javaClass.simpleName
                e.createConversation(config(name, history, withTools = false))
            }
    }

    private fun config(name: String, history: List<Line>, withTools: Boolean): ConversationConfig {
        val now = SimpleDateFormat("EEEE d 'de' MMMM yyyy, HH:mm", Locale.forLanguageTag("es-MX")).format(Date())
        val past = history.takeLast(CONTEXT_LINES).let { lines ->
            // The model expects to start with you speaking.
            lines.dropWhile { !it.fromYou }
        }.map { if (it.fromYou) Message.user(it.text) else Message.model(it.text) }
        return ConversationConfig(
            systemInstruction = Contents.of(Persona.instructions(name, now)),
            initialMessages = past,
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7),
            tools = if (withTools) tools() else emptyList(),
        )
    }

    /** Starts over (after forgetting, or when the chat grows long). */
    suspend fun restart(name: String, history: List<Line>) = lock.withLock {
        withContext(Dispatchers.IO) {
            val e = engine ?: return@withContext
            runCatching { conversation?.close() }
            conversation = converse(e, name, history)
        }
    }

    /** Tokkip's answer as it comes, piece by piece. */
    fun ask(text: String): Flow<String> {
        val c = conversation ?: error("El modelo no está cargado")
        return c.sendMessageAsync(text).map { it.toString() }.flowOn(Dispatchers.IO)
    }

    fun stop() {
        runCatching { conversation?.cancelProcess() }
    }

    suspend fun unload() = lock.withLock { withContext(Dispatchers.IO) { closeLocked() } }

    private fun closeLocked() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
        loadedFrom = null
        _state.value = State.Off
    }
}
