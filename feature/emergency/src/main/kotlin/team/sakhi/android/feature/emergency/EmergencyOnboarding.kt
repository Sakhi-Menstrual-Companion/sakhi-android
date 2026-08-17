package team.sakhi.android.feature.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WorkspacePremium
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
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.ui.CloseButton
import team.sakhi.android.ui.FeatureBulletRow
import team.sakhi.android.ui.OnboardingHeaderTopGap
import team.sakhi.android.ui.OnboardingIntroScaffold
import team.sakhi.android.ui.OnboardingNavBarMinHeight
import team.sakhi.android.ui.SakhiNavBar
import team.sakhi.android.ui.SakhiSwitch

/**
 * The three-page introduction shown the first time she opens Emergency Assistance.
 *
 * This is the account onboarding screen, not a lookalike. It renders through the same
 * shell every one of those ~31 steps uses: safe-area insets consumed once at the top, the
 * shared [SakhiNavBar] for back/close, [OnboardingIntroScaffold] for the title, subtitle
 * and content, and [team.sakhi.android.ui.SakhiFooter] pinned at the bottom carrying
 * Continue. Those pieces moved into `core:ui` when this screen was built, so account
 * onboarding and this one cannot drift apart.
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

    // This is an overlay above Home rather than a route of its own, so it has to paint
    // its own opaque ground; without a fill the home screen reads straight through it.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(sakhiSystemBackground())
            // Insets applied once here for all three pages, matching `OnboardingFlowHost`.
            // Consuming them afterwards stops `SakhiFooter`'s own `navigationBarsPadding`
            // from applying the bottom inset a second time.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .consumeWindowInsets(WindowInsets.safeDrawing),
    ) {
        Spacer(modifier = Modifier.height(OnboardingHeaderTopGap))

        SakhiNavBar(
            modifier = Modifier.heightIn(min = OnboardingNavBarMinHeight),
            onBack = if (page > 0) ({ page-- }) else null,
            leading = if (page == 0) {
                {
                    CloseButton(onClick = onCancel)
                    Spacer(modifier = Modifier.weight(1f))
                }
            } else {
                null
            },
        )

        when (page) {
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
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                PermissionRow(
                    title = stringResource(R.string.emergency_permission_location),
                    isOn = locationGranted,
                    onGrant = onRequestLocation,
                )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        SakhiSwitch(
            checked = isOn,
            onCheckedChange = { wants -> if (wants) onGrant() },
            enabled = !isOn,
        )
    }
}

private val SakhiRadiusLg = 16.dp
