package thwiply.elopenmike.com.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import thwiply.elopenmike.com.data.local.entity.UserRuleEntity

@Dao
interface UserRuleDao {
    @Query("SELECT * FROM user_rules ORDER BY updated_at_epoch_millis DESC, id ASC")
    fun observeRules(): Flow<List<UserRuleEntity>>

    @Upsert
    suspend fun upsertRule(rule: UserRuleEntity)

    @Query("DELETE FROM user_rules WHERE id = :ruleId")
    suspend fun deleteRule(ruleId: String): Int
}
