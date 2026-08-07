package team.sakhi.android.feature.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import team.sakhi.care.CareStore
import team.sakhi.session.SessionManager
import team.sakhi.android.common.toSafeUserMessage

data class LogPermissionUiState(
    val isSending: Boolean = false,
    val requestSent: Boolean = false,
    val error: String? = null,
)

/**
 * Sends a care partner's request for logging access — Android counterpart of iOS's
 * `LogPermissionVM`.
 *
 * Deliberately has no "grant" path. A partner can ask; only the data owner can approve,
 * and she does that from her own device. Nothing here changes a permission.
 */
class LogPermissionViewModel(
    private val appContext: android.content.Context,
    private val sessionManager: SessionManager,
    private val careStore: CareStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogPermissionUiState())
    val uiState: StateFlow<LogPermissionUiState> = _uiState.asStateFlow()

    fun sendRequest(primaryUserId: String, partnershipId: String, partnerName: String) {
        val state = _uiState.value
        if (state.isSending || state.requestSent) return
        val callerUserId = sessionManager.current?.userId ?: return

        _uiState.value = state.copy(isSending = true, error = null)
        viewModelScope.launch {
            runCatching {
                careStore.requestLogPermission(
                    primaryUserId = primaryUserId,
                    partnershipId = partnershipId,
                    partnerName = partnerName,
                    callerUserId = callerUserId,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isSending = false, requestSent = true)
            }.onFailure { throwable ->
                // Stays un-sent on failure so she is never told a request went out that
                // did not. The button returns to "Request to Log" and can be retried.
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    error = throwable.toSafeUserMessage(appContext, R.string.care_log_permission_request_failed),
                )
            }
        }
    }
}
