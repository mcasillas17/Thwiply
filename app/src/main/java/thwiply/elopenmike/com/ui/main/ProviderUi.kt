package thwiply.elopenmike.com.ui.main

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow
import thwiply.elopenmike.com.R
import thwiply.elopenmike.com.llm.model.ArtifactDefect
import thwiply.elopenmike.com.llm.model.ModelArtifactState
import thwiply.elopenmike.com.llm.provider.FailureKind
import thwiply.elopenmike.com.llm.provider.ModelProvider
import thwiply.elopenmike.com.llm.provider.NanoState
import thwiply.elopenmike.com.llm.provider.ProviderReadiness
import thwiply.elopenmike.com.llm.provider.RetryAction
import thwiply.elopenmike.com.llm.provider.rejectionKind
import thwiply.elopenmike.com.llm.provider.retryAction

@StringRes
fun ModelProvider.label(): Int = when (this) {
    ModelProvider.QWEN -> R.string.provider_qwen
    ModelProvider.GEMINI_NANO -> R.string.provider_nano
}

@StringRes
fun FailureKind.message(): Int = when (this) {
    FailureKind.UNAVAILABLE -> R.string.inference_unavailable
    FailureKind.AICORE_NOT_READY -> R.string.inference_aicore
    FailureKind.DOWNLOAD -> R.string.inference_download
    FailureKind.BUSY -> R.string.inference_busy
    FailureKind.QUOTA -> R.string.inference_quota
    FailureKind.BACKGROUND -> R.string.inference_background
    FailureKind.SAFETY -> R.string.inference_safety
    FailureKind.INPUT_TOO_LONG -> R.string.inference_input
    FailureKind.INVALID_INPUT -> R.string.inference_invalid_input
    FailureKind.EMPTY_OUTPUT -> R.string.inference_empty
    FailureKind.TIMEOUT -> R.string.inference_timeout
    FailureKind.OUTPUT_LIMIT -> R.string.inference_output_limit
    FailureKind.RUNTIME -> R.string.inference_runtime
    FailureKind.PREFERENCE -> R.string.provider_preference_failed
    FailureKind.MODEL_STORAGE -> R.string.qwen_read_failed
    FailureKind.MODEL_CORRUPT -> R.string.inference_model_corrupt
    FailureKind.MODEL_RECORD -> R.string.qwen_record_unusable
    FailureKind.MODEL_FILE_MISSING -> R.string.qwen_corrupt_file_missing
    FailureKind.MODEL_REMOVAL -> R.string.qwen_removal_failed
}

/** A revalidation is a full-file check, not an engine load; the control must say which. */
@StringRes
fun FailureKind.retryLabel(): Int =
    if (retryAction == RetryAction.REVALIDATE) R.string.qwen_verify_again else R.string.lab_retry

@StringRes
fun ArtifactDefect.discardLabel(): Int = when (this) {
    ArtifactDefect.FILE_MISSING -> R.string.qwen_discard_record
    ArtifactDefect.SIZE_MISMATCH, ArtifactDefect.DIGEST_MISMATCH -> R.string.qwen_discard
}

/**
 * Thwiply's own Qwen artifact. Verified weights, an initialized engine and AICore's
 * Nano readiness are three separate things; this maps only the first.
 */
@StringRes
fun ModelArtifactState.message(): Int = when (this) {
    ModelArtifactState.Verifying -> R.string.qwen_verifying
    ModelArtifactState.Missing -> R.string.settings_no_qwen
    is ModelArtifactState.Ready -> R.string.qwen_verified
    is ModelArtifactState.Corrupt -> when (defect) {
        // A record with no file cannot be described as damaged bytes to remove.
        ArtifactDefect.FILE_MISSING -> R.string.qwen_corrupt_file_missing
        ArtifactDefect.SIZE_MISMATCH, ArtifactDefect.DIGEST_MISMATCH -> R.string.qwen_corrupt
    }
    ModelArtifactState.Removing -> R.string.qwen_removing
    // The shared mapping already distinguishes an unusable record and a failed removal
    // from an actual read failure.
    is ModelArtifactState.Failed -> requireNotNull(rejectionKind()).message()
}

@StringRes
fun NanoState.message(): Int = when (this) {
    NanoState.Checking -> R.string.nano_checking
    NanoState.Unavailable -> R.string.nano_unavailable
    NanoState.Downloadable -> R.string.nano_downloadable
    NanoState.Downloading -> R.string.nano_downloading
    NanoState.Ready -> R.string.nano_ready
    is NanoState.Failed -> failure.kind.message()
}

@StringRes
fun ProviderReadiness.message(provider: ModelProvider?, busy: Boolean): Int = when (this) {
    ProviderReadiness.Missing -> R.string.lab_missing
    ProviderReadiness.Removing -> R.string.qwen_removing
    ProviderReadiness.NeedsInitialization -> R.string.lab_needs_initialization
    ProviderReadiness.Initializing -> R.string.lab_initializing
    is ProviderReadiness.Failed -> if (provider == ModelProvider.QWEN && failure.kind == FailureKind.RUNTIME)
        R.string.lab_initialization_failed else failure.kind.message()
    ProviderReadiness.Ready -> R.string.lab_ready
    ProviderReadiness.Checking -> when (provider) {
        null -> R.string.provider_loading
        ModelProvider.QWEN -> R.string.qwen_verifying
        ModelProvider.GEMINI_NANO -> if (busy) R.string.nano_checking else R.string.nano_check_needed
    }
    ProviderReadiness.Unavailable -> R.string.nano_unavailable
    ProviderReadiness.Downloadable -> R.string.nano_downloadable
    ProviderReadiness.Downloading -> if (provider == ModelProvider.GEMINI_NANO)
        R.string.nano_downloading else R.string.lab_qwen_downloading
}

/** A composed but off-screen navigation destination must not keep doing model work. */
@Composable
fun ProviderForegroundEffect(
    foreground: StateFlow<Boolean>,
    providerKey: Any?,
    onEnter: () -> Unit,
    onExit: () -> Unit,
) {
    val owner = LocalLifecycleOwner.current
    val enter by rememberUpdatedState(onEnter)
    val exit by rememberUpdatedState(onExit)
    LaunchedEffect(owner, foreground, providerKey) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                foreground.collect { active -> if (active) enter() else exit() }
            } finally {
                exit()
            }
        }
    }
    DisposableEffect(owner) {
        onDispose { exit() }
    }
}

/** The one-line Settings storage label. Defect-aware, so absent weights are never "installed". */
@StringRes
fun ModelArtifactState.storageLabel(): Int = when (this) {
    ModelArtifactState.Verifying -> R.string.settings_model_verifying
    ModelArtifactState.Missing -> R.string.settings_no_qwen
    is ModelArtifactState.Ready -> R.string.settings_model_installed
    is ModelArtifactState.Corrupt -> when (defect) {
        ArtifactDefect.FILE_MISSING -> R.string.settings_model_record_only
        ArtifactDefect.SIZE_MISMATCH, ArtifactDefect.DIGEST_MISMATCH -> R.string.settings_model_damaged
    }
    ModelArtifactState.Removing -> R.string.settings_model_removing
    // Terse on this surface: Settings hosts no recovery control, so it states the condition and
    // leaves the actionable sentences to setup and Lab, where the buttons are.
    is ModelArtifactState.Failed -> when (rejectionKind()) {
        FailureKind.MODEL_RECORD -> R.string.settings_model_record_unusable
        FailureKind.MODEL_REMOVAL -> R.string.settings_model_removal_incomplete
        else -> R.string.settings_model_unreadable
    }
}
