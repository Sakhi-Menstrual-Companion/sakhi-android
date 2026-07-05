package team.sakhi.android.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SakhiAlert
import team.sakhi.android.ui.SakhiAlertTone
import team.sakhi.android.ui.SheetSurface
import team.sakhi.appstate.AppStateInputBridge
import team.sakhi.auth.AuthRepository
import team.sakhi.repositories.AccountRepository
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.session.SessionManager

private enum class ManageAccountRoute {
    Menu,
    Reset,
    Delete,
}

private enum class LeaveReason(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
) {
    Overwhelmed(
        label = "I'm going through something hard right now",
        icon = Icons.Filled.Cloud,
        tint = Color(0xFF4B7BE5),
    ),
    Privacy(
        label = "I'm worried about my privacy",
        icon = Icons.Filled.Lock,
        tint = Color(0xFFE16A8F),
    ),
    NotForMe(
        label = "The app doesn't feel right for me",
        icon = Icons.Filled.Favorite,
        tint = Color(0xFFE85D75),
    ),
    Switching(
        label = "I found something that works better",
        icon = Icons.Filled.SyncAlt,
        tint = Color(0xFF3AA17E),
    ),
    Technical(
        label = "I kept running into issues",
        icon = Icons.Filled.WarningAmber,
        tint = Color(0xFFF39C48),
    ),
    Personal(
        label = "It's personal",
        icon = Icons.Filled.Person,
        tint = Color(0xFFB86C8B),
    ),
}

private data class ManageAccountStats(
    val logCount: Int = 0,
    val cycleCount: Int = 0,
    val careConnectionCount: Int = 0,
)

/**
 * Profile danger-zone parity port of iOS `DataResetView.swift`.
 *
 * Android keeps the same real destructive behavior already wired earlier:
 * - "Start fresh" signs out and clears this device's session.
 * - "Delete my account" calls `delete-user-data`, then signs out.
 *
 * This pass replaces the earlier simplified confirmation rows with the real
 * iOS flow structure: a full reset explainer screen, plus the 3-step delete
 * wizard with real counts and optional leave reasons.
 */
@Composable
fun ManageAccountScreen(onBack: () -> Unit) {
    val authRepository = koinInject<AuthRepository>()
    val accountRepository = koinInject<AccountRepository>()
    val appStateInputBridge = koinInject<AppStateInputBridge>()
    val sessionManager = koinInject<SessionManager>()
    val periodLogRepository = koinInject<PeriodLogRepository>()
    val cycleDataRepository = koinInject<CycleDataRepository>()
    val hapticManager = koinInject<AndroidHapticManager>()
    val scope = rememberCoroutineScope()
    val session by sessionManager.session.collectAsStateWithLifecycle()

    var route by remember { mutableStateOf(ManageAccountRoute.Menu) }
    var deleteStep by remember { mutableIntStateOf(0) }
    var selectedReasons by remember { mutableStateOf(setOf<LeaveReason>()) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stats by remember { mutableStateOf(ManageAccountStats()) }

    LaunchedEffect(session) {
        val activeSession = session
        val userId = activeSession?.targetUserId
        if (userId == null) {
            stats = ManageAccountStats()
            return@LaunchedEffect
        }

        val logsDeferred = async { periodLogRepository.getAll(userId).getOrDefault(emptyList()) }
        val cyclesDeferred = async { cycleDataRepository.getAll(userId).getOrDefault(emptyList()) }
        val activeCare = if (activeSession.activePartnership?.isActive == true) 1 else 0
        val pendingInvites = activeSession.pendingInvitations.size

        stats = stats.copy(
            logCount = logsDeferred.await().size,
            cycleCount = cyclesDeferred.await().size,
            careConnectionCount = activeCare + pendingInvites,
        )
    }

    fun resetDeleteWizard() {
        deleteStep = 0
        selectedReasons = emptySet()
        route = ManageAccountRoute.Menu
    }

    fun handleBack() {
        when (route) {
            ManageAccountRoute.Menu -> onBack()
            ManageAccountRoute.Reset -> route = ManageAccountRoute.Menu
            ManageAccountRoute.Delete -> {
                if (deleteStep == 0) {
                    resetDeleteWizard()
                } else {
                    deleteStep -= 1
                }
            }
        }
    }

    SheetSurface(showDragHandle = true) {
        DetailHeader(
            title = when (route) {
                ManageAccountRoute.Menu -> "Manage Account"
                ManageAccountRoute.Reset -> "Start fresh"
                ManageAccountRoute.Delete -> "Delete account"
            },
            onBack = ::handleBack,
        )

        when (route) {
            ManageAccountRoute.Menu -> {
                MenuContent(
                    onStartFresh = { route = ManageAccountRoute.Reset },
                    onDeleteAccount = {
                        deleteStep = 0
                        route = ManageAccountRoute.Delete
                    },
                    error = error,
                )
            }

            ManageAccountRoute.Reset -> {
                ResetContent(
                    onResetClick = {
                        hapticManager.impact(HapticImpact.MEDIUM)
                        showResetConfirm = true
                    },
                    error = error,
                )
            }

            ManageAccountRoute.Delete -> {
                DeleteContent(
                    step = deleteStep,
                    selectedReasons = selectedReasons,
                    stats = stats,
                    isBusy = isBusy,
                    error = error,
                    onReasonToggle = { reason ->
                        hapticManager.selection()
                        selectedReasons = if (reason in selectedReasons) {
                            selectedReasons - reason
                        } else {
                            selectedReasons + reason
                        }
                    },
                    onContinue = {
                        if (deleteStep < 2) {
                            deleteStep += 1
                        } else {
                            hapticManager.impact(HapticImpact.HEAVY)
                            showDeleteConfirm = true
                        }
                    },
                    onSkip = { deleteStep = 2 },
                )
            }
        }

        if (isBusy) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.xl),
                    tonalElevation = SakhiSpacing.space1,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = SakhiSpacing.space5,
                            vertical = SakhiSpacing.space4,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        Text(
                            text = if (route == ManageAccountRoute.Reset) {
                                "Clearing your data..."
                            } else {
                                "Deleting your account..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Start fresh?") },
            text = {
                Text("Android starts fresh by signing you out of this device. Your account stays active and you can log back in any time.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        isBusy = true
                        error = null
                        scope.launch {
                            authRepository.signOut()
                                .onSuccess {
                                    appStateInputBridge.setUnauthenticated()
                                }
                                .onFailure {
                                    error = it.message ?: "Couldn't reset. Please try again."
                                }
                            isBusy = false
                        }
                    },
                ) {
                    Text("Yes, reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Keep my data")
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete your account?") },
            text = {
                Text("This is permanent. All data will be removed within 30 days and you won't be able to recover it.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        isBusy = true
                        error = null
                        scope.launch {
                            runCatching { accountRepository.deleteServerAccount() }
                                .onSuccess {
                                    authRepository.signOut()
                                    appStateInputBridge.setUnauthenticated()
                                }
                                .onFailure {
                                    error = it.message
                                        ?: "Couldn't delete account. Please try again or contact support."
                                    isBusy = false
                                }
                        }
                    },
                ) {
                    Text("Delete my account", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Stay")
                }
            },
        )
    }
}

@Composable
private fun MenuContent(
    onStartFresh: () -> Unit,
    onDeleteAccount: () -> Unit,
    error: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
            Text(
                text = "DANGER ZONE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    DangerRow(
                        icon = Icons.Filled.WarningAmber,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Start fresh",
                        subtitle = "See what gets removed from this device before you reset",
                        onClick = onStartFresh,
                    )
                    HorizontalDivider()
                    DangerRow(
                        icon = Icons.Filled.DeleteForever,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = "Delete account",
                        subtitle = "Review what you'll lose before deleting your account",
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = onDeleteAccount,
                    )
                }
            }
        }

        error?.let {
            SakhiAlert(
                title = "Manage Account",
                message = it,
                tone = SakhiAlertTone.Error,
            )
        }
    }
}

@Composable
private fun ResetContent(
    onResetClick: () -> Unit,
    error: String?,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = SakhiSpacing.space5,
                    end = SakhiSpacing.space5,
                    top = SakhiSpacing.space5,
                    bottom = SakhiSpacing.space4,
                ),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
        ) {
            StepHeader(
                icon = Icons.Filled.WarningAmber,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Start fresh",
                subtitle = "This removes everything stored on this device. Your account stays active, log back in any time.",
            )

            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                Text(
                    text = "WHAT GETS REMOVED",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.xl),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        LossRow(Icons.Filled.WaterDrop, Color(0xFFDD5B6A), "Every period log you've added")
                        IndentedDivider()
                        LossRow(Icons.Filled.CalendarMonth, MaterialTheme.colorScheme.primary, "Your cycle history and predictions")
                        IndentedDivider()
                        LossRow(Icons.Filled.Favorite, Color(0xFF6B7CE3), "Health conditions and symptoms")
                        IndentedDivider()
                        LossRow(Icons.Filled.People, Color(0xFF2E9E7E), "Care mode and Sakhi settings")
                        IndentedDivider()
                        LossRow(Icons.Filled.AutoAwesome, Color(0xFFF0A144), "Sakhi AI conversations")
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(SakhiRadius.lg),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(SakhiSpacing.space4),
                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = "Your account and cloud data stay safe. On Android, starting fresh signs you out of this device so you can log back in cleanly any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            error?.let {
                SakhiAlert(
                    title = "Start fresh",
                    message = it,
                    tone = SakhiAlertTone.Error,
                )
            }
        }

        FooterAction(
            primaryLabel = "Reset all data",
            primaryAction = onResetClick,
            note = "This cannot be undone.",
        )
    }
}

@Composable
private fun DeleteContent(
    step: Int,
    selectedReasons: Set<LeaveReason>,
    stats: ManageAccountStats,
    isBusy: Boolean,
    error: String?,
    onReasonToggle: (LeaveReason) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = SakhiSpacing.space5,
                    end = SakhiSpacing.space5,
                    top = SakhiSpacing.space5,
                    bottom = SakhiSpacing.space4,
                ),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
        ) {
            when (step) {
                0 -> {
                    StepHeader(
                        icon = Icons.Filled.DeleteForever,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Before you go",
                        subtitle = "Deleting your account is permanent. Here's what you'll lose.",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
                        BigLossCard(
                            icon = Icons.Filled.WaterDrop,
                            iconTint = Color(0xFFDD5B6A),
                            title = if (stats.logCount > 0) {
                                "${stats.logCount} ${if (stats.logCount == 1) "day" else "days"} of period data"
                            } else {
                                "All your period logs"
                            },
                            subtitle = "Every log, flow entry, and symptom you tracked.",
                        )
                        BigLossCard(
                            icon = Icons.Filled.CalendarMonth,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = if (stats.cycleCount > 0) {
                                "${stats.cycleCount} ${if (stats.cycleCount == 1) "cycle" else "cycles"} of patterns"
                            } else {
                                "Your cycle history"
                            },
                            subtitle = "Predictions and insights built specifically for you.",
                        )
                        BigLossCard(
                            icon = Icons.Filled.People,
                            iconTint = Color(0xFF2E9E7E),
                            title = if (stats.careConnectionCount > 0) {
                                "${stats.careConnectionCount} ${if (stats.careConnectionCount == 1) "care connection" else "care connections"}"
                            } else {
                                "Your care connections"
                            },
                            subtitle = "Sakhi and care settings linked to your account.",
                        )
                    }
                }

                1 -> {
                    StepHeader(
                        icon = Icons.Filled.Info,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "What's making you leave?",
                        subtitle = "You don't have to answer. But if you do, it helps us do better.",
                    )
                    Surface(
                        shape = RoundedCornerShape(SakhiRadius.xl),
                        tonalElevation = SakhiSpacing.space1,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            LeaveReason.entries.forEachIndexed { index, reason ->
                                if (index > 0) HorizontalDivider()
                                ReasonRow(
                                    reason = reason,
                                    selected = reason in selectedReasons,
                                    onClick = { onReasonToggle(reason) },
                                )
                            }
                        }
                    }
                }

                else -> {
                    StepHeader(
                        icon = Icons.Filled.Favorite,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Thank you for trusting us",
                        subtitle = "Every day you showed up for yourself. We are glad we got to be part of that.",
                    )

                    if (stats.logCount > 0 || stats.cycleCount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                        ) {
                            if (stats.logCount > 0) {
                                StatPill(
                                    icon = Icons.Filled.WaterDrop,
                                    iconTint = Color(0xFFDD5B6A),
                                    value = stats.logCount.toString(),
                                    label = if (stats.logCount == 1) "day logged" else "days logged",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (stats.cycleCount > 0) {
                                StatPill(
                                    icon = Icons.Filled.CalendarMonth,
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    value = stats.cycleCount.toString(),
                                    label = if (stats.cycleCount == 1) "cycle" else "cycles",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                        Text(
                            text = "WHAT WILL BE REMOVED",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            shape = RoundedCornerShape(SakhiRadius.xl),
                            tonalElevation = SakhiSpacing.space1,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                LossRow(Icons.Filled.Person, Color(0xFFB86C8B), "Your account and profile")
                                IndentedDivider()
                                LossRow(Icons.Filled.WaterDrop, Color(0xFFDD5B6A), "All period logs and cycle data")
                                IndentedDivider()
                                LossRow(Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.primary, "Everything Sakhi learned about you")
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(SakhiRadius.lg),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(SakhiSpacing.space4),
                            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Text(
                                text = "Your account is scheduled for deletion. All data is permanently removed within 30 days.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            error?.let {
                SakhiAlert(
                    title = "Delete account",
                    message = it,
                    tone = SakhiAlertTone.Error,
                )
            }
        }

        DeleteFooter(
            step = step,
            isBusy = isBusy,
            onContinue = onContinue,
            onSkip = onSkip,
        )
    }
}

@Composable
private fun FooterAction(
    primaryLabel: String,
    primaryAction: () -> Unit,
    note: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SakhiSpacing.space5,
                vertical = SakhiSpacing.space4,
            ),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrimaryButton(
            text = primaryLabel,
            onClick = primaryAction,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeleteFooter(
    step: Int,
    isBusy: Boolean,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SakhiSpacing.space5,
                vertical = SakhiSpacing.space4,
            ),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrimaryButton(
            text = if (step < 2) "Continue" else "Delete my account",
            onClick = onContinue,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        when (step) {
            1 -> {
                TextButton(onClick = onSkip, enabled = !isBusy) {
                    Text("Skip")
                }
            }

            2 -> {
                Text(
                    text = "Permanent. Cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                Text(
                    text = " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Transparent,
                )
            }
        }
    }
}

@Composable
private fun StepHeader(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(52.dp),
        )
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BigLossCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xl),
        tonalElevation = SakhiSpacing.space1,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReasonRow(
    reason: LeaveReason,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.RadioButton
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .clickable(onClick = onClick)
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space4),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = reason.icon,
            contentDescription = null,
            tint = reason.tint,
        )
        Text(
            text = reason.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(
                    width = if (selected) 0.dp else 1.5.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {}
            if (selected) {
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun StatPill(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        tonalElevation = SakhiSpacing.space1,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LossRow(
    icon: ImageVector,
    iconTint: Color,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space4),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconTint.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun IndentedDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
}

@Composable
private fun DangerRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space4),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = titleColor,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.ArrowRightAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
