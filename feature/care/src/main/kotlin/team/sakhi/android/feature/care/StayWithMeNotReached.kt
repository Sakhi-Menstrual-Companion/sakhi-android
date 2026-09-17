package team.sakhi.android.feature.care

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import team.sakhi.android.platform.StayWithMeAlarmPlayer
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiLightPink
import team.sakhi.android.designsystem.sakhiDeepRose
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiProfileCardBackground
import team.sakhi.android.designsystem.sakhiSeparator
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.staywithme.StayWithMeLocation
import team.sakhi.staywithme.StayWithMeSession
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * "She has not reached yet", on her person's phone, with the alarm going.
 *
 * The Android half of iOS's `StayWithMeNotReachedScreen`, screen for screen: her last
 * position as a map he can pan and zoom, a close and a police button over it, and a panel
 * holding her face, what has happened, the three numbers worth knowing before he calls, the
 * three helplines, and a slider he has to drag to say he has got it.
 *
 * Two decisions worth keeping, both Karan's:
 *  - It is slid, not tapped. An alarm that a sleeping hand can dismiss is not an alarm.
 *  - It never says she is in danger. Most of the time this is traffic, and the screen says
 *    so, above a button that calls her rather than the police.
 *
 * When the alarm should fire at all is [team.sakhi.staywithme.StayWithMeAlarm], shared with
 * iOS, so the two phones cannot disagree about the moment.
 */
@Composable
internal fun StayWithMeNotReachedScreen(
    session: StayWithMeSession,
    personName: String,
    faceIndex: Int,
    now: Instant,
    trail: List<StayWithMeLocation>,
    onAcknowledged: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val alarm = remember { StayWithMeAlarmPlayer(context) }
    DisposableEffect(session.id) {
        alarm.start()
        onDispose { alarm.stop() }
    }
    // She reached home while it was ringing: nothing to shout about any more.
    LaunchedEffect(session.status) {
        if (!session.status.isLive) alarm.stop()
    }

    val minutesLate = max(1, ((now - session.expectedArrival).inWholeMinutes).toInt())

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WalkMap(
            location = session.lastLocation,
            accent = AlarmRed,
            initial = "",
            avatarWithoutName = true,
            trail = trail,
            destination = session.destination,
            bottomPadding = PanelHeight,
            modifier = Modifier.fillMaxSize(),
        )

        // iOS puts a plain white circle with a cross on the left and a deep-rose 112 pill
        // on the right, not the "Contact Police" pill the rest of the walk screens use.
        AlarmTopBar(
            onClose = onClose,
            onCallPolice = { dial(context, "112") },
            modifier = Modifier.align(Alignment.TopStart),
        )

        // Pull it up for the rest. iOS's panel does the same, and in an emergency the last
        // thing to ask of someone is a scroll they cannot see the bottom of.
        var expanded by remember { mutableStateOf(false) }
        // iOS's 430 is 430 of content: its panel runs under the home indicator and adds the
        // safe area below. Android's gesture bar would otherwise eat that same 430 from the
        // inside, which pushed the three numbers' captions below the fold.
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val panelHeight by animateDpAsState(
            targetValue = (if (expanded) ExpandedPanelHeight else PanelHeight) + bottomInset,
            label = "alarmPanelHeight",
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(panelHeight),
            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
            // iOS fills this with `DS.Colors.background`, the app's own pink, not white.
            color = MaterialTheme.colorScheme.background,
            // The one shadow Sakhi allows, and only over a map: the panel has to read as
            // separate from what is under it. Same value as iOS's `WalkBottomPanel`.
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // The grabber, and the whole thing it belongs to, answers a drag either way.
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
                            .background(sakhiTertiaryLabel().copy(alpha = 0.35f), CircleShape),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(6.dp))
                    AlarmFace(faceIndex = faceIndex, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(14.dp))
                    AlarmHeadline(personName = personName, minutesLate = minutesLate)
                    Spacer(Modifier.height(20.dp))
                    AlarmFacts(session = session, now = now)
                    Spacer(Modifier.height(20.dp))
                    // iOS: a 17pt bold heading, ten points above the buttons, inset four.
                    Text(
                        text = stringResource(R.string.care_swm_alarm_call_label),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = sakhiLabel(),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    EmergencyCallButtons(onCall = { number -> dial(context, number) })
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.care_swm_alarm_disclaimer),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = sakhiTertiaryLabel(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                }

                SlideToConfirm(
                    title = stringResource(R.string.care_swm_alarm_slide),
                    onConfirmed = {
                        alarm.stop()
                        onAcknowledged()
                    },
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 6.dp, bottom = 8.dp)
                        .navigationBarsPadding(),
                )
            }
        }
    }
}

/**
 * The way out, and the one number worth a tap without reading.
 *
 * iOS's alarm screen uses a plain white circle with a cross and a deep-rose 112 pill, not
 * the "Contact Police" pill the other walk screens carry. Same sizes: a 40pt circle with a
 * half-point hairline, and a 40pt-tall capsule with 16 of padding either side.
 */
@Composable
private fun AlarmTopBar(
    onClose: () -> Unit,
    onCallPolice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeLabel = stringResource(R.string.care_swm_alarm_back)
    val policeLabel = stringResource(R.string.care_swm_alarm_call_police_a11y)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(sakhiSystemBackground(), CircleShape)
                .border(0.5.dp, sakhiSeparator(), CircleShape)
                .clickable(onClick = onClose)
                .semantics { contentDescription = closeLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = sakhiLabel(),
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .height(40.dp)
                .background(sakhiDeepRose(), CircleShape)
                .clickable(onClick = onCallPolice)
                .padding(horizontal = 16.dp)
                .semantics { contentDescription = policeLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Phone,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = "112",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

/** Her face inside a ring that breathes, so the eye lands on her and not on the alarm. */
@Composable
private fun AlarmFace(faceIndex: Int, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "alarmFace")
    val scale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "alarmFaceScale",
    )
    Box(modifier = modifier.size(116.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(116.dp)
                .scale(scale)
                .background(AlarmRed.copy(alpha = 0.14f), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(96.dp)
                .border(2.dp, AlarmRed.copy(alpha = 0.5f), CircleShape),
        )
        Image(
            painter = painterResource(CareAvatars.drawable(faceIndex)),
            contentDescription = null,
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(sakhiLightPink(), CircleShape)
                .scale(CareAvatars.scale(faceIndex)),
        )
    }
}

@Composable
private fun AlarmHeadline(personName: String, minutesLate: Int) {
    // "Rahul" is a name and can start a sentence. "Her" is what this phone calls her when it
    // has none, and "Her is 14 min past her time" is not a sentence (Karan, 2026-09-16).
    val standIns = setOf("her", "she", "your sakhi", "sakhi", "unknown", "")
    val trimmed = personName.trim()
    val subject = if (trimmed.lowercase() in standIns) stringResource(R.string.care_swm_alarm_subject_fallback) else trimmed

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.care_swm_alarm_title),
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiLabel(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.care_swm_alarm_body, subject, minutesLate),
            fontSize = 16.sp,
            lineHeight = 21.sp,
            color = sakhiSecondaryLabel(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        // Never a fright without a first step. Most of these are traffic.
        Text(
            text = stringResource(R.string.care_swm_alarm_first_step),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = sakhiLabel(),
            textAlign = TextAlign.Center,
        )
    }
}

/** When she was due, how fresh her position is, and how much battery she has left. */
@Composable
private fun AlarmFacts(session: StayWithMeSession, now: Instant) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = sakhiProfileCardBackground(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 18.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val lastSeen = session.lastLocation?.let { fix ->
                val minutes = ((now - fix.recordedAt).inWholeMinutes).toInt()
                when {
                    minutes < 1 -> stringResource(R.string.care_swm_alarm_just_now)
                    minutes < 60 -> stringResource(R.string.care_swm_alarm_minutes_ago, minutes)
                    else -> stringResource(R.string.care_swm_alarm_hours_ago, minutes / 60)
                }
            } ?: stringResource(R.string.care_swm_alarm_none)
            AlarmFact(
                value = timeOf(session.expectedArrival),
                caption = stringResource(R.string.care_swm_alarm_was_due),
                modifier = Modifier.weight(1f),
            )
            AlarmFactDivider()
            AlarmFact(
                value = lastSeen,
                caption = stringResource(R.string.care_swm_alarm_last_seen),
                modifier = Modifier.weight(1f),
            )
            AlarmFactDivider()
            AlarmFact(
                value = session.lastLocation?.batteryPercent?.let { "$it%" } ?: "--",
                caption = stringResource(R.string.care_swm_alarm_her_battery),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AlarmFact(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = value,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiLabel(),
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = caption,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            color = sakhiSecondaryLabel(),
            maxLines = 1,
        )
    }
}

@Composable
private fun AlarmFactDivider() {
    // iOS: a half-point separator, 36 tall.
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(36.dp)
            .background(sakhiSeparator()),
    )
}

/**
 * Drag the knob the whole way to confirm.
 *
 * A tap is something a phone in a pocket can do by itself, and "got it" has to mean he read
 * it. The knob springs back if he lets go early, and it takes three quarters of the way to
 * count, exactly as iOS's `SlideToConfirm` does.
 *
 * Its numbers are iOS's: a 64 tall white capsule, a five point inset, the knob filling the
 * rest, the title bold sixteen in secondary ink, fading as he slides, and the track filling
 * behind it in the alarm's own red at 14%.
 */
@Composable
internal fun SlideToConfirm(
    title: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var trackWidth by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableFloatStateOf(0f) }
    var confirmed by remember { mutableStateOf(false) }
    val knob = TrackHeight - TrackInset * 2
    val knobPx = with(density) { knob.toPx() }
    val insetPx = with(density) { TrackInset.toPx() }
    val span = (trackWidth - knobPx - insetPx * 2).coerceAtLeast(1f)
    val slid by animateFloatAsState(targetValue = offset, label = "slideOffset")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TrackHeight)
            .clip(CircleShape)
            .background(sakhiProfileCardBackground())
            .onSizeChanged { trackWidth = it.width.toFloat() }
            .semantics { contentDescription = title },
        contentAlignment = Alignment.CenterStart,
    ) {
        // What has been slid so far, in the alarm's own colour.
        Box(
            modifier = Modifier
                .padding(TrackInset)
                .height(knob)
                .width(with(density) { (knobPx + slid).toDp() })
                .clip(CircleShape)
                .background(AlarmRed.copy(alpha = 0.14f)),
        )
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiSecondaryLabel().copy(
                alpha = (1f - (slid / span) * 1.4f).coerceIn(0f, 1f),
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .offset { IntOffset((insetPx + slid).roundToInt(), 0) }
                .size(knob)
                .background(AlarmRed, CircleShape)
                .pointerInput(trackWidth) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (confirmed) return@detectHorizontalDragGestures
                            if (offset > span * 0.75f) {
                                confirmed = true
                                offset = span
                                onConfirmed()
                            } else {
                                offset = 0f
                            }
                        },
                        onDragCancel = { if (!confirmed) offset = 0f },
                    ) { _, dragAmount ->
                        if (confirmed) return@detectHorizontalDragGestures
                        offset = (offset + dragAmount).coerceIn(0f, span)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (confirmed) Icons.Filled.Check else Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** iOS uses `UIColor.systemRed` here, and only here. Nothing else on a walk screen is red. */
private val AlarmRed = Color(0xFFFF3B30)
private val PanelHeight = 430.dp
/** Pulled up: everything, including the three numbers to call. */
private val ExpandedPanelHeight = 680.dp
/** iOS: a 64 tall track with a five point inset, so the knob is 54. */
private val TrackHeight = 64.dp
private val TrackInset = 5.dp
