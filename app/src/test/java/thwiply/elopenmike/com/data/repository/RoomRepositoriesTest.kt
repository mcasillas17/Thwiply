package thwiply.elopenmike.com.data.repository

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import thwiply.elopenmike.com.data.local.dao.TriageDao
import thwiply.elopenmike.com.data.local.dao.CorrectionDao
import thwiply.elopenmike.com.data.local.dao.DataLifecycleDao
import thwiply.elopenmike.com.data.local.dao.UserRuleDao
import thwiply.elopenmike.com.data.local.entity.TriageDecisionEntity
import thwiply.elopenmike.com.data.local.entity.TriageItemEntity
import thwiply.elopenmike.com.data.local.entity.TriageItemWithDecision
import thwiply.elopenmike.com.data.local.entity.UserCorrectionEntity
import thwiply.elopenmike.com.data.local.entity.UserRuleEntity
import thwiply.elopenmike.com.domain.triage.DecisionOrigin
import thwiply.elopenmike.com.domain.triage.NotificationDataDeletion
import thwiply.elopenmike.com.domain.triage.RepositoryResult
import thwiply.elopenmike.com.domain.triage.RuleAction
import thwiply.elopenmike.com.domain.triage.SourceKind
import thwiply.elopenmike.com.domain.triage.SourceReference
import thwiply.elopenmike.com.domain.triage.StorageFailureReason
import thwiply.elopenmike.com.domain.triage.StorageOperation
import thwiply.elopenmike.com.domain.triage.TriageCategory
import thwiply.elopenmike.com.domain.triage.TriageDecision
import thwiply.elopenmike.com.domain.triage.TriageItem
import thwiply.elopenmike.com.domain.triage.TriageRecord

class RoomRepositoriesTest {
    @Test
    fun `triage observer maps durable rows to validated domain records`() = runBlocking {
        val dao = FakeTriageDao(records = flowOf(listOf(entityRecord())))
        val repository = RoomTriageRepository(dao)

        val result = repository.observeTriageRecords().first()

        val record = (result as RepositoryResult.Success).value.single()
        assertEquals("Buy milk", record.item.displayTitle)
        assertEquals(SourceKind.MANUAL, record.item.source.kind)
        assertEquals(TriageCategory.NOW, record.decision.category)
    }

    @Test
    fun `updating a missing triage item returns a typed not found failure`() = runBlocking {
        val repository = RoomTriageRepository(FakeTriageDao(updateCount = 0))

        val result = repository.updateTriageItem(domainRecord().item)

        result as RepositoryResult.Failure
        assertEquals(StorageOperation.UPDATE_TRIAGE, result.operation)
        assertEquals(StorageFailureReason.NOT_FOUND, result.reason)
        assertEquals(null, result.cause)
    }

    @Test
    fun `sqlite write failure retains its cause`() = runBlocking {
        val sqliteFailure = SQLiteException("database unavailable")
        val repository = RoomTriageRepository(
            FakeTriageDao(insertFailure = sqliteFailure),
        )

        val result = repository.createTriageRecord(domainRecord())

        result as RepositoryResult.Failure
        assertEquals(StorageOperation.CREATE_TRIAGE, result.operation)
        assertEquals(StorageFailureReason.DATABASE, result.reason)
        assertSame(sqliteFailure, result.cause)
    }

    @Test
    fun `unknown write failure is not translated`() {
        val unknownFailure = IllegalStateException("unexpected mapper failure")
        val repository = RoomTriageRepository(
            FakeTriageDao(insertFailure = unknownFailure),
        )

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.createTriageRecord(domainRecord()) }
        }

        assertSame(unknownFailure, thrown)
    }

    @Test
    fun `correction observer maps correction history`() = runBlocking {
        val correction = UserCorrectionEntity(
            id = "correction-1",
            triageItemId = "item-1",
            previousCategory = "LATER",
            correctedCategory = "NOW",
            createdAtEpochMillis = 200,
            createdRuleId = null,
        )
        val repository = RoomCorrectionRepository(
            FakeCorrectionDao(flowOf(listOf(correction))),
        )

        val result = repository.observeCorrections("item-1").first()

        val mapped = (result as RepositoryResult.Success).value.single()
        assertEquals(TriageCategory.LATER, mapped.previousCategory)
        assertEquals(TriageCategory.NOW, mapped.correctedCategory)
    }

    @Test
    fun `rule delete reports a missing rule`() = runBlocking {
        val repository = RoomUserRuleRepository(FakeUserRuleDao(deleteCount = 0))

        val result = repository.deleteRule("missing-rule")

        result as RepositoryResult.Failure
        assertEquals(StorageOperation.DELETE_RULE, result.operation)
        assertEquals(StorageFailureReason.NOT_FOUND, result.reason)
    }

    @Test
    fun `rule observer maps durable rule values`() = runBlocking {
        val rule = UserRuleEntity(
            id = "rule-1",
            packageName = "com.example.mail",
            channelId = "priority",
            action = "PRIORITIZE",
            isEnabled = true,
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 200,
        )
        val repository = RoomUserRuleRepository(
            FakeUserRuleDao(rules = flowOf(listOf(rule))),
        )

        val result = repository.observeRules().first()

        val mapped = (result as RepositoryResult.Success).value.single()
        assertEquals(RuleAction.PRIORITIZE, mapped.action)
        assertEquals("priority", mapped.channelId)
    }

    @Test
    fun `retention purge returns the exact number of deleted notification items`() = runBlocking {
        val repository = RoomNotificationDataLifecycleRepository(
            FakeDataLifecycleDao(expiredDeleteCount = 3),
        )

        val result = repository.purgeExpiredNotificationData(nowEpochMillis = 500)

        assertEquals(3, (result as RepositoryResult.Success).value)
    }

    @Test
    fun `delete all sqlite failure is explicit and retains its cause`() = runBlocking {
        val sqliteFailure = SQLiteException("delete unavailable")
        val repository = RoomNotificationDataLifecycleRepository(
            FakeDataLifecycleDao(deleteAllFailure = sqliteFailure),
        )

        val result = repository.deleteAllNotificationDataAndRules()

        result as RepositoryResult.Failure
        assertEquals(StorageOperation.DELETE_ALL_NOTIFICATION_DATA_AND_RULES, result.operation)
        assertEquals(StorageFailureReason.DATABASE, result.reason)
        assertSame(sqliteFailure, result.cause)
    }

    private fun domainRecord(): TriageRecord {
        val item = TriageItem(
            id = "item-1",
            displayTitle = "Buy milk",
            displaySummary = null,
            source = SourceReference.manual(),
            isHighPriority = false,
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
                explanation = "Added manually",
                origin = DecisionOrigin.MANUAL,
                decidedAtEpochMillis = 100,
            ),
        )
    }

    private fun entityRecord() = TriageItemWithDecision(
        item = TriageItemEntity(
            id = "item-1",
            displayTitle = "Buy milk",
            displaySummary = null,
            sourceKind = "MANUAL",
            sourcePackageName = null,
            sourceAppLabel = "Manual",
            sourceStableKeyHash = null,
            isHighPriority = false,
            createdAtEpochMillis = 100,
            dueAtEpochMillis = null,
            completedAtEpochMillis = null,
        ),
        decision = TriageDecisionEntity(
            id = "decision-1",
            triageItemId = "item-1",
            category = "NOW",
            explanation = "Added manually",
            origin = "MANUAL",
            decidedAtEpochMillis = 100,
        ),
    )

    private class FakeTriageDao(
        private val records: Flow<List<TriageItemWithDecision>> = flowOf(emptyList()),
        private val updateCount: Int = 1,
        private val insertFailure: RuntimeException? = null,
    ) : TriageDao {
        override suspend fun insertTriageItem(item: TriageItemEntity) = Unit

        override suspend fun insertTriageDecision(decision: TriageDecisionEntity) = Unit

        override suspend fun insertTriageRecord(
            item: TriageItemEntity,
            decision: TriageDecisionEntity,
        ) {
            insertFailure?.let { throw it }
        }

        override fun observeTriageRecords(): Flow<List<TriageItemWithDecision>> = records

        override suspend fun findTriageRecord(triageItemId: String): TriageItemWithDecision? = null

        override suspend fun updateTriageItem(item: TriageItemEntity): Int = updateCount

        override suspend fun setCompletedAt(
            triageItemId: String,
            completedAtEpochMillis: Long?,
        ): Int = updateCount

        override suspend fun deleteTriageItem(triageItemId: String): Int = updateCount
    }

    private class FakeCorrectionDao(
        private val corrections: Flow<List<UserCorrectionEntity>>,
    ) : CorrectionDao {
        override fun observeCorrections(
            triageItemId: String,
        ): Flow<List<UserCorrectionEntity>> = corrections

        override suspend fun insertCorrection(correction: UserCorrectionEntity) = Unit
    }

    private class FakeUserRuleDao(
        private val rules: Flow<List<UserRuleEntity>> = flowOf(emptyList()),
        private val deleteCount: Int = 1,
    ) : UserRuleDao {
        override fun observeRules(): Flow<List<UserRuleEntity>> = rules

        override suspend fun upsertRule(rule: UserRuleEntity) = Unit

        override suspend fun deleteRule(ruleId: String): Int = deleteCount
    }

    private class FakeDataLifecycleDao(
        private val expiredDeleteCount: Int = 0,
        private val deleteAllFailure: SQLiteException? = null,
    ) : DataLifecycleDao {
        override suspend fun deleteExpiredNotificationData(nowEpochMillis: Long): Int =
            expiredDeleteCount

        override suspend fun deleteAllNotificationItems(): Int = 1

        override suspend fun deleteAllRules(): Int = 1

        override suspend fun deleteAllNotificationDataAndRules(): NotificationDataDeletion {
            deleteAllFailure?.let { throw it }
            return NotificationDataDeletion(
                notificationItemsDeleted = deleteAllNotificationItems(),
                rulesDeleted = deleteAllRules(),
            )
        }
    }
}
