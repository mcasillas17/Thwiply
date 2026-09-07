package thwiply.elopenmike.com.data.preferences

import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import thwiply.elopenmike.com.llm.provider.ModelProvider
import thwiply.elopenmike.com.llm.provider.ProviderSelectionRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AppPreferencesRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun TestScope.repository(storage: PreferenceStorage) =
        AppPreferencesRepository(storage, backgroundScope, StandardTestDispatcher(testScheduler))

    private fun file() = File(temporaryFolder.newFolder(), "app-preferences")

    @Test fun `missing is loaded defaults not a read failure and does not write`() = runTest {
        val file = file()
        val repository = repository(FilePreferenceStorage(file))
        assertNull(repository.state.value.values)
        runCurrent()
        assertEquals(PreferenceState(AppPreferences()), repository.state.value)
        assertFalse(file.exists())
    }

    @Test fun `theme and education roundtrip across repository recreation`() = runTest {
        val file = file()
        for (theme in ThemeMode.entries) {
            val repository = repository(FilePreferenceStorage(file))
            assertTrue(repository.setTheme(theme))
            assertTrue(repository.acknowledgeModelSetupEducation())
            val recreated = repository(FilePreferenceStorage(file))
            runCurrent()
            assertEquals(AppPreferences(theme, MODEL_SETUP_EDUCATION_VERSION), recreated.state.value.values)
            assertTrue(recreated.state.value.values!!.hasSeenModelSetupEducation)
            assertEquals(setOf("app-preferences"), file.parentFile!!.list()!!.toSet())
        }
    }

    @Test fun `malformed and unsupported bytes are preserved until confirmed reset`() = runTest {
        val cases = mapOf(
            "" to PreferenceFailureReason.MALFORMED,
            "version=\ntheme=dark\nsetupEducation=0\n" to PreferenceFailureReason.MALFORMED,
            "version=broken\ntheme=dark\nsetupEducation=0\n" to PreferenceFailureReason.MALFORMED,
            "version=1\ntheme=dark\n" to PreferenceFailureReason.MALFORMED,
            "version=1\ntheme=dark\nsetupEducation=-1\n" to PreferenceFailureReason.MALFORMED,
            "version=1\ntheme=dark\nsetupEducation=999999999999999\n" to PreferenceFailureReason.MALFORMED,
            "version=1\ntheme=dark\nsetupEducation=0\nextra=secret\n" to PreferenceFailureReason.MALFORMED,
            "x".repeat(257) to PreferenceFailureReason.MALFORMED,
            "version=2\ntheme=dark\nsetupEducation=0\n" to PreferenceFailureReason.UNSUPPORTED,
            "version=1\ntheme=sepia\nsetupEducation=0\n" to PreferenceFailureReason.UNSUPPORTED,
        )
        for ((bytes, reason) in cases) {
            val file = file().apply { writeText(bytes) }
            val repository = repository(FilePreferenceStorage(file))
            runCurrent()
            assertNull(repository.state.value.values)
            assertEquals(reason, repository.state.value.failure!!.reason)
            assertFalse(repository.setTheme(ThemeMode.LIGHT))
            assertFalse(repository.acknowledgeModelSetupEducation())
            assertEquals(bytes, file.readText())
            assertTrue(repository.resetPreferences())
            assertEquals(AppPreferences(), repository.state.value.values)
            assertEquals(AppPreferences(), FilePreferenceStorage(file).read())
        }
    }

    @Test fun `I O and access failures stay distinct from unset and can retry`() = runTest {
        for (error in listOf(IOException("private path"), SecurityException("private path"))) {
            var fail = true
            val storage = object : PreferenceStorage {
                override suspend fun read(): AppPreferences {
                    if (fail) throw error
                    return AppPreferences(ThemeMode.DARK)
                }
                override suspend fun write(value: AppPreferences) = error("Must not overwrite unread preferences")
            }
            val repository = repository(storage)
            runCurrent()
            assertNull(repository.state.value.values)
            assertEquals(PreferenceFailureReason.READ, repository.state.value.failure!!.reason)
            assertSame(error, repository.state.value.failure!!.cause)
            assertFalse(repository.setTheme(ThemeMode.LIGHT))
            fail = false
            repository.reload()
            assertEquals(PreferenceState(AppPreferences(ThemeMode.DARK)), repository.state.value)
        }
    }

    @Test fun `real unreadable path is not missing`() = runTest {
        val repository = repository(FilePreferenceStorage(temporaryFolder.newFolder()))
        runCurrent()
        assertNull(repository.state.value.values)
        assertEquals(PreferenceFailureReason.READ, repository.state.value.failure!!.reason)
    }

    @Test fun `failed writes retain last saved values and cause then recover`() = runTest {
        val file = file()
        val real = FilePreferenceStorage(file)
        val failure = IOException("sensitive path")
        var fail = false
        val repository = repository(object : PreferenceStorage {
            override suspend fun read() = real.read()
            override suspend fun write(value: AppPreferences) {
                if (fail) throw failure
                real.write(value)
            }
        })
        assertTrue(repository.setTheme(ThemeMode.DARK))
        fail = true
        assertFalse(repository.setTheme(ThemeMode.LIGHT))
        assertEquals(ThemeMode.DARK, repository.state.value.values!!.theme)
        assertEquals(ThemeMode.DARK, real.read().theme)
        assertEquals(PreferenceFailureReason.WRITE, repository.state.value.failure!!.reason)
        assertSame(failure, repository.state.value.failure!!.cause)
        fail = false
        assertTrue(repository.setTheme(ThemeMode.LIGHT))
        assertNull(repository.state.value.failure)
    }

    @Test fun `atomic move failure preserves target and cleans candidate`() = runTest {
        val file = file().apply { mkdir(); resolve("sentinel").writeText("keep") }
        val repository = repository(FilePreferenceStorage(file))
        runCurrent()
        assertFalse(repository.resetPreferences())
        assertEquals("keep", file.resolve("sentinel").readText())
        assertEquals(setOf("app-preferences"), file.parentFile!!.list()!!.toSet())
    }

    @Test fun `failed reset cannot make a failed read safe for ordinary updates`() = runTest {
        var readable = true
        var writes = 0
        val saved = AppPreferences(ThemeMode.DARK, MODEL_SETUP_EDUCATION_VERSION)
        val repository = repository(object : PreferenceStorage {
            override suspend fun read(): AppPreferences {
                if (!readable) throw IOException("temporarily unreadable")
                return saved
            }
            override suspend fun write(value: AppPreferences) {
                writes++
                throw IOException("cannot reset")
            }
        })
        runCurrent()
        readable = false
        assertFalse(repository.reload())
        assertFalse(repository.resetPreferences())
        assertEquals(saved, repository.state.value.values)
        assertFalse(repository.state.value.canUpdate)
        assertFalse(repository.setTheme(ThemeMode.LIGHT))
        assertEquals(1, writes)
        readable = true
        assertTrue(repository.reload())
        assertEquals(PreferenceState(saved), repository.state.value)
        assertEquals(1, writes)
    }

    @Test fun `interrupted candidate is ignored on read and reclaimed by next write`() = runTest {
        val file = file()
        val storage = FilePreferenceStorage(file)
        storage.write(AppPreferences(ThemeMode.DARK))
        val candidate = File(file.parentFile, "${file.name}.tmp")
        val unrelated = File(file.parentFile, "model-provider.tmp").apply { writeText("keep") }
        for (interruptedBytes in listOf("version=", "version=1\ntheme=light\nsetupEducation=1\n")) {
            candidate.writeText(interruptedBytes)
            assertEquals(AppPreferences(ThemeMode.DARK), storage.read())
            storage.write(AppPreferences(ThemeMode.DARK))
            assertFalse(candidate.exists())
            assertEquals("keep", unrelated.readText())
        }
    }

    @Test fun `rapid concurrent updates retain both fields and final selection`() = runTest {
        val file = file()
        val repository = repository(FilePreferenceStorage(file))
        val jobs = (0 until 100).map { index ->
            async {
                if (index % 2 == 0) repository.acknowledgeModelSetupEducation()
                else repository.setTheme(ThemeMode.entries[index % 3])
            }
        }
        jobs.awaitAll().forEach { assertTrue(it) }
        assertEquals(AppPreferences(ThemeMode.SYSTEM, MODEL_SETUP_EDUCATION_VERSION), repository.state.value.values)
        assertTrue(repository.setTheme(ThemeMode.DARK))
        val expected = AppPreferences(ThemeMode.DARK, MODEL_SETUP_EDUCATION_VERSION)
        assertEquals(expected, repository.state.value.values)
        assertEquals(expected, FilePreferenceStorage(file).read())
    }

    @Test fun `user write waits for initial read and preserves saved education`() = runTest {
        val readGate = CompletableDeferred<Unit>()
        var saved = AppPreferences(ThemeMode.DARK, MODEL_SETUP_EDUCATION_VERSION)
        val repository = repository(object : PreferenceStorage {
            override suspend fun read(): AppPreferences { readGate.await(); return saved }
            override suspend fun write(value: AppPreferences) { saved = value }
        })
        val write = async { repository.setTheme(ThemeMode.LIGHT) }
        runCurrent()
        assertFalse(write.isCompleted)
        readGate.complete(Unit)
        assertTrue(write.await())
        assertEquals(AppPreferences(ThemeMode.LIGHT, MODEL_SETUP_EDUCATION_VERSION), saved)
        assertEquals(saved, repository.state.value.values)
    }

    @Test fun `education version changes require explanation again without changing theme`() = runTest {
        for (version in listOf(0, MODEL_SETUP_EDUCATION_VERSION + 1)) {
            val file = file()
            FilePreferenceStorage(file).write(AppPreferences(ThemeMode.DARK, version))
            val repository = repository(FilePreferenceStorage(file))
            runCurrent()
            assertFalse(repository.state.value.values!!.hasSeenModelSetupEducation)
            assertTrue(repository.acknowledgeModelSetupEducation())
            assertEquals(AppPreferences(ThemeMode.DARK, MODEL_SETUP_EDUCATION_VERSION), repository.state.value.values)
        }
    }

    @Test fun `cancellation while waiting for initialization never writes`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val repository = repository(object : PreferenceStorage {
            override suspend fun read(): AppPreferences { gate.await(); return AppPreferences() }
            override suspend fun write(value: AppPreferences) { writes++ }
        })
        val job = launch { repository.setTheme(ThemeMode.DARK) }
        runCurrent()
        job.cancelAndJoin()
        gate.complete(Unit)
        runCurrent()
        assertEquals(0, writes)
        assertNull(repository.state.value.failure)
    }

    @Test fun `cancellation during commit finishes disk and state then propagates`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var saved = AppPreferences()
        var entered = false
        val repository = repository(object : PreferenceStorage {
            override suspend fun read() = saved
            override suspend fun write(value: AppPreferences) {
                entered = true
                gate.await()
                saved = value
            }
        })
        var returned = false
        val job = launch { repository.setTheme(ThemeMode.DARK); returned = true }
        runCurrent()
        assertTrue(entered)
        job.cancel()
        val queued = launch { repository.setTheme(ThemeMode.LIGHT) }
        runCurrent()
        queued.cancelAndJoin()
        gate.complete(Unit)
        job.join()
        assertFalse(returned)
        assertEquals(AppPreferences(ThemeMode.DARK), saved)
        assertEquals(PreferenceState(saved), repository.state.value)
    }

    @Test fun `storage cancellation is not reported as a read or write failure`() = runTest {
        val repository = repository(object : PreferenceStorage {
            override suspend fun read() = AppPreferences()
            override suspend fun write(value: AppPreferences): Unit = throw CancellationException()
        })
        runCurrent()
        val job = launch { repository.setTheme(ThemeMode.DARK) }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(PreferenceState(AppPreferences()), repository.state.value)
    }

    @Test fun `cancelled initial read propagates to writers instead of inventing defaults`() = runTest {
        var writes = 0
        val repository = repository(object : PreferenceStorage {
            override suspend fun read(): AppPreferences = throw CancellationException()
            override suspend fun write(value: AppPreferences) { writes++ }
        })
        runCurrent()
        val job = launch { repository.setTheme(ThemeMode.DARK) }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(PreferenceState(), repository.state.value)
        assertEquals(0, writes)
    }

    @Test fun `retry cancellation retains previous values and does not fabricate failure`() = runTest {
        var cancelRead = false
        val repository = repository(object : PreferenceStorage {
            override suspend fun read(): AppPreferences {
                if (cancelRead) throw CancellationException()
                return AppPreferences(ThemeMode.DARK)
            }
            override suspend fun write(value: AppPreferences) = Unit
        })
        runCurrent()
        cancelRead = true
        val job = launch { repository.reload() }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(PreferenceState(AppPreferences(ThemeMode.DARK)), repository.state.value)
    }

    @Test fun `all storage operations run on supplied I O dispatcher`() = runTest {
        lateinit var ioThread: Thread
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "preference-io-test").also { ioThread = it }
        }
        executor.asCoroutineDispatcher().use { io ->
            val threads = mutableListOf<Thread>()
            val repository = AppPreferencesRepository(object : PreferenceStorage {
                override suspend fun read(): AppPreferences {
                    threads += Thread.currentThread()
                    return AppPreferences()
                }
                override suspend fun write(value: AppPreferences) {
                    threads += Thread.currentThread()
                }
            }, backgroundScope, io)
            repository.setTheme(ThemeMode.DARK)
            repository.reload()
            assertEquals(listOf(ioThread, ioThread, ioThread), threads)
        }
    }

    @Test fun `preferences preserve saved Nano choice model weights and task storage`() = runTest {
        val directory = temporaryFolder.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val providerFile = File(directory, "model-provider")
        val provider = ProviderSelectionRepository(providerFile, backgroundScope, dispatcher)
        assertTrue(provider.select(ModelProvider.GEMINI_NANO))
        val weights = File(directory, "model.litertlm").apply { writeText("weights") }
        val tasks = File(directory, "tasks.db").apply { writeText("manual tasks") }
        val repository = repository(FilePreferenceStorage(File(directory, "app-preferences")))
        repository.setTheme(ThemeMode.DARK)
        repository.acknowledgeModelSetupEducation()
        repository.resetPreferences()
        val recreated = ProviderSelectionRepository(providerFile, backgroundScope, dispatcher)
        runCurrent()
        assertEquals(ModelProvider.GEMINI_NANO, recreated.state.value.provider)
        assertEquals("gemini-nano", providerFile.readText())
        assertEquals("weights", weights.readText())
        assertEquals("manual tasks", tasks.readText())
    }
}
