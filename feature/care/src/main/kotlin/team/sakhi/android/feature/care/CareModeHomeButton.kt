package team.sakhi.android.feature.care

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiLightPink
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import team.sakhi.android.platform.StayWithMeLocationService
import team.sakhi.session.SessionManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.AppleSystemColors
import team.sakhi.android.designsystem.sakhiButtonFill
import team.sakhi.android.designsystem.sakhiSeparator
import team.sakhi.care.CareRuntimeState
import team.sakhi.care.CareStore
import team.sakhi.staywithme.StayWithMePhase
import team.sakhi.staywithme.StayWithMeStore

/**
 * The leading control in the calendar's bottom bar: where she is, and the way into the walk.
 *
 * iOS's `HomeNearbyButton`, which Karan moved back into this slot on 2026-09-14: a 46 circle
 * holding a live map of where she is with her own face over it, a white band and a hairline
 * around it so it sits on the page like a sticker, and the walk's ring over that when one is
 * running. Care moved to Home's top right the same day, and this stopped being the Care
 * button; Android kept drawing the two faces here and opening Care, which left the two
 * phones meaning different things by the same corner.
 *
 * It is the only place on Home that can turn orange or red, and that means one thing only: a
 * walk is past its time. Never that somebody else needs something from her.
 */
@Composable
fun CareModeHomeButton(
    /** The walk: hers to start or follow, or the one she is watching. */
    onOpenWalk: () -> Unit,
    /** Care, for anyone with no walk to open and nobody staying with her yet. */
    onOpenCare: () -> Unit,
    /** Her person, with no walk live: the sheet where they can ask her to stay with them. */
    onOpenStayAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val careStore = koinInject<CareStore>()
    val stayWithMeStore = koinInject<StayWithMeStore>()
    val sessionManager = koinInject<SessionManager>()
    val careState by careStore.careState.collectAsStateWithLifecycle()
    val mine by stayWithMeStore.mine.collectAsStateWithLifecycle()
    val watching by stayWithMeStore.watching.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Home is where she looks first, so it cannot wait for her to open Care Mode to find
    // out a walk is live. After the app is killed and reopened the store is empty, which
    // left this button blank and, worse, left her walk sharing nothing until she happened
    // to open the sheet. Ask while Home is on screen, and pick the sharing back up.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                sessionManager.current?.userId?.let { userId ->
                    runCatching { stayWithMeStore.refresh(userId) }
                    stayWithMeStore.checkLate()
                    if (stayWithMeStore.mine.value != null &&
                        StayWithMeLocationService.hasLocationPermission(context)
                    ) {
                        StayWithMeLocationService.start(context)
                    }
                }
                delay(HOME_WALK_REFRESH_MS)
            }
        }
    }

    val name = when (val state = careState) {
        is CareRuntimeState.OwnerConnected -> state.partnership.partnerName
        is CareRuntimeState.PartnerConnected -> state.partnership.partnerName
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }

    // The two of them, not one initial. A single letter in a circle is an account badge, and
    // this button is not an account: it is the one person who is looking out for her. Both
    // faces are the same five stand-ins the connection screen and Emergency use, picked by
    // the same hash of the user id, so a person's face is the same wherever she sees it.
    val partnership = when (val state = careState) {
        is CareRuntimeState.OwnerConnected -> state.partnership
        is CareRuntimeState.PartnerConnected -> state.partnership
        else -> null
    }
    val selfUserId = sessionManager.current?.userId
    val pair = partnership?.let { p ->
        val otherId = if (p.userId == selfUserId) p.partnerId else p.userId
        CareAvatars.selfIndex(context, selfUserId.orEmpty()) to CareAvatars.indexFor(otherId)
    }

    // Where she is, for the tiles. The fix the system already has, never a new request:
    // this is a picture behind a button, not a reason to wake the GPS.
    var here by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(mine?.id, watching?.id) {
        here = (mine ?: watching)?.lastLocation?.let { it.latitude to it.longitude }
            ?: StayWithMeLocationService.lastKnownLatLng(context)
    }

    val walk = mine ?: watching
    // iOS's `onOpenSakhi`: the walk screen when one is live or someone is connected, Care for
    // a person with nobody yet. Her person, with no walk live, lands on the screen where they
    // can ask to stay with her (Karan, 2026-09-19).
    val onOpen = {
        when {
            walk != null || careState is CareRuntimeState.OwnerConnected -> onOpenWalk()
            careState is CareRuntimeState.PartnerConnected -> onOpenStayAsk()
            else -> onOpenCare()
        }
    }
    // The ring moves with her time, so this has to redraw on its own. Every half minute is
    // enough to see it move and costs nothing.
    var now by remember { mutableStateOf(stayWithMeStore.now()) }
    LaunchedEffect(walk?.id) {
        while (walk != null) {
            now = stayWithMeStore.now()
            delay(RING_TICK_MS)
        }
    }
    val phase = walk?.phase(now)
    val late = phase == StayWithMePhase.LATE || phase == StayWithMePhase.GRACE
    // How much of her time has gone. The ring fills toward home as it does.
    val progress = walk?.progress(now)?.toFloat()?.coerceIn(0f, 1f) ?: 0f

    val pulse = rememberInfiniteTransition(label = "careButton")
    val blink by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "careButtonBlink",
    )

    // A ring only while a walk is on. At rest the button has none: the grey hairline it used
    // to wear made a quiet button look like a disabled one, next to a solid pink log button.
    //
    // During a walk it is two rings in one: a faint full circle, and over it a solid slice
    // that fills clockwise from the top as her time goes, so a glance at Home says how far
    // into the walk she is.
    //
    // The colours are iOS's `HomeNearbyButton.rideRing`, not a set of Android's own: Sakhi's
    // pink while she is walking, orange once she is past her time, red once her person has
    // been told. Green was never one of Sakhi's colours, and it said "fine" in a palette
    // where pink already does.
    val hairlineColor = sakhiSeparator()
    val ringColor = when (phase) {
        StayWithMePhase.GRACE -> RideStyle.late
        StayWithMePhase.LATE -> RideStyle.alert
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .size(46.dp)
            .background(
                if (pair != null) {
                    androidx.compose.ui.graphics.lerp(sakhiLightPink(), sakhiSystemBackground(), 0.45f)
                } else {
                    sakhiButtonFill()
                },
                CircleShape,
            )
            .clip(CircleShape)
            .alpha(if (late) blink else 1f)
            .drawWithContent {
                drawContent()
                // The white band that holds the map in, then either the hairline or the ride's
                // ring: iOS's `HomeNearbyButton`, so the button sits on the page like a sticker.
                val band = 2.5.dp.toPx()
                drawCircle(
                    color = Color.White,
                    radius = (size.minDimension - band) / 2f,
                    style = Stroke(width = band),
                )
                if (walk == null) {
                    val hair = 0.5.dp.toPx()
                    drawCircle(
                        color = hairlineColor,
                        radius = (size.minDimension - hair) / 2f,
                        style = Stroke(width = hair),
                    )
                }
                if (walk != null) {
                    val stroke = 4.dp.toPx()
                    val arcTopLeft = Offset(stroke / 2, stroke / 2)
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = ringColor.copy(alpha = 0.22f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        // Never nothing: iOS keeps a two percent tick showing so a walk that
                        // has just started still reads as a walk.
                        sweepAngle = 360f * progress.coerceAtLeast(0.02f),
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            .clickable(onClick = onOpen)
            .semantics { contentDescription = name ?: "Care Mode" },
        contentAlignment = Alignment.Center,
    ) {
        // The map under everything, always. With a fix it is her own surroundings; without
        // one it is the stand-in, so the button is a map rather than an empty disc before
        // anyone has been asked for location (iOS `HomeNearbyButton`, Karan 2026-09-24).
        //
        // What keeps the stand-in honest is that nothing on it is offered as her: no user
        // dot, no faces planted on it as map markers, and it does not move. It reads as the
        // texture behind a button, not a picture of the street she is standing on. If a
        // marker is ever added here, that reasoning goes with it.
        NearbyMapThumbnail(
            latitude = here?.first ?: STAND_IN_LATITUDE,
            longitude = here?.second ?: STAND_IN_LONGITUDE,
            zoom = if (here != null) LIVE_MAP_ZOOM else STAND_IN_MAP_ZOOM,
            // Only her real surroundings tour, and only once she has someone. The stand-in
            // never moves and never carries a marker.
            tourFaces = pair.takeIf { here != null },
            ringColor = MaterialTheme.colorScheme.primary.toArgb(),
            modifier = Modifier.matchParentSize().clip(CircleShape),
        )
        // A lite-mode map hands every tap to the Google Maps app, so the button's own
        // click never fires. This sits over it and takes the tap first.
        Box(Modifier.matchParentSize().clickable(onClick = onOpen))
        // Only before there is a fix. Once the map is her real surroundings iOS draws
        // nothing over it, because the tiles are the button in that state.
        if (here == null) {
            when {
                // The two of them are what this button is about, and two faces say nothing
                // at all about where she is standing.
                pair != null -> Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    HomeButtonFace(avatarIndex = pair.first)
                    HomeButtonFace(avatarIndex = pair.second)
                }
                name != null -> Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> Icon(
                    imageVector = Icons.Filled.PersonAddAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * The map tiles behind the button: where she is, and the two of them on it.
 *
 * With [tourFaces] it does what iOS's `HomeMapThumbnail` does: settles onto one face, lifts,
 * travels to the other, settles again, and round for as long as the button is on screen. The
 * markers are `faceMarkerBitmap`, the same marker the walk itself draws, so a face means the
 * same thing in both places.
 *
 * Without [tourFaces] it stays in lite mode: a still picture of a street corner, which is all
 * the stand-in is and all a button needs when there is nobody to tour. Lite mode cannot move
 * a camera at all, so the tour is the one reason to pay for a real map view here.
 */
@Composable
private fun NearbyMapThumbnail(
    latitude: Double,
    longitude: Double,
    zoom: Float,
    modifier: Modifier = Modifier,
    tourFaces: Pair<Int, Int>? = null,
    ringColor: Int = 0,
) {
    val position = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), zoom)
    }
    val facePoints = remember(latitude, longitude, tourFaces) {
        tourFaces?.let {
            listOf(
                offsetMetres(latitude, longitude, FACE_RADIUS_METRES, bearingDegrees = 90.0),
                offsetMetres(latitude, longitude, FACE_RADIUS_METRES, bearingDegrees = 210.0),
            )
        }.orEmpty()
    }

    // Only while the button is actually on screen. A camera animating behind a backgrounded
    // Home is a frame budget and a battery spent on something nobody is looking at.
    val lifecycleOwner = LocalLifecycleOwner.current
    if (facePoints.size == 2) {
        LaunchedEffect(facePoints) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var index = 0
                while (true) {
                    // Settle onto this one.
                    position.animate(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.fromLatLngZoom(facePoints[index], TOUR_CLOSE_ZOOM),
                        ),
                        TOUR_ZOOM_IN_MS,
                    )
                    delay(TOUR_HOLD_MS.toLong())
                    // Lift, so the next leg reads as travel rather than a cut.
                    position.animate(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.fromLatLngZoom(facePoints[index], TOUR_MID_ZOOM),
                        ),
                        TOUR_ZOOM_OUT_MS,
                    )
                    val next = (index + 1) % facePoints.size
                    position.animate(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.fromLatLngZoom(facePoints[next], TOUR_MID_ZOOM),
                        ),
                        TOUR_TRAVEL_MS,
                    )
                    index = next
                }
            }
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = position,
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(facePoints.size != 2) },
        properties = MapProperties(mapStyleOptions = null),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = false,
            scrollGesturesEnabled = false,
            tiltGesturesEnabled = false,
            zoomControlsEnabled = false,
            zoomGesturesEnabled = false,
        ),
    ) {
        if (facePoints.size == 2 && tourFaces != null) {
            val context = LocalContext.current
            val faces = listOf(tourFaces.first, tourFaces.second)
            faces.forEachIndexed { i, faceIndex ->
                val icon = remember(faceIndex, ringColor) {
                    BitmapDescriptorFactory.fromBitmap(
                        faceMarkerBitmap(context, faceIndex, ringColor, sizeDp = TOUR_FACE_MARKER_DP),
                    )
                }
                Marker(
                    state = rememberMarkerState(position = facePoints[i]),
                    icon = icon,
                    anchor = Offset(0.5f, 0.5f),
                    zIndex = 1f,
                )
            }
        }
    }
}

/**
 * How far the two faces sit from the middle, and how close the camera comes to one.
 *
 * iOS works in camera *distance* and then shows only the middle third of its map, so its
 * 7,000m close camera reads as roughly 2,300m of visible ground. These zoom levels are that
 * visible span across a 46dp circle, which is why they look far closer than iOS's numbers.
 */
private const val FACE_RADIUS_METRES = 1_250.0
private const val TOUR_CLOSE_ZOOM = 13.0f
private const val TOUR_MID_ZOOM = 12.5f
private const val TOUR_FACE_MARKER_DP = 20f

// iOS `HomeMapThumbnail`: zoom in, hold, lift, travel, and round again.
private const val TOUR_ZOOM_IN_MS = 2_200
private const val TOUR_HOLD_MS = 900
private const val TOUR_ZOOM_OUT_MS = 1_200
private const val TOUR_TRAVEL_MS = 1_900

/**
 * A point [metres] away from (lat, lon) on the given bearing. Plane approximation, which is
 * exact enough over the kilometre or so these faces sit apart.
 */
private fun offsetMetres(
    latitude: Double,
    longitude: Double,
    metres: Double,
    bearingDegrees: Double,
): LatLng {
    val bearing = bearingDegrees * PI / 180.0
    val dLat = (metres * cos(bearing)) / 111_320.0
    val dLon = (metres * sin(bearing)) / (111_320.0 * cos(latitude * PI / 180.0))
    return LatLng(latitude + dLat, longitude + dLon)
}

/**
 * Greater Noida. Somewhere that draws like a city and says nothing about her, for the map
 * behind the button before location has been allowed. iOS `HomeNearbyButton.standInCoordinate`.
 */
private const val STAND_IN_LATITUDE = 28.4595
private const val STAND_IN_LONGITUDE = 77.0266

/**
 * Close in on purpose. Pulled back, the tiles letter the region's name across themselves, and
 * half a place name behind a 46dp button reads as a mistake. At this range there are only
 * blocks and roads, which is safe here precisely because they are not her streets.
 */
private const val STAND_IN_MAP_ZOOM = 13f

/** Her own surroundings, once there is a fix. */
private const val LIVE_MAP_ZOOM = 15f

/**
 * How often Home re-asks whether a walk is live. Half a minute: this is a button, not the
 * walk screen, and the walk screen polls at ten seconds while it is open.
 */
private const val HOME_WALK_REFRESH_MS = 30_000L

/** How often the walk ring redraws. It moves by minutes, so half a minute is plenty. */
private const val RING_TICK_MS = 30_000L

/** One small face inside the 46dp button: 22dp, with a thin collar so the two separate. */
@Composable
private fun HomeButtonFace(avatarIndex: Int) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(sakhiSystemBackground(), CircleShape)
            .padding(1.5.dp)
            .background(sakhiLightPink(), CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(CareAvatars.drawable(avatarIndex)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().scale(CareAvatars.scale(avatarIndex)),
        )
    }
}
