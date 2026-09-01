package thwiply.elopenmike.com.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import thwiply.elopenmike.com.data.local.entity.TriageDecisionEntity
import thwiply.elopenmike.com.data.local.entity.TriageItemEntity
import thwiply.elopenmike.com.data.local.entity.UserCorrectionEntity
import thwiply.elopenmike.com.data.local.entity.UserRuleEntity

@RunWith(AndroidJUnit4::class)
class ThwiplyDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: ThwiplyDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE_NAME)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun triageCrudSurvivesDatabaseReopen() = runBlocking {
        val originalItem = TriageItemEntity(
            id = "item-1",
            displayTitle = "Buy milk",
            displaySummary = "Before dinner",
            sourceKind = "MANUAL",
            sourcePackageName = null,
            sourceAppLabel = "Manual",
            sourceStableKeyHash = null,
            isHighPriority = false,
            createdAtEpochMillis = 100,
            dueAtEpochMillis = null,
            completedAtEpochMillis = null,
        )
        val decision = TriageDecisionEntity(
            id = "decision-1",
            triageItemId = originalItem.id,
            category = "NOW",
            explanation = "Added manually",
            origin = "MANUAL",
            decidedAtEpochMillis = 100,
        )
        database.triageDao().insertTriageRecord(originalItem, decision)

        reopenDatabase()
        assertEquals(
            "Buy milk",
            database.triageDao().observeTriageRecords().first().single().item.displayTitle,
        )

        assertEquals(
            1,
            database.triageDao().updateTriageItem(
                originalItem.copy(displayTitle = "Buy oat milk"),
            ),
        )
        reopenDatabase()
        assertEquals(
            "Buy oat milk",
            database.triageDao().findTriageRecord(originalItem.id)?.item?.displayTitle,
        )

        assertEquals(
            1,
            database.triageDao().toggleTriageItemCompletion(
                triageItemId = originalItem.id,
                completedAtEpochMillis = 200,
            ),
        )
        reopenDatabase()
        assertEquals(
            200L,
            database.triageDao().findTriageRecord(originalItem.id)?.item?.completedAtEpochMillis,
        )

        assertEquals(1, database.triageDao().deleteTriageItem(originalItem.id))
        reopenDatabase()
        assertNull(database.triageDao().findTriageRecord(originalItem.id))
    }

    @Test
    fun rapidCompletionTogglesCancelEachOtherAtomically() = runBlocking {
        val item = manualItem(id = "toggle-item")
        database.triageDao().insertTriageRecord(
            item,
            testDecision(id = "toggle-decision", triageItemId = item.id),
        )

        val affectedRows = coroutineScope {
            listOf(
                async { database.triageDao().toggleTriageItemCompletion(item.id, 200) },
                async { database.triageDao().toggleTriageItemCompletion(item.id, 201) },
            ).awaitAll()
        }

        assertEquals(listOf(1, 1), affectedRows)
        reopenDatabase()
        assertNull(database.triageDao().findTriageRecord(item.id)?.item?.completedAtEpochMillis)
    }

    @Test
    fun failedDecisionInsertRollsBackItsTriageItem() {
        val item = manualItem(id = "rollback-item")
        val decisionWithMissingParent = testDecision(
            id = "rollback-decision",
            triageItemId = "missing-item",
        )

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.triageDao().insertTriageRecord(item, decisionWithMissingParent)
            }
        }

        runBlocking {
            assertNull(database.triageDao().findTriageRecord(item.id))
        }
    }

    @Test
    fun correctionAndRuleSurviveReopenAndItemDeleteCascadesCorrection() = runBlocking {
        val item = manualItem(id = "corrected-item")
        database.triageDao().insertTriageRecord(
            item,
            testDecision(id = "decision-2", triageItemId = item.id),
        )
        val rule = UserRuleEntity(
            id = "rule-1",
            packageName = "com.example.mail",
            channelId = "priority",
            action = "PRIORITIZE",
            isEnabled = true,
            createdAtEpochMillis = 200,
            updatedAtEpochMillis = 200,
        )
        database.userRuleDao().upsertRule(rule)
        database.correctionDao().insertCorrection(
            UserCorrectionEntity(
                id = "correction-1",
                triageItemId = item.id,
                previousCategory = "LATER",
                correctedCategory = "NOW",
                createdAtEpochMillis = 200,
                createdRuleId = rule.id,
            ),
        )

        reopenDatabase()
        assertEquals(
            "PRIORITIZE",
            database.userRuleDao().observeRules().first().single().action,
        )
        assertEquals(
            "correction-1",
            database.correctionDao().observeCorrections(item.id).first().single().id,
        )

        database.triageDao().deleteTriageItem(item.id)

        assertEquals(
            emptyList<UserCorrectionEntity>(),
            database.correctionDao().observeCorrections(item.id).first(),
        )
        assertEquals(1, database.userRuleDao().observeRules().first().size)
        assertEquals(1, database.userRuleDao().deleteRule(rule.id))
        assertEquals(emptyList<UserRuleEntity>(), database.userRuleDao().observeRules().first())
    }

    @Test
    fun retentionDeletesOnlyNotificationsAtOrBeforeTheCutoff() = runBlocking {
        val expired = notificationItem("expired", retentionExpiresAtEpochMillis = 199)
        val boundary = notificationItem("boundary", retentionExpiresAtEpochMillis = 200)
        val future = notificationItem("future", retentionExpiresAtEpochMillis = 201)
        val manual = manualItem("manual").copy(retentionExpiresAtEpochMillis = 1)
        listOf(expired, boundary, future, manual).forEachIndexed { index, item ->
            database.triageDao().insertTriageRecord(
                item,
                testDecision("retention-decision-$index", item.id),
            )
        }

        val deleted = database.dataLifecycleDao().deleteExpiredNotificationData(
            nowEpochMillis = 200,
        )

        assertEquals(2, deleted)
        assertNull(database.triageDao().findTriageRecord(expired.id))
        assertNull(database.triageDao().findTriageRecord(boundary.id))
        assertEquals(future.id, database.triageDao().findTriageRecord(future.id)?.item?.id)
        assertEquals(manual.id, database.triageDao().findTriageRecord(manual.id)?.item?.id)
    }

    @Test
    fun deleteAllNotificationDataAndRulesPreservesManualRecords() = runBlocking {
        val notification = notificationItem("notification", retentionExpiresAtEpochMillis = 500)
        val manual = manualItem("manual")
        database.triageDao().insertTriageRecord(
            notification,
            testDecision("notification-decision", notification.id),
        )
        database.triageDao().insertTriageRecord(
            manual,
            testDecision("manual-decision", manual.id),
        )
        val rule = UserRuleEntity(
            id = "delete-all-rule",
            packageName = "com.example.mail",
            channelId = null,
            action = "DEFER",
            isEnabled = true,
            createdAtEpochMillis = 200,
            updatedAtEpochMillis = 200,
        )
        database.userRuleDao().upsertRule(rule)
        database.correctionDao().insertCorrection(
            UserCorrectionEntity(
                id = "notification-correction",
                triageItemId = notification.id,
                previousCategory = "LATER",
                correctedCategory = "NOW",
                createdAtEpochMillis = 300,
                createdRuleId = rule.id,
            ),
        )
        database.correctionDao().insertCorrection(
            UserCorrectionEntity(
                id = "manual-correction",
                triageItemId = manual.id,
                previousCategory = "LATER",
                correctedCategory = "NOW",
                createdAtEpochMillis = 300,
                createdRuleId = rule.id,
            ),
        )

        val deletion = database.dataLifecycleDao().deleteAllNotificationDataAndRules()

        assertEquals(1, deletion.notificationItemsDeleted)
        assertEquals(1, deletion.rulesDeleted)
        assertNull(database.triageDao().findTriageRecord(notification.id))
        assertEquals(manual.id, database.triageDao().findTriageRecord(manual.id)?.item?.id)
        assertEquals(
            emptyList<UserCorrectionEntity>(),
            database.correctionDao().observeCorrections(notification.id).first(),
        )
        assertNull(
            database.correctionDao()
                .observeCorrections(manual.id)
                .first()
                .single()
                .createdRuleId,
        )
        assertEquals(emptyList<UserRuleEntity>(), database.userRuleDao().observeRules().first())
    }

    @Test
    fun durableSchemaContainsOnlyPrivacyReviewedColumns() {
        val expectedColumns = mapOf(
            "triage_items" to setOf(
                "id",
                "display_title",
                "display_summary",
                "source_kind",
                "source_package_name",
                "source_app_label",
                "source_stable_key_hash",
                "is_high_priority",
                "created_at_epoch_millis",
                "due_at_epoch_millis",
                "completed_at_epoch_millis",
                "retention_expires_at_epoch_millis",
            ),
            "triage_decisions" to setOf(
                "id",
                "triage_item_id",
                "category",
                "explanation",
                "origin",
                "decided_at_epoch_millis",
            ),
            "user_corrections" to setOf(
                "id",
                "triage_item_id",
                "previous_category",
                "corrected_category",
                "created_at_epoch_millis",
                "created_rule_id",
            ),
            "user_rules" to setOf(
                "id",
                "package_name",
                "channel_id",
                "action",
                "is_enabled",
                "created_at_epoch_millis",
                "updated_at_epoch_millis",
            ),
        )

        expectedColumns.forEach { (tableName, expected) ->
            val actual = buildSet {
                database.openHelper.readableDatabase
                    .query("PRAGMA table_info($tableName)")
                    .use { cursor ->
                        val nameIndex = cursor.getColumnIndexOrThrow("name")
                        while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                    }
            }
            assertEquals(expected, actual)
        }
    }

    private fun manualItem(id: String) = TriageItemEntity(
        id = id,
        displayTitle = "Manual item",
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

    private fun notificationItem(
        id: String,
        retentionExpiresAtEpochMillis: Long,
    ) = TriageItemEntity(
        id = id,
        displayTitle = "Notification item",
        displaySummary = null,
        sourceKind = "NOTIFICATION",
        sourcePackageName = "com.example.mail",
        sourceAppLabel = "Mail",
        sourceStableKeyHash = "a".repeat(64),
        isHighPriority = false,
        createdAtEpochMillis = 100,
        dueAtEpochMillis = null,
        completedAtEpochMillis = null,
        retentionExpiresAtEpochMillis = retentionExpiresAtEpochMillis,
    )

    private fun testDecision(
        id: String,
        triageItemId: String,
    ) = TriageDecisionEntity(
        id = id,
        triageItemId = triageItemId,
        category = "NOW",
        explanation = "Added manually",
        origin = "MANUAL",
        decidedAtEpochMillis = 100,
    )

    private fun openDatabase(): ThwiplyDatabase = Room.databaseBuilder(
        context,
        ThwiplyDatabase::class.java,
        TEST_DATABASE_NAME,
    ).build()

    private fun reopenDatabase() {
        database.close()
        database = openDatabase()
    }

    private companion object {
        const val TEST_DATABASE_NAME = "thwiply-database-test.db"
    }
}
