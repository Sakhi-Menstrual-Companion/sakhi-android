package team.sakhi.android.feature.care

import team.sakhi.staywithme.StayWithMeLocation
import team.sakhi.android.platform.StayWithMeLocationService
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.SakhiRadius
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.sakhiGroupedBackground
import team.sakhi.care.CareRuntimeState

/**
 * A live Stay With Me walk, full screen, for whichever side of it this phone is on.
 *
 * The host shows this instead of the Care sheet whenever a walk is live, the same way it
 * shows Emergency Assistance: the map is the screen, not a pane inside a sheet over Home.
 * Opened from Home's Care button, from the walk notification, or the moment she starts one.
 *
 * It closes itself when the walk ends, from either phone, so nobody is left looking at a
 * map of a walk that is over.
 */
@Composable
fun StayWithMeLiveLayer(
    onClose: () -> Unit,
    careViewModel: CareViewModel = koinViewModel(),
    viewModel: StayWithMeViewModel = koinViewModel(),
) {
    val care by careViewModel.uiState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val recenterTick by viewModel.recenterTick.collectAsStateWithLifecycle()

    // While this is on screen: re-read the walk, tell her screen her person is looking,
    // and notice a walk that has gone past its time.
    DisposableEffect(Unit) {
        viewModel.onVisible()
        onDispose { viewModel.onHidden() }
    }
    BackHandler(onBack = onClose)

    val owner = care.careState as? CareRuntimeState.OwnerConnected
    val partner = care.careState as? CareRuntimeState.PartnerConnected
    val mine = state.mine.takeIf { owner != null }
    val watching = state.watching.takeIf { partner != null }

    var sawWalk by remember { mutableStateOf(false) }
    if (mine != null || watching != null) sawWalk = true

    // The walk ended while this was open: close. Arriving cold from a notification, give the
    // first read a few seconds before deciding there is nothing to show. Her own side is the
    // exception: with no walk this IS where she starts one, so it stays.
    LaunchedEffect(mine == null && watching == null, sawWalk, owner != null) {
        if (mine == null && watching == null && owner == null) {
            if (!sawWalk) delay(6_000)
            onClose()
        }
    }

    // Where this phone is, for the map before any walk. From the fix the system already has.
    val context = LocalContext.current
    var here by remember { mutableStateOf<StayWithMeLocation?>(null) }
    LaunchedEffect(owner?.partnership?.id) {
        if (owner != null) {
            here = StayWithMeLocationService.lastKnownLatLng(context)?.let { (lat, lng) ->
                StayWithMeLocation(lat, lng, null, null, null, kotlinx.datetime.Clock.System.now())
            }
        }
    }

    when {
        mine != null && owner != null -> StayWithMeOwnerLive(
            session = mine,
            personName = careDisplayName(owner.partnership, isPartnerRole = false),
            now = state.now,
            isBusy = state.isBusy,
            trail = state.mineTrail,
            places = state.places,
            placesLoading = state.placesLoading,
            onArrive = viewModel::arrive,
            onExtend = viewModel::extend,
            onStop = viewModel::stop,
            onClose = onClose,
            onRefresh = viewModel::refreshMyLocation,
            route = route,
            refreshing = refreshing,
            recenterKey = recenterTick,
        )
        watching != null && partner != null -> StayWithMeWatcherLive(
            session = watching,
            herName = careDisplayName(partner.partnership, isPartnerRole = true),
            now = state.now,
            places = state.places,
            placesLoading = state.placesLoading,
            trail = state.watchingTrail,
            onClose = onClose,
            onRefresh = viewModel::refreshWalk,
            route = route,
            refreshing = refreshing,
            recenterKey = recenterTick,
        )
        // No walk, and this is her own connection: the screen she starts one from, in the same
        // full screen the walk itself uses. The map is the screen and the form sits over it.
        owner != null -> StayWithMeStartLayer(
            personName = careDisplayName(owner.partnership, isPartnerRole = false),
            here = here,
            isBusy = state.isBusy,
            error = state.error,
            onClose = onClose,
            onStart = { minutes, note, destination ->
                viewModel.start(owner.partnership.id, minutes, note, destination)
            },
        )
        else -> Box(
            modifier = Modifier.fillMaxSize().background(sakhiGroupedBackground()),
            contentAlignment = Alignment.Center,
        ) {
            // Only while the first read is in flight. A walk that has just ended closes this
            // at once, and a spinner there looked like the app was still waiting for her.
            if (!sawWalk) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Setting off, full screen: the map is the screen, the way out and Contact Police over it,
 * and everything she fills in in the panel below. The same shape the live walk has and the
 * same one Emergency Assistance has (Karan, 2026-09-13), so the three read as one feature.
 */
@Composable
private fun StayWithMeStartLayer(
    personName: String,
    here: StayWithMeLocation?,
    isBusy: Boolean,
    error: String?,
    onClose: () -> Unit,
    onStart: (minutes: Int, note: String, destination: team.sakhi.staywithme.StayWithMeDestination?) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(sakhiSystemBackground())) {
        WalkMap(
            location = here,
            accent = MaterialTheme.colorScheme.primary,
            initial = "",
            modifier = Modifier.fillMaxSize(),
            bottomPadding = 320.dp,
        )
        LiveWalkTopBar(onClose = onClose, modifier = Modifier.align(Alignment.TopCenter))
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = SakhiRadius.xl, topEnd = SakhiRadius.xl),
            color = sakhiSystemBackground(),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(top = SakhiSpacing.space5),
            ) {
                StayWithMeStartSection(
                    personName = personName,
                    isBusy = isBusy,
                    error = error,
                    onStart = onStart,
                )
            }
        }
    }
}
