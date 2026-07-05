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
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import team.sakhi.android.platform.AndroidNotificationReminderManager
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.SheetSurface
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

    SheetSurface(showDragHandle = true) {
        DetailHeader(title = "Reminders & Alerts", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
        ) {
            if (isPartnerRole) {
                ToggleSection(
                    label = "HER HEALTH ALERTS",
                    kvStore = kvStore,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        Triple("Her Period Starting Soon", UserPreferenceKeys.NOTIFICATION_PERIOD_REMINDER, UserPreferenceDefaults.NOTIFICATION_PERIOD_REMINDER),
                        Triple("Her Period Running Late", UserPreferenceKeys.NOTIFICATION_LATE_PERIOD, UserPreferenceDefaults.NOTIFICATION_LATE_PERIOD),
                    ),
                )
                ToggleSection(
                    label = "CARE ALERTS",
                    kvStore = kvStore,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        Triple("Sakhi Care Updates", UserPreferenceKeys.NOTIFICATION_CARE_ALERTS, UserPreferenceDefaults.NOTIFICATION_CARE_ALERTS),
                    ),
                )
                ToggleSection(
                    label = "REMINDERS",
                    kvStore = kvStore,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        Triple("Daily Check-In Reminder", UserPreferenceKeys.NOTIFICATION_PARTNER_CHECKIN, UserPreferenceDefaults.NOTIFICATION_PARTNER_CHECKIN),
                    ),
                )
            } else {
                ToggleSection(
                    label = "PERIOD ALERTS",
                    kvStore = kvStore,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        Triple("Period Starting Soon", UserPreferenceKeys.NOTIFICATION_PERIOD_REMINDER, UserPreferenceDefaults.NOTIFICATION_PERIOD_REMINDER),
                        Triple("Period Running Late", UserPreferenceKeys.NOTIFICATION_LATE_PERIOD, UserPreferenceDefaults.NOTIFICATION_LATE_PERIOD),
                        Triple("Period Ended", UserPreferenceKeys.NOTIFICATION_PERIOD_END, UserPreferenceDefaults.NOTIFICATION_PERIOD_END),
                    ),
                )
                ToggleSection(
                    label = "CYCLE & FERTILITY",
                    kvStore = kvStore,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        Triple("Ovulation Day", UserPreferenceKeys.NOTIFICATION_OVULATION_DAY, UserPreferenceDefaults.NOTIFICATION_OVULATION_DAY),
                        Triple("Fertile Window", UserPreferenceKeys.NOTIFICATION_FERTILE_WINDOW, UserPreferenceDefaults.NOTIFICATION_FERTILE_WINDOW),
                    ),
                )
                ToggleSection(
                    label = "BE HER SAKHI",
                    kvStore = kvStore,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        Triple("Care Mode Alerts", UserPreferenceKeys.NOTIFICATION_CARE_ALERTS, UserPreferenceDefaults.NOTIFICATION_CARE_ALERTS),
                    ),
                )
                ToggleSection(
                    label = "REMINDERS",
                    kvStore = kvStore,
                    onToggleChanged = ::handleToggleChanged,
                    rows = listOf(
                        Triple("Daily Log Reminder", UserPreferenceKeys.NOTIFICATION_LOGGING_REMINDER, UserPreferenceDefaults.NOTIFICATION_LOGGING_REMINDER),
                        Triple("Medicine & Supplements", UserPreferenceKeys.NOTIFICATION_MEDICINE, UserPreferenceDefaults.NOTIFICATION_MEDICINE),
                    ),
                )
            }
        }
    }
}

@Composable
private fun ToggleSection(
    label: String,
    kvStore: PlatformKeyValueStore,
    onToggleChanged: (String, Boolean) -> Unit,
    rows: List<Triple<String, String, Boolean>>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(SakhiRadius.xl),
            tonalElevation = SakhiSpacing.space1,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                rows.forEachIndexed { index, (title, key, default) ->
                    ToggleRow(
                        title = title,
                        kvStore = kvStore,
                        key = key,
                        default = default,
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
