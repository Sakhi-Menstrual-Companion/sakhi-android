package team.sakhi.android.common

import com.getswipe.sakhi.prediction.SakhiPredictionEngine
import com.getswipe.sakhi.prediction.model.PeriodLogEntry
import kotlinx.datetime.LocalDate
import team.sakhi.config.AppConfig
import team.sakhi.cycle.CalendarMarker
import team.sakhi.cycle.CalendarWindows
import team.sakhi.cycle.CycleGeometry
import team.sakhi.cycle.CycleLengthPreference
import team.sakhi.cycle.CyclePhaseInsight
import team.sakhi.models.CycleData
import team.sakhi.models.CyclePhase
import team.sakhi.models.CycleStatistics

/**
 * Android adapter over the shared KMM [CyclePhaseInsight] engine — the direct
 * counterpart of iOS's `SakhiCycleInsightEngine.swift`, whose header states the
 * architecture both platforms follow:
 *
 * > All phase and next-period decision logic lives in KMM (single source of truth,
 * > shared with Android); this file only picks which cycle to interpret and
 * > normalizes lengths, and maps the KMM structured result to presentation.
 *
 * So this file contains no cycle rules. Which phase you are in, which cycle day, and
 * the prediction status are all decided by [CyclePhaseInsight]. What lives here is
 * only the same *input selection and normalisation* iOS does, reproduced exactly so
 * the two platforms cannot answer differently for identical data.
 *
 * Why it had to exist: Android's Home was computing phase and countdown from
 * [team.sakhi.cycle.CycleMath] instead, which is a different algorithm answering a
 * different question. For the same account on 2026-08-01 iOS said "Day 1 of your
 * period / Menstrual Phase" while Android said "Luteal phase / 207 days" — same data,
 * two engines. `CycleMath.daysUntilNextPeriod` also structurally cannot produce a
 * countdown for an in-progress cycle, because it needs a `cycleLength` that only
 * exists once the cycle has closed.
 */
object CycleInsightAdapter {

    /** Phase and next-period prediction for [date], both decided by shared KMM. */
    data class Insight(
        val phase: CyclePhaseInsight.PhaseInsight,
        val prediction: CyclePhaseInsight.PeriodPredictionSnapshot,
        /**
         * One-line phase tip for the hero pill, from the shared engine's own
         * `analyzePhase(...).shortTip` — the same source iOS's `heroTip` uses, so the
         * copy cannot drift between platforms.
         */
        val shortTip: String?,
    )

    /**
     * @param cycles newest first, matching `CycleDataRepository.getAll`'s
     *   `cycle_start_date DESCENDING` and iOS's use of `currentCycles.first`.
     * @param periodLogDates every date the user logged period flow on.
     * @param stats learned averages, used only as the fallback when a cycle carries no
     *   length of its own — the same role iOS's `periodStore.getAvg…Length()` plays.
     * @param userId whose numbers to resolve. A care partner viewing her calendar passes
     *   *her* id, so his own edited cycle length can never bleed into her windows.
     */
    fun insightFor(
        date: LocalDate,
        cycles: List<CycleData>,
        periodLogDates: Set<LocalDate>,
        stats: CycleStatistics?,
        userId: String?,
    ): Insight {
        val engineEntries = periodLogDates.map { PeriodLogEntry(it.toEpochDays().toLong(), isPresent = true) }
        val todayEpochDay = date.toEpochDays().toLong()
        val logEpochDays = periodLogDates.mapTo(mutableSetOf()) { it.toEpochDays().toLong() }

        // Phase geometry is the engine's, not ours.
        //
        // This used to normalise its own cycle length off one stored cycle while the
        // calendar's fertile days came from `buildCalendarState`, which uses the engine's
        // averages. Since ovulation sits at `cycleLength - 14`, one day of disagreement
        // moved the whole window: Home said ovulation on days the calendar left plain.
        // iOS had the identical bug and was fixed the same way.
        val geometry = phaseGeometry(engineEntries, todayEpochDay, userId)
        val cycleForPhase = cycles.firstOrNull { it.isTrustworthyCompleteCycle() } ?: cycles.firstOrNull()

        // The cycle [date] actually sits in. `cycles` is newest-first, so the first row
        // starting on or before the date is its own cycle. Passing the newest row instead
        // made a tapped past date answer with the *current* cycle's next period.
        val cycleForDate = cycles.firstOrNull { it.cycleStartDate <= date } ?: cycles.firstOrNull()
        val resolvedForPrediction = resolveLengths(
            userId,
            normalizedCycleLength(cycleForDate?.cycleLength, stats),
            normalizedPeriodLength(null, stats),
        )
        val phase = CyclePhaseInsight.phaseInsight(
            periodLogEpochDays = logEpochDays,
            cycleForPhaseStartEpochDay = geometry?.cycleStartEpochDay
                ?: cycleForPhase?.cycleStartDate?.toEpochDays()?.toLong(),
            cycleLength = geometry?.cycleLength ?: normalizedCycleLength(cycleForPhase?.cycleLength, stats),
            effectivePeriodLength = geometry?.periodLength ?: effectivePeriodLength(cycleForPhase, stats),
            avgPeriodLengthFloor = geometry?.periodLength ?: normalizedPeriodLength(null, stats),
            todayEpochDay = todayEpochDay,
        )

        // Prediction: iOS passes the most recent cycle here, not the trustworthy one,
        // and normalises period length from the learned average rather than the cycle.
        val prediction = CyclePhaseInsight.predictionSnapshot(
            periodLogEpochDays = logEpochDays,
            firstCycleStartEpochDay = cycleForDate?.cycleStartDate?.toEpochDays()?.toLong(),
            cyclesEmpty = cycles.isEmpty(),
            anyCycleComplete = cycles.any { it.isComplete },
            cycleLength = resolvedForPrediction.cycleLength,
            periodLength = resolvedForPrediction.periodLength,
            todayEpochDay = todayEpochDay,
        )

        val tip = if (engineEntries.isEmpty()) {
            null
        } else {
            SakhiPredictionEngine.analyzePhase(engineEntries, todayEpochDay).shortTip.takeIf { it.isNotBlank() }
        }

        return Insight(phase = phase, prediction = prediction, shortTip = tip)
    }


    /**
     * Calendar day marks built from the shared engine's `buildCalendarState`, which is
     * the same call iOS's `PeriodManager` makes (`PeriodManager.swift:142`).
     *
     * Android previously derived marks from `CalendarMarker.buildMarks(cycles)`, i.e.
     * from `CycleData` alone. That cannot project anything for the cycle you are
     * currently in, because its `cycleLength` is null until the cycle closes — so
     * predicted-period, fertile, ovulation and PMS days simply never rendered. The
     * engine works from the logged days directly and has no such gap.
     */
    fun calendarMarks(
        from: LocalDate,
        to: LocalDate,
        periodLogDates: Set<LocalDate>,
        today: LocalDate,
        userId: String?,
    ): Map<LocalDate, CalendarMarker.DayMark> {
        if (periodLogDates.isEmpty()) return emptyMap()
        val entries = periodLogDates.map { PeriodLogEntry(it.toEpochDays().toLong(), isPresent = true) }
        val state = SakhiPredictionEngine.buildCalendarState(
            logs = entries,
            todayEpochDay = today.toEpochDays().toLong(),
        )
        // `CalendarState` has no per-day cycle number, so take the cycle boundaries from
        // the same engine and count within them. Still no local cycle maths: the
        // boundaries are the detector's.
        val detected = SakhiPredictionEngine.detectCycles(entries)
        val markGeometry = phaseGeometry(entries, today.toEpochDays().toLong(), userId)
        // `CalendarState` predicts only the next period. iOS's calendar shows every period the
        // engine forecasts over nine months (`MultiPeriodPredictor.forecastMonths = 9`), so
        // the rest come from the same engine's forecast; Android's calendar went blank after
        // next month (Karan, 2026-09-13).
        val todayEpoch = today.toEpochDays().toLong()
        // A predicted period that has already passed without a log is exactly what she
        // wants to see, so the forecast is no longer filtered to today onwards (Karan,
        // 2026-09-17: a prediction stays a prediction whether it is behind her or ahead).
        val forecast = SakhiPredictionEngine
            .forecast(entries, horizonMonths = PredictionHorizonMonths, todayEpochDay = todayEpoch)
        val forecastEpochDays: Set<Long> = forecast.periods
            .flatMapTo(mutableSetOf()) { period -> (period.startEpochDay..period.endEpochDay) }
        // One closed cycle before any fertile or ovulation day is drawn, the rule iOS holds
        // in `PeriodManager.refreshCalendarCache` (`hasCycleData`). Where a fertile window
        // falls depends on the length of a cycle, and before one has closed there is no
        // length, only a default. Android drew them anyway, so the same girl saw a fertile
        // week on one phone and nothing on the other (seen on two devices, 2026-09-17).
        // Saying nothing is the honest answer until her own body has answered once.
        // The forecast above says nothing until she has one complete cycle, so a girl who
        // has logged a single period used to get one predicted window and then blank months.
        // This projects a window per cycle from her own resolved lengths out to the same
        // nine-month horizon, shared with iOS so neither calendar can predict differently.
        val loggedEpochDays: Set<Long> = periodLogDates.mapTo(mutableSetOf()) { it.toEpochDays().toLong() }
        val projectedEpochDays: Set<Long> = CalendarWindows.expectedPeriodWindows(
            mostRecentLoggedStartEpochDay = markGeometry?.cycleStartEpochDay,
            avgCycleLength = markGeometry?.cycleLength ?: 0,
            avgPeriodLength = markGeometry?.periodLength ?: 0,
            loggedPeriodEpochDays = loggedEpochDays,
            todayEpochDay = todayEpoch,
            horizonDays = CalendarWindows.DEFAULT_HORIZON_DAYS,
        )
        // Nothing inside the physiological minimum cycle counts as a prediction. Without
        // this the engine's continuation window for a single-log user lands on days that are
        // not predictions at all. iOS rejects the same days in `isPredictedPeriodDate`.
        val predictedCutoff: Long? = CalendarWindows.predictedEligibilityCutoff(markGeometry?.cycleStartEpochDay)
        val marks = mutableMapOf<LocalDate, CalendarMarker.DayMark>()
        var day = from
        while (day <= to) {
            val epoch = day.toEpochDays().toLong()
            val isPeriod = epoch in state.periodEpochDays
            val isPredicted = !isPeriod &&
                (predictedCutoff == null || epoch >= predictedCutoff) &&
                (epoch in state.predictedPeriodEpochDays ||
                    epoch in forecastEpochDays ||
                    epoch in projectedEpochDays)
            // Projected from the shared geometry rather than read off `CalendarState`.
            // The engine emits ovulation and the fertile window for the **current cycle
            // only**, so every other month came back with nothing to colour -- page back
            // a month and the fertile window simply was not there. `CycleGeometry` answers
            // for any date by projecting a whole cycle at a time, as iOS's calendar does.
            val hasClosedCycle = forecast.completeCycleCount > 0
            val isFertile = hasClosedCycle && (markGeometry?.isFertile(epoch) ?: (epoch in state.fertileWindowEpochDays))
            val isOvulation = hasClosedCycle && (markGeometry?.isOvulation(epoch) ?: (state.ovulationEpochDay == epoch))
            val isPms = epoch in state.pmsWindowEpochDays
            if (isPeriod || isPredicted || isFertile || isOvulation || isPms) {
                marks[day] = CalendarMarker.DayMark(
                    date = day,
                    isPeriod = isPeriod,
                    isFertile = isFertile,
                    isOvulation = isOvulation,
                    isPms = isPms,
                    isPredictedPeriod = isPredicted,
                    // `CalendarState` carries day sets, not a phase enum (iOS colours
                    // its cells straight from those sets). Android's `DayMark.phase`
                    // still feeds `filterMarkForSession`'s permission logic, so derive
                    // it from the sets rather than leaving it UNKNOWN, which would
                    // silently widen what a partner can see.
                    cycleDay = detected
                        .firstOrNull { epoch >= it.cycleStartEpochDay && epoch <= (it.cycleEndEpochDay ?: Long.MAX_VALUE) }
                        ?.let { (epoch - it.cycleStartEpochDay).toInt() + 1 }
                        ?: 0,
                    // A fertile day is OVULATION, not FOLLICULAR. `CycleMath` and
                    // `CyclePhaseInsight` both treat the whole fertile window that way,
                    // and the cell next to it is already drawn in the ovulation colour
                    // off `isFertile`, so calling the same day follicular here was the
                    // one place still disagreeing.
                    //
                    // This does not change what a care partner can see: the visibility
                    // of a fertile day is decided by `isFertile` and VIEW_PREDICTIONS in
                    // `filterMarkForSession`, and both FOLLICULAR and OVULATION are
                    // equally "not UNKNOWN" to its `hasVisibleState` check.
                    phase = when {
                        isPeriod || isPredicted -> CyclePhase.MENSTRUAL
                        isOvulation || isFertile -> CyclePhase.OVULATION
                        isPms -> CyclePhase.LUTEAL
                        else -> CyclePhase.UNKNOWN
                    },
                )
            }
            day = LocalDate.fromEpochDays(day.toEpochDays() + 1)
        }
        return marks
    }

    /** iOS `CycleData.isTrustworthyCompleteCycle`. */
    private fun CycleData.isTrustworthyCompleteCycle(): Boolean {
        val lengthValid = cycleLength?.let {
            it >= AppConfig.CYCLE_MIN_LENGTH_DAYS && it <= AppConfig.CYCLE_MAX_LENGTH_DAYS
        } == true
        val end = cycleEndDate ?: return false
        val endValid = end >= cycleStartDate
        return isComplete && lengthValid && endValid
    }

    /**
     * The cycle geometry the prediction engine itself used -- the same analysis every
     * other window is derived from. Null when there is not enough history to describe a
     * cycle, in which case callers fall back to what they did before.
     */
    private fun phaseGeometry(
        entries: List<PeriodLogEntry>,
        todayEpochDay: Long,
        userId: String?,
    ): CycleGeometry? {
        if (entries.isEmpty()) return null
        val analysis = SakhiPredictionEngine.analyzePhase(entries, todayEpochDay)
        val start = analysis.cycleStartEpochDay ?: return null
        // Her own edited lengths win over the engine's averages, resolved through the one
        // shared store. The engine still owns *where* the cycle starts; only the lengths
        // are hers. iOS resolves identically in `PeriodManager.computePhaseGeometry`.
        //
        // The averages come from `getCycleStats`, which is the call iOS resolves from, and
        // not from `analyzePhase`'s own. The two disagree before her first cycle closes:
        // `analyzePhase` reads the period she is still logging, which is by definition
        // shorter than it will end up, while `getCycleStats` holds a neutral length until
        // a cycle actually closes. Reading different ones is why the same girl's calendar
        // predicted a five day period on iOS and a four day one here (seen on two devices,
        // 2026-09-17).
        val stats = SakhiPredictionEngine.getCycleStats(entries)
        val resolved = resolveLengths(userId, stats.avgCycleLength, stats.avgPeriodLength)
        return CycleGeometry(
            cycleStartEpochDay = start,
            cycleLength = resolved.cycleLength,
            periodLength = resolved.periodLength,
        )
    }

    /**
     * Her edits layered over derived averages. A null [userId] means we have no one to
     * resolve for (nothing is signed in), so the engine's own numbers stand.
     */
    private fun resolveLengths(
        userId: String?,
        derivedCycleLength: Int,
        derivedPeriodLength: Int,
    ): CycleLengthPreference.Resolved =
        CycleLengthPreference.shared.resolve(
            userId = userId ?: "",
            derivedCycleLength = derivedCycleLength,
            derivedPeriodLength = derivedPeriodLength,
        )

    /**
     * iOS `effectivePeriodLength`: an explicitly recorded period start→end wins when it
     * is a believable length, otherwise fall back to the normalised average.
     */
    private fun effectivePeriodLength(cycle: CycleData?, stats: CycleStatistics?): Int {
        if (cycle == null) return normalizedPeriodLength(null, stats)
        val end = cycle.periodEndDate
        if (end != null) {
            val explicit = (end.toEpochDays() - cycle.periodStartDate.toEpochDays()) + 1
            if (explicit > 0 && explicit <= MAX_BELIEVABLE_PERIOD_LENGTH) return explicit
        }
        return normalizedPeriodLength(cycle.periodLength, stats)
    }

    private fun normalizedPeriodLength(raw: Int?, stats: CycleStatistics?): Int {
        val candidate = raw
            ?: stats?.averagePeriodLength?.takeIf { it > 0 }?.toInt()
            ?: AppConfig.DEFAULT_PERIOD_LENGTH
        return if (candidate > 0 && candidate <= MAX_BELIEVABLE_PERIOD_LENGTH) {
            candidate
        } else {
            AppConfig.DEFAULT_PERIOD_LENGTH
        }
    }

    private fun normalizedCycleLength(raw: Int?, stats: CycleStatistics?): Int {
        val candidate = raw
            ?: stats?.averageCycleLength?.takeIf { it > 0 }?.toInt()
            ?: AppConfig.DEFAULT_CYCLE_LENGTH
        return if (candidate >= AppConfig.CYCLE_MIN_LENGTH_DAYS && candidate <= AppConfig.CYCLE_MAX_LENGTH_DAYS) {
            candidate
        } else {
            AppConfig.DEFAULT_CYCLE_LENGTH
        }
    }

    /** iOS's own ceiling on a believable period length before it distrusts the value. */
    private const val MAX_BELIEVABLE_PERIOD_LENGTH = 14
}

/** How far ahead the calendar shows predicted periods, matching iOS. */
private const val PredictionHorizonMonths = 9
