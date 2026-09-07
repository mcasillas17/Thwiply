package thwiply.elopenmike.com.ui.theme

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import thwiply.elopenmike.com.data.preferences.*

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeManagerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `recreated manager restores theme and exposes failures without claiming System is saved`() = runTest {
        val file = File(temporaryFolder.newFolder(), "preferences")
        fun manager() = ThemeManager(
            AppPreferencesRepository(FilePreferenceStorage(file), backgroundScope, StandardTestDispatcher(testScheduler)),
            backgroundScope,
        )
        val first = manager()
        first.setThemeMode(ThemeMode.DARK)
        runCurrent()
        assertEquals(ThemeMode.DARK, first.themeMode.value)
        val recreated = manager()
        runCurrent()
        assertEquals(ThemeMode.DARK, recreated.themeMode.value)
        file.writeText("invalid")
        val unavailable = manager()
        runCurrent()
        assertEquals(ThemeMode.SYSTEM, unavailable.themeMode.value)
        assertNull(unavailable.preferences.value.values)
        assertEquals(PreferenceFailureReason.MALFORMED, unavailable.preferences.value.failure!!.reason)
    }
}
