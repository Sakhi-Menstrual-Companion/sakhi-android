package team.sakhi.android.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import team.sakhi.access.FeatureAccessState
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.appstate.AppStateInputBridge
import team.sakhi.auth.AuthRepository
import team.sakhi.care.CareRealtimeCoordinator
import team.sakhi.cycle.CycleMath
import team.sakhi.models.CycleHealthStatus
import team.sakhi.models.UserProfile
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.android.common.toSafeUserMessage
import team.sakhi.sync.SyncPauseState

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
    /**
     * True while she has chosen "Use Sakhi offline" on a real (signed-in) account. iOS
     * branches the Account group on exactly this (`vm.isSyncPaused` in `ProfileView`): the
     * row reads "Use Sakhi offline" normally and "Resume online sync" while held.
     */
    val isSyncPaused: Boolean = false,
    /**
     * iOS `vm.isAccountSecureOnline`, i.e. KMM `FeatureAccessState.isCloudSyncActive`: NOT a
     * guest AND not paused. The profile card's status line reads from this on iOS, which is
     * why a signed-in account that has gone offline says "On this device" there.
     *
     * Android read `isOfflineUser` (guest only) for that line, so the same account kept
     * claiming "Synced & secure" while its sync was held.
     */
    val isAccountSecureOnline: Boolean = true,
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
    private val syncPauseState: SyncPauseState,
    private val careRealtimeCoordinator: CareRealtimeCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        // Keeps the Account row's wording honest while she is on the screen: pausing or
        // resuming from anywhere flips this without the sheet being reopened.
        viewModelScope.launch {
            syncPauseState.isPaused.collect { paused ->
                _uiState.update { it.copy(isSyncPaused = paused) }
            }
        }
    }

    /**
     * The way back from offline, ported from iOS `ProfileViewModel.resumeOnline()`.
     *
     * Order matters and is taken from the Swift: let sync go first, so the durable queue
     * starts draining what she logged while offline, then bring realtime back up so care
     * invites and a partner's data can flow again. Nothing here is destructive; going
     * offline never deleted anything, it only held the queue.
     */
    fun resumeOnline() {
        viewModelScope.launch {
            syncPauseState.resume()
            featureAccessState.setOnlineAccountPaused(false)
            val userId = sessionManager.current?.userId
            if (userId != null) {
                runCatching { careRealtimeCoordinator.startAsOwner(userId) }
            }
        }
    }

    init {
        viewModelScope.launch {
            sessionManager.session.collectLatest { session ->
                loadProfile(session)
            }
        }
        viewModelScope.launch {
            // Both halves of `isCloudSyncActive`, combined here so the card's status line
            // and the Account row's wording can never disagree with each other.
            combine(
                featureAccessState.isGuest,
                featureAccessState.isOnlineAccountPaused,
            ) { isGuest, isPaused -> isGuest to isPaused }
                .collectLatest { (isGuest, isPaused) ->
                    _uiState.update {
                        it.copy(
                            isOfflineUser = isGuest,
                            isAccountSecureOnline = !isGuest && !isPaused,
                        )
                    }
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
                            signOutError = throwable.toSafeUserMessage(appContext, R.string.profile_sign_out_failed),
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
                        error = throwable.toSafeUserMessage(appContext, R.string.profile_load_failed),
                    )
                }
            }
    }
}
