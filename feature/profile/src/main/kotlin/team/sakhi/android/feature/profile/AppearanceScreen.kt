package team.sakhi.android.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.koin.compose.koinInject
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.SheetSurface
import team.sakhi.platform.PlatformKeyValueStore
import team.sakhi.preferences.ThemeMode
import team.sakhi.preferences.ThemePreferenceStore
import team.sakhi.preferences.UserPreferenceDefaults
import team.sakhi.preferences.UserPreferenceKeys

/**
 * Ports iOS `AppearanceSettingsView.swift`'s Theme + Interaction cards. Real,
 * functional theme switching -- `ThemePreferenceStore` is read by `MainActivity`
 * to pick `SakhiTheme`'s `darkTheme` value app-wide, not just cosmetic here.
 * Language switching is not ported: Android has no Sanity-CMS localization
 * layer wired at all this session, unlike iOS's `LocalizationManager` -- a
 * real, separate gap, not a shortcut taken here.
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val themeStore = koinInject<ThemePreferenceStore>()
    val kvStore = koinInject<PlatformKeyValueStore>()
    val hapticManager = koinInject<AndroidHapticManager>()
    val currentMode by themeStore.mode.collectAsState()

    SheetSurface(showDragHandle = true) {
        DetailHeader(title = "Appearance", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                Text(
                    text = "THEME",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.xl),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        val options = listOf(
                            ThemeMode.SYSTEM to "System Default",
                            ThemeMode.LIGHT to "Light",
                            ThemeMode.DARK to "Dark",
                        )
                        options.forEachIndexed { index, (mode, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        hapticManager.impact(HapticImpact.LIGHT)
                                        themeStore.setMode(mode)
                                    }
                                    .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                if (currentMode == mode) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (index != options.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                Text(
                    text = "INTERACTION",
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
                            title = "Haptic Feedback",
                            kvStore = kvStore,
                            key = UserPreferenceKeys.HAPTICS_ENABLED,
                            default = UserPreferenceDefaults.HAPTICS_ENABLED,
                        )
                        HorizontalDivider()
                        PreferenceToggleRow(
                            title = "Reduce Motion",
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

@Composable
internal fun PreferenceToggleRow(title: String, kvStore: PlatformKeyValueStore, key: String, default: Boolean) {
    var checked by remember { mutableStateOf(kvStore.getBool(key, default)) }
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
            },
        )
    }
}
