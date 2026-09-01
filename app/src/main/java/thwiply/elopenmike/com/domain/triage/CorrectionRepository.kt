package thwiply.elopenmike.com.domain.triage

import kotlinx.coroutines.flow.Flow

interface CorrectionRepository {
    fun observeCorrections(
        triageItemId: String,
    ): Flow<RepositoryResult<List<UserCorrection>>>

    suspend fun createCorrection(correction: UserCorrection): RepositoryResult<Unit>
}
