package thwiply.elopenmike.com

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import thwiply.elopenmike.com.llm.model.DownloadState
import thwiply.elopenmike.com.llm.provider.ModelProvider
import thwiply.elopenmike.com.llm.provider.NanoState
import thwiply.elopenmike.com.llm.provider.ProviderSelection
import thwiply.elopenmike.com.ui.onboarding.OnboardingContent
import thwiply.elopenmike.com.ui.theme.ThwiplyTheme
import thwiply.elopenmike.com.data.preferences.*

/** Deterministic UI callbacks, not an AICore availability or inference test. */
@RunWith(AndroidJUnit4::class)
class ProviderControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun acknowledgedEducationStillRequiresBothNanoConsents() {
        var selected by mutableStateOf(ModelProvider.QWEN)
        var downloads = 0
        compose.setContent {
            ThwiplyTheme {
                OnboardingContent(
                    DownloadState.Idle, ProviderSelection(selected), NanoState.Downloadable, false,
                    { selected = it }, { error("No Qwen download") }, {}, { downloads++ },
                    {}, {}, null,
                    preferences = PreferenceState(AppPreferences(modelSetupEducationVersion = MODEL_SETUP_EDUCATION_VERSION)),
                )
            }
        }
        compose.onNodeWithText(text(R.string.setup_optional_description)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.education_show)).performClick()
        compose.onNodeWithText(text(R.string.setup_optional_description)).assertIsDisplayed()
        compose.onNodeWithText("Gemini Nano").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.nano_use_consent)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, downloads); assertEquals(ModelProvider.QWEN, selected) }
        compose.onNodeWithText(text(R.string.nano_use_confirm)).performClick()
        compose.onNodeWithText(text(R.string.nano_prepare)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.nano_consent_body)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        compose.runOnIdle { assertEquals(0, downloads) }
    }

    @Test fun sharedForegroundFailureDoesNotMisidentifyTheProvider() {
        val message = text(R.string.inference_background)
        assertFalse(message.contains("Nano"))
        assertFalse(message.contains("Qwen"))
    }

    @Test fun selectingNanoDoesNotConsentToDownload() {
        var selected by mutableStateOf(ModelProvider.QWEN)
        var downloads = 0
        compose.setContent {
            ThwiplyTheme {
                OnboardingContent(
                    DownloadState.Idle, ProviderSelection(selected), NanoState.Downloadable, false,
                    { selected = it }, { error("Must not download Qwen") },
                    {}, { downloads++ }, {}, {}, null,
                )
            }
        }
        compose.onNodeWithText("Gemini Nano").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.nano_use_consent)).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(ModelProvider.QWEN, selected)
            assertEquals(0, downloads)
        }
        compose.onNodeWithText(text(R.string.nano_use_confirm)).performClick()
        compose.runOnIdle { assertEquals(0, downloads) }
        compose.onNodeWithText(text(R.string.nano_prepare)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.nano_consent_body)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        compose.runOnIdle { assertEquals(0, downloads) }
        compose.onNodeWithText(text(R.string.nano_prepare)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.nano_consent_confirm)).performClick()
        compose.runOnIdle { assertEquals(1, downloads) }
    }

    @Test fun unavailableAndSharedDownloadStatesOfferCheckNotAnAutomaticFallback() {
        var nanoState: NanoState by mutableStateOf(NanoState.Unavailable)
        var checks = 0
        compose.setContent {
            ThwiplyTheme {
                OnboardingContent(
                    DownloadState.Idle, ProviderSelection(ModelProvider.GEMINI_NANO), nanoState, false,
                    {}, { error("Must not download Qwen") }, { checks++ },
                    { error("Unavailable Nano cannot download") }, {}, {}, null,
                )
            }
        }
        compose.onNodeWithText(text(R.string.nano_unavailable)).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Gemini Nano") and isSelected()).performScrollTo().assertIsSelected()
        compose.onNodeWithText(text(R.string.nano_prepare)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.nano_check)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, checks)
            nanoState = NanoState.Downloading
        }
        compose.onNodeWithText(text(R.string.nano_downloading)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.nano_prepare)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.nano_check)).performScrollTo().assertIsEnabled()
    }

    @Test fun activePreparationExposesStopAndDisablesProviderChanges() {
        var stops = 0
        compose.setContent {
            ThwiplyTheme {
                OnboardingContent(
                    DownloadState.Idle, ProviderSelection(ModelProvider.GEMINI_NANO), NanoState.Downloading, true,
                    {}, {}, {}, {}, { stops++ }, {}, null,
                )
            }
        }
        compose.onNode(hasText("Qwen 2.5 1.5B") and hasClickAction()).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.action_stop)).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, stops) }
    }

    @Test fun readyNanoAllowsReturningWithoutOfferingADownload() {
        var exits = 0
        compose.setContent {
            ThwiplyTheme {
                OnboardingContent(
                    DownloadState.Idle, ProviderSelection(ModelProvider.GEMINI_NANO), NanoState.Ready, false,
                    {}, {}, {}, { error("Ready model must not be downloaded") },
                    {}, { exits++ }, null,
                )
            }
        }
        compose.onNodeWithText(text(R.string.nano_ready)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.nano_prepare)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.setup_return)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    private fun text(id: Int) = compose.activity.getString(id)
}
