package com.elopenmike.thwiply.ui.playground

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elopenmike.thwiply.llm.engine.LlmEngineManager
import com.elopenmike.thwiply.llm.model.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class PlaygroundViewModel @Inject constructor(
    private val engineManager: LlmEngineManager,
    private val modelManager: ModelManager
) : ViewModel() {

    private val _isInitializing = MutableStateFlow(true)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    private val _metrics = MutableStateFlow(PlaygroundMetrics())
    val metrics: StateFlow<PlaygroundMetrics> = _metrics.asStateFlow()

    init {
        initEngine()
    }

    private fun initEngine() {
        viewModelScope.launch {
            _isInitializing.value = true
            if (modelManager.isModelAvailable()) {
                engineManager.initialize(modelManager.modelFile)
            }
            _isInitializing.value = false
        }
    }

    fun generate(input: String, isJsonExtraction: Boolean) {
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
            } catch (e: Exception) {
                _output.value = "Error: ${e.message}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        engineManager.close()
    }
}
