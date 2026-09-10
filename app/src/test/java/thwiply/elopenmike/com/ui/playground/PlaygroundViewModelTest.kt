package thwiply.elopenmike.com.ui.playground

import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import thwiply.elopenmike.com.llm.engine.*
import thwiply.elopenmike.com.llm.model.*
import thwiply.elopenmike.com.llm.provider.*
import thwiply.elopenmike.com.testing.providerFixture

@OptIn(ExperimentalCoroutinesApi::class)
class PlaygroundViewModelTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var modelsDirectory: File
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `missing model rejects inference even when called outside the UI`() = runTest {
        val engine = LlmEngineManager(StandardTestDispatcher(testScheduler)) { error("Must not initialize") }
        val vm = PlaygroundViewModel(providerFixture(temporaryFolder.newFolder(), engine, models(false)))
        vm.prepareEngine()
        vm.generate("manual work must remain available", false)
        runCurrent()
        assertEquals(ProviderReadiness.Missing, vm.readiness.value)
        assertFalse(vm.isGenerating.value)
        assertEquals("", vm.output.value)
    }

    @Test fun `installed model needs successful initialization and failure supports retry`() = runTest {
        var attempts = 0
        val fake = FakeEngine()
        val engine = LlmEngineManager(StandardTestDispatcher(testScheduler)) {
            if (++attempts == 1) throw IllegalStateException("Native init failed")
            fake
        }
        val vm = PlaygroundViewModel(providerFixture(temporaryFolder.newFolder(), engine, models(true)))
        vm.generate("too early", false)
        vm.prepareEngine()
        runCurrent()
        assertTrue("readiness=${vm.readiness.value}; failure=${vm.generationFailure.value}; busy=${vm.busy.value}",
            vm.readiness.value is ProviderReadiness.Failed)
        vm.generate("still not ready", false)
        runCurrent()
        assertEquals(0, fake.generations)
        vm.prepareEngine()
        runCurrent()
        assertEquals(ProviderReadiness.Ready, vm.readiness.value)
        vm.generate("hello", false)
        vm.generate("duplicate", false)
        vm.isGenerating.first { !it }
        assertEquals("response", vm.output.value)
        assertEquals(1, fake.generations)
    }

    @Test fun `entering Lab never revalidates rejected weights on its own`() = runTest {
        val models = models(installed = true)
        corruptInstalledFixture()
        val vm = PlaygroundViewModel(providerFixture(temporaryFolder.newFolder(), qwenEngine(), models))
        vm.prepareEngine()
        runCurrent()
        assertEquals(FailureKind.MODEL_CORRUPT, failureKind(vm))

        repairInstalledFixture()
        vm.prepareEngine()
        runCurrent()

        // Automatic entry must not re-hash the artifact; only the explicit control may.
        assertEquals(FailureKind.MODEL_CORRUPT, failureKind(vm))
    }

    @Test fun `the explicit Lab retry revalidates repaired weights and reaches ready`() = runTest {
        val models = models(installed = true)
        corruptInstalledFixture()
        val vm = PlaygroundViewModel(providerFixture(temporaryFolder.newFolder(), qwenEngine(), models))
        vm.prepareEngine()
        runCurrent()
        assertEquals(FailureKind.MODEL_CORRUPT, failureKind(vm))

        repairInstalledFixture()
        vm.retry()
        runCurrent()

        assertEquals(ProviderReadiness.Ready, vm.readiness.value)
        assertNull(vm.generationFailure.value)
    }

    @Test fun `readiness rejects an engine for a different installed path`() = runTest {
        val engine = LlmEngineManager(StandardTestDispatcher(testScheduler)) { FakeEngine() }
        temporaryFolder.newFile("other.litertlm").let { engine.initialize(it, it.absolutePath) }
        val vm = PlaygroundViewModel(providerFixture(temporaryFolder.newFolder(), engine, models(true)))
        vm.generate("wrong engine", false)
        runCurrent()
        assertEquals(ProviderReadiness.NeedsInitialization, vm.readiness.value)
        assertFalse(vm.isGenerating.value)
    }

    @Test fun `generation cancellation propagates without becoming an error response`() = runTest {
        val engine = LlmEngineManager(StandardTestDispatcher(testScheduler)) { FakeEngine(cancel = true) }
        val vm = PlaygroundViewModel(providerFixture(temporaryFolder.newFolder(), engine, models(true)))
        vm.prepareEngine()
        runCurrent()
        vm.generate("hello", false)
        vm.isGenerating.first { !it }
        assertFalse(vm.isGenerating.value)
        assertEquals("", vm.output.value)
        assertNull(vm.generationFailure.value)
    }

    @Test fun `performance count measures text rather than stream emissions`() = runTest {
        val engine = LlmEngineManager(StandardTestDispatcher(testScheduler)) { FakeEngine() }
        val vm = PlaygroundViewModel(providerFixture(temporaryFolder.newFolder(), engine, models(true)))
        vm.prepareEngine()
        runCurrent()
        vm.generate("hello", false)
        vm.isGenerating.first { !it }
        assertEquals("response".length, vm.metrics.value.characterCount)
    }

    @Test fun `Nano stop closes its stream and allows another foreground request`() = runTest {
        val nano = FakeNano(flow { emit("partial"); awaitCancellation() })
        val fixture = providerFixture(temporaryFolder.newFolder(), nanoFactory = NanoClientFactory { nano })
        fixture.selectProvider(ModelProvider.GEMINI_NANO)
        val vm = PlaygroundViewModel(fixture)
        vm.prepareEngine()
        runCurrent()
        assertEquals("failure=${vm.generationFailure.value}; busy=${vm.busy.value}",
            ProviderReadiness.Ready, vm.readiness.value)
        vm.generate("synthetic prompt", false)
        runCurrent()
        assertEquals("partial", vm.output.value)
        assertTrue(vm.isGenerating.value)
        vm.stop()
        runCurrent()
        assertFalse(vm.isGenerating.value)
        assertTrue(vm.stopped.value)
        assertNull(vm.generationFailure.value)
        assertTrue(nano.closes >= 2) // Availability client and generation client.

        nano.responses = flowOf("second")
        vm.prepareEngine()
        runCurrent()
        vm.generate("another prompt", false)
        runCurrent()
        assertEquals("second", vm.output.value)
        assertFalse(vm.stopped.value)
        assertFalse(vm.isGenerating.value)
    }

    @Test fun `switching providers discards old partial output without choosing a fallback`() = runTest {
        val nano = FakeNano(flow { emit("old output"); awaitCancellation() })
        val fixture = providerFixture(temporaryFolder.newFolder(), nanoFactory = NanoClientFactory { nano })
        fixture.selectProvider(ModelProvider.GEMINI_NANO)
        val vm = PlaygroundViewModel(fixture)
        vm.prepareEngine()
        runCurrent()
        vm.generate("synthetic prompt", false)
        runCurrent()
        fixture.selectProvider(ModelProvider.QWEN)
        runCurrent()
        assertEquals("", vm.output.value)
        assertEquals(PlaygroundMetrics(), vm.metrics.value)
        assertEquals(ModelProvider.QWEN, vm.selection.value.provider)
        assertEquals(ProviderReadiness.Missing, vm.readiness.value)
        assertFalse(vm.isGenerating.value)
    }

    @Test fun `losing top foreground cancels Nano without reporting successful completion`() = runTest {
        val nano = FakeNano(flow { emit("partial"); awaitCancellation() })
        val fixture = providerFixture(temporaryFolder.newFolder(), nanoFactory = NanoClientFactory { nano })
        fixture.selectProvider(ModelProvider.GEMINI_NANO)
        val vm = PlaygroundViewModel(fixture)
        vm.prepareEngine()
        runCurrent()
        vm.generate("synthetic prompt", false)
        runCurrent()
        fixture.setForeground(false)
        runCurrent()
        assertFalse(vm.isGenerating.value)
        assertTrue(vm.stopped.value)
        assertNull(vm.generationFailure.value)
    }

    @Test fun `safety rejection removes partial text and keeps original cause`() = runTest {
        val cause = IllegalStateException("synthetic policy failure")
        val nano = FakeNano(flow {
            emit("must be removed")
            throw InferenceFailure(FailureKind.SAFETY, cause)
        })
        val fixture = providerFixture(temporaryFolder.newFolder(), nanoFactory = NanoClientFactory { nano })
        fixture.selectProvider(ModelProvider.GEMINI_NANO)
        val vm = PlaygroundViewModel(fixture)
        vm.prepareEngine()
        runCurrent()
        vm.generate("synthetic prompt", false)
        runCurrent()
        assertEquals("", vm.output.value)
        assertEquals(0, vm.metrics.value.characterCount)
        assertEquals(FailureKind.SAFETY, vm.generationFailure.value?.kind)
        assertSame(cause, vm.generationFailure.value?.cause)
    }

    @Test fun `character metrics join surrogate pairs split between stream chunks`() = runTest {
        val nano = FakeNano(flowOf("A\uD83D", "\uDE00B"))
        val fixture = providerFixture(temporaryFolder.newFolder(), nanoFactory = NanoClientFactory { nano })
        fixture.selectProvider(ModelProvider.GEMINI_NANO)
        val vm = PlaygroundViewModel(fixture)
        vm.prepareEngine()
        runCurrent()
        vm.generate("synthetic prompt", false)
        runCurrent()
        assertEquals("A\uD83D\uDE00B", vm.output.value)
        assertEquals(3, vm.metrics.value.characterCount)
    }

    @Test fun `empty response clears whitespace and metrics`() = runTest {
        val nano = FakeNano(flowOf(" ", "\n"))
        val fixture = providerFixture(temporaryFolder.newFolder(), nanoFactory = NanoClientFactory { nano })
        fixture.selectProvider(ModelProvider.GEMINI_NANO)
        val vm = PlaygroundViewModel(fixture)
        vm.prepareEngine()
        runCurrent()
        vm.generate("synthetic prompt", false)
        runCurrent()
        assertEquals(FailureKind.EMPTY_OUTPUT, vm.generationFailure.value?.kind)
        assertEquals("", vm.output.value)
        assertEquals(PlaygroundMetrics(), vm.metrics.value)
    }

    @Test fun `blank input is not described as exceeding the length limit`() = runTest {
        val vm = PlaygroundViewModel(providerFixture(temporaryFolder.newFolder()))
        vm.generate(" ", false)
        assertNotNull(vm.generationFailure.value)
        assertNotEquals(FailureKind.INPUT_TOO_LONG, vm.generationFailure.value?.kind)
    }

    @Test fun `Lab reentry waits for cancelled preparation cleanup then prepares again`() = runTest {
        val cleanup = CompletableDeferred<Unit>()
        var checks = 0
        val nano = FakeNano(emptyFlow())
        nano.onStatus = {
            if (++checks == 1) {
                try { awaitCancellation() }
                finally { withContext(NonCancellable) { cleanup.await() } }
            }
            NanoState.Ready
        }
        val fixture = providerFixture(temporaryFolder.newFolder(), nanoFactory = NanoClientFactory { nano })
        fixture.selectProvider(ModelProvider.GEMINI_NANO)
        val vm = PlaygroundViewModel(fixture)
        vm.prepareEngine()
        runCurrent()
        vm.stop()
        runCurrent()
        assertTrue(fixture.busy.value)
        vm.prepareEngine()
        runCurrent()
        assertEquals(1, checks)
        cleanup.complete(Unit)
        runCurrent()
        assertEquals(2, checks)
        assertEquals(ProviderReadiness.Ready, vm.readiness.value)
        assertFalse(fixture.busy.value)
    }

    private class FakeNano(var responses: Flow<String>) : NanoClient {
        var closes = 0
        var onStatus: suspend () -> NanoState = { NanoState.Ready }
        override suspend fun checkStatus() = onStatus()
        override suspend fun download() = error("No download consent")
        override suspend fun countTokens(prompt: String) = 10
        override fun generate(prompt: String) = responses
        override fun close() { closes++ }
    }

    // The fixture artifact carries its own digest, so an installed file still has to verify.
    private fun TestScope.models(installed: Boolean): ModelManager {
        modelsDirectory = temporaryFolder.newFolder()
        if (installed) {
            File(modelsDirectory, "active-model").writeText(FIXTURE_PRESET.id)
            installedFixture().writeBytes(FIXTURE_BYTES)
        }
        return ModelManager(
            modelsDirectory, OkHttpClient(), listOf(FIXTURE_PRESET), backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
    }

    private fun installedFixture() = File(modelsDirectory, FIXTURE_PRESET.fileName)

    private class FakeEngine(private val cancel: Boolean = false) : ManagedEngine {
        var generations = 0
        override fun initialize() = Unit
        override fun close() = Unit
        override fun createConversation() = object : ManagedConversation {
            override fun generate(prompt: String): Flow<String> = flow {
                generations++
                if (cancel) throw CancellationException("Test cancellation")
                emit("response")
            }
            override fun close() = Unit
        }
    }

    private fun TestScope.qwenEngine() = LlmEngineManager(StandardTestDispatcher(testScheduler)) { FakeEngine() }

    private fun failureKind(vm: PlaygroundViewModel) =
        (vm.readiness.value as ProviderReadiness.Failed).failure.kind

    private fun corruptInstalledFixture() = installedFixture().writeBytes(ByteArray(FIXTURE_BYTES.size))

    private fun repairInstalledFixture() = installedFixture().writeBytes(FIXTURE_BYTES)

    private companion object {
        val FIXTURE_BYTES = "verified fixture weights".toByteArray()
        val FIXTURE_PRESET = ModelPreset.QWEN_2_5_1_5B.copy(
            fileName = "test.litertlm",
            expectedBytes = FIXTURE_BYTES.size.toLong(),
            sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(FIXTURE_BYTES).toHexString(),
        )
    }
}
