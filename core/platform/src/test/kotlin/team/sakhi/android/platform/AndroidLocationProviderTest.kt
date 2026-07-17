package team.sakhi.android.platform

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidLocationProviderTest {

    @Test
    fun hasPermission_returnsTrueWhenCoarsePermissionGranted() {
        val provider = provider(
            permissionStatus = mapOf(
                Manifest.permission.ACCESS_COARSE_LOCATION to PackageManager.PERMISSION_GRANTED,
                Manifest.permission.ACCESS_FINE_LOCATION to PackageManager.PERMISSION_DENIED,
            ),
        )

        assertTrue(provider.hasPermission())
    }

    @Test
    fun currentLocation_withoutPermission_returnsNullWithoutTouchingLocationSources() = runBlocking {
        var fusedClientCreations = 0
        var locationManagerRequests = 0

        val provider = provider(
            permissionStatus = deniedPermissions(),
            playServicesAvailable = true,
            fusedClientFactory = {
                fusedClientCreations += 1
                mockk(relaxed = true)
            },
            locationManagerProvider = {
                locationManagerRequests += 1
                mockk(relaxed = true)
            },
        )

        assertNull(provider.currentLocation())
        assertEquals(0, fusedClientCreations)
        assertEquals(0, locationManagerRequests)
    }

    @Test
    fun currentLocation_returnsFusedCachedLocationBeforePlatformFallback() = runBlocking {
        val fusedClient = mockk<FusedLocationProviderClient>()
        every { fusedClient.lastLocation } returns successfulTask(mockLocation(12.34, 56.78))
        var locationManagerRequests = 0

        val provider = provider(
            permissionStatus = grantedCoarsePermissions(),
            playServicesAvailable = true,
            fusedClientFactory = { fusedClient },
            locationManagerProvider = {
                locationManagerRequests += 1
                mockk(relaxed = true)
            },
        )

        assertEquals(DeviceLocation(12.34, 56.78), provider.currentLocation())
        verify(exactly = 0) { fusedClient.getCurrentLocation(any<Int>(), any()) }
        assertEquals(0, locationManagerRequests)
    }

    @Test
    fun currentLocation_fallsBackToPlatformCurrentLocationWhenFusedProviderHasNoLocation() = runBlocking {
        val fusedClient = mockk<FusedLocationProviderClient>()
        every { fusedClient.lastLocation } returns failedTask(IllegalStateException("no cached fused location"))
        every {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, any())
        } returns failedTask(IllegalStateException("no fresh fused location"))

        val manager = mockk<LocationManager>()
        val executor = mockk<Executor>(relaxed = true)
        every { manager.getLastKnownLocation(any()) } returns null
        every { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) } returns true
        every { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } returns false
        every {
            manager.getCurrentLocation(LocationManager.GPS_PROVIDER, any(), executor, any())
        } answers {
            arg<Consumer<Location>>(3).accept(mockLocation(22.0, 33.0))
            Unit
        }

        val provider = provider(
            permissionStatus = grantedFinePermissions(),
            playServicesAvailable = true,
            fusedClientFactory = { fusedClient },
            locationManagerProvider = { manager },
            mainExecutorFactory = { executor },
        )

        assertEquals(DeviceLocation(22.0, 33.0), provider.currentLocation())
        verify(exactly = 1) { fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, any()) }
    }

    @Test
    fun currentLocation_reusesPlatformCallbackExecutorAcrossRequests() = runBlocking {
        val manager = mockk<LocationManager>()
        val executor = mockk<Executor>(relaxed = true)
        val executorsUsed = mutableListOf<Executor>()
        var executorCreations = 0

        every { manager.getLastKnownLocation(any()) } returns null
        every { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) } returns false
        every { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } returns true
        every {
            manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, any(), any(), any())
        } answers {
            executorsUsed += arg<Executor>(2)
            arg<Consumer<Location>>(3).accept(mockLocation(5.0, 6.0))
            Unit
        }

        val provider = provider(
            permissionStatus = grantedCoarsePermissions(),
            playServicesAvailable = false,
            locationManagerProvider = { manager },
            mainExecutorFactory = {
                executorCreations += 1
                executor
            },
        )

        assertEquals(DeviceLocation(5.0, 6.0), provider.currentLocation())
        assertEquals(DeviceLocation(5.0, 6.0), provider.currentLocation())
        assertEquals(1, executorCreations)
        assertEquals(listOf(executor, executor), executorsUsed)
    }

    private fun provider(
        permissionStatus: Map<String, Int> = grantedFinePermissions(),
        playServicesAvailable: Boolean = false,
        fusedClientFactory: () -> FusedLocationProviderClient = { mockk(relaxed = true) },
        locationManagerProvider: () -> LocationManager? = { null },
        mainExecutorFactory: () -> Executor = { mockk(relaxed = true) },
        sdkInt: Int = Build.VERSION_CODES.R,
    ): AndroidLocationProvider = AndroidLocationProvider.forTesting(
        AndroidLocationProvider.Dependencies(
            fusedClient = lazy(LazyThreadSafetyMode.NONE) { fusedClientFactory() },
            permissionChecker = { permission ->
                permissionStatus[permission] ?: PackageManager.PERMISSION_DENIED
            },
            playServicesAvailability = { playServicesAvailable },
            locationManagerProvider = locationManagerProvider,
            mainExecutor = lazy(LazyThreadSafetyMode.NONE) { mainExecutorFactory() },
            sdkInt = sdkInt,
        ),
    )

    private fun mockLocation(latitude: Double, longitude: Double): Location = mockk {
        every { this@mockk.latitude } returns latitude
        every { this@mockk.longitude } returns longitude
    }

    private fun successfulTask(location: Location): Task<Location> {
        val task = mockk<Task<Location>>()
        every { task.addOnSuccessListener(any()) } answers {
            firstArg<OnSuccessListener<Location>>().onSuccess(location)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
        every { task.addOnCanceledListener(any()) } returns task
        return task
    }

    private fun failedTask(error: Exception): Task<Location> {
        val task = mockk<Task<Location>>()
        every { task.addOnSuccessListener(any()) } returns task
        every { task.addOnFailureListener(any()) } answers {
            firstArg<OnFailureListener>().onFailure(error)
            task
        }
        every { task.addOnCanceledListener(any()) } returns task
        return task
    }

    private fun deniedPermissions(): Map<String, Int> = mapOf(
        Manifest.permission.ACCESS_COARSE_LOCATION to PackageManager.PERMISSION_DENIED,
        Manifest.permission.ACCESS_FINE_LOCATION to PackageManager.PERMISSION_DENIED,
    )

    private fun grantedCoarsePermissions(): Map<String, Int> = mapOf(
        Manifest.permission.ACCESS_COARSE_LOCATION to PackageManager.PERMISSION_GRANTED,
        Manifest.permission.ACCESS_FINE_LOCATION to PackageManager.PERMISSION_DENIED,
    )

    private fun grantedFinePermissions(): Map<String, Int> = mapOf(
        Manifest.permission.ACCESS_COARSE_LOCATION to PackageManager.PERMISSION_DENIED,
        Manifest.permission.ACCESS_FINE_LOCATION to PackageManager.PERMISSION_GRANTED,
    )
}
