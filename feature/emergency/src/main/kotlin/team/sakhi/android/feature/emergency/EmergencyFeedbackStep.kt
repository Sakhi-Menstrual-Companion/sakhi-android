package team.sakhi.android.feature.emergency

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.models.EmergencyRequestStatus
import team.sakhi.models.EmergencySession

/**
 * The outcome screen. Port of iOS `EmergencyFeedbackView.swift`.
 *
 * **This was never a star-rating prompt, and Android had made it one.** `main`'s
 * `FeedbackViewController` was a *status* screen driven by `configureForStatus(statusTitle:
 * statusTintColor:feedbackText:primaryTitle:secondaryTitle:...)`, with a separate case per
 * request status per role (`configureSeekerUI` / `configureHelperUI`). Android showed five
 * stars and a free-text note, neither of which exists on either the original or iOS: it
 * asked a woman who had just been through something to score it out of five.
 *
 * Every title, tint, line of copy and button label below is taken from that file.
 *
 * Layout, top to bottom: the ring mark, the question as the headline, the status underneath
 * it, then the primary and optional secondary button. A button with no title is hidden
 * outright, as `setButtonTitles` did.
 */
@Composable
internal fun EmergencyFeedbackStep(
    viewModel: EmergencyViewModel,
    session: EmergencySession,
    onDone: () -> Unit,
) {
    var isWorking by remember { mutableStateOf(false) }

    /** `main`'s `feedbackPrimaryButton` / `feedbackSecondaryButton`. */
    val outcome = rememberOutcome(session)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.size(SakhiSpacing.space10))

        EmergencyDottedRingMark()

        // The mockup leads with the question and puts the status underneath, the reverse of
        // `main`, which had "Need Your Feedback" as the title button and the question as the
        // label below it. The question is the thing she has to answer, so it takes the
        // headline.
        AnimatedContent(
            targetState = outcome.text,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "feedback-question",
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = SakhiSpacing.space5)
                    .padding(top = SakhiSpacing.space4),
            )
        }

        Text(
            text = outcome.title,
            style = MaterialTheme.typography.bodyLarge,
            color = sakhiSecondaryLabel(),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = SakhiSpacing.space5)
                .padding(top = SakhiSpacing.space1),
        )

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5)
                .padding(bottom = SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            outcome.primaryTitle?.let { title ->
                Button(
                    onClick = {
                        if (!isWorking) {
                            isWorking = true
                            perform(outcome.primaryAction, viewModel, onDone)
                            isWorking = false
                        }
                    },
                    enabled = !isWorking,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(text = title, modifier = Modifier.padding(vertical = SakhiSpacing.space2))
                    }
                }
            }
            // Pink text, not grey: on this screen "No" is a real answer, not a way out.
            outcome.secondaryTitle?.let { title ->
                TextButton(
                    onClick = {
                        if (!isWorking) {
                            isWorking = true
                            perform(outcome.secondaryAction, viewModel, onDone)
                            isWorking = false
                        }
                    },
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = title, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** iOS `Action`. */
private enum class FeedbackAction { EXIT, FIND_SOMEONE_ELSE, ASSISTANCE_COMPLETED }

private data class FeedbackOutcome(
    val title: String,
    val tint: Color,
    val text: String,
    val primaryTitle: String?,
    val secondaryTitle: String?,
    val primaryAction: FeedbackAction,
    val secondaryAction: FeedbackAction,
)

/**
 * The seeker and helper switches from `main`, mapped onto the statuses this build can
 * actually reach.
 *
 * `EmergencyRequestStatus` collapses several of the original's fourteen cases, so each
 * branch uses the original wording for the case it stands in for; the mapping is called out
 * per branch, exactly as iOS records it.
 */
@Composable
private fun rememberOutcome(session: EmergencySession): FeedbackOutcome {
    // `LocalDataManager.shared.remoteUser?.name.split(separator: " ").first ?? "User"`.
    val remoteName = session.counterpartName?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotEmpty() }
        ?: stringResource(R.string.emergency_user)

    val red = Color(0xFFFF3B30)
    val orange = Color(0xFFFF9500)
    val green = Color(0xFF34C759)
    val pink = MaterialTheme.colorScheme.primary

    return if (session.viewerIsRequester) {
        when (session.status) {
            // main: .waitingForFeedbackFromSeeker
            EmergencyRequestStatus.COMPLETED -> FeedbackOutcome(
                title = stringResource(R.string.emergency_outcome_need_feedback),
                tint = pink,
                text = stringResource(R.string.emergency_outcome_did_help, remoteName),
                primaryTitle = stringResource(R.string.emergency_outcome_she_helped),
                secondaryTitle = stringResource(R.string.emergency_outcome_no),
                primaryAction = FeedbackAction.ASSISTANCE_COMPLETED,
                secondaryAction = FeedbackAction.EXIT,
            )
            // main: .seekerCanceledOngoingRequest
            EmergencyRequestStatus.CANCELLED -> FeedbackOutcome(
                title = stringResource(R.string.emergency_outcome_cancelled),
                tint = red,
                text = stringResource(R.string.emergency_outcome_you_cancelled),
                primaryTitle = stringResource(R.string.emergency_find_someone_else),
                secondaryTitle = stringResource(R.string.emergency_exit),
                primaryAction = FeedbackAction.FIND_SOMEONE_ELSE,
                secondaryAction = FeedbackAction.EXIT,
            )
            // main: .expired. The original said 30 minutes; this build expires a request
            // after 15, and a screen must not state a timeout the server does not use.
            EmergencyRequestStatus.EXPIRED -> FeedbackOutcome(
                title = stringResource(R.string.emergency_outcome_expired),
                tint = orange,
                text = stringResource(R.string.emergency_outcome_timed_out_15),
                primaryTitle = stringResource(R.string.emergency_outcome_try_again),
                secondaryTitle = stringResource(R.string.emergency_exit),
                primaryAction = FeedbackAction.FIND_SOMEONE_ELSE,
                secondaryAction = FeedbackAction.EXIT,
            )
            // main: .helperRejected
            EmergencyRequestStatus.REJECTED -> FeedbackOutcome(
                title = stringResource(R.string.emergency_request_declined),
                tint = red,
                text = stringResource(R.string.emergency_declined_body, remoteName),
                primaryTitle = stringResource(R.string.emergency_find_someone_else),
                secondaryTitle = stringResource(R.string.emergency_exit),
                primaryAction = FeedbackAction.FIND_SOMEONE_ELSE,
                secondaryAction = FeedbackAction.EXIT,
            )
            // main: .seekerRequestedHelp / .noResponse
            EmergencyRequestStatus.REQUESTED -> FeedbackOutcome(
                title = stringResource(R.string.emergency_outcome_closed),
                tint = red,
                text = stringResource(R.string.emergency_outcome_no_response, remoteName),
                primaryTitle = stringResource(R.string.emergency_find_someone_else),
                secondaryTitle = stringResource(R.string.emergency_exit),
                primaryAction = FeedbackAction.FIND_SOMEONE_ELSE,
                secondaryAction = FeedbackAction.EXIT,
            )
            // main: .helperAccepted
            else -> FeedbackOutcome(
                title = stringResource(R.string.emergency_outcome_help_coming),
                tint = green,
                text = stringResource(R.string.emergency_outcome_accepted, remoteName),
                primaryTitle = stringResource(R.string.emergency_continue),
                secondaryTitle = null,
                primaryAction = FeedbackAction.EXIT,
                secondaryAction = FeedbackAction.EXIT,
            )
        }
    } else {
        when (session.status) {
            // main: .requestCompleted
            EmergencyRequestStatus.COMPLETED -> FeedbackOutcome(
                title = stringResource(R.string.emergency_outcome_help_completed),
                tint = green,
                text = stringResource(R.string.emergency_outcome_you_helped, remoteName),
                primaryTitle = stringResource(R.string.emergency_continue),
                secondaryTitle = null,
                primaryAction = FeedbackAction.EXIT,
                secondaryAction = FeedbackAction.EXIT,
            )
            // main: .seekerCanceledOngoingRequest
            EmergencyRequestStatus.CANCELLED -> FeedbackOutcome(
                title = stringResource(R.string.emergency_outcome_ended),
                tint = red,
                text = stringResource(R.string.emergency_outcome_she_cancelled, remoteName),
                primaryTitle = stringResource(R.string.emergency_continue),
                secondaryTitle = null,
                primaryAction = FeedbackAction.EXIT,
                secondaryAction = FeedbackAction.EXIT,
            )
            // main: .expired
            EmergencyRequestStatus.EXPIRED -> FeedbackOutcome(
                title = stringResource(R.string.emergency_outcome_expired),
                tint = orange,
                text = stringResource(R.string.emergency_outcome_their_request_timed_out, remoteName),
                primaryTitle = stringResource(R.string.emergency_continue),
                secondaryTitle = null,
                primaryAction = FeedbackAction.EXIT,
                secondaryAction = FeedbackAction.EXIT,
            )
            // main: .waitingForFeedbackFromSeeker
            else -> FeedbackOutcome(
                title = stringResource(R.string.emergency_outcome_waiting_feedback, remoteName),
                tint = pink,
                text = stringResource(R.string.emergency_outcome_thank_you),
                primaryTitle = stringResource(R.string.emergency_continue),
                secondaryTitle = null,
                primaryAction = FeedbackAction.EXIT,
                secondaryAction = FeedbackAction.EXIT,
            )
        }
    }
}

/**
 * `primaryButtonTapped` / `secondaryButtonTapped`, minus the Firebase cleanup those needed
 * -- closing the request is the server's job here.
 */
private fun perform(
    action: FeedbackAction,
    viewModel: EmergencyViewModel,
    onDone: () -> Unit,
) {
    when (action) {
        FeedbackAction.EXIT -> {
            viewModel.skipFeedback()
            onDone()
        }
        // Back to the start of the flow. main's .findSomeoneElse did the same, reopening the
        // requirement picker rather than the list she came from.
        FeedbackAction.FIND_SOMEONE_ELSE -> {
            viewModel.skipFeedback()
            viewModel.begin()
        }
        // The original's "She Helped" was a yes, with no scale behind it. The rating column
        // only exists to count who has been vouched for, so a yes is recorded as one and
        // "No" simply closes without a rating.
        FeedbackAction.ASSISTANCE_COMPLETED -> {
            viewModel.submitFeedback(5, null)
            onDone()
        }
    }
}
