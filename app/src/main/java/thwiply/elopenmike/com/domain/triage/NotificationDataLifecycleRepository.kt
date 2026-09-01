package thwiply.elopenmike.com.domain.triage

data class NotificationDataDeletion(
    val notificationItemsDeleted: Int,
    val rulesDeleted: Int,
)

interface NotificationDataLifecycleRepository {
    suspend fun purgeExpiredNotificationData(
        nowEpochMillis: Long,
    ): RepositoryResult<Int>

    suspend fun deleteAllNotificationDataAndRules(): RepositoryResult<NotificationDataDeletion>
}
