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
    /** Police, hospitals and medical shops near her, nearest first. Watcher side only. */
    val places: List<EmergencySafePlace> = emptyList(),
    val placesLoading: Boolean = false,
)

/**
 * Android's side of Stay With Me: renders [StayWithMeStore] and starts or stops the
 * location service. The walk's rules live in KMM; nothing here decides whether she is late.
 */
class StayWithMeViewModel(
    private val appContext: Context,
    private val sessionManager: SessionManager,
    private val store: StayWithMeStore,
    private val placesProvider: EmergencySafePlacesProvider,
    private val hapticManager: AndroidHapticManager,
) : ViewModel() {

    private val now = MutableStateFlow(store.now())
    private val places = MutableStateFlow<List<EmergencySafePlace>>(emptyList())
    private val placesLoading = MutableStateFlow(false)
    private var placesAnchor: Pair<Double, Double>? = null
    private var pollJob: Job? = null

    private val base = combine(now, store.mine, store.watching, store.isBusy, store.lastError) { n, mine, watching, busy, error ->
        StayWithMeUiState(now = n, mine = mine, watching = watching, isBusy = busy, error = error)
    }

    val uiState: StateFlow<StayWithMeUiState> =
        combine(base, places, placesLoading) { state, found, loading ->
            state.copy(places = found, placesLoading = loading)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StayWithMeUiState(now = store.now()))

    init {
        // The countdown's clock. One tick a second, only while someone is subscribed.
        viewModelScope.launch {
            while (isActive) {
                now.value = store.now()
                delay(1_000)
            }
        }
        // Help near her follows her, but only after she has really moved.
        viewModelScope.launch {
            store.watching.collect { walk ->
                val location = walk?.lastLocation ?: return@collect
                refreshPlacesIfMoved(location.latitude, location.longitude)
            }
        }
    }

    /** While the Care sheet is on screen: re-read the walk, say "I'm looking", notice late. */
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
                delay(POLL_MS)
            }
        }
    }

    fun onHidden() {
        pollJob?.cancel()
        pollJob = null
    }

    fun start(partnershipId: String, minutes: Int, note: String) {
        hapticManager.impact(HapticImpact.MEDIUM)
        viewModelScope.launch {
            store.start(partnershipId, minutes, note).onSuccess {
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
        const val PLACES_REFRESH_METERS = 300.0
        const val MAX_PLACES = 6
        val NEARBY_KINDS = listOf(
            EmergencySafePlaceKind.POLICE,
            EmergencySafePlaceKind.HOSPITAL,
            EmergencySafePlaceKind.PHARMACY,
        )
    }
}
