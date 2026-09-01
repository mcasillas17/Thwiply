package thwiply.elopenmike.com.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import thwiply.elopenmike.com.domain.triage.NotificationDataDeletion

@Dao
interface DataLifecycleDao {
    @Query(
        """
        DELETE FROM triage_items
        WHERE source_kind = 'NOTIFICATION'
          AND retention_expires_at_epoch_millis IS NOT NULL
          AND retention_expires_at_epoch_millis <= :nowEpochMillis
        """,
    )
    suspend fun deleteExpiredNotificationData(nowEpochMillis: Long): Int

    @Query("DELETE FROM triage_items WHERE source_kind = 'NOTIFICATION'")
    suspend fun deleteAllNotificationItems(): Int

    @Query("DELETE FROM user_rules")
    suspend fun deleteAllRules(): Int

    @Transaction
    suspend fun deleteAllNotificationDataAndRules(): NotificationDataDeletion {
        val notificationItemsDeleted = deleteAllNotificationItems()
        val rulesDeleted = deleteAllRules()
        return NotificationDataDeletion(
            notificationItemsDeleted = notificationItemsDeleted,
            rulesDeleted = rulesDeleted,
        )
    }
}
