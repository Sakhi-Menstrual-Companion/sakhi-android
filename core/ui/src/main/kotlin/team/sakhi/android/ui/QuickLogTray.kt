package team.sakhi.android.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.View
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.models.FlowIntensity
import kotlin.math.roundToInt

// Quick log, the flow levels the log button offers before the full sheet (Karan, 2026-09-13).
//
// Karan's design: tapping + slides everything beside the log button out to the left, a close
// comes in where the leading button was, and a tray of the four flow levels grows out of the
// + button, the same height as the Ask Sakhi bar it replaces. The + button becomes "more
// symptoms", the way into the full sheet. Picking a level fills it, saves it, and the tray
// slides back into the button. It replaced a white card with a drop shadow that blended into
// the white calendar behind it. Four other designs were built alongside it for comparison the
// same day, and Karan kept this one.

internal val QuickLogLevels = listOf(
    FlowIntensity.SPOTTING,
    FlowIntensity.LIGHT,
    FlowIntensity.MEDIUM,
    FlowIntensity.HEAVY,
)

internal fun FlowIntensity.dropCount(): Int = when (this) {
    FlowIntensity.SPOTTING -> 1
    FlowIntensity.LIGHT -> 2
    FlowIntensity.MEDIUM -> 3
    FlowIntensity.HEAVY -> 4
}

/** Content on a solid accent fill: dark on a near-white accent (dark theme), white otherwise. */
internal fun onAccent(accent: Color): Color = if (accent.luminance() > 0.6f) Color.Black else Color.White

/** One droplet per point on the flow scale. */
@Composable
internal fun FlowDrops(count: Int, tint: Color, dropSize: Dp, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(-(dropSize * 0.2f))) {
        repeat(count) {
            Icon(
                imageVector = Icons.Filled.WaterDrop,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(dropSize),
            )
        }
    }
}

/**
 * A choice holds for a moment, long enough to see the level fill, and then is saved and the
 * quick log closes. Tapping the level already logged clears it, as the old menu did.
 */
internal class QuickLogPick(val shown: FlowIntensity?, val pick: (FlowIntensity) -> Unit)

private const val QuickLogPickHoldMs = 230L

@Composable
internal fun rememberQuickLogPick(
    open: Boolean,
    selectedFlow: FlowIntensity?,
    onPick: (FlowIntensity?) -> Unit,
    onClose: () -> Unit,
): QuickLogPick {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var pending by remember { mutableStateOf<FlowIntensity?>(null) }
    var clearing by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            pending = null
            clearing = false
        }
    }
    val latestPick by rememberUpdatedState(onPick)
    val latestClose by rememberUpdatedState(onClose)
    val shown = when {
        clearing -> null
        pending != null -> pending
        else -> selectedFlow
    }
    return QuickLogPick(shown) { level ->
        if (pending == null && !clearing) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val clear = selectedFlow == level
            if (clear) clearing = true else pending = level
            scope.launch {
                delay(QuickLogPickHoldMs)
                latestPick(if (clear) null else level)
                latestClose()
            }
        }
    }
}

// ── 1. Tray ─────────────────────────────────────────────────────────────────────────────

/**
 * Karan's design, drawn over the bar itself. Everything beside the log button has already
 * slid out to the left (the bar does that); this layer brings in a close where the leading
 * button was and grows a tray of the four levels out of the log button, the same height as
 * the Ask Sakhi bar it replaces. Picking a level fills it, and the tray then slides back
 * into the button. [progress] runs 0 (closed) to 1 (open).
 */
@Composable
internal fun QuickLogTrayLayer(
    progress: Float,
    open: Boolean,
    accent: Color,
    selectedFlow: FlowIntensity?,
    onPick: (FlowIntensity?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pick = rememberQuickLogPick(open, selectedFlow, onPick, onClose)
    // The labels arrive once the tray is wide enough to hold them, and drift in from the
    // button's side, so the tray reads as coming out of it.
    val contentIn = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
    val trayFill = accent.copy(alpha = 0.12f).compositeOver(sakhiSystemBackground())

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    alpha = progress.coerceIn(0f, 1f)
                    translationX = -(1f - progress) * 40.dp.toPx()
                }
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.10f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.sakhi_quick_log_close),
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).height(50.dp)) {
            val full = maxWidth
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(full * progress.coerceIn(0.04f, 1f))
                    .fillMaxHeight()
                    // Fill only. A hairline round it read as generated rather than designed
                    // (Karan, 2026-09-13).
                    .clip(CircleShape)
                    .background(trayFill),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth(Alignment.End, unbounded = true)
                        .width(full)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            alpha = contentIn
                            translationX = (1f - contentIn) * 18.dp.toPx()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuickLogLevels.forEach { level ->
                        val selected = pick.shown == level
                        val fill by animateColorAsState(
                            targetValue = if (selected) accent else Color.Transparent,
                            animationSpec = tween(180),
                            label = "trayLevelFill",
                        )
                        val ink = if (selected) onAccent(accent) else accent
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(CircleShape)
                                .background(fill)
                                .clickable { pick.pick(level) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            FlowDrops(count = level.dropCount(), tint = ink, dropSize = 9.dp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = flowDisplayName(context, level),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) ink else sakhiLabel(),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // The log button stays where it is, underneath; it has turned into "more symptoms".
        Spacer(modifier = Modifier.size(50.dp))
    }
}
