package thwiply.elopenmike.com.data.cleanup

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import thwiply.elopenmike.com.data.local.MIGRATION_1_2
import thwiply.elopenmike.com.data.local.THWIPLY_DATABASE_NAME
import thwiply.elopenmike.com.data.local.ThwiplyDatabase
import thwiply.elopenmike.com.data.local.entity.TriageDecisionEntity
import thwiply.elopenmike.com.data.local.entity.TriageItemEntity

/**
 * Exercises the real platform scheduler and job service. Synthetic rows only: no notification
 * listener, model, or network is involved.
 */
@RunWith(AndroidJUnit4::class)
class NotificationMaintenanceSchedulerTest {
    private lateinit var context: Context
    private lateinit var jobScheduler: JobScheduler
    private lateinit var scheduler: NotificationMaintenanceScheduler

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        jobScheduler = context.getSystemService(JobScheduler::class.java)
        jobScheduler.cancel(NotificationMaintenanceScheduler.MAINTENANCE_JOB_ID)
        jobScheduler.cancel(ONE_SHOT_JOB_ID)
        scheduler = NotificationMaintenanceScheduler(context)
    }

    @After
    fun tearDown() {
        jobScheduler.cancel(ONE_SHOT_JOB_ID)
        withAppDatabase { database ->
            runBlocking { database.triageDao().deleteTriageItem(EXPIRED_ITEM_ID) }
        }
    }

    @Test
    fun repeatedRegistrationKeepsOneDailyLocalJob() {
        // The application registers this id at startup too, so assert on the resulting job
        // instead of on which caller won the race: repeated calls add nothing and reset nothing.
        scheduler.ensureScheduled()
        val job = jobScheduler.allPendingJobs
            .single { it.id == NotificationMaintenanceScheduler.MAINTENANCE_JOB_ID }
        assertFalse(scheduler.ensureScheduled())
        assertFalse(scheduler.ensureScheduled())

        assertEquals(
            1,
            jobScheduler.allPendingJobs
                .count { it.id == NotificationMaintenanceScheduler.MAINTENANCE_JOB_ID },
        )
        assertTrue(job.isPeriodic)
        assertEquals(TimeUnit.DAYS.toMillis(1), job.intervalMillis)
        assertEquals(
            NotificationMaintenanceJobService::class.java.name,
            job.service.className,
        )
        // Best effort and local: no network, charging, idle, or persistence requirements.
        assertNull(job.requiredNetwork)
        assertFalse(job.isRequireCharging)
        assertFalse(job.isRequireDeviceIdle)
        assertFalse(job.isPersisted)
    }

    @Test
    fun aMaintenanceRunPurgesExpiredDataAndReportsItselfFinished() {
        withAppDatabase { database ->
            runBlocking {
                database.triageDao().insertTriageRecord(expiredItem, expiredDecision)
                assertNotNull(database.triageDao().findTriageRecord(EXPIRED_ITEM_ID))
            }
        }

        // The platform supplies JobParameters and drives the real service.
        val immediate = JobInfo.Builder(
            ONE_SHOT_JOB_ID,
            ComponentName(context, NotificationMaintenanceJobService::class.java),
        )
            .setOverrideDeadline(0)
            .build()
        assertEquals(JobScheduler.RESULT_SUCCESS, jobScheduler.schedule(immediate))

        val deadline = SystemClock.uptimeMillis() + FINISH_TIMEOUT_MILLIS
        while (jobScheduler.getPendingJob(ONE_SHOT_JOB_ID) != null &&
            SystemClock.uptimeMillis() < deadline
        ) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }

        // A one-shot job leaves the scheduler only after the service reports it finished.
        assertNull(jobScheduler.getPendingJob(ONE_SHOT_JOB_ID))
        withAppDatabase { database ->
            runBlocking { assertNull(database.triageDao().findTriageRecord(EXPIRED_ITEM_ID)) }
        }
    }

    /** Opens a second connection to the database the running application already owns. */
    private fun <T> withAppDatabase(block: (ThwiplyDatabase) -> T): T {
        val database = Room.databaseBuilder(
            context,
            ThwiplyDatabase::class.java,
            THWIPLY_DATABASE_NAME,
        )
            .addMigrations(MIGRATION_1_2)
            .build()
        return try {
            block(database)
        } finally {
            database.close()
        }
    }

    private val expiredItem = TriageItemEntity(
        id = EXPIRED_ITEM_ID,
        displayTitle = "Synthetic expired notification",
        displaySummary = null,
        sourceKind = "NOTIFICATION",
        sourcePackageName = "com.example.messages",
        sourceAppLabel = "Messages",
        sourceStableKeyHash = "a".repeat(64),
        isHighPriority = false,
        createdAtEpochMillis = 100,
        dueAtEpochMillis = null,
        completedAtEpochMillis = null,
        retentionExpiresAtEpochMillis = 200,
    )

    private val expiredDecision = TriageDecisionEntity(
        id = "$EXPIRED_ITEM_ID-decision",
        triageItemId = EXPIRED_ITEM_ID,
        category = "NOW",
        explanation = "Synthetic decision",
        origin = "MANUAL",
        decidedAtEpochMillis = 100,
    )

    private companion object {
        const val ONE_SHOT_JOB_ID = 191_299
        const val EXPIRED_ITEM_ID = "maintenance-expired-notification"
        const val FINISH_TIMEOUT_MILLIS = 30_000L
        const val POLL_INTERVAL_MILLIS = 100L
    }
}
