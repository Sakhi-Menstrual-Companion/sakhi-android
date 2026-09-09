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
import team.sakhi.localdb.SakhiPhaseALocalStore
import team.sakhi.di.appModule
import team.sakhi.di.platformModule
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest
import team.sakhi.platform.BuildConfigProvider
import team.sakhi.platform.NetworkStatus

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

        // A failed DNS lookup is cached by the JVM for `networkaddress.cache.negative.ttl`
        // seconds, and the cache is not cleared when the network changes. That is what makes
        // "the internet is back, but Try again still does nothing" happen: the retry never
        // reaches the resolver, it re-throws the same
        // `Unable to resolve host ... No address associated with hostname` straight out of
        // the cache. Zero means every retry asks the resolver again, which is what a retry
        // button has to do to be honest.
        java.security.Security.setProperty("networkaddress.cache.negative.ttl", "0")

        // Populate SakhiCore's BuildConfigProvider from this module's generated
        // BuildConfig (itself sourced from secrets.properties / CI env vars, see
        // build.gradle.kts) before Koin constructs anything that reads PlatformConfig
        // (AuthRepository, SakhiSupabaseClient, etc.).
        BuildConfigProvider.SUPABASE_URL = BuildConfig.SUPABASE_URL
        BuildConfigProvider.SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY
        BuildConfigProvider.CLAUDE_API_KEY = BuildConfig.CLAUDE_API_KEY
        BuildConfigProvider.GOOGLE_PLACES_API_KEY = BuildConfig.GOOGLE_PLACES_API_KEY
        BuildConfigProvider.GOOGLE_MAPS_API_KEY = BuildConfig.GOOGLE_MAPS_API_KEY
        BuildConfigProvider.EXOTEL_SID = BuildConfig.EXOTEL_SID
        BuildConfigProvider.EXOTEL_TOKEN = BuildConfig.EXOTEL_TOKEN
        BuildConfigProvider.SANITY_PROJECT_ID = BuildConfig.SANITY_PROJECT_ID
        BuildConfigProvider.SANITY_DATASET = BuildConfig.SANITY_DATASET
        BuildConfigProvider.RAZORPAY_KEY_ID = BuildConfig.RAZORPAY_KEY_ID
        BuildConfigProvider.USDA_API_KEY = BuildConfig.USDA_API_KEY

        // Who this app is, for Google's application-restricted Maps keys. The Places web
        // service refuses such a key outright unless the request carries `X-Android-Package`
        // and `X-Android-Cert`, which is why Emergency Assistance's safe-places list was
        // empty on every build while the map beside it drew fine off the same key.
        // Read off the installed package rather than a build constant, so debug and release
        // each send their own signature and neither can go stale.
        BuildConfigProvider.APP_PACKAGE_NAME = packageName
        BuildConfigProvider.APP_SIGNING_SHA1 = signingCertificateSha1().orEmpty()

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
        // Same lazy-construction trap as CurrentActivityHolder below, and worse in effect.
        //
        // `NetworkStatus` defaults to `MutableStateFlow(true)` and only starts telling the
        // truth once `register()` attaches a ConnectivityManager callback. Nothing called
        // it: two places inject the class and read `isOnline`, and no one ever registered
        // it. So the flow was pinned to "online" for the life of the process no matter what
        // the radio was doing -- the offline banner never appeared, the feature gate never
        // engaged, and `setCloudAvailable` was told the cloud was reachable while calls
        // were failing. The file's own comment predicted exactly this: it "degrades to
        // always online, which is indistinguishable from working".
        koin.get<NetworkStatus>().register(applicationContext)

        // CurrentActivityHolder must be attached to the Activity lifecycle explicitly —
        // Koin only constructs it lazily on first `get()`, it doesn't register callbacks.
        registerActivityLifecycleCallbacks(koin.get<CurrentActivityHolder>())

        // Locale store reconciliation, reminder scheduling, and widget snapshot
        // observation are all real startup work, but none are required to draw
        // the first signed-out/home frame. Move them off the cold-start critical
        // path so `Application.onCreate()` only does DI/bootstrap wiring.
        // Remote config. Nothing on Android ever called `refresh()`, so every flag sat on
        // its compiled-in default and the kill switch did nothing here. Started off the
        // critical path: every feature reads defaults until the first fetch lands, so no
        // frame waits on it.
        AndroidRemoteConfigController(
            application = this,
            store = koin.get(),
            sessionManager = koin.get(),
            versionName = BuildConfig.VERSION_NAME,
        ).start()

        // Open the Room database NOW, off the main thread, instead of leaving it to whoever
        // queries it first.
        //
        // Room opens lazily on first access: it opens the file, validates the schema and runs
        // any migration. On a cold start the first caller is Home, so that whole cost landed
        // between the first frame and Home having anything to show — a measured ~3s hole on a
        // real device where the app looked alive but empty, with libsqliteJni only being
        // loaded at that point.
        //
        // Started before the frame callback below, deliberately: this is the one piece of
        // startup work Home genuinely waits on, so it should begin as early as possible rather
        // than be deferred with the things that can wait. It stays OFF the main thread, so it
        // costs the first frame nothing and is simply already done by the time Home asks.
        deferredStartupScope.launch {
            runCatching { koin.get<SakhiPhaseALocalStore>().warmUp() }
        }

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
    /**
     * The SHA-1 of the certificate this APK was signed with, uppercase and colon-free --
     * the form Google's `X-Android-Cert` header wants, and the same string the API console
     * lists under the key's Android restrictions.
     *
     * Failure returns null rather than throwing: the calls that use it already degrade to
     * an empty list, and an app that will not start because it could not read its own
     * signature would be a far worse trade.
     */
    private fun signingCertificateSha1(): String? = runCatching {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val first = signatures?.firstOrNull() ?: return@runCatching null
        MessageDigest.getInstance("SHA-1")
            .digest(first.toByteArray())
            .joinToString("") { byte -> "%02X".format(byte) }
    }.onFailure {
        Log.w("SakhiApplication", "could not read the signing certificate: $it")
    }.getOrNull()

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
