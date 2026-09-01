package thwiply.elopenmike.com.domain.triage

sealed interface RepositoryResult<out T> {
    data class Success<T>(val value: T) : RepositoryResult<T>

    data class Failure(
        val operation: StorageOperation,
        val reason: StorageFailureReason,
        val cause: Throwable?,
    ) : RepositoryResult<Nothing>
}

enum class StorageOperation {
    OBSERVE_TRIAGE,
    CREATE_TRIAGE,
    UPDATE_TRIAGE,
    TOGGLE_TRIAGE_COMPLETION,
    DELETE_TRIAGE,
    OBSERVE_CORRECTIONS,
    CREATE_CORRECTION,
    OBSERVE_RULES,
    UPSERT_RULE,
    DELETE_RULE,
    PURGE_EXPIRED_NOTIFICATION_DATA,
    DELETE_ALL_NOTIFICATION_DATA_AND_RULES,
}

enum class StorageFailureReason {
    NOT_FOUND,
    DATABASE,
}
