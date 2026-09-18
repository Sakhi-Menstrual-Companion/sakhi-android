package team.sakhi.android.feature.profile

import android.Manifest
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import team.sakhi.android.platform.AndroidNotificationReminderManager
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.android.ui.ProfileSectionLabel
import team.sakhi.platform.PlatformKeyValueStore
import team.sakhi.preferences.UserPreferenceDefaults
import team.sakhi.preferences.UserPreferenceKeys
import team.sakhi.session.SessionManager
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.android.ui.SakhiSwitch

/**
 * Ports iOS `NotificationsSettingsView.swift`'s toggle rows, persisted through
 * the same shared `UserPreferenceKeys` KMM already defines.
 *
 * Real local scheduling now exists only for the reminder types that have a live
 * shared/iOS timing rule today via `NotificationScheduleBuilder`: period-start,
 * fertile-window, ovulation-day, and daily-log reminders. Other persisted toggles
 * in this screen (`late period`, `period ended`, `care alerts`, `medicine`,
 * `partner check-in`) still have settings copy but no real trigger-definition in
 * iOS or KMM yet, so Android keeps them as preferences only instead of inventing
 * local scheduling rules.
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val kvStore = koinInject<PlatformKeyValueStore>()
    val sessionManager = koinInject<SessionManager>()
    val reminderManager = koinInject<AndroidNotificationReminderManager>()
    val context = LocalContext.current
    val currentSession by sessionManager.session.collectAsStateWithLifecycle()
    val isPartnerRole = currentSession?.isViewingOwnData == false
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        reminderManager.requestImmediateRefresh()
    }

    fun handleToggleChanged(key: String, value: Boolean) {
        if (reminderManager.isBuilderBackedLocalReminder(key)) {
            if (
                value &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            reminderManager.requestImmediateRefresh()
        }
    }

    DetailSheetScaffold(
        title = stringResource(R.string.profile_notifications_title),
        subtitle = stringResource(
            if (isPartnerRole) {
                R.string.profile_notifications_header_subtitle_partner
            } else {
                R.string.profile_notifications_header_subtitle_self
            },
        ),
        headerIcon = Icons.Filled.NotificationsActive,
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5)) {
            if (isPartnerRole) {
                ToggleSection(
                    labelRes = R.string.profile_notifications_section_her_health_alerts,
                    kvStore = kvStore,
                    isViewingOwnData = !isPartnerRole,
                    onToggleChanged = ::handleToggleChanged,
                    footerRes = R.string.profile_notifications_note_her_health_alerts,
                    rows = listOf(
                        ToggleOption(
                            titleRes = R.string.profile_notifications_her_period_soon,
                            subtitleRes = R.string.profile_notifications_her_period_soon_subtitle,
                            icon = Icons.Filled.WaterDrop,
                            key = UserPreferenceKeys.NOTIFICATION_PERIOD_REMINDER,
                            default = UserPreferenceDefaults.NOTIFICATION_PERIOD_REMINDER,
                        ),
                        ToggleOption(
                            titleRes = R.string.profile_notifications_her_period_late,
                            subtitleRes = R.string.profile_notifications_her_period_late_subtitle,
                            icon = Icons.Filled.Error,
                            key = UserPreferenceKeys.NOTIFICATION_LATE_PERIOD,
                            default = UserPreferenceDefaults.NOTIFICATION_LATE_PERIOD,
                        ),
                    ),
                )
                ToggleSection(
                    labelRes = R.string.profile_notifications_section_care_alerts,
                    kvStore = kvStore,
                    isViewingOwnData = !isPartnerRole,
                    onToggleChanged = ::handleToggleChanged,
                    footerRes = R.string.profile_notifications_note_care_alerts,
                    rows = listOf(
                        ToggleOption(
                            titleRes = R.string.profile_notifications_care_updates,
                            subtitleRes = R.string.profile_notifications_care_updates_subtitle,
                            icon = Icons.Filled.Favorite,
                            key = UserPreferenceKeys.NOTIFICATION_CARE_ALERTS,
                            default = UserPreferenceDefaults.NOTIFICATION_CARE_ALERTS,
                        ),
                    ),
                )
                ToggleSection(
                    labelRes = R.string.profile_notifications_section_reminders,
                    kvStore = kvStore,
                    isViewingOwnData = !isPartnerRole,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        ToggleOption(
                            titleRes = R.string.profile_notifications_daily_checkin,
                            subtitleRes = R.string.profile_notifications_daily_checkin_subtitle,
                            icon = Icons.Filled.CalendarMonth,
                            key = UserPreferenceKeys.NOTIFICATION_PARTNER_CHECKIN,
                            default = UserPreferenceDefaults.NOTIFICATION_PARTNER_CHECKIN,
                        ),
                    ),
                )
            } else {
                ToggleSection(
                    labelRes = R.string.profile_notifications_section_period_alerts,
                    kvStore = kvStore,
                    isViewingOwnData = !isPartnerRole,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        ToggleOption(
                            titleRes = R.string.profile_notifications_period_soon,
                            subtitleRes = R.string.profile_notifications_period_soon_subtitle,
                            icon = Icons.Filled.WaterDrop,
                            key = UserPreferenceKeys.NOTIFICATION_PERIOD_REMINDER,
                            default = UserPreferenceDefaults.NOTIFICATION_PERIOD_REMINDER,
                        ),
                        ToggleOption(
                            titleRes = R.string.profile_notifications_period_late,
                            subtitleRes = R.string.profile_notifications_period_late_subtitle,
                            icon = Icons.Filled.Error,
                            key = UserPreferenceKeys.NOTIFICATION_LATE_PERIOD,
                            default = UserPreferenceDefaults.NOTIFICATION_LATE_PERIOD,
                        ),
                        ToggleOption(
                            titleRes = R.string.profile_notifications_period_end,
                            subtitleRes = R.string.profile_notifications_period_end_subtitle,
                            icon = Icons.Filled.CheckCircle,
                            key = UserPreferenceKeys.NOTIFICATION_PERIOD_END,
                            default = UserPreferenceDefaults.NOTIFICATION_PERIOD_END,
                        ),
                    ),
                )
                ToggleSection(
                    labelRes = R.string.profile_notifications_section_cycle_fertility,
                    kvStore = kvStore,
                    isViewingOwnData = !isPartnerRole,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        ToggleOption(
                            titleRes = R.string.profile_notifications_ovulation_day,
                            subtitleRes = R.string.profile_notifications_ovulation_day_subtitle,
                            icon = Icons.Filled.AutoAwesome,
                            key = UserPreferenceKeys.NOTIFICATION_OVULATION_DAY,
                            default = UserPreferenceDefaults.NOTIFICATION_OVULATION_DAY,
                        ),
                        ToggleOption(
                            titleRes = R.string.profile_notifications_fertile_window,
                            subtitleRes = R.string.profile_notifications_fertile_window_subtitle,
                            icon = Icons.Filled.Eco,
                            key = UserPreferenceKeys.NOTIFICATION_FERTILE_WINDOW,
                            default = UserPreferenceDefaults.NOTIFICATION_FERTILE_WINDOW,
                        ),
                    ),
                )
                ToggleSection(
                    labelRes = R.string.profile_notifications_section_be_her_sakhi,
                    kvStore = kvStore,
                    isViewingOwnData = !isPartnerRole,
                    onToggleChanged = ::handleToggleChanged,
                    footerRes = R.string.profile_notifications_note_be_her_sakhi,
                    rows = listOf(
                        ToggleOption(
                            titleRes = R.string.profile_notifications_care_mode_alerts,
                            subtitleRes = R.string.profile_notifications_care_mode_alerts_subtitle,
                            icon = Icons.Filled.Favorite,
                            key = UserPreferenceKeys.NOTIFICATION_CARE_ALERTS,
                            default = UserPreferenceDefaults.NOTIFICATION_CARE_ALERTS,
                        ),
                    ),
                )
                ToggleSection(
                    labelRes = R.string.profile_notifications_section_reminders,
                    kvStore = kvStore,
                    isViewingOwnData = !isPartnerRole,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        ToggleOption(
                            titleRes = R.string.profile_notifications_daily_log,
                            subtitleRes = R.string.profile_notifications_daily_log_subtitle,
                            icon = Icons.Filled.CalendarMonth,
                            key = UserPreferenceKeys.NOTIFICATION_LOGGING_REMINDER,
                            default = UserPreferenceDefaults.NOTIFICATION_LOGGING_REMINDER,
                        ),
                        ToggleOption(
                            titleRes = R.string.profile_notifications_medicine,
                            subtitleRes = R.string.profile_notifications_medicine_subtitle,
                            icon = Icons.Filled.Medication,
                            key = UserPreferenceKeys.NOTIFICATION_MEDICINE,
                            default = UserPreferenceDefaults.NOTIFICATION_MEDICINE,
                        ),
                    ),
                )
            }
            NotificationSettingsCard(
                title = stringResource(R.string.profile_notifications_settings_title),
                subtitle = stringResource(R.string.profile_notifications_settings_subtitle),
            )
        }
    }
}

private data class ToggleOption(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val key: String,
    val default: Boolean,
)

private val NotificationToggleDividerInset = SakhiSpacing.space4 + 24.dp + SakhiSpacing.space3
private val NotificationRowIconSize = 15.dp
private val NotificationRowTitleSize = 16.sp
private val NotificationRowSubtitleSize = 12.sp
private val NotificationSettingsTrailingIconSize = 13.dp

@Composable
private fun ToggleSection(
    @StringRes labelRes: Int,
    kvStore: PlatformKeyValueStore,
    /**
     * Which mode these toggles belong to.
     *
     * Both branches of this screen list the same keys: "Her period is coming" for a care
     * partner and "Your period is coming" for her own reads and writes
     * `notif_period_reminder` either way. Turning one off turned the other off. The read
     * side already knew about the mode (`resolvePreferences` takes `isViewingOwnData`);
     * only the storage was blind to it.
     */
    isViewingOwnData: Boolean,
    onToggleChanged: (String, Boolean) -> Unit,
    rows: List<ToggleOption>,
    @StringRes footerRes: Int? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        ProfileSectionLabel(text = stringResource(labelRes))
        Surface(
            color = sakhiSystemBackground(),
            shape = RoundedCornerShape(SakhiRadius.xl),
            tonalElevation = SakhiSpacing.space1,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    ToggleRow(
                        title = stringResource(row.titleRes),
                        subtitle = stringResource(row.subtitleRes),
                        icon = row.icon,
                        kvStore = kvStore,
                        key = UserPreferenceKeys.notificationKey(row.key, isViewingOwnData),
                        default = row.default,
                        onToggleChanged = onToggleChanged,
                    )
                    if (index != rows.lastIndex) {
                        SakhiListDivider(startInset = NotificationToggleDividerInset)
                    }
                }
            }
        }
        footerRes?.let { note ->
            Text(
                text = stringResource(note),
                style = MaterialTheme.typography.bodySmall,
                color = sakhiSecondaryLabel(),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    kvStore: PlatformKeyValueStore,
    key: String,
    default: Boolean,
    onToggleChanged: (String, Boolean) -> Unit,
) {
    var checked by remember(key, default) { mutableStateOf(kvStore.getBool(key, default)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .width(NotificationRowIconSize)
                .height(NotificationRowIconSize),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = NotificationRowTitleSize),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = NotificationRowSubtitleSize),
                color = sakhiSecondaryLabel(),
            )
        }
        SakhiSwitch(
            checked = checked,
            onCheckedChange = { value ->
                checked = value
                kvStore.setBool(key, value)
                onToggleChanged(key, value)
            },
        )
    }
}

@Composable
private fun NotificationSettingsCard(
    title: String,
    subtitle: String,
) {
    val context = LocalContext.current
    Surface(
        color = sakhiSystemBackground(),
        shape = RoundedCornerShape(SakhiRadius.xl),
        tonalElevation = SakhiSpacing.space1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                    }
                    runCatching { context.startActivity(intent) }
                        .recoverCatching { context.startActivity(fallbackIntent) }
                }
                .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .width(NotificationRowIconSize)
                    .height(NotificationRowIconSize),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = NotificationRowTitleSize),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = NotificationRowSubtitleSize),
                    color = sakhiSecondaryLabel(),
                )
            }
            Icon(
                imageVector = Icons.Filled.ArrowOutward,
                contentDescription = null,
                tint = sakhiTertiaryLabel(),
                modifier = Modifier
                    .width(NotificationSettingsTrailingIconSize)
                    .height(NotificationSettingsTrailingIconSize),
            )
        }
    }
}
