package team.sakhi.android.feature.care

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
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
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
    onStart: (minutes: Int, note: String) -> Unit,
) {
    val context = LocalContext.current
    var minutes by rememberSaveable { mutableStateOf(StayWithMeDurations.DEFAULT_MINUTES) }
    var note by rememberSaveable { mutableStateOf("") }

    // Asked at the moment she needs them, never on first open. If she says no to location,
    // the walk still starts: her person still gets told if she does not reach.
    var pending by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (pending) onStart(minutes, note)
        pending = false
    }

    Column {
        SwmSectionLabel(stringResource(R.string.care_swm_section))
        Surface(
            color = sakhiSystemBackground(),
            shape = RoundedCornerShape(SakhiRadius.xxl),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space6),
        ) {
            Column(modifier = Modifier.padding(SakhiSpacing.space5)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(sakhiLightPink(), RoundedCornerShape(SakhiRadius.md)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(SakhiSpacing.space3))
                    Column {
                        Text(
                            text = stringResource(R.string.care_swm_heading_home),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = sakhiLabel(),
                        )
                        Text(
                            text = stringResource(R.string.care_swm_ask_subtitle, personName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = sakhiSecondaryLabel(),
                        )
                    }
                }

                Spacer(Modifier.height(SakhiSpacing.space5))
                DurationPicker(selected = minutes, onSelect = { minutes = it })

                Spacer(Modifier.height(SakhiSpacing.space3))
                SakhiTextField(
                    value = note,
                    onValueChange = { note = it.take(StayWithMeDurations.MAX_NOTE_LENGTH) },
                    placeholder = stringResource(R.string.care_swm_where_placeholder),
                )

                Spacer(Modifier.height(SakhiSpacing.space4))
                PrimaryButton(
                    text = stringResource(R.string.care_swm_ask_button, personName),
                    enabled = !isBusy,
                    onClick = {
                        val missing = requiredPermissions().filterNot { granted(context, it) }
                        if (missing.isEmpty()) {
                            onStart(minutes, note)
                        } else {
                            pending = true
                            launcher.launch(missing.toTypedArray())
                        }
                    },
                )

                Spacer(Modifier.height(SakhiSpacing.space3))
                Text(
                    text = stringResource(R.string.care_swm_consent, personName),
                    style = MaterialTheme.typography.bodySmall,
                    color = sakhiSecondaryLabel(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(SakhiSpacing.space2))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppleSystemColors.red,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
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
@Composable
internal fun StayWithMeOwnerLive(
    session: StayWithMeSession,
    personName: String,
    now: Instant,
    isBusy: Boolean,
    onArrive: () -> Unit,
    onExtend: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val phase = session.phase(now)
    val isLate = phase == StayWithMePhase.LATE
    val accent = if (isLate || phase == StayWithMePhase.GRACE) AppleSystemColors.red else MaterialTheme.colorScheme.primary
    var confirmStop by remember { mutableStateOf(false) }
    val sharing = StayWithMeLocationService.hasLocationPermission(context)

    Column(modifier = Modifier.fillMaxSize().background(sakhiGroupedBackground())) {
        SakhiNavBar(onClose = onClose, title = stringResource(R.string.care_swm_title))

        LazyColumn(modifier = Modifier.weight(1f)) {
            item(key = "swm-map") {
                Box(
                    modifier = Modifier
                        .padding(horizontal = SakhiSpacing.space6)
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(SakhiRadius.xxl)),
                ) {
                    WalkMap(
                        location = session.lastLocation,
                        accent = accent,
                        initial = "",
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (session.lastLocation == null) {
                        MapNotice(
                            text = if (sharing) stringResource(R.string.care_swm_finding) else stringResource(R.string.care_swm_not_sharing),
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }

            item(key = "swm-presence") {
                PresenceRow(
                    name = personName,
                    present = session.isWatcherPresent(now),
                    late = isLate,
                    modifier = Modifier.padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space5),
                )
            }

            item(key = "swm-time") {
                Surface(
                    color = sakhiSystemBackground(),
                    shape = RoundedCornerShape(SakhiRadius.xxl),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SakhiSpacing.space6),
                ) {
                    Column(modifier = Modifier.padding(SakhiSpacing.space5)) {
                        val big = when (phase) {
                            StayWithMePhase.WALKING -> stringResource(R.string.care_swm_minutes_left, minutesUp(session.secondsRemaining(now)))
                            else -> stringResource(R.string.care_swm_past_time)
                        }
                        Text(
                            text = big,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (phase == StayWithMePhase.WALKING) sakhiLabel() else AppleSystemColors.red,
                        )
                        Spacer(Modifier.height(SakhiSpacing.space1))
                        Text(
                            text = when (phase) {
                                StayWithMePhase.GRACE -> stringResource(
                                    R.string.care_swm_grace_left,
                                    minutesUp(session.secondsRemaining(now)),
                                    personName,
                                )
                                StayWithMePhase.LATE -> stringResource(R.string.care_swm_person_told_late, personName)
                                else -> stringResource(R.string.care_swm_home_by, timeOf(session.expectedArrival))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = sakhiSecondaryLabel(),
                        )
                        Spacer(Modifier.height(SakhiSpacing.space4))
                        WalkProgress(progress = session.progress(now).toFloat(), color = accent)
                        Spacer(Modifier.height(SakhiSpacing.space4))
                        SakhiListDivider()
                        Spacer(Modifier.height(SakhiSpacing.space3))
                        val battery = session.lastLocation?.batteryPercent
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Place,
                                contentDescription = null,
                                tint = if (sharing) MaterialTheme.colorScheme.primary else sakhiTertiaryLabel(),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(SakhiSpacing.space2))
                            Text(
                                text = when {
                                    !sharing -> stringResource(R.string.care_swm_not_sharing)
                                    battery != null -> stringResource(R.string.care_swm_sharing_battery, battery)
                                    else -> stringResource(R.string.care_swm_sharing)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = sakhiLabel(),
                            )
                        }
                    }
                }
            }

            item(key = "swm-stop") {
                TextButton(
                    onClick = { confirmStop = true },
                    modifier = Modifier.fillMaxWidth().padding(top = SakhiSpacing.space3),
                ) {
                    Text(
                        text = stringResource(R.string.care_swm_stop),
                        style = MaterialTheme.typography.bodyLarge,
                        color = sakhiSecondaryLabel(),
                    )
                }
            }
        }

        SakhiFooter(
            primaryLabel = stringResource(R.string.care_swm_im_home),
            onPrimaryClick = onArrive,
            primaryEnabled = !isBusy,
            secondaryLabel = stringResource(R.string.care_swm_extend),
            onSecondaryClick = onExtend,
            secondaryEnabled = !isBusy,
        )
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
        InitialAvatar(initial = name.take(1).uppercase(), size = 40)
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
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val phase = session.phase(now)
    val isLate = phase == StayWithMePhase.LATE
    val accent = if (isLate) AppleSystemColors.red else MaterialTheme.colorScheme.primary
    val location = session.lastLocation

    Box(modifier = Modifier.fillMaxSize().background(sakhiGroupedBackground())) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(0.52f)) {
                WalkMap(
                    location = location,
                    accent = accent,
                    initial = herName.take(1).uppercase(),
                    modifier = Modifier.fillMaxSize(),
                )
                if (location == null) {
                    MapNotice(
                        text = stringResource(R.string.care_swm_waiting_location),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Spacer(Modifier.weight(0.48f))
        }

        // Close, over the map.
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(SakhiSpacing.space4)
                .size(40.dp)
                .clip(CircleShape)
                .background(sakhiSystemBackground())
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.care_swm_close),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        // The panel rises 24dp over the map's bottom edge.
        Surface(
            color = sakhiSystemBackground(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.52f),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                item(key = "who") {
                    Row(
                        modifier = Modifier.padding(
                            start = SakhiSpacing.space6,
                            end = SakhiSpacing.space6,
                            top = SakhiSpacing.space6,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InitialAvatar(initial = herName.take(1).uppercase(), size = 48, fill = accent)
                        Spacer(Modifier.width(SakhiSpacing.space3))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isLate) {
                                    stringResource(R.string.care_swm_her_late, herName)
                                } else {
                                    stringResource(R.string.care_swm_her_walking, herName)
                                },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isLate) AppleSystemColors.red else sakhiLabel(),
                            )
                            Text(
                                text = if (phase == StayWithMePhase.WALKING) {
                                    stringResource(
                                        R.string.care_swm_her_home_by,
                                        timeOf(session.expectedArrival),
                                        minutesUp(session.secondsRemaining(now)),
                                    )
                                } else {
                                    stringResource(R.string.care_swm_her_due, timeOf(session.expectedArrival))
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = sakhiSecondaryLabel(),
                            )
                        }
                    }
                }

                item(key = "chips") {
                    Row(
                        modifier = Modifier.padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space4),
                        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                    ) {
                        location?.batteryPercent?.let { battery ->
                            InfoChip(
                                icon = if (location.isCharging == true) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                                text = "$battery%",
                                tint = if (battery <= 15 && location.isCharging != true) AppleSystemColors.red else sakhiLabel(),
                            )
                        }
                        session.locationAgeSeconds(now)?.let { age ->
                            InfoChip(icon = Icons.Filled.Schedule, text = updatedText(age), tint = sakhiLabel())
                        }
                        session.note?.let { note ->
                            InfoChip(icon = Icons.Filled.Place, text = note, tint = sakhiLabel())
                        }
                    }
                }

                if (isLate) {
                    item(key = "late") {
                        Text(
                            text = stringResource(R.string.care_swm_late_banner),
                            style = MaterialTheme.typography.bodyMedium,
                            color = sakhiLabel(),
                            modifier = Modifier
                                .padding(horizontal = SakhiSpacing.space6)
                                .fillMaxWidth()
                                .background(AppleSystemColors.red.copy(alpha = 0.10f), RoundedCornerShape(SakhiRadius.lg))
                                .padding(SakhiSpacing.space4),
                        )
                    }
                }

                item(key = "near-label") { SwmSectionLabel(stringResource(R.string.care_swm_help_near_her), top = SakhiSpacing.space5) }
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

                item(key = "lines-label") { SwmSectionLabel(stringResource(R.string.care_swm_helplines), top = SakhiSpacing.space5) }
                item(key = "lines") {
                    GroupedCard {
                        HelplineRow(label = stringResource(R.string.care_swm_emergency), number = "112") { dial(context, "112") }
                        SakhiListDivider(startInset = 56.dp)
                        HelplineRow(label = stringResource(R.string.care_swm_women_helpline), number = "181") { dial(context, "181") }
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
        EmergencySafePlaceKind.HOSPITAL -> Icons.Filled.LocalHospital to AppleSystemColors.red
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
private fun HelplineRow(label: String, number: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(SakhiRadius.sm)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(SakhiSpacing.space3))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = sakhiLabel(), modifier = Modifier.weight(1f))
        Text(
            text = number,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
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
private fun WalkMap(
    location: StayWithMeLocation?,
    accent: Color,
    initial: String,
    modifier: Modifier = Modifier,
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
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        val zoom = cameraState.position.zoom.coerceAtLeast(15.5f)
        runCatching { cameraState.animate(CameraUpdateFactory.newLatLngZoom(target, zoom), 1_000) }
    }
    val shown = interpolate(from, to, glide.value)

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraState,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false,
        ),
    ) {
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
            val icon = remember(accent, initial) {
                BitmapDescriptorFactory.fromBitmap(markerBitmap(context, accent.toArgb(), initial))
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
private fun markerBitmap(context: Context, color: Int, initial: String): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (if (initial.isEmpty()) 22 else 40) * density
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

@Composable
private fun InitialAvatar(initial: String, size: Int, fill: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier.size(size.dp).background(fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = (size * 0.4f).sp),
            color = Color.White,
        )
    }
}

@Composable
private fun GroupedCard(content: @Composable () -> Unit) {
    Surface(
        color = sakhiGroupedBackground(),
        shape = RoundedCornerShape(SakhiRadius.xxl),
        modifier = Modifier.fillMaxWidth().padding(horizontal = SakhiSpacing.space6),
    ) {
        Column { content() }
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

private fun minutesUp(seconds: Long): Int = ((seconds + 59) / 60).toInt().coerceAtLeast(0)

private fun timeOf(instant: Instant): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(instant.toEpochMilliseconds()))

private fun requiredPermissions(): List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Only fills in the number. The call is always theirs to place. */
private fun dial(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openDirections(context: Context, place: EmergencySafePlace) {
    val uri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${Uri.encode(place.name)})")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private val DEFAULT_CENTER = LatLng(28.6139, 77.2090)
