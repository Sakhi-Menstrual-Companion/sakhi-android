package team.sakhi.android.app

import co.touchlab.kermit.Logger
import android.net.NetworkCapabilities
import android.net.ConnectivityManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import team.sakhi.access.FeatureAccessState
import team.sakhi.android.R
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.platform.AndroidAppVersionProvider
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.AndroidWidgetSnapshotManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.ui.ForceUpdateScreen
import team.sakhi.android.ui.SakhiLoadingContext
import team.sakhi.android.ui.SakhiLoadingView
import team.sakhi.android.ui.ToastHost
import team.sakhi.android.ui.ToastManager
import team.sakhi.android.ui.ToastType
import team.sakhi.android.feature.onboarding.OnboardingFlowHost
import team.sakhi.appstate.AppRoute
import team.sakhi.appstate.AppStateInputBridge
import team.sakhi.appstate.AppStateStore
import team.sakhi.auth.AuthRepository
import team.sakhi.auth.resolvedUserId
import team.sakhi.care.CareRealtimeCoordinator
import team.sakhi.care.CareRuntimeState
import team.sakhi.care.CareStore
import team.sakhi.sync.SyncStore
import team.sakhi.deeplink.SakhiDeepLink
import team.sakhi.onboarding.OnboardingCompletionBridge
import team.sakhi.onboarding.OnboardingFlowCompletion
import team.sakhi.platform.NetworkStatus
import team.sakhi.session.SessionManager
import team.sakhi.state.SessionState
import team.sakhi.update.UpdateGateController

private data class ForcedOnboardingDeepLink(
    val flowId: String,
    val pendingInviteCode: String = "",
)

/**
 * Root shell: renders whichever screen KMM's `AppStateStore.appRoute` says to show.
 * This composable must never contain a routing decision of its own (plan Section 0,
 * rule 1 and Section 5, "App shell / navigation") — it only pattern-matches the
 * route KMM already resolved and mounts the corresponding screen/nav-graph.
 *
 * `Home` now hosts its own nested [HomeNavHost] (Profile/Care/Calendar/Chat/
 * Logging/Reports all reachable from it) since that feature area has more than
 * one screen — see `HomeNavHost.kt` for the full graph and why it mirrors iOS's
 * actual navigation shape instead of a bottom tab bar. `Splash` stays a plain
 * loading placeholder while KMM resolves the route.
 */
@Composable
fun RootNavHost() {
    val appStateStore = koinInject<AppStateStore>()
    val networkStatus = koinInject<NetworkStatus>()
    val authRepository = koinInject<AuthRepository>()
    val appStateInputBridge = koinInject<AppStateInputBridge>()
    val onboardingCompletionBridge = koinInject<OnboardingCompletionBridge>()
    val featureAccessState = koinInject<FeatureAccessState>()
    val updateGateController = koinInject<UpdateGateController>()
    val appVersionProvider = koinInject<AndroidAppVersionProvider>()
    val hapticManager = koinInject<AndroidHapticManager>()
    val route by appStateStore.appRoute.collectAsStateWithLifecycle()
    val pendingDeepLink by AndroidDeepLinkManager.pending.collectAsStateWithLifecycle()
    val isOnline by networkStatus.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val updateGateState by updateGateController.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var forcedOnboardingDeepLink by remember { mutableStateOf<ForcedOnboardingDeepLink?>(null) }
    var waitingForForcedOnboardingExit by remember { mutableStateOf(false) }

    // Port of iOS `RootView` + `VersionManager.checkForUpdates` -- runs once per
    // process, before any auth/route gating, so a version-blocked device is
    // blocked regardless of sign-in state (matches the "keep your data safe"
    // motivation in `ForceUpdatePolicy`'s doc comment).
    LaunchedEffect(Unit) {
        // The update fetch can wait until the first composition frame commits; it
        // is not needed to render the first signed-out/home surface, but it does
        // contend with auth restore and Compose work during cold start.
        withFrameNanos { }
        updateGateController.checkNow(appVersionProvider.currentVersion)
    }

    // Silent, non-blocking nudge -- iOS only shows this when an update exists
    // but isn't force-required (`updateAvailable && silent && !forceUpdate`).
    LaunchedEffect(updateGateState.updateAvailable, updateGateState.forceUpdate) {
        if (updateGateState.updateAvailable && !updateGateState.forceUpdate) {
            ToastManager.show(
                title = context.getString(R.string.app_update_available_title),
                message = context.getString(R.string.app_update_available_message),
                type = ToastType.INFO,
            )
        }
    }

    if (updateGateState.forceUpdate) {
        ForceUpdateScreen(
            title = updateGateState.title ?: context.getString(R.string.app_force_update_fallback_title),
            message = updateGateState.message ?: context.getString(R.string.app_force_update_fallback_message),
            onUpdateClick = {
                hapticManager.impact(HapticImpact.MEDIUM)
                val packageName = context.packageName
                val marketUri = updateGateState.updateUrl?.let(Uri::parse)
                    ?: Uri.parse("market://details?id=$packageName")
                val intent = Intent(Intent.ACTION_VIEW, marketUri)
                runCatching { context.startActivity(intent) }.onFailure {
                    val webUri = Uri.parse(
                        updateGateState.updateUrl
                            ?: "https://play.google.com/store/apps/details?id=$packageName",
                    )
                    context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                }
            },
            onSupportClick = {
                hapticManager.impact(HapticImpact.LIGHT)
                ToastManager.show(
                    title = context.getString(R.string.app_support_title),
                    message = context.getString(R.string.app_force_update_support_message),
                    type = ToastType.INFO,
                )
            },
        )
        return
    }

    // Feeds the shared `FeatureAccessState` (registered in Koin 2026-07-05,
    // previously dormant everywhere) so `FeatureAccessResolver` can tell real
    // account state apart from a network blip: `isGuest` from the actual
    // session type (a local-only/offline account, not a real cloud account),
    // `cloudAvailable` from live connectivity. `isOnlineAccountPaused` stays
    // false always -- Android has no "Use Sakhi Offline" toggle yet for a
    // signed-in user to trigger it, so it would never be honestly true.
    LaunchedEffect(Unit) {
        authRepository.sessionState.collectLatest { state ->
            featureAccessState.setGuest(state is SessionState.LocalOnlyUser)
        }
    }
    LaunchedEffect(isOnline) {
        // Diagnostic (2026-08-01): `isOnline` gates both the offline banner and
        // `cloudAvailable`, so when it is wrong the whole cloud path degrades with
        // no visible reason. Log the shared flow's value next to what the platform
        // actually reports at that same instant, so a stale flag is immediately
        // distinguishable from genuinely absent connectivity.
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        Logger.withTag("SakhiNet").i {
            "isOnline(flow)=$isOnline | activeNetwork=$activeNetwork | " +
                "INTERNET=${capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)} | " +
                "VALIDATED=${capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}"
        }
        featureAccessState.setCloudAvailable(isOnline)
    }

    LaunchedEffect(route, pendingDeepLink?.id, forcedOnboardingDeepLink) {
        val pending = pendingDeepLink ?: return@LaunchedEffect
        if (forcedOnboardingDeepLink != null || route !is AppRoute.SignedOut) return@LaunchedEffect

        when (val link = pending.link) {
            is SakhiDeepLink.AcceptInvite -> {
                forcedOnboardingDeepLink = ForcedOnboardingDeepLink(
                    flowId = "joinFamily",
                    pendingInviteCode = link.code,
                )
                AndroidDeepLinkManager.consume(pending.id)
            }
            SakhiDeepLink.OpenOnboarding -> {
                forcedOnboardingDeepLink = ForcedOnboardingDeepLink(flowId = "newUser")
                AndroidDeepLinkManager.consume(pending.id)
            }
            is SakhiDeepLink.OpenEmergency -> AndroidDeepLinkManager.consume(pending.id)
            SakhiDeepLink.Unknown -> AndroidDeepLinkManager.consume(pending.id)
            else -> Unit
        }
    }

    LaunchedEffect(route, waitingForForcedOnboardingExit) {
        if (waitingForForcedOnboardingExit && route is AppRoute.Home) {
            forcedOnboardingDeepLink = null
            waitingForForcedOnboardingExit = false
        }
    }

    // Cold-start session restore: this was a real gap. `AppStateStore` only
    // observes `AppStateInputBridge.sessionState` (a manually-fed flow, doc
    // comment: "iOS drives this bridge when Supabase auth events fire"), never
    // `AuthRepository.sessionState` directly, and `authRepository.initialize()`
    // (which restores the session from stored tokens) was never called anywhere
    // in the app. Without this, `AppStateInputBridge` stayed at its default
    // `Loading` state forever on every cold start unless the user went through a
    // brand-new phone/OTP verification in that process -- a previously
    // signed-in user reopening the app would be stuck on the Splash screen
    // permanently. Run the restore itself on a background dispatcher so the
    // signed-out cold-start path is not paying secure-storage/session parsing
    // cost on the main thread before the phone screen can mount.
    // Restores the stored session once, then KEEPS OBSERVING `authRepository.sessionState`
    // for the lifetime of this composable. The observe half matters: this used to read
    // `currentSessionState()` exactly once inside a `LaunchedEffect(Unit)`, so any session
    // change made LATER in the process never reached `AppStateInputBridge` at all. That is
    // what left offline onboarding stuck on the setup loading view -- choosing "Continue
    // Offline" starts a `SessionState.LocalOnlyUser` session mid-flow (see
    // `OnboardingViewModel.resolvePrivacy`), but the bridge was still holding the
    // `Unauthenticated` value captured at cold start, so `AccountClassifier` never got the
    // chance to map `LocalOnlyUser -> AppRoute.Home` and nothing ever routed out of
    // onboarding. Collecting the flow fixes offline onboarding and every other
    // mid-session transition (sign-out, session expiry) with the same one change.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { authRepository.initialize() }
        authRepository.sessionState.collect { state ->
            when (state) {
                is SessionState.Authenticated -> appStateInputBridge.setAuthenticated(state.userId)
                is SessionState.LocalOnlyUser -> appStateInputBridge.setLocalOnly(state.userId)
                is SessionState.SessionExpired -> appStateInputBridge.setUnauthenticated()
                SessionState.Unauthenticated -> appStateInputBridge.setUnauthenticated()
                is SessionState.Loading -> appStateInputBridge.setLoading(state.hasKnownSession)
            }
        }
    }

    ToastHost()

    Column(modifier = Modifier.fillMaxSize()) {
        // No connectivity banner. Karan asked for it gone entirely: Sakhi is offline-first
        // by design, so losing the network is not an error state the user needs announced
        // across the top of every screen -- logging, the calendar and Home all keep
        // working from the local store. Features that genuinely require the cloud say so
        // themselves through `FeatureAccessGate`.
        Box(modifier = Modifier.weight(1f)) {
            val forcedFlow = forcedOnboardingDeepLink
            if (forcedFlow != null) {
                OnboardingFlowHost(
                    flowId = forcedFlow.flowId,
                    pendingInviteCodeOverride = forcedFlow.pendingInviteCode,
                    onFlowCompleted = { completion ->
                        waitingForForcedOnboardingExit = handleOnboardingCompletion(
                            completion = completion,
                            authRepository = authRepository,
                            onboardingCompletionBridge = onboardingCompletionBridge,
                        )
                    },
                )
            } else {
                when (val current = route) {
                    is AppRoute.Splash -> SplashPlaceholder()
                    // Matches iOS's real MainFlowView exactly (confirmed by reading it
                    // directly): `case .splash, .signedOut, .home: return .newUser` --
                    // iOS treats a signed-out entry as onboarding's own phone/OTP step
                    // (flowId "newUser"/NEW_OWNER), not a separate standalone auth
                    // screen. The instant OTP verification succeeds, the shared
                    // `AppStateStore` recomputes the route to `AppRoute.Onboarding` for
                    // the rest of the flow (DOB/height/etc) -- this must stay the *same*
                    // `OnboardingFlowHost`/`OnboardingViewModel` instance across that
                    // transition, not a fresh one, or the just-verified session and
                    // current step would be lost. That continuity comes for free here:
                    // Koin's `koinViewModel()` caches by class within this Activity's
                    // ViewModelStoreOwner, not by the `parametersOf(flowId)` value, so
                    // both branches resolve the same cached `OnboardingViewModel`
                    // regardless of which one is currently active -- the same pattern
                    // the `forcedOnboardingDeepLink` branch above already relies on.
                    // Previously this rendered a completely separate `SignedOutFlow()`
                    // (bare `PhoneScreen`/`OtpScreen`, no onboarding chrome, no back
                    // button) -- removed now that this is the correct route for it.
                    is AppRoute.SignedOut -> OnboardingFlowHost(
                        flowId = "newUser",
                        onFlowCompleted = { completion ->
                            handleOnboardingCompletion(
                                completion = completion,
                                authRepository = authRepository,
                                onboardingCompletionBridge = onboardingCompletionBridge,
                            )
                        },
                    )
                    is AppRoute.Onboarding -> OnboardingFlowHost(
                        flowId = current.flow.flowId,
                        onFlowCompleted = { completion ->
                            handleOnboardingCompletion(
                                completion = completion,
                                authRepository = authRepository,
                                onboardingCompletionBridge = onboardingCompletionBridge,
                                fallbackUserId = current.flow.accountState.resolvedUserId,
                            )
                        },
                    )
                    is AppRoute.Home -> HomeSessionGate(session = current.session)
                }
            }
        }
    }
}

private fun handleOnboardingCompletion(
    completion: OnboardingFlowCompletion,
    authRepository: AuthRepository,
    onboardingCompletionBridge: OnboardingCompletionBridge,
    fallbackUserId: String? = null,
) : Boolean {
    if (!completion.shouldSignalAppCompletion) return false
    // `currentUserId` only ever reflects a real (cloud) Supabase session, so it is null
    // for an OFFLINE user by design -- and `fallbackUserId` comes from a cloud account
    // state, so it is null there too. That made this return false and never signal
    // completion, leaving the app stuck on onboarding's setup loading view forever
    // (reported live). An offline user's identity lives in
    // `SessionState.LocalOnlyUser`, which `OnboardingViewModel.resolvePrivacy` now
    // starts when "Continue Offline" is chosen -- read it here so offline onboarding
    // can actually complete.
    val userId = authRepository.currentUserId
        ?: (authRepository.currentSessionState() as? SessionState.LocalOnlyUser)?.userId
        ?: fallbackUserId
        ?: return false
    onboardingCompletionBridge.signalCompletion(userId, "androidOnboarding")
    return true
}

/**
 * Starts the shared `SessionManager` (and `CareRealtimeCoordinator`) for this
 * `AppRoute.Home` session before mounting `HomeNavHost()`. Both were real gaps:
 * `sessionManager.startAsPrimary`/`startAsPartner` and
 * `careRealtimeCoordinator.startAsOwner`/`startAsPartner` were never called
 * anywhere in the app (verified by grep across both KMM and Android), so
 * `sessionManager.current` was always null at runtime for every feature that
 * reads it (Care/Logging/Profile/Chat/Onboarding all silently no-op on their
 * `sessionManager.current ?: return` guards), and Care realtime updates
 * (partner-status changes, invite acceptance) never fired at all despite
 * everything compiling successfully. Android now resolves the shared `CareStore`
 * once per Home entry: `PartnerConnected` boots the partner-view session and
 * partner realtime path, every other care state falls back to the existing
 * primary-user boot path so Home still opens even when care status is unknown.
 */
@Composable
private fun HomeSessionGate(
    session: team.sakhi.appstate.AppSessionState,
    sessionManager: SessionManager = koinInject(),
    careStore: CareStore = koinInject(),
    careRealtimeCoordinator: CareRealtimeCoordinator = koinInject(),
    widgetSnapshotManager: AndroidWidgetSnapshotManager = koinInject(),
    syncStore: SyncStore = koinInject(),
) {
    var isReady by remember(session.userId) { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(session.userId) {
        isReady = false
        when (val careState = runCatching {
            careStore.refresh(session.userId)
            careStore.careState.value
        }.getOrNull()) {
            is CareRuntimeState.PartnerConnected -> {
                sessionManager.startAsPartner(
                    userId = session.userId,
                    userName = "",
                    targetUserId = careState.partnership.userId,
                    partnership = careState.partnership,
                )
                // `CareRealtimeCoordinator.startAsPartner(partnerId, partnerUserId)` still
                // names both params like "partner", but the first one is the current
                // viewer's user id (see `subscribeAsPartner(partnerId)` filtering the
                // `care_partnerships.partner_id` column). In `PartnerConnected`, the
                // domain partnership already exposes that as `partnership.partnerId`.
                runCatching {
                    careRealtimeCoordinator.startAsPartner(
                        partnerId = careState.partnership.partnerId,
                        partnerUserId = careState.partnership.partnerId,
                    )
                }
            }
            else -> {
                sessionManager.startAsPrimary(userId = session.userId, userName = "")
                runCatching { careRealtimeCoordinator.startAsOwner(session.userId) }
            }
        }
        widgetSnapshotManager.refreshAsync()
        isReady = true
    }

    // `stop()` (not `destroy()`) on leaving Home: it cancels the current
    // subscription/job and resets state but keeps the coordinator's own scope
    // alive so a later re-entry to Home can call startAsOwner again. destroy()
    // cancels that scope permanently -- only appropriate at real process
    // shutdown, not a Home->SignedOut transition (e.g. sign out then back in).
    //
    // `syncStore.clearPartnerHealth()` belongs in this same teardown: `SyncStore`
    // is a process-lifetime Koin singleton (like `CareStore`), and its
    // `_partnerHealthSnapshot`/`_syncState` otherwise keep the previous signed-in
    // user's partner-health data around for whoever `HomeViewModel` reads it for
    // next -- confirmed via grep that `clearPartnerHealth()` (which already
    // existed for exactly this purpose) was never called anywhere on either
    // platform. Found doing the equivalent check for `CareStore.reset()` above:
    // both are the same "process-lifetime singleton never reset on sign-out" bug
    // class, just two different stores.
    DisposableEffect(session.userId) {
        onDispose {
            scope.launch { careRealtimeCoordinator.stop() }
            sessionManager.stop()
            syncStore.clearPartnerHealth()
        }
    }

    if (isReady) {
        HomeNavHost()
    } else {
        SplashPlaceholder()
    }
}

/**
 * Karan: "sirf sakhi ka loading view use hoga har jagah" -- a bare Material spinner is
 * never the right thing for a blocking, full-screen wait. This is the gap between
 * accepting Terms and Home appearing, which is exactly where iOS shows its own
 * `SakhiLoadingView`, so it shows the branded one here too.
 */
@Composable
private fun SplashPlaceholder() {
    SakhiLoadingView(
        context = SakhiLoadingContext.HomeSetup,
        modifier = Modifier.fillMaxSize(),
    )
}

