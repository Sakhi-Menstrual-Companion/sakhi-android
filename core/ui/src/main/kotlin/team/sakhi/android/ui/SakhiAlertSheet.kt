package team.sakhi.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground

/**
 * Icon variants for [SakhiAlertSheet], mirroring iOS `SakhiAlertType`.
 *
 * Note the colour does NOT vary by type on iOS -- `var color: Color { DS.Colors.pink }`
 * returns pink for every case, and only the glyph changes. Reproduced here rather than
 * inventing a red/orange/green palette Android would not share with iOS.
 */
enum class SakhiAlertKind { Info, Success, Warning, Destructive }

/**
 * Sakhi's modal alert, a real port of iOS `SakhiAlertView` + `.sakhiAlert(...)`.
 *
 * iOS presents this as a BOTTOM SHEET, not a dialog:
 * `.sheet { SakhiAlertView(...).presentationDetents([.height(300)]) }` with the drag
 * indicator hidden and `DS.Radius.bottomSheet` corners. It is deliberately a fixed
 * 300pt tall for every alert in the app, "no taller, no shorter", so the experience
 * stays consistent.
 *
 * This exists because [SakhiAlert] is an INLINE banner -- a `Surface` with a `Row` --
 * and is not a substitute for a modal. Call sites that need iOS's alert were falling
 * back to a raw Material3 `AlertDialog`, which looks nothing like it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SakhiAlertSheet(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    onDismissRequest: () -> Unit,
    kind: SakhiAlertKind = SakhiAlertKind.Info,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
) {
    val sheetState = rememberSakhiModalSheetState()
    val accent = MaterialTheme.colorScheme.primary

    SakhiModalSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(AlertSheetHeight)
                .background(sakhiSystemBackground()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // iOS: `VStack(spacing: DS.Spacing.m) { iconBadge; textBlock }`
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AlertIconBadge(kind = kind, accent = accent)

                // iOS `textBlock`: `VStack(spacing: DS.Spacing.xs)`.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = message,
                        fontSize = 15.sp,
                        // iOS `.lineSpacing(3)` on a 15pt face.
                        lineHeight = 21.sp,
                        color = sakhiSecondaryLabel(),
                        textAlign = TextAlign.Center,
                        // iOS `.frame(maxWidth: 260)`.
                        modifier = Modifier.widthIn(max = AlertMessageMaxWidth),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // iOS `buttonRow`: `HStack(spacing: DS.Spacing.s)`, secondary first.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (secondaryLabel != null && onSecondaryClick != null) {
                    AlertButton(
                        label = secondaryLabel,
                        onClick = onSecondaryClick,
                        // iOS `greyBtn`: pink label on `pink.opacity(0.08)`, regular weight.
                        containerColor = accent.copy(alpha = 0.08f),
                        contentColor = accent,
                        bold = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                AlertButton(
                    label = primaryLabel,
                    onClick = onPrimaryClick,
                    // iOS `accentBtn`: white bold label on solid pink.
                    containerColor = accent,
                    contentColor = Color.White,
                    bold = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * iOS `iconBadge`: two dashed rings around a filled disc, the same brand motif
 * `SakhiLoadingView` uses. 66/50/36pt, `[4, 5]` dash at 1.5pt, tinted 0.14 / 0.28 / 0.10.
 */
@Composable
private fun AlertIconBadge(kind: SakhiAlertKind, accent: Color) {
    Box(
        modifier = Modifier.size(AlertBadgeOuterSize),
        contentAlignment = Alignment.Center,
    ) {
        AlertDashedRing(diameter = AlertBadgeOuterSize, color = accent.copy(alpha = 0.14f))
        AlertDashedRing(diameter = AlertBadgeInnerRingSize, color = accent.copy(alpha = 0.28f))
        Box(
            modifier = Modifier
                .size(AlertBadgeDiscSize)
                .background(accent.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = kind.glyph(),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}

@Composable
private fun AlertDashedRing(diameter: androidx.compose.ui.unit.Dp, color: Color) {
    Canvas(modifier = Modifier.size(diameter)) {
        drawCircle(
            color = color,
            radius = size.minDimension / 2f,
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(4.dp.toPx(), 5.dp.toPx()),
                ),
            ),
        )
    }
}

@Composable
private fun AlertButton(
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    bold: Boolean,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(SakhiRadius.full),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        // iOS: `.frame(height: 50)`.
        modifier = modifier.height(AlertButtonHeight),
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * iOS uses SF Symbols here (`info.circle.fill` and friends). Android has no equivalent
 * glyph set at this weight, so the sheet uses a text glyph rather than swapping in a
 * Material icon whose silhouette would not match.
 */
private fun SakhiAlertKind.glyph(): String = when (this) {
    SakhiAlertKind.Info -> "i"
    SakhiAlertKind.Success -> "✓"
    SakhiAlertKind.Warning -> "!"
    SakhiAlertKind.Destructive -> "!"
}

/** iOS `SakhiAlertView.preferredHeight` -- every Sakhi alert is exactly this tall. */
private val AlertSheetHeight = 300.dp

/** iOS `iconBadge`: outer dashed ring 66, inner dashed ring 50, filled disc 36. */
private val AlertBadgeOuterSize = 66.dp
private val AlertBadgeInnerRingSize = 50.dp
private val AlertBadgeDiscSize = 36.dp

/** iOS `textBlock`: `.frame(maxWidth: 260)` on the message. */
private val AlertMessageMaxWidth = 260.dp

/** iOS `accentBtn`/`greyBtn`: `.frame(height: 50)`. */
private val AlertButtonHeight = 50.dp
