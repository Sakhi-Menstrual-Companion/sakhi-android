package team.sakhi.android.feature.onboarding

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import team.sakhi.android.designsystem.SakhiRadius
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.BackButton
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SakhiNavDirection
import team.sakhi.android.ui.SakhiScreenTransition
import team.sakhi.onboarding.OnboardingFlowCompletion
import team.sakhi.onboarding.OnboardingFlowStep
import team.sakhi.android.designsystem.sakhiSoftPinkPageBrush
import team.sakhi.android.ui.OnboardingShell
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground

/**
 * Real Phase 1 onboarding host: renders from KMM `OnboardingNavState.currentStep`
 * and sends user actions back as KMM intents. The health basics slice now renders
 * iOS-matching Android UI for DOB/height/weight/last-period/lengths/conditions,
 * while the rest of the flow stays on the generic KMM-driven shell until its own
 * parity pass lands.
 */
@Composable
fun OnboardingFlowHost(
    flowId: String,
    pendingInviteCodeOverride: String = "",
    onFlowCompleted: (OnboardingFlowCompletion) -> Unit = {},
    /**
     * Dismiss for a modal flow's ROOT step. iOS's `regularShell` puts a `DSCloseButton`
     * in the same leading slot the back button uses when the step cannot go back and the
     * flow is modal, so the Care invite intro shows an X top-LEFT. Android rendered
     * nothing there, leaving that screen with no way out except system back.
     */
    onDismiss: (() -> Unit)? = null,
    /**
     * Changes when the app re-enters onboarding from a real session, i.e. she signed out.
     * See `OnboardingViewModel.restartFlowIfNewRun` for why this is a token from the route
     * rather than something this host can work out for itself.
     */
    restartToken: Int = 0,
    // `key = flowId` is load-bearing, not a tidy-up. `koinViewModel` resolves out of the
    // ViewModelStore by TYPE (plus key); with no key, the second flow in a session gets
    // the FIRST flow's instance back and `parametersOf(flowId)` is silently ignored,
    // because the instance already exists. Live symptom: after a returning-user login
    // (flow "returningSync", plan = [SetupLoading], already completed), opening Care
    // re-used that same view model instead of building "carePartnerInvite", instantly
    // re-emitted its completion, and `CareScreen`'s `onFlowCompleted` closed the sheet
    // about 280ms after it opened -- so Be Her Sakhi could not be reached from Home at
    // all, by button or by deep link.
    viewModel: OnboardingViewModel = koinViewModel(
        key = flowId,
        parameters = { parametersOf(flowId) },
    ),
) {
    val context = LocalContext.current

    // Ordering here is load-bearing. This runs BEFORE `navState` is read so the collector
    // below picks up the restarted state as its initial value and step one renders on this
    // very pass. Read first and then restarted, the stale step composes once anyway, and
    // when that step is `SetupLoading` its `LaunchedEffect(Unit) { onSetupLoading() }`
    // fires -- which is exactly how a sign-out ended up minting a local-only account and
    // bouncing straight back to Home. Returning early instead of reordering was tried and
    // left the screen blank: nothing re-triggered composition once the frame was skipped.
    viewModel.restartFlowIfNewRun(restartToken)

    val navState by viewModel.navState.collectAsStateWithLifecycle()
    val healthUiState by viewModel.healthUiState.collectAsStateWithLifecycle()
    val careInviteUiState by viewModel.careInviteUiState.collectAsStateWithLifecycle()
    val dataSourceUiState by viewModel.dataSourceUiState.collectAsStateWithLifecycle()
    val acceptUiState by viewModel.acceptUiState.collectAsStateWithLifecycle()
    val conversionUiState by viewModel.conversionUiState.collectAsStateWithLifecycle()
    val setupUiState by viewModel.setupUiState.collectAsStateWithLifecycle()
    val currentOnFlowCompleted by rememberUpdatedState(onFlowCompleted)

    LaunchedEffect(viewModel) {
        viewModel.completion.collect { completion ->
            currentOnFlowCompleted(completion)
        }
    }

    // Sign-out brings her back here with the SAME retained view model, still parked wherever
    // the previous run left it. `restartToken` changes only on a real Home -> SignedOut
    // transition, so this restarts after a sign-out and leaves a rotation mid-onboarding
    // alone. Runs in the composition body, not an effect: an effect is dispatched a frame
    // later, and the stale step gets to compose and re-fire its completion first.

    // Both back triggers (system back below, and the on-screen `SakhiNavBar` button
    // further down) route through this one handler so keyboard-aware steps only need
    // handling in one place. Reported live: backing out of the OTP step made the
    // keyboard dismiss and the screen transition fire in the same instant -- two
    // unsynced animations landing together read as the view "dancing." iOS's own
    // `PhoneStep.dismissKeyboardBeforeContinue` sequences the same problem on the way
    // *into* OTP (keyboard down, then the push); this is that same fix for the way
    // *out*. Scoped to `OtpVerification` specifically, not every step with a keyboard --
    // it is the only onboarding step where `autoFocus`/an always-open keyboard is
    // actually in play.
    val keyboardController = LocalSoftwareKeyboardController.current
    val backCoroutineScope = rememberCoroutineScope()
    val handleBack: () -> Unit = handleBack@{
        if (navState.currentStep == OnboardingFlowStep.OtpVerification) {
            keyboardController?.hide()
            backCoroutineScope.launch {
                // Long enough for the IME's own close animation to actually start
                // collapsing before the screen also starts sliding, short enough that
                // back still feels immediate rather than delayed.
                delay(180)
                viewModel.goBack()
            }
            return@handleBack
        }
        viewModel.goBack()
    }

    // System back should step back one onboarding step, matching the on-screen back
    // button exactly. Scoped to currentIndex > 0 only: the shared
    // `OnboardingFlowStore.handleBack` deliberately no-ops at index 0 ("iOS dismisses the
    // sheet" -- a real product decision about what step 0's back button exits *to*, not
    // yet defined for Android). Leaving index 0 unhandled here means system back falls
    // through to the existing default behavior at that boundary, unchanged from before
    // this fix -- not inventing a new exit path without that product decision.
    // Also disabled on `SetupLoading` -- the save is in flight and the nav-bar back
    // button is hidden there, so letting system back still fire would be the one way
    // to escape a step the UI deliberately offers no exit from.
    BackHandler(
        enabled = navState.currentIndex > 0 && navState.currentStep != OnboardingFlowStep.SetupLoading,
    ) { handleBack() }

    // Every onboarding step renders through this one shared wrapper, so applying
    // safe-area insets here once covers all ~31 step screens instead of touching
    // each individually -- a real, systemic bug Karan found by looking at the app:
    // `enableEdgeToEdge()` is on (`MainActivity`), but nothing padded content away
    // from the status bar/navigation bar for any of these screens, so titles
    // rendered under the status bar and footer buttons were cut off by the
    // gesture/button nav bar on every single onboarding screen. Deliberately
    // scoped to onboarding only, not a root-level `RootNavHost` fix: `HomeScreen.kt`
    // already has its own real, on-device-verified `.statusBarsPadding()` from an
    // earlier session's device walkthrough, so a blanket fix at the shared root
    // would double-pad Home instead of fixing it.
    // `consumeWindowInsets` after the padding matters: Compose does not treat
    // insets as globally consumed just because a parent padded for them, so
    // without this, `PhoneScreen`/`OtpScreen`'s `KeyboardSafeScaffold` footer
    // (which independently calls `.navigationBarsPadding()`) would apply the
    // same nav-bar inset a second time, doubling the footer's bottom padding.
    // Shared top bar for every onboarding step, matching iOS's `OnboardingFlowView.
    // regularShell`, whose own comment is the spec: "Always render the top bar so
    // content never touches the top edge, even when there is no back button." iOS
    // reserves a 44pt row at the top of every step and puts a `DSBackButton` in it
    // when the step can go back, else a `Color.clear.frame(44x44)` placeholder. This
    // is the real fix for both of Karan's reports on these screens: content was jammed
    // against the very top with no breathing room, and there was no visible back
    // button (only a stray bottom TextButton on some steps). Rendered here once,
    // OUTSIDE the AnimatedContent below, so it stays fixed while only the step content
    // slides -- exactly like iOS's fixed shell chrome.
    // Now the shared `SakhiNavBar`, like every other sheet. iOS's own `regularShell`
    // header uses `.padding(.horizontal, DS.Spacing.screenHorizontal)` (24) and
    // `.padding(.top, DS.Spacing.ml)` (20) -- the exact metrics that component already
    // encodes. Android had 8/8 here, which is why the Care intro's X sat jammed into the
    // corner while Profile's sat properly inset.
    //
    // iOS keeps a 44pt row even with no button (`Color.clear.frame(44, 44)`) so content
    // never touches the top edge; `heightIn` preserves that.
    // Sits ABOVE the nav bar, not below it. The total distance from the status bar down to
    // the step title is the same either way, but placing it here pushes the back/close
    // button itself clear of the status bar instead of opening a gap between that button and
    // the title. Karan's call after seeing both on a real device.
    // `SetupLoading` is a full-screen loading state that owns the entire window (see
    // `SetupLoadingScreen`'s `SakhiLoadingView`), and it is not a step the user can
    // back out of mid-save anyway -- so the whole shell chrome (top gap + nav bar with
    // its back/close button) is suppressed for it. Per Karan: "jo back button hai vo
    // loading ke samay nahi aayega."
    val isFullScreenLoadingStep = navState.currentStep == OnboardingFlowStep.SetupLoading

    // The invite steps draw their own close button, exactly as iOS's `.fullscreen` steps
    // do, so the shell must not also put a back arrow on them. On iOS you cannot go back
    // from the share screen at all: the invitation already exists, so "back" to the
    // permissions step is meaningless.
    val stepOwnsChrome = navState.currentStep.ownsChrome

    // The frame itself now lives in `core:ui` as `OnboardingShell`, shared with Emergency
    // Assistance's own three-page intro, which had quietly drifted from it while it was a
    // Column declared here. iOS: `else if step.showsCloseButton || isModal { DSCloseButton
    // { ... } }` -- the close shares the LEADING slot with back, which is why the Care
    // intro's X is top-left, not top-right.
    // The Care intro is drawn with `SakhiOnboardingView`, whose ground is the soft pink
    // gradient. The shell paints it, not the step, so it runs edge to edge behind the
    // close button rather than starting below it and leaving a seam across the top.
    val stepPageBrush = if (navState.currentStep == OnboardingFlowStep.PartnerInvitePrompt) {
        sakhiSoftPinkPageBrush()
    } else {
        null
    }

    OnboardingShell(
        showChrome = !isFullScreenLoadingStep && !stepOwnsChrome,
        onBack = if (navState.canGoBack) handleBack else null,
        onClose = onDismiss,
        pageBrush = stepPageBrush,
    ) {


    // Directional slide, matching iOS's NavigationStack push/pop feel instead of an
    // instant cut between steps: `navWasForward` is shared KMM state, already set
    // correctly by every intent handler in `OnboardingFlowStore` (continue = forward,
    // back = backward), so direction reuses that instead of inventing local tracking.
    // Keyed on `currentIndex` specifically (not the whole `navState`) so a transition
    // only fires on a real step change, not on every unrelated field (fieldError, etc.)
    // update within the same step.
    SakhiScreenTransition(
        modifier = Modifier.weight(1f),
        targetState = navState.currentIndex,
        directionFor = { _, _ ->
            if (navState.navWasForward) SakhiNavDirection.Forward else SakhiNavDirection.Backward
        },
        label = "onboarding_step_transition",
    ) { targetIndex ->
        // AnimatedContent composes outgoing and incoming layers at the same time.
        // Each layer must render from its own target index; reading currentStep here
        // makes both layers draw the new screen, which looks like a duplicate view.
        val renderedStep = navState.plan.getOrNull(targetIndex) ?: navState.currentStep
        val renderedProgress = onboardingProgressForIndex(targetIndex, navState.plan.size)
        val continueRenderedStep = { viewModel.continueFlow(expectedStep = renderedStep) }

        when {
            renderedStep.isParityHealthStep() -> OnboardingHealthStepScreen(
                step = renderedStep,
                navStateProgress = renderedProgress,
                fieldError = navState.fieldError,
                canGoBack = navState.canGoBack,
                uiState = healthUiState,
                onContinue = continueRenderedStep,
                onBack = viewModel::goBack,
                onDateOfBirthChanged = viewModel::updateDateOfBirth,
                onHeightUnitChanged = viewModel::setHeightUnit,
                onHeightCmChanged = viewModel::setHeightCm,
                onWeightUnitChanged = viewModel::setWeightUnit,
                onWeightKgChanged = viewModel::setWeightKg,
                onLastPeriodDateChanged = viewModel::updateLastPeriodDate,
                onPreviousLastPeriodMonth = viewModel::showPreviousLastPeriodMonth,
                onNextLastPeriodMonth = viewModel::showNextLastPeriodMonth,
                onPeriodLengthChanged = viewModel::updatePeriodLengthText,
                onCycleLengthChanged = viewModel::updateCycleLengthText,
                onConditionToggled = viewModel::toggleCondition,
            )

            renderedStep.isParityContentStep() -> OnboardingContentStepScreen(
                step = renderedStep,
                canGoBack = navState.canGoBack,
                careInviteUiState = careInviteUiState,
                dataSourceUiState = dataSourceUiState,
                pendingPartnerRelation = navState.pendingPartnerRelation,
                fieldError = navState.fieldError,
                onContinue = continueRenderedStep,
                onBack = viewModel::goBack,
                onModeSelected = viewModel::selectMode,
                onPrivacyResolved = viewModel::resolvePrivacy,
                onTermsResolved = viewModel::resolveTerms,
                onPartnerRelationSelected = viewModel::selectPartnerRelation,
                onContactSelected = viewModel::onContactSelected,
                onBeHerSakhiCodeSubmitted = viewModel::submitBeHerSakhiCode,
                onStartCareInviteUpgrade = viewModel::startCareInviteUpgrade,
                onInvitePermissionsChanged = viewModel::setInvitePermissions,
                onCreateInvitation = viewModel::createCareInvitationAndContinue,
                onContinueToInviteWaiting = viewModel::continueToInviteWaiting,
                onCancelInvitation = viewModel::cancelCareInvitation,
                onDismissInviteError = viewModel::dismissCareInviteError,
                onDismiss = onDismiss,
                onOtpResolved = { isReturningUser, hasExistingOwnAccount ->
                    viewModel.resolveOtp(
                        isReturningUser = isReturningUser,
                        pendingInviteCode = pendingInviteCodeOverride.ifBlank { navState.pendingInviteCode },
                        hasExistingOwnAccount = hasExistingOwnAccount,
                    )
                },
                acceptUiState = acceptUiState,
                onAcceptInvite = viewModel::acceptBeHerSakhiInvite,
                onCompleteOnboarding = viewModel::completeOnboarding,
                conversionUiState = conversionUiState,
                onConvertAccount = viewModel::convertAccountToPartnerAndProceed,
                onKeepOwnAccount = viewModel::keepOwnAccount,
                setupUiState = setupUiState,
                onSetupLoading = viewModel::handleSetupLoading,
                onSelectHealthConnectDataSource = viewModel::selectHealthConnectDataSource,
                onSelectManualDataSource = viewModel::selectManualDataSource,
                onContinueManualDataSource = viewModel::continueManualDataSource,
                onImportHealthConnect = viewModel::importFromHealthConnect,
                onHealthConnectPermissionsResult = viewModel::onDataSourcePermissionsResult,
                onShowDataSourceFailureAlert = viewModel::reopenDataSourceFailureAlert,
                onAcknowledgeDataSourceFailure = viewModel::acknowledgeDataSourceFailureAndSwitchToManual,
                onHealthConnectUnavailable = viewModel::handleUnavailableHealthConnectSelection,
            )

            // Fallback for a step with no dedicated screen yet. It used to render the
            // scaffolding this host was built with: the raw flow id ("Flow: newUser"), a
            // "Step 4 of 9" counter card, a second back button below the shell's own, a
            // divider, and then every step of the plan listed out under a "Planned steps"
            // heading. All of that is for whoever is building the flow, not for her, and it
            // is what made these screens read as cluttered.
            //
            // What is left is what the situation actually calls for: this step's own title
            // and line, the error if there is one, and the action. The shell above already
            // owns the back button and the chrome, so none of it is repeated here.
            // No `verticalScroll` here: the weight below pins the action to the bottom the
            // way every other step does, and a weighted child inside a scrollable column is
            // an infinite-height measure crash, not a layout choice. The content is a title
            // and one line, so there is nothing to scroll.
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SakhiSpacing.space6),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                Text(
                    text = stepTitle(context, renderedStep),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stepDescription(context, renderedStep),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                )

                navState.fieldError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                StepActions(
                    step = renderedStep,
                    onContinue = continueRenderedStep,
                    onModeSelected = viewModel::selectMode,
                    onUpgradeRequired = viewModel::startCareInviteUpgrade,
                )
            }
        }
    }
    }
}

private fun onboardingProgressForIndex(stepIndex: Int, stepCount: Int): Float {
    if (stepCount <= 0) return 0f
    return ((stepIndex + 1).toFloat() / stepCount.toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun StepActions(
    step: OnboardingFlowStep,
    onContinue: () -> Unit,
    onModeSelected: (Boolean) -> Unit,
    onUpgradeRequired: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
        when (step) {
            OnboardingFlowStep.ModeSelection -> {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_host_mode_myself_action),
                    onClick = { onModeSelected(false) },
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = stringResource(R.string.onboarding_host_mode_partner_action),
                    onClick = { onModeSelected(true) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }


            OnboardingFlowStep.PartnerInvitePrompt -> {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_host_continue_invite_flow),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
                AssistChip(
                    onClick = onUpgradeRequired,
                    label = { Text(stringResource(R.string.onboarding_host_switch_upgrade_path)) },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }

            OnboardingFlowStep.SetupLoading -> {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_host_finish_setup_loading),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OnboardingFlowStep.Celebration,
            OnboardingFlowStep.InviteWaiting,
            OnboardingFlowStep.BeHerAccept -> {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_host_finish),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_continue),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun stepTitle(context: Context, step: OnboardingFlowStep): String = when (step) {
    OnboardingFlowStep.UniversalIntro -> context.getString(R.string.onboarding_host_step_universal_intro)
    OnboardingFlowStep.ModeSelection -> context.getString(R.string.onboarding_host_step_mode_selection)
    OnboardingFlowStep.MyselfIntroCarousel -> context.getString(R.string.onboarding_host_step_myself_intro_carousel)
    OnboardingFlowStep.Privacy -> context.getString(R.string.onboarding_host_step_privacy)
    OnboardingFlowStep.OfflineWarning -> context.getString(R.string.onboarding_host_step_offline_warning)
    OnboardingFlowStep.Phone -> context.getString(R.string.onboarding_host_step_phone)
    OnboardingFlowStep.OtpVerification -> context.getString(R.string.onboarding_host_step_otp_verification)
    OnboardingFlowStep.DataSource -> context.getString(R.string.onboarding_host_step_data_source)
    OnboardingFlowStep.DateOfBirth -> context.getString(R.string.onboarding_host_step_date_of_birth)
    OnboardingFlowStep.Height -> context.getString(R.string.onboarding_host_step_height)
    OnboardingFlowStep.Weight -> context.getString(R.string.onboarding_host_step_weight)
    OnboardingFlowStep.LastPeriod -> context.getString(R.string.onboarding_host_step_last_period)
    OnboardingFlowStep.PeriodLength -> context.getString(R.string.onboarding_host_step_period_length)
    OnboardingFlowStep.CycleLength -> context.getString(R.string.onboarding_host_step_cycle_length)
    OnboardingFlowStep.HealthConditions -> context.getString(R.string.onboarding_host_step_health_conditions)
    OnboardingFlowStep.Terms -> context.getString(R.string.onboarding_host_step_terms)
    OnboardingFlowStep.SetupLoading -> context.getString(R.string.onboarding_host_step_setup_loading)
    OnboardingFlowStep.Celebration -> context.getString(R.string.onboarding_host_step_celebration)
    OnboardingFlowStep.BeHerSakhi -> context.getString(R.string.onboarding_host_step_be_her_sakhi)
    OnboardingFlowStep.PartnerRelation -> context.getString(R.string.onboarding_host_step_partner_relation)
    OnboardingFlowStep.BeHerAccept -> context.getString(R.string.onboarding_host_step_be_her_accept)
    OnboardingFlowStep.PartnerConversionWarning -> context.getString(R.string.onboarding_host_step_partner_conversion_warning)
    OnboardingFlowStep.PartnerInvitePrompt -> context.getString(R.string.onboarding_host_step_partner_invite_prompt)
    OnboardingFlowStep.InviteContactAccess -> context.getString(R.string.onboarding_host_step_invite_contact_access)
    OnboardingFlowStep.InvitePickContact -> context.getString(R.string.onboarding_host_step_invite_pick_contact)
    OnboardingFlowStep.InvitePermissions -> context.getString(R.string.onboarding_host_step_invite_permissions)
    OnboardingFlowStep.InviteCreating -> context.getString(R.string.onboarding_host_step_invite_creating)
    OnboardingFlowStep.InviteShare -> context.getString(R.string.onboarding_host_step_invite_share)
    OnboardingFlowStep.InviteWaiting -> context.getString(R.string.onboarding_host_step_invite_waiting)
    OnboardingFlowStep.CareUpgradeIntro -> context.getString(R.string.onboarding_host_step_care_upgrade_intro)
    OnboardingFlowStep.JoinFamilyIntroCarousel -> context.getString(R.string.onboarding_host_step_join_family_intro_carousel)
    OnboardingFlowStep.JoinFamilyIntro -> context.getString(R.string.onboarding_host_step_join_family_intro)
}

private fun stepDescription(context: Context, step: OnboardingFlowStep): String = when (step) {
    OnboardingFlowStep.ModeSelection ->
        context.getString(R.string.onboarding_host_description_mode_selection)
    OnboardingFlowStep.Privacy ->
        context.getString(R.string.onboarding_host_description_privacy)
    OnboardingFlowStep.OtpVerification ->
        context.getString(R.string.onboarding_host_description_otp_verification)
    OnboardingFlowStep.SetupLoading ->
        context.getString(R.string.onboarding_host_description_setup_loading)
    OnboardingFlowStep.PartnerInvitePrompt ->
        context.getString(R.string.onboarding_host_description_partner_invite_prompt)
    else ->
        context.getString(R.string.onboarding_host_description_default)
}

private fun OnboardingFlowStep.isParityHealthStep(): Boolean = when (this) {
    OnboardingFlowStep.DateOfBirth,
    OnboardingFlowStep.Height,
    OnboardingFlowStep.Weight,
    OnboardingFlowStep.LastPeriod,
    OnboardingFlowStep.PeriodLength,
    OnboardingFlowStep.CycleLength,
    OnboardingFlowStep.HealthConditions -> true
    else -> false
}


