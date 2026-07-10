package team.sakhi.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiSpacing

/**
 * Non-dismissable gate -- port of iOS `ForceUpdateView`. Shown in place of the
 * app's normal content (never as a cancellable dialog/sheet) whenever
 * `UpdateGateController.state.forceUpdate` is true: the installed version is
 * below the remote `app_update_policies.minimum_supported_version` row.
 */
@Composable
fun ForceUpdateScreen(
    title: String,
    message: String,
    onUpdateClick: () -> Unit,
    onSupportClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val supportDescription = stringResource(R.string.force_update_contact_support)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onSupportClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(SakhiSpacing.space4)
                    .semantics { contentDescription = supportDescription },
            ) {
                Icon(imageVector = Icons.Filled.Headphones, contentDescription = null)
            }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(SakhiSpacing.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = SakhiSpacing.space4),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SakhiSpacing.space2, bottom = SakhiSpacing.space6),
            )
            PrimaryButton(
                text = stringResource(R.string.force_update_update_now),
                onClick = onUpdateClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space4)
                    .height(56.dp),
            )
        }
        }
    }
}
