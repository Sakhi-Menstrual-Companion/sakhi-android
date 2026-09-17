package team.sakhi.android.feature.care

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
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
import team.sakhi.android.ui.IntroSeenKey
import team.sakhi.android.ui.SakhiOnboardingPoint
import team.sakhi.android.ui.SakhiOnboardingView
import team.sakhi.android.ui.rememberIntroSeen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Map
import androidx.compose.ui.res.stringResource
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
    /**
     * Opens the Care screen, where a care partner is invited. Reached only from the intro's
     * primary button, and only for someone who has nobody on Be Her Sakhi yet, so the screen
     * can explain the walk without ending in a dead button.
     */
    onAddCarePartner: () -> Unit = {},
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

    // What a walk is, once, before the first one. Kept on the device under the same key iOS
    // reads from UserDefaults.
    val introSeen = rememberIntroSeen(IntroSeenKey.STAY_WITH_ME)

    // Whether the care read has finished, or has had long enough that waiting further would
    // just be a spinner. It cannot be `careState !is Loading` alone: a local-only account
    // has no cloud to read from, so the refresh fails and the state never leaves `Loading`.
    // That left this screen spinning for ever instead of explaining what a walk is.
    var careReadTimedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(CareReadGraceMs)
        careReadTimedOut = true
    }
    val careSettled = care.careState !is CareRuntimeState.Loading || careReadTimedOut

    // The walk ended while this was open: close. Arriving cold from a notification, give the
    // first read a few seconds before deciding there is nothing to show. Two exceptions,
    // both of which are screens rather than nothing: her own side, where with no walk this
    // IS where she starts one, and someone with nobody on Be Her Sakhi yet, who gets the
    // intro explaining what a walk is instead of a screen that closes itself.
    LaunchedEffect(mine == null && watching == null, sawWalk, owner != null, partner != null) {
        if (mine == null && watching == null && owner == null && partner != null) {
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
            checkInRequested = state.checkInRequested,
            checkInChecking = state.checkInChecking,
            checkInFailed = state.checkInFailed,
            onCheckInOkay = viewModel::confirmCheckIn,
        )
        // Past her time by his own delay, and she has not said she is home. This takes the
        // whole screen with the alarm going, and he has to slide it away. The rule for when
        // is `StayWithMeAlarm`, shared with iOS.
        watching != null && partner != null && state.alarmRaised -> StayWithMeNotReachedScreen(
            session = watching,
            personName = careDisplayName(partner.partnership, isPartnerRole = true),
            faceIndex = CareAvatars.indexFor(watching.ownerUserId),
            now = state.now,
            trail = state.watchingTrail,
            onAcknowledged = viewModel::acknowledgeAlarm,
            onClose = onClose,
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
        //
        // What a walk is comes first, once. After that the start screen opens straight away.
        owner != null && !introSeen.seen -> StayWithMeIntro(
            personName = careDisplayName(owner.partnership, isPartnerRole = false),
            onPrimary = introSeen::markSeen,
            onNotNow = onClose,
            onClose = onClose,
        )
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
        // Nobody on Be Her Sakhi yet. The same screen still explains what a walk is, and
        // its one action opens the Care screen, where she can add someone if she wants to.
        // Nothing here asks twice: adding a person means sharing where she is with them,
        // and that choice is hers alone.
        // Held back until the care read has settled, so the intro cannot flash over a
        // connection that is about to arrive. The spinner below covers that moment.
        careSettled && partner == null -> StayWithMeIntro(
            personName = null,
            onPrimary = {
                introSeen.markSeen()
                onAddCarePartner()
            },
            onNotNow = onClose,
            onClose = onClose,
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
 * What a walk is, once, before the first one. Drawn with the app's intro template, the same
 * screen Care and Sakhi AI open on.
 *
 * [personName] is null for someone who has nobody on Be Her Sakhi yet. The screen then says
 * the same things in the third person and its one action opens the Care screen, where she
 * can add someone if she wants to. It is an explanation with a door, not a request: adding
 * a person means sharing where she is with them, and that decision is hers alone.
 */
@Composable
private fun StayWithMeIntro(
    personName: String?,
    onPrimary: () -> Unit,
    onNotNow: () -> Unit,
    onClose: () -> Unit,
) {
    SakhiOnboardingView(
        // iOS `figure.walk.motion`.
        icon = Icons.Filled.DirectionsWalk,
        title = stringResource(R.string.stay_with_me_intro_title),
        message = if (personName != null) {
            stringResource(R.string.stay_with_me_intro_message, personName)
        } else {
            stringResource(R.string.stay_with_me_intro_no_partner_message)
        },
        points = listOf(
            SakhiOnboardingPoint(
                icon = Icons.Filled.Map,
                title = stringResource(R.string.stay_with_me_intro_point_1_title),
                detail = stringResource(R.string.stay_with_me_intro_point_1_detail),
            ),
            SakhiOnboardingPoint(
                icon = Icons.Filled.CheckCircle,
                title = stringResource(R.string.stay_with_me_intro_point_2_title),
                detail = if (personName != null) {
                    stringResource(R.string.stay_with_me_intro_point_2_detail, personName)
                } else {
                    stringResource(R.string.stay_with_me_intro_no_partner_point_2_detail)
                },
            ),
            SakhiOnboardingPoint(
                icon = Icons.Filled.AccessAlarm,
                title = stringResource(R.string.stay_with_me_intro_point_3_title),
                detail = if (personName != null) {
                    stringResource(R.string.stay_with_me_intro_point_3_detail, personName)
                } else {
                    stringResource(R.string.stay_with_me_intro_no_partner_point_3_detail)
                },
            ),
        ),
        primaryLabel = if (personName != null) {
            stringResource(R.string.stay_with_me_intro_primary)
        } else {
            stringResource(R.string.stay_with_me_intro_no_partner_primary)
        },
        onPrimaryClick = onPrimary,
        secondaryLabel = stringResource(R.string.stay_with_me_intro_secondary),
        onSecondaryClick = onNotNow,
        onClose = onClose,
    )
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
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().background(RideStyle.ground)) {
        WalkMap(
            location = here,
            accent = MaterialTheme.colorScheme.primary,
            initial = "",
            modifier = Modifier.fillMaxSize(),
            bottomPadding = 320.dp,
        )
        RideTopBar(
            onClose = onClose,
            freshness = null,
            onCallPolice = { dial(context, "112") },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
            color = RideStyle.ground,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 5.dp)
                            .background(RideStyle.hairline, CircleShape),
                    )
                }
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

/**
 * How long this screen waits for care state before it stops waiting.
 *
 * A signed-in phone answers in well under this. A local-only account never answers at all,
 * because there is no cloud behind it, and that is the case this number exists for.
 */
private const val CareReadGraceMs = 2_500L
