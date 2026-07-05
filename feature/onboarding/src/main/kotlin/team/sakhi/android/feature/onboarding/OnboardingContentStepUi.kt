package team.sakhi.android.feature.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.health.connect.client.PermissionController
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HealthConnectAvailability
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.feature.auth.AuthViewModel
import team.sakhi.android.feature.auth.OtpScreen
import team.sakhi.android.feature.auth.PhoneScreen
import team.sakhi.android.ui.GlassCard
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SakhiAlert
import team.sakhi.android.ui.SakhiAlertTone
import team.sakhi.android.ui.SecondaryButton
import team.sakhi.auth.AccountState
import team.sakhi.models.ParentChildPermissions
import team.sakhi.onboarding.OnboardingFlowStep

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
    onOtpResolved: (isReturningUser: Boolean) -> Unit = {},
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
            uiState = careInviteUiState,
            onContinueToInviteWaiting = onContinueToInviteWaiting,
            onCancelInvitation = { onCancelInvitation(true) },
            onDismissError = onDismissInviteError,
        )
        OnboardingFlowStep.InviteWaiting -> InviteWaitingScreen(
            uiState = careInviteUiState,
            onContinue = onContinue,
            onCancelInvitation = { onCancelInvitation(true) },
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
            canGoBack = canGoBack,
            fieldError = fieldError,
            onSubmit = onBeHerSakhiCodeSubmitted,
            onBack = onBack,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = "Who Are You Here For?",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = "We'll tailor your experience just for you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ModeSelectionCard(
            icon = Icons.Filled.Person,
            title = "Myself",
            description = "Track your own health and cycle, and get answers made just for you.",
            isSelected = !isPartnerSelected,
            onClick = {
                hapticManager.impact(HapticImpact.LIGHT)
                isPartnerSelected = false
            },
        )
        ModeSelectionCard(
            icon = Icons.Filled.Groups,
            title = "My Partner",
            description = "Care for someone you love. See what they share and be there when it matters.",
            isSelected = isPartnerSelected,
            onClick = {
                hapticManager.impact(HapticImpact.LIGHT)
                isPartnerSelected = true
            },
        )

        Spacer(modifier = Modifier.weight(1f))

        PrimaryButton(
            text = "Continue",
            onClick = { onModeSelected(isPartnerSelected) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ModeSelectionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xxl),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.selected = isSelected
                role = Role.RadioButton
                stateDescription = if (isSelected) "Selected" else "Not selected"
            }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape,
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PartnerInvitePromptScreen(
    canGoBack: Boolean,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onStartCareInviteUpgrade: () -> Unit,
) {
    val hapticManager = koinInject<AndroidHapticManager>()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space6),
    ) {
        Text(
            text = "Bring Someone In?",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = "Someone who cares about you might want to be here for you. You can always do this later from Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = SakhiSpacing.space2),
        )

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SakhiSpacing.space6),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = SakhiSpacing.space4),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            CarePromptBullet(
                icon = Icons.Filled.VisibilityOff,
                text = "They only see what you choose to share",
            )
            CarePromptBullet(
                icon = Icons.Filled.Notifications,
                text = "Get gentle check-ins when it matters",
            )
            CarePromptBullet(
                icon = Icons.Filled.Favorite,
                text = "They can help log your period on your behalf",
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        PrimaryButton(
            text = "Invite a Care Partner",
            onClick = {
                hapticManager.impact(HapticImpact.MEDIUM)
                onContinue()
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (canGoBack) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Back")
            }
        } else {
            TextButton(
                onClick = onStartCareInviteUpgrade,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Continue as Care Partner")
            }
        }
    }
}

@Composable
private fun CarePromptBullet(
    icon: ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SakhiSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InvitePermissionsScreen(
    uiState: OnboardingCareInviteUiState,
    onPermissionsChanged: (ParentChildPermissions) -> Unit,
    onContinue: () -> Unit,
    onDismissError: () -> Unit,
) {
    var allowAll by remember(uiState.permissions) { mutableStateOf(uiState.permissions.isFullyShared()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SakhiSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = "Share Only What Feels Right",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = "What stays on, they see. What stays off, stays yours.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        uiState.errorMessage?.let { error ->
            SakhiAlert(
                title = "Couldn't create invite",
                message = error,
                tone = SakhiAlertTone.Error,
                onDismiss = onDismissError,
            )
        }

        PermissionCard(
            icon = Icons.Filled.AutoAwesome,
                title = "Allow everything",
                description = "She can see all your cycle data and help log periods.",
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
                title = "Cycle & Periods",
                description = "Period dates, cycle history, predictions, period logging.",
                isOn = uiState.permissions.isCycleEnabled(),
                onToggle = { enabled ->
                    onPermissionsChanged(uiState.permissions.withCyclePermissions(enabled))
                    allowAll = false
                },
            )
            PermissionCard(
                icon = Icons.Filled.Favorite,
                title = "Symptoms & Moods",
                description = "How you're feeling physically and emotionally each day.",
                isOn = uiState.permissions.isSymptomsEnabled(),
                onToggle = { enabled ->
                    onPermissionsChanged(uiState.permissions.withSymptomsPermissions(enabled))
                    allowAll = false
                },
            )
            PermissionCard(
                icon = Icons.Filled.CheckCircle,
                title = "Daily Logs",
                description = "Daily check-ins and ovulation test results.",
                isOn = uiState.permissions.isDailyLogsEnabled(),
                onToggle = { enabled ->
                    onPermissionsChanged(uiState.permissions.withDailyLogPermissions(enabled))
                    allowAll = false
                },
            )
            PermissionCard(
                icon = Icons.Filled.Sync,
                title = "Body Stats",
                description = "Temperature, weight, and discharge tracking.",
                isOn = uiState.permissions.isBodyStatsEnabled(),
                onToggle = { enabled ->
                    onPermissionsChanged(uiState.permissions.withBodyStatsPermissions(enabled))
                    allowAll = false
                },
            )
            PermissionCard(
                icon = Icons.Filled.Lock,
                title = "Private Data",
                description = "Personal notes and medications.",
                isOn = uiState.permissions.isPrivateDataEnabled(),
                onToggle = { enabled ->
                    onPermissionsChanged(uiState.permissions.withPrivateDataPermissions(enabled))
                    allowAll = false
                },
            )
        }

        PrimaryButton(
            text = if (uiState.isCreatingInvite) "Creating invite..." else "Continue",
            onClick = onContinue,
            enabled = !uiState.isCreatingInvite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SakhiSpacing.space2),
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
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xxl),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isOn) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Switch(
                checked = isOn,
                onCheckedChange = onToggle,
            )
        }
    }
}

@Composable
private fun InviteShareScreen(
    uiState: OnboardingCareInviteUiState,
    onContinueToInviteWaiting: () -> Unit,
    onCancelInvitation: () -> Unit,
    onDismissError: () -> Unit,
) {
    val context = LocalContext.current
    val hapticManager = koinInject<AndroidHapticManager>()
    val displayName = uiState.pendingInvitation.displayName()
    val shareMessage = "Hey! I use Sakhi to track my health. Open the app, go to My Sakhi, tap I have a code, and enter: ${uiState.inviteCode}"
    var copyNotice by remember { mutableStateOf<String?>(null) }
    var previousConnected by remember { mutableStateOf(uiState.isConnected) }

    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected && !previousConnected) {
            hapticManager.success()
        }
        previousConnected = uiState.isConnected
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        InviteHero(icon = Icons.Filled.Share)

        Text(
            text = "Share with $displayName",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SakhiSpacing.space6),
        )
        Text(
            text = "Ask $displayName to open Sakhi and enter this code to connect.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SakhiSpacing.space2),
        )

        uiState.errorMessage?.let { error ->
            SakhiAlert(
                title = "Couldn't finish that",
                message = error,
                tone = SakhiAlertTone.Error,
                onDismiss = onDismissError,
                modifier = Modifier.padding(top = SakhiSpacing.space4),
            )
        }

        copyNotice?.let { notice ->
            SakhiAlert(
                title = "Code Copied",
                message = notice,
                modifier = Modifier.padding(top = SakhiSpacing.space4),
                onDismiss = { copyNotice = null },
            )
        }

        InviteCodeChip(
            code = uiState.inviteCode,
            modifier = Modifier.padding(top = SakhiSpacing.space5),
            onCopy = {
                hapticManager.success()
                copyInviteCode(context = context, code = uiState.inviteCode)
                copyNotice = "Share it with $displayName."
            },
        )

        Spacer(modifier = Modifier.weight(1f))

        PrimaryButton(
            text = "Share",
            onClick = {
                hapticManager.impact(HapticImpact.MEDIUM)
                shareInviteMessage(context, shareMessage)
                onContinueToInviteWaiting()
            },
            enabled = uiState.inviteCode.isNotBlank() && !uiState.isCancellingInvite,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = if (uiState.isCancellingInvite) "Cancelling..." else "Cancel",
            onClick = onCancelInvitation,
            enabled = !uiState.isCancellingInvite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SakhiSpacing.space2),
        )
    }
}

@Composable
private fun InviteWaitingScreen(
    uiState: OnboardingCareInviteUiState,
    onContinue: () -> Unit,
    onCancelInvitation: () -> Unit,
    onDismissError: () -> Unit,
) {
    val context = LocalContext.current
    val hapticManager = koinInject<AndroidHapticManager>()
    val displayName = uiState.pendingInvitation.displayName()
    val shareMessage = "Hey! I use Sakhi to track my health. Open the app, go to My Sakhi, tap I have a code, and enter: ${uiState.inviteCode}"
    var copyNotice by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        InviteHero(icon = if (uiState.isConnected) Icons.Filled.CheckCircle else Icons.Filled.Groups)

        val title = if (uiState.isConnected) {
            "Connected"
        } else {
            "Waiting for $displayName to accept. I'm making something just between you two."
        }
        val subtitle = if (uiState.isConnected) {
            "$displayName is now your care partner."
        } else {
            "We'll let you know as soon as $displayName connects."
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SakhiSpacing.space6),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SakhiSpacing.space2),
        )

        uiState.errorMessage?.let { error ->
            SakhiAlert(
                title = "Couldn't finish that",
                message = error,
                tone = SakhiAlertTone.Error,
                onDismiss = onDismissError,
                modifier = Modifier.padding(top = SakhiSpacing.space4),
            )
        }

        if (!uiState.cancelMessage.isNullOrBlank()) {
            SakhiAlert(
                message = uiState.cancelMessage,
                modifier = Modifier.padding(top = SakhiSpacing.space4),
            )
        }

        copyNotice?.let { notice ->
            SakhiAlert(
                title = "Code Copied",
                message = notice,
                modifier = Modifier.padding(top = SakhiSpacing.space4),
                onDismiss = { copyNotice = null },
            )
        }

        if (!uiState.isConnected) {
            InviteCodeChip(
                code = uiState.inviteCode,
                modifier = Modifier.padding(top = SakhiSpacing.space5),
                onCopy = {
                    hapticManager.success()
                    copyInviteCode(context = context, code = uiState.inviteCode)
                    copyNotice = "Share it with $displayName."
                },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (uiState.isConnected) {
            PrimaryButton(
                text = "Let's Get Started",
                onClick = {
                    hapticManager.impact(HapticImpact.MEDIUM)
                    onContinue()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PrimaryButton(
                text = "Share",
                onClick = {
                    hapticManager.impact(HapticImpact.MEDIUM)
                    shareInviteMessage(context, shareMessage)
                },
                enabled = uiState.inviteCode.isNotBlank() && !uiState.isCancellingInvite,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = if (uiState.isCancellingInvite) "Cancelling..." else "Cancel",
                onClick = onCancelInvitation,
                enabled = !uiState.isCancellingInvite,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SakhiSpacing.space2),
            )
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

    Surface(
        shape = RoundedCornerShape(SakhiRadius.full),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        modifier = modifier.clickable(onClick = onCopy),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SakhiSpacing.space5,
                vertical = SakhiSpacing.space3,
            ),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatInviteCode(code),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun copyInviteCode(
    context: android.content.Context,
    code: String,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Sakhi invite code", code))
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
    val name = this?.inviteeName?.trim().orEmpty()
    return if (name.isBlank()) "your partner" else name
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

    Column(modifier = Modifier.fillMaxSize().padding(SakhiSpacing.space6)) {
        Text(
            text = "Your Privacy Comes First, Always.",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = SakhiSpacing.space6),
        )

        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4)) {
            PrivacyChoiceCard(
                icon = Icons.Filled.Shield,
                title = "Secure my data",
                description = "Create an account to safely back up your data and access it anytime, across your devices.",
                isSelected = !isOfflineSelected,
                onClick = { isOfflineSelected = false },
            )
            PrivacyChoiceCard(
                icon = Icons.Filled.PhoneAndroid,
                title = "Keep data on this device only",
                description = "Your data stays on this device and won't be available if you change or lose your phone.",
                isSelected = isOfflineSelected,
                onClick = { isOfflineSelected = true },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        PrimaryButton(
            text = "Continue",
            onClick = { onContinue(isOfflineSelected) },
            modifier = Modifier.fillMaxWidth(),
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
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xl),
        tonalElevation = SakhiSpacing.space1,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.selected = isSelected
                role = Role.RadioButton
                stateDescription = when {
                    isError -> "Needs attention"
                    isSelected -> "Selected"
                    else -> "Not selected"
                }
            }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space5),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .border(
                        width = 2.dp,
                        color = when {
                            isError -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    isSelected -> {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

// ── Terms ───────────────────────────────────────────────────────────────────

private const val privacyFallbackText = """At Sakhi, protecting your privacy is our highest priority. Your data is securely stored and remains solely under your control.

Your personal information, including cycle data, is never shared with third parties. We do not track, sell, or distribute your data. Only you have access to your information.

By using Sakhi, you retain full control over your data, and you may request its deletion at any time.

We use industry-standard encryption to safeguard your health information. Your cycle, health metrics, and personal details are protected end-to-end.

Sakhi will never sell your data to advertisers or data brokers. We believe your health is your business alone."""

@Composable
private fun TermsScreen(fieldError: String?, onContinue: (accepted: Boolean) -> Unit) {
    val hapticManager = koinInject<AndroidHapticManager>()
    var hasAccepted by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(SakhiSpacing.space6)) {
        Text(
            text = "You're in",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = "Sakhi encrypts and protects everything you track.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = SakhiSpacing.space1, bottom = SakhiSpacing.space5),
        )

        Surface(
            shape = RoundedCornerShape(SakhiRadius.xl),
            tonalElevation = SakhiSpacing.space1,
            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
        ) {
            Text(
                text = privacyFallbackText,
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
                .semantics {
                    selected = hasAccepted
                    role = Role.Checkbox
                    stateDescription = if (hasAccepted) "Selected" else "Not selected"
                }
                .clickable {
                    hapticManager.impact(HapticImpact.LIGHT)
                    hasAccepted = !hasAccepted
                }
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(SakhiRadius.xl))
                .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        color = if (hasAccepted) MaterialTheme.colorScheme.primary else Color.Transparent,
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
                text = "I agree to the Terms & Conditions and Privacy Policy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }

        fieldError?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = SakhiSpacing.space3),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        PrimaryButton(
            text = "Continue",
            onClick = { onContinue(hasAccepted) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Universal Intro / Celebration / Offline Warning ───────────────────────

@Composable
private fun UniversalIntroScreen(onContinue: () -> Unit) {
    HeroContentStep(
        icon = Icons.Filled.AutoAwesome,
        title = "Sakhi",
        subtitle = "Track your cycle, understand your body, and feel a little more supported every day.",
        primaryLabel = "Continue",
        onPrimaryClick = onContinue,
    )
}

@Composable
private fun CelebrationScreen(onContinue: () -> Unit) {
    HeroContentStep(
        icon = Icons.Filled.Favorite,
        title = "You're all set",
        subtitle = "Sakhi is ready. Let's start with a few quick things so your cycle stays accurate from day one.",
        primaryLabel = "Continue",
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
    var pageIndex by remember(slides) { mutableStateOf(0) }
    val currentSlide = slides[pageIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (canGoBack) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text("Back")
            }
        } else {
            Spacer(modifier = Modifier.size(SakhiSpacing.space8))
        }

        Spacer(modifier = Modifier.weight(1f))

        InviteHero(icon = currentSlide.icon)
        OnboardingDots(
            currentIndex = pageIndex,
            totalCount = slides.size,
            modifier = Modifier.padding(top = SakhiSpacing.space5),
        )
        Text(
            text = currentSlide.title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SakhiSpacing.space6),
        )
        Text(
            text = currentSlide.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SakhiSpacing.space2),
        )

        Spacer(modifier = Modifier.weight(1f))

        PrimaryButton(
            text = "Continue",
            onClick = {
                if (pageIndex < slides.lastIndex) {
                    pageIndex += 1
                } else {
                    onContinue()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OfflineWarningScreen(
    canGoBack: Boolean,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    HeroContentStep(
        icon = Icons.Filled.CloudOff,
        title = "You can use Sakhi offline",
        subtitle = "Some features will be limited until you're back online, but your data will stay on this device.",
        primaryLabel = "Continue",
        onPrimaryClick = onContinue,
        secondaryLabel = if (canGoBack) "Back" else null,
        onSecondaryClick = if (canGoBack) onBack else null,
    )
}

@Composable
private fun JoinFamilyIntroScreen(onContinue: () -> Unit) {
    HeroContentStep(
        icon = Icons.Filled.Groups,
        title = "You Belong Here",
        subtitle = "Your history stays safe, right where you left it. And you're no longer doing this alone.",
        primaryLabel = "Continue",
        onPrimaryClick = onContinue,
    )
}

@Composable
private fun CareUpgradeIntroScreen(onContinue: () -> Unit) {
    HeroContentStep(
        icon = Icons.Filled.CloudOff,
        title = "You're using Sakhi offline",
        subtitle = "Care partner setup needs your online account so Sakhi can create and share the invite securely. Your logs stay safe when you continue.",
        primaryLabel = "Create account",
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        InviteHero(icon = icon)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SakhiSpacing.space6),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SakhiSpacing.space2),
        )
        Spacer(modifier = Modifier.weight(1f))
        PrimaryButton(
            text = primaryLabel,
            onClick = onPrimaryClick,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!secondaryLabel.isNullOrBlank() && onSecondaryClick != null) {
            TextButton(onClick = onSecondaryClick) {
                Text(secondaryLabel)
            }
        }
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

private data class OnboardingIntroSlide(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

private val myselfIntroSlides = listOf(
    OnboardingIntroSlide(
        icon = Icons.Filled.AutoAwesome,
        title = "Log It in One Tap",
        subtitle = "A cramp, a mood, a long day. Tap once and Sakhi remembers, so you never have to carry it all in your head.",
    ),
    OnboardingIntroSlide(
        icon = Icons.Filled.Favorite,
        title = "Made for You",
        subtitle = "Not the same tips everyone gets. What Sakhi suggests is shaped by your body, your cycle, your day.",
    ),
    OnboardingIntroSlide(
        icon = Icons.Filled.Groups,
        title = "Never Do It Alone",
        subtitle = "Track just for you, or let someone you trust be there for you. You choose what they see, always.",
    ),
)

private val joinFamilyIntroSlides = listOf(
    OnboardingIntroSlide(
        icon = Icons.Filled.AutoAwesome,
        title = "Almost There",
        subtitle = "A safe space built just for you. Your cycle, your body, always protected.",
    ),
    OnboardingIntroSlide(
        icon = Icons.Filled.Shield,
        title = "Always Yours",
        subtitle = "Encrypted and private. Sakhi will never share your health information with anyone.",
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onContinue()
        } else {
            showDeniedSheet = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
        Text(
            text = "Invite a Care Partner",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Sakhi needs access to your contacts so you can quickly find and invite someone to be your Care Partner.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SakhiSpacing.space2),
        )
        Spacer(modifier = Modifier.weight(1f))
        PrimaryButton(
            text = "Allow Contacts Access",
            onClick = {
                hapticManager.impact(HapticImpact.LIGHT)
                permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // Two-tier denial handling, matching iOS's `ContactPermissionDeniedSheet`:
    // "Give Access" re-requests on a soft (rationale-eligible) denial, or opens
    // this app's Settings page once the OS has permanently denied it.
    if (showDeniedSheet) {
        AlertDialog(
            onDismissRequest = { showDeniedSheet = false },
            title = { Text("Contacts access needed") },
            text = { Text("Sakhi needs contacts access to invite a Care Partner. You can allow it from Settings.") },
            confirmButton = {
                TextButton(onClick = {
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
                }) {
                    Text("Give Access")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeniedSheet = false }) {
                    Text("Not Now")
                }
            },
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space6)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Choose Who to Invite",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = "Pick someone from your contacts to be your Care Partner.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = SakhiSpacing.space1, bottom = SakhiSpacing.space5),
        )

        Surface(
            shape = RoundedCornerShape(SakhiRadius.xl),
            tonalElevation = SakhiSpacing.space1,
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
                    text = uiState.selectedContactName.ifBlank { "Choose a contact" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (uiState.selectedContactName.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }

        if (uiState.selectedContactName.isNotBlank()) {
            Text(
                text = "Their Relation to You",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = SakhiSpacing.space6, bottom = SakhiSpacing.space3),
            )
            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
                partnerRelationOptions.forEach { option ->
                    RelationOptionCard(
                        option = option,
                        isSelected = option.title == pendingPartnerRelation,
                        onClick = { onPartnerRelationSelected(option.title) },
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

        Spacer(modifier = Modifier.height(SakhiSpacing.space6))
        PrimaryButton(
            text = "Continue",
            onClick = {
                if (uiState.selectedContactName.isBlank() || pendingPartnerRelation.isBlank()) {
                    hapticManager.error()
                } else {
                    onContinue()
                }
            },
            modifier = Modifier.fillMaxWidth(),
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

private data class RelationOption(val title: String, val subtitle: String, val icon: ImageVector)

private val partnerRelationOptions = listOf(
    RelationOption("Boyfriend", "Your partner", Icons.Filled.Favorite),
    RelationOption("Husband", "Your life partner", Icons.Filled.Favorite),
    RelationOption("Brother", "Your sibling", Icons.Filled.Groups),
    RelationOption("Father", "Your parent", Icons.Filled.Person),
    RelationOption("Friend", "Your bestie", Icons.Filled.Person),
    RelationOption("Other", "Someone special", Icons.Filled.AutoAwesome),
)

@Composable
private fun PartnerRelationScreen(
    selected: String,
    fieldError: String?,
    onSelected: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(SakhiSpacing.space6)) {
        Text(
            text = "Their Relation to You",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = "Who are you inviting?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = SakhiSpacing.space1, bottom = SakhiSpacing.space5),
        )

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            partnerRelationOptions.forEach { option ->
                RelationOptionCard(
                    option = option,
                    isSelected = option.title == selected,
                    onClick = { onSelected(option.title) },
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

        PrimaryButton(text = "Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RelationOptionCard(option: RelationOption, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xl),
        tonalElevation = SakhiSpacing.space1,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.selected = isSelected
                role = Role.RadioButton
                stateDescription = if (isSelected) "Selected" else "Not selected"
            }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = option.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = option.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    canGoBack: Boolean,
    fieldError: String?,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(SakhiSpacing.space6)) {
        Text(
            text = "Enter the code",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = "Ask the person caring for you to share their Sakhi invite code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = SakhiSpacing.space1, bottom = SakhiSpacing.space5),
        )

        androidx.compose.material3.OutlinedTextField(
            value = code,
            onValueChange = { code = it.take(6).uppercase() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Partner code (e.g. AB3K7R)") },
            singleLine = true,
        )

        if (!fieldError.isNullOrBlank()) {
            Text(
                text = fieldError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = SakhiSpacing.space2),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        PrimaryButton(text = "Continue", onClick = { onSubmit(code) }, modifier = Modifier.fillMaxWidth())
        if (canGoBack) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
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
    onOtpResolved: (isReturningUser: Boolean) -> Unit,
    authViewModel: AuthViewModel = koinViewModel(),
) {
    when (step) {
        OnboardingFlowStep.Phone -> PhoneScreen(
            onOtpSent = { onContinue() },
            viewModel = authViewModel,
        )
        OnboardingFlowStep.OtpVerification -> OtpScreen(
            onOtpVerified = { result ->
                onOtpResolved(result.accountState is AccountState.ExistingComplete)
            },
            viewModel = authViewModel,
        )
        else -> {}
    }
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
) {
    LaunchedEffect(Unit) { onAccept() }

    LaunchedEffect(uiState.succeeded) {
        if (uiState.succeeded) {
            kotlinx.coroutines.delay(2_200)
            onComplete()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(SakhiSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        when {
            uiState.succeeded -> {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
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
                    text = "You're connected!",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = SakhiSpacing.space5),
                )
                Text(
                    text = "You're now her Sakhi. She'll feel the difference.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = SakhiSpacing.space2),
                )
            }
            uiState.error != null -> {
                Text(
                    text = if (uiState.canRetry) "Connection issue" else "Code not valid",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = uiState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = SakhiSpacing.space2),
                )
            }
            else -> {
                CircularProgressIndicator()
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (uiState.error != null && uiState.canRetry) {
            PrimaryButton(text = "Retry", onClick = onAccept, modifier = Modifier.fillMaxWidth())
        }
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

    Column(
        modifier = Modifier.fillMaxSize().padding(SakhiSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }

        Text(
            text = "You already have an account",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SakhiSpacing.space5),
        )
        Text(
            text = "Continuing as your Sakhi's care partner will permanently delete all your personal health data. This cannot be undone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SakhiSpacing.space2),
        )

        if (uiState.error != null) {
            Text(
                text = uiState.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space3),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        PrimaryButton(
            text = if (uiState.isConverting) "Converting..." else "Continue as Partner",
            enabled = !uiState.isConverting,
            onClick = {
                hapticManager.impact(HapticImpact.MEDIUM)
                showConfirm = true
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onKeepOwnAccount, enabled = !uiState.isConverting, modifier = Modifier.fillMaxWidth()) {
            Text("Keep My Account")
        }
    }

    if (showConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete your data and continue?") },
            text = { Text("All your personal health data will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onConvert()
                }) {
                    Text("Delete & Continue as Partner", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            },
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

    Column(modifier = Modifier.fillMaxSize().padding(SakhiSpacing.space6)) {
        Text(
            text = if (uiState.importFailed) "Almost There" else "Help Sakhi Know You",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = if (uiState.importFailed) {
                "Health Connect didn't have all the details. You can add them below."
            } else {
                "Bring your health details from Health Connect and Sakhi will understand you better from day one."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = SakhiSpacing.space1, bottom = SakhiSpacing.space5),
        )

        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
            PrivacyChoiceCard(
                icon = Icons.Filled.Favorite,
                title = "Import from Health Connect",
                description = "Bring your height, weight, and health details securely from Health Connect.",
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
                title = "Add Details Myself",
                description = "Enter your details and Sakhi will start learning you from the beginning.",
                isSelected = uiState.selectedChoice == OnboardingDataSourceChoice.Manual,
                onClick = onSelectManual,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        PrimaryButton(
            text = if (uiState.isImporting) "Importing..." else "Continue",
            enabled = !uiState.isImporting,
            onClick = {
                when (uiState.selectedChoice) {
                    OnboardingDataSourceChoice.Manual -> onContinueManual()
                    OnboardingDataSourceChoice.HealthConnect -> when (uiState.availability) {
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
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (uiState.showFailureAlert) {
        AlertDialog(
            onDismissRequest = onAcknowledgeFailure,
            title = { Text("No Health Data Found") },
            text = {
                Text(
                    uiState.failureMessage
                        ?: "Health Connect doesn't have your data. Please fill in your details yourself.",
                )
            },
            confirmButton = {
                TextButton(onClick = onAcknowledgeFailure) {
                    Text("OK")
                }
            },
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

    Column(
        modifier = Modifier.fillMaxSize().padding(SakhiSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (uiState.error != null) {
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Text(
                text = uiState.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space2, bottom = SakhiSpacing.space5),
            )
            PrimaryButton(text = "Retry", onClick = onSetupLoading, modifier = Modifier.fillMaxWidth())
        } else {
            CircularProgressIndicator()
            Text(
                text = "Setting up your Sakhi...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = SakhiSpacing.space4),
            )
        }
    }
}
