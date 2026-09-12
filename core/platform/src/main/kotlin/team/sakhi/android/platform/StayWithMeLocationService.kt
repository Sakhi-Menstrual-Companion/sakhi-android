package team.sakhi.android.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import team.sakhi.care.CareRuntimeState
import team.sakhi.care.CareStore
import team.sakhi.session.SessionManager
import team.sakhi.staywithme.StayWithMePhase
import team.sakhi.staywithme.StayWithMeSession
import team.sakhi.staywithme.StayWithMeStore
import java.text.DateFormat
import java.util.Date

/**
 * Keeps her phone sending its position and battery while a Stay With Me walk is live.
 *
 * A foreground service with type `location`, because that is the only way Android lets an
 * app keep receiving location once she locks the phone and puts it in her bag, which is
 * exactly when a walk home happens. It is started from the screen where she taps "Ask ...
 * to stay", while the app is visible and location is granted, so no background-location
 * permission is ever needed.
 *
 * ── Battery ─────────────────────────────────────────────────────────────────
 *
 * Balanced power accuracy (Wi-Fi and cell, not GPS), an update at most every 30 seconds,
 * only after she has moved 20 metres, and batched up to a minute when the phone is idle.
 * [StayWithMeStore] then throttles again to one upload every 15 seconds. Her person sees
 * "updated 40s ago" rather than a pretend live dot, which is the honest trade for a phone
 * that still has battery when she gets home.
 *
 * It stops itself the moment the walk ends, from any path: "I'm home" on the notification,
 * the button in the app, or the walk ending on the server.
 */
class StayWithMeLocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var locationCallback: LocationCallback? = null
    private var runJob: Job? = null

    private val store: StayWithMeStore? get() = GlobalContext.getOrNull()?.getOrNull()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store = store ?: return stopNow()
        StayWithMeNotifications.ensureChannels(this)

        // startForeground has to be called promptly after startForegroundService, before any
        // network round trip, or Android kills the app.
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                ONGOING_NOTIFICATION_ID,
                ongoingNotification(store.mine.value),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        }.isSuccess
        // Refused when location is not granted. The walk still works, it just cannot share
        // where she is, and her screen says so.
        if (!started) return stopNow()

        if (intent?.action == ACTION_ARRIVE) {
            scope.launch { store.arrive() }
            return START_NOT_STICKY
        }

        if (runJob?.isActive != true) runJob = scope.launch { run(store) }
        return START_STICKY
    }

    private suspend fun run(store: StayWithMeStore) {
        // Started again by the system after the process was killed: the store is empty, so
        // ask the server whether the walk is still live before sharing anything.
        if (store.mine.value == null) {
            GlobalContext.getOrNull()?.getOrNull<SessionManager>()?.current?.userId?.let { store.refresh(it) }
        }
        if (store.mine.value == null) {
            withContext(Dispatchers.Main) { stopNow() }
            return
        }

        withContext(Dispatchers.Main) { startLocationUpdates() }

        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                store.checkLate()
                store.mine.value?.let(::postOngoing)
            }
        }

        store.mine.collect { session ->
            if (session == null) {
                withContext(Dispatchers.Main) { stopNow() }
                scope.cancel()
            } else {
                postOngoing(session)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission(this) || locationCallback != null) return

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_M)
            .setMaxUpdateDelayMillis(MAX_UPDATE_DELAY_MS)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location -> scope.launch { report(location, force = false) } }
            }
        }
        locationCallback = callback
        runCatching { fused.requestLocationUpdates(request, callback, Looper.getMainLooper()) }

        // The first fix goes up at once, so her person has somewhere to look straight away
        // instead of waiting for her to walk twenty metres.
        runCatching {
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location -> location?.let { scope.launch { report(it, force = true) } } }
        }
    }

    private suspend fun report(location: Location, force: Boolean) {
        val store = store ?: return
        val (battery, charging) = batteryState()
        store.reportLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            batteryPercent = battery,
            isCharging = charging,
            force = force,
        )
    }

    private fun batteryState(): Pair<Int?, Boolean?> {
        val manager = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null to null
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        return percent to manager.isCharging
    }

    // ── Her ongoing notification ────────────────────────────────────────────

    private fun postOngoing(session: StayWithMeSession) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching { NotificationManagerCompat.from(this).notify(ONGOING_NOTIFICATION_ID, ongoingNotification(session)) }
    }

    private fun ongoingNotification(session: StayWithMeSession?): Notification {
        val name = watcherName()
        val title = name?.let { getString(R.string.platform_swm_ongoing_title, it) }
            ?: getString(R.string.platform_swm_ongoing_title_generic)
        val now = store?.now()
        val text = when {
            session == null || now == null -> getString(R.string.platform_swm_ongoing_starting)
            session.phase(now) == StayWithMePhase.LATE ->
                name?.let { getString(R.string.platform_swm_ongoing_late, it) }
                    ?: getString(R.string.platform_swm_ongoing_late_generic)
            else -> getString(
                R.string.platform_swm_ongoing_text,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(session.expectedArrival.toEpochMilliseconds())),
            )
        }

        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sakhi://care/stay/${session?.id.orEmpty()}"))
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            this, ONGOING_NOTIFICATION_ID, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val arriveIntent = PendingIntent.getService(
            this, ONGOING_NOTIFICATION_ID + 1,
            Intent(this, StayWithMeLocationService::class.java).setAction(ACTION_ARRIVE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, StayWithMeNotifications.ONGOING_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.platform_swm_action_arrived), arriveIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** The name she gave her Care Mode person, for "Rahul is with you". */
    private fun watcherName(): String? {
        val care = GlobalContext.getOrNull()?.getOrNull<CareStore>()?.careState?.value
        val name = (care as? CareRuntimeState.OwnerConnected)?.partnership?.partnerName?.trim()
        return name?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
    }

    private fun stopNow(): Int {
        locationCallback?.let { runCatching { fused.removeLocationUpdates(it) } }
        locationCallback = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        locationCallback?.let { runCatching { fused.removeLocationUpdates(it) } }
        locationCallback = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_ARRIVE = "team.sakhi.android.platform.stay_with_me.ARRIVE"

        private const val ONGOING_NOTIFICATION_ID = 2_001
        private const val TICK_MS = 30_000L
        private const val UPDATE_INTERVAL_MS = 30_000L
        private const val MIN_UPDATE_INTERVAL_MS = 15_000L
        private const val MAX_UPDATE_DELAY_MS = 60_000L
        private const val MIN_UPDATE_DISTANCE_M = 20f

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, StayWithMeLocationService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StayWithMeLocationService::class.java))
        }

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}
