package thwiply.elopenmike.com.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import thwiply.elopenmike.com.data.local.entity.UserCorrectionEntity

@Dao
interface CorrectionDao {
    @Query(
        """
        SELECT * FROM user_corrections
        WHERE triage_item_id = :triageItemId
        ORDER BY created_at_epoch_millis ASC, id ASC
        """,
    )
    fun observeCorrections(triageItemId: String): Flow<List<UserCorrectionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCorrection(correction: UserCorrectionEntity)
}
