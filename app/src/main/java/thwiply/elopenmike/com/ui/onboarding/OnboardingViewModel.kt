package thwiply.elopenmike.com.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import thwiply.elopenmike.com.llm.model.DownloadState
import thwiply.elopenmike.com.llm.model.ModelManager
import thwiply.elopenmike.com.llm.model.ModelPreset
import thwiply.elopenmike.com.ui.theme.ThemeManager
import thwiply.elopenmike.com.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val _customUrl = MutableStateFlow("")
    val customUrl: StateFlow<String> = _customUrl.asStateFlow()

    private val _hfToken = MutableStateFlow("")
    val hfToken: StateFlow<String> = _hfToken.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        themeManager.setThemeMode(mode)
    }

    fun selectPreset(preset: ModelPreset) {
        _selectedPreset.value = preset
    }

    fun updateCustomUrl(url: String) {
        _customUrl.value = url
    }

    fun updateHfToken(token: String) {
        _hfToken.value = token
    }

    fun startDownload() {
        val preset = _selectedPreset.value
        val url = if (preset.id == "custom") _customUrl.value.trim() else preset.url
        val token = _hfToken.value.trim()

        if (url.isBlank()) {
            _uiState.value = DownloadState.Error("Please enter a valid model URL.")
            return
        }

        if (preset.requiresHfToken && token.isBlank()) {
            _uiState.value = DownloadState.Error("Hugging Face token is required for ${preset.name}.")
            return
        }

        viewModelScope.launch {
            modelManager.downloadModel(url, token.ifBlank { null }).collect { state ->
                _uiState.value = state
            }
        }
    }
}
