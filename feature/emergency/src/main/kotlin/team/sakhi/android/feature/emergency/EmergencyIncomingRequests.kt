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
import androidx.compose.ui.text.font.FontWeight
import team.sakhi.android.designsystem.sakhiDeepRose
import team.sakhi.android.designsystem.sakhiGroupedBackground
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.IncomingRequest
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemGray5
import team.sakhi.android.designsystem.sakhiLightPink
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import kotlinx.datetime.Clock
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.android.ui.SakhiSwitch
import team.sakhi.design.SakhiUIColors

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
            .heightIn(min = 300.dp, max = 640.dp),
    ) {
        AvailabilityHeader(
            isAvailable = responder.isAvailable,
            onToggle = viewModel::setAvailable,
        )

        responder.lastError?.let { error ->
            ErrorNotice(message = error, onDismiss = viewModel::clearResponderError)
        }

        // A request that names her is shown first, whatever the availability flag says.
        // That flag is local and has been wrong -- it defaulted to off on every launch --
        // and a woman who had been asked was shown "you are off" with the request hidden
        // behind it. If someone asked her, she was findable; the request is the truth.
        if (responder.incoming.isEmpty() && !responder.isAvailable) {
            OffState()
        } else if (responder.incoming.isEmpty()) {
            EmptyNearby(isRefreshing = responder.isRefreshing)
        } else {
            // iOS: `.refreshable { await viewModel.refreshIncoming() }`. The list is polled
            // on a timer -- she has no read on a request row until she accepts, so there is
            // nothing the server could push her -- and without a pull she had no way to ask
            // for a check herself.
            PullToRefreshBox(
                isRefreshing = responder.isRefreshing,
                onRefresh = viewModel::refreshIncoming,
            ) {
                LazyColumn(modifier = Modifier.padding(bottom = SakhiSpacing.space5)) {
                    item {
                        EmergencySectionHeader(
                            title = stringResource(R.string.emergency_asking_you_now),
                        )
                    }
                    item {
                        EmergencyCard {
                            responder.incoming.forEachIndexed { index, request ->
                                if (index > 0) EmergencyRowDivider(leadingInset = 0.dp)
                                IncomingRequestCard(
                                    request = request,
                                    countdownSeconds = EmergencyIso8601.secondsUntil(
                                        request.expiresAtIso,
                                        Clock.System.now(),
                                    ),
                                    onAccept = { viewModel.acceptIncoming(request) },
                                    onDecline = { viewModel.declineIncoming(request) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Figma `EA-13` -> `header`: the question, the state under it, and the switch on the right.
 *
 * It used to be a grey card with a 40dp location glyph inside it. Being findable is the one
 * decision on this screen, and wrapping it in a panel made it read as a setting she had
 * scrolled past rather than the thing the screen is for.
 */
@Composable
private fun AvailabilityHeader(isAvailable: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space5)
            .padding(top = SakhiSpacing.space1, bottom = SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(R.string.emergency_help_someone_nearby),
                fontSize = 20.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
            )
            Text(
                text = if (isAvailable) {
                    stringResource(R.string.emergency_available_to_help)
                } else {
                    stringResource(R.string.emergency_location_not_shared)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
            )
        }
        SakhiSwitch(checked = isAvailable, onCheckedChange = onToggle)
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
            color = sakhiSecondaryLabel(),
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
            color = sakhiSecondaryLabel(),
        )
        Text(
            text = stringResource(R.string.emergency_we_will_keep_checking),
            style = MaterialTheme.typography.bodySmall,
            color = sakhiSecondaryLabel(),
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
    countdownSeconds: Long,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val faceIndex = remember(request.requesterId) {
        EmergencyAvatarCatalog.dealtIndex(request.requesterId)
    }

    // Figma `request`: `px-16 py-14`, 12 between the two halves. One white card inside the
    // section, like every other list in the flow -- it was a grey panel with a 44dp
    // requirement badge on the right and the two buttons in flat red and green.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space4, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            // The illustrated avatar, never her photograph: this card is on screen before
            // anyone has accepted anything.
            Image(
                painter = painterResource(EmergencyAvatarCatalog.drawableAt(faceIndex)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .scale(EmergencyAvatarCatalog.contentScaleAt(faceIndex)),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.emergency_she_needs,
                        EmergencyFormatting.requirementLabel(request.requirement).lowercase(),
                    ),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = sakhiLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${EmergencyFormatting.approximateDistance(request.distanceBucketMeters)} · " +
                        EmergencyFormatting.walkingTime(request.etaMinutes),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = sakhiSecondaryLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // How long she has to answer. Without it, Decline and a request that simply
            // expired look identical from here.
            countdownSeconds.takeIf { it > 0 }?.let { seconds ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            SakhiUIColors.BRAND_PINK.toComposeColor().copy(alpha = 0.12f),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${seconds}s",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = SakhiUIColors.BRAND_PINK.toComposeColor(),
                    )
                }
            }
        }

        // Figma `actions`: two equal pills 10 apart -- Decline a flat neutral, Accept the
        // brand fill. `main` had them in flat red and green, which put the loudest colour
        // in the app on the button that does nothing for the woman asking.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IncomingAction(
                title = stringResource(R.string.emergency_decline),
                container = sakhiSecondaryLabel().copy(alpha = 0.08f),
                content = sakhiSecondaryLabel(),
                onClick = onDecline,
                modifier = Modifier.weight(1f),
            )
            IncomingAction(
                title = stringResource(R.string.emergency_accept),
                container = SakhiUIColors.BRAND_PINK.toComposeColor(),
                content = Color.White,
                onClick = onAccept,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One of the two pills on an incoming request. Figma: `py-12`, capsule, Bold 17. */
@Composable
private fun IncomingAction(
    title: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            color = content,
        )
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
