package team.sakhi.android.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
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
                    registerThisPhonesToken(session.userId)
                }
            }
        }
    }

    /**
     * Gives this phone's push token to whoever just signed in, on every sign-in.
     *
     * It used to register a token cached by `onNewToken` and then delete the cache. FCM
     * mints a token once per install and calls `onNewToken` again only if it changes, so
     * the first account to sign in got the token and every account after it on the same
     * phone never did -- while the first kept receiving pushes on a phone that was no
     * longer hers. Now the phone asks Firebase for its current token each time, falls back
     * to the cached one if Firebase cannot answer, and keeps it cached: it is this phone's
     * token, and sign-out needs it too. `register_push_token` (migration 055) moves it to
     * the signed-in account server-side.
     */
    private fun registerThisPhonesToken(userId: String) {
        scope.launch {
            val token = currentFcmToken() ?: kvStore.get(PENDING_FCM_TOKEN) ?: return@launch
            kvStore.set(PENDING_FCM_TOKEN, token)
            deviceRepository.registerToken(userId = userId, token = token, platform = "android")
        }
    }

    /**
     * Firebase's own answer for this phone's token. Null when Firebase is not configured
     * in this build -- `getInstance()` throws without `google-services.json` -- or when it
     * cannot reach Google right now.
     */
    private suspend fun currentFcmToken(): String? = runCatching {
        suspendCancellableCoroutine<String?> { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
        }
    }.getOrNull()

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
        // The signed-in account, not the data being viewed -- these are that account's own
        // reminder choices, so a worker running after she has signed out and someone else
        // has signed in must not read the toggles she left behind.
        kvStore.set(CONTEXT_USER_ID, session.userId)
    }

    private fun clearSchedulingContext() {
        kvStore.remove(CONTEXT_TARGET_USER_ID)
        kvStore.remove(CONTEXT_IS_VIEWING_OWN_DATA)
        kvStore.remove(CONTEXT_USER_NAME)
        kvStore.remove(CONTEXT_USER_ID)
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
        private const val CONTEXT_USER_ID = "sakhi.notification.context.user_id"

        /** Shared with `SakhiFirebaseMessagingService.onNewToken`, which writes this key. */
        internal const val PENDING_FCM_TOKEN = "sakhi.notification.pending_fcm_token"

        private const val INPUT_NOTIFICATION_ID = "notification_id"

        internal fun resolvePreferences(
            kvStore: PlatformKeyValueStore,
            isViewingOwnData: Boolean,
            userId: String? = null,
        ): NotificationPreferences {
            // Scoped on the mode, the same way the Notifications screen writes them. These
            // two branches used to read one shared value, so a care partner's choice about
            // her alerts and her own choice about her own were the same stored bool.
            return if (isViewingOwnData) {
                NotificationPreferences(
                    periodReminder = kvStore.getBool(
                        UserPreferenceKeys.notificationKey(UserPreferenceKeys.NOTIFICATION_PERIOD_REMINDER, isViewingOwnData, userId),
                        UserPreferenceDefaults.NOTIFICATION_PERIOD_REMINDER,
                    ),
                    fertileWindow = kvStore.getBool(
                        UserPreferenceKeys.notificationKey(UserPreferenceKeys.NOTIFICATION_FERTILE_WINDOW, isViewingOwnData, userId),
                        UserPreferenceDefaults.NOTIFICATION_FERTILE_WINDOW,
                    ),
                    ovulationDay = kvStore.getBool(
                        UserPreferenceKeys.notificationKey(UserPreferenceKeys.NOTIFICATION_OVULATION_DAY, isViewingOwnData, userId),
                        UserPreferenceDefaults.NOTIFICATION_OVULATION_DAY,
                    ),
                    pmsWindow = false,
                    loggingReminder = kvStore.getBool(
                        UserPreferenceKeys.notificationKey(UserPreferenceKeys.NOTIFICATION_LOGGING_REMINDER, isViewingOwnData, userId),
                        UserPreferenceDefaults.NOTIFICATION_LOGGING_REMINDER,
                    ),
                    // iOS's live `NotificationManager.scheduleCycleReminders` overrides
                    // the shared default and schedules this exactly one day before.
                    daysBefore = 1,
                )
            } else {
                NotificationPreferences(
                    periodReminder = kvStore.getBool(
                        UserPreferenceKeys.notificationKey(UserPreferenceKeys.NOTIFICATION_PERIOD_REMINDER, isViewingOwnData, userId),
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
                userId = kvStore.get(CONTEXT_USER_ID),
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
                .setSmallIcon(R.drawable.ic_stat_sakhi)
                .setColor(ContextCompat.getColor(context, R.color.platform_notification_accent))
                .setContentTitle(context.getString(R.string.platform_notification_app_name))
                .setContentText(context.getString(R.string.platform_reminder_open_today))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                // The copy is already generic ("Open Sakhi for today's update"), but mark
                // it private too so a locked screen set to hide sensitive content treats
                // it the same way it treats the push lane.
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()

            manager.notify(notificationId.hashCode(), notification)
        }

        private data class ReminderSchedulingContext(
            val targetUserId: String,
            val isViewingOwnData: Boolean,
            val userName: String,
            val userId: String? = null,
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
            val preferences = resolvePreferences(kvStore, context.isViewingOwnData, context.userId)
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
