package thwiply.elopenmike.com.ui.today

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import thwiply.elopenmike.com.domain.triage.DecisionOrigin
import thwiply.elopenmike.com.domain.triage.NotificationDataDeletion
import thwiply.elopenmike.com.domain.triage.NotificationDataLifecycleRepository
import thwiply.elopenmike.com.domain.triage.RepositoryResult
import thwiply.elopenmike.com.domain.triage.SourceKind
import thwiply.elopenmike.com.domain.triage.SourceReference
import thwiply.elopenmike.com.domain.triage.StorageFailureReason
import thwiply.elopenmike.com.domain.triage.StorageOperation
import thwiply.elopenmike.com.domain.triage.TriageCategory
import thwiply.elopenmike.com.domain.triage.TriageDecision
import thwiply.elopenmike.com.domain.triage.TriageItem
import thwiply.elopenmike.com.domain.triage.TriageRecord
import thwiply.elopenmike.com.domain.triage.TriageRepository
import thwiply.elopenmike.com.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state is loading before the repository flow is collected`() = runTest {
        val viewModel = todayViewModel(FakeTriageRepository(), enterToday = false)

        assertEquals(TodayUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `empty repository renders a real empty state`() = runTest {
        val repository = FakeTriageRepository()
        val viewModel = todayViewModel(repository)

        advanceUntilIdle()

        assertEquals(TodayUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `durable records map to content without raw notification text`() = runTest {
        val repository = FakeTriageRepository(listOf(notificationRecord()))
        val viewModel = todayViewModel(repository)

        advanceUntilIdle()

        val task = (viewModel.uiState.value as TodayUiState.Content).tasks.single()
        assertEquals("Call Alex", task.title)
        assertEquals(SourceKind.NOTIFICATION, task.sourceKind)
        assertEquals("Messages", task.sourceLabel)
        assertEquals("Direct request", task.decisionExplanation)
    }

    @Test
    fun `repository read failure renders storage error`() = runTest {
        val failure = RepositoryResult.Failure(
            operation = StorageOperation.OBSERVE_TRIAGE,
            reason = StorageFailureReason.DATABASE,
            cause = null,
        )
        val repository = FakeTriageRepository(initialResult = failure)
        val viewModel = todayViewModel(repository)

        advanceUntilIdle()

        assertEquals(TodayUiState.StorageError, viewModel.uiState.value)
    }

    @Test
    fun `quick add creates a trimmed manual record`() = runTest {
        val repository = FakeTriageRepository()
        val viewModel = todayViewModel(repository)

        viewModel.addTask("  Buy milk  ", "  Before dinner  ", isHighPriority = true)
        advanceUntilIdle()

        val created = repository.createdRecords.single()
        assertEquals("Buy milk", created.item.displayTitle)
        assertEquals("Before dinner", created.item.displaySummary)
        assertEquals(SourceKind.MANUAL, created.item.source.kind)
        assertEquals(TriageCategory.NOW, created.decision.category)
        assertEquals(DecisionOrigin.MANUAL, created.decision.origin)
    }

    @Test
    fun `quick add rejects an overlong title without writing`() = runTest {
        val repository = FakeTriageRepository()
        val viewModel = todayViewModel(repository)

        viewModel.addTask(
            title = "x".repeat(TriageItem.MAX_DISPLAY_TITLE_LENGTH + 1),
            subtitle = null,
            isHighPriority = false,
        )
        advanceUntilIdle()

        assertEquals(emptyList<TriageRecord>(), repository.createdRecords)
        assertEquals(TaskInputFailure.TITLE_TOO_LONG, viewModel.taskInputFailure.value)
    }

    @Test
    fun `quick add rejects an overlong summary without writing`() = runTest {
        val repository = FakeTriageRepository()
        val viewModel = todayViewModel(repository)

        viewModel.addTask(
            title = "Buy milk",
            subtitle = "x".repeat(TriageItem.MAX_DISPLAY_SUMMARY_LENGTH + 1),
            isHighPriority = false,
        )
        advanceUntilIdle()

        assertEquals(emptyList<TriageRecord>(), repository.createdRecords)
        assertEquals(TaskInputFailure.SUMMARY_TOO_LONG, viewModel.taskInputFailure.value)
    }

    @Test
    fun `complete and delete delegate to durable repository operations`() = runTest {
        val record = manualRecord()
        val repository = FakeTriageRepository(listOf(record))
        val viewModel = todayViewModel(repository)
        advanceUntilIdle()

        viewModel.toggleTask(record.item.id)
        viewModel.deleteTask(record.item.id)
        advanceUntilIdle()

        assertEquals(record.item.id, repository.completionIds.single())
        assertNotNull(repository.completionTimes.single())
        assertEquals(listOf(record.item.id), repository.deletedIds)
    }

    @Test
    fun `write failure remains visible until dismissed`() = runTest {
        val failure = RepositoryResult.Failure(
            operation = StorageOperation.CREATE_TRIAGE,
            reason = StorageFailureReason.DATABASE,
            cause = null,
        )
        val repository = FakeTriageRepository(createResult = failure)
        val viewModel = todayViewModel(repository)

        viewModel.addTask("Buy milk", null, isHighPriority = false)
        advanceUntilIdle()

        assertEquals(failure, viewModel.operationFailure.value)
        viewModel.dismissOperationFailure()
        assertNull(viewModel.operationFailure.value)
    }

    @Test
    fun `opening Today purges expired notification data`() = runTest {
        val lifecycleRepository = FakeLifecycleRepository()
        TodayViewModel(FakeTriageRepository(), lifecycleRepository).onTodayEntered()

        advanceUntilIdle()

        assertEquals(1, lifecycleRepository.purgeTimes.size)
        assertNotNull(lifecycleRepository.purgeTimes.single())
    }

    @Test
    fun `retention failure blocks durable rows from being displayed`() = runTest {
        val failure = RepositoryResult.Failure(
            operation = StorageOperation.PURGE_EXPIRED_NOTIFICATION_DATA,
            reason = StorageFailureReason.DATABASE,
            cause = null,
        )
        val viewModel = TodayViewModel(
            FakeTriageRepository(listOf(notificationRecord())),
            FakeLifecycleRepository(purgeResult = failure),
        )
        viewModel.onTodayEntered()

        advanceUntilIdle()

        assertEquals(TodayUiState.StorageError, viewModel.uiState.value)
    }

    @Test
    fun `reentering Today reruns retention and recovers from an earlier failure`() = runTest {
        val failure = RepositoryResult.Failure(
            operation = StorageOperation.PURGE_EXPIRED_NOTIFICATION_DATA,
            reason = StorageFailureReason.DATABASE,
            cause = null,
        )
        val lifecycleRepository = FakeLifecycleRepository(purgeResult = failure)
        val viewModel = TodayViewModel(FakeTriageRepository(), lifecycleRepository)
        viewModel.onTodayEntered()
        advanceUntilIdle()
        assertEquals(TodayUiState.StorageError, viewModel.uiState.value)

        lifecycleRepository.purgeResult = RepositoryResult.Success(0)
        viewModel.onTodayEntered()
        advanceUntilIdle()

        assertEquals(TodayUiState.Empty, viewModel.uiState.value)
        assertEquals(2, lifecycleRepository.purgeTimes.size)
    }

    private fun todayViewModel(
        repository: TriageRepository,
        enterToday: Boolean = true,
    ) = TodayViewModel(repository, FakeLifecycleRepository()).also { viewModel ->
        if (enterToday) viewModel.onTodayEntered()
    }

    private fun manualRecord() = record(
        source = SourceReference.manual(),
        title = "Buy milk",
        explanation = "Added manually",
    )

    private fun notificationRecord() = record(
        source = SourceReference.notification(
            packageName = "com.example.messages",
            appLabel = "Messages",
            stableKeyHash = "a".repeat(64),
        ),
        title = "Call Alex",
        explanation = "Direct request",
    )

    private fun record(
        source: SourceReference,
        title: String,
        explanation: String,
    ): TriageRecord {
        val item = TriageItem(
            id = "item-1",
            displayTitle = title,
            displaySummary = null,
            source = source,
            isHighPriority = true,
            createdAtEpochMillis = 100,
            dueAtEpochMillis = null,
            completedAtEpochMillis = null,
        )
        return TriageRecord(
            item = item,
            decision = TriageDecision(
                id = "decision-1",
                triageItemId = item.id,
                category = TriageCategory.NOW,
                explanation = explanation,
                origin = if (source.kind == SourceKind.MANUAL) {
                    DecisionOrigin.MANUAL
                } else {
                    DecisionOrigin.ON_DEVICE_MODEL
                },
                decidedAtEpochMillis = 100,
            ),
        )
    }

    private class FakeTriageRepository(
        initialRecords: List<TriageRecord> = emptyList(),
        initialResult: RepositoryResult<List<TriageRecord>> = RepositoryResult.Success(
            initialRecords,
        ),
        private val createResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
        private val completionResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
        private val deleteResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
    ) : TriageRepository {
        private val records = MutableStateFlow(initialResult)
        val createdRecords = mutableListOf<TriageRecord>()
        val completionIds = mutableListOf<String>()
        val completionTimes = mutableListOf<Long?>()
        val deletedIds = mutableListOf<String>()

        override fun observeTriageRecords(): Flow<RepositoryResult<List<TriageRecord>>> = records

        override suspend fun createTriageRecord(record: TriageRecord): RepositoryResult<Unit> {
            createdRecords += record
            return createResult
        }

        override suspend fun updateTriageItem(item: TriageItem): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun toggleTriageItemCompletion(
            triageItemId: String,
            completedAtEpochMillis: Long,
        ): RepositoryResult<Unit> {
            completionIds += triageItemId
            completionTimes += completedAtEpochMillis
            return completionResult
        }

        override suspend fun deleteTriageItem(
            triageItemId: String,
        ): RepositoryResult<Unit> {
            deletedIds += triageItemId
            return deleteResult
        }
    }

    private class FakeLifecycleRepository(
        var purgeResult: RepositoryResult<Int> = RepositoryResult.Success(0),
    ) : NotificationDataLifecycleRepository {
        val purgeTimes = mutableListOf<Long>()

        override suspend fun purgeExpiredNotificationData(
            nowEpochMillis: Long,
        ): RepositoryResult<Int> {
            purgeTimes += nowEpochMillis
            return purgeResult
        }

        override suspend fun deleteAllNotificationDataAndRules():
            RepositoryResult<NotificationDataDeletion> = RepositoryResult.Success(
            NotificationDataDeletion(
                notificationItemsDeleted = 0,
                rulesDeleted = 0,
            ),
        )
    }
}
