package thwiply.elopenmike.com

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Test fun missingModelKeepsManualWorkAndSettingsAvailable() =
        unavailableLaunch("missing", R.string.lab_missing)
    @Test fun settingsDisplaysPackagedVersion() {
        val expectedVersion = BuildConfig.VERSION_NAME
        
        launch()
        tab("Settings")
        compose.onNodeWithText(text(R.string.settings_version_label)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(expectedVersion).performScrollTo().assertIsDisplayed()
    }
    @Test fun partialDownloadKeepsManualWorkAndSettingsAvailable() =
        unavailableLaunch("partial", R.string.lab_missing)
    @Test fun truncatedModelKeepsManualWorkAndSettingsAvailable() =
        unavailableLaunch("truncated", R.string.inference_model_corrupt)
    @Test fun tamperedModelKeepsManualWorkAndSettingsAvailable() =
        unavailableLaunch("tampered", R.string.inference_model_corrupt)
    @Test fun removedModelKeepsManualWorkAndSettingsAvailable() =
        unavailableLaunch("removed", R.string.qwen_corrupt_file_missing)

    /** Revalidation is explicit, observable, and never reaches the network. */
    @Test fun damagedModelRevalidatesExplicitlyWithoutDownloading() {
        launch("tampered")
        addManualTask()
        tab("Settings")
        openSetup()
        compose.onNodeWithText(text(R.string.qwen_corrupt)).performScrollTo().assertIsDisplayed()

        File(modelDirectory, "fixture.litertlm").writeText("test")
        compose.onNodeWithText(text(R.string.qwen_verify_again)).performScrollTo().performClick()
        compose.waitUntil(10_000) { setup.qwenArtifact.value is ModelArtifactState.Ready }

        assertEquals(0, downloadStarts)
        back()
        tab("Today")
        awaitText("Synthetic manual task").assertIsDisplayed()
    }

    /** Discarding removes only the damaged artifact, never manual work or the provider choice. */
    @Test fun damagedModelDiscardRemovesOnlyTheDamagedWeights() {
        launch("tampered")
        addManualTask()
        tab("Settings")
        openSetup()

        compose.onNodeWithText(text(R.string.qwen_discard)).performScrollTo().performClick()
        compose.waitUntil(10_000) { setup.qwenArtifact.value is ModelArtifactState.Missing }

        assertFalse(File(modelDirectory, "fixture.litertlm").exists())
        assertFalse(File(modelDirectory, "active-model").exists())
        assertEquals(0, downloadStarts)
        assertEquals(ModelProvider.QWEN, coordinator.selection.value.provider)
        back()
        tab("Today")
        awaitText("Synthetic manual task").assertIsDisplayed()
        tab("Settings")
        compose.onNodeWithText("Delete notification data and rules").performScrollTo().assertIsEnabled()
    }

    @Test fun unreadableQwenMetadataKeepsManualWorkAndSettingsAvailable() {
        launch("unreadable")
        addManualTask()
        tab("Settings")
        compose.onNodeWithText("Delete notification data and rules").performScrollTo().assertIsEnabled()
        openSetup()
        compose.onNodeWithText(text(R.string.qwen_read_failed)).performScrollTo().assertIsDisplayed()
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
        compose.onAllNodesWithText(text(R.string.qwen_verifying)).assertCountEquals(2)
    }

    private fun unavailableLaunch(modelCondition: String, labMessage: Int) {
        launch(modelCondition)
        addManualTask()
        tab("Lab")
        // Lab renders the provider state in more than one place; every copy must be truthful.
        compose.onAllNodesWithText(text(labMessage)).onFirst().assertIsDisplayed()
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
        val context = compose.activity
        modelDirectory = File(context.cacheDir, "fnd02-${System.nanoTime()}").also { it.mkdirs() }
        val preset = ModelPreset.QWEN_2_5_1_5B.copy(
            fileName = "fixture.litertlm", expectedBytes = 4,
            sha256 = MessageDigest.getInstance("SHA-256").digest("test".toByteArray()).toHexString(),
        )
        // Production writes the activation record only after a candidate digest verified, so an
        // interrupted download has no record at all.
        if (modelCondition !in setOf("missing", "partial")) {
            File(modelDirectory, "active-model").writeText(preset.id)
        }
        when (modelCondition) {
            "installed" -> File(modelDirectory, preset.fileName).writeText("test")
            "truncated" -> File(modelDirectory, preset.fileName).writeText("bad")
            // Same length as the approved artifact, different bytes: only a digest rejects it.
            "tampered" -> File(modelDirectory, preset.fileName).writeText("tes7")
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
        setup = OnboardingViewModel(coordinator, preferences) {
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
            ThwiplyTheme {
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
