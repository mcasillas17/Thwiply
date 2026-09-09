package thwiply.elopenmike.com.llm.model

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import thwiply.elopenmike.com.testing.verifiedArtifactFile

/**
 * Restart adoption must revalidate the approved artifact instead of trusting its length.
 * Every fixture is a real file whose expected digest is computed from its own bytes.
 * Tiny fixtures exercise the state contract; they are not real-model inference evidence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelArtifactVerificationTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var modelsDir: File

    @Before fun setUp() {
        modelsDir = temporaryFolder.newFolder("models")
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `installed artifact becomes Ready only after its full digest matches`() = runTest {
        val bytes = ByteArray(200_000) { (it * 31).toByte() }
        val preset = install(bytes)

        val manager = testManager(preset)
        manager.awaitLoaded()

        assertEquals(ModelArtifactState.Ready(preset), manager.state.value)
        assertTrue(manager.state.value is ModelArtifactState.Ready)
        assertArrayEquals(bytes, manager.verifiedArtifactFile().readBytes())
    }

    /**
     * Known-answer vector. The pinned preset digests are lowercase literals, so the encoding
     * production compares against must stay lowercase regardless of how fixtures compute theirs.
     */
    @Test fun `verification matches a known lowercase SHA-256 literal`() = runTest {
        val bytes = "abc".toByteArray()
        val preset = ModelPreset.QWEN_2_5_1_5B.copy(
            id = "known-answer",
            fileName = "known-answer.litertlm",
            expectedBytes = bytes.size.toLong(),
            sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        )
        File(modelsDir, preset.fileName).writeBytes(bytes)
        File(modelsDir, "active-model").writeText(preset.id)

        val manager = testManager(preset)
        manager.awaitLoaded()

        assertEquals(ModelArtifactState.Ready(preset), manager.state.value)
    }

    @Test fun `equal length tampering after restart is corrupt not ready`() = runTest {
        val bytes = "approved model bytes".toByteArray()
        val preset = install(bytes)
        val tampered = bytes.copyOf().also { it[it.lastIndex] = 'X'.code.toByte() }
        assertEquals(bytes.size, tampered.size)
        File(modelsDir, preset.fileName).writeBytes(tampered)

        val manager = testManager(preset)
        manager.awaitLoaded()

        assertEquals(
            ModelArtifactState.Corrupt(preset, ArtifactDefect.DIGEST_MISMATCH),
            manager.state.value,
        )
        assertFalse(manager.state.value is ModelArtifactState.Ready)
    }

    @Test fun `truncated artifact after restart is corrupt not ready`() = runTest {
        val bytes = "approved model bytes".toByteArray()
        val preset = install(bytes)
        File(modelsDir, preset.fileName).writeBytes(bytes.copyOf(bytes.size - 1))

        val manager = testManager(preset)
        manager.awaitLoaded()

        assertEquals(
            ModelArtifactState.Corrupt(preset, ArtifactDefect.SIZE_MISMATCH),
            manager.state.value,
        )
    }

    @Test fun `oversized artifact after restart is corrupt not ready`() = runTest {
        val bytes = "approved model bytes".toByteArray()
        val preset = install(bytes)
        File(modelsDir, preset.fileName).writeBytes(bytes + "extra".toByteArray())

        val manager = testManager(preset)
        manager.awaitLoaded()

        assertEquals(
            ModelArtifactState.Corrupt(preset, ArtifactDefect.SIZE_MISMATCH),
            manager.state.value,
        )
    }

    @Test fun `artifact missing behind existing metadata is corrupt not missing`() = runTest {
        val preset = install("approved model bytes".toByteArray())
        assertTrue(File(modelsDir, preset.fileName).delete())

        val manager = testManager(preset)
        manager.awaitLoaded()

        assertEquals(
            ModelArtifactState.Corrupt(preset, ArtifactDefect.FILE_MISSING),
            manager.state.value,
        )
    }

    @Test fun `absent activation metadata is an ordinary missing installation`() = runTest {
        val manager = testManager(presetFor("never installed".toByteArray()))
        manager.awaitLoaded()

        assertEquals(ModelArtifactState.Missing, manager.state.value)
        assertFalse(manager.state.value is ModelArtifactState.Ready)
    }

    @Test fun `unknown activation metadata fails instead of reporting nothing installed`() = runTest {
        val preset = install("approved model bytes".toByteArray())
        File(modelsDir, "active-model").writeText("some-unapproved-model")

        val manager = testManager(preset)
        manager.awaitLoaded()

        val failed = manager.state.value as ModelArtifactState.Failed
        assertTrue(failed.cause is UnusableActivationRecord)
    }

    @Test fun `an unusable activation record can be cleared without touching the weights`() = runTest {
        val bytes = "approved model bytes".toByteArray()
        val preset = install(bytes)
        File(modelsDir, "active-model").writeText("some-unapproved-model")
        val manager = testManager(preset)
        manager.awaitLoaded()
        assertTrue((manager.state.value as ModelArtifactState.Failed).cause is UnusableActivationRecord)

        manager.discardRejectedInstallation()

        assertEquals(ModelArtifactState.Missing, manager.state.value)
        assertFalse(File(modelsDir, "active-model").exists())
        // The record named no approved model, so the file it left behind is not ours to delete.
        assertArrayEquals(bytes, File(modelsDir, preset.fileName).readBytes())
    }

    @Test fun `a storage read failure is not treated as an unusable record`() = runTest {
        val preset = install("approved model bytes".toByteArray())
        val installed = File(modelsDir, preset.fileName)
        assumeTrue(installed.setReadable(false, false) && !installed.canRead())
        try {
            val manager = testManager(preset)
            manager.awaitLoaded()
            val failed = manager.state.value as ModelArtifactState.Failed
            assertTrue(failed.cause is IOException)
            assertFalse(failed.cause is UnusableActivationRecord)

            manager.discardRejectedInstallation()

            assertTrue(manager.state.value is ModelArtifactState.Failed)
            assertTrue(installed.exists())
        } finally {
            installed.setReadable(true, true)
        }
    }

    @Test fun `unreadable artifact storage fails and keeps the original cause`() = runTest {
        val preset = install("approved model bytes".toByteArray())
        val installed = File(modelsDir, preset.fileName)
        assumeTrue(installed.setReadable(false, false) && !installed.canRead())

        try {
            val manager = testManager(preset)
            manager.awaitLoaded()

            assertTrue((manager.state.value as ModelArtifactState.Failed).cause is IOException)
        } finally {
            installed.setReadable(true, true)
        }
    }

    @Test fun `recreating the manager verifies the artifact again`() = runTest {
        val bytes = "approved model bytes".toByteArray()
        val preset = install(bytes)
        testManager(preset).awaitLoaded()

        File(modelsDir, preset.fileName).writeBytes(ByteArray(bytes.size))
        val restarted = testManager(preset)
        restarted.awaitLoaded()

        assertEquals(
            ModelArtifactState.Corrupt(preset, ArtifactDefect.DIGEST_MISMATCH),
            restarted.state.value,
        )
    }

    @Test fun `verification never dispatches work to the main thread`() = runTest {
        Dispatchers.setMain(RejectingDispatcher)
        val bytes = ByteArray(300_000) { it.toByte() }
        val preset = install(bytes)

        val manager = testManager(preset)
        manager.awaitLoaded()
        manager.refreshInstalledModel()

        assertEquals(ModelArtifactState.Ready(preset), manager.state.value)
    }

    /**
     * The discard is issued while a refresh holds the artifact lock — Verifying is published
     * under that lock, before the hash. Serialization is what lets the discard observe the
     * settled verdict and act on it; an unserialized discard would see Verifying, decline,
     * and leave the rejected artifact on disk.
     */
    @Test fun `a discard issued during verification waits and then acts on the settled verdict`() =
        runBlocking {
            val good = ByteArray(8_000_000) { (it * 31).toByte() }
            val preset = presetFor(good)
            val installed = File(modelsDir, preset.fileName)
            installed.writeBytes(ByteArray(good.size))
            File(modelsDir, "active-model").writeText(preset.id)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val manager = ModelManager(modelsDir, OkHttpClient(), listOf(preset), scope, Dispatchers.IO)
                manager.awaitLoaded()
                assertTrue(manager.state.value is ModelArtifactState.Corrupt)

                val discardIssued = CompletableDeferred<Job>()
                val watcher = scope.launch(Dispatchers.Unconfined) {
                    manager.state.collect {
                        if (it is ModelArtifactState.Verifying && !discardIssued.isCompleted) {
                            discardIssued.complete(scope.launch { manager.discardRejectedInstallation() })
                        }
                    }
                }
                val refresh = scope.launch { manager.refreshInstalledModel() }
                discardIssued.await().join()
                refresh.join()
                watcher.cancel()

                assertEquals(ModelArtifactState.Missing, manager.state.value)
                assertFalse(installed.exists())
                assertFalse(File(modelsDir, "active-model").exists())
            } finally {
                scope.cancel()
            }
        }

    /**
     * Both refreshes complete and the manager converges on the bytes installed at the end,
     * with no stranded Verifying. This pins convergence, not lock ordering: see the discard
     * test above for the case where serialization changes the observable outcome.
     */
    @Test fun `overlapping refreshes converge on the installed bytes`() = runBlocking {
        val bytes = "approved model bytes".toByteArray()
        val preset = install(bytes)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val manager = ModelManager(modelsDir, OkHttpClient(), listOf(preset), scope, Dispatchers.IO)
            manager.awaitLoaded()

            val first = scope.launch { manager.refreshInstalledModel() }
            File(modelsDir, preset.fileName).writeBytes(ByteArray(bytes.size))
            val second = scope.launch { manager.refreshInstalledModel() }
            first.join()
            second.join()

            assertEquals(
                ModelArtifactState.Corrupt(preset, ArtifactDefect.DIGEST_MISMATCH),
                manager.state.value,
            )
        } finally {
            scope.cancel()
        }
    }

    /**
     * Cancels the caller only once its refresh has demonstrably begun, and over bytes whose
     * verdict differs from the last completed one. Verification is owned by the application
     * scope, so the truthful new result must still be published; a caller-owned design would
     * abandon the hash and leave the previous result standing.
     */
    @Test fun `a canceled refresh caller still publishes the result for the current bytes`() =
        runBlocking {
            val bytes = ByteArray(2_000_000) { (it * 7).toByte() }
            val preset = install(bytes)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val manager = ModelManager(modelsDir, OkHttpClient(), listOf(preset), scope, Dispatchers.IO)
                manager.awaitLoaded()
                assertEquals(ModelArtifactState.Ready(preset), manager.state.value)

                File(modelsDir, preset.fileName).writeBytes(ByteArray(bytes.size))
                // Hand the job over explicitly; a lateinit read from the emitting thread could
                // lose the race and silently degrade this into a plain refresh test.
                val started = CompletableDeferred<Job>()
                val watcher = scope.launch(Dispatchers.Unconfined) {
                    manager.state.collect {
                        if (it is ModelArtifactState.Verifying) started.await().cancel()
                    }
                }
                // Started lazily so the job is published before it can emit Verifying; the
                // unconfined collector then cancels inline, before the hash begins.
                val caller = scope.launch(start = CoroutineStart.LAZY) {
                    manager.refreshInstalledModel()
                }
                started.complete(caller)
                caller.start()
                caller.join()
                assertTrue(caller.isCancelled)

                val settled = withTimeout(TIMEOUT_MS) {
                    manager.state.first { it is ModelArtifactState.Corrupt }
                }
                assertEquals(ModelArtifactState.Corrupt(preset, ArtifactDefect.DIGEST_MISMATCH), settled)
                watcher.cancel()
            } finally {
                scope.cancel()
            }
        }

    /**
     * Cancels the owning scope from inside the Ready -> Verifying transition, so cancellation
     * lands deterministically once verification has begun rather than before it starts. The
     * canceled run must publish neither a result for the new bytes nor a stranded Verifying.
     */
    @Test fun `canceling verification keeps the last completed result and adopts nothing new`() =
        runBlocking {
            val bytes = "approved model bytes".toByteArray()
            val preset = install(bytes)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val manager = ModelManager(modelsDir, OkHttpClient(), listOf(preset), scope, Dispatchers.IO)
            manager.awaitLoaded()
            assertEquals(ModelArtifactState.Ready(preset), manager.state.value)

            File(modelsDir, preset.fileName).writeBytes(ByteArray(bytes.size))
            val watcher = scope.launch(Dispatchers.Unconfined) {
                manager.state.collect { if (it is ModelArtifactState.Verifying) scope.cancel() }
            }
            runCatching { manager.refreshInstalledModel() }
            watcher.join()

            assertEquals(ModelArtifactState.Ready(preset), manager.state.value)
            assertNotEquals(
                ModelArtifactState.Corrupt(preset, ArtifactDefect.DIGEST_MISMATCH),
                manager.state.value,
            )
        }

    @Test fun `discarding a corrupt artifact removes only the damaged model files`() = runTest {
        val bytes = "approved model bytes".toByteArray()
        val preset = install(bytes)
        File(modelsDir, preset.fileName).writeBytes(ByteArray(bytes.size))
        File(modelsDir, "${preset.fileName}.part").writeText("partial")
        val unrelated = File(modelsDir.parentFile, "manual-tasks.db").apply { writeText("keep me") }
        val manager = scheduledManager(preset)
        manager.awaitLoaded()

        val observed = mutableListOf<ModelArtifactState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.state.collect { observed += it }
        }
        manager.discardRejectedInstallation()

        assertEquals(ModelArtifactState.Missing, manager.state.value)
        assertTrue(observed.contains(ModelArtifactState.Removing))
        assertFalse(File(modelsDir, preset.fileName).exists())
        assertFalse(File(modelsDir, "active-model").exists())
        // Resumable download data is a separate operation's and is not named by this action.
        assertEquals("partial", File(modelsDir, "${preset.fileName}.part").readText())
        assertEquals("keep me", unrelated.readText())
    }

    @Test fun `a removal that cannot finish is reported as a removal failure, not a read failure`() =
        runTest {
            val bytes = "approved model bytes".toByteArray()
            val preset = install(bytes)
            val installed = File(modelsDir, preset.fileName)
            installed.writeBytes(ByteArray(bytes.size))
            val manager = testManager(preset)
            manager.awaitLoaded()
            assertTrue(manager.state.value is ModelArtifactState.Corrupt)

            // Replace the record with a non-empty directory: delete() cannot remove it, so the
            // weights go and the record stays.
            val record = File(modelsDir, "active-model")
            assertTrue(record.delete())
            assertTrue(File(record, "occupied").let { record.mkdirs() && it.createNewFile() })

            manager.discardRejectedInstallation()

            val failed = manager.state.value as ModelArtifactState.Failed
            assertTrue(failed.cause is ArtifactRemovalFailure)
            assertFalse(installed.exists())
        }

    @Test fun `discarding is rejected unless the artifact failed verification`() = runTest {
        val preset = install("approved model bytes".toByteArray())
        val manager = testManager(preset)
        manager.awaitLoaded()

        manager.discardRejectedInstallation()

        assertEquals(ModelArtifactState.Ready(preset), manager.state.value)
        assertTrue(File(modelsDir, preset.fileName).exists())
    }

    @Test fun `the installed download fast path cannot skip verification`() = runBlocking {
        val bytes = "approved model bytes".toByteArray()
        val preset = install(bytes)
        File(modelsDir, preset.fileName).writeBytes(ByteArray(bytes.size))
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(bytes)))
            val manager = ModelManager(modelsDir, redirectedClient(server), listOf(preset))
            manager.awaitLoaded()
            assertTrue(manager.state.value is ModelArtifactState.Corrupt)

            val result = manager.downloadModel(preset).last()

            assertEquals(DownloadState.Success, result)
            assertEquals(1, server.requestCount)
            assertEquals(ModelArtifactState.Ready(preset), manager.state.value)
            assertArrayEquals(bytes, manager.verifiedArtifactFile().readBytes())
        } finally {
            server.shutdown()
        }
    }

    @Test fun `a verified installation still answers the download fast path without network`() = runBlocking {
        val bytes = "approved model bytes".toByteArray()
        val preset = install(bytes)
        val server = MockWebServer().apply { start() }
        try {
            val manager = ModelManager(modelsDir, redirectedClient(server), listOf(preset))
            manager.awaitLoaded()

            assertEquals(DownloadState.Success, manager.downloadModel(preset).last())
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `activation racing a refresh ends on the freshly verified bytes`() = runBlocking {
        val bytes = "approved model bytes".toByteArray()
        val preset = install(bytes)
        File(modelsDir, preset.fileName).writeBytes(ByteArray(bytes.size))
        val server = MockWebServer().apply { start() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(bytes)))
            val manager = ModelManager(modelsDir, redirectedClient(server), listOf(preset), scope, Dispatchers.IO)
            manager.awaitLoaded()

            val refresh = scope.launch { manager.refreshInstalledModel() }
            val download = scope.launch { manager.downloadModel(preset).last() }
            refresh.join()
            download.join()

            assertEquals(ModelArtifactState.Ready(preset), manager.state.value)
            assertArrayEquals(bytes, manager.verifiedArtifactFile().readBytes())
        } finally {
            scope.cancel()
            server.shutdown()
        }
    }

    private fun TestScope.testManager(preset: ModelPreset) =
        ModelManager(modelsDir, OkHttpClient(), listOf(preset), backgroundScope, Dispatchers.IO)

    private fun TestScope.scheduledManager(preset: ModelPreset) = ModelManager(
        modelsDir, OkHttpClient(), listOf(preset), backgroundScope, StandardTestDispatcher(testScheduler),
    )

    private fun install(bytes: ByteArray): ModelPreset {
        val preset = presetFor(bytes)
        File(modelsDir, preset.fileName).writeBytes(bytes)
        File(modelsDir, "active-model").writeText(preset.id)
        return preset
    }

    private fun presetFor(bytes: ByteArray, id: String = "fixture"): ModelPreset =
        ModelPreset.QWEN_2_5_1_5B.copy(
            id = id,
            fileName = "$id.litertlm",
            expectedBytes = bytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString(),
        )

    private fun redirectedClient(server: MockWebServer): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            object : Interceptor {
                override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
                    chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build(),
                )
            },
        )
        .build()

    private object RejectingDispatcher : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable): Unit =
            throw AssertionError("Model verification must never dispatch to the main thread")
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
