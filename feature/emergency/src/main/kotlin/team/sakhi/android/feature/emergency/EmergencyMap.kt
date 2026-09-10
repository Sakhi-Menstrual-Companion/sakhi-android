package team.sakhi.android.feature.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import team.sakhi.android.designsystem.LocalSakhiDarkTheme
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSeparator
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.SakhiUIColors
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiLabel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue

/**
 * The map the whole flow sits on, matching iOS.
 *
 * `main` presented every step as a sheet over a live map, and the single most
 * recognisable thing about that screen is that the sheet does **not** dim the map — it
 * stays visible and pannable behind. iOS gets that from an undimmed medium detent; here
 * it comes from a non-modal `BottomSheetScaffold` in [EmergencyFlowScreen].
 *
 * Floating over the map: a back chevron top-left and a "Nearby Sakhis: N" pill
 * top-right, both as the original had them.
 *
 * ── What the pins are ────────────────────────────────────────────────────────
 *
 * Before anyone accepts, the server hands out no coordinates at all — only a bucketed
 * distance and a bearing snapped to 45°. Each pin is placed by walking that bucket along
 * that bearing from *her own* position, so nobody's real location leaves the database.
 * The result is a 45°-wide wedge at least 100 m deep, which is why these are drawn as
 * translucent circles rather than precise markers: a sharp pin would claim an accuracy
 * the data does not have.
 *
 * Inside an accepted session the pin is real, because at that point the server does give
 * both women each other's position.
 */
@Composable
internal fun EmergencyMap(
    userLocation: LatLng?,
    pins: List<EmergencyMapPin>,
    pulseRadiusMeters: Double?,
    modifier: Modifier = Modifier,
    /**
     * How much of the map the sheet covers. The map itself draws full-bleed behind the
     * sheet -- that is what makes the sheet's rounded corners read as rounded -- so the
     * camera has to be told which part is actually visible, or her own position centres
     * underneath the sheet where she cannot see it.
     */
    bottomInset: Dp = 0.dp,
) {
    val context = LocalContext.current
    val isDark = LocalSakhiDarkTheme.current
    // Read once, outside the map body: the renderers want plain ARGB ints, and pulling
    // MaterialTheme inside a `Marker` block is not allowed anyway.
    val accent = MaterialTheme.colorScheme.primary
    val surface = sakhiSystemBackground()
    val onSurface = sakhiLabel()
    val onSurfaceVariant = sakhiSecondaryLabel()
    val separator = sakhiSeparator()

    // Pills are drawn in the current theme's ink, so a theme flip must not reuse them.
    LaunchedEffect(isDark) { EmergencyPinRenderer.clearCache() }

    // Starts on her if she is already known, rather than on Maps' default.
    //
    // `rememberCameraPositionState()` with no argument starts at 0°N 0°E, fully zoomed out:
    // the whole world, centred off West Africa. The first fix then *animated* the camera all
    // the way from there to street level, so opening Emergency showed Europe and North
    // Africa for a beat and then a long zoom down to her -- recorded on Karan's phone.
    val cameraPositionState = rememberCameraPositionState {
        userLocation?.let { position = CameraPosition.fromLatLngZoom(it, MapZoom) }
    }
    var cameraPlaced by remember { mutableStateOf(userLocation != null) }

    LaunchedEffect(userLocation) {
        val target = userLocation ?: return@LaunchedEffect
        val update = CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(target, MapZoom))
        if (cameraPlaced) {
            // She moved: a short glide is right, it is a few streets at most.
            cameraPositionState.animate(update)
        } else {
            // The first fix is a placement, not a journey. Jump straight there.
            cameraPositionState.move(update)
            cameraPlaced = true
        }
    }

    // Held back until it is over her, then faded in. Before the first fix the only thing it
    // could show is that world view, so the sheet's own ground stands in for a moment instead.
    val mapAlpha by animateFloatAsState(
        targetValue = if (cameraPlaced) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "map-fade-in",
    )

    GoogleMap(
        modifier = modifier
            .fillMaxSize()
            .alpha(mapAlpha),
        cameraPositionState = cameraPositionState,
        // Google's own inset mechanism, the same one iOS uses through `GMSMapView.padding`:
        // it shifts the camera's idea of centre rather than shrinking the map, so the
        // tiles still run edge to edge under the sheet.
        contentPadding = PaddingValues(bottom = bottomInset),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
        ),
        // iOS switches the SDK's own dot off and draws `EmergencyUserMarker` instead. Two
        // markers at one coordinate reads as a rendering fault, and the built-in dot cannot
        // carry the "You" label.
        properties = MapProperties(isMyLocationEnabled = false),
    ) {
        if (userLocation != null) {
            Marker(
                state = rememberMarkerState(position = userLocation),
                icon = BitmapDescriptorFactory.fromBitmap(
                    EmergencyUserMarker.bitmap(context, tint = accent.toArgb()),
                ),
                // Centre the dot on the coordinate, with the pill hanging below it: the dot
                // sits in the top 14dp of a 34dp-tall bitmap.
                anchor = Offset(0.5f, 14f / 34f),
                // Her own marker must never intercept a tap meant for a Sakhi's pin behind it.
                zIndex = 0f,
            )
        }

        // The red pulse over her own position, as the original drew it.
        if (userLocation != null && pulseRadiusMeters != null) {
            Circle(
                center = userLocation,
                radius = pulseRadiusMeters,
                fillColor = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.16f),
                strokeColor = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.40f),
                strokeWidth = 2f,
            )
        }

        pins.forEach { pin ->
            if (pin.isApproximate) {
                // A soft disc, not a point. See the note above on why.
                Circle(
                    center = pin.position,
                    radius = APPROXIMATE_PIN_RADIUS_M,
                    fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    strokeWidth = 3f,
                )
            }
            Marker(
                state = rememberMarkerState(position = pin.position),
                title = pin.title,
                icon = BitmapDescriptorFactory.fromBitmap(
                    EmergencyPinRenderer.bitmap(
                        context = context,
                        pin = pin,
                        accent = accent.toArgb(),
                        surface = surface.toArgb(),
                        onSurface = onSurface.toArgb(),
                        onSurfaceVariant = onSurfaceVariant.toArgb(),
                        separator = separator.toArgb(),
                        isDark = isDark,
                    ),
                ),
                // The pointer at the foot of the pin is what sits on the coordinate, not the
                // centre of the bitmap -- same as iOS's `groundAnchor`.
                anchor = Offset(0.5f, 1f),
                zIndex = 1f,
            )
        }
    }
}

/** One thing drawn on the map. */
internal data class EmergencyMapPin(
    val id: String,
    val position: LatLng,
    val title: String?,
    /**
     * The exact spot, second line of the pill. Only ever set inside an accepted session,
     * where it is the point of the pin: "IS IN 2ND FLOOR WASHROOM" is what gets her to the
     * door. Null everywhere else, and the pill then draws as a single-line capsule.
     */
    val spotLabel: String? = null,
    /** True when placed from a bucket and a coarse bearing rather than a real position. */
    val isApproximate: Boolean,
)

private const val APPROXIMATE_PIN_RADIUS_M = 90.0

/**
 * Walks [metres] along [bearingDegrees] from this point, on a sphere.
 *
 * Mirrors `CLLocationCoordinate2D.offset(metres:bearingDegrees:)` in
 * `EmergencyPinRenderer.swift` exactly, so a pin lands in the same place on both
 * platforms. Worth moving into SakhiCore alongside the other Emergency geometry the next
 * time the framework is rebuilt — it is shared behaviour living in two places.
 */
internal fun LatLng.offset(metres: Double, bearingDegrees: Double): LatLng {
    val earthRadius = 6_371_000.0
    val angular = metres / earthRadius
    val bearing = Math.toRadians(bearingDegrees)
    val lat1 = Math.toRadians(latitude)
    val lon1 = Math.toRadians(longitude)

    val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
    val lon2 = lon1 + atan2(
        sin(bearing) * sin(angular) * cos(lat1),
        cos(angular) - sin(lat1) * sin(lat2),
    )

    return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

/**
 * The controls floating over the map: back on the left, Contact Police on the right.
 *
 * There used to be a "Nearby Sakhis: N" pill opposite the back button. iOS has no such pill
 * and neither does Figma `13 · Emergency Assistance` -- it was carried over from `main`, and
 * Karan spotted it on a device. What iOS *does* put in that corner, and Android was missing
 * entirely, is the way out that does not depend on anyone answering.
 */
@Composable
internal fun EmergencyMapOverlay(
    onBack: () -> Unit,
    onContactPolice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Full width even though there is one control in it. The caller aligns this to
    // `TopCenter`, so a Row that wraps its content parked the back button in the middle of
    // the map the moment the count pill beside it was removed.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Figma `map control · back`: a 35 white disc with a 1/4 black-at-16% shadow and a
        // 17 pink chevron. It was a dark scrim disc with a white arrow, carried over from
        // `main`; against a light map that read as a system control dropped onto the
        // screen rather than one of this flow's own.
        Surface(
            shape = CircleShape,
            color = sakhiSystemBackground(),
            shadowElevation = 2.dp,
            modifier = Modifier.size(35.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(35.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.emergency_back),
                    tint = SakhiUIColors.BRAND_PINK.toComposeColor(),
                    modifier = Modifier.size(17.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // "Contact Police" -- the way out of this flow that does not depend on anyone
        // answering. Port of iOS `EmergencyMapScreen.contactPoliceButton`: a 38dp white
        // capsule, 14 inset, a 14pt pink handset and the words in Lato 15 bold.
        //
        // Present on every step, including while she is waiting on a Sakhi, because the
        // moment she needs this is not a moment to go looking for it.
        Surface(
            shape = CircleShape,
            color = sakhiSystemBackground(),
            shadowElevation = 2.dp,
            modifier = Modifier.height(38.dp),
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onContactPolice)
                    .padding(horizontal = 14.dp)
                    .semantics {
                        contentDescription = "Contact Police"
                        stateDescription = "Calls $PoliceEmergencyNumber, the emergency number"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = SakhiUIColors.BRAND_PINK.toComposeColor(),
                )
                Text(
                    text = stringResource(R.string.emergency_contact_police),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = sakhiLabel(),
                )
            }
        }
    }
}

/**
 * India's single emergency number.
 *
 * 112 rather than 100: it is the unified number, it reaches the police, it works from a
 * locked phone and across every state, which 100 does not reliably do. The app's own safety
 * guidance already names it.
 */
internal const val PoliceEmergencyNumber = "112"

/** Street level: close enough to read the roads around her, far enough to see the next one. */
private const val MapZoom = 15f

