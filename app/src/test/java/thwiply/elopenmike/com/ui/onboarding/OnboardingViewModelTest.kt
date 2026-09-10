package thwiply.elopenmike.com.ui.onboarding

import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import okhttp3.OkHttpClient
import thwiply.elopenmike.com.llm.model.DownloadState
import thwiply.elopenmike.com.llm.model.ModelArtifactState
import thwiply.elopenmike.com.llm.model.ModelManager
import thwiply.elopenmike.com.llm.model.ModelPreset
import thwiply.elopenmike.com.llm.provider.*
import thwiply.elopenmike.com.testing.providerFixture
import thwiply.elopenmike.com.testing.preferenceFixture

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `repeated entry cannot start concurrent downloads and leaving preserves cancellation`() = runTest {
        var starts = 0
        var cancelled = 0
        val viewModel = OnboardingViewModel(providerFixture(temporaryFolder.newFolder()), preferenceFixture(temporaryFolder.newFolder())) {
            flow {
                starts++
                try {
                    emit(DownloadState.Downloading(25))
                    awaitCancellation()
                } finally { cancelled++ }
            }
        }
        viewModel.startDownload()
        viewModel.startDownload()
        runCurrent()
        assertEquals(1, starts)
        viewModel.pauseDownload()
        viewModel.startDownload() // Do not overlap a still-unwinding writer.
        runCurrent()
        assertEquals(1, cancelled)
        assertEquals(DownloadState.Idle, viewModel.uiState.value)
        viewModel.startDownload()
        runCurrent()
        assertEquals(2, starts)
        viewModel.pauseDownload()
        runCurrent()
    }

    @Test fun `setup failure remains explicit and can be retried to success`() = runTest {
        var starts = 0
        val viewModel = OnboardingViewModel(providerFixture(temporaryFolder.newFolder()), preferenceFixture(temporaryFolder.newFolder())) {
            flow {
                starts++
                if (starts == 1) throw IOException("Disk unavailable")
                emit(DownloadState.Success)
            }
        }
        viewModel.startDownload()
        runCurrent()
        assertTrue(viewModel.uiState.value is DownloadState.Error)
        viewModel.startDownload()
        runCurrent()
        assertEquals(DownloadState.Success, viewModel.uiState.value)
    }

    @Test fun `restored setup adopts a verified installed model without downloading`() = runTest {
        // A real verified artifact, not a stubbed availability flag: setup reports completion
        // only because the artifact state says Ready.
        val models = installedModels()
        val fixture = providerFixture(temporaryFolder.newFolder(), models = models)
        val viewModel = OnboardingViewModel(fixture, preferenceFixture(temporaryFolder.newFolder())) { error("Must not download") }
        runCurrent()

        assertEquals(ModelArtifactState.Ready(FIXTURE_PRESET), viewModel.qwenArtifact.value)
        assertEquals(DownloadState.Success, viewModel.uiState.value)
    }

    @Test fun `setup stops reporting a completed install once weights fail revalidation`() = runTest {
        val models = installedModels()
        val fixture = providerFixture(temporaryFolder.newFolder(), models = models)
        val viewModel = OnboardingViewModel(fixture, preferenceFixture(temporaryFolder.newFolder())) { error("Must not download") }
        runCurrent()
        assertEquals(DownloadState.Success, viewModel.uiState.value)

        installedArtifact().writeBytes(ByteArray(VERIFIED.size))
        viewModel.verifyModel()
        runCurrent()

        assertTrue(viewModel.qwenArtifact.value is ModelArtifactState.Corrupt)
        assertEquals(DownloadState.Idle, viewModel.uiState.value)
    }

    @Test fun `discarding damaged weights leaves setup ready for an explicit new download`() = runTest {
        val models = installedModels()
        installedArtifact().writeBytes(ByteArray(VERIFIED.size))
        val fixture = providerFixture(temporaryFolder.newFolder(), models = models)
        val viewModel = OnboardingViewModel(fixture, preferenceFixture(temporaryFolder.newFolder())) { error("Must not download") }
        runCurrent()
        assertTrue(viewModel.qwenArtifact.value is ModelArtifactState.Corrupt)

        viewModel.discardRejectedModel()
        runCurrent()

        assertEquals(ModelArtifactState.Missing, viewModel.qwenArtifact.value)
        assertFalse(installedArtifact().exists())
        assertEquals(DownloadState.Idle, viewModel.uiState.value)
    }

    @Test fun `immediate exit before the download starts does not leave a stuck spinner`() = runTest {
        val viewModel = OnboardingViewModel(providerFixture(temporaryFolder.newFolder()), preferenceFixture(temporaryFolder.newFolder())) { flow { awaitCancellation() } }
        viewModel.startDownload()
        viewModel.pauseDownload()
        runCurrent()
        assertEquals(DownloadState.Idle, viewModel.uiState.value)
    }

    @Test fun `setup reentry waits for cancelled check cleanup then checks again`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        var checks = 0
        val nano = object : NanoClient {
            override suspend fun checkStatus(): NanoState {
                if (++checks == 1) {
                    try { awaitCancellation() }
                    finally { withContext(NonCancellable) { cleanup.await() } }
                }
                return NanoState.Ready
            }
            override suspend fun download() = error("No download consent")
            override suspend fun countTokens(prompt: String) = error("No inference")
            override fun generate(prompt: String) = flow<String> { error("No inference") }
            override fun close() = Unit
        }
        val fixture = providerFixture(temporaryFolder.newFolder(), nanoFactory = NanoClientFactory { nano })
        fixture.selectProvider(ModelProvider.GEMINI_NANO)
        val vm = OnboardingViewModel(fixture, preferenceFixture(temporaryFolder.newFolder())) { error("No Qwen download") }
        vm.checkNano()
        runCurrent()
        vm.pauseDownload()
        runCurrent()
        vm.checkNano()
        runCurrent()
        assertTrue(fixture.busy.value)
        assertEquals(1, checks)
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(2, checks)
        assertEquals(NanoState.Ready, vm.nanoState.value)
        assertFalse(fixture.busy.value)
    }

    private lateinit var modelsDirectory: File

    private fun TestScope.installedModels(): ModelManager {
        modelsDirectory = temporaryFolder.newFolder()
        File(modelsDirectory, "active-model").writeText(FIXTURE_PRESET.id)
        File(modelsDirectory, FIXTURE_PRESET.fileName).writeBytes(VERIFIED)
        return ModelManager(
            modelsDirectory, OkHttpClient(), listOf(FIXTURE_PRESET), backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
    }

    private fun installedArtifact() = File(modelsDirectory, FIXTURE_PRESET.fileName)

    private companion object {
        val VERIFIED = "verified fixture weights".toByteArray()
        val FIXTURE_PRESET = ModelPreset.QWEN_2_5_1_5B.copy(
            fileName = "tiny.litertlm",
            expectedBytes = VERIFIED.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(VERIFIED).toHexString(),
        )
    }
}
