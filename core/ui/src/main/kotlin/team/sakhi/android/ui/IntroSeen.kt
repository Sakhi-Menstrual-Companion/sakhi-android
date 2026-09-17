package team.sakhi.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import team.sakhi.platform.PlatformKeyValueStore

/**
 * The keys behind "she has seen this feature's intro", one per feature.
 *
 * Same strings iOS stores under, so the two platforms name the same thing the same way.
 * Deliberately per device rather than per account: it is a note about what this person has
 * already been shown, not something about her that belongs in her health record.
 */
object IntroSeenKey {
    /** iOS `UserDefaultsKey.hasSeenSakhiAIIntro`. */
    const val SAKHI_AI = "sakhi_seen_sakhi_ai_intro"

    /** iOS `UserDefaultsKey.hasSeenStayWithMeIntro`. */
    const val STAY_WITH_ME = "sakhi_seen_stay_with_me_intro"
}

/**
 * The Android answer to iOS's `@AppStorage(UserDefaultsKey.hasSeen…)`: [seen] recomposes
 * the screen reading it, and [markSeen] both flips it and writes it to the device, so the
 * intro does not come back the next time she opens the feature.
 */
@Stable
class IntroSeenState internal constructor(
    private val store: PlatformKeyValueStore,
    private val key: String,
) {
    var seen by mutableStateOf(store.getBool(key, default = false))
        private set

    fun markSeen() {
        if (seen) return
        store.setBool(key, true)
        seen = true
    }
}

@Composable
fun rememberIntroSeen(key: String): IntroSeenState {
    val store = koinInject<PlatformKeyValueStore>()
    return remember(key, store) { IntroSeenState(store, key) }
}
