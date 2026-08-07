package team.sakhi.android.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.AndroidHealthConnectManager
import team.sakhi.android.platform.DailyHealthValue
import team.sakhi.android.platform.HealthConnectAvailability
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.ui.GlassCard
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.ProfileSectionLabel
import team.sakhi.android.ui.SakhiAlert
import team.sakhi.android.ui.SakhiAlertTone
import team.sakhi.android.ui.SecondaryButton
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel

private val AppIntegrationInsightDividerInset = 22.dp + SakhiSpacing.space3
private val AppIntegrationAppsRowBadgeSize = 36.dp
private val AppIntegrationAppsRowIconSize = 16.dp
private val AppIntegrationAppsRowTitleSize = 15.sp
private val AppIntegrationAppsRowSubtitleSize = 11.sp
private val AppIntegrationAppsRowSpinnerSize = 18.dp

/**
 * Real Android Health Connect bridge for Profile's App Integration card.
 *
 * Mirrors iOS's intent: platform code owns permission / provider / import work,
 * while the screen only drives that adapter and renders the imported local
 * insights. Period-day imports reuse the shared `PeriodLog` path so Calendar/Home
 * can actually see them; sleep/steps/temperature stay in the shared local
 * health-sample store and are rendered here.
 */
@Composable
fun AppIntegrationScreen(
    onBack: () -> Unit,
    viewModel: AppIntegrationViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val healthConnectManager = koinInject<AndroidHealthConnectManager>()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
        onResult = viewModel::onPermissionsResult,
    )

    DetailSheetScaffold(
        title = stringResource(R.string.profile_app_integration_title),
        subtitle = stringResource(R.string.profile_app_integration_intro),
        headerIcon = Icons.Filled.MonitorHeart,
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5)) {
            if (!uiState.error.isNullOrBlank()) {
                SakhiAlert(
                    title = stringResource(R.string.profile_app_integration_health_connect),
                    message = uiState.error.orEmpty(),
                    tone = SakhiAlertTone.Error,
                    onDismiss = viewModel::clearError,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                ProfileSectionLabel(text = stringResource(R.string.profile_app_integration_label_apps))
                HealthConnectCard(
                    uiState = uiState,
                    onInstall = {
                        runCatching { context.startActivity(healthConnectManager.installIntent()) }
                            .onFailure {
                                viewModel.clearError()
                            }
                    },
                    onConnect = {
                        if (uiState.hasPermissions) {
                            viewModel.syncNow()
                        } else {
                            permissionLauncher.launch(healthConnectManager.requiredPermissions)
                        }
                    },
                    onSync = viewModel::syncNow,
                    onDisconnect = viewModel::disconnect,
                )
            }

            if (shouldShowHealthConnectDisabledNote(uiState)) {
                Text(
                    text = stringResource(R.string.profile_app_integration_disabled_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = sakhiSecondaryLabel(),
                )
            }

            if (uiState.isEnabled && uiState.sleepEntries.isNotEmpty()) {
                InsightCard(
                    label = stringResource(R.string.profile_app_integration_label_sleep),
                    title = stringResource(R.string.profile_app_integration_title_avg_sleep),
                    value = stringResource(R.string.profile_app_integration_value_hours, average(uiState.sleepEntries)),
                    summaryIcon = Icons.Filled.Hotel,
                    summaryIconTint = Color(0xFF5C6BC0),
                    rows = uiState.sleepEntries,
                    formatter = { value -> context.getString(R.string.profile_app_integration_value_hours, value) },
                )
            }
            if (uiState.isEnabled && uiState.stepEntries.isNotEmpty()) {
                InsightCard(
                    label = stringResource(R.string.profile_app_integration_label_activity),
                    title = stringResource(R.string.profile_app_integration_title_avg_steps),
                    value = average(uiState.stepEntries).toInt().toString(),
                    summaryIcon = Icons.AutoMirrored.Filled.DirectionsWalk,
                    summaryIconTint = Color(0xFF2E9E7E),
                    rows = uiState.stepEntries,
                    formatter = { it.toInt().toString() },
                )
            }
            if (uiState.isEnabled && uiState.temperatureEntries.isNotEmpty()) {
                TemperatureInsightCard(
                    label = stringResource(R.string.profile_app_integration_label_temperature),
                    rows = uiState.temperatureEntries,
                    formatter = { value -> context.getString(R.string.profile_app_integration_value_temperature, value) },
                )
            }
        }
    }
}

@Composable
private fun HealthConnectCard(
    uiState: AppIntegrationUiState,
    onInstall: () -> Unit,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val hapticManager = koinInject<AndroidHapticManager>()
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                modifier = Modifier.size(AppIntegrationAppsRowBadgeSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(AppIntegrationAppsRowIconSize),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.profile_app_integration_health_connect),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = AppIntegrationAppsRowTitleSize,
                    ),
                )
                Text(
                    text = healthConnectSecondaryText(uiState),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = AppIntegrationAppsRowSubtitleSize),
                    color = sakhiSecondaryLabel(),
                )
            }
            if (uiState.isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AppIntegrationAppsRowSpinnerSize),
                    strokeWidth = 2.dp,
                )
            }
        }

        uiState.latestSync?.let { latest ->
            Text(
                text = stringResource(
                    R.string.profile_app_integration_imported_summary,
                    latest.importedPeriodLogs,
                    latest.importedSleepSamples,
                    latest.importedStepSamples,
                    latest.importedTemperatureSamples,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = sakhiSecondaryLabel(),
                modifier = Modifier.padding(top = SakhiSpacing.space2),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SakhiSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            when {
                uiState.session?.isViewingOwnData == false -> {
                    Text(
                        text = stringResource(R.string.profile_app_integration_own_data_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = sakhiSecondaryLabel(),
                    )
                }
                uiState.availability == HealthConnectAvailability.NotInstalled -> {
                    PrimaryButton(
                        text = stringResource(R.string.profile_app_integration_install),
                        onClick = onInstall,
                        modifier = Modifier.weight(1f),
                    )
                }
                uiState.availability == HealthConnectAvailability.NotSupported -> {
                    Text(
                        text = stringResource(R.string.profile_app_integration_not_available),
                        style = MaterialTheme.typography.bodySmall,
                        color = sakhiSecondaryLabel(),
                    )
                }
                uiState.isEnabled -> {
                    SecondaryButton(
                        text = stringResource(R.string.profile_app_integration_disconnect),
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSyncing,
                    )
                    PrimaryButton(
                        text = stringResource(R.string.profile_app_integration_sync_now),
                        onClick = {
                            hapticManager.impact(HapticImpact.LIGHT)
                            onSync()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSyncing,
                    )
                }
                else -> {
                    PrimaryButton(
                        text = stringResource(R.string.profile_app_integration_connect),
                        onClick = {
                            hapticManager.impact(HapticImpact.LIGHT)
                            onConnect()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isLoading && !uiState.isSyncing,
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightCard(
    label: String,
    title: String,
    value: String,
    summaryIcon: ImageVector,
    summaryIconTint: Color,
    rows: List<DailyHealthValue>,
    formatter: (Double) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        ProfileSectionLabel(text = label)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SakhiSpacing.space2)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = summaryIcon,
                    contentDescription = null,
                    tint = summaryIconTint,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            rows.forEach { row ->
                HorizontalDivider(modifier = Modifier.padding(start = AppIntegrationInsightDividerInset))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .padding(vertical = SakhiSpacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.date.toDisplayLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = sakhiSecondaryLabel(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatter(row.value),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun TemperatureInsightCard(
    label: String,
    rows: List<DailyHealthValue>,
    formatter: (Double) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        ProfileSectionLabel(text = label)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(start = AppIntegrationInsightDividerInset))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .padding(vertical = SakhiSpacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.date.toDisplayLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = sakhiSecondaryLabel(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatter(row.value),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

private fun average(values: List<DailyHealthValue>): Double {
    if (values.isEmpty()) return 0.0
    return values.sumOf(DailyHealthValue::value) / values.size
}

@Composable
private fun kotlinx.datetime.LocalDate.toDisplayLabel(): String {
    val pattern = stringResource(R.string.profile_app_integration_day_label_format)
    val formatter = remember(pattern) {
        DateTimeFormatter.ofPattern(
            pattern,
            Locale.getDefault(),
        )
    }
    val date = LocalDate.of(year, monthNumber, dayOfMonth)
    return date.format(formatter)
}

@Composable
private fun healthConnectSecondaryText(uiState: AppIntegrationUiState): String {
    val context = LocalContext.current
    uiState.lastSyncedAtLabel?.let { return it }
    return when {
        uiState.session?.isViewingOwnData == false -> context.getString(R.string.profile_app_integration_subtitle_own_data_only)
        uiState.availability == HealthConnectAvailability.NotInstalled -> context.getString(R.string.profile_app_integration_subtitle_not_installed)
        uiState.availability == HealthConnectAvailability.NotSupported -> context.getString(R.string.profile_app_integration_subtitle_not_supported)
        else -> context.getString(R.string.profile_app_integration_subtitle_imports_automatically)
    }
}

private fun shouldShowHealthConnectDisabledNote(uiState: AppIntegrationUiState): Boolean =
    uiState.session?.isViewingOwnData != false &&
        uiState.availability == HealthConnectAvailability.Available &&
        !uiState.isEnabled
