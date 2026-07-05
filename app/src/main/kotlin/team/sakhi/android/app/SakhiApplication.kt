package team.sakhi.android.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import team.sakhi.android.BuildConfig
import team.sakhi.android.feature.ai.aiFeatureModule
import team.sakhi.android.feature.auth.authFeatureModule
import team.sakhi.android.feature.calendar.calendarFeatureModule
import team.sakhi.android.feature.care.careFeatureModule
import team.sakhi.android.feature.home.homeFeatureModule
import team.sakhi.android.feature.logging.loggingFeatureModule
import team.sakhi.android.feature.onboarding.onboardingFeatureModule
import team.sakhi.android.feature.profile.profileFeatureModule
import team.sakhi.android.feature.recommendations.recommendationsFeatureModule
import team.sakhi.android.feature.reports.reportsFeatureModule
import team.sakhi.android.platform.AndroidNotificationReminderManager
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

        // CurrentActivityHolder must be attached to the Activity lifecycle explicitly —
        // Koin only constructs it lazily on first `get()`, it doesn't register callbacks.
        registerActivityLifecycleCallbacks(KoinPlatform.getKoin().get<CurrentActivityHolder>())
        KoinPlatform.getKoin().get<AndroidNotificationReminderManager>().start()
    }
}
