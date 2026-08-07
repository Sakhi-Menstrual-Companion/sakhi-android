package team.sakhi.android.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
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
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.android.ui.ProfileSectionLabel
import team.sakhi.android.ui.SakhiAlert
import team.sakhi.android.ui.SakhiAlertTone
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.appstate.AppStateInputBridge
import team.sakhi.auth.AuthRepository
import team.sakhi.repositories.AccountRepository
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.session.SessionManager
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.common.toSafeUserMessage

private enum class ManageAccountRoute {
    Menu,
    MyData,
    Reset,
    Delete,
}

private enum class LeaveReason(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val tint: Color,
) {
    Overwhelmed(
        labelRes = R.string.profile_manage_account_leave_reason_overwhelmed,
        icon = Icons.Filled.Cloud,
        tint = Color(0xFF4B7BE5),
    ),
    Privacy(
        labelRes = R.string.profile_manage_account_leave_reason_privacy,
        icon = Icons.Filled.Lock,
        tint = Color(0xFFE16A8F),
    ),
    NotForMe(
        labelRes = R.string.profile_manage_account_leave_reason_not_for_me,
        icon = Icons.Filled.Favorite,
        tint = Color(0xFFE85D75),
    ),
    Switching(
        labelRes = R.string.profile_manage_account_leave_reason_switching,
        icon = Icons.Filled.SyncAlt,
        tint = Color(0xFF3AA17E),
    ),
    Technical(
        labelRes = R.string.profile_manage_account_leave_reason_technical,
        icon = Icons.Filled.WarningAmber,
        tint = Color(0xFFF39C48),
    ),
    Personal(
        labelRes = R.string.profile_manage_account_leave_reason_personal,
        icon = Icons.Filled.Person,
        tint = Color(0xFFB86C8B),
    ),
}

private data class ManageAccountStats(
    val logCount: Int = 0,
    val cycleCount: Int = 0,
    val careConnectionCount: Int = 0,
)

private val ManageDataMenuFootnoteSize = 11.sp
private val ManageDataMenuFootnoteLineHeight = 16.sp

/**
 * Profile manage-data parity port of iOS `ManageDataView.swift` / `MyDataView.swift`
 * / `DataResetView.swift`.
 *
 * Android now keeps the same real structure iOS uses:
 * - "Show All My Data" drills into truthful local/cloud snapshots built from the
 *   shared Room store plus the existing shared repositories.
 * - "Start fresh" signs out and clears this device's session.
 * - "Delete my account" calls `delete-user-data`, then signs out.
 *
 * The destructive behavior itself stays exactly what Android already wired earlier:
 * - "Start fresh" signs out and clears this device's session.
 * - "Delete my account" calls `delete-user-data`, then signs out.
 */
@Composable
fun ManageAccountScreen(onBack: () -> Unit) {
    val myDataViewModel: MyDataViewModel = koinViewModel()
    val authRepository = koinInject<AuthRepository>()
    val accountRepository = koinInject<AccountRepository>()
    val appStateInputBridge = koinInject<AppStateInputBridge>()
    val sessionManager = koinInject<SessionManager>()
    val periodLogRepository = koinInject<PeriodLogRepository>()
    val cycleDataRepository = koinInject<CycleDataRepository>()
    val hapticManager = koinInject<AndroidHapticManager>()
    val scope = rememberCoroutineScope()
    val session by sessionManager.session.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
        // Real bug found in this session's own critical self-review (same class as
        // MyDataViewModel's `targetUserId` bug, found while re-reading this exact
        // file for other instances of it): these stats back the Reset All Data /
        // Delete Account confirmation cards ("this will remove N logs, M cycles").
        // Both destructive actions themselves are correctly self-scoped server-side
        // (AccountRepository.deleteServerAccount()/AuthRepository.signOut() take no
        // user id, resolved from the actor's own auth JWT) -- but this preview was
        // reading `targetUserId` (whoever a partner is viewing in care mode), so a
        // partner would see the PRIMARY USER's log/cycle counts in a confirmation
        // screen for an action that actually resets/deletes the partner's OWN
        // account. A real info leak, and a correctness mismatch between what's
        // shown and what the action actually does.
        val userId = activeSession?.userId
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
            ManageAccountRoute.MyData -> route = ManageAccountRoute.Menu
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

    // System back now does exactly what the on-screen back arrow already does at
    // every step of this wizard, including unwinding the delete-account flow one
    // step at a time instead of closing the whole sheet from underneath it.
    BackHandler(onBack = ::handleBack)

    DetailSheetScaffold(
        title = when (route) {
            ManageAccountRoute.Menu -> stringResource(R.string.profile_manage_account_title)
            ManageAccountRoute.MyData -> stringResource(R.string.profile_my_data_title)
            ManageAccountRoute.Reset -> stringResource(R.string.profile_manage_account_start_fresh)
            ManageAccountRoute.Delete -> stringResource(R.string.profile_manage_account_delete_account)
        },
        subtitle = when (route) {
            ManageAccountRoute.Menu -> stringResource(R.string.profile_manage_account_header_subtitle)
            ManageAccountRoute.MyData -> stringResource(R.string.profile_my_data_subtitle)
            else -> null
        },
        headerIcon = when (route) {
            ManageAccountRoute.Menu -> Icons.Filled.Storage
            ManageAccountRoute.MyData -> Icons.Filled.Lock
            else -> null
        },
        trailingHeaderContent = {
            if (route == ManageAccountRoute.MyData) {
                MyDataHeaderRefreshAction(viewModel = myDataViewModel)
            }
        },
        onBack = ::handleBack,
        scrollable = false,
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (route) {
                ManageAccountRoute.Menu -> {
                    MenuContent(
                        onShowMyData = { route = ManageAccountRoute.MyData },
                        onStartFresh = { route = ManageAccountRoute.Reset },
                        onDeleteAccount = {
                            deleteStep = 0
                            route = ManageAccountRoute.Delete
                        },
                        error = error,
                    )
                }

                ManageAccountRoute.MyData -> {
                    MyDataRouteContent(viewModel = myDataViewModel)
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
                                    stringResource(R.string.profile_manage_account_clearing)
                                } else {
                                    stringResource(R.string.profile_manage_account_deleting)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.profile_manage_account_reset_confirm_title)) },
            text = {
                Text(stringResource(R.string.profile_manage_account_reset_confirm_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        isBusy = true
                        error = null
                        scope.launch {
                            resetProfileData(authRepository)
                                .onSuccess {
                                    appStateInputBridge.setUnauthenticated()
                                }
                                .onFailure {
                                    error = it.toSafeUserMessage(context, R.string.profile_manage_account_reset_failed)
                                }
                            isBusy = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.profile_manage_account_reset_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.profile_manage_account_keep_data))
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.profile_manage_account_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.profile_manage_account_delete_confirm_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        isBusy = true
                        error = null
                        scope.launch {
                            deleteAccountAndSignOut(accountRepository, authRepository)
                                .onSuccess {
                                    appStateInputBridge.setUnauthenticated()
                                }
                                .onFailure {
                                    error = it.toSafeUserMessage(context, R.string.profile_manage_account_delete_failed)
                                    isBusy = false
                                }
                        }
                    },
                ) {
                    Text(stringResource(R.string.profile_manage_account_delete_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.profile_manage_account_stay))
                }
            },
        )
    }
}

@Composable
private fun MenuContent(
    onShowMyData: () -> Unit,
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
            ProfileSectionLabel(text = stringResource(R.string.profile_manage_account_your_data))
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                DangerRow(
                    icon = Icons.Filled.Lock,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.profile_manage_account_menu_show_all_my_data),
                    subtitle = stringResource(R.string.profile_manage_account_menu_show_all_my_data_subtitle),
                    onClick = onShowMyData,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
            ProfileSectionLabel(text = stringResource(R.string.profile_manage_account_danger_zone))
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xl),
                tonalElevation = SakhiSpacing.space1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    DangerRow(
                        icon = Icons.Filled.Restore,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.profile_manage_account_menu_reset_all_data),
                        subtitle = stringResource(R.string.profile_manage_account_menu_reset_all_data_subtitle),
                        titleColor = MaterialTheme.colorScheme.error,
                        chevronTint = MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                        onClick = onStartFresh,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = SakhiSpacing.space4))
                    DangerRow(
                        icon = Icons.Filled.Delete,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.profile_manage_account_menu_delete_account),
                        subtitle = stringResource(R.string.profile_manage_account_menu_delete_account_subtitle),
                        titleColor = MaterialTheme.colorScheme.error,
                        chevronTint = MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                        onClick = onDeleteAccount,
                    )
                }
            }
            Text(
                text = stringResource(R.string.profile_manage_account_delete_footnote),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = ManageDataMenuFootnoteSize,
                    lineHeight = ManageDataMenuFootnoteLineHeight,
                ),
                color = sakhiSecondaryLabel(),
                modifier = Modifier.padding(
                    start = SakhiSpacing.space5,
                    top = SakhiSpacing.space1,
                    end = SakhiSpacing.space5,
                ),
            )
        }

        error?.let {
            SakhiAlert(
                title = stringResource(R.string.profile_manage_account_title),
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
                icon = Icons.Filled.Restore,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(R.string.profile_manage_account_start_fresh),
                subtitle = stringResource(R.string.profile_manage_account_reset_header_subtitle),
            )

            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                ProfileSectionLabel(text = stringResource(R.string.profile_manage_account_what_happens))
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.xl),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Each row is what `resetProfileData()` -> `authRepository.signOut()`
                    // actually does. Do not restore iOS's loss list here unless Android gains
                    // a real local store for this to clear.
                    Column {
                        LossRow(Icons.AutoMirrored.Filled.Logout, MaterialTheme.colorScheme.primary, stringResource(R.string.profile_manage_account_reset_signs_out))
                        IndentedDivider()
                        LossRow(Icons.Filled.Lock, Color(0xFF6B7CE3), stringResource(R.string.profile_manage_account_reset_pin_cleared))
                        IndentedDivider()
                        LossRow(Icons.Filled.Widgets, Color(0xFFF0A144), stringResource(R.string.profile_manage_account_reset_widget_cleared))
                        IndentedDivider()
                        LossRow(Icons.Filled.CloudDone, Color(0xFF2E9E7E), stringResource(R.string.profile_manage_account_reset_nothing_deleted))
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
                        text = stringResource(R.string.profile_manage_account_reset_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = sakhiSecondaryLabel(),
                    )
                }
            }

            error?.let {
                SakhiAlert(
                    title = stringResource(R.string.profile_manage_account_start_fresh),
                    message = it,
                    tone = SakhiAlertTone.Error,
                )
            }
        }

        SakhiFooter(
            primaryLabel = stringResource(R.string.profile_manage_account_reset_all_data),
            onPrimaryClick = onResetClick,
            note = stringResource(R.string.profile_manage_account_reset_can_log_back_in),
            showSecondarySlot = false,
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
                        icon = Icons.Filled.Favorite,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.profile_manage_account_before_you_go),
                        subtitle = stringResource(R.string.profile_manage_account_before_you_go_subtitle),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
                        BigLossCard(
                            icon = Icons.Filled.WaterDrop,
                            iconTint = Color(0xFFDD5B6A),
                            title = if (stats.logCount > 0) {
                                pluralStringResource(
                                    R.plurals.profile_manage_account_period_data_count,
                                    stats.logCount,
                                    stats.logCount,
                                )
                            } else {
                                stringResource(R.string.profile_manage_account_all_period_logs)
                            },
                            subtitle = stringResource(R.string.profile_manage_account_period_data_subtitle),
                        )
                        BigLossCard(
                            icon = Icons.Filled.MonitorHeart,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = if (stats.cycleCount > 0) {
                                pluralStringResource(
                                    R.plurals.profile_manage_account_cycle_patterns_count,
                                    stats.cycleCount,
                                    stats.cycleCount,
                                )
                            } else {
                                stringResource(R.string.profile_manage_account_cycle_history)
                            },
                            subtitle = stringResource(R.string.profile_manage_account_cycle_history_subtitle),
                        )
                        BigLossCard(
                            icon = Icons.Filled.People,
                            iconTint = Color(0xFF2E9E7E),
                            title = if (stats.careConnectionCount > 0) {
                                pluralStringResource(
                                    R.plurals.profile_manage_account_care_connections_count,
                                    stats.careConnectionCount,
                                    stats.careConnectionCount,
                                )
                            } else {
                                stringResource(R.string.profile_manage_account_care_connections)
                            },
                            subtitle = stringResource(R.string.profile_manage_account_care_connections_subtitle),
                        )
                    }
                }

                1 -> {
                    StepHeader(
                        icon = Icons.Filled.Forum,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.profile_manage_account_leave_reason_title),
                        subtitle = stringResource(R.string.profile_manage_account_leave_reason_subtitle),
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
                        icon = Icons.Filled.AutoAwesome,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.profile_manage_account_thank_you_for_trusting),
                        subtitle = stringResource(R.string.profile_manage_account_thank_you_subtitle),
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
                                    label = pluralStringResource(
                                        R.plurals.profile_manage_account_days_logged,
                                        stats.logCount,
                                        stats.logCount,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (stats.cycleCount > 0) {
                                StatPill(
                                    icon = Icons.Filled.Autorenew,
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    value = stats.cycleCount.toString(),
                                    label = pluralStringResource(
                                        R.plurals.profile_manage_account_cycles_logged,
                                        stats.cycleCount,
                                        stats.cycleCount,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                        ProfileSectionLabel(text = stringResource(R.string.profile_manage_account_what_will_be_removed))
                        Surface(
                            shape = RoundedCornerShape(SakhiRadius.xl),
                            tonalElevation = SakhiSpacing.space1,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                LossRow(Icons.Filled.Person, Color(0xFFB86C8B), stringResource(R.string.profile_manage_account_loss_account_profile))
                                IndentedDivider()
                                LossRow(Icons.Filled.WaterDrop, Color(0xFFDD5B6A), stringResource(R.string.profile_manage_account_loss_cycle_data))
                                IndentedDivider()
                                LossRow(Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.primary, stringResource(R.string.profile_manage_account_loss_learned_about_you))
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
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Text(
                                text = stringResource(R.string.profile_manage_account_delete_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            error?.let {
                SakhiAlert(
                    title = stringResource(R.string.profile_manage_account_delete_account),
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
private fun DeleteFooter(
    step: Int,
    isBusy: Boolean,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    SakhiFooter(
        primaryLabel = if (step < 2) {
            stringResource(R.string.profile_manage_account_continue)
        } else {
            stringResource(R.string.profile_manage_account_delete_confirm_button)
        },
        onPrimaryClick = onContinue,
        primaryEnabled = !isBusy,
        secondaryLabel = if (step == 1) stringResource(R.string.profile_manage_account_skip) else null,
        onSecondaryClick = if (step == 1) onSkip else null,
        secondaryEnabled = !isBusy,
        note = if (step == 2) stringResource(R.string.profile_manage_account_permanent_note) else null,
    )
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
                color = sakhiSecondaryLabel(),
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
                    color = sakhiSecondaryLabel(),
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
    val stateDescriptionText = if (selected) {
        stringResource(R.string.profile_manage_account_selected)
    } else {
        stringResource(R.string.profile_manage_account_not_selected)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                this.selected = selected
                role = Role.RadioButton
                stateDescription = stateDescriptionText
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
            text = stringResource(reason.labelRes),
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
                    color = sakhiSecondaryLabel(),
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

internal suspend fun resetProfileData(authRepository: AuthRepository): Result<Unit> {
    return authRepository.signOut()
}

internal suspend fun deleteAccountAndSignOut(
    accountRepository: AccountRepository,
    authRepository: AuthRepository,
): Result<Unit> {
    return runCatching {
        accountRepository.deleteServerAccount()
    }.fold(
        onSuccess = { authRepository.signOut() },
        onFailure = { Result.failure(it) },
    )
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
    chevronTint: Color = sakhiTertiaryLabel(),
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
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconTint.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = sakhiSecondaryLabel(),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = chevronTint,
            modifier = Modifier.size(16.dp),
        )
    }
}
