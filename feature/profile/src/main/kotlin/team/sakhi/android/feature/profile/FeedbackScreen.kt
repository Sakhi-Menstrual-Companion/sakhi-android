package team.sakhi.android.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.android.ui.PrimaryButton

private const val FEEDBACK_EMAIL = "hello@getswipe.in"
private const val MAX_CHARS = 500

private enum class FeedbackType(val labelRes: Int, val placeholderRes: Int) {
    GENERAL(R.string.profile_feedback_type_general, R.string.profile_feedback_type_general_placeholder),
    FEATURE(R.string.profile_feedback_type_feature, R.string.profile_feedback_type_feature_placeholder),
    BUG(R.string.profile_feedback_type_bug, R.string.profile_feedback_type_bug_placeholder),
    OTHER(R.string.profile_feedback_type_other, R.string.profile_feedback_type_other_placeholder),
}

/** Ports iOS `FeedbackView.swift`: type picker + text field, submits via a mailto: intent. */
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val hapticManager = koinInject<AndroidHapticManager>()
    var selectedType by remember { mutableStateOf(FeedbackType.GENERAL) }
    var feedbackText by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    DetailSheetScaffold(title = stringResource(R.string.profile_feedback_title), onBack = onBack) {
        if (submitted) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                    modifier = Modifier.padding(SakhiSpacing.space6),
                ) {
                    Text(
                        text = stringResource(R.string.profile_feedback_thank_you),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = stringResource(R.string.profile_feedback_success_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FeedbackType.entries.forEach { type ->
                        val selected = type == selectedType
                        Surface(
                            shape = RoundedCornerShape(SakhiRadius.full),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                hapticManager.selection()
                                selectedType = type
                            },
                        ) {
                            Text(
                                text = stringResource(type.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = SakhiSpacing.space3, vertical = SakhiSpacing.space2),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { if (it.length <= MAX_CHARS) feedbackText = it },
                    placeholder = { Text(stringResource(selectedType.placeholderRes)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
                Text(
                    text = stringResource(R.string.profile_feedback_count, feedbackText.length, MAX_CHARS),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                PrimaryButton(
                    text = stringResource(R.string.profile_feedback_send),
                    enabled = feedbackText.trim().length >= 10,
                    onClick = {
                        hapticManager.impact(HapticImpact.MEDIUM)
                        val subject = context.getString(
                            R.string.profile_feedback_subject,
                            context.getString(selectedType.labelRes),
                        )
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$FEEDBACK_EMAIL")).apply {
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                            putExtra(Intent.EXTRA_TEXT, feedbackText.trim())
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (runCatching { context.startActivity(intent) }.isSuccess) {
                            submitted = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
