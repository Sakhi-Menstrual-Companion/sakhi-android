package team.sakhi.android.app

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import team.sakhi.deeplink.DeepLinkParser
import team.sakhi.deeplink.SakhiDeepLink

data class PendingDeepLink(
    val id: Long,
    val link: SakhiDeepLink,
    val rawUrl: String,
)

/**
 * Thin Android intake lane for incoming app/deep-link intents. Keeps the latest
 * parsed deep link long enough for the root shell or Home nav graph to consume it.
 */
object AndroidDeepLinkManager {
    private val _pending = MutableStateFlow<PendingDeepLink?>(null)
    val pending: StateFlow<PendingDeepLink?> = _pending.asStateFlow()

    private var nextId: Long = 0L

    fun handleIntent(intent: Intent?) {
        val rawUrl = intent?.dataString?.takeIf(String::isNotBlank) ?: return
        val parsed = DeepLinkParser.parse(rawUrl)
        if (parsed is SakhiDeepLink.Unknown) return

        _pending.value = PendingDeepLink(
            id = nextEventId(),
            link = parsed,
            rawUrl = rawUrl,
        )
    }

    fun consume(id: Long) {
        if (_pending.value?.id == id) {
            _pending.value = null
        }
    }

    @Synchronized
    private fun nextEventId(): Long {
        nextId += 1
        return nextId
    }
}
