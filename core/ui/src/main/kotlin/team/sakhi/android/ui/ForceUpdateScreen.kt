package team.sakhi.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * Non-dismissable gate -- port of iOS `ForceUpdateView`. Shown in place of the app's normal
 * content (never as a cancellable dialog or sheet) whenever
 * `UpdateGateController.state.forceUpdate` is true: the installed version is below the
 * remote `app_update_policies.minimum_supported_version` row.
 *
 * Rebuilt on [SakhiOnboardingView], the app's one intro template, so a screen she has never
 * seen before looks like the rest of the app rather than like an error.
 *
 * The [title] and [message] are still the remote copy, unchanged, and the points are the
 * three things she actually wants to know when an app stops working: her data is safe, this
 * is quick, and there is a person if it goes wrong.
 *
 * NO close button, deliberately. There is nothing behind this screen to go back to, and a
 * cross that does nothing is worse than no cross. Support moved from a bare icon in the top
 * corner to the secondary action, where every other way out in the app lives.
 */
@Composable
fun ForceUpdateScreen(
    title: String,
    message: String,
    onUpdateClick: () -> Unit,
    onSupportClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    SakhiOnboardingView(
        icon = Icons.Filled.SystemUpdate,
        title = title,
        message = message,
        points = listOf(
            SakhiOnboardingPoint(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.force_update_point_1_title),
                detail = stringResource(R.string.force_update_point_1_detail),
            ),
            SakhiOnboardingPoint(
                icon = Icons.Filled.Schedule,
                title = stringResource(R.string.force_update_point_2_title),
                detail = stringResource(R.string.force_update_point_2_detail),
            ),
            SakhiOnboardingPoint(
                icon = Icons.Filled.Headphones,
                title = stringResource(R.string.force_update_point_3_title),
                detail = stringResource(R.string.force_update_point_3_detail),
            ),
        ),
        primaryLabel = stringResource(R.string.force_update_update_now),
        onPrimaryClick = onUpdateClick,
        secondaryLabel = stringResource(R.string.force_update_contact_support_action),
        onSecondaryClick = onSupportClick,
        modifier = modifier,
    )
}
