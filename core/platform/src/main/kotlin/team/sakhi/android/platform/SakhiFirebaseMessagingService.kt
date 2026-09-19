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
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import team.sakhi.notifications.InAppNotificationStore
import team.sakhi.notifications.NotificationPayloadParser
import team.sakhi.notifications.NotificationRouting
import team.sakhi.notifications.SakhiNotification
import team.sakhi.platform.PlatformKeyValueStore
import team.sakhi.repositories.DeviceRepository
import team.sakhi.session.SessionManager
import team.sakhi.staywithme.StayWithMeStore

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

        // Every push is also a row in the in-app inbox (migration 056): the server records it
        // before sending. Re-read now so the bell's badge moves the moment the push lands,
        // not the next time she happens to reopen Home. Coalesced and cheap, and harmless for
        // a type the inbox does not hold.
        runCatching { GlobalContext.getOrNull()?.getOrNull<InAppNotificationStore>()?.refresh() }

        // A walk push means the row has already changed. Read it now rather than wait for a
        // socket the OS has very likely already killed, so the screen behind the banner is
        // right the moment she opens it. Same rule as iOS: the push is a reason to go and
        // read, never the truth itself.
        if (notification.isStayWithMe) {
            scope.launch {
                runCatching {
                    val koin = GlobalContext.getOrNull() ?: return@launch
                    val userId = koin.getOrNull<SessionManager>()?.current?.userId
                    if (!userId.isNullOrBlank()) koin.getOrNull<StayWithMeStore>()?.refresh(userId)
                }
            }
        }

        if (notification == SakhiNotification.Unknown) return

        ensureChannel(applicationContext)
        StayWithMeNotifications.ensureChannels(applicationContext)
        val presentation = presentation(applicationContext, notification) ?: return
        // Where a tap goes is decided in ONE place, shared with iOS and with the inbox.
        postPushNotification(applicationContext, presentation, StayWithMeAskLink.uriFor(notification))
    }

    /** Every push that says something about a live walk. */
    private val SakhiNotification.isStayWithMe: Boolean
        get() = this is SakhiNotification.StayWithMeStarted ||
            this is SakhiNotification.StayWithMeExtended ||
            this is SakhiNotification.StayWithMeEnded ||
            this is SakhiNotification.StayWithMeLate ||
            this is SakhiNotification.StayWithMeAsk ||
            this is SakhiNotification.StayWithMeDeclined

    private data class PushPresentation(
        val title: String,
        val body: String,
        /**
         * Android replaces an existing notification when a new one reuses its id. Every
         * care and cycle push now renders the same generic line, so they share one id and
         * collapse into a single "something is waiting" row instead of stacking up as a
         * column of identical notifications, which would leak volume even though it no
         * longer leaks content. The two emergency cases keep their own ids: those must
         * never replace one another, and an SOS must never be replaced by anything.
         */
        val notificationId: Int,
        /** Walk notifications go on their own high-importance channel; see StayWithMeNotifications. */
        val channelId: String = CHANNEL_ID,
        val highPriority: Boolean = false,
    )

    /**
     * Everything except Emergency Assistance renders as the same generic line. See the
     * comment on `platform_push_care_update` in strings.xml for why the per-type copy
     * that used to live here was removed: it put the partner's name and the health fact
     * itself on the lock screen, from a payload the server had deliberately sent silent.
     *
     * The type still matters, it just decides where the tap goes
     * ([NotificationRouting.deepLinkUri]), not
     * what a bystander gets to read.
     */
    private fun presentation(context: Context, notification: SakhiNotification): PushPresentation? = when (notification) {
        is SakhiNotification.PartnerLoggedPeriod,
        is SakhiNotification.InvitationAccepted,
        is SakhiNotification.InvitationReceived,
        is SakhiNotification.LogRequestReceived,
        is SakhiNotification.LogRequestResponse,
        is SakhiNotification.NewCareMessage,
        is SakhiNotification.PeriodReminder,
        SakhiNotification.LoggingReminder,
        -> PushPresentation(
            title = context.getString(R.string.platform_notification_app_name),
            body = context.getString(R.string.platform_push_care_update),
            notificationId = CARE_UPDATE_NOTIFICATION_ID,
        )
        is SakhiNotification.Sos -> PushPresentation(
            title = context.getString(R.string.platform_notification_app_name),
            body = context.getString(R.string.platform_push_sos),
            notificationId = SOS_NOTIFICATION_ID,
        )
        // The one deliberate exception to the generic rule. Safe on a lock screen because
        // the wording names nobody and locates nobody: she learns the spot after she
        // accepts, not before.
        is SakhiNotification.EmergencyRequestReceived -> PushPresentation(
            title = context.getString(R.string.platform_push_emergency_nearby_title),
            body = context.getString(R.string.platform_push_emergency_nearby_body),
            // Keyed on the request so two women asking at once produce two rows rather
            // than one silently replacing the other.
            notificationId = notification.requestId.hashCode(),
        )
        // Sent as a visible FCM alert, which the system draws itself while the app is in the
        // background. In the foreground nothing is drawn, exactly as before this type had a
        // name of its own (it used to parse to Unknown). Its inbox row carries it either way.
        // Stay With Me. Her first name and that she is walking, never where: the position
        // stays inside the app. All four share one id per walk, so "is home" replaces
        // "is heading home" instead of stacking under it.
        is SakhiNotification.StayWithMeStarted -> walkPresentation(
            notification.sessionId,
            context.getString(R.string.platform_push_swm_started_title, walkName(context, notification.ownerName)),
            context.getString(R.string.platform_push_swm_started_body),
        )
        is SakhiNotification.StayWithMeExtended -> walkPresentation(
            notification.sessionId,
            context.getString(R.string.platform_push_swm_extended_title, walkName(context, notification.ownerName)),
            context.getString(R.string.platform_push_swm_extended_body),
        )
        is SakhiNotification.StayWithMeEnded -> walkPresentation(
            notification.sessionId,
            context.getString(
                if (notification.arrived) R.string.platform_push_swm_arrived_title else R.string.platform_push_swm_cancelled_title,
                walkName(context, notification.ownerName),
            ),
            context.getString(
                if (notification.arrived) R.string.platform_push_swm_arrived_body else R.string.platform_push_swm_cancelled_body,
            ),
        )
        is SakhiNotification.StayWithMeLate -> walkPresentation(
            notification.sessionId,
            context.getString(R.string.platform_push_swm_late_title, walkName(context, notification.ownerName)),
            context.getString(R.string.platform_push_swm_late_body),
        )
        // Her person asking to stay with her. On the walk channel because it is the same
        // moment and she should see it, and keyed to the connection so a second ask
        // replaces the first rather than stacking.
        is SakhiNotification.StayWithMeAsk -> walkPresentation(
            notification.partnershipId,
            context.getString(R.string.platform_push_swm_ask_title, walkName(context, notification.askerName)),
            context.getString(R.string.platform_push_swm_ask_body),
        )
        // She turned the ask down. Kept to the fact: nothing was started.
        is SakhiNotification.StayWithMeDeclined -> walkPresentation(
            notification.partnershipId + "declined",
            context.getString(R.string.platform_push_swm_declined_title, walkName(context, notification.ownerName)),
            context.getString(R.string.platform_push_swm_declined_body),
        )
        is SakhiNotification.FeatureAvailable -> null
        SakhiNotification.Unknown -> null
    }

    private fun walkName(context: Context, name: String): String =
        name.trim().ifEmpty { context.getString(R.string.platform_push_swm_someone) }

    private fun walkPresentation(sessionId: String, title: String, body: String) = PushPresentation(
        title = title,
        body = body,
        notificationId = WALK_NOTIFICATION_BASE + (sessionId.hashCode() and 0xFFFF),
        channelId = StayWithMeNotifications.ALERT_CHANNEL,
        highPriority = true,
    )

    companion object {
        private const val CHANNEL_ID = "sakhi_push"
        private const val WALK_NOTIFICATION_BASE = 3_000_000

        /** Shared by every generic care/cycle push so they collapse into one row. */
        private const val CARE_UPDATE_NOTIFICATION_ID = 1001
        private const val SOS_NOTIFICATION_ID = 1002

        internal suspend fun cacheAndMaybeRegisterToken(
            token: String,
            kvStore: PlatformKeyValueStore,
            userId: String?,
            register: suspend (userId: String, token: String) -> Result<Unit>,
        ) {
            // Kept after a successful registration, not removed: it is this phone's token,
            // and the next account to sign in here, and sign-out, both need it. Removing it
            // is what left every account after the first one on a phone without pushes.
            kvStore.set(AndroidNotificationReminderManager.PENDING_FCM_TOKEN, token)
            val activeUserId = userId?.takeIf(String::isNotBlank) ?: return
            register(activeUserId, token)
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.platform_push_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.platform_push_channel_description)
            }
            manager.createNotificationChannel(channel)
        }

        private fun postPushNotification(context: Context, presentation: PushPresentation, deepLinkUri: String?) {
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

            val notificationId = presentation.notificationId
            val contentIntent = PendingIntent.getActivity(
                context,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val built = NotificationCompat.Builder(context, presentation.channelId)
                .setSmallIcon(R.drawable.ic_stat_sakhi)
                .setColor(ContextCompat.getColor(context, R.color.platform_notification_accent))
                .setContentTitle(presentation.title)
                .setContentText(presentation.body)
                .setPriority(if (presentation.highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                // Its own group, so the system never bundles it with other Sakhi notifications.
                // An automatic bundle has no tap action of its own: on a Redmi, tapping the
                // collapsed bundle did nothing, and a walk alert is the one notification that
                // must open on the first tap.
                .apply { if (presentation.channelId != CHANNEL_ID) setGroup(presentation.channelId + notificationId) }
                // Keeps the generic copy generic on a locked screen: without this, an OEM
                // or user setting that hides sensitive content has nothing to fall back to
                // and some launchers will still render the full text.
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()

            manager.notify(notificationId, built)
        }
    }
}
