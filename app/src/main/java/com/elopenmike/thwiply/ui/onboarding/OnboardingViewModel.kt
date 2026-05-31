package com.elopenmike.thwiply.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elopenmike.thwiply.llm.model.DownloadState
import com.elopenmike.thwiply.llm.model.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val modelManager: ModelManager
) : ViewModel() {
    private val _uiState = MutableStateFlow<DownloadState>(
        if (modelManager.isModelAvailable()) DownloadState.Success else DownloadState.Idle
    )
    val uiState: StateFlow<DownloadState> = _uiState.asStateFlow()
    
    fun startDownload(url: String, token: String) {
        viewModelScope.launch {
            modelManager.downloadModel(url, token).collect { state ->
                _uiState.value = state
            }
        }
    }
}
