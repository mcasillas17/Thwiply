package thwiply.elopenmike.com.ui.playground

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import thwiply.elopenmike.com.llm.provider.InferenceCoordinator
import thwiply.elopenmike.com.llm.provider.InferenceFailure
import thwiply.elopenmike.com.llm.provider.FailureKind
import thwiply.elopenmike.com.llm.provider.ProviderReadiness

data class PlaygroundMetrics(
    val characterCount: Int = 0,
    val elapsedMs: Long = 0,
    val charactersPerSec: Double = 0.0,
)

@HiltViewModel
class PlaygroundViewModel @Inject constructor(
    private val coordinator: InferenceCoordinator,
) : ViewModel() {
    val selection = coordinator.selection
    val readiness = coordinator.readiness
    val foreground = coordinator.foreground
    val busy = coordinator.busy
    private var preparationJob: Job? = null
    private var generationJob: Job? = null
    private var revision = 0L

    private val _generationFailure = MutableStateFlow<InferenceFailure?>(null)
    val generationFailure = _generationFailure.asStateFlow()
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()
    private val _stopped = MutableStateFlow(false)
    val stopped = _stopped.asStateFlow()
    private val _output = MutableStateFlow("")
    val output = _output.asStateFlow()
    private val _metrics = MutableStateFlow(PlaygroundMetrics())
    val metrics = _metrics.asStateFlow()

    init {
        var previousProvider = selection.value.provider
        viewModelScope.launch {
            selection.map { it.provider }.distinctUntilChanged().collect { provider ->
                if (provider != previousProvider) {
                    previousProvider = provider
                    stop()
                    _output.value = ""
                    _metrics.value = PlaygroundMetrics()
                    _generationFailure.value = null
                    _stopped.value = false
                }
            }
        }
    }

    fun prepareEngine() {
        if (preparationJob?.isActive == true || selection.value.provider == null) return
        preparationJob = viewModelScope.launch {
            _generationFailure.value = null
            try {
                busy.first { !it }
                coordinator.prepare()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: InferenceFailure) {
                _generationFailure.value = failure
            }
        }
    }

    fun generate(input: String, isJsonExtraction: Boolean) {
        if (_isGenerating.value || busy.value) return
        if (input.isBlank()) {
            _generationFailure.value = InferenceFailure(FailureKind.INVALID_INPUT)
            return
        }
        if (input.length > MAX_INPUT_CHARACTERS) {
            _generationFailure.value = InferenceFailure(FailureKind.INPUT_TOO_LONG)
            return
        }
        if (readiness.value != ProviderReadiness.Ready) {
            _generationFailure.value = InferenceFailure(FailureKind.UNAVAILABLE)
            return
        }
        val provider = selection.value.provider
        val requestRevision = ++revision
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
        } else input
        generationJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            _isGenerating.value = true
            _generationFailure.value = null
            _stopped.value = false
            _output.value = ""
            _metrics.value = PlaygroundMetrics()
            val start = System.nanoTime()
            try {
                coordinator.generate(prompt).collect { chunk ->
                    if (revision == requestRevision && selection.value.provider == provider) {
                        _output.value += chunk
                        val elapsed = (System.nanoTime() - start) / 1_000_000
                        val characters = _output.value.codePointCount(0, _output.value.length)
                        _metrics.value = PlaygroundMetrics(
                            characters, elapsed,
                            if (elapsed > 0) characters * 1_000.0 / elapsed else 0.0,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                if (revision == requestRevision) _stopped.value = true
                throw cancellation
            } catch (failure: InferenceFailure) {
                if (failure.kind == FailureKind.SAFETY || failure.kind == FailureKind.EMPTY_OUTPUT) {
                    _output.value = ""
                    _metrics.value = PlaygroundMetrics()
                }
                if (revision == requestRevision) {
                    _generationFailure.value = failure
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun stop() {
        revision++
        if (_isGenerating.value) _stopped.value = true
        generationJob?.cancel()
        preparationJob?.cancel()
    }

    companion object {
        const val MAX_INPUT_CHARACTERS = 2_000
    }
}
