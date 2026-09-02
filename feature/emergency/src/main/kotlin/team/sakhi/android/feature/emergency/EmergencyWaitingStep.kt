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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.sakhiLightPink
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.emergency.EmergencyState
import team.sakhi.models.EmergencyFormatting
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemGray5
import team.sakhi.android.designsystem.sakhiSeparator
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.ui.SakhiAlertKind
import team.sakhi.android.ui.SakhiAlertSheet

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
    var isCancelling by remember { mutableStateOf(false) }
    val helperName = step.helperName?.trim()?.split(" ")?.firstOrNull().orEmpty()
        .ifEmpty { stringResource(R.string.emergency_her) }

    // Read from the request's own expiry, ticked once a second, so backgrounding the app
    // and coming back does not restart the clock. iOS uses a 1s Timer publisher.
    var remaining by remember(step.expiresAtIso) {
        mutableStateOf(EmergencyIso8601.secondsUntil(step.expiresAtIso, Clock.System.now()))
    }
    LaunchedEffect(step.expiresAtIso) {
        while (true) {
            remaining = EmergencyIso8601.secondsUntil(step.expiresAtIso, Clock.System.now())
            delay(1_000)
        }
    }

    // No `EmergencyHeader` here. iOS puts the name in the body under the ring and has no
    // header on this screen at all; a header repeating "Waiting for Anjali" above a ring
    // that already says so was Android's own addition.
    Column(modifier = Modifier.fillMaxSize()) {
        // Scrolls. At a medium detent the ring and the actions together are taller than the
        // sheet, and what got cut was the cancel button -- the one control on the screen.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The ring leads, on its own, with nothing competing beside it. It is the only
            // thing on this screen that is actually happening.
            EmergencyRequestedProfile(
                name = step.helperName,
                avatarId = step.helperId,
                modifier = Modifier.padding(top = SakhiSpacing.space2),
            )

            Text(
                text = step.helperName ?: stringResource(R.string.emergency_a_sakhi_nearby),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = SakhiSpacing.space1),
            )

            Text(
                text = stringResource(R.string.emergency_waiting_body),
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = SakhiSpacing.space5)
                    .padding(top = 2.dp),
            )

            if (remaining > 0) {
                // Says how long the waiting actually lasts. Standing somewhere uncomfortable
                // with no idea whether this is ten seconds or ten minutes is its own kind of
                // awful. Android had no countdown at all.
                Surface(
                    shape = CircleShape,
                    color = sakhiLightPink(),
                    modifier = Modifier.padding(top = SakhiSpacing.space2),
                ) {
                    Text(
                        text = stringResource(
                            R.string.emergency_left_to_answer,
                            EmergencyIso8601.countdown(remaining),
                        ),
                        // iOS uses `.monospacedDigit()` so the countdown does not jitter
                        // as the digits change width. `tnum` is the same thing here.
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            horizontal = SakhiSpacing.space2,
                            vertical = 5.dp,
                        ),
                    )
                }
            }

            // What she asked for, in a card rather than as loose chips stacked under the
            // name. Two facts belong together and read as a summary.
            Surface(
                shape = RoundedCornerShape(SakhiRadius.lg),
                // iOS wraps this in `EmergencyCard`, which fills with
                // `DS.Colors.systemBackground` -- white. A translucent grey read as a panel
                // on the pink ground rather than a card on it, and it did not match the
                // white cards every other step in the flow uses.
                color = sakhiSystemBackground(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space5)
                    .padding(top = SakhiSpacing.space4),
            ) {
                Column {
                    WaitingSummaryRow(
                        icon = step.requirement.icon(),
                        accent = step.requirement.accentColor(),
                        title = EmergencyFormatting.requirementShortName(step.requirement),
                    )
                    step.spotLabel?.takeIf { it.isNotBlank() }?.let { spot ->
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = sakhiSeparator(),
                        )
                        WaitingSummaryRow(
                            icon = Icons.Filled.PinDrop,
                            accent = MaterialTheme.colorScheme.primary,
                            title = spot,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(SakhiSpacing.space4))
        }

        // Outside the scroll, so it is reachable however little room there is. A real
        // button, not a line of red text: styled like the session screen's secondary
        // action, a neutral fill at 56dp with the label carrying the colour.
        //
        // Neutral fill rather than a red one on purpose. Cancelling strands a woman who may
        // already be walking over, so it should look pressable and deliberate -- but a
        // filled red bar is the loudest thing that could sit under a screen whose whole job
        // is to say "hold on, she is coming". The confirmation behind it guards the decision.
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = sakhiSystemGray5().copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SakhiSpacing.space4,
                    vertical = SakhiSpacing.space3,
                )
                .height(56.dp)
                .clickable(enabled = !isCancelling) { showCancelConfirm = true },
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isCancelling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.error,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(SakhiSpacing.space2))
                }
                Text(
                    text = stringResource(
                        if (isCancelling) R.string.emergency_cancelling
                        else R.string.emergency_cancel_request,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showCancelConfirm) {
        SakhiAlertSheet(
            kind = SakhiAlertKind.Destructive,
            title = stringResource(R.string.emergency_cancel_confirm_title),
            message = stringResource(R.string.emergency_cancel_confirm_body, helperName),
            primaryLabel = stringResource(R.string.emergency_cancel_request),
            onPrimaryClick = {
                showCancelConfirm = false
                isCancelling = true
                viewModel.cancelRequest(step.requestId)
            },
            secondaryLabel = stringResource(R.string.emergency_keep_waiting),
            onSecondaryClick = { showCancelConfirm = false },
            onDismissRequest = { showCancelConfirm = false },
        )
    }
}

/** One line of the "what she asked for" card. iOS's `EmergencyRow` + `EmergencyBadgeIcon`. */
@Composable
private fun WaitingSummaryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    title: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
            color = sakhiSecondaryLabel(),
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
