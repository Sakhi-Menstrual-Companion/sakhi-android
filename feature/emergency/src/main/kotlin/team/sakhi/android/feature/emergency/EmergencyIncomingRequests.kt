package team.sakhi.android.feature.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.IncomingRequest

/**
 * The other side of the network — being the Sakhi someone asked.
 *
 * This is not a feed of everyone nearby who needs something, and it must not become one.
 * On `main` a helper only ever saw a request that named her, and answered it with Accept
 * or Decline. Migration 036 put that back after a rebuild had turned it into a broadcast.
 *
 * Being findable is opt-in, off by default, and reversible in one tap. The list refreshes
 * on a timer rather than arriving over Realtime, and that is not a shortcut: she has no
 * read access to the request row until she accepts it, precisely so the requester's
 * coordinates stay hidden, so there is nothing the server could push her. A push
 * notification covers the case where the app is closed.
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
        } else if (responder.incoming.isEmpty()) {
            EmptyNearby(isRefreshing = responder.isRefreshing)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                modifier = Modifier.padding(bottom = SakhiSpacing.space5),
            ) {
                items(responder.incoming, key = { it.requestId }) { request ->
                    IncomingRequestCard(
                        request = request,
                        onAccept = { viewModel.acceptIncoming(request) },
                        onDecline = { viewModel.declineIncoming(request) },
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
 * Who is asking, what she needs, and roughly how far. No spot label and no position: that
 * is the accept-gate, and it is the one place this build deliberately does not follow
 * `main`, which handed the helper exact coordinates the moment the request arrived.
 */
@Composable
private fun IncomingRequestCard(
    request: IncomingRequest,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val trust = EmergencyFormatting.trustLevel(request.ratingCount)
    val trustColor = Color(0xFF000000 or (trust.colorHex.removePrefix("#").toLongOrNull(16) ?: 0))

    Surface(
        shape = RoundedCornerShape(SakhiRadius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SakhiSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                EmergencyAvatar(name = request.requesterName, photoUrl = request.requesterPhotoUrl)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.requesterName
                            ?: stringResource(R.string.emergency_a_sakhi_nearby),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = trust.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = trustColor,
                    )
                }

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
            }

            Text(
                text = stringResource(
                    R.string.emergency_she_needs,
                    EmergencyFormatting.requirementLabel(request.requirement).lowercase(),
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "${EmergencyFormatting.approximateDistance(request.distanceBucketMeters)} · " +
                    EmergencyFormatting.walkingTime(request.etaMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // main: ProgressButtonView's helper UI — Reject in red on the left, Accept in
            // green on the right, both 50dp tall with 12dp corners and 16dp between.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onDecline,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) {
                    Text(stringResource(R.string.emergency_decline))
                }
                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) {
                    Text(stringResource(R.string.emergency_accept))
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
