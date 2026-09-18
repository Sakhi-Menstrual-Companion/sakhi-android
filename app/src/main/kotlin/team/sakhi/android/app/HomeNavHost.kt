package team.sakhi.android.app

import android.content.Intent
import android.net.Uri
import team.sakhi.android.BuildConfig
import team.sakhi.android.feature.home.inbox.NotificationInboxScreen
import team.sakhi.android.feature.profile.OfflineModeScreen
import team.sakhi.care.CareRuntimeState
import team.sakhi.care.CareStore
import team.sakhi.android.feature.care.LogPermissionRequestSheet
import team.sakhi.android.ui.FeatureAccessGate
import team.sakhi.access.AppFeature
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.ui.SakhiModalSheet
import team.sakhi.android.ui.SheetSurface
import team.sakhi.android.feature.ai.ChatScreen
import team.sakhi.android.feature.emergency.EmergencyFlowScreen
import team.sakhi.android.feature.calendar.CalendarScreen
import team.sakhi.android.feature.care.CareScreen
import team.sakhi.android.feature.care.StayWithMeLiveLayer
import team.sakhi.staywithme.StayWithMeStore
import team.sakhi.android.feature.home.HomeScreen
import team.sakhi.android.feature.home.HomeViewModel
import team.sakhi.android.feature.logging.LoggingSheet
import team.sakhi.android.feature.profile.AboutScreen
import team.sakhi.android.feature.profile.ActivityLogScreen
import team.sakhi.android.feature.profile.AppIntegrationScreen
import team.sakhi.android.feature.profile.AppearanceScreen
import team.sakhi.android.feature.profile.EditProfileScreen
import team.sakhi.android.feature.profile.FeedbackScreen
import team.sakhi.android.feature.profile.HelpSupportScreen
import team.sakhi.android.feature.profile.LegalScreen
import team.sakhi.android.feature.profile.ManageAccountScreen
import team.sakhi.android.feature.profile.NotificationsScreen
import team.sakhi.android.feature.profile.RideAlarmScreen
import team.sakhi.android.feature.profile.PrivacySecurityScreen
import team.sakhi.android.feature.profile.ProfileScreen
import team.sakhi.android.feature.reports.ReportsScreen
import team.sakhi.android.ui.rememberSakhiModalSheetState
import team.sakhi.android.ui.SakhiNavDirection
import team.sakhi.android.ui.SakhiScreenTransition
import team.sakhi.android.ui.SakhiSheetContentTransition
import team.sakhi.deeplink.SakhiDeepLink
import kotlinx.coroutines.launch
import team.sakhi.notifications.InAppNotificationStore

/**
 * Type-safe Navigation Compose graph (plan Section 8) for everything reachable
 * from Home. iOS has no tab bar — Home's hamburger opens Profile, its person icon
 * opens Care, its bottom bar opens Calendar/Chat/Logging (see `HomeScreen`'s doc
 * comment for the exact iOS source lines this mirrors). Profile's own internal
 * "Health Data" row opens Reports, matching iOS's `ProfileRoute` navigation stack.
 *
 * Recommendations has no route here on purpose: on iOS it is not a standalone
 * screen, it is a set of cards ("What to Eat" / "Sakhi's tip for today") embedded
 * in Home's day-detail view. That day-detail view now exists (`HomeScreen.kt`'s
 * `NutritionCard`/`SakhiInsightCard`, backed by the real `RecommendationsViewModel`)
 * so this is no longer a gap — adding a top-level route here would still be an
 * iOS parity bug, since iOS never navigates to Recommendations as its own screen.
 *
 * Presentation style: iOS shows Home-owned surfaces as sheets/overlays, not plain
 * full-screen pushes. Android now matches that for the whole first-layer Home-owned
 * family: Profile, Care, Calendar, Chat, and Logging all open through the shared
 * modal-sheet lane over Home. Profile's child screens stay inside a local
 * sheet-owned stack too, so Home-owned entry points and signed-in report/profile
 * deep links now share the same presentation lane instead of splitting between
 * overlay and root-route paths.
 */
private sealed interface HomeOverlaySheet {
    data class Profile(val initialScreen: ProfileSheetScreen = ProfileSheetScreen.Root) : HomeOverlaySheet
    data object Calendar : HomeOverlaySheet
    data object LogPermissionRequest : HomeOverlaySheet
    data object Chat : HomeOverlaySheet
    /** The in-app inbox behind Home's bell. Also `sakhi://notifications`. */
    data object Notifications : HomeOverlaySheet
    // Emergency Assistance. `deepLinkRequestId` is set when arriving from a
    // sakhi://emergency/{id} link or an SOS notification, so the flow restores that
    // session instead of starting a fresh request.
    data class Emergency(
        val deepLinkRequestId: String? = null,
        // True when arriving from a nearby-request push: land straight on the responder
        // inbox rather than on "what do you need", because she was asked to help, not
        // asked what she needs.
        val openResponderInbox: Boolean = false,
    ) : HomeOverlaySheet
    // Non-null when opened for a specific date other than today -- e.g. Calendar's
    // own "Log" button/quick-log menu, matching iOS's real per-date `calendarLogVM`.
    data class Logging(val initialDate: LocalDate? = null) : HomeOverlaySheet
    data class Care(val prefillInviteCode: String? = null) : HomeOverlaySheet
    /** A live Stay With Me walk, full screen like Emergency. Also `sakhi://care/stay/{id}`. */
    data object StayWithMe : HomeOverlaySheet
}

private enum class ProfileSheetScreen {
    Root,
    Reports,
    EditProfile,
    LogHistory,
    AppIntegration,
    Notifications,
    /** iOS `ProfileRoute.rideAlarm`, the partner-only "Alarm if she is not home". */
    RideAlarm,
    Appearance,
    HelpSupport,
    PrivacySecurity,
    Legal,
    About,
    Feedback,
    ManageAccount,
    /** iOS `navigator.push(.offlineMode)` from the Account group's "Use Sakhi offline". */
    OfflineMode,
}

@Serializable
private sealed interface HomeGraphRoute {
    @Serializable data object Home : HomeGraphRoute
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeNavHost() {
    val navController = rememberNavController()
    var activeOverlaySheet by remember { mutableStateOf<HomeOverlaySheet?>(null) }
    // iOS shows the calendar sheet on Home by DEFAULT -- `HomeView.swift` declares
    // `showCalendarInitially: Bool = true` and only passes false for the authenticated
    // bootstrap path. Android was hiding it until the calendar button was tapped, which
    // is why Home showed a bare stack of day-detail cards where every iOS screenshot
    // shows the white calendar sheet covering that area. Scrolling Home dismisses it,
    // matching iOS's `onScrollBegan`.
    var showCalendar by remember { mutableStateOf(true) }
    val overlaySheetState = rememberSakhiModalSheetState()
    // The inbox's state, for the badge on Home's bell. The same store the inbox sheet reads,
    // so the badge and the list can never disagree.
    val inboxStore = koinInject<InAppNotificationStore>()
    val inboxState by inboxStore.state.collectAsStateWithLifecycle()
    val overlayScope = rememberCoroutineScope()
    // Hoisted (rather than left to HomeScreen's own default `koinViewModel()`) so
    // the Logging sheet's dismiss below can call `refreshSelectedDate()` on the
    // exact same instance Home reads `hasLoggedForSelectedDate`/`selectedLog`
    // from. Real bug found on
    // the first-ever signed-in walkthrough: `LoggingSheet` uses its own
    // `koinViewModel<LoggingViewModel>()` instance (unrelated to Home's own
    // `quickLogViewModel`), and this sheet never leaves the Activity resumed
    // state, so neither of `HomeScreen`'s two existing refresh triggers
    // (ON_RESUME, `quickLogViewModel.isSaving` flip) ever fired after a save
    // through this sheet -- "Logged today" stayed stuck on "Log your day" even
    // though the save itself succeeded and persisted correctly.
    val homeViewModel: HomeViewModel = koinViewModel()
    // Needed to resolve the partnership the log request is sent against.
    val careRuntimeState by koinInject<CareStore>().careState.collectAsStateWithLifecycle()
    // Home's Care button goes straight to a live walk, on either side, instead of to the
    // sheet that would only hand off to it.
    val stayWithMeStore = koinInject<StayWithMeStore>()
    val liveWalkMine by stayWithMeStore.mine.collectAsStateWithLifecycle()
    val liveWalkWatching by stayWithMeStore.watching.collectAsStateWithLifecycle()
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    fun finishOverlaySheetDismiss(dismissedSheet: HomeOverlaySheet?) {
        if (dismissedSheet is HomeOverlaySheet.Logging) homeViewModel.refreshAfterLogChange()
        if (activeOverlaySheet == dismissedSheet) activeOverlaySheet = null
    }

    fun dismissOverlaySheet() {
        val dismissedSheet = activeOverlaySheet
        overlayScope.launch {
            runCatching { overlaySheetState.hide() }
            finishOverlaySheetDismiss(dismissedSheet)
        }
    }

    fun presentOverlaySheet(sheet: HomeOverlaySheet) {
        activeOverlaySheet = sheet
    }

    /**
     * Home's top right. Care, and only Care (Karan, 2026-09-17), even while a walk is live:
     * it used to jump to the walk instead, which meant the one way into Care quietly stopped
     * being that for the length of a ride.
     */
    val openCare = { presentOverlaySheet(HomeOverlaySheet.Care()) }

    /** The nearby button on the calendar's bar: the walk when there is one, else starting it. */
    val openWalk = { presentOverlaySheet(HomeOverlaySheet.StayWithMe) }

    // Signed-in deep links resolve here, not in RootNavHost: Care/Reports/Chat/
    // Profile are all routes this graph owns, and Home is guaranteed mounted by
    // the time this composes. Signed-out invite/onboarding links are intercepted
    // earlier in RootNavHost so they can stay in the onboarding lane instead of
    // incorrectly waiting until Home. Consumed once so a config change or
    // recomposition doesn't re-fire the same navigation repeatedly.
    val pendingDeepLink by AndroidDeepLinkManager.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDeepLink?.id) {
        val pending = pendingDeepLink ?: return@LaunchedEffect
        when (val link = pending.link) {
            is SakhiDeepLink.AcceptInvite -> activeOverlaySheet = HomeOverlaySheet.Care(prefillInviteCode = link.code)
            is SakhiDeepLink.OpenCareMode -> activeOverlaySheet = HomeOverlaySheet.Care()
            // sakhi://care/stay/{id}, from a Stay With Me notification: straight to the live
            // walk, full screen. The store already holds the walk, so nothing is passed.
            is SakhiDeepLink.OpenStayWithMe -> activeOverlaySheet = HomeOverlaySheet.StayWithMe
            is SakhiDeepLink.OpenReport -> activeOverlaySheet = HomeOverlaySheet.Profile(initialScreen = ProfileSheetScreen.Reports)
            is SakhiDeepLink.OpenAIChat -> activeOverlaySheet = HomeOverlaySheet.Chat
            is SakhiDeepLink.OpenProfile -> activeOverlaySheet = HomeOverlaySheet.Profile()
            // Emergency Assistance now exists on Android, so this routes for real. The
            // session id comes from sakhi://emergency/{id} or the SOS notification that
            // SakhiFirebaseMessagingService turns into that same link; an empty one is
            // left to open a fresh request rather than trying to restore nothing.
            is SakhiDeepLink.OpenEmergency ->
                activeOverlaySheet = HomeOverlaySheet.Emergency(
                    deepLinkRequestId = link.sessionId.ifBlank { null },
                )
            SakhiDeepLink.OpenEmergencyResponderInbox ->
                activeOverlaySheet = HomeOverlaySheet.Emergency(openResponderInbox = true)
            is SakhiDeepLink.OpenNotifications -> {
                // Sample rows, for checking the design on a phone. Debug builds only.
                if (link.demo && BuildConfig.DEBUG) inboxStore.showDemo()
                activeOverlaySheet = HomeOverlaySheet.Notifications
            }
            // Doesn't apply once already inside Home.
            SakhiDeepLink.OpenOnboarding -> Unit
            SakhiDeepLink.Unknown -> Unit
        }
        AndroidDeepLinkManager.consume(pending.id)
    }

    // Sample inbox rows last only while the inbox sheet is what is showing. Tied to the
    // sheet rather than to the screen's own disposal, because the sheet host can compose
    // and dispose its content while presenting, which dropped the rows the moment they
    // appeared.
    LaunchedEffect(activeOverlaySheet) {
        if (activeOverlaySheet != HomeOverlaySheet.Notifications) inboxStore.exitDemo()
    }

    NavHost(navController = navController, startDestination = HomeGraphRoute.Home) {
        composable<HomeGraphRoute.Home> {
            HomeScreen(
                viewModel = homeViewModel,
                onOpenProfile = { presentOverlaySheet(HomeOverlaySheet.Profile()) },
                // iOS's "How you feel" card presents the activity sheet for the selected
                // day. Android's equivalent view is `ActivityLogScreen`, which already
                // lives behind the profile sheet's LogHistory route -- the same route the
                // OpenReport deep link uses for Reports.
                onOpenLogHistory = {
                    presentOverlaySheet(
                        HomeOverlaySheet.Profile(initialScreen = ProfileSheetScreen.LogHistory),
                    )
                },
                onOpenCare = openCare,
                onOpenNotifications = { presentOverlaySheet(HomeOverlaySheet.Notifications) },
                unreadNotificationCount = inboxState.unreadCount,
                onOpenCalendar = { showCalendar = true },
                onCloseCalendar = { showCalendar = false },
                onOpenChat = { presentOverlaySheet(HomeOverlaySheet.Chat) },
                onQuickLogClick = { date -> presentOverlaySheet(HomeOverlaySheet.Logging(initialDate = date)) },
                // A partner tapping the locked log button gets iOS's request sheet
                // rather than a dead button.
                onLockedLogClick = { presentOverlaySheet(HomeOverlaySheet.LogPermissionRequest) },
            )
        }
    }

    // Held back while Home is on its first-load skeleton, and raised once her data is in, as
    // iOS does (`canPresentOwnCalendar` waits for `!isLoading`). Up during the skeleton it
    // showed a partner his own empty calendar for the seconds before his role was settled
    // (2026-09-13).
    val homeFirstLoading = homeUiState.isLoadingCycle && !homeUiState.hasCycleData
    HomeCalendarOverlay(
        visible = showCalendar && activeOverlaySheet == null && !homeFirstLoading,
        onDismiss = { showCalendar = false },
        // `homeUiState.phase` is recomputed for `selectedDate` on every day tap
        // (`HomeViewModel.selectDate`), so this is iOS's `snapshot.displayPhase` -- the
        // selected day's phase -- and the sheet re-tints with the selection in dark mode.
        phase = homeUiState.phase,
    ) { expanded, setExpanded ->
        CalendarScreen(
            onAskSakhi = { presentOverlaySheet(HomeOverlaySheet.Chat) },
            // The nearby button in the calendar's bottom bar, matching iOS's
            // `HomeCalendarSheet` -> `HomeNearbyButton` -> `.fullScreenCover`.
            onOpenEmergency = { openInbox ->
                presentOverlaySheet(HomeOverlaySheet.Emergency(openResponderInbox = openInbox))
            },
            onOpenCare = openCare,
            onOpenWalk = openWalk,
            onLog = { date -> presentOverlaySheet(HomeOverlaySheet.Logging(initialDate = date)) },
            onDaySelected = homeViewModel::selectDate,
            // Detent and year mode are the same concept on iOS: dragging the sheet
            // up crossfades month -> year, and the month-header chevron expands the
            // sheet rather than swapping content underneath a static sheet. Binding
            // them here also fills the expanded detent, which previously showed a
            // large empty area below the month grid.
            yearExpanded = expanded,
            onYearExpandedChange = setExpanded,
            currentPhase = homeUiState.phase,
            // Home resolves its own phase/day and has no idea a log was made from inside
            // the calendar sheet, which owns a separate `LoggingViewModel`.
            onLogChanged = homeViewModel::refreshAfterLogChange,
        )
    }

    // Emergency Assistance is FULL SCREEN, not a sheet.
    //
    // Both of iOS's presentation sites use `.fullScreenCover` -- `RootView` for the deep
    // link and SOS notification, and `SakhiAIChatView` for the Nearby button. Routing it
    // through the shared modal-sheet lane left Home visible above it and gave it a sheet's
    // rounded top, which is a different presentation from the one iOS ships. It also owns
    // its own map-plus-sheet layout internally (`BottomSheetScaffold`), exactly as iOS's
    // `EmergencyFlowView` owns its map and `EmergencySheetContent`; that inner sheet is the
    // one that is meant to look like a sheet, not the screen containing it.
    (activeOverlaySheet as? HomeOverlaySheet.Emergency)?.let { emergency ->
        Box(modifier = Modifier.fillMaxSize()) {
            FeatureAccessGate(
                feature = AppFeature.EMERGENCY_ASSISTANCE,
                onBack = ::dismissOverlaySheet,
            ) {
                EmergencyFlowScreen(
                    onClose = ::dismissOverlaySheet,
                    deepLinkRequestId = emergency.deepLinkRequestId,
                    openResponderInbox = emergency.openResponderInbox,
                )
            }
        }
    }

    // A live walk is full screen too, for the same reason Emergency is: the map is the
    // screen, not a pane inside a sheet over Home. Its own layout carries the panel.
    if (activeOverlaySheet == HomeOverlaySheet.StayWithMe) {
        Box(modifier = Modifier.fillMaxSize()) {
            StayWithMeLiveLayer(
                onClose = ::dismissOverlaySheet,
                // The walk intro's one action, for someone who has nobody on Be Her Sakhi
                // yet: the same Care screen Home's top-right button opens, where a care
                // partner is invited.
                onAddCarePartner = openCare,
            )
        }
    }

    activeOverlaySheet
        ?.takeIf {
            it !is HomeOverlaySheet.Calendar &&
                it !is HomeOverlaySheet.Emergency &&
                it != HomeOverlaySheet.StayWithMe
        }
        ?.let { sheet ->
        SakhiModalSheet(
            onDismissRequest = {
                finishOverlaySheetDismiss(sheet)
            },
            sheetState = overlaySheetState,
            // Chat's header no longer carries a close button, because iOS's does not:
            // `SakhiAIChatView` says outright that "the sheet has no X any more, so closing
            // is its grabber, which is shown for exactly that reason", and warns against
            // hiding the grabber without putting a close button back somewhere.
            //
            // This is the shared host for these overlay sheets, which is the same shape
            // iOS has (`HomeView.sharedSheetView` owns the grabber for all of them), so the
            // handle belongs here rather than inside Chat.
            // FALSE, deliberately. `SakhiModalSheet` sets `containerColor = Color.Transparent`,
            // so the sheet's own container is invisible and the white rounded card is drawn by
            // `SheetSurface` INSIDE the content. Material renders `dragHandle` in that
            // invisible container, above the card — so the grabber floated over the dimmed
            // Home content instead of sitting on the sheet, and pushed the close button down
            // below a gap. The grabber belongs inside `SheetSurface`, which owns the surface
            // it should sit on.
            showSystemDragHandle = false,
            // Every sheet here fills the height on a `SheetSurface`, so an empty one is the
            // right size to slide in while the real content is built.
            placeholder = { SheetSurface {} },
        ) {
            // Peer sheet swaps are not full-screen pushes. Raindrop uses native modal
            // presentation plus short opacity fades for lightweight modal content
            // changes; Sakhi keeps that same host-owned motion here.
            SakhiSheetContentTransition(
                targetState = sheet,
                label = "home_overlay_sheet_transition",
            ) { targetSheet ->
                when (targetSheet) {
                    // Handled above as full-screen layers, never in this sheet host.
                    is HomeOverlaySheet.Emergency -> Unit
                    HomeOverlaySheet.StayWithMe -> Unit
                    is HomeOverlaySheet.Profile -> ProfileOverlaySheet(
                        initialScreen = targetSheet.initialScreen,
                        onDismiss = ::dismissOverlaySheet,
                    )
                    // Calendar is deliberately NOT here: it renders as an in-tree
                    // overlay over Home (see HomeCalendarOverlay below), matching
                    // iOS's ZStack sheet rather than a separate scrimmed window.
                    HomeOverlaySheet.Calendar -> Unit
                    // Gated exactly as iOS gates them: both features are
                    // `requiresInternet = true` in the shared `AppFeature`, so a guest
                    // account or a paused/unreachable cloud gets the explainer instead
                    // of a screen that silently cannot work.
                    HomeOverlaySheet.LogPermissionRequest -> {
                        val partnership = (careRuntimeState as? CareRuntimeState.PartnerConnected)?.partnership
                        if (partnership == null) {
                            // No partnership means nothing to request against; close
                            // rather than render a sheet that cannot send anything.
                            LaunchedEffect(Unit) { dismissOverlaySheet() }
                        } else {
                            LogPermissionRequestSheet(
                                primaryUserId = partnership.userId,
                                partnershipId = partnership.id,
                                partnerName = partnership.partnerName,
                                onClose = ::dismissOverlaySheet,
                            )
                        }
                    }
                    // A tapped row goes through the same AndroidDeepLinkManager pipeline a tapped
                    // push does. Setting the destination swaps this sheet's content for it, so
                    // the inbox is replaced rather than stacked under the next screen.
                    HomeOverlaySheet.Notifications -> NotificationInboxScreen(
                        onClose = ::dismissOverlaySheet,
                        onOpenLink = { uri ->
                            AndroidDeepLinkManager.handleIntent(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                        },
                    )
                    HomeOverlaySheet.Chat -> FeatureAccessGate(
                        feature = AppFeature.SAKHI_AI_CHAT,
                        onBack = ::dismissOverlaySheet,
                        presentedAsSheet = true,
                    ) {
                        ChatScreen(
                            onClose = ::dismissOverlaySheet,
                            onOpenEmergency = { activeOverlaySheet = HomeOverlaySheet.Emergency() },
                        )
                    }
                    is HomeOverlaySheet.Logging -> LoggingSheet(
                        hasPeriodData = homeUiState.hasCycleData || homeUiState.cyclesAnalyzed > 0,
                        initialDate = targetSheet.initialDate,
                        onClose = {
                            // Full reload, not the narrow selected-date refresh: a log
                            // save can create or move an entire cycle, and the narrow
                            // one leaves phase/dayInCycle/currentCycle stale.
                            dismissOverlaySheet()
                        },
                    )
                    is HomeOverlaySheet.Care -> FeatureAccessGate(
                        feature = AppFeature.BE_HER_SAKHI,
                        onBack = ::dismissOverlaySheet,
                        presentedAsSheet = true,
                    ) {
                        CareScreen(
                            prefillInviteCode = targetSheet.prefillInviteCode,
                            onClose = ::dismissOverlaySheet,
                            onOpenLiveWalk = { activeOverlaySheet = HomeOverlaySheet.StayWithMe },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileOverlaySheet(
    initialScreen: ProfileSheetScreen,
    onDismiss: () -> Unit,
) {
    var screen by remember(initialScreen) { mutableStateOf(initialScreen) }

    // The outer `SakhiModalSheet` this content lives in already gets system
    // back-dismiss for free (it presents in a dialog window, whose own back handling runs
    // the sheet's exit animation), but that only closes the whole sheet -- it doesn't know about this flat root/sub-screen state underneath it.
    // Without this, system back from e.g. EditProfile skipped straight past Profile
    // Root and closed the entire sheet. Every sub-screen's own on-screen back arrow
    // already does exactly this same `screen = Root` step, so this just makes system
    // back match it.
    BackHandler(enabled = screen != ProfileSheetScreen.Root) { screen = ProfileSheetScreen.Root }

    // Flat root/sub-screen stack (not multi-level), so direction is fully determined
    // by whether we're entering a sub-screen (push) or returning to Root (pop) -- no
    // separate "was forward" flag needed the way onboarding's linear step list uses one.
    SakhiScreenTransition(
        targetState = screen,
        directionFor = { _, target ->
            if (target == ProfileSheetScreen.Root) SakhiNavDirection.Backward else SakhiNavDirection.Forward
        },
        label = "profile_sheet_transition",
        // Profile's sub-screens are children of Profile, not peers of it: iOS pushes
        // them onto the same `NavigationStack` so Profile stays behind. Without this
        // the shared transition fades Profile fully out mid-slide, which reads as the
        // sub-screen REPLACING it. Onboarding keeps the fade -- its steps really are
        // peers with no parent underneath.
        parentStaysBehind = true,
    ) { targetScreen ->
        when (targetScreen) {
            ProfileSheetScreen.Root -> ProfileScreen(
                onEditProfileClick = { screen = ProfileSheetScreen.EditProfile },
                onHealthDataClick = { screen = ProfileSheetScreen.Reports },
                onLogHistoryClick = { screen = ProfileSheetScreen.LogHistory },
                onAppIntegrationClick = { screen = ProfileSheetScreen.AppIntegration },
                onNotificationsClick = { screen = ProfileSheetScreen.Notifications },
                onRideAlarmClick = { screen = ProfileSheetScreen.RideAlarm },
                onAppearanceClick = { screen = ProfileSheetScreen.Appearance },
                onHelpSupportClick = { screen = ProfileSheetScreen.HelpSupport },
                onPrivacySecurityClick = { screen = ProfileSheetScreen.PrivacySecurity },
                onLegalClick = { screen = ProfileSheetScreen.Legal },
                onAboutClick = { screen = ProfileSheetScreen.About },
                onFeedbackClick = { screen = ProfileSheetScreen.Feedback },
                onManageAccountClick = { screen = ProfileSheetScreen.ManageAccount },
                onUseOfflineClick = { screen = ProfileSheetScreen.OfflineMode },
                // iOS's `DSNavBar(onClose:)` on ProfileView dismisses the whole sheet
                // (`AppCoordinator.shared.dismissSheet()`), not just this sub-screen.
                onClose = onDismiss,
            )
            ProfileSheetScreen.Reports -> ReportsScreen(onClose = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.EditProfile -> EditProfileScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.LogHistory -> ActivityLogScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.AppIntegration -> AppIntegrationScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.Notifications -> NotificationsScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.RideAlarm -> RideAlarmScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.Appearance -> AppearanceScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.HelpSupport -> HelpSupportScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.PrivacySecurity -> PrivacySecurityScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.Legal -> LegalScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.About -> AboutScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.Feedback -> FeedbackScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.ManageAccount -> ManageAccountScreen(onBack = { screen = ProfileSheetScreen.Root })
            // Closes back to Profile on its own once the switch has been made, which is what
            // `OfflineModeScreen` calls `onClose` for.
            ProfileSheetScreen.OfflineMode -> OfflineModeScreen(onClose = { screen = ProfileSheetScreen.Root })
        }
    }
}
