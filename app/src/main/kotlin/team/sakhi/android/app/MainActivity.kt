package team.sakhi.android.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.mp.KoinPlatform
import org.koin.compose.koinInject
import team.sakhi.android.platform.AndroidWidgetSnapshotManager
import team.sakhi.android.R
import team.sakhi.android.designsystem.SakhiTheme
import team.sakhi.android.ui.ToastManager
import team.sakhi.android.ui.ToastType
import team.sakhi.platform.PlatformKeyValueStore
import team.sakhi.preferences.ThemeMode
import team.sakhi.preferences.ThemePreferenceStore
import team.sakhi.preferences.UserPreferenceDefaults
import team.sakhi.preferences.UserPreferenceKeys

/**
 * `FragmentActivity`, not plain `ComponentActivity` — `AndroidBiometricAdapter`
 * needs a `FragmentActivity` to host `BiometricPrompt` (see :core:platform).
 *
 * No `installSplashScreen()` call yet: `core-splashscreen` 1.0.1's `SplashScreen`
 * API failed to resolve under every calling convention tried (top-level extension,
 * static call, explicit `Companion` call — all "unresolved reference", verified by
 * real build failures), and the dependency isn't worth chasing further right now.
 * The system default cold-start treatment is fine for this foundation milestone;
 * wiring a real themed splash is a tracked follow-up for the design-system pass
 * (plan Section 4/8), not a Phase B blocker.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // `enableEdgeToEdge()` with no arguments makes the status bar fully transparent, but
        // for the navigation bar it applies AndroidX's own safety scrim -- ~90% opaque white
        // in light mode, ~50% black in dark -- specifically for 3-button nav, to keep the
        // system-drawn back/home/recents glyphs legible against arbitrary app content. On a
        // 3-button-nav device (this app's whole screen palette is saturated per-phase colour,
        // not arbitrary content) that scrim reads as a hard white bar cutting off the bottom
        // of every screen, which iOS has no equivalent of: its home indicator is a thin
        // translucent pill with no backing bar at all. Passing fully transparent styles for
        // both bars removes the scrim; `isAppearanceLightNavigationBars` below keeps the
        // system glyphs themselves legible by following the app's actual (user-selectable)
        // theme rather than the OS default.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // `enableEdgeToEdge` alone was NOT enough on a real MIUI/Android 13 device: the nav
        // bar strip still measured a solid #FEFEFE in a screenshot pixel sample. Two things
        // re-introduce it and both have to be turned off explicitly rather than inferred:
        //   - `isNavigationBarContrastEnforced` (API 29+) asks the system to paint its own
        //     translucent scrim behind the nav bar whenever it thinks app content might not
        //     contrast with the buttons. It defaults to true.
        //   - OEM skins (MIUI here) re-apply an opaque `navigationBarColor` of their own on
        //     top of what AndroidX sets.
        // Setting both directly, after `enableEdgeToEdge`, is what actually makes the bar
        // transparent so the app's own background shows through to the screen edge.
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        handleDeepLinkIntent(intent)

        setContent {
            val themeStore = koinInject<ThemePreferenceStore>()
            val mode by themeStore.mode.collectAsStateWithLifecycle()
            val darkTheme = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Keeps the OS-drawn nav/status bar icons dark-on-light or light-on-dark to match
            // *this app's* theme choice, not `isSystemInDarkTheme()` -- the two can disagree
            // when the user has picked Light or Dark explicitly rather than System.
            DisposableEffect(darkTheme) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
                onDispose {}
            }
            SakhiTheme(darkTheme = darkTheme) {
                RootNavHost()
            }
        }
    }

    // Deliberately untyped at the MainActivity level -- see `ScreenshotWarningController`
    // below for why. Holds one once `onStart` creates it on API 34+; null otherwise.
    private var screenshotWarningController: ScreenshotWarningController? = null

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val controller = ScreenshotWarningController(this)
            screenshotWarningController = controller
            controller.register()
        }
    }

    override fun onStop() {
        screenshotWarningController?.unregister()
        screenshotWarningController = null
        super.onStop()
    }

    // Cold start: `onCreate`'s `intent` above. Already running (tapped a link
    // while the app is alive): this. Both must handle it, or a deep link tapped
    // from a backgrounded app silently does nothing.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val rawUrl = intent?.dataString.orEmpty()
        if (rawUrl == AndroidWidgetSnapshotManager.WIDGET_LOG_TODAY_URL) {
            KoinPlatform.getKoin().get<AndroidWidgetSnapshotManager>().handleWidgetLogTodayDeepLink()
            return
        }
        AndroidDeepLinkManager.handleIntent(intent)
    }
}

/**
 * Ports iOS's screenshot warning (`handleScreenshotTaken` in `SakhiAppShellSnapshot`), which
 * the Privacy & Security toggle has always claimed to control on Android while nothing
 * implemented it — the preference was written and never read.
 *
 * A genuine crash, found on a real API 33 device, not a parity nitpick: this callback used
 * to be a field directly on `MainActivity` typed `Activity.ScreenCaptureCallback`
 * (`@RequiresApi`-annotated) with the *registration calls* guarded by
 * `Build.VERSION.SDK_INT >= UPSIDE_DOWN_CAKE`. That guard is not enough. `@RequiresApi` is
 * lint metadata only — it does not stop the ART verifier from resolving a field's *type* when
 * `MainActivity` itself loads, on every device, regardless of any runtime check. On API < 34
 * that type does not exist, so the whole app failed with
 * `NoClassDefFoundError: Landroid/app/Activity$ScreenCaptureCallback` on every single launch,
 * before `onCreate` ever ran — verified from a live crash on a real Android 13 phone.
 *
 * The fix is this file's actual point: put the API-34 type inside its **own class**, and only
 * ever instantiate that class from behind the SDK check (in `MainActivity.onStart`).
 * `MainActivity`'s own field for it is untyped as this class, so `MainActivity`'s verification
 * only needs `ScreenshotWarningController`'s class descriptor to exist, not its members —
 * *this* class's fields aren't resolved until it is actually loaded, which never happens on a
 * pre-34 device because the guarded `ScreenshotWarningController(this)` call never runs.
 *
 * `registerScreenCaptureCallback` is API 34+ full stop; the only pre-34 way to notice a
 * screenshot is observing MediaStore, which needs `READ_MEDIA_IMAGES` — handing a period
 * tracker read access to the user's entire photo library to deliver a warning is a far worse
 * privacy trade than not warning, so this stays deliberately 34+ only. The Privacy & Security
 * settings row hides itself below 34 rather than promising something the OS cannot deliver.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private class ScreenshotWarningController(private val activity: MainActivity) {

    private val callback = Activity.ScreenCaptureCallback {
        val kvStore = KoinPlatform.getKoin().get<PlatformKeyValueStore>()
        val enabled = kvStore.getBool(
            UserPreferenceKeys.PRIVACY_SCREENSHOT_WARNING,
            UserPreferenceDefaults.PRIVACY_SCREENSHOT_WARNING,
        )
        if (!enabled) return@ScreenCaptureCallback
        ToastManager.show(
            title = activity.getString(R.string.app_screenshot_warning_title),
            message = activity.getString(R.string.app_screenshot_warning_message),
            type = ToastType.WARNING,
            // iOS shows this toast for 7.0s; matched exactly.
            durationMs = 7_000L,
        )
    }

    fun register() {
        activity.registerScreenCaptureCallback(activity.mainExecutor, callback)
    }

    fun unregister() {
        activity.unregisterScreenCaptureCallback(callback)
    }
}
