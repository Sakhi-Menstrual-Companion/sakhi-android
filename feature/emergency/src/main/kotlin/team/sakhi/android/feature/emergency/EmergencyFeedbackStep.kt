package team.sakhi.android.feature.emergency

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.models.EmergencySession

/**
 * Step 5 — rating the person who helped, or who she helped.
 *
 * Ratings are what make the offer list mean anything later, so it is worth asking. It is
 * also entirely skippable: she may have just been through something difficult, and nothing
 * here should feel like a toll gate on closing the screen.
 */
@Composable
internal fun EmergencyFeedbackStep(
    viewModel: EmergencyViewModel,
    session: EmergencySession,
    onDone: () -> Unit,
) {
    var rating by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = SakhiSpacing.space5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        // main's FeedbackViewController was a STATUS screen: a tinted status title over
        // one line of contextual copy, not a bare rating prompt. Wording is that file's.
        val counterpart = session.counterpartName?.substringBefore(' ')
            ?: stringResource(R.string.emergency_your_sakhi)

        Text(
            text = if (session.viewerIsRequester) {
                stringResource(R.string.emergency_status_did_she_help)
            } else {
                stringResource(R.string.emergency_status_thank_you)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = if (session.viewerIsRequester) {
                stringResource(R.string.emergency_feedback_did_help, counterpart)
            } else {
                stringResource(R.string.emergency_feedback_you_helped, counterpart)
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )

        EmergencyAvatar(
            name = session.counterpartName,
            photoUrl = session.counterpartPhotoUrl,
            size = 64.dp,
        )

        Text(
            text = session.counterpartName ?: stringResource(R.string.emergency_your_sakhi),
            style = MaterialTheme.typography.titleMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
            (1..5).forEach { value ->
                Icon(
                    imageVector = if (value <= rating) {
                        Icons.Filled.Star
                    } else {
                        Icons.Outlined.StarBorder
                    },
                    contentDescription = stringResource(R.string.emergency_star_rating, value),
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { rating = value },
                    tint = if (value <= rating) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text(stringResource(R.string.emergency_note_hint)) },
            maxLines = 4,
            shape = RoundedCornerShape(SakhiRadius.lg),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (rating == 0 || isSubmitting) return@Button
                isSubmitting = true
                viewModel.submitFeedback(rating, note.trim().ifEmpty { null })
                onDone()
            },
            enabled = rating > 0 && !isSubmitting,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.emergency_send))
        }

        TextButton(onClick = {
            viewModel.skipFeedback()
            onDone()
        }) {
            Text(stringResource(R.string.emergency_skip_for_now))
        }

        Spacer(modifier = Modifier.size(SakhiSpacing.space4))
    }
}
