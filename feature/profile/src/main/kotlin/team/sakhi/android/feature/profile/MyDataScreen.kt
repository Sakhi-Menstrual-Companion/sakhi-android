package team.sakhi.android.feature.profile

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiTokens
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.rememberSakhiFlingBehavior
import team.sakhi.android.ui.EmptyState
import team.sakhi.android.ui.ProfileSectionLabel
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.models.ConversationMessage
import team.sakhi.models.CycleData
import team.sakhi.models.InvitationStatus
import team.sakhi.models.PeriodLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.designsystem.sakhiGroupedBackground
import team.sakhi.android.designsystem.sakhiSystemGray5

private enum class MyDataScope(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Device(
        labelRes = R.string.profile_my_data_scope_device,
        icon = Icons.Filled.PhoneAndroid,
    ),
    Cloud(
        labelRes = R.string.profile_my_data_scope_cloud,
        icon = Icons.Filled.Cloud,
    ),
}

@Composable
internal fun MyDataHeaderRefreshAction(
    viewModel: MyDataViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isLoadingLocal || uiState.isLoadingCloud || uiState.isRefreshing) {
        CircularProgressIndicator(
            modifier = modifier
                .padding(end = SakhiSpacing.space3)
                .size(20.dp),
            strokeWidth = 2.dp,
        )
    } else {
        IconButton(
            onClick = viewModel::refresh,
            modifier = modifier,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.profile_my_data_refresh),
            )
        }
    }
}

@Composable
internal fun MyDataRouteContent(
    viewModel: MyDataViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var scope by rememberSaveable { mutableStateOf(MyDataScope.Device) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), flingBehavior = rememberSakhiFlingBehavior())
            .padding(
                start = SakhiSpacing.space5,
                end = SakhiSpacing.space5,
                top = SakhiSpacing.space5,
                bottom = SakhiSpacing.space6,
            ),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
    ) {
        ScopePicker(
            selectedScope = scope,
            onScopeSelected = { scope = it },
        )

        when (scope) {
            MyDataScope.Device -> {
                SnapshotContent(
                    isLoading = uiState.isLoadingLocal,
                    error = uiState.localError,
                    isEmpty = uiState.localSnapshot.isEmpty(),
                    emptyTitle = stringResource(R.string.profile_my_data_device_empty_title),
                    emptySubtitle = stringResource(R.string.profile_my_data_device_empty_subtitle),
                    emptyIcon = Icons.Filled.PhoneAndroid,
                ) {
                    LocalSnapshotSections(snapshot = uiState.localSnapshot, context = context)
                }
            }

            MyDataScope.Cloud -> {
                SnapshotContent(
                    isLoading = uiState.isLoadingCloud,
                    error = uiState.cloudError,
                    isEmpty = uiState.cloudSnapshot.isEmpty(),
                    // An offline account has no server row at all, so the ordinary
                    // "nothing synced yet" copy would be misleading -- it implies a
                    // sync that is pending rather than one that will never happen.
                    emptyTitle = stringResource(
                        if (uiState.isOfflineAccount) {
                            R.string.profile_my_data_cloud_offline_title
                        } else {
                            R.string.profile_my_data_cloud_empty_title
                        },
                    ),
                    emptySubtitle = stringResource(
                        if (uiState.isOfflineAccount) {
                            R.string.profile_my_data_cloud_offline_subtitle
                        } else {
                            R.string.profile_my_data_cloud_empty_subtitle
                        },
                    ),
                    emptyIcon = if (uiState.isOfflineAccount) Icons.Filled.CloudOff else Icons.Filled.Cloud,
                ) {
                    CloudSnapshotSections(snapshot = uiState.cloudSnapshot, context = context)
                }
            }
        }
    }
}

@Composable
private fun ScopePicker(
    selectedScope: MyDataScope,
    onScopeSelected: (MyDataScope) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = sakhiGroupedBackground().copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            MyDataScope.entries.forEach { scope ->
                val selected = scope == selectedScope
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onScopeSelected(scope) }
                        .padding(vertical = SakhiSpacing.space3),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = scope.icon,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            sakhiSecondaryLabel()
                        },
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(scope.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            sakhiSecondaryLabel()
                        },
                        modifier = Modifier.padding(start = SakhiSpacing.space2),
                    )
                }
            }
        }
    }
}

@Composable
private fun SnapshotContent(
    isLoading: Boolean,
    error: String?,
    isEmpty: Boolean,
    emptyTitle: String,
    emptySubtitle: String,
    emptyIcon: ImageVector,
    content: @Composable () -> Unit,
) {
    when {
        isLoading && isEmpty -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SakhiSpacing.space8),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.profile_my_data_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = sakhiSecondaryLabel(),
                    )
                }
            }
        }

        error != null && isEmpty -> {
                    EmptyState(
                title = stringResource(R.string.profile_my_data_cloud_error_title),
                subtitle = error,
                icon = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                },
            )
        }

        isEmpty -> {
            EmptyState(
                title = emptyTitle,
                subtitle = emptySubtitle,
                icon = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = emptyIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                },
            )
        }

        else -> content()
    }
}

@Composable
private fun LocalSnapshotSections(
    snapshot: MyDataLocalSnapshot,
    context: android.content.Context,
) {
    snapshot.profile?.let { profile ->
        DataGroup(title = stringResource(R.string.profile_my_data_section_profile)) {
            if (profile.name.isNotBlank()) {
                DataRow(
                    Icons.Filled.Person,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_name_label),
                    profile.name,
                )
            }
            if (profile.phone.isNotBlank()) {
                DataRow(
                    Icons.Filled.Phone,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_phone_label),
                    profile.phone,
                )
            }
            profile.dateOfBirth?.formatDateLabel()?.let { value ->
                DataRow(
                    Icons.Filled.Cake,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_date_of_birth_label),
                    value,
                )
            }
            profile.heightCm?.let {
                DataRow(
                    Icons.Filled.Height,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_height_label),
                    "${it.toInt()} cm",
                )
            }
            profile.weightKg?.let {
                DataRow(
                    Icons.Filled.Scale,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_weight_label),
                    formatWeight(it),
                )
            }
            profile.createdAt.formatDateLabel()?.let { value ->
                DataRow(
                    Icons.Filled.Schedule,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_member_since_label),
                    value,
                )
            }
            if (profile.healthConditions.isNotEmpty()) {
                DataRow(
                    Icons.Filled.Favorite,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_conditions_label),
                    profile.healthConditions.joinToString(", ") { it.displayName },
                )
            }
        }
    }

    // Every tint below is iOS `MyDataView.swift`'s, one row for one row. They were six
    // hand-typed hexes with no token behind them, and two rows were the wrong *kind* of
    // colour entirely: the invitations envelope was an amber where iOS uses
    // `categoryMessages` (blue), and cycles used the brand pink where iOS uses
    // `categoryCycles` (purple).
    if (snapshot.periodLogs.isNotEmpty()) {
        DataGroup(title = stringResource(R.string.profile_my_data_section_period_logs)) {
            snapshot.periodLogs.take(40).forEach { log ->
                DataRow(
                    icon = Icons.Filled.WaterDrop,
                    tint = if (log.periodPresent) SakhiTokens.CategoryPeriod else sakhiTertiaryLabel(),
                    label = log.logDate.formatDateLabel(),
                    value = buildLocalLogValue(log, context),
                )
            }
            OverflowRow(totalCount = snapshot.periodLogs.size, shownCount = 40)
        }
    }

    if (snapshot.cycles.isNotEmpty()) {
        DataGroup(title = stringResource(R.string.profile_my_data_section_cycles)) {
            snapshot.cycles.take(20).forEach { cycle ->
                DataRow(
                    icon = Icons.Filled.Autorenew,
                    tint = SakhiTokens.CategoryCycles,
                    label = cycle.cycleStartDate.formatDateLabel(),
                    value = buildCycleValue(cycle, context),
                )
            }
            OverflowRow(totalCount = snapshot.cycles.size, shownCount = 20)
        }
    }

    if (snapshot.partnerships.isNotEmpty()) {
        DataGroup(title = stringResource(R.string.profile_my_data_section_care_connections)) {
            snapshot.partnerships.forEach { partnership ->
                DataRow(
                    icon = Icons.Filled.People,
                    tint = SakhiTokens.CategoryCare,
                    label = partnership.partnerName.ifBlank { context.getString(R.string.profile_my_data_partner_fallback) },
                    value = partnership.status.value.replaceFirstChar(Char::titlecase),
                )
            }
        }
    }

    if (snapshot.invitations.isNotEmpty()) {
        DataGroup(title = stringResource(R.string.profile_my_data_section_invitations)) {
            snapshot.invitations.forEach { invitation ->
                DataRow(
                    icon = Icons.Filled.Email,
                    tint = SakhiTokens.CategoryMessages,
                    label = invitation.inviteCode,
                    value = invitation.status.label(),
                )
            }
        }
    }

    if (snapshot.aiMessages.isNotEmpty()) {
        DataGroup(title = stringResource(R.string.profile_my_data_section_ai)) {
            snapshot.aiMessages.take(15).forEach { message ->
                DataRow(
                    icon = if (message.isUser) Icons.Filled.Person else Icons.Filled.AutoAwesome,
                    tint = if (message.isUser) MaterialTheme.colorScheme.primary else SakhiTokens.CategoryAi,
                    label = message.timestamp.formatDateLabel()
                        ?: context.getString(R.string.profile_my_data_unknown_date),
                    value = message.content.preview(maxLength = 55),
                )
            }
            OverflowRow(totalCount = snapshot.aiMessages.size, shownCount = 15)
        }
    }

    FooterNote(text = stringResource(R.string.profile_my_data_device_footer))
}

@Composable
private fun CloudSnapshotSections(
    snapshot: MyDataCloudSnapshot,
    context: android.content.Context,
) {
    snapshot.profile?.let { profile ->
        DataGroup(title = stringResource(R.string.profile_my_data_section_profile)) {
            if (profile.name.isNotBlank()) {
                DataRow(
                    Icons.Filled.Person,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_name_label),
                    profile.name,
                )
            }
            if (profile.phone.isNotBlank()) {
                DataRow(
                    Icons.Filled.Phone,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_phone_label),
                    profile.phone,
                )
            }
            if (profile.email.isNotBlank()) {
                DataRow(
                    Icons.Filled.Email,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_email_label),
                    profile.email,
                )
            }
            profile.dateOfBirth?.formatDateLabel()?.let { value ->
                DataRow(
                    Icons.Filled.Cake,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_date_of_birth_label),
                    value,
                )
            }
            profile.heightCm?.let {
                DataRow(
                    Icons.Filled.Height,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_height_label),
                    "${it.toInt()} cm",
                )
            }
            profile.weightKg?.let {
                DataRow(
                    Icons.Filled.Scale,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_weight_label),
                    formatWeight(it),
                )
            }
            if (profile.cycleStatistics.averageCycleLength > 0) {
                DataRow(
                    Icons.Filled.Autorenew,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_average_cycle_length_label),
                    stringResource(
                        R.string.profile_my_data_days_value,
                        profile.cycleStatistics.averageCycleLength.toInt(),
                    ),
                )
            }
            profile.createdAt.formatDateLabel()?.let { value ->
                DataRow(
                    Icons.Filled.Schedule,
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.profile_my_data_member_since_label),
                    value,
                )
            }
        }
    }

    if (snapshot.periodLogs.isNotEmpty()) {
        DataGroup(title = stringResource(R.string.profile_my_data_section_period_logs)) {
            snapshot.periodLogs.take(40).forEach { log ->
                DataRow(
                    icon = Icons.Filled.WaterDrop,
                    tint = if (log.flowIntensity != null) SakhiTokens.CategoryPeriod else sakhiTertiaryLabel(),
                    label = log.logDate.formatDateLabel(),
                    value = buildCloudLogValue(log, context),
                )
            }
            OverflowRow(totalCount = snapshot.periodLogs.size, shownCount = 40)
        }
    }

    if (snapshot.cycles.isNotEmpty()) {
        DataGroup(title = stringResource(R.string.profile_my_data_section_cycles)) {
            snapshot.cycles.take(20).forEach { cycle ->
                DataRow(
                    icon = Icons.Filled.Autorenew,
                    tint = SakhiTokens.CategoryCycles,
                    label = cycle.cycleStartDate.formatDateLabel(),
                    value = buildCycleValue(cycle, context),
                )
            }
            OverflowRow(totalCount = snapshot.cycles.size, shownCount = 20)
        }
    }

    if (snapshot.aiMessages.isNotEmpty()) {
        DataGroup(title = stringResource(R.string.profile_my_data_section_ai)) {
            snapshot.aiMessages.take(30).forEach { message ->
                DataRow(
                    icon = if (message.isUser) Icons.Filled.Person else Icons.Filled.AutoAwesome,
                    tint = if (message.isUser) MaterialTheme.colorScheme.primary else SakhiTokens.CategoryAi,
                    label = message.timestamp.formatDateLabel()
                        ?: context.getString(R.string.profile_my_data_unknown_date),
                    value = message.content.preview(maxLength = 60),
                )
            }
            OverflowRow(totalCount = snapshot.aiMessages.size, shownCount = 30)
        }
    }

    if (snapshot.partnerships.isNotEmpty() || snapshot.invitations.isNotEmpty()) {
        DataGroup(title = stringResource(R.string.profile_my_data_section_care)) {
            if (snapshot.partnerships.isNotEmpty()) {
                DataRow(
                    icon = Icons.Filled.People,
                    tint = SakhiTokens.CategoryCare,
                    label = stringResource(R.string.profile_my_data_care_connections_label),
                    value = snapshot.partnerships.size.toString(),
                )
            }
            if (snapshot.invitations.isNotEmpty()) {
                DataRow(
                    icon = Icons.Filled.Email,
                    tint = SakhiTokens.CategoryMessages,
                    label = stringResource(R.string.profile_my_data_invitations_sent_label),
                    value = snapshot.invitations.size.toString(),
                )
            }
        }
    }

    FooterNote(text = stringResource(R.string.profile_my_data_cloud_footer))
}

@Composable
private fun DataGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        ProfileSectionLabel(text = title)
        Surface(
            color = sakhiSystemBackground(),
            shape = RoundedCornerShape(SakhiRadius.xl),
            tonalElevation = SakhiSpacing.space1,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun DataRow(
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space4),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = sakhiSecondaryLabel(),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
    SakhiListDivider(startInset = 68.dp)
}

@Composable
private fun OverflowRow(
    totalCount: Int,
    shownCount: Int,
) {
    val remaining = totalCount - shownCount
    if (remaining <= 0) return

    Text(
        text = stringResource(R.string.profile_my_data_more_count, remaining),
        style = MaterialTheme.typography.bodySmall,
        color = sakhiSecondaryLabel(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SakhiSpacing.space4,
                end = SakhiSpacing.space4,
                bottom = SakhiSpacing.space3,
            ),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun FooterNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
        color = sakhiSecondaryLabel(),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun MyDataLocalSnapshot.isEmpty(): Boolean {
    return profile == null &&
        periodLogs.isEmpty() &&
        cycles.isEmpty() &&
        partnerships.isEmpty() &&
        invitations.isEmpty() &&
        aiMessages.isEmpty()
}

private fun MyDataCloudSnapshot.isEmpty(): Boolean {
    return profile == null &&
        periodLogs.isEmpty() &&
        cycles.isEmpty() &&
        partnerships.isEmpty() &&
        invitations.isEmpty() &&
        aiMessages.isEmpty()
}

private fun buildLocalLogValue(
    log: PeriodLog,
    context: android.content.Context,
): String {
    val parts = buildList {
        if (log.periodPresent) add(context.getString(R.string.profile_my_data_log_period))
        log.flowIntensity?.let { add(it.displayName) }
        if (log.symptoms.isNotEmpty()) {
            add(
                context.getString(
                    R.string.profile_my_data_symptom_count,
                    log.symptoms.size,
                    if (log.symptoms.size == 1) "" else "s",
                ),
            )
        }
        if (log.moods.isNotEmpty()) {
            add(
                context.getString(
                    R.string.profile_my_data_mood_count,
                    log.moods.size,
                    if (log.moods.size == 1) "" else "s",
                ),
            )
        }
    }
    return parts.ifEmpty {
        listOf(context.getString(R.string.profile_my_data_logged))
    }.joinToString(" · ")
}

private fun buildCloudLogValue(
    log: PeriodLog,
    context: android.content.Context,
): String {
    val parts = buildList {
        log.flowIntensity?.let { add(it.displayName) }
        if (log.symptoms.isNotEmpty()) {
            add(
                context.getString(
                    R.string.profile_my_data_symptom_count,
                    log.symptoms.size,
                    if (log.symptoms.size == 1) "" else "s",
                ),
            )
        }
    }
    return parts.ifEmpty { listOf(context.getString(R.string.profile_my_data_logged)) }.joinToString(" · ")
}

private fun buildCycleValue(
    cycle: CycleData,
    context: android.content.Context,
): String {
    val parts = buildList {
        cycle.cycleLength?.let { add(context.getString(R.string.profile_my_data_days_value, it)) }
        if (cycle.isComplete) {
            add(context.getString(R.string.profile_my_data_cycle_complete))
        } else {
            add(context.getString(R.string.profile_my_data_cycle_ongoing))
        }
    }
    return parts.joinToString(" · ")
}

private fun formatWeight(weightKg: Double): String {
    return String.format(Locale.US, "%.1f kg", weightKg)
}

private fun String.preview(maxLength: Int): String {
    return if (length > maxLength) {
        take(maxLength) + "…"
    } else {
        this.ifBlank { ", " }
    }
}

private fun InvitationStatus.label(): String {
    return value.replaceFirstChar(Char::titlecase)
}

private fun String?.formatDateLabel(): String? {
    if (this.isNullOrBlank()) return null
    return runCatching {
        val instant = Instant.parse(this)
        instant.atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }.getOrElse {
        runCatching {
            LocalDate.parse(this).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }.getOrNull()
    }
}

private fun kotlinx.datetime.LocalDate.formatDateLabel(): String {
    return LocalDate.of(year, monthNumber, dayOfMonth)
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}
