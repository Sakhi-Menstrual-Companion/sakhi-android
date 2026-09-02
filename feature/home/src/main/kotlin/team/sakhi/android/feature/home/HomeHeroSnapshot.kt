package team.sakhi.android.feature.home

import team.sakhi.cycle.CyclePhaseInsight
import team.sakhi.models.CyclePhase
import team.sakhi.platform.PlatformKeyValueStore

/**
 * Remembers what Home last showed, so the next launch opens on it instead of on defaults.
 *
 * THE PROBLEM THIS SOLVES
 *
 * `HomeUiState()` starts at `phase = UNKNOWN` / `hasCycleData = false`, and Compose renders
 * that immediately. The real values arrive a moment later from the local database. For a
 * woman with cycles already logged, every launch therefore drew a wrong screen first:
 * `rememberPhasePalette` maps UNKNOWN to the FOLLICULAR palette, so the background flashed a
 * colour belonging to a phase she was not in, the hero read "start tracking today", and both
 * then snapped to the truth. Nothing was blocking — the app was showing an answer before it
 * had one.
 *
 * Seeding fixes it at the source. `PlatformKeyValueStore` is synchronous, so the last known
 * values are read while the ViewModel is constructed, before the first composition. The first
 * frame is already correct, so there is nothing to snap from.
 *
 * KEYED BY USER, DELIBERATELY. The key includes the user id, so one account can never open on
 * another account's phase. On a health app that is not a cosmetic concern.
 *
 * ENCODING: a pipe-delimited string rather than JSON, on purpose. `org.json` is an Android
 * stub in local JUnit runs and throws "not mocked" from `put`, which is the trap
 * `:feature:reports` already documents. The alternative — switching that module to
 * `isReturnDefaultValues` — would silently turn every JSON call into a no-op and hide real
 * breakage. Five scalar fields do not need a document format.
 */
internal object HomeHeroSnapshot {

    fun save(kvStore: PlatformKeyValueStore, userId: String, state: HomeUiState) {
        // A state that has not loaded yet must never overwrite a good snapshot with blanks.
        if (!state.hasCycleData) return
        val encoded = listOf(
            state.phase.name,
            state.phaseKind.name,
            state.dayInCycle?.toString().orEmpty(),
            state.cycleLength?.toString().orEmpty(),
            state.daysUntilNextPeriod?.toString().orEmpty(),
        ).joinToString(SEPARATOR)
        runCatching { kvStore.set(keyFor(userId), encoded) }
    }

    /**
     * The last known state for this user, or null on a first launch or after sign-out.
     *
     * Never throws: a snapshot written by an older build with different enum names must
     * degrade to "no snapshot" and let Home load normally, not crash the screen.
     */
    fun restore(kvStore: PlatformKeyValueStore, userId: String): HomeUiState? = runCatching {
        val parts = kvStore.get(keyFor(userId))?.split(SEPARATOR) ?: return null
        if (parts.size < FIELD_COUNT) return null
        HomeUiState(
            phase = CyclePhase.valueOf(parts[0]),
            phaseKind = CyclePhaseInsight.PhaseKind.valueOf(parts[1]),
            dayInCycle = parts[2].toIntOrNull(),
            cycleLength = parts[3].toIntOrNull(),
            daysUntilNextPeriod = parts[4].toIntOrNull(),
            hasCycleData = true,
            // The restored values are real, but they are from the previous run and the fresh
            // read is already on its way. Marking it loading is what lets the top bar show the
            // rotating icon over values she can already see, rather than over a blank screen.
            isLoadingCycle = true,
        )
    }.getOrNull()

    fun clear(kvStore: PlatformKeyValueStore, userId: String) {
        runCatching { kvStore.remove(keyFor(userId)) }
    }

    private fun keyFor(userId: String) = "$KEY_PREFIX$userId"

    private const val KEY_PREFIX = "home_hero_snapshot_"
    // Not present in an enum name or a number, so it can never appear inside a field.
    private const val SEPARATOR = "|"
    private const val FIELD_COUNT = 5
}
