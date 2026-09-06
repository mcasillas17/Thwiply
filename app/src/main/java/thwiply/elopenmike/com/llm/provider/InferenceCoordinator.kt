package thwiply.elopenmike.com.llm.provider

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import thwiply.elopenmike.com.llm.engine.EngineState
import thwiply.elopenmike.com.llm.engine.LlmEngineManager
import thwiply.elopenmike.com.llm.model.DownloadState
import thwiply.elopenmike.com.llm.model.ModelManager
import thwiply.elopenmike.com.llm.model.ModelPreset
import thwiply.elopenmike.com.llm.model.ModelLoadState

@Singleton
class InferenceCoordinator internal constructor(
    private val repository: ProviderSelectionRepository,
    private val models: ModelManager,
    private val engine: LlmEngineManager,
    private val nanoFactory: NanoClientFactory,
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    @Inject constructor(
        repository: ProviderSelectionRepository,
        models: ModelManager,
        engine: LlmEngineManager,
    ) : this(repository, models, engine, NanoClientFactory(::MlKitNanoClient),
        CoroutineScope(SupervisorJob() + Dispatchers.IO), Dispatchers.IO)

    val selection = repository.state
    val qwenLoadState = models.loadState
    private val mutableReadiness = MutableStateFlow<ProviderReadiness>(ProviderReadiness.Checking)
    val readiness = mutableReadiness.asStateFlow()
    private val mutableNanoState = MutableStateFlow<NanoState>(NanoState.Checking)
    val nanoState = mutableNanoState.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableForeground = MutableStateFlow(false)
    val foreground = mutableForeground.asStateFlow()

    // One process-wide lease: canceling a coroutine does not mean native work has unwound.
    private val lock = Any()
    private var active: Operation? = null
    @Volatile private var qwenDownloading = false
    private var pendingNanoClose: NanoClient? = null
    private var readinessFailure: InferenceFailure? = null
    private class Operation(val job: Deferred<Unit>, val switching: Boolean)

    init {
        scope.launch {
            combine(selection, models.activeModel, engine.state, nanoState, models.loadState) { _, _, _, _, _ -> Unit }
                .collect { refreshReadiness() }
        }
    }

    fun setForeground(value: Boolean) = synchronized(lock) {
        if (!value) active?.job?.cancel(CancellationException("Top foreground lost"))
        mutableForeground.value = value
    }

    suspend fun selectProvider(provider: ModelProvider) = exclusive(switching = true) {
        if (!repository.select(provider)) {
            throw InferenceFailure(FailureKind.PREFERENCE, selection.value.failure)
        }
        refreshReadiness()
    }

    suspend fun prepare() = exclusive {
        requireForeground()
        when (selectedProvider()) {
            ModelProvider.QWEN -> {
                models.awaitLoaded()
                if (models.loadState.value is ModelLoadState.Failed) models.refreshInstalledModel()
                (models.loadState.value as? ModelLoadState.Failed)?.let {
                    throw InferenceFailure(FailureKind.MODEL_STORAGE, it.cause)
                }
                refreshReadiness()
                if (models.isModelAvailable() && readiness.value != ProviderReadiness.Ready) {
                    mutableReadiness.value = ProviderReadiness.Initializing
                    withTimeout(INFERENCE_TIMEOUT_MS) {
                        engine.initialize(models.modelFile).getOrElse {
                            throw InferenceFailure(FailureKind.RUNTIME, it)
                        }
                    }
                    refreshReadiness()
                }
            }
            ModelProvider.GEMINI_NANO -> checkNanoUnderLease()
        }
    }

    suspend fun checkNano() = exclusive {
        requireForeground()
        checkNanoUnderLease()
    }

    suspend fun downloadNano() = exclusive {
        requireForeground()
        if (selectedProvider() != ModelProvider.GEMINI_NANO) throw InferenceFailure(FailureKind.UNAVAILABLE)
        nanoOperation(DOWNLOAD_TIMEOUT_MS) {
            val status = withTimeout(STATUS_TIMEOUT_MS) { checkStatus() }
            updateNano(status)
            when (status) {
                NanoState.Ready -> Unit
                NanoState.Downloadable, NanoState.Downloading -> {
                    currentCoroutineContext().ensureActive()
                    requireForeground()
                    updateNano(NanoState.Downloading)
                    download()
                    currentCoroutineContext().ensureActive()
                    requireForeground()
                    updateNano(withTimeout(STATUS_TIMEOUT_MS) { checkStatus() })
                    if (nanoState.value != NanoState.Ready) throw InferenceFailure(FailureKind.DOWNLOAD)
                }
                NanoState.Unavailable -> throw InferenceFailure(FailureKind.UNAVAILABLE)
                is NanoState.Failed -> throw status.failure
                NanoState.Checking -> throw InferenceFailure(FailureKind.AICORE_NOT_READY)
            }
        }
    }

    fun downloadQwen(preset: ModelPreset): Flow<DownloadState> = channelFlow {
        exclusive {
            requireForeground()
            if (selectedProvider() != ModelProvider.QWEN) throw InferenceFailure(FailureKind.UNAVAILABLE)
            qwenDownloading = true
            refreshReadiness()
            try {
                withTimeout(DOWNLOAD_TIMEOUT_MS) {
                    models.downloadModel(preset).collect {
                        currentCoroutineContext().ensureActive()
                        requireForeground()
                        send(it)
                    }
                }
            } finally {
                qwenDownloading = false
                refreshReadiness()
            }
        }
    }.buffer(0)

    fun generate(prompt: String): Flow<String> = channelFlow {
        exclusive(reportReadinessFailure = false) {
            requireForeground()
            val provider = selectedProvider()
            if (prompt.isBlank()) throw InferenceFailure(FailureKind.INVALID_INPUT)
            if (prompt.length > MAX_PROMPT_CHARS) throw InferenceFailure(FailureKind.INPUT_TOO_LONG)
            refreshReadiness()
            if (readiness.value != ProviderReadiness.Ready) throw InferenceFailure(FailureKind.UNAVAILABLE)
            var length = 0
            var nonBlank = false
            suspend fun collectOutput(output: Flow<String>) {
                output.collect { chunk ->
                    currentCoroutineContext().ensureActive()
                    requireForeground()
                    if (chunk.length > MAX_OUTPUT_CHARS - length) throw InferenceFailure(FailureKind.OUTPUT_LIMIT)
                    length += chunk.length
                    if (chunk.isNotBlank()) nonBlank = true
                    if (chunk.isNotEmpty()) send(chunk)
                }
            }
            withTimeout(INFERENCE_TIMEOUT_MS) {
                when (provider) {
                    ModelProvider.QWEN -> collectOutput(engine.generateStream(prompt))
                    ModelProvider.GEMINI_NANO -> withNano {
                        val status = withTimeout(STATUS_TIMEOUT_MS) { checkStatus() }
                        updateNano(status)
                        if (status != NanoState.Ready) {
                            throw (status as? NanoState.Failed)?.failure
                                ?: InferenceFailure(FailureKind.UNAVAILABLE)
                        }
                        currentCoroutineContext().ensureActive()
                        requireForeground()
                        val count = countTokens(prompt)
                        if (count < 0 || count >= MAX_NANO_INPUT_TOKENS) throw InferenceFailure(FailureKind.INPUT_TOO_LONG)
                        currentCoroutineContext().ensureActive()
                        requireForeground()
                        collectOutput(generate(prompt))
                    }
                }
            }
            if (!nonBlank) throw InferenceFailure(FailureKind.EMPTY_OUTPUT)
        }
    }.buffer(0)

    private suspend fun checkNanoUnderLease() {
        updateNano(NanoState.Checking)
        nanoOperation(STATUS_TIMEOUT_MS) {
            updateNano(checkStatus())
        }
    }

    private suspend fun nanoOperation(timeoutMs: Long, block: suspend NanoClient.() -> Unit) {
        try {
            withTimeout(timeoutMs) { withNano(block) }
        } catch (cancellation: CancellationException) {
            if (cancellation is TimeoutCancellationException) {
                val failure = InferenceFailure(FailureKind.TIMEOUT, cancellation)
                updateNano(NanoState.Failed(failure))
                throw failure
            }
            // A canceled check/download needs a fresh explicit status check, not a stuck spinner.
            updateNano(NanoState.Checking)
            throw cancellation
        } catch (error: Exception) {
            val failure = error.asInferenceFailure()
            updateNano(NanoState.Failed(failure))
            throw failure
        }
    }

    private suspend fun withNano(block: suspend NanoClient.() -> Unit) {
        engine.close()
        currentCoroutineContext().ensureActive()
        requireForeground()
        val client = nanoFactory.open()
        pendingNanoClose = client
        var primary: Throwable? = null
        try {
            currentCoroutineContext().ensureActive()
            requireForeground()
            client.block()
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try {
                closePendingNano()
            } catch (cleanup: Exception) {
                if (primary == null) throw cleanup
                if (primary !== cleanup) primary.addSuppressed(cleanup)
            }
        }
    }

    private fun closePendingNano() {
        pendingNanoClose?.close()
        pendingNanoClose = null
    }

    private suspend fun selectedProvider(): ModelProvider {
        repository.awaitLoaded()
        val current = selection.value
        current.failure?.let { throw InferenceFailure(FailureKind.PREFERENCE, it) }
        return current.provider ?: throw InferenceFailure(FailureKind.PREFERENCE)
    }

    private suspend fun requireForeground() {
        val context = currentCoroutineContext()
        synchronized(lock) {
            context.ensureActive()
            if (!foreground.value) throw InferenceFailure(FailureKind.BACKGROUND)
        }
    }

    private fun updateNano(state: NanoState) {
        mutableNanoState.value = state
        refreshReadiness()
    }

    private fun refreshReadiness() = synchronized(lock) {
        val current = selection.value
        val modelLoad = models.loadState.value
        mutableReadiness.value = when {
            current.failure != null -> ProviderReadiness.Failed(InferenceFailure(FailureKind.PREFERENCE, current.failure))
            current.provider == null -> ProviderReadiness.Checking
            readinessFailure != null -> ProviderReadiness.Failed(requireNotNull(readinessFailure))
            current.provider == ModelProvider.GEMINI_NANO -> when (val nano = nanoState.value) {
                NanoState.Checking -> ProviderReadiness.Checking
                NanoState.Unavailable -> ProviderReadiness.Unavailable
                NanoState.Downloadable -> ProviderReadiness.Downloadable
                NanoState.Downloading -> ProviderReadiness.Downloading
                NanoState.Ready -> ProviderReadiness.Ready
                is NanoState.Failed -> ProviderReadiness.Failed(nano.failure)
            }
            qwenDownloading -> ProviderReadiness.Downloading
            modelLoad == ModelLoadState.Loading -> ProviderReadiness.Checking
            modelLoad is ModelLoadState.Failed -> ProviderReadiness.Failed(
                InferenceFailure(FailureKind.MODEL_STORAGE, modelLoad.cause),
            )
            !models.isModelAvailable() -> ProviderReadiness.Missing
            else -> when (val state = engine.state.value) {
                EngineState.Idle -> ProviderReadiness.NeedsInitialization
                is EngineState.Initializing -> ProviderReadiness.Initializing
                is EngineState.Ready -> if (state.modelPath == models.modelFile.absolutePath) ProviderReadiness.Ready
                    else ProviderReadiness.NeedsInitialization
                is EngineState.Failed -> ProviderReadiness.Failed(InferenceFailure(FailureKind.RUNTIME, state.cause))
            }
        }
    }

    private suspend fun exclusive(
        switching: Boolean = false,
        reportReadinessFailure: Boolean = true,
        block: suspend () -> Unit,
    ) = coroutineScope {
        var previous: Operation? = null
        val job = async(dispatcher, start = CoroutineStart.LAZY) {
            previous?.job?.join()
            currentCoroutineContext().ensureActive()
            try {
                // A failed close still owns native resources; do not replace them.
                closePendingNano()
                block()
            } catch (timeout: TimeoutCancellationException) {
                throw InferenceFailure(FailureKind.TIMEOUT, timeout)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                throw error.asInferenceFailure()
            }
        }
        val operation = Operation(job, switching)
        synchronized(lock) {
            if (active != null && (!switching || active!!.switching)) {
                job.cancel()
                throw InferenceFailure(FailureKind.BUSY)
            }
            previous = active
            previous?.job?.cancel(CancellationException("Provider changed"))
            active = operation
            mutableBusy.value = true
            if (reportReadinessFailure) {
                readinessFailure = null
                refreshReadiness()
            }
        }
        try {
            job.start()
            job.await()
        } catch (failure: InferenceFailure) {
            synchronized(lock) {
                if (reportReadinessFailure && active === operation) {
                    readinessFailure = failure
                    refreshReadiness()
                }
            }
            throw failure
        } finally {
            withContext(NonCancellable) {
                job.cancel()
                job.join()
                // Even a canceled switch retains the old native lease until it has unwound.
                previous?.job?.join()
                synchronized(lock) {
                    if (active === operation) {
                        active = null
                        mutableBusy.value = false
                    }
                }
            }
        }
    }

    private fun Exception.asInferenceFailure(): InferenceFailure =
        this as? InferenceFailure ?: InferenceFailure(FailureKind.RUNTIME, this)

    companion object {
        const val MAX_PROMPT_CHARS = 6_000
        const val MAX_OUTPUT_CHARS = 8_000
        const val MAX_NANO_INPUT_TOKENS = 4_000
        const val STATUS_TIMEOUT_MS = 30_000L
        const val INFERENCE_TIMEOUT_MS = 120_000L
        const val DOWNLOAD_TIMEOUT_MS = 900_000L
    }
}
