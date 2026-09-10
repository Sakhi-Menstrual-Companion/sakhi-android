package team.sakhi.android.feature.emergency

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import team.sakhi.emergency.EmergencySafePlace
import team.sakhi.models.EmergencyRequirement
import team.sakhi.android.designsystem.sakhiGroupedBackground
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import com.google.android.gms.maps.model.LatLng
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.emergency.EmergencyState
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSeparator
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.lifecycle.repeatOnLifecycle

/** Where she is inside the safe-places browser. iOS `EmergencySheetContent.PlacesRoute`. */
private sealed interface PlacesRoute {
    data object None : PlacesRoute
    data object List : PlacesRoute
    data class Detail(val place: EmergencySafePlace) : PlacesRoute
}

/**
 * Root of Emergency Assistance on Android. Draws whatever step the shared
 * [team.sakhi.emergency.EmergencyStore] reports.
 *
 * This screen owns no step logic — it does not know that picking a requirement leads to
 * picking a spot. KMM decides that, which is what lets a request accepted while the app
 * was backgrounded resume on the correct screen, and what keeps this identical to iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyFlowScreen(
    onClose: () -> Unit,
    deepLinkRequestId: String? = null,
    openResponderInbox: Boolean = false,
    viewModel: EmergencyViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val nearbyCount by viewModel.nearbyAvailableCount.collectAsStateWithLifecycle()
    val nearbySakhis by viewModel.nearbySakhis.collectAsStateWithLifecycle()
    val hasSeenIntro by viewModel.hasSeenIntro.collectAsStateWithLifecycle()
    val responder by viewModel.responderState.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val isSearchingPlaces by viewModel.isSearchingPlaces.collectAsStateWithLifecycle()
    val lastCoordinate by viewModel.lastCoordinate.collectAsStateWithLifecycle()
    val profileDetail by viewModel.profileDetail.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Android-local on purpose: browsing places changes nothing about her request, and
    // putting it in the shared state machine would mean a woman who opened the list to find
    // a washroom had "moved" in a flow the server is tracking.
    var placesRoute by remember { mutableStateOf<PlacesRoute>(PlacesRoute.None) }
    var pendingRequirement by remember { mutableStateOf<EmergencyRequirement?>(null) }
    var showMyProfile by remember { mutableStateOf(false) }
    var showFacePicker by remember { mutableStateOf(false) }

    // The requests addressed to her, as a sheet that can sit over any step.
    //
    // Opens on its own when a request she has not seen yet arrives, so a woman who is asked
    // while Emergency is open does not have to know to go looking for it. Opens at once when
    // she came here from Home's red ring or from the notification.
    var showInbox by remember { mutableStateOf(openResponderInbox) }
    var seenRequestIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Bumped when she picks a face, so the profile and the header avatar both re-read the
    // preference. It is stored in UserDefaults-style local prefs, which Compose cannot
    // observe on its own.
    var faceRevision by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        viewModel.onLocationPermissionResult(granted.values.any { it })
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    LaunchedEffect(deepLinkRequestId) {
        if (deepLinkRequestId != null) {
            viewModel.openSession(deepLinkRequestId)
        } else {
            viewModel.restore()
        }
    }

    if (!hasSeenIntro) {
        EmergencyOnboarding(
            locationGranted = uiState.hasLocationPermission,
            notificationsGranted = uiState.hasNotificationPermission,
            onRequestLocation = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                )
            },
            onRequestNotifications = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Pre-13 has no runtime notification permission; it is granted at install.
                    viewModel.onNotificationPermissionResult(true)
                }
            },
            onFinished = viewModel::markIntroSeen,
            onCancel = {
                viewModel.dismiss()
                onClose()
            },
        )
        return
    }

    // Keep what is live for her fresh while Emergency is on screen. This is also the only
    // thing that notices a request landing mid-flow: requests addressed to her are polled,
    // not pushed, because she has no read on the row until she accepts.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refreshHomeSignal()
                kotlinx.coroutines.delay(IncomingPollMillis)
            }
        }
    }

    LaunchedEffect(responder.incoming) {
        val ids = responder.incoming.map { it.requestId }.toSet()
        if ((ids - seenRequestIds).isNotEmpty()) showInbox = true
        seenRequestIds = ids
    }

    // Once she has accepted, the session is the screen; the inbox has done its job.
    LaunchedEffect(state) {
        if (state is EmergencyState.InSession) showInbox = false
    }

    // Restore lands on Idle when she has nothing in flight, which is the point to start a
    // fresh request — that is what tapping the map button asked for. Not when she came
    // from an incoming-request push, though: she is here to answer, not to ask.
    LaunchedEffect(state, hasSeenIntro) {
        if (hasSeenIntro && deepLinkRequestId == null && !openResponderInbox && state is EmergencyState.Idle) {
            viewModel.begin()
        }
    }

    // Only once she is past the introduction. The intro's own Location Access toggle is
    // what asks first; prompting behind an explainer she has not read yet inverts the
    // order the screens were designed in.
    LaunchedEffect(hasSeenIntro) {
        if (hasSeenIntro && !uiState.hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    // Map first, sheet over it — the shape of the original screen, and of iOS. The sheet
    // is non-modal on purpose: `main`'s most recognisable detail is that it does not dim
    // the map, which stays visible and pannable behind every step.
    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true,
        ),
    )

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = SheetPeekHeight,
        // Material's default handle carries 22dp of padding above and below its 4dp bar --
        // 48dp of dead space before any step's content starts. iOS's grabber is the same
        // 38x4 capsule with 10dp either side (see `SheetSurface`), which is half that. The
        // gap between the grabber and the content was Material's padding, not the steps'.
        sheetDragHandle = { EmergencySheetGrabber() },
        sheetContainerColor = MaterialTheme.colorScheme.background,
        // 40, from Figma. Every frame in `13 · Emergency Assistance` draws the sheet with
        // `rounded-t-[40px]`; 28 was Material's own bottom-sheet corner carried over.
        sheetShape = RoundedCornerShape(topStart = SheetCornerRadius, topEnd = SheetCornerRadius),
        sheetContent = {
        // The demo switch is an OVERLAY, never a sibling. iOS attaches it with
        // `.overlay(alignment: .topTrailing)`, which takes no space in the layout. As a
        // Column child it occupied a row of its own and pushed every step's content down,
        // so a debug-only control was changing the spacing of the screens it exists to let
        // you look at.
        Box(modifier = Modifier.fillMaxSize()) {
        // `Loading` never gets a screen of its own. `restore()` passes through it every time
        // the flow opens, so giving it a step meant a spinner flashed in and the picker
        // animated in over it -- and reopening a live session flashed the spinner over the
        // session. While loading, the last settled step stays up and nothing moves.
        var lastSettled by remember { mutableStateOf<EmergencyState?>(null) }
        if (state !is EmergencyState.Loading) lastSettled = state
        val shown = if (state is EmergencyState.Loading) lastSettled ?: state else state

        AnimatedContent(
            targetState = shown.stepKey(),
            transitionSpec = {
                (
                    slideInVertically(animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f)) { it / 12 } +
                        fadeIn()
                    ).togetherWith(fadeOut())
            },
            label = "emergency-step",
            modifier = Modifier.fillMaxSize(),
        ) { key ->
            // The layer that is on screen reads the LIVE state; only the layer on its way out
            // keeps the snapshot it had, so it does not redraw as the new step.
            //
            // This was `remember(key) { state }` for both, which froze every step at the
            // moment it first appeared. A step whose state changes without its key changing
            // -- the Sakhi list filling in after "Looking for Sakhis…", say -- never showed
            // the change, and sat on its first frame for ever.
            val snapshot = remember(key) { shown }
            val current = if (key == shown.stepKey()) shown else snapshot
            when (current) {
                is EmergencyState.Loading -> EmergencyLoading()

                is EmergencyState.Idle,
                is EmergencyState.ChoosingRequirement,
                -> EmergencyRequirementStep(
                    // Always the picker now, matching iOS. There used to be a branch here
                    // showing "No Nearby Sakhis" whenever `nearbyCount` was 0. That empty
                    // state is gone: it made the app's answer depend on whether somebody
                    // happened to be online, and it was a dead end with a Search Again
                    // button on it. The picker leads to the places she can walk to either
                    // way, so there is nothing it was still telling her.
                    viewModel = viewModel,
                    onShowPlaces = { requirement ->
                        pendingRequirement = requirement
                        placesRoute = PlacesRoute.List
                    },
                    myUserId = viewModel.currentUserId,
                    onOpenMyProfile = { showMyProfile = true },
                    faceRevision = faceRevision,
                )

                is EmergencyState.ChoosingSpot -> EmergencySpotStep(
                    viewModel = viewModel,
                    requirement = current.requirement,
                )

                is EmergencyState.ChoosingSakhi -> EmergencyNearbySakhisStep(
                    viewModel = viewModel,
                    step = current,
                )

                is EmergencyState.WaitingForAcceptance -> EmergencyWaitingStep(
                    viewModel = viewModel,
                    step = current,
                )

                is EmergencyState.Rejected -> EmergencyRejectedStep(
                    viewModel = viewModel,
                    step = current,
                    onExit = onClose,
                )

                is EmergencyState.InSession -> EmergencySessionStep(
                    viewModel = viewModel,
                    step = current,
                )

                is EmergencyState.Completed -> EmergencyFeedbackStep(
                    viewModel = viewModel,
                    session = current.session,
                    onDone = onClose,
                )

                is EmergencyState.Failed -> EmergencyError(
                    message = current.message,
                    onRetry = viewModel::begin,
                )
            }
        }

        // The five faces, as a dialog rather than a second sheet over the profile.
        if (showFacePicker) {
            val userId = viewModel.currentUserId
            if (userId != null) {
                EmergencyFacePickerDialog(
                    userId = userId,
                    onPicked = {
                        showFacePicker = false
                        faceRevision++
                    },
                    onDismissRequest = { showFacePicker = false },
                )
            }
        }

        // The places browser sits OVER the step it was opened from rather than replacing
        // it, so closing it puts her back exactly where she was. Same shape as iOS's
        // `EmergencySheetContent`, which switches on its own `placesRoute` above the steps.
        when (val route = placesRoute) {
            is PlacesRoute.List -> {
                LaunchedEffect(Unit) {
                    viewModel.loadPlaces()
                    // The people, alongside the places. Both lists are on this one sheet now.
                    viewModel.loadNearbySakhis()
                }
                // The sheet's own blush ground, not `sakhiGroupedBackground()`'s grey.
                // These two screens sit inside the same sheet as the step behind them, and
                // pushing to them visibly changed the colour of the sheet.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    EmergencyNearbyPlaces(
                        places = places,
                        sakhis = nearbySakhis,
                        isSearching = isSearchingPlaces,
                        hasLocation = lastCoordinate != null,
                        onBack = { placesRoute = PlacesRoute.None },
                        // Asking from here goes straight through the state machine: the
                        // requirement she tapped to get to this sheet is carried into
                        // `ChoosingSakhi`, and the request goes out to the woman she picked.
                        onAskSakhi = { sakhi ->
                            placesRoute = PlacesRoute.None
                            pendingRequirement?.let { viewModel.askFromNearby(it, sakhi) }
                        },
                        onOpenSakhiProfile = { sakhi -> viewModel.openProfile(sakhi.userId) },
                        onSelect = { placesRoute = PlacesRoute.Detail(it) },
                    )
                }
            }

            is PlacesRoute.Detail -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                EmergencyPlaceDetail(
                    place = route.place,
                    onBack = { placesRoute = PlacesRoute.List },
                    onDirections = { place -> openPlaceDirections(context, place) },
                    onCall = { number -> dialPlaceNumber(context, number) },
                )
            }

            PlacesRoute.None -> Unit
        }

        // Requests addressed to her -- the requester's face, what she needs, and Accept /
        // Decline. Lives here rather than inside one step so it can appear over any of them.
        if (showInbox) {
            ModalBottomSheet(
                onDismissRequest = { showInbox = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topStart = SheetCornerRadius, topEnd = SheetCornerRadius),
                scrimColor = Color.Transparent,
                dragHandle = { EmergencySheetGrabber() },
            ) {
                EmergencyResponderInbox(viewModel = viewModel)
            }
        }

        // Her own profile, opened from the avatar beside "What do you need?". The face
        // picker lives inside it now, as Figma `EA-02` has it, rather than pushing a
        // second sheet for a one-tap choice.
        if (showMyProfile) {
            val userId = viewModel.currentUserId
            if (userId != null) {
                ModalBottomSheet(
                    onDismissRequest = { showMyProfile = false },
                    // The flow's own blush ground, not `sakhiGroupedBackground()`'s grey:
                    // this is the same sheet as every other step, opened taller.
                    containerColor = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(
                        topStart = SheetCornerRadius,
                        topEnd = SheetCornerRadius,
                    ),
                    // No scrim, matching the rest of this flow. Karan's standing note on
                    // Emergency is that the map never dims behind a sheet, and Figma
                    // `EA-02` shows the map at its normal weight behind this one too.
                    scrimColor = Color.Transparent,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    // The map sheet's own grabber, so the two read as one sheet rather than
                    // this one arriving with Material's 48dp-tall handle.
                    dragHandle = { EmergencySheetGrabber() },
                    contentWindowInsets = { WindowInsets(0) },
                ) {
                    LaunchedEffect(userId) { viewModel.openProfile(userId) }
                    EmergencyMyProfileSheet(
                        userId = userId,
                        profile = profileDetail,
                        onOpenFacePicker = { showFacePicker = true },
                        onClose = { showMyProfile = false },
                        // A fraction, not a fixed dp: iOS opens this one on
                        // `EmergencySheetDetents.profileFraction` (0.82) rather than the
                        // flow's usual detent, because the stats and the face row together
                        // are taller than a 502 sheet. Material sizes a modal sheet to its
                        // content, so the height goes on the content.
                        modifier = Modifier
                            .fillMaxHeight(ProfileSheetFraction)
                            .navigationBarsPadding(),
                    )
                }
            }
        }

        }
        },
    ) { padding ->
        // The scaffold's `padding` reserves the whole peek height at the bottom. Applying
        // it here would stop the map dead at the sheet's top edge, and since the scaffold
        // background is the same colour as the sheet, the sheet's 28dp corners would sit
        // against an identical colour and read as square. The map runs the full height
        // instead, exactly as it does on iOS, and the sheet floats over it.
        Box(modifier = Modifier.fillMaxSize()) {
            EmergencyMap(
                userLocation = uiState.userLatLng,
                pins = state.mapPins(uiState.userLatLng, responder.incoming),
                pulseRadiusMeters = state.pulseRadiusMeters(),
                bottomInset = SheetPeekHeight,
            )

            // Figma `mute overlay`: the page background at 38% over the whole map. Google's
            // default tiles are far more saturated than Apple's, so without this the map
            // read as the loudest thing on a screen whose subject is the sheet in front of
            // it. Carries no pointer modifier of its own, so it is not a hit target and
            // panning and the pins underneath still work.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MapMuteOverlay),
            )

            // The sheet's own lift off the map, drawn rather than elevated.
            //
            // Figma gives every frame's `Sheet` `shadow-[0px_-2px_16px_rgba(0,0,0,0.12)]`.
            // `sheetShadowElevation` cannot produce that: Android's light comes from above,
            // so almost all of an elevation shadow falls BELOW the object, and a bottom
            // sheet's bottom is off-screen. Measured on Karan's phone, 16dp of elevation put
            // a band 3% darker than the map above the edge and 32dp barely improved on it,
            // against the 12% the frame asks for.
            //
            // So: a gradient tied to the sheet's live offset, which means it still hugs the
            // edge while she drags. Its bottom corners are cut to the sheet's own 40dp
            // radius, so no shadow spills over the map where the sheet is not.
            val sheetTop = runCatching {
                sheetState.bottomSheetState.requireOffset()
            }.getOrNull()
            if (sheetTop != null) {
                val density = LocalDensity.current
                val bandPx = with(density) { SheetShadowBand.roundToPx() }
                // Slid down so its darkest edge tucks under the sheet rather than sitting
                // on the map. Karan: "thodi jada bahar hogayi hai, halki si niche lao."
                val tuckPx = with(density) { SheetShadowTuck.roundToPx() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Tall enough for the corner, not just for the shadow. Compose
                        // clamps a corner radius to half the shorter side, so an 18dp-tall
                        // strip could only round to 9 and left a notch at each end instead
                        // of following the sheet's 40dp sweep -- Karan called it "ajeeb".
                        // The band is this tall and the gradient stays transparent for all
                        // but its last 18dp.
                        .height(SheetShadowBand)
                        .offset { IntOffset(0, sheetTop.roundToInt() - bandPx + tuckPx) }
                        .clip(
                            RoundedCornerShape(
                                bottomStart = SheetCornerRadius,
                                bottomEnd = SheetCornerRadius,
                            ),
                        )
                        .background(
                            // Eased, not a straight ramp: most of the visible band stays
                            // under 4% and only the last few pixels reach 10%, so it reads
                            // as the map falling away under the sheet rather than as a drawn
                            // edge. Karan's note -- "thodi soft he rakhna".
                            Brush.verticalGradient(
                                0.00f to Color.Transparent,
                                SheetShadowStart to Color.Transparent,
                                0.90f to Color.Black.copy(alpha = 0.03f),
                                0.97f to Color.Black.copy(alpha = 0.07f),
                                1.00f to Color.Black.copy(alpha = 0.10f),
                            ),
                        ),
                )
            }

            EmergencyMapOverlay(
                // ACTION_DIAL, not ACTION_CALL: it opens the dialer with 112 already in it
                // and waits for her to press call. iOS gets the same guard for free -- the
                // system confirms a `tel:` URL before dialling -- and it is the right one
                // here, because a brush against a button on a map must not place a call to
                // the emergency services.
                onContactPolice = { dialPlaceNumber(context, PoliceEmergencyNumber) },
                onBack = {
                    viewModel.dismiss()
                    onClose()
                },
                modifier = Modifier.align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = padding.calculateTopPadding()),
            )

        }
    }
}

/**
 * Figma `EA-01 · Requirement picker` -> `mute overlay`: `rgba(248, 242, 244, 0.38)`, which
 * is the brand page background over the map at 38%.
 */
private val MapMuteOverlay = Color(0x61F8F2F4)

/**
 * How much of the screen the sheet occupies at rest. Shared by the scaffold and by the
 * map's camera inset, which have to agree: the map draws behind the sheet, so the camera
 * needs to know how much of it is actually visible.
 */
/**
 * The one height every sheet in this flow opens at, taken from the Figma frame
 * "EA-01 · Requirement picker": a 502 sheet on an 874 screen.
 *
 * His phone is 1080x2400 at 2.75 density, so ~873dp tall -- the same figure the frame was
 * drawn against, which is why this is an absolute dp rather than a fraction.
 */
private val SheetPeekHeight = 502.dp

/**
 * The taller sheet her own profile opens at.
 *
 * iOS `EmergencySheetDetents.profileFraction`. Everything else in the flow opens at the
 * one shared height; this screen is the exception both platforms make, because her stats
 * and the face row together do not fit in it.
 */
private const val ProfileSheetFraction = 0.82f

/**
 * How often the flow re-asks for requests addressed to her. Short: a request is only
 * answerable for two minutes, and she should see it within seconds of it landing.
 */
private const val IncomingPollMillis = 6_000L

/** Figma draws every sheet in this flow with `rounded-t-[40px]`. */
private val SheetCornerRadius = 40.dp

/**
 * The band the sheet's shadow is drawn in.
 *
 * Twice the corner radius, so the 40dp round is never clamped. Only the last
 * [SheetShadowStart] of it is anything but transparent, which is Figma's `0px -2px 16px`.
 */
private val SheetShadowBand = SheetCornerRadius * 2

/** Where the gradient stops being transparent -- the last 18dp of [SheetShadowBand]. */
private const val SheetShadowStart = 1f - 18f / 80f

/**
 * How far the band is slid down behind the sheet.
 *
 * The darkest few pixels end up under the sheet's own surface, so what shows above the edge
 * is only the soft part of the falloff.
 */
private val SheetShadowTuck = 7.dp

/**
 * Where each step's pins come from.
 *
 * Pre-accept these are placed from (bucketed distance, 45°-snapped bearing) around her
 * own position, so they are estimates by construction. Inside an accepted session the
 * server gives real coordinates to both women, and that pin is exact.
 */
private fun EmergencyState.mapPins(
    origin: LatLng?,
    incoming: List<team.sakhi.models.IncomingRequest>,
): List<EmergencyMapPin> {
    if (origin == null) return emptyList()

    return when (this) {
        is EmergencyState.ChoosingSakhi -> sakhis.mapNotNull { sakhi ->
            val bearing = sakhi.approximateBearingDegrees?.toDouble() ?: return@mapNotNull null
            val bucket = sakhi.distanceBucketMeters?.toDouble() ?: return@mapNotNull null
            EmergencyMapPin(
                id = sakhi.userId,
                position = origin.offset(bucket, bearing),
                title = sakhi.name,
                isApproximate = true,
            )
        }

        // Nothing to draw. The one Sakhi she asked has not agreed yet, so the server
        // gives out no position for her, not even a bucket.
        is EmergencyState.WaitingForAcceptance -> emptyList()

        is EmergencyState.InSession -> listOf(
            EmergencyMapPin(
                id = session.requestId,
                position = LatLng(session.location.latitude, session.location.longitude),
                title = if (session.viewerIsRequester) session.responderName else session.requesterName,
                // Only here, matching iOS `EmergencyMapScreen.swift:103`. The exact spot is
                // the point of the pin once someone has accepted; before that the server
                // gives out no position worth labelling.
                spotLabel = session.spotLabel,
                isApproximate = false,
            ),
        )

        // Her inbox: one pin per woman who has asked her.
        else -> incoming.mapNotNull { request ->
            val bearing = request.approximateBearingDegrees?.toDouble() ?: return@mapNotNull null
            val bucket = request.distanceBucketMeters?.toDouble() ?: return@mapNotNull null
            EmergencyMapPin(
                id = request.requestId,
                position = origin.offset(bucket, bearing),
                title = request.requesterName,
                isApproximate = true,
            )
        }
    }
}

/** The original drew a red pulse over the user while a request was live. */
private fun EmergencyState.pulseRadiusMeters(): Double? = when (this) {
    is EmergencyState.WaitingForAcceptance, is EmergencyState.ChoosingSakhi -> 120.0
    is EmergencyState.InSession -> 60.0
    else -> null
}

/**
 * Animating on the state object itself would restart the transition every time the nearby
 * list refreshes. Keying on step identity keeps it to real step changes.
 */
private fun EmergencyState.stepKey(): String = when (this) {
    // One key for both. They draw the same picker, and as two keys opening the flow ran
    // the step animation twice -- Idle, then ChoosingRequirement a beat later -- which is
    // the "what do you need jumps two times" Karan saw.
    is EmergencyState.Idle -> "requirement"
    is EmergencyState.Loading -> "loading"
    is EmergencyState.ChoosingRequirement -> "requirement"
    is EmergencyState.ChoosingSpot -> "spot"
    is EmergencyState.ChoosingSakhi -> "choosing-sakhi"
    is EmergencyState.WaitingForAcceptance -> "waiting"
    is EmergencyState.Rejected -> "rejected"
    is EmergencyState.InSession -> "session"
    is EmergencyState.Completed -> "completed"
    is EmergencyState.Failed -> "failed"
}

@Composable
internal fun EmergencyLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.emergency_one_moment),
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
            )
        }
    }
}


/** Hands the walk to whatever maps app she uses, rather than drawing a route in-app. */
private fun openPlaceDirections(context: android.content.Context, place: EmergencySafePlace) {
    val uri = android.net.Uri.parse(
        "google.navigation:q=${place.latitude},${place.longitude}&mode=w",
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
    runCatching { context.startActivity(intent) }.onFailure {
        // No maps app: fall back to a browser rather than failing silently on the one
        // screen whose whole point is getting her somewhere.
        val web = android.net.Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=" +
                "${place.latitude},${place.longitude}&travelmode=walking",
        )
        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, web)) }
    }
}

/** Opens the dialler pre-filled, never places the call itself. */
private fun dialPlaceNumber(context: android.content.Context, number: String) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_DIAL,
        android.net.Uri.parse("tel:$number"),
    )
    runCatching { context.startActivity(intent) }
}
