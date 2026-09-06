package thwiply.elopenmike.com.data.cleanup

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import thwiply.elopenmike.com.di.ApplicationScope
import thwiply.elopenmike.com.domain.cleanup.CLEANUP_LOG_TAG
import thwiply.elopenmike.com.domain.cleanup.NotificationCleanupTrigger
import thwiply.elopenmike.com.domain.cleanup.NotificationDataCleanupCoordinator

/**
 * Registers the one best-effort daily maintenance job.
 *
 * The platform [JobScheduler] is the smallest mechanism that fits: no extra dependency, no
 * custom initializer, and Android already deduplicates by job id. Android decides when a
 * deferrable job actually runs, so this is eventual physical deletion, not a deadline.
 */
@Singleton
class NotificationMaintenanceScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Returns true when this call registered the periodic job, false when one was already
     * pending. Repeated launches therefore never enqueue a duplicate or restart the interval.
     */
    fun ensureScheduled(): Boolean {
        val jobScheduler = context.getSystemService(JobScheduler::class.java)
        if (jobScheduler == null) {
            Log.w(CLEANUP_LOG_TAG, "operation=schedule_maintenance outcome=unavailable")
            return false
        }
        if (jobScheduler.getPendingJob(MAINTENANCE_JOB_ID) != null) return false
        val maintenance = JobInfo.Builder(
            MAINTENANCE_JOB_ID,
            ComponentName(context, NotificationMaintenanceJobService::class.java),
        )
            .setPeriodic(MAINTENANCE_INTERVAL_MILLIS)
            .build()
        val scheduled = jobScheduler.schedule(maintenance) == JobScheduler.RESULT_SUCCESS
        if (!scheduled) {
            Log.w(CLEANUP_LOG_TAG, "operation=schedule_maintenance outcome=rejected")
        }
        return scheduled
    }

    companion object {
        /** Stable, app-unique job id for notification-data maintenance. */
        const val MAINTENANCE_JOB_ID = 1912

        /** At most one maintenance attempt per day. */
        val MAINTENANCE_INTERVAL_MILLIS: Long = TimeUnit.DAYS.toMillis(1)
    }
}

/**
 * Runs one local retention cleanup per maintenance window. No network, no model work, and no
 * retry on failure: the next daily window is the retry.
 */
@AndroidEntryPoint
class NotificationMaintenanceJobService : JobService() {
    @Inject
    lateinit var cleanupCoordinator: NotificationDataCleanupCoordinator

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    // One service instance hosts every job routed to it, so runs are tracked per job id.
    private val runs = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        runs[params.jobId] = applicationScope.launch {
            try {
                cleanupCoordinator.cleanUpExpiredNotificationData(
                    NotificationCleanupTrigger.PERIODIC_MAINTENANCE,
                )
            } finally {
                runs.remove(params.jobId)
                // Report completion even after a failure, so the job never holds its
                // wakelock until the platform timeout. onStopJob owns the cancelled case.
                if (isActive) jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runs.remove(params.jobId)?.cancel()
        return false
    }
}
