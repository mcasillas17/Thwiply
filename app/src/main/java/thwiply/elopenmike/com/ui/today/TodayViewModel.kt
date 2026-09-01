package thwiply.elopenmike.com.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import thwiply.elopenmike.com.domain.triage.DecisionOrigin
import thwiply.elopenmike.com.domain.triage.NotificationDataLifecycleRepository
import thwiply.elopenmike.com.domain.triage.RepositoryResult
import thwiply.elopenmike.com.domain.triage.SourceReference
import thwiply.elopenmike.com.domain.triage.StorageFailureReason
import thwiply.elopenmike.com.domain.triage.StorageOperation
import thwiply.elopenmike.com.domain.triage.TriageCategory
import thwiply.elopenmike.com.domain.triage.TriageDecision
import thwiply.elopenmike.com.domain.triage.TriageItem
import thwiply.elopenmike.com.domain.triage.TriageRecord
import thwiply.elopenmike.com.domain.triage.TriageRepository

enum class TaskFilter(val label: String) {
    ALL("All"),
    NOTIFICATIONS("Notifications"),
    HIGH_PRIORITY("High Priority"),
    COMPLETED("Done"),
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val triageRepository: TriageRepository,
    private val dataLifecycleRepository: NotificationDataLifecycleRepository,
) : ViewModel() {
    val uiState: StateFlow<TodayUiState> = flow {
        when (
            dataLifecycleRepository.purgeExpiredNotificationData(
                nowEpochMillis = System.currentTimeMillis(),
            )
        ) {
            is RepositoryResult.Success -> emitAll(
                triageRepository.observeTriageRecords().map { result ->
                    when (result) {
                        is RepositoryResult.Success -> {
                            val tasks = result.value.map(TriageRecord::toTaskItem)
                            if (tasks.isEmpty()) {
                                TodayUiState.Empty
                            } else {
                                TodayUiState.Content(tasks)
                            }
                        }

                        is RepositoryResult.Failure -> TodayUiState.StorageError
                    }
                },
            )

            is RepositoryResult.Failure -> emit(TodayUiState.StorageError)
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = TodayUiState.Loading,
        )

    private val _selectedFilter = MutableStateFlow(TaskFilter.ALL)
    val selectedFilter: StateFlow<TaskFilter> = _selectedFilter.asStateFlow()

    private val _operationFailure = MutableStateFlow<RepositoryResult.Failure?>(null)
    val operationFailure: StateFlow<RepositoryResult.Failure?> = _operationFailure.asStateFlow()

    fun setFilter(filter: TaskFilter) {
        _selectedFilter.value = filter
    }

    fun toggleTask(id: String) {
        val task = (uiState.value as? TodayUiState.Content)
            ?.tasks
            ?.firstOrNull { it.id == id }
        if (task == null) {
            _operationFailure.value = RepositoryResult.Failure(
                operation = StorageOperation.SET_TRIAGE_COMPLETION,
                reason = StorageFailureReason.NOT_FOUND,
                cause = null,
            )
            return
        }
        val completedAtEpochMillis = if (task.isCompleted) {
            null
        } else {
            maxOf(System.currentTimeMillis(), task.createdAtEpochMillis)
        }
        runRepositoryOperation {
            triageRepository.setTriageItemCompleted(id, completedAtEpochMillis)
        }
    }

    fun deleteTask(id: String) {
        runRepositoryOperation { triageRepository.deleteTriageItem(id) }
    }

    fun addTask(
        title: String,
        subtitle: String?,
        isHighPriority: Boolean,
    ) {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotBlank()) { "task title must not be blank" }
        val now = System.currentTimeMillis()
        val itemId = UUID.randomUUID().toString()
        val record = TriageRecord(
            item = TriageItem(
                id = itemId,
                displayTitle = normalizedTitle,
                displaySummary = subtitle?.trim()?.ifBlank { null },
                source = SourceReference.manual(),
                isHighPriority = isHighPriority,
                createdAtEpochMillis = now,
                dueAtEpochMillis = null,
                completedAtEpochMillis = null,
            ),
            decision = TriageDecision(
                id = UUID.randomUUID().toString(),
                triageItemId = itemId,
                category = TriageCategory.NOW,
                explanation = "Added manually",
                origin = DecisionOrigin.MANUAL,
                decidedAtEpochMillis = now,
            ),
        )
        runRepositoryOperation { triageRepository.createTriageRecord(record) }
    }

    fun dismissOperationFailure() {
        _operationFailure.value = null
    }

    private fun runRepositoryOperation(
        operation: suspend () -> RepositoryResult<Unit>,
    ) {
        viewModelScope.launch {
            when (val result = operation()) {
                is RepositoryResult.Success -> Unit
                is RepositoryResult.Failure -> _operationFailure.value = result
            }
        }
    }
}
