package thwiply.elopenmike.com.ui.onboarding

import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import thwiply.elopenmike.com.llm.model.DownloadState

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `repeated entry cannot start concurrent downloads and leaving preserves cancellation`() = runTest {
        var starts = 0
        var cancelled = 0
        val viewModel = OnboardingViewModel({ false }) {
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
        val viewModel = OnboardingViewModel({ false }) {
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

    @Test fun `restored setup adopts installed model without downloading`() {
        val viewModel = OnboardingViewModel({ true }) { error("Must not download") }
        assertEquals(DownloadState.Success, viewModel.uiState.value)
    }

    @Test fun `immediate exit before the download starts does not leave a stuck spinner`() = runTest {
        val viewModel = OnboardingViewModel({ false }) { flow { awaitCancellation() } }
        viewModel.startDownload()
        viewModel.pauseDownload()
        runCurrent()
        assertEquals(DownloadState.Idle, viewModel.uiState.value)
    }
}
