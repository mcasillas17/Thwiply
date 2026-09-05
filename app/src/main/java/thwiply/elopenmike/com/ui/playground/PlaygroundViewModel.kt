package thwiply.elopenmike.com.ui.playground

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import thwiply.elopenmike.com.llm.engine.LlmEngineManager
import thwiply.elopenmike.com.llm.model.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import thwiply.elopenmike.com.llm.engine.EngineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaygroundMetrics(
    val tokenCount: Int = 0,
    val elapsedMs: Long = 0,
    val tokensPerSec: Double = 0.0
)

sealed interface LabReadiness {
    data object Missing : LabReadiness
    data object NeedsInitialization : LabReadiness
    data object Initializing : LabReadiness
    data object Ready : LabReadiness
    data class Failed(val message: String) : LabReadiness
}

@HiltViewModel
class PlaygroundViewModel internal constructor(
    private val engineManager: LlmEngineManager,
    private val modelManager: ModelManager,
    private val initializationDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject constructor(engineManager: LlmEngineManager, modelManager: ModelManager) :
        this(engineManager, modelManager, Dispatchers.IO)

    val activeModel = modelManager.activeModel
    val readiness = combine(activeModel, engineManager.state) { _, _ -> currentReadiness() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, currentReadiness())
    private var initializationJob: Job? = null
    private val _generationFailure = MutableStateFlow<Throwable?>(null)
    val generationFailure = _generationFailure.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    private val _metrics = MutableStateFlow(PlaygroundMetrics())
    val metrics: StateFlow<PlaygroundMetrics> = _metrics.asStateFlow()

    private fun currentReadiness(): LabReadiness {
        if (!modelManager.isModelAvailable()) return LabReadiness.Missing
        return when (val state = engineManager.state.value) {
            EngineState.Idle -> LabReadiness.NeedsInitialization
            is EngineState.Initializing -> LabReadiness.Initializing
            is EngineState.Failed -> LabReadiness.Failed(state.message)
            is EngineState.Ready -> if (state.modelPath == modelManager.modelFile.absolutePath) {
                LabReadiness.Ready
            } else {
                LabReadiness.NeedsInitialization
            }
        }
    }

    fun prepareEngine() {
        if (!modelManager.isModelAvailable() || currentReadiness() == LabReadiness.Ready ||
            initializationJob?.isActive == true || _isGenerating.value) return
        initializationJob = viewModelScope.launch {
            // Keep the shell responsive while the existing process-owned engine initializes.
            val result = withContext(initializationDispatcher) {
                engineManager.initialize(modelManager.modelFile)
            }
            ensureActive()
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            // EngineState carries the initialization failure; output remains generation-only.
        }
    }

    fun generate(input: String, isJsonExtraction: Boolean) {
        if (input.isBlank() || currentReadiness() != LabReadiness.Ready || _isGenerating.value) return
        _isGenerating.value = true
        _generationFailure.value = null
        val prompt = if (isJsonExtraction) {
            """
            You are Thwiply, an on-device AI assistant. Extract any actionable task from the following message as clean JSON:
            Message: "$input"
            Output schema:
            {
              "task": "Task title",
              "due": "Due date/time if specified",
              "priority": "HIGH" | "NORMAL",
              "sender": "Identified sender"
            }
            """.trimIndent()
        } else {
            input
        }

        viewModelScope.launch {
            _isGenerating.value = true
            _output.value = ""
            val startTime = System.currentTimeMillis()
            var tokens = 0

            try {
                engineManager.generateStream(prompt).collect { token ->
                    _output.value += token
                    tokens++
                    val elapsed = System.currentTimeMillis() - startTime
                    val tps = if (elapsed > 0) (tokens.toDouble() / (elapsed / 1000.0)) else 0.0
                    _metrics.value = PlaygroundMetrics(tokens, elapsed, tps)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _generationFailure.value = error
            } finally {
                _isGenerating.value = false
            }
        }
    }

}
