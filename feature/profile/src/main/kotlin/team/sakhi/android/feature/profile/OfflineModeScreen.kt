package team.sakhi.android.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.rememberSakhiFlingBehavior
import team.sakhi.android.ui.BackButton
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.designsystem.sakhiGroupedBackground
import team.sakhi.android.designsystem.sakhiSystemGray5

/**
 * "Use Sakhi offline" — port of iOS's `OfflineModeSheet.swift`.
 *
 * Copy, section labels and the two capability lists are transcribed from the Swift
 * source rather than rewritten, so both platforms make the user the same promises about
 * what keeps working and what pauses.
 */
@Composable
fun OfflineModeScreen(
    onClose: () -> Unit,
    viewModel: OfflineModeViewModel = koinViewModel(),
) {
    val isActivating by viewModel.isActivating.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()

    LaunchedEffect(finished) {
        if (finished) onClose()
    }

    if (isActivating) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val columnScope: ColumnScope = this
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SakhiSpacing.space6, end = SakhiSpacing.space6, top = SakhiSpacing.space5),
        ) {
            BackButton(onClick = onClose)
        }

        Column(
            modifier = with(columnScope) { Modifier.weight(1f) }
                .verticalScroll(rememberScrollState(), flingBehavior = rememberSakhiFlingBehavior())
                .padding(horizontal = SakhiSpacing.space6),
        ) {
            Text(
                text = stringResource(R.string.offline_mode_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = SakhiSpacing.space5),
            )
            Text(
                text = stringResource(R.string.offline_mode_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
                modifier = Modifier.padding(top = SakhiSpacing.space2),
            )

            CapabilitySection(
                label = stringResource(R.string.offline_mode_stays_label),
                tint = MaterialTheme.colorScheme.primary,
                icon = Icons.Rounded.Check,
                items = listOf(
                    stringResource(R.string.offline_mode_stays_logging),
                    stringResource(R.string.offline_mode_stays_calendar),
                    stringResource(R.string.offline_mode_stays_predictions),
                    stringResource(R.string.offline_mode_stays_reports),
                ),
            )
            CapabilitySection(
                label = stringResource(R.string.offline_mode_pauses_label),
                tint = MaterialTheme.colorScheme.error,
                icon = Icons.Rounded.Pause,
                items = listOf(
                    stringResource(R.string.offline_mode_pauses_backup),
                    stringResource(R.string.offline_mode_pauses_devices),
                    stringResource(R.string.offline_mode_pauses_care),
                    stringResource(R.string.offline_mode_pauses_chat),
                ),
            )

            PrivacyNote()
        }

        SakhiFooter(
            primaryLabel = stringResource(R.string.offline_mode_primary),
            onPrimaryClick = viewModel::activateOfflineMode,
            secondaryLabel = stringResource(R.string.offline_mode_secondary),
            onSecondaryClick = onClose,
            primaryEnabled = !isActivating,
            secondaryEnabled = !isActivating,
        )
    }
}

@Composable
private fun CapabilitySection(
    label: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<String>,
) {
    Column(modifier = Modifier.padding(top = SakhiSpacing.space6)) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiSecondaryLabel(),
            modifier = Modifier.padding(start = SakhiSpacing.space4, bottom = SakhiSpacing.space2),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    sakhiGroupedBackground().copy(alpha = 0.35f),
                    RoundedCornerShape(SakhiRadius.lg),
                ),
        ) {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SakhiSpacing.space4, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(text = item, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PrivacyNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SakhiSpacing.space6, bottom = SakhiSpacing.space8)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                RoundedCornerShape(SakhiRadius.lg),
            )
            .padding(SakhiSpacing.space4),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.offline_mode_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = sakhiSecondaryLabel(),
        )
    }
}
