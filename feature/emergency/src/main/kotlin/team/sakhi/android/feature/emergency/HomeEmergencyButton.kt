package team.sakhi.android.feature.emergency

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import team.sakhi.emergency.EmergencyHomeSignal
import team.sakhi.emergency.EmergencyStore
import team.sakhi.android.platform.DeviceLocation
import team.sakhi.session.SessionManager

/**
 * The Emergency button on Home, with what is live for her drawn on it.
 *
 * Home had no way to know a woman was being asked for help, or that somebody was on their
 * way to her, unless she happened to open Emergency. The push notification covers the app
 * being closed; this covers her sitting on Home with it open and the notification already
 * swiped away. It asks the server on a short interval while Home is on screen, and the
 * button draws the answer: a blinking red ring and a count when someone is waiting on her,
 * a steady green one once the two of them are connected.
 *
 * Tapping it with a request waiting opens Emergency straight onto that request -- the
 * requester's face, what she needs, and Accept / Decline -- rather than onto the
 * requirement picker, which would be the wrong first screen for someone who was asked.
 */
@Composable
fun HomeEmergencyButton(
    count: Int?,
    coordinate: DeviceLocation?,
    /** `true` when she should land on the request waiting for her, not the picker. */
    onOpen: (openInbox: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = koinInject<EmergencyStore>()
    val sessionManager = koinInject<SessionManager>()
    val signal by store.homeSignal.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Only while Home is actually showing. Polling from the background would spend battery
    // and data on a button nobody can see; the push notification is what covers that case.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                sessionManager.current?.userId?.let { userId ->
                    runCatching { store.refreshHomeSignal(userId) }
                }
                delay(HOME_SIGNAL_INTERVAL_MS)
            }
        }
    }

    HomeNearbyCircleButton(
        count = count,
        coordinate = coordinate,
        signal = signal,
        onClick = { onOpen(signal is EmergencyHomeSignal.Incoming) },
        modifier = modifier,
    )
}

/**
 * How often Home re-asks. Short, because a request only stays answerable for two minutes;
 * a woman who glances at Home should see it within a few seconds, not after it has expired.
 */
private const val HOME_SIGNAL_INTERVAL_MS = 6_000L
