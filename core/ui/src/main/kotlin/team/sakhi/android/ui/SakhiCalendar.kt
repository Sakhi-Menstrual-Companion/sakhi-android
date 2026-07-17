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
import kotlinx.datetime.LocalDate
import team.sakhi.android.designsystem.LocalSakhiDarkTheme
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.DesignTokens

@Immutable
data class SakhiCalendarDay(
    val date: LocalDate,
    val isInVisibleMonth: Boolean,
    val isSelected: Boolean = false,
    val isToday: Boolean = false,
    val isFuture: Boolean = false,
    val markerType: SakhiCalendarMarkerType? = null,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        day.markerType == SakhiCalendarMarkerType.OVULATION -> accentColor
        day.markerType == SakhiCalendarMarkerType.FERTILE -> accentColor
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
        day.isFuture && day.markerType == SakhiCalendarMarkerType.OVULATION -> accentColor.copy(alpha = disabledSemanticOpacity)
        day.isFuture && day.markerType == SakhiCalendarMarkerType.FERTILE -> accentColor.copy(alpha = disabledSemanticOpacity)
        day.isFuture && day.markerType == SakhiCalendarMarkerType.PREDICTED_PERIOD -> periodColor.copy(alpha = disabledSemanticOpacity)
        day.markerType == SakhiCalendarMarkerType.OVULATION -> accentColor
        day.markerType == SakhiCalendarMarkerType.FERTILE -> accentColor
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
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (day.isSelected || day.isToday || day.markerType == SakhiCalendarMarkerType.PERIOD) {
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

@Immutable
private data class SakhiCalendarWeekRows(
    val rows: List<List<SakhiCalendarDay>>,
)

private val calendarCellHeight = SakhiSpacing.space10 + SakhiSpacing.space1 * 2
private val calendarDotSize = SakhiSpacing.space8 + SakhiSpacing.space1 / 2
private val calendarRingSize = SakhiSpacing.space10 + SakhiSpacing.space1 / 2
private val calendarRingStroke = SakhiSpacing.space1 / 2 + SakhiSpacing.space1 / 8
private val yearGridCellHeight = SakhiSpacing.space6
private val yearGridDotSize = SakhiSpacing.space5
private const val disabledSemanticOpacity = 0.68f
