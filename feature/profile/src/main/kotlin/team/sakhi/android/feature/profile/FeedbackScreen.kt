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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SheetSurface

private const val FEEDBACK_EMAIL = "hello@getswipe.in"
private const val MAX_CHARS = 500

private enum class FeedbackType(val label: String, val placeholder: String) {
    GENERAL("General Feedback", "Share what's on your mind..."),
    FEATURE("Feature Idea", "What would make Sakhi more useful for you?"),
    BUG("Bug Report", "What went wrong? What did you expect to happen?"),
    OTHER("Other", "Tell us anything..."),
}

/** Ports iOS `FeedbackView.swift`: type picker + text field, submits via a mailto: intent. */
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val hapticManager = koinInject<AndroidHapticManager>()
    var selectedType by remember { mutableStateOf(FeedbackType.GENERAL) }
    var feedbackText by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    SheetSurface(showDragHandle = true) {
        DetailHeader(title = "Share Feedback", onBack = onBack)

        if (submitted) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                    modifier = Modifier.padding(SakhiSpacing.space6),
                ) {
                    Text(text = "Thank you!", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "Your feedback has been sent. We read every message and use it to make Sakhi better.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(SakhiSpacing.space5),
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
                                text = type.label,
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
                    placeholder = { Text(selectedType.placeholder) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
                Text(
                    text = "${feedbackText.length}/$MAX_CHARS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                PrimaryButton(
                    text = "Send Feedback",
                    enabled = feedbackText.trim().length >= 10,
                    onClick = {
                        hapticManager.impact(HapticImpact.MEDIUM)
                        val subject = "Sakhi ${selectedType.label} Feedback"
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
