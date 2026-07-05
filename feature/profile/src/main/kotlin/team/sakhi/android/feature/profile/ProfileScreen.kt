package team.sakhi.android.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.SheetSurface

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
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPartnerRole = uiState.session?.isViewingOwnData == false

    val groups = profileSettingGroups(
        isPartnerRole = isPartnerRole,
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
            title = { Text("Sign Out") },
            text = {
                Column {
                    Text("Are you sure you want to sign out?")
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
                        Text("Sign Out", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSignOutConfirm, enabled = !uiState.isSigningOut) {
                    Text("Cancel")
                }
            },
        )
    }

    // iOS presents this as `.sheet(...).presentationDragIndicator(.hidden)` --
    // no drag handle, relying on the in-header close button instead.
    SheetSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(
                    horizontal = SakhiSpacing.space2,
                    vertical = SakhiSpacing.space2,
                ),
            )

            ProfileCard(
                uiState = uiState,
                isPartnerRole = isPartnerRole,
                onClick = onEditProfileClick,
            )

            if (uiState.isLoading) {
                Text(
                    text = "Loading profile",
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
                Text(
                    text = group.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = SakhiSpacing.space4,
                        top = SakhiSpacing.space3,
                    ),
                )
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.xxl),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        group.items.forEachIndexed { index, item ->
                            ProfileSettingRow(item = item)
                            if (index != group.items.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    uiState: ProfileUiState,
    isPartnerRole: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xxl),
        tonalElevation = SakhiSpacing.space1,
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
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.lg),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(52.dp),
                ) {}

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isPartnerRole) profileName(uiState) else "You",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = if (uiState.isOfflineUser) "On this device only" else "Synced to your account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!isPartnerRole) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Cycle Health",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = profilePhone(uiState),
                        style = MaterialTheme.typography.bodySmall,
                    )
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
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (item.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        item.value?.let { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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

/** Same group labels, order, and item titles as iOS `ProfileView.groups`/`partnerGroups`. */
private fun profileSettingGroups(
    isPartnerRole: Boolean,
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
            label = "Cycle & Health",
            items = listOf(
                ProfileSettingItem(Icons.Filled.CalendarMonth, "Log History", onLogHistoryClick),
                ProfileSettingItem(Icons.Filled.MonitorHeart, "App Integration", onAppIntegrationClick),
                ProfileSettingItem(Icons.Filled.Description, "Health Report", onHealthDataClick),
            ),
        )
    }

    groups += ProfileSettingGroup(
        label = "Preferences",
        items = listOf(
            ProfileSettingItem(Icons.Filled.NotificationsActive, "Reminders & Alerts", onNotificationsClick),
            ProfileSettingItem(Icons.Filled.Palette, "Appearance & Theme", onAppearanceClick),
        ),
    )

    groups += ProfileSettingGroup(
        label = "Support",
        items = listOf(
            ProfileSettingItem(Icons.Filled.HelpOutline, "Help & Support", onHelpSupportClick),
            ProfileSettingItem(Icons.Filled.Lock, "Privacy & Security", onPrivacySecurityClick),
            ProfileSettingItem(Icons.Filled.Gavel, "Legal & Compliance", onLegalClick),
        ),
    )

    groups += ProfileSettingGroup(
        label = "About",
        items = listOf(
            ProfileSettingItem(Icons.Filled.Info, "About Sakhi", onAboutClick),
            ProfileSettingItem(Icons.Filled.Favorite, "Share Feedback", onFeedbackClick),
        ),
    )

    groups += ProfileSettingGroup(
        label = "Account",
        items = listOf(
            ProfileSettingItem(Icons.Filled.Storage, "Manage Account", onManageAccountClick),
            ProfileSettingItem(Icons.Filled.Logout, "Sign Out", onSignOutClick, isDestructive = true),
        ),
    )

    return groups
}

private fun profileName(uiState: ProfileUiState): String = when {
    uiState.isLoading -> "Loading profile"
    !uiState.profile?.name.isNullOrBlank() -> uiState.profile?.name.orEmpty()
    !uiState.session?.userName.isNullOrBlank() -> uiState.session?.userName.orEmpty()
    else -> "User"
}

private fun profilePhone(uiState: ProfileUiState): String = when {
    !uiState.profile?.phone.isNullOrBlank() -> uiState.profile?.phone.orEmpty()
    !uiState.profile?.email.isNullOrBlank() -> uiState.profile?.email.orEmpty()
    !uiState.session?.userId.isNullOrBlank() -> uiState.session?.userId.orEmpty()
    else -> "No contact info available"
}
