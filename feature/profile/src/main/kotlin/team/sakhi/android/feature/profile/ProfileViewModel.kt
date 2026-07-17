package team.sakhi.android.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import team.sakhi.access.FeatureAccessState
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.appstate.AppStateInputBridge
import team.sakhi.auth.AuthRepository
import team.sakhi.cycle.CycleMath
import team.sakhi.models.CycleHealthStatus
import team.sakhi.models.UserProfile
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager

data class ProfileUiState(
    val session: SessionContext? = null,
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showSignOutConfirm: Boolean = false,
    val isSigningOut: Boolean = false,
    val signOutError: String? = null,
    val cycleHealthStatus: CycleHealthStatus? = null,
    // Real signal from the shared `FeatureAccessState` (wired 2026-07-05):
    // true for a local-only/offline account that never signed in to the
    // cloud. Previously this screen always assumed the "signed-in owner"
    // case, documented as a real gap in this file's doc comment.
    val isOfflineUser: Boolean = false,
)

/**
 * Thin profile-state adapter over KMM session and profile repositories. Identity
 * stays owned by `SessionManager`, and profile fetching stays owned by
 * `UserProfileRepository`.
 */
class ProfileViewModel(
    private val sessionManager: SessionManager,
    private val userProfileRepository: UserProfileRepository,
    private val cycleDataRepository: CycleDataRepository,
    private val authRepository: AuthRepository,
    private val appStateInputBridge: AppStateInputBridge,
    private val featureAccessState: FeatureAccessState,
    private val hapticManager: AndroidHapticManager,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.session.collectLatest { session ->
                loadProfile(session)
            }
        }
        viewModelScope.launch {
            featureAccessState.isGuest.collectLatest { isGuest ->
                _uiState.update { it.copy(isOfflineUser = isGuest) }
            }
        }
    }

    fun requestSignOut() {
        _uiState.update { it.copy(showSignOutConfirm = true, signOutError = null) }
    }

    fun dismissSignOutConfirm() {
        _uiState.update { it.copy(showSignOutConfirm = false) }
    }

    /**
     * Mirrors iOS `ProfileViewModel.logout()` -> `AuthRuntimeController.
     * signOutAfterSettlingPresentations()`: clears the KMM session (Realm/
     * Keychain-equivalent local state + Supabase session on iOS; token storage
     * + kv store here) then flips `AppStateInputBridge` to `Unauthenticated` so
     * `AppStateStore.appRoute` re-resolves to `SignedOut` -- the same bridge
     * `RootNavHost`'s cold-start restore already drives. `sessionManager.stop()`
     * (session teardown) and `careRealtimeCoordinator.stop()` (realtime teardown)
     * both fire from `HomeSessionGate.onDispose` once that route change unmounts
     * `HomeNavHost`, not here, matching the existing "who owns start/stop" split.
     */
    fun confirmSignOut() {
        if (_uiState.value.isSigningOut) return
        _uiState.update { it.copy(isSigningOut = true, signOutError = null) }

        viewModelScope.launch {
            authRepository.signOut()
                .onSuccess {
                    _uiState.update { it.copy(isSigningOut = false, showSignOutConfirm = false) }
                    appStateInputBridge.setUnauthenticated()
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isSigningOut = false,
                            signOutError = throwable.message
                                ?: appContext.getString(R.string.profile_sign_out_failed),
                        )
                    }
                    hapticManager.error()
                }
        }
    }

    private suspend fun loadProfile(session: SessionContext?) {
        if (session == null) {
            // Preserve isOfflineUser -- it's driven by a separate collector
            // (featureAccessState.isGuest) that keeps running independently
            // of session load/error transitions.
            _uiState.update { ProfileUiState(isOfflineUser = it.isOfflineUser) }
            return
        }

        _uiState.update {
            it.copy(
                session = session,
                profile = null,
                isLoading = true,
                error = null,
                cycleHealthStatus = null,
            )
        }

        val requestedUserId = session.userId
        userProfileRepository.get(requestedUserId)
            .onSuccess { profile ->
                if (sessionManager.current?.userId != requestedUserId) return
                val cycles = cycleDataRepository.getAll(requestedUserId).getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        session = session,
                        profile = profile,
                        isLoading = false,
                        error = null,
                        cycleHealthStatus = CycleMath.profileHealthStatus(cycles),
                    )
                }
            }
            .onFailure { throwable ->
                if (sessionManager.current?.userId != requestedUserId) return
                _uiState.update {
                    it.copy(
                        session = session,
                        profile = null,
                        isLoading = false,
                        error = throwable.message
                            ?: appContext.getString(R.string.profile_load_failed),
                    )
                }
            }
    }
}
