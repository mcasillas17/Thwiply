package thwiply.elopenmike.com.ui.settings

import androidx.lifecycle.ViewModel
import thwiply.elopenmike.com.llm.model.ModelManager
import thwiply.elopenmike.com.ui.theme.ThemeManager
import thwiply.elopenmike.com.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeManager: ThemeManager,
    private val modelManager: ModelManager
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themeManager.themeMode
    val activeModel = modelManager.activeModel

    fun setThemeMode(mode: ThemeMode) {
        themeManager.setThemeMode(mode)
    }

    fun isModelReady(): Boolean {
        return modelManager.isModelAvailable()
    }
}
