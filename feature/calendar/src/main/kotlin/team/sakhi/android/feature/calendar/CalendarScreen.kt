package team.sakhi.android.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.ui.SakhiCalendarDay
import team.sakhi.android.ui.SakhiCalendarMarkerType
import team.sakhi.android.ui.SakhiCalendarMonthGrid
import team.sakhi.android.ui.SakhiMiniMonthGrid
import team.sakhi.android.ui.SakhiWeekdayHeaderRow
import team.sakhi.date.DateConverter
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Android port of iOS `HomeCalendarSheet.swift` + `HomeCalendarYearGrid.swift`
 * focused on the two remaining parity gaps:
 * 1. horizontal month swiping using a three-panel drag pager,
 * 2. expanded year browsing that jumps back into the compact month view.
 *
 * The existing day-cell state priority (selected/today/period/predicted/fertile/
 * ovulation) is preserved; this file only changes the container behavior.
 */
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val hapticManager = koinInject<AndroidHapticManager>()
    val locale = Locale.getDefault()
    val compactHeaders = remember(locale) { localizedWeekdayHeaders(sundayFirst = true, locale = locale) }
    val expandedHeaders = remember(locale) { localizedWeekdayHeaders(sundayFirst = false, locale = locale) }
    var isYearExpanded by rememberSaveable { mutableStateOf(false) }
    var viewingYear by rememberSaveable { mutableIntStateOf(uiState.visibleMonth.year) }
    var yearSlideDirection by rememberSaveable { mutableIntStateOf(1) }
    var monthDragOffsetPx by remember { mutableFloatStateOf(0f) }
    var monthPanelWidthPx by remember { mutableFloatStateOf(0f) }
    var isMonthAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(isYearExpanded, viewingYear) {
        if (isYearExpanded) {
            viewModel.ensureYearLoaded(viewingYear)
        }
    }

    LaunchedEffect(uiState.visibleMonth.year, isYearExpanded) {
        if (!isYearExpanded) {
            viewingYear = uiState.visibleMonth.year
        }
    }

    suspend fun settleMonthOffset(target: Float) {
        animate(
            initialValue = monthDragOffsetPx,
            targetValue = target,
            animationSpec = spring(stiffness = 340f, dampingRatio = 0.88f),
        ) { value, _ ->
            monthDragOffsetPx = value
        }
    }

    suspend fun animateMonthChange(direction: Int) {
        if (isMonthAnimating) return
        if (monthPanelWidthPx <= 0f) {
            if (direction < 0) viewModel.showPreviousMonth() else viewModel.showNextMonth()
            return
        }

        isMonthAnimating = true
        settleMonthOffset(if (direction < 0) monthPanelWidthPx else -monthPanelWidthPx)
        if (direction < 0) {
            viewModel.showPreviousMonth()
        } else {
            viewModel.showNextMonth()
        }
        monthDragOffsetPx = 0f
        isMonthAnimating = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space4),
    ) {
        if (isYearExpanded) {
            CalendarYearHeader(
                viewingYear = viewingYear,
                isCurrentYear = viewingYear == compactToday.year,
                onPreviousYear = {
                    hapticManager.selection()
                    yearSlideDirection = -1
                    viewingYear -= 1
                },
                onNextYear = {
                    hapticManager.selection()
                    yearSlideDirection = 1
                    viewingYear += 1
                },
                onCollapse = { isYearExpanded = false },
                onResetToCurrentYear = {
                    hapticManager.selection()
                    yearSlideDirection = if (compactToday.year > viewingYear) 1 else -1
                    viewingYear = compactToday.year
                },
            )
            SakhiWeekdayHeaderRow(labels = expandedHeaders)
        } else {
            CalendarHeader(
                visibleMonth = uiState.visibleMonth,
                selectedDate = uiState.selectedDate,
                onPreviousMonth = { scope.launch { animateMonthChange(direction = -1) } },
                onNextMonth = { scope.launch { animateMonthChange(direction = 1) } },
                onJumpToToday = viewModel::jumpToToday,
                onExpandYear = {
                    viewingYear = uiState.visibleMonth.year
                    isYearExpanded = true
                },
            )
            SakhiWeekdayHeaderRow(labels = compactHeaders)
        }

        AnimatedContent(
            targetState = isYearExpanded,
            transitionSpec = {
                if (targetState) {
                    (fadeIn() + slideInVertically { it / 6 }).togetherWith(
                        fadeOut() + slideOutVertically { -it / 8 },
                    )
                } else {
                    (fadeIn() + slideInVertically { -it / 8 }).togetherWith(
                        fadeOut() + slideOutVertically { it / 6 },
                    )
                }
            },
            label = "calendar_content_mode",
        ) { expanded ->
            if (expanded) {
                CalendarYearView(
                    viewingYear = viewingYear,
                    visibleMonth = uiState.visibleMonth,
                    monthCache = uiState.monthCache,
                    slideDirection = yearSlideDirection,
                    onMonthSelected = { month ->
                        viewModel.jumpToMonth(month)
                        isYearExpanded = false
                    },
                )
            } else {
                SwipeableMonthPager(
                    visibleMonth = uiState.visibleMonth,
                    selectedDate = uiState.selectedDate,
                    monthCache = uiState.monthCache,
                    dragOffsetPx = monthDragOffsetPx,
                    onDragOffsetChanged = { next ->
                        if (!isMonthAnimating) {
                            monthDragOffsetPx = next
                        }
                    },
                    onWidthResolved = { width -> monthPanelWidthPx = width },
                    onMonthCommit = { direction ->
                        scope.launch { animateMonthChange(direction) }
                    },
                    onDateSelected = viewModel::selectDate,
                )
            }
        }

        when {
            uiState.error != null -> {
                Text(
                    text = uiState.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = SakhiSpacing.space4),
                )
            }
            !uiState.hasAnyCalendarAccess -> {
                Text(
                    text = stringResource(R.string.calendar_no_access),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = SakhiSpacing.space4),
                )
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    visibleMonth: LocalDate,
    selectedDate: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToToday: () -> Unit,
    onExpandYear: () -> Unit,
) {
    val isCurrentMonth = visibleMonth.year == compactToday.year && visibleMonth.month == compactToday.month
    val isOnToday = isCurrentMonth && selectedDate == compactToday

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderNavButton(
            onClick = onPreviousMonth,
            icon = Icons.Rounded.ChevronLeft,
            contentDescription = stringResource(R.string.calendar_previous_month),
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.clickable(enabled = isOnToday, onClick = onExpandYear),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space1 + SakhiSpacing.space1 / 2),
            ) {
                Text(
                    text = monthLabel(visibleMonth),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                when {
                    isOnToday -> {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.calendar_expand_year_view),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(SakhiSpacing.space4),
                        )
                    }
                    else -> {
                        IconButton(
                            onClick = onJumpToToday,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.calendar_today),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(SakhiSpacing.space3),
                            )
                        }
                    }
                }
            }
        }
        HeaderNavButton(
            onClick = onNextMonth,
            icon = Icons.Rounded.ChevronRight,
            contentDescription = stringResource(R.string.calendar_next_month),
        )
    }
}

@Composable
private fun CalendarYearHeader(
    viewingYear: Int,
    isCurrentYear: Boolean,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onCollapse: () -> Unit,
    onResetToCurrentYear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderNavButton(
            onClick = onPreviousYear,
            icon = Icons.Rounded.ChevronLeft,
            contentDescription = stringResource(R.string.calendar_previous_year),
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space1 + SakhiSpacing.space1 / 2),
            ) {
                Text(
                    text = viewingYear.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isCurrentYear) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.calendar_collapse_year_view),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(SakhiSpacing.space3),
                        )
                    }
                } else {
                    IconButton(
                        onClick = onResetToCurrentYear,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.calendar_current_year),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(SakhiSpacing.space3),
                        )
                    }
                }
            }
        }
        HeaderNavButton(
            onClick = onNextYear,
            icon = Icons.Rounded.ChevronRight,
            contentDescription = stringResource(R.string.calendar_next_year),
        )
    }
}

@Composable
private fun HeaderNavButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SakhiSpacing.space8 + SakhiSpacing.space1),
        )
    }
}

@Composable
private fun SwipeableMonthPager(
    visibleMonth: LocalDate,
    selectedDate: LocalDate,
    monthCache: CalendarMonthCache,
    dragOffsetPx: Float,
    onDragOffsetChanged: (Float) -> Unit,
    onWidthResolved: (Float) -> Unit,
    onMonthCommit: (Int) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val panelWidth = maxWidth
        val panelWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val draggableState = rememberDraggableState { delta ->
            onDragOffsetChanged((dragOffsetPx + delta).coerceIn(-panelWidthPx, panelWidthPx))
        }

        LaunchedEffect(panelWidthPx) {
            onWidthResolved(panelWidthPx)
        }

        val previousMonth = monthStart(visibleMonth.minus(1, DateTimeUnit.MONTH))
        val nextMonth = monthStart(visibleMonth.plus(1, DateTimeUnit.MONTH))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val threshold = panelWidthPx * 0.28f
                        when {
                            dragOffsetPx < -threshold || velocity < -1800f -> onMonthCommit(1)
                            dragOffsetPx > threshold || velocity > 1800f -> onMonthCommit(-1)
                            else -> {
                                scope.launch {
                                    animate(
                                        initialValue = dragOffsetPx,
                                        targetValue = 0f,
                                        animationSpec = spring(stiffness = 340f, dampingRatio = 0.88f),
                                    ) { value, _ ->
                                        onDragOffsetChanged(value)
                                    }
                                }
                            }
                        }
                    },
                ),
        ) {
            Row(
                modifier = Modifier
                    .width(panelWidth * 3)
                    .offset {
                        IntOffset((dragOffsetPx - panelWidthPx).roundToInt(), 0)
                    },
            ) {
                MonthPanel(
                    month = previousMonth,
                    days = monthCache[previousMonth],
                    selectedDate = selectedDate,
                    onDateSelected = onDateSelected,
                    modifier = Modifier.width(panelWidth),
                )
                MonthPanel(
                    month = visibleMonth,
                    days = monthCache[visibleMonth],
                    selectedDate = selectedDate,
                    onDateSelected = onDateSelected,
                    modifier = Modifier.width(panelWidth),
                )
                MonthPanel(
                    month = nextMonth,
                    days = monthCache[nextMonth],
                    selectedDate = selectedDate,
                    onDateSelected = onDateSelected,
                    modifier = Modifier.width(panelWidth),
                )
            }
        }
    }
}

@Composable
private fun MonthPanel(
    month: LocalDate,
    days: List<CalendarDayUiState>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    SakhiCalendarMonthGrid(
        days = (if (days.isNotEmpty()) days else fallbackMonthCells(month)).toSakhiCalendarDays(
            selectedDate = selectedDate,
        ),
        onDayClick = onDateSelected,
        modifier = modifier,
    )
}

@Composable
private fun CalendarYearView(
    viewingYear: Int,
    visibleMonth: LocalDate,
    monthCache: CalendarMonthCache,
    slideDirection: Int,
    onMonthSelected: (LocalDate) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(viewingYear, visibleMonth, listState) {
        val targetIndex = if (viewingYear == visibleMonth.year) {
            visibleMonth.monthNumber - 1
        } else {
            0
        }
        listState.scrollToItem(targetIndex)
    }

    AnimatedContent(
        targetState = viewingYear,
        transitionSpec = {
            yearSlideTransition(slideDirection)
        },
        label = "calendar_year_change",
    ) { year ->
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        ) {
            items(12) { monthIndex ->
                val monthStart = LocalDate(year, monthIndex + 1, 1)
                CalendarYearMonthCard(
                    month = monthStart,
                    days = monthCache[monthStart].ifEmpty { fallbackMonthCells(monthStart) },
                    isVisibleMonth = monthStart.year == visibleMonth.year && monthStart.month == visibleMonth.month,
                    onClick = { onMonthSelected(monthStart) },
                )
            }
        }
    }
}

private fun AnimatedContentTransitionScope<Int>.yearSlideTransition(
    direction: Int,
): ContentTransform {
    return if (direction >= 0) {
        (fadeIn() + slideInHorizontally { it / 3 }).togetherWith(
            fadeOut() + slideOutHorizontally { -it / 3 },
        )
    } else {
        (fadeIn() + slideInHorizontally { -it / 3 }).togetherWith(
            fadeOut() + slideOutHorizontally { it / 3 },
        )
    }
}

@Composable
private fun CalendarYearMonthCard(
    month: LocalDate,
    days: List<CalendarDayUiState>,
    isVisibleMonth: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xxl),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        border = if (isVisibleMonth) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            Text(
                text = monthLabel(month),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (month.year == compactToday.year && month.month == compactToday.month) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            SakhiMiniMonthGrid(days = days.toSakhiCalendarDays())
        }
    }
}

private fun monthLabel(month: LocalDate): String {
    val monthName = Month.of(month.monthNumber).getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$monthName ${month.year}"
}

private fun fallbackMonthCells(visibleMonth: LocalDate): List<CalendarDayUiState> {
    val start = yearGridMonthStart(visibleMonth)
    return List(GRID_CELL_COUNT) { index ->
        val date = DateConverter.addDays(start, index)
        CalendarDayUiState(
            date = date,
            isInVisibleMonth = date.month == visibleMonth.month && date.year == visibleMonth.year,
        )
    }
}

private fun yearGridMonthStart(month: LocalDate): LocalDate {
    val offset = month.dayOfWeek.isoDayNumber - 1
    return DateConverter.subtractDays(month, offset)
}

private fun monthStart(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

private const val GRID_CELL_COUNT = 42

private val compactToday: LocalDate
    get() = DateConverter.today()

private fun localizedWeekdayHeaders(
    sundayFirst: Boolean,
    locale: Locale,
): List<String> {
    val days = if (sundayFirst) {
        listOf(
            java.time.DayOfWeek.SUNDAY,
            java.time.DayOfWeek.MONDAY,
            java.time.DayOfWeek.TUESDAY,
            java.time.DayOfWeek.WEDNESDAY,
            java.time.DayOfWeek.THURSDAY,
            java.time.DayOfWeek.FRIDAY,
            java.time.DayOfWeek.SATURDAY,
        )
    } else {
        listOf(
            java.time.DayOfWeek.MONDAY,
            java.time.DayOfWeek.TUESDAY,
            java.time.DayOfWeek.WEDNESDAY,
            java.time.DayOfWeek.THURSDAY,
            java.time.DayOfWeek.FRIDAY,
            java.time.DayOfWeek.SATURDAY,
            java.time.DayOfWeek.SUNDAY,
        )
    }
    return days.map { it.getDisplayName(TextStyle.NARROW, locale) }
}

private fun List<CalendarDayUiState>.toSakhiCalendarDays(
    selectedDate: LocalDate? = null,
): List<SakhiCalendarDay> {
    val today = compactToday
    return map { day ->
        SakhiCalendarDay(
            date = day.date,
            isInVisibleMonth = day.isInVisibleMonth,
            isSelected = selectedDate != null && day.isInVisibleMonth && day.date == selectedDate,
            isToday = day.date == today,
            isFuture = day.date > today,
            markerType = when {
                day.mark?.isPeriod == true -> SakhiCalendarMarkerType.PERIOD
                day.mark?.isPredictedPeriod == true -> SakhiCalendarMarkerType.PREDICTED_PERIOD
                day.mark?.isOvulation == true -> SakhiCalendarMarkerType.OVULATION
                day.mark?.isFertile == true -> SakhiCalendarMarkerType.FERTILE
                else -> null
            },
        )
    }
}
