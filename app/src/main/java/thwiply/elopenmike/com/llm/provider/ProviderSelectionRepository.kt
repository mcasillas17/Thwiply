package thwiply.elopenmike.com.llm.provider

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ModelProvider(val id: String) {
    QWEN("qwen"),
    GEMINI_NANO("gemini-nano"),
}

data class ProviderSelection(val provider: ModelProvider?, val failure: Throwable? = null)

@Singleton
class ProviderSelectionRepository internal constructor(
    private val file: File,
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        File(context.noBackupFilesDir, "model-provider"),
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        Dispatchers.IO,
    )

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ProviderSelection(null))
    val state = mutableState.asStateFlow()
    private val initialRead = scope.launch(dispatcher) {
        mutex.withLock {
            mutableState.value = try {
                ProviderSelection(read())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                ProviderSelection(null, failure)
            }
        }
    }

    internal suspend fun awaitLoaded() = initialRead.join()

    suspend fun select(provider: ModelProvider): Boolean {
        initialRead.join()
        return mutex.withLock {
            // A canceled caller must not leave disk and the authoritative state disagreeing.
            withContext(dispatcher + NonCancellable) {
                try {
                    write(provider)
                    mutableState.value = ProviderSelection(provider)
                    true
                } catch (failure: Exception) {
                    mutableState.value = mutableState.value.copy(failure = failure)
                    false
                }
            }
        }
    }

    private fun read(): ModelProvider {
        val bytes = try {
            Files.newInputStream(file.toPath()).use { input ->
                val buffer = ByteArray(33)
                var length = 0
                while (length < buffer.size) {
                    val count = input.read(buffer, length, buffer.size - length)
                    if (count == -1) break
                    length += count
                }
                if (length > 32) throw IOException("Provider selection exceeds storage bound")
                buffer.copyOf(length)
            }
        } catch (_: NoSuchFileException) {
            return ModelProvider.QWEN
        }
        val id = bytes.toString(Charsets.UTF_8)
        return ModelProvider.entries.firstOrNull { it.id == id }
            ?: throw IOException("Unknown provider selection")
    }

    private fun write(provider: ModelProvider) {
        val parent = requireNotNull(file.absoluteFile.parentFile).toPath()
        Files.createDirectories(parent)
        val candidate = Files.createTempFile(parent, "model-provider-", ".tmp")
        var primary: Throwable? = null
        try {
            FileOutputStream(candidate.toFile()).use {
                it.write(provider.id.toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            Files.move(candidate, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try {
                Files.deleteIfExists(candidate)
            } catch (cleanup: Exception) {
                if (primary != null) primary.addSuppressed(cleanup) else throw cleanup
            }
        }
    }
}
