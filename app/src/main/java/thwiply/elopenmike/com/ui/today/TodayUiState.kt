package thwiply.elopenmike.com.ui.today

sealed interface TodayUiState {
    data object Loading : TodayUiState

    data object Empty : TodayUiState

    data class Content(val tasks: List<TaskItem>) : TodayUiState

    data object StorageError : TodayUiState
}
