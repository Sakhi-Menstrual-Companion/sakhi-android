package team.sakhi.android.feature.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.NearbyRequest

/**
 * The other side of the network — being the Sakhi someone finds.
 *
 * Being findable is opt-in, off by default, and reversible in one tap. The list refreshes
 * on a timer rather than arriving over Realtime, and that is not a shortcut: a responder
 * has no read access to open requests until she is accepted, precisely so a requester's
 * coordinates stay hidden, so there is nothing the server could push her. Polling a
 * function that returns coarsened distances is the honest version of this feature.
 */
@Composable
internal fun EmergencyResponderInbox(viewModel: EmergencyViewModel) {
    val responder by viewModel.responderState.collectAsStateWithLifecycle()

    DisposableEffect(responder.isAvailable) {
        if (responder.isAvailable) viewModel.startNearbyPolling()
        onDispose { viewModel.stopNearbyPolling() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space5)
            .heightIn(min = 300.dp, max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        EmergencyHeader(
            title = stringResource(R.string.emergency_help_someone_nearby),
            subtitle = stringResource(R.string.emergency_help_someone_nearby_subtitle),
        )

        AvailabilityToggle(
            isAvailable = responder.isAvailable,
            onToggle = viewModel::setAvailable,
        )

        responder.lastError?.let { error ->
            ErrorNotice(message = error, onDismiss = viewModel::clearResponderError)
        }

        if (!responder.isAvailable) {
            OffState()
        } else if (responder.nearbyRequests.isEmpty()) {
            EmptyNearby(isRefreshing = responder.isRefreshing)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                modifier = Modifier.padding(bottom = SakhiSpacing.space5),
            ) {
                items(responder.nearbyRequests, key = { it.requestId }) { request ->
                    NearbyRequestCard(
                        request = request,
                        onOffer = { viewModel.offerHelp(request) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AvailabilityToggle(isAvailable: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(SakhiRadius.md))
                    .background(
                        if (isAvailable) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isAvailable) Icons.Filled.LocationOn else Icons.Filled.LocationOff,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isAvailable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.emergency_available_to_help),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (isAvailable) {
                        stringResource(R.string.emergency_location_shared_roughly)
                    } else {
                        stringResource(R.string.emergency_location_not_shared)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = isAvailable, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun OffState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = SakhiSpacing.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Icon(
            imageVector = Icons.Filled.VolunteerActivism,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        )
        Text(
            text = stringResource(R.string.emergency_turn_on_to_see),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyNearby(isRefreshing: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = SakhiSpacing.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(R.string.emergency_no_one_needs_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.emergency_we_will_keep_checking),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Shows what she needs and roughly how far. No name, no spot, no position — that is all
 * the server will give out before the requester has picked someone.
 */
@Composable
private fun NearbyRequestCard(request: NearbyRequest, onOffer: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = request.requirement.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.emergency_someone_needs,
                        EmergencyFormatting.requirementLabel(request.requirement).lowercase(),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${EmergencyFormatting.approximateDistance(request.distanceBucketMeters)} · " +
                        EmergencyFormatting.walkingTime(request.etaMinutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (request.alreadyOffered) {
                Text(
                    text = stringResource(R.string.emergency_offered),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Button(onClick = onOffer, shape = CircleShape) {
                    Text(stringResource(R.string.emergency_i_can_help))
                }
            }
        }
    }
}

@Composable
private fun ErrorNotice(message: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.md),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.emergency_dismiss),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
