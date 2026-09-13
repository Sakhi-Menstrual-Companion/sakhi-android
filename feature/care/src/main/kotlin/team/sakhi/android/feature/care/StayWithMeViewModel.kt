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
import kotlinx.datetime.Instant
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.platform.StayWithMeLocationService
import team.sakhi.emergency.EmergencySafePlace
import team.sakhi.emergency.EmergencySafePlaceKind
import team.sakhi.emergency.EmergencySafePlacesProvider
import team.sakhi.session.SessionManager
import team.sakhi.staywithme.StayWithMeDestination
import team.sakhi.staywithme.StayWithMeLocation
import team.sakhi.staywithme.StayWithMeRealtimeCoordinator
import team.sakhi.staywithme.StayWithMeSession
import team.sakhi.staywithme.StayWithMeStore
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
) : ViewModel() {

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

    val uiState: StateFlow<StayWithMeUiState> =
        combine(withPlaces, store.mineTrail, store.watchingTrail, realtime.isLive) { state, mine, watching, live ->
            state.copy(mineTrail = mine, watchingTrail = watching, isLiveSocket = live)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StayWithMeUiState(now = store.now()))

    init {
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

    /** Her refresh: a fresh fix from her phone, now, past the throttle. */
    fun refreshMyLocation() {
        hapticManager.selection()
        if (StayWithMeLocationService.hasLocationPermission(appContext)) {
            StayWithMeLocationService.refreshNow(appContext)
        }
    }

    /** Her person's refresh: the newest the server has, now, rather than at the next beat. */
    fun refreshWalk() {
        hapticManager.selection()
        viewModelScope.launch {
            sessionManager.current?.userId?.let { store.refresh(it) }
        }
    }

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
        const val POLL_MS = 10_000L

        /** The socket is carrying the walk, so this is only there for when it is not. */
        const val SOCKET_POLL_MS = 60_000L
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
