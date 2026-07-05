package team.sakhi.android.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.onboarding.OnboardingFlowCompletion
import team.sakhi.onboarding.OnboardingFlowStep

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
    viewModel: OnboardingViewModel = koinViewModel(parameters = { parametersOf(flowId) }),
) {
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

    if (navState.currentStep.isParityHealthStep()) {
        OnboardingHealthStepScreen(
            step = navState.currentStep,
            navStateProgress = navState.progress,
            fieldError = navState.fieldError,
            canGoBack = navState.canGoBack,
            uiState = healthUiState,
            onContinue = viewModel::continueFlow,
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
        return
    }

    if (navState.currentStep.isParityContentStep()) {
        OnboardingContentStepScreen(
            step = navState.currentStep,
            canGoBack = navState.canGoBack,
            careInviteUiState = careInviteUiState,
            dataSourceUiState = dataSourceUiState,
            pendingPartnerRelation = navState.pendingPartnerRelation,
            fieldError = navState.fieldError,
            onContinue = viewModel::continueFlow,
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
            onOtpResolved = { isReturningUser ->
                viewModel.resolveOtp(
                    isReturningUser = isReturningUser,
                    pendingInviteCode = pendingInviteCodeOverride.ifBlank { navState.pendingInviteCode },
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
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SakhiSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = onboardingFlowTitle(navState.flowKind.flowId),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Flow id: ${viewModel.flowId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LinearProgressIndicator(
            progress = navState.progress.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
        )

        StepSummaryCard(
            step = navState.currentStep,
            stepIndex = navState.currentIndex,
            stepCount = navState.plan.size,
        )

        navState.fieldError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        StepActions(
            step = navState.currentStep,
            onContinue = viewModel::continueFlow,
            onModeSelected = viewModel::selectMode,
            onUpgradeRequired = viewModel::startCareInviteUpgrade,
        )

        if (navState.canGoBack) {
            TextButton(onClick = viewModel::goBack) {
                Text("Back")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = SakhiSpacing.space2))

        Text(
            text = "Planned steps",
            style = MaterialTheme.typography.titleLarge,
        )

        navState.plan.forEachIndexed { index, step ->
            StepPlanRow(
                step = step,
                isCurrent = index == navState.currentIndex,
            )
        }
    }
}

@Composable
private fun StepSummaryCard(
    step: OnboardingFlowStep,
    stepIndex: Int,
    stepCount: Int,
) {
    Surface(
        tonalElevation = SakhiSpacing.space1,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SakhiRadius.xl),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            Text(
                text = "Step ${stepIndex + 1} of $stepCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stepTitle(step),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stepDescription(step),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
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
                    text = "I am using Sakhi for myself",
                    onClick = { onModeSelected(false) },
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = "I am here for someone else",
                    onClick = { onModeSelected(true) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }


            OnboardingFlowStep.PartnerInvitePrompt -> {
                PrimaryButton(
                    text = "Continue invite flow",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
                AssistChip(
                    onClick = onUpgradeRequired,
                    label = { Text("Switch to upgrade path") },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }

            OnboardingFlowStep.SetupLoading -> {
                PrimaryButton(
                    text = "Finish setup loading",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OnboardingFlowStep.Celebration,
            OnboardingFlowStep.InviteWaiting,
            OnboardingFlowStep.BeHerAccept -> {
                PrimaryButton(
                    text = "Finish",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                PrimaryButton(
                    text = "Continue",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StepPlanRow(
    step: OnboardingFlowStep,
    isCurrent: Boolean,
) {
    val color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = if (isCurrent) "• ${stepTitle(step)}" else stepTitle(step),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}

private fun onboardingFlowTitle(flowId: String): String = when (flowId) {
    "newUser" -> "New user onboarding"
    "joinFamily" -> "Join family onboarding"
    "carePartnerUpgrade" -> "Care partner upgrade"
    "invitePartner" -> "Invite partner"
    "partnerToUser" -> "Partner to user conversion"
    "carePartnerInvite" -> "Care partner invite"
    "returningSync" -> "Returning user sync"
    else -> "Onboarding"
}

private fun stepTitle(step: OnboardingFlowStep): String = when (step) {
    OnboardingFlowStep.UniversalIntro -> "Universal intro"
    OnboardingFlowStep.ModeSelection -> "Mode selection"
    OnboardingFlowStep.MyselfIntroCarousel -> "Myself intro carousel"
    OnboardingFlowStep.Privacy -> "Privacy"
    OnboardingFlowStep.OfflineWarning -> "Offline warning"
    OnboardingFlowStep.Phone -> "Phone"
    OnboardingFlowStep.OtpVerification -> "OTP verification"
    OnboardingFlowStep.DataSource -> "Data source"
    OnboardingFlowStep.DateOfBirth -> "Date of birth"
    OnboardingFlowStep.Height -> "Height"
    OnboardingFlowStep.Weight -> "Weight"
    OnboardingFlowStep.LastPeriod -> "Last period"
    OnboardingFlowStep.PeriodLength -> "Period length"
    OnboardingFlowStep.CycleLength -> "Cycle length"
    OnboardingFlowStep.HealthConditions -> "Health conditions"
    OnboardingFlowStep.Terms -> "Terms"
    OnboardingFlowStep.SetupLoading -> "Setup loading"
    OnboardingFlowStep.Celebration -> "Celebration"
    OnboardingFlowStep.BeHerSakhi -> "Be Her Sakhi"
    OnboardingFlowStep.PartnerRelation -> "Partner relation"
    OnboardingFlowStep.BeHerAccept -> "Accept care flow"
    OnboardingFlowStep.PartnerConversionWarning -> "Partner conversion warning"
    OnboardingFlowStep.PartnerInvitePrompt -> "Partner invite prompt"
    OnboardingFlowStep.InviteContactAccess -> "Invite contact access"
    OnboardingFlowStep.InvitePickContact -> "Invite pick contact"
    OnboardingFlowStep.InvitePermissions -> "Invite permissions"
    OnboardingFlowStep.InviteCreating -> "Invite creating"
    OnboardingFlowStep.InviteShare -> "Invite share"
    OnboardingFlowStep.InviteWaiting -> "Invite waiting"
    OnboardingFlowStep.CareUpgradeIntro -> "Care upgrade intro"
    OnboardingFlowStep.JoinFamilyIntroCarousel -> "Join family intro carousel"
    OnboardingFlowStep.JoinFamilyIntro -> "Join family intro"
}

private fun stepDescription(step: OnboardingFlowStep): String = when (step) {
    OnboardingFlowStep.ModeSelection ->
        "This is the main branch point. KMM decides whether the remaining plan becomes the self path or the partner path."
    OnboardingFlowStep.Privacy ->
        "This step decides whether KMM should append auth, skip auth for an already-signed-in user, or branch into the offline warning."
    OnboardingFlowStep.OtpVerification ->
        "OTP success can continue normally, collapse into returning-user setup loading, or jump into the pending-invite acceptance path."
    OnboardingFlowStep.SetupLoading ->
        "This is the hydration handoff point before the app can route away from onboarding."
    OnboardingFlowStep.PartnerInvitePrompt ->
        "This step can stay in the invite flow or switch into the care-upgrade path via a dedicated KMM intent."
    else ->
        "Placeholder content for this KMM-owned step. Visual parity and full field inputs land in the later onboarding UI pass."
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
