package team.sakhi.android.feature.emergency

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.design.SakhiUIColors
import team.sakhi.android.designsystem.sakhiTertiaryLabel
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
internal fun EmergencyRequirement.accentColor(): Color =
    EmergencyFormatting.requirementColorHex(this).toEmergencyColor()

/**
 * A KMM colour hex to a Compose [Color], via the design system's `toComposeColor()`.
 *
 * `HexColor.kt` says it is "the one and only place a hex string becomes a Compose Color",
 * and it is right to -- but this feature had hand-rolled the same
 * `Color(0xFF000000 or hex.toLongOrNull(16))` shift in three separate files instead of
 * calling it. That is the same drift that let feature code type raw hexes in the first
 * place, so they all funnel through here now.
 *
 * `toComposeColor()` throws on a malformed hex, which is right for a compile-time token
 * but not for [TrustLevel.colorHex] / `requirementColorHex`, which arrive as data. The
 * catch preserves each call site's previous "fall back rather than crash" behaviour.
 */
internal fun String.toEmergencyColor(): Color =
    runCatching { toComposeColor() }.getOrDefault(Color.Unspecified)

/** [TrustLevel]'s KMM-owned accent, so all three call sites resolve it identically. */
internal fun TrustLevel.accentColor(): Color = colorHex.toEmergencyColor()

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

/**
 * The label over a group of rows: uppercase, secondary ink, leading aligned.
 *
 * Figma `13 · Emergency Assistance` -> `section header`: Lato Regular 13 with 0.4
 * tracking, `pt-16 pb-8 px-20`. Regular, not Bold -- iOS made the same move, because at
 * Bold this label was heavier than the row titles under it and shouted over the content
 * it labels. `labelMedium` is 12sp, one short of the frame, so the size is spelled out.
 *
 * The 16 above is the whole gap for the first section on a screen. Between sections the
 * caller adds 8 of its own, which is how the frame's 24 is made up.
 */
@Composable
internal fun EmergencySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    /** Override for the frames that space their sections differently -- EA-02 uses 22. */
    topPadding: Dp = SakhiSpacing.space4,
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
        color = sakhiSecondaryLabel(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space5)
            .padding(top = topPadding, bottom = SakhiSpacing.space2),
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
        // 12, not 16. Figma `card` is `rounded-[12px]`; at 16 the corners ate into the
        // 56dp row and the card read rounder than the badges inside it.
        shape = RoundedCornerShape(SakhiRadius.lg),
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
 * A rounded square filled at full strength with a white glyph on top, matching iOS.
 *
 * It used to be a circle filled at 0.2 alpha with a tinted glyph, copied from `main`'s
 * RequirementTableViewCell. That is a soft, washed shape belonging to no platform, and iOS
 * moved off it: a rounded square with a white glyph is the idiom every native list row
 * uses, and it is most of what makes a list read as part of the OS rather than as a web
 * page. The colours are unchanged and still come from `requirementColorHex` in SakhiCore.
 *
 * The 0.25 radius ratio is shared with iOS, so a 30dp badge is 7.5dp on both platforms and
 * the two screenshots line up.
 */
@Composable
internal fun EmergencyBadgeIcon(
    icon: ImageVector,
    color: Color,
    size: Dp = 30.dp,
) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.25f)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/**
 * The flow's grabber, shared by the map sheet and by the modals that open over it so all
 * of them read as the same sheet.
 *
 * Figma: a 36x5 capsule sitting 8 below the sheet's edge, with the content frame starting
 * at 22 -- so 9 below it. Material's own handle carries 22 above and below a 4dp bar, which
 * is 48dp of dead space before any step's content begins.
 */
@Composable
internal fun EmergencySheetGrabber(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 9.dp)
                .width(36.dp)
                .height(5.dp)
                .background(sakhiSeparator(), RoundedCornerShape(percent = 50)),
        )
    }
}

/** Total height of [EmergencySheetGrabber], so a fixed-height sheet can subtract it. */
internal val EmergencyGrabberHeight = 22.dp

/**
 * The flow's primary call to action.
 *
 * Capsule, always: the design system's rule is "CTAs -> always Capsule(), never a fixed
 * radius", and iOS follows it on every button in this feature.
 */
@Composable
internal fun EmergencyPrimaryButton(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (enabled) SakhiUIColors.BRAND_PINK.toComposeColor()
                else SakhiUIColors.BRAND_PINK.toComposeColor().copy(alpha = 0.4f),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = SakhiSpacing.space4),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/** The quieter second action under a [EmergencyPrimaryButton]: outlined, not filled. */
@Composable
internal fun EmergencySecondaryButton(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pink = SakhiUIColors.BRAND_PINK.toComposeColor()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(sakhiSystemBackground())
            .border(1.dp, pink.copy(alpha = 0.35f), RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(vertical = SakhiSpacing.space4),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = pink,
        )
    }
}

/**
 * The header every pushed sheet in this flow wears.
 *
 * Figma `13 · Emergency Assistance` -> `sheet nav bar`: a 44dp row with "< Back" in brand
 * pink at x=14/y=12 (a 13dp chevron, 3 gap, Lato Regular 15) and the screen's own title
 * centred in Lato Bold 17. Android had no equivalent at all -- steps either drew their own
 * header or none -- which is why the title changed size and alignment from screen to screen
 * as she moved through the flow.
 *
 * The whole row is the back target, not just the chevron. Karan asked for this directly
 * ("chevron ya heading kisi pe bhi click karein back ho jae"), and on a screen she may be
 * using one-handed in a hurry, a 13dp glyph is the wrong place to demand precision. A
 * trailing action, where a screen has one, takes its own tap first.
 */
@Composable
internal fun EmergencySheetNavBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailingTitle: String? = null,
    onTrailing: (() -> Unit)? = null,
    trailingEnabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .then(if (onBack != null) Modifier.clickable(onClick = onBack) else Modifier),
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = SakhiUIColors.BRAND_PINK.toComposeColor(),
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "Back",
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = SakhiUIColors.BRAND_PINK.toComposeColor(),
                )
            }
        }

        Text(
            text = title,
            // Figma `Type/cardTitle`: Lato Bold 17.
            fontSize = 17.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiLabel(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                // Keeps a long title clear of both the Back group and any trailing action.
                .padding(horizontal = 76.dp),
        )

        if (onTrailing != null && trailingTitle != null) {
            Text(
                text = trailingTitle,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = if (trailingEnabled) {
                    SakhiUIColors.BRAND_PINK.toComposeColor()
                } else {
                    sakhiSecondaryLabel().copy(alpha = 0.5f)
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = SakhiSpacing.space5)
                    .clickable(enabled = trailingEnabled, onClick = onTrailing),
            )
        }
    }
}

/**
 * iOS `EmergencyChevron`: the disclosure on a row that pushes forward.
 *
 * There were two of these drawn by hand and they disagreed: the requirement rows used a
 * default-size icon in `sakhiSecondaryLabel`, the Sakhi list a 14dp one in tertiary. iOS
 * has a single component at 13pt tertiary, so this is that, used in both places.
 */
@Composable
internal fun EmergencyChevron(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        // Figma `chevron.right` is 13x13.
        modifier = modifier.size(13.dp),
        tint = sakhiTertiaryLabel(),
    )
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
        // Measured off the Figma flow (13 · Emergency Assistance): the row is 56 tall with
        // the 30dp badge inset 16 from the leading edge, so 16 / 13 and a 12 gap puts the
        // title at x=58 exactly as the frame does. Android had 12 / 8, which made every row
        // in the flow 46 tall instead of 56.
        modifier = modifier.fillMaxWidth().padding(
            horizontal = SakhiSpacing.space4,
            vertical = 13.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        leading()
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Figma row text is Lato Regular 15; `bodyLarge` is 16, which pushed the
            // longest label ("Hot Water Bag") into a second line inside a 175dp tile.
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = titleColor,
            modifier = Modifier.weight(1f),
        )
        // Bounded, so a long value cannot starve the label beside it.
        //
        // A Row measures its unweighted children first, against the full width, and only
        // then shares what is left with the weighted ones. On the session screen the
        // destination value ("2nd floor washroom at 2194, Sector 57, Gurugram") took
        // essentially all of it and squeezed "Destination" down to one character per line.
        Box(
            modifier = Modifier.widthIn(max = AccessoryMaxWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            accessory()
        }
    }
}

/** How much of a row a trailing value may take before it has to ellipsize. */
private val AccessoryMaxWidth = 180.dp

/**
 * iOS `EmergencyRowDivider`: hairline inset past the leading glyph, as a grouped table insets.
 *
 * 58, which is where the row title starts (16 inset + 30 badge + 12 gap) -- so the rule
 * begins exactly under the first letter. It was 62, four short of the text it should line
 * up with.
 */
@Composable
internal fun EmergencyRowDivider(leadingInset: Dp = 58.dp) {
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
        trust.accentColor()
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
internal fun EmergencySheetTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    // Leading, not centred, and titleLarge rather than a screen title.
    //
    // Both changes track iOS `EmergencySheetHeading`. A sheet heading is not a screen
    // heading: it sits a few points under the grabber with a list starting right below it,
    // and centred at screen weight it read as a banner over its own content. It is also a
    // question now, so it takes the alignment of body copy -- she is being asked, not told.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space5)
            // 28 / 20, matching iOS `DS.Spacing.xl` and `.ml`. The grabber sits a few dp
            // below the sheet's own edge, so at 16 the heading started almost on the
            // corner. There is no 28 on the Android scale, hence the literal.
            .padding(top = 28.dp, bottom = SakhiSpacing.space5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (trailing != null) {
            // Figma `heading` is a 14 gap, which is off the 4dp scale.
            Spacer(modifier = Modifier.size(14.dp))
            trailing()
        }
    }
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
