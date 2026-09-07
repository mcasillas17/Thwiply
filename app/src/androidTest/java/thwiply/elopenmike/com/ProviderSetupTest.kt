package thwiply.elopenmike.com

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import thwiply.elopenmike.com.llm.provider.InferenceCoordinator

/** Real activity/selection storage; does not consent to any model download. */
@RunWith(AndroidJUnit4::class)
class ProviderSetupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun nanoSelectionSurvivesRecreationWithoutBlockingManualWork() {
        tab("Settings")
        compose.onNodeWithText("Model setup").performScrollTo().performClick()
        compose.onNodeWithText("Gemini Nano").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.nano_use_confirm)).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Gemini Nano") and isSelected())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.activityRule.scenario.recreate()
        compose.onNode(hasText("Gemini Nano") and isSelected())
            .performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Return to app").performClick()
        tab("Today")
        compose.onNodeWithContentDescription("Add task").assertIsEnabled()
        tab("Settings")
        compose.onNodeWithText("Delete notification data and rules")
            .performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Model setup").performScrollTo().performClick()
        // Restore only the preference modified by this scenario, not app data.
        // Re-entry checks Nano asynchronously; never click a disabled provider card.
        val qwen = hasText("Qwen 2.5 1.5B") and hasClickAction()
        compose.waitUntil(InferenceCoordinator.STATUS_TIMEOUT_MS + 5_000) {
            compose.onAllNodes(qwen).fetchSemanticsNodes().singleOrNull()
                ?.config?.contains(SemanticsProperties.Disabled) == false
        }
        compose.onNode(qwen).performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Qwen 2.5 1.5B") and isSelected())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tab(name: String) =
        compose.onNode(hasText(name) and hasClickAction()).performClick()
}
