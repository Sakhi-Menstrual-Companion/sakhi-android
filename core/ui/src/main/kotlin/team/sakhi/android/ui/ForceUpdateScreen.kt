package team.sakhi.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

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
 * cross that does nothing is worse than no cross. The bar is still drawn, empty on the left,
 * so the screen starts at the same height as every other intro instead of reading as a
 * different app.
 *
 * Support sits in the top right, the one other door on this screen, matching iOS
 * `UpdateView.supportButton` (Karan, 2026-09-20).
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
        icon = Icons.Filled.AutoAwesome,
        title = title,
        message = message,
        points = listOf(
            SakhiOnboardingPoint(
                icon = Icons.Filled.AutoAwesome,
                title = stringResource(R.string.force_update_point_1_title),
                detail = stringResource(R.string.force_update_point_1_detail),
            ),
            SakhiOnboardingPoint(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.force_update_point_2_title),
                detail = stringResource(R.string.force_update_point_2_detail),
            ),
            SakhiOnboardingPoint(
                icon = Icons.Filled.Schedule,
                title = stringResource(R.string.force_update_point_3_title),
                detail = stringResource(R.string.force_update_point_3_detail),
            ),
        ),
        primaryLabel = stringResource(R.string.force_update_update_now),
        onPrimaryClick = onUpdateClick,
        modifier = modifier,
        keepsTopBar = true,
        topTrailing = {
            val supportLabel = stringResource(R.string.force_update_contact_support)
            IconButton(onClick = onSupportClick) {
                Icon(
                    imageVector = Icons.Filled.Headphones,
                    contentDescription = supportLabel,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}
