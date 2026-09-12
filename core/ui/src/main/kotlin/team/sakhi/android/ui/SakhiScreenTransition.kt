package team.sakhi.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex

/**
 * How one full screen/view should give way to the next.
 *
 * [Forward] and [Backward] are a horizontal push/pop, mirroring the
 * UIKit/SwiftUI `NavigationStack` feel iOS gives users for the same journeys:
 * Forward = the incoming view slides in from the right (drilling deeper),
 * Backward = the current view slides back off to the right to reveal the one
 * beneath it (going up the stack).
 *
 * [None] is a plain cross-fade with no direction, for structural swaps where a
 * left/right slide would be meaningless (e.g. the app root moving Splash ->
 * Onboarding -> Home, which isn't a stack the user pushed onto).
 */
enum class SakhiNavDirection { Forward, Backward, None }

/**
 * One timing curve for every screen transition in the app, so nothing feels faster
 * or slower than anything else. Tuned toward iOS's native push/pop feel: calm
 * enough to read as a real view transition, short enough to stay out of the way.
 */
private const val SCREEN_TRANSITION_DURATION_MS = 380
private const val ROOT_FADE_DURATION_MS = 240
private const val OUTGOING_PARALLAX_DIVISOR = 3
private const val INCOMING_INITIAL_ALPHA = 0.98f

/**
 * The outgoing screen used to fade to 0.98, i.e. stay ~fully opaque, while only
 * parallax-shifting a third of the screen width. For a full-bleed screen (not a card
 * revealing a dimmed screen underneath), that left both screens visibly present and
 * overlapping for most of the animation -- reported live as "double view stacked on
 * top of each other" during onboarding's Continue transition. Fading fully to 0
 * combined with [INCOMING_Z_INDEX] (below) fixes it: once alpha reaches 0 the old
 * screen is genuinely gone regardless of how far it has physically slid.
 */
private const val OUTGOING_TARGET_ALPHA = 0f

/**
 * The outgoing alpha for a true NavigationStack-style PUSH, where the parent screen is
 * supposed to remain underneath the incoming one rather than being replaced by it.
 *
 * iOS's push does not fade the parent out at all -- it slides it back by roughly a
 * third and leaves it opaque behind the (opaque) incoming view. That is what makes a
 * push read as "over" instead of "instead of". [OUTGOING_TARGET_ALPHA]'s fade-to-zero
 * exists for full-bleed peer screens like onboarding steps, which have no such parent
 * relationship, and must not be applied here.
 */
private const val OUTGOING_TARGET_ALPHA_PUSH = 1f

/**
 * `AnimatedContent` does not itself guarantee the incoming layer draws above the
 * outgoing one -- without an explicit z-index the two can composite in either order,
 * which was the other half of the "double view" bug: the old screen sometimes painted
 * over the new one mid-transition instead of being covered by it.
 */
private const val INCOMING_Z_INDEX = 1f
private const val OUTGOING_Z_INDEX = 0f
private const val SHEET_CONTENT_TRANSITION_DURATION_MS = 300
private val screenTransitionEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/**
 * Deliberately its own duration, not [SCREEN_TRANSITION_DURATION_MS]: a `PagerState`
 * scroll covers a full screen width of continuous motion, which reads faster than an
 * `AnimatedContent` crossfade at the same duration ("transition speed bhaut fast hai").
 * Tuned slower than the 380ms screen-transition default for that reason -- changing
 * this does not affect any other transition in the app.
 */
private const val PAGER_TRANSITION_DURATION_MS = 550

/**
 * A tween for call sites that drive their own `PagerState` scroll animation instead of
 * `AnimatedContent` -- e.g. `PagerState.animateScrollToPage`'s `animationSpec` parameter,
 * which defaults to a fast spring tuned for a quick fling-and-snap ("bhaut fast hai...
 * pages mai jo smooth transition hai vo same karo isme slider").
 *
 * Deliberately does NOT reuse [screenTransitionEasing]: that curve is tuned for fading/
 * offsetting a whole screen as a property animation, and produced visible jitter when
 * used to drive continuous scroll position directly ("abhi bhi jitterness hai") -- a
 * curve that reads fine animating opacity/offset can feel uneven driving raw per-pixel
 * scroll velocity across a full screen width. [FastOutSlowInEasing] is Compose's own
 * standard curve for scroll/fling motion, which is what this needs instead.
 */
val sakhiScreenTransitionSpec: androidx.compose.animation.core.AnimationSpec<Float> =
    tween(PAGER_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing)

/**
 * The single source of truth for transitions between full screens/views across the
 * whole app.
 *
 * Sakhi does not use Navigation-Compose `composable()` destinations for most
 * navigation (iOS parity: Home-owned surfaces are sheets/overlays, Profile is a
 * flat sub-screen stack, onboarding is a linear step flow). Each of those places
 * swaps one view for another by flipping a `when`/state value, which recomposes
 * instantly with no animation unless it is wrapped. Rather than hand-roll
 * `AnimatedContent` + slide specs at every such site (they drift apart and some get
 * forgotten, which is exactly how the app ended up cutting instantly), every
 * view-swap funnels through this one composable so the whole app slides identically.
 *
 * [directionFor] decides, for a given `initial -> target` swap, whether it reads as
 * a forward push, a backward pop, or a non-directional cross-fade. Sites with a
 * natural order (a root/sub-screen stack, a content-page push) compare the two
 * states; sites that already carry an explicit intent flag (e.g. KMM's
 * `navWasForward`) can ignore the arguments and return from that flag instead.
 */
@Composable
fun <T> SakhiScreenTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "sakhi_screen_transition",
    directionFor: (initial: T, target: T) -> SakhiNavDirection = { _, _ -> SakhiNavDirection.Forward },
    /**
     * True where the states form a parent/child stack (a sheet's root and its
     * sub-screens) rather than full-bleed peers. The outgoing screen then stays opaque
     * behind the incoming one, so the child reads as pushed OVER its parent, matching
     * an iOS `NavigationStack`. Peer flows such as onboarding leave this false and keep
     * the fade-to-zero that stops the two steps overlapping.
     */
    parentStaysBehind: Boolean = false,
    content: @Composable AnimatedContentScope.(T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            sakhiScreenSlide(
                direction = directionFor(initialState, targetState),
                parentStaysBehind = parentStaysBehind,
            )
        },
        label = label,
    ) { state ->
        // The incoming layer always draws on top of the outgoing one -- see
        // [INCOMING_Z_INDEX]'s doc for why this is not the AnimatedContent default.
        Box(modifier = Modifier.zIndex(if (state == targetState) INCOMING_Z_INDEX else OUTGOING_Z_INDEX)) {
            content(state)
        }
    }
}

/**
 * For swapping content inside an already-open sheet. Sheets already enter/leave
 * vertically, so their internal peer swaps should not add another direction or a
 * size animation. This mirrors the Raindrop reference pattern for lightweight
 * modal content: a 300ms ease-in-out opacity fade while the host owns the motion.
 */
@Composable
fun <T> SakhiSheetContentTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "sakhi_sheet_content_transition",
    content: @Composable (T) -> Unit,
) {
    Crossfade(
        targetState = targetState,
        modifier = modifier,
        animationSpec = tween(SHEET_CONTENT_TRANSITION_DURATION_MS, easing = screenTransitionEasing),
        label = label,
        content = content,
    )
}

/**
 * The raw push/pop/fade [ContentTransform] used by [SakhiScreenTransition], exposed
 * for the rare call site that has to drive its own `AnimatedContent` (e.g. one that
 * keys on a second piece of state as well) but must still match the app-wide feel.
 * Prefer [SakhiScreenTransition] wherever a single target state is enough.
 */
fun sakhiScreenSlide(
    direction: SakhiNavDirection,
    parentStaysBehind: Boolean = false,
): ContentTransform {
    val alphaSpec = tween<Float>(SCREEN_TRANSITION_DURATION_MS, easing = screenTransitionEasing)
    val offsetSpec = tween<IntOffset>(SCREEN_TRANSITION_DURATION_MS, easing = screenTransitionEasing)
    val outgoingAlpha = if (parentStaysBehind) OUTGOING_TARGET_ALPHA_PUSH else OUTGOING_TARGET_ALPHA
    val transform = when (direction) {
        SakhiNavDirection.None ->
            fadeIn(tween(ROOT_FADE_DURATION_MS, easing = screenTransitionEasing))
                .togetherWith(fadeOut(tween(ROOT_FADE_DURATION_MS, easing = screenTransitionEasing)))
        SakhiNavDirection.Forward ->
            (
                slideInHorizontally(offsetSpec) { fullWidth -> fullWidth } +
                    fadeIn(alphaSpec, initialAlpha = INCOMING_INITIAL_ALPHA)
                ).togetherWith(
                slideOutHorizontally(offsetSpec) { fullWidth -> -fullWidth / OUTGOING_PARALLAX_DIVISOR } +
                    fadeOut(alphaSpec, targetAlpha = outgoingAlpha),
            )
        SakhiNavDirection.Backward ->
            (
                slideInHorizontally(offsetSpec) { fullWidth -> -fullWidth / OUTGOING_PARALLAX_DIVISOR } +
                    fadeIn(alphaSpec, initialAlpha = INCOMING_INITIAL_ALPHA)
                ).togetherWith(
                slideOutHorizontally(offsetSpec) { fullWidth -> fullWidth } +
                    fadeOut(alphaSpec, targetAlpha = outgoingAlpha),
            )
    }
    // `ContentTransform`'s constructor defaults `sizeTransform` to `SizeTransform()`, so
    // every transition above was silently also animating the CONTAINER's size from the
    // outgoing screen's measured size to the incoming one's. That costs a full measure of
    // both screens on every frame of the animation, and it buys nothing here: these are
    // full-bleed screens, so the two sizes are identical and the "animation" is a
    // 380ms-long measure of a value that never changes. Turning it off leaves the slide
    // and the fade, which are the only things that were ever meant to move.
    //
    // Rebuilt through the public constructor rather than with `using null`: in this
    // Compose version `using` is a member of `AnimatedContentTransitionScope`, so it only
    // exists inside a `transitionSpec` lambda, and this function is called from outside
    // one. The `sizeTransform` setter is internal to Compose.
    return ContentTransform(
        targetContentEnter = transform.targetContentEnter,
        initialContentExit = transform.initialContentExit,
        sizeTransform = null,
    )
}
