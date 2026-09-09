package thwiply.elopenmike.com.data.cleanup

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import thwiply.elopenmike.com.R
import thwiply.elopenmike.com.data.local.ThwiplyDatabase
import thwiply.elopenmike.com.data.local.entity.TriageDecisionEntity
import thwiply.elopenmike.com.data.local.entity.TriageItemEntity
import thwiply.elopenmike.com.data.repository.RoomNotificationDataLifecycleRepository
import thwiply.elopenmike.com.data.repository.RoomTriageRepository
import thwiply.elopenmike.com.domain.cleanup.NotificationDataCleanupCoordinator
import thwiply.elopenmike.com.domain.triage.NotificationDataDeletion
import thwiply.elopenmike.com.domain.triage.NotificationDataLifecycleRepository
import thwiply.elopenmike.com.domain.triage.RepositoryResult
import thwiply.elopenmike.com.domain.triage.StorageFailureReason
import thwiply.elopenmike.com.domain.triage.StorageOperation
import thwiply.elopenmike.com.ui.today.TodayScreen
import thwiply.elopenmike.com.ui.today.TodayViewModel
import thwiply.elopenmike.com.ui.today.TodayUiState
import thwiply.elopenmike.com.ui.theme.ThwiplyTheme
import thwiply.elopenmike.com.data.preferences.ThemeMode
import thwiply.elopenmike.com.ui.main.AppViewport
import androidx.activity.enableEdgeToEdge
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

/**
 * Real Compose Today over real Room with an injected retention-delete failure. Synthetic rows
 * only; no notification listener, model, or network is involved.
 */
@RunWith(AndroidJUnit4::class)
class TodayCleanupFailureTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: ThwiplyDatabase
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        applicationScope.cancel()
        if (::database.isInitialized) database.close()
    }

    @Test
    fun purgeFailureKeepsManualWorkUsableAndStillHidesExpiredNotifications() {
        database = Room.inMemoryDatabaseBuilder(
            compose.activity,
            ThwiplyDatabase::class.java,
        ).build()
        runBlocking {
            insert(manualItem)
            insert(expiredNotificationItem)
        }
        val lifecycle = FailableLifecycleRepository(
            RoomNotificationDataLifecycleRepository(database.dataLifecycleDao()),
        )
        val viewModel = TodayViewModel(
            RoomTriageRepository(database.triageDao()),
            NotificationDataCleanupCoordinator(lifecycle, Clock.systemUTC(), applicationScope),
            Clock.systemUTC(),
        )
        val args = InstrumentationRegistry.getArguments()
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            compose.activity.window.attributes = compose.activity.window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        compose.setContent {
            ThwiplyTheme(if (args.getString("fnd04Theme") == "dark") ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppViewport { TodayScreen(viewModel) }
                }
            }
        }

        awaitAndScrollToTask(viewModel, manualItem.displayTitle).assertIsDisplayed()
        // The expired notification row survives the failed delete but is never rendered.
        assertEquals(
            false,
            (viewModel.uiState.value as? TodayUiState.Content)?.tasks?.any {
                it.id == expiredNotificationItem.id
            },
        )
        assertEquals(
            0,
            compose.onAllNodesWithText(expiredNotificationItem.displayTitle)
                .fetchSemanticsNodes()
                .size,
        )
        runBlocking {
            assertEquals(
                expiredNotificationItem.id,
                database.triageDao().findTriageRecord(expiredNotificationItem.id)?.item?.id,
            )
        }
        compose.waitUntil(10_000) { viewModel.cleanupWarning.value }
        scrollToText(warning())
            .assertIsDisplayed()
            // The announcement lives on the node that carries the message text.
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )

        android.util.Log.i("FND04Evidence", "READY scenario=today-cleanup-warning")
        val holdSeconds = args.getString("fnd04HoldSeconds")?.toLongOrNull()?.coerceIn(0, 60) ?: 0
        if (holdSeconds > 0) Thread.sleep(holdSeconds * 1000)

        // Manual work stays usable while the warning is showing.
        compose.onNodeWithContentDescription("Add task").performClick()
        compose.onNodeWithText("Task description").performTextInput("Synthetic cleanup task")
        compose.onNodeWithText("Add Task").performScrollTo().assertIsDisplayed().performClick()
        awaitAndScrollToTask(viewModel, "Synthetic cleanup task").assertIsDisplayed()

        lifecycle.failPurge = false
        scrollToText(compose.activity.getString(R.string.today_cleanup_retry))
            .performClick()

        compose.waitUntil(10_000) { !viewModel.cleanupWarning.value }
        assertEquals(0, compose.onAllNodesWithText(warning()).fetchSemanticsNodes().size)
        scrollToText(manualItem.displayTitle).assertIsDisplayed()
        runBlocking {
            assertNull(database.triageDao().findTriageRecord(expiredNotificationItem.id))
        }
    }

    private fun warning() = compose.activity.getString(R.string.today_cleanup_warning)

    private fun awaitAndScrollToTask(viewModel: TodayViewModel, title: String) = compose.run {
        waitUntil(10_000) {
            (viewModel.uiState.value as? TodayUiState.Content)?.tasks?.any { it.title == title } == true
        }
        scrollToText(title)
    }

    private fun scrollToText(value: String) = compose.run {
        onNodeWithTag("today-scroll").performScrollToNode(hasText(value))
        onNodeWithText(value)
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
        id = "manual-item",
        displayTitle = "Synthetic manual task",
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

    private val expiredNotificationItem = TriageItemEntity(
        id = "expired-notification-item",
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

    private class FailableLifecycleRepository(
        private val delegate: NotificationDataLifecycleRepository,
        var failPurge: Boolean = true,
    ) : NotificationDataLifecycleRepository {
        override suspend fun purgeExpiredNotificationData(
            nowEpochMillis: Long,
        ): RepositoryResult<Int> = if (failPurge) {
            RepositoryResult.Failure(
                operation = StorageOperation.PURGE_EXPIRED_NOTIFICATION_DATA,
                reason = StorageFailureReason.DATABASE,
                cause = null,
            )
        } else {
            delegate.purgeExpiredNotificationData(nowEpochMillis)
        }

        override suspend fun deleteAllNotificationDataAndRules():
            RepositoryResult<NotificationDataDeletion> = throw AssertionError(
            "expiry cleanup must never fall back to delete-all",
        )
    }
}
