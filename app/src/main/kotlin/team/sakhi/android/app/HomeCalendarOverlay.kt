package team.sakhi.android.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import team.sakhi.android.designsystem.calendarSheetBackground
import team.sakhi.android.ui.SakhiSheetLayer
import team.sakhi.android.ui.SakhiSheetRest
import team.sakhi.android.ui.rememberSakhiSheetState
import team.sakhi.models.CyclePhase

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
 * behind it, exactly as in the iOS screenshots. That is why this uses
 * [SakhiSheetLayer] directly rather than `SakhiModalSheet`, which presents the same
 * motion in a window: the calendar has to share Home's window, and it has no scrim
 * and no tap-outside target, because Home stays live behind it and scrolling Home is
 * what dismisses it (iOS's `onScrollBegan`).
 *
 * Geometry is taken from the Swift source rather than eyeballed: 24dp top corners,
 * a 36×4 handle with 10dp top / 4dp bottom padding inside a 36dp touch row.
 *
 * ── Where the motion lives now ──────────────────────────────────────────────────
 *
 * Every frame of it — the single pixel `Animatable` that no composable body reads, the
 * split between height and position, the iOS release rules, the rubber band, the
 * `SakhiMotion.sheet()` settle with the release velocity carried in, the whole-sheet
 * `draggable` — is in `core/ui/SakhiSheetPresentation.kt` now, because Karan asked for
 * exactly one answer to "how does a sheet move" across the whole app: "jitna smooth
 * calendar ka appearance and dismiss animation hai, i want isko utility mai banao, and
 * isko har sheet ya jo bhi presentation hai usme use karo." Read that file's header for
 * what each of those properties is for. Nothing about the calendar's own resting
 * positions or behaviour changed in the move.
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
    val sheetState = rememberSakhiSheetState()
    val scope = rememberCoroutineScope()

    // This overlay is drawn outside the NavHost, so without a handler the system back
    // gesture fell straight through to the nav graph, popped Home and quit the app while
    // the calendar was still on screen. iOS's sheet dismisses on its own swipe-down; back
    // is Android's equivalent affordance and has to do the same thing.
    //
    // `hide()` first, then tell Home: the dismissal is the same slide-out the drag gives,
    // not an instant disappearance. `SakhiModalSheet` does the same thing for every other
    // sheet, through the dialog's own back handling.
    BackHandler(enabled = visible) {
        if (sheetState.isExpanded) {
            sheetState.isExpanded = false
        } else {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        }
    }

    SakhiSheetLayer(
        onDismiss = onDismiss,
        state = sheetState,
        present = visible,
        // iOS `compactY = screenH * 0.20`, giving `actual_top = 0.40 * screenH`.
        rest = SakhiSheetRest.ScreenFraction(COMPACT_TOP_FRACTION),
        // The second detent, `safeTop + 10`. The calendar is the only sheet in the app
        // that has one: compact is the month grid, expanded is the year view.
        expandable = true,
        // No scrim and no tap-outside target. Home is not dimmed behind the calendar and
        // stays fully interactive, which is the whole reason this is an in-tree overlay.
        scrimColor = null,
        dismissOnScrimTap = false,
        sheetModifier = Modifier
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
        // Grabber. Purely the visual affordance — the gesture belongs to the whole sheet,
        // which is why this owns no `pointerInput` of its own.
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
            sheetState.isExpanded,
            { expand -> sheetState.isExpanded = expand },
        )
    }
}

/** iOS `compactY = screenH * 0.20`, giving `actual_top = 0.40 * screenH`. */
private const val COMPACT_TOP_FRACTION = 0.40f
private val SHEET_CORNER = 24.dp
// iOS uses a 36pt row (capsule + 10/4 padding), but that left a visibly wide gap
// between the grabber and the month bar on device. Tightened so the calendar sits
// higher in the sheet; the space it frees is spent between the grid and the bottom
// action bar instead.
private val HANDLE_ROW_HEIGHT = 22.dp
private val HANDLE_TOP_PADDING = 10.dp
private val HANDLE_WIDTH = 36.dp
private val HANDLE_HEIGHT = 4.dp
