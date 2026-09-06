package thwiply.elopenmike.com.llm.provider

enum class FailureKind {
    UNAVAILABLE, AICORE_NOT_READY, DOWNLOAD, BUSY, QUOTA, BACKGROUND, SAFETY,
    INVALID_INPUT, INPUT_TOO_LONG, EMPTY_OUTPUT, TIMEOUT, OUTPUT_LIMIT, RUNTIME, PREFERENCE, MODEL_STORAGE,
}

class InferenceFailure(val kind: FailureKind, cause: Throwable? = null) : Exception(kind.name, cause)

sealed interface ProviderReadiness {
    data object Checking : ProviderReadiness
    data object Missing : ProviderReadiness
    data object NeedsInitialization : ProviderReadiness
    data object Initializing : ProviderReadiness
    data object Ready : ProviderReadiness
    data object Unavailable : ProviderReadiness
    data object Downloadable : ProviderReadiness
    data object Downloading : ProviderReadiness
    data class Failed(val failure: InferenceFailure) : ProviderReadiness
}

sealed interface NanoState {
    data object Checking : NanoState
    data object Unavailable : NanoState
    data object Downloadable : NanoState
    data object Downloading : NanoState
    data object Ready : NanoState
    data class Failed(val failure: InferenceFailure) : NanoState
}
