package team.sakhi.android.feature.emergency

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    val hasSeenIntro by viewModel.hasSeenIntro.collectAsStateWithLifecycle()
    val responder by viewModel.responderState.collectAsStateWithLifecycle()

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
        sheetDragHandle = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(38.dp)
                        .height(4.dp)
                        .background(
                            sakhiSeparator(),
                            RoundedCornerShape(percent = 50),
                        ),
                )
            }
        },
        sheetContainerColor = MaterialTheme.colorScheme.background,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetContent = {
        // The demo switch is an OVERLAY, never a sibling. iOS attaches it with
        // `.overlay(alignment: .topTrailing)`, which takes no space in the layout. As a
        // Column child it occupied a row of its own and pushed every step's content down,
        // so a debug-only control was changing the spacing of the screens it exists to let
        // you look at.
        Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state.stepKey(),
            transitionSpec = {
                (
                    slideInVertically(animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f)) { it / 12 } +
                        fadeIn()
                    ).togetherWith(fadeOut())
            },
            label = "emergency-step",
            modifier = Modifier.fillMaxSize(),
        ) { key ->
            // Read the live value rather than closing over the animated key, so a list
            // refresh mid-step updates in place instead of animating the whole screen.
            when (val current = remember(key) { state }) {
                is EmergencyState.Loading -> EmergencyLoading()

                is EmergencyState.Idle,
                is EmergencyState.ChoosingRequirement,
                -> if (nearbyCount == 0 && !openResponderInbox) {
                    // main's presentNoActiveRequestBottomSheet(): empty state when nobody
                    // is around, picker otherwise. Null keeps showing the picker rather
                    // than flashing an empty state while the count is still loading.
                    //
                    // Skipped when she arrived from a push telling her someone asked her
                    // for help: the inbox is hosted by the requirement step, so showing
                    // the empty state here would swallow the notification entirely.
                    EmergencyNoNearbyStep(viewModel = viewModel)
                } else {
                    EmergencyRequirementStep(
                        viewModel = viewModel,
                        startOnResponderInbox = openResponderInbox,
                    )
                }

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

            EmergencyMapOverlay(
                nearbyCount = state.overlayCount(nearbyCount ?: 0, responder.incoming.size),
                onBack = {
                    viewModel.dismiss()
                    onClose()
                },
                modifier = Modifier.align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = padding.calculateTopPadding()),
            )

            // Debug-only demo switch. Deliberately plain and slightly ugly: it must never be
            // mistaken for product UI in a screenshot. Turning it off mid-request hands the
            // flow straight back to the store.
            //
            // It lives over the MAP rather than in the sheet, and that is the whole point.
            // iOS can put it at the sheet's top-trailing because its Back/Next sit in the
            // real navigation bar, above the content the overlay attaches to. Android draws
            // each step's bar inside the sheet, and every step's bar is a different height,
            // so any fixed offset inside the sheet covers a real control on some step -- it
            // sat on "Next" on Location, then on the first Sakhi's row on the picker. The
            // map is the one surface that is identical on every step.
            if (BuildConfig.DEBUG) {
                var demoOn by remember { mutableStateOf(viewModel.isDemoModeEnabled()) }
                Text(
                    text = if (demoOn) "DEMO ON" else "DEMO OFF",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        // Below the Nearby-count chip that `EmergencyMapOverlay` draws.
                        .padding(top = padding.calculateTopPadding() + 76.dp, end = 12.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (demoOn) Color(0xFF34C759) else Color(0xFFAEAEB2))
                        .clickable {
                            demoOn = !demoOn
                            viewModel.setDemoModeEnabled(demoOn)
                            if (!demoOn) viewModel.refreshNearbySakhis()
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * How much of the screen the sheet occupies at rest. Shared by the scaffold and by the
 * map's camera inset, which have to agree: the map draws behind the sheet, so the camera
 * needs to know how much of it is actually visible.
 */
private val SheetPeekHeight = 420.dp

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
 * What the "Nearby Sakhis: N" pill counts, which changes with the step exactly as it did
 * on `main`: the women she can pick from while choosing, the women who have asked her when
 * she is the helper, and otherwise how many Sakhis are simply around.
 */
private fun EmergencyState.overlayCount(availableNearby: Int, incomingCount: Int): Int = when (this) {
    is EmergencyState.ChoosingSakhi -> sakhis.size
    else -> if (incomingCount > 0) incomingCount else availableNearby
}

/**
 * Animating on the state object itself would restart the transition every time the nearby
 * list refreshes. Keying on step identity keeps it to real step changes.
 */
private fun EmergencyState.stepKey(): String = when (this) {
    is EmergencyState.Idle -> "idle"
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
