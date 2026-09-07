package thwiply.elopenmike.com.llm.provider

import kotlinx.coroutines.flow.Flow

/** The SDK boundary; each operation owns and closes one client. */
internal interface NanoClient : AutoCloseable {
    suspend fun checkStatus(): NanoState
    suspend fun download()
    suspend fun countTokens(prompt: String): Int
    fun generate(prompt: String): Flow<String>
}

internal fun interface NanoClientFactory {
    fun open(): NanoClient
}
