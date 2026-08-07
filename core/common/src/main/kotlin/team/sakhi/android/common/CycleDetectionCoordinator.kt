package team.sakhi.android.common

// Package is `com.getswipe...` even though the engine's source sits under an `in/`
// directory — a leftover path from the old `in.getswipe` package name. Kotlin allows
// the mismatch, so follow the real `package` declaration, not the folder.
import co.touchlab.kermit.Logger
import com.getswipe.sakhi.prediction.SakhiPredictionEngine
import com.getswipe.sakhi.prediction.model.DetectedCycle
import com.getswipe.sakhi.prediction.model.PeriodLogEntry
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import team.sakhi.models.CycleData
import team.sakhi.models.PeriodLog
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository

/**
 * Turns period logs into `CycleData`, so Home/Calendar/Reports have a cycle to show.
 *
 * Direct port of iOS's `CycleDetectionEngine.processLogChange` (`01-iOS/SakhiApp/
 * Domain/Cycle/CycleDetectionEngine.swift`), step for step. Both platforms are thin
 * orchestrators around the *same* shared detector — iOS's own comment is the rule
 * this respects: "The Swift layer no longer runs its own block detection, the engine
 * is THE detector." No detection maths is re-implemented here; every cycle boundary
 * comes from `SakhiPredictionEngine.detectCycles`, which lives in the shared
 * `PredictionSDK` KMP module and is covered by its own `commonTest` suite.
 *
 * Why this exists at all: iOS has always linked both shared frameworks
 * (`SakhiCore.xcframework` + `PredictionSDK.xcframework`), Android only ever included
 * SakhiCore. So `period_logs` rows were written correctly but nothing ever produced a
 * `CycleData`, and Home sat on "Track your first period" no matter how much the user
 * logged. The only visible reaction was the log button's pencil icon, because that
 * reads log presence rather than the cycle.
 *
 * The two judgement rules below (ID reuse window, max-wins period length) are carried
 * over verbatim from iOS rather than invented here, so the two platforms cannot drift.
 * They are the natural candidates to promote into shared code later; today iOS keeps
 * them platform-side too, so matching that is real parity rather than a shortcut.
 */
/** Cycle detection trace: `adb logcat -s SakhiCycle`. Dates and counts only, no health values. */
private val cycleLog = Logger.withTag("SakhiCycle")

class CycleDetectionCoordinator(
    private val periodLogRepository: PeriodLogRepository,
    private val cycleDataRepository: CycleDataRepository,
) {

    /**
     * Recomputes every cycle for [userId] from that user's full log history.
     *
     * Deliberately recomputes from all logs rather than patching the newest one:
     * editing or deleting an old period legitimately changes later cycle boundaries,
     * and a patch-in-place approach silently leaves those stale (the exact failure
     * iOS's `deleteStaleCycles` step exists to prevent).
     *
     * @return the cycles now persisted, or the underlying failure. Never throws.
     */
    suspend fun processLogChange(userId: String): Result<List<CycleData>> = runCatching {
        val logs = periodLogRepository.getAll(userId).getOrThrow()
        val detected = SakhiPredictionEngine.detectCycles(logs.toEngineEntries())

        val existing = cycleDataRepository.getAll(userId).getOrThrow()
        val updated = buildCycles(detected = detected, userId = userId, existing = existing)

        // Stale cycles must go before the upsert. Without this, removing a period log
        // leaves its old CycleData row behind and the calendar/predictions never clear.
        val keepIds = updated.map { it.id }.toSet()
        existing.filter { it.id !in keepIds }.forEach { stale ->
            cycleDataRepository.delete(stale.id).getOrThrow()
        }

        if (updated.isNotEmpty()) {
            cycleDataRepository.upsertAll(updated).getOrThrow()
        }
        updated
    }

    /** Only `periodPresent` days are cycle evidence; the engine sorts and blocks them itself. */
    private fun List<PeriodLog>.toEngineEntries(): List<PeriodLogEntry> = map { log ->
        PeriodLogEntry(
            epochDay = log.logDate.toEpochDays().toLong(),
            isPresent = log.periodPresent,
        )
    }

    private fun buildCycles(
        detected: List<DetectedCycle>,
        userId: String,
        existing: List<CycleData>,
    ): List<CycleData> {
        val nowIso = Clock.System.now().toString()
        // Each existing row may be claimed by at most one detected cycle, and by the
        // CLOSEST one. iOS's equivalent takes the first match within the window per
        // cycle independently, which lets two nearby detected cycles reuse the same
        // id — that produced a real Postgres failure here on the first live run:
        // "ON CONFLICT DO UPDATE command cannot affect row a second time", because a
        // single upsert carried two rows with the same primary key. Claiming makes
        // the mapping one-to-one, so the ids in a batch are unique by construction.
        val unclaimed = existing.toMutableList()

        return detected.map { cycle ->
        val periodStart = LocalDate.fromEpochDays(cycle.periodStartEpochDay.toInt())

        // Reuse an existing row's id when the period start is within +/-7 days, so a
        // user correcting her start date by a day or two updates the same cycle
        // instead of orphaning the old one and creating a duplicate.
        val match = unclaimed
            .map { candidate ->
                val delta = candidate.periodStartDate.toEpochDays() - periodStart.toEpochDays()
                candidate to if (delta < 0) -delta else delta
            }
            .filter { (_, distance) -> distance <= 7 }
            .minByOrNull { (_, distance) -> distance }
            ?.first
            ?.also { unclaimed.remove(it) }

        // Max-wins: never shrink a period below an already-known length. A later log
        // pass can legitimately see fewer consecutive days (e.g. a mid-period day not
        // yet logged), and letting that shorten a recorded period would lose real data.
        val periodLength = match?.periodLength
            ?.let { known -> maxOf(cycle.periodLength, known) }
            ?: cycle.periodLength

        CycleData(
            id = match?.id ?: UUID.randomUUID().toString(),
            userId = userId,
            cycleStartDate = LocalDate.fromEpochDays(cycle.cycleStartEpochDay.toInt()),
            cycleEndDate = cycle.cycleEndEpochDay?.let { LocalDate.fromEpochDays(it.toInt()) },
            periodStartDate = periodStart,
            periodEndDate = LocalDate.fromEpochDays(cycle.periodEndEpochDay.toInt()),
            cycleLength = cycle.cycleLength,
            periodLength = periodLength,
            isComplete = cycle.isComplete,
            // Preserve the original creation time when updating an existing cycle;
            // stamp a real one when creating. Previously this wrote "" for new cycles,
            // which came back from Postgres as `created_at: null` and then broke the
            // decode of every subsequent cycle read.
            createdAt = match?.createdAt?.takeIf { it.isNotBlank() } ?: nowIso,
            updatedAt = nowIso,
        )
        }
    }
}
