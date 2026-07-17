package team.sakhi.android.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import team.sakhi.notifications.NotificationPreferences
import team.sakhi.notifications.NotificationScheduleBuilder
import team.sakhi.notifications.ScheduledNotification
import team.sakhi.platform.PlatformKeyValueStore
import team.sakhi.preferences.UserPreferenceDefaults
import team.sakhi.preferences.UserPreferenceKeys
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.DeviceRepository
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Android-side local reminder coordinator. Mirrors the real iOS split:
 * shared KMM `NotificationScheduleBuilder` owns cycle reminder timing rules,
 * platform code owns OS scheduling and delivery.
 *
 * Important parity constraint:
 * - iOS only has concrete scheduling rules today for KMM builder-backed cycle reminders
 *   (`periodReminder`, `fertileWindow`, `ovulationDay`, `loggingReminder`, with
 *   `daysBefore = 1` hardcoded in `NotificationManager.scheduleCycleReminders`).
 * - Profile-screen toggles like `late period`, `period end`, `medicine`, `care alerts`,
 *   and `partner check-in` currently have settings copy but no source-of-truth scheduling
 *   logic anywhere in iOS or KMM. They remain persisted-only here until product/shared
 *   timing rules exist.
 */
class AndroidNotificationReminderManager(
    private val appContext: Context,
    private val sessionManager: SessionManager,
    private val kvStore: PlatformKeyValueStore,
    private val deviceRepository: DeviceRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workManager = WorkManager.getInstance(appContext)
    private var started = false

    fun start() {
        if (started) return
        started = true
        ensureChannel()
        enqueuePeriodicRefresh()

        scope.launch {
            sessionManager.session.collectLatest { session ->
                if (session == null) {
                    clearSchedulingContext()
                    cancelScheduledReminderWork()
                } else {
                    persistSchedulingContext(session)
                    requestImmediateRefresh()
                    registerPendingFcmTokenIfAny(session.userId)
                }
            }
        }
    }

    /**
     * FCM can hand `SakhiFirebaseMessagingService.onNewToken` a token before
     * anyone is signed in (a fresh install generates a token immediately,
     * well before onboarding/auth finishes) -- iOS's `AppDelegate` handles
     * this exact race by caching `latestAPNsToken` and retrying from its
     * `.userDidSignIn` observer. This is the Android equivalent: the token
     * itself is cached in `kvStore` (not just an in-memory var) because
     * `SakhiFirebaseMessagingService` is OS-managed and not guaranteed to
     * stay alive between `onNewToken` and the user actually finishing
     * sign-in, whereas this manager is a long-lived Koin singleton with its
     * own scope for the whole process lifetime.
     */
    private fun registerPendingFcmTokenIfAny(userId: String) {
        val pendingToken = kvStore.get(PENDING_FCM_TOKEN) ?: return
        scope.launch {
            deviceRepository.registerToken(userId = userId, token = pendingToken, platform = "android")
                .onSuccess { kvStore.remove(PENDING_FCM_TOKEN) }
        }
    }

    fun isBuilderBackedLocalReminder(key: String): Boolean {
        return key in setOf(
            UserPreferenceKeys.NOTIFICATION_PERIOD_REMINDER,
            UserPreferenceKeys.NOTIFICATION_FERTILE_WINDOW,
            UserPreferenceKeys.NOTIFICATION_OVULATION_DAY,
            UserPreferenceKeys.NOTIFICATION_LOGGING_REMINDER,
        )
    }

    fun requestImmediateRefresh() {
        ensureChannel()
        workManager.enqueueUniqueWork(
            REFRESH_NOW_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderRefreshWorker>().build(),
        )
    }

    fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(appContext).areNotificationsEnabled()

    private fun persistSchedulingContext(session: SessionContext) {
        kvStore.set(CONTEXT_TARGET_USER_ID, session.targetUserId)
        kvStore.set(CONTEXT_IS_VIEWING_OWN_DATA, session.isViewingOwnData.toString())
        kvStore.set(CONTEXT_USER_NAME, session.userName)
    }

    private fun clearSchedulingContext() {
        kvStore.remove(CONTEXT_TARGET_USER_ID)
        kvStore.remove(CONTEXT_IS_VIEWING_OWN_DATA)
        kvStore.remove(CONTEXT_USER_NAME)
    }

    private fun enqueuePeriodicRefresh() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReminderRefreshWorker>(12, TimeUnit.HOURS).build(),
        )
    }

    private fun cancelScheduledReminderWork() {
        workManager.cancelAllWorkByTag(DELIVERY_WORK_TAG)
        NotificationManagerCompat.from(appContext).cancelAll()
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.platform_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appContext.getString(R.string.platform_reminder_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "sakhi_reminders"
        private const val PERIODIC_REFRESH_WORK_NAME = "sakhi.notification.refresh.periodic"
        private const val REFRESH_NOW_WORK_NAME = "sakhi.notification.refresh.now"
        private const val DELIVERY_WORK_NAME_PREFIX = "sakhi.notification.delivery."
        private const val DELIVERY_WORK_TAG = "sakhi.notification.delivery"

        private const val CONTEXT_TARGET_USER_ID = "sakhi.notification.context.target_user_id"
        private const val CONTEXT_IS_VIEWING_OWN_DATA = "sakhi.notification.context.is_viewing_own_data"
        private const val CONTEXT_USER_NAME = "sakhi.notification.context.user_name"

        /** Shared with `SakhiFirebaseMessagingService.onNewToken`, which writes this key. */
        internal const val PENDING_FCM_TOKEN = "sakhi.notification.pending_fcm_token"

        private const val INPUT_NOTIFICATION_ID = "notification_id"

        internal fun resolvePreferences(
            kvStore: PlatformKeyValueStore,
            isViewingOwnData: Boolean,
        ): NotificationPreferences {
            return if (isViewingOwnData) {
                NotificationPreferences(
                    periodReminder = kvStore.getBool(
                        UserPreferenceKeys.NOTIFICATION_PERIOD_REMINDER,
                        UserPreferenceDefaults.NOTIFICATION_PERIOD_REMINDER,
                    ),
                    fertileWindow = kvStore.getBool(
                        UserPreferenceKeys.NOTIFICATION_FERTILE_WINDOW,
                        UserPreferenceDefaults.NOTIFICATION_FERTILE_WINDOW,
                    ),
                    ovulationDay = kvStore.getBool(
                        UserPreferenceKeys.NOTIFICATION_OVULATION_DAY,
                        UserPreferenceDefaults.NOTIFICATION_OVULATION_DAY,
                    ),
                    pmsWindow = false,
                    loggingReminder = kvStore.getBool(
                        UserPreferenceKeys.NOTIFICATION_LOGGING_REMINDER,
                        UserPreferenceDefaults.NOTIFICATION_LOGGING_REMINDER,
                    ),
                    // iOS's live `NotificationManager.scheduleCycleReminders` overrides
                    // the shared default and schedules this exactly one day before.
                    daysBefore = 1,
                )
            } else {
                NotificationPreferences(
                    periodReminder = kvStore.getBool(
                        UserPreferenceKeys.NOTIFICATION_PERIOD_REMINDER,
                        UserPreferenceDefaults.NOTIFICATION_PERIOD_REMINDER,
                    ),
                    fertileWindow = false,
                    ovulationDay = false,
                    pmsWindow = false,
                    loggingReminder = false,
                    daysBefore = 1,
                )
            }
        }

        private fun schedulingContext(kvStore: PlatformKeyValueStore): ReminderSchedulingContext? {
            val targetUserId = kvStore.get(CONTEXT_TARGET_USER_ID).orEmpty()
            if (targetUserId.isBlank()) return null

            return ReminderSchedulingContext(
                targetUserId = targetUserId,
                isViewingOwnData = kvStore.get(CONTEXT_IS_VIEWING_OWN_DATA)?.toBooleanStrictOrNull() ?: true,
                userName = kvStore.get(CONTEXT_USER_NAME).orEmpty(),
            )
        }

        private fun deliveryWorkName(notificationId: String): String = DELIVERY_WORK_NAME_PREFIX + notificationId

        private fun deliveryRequest(
            notification: ScheduledNotification,
        ) = OneTimeWorkRequestBuilder<ReminderDeliveryWorker>()
            .setInitialDelay(delayUntilLocalNoon(notification))
            .addTag(DELIVERY_WORK_TAG)
            .setInputData(workDataOf(INPUT_NOTIFICATION_ID to notification.id))
            .build()

        private fun delayUntilLocalNoon(notification: ScheduledNotification): Duration {
            val triggerAt = ZonedDateTime.of(
                LocalDate.of(
                    notification.triggerDate.year,
                    notification.triggerDate.monthNumber,
                    notification.triggerDate.dayOfMonth,
                ),
                LocalTime.NOON,
                ZoneId.systemDefault(),
            ).toInstant()
            val now = Instant.now()
            return if (triggerAt.isAfter(now)) Duration.between(now, triggerAt) else Duration.ZERO
        }

        private fun buildLaunchIntent(context: Context): Intent {
            return context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            } ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
        }

        internal fun postReminderNotification(context: Context, notificationId: String) {
            val manager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if (!manager.areNotificationsEnabled()) return

            val contentIntent = PendingIntent.getActivity(
                context,
                notificationId.hashCode(),
                buildLaunchIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.platform_notification_app_name))
                .setContentText(context.getString(R.string.platform_reminder_open_today))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()

            manager.notify(notificationId.hashCode(), notification)
        }

        private data class ReminderSchedulingContext(
            val targetUserId: String,
            val isViewingOwnData: Boolean,
            val userName: String,
        )
    }

    class ReminderRefreshWorker(
        appContext: Context,
        params: WorkerParameters,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val koin = GlobalContext.get()
            val kvStore = koin.get<PlatformKeyValueStore>()
            val cycleDataRepository = koin.get<CycleDataRepository>()
            val reminderManager = koin.get<AndroidNotificationReminderManager>()
            val workManager = WorkManager.getInstance(applicationContext)

            reminderManager.ensureChannel()
            workManager.cancelAllWorkByTag(DELIVERY_WORK_TAG)

            val context = schedulingContext(kvStore) ?: return Result.success()
            val cycle = cycleDataRepository.getLatest(context.targetUserId).getOrNull() ?: return Result.success()
            val preferences = resolvePreferences(kvStore, context.isViewingOwnData)
            val scheduled = NotificationScheduleBuilder.build(
                cycle = cycle,
                userName = context.userName,
                preferences = preferences,
            )

            scheduled
                .filter {
                    val delay = delayUntilLocalNoon(it)
                    !delay.isZero && !delay.isNegative
                }
                .forEach { notification ->
                    workManager.enqueueUniqueWork(
                        deliveryWorkName(notification.id),
                        ExistingWorkPolicy.REPLACE,
                        deliveryRequest(notification),
                    )
                }

            return Result.success()
        }
    }

    class ReminderDeliveryWorker(
        appContext: Context,
        params: WorkerParameters,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val notificationId = inputData.getString(INPUT_NOTIFICATION_ID) ?: return Result.success()
            val koin = GlobalContext.get()
            koin.get<AndroidNotificationReminderManager>().ensureChannel()
            postReminderNotification(applicationContext, notificationId)
            return Result.success()
        }
    }
}
