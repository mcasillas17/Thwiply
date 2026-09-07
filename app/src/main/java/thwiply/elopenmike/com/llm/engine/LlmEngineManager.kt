package thwiply.elopenmike.com.llm.engine

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface EngineState {
    data object Idle : EngineState
    data class Initializing(val modelPath: String) : EngineState
    data class Ready(val modelPath: String) : EngineState
    data class Failed(val message: String, val cause: Throwable? = null) : EngineState
}

internal fun interface ManagedEngineFactory {
    fun create(modelFile: File): ManagedEngine
}

internal interface ManagedEngine : AutoCloseable {
    fun initialize()
    fun createConversation(): ManagedConversation
}

internal interface ManagedConversation : AutoCloseable {
    fun generate(prompt: String): Flow<String>
}

@Singleton
class LlmEngineManager internal constructor(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val engineFactory: ManagedEngineFactory
) {
    @Inject
    constructor() : this(Dispatchers.IO, ManagedEngineFactory(::LiteRtManagedEngine))

    private val mutex = Mutex()
    private var engine: ManagedEngine? = null
    private var activeModelPath: String? = null
    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    suspend fun initialize(modelFile: File): Result<Unit> = withContext(dispatcher) { mutex.withLock {
        val modelPath = modelFile.absolutePath
        if (engine != null && activeModelPath == modelPath) {
            return@withLock Result.success(Unit)
        }

        _state.value = EngineState.Initializing(modelPath)
        var candidate: ManagedEngine? = null
        try {
            // Do not hold two native engines while replacing the active model.
            closeEngine()
            candidate = engineFactory.create(modelFile)
            candidate.initialize()
            currentCoroutineContext().ensureActive()
            engine = candidate
            activeModelPath = modelPath
            _state.value = EngineState.Ready(modelPath)
            Result.success(Unit)
        } catch (error: Exception) {
            try {
                candidate?.close()
            } catch (cleanup: Exception) {
                engine = candidate
                activeModelPath = null
                error.addSuppressed(cleanup)
            }
            if (error is CancellationException) {
                _state.value = EngineState.Idle
                throw error
            }
            _state.value = EngineState.Failed(
                error.message ?: "Unable to initialize the local model", error
            )
            Result.failure(error)
        }
    } }

    fun generateStream(prompt: String): Flow<String> = flow {
        mutex.withLock {
            val currentEngine = engine
                ?: throw IllegalStateException("Engine not initialized")
            currentEngine.createConversation().use { conversation ->
                conversation.generate(prompt).collect(::emit)
            }
        }
    }.flowOn(dispatcher)

    suspend fun close() = withContext(dispatcher) { mutex.withLock {
        try {
            closeEngine()
            _state.value = EngineState.Idle
        } catch (error: Exception) {
            _state.value = EngineState.Failed("Unable to close the local model", error)
            throw error
        }
    } }

    private fun closeEngine() {
        val previous = engine
        activeModelPath = null
        previous?.close()
        engine = null
    }
}

private class LiteRtManagedEngine(
    modelFile: File
) : ManagedEngine {
    private val engine = Engine(EngineConfig(modelFile.absolutePath))

    override fun initialize() {
        engine.initialize()
    }

    override fun createConversation(): ManagedConversation =
        LiteRtManagedConversation(engine.createConversation())

    override fun close() {
        engine.close()
    }
}

private class LiteRtManagedConversation(
    private val conversation: Conversation
) : ManagedConversation {
    override fun generate(prompt: String): Flow<String> =
        conversation.sendMessageAsync(prompt).map { message ->
            message.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
        }

    override fun close() {
        conversation.close()
    }
}
