package team.sakhi.android.platform

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * The two things a walk has to say when nobody is looking at the app.
 *
 * "She has not reached yet", on her person's phone, and "Are you okay?", on hers. iOS
 * schedules both as local notifications, because both are decided on the phone rather than
 * by the server: his alarm delay and her check-in interval are each his and hers alone, and
 * the server has never heard of either.
 *
 * Inexact on purpose. `setAndAllowWhileIdle` wakes the phone near the time even in Doze,
 * which is what Android allows an app that is not a clock. An exact alarm needs a
 * permission Play grants to alarm clocks and timers, and asking for it to be a few seconds
 * sharper on a notification that is already a minute-scale promise is not a trade worth
 * making. What it must never do is not fire at all, and this does not.
 */
object StayWithMeLocalAlarms {

    /** Her person's phone: raise "she has not reached yet" at this moment. */
    fun scheduleNotReached(context: Context, sessionId: String, atEpochMillis: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(context, NOT_REACHED_REQUEST, notReachedIntent(context, sessionId))
        // Already past: say it now rather than never.
        val fireAt = maxOf(atEpochMillis, System.currentTimeMillis() + 1_000)
        runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending) }
    }

    fun cancelNotReached(context: Context) {
        cancel(context, NOT_REACHED_REQUEST, notReachedIntent(context, sessionId = null))
        NotificationManagerCompat.from(context).cancel(NOT_REACHED_NOTIFICATION_ID)
    }

    /** Her phone: ask her again in this many minutes. Re-armed each time it fires. */
    fun scheduleCheckIn(context: Context, everyMinutes: Int) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(context, CHECK_IN_REQUEST, checkInIntent(context, everyMinutes))
        val fireAt = System.currentTimeMillis() + everyMinutes.coerceAtLeast(1) * 60_000L
        runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending) }
    }

    fun cancelCheckIn(context: Context) {
        cancel(context, CHECK_IN_REQUEST, checkInIntent(context, everyMinutes = 0))
        NotificationManagerCompat.from(context).cancel(CHECK_IN_NOTIFICATION_ID)
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun notReachedIntent(context: Context, sessionId: String?) =
        Intent(context, StayWithMeAlarmReceiver::class.java).apply {
            action = ACTION_NOT_REACHED
            if (sessionId != null) putExtra(EXTRA_SESSION_ID, sessionId)
        }

    private fun checkInIntent(context: Context, everyMinutes: Int) =
        Intent(context, StayWithMeAlarmReceiver::class.java).apply {
            action = ACTION_CHECK_IN
            putExtra(EXTRA_EVERY_MINUTES, everyMinutes)
        }

    private fun pendingIntent(context: Context, request: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun cancel(context: Context, request: Int, intent: Intent) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.cancel(pendingIntent(context, request, intent)) }
    }

    internal const val ACTION_NOT_REACHED = "team.sakhi.android.STAY_WITH_ME_NOT_REACHED"
    internal const val ACTION_CHECK_IN = "team.sakhi.android.STAY_WITH_ME_CHECK_IN"
    internal const val EXTRA_SESSION_ID = "sessionId"
    internal const val EXTRA_EVERY_MINUTES = "everyMinutes"

    private const val NOT_REACHED_REQUEST = 8801
    private const val CHECK_IN_REQUEST = 8802
    internal const val NOT_REACHED_NOTIFICATION_ID = 8811
    internal const val CHECK_IN_NOTIFICATION_ID = 8812
}

/**
 * What the two alarms actually do when they go off: post a notification that opens the walk.
 *
 * The screen behind it does the rest. "She has not reached yet" raises the alarm screen with
 * its sound and its slider, and the check-in raises the box that asks her. Neither is
 * decided here, because a notification that could be wrong about a walk is worse than a
 * plain one that opens it.
 */
class StayWithMeAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!canPost(context)) return
        StayWithMeNotifications.ensureChannels(context)

        when (intent.action) {
            StayWithMeLocalAlarms.ACTION_NOT_REACHED -> post(
                context = context,
                id = StayWithMeLocalAlarms.NOT_REACHED_NOTIFICATION_ID,
                title = context.getString(R.string.platform_swm_not_reached_title),
                body = context.getString(R.string.platform_swm_not_reached_body),
                // The loudest thing Sakhi posts, and the only one that may break through.
                priority = NotificationCompat.PRIORITY_MAX,
                fullScreen = true,
            )
            StayWithMeLocalAlarms.ACTION_CHECK_IN -> {
                post(
                    context = context,
                    id = StayWithMeLocalAlarms.CHECK_IN_NOTIFICATION_ID,
                    title = context.getString(R.string.platform_swm_check_in_title),
                    body = context.getString(R.string.platform_swm_check_in_body),
                    priority = NotificationCompat.PRIORITY_HIGH,
                    fullScreen = false,
                )
                // Ask again after the same gap, for as long as the walk lasts. The screen
                // cancels this the moment she marks herself home.
                val every = intent.getIntExtra(StayWithMeLocalAlarms.EXTRA_EVERY_MINUTES, 10)
                if (every > 0) StayWithMeLocalAlarms.scheduleCheckIn(context, every)
            }
        }
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun post(
        context: Context,
        id: Int,
        title: String,
        body: String,
        priority: Int,
        fullScreen: Boolean,
    ) {
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(DEEP_LINK))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            id,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, StayWithMeNotifications.ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sakhi)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(priority)
            .setCategory(if (fullScreen) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .apply { if (fullScreen) setFullScreenIntent(pending, true) }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private companion object {
        /** The walk itself. `NotificationRouting` sends every walk push to the same place. */
        const val DEEP_LINK = "sakhi://care/stay"
    }
}
