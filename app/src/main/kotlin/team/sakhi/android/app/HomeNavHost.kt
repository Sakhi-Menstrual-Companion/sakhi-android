package team.sakhi.android.app

import team.sakhi.care.CareRuntimeState
import team.sakhi.care.CareStore
import team.sakhi.android.feature.care.LogPermissionRequestSheet
import team.sakhi.android.ui.FeatureAccessGate
import team.sakhi.access.AppFeature
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
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
import team.sakhi.android.feature.ai.ChatScreen
import team.sakhi.android.feature.calendar.CalendarScreen
import team.sakhi.android.feature.care.CareScreen
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
import team.sakhi.android.feature.profile.PrivacySecurityScreen
import team.sakhi.android.feature.profile.ProfileScreen
import team.sakhi.android.feature.reports.ReportsScreen
import team.sakhi.android.ui.rememberSakhiModalSheetState
import team.sakhi.android.ui.SakhiNavDirection
import team.sakhi.android.ui.SakhiScreenTransition
import team.sakhi.android.ui.SakhiSheetContentTransition
import team.sakhi.deeplink.SakhiDeepLink
import kotlinx.coroutines.launch

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
    // Non-null when opened for a specific date other than today -- e.g. Calendar's
    // own "Log" button/quick-log menu, matching iOS's real per-date `calendarLogVM`.
    data class Logging(val initialDate: LocalDate? = null) : HomeOverlaySheet
    data class Care(val prefillInviteCode: String? = null) : HomeOverlaySheet
}

private enum class ProfileSheetScreen {
    Root,
    Reports,
    EditProfile,
    LogHistory,
    AppIntegration,
    Notifications,
    Appearance,
    HelpSupport,
    PrivacySecurity,
    Legal,
    About,
    Feedback,
    ManageAccount,
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
            is SakhiDeepLink.OpenReport -> activeOverlaySheet = HomeOverlaySheet.Profile(initialScreen = ProfileSheetScreen.Reports)
            is SakhiDeepLink.OpenAIChat -> activeOverlaySheet = HomeOverlaySheet.Chat
            is SakhiDeepLink.OpenProfile -> activeOverlaySheet = HomeOverlaySheet.Profile()
            // No Android "live safety session" screen exists yet (checked -- not in
            // this session's known-built feature list), so this deliberately
            // doesn't route anywhere rather than faking a destination; documented
            // as a real, separate gap in the plan file, not silently dropped.
            is SakhiDeepLink.OpenEmergency -> Unit
            // Doesn't apply once already inside Home.
            SakhiDeepLink.OpenOnboarding -> Unit
            SakhiDeepLink.Unknown -> Unit
        }
        AndroidDeepLinkManager.consume(pending.id)
    }

    NavHost(navController = navController, startDestination = HomeGraphRoute.Home) {
        composable<HomeGraphRoute.Home> {
            HomeScreen(
                viewModel = homeViewModel,
                onOpenProfile = { presentOverlaySheet(HomeOverlaySheet.Profile()) },
                onOpenCare = { presentOverlaySheet(HomeOverlaySheet.Care()) },
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

    HomeCalendarOverlay(
        visible = showCalendar && activeOverlaySheet == null,
        onDismiss = { showCalendar = false },
    ) { expanded, setExpanded ->
        CalendarScreen(
            onAskSakhi = { presentOverlaySheet(HomeOverlaySheet.Chat) },
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

    activeOverlaySheet?.takeIf { it !is HomeOverlaySheet.Calendar }?.let { sheet ->
        SakhiModalSheet(
            onDismissRequest = {
                finishOverlaySheetDismiss(sheet)
            },
            sheetState = overlaySheetState,
        ) {
            // Peer sheet swaps are not full-screen pushes. Raindrop uses native modal
            // presentation plus short opacity fades for lightweight modal content
            // changes; Sakhi keeps that same host-owned motion here.
            SakhiSheetContentTransition(
                targetState = sheet,
                label = "home_overlay_sheet_transition",
            ) { targetSheet ->
                when (targetSheet) {
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
                    HomeOverlaySheet.Chat -> FeatureAccessGate(
                        feature = AppFeature.SAKHI_AI_CHAT,
                        onBack = ::dismissOverlaySheet,
                    ) {
                        ChatScreen(onClose = ::dismissOverlaySheet)
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
                    ) {
                        CareScreen(
                            prefillInviteCode = targetSheet.prefillInviteCode,
                            onClose = ::dismissOverlaySheet,
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
    // back-dismiss for free (real `ModalBottomSheet`), but that only closes the whole
    // sheet -- it doesn't know about this flat root/sub-screen state underneath it.
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
    ) { targetScreen ->
        when (targetScreen) {
            ProfileSheetScreen.Root -> ProfileScreen(
                onEditProfileClick = { screen = ProfileSheetScreen.EditProfile },
                onHealthDataClick = { screen = ProfileSheetScreen.Reports },
                onLogHistoryClick = { screen = ProfileSheetScreen.LogHistory },
                onAppIntegrationClick = { screen = ProfileSheetScreen.AppIntegration },
                onNotificationsClick = { screen = ProfileSheetScreen.Notifications },
                onAppearanceClick = { screen = ProfileSheetScreen.Appearance },
                onHelpSupportClick = { screen = ProfileSheetScreen.HelpSupport },
                onPrivacySecurityClick = { screen = ProfileSheetScreen.PrivacySecurity },
                onLegalClick = { screen = ProfileSheetScreen.Legal },
                onAboutClick = { screen = ProfileSheetScreen.About },
                onFeedbackClick = { screen = ProfileSheetScreen.Feedback },
                onManageAccountClick = { screen = ProfileSheetScreen.ManageAccount },
                // iOS's `DSNavBar(onClose:)` on ProfileView dismisses the whole sheet
                // (`AppCoordinator.shared.dismissSheet()`), not just this sub-screen.
                onClose = onDismiss,
            )
            ProfileSheetScreen.Reports -> ReportsScreen(onClose = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.EditProfile -> EditProfileScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.LogHistory -> ActivityLogScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.AppIntegration -> AppIntegrationScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.Notifications -> NotificationsScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.Appearance -> AppearanceScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.HelpSupport -> HelpSupportScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.PrivacySecurity -> PrivacySecurityScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.Legal -> LegalScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.About -> AboutScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.Feedback -> FeedbackScreen(onBack = { screen = ProfileSheetScreen.Root })
            ProfileSheetScreen.ManageAccount -> ManageAccountScreen(onBack = { screen = ProfileSheetScreen.Root })
        }
    }
}
