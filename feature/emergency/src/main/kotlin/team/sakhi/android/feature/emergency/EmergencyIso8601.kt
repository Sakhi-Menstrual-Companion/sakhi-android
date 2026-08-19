package team.sakhi.android.feature.emergency

import kotlinx.datetime.Instant

/**
 * Parses an ISO-8601 timestamp whether or not it carries fractional seconds. Port of iOS
 * `EmergencyISO8601.swift`.
 *
 * Postgres sends both forms, depending on whether the value happened to land on a whole
 * second. On iOS this was a single formatter with `.withFractionalSeconds` set, named
 * "flexible" but in fact the opposite: that option is a *requirement*, not a permission, so
 * every timestamp without a fractional part failed and the profile read "Unknown" for a
 * Sakhi who was active minutes ago.
 *
 * `kotlinx.datetime.Instant.parse` already accepts both shapes, so the fix here is thinner
 * than on iOS. It exists as its own file anyway for two reasons: it is the one place that
 * decides what a bad timestamp means, and it returns null rather than throwing, so no screen
 * can crash on a value the server phrased differently.
 */
internal object EmergencyIso8601 {

    /** Null when the string is absent or unparseable. Never throws. */
    fun instant(iso: String?): Instant? {
        val raw = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Instant.parse(raw) }.getOrNull()
            // Postgres can also hand back a space instead of the `T`, and a naive timestamp
            // with no zone at all. Treat the latter as UTC, which is what the column is.
            ?: runCatching { Instant.parse(raw.replace(' ', 'T')) }.getOrNull()
            ?: runCatching { Instant.parse(raw.replace(' ', 'T') + "Z") }.getOrNull()
    }

    /**
     * Seconds left until [iso], never negative. Zero when it has passed or cannot be read.
     *
     * Read from the request's own expiry rather than counted from when a screen appeared, so
     * backgrounding the app and coming back does not restart the clock.
     */
    fun secondsUntil(iso: String?, now: Instant): Long {
        val expiry = instant(iso) ?: return 0
        return (expiry.epochSeconds - now.epochSeconds).coerceAtLeast(0)
    }

    /** `m:ss`, as iOS formats the countdown. */
    fun countdown(totalSeconds: Long): String =
        "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
