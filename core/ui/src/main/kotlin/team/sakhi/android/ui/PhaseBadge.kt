package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.phasePrimaryColor
import team.sakhi.models.CyclePhase

@Composable
fun PhaseBadge(
    phase: CyclePhase,
    modifier: Modifier = Modifier,
    text: String = phase.displayName,
    accentColor: Color = phasePrimaryColor(phase),
) {
    Row(
        modifier = modifier
            .background(accentColor.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = SakhiSpacing.space3, vertical = SakhiSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = accentColor,
            modifier = Modifier.size(7.dp),
        ) {}
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = accentColor,
        )
    }
}
