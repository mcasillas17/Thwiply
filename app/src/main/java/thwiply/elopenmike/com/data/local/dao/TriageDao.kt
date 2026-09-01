package thwiply.elopenmike.com.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import thwiply.elopenmike.com.data.local.entity.TriageDecisionEntity
import thwiply.elopenmike.com.data.local.entity.TriageItemEntity
import thwiply.elopenmike.com.data.local.entity.TriageItemWithDecision

@Dao
interface TriageDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTriageItem(item: TriageItemEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTriageDecision(decision: TriageDecisionEntity)

    @Transaction
    suspend fun insertTriageRecord(
        item: TriageItemEntity,
        decision: TriageDecisionEntity,
    ) {
        insertTriageItem(item)
        insertTriageDecision(decision)
    }

    @Transaction
    @Query("SELECT * FROM triage_items ORDER BY created_at_epoch_millis DESC, id ASC")
    fun observeTriageRecords(): Flow<List<TriageItemWithDecision>>

    @Transaction
    @Query("SELECT * FROM triage_items WHERE id = :triageItemId")
    suspend fun findTriageRecord(triageItemId: String): TriageItemWithDecision?

    @Update
    suspend fun updateTriageItem(item: TriageItemEntity): Int

    @Query(
        """
        UPDATE triage_items
        SET completed_at_epoch_millis = :completedAtEpochMillis
        WHERE id = :triageItemId
        """,
    )
    suspend fun setCompletedAt(
        triageItemId: String,
        completedAtEpochMillis: Long?,
    ): Int

    @Query("DELETE FROM triage_items WHERE id = :triageItemId")
    suspend fun deleteTriageItem(triageItemId: String): Int
}
