package team.sakhi.android.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import team.sakhi.android.platform.AndroidHealthConnectManager
import team.sakhi.android.platform.DailyHealthValue
import team.sakhi.android.platform.HealthConnectAvailability
import team.sakhi.android.platform.HealthConnectSyncResult
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager

data class AppIntegrationUiState(
    val session: SessionContext? = null,
    val availability: HealthConnectAvailability = HealthConnectAvailability.NotSupported,
    val hasPermissions: Boolean = false,
    val isEnabled: Boolean = false,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val lastSyncedAtLabel: String? = null,
    val latestSync: HealthConnectSyncResult? = null,
    val sleepEntries: List<DailyHealthValue> = emptyList(),
    val stepEntries: List<DailyHealthValue> = emptyList(),
    val temperatureEntries: List<DailyHealthValue> = emptyList(),
    val error: String? = null,
)

class AppIntegrationViewModel(
    private val sessionManager: SessionManager,
    private val healthConnectManager: AndroidHealthConnectManager,
    private val appContext: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppIntegrationUiState())
    val uiState: StateFlow<AppIntegrationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.session.collectLatest { session ->
                refresh(session = session)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refresh(session = sessionManager.current)
        }
    }

    fun onPermissionsResult(grantedPermissions: Set<String>) {
        if (grantedPermissions.containsAll(healthConnectManager.requiredPermissions)) {
            syncNow()
        } else {
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    error = appContext.getString(R.string.profile_app_integration_permission_denied),
                )
            }
            refresh()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, error = null) }
            runCatching { healthConnectManager.syncNow() }
                .onSuccess { result ->
                    val insights = healthConnectManager.loadInsights()
                    _uiState.update {
                        it.copy(
                            hasPermissions = true,
                            isEnabled = true,
                            isLoading = false,
                            isSyncing = false,
                            latestSync = result,
                            lastSyncedAtLabel = formatLastSynced(result.syncedAtIso),
                            sleepEntries = insights.sleepEntries,
                            stepEntries = insights.stepEntries,
                            temperatureEntries = insights.temperatureEntries,
                            error = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            isLoading = false,
                            error = throwable.message ?: appContext.getString(R.string.profile_app_integration_sync_failed),
                        )
                    }
                }
        }
    }

    fun disconnect() {
        healthConnectManager.disableIntegration()
        _uiState.update { it.copy(latestSync = null) }
        refresh()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun refresh(session: SessionContext?) {
        val availability = healthConnectManager.availability()
        if (session == null) {
            _uiState.value = AppIntegrationUiState(
                session = null,
                availability = availability,
                isLoading = false,
            )
            return
        }
        if (!session.isViewingOwnData || availability != HealthConnectAvailability.Available) {
            _uiState.value = AppIntegrationUiState(
                session = session,
                availability = availability,
                isLoading = false,
            )
            return
        }

        val hasPermissions = runCatching { healthConnectManager.hasAllPermissions() }.getOrDefault(false)
        val enabled = healthConnectManager.isEnabled()
        val insights = if (hasPermissions && enabled) {
            runCatching { healthConnectManager.loadInsights() }.getOrDefault(team.sakhi.android.platform.HealthConnectInsights())
        } else {
            team.sakhi.android.platform.HealthConnectInsights()
        }

        _uiState.value = AppIntegrationUiState(
            session = session,
            availability = availability,
            hasPermissions = hasPermissions,
            isEnabled = enabled,
            isLoading = false,
            isSyncing = false,
            latestSync = if (enabled) _uiState.value.latestSync else null,
            lastSyncedAtLabel = healthConnectManager.lastSyncedAtIso()?.let(::formatLastSynced),
            sleepEntries = insights.sleepEntries,
            stepEntries = insights.stepEntries,
            temperatureEntries = insights.temperatureEntries,
            error = _uiState.value.error,
        )
    }

    private fun formatLastSynced(iso: String): String {
        return runCatching {
            val time = Instant.parse(iso).atZone(ZoneId.systemDefault())
            appContext.getString(
                R.string.profile_app_integration_last_synced,
                time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)),
            )
        }.getOrDefault(iso)
    }
}
