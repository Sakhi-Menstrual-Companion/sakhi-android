package team.sakhi.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing

enum class SakhiAlertTone {
    Info,
    Error,
}

/** Shared inline alert/banner shell for errors and low-friction notices. */
@Composable
fun SakhiAlert(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: SakhiAlertTone = SakhiAlertTone.Info,
    dismissLabel: String = "Dismiss",
    onDismiss: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    val containerColor = when (tone) {
        SakhiAlertTone.Info -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        SakhiAlertTone.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
    }
    val contentColor = when (tone) {
        SakhiAlertTone.Info -> MaterialTheme.colorScheme.primary
        SakhiAlertTone.Error -> MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(SakhiRadius.xl),
        color = containerColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent?.invoke()

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
            ) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = dismissLabel,
                        color = contentColor,
                    )
                }
            }
        }
    }
}
