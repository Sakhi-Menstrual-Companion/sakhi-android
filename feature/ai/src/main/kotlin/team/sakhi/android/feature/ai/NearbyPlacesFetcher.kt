package team.sakhi.android.feature.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import team.sakhi.models.SafePlace
import team.sakhi.platform.PlatformConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val PLACES_TAG = "SakhiPlaces"
private const val SEARCH_RADIUS_METERS = 2000
private const val MAX_RESULTS = 5

sealed interface NearbyPlacesFetchResult {
    data class Success(val places: List<SafePlace>) : NearbyPlacesFetchResult

    data object ZeroResults : NearbyPlacesFetchResult

    data class LookupError(
        val status: String,
        val errorMessage: String? = null,
        val httpCode: Int? = null,
    ) : NearbyPlacesFetchResult
}

class NearbyPlacesFetcher {

    suspend fun inspectNearby(
        latitude: Double,
        longitude: Double,
        placeType: String,
    ): NearbyPlacesFetchResult = withContext(Dispatchers.IO) {
        val apiKey = PlatformConfig.googlePlacesApiKey
        if (apiKey.isBlank()) {
            Log.e(PLACES_TAG, "Places lookup aborted: GOOGLE_PLACES_API_KEY is blank")
            return@withContext NearbyPlacesFetchResult.LookupError(status = "MISSING_API_KEY")
        }

        val keyword = keywordFor(placeType)
        val encodedKeyword = URLEncoder.encode(keyword, Charsets.UTF_8.name())
        val requestUrl = buildString {
            append("https://maps.googleapis.com/maps/api/place/nearbysearch/json")
            append("?location=")
            append(latitude)
            append(",")
            append(longitude)
            append("&radius=")
            append(SEARCH_RADIUS_METERS)
            append("&keyword=")
            append(encodedKeyword)
            append("&key=")
            append(apiKey)
        }

        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }

        try {
            val httpCode = connection.responseCode
            val body = (if (httpCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            })?.bufferedReader()?.use { it.readText() }.orEmpty()
            val root = JSONObject(body.ifBlank { "{}" })
            val status = root.optString("status").ifBlank {
                if (httpCode in 200..299) "UNKNOWN" else "HTTP_$httpCode"
            }
            val errorMessage = root.optString("error_message").takeIf { it.isNotBlank() }

            when {
                httpCode !in 200..299 -> {
                    Log.e(
                        PLACES_TAG,
                        "Places HTTP failure code=$httpCode status=$status placeType=$placeType lat=$latitude lon=$longitude error=${errorMessage ?: "<none>"}",
                    )
                    NearbyPlacesFetchResult.LookupError(status, errorMessage, httpCode)
                }

                status == "OK" -> {
                    val places = parsePlaces(root, latitude, longitude)
                    Log.i(
                        PLACES_TAG,
                        "Places success count=${places.size} placeType=$placeType lat=$latitude lon=$longitude",
                    )
                    if (places.isEmpty()) NearbyPlacesFetchResult.ZeroResults
                    else NearbyPlacesFetchResult.Success(places)
                }

                status == "ZERO_RESULTS" -> {
                    Log.i(
                        PLACES_TAG,
                        "Places zero results placeType=$placeType lat=$latitude lon=$longitude",
                    )
                    NearbyPlacesFetchResult.ZeroResults
                }

                else -> {
                    Log.e(
                        PLACES_TAG,
                        "Places API failure status=$status placeType=$placeType lat=$latitude lon=$longitude error=${errorMessage ?: "<none>"}",
                    )
                    NearbyPlacesFetchResult.LookupError(status, errorMessage, httpCode)
                }
            }
        } catch (error: Exception) {
            Log.e(
                PLACES_TAG,
                "Places transport failure placeType=$placeType lat=$latitude lon=$longitude error=${error.message}",
                error,
            )
            NearbyPlacesFetchResult.LookupError(
                status = "TRANSPORT_ERROR",
                errorMessage = error.message,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePlaces(
        root: JSONObject,
        originLatitude: Double,
        originLongitude: Double,
    ): List<SafePlace> {
        val results = root.optJSONArray("results") ?: return emptyList()
        val places = mutableListOf<SafePlace>()
        for (index in 0 until minOf(results.length(), MAX_RESULTS * 2)) {
            val place = results.optJSONObject(index) ?: continue
            val geometry = place.optJSONObject("geometry") ?: continue
            val location = geometry.optJSONObject("location") ?: continue
            val lat = location.optDouble("lat", Double.NaN)
            val lng = location.optDouble("lng", Double.NaN)
            val placeId = place.optString("place_id")
            val name = place.optString("name")
            if (lat.isNaN() || lng.isNaN() || placeId.isBlank() || name.isBlank()) continue
            places += SafePlace(
                placeId = placeId,
                name = name,
                distanceMeters = haversineDistance(originLatitude, originLongitude, lat, lng),
                rating = place.optDouble("rating").takeUnless { it.isNaN() },
                latitude = lat,
                longitude = lng,
            )
        }
        return places
            .sortedBy { it.distanceMeters }
            .take(MAX_RESULTS)
    }

    private fun keywordFor(placeType: String): String = when (placeType) {
        "washroom" -> "public washroom OR toilet"
        "hospital" -> "hospital OR clinic"
        "police_station" -> "police station"
        else -> placeType
    }

    private fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = toRadians(lat2 - lat1)
        val dLng = toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(toRadians(lat1)) * cos(toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    private fun toRadians(degrees: Double): Double = degrees * (kotlin.math.PI / 180.0)
}
