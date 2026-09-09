package thwiply.elopenmike.com

import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Parcel
import androidx.compose.ui.semantics.SemanticsProperties
import thwiply.elopenmike.com.domain.triage.TriageItem
import android.view.Window
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.core.view.descendants
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import thwiply.elopenmike.com.data.preferences.ThemeMode

/** Real Activity/window/IME. Run on each API, navigation mode and window configuration. */
@RunWith(AndroidJUnit4::class)
class AdaptiveWindowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var preferenceFile: File
    private var originalPreferences: ByteArray? = null

    @Before fun rememberPreferences() {
        if (InstrumentationRegistry.getArguments().getString("fnd04Orientation") == "landscape") {
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
        }
        preferenceFile = File(compose.activity.noBackupFilesDir, "app-preferences")
        originalPreferences = preferenceFile.takeIf { it.exists() }?.readBytes()
        compose.waitUntil(5_000) { compose.activity.themeManager.preferences.value.values != null }
    }

    @After fun restorePreferences() {
        if (!::preferenceFile.isInitialized) return
        originalPreferences?.let(preferenceFile::writeBytes) ?: run {
            if (preferenceFile.exists()) check(preferenceFile.delete())
        }
        runBlocking { compose.activity.themeManager.reloadPreferences() }
    }

    @Test fun explicitThemeControlsBothSystemBarIconAppearancesAfterRecreation() {
        for (theme in listOf(ThemeMode.DARK, ThemeMode.LIGHT)) {
            runBlocking { compose.activity.themeManager.setThemeMode(theme) }
            compose.waitUntil(5_000) { compose.activity.themeManager.themeMode.value == theme }
            compose.waitForIdle()
            assertBarAppearance(theme == ThemeMode.LIGHT)
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            assertBarAppearance(theme == ThemeMode.LIGHT)
            compose.onNodeWithContentDescription("Add task").performClick()
            compose.runOnIdle {
                val window = dialogWindow()
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                assertEquals(theme == ThemeMode.LIGHT, controller.isAppearanceLightStatusBars)
                assertEquals(theme == ThemeMode.LIGHT, controller.isAppearanceLightNavigationBars)
                assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
                    window.attributes.layoutInDisplayCutoutMode)
                assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
                    window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                assertEquals(window.windowManager.currentWindowMetrics.bounds.height(), window.decorView.height)
                assertEquals(android.graphics.PixelFormat.TRANSPARENT, window.decorView.background.opacity)
            }
            assertDialogChromeMatchesBackdrop()
            compose.onNodeWithText("Cancel").performScrollTo().performClick()
        }
    }

    @Test fun shellUsesFullWindowButKeepsControlsInsideSystemInsets() {
        val decor = compose.activity.window.decorView
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals("root must draw behind system bars", decor.height.toFloat(), root.height, 1f)
        val insets = requireNotNull(ViewCompat.getRootWindowInsets(decor))
            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        for (tab in listOf("Today", "Lab", "Settings")) {
            val bounds = compose.onNode(hasText(tab) and hasClickAction()).fetchSemanticsNode().boundsInRoot
            assertTrue("$tab under navigation bar", bounds.bottom <= decor.height - insets.bottom + 1)
            assertTrue("$tab under left cutout", bounds.left >= insets.left - 1)
            assertTrue("$tab under right cutout", bounds.right <= decor.width - insets.right + 1)
        }
        val add = compose.onNodeWithContentDescription("Add task").fetchSemanticsNode().boundsInRoot
        assertTrue("Add task under status bar", add.top >= insets.top)
        val heading = compose.onNode(hasText("Today") and !hasClickAction()).fetchSemanticsNode().boundsInRoot
        with(compose.density) {
            assertEquals("status inset must be owned exactly once",
                insets.top + 12.dp.toPx(), heading.top, 1f)
        }
        assertEquals("safe pane must not duplicate native insets",
            (decor.height - insets.top - insets.bottom).toFloat(),
            compose.onNodeWithTag("adaptive-pane").fetchSemanticsNode().boundsInRoot.height, 1f)
    }

    @Test fun taskEntryRestoresWhileImeActionsRemainReachable() {
        val activityHeight = compose.onNodeWithTag("adaptive-pane").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithContentDescription("Add task").performClick()
        compose.onNodeWithText("Task description").performScrollTo().performTextInput("Synthetic draft")
        compose.onNodeWithText("Synthetic draft").assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Synthetic draft").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Synthetic draft").assertExists()
        val window = compose.runOnIdle { dialogWindow() }
        setDialogIme(window, visible = false)
        val pane = compose.onNode(hasTestTag("adaptive-pane") and hasAnyAncestor(isDialog()))
        val normalHeight = pane.fetchSemanticsNode().boundsInRoot.height
        val normalDialogHeight = compose.onNodeWithTag("adaptive-dialog").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithText("Task description").performScrollTo().performClick()
        setDialogIme(window, visible = true)
        assertAboveDialogIme(compose.onNodeWithText("Task description").performScrollTo(), window)
        compose.onNodeWithText("Notes (optional)").performScrollTo().performClick()
            .performTextInput("Synthetic notes")
        assertAboveDialogIme(compose.onNodeWithText("Notes (optional)").performScrollTo(), window)
        compose.onNodeWithText("Task description").performScrollTo()
            .performTextReplacement("x".repeat(TriageItem.MAX_DISPLAY_TITLE_LENGTH + 1))
        val error = compose.activity.getString(R.string.task_draft_limit, TriageItem.MAX_DISPLAY_TITLE_LENGTH)
        val errorNode = compose.onNodeWithText(error, useUnmergedTree = true)
        // The editor's nested ScrollBy cannot move its supporting text. Use the form's
        // scroll action, equivalent to dragging the padding outside the text editor.
        val errorBottom = with(compose.density) { errorNode.getUnclippedBoundsInRoot().bottom.toPx() }
        val dialogBottom = compose.onNodeWithTag("adaptive-dialog").fetchSemanticsNode().boundsInRoot.bottom
        compose.onNode(hasScrollAction() and !hasSetTextAction() and hasAnyAncestor(isDialog()))
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {
                it(0f, (errorBottom - dialogBottom).coerceAtLeast(0f))
            }
        assertAboveDialogIme(errorNode, window)
        assertAboveDialogIme(compose.onNodeWithText("High priority").performScrollTo(), window)
        assertAboveDialogIme(compose.onNodeWithText("Add Task").performScrollTo().assertIsNotEnabled(), window)
        assertAboveDialogIme(compose.onNodeWithText("Cancel").performScrollTo(), window)
        compose.onNodeWithText("Task description").performScrollTo().performTextReplacement("Synthetic draft")
        setDialogIme(window, visible = false)
        assertEquals("dialog pane kept an IME-sized gap", normalHeight, pane.fetchSemanticsNode().boundsInRoot.height, 1f)
        assertEquals("dialog did not return to its normal constraints", normalDialogHeight,
            compose.onNodeWithTag("adaptive-dialog").fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Add task").assertIsDisplayed()
        assertEquals(activityHeight, compose.onNodeWithTag("adaptive-pane").fetchSemanticsNode().boundsInRoot.height, 1f)
    }

    @Test fun tappingOutsideTheAdaptiveDialogStillDismissesIt() {
        compose.onNodeWithContentDescription("Add task").performClick()
        compose.onNode(isDialog()).performTouchInput { click(Offset(4f, 4f)) }
        compose.onNodeWithText("Task description").assertDoesNotExist()
    }

    @Test fun oversizedTaskDraftKeepsActualActivitySavedStateBoundedAndCannotSubmit() {
        compose.onNodeWithContentDescription("Add task").performClick()
        val oversized = "Keep this prefix" + " ".repeat(300_000) + "important suffix"
        compose.onNodeWithText("Task description").performTextReplacement(oversized)
        compose.onNodeWithText("Notes (optional)").performTextReplacement(oversized)
        compose.onNodeWithText("Cancel").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        setDialogIme(compose.runOnIdle { dialogWindow() }, visible = false)
        val beforeTitle = compose.onNodeWithText("Task description").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        val beforeNotes = compose.onNodeWithText("Notes (optional)").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertTrue("title paste was not applied: ${beforeTitle.take(30)}", beforeTitle.startsWith("Keep this prefix"))
        assertTrue("notes paste was not applied: ${beforeNotes.take(30)}", beforeNotes.startsWith("Keep this prefix"))
        val state = Bundle()
        compose.runOnUiThread {
            InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(compose.activity, state)
        }
        val parcel = Parcel.obtain()
        val savedBytes = try {
            parcel.writeBundle(state)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
        android.util.Log.i("FND04Evidence", "task-draft savedBytes=$savedBytes")
        assertTrue("task draft inflated real Activity saved state to $savedBytes bytes", savedBytes < 128 * 1024)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Task description").fetchSemanticsNodes().singleOrNull()
                ?.config?.get(SemanticsProperties.EditableText)?.text == beforeTitle &&
                compose.onAllNodesWithText("Notes (optional)").fetchSemanticsNodes().singleOrNull()
                    ?.config?.get(SemanticsProperties.EditableText)?.text == beforeNotes
        }
        val title = compose.onNodeWithText("Task description").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        val notes = compose.onNodeWithText("Notes (optional)").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertTrue(title.length <= TriageItem.MAX_DISPLAY_TITLE_LENGTH + 1)
        assertTrue(notes.length <= TriageItem.MAX_DISPLAY_SUMMARY_LENGTH + 1)
        assertEquals("title changed on recreation", beforeTitle, title)
        assertEquals("notes changed on recreation", beforeNotes, notes)
        for (limit in listOf(TriageItem.MAX_DISPLAY_TITLE_LENGTH, TriageItem.MAX_DISPLAY_SUMMARY_LENGTH)) {
            compose.onNodeWithText(compose.activity.getString(R.string.task_draft_truncated, limit + 1)).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
            )
        }
        compose.onNodeWithText("Add Task").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Task description").performScrollTo().performTextReplacement("Ordinary draft")
        compose.onNodeWithText("Add Task").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Notes (optional)").performScrollTo().performTextReplacement("Ordinary notes")
        compose.onNodeWithText("Add Task").performScrollTo().assertIsEnabled()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Ordinary draft").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("Ordinary notes").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Ordinary draft").assertExists()
        compose.onNodeWithText("Ordinary notes").assertExists()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
    }

    private fun assertBarAppearance(light: Boolean) = compose.runOnIdle {
        val window = compose.activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        assertEquals("status bar icons", light, controller.isAppearanceLightStatusBars)
        assertEquals("navigation bar icons", light, controller.isAppearanceLightNavigationBars)
    }

    private fun dialogWindow(): Window = WindowInspector.getGlobalWindowViews().asSequence()
        .flatMap { root -> sequenceOf(root) + ((root as? ViewGroup)?.descendants ?: emptySequence()) }
        .filterIsInstance<DialogWindowProvider>()
        .map { it.window }
        .single()

    private fun assertDialogChromeMatchesBackdrop() {
        val (position, width, top) = compose.runOnIdle {
            val decor = dialogWindow().decorView
            val position = IntArray(2).also(decor::getLocationOnScreen)
            val insets = requireNotNull(ViewCompat.getRootWindowInsets(decor))
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).top
            // A physical camera cutout is not an app-drawn chrome pixel.
            val cutoutAtSample = insets.displayCutout?.boundingRects
                ?.any { it.contains(decor.width / 2, top / 2) } == true
            Triple(position, decor.width, if (cutoutAtSample) 0 else top)
        }
        if (top == 0) return
        val frame = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            val bar = frame.getPixel(position[0] + width / 2, position[1] + top / 2)
            val backdrop = frame.getPixel(position[0] + width / 2, position[1] + top + 2)
            for (channel in listOf<(Int) -> Int>(android.graphics.Color::red, android.graphics.Color::green, android.graphics.Color::blue)) {
                assertEquals("dialog status chrome replaced the dimmed app backdrop",
                    channel(backdrop).toFloat(), channel(bar).toFloat(), 2f)
            }
        } finally {
            frame.recycle()
        }
    }

    private fun setDialogIme(window: Window, visible: Boolean) {
        compose.runOnIdle {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (visible) controller.show(WindowInsetsCompat.Type.ime()) else controller.hide(WindowInsetsCompat.Type.ime())
        }
        val pane = compose.onNode(hasTestTag("adaptive-pane") and hasAnyAncestor(isDialog()))
        compose.waitUntil(5_000) {
            val insets = ViewCompat.getRootWindowInsets(window.decorView) ?: return@waitUntil false
            val height = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val requestedState = if (visible) height > 0 && insets.isVisible(WindowInsetsCompat.Type.ime())
                else height == 0
            // Native target insets can arrive before Compose's animated padding settles.
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            val expectedHeight = (window.decorView.height - safe.top - safe.bottom).toFloat()
            requestedState && kotlin.math.abs(pane.fetchSemanticsNode().boundsInRoot.height - expectedHeight) <= 1f
        }
        compose.waitForIdle()
    }

    private fun assertAboveDialogIme(node: SemanticsNodeInteraction, window: Window) {
        val clipped = node.fetchSemanticsNode().boundsInRoot
        val unClipped = node.getUnclippedBoundsInRoot()
        assertTrue("dialog control not visible: clipped=$clipped raw=$unClipped " +
            "pane=${compose.onNodeWithTag("adaptive-dialog").fetchSemanticsNode().boundsInRoot} " +
            "ime=${ViewCompat.getRootWindowInsets(window.decorView)?.getInsets(WindowInsetsCompat.Type.ime())}",
            clipped.width > 0 && clipped.height > 0)
        node.assertIsDisplayed()
        with(compose.density) {
            assertEquals("dialog control width clipped", (unClipped.right - unClipped.left).toPx(), clipped.width, 1f)
            assertEquals("dialog control height clipped", (unClipped.bottom - unClipped.top).toPx(), clipped.height, 1f)
        }
        val insets = requireNotNull(ViewCompat.getRootWindowInsets(window.decorView))
        val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        assertTrue("actual dialog-window IME is required", keyboard > 0)
        val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val bounds = node.fetchSemanticsNode().boundsInWindow
        assertTrue("dialog control behind IME: $bounds", bounds.bottom <= window.decorView.height - keyboard + 1)
        assertTrue("dialog control behind cutout", bounds.top >= safe.top - 1 &&
            bounds.left >= safe.left - 1 && bounds.right <= window.decorView.width - safe.right + 1)
    }
}
