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
import androidx.compose.material3.LocalTextStyle
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
import team.sakhi.android.designsystem.rememberSakhiFlingBehavior
import team.sakhi.android.designsystem.sakhiLightPink
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.emergency.EmergencyState
import team.sakhi.models.EmergencyFormatting
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemGray5
import team.sakhi.android.designsystem.sakhiSeparator
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.ui.SakhiAlertKind
import team.sakhi.android.ui.SakhiAlertSheet
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.SakhiTokens
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.SakhiUIColors
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource

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
    // `fillMaxWidth`, not `fillMaxSize`, and the content wraps instead of taking a
    // weighted slice.
    //
    // The sheet's content is measured against the whole window while only the 502dp peek is
    // on screen, so a filling column with `weight(1f)` pushed Cancel Request -- the one
    // control here -- to the bottom of the WINDOW, well below the fold. Wrapping means the
    // sheet is as tall as its content and the button sits directly under the card.
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState(), flingBehavior = rememberSakhiFlingBehavior())
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The ring leads, on its own, with nothing competing beside it. It is the only
            // thing on this screen that is actually happening.
            // Figma `person`: `pt-6 pb-8`, 9 between items. The ring leads, on its own,
            // with nothing competing beside it -- it is the only thing on this screen that
            // is actually happening.
            EmergencyRequestedProfile(
                name = step.helperName,
                avatarId = step.helperId,
                size = 88.dp,
                modifier = Modifier.padding(top = 6.dp),
            )

            Text(
                text = stringResource(
                    R.string.emergency_waiting_for,
                    step.helperName ?: stringResource(R.string.emergency_a_sakhi_nearby),
                ),
                fontSize = 20.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 9.dp),
            )

            Text(
                text = stringResource(R.string.emergency_waiting_body),
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = SakhiSpacing.space5)
                    .padding(top = 9.dp),
            )

            if (remaining > 0) {
                // Says how long the waiting actually lasts. Standing somewhere uncomfortable
                // with no idea whether this is ten seconds or ten minutes is its own kind of
                // awful. Android had no countdown at all.
                //
                // Figma `countdown`: brand pink at 12%, `px-14 py-6`, a 14dp clock and the
                // time in Bold 17 pink.
                Row(
                    modifier = Modifier
                        .padding(top = 9.dp)
                        .clip(CircleShape)
                        .background(SakhiUIColors.BRAND_PINK.toComposeColor().copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = SakhiUIColors.BRAND_PINK.toComposeColor(),
                    )
                    Text(
                        text = stringResource(
                            R.string.emergency_left_to_answer,
                            EmergencyIso8601.countdown(remaining),
                        ),
                        // iOS uses `.monospacedDigit()` so the countdown does not jitter
                        // as the digits change width. `tnum` is the same thing here, and it
                        // only exists on `TextStyle`, not as a `Text` parameter.
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                        fontSize = 17.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SakhiUIColors.BRAND_PINK.toComposeColor(),
                    )
                }
            }

            // What she asked for, in a card rather than as loose chips stacked under the
            // name. Figma `EA-08` labels it and gives each fact its own row, so she can see
            // at a glance exactly how much the woman she asked was told -- including that
            // her live location was not part of it.
            EmergencySectionHeader(
                title = stringResource(R.string.emergency_what_she_was_told),
                topPadding = 20.dp,
            )
            EmergencyCard {
                WaitingSummaryRow(
                    icon = step.requirement.icon(),
                    accent = SakhiUIColors.BRAND_PINK.toComposeColor(),
                    title = stringResource(R.string.emergency_requirement_label),
                    value = EmergencyFormatting.requirementShortName(step.requirement),
                )
                step.spotLabel?.takeIf { it.isNotBlank() }?.let { spot ->
                    SakhiListDivider(startInset = EmergencyRowInset)
                    WaitingSummaryRow(
                        icon = Icons.Filled.PinDrop,
                        accent = SakhiTokens.SectionBlue,
                        title = stringResource(R.string.emergency_spot_label),
                        value = spot,
                    )
                }
                SakhiListDivider(startInset = EmergencyRowInset)
                WaitingSummaryRow(
                    icon = Icons.Filled.MyLocation,
                    accent = SakhiUIColors.BRAND_CONFIRM.toComposeColor(),
                    title = stringResource(R.string.emergency_your_location_label),
                    value = stringResource(R.string.emergency_not_shared),
                )
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
        // Outside the scroll, so it is reachable however little room there is.
        //
        // Figma `CTA · Cancel Request`: a white pill with a 35%-red edge and the label in
        // red, not a filled red bar. Cancelling strands a woman who may already be walking
        // over, so it should look pressable and deliberate -- but the loudest thing on a
        // screen whose whole job is "hold on, she is coming" should not be the way out.
        // The confirmation behind it guards the decision.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5)
                .padding(top = SakhiSpacing.space6, bottom = 10.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(sakhiSystemBackground())
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(percent = 50),
                )
                .clickable(enabled = !isCancelling) { showCancelConfirm = true }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isCancelling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.error,
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    text = stringResource(
                        if (isCancelling) R.string.emergency_cancelling
                        else R.string.emergency_cancel_request,
                    ),
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
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

/** One line of the "what she was told" card, through the flow's shared row. */
@Composable
private fun WaitingSummaryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    title: String,
    value: String,
) {
    EmergencyRow(
        title = title,
        leading = { EmergencyBadgeIcon(icon = icon, color = accent) },
        accessory = {
            Text(
                text = value,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = sakhiSecondaryLabel(),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
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

    // Figma `EA-09`: a 14 gap, her face in an 88dp ring, the headline, the line saying what
    // happened, then the reassurance in a card of its own and two ways forward.
    //
    // It used to be a red `person.off` glyph over a red pill. Nothing went wrong here -- she
    // was allowed to say no -- so the screen should not read like an error.
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.size(14.dp))

        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(88.dp)
                .clip(CircleShape)
                .background(sakhiSecondaryLabel().copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            // `Rejected` carries her name but not her id, so the face is dealt from the
            // name. It is the same woman she was just looking at on the waiting screen, and
            // a blank disc there would read as "someone" rather than as an answer from her.
            val faceIndex = remember(step.helperName) {
                EmergencyAvatarCatalog.dealtIndex(step.helperName.orEmpty())
            }
            Image(
                painter = painterResource(EmergencyAvatarCatalog.drawableAt(faceIndex)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .scale(EmergencyAvatarCatalog.contentScaleAt(faceIndex)),
            )
        }

        // main: configureSeekerUI(.helperRejected) — "Request Declined", with "{name}
        // declined your request" underneath.
        Text(
            text = stringResource(R.string.emergency_request_declined),
            fontSize = 20.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiLabel(),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 9.dp),
        )

        Text(
            text = stringResource(R.string.emergency_declined_body, helperName),
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = SakhiSpacing.space5)
                .padding(top = 9.dp),
        )

        // Figma `reassurance`: the privacy note in a card of its own with a badge, not a
        // caption under the body copy. It is the single most important thing on this screen
        // and it was the smallest text on it.
        Spacer(modifier = Modifier.size(SakhiSpacing.space4))
        EmergencyCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EmergencyBadgeIcon(
                    icon = Icons.Filled.Lock,
                    color = SakhiUIColors.BRAND_CONFIRM.toComposeColor(),
                )
                Text(
                    text = stringResource(R.string.emergency_declined_privacy_note),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = sakhiSecondaryLabel(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Figma `actions`: two full-width pills 10 apart. `main`'s primary on this status
        // was "Find Someone Else"; the second is the other real answer -- a place she can
        // walk to without waiting on anyone.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5)
                .padding(top = SakhiSpacing.space6, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EmergencyPrimaryButton(
                title = stringResource(R.string.emergency_find_someone_else),
                onClick = viewModel::askSomeoneElse,
            )
            EmergencySecondaryButton(title = stringResource(R.string.emergency_exit)) {
                viewModel.dismiss()
                onExit()
            }
        }
    }
}
