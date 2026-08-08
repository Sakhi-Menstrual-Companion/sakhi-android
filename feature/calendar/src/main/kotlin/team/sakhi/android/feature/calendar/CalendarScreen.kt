package team.sakhi.android.feature.calendar

import team.sakhi.android.ui.CloseButton
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel

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
    // Year mode is hoistable so the host can bind it to a sheet detent. iOS ties the
    // two together explicitly -- `HomeCalendarSheet.swift`'s header states
    // "compact = month, expanded = year", and it crossfades the two as the sheet
    // rises. Left null, the screen keeps its own state so it still works standalone.
    yearExpanded: Boolean? = null,
    onYearExpandedChange: ((Boolean) -> Unit)? = null,
    // Real feature build (2026-07-16): propagates the month-view day tap up to
    // Home's own `selectedDate` (matches iOS's real `HomeCalendarSheet`
    // `onDateTap: { date in onDayTap(date) }`, which does NOT dismiss the
    // sheet -- verified directly against `HomeView.swift`/
    // `HomeCalendarSheet.swift`). Separate from `viewModel::selectDate` below,
    // which only drives this screen's own grid-selection/quick-log state.
    onDaySelected: (LocalDate) -> Unit = {},
    /**
     * The user's current cycle phase, used when the selected day has no mark of its own.
     * Supplied by the host because this screen's own state is per-day marks, not the
     * cycle-level phase Home already resolves.
     */
    currentPhase: CyclePhase = CyclePhase.UNKNOWN,
    /**
     * Fired after a log written from this sheet has settled.
     *
     * This screen owns its own `LoggingViewModel` instance (see the constructor doc), so
     * nothing else in the app hears about a log made here. Home kept showing the phase
     * and day it had resolved when it last loaded -- logging or clearing a period from
     * the calendar left its hero stale until the app restarted.
     */
    onLogChanged: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logUiState by logViewModel.uiState.collectAsStateWithLifecycle()

    // Reload the grid whenever a save settles.
    //
    // The grid is driven by `combine(session, visibleMonth)`, and a log changes neither,
    // so logging a period updated Home immediately but left the calendar showing stale
    // marks until the app restarted. Keyed on `saveAttemptId` rather than `isSaving`
    // because a fast save can flip `isSaving` true and back inside one StateFlow emission
    // window, which an effect watching that transition would miss entirely -- the reason
    // that id exists at all. `save()` is asynchronous, so refreshing at the call site
    // would race the write.
    LaunchedEffect(logUiState.saveAttemptId, logUiState.isSaving) {
        if (logUiState.saveAttemptId > 0 && !logUiState.isSaving) {
            viewModel.refreshAfterLogChange()
            onLogChanged()
        }
    }
    val scope = rememberCoroutineScope()
    val hapticManager = koinInject<AndroidHapticManager>()

    LaunchedEffect(uiState.selectedDate) {
        logViewModel.selectDate(uiState.selectedDate)
    }
    val locale = Locale.getDefault()
    val compactHeaders = remember(locale) { localizedWeekdayHeaders(sundayFirst = true, locale = locale) }
    val expandedHeaders = remember(locale) { localizedWeekdayHeaders(sundayFirst = false, locale = locale) }
    var localYearExpanded by rememberSaveable { mutableStateOf(false) }
    val isYearExpanded = yearExpanded ?: localYearExpanded
    val setYearExpanded: (Boolean) -> Unit = onYearExpandedChange ?: { localYearExpanded = it }
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
            // No TOP padding. iOS's month content is explicitly `.padding(.top, 0)`
            // (HomeCalendarSheet.swift) because the 36dp drag-handle row above already
            // provides that spacing. Android was adding another 16dp on top of the
            // handle, which pushed the month bar ~52dp down the sheet and read as a
            // band of dead space above it.
            .padding(
                start = SakhiSpacing.space5,
                end = SakhiSpacing.space5,
                bottom = SakhiSpacing.space4,
            ),
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
                onCollapse = { setYearExpanded(false) },
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
                    setYearExpanded(true)
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
                        setYearExpanded(false)
                    },
                    // Same effect as the header chevrons, including clearing any
                    // in-progress multi-selection, exactly as iOS's `changeYear` does.
                    onChangeYear = { delta ->
                        hapticManager.selection()
                        yearSlideDirection = delta
                        resetYearSelection()
                        viewingYear += delta
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
                    color = sakhiSecondaryLabel(),
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
            // Falls back to the CURRENT cycle phase, not UNKNOWN -- iOS's own
            // `selectedDatePhase` ends `return phase` for exactly this case
            // (HomeCalendarSheet.swift). Most days carry no mark, so the old
            // `?: CyclePhase.UNKNOWN` meant the bar took UNKNOWN's primary (#1F2833) on
            // nearly every day: the log button rendered as a near-black circle on a pink
            // sheet, whatever phase the user was actually in.
            val selectedDatePhase = uiState.days
                .firstOrNull { it.date == uiState.selectedDate }
                ?.mark
                ?.phase
                ?: currentPhase
            // Claims the navigation-bar inset, exactly as iOS reserves
            // `.padding(.bottom, max(safeBottom, 16))` for this same bar. Home's copy
            // already did this; the calendar's did not, because it used to live inside a
            // `ModalBottomSheet` which reserved the inset for it. That stopped being true
            // when the calendar became an in-tree overlay drawn over Home, and the stale
            // comment on Home's copy still claimed otherwise. Invisible on gesture
            // navigation, but on 3-button navigation (Karan's Xiaomi) the OS
            // back/home/recents buttons sat right on top of the Ask Sakhi bar.
            Box(
                modifier = Modifier
                    // Separates the bar from the last row of dates. The gap that used
                    // to sit above the month bar belongs here instead.
                    .padding(top = SakhiSpacing.space5)
                    .navigationBarsPadding(),
            ) {
            SakhiBottomActionBar(
                phase = selectedDatePhase,
                accentColor = phasePrimaryColor(selectedDatePhase),
                isPartnerMode = logUiState.session?.isViewingOwnData == false,
                canLog = logUiState.canLogPeriod && logUiState.canMutateSelectedDate,
                hasLoggedForDate = logUiState.hasAnyData,
                isLogSaving = logUiState.isSaving,
                selectedFlow = logUiState.selectedFlow,
                selectedDate = uiState.selectedDate,
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
            }
        } else if (canEditPeriodDates) {
            // Matches iOS's `canEditPeriodDates: Bool { partnerUserId == nil }` --
            // own data only, stricter than the quick-log bar's permission-based
            // `canLogPeriod` gate: a care viewer can quick-log a single flow entry
            // if granted that permission, but bulk-editing someone else's period
            // history is never allowed here, regardless of permissions.
            // iOS renders this as `.overlay(alignment: .bottom)` with
            // `.padding(.horizontal, 24).padding(.bottom, max(safeBottom, 16))`.
            // Android had no wrapper at all: the `navigationBarsPadding()` Box above
            // wraps only the month view's action bar, so in the year view the pill sat
            // directly on the gesture bar and covered the last month's first row.
            // Given the same treatment as its sibling bar, whose position Karan has
            // already signed off on.
            Box(
                modifier = Modifier
                    .padding(top = SakhiSpacing.space5)
                    .padding(horizontal = EditPeriodDatesBarHorizontalPadding)
                    .navigationBarsPadding(),
            ) {
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
                    CloseButton(
                        onClick = onCancel,
                        // The bar is `inverseSurface`, so it needs the on-gradient
                        // treatment rather than an onSurface tint that would vanish --
                        // but tinted from `inverseOnSurface`, not hardcoded white. That
                        // token flips with the theme exactly as the bar does; a fixed
                        // white glyph disappeared into the light bar in dark mode.
                        onGradient = true,
                        onGradientColor = MaterialTheme.colorScheme.inverseOnSurface,
                        contentDescription = stringResource(R.string.calendar_edit_period_dates_cancel),
                    )
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
                    // Both buttons sit on the dark bar, so they take their colours from
                    // it rather than from the theme's primary. iOS: Undo is a translucent
                    // white capsule (0.14 enabled / 0.07 disabled, text white / white at
                    // 0.30) sized 56x36; Save is a solid white capsule with bar-dark text.
                    // Android was using a default TextButton and Button, which render pink
                    // on pink-adjacent dark and gave Undo no readable disabled state.
                    val onBar = MaterialTheme.colorScheme.inverseOnSurface
                    Surface(
                        shape = CircleShape,
                        color = onBar.copy(alpha = if (canUndo) 0.14f else 0.07f),
                        modifier = Modifier
                            .size(width = 56.dp, height = 36.dp)
                            .clickable(enabled = canUndo, onClick = onUndo)
                            .semantics { role = Role.Button },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.calendar_edit_period_dates_undo),
                                fontSize = 13.sp,
                                color = if (canUndo) onBar else onBar.copy(alpha = 0.30f),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(SakhiSpacing.space2))
                    Surface(
                        shape = CircleShape,
                        color = onBar,
                        modifier = Modifier
                            .clickable(enabled = !isSaving, onClick = onSave)
                            .semantics { role = Role.Button },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.inverseSurface,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.calendar_edit_period_dates_save),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.inverseSurface,
                                )
                            }
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
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
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
                CalendarHeaderGlyph(
                    showPrimary = isOnToday,
                    primaryIcon = Icons.Rounded.KeyboardArrowDown,
                    primaryDescription = stringResource(R.string.calendar_expand_year_view),
                    onPrimaryClick = onExpandYear,
                    secondaryIcon = Icons.Rounded.Refresh,
                    secondaryDescription = stringResource(R.string.calendar_today),
                    onSecondaryClick = onJumpToToday,
                )
            }
        }
        HeaderNavButton(
            onClick = onNextMonth,
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
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
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
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
                // Same treatment as the month header, per Karan -- one shared glyph.
                CalendarHeaderGlyph(
                    showPrimary = isCurrentYear,
                    primaryIcon = Icons.Rounded.KeyboardArrowDown,
                    primaryDescription = stringResource(R.string.calendar_collapse_year_view),
                    onPrimaryClick = onCollapse,
                    secondaryIcon = Icons.Rounded.Refresh,
                    secondaryDescription = stringResource(R.string.calendar_current_year),
                    onSecondaryClick = onResetToCurrentYear,
                )
            }
        }
        HeaderNavButton(
            onClick = onNextYear,
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
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
        // iOS: `Image(systemName: "chevron.left").font(.lato(15, .bold)).frame(44, 44)`
        // -- a small chevron inside a large touch target. Android was drawing a 36dp
        // arrow glyph, which read as an oversized arrow rather than iOS's light chevron.
        modifier = Modifier.size(44.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            // iOS `SakhiCalendarView:116`: the month-nav chevron is
            // `DS.Colors.label` -- full strength. NOT the tertiary used for list
            // disclosure chevrons; different role, two steps darker.
            tint = sakhiLabel(),
            modifier = Modifier.size(18.dp),
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
    /**
     * Horizontal swipe across the months, matching iOS's `simultaneousGesture` on
     * `yearMonthsScroll`: `changeYear(by: w < 0 ? 1 : -1)` past a 50pt drag.
     *
     * Android had no horizontal gesture here at all, so the swipe fell through to the
     * sheet's own drag handling and **collapsed the calendar** instead of changing year —
     * the opposite of what iOS does with the same motion. Handling it here also consumes
     * the horizontal axis, which is what stops the collapse.
     */
    onChangeYear: (Int) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // iOS: minimumDistance 50 on the drag gesture.
    val yearSwipeThresholdPx = with(density) { 50.dp.toPx() }
    var yearDragTotalPx by remember(viewingYear) { mutableFloatStateOf(0f) }
    val yearDragState = rememberDraggableState { delta -> yearDragTotalPx += delta }

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
        modifier = Modifier.draggable(
            state = yearDragState,
            orientation = Orientation.Horizontal,
            onDragStopped = {
                val travelled = yearDragTotalPx
                yearDragTotalPx = 0f
                if (abs(travelled) > yearSwipeThresholdPx) {
                    // Dragging left (negative) moves forward a year, as on iOS.
                    onChangeYear(if (travelled < 0) 1 else -1)
                }
            },
        ),
    ) { year ->
        LazyColumn(
            state = listState,
            // iOS: `VStack(spacing: 0)` — the month label's own top padding (12) is the
            // only separation between months, so an extra 16dp gap here double-spaced them.
            verticalArrangement = Arrangement.spacedBy(0.dp),
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
    // iOS draws no container here. `yearMonthsScroll` is a plain `VStack(spacing: 0)`
    // per month: a left-aligned label (`.lato(15, .bold)`, accent when it is the current
    // calendar month) with `.padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 4)`,
    // and the grid directly beneath. Android had wrapped each month in a rounded, tinted
    // Surface with a border on the visible month -- twelve cards iOS does not have, which
    // also boxed in the grid and ate horizontal room.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Text(
            text = monthLabel(month),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (month.year == compactToday.year && month.month == compactToday.month) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 4.dp),
        )
        SakhiMiniMonthGrid(
            days = days.toSakhiCalendarDays(),
            isMultiSelectMode = isMultiSelectMode,
            selectionSet = yearSelection,
            onDayToggle = onToggleDate,
        )
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
            hasLogDetail = day.hasLogDetail,
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

/**
 * The single glyph that sits to the right of the calendar's month/year title.
 *
 * Real port of iOS `SakhiCalendarView.header`: with `onChevronTap` set, exactly one
 * button is always present -- `chevron.down` while `isOnToday`, `arrow.clockwise`
 * otherwise -- and BOTH are `.frame(width: 24, height: 24)`. That equal size is the
 * whole point: the title and glyph share a centred `HStack`, so if the two states had
 * different widths the title would slide sideways every time you paged off today.
 *
 * Android had exactly that bug: the chevron was a bare 16dp `Icon` while the reset was
 * a 48dp `IconButton`, a 32dp swing that shifted the centred title on every month
 * change. Both now occupy the same fixed box and only the glyph swaps, with iOS's
 * `.easeInOut(duration: 0.2)` and `.opacity.combined(with: .scale(scale: 0.75))`.
 */
@Composable
private fun CalendarHeaderGlyph(
    showPrimary: Boolean,
    primaryIcon: ImageVector,
    primaryDescription: String,
    onPrimaryClick: () -> Unit,
    secondaryIcon: ImageVector,
    secondaryDescription: String,
    onSecondaryClick: () -> Unit,
) {
    AnimatedContent(
        targetState = showPrimary,
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(CalendarGlyphSwapMillis)) +
                    scaleIn(
                        animationSpec = tween(CalendarGlyphSwapMillis),
                        initialScale = CalendarGlyphSwapScale,
                    )
                ) togetherWith fadeOut(animationSpec = tween(CalendarGlyphSwapMillis))
        },
        label = "calendarHeaderGlyph",
    ) { primary ->
        Box(
            modifier = Modifier
                .size(CalendarHeaderGlyphSize)
                .clickable(onClick = if (primary) onPrimaryClick else onSecondaryClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (primary) primaryIcon else secondaryIcon,
                contentDescription = if (primary) primaryDescription else secondaryDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(CalendarHeaderGlyphIconSize),
            )
        }
    }
}

/** iOS `editControl`: `.padding(.horizontal, 24)`. */
private val EditPeriodDatesBarHorizontalPadding = 24.dp

/** iOS: both header glyphs are `.frame(width: 24, height: 24)`. */
private val CalendarHeaderGlyphSize = 24.dp

/** iOS: `.font(.lato(11, .bold))` on the glyph itself. */
private val CalendarHeaderGlyphIconSize = 16.dp

/** iOS: `.animation(.easeInOut(duration: 0.2), value: isOnToday)`. */
private const val CalendarGlyphSwapMillis = 200

/** iOS: `.scale(scale: 0.75)` on the transition. */
private const val CalendarGlyphSwapScale = 0.75f
