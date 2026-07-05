package team.sakhi.android.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
import team.sakhi.android.ui.SakhiAlert
import team.sakhi.android.ui.SakhiAlertTone
import team.sakhi.android.ui.SecondaryButton
import team.sakhi.android.ui.SheetSurface

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

    SheetSurface(showDragHandle = true) {
        DetailHeader(title = "App Integration", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
        ) {
            Text(
                text = "Import period, sleep, steps, and temperature from Health Connect. Sleep, steps, and temperature stay on device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!uiState.error.isNullOrBlank()) {
                SakhiAlert(
                    title = "Health Connect",
                    message = uiState.error.orEmpty(),
                    tone = SakhiAlertTone.Error,
                    onDismiss = viewModel::clearError,
                )
            }

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

            if (uiState.isEnabled && uiState.sleepEntries.isNotEmpty()) {
                InsightCard(
                    label = "SLEEP",
                    title = "Avg Sleep",
                    value = average(uiState.sleepEntries).let { String.format("%.1f hrs", it) },
                    rows = uiState.sleepEntries,
                    formatter = { String.format("%.1f hrs", it) },
                )
            }
            if (uiState.isEnabled && uiState.stepEntries.isNotEmpty()) {
                InsightCard(
                    label = "ACTIVITY",
                    title = "Avg Steps",
                    value = average(uiState.stepEntries).toInt().toString(),
                    rows = uiState.stepEntries,
                    formatter = { it.toInt().toString() },
                )
            }
            if (uiState.isEnabled && uiState.temperatureEntries.isNotEmpty()) {
                InsightCard(
                    label = "TEMPERATURE",
                    title = "Recent Readings",
                    value = "${uiState.temperatureEntries.size} days",
                    rows = uiState.temperatureEntries,
                    formatter = { String.format("%.1f°C", it) },
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
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Health Connect",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = healthConnectSubtitle(uiState),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (uiState.isSyncing) {
                CircularProgressIndicator(strokeWidth = SakhiSpacing.space1 / 3)
            } else if (uiState.isEnabled) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        uiState.lastSyncedAtLabel?.let { lastSync ->
            Text(
                text = lastSync,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = SakhiSpacing.space3),
            )
        }

        uiState.latestSync?.let { latest ->
            Text(
                text = "Imported ${latest.importedPeriodLogs} period days, ${latest.importedSleepSamples} sleep entries, ${latest.importedStepSamples} step entries, and ${latest.importedTemperatureSamples} temperature readings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text = "Health Connect is only available when viewing your own data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.availability == HealthConnectAvailability.NotInstalled -> {
                    PrimaryButton(
                        text = "Install Health Connect",
                        onClick = onInstall,
                        modifier = Modifier.weight(1f),
                    )
                }
                uiState.availability == HealthConnectAvailability.NotSupported -> {
                    Text(
                        text = "Health Connect is not available on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.isEnabled -> {
                    SecondaryButton(
                        text = "Disconnect",
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSyncing,
                    )
                    PrimaryButton(
                        text = "Sync now",
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
                        text = "Connect Health Connect",
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
    rows: List<DailyHealthValue>,
    formatter: (Double) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            rows.forEachIndexed { index, row ->
                HorizontalDivider(modifier = Modifier.padding(top = SakhiSpacing.space3))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .padding(top = SakhiSpacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.date.toDisplayLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun healthConnectSubtitle(uiState: AppIntegrationUiState): String {
    return when {
        uiState.session?.isViewingOwnData == false -> "Available only for your own Health Connect data."
        uiState.availability == HealthConnectAvailability.NotInstalled -> "Install the Health Connect app to start importing."
        uiState.availability == HealthConnectAvailability.NotSupported -> "This device does not support Health Connect."
        uiState.isSyncing -> "Syncing period, sleep, steps, and temperature."
        uiState.isEnabled -> "Imports cycle data into Sakhi and keeps health insights on this device."
        uiState.hasPermissions -> "Permissions granted. Connect when you're ready."
        else -> "Import period, sleep, steps, and temperature."
    }
}

private fun average(values: List<DailyHealthValue>): Double {
    if (values.isEmpty()) return 0.0
    return values.sumOf(DailyHealthValue::value) / values.size
}

private fun kotlinx.datetime.LocalDate.toDisplayLabel(): String {
    val date = LocalDate.of(year, monthNumber, dayOfMonth)
    return date.format(DateTimeFormatter.ofPattern("d MMM"))
}
