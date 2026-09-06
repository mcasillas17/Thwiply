package thwiply.elopenmike.com.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import thwiply.elopenmike.com.domain.triage.NotificationDataDeletion

@Dao
interface DataLifecycleDao {
    /**
     * Deletes notification-derived rows that are past retention. A missing expiry is unknown
     * retention, which the read filter already hides, so it is deleted rather than kept
     * forever. Manual rows and rules are never touched here.
     */
    @Query(
        """
        DELETE FROM triage_items
        WHERE source_kind = 'NOTIFICATION'
          AND (retention_expires_at_epoch_millis IS NULL
               OR retention_expires_at_epoch_millis <= :nowEpochMillis)
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
