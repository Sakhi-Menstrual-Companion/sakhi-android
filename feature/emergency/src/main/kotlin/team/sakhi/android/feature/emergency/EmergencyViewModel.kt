package team.sakhi.android.feature.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.AndroidLocationProvider
import team.sakhi.android.platform.HapticImpact
import team.sakhi.emergency.EmergencyRealtimeCoordinator
import team.sakhi.emergency.EmergencyState
import team.sakhi.emergency.EmergencyStore
import team.sakhi.emergency.ResponderState
import team.sakhi.models.EmergencyOffer
import team.sakhi.models.EmergencyProfileDetail
import team.sakhi.models.EmergencyRequirement
import team.sakhi.models.NearbyRequest
import team.sakhi.session.SessionManager

/**
 * Local-only UI state. Everything that is not a draft text field or a transient spinner
 * lives in the shared [EmergencyStore].
 */
data class EmergencyUiState(
    val spotDraft: String = "",
    val messageDraft: String = "",
    val isSubmitting: Boolean = false,
    val hasLocationPermission: Boolean = false,
)

/**
 * Android's adapter over the shared Emergency Assistance state machine.
 *
 * The same rule applies here as on iOS: this class holds no feature logic. It does not
 * decide which step follows which, it does not format a distance, and it does not talk to
 * Supabase. All of that is [EmergencyStore] and [team.sakhi.repositories.EmergencyRepository]
 * in SakhiCore, so the two platforms cannot drift. What is left here is draft text, a
 * couple of pollers, and forwarding taps.
 */
class EmergencyViewModel(
    private val store: EmergencyStore,
    private val realtime: EmergencyRealtimeCoordinator,
    private val locationProvider: AndroidLocationProvider,
    private val hapticManager: AndroidHapticManager,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val state: StateFlow<EmergencyState> = store.state
    val responderState: StateFlow<ResponderState> = store.responderState

    /** Spot names she has used before, newest first. Owned by the shared store. */
    val recentSpots: StateFlow<List<String>> = store.recentSpots

    /**
     * How many Sakhis are discoverable around her. `null` means not checked yet, which is
     * not the same as zero — the original only showed its empty state once it knew.
     */
    val nearbyAvailableCount: StateFlow<Int?> = store.nearbyAvailableCount

    /** The profile card behind an offer, once she taps one open. */
    val profileDetail: StateFlow<EmergencyProfileDetail?> = store.profileDetail

    private val _uiState = MutableStateFlow(EmergencyUiState())
    val uiState: StateFlow<EmergencyUiState> = _uiState.asStateFlow()

    private var nearbyPollJob: Job? = null
    private var sessionPollJob: Job? = null
    private var subscribedRequestId: String? = null

    init {
        // Keep subscriptions and pollers matched to whatever step the store is on. Doing
        // this by observing state rather than from each screen means a step reached by
        // restore (app reopened mid-session) wires up exactly like one reached by tapping.
        viewModelScope.launch {
            store.state.collectLatest { current -> onStateChanged(current) }
        }
        viewModelScope.launch {
            sessionManager.session.collectLatest { session ->
                if (session == null) {
                    stopNearbyPolling()
                    teardownSubscriptions()
                    store.dismiss()
                }
            }
        }
    }

    private suspend fun onStateChanged(current: EmergencyState) {
        when (current) {
            is EmergencyState.WaitingForHelp -> {
                subscribe(current.requestId, includeMessages = false)
                stopSessionPolling()
            }
            is EmergencyState.InSession -> {
                subscribe(current.session.requestId, includeMessages = true)
                startSessionPolling(isRequester = current.session.viewerIsRequester)
            }
            else -> {
                stopSessionPolling()
                teardownSubscriptions()
            }
        }
    }

    private suspend fun subscribe(requestId: String, includeMessages: Boolean) {
        if (subscribedRequestId == requestId) return
        subscribedRequestId = requestId
        runCatching {
            realtime.subscribeToOffers(requestId)
            if (includeMessages) realtime.subscribeToSession(requestId)
        }
    }

    private suspend fun teardownSubscriptions() {
        if (subscribedRequestId == null) return
        subscribedRequestId = null
        runCatching { realtime.unsubscribeAll() }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /** Resolves whether she is waiting on help, on her way to help, or neither. */
    fun restore() {
        val userId = sessionManager.session.value?.userId ?: return
        viewModelScope.launch {
            refreshLocation()
            runCatching { store.restore(userId) }
        }
    }

    /** Entry point from the map button in the AI chat input bar. */
    fun begin() {
        viewModelScope.launch {
            refreshLocation()
            // The original checked who was around before deciding which sheet to show.
            runCatching { store.refreshNearbyAvailableCount() }
            store.beginRequest()
        }
    }

    /** The original's "Search Again" button on the No Nearby Sakhis screen. */
    fun searchAgain(onDone: () -> Unit) {
        hapticManager.impact(HapticImpact.LIGHT)
        viewModelScope.launch {
            refreshLocation()
            runCatching { store.refreshNearbyAvailableCount() }
            onDone()
        }
    }

    fun dismiss() {
        stopNearbyPolling()
        stopSessionPolling()
        viewModelScope.launch { teardownSubscriptions() }
        store.dismiss()
    }

    /**
     * Pulls a fix and hands it to KMM. Location is a platform API, so capturing it is
     * Android's job, but nothing here decides what the position is used for.
     */
    private suspend fun refreshLocation(): Boolean {
        val granted = locationProvider.hasPermission()
        _uiState.update { it.copy(hasLocationPermission = granted) }
        if (!granted) return false

        val fix = locationProvider.currentLocation() ?: return false
        store.updateDeviceLocation(fix.latitude, fix.longitude)
        return true
    }

    /** Called by the screen after the runtime permission dialog resolves. */
    fun onLocationPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
        if (granted) viewModelScope.launch { refreshLocation() }
    }

    // ── Requester ────────────────────────────────────────────────────────────

    fun chooseRequirement(requirement: EmergencyRequirement) {
        hapticManager.impact(HapticImpact.LIGHT)
        store.chooseRequirement(requirement)
    }

    fun backToRequirement() = store.backToRequirement()

    fun useRecentSpot(spot: String) {
        hapticManager.impact(HapticImpact.LIGHT)
        _uiState.update { it.copy(spotDraft = spot) }
    }

    fun forgetRecentSpot(spot: String) = store.forgetSpot(spot)

    fun onSpotDraftChanged(value: String) = _uiState.update { it.copy(spotDraft = value) }

    fun submitRequest(requirement: EmergencyRequirement) {
        if (_uiState.value.isSubmitting) return
        hapticManager.impact(HapticImpact.MEDIUM)
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            // Take a fresh fix rather than trusting the one captured when the sheet
            // opened; she may have moved while choosing.
            refreshLocation()
            val spot = _uiState.value.spotDraft.trim().ifEmpty { null }
            runCatching { store.submitRequest(requirement, spot) }
            _uiState.update { it.copy(isSubmitting = false, spotDraft = "") }
        }
    }

    fun openProfile(userId: String) {
        hapticManager.impact(HapticImpact.LIGHT)
        viewModelScope.launch { runCatching { store.openProfile(userId) } }
    }

    fun closeProfile() = store.closeProfile()

    fun setBlocked(userId: String, blocked: Boolean) {
        hapticManager.impact(HapticImpact.MEDIUM)
        viewModelScope.launch { runCatching { store.setBlocked(userId, blocked) } }
    }

    fun acceptOffer(offer: EmergencyOffer) {
        hapticManager.impact(HapticImpact.MEDIUM)
        viewModelScope.launch { runCatching { store.acceptOffer(offer.offerId) } }
    }

    fun cancelRequest(requestId: String) {
        viewModelScope.launch { runCatching { store.cancelRequest(requestId) } }
    }

    // ── Responder ────────────────────────────────────────────────────────────

    fun setAvailable(available: Boolean) {
        viewModelScope.launch {
            if (available && !refreshLocation()) return@launch
            runCatching { store.setAvailable(available) }
            if (available) startNearbyPolling() else stopNearbyPolling()
        }
    }

    fun offerHelp(request: NearbyRequest) {
        hapticManager.impact(HapticImpact.MEDIUM)
        viewModelScope.launch { runCatching { store.offerHelp(request.requestId) } }
    }

    fun refreshNearby() {
        viewModelScope.launch { runCatching { store.refreshNearby(NEARBY_RADIUS_METERS) } }
    }

    /**
     * Open requests are polled, not pushed. Realtime honours RLS and a responder has no
     * read access to open requests until she is accepted, which is what keeps a
     * requester's position hidden, so there is nothing the server could send her.
     */
    fun startNearbyPolling() {
        if (nearbyPollJob?.isActive == true) return
        nearbyPollJob = viewModelScope.launch {
            while (isActive) {
                refreshLocation()
                runCatching { store.refreshNearby(NEARBY_RADIUS_METERS) }
                delay(NEARBY_POLL_INTERVAL_MS)
            }
        }
    }

    fun stopNearbyPolling() {
        nearbyPollJob?.cancel()
        nearbyPollJob = null
    }

    fun clearResponderError() = store.clearResponderError()

    // ── Session ──────────────────────────────────────────────────────────────

    /**
     * While she walks, the distance the other woman sees should keep up. Only the
     * responder pushes updates; the requester is the one standing still and waiting.
     */
    private fun startSessionPolling(isRequester: Boolean) {
        if (sessionPollJob?.isActive == true) return
        sessionPollJob = viewModelScope.launch {
            while (isActive) {
                delay(SESSION_POLL_INTERVAL_MS)
                if (isRequester) {
                    runCatching { store.refreshSession() }
                } else {
                    refreshLocation()
                    runCatching { store.pushResponderLocation() }
                }
            }
        }
    }

    private fun stopSessionPolling() {
        sessionPollJob?.cancel()
        sessionPollJob = null
    }

    fun onMessageDraftChanged(value: String) = _uiState.update { it.copy(messageDraft = value) }

    fun sendMessage() {
        val body = _uiState.value.messageDraft.trim()
        if (body.isEmpty()) return
        _uiState.update { it.copy(messageDraft = "") }
        viewModelScope.launch { runCatching { store.sendMessage(body) } }
    }

    fun completeSession() {
        hapticManager.impact(HapticImpact.MEDIUM)
        viewModelScope.launch { runCatching { store.completeSession() } }
    }

    fun submitFeedback(rating: Int, note: String?) {
        viewModelScope.launch { runCatching { store.submitFeedback(rating, note) } }
    }

    fun skipFeedback() = store.skipFeedback()

    /** Deep-link and SOS-notification landing point. */
    fun openSession(requestId: String) {
        viewModelScope.launch { runCatching { store.loadSession(requestId) } }
    }

    override fun onCleared() {
        stopNearbyPolling()
        stopSessionPolling()
        super.onCleared()
    }

    private companion object {
        const val NEARBY_RADIUS_METERS = 2000.0
        const val NEARBY_POLL_INTERVAL_MS = 20_000L
        const val SESSION_POLL_INTERVAL_MS = 15_000L
    }
}
