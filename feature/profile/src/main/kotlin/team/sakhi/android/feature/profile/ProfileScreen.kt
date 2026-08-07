package team.sakhi.android.feature.profile

import android.content.Context

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiConfirm
import team.sakhi.android.designsystem.sakhiDeepRose
import team.sakhi.android.designsystem.sakhiLightPink
import team.sakhi.android.designsystem.sakhiProfileCardBackground
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.designsystem.sakhiWarning
import team.sakhi.android.ui.ProfileSectionLabel
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.android.ui.SheetSurface
import team.sakhi.models.CycleHealthStatus
import team.sakhi.preferences.ThemeMode
import team.sakhi.preferences.ThemePreferenceStore
import team.sakhi.android.ui.SakhiNavBar

/**
 * Real port of iOS `ProfileView.swift`'s grouped settings list: same section
 * labels/order/icons for the owner role ("Cycle & Health", "Preferences",
 * "Support", "About", "Account") and the reduced partner-role group set (no
 * "Cycle & Health"), same profile-card layout (icon, name, account-status line,
 * chevron). Every `ProfileRoute` destination iOS pushes onto its own
 * `NavigationStack` (EditProfile, ActivityLog, AppIntegration, Notifications,
 * Appearance, HelpSupport, PrivacySecurity, Legal, About, Feedback,
 * ManageAccount) now has a real Android destination wired in `HomeNavHost.kt`
 * (2026-07-05) — see that file's `HomeGraphRoute` entries.
 * `isOfflineUser` (drives the account-status line) is now real, wired to the
 * shared `FeatureAccessState.isGuest` (2026-07-05) fed by `RootNavHost` from
 * the actual `SessionState` type. `isSyncPaused` stays unwired -- Android has
 * no "Use Sakhi Offline" toggle yet for a signed-in user to trigger it, so
 * `FeatureAccessState.isOnlineAccountPaused` would only ever honestly read
 * false; the Account-group rows that depend on it ("Resume Online Sync") are
 * a real, separate, larger gap (needs the offline-toggle feature itself
 * first), not silently faked here.
 */
@Composable
fun ProfileScreen(
    onEditProfileClick: () -> Unit = {},
    onLogHistoryClick: () -> Unit = {},
    onAppIntegrationClick: () -> Unit = {},
    onHealthDataClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {},
    onHelpSupportClick: () -> Unit = {},
    onPrivacySecurityClick: () -> Unit = {},
    onLegalClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onFeedbackClick: () -> Unit = {},
    onManageAccountClick: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isPartnerRole = uiState.session?.isViewingOwnData == false
    val uriHandler = LocalUriHandler.current
    val themeStore = koinInject<ThemePreferenceStore>()
    val currentThemeMode by themeStore.mode.collectAsState()

    val groups = profileSettingGroups(
        context = context,
        isPartnerRole = isPartnerRole,
        appearanceModeLabel = appearanceModeLabel(context, currentThemeMode),
        onLogHistoryClick = onLogHistoryClick,
        onAppIntegrationClick = onAppIntegrationClick,
        onHealthDataClick = onHealthDataClick,
        onNotificationsClick = onNotificationsClick,
        onAppearanceClick = onAppearanceClick,
        onHelpSupportClick = onHelpSupportClick,
        onPrivacySecurityClick = onPrivacySecurityClick,
        onLegalClick = onLegalClick,
        onAboutClick = onAboutClick,
        onFeedbackClick = onFeedbackClick,
        onManageAccountClick = onManageAccountClick,
        onSignOutClick = viewModel::requestSignOut,
    )

    if (uiState.showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSignOutConfirm,
            title = { Text(stringResource(R.string.profile_sign_out_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.profile_sign_out_confirm))
                    uiState.signOutError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = SakhiSpacing.space2),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmSignOut, enabled = !uiState.isSigningOut) {
                    if (uiState.isSigningOut) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            text = stringResource(R.string.profile_sign_out_action),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSignOutConfirm, enabled = !uiState.isSigningOut) {
                    Text(stringResource(R.string.profile_cancel))
                }
            },
        )
    }

    // iOS presents this as `.sheet(...).presentationDragIndicator(.hidden)` --
    // no drag handle, relying on the in-header close button instead.
    // iOS `.profileStaticPageBackground()` -> `DS.Colors.background` (#F8F2F4) in light,
    // not the brand lightPink every other sheet uses.
    SheetSurface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // iOS: `DSNavBar(onClose: { dismissSheet() }, title: "Profile")`
            // (`ProfileView.swift:205`). Android had a bare headline Text and **no close
            // button at all**, despite the comment above stating iOS relies on one.
            SakhiNavBar(
                title = stringResource(R.string.profile_title),
                onClose = onClose,
            )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {

            ProfileCard(
                uiState = uiState,
                context = context,
                isPartnerRole = isPartnerRole,
                onClick = onEditProfileClick,
            )

            if (uiState.isLoading) {
                Text(
                    text = stringResource(R.string.profile_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = SakhiSpacing.space2),
                )
            }
            uiState.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = SakhiSpacing.space2),
                )
            }

            groups.forEach { group ->
                ProfileSectionLabel(
                    text = group.label.uppercase(),
                    modifier = Modifier.padding(top = SakhiSpacing.space3),
                )
                Surface(
                    shape = RoundedCornerShape(ProfileCardRadius),
                    color = sakhiProfileCardBackground(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        group.items.forEachIndexed { index, item ->
                            ProfileSettingRow(item = item)
                            if (index != group.items.lastIndex) {
                                SakhiListDivider(startInset = ProfileSettingDividerInset)
                            }
                        }
                    }
                }
            }

            ProfileFooter(onConnectClick = { uriHandler.openUri("https://sakhi.rachna.co") })
        }
        }
    }
}

@Composable
private fun ProfileCard(
    uiState: ProfileUiState,
    context: android.content.Context,
    isPartnerRole: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(ProfileCardRadius),
        // iOS `.dsCard(.pink)` = r16 filled with `profileCardBackground` (white). Letting
        // this fall through to `colorScheme.surface` drew a pink card on the pink page.
        color = sakhiProfileCardBackground(),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SakhiSpacing.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
            ) {
                // iOS `appIconView`: a 52pt rounded SQUARE (r12) filled `lightPink` with
                // the 30pt brand symbol on it -- not an initials avatar. Android drew a
                // pink circle with the first letter of the account name, which is a
                // different element entirely.
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(sakhiLightPink(), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(team.sakhi.android.ui.R.drawable.sakhi_symbol),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isPartnerRole) profileName(context, uiState) else stringResource(R.string.profile_you),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // iOS: `.foregroundColor(isAccountSecureOnline ? DS.Colors.confirm
                        // : DS.Colors.secondaryLabel)` -- the secure state is GREEN, and it
                        // is the one signal on this screen telling her the data is off the
                        // device. Android was drawing it in brand pink, indistinguishable
                        // from every other accent here.
                        val statusColor = if (uiState.isOfflineUser) {
                            sakhiSecondaryLabel()
                        } else {
                            sakhiConfirm()
                        }
                        Icon(
                            imageVector = if (uiState.isOfflineUser) Icons.Filled.PhoneAndroid else Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = if (uiState.isOfflineUser) {
                                stringResource(R.string.profile_on_device)
                            } else {
                                stringResource(R.string.profile_synced_secure)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    // iOS: chevron.right in `tertiaryLabel`, the lightest of the three inks.
                    tint = sakhiTertiaryLabel(),
                )
            }

            if (!isPartnerRole) {
                SakhiListDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.profile_cycle_health),
                        style = MaterialTheme.typography.bodySmall,
                        color = sakhiSecondaryLabel(),
                    )
                    uiState.cycleHealthStatus?.let { status ->
                        CycleHealthBadge(status = status)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSettingRow(item: ProfileSettingItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = item.onClick)
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        Box(
            modifier = Modifier.width(ProfileSettingLeadingIconWidth),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (item.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ProfileSettingLeadingIconSize),
            )
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = ProfileSettingTitleSize),
            color = if (item.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        item.value?.let { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = ProfileSettingValueSize),
                // iOS: lato(13) in `secondaryLabel`.
                color = sakhiSecondaryLabel(),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (item.isDestructive) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            } else {
                sakhiTertiaryLabel()
            },
            modifier = Modifier.size(ProfileSettingChevronSize),
        )
    }
}

private fun appearanceModeLabel(context: Context, themeMode: ThemeMode): String = when (themeMode) {
    ThemeMode.SYSTEM -> context.getString(R.string.profile_appearance_row_value_system)
    ThemeMode.LIGHT -> context.getString(R.string.profile_appearance_theme_light)
    ThemeMode.DARK -> context.getString(R.string.profile_appearance_theme_dark)
}

private data class ProfileSettingItem(
    val icon: ImageVector,
    val title: String,
    val onClick: () -> Unit,
    val value: String? = null,
    val isDestructive: Boolean = false,
)

private data class ProfileSettingGroup(
    val label: String,
    val items: List<ProfileSettingItem>,
)

private val ProfileSettingLeadingIconWidth = 28.dp
private val ProfileSettingLeadingIconSize = 18.dp
private val ProfileSettingTitleSize = 15.sp
private val ProfileSettingValueSize = 13.sp
private val ProfileSettingChevronSize = 11.dp

/** iOS `.dsCard(.pink)` -> `DS.Radius.onboardingCard` = 16. */
private val ProfileCardRadius = SakhiRadius.xl

/**
 * iOS: `.padding(.leading, DS.Spacing.m + 34 + DS.Spacing.s)` = 16 + 34 + 12 = 62.
 *
 * Written out rather than derived from the 28pt icon frame above: iOS uses 34 here, not
 * the 28 it gives the icon, so deriving it produced a 52pt inset and the dividers started
 * 10pt further left than iOS's.
 */
private val ProfileSettingDividerInset = 62.dp

/** Same group labels, order, and item titles as iOS `ProfileView.groups`/`partnerGroups`. */
private fun profileSettingGroups(
    context: Context,
    isPartnerRole: Boolean,
    appearanceModeLabel: String,
    onLogHistoryClick: () -> Unit,
    onAppIntegrationClick: () -> Unit,
    onHealthDataClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onHelpSupportClick: () -> Unit,
    onPrivacySecurityClick: () -> Unit,
    onLegalClick: () -> Unit,
    onAboutClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    onManageAccountClick: () -> Unit,
    onSignOutClick: () -> Unit,
): List<ProfileSettingGroup> {
    val groups = mutableListOf<ProfileSettingGroup>()

    if (!isPartnerRole) {
        groups += ProfileSettingGroup(
            label = context.getString(R.string.profile_group_cycle_health),
            items = listOf(
                ProfileSettingItem(Icons.Filled.CalendarMonth, context.getString(R.string.profile_item_log_history), onLogHistoryClick),
                ProfileSettingItem(Icons.Filled.MonitorHeart, context.getString(R.string.profile_item_app_integration), onAppIntegrationClick),
                ProfileSettingItem(Icons.Filled.Description, context.getString(R.string.profile_item_health_report), onHealthDataClick),
            ),
        )
    }

    groups += ProfileSettingGroup(
        label = context.getString(R.string.profile_group_preferences),
        items = listOf(
            ProfileSettingItem(Icons.Filled.Notifications, context.getString(R.string.profile_item_notifications), onNotificationsClick),
            ProfileSettingItem(
                Icons.Filled.Brush,
                context.getString(R.string.profile_item_appearance),
                onAppearanceClick,
                value = appearanceModeLabel,
            ),
        ),
    )

    groups += ProfileSettingGroup(
        label = context.getString(R.string.profile_group_support),
        items = listOf(
            ProfileSettingItem(Icons.AutoMirrored.Filled.Help, context.getString(R.string.profile_item_help_support), onHelpSupportClick),
            ProfileSettingItem(Icons.Filled.Shield, context.getString(R.string.profile_item_privacy_security), onPrivacySecurityClick),
            ProfileSettingItem(Icons.Filled.Gavel, context.getString(R.string.profile_item_legal), onLegalClick),
        ),
    )

    groups += ProfileSettingGroup(
        label = context.getString(R.string.profile_group_about),
        items = listOf(
            ProfileSettingItem(Icons.Filled.Info, context.getString(R.string.profile_item_about), onAboutClick),
            ProfileSettingItem(Icons.Filled.Favorite, context.getString(R.string.profile_item_feedback), onFeedbackClick),
        ),
    )

    groups += ProfileSettingGroup(
        label = context.getString(R.string.profile_group_account),
        items = listOf(
            ProfileSettingItem(Icons.Filled.Storage, context.getString(R.string.profile_item_manage_account), onManageAccountClick),
            // "Use Sakhi offline" is deliberately NOT listed yet. The screen exists
            // (OfflineModeScreen) but Android cannot honour what it promises: every
            // repository writes straight to Supabase, so logging does not work offline
            // and there is no queued sync to pause. Re-add this row only once Android
            // has a local-first write path. See Android-Live-Status-Log.md.
            ProfileSettingItem(Icons.AutoMirrored.Filled.Logout, context.getString(R.string.profile_item_sign_out), onSignOutClick, isDestructive = true),
        ),
    )

    return groups
}

private fun profileName(context: android.content.Context, uiState: ProfileUiState): String = when {
    uiState.isLoading -> context.getString(R.string.profile_loading)
    !uiState.profile?.name.isNullOrBlank() -> uiState.profile?.name.orEmpty()
    !uiState.session?.userName.isNullOrBlank() -> uiState.session?.userName.orEmpty()
    else -> context.getString(R.string.profile_fallback_user)
}

@Composable
private fun CycleHealthBadge(status: CycleHealthStatus) {
    // iOS `cycleRegularityDisplay` picks from DS.Colors: confirm / warning / danger, where
    // `warning` is deepRose at 65% and `danger` is deepRose. Android had two hardcoded hexes
    // that belong to neither palette -- #F39C48 in particular rendered the "Irregular" pill
    // orange, the only non-brand colour on the screen.
    val color = when (status) {
        CycleHealthStatus.REGULAR -> sakhiConfirm()
        CycleHealthStatus.IRREGULAR -> sakhiWarning()
        CycleHealthStatus.DELAYED -> sakhiDeepRose()
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = 5.dp)
            .background(color.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(7.dp),
        ) {}
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

private fun avatarInitials(uiState: ProfileUiState): String? {
    val name = uiState.profile?.name
        ?.takeIf { it.isNotBlank() }
        ?: uiState.session?.userName?.takeIf { it.isNotBlank() }
        ?: return null
    val initials = name
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
    return initials.ifBlank { null }
}

@Composable
private fun ProfileFooter(onConnectClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SakhiSpacing.space6, bottom = SakhiSpacing.space10),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.profile_footer_body),
            style = MaterialTheme.typography.bodySmall,
            color = sakhiSecondaryLabel(),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onConnectClick,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_footer_cta),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
