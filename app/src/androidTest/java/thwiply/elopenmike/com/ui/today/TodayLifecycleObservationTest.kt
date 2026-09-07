package thwiply.elopenmike.com.ui.today

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import thwiply.elopenmike.com.data.local.ThwiplyDatabase
import thwiply.elopenmike.com.data.local.entity.TriageDecisionEntity
import thwiply.elopenmike.com.data.local.entity.TriageItemEntity
import thwiply.elopenmike.com.data.repository.RoomTriageRepository
import thwiply.elopenmike.com.domain.cleanup.NotificationDataCleanupCoordinator
import thwiply.elopenmike.com.domain.triage.NotificationDataDeletion
import thwiply.elopenmike.com.domain.triage.NotificationDataLifecycleRepository
import thwiply.elopenmike.com.domain.triage.RepositoryResult
import thwiply.elopenmike.com.domain.triage.TriageRepository
import thwiply.elopenmike.com.domain.triage.VisibleTriageRecords

/**
 * Real Compose Today over real Room, driven by real activity lifecycle transitions. The
 * repository is wrapped in a counting decorator so the test asserts how many Room
 * observations are actually open, not merely which collection API the screen calls.
 *
 * Retention deletes are stubbed out here on purpose: the point is that a resumed screen
 * re-reads at the current time and hides an expired notification row that is still stored.
 */
@RunWith(AndroidJUnit4::class)
class TodayLifecycleObservationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = MutableClock(BASE_EPOCH_MILLIS)
    private lateinit var database: ThwiplyDatabase
    private lateinit var repository: CountingTriageRepository
    private lateinit var viewModel: TodayViewModel

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            compose.activity,
            ThwiplyDatabase::class.java,
        ).build()
        runBlocking {
            insert(manualItem)
            insert(expiringNotificationItem)
        }
        repository = CountingTriageRepository(RoomTriageRepository(database.triageDao()))
        viewModel = TodayViewModel(
            repository,
            NotificationDataCleanupCoordinator(NeverDeletingRepository, clock, applicationScope),
            clock,
        )
    }

    @After
    fun tearDown() {
        // Destroy the screen and let the shared subscription unwind before the database goes
        // away; closing Room underneath a live observation is a test artifact, not behavior.
        if (::repository.isInitialized) {
            compose.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
            val deadline = System.nanoTime() + TIMEOUT_MILLIS * 1_000_000
            while (repository.activeSubscriptions > 0 && System.nanoTime() < deadline) {
                Thread.sleep(50)
            }
        }
        applicationScope.cancel()
        if (::database.isInitialized) database.close()
    }

    @Test
    fun stoppingTheActivityReleasesTodayObservationAndResumingRereadsAtTheCurrentTime() {
        compose.setContent { TodayScreen(viewModel) }
        awaitText(manualItem.displayTitle)
        awaitText(expiringNotificationItem.displayTitle)
        assertEquals(1, repository.activeSubscriptions)

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        awaitCondition("stopped Today kept a Room observation open") {
            repository.activeSubscriptions == 0
        }
        val readsWhileStopped = repository.queryTimes.size

        // The device slept past the notification's retention expiry while Today was stopped.
        clock.advanceTo(BASE_EPOCH_MILLIS + 120_000)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        awaitCondition("resumed Today did not restart its observation") {
            repository.activeSubscriptions == 1
        }
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(expiringNotificationItem.displayTitle)
                .fetchSemanticsNodes()
                .isEmpty()
        }
        compose.onNodeWithText(manualItem.displayTitle).assertIsDisplayed()
        assertEquals(1, repository.peakSubscriptions)
        assertTrue(
            "resume must re-read with the current time, got ${repository.queryTimes}",
            repository.queryTimes.size > readsWhileStopped &&
                repository.queryTimes.last() >= BASE_EPOCH_MILLIS + 120_000,
        )
        // Hidden by the time-scoped read, not by a delete.
        runBlocking {
            assertNotNull(
                database.triageDao().findTriageRecord(expiringNotificationItem.id),
            )
        }
    }

    @Test
    fun restartingCollectionAtStartedRereadsAtTheCurrentTimeAndNeverShowsTheExpiredRow() {
        compose.setContent { TodayScreen(viewModel) }
        awaitText(expiringNotificationItem.displayTitle)

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        awaitCondition("stopped Today kept a Room observation open") {
            repository.activeSubscriptions == 0
        }
        val readsWhileStopped = repository.queryTimes.size
        clock.advanceTo(BASE_EPOCH_MILLIS + 120_000)

        // STARTED, not RESUMED: collection restarts before Today's resume callback runs, so
        // starting to collect must itself re-read at the current time.
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)

        val deadline = System.nanoTime() + TIMEOUT_MILLIS * 1_000_000
        var reread = false
        while (System.nanoTime() < deadline) {
            val rendered = (viewModel.uiState.value as? TodayUiState.Content)?.tasks.orEmpty()
            assertTrue(
                "expired notification row was exposed after restarting collection",
                rendered.none { it.id == expiringNotificationItem.id },
            )
            reread = repository.queryTimes.size > readsWhileStopped
            if (reread && rendered.any { it.id == manualItem.id }) break
            Thread.sleep(10)
        }
        assertTrue("restarting collection issued no read", reread)
        assertTrue(
            "reads taken after the restart must use the current time, got ${repository.queryTimes}",
            repository.queryTimes.drop(readsWhileStopped)
                .all { it >= BASE_EPOCH_MILLIS + 120_000 },
        )

        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(manualItem.displayTitle).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            0,
            compose.onAllNodesWithText(expiringNotificationItem.displayTitle)
                .fetchSemanticsNodes()
                .size,
        )
        assertEquals(1, repository.peakSubscriptions)
    }

    @Test
    fun overlappingAndDepartingTodayCollectorsNeverOpenASecondObservation() {
        var screens by mutableStateOf(1)
        compose.setContent { repeat(screens) { TodayScreen(viewModel) } }
        awaitComposedCondition("Today never opened its observation") {
            repository.activeSubscriptions == 1
        }

        // Two collectors briefly overlap the way an animated tab change composes both sides.
        compose.runOnUiThread { screens = 2 }
        compose.waitForIdle()
        assertEquals(1, repository.activeSubscriptions)
        assertEquals(1, repository.peakSubscriptions)

        // Navigated away: nothing collects, so the Room observation must close.
        compose.runOnUiThread { screens = 0 }
        awaitComposedCondition("Today kept observing after leaving composition") {
            repository.activeSubscriptions == 0
        }

        // Navigated back: exactly one observation again, never a second.
        compose.runOnUiThread { screens = 1 }
        awaitComposedCondition("returning to Today did not restart its observation") {
            repository.activeSubscriptions == 1
        }
        awaitText(manualItem.displayTitle)
        assertEquals(1, repository.peakSubscriptions)
    }

    @Test
    fun aReleasedObservationIsNeverReplayedAfterTheNotificationDataIsDeleted() {
        var visible by mutableStateOf(true)
        compose.setContent { if (visible) TodayScreen(viewModel) }
        awaitText(manualItem.displayTitle)
        awaitText(expiringNotificationItem.displayTitle)

        // Today leaves composition the way a tab change does; its observation closes.
        compose.runOnUiThread { visible = false }
        awaitComposedCondition("Today kept observing after leaving composition") {
            repository.activeSubscriptions == 0
        }

        // Settings deletes the notification data while nothing is observing Today.
        runBlocking { database.dataLifecycleDao().deleteAllNotificationDataAndRules() }
        compose.runOnUiThread { visible = true }

        // From the instant Today returns, the deleted row must never be part of what it
        // renders - a snapshot no observation stood behind is reloaded, never replayed.
        // The condition runs on every poll while the Compose clock advances, so the
        // assertion covers the whole reload window rather than only its end.
        awaitComposedCondition("Today never reloaded after returning") {
            val rendered = (viewModel.uiState.value as? TodayUiState.Content)?.tasks.orEmpty()
            assertTrue(
                "deleted notification row was replayed after returning to Today",
                rendered.none { it.id == expiringNotificationItem.id },
            )
            rendered.any { it.id == manualItem.id }
        }
        awaitText(manualItem.displayTitle)
        assertEquals(
            0,
            compose.onAllNodesWithText(expiringNotificationItem.displayTitle)
                .fetchSemanticsNodes()
                .size,
        )
    }

    private fun awaitText(value: String) = compose.run {
        waitUntil(TIMEOUT_MILLIS) {
            onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(value)
    }

    /**
     * Polls while pumping the Compose clock, for conditions that need a recomposition first
     * (a screen only leaves composition once the frame that removes it has run).
     */
    private fun awaitComposedCondition(message: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(TIMEOUT_MILLIS, condition)
        } catch (timeout: ComposeTimeoutException) {
            fail("$message (${timeout.message})")
        }
    }

    /**
     * Polls off the main thread. The subscription counters are plain state, and part of this
     * test runs while the activity is stopped and Compose is not drawing.
     */
    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TIMEOUT_MILLIS * 1_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail(message)
    }

    private suspend fun insert(item: TriageItemEntity) {
        database.triageDao().insertTriageRecord(
            item,
            TriageDecisionEntity(
                id = "${item.id}-decision",
                triageItemId = item.id,
                category = "NOW",
                explanation = "Synthetic decision",
                origin = "MANUAL",
                decidedAtEpochMillis = 100,
            ),
        )
    }

    private val manualItem = TriageItemEntity(
        id = "lifecycle-manual-item",
        displayTitle = "Synthetic lifecycle manual task",
        displaySummary = null,
        sourceKind = "MANUAL",
        sourcePackageName = null,
        sourceAppLabel = "Manual",
        sourceStableKeyHash = null,
        isHighPriority = false,
        createdAtEpochMillis = 100,
        dueAtEpochMillis = null,
        completedAtEpochMillis = null,
    )

    private val expiringNotificationItem = TriageItemEntity(
        id = "lifecycle-notification-item",
        displayTitle = "Synthetic lifecycle notification",
        displaySummary = null,
        sourceKind = "NOTIFICATION",
        sourcePackageName = "com.example.messages",
        sourceAppLabel = "Messages",
        sourceStableKeyHash = "a".repeat(64),
        isHighPriority = false,
        createdAtEpochMillis = 100,
        dueAtEpochMillis = null,
        completedAtEpochMillis = null,
        retentionExpiresAtEpochMillis = BASE_EPOCH_MILLIS + 60_000,
    )

    /** Counts open Room observations and the cutoff each one was opened with. */
    private class CountingTriageRepository(
        private val delegate: TriageRepository,
    ) : TriageRepository by delegate {
        @Volatile
        var activeSubscriptions = 0
            private set

        @Volatile
        var peakSubscriptions = 0
            private set
        val queryTimes = java.util.concurrent.CopyOnWriteArrayList<Long>()

        override fun observeVisibleTriageRecords(
            nowEpochMillis: Long,
        ): Flow<RepositoryResult<VisibleTriageRecords>> =
            delegate.observeVisibleTriageRecords(nowEpochMillis)
                .onStart {
                    queryTimes += nowEpochMillis
                    synchronized(this@CountingTriageRepository) {
                        activeSubscriptions += 1
                        peakSubscriptions = maxOf(peakSubscriptions, activeSubscriptions)
                    }
                }
                .onCompletion {
                    synchronized(this@CountingTriageRepository) { activeSubscriptions -= 1 }
                }
    }

    private object NeverDeletingRepository : NotificationDataLifecycleRepository {
        override suspend fun purgeExpiredNotificationData(
            nowEpochMillis: Long,
        ): RepositoryResult<Int> = RepositoryResult.Success(0)

        override suspend fun deleteAllNotificationDataAndRules():
            RepositoryResult<NotificationDataDeletion> = throw AssertionError(
            "expiry cleanup must never fall back to delete-all",
        )
    }

    private class MutableClock(epochMillis: Long) : Clock() {
        @Volatile
        private var current = Instant.ofEpochMilli(epochMillis)

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advanceTo(epochMillis: Long) {
            current = Instant.ofEpochMilli(epochMillis)
        }
    }

    private companion object {
        const val BASE_EPOCH_MILLIS = 1_800_000_000_000
        const val TIMEOUT_MILLIS = 20_000L
    }
}
