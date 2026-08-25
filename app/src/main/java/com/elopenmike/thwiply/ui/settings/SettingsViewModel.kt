package com.elopenmike.thwiply.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elopenmike.thwiply.llm.model.ModelManager
import com.elopenmike.thwiply.ui.theme.ThemeManager
import com.elopenmike.thwiply.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeManager: ThemeManager,
    private val modelManager: ModelManager
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themeManager.themeMode

    private val _notificationCaptureEnabled = MutableStateFlow(true)
    val notificationCaptureEnabled: StateFlow<Boolean> = _notificationCaptureEnabled.asStateFlow()

    private val _screenshotCaptureEnabled = MutableStateFlow(true)
    val screenshotCaptureEnabled: StateFlow<Boolean> = _screenshotCaptureEnabled.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        themeManager.setThemeMode(mode)
    }

    fun setNotificationCapture(enabled: Boolean) {
        _notificationCaptureEnabled.value = enabled
    }

    fun setScreenshotCapture(enabled: Boolean) {
        _screenshotCaptureEnabled.value = enabled
    }

    fun isModelReady(): Boolean {
        return modelManager.isModelAvailable()
    }
}
