package team.sakhi.android.feature.care

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

    // The walk ended while this was open: close. Arriving cold from a notification, give
    // the first read a few seconds before deciding there is nothing to show.
    LaunchedEffect(mine == null && watching == null, sawWalk) {
        if (mine == null && watching == null) {
            if (!sawWalk) delay(6_000)
            onClose()
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
        )
        else -> Box(
            modifier = Modifier.fillMaxSize().background(sakhiGroupedBackground()),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}
