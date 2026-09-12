package team.sakhi.android.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import team.sakhi.android.designsystem.SakhiMotion
import team.sakhi.android.designsystem.calendarSheetBackground
import team.sakhi.models.CyclePhase
import kotlin.math.roundToInt

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
 *
 * ── How the motion works, and why it is built this way ──────────────────────────
 *
 * The resting positions above are unchanged. What changed is everything about how the
 * sheet gets between them, because the previous version had four separate problems
 * that together made it read as "glitchy" rather than physical:
 *
 *  1. The sheet position was held in a `Dp` read with `by animateDpAsState(...)`
 *     directly in this composable's body. That read invalidated `HomeCalendarOverlay`
 *     on EVERY animation and drag frame, and `content()` is `CalendarScreen` — a
 *     1300-line tree with its own ViewModel, month grid and year `LazyColumn`. So the
 *     whole calendar recomposed 60-120 times a second for the length of every drag.
 *     It is now an `Animatable` in pixels whose `.value` is read ONLY inside the
 *     `Modifier.layout` measure block below. A snapshot read in that position
 *     invalidates the layout phase and nothing above it, so dragging the sheet no
 *     longer recomposes a single composable. This is the same fix, and the same
 *     reasoning, as `HomeScreen`'s `heroScrollProgress` lambda.
 *
 *  2. Release decided where to land purely on distance travelled (a fixed 56dp
 *     threshold). A short fast flick therefore did nothing and sprang back, which is
 *     the single clearest tell that a gesture is not physical. [chooseSettleTop] is now
 *     a line-for-line port of iOS's own `panGesture.onEnded`, which judges a release by
 *     where it was going (projected distance) as well as where it was let go.
 *
 *  3. The live drag offset was added ON TOP of a still-running `animateDpAsState`
 *     spring, and that spring was `DampingRatioLowBouncy`. A bouncy spring fighting a
 *     finger is where the visible wobble and overshoot came from. There is now one
 *     owner of the position ([sheetTop]), the finger writes to it directly through
 *     `snapTo`, and every settle uses iOS's own sheet spring, `SakhiMotion.sheet()`
 *     (`.interpolatingSpring(stiffness: 340, damping: 34)`, damping ratio ≈ 0.92).
 *
 *  4. The sheet had no resistance at its ends. iOS's `rubberBand` makes it heavy past
 *     its limits: pulled above the expanded position it follows the finger at 14%, and
 *     pulled down from compact at 22%, so a dismiss has to be meant. That resistance is
 *     most of the "weight" the iOS sheet has, and it is ported exactly in [rubberBand].
 *
 * The drag also lives on the whole sheet now rather than only the 22dp grabber row.
 * `Modifier.draggable` dispatches on the main pointer pass, so a scrollable child (the
 * year view's `LazyColumn`) still consumes its own vertical drags first and scrolls
 * normally; everything that is NOT a scroller — the grabber, the month header, the day
 * grid, the bottom bar — drags the sheet, which is what both iOS sheets and Material
 * sheets do. Taps are unaffected, because `draggable` only claims the gesture after
 * touch slop.
 */
@Composable
fun HomeCalendarOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    /**
     * The **selected day's** phase, which tints the sheet in dark mode. iOS passes
     * `snapshot.displayPhase` from a `HomeSelectedDaySnapshot`, so this re-tints as the
     * user taps around the grid. See [calendarSheetBackground].
     */
    phase: CyclePhase,
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

    // Every anchor is kept in PIXELS from here down. The position is animated and
    // dragged as a raw float, and converting Dp per frame would be pointless work in
    // the one place that runs on every frame.
    val density = LocalDensity.current
    val screenHeightPx = with(density) { screenHeight.toPx() }
    val expandedTopPx = with(density) { (topSafeInset + EXPANDED_TOP_GAP).toPx() }
    val compactTopPx = screenHeightPx * COMPACT_TOP_FRACTION
    // Off the bottom of the screen. Also the dismiss anchor: settling here IS the
    // dismissal, so a hard flick down leaves through the same motion a slow drag does.
    val hiddenTopPx = screenHeightPx
    // iOS's release thresholds, in points there and dp here.
    val thresholds = with(density) {
        ReleaseThresholds(
            dismissPastCompact = 80.dp.toPx(),
            dismissProjected = 600.dp.toPx(),
            collapsePastExpanded = 120.dp.toPx(),
            collapseProjected = 500.dp.toPx(),
            expandAboveCompact = 8.dp.toPx(),
            expandProjected = 120.dp.toPx(),
        )
    }

    var detent by remember { mutableStateOf(CalendarDetent.Compact) }

    /**
     * The sheet's top edge, in pixels from the top of the screen. The single owner of
     * where the sheet is: the finger writes to it with `snapTo`, releases and external
     * detent changes animate it with `animateTo`, and `Animatable`'s own mutex means a
     * new gesture cleanly takes over from an animation already in flight (grabbing a
     * moving sheet works, rather than the two fighting).
     */
    val sheetTop = remember { Animatable(hiddenTopPx) }
    val scope = rememberCoroutineScope()

    /**
     * Where the FINGER has taken the sheet's top edge, before resistance. iOS keeps the
     * same split (`committedOffset + translation` versus the rubber-banded
     * `activeOffset`): the release rules below judge the raw position, so pulling hard
     * against the resistance still counts as a pull even though the sheet barely moved.
     * A plain holder for the same reason as [pendingVelocity].
     */
    val rawTop = remember { floatArrayOf(0f) }

    /** iOS `HomeCalendarSheet.rubberBand`, ported line for line (multi-select aside). */
    fun rubberBand(raw: Float): Float = when {
        raw < expandedTopPx -> expandedTopPx + (raw - expandedTopPx) * RUBBER_BAND_ABOVE_EXPANDED
        detent == CalendarDetent.Compact && raw > compactTopPx ->
            compactTopPx + (raw - compactTopPx) * RUBBER_BAND_BELOW_COMPACT
        else -> raw
    }

    // Bumped on every drag release so that releasing back onto the detent you started
    // from still re-settles. Without it the settle effect below is keyed only on
    // `visible`/`detent`, neither of which changed in that case, so the sheet would
    // simply stay wherever the finger let go of it.
    var settleRequest by remember { mutableIntStateOf(0) }
    // A plain holder, NOT snapshot state: this is written during a gesture and read
    // once by the effect, and making it observable would put a recomposition back into
    // exactly the path this file exists to keep out of composition.
    val pendingVelocity = remember { floatArrayOf(0f) }

    val targetTopPx = when {
        !visible -> hiddenTopPx
        detent == CalendarDetent.Expanded -> expandedTopPx
        else -> compactTopPx
    }

    LaunchedEffect(targetTopPx, settleRequest) {
        val velocity = pendingVelocity[0]
        pendingVelocity[0] = 0f
        if (sheetTop.value != targetTopPx) {
            sheetTop.animateTo(
                targetValue = targetTopPx,
                // Half a pixel. The Float default (0.01) keeps the spring alive for frames
                // after the sheet has visibly stopped.
                animationSpec = SakhiMotion.sheet(visibilityThreshold = 0.5f),
                // One deliberate difference from iOS: its `withAnimation(spring)` starts
                // the settle from rest, so a hard flick visibly stops at release and then
                // restarts. Carrying the throw's velocity into the same spring removes that
                // hitch without changing where it lands or how the spring settles.
                initialVelocity = velocity,
            )
        }
    }

    val draggableState = rememberDraggableState { delta ->
        rawTop[0] += delta
        val drawn = rubberBand(rawTop[0]).coerceAtMost(hiddenTopPx)
        scope.launch { sheetTop.snapTo(drawn) }
    }

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
                // The ONLY place `sheetTop.value` is read. Inside a `Modifier.layout`
                // measure block a snapshot read invalidates the layout phase alone, so a
                // drag re-measures this subtree and recomposes nothing. Reading the same
                // value in the composable body (which is what `by animateDpAsState` did)
                // recomposed the entire calendar every frame instead.
                .layout { measurable, constraints ->
                    val height = (screenHeightPx - sheetTop.value)
                        .roundToInt()
                        .coerceIn(0, constraints.maxHeight)
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = height, maxHeight = height),
                    )
                    layout(placeable.width, height) { placeable.place(0, 0) }
                }
                // Whole-sheet drag. Scrollable children (the year view's LazyColumn) take
                // the gesture first on the main pass and keep scrolling; everything else
                // moves the sheet. See this file's header for why it is no longer confined
                // to the grabber row.
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    enabled = visible,
                    // Grabbing the sheet mid-animation starts from where it visibly is, so
                    // it never jumps under the finger.
                    onDragStarted = { rawTop[0] = sheetTop.value },
                    onDragStopped = { velocity ->
                        val settleTo = chooseSettleTop(
                            rawTopPx = rawTop[0],
                            projectedPx = SakhiMotion.projectedDistance(velocity),
                            isExpanded = detent == CalendarDetent.Expanded,
                            expandedTopPx = expandedTopPx,
                            compactTopPx = compactTopPx,
                            hiddenTopPx = hiddenTopPx,
                            thresholds = thresholds,
                        )
                        pendingVelocity[0] = velocity
                        when (settleTo) {
                            expandedTopPx -> detent = CalendarDetent.Expanded
                            compactTopPx -> detent = CalendarDetent.Compact
                            // Settling off the bottom is the dismissal. Telling the parent
                            // flips `visible`, which moves `targetTopPx` to the hidden
                            // anchor, and the effect above runs the throw out with the
                            // velocity it was given.
                            else -> onDismiss()
                        }
                        settleRequest++
                    },
                )
                .clip(RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER))
                // iOS `HomeCalendarSheet.sheetBackground`: plain `systemBackground` in
                // light -- deliberately NOT the phase-tinted surface -- and one of three
                // phase tokens in dark, chosen by the SELECTED day's phase.
                //
                // Android has had three different wrong answers here. First
                // `colorScheme.surface` (pink), so the sheet blended into the phase
                // background. Then a hand-rolled light/dark `if` whose dark branch went
                // back to `colorScheme.surface`, reintroducing that bug in dark only.
                // Then a flat `sakhiSystemBackground()`, which fixed the pink but ported
                // only the light half of `sheetBackground` -- so the sheet never re-tinted
                // on date selection, which is what Karan spotted. The whole function is
                // ported now and lives in the design system with the rest of the phase
                // colour, not inline here.
                .background(calendarSheetBackground(phase)),
        ) {
            // Grabber. Purely the visual affordance now — the gesture belongs to the whole
            // sheet above, so this no longer owns a `pointerInput` of its own.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HANDLE_ROW_HEIGHT),
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

/** iOS's release thresholds from `HomeCalendarSheet.panGesture.onEnded`, in pixels. */
private class ReleaseThresholds(
    val dismissPastCompact: Float,
    val dismissProjected: Float,
    val collapsePastExpanded: Float,
    val collapseProjected: Float,
    val expandAboveCompact: Float,
    val expandProjected: Float,
)

/**
 * Where a release lands, ported line for line from iOS `HomeCalendarSheet.panGesture
 * .onEnded` (its multi-select branches aside, which Android's sheet does not have).
 * Anchors are top-edge positions in pixels, so SMALLER is higher up the screen.
 *
 * Each rule is "dragged far enough OR thrown hard enough", where "thrown" is the
 * projected glide distance rather than raw speed, exactly as iOS compares
 * `predictedEndTranslation - translation`. The thresholds are lopsided on purpose, and
 * that asymmetry is a big part of the iOS feel: a light flick up (120pt projected)
 * expands, while closing needs a real throw (500-600pt) or a long drag.
 *
 *  - compact, pulled 80pt below compact or thrown down 600pt  -> dismiss
 *  - expanded, pulled 120pt below expanded or thrown down 500pt -> compact
 *  - compact, pushed 8pt above compact or thrown up 120pt       -> expanded
 *  - otherwise expanded stays expanded, and compact goes to whichever side of the
 *    compact line it was released on
 */
private fun chooseSettleTop(
    rawTopPx: Float,
    projectedPx: Float,
    isExpanded: Boolean,
    expandedTopPx: Float,
    compactTopPx: Float,
    hiddenTopPx: Float,
    thresholds: ReleaseThresholds,
): Float = when {
    !isExpanded && (rawTopPx > compactTopPx + thresholds.dismissPastCompact ||
        projectedPx > thresholds.dismissProjected) -> hiddenTopPx
    isExpanded && (rawTopPx > expandedTopPx + thresholds.collapsePastExpanded ||
        projectedPx > thresholds.collapseProjected) -> compactTopPx
    !isExpanded && (rawTopPx < compactTopPx - thresholds.expandAboveCompact ||
        projectedPx < -thresholds.expandProjected) -> expandedTopPx
    isExpanded -> expandedTopPx
    rawTopPx < compactTopPx -> expandedTopPx
    else -> compactTopPx
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

/**
 * iOS `rubberBand`: how much of the finger's travel the sheet follows once past an end.
 * 14% above the expanded position, 22% below compact.
 */
private const val RUBBER_BAND_ABOVE_EXPANDED = 0.14f
private const val RUBBER_BAND_BELOW_COMPACT = 0.22f
