package thwiply.elopenmike.com.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import thwiply.elopenmike.com.domain.triage.NotificationDataDeletion
import thwiply.elopenmike.com.domain.triage.NotificationDataLifecycleRepository
import thwiply.elopenmike.com.domain.triage.RepositoryResult

sealed interface NotificationDataDeletionState {
    data object Idle : NotificationDataDeletionState

    data object Deleting : NotificationDataDeletionState

    data class Deleted(
        val deletion: NotificationDataDeletion,
    ) : NotificationDataDeletionState

    data object Error : NotificationDataDeletionState
}

@HiltViewModel
class NotificationDataSettingsViewModel @Inject constructor(
    private val dataLifecycleRepository: NotificationDataLifecycleRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<NotificationDataDeletionState>(
        NotificationDataDeletionState.Idle,
    )
    val state: StateFlow<NotificationDataDeletionState> = _state.asStateFlow()

    fun deleteAllNotificationDataAndRules() {
        if (_state.value == NotificationDataDeletionState.Deleting) return
        _state.value = NotificationDataDeletionState.Deleting
        viewModelScope.launch {
            _state.value = when (
                val result = dataLifecycleRepository.deleteAllNotificationDataAndRules()
            ) {
                is RepositoryResult.Success -> NotificationDataDeletionState.Deleted(result.value)
                is RepositoryResult.Failure -> NotificationDataDeletionState.Error
            }
        }
    }

    fun dismissResult() {
        _state.value = NotificationDataDeletionState.Idle
    }
}
