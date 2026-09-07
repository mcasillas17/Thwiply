package thwiply.elopenmike.com.llm.engine

import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LlmEngineLifecycleTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `native initialize and close run on supplied worker dispatcher`() = runBlocking {
        Executors.newSingleThreadExecutor { Thread(it, "qwen-worker") }.asCoroutineDispatcher().use { dispatcher ->
            val threads = mutableListOf<String>()
            val manager = LlmEngineManager(dispatcher) {
                object : ManagedEngine {
                    override fun initialize() { threads += Thread.currentThread().name }
                    override fun close() { threads += Thread.currentThread().name }
                    override fun createConversation(): ManagedConversation = error("unused")
                }
            }
            assertTrue(manager.initialize(temporaryFolder.newFile()).isSuccess)
            manager.close()
            assertEquals(2, threads.size)
            assertTrue(threads.all { it.substringBefore(" @") == "qwen-worker" })
        }
    }

    @Test fun `initialization cancellation propagates and closes candidate`() = runBlocking {
        var closed = false
        val cancellation = CancellationException("native cancellation")
        val manager = LlmEngineManager {
            object : ManagedEngine {
                override fun initialize(): Unit = throw cancellation
                override fun close() { closed = true }
                override fun createConversation(): ManagedConversation = error("unused")
            }
        }
        try {
            manager.initialize(temporaryFolder.newFile())
            fail("Cancellation was swallowed")
        } catch (caught: CancellationException) {
            // Coroutine stack recovery may copy CancellationException across dispatchers.
            assertTrue(caught === cancellation || caught.cause === cancellation)
        }
        assertTrue(closed)
        assertEquals(EngineState.Idle, manager.state.value)
    }

    @Test fun `cleanup failure does not replace original initialization cause`() = runBlocking {
        val original = IllegalStateException("init")
        val close = IllegalStateException("close")
        val manager = LlmEngineManager {
            object : ManagedEngine {
                override fun initialize(): Unit = throw original
                override fun close(): Unit = throw close
                override fun createConversation(): ManagedConversation = error("unused")
            }
        }
        val result = manager.initialize(temporaryFolder.newFile())
        assertSame(original, result.exceptionOrNull())
        assertTrue(original.suppressed.contains(close))
        assertSame(original, (manager.state.value as EngineState.Failed).cause)
    }

    @Test fun `cancel during blocking native initialize waits for unwind then closes candidate`() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val entered = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            var closed = false
            val manager = LlmEngineManager(dispatcher) {
                object : ManagedEngine {
                    override fun initialize() {
                        entered.complete(Unit)
                        check(release.await(5, TimeUnit.SECONDS))
                    }
                    override fun close() { closed = true }
                    override fun createConversation(): ManagedConversation = error("unused")
                }
            }
            val request = launch { manager.initialize(temporaryFolder.newFile()) }
            try {
                entered.await()
                request.cancel()
                assertFalse(request.isCompleted)
                assertFalse(closed)
            } finally {
                release.countDown()
                request.join()
            }
            assertTrue(closed)
            assertEquals(EngineState.Idle, manager.state.value)
        }
    }

    @Test fun `failed native close retains ownership before replacement is allowed`() = runBlocking {
        var factories = 0
        var closes = 0
        val manager = LlmEngineManager {
            factories++
            object : ManagedEngine {
                override fun initialize() = Unit
                override fun close() {
                    closes++
                    if (closes <= 2) error("Synthetic close failure")
                }
                override fun createConversation(): ManagedConversation = error("unused")
            }
        }
        manager.initialize(temporaryFolder.newFile())
        try { manager.close(); fail("Expected close failure") } catch (_: IllegalStateException) { }
        assertTrue(manager.initialize(temporaryFolder.newFile()).isFailure)
        assertEquals(1, factories)
        assertTrue(manager.initialize(temporaryFolder.newFile()).isSuccess)
        assertEquals(2, factories)
        assertEquals(3, closes)
    }
}
