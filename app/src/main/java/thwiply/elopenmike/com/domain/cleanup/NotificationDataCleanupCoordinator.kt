package thwiply.elopenmike.com.domain.cleanup

import android.util.Log
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import thwiply.elopenmike.com.di.ApplicationScope
import thwiply.elopenmike.com.domain.triage.NotificationDataLifecycleRepository
import thwiply.elopenmike.com.domain.triage.RepositoryResult

/** Single logcat tag for every cleanup diagnostic; documented in the README. */
internal const val CLEANUP_LOG_TAG = "ThwiplyCleanup"

/** Where a cleanup run came from. Reported in diagnostics only. */
enum class NotificationCleanupTrigger {
    APP_STARTUP,
    TODAY_ENTRY,
    PERIODIC_MAINTENANCE,
}

/**
 * Single entry point for expired notification-data cleanup.
 *
 * Every trigger - application startup, Today entry, periodic maintenance, and any future
 * notification-ingestion boundary - calls [cleanUpExpiredNotificationData], so the retention
 * policy lives in exactly one place. Overlapping callers share the in-flight run, so a run
 * performs one retention delete transaction and never blocks manual reads or writes.
 */
@Singleton
class NotificationDataCleanupCoordinator @Inject constructor(
    private val lifecycleRepository: NotificationDataLifecycleRepository,
    private val clock: Clock,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val inFlightGuard = Mutex()
    private var inFlight: Deferred<RepositoryResult<Int>>? = null

    /**
     * Deletes notification-derived rows whose retention expired at or before now, and
     * reports the repository's own typed result: the deleted count, or the storage failure
     * with its original cause preserved for local debugging.
     *
     * Runs on [applicationScope], so a cancelled caller neither aborts an already started
     * delete nor turns into a success; the next call simply starts a new run.
     */
    suspend fun cleanUpExpiredNotificationData(
        trigger: NotificationCleanupTrigger,
    ): RepositoryResult<Int> {
        val run = inFlightGuard.withLock {
            inFlight?.takeIf { it.isActive }
                ?: applicationScope.async { purgeOnce(trigger) }.also { inFlight = it }
        }
        return run.await()
    }

    private suspend fun purgeOnce(
        trigger: NotificationCleanupTrigger,
    ): RepositoryResult<Int> {
        val result = lifecycleRepository.purgeExpiredNotificationData(clock.millis())
        when (result) {
            is RepositoryResult.Success -> Log.i(
                CLEANUP_LOG_TAG,
                "trigger=$trigger outcome=purged deleted=${result.value}",
            )

            is RepositoryResult.Failure -> Log.w(
                CLEANUP_LOG_TAG,
                "trigger=$trigger outcome=failed op=${result.operation} reason=${result.reason}",
            )
        }
        return result
    }
}
