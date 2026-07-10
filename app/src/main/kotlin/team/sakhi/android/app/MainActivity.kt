package team.sakhi.android.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.mp.KoinPlatform
import org.koin.compose.koinInject
import team.sakhi.android.platform.AndroidWidgetSnapshotManager
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
        enableEdgeToEdge()
        handleDeepLinkIntent(intent)

        setContent {
            val themeStore = koinInject<ThemePreferenceStore>()
            val mode by themeStore.mode.collectAsStateWithLifecycle()
            val darkTheme = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
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
