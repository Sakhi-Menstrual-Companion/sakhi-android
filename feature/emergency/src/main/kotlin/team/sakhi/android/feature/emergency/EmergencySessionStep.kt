package team.sakhi.android.feature.emergency

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.emergency.EmergencyState
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.EmergencyMessage
import team.sakhi.models.EmergencySession

/**
 * Step 4 — the two women are connected.
 *
 * **No route is drawn here, and none should be added.**
 *
 * The original build rendered a live path between the two people. It was tested at the iOS
 * Development Centre on 2025-02-14 and failed: indoor GPS is not accurate enough for the
 * line to mean anything, and a confidently wrong path is worse than no path when someone
 * is trying to find you. The decision recorded in 01-HQ/01-AI/Timeline.md was to show time
 * and distance only, and the server returns no geometry at all to back that up.
 *
 * What she gets instead: who is coming, how far, how long that walk takes, the spot label
 * in plain words, and a way to talk.
 */
@Composable
internal fun EmergencySessionStep(
    viewModel: EmergencyViewModel,
    step: EmergencyState.InSession,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showCompleteConfirm by remember { mutableStateOf(false) }

    val session = step.session
    val messages = step.messages
    val currentUserId = if (session.viewerIsRequester) session.requesterId else session.responderId

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Header ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(
                horizontal = SakhiSpacing.space5,
                vertical = SakhiSpacing.space3,
            ),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                EmergencyAvatar(
                    name = session.counterpartName,
                    photoUrl = session.counterpartPhotoUrl,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.counterpartName
                            ?: stringResource(R.string.emergency_your_sakhi),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (session.viewerIsRequester) {
                            stringResource(R.string.emergency_on_her_way)
                        } else {
                            stringResource(R.string.emergency_needs_your_help)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showCompleteConfirm = true }) {
                    Text(stringResource(R.string.emergency_done))
                }
            }

            // Two plain numbers. This is what replaced the route.
            Row(horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                Metric(
                    icon = Icons.Filled.LocationOn,
                    value = EmergencyFormatting.exactDistance(session.distanceMeters),
                    caption = stringResource(R.string.emergency_apart),
                    modifier = Modifier.weight(1f),
                )
                Metric(
                    icon = Icons.Filled.DirectionsWalk,
                    value = EmergencyFormatting.walkingTime(session.etaMinutes),
                    caption = stringResource(R.string.emergency_roughly),
                    modifier = Modifier.weight(1f),
                )
            }

            if (!session.viewerIsRequester) {
                WhereToGo(session = session, onOpenMaps = { openWalkingDirections(context, session) })
            }
        }

        HorizontalDivider()

        // ── Thread ───────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                Text(
                    text = stringResource(R.string.emergency_say_hello),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = SakhiSpacing.space6),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = SakhiSpacing.space5),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    vertical = SakhiSpacing.space3,
                ),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isMine = message.senderId == currentUserId,
                        senderName = if (message.senderId == currentUserId) {
                            stringResource(R.string.emergency_you)
                        } else {
                            session.counterpartName?.substringBefore(' ')
                                ?: stringResource(R.string.emergency_your_sakhi)
                        },
                    )
                }
            }
        }

        // ── Input ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            OutlinedTextField(
                value = uiState.messageDraft,
                onValueChange = viewModel::onMessageDraftChanged,
                placeholder = { Text(stringResource(R.string.emergency_message_hint)) },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = viewModel::sendMessage,
                enabled = uiState.messageDraft.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.emergency_send),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (showCompleteConfirm) {
        AlertDialog(
            onDismissRequest = { showCompleteConfirm = false },
            title = {
                Text(
                    if (session.viewerIsRequester) {
                        stringResource(R.string.emergency_did_she_reach_you)
                    } else {
                        stringResource(R.string.emergency_all_done)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCompleteConfirm = false
                    viewModel.completeSession()
                }) {
                    Text(stringResource(R.string.emergency_yes_we_are_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteConfirm = false }) {
                    Text(stringResource(R.string.emergency_not_yet))
                }
            },
        )
    }
}

@Composable
private fun Metric(
    icon: ImageVector,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(text = value, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The responder's actual instructions. The spot label does more work here than any map
 * would: it is the part GPS cannot give you.
 */
@Composable
private fun WhereToGo(session: EmergencySession, onOpenMaps: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.md),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(SakhiSpacing.space3)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            ) {
                Icon(
                    imageVector = session.requirement.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(
                        R.string.emergency_she_needs,
                        EmergencyFormatting.requirementLabel(session.requirement).lowercase(),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            session.spotLabel?.takeIf { it.isNotBlank() }?.let { spot ->
                Text(text = spot, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onOpenMaps, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(stringResource(R.string.emergency_open_in_maps))
            }
        }
    }
}

/**
 * Bubble plus the sender name above it.
 *
 * `main` used MessageKit with a 16dp `messageTopLabel` showing the sender, and coloured
 * bubbles pink for the current user, gray for the other person.
 */
@Composable
private fun MessageBubble(message: EmergencyMessage, isMine: Boolean, senderName: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
    Text(
        text = senderName,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(16.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isMine) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isMine) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(
                    horizontal = SakhiSpacing.space3,
                    vertical = SakhiSpacing.space2,
                ),
            )
        }
    }
    }
}

/**
 * Hands off to a maps app for the walk. Sakhi shows a number; a maps app is the right
 * place for turn-by-turn, and it is honest about its own accuracy.
 */
private fun openWalkingDirections(context: android.content.Context, session: EmergencySession) {
    val uri = Uri.parse(
        "google.navigation:q=${session.location.latitude},${session.location.longitude}&mode=w",
    )
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
        return
    }
    // No Google Maps on the device — fall back to whatever handles a geo: URI.
    val fallback = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:${session.location.latitude},${session.location.longitude}"),
    )
    if (fallback.resolveActivity(context.packageManager) != null) {
        context.startActivity(fallback)
    }
}
