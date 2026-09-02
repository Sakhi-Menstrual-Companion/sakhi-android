package team.sakhi.android.common

import team.sakhi.models.CyclePhase
import team.sakhi.platform.PlatformKeyValueStore

/**
 * The phase Home last displayed, remembered across launches so the app can open in the right
 * colour instead of changing colour while she watches.
 *
 * WHY THIS IS NOT JUST A HOME CONCERN
 *
 * Before Home mounts, two screens paint: the `AppRoute.Splash` route and the session gate's
 * placeholder. Both use `SakhiLoadingView`, which draws its rings in
 * `MaterialTheme.colorScheme.primary` — the brand pink. So every launch went pink, pink, then
 * whatever phase she is actually in. Seeding Home's own state fixed the *values* flashing but
 * could not fix that, because the colour is decided a layer above Home and before it exists.
 *
 * Keeping the phase here, readable synchronously by `:app` and writable by `:feature:home`,
 * lets the loading screens paint the same background Home is about to paint. The colour then
 * never changes at all.
 *
 * KEYED BY USER. One account must never open in another account's phase colour, which on this
 * app leaks something real about the other person.
 *
 * Returns null when unknown (first launch, or signed out), and callers fall back to the brand
 * treatment — which is the correct look for someone with no cycle data yet.
 */
object LastKnownPhaseStore {

    fun save(kvStore: PlatformKeyValueStore, userId: String, phase: CyclePhase) {
        // UNKNOWN carries no colour information; storing it would overwrite a good value with
        // one that resolves back to the default palette.
        if (phase == CyclePhase.UNKNOWN) return
        runCatching { kvStore.set(keyFor(userId), phase.name) }
    }

    /** Never throws: an unreadable or renamed value must degrade to "unknown", not crash. */
    fun restore(kvStore: PlatformKeyValueStore, userId: String): CyclePhase? = runCatching {
        kvStore.get(keyFor(userId))?.let { CyclePhase.valueOf(it) }
    }.getOrNull()

    fun clear(kvStore: PlatformKeyValueStore, userId: String) {
        runCatching { kvStore.remove(keyFor(userId)) }
    }

    /**
     * The phase for whoever was last signed in, resolvable WITHOUT an auth session.
     *
     * The splash screen needs the colour on its very first frame, and at that moment the
     * Supabase session has not been read from storage yet — `AuthRepository.currentUserId` is
     * still null. Keying the lookup on it therefore always missed, fell back to the brand
     * pink, and produced exactly the colour change this store exists to prevent.
     *
     * Recording the user id separately lets the splash resolve the phase immediately. The
     * phase itself stays keyed per user, so this only changes WHEN we can look it up, not who
     * it belongs to.
     */
    fun restoreForLastActiveUser(kvStore: PlatformKeyValueStore): CyclePhase? = runCatching {
        kvStore.get(KEY_LAST_ACTIVE_USER)?.let { restore(kvStore, it) }
    }.getOrNull()

    fun setLastActiveUser(kvStore: PlatformKeyValueStore, userId: String) {
        runCatching { kvStore.set(KEY_LAST_ACTIVE_USER, userId) }
    }

    /**
     * Called on sign-out. Without this the next person to use the device would see the
     * previous user's phase colour on the splash, and on this app a phase colour says
     * something real about her.
     */
    fun clearLastActiveUser(kvStore: PlatformKeyValueStore) {
        runCatching { kvStore.remove(KEY_LAST_ACTIVE_USER) }
    }

    private fun keyFor(userId: String) = "$KEY_PREFIX$userId"

    private const val KEY_PREFIX = "last_known_phase_"
    private const val KEY_LAST_ACTIVE_USER = "last_known_phase_active_user"
}
