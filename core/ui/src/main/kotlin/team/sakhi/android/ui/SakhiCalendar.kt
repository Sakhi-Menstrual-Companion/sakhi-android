package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import team.sakhi.android.designsystem.LocalSakhiCalendarAccent
import team.sakhi.android.designsystem.LocalSakhiDarkTheme
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.SakhiColors
import team.sakhi.design.SakhiUIColors
import team.sakhi.models.CyclePhase

@Immutable
data class SakhiCalendarDay(
    val date: LocalDate,
    val isInVisibleMonth: Boolean,
    val isSelected: Boolean = false,
    val isToday: Boolean = false,
    val isFuture: Boolean = false,
    val markerType: SakhiCalendarMarkerType? = null,
    /**
     * The day has something logged beyond the period itself -- symptoms, moods, notes,
     * weight, BBT and so on. Drawn as a small dot under the date so a day that carries
     * detail is distinguishable from a bare period day at a glance.
     */
    val hasLogDetail: Boolean = false,
)

enum class SakhiCalendarMarkerType {
    PERIOD,
    PREDICTED_PERIOD,
    FERTILE,
    OVULATION,
}

@Composable
fun SakhiWeekdayHeaderRow(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SakhiSpacing.space6),
    ) {
        labels.forEach { day ->
            Text(
                text = day,
                style = MaterialTheme.typography.labelSmall,
                // iOS `SakhiCalendarView.weekdayRow` uses `DS.Colors.tertiaryLabel` for the
                // S/M/T letters -- tertiary, not secondary.
                color = sakhiTertiaryLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The selected-day ring, guaranteed circular at whatever height the cell actually gets.
 *
 * `CircleShape` only draws a circle on a SQUARE box, and neither a fixed size nor a measured
 * one worked here:
 *
 *  - A fixed `.size(42.dp)` renders a STADIUM whenever the cell is shorter than 42dp, because
 *    the width survives and the height is coerced down. In the compact calendar sheet the grid
 *    is laid out with `weight(1f)`, so six rows share the leftover space and each cell ends up
 *    around 21dp tall — less than half the 48dp the constant implies.
 *  - `BoxWithConstraints` + `matchParentSize()` measured the wrong box entirely, because
 *    `BoxWithConstraints` subcomposes from its incoming constraints while `matchParentSize`
 *    resolves in a later pass.
 *
 * `fillMaxHeight` + `aspectRatio(1f)` needs neither. The height is taken from the cell, the
 * width is then forced to match it, and the result is square by construction — so it is a
 * circle at 21dp in the compact sheet and at [diameter] wherever there is room, without either
 * layout knowing about the other.
 *
 * NOTE: a tight-looking ring is this working correctly on a cramped cell, not a bug in the
 * ring. If the calendar looks cramped, the row height is the thing to change.
 */
@Composable
private fun SelectionRing(
    diameter: Dp,
    strokeWidth: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            // Cap first, then fill: never larger than the design size, never taller than the
            // cell, and always exactly as wide as it is tall.
            .heightIn(max = diameter)
            .fillMaxHeight()
            .aspectRatio(1f)
            .border(strokeWidth, color, CircleShape),
    )
}

@Composable
fun SakhiCalendarMonthGrid(
    days: List<SakhiCalendarDay>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekRows = remember(days) { SakhiCalendarWeekRows(days.chunked(7)) }
    Column(
        // Rows SHARE the height rather than each claiming `calendarCellHeight` outright.
        //
        // The compact sheet's grid area is laid out with `weight(1f)`, and a six-week month
        // needs more than it gets. With fixed-height rows the first five took their full 48dp
        // and the sixth was left with whatever remained — measured at 21dp against 48dp for
        // the others, which squashed that row's day circles into ovals. It only showed on
        // months whose last row is occupied, which is why it looked intermittent.
        //
        // Weighting makes every row the same height whatever the sheet gives, so no row is
        // ever starved. Safe here because this grid's only caller sits in a height-bounded
        // sheet; `weight` inside an unbounded parent would not have a height to divide.
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1 + SakhiSpacing.space1 / 2),
    ) {
        weekRows.rows.forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // Never taller than the design row height when there IS spare room —
                    // otherwise a five-week month would stretch its rows to fill the sheet.
                    .heightIn(max = calendarCellHeight),
            ) {
                week.forEach { day ->
                    SakhiCalendarDayCell(
                        day = day,
                        onDayClick = onDayClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun SakhiMiniMonthGrid(
    days: List<SakhiCalendarDay>,
    modifier: Modifier = Modifier,
    isMultiSelectMode: Boolean = false,
    selectionSet: Set<LocalDate> = emptySet(),
    onDayToggle: ((LocalDate) -> Unit)? = null,
) {
    val weekRows = remember(days) { SakhiCalendarWeekRows(days.chunked(7)) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
    ) {
        weekRows.rows.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    SakhiMiniMonthDayCell(
                        day = day,
                        isMultiSelectMode = isMultiSelectMode,
                        isInSelection = selectionSet.contains(day.date),
                        onToggle = onDayToggle,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SakhiMiniMonthDayCell(
    day: SakhiCalendarDay,
    modifier: Modifier = Modifier,
    isMultiSelectMode: Boolean = false,
    isInSelection: Boolean = false,
    onToggle: ((LocalDate) -> Unit)? = null,
) {
    if (!day.isInVisibleMonth) {
        Box(modifier = modifier.height(yearGridCellHeight))
        return
    }

    val periodColor = SakhiUIColors.BRAND_PERIOD_RED.toComposeColor()
    // The phase colour the sheet is wearing, not the brand pink. See
    // [LocalSakhiCalendarAccent]; the fallback is what this drew before anyone provided one.
    val accentColor = LocalSakhiCalendarAccent.current ?: MaterialTheme.colorScheme.primary
    val isDarkTheme = LocalSakhiDarkTheme.current
    val isPeriod = day.markerType == SakhiCalendarMarkerType.PERIOD
    val ovulationRingColor = remember(isDarkTheme) {
        SakhiColors.resolved(isDarkTheme).forPhase(CyclePhase.OVULATION).ring.toComposeColor()
    }

    // Matches iOS `YearDayCell`'s multi-select fill rules exactly: a selected day
    // that's already a real period day is "marked for removal" (no fill, normal
    // label -- it'll look unselected once saved); a selected day that ISN'T yet a
    // period day is "marked to add" (period-colored fill); an already-period day
    // outside the selection keeps its normal fill; everything else in multi-select
    // mode renders with no fill (predicted/fertile/ovulation markers are hidden
    // while editing, same as iOS's `!isMultiSelectMode &&` guards on those flags).
    val fillColor = when {
        isMultiSelectMode && isInSelection && isPeriod -> Color.Transparent
        isMultiSelectMode && (isInSelection || isPeriod) -> periodColor
        isMultiSelectMode -> Color.Transparent
        // Two fills, and only two (Karan, 2026-09-17). Solid means she logged that day
        // herself. A prediction is the light fill and stays the light fill whether it is
        // behind her or ahead of her, because a prediction does not become a fact by
        // passing. Matches iOS's `SakhiCalendarView` exactly.
        day.markerType == SakhiCalendarMarkerType.PERIOD -> periodColor
        day.markerType == SakhiCalendarMarkerType.PREDICTED_PERIOD ->
            periodColor.copy(alpha = PredictedPeriodFillAlpha)
        else -> Color.Transparent
    }
    val labelColor = when {
        isMultiSelectMode && isInSelection && isPeriod -> MaterialTheme.colorScheme.onSurface
        isMultiSelectMode && (isInSelection || isPeriod) -> Color.White
        day.markerType == SakhiCalendarMarkerType.PERIOD -> Color.White
        isDarkTheme && day.markerType == SakhiCalendarMarkerType.PREDICTED_PERIOD -> Color.White
        // iOS `YearDayCell` labels these with `PhaseColorManager.ovulation` /
        // `.fertileWindow`, and both resolve to `PhaseColors(.ovulation).ring` — the
        // ovulation purple, not the app accent. Android used `primary` (pink) for both,
        // which merged fertile/ovulation days into the same hue as today and selection
        // and lost the distinction the year grid exists to show. Using the resolved
        // pair so it still adapts to dark.
        // The year grid had no isFuture branch at all, so a future date rendered at full
        // strength onSurface, identical to a day that had already happened, and nothing
        // on screen said it could not be logged. These mirror the compact cell.
        day.isFuture && day.markerType == SakhiCalendarMarkerType.OVULATION ->
            ovulationRingColor.copy(alpha = disabledSemanticOpacity)
        day.isFuture && day.markerType == SakhiCalendarMarkerType.FERTILE ->
            ovulationRingColor.copy(alpha = disabledSemanticOpacity)
        day.markerType == SakhiCalendarMarkerType.PREDICTED_PERIOD -> periodColor
        day.isFuture -> sakhiSecondaryLabel()
        day.markerType == SakhiCalendarMarkerType.OVULATION -> ovulationRingColor
        day.markerType == SakhiCalendarMarkerType.FERTILE -> ovulationRingColor
        day.isToday -> accentColor
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .height(yearGridCellHeight)
            .let { base ->
                // `enabled = !day.isFuture` matters more here than the colours do. Without
                // it, multi-select in the year grid let a future date be tapped and saved
                // as a period day, which is a log for something that has not happened. The
                // compact grid has always guarded this; the year grid never did.
                if (isMultiSelectMode && onToggle != null) {
                    base.clickable(enabled = !day.isFuture) { onToggle(day.date) }
                } else {
                    base
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // iOS `YearDayCell` draws the selected-date ring here -- `Circle().stroke(accent,
        // lineWidth: 2.5).frame(width: 40, height: 40)`. Android had no selected-date
        // ring in the year view at all, so the day you had picked was indistinguishable
        // from any other once the year grid was open.
        if (day.isSelected) {
            SelectionRing(
                diameter = yearGridRingSize,
                strokeWidth = yearGridRingStroke,
                color = accentColor,
            )
        }
        if (isMultiSelectMode) {
            Box(
                modifier = Modifier
                    .size(yearGridMultiSelectHintSize)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(yearGridDotSize)
                .background(fillColor, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = yearGridFontSize,
                    fontWeight = if (day.isToday || isPeriod || (isMultiSelectMode && isInSelection)) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                ),
                color = labelColor,
            )
        }

        // The same "she logged something" dot the month grid draws, in the same brand pink
        // and in the same place relative to the circle: under it, clear of the fill and of
        // the selection ring. The year grid had no dot at all, so a day she had written
        // about looked empty until the month was opened.
        if (day.hasLogDetail) {
            Box(
                modifier = Modifier
                    .offset(y = yearGridLogDotOffsetY)
                    .size(CalendarLogDotSize)
                    // Brand pink, like the month grid's, not the accent: iOS draws this one
                    // as `DS.Colors.pink` in both grids whatever phase she is in.
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
            )
        }
    }
}

@Composable
private fun SakhiCalendarDayCell(
    day: SakhiCalendarDay,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!day.isInVisibleMonth) {
        Box(modifier = modifier.height(calendarCellHeight))
        return
    }

    val periodColor = SakhiUIColors.BRAND_PERIOD_RED.toComposeColor()
    // The phase colour the sheet is wearing, not the brand pink. See
    // [LocalSakhiCalendarAccent]; the fallback is what this drew before anyone provided one.
    val accentColor = LocalSakhiCalendarAccent.current ?: MaterialTheme.colorScheme.primary
    val isDarkTheme = LocalSakhiDarkTheme.current
    val ovulationRingColor = remember(isDarkTheme) {
        SakhiColors.resolved(isDarkTheme).forPhase(CyclePhase.OVULATION).ring.toComposeColor()
    }
    val todayLabel = stringResource(R.string.calendar_a11y_today)
    val selectedLabel = stringResource(R.string.calendar_a11y_selected)
    val periodDayLabel = stringResource(R.string.calendar_a11y_period_day)
    val predictedPeriodLabel = stringResource(R.string.calendar_a11y_predicted_period)
    val ovulationDayLabel = stringResource(R.string.calendar_a11y_ovulation_day)
    val fertileWindowLabel = stringResource(R.string.calendar_a11y_fertile_window)

    val fillColor = when (day.markerType) {
        SakhiCalendarMarkerType.PERIOD -> periodColor
        SakhiCalendarMarkerType.PREDICTED_PERIOD -> {
            periodColor.copy(alpha = PredictedPeriodFillAlpha)
        }
        else -> Color.Transparent
    }

    val labelColor = when {
        day.markerType == SakhiCalendarMarkerType.PERIOD -> Color.White
        isDarkTheme && day.markerType == SakhiCalendarMarkerType.PREDICTED_PERIOD -> Color.White
        day.isSelected -> accentColor
        // iOS's compact `dayCell` calls these out as "Fixed semantic colors — independent
        // of phase accent" and labels them with `PhaseColorManager.ovulation` /
        // `.fertileWindow` (both the ovulation ring), exactly as `YearDayCell` does.
        // Android used the phase accent, so fertile/ovulation shared a hue with today and
        // selection on the screen users see most.
        day.isFuture && day.markerType == SakhiCalendarMarkerType.OVULATION -> ovulationRingColor.copy(alpha = disabledSemanticOpacity)
        day.isFuture && day.markerType == SakhiCalendarMarkerType.FERTILE -> ovulationRingColor.copy(alpha = disabledSemanticOpacity)
        // A predicted day reads the same on both sides of today, for the same reason its
        // fill does.
        day.markerType == SakhiCalendarMarkerType.PREDICTED_PERIOD -> periodColor
        // A future day with no marker. iOS ends its `isFuture` branch with
        // `DS.Colors.secondaryLabel`; Android had no such branch, so a plain future date
        // fell through to full-strength `onSurface` and read exactly as solid as a day
        // that has already happened. Every *marked* future day was already dimmed above,
        // which made the undimmed plain ones look like the odd ones out.
        day.isFuture -> sakhiSecondaryLabel()
        day.markerType == SakhiCalendarMarkerType.OVULATION -> ovulationRingColor
        day.markerType == SakhiCalendarMarkerType.FERTILE -> ovulationRingColor
        day.isToday -> accentColor
        else -> MaterialTheme.colorScheme.onSurface
    }

    val description = buildList {
        add(day.date.dayOfMonth.toString())
        if (day.isToday) add(todayLabel)
        if (day.isSelected) add(selectedLabel)
        when (day.markerType) {
            // A logged day is a logged day. It is never in the future, and calling it a
            // prediction in the spoken description was the screen reader's version of the
            // lighter fill this cell no longer draws.
            SakhiCalendarMarkerType.PERIOD -> add(periodDayLabel)
            SakhiCalendarMarkerType.PREDICTED_PERIOD -> add(predictedPeriodLabel)
            SakhiCalendarMarkerType.OVULATION -> add(ovulationDayLabel)
            SakhiCalendarMarkerType.FERTILE -> add(fertileWindowLabel)
            null -> Unit
        }
    }.joinToString(stringResource(R.string.calendar_a11y_separator))

    Box(
        modifier = modifier
            // fillMaxHeight, not height(): the row above now decides the height and shares it
            // evenly. Re-imposing a fixed 48dp here would reintroduce the starved last row.
            .fillMaxHeight()
            .clickable(enabled = !day.isFuture) { onDayClick(day.date) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (day.isSelected) {
            SelectionRing(
                diameter = calendarRingSize,
                strokeWidth = calendarRingStroke,
                color = accentColor,
            )
        }
        Box(
            modifier = Modifier
                .size(calendarDotSize)
                .background(fillColor, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                // bodySmall is 12sp, which read as small for the primary content of the
                // month grid. 15sp matches the size iOS uses across the calendar chrome
                // and sits comfortably inside the day circle.
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = CalendarDayFontSize,
                    fontWeight = if (day.isSelected || day.isToday || day.markerType == SakhiCalendarMarkerType.PERIOD) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                ),
                color = labelColor,
            )
        }

        // Small dot under the date when the day carries logged detail. Sits below the
        // day circle rather than inside it so it never competes with the period fill or
        // the today ring.
        if (day.hasLogDetail) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // `offset` rather than padding: it moves where the dot DRAWS without
                    // changing what the cell measures, so the extra clearance below the
                    // today ring costs nothing in row height. Growing the cell instead
                    // spread the grid and pushed the month's last week off the sheet.
                    .offset(y = CalendarLogDotOffsetY)
                    .padding(bottom = CalendarLogDotBottomInset)
                    .size(CalendarLogDotSize)
                    .background(
                        // Always the brand pink, including on a day she also logged a period
                        // (Karan, 2026-09-17: "dot gayaab ho jaa raha hai"). White was right
                        // when this dot sat inside the filled circle; it sits under the
                        // circle now, on the page, where white is invisible.
                        color = MaterialTheme.colorScheme.primary,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
            )
        }
    }
}

@Immutable
private data class SakhiCalendarWeekRows(
    val rows: List<List<SakhiCalendarDay>>,
)

// Back to the original height. Growing this to make room under the ring for the log dot
// spread the rows apart and pushed the last week of the month off the bottom of the
// sheet -- six rows multiply every dp added here. The dot's clearance comes from a
// slightly smaller today ring instead, which costs no layout.
private val calendarCellHeight = SakhiSpacing.space10 + SakhiSpacing.space1 * 2
private val CalendarDayFontSize = 15.sp
/** The one predicted-period fill, past or future. Mirrors iOS's `DS.CalendarStyle`. */
private const val PredictedPeriodFillAlpha = 0.16f

private val CalendarLogDotSize = 4.dp
private val CalendarLogDotBottomInset = 1.dp
private val CalendarLogDotOffsetY = 8.dp
/** iOS `DS.CalendarStyle.logDotGap`. */
private val yearGridLogDotGap = 3.dp
private val calendarDotSize = SakhiSpacing.space8 + SakhiSpacing.space1 / 2
// iOS `SakhiCalendarView` day cell: the ring is `dotSize + 8`, where
// `dotSize = min(cellHeight - 10, 36)`. The Calendar tab uses the view's default
// `cellHeight: 44` (`HomeCalendarSheet` overrides only `navButtonSize`), so dotSize is
// 34 and the ring is 42.
//
// This was cut to 38 earlier on the belief that 42 crowded the log-detail dot. It does
// not: that dot is Android-only (no iOS counterpart) and is drawn at the cell's bottom
// edge with an 8dp *offset*, i.e. outside the cell bounds in the row gap, so it never
// competed with the ring for space inside the cell.
private val calendarRingSize = 42.dp
private val calendarRingStroke = SakhiSpacing.space1 / 2 + SakhiSpacing.space1 / 8
// iOS `HomeCalendarYearGrid.YearMonthGrid` / `YearDayCell`, read from source:
//   `.frame(maxWidth: .infinity).frame(height: 36)`  -- cell
//   `Circle().fill(fill).frame(width: 32, height: 32)` -- day dot
//   `Circle().stroke(accent, lineWidth: 2.5).frame(width: 40, height: 40)` -- selection
//   `Circle().stroke(systemGray4, lineWidth: 1).frame(width: 34, height: 34)` -- multi-select hint
//   `.font(.lato(14, ...))` -- label
// Android had a 24dp cell with a 20dp dot and an 11sp label, which rendered the year
// view far denser and smaller than iOS's.
// The row is taller than the circle on purpose. The "she logged something" dot goes under
// the circle, and at the old 36 there was no room for it: it landed on the circle's own
// edge (Karan, 2026-09-17, "year view mai dot circle ke andar chale jaa raha hai"). iOS
// `YearDayCell` now reads `circleSize: 32`, `cellHeight: 44`, and this is that.
private val yearGridCellHeight = 44.dp
private val yearGridDotSize = 32.dp
// iOS's own `Circle().stroke(accent, lineWidth: 2.5).frame(width: 40, height: 40)`. It had
// to be cut to 34 while the cell was 36, because a circle bigger than its own cell renders
// as a stadium. At 44 the real number fits.
private val yearGridRingSize = 40.dp
private val yearGridRingStroke = 2.5.dp
private val yearGridMultiSelectHintSize = 34.dp
// Clear of the selection ring, not just of the day circle. Measured from the ring because
// the ring is the bigger of the two: at half the circle the dot landed on the ring's own
// stroke and vanished into it on the selected day. iOS `YearDayCell.logDotOffset` is this
// same sum.
private val yearGridLogDotOffsetY =
    yearGridRingSize / 2 + yearGridRingStroke / 2 + yearGridLogDotGap + CalendarLogDotSize / 2
private val yearGridFontSize = 14.sp
private const val disabledSemanticOpacity = 0.68f
