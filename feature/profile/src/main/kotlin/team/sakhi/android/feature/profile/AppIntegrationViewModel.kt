package team.sakhi.android.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Duration
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
import team.sakhi.android.common.toSafeUserMessage

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
            val requestedSession = sessionManager.current
            if (requestedSession == null || !requestedSession.isViewingOwnData) {
                refresh()
                return@launch
            }
            _uiState.update { it.copy(isSyncing = true, error = null) }
            runCatching {
                val result = healthConnectManager.syncNow()
                if (!isStillCurrent(requestedSession)) return@runCatching null
                val insights = healthConnectManager.loadInsights()
                if (!isStillCurrent(requestedSession)) return@runCatching null
                result to insights
            }
                .onSuccess { outcome ->
                    if (outcome == null || !isStillCurrent(requestedSession)) return@onSuccess
                    val (result, insights) = outcome
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
                    if (!isStillCurrent(requestedSession)) return@onFailure
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            isLoading = false,
                            error = throwable.toSafeUserMessage(appContext, R.string.profile_app_integration_sync_failed),
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
        if (!isStillCurrent(session)) return
        val availability = healthConnectManager.availability()
        if (session == null) {
            if (!isStillCurrent(null)) return
            _uiState.value = AppIntegrationUiState(
                session = null,
                availability = availability,
                isLoading = false,
            )
            return
        }
        if (!session.isViewingOwnData || availability != HealthConnectAvailability.Available) {
            if (!isStillCurrent(session)) return
            _uiState.value = AppIntegrationUiState(
                session = session,
                availability = availability,
                isLoading = false,
            )
            return
        }

        val hasPermissions = runCatching { healthConnectManager.hasAllPermissions() }.getOrDefault(false)
        if (!isStillCurrent(session)) return
        val enabled = healthConnectManager.isEnabled()
        val insights = if (hasPermissions && enabled) {
            runCatching { healthConnectManager.loadInsights() }.getOrDefault(team.sakhi.android.platform.HealthConnectInsights())
        } else {
            team.sakhi.android.platform.HealthConnectInsights()
        }
        if (!isStillCurrent(session)) return

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
            val zoneId = ZoneId.systemDefault()
            val now = Instant.now().atZone(zoneId)
            val time = Instant.parse(iso).atZone(zoneId)
            val elapsedSeconds = Duration.between(time.toInstant(), now.toInstant()).seconds
            val relativeLabel = when {
                elapsedSeconds < MINUTE_IN_SECONDS -> {
                    appContext.getString(R.string.profile_app_integration_last_synced_just_now)
                }

                elapsedSeconds < HOUR_IN_SECONDS -> {
                    val minutes = (elapsedSeconds / MINUTE_IN_SECONDS).toInt()
                    appContext.resources.getQuantityString(
                        R.plurals.profile_app_integration_last_synced_minutes_ago,
                        minutes,
                        minutes,
                    )
                }

                elapsedSeconds < DAY_IN_SECONDS -> {
                    val hours = (elapsedSeconds / HOUR_IN_SECONDS).toInt()
                    appContext.resources.getQuantityString(
                        R.plurals.profile_app_integration_last_synced_hours_ago,
                        hours,
                        hours,
                    )
                }

                time.toLocalDate() == now.toLocalDate().minusDays(1) -> {
                    appContext.getString(R.string.profile_app_integration_last_synced_yesterday)
                }

                else -> {
                    time.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                }
            }
            appContext.getString(
                R.string.profile_app_integration_last_synced,
                relativeLabel,
            )
        }.getOrDefault(iso)
    }

    // isSameSubjectAs, not ==: see SessionContext.isSameSubjectAs.
    private fun isStillCurrent(session: SessionContext?): Boolean =
        session?.isSameSubjectAs(sessionManager.current) ?: (sessionManager.current == null)

    private companion object {
        const val MINUTE_IN_SECONDS = 60L
        const val HOUR_IN_SECONDS = 60L * MINUTE_IN_SECONDS
        const val DAY_IN_SECONDS = 24L * HOUR_IN_SECONDS
    }
}
