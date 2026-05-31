package com.elopenmike.thwiply.llm.engine

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmEngineManager @Inject constructor() {
    private var engine: Engine? = null
    
    suspend fun initialize(modelFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (engine == null) {
                engine = Engine(EngineConfig(modelFile.absolutePath)).apply { initialize() }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun generateStream(prompt: String): Flow<String> {
        val currentEngine = engine ?: throw IllegalStateException("Engine not initialized")
        val conversation = currentEngine.createConversation()
        return conversation.sendMessageAsync(prompt).flowOn(Dispatchers.IO)
    }
    
    fun close() {
        engine?.close()
        engine = null
    }
}
