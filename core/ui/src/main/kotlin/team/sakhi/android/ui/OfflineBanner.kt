package team.sakhi.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing

/** Thin top-of-screen offline notice. Wiring to `NetworkStatus` happens elsewhere. */
@Composable
fun OfflineBanner(
    modifier: Modifier = Modifier,
    message: String = "You are offline. Sakhi will sync again when the connection returns.",
    leadingContent: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.lg),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SakhiSpacing.space4,
                vertical = SakhiSpacing.space2,
            ),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent?.invoke()

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
