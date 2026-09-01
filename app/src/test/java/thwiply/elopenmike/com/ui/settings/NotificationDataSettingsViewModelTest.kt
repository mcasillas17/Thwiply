package thwiply.elopenmike.com.ui.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import thwiply.elopenmike.com.domain.triage.NotificationDataDeletion
import thwiply.elopenmike.com.domain.triage.NotificationDataLifecycleRepository
import thwiply.elopenmike.com.domain.triage.RepositoryResult
import thwiply.elopenmike.com.domain.triage.StorageFailureReason
import thwiply.elopenmike.com.domain.triage.StorageOperation
import thwiply.elopenmike.com.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationDataSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `delete all reports exact deleted item and rule counts`() = runTest {
        val deletion = NotificationDataDeletion(
            notificationItemsDeleted = 2,
            rulesDeleted = 3,
        )
        val repository = FakeLifecycleRepository(RepositoryResult.Success(deletion))
        val viewModel = NotificationDataSettingsViewModel(repository)

        viewModel.deleteAllNotificationDataAndRules()
        advanceUntilIdle()

        assertEquals(NotificationDataDeletionState.Deleted(deletion), viewModel.state.value)
        assertEquals(1, repository.deleteAllCalls)
    }

    @Test
    fun `delete all failure remains visible and retryable`() = runTest {
        val failure = RepositoryResult.Failure(
            operation = StorageOperation.DELETE_ALL_NOTIFICATION_DATA_AND_RULES,
            reason = StorageFailureReason.DATABASE,
            cause = null,
        )
        val repository = FakeLifecycleRepository(failure)
        val viewModel = NotificationDataSettingsViewModel(repository)

        viewModel.deleteAllNotificationDataAndRules()
        advanceUntilIdle()

        assertEquals(NotificationDataDeletionState.Error, viewModel.state.value)
        viewModel.dismissResult()
        assertEquals(NotificationDataDeletionState.Idle, viewModel.state.value)
    }

    private class FakeLifecycleRepository(
        private val deleteResult: RepositoryResult<NotificationDataDeletion>,
    ) : NotificationDataLifecycleRepository {
        var deleteAllCalls = 0

        override suspend fun purgeExpiredNotificationData(
            nowEpochMillis: Long,
        ): RepositoryResult<Int> = RepositoryResult.Success(0)

        override suspend fun deleteAllNotificationDataAndRules():
            RepositoryResult<NotificationDataDeletion> {
            deleteAllCalls += 1
            return deleteResult
        }
    }
}
