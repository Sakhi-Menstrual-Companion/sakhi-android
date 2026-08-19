package team.sakhi.android.feature.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import team.sakhi.android.designsystem.LocalSakhiDarkTheme
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.platform.DeviceLocation
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.MapStyleOptions

/**
 * The chat header's Nearby button, ported from iOS `SakhiAIChatView.nearbySakhiButton`.
 *
 * It sits where a close button would, because iOS's chat sheet has no X — the sheet's
 * grabber closes it, and this slot carries the way into Emergency Assistance instead.
 *
 * A white capsule holding either a pile of nearby faces and their count, or a fallback
 * glyph when there is nobody around or no count yet. The fallback matters as much as the
 * count: the screen this opens is the one offering Search Again and the nearest washroom
 * or hospital, which is exactly what she needs when there is no one nearby. A face pile of
 * zero faces would remove the entry point at the worst moment.
 *
 * **It never asks for location.** The count only appears if permission was already
 * granted elsewhere, mirroring iOS's `NearbySakhiCountProbe.refreshIfAlreadyAllowed()`.
 */
@Composable
internal fun NearbySakhiButton(
    count: Int?,
    coordinate: DeviceLocation?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = nearbyBadgeCount(count)
    val label = "Nearby Sakhis"
    val value = when {
        shown <= 0 -> ""
        shown == 1 -> "1 nearby"
        else -> "$shown nearby"
    }

    val capsule = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            // Two rings, drawn in this order for a reason, straight from iOS: the white
            // takes the outer band and the hairline then lands on the very edge of it, so
            // the result is a white band holding the capsule in with a defined outline
            // around the whole thing — the way a sticker sits on a photo. One ring alone
            // gave either a soft edge or a hard line with no separation from the page.
            .clip(capsule)
            .background(sakhiSystemBackground())
            .clickable(onClick = onClick),
    ) {
        // Her surroundings behind the capsule, once there is a fix. Falls back to the plain
        // fill with no location, which is also what anyone who never granted it sees.
        if (coordinate != null) {
            NearbyMapThumbnail(
                coordinate = coordinate,
                modifier = Modifier.matchParentSize().clip(capsule),
            )
        }

        Row(
            modifier = Modifier
                // iOS `.padding(.horizontal, 12).padding(.vertical, 7)`.
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    if (value.isNotEmpty()) stateDescription = value
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (shown > 0) {
            NearbyFacePile(count = shown)
            // iOS `HStack(spacing: 7)`.
            Box(modifier = Modifier.width(7.dp))
            Text(
                text = "$shown",
                // iOS `.font(.lato(14, .bold))` with `.foregroundColor(DS.Colors.label)`.
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = sakhiLabel(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.People,
                contentDescription = null,
                // iOS `person.2.fill` at `.system(size: 13, weight: .semibold)`,
                // `DS.Colors.secondaryLabel`.
                tint = sakhiSecondaryLabel(),
                modifier = Modifier.size(13.dp),
            )
        }

        Box(modifier = Modifier.width(7.dp))

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            // iOS draws the disclosure smaller and lighter than the content it follows:
            // `.system(size: 10, weight: .semibold)`, `DS.Colors.secondaryLabel`.
            // Secondary rather than tertiary, which is the first thing to vanish over a
            // busy surface — a disclosure that fades in and out reads as a rendering fault.
                tint = sakhiSecondaryLabel(),
                modifier = Modifier.size(10.dp),
            )
        }

        // Drawn last so they sit over the map, exactly as iOS's two `.overlay(Capsule()
        // .strokeBorder(...))` do.
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(2.5.dp, Color.White, capsule)
                .border(0.5.dp, nearbyCapsuleOutline(), capsule),
        )
    }
}


/**
 * What the badge shows, ported from iOS `SakhiAIChatView.nearbyBadgeCount`.
 *
 * In debug it falls back to a stand-in so the pile can be seen while the header is being
 * designed, without granting location or opening the Emergency flow first. A real count
 * always wins over it, and it falls back on zero as well as null: opening the Emergency
 * screen makes the shared store fetch the real count, and on a test account with nobody
 * nearby that real answer is 0 — coming back, that 0 would replace the stand-in and the
 * avatars would vanish, which looks like dismissing the screen broke the button.
 *
 * **Release builds get the real count only, and this must stay that way.** A made-up number
 * of women nearby, on the button she taps when she needs help, would be the worst kind of
 * thing to be wrong about. Release still hides the pile at 0 and shows the fallback glyph.
 */
private fun nearbyBadgeCount(real: Int?): Int {
    if (real != null && real > 0) return real
    return if (BuildConfig.DEBUG) SAMPLE_NEARBY_COUNT else 0
}

/** iOS `SampleNearbyCount`. */
private const val SAMPLE_NEARBY_COUNT = 3

/** iOS `DS.Colors.opaqueSeparator`, i.e. `UIColor.opaqueSeparator`. */
@Composable
private fun nearbyCapsuleOutline(): Color =
    if (LocalSakhiDarkTheme.current) Color(0xFF38383A) else Color(0xFFC6C6C8)

/**
 * The overlapping discs, ported from iOS `NearbyFacePile`.
 *
 * Rotating which slot each disc sits in is the whole animation: the discs keep their
 * identity and their colour and only their position changes, so they move rather than
 * being redrawn somewhere else.
 */
@Composable
private fun NearbyFacePile(count: Int, modifier: Modifier = Modifier) {
    val shown = count.coerceAtMost(MAX_FACES)
    if (shown <= 0) return

    // Which slot each disc currently sits in.
    var slots by remember(shown) { mutableStateOf(List(shown) { it }) }

    // Rotates every disc forward one slot, forever. Skipped below two discs, where there
    // is nothing to swap with — the pile says the same thing standing still.
    LaunchedEffect(shown) {
        if (shown < 2) return@LaunchedEffect
        while (true) {
            // Every few seconds, not every second: at 1.5s the pile reshuffled often
            // enough to read as activity in the corner of her eye.
            delay(FACE_CYCLE_MILLIS)
            slots = slots.map { (it + 1) % shown }
        }
    }

    Box(
        modifier = modifier
            // Width comes from the slots, not the Box's natural size, which would be one
            // disc wide because every disc is drawn at the same origin before its offset.
            .width(FACE_SIZE + (FACE_SLOT_STEP * (shown - 1)))
            .height(FACE_SIZE),
        contentAlignment = Alignment.CenterStart,
    ) {
        repeat(shown) { index ->
            val slot = slots.getOrElse(index) { index }
            val offsetX by animateDpAsState(
                targetValue = FACE_SLOT_STEP * slot,
                // Linear, on purpose. It suits a cycle: constant speed reads as
                // circulation, where a spring would make each swap look like it lands
                // somewhere and stops.
                animationSpec = tween(durationMillis = 900, easing = LinearEasing),
                label = "nearbyFaceSlot$index",
            )

            Box(
                modifier = Modifier
                    .offset(x = offsetX)
                    // Leading slot on top, so a disc travelling back to the front passes
                    // behind the others instead of sliding over them.
                    .zIndex((shown - slot).toFloat())
                    // Lifts each face off the one behind it, barely. At 24dp anything
                    // heavier stops reading as depth and starts reading as a grey rim.
                    .shadow(1.5.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                    .size(FACE_SIZE)
                    .clip(CircleShape)
                    // Opaque, so the map behind never shows through and shifts the face's
                    // colour as the camera drifts. The faces bring their own colour, so
                    // the tint sits under the artwork at a fraction of its strength.
                    .background(sakhiSystemBackground())
                    .background(FACE_TINTS[index % FACE_TINTS.size].copy(alpha = 0.16f))
                    // Ring in the capsule's own fill, so each disc reads as punched out of
                    // the one behind it rather than outlined on top of it.
                    .border(1.5.dp, sakhiSystemBackground(), CircleShape),
            ) {
                Image(
                    painter = painterResource(FACE_AVATARS[index % FACE_AVATARS.size]),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(FACE_SIZE).clip(CircleShape),
                )
            }
        }
    }
}

private const val MAX_FACES = 3
private const val FACE_CYCLE_MILLIS = 3_000L

/** iOS: 24pt disc. */
private val FACE_SIZE = 24.dp

/** iOS: 24pt disc less the 9pt overlap. */
private val FACE_SLOT_STEP = 15.dp

/** iOS `NearbyFacePile.tints`: systemPurple, systemTeal, systemOrange. */
private val FACE_TINTS = listOf(
    Color(0xFFAF52DE),
    Color(0xFF30B0C7),
    Color(0xFFFF9500),
)

private val FACE_AVATARS = listOf(
    team.sakhi.android.ui.R.drawable.nearby_sakhi_1,
    team.sakhi.android.ui.R.drawable.nearby_sakhi_2,
    team.sakhi.android.ui.R.drawable.nearby_sakhi_3,
)

/**
 * The map behind the capsule, ported from iOS `NearbyMapThumbnail`.
 *
 * Maps SDK in **lite mode**, which is the closest thing Android has to what iOS does here.
 * iOS draws a real MapKit view; lite mode draws a real Google map too, but as a static
 * bitmap in an ordinary View rather than a live `SurfaceView`.
 *
 * That difference is why it is lite and not a full `GoogleMap`: a full map's SurfaceView
 * punches through the Compose layer, so the face pile drawn over it disappeared and the
 * capsule showed nothing but the map's own branding. Lite mode composes normally, costs a
 * fraction as much, and needs no web-service API enabled -- only the Maps SDK the app
 * already uses.
 *
 * Deliberately not near street level, and carrying no marker. iOS spells out why: "The
 * header must not draw her road at legible size for anyone glancing over, however far in it
 * travels." A pin would do exactly that.
 */
@Composable
private fun NearbyMapThumbnail(coordinate: DeviceLocation, modifier: Modifier = Modifier) {
    val target = LatLng(coordinate.latitude, coordinate.longitude)
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(target, NEARBY_MAP_ZOOM)
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        uiSettings = MapUiSettings(
            compassEnabled = false,
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = false,
            myLocationButtonEnabled = false,
            indoorLevelPickerEnabled = false,
        ),
        properties = MapProperties(
            isMyLocationEnabled = false,
            mapStyleOptions = MapStyleOptions(NEARBY_MAP_STYLE),
        ),
    )
}

/**
 * Labels off.
 *
 * At capsule size a single city name renders as large as the whole pill, which is what this
 * looked like before: one enormous word rather than a map. Google keeps labelling at low
 * zooms where MapKit, which iOS uses, does not -- so the parity fix is to turn the text off
 * rather than to keep pulling the camera back.
 *
 * It also happens to be the safer picture: no place name behind her Nearby button for
 * anyone glancing over her shoulder.
 */
private const val NEARBY_MAP_STYLE = """
[
  { "elementType": "labels", "stylers": [{ "visibility": "off" }] },
  { "featureType": "poi", "stylers": [{ "visibility": "off" }] },
  { "featureType": "transit", "stylers": [{ "visibility": "off" }] }
]
"""

/**
 * Wide, and that is the point.
 *
 * iOS rests at a 20 km camera distance, but the right number here is set by how large the
 * labels render inside an 80x38dp capsule, not by matching the metres. At z12 the city name
 * alone filled the whole pill. This pulls back until the map reads as a place rather than as
 * one enormous word -- and it keeps her street well below legible size, which is iOS's own
 * stated rule for this thumbnail.
 */
private const val NEARBY_MAP_ZOOM = 9f
