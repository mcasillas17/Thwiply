package thwiply.elopenmike.com.data.repository

import kotlinx.coroutines.flow.Flow
import thwiply.elopenmike.com.data.local.dao.CorrectionDao
import thwiply.elopenmike.com.domain.triage.CorrectionRepository
import thwiply.elopenmike.com.domain.triage.RepositoryResult
import thwiply.elopenmike.com.domain.triage.StorageOperation
import thwiply.elopenmike.com.domain.triage.UserCorrection
import javax.inject.Inject

class RoomCorrectionRepository @Inject constructor(
    private val correctionDao: CorrectionDao,
) : CorrectionRepository {
    override fun observeCorrections(
        triageItemId: String,
    ): Flow<RepositoryResult<List<UserCorrection>>> = observeStorage(
        operation = StorageOperation.OBSERVE_CORRECTIONS,
        source = correctionDao.observeCorrections(triageItemId),
    ) { corrections -> corrections.map { it.toDomain() } }

    override suspend fun createCorrection(
        correction: UserCorrection,
    ): RepositoryResult<Unit> = executeStorageOperation(StorageOperation.CREATE_CORRECTION) {
        correctionDao.insertCorrection(correction.toEntity())
    }
}
