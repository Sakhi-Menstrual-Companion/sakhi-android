package team.sakhi.android.feature.care

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.datetime.toInstant
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.rememberSakhiFlingBehavior
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.feature.onboarding.OnboardingFlowHost
import team.sakhi.android.ui.GlassCard
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.PartnerAvatarCloud
import team.sakhi.android.ui.SakhiAlertKind
import team.sakhi.android.ui.SakhiAlertSheet
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.android.ui.SakhiModalSheet
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.android.ui.SakhiNavBar
import team.sakhi.android.ui.SakhiSwitch
import team.sakhi.android.ui.SheetSurface
import team.sakhi.android.ui.ToastManager
import team.sakhi.android.ui.ToastType
import team.sakhi.care.CareRuntimeState
import team.sakhi.date.DateConverter
import team.sakhi.models.CarePartnership
import team.sakhi.models.ParentChildPermissions
import team.sakhi.models.PartnerInvitation
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiLightPink
import androidx.compose.ui.graphics.Brush
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.common.toSafeUserMessage

/**
 * "Be Her Sakhi" care hub — mirrors iOS's `CareModeSettingsView` routing: one
 * state (`CareRuntimeState`), one render, no flash. Real iOS destinations ported:
 * `PartnerDetailView` (connected), `PendingPartnerWaitingView` (pending invite),
 * `PartnerPermissionsEditView` (permission toggles). The disconnected state keeps
 * Android now uses the same owner-side invite onboarding flow iOS launches from
 * the disconnected hub (`OnboardingFlowView(flow: .carePartnerInvite)`), while
 * still preserving the deep-link prefill accept-code path as a separate
 * disconnected route.
 */
@Composable
fun CareScreen(
    prefillInviteCode: String? = null,
    viewModel: CareViewModel = koinViewModel(),
    onClose: () -> Unit = {},
    /**
     * A walk is live on either side. The host swaps this sheet for the full-screen walk
     * ([StayWithMeLiveLayer]), the way Emergency Assistance is full screen rather than a
     * pane inside a sheet.
     */
    onOpenLiveWalk: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticManager = koinInject<AndroidHapticManager>()
    val stayWithMeViewModel: StayWithMeViewModel = koinViewModel()
    val stayWithMe by stayWithMeViewModel.uiState.collectAsStateWithLifecycle()
    // Only while this sheet is on screen: it re-reads the walk, tells her screen that her
    // person is looking, and notices a walk that has gone past its time.
    DisposableEffect(Unit) {
        stayWithMeViewModel.onVisible()
        onDispose { stayWithMeViewModel.onHidden() }
    }
    var showPermissionsEdit by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    /** Her side: the duration, the destination and the ask, raised by the footer button. */
    var showStartWalk by remember { mutableStateOf(false) }
    var showOwnerInviteFlow by remember(prefillInviteCode) { mutableStateOf(false) }
    var autoLaunchInviteFlow by remember(prefillInviteCode) {
        mutableStateOf(prefillInviteCode.isNullOrBlank())
    }

    // Deep-link entry (`sakhi://invite/CODE` / `https://sakhi-care.web.app/invite/CODE`
    // / `https://sakhi.com/invite/CODE`) -- pre-fills the accept-code field so the
    // user just has to confirm, matching iOS's AcceptInviteSheet fetching the invite
    // by code immediately on open rather than making the user retype it.
    LaunchedEffect(prefillInviteCode) {
        prefillInviteCode?.let(viewModel::onAcceptInviteCodeChanged)
    }
    // The real things that happened between them: walks her person stayed for, and days
    // they logged for her. Read here, where both are already available, and handed to the
    // screen; nothing on that screen invents a moment of its own.
    val careContext = LocalContext.current
    val walkHistory by stayWithMeViewModel.history.collectAsStateWithLifecycle()
    val periodLogRepository = koinInject<team.sakhi.repositories.PeriodLogRepository>()
    var loggedDays by remember { mutableStateOf<List<Pair<kotlinx.datetime.Instant, String>>>(emptyList()) }

    val ownerConnected = uiState.careState as? CareRuntimeState.OwnerConnected
    val partnerConnected = uiState.careState as? CareRuntimeState.PartnerConnected
    val connectedPartnership = ownerConnected?.partnership ?: partnerConnected?.partnership
    // Her person hears back after they ask, only when it did not go. When it did, the button
    // turns into "Waiting for her to start".
    val askResult by stayWithMeViewModel.askResult.collectAsStateWithLifecycle()
    val askedAt by stayWithMeViewModel.askedAt.collectAsStateWithLifecycle()
    val route by stayWithMeViewModel.route.collectAsStateWithLifecycle()
    // Where this phone is, for the card's map before any walk. Read once, from the last fix
    // the system already has, so opening the screen never switches the GPS on.
    var here by remember { mutableStateOf<team.sakhi.staywithme.StayWithMeLocation?>(null) }
    LaunchedEffect(Unit) {
        here = team.sakhi.android.platform.StayWithMeLocationService.lastKnownLatLng(careContext)?.let { (lat, lng) ->
            team.sakhi.staywithme.StayWithMeLocation(lat, lng, null, null, null, kotlinx.datetime.Clock.System.now())
        }
    }
    LaunchedEffect(askResult) {
        askResult?.let {
            android.widget.Toast.makeText(careContext, it, android.widget.Toast.LENGTH_SHORT).show()
            stayWithMeViewModel.clearAskResult()
        }
    }

    LaunchedEffect(connectedPartnership?.id) {
        connectedPartnership?.id?.let(stayWithMeViewModel::loadHistory)
        connectedPartnership?.id?.let(stayWithMeViewModel::loadPartnerCard)
    }
    val partnerCard by stayWithMeViewModel.partnerCard.collectAsStateWithLifecycle()
    val sessionManager = koinInject<team.sakhi.session.SessionManager>()
    val selfAvatarIndex = remember(sessionManager.current?.userId) {
        CareAvatars.indexFor(sessionManager.current?.userId.orEmpty())
    }
    LaunchedEffect(connectedPartnership?.userId) {
        val userId = connectedPartnership?.userId ?: return@LaunchedEffect
        loggedDays = periodLogRepository.getAll(userId).getOrNull().orEmpty()
            .filter { it.loggedBy == team.sakhi.models.LogSource.PARTNER }
            .sortedByDescending { it.logDate.toString() }
            .take(6)
            .map { log ->
                kotlinx.datetime.LocalDateTime(log.logDate, kotlinx.datetime.LocalTime(12, 0))
                    .toInstant(kotlinx.datetime.TimeZone.currentSystemDefault()) to log.logDate.toString()
            }
    }

    val shouldAutoShowInviteFlow = autoLaunchInviteFlow &&
        prefillInviteCode.isNullOrBlank() &&
        uiState.careState is CareRuntimeState.Disconnected
    val showInviteFlowRoute = (showOwnerInviteFlow || shouldAutoShowInviteFlow) &&
        prefillInviteCode.isNullOrBlank() &&
        (
            uiState.careState is CareRuntimeState.Disconnected ||
                uiState.careState is CareRuntimeState.PendingInvitation ||
                uiState.careState is CareRuntimeState.OwnerConnected
            )

    LaunchedEffect(uiState.careState, autoLaunchInviteFlow) {
        if (autoLaunchInviteFlow && uiState.careState is CareRuntimeState.Disconnected) {
            showOwnerInviteFlow = true
        }
        // Once she is connected the invite flow has nothing left to do, so drop the
        // auto-launch latch.
        //
        // Without this she was shown a dead invite code after her partner had already
        // joined. Care state reads Disconnected for a moment on open, before the care
        // status resolves, which latches the flow on; `showInviteFlowRoute` then keeps
        // rendering it because it accepts OwnerConnected too. Verified on 2026-09-02
        // against a real partnership: the server said owner_connected while the app sat
        // on "Share with your partner", and it survived a force-stop and relaunch.
        //
        // The cost is that someone sitting on the waiting step goes straight to the
        // connected detail screen instead of seeing the connected state on the waiting
        // step first. Being told to share a code that is already used is the worse of
        // the two.
        if (uiState.careState is CareRuntimeState.OwnerConnected ||
            uiState.careState is CareRuntimeState.PartnerConnected
        ) {
            autoLaunchInviteFlow = false
            showOwnerInviteFlow = false
        }
    }

    // iOS presents this as a `.sheet(...).presentationDetents([.large])` with a
    // drag indicator over Home -- SheetSurface gives the same rounded-top +
    // drag-handle look without changing the nav-graph push mechanism itself.
    SheetSurface {
        if (showInviteFlowRoute) {
            OnboardingFlowHost(
                flowId = "carePartnerInvite",
                // Modal flow: iOS shows a close button on the root step. Closes the whole
                // care sheet, matching `requestDismiss(route:)`.
                onDismiss = onClose,
                onFlowCompleted = {
                    showOwnerInviteFlow = false
                    autoLaunchInviteFlow = false
                    if (uiState.careState !is CareRuntimeState.OwnerConnected) {
                        onClose()
                    }
                },
            )
        } else if (showHistory && connectedPartnership != null) {
            PartnerHistoryContent(
                partnership = connectedPartnership,
                isPartnerRole = partnerConnected != null,
                onBack = { showHistory = false },
            )
        } else if (showPermissionsEdit && ownerConnected != null) {
            PartnerPermissionsEditContent(
                partnership = ownerConnected.partnership,
                isSaving = uiState.isSavingPermissions,
                onBack = { showPermissionsEdit = false },
                onSave = { permissions ->
                    viewModel.updatePermissions(ownerConnected.partnership.id, permissions) { saved ->
                        if (saved) showPermissionsEdit = false
                    }
                },
            )
        } else {
            when (val state = uiState.careState) {
                CareRuntimeState.Loading -> LoadingContent()

                CareRuntimeState.Disconnected -> InviteCreationContent(
                    uiState = uiState,
                    onInviteeNameChanged = viewModel::onInviteeNameChanged,
                    onPartnerRelationChanged = viewModel::onPartnerRelationChanged,
                    onCreateInvitation = viewModel::createInvitation,
                    onAcceptInviteCodeChanged = viewModel::onAcceptInviteCodeChanged,
                    onAcceptInvitation = {
                        hapticManager.impact(HapticImpact.MEDIUM)
                        viewModel.acceptInvitation()
                    },
                )

                is CareRuntimeState.PendingInvitation -> PendingInviteContent(
                    invitation = state.invitation,
                    isCancelling = uiState.isCancellingInvite,
                    onCancel = viewModel::cancelInvitation,
                    onClose = onClose,
                )

                is CareRuntimeState.OwnerConnected -> {
                    val walk = stayWithMe.mine
                    val personName = careDisplayName(state.partnership, isPartnerRole = false)
                    val moments = remember(walkHistory, loggedDays, personName) {
                        careMoments(careContext, walkHistory, loggedDays, isPartnerRole = false, otherName = personName)
                    }
                    ConnectedCare(
                        partnership = state.partnership,
                        isPartnerRole = false,
                        isRemoving = uiState.isRemovingPartnership,
                        moments = moments,
                        selfAvatarIndex = selfAvatarIndex,
                        otherAvatarIndex = partnerCard?.avatarIndex ?: 0,
                        serverName = partnerCard?.name,
                        stayState = when {
                            walk != null -> CareStayState.Live
                            stayWithMe.isBusy -> CareStayState.Working
                            else -> CareStayState.Idle
                        },
                        stayLine = if (walk != null) {
                            stringResource(R.string.care_stay_live_owner, personName)
                        } else {
                            stringResource(R.string.care_section_stay_line_owner, personName)
                        },
                        stayIdleLabel = stringResource(R.string.care_swm_ask_button, personName),
                        onStayButton = { showStartWalk = true },
                        mapLocation = walk?.lastLocation ?: here,
                        mapTrail = if (walk != null) stayWithMe.mineTrail else emptyList(),
                        mapDestination = walk?.destination,
                        mapRoute = if (walk != null) route?.points.orEmpty() else emptyList(),
                        mapInitial = "",
                        mapAvatarWithoutName = false,
                        liveWalk = walk?.let { session ->
                            { close ->
                                StayWithMeOwnerLive(
                                    session = session,
                                    personName = personName,
                                    now = stayWithMe.now,
                                    isBusy = stayWithMe.isBusy,
                                    trail = stayWithMe.mineTrail,
                                    places = stayWithMe.places,
                                    placesLoading = stayWithMe.placesLoading,
                                    onArrive = stayWithMeViewModel::arrive,
                                    onExtend = stayWithMeViewModel::extend,
                                    onStop = stayWithMeViewModel::stop,
                                    onClose = close,
                                    onRefresh = stayWithMeViewModel::refreshMyLocation,
                                    route = route,
                                )
                            }
                        },
                        onHistory = { showHistory = true },
                        onManagePermissions = { showPermissionsEdit = true },
                        onRemove = {
                            hapticManager.impact(HapticImpact.MEDIUM)
                            viewModel.removePartnership(state.partnership.id)
                        },
                        onClose = onClose,
                    )
                }

                is CareRuntimeState.PartnerConnected -> {
                    val walk = stayWithMe.watching
                    val herName = careDisplayName(state.partnership, isPartnerRole = true)
                    val moments = remember(walkHistory, loggedDays, herName) {
                        careMoments(careContext, walkHistory, loggedDays, isPartnerRole = true, otherName = herName)
                    }
                    val stayState = when {
                        walk != null -> CareStayState.Live
                        stayWithMe.isBusy -> CareStayState.Working
                        askedAt != null -> CareStayState.Waiting
                        else -> CareStayState.Idle
                    }
                    ConnectedCare(
                        partnership = state.partnership,
                        isPartnerRole = true,
                        isRemoving = uiState.isRemovingPartnership,
                        moments = moments,
                        selfAvatarIndex = selfAvatarIndex,
                        otherAvatarIndex = partnerCard?.avatarIndex ?: 0,
                        serverName = partnerCard?.name,
                        stayState = stayState,
                        stayLine = when (stayState) {
                            CareStayState.Live -> stringResource(R.string.care_stay_live_partner)
                            CareStayState.Waiting -> stringResource(R.string.care_stay_waiting_line)
                            else -> stringResource(R.string.care_section_stay_line_partner)
                        },
                        stayIdleLabel = stringResource(R.string.care_ask_to_stay_with_her),
                        onStayButton = { stayWithMeViewModel.askToStay(state.partnership.id) },
                        // Before she starts, this phone's own position: her person sees a real
                        // map, never where she is until she chooses to share it.
                        mapLocation = walk?.lastLocation ?: here,
                        mapTrail = if (walk != null) stayWithMe.watchingTrail else emptyList(),
                        mapDestination = walk?.destination,
                        mapRoute = if (walk != null) route?.points.orEmpty() else emptyList(),
                        mapInitial = if (walk != null) walkInitials(herName) ?: "" else "",
                        mapAvatarWithoutName = walk != null,
                        liveWalk = walk?.let { session ->
                            { close ->
                                StayWithMeWatcherLive(
                                    session = session,
                                    herName = herName,
                                    now = stayWithMe.now,
                                    places = stayWithMe.places,
                                    placesLoading = stayWithMe.placesLoading,
                                    trail = stayWithMe.watchingTrail,
                                    onClose = close,
                                    onRefresh = stayWithMeViewModel::refreshWalk,
                                    route = route,
                                )
                            }
                        },
                        onHistory = { showHistory = true },
                        onManagePermissions = null,
                        onRemove = {
                            hapticManager.impact(HapticImpact.MEDIUM)
                            viewModel.removePartnership(state.partnership.id)
                        },
                        onClose = onClose,
                    )
                }
            }
        }
    }

    // Her side: duration, where to, and the ask, raised by the footer button rather than
    // sitting in the page. The page is about the two of them; this is the doing.
    if (showStartWalk && ownerConnected != null) {
        SakhiModalSheet(onDismissRequest = { showStartWalk = false }) {
            SheetSurface {
                Column(modifier = Modifier.padding(bottom = SakhiSpacing.space6)) {
                    StayWithMeStartSection(
                        personName = careDisplayName(ownerConnected.partnership, isPartnerRole = false),
                        isBusy = stayWithMe.isBusy,
                        error = stayWithMe.error,
                        onStart = { minutes, note, destination ->
                            stayWithMeViewModel.start(ownerConnected.partnership.id, minutes, note, destination)
                            showStartWalk = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** The other person's name, or a plain stand-in when she has not set one. */
@Composable
internal fun careDisplayName(partnership: CarePartnership, isPartnerRole: Boolean): String {
    val resolved = partnership.partnerName.takeIf { name ->
        name.isNotEmpty() &&
            !name.lowercase().contains("partner") &&
            !name.lowercase().contains("sakhi") &&
            name.lowercase() != "unknown"
    }
    return resolved
        ?: if (isPartnerRole) stringResource(R.string.care_swm_she_fallback) else stringResource(R.string.care_swm_fallback_person)
}

// ── Connected: PartnerDetailView parity ────────────────────────────────────

/** The link between the card's map and the full screen map it opens into. */
private const val CARE_STAY_MAP_KEY = "care-stay-map"

/**
 * The connected screen, and the Stay With Me map growing out of it.
 *
 * Tapping the card's map, or its corner button, opens the map full screen with the card's own
 * bounds animating out to the screen's, and closing it shrinks it back into the card. Both are
 * in one [SharedTransitionLayout] for that reason: the map is the same thing in both places,
 * and it should look like it.
 *
 * A live walk is shown right here, in the card, as it happens. Nothing jumps away from the
 * page when her walk starts; the card turns live and the button opens it.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ConnectedCare(
    partnership: CarePartnership,
    isPartnerRole: Boolean,
    isRemoving: Boolean,
    moments: List<CareMoment>,
    selfAvatarIndex: Int,
    otherAvatarIndex: Int,
    serverName: String?,
    stayState: CareStayState,
    stayLine: String,
    stayIdleLabel: String,
    onStayButton: () -> Unit,
    mapLocation: team.sakhi.staywithme.StayWithMeLocation?,
    mapTrail: List<team.sakhi.staywithme.StayWithMeLocation>,
    mapDestination: team.sakhi.staywithme.StayWithMeDestination?,
    mapRoute: List<Pair<Double, Double>>,
    mapInitial: String,
    mapAvatarWithoutName: Boolean,
    /** The full screen walk, when there is one. Given the way to close back into the card. */
    liveWalk: (@Composable (onClose: () -> Unit) -> Unit)?,
    onHistory: () -> Unit,
    onManagePermissions: (() -> Unit)?,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }
    val listState = rememberLazyListState()
    val accent = MaterialTheme.colorScheme.primary

    SharedTransitionLayout {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
            label = "careStayExpand",
        ) { isExpanded ->
            val sharedMap = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = CARE_STAY_MAP_KEY),
                animatedVisibilityScope = this@AnimatedContent,
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(SakhiRadius.lg)),
            )
            if (!isExpanded) {
                PartnerDetailContent(
                    partnership = partnership,
                    isPartnerRole = isPartnerRole,
                    isRemoving = isRemoving,
                    moments = moments,
                    selfAvatarIndex = selfAvatarIndex,
                    otherAvatarIndex = otherAvatarIndex,
                    serverName = serverName,
                    listState = listState,
                    stayCard = {
                        CareStayCard(
                            state = stayState,
                            title = stringResource(R.string.care_section_stay_with_me),
                            line = stayLine,
                            idleLabel = stayIdleLabel,
                            onButton = onStayButton,
                            onExpand = { expanded = true },
                            mapModifier = sharedMap,
                        ) {
                            WalkMap(
                                location = mapLocation,
                                accent = accent,
                                initial = mapInitial,
                                modifier = Modifier.fillMaxSize(),
                                trail = mapTrail,
                                avatarWithoutName = mapAvatarWithoutName,
                                destination = mapDestination,
                                routeLine = mapRoute,
                                topPadding = 0.dp,
                                interactive = false,
                            )
                        }
                    },
                    onHistory = onHistory,
                    onManagePermissions = onManagePermissions,
                    onRemove = onRemove,
                    onClose = onClose,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().then(sharedMap)) {
                    if (liveWalk != null) {
                        liveWalk { expanded = false }
                    } else {
                        CareStayExpandedIdle(
                            state = stayState,
                            line = stayLine,
                            idleLabel = stayIdleLabel,
                            onButton = onStayButton,
                            onClose = { expanded = false },
                        ) {
                            WalkMap(
                                location = mapLocation,
                                accent = accent,
                                initial = mapInitial,
                                modifier = Modifier.fillMaxSize(),
                                bottomPadding = 180.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The map full screen before any walk: the map, the way out, and the same button as the card,
 * so opening it never strands her without the thing she opened it for.
 */
@Composable
private fun CareStayExpandedIdle(
    state: CareStayState,
    line: String,
    idleLabel: String,
    onButton: () -> Unit,
    onClose: () -> Unit,
    map: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(sakhiSystemBackground())) {
        map()
        LiveWalkTopBar(onClose = onClose, modifier = Modifier.align(Alignment.TopCenter))
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = SakhiRadius.xl, topEnd = SakhiRadius.xl),
            color = sakhiSystemBackground(),
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.navigationBarsPadding().padding(top = SakhiSpacing.space5)) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                    modifier = Modifier.padding(horizontal = SakhiSpacing.space5),
                )
                CareStayButton(
                    state = state,
                    idleLabel = idleLabel,
                    onClick = onButton,
                    modifier = Modifier.padding(
                        start = SakhiSpacing.space4,
                        end = SakhiSpacing.space4,
                        top = SakhiSpacing.space4,
                        bottom = SakhiSpacing.space5,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PartnerDetailContent(
    partnership: CarePartnership,
    isPartnerRole: Boolean,
    isRemoving: Boolean,
    /** The Stay With Me block, built by the caller so it can link to the full screen map. */
    stayCard: @Composable () -> Unit,
    /** Hoisted so the page comes back where she left it after the map closes. */
    listState: androidx.compose.foundation.lazy.LazyListState,
    /** The real things that happened between them: walks stayed for, days logged. */
    moments: List<CareMoment> = emptyList(),
    /** Her own face, and the other person's, both from the server's five (migration 063). */
    selfAvatarIndex: Int = 0,
    otherAvatarIndex: Int = 0,
    /** The name on their profile, which the partnership row may not have. */
    serverName: String? = null,
    onHistory: () -> Unit,
    onManagePermissions: (() -> Unit)?,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var showConfirmRemove by remember { mutableStateOf(false) }

    val resolvedName = (serverName ?: partnership.partnerName).takeIf { name ->
        name.isNotEmpty() &&
            !name.lowercase().contains("partner") &&
            !name.lowercase().contains("sakhi") &&
            name.lowercase() != "unknown"
    }.orEmpty()
    // Both of these read from the owner's seat. A care partner opening this screen is
    // looking at the woman she cares for, so "Your Sakhi" described the reader to
    // themselves and the subtitle did the same.
    val fallbackLabel = if (isPartnerRole) {
        stringResource(R.string.care_fallback_someone_you_care_for)
    } else {
        stringResource(R.string.care_fallback_your_sakhi)
    }
    val displayLabel = if (resolvedName.isEmpty()) fallbackLabel else resolvedName
    val headerTitle = if (resolvedName.isEmpty()) {
        displayLabel
    } else {
        stringResource(R.string.care_header_you_and_name, resolvedName)
    }
    val partnerInitial = displayLabel.take(1).uppercase()

    val createdAtDate = partnershipStartDate(partnership)
    val dateString = formatConnectedSince(createdAtDate, context)
    val daysOfCare = DateConverter.daysBetween(createdAtDate, DateConverter.today()).coerceAtLeast(0)
    val daysValue = when (daysOfCare) {
        0 -> stringResource(R.string.care_today)
        1 -> stringResource(R.string.care_one_day)
        else -> pluralStringResource(R.plurals.care_days_plural, daysOfCare, daysOfCare)
    }

    // One pink surface for the whole screen, from the soft pink behind the picture at the
    // top down to a paler pink at the bottom. It never reaches white, because the blocks
    // that sit on it are white: on a white page they would be invisible, and this screen is
    // meant to read as a few separate things rather than one long list.
    val pageTop = sakhiLightPink()
    // Not quite white at the bottom, so the white blocks still read as blocks without an
    // outline round them.
    val pageBottom = androidx.compose.ui.graphics.lerp(sakhiLightPink(), sakhiSystemBackground(), 0.82f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to pageTop,
                    0.30f to androidx.compose.ui.graphics.lerp(pageTop, pageBottom, 0.72f),
                    1f to pageBottom,
                ),
            ),
    ) {
        // iOS gets its way out of this screen from the sheet's own drag indicator
        // (`PartnerDetailView` hides the nav bar and is presented as a `.sheet`).
        // Android pushes it into the care nav host with no indicator, so without this
        // the connected state is a dead end -- the only way out was Remove, which
        // deletes the partnership rather than just closing the sheet. Same fix, and
        // same reason, as the pending-invite state above.
        SakhiNavBar(onClose = onClose)
        LazyColumn(
            // Lazily built, so the blocks below the fold are not composed before the sheet's
            // push animation starts.
            modifier = Modifier.weight(1f),
            state = listState,
            flingBehavior = rememberSakhiFlingBehavior(),
        ) {
            item(key = "care-detail-header") {
                // The page opens with the picture itself, edge to edge, then who this is and
                // how long it has been. The "since" line is a pill rather than a grey
                // sentence: it is the one fact under her name and it should read as
                // something the two of them earned.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CareConnectionArt(
                        selfAvatarIndex = selfAvatarIndex,
                        otherAvatarIndex = otherAvatarIndex,
                    )
                    Text(
                        text = if (isPartnerRole) {
                            stringResource(R.string.care_header_you_are_with, displayLabel)
                        } else {
                            stringResource(R.string.care_header_is_with_you, displayLabel)
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = sakhiLabel(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = SakhiSpacing.space6),
                    )
                    CareSinceChip(
                        text = stringResource(R.string.care_taking_care_since, dateString),
                        modifier = Modifier.padding(top = SakhiSpacing.space3),
                    )
                    Spacer(modifier = Modifier.height(SakhiSpacing.space6))
                }
            }

            // ── The one thing she came here to do ────────────────────────────────
            //
            // First, because everything else on this screen is something to read. A real
            // map, live the moment a walk is, that opens full screen from where it sits.
            item(key = "care-stay-with-me") {
                stayCard()
                Spacer(modifier = Modifier.height(SakhiSpacing.space4))
            }

            // ── What actually happened between them ──────────────────────────────
            item(key = "care-moments") {
                CareSection {
                    CareSectionTitle(
                        text = if (isPartnerRole) {
                            stringResource(R.string.care_section_moments_partner, displayLabel)
                        } else {
                            stringResource(R.string.care_section_moments_owner, displayLabel)
                        },
                        actionText = if (moments.isNotEmpty()) stringResource(R.string.care_moments_see_all) else null,
                        onAction = if (moments.isNotEmpty()) onHistory else null,
                    )
                    CareMoments(
                        moments = moments.take(4),
                        emptyText = if (isPartnerRole) {
                            stringResource(R.string.care_moments_empty_partner)
                        } else {
                            stringResource(R.string.care_moments_empty_owner, displayLabel)
                        },
                        dayLabel = { momentDayLabel(it, context) },
                    )
                    Spacer(modifier = Modifier.height(SakhiSpacing.space2))
                }
                Spacer(modifier = Modifier.height(SakhiSpacing.space4))
            }

            // ── The connection itself ────────────────────────────────────────────
            item(key = "care-connection-links") {
                CareSection {
                    CareSectionTitle(text = stringResource(R.string.care_section_connection))
                    // What the other person can see comes first: on her side because it is
                    // hers to change, on his because it is the promise she was made.
                    CareLinkRow(
                        icon = Icons.Filled.Shield,
                        title = if (isPartnerRole) {
                            stringResource(R.string.care_link_she_shares)
                        } else {
                            stringResource(R.string.care_link_what_can_see, displayLabel)
                        },
                        subtitle = if (isPartnerRole) {
                            stringResource(R.string.care_link_she_shares_sub)
                        } else {
                            stringResource(R.string.care_link_what_can_see_sub)
                        },
                        onClick = if (!isPartnerRole) onManagePermissions else null,
                    )
                    SakhiListDivider(startInset = SakhiSpacing.space5 + 34.dp + SakhiSpacing.space3)
                    CareLinkRow(
                        icon = Icons.Filled.History,
                        title = stringResource(R.string.care_action_history),
                        subtitle = null,
                        onClick = onHistory,
                    )
                    SakhiListDivider(startInset = SakhiSpacing.space5 + 34.dp + SakhiSpacing.space3)
                    // Ending the connection lives here, at the end of what there is to read,
                    // rather than as a button under everything: it is the rarest thing on
                    // this screen and the least like the others.
                    CareLinkRow(
                        icon = Icons.Filled.HeartBroken,
                        title = if (isPartnerRole) {
                            stringResource(R.string.care_leave_her)
                        } else {
                            stringResource(R.string.care_remove_name, displayLabel)
                        },
                        subtitle = stringResource(R.string.care_remove_sub),
                        onClick = { showConfirmRemove = true },
                    )
                    Spacer(modifier = Modifier.height(SakhiSpacing.space2))
                }
            }

            item(key = "care-detail-tail") {
                Spacer(modifier = Modifier.height(SakhiSpacing.space12))
            }
        }
    }

    if (showConfirmRemove) {
        // iOS uses `.sakhiAlert(type: .destructive, ... secondaryButton: .cancel())` here,
        // not a system alert. A raw Material3 AlertDialog shares none of the app's styling
        // and it is the last thing she sees before a partnership is deleted.
        SakhiAlertSheet(
            kind = SakhiAlertKind.Destructive,
            title = if (isPartnerRole) {
                stringResource(R.string.care_leave_her_title)
            } else {
                stringResource(R.string.care_remove_name_title, displayLabel)
            },
            message = if (isPartnerRole) {
                stringResource(R.string.care_leave_her_body)
            } else {
                stringResource(R.string.care_remove_name_body, displayLabel)
            },
            primaryLabel = if (isPartnerRole) {
                stringResource(R.string.care_confirm_leave_her)
            } else {
                stringResource(R.string.care_confirm_remove)
            },
            onPrimaryClick = {
                showConfirmRemove = false
                onRemove()
            },
            secondaryLabel = stringResource(R.string.care_cancel),
            onSecondaryClick = { showConfirmRemove = false },
            onDismissRequest = { showConfirmRemove = false },
        )
    }
}

@Composable
private fun AvatarPair(partnerInitial: String) {
    Box(modifier = Modifier.size(width = 96.dp, height = 66.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(58.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape)
                .padding(0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = partnerInitial,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(58.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(20.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(8.dp),
                )
            }
        }
    }
}

@Composable
private fun SparkleGlyph() {
    InfoSymbolIcon(Icons.Filled.AutoAwesome)
}


@Composable
private fun InfoSymbolIcon(imageVector: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        color = sakhiSecondaryLabel(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2),
    )
}

@Composable
private fun InfoRow(icon: @Composable () -> Unit, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) { icon() }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
        )
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            // Disclosure chevron. iOS tints `chevron.right` with
            // `DS.Colors.tertiaryLabel` in 10 of its 13 uses -- it is the
            // convention, not a one-off.
            tint = sakhiTertiaryLabel(),
        )
    }
}

private fun formatConnectedSince(
    date: kotlinx.datetime.LocalDate,
    context: android.content.Context,
): String {
    val javaDate = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
    val formatter = java.time.format.DateTimeFormatter.ofPattern(
        context.getString(R.string.care_connected_since_date_format),
        java.util.Locale.getDefault(),
    )
    return javaDate.format(formatter)
}

private fun partnershipStartDate(partnership: CarePartnership): kotlinx.datetime.LocalDate =
    DateConverter.isoToLocalDate(partnership.createdAt)
        ?: DateConverter.isoToLocalDate(partnership.updatedAt)
        ?: DateConverter.today()

private fun sharePendingInvite(
    context: android.content.Context,
    shareMessage: String,
) {
    val baseIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareMessage)
    }
    val whatsAppIntent = Intent(baseIntent).apply {
        `package` = WHATSAPP_PACKAGE
    }
    val canOpenWhatsApp = runCatching {
        context.packageManager.getPackageInfo(WHATSAPP_PACKAGE, PackageManager.GET_ACTIVITIES)
    }.isSuccess

    if (canOpenWhatsApp) {
        context.startActivity(whatsAppIntent)
    } else {
        context.startActivity(
            Intent.createChooser(baseIntent, context.getString(R.string.care_share_chooser_title))
        )
    }
}

private const val WHATSAPP_PACKAGE = "com.whatsapp"

// ── Pending invitation: PendingPartnerWaitingView parity ───────────────────

@Composable
private fun PendingInviteContent(
    invitation: PartnerInvitation,
    isCancelling: Boolean,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScopeCompat()
    val hapticManager = koinInject<AndroidHapticManager>()

    val fallbackLabel = stringResource(R.string.care_fallback_your_sakhi)
    val partnerName = invitation.inviteeName?.takeIf { it.isNotBlank() } ?: fallbackLabel
    val formattedCode = if (invitation.inviteCode.length >= 6) {
        "${invitation.inviteCode.take(3)}-${invitation.inviteCode.takeLast(3)}"
    } else {
        invitation.inviteCode
    }
    val shareMessage = context.getString(R.string.care_pending_share_message, invitation.inviteCode)

    Column(modifier = Modifier.fillMaxSize()) {
    // iOS opens this view with `DSNavBar(onClose:)` (CareModeSettingsView.swift). Android
    // had no bar at all here, so the pending state was a dead end -- the only way out was
    // Cancel Request, which withdraws the invitation rather than just closing the sheet.
    SakhiNavBar(onClose = onClose)
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(SakhiSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        PartnerAvatarCloud(partnerName = partnerName)

        // iOS puts `.multilineTextAlignment(.center)` on both of these
        // (CareModeSettingsView.swift). Without it the Column's
        // `horizontalAlignment` only centres each Text as a block -- a wrapped line
        // still starts at the left edge of that block, which is why the second line of
        // the description sat hard left.
        Text(
            text = stringResource(R.string.care_pending_share_with_name, partnerName),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SakhiSpacing.space6),
        )
        Text(
            text = stringResource(R.string.care_pending_waiting_for_name, partnerName),
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
            textAlign = TextAlign.Center,
            // iOS: `.lineSpacing(4)` and `.padding(.horizontal, DS.Spacing.xxl)` (32).
            lineHeight = CarePendingSubtitleLineHeight,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space8)
                .padding(top = SakhiSpacing.space2, bottom = SakhiSpacing.space6),
        )

        Surface(
            shape = RoundedCornerShape(SakhiRadius.full),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier
                .semantics {
                    contentDescription = context.getString(
                        R.string.care_pending_invite_code_description,
                        formattedCode,
                    )
                }
                .clickable {
                    scope.launch {
                        clipboard.setClipEntry(
                            androidx.compose.ui.platform.ClipEntry(
                                ClipData.newPlainText(
                                    context.getString(R.string.care_clip_label_invite_code),
                                    invitation.inviteCode,
                                )
                            )
                        )
                        hapticManager.success()
                        ToastManager.show(
                            title = context.getString(R.string.care_pending_code_copied_title),
                            message = context.getString(R.string.care_pending_code_copied_message, partnerName),
                            type = ToastType.SUCCESS,
                            durationMs = 2000L,
                        )
                    }
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            ) {
                Text(
                    text = formattedCode,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.care_copy_code),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
        SakhiFooter(
            primaryLabel = stringResource(R.string.care_share),
            onPrimaryClick = {
                hapticManager.impact(HapticImpact.MEDIUM)
                sharePendingInvite(context, shareMessage)
            },
            secondaryLabel = if (isCancelling) {
                stringResource(R.string.care_cancelling)
            } else {
                stringResource(R.string.care_cancel_request)
            },
            onSecondaryClick = onCancel,
            secondaryEnabled = !isCancelling,
        )
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()

// ── Disconnected: invite creation / accept-a-code ──────────────────────────

@Composable
private fun InviteCreationContent(
    uiState: CareUiState,
    onInviteeNameChanged: (String) -> Unit,
    onPartnerRelationChanged: (String) -> Unit,
    onCreateInvitation: () -> Unit,
    onAcceptInviteCodeChanged: (String) -> Unit,
    onAcceptInvitation: () -> Unit,
) {
    val title = stringResource(R.string.care_disconnected_title)
    val subtitle = stringResource(R.string.care_intro_optional_share)
    val feature1 = stringResource(R.string.care_disconnected_feature_1)
    val feature2 = stringResource(R.string.care_disconnected_feature_2)
    val feature3 = stringResource(R.string.care_disconnected_feature_3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), flingBehavior = rememberSakhiFlingBehavior())
            .padding(SakhiSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
        )

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SakhiSpacing.space2),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = SakhiSpacing.space4),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            // Row gap comes from here, not from the row itself — iOS's `pointsSlide`
            // uses VStack(spacing: DS.Spacing.l) = 24 and the component carries no
            // padding of its own, so callers stay free to set their own rhythm.
            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space6)) {
            // iOS maps these three to heart.circle.fill / bell.badge.fill /
            // person.2.wave.2.fill. Android had VisibilityOff on the first, which reads as
            // "hidden" where iOS means "cared for", and Favorite on the third, which reads
            // as "favourite" where iOS means "people".
            CarePromptBullet(
                icon = Icons.Filled.Favorite,
                text = feature1,
                subtitle = stringResource(R.string.care_disconnected_feature_1_subtitle),
            )
            CarePromptBullet(
                icon = Icons.Filled.NotificationsActive,
                text = feature2,
                subtitle = stringResource(R.string.care_disconnected_feature_2_subtitle),
            )
            CarePromptBullet(
                icon = Icons.Filled.Groups,
                text = feature3,
                subtitle = stringResource(R.string.care_disconnected_feature_3_subtitle),
            )
            }
        }

        // Someone who arrived through an invite link is here to ACCEPT, not to
        // invite. iOS sends that person straight into `AcceptInviteSheet`; Android
        // shares one screen for both, so at minimum the accept card leads when a code
        // is already in hand. Otherwise the invited person has to scroll past a
        // full "Pick Your Person / Create invite code" form to reach the field that
        // is already filled in for them.
        val arrivedWithCode = uiState.acceptInviteCode.isNotBlank()
        val createInviteCard: @Composable () -> Unit = {
            Surface(
                color = sakhiSystemBackground(),
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(SakhiSpacing.space5),
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                ) {
                    Text(text = stringResource(R.string.care_invite_someone_you_trust), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.care_invite_prepare_code),
                        style = MaterialTheme.typography.bodyMedium,
                        color = sakhiSecondaryLabel(),
                    )

                    OutlinedTextField(
                        value = uiState.inviteeName,
                        onValueChange = onInviteeNameChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.care_label_name_optional)) },
                        placeholder = { Text(stringResource(R.string.care_placeholder_optional)) },
                    )
                    OutlinedTextField(
                        value = uiState.partnerRelation,
                        onValueChange = onPartnerRelationChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.care_label_relationship)) },
                        placeholder = { Text(stringResource(R.string.care_placeholder_relationship)) },
                        singleLine = true,
                    )

                    PrimaryButton(
                        text = if (uiState.isCreatingInvite) {
                            stringResource(R.string.care_preparing_invite)
                        } else {
                            stringResource(R.string.care_create_invite_code)
                        },
                        onClick = onCreateInvitation,
                        enabled = !uiState.isCreatingInvite,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        val acceptCodeCard: @Composable () -> Unit = {
            Surface(
                color = sakhiSystemBackground(),
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(SakhiSpacing.space5),
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                ) {
                    Text(text = stringResource(R.string.care_i_have_a_code), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.care_enter_shared_code),
                        style = MaterialTheme.typography.bodyMedium,
                        color = sakhiSecondaryLabel(),
                    )
                    OutlinedTextField(
                        value = uiState.acceptInviteCode,
                        onValueChange = onAcceptInviteCodeChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.care_label_invite_code)) },
                        placeholder = { Text(stringResource(R.string.care_placeholder_invite_code)) },
                        singleLine = true,
                    )
                    PrimaryButton(
                        text = if (uiState.isAcceptingInvite) {
                            stringResource(R.string.care_joining)
                        } else {
                            stringResource(R.string.care_accept_invite)
                        },
                        onClick = onAcceptInvitation,
                        enabled = !uiState.isAcceptingInvite,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        // One path at a time, as on iOS.
        //
        // iOS sends someone who arrived through an invite link straight into
        // `AcceptInviteSheet`: she sees the code she was given, and nothing else.
        // Android used to render both cards in both orders, so she was also handed a
        // full "Invite someone you trust" form, with a name field, a relationship field
        // and a Create invite code button, directly under the one she came to use. That
        // is the opposite flow, offered at the moment she is trying to finish this one.
        //
        // The no-code case does not normally reach here at all: `shouldAutoShowInviteFlow`
        // routes a disconnected hub with no prefill into the `carePartnerInvite` flow
        // above, which is what iOS's `InviteFlowLauncher` does. The else branch stays as
        // the fallback for someone who clears the prefilled field.
        if (arrivedWithCode) {
            acceptCodeCard()
        } else {
            createInviteCard()
        }

        uiState.error?.let { error ->
            Text(text = error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        uiState.infoMessage?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Port of iOS's `FeatureBulletRow` for the Care disconnected screen.
 *
 * Matches the onboarding copy of this row (`OnboardingContentStepUi`), which was brought
 * to iOS spec earlier: `HStack(alignment: .top, spacing: .m)` (16), 44pt circle, 20pt
 * glyph, 15pt bold title over a 14pt secondary subtitle. This screen renders the same
 * `care.onboarding.*` CMS copy iOS puts through `FeatureBulletRow`, so it gets the same
 * treatment rather than a title-only variant.
 */
@Composable
private fun CarePromptBullet(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        ) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                // iOS default 14pt line height (~16.8) plus its explicit lineSpacing(3).
                lineHeight = 20.sp,
                color = sakhiSecondaryLabel(),
            )
        }
    }
}

// ── Permission editing: PartnerPermissionsEditView parity ──────────────────

@Composable
private fun PartnerPermissionsEditContent(
    partnership: CarePartnership,
    isSaving: Boolean,
    onBack: () -> Unit,
    onSave: (ParentChildPermissions) -> Unit,
) {
    val initial = partnership.enhancedPermissions ?: ParentChildPermissions()
    var canLogPeriods by remember { mutableStateOf(initial.canLogPeriods) }
    // New granular permission (2026-07-15, real security fix): generating/
    // exporting a full health report exposes essentially every other
    // granular field at once, so it's its own explicit action-style grant --
    // matching `canLogPeriods` above, not folded into the "what they can
    // see" view-permission list below -- and must never default to enabled.
    var canGenerateReports by remember { mutableStateOf(initial.canGenerateReports) }
    var sharePeriodDates by remember { mutableStateOf(initial.canViewPeriodDates) }
    var shareCycleHistory by remember { mutableStateOf(initial.canViewCycleHistory) }
    var sharePredictions by remember { mutableStateOf(initial.canViewPredictions) }
    var shareSymptoms by remember { mutableStateOf(initial.canViewSymptoms) }
    var shareMoods by remember { mutableStateOf(initial.canViewMoods) }
    var shareDailyLogs by remember { mutableStateOf(initial.canViewDailyLogs) }
    var shareOvulationTests by remember { mutableStateOf(initial.canViewOvulationTests) }
    var shareMedications by remember { mutableStateOf(initial.canViewMedications) }
    var shareTemperature by remember { mutableStateOf(initial.canViewTemperature) }
    var shareWeight by remember { mutableStateOf(initial.canViewWeight) }
    var shareDischarge by remember { mutableStateOf(initial.canViewDischarge) }
    var shareNotes by remember { mutableStateOf(initial.canViewNotes) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Had no header at all, so the only way back out of Manage Permissions was Save.
        SakhiNavBar(onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState(), flingBehavior = rememberSakhiFlingBehavior()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space5),
            ) {
                Text(
                    text = stringResource(R.string.care_permissions_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = stringResource(R.string.care_permissions_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                    modifier = Modifier.padding(top = SakhiSpacing.space2),
                )
            }

            SectionHeader(text = stringResource(R.string.care_section_what_they_can_do))
            Surface(
                color = sakhiSystemBackground(),
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = SakhiSpacing.space6),
            ) {
                Column {
                    PermissionToggleRow(
                        title = stringResource(R.string.care_permission_log_periods),
                        checked = canLogPeriods,
                        onCheckedChange = { canLogPeriods = it },
                    )
                    SakhiListDivider(startInset = SakhiSpacing.space10)
                    PermissionToggleRow(
                        title = stringResource(R.string.care_permission_generate_reports),
                        checked = canGenerateReports,
                        onCheckedChange = { canGenerateReports = it },
                    )
                }
            }

            SectionHeader(
                text = stringResource(R.string.care_section_what_they_can_see),
                modifier = Modifier.padding(top = SakhiSpacing.space6),
            )
            Surface(
                color = sakhiSystemBackground(),
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = SakhiSpacing.space6),
            ) {
                Column {
                    val rows = listOf(
                        Triple(stringResource(R.string.care_permission_period_dates), sharePeriodDates) { v: Boolean -> sharePeriodDates = v },
                        Triple(stringResource(R.string.care_permission_cycle_history), shareCycleHistory) { v: Boolean -> shareCycleHistory = v },
                        Triple(stringResource(R.string.care_permission_cycle_predictions), sharePredictions) { v: Boolean -> sharePredictions = v },
                        Triple(stringResource(R.string.care_permission_symptoms), shareSymptoms) { v: Boolean -> shareSymptoms = v },
                        Triple(stringResource(R.string.care_permission_moods), shareMoods) { v: Boolean -> shareMoods = v },
                        Triple(stringResource(R.string.care_permission_daily_health_logs), shareDailyLogs) { v: Boolean -> shareDailyLogs = v },
                        Triple(stringResource(R.string.care_permission_ovulation_tests), shareOvulationTests) { v: Boolean -> shareOvulationTests = v },
                        Triple(stringResource(R.string.care_permission_medications), shareMedications) { v: Boolean -> shareMedications = v },
                        Triple(stringResource(R.string.care_permission_body_temperature), shareTemperature) { v: Boolean -> shareTemperature = v },
                        Triple(stringResource(R.string.care_permission_weight_body), shareWeight) { v: Boolean -> shareWeight = v },
                        Triple(stringResource(R.string.care_permission_discharge), shareDischarge) { v: Boolean -> shareDischarge = v },
                        Triple(stringResource(R.string.care_permission_personal_notes), shareNotes) { v: Boolean -> shareNotes = v },
                    )
                    rows.forEachIndexed { index, (title, checked, onChange) ->
                        PermissionToggleRow(title = title, checked = checked, onCheckedChange = onChange)
                        if (index != rows.lastIndex) SakhiListDivider(startInset = SakhiSpacing.space10)
                    }
                }
            }

            Text(
                text = stringResource(R.string.care_permission_sexual_activity_private),
                style = MaterialTheme.typography.bodySmall,
                color = sakhiSecondaryLabel(),
                modifier = Modifier.padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2),
            )

            Spacer(modifier = Modifier.height(SakhiSpacing.space16))
        }

        SakhiListDivider()
        SakhiFooter(
            primaryLabel = if (isSaving) stringResource(R.string.care_saving) else stringResource(R.string.care_save),
            primaryEnabled = !isSaving,
            onPrimaryClick = {
                onSave(
                    ParentChildPermissions(
                        canViewPeriodDates = sharePeriodDates,
                        canViewSymptoms = shareSymptoms,
                        canViewMoods = shareMoods,
                        canViewMedications = shareMedications,
                        canViewPredictions = sharePredictions,
                        canLogPeriods = canLogPeriods,
                        canViewCycleHistory = shareCycleHistory,
                        canViewDailyLogs = shareDailyLogs,
                        canViewOvulationTests = shareOvulationTests,
                        canViewTemperature = shareTemperature,
                        canViewWeight = shareWeight,
                        canViewNotes = shareNotes,
                        canViewDischarge = shareDischarge,
                        canViewSexualActivity = false,
                        canGenerateReports = canGenerateReports,
                    )
                )
            },
            showSecondarySlot = false,
        )
    }
}

@Composable
private fun PermissionToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        SakhiSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── History: PartnerHistoryView parity ─────────────────────────────────────
// Ports iOS `PartnerDetailView.swift`'s `PartnerHistoryView`: only logs where
// `loggedBy == .partner` (the partner's own care-log actions), never the
// primary user's private symptom/mood/notes entries -- partner-authored logs
// only ever set periodPresent/flowIntensity per `PeriodLog.partnerLog(...)`,
// so that's all this timeline can ever show, matching iOS exactly.

@Composable
private fun PartnerHistoryContent(
    partnership: CarePartnership,
    isPartnerRole: Boolean,
    onBack: () -> Unit,
    periodLogRepository: team.sakhi.repositories.PeriodLogRepository = org.koin.compose.koinInject(),
) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf<List<team.sakhi.models.PeriodLog>>(emptyList()) }
    var isLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val fallbackLabel = if (isPartnerRole) {
        stringResource(R.string.care_fallback_someone_you_care_for)
    } else {
        stringResource(R.string.care_fallback_your_sakhi)
    }
    val connectedDate = formatConnectedSince(partnershipStartDate(partnership), context)

    androidx.compose.runtime.LaunchedEffect(partnership.userId) {
        logs = emptyList()
        loadError = null
        isLoaded = false
        periodLogRepository.getAll(partnership.userId)
            .onSuccess { all ->
                logs = all
                    .filter { it.loggedBy == team.sakhi.models.LogSource.PARTNER }
                    .sortedByDescending { it.logDate.toString() }
            }
            .onFailure { throwable ->
                loadError = throwable.toSafeUserMessage(context, R.string.care_error_load_activity_right_now)
            }
        isLoaded = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Was a hand-rolled Row of BackButton + Text, which is exactly the drift
        // SakhiNavBar exists to stop: its button size and paddings did not match any
        // other sheet header in the app.
        SakhiNavBar(onBack = onBack, title = stringResource(R.string.care_title_activity))
        SakhiListDivider()

        if (!isLoaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (loadError != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // The sheet runs to the bottom of the screen, so without this the
                    // last row sits under the gesture bar.
                    .navigationBarsPadding()
                    .padding(SakhiSpacing.space6),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = sakhiSecondaryLabel(),
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = stringResource(R.string.care_error_couldnt_load_activity),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = loadError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                    modifier = Modifier.padding(top = SakhiSpacing.space2),
                )
            }
            return
        }

        if (logs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(SakhiSpacing.space6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = sakhiSecondaryLabel(),
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = stringResource(R.string.care_empty_no_activity_yet),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = sakhiSecondaryLabel(),
                )
                Text(
                    // The partner is the one doing the logging here, so the owner's
                    // sentence ("<name> logs something for you") read backwards to them.
                    text = if (isPartnerRole) {
                        stringResource(R.string.care_empty_when_you_log_for_her)
                    } else {
                        stringResource(
                            R.string.care_empty_when_name_logs,
                            partnership.partnerName.ifBlank { fallbackLabel },
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                )
                Spacer(modifier = Modifier.weight(1f))
                ConnectionBadge(connectedDate = connectedDate)
            }
            return
        }

        // The only genuinely unbounded list in the app: every period log a care partner has
        // ever made. It used to build every row up front inside one Surface, so opening the
        // screen cost one composition per log however many there were.
        //
        // Each row is its own lazy item now. They still read as ONE rounded card because each
        // row draws the card background itself and only the first and last round their
        // corners, which is what `historyRowShape` is for.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(SakhiSpacing.space5),
            flingBehavior = rememberSakhiFlingBehavior(),
        ) {
            item(key = "recent-activity-header") {
                SectionHeader(text = stringResource(R.string.care_section_recent_activity))
            }

            itemsIndexed(
                items = logs,
                // Logs carry no stable id of their own here, so the date plus position is the
                // closest thing to one. Never a bare index: a new log arriving at the top
                // would renumber every row and defeat the point of having keys.
                key = { index, log -> "log-${log.logDate}-$index" },
                contentType = { _, _ -> "log-row" },
            ) { index, log ->
                Surface(
                    color = sakhiSystemBackground(),
                    shape = historyRowShape(index = index, lastIndex = logs.lastIndex),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {}
                                .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                        ) {
                            Icon(
                                imageVector = if (log.periodPresent) Icons.Filled.WaterDrop else Icons.Filled.Opacity,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(28.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (log.periodPresent) {
                                        stringResource(R.string.care_period_logged)
                                    } else {
                                        stringResource(R.string.care_period_cleared)
                                    },
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = formatConnectedSince(log.logDate, context),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sakhiSecondaryLabel(),
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.care_by_name,
                                    partnership.partnerName.ifBlank { fallbackLabel },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = sakhiSecondaryLabel(),
                            )
                        }
                        if (index != logs.lastIndex) SakhiListDivider(startInset = SakhiSpacing.space10)
                    }
                }
            }

            item(key = "connection-section") {
                Column {
                    SectionHeader(
                        text = stringResource(R.string.care_section_connection),
                        modifier = Modifier.padding(top = SakhiSpacing.space6),
                    )
                    Surface(
                        color = sakhiSystemBackground(),
                        shape = RoundedCornerShape(SakhiRadius.xxl),
                        tonalElevation = SakhiSpacing.space1,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {}
                                .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(28.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        R.string.care_connected_with_name,
                                        partnership.partnerName.ifBlank { fallbackLabel },
                                    ),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = connectedDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sakhiSecondaryLabel(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Rounds only the outer corners of a grouped list, so rows that are separate lazy items still
 * draw as one card. Matches the single `RoundedCornerShape(SakhiRadius.xxl)` the whole group
 * carried when it was one Surface.
 */
@Composable
private fun historyRowShape(index: Int, lastIndex: Int): Shape {
    val radius = SakhiRadius.xxl
    val square = 0.dp
    return RoundedCornerShape(
        topStart = if (index == 0) radius else square,
        topEnd = if (index == 0) radius else square,
        bottomStart = if (index == lastIndex) radius else square,
        bottomEnd = if (index == lastIndex) radius else square,
    )
}

@Composable
private fun ConnectionBadge(connectedDate: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(R.string.care_connected_badge, connectedDate),
            style = MaterialTheme.typography.labelSmall,
            color = sakhiSecondaryLabel(),
        )
    }
}

/** iOS `.lineSpacing(4)` on the pending description. */
private val CarePendingSubtitleLineHeight = 22.sp
