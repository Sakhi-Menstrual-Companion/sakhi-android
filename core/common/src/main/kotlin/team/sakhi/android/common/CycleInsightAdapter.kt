package team.sakhi.android.common

import com.getswipe.sakhi.prediction.SakhiPredictionEngine
import com.getswipe.sakhi.prediction.model.PeriodLogEntry
import kotlinx.datetime.LocalDate
import team.sakhi.config.AppConfig
import team.sakhi.cycle.CalendarMarker
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
     */
    fun insightFor(
        date: LocalDate,
        cycles: List<CycleData>,
        periodLogDates: Set<LocalDate>,
        stats: CycleStatistics?,
    ): Insight {
        val engineEntries = periodLogDates.map { PeriodLogEntry(it.toEpochDays().toLong(), isPresent = true) }
        val todayEpochDay = date.toEpochDays().toLong()
        val logEpochDays = periodLogDates.mapTo(mutableSetOf()) { it.toEpochDays().toLong() }

        // Phase: prefer a cycle we actually trust, else the most recent one. Mirrors
        // iOS's `currentCycles.first(where: \.isTrustworthyCompleteCycle) ?? .first`.
        val cycleForPhase = cycles.firstOrNull { it.isTrustworthyCompleteCycle() } ?: cycles.firstOrNull()
        val phase = CyclePhaseInsight.phaseInsight(
            periodLogEpochDays = logEpochDays,
            cycleForPhaseStartEpochDay = cycleForPhase?.cycleStartDate?.toEpochDays()?.toLong(),
            cycleLength = normalizedCycleLength(cycleForPhase?.cycleLength, stats),
            effectivePeriodLength = effectivePeriodLength(cycleForPhase, stats),
            avgPeriodLengthFloor = normalizedPeriodLength(null, stats),
            todayEpochDay = todayEpochDay,
        )

        // Prediction: iOS passes the most recent cycle here, not the trustworthy one,
        // and normalises period length from the learned average rather than the cycle.
        val prediction = CyclePhaseInsight.predictionSnapshot(
            periodLogEpochDays = logEpochDays,
            firstCycleStartEpochDay = cycles.firstOrNull()?.cycleStartDate?.toEpochDays()?.toLong(),
            cyclesEmpty = cycles.isEmpty(),
            anyCycleComplete = cycles.any { it.isComplete },
            cycleLength = normalizedCycleLength(cycles.firstOrNull()?.cycleLength, stats),
            periodLength = normalizedPeriodLength(null, stats),
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
        val marks = mutableMapOf<LocalDate, CalendarMarker.DayMark>()
        var day = from
        while (day <= to) {
            val epoch = day.toEpochDays().toLong()
            val isPeriod = epoch in state.periodEpochDays
            val isPredicted = epoch in state.predictedPeriodEpochDays
            val isFertile = epoch in state.fertileWindowEpochDays
            val isOvulation = state.ovulationEpochDay == epoch
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
                    phase = when {
                        isPeriod || isPredicted -> CyclePhase.MENSTRUAL
                        isOvulation -> CyclePhase.OVULATION
                        isPms -> CyclePhase.LUTEAL
                        isFertile -> CyclePhase.FOLLICULAR
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
