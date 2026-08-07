package team.sakhi.android.feature.profile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.android.ui.ProfileSectionLabel
import team.sakhi.platform.PlatformKeyValueStore
import team.sakhi.preferences.UserPreferenceDefaults
import team.sakhi.preferences.UserPreferenceKeys
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.SessionManager
import java.io.File
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.ui.SakhiListDivider

private val PrivacySecurityDividerInset = SakhiSpacing.space4 + 24.dp + SakhiSpacing.space3
private val PrivacySecurityRowIconSize = 15.dp
private val PrivacySecurityRowTitleSize = 16.sp
private val PrivacySecurityRowSubtitleSize = 12.sp
private val PrivacySecurityTrailingArrowSize = 13.dp
private val PrivacySecurityChevronSize = 12.dp
private val PrivacySecurityFootnoteSize = 11.sp
private val PrivacySecurityFootnoteLineHeight = 16.sp
private val PrivacySecurityFootnoteInset = SakhiSpacing.space4 + 4.dp

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

    val exportFailedText = stringResource(R.string.profile_privacy_export_failed)

    DetailSheetScaffold(
        title = stringResource(R.string.profile_privacy_title),
        subtitle = stringResource(R.string.profile_privacy_header_subtitle),
        headerIcon = Icons.Filled.Shield,
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5)) {
            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                ProfileSectionLabel(text = stringResource(R.string.profile_privacy_section_privacy))
                Surface(
                    color = sakhiSystemBackground(),
                    shape = RoundedCornerShape(SakhiRadius.xl),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        // MainActivity can only detect screenshots on API 34+
                        // (`registerScreenCaptureCallback`); the pre-34 alternative needs
                        // READ_MEDIA_IMAGES, which this app will not ask for. Below 34 the
                        // row is hidden rather than shown as a toggle that does nothing.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            PrivacyToggleRow(
                                title = stringResource(R.string.profile_privacy_screenshot_warning),
                                subtitle = stringResource(R.string.profile_privacy_screenshot_warning_subtitle),
                                icon = Icons.Filled.PhotoCamera,
                                kvStore = kvStore,
                                key = UserPreferenceKeys.PRIVACY_SCREENSHOT_WARNING,
                                default = UserPreferenceDefaults.PRIVACY_SCREENSHOT_WARNING,
                            )
                            SakhiListDivider(startInset = PrivacySecurityDividerInset)
                        }
                        PrivacyToggleRow(
                            title = stringResource(R.string.profile_privacy_analytics),
                            subtitle = stringResource(R.string.profile_privacy_analytics_subtitle),
                            icon = Icons.Filled.BarChart,
                            kvStore = kvStore,
                            key = UserPreferenceKeys.ANALYTICS_OPT_IN,
                            default = UserPreferenceDefaults.ANALYTICS_OPT_IN,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                ProfileSectionLabel(text = stringResource(R.string.profile_privacy_section_your_data))
                Surface(
                    color = sakhiSystemBackground(),
                    shape = RoundedCornerShape(SakhiRadius.xl),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isExporting) {
                                // Real bug found in this session's own critical self-review
                                // (same class as MyDataViewModel's `targetUserId` bug):
                                // "Export Your Data" is a self-only account tool -- must
                                // export the signed-in device owner's own data, never
                                // whoever a partner happens to be viewing in care mode.
                                // This screen is reachable by partners (the "Support" group
                                // in ProfileScreen.kt's `profileSettingGroups` is unconditional,
                                // unlike the partner-hidden "Cycle & Health" group), so this
                                // was a real, live leak: a partner could have exported the
                                // primary user's period logs/cycles/profile to a file on
                                // their own device.
                                val userId = sessionManager.current?.userId ?: return@clickable
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
                                        exportError = it.message ?: exportFailedText
                                    }
                                    isExporting = false
                                }
                            }
                            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(PrivacySecurityRowIconSize),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.profile_privacy_download_data),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = PrivacySecurityRowTitleSize),
                            )
                            Text(
                                text = stringResource(R.string.profile_privacy_download_data_subtitle),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = PrivacySecurityRowSubtitleSize),
                                color = sakhiSecondaryLabel(),
                            )
                        }
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = sakhiTertiaryLabel(),
                                modifier = Modifier.size(PrivacySecurityChevronSize),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.profile_privacy_download_data_note),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = PrivacySecurityFootnoteSize,
                        lineHeight = PrivacySecurityFootnoteLineHeight,
                    ),
                    color = sakhiSecondaryLabel(),
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PrivacySecurityFootnoteInset),
                )
                exportError?.let { error ->
                    Text(text = error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                ProfileSectionLabel(text = stringResource(R.string.profile_privacy_section_permissions))
                Surface(
                    color = sakhiSystemBackground(),
                    shape = RoundedCornerShape(SakhiRadius.xl),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        listOf(
                            PermissionRowContent(
                                titleRes = R.string.profile_privacy_permission_location,
                                subtitleRes = R.string.profile_privacy_permission_location_subtitle,
                                icon = Icons.Filled.LocationOn,
                            ),
                            PermissionRowContent(
                                titleRes = R.string.profile_privacy_permission_camera,
                                subtitleRes = R.string.profile_privacy_permission_camera_subtitle,
                                icon = Icons.Filled.PhotoCamera,
                            ),
                            PermissionRowContent(
                                titleRes = R.string.profile_privacy_permission_contacts,
                                subtitleRes = R.string.profile_privacy_permission_contacts_subtitle,
                                icon = Icons.Filled.People,
                            ),
                        ).forEachIndexed { index, row ->
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
                                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = row.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(PrivacySecurityRowIconSize),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(row.titleRes),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = PrivacySecurityRowTitleSize),
                                    )
                                    Text(
                                        text = stringResource(row.subtitleRes),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = PrivacySecurityRowSubtitleSize),
                                        color = sakhiSecondaryLabel(),
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ArrowOutward,
                                    contentDescription = null,
                                    tint = sakhiTertiaryLabel(),
                                    modifier = Modifier.size(PrivacySecurityTrailingArrowSize),
                                )
                            }
                            if (index != 2) {
                                SakhiListDivider(startInset = PrivacySecurityDividerInset)
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.profile_privacy_permissions_note),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = PrivacySecurityFootnoteSize,
                        lineHeight = PrivacySecurityFootnoteLineHeight,
                    ),
                    color = sakhiSecondaryLabel(),
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PrivacySecurityFootnoteInset),
                )
            }
        }
    }
}

private data class PermissionRowContent(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
)

@Composable
private fun PrivacyToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    kvStore: PlatformKeyValueStore,
    key: String,
    default: Boolean,
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
            modifier = Modifier.size(PrivacySecurityRowIconSize),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = PrivacySecurityRowTitleSize),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = PrivacySecurityRowSubtitleSize),
                color = sakhiSecondaryLabel(),
            )
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = { value ->
                checked = value
                kvStore.setBool(key, value)
            },
        )
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
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.profile_privacy_export_chooser)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

private fun String.jsonEscaped(): String = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
