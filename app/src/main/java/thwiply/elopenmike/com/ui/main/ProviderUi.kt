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
import thwiply.elopenmike.com.llm.provider.FailureKind
import thwiply.elopenmike.com.llm.provider.ModelProvider
import thwiply.elopenmike.com.llm.provider.NanoState
import thwiply.elopenmike.com.llm.provider.ProviderReadiness

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
    FailureKind.MODEL_STORAGE -> R.string.qwen_metadata_failed
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
    ProviderReadiness.NeedsInitialization -> R.string.lab_needs_initialization
    ProviderReadiness.Initializing -> R.string.lab_initializing
    is ProviderReadiness.Failed -> if (provider == ModelProvider.QWEN && failure.kind == FailureKind.RUNTIME)
        R.string.lab_initialization_failed else failure.kind.message()
    ProviderReadiness.Ready -> R.string.lab_ready
    ProviderReadiness.Checking -> when (provider) {
        null -> R.string.provider_loading
        ModelProvider.QWEN -> R.string.qwen_metadata_checking
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
