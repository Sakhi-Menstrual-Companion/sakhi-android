package team.sakhi.android.feature.profile

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import team.sakhi.android.platform.AndroidNotificationReminderManager
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.platform.PlatformKeyValueStore
import team.sakhi.preferences.UserPreferenceDefaults
import team.sakhi.preferences.UserPreferenceKeys
import team.sakhi.session.SessionManager

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

    DetailSheetScaffold(title = stringResource(R.string.profile_notifications_title), onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5)) {
            if (isPartnerRole) {
                ToggleSection(
                    labelRes = R.string.profile_notifications_section_her_health_alerts,
                    kvStore = kvStore,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        ToggleOption(R.string.profile_notifications_her_period_soon, UserPreferenceKeys.NOTIFICATION_PERIOD_REMINDER, UserPreferenceDefaults.NOTIFICATION_PERIOD_REMINDER),
                        ToggleOption(R.string.profile_notifications_her_period_late, UserPreferenceKeys.NOTIFICATION_LATE_PERIOD, UserPreferenceDefaults.NOTIFICATION_LATE_PERIOD),
                    ),
                )
                ToggleSection(
                    labelRes = R.string.profile_notifications_section_care_alerts,
                    kvStore = kvStore,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        ToggleOption(R.string.profile_notifications_care_updates, UserPreferenceKeys.NOTIFICATION_CARE_ALERTS, UserPreferenceDefaults.NOTIFICATION_CARE_ALERTS),
                    ),
                )
                ToggleSection(
                    labelRes = R.string.profile_notifications_section_reminders,
                    kvStore = kvStore,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        ToggleOption(R.string.profile_notifications_daily_checkin, UserPreferenceKeys.NOTIFICATION_PARTNER_CHECKIN, UserPreferenceDefaults.NOTIFICATION_PARTNER_CHECKIN),
                    ),
                )
            } else {
                ToggleSection(
                    labelRes = R.string.profile_notifications_section_period_alerts,
                    kvStore = kvStore,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        ToggleOption(R.string.profile_notifications_period_soon, UserPreferenceKeys.NOTIFICATION_PERIOD_REMINDER, UserPreferenceDefaults.NOTIFICATION_PERIOD_REMINDER),
                        ToggleOption(R.string.profile_notifications_period_late, UserPreferenceKeys.NOTIFICATION_LATE_PERIOD, UserPreferenceDefaults.NOTIFICATION_LATE_PERIOD),
                        ToggleOption(R.string.profile_notifications_period_end, UserPreferenceKeys.NOTIFICATION_PERIOD_END, UserPreferenceDefaults.NOTIFICATION_PERIOD_END),
                    ),
                )
                ToggleSection(
                    labelRes = R.string.profile_notifications_section_cycle_fertility,
                    kvStore = kvStore,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        ToggleOption(R.string.profile_notifications_ovulation_day, UserPreferenceKeys.NOTIFICATION_OVULATION_DAY, UserPreferenceDefaults.NOTIFICATION_OVULATION_DAY),
                        ToggleOption(R.string.profile_notifications_fertile_window, UserPreferenceKeys.NOTIFICATION_FERTILE_WINDOW, UserPreferenceDefaults.NOTIFICATION_FERTILE_WINDOW),
                    ),
                )
                ToggleSection(
                    labelRes = R.string.profile_notifications_section_be_her_sakhi,
                    kvStore = kvStore,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        ToggleOption(R.string.profile_notifications_care_mode_alerts, UserPreferenceKeys.NOTIFICATION_CARE_ALERTS, UserPreferenceDefaults.NOTIFICATION_CARE_ALERTS),
                    ),
                )
                ToggleSection(
                    labelRes = R.string.profile_notifications_section_reminders,
                    kvStore = kvStore,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        ToggleOption(R.string.profile_notifications_daily_log, UserPreferenceKeys.NOTIFICATION_LOGGING_REMINDER, UserPreferenceDefaults.NOTIFICATION_LOGGING_REMINDER),
                        ToggleOption(R.string.profile_notifications_medicine, UserPreferenceKeys.NOTIFICATION_MEDICINE, UserPreferenceDefaults.NOTIFICATION_MEDICINE),
                    ),
                )
            }
        }
    }
}

private data class ToggleOption(
    @StringRes val titleRes: Int,
    val key: String,
    val default: Boolean,
)

@Composable
private fun ToggleSection(
    @StringRes labelRes: Int,
    kvStore: PlatformKeyValueStore,
    onToggleChanged: (String, Boolean) -> Unit,
    rows: List<ToggleOption>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(SakhiRadius.xl),
            tonalElevation = SakhiSpacing.space1,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    ToggleRow(
                        title = stringResource(row.titleRes),
                        kvStore = kvStore,
                        key = row.key,
                        default = row.default,
                        onToggleChanged = onToggleChanged,
                    )
                    if (index != rows.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { value ->
                checked = value
                kvStore.setBool(key, value)
                onToggleChanged(key, value)
            },
        )
    }
}
