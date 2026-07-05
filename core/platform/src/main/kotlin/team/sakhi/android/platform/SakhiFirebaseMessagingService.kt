package team.sakhi.android.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import team.sakhi.notifications.NotificationPayloadParser
import team.sakhi.notifications.SakhiNotification
import team.sakhi.platform.PlatformKeyValueStore
import team.sakhi.repositories.DeviceRepository
import team.sakhi.session.SessionManager

/**
 * FCM transport for Android -- the platform-specific counterpart to iOS's raw
 * APNs registration in `AppDelegate.swift` (iOS keeps Firebase for Crashlytics/
 * Analytics only and pushes over APNs directly; Android has no APNs
 * equivalent, so FCM is the real push transport here). Payload interpretation
 * itself stays shared: both platforms hand the raw string map to KMM's
 * `NotificationPayloadParser`, not a platform-local reimplementation.
 *
 * Not wired to a live Firebase project yet -- `google-services.json` doesn't
 * exist in this repo (see plan Section 7), so `FirebaseApp` has no default
 * config to auto-initialize from and this service won't actually receive a
 * token or a message at runtime until that file is added. The class is real
 * and compiles/packages correctly regardless; only the credential is missing.
 *
 * The OS instantiates this class directly (like `CoroutineWorker`), not Koin,
 * so it reaches the shared graph via `GlobalContext.get()`, the same pattern
 * `AndroidNotificationReminderManager.ReminderRefreshWorker` already uses.
 */
class SakhiFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Cache first, unconditionally -- a fresh install can generate a token
        // before Koin/startup/auth are ready, so returning early before this
        // write would lose the token entirely. Use a context-backed local
        // store here instead of depending on the DI graph just to persist the
        // pending token.
        val kvStore = PlatformKeyValueStore().apply { init(applicationContext) }
        val koin = GlobalContext.getOrNull()
        val sessionManager = runCatching { koin?.get<SessionManager>() }.getOrNull()
        val deviceRepository = runCatching { koin?.get<DeviceRepository>() }.getOrNull()
        val userId = sessionManager?.session?.value?.userId

        scope.launch {
            cacheAndMaybeRegisterToken(
                token = token,
                kvStore = kvStore,
                userId = userId,
            ) { activeUserId, freshToken ->
                deviceRepository?.registerToken(
                    userId = activeUserId,
                    token = freshToken,
                    platform = "android",
                ) ?: Result.failure(IllegalStateException("DeviceRepository unavailable"))
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data.isEmpty()) return

        val notification = NotificationPayloadParser.parse(message.data)
        if (notification == SakhiNotification.Unknown) return

        ensureChannel(applicationContext)
        val (title, body) = titleAndBody(notification) ?: return
        postPushNotification(applicationContext, notification, title, body, deepLinkUri(notification))
    }

    /**
     * Builds the same `sakhi://` scheme `MainActivity`'s own intent-filter
     * already listens for, so a tapped notification flows through the exact
     * same `AndroidDeepLinkManager.handleIntent` -> `DeepLinkParser` pipeline
     * a tapped web/invite link does -- no separate routing table to keep in
     * sync. Returns null for types with nothing more specific than "open the
     * app" (the two local-reminder-shaped cases).
     */
    private fun deepLinkUri(notification: SakhiNotification): String? = when (notification) {
        is SakhiNotification.InvitationReceived -> "sakhi://invite/${notification.inviteCode}"
        is SakhiNotification.Sos -> "sakhi://emergency/${notification.sessionId}"
        is SakhiNotification.PartnerLoggedPeriod,
        is SakhiNotification.InvitationAccepted,
        is SakhiNotification.LogRequestReceived,
        is SakhiNotification.LogRequestResponse,
        is SakhiNotification.NewCareMessage,
        -> "sakhi://care"
        is SakhiNotification.PeriodReminder, SakhiNotification.LoggingReminder, SakhiNotification.Unknown -> null
    }

    private fun titleAndBody(notification: SakhiNotification): Pair<String, String>? = when (notification) {
        is SakhiNotification.PartnerLoggedPeriod ->
            "Sakhi" to "${notification.partnerName} logged their period today"
        is SakhiNotification.InvitationAccepted ->
            "Sakhi" to "${notification.partnerName} accepted your invite"
        is SakhiNotification.InvitationReceived ->
            "Sakhi" to "${notification.inviterName} invited you to Be Her Sakhi"
        is SakhiNotification.LogRequestReceived ->
            "Sakhi" to "${notification.partnerName} asked you to log today"
        is SakhiNotification.LogRequestResponse ->
            "Sakhi" to if (notification.approved) {
                "${notification.partnerName} shared today's log with you"
            } else {
                "${notification.partnerName} isn't ready to share today's log"
            }
        is SakhiNotification.NewCareMessage ->
            notification.senderName to "New message"
        is SakhiNotification.PeriodReminder ->
            "Sakhi" to "Your period is expected in ${notification.daysUntil} days"
        is SakhiNotification.LoggingReminder ->
            "Sakhi" to "How are you feeling today?"
        is SakhiNotification.Sos ->
            "Sakhi" to "Emergency alert -- tap to open"
        SakhiNotification.Unknown -> null
    }

    companion object {
        private const val CHANNEL_ID = "sakhi_push"

        internal suspend fun cacheAndMaybeRegisterToken(
            token: String,
            kvStore: PlatformKeyValueStore,
            userId: String?,
            register: suspend (userId: String, token: String) -> Result<Unit>,
        ) {
            kvStore.set(AndroidNotificationReminderManager.PENDING_FCM_TOKEN, token)
            val activeUserId = userId?.takeIf(String::isNotBlank) ?: return
            register(activeUserId, token)
                .onSuccess { kvStore.remove(AndroidNotificationReminderManager.PENDING_FCM_TOKEN) }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sakhi updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Partner activity, invites, and care messages"
            }
            manager.createNotificationChannel(channel)
        }

        private fun postPushNotification(context: Context, notification: SakhiNotification, title: String, body: String, deepLinkUri: String?) {
            val manager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if (!manager.areNotificationsEnabled()) return

            val launchIntent = if (deepLinkUri != null) {
                Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUri))
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            } else {
                context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                } ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
            }

            val notificationId = notification.hashCode()
            val contentIntent = PendingIntent.getActivity(
                context,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val built = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()

            manager.notify(notificationId, built)
        }
    }
}
