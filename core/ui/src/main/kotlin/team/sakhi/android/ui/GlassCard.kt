package team.sakhi.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.phasePrimaryColor
import team.sakhi.models.CyclePhase

/** Shared translucent card shell for Home, reports, and other elevated content blocks. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentPhase: CyclePhase? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val phaseAccent = accentPhase?.let { phasePrimaryColor(it) }

    Surface(
        shape = RoundedCornerShape(SakhiRadius.xxl),
        color = (phaseAccent ?: MaterialTheme.colorScheme.surface).copy(alpha = 0.16f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = SakhiSpacing.space1,
        border = phaseAccent?.let {
            BorderStroke(
                width = SakhiSpacing.space1 / 4,
                color = it.copy(alpha = 0.24f),
            )
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(SakhiSpacing.space5),
            content = content,
        )
    }
}
