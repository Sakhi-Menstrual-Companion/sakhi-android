package team.sakhi.android.feature.emergency

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.MapsComposeExperimentalApi
import kotlin.math.cos
import kotlin.math.sin
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import team.sakhi.android.designsystem.sakhiButtonFill
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.SakhiUIColors
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
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
import team.sakhi.android.designsystem.AppleSystemColors
import team.sakhi.android.designsystem.SakhiTokens
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import team.sakhi.emergency.EmergencyHomeSignal

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
fun NearbySakhiButton(
    count: Int?,
    coordinate: DeviceLocation?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = nearbyBadgeCount(count)
    val mapCoordinate = sampleCoordinateOrNull(coordinate)
    val label = "Nearby Sakhis"
    val value = when {
        shown <= 0 -> ""
        shown == 1 -> "1 nearby"
        else -> "$shown nearby"
    }

    val capsule = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            // The capsule's OWN size, which the oversized map behind it must not change.
            // See the `matchParentSize` note below.
            .defaultMinSize(minWidth = NEARBY_CAPSULE_MIN_WIDTH, minHeight = NEARBY_CAPSULE_HEIGHT)
            // Two rings, drawn in this order for a reason, straight from iOS: the white
            // takes the outer band and the hairline then lands on the very edge of it, so
            // the result is a white band holding the capsule in with a defined outline
            // around the whole thing — the way a sticker sits on a photo. One ring alone
            // gave either a soft edge or a hard line with no separation from the page.
            .clip(capsule)
            .background(sakhiSystemBackground()),
        // The click deliberately does NOT live here. See the overlay at the end of this
        // Box for why.
    ) {
        // Her surroundings behind the capsule, once there is a fix. Falls back to the plain
        // fill with no location, which is also what anyone who never granted it sees.
        if (mapCoordinate != null) {
            // The oversized map is held inside a `matchParentSize` box, and that is load
            // bearing.
            //
            // `requiredSize` ignores the parent's constraints, but the child still REPORTS
            // its size, so a Box sizes itself to hold it. That made this capsule measure
            // 308dp wide (220 + 2 x 44 of overscan) and 126dp tall instead of 220 x 38,
            // while `clip` kept it LOOKING right, because clip changes drawing and not
            // layout. In the AI chat header, where this sits in a Row beside a
            // `weight(1f)` title, those extra 88dp left the title about 40dp wide and it
            // rendered one letter per line (reported live: "ai sheet ... pura he khrab ho
            // rakha hai"). The same silent inflation applied anywhere else this capsule is
            // laid out next to something.
            //
            // A `matchParentSize` child takes the parent's size and does NOT contribute to
            // it, and `wrapContentSize(unbounded = true)` then lets the map be bigger than
            // that box without pushing it. So the overscan still hides Google's pinned
            // attribution, and layout sees only the capsule.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .wrapContentSize(align = Alignment.Center, unbounded = true),
            ) {
                NearbyMapThumbnail(
                    coordinate = mapCoordinate,
                    faceCount = shown,
                    // Same overscan as the circle: the Google logo is pinned to the map
                    // view's bottom-left, so the view has to be bigger than the shape
                    // clipping it.
                    modifier = Modifier.requiredSize(
                        width = NEARBY_CAPSULE_MIN_WIDTH + MAP_ATTRIBUTION_OVERSCAN * 2,
                        height = NEARBY_CAPSULE_HEIGHT + MAP_ATTRIBUTION_OVERSCAN * 2,
                    ),
                )
            }
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
        }
        // No count yet, or nobody around: the map and the chevron carry the capsule on
        // their own. A `person.2.fill` stood in for the faces here and over live map tiles
        // it read as a missing-image icon rather than as people, so iOS dropped it. Only
        // the glyph went, not the control: the screen it opens is the one offering the
        // nearest washroom or hospital, which is exactly what she needs when nobody is
        // around.

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
        //
        // This overlay also carries the click, and that is not a style choice.
        //
        // NearbyMapThumbnail is a GoogleMap, and a map consumes the touch itself: in lite
        // mode its built-in tap handler launched the Google Maps app, and as a full map it
        // simply eats the event. None of the MapUiSettings gesture flags turn that off,
        // because it is not a gesture. Being a child of this Box, the map is hit tested
        // before any clickable on the parent, so it swallowed the tap.
        //
        // Putting the click on the topmost child fixes it: this Box is hit first, consumes
        // the tap, and the map never sees one.
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(2.5.dp, Color.White, capsule)
                .border(0.5.dp, nearbyCapsuleOutline(), capsule)
                .clickable(onClick = onClick),
        )
    }
}


/**
 * iOS `HomeNearbyButton`: the 46dp circle in the calendar sheet's bottom bar.
 *
 * A different control from [NearbySakhiButton] above, and the distinction is the whole
 * point. That one is the wide capsule in the Sakhi AI chat header -- faces, a count and a
 * chevron. This is the circle that sits in the action bar beside the Ask field and the log
 * button, where the map itself is the button and there is no room for a number next to the
 * faces. Putting the capsule in the circle's slot is what made the bar look wrong.
 *
 * Same two rings as the capsule: a white band holding the map in, then a hairline defining
 * the outer edge, so it sits on the page like a sticker rather than a hole cut in it.
 */
@Composable
fun HomeNearbyCircleButton(
    count: Int?,
    coordinate: DeviceLocation?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * What is live for her right now. Drives the ring: red and blinking with a badge when
     * someone has asked her, steady green once she is in a session. See
     * [team.sakhi.emergency.EmergencyHomeSignal].
     */
    signal: EmergencyHomeSignal = EmergencyHomeSignal.None,
) {
    val shown = nearbyBadgeCount(count)
    val mapCoordinate = sampleCoordinateOrNull(coordinate)
    val value = when {
        shown <= 0 -> ""
        shown == 1 -> "1 nearby"
        else -> "$shown nearby"
    }

    // The blink, shared by the ring and the badge so they pulse as one thing. Only runs while
    // someone is actually waiting on her; an infinite transition left running at idle would
    // redraw the button for ever for nothing.
    val incoming = signal as? EmergencyHomeSignal.Incoming
    val pulse = if (incoming != null) {
        val transition = rememberInfiniteTransition(label = "incoming-pulse")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.28f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "incoming-alpha",
        ).value
    } else {
        1f
    }

    // Unclipped, so the badge can sit on the ring's edge rather than inside the circle.
    Box(modifier = modifier) {
    Box(
        modifier = Modifier
            .size(NEARBY_CIRCLE_DIAMETER)
            .clip(CircleShape)
            .background(if (mapCoordinate == null) sakhiButtonFill() else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (mapCoordinate != null) {
            NearbyMapThumbnail(
                coordinate = mapCoordinate,
                faceCount = shown,
                // Drawn larger than the circle that clips it.
                //
                // The Maps SDK pins the Google logo to the bottom-left of its own view and
                // there is no API to move or hide it. In a 46dp circle it lands inside the
                // clip and reads as a smear across the bottom of the button.
                // `requiredSize` ignores the parent's constraints, so the view is genuinely
                // bigger and the logo sits outside the visible area.
                //
                // Be clear what this is: the attribution still renders, it is just off
                // screen, and Google's terms ask that it stay visible. Set the overscan to
                // 0.dp and it comes straight back.
                modifier = Modifier.requiredSize(NEARBY_CIRCLE_DIAMETER + MAP_ATTRIBUTION_OVERSCAN * 2),
            )
        }

        if (mapCoordinate == null && shown > 0) {
            // The pile only stands in for the map.
            //
            // With tiles loading, the faces are already out on the map as markers and the
            // camera tours between them, so a second copy of the same three faces sitting
            // still on top just covered the thing that moves. It stays for the case where
            // there is no map to show, which is the only time it was carrying the meaning.
            NearbyFacePile(count = shown)
        } else if (mapCoordinate == null) {
            // Only when there is no map behind it. Over tiles the button needs no glyph:
            // iOS removed the `person.2.fill` that used to sit here because it read as a
            // missing image. A pin, because with no fix and no count this control offers
            // the places around her, not the women.
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = SakhiUIColors.BRAND_PINK.toComposeColor(),
                modifier = Modifier.size(15.dp),
            )
        }

        // Rings AND the tap target, drawn last so they sit over the map tiles.
        //
        // The click cannot live on the container. A non-lite `GoogleMap` is an AndroidView
        // and it consumes the touch before the parent Box ever sees it, so the button was
        // completely dead once the map started rendering. Compose hit-tests the topmost
        // child first, so putting `clickable` on this final overlay gets the tap back.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = "Nearby Sakhis"
                    if (value.isNotEmpty()) stateDescription = value
                }
                .then(
                    when (signal) {
                        // Someone has asked her. Thick, red, and blinking -- the one state on
                        // Home that is somebody else waiting on her.
                        is EmergencyHomeSignal.Incoming -> Modifier.border(
                            SIGNAL_RING_WIDTH,
                            IncomingRed.copy(alpha = pulse),
                            CircleShape,
                        )
                        // Connected. Same weight, steady: nothing is being asked of her, she
                        // just has somewhere to be.
                        EmergencyHomeSignal.Connected -> Modifier.border(
                            SIGNAL_RING_WIDTH,
                            ConnectedGreen,
                            CircleShape,
                        )
                        EmergencyHomeSignal.None -> Modifier
                            .border(2.5.dp, Color.White, CircleShape)
                            .border(0.5.dp, nearbyCapsuleOutline(), CircleShape)
                    },
                ),
        )
    }

    // The count, sitting on the ring at one o'clock. It blinks with the ring, and it carries
    // a white edge so it reads as a badge on the button rather than a red blot beside it.
    if (incoming != null) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
                .padding(2.dp)
                .clip(CircleShape)
                .background(IncomingRed.copy(alpha = 0.55f + 0.45f * pulse)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (incoming.count > 9) "9+" else incoming.count.toString(),
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
    }
}

/** iOS `HomeNearbyButton.diameter` -- matches the log button beside it. */
private val NEARBY_CIRCLE_DIAMETER = 46.dp

/** Thicker than the 2.5dp idle ring, so an alert reads from across the screen. */
private val SIGNAL_RING_WIDTH = 3.5.dp

/** iOS `systemRed`. The alert colour, not the brand pink the idle button already uses. */
private val IncomingRed = Color(0xFFFF3B30)

/** iOS `systemGreen` -- the same "all good" green the session screen uses. */
private val ConnectedGreen = Color(0xFF34C759)

/**
 * How far the map is drawn beyond the shape clipping it, per side.
 *
 * 44 clears the Google logo and the Terms link at every size this is used at. Matches the
 * `attributionOverscan` iOS uses for the same reason with MapKit's Apple logo.
 */
private val MAP_ATTRIBUTION_OVERSCAN = 44.dp

/** Big enough to cover the capsule at any label width, before the clip trims it back. */
private val NEARBY_CAPSULE_MIN_WIDTH = 220.dp
private val NEARBY_CAPSULE_HEIGHT = 38.dp

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
private const val SAMPLE_NEARBY_COUNT = 10

/**
 * Stand-in position, so the map draws while the button is being looked at.
 *
 * iOS has had `SampleNearbyCoordinate` all along and Android had only the count fallback.
 * The effect was that with no fix the thumbnail drew nothing at all, so the button showed a
 * plain fill with the face pile cycling over it -- which looks exactly like "the map
 * animation was never implemented". Greater Noida, same point iOS uses.
 *
 * Debug only. A real fix always wins, and release draws no map rather than a made-up place.
 */
private fun sampleCoordinateOrNull(real: DeviceLocation?): DeviceLocation? {
    if (real != null) return real
    return if (BuildConfig.DEBUG) DeviceLocation(28.4595, 77.0266) else null
}

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
                    .background(faceTints.let { it[index % it.size] }.copy(alpha = 0.16f))
                    // Ring in the capsule's own fill, so each disc reads as punched out of
                    // the one behind it rather than outlined on top of it.
                    .border(1.5.dp, sakhiSystemBackground(), CircleShape),
            ) {
                Image(
                    painter = painterResource(FACE_AVATARS[index % FACE_AVATARS.size]),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(FACE_SIZE)
                        .clip(CircleShape)
                        // Scale then clip, the same order iOS uses: the enlarged head is
                        // cropped by the circle rather than overflowing it.
                        .scale(FACE_CONTENT_SCALES[index % FACE_CONTENT_SCALES.size]),
                )
            }
        }
    }
}

// iOS `NearbyMapThumbnail.maxFaces`. Ten, not three: the camera tours between the faces,
// and three points on the ring are so far apart that every leg reads as a zoom rather than
// a journey. Ten short hops read as one continuous orbit.
private const val MAX_FACES = 10
private const val FACE_CYCLE_MILLIS = 3_000L

/** iOS: 24pt disc. */
private val FACE_SIZE = 24.dp

/** iOS: 24pt disc less the 9pt overlap. */
private val FACE_SLOT_STEP = 15.dp

/**
 * iOS `NearbyFacePile.tints`: the five SakhiCore section colours.
 *
 * They used to be Apple's purple, teal and orange, which is generic iOS rather than Sakhi
 * and is why the faces read as belonging to someone else's app. iOS moved to the section
 * tokens for exactly that reason, and there are five of them for five avatars.
 *
 * Still a `@Composable` getter: a top-level `val` has nowhere to read the theme from, so
 * the pile could not follow dark mode.
 */
private val faceTints: List<Color>
    @Composable get() = listOf(
        SakhiTokens.SectionRose,
        SakhiTokens.SectionMood,
        SakhiTokens.SectionBlue,
        SakhiTokens.SectionAmber,
        SakhiTokens.SectionGreen,
    )

private val FACE_AVATARS = listOf(
    team.sakhi.android.ui.R.drawable.nearby_sakhi_1,
    team.sakhi.android.ui.R.drawable.nearby_sakhi_2,
    team.sakhi.android.ui.R.drawable.nearby_sakhi_3,
    team.sakhi.android.ui.R.drawable.nearby_sakhi_4,
    team.sakhi.android.ui.R.drawable.nearby_sakhi_5,
)

/**
 * iOS `NearbyFacePile.contentScales`.
 *
 * The artwork ships with a wide transparent margin and the margin differs per file, so
 * drawn raw the head covers roughly half the disc and reads as a faded avatar rather than
 * a small one. Measured content boxes: 830x812 in 1242, 590x893 in 1242, 729x857 in 1346,
 * and about 400x397 in 512. Each factor sizes the head's longest edge to the disc.
 */
private val FACE_CONTENT_SCALES = listOf(1.50f, 1.39f, 1.57f, 1.28f, 1.29f)

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
@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun NearbyMapThumbnail(
    coordinate: DeviceLocation,
    faceCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val target = LatLng(coordinate.latitude, coordinate.longitude)
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(target, NEARBY_MAP_WIDE_ZOOM)
    }

    val shown = faceCount.coerceIn(0, MAX_FACES)
    val facePoints = remember(target, shown) { ringPoints(target, shown) }

    // Ported line for line from iOS `NearbyMapThumbnail.startDrift`.
    var mapLoaded by remember { mutableStateOf(false) }

    // Which face the camera is currently resting on, so it can be lifted while visited.
    // iOS `focusedIndex`.
    var focusedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(mapLoaded, facePoints) {
        if (!mapLoaded || facePoints.size < 2) return@LaunchedEffect

        // iOS: "The map has no size on the first pass, and a camera move issued then is
        // simply lost -- which looked like the animation never running at all."
        delay(700)

        // The opening approach, from the wide frame down onto the first face. Only happens
        // once; from here the arrival is the end of each leg.
        var index = 0
        focusedIndex = index
        runCatching {
            camera.animate(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.fromLatLngZoom(facePoints[index], orbitZoom(index, facePoints.size)),
                ),
                NEARBY_APPROACH_MILLIS,
            )
        }

        while (isActive) {
            val next = (index + 1) % facePoints.size

            // Fire and do NOT await. This is the whole trick, and it is what iOS does:
            // `withAnimation` returns immediately and the sleep below is `legDuration -
            // legOverlap`, so the next leg is issued 80ms BEFORE this one lands. The camera
            // is retargeted while still moving, which is why iOS reads as one continuous
            // orbit. Awaiting the animation instead lets it come to rest every single leg,
            // which is the stop-start you were seeing.
            launch {
                runCatching {
                    camera.animate(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.fromLatLngZoom(
                                facePoints[next],
                                orbitZoom(next, facePoints.size),
                            ),
                        ),
                        NEARBY_LEG_MILLIS,
                    )
                }
            }
            focusedIndex = next

            // Always a real suspension, so this loop can never busy-spin the main thread
            // the way the first attempt did.
            delay((NEARBY_LEG_MILLIS - NEARBY_LEG_OVERLAP_MILLIS).toLong())
            index = next
        }
    }

    val isDarkMap = LocalSakhiDarkTheme.current
    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        onMapLoaded = { mapLoaded = true },
        // NOT lite mode: a lite map is a static bitmap and its camera cannot move at all,
        // so iOS's pan and zoom between the faces is impossible there.
        googleMapOptionsFactory = { GoogleMapOptions() },
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
            // Dark tiles under the dark theme. The light ones were a bright white disc in
            // the dark Home bar, the loudest thing on the screen for no reason.
            mapStyleOptions = MapStyleOptions(
                if (isDarkMap) mergeMapStyles(DarkMapStyle, NEARBY_MAP_STYLE) else NEARBY_MAP_STYLE,
            ),
        ),
    ) {
        // The same faces the pile in front shows, out on the map the camera flies between.
        // iOS draws these as `Annotation`s at each ring point, so the woman in the pile and
        // the woman the camera settles on are recognisably the same person.
        facePoints.forEachIndexed { index, point ->
            val isFocused = focusedIndex == index
            // iOS: grows a little as the camera settles on it, so the tour has a subject
            // rather than just a destination. Small on purpose -- at 15dp anything larger
            // reads as a popping animation instead of focus.
            val scale by animateFloatAsState(
                targetValue = if (isFocused) 1.28f else 1f,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 90f),
                label = "face-focus",
            )
            MarkerComposable(
                keys = arrayOf(index, isFocused),
                state = rememberMarkerState(position = point),
                anchor = Offset(0.5f, 0.5f),
            ) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(sakhiSystemBackground()),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(FACE_AVATARS[index % FACE_AVATARS.size]),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // One `graphicsLayer` doing both, not `.clip()` then `.scale()`.
                        //
                        // Those are two separate layers: the clip applies to the unscaled
                        // content and the scale then blows the result past it, so the face
                        // rendered as a square. Setting `clip` and `shape` inside the same
                        // layer that does the scaling makes the circle the last thing
                        // applied, which is the order iOS gets from `.scaleEffect` followed
                        // by `.clipShape`.
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer {
                                val f = FACE_CONTENT_SCALES[index % FACE_CONTENT_SCALES.size]
                                scaleX = f
                                scaleY = f
                                clip = true
                                shape = CircleShape
                            },
                    )
                }
            }
        }
    }
}

/**
 * Evenly spaced points on a ring around her, computed rather than listed so the spacing
 * stays even at any count from one to ten.
 *
 * The radius is NOT iOS's 3,200 m. iOS drives its camera by altitude in metres, Android by
 * zoom level, and at the zoom this thumbnail sits on the visible span is only a few hundred
 * metres across -- a 3.2 km ring would put every face off screen and the camera would tour
 * empty tiles. This is the same motion, recalibrated to Android's projection.
 */
private fun ringPoints(centre: LatLng, count: Int): List<LatLng> {
    if (count <= 0) return emptyList()
    val metresPerDegreeLat = 111_320.0
    return (0 until count).map { i ->
        val bearing = Math.toRadians(90.0 + (360.0 / count) * i)
        val dLat = (NEARBY_RING_METRES * sin(bearing)) / metresPerDegreeLat
        val dLon = (NEARBY_RING_METRES * cos(bearing)) /
            (metresPerDegreeLat * cos(Math.toRadians(centre.latitude)))
        LatLng(centre.latitude + dLat, centre.longitude + dLon)
    }
}

/**
 * The orbit zoom at one face: a single cosine spread over the whole lap, so the camera
 * rises and falls once per circuit rather than once per hop. iOS `orbitDistance(at:of:)`.
 */
private fun orbitZoom(index: Int, count: Int): Float {
    if (count <= 1) return NEARBY_ORBIT_ZOOM
    val phase = (2 * Math.PI * index) / count
    return (NEARBY_ORBIT_ZOOM + NEARBY_ORBIT_BREATH * cos(phase)).toFloat()
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
/**
 * Street level, so the thumbnail actually shows where she is.
 *
 * This was 9f, which is roughly a hundred kilometres across. Inside a capsule barely
 * 38dp tall that is not a map of her surroundings, it is a beige smudge, and it made the
 * whole pill look broken rather than informative.
 *
 * 9f was never a design choice. The comment on NEARBY_MAP_STYLE explains it: Google keeps
 * drawing labels at low zooms where MapKit does not, and the camera kept getting pulled
 * back to shake them off. Turning the labels off in the style was the real fix, but the
 * pulled-back zoom was left behind. With labels already off, the camera can come back in.
 */
private const val NEARBY_MAP_ZOOM = 15f

/** Where the camera opens, before its first approach onto the ring. */
private const val NEARBY_WIDE_ZOOM_UNUSED = 0f
private const val NEARBY_MAP_WIDE_ZOOM = 13.9f

/** The orbit zoom, and how far it drifts either side of that over one full lap. */
private const val NEARBY_ORBIT_ZOOM = 14.6f
private const val NEARBY_ORBIT_BREATH = 0.35

/** How far each decorative face sits from centre. See [ringPoints] for why not 3,200 m. */
private const val NEARBY_RING_METRES = 250.0

/** One hop, face to face. Ten of these make a lap of about 26 seconds. iOS: 2.6s. */
private const val NEARBY_LEG_MILLIS = 2_600

/** The opening move, from the wide frame down onto the first face. iOS: 3.4s. */
private const val NEARBY_APPROACH_MILLIS = 3_400

/**
 * The next leg is started this much before the current one ends. iOS `legOverlap` = 0.08s.
 *
 * Without it every leg runs to completion and the camera comes to rest before the next
 * instruction lands, which is the stutter at each face.
 */
private const val NEARBY_LEG_OVERLAP_MILLIS = 80
