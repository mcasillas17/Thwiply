package thwiply.elopenmike.com.ui.onboarding

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import thwiply.elopenmike.com.testing.verifiedArtifactFile
import thwiply.elopenmike.com.llm.model.*
import thwiply.elopenmike.com.testing.providerFixture
import thwiply.elopenmike.com.testing.preferenceFixture

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingDownloadTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `exit during a blocking HTTP request retains bytes and prevents overlapping retries`() = runTest {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val bytes = "verified fixture bytes".toByteArray()
        val directory = temporaryFolder.newFolder("models")
        val preset = ModelPreset.QWEN_2_5_1_5B.copy(
            expectedBytes = bytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString(),
        )
        val partial = File(directory, "${preset.fileName}.part")
        partial.writeBytes(bytes.copyOfRange(0, 4))
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "Test did not release HTTP response" }
                val offset = request.getHeader("Range")?.removePrefix("bytes=")
                    ?.removeSuffix("-")?.toInt() ?: 0
                return MockResponse().setResponseCode(if (offset > 0) 206 else 200)
                    .setHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}")
                    .setBody(okio.Buffer().write(bytes.copyOfRange(offset, bytes.size)))
            }
        }
        server.start()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url("/model")).build())
        }.build()
        val models = ModelManager(directory, client, listOf(preset))
        val viewModel = OnboardingViewModel(providerFixture(temporaryFolder.newFolder()), preferenceFixture(temporaryFolder.newFolder())) { models.downloadModel(preset) }
        try {
            viewModel.startDownload()
            runCurrent()
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            viewModel.pauseDownload()
            viewModel.startDownload()
            runCurrent()
            assertEquals(1, server.requestCount)
            assertEquals("bytes=4-", server.takeRequest().getHeader("Range"))
            assertFalse(models.state.value is ModelArtifactState.Ready)
            assertTrue(partial.exists())
            release.countDown()
            viewModel.uiState.first { it == DownloadState.Idle }

            viewModel.startDownload()
            assertEquals(DownloadState.Success, viewModel.uiState.first {
                it is DownloadState.Success || it is DownloadState.Error
            })
            assertArrayEquals(bytes, models.verifiedArtifactFile().readBytes())
        } finally {
            release.countDown()
            viewModel.pauseDownload()
            withContext(Dispatchers.IO) { server.shutdown() }
        }
    }
}
