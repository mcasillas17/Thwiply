package thwiply.elopenmike.com.llm.provider

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class MlKitNanoClient(
    private val model: GenerativeModel = Generation.getClient(),
) : NanoClient {
    override suspend fun checkStatus(): NanoState = sdkCall {
        when (model.checkStatus()) {
            FeatureStatus.UNAVAILABLE -> NanoState.Unavailable
            FeatureStatus.DOWNLOADABLE -> NanoState.Downloadable
            FeatureStatus.DOWNLOADING -> NanoState.Downloading
            FeatureStatus.AVAILABLE -> NanoState.Ready
            else -> throw InferenceFailure(FailureKind.RUNTIME)
        }
    }

    override suspend fun download() = sdkCall(downloading = true) {
        var completed = false
        model.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadFailed -> throw status.e
                DownloadStatus.DownloadCompleted -> completed = true
                else -> Unit
            }
        }
        if (!completed) throw InferenceFailure(FailureKind.DOWNLOAD)
    }

    override suspend fun countTokens(prompt: String): Int = sdkCall {
        val count = model.countTokens(request(prompt)).totalTokens
        if (count < 0 || count.toLong() + MAX_OUTPUT_TOKENS > model.getTokenLimit().toLong()) {
            throw InferenceFailure(FailureKind.INPUT_TOO_LONG)
        }
        count
    }

    override fun generate(prompt: String): Flow<String> = flow {
        sdkCall {
            var completed = false
            model.generateContentStream(request(prompt)).collect { response ->
                val candidate = response.candidates.firstOrNull()
                    ?: throw InferenceFailure(FailureKind.EMPTY_OUTPUT)
                when (candidate.finishReason) {
                    null -> Unit
                    Candidate.FinishReason.STOP -> completed = true
                    Candidate.FinishReason.MAX_TOKENS -> throw InferenceFailure(FailureKind.OUTPUT_LIMIT)
                    else -> throw InferenceFailure(FailureKind.RUNTIME)
                }
                emit(candidate.text)
            }
            if (!completed) throw InferenceFailure(FailureKind.RUNTIME)
        }
    }

    override fun close() = model.close()

    private fun request(prompt: String) = GenerateContentRequest.Builder(TextPart(prompt)).apply {
        candidateCount = 1
        maxOutputTokens = MAX_OUTPUT_TOKENS
    }.build()

    private suspend fun <T> sdkCall(downloading: Boolean = false, block: suspend () -> T): T {
        try {
            return block()
        } catch (error: GenAiException) {
            if (error.errorCode == GenAiException.ErrorCode.CANCELLED) {
                throw CancellationException("Nano request canceled").apply { initCause(error) }
            }
            val kind = when (error.errorCode) {
                GenAiException.ErrorCode.NOT_AVAILABLE -> FailureKind.UNAVAILABLE
                GenAiException.ErrorCode.AICORE_INCOMPATIBLE,
                GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE,
                AICORE_BINDING_FAILURE,
                AICORE_FEATURE_NOT_FOUND -> FailureKind.AICORE_NOT_READY
                GenAiException.ErrorCode.BUSY -> FailureKind.BUSY
                GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> FailureKind.QUOTA
                GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> FailureKind.BACKGROUND
                GenAiException.ErrorCode.REQUEST_PROCESSING_ERROR,
                GenAiException.ErrorCode.RESPONSE_PROCESSING_ERROR,
                GenAiException.ErrorCode.RESPONSE_GENERATION_ERROR -> FailureKind.SAFETY
                GenAiException.ErrorCode.REQUEST_TOO_LARGE -> FailureKind.INPUT_TOO_LONG
                GenAiException.ErrorCode.REQUEST_TOO_SMALL -> FailureKind.INVALID_INPUT
                GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> FailureKind.DOWNLOAD
                else -> if (downloading) FailureKind.DOWNLOAD else FailureKind.RUNTIME
            }
            throw InferenceFailure(kind, error)
        }
    }

    private companion object {
        // beta2's actual request builder caps this at 256, despite newer API docs.
        const val MAX_OUTPUT_TOKENS = 256
        // AICore codes pass through beta3 GenAiException without named SDK constants.
        const val AICORE_BINDING_FAILURE = 601
        const val AICORE_FEATURE_NOT_FOUND = 606
    }
}
