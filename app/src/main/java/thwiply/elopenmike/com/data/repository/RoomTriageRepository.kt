package thwiply.elopenmike.com.data.repository

import kotlinx.coroutines.flow.Flow
import thwiply.elopenmike.com.data.local.dao.TriageDao
import thwiply.elopenmike.com.domain.triage.RepositoryResult
import thwiply.elopenmike.com.domain.triage.StorageOperation
import thwiply.elopenmike.com.domain.triage.TriageItem
import thwiply.elopenmike.com.domain.triage.TriageRecord
import thwiply.elopenmike.com.domain.triage.TriageRepository
import javax.inject.Inject

class RoomTriageRepository @Inject constructor(
    private val triageDao: TriageDao,
) : TriageRepository {
    override fun observeTriageRecords(): Flow<RepositoryResult<List<TriageRecord>>> =
        observeStorage(
            operation = StorageOperation.OBSERVE_TRIAGE,
            source = triageDao.observeTriageRecords(),
        ) { records -> records.map { it.toDomain() } }

    override suspend fun createTriageRecord(
        record: TriageRecord,
    ): RepositoryResult<Unit> = executeStorageOperation(StorageOperation.CREATE_TRIAGE) {
        triageDao.insertTriageRecord(
            item = record.item.toEntity(),
            decision = record.decision.toEntity(),
        )
    }

    override suspend fun updateTriageItem(item: TriageItem): RepositoryResult<Unit> {
        var updatedRows = 0
        val result = executeStorageOperation(StorageOperation.UPDATE_TRIAGE) {
            updatedRows = triageDao.updateTriageItem(item.toEntity())
        }
        return if (result is RepositoryResult.Success && updatedRows == 0) {
            missingRecord(StorageOperation.UPDATE_TRIAGE)
        } else {
            result
        }
    }

    override suspend fun setTriageItemCompleted(
        triageItemId: String,
        completedAtEpochMillis: Long?,
    ): RepositoryResult<Unit> {
        var updatedRows = 0
        val result = executeStorageOperation(StorageOperation.SET_TRIAGE_COMPLETION) {
            updatedRows = triageDao.setCompletedAt(triageItemId, completedAtEpochMillis)
        }
        return if (result is RepositoryResult.Success && updatedRows == 0) {
            missingRecord(StorageOperation.SET_TRIAGE_COMPLETION)
        } else {
            result
        }
    }

    override suspend fun deleteTriageItem(triageItemId: String): RepositoryResult<Unit> {
        var deletedRows = 0
        val result = executeStorageOperation(StorageOperation.DELETE_TRIAGE) {
            deletedRows = triageDao.deleteTriageItem(triageItemId)
        }
        return if (result is RepositoryResult.Success && deletedRows == 0) {
            missingRecord(StorageOperation.DELETE_TRIAGE)
        } else {
            result
        }
    }
}
