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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.AndroidLocaleManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.platform.Language as AppLanguage
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.android.ui.ProfileSectionLabel
import team.sakhi.platform.PlatformKeyValueStore
import team.sakhi.preferences.ThemeMode
import team.sakhi.preferences.ThemePreferenceStore
import team.sakhi.preferences.UserPreferenceDefaults
import team.sakhi.preferences.UserPreferenceKeys

private val AppearanceRowDividerInset = SakhiSpacing.space4 + 24.dp + SakhiSpacing.space3
private val AppearanceThemeIconSize = 15.dp
private val AppearanceThemeCheckSize = 14.dp

/**
 * Ports iOS `AppearanceSettingsView.swift`'s Language + Theme + Interaction
 * cards. Real, functional theme switching -- `ThemePreferenceStore` is read
 * by `MainActivity` to pick `SakhiTheme`'s `darkTheme` value app-wide, not
 * just cosmetic here. Language switching is real too: `AndroidLocaleManager`
 * triggers the actual OS-level per-app locale switch
 * (`AppCompatDelegate.setApplicationLocales`), which both re-localizes
 * `strings.xml` resources and flows into `Locale.getDefault().language`,
 * already read by `ContentPageScreen.kt`'s live Sanity FAQ/legal content.
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val themeStore = koinInject<ThemePreferenceStore>()
    val kvStore = koinInject<PlatformKeyValueStore>()
    val hapticManager = koinInject<AndroidHapticManager>()
    val localeManager = koinInject<AndroidLocaleManager>()
    val currentMode by themeStore.mode.collectAsState()
    val currentLanguage = localeManager.currentLanguage

    DetailSheetScaffold(
        title = stringResource(R.string.profile_appearance_title),
        subtitle = stringResource(R.string.profile_appearance_header_subtitle),
        headerIcon = Icons.Filled.Palette,
        onBack = onBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5)) {
            LanguageCard(
                currentLanguage = currentLanguage,
                onLanguageChosen = { language ->
                    hapticManager.impact(HapticImpact.MEDIUM)
                    localeManager.setLanguage(language)
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                ProfileSectionLabel(text = stringResource(R.string.profile_appearance_section_theme))
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.xl),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        val options = listOf(
                            ThemeOption(ThemeMode.SYSTEM, R.string.profile_appearance_theme_system, Icons.Filled.BrightnessAuto),
                            ThemeOption(ThemeMode.LIGHT, R.string.profile_appearance_theme_light, Icons.Filled.LightMode),
                            ThemeOption(ThemeMode.DARK, R.string.profile_appearance_theme_dark, Icons.Filled.DarkMode),
                        )
                        options.forEachIndexed { index, option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        hapticManager.impact(HapticImpact.LIGHT)
                                        themeStore.setMode(option.mode)
                                    }
                                    .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    tint = if (currentMode == option.mode) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(AppearanceThemeIconSize),
                                )
                                Text(
                                    text = stringResource(option.labelRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (currentMode == option.mode) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(AppearanceThemeCheckSize),
                                    )
                                }
                            }
                            if (index != options.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(start = AppearanceRowDividerInset))
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                ProfileSectionLabel(text = stringResource(R.string.profile_appearance_section_interaction))
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.xl),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        PreferenceToggleRow(
                            title = stringResource(R.string.profile_appearance_haptics),
                            subtitle = stringResource(R.string.profile_appearance_haptics_subtitle),
                            icon = Icons.Filled.TouchApp,
                            kvStore = kvStore,
                            key = UserPreferenceKeys.HAPTICS_ENABLED,
                            default = UserPreferenceDefaults.HAPTICS_ENABLED,
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = AppearanceRowDividerInset))
                        PreferenceToggleRow(
                            title = stringResource(R.string.profile_appearance_reduce_motion),
                            subtitle = stringResource(R.string.profile_appearance_reduce_motion_subtitle),
                            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                            kvStore = kvStore,
                            key = UserPreferenceKeys.REDUCE_MOTION,
                            default = UserPreferenceDefaults.REDUCE_MOTION,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Port of iOS's `languageCard`: a row (globe icon, "Language" title, current
 * "{flag} {name}" subtitle) that opens a menu over `Language.entries`, each
 * showing "{flag} {name} ({nativeName})" with a checkmark on the current
 * selection. Picking a different language shows the real iOS confirmation
 * copy ("Change Language? / The app will reload in {name}. This takes about
 * a second.") before actually switching -- matches iOS's `pendingLanguage`
 * two-step confirm, not an instant switch.
 */
@Composable
private fun LanguageCard(currentLanguage: AppLanguage, onLanguageChosen: (AppLanguage) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingLanguage by remember { mutableStateOf<AppLanguage?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        ProfileSectionLabel(text = stringResource(R.string.profile_appearance_section_language))
        Surface(
            shape = RoundedCornerShape(SakhiRadius.xl),
            tonalElevation = SakhiSpacing.space1,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { menuExpanded = true }
                        .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        val currentDisplayName = stringResource(currentLanguage.displayNameRes)
                        Text(
                            text = stringResource(R.string.profile_appearance_language_row_title),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(
                                R.string.profile_appearance_language_row_subtitle,
                                currentLanguage.flag,
                                currentDisplayName,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.UnfoldMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }

                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    AppLanguage.entries.forEach { language ->
                        val displayName = stringResource(language.displayNameRes)
                        val nativeName = stringResource(language.nativeNameRes)
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.profile_appearance_language_option_label,
                                            language.flag,
                                            displayName,
                                            nativeName,
                                        )
                                    )
                                }
                            },
                            trailingIcon = {
                                if (language == currentLanguage) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                menuExpanded = false
                                if (language != currentLanguage) pendingLanguage = language
                            },
                        )
                    }
                }
            }
        }
    }

    pendingLanguage?.let { language ->
        val displayName = stringResource(language.displayNameRes)
        AlertDialog(
            onDismissRequest = { pendingLanguage = null },
            title = { Text(stringResource(R.string.profile_appearance_change_language_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.profile_appearance_change_language_message,
                        displayName,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLanguageChosen(language)
                        pendingLanguage = null
                    },
                ) {
                    Text(
                        stringResource(
                            R.string.profile_appearance_change_language_confirm,
                            language.flag,
                            displayName,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLanguage = null }) {
                    Text(stringResource(R.string.profile_appearance_cancel))
                }
            },
        )
    }
}

@Composable
internal fun PreferenceToggleRow(
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
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { value ->
                checked = value
                kvStore.setBool(key, value)
            },
        )
    }
}

private data class ThemeOption(
    val mode: ThemeMode,
    val labelRes: Int,
    val icon: ImageVector,
)
