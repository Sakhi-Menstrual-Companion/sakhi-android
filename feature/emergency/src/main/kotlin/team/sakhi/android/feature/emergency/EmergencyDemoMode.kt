package team.sakhi.android.feature.emergency

import android.content.Context
import team.sakhi.emergency.EmergencyState
import team.sakhi.models.EmergencyMessage
import team.sakhi.models.EmergencyProfileDetail
import team.sakhi.models.EmergencyRequirement
import team.sakhi.models.EmergencyRequestStatus
import team.sakhi.models.EmergencySession
import team.sakhi.models.LocationData
import team.sakhi.models.NearbySakhi
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * A stand-in Sakhi who is always nearby and always says yes. DEBUG only.
 *
 * Ported from iOS `EmergencyDemoMode.swift`, same data and same delay.
 *
 * Lets the whole request flow be walked on one device. Every screen past "Nearby Sakhis"
 * needs a second real woman, signed in, close enough to be found, who answers while you
 * watch. That cannot be staged on demand, so the accept, session, chat and feedback
 * screens were only ever seen by accident.
 *
 * With this on, one made-up Sakhi is always in the list and accepts [ACCEPT_DELAY_MILLIS]
 * after she is asked.
 *
 * ── What this is not ─────────────────────────────────────────────────────────
 *
 * It is not a test of the real flow. Once the demo Sakhi is asked, the transitions come
 * from the view model's demo branch rather than from `EmergencyStore`, so what you are
 * watching is a mirror of the flow, not the flow. Nothing here proves the server, the
 * RPCs, or the realtime push work — only two devices prove that. Use it to look at
 * screens, not to sign off behaviour.
 *
 * ── Plugging it in and out ───────────────────────────────────────────────────
 *
 * [isEnabled] is the whole switch, persisted so it survives a relaunch. Off, every code
 * path that touches this returns early and the flow is exactly as shipped. There is a
 * toggle on the Emergency sheet in debug builds.
 */
object EmergencyDemoMode {

    private const val PREFS = "sakhi.emergency.demo"
    private const val KEY_ENABLED = "emergency.demoMode.enabled"

    /**
     * Compiled out of release entirely.
     *
     * Guarded on `BuildConfig.DEBUG` as well as the stored flag, so no combination of a
     * stale preference and a release build can put a made-up Sakhi in front of a woman who
     * actually needs help.
     */
    fun isEnabled(context: Context): Boolean =
        BuildConfig.DEBUG &&
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    /** How long she "thinks about it" before accepting. iOS: 7 seconds. */
    const val ACCEPT_DELAY_MILLIS = 7_000L

    const val USER_ID = "demo-sakhi"
    const val NAME = "Demo Sakhi"
    const val REQUEST_ID = "demo-request"

    /** iOS `EmergencyStore.REQUEST_ANSWER_WINDOW_SECONDS`. */
    private const val ANSWER_WINDOW_SECONDS = 120L

    /** Fallback position when there is no fix, matching iOS's `SampleNearbyCoordinate`. */
    private const val FALLBACK_LAT = 28.4595
    private const val FALLBACK_LON = 77.0266

    /**
     * Obviously not real, on purpose. Anyone looking at a screenshot should be able to tell
     * at a glance that this is not a woman who was actually nearby.
     */
    val sakhi: NearbySakhi
        get() = NearbySakhi(
            userId = USER_ID,
            name = NAME,
            photoUrl = null,
            distanceBucketMeters = 200,
            etaMinutes = 3,
            approximateBearingDegrees = 45,
            ratingAverage = 4.9,
            ratingCount = 24,
            lastActiveIso = iso(offsetSeconds = -120),
            alreadyAsked = false,
        )

    val profile: EmergencyProfileDetail
        get() = EmergencyProfileDetail(
            userId = USER_ID,
            name = NAME,
            photoUrl = null,
            helpedCount = 18,
            requestedCount = 4,
            receivedCount = 23,
            ratingAverage = 4.9,
            ratingCount = 24,
            lastActiveIso = iso(offsetSeconds = -120),
            isBlocked = false,
        )

    /** The state she lands on the moment the demo Sakhi is asked. */
    fun waiting(
        requirement: EmergencyRequirement,
        spotLabel: String?,
    ): EmergencyState.WaitingForAcceptance = EmergencyState.WaitingForAcceptance(
        requestId = REQUEST_ID,
        requirement = requirement,
        spotLabel = spotLabel,
        helperId = USER_ID,
        helperName = NAME,
        helperPhotoUrl = null,
        // The countdown on that screen reads this, so it has to be the real window.
        expiresAtIso = iso(offsetSeconds = ANSWER_WINDOW_SECONDS),
    )

    /** The accepted session, seven seconds later. */
    fun session(
        requirement: EmergencyRequirement,
        spotLabel: String?,
        latitude: Double?,
        longitude: Double?,
        status: EmergencyRequestStatus = EmergencyRequestStatus.ACCEPTED,
    ): EmergencySession {
        // Her own position when there is one, so the map and the distance are consistent
        // with what she is looking at rather than a spot in another city.
        val lat = latitude ?: FALLBACK_LAT
        val lon = longitude ?: FALLBACK_LON

        return EmergencySession(
            requestId = REQUEST_ID,
            requirement = requirement,
            spotLabel = spotLabel,
            status = status,
            requesterId = "demo-requester",
            requesterName = "You",
            requesterPhotoUrl = null,
            responderId = USER_ID,
            responderName = NAME,
            responderPhotoUrl = null,
            location = LocationData(latitude = lat, longitude = lon, address = null),
            // A couple of hundred metres north-east, so the two pins are distinguishable.
            responderLocation = LocationData(
                latitude = lat + 0.0018,
                longitude = lon + 0.0018,
                address = null,
            ),
            distanceMeters = 200,
            etaMinutes = 3,
            viewerIsRequester = true,
            acceptedAtIso = iso(offsetSeconds = 0),
            completedAtIso = if (status == EmergencyRequestStatus.COMPLETED) iso(offsetSeconds = 0) else null,
        )
    }

    fun message(body: String, fromHelper: Boolean): EmergencyMessage = EmergencyMessage(
        id = UUID.randomUUID().toString(),
        requestId = REQUEST_ID,
        senderId = if (fromHelper) USER_ID else "demo-requester",
        body = body,
        createdAtIso = iso(offsetSeconds = 0),
    )

    /** What she says once she has accepted, so the chat is not empty on arrival. */
    const val OPENING_MESSAGE = "On my way, two minutes."

    private fun iso(offsetSeconds: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(offsetSeconds))
}
