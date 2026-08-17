package team.sakhi.android.feature.emergency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.emergency.EmergencyState
import team.sakhi.models.EmergencyFormatting

/**
 * Step 4 — she has asked one Sakhi and is waiting on the answer.
 *
 * `main`'s `seekerRequestedHelp`, whose status string was literally "Waiting for
 * Acceptance". One request, one named Sakhi, so this screen says who: a screen that said
 * "asking women near you" would be describing a broadcast the feature no longer does.
 *
 * The answer arrives over Realtime rather than a poll. She owns her own request row, so
 * the server can push her the status change the moment it happens; the Sakhi she asked has
 * no read on that row until she accepts, which is why *her* side polls instead.
 */
@Composable
internal fun EmergencyWaitingStep(
    viewModel: EmergencyViewModel,
    step: EmergencyState.WaitingForAcceptance,
) {
    var showCancelConfirm by remember { mutableStateOf(false) }
    val helperName = step.helperName?.trim()?.split(" ")?.firstOrNull().orEmpty()
        .ifEmpty { stringResource(R.string.emergency_a_sakhi_nearby) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = SakhiSpacing.space5),
    ) {
        EmergencyHeader(
            title = stringResource(R.string.emergency_waiting_for, helperName),
            subtitle = stringResource(R.string.emergency_waiting_for_subtitle),
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                EmergencyPulse()

                Text(
                    text = step.helperName ?: stringResource(R.string.emergency_a_sakhi_nearby),
                    style = MaterialTheme.typography.titleMedium,
                )

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = SakhiSpacing.space3,
                            vertical = SakhiSpacing.space1,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                    ) {
                        Icon(
                            imageVector = step.requirement.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = EmergencyFormatting.requirementShortName(step.requirement),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                step.spotLabel?.takeIf { it.isNotBlank() }?.let { spot ->
                    Text(
                        text = spot,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        TextButton(
            onClick = { showCancelConfirm = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.emergency_cancel_request),
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.size(SakhiSpacing.space3))
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(stringResource(R.string.emergency_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.emergency_cancel_confirm_body, helperName)) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    viewModel.cancelRequest(step.requestId)
                }) {
                    Text(
                        text = stringResource(R.string.emergency_cancel_request),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text(stringResource(R.string.emergency_keep_waiting))
                }
            },
        )
    }
}

/**
 * `main`'s `helperRejected`, as its own screen.
 *
 * Being told plainly is the point. A decline that looked like silence would leave her
 * waiting out a fifteen minute expiry on an answer that already came, which is the worst
 * possible outcome for someone who needs something right now.
 */
@Composable
internal fun EmergencyRejectedStep(
    viewModel: EmergencyViewModel,
    step: EmergencyState.Rejected,
    onExit: () -> Unit,
) {
    val helperName = step.helperName?.trim()?.split(" ")?.firstOrNull().orEmpty()
        .ifEmpty { stringResource(R.string.emergency_she) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = SakhiSpacing.space5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.Filled.PersonOff,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.error,
        )

        // main: configureSeekerUI(.helperRejected) — "Request Declined" tinted red, with
        // "{name} declined your request" underneath.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        ) {
            Text(
                text = stringResource(R.string.emergency_request_declined),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(
                    horizontal = SakhiSpacing.space4,
                    vertical = SakhiSpacing.space2,
                ),
            )
        }

        Text(
            text = stringResource(R.string.emergency_declined_body, helperName),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.emergency_declined_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1f))

        // main's primary button on this status was "Find Someone Else".
        Button(
            onClick = viewModel::askSomeoneElse,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.emergency_find_someone_else),
                modifier = Modifier.padding(vertical = SakhiSpacing.space2),
            )
        }

        TextButton(onClick = {
            viewModel.dismiss()
            onExit()
        }) {
            Text(stringResource(R.string.emergency_exit))
        }

        Spacer(modifier = Modifier.size(SakhiSpacing.space3))
    }
}
