package team.sakhi.android.feature.care

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.android.ui.SakhiNavBar
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiLightPink
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel

/**
 * Shown to a care partner who taps the log button without permission — port of iOS's
 * `LogPermissionSheet`.
 *
 * The whole point is that she, and only she, grants this. The sheet cannot unlock
 * anything itself; it can only send a request she is free to ignore. Copy is
 * transcribed from the Swift source so neither platform softens that.
 */
@Composable
fun LogPermissionRequestSheet(
    primaryUserId: String,
    partnershipId: String,
    partnerName: String,
    onClose: () -> Unit,
    viewModel: LogPermissionViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loadingSlot: @Composable () -> Unit = { CareLoadingButton() }

    // iOS `LogPermissionSheet`: the way out (X, left) at the top, then the icon and the two
    // lines centred in the space between it and the footer.
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SakhiNavBar(onClose = onClose)
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(88.dp)
                .background(sakhiLightPink(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }

        Text(
            text = stringResource(R.string.care_log_permission_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiLabel(),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = SakhiSpacing.space8)
                .padding(top = SakhiSpacing.space6),
        )
        Text(
            text = stringResource(R.string.care_log_permission_subtitle),
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = sakhiSecondaryLabel(),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space8)
                .padding(top = SakhiSpacing.space2),
        )

        Spacer(modifier = Modifier.weight(1f))

        SakhiFooter(
            primaryLabel = if (uiState.requestSent) {
                stringResource(R.string.care_log_permission_request_sent)
            } else {
                stringResource(R.string.care_log_permission_request)
            },
            onPrimaryClick = {
                viewModel.sendRequest(
                    primaryUserId = primaryUserId,
                    partnershipId = partnershipId,
                    partnerName = partnerName,
                )
            },
            // Once sent there is nothing left to decline, so iOS drops the secondary
            // action entirely rather than leaving a dead "Not Now".
            secondaryLabel = if (uiState.requestSent) null else stringResource(R.string.care_log_permission_not_now),
            onSecondaryClick = if (uiState.requestSent) null else onClose,
            primaryEnabled = !uiState.requestSent && !uiState.isSending,
            primarySlot = if (uiState.isSending) loadingSlot else null,
            note = if (uiState.requestSent) stringResource(R.string.care_log_permission_sent_note) else null,
        )
    }
}
