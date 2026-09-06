package thwiply.elopenmike.com.ui.today

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import thwiply.elopenmike.com.domain.cleanup.CLEANUP_LOG_TAG
import thwiply.elopenmike.com.domain.cleanup.NotificationCleanupTrigger
import thwiply.elopenmike.com.domain.cleanup.NotificationDataCleanupCoordinator
import thwiply.elopenmike.com.domain.triage.DecisionOrigin
import thwiply.elopenmike.com.domain.triage.RepositoryResult
import thwiply.elopenmike.com.domain.triage.SourceReference
import thwiply.elopenmike.com.domain.triage.StorageFailureReason
import thwiply.elopenmike.com.domain.triage.StorageOperation
import thwiply.elopenmike.com.domain.triage.TriageCategory
import thwiply.elopenmike.com.domain.triage.TriageDecision
import thwiply.elopenmike.com.domain.triage.TriageItem
import thwiply.elopenmike.com.domain.triage.TriageRecord
import thwiply.elopenmike.com.domain.triage.TriageRepository
import thwiply.elopenmike.com.domain.triage.VisibleTriageRecords

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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val triageRepository: TriageRepository,
    private val cleanupCoordinator: NotificationDataCleanupCoordinator,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()
    private var todayObservationJob: Job? = null
    private var expiryRefreshJob: Job? = null

    /** Time the visible records were read as of; a new value re-reads with a fresh cutoff. */
    private val visibleAsOfEpochMillis = MutableStateFlow(0L)

    private val _selectedFilter = MutableStateFlow(TaskFilter.ALL)
    val selectedFilter: StateFlow<TaskFilter> = _selectedFilter.asStateFlow()

    private val _operationFailure = MutableStateFlow<RepositoryResult.Failure?>(null)
    val operationFailure: StateFlow<RepositoryResult.Failure?> = _operationFailure.asStateFlow()

    private val _taskInputFailure = MutableStateFlow<TaskInputFailure?>(null)
    val taskInputFailure: StateFlow<TaskInputFailure?> = _taskInputFailure.asStateFlow()

    /**
     * True when the last cleanup run could not delete expired notification data. Expired
     * records stay hidden and manual tasks stay usable, so this never blocks the screen.
     */
    private val _cleanupWarning = MutableStateFlow(false)
    val cleanupWarning: StateFlow<Boolean> = _cleanupWarning.asStateFlow()

    fun setFilter(filter: TaskFilter) {
        _selectedFilter.value = filter
    }

    /**
     * Entry and every resume of Today: re-read records against the current time and run the
     * shared cleanup. The two are independent jobs, so a cleanup failure can neither cancel
     * the read nor hide durable rows, and already-loaded records are not replaced by a
     * loading state on re-entry.
     */
    fun onTodayEntered() {
        todayObservationJob?.cancel()
        expiryRefreshJob?.cancel()
        if (_uiState.value !is TodayUiState.Content) {
            _uiState.value = TodayUiState.Loading
        }
        visibleAsOfEpochMillis.value = clock.millis()
        launchCleanup(NotificationCleanupTrigger.TODAY_ENTRY)
        todayObservationJob = viewModelScope.launch {
            visibleAsOfEpochMillis
                .flatMapLatest { nowEpochMillis ->
                    triageRepository.observeVisibleTriageRecords(nowEpochMillis)
                }
                .collect(::render)
        }
    }

    fun retryCleanup() {
        launchCleanup(NotificationCleanupTrigger.TODAY_ENTRY)
    }

    /**
     * Cleanup is best effort. A typed storage failure raises the nonblocking warning; an
     * unexpected failure raises the same warning and records a bounded, content-free
     * diagnostic instead of taking Today down. Cancellation is never handled here.
     */
    private fun launchCleanup(trigger: NotificationCleanupTrigger) {
        val unexpectedFailureHandler = CoroutineExceptionHandler { _, throwable ->
            _cleanupWarning.value = true
            Log.e(
                CLEANUP_LOG_TAG,
                "trigger=$trigger outcome=unexpected error=${throwable.javaClass.simpleName}",
            )
        }
        viewModelScope.launch(unexpectedFailureHandler) {
            val outcome = cleanupCoordinator.cleanUpExpiredNotificationData(trigger)
            _cleanupWarning.value = outcome is RepositoryResult.Failure
        }
    }

    private fun render(result: RepositoryResult<VisibleTriageRecords>) {
        _uiState.value = when (result) {
            is RepositoryResult.Success -> {
                scheduleExpiryRefresh(result.value.nextExpiryAtEpochMillis)
                val tasks = result.value.records.map(TriageRecord::toTaskItem)
                if (tasks.isEmpty()) TodayUiState.Empty else TodayUiState.Content(tasks)
            }

            is RepositoryResult.Failure -> TodayUiState.StorageError
        }
    }

    /**
     * Re-reads once when the earliest visible notification record expires. One timer per
     * emission, driven by stored expiry values, so nothing polls while Today is open. The
     * timer does not advance while the device sleeps; the resume boundary covers that.
     */
    private fun scheduleExpiryRefresh(nextExpiryAtEpochMillis: Long?) {
        expiryRefreshJob?.cancel()
        val expiry = nextExpiryAtEpochMillis ?: return
        expiryRefreshJob = viewModelScope.launch {
            delay(expiry - clock.millis())
            visibleAsOfEpochMillis.value = maxOf(clock.millis(), expiry)
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
                completedAtEpochMillis = maxOf(clock.millis(), task.createdAtEpochMillis),
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
        val now = clock.millis()
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
