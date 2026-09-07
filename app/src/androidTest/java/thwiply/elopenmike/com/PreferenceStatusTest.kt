package thwiply.elopenmike.com

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import thwiply.elopenmike.com.data.preferences.*
import thwiply.elopenmike.com.ui.preferences.PreferenceStatus
import thwiply.elopenmike.com.ui.theme.ThwiplyTheme

@RunWith(AndroidJUnit4::class)
class PreferenceStatusTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun failedResetRetainsNonDestructiveReadRecovery() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var readable = false
        var writes = 0
        val saved = AppPreferences(ThemeMode.DARK, MODEL_SETUP_EDUCATION_VERSION)
        val repository = AppPreferencesRepository(object : PreferenceStorage {
            override suspend fun read(): AppPreferences {
                if (!readable) throw IOException("private path")
                return saved
            }
            override suspend fun write(value: AppPreferences) {
                writes++
                throw IOException("private path")
            }
        }, scope, Dispatchers.IO)
        try {
            compose.setContent {
                val state by repository.state.collectAsState()
                ThwiplyTheme { PreferenceStatus(state) { scope.launch { repository.reload() } } }
            }
            runBlocking { assertFalse(repository.resetPreferences()) }
            compose.onNodeWithText(text(R.string.preferences_retry)).assertIsEnabled()
            readable = true
            compose.onNodeWithText(text(R.string.preferences_retry)).performClick()
            compose.waitUntil(5_000) { repository.state.value.failure == null }
            assertEquals(saved, repository.state.value.values)
            assertEquals(1, writes)
        } finally {
            scope.cancel()
        }
    }

    @Test fun failureCopyIsResourceBackedAnnouncedAndDoesNotRevealExceptionDetails() {
        var state by mutableStateOf(PreferenceState())
        var retries = 0
        compose.setContent { ThwiplyTheme { PreferenceStatus(state) { retries++ } } }
        compose.onNodeWithText(text(R.string.preferences_loading)).assertIsDisplayed()
        val messages = mapOf(
            PreferenceFailureReason.READ to R.string.preferences_read_failed,
            PreferenceFailureReason.MALFORMED to R.string.preferences_malformed,
            PreferenceFailureReason.UNSUPPORTED to R.string.preferences_unsupported,
            PreferenceFailureReason.WRITE to R.string.preferences_write_failed,
            PreferenceFailureReason.RESET to R.string.preferences_reset_failed,
        )
        for ((reason, message) in messages) {
            compose.runOnIdle {
                state = PreferenceState(failure = PreferenceFailure(reason, IOException("/private/path secret")))
            }
            compose.onNodeWithText(text(message)).assertIsDisplayed().assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
            )
            compose.onNodeWithText("/private/path", substring = true).assertDoesNotExist()
            if (reason != PreferenceFailureReason.WRITE) {
                compose.onNodeWithText(text(R.string.preferences_retry)).performClick()
            } else {
                compose.onNodeWithText(text(R.string.preferences_retry)).assertDoesNotExist()
            }
        }
        compose.runOnIdle { assertEquals(4, retries) }
    }

    private fun text(id: Int) = compose.activity.getString(id)
}
