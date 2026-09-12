package team.sakhi.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import team.sakhi.android.designsystem.SakhiMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The app's ONE sheet presentation, extracted from Home's calendar overlay.
 *
 * The calendar was the only surface in the app with a real iOS-feeling sheet: it was a
 * line-for-line port of `HomeCalendarSheet.swift` and every other sheet was a Material3
 * `ModalBottomSheet`, which has a different spring, no rubber band, and decides a release
 * on raw velocity. Karan's ask was to stop having two answers: "jitna smooth calendar ka
 * appearance and dismiss animation hai, i want isko utility mai banao, and isko har sheet
 * ya jo bhi presentation hai usme use karo."
 *
 * So the motion lives here once, and both shapes of sheet the app has are the same code:
 *
 *  - [SakhiSheetRest.Content] -- a single detent. The sheet is as tall as its content,
 *    capped just below the status bar. This is what every normal sheet needs, and it is
 *    what Material sized a `ModalBottomSheet` to, so no existing sheet's content has to
 *    change shape.
 *  - [SakhiSheetRest.ScreenFraction] -- the calendar's compact detent
 *    (`compactY = screenH * 0.20`, i.e. `actual_top = 0.40 * screenH`), with a second
 *    expanded detent just below the status bar when `expandable` is on.
 *
 * ── The four properties this motion has, and why ────────────────────────────────────
 *
 *  1. POSITION IS ONE `Animatable` IN PIXELS, AND ITS VALUE IS NEVER READ IN A
 *     COMPOSABLE BODY. `state.top.value` is read only inside the `Modifier.layout`
 *     measure block and the `placeWithLayer` layer block below. A snapshot read in those
 *     positions invalidates layout/draw and nothing above them, so dragging a sheet does
 *     not recompose a single composable -- which matters because the calendar's content
 *     is a 1300-line screen with its own ViewModel, month grid and year `LazyColumn`.
 *     Same fix, and same reasoning, as `HomeScreen`'s `heroScrollProgress` lambda.
 *
 *  2. HEIGHT AND POSITION ARE SEPARATE. At or below the resting detent the height is
 *     fixed and the sheet TRANSLATES (present, dismiss, drag-down, rubber band). Only
 *     above the resting detent does the height grow with the top edge. The two branches
 *     meet exactly at the rest line, so nothing jumps where they change over. Deriving
 *     the height straight from the top edge is what used to squash the calendar flat on
 *     the way out instead of sliding it away ("calendar ki height choti kyun ho rahi hai,
 *     usse toh sirf niche jana hai").
 *
 *  3. A RELEASE IS JUDGED ON WHERE IT WAS GOING, not on raw speed. [chooseSettleTop] is a
 *     port of iOS `HomeCalendarSheet.panGesture.onEnded`, comparing the raw finger
 *     position and the projected glide distance (`SakhiMotion.projectedDistance`) against
 *     iOS's own thresholds. A short fast flick therefore lands, rather than springing
 *     back, which is the clearest single tell that a gesture is not physical.
 *
 *  4. THE ENDS RESIST. iOS's `rubberBand` follows the finger at 14% above the expanded
 *     detent and 22% below the resting one, so a dismiss has to be meant. That resistance
 *     is most of the "weight" the iOS sheet has.
 *
 * Every settle uses `SakhiMotion.sheet()`, iOS's `.interpolatingSpring(stiffness: 340,
 * damping: 34)`, with the release velocity carried in as `initialVelocity` so a hard flick
 * does not visibly stop at release and then restart.
 *
 * The drag lives on the WHOLE sheet via `Modifier.draggable`, which dispatches on the main
 * pointer pass, so a scrollable child (a list inside the sheet) still consumes its own
 * vertical drags and scrolls normally. Taps are unaffected, because `draggable` only
 * claims the gesture after touch slop.
 *
 * ── Why modal sheets still present in a window ──────────────────────────────────────
 *
 * [SakhiSheetLayer] is in-tree: it is a `Box` in the caller's own composition, which is
 * what lets the calendar sit over Home with the phase background and hero still visible
 * behind it. [SakhiModalSheet] wraps that same layer in a Compose `Dialog`, because the
 * seventeen `SakhiAlertSheet` call sites and the emergency sheets are raised from deep
 * inside `Column`s and scroll containers -- a full-size in-tree overlay there would take
 * layout space from its siblings and be drawn under anything composed after it. Material's
 * `ModalBottomSheet` was a window for exactly this reason, and keeping that one property
 * is what lets every call site stay where it is.
 *
 * The dialog is configured `usePlatformDefaultWidth = false` (so it fills the window and
 * Compose's `DialogWindowTheme`, which does not dim, applies) and
 * `decorFitsSystemWindows = false` (so the window is edge to edge and reports real IME
 * insets, which is what makes [Modifier.imePadding] work for the sheets that hold a text
 * field). That is the same pair Material3's own `ModalBottomSheetDialogWrapper` uses.
 */
@Composable
fun rememberSakhiSheetState(): SakhiSheetState = remember { SakhiSheetState() }

/**
 * Kept under the old name and the old default so `SakhiAlertSheet` and the other existing
 * call sites do not have to change a line.
 *
 * `skipPartiallyExpanded` is accepted and ignored: it was a Material `SheetState` concept
 * (a half detent this app never used -- every call site passed `true`). A Sakhi sheet's
 * detents come from [SakhiSheetRest] instead.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun rememberSakhiModalSheetState(
    skipPartiallyExpanded: Boolean = true,
): SakhiSheetState = rememberSakhiSheetState()

/** Where a sheet's top edge comes to rest. */
sealed interface SakhiSheetRest {

    /**
     * As tall as the content, capped so the top edge never rises above the status bar.
     * What Material sized a `ModalBottomSheet` to, and what every normal sheet uses.
     */
    data object Content : SakhiSheetRest

    /**
     * A fraction of the FULL screen height, measured the way iOS measures it.
     * The calendar's `actual_top = 0.40 * screenH`.
     */
    data class ScreenFraction(val fraction: Float) : SakhiSheetRest
}

/**
 * Where the sheet is, and the only handle callers need on it.
 *
 * The position is an `Animatable` rather than snapshot state the composition reads, for
 * the reason in this file's header. [isExpanded] and [isVisible] ARE snapshot state,
 * because they change once per gesture rather than once per frame.
 */
@Stable
class SakhiSheetState internal constructor() {

    /**
     * The sheet's top edge, in pixels from the top of the screen. The single owner of
     * where the sheet is: the finger writes to it with `snapTo`, releases and detent
     * changes animate it with `animateTo`, and `Animatable`'s own mutex means a new
     * gesture cleanly takes over from an animation already in flight (grabbing a moving
     * sheet works, rather than the two fighting).
     */
    internal val top = Animatable(UNPLACED)

    /** Null until the sheet has been laid out once and its detents are known. */
    internal var anchors: SakhiSheetAnchors? by mutableStateOf(null)

    /** Set by [hide]. Overrides the detent target until the sheet is presented again. */
    internal var hideRequested: Boolean by mutableStateOf(false)

    /** The second detent, for sheets that have one. Two-way: content can drive it. */
    var isExpanded: Boolean by mutableStateOf(false)

    /** True from the moment the sheet is presented until it has settled off-screen. */
    var isVisible: Boolean by mutableStateOf(false)
        internal set

    /**
     * Takes the sheet off-screen with the shared spring and suspends until it has landed.
     *
     * Suspending is the point: call sites close a sheet by hiding it and only then
     * dropping it out of composition (`HomeNavHost.dismissOverlaySheet`,
     * `PhoneScreen.closeCountryPicker`), so returning early would cut the exit animation
     * off at the first frame.
     */
    suspend fun hide() {
        hideRequested = true
        // Waits on the real position rather than on a duration, so it is right whatever
        // distance the sheet had left to travel. The timeout is a safety net only: a
        // caller that is waiting to unmount a sheet must never be able to hang because a
        // sheet was hidden before it was ever laid out.
        withTimeoutOrNull(SettleTimeoutMs) {
            snapshotFlow {
                val settled = anchors
                settled != null && !top.isRunning && top.value >= settled.hiddenTop - SettleEpsilonPx
            }.first { it }
        }
        isVisible = false
    }
}

/** The three positions a sheet can settle at, in pixels from the top of the screen. */
internal data class SakhiSheetAnchors(
    val restTop: Float,
    val expandedTop: Float,
    val hiddenTop: Float,
)

/**
 * The sheet motion itself, composed IN-TREE.
 *
 * Use this directly only when the sheet has to share the caller's window, the way Home's
 * calendar does. Everything else wants [SakhiModalSheet], which is this plus a window.
 *
 * @param onDismiss called AFTER the sheet has finished animating off-screen, never at the
 *   moment the gesture ended. A caller that unmounts the sheet in response therefore
 *   cannot cut the exit short.
 * @param present drives the sheet in and out from outside. Dismissal from within (drag,
 *   scrim tap) does not go through this, it calls [onDismiss].
 * @param sheetModifier applied to the sheet container after the drag, i.e. this is where
 *   a caller puts its own `clip` and `background`.
 */
@Composable
fun SakhiSheetLayer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    state: SakhiSheetState = rememberSakhiSheetState(),
    present: Boolean = true,
    rest: SakhiSheetRest = SakhiSheetRest.Content,
    expandable: Boolean = false,
    scrimColor: Color? = null,
    dismissOnScrimTap: Boolean = true,
    sheetModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // iOS computes its detents against the FULL screen height (`UIScreen.main.bounds`),
    // which includes the status and home-indicator areas. Compose's `screenHeightDp`
    // excludes the system bars, so using it directly made the calendar rest lower than
    // iOS's -- 0.40 of a smaller number. Add the insets back so the fraction is measured
    // against the same quantity iOS measures against.
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    val topSafeInset = systemBars.calculateTopPadding()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp +
        topSafeInset + systemBars.calculateBottomPadding()

    // Every anchor is kept in PIXELS from here down. The position is animated and dragged
    // as a raw float, and converting Dp per frame would be pointless work in the one place
    // that runs on every frame.
    val density = LocalDensity.current
    val screenHeightPx = with(density) { screenHeight.toPx() }
    // iOS `HomeCalendarSheet` expanded: `actual_top = safeTop + 10`, and its own note on
    // why it is not full screen -- "keep this as a long detent, not full screen, so year
    // view still feels like a sheet over HomeView". The same line is the ceiling for every
    // other sheet: none of them ever runs under the status bar.
    val minTopPx = with(density) { (topSafeInset + SheetTopGap).toPx() }
    // Off the bottom of the screen. Also the dismiss anchor: settling here IS the
    // dismissal, so a hard flick down leaves through the same motion a slow drag does.
    val hiddenTopPx = screenHeightPx

    val fractionRestTopPx = (rest as? SakhiSheetRest.ScreenFraction)
        ?.let { screenHeightPx * it.fraction }

    // For a content-sized sheet the rest line is only known once the content has been
    // measured, so `onSizeChanged` below publishes it here. It changes when the CONTENT
    // changes size -- including when `imePadding` grows it as the keyboard opens, which is
    // what lifts the sheet above the keyboard -- never while the sheet moves.
    var measuredRestTopPx by remember { mutableFloatStateOf(Float.NaN) }
    val restTopPx = fractionRestTopPx ?: measuredRestTopPx
    val expandedTopPx = if (expandable) minTopPx else restTopPx

    val anchors = remember(restTopPx, expandedTopPx, hiddenTopPx) {
        if (restTopPx.isNaN()) null else SakhiSheetAnchors(restTopPx, expandedTopPx, hiddenTopPx)
    }
    SideEffect { state.anchors = anchors }

    // iOS's release thresholds, in points there and dp here.
    val thresholds = remember(density) {
        with(density) {
            ReleaseThresholds(
                dismissPastRest = 80.dp.toPx(),
                dismissProjected = 600.dp.toPx(),
                collapsePastExpanded = 120.dp.toPx(),
                collapseProjected = 500.dp.toPx(),
                expandAboveRest = 8.dp.toPx(),
                expandProjected = 120.dp.toPx(),
            )
        }
    }

    val scope = rememberCoroutineScope()

    /**
     * Where the FINGER has taken the sheet's top edge, before resistance. iOS keeps the
     * same split (`committedOffset + translation` versus the rubber-banded `activeOffset`):
     * the release rules judge the raw position, so pulling hard against the resistance
     * still counts as a pull even though the sheet barely moved. A plain holder for the
     * same reason as [pendingVelocity].
     */
    val rawTop = remember { floatArrayOf(0f) }

    // Bumped on every drag release so that releasing back onto the detent you started from
    // still re-settles. Without it the settle effect below is keyed only on the target,
    // which did not change in that case, so the sheet would simply stay where the finger
    // let go of it.
    var settleRequest by remember { mutableIntStateOf(0) }
    // A plain holder, NOT snapshot state: this is written during a gesture and read once by
    // the effect, and making it observable would put a recomposition back into exactly the
    // path this file exists to keep out of composition.
    val pendingVelocity = remember { floatArrayOf(0f) }

    // Latched for the length of one dismissal so a drag release, a scrim tap and a back
    // press cannot each start their own. Cleared on the next present, which matters for a
    // layer that stays composed across dismissals the way Home's calendar does.
    var dismissing by remember { mutableStateOf(false) }

    // A sheet state outlives one presentation (`HomeNavHost` remembers one state for every
    // overlay sheet), so a previous `hide()` has to be cleared before this one can present.
    LaunchedEffect(present) {
        if (present) {
            state.hideRequested = false
            state.isVisible = true
            dismissing = false
        }
    }

    val targetTopPx = when {
        anchors == null -> Float.NaN
        !present || state.hideRequested -> hiddenTopPx
        expandable && state.isExpanded -> expandedTopPx
        else -> restTopPx
    }

    LaunchedEffect(targetTopPx, settleRequest) {
        if (targetTopPx.isNaN()) return@LaunchedEffect
        val velocity = pendingVelocity[0]
        pendingVelocity[0] = 0f
        // First layout. Start from off the bottom so the first move is a present rather
        // than the sheet appearing at rest.
        if (state.top.value == UNPLACED) state.top.snapTo(hiddenTopPx)
        if (state.top.value != targetTopPx) {
            state.top.animateTo(
                targetValue = targetTopPx,
                // Half a pixel. The Float default (0.01) keeps the spring alive for frames
                // after the sheet has visibly stopped.
                animationSpec = SakhiMotion.sheet(visibilityThreshold = 0.5f),
                // One deliberate difference from iOS: its `withAnimation(spring)` starts the
                // settle from rest, so a hard flick visibly stops at release and then
                // restarts. Carrying the throw's velocity into the same spring removes that
                // hitch without changing where it lands or how the spring settles.
                initialVelocity = velocity,
            )
        }
    }

    fun dismiss(velocity: Float = 0f) {
        if (dismissing) return
        dismissing = true
        pendingVelocity[0] = velocity
        scope.launch {
            state.hide()
            onDismiss()
        }
    }

    /** iOS `HomeCalendarSheet.rubberBand`, ported line for line (multi-select aside). */
    fun rubberBand(raw: Float): Float = when {
        // Above the topmost detent. A sheet with a second detent stretches at 14% the way
        // iOS's does; a single-detent sheet is held hard at its rest line instead, because
        // its height is its content's and stretching would open a gap under it.
        raw < expandedTopPx ->
            if (expandable) expandedTopPx + (raw - expandedTopPx) * RUBBER_BAND_ABOVE_EXPANDED
            else expandedTopPx
        !state.isExpanded && raw > restTopPx ->
            restTopPx + (raw - restTopPx) * RUBBER_BAND_BELOW_REST
        else -> raw
    }

    val draggableState = rememberDraggableState { delta ->
        rawTop[0] += delta
        val drawn = rubberBand(rawTop[0]).coerceAtMost(hiddenTopPx)
        scope.launch { state.top.snapTo(drawn) }
    }

    /**
     * How far in the sheet is, 0 fully hidden to 1 at rest. Read only from a
     * `graphicsLayer` block, so the scrim fades with the sheet without recomposing.
     */
    val scrimAlpha: () -> Float = remember(state) {
        {
            val settled = state.anchors
            val travel = settled?.let { it.hiddenTop - it.restTop } ?: 0f
            if (settled == null || travel <= 0f) {
                0f
            } else {
                (1f - (state.top.value - settled.restTop) / travel).coerceIn(0f, 1f)
            }
        }
    }

    // `sheetHeightPx` is derived, so during a present, a dismiss or a drag-down it does not
    // change at all and the layout below never re-measures: those are pure layer
    // translations. Above the rest line it re-measures, which is the price of the content
    // genuinely resizing.
    val heightClamp = fractionRestTopPx ?: Float.MAX_VALUE
    val sheetHeightState = remember(screenHeightPx, heightClamp, state) {
        derivedStateOf { screenHeightPx - min(state.top.value, heightClamp) }
    }
    val sheetHeightPx: () -> Float = remember(sheetHeightState) { { sheetHeightState.value } }

    Box(modifier = modifier.fillMaxSize()) {
        // Drawn only when it does something. The calendar wants neither a dim nor a tap
        // target, because Home stays live behind it and scrolling Home is what dismisses it.
        if (scrimColor != null || dismissOnScrimTap) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrimAlpha() }
                    .background(scrimColor ?: Color.Transparent)
                    .then(
                        if (dismissOnScrimTap) {
                            Modifier.pointerInput(Unit) { detectTapGestures { dismiss() } }
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Height and position are deliberately NOT the same thing.
                //
                // A sheet leaving the screen is a rigid object moving, so:
                //
                //   at or below rest   height stays as it is, the sheet TRANSLATES
                //                      (present, dismiss, drag-down, rubber band)
                //   above rest         height grows with the top edge, bottom stays put
                //                      (expanding toward the second detent, and back)
                //
                // Both branches meet exactly at the rest line (height = rest height,
                // offset = 0), so there is no jump where they change over. Resizing is kept
                // only for rest <-> expanded, where the content really does change and has
                // to re-lay out anyway -- iOS animates its frame height across that same
                // range for the same reason.
                //
                // Either way `state.top.value` is read only inside the layout and layer
                // blocks, never in the composable body, so the sheet's content never
                // recomposes while it moves.
                // A content-sized sheet's rest line is its own height, so it is published
                // from here rather than computed in composition. `onSizeChanged` is the
                // sanctioned place to write snapshot state out of layout (it is where
                // Material3's own sheet updates its anchors); writing it inside the measure
                // lambda below would be a state write composition reads in the same frame.
                .onSizeChanged { size ->
                    if (fractionRestTopPx == null) {
                        measuredRestTopPx = screenHeightPx - size.height
                    }
                }
                .layout { measurable, constraints ->
                    if (fractionRestTopPx != null) {
                        val height = sheetHeightPx().roundToInt().coerceIn(0, constraints.maxHeight)
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = height, maxHeight = height),
                        )
                        layout(placeable.width, height) {
                            placeable.placeWithLayer(0, 0) {
                                translationY = offsetBelow(state.top.value, fractionRestTopPx, hiddenTopPx)
                            }
                        }
                    } else {
                        // Content-sized. The cap is the same line the expanded detent uses,
                        // so a tall sheet stops just below the status bar and a short one
                        // (the 300dp alert, an info sheet) rests exactly as tall as it is.
                        val maxHeight = (screenHeightPx - minTopPx).roundToInt()
                            .coerceIn(0, constraints.maxHeight)
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = 0, maxHeight = maxHeight),
                        )
                        // Taken from this measure pass rather than read back out of state,
                        // so the sheet is never drawn one frame behind its own height.
                        val naturalRestTop = screenHeightPx - placeable.height
                        layout(placeable.width, placeable.height) {
                            placeable.placeWithLayer(0, 0) {
                                translationY = offsetBelow(state.top.value, naturalRestTop, hiddenTopPx)
                            }
                        }
                    }
                }
                // Whole-sheet drag. Scrollable children take the gesture first on the main
                // pass and keep scrolling; everything else moves the sheet. See the header.
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    enabled = present && !state.hideRequested && anchors != null,
                    // Grabbing the sheet mid-animation starts from where it visibly is, so
                    // it never jumps under the finger.
                    onDragStarted = { rawTop[0] = state.top.value },
                    onDragStopped = { velocity ->
                        val settled = state.anchors
                        if (settled != null) {
                            val settleTo = chooseSettleTop(
                                rawTopPx = rawTop[0],
                                projectedPx = SakhiMotion.projectedDistance(velocity),
                                isExpanded = expandable && state.isExpanded,
                                hasExpandedDetent = expandable,
                                expandedTopPx = settled.expandedTop,
                                restTopPx = settled.restTop,
                                hiddenTopPx = settled.hiddenTop,
                                thresholds = thresholds,
                            )
                            when {
                                settleTo == settled.hiddenTop -> dismiss(velocity)
                                expandable && settleTo == settled.expandedTop -> {
                                    pendingVelocity[0] = velocity
                                    state.isExpanded = true
                                    settleRequest++
                                }
                                else -> {
                                    pendingVelocity[0] = velocity
                                    state.isExpanded = false
                                    settleRequest++
                                }
                            }
                        }
                    },
                )
                .then(sheetModifier)
                // Sheets that hold a text field (Chat, the country picker with its search
                // field, the emergency thread) need their content above the keyboard. On a
                // content-sized sheet this also grows the measured height by the IME, which
                // moves the rest line up by exactly the keyboard's height -- so the sheet
                // springs up over the keyboard rather than sitting behind it. The
                // background above stays outside it, so the surface still runs to the
                // bottom edge.
                .imePadding(),
            content = content,
        )
    }
}

/**
 * How far down from its rest line the sheet is drawn.
 *
 * Never negative: above the rest line the sheet grows upward instead of moving, which is
 * the split described in the header. Never past the hidden anchor either, which matters
 * for the one frame between a sheet's first layout and the position being initialised --
 * [UNPLACED] is `Float.MAX_VALUE`, and handing that to a graphics layer is not something
 * to rely on being harmless.
 */
private fun offsetBelow(topPx: Float, restTopPx: Float, hiddenTopPx: Float): Float =
    max(0f, min(topPx, hiddenTopPx) - restTopPx)

/** iOS's release thresholds from `HomeCalendarSheet.panGesture.onEnded`, in pixels. */
private class ReleaseThresholds(
    val dismissPastRest: Float,
    val dismissProjected: Float,
    val collapsePastExpanded: Float,
    val collapseProjected: Float,
    val expandAboveRest: Float,
    val expandProjected: Float,
)

/**
 * Where a release lands, ported line for line from iOS `HomeCalendarSheet.panGesture
 * .onEnded` (its multi-select branches aside, which Android has no equivalent of).
 * Anchors are top-edge positions in pixels, so SMALLER is higher up the screen.
 *
 * Each rule is "dragged far enough OR thrown hard enough", where "thrown" is the projected
 * glide distance rather than raw speed, exactly as iOS compares `predictedEndTranslation -
 * translation`. The thresholds are lopsided on purpose, and that asymmetry is a big part of
 * the iOS feel: a light flick up (120pt projected) expands, while closing needs a real
 * throw (500-600pt) or a long drag.
 *
 *  - at rest, pulled 80pt below it or thrown down 600pt        -> dismiss
 *  - expanded, pulled 120pt below expanded or thrown down 500pt -> rest
 *  - at rest, pushed 8pt above it or thrown up 120pt            -> expanded
 *  - otherwise expanded stays expanded, and a sheet at rest goes to whichever side of the
 *    rest line it was released on
 *
 * A sheet with no second detent skips every expand branch, so its only two outcomes are
 * "back to rest" and "dismiss".
 */
private fun chooseSettleTop(
    rawTopPx: Float,
    projectedPx: Float,
    isExpanded: Boolean,
    hasExpandedDetent: Boolean,
    expandedTopPx: Float,
    restTopPx: Float,
    hiddenTopPx: Float,
    thresholds: ReleaseThresholds,
): Float = when {
    !isExpanded && (
        rawTopPx > restTopPx + thresholds.dismissPastRest ||
            projectedPx > thresholds.dismissProjected
        ) -> hiddenTopPx
    isExpanded && (
        rawTopPx > expandedTopPx + thresholds.collapsePastExpanded ||
            projectedPx > thresholds.collapseProjected
        ) -> restTopPx
    hasExpandedDetent && !isExpanded && (
        rawTopPx < restTopPx - thresholds.expandAboveRest ||
            projectedPx < -thresholds.expandProjected
        ) -> expandedTopPx
    isExpanded -> expandedTopPx
    hasExpandedDetent && rawTopPx < restTopPx -> expandedTopPx
    else -> restTopPx
}

/**
 * iOS `HomeCalendarSheet` expanded detent: `actual_top = safeTop + 10`. Also the ceiling
 * every other sheet stops at, so none of them runs under the status bar.
 */
private val SheetTopGap = 10.dp

/**
 * iOS `rubberBand`: how much of the finger's travel the sheet follows once past an end.
 * 14% above the expanded position, 22% below the resting one.
 */
private const val RUBBER_BAND_ABOVE_EXPANDED = 0.14f
private const val RUBBER_BAND_BELOW_REST = 0.22f

/** Before the first layout there are no anchors, so the sheet has nowhere real to be. */
private const val UNPLACED = Float.MAX_VALUE

/** Half a pixel, the same tolerance the settle spring stops at. */
private const val SettleEpsilonPx = 0.5f

/** Longer than any settle this spring produces, and short enough not to read as a hang. */
private const val SettleTimeoutMs = 1_200L
