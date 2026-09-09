package thwiply.elopenmike.com.llm.provider

import thwiply.elopenmike.com.llm.model.ArtifactDefect
import thwiply.elopenmike.com.llm.model.ArtifactRemovalFailure
import thwiply.elopenmike.com.llm.model.ModelArtifactState
import thwiply.elopenmike.com.llm.model.UnusableActivationRecord

enum class FailureKind {
    UNAVAILABLE, AICORE_NOT_READY, DOWNLOAD, BUSY, QUOTA, BACKGROUND, SAFETY,
    INVALID_INPUT, INPUT_TOO_LONG, EMPTY_OUTPUT, TIMEOUT, OUTPUT_LIMIT, RUNTIME, PREFERENCE, MODEL_STORAGE,
    MODEL_CORRUPT, MODEL_RECORD, MODEL_FILE_MISSING, MODEL_REMOVAL,
}

class InferenceFailure(val kind: FailureKind, cause: Throwable? = null) : Exception(kind.name, cause)

/** What an explicit retry can do about a failure. */
enum class RetryAction { REVALIDATE, PREPARE, NONE }

/**
 * One exhaustive mapping, so the control and its action cannot drift apart: a new failure kind
 * will not compile until it says whether a retry can clear it. Re-reading an unusable activation
 * record always fails the same way, so that kind offers no retry at all.
 */
val FailureKind.retryAction: RetryAction
    get() = when (this) {
        FailureKind.MODEL_CORRUPT, FailureKind.MODEL_STORAGE, FailureKind.MODEL_FILE_MISSING,
        FailureKind.MODEL_REMOVAL -> RetryAction.REVALIDATE
        // Re-reading an unusable record fails the same way; a preference read failure is
        // cleared by choosing a provider again, not by preparing.
        FailureKind.MODEL_RECORD, FailureKind.PREFERENCE -> RetryAction.NONE
        FailureKind.UNAVAILABLE, FailureKind.AICORE_NOT_READY, FailureKind.DOWNLOAD,
        FailureKind.BUSY, FailureKind.QUOTA, FailureKind.BACKGROUND, FailureKind.SAFETY,
        FailureKind.INVALID_INPUT, FailureKind.INPUT_TOO_LONG, FailureKind.EMPTY_OUTPUT,
        FailureKind.TIMEOUT, FailureKind.OUTPUT_LIMIT, FailureKind.RUNTIME -> RetryAction.PREPARE
    }

sealed interface ProviderReadiness {
    data object Checking : ProviderReadiness
    data object Missing : ProviderReadiness
    /** Damaged Qwen bytes are being discarded; no other model work may start. */
    data object Removing : ProviderReadiness
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

/**
 * The one mapping from Thwiply's artifact state to a provider failure, or null when the artifact
 * is not in a failed state at all. Every surface reads it, so no screen has to keep its own idea
 * of which installations are rejected or which rejections a retry can clear.
 */
fun ModelArtifactState.rejectionKind(): FailureKind? = when (this) {
    ModelArtifactState.Verifying, ModelArtifactState.Missing, ModelArtifactState.Removing -> null
    is ModelArtifactState.Ready -> null
    is ModelArtifactState.Corrupt -> when (defect) {
        ArtifactDefect.FILE_MISSING -> FailureKind.MODEL_FILE_MISSING
        ArtifactDefect.SIZE_MISMATCH, ArtifactDefect.DIGEST_MISMATCH -> FailureKind.MODEL_CORRUPT
    }
    is ModelArtifactState.Failed -> when (cause) {
        is UnusableActivationRecord -> FailureKind.MODEL_RECORD
        is ArtifactRemovalFailure -> FailureKind.MODEL_REMOVAL
        else -> FailureKind.MODEL_STORAGE
    }
}
