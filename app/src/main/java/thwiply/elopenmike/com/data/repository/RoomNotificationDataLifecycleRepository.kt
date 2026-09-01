package thwiply.elopenmike.com.data.repository

import thwiply.elopenmike.com.data.local.dao.DataLifecycleDao
import thwiply.elopenmike.com.domain.triage.NotificationDataDeletion
import thwiply.elopenmike.com.domain.triage.NotificationDataLifecycleRepository
import thwiply.elopenmike.com.domain.triage.RepositoryResult
import thwiply.elopenmike.com.domain.triage.StorageOperation
import javax.inject.Inject

class RoomNotificationDataLifecycleRepository @Inject constructor(
    private val dataLifecycleDao: DataLifecycleDao,
) : NotificationDataLifecycleRepository {
    override suspend fun purgeExpiredNotificationData(
        nowEpochMillis: Long,
    ): RepositoryResult<Int> = executeStorageOperation(
        operation = StorageOperation.PURGE_EXPIRED_NOTIFICATION_DATA,
    ) {
        dataLifecycleDao.deleteExpiredNotificationData(nowEpochMillis)
    }

    override suspend fun deleteAllNotificationDataAndRules():
        RepositoryResult<NotificationDataDeletion> = executeStorageOperation(
        operation = StorageOperation.DELETE_ALL_NOTIFICATION_DATA_AND_RULES,
    ) {
        dataLifecycleDao.deleteAllNotificationDataAndRules()
    }
}
