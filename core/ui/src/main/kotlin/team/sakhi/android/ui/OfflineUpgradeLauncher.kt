package team.sakhi.android.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lets a screen ask the app root to start the offline-to-online upgrade (phone + OTP), the
 * way [ToastManager] and [SakhiAlertManager] let a screen raise a toast or an alert from
 * outside the composition that owns them.
 *
 * Needed because the upgrade runs the onboarding flow OVER Home. `RootNavHost` already
 * knows how to do that -- it is the same `forcedOnboardingDeepLink` path a deep link uses
 * -- but that path only reacts to links while the app is signed out, and a local-only user
 * is signed in and sitting on Home. Rather than widen the deep-link rules for a
 * user-initiated action, this asks for the flow directly.
 *
 * The migration that makes the upgrade safe is not here: it runs in `AuthViewModel` on the
 * sign-in that creates the account, so every route into OTP is covered, not just this one.
 */
object OfflineUpgradeLauncher {

    private val _requested = MutableStateFlow(false)

    /** True while an upgrade has been asked for and the root has not started it yet. */
    val requested: StateFlow<Boolean> = _requested.asStateFlow()

    fun request() {
        _requested.value = true
    }

    /** Called by the root once the flow is actually on screen. */
    fun consume() {
        _requested.value = false
    }
}
