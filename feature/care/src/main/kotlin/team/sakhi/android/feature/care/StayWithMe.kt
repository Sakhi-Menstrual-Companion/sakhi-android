package team.sakhi.android.feature.care

import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import team.sakhi.android.designsystem.AppleSystemColors
import team.sakhi.android.platform.StayWithMeAskDetails
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
import team.sakhi.android.ui.SakhiConnectFooter
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
/**
 * What she fills in before a walk: where, by when, how often Sakhi asks. Held apart from the
 * layout so the form can scroll in the panel while the button stays pinned under it.
 */
@Stable
internal class StayWithMeStartState(initialCheckInMinutes: Int) {
    var minutes by mutableIntStateOf(StayWithMeDurations.DEFAULT_MINUTES)
    var note by mutableStateOf("")
    /** Where she is going, once picked from the suggestions. Typed but not picked stays her own words. */
    var destination by mutableStateOf<StayWithMeDestination?>(null)
    var suggestions by mutableStateOf<List<StayWithMeDestinationSearch.Suggestion>>(emptyList())
    var checkInMinutes by mutableIntStateOf(initialCheckInMinutes)
    var pickingDuration by mutableStateOf(false)
    var pickingCheckIn by mutableStateOf(false)
}

@Composable
internal fun rememberStayWithMeStartState(ask: StayWithMeAskDetails? = null): StayWithMeStartState {
    val context = LocalContext.current
    val checkInPreference = koinInject<StayWithMeCheckInPreference>()
    // What her person asked for is already chosen when she opens it: where, and for how long.
    val state = remember(ask) {
        StayWithMeStartState(checkInPreference.minutes()).also { fresh ->
            ask?.destination?.let { place ->
                fresh.destination = place
                fresh.note = place.name
            }
            ask?.minutes?.let { fresh.minutes = it }
        }
    }
    val search = remember { StayWithMeDestinationSearch(context.applicationContext) }
    LaunchedEffect(state.note, state.destination) {
        if (state.destination != null) { state.suggestions = emptyList(); return@LaunchedEffect }
        delay(350)
        state.suggestions = search.search(state.note)
    }
    return state
}

/** Where she is going: a search field until a place is picked, then the place with Edit. */
@Composable
internal fun StayWithMeDestinationSection(state: StayWithMeStartState) {
    var note by state::note
    var destination by state::destination
    val suggestions = state.suggestions
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

}

/** Reach by and, on her own screen, Check in: two settings rows in one card. */
@Composable
internal fun StayWithMeSettingsSection(state: StayWithMeStartState, includeCheckIn: Boolean) {
    var minutes by state::minutes
    val checkInPreference = koinInject<StayWithMeCheckInPreference>()
    var checkInMinutes by state::checkInMinutes
    var pickingDuration by state::pickingDuration
    var pickingCheckIn by state::pickingCheckIn
    val reachBy = remember(minutes) { Clock.System.now().plus(minutes.toLong(), DateTimeUnit.MINUTE) }
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
        if (includeCheckIn) {
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
    }
}

/**
 * The card she sets a walk off from, in iOS's order (`StayWithMeCard`): the destination as a
 * search field, Reach by and Check in as two settings rows, the three numbers, then who is
 * being asked. When her person has asked, their face and what they said head it. The button
 * is not in here: it is pinned under the panel by [StayWithMeStartFooter].
 */
@Composable
internal fun StayWithMeStartForm(
    state: StayWithMeStartState,
    personName: String,
    askerFaceIndex: Int?,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (askerFaceIndex != null) {
            AskHeader(faceIndex = askerFaceIndex)
            Spacer(Modifier.height(16.dp))
        }
        StayWithMeDestinationSection(state)
        Spacer(Modifier.height(14.dp))
        StayWithMeSettingsSection(state, includeCheckIn = true)
        Spacer(Modifier.height(18.dp))

        // 3. The three numbers, in their own section and in the same component the connected
        // screen and the alarm screen use, so an emergency looks the same everywhere in this
        // feature (Karan, 2026-09-18). It is also the only real colour on this card: a blue
        // shield and a red cross next to the pink helpline, against rows that are otherwise
        // all pink discs.
        Text(
            text = stringResource(R.string.care_swm_call_for_help),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiLabel(),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        HelplineCallButtons(onCall = { number -> dial(context, number) })

        Spacer(Modifier.height(18.dp))

        // 4. Who is being asked. Her Sakhi and nobody else: the helplines above are not
        // contacts, and there has never been a way to add a number here.
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
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * The button that sets her off, pinned at the bottom of the panel with the consent line under
 * it, or, when her person has asked, the two answers. Also asks for the permissions the walk
 * needs, at the moment she needs them and never on first open.
 */
@Composable
internal fun StayWithMeStartFooter(
    state: StayWithMeStartState,
    personName: String,
    isBusy: Boolean,
    error: String?,
    fromAsk: Boolean,
    onStart: (minutes: Int, note: String, destination: StayWithMeDestination?) -> Unit,
    onReject: () -> Unit,
) {
    val context = LocalContext.current
    // If she says no to location, the walk still starts: her person still gets told if she
    // does not reach.
    var pending by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (pending) onStart(state.minutes, state.note, state.destination)
        pending = false
    }
    val startWalk = {
        val missing = requiredPermissions().filterNot { granted(context, it) }
        if (missing.isEmpty()) {
            onStart(state.minutes, state.note, state.destination)
        } else {
            pending = true
            launcher.launch(missing.toTypedArray())
        }
    }
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp),
    ) {
        if (fromAsk) {
            AskAnswerButtons(isBusy = isBusy, onReject = onReject, onAccept = startWalk)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .background(RideStyle.pink.copy(alpha = if (isBusy) 0.4f else 1f), CircleShape)
                    .clickable(enabled = !isBusy) { startWalk() },
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

    }
}

/** Who is asking: their face and what they are asking, at the top of the panel. */
@Composable
private fun AskHeader(faceIndex: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(CareAvatars.drawable(faceIndex)),
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(RideStyle.soft, CircleShape),
        )
        Text(
            text = stringResource(R.string.care_swm_ask_banner),
            fontSize = 19.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiLabel(),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Reject in red, Accept in green, side by side. Accept is the same tap as "Stay with me". */
@Composable
private fun AskAnswerButtons(isBusy: Boolean, onReject: () -> Unit, onAccept: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
                .background(AppleSystemColors.red, CircleShape)
                .clickable(enabled = !isBusy, onClick = onReject),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.care_swm_ask_reject),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
                .background(AppleSystemColors.green, CircleShape)
                .clickable(enabled = !isBusy, onClick = onAccept),
            contentAlignment = Alignment.Center,
        ) {
            if (isBusy) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = stringResource(R.string.care_swm_ask_accept),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
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
 * Her own screen once a walk is running. iOS's `StayWithMeLiveScreen`, walking side.
 *
 * The same map-first layout her person sees, deliberately. She is the one out there, so she
 * gets the bigger picture, not the smaller one: the way she has come, where she is now, her
 * person right there on the screen with her, and the police station and helplines within
 * reach without leaving the walk. The countdown and "I'm home" sit on top of that, not
 * instead of it.
 *
 * At rest the panel is the ride at a glance and its two buttons. Pulled up, the rest sits
 * under it: the numbers to call, help near her, and the way out of the walk. While
 * "Are you okay?" is waiting, the panel drops to its summary and puts its buttons away, so
 * the box's "I'm okay" is the only thing on the screen to press.
 *
 * "I'm home" and "Stop sharing" ask for her fingerprint or face first, so someone else
 * holding her phone cannot end the walk for her (iOS asks for Face ID).
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
    /** Kept for the callers; her own screen has no refresh, as on iOS. */
    onRefresh: () -> Unit = {},
    route: WalkRoute? = null,
    /** Kept for the callers; her own screen has no refresh, as on iOS. */
    refreshing: Boolean = false,
    /** Bumped when a refresh lands, so the map frames her again even if she has not moved. */
    recenterKey: Int = 0,
    /** Their face, as the server keeps it. Null falls back to the shared hash. */
    otherFaceIndex: Int? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirmItIsHer = rememberConfirmItIsHer()
    val homeReason = stringResource(R.string.ride_home_reason)
    val stopReason = stringResource(R.string.ride_stop_reason)
    val phase = session.phase(now)
    // Pink while she is inside her time, red the moment she is not.
    val accent = if (phase == StayWithMePhase.WALKING) MaterialTheme.colorScheme.primary else RideStyle.alert
    var confirmStop by remember { mutableStateOf(false) }
    // A check of her fingerprint or face is up: the two buttons wait for it.
    var confirming by remember { mutableStateOf(false) }
    val sharing = StayWithMeLocationService.hasLocationPermission(context)
    val location = session.lastLocation
    // Her map is a navigation camera from the moment the walk starts.
    var camera by remember { mutableStateOf(WalkCameraMode.HEADING) }
    var bearing by remember { mutableFloatStateOf(0f) }
    val heading = remember(trail) { travelBearing(trail) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(sakhiGroupedBackground())) {
        // The panel at rest, and the room the check-in box takes above it. The map and its
        // controls move up by this much while the box is showing, so it never covers the
        // map's own logo.
        val restingHeight = if (checkInRequested) 216.dp else 318.dp
        val checkInClearance = if (checkInRequested) 168.dp else 0.dp
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        // Following the road she sits low in the room the panel leaves, so most of the screen
        // is what is ahead of her, the way every navigation app places "you".
        val lift = if (camera == WalkCameraMode.HEADING) {
            ((maxHeight - restingHeight - 64.dp) * 0.35f).coerceAtLeast(0.dp)
        } else {
            0.dp
        }

        // The map is the whole screen. It frames her in the part the panel leaves uncovered
        // rather than centring her underneath it.
        WalkMap(
            location = location,
            accent = accent,
            initial = "",
            trail = trail,
            modifier = Modifier.fillMaxSize(),
            bottomPadding = restingHeight + checkInClearance + bottomInset,
            topPadding = statusTop + 64.dp + lift,
            destination = session.destination,
            routeLine = route?.points.orEmpty(),
            recenterKey = recenterKey,
            places = places,
            camera = camera,
            heading = heading,
            onUserGesture = { camera = WalkCameraMode.FREE },
            onBearingChange = { bearing = it },
            showAccuracy = false,
        )
        if (location == null || !sharing) {
            MapNotice(
                text = if (sharing) stringResource(R.string.care_swm_finding) else stringResource(R.string.care_swm_not_sharing),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.3f),
            )
        }

        RideMapControls(
            camera = camera,
            bearing = bearing,
            onCompass = {
                camera = if (camera == WalkCameraMode.HEADING) WalkCameraMode.FOLLOW else WalkCameraMode.HEADING
            },
            onOverview = { camera = WalkCameraMode.OVERVIEW },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = restingHeight + checkInClearance + bottomInset + 14.dp),
        )

        // Who is with her, live, in the one place the eye lands first.
        val chip = when {
            phase == StayWithMePhase.LATE -> RideChip(
                text = stringResource(R.string.care_swm_person_has_been_told, personName),
                dot = RideStyle.alert,
                live = true,
            )
            session.isWatcherPresent(now) -> RideChip(
                text = stringResource(R.string.care_swm_is_with_you, personName),
                dot = RideStyle.pink,
                live = true,
            )
            else -> RideChip(
                text = stringResource(R.string.ride_waiting_for_person, personName),
                dot = sakhiTertiaryLabel(),
                live = false,
            )
        }
        RideTopBar(
            onClose = onClose,
            chip = chip,
            onCallPolice = { dial(context, "112") },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // The check-in rises from the bottom and settles on top of the panel, and stays until
        // she answers (Karan, 2026-09-16). The panel drops to its summary meanwhile.
        AnimatedVisibility(
            visible = checkInRequested,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = restingHeight + bottomInset + 10.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            RideCheckInBox(
                personName = personName,
                checking = checkInChecking,
                failed = checkInFailed,
                onOkay = onCheckInOkay,
                onGetHelp = { dial(context, "112") },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        // Two buttons, pinned under the panel at every height: more time as the small one, the
        // way out of the ride as the one wide pink button on the screen.
        val footerButtons: @Composable () -> Unit = {
            RideFooterButtons(
                onExtend = onExtend,
                onArrive = {
                    scope.launch {
                        confirming = true
                        val isHer = confirmItIsHer(homeReason)
                        confirming = false
                        if (isHer) onArrive()
                    }
                },
                enabled = !isBusy && !confirming,
                busy = isBusy,
            )
        }

        RideBottomPanel(
            restingHeight = restingHeight,
            fullHeight = maxHeight * 0.9f,
            modifier = Modifier.align(Alignment.BottomCenter),
            resetKey = session.id,
            footer = footerButtons.takeIf { !checkInRequested },
        ) {
            item(key = "summary") {
                val status = rideOwnerStatus(session, now, route)
                val hero = rideOwnerHero(session, now, route, personName)
                val present = session.isWatcherPresent(now)
                RideSummary(
                    heading = session.destination?.name?.let { stringResource(R.string.care_swm_to_place, it) }
                        ?: session.note?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.care_swm_to_place, it) }
                        ?: stringResource(R.string.care_swm_your_ride_home),
                    // On her own screen the house stays a house: the alert is her person's.
                    alerted = false,
                    statusLabel = stringResource(status.ownerLabelRes),
                    statusTint = status.tint(),
                    trackTint = status.trackTint(),
                    heroValue = hero.value,
                    heroUnit = hero.unit,
                    heroDetail = hero.detail,
                    heroTint = rideHeroTint(phase),
                    faceIndex = otherFaceIndex ?: CareAvatars.indexFor(session.watcherUserId),
                    faceCaption = personName,
                    facePresent = present,
                    faceDescription = if (present) {
                        stringResource(R.string.care_swm_is_with_you, personName)
                    } else {
                        stringResource(R.string.ride_person_not_opened, personName)
                    },
                    progress = rideProgress(session, now, trail),
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

            // Everything under the summary waits while a check-in is up, so the box's
            // "I'm okay" is the only thing to press.
            if (!checkInRequested) {
                // The three numbers anyone in India might need, as three tiles side by side.
                // Every tile only opens the dialler.
                item(key = "call") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        RideSectionHeader(title = stringResource(R.string.care_swm_call_for_help))
                        HelplineCallButtons(onCall = { number -> dial(context, number) })
                    }
                }

                // Police, hospitals and medical shops, looked up again as she moves.
                item(key = "help") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        RideSectionHeader(
                            title = stringResource(R.string.ride_help_near_you),
                            caption = stringResource(R.string.ride_updates_you_move),
                        )
                        RideCardGroup {
                            RideNearbyRows(
                                places = places,
                                loading = placesLoading,
                                loadingText = stringResource(R.string.care_swm_places_loading_you),
                            )
                        }
                    }
                }

                item(key = "stop") {
                    RideStopButton(
                        onClick = { confirmStop = true },
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 28.dp, bottom = 32.dp),
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
                scope.launch {
                    if (confirmItIsHer(stopReason)) onStop()
                }
            },
            secondaryLabel = stringResource(R.string.care_swm_keep_sharing),
            onSecondaryClick = { confirmStop = false },
            onDismissRequest = { confirmStop = false },
        )
    }
}

/** The places near her as rows, or what it says while it looks and when it finds none. */
@Composable
private fun RideNearbyRows(places: List<EmergencySafePlace>, loading: Boolean, loadingText: String) {
    val context = LocalContext.current
    if (places.isEmpty()) {
        RideEmptyRow(
            loading = loading,
            loadingText = loadingText,
            emptyText = stringResource(R.string.ride_nothing_close),
        )
    } else {
        places.forEachIndexed { index, place ->
            if (index > 0) RideRowDivider()
            RidePlaceRow(place = place, onClick = { openDirections(context, place) })
        }
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

/** Behind her and tilted, the road ahead: what a navigation app shows. iOS's 650 m at 60 degrees. */
private const val HEADING_ZOOM = 17f
private const val HEADING_TILT = 60f

/** North up, on her: a few streets around. iOS's 1100 m. */
private const val FOLLOW_ZOOM = 16f

/** How much of the screen each side's panel takes. The map is the rest, and behind it. */
private const val OWNER_PANEL_FRACTION = 0.58f
private const val WATCHER_PANEL_FRACTION = 0.52f

/**
 * The controls over the map: a 35dp white disc at the top left, and Contact Police as a
 * white capsule opposite it.
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
 * iOS's `StayWithMeLiveScreen`, staying side.
 *
 * The map takes the screen and a panel rises over its bottom edge. At rest it is the ride at
 * a glance: where to, the one number that matters, her face and how far along she is. Pulled
 * up, what her phone is telling him, the numbers to call, and help near her.
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
    /** Her face, as the server keeps it. Null falls back to the shared hash. */
    herFaceIndex: Int? = null,
) {
    val context = LocalContext.current
    val phase = session.phase(now)
    val isLate = phase == StayWithMePhase.LATE
    val accent = if (phase == StayWithMePhase.WALKING) MaterialTheme.colorScheme.primary else RideStyle.alert
    val location = session.lastLocation
    val face = herFaceIndex ?: CareAvatars.indexFor(session.ownerUserId)
    val ageSeconds = session.locationAgeSeconds(now)

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(sakhiGroupedBackground())) {
        // iOS rests at 216, which just holds this summary in its own font; Lato here is a
        // little taller and the "Started" row was cut by the navigation bar.
        val restingHeight = 232.dp
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        WalkMap(
            location = location,
            accent = accent,
            // On his phone she is "she", and her marker is a face, not the first letter of
            // whatever her profile happens to be called ("U" for "User").
            initial = "",
            avatarWithoutName = true,
            faceIndex = face,
            trail = trail,
            modifier = Modifier.fillMaxSize(),
            bottomPadding = restingHeight + bottomInset,
            topPadding = statusTop + 64.dp,
            destination = session.destination,
            routeLine = route?.points.orEmpty(),
            recenterKey = recenterKey,
            places = places,
            showAccuracy = false,
        )
        if (location == null) {
            MapNotice(
                text = stringResource(R.string.care_swm_waiting_location),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.3f),
            )
        }

        // Three things over the map: the way out, how fresh her position is, and the one
        // number worth a tap without reading.
        val chip = if (ageSeconds == null) {
            RideChip(
                text = stringResource(R.string.care_swm_waiting_location),
                dot = sakhiTertiaryLabel(),
                live = false,
            )
        } else {
            RideChip(
                text = updatedText(ageSeconds),
                dot = if (ageSeconds < 120L) RideStyle.pink else RideStyle.late,
                live = ageSeconds < 120L,
            )
        }
        RideTopBar(
            onClose = onClose,
            chip = chip,
            onCallPolice = { dial(context, "112") },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        RideBottomPanel(
            restingHeight = restingHeight,
            fullHeight = maxHeight * 0.9f,
            modifier = Modifier.align(Alignment.BottomCenter),
            resetKey = session.id,
        ) {
            item(key = "summary") {
                val status = rideWatcherStatus(session, now, route)
                val hero = rideWatcherHero(session, now, route)
                val live = (ageSeconds ?: Long.MAX_VALUE) < 120L
                RideSummary(
                    heading = when {
                        isLate -> stringResource(R.string.care_swm_not_reached_heading)
                        // "She", not her profile name: on his phone she is "she".
                        session.destination != null && !session.destination!!.name.equals("home", true) ->
                            stringResource(R.string.ride_she_going_to, session.destination!!.name)
                        else -> stringResource(R.string.care_swm_going_home)
                    },
                    alerted = isLate,
                    statusLabel = stringResource(status.labelRes),
                    statusTint = status.tint(),
                    trackTint = status.trackTint(),
                    heroValue = hero.value,
                    heroUnit = hero.unit,
                    heroDetail = hero.detail,
                    heroTint = rideHeroTint(phase),
                    faceIndex = face,
                    faceCaption = stringResource(R.string.care_swm_her),
                    facePresent = live,
                    faceDescription = if (live) {
                        stringResource(R.string.ride_location_live)
                    } else {
                        stringResource(R.string.ride_location_not_fresh)
                    },
                    progress = rideProgress(session, now, trail),
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

            if (isLate) {
                item(key = "late") {
                    RideLateBanner(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp))
                }
            }

            // What her phone is telling him.
            item(key = "her-phone") {
                RideHerPhoneCard(
                    destinationName = session.destination?.name,
                    locationTitle = ageSeconds?.let { updatedText(it) } ?: stringResource(R.string.care_swm_waiting_location),
                    batteryPercent = location?.batteryPercent,
                    charging = location?.isCharging == true,
                    onRefresh = onRefresh,
                    refreshing = refreshing,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp),
                )
            }

            item(key = "call") {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    RideSectionHeader(title = stringResource(R.string.care_swm_call_for_help))
                    HelplineCallButtons(onCall = { number -> dial(context, number) })
                }
            }

            item(key = "help") {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    RideSectionHeader(
                        title = stringResource(R.string.ride_help_near_her),
                        caption = stringResource(R.string.care_swm_help_near_her_note),
                    )
                    RideCardGroup {
                        RideNearbyRows(
                            places = places,
                            loading = placesLoading,
                            loadingText = stringResource(R.string.care_swm_places_loading),
                        )
                    }
                }
            }

            item(key = "privacy") {
                Text(
                    text = stringResource(R.string.ride_privacy_note),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = sakhiTertiaryLabel(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 28.dp, bottom = 32.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared pieces
// ─────────────────────────────────────────────────────────────────────────────

/**
 * How the camera on her ride moves. iOS's `WalkCamera`.
 *
 * Her map is a navigation camera from the moment the ride starts, and it stays one until she
 * moves the map herself.
 */
internal enum class WalkCameraMode {
    /** The whole way in view, north up. */
    OVERVIEW,

    /** Centred on her, north up. */
    FOLLOW,

    /** Behind her and tilted, turned the way she is travelling: a navigation app's camera. */
    HEADING,

    /** She moved the map herself: it stays where she put it until she taps the compass. */
    FREE,
}

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
    /** Help around her, drawn as small discs. Empty leaves the map as it was. */
    places: List<EmergencySafePlace> = emptyList(),
    /**
     * How a ride's camera moves. Null keeps the plain framing: the start screen, the Care
     * card, the alarm and her person's map.
     */
    camera: WalkCameraMode? = null,
    /** Which way she is travelling, in degrees clockwise from north, for the navigation camera. */
    heading: Float? = null,
    /** She moved the map with her fingers, so the ride stops steering it. */
    onUserGesture: (() -> Unit)? = null,
    /** The map's turn, for a compass needle. */
    onBearingChange: ((Float) -> Unit)? = null,
    /**
     * Whether a soft circle shows how sure the fix is. iOS draws none on a ride, where a
     * tilted camera made it a large blob under her, so the ride screens pass false.
     */
    showAccuracy: Boolean = true,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
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
    LaunchedEffect(target, destination, routeLine.size, mapLoaded, recenterKey, camera) {
        if (target == null || !mapLoaded || camera != null) return@LaunchedEffect
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

    // A ride's camera. Moves only when something has actually changed: re-issuing the same
    // camera on every tick is what makes a map feel like it is fighting the person looking at it.
    var lastHeading by remember { mutableStateOf(0f) }
    if (heading != null) lastHeading = heading
    LaunchedEffect(camera, target, heading, routeLine.size, mapLoaded, recenterKey) {
        if (camera == null || target == null || !mapLoaded) return@LaunchedEffect
        when (camera) {
            WalkCameraMode.FREE -> Unit
            WalkCameraMode.HEADING -> runCatching {
                cameraState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(target).zoom(HEADING_ZOOM).tilt(HEADING_TILT).bearing(lastHeading).build(),
                    ),
                    600,
                )
            }
            WalkCameraMode.FOLLOW -> runCatching {
                cameraState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(target).zoom(FOLLOW_ZOOM).tilt(0f).bearing(0f).build(),
                    ),
                    600,
                )
            }
            WalkCameraMode.OVERVIEW -> {
                // Her, the way ahead, the last of the way she has come and where she is going.
                val bounds = LatLngBounds.builder().include(target)
                routeLine.forEach { bounds.include(LatLng(it.first, it.second)) }
                trail.takeLast(60).forEach { bounds.include(LatLng(it.latitude, it.longitude)) }
                destination?.let { bounds.include(LatLng(it.latitude, it.longitude)) }
                runCatching { cameraState.animate(CameraUpdateFactory.newLatLngBounds(bounds.build(), 160), 800) }
                if (cameraState.position.zoom > MAX_FRAMING_ZOOM) {
                    runCatching { cameraState.animate(CameraUpdateFactory.zoomTo(MAX_FRAMING_ZOOM), 400) }
                }
            }
        }
    }
    // Her fingers win until something asks for the map again.
    LaunchedEffect(cameraState.isMoving) {
        if (
            onUserGesture != null &&
            cameraState.isMoving &&
            cameraState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
        ) {
            onUserGesture()
        }
    }
    val bearingCallback by rememberUpdatedState(onBearingChange)
    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.position.bearing }.collect { bearingCallback?.invoke(it) }
    }

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
        // The way she has come, faint and dashed, under everything else (iOS's dotted trail).
        if (trail.size >= 2) {
            val points = remember(trail) { trail.map { LatLng(it.latitude, it.longitude) } }
            val trailWidth = with(density) { 4.dp.toPx() }
            Polyline(
                points = points,
                color = accent.copy(alpha = 0.45f),
                width = trailWidth,
                pattern = listOf(Dot(), Gap(trailWidth * 2f)),
                jointType = JointType.ROUND,
                startCap = RoundCap(),
                endCap = RoundCap(),
            )
        }
        // The way ahead, solid, with a white casing under the colour the way map apps draw a
        // route, so it holds its edge over any road colour.
        if (routeLine.size >= 2) {
            val ahead = remember(routeLine) { routeLine.map { LatLng(it.first, it.second) } }
            Polyline(
                points = ahead,
                color = Color.White,
                width = with(density) { 11.dp.toPx() },
                jointType = JointType.ROUND,
                startCap = RoundCap(),
                endCap = RoundCap(),
            )
            Polyline(
                points = ahead,
                color = accent,
                width = with(density) { 6.5.dp.toPx() },
                jointType = JointType.ROUND,
                startCap = RoundCap(),
                endCap = RoundCap(),
            )
        }
        // Help near her, as small white discs: the glyph says what each place is.
        places.forEach { place ->
            key(place.id) {
                MarkerComposable(
                    keys = arrayOf(place.id, place.kind),
                    state = rememberMarkerState(position = LatLng(place.latitude, place.longitude)),
                    anchor = Offset(0.5f, 0.5f),
                ) {
                    RidePlaceDisc(kind = place.kind)
                }
            }
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

        if (shown != null) {
            if (showAccuracy) {
                val accuracy = (location?.accuracyMeters ?: 40.0).coerceIn(15.0, 150.0)
                Circle(
                    center = shown,
                    radius = accuracy,
                    fillColor = accent.copy(alpha = 0.12f),
                    strokeColor = accent.copy(alpha = 0.35f),
                    strokeWidth = 2f,
                )
            }
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

/** A quiet line floating over the map while there is nothing on it yet. iOS's `mapNotice`. */
@Composable
private fun MapNotice(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = sakhiSecondaryLabel(),
        modifier = modifier
            .background(RideStyle.floating, CircleShape)
            .border(0.5.dp, RideStyle.hairline, CircleShape)
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

/** How old her position is, in iOS's steps: just now under twenty seconds, then seconds, then minutes rounded up. */
@Composable
private fun updatedText(ageSeconds: Long): String = when {
    ageSeconds < 20 -> stringResource(R.string.care_swm_updated_now)
    ageSeconds < 60 -> stringResource(R.string.care_swm_updated_seconds, ageSeconds.toInt())
    else -> stringResource(R.string.care_swm_updated_minutes, ((ageSeconds + 59) / 60).toInt())
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

/**
 * Driving directions to a place: she is in a cab or an auto, not on foot. Maps' own turn-by-turn
 * screen where the phone has it, and the place on the map where it has not.
 */
private fun openDirections(context: Context, place: EmergencySafePlace) {
    val driving = Uri.parse("google.navigation:q=${place.latitude},${place.longitude}&mode=d")
    val opened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, driving).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (opened) return
    val uri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${Uri.encode(place.name)})")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private val DEFAULT_CENTER = LatLng(28.6139, 77.2090)
