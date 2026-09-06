package thwiply.elopenmike.com.domain.cleanup

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import thwiply.elopenmike.com.domain.triage.NotificationDataDeletion
import thwiply.elopenmike.com.domain.triage.NotificationDataLifecycleRepository
import thwiply.elopenmike.com.domain.triage.RepositoryResult
import thwiply.elopenmike.com.domain.triage.StorageFailureReason
import thwiply.elopenmike.com.domain.triage.StorageOperation
import thwiply.elopenmike.com.testing.MutableClock

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationDataCleanupCoordinatorTest {

    @Test
    fun `cleanup purges as of the injected clock and reports the deleted count`() = runTest {
        val repository = FakeLifecycleRepository(purgeResult = RepositoryResult.Success(2))
        val coordinator = coordinator(repository)

        val outcome = coordinator.cleanUpExpiredNotificationData(
            NotificationCleanupTrigger.TODAY_ENTRY,
        )

        assertEquals(listOf(NOW), repository.purgeTimes)
        assertEquals(RepositoryResult.Success(2), outcome)
    }

    @Test
    fun `every trigger reuses the same single purge policy`() = runTest {
        val repository = FakeLifecycleRepository()
        val coordinator = coordinator(repository)

        NotificationCleanupTrigger.entries.forEach { trigger ->
            coordinator.cleanUpExpiredNotificationData(trigger)
        }

        assertEquals(
            List(NotificationCleanupTrigger.entries.size) { NOW },
            repository.purgeTimes,
        )
    }

    @Test
    fun `overlapping cleanup requests share one delete transaction`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeLifecycleRepository(
            purgeResult = RepositoryResult.Success(3),
            gate = gate,
        )
        val coordinator = coordinator(repository)

        val startup = async {
            coordinator.cleanUpExpiredNotificationData(NotificationCleanupTrigger.APP_STARTUP)
        }
        val todayEntry = async {
            coordinator.cleanUpExpiredNotificationData(NotificationCleanupTrigger.TODAY_ENTRY)
        }
        runCurrent()
        gate.complete(Unit)

        val expected = RepositoryResult.Success(3)
        assertEquals(expected, startup.await())
        assertEquals(expected, todayEntry.await())
        assertEquals(listOf(NOW), repository.purgeTimes)
    }

    @Test
    fun `purge failure is reported as a typed failure instead of a success default`() = runTest {
        val cause = SQLiteException("database unavailable")
        val repository = FakeLifecycleRepository(
            purgeResult = RepositoryResult.Failure(
                operation = StorageOperation.PURGE_EXPIRED_NOTIFICATION_DATA,
                reason = StorageFailureReason.DATABASE,
                cause = cause,
            ),
        )
        val coordinator = coordinator(repository)

        val outcome = coordinator.cleanUpExpiredNotificationData(
            NotificationCleanupTrigger.APP_STARTUP,
        )

        outcome as RepositoryResult.Failure
        assertEquals(StorageOperation.PURGE_EXPIRED_NOTIFICATION_DATA, outcome.operation)
        assertEquals(StorageFailureReason.DATABASE, outcome.reason)
        assertSame(cause, outcome.cause)
    }

    @Test
    fun `a transient failure does not leave cleanup permanently stuck`() = runTest {
        val repository = FakeLifecycleRepository(
            purgeResult = RepositoryResult.Failure(
                operation = StorageOperation.PURGE_EXPIRED_NOTIFICATION_DATA,
                reason = StorageFailureReason.DATABASE,
                cause = null,
            ),
        )
        val coordinator = coordinator(repository)
        val failed = coordinator.cleanUpExpiredNotificationData(
            NotificationCleanupTrigger.APP_STARTUP,
        )
        assertTrue(failed is RepositoryResult.Failure)

        repository.purgeResult = RepositoryResult.Success(1)
        val recovered = coordinator.cleanUpExpiredNotificationData(
            NotificationCleanupTrigger.TODAY_ENTRY,
        )

        assertEquals(
            RepositoryResult.Success(1),
            recovered,
        )
        assertEquals(listOf(NOW, NOW), repository.purgeTimes)
    }

    @Test
    fun `cancelling one caller never reports success and leaves cleanup usable`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeLifecycleRepository(gate = gate)
        val coordinator = coordinator(repository)
        val caller = launch {
            coordinator.cleanUpExpiredNotificationData(NotificationCleanupTrigger.APP_STARTUP)
        }
        runCurrent()

        caller.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(caller.isCancelled)
        assertEquals(listOf(NOW), repository.purgeTimes)
        assertEquals(
            RepositoryResult.Success(0),
            coordinator.cleanUpExpiredNotificationData(NotificationCleanupTrigger.TODAY_ENTRY),
        )
        assertEquals(listOf(NOW, NOW), repository.purgeTimes)
    }

    @Test
    fun `an unexpected storage failure surfaces instead of becoming a cleanup outcome`() =
        runTest {
            val unexpected = IllegalStateException("unexpected storage failure")
            val repository = FakeLifecycleRepository(purgeFailure = unexpected)
            val coordinator = coordinator(repository)

            var thrown: Throwable? = null
            try {
                coordinator.cleanUpExpiredNotificationData(
                    NotificationCleanupTrigger.APP_STARTUP,
                )
            } catch (failure: IllegalStateException) {
                thrown = failure
            }

            // Coroutine stack-trace recovery may hand back a copy that keeps the original
            // as its cause; either way the failure is never translated or swallowed.
            assertSame(unexpected, thrown?.cause ?: thrown)
            // The supervised application scope survives, so cleanup is not stuck.
            repository.purgeFailure = null
            assertEquals(
                RepositoryResult.Success(0),
                coordinator.cleanUpExpiredNotificationData(
                    NotificationCleanupTrigger.TODAY_ENTRY,
                ),
            )
        }

    /**
     * The real coordinator owns an application-lifetime scope. Tests drive it with the test
     * scheduler so cleanup runs are foreground work the test can advance deterministically.
     */
    private fun TestScope.coordinator(
        repository: NotificationDataLifecycleRepository,
    ) = NotificationDataCleanupCoordinator(
        lifecycleRepository = repository,
        clock = MutableClock(NOW),
        applicationScope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler),
        ),
    )

    private class FakeLifecycleRepository(
        var purgeResult: RepositoryResult<Int> = RepositoryResult.Success(0),
        var purgeFailure: RuntimeException? = null,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : NotificationDataLifecycleRepository {
        val purgeTimes = mutableListOf<Long>()

        override suspend fun purgeExpiredNotificationData(
            nowEpochMillis: Long,
        ): RepositoryResult<Int> {
            purgeTimes += nowEpochMillis
            gate?.await()
            purgeFailure?.let { throw it }
            return purgeResult
        }

        override suspend fun deleteAllNotificationDataAndRules():
            RepositoryResult<NotificationDataDeletion> = throw AssertionError(
            "expiry cleanup must never fall back to delete-all",
        )
    }

    private companion object {
        const val NOW = 1_700_000_000_000
    }
}
