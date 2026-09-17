package team.sakhi.android.feature.care

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import team.sakhi.android.platform.StayWithMeAlarmPlayer
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
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

    Box(modifier = Modifier.fillMaxSize().background(sakhiSystemBackground())) {
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

        LiveWalkTopBar(onClose = onClose, modifier = Modifier.align(Alignment.TopStart))

        // Pull it up for the rest. iOS's panel does the same, and in an emergency the last
        // thing to ask of someone is a scroll they cannot see the bottom of.
        var expanded by remember { mutableStateOf(false) }
        val panelHeight by animateDpAsState(
            targetValue = if (expanded) ExpandedPanelHeight else PanelHeight,
            label = "alarmPanelHeight",
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(panelHeight),
            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
            color = sakhiSystemBackground(),
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
                    Spacer(Modifier.height(SakhiSpacing.space3))
                    AlarmFace(faceIndex = faceIndex, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(14.dp))
                    AlarmHeadline(personName = personName, minutesLate = minutesLate)
                    Spacer(Modifier.height(20.dp))
                    AlarmFacts(session = session, now = now)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.care_swm_alarm_call_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = sakhiSecondaryLabel(),
                    )
                    Spacer(Modifier.height(SakhiSpacing.space2))
                    EmergencyCallButtons(onCall = { number -> dial(context, number) })
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.care_swm_alarm_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = sakhiTertiaryLabel(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(SakhiSpacing.space4))
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
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
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
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
            color = sakhiLabel(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(SakhiSpacing.space2))
        Text(
            text = stringResource(R.string.care_swm_alarm_body, subject, minutesLate),
            style = MaterialTheme.typography.bodyLarge,
            color = sakhiSecondaryLabel(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(SakhiSpacing.space1))
        // Never a fright without a first step. Most of these are traffic.
        Text(
            text = stringResource(R.string.care_swm_alarm_first_step),
            style = MaterialTheme.typography.bodyMedium,
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
        color = MaterialTheme.colorScheme.surfaceVariant,
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
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
            color = sakhiLabel(),
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = sakhiTertiaryLabel(),
            maxLines = 1,
        )
    }
}

@Composable
private fun AlarmFactDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(sakhiTertiaryLabel().copy(alpha = 0.25f)),
    )
}

/**
 * Drag the knob the whole way to confirm.
 *
 * A tap is something a phone in a pocket can do by itself, and "got it" has to mean he read
 * it. The knob springs back if he lets go early.
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
    var done by remember { mutableFloatStateOf(0f) }
    val knobPx = with(density) { KnobSize.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(KnobSize + 12.dp)
            .clip(CircleShape)
            .background(AlarmRed.copy(alpha = 0.12f))
            .onSizeChanged { trackWidth = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = AlarmRed,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .padding(6.dp)
                .size(KnobSize)
                .background(AlarmRed, CircleShape)
                .pointerInput(trackWidth) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val limit = (trackWidth - knobPx - 12f).coerceAtLeast(1f)
                            if (offset >= limit * 0.9f && done == 0f) {
                                done = 1f
                                onConfirmed()
                            } else {
                                offset = 0f
                            }
                        },
                        onDragCancel = { offset = 0f },
                    ) { _, dragAmount ->
                        val limit = (trackWidth - knobPx - 12f).coerceAtLeast(1f)
                        offset = (offset + dragAmount).coerceIn(0f, limit)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** iOS uses `UIColor.systemRed` here, and only here. Nothing else on a walk screen is red. */
private val AlarmRed = Color(0xFFFF3B30)
private val PanelHeight = 430.dp
/** Pulled up: everything, including the three numbers to call. */
private val ExpandedPanelHeight = 680.dp
private val KnobSize = 52.dp
