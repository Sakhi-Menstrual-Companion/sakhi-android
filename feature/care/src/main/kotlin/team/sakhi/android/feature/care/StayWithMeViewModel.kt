package team.sakhi.android.feature.care

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.platform.StayWithMeLocationService
import team.sakhi.android.platform.StayWithMeLocalAlarms
import team.sakhi.emergency.EmergencySafePlace
import team.sakhi.emergency.EmergencySafePlaceKind
import team.sakhi.emergency.EmergencySafePlacesProvider
import team.sakhi.session.SessionManager
import team.sakhi.platform.PlatformKeyValueStore
import team.sakhi.platform.BiometricInterface
import team.sakhi.platform.BiometricResult
import team.sakhi.staywithme.CarePartnerCard
import team.sakhi.staywithme.StayWithMeAlarm
import team.sakhi.staywithme.StayWithMeAlarmPreference
import team.sakhi.staywithme.StayWithMeCheckInPreference
import team.sakhi.staywithme.StayWithMeDestination
import team.sakhi.staywithme.StayWithMeLocation
import team.sakhi.staywithme.StayWithMeRealtimeCoordinator
import team.sakhi.staywithme.StayWithMeSession
import team.sakhi.staywithme.StayWithMeStore
import team.sakhi.staywithme.StayWithMeWalkRecord
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class StayWithMeUiState(
    val now: Instant,
    /** Her own live walk. */
    val mine: StayWithMeSession? = null,
    /** A live walk this person was asked to stay for. */
    val watching: StayWithMeSession? = null,
    val isBusy: Boolean = false,
    val error: String? = null,
    /** Police, hospitals and medical shops near her, nearest first. Both sides see these. */
    val places: List<EmergencySafePlace> = emptyList(),
    val placesLoading: Boolean = false,
    /** The line her own walk has drawn, on her phone. */
    val mineTrail: List<StayWithMeLocation> = emptyList(),
    /** The same line as her person has been shown it. */
    val watchingTrail: List<StayWithMeLocation> = emptyList(),
    /** True while the walk is arriving on a socket rather than being asked for. */
    val isLiveSocket: Boolean = false,
    /**
     * True while "Are you okay?" is waiting for her answer. The panel drops to make room
     * and its buttons hide, so only one thing is asking at a time.
     */
    val checkInRequested: Boolean = false,
    /** Her face is being checked right now. */
    val checkInChecking: Boolean = false,
    /** It was not her, or she cancelled. The box says so and stays. */
    val checkInFailed: Boolean = false,
    /**
     * True when this phone should be showing "she has not reached yet", with the alarm.
     *
     * The rule is `StayWithMeAlarm`, shared with iOS, so the same walk raises the alarm at
     * the same moment on both phones.
     */
    val alarmRaised: Boolean = false,
)

/**
 * Android's side of Stay With Me: renders [StayWithMeStore] and starts or stops the
 * location service. The walk's rules live in KMM; nothing here decides whether she is late.
 */
class StayWithMeViewModel(
    private val appContext: Context,
    private val sessionManager: SessionManager,
    private val store: StayWithMeStore,
    private val realtime: StayWithMeRealtimeCoordinator,
    private val placesProvider: EmergencySafePlacesProvider,
    private val hapticManager: AndroidHapticManager,
    private val alarmPreference: StayWithMeAlarmPreference,
    private val checkInPreference: StayWithMeCheckInPreference,
    private val kvStore: PlatformKeyValueStore,
    private val biometrics: BiometricInterface,
) : ViewModel() {

    /**
     * The walk whose alarm he has already slid away. Kept on the phone, because an alarm
     * that comes back the moment the screen redraws is worse than one that never fired.
     */
    private val acknowledgedAlarmId = MutableStateFlow(kvStore.get(ACKNOWLEDGED_ALARM_KEY))

    private val checkIn = MutableStateFlow(CheckInState())

    private data class CheckInState(
        val requested: Boolean = false,
        val checking: Boolean = false,
        val failed: Boolean = false,
    )

    private val now = MutableStateFlow(store.now())
    private val places = MutableStateFlow<List<EmergencySafePlace>>(emptyList())
    private val placesLoading = MutableStateFlow(false)
    private var placesAnchor: Pair<Double, Double>? = null
    private var pollJob: Job? = null

    private val _route = MutableStateFlow<WalkRoute?>(null)
    /** The way from where she is to where she is going, when she said where. */
    internal val route: StateFlow<WalkRoute?> = _route
    private var routeFrom: Pair<Double, Double>? = null
    private var routeTo: StayWithMeDestination? = null

    private val base = combine(now, store.mine, store.watching, store.isBusy, store.lastError) { n, mine, watching, busy, error ->
        StayWithMeUiState(now = n, mine = mine, watching = watching, isBusy = busy, error = error)
    }

    private val withPlaces = combine(base, places, placesLoading) { state, found, loading ->
        state.copy(places = found, placesLoading = loading)
    }

    private val withTrails =
        combine(withPlaces, store.mineTrail, store.watchingTrail, realtime.isLive) { state, mine, watching, live ->
            state.copy(mineTrail = mine, watchingTrail = watching, isLiveSocket = live)
        }

    val uiState: StateFlow<StayWithMeUiState> =
        combine(withTrails, acknowledgedAlarmId, checkIn) { state, acknowledged, ask ->
            state.copy(
                checkInRequested = ask.requested && state.mine != null,
                checkInChecking = ask.checking,
                checkInFailed = ask.failed,
                alarmRaised = StayWithMeAlarm.isRaised(
                    session = state.watching,
                    now = state.now,
                    afterMinutes = alarmPreference.minutes(),
                    acknowledgedSessionId = acknowledged,
                ),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StayWithMeUiState(now = store.now()))

    /** Time to ask her again. Comes from the check-in push, or the timer below. */
    fun requestCheckIn() {
        if (store.mine.value == null) return
        checkIn.value = CheckInState(requested = true)
        hapticManager.impact(HapticImpact.HEAVY)
    }

    /**
     * She said she is okay. Her face is asked for first, because a box anyone holding her
     * phone could dismiss would make the question worth nothing.
     */
    fun confirmCheckIn() {
        viewModelScope.launch {
            checkIn.value = checkIn.value.copy(checking = true, failed = false)
            val isHer = runCatching {
                biometrics.authenticate(appContext.getString(R.string.care_swm_checkin_reason))
            }.getOrNull()
            val passed = isHer is BiometricResult.Success
            if (passed) {
                checkIn.value = CheckInState()
                store.markSeen()
                hapticManager.impact(HapticImpact.MEDIUM)
            } else {
                checkIn.value = CheckInState(requested = true, checking = false, failed = true)
            }
        }
    }

    /** He slid it away. This walk does not raise the alarm again. */
    fun acknowledgeAlarm() {
        val id = store.watching.value?.id ?: return
        kvStore.set(ACKNOWLEDGED_ALARM_KEY, id)
        acknowledgedAlarmId.value = id
        StayWithMeLocalAlarms.cancelNotReached(appContext)
        hapticManager.impact(HapticImpact.HEAVY)
    }

    init {
        // "Are you okay?", on her own interval, while the app is open. The same question is
        // scheduled as a notification below, for when it is not.
        viewModelScope.launch {
            while (isActive) {
                delay(checkInPreference.minutes().coerceAtLeast(1) * 60_000L)
                if (store.mine.value?.status?.isLive == true && !checkIn.value.requested) {
                    requestCheckIn()
                }
            }
        }

        // The two things a walk has to say when nobody is looking at the app. Both are
        // decided here rather than by the server, because his alarm delay and her check-in
        // interval never leave their own phones.
        viewModelScope.launch {
            store.watching.collect { watched ->
                if (watched != null && watched.status.isLive) {
                    StayWithMeLocalAlarms.scheduleNotReached(
                        context = appContext,
                        sessionId = watched.id,
                        atEpochMillis = StayWithMeAlarm
                            .alarmAt(watched, alarmPreference.minutes())
                            .toEpochMilliseconds(),
                    )
                } else {
                    StayWithMeLocalAlarms.cancelNotReached(appContext)
                }
            }
        }
        viewModelScope.launch {
            store.mine.collect { walk ->
                if (walk != null && walk.status.isLive) {
                    StayWithMeLocalAlarms.scheduleCheckIn(appContext, checkInPreference.minutes())
                } else {
                    StayWithMeLocalAlarms.cancelCheckIn(appContext)
                }
            }
        }
        // The countdown's clock. One tick a second, only while someone is subscribed.
        viewModelScope.launch {
            while (isActive) {
                now.value = store.now()
                delay(1_000)
            }
        }
        // Help near her follows her, but only after she has really moved. Both sides get it:
        // being the one walking is not a reason to be the one who cannot see a police
        // station, and she is the person who might have to walk into one.
        viewModelScope.launch {
            combine(store.mine, store.watching) { mine, watching -> mine ?: watching }.collect { walk ->
                val location = walk?.lastLocation ?: return@collect
                refreshPlacesIfMoved(location.latitude, location.longitude)
            }
        }
        // The way ahead. Asked for again only once she has moved sixty metres or the
        // destination changes, so a fix every fifteen seconds is not a route request every
        // fifteen seconds.
        viewModelScope.launch {
            combine(store.mine, store.watching) { mine, watching -> mine ?: watching }.collect { walk ->
                val destination = walk?.destination
                val here = walk?.lastLocation
                if (destination == null || here == null) {
                    _route.value = null
                    routeFrom = null
                    routeTo = null
                    return@collect
                }
                val from = here.latitude to here.longitude
                val moved = routeFrom?.let { distanceMeters(it.first, it.second, from.first, from.second) } ?: Double.MAX_VALUE
                if (destination == routeTo && moved < ROUTE_REFRESH_METERS) return@collect
                routeFrom = from
                routeTo = destination
                _route.value = StayWithMeRoutes.road(appContext, from, destination)
            }
        }
        // One socket, following whichever walks this phone is part of. A walk that ends
        // takes its channel down with it.
        viewModelScope.launch {
            combine(store.mine, store.watching) { mine, watching -> mine?.id to watching?.id }
                .distinctUntilChanged()
                .collect { runCatching { realtime.sync() } }
        }
    }

    /**
     * While the Care sheet is on screen: re-read the walk, say "I'm looking", notice late.
     *
     * Once the socket is up this is a safety net, not the way the map moves, so it drops to
     * one round trip a minute. That is the difference between six requests a minute and one
     * on both phones, which on her side is battery she needs to still have when she is home.
     */
    fun onVisible() {
        // Came back to the screen with an ask still out: keep listening for her answer.
        if (store.isAskPending()) followAsk()
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                sessionManager.current?.userId?.let { userId ->
                    store.refresh(userId)
                    if (store.watching.value != null) store.markSeen()
                    store.checkLate()
                    // Her walk is live but nothing is sharing, for example after the app was
                    // killed and reopened. Pick it back up rather than going silent.
                    if (store.mine.value != null && StayWithMeLocationService.hasLocationPermission(appContext)) {
                        StayWithMeLocationService.start(appContext)
                    }
                }
                delay(if (realtime.isLive.value) SOCKET_POLL_MS else POLL_MS)
            }
        }
    }

    fun onHidden() {
        pollJob?.cancel()
        pollJob = null
        askJob?.cancel()
        askJob = null
    }

    override fun onCleared() {
        viewModelScope.launch { runCatching { realtime.stop() } }
        super.onCleared()
    }

    fun start(partnershipId: String, minutes: Int, note: String, destination: StayWithMeDestination? = null) {
        hapticManager.impact(HapticImpact.MEDIUM)
        viewModelScope.launch {
            store.start(partnershipId, minutes, note, destination).onSuccess {
                // No location, no service: the walk and its timer still protect her, her
                // screen just says location is off.
                if (StayWithMeLocationService.hasLocationPermission(appContext)) {
                    StayWithMeLocationService.start(appContext)
                }
            }
        }
    }

    fun extend() {
        hapticManager.selection()
        viewModelScope.launch { store.extend() }
    }

    fun arrive() {
        hapticManager.impact(HapticImpact.MEDIUM)
        viewModelScope.launch { store.arrive().onSuccess { StayWithMeLocationService.stop(appContext) } }
    }

    fun stop() {
        viewModelScope.launch { store.cancel().onSuccess { StayWithMeLocationService.stop(appContext) } }
    }

    /** Who the other person is: the name they set, and the face the server keeps for them. */
    val partnerCard: StateFlow<CarePartnerCard?> = store.partnerCard

    fun loadPartnerCard(partnershipId: String) {
        viewModelScope.launch { store.loadPartnerCard(partnershipId) }
    }

    /** The walks this connection has already done, for the connection screen. */
    val history: StateFlow<List<StayWithMeWalkRecord>> = store.history

    fun loadHistory(partnershipId: String) {
        viewModelScope.launch { store.loadHistory(partnershipId) }
    }

    /**
     * Her person asking to stay with her. Sends a question to her phone and nothing else:
     * no walk starts here, because it is her walk to start.
     */
    fun askToStay(
        partnershipId: String,
        destination: team.sakhi.staywithme.StayWithMeDestination? = null,
        minutes: Int? = null,
    ) {
        hapticManager.impact(HapticImpact.MEDIUM)
        viewModelScope.launch {
            // Success needs no toast: the button itself turns into "Waiting for her to
            // start" and then into her walk. Only a failure is worth a word.
            store.askToStay(partnershipId, destination, minutes)
                .onSuccess { followAsk() }
                .onFailure { _askResult.value = appContext.getString(R.string.care_ask_failed) }
        }
    }

    /** She turns an ask down. Tells the person who asked and nothing else. */
    fun declineAsk(partnershipId: String) {
        viewModelScope.launch { store.declineAsk(partnershipId) }
    }

    /** When her person asked, while it still waits on her. Drives the waiting button. */
    val askedAt: StateFlow<Instant?> = store.askedAt

    private var askJob: Job? = null

    /** Watches for her answer while this screen is open. One at a time. */
    private fun followAsk() {
        if (askJob?.isActive == true) return
        val userId = sessionManager.current?.userId ?: return
        askJob = viewModelScope.launch { store.followAsk(userId) }
    }

    private val _askResult = MutableStateFlow<String?>(null)
    /** What to tell her person after they asked. Cleared once shown. */
    val askResult: StateFlow<String?> = _askResult
    fun clearAskResult() { _askResult.value = null }

    /** Her refresh: a fresh fix from her phone, now, past the throttle. */
    fun refreshMyLocation() {
        hapticManager.selection()
        if (_refreshing.value) return
        if (!StayWithMeLocationService.hasLocationPermission(appContext)) return
        val before = store.mine.value?.lastLocation?.recordedAt
        _refreshing.value = true
        StayWithMeLocationService.refreshNow(appContext)
        viewModelScope.launch {
            // Her fix goes up and comes back as her row. Wait for one newer than what was on
            // screen, but never longer than a phone takes to find the sky.
            withTimeoutOrNull(REFRESH_WAIT_MS) {
                store.mine.first { walk ->
                    val at = walk?.lastLocation?.recordedAt
                    at != null && (before == null || at > before)
                }
            }
            _refreshing.value = false
            _recenterTick.value += 1
        }
    }

    /** Her person's refresh: the newest the server has, now, rather than at the next beat. */
    fun refreshWalk() {
        hapticManager.selection()
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            sessionManager.current?.userId?.let { store.refresh(it) }
            // A read that comes back in 80 ms would only flash the spinner.
            delay(REFRESH_MIN_SPIN_MS)
            _refreshing.value = false
            _recenterTick.value += 1
        }
    }

    private val _refreshing = MutableStateFlow(false)
    /** A refresh is in flight, from either side. */
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _recenterTick = MutableStateFlow(0)
    /** Bumped when a refresh lands, so the map frames her again. */
    val recenterTick: StateFlow<Int> = _recenterTick

    fun clearError() = store.clearError()

    private fun refreshPlacesIfMoved(latitude: Double, longitude: Double) {
        val anchor = placesAnchor
        if (anchor != null && distanceMeters(anchor.first, anchor.second, latitude, longitude) < PLACES_REFRESH_METERS) return
        placesAnchor = latitude to longitude
        viewModelScope.launch {
            placesLoading.value = true
            val found = runCatching { placesProvider.searchKinds(latitude, longitude, NEARBY_KINDS) }
                .getOrDefault(emptyList())
            places.value = found.distinctBy { it.id }.sortedBy { it.distanceMeters }.take(MAX_PLACES)
            placesLoading.value = false
        }
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private companion object {
        /** The longest a refresh waits for her phone's fresh fix before giving up quietly. */
        private const val REFRESH_WAIT_MS = 10_000L
        /** The least a refresh spins, so a fast answer does not read as a flicker. */
        private const val REFRESH_MIN_SPIN_MS = 450L
        /** The walk whose alarm he has already slid away, on this phone. */
        const val ACKNOWLEDGED_ALARM_KEY = "stayWithMe.notReached.acknowledged"

        const val POLL_MS = 10_000L

        /** The socket is carrying the walk, so this is only there for when it is not. */
        /**
         * Once the socket is up, every change arrives pushed, including the start and the
         * end, so this is only a watchdog against a broken trigger or a silently dead
         * channel. Five minutes, the same as iOS.
         */
        const val SOCKET_POLL_MS = 300_000L
        const val PLACES_REFRESH_METERS = 300.0
        const val ROUTE_REFRESH_METERS = 60.0
        const val MAX_PLACES = 6
        val NEARBY_KINDS = listOf(
            EmergencySafePlaceKind.POLICE,
            EmergencySafePlaceKind.HOSPITAL,
            EmergencySafePlaceKind.PHARMACY,
        )
    }
}
