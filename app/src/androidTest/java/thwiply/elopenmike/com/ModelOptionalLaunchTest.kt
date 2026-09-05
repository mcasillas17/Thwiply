package thwiply.elopenmike.com

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real activity, Hilt and Room; no model fixture or network request. */
@RunWith(AndroidJUnit4::class)
class ModelOptionalLaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun missingModelAllowsManualTaskSettingsAndRecreation() {
        // Fail explicitly on a device with installed weights instead of silently
        // treating initialization/failure as evidence of the missing-model case.
        compose.onNode(hasText("Lab") and hasClickAction()).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.lab_missing)).assertIsDisplayed()
        compose.onNode(hasText("Today") and hasClickAction()).performClick()
        val title = "FND-02 manual ${System.nanoTime()}"
        compose.onNodeWithContentDescription("Add task").performClick()
        compose.onNodeWithText("Task description").performTextInput(title)
        compose.onNodeWithText("Add Task").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNode(hasText("Settings") and hasClickAction()).performClick()
        compose.onNodeWithText("Delete notification data and rules")
            .performScrollTo().performClick()
        compose.onNodeWithText("Delete notification data?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNode(hasText("Lab") and hasClickAction()).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.lab_missing)).assertIsDisplayed()
        compose.onNodeWithText("Thwip Test").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun setupBackAndActivityRecreationKeepTheOriginalTab() {
        compose.onNode(hasText("Lab") and hasClickAction()).performClick()
        compose.onNodeWithText("Model setup").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Return to app").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(hasText("Lab") and hasClickAction()).assertIsSelected()
        repeat(2) {
            compose.onNodeWithText("Model setup").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Return to app").performClick()
        }
        compose.onNode(hasText("Settings") and hasClickAction()).performClick()
        compose.onNodeWithText("Model setup").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(hasText("Settings") and hasClickAction()).assertIsSelected()
    }
}
