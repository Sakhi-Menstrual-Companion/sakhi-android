package team.sakhi.android.feature.emergency

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSeparator
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.ui.FeatureBulletRow
import team.sakhi.android.ui.OnboardingIntroScaffold
import team.sakhi.android.ui.OnboardingShell
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.android.ui.SakhiNavDirection
import team.sakhi.android.ui.SakhiScreenTransition
import team.sakhi.android.ui.SakhiSwitch

/**
 * The three-page introduction shown the first time she opens Emergency Assistance.
 *
 * This is the account onboarding screen, not a lookalike. It renders through
 * [OnboardingShell] -- the exact frame every one of account onboarding's ~31 steps uses --
 * with [OnboardingIntroScaffold] for the title, subtitle and content, and
 * [team.sakhi.android.ui.SakhiFooter] pinned at the bottom carrying Continue. Those pieces
 * live in `core:ui` so the two flows cannot drift apart, which they had: this screen used
 * to re-declare the shell's Column itself and had lost the top safe-area inset and the
 * page's pink ground along the way.
 *
 * Page one is what the feature is, page two is how trust levels work, page three is the
 * permissions it needs. Shown once and then remembered -- the flag lives in the shared
 * [team.sakhi.emergency.EmergencyStore], so both platforms show these on the same
 * schedule rather than each keeping its own idea of whether she has read them.
 *
 * The back affordance lives in the nav bar, as it does in account onboarding. Page one
 * has nothing to go back to, so it takes a close button in the same leading slot instead
 * -- iOS does the same thing, sharing that slot between back and close.
 */
@Composable
internal fun EmergencyOnboarding(
    locationGranted: Boolean,
    notificationsGranted: Boolean,
    onRequestLocation: () -> Unit,
    onRequestNotifications: () -> Unit,
    onFinished: () -> Unit,
    onCancel: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }

    // The system back does what the nav bar's leading button does. Without it, back from
    // here fell through to the activity and closed the app.
    BackHandler { if (page > 0) page-- else onCancel() }

    OnboardingShell(
        onBack = if (page > 0) ({ page-- }) else null,
        onClose = if (page == 0) onCancel else null,
        // Presented as a full-screen cover over Home (`HomeNavHost`), so unlike the flow
        // inside `RootNavHost` there is no themed root painting the page behind it.
        paintsPageBackground = true,
    ) {
        // Account onboarding slides between its steps rather than cutting; going through
        // the same component is what keeps the two feeling like one flow. `page` has a
        // natural order, so direction comes from comparing the two values rather than from
        // a separate intent flag.
        SakhiScreenTransition(
            modifier = Modifier.weight(1f),
            targetState = page,
            directionFor = { initial, target ->
                if (target >= initial) SakhiNavDirection.Forward else SakhiNavDirection.Backward
            },
            label = "emergency_onboarding_page",
        ) { rendered ->
            when (rendered) {
                0 -> IntroPage(onContinue = { page = 1 })
                1 -> TrustPage(onContinue = { page = 2 })
                else -> PermissionsPage(
                    locationGranted = locationGranted,
                    notificationsGranted = notificationsGranted,
                    onRequestLocation = onRequestLocation,
                    onRequestNotifications = onRequestNotifications,
                    onContinue = onFinished,
                )
            }
        }
    }
}

@Composable
private fun IntroPage(onContinue: () -> Unit) {
    OnboardingIntroScaffold(
        title = stringResource(R.string.emergency_intro_title),
        subtitle = stringResource(R.string.emergency_intro_subtitle),
        primaryLabel = stringResource(R.string.emergency_continue),
        onPrimaryClick = onContinue,
    ) {
        FeatureBulletRow(
            icon = Icons.Filled.ErrorOutline,
            title = stringResource(R.string.emergency_intro_row1_title),
            subtitle = stringResource(R.string.emergency_intro_row1_body),
        )
        FeatureBulletRow(
            icon = Icons.Filled.Group,
            title = stringResource(R.string.emergency_intro_row2_title),
            subtitle = stringResource(R.string.emergency_intro_row2_body),
        )
        FeatureBulletRow(
            icon = Icons.Filled.NearMe,
            title = stringResource(R.string.emergency_intro_row3_title),
            subtitle = stringResource(R.string.emergency_intro_row3_body),
        )
    }
}

@Composable
private fun TrustPage(onContinue: () -> Unit) {
    OnboardingIntroScaffold(
        title = stringResource(R.string.emergency_trust_title),
        subtitle = stringResource(R.string.emergency_trust_subtitle),
        primaryLabel = stringResource(R.string.emergency_continue),
        onPrimaryClick = onContinue,
    ) {
        // The three icons are TrustLevel's own badges, so the explainer and the badge on a
        // Sakhi's card cannot drift apart.
        FeatureBulletRow(
            icon = Icons.Filled.Spa,
            title = stringResource(R.string.emergency_trust_row1_title),
            subtitle = stringResource(R.string.emergency_trust_row1_body),
        )
        FeatureBulletRow(
            icon = Icons.Filled.Favorite,
            title = stringResource(R.string.emergency_trust_row2_title),
            subtitle = stringResource(R.string.emergency_trust_row2_body),
        )
        FeatureBulletRow(
            icon = Icons.Filled.WorkspacePremium,
            title = stringResource(R.string.emergency_trust_row3_title),
            subtitle = stringResource(R.string.emergency_trust_row3_body),
        )
    }
}

/**
 * Continue stays enabled whether or not she grants anything. Neither permission is a gate:
 * the feature asks, it does not demand, and she can reach the map and read it without
 * either. Blocking the button here would turn a request into a toll.
 */
@Composable
private fun PermissionsPage(
    locationGranted: Boolean,
    notificationsGranted: Boolean,
    onRequestLocation: () -> Unit,
    onRequestNotifications: () -> Unit,
    onContinue: () -> Unit,
) {
    OnboardingIntroScaffold(
        title = stringResource(R.string.emergency_permissions_title),
        subtitle = stringResource(R.string.emergency_permissions_subtitle),
        primaryLabel = stringResource(R.string.emergency_continue),
        onPrimaryClick = onContinue,
    ) {
        Surface(
            shape = RoundedCornerShape(SakhiRadiusLg),
            // iOS fills this card with `DS.Colors.systemBackground` -- white -- over the
            // page's own pink ground. `colorScheme.surface` maps to brand.lightPink here,
            // so reaching for that would give the card no edge against the page.
            color = sakhiSystemBackground(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                PermissionRow(
                    title = stringResource(R.string.emergency_permission_location),
                    isOn = locationGranted,
                    onGrant = onRequestLocation,
                )
                // iOS: `Divider().padding(.leading, DS.Spacing.m)`. Without it the two rows
                // read as one tall block.
                SakhiListDivider(startInset = SakhiSpacing.space4)
                PermissionRow(
                    title = stringResource(R.string.emergency_permission_notifications),
                    isOn = notificationsGranted,
                    onGrant = onRequestNotifications,
                )
            }
        }
    }
}

/**
 * A toggle she taps to grant, never to revoke. Android only lets an app *ask*, so once
 * granted the switch stays on and does nothing; taking it back is done in Settings.
 * A switch that appeared to turn a permission off would be a control that does not work.
 */
@Composable
private fun PermissionRow(title: String, isOn: Boolean, onGrant: () -> Unit) {
    Row(
        // iOS `PermissionToggleRow`: `.padding(.horizontal, DS.Spacing.m)` (16) and
        // `.padding(.vertical, DS.Spacing.ml)` (20). Android had 16/12, which made these
        // rows visibly shorter than the same component's on every Care screen.
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // iOS: `.lato(15)`, not the 16sp `bodyLarge` this used to take.
            text = title,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        SakhiSwitch(
            checked = isOn,
            onCheckedChange = { wants -> if (wants) onGrant() },
            enabled = !isOn,
            // A granted permission is ON, and it must look it. Material's disabled
            // treatment washes the track down to 40% alpha, so the one permission she had
            // actually granted read as the greyed-out row -- reported by Karan as "jo
            // toggle hai vo bhi disable lag raha hai". iOS's `.disabled(isOn)` dims far
            // more gently than this, so the closer match is not to dim at all: the switch
            // is inert rather than unavailable.
            dimWhenDisabled = false,
        )
    }
}

private val SakhiRadiusLg = 16.dp
