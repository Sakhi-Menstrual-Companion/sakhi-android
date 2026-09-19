package team.sakhi.android.feature.care

import team.sakhi.android.ui.SakhiNavDirection
import team.sakhi.android.ui.SakhiScreenTransition
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PersonAdd
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
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
import team.sakhi.models.RelationType
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
     * Not used by this page any more: the Stay With Me card left the Care page, as on iOS
     * (2026-09-14), and the ride opens from the calendar bar's nearby-map button. Kept so the
     * host's call does not change; iOS's "an ask from her person opens the ride from this
     * page" has no Android counterpart yet.
     */
    @Suppress("UNUSED_PARAMETER")
    onOpenLiveWalk: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticManager = koinInject<AndroidHapticManager>()
    val stayWithMeViewModel: StayWithMeViewModel = koinViewModel()
    // Only while this sheet is on screen: it re-reads the walk, tells her screen that her
    // person is looking, and notices a walk that has gone past its time.
    DisposableEffect(Unit) {
        stayWithMeViewModel.onVisible()
        onDispose { stayWithMeViewModel.onHidden() }
    }
    // Which page of Care is showing, as ONE value, the way Profile keeps its sub-screens.
    // It used to be four flags, each ANDed with live connection state, so a care refresh that
    // briefly had no partnership popped the page and pushed it back.
    var careScreen by remember { mutableStateOf(CareSubScreen.Root) }
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
    // No Stay With Me card on this page, as on iOS (Karan, 2026-09-14): the ride opens from
    // the nearby-map button in the calendar bar, so the Care page is the two faces, what they
    // did for each other, and the connection.

    LaunchedEffect(connectedPartnership?.id) {
        connectedPartnership?.id?.let(stayWithMeViewModel::loadHistory)
        connectedPartnership?.id?.let(stayWithMeViewModel::loadPartnerCard)
    }
    val partnerCard by stayWithMeViewModel.partnerCard.collectAsStateWithLifecycle()
    // What her person did that reached her: this phone's inbox, the same rows the bell shows.
    val inboxStore = koinInject<team.sakhi.notifications.InAppNotificationStore>()
    val inbox by inboxStore.state.collectAsStateWithLifecycle()
    LaunchedEffect(connectedPartnership?.id) { if (connectedPartnership != null) inboxStore.refresh() }
    val careActions = remember(inbox.items, ownerConnected?.partnership?.partnerId) {
        val partnerId = ownerConnected?.partnership?.partnerId
        if (partnerId == null) {
            emptyList()
        } else {
            inbox.items.filter { row ->
                row.type in CARE_ACTION_TYPES && row.actorUserId.equals(partnerId, ignoreCase = true)
            }
        }
    }
    val sessionManager = koinInject<team.sakhi.session.SessionManager>()
    val selfAvatarIndex = remember(sessionManager.current?.userId) {
        CareAvatars.selfIndex(careContext, sessionManager.current?.userId.orEmpty())
    }
    LaunchedEffect(connectedPartnership?.userId) {
        val userId = connectedPartnership?.userId ?: return@LaunchedEffect
        loggedDays = periodLogRepository.getAll(userId).getOrNull().orEmpty()
            .filter { it.loggedBy == team.sakhi.models.LogSource.PARTNER }
            .sortedByDescending { it.logDate.toString() }
            .take(6)
            .map { log -> careLoggedDay(log.logDate) }
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
    // Every screen opened from inside Care pushes in and slides back out, the way Profile's
    // sub-screens do (Karan, 2026-09-13). They used to swap in with no motion at all, which
    // read as the sheet changing into something else rather than going one step deeper.
    // The partnership a page was opened with, kept while it slides out even if a refresh
    // briefly has none. If the connection really ends, the page goes back to Care once.
    var pagePartnership by remember { mutableStateOf<CarePartnership?>(null) }
    LaunchedEffect(connectedPartnership) { connectedPartnership?.let { pagePartnership = it } }
    val shownPartnership = connectedPartnership ?: pagePartnership
    val shownIsPartner = partnerConnected != null ||
        (connectedPartnership == null && pagePartnership?.let { it.userId != sessionManager.current?.userId } == true)
    LaunchedEffect(uiState.careState is CareRuntimeState.Disconnected) {
        if (uiState.careState is CareRuntimeState.Disconnected) careScreen = CareSubScreen.Root
    }
    // One back handler, outside the transition, as Profile has. Handlers inside each page were
    // left registered while pages slid, so back did nothing or the wrong thing.
    BackHandler(enabled = !showInviteFlowRoute && careScreen != CareSubScreen.Root) {
        careScreen = CareSubScreen.Root
    }
    val subScreen = if (showInviteFlowRoute) CareSubScreen.InviteFlow else careScreen

    // What the other person is called on these pages, as iOS's `displayLabel`: their name on
    // their profile, then the one on the connection, then a plain stand-in.
    val shownLabel = shownPartnership?.let { careDetailLabel(it, partnerCard?.name, shownIsPartner) }.orEmpty()

    // Cancelling a pending invite, as iOS's hub does it: a loading screen while it goes, then
    // "Request cancelled" with Done, rather than dropping her straight into a new invite flow.
    var cancelRequested by remember { mutableStateOf(false) }
    var requestCancelled by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isCancellingInvite) {
        if (cancelRequested && !uiState.isCancellingInvite) {
            cancelRequested = false
            if (uiState.error == null) {
                requestCancelled = true
            } else {
                ToastManager.show(
                    title = careContext.getString(R.string.care_cancel_request_failed_title),
                    message = careContext.getString(R.string.care_cancel_request_failed_message),
                    type = ToastType.ERROR,
                    durationMs = 2000L,
                )
            }
        }
    }
    // The permissions page was left as it was: a failed save says so (iOS's alert), and a
    // saving one is covered by the loading screen.
    var permissionsFailed by remember { mutableStateOf(false) }

    SheetSurface {
        when {
            requestCancelled -> CareActionCompletion(
                title = stringResource(R.string.care_request_cancelled_title),
                message = stringResource(R.string.care_info_invite_closed),
                primaryLabel = stringResource(R.string.care_done),
                onPrimary = onClose,
                onClose = onClose,
            )

            uiState.isCancellingInvite -> CareProgressLoading(
                listOf(
                    stringResource(R.string.care_loading_cancelling_1),
                    stringResource(R.string.care_loading_cancelling_2),
                    stringResource(R.string.care_loading_cancelling_3),
                ),
            )

            // Leaving or removing: iOS swaps the whole page for this while the server does it.
            uiState.isRemovingPartnership -> CareProgressLoading(
                listOf(
                    stringResource(R.string.care_loading_removing_1),
                    stringResource(R.string.care_loading_removing_2),
                    stringResource(R.string.care_loading_removing_3),
                ),
            )

            else -> SakhiScreenTransition(
                targetState = subScreen,
                directionFor = { _, target ->
                    when (target) {
                        CareSubScreen.Root -> SakhiNavDirection.Backward
                        CareSubScreen.InviteFlow -> SakhiNavDirection.None
                        else -> SakhiNavDirection.Forward
                    }
                },
                label = "care_sheet_transition",
                // Care's pages are children of the Care screen, so it stays behind them.
                parentStaysBehind = true,
            ) { target ->
                // Each branch reads its partnership again rather than trusting the check above:
                // a screen sliding out can still be drawn after the connection it showed is gone.
                when (target) {
                    CareSubScreen.InviteFlow -> OnboardingFlowHost(
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

                    CareSubScreen.Leave -> shownPartnership?.let { partnership ->
                        LeaveConnectionContent(
                            isPartnerRole = shownIsPartner,
                            name = shownLabel,
                            isRemoving = uiState.isRemovingPartnership,
                            onBack = { careScreen = CareSubScreen.Root },
                            onConfirm = {
                                hapticManager.impact(HapticImpact.MEDIUM)
                                viewModel.removePartnership(partnership.id)
                            },
                        )
                    }

                    CareSubScreen.History -> shownPartnership?.let { partnership ->
                        PartnerHistoryContent(
                            partnership = partnership,
                            isPartnerRole = shownIsPartner,
                            name = shownLabel,
                            walks = walkHistory,
                            actions = careActions,
                            onBack = { careScreen = CareSubScreen.Root },
                        )
                    }

                    CareSubScreen.Permissions -> shownPartnership?.let { partnership ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            PartnerPermissionsEditContent(
                                partnership = partnership,
                                isSaving = uiState.isSavingPermissions,
                                onBack = { careScreen = CareSubScreen.Root },
                                onSave = { permissions ->
                                    viewModel.updatePermissions(partnership.id, permissions) { saved ->
                                        if (saved) careScreen = CareSubScreen.Root else permissionsFailed = true
                                    }
                                },
                            )
                            // iOS covers the page with the loading screen while it saves.
                            if (uiState.isSavingPermissions) {
                                CareProgressLoading(
                                    listOf(
                                        stringResource(R.string.care_loading_permissions_1, shownLabel),
                                        stringResource(R.string.care_loading_permissions_2),
                                        stringResource(R.string.care_loading_permissions_3),
                                    ),
                                )
                            }
                        }
                    }

                    CareSubScreen.Root -> {
                        when (val state = uiState.careState) {
                            CareRuntimeState.Loading -> CareProgressLoading(
                                listOf(
                                    stringResource(R.string.care_loading_syncing_1),
                                    stringResource(R.string.care_loading_syncing_2),
                                    stringResource(R.string.care_loading_syncing_3),
                                ),
                            )

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
                                onCancel = {
                                    cancelRequested = true
                                    viewModel.cancelInvitation()
                                },
                                onClose = onClose,
                            )

                            is CareRuntimeState.OwnerConnected -> {
                                val personName = careDetailLabel(state.partnership, partnerCard?.name, isPartnerRole = false)
                                val moments = remember(walkHistory, loggedDays, personName, careActions) {
                                    careMoments(
                                        careContext,
                                        walkHistory,
                                        loggedDays,
                                        isPartnerRole = false,
                                        otherName = personName,
                                        actions = careActions,
                                    )
                                }
                                PartnerDetailContent(
                                    partnership = state.partnership,
                                    isPartnerRole = false,
                                    moments = moments,
                                    selfAvatarIndex = selfAvatarIndex,
                                    otherAvatarIndex = partnerCard?.avatarIndex ?: 0,
                                    serverName = partnerCard?.name,
                                    onHistory = { careScreen = CareSubScreen.History },
                                    onManagePermissions = { careScreen = CareSubScreen.Permissions },
                                    onRemove = { careScreen = CareSubScreen.Leave },
                                    onClose = onClose,
                                )
                            }

                            is CareRuntimeState.PartnerConnected -> {
                                val herName = careDetailLabel(state.partnership, partnerCard?.name, isPartnerRole = true)
                                val moments = remember(walkHistory, loggedDays, herName) {
                                    careMoments(careContext, walkHistory, loggedDays, isPartnerRole = true, otherName = herName)
                                }
                                PartnerDetailContent(
                                    partnership = state.partnership,
                                    isPartnerRole = true,
                                    moments = moments,
                                    selfAvatarIndex = selfAvatarIndex,
                                    otherAvatarIndex = partnerCard?.avatarIndex ?: 0,
                                    serverName = partnerCard?.name,
                                    onHistory = { careScreen = CareSubScreen.History },
                                    // What she shares with him is a line he reads, not a page:
                                    // only she can change it, and iOS gives the row no action.
                                    onManagePermissions = null,
                                    onRemove = { careScreen = CareSubScreen.Leave },
                                    onClose = onClose,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (permissionsFailed) {
        SakhiAlertSheet(
            kind = SakhiAlertKind.Warning,
            title = stringResource(R.string.care_permissions_failed_title),
            message = stringResource(R.string.care_error_permission_change_not_saved),
            primaryLabel = stringResource(R.string.care_ok),
            onPrimaryClick = { permissionsFailed = false },
            onDismissRequest = { permissionsFailed = false },
        )
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

/** The pages the Care sheet can show, for the push and slide between them. */
private enum class CareSubScreen { Root, InviteFlow, Leave, History, Permissions }

// ── Connected: PartnerDetailView parity ────────────────────────────────────

/**
 * The name to show for the other person: a real one, or nothing. Generic labels ("partner",
 * "sakhi", "unknown") are not names and are never shown as one. Same test as iOS's
 * `resolvedName`.
 */
private fun careRealName(name: String?): String? {
    val n = name.orEmpty()
    val l = n.lowercase()
    return n.takeIf { it.isNotEmpty() && !l.contains("partner") && !l.contains("sakhi") && l != "unknown" }
}

/**
 * What the other person is called on the connection pages, as iOS's `displayLabel`: their
 * name on their profile, then the one on the connection, then a plain stand-in for the seat
 * the reader is in. ([careDisplayName] is the walk screens' own, with its own fallbacks.)
 */
@Composable
internal fun careDetailLabel(partnership: CarePartnership, serverName: String?, isPartnerRole: Boolean): String =
    careRealName(serverName)
        ?: careRealName(partnership.partnerName)
        ?: stringResource(
            if (isPartnerRole) R.string.care_fallback_someone_you_care_for else R.string.care_fallback_your_sakhi,
        )

/**
 * The loading screen every Care stage shares, as iOS's `CareProgressLoadingView`: the Sakhi
 * mark, and a line under it that moves on while it waits.
 */
@Composable
internal fun CareProgressLoading(messages: List<String>) {
    team.sakhi.android.ui.SakhiLoadingView(
        context = team.sakhi.android.ui.SakhiLoadingContext.Messages(messages),
    )
}

/**
 * The connected Care screen, for both sides of a connection. Port of iOS's `PartnerDetailView`.
 *
 * One soft pink page with the two faces at the top and two white blocks under them: what this
 * person actually did for her, and the connection. There is no Stay With Me card here (Karan,
 * 2026-09-14): the ride opens from the nearby-map button in the calendar bar.
 */
@Composable
private fun PartnerDetailContent(
    partnership: CarePartnership,
    isPartnerRole: Boolean,
    /** The real things that happened between them: walks stayed for, days logged. */
    moments: List<CareMoment> = emptyList(),
    /** Her own face, and the other person's, both from the server's five (migration 063). */
    selfAvatarIndex: Int = 0,
    otherAvatarIndex: Int = 0,
    /** The name on their profile, which the partnership row may not have. */
    serverName: String? = null,
    onHistory: () -> Unit,
    /** Null on his side: what she shares with him is something he reads, not a page. */
    onManagePermissions: (() -> Unit)?,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val displayLabel = careDetailLabel(partnership, serverName, isPartnerRole)

    // "You & Her" / "You & Him": the two of them, in the order she reads the faces above. His
    // side is always "Her", because the person he cares for is the woman whose app this is.
    // Hers comes from the relation she picked when she invited them: the app never asks
    // anyone's gender, so mother and father are the only two it can be sure of, and a parent
    // it cannot read is "Them".
    val pronoun = stringResource(
        if (isPartnerRole) {
            R.string.care_pronoun_her
        } else when (partnership.relationType) {
            RelationType.MOTHER -> R.string.care_pronoun_her
            RelationType.FATHER -> R.string.care_pronoun_him
            RelationType.PARTNER -> R.string.care_pronoun_him
            RelationType.PARENT -> R.string.care_pronoun_them
        },
    )

    val createdAtDate = partnershipStartDate(partnership)
    val dateString = formatConnectedSince(createdAtDate, context)

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
                // The page opens with the picture itself, then who this is and how long it has
                // been. The "since" line is a pill rather than a grey sentence: it is the one
                // fact under the heading and it should read as something the two of them earned.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CareConnectionArt(
                        selfAvatarIndex = selfAvatarIndex,
                        otherAvatarIndex = otherAvatarIndex,
                    )
                    Text(
                        text = stringResource(R.string.care_header_you_and_name, pronoun),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = sakhiLabel(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = SakhiSpacing.space6),
                    )
                    CareSinceChip(
                        text = stringResource(R.string.care_taking_care_since, dateString),
                        modifier = Modifier.padding(top = SakhiSpacing.space3),
                    )
                    // 8 under the header, and 16 between the blocks, as iOS lays them out.
                    Spacer(modifier = Modifier.height(SakhiSpacing.space6))
                }
            }

            // ── What actually happened between them ──────────────────────────────
            item(key = "care-moments") {
                CareSection {
                    CareSectionTitle(
                        text = if (isPartnerRole) {
                            stringResource(R.string.care_section_moments_partner)
                        } else {
                            stringResource(R.string.care_section_moments_owner, displayLabel)
                        },
                    )
                    CareMoments(
                        moments = moments,
                        onShowAll = onHistory,
                        emptyText = if (isPartnerRole) {
                            stringResource(R.string.care_moments_empty_partner)
                        } else {
                            stringResource(R.string.care_moments_empty_owner, displayLabel)
                        },
                        dayLabel = { momentDayLabel(it, context) },
                    )
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
                        // Hers opens the page where she edits it; his has no action and no
                        // chevron, as on iOS.
                        onClick = onManagePermissions,
                    )
                    // No separate History row: "Show all" on the moments above opens the same
                    // page, and a second door to it read as clutter (Karan, 2026-09-13).
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
                        onClick = onRemove,
                    )
                    Spacer(modifier = Modifier.height(SakhiSpacing.space2))
                }
            }

            item(key = "care-detail-tail") {
                Spacer(modifier = Modifier.height(SakhiSpacing.space10))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    // iOS writes these in capitals ("WHAT THEY CAN DO"), 11 bold, tertiary, 0.5 tracking.
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = sakhiTertiaryLabel(),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SakhiSpacing.space6, end = SakhiSpacing.space6, bottom = SakhiSpacing.space2),
    )
}

private fun formatConnectedSince(
    date: kotlinx.datetime.LocalDate,
    context: android.content.Context,
): String {
    val javaDate = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
    val formatter = java.time.format.DateTimeFormatter.ofPattern(
        context.getString(R.string.care_connected_since_date_format),
        // Language only: en-IN spells September "Sept", iOS writes "Sep".
        java.util.Locale(java.util.Locale.getDefault().language),
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
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SakhiSpacing.space6),
        )
        Text(
            text = stringResource(R.string.care_pending_waiting_for_name, partnerName),
            fontSize = 15.sp,
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
            // iOS: a `lightPink` capsule, not the brand pink at 12%.
            color = sakhiLightPink(),
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
                modifier = Modifier.padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            ) {
                Text(
                    text = formattedCode,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
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
            // iOS keeps the label and only disables it; the loading screen carries the wait.
            secondaryLabel = stringResource(R.string.care_cancel_request),
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
    val loadingSlot: @Composable () -> Unit = { CareLoadingButton() }

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
                    SakhiListDivider(startInset = SakhiSpacing.space4)
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
                        if (index != rows.lastIndex) SakhiListDivider(startInset = SakhiSpacing.space4)
                    }
                }
            }

            Text(
                text = stringResource(R.string.care_permission_sexual_activity_private),
                fontSize = 12.sp,
                color = sakhiTertiaryLabel(),
                modifier = Modifier.padding(start = SakhiSpacing.space6, end = SakhiSpacing.space6, top = SakhiSpacing.space3),
            )

            Spacer(modifier = Modifier.height(SakhiSpacing.space10))
        }

        SakhiListDivider()
        SakhiFooter(
            primaryLabel = stringResource(R.string.care_save),
            primaryEnabled = !isSaving,
            primarySlot = if (isSaving) loadingSlot else null,
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
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
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
    /** The other person's name, as the connection page shows it. */
    name: String,
    /** The walks this connection has done, from the Stay With Me store. */
    walks: List<team.sakhi.staywithme.StayWithMeWalkRecord>,
    /** Her person's other care actions, from the inbox. Empty on his side. */
    actions: List<team.sakhi.notifications.InAppNotification> = emptyList(),
    onBack: () -> Unit,
    periodLogRepository: team.sakhi.repositories.PeriodLogRepository = org.koin.compose.koinInject(),
) {
    // Everything this person did for her, as one timeline: every walk they stayed for and
    // every day they logged, newest first. The card on the connection screen shows three of
    // these and opens this with "Show all"; it is drawn with the card's own timeline so the
    // two read as the same thing at two lengths.
    val context = LocalContext.current
    var logs by remember { mutableStateOf<List<team.sakhi.models.PeriodLog>>(emptyList()) }
    var isLoaded by remember { mutableStateOf(false) }
    // iOS: `dateStyle = .medium`, in the phone's own locale.
    val connectedSince = remember(partnership.id, partnership.createdAt) {
        val date = partnershipStartDate(partnership)
        java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
            .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
    }

    LaunchedEffect(partnership.userId) {
        periodLogRepository.getAll(partnership.userId).onSuccess { all ->
            logs = all
                .filter { it.loggedBy == team.sakhi.models.LogSource.PARTNER }
                .sortedByDescending { it.logDate.toString() }
        }
        isLoaded = true
    }
    val moments = remember(walks, logs, name, isPartnerRole, actions) {
        careMoments(context, walks, logs.map { careLoggedDay(it.logDate) }, isPartnerRole, name, actions)
    }

    val pageTop = sakhiLightPink()
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
        SakhiNavBar(onBack = onBack)
        // A large title under the back button, as every page opened from Care has.
        Text(
            text = if (isPartnerRole) {
                stringResource(R.string.care_section_moments_partner)
            } else {
                stringResource(R.string.care_section_moments_owner, name)
            },
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiLabel(),
            modifier = Modifier.padding(
                start = SakhiSpacing.space6,
                end = SakhiSpacing.space6,
                top = SakhiSpacing.space6,
                bottom = SakhiSpacing.space3,
            ),
        )
        // Nothing on the page until the days are read, as iOS: a spinner for a moment reads as
        // something being wrong on a page that is only a list.
        if (!isLoaded && walks.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth())
        } else if (moments.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = sakhiTertiaryLabel(),
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = stringResource(R.string.care_history_empty_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = sakhiSecondaryLabel(),
                    modifier = Modifier.padding(top = SakhiSpacing.space3),
                )
                Text(
                    text = if (isPartnerRole) {
                        stringResource(R.string.care_history_empty_partner)
                    } else {
                        stringResource(R.string.care_history_empty_owner, name)
                    },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = sakhiTertiaryLabel(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = SakhiSpacing.space8)
                        .padding(top = SakhiSpacing.space3),
                )
                Spacer(modifier = Modifier.weight(1f))
                ConnectionBadge(connectedDate = connectedSince)
                Spacer(modifier = Modifier.height(SakhiSpacing.space10))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
                contentPadding = PaddingValues(top = SakhiSpacing.space4, bottom = SakhiSpacing.space10),
                flingBehavior = rememberSakhiFlingBehavior(),
            ) {
                // On the page, not in a card: this page is only this list, and a card inside it
                // just boxes it in (Karan, 2026-09-13).
                item(key = "timeline") {
                    Column(modifier = Modifier.padding(horizontal = SakhiSpacing.space1)) {
                        CareMoments(
                            moments = moments,
                            emptyText = "",
                            dayLabel = { momentDayLabel(it, context) },
                            limit = null,
                        )
                    }
                }
                item(key = "since") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = SakhiSpacing.space6),
                        contentAlignment = Alignment.Center,
                    ) {
                        ConnectionBadge(connectedDate = connectedSince)
                    }
                }
            }
        }
    }
}

/**
 * Leaving (her person) or removing (her), as its own screen rather than an alert: what
 * actually changes, said plainly, and the choice in the footer. An alert gave one line to
 * the most consequential thing on the Care screen.
 */
@Composable
private fun LeaveConnectionContent(
    isPartnerRole: Boolean,
    name: String,
    isRemoving: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    val pageTop = sakhiLightPink()
    val pageBottom = androidx.compose.ui.graphics.lerp(sakhiLightPink(), sakhiSystemBackground(), 0.82f)
    val loadingSlot: @Composable () -> Unit = { CareLoadingButton() }
    val points = if (isPartnerRole) {
        listOf(
            Icons.Filled.VisibilityOff to stringResource(R.string.care_leave_point_see_partner),
            Icons.Filled.DirectionsCar to stringResource(R.string.care_leave_point_walk_partner),
            Icons.Filled.History to stringResource(R.string.care_leave_point_history_partner),
            Icons.Filled.PersonAdd to stringResource(R.string.care_leave_point_again_partner),
        )
    } else {
        listOf(
            Icons.Filled.VisibilityOff to stringResource(R.string.care_leave_point_see_owner, name),
            Icons.Filled.DirectionsCar to stringResource(R.string.care_leave_point_walk_owner, name),
            Icons.Filled.History to stringResource(R.string.care_leave_point_history_owner, name),
            Icons.Filled.PersonAdd to stringResource(R.string.care_leave_point_again_owner),
        )
    }
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
        SakhiNavBar(onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = SakhiSpacing.space6)
                    .size(72.dp)
                    .background(sakhiSystemBackground(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.HeartBroken,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = if (isPartnerRole) {
                    stringResource(R.string.care_leave_title_partner)
                } else {
                    stringResource(R.string.care_leave_title_owner, name)
                },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = SakhiSpacing.space6, end = SakhiSpacing.space6, top = SakhiSpacing.space5),
            )
            Text(
                text = if (isPartnerRole) {
                    stringResource(R.string.care_leave_sub_partner)
                } else {
                    stringResource(R.string.care_leave_sub_owner)
                },
                fontSize = 15.sp,
                color = sakhiSecondaryLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = SakhiSpacing.space6, end = SakhiSpacing.space6, top = SakhiSpacing.space2),
            )
            Spacer(modifier = Modifier.height(28.dp))
            // On the page, not in a card: this screen is only this list.
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = SakhiSpacing.space1)) {
                points.forEachIndexed { index, (icon, text) ->
                    if (index > 0) SakhiListDivider(startInset = SakhiSpacing.space5 + 34.dp + SakhiSpacing.space3)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SakhiSpacing.space5, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp).background(sakhiLightPink(), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            text = text,
                            fontSize = 15.sp,
                            color = sakhiLabel(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(SakhiSpacing.space8))
        }
        // The choice, where every Sakhi screen keeps its one action: leaving on the button,
        // and staying as the way out beside it.
        SakhiFooter(
            primaryLabel = if (isPartnerRole) {
                stringResource(R.string.care_leave_cta_partner)
            } else {
                stringResource(R.string.care_leave_cta_owner, name)
            },
            onPrimaryClick = onConfirm,
            primaryEnabled = !isRemoving,
            primarySlot = if (isRemoving) loadingSlot else null,
            secondaryLabel = if (isPartnerRole) {
                stringResource(R.string.care_leave_keep_partner)
            } else {
                stringResource(R.string.care_leave_keep_owner, name)
            },
            onSecondaryClick = onBack,
        )
    }
}

@Composable
private fun ConnectionBadge(connectedDate: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(R.string.care_taking_care_since, connectedDate),
            fontSize = 12.sp,
            color = sakhiTertiaryLabel(),
        )
    }
}

/** iOS `.lineSpacing(4)` on the pending description. */
private val CarePendingSubtitleLineHeight = 22.sp
