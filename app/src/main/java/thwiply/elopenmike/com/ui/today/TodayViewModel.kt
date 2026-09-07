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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
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
    /** Time the visible records were read as of; a new value re-reads with a fresh cutoff. */
    private val visibleAsOfEpochMillis = MutableStateFlow(clock.millis())

    /**
     * What Today renders: the screen-owned Room observation and its single expiry timer,
     * re-checked against the cutoff in force. One observation serves every collector and
     * closes as soon as this stops being collected, so a stopped screen leaves neither a
     * database observer nor a timer running.
     *
     * Starting to collect is itself the visibility boundary. Compose collects at `STARTED`,
     * which is earlier than the `RESUMED` entry callback, so the cutoff is refreshed here
     * before anything is read and a restarted subscription always queries at the current time.
     *
     * What the screen last rendered lives exactly as long as the observation behind it: a
     * recreation or tab change inside [SUBSCRIPTION_STOP_TIMEOUT_MS] keeps the content, and a
     * longer absence resets to [TodayUiState.Loading] and reloads. Today therefore never
     * replays a snapshot that nothing was observing, whether a record reached its retention or
     * Settings deleted the notification data while the screen was away. Application-owned work
     * (retention cleanup and daily maintenance) is deliberately not part of this subscription.
     */
    val uiState: StateFlow<TodayUiState> = combine(
        visibleAsOfEpochMillis.flatMapLatest { asOf ->
            triageRepository.observeVisibleTriageRecords(asOf).transformLatest { result ->
                emit(result)
                // One timer per read, driven by stored expiry values, so nothing polls while
                // Today is open. The next read cancels it and the subscription ending cancels
                // it; it never delays a read, because transformLatest restarts on every
                // emission. The timer does not advance while the device sleeps; a restarted
                // subscription and the resume boundary both re-read at the current time.
                val expiry = (result as? RepositoryResult.Success)
                    ?.value
                    ?.nextExpiryAtEpochMillis
                    ?: return@transformLatest
                if (expiry <= asOf) return@transformLatest
                delay(expiry - clock.millis())
                visibleAsOfEpochMillis.value = maxOf(clock.millis(), expiry)
            }
        },
        visibleAsOfEpochMillis,
        ::toUiState,
    )
        .onStart { visibleAsOfEpochMillis.value = clock.millis() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = SUBSCRIPTION_STOP_TIMEOUT_MS,
                replayExpirationMillis = 0,
            ),
            initialValue = TodayUiState.Loading,
        )

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
     * shared cleanup. The two are independent, so a cleanup failure can neither cancel the
     * read nor hide durable rows, and already-loaded records are not replaced by a loading
     * state on re-entry. Starting and stopping are not this call's job: the observation
     * begins and ends with the lifecycle-aware collector that owns it.
     */
    fun onTodayEntered() {
        visibleAsOfEpochMillis.value = clock.millis()
        launchCleanup(NotificationCleanupTrigger.TODAY_ENTRY)
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

    /**
     * Renders the most recent read against the cutoff in force. A read taken against an older
     * cutoff can contain a notification-derived row whose own retention has ended since, so
     * each record is re-checked against its own expiry and only the ones actually past it are
     * dropped until the fresh read lands. Manual rows have no retention and always stay.
     */
    private fun toUiState(
        result: RepositoryResult<VisibleTriageRecords>,
        asOfEpochMillis: Long,
    ): TodayUiState = when (result) {
        is RepositoryResult.Failure -> TodayUiState.StorageError
        is RepositoryResult.Success -> {
            val tasks = result.value.records
                .filterNot { result.value.hasExpired(it, asOfEpochMillis) }
                .map(TriageRecord::toTaskItem)
            if (tasks.isEmpty()) TodayUiState.Empty else TodayUiState.Content(tasks)
        }
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

    companion object {
        /**
         * How long the screen-owned Room observation, expiry timer and last rendered state
         * outlive their last collector. Long enough to carry an activity recreation or a tab
         * change, short enough that a backgrounded screen stops observing. Nothing survives
         * past it, so a returning screen reloads instead of replaying an unobserved snapshot.
         */
        const val SUBSCRIPTION_STOP_TIMEOUT_MS = 5_000L
    }
}
