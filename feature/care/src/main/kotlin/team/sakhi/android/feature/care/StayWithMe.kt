package team.sakhi.android.feature.care

import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.koin.compose.koinInject
import team.sakhi.staywithme.StayWithMeCheckInPreference
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import team.sakhi.android.designsystem.sakhiProfileCardBackground
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import team.sakhi.android.designsystem.AppleSystemColors
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiButtonFill
import team.sakhi.android.designsystem.sakhiGroupedBackground
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiLightPink
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.platform.StayWithMeLocationService
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SakhiAlertKind
import team.sakhi.android.ui.SakhiAlertSheet
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.android.ui.SakhiNavBar
import team.sakhi.android.ui.SakhiTextField
import team.sakhi.emergency.EmergencySafePlace
import team.sakhi.emergency.EmergencySafePlaceKind
import team.sakhi.staywithme.StayWithMeDestination
import team.sakhi.staywithme.StayWithMeDurations
import team.sakhi.staywithme.StayWithMeLocation
import team.sakhi.staywithme.StayWithMePhase
import team.sakhi.staywithme.StayWithMeSession
import java.text.DateFormat
import java.util.Date

// ─────────────────────────────────────────────────────────────────────────────
// Her side, before a walk: the card inside Care Mode
// ─────────────────────────────────────────────────────────────────────────────

/**
 * "Heading home? Ask Rahul to stay." One card, in the same grouped-list language as the
 * rest of the Care sheet: a white card on the grouped background, no shadow, one action.
 *
 * The consent line under the button is not small print. It is the only place she is told,
 * before she taps, that her location and battery will be shared, and that it stops when she
 * marks herself home.
 */
@Composable
internal fun StayWithMeStartSection(
    personName: String,
    isBusy: Boolean,
    error: String?,
    onStart: (minutes: Int, note: String, destination: StayWithMeDestination?) -> Unit,
) {
    val context = LocalContext.current
    var minutes by rememberSaveable { mutableStateOf(StayWithMeDurations.DEFAULT_MINUTES) }
    var note by rememberSaveable { mutableStateOf("") }
    // Where she is going, once picked from the suggestions. Typed but not picked stays her
    // own words, the way the field always worked, rather than being guessed into a pin.
    var destination by remember { mutableStateOf<StayWithMeDestination?>(null) }
    val search = remember { StayWithMeDestinationSearch(context.applicationContext) }
    var suggestions by remember { mutableStateOf<List<StayWithMeDestinationSearch.Suggestion>>(emptyList()) }
    LaunchedEffect(note, destination) {
        if (destination != null) { suggestions = emptyList(); return@LaunchedEffect }
        delay(350)
        suggestions = search.search(note)
    }

    // Asked at the moment she needs them, never on first open. If she says no to location,
    // the walk still starts: her person still gets told if she does not reach.
    var pending by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (pending) onStart(minutes, note, destination)
        pending = false
    }

    // iOS's shape, in iOS's order (`StayWithMeCard`): the destination on its own at the top
    // as a search field, then Reach by and Check in together as two settings rows, then who
    // is being asked, then the button. Every size here is read off that screen.
    val checkInPreference = koinInject<StayWithMeCheckInPreference>()
    var checkInMinutes by remember { mutableIntStateOf(checkInPreference.minutes()) }
    var pickingDuration by remember { mutableStateOf(false) }
    var pickingCheckIn by remember { mutableStateOf(false) }
    val reachBy = remember(minutes) { Clock.System.now().plus(minutes.toLong(), DateTimeUnit.MINUTE) }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        // 1. Where she is going.
        val picked = destination
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(RideStyle.card, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = sakhiTertiaryLabel(),
                modifier = Modifier.size(18.dp),
            )
            if (picked != null) {
                Text(
                    text = picked.name,
                    fontSize = 16.sp,
                    color = sakhiLabel(),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.care_swm_edit),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = RideStyle.rose,
                    modifier = Modifier.clickable { destination = null; note = "" },
                )
            } else {
                SakhiTextField(
                    value = note,
                    onValueChange = { note = it.take(StayWithMeDurations.MAX_NOTE_LENGTH) },
                    placeholder = stringResource(R.string.care_swm_search_destination),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (picked == null && suggestions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RideStyle.card, RoundedCornerShape(20.dp)),
            ) {
                suggestions.take(5).forEachIndexed { index, suggestion ->
                    if (index > 0) SakhiListDivider(startInset = 48.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                destination = suggestion.destination
                                note = suggestion.title
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(text = suggestion.title, fontSize = 16.sp, color = sakhiLabel(), maxLines = 1)
                        suggestion.subtitle?.let {
                            Text(text = it, fontSize = 13.sp, color = sakhiSecondaryLabel(), maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 2. When she should be there, and how often Sakhi asks.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RideStyle.card, RoundedCornerShape(20.dp)),
        ) {
            SwmSettingRow(
                icon = Icons.Filled.Schedule,
                label = stringResource(R.string.care_swm_reach_by_row),
                value = timeOf(reachBy),
                expanded = pickingDuration,
                onClick = { pickingDuration = !pickingDuration; pickingCheckIn = false },
            )
            if (pickingDuration) {
                SwmChoiceRow(
                    choices = StayWithMeDurations.presetMinutes.map { it to stringResource(R.string.care_swm_minutes_short, it) },
                    selected = minutes,
                    onSelect = { minutes = it; pickingDuration = false },
                )
            }
            SakhiListDivider(startInset = 62.dp)
            SwmSettingRow(
                icon = Icons.Filled.VerifiedUser,
                label = stringResource(R.string.care_swm_check_in_row),
                value = stringResource(R.string.care_swm_every_minutes, checkInMinutes),
                expanded = pickingCheckIn,
                onClick = { pickingCheckIn = !pickingCheckIn; pickingDuration = false },
            )
            if (pickingCheckIn) {
                SwmChoiceRow(
                    choices = CHECK_IN_CHOICES.map { it to stringResource(R.string.care_swm_minutes_short, it) },
                    selected = checkInMinutes,
                    onSelect = {
                        checkInMinutes = it
                        checkInPreference.setMinutes(it)
                        pickingCheckIn = false
                    },
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // 3. Who is being asked.
        Text(
            text = stringResource(R.string.care_swm_emergency_contacts),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiLabel(),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RideStyle.card, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(CareAvatars.drawable(CareAvatars.indexFor(personName))),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(RideStyle.soft, CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = personName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = sakhiLabel(),
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.care_swm_sees_your_way_home),
                    fontSize = 15.sp,
                    color = sakhiSecondaryLabel(),
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // 4. The button, with the consent line under it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(RideStyle.pink.copy(alpha = if (isBusy) 0.4f else 1f), CircleShape)
                .clickable(enabled = !isBusy) {
                    val missing = requiredPermissions().filterNot { granted(context, it) }
                    if (missing.isEmpty()) {
                        onStart(minutes, note, destination)
                    } else {
                        pending = true
                        launcher.launch(missing.toTypedArray())
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isBusy) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                // iOS puts Sakhi's own mark here, not a heart: `Image("BrandMedia/
                // sakhiSymbolAccent")` at 20, tinted white.
                Image(
                    painter = painterResource(team.sakhi.android.ui.R.drawable.sakhi_symbol_accent),
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.care_swm_start_button),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.care_swm_consent, personName),
            fontSize = 13.sp,
            color = sakhiSecondaryLabel(),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = error,
                fontSize = 13.sp,
                color = RideStyle.alert,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** One settings row: a pink disc, the label, the value, and a chevron that turns. */
@Composable
private fun SwmSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(RideStyle.soft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RideStyle.rose,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(text = label, fontSize = 16.sp, color = sakhiLabel())
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 16.sp,
            color = if (expanded) RideStyle.pink else sakhiSecondaryLabel(),
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = sakhiTertiaryLabel(),
            modifier = Modifier
                .size(13.dp)
                .rotate(if (expanded) 90f else 0f),
        )
    }
}

/** The choices under a settings row, as pills, the way the duration picker always was. */
@Composable
private fun SwmChoiceRow(
    choices: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(
                        if (isSelected) RideStyle.pink else RideStyle.soft,
                        CircleShape,
                    )
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else sakhiLabel(),
                    maxLines = 1,
                )
            }
        }
    }
}

/** iOS's `StayWithMeCard.checkInChoices`. */
private val CHECK_IN_CHOICES = listOf(5, 10, 15, 30)

@Composable
private fun SwmFormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
        color = sakhiTertiaryLabel(),
    )
}

/** Four durations in one track, the selection sliding between them. */
@Composable
private fun DurationPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(sakhiButtonFill(), RoundedCornerShape(SakhiRadius.full))
            .padding(3.dp),
    ) {
        StayWithMeDurations.presetMinutes.forEach { m ->
            val isSelected = m == selected
            val fill by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                tween(220),
                label = "durationFill",
            )
            val ink by animateColorAsState(
                if (isSelected) Color.White else sakhiLabel(),
                tween(220),
                label = "durationInk",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(SakhiRadius.full))
                    .background(fill)
                    .clickable { onSelect(m) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (m == 60) stringResource(R.string.care_swm_one_hour) else stringResource(R.string.care_swm_minutes, m),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = ink,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Her side, during a walk
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Her live walk. The map shows her, the line under it says whether her person is looking
 * right now, and the time is the one number she glances at. "I'm home" is the primary
 * action and sits in the footer, in thumb reach.
 */
/**
 * Her own screen once a walk is running.
 *
 * The same map-first layout her person sees, deliberately. She is the one out there, so she
 * gets the bigger picture, not the smaller one: the way she has come, where she is now, her
 * person right there on the screen with her, and the police station and helplines within
 * reach without leaving the walk. The countdown and "I'm home" sit on top of that, not
 * instead of it.
 */
@Composable
internal fun StayWithMeOwnerLive(
    session: StayWithMeSession,
    personName: String,
    now: Instant,
    isBusy: Boolean,
    trail: List<StayWithMeLocation>,
    places: List<EmergencySafePlace>,
    placesLoading: Boolean,
    onArrive: () -> Unit,
    onExtend: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
    /** "Are you okay?" is waiting for her answer. */
    checkInRequested: Boolean = false,
    checkInChecking: Boolean = false,
    checkInFailed: Boolean = false,
    onCheckInOkay: () -> Unit = {},
    onRefresh: () -> Unit = {},
    route: WalkRoute? = null,
    /** A refresh is in flight: the button shows a spinner. */
    refreshing: Boolean = false,
    /** Bumped when a refresh lands, so the map frames her again even if she has not moved. */
    recenterKey: Int = 0,
) {
    val context = LocalContext.current
    val phase = session.phase(now)
    val isLate = phase == StayWithMePhase.LATE
    val accent = if (isLate || phase == StayWithMePhase.GRACE) AppleSystemColors.red else MaterialTheme.colorScheme.primary
    var confirmStop by remember { mutableStateOf(false) }
    val sharing = StayWithMeLocationService.hasLocationPermission(context)
    val location = session.lastLocation

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(sakhiGroupedBackground())) {
        // The map is the whole screen, as on Emergency Assistance. It frames her in the part
        // the panel leaves uncovered rather than centring her underneath it.
        WalkMap(
            location = location,
            accent = accent,
            initial = "",
            trail = trail,
            modifier = Modifier.fillMaxSize(),
            bottomPadding = 216.dp,
            destination = session.destination,
            routeLine = route?.points.orEmpty(),
            recenterKey = recenterKey,
        )
        if (location == null) {
            MapNotice(
                text = if (sharing) stringResource(R.string.care_swm_finding) else stringResource(R.string.care_swm_not_sharing),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.22f),
            )
        }

        RideTopBar(
            onClose = onClose,
            // On her own screen the pill says who is with her, not how old her own fix is.
            freshness = if (session.isWatcherPresent(now)) {
                stringResource(R.string.care_swm_is_with_you, personName)
            } else {
                null
            },
            onCallPolice = { dial(context, "112") },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        var expanded by remember(session.id) { mutableStateOf(false) }
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val restingHeight = if (checkInRequested) 216.dp else 318.dp
        val panelHeight by animateDpAsState(
            targetValue = if (expanded) maxHeight - 64.dp else restingHeight + bottomInset,
            label = "ownerPanelHeight",
        )

        // Above the panel, over the map: one thing asking at a time (Karan, 2026-09-16).
        if (checkInRequested) {
            RideCheckInBox(
                personName = personName,
                checking = checkInChecking,
                failed = checkInFailed,
                onOkay = onCheckInOkay,
                onGetHelp = { dial(context, "112") },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = restingHeight + bottomInset + 10.dp),
            )
        }
        Surface(
            color = RideStyle.ground,
            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(panelHeight),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount < -6f) expanded = true
                                if (dragAmount > 6f) expanded = false
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 5.dp)
                            .background(RideStyle.hairline, CircleShape),
                    )
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item(key = "summary") {
                        val status = rideOwnerStatus(session, now, route)
                        val hero = rideOwnerHero(session, now, route, personName)
                        RideSummary(
                            heading = session.destination?.name?.let { stringResource(R.string.care_swm_to_place, it) }
                                ?: session.note?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.care_swm_to_place, it) }
                                ?: stringResource(R.string.care_swm_your_ride_home),
                            alerted = phase == StayWithMePhase.LATE,
                            statusLabel = stringResource(status.ownerLabelRes),
                            statusTint = status.trackTint(),
                            heroValue = hero.value,
                            heroUnit = hero.unit,
                            heroDetail = hero.detail,
                            heroTint = if (phase == StayWithMePhase.LATE) RideStyle.alert else sakhiLabel(),
                            faceIndex = CareAvatars.indexFor(session.watcherUserId),
                            faceCaption = personName,
                            facePresent = session.isWatcherPresent(now),
                            progress = rideProgress(session, now),
                            startedAt = session.startedAt,
                            endCaption = stringResource(
                                when (phase) {
                                    StayWithMePhase.WALKING -> R.string.care_swm_track_reach_by
                                    StayWithMePhase.GRACE -> R.string.care_swm_track_alert_at
                                    else -> R.string.care_swm_track_was_due
                                },
                            ),
                            endAt = if (phase == StayWithMePhase.GRACE) session.alertAt else session.expectedArrival,
                        )
                    }

                    item(key = "facts") {
                        RideFactsCard(
                            destinationName = session.destination?.name ?: session.note,
                            freshness = if (sharing) session.locationAgeSeconds(now)?.let { updatedText(it) } else stringResource(R.string.care_swm_not_sharing),
                            batteryPercent = location?.batteryPercent,
                            onRefresh = onRefresh,
                            refreshing = refreshing,
                        )
                    }

                    item(key = "near-label") { SwmSectionLabel(stringResource(R.string.care_swm_help_near_you), top = SakhiSpacing.space5) }
                    item(key = "near") {
                        GroupedCard {
                            when {
                                places.isNotEmpty() -> places.forEachIndexed { index, place ->
                                    if (index > 0) SakhiListDivider(startInset = 56.dp)
                                    PlaceRow(place = place, onClick = { openDirections(context, place) })
                                }
                                placesLoading -> StatusRow(text = stringResource(R.string.care_swm_places_loading_you), loading = true)
                                else -> StatusRow(text = stringResource(R.string.care_swm_places_empty_you), loading = false)
                            }
                        }
                    }

                    item(key = "lines-label") { SwmSectionLabel(stringResource(R.string.care_swm_helplines), top = SakhiSpacing.space5) }
                    item(key = "lines") {
                        // Three buttons read by their picture, not two rows of text. In an
                        // emergency nobody reads (Karan, 2026-09-16), and the ambulance was
                        // missing from this list entirely.
                        EmergencyCallButtons(onCall = { number -> dial(context, number) })
                    }

                    item(key = "swm-stop") {
                        TextButton(
                            onClick = { confirmStop = true },
                            modifier = Modifier.fillMaxWidth().padding(top = SakhiSpacing.space4, bottom = SakhiSpacing.space2),
                        ) {
                            Text(
                                text = stringResource(R.string.care_swm_stop),
                                style = MaterialTheme.typography.bodyLarge,
                                color = sakhiSecondaryLabel(),
                            )
                        }
                    }
                }

                // iOS's two buttons, side by side: a 112 wide white "+15 min" and "I'm home"
                // taking the rest, both 58 tall.
                if (!checkInRequested) {
                    RideFooterButtons(
                        onExtend = onExtend,
                        onArrive = onArrive,
                        enabled = !isBusy,
                        busy = isBusy,
                    )
                }
            }
        }
    }

    if (confirmStop) {
        SakhiAlertSheet(
            kind = SakhiAlertKind.Destructive,
            title = stringResource(R.string.care_swm_stop_title),
            message = stringResource(R.string.care_swm_stop_body, personName),
            primaryLabel = stringResource(R.string.care_swm_stop_confirm),
            onPrimaryClick = {
                confirmStop = false
                onStop()
            },
            secondaryLabel = stringResource(R.string.care_swm_keep_sharing),
            onSecondaryClick = { confirmStop = false },
            onDismissRequest = { confirmStop = false },
        )
    }
}

/** Where she is going and how far is left, from the route when there is one. */
@Composable
private fun DestinationCard(place: StayWithMeDestination, route: WalkRoute?, session: StayWithMeSession) {
    val metres = route?.distanceMeters ?: session.metresToDestination()
    val distance = metres?.let { if (it < 1000) "${it.toInt()} m" else String.format("%.1f km", it / 1000) }
    val minutes = route?.durationSeconds?.let { ((it + 59) / 60).coerceAtLeast(1) }
    Row(
        modifier = Modifier
            .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2)
            .fillMaxWidth()
            // Tonal, not a white card: it is one line about where she is going.
            .background(sakhiLightPink(), RoundedCornerShape(SakhiRadius.lg))
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(SakhiSpacing.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.care_swm_going_to, place.name),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = sakhiLabel(),
                maxLines = 1,
            )
            if (distance != null) {
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.care_swm_left_distance, distance),
                        minutes?.let { stringResource(R.string.care_swm_minutes_by_car, it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = sakhiSecondaryLabel(),
                )
            }
        }
    }
}

/** A round refresh control: her phone sends where she is now, or his asks for the newest. */
/**
 * Refresh, with a spinner in its place until the answer is in. Without it a tap did nothing
 * visible for up to ten seconds while the phone found a fresh fix, and got tapped again.
 */
@Composable
internal fun RefreshButton(onClick: () -> Unit, label: String, refreshing: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = sakhiLightPink(),
        modifier = Modifier.size(36.dp),
    ) {
        IconButton(onClick = onClick, enabled = !refreshing, modifier = Modifier.size(36.dp)) {
            if (refreshing) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** The height of the close button and Contact Police row over the map. */
private val TOP_BAR_HEIGHT = 56.dp

/** The closest the walk map frames itself when her and her destination are near. */
private const val MAX_FRAMING_ZOOM = 16.5f

/** How much of the screen each side's panel takes. The map is the rest, and behind it. */
private const val OWNER_PANEL_FRACTION = 0.58f
private const val WATCHER_PANEL_FRACTION = 0.52f

/**
 * The controls over the map, where Emergency Assistance puts them and drawn the same way:
 * a 35dp white disc at the top left, and Contact Police as a white capsule opposite it.
 * The disc carries a cross, because this screen closes rather than steps back. Closing is
 * not stopping: the walk carries on, and Home's ring shows it.
 */
@Composable
internal fun LiveWalkTopBar(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = sakhiSystemBackground(),
            shadowElevation = 0.dp,
            modifier = Modifier.size(35.dp),
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(35.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.care_swm_close),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            shape = CircleShape,
            color = sakhiSystemBackground(),
            shadowElevation = 0.dp,
            modifier = Modifier.height(38.dp),
        ) {
            Row(
                modifier = Modifier
                    .clickable { dial(context, "112") }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.care_swm_contact_police),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = sakhiLabel(),
                )
            }
        }
    }
}

/** "Rahul is with you", with a live dot, or the quieter line when he has not opened it yet. */
@Composable
private fun PresenceRow(name: String, present: Boolean, late: Boolean, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "presence")
    val halo by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "presenceHalo",
    )
    val dot = when {
        late -> AppleSystemColors.red
        present -> AppleSystemColors.green
        else -> sakhiTertiaryLabel()
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WalkAvatar(name = name, size = 40)
        Spacer(Modifier.width(SakhiSpacing.space3))
        Text(
            text = when {
                late -> stringResource(R.string.care_swm_person_told_late, name)
                present -> stringResource(R.string.care_swm_person_with_you, name)
                else -> stringResource(R.string.care_swm_person_asked, name)
            },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = sakhiLabel(),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(if (present || late) halo else 1f)
                .background(dot, CircleShape),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Her person's side
// ─────────────────────────────────────────────────────────────────────────────

/**
 * What her person sees when they open the notification: her on a big map, moving as her
 * phone reports in, with her battery, when she is due, and what is near her if they need it.
 *
 * Standard map-with-sheet layout. The map takes the top, a white panel rises over its
 * bottom edge, and nothing on it is decoration: every row is either about her, or
 * something they can do.
 */
@Composable
internal fun StayWithMeWatcherLive(
    session: StayWithMeSession,
    herName: String,
    now: Instant,
    places: List<EmergencySafePlace>,
    placesLoading: Boolean,
    trail: List<StayWithMeLocation>,
    onClose: () -> Unit,
    onRefresh: () -> Unit = {},
    route: WalkRoute? = null,
    refreshing: Boolean = false,
    recenterKey: Int = 0,
) {
    val context = LocalContext.current
    val phase = session.phase(now)
    val isLate = phase == StayWithMePhase.LATE
    val accent = if (isLate) AppleSystemColors.red else MaterialTheme.colorScheme.primary
    val location = session.lastLocation

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(sakhiGroupedBackground())) {
        WalkMap(
            location = location,
            accent = accent,
            // On his phone she is "she", and her marker is a face, not the first letter of
            // whatever her profile happens to be called ("U" for "User").
            initial = "",
            avatarWithoutName = true,
            faceIndex = CareAvatars.indexFor(session.ownerUserId),
            trail = trail,
            modifier = Modifier.fillMaxSize(),
            bottomPadding = 216.dp,
            destination = session.destination,
            routeLine = route?.points.orEmpty(),
            recenterKey = recenterKey,
        )
        if (location == null) {
            MapNotice(
                text = stringResource(R.string.care_swm_waiting_location),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.22f),
            )
        }

        // iOS puts three things over the map: the way out, how fresh her position is, and
        // the one number worth a tap without reading.
        RideTopBar(
            onClose = onClose,
            freshness = session.locationAgeSeconds(now)?.let { updatedText(it) },
            onCallPolice = { dial(context, "112") },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // iOS rests this at 216 and lets it be pulled up. The corners are the app's own
        // bottom-sheet radius, 40, not the 28 this screen used to draw.
        var expanded by remember(session.id) { mutableStateOf(false) }
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val panelHeight by animateDpAsState(
            targetValue = if (expanded) maxHeight - 64.dp else 216.dp + bottomInset,
            label = "ridePanelHeight",
        )
        Surface(
            color = RideStyle.ground,
            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(panelHeight),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                item(key = "grabber") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 2.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount < -6f) expanded = true
                                    if (dragAmount > 6f) expanded = false
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 5.dp)
                                .background(RideStyle.hairline, CircleShape),
                        )
                    }
                }

                item(key = "summary") {
                    val status = rideWatcherStatus(session, now, route)
                    val hero = rideWatcherHero(session, now, route)
                    RideSummary(
                        heading = when {
                            phase == StayWithMePhase.LATE -> stringResource(R.string.care_swm_not_reached_heading)
                            session.destination != null && !session.destination!!.name.equals("home", true) ->
                                stringResource(R.string.care_swm_going_to, session.destination!!.name)
                            else -> stringResource(R.string.care_swm_going_home)
                        },
                        alerted = phase == StayWithMePhase.LATE,
                        statusLabel = stringResource(status.labelRes),
                        statusTint = status.trackTint(),
                        heroValue = hero.value,
                        heroUnit = hero.unit,
                        heroDetail = hero.detail,
                        heroTint = if (phase == StayWithMePhase.LATE) RideStyle.alert else sakhiLabel(),
                        faceIndex = CareAvatars.indexFor(session.ownerUserId),
                        faceCaption = stringResource(R.string.care_swm_her),
                        facePresent = (session.locationAgeSeconds(now)?.toLong() ?: Long.MAX_VALUE) < 120L,
                        progress = rideProgress(session, now),
                        startedAt = session.startedAt,
                        endCaption = stringResource(
                            when (phase) {
                                StayWithMePhase.WALKING -> R.string.care_swm_track_reach_by
                                StayWithMePhase.GRACE -> R.string.care_swm_track_alert_at
                                else -> R.string.care_swm_track_was_due
                            },
                        ),
                        endAt = if (phase == StayWithMePhase.GRACE) session.alertAt else session.expectedArrival,
                    )
                }

                item(key = "facts") {
                    RideFactsCard(
                        destinationName = session.destination?.name ?: session.note,
                        freshness = session.locationAgeSeconds(now)?.let { updatedText(it) },
                        batteryPercent = location?.batteryPercent,
                        onRefresh = onRefresh,
                        refreshing = refreshing,
                    )
                }

                item(key = "call-label") { SwmSectionLabel(stringResource(R.string.care_swm_call_for_help), top = SakhiSpacing.space5) }
                item(key = "lines") {
                    EmergencyCallButtons(onCall = { number -> dial(context, number) })
                }

                item(key = "near-label") {
                    Column(
                        modifier = Modifier.padding(
                            start = SakhiSpacing.space5,
                            end = SakhiSpacing.space5,
                            top = SakhiSpacing.space5,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.care_swm_help_near_her),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = sakhiLabel(),
                        )
                        Text(
                            text = stringResource(R.string.care_swm_help_near_her_note),
                            fontSize = 15.sp,
                            color = sakhiSecondaryLabel(),
                        )
                    }
                }
                item(key = "near") {
                    GroupedCard {
                        when {
                            places.isNotEmpty() -> places.forEachIndexed { index, place ->
                                if (index > 0) SakhiListDivider(startInset = 56.dp)
                                PlaceRow(place = place, onClick = { openDirections(context, place) })
                            }
                            placesLoading -> StatusRow(text = stringResource(R.string.care_swm_places_loading), loading = true)
                            else -> StatusRow(text = stringResource(R.string.care_swm_places_empty), loading = false)
                        }
                    }
                }

                item(key = "privacy") {
                    Text(
                        text = stringResource(R.string.care_swm_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = sakhiTertiaryLabel(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SakhiSpacing.space8, vertical = SakhiSpacing.space6),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: EmergencySafePlace, onClick: () -> Unit) {
    val (icon, tint) = when (place.kind) {
        EmergencySafePlaceKind.POLICE -> Icons.Filled.LocalPolice to AppleSystemColors.blue
        // Pink, as iOS and Emergency Assistance draw it. Red on this screen means late.
        EmergencySafePlaceKind.HOSPITAL -> Icons.Filled.LocalHospital to MaterialTheme.colorScheme.primary
        else -> Icons.Filled.LocalPharmacy to AppleSystemColors.green
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(SakhiRadius.sm)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(SakhiSpacing.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge,
                color = sakhiLabel(),
                maxLines = 1,
            )
            Text(
                text = "${place.kind.rowLabel}, ${place.formattedDistance}",
                style = MaterialTheme.typography.bodySmall,
                color = sakhiSecondaryLabel(),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = sakhiTertiaryLabel(),
        )
    }
}

@Composable
private fun StatusRow(text: String, loading: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(SakhiSpacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(SakhiSpacing.space3))
        }
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = sakhiSecondaryLabel())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared pieces
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The walk map. Her marker glides to each new position instead of jumping, and the camera
 * follows it, so a position that arrives every thirty seconds still reads as her moving.
 * The soft circle is the fix's accuracy, capped so a poor fix never paints half the city.
 */
@Composable
internal fun WalkMap(
    location: StayWithMeLocation?,
    accent: Color,
    initial: String,
    modifier: Modifier = Modifier,
    trail: List<StayWithMeLocation> = emptyList(),
    /** Her person's map draws her as an avatar even when there is no name for initials. */
    avatarWithoutName: Boolean = false,
    destination: StayWithMeDestination? = null,
    /** The way ahead, from her to [destination]. */
    routeLine: List<Pair<Double, Double>> = emptyList(),
    /** How much of the map's bottom the panel covers, so the camera frames above it. */
    bottomPadding: Dp = 0.dp,
    /**
     * How much of the top is covered. Null is the full-screen default: the status bar and
     * the close / Contact Police row. The card on the connection screen has neither.
     */
    topPadding: Dp? = null,
    /**
     * False for the card: a map inside a scrolling page must not take the page's drags,
     * or the page stops scrolling wherever the map is. Tapping the card opens it instead.
     */
    interactive: Boolean = true,
    /** Changing it frames her again, for after a refresh, whether or not she has moved. */
    recenterKey: Int = 0,
    /**
     * Her actual face on the map, the way iOS draws it. Null falls back to the glyph, which
     * is what her own screen uses for herself.
     */
    faceIndex: Int? = null,
) {
    val context = LocalContext.current
    val target = location?.let { LatLng(it.latitude, it.longitude) }
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(target ?: DEFAULT_CENTER, if (target != null) 16f else 11f)
    }

    // Glide between fixes: animate a fraction from the last shown point to the new one.
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val glide = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        val current = interpolate(from, to, glide.value)
        if (current == null) {
            from = target
            to = target
        } else {
            from = current
            to = target
            glide.snapTo(0f)
            glide.animateTo(1f, tween(1_200, easing = FastOutSlowInEasing))
        }
    }
    // Framing waits for the map to load: before that the panel's padding is not applied,
    // and the first build framed the destination straight under the panel.
    var mapLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(target, destination, routeLine.size, mapLoaded, recenterKey) {
        if (target == null || !mapLoaded) return@LaunchedEffect
        val place = destination
        if (place != null) {
            // With somewhere to go, the whole of it is in view: her, and where she is headed.
            val there = LatLng(place.latitude, place.longitude)
            val bounds = LatLngBounds.builder().include(target).include(there)
            routeLine.forEach { bounds.include(LatLng(it.first, it.second)) }
            // Less margin in the card, which is a fraction of the screen tall.
            val margin = if (interactive) 160 else 80
            runCatching { cameraState.animate(CameraUpdateFactory.newLatLngBounds(bounds.build(), margin), 1_000) }
            // Points a few steps apart would zoom to the kerb. Street level is enough.
            if (cameraState.position.zoom > MAX_FRAMING_ZOOM) {
                runCatching { cameraState.animate(CameraUpdateFactory.zoomTo(MAX_FRAMING_ZOOM), 600) }
            }
        } else {
            val zoom = cameraState.position.zoom.coerceAtLeast(15.5f)
            runCatching { cameraState.animate(CameraUpdateFactory.newLatLngZoom(target, zoom), 1_000) }
        }
    }
    val shown = interpolate(from, to, glide.value)

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraState,
        // Top too: the close button and Contact Police sit over the map, and her own marker
        // was framed straight under Contact Police.
        contentPadding = PaddingValues(
            top = topPadding ?: (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TOP_BAR_HEIGHT),
            bottom = bottomPadding.coerceAtLeast(0.dp),
        ),
        onMapLoaded = { mapLoaded = true },
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false,
            scrollGesturesEnabled = interactive,
            zoomGesturesEnabled = interactive,
            tiltGesturesEnabled = interactive,
            rotationGesturesEnabled = interactive,
        ),
    ) {
        // The way ahead, dotted, under everything else.
        if (routeLine.size >= 2) {
            val ahead = remember(routeLine) { routeLine.map { LatLng(it.first, it.second) } }
            Polyline(
                points = ahead,
                color = accent.copy(alpha = 0.9f),
                width = 10f,
                pattern = listOf(Dot(), Gap(18f)),
                jointType = JointType.ROUND,
            )
        }
        destination?.let { place ->
            val pinState = rememberMarkerState(position = LatLng(place.latitude, place.longitude))
            pinState.position = LatLng(place.latitude, place.longitude)
            val homeIcon = remember(accent) {
                BitmapDescriptorFactory.fromBitmap(homeMarkerBitmap(context, accent.toArgb()))
            }
            Marker(
                state = pinState,
                title = place.name,
                icon = homeIcon,
                anchor = Offset(0.5f, 0.5f),
            )
        }

        // The way she has come. Drawn under her dot, and only from the second point, so a
        // walk that has just started shows a dot rather than a line of length zero.
        if (trail.size >= 2) {
            val points = remember(trail) { trail.map { LatLng(it.latitude, it.longitude) } }
            Polyline(
                points = points,
                color = accent.copy(alpha = 0.55f),
                width = 12f,
                jointType = JointType.ROUND,
                startCap = RoundCap(),
                endCap = RoundCap(),
            )
            points.firstOrNull()?.let { start ->
                Circle(
                    center = start,
                    radius = 12.0,
                    fillColor = accent,
                    strokeColor = Color.White,
                    strokeWidth = 4f,
                )
            }
        }

        if (shown != null) {
            val accuracy = (location?.accuracyMeters ?: 40.0).coerceIn(15.0, 150.0)
            Circle(
                center = shown,
                radius = accuracy,
                fillColor = accent.copy(alpha = 0.12f),
                strokeColor = accent.copy(alpha = 0.35f),
                strokeWidth = 2f,
            )
            val markerState = rememberMarkerState(position = shown)
            markerState.position = shown
            val icon = remember(accent, initial, avatarWithoutName, faceIndex) {
                val bitmap = if (faceIndex != null) {
                    faceMarkerBitmap(context, faceIndex, accent.toArgb())
                } else {
                    markerBitmap(context, accent.toArgb(), initial, avatar = initial.isNotEmpty() || avatarWithoutName)
                }
                BitmapDescriptorFactory.fromBitmap(bitmap)
            }
            Marker(state = markerState, icon = icon, anchor = Offset(0.5f, 0.5f), flat = true)
        }
    }
}

private fun interpolate(from: LatLng?, to: LatLng?, fraction: Float): LatLng? {
    if (to == null) return from
    if (from == null) return to
    val f = fraction.toDouble()
    return LatLng(from.latitude + (to.latitude - from.latitude) * f, from.longitude + (to.longitude - from.longitude) * f)
}

/** Her dot: a filled circle with a white collar, her initial on it when there is one. */
/**
 * Her face on the map, inside a white ring, the way iOS's `WalkRidePuck` draws it. Forty
 * across like the glyph marker it replaces, so the map's framing does not change.
 */
private fun faceMarkerBitmap(context: Context, faceIndex: Int, ringColor: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (44 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = size / 2f
    canvas.drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ringColor })
    canvas.drawCircle(radius, radius, radius - 2.5f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    val face = androidx.core.content.ContextCompat.getDrawable(context, CareAvatars.drawable(faceIndex))
    if (face != null) {
        val inset = (3.5f * density).toInt()
        face.setBounds(inset, inset, size - inset, size - inset)
        val saved = canvas.save()
        val clip = android.graphics.Path().apply {
            addCircle(radius, radius, radius - 3.5f * density, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(clip)
        face.draw(canvas)
        canvas.restoreToCount(saved)
    }
    return bitmap
}

/** Where she is going: a disc with a house in it, as iOS draws the destination. */
private fun homeMarkerBitmap(context: Context, color: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (32 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = size / 2f
    canvas.drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE })
    canvas.drawCircle(radius, radius, radius - 2.5f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE }
    // A small house: a roof over a square, centred.
    val roof = android.graphics.Path().apply {
        moveTo(radius, radius - 6.5f * density)
        lineTo(radius + 6f * density, radius - 0.5f * density)
        lineTo(radius - 6f * density, radius - 0.5f * density)
        close()
    }
    canvas.drawPath(roof, white)
    canvas.drawRect(
        radius - 4f * density, radius - 0.5f * density,
        radius + 4f * density, radius + 6f * density,
        white,
    )
    return bitmap
}

private fun markerBitmap(context: Context, color: Int, initial: String, avatar: Boolean): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (if (avatar) 40 else 22) * density
    val bitmap = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = size / 2f
    canvas.drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE })
    canvas.drawCircle(radius, radius, radius - 3 * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    if (initial.isNotEmpty()) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = 17 * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(initial, radius, radius - (text.descent() + text.ascent()) / 2f, text)
    } else if (avatar) {
        // No name to take initials from: a head and shoulders, the profile's person glyph.
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE }
        canvas.drawCircle(radius, radius - 4 * density, 5.5f * density, white)
        canvas.drawArc(
            radius - 9 * density, radius + 3 * density, radius + 9 * density, radius + 19 * density,
            180f, 180f, true, white,
        )
    }
    return bitmap
}

@Composable
private fun MapNotice(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = sakhiLabel(),
        modifier = modifier
            .padding(SakhiSpacing.space4)
            .background(sakhiSystemBackground(), RoundedCornerShape(SakhiRadius.full))
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space2),
    )
}

@Composable
private fun WalkProgress(progress: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(sakhiButtonFill(), RoundedCornerShape(SakhiRadius.full)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .background(color, RoundedCornerShape(SakhiRadius.full)),
        )
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, tint: Color) {
    Row(
        modifier = Modifier
            .background(sakhiButtonFill(), RoundedCornerShape(SakhiRadius.full))
            .padding(horizontal = SakhiSpacing.space3, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(SakhiSpacing.space1))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            maxLines = 1,
        )
    }
}

/**
 * The other person's face on the walk: the same avatar their profile shows, their initials
 * (first letters of the first two words of their name) in a circle, and a person glyph when
 * there is no real name to take them from. A stand-in label like "Your Sakhi" is not a
 * name, and drawing its first letter showed a "Y" that belonged to nobody.
 */
@Composable
private fun WalkAvatar(name: String, size: Int, fill: Color = MaterialTheme.colorScheme.primary) {
    val initials = walkInitials(name)
    Box(
        modifier = Modifier.size(size.dp).background(fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (initials != null) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size * if (initials.length > 1) 0.36f else 0.42f).sp,
                ),
                color = Color.White,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size((size * 0.5f).dp),
            )
        }
    }
}

/** Labels the app puts in when it does not know a name. */
private val STAND_IN_NAMES = setOf("your sakhi", "her", "she", "you", "your person", "sakhi")

/** The profile's rule for initials, or null when there is no real name. */
internal fun walkInitials(name: String): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || trimmed.lowercase() in STAND_IN_NAMES) return null
    return trimmed.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
        .takeIf { it.isNotEmpty() }
}

@Composable
private fun GroupedCard(content: @Composable () -> Unit) {
    // No card. The panel over the map is already a surface, so these rows sit on it with a
    // hairline between them; a white card inside it is a box inside a box (Karan, 2026-09-13).
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = SakhiSpacing.space2)) {
        content()
    }
}

@Composable
private fun SwmSectionLabel(text: String, top: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
        color = sakhiSecondaryLabel(),
        modifier = Modifier.padding(start = SakhiSpacing.space6, end = SakhiSpacing.space6, top = top, bottom = SakhiSpacing.space2),
    )
}

@Composable
private fun updatedText(ageSeconds: Long): String = when {
    ageSeconds < 15 -> stringResource(R.string.care_swm_updated_now)
    ageSeconds < 60 -> stringResource(R.string.care_swm_updated_seconds, ageSeconds.toInt())
    else -> stringResource(R.string.care_swm_updated_minutes, (ageSeconds / 60).toInt())
}

internal fun minutesUp(seconds: Long): Int = ((seconds + 59) / 60).toInt().coerceAtLeast(0)

/** A wall clock time in her own locale. Shared with the alarm screen. */
internal fun timeOf(instant: Instant): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(instant.toEpochMilliseconds()))

private fun requiredPermissions(): List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Only fills in the number. The call is always theirs to place. */
internal fun dial(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openDirections(context: Context, place: EmergencySafePlace) {
    val uri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${Uri.encode(place.name)})")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private val DEFAULT_CENTER = LatLng(28.6139, 77.2090)
