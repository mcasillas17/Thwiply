package thwiply.elopenmike.com.ui.today

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import thwiply.elopenmike.com.domain.cleanup.NotificationDataCleanupCoordinator
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
import thwiply.elopenmike.com.domain.triage.VisibleTriageRecords
import thwiply.elopenmike.com.testing.MainDispatcherRule
import thwiply.elopenmike.com.testing.MutableClock

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = MutableClock(NOW)

    @Test
    fun `initial state is loading before the repository flow is collected`() = runTest {
        val viewModel = todayViewModel(FakeTriageRepository(), enterToday = false)

        assertEquals(TodayUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `empty repository renders a real empty state`() = runTest {
        val viewModel = todayViewModel(FakeTriageRepository())

        advanceUntilIdle()

        assertEquals(TodayUiState.Empty, viewModel.uiState.value)
        assertFalse(viewModel.cleanupWarning.value)
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
    fun `entering Today reads visible records as of the current clock time`() = runTest {
        val repository = FakeTriageRepository()
        todayViewModel(repository)

        advanceUntilIdle()

        assertEquals(listOf(NOW), repository.queryTimes)
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
        assertFalse(viewModel.cleanupWarning.value)
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
        assertEquals(NOW, created.item.createdAtEpochMillis)
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
    fun `opening Today runs cleanup through the shared coordinator`() = runTest {
        val lifecycleRepository = FakeLifecycleRepository()
        todayViewModel(FakeTriageRepository(), lifecycleRepository = lifecycleRepository)

        advanceUntilIdle()

        assertEquals(listOf(NOW), lifecycleRepository.purgeTimes)
    }

    @Test
    fun `cleanup failure keeps manual tasks usable behind a nonblocking warning`() = runTest {
        val manual = manualRecord()
        val repository = FakeTriageRepository(listOf(manual))
        val viewModel = todayViewModel(
            repository,
            lifecycleRepository = FakeLifecycleRepository(purgeResult = purgeFailure()),
        )

        advanceUntilIdle()

        val tasks = (viewModel.uiState.value as TodayUiState.Content).tasks
        assertEquals(listOf(manual.item.id), tasks.map(TaskItem::id))
        assertTrue(viewModel.cleanupWarning.value)

        viewModel.toggleTask(manual.item.id)
        viewModel.deleteTask(manual.item.id)
        advanceUntilIdle()

        assertEquals(listOf(manual.item.id), repository.completionIds)
        assertEquals(listOf(manual.item.id), repository.deletedIds)
    }

    @Test
    fun `cleanup failure never exposes records the repository hides`() = runTest {
        val repository = FakeTriageRepository()
        val viewModel = todayViewModel(
            repository,
            lifecycleRepository = FakeLifecycleRepository(purgeResult = purgeFailure()),
        )

        advanceUntilIdle()

        assertEquals(TodayUiState.Empty, viewModel.uiState.value)
        assertTrue(viewModel.cleanupWarning.value)
        assertEquals(listOf(NOW), repository.queryTimes)
    }

    @Test
    fun `an unexpected cleanup failure warns without taking Today down`() = runTest {
        val manual = manualRecord()
        val repository = FakeTriageRepository(listOf(manual))
        val lifecycleRepository = FakeLifecycleRepository(
            purgeFailure = IllegalStateException("unexpected storage failure"),
        )
        val viewModel = todayViewModel(repository, lifecycleRepository = lifecycleRepository)

        advanceUntilIdle()

        assertTrue(viewModel.cleanupWarning.value)
        assertEquals(
            listOf(manual.item.id),
            (viewModel.uiState.value as TodayUiState.Content).tasks.map(TaskItem::id),
        )

        viewModel.toggleTask(manual.item.id)
        advanceUntilIdle()
        assertEquals(listOf(manual.item.id), repository.completionIds)

        lifecycleRepository.purgeFailure = null
        viewModel.retryCleanup()
        advanceUntilIdle()
        assertFalse(viewModel.cleanupWarning.value)
    }

    @Test
    fun `retrying cleanup after a failure clears the warning without reloading records`() =
        runTest {
            val lifecycleRepository = FakeLifecycleRepository(purgeResult = purgeFailure())
            val repository = FakeTriageRepository(listOf(manualRecord()))
            val viewModel = todayViewModel(repository, lifecycleRepository = lifecycleRepository)
            advanceUntilIdle()
            assertTrue(viewModel.cleanupWarning.value)

            lifecycleRepository.purgeResult = RepositoryResult.Success(1)
            viewModel.retryCleanup()
            advanceUntilIdle()

            assertFalse(viewModel.cleanupWarning.value)
            assertEquals(2, lifecycleRepository.purgeTimes.size)
            assertEquals(listOf(NOW), repository.queryTimes)
            assertTrue(viewModel.uiState.value is TodayUiState.Content)
        }

    @Test
    fun `reentering Today reruns cleanup and refreshes visibility`() = runTest {
        val lifecycleRepository = FakeLifecycleRepository(purgeResult = purgeFailure())
        val repository = FakeTriageRepository()
        val viewModel = todayViewModel(repository, lifecycleRepository = lifecycleRepository)
        advanceUntilIdle()
        assertTrue(viewModel.cleanupWarning.value)

        lifecycleRepository.purgeResult = RepositoryResult.Success(0)
        clock.advanceTo(NOW + 60_000)
        viewModel.onTodayEntered()
        advanceUntilIdle()

        assertEquals(TodayUiState.Empty, viewModel.uiState.value)
        assertFalse(viewModel.cleanupWarning.value)
        assertEquals(listOf(NOW, NOW + 60_000), lifecycleRepository.purgeTimes)
        assertEquals(listOf(NOW, NOW + 60_000), repository.queryTimes)
    }

    @Test
    fun `resuming Today rereads at the current time without clearing loaded records`() =
        runTest {
            val manual = manualRecord()
            val repository = FakeTriageRepository(
                resultFor = { now ->
                    if (now < EXPIRY) {
                        RepositoryResult.Success(
                            VisibleTriageRecords(listOf(manual, notificationRecord2()), null),
                        )
                    } else {
                        RepositoryResult.Success(VisibleTriageRecords(listOf(manual), null))
                    }
                },
            )
            val viewModel = todayViewModel(repository)
            advanceUntilIdle()
            assertEquals(2, (viewModel.uiState.value as TodayUiState.Content).tasks.size)

            // The device slept past the expiry; the resume boundary re-reads with the new time.
            clock.advanceTo(EXPIRY)
            viewModel.onTodayEntered()
            assertTrue(viewModel.uiState.value is TodayUiState.Content)
            advanceUntilIdle()

            assertEquals(listOf(NOW, EXPIRY), repository.queryTimes)
            assertEquals(
                listOf(manual.item.id),
                (viewModel.uiState.value as TodayUiState.Content).tasks.map(TaskItem::id),
            )
        }

    @Test
    fun `an item expiring while Today is visible refreshes once at its expiry`() = runTest {
        val expiring = notificationRecord()
        val repository = FakeTriageRepository(
            resultFor = { now ->
                if (now < EXPIRY) {
                    RepositoryResult.Success(VisibleTriageRecords(listOf(expiring), EXPIRY))
                } else {
                    RepositoryResult.Success(VisibleTriageRecords(emptyList(), null))
                }
            },
        )
        val viewModel = todayViewModel(repository)
        runCurrent()
        assertTrue(viewModel.uiState.value is TodayUiState.Content)

        // Virtual time is advanced with the injected clock; nothing polls in between.
        clock.advanceTo(EXPIRY - 1)
        advanceTimeBy(EXPIRY - 1 - NOW)
        runCurrent()
        assertEquals(listOf(NOW), repository.queryTimes)

        clock.advanceTo(EXPIRY)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(NOW, EXPIRY), repository.queryTimes)
        assertEquals(TodayUiState.Empty, viewModel.uiState.value)
    }

    private fun TestScope.todayViewModel(
        repository: TriageRepository,
        lifecycleRepository: NotificationDataLifecycleRepository = FakeLifecycleRepository(),
        enterToday: Boolean = true,
    ) = TodayViewModel(
        triageRepository = repository,
        cleanupCoordinator = NotificationDataCleanupCoordinator(
            lifecycleRepository = lifecycleRepository,
            clock = clock,
            applicationScope = CoroutineScope(
                SupervisorJob() + StandardTestDispatcher(testScheduler),
            ),
        ),
        clock = clock,
    ).also { viewModel ->
        if (enterToday) viewModel.onTodayEntered()
    }

    private fun purgeFailure() = RepositoryResult.Failure(
        operation = StorageOperation.PURGE_EXPIRED_NOTIFICATION_DATA,
        reason = StorageFailureReason.DATABASE,
        cause = null,
    )

    private fun manualRecord() = record(
        source = SourceReference.manual(),
        title = "Buy milk",
        explanation = "Added manually",
    )

    private fun notificationRecord2() = record(
        source = SourceReference.notification(
            packageName = "com.example.messages",
            appLabel = "Messages",
            stableKeyHash = "b".repeat(64),
        ),
        title = "Expiring notification",
        explanation = "Direct request",
        id = "item-2",
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
        id: String = "item-1",
    ): TriageRecord {
        val item = TriageItem(
            id = id,
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
                id = "decision-$id",
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
        initialResult: RepositoryResult<VisibleTriageRecords> = RepositoryResult.Success(
            VisibleTriageRecords(initialRecords, null),
        ),
        private val resultFor: (Long) -> RepositoryResult<VisibleTriageRecords> = { initialResult },
        private val createResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
        private val completionResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
        private val deleteResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
    ) : TriageRepository {
        val queryTimes = mutableListOf<Long>()
        val createdRecords = mutableListOf<TriageRecord>()
        val completionIds = mutableListOf<String>()
        val completionTimes = mutableListOf<Long?>()
        val deletedIds = mutableListOf<String>()

        override fun observeVisibleTriageRecords(
            nowEpochMillis: Long,
        ): Flow<RepositoryResult<VisibleTriageRecords>> {
            queryTimes += nowEpochMillis
            return MutableStateFlow(resultFor(nowEpochMillis))
        }

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
        var purgeFailure: RuntimeException? = null,
    ) : NotificationDataLifecycleRepository {
        val purgeTimes = mutableListOf<Long>()

        override suspend fun purgeExpiredNotificationData(
            nowEpochMillis: Long,
        ): RepositoryResult<Int> {
            purgeTimes += nowEpochMillis
            purgeFailure?.let { throw it }
            return purgeResult
        }

        override suspend fun deleteAllNotificationDataAndRules():
            RepositoryResult<NotificationDataDeletion> = throw AssertionError(
            "Today must never invoke delete-all as a cleanup fallback",
        )
    }

    private companion object {
        const val NOW = 1_700_000_000_000
        const val EXPIRY = NOW + 5_000
    }
}
