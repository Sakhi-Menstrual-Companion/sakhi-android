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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.res.pluralStringResource
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
import team.sakhi.android.designsystem.phasePrimaryColor
import team.sakhi.android.feature.logging.LoggingViewModel
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.ui.SakhiBottomActionBar
import team.sakhi.android.ui.SakhiCalendarDay
import team.sakhi.android.ui.SakhiCalendarMarkerType
import team.sakhi.android.ui.SakhiCalendarMonthGrid
import team.sakhi.android.ui.SakhiMiniMonthGrid
import team.sakhi.android.ui.SakhiWeekdayHeaderRow
import team.sakhi.date.DateConverter
import team.sakhi.models.CyclePhase
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Android port of iOS `HomeCalendarSheet.swift` + `HomeCalendarYearGrid.swift`.
 * Now includes the sheet's own bottom action bar (`SakhiBottomActionBar`, shared
 * with `feature:home` via `core:ui` -- matches iOS's own `HomeActionBar.swift`
 * being reused by both `HomeView` and this exact sheet) and the real month-swipe/
 * year-browsing parity gaps a previous pass already covered.
 *
 * The existing day-cell state priority (selected/today/period/predicted/fertile/
 * ovulation) is preserved; this file only changes the container behavior.
 */
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = koinViewModel(),
    // A dedicated `LoggingViewModel` instance (Koin's `viewModel { ... }` factory
    // registration gives every `koinViewModel()` call site its own instance --
    // see `LoggingFeatureModule.kt`), kept in sync with whichever date is
    // currently selected in the grid via the `LaunchedEffect` below. This is
    // deliberately separate from Home's own `quickLogViewModel` and from the
    // full `LoggingSheet`'s instance, matching iOS's own dedicated
    // `calendarLogVM` (`HomeCalendarSheet.swift`).
    logViewModel: LoggingViewModel = koinViewModel(),
    onAskSakhi: () -> Unit = {},
    onLog: (LocalDate) -> Unit = {},
    // Real feature build (2026-07-16): propagates the month-view day tap up to
    // Home's own `selectedDate` (matches iOS's real `HomeCalendarSheet`
    // `onDateTap: { date in onDayTap(date) }`, which does NOT dismiss the
    // sheet -- verified directly against `HomeView.swift`/
    // `HomeCalendarSheet.swift`). Separate from `viewModel::selectDate` below,
    // which only drives this screen's own grid-selection/quick-log state.
    onDaySelected: (LocalDate) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logUiState by logViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val hapticManager = koinInject<AndroidHapticManager>()

    LaunchedEffect(uiState.selectedDate) {
        logViewModel.selectDate(uiState.selectedDate)
    }
    val locale = Locale.getDefault()
    val compactHeaders = remember(locale) { localizedWeekdayHeaders(sundayFirst = true, locale = locale) }
    val expandedHeaders = remember(locale) { localizedWeekdayHeaders(sundayFirst = false, locale = locale) }
    var isYearExpanded by rememberSaveable { mutableStateOf(false) }
    var viewingYear by rememberSaveable { mutableIntStateOf(uiState.visibleMonth.year) }
    var yearSlideDirection by rememberSaveable { mutableIntStateOf(1) }
    var monthDragOffsetPx by remember { mutableFloatStateOf(0f) }
    var monthPanelWidthPx by remember { mutableFloatStateOf(0f) }
    var isMonthAnimating by remember { mutableStateOf(false) }

    // Year-view multi-select "Edit Period Dates" state -- matches iOS's own
    // `@State private var yearSelection/selectionHistory/isMultiSelectMode` on
    // `HomeCalendarSheet` exactly (transient, view-scoped scratch state, not
    // persisted ViewModel state; losing it on process death is an accepted
    // edge case, same as iOS's `@State` resetting on a fresh view instance).
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var yearSelection by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    // (date, wasAdded) per toggle, most recent last -- powers "Undo," matching
    // iOS's `selectionHistory: [(date: Date, added: Bool)]` exactly.
    var selectionHistory by remember { mutableStateOf<List<Pair<LocalDate, Boolean>>>(emptyList()) }

    fun resetYearSelection() {
        yearSelection = emptySet()
        selectionHistory = emptyList()
        isMultiSelectMode = false
    }

    // Matches iOS's `.onChange(of: isExpanded) { if !expanded { ...reset... } }` --
    // collapsing back to month view (chevron tap or picking a month card) always
    // clears any in-progress multi-select, same as leaving edit mode for real.
    LaunchedEffect(isYearExpanded) {
        if (!isYearExpanded) resetYearSelection()
    }

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
                    resetYearSelection()
                    viewingYear -= 1
                },
                onNextYear = {
                    hapticManager.selection()
                    yearSlideDirection = 1
                    resetYearSelection()
                    viewingYear += 1
                },
                onCollapse = { isYearExpanded = false },
                onResetToCurrentYear = {
                    hapticManager.selection()
                    yearSlideDirection = if (compactToday.year > viewingYear) 1 else -1
                    resetYearSelection()
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
            modifier = Modifier.weight(1f),
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
                    isMultiSelectMode = isMultiSelectMode,
                    yearSelection = yearSelection,
                    onMonthSelected = { month ->
                        viewModel.jumpToMonth(month)
                        isYearExpanded = false
                    },
                    onToggleDate = { date ->
                        hapticManager.impact(HapticImpact.LIGHT)
                        if (yearSelection.contains(date)) {
                            yearSelection = yearSelection - date
                            selectionHistory = selectionHistory + (date to false)
                        } else {
                            yearSelection = yearSelection + date
                            selectionHistory = selectionHistory + (date to true)
                        }
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
                    onDateSelected = { date ->
                        viewModel.selectDate(date)
                        onDaySelected(date)
                    },
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

        // Matches iOS's real `HomeCalendarSheet.bottomBar` -- the sheet's own
        // "Ask Sakhi"/"Log" action bar, previously entirely absent from Android's
        // Calendar sheet (confirmed on-device in the second parity sweep: the
        // sheet was just the grid over empty space with no way to log or ask
        // Sakhi about the selected date without first closing the sheet).
        // `showCalendarButton = false` matches iOS's own convenience init used
        // specifically by the calendar sheet (`CalendarBtn == EmptyView`) -- no
        // point showing a calendar button from inside the calendar itself.
        //
        // iOS nests `bottomBar` inside `monthContent` only (line 548) -- `yearContent`
        // renders `editControl` (the multi-select "Edit Period Dates" toggle)
        // instead, never `SakhiBottomActionBar`. Mirrored below with the same
        // `!isYearExpanded`/`isYearExpanded` split.
        val canEditPeriodDates = logUiState.session?.isViewingOwnData == true
        if (!isYearExpanded) {
            val selectedDatePhase = uiState.days
                .firstOrNull { it.date == uiState.selectedDate }
                ?.mark
                ?.phase
                ?: CyclePhase.UNKNOWN
            SakhiBottomActionBar(
                phase = selectedDatePhase,
                accentColor = phasePrimaryColor(selectedDatePhase),
                isPartnerMode = logUiState.session?.isViewingOwnData == false,
                canLog = logUiState.canLogPeriod && logUiState.canMutateSelectedDate,
                hasLoggedForDate = logUiState.hasAnyData,
                isLogSaving = logUiState.isSaving,
                selectedFlow = logUiState.selectedFlow,
                showCalendarButton = false,
                onAskSakhiClick = {
                    hapticManager.selection()
                    onAskSakhi()
                },
                onLogClick = {
                    hapticManager.impact(HapticImpact.MEDIUM)
                    onLog(uiState.selectedDate)
                },
                onQuickLogFlow = { level ->
                    hapticManager.selection()
                    logViewModel.onFlowSelected(level)
                    logViewModel.save()
                },
            )
        } else if (canEditPeriodDates) {
            // Matches iOS's `canEditPeriodDates: Bool { partnerUserId == nil }` --
            // own data only, stricter than the quick-log bar's permission-based
            // `canLogPeriod` gate: a care viewer can quick-log a single flow entry
            // if granted that permission, but bulk-editing someone else's period
            // history is never allowed here, regardless of permissions.
            EditPeriodDatesBar(
                isMultiSelectMode = isMultiSelectMode,
                selectionCount = yearSelection.size,
                canUndo = selectionHistory.isNotEmpty(),
                isSaving = logUiState.isSavingYearSelection,
                onStart = {
                    hapticManager.impact(HapticImpact.LIGHT)
                    isMultiSelectMode = true
                },
                onCancel = {
                    hapticManager.impact(HapticImpact.LIGHT)
                    resetYearSelection()
                },
                onUndo = {
                    val last = selectionHistory.lastOrNull() ?: return@EditPeriodDatesBar
                    hapticManager.impact(HapticImpact.LIGHT)
                    selectionHistory = selectionHistory.dropLast(1)
                    yearSelection = if (last.second) yearSelection - last.first else yearSelection + last.first
                },
                onSave = {
                    val datesToSave = yearSelection.toList()
                    scope.launch {
                        logViewModel.saveYearSelection(datesToSave)
                        resetYearSelection()
                    }
                },
            )
        }
    }
}

/**
 * Android port of iOS's real `editControl` (`HomeCalendarSheet.swift`, lines
 * ~707-772) -- the year view's "Edit Period Dates" bar. Three states exactly
 * matching iOS's: idle full-width toggle button; active with an empty
 * selection (label + a close "X" to cancel); active with a non-empty
 * selection (day count + Undo + Save, Save showing a spinner while
 * [isSaving]). Uses `inverseSurface`/`inverseOnSurface` for the same
 * "high-contrast pill regardless of app theme" effect as iOS's fixed
 * `DS.Colors.calBarDark` -- there's no existing shared token for that exact
 * treatment yet, and Material3's inverse-surface pair is the closest built-in
 * semantic equivalent rather than a one-off hardcoded color.
 */
@Composable
private fun EditPeriodDatesBar(
    isMultiSelectMode: Boolean,
    selectionCount: Int,
    canUndo: Boolean,
    isSaving: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = SakhiSpacing.space1,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (isMultiSelectMode && selectionCount > 0) 54.dp else 50.dp),
    ) {
        when {
            !isMultiSelectMode -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onStart),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.EditCalendar, contentDescription = null)
                    Spacer(modifier = Modifier.width(SakhiSpacing.space2))
                    Text(
                        text = stringResource(R.string.calendar_edit_period_dates),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                    )
                }
            }
            selectionCount == 0 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = SakhiSpacing.space4, end = SakhiSpacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.calendar_edit_period_dates_active),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.calendar_edit_period_dates_cancel),
                        )
                    }
                }
            }
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = SakhiSpacing.space4, end = SakhiSpacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.calendar_edit_period_dates_count,
                            selectionCount,
                            selectionCount,
                        ),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onUndo, enabled = canUndo) {
                        Text(stringResource(R.string.calendar_edit_period_dates_undo))
                    }
                    Spacer(modifier = Modifier.width(SakhiSpacing.space2))
                    Button(onClick = onSave, enabled = !isSaving) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.calendar_edit_period_dates_save))
                        }
                    }
                }
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
            icon = Icons.AutoMirrored.Filled.ArrowBack,
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
            icon = Icons.AutoMirrored.Filled.ArrowForward,
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
            icon = Icons.AutoMirrored.Filled.ArrowBack,
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
            icon = Icons.AutoMirrored.Filled.ArrowForward,
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
                    // `.width()` respects the incoming max-width constraint from
                    // this Box (which is only one panel wide, via `fillMaxWidth()`
                    // above) -- so a plain `.width(panelWidth * 3)` here was being
                    // clamped straight back down to a single panel's width. The
                    // three `MonthPanel`s (previous/visible/next) then had to
                    // fight over that single panel's worth of space: the first
                    // claimed it all, and the actually-visible (middle) panel --
                    // the one the `-panelWidthPx` offset below scrolls into view --
                    // was left with ~0 width, collapsing its 7-wide day grid down
                    // to a sliver with only its first column rendering content.
                    // `.requiredWidth()` ignores the incoming constraint so this
                    // row can genuinely be 3 panels wide, same as the `.offset` /
                    // `.clipToBounds()` swipe mechanics below already assumed. Real,
                    // reproducible bug found on the first-ever signed-in device
                    // walkthrough (confirmed via `SakhiCalendarMonthGrid` always
                    // receiving the correct 42-day/6-row/7-per-row data -- this was
                    // a pure layout bug, not a data bug).
                    //
                    // Second real bug, found doing a genuine iOS-parity check right
                    // after the fix above: this Box (the drag/clip viewport) already
                    // centers an over-width `requiredWidth` child on its middle third
                    // by default (`Box`'s default `Alignment.TopStart` content
                    // alignment, applied to a child wider than the Box itself, ends up
                    // centering that child here) -- so the offset only ever needed to
                    // apply the *live drag delta*. The extra `- panelWidthPx` term
                    // double-applied a full panel's worth of leftward shift on top of
                    // that, permanently showing the *next* month's panel while the
                    // header (driven by the same `visibleMonth` state, computed
                    // separately) correctly showed the current one -- confirmed with a
                    // temporary on-screen `month` marker showing "2026-08-01" under a
                    // "July 2026" header. Every date in the compact grid was rendered
                    // one real month ahead of what the header and the rest of the app
                    // (Home's "Started 3 Jul" period card, etc.) agreed was true.
                    .requiredWidth(panelWidth * 3)
                    .offset {
                        IntOffset(dragOffsetPx.roundToInt(), 0)
                    },
            ) {
                // Explicit `key(month)` per panel: found via a real device parity check
                // (comparing against iOS's actual weekday alignment) that this row's three
                // unkeyed `MonthPanel` calls let Compose match children positionally across
                // recompositions. Real, reproducible bug: a debug capture showed the
                // "visible month" (July) panel's own composition intermittently skipped on
                // some recomposition passes -- when that happened, the next positional slot
                // (originally July's) got re-matched to "next month" (August)'s content
                // instead, so the header correctly read "July 2026" while the actual grid
                // silently rendered August's day/weekday arrangement. Each panel's month
                // value is a stable, natural identity -- keying by it makes Compose track
                // each panel by identity instead of position, regardless of which of the
                // three calls does or doesn't recompose in a given frame.
                key(previousMonth) {
                    MonthPanel(
                        month = previousMonth,
                        days = monthCache[previousMonth],
                        selectedDate = selectedDate,
                        onDateSelected = onDateSelected,
                        modifier = Modifier.width(panelWidth),
                    )
                }
                key(visibleMonth) {
                    MonthPanel(
                        month = visibleMonth,
                        days = monthCache[visibleMonth],
                        selectedDate = selectedDate,
                        onDateSelected = onDateSelected,
                        modifier = Modifier.width(panelWidth),
                    )
                }
                key(nextMonth) {
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
}

@Composable
private fun MonthPanel(
    month: LocalDate,
    days: List<CalendarDayUiState>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourceDays = days.ifEmpty { fallbackMonthCells(month) }
    SakhiCalendarMonthGrid(
        days = sundayFirstMonthCells(month, sourceDays).toSakhiCalendarDays(
            selectedDate = selectedDate,
        ),
        onDayClick = onDateSelected,
        modifier = modifier,
    )
}

// `CalendarViewModel`'s `monthCache` (and this file's own `fallbackMonthCells`) build
// every month's 42-cell grid Monday-first (`isoDayNumber - 1`), which is correct for
// this file's year-expanded view (its header really is Monday-first, matching iOS's
// `HomeCalendarSheet.swift` hardcoded `["M","T","W","T","F","S","S"]`) but wrong for
// this compact swipeable pager: its own header (`compactHeaders` above,
// `sundayFirst = true`) matches iOS's real `SakhiCalendarView.swift` compact grid
// (`lead = cal.component(.weekday, from: start) - 1`, a fixed Sunday-first offset),
// not the year view's. Real, reproducible bug found doing a side-by-side iOS
// comparison after the earlier layout-collapse fix: every date rendered two columns
// off from where the real iOS app puts it (e.g. Wed 1 Jul 2026 rendered under "F",
// not "W"). Re-derives a genuinely Sunday-first 42-cell grid for this specific view
// from the same per-date marks already present in the Monday-first source list
// (looked up by date, not by list position) rather than changing the shared
// `monthCache`, which the year view still needs Monday-first.
private fun sundayFirstMonthCells(
    visibleMonth: LocalDate,
    monthlyDays: List<CalendarDayUiState>,
): List<CalendarDayUiState> {
    val byDate = monthlyDays.associateBy { it.date }
    val offset = visibleMonth.dayOfWeek.isoDayNumber % 7
    val start = DateConverter.subtractDays(visibleMonth, offset)
    return List(GRID_CELL_COUNT) { index ->
        val date = DateConverter.addDays(start, index)
        byDate[date] ?: CalendarDayUiState(
            date = date,
            isInVisibleMonth = date.month == visibleMonth.month && date.year == visibleMonth.year,
        )
    }
}

@Composable
private fun CalendarYearView(
    viewingYear: Int,
    visibleMonth: LocalDate,
    monthCache: CalendarMonthCache,
    slideDirection: Int,
    onMonthSelected: (LocalDate) -> Unit,
    isMultiSelectMode: Boolean = false,
    yearSelection: Set<LocalDate> = emptySet(),
    onToggleDate: (LocalDate) -> Unit = {},
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
                    // Whole-card tap-to-jump is a distinct interaction from
                    // per-day tap-to-toggle -- matching iOS, which only ever
                    // wires day-level taps in the year view (`onDayTap`/
                    // `onToggleDate` on `YearDayCell`, never a month-level tap
                    // target at all). Disabled during multi-select so a tap
                    // meant to select several days across different months
                    // can't accidentally also jump the compact view to one of them.
                    onClick = { if (!isMultiSelectMode) onMonthSelected(monthStart) },
                    isMultiSelectMode = isMultiSelectMode,
                    yearSelection = yearSelection,
                    onToggleDate = onToggleDate,
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
    isMultiSelectMode: Boolean = false,
    yearSelection: Set<LocalDate> = emptySet(),
    onToggleDate: (LocalDate) -> Unit = {},
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
            SakhiMiniMonthGrid(
                days = days.toSakhiCalendarDays(),
                isMultiSelectMode = isMultiSelectMode,
                selectionSet = yearSelection,
                onDayToggle = onToggleDate,
            )
        }
    }
}

@Composable
private fun monthLabel(month: LocalDate): String {
    val monthName = Month.of(month.monthNumber).getDisplayName(TextStyle.FULL, Locale.getDefault())
    return stringResource(R.string.calendar_month_year, monthName, month.year)
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
