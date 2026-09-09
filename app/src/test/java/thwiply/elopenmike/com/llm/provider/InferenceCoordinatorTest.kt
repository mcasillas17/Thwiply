package thwiply.elopenmike.com.llm.provider

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import thwiply.elopenmike.com.llm.engine.*
import thwiply.elopenmike.com.llm.model.*

@OptIn(ExperimentalCoroutinesApi::class)
class InferenceCoordinatorTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `Qwen default readiness follows verified model and engine path`() = runTest {
        val absent = fixture(installed = false)
        runCurrent()
        assertEquals(ProviderReadiness.Missing, absent.coordinator.readiness.value)
        val f = fixture()
        runCurrent()
        assertEquals(ProviderReadiness.NeedsInitialization, f.coordinator.readiness.value)
        f.coordinator.prepare()
        assertEquals(ProviderReadiness.Ready, f.coordinator.readiness.value)
        assertEquals(listOf("qwen"), f.coordinator.generate("hello").toList())
        assertEquals(0, f.nano.opens)
        assertEquals(ModelPreset.QWEN_2_5_1_5B, ModelPreset.PRESETS.single())
    }

    @Test fun `preparing an already ready Qwen engine preserves readiness on foreground reentry`() = runTest {
        val f = fixture()
        f.coordinator.prepare()
        val observed = mutableListOf<ProviderReadiness>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            f.coordinator.readiness.collect { observed += it }
        }
        f.coordinator.setForeground(false)
        f.coordinator.setForeground(true)
        f.coordinator.prepare()
        assertEquals(listOf(ProviderReadiness.Ready), observed)
        assertEquals(1, f.engine.initializations)
    }

    @Test fun `checking Nano reports each status and never downloads or falls back`() = runTest {
        val f = fixture()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        for ((state, readiness) in listOf(
            NanoState.Unavailable to ProviderReadiness.Unavailable,
            NanoState.Downloadable to ProviderReadiness.Downloadable,
            NanoState.Downloading to ProviderReadiness.Downloading,
            NanoState.Ready to ProviderReadiness.Ready,
        )) {
            f.nano.status = state
            f.coordinator.prepare()
            assertEquals(state, f.coordinator.nanoState.value)
            assertEquals(readiness, f.coordinator.readiness.value)
        }
        assertEquals(0, f.nano.downloads)
        assertEquals(0, f.engine.initializations)
        assertEquals(ModelProvider.GEMINI_NANO, f.repository.state.value.provider)
        assertEquals(f.nano.opens, f.nano.closes)
    }

    @Test fun `Nano download requires explicit request and checks readiness afterward`() = runTest {
        val f = fixture()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.nano.status = NanoState.Downloadable
        f.coordinator.checkNano()
        assertEquals(0, f.nano.downloads)
        f.coordinator.downloadNano()
        assertEquals(1, f.nano.downloads)
        assertEquals(ProviderReadiness.Ready, f.coordinator.readiness.value)
        assertEquals(f.nano.opens, f.nano.closes)
    }

    @Test fun `generation rechecks Nano availability without implicit download`() = runTest {
        val f = fixture()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.coordinator.prepare()
        f.nano.status = NanoState.Downloadable
        assertFailure(FailureKind.UNAVAILABLE) { f.coordinator.generate("x").collect() }
        assertEquals(ProviderReadiness.Downloadable, f.coordinator.readiness.value)
        assertEquals(0, f.nano.generations)
        assertEquals(0, f.nano.downloads)
    }

    @Test fun `foreground is false by default and gates native work`() = runTest {
        val f = fixture(foreground = false)
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        assertFailure(FailureKind.BACKGROUND) { f.coordinator.prepare() }
        assertFailure(FailureKind.BACKGROUND) { f.coordinator.downloadNano() }
        assertEquals(0, f.nano.opens)
    }

    @Test fun `Nano closes resident Qwen before opening and closes every request client`() = runTest {
        val f = fixture()
        f.coordinator.prepare()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.nano.beforeOpen = { assertTrue(f.engine.closed) }
        f.coordinator.prepare()
        repeat(2) { assertEquals(listOf("nano"), f.coordinator.generate("hello").toList()) }
        assertEquals(2, f.nano.generations)
        assertEquals(f.nano.opens, f.nano.closes)
    }

    @Test fun `duplicate work is busy and foreground loss cancels without canceling caller`() = runTest {
        val f = fixture()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.coordinator.prepare()
        f.nano.stream = { flow { emit("partial"); awaitCancellation() } }
        var canceled = false
        val request = launch {
            try { f.coordinator.generate("hello").collect() }
            catch (_: CancellationException) { canceled = true }
            assertTrue(currentCoroutineContext().isActive)
        }
        runCurrent()
        assertTrue(f.coordinator.busy.value)
        assertFailure(FailureKind.BUSY) { f.coordinator.checkNano() }
        f.coordinator.setForeground(false)
        request.join()
        assertTrue(canceled)
        assertFalse(f.coordinator.busy.value)
        assertEquals(f.nano.opens, f.nano.closes)
        f.coordinator.setForeground(true)
        f.nano.stream = { flowOf("again") }
        f.coordinator.prepare()
        assertEquals(listOf("again"), f.coordinator.generate("hello").toList())
    }

    @Test fun `foreground loss cancels an emitting provider before publishing background state`() = runTest {
        val f = fixture()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.coordinator.prepare()
        lateinit var providerJob: Job
        f.nano.stream = { flow {
            providerJob = currentCoroutineContext().job
            while (true) {
                emit("x")
                delay(1)
            }
        } }
        val request = launch { f.coordinator.generate("x").collect() }
        runCurrent()
        var backgroundWithActiveProvider: Boolean? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            f.coordinator.foreground.first { !it }
            backgroundWithActiveProvider = providerJob.isActive
        }
        f.coordinator.setForeground(false)
        request.join()
        assertEquals(false, backgroundWithActiveProvider)
        assertFalse(f.coordinator.busy.value)
    }

    @Test fun `switch waits for native cancellation cleanup and rejects other work meanwhile`() = runTest {
        val f = fixture()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.coordinator.prepare()
        val cleanup = CompletableDeferred<Unit>()
        f.nano.stream = {
            flow {
                try { awaitCancellation() }
                finally { withContext(NonCancellable) { cleanup.await() } }
            }
        }
        val request = launch { f.coordinator.generate("hello").collect() }
        runCurrent()
        val switch = async { f.coordinator.selectProvider(ModelProvider.QWEN) }
        runCurrent()
        assertTrue(f.coordinator.busy.value)
        assertEquals(ModelProvider.GEMINI_NANO, f.repository.state.value.provider)
        assertEquals(0, f.engine.initializations)
        assertFailure(FailureKind.BUSY) { f.coordinator.prepare() }
        cleanup.complete(Unit)
        switch.await()
        request.join()
        assertEquals(ModelProvider.QWEN, f.repository.state.value.provider)
        assertEquals(f.nano.opens, f.nano.closes)
        f.coordinator.prepare()
        assertEquals(1, f.engine.initializations)
    }

    @Test fun `stop keeps arbiter busy until cleanup and permits repeated requests`() = runTest {
        val f = fixture()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.coordinator.prepare()
        val cleanup = CompletableDeferred<Unit>()
        f.nano.stream = { flow { try { awaitCancellation() } finally {
            withContext(NonCancellable) { cleanup.await() }
        } } }
        val request = launch { f.coordinator.generate("hello").collect() }
        runCurrent()
        request.cancel()
        runCurrent()
        assertTrue(f.coordinator.busy.value)
        assertFailure(FailureKind.BUSY) { f.coordinator.generate("second").collect() }
        cleanup.complete(Unit)
        request.join()
        assertFalse(f.coordinator.busy.value)
        f.nano.stream = { flowOf("new") }
        assertEquals(listOf("new"), f.coordinator.generate("hello").toList())
    }

    @Test fun `canceled setup allows explicit status retry after caller owned cleanup`() = runTest {
        for (download in listOf(false, true)) {
            val f = fixture()
            f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
            val cleanup = CompletableDeferred<Unit>()
            suspend fun interrupted(): Nothing {
                try { awaitCancellation() }
                finally { withContext(NonCancellable) { cleanup.await() } }
            }
            if (download) {
                f.nano.status = NanoState.Downloadable
                f.nano.onDownload = { interrupted() }
            } else {
                f.nano.onStatus = { interrupted() }
            }
            val request = launch {
                if (download) f.coordinator.downloadNano() else f.coordinator.checkNano()
            }
            runCurrent()
            request.cancel()
            runCurrent()
            assertTrue(f.coordinator.busy.value)
            assertFailure(FailureKind.BUSY) { f.coordinator.checkNano() }
            cleanup.complete(Unit)
            request.join()
            assertFalse(f.coordinator.busy.value)
            assertEquals(NanoState.Checking, f.coordinator.nanoState.value)
            assertEquals(1, f.nano.opens) // No implicit retry after canceled setup.
            assertEquals(f.nano.opens, f.nano.closes)
            f.nano.onStatus = null
            f.nano.status = NanoState.Ready
            f.coordinator.checkNano()
            assertEquals(NanoState.Ready, f.coordinator.nanoState.value)
        }
    }

    @Test fun `prompt token output and empty bounds fail clearly`() = runTest {
        val f = fixture()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.coordinator.prepare()
        assertFailure(FailureKind.INPUT_TOO_LONG) { f.coordinator.generate("x".repeat(6001)).collect() }
        assertFailure(FailureKind.INVALID_INPUT) { f.coordinator.generate(" ").collect() }
        f.nano.tokens = -1
        assertFailure(FailureKind.INPUT_TOO_LONG) { f.coordinator.generate("x").collect() }
        f.nano.tokens = 4000
        assertFailure(FailureKind.INPUT_TOO_LONG) { f.coordinator.generate("x").collect() }
        assertEquals(0, f.nano.generations)
        f.nano.tokens = 3999
        f.nano.stream = { flowOf("x".repeat(8001)) }
        assertFailure(FailureKind.OUTPUT_LIMIT) { f.coordinator.generate("x").collect() }
        f.nano.stream = { flowOf("", "   ") }
        assertFailure(FailureKind.EMPTY_OUTPUT) { f.coordinator.generate("x").collect() }
        f.nano.stream = { flowOf("valid") }
        assertEquals(listOf("valid"), f.coordinator.generate("x".repeat(6000)).toList())
    }

    @Test fun `failures preserve causes and never change provider selection`() = runTest {
        val f = fixture()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.coordinator.prepare()
        for (kind in FailureKind.entries.filter { it != FailureKind.PREFERENCE }) {
            val original = IllegalStateException("synthetic")
            val failure = InferenceFailure(kind, original)
            f.nano.stream = { flow { throw failure } }
            assertSame(failure, assertFailure(kind) { f.coordinator.generate("x").collect() })
            assertSame(original, failure.cause)
            assertEquals(ModelProvider.GEMINI_NANO, f.repository.state.value.provider)
            assertEquals(f.nano.opens, f.nano.closes)
        }
    }

    @Test fun `status generation and download timeouts free the arbiter after cleanup`() = runTest {
        val f = fixture()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.nano.onStatus = { awaitCancellation() }
        assertFailure(FailureKind.TIMEOUT) { f.coordinator.checkNano() }
        assertEquals(30_000, testScheduler.currentTime)
        f.nano.onStatus = null
        f.coordinator.prepare()
        f.nano.stream = { flow { awaitCancellation() } }
        assertFailure(FailureKind.TIMEOUT) { f.coordinator.generate("x").collect() }
        assertEquals(150_000, testScheduler.currentTime)
        f.nano.status = NanoState.Downloadable
        f.nano.onDownload = { awaitCancellation() }
        assertFailure(FailureKind.TIMEOUT) { f.coordinator.downloadNano() }
        assertEquals(1_050_000, testScheduler.currentTime)
        assertFalse(f.coordinator.busy.value)
        assertEquals(f.nano.opens, f.nano.closes)
    }

    @Test fun `status deadline includes client setup and still closes the opened client`() = runTest {
        val f = fixture()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.nano.beforeOpen = { testScheduler.advanceTimeBy(30_001) }
        assertFailure(FailureKind.TIMEOUT) { f.coordinator.checkNano() }
        assertEquals(f.nano.opens, f.nano.closes)
        assertFalse(f.coordinator.busy.value)
    }

    @Test fun `download precheck uses status deadline rather than download deadline`() = runTest {
        val f = fixture()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.nano.onStatus = { awaitCancellation() }
        assertFailure(FailureKind.TIMEOUT) { f.coordinator.downloadNano() }
        assertEquals(30_000, testScheduler.currentTime)
        assertEquals(0, f.nano.downloads)
        assertEquals(f.nano.opens, f.nano.closes)
    }

    @Test fun `SDK close failure preserves generation failure as primary`() = runTest {
        val f = fixture()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.coordinator.prepare()
        val original = InferenceFailure(FailureKind.SAFETY, IllegalStateException("policy"))
        val cleanup = IllegalStateException("close")
        f.nano.stream = { flow { emit("partial"); throw original } }
        f.nano.closeFailure = cleanup
        assertSame(original, assertFailure(FailureKind.SAFETY) { f.coordinator.generate("x").collect() })
        assertTrue(original.suppressed.contains(cleanup))
        assertFalse(f.coordinator.busy.value)
    }

    @Test fun `failed Nano cleanup prevents replacement work until explicit cleanup succeeds`() = runTest {
        val f = fixture()
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.coordinator.prepare()
        val cleanup = IllegalStateException("still allocated")
        f.nano.closeFailure = cleanup
        assertFailure(FailureKind.RUNTIME) { f.coordinator.generate("x").collect() }
        val opened = f.nano.opens
        val generations = f.nano.generations
        assertFailure(FailureKind.RUNTIME) { f.coordinator.prepare() }
        assertFailure(FailureKind.RUNTIME) { f.coordinator.selectProvider(ModelProvider.QWEN) }
        assertEquals(opened, f.nano.opens)
        assertEquals(generations, f.nano.generations)
        assertEquals(0, f.engine.initializations)
        assertEquals(ModelProvider.GEMINI_NANO, f.coordinator.selection.value.provider)
        f.nano.closeFailure = null
        f.coordinator.selectProvider(ModelProvider.QWEN)
        f.coordinator.prepare()
        assertEquals(1, f.engine.initializations)
    }

    @Test fun `Qwen download shares generation lease`() = runTest {
        val f = fixture()
        f.coordinator.prepare()
        f.engine.stream = { flow { awaitCancellation() } }
        val request = launch { f.coordinator.generate("x").collect() }
        runCurrent()
        assertFailure(FailureKind.BUSY) { f.coordinator.downloadQwen(ModelPreset.QWEN_2_5_1_5B).collect() }
        request.cancel()
        request.join()
        assertEquals(ProviderReadiness.Ready, f.coordinator.readiness.value)
        assertFalse(f.coordinator.busy.value)
    }

    @Test fun `Qwen initialization failure retains native cause and retry can succeed`() = runTest {
        val f = fixture()
        val cause = IllegalStateException("native init")
        f.engine.onInitialize = { throw cause }
        val failure = assertFailure(FailureKind.RUNTIME) { f.coordinator.prepare() }
        assertSame(cause, failure.cause)
        assertTrue(f.engine.closed)
        assertTrue(f.coordinator.readiness.value is ProviderReadiness.Failed)
        f.engine.onInitialize = {}
        f.coordinator.prepare()
        assertEquals(ProviderReadiness.Ready, f.coordinator.readiness.value)
    }

    @Test fun `late readiness observation cannot erase Qwen initialization timeout`() = runTest {
        val observerScheduler = TestCoroutineScheduler()
        val observerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(observerScheduler))
        try {
            val f = fixture(observerScope = observerScope)
            f.engine.onInitialize = { testScheduler.advanceTimeBy(InferenceCoordinator.INFERENCE_TIMEOUT_MS + 1) }
            val timeout = assertFailure(FailureKind.TIMEOUT) { f.coordinator.prepare() }
            observerScheduler.runCurrent()
            assertEquals(ProviderReadiness.Failed(timeout), f.coordinator.readiness.value)
            f.engine.onInitialize = {}
            f.coordinator.prepare()
            observerScheduler.runCurrent()
            assertEquals(ProviderReadiness.Ready, f.coordinator.readiness.value)
        } finally {
            observerScope.cancel()
        }
    }

    @Test fun `Qwen applies the same text input output and empty bounds`() = runTest {
        val f = fixture()
        f.coordinator.prepare()
        assertFailure(FailureKind.INPUT_TOO_LONG) { f.coordinator.generate("x".repeat(6001)).collect() }
        f.engine.stream = { flowOf("x".repeat(8001)) }
        assertFailure(FailureKind.OUTPUT_LIMIT) { f.coordinator.generate("x").collect() }
        f.engine.stream = { emptyFlow() }
        assertFailure(FailureKind.EMPTY_OUTPUT) { f.coordinator.generate("x").collect() }
    }

    @Test fun `preference read failure stays explicit and can be recovered by selection`() = runTest {
        val f = fixture(selectionText = "corrupt")
        runCurrent()
        assertNull(f.coordinator.selection.value.provider)
        val readiness = f.coordinator.readiness.value as ProviderReadiness.Failed
        assertEquals(FailureKind.PREFERENCE, readiness.failure.kind)
        assertSame(f.repository.state.value.failure, readiness.failure.cause)
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.coordinator.prepare()
        assertEquals(ProviderReadiness.Ready, f.coordinator.readiness.value)
    }

    @Test fun `failed Qwen metadata does not block explicitly selected Nano`() = runTest {
        val f = fixture(modelMetadata = "x".repeat(257))
        runCurrent()
        // A record over the storage bound is unusable, not a storage read failure.
        assertEquals(FailureKind.MODEL_RECORD,
            (f.coordinator.readiness.value as ProviderReadiness.Failed).failure.kind)
        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.coordinator.prepare()
        assertEquals(ProviderReadiness.Ready, f.coordinator.readiness.value)
        assertEquals(listOf("nano"), f.coordinator.generate("hello").toList())
        assertEquals(0, f.engine.initializations)
        assertEquals(0, f.nano.downloads)
    }

    @Test fun `a corrupt artifact is reported and never reaches native initialization`() = runTest {
        val f = fixture(corrupt = true)
        runCurrent()
        assertEquals(
            ArtifactDefect.DIGEST_MISMATCH,
            (f.coordinator.qwenArtifact.value as ModelArtifactState.Corrupt).defect,
        )
        assertEquals(
            FailureKind.MODEL_CORRUPT,
            (f.coordinator.readiness.value as ProviderReadiness.Failed).failure.kind,
        )
        assertFailure(FailureKind.MODEL_CORRUPT) { f.coordinator.prepare() }
        assertFailure(FailureKind.UNAVAILABLE) { f.coordinator.generate("hello").collect() }
        assertEquals(0, f.engine.initializations)
    }

    @Test fun `explicit reverification recovers a repaired artifact without any download`() = runTest {
        val f = fixture(corrupt = true)
        runCurrent()
        f.repairArtifact()

        f.coordinator.verifyQwen()

        assertEquals(ModelArtifactState.Ready(f.preset), f.coordinator.qwenArtifact.value)
        assertEquals(ProviderReadiness.NeedsInitialization, f.coordinator.readiness.value)
        f.coordinator.prepare()
        assertEquals(ProviderReadiness.Ready, f.coordinator.readiness.value)
    }

    @Test fun `verified readiness is withdrawn when the active artifact stops verifying`() = runTest {
        val f = fixture()
        f.coordinator.prepare()
        assertEquals(ProviderReadiness.Ready, f.coordinator.readiness.value)

        f.tamperWithArtifact()
        f.coordinator.verifyQwen()

        assertEquals(
            FailureKind.MODEL_CORRUPT,
            (f.coordinator.readiness.value as ProviderReadiness.Failed).failure.kind,
        )
        assertFailure(FailureKind.UNAVAILABLE) { f.coordinator.generate("hello").collect() }
    }

    @Test fun `discarding corrupt weights closes the engine and keeps the provider choice`() = runTest {
        val f = fixture()
        f.coordinator.prepare()
        f.tamperWithArtifact()
        f.coordinator.verifyQwen()
        val observed = mutableListOf<ModelArtifactState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            f.coordinator.qwenArtifact.collect { observed += it }
        }

        f.coordinator.discardRejectedQwen()

        assertEquals(ModelArtifactState.Missing, f.coordinator.qwenArtifact.value)
        assertEquals(ProviderReadiness.Missing, f.coordinator.readiness.value)
        assertTrue(observed.contains(ModelArtifactState.Removing))
        assertTrue(f.engine.closed)
        assertFalse(f.artifact.exists())
        assertEquals(ModelProvider.QWEN, f.coordinator.selection.value.provider)
    }

    @Test fun `an unusable activation record is cleared explicitly and is not a read failure`() =
        runTest {
            val f = fixture(modelMetadata = "x".repeat(257))
            runCurrent()
            assertEquals(
                FailureKind.MODEL_RECORD,
                (f.coordinator.readiness.value as ProviderReadiness.Failed).failure.kind,
            )

            f.coordinator.discardRejectedQwen()

            assertEquals(ModelArtifactState.Missing, f.coordinator.qwenArtifact.value)
            assertEquals(ProviderReadiness.Missing, f.coordinator.readiness.value)
            // The record named no approved model, so its file is not ours to delete.
            assertTrue(f.artifact.exists())
            assertEquals(0, f.engine.initializations)
        }

    @Test fun `a canceled discard still settles and never strands Removing readiness`() = runTest {
        val f = fixture(corrupt = true)
        runCurrent()
        lateinit var discard: Job
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            // Cancel only once the discard has actually begun and published Removing.
            f.coordinator.qwenArtifact.collect {
                if (it is ModelArtifactState.Removing) discard.cancel()
            }
        }
        discard = launch { f.coordinator.discardRejectedQwen() }
        discard.join()
        runCurrent()

        // The delete sequence is NonCancellable, so disk and state must still agree.
        assertNotEquals(ProviderReadiness.Removing, f.coordinator.readiness.value)
        assertEquals(ModelArtifactState.Missing, f.coordinator.qwenArtifact.value)
        assertFalse(f.artifact.exists())
    }

    @Test fun `automatic preparation never revalidates a rejected artifact`() = runTest {
        val f = fixture(corrupt = true)
        runCurrent()
        f.repairArtifact()

        assertFailure(FailureKind.MODEL_CORRUPT) { f.coordinator.prepare() }

        // Only the explicit control may re-hash; entry must not.
        assertTrue(f.coordinator.qwenArtifact.value is ModelArtifactState.Corrupt)
        f.coordinator.verifyQwen()
        assertEquals(ModelArtifactState.Ready(f.preset), f.coordinator.qwenArtifact.value)
    }

    @Test fun `discarding is refused for a verified installed artifact`() = runTest {
        val f = fixture()
        runCurrent()

        f.coordinator.discardRejectedQwen()

        assertEquals(ModelArtifactState.Ready(f.preset), f.coordinator.qwenArtifact.value)
        assertTrue(f.artifact.exists())
    }

    @Test fun `Nano stays independently usable while the Qwen artifact is corrupt`() = runTest {
        val f = fixture(corrupt = true)
        runCurrent()

        f.coordinator.selectProvider(ModelProvider.GEMINI_NANO)
        f.coordinator.prepare()

        assertEquals(ProviderReadiness.Ready, f.coordinator.readiness.value)
        assertEquals(listOf("nano"), f.coordinator.generate("hello").toList())
        assertEquals(0, f.engine.initializations)
        assertEquals(0, f.nano.downloads)
    }

    @Test fun `verification and native initialization never dispatch to the main thread`() = runTest {
        Dispatchers.setMain(RejectingDispatcher)
        try {
            val f = fixture()
            f.coordinator.verifyQwen()
            f.coordinator.prepare()

            assertEquals(ProviderReadiness.Ready, f.coordinator.readiness.value)
            assertEquals(listOf("qwen"), f.coordinator.generate("hello").toList())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private object RejectingDispatcher : CoroutineDispatcher() {
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable): Unit =
            throw AssertionError("Model verification and engine work must stay off the main thread")
    }

    private suspend fun assertFailure(kind: FailureKind, block: suspend () -> Unit): InferenceFailure {
        try { block(); fail("Expected $kind") } catch (failure: InferenceFailure) {
            assertEquals(kind, failure.kind)
            return failure
        }
        error("unreachable")
    }

    private fun TestScope.fixture(
        installed: Boolean = true,
        foreground: Boolean = true,
        selectionText: String? = null,
        observerScope: CoroutineScope = backgroundScope,
        modelMetadata: String? = null,
        corrupt: Boolean = false,
    ): Fixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val providerFile = File(temporaryFolder.newFolder(), "provider")
        selectionText?.let(providerFile::writeText)
        val repository = ProviderSelectionRepository(providerFile, backgroundScope, dispatcher)
        val modelsDir = temporaryFolder.newFolder()
        // The fixture artifact carries its own digest: a tiny file still has to verify.
        val preset = ModelPreset.QWEN_2_5_1_5B.copy(
            fileName = "tiny.litertlm",
            expectedBytes = VERIFIED_FIXTURE.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(VERIFIED_FIXTURE).toHexString(),
        )
        if (installed) {
            File(modelsDir, "active-model").writeText(preset.id)
            File(modelsDir, preset.fileName)
                .writeBytes(if (corrupt) TAMPERED_FIXTURE else VERIFIED_FIXTURE)
        }
        modelMetadata?.let { File(modelsDir, "active-model").writeText(it) }
        val models = ModelManager(modelsDir, OkHttpClient(), listOf(preset), backgroundScope, dispatcher)
        val engine = FakeQwen()
        val manager = LlmEngineManager(dispatcher) { engine }
        val nano = FakeNano()
        val coordinator = InferenceCoordinator(repository, models, manager, NanoClientFactory(nano::open), observerScope, dispatcher)
        coordinator.setForeground(foreground)
        return Fixture(repository, coordinator, nano, engine, File(modelsDir, preset.fileName), preset)
    }

    private data class Fixture(
        val repository: ProviderSelectionRepository,
        val coordinator: InferenceCoordinator,
        val nano: FakeNano,
        val engine: FakeQwen,
        val artifact: File,
        val preset: ModelPreset,
    ) {
        fun tamperWithArtifact() = artifact.writeBytes(TAMPERED_FIXTURE)
        fun repairArtifact() = artifact.writeBytes(VERIFIED_FIXTURE)
    }

    private class FakeQwen : ManagedEngine {
        var initializations = 0
        var closed = false
        var onInitialize: () -> Unit = {}
        var stream: () -> Flow<String> = { flowOf("qwen") }
        override fun initialize() { initializations++; closed = false; onInitialize() }
        override fun close() { closed = true }
        override fun createConversation() = object : ManagedConversation {
            override fun generate(prompt: String) = stream()
            override fun close() = Unit
        }
    }

    private class FakeNano {
        var status: NanoState = NanoState.Ready
        var opens = 0
        var closes = 0
        var downloads = 0
        var generations = 0
        var tokens = 20
        var beforeOpen: () -> Unit = {}
        var closeFailure: Exception? = null
        var onStatus: (suspend () -> NanoState)? = null
        var onDownload: (suspend () -> Unit)? = null
        var stream: () -> Flow<String> = { flowOf("nano") }
        fun open(): NanoClient {
            beforeOpen()
            opens++
            return object : NanoClient {
                override suspend fun checkStatus(): NanoState = onStatus?.invoke() ?: status
                override suspend fun download() {
                    downloads++
                    onDownload?.invoke()
                    status = NanoState.Ready
                }
                override suspend fun countTokens(prompt: String) = tokens
                override fun generate(prompt: String): Flow<String> {
                    generations++
                    return stream()
                }
                override fun close() { closes++; closeFailure?.let { throw it } }
            }
        }
    }

    private companion object {
        val VERIFIED_FIXTURE = "verified fixture weights".toByteArray()
        val TAMPERED_FIXTURE = "TAMPERED fixture weights".toByteArray()
    }
}
