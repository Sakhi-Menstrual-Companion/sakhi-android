package team.sakhi.android.feature.care

import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.feature.onboarding.OnboardingFlowHost
import team.sakhi.android.ui.BackButton
import team.sakhi.android.ui.GlassCard
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.android.ui.SheetSurface
import team.sakhi.android.ui.ToastManager
import team.sakhi.android.ui.ToastType
import team.sakhi.care.CareRuntimeState
import team.sakhi.date.DateConverter
import team.sakhi.models.CarePartnership
import team.sakhi.models.ParentChildPermissions
import team.sakhi.models.PartnerInvitation
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.common.toSafeUserMessage

/**
 * "Be Her Sakhi" care hub — mirrors iOS's `CareModeSettingsView` routing: one
 * state (`CareRuntimeState`), one render, no flash. Real iOS destinations ported:
 * `PartnerDetailView` (connected), `PendingPartnerWaitingView` (pending invite),
 * `PartnerPermissionsEditView` (permission toggles). The disconnected state keeps
 * Android now uses the same owner-side invite onboarding flow iOS launches from
 * the disconnected hub (`OnboardingFlowView(flow: .carePartnerInvite)`), while
 * still preserving the deep-link prefill accept-code path as a separate
 * disconnected route.
 */
@Composable
fun CareScreen(
    prefillInviteCode: String? = null,
    viewModel: CareViewModel = koinViewModel(),
    onClose: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticManager = koinInject<AndroidHapticManager>()
    var showPermissionsEdit by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showOwnerInviteFlow by remember(prefillInviteCode) { mutableStateOf(false) }
    var autoLaunchInviteFlow by remember(prefillInviteCode) {
        mutableStateOf(prefillInviteCode.isNullOrBlank())
    }

    // Deep-link entry (`sakhi://invite/CODE` / `https://sakhi-care.web.app/invite/CODE`
    // / `https://sakhi.com/invite/CODE`) -- pre-fills the accept-code field so the
    // user just has to confirm, matching iOS's AcceptInviteSheet fetching the invite
    // by code immediately on open rather than making the user retype it.
    LaunchedEffect(prefillInviteCode) {
        prefillInviteCode?.let(viewModel::onAcceptInviteCodeChanged)
    }
    val ownerConnected = uiState.careState as? CareRuntimeState.OwnerConnected
    val partnerConnected = uiState.careState as? CareRuntimeState.PartnerConnected
    val connectedPartnership = ownerConnected?.partnership ?: partnerConnected?.partnership
    val shouldAutoShowInviteFlow = autoLaunchInviteFlow &&
        prefillInviteCode.isNullOrBlank() &&
        uiState.careState is CareRuntimeState.Disconnected
    val showInviteFlowRoute = (showOwnerInviteFlow || shouldAutoShowInviteFlow) &&
        prefillInviteCode.isNullOrBlank() &&
        (
            uiState.careState is CareRuntimeState.Disconnected ||
                uiState.careState is CareRuntimeState.PendingInvitation ||
                uiState.careState is CareRuntimeState.OwnerConnected
            )

    LaunchedEffect(uiState.careState, autoLaunchInviteFlow) {
        if (autoLaunchInviteFlow && uiState.careState is CareRuntimeState.Disconnected) {
            showOwnerInviteFlow = true
        }
    }

    // iOS presents this as a `.sheet(...).presentationDetents([.large])` with a
    // drag indicator over Home -- SheetSurface gives the same rounded-top +
    // drag-handle look without changing the nav-graph push mechanism itself.
    SheetSurface {
        if (showInviteFlowRoute) {
            OnboardingFlowHost(
                flowId = "carePartnerInvite",
                // Modal flow: iOS shows a close button on the root step. Closes the whole
                // care sheet, matching `requestDismiss(route:)`.
                onDismiss = onClose,
                onFlowCompleted = {
                    showOwnerInviteFlow = false
                    autoLaunchInviteFlow = false
                    if (uiState.careState !is CareRuntimeState.OwnerConnected) {
                        onClose()
                    }
                },
            )
        } else if (showHistory && connectedPartnership != null) {
            PartnerHistoryContent(
                partnership = connectedPartnership,
                onBack = { showHistory = false },
            )
        } else if (showPermissionsEdit && ownerConnected != null) {
            PartnerPermissionsEditContent(
                partnership = ownerConnected.partnership,
                isSaving = uiState.isSavingPermissions,
                onBack = { showPermissionsEdit = false },
                onSave = { permissions ->
                    viewModel.updatePermissions(ownerConnected.partnership.id, permissions) { saved ->
                        if (saved) showPermissionsEdit = false
                    }
                },
            )
        } else {
            when (val state = uiState.careState) {
                CareRuntimeState.Loading -> LoadingContent()

                CareRuntimeState.Disconnected -> InviteCreationContent(
                    uiState = uiState,
                    onInviteeNameChanged = viewModel::onInviteeNameChanged,
                    onPartnerRelationChanged = viewModel::onPartnerRelationChanged,
                    onCreateInvitation = viewModel::createInvitation,
                    onAcceptInviteCodeChanged = viewModel::onAcceptInviteCodeChanged,
                    onAcceptInvitation = {
                        hapticManager.impact(HapticImpact.MEDIUM)
                        viewModel.acceptInvitation()
                    },
                )

                is CareRuntimeState.PendingInvitation -> PendingInviteContent(
                    invitation = state.invitation,
                    isCancelling = uiState.isCancellingInvite,
                    onCancel = viewModel::cancelInvitation,
                )

                is CareRuntimeState.OwnerConnected -> PartnerDetailContent(
                    partnership = state.partnership,
                    isPartnerRole = false,
                    isRemoving = uiState.isRemovingPartnership,
                    onHistory = { showHistory = true },
                    onManagePermissions = { showPermissionsEdit = true },
                    onRemove = {
                        hapticManager.impact(HapticImpact.MEDIUM)
                        viewModel.removePartnership(state.partnership.id)
                    },
                )

                is CareRuntimeState.PartnerConnected -> PartnerDetailContent(
                    partnership = state.partnership,
                    isPartnerRole = true,
                    isRemoving = uiState.isRemovingPartnership,
                    onHistory = { showHistory = true },
                    onManagePermissions = null,
                    onRemove = {
                        hapticManager.impact(HapticImpact.MEDIUM)
                        viewModel.removePartnership(state.partnership.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

// ── Connected: PartnerDetailView parity ────────────────────────────────────

@Composable
private fun PartnerDetailContent(
    partnership: CarePartnership,
    isPartnerRole: Boolean,
    isRemoving: Boolean,
    onHistory: () -> Unit,
    onManagePermissions: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    var showConfirmRemove by remember { mutableStateOf(false) }

    val resolvedName = partnership.partnerName.takeIf { name ->
        name.isNotEmpty() &&
            !name.lowercase().contains("partner") &&
            !name.lowercase().contains("sakhi") &&
            name.lowercase() != "unknown"
    }.orEmpty()
    val fallbackLabel = stringResource(R.string.care_fallback_your_sakhi)
    val displayLabel = if (resolvedName.isEmpty()) fallbackLabel else resolvedName
    val headerTitle = if (resolvedName.isEmpty()) {
        displayLabel
    } else {
        stringResource(R.string.care_header_you_and_name, resolvedName)
    }
    val partnerInitial = displayLabel.take(1).uppercase()

    val createdAtDate = partnershipStartDate(partnership)
    val dateString = formatConnectedSince(createdAtDate, context)
    val daysOfCare = DateConverter.daysBetween(createdAtDate, DateConverter.today()).coerceAtLeast(0)
    val daysValue = when (daysOfCare) {
        0 -> stringResource(R.string.care_today)
        1 -> stringResource(R.string.care_one_day)
        else -> pluralStringResource(R.plurals.care_days_plural, daysOfCare, daysOfCare)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space5),
            ) {
                AvatarPair(partnerInitial = partnerInitial)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space6)
                    .padding(bottom = SakhiSpacing.space8),
            ) {
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.care_subtitle_trusted_sakhi),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                    modifier = Modifier.padding(top = SakhiSpacing.space1),
                )
            }

            SectionHeader(text = stringResource(R.string.care_section_details))
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space6),
            ) {
                Column {
                    InfoRow(
                        icon = { SparkleGlyph() },
                        label = stringResource(R.string.care_label_days_of_care),
                        value = daysValue,
                    )
                    RowDivider()
                    InfoRow(
                        icon = { InfoSymbolIcon(Icons.Filled.CalendarToday) },
                        label = stringResource(R.string.care_label_connected_since),
                        value = dateString,
                    )
                }
            }

            SectionHeader(
                text = stringResource(R.string.care_section_actions),
                modifier = Modifier.padding(top = SakhiSpacing.space6),
            )
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space6),
            ) {
                Column {
                    ActionRow(
                        icon = Icons.Filled.History,
                        label = stringResource(R.string.care_action_history),
                        onClick = onHistory,
                    )
                    if (!isPartnerRole && onManagePermissions != null) {
                        RowDivider()
                        ActionRow(
                            icon = Icons.Filled.Shield,
                            label = stringResource(R.string.care_action_manage_permissions),
                            onClick = onManagePermissions,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(SakhiSpacing.space16))
        }

        HorizontalDivider()
        TextButton(
            onClick = { showConfirmRemove = true },
            enabled = !isRemoving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space3),
        ) {
            Text(
                text = if (isPartnerRole) {
                    stringResource(R.string.care_leave_her)
                } else {
                    stringResource(R.string.care_remove_name, displayLabel)
                },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
    }

    if (showConfirmRemove) {
        AlertDialog(
            onDismissRequest = { showConfirmRemove = false },
            title = {
                Text(
                    if (isPartnerRole) {
                        stringResource(R.string.care_leave_her_title)
                    } else {
                        stringResource(R.string.care_remove_name_title, displayLabel)
                    }
                )
            },
            text = {
                Text(
                    if (isPartnerRole) {
                        stringResource(R.string.care_leave_her_body)
                    } else {
                        stringResource(R.string.care_remove_name_body, displayLabel)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmRemove = false
                    onRemove()
                }) {
                    Text(
                        text = if (isPartnerRole) {
                            stringResource(R.string.care_confirm_leave_her)
                        } else {
                            stringResource(R.string.care_confirm_remove)
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRemove = false }) {
                    Text(stringResource(R.string.care_cancel))
                }
            },
        )
    }
}

@Composable
private fun AvatarPair(partnerInitial: String) {
    Box(modifier = Modifier.size(width = 96.dp, height = 66.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(58.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape)
                .padding(0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = partnerInitial,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(58.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(20.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(8.dp),
                )
            }
        }
    }
}

@Composable
private fun SparkleGlyph() {
    InfoSymbolIcon(Icons.Filled.AutoAwesome)
}

@Composable
private fun PartnerAvatarCloud(partnerName: String) {
    val partnerInitial = partnerName.take(1).uppercase()

    Box(
        modifier = Modifier.size(width = 196.dp, height = 144.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(116.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(116.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 13.dp, top = 13.dp)
                .size(90.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 13.dp, top = 13.dp)
                .size(90.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = partnerInitial,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-10).dp)
                .size(30.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoSymbolIcon(imageVector: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        color = sakhiSecondaryLabel(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2),
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = SakhiSpacing.space10))
}

@Composable
private fun InfoRow(icon: @Composable () -> Unit, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) { icon() }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
        )
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            // Disclosure chevron. iOS tints `chevron.right` with
            // `DS.Colors.tertiaryLabel` in 10 of its 13 uses -- it is the
            // convention, not a one-off.
            tint = sakhiTertiaryLabel(),
        )
    }
}

private fun formatConnectedSince(
    date: kotlinx.datetime.LocalDate,
    context: android.content.Context,
): String {
    val javaDate = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
    val formatter = java.time.format.DateTimeFormatter.ofPattern(
        context.getString(R.string.care_connected_since_date_format),
        java.util.Locale.getDefault(),
    )
    return javaDate.format(formatter)
}

private fun partnershipStartDate(partnership: CarePartnership): kotlinx.datetime.LocalDate =
    DateConverter.isoToLocalDate(partnership.createdAt)
        ?: DateConverter.isoToLocalDate(partnership.updatedAt)
        ?: DateConverter.today()

private fun sharePendingInvite(
    context: android.content.Context,
    shareMessage: String,
) {
    val baseIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareMessage)
    }
    val whatsAppIntent = Intent(baseIntent).apply {
        `package` = WHATSAPP_PACKAGE
    }
    val canOpenWhatsApp = runCatching {
        context.packageManager.getPackageInfo(WHATSAPP_PACKAGE, PackageManager.GET_ACTIVITIES)
    }.isSuccess

    if (canOpenWhatsApp) {
        context.startActivity(whatsAppIntent)
    } else {
        context.startActivity(
            Intent.createChooser(baseIntent, context.getString(R.string.care_share_chooser_title))
        )
    }
}

private const val WHATSAPP_PACKAGE = "com.whatsapp"

// ── Pending invitation: PendingPartnerWaitingView parity ───────────────────

@Composable
private fun PendingInviteContent(
    invitation: PartnerInvitation,
    isCancelling: Boolean,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScopeCompat()
    val hapticManager = koinInject<AndroidHapticManager>()

    val fallbackLabel = stringResource(R.string.care_fallback_your_sakhi)
    val partnerName = invitation.inviteeName?.takeIf { it.isNotBlank() } ?: fallbackLabel
    val formattedCode = if (invitation.inviteCode.length >= 6) {
        "${invitation.inviteCode.take(3)}-${invitation.inviteCode.takeLast(3)}"
    } else {
        invitation.inviteCode
    }
    val shareMessage = context.getString(R.string.care_pending_share_message, invitation.inviteCode)

    Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(SakhiSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        PartnerAvatarCloud(partnerName = partnerName)

        Text(
            text = stringResource(R.string.care_pending_share_with_name, partnerName),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = SakhiSpacing.space6),
        )
        Text(
            text = stringResource(R.string.care_pending_waiting_for_name, partnerName),
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
            modifier = Modifier.padding(top = SakhiSpacing.space2, bottom = SakhiSpacing.space6),
        )

        Surface(
            shape = RoundedCornerShape(SakhiRadius.full),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier
                .semantics {
                    contentDescription = context.getString(
                        R.string.care_pending_invite_code_description,
                        formattedCode,
                    )
                }
                .clickable {
                    scope.launch {
                        clipboard.setClipEntry(
                            androidx.compose.ui.platform.ClipEntry(
                                ClipData.newPlainText(
                                    context.getString(R.string.care_clip_label_invite_code),
                                    invitation.inviteCode,
                                )
                            )
                        )
                        hapticManager.success()
                        ToastManager.show(
                            title = context.getString(R.string.care_pending_code_copied_title),
                            message = context.getString(R.string.care_pending_code_copied_message, partnerName),
                            type = ToastType.SUCCESS,
                            durationMs = 2000L,
                        )
                    }
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            ) {
                Text(
                    text = formattedCode,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.care_copy_code),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
        SakhiFooter(
            primaryLabel = stringResource(R.string.care_share),
            onPrimaryClick = {
                hapticManager.impact(HapticImpact.MEDIUM)
                sharePendingInvite(context, shareMessage)
            },
            secondaryLabel = if (isCancelling) {
                stringResource(R.string.care_cancelling)
            } else {
                stringResource(R.string.care_cancel_request)
            },
            onSecondaryClick = onCancel,
            secondaryEnabled = !isCancelling,
        )
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()

// ── Disconnected: invite creation / accept-a-code ──────────────────────────

@Composable
private fun InviteCreationContent(
    uiState: CareUiState,
    onInviteeNameChanged: (String) -> Unit,
    onPartnerRelationChanged: (String) -> Unit,
    onCreateInvitation: () -> Unit,
    onAcceptInviteCodeChanged: (String) -> Unit,
    onAcceptInvitation: () -> Unit,
) {
    val title = stringResource(R.string.care_disconnected_title)
    val subtitle = stringResource(R.string.care_intro_optional_share)
    val feature1 = stringResource(R.string.care_disconnected_feature_1)
    val feature2 = stringResource(R.string.care_disconnected_feature_2)
    val feature3 = stringResource(R.string.care_disconnected_feature_3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SakhiSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
        )

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SakhiSpacing.space2),
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

            // Row gap comes from here, not from the row itself — iOS's `pointsSlide`
            // uses VStack(spacing: DS.Spacing.l) = 24 and the component carries no
            // padding of its own, so callers stay free to set their own rhythm.
            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space6)) {
            // iOS maps these three to heart.circle.fill / bell.badge.fill /
            // person.2.wave.2.fill. Android had VisibilityOff on the first, which reads as
            // "hidden" where iOS means "cared for", and Favorite on the third, which reads
            // as "favourite" where iOS means "people".
            CarePromptBullet(
                icon = Icons.Filled.Favorite,
                text = feature1,
                subtitle = stringResource(R.string.care_disconnected_feature_1_subtitle),
            )
            CarePromptBullet(
                icon = Icons.Filled.NotificationsActive,
                text = feature2,
                subtitle = stringResource(R.string.care_disconnected_feature_2_subtitle),
            )
            CarePromptBullet(
                icon = Icons.Filled.Groups,
                text = feature3,
                subtitle = stringResource(R.string.care_disconnected_feature_3_subtitle),
            )
            }
        }

        // Someone who arrived through an invite link is here to ACCEPT, not to
        // invite. iOS sends that person straight into `AcceptInviteSheet`; Android
        // shares one screen for both, so at minimum the accept card leads when a code
        // is already in hand. Otherwise the invited person has to scroll past a
        // full "Pick Your Person / Create invite code" form to reach the field that
        // is already filled in for them.
        val arrivedWithCode = uiState.acceptInviteCode.isNotBlank()
        val createInviteCard: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(SakhiSpacing.space5),
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                ) {
                    Text(text = stringResource(R.string.care_invite_someone_you_trust), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.care_invite_prepare_code),
                        style = MaterialTheme.typography.bodyMedium,
                        color = sakhiSecondaryLabel(),
                    )

                    OutlinedTextField(
                        value = uiState.inviteeName,
                        onValueChange = onInviteeNameChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.care_label_name_optional)) },
                        placeholder = { Text(stringResource(R.string.care_placeholder_optional)) },
                    )
                    OutlinedTextField(
                        value = uiState.partnerRelation,
                        onValueChange = onPartnerRelationChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.care_label_relationship)) },
                        placeholder = { Text(stringResource(R.string.care_placeholder_relationship)) },
                        singleLine = true,
                    )

                    PrimaryButton(
                        text = if (uiState.isCreatingInvite) {
                            stringResource(R.string.care_preparing_invite)
                        } else {
                            stringResource(R.string.care_create_invite_code)
                        },
                        onClick = onCreateInvitation,
                        enabled = !uiState.isCreatingInvite,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        val acceptCodeCard: @Composable () -> Unit = {
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(SakhiSpacing.space5),
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                ) {
                    Text(text = stringResource(R.string.care_i_have_a_code), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.care_enter_shared_code),
                        style = MaterialTheme.typography.bodyMedium,
                        color = sakhiSecondaryLabel(),
                    )
                    OutlinedTextField(
                        value = uiState.acceptInviteCode,
                        onValueChange = onAcceptInviteCodeChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.care_label_invite_code)) },
                        placeholder = { Text(stringResource(R.string.care_placeholder_invite_code)) },
                        singleLine = true,
                    )
                    PrimaryButton(
                        text = if (uiState.isAcceptingInvite) {
                            stringResource(R.string.care_joining)
                        } else {
                            stringResource(R.string.care_accept_invite)
                        },
                        onClick = onAcceptInvitation,
                        enabled = !uiState.isAcceptingInvite,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (arrivedWithCode) {
            acceptCodeCard()
            Spacer(modifier = Modifier.height(SakhiSpacing.space4))
            createInviteCard()
        } else {
            createInviteCard()
            Spacer(modifier = Modifier.height(SakhiSpacing.space4))
            acceptCodeCard()
        }

        uiState.error?.let { error ->
            Text(text = error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        uiState.infoMessage?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Port of iOS's `FeatureBulletRow` for the Care disconnected screen.
 *
 * Matches the onboarding copy of this row (`OnboardingContentStepUi`), which was brought
 * to iOS spec earlier: `HStack(alignment: .top, spacing: .m)` (16), 44pt circle, 20pt
 * glyph, 15pt bold title over a 14pt secondary subtitle. This screen renders the same
 * `care.onboarding.*` CMS copy iOS puts through `FeatureBulletRow`, so it gets the same
 * treatment rather than a title-only variant.
 */
@Composable
private fun CarePromptBullet(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
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
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                // iOS default 14pt line height (~16.8) plus its explicit lineSpacing(3).
                lineHeight = 20.sp,
                color = sakhiSecondaryLabel(),
            )
        }
    }
}

// ── Permission editing: PartnerPermissionsEditView parity ──────────────────

@Composable
private fun PartnerPermissionsEditContent(
    partnership: CarePartnership,
    isSaving: Boolean,
    onBack: () -> Unit,
    onSave: (ParentChildPermissions) -> Unit,
) {
    val initial = partnership.enhancedPermissions ?: ParentChildPermissions()
    var canLogPeriods by remember { mutableStateOf(initial.canLogPeriods) }
    // New granular permission (2026-07-15, real security fix): generating/
    // exporting a full health report exposes essentially every other
    // granular field at once, so it's its own explicit action-style grant --
    // matching `canLogPeriods` above, not folded into the "what they can
    // see" view-permission list below -- and must never default to enabled.
    var canGenerateReports by remember { mutableStateOf(initial.canGenerateReports) }
    var sharePeriodDates by remember { mutableStateOf(initial.canViewPeriodDates) }
    var shareCycleHistory by remember { mutableStateOf(initial.canViewCycleHistory) }
    var sharePredictions by remember { mutableStateOf(initial.canViewPredictions) }
    var shareSymptoms by remember { mutableStateOf(initial.canViewSymptoms) }
    var shareMoods by remember { mutableStateOf(initial.canViewMoods) }
    var shareDailyLogs by remember { mutableStateOf(initial.canViewDailyLogs) }
    var shareOvulationTests by remember { mutableStateOf(initial.canViewOvulationTests) }
    var shareMedications by remember { mutableStateOf(initial.canViewMedications) }
    var shareTemperature by remember { mutableStateOf(initial.canViewTemperature) }
    var shareWeight by remember { mutableStateOf(initial.canViewWeight) }
    var shareDischarge by remember { mutableStateOf(initial.canViewDischarge) }
    var shareNotes by remember { mutableStateOf(initial.canViewNotes) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space5),
            ) {
                Text(
                    text = stringResource(R.string.care_permissions_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = stringResource(R.string.care_permissions_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                    modifier = Modifier.padding(top = SakhiSpacing.space2),
                )
            }

            SectionHeader(text = stringResource(R.string.care_section_what_they_can_do))
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = SakhiSpacing.space6),
            ) {
                Column {
                    PermissionToggleRow(
                        title = stringResource(R.string.care_permission_log_periods),
                        checked = canLogPeriods,
                        onCheckedChange = { canLogPeriods = it },
                    )
                    RowDivider()
                    PermissionToggleRow(
                        title = stringResource(R.string.care_permission_generate_reports),
                        checked = canGenerateReports,
                        onCheckedChange = { canGenerateReports = it },
                    )
                }
            }

            SectionHeader(
                text = stringResource(R.string.care_section_what_they_can_see),
                modifier = Modifier.padding(top = SakhiSpacing.space6),
            )
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = SakhiSpacing.space6),
            ) {
                Column {
                    val rows = listOf(
                        Triple(stringResource(R.string.care_permission_period_dates), sharePeriodDates) { v: Boolean -> sharePeriodDates = v },
                        Triple(stringResource(R.string.care_permission_cycle_history), shareCycleHistory) { v: Boolean -> shareCycleHistory = v },
                        Triple(stringResource(R.string.care_permission_cycle_predictions), sharePredictions) { v: Boolean -> sharePredictions = v },
                        Triple(stringResource(R.string.care_permission_symptoms), shareSymptoms) { v: Boolean -> shareSymptoms = v },
                        Triple(stringResource(R.string.care_permission_moods), shareMoods) { v: Boolean -> shareMoods = v },
                        Triple(stringResource(R.string.care_permission_daily_health_logs), shareDailyLogs) { v: Boolean -> shareDailyLogs = v },
                        Triple(stringResource(R.string.care_permission_ovulation_tests), shareOvulationTests) { v: Boolean -> shareOvulationTests = v },
                        Triple(stringResource(R.string.care_permission_medications), shareMedications) { v: Boolean -> shareMedications = v },
                        Triple(stringResource(R.string.care_permission_body_temperature), shareTemperature) { v: Boolean -> shareTemperature = v },
                        Triple(stringResource(R.string.care_permission_weight_body), shareWeight) { v: Boolean -> shareWeight = v },
                        Triple(stringResource(R.string.care_permission_discharge), shareDischarge) { v: Boolean -> shareDischarge = v },
                        Triple(stringResource(R.string.care_permission_personal_notes), shareNotes) { v: Boolean -> shareNotes = v },
                    )
                    rows.forEachIndexed { index, (title, checked, onChange) ->
                        PermissionToggleRow(title = title, checked = checked, onCheckedChange = onChange)
                        if (index != rows.lastIndex) RowDivider()
                    }
                }
            }

            Text(
                text = stringResource(R.string.care_permission_sexual_activity_private),
                style = MaterialTheme.typography.bodySmall,
                color = sakhiSecondaryLabel(),
                modifier = Modifier.padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2),
            )

            Spacer(modifier = Modifier.height(SakhiSpacing.space16))
        }

        HorizontalDivider()
        SakhiFooter(
            primaryLabel = if (isSaving) stringResource(R.string.care_saving) else stringResource(R.string.care_save),
            primaryEnabled = !isSaving,
            onPrimaryClick = {
                onSave(
                    ParentChildPermissions(
                        canViewPeriodDates = sharePeriodDates,
                        canViewSymptoms = shareSymptoms,
                        canViewMoods = shareMoods,
                        canViewMedications = shareMedications,
                        canViewPredictions = sharePredictions,
                        canLogPeriods = canLogPeriods,
                        canViewCycleHistory = shareCycleHistory,
                        canViewDailyLogs = shareDailyLogs,
                        canViewOvulationTests = shareOvulationTests,
                        canViewTemperature = shareTemperature,
                        canViewWeight = shareWeight,
                        canViewNotes = shareNotes,
                        canViewDischarge = shareDischarge,
                        canViewSexualActivity = false,
                        canGenerateReports = canGenerateReports,
                    )
                )
            },
            showSecondarySlot = false,
        )
    }
}

@Composable
private fun PermissionToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── History: PartnerHistoryView parity ─────────────────────────────────────
// Ports iOS `PartnerDetailView.swift`'s `PartnerHistoryView`: only logs where
// `loggedBy == .partner` (the partner's own care-log actions), never the
// primary user's private symptom/mood/notes entries -- partner-authored logs
// only ever set periodPresent/flowIntensity per `PeriodLog.partnerLog(...)`,
// so that's all this timeline can ever show, matching iOS exactly.

@Composable
private fun PartnerHistoryContent(
    partnership: CarePartnership,
    onBack: () -> Unit,
    periodLogRepository: team.sakhi.repositories.PeriodLogRepository = org.koin.compose.koinInject(),
) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf<List<team.sakhi.models.PeriodLog>>(emptyList()) }
    var isLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val fallbackLabel = stringResource(R.string.care_fallback_your_sakhi)
    val connectedDate = formatConnectedSince(partnershipStartDate(partnership), context)

    androidx.compose.runtime.LaunchedEffect(partnership.userId) {
        logs = emptyList()
        loadError = null
        isLoaded = false
        periodLogRepository.getAll(partnership.userId)
            .onSuccess { all ->
                logs = all
                    .filter { it.loggedBy == team.sakhi.models.LogSource.PARTNER }
                    .sortedByDescending { it.logDate.toString() }
            }
            .onFailure { throwable ->
                loadError = throwable.toSafeUserMessage(context, R.string.care_error_load_activity_right_now)
            }
        isLoaded = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
            Text(
                text = stringResource(R.string.care_title_activity),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
        HorizontalDivider()

        if (!isLoaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (loadError != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SakhiSpacing.space6),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = sakhiSecondaryLabel(),
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = stringResource(R.string.care_error_couldnt_load_activity),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = loadError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                    modifier = Modifier.padding(top = SakhiSpacing.space2),
                )
            }
            return
        }

        if (logs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SakhiSpacing.space6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = sakhiSecondaryLabel(),
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = stringResource(R.string.care_empty_no_activity_yet),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = sakhiSecondaryLabel(),
                )
                Text(
                    text = stringResource(
                        R.string.care_empty_when_name_logs,
                        partnership.partnerName.ifBlank { fallbackLabel },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                )
                Spacer(modifier = Modifier.weight(1f))
                ConnectionBadge(connectedDate = connectedDate)
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space5),
        ) {
            SectionHeader(text = stringResource(R.string.care_section_recent_activity))
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    logs.forEachIndexed { index, log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {}
                                .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                        ) {
                            Icon(
                                imageVector = if (log.periodPresent) Icons.Filled.WaterDrop else Icons.Filled.Opacity,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(28.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (log.periodPresent) {
                                        stringResource(R.string.care_period_logged)
                                    } else {
                                        stringResource(R.string.care_period_cleared)
                                    },
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = formatConnectedSince(log.logDate, context),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sakhiSecondaryLabel(),
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.care_by_name,
                                    partnership.partnerName.ifBlank { fallbackLabel },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = sakhiSecondaryLabel(),
                            )
                        }
                        if (index != logs.lastIndex) RowDivider()
                    }
                }
            }

            SectionHeader(
                text = stringResource(R.string.care_section_connection),
                modifier = Modifier.padding(top = SakhiSpacing.space6),
            )
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                R.string.care_connected_with_name,
                                partnership.partnerName.ifBlank { fallbackLabel },
                            ),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = connectedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = sakhiSecondaryLabel(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionBadge(connectedDate: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(R.string.care_connected_badge, connectedDate),
            style = MaterialTheme.typography.labelSmall,
            color = sakhiSecondaryLabel(),
        )
    }
}
