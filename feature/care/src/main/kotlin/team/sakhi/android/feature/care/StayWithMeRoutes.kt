package team.sakhi.android.feature.care

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import team.sakhi.platform.PlatformConfig
import team.sakhi.staywithme.StayWithMeDestination
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The way from where she is to where she is going, and how long it is. */
internal data class WalkRoute(
    val points: List<Pair<Double, Double>>,
    val distanceMeters: Double,
    /** Null when there is no real route, only a straight line. */
    val durationSeconds: Long?,
)

/**
 * A walking route from Google's Routes API, with a straight line standing in when there is
 * none. The line still tells her person which way home is, which is most of what they need.
 *
 * The key is Android-restricted, so every request says which app it comes from (package
 * and signing certificate), the way Google checks it. Until the Routes API is enabled on
 * the Cloud project, every request is refused and the straight line is what shows.
 */
internal object StayWithMeRoutes {

    suspend fun walking(context: Context, from: Pair<Double, Double>, to: StayWithMeDestination): WalkRoute =
        withContext(Dispatchers.IO) {
            runCatching { request(context, from, to) }.getOrNull() ?: straight(from, to)
        }

    private fun request(context: Context, from: Pair<Double, Double>, to: StayWithMeDestination): WalkRoute? {
        val key = PlatformConfig.googlePlacesApiKey.takeIf { it.isNotBlank() } ?: return null
        val body = JSONObject()
            .put("origin", JSONObject().put("location", JSONObject().put("latLng", latLng(from.first, from.second))))
            .put("destination", JSONObject().put("location", JSONObject().put("latLng", latLng(to.latitude, to.longitude))))
            .put("travelMode", "WALK")
            .toString()

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", key)
            setRequestProperty("X-Goog-FieldMask", "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline")
            setRequestProperty("X-Android-Package", context.packageName)
            signingSha1(context)?.let { setRequestProperty("X-Android-Cert", it) }
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            if (connection.responseCode !in 200..299) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val route = json.optJSONArray("routes")?.optJSONObject(0) ?: return null
            val encoded = route.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()
            val points = decodePolyline(encoded).takeIf { it.size >= 2 } ?: return null
            WalkRoute(
                points = points,
                distanceMeters = route.optDouble("distanceMeters", metres(from, to.latitude to to.longitude)),
                durationSeconds = route.optString("duration").removeSuffix("s").toLongOrNull(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun straight(from: Pair<Double, Double>, to: StayWithMeDestination) = WalkRoute(
        points = listOf(from, to.latitude to to.longitude),
        distanceMeters = metres(from, to.latitude to to.longitude),
        durationSeconds = null,
    )

    private fun latLng(lat: Double, lng: Double) = JSONObject().put("latitude", lat).put("longitude", lng)

    /** The signing certificate's SHA-1, as Google expects it: hex, no separators. */
    private fun signingSha1(context: Context): String? = runCatching {
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        } ?: return null
        MessageDigest.getInstance("SHA-1").digest(signature.toByteArray()).joinToString("") { "%02X".format(it) }
    }.getOrNull()

    /** Google's encoded polyline format. */
    private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            points += lat / 1e5 to lng / 1e5
        }
        return points
    }

    private fun metres(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.first - a.first)
        val dLng = Math.toRadians(b.second - a.second)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.first)) * cos(Math.toRadians(b.first)) * sin(dLng / 2) * sin(dLng / 2)
        return r * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    private const val ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes"
}
