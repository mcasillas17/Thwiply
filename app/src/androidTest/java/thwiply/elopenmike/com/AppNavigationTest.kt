package thwiply.elopenmike.com

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import okhttp3.OkHttpClient
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import thwiply.elopenmike.com.domain.cleanup.NotificationDataCleanupCoordinator
import thwiply.elopenmike.com.data.local.ThwiplyDatabase
import thwiply.elopenmike.com.data.repository.*
import thwiply.elopenmike.com.data.preferences.*
import thwiply.elopenmike.com.llm.engine.*
import thwiply.elopenmike.com.llm.model.*
import thwiply.elopenmike.com.llm.provider.*
import thwiply.elopenmike.com.ui.main.*
import thwiply.elopenmike.com.ui.onboarding.*
import thwiply.elopenmike.com.ui.playground.*
import thwiply.elopenmike.com.ui.settings.*
import thwiply.elopenmike.com.ui.theme.*
import thwiply.elopenmike.com.ui.today.*

/** Real navigation/screens/Room; tiny model and fake native engine, never a model download. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AppNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var database: ThwiplyDatabase
    private lateinit var modelDirectory: File
    private lateinit var setup: OnboardingViewModel
    private lateinit var restoration: StateRestorationTester
    private val downloads = MutableStateFlow<DownloadState>(DownloadState.Idle)
    private var initFails = false
    @Volatile private var downloadStarts = 0
    @Volatile private var generations = 0
    private val viewModels = mutableListOf<ViewModel>()
    private var initializationGate: CountDownLatch? = null
    private val clock: Clock = Clock.systemUTC()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadGate: CountDownLatch? = null
    private var metadataScheduler: TestCoroutineScheduler? = null
    private lateinit var coordinator: InferenceCoordinator
    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var constrained = false
    private var theme = ThemeMode.SYSTEM

    /** Optional bounded hold lets the parent capture actual fake-provider UI, never real inference. */
    @Test fun deviceEvidenceScenario() {
        val args = InstrumentationRegistry.getArguments()
        val scenario = args.getString("fnd04Scenario") ?: "lab-ready"
        theme = if (args.getString("fnd04Theme") == "dark") ThemeMode.DARK else ThemeMode.LIGHT
        initFails = scenario == "lab-error"
        if (scenario == "lab-initializing") initializationGate = CountDownLatch(1)
        launch(if (scenario == "lab-missing") "missing" else "installed")
        when {
            scenario.startsWith("lab-") -> {
                tab("Lab")
                when (scenario) {
                    "lab-error" -> awaitText(text(R.string.lab_initialization_failed)).performScrollTo()
                    "lab-initializing" -> compose.onAllNodesWithText(text(R.string.lab_initializing))
                        .onFirst().performScrollTo()
                    "lab-missing" -> compose.onNodeWithText(text(R.string.lab_missing)).performScrollTo()
                    else -> {
                        readyButton().performScrollTo()
                        if (scenario == "lab-ime") {
                            compose.onNodeWithText(text(R.string.lab_input_label)).performScrollTo()
                                .performClick().performTextReplacement("Synthetic keyboard prompt")
                            compose.runOnIdle {
                                WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView)
                                    .show(WindowInsetsCompat.Type.ime())
                            }
                            compose.waitUntil(5_000) {
                                ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                            }
                            readyButton().performScrollTo()
                            assertTabsAboveIme()
                        }
                    }
                }
            }
            scenario == "settings" -> {
                tab("Settings")
                compose.onNodeWithText("Model setup").performScrollTo()
            }
            scenario == "today-ime" -> {
                compose.onNodeWithContentDescription("Add task").performClick()
                compose.onNodeWithText("Task description").performClick().performTextInput("Synthetic manual draft")
                compose.onNodeWithText("Add Task").performScrollTo()
            }
            else -> addManualTask()
        }
        compose.waitForIdle()
        android.util.Log.i("FND04Evidence", "READY scenario=$scenario")
        val seconds = args.getString("fnd04HoldSeconds")?.toLongOrNull()?.coerceIn(0, 60) ?: 0
        if (seconds > 0) Thread.sleep(seconds * 1000)
    }

    @After fun tearDown() {
        initializationGate?.countDown()
        downloadGate?.countDown()
        metadataScheduler?.runCurrent()
        compose.runOnIdle { viewModels.forEach { it.viewModelScope.cancel() } }
        providerScope.cancel()
        if (::database.isInitialized) database.close()
        if (::modelDirectory.isInitialized) modelDirectory.deleteRecursively()
        applicationScope.cancel()
    }

    @Test fun missingModelKeepsManualWorkAndSettingsAvailable() = unavailableLaunch("missing")
    @Test fun fakeHostMatchesProductionWindowConfiguration() {
        launch()
        compose.runOnIdle {
            assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                compose.activity.window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
            assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
                compose.activity.window.attributes.layoutInDisplayCutoutMode)
        }
    }
    @Test fun labDraftAndScrollSurviveTabSetupAndRestoration() {
        launch("installed")
        tab("Lab")
        readyButton()
        val draft = "Synthetic draft retained while resizing"
        compose.onNodeWithText(text(R.string.lab_input_label)).performScrollTo()
            .performTextReplacement(draft)
        // Text replacement focuses the field and opens the real IME. Compare saved
        // scroll positions in the same settled viewport, not during its dismissal.
        compose.runOnIdle {
            WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom == 0
        }
        compose.onNodeWithText("Output Stream").performScrollTo()
        compose.onNodeWithText("Output Stream").assertIsDisplayed()
        val savedScroll = labScrollPosition()
        assertTrue("fixture must be scrolled before restoration", savedScroll > 0)
        tab("Settings")
        tab("Lab")
        // Re-entry starts an asynchronous readiness operation. Its temporary busy
        // message is not part of the stable layout whose saved offset we compare.
        readyButton()
        assertEquals("tab return lost Lab scroll", savedScroll, labScrollPosition(), 1f)
        compose.onNodeWithText("Output Stream").assertIsDisplayed()
        compose.onNodeWithText(draft).assertExists()
        restoration.emulateSavedInstanceStateRestore()
        readyButton()
        assertEquals("saved-state restoration lost Lab scroll", savedScroll, labScrollPosition(), 1f)
        compose.onNodeWithText("Output Stream").assertIsDisplayed()
        compose.onNodeWithText(draft).assertExists()
        compose.onNodeWithText(text(R.string.provider_select)).performScrollTo()
        val setupScroll = labScrollPosition()
        compose.onNodeWithText(text(R.string.provider_select)).performClick()
        back()
        readyButton()
        assertEquals("setup return lost Lab scroll", setupScroll, labScrollPosition(), 1f)
        compose.onNodeWithText(draft).assertExists()
    }

    private fun labScrollPosition() = compose.onNodeWithTag("lab-scroll").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun shortLargeTextLabKeepsRunActionFullyReachable() {
        constrained = true
        launch("installed")
        tab("Lab")
        readyButton().performScrollTo().assertIsDisplayed()
        assertFullyVisible(compose.onNodeWithText(text(R.string.lab_run)))
        compose.onNodeWithText(text(R.string.lab_json_mode)).performScrollTo()
        assertFullyVisible(compose.onNodeWithText(text(R.string.lab_json_mode)))
    }

    @Test fun shortLargeTextSettingsKeepsThemeChoicesReadable() {
        constrained = true
        launch()
        tab("Settings")
        for (label in listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark)) {
            val node = compose.onNodeWithText(text(label), useUnmergedTree = true).performScrollTo()
            assertFullyVisible(node)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            assertTrue("theme label clipped: ${text(label)}", layouts.all { layout ->
                (0 until layout.lineCount).all {
                    !layout.isLineEllipsized(it) &&
                        layout.getLineRight(it) <= layout.size.width + 1 &&
                        layout.getLineBottom(it) <= layout.size.height + 1
                }
            })
        }
    }

    @Test fun taskDraftSurvivesSavedStateRestoration() {
        launch()
        compose.onNodeWithContentDescription("Add task").performClick()
        compose.onNodeWithText("Task description").performTextInput("Synthetic unsaved title")
        compose.onNodeWithText("Notes (optional)").performTextInput("Synthetic unsaved notes")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Synthetic unsaved title").assertExists()
        compose.onNodeWithText("Synthetic unsaved notes").assertExists()
        compose.onNodeWithText("Add Task").performClick()
        awaitText("Synthetic unsaved title").assertIsDisplayed()
    }

    @Test fun realImeWindowKeepsLabRunFullyReachableAndClosesWithoutGap() {
        // Use the real window: a forced small Compose box still gets the full-size
        // keyboard's insets, unlike an actual landscape or resized Android window.
        launch("installed")
        tab("Lab")
        readyButton()
        val fullHeight = compose.onNodeWithTag("adaptive-pane").fetchSemanticsNode().boundsInRoot.height
        val input = compose.onNodeWithText(text(R.string.lab_input_label))
        input.performScrollTo().performClick()
        compose.runOnIdle {
            WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView)
                .show(WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        input.performTextReplacement("Synthetic keyboard draft")
        readyButton().performScrollTo()
        assertFullyVisible(readyButton())
        val keyboard = requireNotNull(ViewCompat.getRootWindowInsets(compose.activity.window.decorView))
            .getInsets(WindowInsetsCompat.Type.ime()).bottom
        val runBounds = readyButton().fetchSemanticsNode().boundsInWindow
        assertTrue("run action is behind the actual IME",
            runBounds.bottom <= compose.activity.window.decorView.height - keyboard + 1)
        assertTabsAboveIme()
        compose.runOnIdle {
            WindowInsetsControllerCompat(compose.activity.window, compose.activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == false
        }
        compose.onNodeWithText("Output Stream").performScrollTo()
        assertFullyVisible(compose.onNodeWithText("Output Stream"))
        compose.onNodeWithText("Synthetic keyboard draft").assertExists()
        assertEquals("IME dismissal left a gap", fullHeight,
            compose.onNodeWithTag("adaptive-pane").fetchSemanticsNode().boundsInRoot.height, 1f)
    }

    private fun assertFullyVisible(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val size = node.getUnclippedBoundsInRoot()
        with(compose.density) {
            assertEquals("clipped width", (size.right - size.left).toPx(), bounds.width, 1f)
            assertEquals("clipped height", (size.bottom - size.top).toPx(), bounds.height, 1f)
        }
    }

    private fun assertTabsAboveIme() {
        val decor = compose.activity.window.decorView
        val insets = requireNotNull(ViewCompat.getRootWindowInsets(decor))
        val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        assertTrue("keyboard must really be open", keyboard > 0)
        val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        for (label in listOf("Today", "Lab", "Settings")) {
            val tab = compose.onNode(hasText(label) and hasClickAction())
            assertFullyVisible(tab)
            val bounds = tab.fetchSemanticsNode().boundsInWindow
            assertTrue("$label behind actual IME: $bounds", bounds.bottom <= decor.height - keyboard + 1)
            assertTrue("$label under cutout", bounds.left >= safe.left - 1 && bounds.right <= decor.width - safe.right + 1)
        }
    }
    @Test fun settingsDisplaysPackagedVersion() {
        val expectedVersion = BuildConfig.VERSION_NAME
        
        launch()
        tab("Settings")
        compose.onNodeWithText(text(R.string.settings_version_label)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(expectedVersion).performScrollTo().assertIsDisplayed()
    }
    @Test fun partialDownloadKeepsManualWorkAndSettingsAvailable() = unavailableLaunch("partial")
    @Test fun truncatedModelKeepsManualWorkAndSettingsAvailable() = unavailableLaunch("truncated")
    @Test fun removedModelKeepsManualWorkAndSettingsAvailable() = unavailableLaunch("removed")

    @Test fun unreadableQwenMetadataKeepsManualWorkAndSettingsAvailable() {
        launch("unreadable")
        addManualTask()
        tab("Settings")
        compose.onNodeWithText("Delete notification data and rules").performScrollTo().assertIsEnabled()
        openSetup()
        compose.onNodeWithText(text(R.string.qwen_metadata_failed)).performScrollTo().assertIsDisplayed()
        back()
        tab("Today")
        awaitText("Synthetic manual task").assertIsDisplayed()
    }

    @Test fun pendingQwenMetadataNeverLabelsTheOutputPaneAsNano() {
        metadataScheduler = TestCoroutineScheduler()
        launch()
        tab("Lab")
        compose.waitUntil(5_000) { coordinator.busy.value }
        compose.onNodeWithText(text(R.string.nano_checking)).assertDoesNotExist()
        compose.onAllNodesWithText(text(R.string.qwen_metadata_checking)).assertCountEquals(2)
    }

    private fun unavailableLaunch(modelCondition: String) {
        launch(modelCondition)
        addManualTask()
        tab("Lab")
        compose.waitUntil(10_000) {
            coordinator.readiness.value == ProviderReadiness.Missing && !coordinator.busy.value &&
                ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom == 0
        }
        awaitText(text(R.string.lab_missing)).assertIsDisplayed()
        compose.onNodeWithText("Thwip Test").performScrollTo().assertIsNotEnabled()
        tab("Settings")
        openSetup()
        back()
        compose.onNodeWithText("Delete notification data and rules").performScrollTo().assertIsEnabled()
        tab("Today")
        awaitText("Synthetic manual task").assertIsDisplayed()
    }

    @Test fun installedStartupInitializesLabWithoutForcingSetup() {
        launch("installed")
        compose.onNodeWithContentDescription("Add task").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.setup_back)).assertDoesNotExist()
        tab("Lab")
        readyButton().performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5_000) { generations == 1 }
        awaitText("Synthetic local response").performScrollTo().assertIsDisplayed()
    }

    @Test fun failedEngineStillAllowsManualWorkAndRetry() {
        initFails = true
        launch("installed")
        tab("Lab")
        awaitText(text(R.string.lab_initialization_failed)).assertIsDisplayed()
        compose.onNodeWithText("Thwip Test").performScrollTo().assertIsNotEnabled()
        tab("Today")
        addManualTask()
        tab("Settings")
        openSetup()
        back()
        tab("Lab")
        compose.runOnIdle { initFails = false }
        compose.onNodeWithText(text(R.string.lab_retry)).performScrollTo().performClick()
        readyButton().performScrollTo().assertIsEnabled()
    }

    @Test fun initializingEngineDoesNotBlockManualWork() {
        initializationGate = CountDownLatch(1)
        launch("installed")
        tab("Lab")
        compose.onAllNodesWithText(text(R.string.lab_initializing)).onFirst().assertIsDisplayed()
        compose.onNodeWithText("Thwip Test").performScrollTo().assertIsNotEnabled()
        tab("Today")
        addManualTask()
        initializationGate!!.countDown()
        // A cancelled native initialization must unwind before another request can start.
        compose.waitUntil(10_000) { !coordinator.busy.value }
        tab("Lab")
        readyButton().performScrollTo().assertIsEnabled()
    }

    @Test fun failedSetupRetriesAndCompletionWaitsForExplicitReturn() {
        launch()
        tab("Settings")
        openSetup()
        compose.onNodeWithText(text(R.string.setup_download)).performScrollTo().performClick()
        compose.runOnIdle { downloads.value = DownloadState.Error("Synthetic setup failure") }
        compose.onNodeWithText("Synthetic setup failure").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_retry)).performScrollTo().performClick()
        compose.runOnIdle { downloads.value = DownloadState.Success }
        awaitText(text(R.string.setup_complete)).performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        awaitText(text(R.string.setup_complete)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_return)).performScrollTo().performClick()
        compose.onNode(hasText("Settings") and hasClickAction()).assertIsSelected()
        tab("Lab")
        readyButton().performScrollTo().assertIsEnabled()
        tab("Today")
        addManualTask()
        compose.runOnIdle { assertEquals(2, downloadStarts) }
    }

    @Test fun leavingAnActiveDownloadAndRepeatedSetupEntryRestoreTheShellTab() {
        launch()
        tab("Lab")
        openSetup()
        compose.onNodeWithText(text(R.string.setup_download)).performScrollTo().performClick()
        compose.runOnIdle { downloads.value = DownloadState.Downloading(35) }
        restoration.emulateSavedInstanceStateRestore()
        // Disposing setup cancels its owned work; returning requires another explicit download.
        awaitText(text(R.string.setup_download)).performScrollTo().assertIsEnabled()
        back()
        compose.onNode(hasText("Lab") and hasClickAction()).assertIsSelected()
        repeat(3) { openSetup(); back() }
        tab("Today")
        addManualTask()
        compose.runOnIdle { assertEquals(1, downloadStarts) }
    }

    @Test fun returningToLabWhileQwenDownloadUnwindsDoesNotReportNanoDownloading() {
        downloadGate = CountDownLatch(1)
        launch()
        tab("Lab")
        openSetup()
        compose.onNodeWithText(text(R.string.setup_download)).performScrollTo().performClick()
        compose.waitUntil(5_000) { downloadStarts == 1 }
        back()
        compose.onNode(hasText("Lab") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText(text(R.string.nano_downloading)).assertDoesNotExist()
        downloadGate!!.countDown()
    }

    private fun launch(modelCondition: String = "missing") {
        if (InstrumentationRegistry.getArguments().getString("fnd04Orientation") == "landscape") {
            compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) {
                compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
        }
        val context = compose.activity
        compose.runOnUiThread {
            context.enableEdgeToEdge()
            context.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            context.window.attributes = context.window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        modelDirectory = File(context.cacheDir, "fnd02-${System.nanoTime()}").also { it.mkdirs() }
        val preset = ModelPreset.QWEN_2_5_1_5B.copy(
            fileName = "fixture.litertlm", expectedBytes = 4,
            sha256 = MessageDigest.getInstance("SHA-256").digest("test".toByteArray())
                .joinToString("") { "%02x".format(it) },
        )
        if (modelCondition != "missing") File(modelDirectory, "active-model").writeText(preset.id)
        when (modelCondition) {
            "installed" -> File(modelDirectory, preset.fileName).writeText("test")
            "truncated" -> File(modelDirectory, preset.fileName).writeText("bad")
            "partial" -> File(modelDirectory, "${preset.fileName}.part").writeText("pa")
            "removed" -> File(modelDirectory, preset.fileName).apply {
                writeText("test")
                check(delete())
            }
            "unreadable" -> {
                File(modelDirectory, preset.fileName).writeText("test")
                check(File(modelDirectory, "active-model").setReadable(false, false))
            }
        }
        val client = OkHttpClient.Builder().addInterceptor {
            downloadStarts++
            check(downloadGate?.await(30, TimeUnit.SECONDS) == true)
            throw java.io.IOException("Synthetic stopped download")
        }.build()
        val models = metadataScheduler?.let {
            ModelManager(modelDirectory, client, listOf(preset), providerScope, StandardTestDispatcher(it))
        } ?: ModelManager(modelDirectory, client, listOf(preset))
        if (metadataScheduler == null) runBlocking { models.awaitLoaded() }
        database = Room.inMemoryDatabaseBuilder(context, ThwiplyDatabase::class.java).build()
        val lifecycle = RoomNotificationDataLifecycleRepository(database.dataLifecycleDao())
        val today = TodayViewModel(
            RoomTriageRepository(database.triageDao()),
            NotificationDataCleanupCoordinator(lifecycle, clock, applicationScope),
            clock,
        )
        val selection = ProviderSelectionRepository(File(modelDirectory, "provider"), providerScope, Dispatchers.IO)
        runBlocking { selection.awaitLoaded() }
        val preferences = AppPreferencesRepository(
            FilePreferenceStorage(File(modelDirectory, "preferences")), providerScope, Dispatchers.IO,
        )
        val settings = SettingsViewModel(ThemeManager(preferences, providerScope), models, selection)
        val deletion = NotificationDataSettingsViewModel(lifecycle)
        val engine = LlmEngineManager {
            object : ManagedEngine {
                override fun initialize() {
                    check(initializationGate?.await(30, TimeUnit.SECONDS) != false)
                    check(!initFails) { "Synthetic initialization failure" }
                }
                override fun close() = Unit
                override fun createConversation() = object : ManagedConversation {
                    override fun generate(prompt: String) = flow {
                        generations++
                        emit("Synthetic local response")
                    }
                    override fun close() = Unit
                }
            }
        }
        coordinator = InferenceCoordinator(
            selection, models, engine, NanoClientFactory { error("Nano not selected") },
            providerScope, Dispatchers.IO,
        )
        coordinator.setForeground(true)
        val playground = PlaygroundViewModel(coordinator)
        setup = OnboardingViewModel(coordinator, models::isModelAvailable, preferences) {
            if (downloadGate != null) coordinator.downloadQwen(preset) else flow {
                downloadStarts++
                downloads.value = DownloadState.Downloading(0)
                downloads.takeWhile { it !is DownloadState.Success && it !is DownloadState.Error }
                    .collect { emit(it) }
                if (downloads.value == DownloadState.Success) {
                    File(modelDirectory, "${preset.fileName}.part").writeText("test")
                    models.downloadModel(preset).collect { emit(it) }
                } else {
                    emit(downloads.value)
                }
            }
        }
        viewModels += listOf(today, settings, deletion, playground, setup)
        restoration = StateRestorationTester(compose)
        restoration.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, if (constrained) 1.5f else density.fontScale),
            ) {
            Box(if (constrained) Modifier.requiredSize(320.dp, 440.dp) else Modifier) {
            ThwiplyTheme(theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AppNavigation(
                    mainScreen = { open ->
                        MainAppContent { tab ->
                            when (tab) {
                                MainTab.TODAY -> TodayScreen(today)
                                MainTab.LAB -> PlaygroundScreen(open, playground)
                                MainTab.SETTINGS -> SettingsScreen(open, settings, deletion)
                            }
                        }
                    },
                    setupScreen = { exit ->
                        OnboardingScreen(onExit = { setup.pauseDownload(); exit() }, viewModel = setup)
                    },
                )
                }
            }
            }
            }
        }
    }

    private fun addManualTask() {
        compose.onNodeWithContentDescription("Add task").performClick()
        compose.onNodeWithText("Task description").performTextInput("Synthetic manual task")
        compose.onNodeWithText("Add Task").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Synthetic manual task").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitText(value: String): SemanticsNodeInteraction {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNodeWithText(value)
    }

    private fun readyButton(): SemanticsNodeInteraction {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Thwip Test").fetchSemanticsNodes().singleOrNull()
                ?.config?.contains(SemanticsProperties.Disabled) == false
        }
        return compose.onNodeWithText("Thwip Test")
    }

    private fun text(id: Int) = compose.activity.getString(id)
    private fun tab(name: String) = compose.onNode(hasText(name) and hasClickAction()).performClick()
    private fun openSetup() = compose.onNodeWithText(text(R.string.setup_open)).performScrollTo().performClick()
    private fun back() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
}
