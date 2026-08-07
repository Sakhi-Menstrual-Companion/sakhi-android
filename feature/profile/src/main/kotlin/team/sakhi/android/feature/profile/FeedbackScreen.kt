package team.sakhi.android.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.ui.DetailSheetScaffold
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.design.DesignTokens
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel

private const val FEEDBACK_EMAIL = "hello@getswipe.in"
private const val MAX_CHARS = 500
private val FeedbackTypeChipSize = 32.dp
private val FeedbackTypeGlyphSize = 13.dp
private val FeedbackTypeChevronSize = 11.dp
private val FeedbackTypeTitleSize = 15.sp
private val FeedbackInputMinHeight = 140.dp
private val FeedbackInputTextSize = 14.sp
private val FeedbackInputCountSize = 11.sp

private enum class FeedbackType(
    val labelRes: Int,
    val placeholderRes: Int,
    val icon: ImageVector,
) {
    GENERAL(
        R.string.profile_feedback_type_general,
        R.string.profile_feedback_type_general_placeholder,
        Icons.Filled.ChatBubble,
    ),
    FEATURE(
        R.string.profile_feedback_type_feature,
        R.string.profile_feedback_type_feature_placeholder,
        Icons.Filled.Lightbulb,
    ),
    BUG(
        R.string.profile_feedback_type_bug,
        R.string.profile_feedback_type_bug_placeholder,
        Icons.Filled.Error,
    ),
    OTHER(
        R.string.profile_feedback_type_other,
        R.string.profile_feedback_type_other_placeholder,
        Icons.Filled.MoreHoriz,
    ),
}

/** Ports iOS `FeedbackView.swift`: type picker + text field, submits via a mailto: intent. */
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val hapticManager = koinInject<AndroidHapticManager>()
    var selectedType by remember { mutableStateOf(FeedbackType.GENERAL) }
    var feedbackText by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    DetailSheetScaffold(
        title = stringResource(R.string.profile_feedback_title),
        subtitle = stringResource(R.string.profile_feedback_header_subtitle),
        headerIcon = Icons.Filled.Favorite,
        onBack = onBack,
    ) {
        if (submitted) {
            FeedbackSubmittedState()
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
            ) {
                FeedbackTypeMenuCard(
                    selectedType = selectedType,
                    onTypeSelected = {
                        hapticManager.selection()
                        selectedType = it
                    },
                )

                FeedbackInputCard(
                    selectedType = selectedType,
                    feedbackText = feedbackText,
                    onFeedbackChange = { updated ->
                        feedbackText = if (updated.length <= MAX_CHARS) {
                            updated
                        } else {
                            updated.take(MAX_CHARS)
                        }
                    },
                )

                PrimaryButton(
                    text = stringResource(R.string.profile_feedback_send),
                    enabled = feedbackText.trim().length >= 10,
                    onClick = {
                        hapticManager.impact(HapticImpact.MEDIUM)
                        val trimmedFeedback = feedbackText.trim()
                        val subject = context.getString(
                            R.string.profile_feedback_subject,
                            context.getString(selectedType.labelRes),
                        )
                        val mailtoUri = Uri.parse("mailto:$FEEDBACK_EMAIL")
                            .buildUpon()
                            .appendQueryParameter("subject", subject)
                            .appendQueryParameter("body", trimmedFeedback)
                            .build()
                        val intent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
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

@Composable
private fun FeedbackTypeMenuCard(
    selectedType: FeedbackType,
    onTypeSelected: (FeedbackType) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = sakhiSystemBackground(),
        shape = RoundedCornerShape(SakhiRadius.xl),
        tonalElevation = SakhiSpacing.space1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { menuExpanded = true }
                    .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space4),
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(FeedbackTypeChipSize)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = selectedType.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(FeedbackTypeGlyphSize),
                    )
                }
                Text(
                    text = stringResource(selectedType.labelRes),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = FeedbackTypeTitleSize,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.UnfoldMore,
                    contentDescription = null,
                    tint = sakhiSecondaryLabel(),
                    modifier = Modifier.size(FeedbackTypeChevronSize),
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                FeedbackType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(stringResource(type.labelRes)) },
                        leadingIcon = {
                            Icon(
                                imageVector = type.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            if (type != selectedType) onTypeSelected(type)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackInputCard(
    selectedType: FeedbackType,
    feedbackText: String,
    onFeedbackChange: (String) -> Unit,
) {
    val nearingLimit = feedbackText.length > MAX_CHARS - 50

    Surface(
        color = sakhiSystemBackground(),
        shape = RoundedCornerShape(SakhiRadius.xl),
        tonalElevation = SakhiSpacing.space1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
            modifier = Modifier.padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FeedbackInputMinHeight),
                contentAlignment = Alignment.TopStart,
            ) {
                if (feedbackText.isEmpty()) {
                    Text(
                        text = stringResource(selectedType.placeholderRes),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = FeedbackInputTextSize),
                        color = sakhiSecondaryLabel().copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = SakhiSpacing.space1, start = 4.dp),
                    )
                }
                BasicTextField(
                    value = feedbackText,
                    onValueChange = onFeedbackChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = FeedbackInputTextSize,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SakhiSpacing.space1),
                )
            }

            Text(
                text = stringResource(R.string.profile_feedback_count, feedbackText.length, MAX_CHARS),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = FeedbackInputCountSize),
                color = if (nearingLimit) {
                    MaterialTheme.colorScheme.primary
                } else {
                    sakhiSecondaryLabel()
                },
            )
        }
    }
}

@Composable
private fun FeedbackSubmittedState() {
    val confirmColor = DesignTokens.COLOR_CONFIRM.toComposeColor()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, start = SakhiSpacing.space6, end = SakhiSpacing.space6),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .background(confirmColor.copy(alpha = 0.07f), CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(confirmColor.copy(alpha = 0.11f), CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(confirmColor.copy(alpha = 0.16f), CircleShape),
                )
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = confirmColor,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.height(SakhiSpacing.space6))

            Text(
                text = stringResource(R.string.profile_feedback_thank_you),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )

            Spacer(modifier = Modifier.height(SakhiSpacing.space2))

            Text(
                text = stringResource(R.string.profile_feedback_success_body),
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = SakhiSpacing.space8),
            )
        }
    }
}
