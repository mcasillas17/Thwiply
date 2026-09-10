package thwiply.elopenmike.com.llm.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import thwiply.elopenmike.com.di.ApplicationScope

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    data object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * The activation record itself cannot identify an approved artifact — it is malformed or
 * names a model this build does not approve. Distinct from a storage read failure, because
 * the record read fine and clearing it is a safe, offered recovery.
 */
class UnusableActivationRecord(message: String) : IOException(message)

/** A discard could not finish, so storage and the activation record may disagree. */
class ArtifactRemovalFailure(message: String) : IOException(message)

/** Why an installed artifact was rejected. Loaded metadata is not verified weights. */
enum class ArtifactDefect { FILE_MISSING, SIZE_MISMATCH, DIGEST_MISMATCH }

/**
 * The single authoritative state of Thwiply's own Qwen artifact. Readiness is never
 * inferred from nullable metadata: only a completed size and SHA-256 verification of
 * the whole approved file publishes [Ready].
 *
 * Android AICore owns the shared Gemini Nano model; it is never described by this type.
 */
sealed interface ModelArtifactState {
    /** Initial adoption or an explicit revalidation is running off the main thread. */
    data object Verifying : ModelArtifactState

    /** No activation metadata exists: nothing was ever installed, or a discard completed. */
    data object Missing : ModelArtifactState

    data class Ready(val preset: ModelPreset) : ModelArtifactState {
        /** Engine identity is the verified content, never the pathname. */
        val engineKey: String get() = "${preset.id}:${preset.sha256}"
    }

    /** Activation metadata names an approved preset whose bytes failed verification. */
    data class Corrupt(val preset: ModelPreset, val defect: ArtifactDefect) : ModelArtifactState

    /** An explicit discard of rejected bytes is running. */
    data object Removing : ModelArtifactState

    /** Unreadable storage, malformed metadata, or an unapproved model id. */
    data class Failed(val cause: Exception) : ModelArtifactState
}

/** Installations the user can explicitly discard: damaged bytes, or an unusable record. */
fun ModelArtifactState.isRejectedInstallation(): Boolean =
    this is ModelArtifactState.Corrupt ||
        (this is ModelArtifactState.Failed && cause is UnusableActivationRecord)

@Singleton
class ModelManager internal constructor(
    private val modelsDir: File,
    private val okHttpClient: OkHttpClient,
    private val presets: List<ModelPreset>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        modelsDir = File(context.noBackupFilesDir, MODELS_DIRECTORY),
        okHttpClient = okHttpClient,
        presets = ModelPreset.PRESETS,
        scope = scope,
    )

    private val activeModelFile = File(modelsDir, ACTIVE_MODEL_FILE)
    private val _state = MutableStateFlow<ModelArtifactState>(ModelArtifactState.Verifying)

    /**
     * Verification runs once per adoption event — process start, an explicit refresh, and
     * activation of freshly verified download bytes — never per state emission or
     * recomposition. The published result stays valid for the process until activation,
     * discard, or another explicit refresh replaces it.
     */
    val state: StateFlow<ModelArtifactState> = _state.asStateFlow()

    /** Verification, activation and discard never observe each other's partial work. */
    private val artifactMutex = Mutex()
    private val initialVerification = verifyInBackground()

    suspend fun awaitLoaded() = initialVerification.join()

    /**
     * Explicit revalidation. Verification is owned by the application scope, so a canceled
     * caller can neither strand [ModelArtifactState.Verifying] nor race its own retry.
     */
    suspend fun refreshInstalledModel() {
        awaitLoaded()
        verifyInBackground().join()
    }

    /**
     * Explicit recovery for a rejected installation: bytes that failed verification, or an
     * activation record that cannot name an approved model. Manual tasks, the provider choice
     * and preferences are never touched, and nothing else is removed. Removing a healthy
     * model remains FND-10.
     */
    suspend fun discardRejectedInstallation() = withContext(dispatcher) {
        // Waiting for the lock stays cancellable: a discard queued behind a running hash
        // must be abandonable before any file is touched.
        artifactMutex.withLock {
            val rejected = _state.value
            val corrupt = when {
                rejected is ModelArtifactState.Corrupt -> rejected.preset
                rejected is ModelArtifactState.Failed &&
                    rejected.cause is UnusableActivationRecord -> null
                else -> return@withLock
            }
            _state.value = ModelArtifactState.Removing
            _state.value = withContext(NonCancellable) {
                try {
                    // An unusable record names no approved model, so the file it points at is
                    // not ours to identify or delete; only the record goes.
                    corrupt?.let {
                        // Leave the record until the bytes are gone: a crash between the two
                        // must reappear as a visible damaged install, never an invisible orphan.
                        // The resumable .part candidate is a separate download's data and is not
                        // named by either recovery action, so it is left for FND-10 to own.
                        delete(installedFile(it))
                    }
                    delete(activeModelFile)
                    ModelArtifactState.Missing
                } catch (error: IOException) {
                    ModelArtifactState.Failed(error)
                } catch (error: SecurityException) {
                    ModelArtifactState.Failed(error)
                }
            }
        }
    }

    /**
     * The file named by one verification result. Callers pass the [ModelArtifactState.Ready]
     * they already hold, so a path can never be paired with a key from a different read.
     */
    fun verifiedFile(verified: ModelArtifactState.Ready): File = installedFile(verified.preset)

    fun downloadModel(preset: ModelPreset): Flow<DownloadState> = flow {
        awaitLoaded()
        if (presets.none { it == preset }) {
            emit(DownloadState.Error("This model is not approved by this build."))
            return@flow
        }

        val url = preset.url.toHttpUrlOrNull()
        if (url == null || url.scheme != "https" || url.host != HUGGING_FACE_HOST) {
            emit(DownloadState.Error("The approved model URL is invalid."))
            return@flow
        }

        if (!modelsDir.exists() && !modelsDir.mkdirs()) {
            emit(DownloadState.Error("Unable to create private model storage."))
            return@flow
        }

        val installed = installedFile(preset)
        // The fast path answers from verified state only; an installed file of the right
        // length is not evidence that its bytes are the approved release.
        if ((state.value as? ModelArtifactState.Ready)?.preset == preset) {
            emit(DownloadState.Success)
            return@flow
        }

        // The candidate belongs to this download operation, which the coordinator lease
        // serializes against verification and discard. If a candidate did vanish mid-hash,
        // activation fails and surfaces as an error rather than an unverified adoption.
        val candidate = candidateFile(preset)
        if (candidate.length() > preset.expectedBytes) {
            candidate.delete()
        }
        if (candidate.length() == preset.expectedBytes) {
            if (candidate.matches(preset)) {
                activate(preset, candidate, installed)
                emit(DownloadState.Success)
                return@flow
            }
            candidate.delete()
        }

        val resumeOffset = candidate.takeIf(File::exists)?.length() ?: 0L
        val request = Request.Builder()
            .url(url)
            .apply {
                if (resumeOffset > 0L) {
                    header("Range", "bytes=$resumeOffset-")
                }
            }
            .build()

        emit(DownloadState.Downloading(progress(resumeOffset, preset.expectedBytes)))

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadState.Error("Model download failed with HTTP ${response.code}."))
                    return@flow
                }

                val body = response.body
                if (body == null) {
                    emit(DownloadState.Error("Model download returned an empty response."))
                    return@flow
                }

                val append = resumeOffset > 0L && response.code == 206
                val startingBytes = if (append) resumeOffset else 0L
                FileOutputStream(candidate, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloadedBytes = startingBytes
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                            downloadedBytes += count
                            emit(
                                DownloadState.Downloading(
                                    progress(downloadedBytes, preset.expectedBytes)
                                )
                            )
                        }
                    }
                    output.fd.sync()
                }
            }

            when {
                candidate.length() < preset.expectedBytes -> {
                    emit(DownloadState.Error("Model download is incomplete and can be resumed."))
                    return@flow
                }
                candidate.length() > preset.expectedBytes -> {
                    candidate.delete()
                    emit(DownloadState.Error("Model download exceeded the expected size."))
                    return@flow
                }
                !candidate.matches(preset) -> {
                    candidate.delete()
                    emit(DownloadState.Error("Model verification failed. Downloaded data was removed."))
                    return@flow
                }
            }

            activate(preset, candidate, installed)
            emit(DownloadState.Success)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: IOException) {
            emit(
                DownloadState.Error(
                    "Model download failed: ${error.message ?: "I/O error"}"
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun verifyInBackground() = scope.launch(dispatcher) { verifyInstalledArtifact() }

    private suspend fun verifyInstalledArtifact() = artifactMutex.withLock {
        val previous = _state.value
        _state.value = ModelArtifactState.Verifying
        val verified = try {
            inspectInstalledArtifact()
        } catch (cancellation: CancellationException) {
            // Only teardown of the owning scope cancels verification. Restore the last
            // completed result rather than leaving an unrecoverable Verifying state.
            _state.value = previous
            throw cancellation
        } catch (error: IOException) {
            ModelArtifactState.Failed(error)
        } catch (error: SecurityException) {
            ModelArtifactState.Failed(error)
        }
        _state.value = verified
    }

    private suspend fun inspectInstalledArtifact(): ModelArtifactState {
        val id = readActiveModelId() ?: return ModelArtifactState.Missing
        val preset = presets.firstOrNull { it.id == id }
            ?: throw UnusableActivationRecord(
                "Activation metadata names a model this build does not approve",
            )
        val installed = installedFile(preset)
        if (!installed.isFile) {
            return ModelArtifactState.Corrupt(preset, ArtifactDefect.FILE_MISSING)
        }
        // Cheap rejection first; the streamed byte count below stays authoritative.
        if (installed.length() != preset.expectedBytes) {
            return ModelArtifactState.Corrupt(preset, ArtifactDefect.SIZE_MISMATCH)
        }
        val (bytes, digest) = installed.streamDigest()
        return when {
            bytes != preset.expectedBytes -> ModelArtifactState.Corrupt(preset, ArtifactDefect.SIZE_MISMATCH)
            digest != preset.sha256 -> ModelArtifactState.Corrupt(preset, ArtifactDefect.DIGEST_MISMATCH)
            else -> ModelArtifactState.Ready(preset)
        }
    }

    private fun readActiveModelId(): String? = activeModelFile
        .takeIf(File::isFile)
        ?.inputStream()?.buffered()?.use { input ->
            val bytes = ByteArray(257)
            var length = 0
            while (length < bytes.size) {
                val count = input.read(bytes, length, bytes.size - length)
                if (count == -1) break
                length += count
            }
            if (length > 256) throw UnusableActivationRecord("Model metadata exceeds storage bound")
            String(bytes, 0, length, Charsets.UTF_8)
        }
        ?.trim()

    private fun installedFile(preset: ModelPreset): File = File(modelsDir, preset.fileName)

    private fun candidateFile(preset: ModelPreset): File =
        File(modelsDir, "${preset.fileName}.part")

    private fun delete(file: File) {
        if (file.exists() && !file.delete()) {
            throw ArtifactRemovalFailure("Unable to remove ${file.name}")
        }
    }

    private suspend fun File.matches(preset: ModelPreset): Boolean {
        val (bytes, digest) = streamDigest()
        return bytes == preset.expectedBytes && digest == preset.sha256
    }

    /**
     * Streams the whole file through SHA-256 with one bounded buffer. The model is never
     * loaded into memory, and cancellation is honored between reads.
     */
    private suspend fun File.streamDigest(): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        // One bounded buffer, never the whole model. No buffered() wrapper: reads are already
        // larger than one, so BufferedInputStream would only add a second allocation.
        inputStream().use { input ->
            val buffer = ByteArray(DIGEST_BUFFER_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
                total += count
            }
        }
        return total to digest.digest().toHexString()
    }

    /** Called only from the downloadModel flow, whose flowOn(Dispatchers.IO) owns this context. */
    private suspend fun activate(preset: ModelPreset, candidate: File, installed: File) {
        artifactMutex.withLock {
            withContext(NonCancellable) {
                atomicMove(candidate, installed)
                val metadataCandidate = File(modelsDir, "$ACTIVE_MODEL_FILE.part")
                metadataCandidate.writeText(preset.id)
                atomicMove(metadataCandidate, activeModelFile)
                // The candidate's digest was just verified, so adoption needs no second hash.
                _state.value = ModelArtifactState.Ready(preset)
            }
        }
    }

    private fun atomicMove(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun progress(bytes: Long, total: Long): Int =
        ((bytes.coerceAtMost(total) * 100L) / total).toInt()

    private companion object {
        const val MODELS_DIRECTORY = "models"
        const val ACTIVE_MODEL_FILE = "active-model"
        const val HUGGING_FACE_HOST = "huggingface.co"

        /** Bounded and constant. 8 KiB would cost ~195,000 reads for the approved artifact. */
        const val DIGEST_BUFFER_BYTES = 1 shl 20
    }
}
