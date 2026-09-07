package thwiply.elopenmike.com.ui.theme

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import thwiply.elopenmike.com.data.preferences.AppPreferencesRepository
import thwiply.elopenmike.com.data.preferences.ThemeMode
import thwiply.elopenmike.com.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeManager @Inject constructor(
    private val repository: AppPreferencesRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    val preferences = repository.state
    // System is a rendering fallback while storage is unknown, not a saved selection.
    val themeMode = preferences.map { it.values?.theme ?: ThemeMode.SYSTEM }
        .stateIn(scope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    suspend fun setThemeMode(mode: ThemeMode) = repository.setTheme(mode)
    suspend fun reloadPreferences() = repository.reload()
    suspend fun resetPreferences() = repository.resetPreferences()
}
