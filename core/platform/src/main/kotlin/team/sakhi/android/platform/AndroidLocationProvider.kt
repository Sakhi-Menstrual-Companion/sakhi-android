package team.sakhi.android.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

data class DeviceLocation(val latitude: Double, val longitude: Double)

/**
 * One-shot device location for AI Chat's Nearby Places.
 *
 * Mirrors iOS's CoreLocation flow for the nearby-places button: return a cached
 * location immediately when available, otherwise request a single fresh fix. On
 * Android the primary source is Play Services' fused provider so emulator mock
 * locations are visible even when they were injected through a test provider.
 */
class AndroidLocationProvider private constructor(
    private val dependencies: Dependencies,
) {
    constructor(context: Context) : this(Dependencies.create(context))

    internal class Dependencies(
        val fusedClient: Lazy<FusedLocationProviderClient>,
        val permissionChecker: (String) -> Int,
        val playServicesAvailability: () -> Boolean,
        val locationManagerProvider: () -> LocationManager?,
        val mainExecutor: Lazy<Executor>,
        val sdkInt: Int,
    ) {
        companion object {
            fun create(context: Context) = Dependencies(
                fusedClient = lazy(LazyThreadSafetyMode.NONE) {
                    LocationServices.getFusedLocationProviderClient(context)
                },
                permissionChecker = { permission ->
                    ContextCompat.checkSelfPermission(context, permission)
                },
                playServicesAvailability = {
                    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
                        ConnectionResult.SUCCESS
                },
                locationManagerProvider = {
                    context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                },
                mainExecutor = lazy(LazyThreadSafetyMode.NONE) {
                    ContextCompat.getMainExecutor(context)
                },
                sdkInt = Build.VERSION.SDK_INT,
            )
        }
    }

    internal companion object {
        fun forTesting(dependencies: Dependencies): AndroidLocationProvider =
            AndroidLocationProvider(dependencies)
    }

    fun hasPermission(): Boolean =
        dependencies.permissionChecker(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            dependencies.permissionChecker(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    suspend fun currentLocation(): DeviceLocation? {
        if (!hasPermission()) return null
        cachedLocation()?.let { return it }

        return if (isPlayServicesLocationAvailable()) {
            currentLocationFused(priority = requestedPriority()) ?: currentLocationPlatform()
        } else {
            currentLocationPlatform()
        }
    }

    private fun hasFineLocationPermission(): Boolean =
        dependencies.permissionChecker(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestedPriority(): Int =
        if (hasFineLocationPermission()) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

    private fun isPlayServicesLocationAvailable(): Boolean =
        dependencies.playServicesAvailability()

    private suspend fun cachedLocation(): DeviceLocation? =
        if (isPlayServicesLocationAvailable()) {
            lastKnownLocationFused() ?: lastKnownLocationPlatform()
        } else {
            lastKnownLocationPlatform()
        }

    // Lint's `MissingPermission` check can't trace the real runtime gate here --
    // every caller of these private functions only ever reaches them through
    // `currentLocation()`/`cachedLocation()`, which already return early via
    // `hasPermission()` (itself routed through the injectable
    // `dependencies.permissionChecker` lambda for testability, which is
    // exactly the indirection lint's interprocedural analysis can't follow).
    @SuppressLint("MissingPermission")
    private suspend fun lastKnownLocationFused(): DeviceLocation? =
        suspendCancellableCoroutine { continuation ->
            runCatching {
                dependencies.fusedClient.value.lastLocation
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) continuation.resume(location?.toDeviceLocation())
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    // Same real runtime gate as `lastKnownLocationFused` above -- only ever
    // reached through `currentLocation()`'s `hasPermission()` check.
    @SuppressLint("MissingPermission")
    private suspend fun currentLocationFused(priority: Int): DeviceLocation? =
        suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellationTokenSource.cancel() }
            runCatching {
                dependencies.fusedClient.value.getCurrentLocation(priority, cancellationTokenSource.token)
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) continuation.resume(location?.toDeviceLocation())
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    // Real fix: lint's `NewApi` check doesn't recognize `dependencies.sdkInt`
    // (a stored field, used instead of a direct `Build.VERSION.SDK_INT` check
    // so tests can inject any SDK version without Robolectric config) as
    // proof of the API 30 guard on `currentLocationModern` below, even though
    // this `if` branch is the only real call site and is genuinely gated.
    @SuppressLint("NewApi")
    private suspend fun currentLocationPlatform(): DeviceLocation? {
        val manager = dependencies.locationManagerProvider() ?: return null
        val provider = bestProvider(manager) ?: return null

        return if (dependencies.sdkInt >= Build.VERSION_CODES.R) {
            currentLocationModern(manager, provider)
        } else {
            lastKnownLocationPlatform()
        }
    }

    private fun bestProvider(manager: LocationManager): String? = when {
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    // Real fix: `LocationManager.getCurrentLocation(String, ...)` requires
    // API 30, but this app's minSdk is 26. Lint's `NewApi` check doesn't
    // recognize the `dependencies.sdkInt >= Build.VERSION_CODES.R` runtime
    // guard in `currentLocationPlatform()` (its only caller) since it's a
    // stored field rather than a direct `Build.VERSION.SDK_INT` comparison
    // -- `@RequiresApi` documents the true contract lint can then trust.
    // Same permission-check indirection as the two functions above for
    // `MissingPermission`.
    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    private suspend fun currentLocationModern(manager: LocationManager, provider: String): DeviceLocation? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            runCatching {
                manager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    dependencies.mainExecutor.value,
                ) { location: Location? ->
                    if (continuation.isActive) {
                        continuation.resume(location?.let { DeviceLocation(it.latitude, it.longitude) })
                    }
                }
            }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    // Same real runtime gate as the fused-provider functions above -- only
    // ever reached through `currentLocation()`'s `hasPermission()` check.
    @SuppressLint("MissingPermission")
    private fun lastKnownLocationPlatform(): DeviceLocation? {
        val manager = dependencies.locationManagerProvider() ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers.firstNotNullOfOrNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }?.toDeviceLocation()
    }

    private fun Location.toDeviceLocation(): DeviceLocation = DeviceLocation(latitude = latitude, longitude = longitude)
}
