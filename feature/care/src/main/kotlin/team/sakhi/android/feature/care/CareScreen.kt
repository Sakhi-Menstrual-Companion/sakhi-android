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
    var showConfirmRemove by remember { mutableStateOf(false) }

    val resolvedName = partnership.partnerName.takeIf { name ->
        name.isNotEmpty() &&
            !name.lowercase().contains("partner") &&
            !name.lowercase().contains("sakhi") &&
            name.lowercase() != "unknown"
    }.orEmpty()
    val headerTitle = if (resolvedName.isEmpty()) "Your Sakhi" else "You & $resolvedName"
    val displayLabel = resolvedName.ifEmpty { "Your Sakhi" }
    val partnerInitial = displayLabel.take(1).uppercase()

    val createdAtDate = DateConverter.isoToLocalDate(partnership.createdAt)
    val dateString = createdAtDate?.let { formatConnectedSince(it) }
    val daysOfCare = createdAtDate?.let {
        DateConverter.daysBetween(it, DateConverter.today()).coerceAtLeast(0)
    }
    val daysValue = daysOfCare?.let {
        when (it) {
            0 -> "Today"
            1 -> "1 day"
            else -> "$it days"
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
                    text = "Your trusted Sakhi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = SakhiSpacing.space1),
                )
            }

            SectionHeader(text = "DETAILS")
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space6),
            ) {
                Column {
                    if (daysValue != null && dateString != null) {
                        InfoRow(icon = { SparkleGlyph() }, label = "Days of care", value = daysValue)
                        RowDivider()
                        InfoRow(icon = { Text("📅") }, label = "Connected since", value = dateString)
                    } else {
                        InfoRow(
                            icon = { Text("📅") },
                            label = "Connection details",
                            value = "Unavailable right now",
                        )
                    }
                }
            }

            SectionHeader(text = "ACTIONS", modifier = Modifier.padding(top = SakhiSpacing.space6))
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
                        label = "History",
                        onClick = onHistory,
                    )
                    if (!isPartnerRole && onManagePermissions != null) {
                        RowDivider()
                        ActionRow(
                            icon = Icons.Filled.Lock,
                            label = "Manage Permissions",
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
                text = if (isPartnerRole) "Leave Her" else "Remove $displayLabel",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
    }

    if (showConfirmRemove) {
        AlertDialog(
            onDismissRequest = { showConfirmRemove = false },
            title = { Text(if (isPartnerRole) "Leave Her?" else "Remove $displayLabel?") },
            text = {
                Text(
                    if (isPartnerRole) {
                        "She trusted you with something personal. You can always reconnect if she invites you again."
                    } else {
                        "$displayLabel will no longer be able to see your data."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmRemove = false
                    onRemove()
                }) {
                    Text(
                        text = if (isPartnerRole) "Yes, Leave Her" else "Remove",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRemove = false }) {
                    Text("Cancel")
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

private fun formatConnectedSince(date: kotlinx.datetime.LocalDate): String {
    val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${date.dayOfMonth} ${months[date.monthNumber - 1]}, ${date.year}"
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

    val partnerName = invitation.inviteeName?.takeIf { it.isNotBlank() } ?: "Your Sakhi"
    val formattedCode = if (invitation.inviteCode.length >= 6) {
        "${invitation.inviteCode.take(3)}-${invitation.inviteCode.takeLast(3)}"
    } else {
        invitation.inviteCode
    }
    val shareMessage = "Hey! I use Sakhi to track my health. Open Sakhi → My Sakhi → " +
        "\"I have a code\" → enter: ${invitation.inviteCode} 💕"

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
            text = "Share with $partnerName",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = SakhiSpacing.space6),
        )
        Text(
            text = "Waiting for $partnerName to accept. Your code is ready to share.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = SakhiSpacing.space2, bottom = SakhiSpacing.space6),
        )

        Surface(
            shape = RoundedCornerShape(SakhiRadius.full),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier
                .semantics {
                    contentDescription = "Invite code $formattedCode. Double tap to copy."
                }
                .clickable {
                    scope.launch {
                        clipboard.setClipEntry(
                            androidx.compose.ui.platform.ClipEntry(
                                ClipData.newPlainText("Invite code", invitation.inviteCode)
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
                    contentDescription = "Copy code",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        PrimaryButton(
            text = "Share",
            onClick = {
                hapticManager.impact(HapticImpact.MEDIUM)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareMessage)
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onCancel,
            enabled = !isCancelling,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isCancelling) "Cancelling..." else "Cancel Request")
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
            text = "Be Her Sakhi",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = "If you would like to stay connected with someone you trust, you can share a code from here. This is always optional.",
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
                Text(text = "Invite someone you trust", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "You can prepare a code here and share it only if and when you feel comfortable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = uiState.inviteeName,
                    onValueChange = onInviteeNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name, if you want to add one") },
                    placeholder = { Text("Optional") },
                )
                OutlinedTextField(
                    value = uiState.partnerRelation,
                    onValueChange = onPartnerRelationChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Relationship") },
                    placeholder = { Text("Partner, friend, parent, or similar") },
                )

                PrimaryButton(
                    text = if (uiState.isCreatingInvite) "Preparing invite..." else "Create invite code",
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
                Text(text = "I have a code", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "If someone shared a Sakhi code with you, you can enter it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = uiState.acceptInviteCode,
                    onValueChange = onAcceptInviteCodeChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Invite code") },
                    placeholder = { Text("ABCDEF") },
                )
                PrimaryButton(
                    text = if (uiState.isAcceptingInvite) "Joining..." else "Accept invite",
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
                    text = "Share only what\nfeels right",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "You're always in control. Change this any time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = SakhiSpacing.space2),
                )
            }

            SectionHeader(text = "WHAT THEY CAN DO")
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = SakhiSpacing.space6),
            ) {
                PermissionToggleRow(title = "Log Periods", checked = canLogPeriods, onCheckedChange = { canLogPeriods = it })
            }

            SectionHeader(text = "WHAT THEY CAN SEE", modifier = Modifier.padding(top = SakhiSpacing.space6))
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xxl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = SakhiSpacing.space6),
            ) {
                Column {
                    val rows = listOf(
                        Triple("Period Dates", sharePeriodDates) { v: Boolean -> sharePeriodDates = v },
                        Triple("Cycle History", shareCycleHistory) { v: Boolean -> shareCycleHistory = v },
                        Triple("Cycle Predictions", sharePredictions) { v: Boolean -> sharePredictions = v },
                        Triple("Symptoms", shareSymptoms) { v: Boolean -> shareSymptoms = v },
                        Triple("Moods", shareMoods) { v: Boolean -> shareMoods = v },
                        Triple("Daily Health Logs", shareDailyLogs) { v: Boolean -> shareDailyLogs = v },
                        Triple("Ovulation Tests", shareOvulationTests) { v: Boolean -> shareOvulationTests = v },
                        Triple("Medications", shareMedications) { v: Boolean -> shareMedications = v },
                        Triple("Body Temperature", shareTemperature) { v: Boolean -> shareTemperature = v },
                        Triple("Weight & Body", shareWeight) { v: Boolean -> shareWeight = v },
                        Triple("Discharge", shareDischarge) { v: Boolean -> shareDischarge = v },
                        Triple("Personal Notes", shareNotes) { v: Boolean -> shareNotes = v },
                    )
                    rows.forEachIndexed { index, (title, checked, onChange) ->
                        PermissionToggleRow(title = title, checked = checked, onCheckedChange = onChange)
                        if (index != rows.lastIndex) RowDivider()
                    }
                }
            }

            Text(
                text = "Sexual activity is always kept private and cannot be shared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2),
            )

            Spacer(modifier = Modifier.height(SakhiSpacing.space16))
        }

        HorizontalDivider()
        PrimaryButton(
            text = if (isSaving) "Saving..." else "Save",
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
    var logs by remember { mutableStateOf<List<team.sakhi.models.PeriodLog>>(emptyList()) }
    var isLoaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

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
                loadError = throwable.message ?: "Couldn't load activity right now."
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
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Activity",
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
                    text = "Couldn't load activity",
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
                    text = "No activity yet",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "When ${partnership.partnerName} logs something for you, it will show up here.",
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
            SectionHeader(text = "RECENT ACTIVITY")
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
                                    text = if (log.periodPresent) "Period logged" else "Period cleared",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = log.logDate.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "by ${partnership.partnerName}",
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
