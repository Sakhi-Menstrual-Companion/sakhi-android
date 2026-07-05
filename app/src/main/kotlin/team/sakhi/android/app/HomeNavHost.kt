package team.sakhi.android.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
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
 * Presentation style: iOS shows Profile/Care/Calendar/Chat/Logging as sheets with
 * specific detents. This graph uses plain full-screen pushes for now — matching
 * the *destinations* while leaving the *presentation* (a real modal sheet lane)
 * as a tracked follow-up, same as every other "wiring first, parity later" module
 * this session.
 */
@Serializable
private sealed interface HomeGraphRoute {
    @Serializable data object Home : HomeGraphRoute
    @Serializable data object Profile : HomeGraphRoute
    @Serializable data class Care(val prefillInviteCode: String? = null) : HomeGraphRoute
    @Serializable data object Calendar : HomeGraphRoute
    @Serializable data object Chat : HomeGraphRoute
    @Serializable data object Logging : HomeGraphRoute
    @Serializable data object Reports : HomeGraphRoute
    @Serializable data object LogHistory : HomeGraphRoute
    @Serializable data object AppIntegration : HomeGraphRoute
    @Serializable data object Notifications : HomeGraphRoute
    @Serializable data object Appearance : HomeGraphRoute
    @Serializable data object HelpSupport : HomeGraphRoute
    @Serializable data object PrivacySecurity : HomeGraphRoute
    @Serializable data object Legal : HomeGraphRoute
    @Serializable data object About : HomeGraphRoute
    @Serializable data object Feedback : HomeGraphRoute
    @Serializable data object ManageAccount : HomeGraphRoute
    @Serializable data object EditProfile : HomeGraphRoute
}

@Composable
fun HomeNavHost() {
    val navController = rememberNavController()

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
            is SakhiDeepLink.AcceptInvite -> navController.navigate(HomeGraphRoute.Care(prefillInviteCode = link.code))
            is SakhiDeepLink.OpenCareMode -> navController.navigate(HomeGraphRoute.Care())
            is SakhiDeepLink.OpenReport -> navController.navigate(HomeGraphRoute.Reports)
            is SakhiDeepLink.OpenAIChat -> navController.navigate(HomeGraphRoute.Chat)
            is SakhiDeepLink.OpenProfile -> navController.navigate(HomeGraphRoute.Profile)
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
                onOpenProfile = { navController.navigate(HomeGraphRoute.Profile) },
                onOpenCare = { navController.navigate(HomeGraphRoute.Care()) },
                onOpenCalendar = { navController.navigate(HomeGraphRoute.Calendar) },
                onOpenChat = { navController.navigate(HomeGraphRoute.Chat) },
                onQuickLogClick = { navController.navigate(HomeGraphRoute.Logging) },
            )
        }
        composable<HomeGraphRoute.Profile> {
            ProfileScreen(
                onEditProfileClick = { navController.navigate(HomeGraphRoute.EditProfile) },
                onHealthDataClick = { navController.navigate(HomeGraphRoute.Reports) },
                onLogHistoryClick = { navController.navigate(HomeGraphRoute.LogHistory) },
                onAppIntegrationClick = { navController.navigate(HomeGraphRoute.AppIntegration) },
                onNotificationsClick = { navController.navigate(HomeGraphRoute.Notifications) },
                onAppearanceClick = { navController.navigate(HomeGraphRoute.Appearance) },
                onHelpSupportClick = { navController.navigate(HomeGraphRoute.HelpSupport) },
                onPrivacySecurityClick = { navController.navigate(HomeGraphRoute.PrivacySecurity) },
                onLegalClick = { navController.navigate(HomeGraphRoute.Legal) },
                onAboutClick = { navController.navigate(HomeGraphRoute.About) },
                onFeedbackClick = { navController.navigate(HomeGraphRoute.Feedback) },
                onManageAccountClick = { navController.navigate(HomeGraphRoute.ManageAccount) },
            )
        }
        composable<HomeGraphRoute.Care> { backStackEntry ->
            val route = backStackEntry.toRoute<HomeGraphRoute.Care>()
            CareScreen(prefillInviteCode = route.prefillInviteCode)
        }
        composable<HomeGraphRoute.Calendar> {
            CalendarScreen()
        }
        composable<HomeGraphRoute.Chat> {
            ChatScreen(onClose = { navController.popBackStack() })
        }
        composable<HomeGraphRoute.Logging> {
            LoggingSheet(onClose = { navController.popBackStack() })
        }
        composable<HomeGraphRoute.Reports> {
            ReportsScreen()
        }
        composable<HomeGraphRoute.LogHistory> {
            ActivityLogScreen(onBack = { navController.popBackStack() })
        }
        composable<HomeGraphRoute.AppIntegration> {
            AppIntegrationScreen(onBack = { navController.popBackStack() })
        }
        composable<HomeGraphRoute.Notifications> {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }
        composable<HomeGraphRoute.Appearance> {
            AppearanceScreen(onBack = { navController.popBackStack() })
        }
        composable<HomeGraphRoute.HelpSupport> {
            HelpSupportScreen(onBack = { navController.popBackStack() })
        }
        composable<HomeGraphRoute.PrivacySecurity> {
            PrivacySecurityScreen(onBack = { navController.popBackStack() })
        }
        composable<HomeGraphRoute.Legal> {
            LegalScreen(onBack = { navController.popBackStack() })
        }
        composable<HomeGraphRoute.About> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable<HomeGraphRoute.Feedback> {
            FeedbackScreen(onBack = { navController.popBackStack() })
        }
        composable<HomeGraphRoute.ManageAccount> {
            ManageAccountScreen(onBack = { navController.popBackStack() })
        }
        composable<HomeGraphRoute.EditProfile> {
            EditProfileScreen(onBack = { navController.popBackStack() })
        }
    }
}
