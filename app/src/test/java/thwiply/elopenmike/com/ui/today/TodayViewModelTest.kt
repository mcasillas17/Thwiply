package thwiply.elopenmike.com.ui.today

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
                            VisibleTriageRecords(listOf(manual, notificationRecord2())),
                        )
                    } else {
                        RepositoryResult.Success(VisibleTriageRecords(listOf(manual)))
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
                    RepositoryResult.Success(
                        VisibleTriageRecords(listOf(expiring), mapOf(expiring.item.id to EXPIRY)),
                    )
                } else {
                    RepositoryResult.Success(VisibleTriageRecords(emptyList()))
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

    @Test
    fun `a screen that never collects starts no room observation`() = runTest {
        val repository = FakeTriageRepository()

        todayViewModel(repository, collectState = false)
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), repository.queryTimes)
        assertEquals(0, repository.activeSubscriptions)
    }

    @Test
    fun `two collectors share a single room observation`() = runTest {
        val repository = FakeTriageRepository(listOf(manualRecord()))
        val viewModel = todayViewModel(repository)

        collectUiState(viewModel)
        advanceUntilIdle()

        assertEquals(1, repository.activeSubscriptions)
        assertEquals(1, repository.peakSubscriptions)
        assertEquals(listOf(NOW), repository.queryTimes)
    }

    @Test
    fun `stopping the last collector cancels the room observation`() = runTest {
        val repository = FakeTriageRepository(listOf(manualRecord()))
        val viewModel = todayViewModel(repository, collectState = false)
        val collector = collectUiState(viewModel)
        viewModel.onTodayEntered()
        advanceUntilIdle()
        assertEquals(1, repository.activeSubscriptions)

        collector.cancel()
        advanceTimeBy(TodayViewModel.SUBSCRIPTION_STOP_TIMEOUT_MS + 1)
        runCurrent()

        assertEquals(0, repository.activeSubscriptions)
    }

    @Test
    fun `a stopped screen keeps no expiry timer alive`() = runTest {
        // The expiry lands after the stop timeout, so the timer is cancelled, never raced.
        val lateExpiry = NOW + TodayViewModel.SUBSCRIPTION_STOP_TIMEOUT_MS * 2
        val repository = FakeTriageRepository(
            resultFor = { now ->
                if (now < lateExpiry) {
                    RepositoryResult.Success(
                        VisibleTriageRecords(
                            listOf(notificationRecord()),
                            mapOf(notificationRecord().item.id to lateExpiry),
                        ),
                    )
                } else {
                    RepositoryResult.Success(VisibleTriageRecords(emptyList()))
                }
            },
        )
        val viewModel = todayViewModel(repository, collectState = false)
        val collector = collectUiState(viewModel)
        viewModel.onTodayEntered()
        runCurrent()
        assertEquals(listOf(NOW), repository.queryTimes)

        collector.cancel()
        advanceTimeBy(TodayViewModel.SUBSCRIPTION_STOP_TIMEOUT_MS + 1)
        runCurrent()
        clock.advanceTo(lateExpiry + 1)
        advanceTimeBy(lateExpiry + 1 - NOW)
        runCurrent()

        // The timer belonged to the stopped screen, so no refresh read was issued.
        assertEquals(listOf(NOW), repository.queryTimes)
        assertEquals(0, repository.activeSubscriptions)
    }

    @Test
    fun `a collector returning inside the grace period keeps the same observation`() = runTest {
        val repository = FakeTriageRepository(listOf(manualRecord()))
        val viewModel = todayViewModel(repository, enterToday = false, collectState = false)
        val collector = collectUiState(viewModel)
        runCurrent()
        assertEquals(listOf(NOW), repository.queryTimes)

        // A recreation or tab change drops the collector for less than the grace period.
        collector.cancel()
        advanceTimeBy(TodayViewModel.SUBSCRIPTION_STOP_TIMEOUT_MS / 2)
        runCurrent()
        clock.advanceTo(NOW + 120_000)
        collectUiState(viewModel)
        runCurrent()

        // The observation was never released, so it is not reopened or re-read either.
        assertEquals(listOf(NOW), repository.queryTimes)
        assertEquals(1, repository.activeSubscriptions)
        assertEquals(1, repository.peakSubscriptions)
    }

    @Test
    fun `restarting then resuming reads at each boundary through one observation`() = runTest {
        val repository = FakeTriageRepository(listOf(manualRecord()))
        val viewModel = todayViewModel(repository, collectState = false)
        val collector = collectUiState(viewModel)
        viewModel.onTodayEntered()
        runCurrent()

        collector.cancel()
        advanceTimeBy(TodayViewModel.SUBSCRIPTION_STOP_TIMEOUT_MS + 1)
        runCurrent()
        // Production order and timing: STARTED restarts collection first, RESUMED calls back
        // a moment later, so the two boundaries carry different clock readings.
        clock.advanceTo(NOW + 120_000)
        collectUiState(viewModel)
        runCurrent()
        clock.advanceTo(NOW + 120_010)
        viewModel.onTodayEntered()
        runCurrent()

        // Each boundary reads at its own current time - Today entry always refreshes
        // visibility - and neither opens a second observation.
        assertEquals(listOf(NOW, NOW + 120_000, NOW + 120_010), repository.queryTimes)
        assertEquals(1, repository.activeSubscriptions)
        assertEquals(1, repository.peakSubscriptions)
    }

    @Test
    fun `a restarted subscription reads at the current time before any resume callback`() =
        runTest {
            val repository = FakeTriageRepository(listOf(manualRecord()))
            val viewModel = todayViewModel(repository, enterToday = false, collectState = false)
            val collector = collectUiState(viewModel)
            runCurrent()
            assertEquals(listOf(NOW), repository.queryTimes)

            collector.cancel()
            advanceTimeBy(TodayViewModel.SUBSCRIPTION_STOP_TIMEOUT_MS + 1)
            runCurrent()
            clock.advanceTo(NOW + 120_000)
            collectUiState(viewModel)
            runCurrent()

            // No onTodayEntered() here: starting to collect is itself a visibility boundary.
            assertEquals(listOf(NOW, NOW + 120_000), repository.queryTimes)
        }

    @Test
    fun `a subscription restarted after an expiry reloads instead of replaying its snapshot`() =
        runTest {
            val manual = manualRecord()
            val repository = FakeTriageRepository(
                flowFor = { now ->
                    if (now < EXPIRY) {
                        MutableStateFlow(
                            RepositoryResult.Success(
                                VisibleTriageRecords(
                                    listOf(manual, notificationRecord2()),
                                    mapOf(notificationRecord2().item.id to EXPIRY),
                                ),
                            ),
                        )
                    } else {
                        // A real Room re-read is not instantaneous.
                        flow {
                            delay(50)
                            emit(
                                RepositoryResult.Success(
                                    VisibleTriageRecords(listOf(manual)),
                                ),
                            )
                        }
                    }
                },
            )
            val viewModel = todayViewModel(repository, enterToday = false, collectState = false)
            val collector = collectUiState(viewModel)
            runCurrent()
            assertEquals(2, (viewModel.uiState.value as TodayUiState.Content).tasks.size)

            collector.cancel()
            advanceTimeBy(TodayViewModel.SUBSCRIPTION_STOP_TIMEOUT_MS + 1)
            runCurrent()
            // The device slept past the expiry while nothing was collecting.
            clock.advanceTo(EXPIRY)
            collectUiState(viewModel)
            runCurrent()

            // Nothing observed the old snapshot any more, so it is reloaded, never replayed.
            assertEquals(TodayUiState.Loading, viewModel.uiState.value)

            advanceTimeBy(50)
            runCurrent()
            assertEquals(
                listOf(manual.item.id),
                (viewModel.uiState.value as TodayUiState.Content).tasks.map(TaskItem::id),
            )
        }

    @Test
    fun `a released snapshot is never replayed after the data behind it changed`() = runTest {
        val manual = manualRecord()
        val notification = notificationRecord2()
        var visible = listOf(manual, notification)
        val repository = FakeTriageRepository(resultFor = { RepositoryResult.Success(VisibleTriageRecords(visible)) })
        val viewModel = todayViewModel(repository, enterToday = false, collectState = false)
        val collector = collectUiState(viewModel)
        runCurrent()
        assertEquals(2, (viewModel.uiState.value as TodayUiState.Content).tasks.size)

        // Today leaves composition, its observation closes, and Settings deletes the
        // notification data while nothing is observing.
        collector.cancel()
        advanceTimeBy(TodayViewModel.SUBSCRIPTION_STOP_TIMEOUT_MS + 1)
        runCurrent()
        visible = listOf(manual)

        // Returning must not replay a snapshot that no observation stood behind.
        assertEquals(TodayUiState.Loading, viewModel.uiState.value)
        collectUiState(viewModel)
        runCurrent()
        assertEquals(
            listOf(manual.item.id),
            (viewModel.uiState.value as TodayUiState.Content).tasks.map(TaskItem::id),
        )
    }

    @Test
    fun `a stale snapshot drops only the notification row that actually expired`() = runTest {
        val manual = manualRecord()
        val early = notificationRecord(id = "item-3")
        val late = notificationRecord2()
        val lateExpiry = EXPIRY + 60_000
        val repository = FakeTriageRepository(
            flowFor = { now ->
                if (now < EXPIRY) {
                    MutableStateFlow(
                        RepositoryResult.Success(
                            VisibleTriageRecords(
                                listOf(manual, early, late),
                                mapOf(early.item.id to EXPIRY, late.item.id to lateExpiry),
                            ),
                        ),
                    )
                } else {
                    // A real Room re-read is not instantaneous.
                    flow {
                        delay(50)
                        emit(
                            RepositoryResult.Success(
                                VisibleTriageRecords(
                                    listOf(manual, late),
                                    mapOf(late.item.id to lateExpiry),
                                ),
                            ),
                        )
                    }
                }
            },
        )
        val viewModel = todayViewModel(repository, enterToday = false)
        runCurrent()
        assertEquals(3, (viewModel.uiState.value as TodayUiState.Content).tasks.size)

        clock.advanceTo(EXPIRY)
        advanceTimeBy(EXPIRY - NOW)
        runCurrent()

        // Only the row past its own retention is hidden; the later one keeps rendering.
        assertEquals(
            listOf(manual.item.id, late.item.id),
            (viewModel.uiState.value as TodayUiState.Content).tasks.map(TaskItem::id),
        )
    }

    @Test
    fun `a snapshot read before an expiry never renders the expired notification row`() = runTest {
        val manual = manualRecord()
        val repository = FakeTriageRepository(
            flowFor = { now ->
                if (now < EXPIRY) {
                    MutableStateFlow(
                        RepositoryResult.Success(
                            VisibleTriageRecords(
                                listOf(manual, notificationRecord2()),
                                mapOf(notificationRecord2().item.id to EXPIRY),
                            ),
                        ),
                    )
                } else {
                    // A real Room re-read is not instantaneous; the stale snapshot must not show.
                    flow {
                        delay(50)
                        emit(RepositoryResult.Success(VisibleTriageRecords(listOf(manual))))
                    }
                }
            },
        )
        val viewModel = todayViewModel(repository)
        runCurrent()
        assertEquals(2, (viewModel.uiState.value as TodayUiState.Content).tasks.size)

        clock.advanceTo(EXPIRY)
        advanceTimeBy(EXPIRY - NOW)
        runCurrent()

        assertEquals(
            listOf(manual.item.id),
            (viewModel.uiState.value as TodayUiState.Content).tasks.map(TaskItem::id),
        )
    }

    @Test
    fun `cleanup started by Today survives the screen it was started from`() = runTest {
        val lifecycleRepository = FakeLifecycleRepository()
        val repository = FakeTriageRepository()
        val viewModel = todayViewModel(repository, lifecycleRepository, collectState = false)
        val collector = collectUiState(viewModel)
        viewModel.onTodayEntered()

        collector.cancel()
        advanceUntilIdle()

        assertEquals(listOf(NOW), lifecycleRepository.purgeTimes)
    }

    private fun TestScope.todayViewModel(
        repository: TriageRepository,
        lifecycleRepository: NotificationDataLifecycleRepository = FakeLifecycleRepository(),
        enterToday: Boolean = true,
        collectState: Boolean = true,
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
        // A started screen collects; the collector, not the entry call, owns the subscription.
        if (collectState) collectUiState(viewModel)
        if (enterToday) viewModel.onTodayEntered()
    }

    private fun TestScope.collectUiState(viewModel: TodayViewModel) =
        backgroundScope.launch { viewModel.uiState.collect {} }

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

    private fun notificationRecord(id: String = "item-1") = record(
        source = SourceReference.notification(
            packageName = "com.example.messages",
            appLabel = "Messages",
            stableKeyHash = "a".repeat(64),
        ),
        title = "Call Alex",
        explanation = "Direct request",
        id = id,
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
            VisibleTriageRecords(initialRecords),
        ),
        private val resultFor: (Long) -> RepositoryResult<VisibleTriageRecords> = { initialResult },
        private val flowFor: (Long) -> Flow<RepositoryResult<VisibleTriageRecords>> = { now ->
            MutableStateFlow(resultFor(now))
        },
        private val createResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
        private val completionResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
        private val deleteResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
    ) : TriageRepository {
        val queryTimes = mutableListOf<Long>()

        /** Room observations currently collected, and the most ever collected at once. */
        var activeSubscriptions = 0
            private set
        var peakSubscriptions = 0
            private set
        val createdRecords = mutableListOf<TriageRecord>()
        val completionIds = mutableListOf<String>()
        val completionTimes = mutableListOf<Long?>()
        val deletedIds = mutableListOf<String>()

        override fun observeVisibleTriageRecords(
            nowEpochMillis: Long,
        ): Flow<RepositoryResult<VisibleTriageRecords>> = flowFor(nowEpochMillis)
            .onStart {
                queryTimes += nowEpochMillis
                activeSubscriptions += 1
                peakSubscriptions = maxOf(peakSubscriptions, activeSubscriptions)
            }
            .onCompletion { activeSubscriptions -= 1 }

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
