package thwiply.elopenmike.com.llm.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LlmEngineManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `generation closes its conversation`() = runBlocking {
        val engine = FakeEngine()
        val manager = LlmEngineManager { engine }
        val model = temporaryFolder.newFile("model.litertlm")
        assertTrue(manager.initialize(model, model.absolutePath).isSuccess)

        val output = manager.generateStream("hello").toList()

        assertEquals(listOf("response"), output)
        assertTrue(engine.conversations.single().closed)
    }

    @Test
    fun `initializing a different model closes the previous engine`() = runBlocking {
        val engines = mutableListOf<FakeEngine>()
        val manager = LlmEngineManager {
            FakeEngine().also(engines::add)
        }
        val first = temporaryFolder.newFile("first.litertlm")
        val second = temporaryFolder.newFile("second.litertlm")

        assertTrue(manager.initialize(first, first.absolutePath).isSuccess)
        assertTrue(manager.initialize(second, second.absolutePath).isSuccess)

        assertEquals(2, engines.size)
        assertTrue(engines.first().closed)
        assertEquals(EngineState.Ready(second.absolutePath), manager.state.value)
    }

    @Test
    fun `replacing the artifact at one path cannot reuse the engine loaded from other bytes`() =
        runBlocking {
            val engines = mutableListOf<FakeEngine>()
            val manager = LlmEngineManager { FakeEngine().also(engines::add) }
            val model = temporaryFolder.newFile("model.litertlm")

            assertTrue(manager.initialize(model, "qwen:first-digest").isSuccess)
            assertTrue(manager.initialize(model, "qwen:second-digest").isSuccess)

            assertEquals(2, engines.size)
            assertTrue(engines.first().closed)
            assertEquals(EngineState.Ready("qwen:second-digest"), manager.state.value)
        }

    @Test
    fun `re-initializing the same verified content reuses the loaded engine`() = runBlocking {
        val engines = mutableListOf<FakeEngine>()
        val manager = LlmEngineManager { FakeEngine().also(engines::add) }
        val model = temporaryFolder.newFile("model.litertlm")

        assertTrue(manager.initialize(model, "qwen:same-digest").isSuccess)
        assertTrue(manager.initialize(model, "qwen:same-digest").isSuccess)

        assertEquals(1, engines.size)
    }

    private class FakeEngine : ManagedEngine {
        val conversations = mutableListOf<FakeConversation>()
        var closed = false

        override fun initialize() = Unit

        override fun createConversation(): ManagedConversation =
            FakeConversation().also(conversations::add)

        override fun close() {
            closed = true
        }
    }

    private class FakeConversation : ManagedConversation {
        var closed = false

        override fun generate(prompt: String): Flow<String> = flowOf("response")

        override fun close() {
            closed = true
        }
    }
}
