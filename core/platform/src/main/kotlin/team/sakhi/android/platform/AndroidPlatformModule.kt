package team.sakhi.android.platform

import org.koin.dsl.module
import team.sakhi.localdb.PlatformRoomDatabaseFactory
import team.sakhi.localdb.SakhiPhaseALocalStore
import team.sakhi.notifications.NotificationRepository
import team.sakhi.platform.BiometricInterface
import team.sakhi.sync.DefaultOfflineUpgradeDataSource
import team.sakhi.sync.OfflineUpgradeDataSource
import team.sakhi.sync.OfflineUpgradeMigrator

/**
 * Android-app-level adapters that live outside SakhiCore (biometric today; Health
 * Connect, FCM, Maps, Play Billing, WorkManager land here in later phases per plan
 * Section 6). Loaded in `Application.onCreate` alongside SakhiCore's
 * `team.sakhi.di.appModule()` / `platformModule()`.
 */
val androidPlatformModule = module {
    single { CurrentActivityHolder() }
    // Push trigger for care partners. SakhiCore's own `appModule()` never registered
    // this, which is part of why Android had no way to send one: iOS builds its
    // `notificationRepo` by hand in `SakhiCoreSDK`, so the gap was invisible from there.
    // Registered on the Android side rather than in the shared module so this change
    // needs no SakhiCore rebuild; it belongs in `team.sakhi.di.appModule()` the next time
    // SakhiCore is rebuilt, and the iOS hand-construction can then go with it.
    single { NotificationRepository() }
    single<BiometricInterface> { AndroidBiometricAdapter(activityHolder = get(), appContext = get()) }
    single {
        val factory = PlatformRoomDatabaseFactory().apply { init(get()) }
        SakhiPhaseALocalStore(factory = factory)
    }
    // Offline-to-online upgrade. Registered here rather than in SakhiCore's shared module
    // because it needs the shared Room store, and Android is the platform that has one --
    // a Koin `single` cannot be a nullable type, so a shared registration would have to
    // pretend the store always exists.
    single<OfflineUpgradeDataSource> {
        DefaultOfflineUpgradeDataSource(
            localStore = get(),
            userProfileRepository = get(),
            periodLogRepository = get(),
            cycleDataRepository = get(),
        )
    }
    single { OfflineUpgradeMigrator(get()) }
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
    single { AndroidLocaleManager(languageStore = get()) }
    single { AndroidAppVersionProvider(appContext = get()) }
    single {
        AndroidWidgetSnapshotManager(
            appContext = get(),
            sessionManager = get(),
            cycleDataRepository = get(),
            periodLogRepository = get(),
            kvStore = get(),
        )
    }
}
