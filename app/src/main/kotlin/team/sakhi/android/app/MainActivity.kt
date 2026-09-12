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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
 * Cold start is owned by androidx's SplashScreen compat (see `installSplashScreen()`
 * below and `Theme.Sakhi.Splash` in themes.xml). The note that used to sit here said the
 * API "failed to resolve under every calling convention tried". Why is not recorded in git
 * history. As of 2026-09-12 it builds cleanly with `core-splashscreen` declared in :app,
 * see the note beside `coreSplashscreen` in libs.versions.toml.
 */
class MainActivity : FragmentActivity() {
    /**
     * Flipped by the composition itself, once Compose has produced its first frame.
     *
     * This is what the system splash waits on. Left to itself the splash tears down as
     * soon as the activity's window is up, which is BEFORE Compose has drawn anything, so
     * the bare window background showed for a frame or two in between. Holding the splash
     * across that gap is what removes the flicker.
     */
    private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate(), which the API requires: this is what swaps the activity
        // from the splash theme to `postSplashScreenTheme`, and that has to happen before
        // the window is created.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { !contentReady }
        // A stalled composition must never be able to strand the user on a splash that
        // looks frozen. After this the splash leaves regardless, and whatever the app did
        // manage to draw is always better than a dead screen.
        window.decorView.postDelayed({ contentReady = true }, MAX_SPLASH_HOLD_MS)
        // Hand off by fading, not by cutting. The splash background and the window behind
        // Compose are the same colour (themes.xml points both at `sakhi_window_background`),
        // so across this fade the only thing that visibly changes is the icon dissolving
        // into the first frame. That is the whole "one continuous move" the cold start was
        // missing.
        splashScreen.setOnExitAnimationListener { splashProvider ->
            splashProvider.view
                .animate()
                .alpha(0f)
                .setDuration(SPLASH_EXIT_FADE_MS)
                .withEndAction { splashProvider.remove() }
                .start()
        }
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
            // Runs once the first composition has been applied, i.e. immediately before
            // Compose's first draw. That is the earliest honest moment to say the content
            // is ready, and it is what releases the splash above.
            //
            // Deliberately NOT held until the session gate inside RootNavHost resolves:
            // that gate can wait on the network, and a system splash held that long reads
            // as a hang. RootNavHost's own `SakhiLoadingView` is a designed, branded state
            // that already paints the remembered phase colour, so handing off to it is
            // continuous rather than a third cut.
            LaunchedEffect(Unit) { contentReady = true }
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

/**
 * How long the splash fades out over. Short enough that it never reads as a delay, long
 * enough that the icon dissolves rather than blinks.
 */
private const val SPLASH_EXIT_FADE_MS = 220L

/**
 * The hard ceiling on holding the splash, as a safety net only. Normal cold starts release
 * it on the first composition, far inside this. It exists so that a composition which never
 * completes cannot leave the user looking at a splash that appears frozen.
 */
private const val MAX_SPLASH_HOLD_MS = 2_000L
