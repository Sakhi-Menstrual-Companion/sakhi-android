package team.sakhi.android.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import team.sakhi.android.ui.SakhiModalSheet
import team.sakhi.android.feature.ai.ChatScreen
import team.sakhi.android.feature.calendar.CalendarScreen
import team.sakhi.android.feature.care.CareScreen
import team.sakhi.android.feature.home.HomeScreen
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
import team.sakhi.android.ui.SheetSurface
import team.sakhi.deeplink.SakhiDeepLink

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
    data object Chat : HomeOverlaySheet
    data object Logging : HomeOverlaySheet
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
    val overlaySheetState = rememberSakhiModalSheetState()

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
                onOpenProfile = { activeOverlaySheet = HomeOverlaySheet.Profile() },
                onOpenCare = { activeOverlaySheet = HomeOverlaySheet.Care() },
                onOpenCalendar = { activeOverlaySheet = HomeOverlaySheet.Calendar },
                onOpenChat = { activeOverlaySheet = HomeOverlaySheet.Chat },
                onQuickLogClick = { activeOverlaySheet = HomeOverlaySheet.Logging },
            )
        }
    }

    activeOverlaySheet?.let { sheet ->
        SakhiModalSheet(
            onDismissRequest = { activeOverlaySheet = null },
            sheetState = overlaySheetState,
        ) {
            when (sheet) {
                is HomeOverlaySheet.Profile -> ProfileOverlaySheet(initialScreen = sheet.initialScreen)
                HomeOverlaySheet.Calendar -> SheetSurface(showDragHandle = true) { CalendarScreen() }
                HomeOverlaySheet.Chat -> ChatScreen(onClose = { activeOverlaySheet = null })
                HomeOverlaySheet.Logging -> LoggingSheet(onClose = { activeOverlaySheet = null })
                is HomeOverlaySheet.Care -> CareScreen(prefillInviteCode = sheet.prefillInviteCode)
            }
        }
    }
}

@Composable
private fun ProfileOverlaySheet(
    initialScreen: ProfileSheetScreen,
) {
    var screen by remember(initialScreen) { mutableStateOf(initialScreen) }

    when (screen) {
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
