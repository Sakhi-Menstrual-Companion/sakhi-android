package team.sakhi.android.feature.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.EmergencyProfileDetail

/**
 * Replica of `ProfielDetailViewController` — the card a woman opens before deciding to let
 * someone walk over to her.
 *
 * The original laid this out as a grouped table with 11pt corners: a "Requests" section
 * with "Helped Other" and "Requested Help" (both `person.2.fill`), a "Last Active" section
 * (`clock`), and a "Block" section.
 *
 * Nothing here is a position. Deciding whether to trust someone does not require knowing
 * where she is, so the server does not send it.
 */
@Composable
internal fun EmergencyProfileDetailSheet(
    viewModel: EmergencyViewModel,
    profile: EmergencyProfileDetail,
    onDismiss: () -> Unit,
) {
    var showBlockConfirm by remember { mutableStateOf(false) }
    val trust = EmergencyFormatting.trustLevel(profile.ratingCount)
    val trustColor = Color(0xFF000000 or (trust.colorHex.removePrefix("#").toLongOrNull(16) ?: 0))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmergencyAvatar(name = profile.name, photoUrl = profile.photoUrl, size = 72.dp)

        Text(
            text = profile.name ?: stringResource(R.string.emergency_a_sakhi_nearby),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(text = trust.displayName, style = MaterialTheme.typography.labelLarge, color = trustColor)

        ProfileSection(stringResource(R.string.emergency_profile_requests)) {
            ProfileRow(Icons.Filled.Group, stringResource(R.string.emergency_profile_helped), profile.helpedCount.toString())
            HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
            ProfileRow(Icons.Filled.Group, stringResource(R.string.emergency_profile_requested), profile.requestedCount.toString())
        }

        ProfileSection(stringResource(R.string.emergency_profile_last_active)) {
            ProfileRow(
                Icons.Filled.Schedule,
                stringResource(R.string.emergency_profile_last_active),
                profile.lastActiveIso ?: stringResource(R.string.emergency_profile_unknown),
            )
        }

        ProfileSection(stringResource(R.string.emergency_profile_block)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBlockConfirm = true }
                    .padding(SakhiSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = if (profile.isBlocked) {
                        stringResource(R.string.emergency_unblock)
                    } else {
                        stringResource(R.string.emergency_block)
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = {
                Text(
                    if (profile.isBlocked) stringResource(R.string.emergency_unblock)
                    else stringResource(R.string.emergency_block)
                )
            },
            text = {
                Text(
                    if (profile.isBlocked) stringResource(R.string.emergency_unblock_body)
                    else stringResource(R.string.emergency_block_body)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBlockConfirm = false
                    viewModel.setBlocked(profile.userId, !profile.isBlocked)
                    if (!profile.isBlocked) onDismiss()
                }) {
                    Text(
                        text = if (profile.isBlocked) {
                            stringResource(R.string.emergency_unblock)
                        } else {
                            stringResource(R.string.emergency_block)
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text(stringResource(R.string.emergency_keep_waiting))
                }
            },
        )
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(11.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
