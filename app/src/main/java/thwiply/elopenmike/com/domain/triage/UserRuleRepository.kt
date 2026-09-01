package thwiply.elopenmike.com.domain.triage

import kotlinx.coroutines.flow.Flow

interface UserRuleRepository {
    fun observeRules(): Flow<RepositoryResult<List<UserRule>>>

    suspend fun upsertRule(rule: UserRule): RepositoryResult<Unit>

    suspend fun deleteRule(ruleId: String): RepositoryResult<Unit>
}
