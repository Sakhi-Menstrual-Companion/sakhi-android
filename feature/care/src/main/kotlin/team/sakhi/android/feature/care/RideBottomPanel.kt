package team.sakhi.android.feature.care

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.sakhiTertiaryLabel

/**
 * The panel that sits over the map on the walk screens. Android's `WalkBottomPanel`.
 *
 * It keeps iOS's shape and its behaviour: a rounded top, a grabber, a resting height that
 * never covers the map, and a pull to bring up the rest. At rest the whole panel answers a
 * drag, so the bar reads as one thing and not a tiny scrolling list; once it is up, its
 * content scrolls and only the grabber moves it. Tapping the grabber toggles it, and it is
 * named "Show more" or "Show less" for TalkBack, the way iOS names it for VoiceOver.
 *
 * The panel follows the finger while it is dragged, then settles on a spring from where the
 * finger left it, and a quick flick opens it even from a short drag.
 *
 * [restingHeight] is the panel's own height at rest. The navigation bar's room is added here,
 * so a caller never has to. [footer] is pinned under the content at every height; it must
 * pad for the navigation bar itself, the way [RideFooterButtons] does.
 *
 * The one shadow Sakhi allows besides the check-in box's: this panel sits on a map, whose own
 * light tones run straight into the panel's pink without it.
 */
@Composable
internal fun RideBottomPanel(
    restingHeight: Dp,
    fullHeight: Dp,
    modifier: Modifier = Modifier,
    /** A new value puts the panel back at rest, for a different walk. */
    resetKey: Any? = null,
    footer: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val restingPx = with(density) { (restingHeight + bottomInset).toPx() }
    val fullPx = with(density) { fullHeight.toPx() }.coerceAtLeast(restingPx)

    var expanded by remember(resetKey) { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var settleTick by remember { mutableIntStateOf(0) }
    var heightPx by remember { mutableFloatStateOf(restingPx) }

    // Settles on a spring to wherever it should rest. Held still while a finger is on it, and
    // started again from the finger's own position when it lifts.
    LaunchedEffect(restingPx, fullPx, expanded, dragging, settleTick) {
        if (dragging) return@LaunchedEffect
        animate(
            initialValue = heightPx,
            targetValue = if (expanded) fullPx else restingPx,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f),
        ) { value, _ -> heightPx = value }
    }

    val dragState = rememberDraggableState { delta ->
        // Past either end it stops following. A pull up is a negative delta.
        heightPx = (heightPx - delta).coerceIn(restingPx * 0.7f, fullPx)
    }
    val drag = Modifier.draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStarted = { dragging = true },
        onDragStopped = { velocity ->
            // Where the flick would carry the panel, not only where the finger stopped.
            val projected = heightPx - velocity * 0.15f
            expanded = projected > (restingPx + fullPx) / 2f
            dragging = false
            settleTick += 1
        },
    )

    val grabberLabel = stringResource(if (expanded) R.string.ride_panel_show_less else R.string.ride_panel_show_more)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { heightPx.toDp() }),
        color = RideStyle.ground,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        shadowElevation = 12.dp,
    ) {
        // At rest a drag anywhere on the panel moves it. Once it is up, the list has the drag.
        Column(modifier = Modifier.fillMaxSize().then(if (expanded) Modifier else drag)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .then(drag)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) {
                        expanded = !expanded
                        settleTick += 1
                    }
                    .semantics { contentDescription = grabberLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 5.dp)
                        .background(sakhiTertiaryLabel().copy(alpha = 0.45f), CircleShape),
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .then(if (footer == null) Modifier.navigationBarsPadding() else Modifier),
                userScrollEnabled = expanded,
                content = content,
            )
            footer?.invoke()
        }
    }
}
