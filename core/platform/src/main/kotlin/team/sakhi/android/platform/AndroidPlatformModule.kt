package team.sakhi.android.platform

import org.koin.dsl.module
import team.sakhi.localdb.PlatformRoomDatabaseFactory
import team.sakhi.localdb.SakhiPhaseALocalStore
import team.sakhi.platform.BiometricInterface

/**
 * Android-app-level adapters that live outside SakhiCore (biometric today; Health
 * Connect, FCM, Maps, Play Billing, WorkManager land here in later phases per plan
 * Section 6). Loaded in `Application.onCreate` alongside SakhiCore's
 * `team.sakhi.di.appModule()` / `platformModule()`.
 */
val androidPlatformModule = module {
    single { CurrentActivityHolder() }
    single<BiometricInterface> { AndroidBiometricAdapter(activityHolder = get()) }
    single {
        val factory = PlatformRoomDatabaseFactory().apply { init(get()) }
        SakhiPhaseALocalStore(factory = factory)
    }
    single {
        AndroidNotificationReminderManager(
            appContext = get(),
            sessionManager = get(),
            kvStore = get(),
            deviceRepository = get(),
        )
    }
    single {
        AndroidHealthConnectManager(
            appContext = get(),
            sessionManager = get(),
            kvStore = get(),
            periodLogRepository = get(),
            localStore = get(),
        )
    }
    single { AndroidLocationProvider(context = get()) }
    single { AndroidHapticManager(appContext = get(), kvStore = get()) }
    single { AndroidAppVersionProvider(appContext = get()) }
}
