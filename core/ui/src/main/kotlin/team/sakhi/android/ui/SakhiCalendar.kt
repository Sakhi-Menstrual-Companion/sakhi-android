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
import team.sakhi.android.designsystem.LocalSakhiDarkTheme
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.DesignTokens
import team.sakhi.design.SakhiColors
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

@Composable
fun SakhiCalendarMonthGrid(
    days: List<SakhiCalendarDay>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekRows = remember(days) { SakhiCalendarWeekRows(days.chunked(7)) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1 + SakhiSpacing.space1 / 2),
    ) {
        weekRows.rows.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
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

    val periodColor = DesignTokens.PERIOD_RED.toComposeColor()
    val accentColor = MaterialTheme.colorScheme.primary
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
        // Future days are dimmed here exactly as the compact grid dims them. Without
        // these two branches a predicted period in a coming month drew at full strength
        // and read as something that had already happened.
        day.markerType == SakhiCalendarMarkerType.PERIOD ->
            if (day.isFuture) periodColor.copy(alpha = 0.12f) else periodColor
        day.markerType == SakhiCalendarMarkerType.PREDICTED_PERIOD ->
            periodColor.copy(alpha = if (day.isFuture) 0.12f else 0.16f)
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
        day.isFuture && day.markerType == SakhiCalendarMarkerType.PREDICTED_PERIOD ->
            periodColor.copy(alpha = disabledSemanticOpacity)
        day.isFuture -> sakhiSecondaryLabel()
        day.markerType == SakhiCalendarMarkerType.OVULATION -> ovulationRingColor
        day.markerType == SakhiCalendarMarkerType.FERTILE -> ovulationRingColor
        day.markerType == SakhiCalendarMarkerType.PREDICTED_PERIOD -> periodColor
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
            Box(
                modifier = Modifier
                    .size(yearGridRingSize)
                    .border(
                        yearGridRingStroke,
                        accentColor,
                        androidx.compose.foundation.shape.CircleShape,
                    ),
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

    val periodColor = DesignTokens.PERIOD_RED.toComposeColor()
    val accentColor = MaterialTheme.colorScheme.primary
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
        SakhiCalendarMarkerType.PERIOD -> {
            if (day.isFuture) periodColor.copy(alpha = 0.12f) else periodColor
        }
        SakhiCalendarMarkerType.PREDICTED_PERIOD -> {
            periodColor.copy(alpha = if (day.isFuture) 0.12f else 0.16f)
        }
        else -> Color.Transparent
    }

    val labelColor = when {
        day.markerType == SakhiCalendarMarkerType.PERIOD -> {
            if (day.isFuture) periodColor.copy(alpha = disabledSemanticOpacity) else Color.White
        }
        isDarkTheme && day.markerType == SakhiCalendarMarkerType.PREDICTED_PERIOD -> {
            if (day.isFuture) Color.White.copy(alpha = disabledSemanticOpacity) else Color.White
        }
        day.isSelected -> accentColor
        // iOS's compact `dayCell` calls these out as "Fixed semantic colors — independent
        // of phase accent" and labels them with `PhaseColorManager.ovulation` /
        // `.fertileWindow` (both the ovulation ring), exactly as `YearDayCell` does.
        // Android used the phase accent, so fertile/ovulation shared a hue with today and
        // selection on the screen users see most.
        day.isFuture && day.markerType == SakhiCalendarMarkerType.OVULATION -> ovulationRingColor.copy(alpha = disabledSemanticOpacity)
        day.isFuture && day.markerType == SakhiCalendarMarkerType.FERTILE -> ovulationRingColor.copy(alpha = disabledSemanticOpacity)
        day.isFuture && day.markerType == SakhiCalendarMarkerType.PREDICTED_PERIOD -> periodColor.copy(alpha = disabledSemanticOpacity)
        // A future day with no marker. iOS ends its `isFuture` branch with
        // `DS.Colors.secondaryLabel`; Android had no such branch, so a plain future date
        // fell through to full-strength `onSurface` and read exactly as solid as a day
        // that has already happened. Every *marked* future day was already dimmed above,
        // which made the undimmed plain ones look like the odd ones out.
        day.isFuture -> sakhiSecondaryLabel()
        day.markerType == SakhiCalendarMarkerType.OVULATION -> ovulationRingColor
        day.markerType == SakhiCalendarMarkerType.FERTILE -> ovulationRingColor
        day.markerType == SakhiCalendarMarkerType.PREDICTED_PERIOD -> periodColor
        day.isToday -> accentColor
        else -> MaterialTheme.colorScheme.onSurface
    }

    val description = buildList {
        add(day.date.dayOfMonth.toString())
        if (day.isToday) add(todayLabel)
        if (day.isSelected) add(selectedLabel)
        when (day.markerType) {
            SakhiCalendarMarkerType.PERIOD -> add(if (day.isFuture) predictedPeriodLabel else periodDayLabel)
            SakhiCalendarMarkerType.PREDICTED_PERIOD -> add(predictedPeriodLabel)
            SakhiCalendarMarkerType.OVULATION -> add(ovulationDayLabel)
            SakhiCalendarMarkerType.FERTILE -> add(fertileWindowLabel)
            null -> Unit
        }
    }.joinToString(stringResource(R.string.calendar_a11y_separator))

    Box(
        modifier = modifier
            .height(calendarCellHeight)
            .clickable(enabled = !day.isFuture) { onDayClick(day.date) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (day.isSelected) {
            Box(
                modifier = Modifier
                    .size(calendarRingSize)
                    .background(Color.Transparent, androidx.compose.foundation.shape.CircleShape)
                    .border(
                        width = calendarRingStroke,
                        color = accentColor,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
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
                        color = if (day.markerType == SakhiCalendarMarkerType.PERIOD) {
                            // On a filled period cell the brand pink would disappear.
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
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
private val CalendarLogDotSize = 4.dp
private val CalendarLogDotBottomInset = 1.dp
private val CalendarLogDotOffsetY = 8.dp
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
private val yearGridCellHeight = 36.dp
private val yearGridDotSize = 32.dp
private val yearGridRingSize = 40.dp
private val yearGridRingStroke = 2.5.dp
private val yearGridMultiSelectHintSize = 34.dp
private val yearGridFontSize = 14.sp
private const val disabledSemanticOpacity = 0.68f
