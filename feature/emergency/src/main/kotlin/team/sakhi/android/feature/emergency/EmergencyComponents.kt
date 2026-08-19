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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Thermostat
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSeparator
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.models.TrustLevel
import androidx.compose.ui.res.painterResource
import team.sakhi.android.designsystem.sakhiLightPink
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.outlined.FavoriteBorder
import team.sakhi.android.designsystem.SakhiSpacing
import androidx.compose.ui.graphics.Color
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.EmergencyRequirement
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSystemGray5

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
    // iOS uses "thermometer" here, the symbol `main` paired with a hot water bag.
    EmergencyRequirement.HOT_WATER_BAG -> Icons.Filled.Thermostat
    EmergencyRequirement.PAINKILLER -> Icons.Filled.Medication
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
                color = sakhiSecondaryLabel(),
            )
        }
    }
}

// `SecondaryPill` drew the two secondary actions iOS removed from the requirement
// picker. With both gone it had no callers left.


/**
 * A slow pulse rather than a spinner. She may be standing somewhere uncomfortable, so the
 * screen should read calm rather than urgent.
 */
// `EmergencyPulse` drew expanding rings with no avatar on the waiting screen, so that
// screen never said who was being waited on. iOS draws `EmergencyRequestedProfile`
// there instead, and Android now does too.


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

// ─────────────────────────────────────────────────────────────────────────────
// The shared vocabulary from iOS `EmergencyComponents.swift`.
//
// Ported as one set rather than per screen. Three Android screens were each building
// their own trust chip and their own card rows, so the same fact rendered three ways --
// which is how the profile ended up with a plain grey trust label while the nearby list
// had a tinted one.
// ─────────────────────────────────────────────────────────────────────────────

/** iOS `EmergencySectionHeader`: 12sp bold, uppercase, secondary ink, leading aligned. */
@Composable
internal fun EmergencySectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = sakhiSecondaryLabel(),
        modifier = modifier
            .fillMaxWidth()
            // iOS `EmergencySectionHeader`: `.padding(.horizontal, DS.Spacing.ml)` = 20.
            .padding(horizontal = SakhiSpacing.space5)
            .padding(bottom = SakhiSpacing.space1),
    )
}

/**
 * iOS `EmergencyCard`: the rounded container rows sit in.
 *
 * The system background, not `surfaceVariant`, so it lifts off the sheet's pink-tinted
 * ground the way the mockups show.
 */
@Composable
internal fun EmergencyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xl),
        color = sakhiSystemBackground(),
        modifier = modifier
            .fillMaxWidth()
            // iOS `EmergencyCard`: `.padding(.horizontal, DS.Spacing.ml)` = 20.
            .padding(horizontal = SakhiSpacing.space5),
    ) {
        Column(content = content)
    }
}

/**
 * iOS `EmergencyBadgeIcon`: the tinted circular glyph on a row.
 *
 * `main`'s RequirementTableViewCell drew exactly this -- a circle filled with the row's own
 * colour at 0.2 alpha behind the glyph at full strength.
 */
@Composable
internal fun EmergencyBadgeIcon(
    icon: ImageVector,
    color: Color,
    size: Dp = 30.dp,
) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.5f))
    }
}

/** iOS `EmergencyRow`: leading glyph, title, optional trailing accessory. */
@Composable
internal fun EmergencyRow(
    title: String,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color = sakhiLabel(),
    accessory: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(
            horizontal = SakhiSpacing.space3,
            vertical = SakhiSpacing.space2,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        leading()
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
            modifier = Modifier.weight(1f),
        )
        accessory()
    }
}

/** iOS `EmergencyRowDivider`: hairline inset past the leading glyph, as a grouped table insets. */
@Composable
internal fun EmergencyRowDivider(leadingInset: Dp = 62.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = leadingInset),
        color = sakhiSeparator(),
    )
}

/**
 * iOS `EmergencyTrustChip`.
 *
 * [tinted] carries the trust level's own colour; untinted it falls back to secondary ink.
 * The profile passes `tinted = true`, because the one thing on that screen about
 * trustworthiness should not be the dullest thing on it.
 */
@Composable
internal fun EmergencyTrustChip(
    trust: TrustLevel,
    tinted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val ink = if (tinted) {
        Color(0xFF000000 or (trust.colorHex.removePrefix("#").toLongOrNull(16) ?: 0L))
    } else {
        sakhiSecondaryLabel()
    }
    Surface(
        shape = CircleShape,
        color = sakhiSystemGray5().copy(alpha = 0.5f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SakhiSpacing.space2, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = trust.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ink,
            )
        }
    }
}

/**
 * iOS `EmergencySheetTitle`: one centred title, no subtitle.
 *
 * The requirement picker uses this rather than [EmergencyHeader]. iOS's version is a
 * centred "Select Requirement" and nothing else; Android had a left-aligned heading with a
 * subtitle under it, which made a four-row picker look like a page of instructions.
 */
@Composable
internal fun EmergencySheetTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = sakhiLabel(),
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = SakhiSpacing.space4, bottom = SakhiSpacing.space5),
    )
}

/**
 * iOS `EmergencyMarkAvatar`: the circular Sakhi mark used on the nearby list and the
 * session card.
 *
 * Not her photograph. Android's nearby list rendered `photoUrl`, which puts the real faces
 * of women nearby on screen before anyone has accepted -- where a glance over her shoulder
 * catches them. iOS shows the brand mark here on purpose.
 */
@Composable
internal fun EmergencyMarkAvatar(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(sakhiLightPink()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            // The mark lives in `core:ui`, so it comes from that module's R, not this one's.
            painter = painterResource(team.sakhi.android.ui.R.drawable.sakhi_symbol),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/**
 * iOS `EmergencyDottedRingMark`: the mark on the outcome screen -- the Sakhi symbol on a
 * pale disc, ringed by pink dots.
 *
 * `main`'s `RequestedProfile` drew a dashed ring that pulsed while it waited. Here the wait
 * is over and she is being asked a question, so the ring is still and finer: the same visual
 * family, without the "still going" motion that would now be telling her the wrong thing.
 */
@Composable
internal fun EmergencyDottedRingMark(size: Dp = 128.dp, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val disc = sakhiLightPink().copy(alpha = 0.55f)

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(size * 0.84f).clip(CircleShape).background(disc))

        Canvas(modifier = Modifier.size(size)) {
            val stroke = 3.dp.toPx()
            drawCircle(
                color = accent,
                radius = (this.size.minDimension - stroke) / 2f,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round,
                    // Dash [1, 8]: dots rather than dashes, which is what makes this read
                    // as still where the waiting ring reads as moving.
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 8.dp.toPx()), 0f),
                ),
            )
        }

        Icon(
            painter = painterResource(team.sakhi.android.ui.R.drawable.sakhi_symbol),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.40f),
        )
    }
}

/**
 * Material equivalents of the SF Symbols in shared `TrustLevel.badgeIcon`.
 *
 * Deliberately *not* used by [EmergencyTrustChip] or the nearby list: iOS draws
 * `checkmark.seal.fill` in both of those and lets the colour carry the level. The per-level
 * glyph appears in exactly one place, the incoming-request card, where the helper is being
 * told who is asking rather than comparing several people.
 *
 * Mapped from the shared strings so the two platforms cannot pick different glyphs:
 * `leaf.fill`, `heart`, `heart.fill`, `sparkles`.
 */
@Composable
internal fun TrustLevel.badgeIcon(): ImageVector = when (this) {
    TrustLevel.CARING -> Icons.Filled.Spa
    TrustLevel.KIND -> Icons.Outlined.FavoriteBorder
    TrustLevel.VERY_KIND -> Icons.Filled.Favorite
    TrustLevel.ANGEL -> Icons.Filled.AutoAwesome
}
