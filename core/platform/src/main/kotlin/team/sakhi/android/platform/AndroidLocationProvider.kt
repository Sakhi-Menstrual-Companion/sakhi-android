package team.sakhi.android.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

data class DeviceLocation(val latitude: Double, val longitude: Double)

/**
 * One-shot device location for AI Chat's Nearby Places -- deliberately plain
 * `android.location.LocationManager`, not Google Play Services'
 * `FusedLocationProviderClient`, to avoid pulling in a new heavy dependency
 * for a single coarse, on-demand lookup (no continuous tracking, matching
 * iOS's stated location-usage scope: emergency SOS + safe-places search).
 */
class AndroidLocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    suspend fun currentLocation(): DeviceLocation? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = bestProvider(manager) ?: return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            currentLocationModern(manager, provider)
        } else {
            lastKnownLocationLegacy(manager)
        }
    }

    private fun bestProvider(manager: LocationManager): String? = when {
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    private suspend fun currentLocationModern(manager: LocationManager, provider: String): DeviceLocation? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            runCatching {
                manager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    Executors.newSingleThreadExecutor(),
                ) { location: Location? ->
                    if (continuation.isActive) {
                        continuation.resume(location?.let { DeviceLocation(it.latitude, it.longitude) })
                    }
                }
            }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun lastKnownLocationLegacy(manager: LocationManager): DeviceLocation? {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers.firstNotNullOfOrNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }?.let { DeviceLocation(it.latitude, it.longitude) }
    }
}
