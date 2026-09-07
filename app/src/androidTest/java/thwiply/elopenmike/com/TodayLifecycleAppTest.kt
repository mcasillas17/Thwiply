package thwiply.elopenmike.com

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real activity, Hilt and Room. Proves that making collection lifecycle-aware did not cost
 * any visible state across a tab change, an activity recreation, or a background/resume
 * cycle, and that the Lab's own top-foreground gate still reports its state after a resume.
 * No model fixture or network request is involved.
 */
@RunWith(AndroidJUnit4::class)
class TodayLifecycleAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun todayKeepsItsTasksAndFilterAcrossTabsRecreationAndBackgrounding() {
        val title = "FND-03 manual ${System.nanoTime()}"
        compose.onNodeWithContentDescription("Add task").performClick()
        compose.onNodeWithText("Task description").performTextInput(title)
        compose.onNodeWithText("Add Task").performClick()
        awaitTitle(title)

        selectFilter("Notifications")
        compose.onNode(hasText("Lab") and hasClickAction()).performClick()
        compose.onNode(hasText("Today") and hasClickAction()).performClick()
        // The filter is view-model-owned, so it survives whether the observation was kept
        // across the tab change or released and reloaded.
        awaitFilter("Notifications").assertIsSelected()
        awaitTitle(title, expectVisible = false)

        compose.activityRule.scenario.recreate()
        awaitFilter("Notifications").assertIsSelected()

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitFilter("Notifications").assertIsSelected()
        selectFilter("All")
        awaitTitle(title)
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    /**
     * The filter row is composed only while Today has content, and a reload after the
     * subscription grace briefly shows the loading state, so wait for the chip rather than
     * assuming the transition finished inside that window.
     */
    private fun awaitFilter(label: String) = compose.run {
        waitUntil(10_000) {
            onAllNodes(hasText(label) and isSelectable()).fetchSemanticsNodes().isNotEmpty()
        }
        onNode(hasText(label) and isSelectable())
    }

    private fun selectFilter(label: String) = awaitFilter(label).performClick()

    @Test
    fun backgroundingAndResumingTheLabKeepsItsProviderStateVisible() {
        compose.onNode(hasText("Lab") and hasClickAction()).performClick()
        val missing = compose.activity.getString(R.string.lab_missing)
        compose.onNodeWithText(missing).assertIsDisplayed()

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(missing).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(missing).assertIsDisplayed()
        compose.onNodeWithText("Thwip Test").performScrollTo().assertIsNotEnabled()
    }

    private fun awaitTitle(title: String, expectVisible: Boolean = true) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() == expectVisible
        }
    }
}
