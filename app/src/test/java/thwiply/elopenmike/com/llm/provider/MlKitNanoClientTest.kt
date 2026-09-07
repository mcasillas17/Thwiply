package thwiply.elopenmike.com.llm.provider

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.CountTokensResponse
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.GenerateContentResponse
import com.google.mlkit.genai.prompt.GenerativeModel
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

class MlKitNanoClientTest {
    @Test fun `SDK input request counts same text with bounded output and one candidate`() = runTest {
        val calls = mutableListOf<String>()
        val client = MlKitNanoClient(model { method, args ->
            calls += method
            when (method) {
                "countTokens" -> {
                    val request = args[0] as GenerateContentRequest
                    assertEquals(256, request.maxOutputTokens)
                    assertEquals(1, request.candidateCount)
                    assertEquals("hello", request.text.textString)
                    CountTokensResponse(3999)
                }
                "getTokenLimit" -> 8192
                "close" -> Unit
                else -> error("Unexpected $method")
            }
        })
        assertEquals(3999, client.countTokens("hello"))
        client.close()
        assertEquals(listOf("countTokens", "getTokenLimit", "close"), calls)
    }

    @Test fun `counted input plus configured output respects device token limit`() = runTest {
        for ((count, accepted) in listOf(3840 to true, 3841 to false)) {
            val client = MlKitNanoClient(model { method, _ ->
                when (method) {
                    "countTokens" -> CountTokensResponse(count)
                    "getTokenLimit" -> 4096
                    else -> error("Unexpected $method")
                }
            })
            if (accepted) {
                assertEquals(count, client.countTokens("x"))
            } else {
                try { client.countTokens("x"); fail("Expected combined token limit failure") }
                catch (failure: InferenceFailure) { assertEquals(FailureKind.INPUT_TOO_LONG, failure.kind) }
            }
        }
    }

    @Test fun `SDK statuses map without triggering a download`() = runTest {
        for ((code, expected) in listOf(
            FeatureStatus.AVAILABLE to NanoState.Ready,
            FeatureStatus.UNAVAILABLE to NanoState.Unavailable,
            FeatureStatus.DOWNLOADING to NanoState.Downloading,
            FeatureStatus.DOWNLOADABLE to NanoState.Downloadable,
        )) {
            val client = MlKitNanoClient(model { method, _ ->
                assertEquals("checkStatus", method)
                code
            })
            assertEquals(expected, client.checkStatus())
        }
    }

    @Test fun `SDK error codes preserve original cause and safety distinctions`() = runTest {
        for ((code, kind) in listOf(
            GenAiException.ErrorCode.NOT_AVAILABLE to FailureKind.UNAVAILABLE,
            GenAiException.ErrorCode.AICORE_INCOMPATIBLE to FailureKind.AICORE_NOT_READY,
            GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE to FailureKind.AICORE_NOT_READY,
            GenAiException.ErrorCode.BUSY to FailureKind.BUSY,
            GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED to FailureKind.QUOTA,
            GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED to FailureKind.BACKGROUND,
            GenAiException.ErrorCode.REQUEST_PROCESSING_ERROR to FailureKind.SAFETY,
            GenAiException.ErrorCode.RESPONSE_PROCESSING_ERROR to FailureKind.SAFETY,
            GenAiException.ErrorCode.RESPONSE_GENERATION_ERROR to FailureKind.SAFETY,
            GenAiException.ErrorCode.REQUEST_TOO_LARGE to FailureKind.INPUT_TOO_LONG,
            GenAiException.ErrorCode.REQUEST_TOO_SMALL to FailureKind.INVALID_INPUT,
            GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE to FailureKind.DOWNLOAD,
            GenAiException.ErrorCode.UNKNOWN to FailureKind.RUNTIME,
        )) {
            val error = GenAiException("synthetic", null, code)
            val client = MlKitNanoClient(model { _, _ -> throw error })
            try { client.checkStatus(); fail("Expected $kind") }
            catch (failure: InferenceFailure) {
                assertEquals(kind, failure.kind)
                assertSame(error, failure.cause)
            }
        }
    }

    @Test fun `failed download event throws categorized failure retaining SDK exception`() = runTest {
        val original = GenAiException("synthetic", null, GenAiException.ErrorCode.UNKNOWN)
        val client = MlKitNanoClient(model { _, _ -> flow {
            emit(DownloadStatus.DownloadFailed(original))
            throw original
        } })
        try { client.download(); fail("Expected download failure") }
        catch (failure: InferenceFailure) {
            assertEquals(FailureKind.DOWNLOAD, failure.kind)
            assertSame(original, failure.cause)
        }
    }

    @Test fun `raw AICore connection and preparation codes preserve setup failure cause`() = runTest {
        for (code in listOf(601, 606)) {
            val original = GenAiException("synthetic", null, code)
            assertEquals(code, original.errorCode)
            val client = MlKitNanoClient(model { _, _ -> throw original })
            try { client.checkStatus(); fail("Expected AICore setup failure") }
            catch (failure: InferenceFailure) {
                assertEquals(FailureKind.AICORE_NOT_READY, failure.kind)
                assertSame(original, failure.cause)
            }
        }
    }

    @Test fun `SDK cancellation remains cancellation with original cause`() = runTest {
        val original = GenAiException("synthetic", null, GenAiException.ErrorCode.CANCELLED)
        val client = MlKitNanoClient(model { _, _ -> flow<Nothing> { throw original } })
        try { client.generate("x").collect(); fail("Expected cancellation") }
        catch (cancellation: CancellationException) { assertSame(original, cancellation.cause) }
    }

    @Test fun `stream chunks remain chunks and SDK max tokens signals output limit`() = runTest {
        val client = MlKitNanoClient(model { _, args ->
            val request = args[0] as GenerateContentRequest
            assertEquals("hello", request.text.textString)
            assertEquals(256, request.maxOutputTokens)
            flowOf(response("one", null), response(" two", Candidate.FinishReason.STOP))
        })
        assertEquals(listOf("one", " two"), client.generate("hello").toList())
        val limited = MlKitNanoClient(model { _, _ -> flowOf(response("partial", Candidate.FinishReason.MAX_TOKENS)) })
        try { limited.generate("x").collect(); fail("Expected output limit") }
        catch (failure: InferenceFailure) { assertEquals(FailureKind.OUTPUT_LIMIT, failure.kind) }
    }

    @Test fun `stream without a successful terminal reason is not completed output`() = runTest {
        for (finish in listOf(null, 99)) {
            val client = MlKitNanoClient(model { _, _ -> flowOf(response("partial", finish)) })
            try { client.generate("x").collect(); fail("Expected incomplete stream failure") }
            catch (failure: InferenceFailure) { assertEquals(FailureKind.RUNTIME, failure.kind) }
        }
    }

    private fun response(text: String, finish: Int?): GenerateContentResponse {
        // beta2 exposes output value constructors only to Java, not Kotlin source.
        val candidate = Candidate::class.java.constructors.single().newInstance(text, finish, null)
        return GenerateContentResponse::class.java.constructors.single()
            .newInstance(listOf(candidate), null) as GenerateContentResponse
    }

    private fun model(answer: (String, Array<out Any?>) -> Any?): GenerativeModel =
        Proxy.newProxyInstance(GenerativeModel::class.java.classLoader, arrayOf(GenerativeModel::class.java)) { _, method, args ->
            try {
                answer(method.name, args ?: emptyArray())
            } catch (failure: GenAiException) {
                // A proxy would wrap a checked exception thrown across its Java boundary.
                // Deliver suspend failures through the continuation, as the real SDK does.
                @Suppress("UNCHECKED_CAST")
                val continuation = args?.lastOrNull() as? Continuation<Any?> ?: throw failure
                continuation.resumeWith(Result.failure(failure))
                COROUTINE_SUSPENDED
            }
        } as GenerativeModel
}
