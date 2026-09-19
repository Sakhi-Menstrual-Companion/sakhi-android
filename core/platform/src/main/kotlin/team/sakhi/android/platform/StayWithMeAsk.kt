package team.sakhi.android.platform

import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import team.sakhi.notifications.NotificationRouting
import team.sakhi.notifications.SakhiNotification
import team.sakhi.staywithme.StayWithMeDestination

/** What her person asked for: to stay with her, and where and for how long when they said. */
data class StayWithMeAskDetails(
    val partnershipId: String,
    val destination: StayWithMeDestination?,
    val minutes: Int?,
)

/**
 * Her person asking to stay with her, from the moment she taps the push or the inbox row to the
 * moment she answers. iOS keeps the same fact on its Stay With Me manager.
 *
 * The shared routing sends this notification to Care, which is not where an ask is answered:
 * she answers it on her Stay With Me screen, so on Android the link carries the ask itself, in
 * its id, and what they asked for is already filled in when she gets there.
 */
object StayWithMeAskLink {
    private const val PREFIX = "ask-"

    /** Where a tap on [notification] goes, with an ask sent to her Stay With Me screen. */
    fun uriFor(notification: SakhiNotification): String? =
        if (notification is SakhiNotification.StayWithMeAsk) {
            "sakhi://care/stay/$PREFIX${encode(notification)}"
        } else {
            NotificationRouting.deepLinkUri(notification)
        }

    fun isAsk(sessionId: String): Boolean = sessionId.startsWith(PREFIX)

    /** What the link says was asked. Null when it is not an ask or cannot be read. */
    fun decode(sessionId: String): StayWithMeAskDetails? {
        if (!isAsk(sessionId)) return null
        return runCatching {
            val bytes = Base64.decode(sessionId.removePrefix(PREFIX), FLAGS)
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            val name = json.optString("n").takeIf { it.isNotBlank() }
            StayWithMeAskDetails(
                partnershipId = json.getString("p"),
                destination = if (name != null && json.has("la") && json.has("lo")) {
                    StayWithMeDestination(name, json.getDouble("la"), json.getDouble("lo"))
                } else {
                    null
                },
                minutes = json.optInt("m", 0).takeIf { it > 0 },
            )
        }.getOrNull()
    }

    private fun encode(ask: SakhiNotification.StayWithMeAsk): String {
        val json = JSONObject().put("p", ask.partnershipId)
        val lat = ask.destinationLatitude
        val lng = ask.destinationLongitude
        if (!ask.destinationName.isNullOrBlank() && lat != null && lng != null) {
            json.put("n", ask.destinationName).put("la", lat).put("lo", lng)
        }
        ask.minutes?.let { json.put("m", it) }
        return Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8), FLAGS)
    }

    private const val FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
}

object StayWithMeAskInbox {
    private val _pending = MutableStateFlow<StayWithMeAskDetails?>(null)

    /** The ask she opened Stay With Me for, until she answers it or leaves. */
    val pending: StateFlow<StayWithMeAskDetails?> = _pending

    fun markAsked(details: StayWithMeAskDetails) { _pending.value = details }

    fun clear() { _pending.value = null }
}
