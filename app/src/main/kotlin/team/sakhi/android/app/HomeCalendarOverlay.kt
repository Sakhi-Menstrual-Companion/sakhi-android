package team.sakhi.android.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.LocalSakhiDarkTheme

private enum class CalendarDetent { Compact, Expanded }

/**
 * Home's calendar, presented the way iOS presents it: an overlay that slides up
 * *over* Home, with the hero still visible above it.
 *
 * Ported from `HomeCalendarSheet.swift`, whose own header describes the structure
 * this reproduces — "Layer 2 — HomeCalendarSheet (ZStack overlay, yOffset controls
 * show/hide)" — along with its two resting positions:
 *
 * ```
 * Compact:  actual_top = 0.40 × screenH   (60% of screen visible)
 * Expanded: actual_top = safeTop + 10
 *           "Keep this as a long detent, not full screen, so year view still
 *            feels like a sheet over HomeView."
 * ```
 *
 * Android previously put the calendar in a Material3 `ModalBottomSheet`, which is a
 * separate window with its own scrim that expands to essentially full screen. That
 * is what made the calendar "full screen" with a "long detent" and hid Home behind
 * a dim layer — a different interaction from iOS, not a styling difference. Being an
 * in-tree overlay instead is what lets the phase background and hero stay visible
 * behind it, exactly as in the iOS screenshots.
 *
 * Geometry is taken from the Swift source rather than eyeballed: 24dp top corners,
 * a 36×4 handle with 10dp top / 4dp bottom padding inside a 36dp touch row.
 */
@Composable
fun HomeCalendarOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    /**
     * Receives whether the sheet is at the expanded detent, and a setter so the content
     * can drive it (iOS's month-header chevron calls `snapToExpanded()`).
     * iOS keeps these as one concept: "compact = month, expanded = year".
     */
    content: @Composable ColumnScope.(expanded: Boolean, setExpanded: (Boolean) -> Unit) -> Unit,
) {
    // iOS computes its detents against the FULL screen height (`UIScreen.main.bounds`),
    // which includes the status and home-indicator areas. Compose's
    // `screenHeightDp` excludes the system bars, so using it directly made the sheet
    // rest lower than iOS's -- 0.40 of a smaller number. Add the insets back so the
    // 0.40 fraction is measured against the same quantity iOS measures against.
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    val topSafeInset = systemBars.calculateTopPadding()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp +
        topSafeInset + systemBars.calculateBottomPadding()

    val compactTop = screenHeight * COMPACT_TOP_FRACTION
    val expandedTop = topSafeInset + EXPANDED_TOP_GAP

    var detent by remember { mutableStateOf(CalendarDetent.Compact) }
    // Live finger movement, folded into the resting position so the sheet tracks the
    // drag 1:1 instead of only animating after release.
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val restingTop = when {
        !visible -> screenHeight
        detent == CalendarDetent.Expanded -> expandedTop
        else -> compactTop
    }
    val animatedTop by animateDpAsState(
        targetValue = restingTop,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "calendar_detent",
    )

    val sheetTop = (animatedTop + if (visible) dragOffset.dp else 0.dp).coerceIn(expandedTop, screenHeight)

    // This overlay is drawn outside the NavHost, so without a handler the system back
    // gesture fell straight through to the nav graph, popped Home and quit the app while
    // the calendar was still on screen. iOS's sheet dismisses on its own swipe-down; back
    // is Android's equivalent affordance and has to do the same thing.
    BackHandler(enabled = visible) {
        if (detent == CalendarDetent.Expanded) detent = CalendarDetent.Compact else onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height((screenHeight - sheetTop).coerceAtLeast(0.dp))
                .clip(RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER))
                // iOS uses `DS.Colors.systemBackground` here, which is plain white in
                // light mode — deliberately NOT the phase-tinted surface. Android was
                // using `colorScheme.surface`, which in the Sakhi palette is pink, so
                // the sheet blended into the phase background instead of reading as a
                // separate white card the way it does on iOS.
                .background(
                    if (LocalSakhiDarkTheme.current) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.White
                    },
                ),
        ) {
            // Drag handle. Owns the gesture, like iOS's `dragHandle` + `panGesture`,
            // so dragging inside the month grid still scrolls the grid rather than
            // fighting the sheet.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HANDLE_ROW_HEIGHT)
                    .pointerInput(detent) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, delta -> dragOffset += delta / density },
                            onDragEnd = {
                                // Always land on a real detent — never leave the sheet
                                // at an intermediate position (iOS makes the same
                                // point explicitly in its own drag handler).
                                when {
                                    dragOffset < -SNAP_THRESHOLD -> detent = CalendarDetent.Expanded
                                    dragOffset > SNAP_THRESHOLD ->
                                        if (detent == CalendarDetent.Expanded) {
                                            detent = CalendarDetent.Compact
                                        } else {
                                            onDismiss()
                                        }
                                }
                                dragOffset = 0f
                            },
                            onDragCancel = { dragOffset = 0f },
                        )
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = HANDLE_TOP_PADDING)
                        .size(width = HANDLE_WIDTH, height = HANDLE_HEIGHT)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }

            content(
                detent == CalendarDetent.Expanded,
                { expand -> detent = if (expand) CalendarDetent.Expanded else CalendarDetent.Compact },
            )
        }
    }
}

/** iOS `compactY = screenH * 0.20`, giving `actual_top = 0.40 * screenH`. */
private const val COMPACT_TOP_FRACTION = 0.40f
private val EXPANDED_TOP_GAP = 10.dp
private val SHEET_CORNER = 24.dp
// iOS uses a 36pt row (capsule + 10/4 padding), but that left a visibly wide gap
// between the grabber and the month bar on device. Tightened so the calendar sits
// higher in the sheet; the space it frees is spent between the grid and the bottom
// action bar instead.
private val HANDLE_ROW_HEIGHT = 22.dp
private val HANDLE_TOP_PADDING = 10.dp
private val HANDLE_WIDTH = 36.dp
private val HANDLE_HEIGHT = 4.dp
private const val SNAP_THRESHOLD = 56f
