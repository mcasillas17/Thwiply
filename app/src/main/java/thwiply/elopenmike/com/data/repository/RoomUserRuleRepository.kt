package thwiply.elopenmike.com.data.repository

import kotlinx.coroutines.flow.Flow
import thwiply.elopenmike.com.data.local.dao.UserRuleDao
import thwiply.elopenmike.com.domain.triage.RepositoryResult
import thwiply.elopenmike.com.domain.triage.StorageOperation
import thwiply.elopenmike.com.domain.triage.UserRule
import thwiply.elopenmike.com.domain.triage.UserRuleRepository
import javax.inject.Inject

class RoomUserRuleRepository @Inject constructor(
    private val userRuleDao: UserRuleDao,
) : UserRuleRepository {
    override fun observeRules(): Flow<RepositoryResult<List<UserRule>>> = observeStorage(
        operation = StorageOperation.OBSERVE_RULES,
        source = userRuleDao.observeRules(),
    ) { rules -> rules.map { it.toDomain() } }

    override suspend fun upsertRule(rule: UserRule): RepositoryResult<Unit> =
        executeStorageOperation(StorageOperation.UPSERT_RULE) {
            userRuleDao.upsertRule(rule.toEntity())
        }

    override suspend fun deleteRule(ruleId: String): RepositoryResult<Unit> {
        var deletedRows = 0
        val result = executeStorageOperation(StorageOperation.DELETE_RULE) {
            deletedRows = userRuleDao.deleteRule(ruleId)
        }
        return if (result is RepositoryResult.Success && deletedRows == 0) {
            missingRecord(StorageOperation.DELETE_RULE)
        } else {
            result
        }
    }
}
