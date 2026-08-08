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
        day.markerType == SakhiCalendarMarkerType.PERIOD -> periodColor
        day.markerType == SakhiCalendarMarkerType.PREDICTED_PERIOD -> periodColor.copy(alpha = 0.16f)
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
                if (isMultiSelectMode && onToggle != null) {
                    base.clickable { onToggle(day.date) }
                } else {
                    base
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isMultiSelectMode) {
            Box(
                modifier = Modifier
                    .size(yearGridDotSize + SakhiSpacing.space1 / 2)
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
private val calendarDotSize = SakhiSpacing.space8 + SakhiSpacing.space1 / 2
// 42dp left only ~3dp of cell below it, which is not enough for the log dot to sit
// clear. 38dp frees that band without touching the row height.
private val calendarRingSize = SakhiSpacing.space8 + SakhiSpacing.space1 + SakhiSpacing.space1 / 2
private val calendarRingStroke = SakhiSpacing.space1 / 2 + SakhiSpacing.space1 / 8
private val yearGridCellHeight = SakhiSpacing.space6
private val yearGridDotSize = SakhiSpacing.space5
private const val disabledSemanticOpacity = 0.68f
