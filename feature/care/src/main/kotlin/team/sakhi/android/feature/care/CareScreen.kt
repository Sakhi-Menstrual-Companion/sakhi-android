package team.sakhi.android.feature.care

import android.content.ClipData
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import team.sakhi.android.ui.BackButton
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SheetSurface
import team.sakhi.care.CareRuntimeState
import team.sakhi.date.DateConverter
import team.sakhi.models.CarePartnership
import team.sakhi.models.ParentChildPermissions
import team.sakhi.models.PartnerInvitation

/**
 * "Be Her Sakhi" care hub — mirrors iOS's `CareModeSettingsView` routing: one
 * state (`CareRuntimeState`), one render, no flash. Real iOS destinations ported:
 * `PartnerDetailView` (connected), `PendingPartnerWaitingView` (pending invite),
 * `PartnerPermissionsEditView` (permission toggles). The disconnected state keeps
 * Android's existing invite-creation form since iOS launches a full onboarding-flow
 * overlay there (`OnboardingFlowView(flow: .carePartnerInvite)`) that depends on
 * account-upgrade machinery not built on Android yet — porting that whole flow is
 * out of scope for this pass; this screen's create/accept forms already call the
 * same KMM `CareStore` mutations so the underlying behaviour matches.
 */
@Composable
fun CareScreen(
    prefillInviteCode: String? = null,
    viewModel: CareViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hapticManager = koinInject<AndroidHapticManager>()
    var showPermissionsEdit by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

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

    // iOS presents this as a `.sheet(...).presentationDetents([.large])` with a
    // drag indicator over Home -- SheetSurface gives the same rounded-top +
    // drag-handle look without changing the nav-graph push mechanism itself.
    SheetSurface {
        if (showHistory && connectedPartnership != null) {
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

    val createdAtDate = DateConverter.isoToLocalDate(partnership.createdAt)
    val dateString = createdAtDate?.let { formatConnectedSince(it, context) }
    val daysOfCare = createdAtDate?.let {
        DateConverter.daysBetween(it, DateConverter.today()).coerceAtLeast(0)
    }
    val daysValue = daysOfCare?.let {
        when (it) {
            0 -> stringResource(R.string.care_today)
            1 -> stringResource(R.string.care_one_day)
            else -> pluralStringResource(R.plurals.care_days_plural, it, it)
        }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    if (daysValue != null && dateString != null) {
                        InfoRow(
                            icon = { SparkleGlyph() },
                            label = stringResource(R.string.care_label_days_of_care),
                            value = daysValue,
                        )
                        RowDivider()
                        InfoRow(
                            icon = { Text("📅") },
                            label = stringResource(R.string.care_label_connected_since),
                            value = dateString,
                        )
                    } else {
                        InfoRow(
                            icon = { Text("📅") },
                            label = stringResource(R.string.care_label_connection_details),
                            value = stringResource(R.string.care_value_unavailable_right_now),
                        )
                    }
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
                            icon = Icons.Filled.Lock,
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
                .padding(start = 34.dp, top = 44.dp)
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
    Text("✨")
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatConnectedSince(
    date: kotlinx.datetime.LocalDate,
    context: android.content.Context,
): String {
    val javaDate = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
    val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMM, yyyy", java.util.Locale.getDefault())
    return javaDate.format(formatter)
}

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Filled.Person, contentDescription = null, tint = Color.White)
        }

        Text(
            text = stringResource(R.string.care_pending_share_with_name, partnerName),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = SakhiSpacing.space6),
        )
        Text(
            text = stringResource(R.string.care_pending_waiting_for_name, partnerName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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

        PrimaryButton(
            text = stringResource(R.string.care_share),
            onClick = {
                hapticManager.impact(HapticImpact.MEDIUM)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareMessage)
                }
                context.startActivity(
                    Intent.createChooser(sendIntent, context.getString(R.string.care_share_chooser_title))
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onCancel,
            enabled = !isCancelling,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (isCancelling) {
                    stringResource(R.string.care_cancelling)
                } else {
                    stringResource(R.string.care_cancel_request)
                }
            )
        }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SakhiSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = stringResource(R.string.care_title_be_her_sakhi),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = stringResource(R.string.care_intro_optional_share),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = uiState.acceptInviteCode,
                    onValueChange = onAcceptInviteCodeChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.care_label_invite_code)) },
                    placeholder = { Text(stringResource(R.string.care_placeholder_invite_code)) },
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

        uiState.error?.let { error ->
            Text(text = error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        uiState.infoMessage?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = SakhiSpacing.space2),
                )
            }

            SectionHeader(text = stringResource(R.string.care_section_what_they_can_do))
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = SakhiSpacing.space6),
            ) {
                PermissionToggleRow(
                    title = stringResource(R.string.care_permission_log_periods),
                    checked = canLogPeriods,
                    onCheckedChange = { canLogPeriods = it },
                )
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2),
            )

            Spacer(modifier = Modifier.height(SakhiSpacing.space16))
        }

        HorizontalDivider()
        PrimaryButton(
            text = if (isSaving) stringResource(R.string.care_saving) else stringResource(R.string.care_save),
            enabled = !isSaving,
            onClick = {
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
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space4),
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
                loadError = throwable.message ?: context.getString(R.string.care_error_load_activity_right_now)
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = SakhiSpacing.space2),
                )
            }
            return
        }

        if (logs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(SakhiSpacing.space6),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = stringResource(R.string.care_empty_no_activity_yet),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.care_empty_when_name_logs,
                        partnership.partnerName.ifBlank { fallbackLabel },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        ) {
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.care_by_name,
                                    partnership.partnerName.ifBlank { fallbackLabel },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index != logs.lastIndex) RowDivider()
                    }
                }
            }
        }
    }
}
