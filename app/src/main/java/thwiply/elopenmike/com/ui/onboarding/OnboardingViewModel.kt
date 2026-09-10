package thwiply.elopenmike.com.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import thwiply.elopenmike.com.llm.model.DownloadState
import thwiply.elopenmike.com.llm.model.ModelPreset
import thwiply.elopenmike.com.llm.model.ModelArtifactState
import thwiply.elopenmike.com.llm.provider.InferenceCoordinator
import thwiply.elopenmike.com.llm.provider.InferenceFailure
import thwiply.elopenmike.com.llm.provider.ModelProvider
import thwiply.elopenmike.com.data.preferences.AppPreferencesRepository

@HiltViewModel
class OnboardingViewModel internal constructor(
    private val coordinator: InferenceCoordinator,
    private val preferenceRepository: AppPreferencesRepository,
    private val downloadModel: (ModelPreset) -> Flow<DownloadState>,
) : ViewModel() {
    @Inject constructor(
        coordinator: InferenceCoordinator,
        preferences: AppPreferencesRepository,
    ) : this(coordinator, preferences, coordinator::downloadQwen)

    // The artifact collector below is the only publisher of this state; it emits the current
    // value undispatched at construction, so there is no second source to keep in step.
    private val _uiState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val uiState: StateFlow<DownloadState> = _uiState.asStateFlow()
    val selection = coordinator.selection
    val nanoState = coordinator.nanoState
    val busy = coordinator.busy
    val foreground = coordinator.foreground
    val qwenArtifact = coordinator.qwenArtifact
    val preferences = preferenceRepository.state
    private val _failure = MutableStateFlow<InferenceFailure?>(null)
    val failure = _failure.asStateFlow()
    private val _selecting = MutableStateFlow(false)
    val selecting = _selecting.asStateFlow()
    private var downloadJob: Job? = null
    private var providerJob: Job? = null
    private var selectionJob: Job? = null

    fun acknowledgeModelSetupEducation() {
        viewModelScope.launch { preferenceRepository.acknowledgeModelSetupEducation() }
    }

    fun reloadPreferences() {
        viewModelScope.launch { preferenceRepository.reload() }
    }

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            qwenArtifact.collect {
                if (downloadJob?.isCompleted == false) return@collect
                // Setup never keeps reporting a completed install for weights that stopped verifying.
                if (it is ModelArtifactState.Ready) _uiState.value = DownloadState.Success
                else if (_uiState.value is DownloadState.Success) _uiState.value = DownloadState.Idle
            }
        }
    }

    /** Explicit revalidation of the installed artifact. It never starts a download. */
    fun verifyModel() {
        if (selection.value.provider == ModelProvider.QWEN) providerAction { coordinator.verifyQwen() }
    }

    /** Explicit discard of a rejected installation. Tasks and preferences are kept. */
    fun discardRejectedModel() {
        if (selection.value.provider == ModelProvider.QWEN) {
            providerAction { coordinator.discardRejectedQwen() }
        }
    }

    fun selectProvider(provider: ModelProvider) {
        if (selectionJob?.isCompleted == false || providerJob?.isCompleted == false ||
            downloadJob?.isCompleted == false) return
        selectionJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            _selecting.value = true
            _failure.value = null
            try {
                coordinator.selectProvider(provider)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: InferenceFailure) {
                _failure.value = failure
            } finally {
                _selecting.value = false
            }
        }
    }

    fun checkNano() = providerAction { coordinator.checkNano() }

    fun downloadNano() {
        if (selection.value.provider == ModelProvider.GEMINI_NANO) {
            providerAction { coordinator.downloadNano() }
        }
    }

    /** One owned provider operation at a time; the coordinator still holds the real lease. */
    private fun providerAction(action: suspend () -> Unit) {
        if (providerJob?.isActive == true || downloadJob?.isActive == true) return
        providerJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            _failure.value = null
            try {
                busy.first { !it }
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: InferenceFailure) {
                _failure.value = failure
            }
        }
    }

    fun startDownload() {
        // A cancelled blocking I/O writer may still be unwinding. Never overlap it.
        if (downloadJob?.isCompleted == false || busy.value ||
            selection.value.provider != ModelProvider.QWEN) return
        downloadJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            _failure.value = null
            _uiState.value = DownloadState.Downloading(0)
            try {
                downloadModel(ModelPreset.QWEN_2_5_1_5B).collect { _uiState.value = it }
            } catch (failure: InferenceFailure) {
                _failure.value = failure
            } catch (error: IOException) {
                _uiState.value = DownloadState.Error(error.message ?: error.javaClass.simpleName)
            } finally {
                if (_uiState.value is DownloadState.Downloading) {
                    _uiState.value = DownloadState.Idle
                }
            }
        }
    }

    fun pauseDownload() {
        downloadJob?.cancel()
        providerJob?.cancel()
    }
}
