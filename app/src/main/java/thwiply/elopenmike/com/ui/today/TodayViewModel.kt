package thwiply.elopenmike.com.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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

enum class TaskInputFailure {
    BLANK_TITLE,
    TITLE_TOO_LONG,
    SUMMARY_TOO_LONG,
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val triageRepository: TriageRepository,
    private val dataLifecycleRepository: NotificationDataLifecycleRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()
    private var todayObservationJob: Job? = null

    private val _selectedFilter = MutableStateFlow(TaskFilter.ALL)
    val selectedFilter: StateFlow<TaskFilter> = _selectedFilter.asStateFlow()

    private val _operationFailure = MutableStateFlow<RepositoryResult.Failure?>(null)
    val operationFailure: StateFlow<RepositoryResult.Failure?> = _operationFailure.asStateFlow()

    private val _taskInputFailure = MutableStateFlow<TaskInputFailure?>(null)
    val taskInputFailure: StateFlow<TaskInputFailure?> = _taskInputFailure.asStateFlow()

    fun setFilter(filter: TaskFilter) {
        _selectedFilter.value = filter
    }

    fun onTodayEntered() {
        todayObservationJob?.cancel()
        _uiState.value = TodayUiState.Loading
        todayObservationJob = viewModelScope.launch {
            when (
                dataLifecycleRepository.purgeExpiredNotificationData(
                    nowEpochMillis = System.currentTimeMillis(),
                )
            ) {
                is RepositoryResult.Success -> {
                    triageRepository.observeTriageRecords().collect { result ->
                        _uiState.value = when (result) {
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
                    }
                }

                is RepositoryResult.Failure -> _uiState.value = TodayUiState.StorageError
            }
        }
    }

    fun toggleTask(id: String) {
        val task = (uiState.value as? TodayUiState.Content)
            ?.tasks
            ?.firstOrNull { it.id == id }
        if (task == null) {
            _operationFailure.value = RepositoryResult.Failure(
                operation = StorageOperation.TOGGLE_TRIAGE_COMPLETION,
                reason = StorageFailureReason.NOT_FOUND,
                cause = null,
            )
            return
        }
        runRepositoryOperation {
            triageRepository.toggleTriageItemCompletion(
                triageItemId = id,
                completedAtEpochMillis = maxOf(
                    System.currentTimeMillis(),
                    task.createdAtEpochMillis,
                ),
            )
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
        val normalizedSummary = subtitle?.trim()?.ifBlank { null }
        val inputFailure = when {
            normalizedTitle.isBlank() -> TaskInputFailure.BLANK_TITLE
            normalizedTitle.length > TriageItem.MAX_DISPLAY_TITLE_LENGTH -> {
                TaskInputFailure.TITLE_TOO_LONG
            }
            normalizedSummary?.length?.let { it > TriageItem.MAX_DISPLAY_SUMMARY_LENGTH } == true -> {
                TaskInputFailure.SUMMARY_TOO_LONG
            }
            else -> null
        }
        if (inputFailure != null) {
            _taskInputFailure.value = inputFailure
            return
        }
        _taskInputFailure.value = null
        val now = System.currentTimeMillis()
        val itemId = UUID.randomUUID().toString()
        val record = TriageRecord(
            item = TriageItem(
                id = itemId,
                displayTitle = normalizedTitle,
                displaySummary = normalizedSummary,
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

    fun dismissTaskInputFailure() {
        _taskInputFailure.value = null
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
