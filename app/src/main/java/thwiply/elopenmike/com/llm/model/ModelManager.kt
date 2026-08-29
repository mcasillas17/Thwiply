package thwiply.elopenmike.com.llm.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    data object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}

@Singleton
class ModelManager internal constructor(
    private val modelsDir: File,
    private val okHttpClient: OkHttpClient,
    private val presets: List<ModelPreset>
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ) : this(
        modelsDir = File(context.noBackupFilesDir, MODELS_DIRECTORY),
        okHttpClient = okHttpClient,
        presets = ModelPreset.PRESETS
    )

    private val activeModelFile = File(modelsDir, ACTIVE_MODEL_FILE)
    private val _activeModel = MutableStateFlow(readActiveModel())
    val activeModel: StateFlow<ModelPreset?> = _activeModel.asStateFlow()

    val modelFile: File
        get() = activeModel.value?.let(::installedFile)
            ?: throw IllegalStateException("No verified model is active")

    fun isModelAvailable(): Boolean = activeModel.value != null

    fun downloadModel(preset: ModelPreset): Flow<DownloadState> = flow {
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
        if (activeModel.value == preset && installed.length() == preset.expectedBytes) {
            emit(DownloadState.Success)
            return@flow
        }

        val candidate = candidateFile(preset)
        if (candidate.length() > preset.expectedBytes) {
            candidate.delete()
        }
        if (candidate.length() == preset.expectedBytes) {
            if (candidate.sha256() == preset.sha256) {
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
                candidate.sha256() != preset.sha256 -> {
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

    private fun readActiveModel(): ModelPreset? {
        val preset = activeModelFile
            .takeIf(File::isFile)
            ?.readText()
            ?.trim()
            ?.let { id -> presets.firstOrNull { it.id == id } }
            ?: return null

        return preset.takeIf { installedFile(it).length() == it.expectedBytes }
    }

    private fun installedFile(preset: ModelPreset): File = File(modelsDir, preset.fileName)

    private fun candidateFile(preset: ModelPreset): File =
        File(modelsDir, "${preset.fileName}.part")

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun activate(preset: ModelPreset, candidate: File, installed: File) {
        atomicMove(candidate, installed)
        val metadataCandidate = File(modelsDir, "$ACTIVE_MODEL_FILE.part")
        metadataCandidate.writeText(preset.id)
        atomicMove(metadataCandidate, activeModelFile)
        _activeModel.value = preset
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
    }
}
