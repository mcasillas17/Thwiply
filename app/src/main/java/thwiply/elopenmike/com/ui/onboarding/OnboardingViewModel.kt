package thwiply.elopenmike.com.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import thwiply.elopenmike.com.llm.model.DownloadState
import thwiply.elopenmike.com.llm.model.ModelManager
import thwiply.elopenmike.com.llm.model.ModelPreset

@HiltViewModel
class OnboardingViewModel internal constructor(
    isModelAvailable: () -> Boolean,
    private val downloadModel: (ModelPreset) -> Flow<DownloadState>,
) : ViewModel() {
    @Inject constructor(modelManager: ModelManager) : this(
        modelManager::isModelAvailable,
        modelManager::downloadModel,
    )

    private val _uiState = MutableStateFlow<DownloadState>(
        if (isModelAvailable()) DownloadState.Success else DownloadState.Idle,
    )
    val uiState: StateFlow<DownloadState> = _uiState.asStateFlow()
    private val _selectedPreset = MutableStateFlow(ModelPreset.QWEN_2_5_1_5B)
    val selectedPreset: StateFlow<ModelPreset> = _selectedPreset.asStateFlow()
    private var downloadJob: Job? = null

    fun selectPreset(preset: ModelPreset) {
        if (downloadJob?.isCompleted != false) _selectedPreset.value = preset
    }

    fun startDownload() {
        // A cancelled blocking I/O writer may still be unwinding. Never overlap it.
        if (downloadJob?.isCompleted == false) return
        val preset = _selectedPreset.value
        downloadJob = viewModelScope.launch {
            _uiState.value = DownloadState.Downloading(0)
            try {
                downloadModel(preset).collect { _uiState.value = it }
            } catch (error: IOException) {
                _uiState.value = DownloadState.Error(error.message ?: error.javaClass.simpleName)
            } finally {
                if (_uiState.value is DownloadState.Downloading) {
                    _uiState.value = DownloadState.Idle
                }
            }
        }
    }

    fun pauseDownload() {
        downloadJob?.cancel()
    }
}
