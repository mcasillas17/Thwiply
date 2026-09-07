package thwiply.elopenmike.com.domain.triage

import kotlinx.coroutines.flow.Flow

/**
 * Records that may be shown as of a given time, plus the retention expiry of each
 * notification-derived record among them, keyed by triage item id. Manual records never
 * expire and never appear in the map.
 *
 * Expiry is a property of this read, not of the durable [TriageItem] contract, so it stays in
 * this projection. A caller uses [nextExpiryAtEpochMillis] to refresh once when the earliest
 * visible record expires, instead of polling, and the per-record entries to re-check a
 * snapshot against a later time without waiting for the fresh read.
 */
data class VisibleTriageRecords(
    val records: List<TriageRecord>,
    val notificationExpiryByItemId: Map<String, Long> = emptyMap(),
) {
    val nextExpiryAtEpochMillis: Long? = notificationExpiryByItemId.values.minOrNull()

    /** True when this record's own retention ended at or before [asOfEpochMillis]. */
    fun hasExpired(record: TriageRecord, asOfEpochMillis: Long): Boolean =
        notificationExpiryByItemId[record.item.id]?.let { it <= asOfEpochMillis } == true
}

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
