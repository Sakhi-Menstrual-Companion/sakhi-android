package team.sakhi.android.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * The two notification channels a walk uses.
 *
 * Kept apart from the generic `sakhi_push` channel on purpose. That one is default
 * importance and deliberately bland, because it carries care and cycle updates that must
 * never be loud on a lock screen. A walk is the opposite: when she asks her person to stay
 * with her, or when she has not reached, it has to get their attention. Separate channels
 * also let either person mute one without muting the other.
 */
object StayWithMeNotifications {
    /** Her person's alerts: she is heading home, she needs longer, she is home, she is late. */
    const val ALERT_CHANNEL = "stay_with_me"

    /** Her own ongoing "your person is with you" notification while she walks. Silent. */
    const val ONGOING_CHANNEL = "stay_with_me_ongoing"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL,
                context.getString(R.string.platform_swm_channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.platform_swm_channel_alerts_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ONGOING_CHANNEL,
                context.getString(R.string.platform_swm_channel_ongoing_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.platform_swm_channel_ongoing_description)
                setShowBadge(false)
            },
        )
    }
}
