package team.sakhi.android.app

import android.app.Application
import android.view.Choreographer
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import team.sakhi.android.BuildConfig
import team.sakhi.android.feature.ai.aiFeatureModule
import team.sakhi.android.feature.auth.authFeatureModule
import team.sakhi.android.feature.calendar.calendarFeatureModule
import team.sakhi.android.feature.care.careFeatureModule
import team.sakhi.android.feature.emergency.emergencyFeatureModule
import team.sakhi.android.feature.home.homeFeatureModule
import team.sakhi.android.feature.logging.loggingFeatureModule
import team.sakhi.android.feature.onboarding.onboardingFeatureModule
import team.sakhi.android.feature.profile.profileFeatureModule
import team.sakhi.android.feature.recommendations.recommendationsFeatureModule
import team.sakhi.android.feature.reports.reportsFeatureModule
import team.sakhi.android.platform.AndroidLocaleManager
import team.sakhi.android.platform.AndroidNotificationReminderManager
import team.sakhi.android.platform.AndroidWidgetSnapshotManager
import team.sakhi.android.platform.CurrentActivityHolder
import team.sakhi.android.platform.androidPlatformModule
import team.sakhi.di.appModule
import team.sakhi.di.platformModule
import team.sakhi.platform.BuildConfigProvider

/**
 * App entry point. Boots the single Koin graph: SakhiCore's shared `appModule()`
 * (repositories, managers, stores — never re-implemented here), SakhiCore's Android
 * `platformModule()` (token/kv storage, network status — needs `androidContext()`,
 * hence started here and not in SakhiCore itself), this app's own
 * `androidPlatformModule` (biometric today), and each feature's Koin module.
 *
 * No business logic lives in this class or anywhere else in :app — this is wiring
 * only, per the thin-shell rule (plan Section 0).
 */
class SakhiApplication : Application() {
    private val deferredStartupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Populate SakhiCore's BuildConfigProvider from this module's generated
        // BuildConfig (itself sourced from secrets.properties / CI env vars, see
        // build.gradle.kts) before Koin constructs anything that reads PlatformConfig
        // (AuthRepository, SakhiSupabaseClient, etc.).
        BuildConfigProvider.SUPABASE_URL = BuildConfig.SUPABASE_URL
        BuildConfigProvider.SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY
        BuildConfigProvider.CLAUDE_API_KEY = BuildConfig.CLAUDE_API_KEY
        BuildConfigProvider.GOOGLE_PLACES_API_KEY = BuildConfig.GOOGLE_PLACES_API_KEY
        BuildConfigProvider.EXOTEL_SID = BuildConfig.EXOTEL_SID
        BuildConfigProvider.EXOTEL_TOKEN = BuildConfig.EXOTEL_TOKEN
        BuildConfigProvider.SANITY_PROJECT_ID = BuildConfig.SANITY_PROJECT_ID
        BuildConfigProvider.SANITY_DATASET = BuildConfig.SANITY_DATASET
        BuildConfigProvider.RAZORPAY_KEY_ID = BuildConfig.RAZORPAY_KEY_ID
        BuildConfigProvider.USDA_API_KEY = BuildConfig.USDA_API_KEY

        warnOnMissingBackendConfig()

        startKoin {
            androidContext(this@SakhiApplication)
            modules(
                appModule(),
                platformModule(),
                androidPlatformModule,
                authFeatureModule,
                onboardingFeatureModule,
                homeFeatureModule,
                profileFeatureModule,
                calendarFeatureModule,
                loggingFeatureModule,
                careFeatureModule,
                emergencyFeatureModule,
                aiFeatureModule,
                reportsFeatureModule,
                recommendationsFeatureModule,
                // Profile/Calendar/Logging/Care/AI/Reports/Recommendations are all
                // reached via in-app navigation from Home (not AppRoute cases), so
                // their modules are registered but not yet mounted anywhere — wire
                // them once Home grows real navigation/tabs.
                // Each remaining :feature:* module's Koin module is added here as it lands.
            )
        }

        val koin = KoinPlatform.getKoin()
        // CurrentActivityHolder must be attached to the Activity lifecycle explicitly —
        // Koin only constructs it lazily on first `get()`, it doesn't register callbacks.
        registerActivityLifecycleCallbacks(koin.get<CurrentActivityHolder>())

        // Locale store reconciliation, reminder scheduling, and widget snapshot
        // observation are all real startup work, but none are required to draw
        // the first signed-out/home frame. Move them off the cold-start critical
        // path so `Application.onCreate()` only does DI/bootstrap wiring.
        Choreographer.getInstance().postFrameCallback {
            koin.get<AndroidLocaleManager>().syncPersistedLanguageWithActiveLocale()
            deferredStartupScope.launch {
                koin.get<AndroidNotificationReminderManager>().start()
                koin.get<AndroidWidgetSnapshotManager>().start()
            }
        }
    }

    /**
     * Names a blank backend config out loud at startup.
     *
     * A missing secret is deliberately a runtime failure here, not a build failure,
     * so a clean checkout still compiles (see `build.gradle.kts`). The problem was
     * that it failed *silently and misleadingly*: with `SUPABASE_URL` empty, Ktor
     * has no host to resolve and every call lands on `localhost:443`, so the real
     * symptom is `Failed to connect to localhost/127.0.0.1:443` on a POST to
     * `/auth/v1/otp` — which reads like the app is deliberately pointed at a local
     * dev server rather than simply unconfigured. That cost real debugging time on
     * 2026-08-01. Keeping the graceful-degradation behaviour, but no longer keeping
     * it quiet.
     */
    private fun warnOnMissingBackendConfig() {
        val missing = buildList {
            if (BuildConfig.SUPABASE_URL.isBlank()) add("SUPABASE_URL")
            if (BuildConfig.SUPABASE_ANON_KEY.isBlank()) add("SUPABASE_ANON_KEY")
        }
        if (missing.isEmpty()) return
        Logger.withTag("SakhiConfig").e {
            "BACKEND NOT CONFIGURED — missing ${missing.joinToString()} in secrets.properties " +
                "(or the same-named CI env vars). Every Supabase call will fail against " +
                "localhost:443 until these are set; that connection error is a symptom of " +
                "this, not a real localhost endpoint. Sign-in, OTP, care, and sync are all " +
                "non-functional in this build."
        }
    }
}
