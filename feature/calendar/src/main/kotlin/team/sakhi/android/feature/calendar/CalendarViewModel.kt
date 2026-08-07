package team.sakhi.android.feature.calendar

import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.android.common.CycleInsightAdapter
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.cycle.CalendarMarker
import team.sakhi.date.DateConverter
import team.sakhi.models.CycleData
import team.sakhi.models.CyclePhase
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.session.Permission
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.android.common.toSafeUserMessage

private fun currentMonthStart(): LocalDate {
    val today = DateConverter.today()
    return LocalDate(today.year, today.month, 1)
}

@Immutable
data class CalendarDayUiState(
    val date: LocalDate,
    val isInVisibleMonth: Boolean,
    val mark: CalendarMarker.DayMark? = null,
)

@Immutable
data class CalendarMonthCache(
    val months: Map<LocalDate, List<CalendarDayUiState>> = emptyMap(),
) {
    operator fun get(month: LocalDate): List<CalendarDayUiState> = months[month].orEmpty()

    operator fun plus(other: CalendarMonthCache): CalendarMonthCache =
        CalendarMonthCache(months = months + other.months)
}

@Immutable
data class CalendarUiState(
    val visibleMonth: LocalDate = currentMonthStart(),
    val selectedDate: LocalDate = DateConverter.today(),
    val days: List<CalendarDayUiState> = emptyList(),
    val monthCache: CalendarMonthCache = CalendarMonthCache(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasAnyCalendarAccess: Boolean = false,
)

/**
 * Thin calendar-state adapter over KMM session, cycle repositories, and shared
 * calendar marker building. Android never computes cycle or phase rules itself.
 */
class CalendarViewModel(
    private val sessionManager: SessionManager,
    private val cycleDataRepository: CycleDataRepository,
    private val periodLogRepository: PeriodLogRepository,
    private val hapticManager: AndroidHapticManager,
    private val appContext: Context,
) : ViewModel() {

    private val visibleMonth = MutableStateFlow(currentMonthStart())
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()
    private var cachedSession: SessionContext? = null
    private var cachedPeriodLogDates: Set<LocalDate> = emptySet()
    private var cachedCycles: List<CycleData> = emptyList()

    init {
        viewModelScope.launch {
            combine(
                sessionManager.session,
                visibleMonth,
            ) { session, month ->
                session to month
            }.collectLatest { (session, month) ->
                loadMonth(session, month)
            }
        }
    }

    fun showPreviousMonth() {
        visibleMonth.update {
            val previous = it.minus(1, DateTimeUnit.MONTH)
            LocalDate(previous.year, previous.month, 1)
        }
        hapticManager.selection()
    }

    fun showNextMonth() {
        visibleMonth.update {
            val next = it.plus(1, DateTimeUnit.MONTH)
            LocalDate(next.year, next.month, 1)
        }
        hapticManager.selection()
    }

    fun showMonth(month: LocalDate) {
        visibleMonth.value = LocalDate(month.year, month.month, 1)
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { current -> current.copy(selectedDate = date) }
    }

    fun jumpToMonth(month: LocalDate) {
        val today = DateConverter.today()
        val monthStart = LocalDate(month.year, month.month, 1)
        val nextSelectedDate = if (month.year == today.year && month.month == today.month) {
            today
        } else {
            monthStart
        }
        visibleMonth.value = monthStart
        _uiState.update { current -> current.copy(selectedDate = nextSelectedDate) }
        hapticManager.selection()
    }

    /**
     * Re-reads logs and cycles for the visible month.
     *
     * The grid is driven by a `combine(session, visibleMonth)` flow, so it only reloads
     * when one of those changes -- neither of which a log does. Logging a period
     * therefore updated Home immediately but left the calendar showing the old marks
     * until the app was restarted. Callers invoke this after a save or delete.
     */
    fun refreshAfterLogChange() {
        val session = sessionManager.current ?: return
        viewModelScope.launch { loadMonth(session, visibleMonth.value) }
    }

    fun jumpToToday() {
        val today = DateConverter.today()
        visibleMonth.value = LocalDate(today.year, today.month, 1)
        _uiState.update { current -> current.copy(selectedDate = today) }
        hapticManager.selection()
    }

    fun ensureYearLoaded(year: Int) {
        val session = cachedSession
        val nextCache = if (session == null) {
            buildMonthCache(
                months = yearMonthStarts(year),
                marksByMonth = emptyMap(),
            )
        } else {
            buildMonthCache(
                months = yearMonthStarts(year),
                marksByMonth = buildMarksByMonth(
                    months = yearMonthStarts(year),
                    cycles = cachedCycles,
                    session = session,
                ),
            )
        }

        _uiState.update { current ->
            current.copy(monthCache = current.monthCache + nextCache)
        }
    }

    private suspend fun loadMonth(
        session: SessionContext?,
        month: LocalDate,
    ) {
        val selectedDate = _uiState.value.selectedDate
        cachedSession = session
        cachedCycles = emptyList()
        if (session == null) {
            val monthCache = buildMonthCache(
                months = preloadMonthsFor(month),
                marksByMonth = emptyMap(),
            )
            _uiState.value = CalendarUiState(
                visibleMonth = month,
                selectedDate = selectedDate,
                days = monthCache[month],
                monthCache = monthCache,
            )
            return
        }

        val initialCache = buildMonthCache(
            months = preloadMonthsFor(month),
            marksByMonth = emptyMap(),
        )
        _uiState.value = CalendarUiState(
            visibleMonth = month,
            selectedDate = selectedDate,
            days = initialCache[month],
            monthCache = initialCache,
            isLoading = true,
            error = null,
            hasAnyCalendarAccess = hasCalendarAccess(session),
        )

        if (!hasCalendarAccess(session)) {
            _uiState.value = CalendarUiState(
                visibleMonth = month,
                selectedDate = selectedDate,
                days = initialCache[month],
                monthCache = initialCache,
                isLoading = false,
                error = null,
                hasAnyCalendarAccess = false,
            )
            return
        }

        val requestedTargetUserId = session.targetUserId
        // Cleared FIRST. Marks are now built from logged days, so leaving the previous
        // target's logs in place would render that person's period days while the new
        // target is still loading -- a real cross-account leak, caught by
        // `ensureYearLoaded does not reuse the previous targets cached cycles`.
        cachedPeriodLogDates = emptySet()
        val loadedLogDates = runCatching {
            periodLogRepository.getAll(requestedTargetUserId).getOrDefault(emptyList())
        }.getOrDefault(emptyList())
            .filter { it.periodPresent }
            .mapTo(mutableSetOf()) { it.logDate }
        cycleDataRepository.getAll(requestedTargetUserId)
            .onSuccess { cycles ->
                if (sessionManager.current?.targetUserId != requestedTargetUserId) return
                cachedSession = session
                cachedCycles = cycles
                // Only adopt the logs once this target is confirmed still current.
                cachedPeriodLogDates = loadedLogDates
                val monthsToLoad = preloadMonthsFor(month)
                val monthCache = buildMonthCache(
                    months = monthsToLoad,
                    marksByMonth = buildMarksByMonth(
                        months = monthsToLoad,
                        cycles = cycles,
                        session = session,
                    ),
                )

                _uiState.value = CalendarUiState(
                    visibleMonth = month,
                    selectedDate = selectedDate,
                    days = monthCache[month],
                    monthCache = monthCache,
                    isLoading = false,
                    error = null,
                    hasAnyCalendarAccess = hasCalendarAccess(session),
                )
            }
            .onFailure { throwable ->
                if (sessionManager.current?.targetUserId != requestedTargetUserId) return

                val monthCache = buildMonthCache(
                    months = preloadMonthsFor(month),
                    marksByMonth = emptyMap(),
                )
                _uiState.value = CalendarUiState(
                    visibleMonth = month,
                    selectedDate = selectedDate,
                    days = monthCache[month],
                    monthCache = monthCache,
                    isLoading = false,
                    error = throwable.toSafeUserMessage(appContext, R.string.calendar_load_failed),
                    hasAnyCalendarAccess = hasCalendarAccess(session),
                )
            }
    }

    private fun hasCalendarAccess(session: SessionContext): Boolean {
        if (session.isViewingOwnData) return true
        return session.can(Permission.VIEW_PERIOD_DATES) ||
            session.can(Permission.VIEW_CYCLE_HISTORY) ||
            session.can(Permission.VIEW_PREDICTIONS)
    }

    private fun filterMarkForSession(
        mark: CalendarMarker.DayMark,
        session: SessionContext,
    ): CalendarMarker.DayMark? {
        if (session.isViewingOwnData) return mark

        val canViewPeriodDates = session.can(Permission.VIEW_PERIOD_DATES)
        val canViewCycleHistory = session.can(Permission.VIEW_CYCLE_HISTORY)
        val canViewPredictions = session.can(Permission.VIEW_PREDICTIONS)

        if (!canViewPeriodDates && !canViewCycleHistory && !canViewPredictions) return null

        val phase = when {
            mark.phase == CyclePhase.MENSTRUAL && !canViewPeriodDates -> CyclePhase.UNKNOWN
            canViewPredictions || canViewCycleHistory -> mark.phase
            else -> CyclePhase.UNKNOWN
        }

        val filtered = mark.copy(
            isPeriod = canViewPeriodDates && mark.isPeriod,
            isFertile = canViewPredictions && mark.isFertile,
            isOvulation = canViewPredictions && mark.isOvulation,
            isPms = canViewPredictions && mark.isPms,
            isPredictedPeriod = canViewPredictions && mark.isPredictedPeriod,
            phase = phase,
            cycleDay = if (canViewCycleHistory || canViewPredictions) mark.cycleDay else 0,
        )

        val hasVisibleState = filtered.isPeriod ||
            filtered.isFertile ||
            filtered.isOvulation ||
            filtered.isPms ||
            filtered.isPredictedPeriod ||
            filtered.phase != CyclePhase.UNKNOWN

        return if (hasVisibleState) filtered else null
    }

    private fun buildMonthCells(
        visibleMonth: LocalDate,
        marks: Map<LocalDate, CalendarMarker.DayMark> = emptyMap(),
    ): List<CalendarDayUiState> {
        val start = monthGridStart(visibleMonth)
        return List(GRID_CELL_COUNT) { index ->
            val date = DateConverter.addDays(start, index)
            CalendarDayUiState(
                date = date,
                isInVisibleMonth = date.month == visibleMonth.month && date.year == visibleMonth.year,
                mark = marks[date],
            )
        }
    }

    private fun monthGridStart(month: LocalDate): LocalDate {
        val offset = month.dayOfWeek.isoDayNumber - 1
        return DateConverter.subtractDays(month, offset)
    }

    private fun monthGridEnd(month: LocalDate): LocalDate {
        return DateConverter.addDays(monthGridStart(month), GRID_CELL_COUNT - 1)
    }

    private fun preloadMonthsFor(month: LocalDate): List<LocalDate> {
        val currentYearMonths = yearMonthStarts(month.year)
        val previousMonth = monthStart(month.minus(1, DateTimeUnit.MONTH))
        val nextMonth = monthStart(month.plus(1, DateTimeUnit.MONTH))

        return linkedSetOf(previousMonth, *currentYearMonths.toTypedArray(), nextMonth).toList()
    }

    private fun buildMarksByMonth(
        months: List<LocalDate>,
        cycles: List<CycleData>,
        session: SessionContext,
    ): Map<LocalDate, Map<LocalDate, CalendarMarker.DayMark>> {
        return months.associateWith { month ->
            // Engine-built, matching iOS's `PeriodManager` -> `buildCalendarState`.
            // `CalendarMarker.buildMarks(cycles)` cannot project predicted/fertile/
            // ovulation/PMS days for the cycle you are currently in, because its
            // cycleLength stays null until the cycle closes.
            val rawMarks = CycleInsightAdapter.calendarMarks(
                from = monthGridStart(month),
                to = monthGridEnd(month),
                periodLogDates = cachedPeriodLogDates,
                today = DateConverter.today(),
            )
            rawMarks.mapValues { (_, mark) ->
                filterMarkForSession(mark, session)
            }.filterValues { it != null }
                .mapValues { (_, mark) -> requireNotNull(mark) }
        }
    }

    private fun buildMonthCache(
        months: List<LocalDate>,
        marksByMonth: Map<LocalDate, Map<LocalDate, CalendarMarker.DayMark>>,
    ): CalendarMonthCache {
        return CalendarMonthCache(
            months = months.associateWith { month ->
                buildMonthCells(
                    visibleMonth = month,
                    marks = marksByMonth[month].orEmpty(),
                )
            },
        )
    }

    private fun yearMonthStarts(year: Int): List<LocalDate> =
        (1..12).map { month -> LocalDate(year, month, 1) }

    companion object {
        private const val GRID_CELL_COUNT = 42
    }
}

private fun monthStart(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)
