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
import thwiply.elopenmike.com.llm.model.DownloadState
import thwiply.elopenmike.com.llm.provider.*
import thwiply.elopenmike.com.testing.providerFixture

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `repeated entry cannot start concurrent downloads and leaving preserves cancellation`() = runTest {
        var starts = 0
        var cancelled = 0
        val viewModel = OnboardingViewModel(providerFixture(temporaryFolder.newFolder()), { false }) {
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
        val viewModel = OnboardingViewModel(providerFixture(temporaryFolder.newFolder()), { false }) {
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

    @Test fun `restored setup adopts installed model without downloading`() = runTest {
        val viewModel = OnboardingViewModel(providerFixture(temporaryFolder.newFolder()), { true }) { error("Must not download") }
        assertEquals(DownloadState.Success, viewModel.uiState.value)
    }

    @Test fun `immediate exit before the download starts does not leave a stuck spinner`() = runTest {
        val viewModel = OnboardingViewModel(providerFixture(temporaryFolder.newFolder()), { false }) { flow { awaitCancellation() } }
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
        val vm = OnboardingViewModel(fixture, { false }) { error("No Qwen download") }
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
}
