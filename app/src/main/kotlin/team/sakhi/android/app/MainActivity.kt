package team.sakhi.android.app

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import team.sakhi.preferences.ThemeMode
import team.sakhi.preferences.ThemePreferenceStore

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
