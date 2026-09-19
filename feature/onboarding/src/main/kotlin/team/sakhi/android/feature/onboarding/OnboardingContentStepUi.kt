package team.sakhi.android.feature.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.feature.auth.AuthViewModel
import team.sakhi.android.feature.auth.OtpScreen
import team.sakhi.android.feature.auth.PhoneScreen
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.platform.HealthConnectAvailability
import team.sakhi.android.ui.BackButton
import team.sakhi.android.ui.FeatureBulletRow
import team.sakhi.android.ui.GlassCard
import team.sakhi.android.ui.OnboardingBulletSpacing
import team.sakhi.android.ui.OnboardingHeaderContentGap
import team.sakhi.android.ui.OnboardingIntroScaffold
import team.sakhi.android.ui.OnboardingStepTitle
import team.sakhi.android.ui.SakhiOnboardingPoint
import team.sakhi.android.ui.SakhiOnboardingView
import team.sakhi.android.ui.OnboardingTitleTopPadding
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SakhiAlert
import team.sakhi.android.ui.PartnerAvatarCloud
import team.sakhi.android.ui.SakhiNavBar
import team.sakhi.android.ui.SakhiAlertKind
import team.sakhi.android.ui.SakhiAlertSheet
import team.sakhi.android.ui.SakhiAlertTone
import team.sakhi.android.ui.sakhiShakeOnError
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.android.ui.SakhiLoadingContext
import team.sakhi.android.ui.SakhiLoadingView
import team.sakhi.android.ui.SakhiSwitch
import team.sakhi.android.ui.SakhiTextField
import team.sakhi.android.ui.ToastType
import team.sakhi.android.ui.ToastManager
import team.sakhi.android.ui.sakhiScreenTransitionSpec
import team.sakhi.android.ui.SecondaryButton
import team.sakhi.auth.AccountState
import team.sakhi.models.ParentChildPermissions
import team.sakhi.onboarding.OnboardingFlowStep
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.designsystem.sakhiSystemGray5
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiLightPink
import team.sakhi.android.designsystem.sakhiSystemBackground

/**
 * Real parity screens for the simple display-only onboarding steps, plus the
 * owner-side care invite onboarding slice (`PartnerInvitePrompt`,
 * `InvitePermissions`, `InviteShare`, `InviteWaiting`) ported from iOS, the
 * partner-path `Phone`/`OtpVerification` steps (reusing the already-parity'd
 * `AuthViewModel`/`PhoneScreen`/`OtpScreen` from `feature:auth` rather than
 * duplicating a second phone/OTP UI), `PartnerRelation`, `BeHerSakhi`
 * (new-user code-entry path), and `BeHerAccept` (auto-accept using the
 * `pendingInviteCode` now threaded through `OnboardingNavState`).
 */
@Composable
fun OnboardingContentStepScreen(
    step: OnboardingFlowStep,
    canGoBack: Boolean,
    careInviteUiState: OnboardingCareInviteUiState,
    dataSourceUiState: OnboardingDataSourceUiState = OnboardingDataSourceUiState(),
    pendingPartnerRelation: String = "",
    fieldError: String?,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onModeSelected: (partner: Boolean) -> Unit = {},
    onPrivacyResolved: (authenticated: Boolean, offline: Boolean) -> Unit = { _, _ -> },
    onTermsResolved: (accepted: Boolean) -> Unit = {},
    onPartnerRelationSelected: (String) -> Unit = {},
    onBeHerSakhiCodeSubmitted: (String) -> Unit = {},
    onStartCareInviteUpgrade: () -> Unit = {},
    onInvitePermissionsChanged: (ParentChildPermissions) -> Unit = {},
    onCreateInvitation: () -> Unit = {},
    onContinueToInviteWaiting: () -> Unit = {},
    onCancelInvitation: (Boolean) -> Unit = {},
    onDismissInviteError: () -> Unit = {},
    // Only the steps that own their chrome need this: the shell no longer draws a
    // button for them, so they draw their own close.
    onDismiss: (() -> Unit)? = null,
    onOtpResolved: (isReturningUser: Boolean, hasExistingOwnAccount: Boolean) -> Unit = { _, _ -> },
    acceptUiState: OnboardingAcceptUiState = OnboardingAcceptUiState(),
    onAcceptInvite: () -> Unit = {},
    onCompleteOnboarding: () -> Unit = {},
    conversionUiState: OnboardingConversionUiState = OnboardingConversionUiState(),
    onConvertAccount: () -> Unit = {},
    onKeepOwnAccount: () -> Unit = {},
    setupUiState: OnboardingSetupUiState = OnboardingSetupUiState(),
    onSetupLoading: () -> Unit = {},
    onSelectHealthConnectDataSource: () -> Unit = {},
    onSelectManualDataSource: () -> Unit = {},
    onContinueManualDataSource: () -> Unit = {},
    onImportHealthConnect: () -> Unit = {},
    onHealthConnectPermissionsResult: (Set<String>) -> Unit = {},
    onShowDataSourceFailureAlert: () -> Unit = {},
    onAcknowledgeDataSourceFailure: () -> Unit = {},
    onHealthConnectUnavailable: () -> Unit = {},
    onContactSelected: (name: String, phone: String) -> Unit = { _, _ -> },
) {
    when (step) {
        OnboardingFlowStep.InviteContactAccess -> InviteContactAccessScreen(onContinue = onContinue)
        OnboardingFlowStep.InvitePickContact -> InvitePickContactScreen(
            uiState = careInviteUiState,
            pendingPartnerRelation = pendingPartnerRelation,
            fieldError = fieldError,
            onContactSelected = onContactSelected,
            onPartnerRelationSelected = onPartnerRelationSelected,
            onContinue = onContinue,
        )
        OnboardingFlowStep.SetupLoading -> SetupLoadingScreen(uiState = setupUiState, onSetupLoading = onSetupLoading)
        OnboardingFlowStep.DataSource -> DataSourceScreen(
            uiState = dataSourceUiState,
            onSelectHealthConnect = onSelectHealthConnectDataSource,
            onSelectManual = onSelectManualDataSource,
            onContinueManual = onContinueManualDataSource,
            onImportHealthConnect = onImportHealthConnect,
            onHealthConnectPermissionsResult = onHealthConnectPermissionsResult,
            onShowFailureAlert = onShowDataSourceFailureAlert,
            onAcknowledgeFailure = onAcknowledgeDataSourceFailure,
            onHealthConnectUnavailable = onHealthConnectUnavailable,
        )
        OnboardingFlowStep.PartnerConversionWarning -> PartnerConversionWarningScreen(
            uiState = conversionUiState,
            onConvert = onConvertAccount,
            onKeepOwnAccount = onKeepOwnAccount,
        )
        OnboardingFlowStep.Phone,
        OnboardingFlowStep.OtpVerification -> OnboardingPhoneOtpScreen(
            step = step,
            onContinue = onContinue,
            onOtpResolved = onOtpResolved,
        )
        OnboardingFlowStep.BeHerAccept -> BeHerAcceptScreen(
            uiState = acceptUiState,
            onAccept = onAcceptInvite,
            onComplete = onCompleteOnboarding,
            onBack = onBack,
        )
        OnboardingFlowStep.ModeSelection -> ModeSelectionScreen(
            onModeSelected = onModeSelected,
        )
        OnboardingFlowStep.UniversalIntro -> UniversalIntroScreen(onContinue = onContinue)
        OnboardingFlowStep.Celebration -> CelebrationScreen(onContinue = onContinue)
        OnboardingFlowStep.MyselfIntroCarousel -> IntroCarouselScreen(
            slides = myselfIntroSlides,
            onContinue = onContinue,
            canGoBack = canGoBack,
            onBack = onBack,
        )
        OnboardingFlowStep.OfflineWarning -> OfflineWarningScreen(
            canGoBack = canGoBack,
            onContinue = onContinue,
            onBack = onBack,
        )
        OnboardingFlowStep.Privacy -> PrivacyScreen(onContinue = { offline -> onPrivacyResolved(false, offline) })
        OnboardingFlowStep.Terms -> TermsScreen(fieldError = fieldError, onContinue = onTermsResolved)
        OnboardingFlowStep.PartnerInvitePrompt -> PartnerInvitePromptScreen(
            canGoBack = canGoBack,
            onContinue = onContinue,
            onBack = onBack,
            onStartCareInviteUpgrade = onStartCareInviteUpgrade,
        )
        OnboardingFlowStep.InvitePermissions -> InvitePermissionsScreen(
            uiState = careInviteUiState,
            onPermissionsChanged = onInvitePermissionsChanged,
            onContinue = onCreateInvitation,
            onDismissError = onDismissInviteError,
        )
        OnboardingFlowStep.InviteShare -> InviteShareScreen(
            onDismiss = onDismiss,
            uiState = careInviteUiState,
            // No carrying on to the waiting step from Share, as iOS: the request is cancelled
            // from here, and the screen that shows it cancelled ends the flow with Done.
            onCancelInvitation = { onCancelInvitation(false) },
            onDismissError = onDismissInviteError,
            onDone = onCompleteOnboarding,
        )
        OnboardingFlowStep.InviteWaiting -> InviteWaitingScreen(
            onDismiss = onDismiss,
            uiState = careInviteUiState,
            onContinue = onContinue,
            onCancelInvitation = { onCancelInvitation(false) },
            onDismissError = onDismissInviteError,
        )
        OnboardingFlowStep.JoinFamilyIntroCarousel -> IntroCarouselScreen(
            slides = joinFamilyIntroSlides,
            onContinue = onContinue,
            canGoBack = canGoBack,
            onBack = onBack,
        )
        OnboardingFlowStep.JoinFamilyIntro -> JoinFamilyIntroScreen(onContinue = onContinue)
        OnboardingFlowStep.CareUpgradeIntro -> CareUpgradeIntroScreen(onContinue = onContinue)
        OnboardingFlowStep.PartnerRelation -> PartnerRelationScreen(
            selected = pendingPartnerRelation,
            fieldError = fieldError,
            onSelected = onPartnerRelationSelected,
            onContinue = onContinue,
        )
        OnboardingFlowStep.BeHerSakhi -> BeHerSakhiScreen(
            fieldError = fieldError,
            onSubmit = onBeHerSakhiCodeSubmitted,
        )
        else -> {}
    }
}

fun OnboardingFlowStep.isParityContentStep(): Boolean = when (this) {
    OnboardingFlowStep.ModeSelection,
    OnboardingFlowStep.UniversalIntro,
    OnboardingFlowStep.Celebration,
    OnboardingFlowStep.MyselfIntroCarousel,
    OnboardingFlowStep.OfflineWarning,
    OnboardingFlowStep.Privacy,
    OnboardingFlowStep.Terms,
    OnboardingFlowStep.PartnerInvitePrompt,
    OnboardingFlowStep.InvitePermissions,
    OnboardingFlowStep.InviteShare,
    OnboardingFlowStep.InviteWaiting,
    OnboardingFlowStep.JoinFamilyIntroCarousel,
    OnboardingFlowStep.JoinFamilyIntro,
    OnboardingFlowStep.CareUpgradeIntro,
    OnboardingFlowStep.PartnerRelation,
    OnboardingFlowStep.BeHerSakhi,
    OnboardingFlowStep.Phone,
    OnboardingFlowStep.OtpVerification,
    OnboardingFlowStep.BeHerAccept,
    OnboardingFlowStep.PartnerConversionWarning,
    OnboardingFlowStep.DataSource,
    OnboardingFlowStep.SetupLoading,
    OnboardingFlowStep.InviteContactAccess,
    OnboardingFlowStep.InvitePickContact -> true
    // `InviteCreating` deliberately excluded: real (per KMM), but unreachable --
    // grepped every `OnboardingFlowKind` step plan in `OnboardingFlowStore.kt`
    // and it appears in none of them (`INVITE_PARTNER`'s real plan is
    // InviteContactAccess -> InvitePickContact -> InvitePermissions ->
    // InviteShare -> InviteWaiting, no InviteCreating in between). Building a
    // screen for a step nothing ever navigates to would be exactly the kind
    // of unreachable-code half-feature this whole session has avoided
    // elsewhere (Reduce Motion, PartnerPersonality, sexualActivity/undo).
    else -> false
}

// ── Care Invite ────────────────────────────────────────────────────────────

@Composable
private fun ModeSelectionScreen(
    onModeSelected: (partner: Boolean) -> Unit,
) {
    val hapticManager = koinInject<AndroidHapticManager>()
    var isPartnerSelected by remember { mutableStateOf(false) }
    val title = stringResource(R.string.onboarding_mode_selection_title)
    val subtitle = stringResource(R.string.onboarding_mode_selection_subtitle)
    val myselfTitle = stringResource(R.string.onboarding_mode_selection_myself_title)
    val myselfDescription = stringResource(R.string.onboarding_mode_selection_myself_description)
    val partnerTitle = stringResource(R.string.onboarding_mode_selection_partner_title)
    val partnerDescription = stringResource(R.string.onboarding_mode_selection_partner_description)
    val continueLabel = stringResource(R.string.onboarding_continue)

    // Content in a weighted area, action pinned in the shared `SakhiFooter` -- the
    // real port of iOS's `SakhiFooter`, whose reserved secondary slot keeps the
    // primary button's Y position identical across every step (previously each
    // screen hand-placed its own button, so it shifted between screens).
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = SakhiSpacing.space6)
                .padding(top = SakhiSpacing.space6),
        ) {
            OnboardingStepTitle(text = title)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
                modifier = Modifier.padding(top = SakhiSpacing.space1),
            )

            Column(
                modifier = Modifier.padding(top = OnboardingHeaderContentGap),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
            ) {
                PrivacyChoiceCard(
                    icon = Icons.Filled.Person,
                    title = myselfTitle,
                    description = myselfDescription,
                    isSelected = !isPartnerSelected,
                    onClick = {
                        hapticManager.impact(HapticImpact.LIGHT)
                        isPartnerSelected = false
                    },
                )
                PrivacyChoiceCard(
                    icon = Icons.Filled.Groups,
                    title = partnerTitle,
                    description = partnerDescription,
                    isSelected = isPartnerSelected,
                    onClick = {
                        hapticManager.impact(HapticImpact.LIGHT)
                        isPartnerSelected = true
                    },
                )
            }
        }

        SakhiFooter(
            primaryLabel = continueLabel,
            onPrimaryClick = { onModeSelected(isPartnerSelected) },
        )
    }
}


/** iOS `IntroCarouselStep.Layout.imageVisualHeight` (300) -- was a fixed 220dp square. */
private val IntroCarouselImageHeight = 300.dp

/**
 * The Care intro, drawn with the app's onboarding template. Same words as before, from the
 * same string resources, in the shape every other intro in the app now uses.
 *
 * Continue still calls `onContinue`, which runs the step's own handler and carries on into
 * the invite flow exactly as it did when this screen was a two-page carousel. Nothing about
 * what she is asked, or when, changed here.
 *
 * NO close button of its own, and no secondary action. `OnboardingShell` above it already
 * draws back and close, and this is a screen about a person she may or may not want to add:
 * it explains, and then it gets out of the way. Adding someone means sharing her health
 * data with them, and that decision is entirely hers.
 *
 * The soft pink ground is painted by the shell, not here, so it runs edge to edge behind
 * the close button instead of starting below it. See `OnboardingShell.pageBrush`.
 */
@Composable
private fun PartnerInvitePromptScreen(
    canGoBack: Boolean,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onStartCareInviteUpgrade: () -> Unit,
) {
    val hapticManager = koinInject<AndroidHapticManager>()

    SakhiOnboardingView(
        // iOS `person.2.fill`.
        icon = Icons.Filled.People,
        title = stringResource(R.string.onboarding_partner_invite_prompt_title),
        message = stringResource(R.string.onboarding_partner_invite_prompt_subtitle),
        points = listOf(
            SakhiOnboardingPoint(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.onboarding_partner_invite_prompt_feature_1),
                detail = stringResource(R.string.onboarding_partner_invite_prompt_feature_1_subtitle),
            ),
            SakhiOnboardingPoint(
                icon = Icons.Filled.NotificationsActive,
                title = stringResource(R.string.onboarding_partner_invite_prompt_feature_2),
                detail = stringResource(R.string.onboarding_partner_invite_prompt_feature_2_subtitle),
            ),
            SakhiOnboardingPoint(
                icon = Icons.Filled.Groups,
                title = stringResource(R.string.onboarding_partner_invite_prompt_feature_3),
                detail = stringResource(R.string.onboarding_partner_invite_prompt_feature_3_subtitle),
            ),
        ),
        primaryLabel = stringResource(R.string.onboarding_continue),
        onPrimaryClick = {
            hapticManager.impact(HapticImpact.MEDIUM)
            onContinue()
        },
        drawsBackground = false,
    )
}

@Composable
private fun InvitePermissionsScreen(
    uiState: OnboardingCareInviteUiState,
    onPermissionsChanged: (ParentChildPermissions) -> Unit,
    onContinue: () -> Unit,
    onDismissError: () -> Unit,
) {
    var allowAll by remember(uiState.permissions) { mutableStateOf(uiState.permissions.isFullyShared()) }
    val continueLabel = stringResource(R.string.onboarding_continue)

    Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(SakhiSpacing.space6),
    ) {
        OnboardingStepTitle(text = stringResource(R.string.onboarding_invite_permissions_title))
        Text(
            text = stringResource(R.string.onboarding_invite_permissions_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
            modifier = Modifier.padding(top = SakhiSpacing.space1),
        )

        Column(
            modifier = Modifier.padding(top = OnboardingHeaderContentGap),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        ) {
        uiState.errorMessage?.let { error ->
            SakhiAlert(
                title = stringResource(R.string.onboarding_invite_permissions_error_title),
                message = error,
                tone = SakhiAlertTone.Error,
                onDismiss = onDismissError,
            )
        }

        PermissionCard(
            icon = Icons.Filled.AutoAwesome,
                title = stringResource(R.string.onboarding_invite_permissions_allow_all_title),
                description = stringResource(R.string.onboarding_invite_permissions_allow_all_description),
                isOn = allowAll,
                onToggle = { enabled ->
                    allowAll = enabled
                    if (enabled) {
                        onPermissionsChanged(fullySharedCareInvitePermissions())
                    }
                },
            )

        if (!allowAll) {
            PermissionCard(
                icon = Icons.Filled.CalendarMonth,
                title = stringResource(R.string.onboarding_invite_permissions_cycle_title),
                description = stringResource(R.string.onboarding_invite_permissions_cycle_description),
                isOn = uiState.permissions.isCycleEnabled(),
                onToggle = { enabled ->
                    onPermissionsChanged(uiState.permissions.withCyclePermissions(enabled))
                    allowAll = false
                },
            )
            PermissionCard(
                icon = Icons.Filled.SentimentSatisfied,
                title = stringResource(R.string.onboarding_invite_permissions_symptoms_title),
                description = stringResource(R.string.onboarding_invite_permissions_symptoms_description),
                isOn = uiState.permissions.isSymptomsEnabled(),
                onToggle = { enabled ->
                    onPermissionsChanged(uiState.permissions.withSymptomsPermissions(enabled))
                    allowAll = false
                },
            )
            PermissionCard(
                icon = Icons.Filled.EditNote,
                title = stringResource(R.string.onboarding_invite_permissions_daily_logs_title),
                description = stringResource(R.string.onboarding_invite_permissions_daily_logs_description),
                isOn = uiState.permissions.isDailyLogsEnabled(),
                onToggle = { enabled ->
                    onPermissionsChanged(uiState.permissions.withDailyLogPermissions(enabled))
                    allowAll = false
                },
            )
            PermissionCard(
                icon = Icons.Filled.BarChart,
                title = stringResource(R.string.onboarding_invite_permissions_body_stats_title),
                description = stringResource(R.string.onboarding_invite_permissions_body_stats_description),
                isOn = uiState.permissions.isBodyStatsEnabled(),
                onToggle = { enabled ->
                    onPermissionsChanged(uiState.permissions.withBodyStatsPermissions(enabled))
                    allowAll = false
                },
            )
            PermissionCard(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.onboarding_invite_permissions_private_data_title),
                description = stringResource(R.string.onboarding_invite_permissions_private_data_description),
                isOn = uiState.permissions.isPrivateDataEnabled(),
                onToggle = { enabled ->
                    onPermissionsChanged(uiState.permissions.withPrivateDataPermissions(enabled))
                    allowAll = false
                },
            )
        }
        }
    }
        SakhiFooter(
            primaryLabel = if (uiState.isCreatingInvite) {
                stringResource(R.string.onboarding_invite_permissions_creating)
            } else {
                continueLabel
            },
            onPrimaryClick = onContinue,
            primaryEnabled = !uiState.isCreatingInvite,
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    // iOS `InvitePartnerFlowSteps.swift`'s permission row: plain
    // `profileCardBackground` (white) at `onboardingCard` radius (16 = `SakhiRadius.xl`,
    // not `xxl`); 42pt icon circle filled `pink` when on and `lightPink` when off (not
    // pink-at-10%-alpha); 15pt bold title over a 13pt secondary description. Same
    // tinted-fill bug class fixed across the rest of onboarding this session.
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xl),
        color = sakhiSystemBackground(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = if (isOn) MaterialTheme.colorScheme.primary else sakhiLightPink(),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isOn) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1 / 2),
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = sakhiSecondaryLabel(),
                )
            }

            SakhiSwitch(
                checked = isOn,
                onCheckedChange = onToggle,
            )
        }
    }
}

/**
 * Cancelling an invite, as iOS's share and waiting steps do it: a loading screen while it
 * goes, then "Request cancelled" with Done; a failure is a toast and she stays where she was.
 * Shared by both steps so they cannot drift.
 */
@Composable
private fun InviteCancelFlow(
    uiState: OnboardingCareInviteUiState,
    onCancelInvitation: () -> Unit,
    onDismissError: () -> Unit,
    onDone: () -> Unit,
    onDismiss: (() -> Unit)?,
    content: @Composable (requestCancel: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var cancelRequested by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    val loadingMessages = listOf(
        stringResource(R.string.onboarding_invite_cancelling_1),
        stringResource(R.string.onboarding_invite_cancelling_2),
        stringResource(R.string.onboarding_invite_cancelling_3),
    )

    LaunchedEffect(uiState.isCancellingInvite) {
        if (cancelRequested && !uiState.isCancellingInvite) {
            cancelRequested = false
            if (uiState.errorMessage == null) {
                cancelled = true
            } else {
                onDismissError()
                ToastManager.show(
                    title = context.getString(R.string.onboarding_invite_cancel_failed_title),
                    message = context.getString(R.string.onboarding_invite_cancel_failed_message),
                    type = ToastType.ERROR,
                    durationMs = 2000L,
                )
            }
        }
    }

    when {
        cancelled -> InviteRequestCancelled(onDone = onDone, onClose = onDismiss)
        uiState.isCancellingInvite -> SakhiLoadingView(context = SakhiLoadingContext.Messages(loadingMessages))
        else -> content {
            cancelRequested = true
            onCancelInvitation()
        }
    }
}

/** iOS `CareActionCompletionView` for a closed invite: a tick, what happened, and Done. */
@Composable
private fun InviteRequestCancelled(
    onDone: () -> Unit,
    onClose: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SakhiNavBar(onClose = onClose)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = stringResource(R.string.onboarding_invite_cancelled_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space8, start = SakhiSpacing.space6, end = SakhiSpacing.space6),
            )
            Text(
                text = stringResource(R.string.onboarding_info_invite_closed),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = sakhiSecondaryLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = SakhiSpacing.space3)
                    .padding(horizontal = SakhiSpacing.space8),
            )
        }
        SakhiFooter(
            primaryLabel = stringResource(R.string.onboarding_done),
            onPrimaryClick = onDone,
            showSecondarySlot = false,
        )
    }
}

/**
 * iOS `InviteShareView`. Share opens the system share sheet and nothing else: she stays on
 * this screen, with her code, until she closes it or cancels the request. (Android used to
 * carry on to the waiting step from here; iOS never does.)
 */
@Composable
private fun InviteShareScreen(
    uiState: OnboardingCareInviteUiState,
    onCancelInvitation: () -> Unit,
    onDismissError: () -> Unit,
    onDone: () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val hapticManager = koinInject<AndroidHapticManager>()
    val displayName = uiState.pendingInvitation.displayName().ifBlank {
        context.getString(R.string.onboarding_invite_fallback_partner)
    }
    val shareMessage = context.getString(R.string.onboarding_invite_share_message, uiState.inviteCode)
    val shareTitle = stringResource(R.string.onboarding_invite_share_title, displayName)
    val shareSubtitle = stringResource(R.string.onboarding_invite_share_subtitle, displayName)
    val codeCopiedTitle = stringResource(R.string.onboarding_invite_code_copied_title)
    val codeCopiedMessage = stringResource(R.string.onboarding_invite_code_copied_message, displayName)
    val shareButtonLabel = stringResource(R.string.onboarding_invite_share_button)
    val cancelLabel = stringResource(R.string.onboarding_invite_cancel)
    var previousConnected by remember { mutableStateOf(uiState.isConnected) }

    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected && !previousConnected) {
            hapticManager.success()
        }
        previousConnected = uiState.isConnected
    }

    InviteCancelFlow(
        uiState = uiState,
        onCancelInvitation = onCancelInvitation,
        onDismissError = onDismissError,
        onDone = onDone,
        onDismiss = onDismiss,
    ) { requestCancel ->
        Column(modifier = Modifier.fillMaxSize()) {
            // iOS `InviteShareView` draws `DSNavBar(onClose:)` itself, and the shell adds nothing
            // for a fullscreen step. There is deliberately no back button: the invitation has
            // already been created by the time this screen is reached.
            SakhiNavBar(onClose = onDismiss)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(SakhiSpacing.space6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))

                PartnerAvatarCloud(partnerName = displayName)

                Text(
                    text = shareTitle,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = sakhiLabel(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 28.dp),
                )
                Text(
                    text = shareSubtitle,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = sakhiSecondaryLabel(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = SakhiSpacing.space8)
                        .padding(top = SakhiSpacing.space4),
                )

                InviteCodeChip(
                    code = uiState.inviteCode,
                    modifier = Modifier.padding(top = SakhiSpacing.space4),
                    onCopy = {
                        hapticManager.success()
                        copyInviteCode(context = context, code = uiState.inviteCode)
                        ToastManager.show(
                            title = codeCopiedTitle,
                            message = codeCopiedMessage,
                            type = ToastType.SUCCESS,
                            durationMs = 2000L,
                        )
                    },
                )

                Spacer(modifier = Modifier.weight(1f))
            }
            SakhiFooter(
                primaryLabel = shareButtonLabel,
                onPrimaryClick = {
                    hapticManager.impact(HapticImpact.MEDIUM)
                    shareInviteMessage(context, shareMessage)
                },
                primaryEnabled = uiState.inviteCode.isNotBlank(),
                secondaryLabel = cancelLabel,
                onSecondaryClick = requestCancel,
            )
        }
    }
}

@Composable
private fun InviteWaitingScreen(
    uiState: OnboardingCareInviteUiState,
    onDismiss: (() -> Unit)? = null,
    onContinue: () -> Unit,
    onCancelInvitation: () -> Unit,
    onDismissError: () -> Unit,
) {
    val context = LocalContext.current
    val hapticManager = koinInject<AndroidHapticManager>()
    val displayName = uiState.pendingInvitation.displayName().ifBlank {
        context.getString(R.string.onboarding_invite_fallback_partner)
    }
    val shareMessage = context.getString(R.string.onboarding_invite_share_message, uiState.inviteCode)
    val connectedTitle = stringResource(R.string.onboarding_invite_connected_title)
    val connectedSubtitle = stringResource(R.string.onboarding_invite_connected_subtitle, displayName)
    val waitingTitle = stringResource(R.string.onboarding_invite_waiting_title, displayName)
    val waitingSubtitle = stringResource(R.string.onboarding_invite_waiting_subtitle, displayName)
    val codeCopiedTitle = stringResource(R.string.onboarding_invite_code_copied_title)
    val codeCopiedMessage = stringResource(R.string.onboarding_invite_code_copied_message, displayName)
    val connectedButtonLabel = stringResource(R.string.onboarding_invite_connected_button)
    val shareButtonLabel = stringResource(R.string.onboarding_invite_share_button)
    val cancelLabel = stringResource(R.string.onboarding_invite_cancel)

    InviteCancelFlow(
        uiState = uiState,
        onCancelInvitation = onCancelInvitation,
        onDismissError = onDismissError,
        onDone = onContinue,
        onDismiss = onDismiss,
    ) { requestCancel ->
        Column(modifier = Modifier.fillMaxSize()) {
            // iOS `InviteWaitingView` draws `DSNavBar(onClose:)` itself. No back button: the
            // invitation exists by now.
            SakhiNavBar(onClose = onDismiss)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(SakhiSpacing.space6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))

                PartnerAvatarCloud(partnerName = displayName)

                // iOS: 30 for the celebration, 26 while she waits.
                Text(
                    text = if (uiState.isConnected) connectedTitle else waitingTitle,
                    fontSize = if (uiState.isConnected) 30.sp else 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = sakhiLabel(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 28.dp),
                )
                Text(
                    text = if (uiState.isConnected) connectedSubtitle else waitingSubtitle,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = sakhiSecondaryLabel(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = SakhiSpacing.space8)
                        .padding(top = if (uiState.isConnected) SakhiSpacing.space2 else SakhiSpacing.space4),
                )

                if (!uiState.isConnected) {
                    InviteCodeChip(
                        code = uiState.inviteCode,
                        modifier = Modifier.padding(top = SakhiSpacing.space4),
                        onCopy = {
                            hapticManager.success()
                            copyInviteCode(context = context, code = uiState.inviteCode)
                            ToastManager.show(
                                title = codeCopiedTitle,
                                message = codeCopiedMessage,
                                type = ToastType.SUCCESS,
                                durationMs = 2000L,
                            )
                        },
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }
            if (uiState.isConnected) {
                SakhiFooter(
                    primaryLabel = connectedButtonLabel,
                    onPrimaryClick = {
                        hapticManager.impact(HapticImpact.MEDIUM)
                        onContinue()
                    },
                )
            } else {
                SakhiFooter(
                    primaryLabel = shareButtonLabel,
                    onPrimaryClick = {
                        hapticManager.impact(HapticImpact.MEDIUM)
                        shareInviteMessage(context, shareMessage)
                    },
                    primaryEnabled = uiState.inviteCode.isNotBlank(),
                    secondaryLabel = cancelLabel,
                    onSecondaryClick = requestCancel,
                )
            }
        }
    }
}

@Composable
private fun InviteHero(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(104.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
        )
    }
}

@Composable
private fun InviteCodeChip(
    code: String,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit,
) {
    if (code.isBlank()) return

    // iOS: a `lightPink` capsule with no outline, the code in bold monospaced 15 with 2 of
    // tracking, and a small copy glyph in the brand pink.
    Surface(
        shape = RoundedCornerShape(SakhiRadius.full),
        color = sakhiLightPink(),
        modifier = modifier.clickable(onClick = onCopy),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SakhiSpacing.space6,
                vertical = SakhiSpacing.space3,
            ),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatInviteCode(code),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun copyInviteCode(
    context: android.content.Context,
    code: String,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(
        ClipData.newPlainText(context.getString(R.string.onboarding_invite_clipboard_label), code),
    )
}

private fun shareInviteMessage(
    context: android.content.Context,
    message: String,
) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    val chooser = Intent.createChooser(shareIntent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

private fun formatInviteCode(code: String): String {
    val trimmed = code.trim()
    return if (trimmed.length >= 6) {
        "${trimmed.take(3)}-${trimmed.takeLast(3)}"
    } else {
        trimmed
    }
}

private fun team.sakhi.models.PartnerInvitation?.displayName(): String {
    return this?.inviteeName?.trim().orEmpty()
}

private fun ParentChildPermissions.isFullyShared(): Boolean =
    canViewPeriodDates &&
        canViewSymptoms &&
        canViewMoods &&
        canViewMedications &&
        canViewPredictions &&
        canLogPeriods &&
        canViewCycleHistory &&
        canViewDailyLogs &&
        canViewOvulationTests &&
        canViewTemperature &&
        canViewWeight &&
        canViewNotes &&
        canViewDischarge &&
        !canViewSexualActivity

private fun fullySharedCareInvitePermissions(): ParentChildPermissions = ParentChildPermissions(
    canViewPeriodDates = true,
    canViewSymptoms = true,
    canViewMoods = true,
    canViewMedications = true,
    canViewPredictions = true,
    canLogPeriods = true,
    canViewCycleHistory = true,
    canViewDailyLogs = true,
    canViewOvulationTests = true,
    canViewTemperature = true,
    canViewWeight = true,
    canViewNotes = true,
    canViewDischarge = true,
    canViewSexualActivity = false,
)

private fun ParentChildPermissions.isCycleEnabled(): Boolean =
    canViewPeriodDates && canViewCycleHistory && canViewPredictions && canLogPeriods

private fun ParentChildPermissions.isSymptomsEnabled(): Boolean =
    canViewSymptoms && canViewMoods

private fun ParentChildPermissions.isDailyLogsEnabled(): Boolean =
    canViewDailyLogs && canViewOvulationTests

private fun ParentChildPermissions.isBodyStatsEnabled(): Boolean =
    canViewTemperature && canViewWeight && canViewDischarge

private fun ParentChildPermissions.isPrivateDataEnabled(): Boolean =
    canViewNotes && canViewMedications

private fun ParentChildPermissions.withCyclePermissions(enabled: Boolean): ParentChildPermissions = copy(
    canViewPeriodDates = enabled,
    canViewCycleHistory = enabled,
    canViewPredictions = enabled,
    canLogPeriods = enabled,
)

private fun ParentChildPermissions.withSymptomsPermissions(enabled: Boolean): ParentChildPermissions = copy(
    canViewSymptoms = enabled,
    canViewMoods = enabled,
)

private fun ParentChildPermissions.withDailyLogPermissions(enabled: Boolean): ParentChildPermissions = copy(
    canViewDailyLogs = enabled,
    canViewOvulationTests = enabled,
)

private fun ParentChildPermissions.withBodyStatsPermissions(enabled: Boolean): ParentChildPermissions = copy(
    canViewTemperature = enabled,
    canViewWeight = enabled,
    canViewDischarge = enabled,
)

private fun ParentChildPermissions.withPrivateDataPermissions(enabled: Boolean): ParentChildPermissions = copy(
    canViewNotes = enabled,
    canViewMedications = enabled,
)

// ── Privacy ─────────────────────────────────────────────────────────────────

@Composable
private fun PrivacyScreen(onContinue: (offline: Boolean) -> Unit) {
    var isOfflineSelected by remember { mutableStateOf(false) }
    val title = stringResource(R.string.onboarding_privacy_title)
    val secureTitle = stringResource(R.string.onboarding_privacy_secure_title)
    val secureDescription = stringResource(R.string.onboarding_privacy_secure_description)
    val offlineTitle = stringResource(R.string.onboarding_privacy_offline_title)
    val offlineDescription = stringResource(R.string.onboarding_privacy_offline_description)
    val continueLabel = stringResource(R.string.onboarding_continue)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = SakhiSpacing.space6)
                .padding(top = SakhiSpacing.space6),
        ) {
            OnboardingStepTitle(
                text = title,
                modifier = Modifier.padding(bottom = OnboardingHeaderContentGap),
            )

            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4)) {
                PrivacyChoiceCard(
                    icon = Icons.Filled.Shield,
                    title = secureTitle,
                    description = secureDescription,
                    isSelected = !isOfflineSelected,
                    onClick = { isOfflineSelected = false },
                )
                PrivacyChoiceCard(
                    icon = Icons.Filled.PhoneAndroid,
                    title = offlineTitle,
                    description = offlineDescription,
                    isSelected = isOfflineSelected,
                    onClick = { isOfflineSelected = true },
                )
            }
        }

        SakhiFooter(
            primaryLabel = continueLabel,
            onPrimaryClick = { onContinue(isOfflineSelected) },
        )
    }
}

@Composable
private fun PrivacyChoiceCard(
    icon: ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    isError: Boolean = false,
    onClick: () -> Unit,
) {
    val stateNeedsAttention = stringResource(R.string.onboarding_state_needs_attention)
    val stateSelected = stringResource(R.string.onboarding_state_selected)
    val stateNotSelected = stringResource(R.string.onboarding_state_not_selected)
    // Real port of iOS's `OnboardingPrivacyCard` (`OnboardingPrivacyComponents.swift`) --
    // the shared card behind `CareForStep` ("Who Are You Here For?"), `PrivacyStep`, and
    // `DataSourceStep`. The prior Android version filled the card pink when selected and
    // put every icon in its own circular badge; iOS does neither -- the card background
    // never changes, only the icon/title glyph colour and the border do.
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else sakhiLabel()
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xl),
        color = sakhiSystemBackground(),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.selected = isSelected
                role = Role.RadioButton
                stateDescription = when {
                    isError -> stateNeedsAttention
                    isSelected -> stateSelected
                    else -> stateNotSelected
                }
            }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = PrivacyCardHorizontalPadding,
                vertical = SakhiSpacing.space5,
            ),
            horizontalArrangement = Arrangement.spacedBy(PrivacyCardTrailingGap),
        ) {
            // iOS: single `VStack(spacing: DS.Spacing.iconToTitle)` holding the icon
            // (no badge), title, and description -- one uniform 10pt gap between all
            // three, not a separate icon-circle + text-column split.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PrivacyCardInternalGap),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = contentColor,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = sakhiSecondaryLabel(),
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = SakhiSpacing.space1)
                    .size(26.dp)
                    .border(
                        width = 2.dp,
                        color = when {
                            isError -> sakhiSecondaryLabel().copy(alpha = 0.35f)
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isError -> {
                        Text(
                            text = "!",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = sakhiSecondaryLabel(),
                        )
                    }

                    isSelected -> {
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

/** iOS `DS.Spacing.cardHorizontal` (18) -- not on the 4dp token scale, spelled literal. */
private val PrivacyCardHorizontalPadding = 18.dp

/** iOS `DS.Spacing.cardHorizontal - DS.Spacing.xxs` (18 - 4 = 14): gap before the trailing selection circle. */
private val PrivacyCardTrailingGap = 14.dp

/** iOS `DS.Spacing.iconToTitle` (10): the one gap used between icon, title, and description. */
private val PrivacyCardInternalGap = 10.dp

// ── Terms ───────────────────────────────────────────────────────────────────

@Composable
private fun TermsScreen(fieldError: String?, onContinue: (accepted: Boolean) -> Unit) {
    val hapticManager = koinInject<AndroidHapticManager>()
    var hasAccepted by remember { mutableStateOf(false) }
    // A counter, not a Boolean: tapping Continue again while still unchecked has to
    // re-shake, and a Boolean that is already `true` would not re-fire the effect.
    var declinedAttempts by remember { mutableIntStateOf(0) }
    val title = stringResource(R.string.onboarding_terms_title)
    val subtitle = stringResource(R.string.onboarding_terms_subtitle)
    val agreementLabel = stringResource(R.string.onboarding_terms_agreement)
    val privacyText = stringResource(R.string.onboarding_terms_privacy_fallback)
    val continueLabel = stringResource(R.string.onboarding_continue)
    val stateSelected = stringResource(R.string.onboarding_state_selected)
    val stateNotSelected = stringResource(R.string.onboarding_state_not_selected)

    Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = SakhiSpacing.space6)
            .padding(top = SakhiSpacing.space6),
    ) {
        OnboardingStepTitle(text = title)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
            modifier = Modifier.padding(top = SakhiSpacing.space1, bottom = OnboardingHeaderContentGap),
        )

        // Real port of iOS's `TermsStepContent`: both the legal-text card and the
        // checkbox row fill `DS.Colors.profileCardBackground` (plain white), not a
        // tinted `colorScheme.surface`/`sakhiGroupedBackground` -- the same root-cause
        // bug pattern found across this whole session, confirmed live against an
        // actual iOS screenshot ("you're in wale... alag hi color ke hai weird se").
        Surface(
            shape = RoundedCornerShape(SakhiRadius.xl),
            color = sakhiSystemBackground(),
            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
        ) {
            Text(
                text = privacyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(SakhiSpacing.space5),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SakhiSpacing.space4)
                // iOS `.shakeOnError(...)` -- the card itself answers the tap so the
                // user's eye goes to the thing they still have to do.
                .sakhiShakeOnError(declinedAttempts.takeIf { it > 0 })
                .semantics {
                    selected = hasAccepted
                    role = Role.Checkbox
                    stateDescription = if (hasAccepted) stateSelected else stateNotSelected
                }
                .clickable {
                    hapticManager.impact(HapticImpact.LIGHT)
                    hasAccepted = !hasAccepted
                }
                .background(sakhiSystemBackground(), RoundedCornerShape(SakhiRadius.xl))
                .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            // iOS: 22x22, 6dp corner radius, filled pink + pink 1.5pt stroke when
            // checked; filled white + separator-grey 1.5pt stroke when unchecked --
            // was `Color.Transparent` with no border at all in the unchecked state,
            // so the checkbox was invisible against the (also-wrong) tinted row.
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        color = if (hasAccepted) MaterialTheme.colorScheme.primary else sakhiSystemBackground(),
                        shape = RoundedCornerShape(6.dp),
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (hasAccepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (hasAccepted) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = agreementLabel,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }

        // Karan: short, crisp, with an icon, in the accent colour -- not a long
        // sentence in Material's error red. This is a "you missed a step" nudge on a
        // consent screen, not a failure.
        fieldError?.takeIf(String::isNotBlank)?.let {
            Row(
                modifier = Modifier.padding(top = SakhiSpacing.space3),
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(TermsValidationIconSize),
                )
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

    }

        SakhiFooter(
            primaryLabel = continueLabel,
            onPrimaryClick = {
                if (!hasAccepted) declinedAttempts++
                onContinue(hasAccepted)
            },
        )
    }
}

/** Sized to sit level with the 13sp validation text beside it. */
private val TermsValidationIconSize = 16.dp

// ── Universal Intro / Celebration / Offline Warning ───────────────────────

// iOS's `UniversalIntroStep` has no hero icon of its own -- the shared
// `OnboardingFlowView` shell renders just title+subtitle, then the step's
// `contentView`, which for this step is three `FeatureBulletRow`s (real
// Sanity-CMS copy, bundled as `onboarding.intro.feature{1,2,3}.title/subtitle`).
// This was the first real screen a new user sees and was missing this content
// entirely on Android.
@Composable
private fun UniversalIntroScreen(onContinue: () -> Unit) {
    OnboardingIntroScaffold(
        title = stringResource(R.string.onboarding_intro_title),
        subtitle = stringResource(R.string.onboarding_intro_subtitle),
        primaryLabel = stringResource(R.string.onboarding_continue),
        onPrimaryClick = onContinue,
    ) {
        FeatureBulletRow(
            icon = Icons.Filled.TouchApp,
            title = stringResource(R.string.onboarding_intro_feature_1_title),
            subtitle = stringResource(R.string.onboarding_intro_feature_1_subtitle),
        )
        FeatureBulletRow(
            icon = Icons.Filled.AutoAwesome,
            title = stringResource(R.string.onboarding_intro_feature_2_title),
            subtitle = stringResource(R.string.onboarding_intro_feature_2_subtitle),
        )
        FeatureBulletRow(
            icon = Icons.Filled.Favorite,
            title = stringResource(R.string.onboarding_intro_feature_3_title),
            subtitle = stringResource(R.string.onboarding_intro_feature_3_subtitle),
        )
    }
}

@Composable
private fun CelebrationScreen(onContinue: () -> Unit) {
    HeroContentStep(
        icon = Icons.Filled.Favorite,
        title = stringResource(R.string.onboarding_celebration_title),
        subtitle = stringResource(R.string.onboarding_celebration_subtitle),
        primaryLabel = stringResource(R.string.onboarding_celebration_button),
        onPrimaryClick = onContinue,
    )
}

@Composable
private fun IntroCarouselScreen(
    slides: List<OnboardingIntroSlide>,
    onContinue: () -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
) {
    // iOS's real carousel is `TabView(selection:).tabViewStyle(.page)` -- a swipeable,
    // physically-paginated view. Android was swapping `currentSlide` on a plain `var`,
    // which recomposes instantly with no motion at all ("pura sudden transition hai").
    // `HorizontalPager` is this app's established port of that same iOS pattern (see
    // `PartnerInvitePromptScreen` above), so Continue now animates to the next page
    // instead of cutting to it, and the page is swipeable too, matching iOS.
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val coroutineScope = rememberCoroutineScope()
    val backLabel = stringResource(R.string.onboarding_back)
    val continueLabel = stringResource(R.string.onboarding_continue)

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val slide = slides[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = SakhiSpacing.space6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Back + top spacing are the shared top bar in `OnboardingFlowHost`
                // (matching iOS's single `regularShell` chrome), so this carousel no
                // longer renders its own top back button / spacer placeholder -- that
                // duplicated the shared one on `MyselfIntroCarousel`.
                Spacer(modifier = Modifier.weight(1f))

                // Real illustration, matching iOS. `BundledOnboardingContent.imageAssets`
                // maps both `onboarding.carousel.slideN.image` and
                // `onboarding.care_carousel.slideN.image` to `Onboarding/1|2|3`, i.e. by
                // slide index for both carousels -- so index is the correct key here,
                // not the flow. Falls back to the old Material icon if a carousel ever
                // has more slides than there are illustrations, rather than crashing or
                // showing nothing.
                val slideIllustration = onboardingSlideIllustration(page)
                if (slideIllustration != null) {
                    // iOS `IntroCarouselStep.Layout.imageVisualHeight` = 300,
                    // `.scaledToFit()` across the full page width. Android was a fixed
                    // 220dp square -- visibly smaller than iOS and cropped to a square
                    // instead of the illustration's real aspect ratio.
                    Image(
                        painter = painterResource(slideIllustration),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntroCarouselImageHeight),
                    )
                } else {
                    InviteHero(icon = slide.icon)
                }
                OnboardingDots(
                    currentIndex = pagerState.currentPage,
                    totalCount = slides.size,
                    modifier = Modifier.padding(top = SakhiSpacing.space5),
                )
                OnboardingStepTitle(
                    text = stringResource(slide.titleRes),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = SakhiSpacing.space6),
                )
                Text(
                    text = stringResource(slide.subtitleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = SakhiSpacing.space2),
                )

                Spacer(modifier = Modifier.weight(1f))
            }
        }

        SakhiFooter(
            primaryLabel = continueLabel,
            onPrimaryClick = {
                if (pagerState.currentPage < slides.lastIndex) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(
                            page = pagerState.currentPage + 1,
                            animationSpec = sakhiScreenTransitionSpec,
                        )
                    }
                } else {
                    onContinue()
                }
            },
        )
    }
}

/**
 * Real port of iOS's `OfflineWarningStep` (`OfflineWarningStep.swift`). The prior Android
 * version used the generic centered `HeroContentStep` (icon + title + subtitle), which is
 * not what this step is on iOS at all: a top-anchored title/subtitle over a genuine
 * 2-page carousel -- slide 0 the real "offline" illustration (`Conditions/offline`, same
 * 335pt-tall/460pt-container geometry as `PartnerInvitePromptStep`'s carousel, since iOS
 * explicitly notes it "matches the care-partner onboarding hero... so the offline image
 * is the same size everywhere it appears"), slide 1 a list of what stops working (no
 * backup, no care connection, no sync). `canGoBack`/`onBack` are unused: iOS's own
 * `onBack` here just flips `isOfflineUser = false` and returns to `PrivacyStep`, which is
 * `OnboardingFlowHost`'s shared back button/handler doing already, not this screen's job.
 */
@Composable
private fun OfflineWarningScreen(
    canGoBack: Boolean,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val title = stringResource(R.string.onboarding_offline_warning_title)
    val subtitle = stringResource(R.string.onboarding_offline_warning_subtitle)
    val continueLabel = stringResource(R.string.onboarding_offline_warning_continue)
    val pagerState = rememberPagerState(pageCount = { 1 })

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space6),
        ) {
            OnboardingStepTitle(text = title)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
                modifier = Modifier.padding(top = SakhiSpacing.space2),
            )

            // The hero size iOS gives `PartnerInvitePromptContent`, kept for this screen
            // after the Care intro moved to the shared onboarding template. The top offset
            // and dots position are deliberately larger: Karan asked for both content and
            // the page dots pushed down further on this screen specifically.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = OfflineCarouselTopGap)
                    .height(OfflineCarouselHeight),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    // Illustration page dropped on Karan's ask: this step is now only the
                    // list of what going offline actually costs you, so there is nothing
                    // to swipe past before reading it. Single page, so `page` is unused.
                    run {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = SakhiSpacing.space3),
                            verticalArrangement = Arrangement.spacedBy(OnboardingBulletSpacing),
                        ) {
                            OfflineFeatureLossRow(
                                icon = Icons.Filled.CloudOff,
                                title = stringResource(R.string.onboarding_offline_no_backup_title),
                                description = stringResource(R.string.onboarding_offline_no_backup_description),
                            )
                            OfflineFeatureLossRow(
                                icon = Icons.Filled.VisibilityOff,
                                title = stringResource(R.string.onboarding_offline_no_care_title),
                                description = stringResource(R.string.onboarding_offline_no_care_description),
                            )
                            OfflineFeatureLossRow(
                                icon = Icons.Filled.Sync,
                                title = stringResource(R.string.onboarding_offline_no_sync_title),
                                description = stringResource(R.string.onboarding_offline_no_sync_description),
                            )
                        }
                    }
                }

                OnboardingDots(
                    currentIndex = pagerState.currentPage,
                    totalCount = 2,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = OfflineIndicatorTop),
                )
            }
        }

        SakhiFooter(
            primaryLabel = continueLabel,
            onPrimaryClick = onContinue,
        )
    }
}

/** Karan: offline carousel content and page dots pushed down further than the default. */
private val OfflineCarouselTopGap = SakhiSpacing.space4

/**
 * Transcribed from iOS `PartnerInvitePromptContent.Layout`, which is where this screen's
 * hero size came from. They used to be read off the Care intro's own constants; that screen
 * is now drawn with `SakhiOnboardingView` and has no carousel, so these live here, with the
 * one screen still using them.
 */
private val OfflineCarouselHeight = 460.dp
private val OfflineIndicatorTop = 307.dp + SakhiSpacing.space4

/**
 * iOS `offlineLossesSlide`'s row: `HStack(alignment: .top, spacing: .m)` (16), a 52x52
 * `DS.Colors.lightPink` circle (not [FeatureBulletRow]'s 44dp -- a different, larger size
 * used only here), 16pt bold title over a 13pt secondary description at `lineSpacing(4)`.
 */
@Composable
private fun OfflineFeatureLossRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(sakhiLightPink(), CircleShape),
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
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = sakhiSecondaryLabel(),
            )
        }
    }
}

@Composable
private fun JoinFamilyIntroScreen(onContinue: () -> Unit) {
    HeroContentStep(
        icon = Icons.Filled.Groups,
        title = stringResource(R.string.onboarding_join_family_intro_title),
        subtitle = stringResource(R.string.onboarding_join_family_intro_subtitle),
        primaryLabel = stringResource(R.string.onboarding_continue),
        onPrimaryClick = onContinue,
    )
}

@Composable
private fun CareUpgradeIntroScreen(onContinue: () -> Unit) {
    HeroContentStep(
        icon = Icons.Filled.CloudOff,
        title = stringResource(R.string.onboarding_care_upgrade_intro_title),
        subtitle = stringResource(R.string.onboarding_care_upgrade_intro_subtitle),
        primaryLabel = stringResource(R.string.onboarding_care_upgrade_intro_button),
        onPrimaryClick = onContinue,
    )
}

@Composable
private fun HeroContentStep(
    icon: ImageVector,
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
) {
    // Shared hero+actions layout used by 4 onboarding screens. Actions live in the
    // shared `SakhiFooter` so the primary button's Y is identical whether or not a
    // secondary exists (previously the optional secondary `TextButton` sat directly
    // under the primary, so the primary shifted between screens that had one and
    // screens that didn't).
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = SakhiSpacing.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            InviteHero(icon = icon)
            OnboardingStepTitle(
                text = title,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space6),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space2),
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        SakhiFooter(
            primaryLabel = primaryLabel,
            onPrimaryClick = onPrimaryClick,
            secondaryLabel = secondaryLabel?.takeIf { it.isNotBlank() },
            onSecondaryClick = onSecondaryClick,
        )
    }
}

@Composable
private fun OnboardingDots(
    currentIndex: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalCount) { index ->
            val isSelected = index == currentIndex
            Surface(
                shape = RoundedCornerShape(SakhiRadius.full),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                modifier = Modifier
                    .width(if (isSelected) SakhiSpacing.space5 else SakhiSpacing.space2)
                    .height(SakhiSpacing.space2),
            ) {}
        }
    }
}


/**
 * The ported iOS onboarding illustration for a carousel slide, or null past slide 3.
 *
 * Assets are the real `Onboarding/1|2|3` PDFs from iOS's asset catalog, rasterised into
 * Android density buckets with light and dark (`drawable-night-*`) variants, so the
 * artwork switches with the theme exactly as iOS's light/dark PDF pair does.
 */
@DrawableRes
private fun onboardingSlideIllustration(index: Int): Int? = when (index) {
    0 -> team.sakhi.android.ui.R.drawable.onboarding_slide_1
    1 -> team.sakhi.android.ui.R.drawable.onboarding_slide_2
    2 -> team.sakhi.android.ui.R.drawable.onboarding_slide_3
    else -> null
}

private data class OnboardingIntroSlide(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
)

// iOS `IntroCarouselStep` in .newUser/.universal mode reads onboarding.carousel.slide1-3,
// which is a different copy set from onboarding.intro.feature*, the bullet list on
// `UniversalIntroStep`. Android pointed both at the bullet strings, so the carousel and
// the intro screen showed identical text.
private val myselfIntroSlides = listOf(
    OnboardingIntroSlide(
        icon = Icons.Filled.AutoAwesome,
        titleRes = R.string.onboarding_carousel_slide_1_title,
        subtitleRes = R.string.onboarding_carousel_slide_1_subtitle,
    ),
    OnboardingIntroSlide(
        icon = Icons.Filled.Favorite,
        titleRes = R.string.onboarding_carousel_slide_2_title,
        subtitleRes = R.string.onboarding_carousel_slide_2_subtitle,
    ),
    OnboardingIntroSlide(
        icon = Icons.Filled.Groups,
        titleRes = R.string.onboarding_carousel_slide_3_title,
        subtitleRes = R.string.onboarding_carousel_slide_3_subtitle,
    ),
)

private val joinFamilyIntroSlides = listOf(
    OnboardingIntroSlide(
        icon = Icons.Filled.AutoAwesome,
        titleRes = R.string.onboarding_join_slide_1_title,
        subtitleRes = R.string.onboarding_join_slide_1_subtitle,
    ),
    OnboardingIntroSlide(
        icon = Icons.Filled.Shield,
        titleRes = R.string.onboarding_join_slide_2_title,
        subtitleRes = R.string.onboarding_join_slide_2_subtitle,
    ),
)

// ── Invite a Care Partner: contact-based invite (Steps 1-2) ─────────────────
// Ports `InviteContactAccessStep`/`InvitePickContactStep`
// (`InvitePartnerContactSteps.swift`). Real `INVITE_PARTNER` flow plan (see
// `OnboardingFlowStore.kt`): InviteContactAccess -> InvitePickContact ->
// InvitePermissions -> InviteShare -> InviteWaiting. The relation picker
// lives inline on the pick-contact screen, not as its own step, matching
// iOS's actual layout (the relation field animates in below the contact
// field once a contact is chosen) rather than the separate `PartnerRelation`
// step other flows use.

@Composable
private fun InviteContactAccessScreen(onContinue: () -> Unit) {
    val hapticManager = koinInject<AndroidHapticManager>()
    var showDeniedSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val accessTitle = stringResource(R.string.onboarding_invite_contact_access_title)
    val accessSubtitle = stringResource(R.string.onboarding_invite_contact_access_subtitle)
    val accessButtonLabel = stringResource(R.string.onboarding_invite_contact_access_button)
    val deniedTitle = stringResource(R.string.onboarding_invite_contact_denied_title)
    val deniedMessage = stringResource(R.string.onboarding_invite_contact_denied_message)
    val deniedConfirmLabel = stringResource(R.string.onboarding_invite_contact_denied_confirm)
    val deniedDismissLabel = stringResource(R.string.onboarding_invite_contact_denied_dismiss)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onContinue()
        } else {
            showDeniedSheet = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(SakhiSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(SakhiSpacing.space8))
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Spacer(modifier = Modifier.height(SakhiSpacing.space6))
        OnboardingStepTitle(
            text = accessTitle,
            textAlign = TextAlign.Center,
        )
        Text(
            text = accessSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SakhiSpacing.space2),
        )
        Spacer(modifier = Modifier.weight(1f))
    }
        SakhiFooter(
            primaryLabel = accessButtonLabel,
            onPrimaryClick = {
                hapticManager.impact(HapticImpact.LIGHT)
                permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            },
        )
    }

    // Two-tier denial handling, matching iOS's `ContactPermissionDeniedSheet`:
    // "Give Access" re-requests on a soft (rationale-eligible) denial, or opens
    // this app's Settings page once the OS has permanently denied it.
    if (showDeniedSheet) {
        SakhiAlertSheet(
            kind = SakhiAlertKind.Warning,
            title = deniedTitle,
            message = deniedMessage,
            primaryLabel = deniedConfirmLabel,
            onPrimaryClick = {
                showDeniedSheet = false
                val activity = context as? android.app.Activity
                val shouldShowRationale = activity?.let {
                    androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                        it, android.Manifest.permission.READ_CONTACTS,
                    )
                } ?: false
                if (shouldShowRationale) {
                    permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                } else {
                    val intent = Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                }
            },
            secondaryLabel = deniedDismissLabel,
            onSecondaryClick = { showDeniedSheet = false },
            onDismissRequest = { showDeniedSheet = false },
        )
    }
}

@Composable
private fun InvitePickContactScreen(
    uiState: OnboardingCareInviteUiState,
    pendingPartnerRelation: String,
    fieldError: String?,
    onContactSelected: (name: String, phone: String) -> Unit,
    onPartnerRelationSelected: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val hapticManager = koinInject<AndroidHapticManager>()
    val context = LocalContext.current
    val pickTitle = stringResource(R.string.onboarding_invite_pick_title)
    val pickSubtitle = stringResource(R.string.onboarding_invite_pick_subtitle)
    val contactPlaceholder = stringResource(R.string.onboarding_invite_pick_placeholder)
    val relationTitle = stringResource(R.string.onboarding_invite_relation_title)
    val continueLabel = stringResource(R.string.onboarding_continue)

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickContact(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val (name, phone) = queryContact(context, uri)
        if (name.isNotBlank()) {
            hapticManager.selection()
            onContactSelected(name, phone)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(SakhiSpacing.space6)
            .verticalScroll(rememberScrollState()),
    ) {
        OnboardingStepTitle(text = pickTitle)
        Text(
            text = pickSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
            modifier = Modifier.padding(top = SakhiSpacing.space1, bottom = OnboardingHeaderContentGap),
        )

        // Explicit white, like every other onboarding card. `Surface`'s default colour
        // is `colorScheme.surface`, which in this app is brand-pink tinted (and
        // `tonalElevation` tints it further with the primary colour), so leaving it
        // implicit renders a pink block instead of a card.
        Surface(
            shape = RoundedCornerShape(SakhiRadius.xl),
            color = sakhiSystemBackground(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    hapticManager.impact(HapticImpact.LIGHT)
                    contactPickerLauncher.launch(null)
                },
        ) {
            Row(
                modifier = Modifier.padding(SakhiSpacing.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(SakhiSpacing.space3))
                Text(
                    text = uiState.selectedContactName.ifBlank { contactPlaceholder },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (uiState.selectedContactName.isBlank()) {
                        sakhiSecondaryLabel()
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }

        if (uiState.selectedContactName.isNotBlank()) {
            Text(
                text = relationTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = SakhiSpacing.space6, bottom = SakhiSpacing.space3),
            )
            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
                partnerRelationOptions.forEach { option ->
                    RelationOptionCard(
                        option = option,
                        isSelected = option.value == pendingPartnerRelation,
                        onClick = { onPartnerRelationSelected(option.value) },
                    )
                }
            }
        }

        if (!fieldError.isNullOrBlank()) {
            Text(
                text = fieldError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = SakhiSpacing.space2),
            )
        }

    }
        SakhiFooter(
            primaryLabel = continueLabel,
            onPrimaryClick = {
                if (uiState.selectedContactName.isBlank() || pendingPartnerRelation.isBlank()) {
                    hapticManager.error()
                } else {
                    onContinue()
                }
            },
        )
    }
}

private fun queryContact(context: android.content.Context, uri: android.net.Uri): Pair<String, String> {
    var name = ""
    var phone = ""
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
            if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: ""

            val contactId = cursor.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
                .takeIf { it >= 0 }?.let { cursor.getString(it) }
            val hasPhone = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER)
                .takeIf { it >= 0 }?.let { cursor.getInt(it) == 1 } ?: false

            if (hasPhone && contactId != null) {
                context.contentResolver.query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null,
                )?.use { phoneCursor ->
                    if (phoneCursor.moveToFirst()) {
                        val phoneIndex = phoneCursor.getColumnIndex(
                            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                        )
                        if (phoneIndex >= 0) phone = phoneCursor.getString(phoneIndex) ?: ""
                    }
                }
            }
        }
    }
    return name to phone
}

// ── Partner Relation ─────────────────────────────────────────────────────────
// Ports `PartnerRelationStep.swift`'s 6-option relation picker. Selection is
// threaded through the shared `OnboardingFlowStore` (`PartnerRelationSelected`
// intent -> `pendingPartnerRelation`), and `handleContinue` itself rejects an
// empty selection with the exact iOS field-error copy -- same shared-first
// pattern as `TermsDecided`, not an Android-local validation fork.

private data class RelationOption(
    val value: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
)

private val partnerRelationOptions = listOf(
    RelationOption(
        value = "Boyfriend",
        titleRes = R.string.onboarding_invite_relation_boyfriend_title,
        subtitleRes = R.string.onboarding_invite_relation_boyfriend_subtitle,
        icon = Icons.Filled.Favorite,
    ),
    RelationOption(
        value = "Husband",
        titleRes = R.string.onboarding_invite_relation_husband_title,
        subtitleRes = R.string.onboarding_invite_relation_husband_subtitle,
        icon = Icons.Filled.Favorite,
    ),
    RelationOption(
        value = "Brother",
        titleRes = R.string.onboarding_invite_relation_brother_title,
        subtitleRes = R.string.onboarding_invite_relation_brother_subtitle,
        icon = Icons.Filled.Groups,
    ),
    RelationOption(
        value = "Father",
        titleRes = R.string.onboarding_invite_relation_father_title,
        subtitleRes = R.string.onboarding_invite_relation_father_subtitle,
        icon = Icons.Filled.Person,
    ),
    RelationOption(
        value = "Friend",
        titleRes = R.string.onboarding_invite_relation_friend_title,
        subtitleRes = R.string.onboarding_invite_relation_friend_subtitle,
        icon = Icons.Filled.Person,
    ),
    RelationOption(
        value = "Other",
        titleRes = R.string.onboarding_invite_relation_other_title,
        subtitleRes = R.string.onboarding_invite_relation_other_subtitle,
        icon = Icons.Filled.AutoAwesome,
    ),
)

@Composable
private fun PartnerRelationScreen(
    selected: String,
    fieldError: String?,
    onSelected: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val relationTitle = stringResource(R.string.onboarding_invite_relation_title)
    val relationSubtitle = stringResource(R.string.onboarding_invite_relation_subtitle)
    val continueLabel = stringResource(R.string.onboarding_continue)

    Column(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.weight(1f).padding(SakhiSpacing.space6)) {
        OnboardingStepTitle(text = relationTitle)
        Text(
            text = relationSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
            modifier = Modifier.padding(top = SakhiSpacing.space1, bottom = OnboardingHeaderContentGap),
        )

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            partnerRelationOptions.forEach { option ->
                RelationOptionCard(
                    option = option,
                    isSelected = option.value == selected,
                    onClick = { onSelected(option.value) },
                )
            }
        }

        if (!fieldError.isNullOrBlank()) {
            Text(
                text = fieldError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = SakhiSpacing.space2),
            )
        }
    }
        SakhiFooter(primaryLabel = continueLabel, onPrimaryClick = onContinue)
    }
}

@Composable
private fun RelationOptionCard(option: RelationOption, isSelected: Boolean, onClick: () -> Unit) {
    val stateSelected = stringResource(R.string.onboarding_state_selected)
    val stateNotSelected = stringResource(R.string.onboarding_state_not_selected)
    // Was `Surface(tonalElevation = ...)` with no explicit colour. Material3 tonal
    // elevation tints the surface with the PRIMARY colour, so every row rendered as a
    // flat pink blob with no visible selected state -- nothing like the white bordered
    // cards the rest of onboarding uses, and reported live as "kitna ganda hai".
    // Matches `PrivacyChoiceCard` (the real port of iOS `OnboardingPrivacyCard`): the
    // background never changes, only the border and the icon/title colour do, and there
    // is no circular icon badge.
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else sakhiLabel()
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xl),
        color = sakhiSystemBackground(),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.selected = isSelected
                role = Role.RadioButton
                stateDescription = if (isSelected) stateSelected else stateNotSelected
            }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = PrivacyCardHorizontalPadding,
                vertical = SakhiSpacing.space4,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(option.titleRes),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = contentColor,
                )
                Text(
                    text = stringResource(option.subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = sakhiSecondaryLabel(),
                )
            }
        }
    }
}

// ── BeHerSakhi: partner-path code entry ─────────────────────────────────────
// Ports the "new user" branch of `BeHerSakhiStep.swift`'s code-entry page only
// (store code, advance). The authenticated-user account-conflict/fetch-and-
// verify branch (`BeHerSakhiVM.fetchInvitation`, data-deletion-and-convert
// flow) needs a `CareRuntimeController.fetchInvitation` equivalent that has no
// Android/KMM home yet -- documented gap, not faked here.

@Composable
private fun BeHerSakhiScreen(
    fieldError: String?,
    onSubmit: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    val continueLabel = stringResource(R.string.onboarding_continue)

    Column(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.weight(1f).padding(SakhiSpacing.space6)) {
        OnboardingStepTitle(text = stringResource(R.string.onboarding_be_her_sakhi_title))
        Text(
            text = stringResource(R.string.onboarding_be_her_sakhi_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
            modifier = Modifier.padding(top = SakhiSpacing.space1, bottom = OnboardingHeaderContentGap),
        )

        // Was a bare Material3 `OutlinedTextField`, which renders transparent with a
        // thin grey outline -- the odd one out in a flow where every other field
        // (phone, DOB, period/cycle length) is a filled white `SakhiTextField`.
        // Same root cause Karan reported for the other fields; this screen was missed
        // because it only appears on the partner path.
        SakhiTextField(
            value = code,
            onValueChange = { code = it.take(6).uppercase() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.onboarding_be_her_sakhi_placeholder),
            singleLine = true,
            isError = !fieldError.isNullOrBlank(),
        )

        if (!fieldError.isNullOrBlank()) {
            // iOS draws the message in the brand pink at 13, not the system error red.
            Text(
                text = fieldError,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = SakhiSpacing.space2),
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
        SakhiFooter(primaryLabel = continueLabel, onPrimaryClick = { onSubmit(code) })
    }
}

// ── Phone / OTP (partner path) ───────────────────────────────────────────────
// Reuses the real, already-parity'd `AuthViewModel`/`PhoneScreen`/`OtpScreen`
// from `feature:auth` instead of duplicating a second phone/OTP UI+ViewModel.
// A single `koinViewModel()` call here stays alive across the Phone->
// OtpVerification step transition since both branch off the same composable
// call site (same slot in the composition), matching the pattern already used
// by `RootNavHost.SignedOutFlow` for the top-level sign-in screens.
//
// `verifyOtpAndClassify` (called inside `AuthViewModel.verifyOtp`) legitimately
// flips the shared `AuthRepository`/`AppStateInputBridge` session state to
// Authenticated for this newly-verified user -- traced through
// `AppStateStore.resolveRoute`: an Authenticated session with no matching
// `OnboardingCompletionBridge` signal re-resolves via `classifier.
// bootstrapAppEntry`, which still routes back to Onboarding (not Home) until
// this flow actually completes. This is the exact same mechanism the
// top-level sign-up flow already relies on, not a new risk introduced here.
@Composable
private fun OnboardingPhoneOtpScreen(
    step: OnboardingFlowStep,
    onContinue: () -> Unit,
    onOtpResolved: (isReturningUser: Boolean, hasExistingOwnAccount: Boolean) -> Unit,
    authViewModel: AuthViewModel = koinViewModel(),
) {
    // The visible back button and the top-bar spacing are now owned by
    // `OnboardingFlowHost`'s shared top bar (matching iOS's single `regularShell`
    // chrome), so this screen no longer overlays its own -- it just renders the
    // Phone/OTP content.
    // The reset below is still needed: system back can step from OtpVerification back
    // to Phone (the host's `BackHandler` decrements the step index), and without this
    // `PhoneScreen`'s own `otpSentTo != null` auto-advance check would see the still-set
    // OTP destination the instant it recomposes and immediately jump back forward to
    // OtpVerification -- back would appear to do nothing. Keyed on `step` (not a one-shot
    // top-of-flow reset) since this step can be revisited mid-flow.
    //
    // DELIBERATELY `remember`, not `LaunchedEffect` -- reported live as "back button
    // isn't going back at all." `LaunchedEffect` dispatches its body as a coroutine
    // that only runs after this composition commits; on the very frame `step` flips
    // back to `Phone`, `PhoneScreen` below reads the still-stale `otpSentTo` and
    // re-fires `onOtpSent` before that coroutine ever gets a turn, jumping straight
    // back to OtpVerification -- the exact race this comment already warned about,
    // just not far enough: an effect that runs "soon" still loses to a composable
    // that reads state synchronously on the same pass. `remember`'s calculation
    // block runs synchronously during composition, before `PhoneScreen` below sees
    // the state, which actually closes the race.
    remember(step) {
        if (step == OnboardingFlowStep.Phone) {
            authViewModel.resetPhoneFlow()
        }
    }

    when (step) {
        OnboardingFlowStep.Phone -> PhoneScreen(
            onOtpSent = { onContinue() },
            viewModel = authViewModel,
        )
        OnboardingFlowStep.OtpVerification -> OtpScreen(
            onOtpVerified = { result ->
                onOtpResolved(
                    result.accountState is AccountState.ExistingComplete,
                    result.accountState.isExistingOwnAccount(),
                )
            },
            viewModel = authViewModel,
        )
        else -> {}
    }
}

// Gates `PartnerConversionWarning` -- the "You already have an account" screen that
// offers to delete her own health data and continue as a partner, or keep the account.
// This used to be the same `accountState is ExistingComplete` test that decides whether
// to skip the remaining profile steps, and that let two real existing-account cases walk
// straight past the warning into `BeHerAccept`, silently becoming a partner:
//
//  - `LocalOnlyComplete`: an offline-first user who finished onboarding on this device.
//    She is the case that most needs asking, because her cycle history is definitely
//    there and `ExistingComplete` never covered her.
//  - `ExistingIncomplete`: `AccountClassifier.classify` also returns this from its own
//    `catch` when the remote profile lookup THROWS, so an existing account on a flaky
//    connection lands here alongside the genuinely-unfinished signups.
//
// Only a true `NewAccount` has nothing of her own to ask about. For the rest, one extra
// screen she can decline with "Keep My Account" is the cheap side of this trade; guessing
// wrong the other way deletes health data without ever asking. `SessionExpired` re-enters
// through `newUser` and never reaches this branch with an invite code.
internal fun AccountState.isExistingOwnAccount(): Boolean = when (this) {
    is AccountState.ExistingComplete,
    is AccountState.ExistingIncomplete,
    is AccountState.LocalOnlyComplete,
    -> true
    AccountState.NewAccount,
    is AccountState.SessionExpired,
    -> false
}

// ── BeHerAccept ───────────────────────────────────────────────────────────────
// Ports `BeHerAcceptStep.swift`'s auto-accept flow for the new-user
// `BeHerSakhi` code-entry path: loading -> success (auto-advance after the
// same ~2.2s beat iOS uses) or error with retry. The existing-account
// data-deletion-and-convert branch is the same documented gap as
// `BeHerSakhiScreen` above -- not reachable via the path this screen serves.

@Composable
private fun BeHerAcceptScreen(
    uiState: OnboardingAcceptUiState,
    onAccept: () -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { onAccept() }
    val context = LocalContext.current
    val successTitle = stringResource(R.string.onboarding_accept_success_title)
    val successSubtitle = stringResource(R.string.onboarding_accept_success_subtitle)
    val connectionIssueTitle = stringResource(R.string.onboarding_accept_error_connection_title)
    val invalidCodeTitle = stringResource(R.string.onboarding_accept_error_invalid_title)
    // iOS `common.retry` is "Try Again", not the "Retry" the setup error uses.
    val retryLabel = stringResource(R.string.onboarding_accept_try_again)
    val differentCodeLabel = stringResource(R.string.onboarding_accept_use_different_code)
    val contactSupportLabel = stringResource(R.string.onboarding_accept_contact_support)
    val loadingMessages = listOf(
        stringResource(R.string.onboarding_accept_loading_1),
        stringResource(R.string.onboarding_accept_loading_2),
        stringResource(R.string.onboarding_accept_loading_3),
    )

    LaunchedEffect(uiState.succeeded) {
        if (uiState.succeeded) {
            kotlinx.coroutines.delay(2_200)
            onComplete()
        }
    }

    when {
        uiState.succeeded -> Column(
            modifier = Modifier.fillMaxSize().padding(SakhiSpacing.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(sakhiLightPink(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Text(
                text = successTitle,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space4),
            )
            Text(
                text = successSubtitle,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = sakhiSecondaryLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = SakhiSpacing.space4)
                    .padding(horizontal = SakhiSpacing.space8),
            )
        }

        uiState.error != null -> Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(sakhiLightPink(), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        // iOS `wifi.exclamationmark` when it can be tried again, and
                        // `link.badge.plus` when the code itself is the problem.
                        imageVector = if (uiState.canRetry) Icons.Filled.WifiOff else Icons.Filled.AddLink,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    text = if (uiState.canRetry) connectionIssueTitle else invalidCodeTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = sakhiLabel(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = SakhiSpacing.space4),
                )
                Text(
                    text = uiState.error,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = sakhiSecondaryLabel(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = SakhiSpacing.space4)
                        .padding(horizontal = SakhiSpacing.space6),
                )
            }
            if (uiState.canRetry) {
                SakhiFooter(primaryLabel = retryLabel, onPrimaryClick = onAccept, showSecondarySlot = false)
            } else {
                SakhiFooter(
                    primaryLabel = differentCodeLabel,
                    onPrimaryClick = onBack,
                    secondaryLabel = contactSupportLabel,
                    onSecondaryClick = {
                        // iOS opens the mail app to the support address.
                        val mail = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:support@sakhi.app"))
                        runCatching { context.startActivity(mail) }
                    },
                )
            }
        }

        // iOS `CareProgressLoadingView(stage: .acceptingInvite)`.
        else -> SakhiLoadingView(context = SakhiLoadingContext.Messages(loadingMessages))
    }
}

// ── PartnerConversionWarning ─────────────────────────────────────────────────
// Ports the `PartnerConversionWarningView` half of `BeHerAcceptStep.swift`:
// shown only when the phone number just verified already has a completed
// Sakhi account of its own (see the `handleOtpVerified` fix in
// `OnboardingFlowStore.kt`). Deletion uses the already-shared KMM
// `PeriodLogRepository`/`CycleDataRepository.deleteAll`, not new logic.

@Composable
private fun PartnerConversionWarningScreen(
    uiState: OnboardingConversionUiState,
    onConvert: () -> Unit,
    onKeepOwnAccount: () -> Unit,
) {
    val hapticManager = koinInject<AndroidHapticManager>()
    var showConfirm by remember { mutableStateOf(false) }
    val title = stringResource(R.string.onboarding_partner_conversion_title)
    val subtitle = stringResource(R.string.onboarding_partner_conversion_subtitle)
    val continueAsPartnerLabel = stringResource(R.string.onboarding_partner_conversion_continue)
    val convertingLabel = stringResource(R.string.onboarding_partner_conversion_converting)
    val keepAccountLabel = stringResource(R.string.onboarding_partner_conversion_keep_account)
    val confirmTitle = stringResource(R.string.onboarding_partner_conversion_confirm_title)
    val confirmMessage = stringResource(R.string.onboarding_partner_conversion_confirm_message)
    val confirmButtonLabel = stringResource(R.string.onboarding_partner_conversion_confirm_button)
    val cancelLabel = stringResource(R.string.onboarding_cancel)

    Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.weight(1f).padding(SakhiSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(88.dp)
                .background(sakhiLightPink(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // iOS `exclamationmark.triangle.fill`.
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }

        Text(
            text = title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiLabel(),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SakhiSpacing.space6),
        )
        Text(
            text = subtitle,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = sakhiSecondaryLabel(),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = SakhiSpacing.space3)
                .padding(horizontal = SakhiSpacing.space4),
        )

        if (uiState.error != null) {
            Text(
                text = uiState.error,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = SakhiSpacing.space3)
                    .padding(horizontal = SakhiSpacing.space8),
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
        SakhiFooter(
            primaryLabel = if (uiState.isConverting) convertingLabel else continueAsPartnerLabel,
            primaryEnabled = !uiState.isConverting,
            onPrimaryClick = {
                hapticManager.impact(HapticImpact.MEDIUM)
                showConfirm = true
            },
            secondaryLabel = keepAccountLabel,
            onSecondaryClick = onKeepOwnAccount,
            secondaryEnabled = !uiState.isConverting,
        )
    }

    if (showConfirm) {
        SakhiAlertSheet(
            kind = SakhiAlertKind.Destructive,
            title = confirmTitle,
            message = confirmMessage,
            primaryLabel = confirmButtonLabel,
            onPrimaryClick = {
                showConfirm = false
                onConvert()
            },
            secondaryLabel = cancelLabel,
            onSecondaryClick = { showConfirm = false },
            onDismissRequest = { showConfirm = false },
        )
    }
}

// ── DataSource ────────────────────────────────────────────────────────────────
// Ports `DataSourceStep.swift`'s real 2-card branch: manual always restores the
// complete health-question tail, while Health Connect attempts a platform import
// and trims the remaining KMM plan down to only the fields that still need
// manual input. Failure stays sticky on the import card until the user accepts
// the same "fill in your details yourself" fallback iOS shows.

@Composable
private fun DataSourceScreen(
    uiState: OnboardingDataSourceUiState,
    onSelectHealthConnect: () -> Unit,
    onSelectManual: () -> Unit,
    onContinueManual: () -> Unit,
    onImportHealthConnect: () -> Unit,
    onHealthConnectPermissionsResult: (Set<String>) -> Unit,
    onShowFailureAlert: () -> Unit,
    onAcknowledgeFailure: () -> Unit,
    onHealthConnectUnavailable: () -> Unit,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
        onResult = onHealthConnectPermissionsResult,
    )
    val continueLabel = stringResource(R.string.onboarding_continue)
    val importingLabel = stringResource(R.string.onboarding_importing)

    Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = SakhiSpacing.space6)
            .padding(top = SakhiSpacing.space6),
    ) {
        OnboardingStepTitle(
            text = if (uiState.importFailed) {
                stringResource(R.string.onboarding_data_source_import_failed_title)
            } else {
                stringResource(R.string.onboarding_data_source_title)
            },
        )
        Text(
            text = if (uiState.importFailed) {
                stringResource(R.string.onboarding_data_source_import_failed_subtitle)
            } else {
                stringResource(R.string.onboarding_data_source_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
            modifier = Modifier.padding(top = SakhiSpacing.space1, bottom = OnboardingHeaderContentGap),
        )

        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
            PrivacyChoiceCard(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.onboarding_data_source_health_connect_title),
                description = stringResource(R.string.onboarding_data_source_health_connect_description),
                isSelected = uiState.selectedChoice == OnboardingDataSourceChoice.HealthConnect,
                isError = uiState.importFailed,
                onClick = {
                    if (uiState.importFailed) {
                        onShowFailureAlert()
                    } else {
                        onSelectHealthConnect()
                    }
                },
            )
            PrivacyChoiceCard(
                icon = Icons.Filled.TouchApp,
                title = stringResource(R.string.onboarding_data_source_manual_title),
                description = stringResource(R.string.onboarding_data_source_manual_description),
                isSelected = uiState.selectedChoice == OnboardingDataSourceChoice.Manual,
                onClick = onSelectManual,
            )
        }

    }

        SakhiFooter(
            primaryLabel = if (uiState.isImporting) importingLabel else continueLabel,
            primaryEnabled = !uiState.isImporting,
            onPrimaryClick = {
                when (uiState.selectedChoice) {
                    OnboardingDataSourceChoice.Manual -> onContinueManual()
                    // No installed app feeds Health Connect, so there is nothing to
                    // import from. Show Sakhi's alert instead of walking the user into
                    // Health Connect's own onboarding to reach a dead end.
                    OnboardingDataSourceChoice.HealthConnect -> if (
                        uiState.availability == HealthConnectAvailability.Available &&
                        uiState.sourceApps.isEmpty()
                    ) {
                        onHealthConnectUnavailable()
                    } else when (uiState.availability) {
                        HealthConnectAvailability.Available -> {
                            if (uiState.hasPermissions) {
                                onImportHealthConnect()
                            } else {
                                permissionLauncher.launch(uiState.requiredPermissions)
                            }
                        }

                        HealthConnectAvailability.NotInstalled,
                        HealthConnectAvailability.NotSupported -> onHealthConnectUnavailable()
                    }
                }
            },
        )
    }

    if (uiState.showFailureAlert) {
        // iOS presents this through `.sakhiAlert(type: .info, ...)`, which is Sakhi's
        // own 300pt bottom sheet with the dashed-ring badge -- not a system dialog.
        // Android was using a raw Material3 `AlertDialog`, which shares none of that
        // styling. `SakhiAlert` could not be swapped in directly: it is an inline
        // banner, so `SakhiAlertSheet` was added to core/ui for this.
        SakhiAlertSheet(
            kind = SakhiAlertKind.Info,
            title = stringResource(R.string.onboarding_data_source_no_data_title),
            message = uiState.failureMessage
                ?: stringResource(R.string.onboarding_data_source_no_data_message),
            primaryLabel = stringResource(R.string.onboarding_ok),
            // iOS's primary button switches the choice to manual entry, which is what
            // `onAcknowledgeFailure` already does here.
            onPrimaryClick = onAcknowledgeFailure,
            onDismissRequest = onAcknowledgeFailure,
        )
    }
}

// ── SetupLoading ────────────────────────────────────────────────────────────
// Ports `SakhiSetupLoadingView`'s new-user save path (animated loading ->
// success -> flow completes). This is the step that actually persists
// everything the health-step screens collected -- see
// `OnboardingViewModel.handleSetupLoading()` for the save + the returning-user
// skip logic.

@Composable
private fun SetupLoadingScreen(uiState: OnboardingSetupUiState, onSetupLoading: () -> Unit) {
    LaunchedEffect(Unit) { onSetupLoading() }
    val retryLabel = stringResource(R.string.onboarding_retry)

    if (uiState.error != null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(SakhiSpacing.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.onboarding_setup_error_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Text(
                text = uiState.error,
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space2, bottom = SakhiSpacing.space5),
            )
            PrimaryButton(text = retryLabel, onClick = onSetupLoading, modifier = Modifier.fillMaxWidth())
        }
    } else {
        // Real `SakhiLoadingView` (the app's shared loading component, ported from
        // iOS's `SakhiLoadingView.swift`) instead of a bare Material spinner -- iOS's
        // `SakhiSetupLoadingStep` renders exactly this, in its cycling-`messages`
        // mode. Deliberately NOT wrapped in the padded/centred Column the error branch
        // uses: this view owns its own full-bleed background and centring, per Karan
        // ("vo har loading ke samay aayega fully screen mai").
        SakhiLoadingView(
            context = SakhiLoadingContext.Messages(
                listOf(
                    stringResource(R.string.onboarding_setup_loading_message),
                    stringResource(R.string.onboarding_setup_loading_message_2),
                    stringResource(R.string.onboarding_setup_loading_message_3),
                ),
            ),
        )
    }
}
