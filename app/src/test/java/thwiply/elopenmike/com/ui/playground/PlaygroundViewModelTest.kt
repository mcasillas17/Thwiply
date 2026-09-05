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

@OptIn(ExperimentalCoroutinesApi::class)
class PlaygroundViewModelTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `missing model rejects inference even when called outside the UI`() = runTest {
        val engine = LlmEngineManager { error("Must not initialize") }
        val vm = PlaygroundViewModel(engine, models(installed = false), StandardTestDispatcher(testScheduler))
        vm.prepareEngine()
        vm.generate("manual work must remain available", false)
        runCurrent()
        assertEquals(LabReadiness.Missing, vm.readiness.value)
        assertFalse(vm.isGenerating.value)
        assertEquals("", vm.output.value)
    }

    @Test fun `installed model needs successful initialization and failure supports retry`() = runTest {
        var attempts = 0
        val fake = FakeEngine()
        val engine = LlmEngineManager {
            if (++attempts == 1) throw IllegalStateException("Native init failed")
            fake
        }
        val vm = PlaygroundViewModel(engine, models(true), StandardTestDispatcher(testScheduler))
        vm.generate("too early", false)
        vm.prepareEngine()
        runCurrent()
        assertTrue(vm.readiness.value is LabReadiness.Failed)
        vm.generate("still not ready", false)
        runCurrent()
        assertEquals(0, fake.generations)
        vm.prepareEngine()
        runCurrent()
        assertEquals(LabReadiness.Ready, vm.readiness.value)
        vm.generate("hello", false)
        vm.generate("duplicate", false)
        vm.isGenerating.first { !it }
        assertEquals("response", vm.output.value)
        assertEquals(1, fake.generations)
    }

    @Test fun `readiness rejects an engine for a different installed path`() = runTest {
        val engine = LlmEngineManager { FakeEngine() }
        engine.initialize(temporaryFolder.newFile("other.litertlm"))
        val vm = PlaygroundViewModel(engine, models(true), StandardTestDispatcher(testScheduler))
        vm.generate("wrong engine", false)
        runCurrent()
        assertEquals(LabReadiness.NeedsInitialization, vm.readiness.value)
        assertFalse(vm.isGenerating.value)
    }

    @Test fun `generation cancellation propagates without becoming an error response`() = runTest {
        val engine = LlmEngineManager { FakeEngine(cancel = true) }
        val vm = PlaygroundViewModel(engine, models(true), StandardTestDispatcher(testScheduler))
        vm.prepareEngine()
        runCurrent()
        vm.generate("hello", false)
        vm.isGenerating.first { !it }
        assertFalse(vm.isGenerating.value)
        assertEquals("", vm.output.value)
        assertNull(vm.generationFailure.value)
    }

    // Exercises the existing length-based restart adoption, not new digest verification.
    private fun models(installed: Boolean): ModelManager {
        val directory = temporaryFolder.newFolder()
        val preset = ModelPreset.QWEN_2_5_1_5B.copy(fileName = "test.litertlm", expectedBytes = 4)
        if (installed) {
            File(directory, "active-model").writeText(preset.id)
            File(directory, preset.fileName).writeText("test")
        }
        return ModelManager(directory, OkHttpClient(), listOf(preset))
    }

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
}
