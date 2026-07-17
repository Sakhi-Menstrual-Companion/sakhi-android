package team.sakhi.android.feature.onboarding

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    val context = LocalContext.current
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
            text = onboardingFlowTitle(context, navState.flowKind.flowId),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_host_flow_id, viewModel.flowId),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LinearProgressIndicator(
            progress = { navState.progress.coerceIn(0f, 1f) },
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
                Text(stringResource(R.string.onboarding_back))
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = SakhiSpacing.space2))

        Text(
            text = stringResource(R.string.onboarding_host_planned_steps),
            style = MaterialTheme.typography.titleLarge,
        )

        navState.plan.forEachIndexed { index, step ->
            StepPlanRow(
                step = step,
                isCurrent = index == navState.currentIndex,
                context = context,
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
    val context = LocalContext.current
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
                text = stringResource(R.string.onboarding_host_step_counter, stepIndex + 1, stepCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stepTitle(context, step),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stepDescription(context, step),
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

@Composable
private fun StepPlanRow(
    step: OnboardingFlowStep,
    isCurrent: Boolean,
    context: Context,
) {
    val color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = if (isCurrent) "• ${stepTitle(context, step)}" else stepTitle(context, step),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}

private fun onboardingFlowTitle(context: Context, flowId: String): String = when (flowId) {
    "newUser" -> context.getString(R.string.onboarding_host_flow_new_user)
    "joinFamily" -> context.getString(R.string.onboarding_host_flow_join_family)
    "carePartnerUpgrade" -> context.getString(R.string.onboarding_host_flow_care_partner_upgrade)
    "invitePartner" -> context.getString(R.string.onboarding_host_flow_invite_partner)
    "partnerToUser" -> context.getString(R.string.onboarding_host_flow_partner_to_user)
    "carePartnerInvite" -> context.getString(R.string.onboarding_host_flow_care_partner_invite)
    "returningSync" -> context.getString(R.string.onboarding_host_flow_returning_sync)
    else -> context.getString(R.string.onboarding_host_flow_default)
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
