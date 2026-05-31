package com.elopenmike.thwiply.ui.debug

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

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val engineManager: LlmEngineManager,
    private val modelManager: ModelManager
) : ViewModel() {

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()
    
    private val _isInitializing = MutableStateFlow(true)
    val isInitializing = _isInitializing.asStateFlow()
    
    init {
        viewModelScope.launch {
            if (modelManager.isModelAvailable()) {
                engineManager.initialize(modelManager.modelFile)
            }
            _isInitializing.value = false
        }
    }

    fun generate(prompt: String) {
        _output.value = ""
        viewModelScope.launch {
            try {
                engineManager.generateStream(prompt).collect { token ->
                    _output.value += token
                }
            } catch (e: Exception) {
                _output.value = "Error: ${e.message}"
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        engineManager.close()
    }
}
