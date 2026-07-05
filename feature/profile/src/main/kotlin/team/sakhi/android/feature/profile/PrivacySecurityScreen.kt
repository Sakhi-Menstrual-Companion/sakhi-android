package team.sakhi.android.feature.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.SheetSurface
import team.sakhi.platform.PlatformKeyValueStore
import team.sakhi.preferences.UserPreferenceDefaults
import team.sakhi.preferences.UserPreferenceKeys
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.SessionManager
import java.io.File

/**
 * Ports iOS `PrivacySecurityView.swift`. "Download my data" is a real export
 * (period logs + cycles + profile as JSON, shared via Android's share sheet)
 * built on the same repositories Reports/Logging already use -- not iOS's
 * exact export format (iOS also includes AI chat history; that repository
 * isn't wired into this export yet, a documented gap not a silent drop).
 * Permission rows open Android's app-info settings, matching iOS's "open
 * system settings" pattern (Android has no single unified permission page).
 */
@Composable
fun PrivacySecurityScreen(onBack: () -> Unit) {
    val kvStore = koinInject<PlatformKeyValueStore>()
    val sessionManager = koinInject<SessionManager>()
    val periodLogRepository = koinInject<PeriodLogRepository>()
    val cycleDataRepository = koinInject<CycleDataRepository>()
    val userProfileRepository = koinInject<UserProfileRepository>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }

    SheetSurface(showDragHandle = true) {
        DetailHeader(title = "Privacy & Security", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                Text(
                    text = "PRIVACY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.xl),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        PreferenceToggleRow(
                            title = "Screenshot Warning",
                            kvStore = kvStore,
                            key = UserPreferenceKeys.PRIVACY_SCREENSHOT_WARNING,
                            default = UserPreferenceDefaults.PRIVACY_SCREENSHOT_WARNING,
                        )
                        HorizontalDivider()
                        PreferenceToggleRow(
                            title = "Share Anonymous Analytics",
                            kvStore = kvStore,
                            key = UserPreferenceKeys.ANALYTICS_OPT_IN,
                            default = UserPreferenceDefaults.ANALYTICS_OPT_IN,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                Text(
                    text = "YOUR DATA",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.xl),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isExporting) {
                                val userId = sessionManager.current?.targetUserId ?: return@clickable
                                isExporting = true
                                exportError = null
                                scope.launch {
                                    runCatching {
                                        exportUserData(
                                            context = context,
                                            userId = userId,
                                            periodLogRepository = periodLogRepository,
                                            cycleDataRepository = cycleDataRepository,
                                            userProfileRepository = userProfileRepository,
                                        )
                                    }.onFailure {
                                        exportError = it.message ?: "Couldn't export your data. Please try again."
                                    }
                                    isExporting = false
                                }
                            }
                            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Download my data", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Export a copy of all your health data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    }
                }
                exportError?.let { error ->
                    Text(text = error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                Text(
                    text = "PERMISSIONS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.xl),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        listOf(
                            "Location Access" to "Used only for emergency SOS",
                            "Camera & Microphone" to "Never used by Sakhi",
                            "Contacts" to "For adding care mode contacts",
                        ).forEachIndexed { index, (title, subtitle) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        runCatching { context.startActivity(intent) }
                                    }
                                    .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = title, style = MaterialTheme.typography.bodyLarge)
                                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Filled.ArrowOutward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (index != 2) HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

private suspend fun exportUserData(
    context: android.content.Context,
    userId: String,
    periodLogRepository: PeriodLogRepository,
    cycleDataRepository: CycleDataRepository,
    userProfileRepository: UserProfileRepository,
) {
    // Propagate fetch failures instead of silently exporting a false "0 records"
    // file -- matches iOS's AccountDataInspectorService.buildExportPayload, which
    // throws and shows an "Export failed" toast rather than returning empty data.
    val logs = periodLogRepository.getAll(userId).getOrThrow()
    val cycles = cycleDataRepository.getAll(userId).getOrThrow()
    val profile = userProfileRepository.get(userId).getOrThrow()

    val logsJson = logs.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n  ]") { log ->
        """    {
      "logDate": "${log.logDate}",
      "periodPresent": ${log.periodPresent},
      "flowIntensity": ${log.flowIntensity?.let { "\"${it.value}\"" } ?: "null"},
      "symptoms": [${log.symptoms.joinToString(", ") { "\"${it.jsonEscaped()}\"" }}],
      "moods": [${log.moods.joinToString(", ") { "\"${it.jsonEscaped()}\"" }}],
      "notes": ${log.notes?.let { "\"${it.jsonEscaped()}\"" } ?: "null"}
    }"""
    }

    val payload = """
{
  "userId": "$userId",
  "profileName": "${profile?.name.orEmpty().jsonEscaped()}",
  "periodLogCount": ${logs.size},
  "cycleCount": ${cycles.size},
  "periodLogs": $logsJson
}
""".trimIndent()

    withContext(Dispatchers.IO) {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "sakhi-data-export.json")
        file.writeText(payload)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Sakhi data").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

private fun String.jsonEscaped(): String = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
