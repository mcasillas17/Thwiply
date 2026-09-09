package thwiply.elopenmike.com

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.window.layout.FoldingFeature
import android.graphics.Rect
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import thwiply.elopenmike.com.data.preferences.*
import thwiply.elopenmike.com.llm.model.DownloadState
import thwiply.elopenmike.com.llm.model.ArtifactDefect
import thwiply.elopenmike.com.llm.model.ModelArtifactState
import thwiply.elopenmike.com.llm.model.ModelPreset
import thwiply.elopenmike.com.llm.provider.*
import thwiply.elopenmike.com.ui.main.*
import thwiply.elopenmike.com.ui.onboarding.OnboardingContent
import thwiply.elopenmike.com.ui.playground.LabReadinessCard
import thwiply.elopenmike.com.ui.theme.ThwiplyTheme

/** Deterministic layout states, real edge-to-edge window; never an SDK/model operation. */
@RunWith(AndroidJUnit4::class)
class AdaptiveContentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun edgeToEdge() {
        if (InstrumentationRegistry.getArguments().getString("fnd04Orientation") == "landscape") {
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
        }
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            compose.activity.window.attributes = compose.activity.window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
    }

    @Test fun resizingAndHingeUpdatesKeepOneBoundedContentInstance() {
        var width by mutableStateOf(1000.dp)
        var folds by mutableStateOf(emptyList<IntRect>())
        var entries = 0
        var disposals = 0
        compose.setContent {
            Box(Modifier.requiredSize(width, 500.dp)) {
                CompositionLocalProvider(LocalFoldingBounds provides folds) {
                    AppViewport {
                        DisposableEffect(Unit) { entries++; onDispose { disposals++ } }
                        val draft by rememberSaveable { mutableStateOf("Retained") }
                        Text(draft, Modifier.testTag("retained-draft"))
                    }
                }
            }
        }
        assertPaneWidth(1000f)
        compose.runOnIdle { width = 700.dp }
        assertPaneWidth(700f)
        compose.runOnIdle { width = 320.dp }
        assertPaneWidth(320f)
        compose.runOnIdle {
            width = 700.dp
            with(compose.density) {
                folds = listOf(IntRect(340.dp.roundToPx(), 0, 360.dp.roundToPx(), 2000.dp.roundToPx()))
            }
        }
        val pane = compose.onNodeWithTag("adaptive-pane").fetchSemanticsNode().boundsInWindow
        with(compose.density) {
            assertTrue("pane crosses fold", pane.right <= 340.dp.toPx() + 1 || pane.left >= 360.dp.toPx() - 1)
        }
        compose.onNodeWithTag("retained-draft").assertTextEquals("Retained")
        compose.runOnIdle { assertEquals(1, entries); assertEquals(0, disposals) }
    }

    @Test fun onlySeparatingOrOccludingFeaturesMoveTheAppAndItsOpenDialog() {
        val width = compose.activity.window.decorView.width
        val height = compose.activity.window.decorView.height
        val hinge = Rect(width / 2 - 10, 0, width / 2 + 10, height)
        var features by mutableStateOf(listOf(fold(hinge, separating = false, occluding = false)))
        compose.setContent {
            ThwiplyTheme {
                AppNavigation(
                    mainScreen = {
                        var dialog by rememberSaveable { mutableStateOf(false) }
                        Button(onClick = { dialog = true }) { Text("Open fixture dialog") }
                        if (dialog) AppAlertDialog(
                            onDismissRequest = { dialog = false },
                            title = { Text("Synthetic dialog") },
                            text = { Text("A retained dialog must avoid an occluding fold.") },
                            confirmButton = { Button(onClick = { dialog = false }) { Text("Done") } },
                        )
                    },
                    setupScreen = {},
                    foldingFeatures = features,
                )
            }
        }
        val initialPane = compose.onNodeWithTag("adaptive-pane").fetchSemanticsNode().boundsInWindow
        assertTrue("flat nonoccluding fold should not divide the app",
            initialPane.left < hinge.left && initialPane.right > hinge.right)
        compose.onNodeWithText("Open fixture dialog").performClick()
        for ((separating, occluding) in listOf(true to false, false to true)) {
            compose.runOnIdle { features = listOf(fold(hinge, separating, occluding)) }
            val dialog = compose.onNodeWithTag("adaptive-dialog").fetchSemanticsNode().boundsInWindow
            assertTrue("dialog intersects the fold", dialog.right <= hinge.left + 1 || dialog.left >= hinge.right - 1)
            compose.onNodeWithText("Synthetic dialog").assertExists()
        }
        compose.onNodeWithText("Done").performScrollTo().performClick()
    }

    @Test fun longSetupStatesKeepActionsReachableAtLargeText() {
        var state: DownloadState by mutableStateOf(DownloadState.Idle)
        var load: ModelArtifactState by mutableStateOf(ModelArtifactState.Ready(ModelPreset.QWEN_2_5_1_5B))
        var preferences by mutableStateOf(PreferenceState(AppPreferences()))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                Box(Modifier.requiredSize(320.dp, 440.dp)) {
                    ThwiplyTheme {
                        AppViewport {
                            OnboardingContent(
                                state, ProviderSelection(ModelProvider.QWEN), NanoState.Unavailable,
                                state is DownloadState.Downloading, {}, {}, {}, {}, {}, {}, null,
                                load, preferences = preferences,
                            )
                        }
                    }
                }
            }
        }
        for (download in listOf(
            DownloadState.Idle,
            DownloadState.Downloading(45),
            DownloadState.Error("Synthetic retryable setup failure. ".repeat(8)),
            DownloadState.Success,
        )) {
            compose.runOnIdle { state = download }
            val action = when (download) {
                is DownloadState.Downloading -> R.string.action_stop
                is DownloadState.Error -> R.string.setup_retry
                DownloadState.Success -> R.string.setup_return
                else -> R.string.setup_download
            }
            val button = compose.onNodeWithText(text(action)).performScrollTo()
            assertFullyVisible(button)
            assertTextNotEllipsized(text(action))
        }
        for (reason in PreferenceFailureReason.entries) {
            compose.runOnIdle {
                state = DownloadState.Idle
                preferences = PreferenceState(failure = PreferenceFailure(reason, IllegalStateException("Synthetic")))
            }
            if (reason != PreferenceFailureReason.WRITE) {
                assertFullyVisible(compose.onNodeWithText(text(R.string.preferences_retry)).performScrollTo())
            }
            assertFullyVisible(compose.onNodeWithText(text(R.string.setup_download)).performScrollTo())
        }
        // The rejected states render the longest copy and the most recovery controls, so they
        // are the strongest case for "actions stay reachable at large text".
        for (model in listOf(
            ModelArtifactState.Verifying,
            ModelArtifactState.Failed(IllegalStateException()),
            ModelArtifactState.Corrupt(ModelPreset.QWEN_2_5_1_5B, ArtifactDefect.DIGEST_MISMATCH),
        )) {
            compose.runOnIdle { load = model }
            assertFullyVisible(compose.onNodeWithContentDescription(text(R.string.setup_back)))
        }
    }

    @Test fun everyLabReadinessStateRetainsAnUnclippedSetupAction() {
        var state: ProviderReadiness by mutableStateOf(ProviderReadiness.Missing)
        var provider by mutableStateOf(ModelProvider.QWEN)
        var busy by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                Box(Modifier.requiredSize(320.dp, 440.dp)) {
                    ThwiplyTheme {
                        AppViewport {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                LabReadinessCard(state, provider, busy, {}, {})
                            }
                        }
                    }
                }
            }
        }
        for (selected in ModelProvider.entries) {
            for (readiness in listOf(
                ProviderReadiness.Missing, ProviderReadiness.Checking,
                ProviderReadiness.Initializing, ProviderReadiness.NeedsInitialization,
                ProviderReadiness.Unavailable, ProviderReadiness.Downloadable,
                ProviderReadiness.Downloading, ProviderReadiness.Failed(InferenceFailure(FailureKind.RUNTIME)),
            )) {
                compose.runOnIdle {
                    provider = selected
                    state = readiness
                    busy = readiness == ProviderReadiness.Initializing || readiness == ProviderReadiness.Downloading
                }
                assertFullyVisible(compose.onNodeWithText(text(R.string.setup_open)).performScrollTo())
                assertTextNotEllipsized(text(readiness.message(selected, busy)))
            }
        }
    }

    /**
     * Parent capture contract: -e class ...AdaptiveContentTest#deviceEvidenceScenario
     * -e fnd04Scenario setup-error|setup-downloading|nano-unavailable|nano-downloading|
     * nano-downloadable|nano-ready|preferences-error -e fnd04Theme dark|light
     * -e fnd04HoldSeconds 20. Frames are actual UI, not generated screenshots.
     */
    @Test fun deviceEvidenceScenario() {
        val args = InstrumentationRegistry.getArguments()
        val scenario = args.getString("fnd04Scenario") ?: "setup-error"
        val nano = scenario.startsWith("nano-")
        val state: DownloadState = when (scenario) {
            "setup-downloading" -> DownloadState.Downloading(45)
            "setup-error" -> DownloadState.Error("Synthetic setup failure. No model was downloaded.")
            else -> DownloadState.Idle
        }
        val nanoState: NanoState = when (scenario) {
            "nano-downloading" -> NanoState.Downloading
            "nano-downloadable" -> NanoState.Downloadable
            "nano-ready" -> NanoState.Ready
            else -> NanoState.Unavailable
        }
        compose.setContent {
            ThwiplyTheme(if (args.getString("fnd04Theme") == "dark") ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AppViewport {
                    OnboardingContent(
                        state, ProviderSelection(if (nano) ModelProvider.GEMINI_NANO else ModelProvider.QWEN),
                        nanoState, scenario.endsWith("downloading"), {}, {}, {}, {}, {}, {}, null,
                        preferences = if (scenario == "preferences-error")
                            PreferenceState(failure = PreferenceFailure(PreferenceFailureReason.READ, IllegalStateException("Synthetic")))
                        else PreferenceState(AppPreferences()),
                    )
                }
                }
            }
        }
        val action = when {
            scenario.endsWith("downloading") -> R.string.action_stop
            scenario == "preferences-error" -> R.string.preferences_retry
            nano -> R.string.nano_check
            else -> R.string.setup_retry
        }
        compose.onNodeWithText(text(action)).performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        android.util.Log.i("FND04Evidence", "READY scenario=$scenario")
        val seconds = args.getString("fnd04HoldSeconds")?.toLongOrNull()?.coerceIn(0, 60) ?: 0
        if (seconds > 0) Thread.sleep(seconds * 1000)
    }

    private fun assertPaneWidth(windowWidthDp: Float) {
        val native = requireNotNull(ViewCompat.getRootWindowInsets(compose.activity.window.decorView))
            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val horizontalDp = with(compose.density) { (native.left + native.right).toDp().value }
        val expected = minOf(720f, windowWidthDp - horizontalDp)
        val bounds = compose.onNodeWithTag("adaptive-pane").getUnclippedBoundsInRoot()
        assertEquals(expected, (bounds.right - bounds.left).value, 1f)
    }

    private fun assertFullyVisible(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val unclipped = node.getUnclippedBoundsInRoot()
        with(compose.density) {
            assertEquals((unclipped.right - unclipped.left).toPx(), bounds.width, 1f)
            assertEquals((unclipped.bottom - unclipped.top).toPx(), bounds.height, 1f)
        }
    }

    private fun assertTextNotEllipsized(value: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(value, useUnmergedTree = true).performSemanticsAction(
            SemanticsActions.GetTextLayoutResult,
        ) { it(results) }
        assertTrue("text layout missing", results.isNotEmpty())
        assertTrue(
            "text truncated: $value ${results.map { "size=${it.size} paragraph=${it.multiParagraph.width}x${it.multiParagraph.height} lines=${it.lineCount}" }}",
            results.all { result ->
                // A paragraph may retain wider constraints than the final Text box.
                // Check the visible line extents, not that paragraph's reserved width.
                (0 until result.lineCount).all { line ->
                    !result.isLineEllipsized(line) &&
                        result.getLineRight(line) <= result.size.width + 1 &&
                        result.getLineBottom(line) <= result.size.height + 1
                }
            },
        )
    }

    private fun text(id: Int) = compose.activity.getString(id)

    private fun fold(rect: Rect, separating: Boolean, occluding: Boolean) = object : FoldingFeature {
        override val bounds = rect
        override val isSeparating = separating
        override val occlusionType = if (occluding) FoldingFeature.OcclusionType.FULL else FoldingFeature.OcclusionType.NONE
        override val orientation = FoldingFeature.Orientation.VERTICAL
        override val state = if (separating) FoldingFeature.State.HALF_OPENED else FoldingFeature.State.FLAT
    }
}
