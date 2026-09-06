package thwiply.elopenmike.com.domain.triage

import kotlinx.coroutines.flow.Flow

/**
 * Records that may be shown as of a given time, plus the earliest notification expiry still
 * ahead of that time. A caller uses [nextExpiryAtEpochMillis] to refresh once when a visible
 * record expires, instead of polling.
 */
data class VisibleTriageRecords(
    val records: List<TriageRecord>,
    val nextExpiryAtEpochMillis: Long?,
)

interface TriageRepository {
    fun observeVisibleTriageRecords(
        nowEpochMillis: Long,
    ): Flow<RepositoryResult<VisibleTriageRecords>>

    suspend fun createTriageRecord(record: TriageRecord): RepositoryResult<Unit>

    suspend fun updateTriageItem(item: TriageItem): RepositoryResult<Unit>

    suspend fun toggleTriageItemCompletion(
        triageItemId: String,
        completedAtEpochMillis: Long,
    ): RepositoryResult<Unit>

    suspend fun deleteTriageItem(triageItemId: String): RepositoryResult<Unit>
}
