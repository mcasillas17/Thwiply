package thwiply.elopenmike.com.llm.model

import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class ModelManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var modelsDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        modelsDir = temporaryFolder.newFolder("models")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `partial download is never reported as active`() {
        val preset = preset("candidate", "candidate".toByteArray())
        File(modelsDir, "${preset.fileName}.part").writeText("partial")

        val manager = ModelManager(modelsDir, redirectedClient(), listOf(preset))

        assertFalse(manager.isModelAvailable())
        assertNull(manager.activeModel.value)
    }

    @Test
    fun `valid download is atomically activated`() = runBlocking {
        val modelBytes = "verified model bytes".toByteArray()
        val preset = preset("verified", modelBytes)
        server.enqueue(MockResponse().setResponseCode(200).setBody(modelBytes.toOkioBuffer()))
        val manager = ModelManager(modelsDir, redirectedClient(), listOf(preset))

        val result = manager.downloadModel(preset).last()

        assertEquals(DownloadState.Success, result)
        assertEquals(preset, manager.activeModel.value)
        assertArrayEquals(modelBytes, manager.modelFile.readBytes())
        assertFalse(File(modelsDir, "${preset.fileName}.part").exists())
    }

    @Test
    fun `digest mismatch deletes candidate and preserves active model`() = runBlocking {
        val activeBytes = "active model".toByteArray()
        val activePreset = preset("active", activeBytes)
        val replacementBytes = "replacement model".toByteArray()
        val tamperedBytes = ByteArray(replacementBytes.size) { 0x42 }
        val replacementPreset = preset("replacement", replacementBytes)
        server.enqueue(MockResponse().setResponseCode(200).setBody(activeBytes.toOkioBuffer()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(tamperedBytes.toOkioBuffer()))
        val manager = ModelManager(
            modelsDir,
            redirectedClient(),
            listOf(activePreset, replacementPreset)
        )
        assertEquals(DownloadState.Success, manager.downloadModel(activePreset).last())

        val result = manager.downloadModel(replacementPreset).last()

        assertTrue(result is DownloadState.Error)
        assertEquals(activePreset, manager.activeModel.value)
        assertArrayEquals(activeBytes, manager.modelFile.readBytes())
        assertFalse(File(modelsDir, "${replacementPreset.fileName}.part").exists())
    }

    @Test
    fun `partial download resumes with range request`() = runBlocking {
        val modelBytes = "resumable model bytes".toByteArray()
        val firstChunk = modelBytes.copyOfRange(0, 9)
        val remainder = modelBytes.copyOfRange(9, modelBytes.size)
        val preset = preset("resumable", modelBytes)
        File(modelsDir, "${preset.fileName}.part").writeBytes(firstChunk)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 9-${modelBytes.lastIndex}/${modelBytes.size}")
                .setBody(remainder.toOkioBuffer())
        )
        val manager = ModelManager(modelsDir, redirectedClient(), listOf(preset))

        val result = manager.downloadModel(preset).last()

        assertEquals(DownloadState.Success, result)
        assertEquals("bytes=9-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(modelBytes, manager.modelFile.readBytes())
    }

    @Test
    fun `complete candidate is verified without another network request`() = runBlocking {
        val modelBytes = "complete candidate".toByteArray()
        val preset = preset("complete", modelBytes)
        File(modelsDir, "${preset.fileName}.part").writeBytes(modelBytes)
        val manager = ModelManager(modelsDir, redirectedClient(), listOf(preset))

        val result = manager.downloadModel(preset).last()

        assertEquals(DownloadState.Success, result)
        assertEquals(0, server.requestCount)
        assertArrayEquals(modelBytes, manager.modelFile.readBytes())
    }

    private fun preset(id: String, expectedBytes: ByteArray): ModelPreset = ModelPreset(
        id = id,
        name = "$id model",
        tag = "Test",
        size = "${expectedBytes.size} bytes",
        description = "Test model",
        url = "https://huggingface.co/test/$id.litertlm",
        fileName = "$id.litertlm",
        expectedBytes = expectedBytes.size.toLong(),
        sha256 = expectedBytes.sha256()
    )

    private fun redirectedClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            object : Interceptor {
                override fun intercept(chain: Interceptor.Chain): Response {
                    val request = chain.request()
                    return chain.proceed(
                        request.newBuilder()
                            .url(server.url(request.url.encodedPath))
                            .build()
                    )
                }
            }
        )
        .build()

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private fun ByteArray.toOkioBuffer(): okio.Buffer = okio.Buffer().write(this)
}
