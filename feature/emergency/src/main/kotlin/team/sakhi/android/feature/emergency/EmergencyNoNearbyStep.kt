package team.sakhi.android.feature.emergency

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import team.sakhi.android.designsystem.SakhiSpacing

/**
 * "No Nearby Sakhis" — the Android half of `NoNearByViewController`.
 *
 * Same structure as the original's two rows: a 230dp logo/heading/description block over
 * an 80dp button row. The icon fades and scales in from 0.8 with a spring, slowly and on a
 * short delay, because an empty result should read as "still looking" rather than as a
 * failure. Copy is verbatim from that file.
 */
@Composable
internal fun EmergencyNoNearbyStep(
    viewModel: EmergencyViewModel,
) {
    var visible by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow),
        label = "no-nearby-alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow),
        label = "no-nearby-scale",
    )

    LaunchedEffect(Unit) {
        delay(100)  // the original's 0.1s beat before animateLogo()
        visible = true
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = SakhiSpacing.space5),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(230.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PersonOff,
                contentDescription = null,
                modifier = Modifier.size(54.dp).alpha(alpha).scale(scale),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(SakhiSpacing.space3))
            Text(
                text = stringResource(R.string.emergency_no_nearby_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(SakhiSpacing.space2))
            Text(
                text = stringResource(R.string.emergency_no_nearby_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Button(
            onClick = {
                if (isSearching) return@Button
                isSearching = true
                viewModel.searchAgain { isSearching = false }
            },
            enabled = !isSearching,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.emergency_search_again))
            }
        }

        // iOS's `EmergencyNoNearbyView` has exactly one action, "Search Again". The
        // safe-places link that used to sit here was removed there and is removed here for
        // the same reason, so the empty state reads the same on both platforms.
    }
}
