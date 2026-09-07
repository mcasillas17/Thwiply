package thwiply.elopenmike.com

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import thwiply.elopenmike.com.data.preferences.*

/** Real activity/Hilt/storage. Restores only this test's preference bytes; never clears app data. */
@RunWith(AndroidJUnit4::class)
class PreferenceActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var file: File
    private var original: ByteArray? = null

    @Before fun rememberPreferences() {
        file = File(compose.activity.noBackupFilesDir, "app-preferences")
        original = if (file.exists()) file.readBytes() else null
        compose.waitUntil(5_000) { compose.activity.themeManager.preferences.value.values != null }
    }

    @After fun restorePreferences() {
        if (!::file.isInitialized) return
        if (original == null) {
            if (file.exists()) check(file.delete())
        } else {
            file.writeBytes(original!!)
        }
        runBlocking { compose.activity.themeManager.reloadPreferences() }
    }

    @Test fun themeSelectionSurvivesActivityAndRepositoryRecreation() {
        tab("Settings")
        compose.onNodeWithText(text(R.string.theme_dark)).performClick()
        compose.waitUntil(5_000) { compose.activity.themeManager.themeMode.value == ThemeMode.DARK }
        assertEquals(ThemeMode.DARK, runBlocking { FilePreferenceStorage(file).read().theme })
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(text(R.string.theme_dark)).assertIsSelected()
        tab("Today")
        compose.onNodeWithContentDescription("Add task").assertIsEnabled()
        tab("Settings")
        compose.onNodeWithText("Delete notification data and rules").performScrollTo().assertIsEnabled()
    }

    @Test fun educationAcknowledgementSurvivesRecreationAndCanBeReopened() {
        runBlocking { compose.activity.themeManager.resetPreferences() }
        tab("Settings")
        compose.onNodeWithText("Model setup").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.education_acknowledge)).performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.activity.themeManager.preferences.value.values?.hasSeenModelSetupEducation == true
        }
        assertTrue(runBlocking { FilePreferenceStorage(file).read().hasSeenModelSetupEducation })
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(text(R.string.setup_optional_description)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.education_show)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.setup_optional_description)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Return to app").performClick()
        tab("Today")
        compose.onNodeWithContentDescription("Add task").assertIsEnabled()
    }

    @Test fun malformedPreferencesRequireConfirmedResetAndLeaveProviderUntouched() {
        val provider = File(compose.activity.noBackupFilesDir, "model-provider")
        val providerBytes = if (provider.exists()) provider.readBytes() else null
        file.writeText("synthetic damaged preferences")
        runBlocking { compose.activity.themeManager.reloadPreferences() }
        tab("Settings")
        compose.onNodeWithText(text(R.string.preferences_malformed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.theme_dark)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.preferences_reset)).performClick()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        assertEquals("synthetic damaged preferences", file.readText())
        compose.onNodeWithText(text(R.string.preferences_reset)).performClick()
        compose.onNodeWithText(text(R.string.preferences_reset_confirm)).performClick()
        compose.waitUntil(5_000) { compose.activity.themeManager.preferences.value.failure == null }
        assertEquals(AppPreferences(), runBlocking { FilePreferenceStorage(file).read() })
        if (providerBytes == null) assertFalse(provider.exists())
        else assertArrayEquals(providerBytes, provider.readBytes())
    }

    private fun text(id: Int) = compose.activity.getString(id)
    private fun tab(name: String) =
        compose.onNode(hasText(name) and hasClickAction()).performClick()
}
