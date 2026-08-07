package team.sakhi.android.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import team.sakhi.access.FeatureAccessState
import team.sakhi.care.CareRealtimeCoordinator
import team.sakhi.care.CareRuntimeState
import team.sakhi.care.CareStore
import team.sakhi.session.SessionManager
import team.sakhi.sync.SyncEngine

/**
 * Backs "Use Sakhi offline" — the Android counterpart of iOS's `OfflineModeView` /
 * `ProfileViewModel.goOffline()`.
 *
 * The order of operations is taken from the Swift source and matters: disconnect the
 * partner first (while the network still works and the server can be told), then tear
 * down realtime, then hold sync. Doing it the other way round would try to notify a
 * partner over a connection the app had already stopped using.
 */
class OfflineModeViewModel(
    private val sessionManager: SessionManager,
    private val careStore: CareStore,
    private val careRealtimeCoordinator: CareRealtimeCoordinator,
    private val syncEngine: SyncEngine,
    private val featureAccessState: FeatureAccessState,
) : ViewModel() {

    private val _isActivating = MutableStateFlow(false)
    val isActivating: StateFlow<Boolean> = _isActivating.asStateFlow()

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    /**
     * Switches Sakhi to offline. Mirrors iOS `goOffline()` step for step.
     *
     * Nothing here deletes anything: the sync queue is durable, so every write made
     * while offline is held and pushed on resume. That is exactly what this screen
     * promises the user, so it must stay true.
     */
    fun activateOfflineMode() {
        if (_isActivating.value) return
        _isActivating.value = true
        viewModelScope.launch {
            // 1. Cleanly disconnect a partnership she OWNS. Deliberately scoped to
            //    `OwnerConnected`: iOS uses `CareSessionManager.ownedPartnership`, and a
            //    care partner going offline must not be able to dissolve someone else's
            //    partnership from her side.
            val state = careStore.careState.value
            val userId = sessionManager.current?.userId
            if (state is CareRuntimeState.OwnerConnected && userId != null) {
                runCatching { careStore.leavePartnership(state.partnership.id, userId) }
            }

            // 2. Tear down realtime so nothing streams in or out.
            runCatching { careRealtimeCoordinator.stop() }

            // 3. Hold the durable queue and suppress every push and pull.
            syncEngine.pause()

            // 4. Finally flag the account as paused, which is what the feature gate
            //    reads. Last on purpose: if any step above failed we would rather be
            //    honestly "still online" than claim offline while sync is live.
            featureAccessState.setOnlineAccountPaused(true)

            _isActivating.value = false
            _finished.value = true
        }
    }
}
