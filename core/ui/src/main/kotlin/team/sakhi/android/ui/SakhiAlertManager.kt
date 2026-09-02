package team.sakhi.android.ui

import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import team.sakhi.platform.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Port of iOS `AlertModel`. */
data class SakhiAlertModel(
    val title: String,
    val message: String,
    val kind: SakhiAlertKind = SakhiAlertKind.Info,
    val primaryButton: String,
    val primaryAction: (() -> Unit)? = null,
    val secondaryButton: String? = null,
    val secondaryAction: (() -> Unit)? = null,
    /**
     * When true the host keeps this alert in step with live connectivity, so it stops
     * saying "No internet connection" the moment the connection is genuinely back.
     */
    val tracksConnectivity: Boolean = false,
)

/**
 * Port of iOS `AlertManager.shared`: show Sakhi's own alert sheet from anywhere,
 * including a ViewModel, without the calling screen owning a visibility flag.
 *
 * Android already had [SakhiAlertSheet], the 300dp bottom sheet iOS renders, but had no
 * way to raise one from outside a composable -- so failures caught in a ViewModel could
 * only ever become inline red text. A plain singleton object rather than a Koin
 * registration, matching iOS's `static let shared` and Android's own [ToastManager],
 * so every call site reads `SakhiAlertManager.show(...)` the same way.
 */
object SakhiAlertManager {

    private val _alert = MutableStateFlow<SakhiAlertModel?>(null)
    val alert: StateFlow<SakhiAlertModel?> = _alert.asStateFlow()

    fun show(
        title: String,
        message: String,
        kind: SakhiAlertKind = SakhiAlertKind.Info,
        primaryButton: String,
        primaryAction: (() -> Unit)? = null,
        secondaryButton: String? = null,
        secondaryAction: (() -> Unit)? = null,
        tracksConnectivity: Boolean = false,
    ) {
        _alert.value = SakhiAlertModel(
            title = title,
            message = message,
            kind = kind,
            primaryButton = primaryButton,
            primaryAction = primaryAction,
            secondaryButton = secondaryButton,
            secondaryAction = secondaryAction,
            tracksConnectivity = tracksConnectivity,
        )
    }

    /**
     * Clears the alert AND the actions it holds. Actions usually capture a ViewModel, and
     * this object outlives every one of them, so nothing may stay referenced here after
     * the sheet is gone.
     */
    fun dismiss() {
        _alert.value = null
    }

    /**
     * The one no-internet alert for the whole app, so no surface invents its own wording.
     *
     * Copy is taken verbatim from iOS rather than written fresh: the title is what
     * `NetworkOfflineBanner` and `NetworkError.noConnection` both say, and the message is
     * the second half of what `PhoneOTPAuthService.friendlySendOTPMessage` returns for a
     * network failure.
     */
    fun showNoInternet(context: Context, onRetry: (() -> Unit)? = null) {
        show(
            title = context.getString(R.string.no_internet_alert_title),
            message = context.getString(R.string.no_internet_alert_message),
            kind = SakhiAlertKind.Warning,
            // Without a retry to run, "Try again" would be a button that cannot do what it
            // says, so the single-button form acknowledges instead of promising.
            primaryButton = context.getString(
                if (onRetry != null) R.string.no_internet_alert_retry else R.string.sakhi_alert_ok,
            ),
            primaryAction = onRetry,
            secondaryButton = onRetry?.let { context.getString(R.string.no_internet_alert_not_now) },
            tracksConnectivity = true,
        )
    }
}

/**
 * Port of iOS's `.alertManager()` root modifier. Mount exactly once, at the app root
 * (`RootNavHost`), after the app content so the sheet is the last thing composed.
 */
@Composable
fun SakhiAlertHost() {
    val alert by SakhiAlertManager.alert.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime

    // The sheet waits for the keyboard to finish leaving before it slides up. Presenting
    // straight away runs the IME's slide-down and the sheet's slide-up over each other,
    // which is the jump on the phone step: the alert is raised while the number field
    // still holds focus, because the send failed a few hundred ms after she typed.
    var keyboardSettled by remember { mutableStateOf(false) }

    LaunchedEffect(alert) {
        if (alert == null) {
            keyboardSettled = false
            return@LaunchedEffect
        }
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        // Waits on the real inset instead of a fixed delay. IME animation length varies by
        // OEM and is zero when the system's "remove animations" setting is on, so any
        // hardcoded duration is a stutter on one device and dead time on another. When no
        // keyboard is up this resolves on the first emission and costs nothing. The
        // timeout is the safety net: an alert must never be swallowed because an inset
        // never reported back.
        withTimeoutOrNull(KeyboardSettleTimeoutMs) {
            snapshotFlow { imeInsets.getBottom(density) }.first { it == 0 }
        }
        keyboardSettled = true
    }

    val current = alert ?: return
    if (!keyboardSettled) return

    // Starts at false, not true: until the first emission arrives this alert's own subject
    // is "we could not reach the network", so assuming online for a frame would flash the
    // wrong copy at her.
    val isOnline by koinInject<NetworkStatus>().isOnline
        .collectAsStateWithLifecycle(initialValue = false)
    // She should not be left reading "No internet connection" after it has come back, and
    // should not have to guess whether retrying is worth it yet. Deliberately does NOT
    // retry on its own: the retry here sends a real OTP SMS, and that is her decision to
    // make, not something a reconnect should trigger behind her.
    val backOnline = current.tracksConnectivity && isOnline

    // iOS defers the action 0.35s behind the dismissal because presenting a second sheet
    // straight after dismissing one is unreliable in SwiftUI. Not ported: Compose's
    // ModalBottomSheet has no such constraint, and a hardcoded delay would only make the
    // retry feel slow.
    SakhiAlertSheet(
        title = if (backOnline) {
            stringResource(R.string.no_internet_alert_restored_title)
        } else {
            current.title
        },
        message = if (backOnline) {
            stringResource(R.string.no_internet_alert_restored_message)
        } else {
            current.message
        },
        kind = if (backOnline) SakhiAlertKind.Success else current.kind,
        primaryLabel = current.primaryButton,
        onPrimaryClick = {
            SakhiAlertManager.dismiss()
            current.primaryAction?.invoke()
        },
        secondaryLabel = current.secondaryButton,
        onSecondaryClick = current.secondaryButton?.let {
            {
                SakhiAlertManager.dismiss()
                current.secondaryAction?.invoke()
            }
        },
        onDismissRequest = SakhiAlertManager::dismiss,
    )
}

/** Upper bound on how long the sheet will wait for the IME to finish its slide-out. */
private const val KeyboardSettleTimeoutMs = 400L
