package com.elopenmike.thwiply.llm.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    data object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient?
) {
    // For testability we can provide a constructor that takes filesDir directly
    constructor(filesDir: File, okHttpClient: OkHttpClient?) : this(
        context = object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): File = filesDir
        },
        okHttpClient = okHttpClient
    )

    val modelFile: File get() = File(context.filesDir, "model.litertlm")
    
    fun isModelAvailable(): Boolean = modelFile.exists() && modelFile.length() > 0
    
    fun downloadModel(url: String, hfToken: String? = null): Flow<DownloadState> = flow {
        if (isModelAvailable()) {
            emit(DownloadState.Success)
            return@flow
        }
        
        emit(DownloadState.Downloading(0))
        val requestBuilder = Request.Builder().url(url)
        if (!hfToken.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $hfToken")
        }
        
        try {
            val response = okHttpClient!!.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                emit(DownloadState.Error("HTTP Error: ${response.code}"))
                return@flow
            }
            
            val body = response.body
            if (body == null) {
                emit(DownloadState.Error("Empty response body"))
                return@flow
            }
            
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            
            body.source().use { source ->
                modelFile.outputStream().use { output ->
                    val buffer = okio.Buffer()
                    var read: Long
                    while (source.read(buffer, 8192).also { read = it } != -1L) {
                        output.write(buffer.readByteArray())
                        downloadedBytes += read
                        val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                        emit(DownloadState.Downloading(progress))
                    }
                }
            }
            emit(DownloadState.Success)
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Download failed"))
            modelFile.delete()
        }
    }.flowOn(Dispatchers.IO)
}
