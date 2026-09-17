package team.sakhi.android.app

import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.runtime.mutableIntStateOf
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
import team.sakhi.android.ui.OfflineUpgradeLauncher
import team.sakhi.android.ui.SakhiAlertHost
import team.sakhi.android.ui.ToastHost
import team.sakhi.android.ui.ToastManager
import team.sakhi.android.ui.ToastType
import team.sakhi.android.feature.onboarding.OnboardingFlowHost
import team.sakhi.android.common.LastKnownPhaseStore
import team.sakhi.onboarding.PendingInviteStore
import team.sakhi.platform.PlatformKeyValueStore
import team.sakhi.models.CyclePhase
import team.sakhi.appstate.AppRoute
import team.sakhi.appstate.AppStateInputBridge
import team.sakhi.appstate.AppStateStore
import team.sakhi.auth.AuthRepository
import team.sakhi.auth.resolvedUserId
import team.sakhi.care.CareRealtimeCoordinator
import team.sakhi.staywithme.StayWithMeRealtimeCoordinator
import team.sakhi.staywithme.StayWithMeStore
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
    // The Koin singleton, the one `platformModule()` called `init(androidContext())` on.
    // Constructing `PlatformKeyValueStore()` here instead leaves `prefs` null, so every read
    // and write goes to a throwaway per-instance HashMap: `PendingInviteStore` looked durable
    // and remembered nothing, and the owed-join rescue below could never fire.
    val kvStore = koinInject<PlatformKeyValueStore>()
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

    // Offline-to-online upgrade, asked for from Profile. Runs the onboarding phone/OTP flow
    // OVER Home, because a local-only user is signed in and never passes through the
    // signed-out world the deep-link path below serves. Guarded on the session actually
    // being local-only: a real account has nothing to upgrade, and starting the flow for one
    // would just be a sign-in prompt on top of the app she is already signed in to.
    val offlineUpgradeRequested by OfflineUpgradeLauncher.requested.collectAsStateWithLifecycle()
    LaunchedEffect(offlineUpgradeRequested, route) {
        if (!offlineUpgradeRequested) return@LaunchedEffect
        if (authRepository.currentSessionState() !is SessionState.LocalOnlyUser) {
            OfflineUpgradeLauncher.consume()
            return@LaunchedEffect
        }
        forcedOnboardingDeepLink = ForcedOnboardingDeepLink(flowId = "newUser")
        OfflineUpgradeLauncher.consume()
    }

    // A join that is still owed outranks whatever the account state says the route should be.
    //
    // AccountClassifier decides purely on account state: a number that already has a profile
    // goes straight to AppRoute.Home, a new one gets a fresh onboarding plan. Neither knows
    // that she typed an invite code two screens ago, so verifying the OTP dropped the whole
    // partner flow and the code with it, and the "I am here for someone else" path never
    // actually joined anyone. Found on a device on 2026-09-02, from a clean install.
    //
    // PendingInviteStore keeps the code across that boundary. Here we notice it is still
    // owed and force the accept flow instead of Home.
    LaunchedEffect(route, forcedOnboardingDeepLink) {
        if (forcedOnboardingDeepLink != null) return@LaunchedEffect
        val owed = PendingInviteStore.read(kvStore) ?: return@LaunchedEffect
        // Only once she is actually signed in. While signed out the flow still holds the
        // code itself and the normal steps handle it.
        if (route !is AppRoute.Home && route !is AppRoute.Onboarding) return@LaunchedEffect
        forcedOnboardingDeepLink = ForcedOnboardingDeepLink(
            flowId = "joinFamily",
            pendingInviteCode = owed,
        )
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

    // Which transition the splash route represents. `AppRoute.Splash` is shown both on a
    // cold start and on the way out of a sign-out, and it was hardcoded to the
    // `HomeSetup` copy -- so signing out told the user Sakhi was "setting up your home",
    // which is the opposite of what was happening, and a cold start said it before there
    // was a home to set up.
    var hasBeenSignedIn by remember { mutableStateOf(false) }
    // Bumped when the app re-enters the signed-out world from a real session, which in
    // practice means she signed out. `OnboardingFlowHost`'s view model is retained per
    // Activity and keyed by flow id, so without this it resumes the previous run's step
    // instead of starting over. A rotation mid-onboarding never crosses Home, so the token
    // does not move and her progress survives.
    var onboardingRestartToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(route) {
        if (route is AppRoute.Home) hasBeenSignedIn = true
        if (route is AppRoute.SignedOut) {
            if (hasBeenSignedIn) onboardingRestartToken += 1
            hasBeenSignedIn = false
        }
    }

    ToastHost()

    // Partner mode is a live view of someone else's health, so it must never draw yesterday's
    // numbers. Losing the network closes this side of the app until it is back (Karan,
    // 2026-09-13). Her own side stays offline-first and is deliberately untouched, as the
    // note below about the removed connectivity banner says.
    val partnerSessionManager = koinInject<SessionManager>()
    val partnerSyncStore = koinInject<SyncStore>()
    val partnerSession by partnerSessionManager.session.collectAsStateWithLifecycle()
    val coverScope = rememberCoroutineScope()
    PartnerConnectionCover(
        visible = partnerSession?.isViewingOwnData == false && !isOnline,
        onRetry = { coverScope.launch { runCatching { partnerSyncStore.refreshPartnerHealth() } } },
    )

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
                    // Every OnboardingFlowHost call site must pass the SAME token. See
                    // the note on the AppRoute.Onboarding branch below.
                    restartToken = onboardingRestartToken,
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
                    is AppRoute.Splash -> SplashPlaceholder(
                        // Leaving Home for the splash route means a sign-out is in
                        // flight, not a cold start.
                        context = if (hasBeenSignedIn) {
                            SakhiLoadingContext.SigningOut
                        } else {
                            SakhiLoadingContext.AppLaunch
                        },
                    )
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
                    is AppRoute.SignedOut -> {
                        // Signed out: forget whose phase we were showing, so the next person
                        // to open the app on this device does not get her colour on the
                        // splash. A phase colour says something real about her.
                        LaunchedEffect(Unit) {
                            LastKnownPhaseStore.clearLastActiveUser(kvStore)
                        }
                        OnboardingFlowHost(
                            flowId = "newUser",
                            restartToken = onboardingRestartToken,
                            onFlowCompleted = { completion ->
                                handleOnboardingCompletion(
                                    completion = completion,
                                    authRepository = authRepository,
                                    onboardingCompletionBridge = onboardingCompletionBridge,
                                )
                            },
                        )
                    }
                    is AppRoute.Onboarding -> OnboardingFlowHost(
                        // MUST pass the token, and it must be the same one the SignedOut
                        // branch passes.
                        //
                        // This branch used to leave it at its default of 0. Verifying the
                        // OTP moves the route from SignedOut to Onboarding, so the host
                        // switches from that branch to this one mid-flow. Once anyone had
                        // signed out on this device the token was already 1, so arriving
                        // here with 0 looked like a brand new run and fired a Restart that
                        // threw away everything collected before the OTP.
                        //
                        // For the care partner path that meant the invite code she had
                        // just typed was silently dropped and she was re-planned into the
                        // owner's health questionnaire, so joining never happened. It also
                        // discarded ordinary onboarding answers for anyone who signed out
                        // and started again. Found on 2026-09-02 driving the join flow on
                        // a device.
                        restartToken = onboardingRestartToken,
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

    // Mounted once at the root, after the content, mirroring where iOS attaches
    // `.alertManager()`. Placement is for readability rather than z-order: Material3
    // 1.4's `ModalBottomSheet` presents through `ModalBottomSheetDialogWrapper`, its own
    // window, so it already sits above every screen that raises it -- verified on the QA
    // emulator, alert over the onboarding nav bar and step title, content behind
    // untouched and dimmed.
    SakhiAlertHost()
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
    stayWithMeRealtime: StayWithMeRealtimeCoordinator = koinInject(),
    stayWithMeStore: StayWithMeStore = koinInject(),
    widgetSnapshotManager: AndroidWidgetSnapshotManager = koinInject(),
    syncStore: SyncStore = koinInject(),
) {
    var isReady by remember(session.userId) { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(session.userId) {
        // ── Home renders FIRST. Nothing below this line may gate the first screen. ──────
        //
        // This gate used to hold a full-screen spinner through up to five network round
        // trips: two inside `startAsPrimary` (name lookup + sent invitations), the
        // care-status Edge Function, and the realtime websocket handshake plus three channel
        // subscriptions. In an app that stores everything locally, none of that is needed to
        // draw Home.
        //
        // `startAsPrimaryLocal` publishes the same session with the same permissions, read
        // from the local profile, with no network at all. Home mounts on the next frame.
        sessionManager.startAsPrimaryLocal(userId = session.userId, userName = "")
        isReady = true

        // ── Everything that needs the network now happens behind the visible screen. ─────
        //
        // Surfaced through `SyncStore`, which Home already observes, so the top bar can show
        // "Syncing" while this runs instead of the app pretending to be idle.
        syncStore.markSyncing()
        launch {
            // Fills in the display name and pending invitations the local boot left out.
            runCatching { sessionManager.refreshPrimaryDetails(session.userId) }

            // Care status decides whether she is viewing her own data or someone else's.
            // Starting as PRIMARY and correcting here is safe in one direction only, and this
            // is that direction: the local boot shows HER OWN data, never another user's, so
            // a slow or failed care lookup can never expose the wrong person's health record.
            val careState = runCatching {
                careStore.refresh(session.userId)
                careStore.careState.value
            }.getOrNull()

            when (careState) {
                is CareRuntimeState.PartnerConnected -> {
                    sessionManager.startAsPartner(
                        userId = session.userId,
                        userName = "",
                        targetUserId = careState.partnership.userId,
                        partnership = careState.partnership,
                    )
                    runCatching {
                        careRealtimeCoordinator.startAsPartner(
                            partnerId = careState.partnership.partnerId,
                            partnerUserId = careState.partnership.partnerId,
                        )
                    }
                }
                else -> {
                    // She stays the primary user (or the lookup could not be asked). Only now
                    // may Home treat an empty read of her own data as "nothing logged yet".
                    sessionManager.markRoleSettled()
                    runCatching { careRealtimeCoordinator.startAsOwner(session.userId) }
                }
            }

            // A walk has to reach this phone wherever it happens to be. The listener starts
            // with the session, not with the Care screen: her person can be on Home doing
            // nothing and still be told the moment she sets off. It rides the private
            // per-user topic Care has just joined, so it opens no socket of its own.
            runCatching {
                stayWithMeRealtime.start(session.userId)
                stayWithMeStore.refresh(session.userId)
            }

            widgetSnapshotManager.refreshAsync()
            // markIdle, NOT markSuccess: this bootstrap settles the session and care state,
            // it does not fetch cycle data. `markSuccess` publishes a new `lastSyncedAt`,
            // which Home reads as "new rows may exist" and answers with a full re-read plus a
            // complete engine pass — a second cold-start reload for nothing.
            syncStore.markIdle()
        }
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
            scope.launch { stayWithMeRealtime.stop() }
            sessionManager.stop()
            syncStore.clearPartnerHealth()
        }
    }

    if (isReady) {
        HomeNavHost()
    } else {
        // This one really is home setup: the gate is resolving the session before Home
        // mounts. The splash ROUTE is a different situation and picks its own context.
        SplashPlaceholder(context = SakhiLoadingContext.HomeSetup)
    }
}

/**
 * Karan: "sirf sakhi ka loading view use hoga har jagah" -- a bare Material spinner is
 * never the right thing for a blocking, full-screen wait. This is the gap between
 * accepting Terms and Home appearing, which is exactly where iOS shows its own
 * `SakhiLoadingView`, so it shows the branded one here too.
 */
@Composable
private fun SplashPlaceholder(
    context: SakhiLoadingContext = SakhiLoadingContext.AppLaunch,
) {
    // Paints in the phase Home last showed, so the loading screen and Home are the same
    // colour and nothing changes underneath her when Home mounts. Read synchronously from
    // the key-value store, so it is already correct on the very first frame; null on a first
    // launch, which correctly falls back to the brand treatment.
    // Deliberately NOT via `AuthRepository.currentUserId`: on the first splash frame the
    // Supabase session has not been read from storage yet, so that is null and the lookup
    // always missed — falling back to brand pink, which is the colour change this is meant to
    // remove. The last active user is recorded separately for exactly this moment.
    // Same injected singleton as above, for the same reason: a bare `PlatformKeyValueStore()`
    // reads from an empty per-instance map, so this lookup always missed and the splash always
    // fell back, which is the colour change the block below is written to avoid.
    val kvStore = koinInject<PlatformKeyValueStore>()
    val lastPhase = remember {
        // FOLLICULAR when nothing is remembered yet, rather than null. Null falls back to the
        // brand pink treatment inside SakhiLoadingView, so a first launch went pink and then
        // changed to whatever Home settled on. FOLLICULAR is the same neutral-cycle colour
        // Home itself shows while it has no phase, so the two agree from the first frame.
        LastKnownPhaseStore.restoreForLastActiveUser(kvStore)
            ?: CyclePhase.FOLLICULAR
    }
    SakhiLoadingView(
        context = context,
        modifier = Modifier.fillMaxSize(),
        phase = lastPhase,
    )
}

