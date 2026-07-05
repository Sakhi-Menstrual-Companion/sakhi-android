package team.sakhi.android.common

/** Generic screen-load state wrapper for ViewModels observing KMM repository `Result<T>` calls. */
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
