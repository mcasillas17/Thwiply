package thwiply.elopenmike.com.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import thwiply.elopenmike.com.llm.model.ModelManager
import thwiply.elopenmike.com.llm.provider.ProviderSelectionRepository
import thwiply.elopenmike.com.ui.theme.ThemeManager
import thwiply.elopenmike.com.data.preferences.ThemeMode
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeManager: ThemeManager,
    private val modelManager: ModelManager,
    selectionRepository: ProviderSelectionRepository,
) : ViewModel() {

    val preferences = themeManager.preferences
    val activeModel = modelManager.activeModel
    val modelLoadState = modelManager.loadState
    val selection = selectionRepository.state

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themeManager.setThemeMode(mode) }
    }

    fun reloadPreferences() {
        viewModelScope.launch { themeManager.reloadPreferences() }
    }

    fun resetPreferences() {
        viewModelScope.launch { themeManager.resetPreferences() }
    }
}
