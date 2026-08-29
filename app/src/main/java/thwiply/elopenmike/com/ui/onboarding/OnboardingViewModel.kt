package thwiply.elopenmike.com.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import thwiply.elopenmike.com.llm.model.DownloadState
import thwiply.elopenmike.com.llm.model.ModelManager
import thwiply.elopenmike.com.llm.model.ModelPreset
import thwiply.elopenmike.com.ui.theme.ThemeManager
import thwiply.elopenmike.com.ui.theme.ThemeMode
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val themeManager: ThemeManager
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themeManager.themeMode

    private val _uiState = MutableStateFlow<DownloadState>(
        if (modelManager.isModelAvailable()) DownloadState.Success else DownloadState.Idle
    )
    val uiState: StateFlow<DownloadState> = _uiState.asStateFlow()

    private val _selectedPreset = MutableStateFlow(ModelPreset.QWEN_2_5_1_5B)
    val selectedPreset: StateFlow<ModelPreset> = _selectedPreset.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        themeManager.setThemeMode(mode)
    }

    fun selectPreset(preset: ModelPreset) {
        _selectedPreset.value = preset
    }

    fun startDownload() {
        val preset = _selectedPreset.value

        viewModelScope.launch {
            modelManager.downloadModel(preset).collect { state ->
                _uiState.value = state
            }
        }
    }
}
