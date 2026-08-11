package team.sakhi.android.feature.emergency

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiSpacing
import androidx.compose.ui.graphics.Color
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.EmergencyRequirement

/**
 * Maps the shared requirement to a Material icon.
 *
 * `EmergencyFormatting.requirementIcon` returns an SF Symbol name, which is the right
 * thing for shared code to carry (iOS reads it directly) but means nothing to Compose.
 * The mapping is intentionally by enum rather than by parsing that string, so adding a
 * requirement is a compile error here instead of a blank icon at runtime.
 */
internal fun EmergencyRequirement.icon(): ImageVector = when (this) {
    EmergencyRequirement.PAD -> Icons.Filled.WaterDrop
    EmergencyRequirement.TAMPON -> Icons.Filled.WaterDrop
    EmergencyRequirement.MENSTRUAL_CUP -> Icons.Filled.HealthAndSafety
    EmergencyRequirement.PAINKILLER -> Icons.Filled.Medication
    EmergencyRequirement.CLEAN_CLOTHES -> Icons.Filled.Checkroom
    EmergencyRequirement.WALK_WITH_ME -> Icons.Filled.DirectionsWalk
    EmergencyRequirement.OTHER -> Icons.Filled.MoreHoriz
}

/**
 * Per-requirement accent, from the shared `EmergencyFormatting.requirementColorHex`.
 *
 * The original iOS build colour-coded each row (red pad, blue tampon, orange hot water
 * bag, teal medicine) and tinted a circular badge with it at 0.2 alpha. Reading the hex
 * from shared code rather than hardcoding it here is what stops the two platforms tinting
 * the same requirement differently.
 */
internal fun EmergencyRequirement.accentColor(): Color {
    val hex = EmergencyFormatting.requirementColorHex(this).removePrefix("#")
    val value = hex.toLongOrNull(16) ?: return Color.Unspecified
    return Color(0xFF000000 or value)
}

@Composable
internal fun EmergencyHeader(title: String, subtitle: String? = null) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        modifier = Modifier.padding(top = SakhiSpacing.space3, bottom = SakhiSpacing.space2),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SecondaryPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space3),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * A slow pulse rather than a spinner. She may be standing somewhere uncomfortable, so the
 * screen should read calm rather than urgent.
 */
@Composable
internal fun EmergencyPulse() {
    val transition = rememberInfiniteTransition(label = "emergency-pulse")

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
        repeat(3) { index ->
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                    // Staggered so the three rings read as one outward pulse rather than
                    // three rings expanding in lockstep.
                    initialStartOffset = StartOffset(index * 800),
                ),
                label = "pulse-$index",
            )
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .scale(0.7f + progress * 1.3f)
                    .alpha((1f - progress) * 0.5f)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Initials only.
 *
 * `photoUrl` is accepted because the shared model carries one and iOS renders it, but this
 * app has no image-loading library anywhere, and pulling Coil in for one avatar would add a
 * dependency the rest of the codebase does not use. If remote avatars are wanted on Android
 * later, that is a project-wide decision rather than something to sneak in here.
 */
@Composable
internal fun EmergencyAvatar(
    name: String?,
    @Suppress("UNUSED_PARAMETER") photoUrl: String?,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val initial = name?.trim()?.firstOrNull()?.uppercase() ?: "S"

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun EmergencyError(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
            modifier = Modifier.padding(horizontal = SakhiSpacing.space5),
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.emergency_try_again))
            }
        }
    }
}
