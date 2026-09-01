package thwiply.elopenmike.com.domain.triage

import kotlinx.coroutines.flow.Flow

interface TriageRepository {
    fun observeTriageRecords(): Flow<RepositoryResult<List<TriageRecord>>>

    suspend fun createTriageRecord(record: TriageRecord): RepositoryResult<Unit>

    suspend fun updateTriageItem(item: TriageItem): RepositoryResult<Unit>

    suspend fun toggleTriageItemCompletion(
        triageItemId: String,
        completedAtEpochMillis: Long,
    ): RepositoryResult<Unit>

    suspend fun deleteTriageItem(triageItemId: String): RepositoryResult<Unit>
}
