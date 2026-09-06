package thwiply.elopenmike.com.llm.provider

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderSelectionRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `unset selects Qwen only after initial read succeeds`() = runTest {
        val file = File(temporaryFolder.newFolder(), "provider")
        val repository = ProviderSelectionRepository(file, backgroundScope, StandardTestDispatcher(testScheduler))
        assertNull(repository.state.value.provider)
        runCurrent()
        assertEquals(ProviderSelection(ModelProvider.QWEN), repository.state.value)
        assertFalse(file.exists())
    }

    @Test fun `Nano selection survives restart without touching neighboring files`() = runTest {
        val directory = temporaryFolder.newFolder()
        val neighbor = File(directory, "model.litertlm").apply { writeText("untouched") }
        val file = File(directory, "provider")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = ProviderSelectionRepository(file, backgroundScope, dispatcher)
        assertTrue(repository.select(ModelProvider.GEMINI_NANO))
        assertEquals("gemini-nano", file.readText())
        val restarted = ProviderSelectionRepository(file, backgroundScope, dispatcher)
        runCurrent()
        assertEquals(ModelProvider.GEMINI_NANO, restarted.state.value.provider)
        assertEquals("untouched", neighbor.readText())
        assertEquals(setOf("provider", "model.litertlm"), directory.list()!!.toSet())
    }

    @Test fun `unknown oversized and unreadable states do not silently select Qwen`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        for (contents in listOf("unknown", "qwen\n", "x".repeat(1000))) {
            val file = temporaryFolder.newFile().apply { writeText(contents) }
            val repository = ProviderSelectionRepository(file, backgroundScope, dispatcher)
            runCurrent()
            assertNull(repository.state.value.provider)
            assertNotNull(repository.state.value.failure)
            assertTrue(repository.select(ModelProvider.GEMINI_NANO))
            assertEquals(ProviderSelection(ModelProvider.GEMINI_NANO), repository.state.value)
        }
        val directoryInsteadOfFile = temporaryFolder.newFolder()
        val repository = ProviderSelectionRepository(directoryInsteadOfFile, backgroundScope, dispatcher)
        runCurrent()
        assertNull(repository.state.value.provider)
        assertNotNull(repository.state.value.failure)
    }

    @Test fun `write failure preserves selected provider and exposes original disk exception`() = runTest {
        val file = File(temporaryFolder.newFolder(), "provider")
        val repository = ProviderSelectionRepository(file, backgroundScope, StandardTestDispatcher(testScheduler))
        assertTrue(repository.select(ModelProvider.GEMINI_NANO))
        assertTrue(file.delete())
        assertTrue(file.mkdir())
        assertFalse(repository.select(ModelProvider.QWEN))
        assertEquals(ModelProvider.GEMINI_NANO, repository.state.value.provider)
        assertTrue(repository.state.value.failure is java.io.IOException)
    }

    @Test fun `selection waits for initial read rather than being overwritten by it`() = runTest {
        val file = temporaryFolder.newFile().apply { writeText("gemini-nano") }
        val repository = ProviderSelectionRepository(file, backgroundScope, StandardTestDispatcher(testScheduler))
        val selected = async { repository.select(ModelProvider.QWEN) }
        runCurrent()
        assertTrue(selected.await())
        assertEquals(ProviderSelection(ModelProvider.QWEN), repository.state.value)
        assertEquals("qwen", file.readText())
    }
}
