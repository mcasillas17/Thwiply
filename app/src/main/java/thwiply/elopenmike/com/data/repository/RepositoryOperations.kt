package thwiply.elopenmike.com.data.repository

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import thwiply.elopenmike.com.domain.triage.RepositoryResult
import thwiply.elopenmike.com.domain.triage.StorageFailureReason
import thwiply.elopenmike.com.domain.triage.StorageOperation

internal suspend fun <T> executeStorageOperation(
    operation: StorageOperation,
    block: suspend () -> T,
): RepositoryResult<T> = try {
    RepositoryResult.Success(block())
} catch (exception: SQLiteException) {
    RepositoryResult.Failure(
        operation = operation,
        reason = StorageFailureReason.DATABASE,
        cause = exception,
    )
}

internal fun <T, R> observeStorage(
    operation: StorageOperation,
    source: Flow<T>,
    transform: (T) -> R,
): Flow<RepositoryResult<R>> = flow {
    try {
        source.collect { value ->
            emit(RepositoryResult.Success(transform(value)))
        }
    } catch (exception: SQLiteException) {
        emit(
            RepositoryResult.Failure(
                operation = operation,
                reason = StorageFailureReason.DATABASE,
                cause = exception,
            ),
        )
    }
}

internal fun missingRecord(operation: StorageOperation) = RepositoryResult.Failure(
    operation = operation,
    reason = StorageFailureReason.NOT_FOUND,
    cause = null,
)
