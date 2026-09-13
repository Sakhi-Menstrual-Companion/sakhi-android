package team.sakhi.android.feature.care

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import team.sakhi.android.platform.StayWithMeLocationService
import team.sakhi.staywithme.StayWithMeDestination

/**
 * "Where to?" for a walk: a few letters in, a short list of real places out.
 *
 * The phone's own Geocoder rather than a web API: it needs no key, costs nothing per
 * search, and on a Google phone it knows the same places Maps does. Results are drawn
 * towards where she is when the phone already knows that, so "Metro" means her metro.
 * Only the place she picks leaves the phone, as the walk's destination.
 */
internal class StayWithMeDestinationSearch(private val context: Context) {

    suspend fun search(query: String): List<Suggestion> {
        val text = query.trim()
        if (text.length < 3 || !Geocoder.isPresent()) return emptyList()
        val near = lastKnown()
        return withContext(Dispatchers.IO) {
            runCatching {
                val geocoder = Geocoder(context)
                @Suppress("DEPRECATION")
                val found: List<Address> = if (near != null) {
                    // Roughly 25 km each way around her.
                    geocoder.getFromLocationName(
                        text, MAX_RESULTS,
                        near.first - 0.25, near.second - 0.25, near.first + 0.25, near.second + 0.25,
                    )
                } else {
                    geocoder.getFromLocationName(text, MAX_RESULTS)
                } ?: emptyList()
                found.filter { it.hasLatitude() && it.hasLongitude() }.map { it.toSuggestion(text) }
            }.getOrDefault(emptyList())
        }
    }

    private suspend fun lastKnown(): Pair<Double, Double>? = StayWithMeLocationService.lastKnownLatLng(context)

    private fun Address.toSuggestion(query: String): Suggestion {
        val title = listOfNotNull(featureName, thoroughfare, subLocality, locality)
            .firstOrNull { it.isNotBlank() && it.any(Char::isLetter) }
            ?: query
        val subtitle = (0..maxAddressLineIndex).mapNotNull { getAddressLine(it) }.joinToString(", ")
        return Suggestion(
            title = title,
            subtitle = subtitle.takeIf { it.isNotBlank() && it != title },
            destination = StayWithMeDestination(title.take(80), latitude, longitude),
        )
    }

    data class Suggestion(
        val title: String,
        val subtitle: String?,
        val destination: StayWithMeDestination,
    )

    private companion object {
        const val MAX_RESULTS = 4
    }
}
