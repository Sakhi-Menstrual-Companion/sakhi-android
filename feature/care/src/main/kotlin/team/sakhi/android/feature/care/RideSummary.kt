package team.sakhi.android.feature.care

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.staywithme.StayWithMePhase
import team.sakhi.staywithme.StayWithMeSession
import kotlinx.datetime.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.math.roundToInt

/**
 * The walk at a glance, in iOS's order of weight: where to, the one number that matters,
 * the other person's face, and how far along she is.
 *
 * Every size, weight and gap here is read off iOS's `StayWithMeLiveScreen.summary`, not
 * chosen again: a 16 bold heading beside a 13 bold status word, a 48 number with its unit
 * at 20 beside a 52 face in a 2.5 ring, and a track eight tall with a thirty wide knob.
 */
@Composable
internal fun RideSummary(
    heading: String,
    /** True when the walk is past her time and her person has been told. */
    alerted: Boolean,
    statusLabel: String,
    statusTint: Color,
    heroValue: String,
    heroUnit: String,
    heroDetail: String,
    heroTint: Color,
    faceIndex: Int,
    faceCaption: String,
    /** A pink ring and a live dot when she is really there, grey when she is not. */
    facePresent: Boolean,
    progress: Float,
    startedAt: Instant,
    endCaption: String,
    endAt: Instant,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 2.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RideHeading(text = heading, alerted = alerted)
            Spacer(Modifier.weight(1f).width(8.dp))
            RideStatusWord(label = statusLabel, tint = statusTint)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RideHero(
                value = heroValue,
                unit = heroUnit,
                detail = heroDetail,
                tint = heroTint,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            RideCompanion(faceIndex = faceIndex, caption = faceCaption, present = facePresent)
        }

        JourneyTrack(
            progress = progress,
            tint = statusTint,
            startedAt = startedAt,
            endCaption = endCaption,
            endAt = endAt,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

@Composable
private fun RideHeading(text: String, alerted: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = if (alerted) Icons.Filled.Error else Icons.Filled.Home,
            contentDescription = null,
            tint = RideStyle.rose,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiLabel(),
            maxLines = 1,
        )
    }
}

/**
 * The walk's status as a dot and a word, with no capsule behind it (Karan, 2026-09-16):
 * the colour of the dot and the word already say it.
 */
@Composable
private fun RideStatusWord(label: String, tint: Color) {
    Row(
        modifier = Modifier.height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).background(tint, CircleShape))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
            maxLines = 1,
        )
    }
}

/** The one big number: how long until she is there. */
@Composable
private fun RideHero(
    value: String,
    unit: String,
    detail: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 48.sp,
                lineHeight = 52.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1,
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    fontSize = 20.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = sakhiSecondaryLabel(),
                    maxLines = 1,
                )
            }
        }
        Text(
            text = detail,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = sakhiSecondaryLabel(),
            maxLines = 1,
        )
    }
}

/**
 * The other person, as a face in a ring, the way Apple Maps shows who it is sharing an
 * arrival with. A pink ring and a live dot when they are really there; grey when they are
 * not.
 */
@Composable
private fun RideCompanion(faceIndex: Int, caption: String, present: Boolean) {
    Column(
        modifier = Modifier.width(78.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .border(2.5.dp, if (present) RideStyle.pink else RideStyle.hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(CareAvatars.drawable(faceIndex)),
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(RideStyle.soft, CircleShape)
                        .scale(CareAvatars.scale(faceIndex)),
                )
            }
            if (present) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-1).dp, y = (-1).dp)
                        .size(14.dp)
                        .background(RideStyle.pink, CircleShape)
                        .border(2.5.dp, RideStyle.ground, CircleShape),
                )
            }
        }
        Text(
            text = caption,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiSecondaryLabel(),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A delivery tracker's track: her marker moves along it towards home, with when she started
 * under one end and when she should be there under the other.
 */
@Composable
private fun JourneyTrack(
    progress: Float,
    tint: Color,
    startedAt: Instant,
    endCaption: String,
    endAt: Instant,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(30.dp)) {
            val width = maxWidth
            val knob = 30.dp
            val end = 30.dp
            val centre = (width * progress.coerceIn(0f, 1f))
                .coerceIn(knob / 2, (width - end - knob / 2 - 2.dp).coerceAtLeast(knob / 2))
            Box(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(RideStyle.track, CircleShape),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(centre.coerceAtLeast(8.dp))
                        .height(8.dp)
                        .background(
                            Brush.horizontalGradient(listOf(tint.copy(alpha = 0.35f), tint)),
                            CircleShape,
                        ),
                )
                // Home, at the end of the line.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = width - end)
                        .size(end)
                        .background(if (progress >= 1f) RideStyle.pink else RideStyle.card, CircleShape)
                        .border(0.5.dp, RideStyle.hairline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = null,
                        tint = if (progress >= 1f) Color.White else RideStyle.rose,
                        modifier = Modifier.size(12.dp),
                    )
                }
                // Her, moving along it.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = centre - knob / 2)
                        .size(knob)
                        .background(tint, CircleShape)
                        .border(3.dp, RideStyle.ground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RideTimeLabel(caption = stringResource(R.string.care_swm_track_started), time = startedAt)
            Spacer(Modifier.weight(1f))
            RideTimeLabel(caption = endCaption, time = endAt)
        }
    }
}

/**
 * The word and its time in one plain colour, never the walk's status colour (Karan,
 * 2026-09-16): lateness is already on the dot, the word above her face and the track.
 */
@Composable
private fun RideTimeLabel(caption: String, time: Instant) {
    Row {
        Text(
            text = "$caption ",
            fontSize = 13.sp,
            color = sakhiLabel(),
            maxLines = 1,
        )
        Text(
            text = timeOf(time),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiLabel(),
            maxLines = 1,
        )
    }
}

/**
 * What floats over the map: the way out, how fresh her position is, and 112.
 *
 * iOS's own sizes: a 40 circle with a half point hairline, a white pill with a six point
 * live dot, and a deep-rose capsule 40 tall with sixteen of padding either side.
 */
@Composable
internal fun RideTopBar(
    onClose: () -> Unit,
    freshness: String?,
    onCallPolice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(RideStyle.floating, CircleShape)
                .border(0.5.dp, RideStyle.hairline, CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.care_swm_close),
                tint = sakhiLabel(),
                modifier = Modifier.size(15.dp),
            )
        }

        if (freshness != null) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .height(36.dp)
                    .background(RideStyle.floating, CircleShape)
                    .border(0.5.dp, RideStyle.hairline, CircleShape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(modifier = Modifier.size(6.dp).background(RideStyle.pink, CircleShape))
                Text(
                    text = freshness,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = sakhiLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .height(40.dp)
                .background(RideStyle.rose, CircleShape)
                .clickable(onClick = onCallPolice)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Phone,
                contentDescription = stringResource(R.string.care_swm_alarm_call_police_a11y),
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = "112",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * The three things worth knowing while she walks, as one white card: where she is going,
 * how fresh her position is with the way to ask again beside it, and her battery.
 */
@Composable
internal fun RideFactsCard(
    destinationName: String?,
    freshness: String?,
    batteryPercent: Int?,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(RideStyle.cardRadius),
        color = RideStyle.card,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Column {
            RideFactRow(
                icon = Icons.Filled.Home,
                title = destinationName ?: stringResource(R.string.care_swm_fact_home),
                subtitle = stringResource(R.string.care_swm_fact_home_note),
            )
            if (freshness != null) {
                RideFactDivider()
                RideFactRow(
                    icon = Icons.Filled.NearMe,
                    title = freshness,
                    subtitle = stringResource(R.string.care_swm_fact_her_location),
                    trailing = {
                        RefreshButton(
                            onClick = onRefresh,
                            label = stringResource(R.string.care_swm_refresh_hers),
                            refreshing = refreshing,
                        )
                    },
                )
            }
            if (batteryPercent != null) {
                RideFactDivider()
                RideFactRow(
                    icon = Icons.Filled.BatteryStd,
                    title = "$batteryPercent%",
                    subtitle = stringResource(R.string.care_swm_fact_her_battery),
                )
            }
        }
    }
}

@Composable
private fun RideFactRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(RideStyle.soft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RideStyle.rose,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
                maxLines = 1,
            )
            Text(
                text = subtitle,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = sakhiSecondaryLabel(),
                maxLines = 1,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun RideFactDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 74.dp)
            .height(0.5.dp)
            .background(RideStyle.hairline),
    )
}

/** iOS's `RideStatus`: what the dot and the word say, and in what colour. */
internal enum class RideWatcherStatus(val labelRes: Int) {
    /** Inside her time, and the road agrees. */
    ON_TIME(R.string.care_swm_status_on_time),
    /** Inside her time, but the drive left is longer than the time left. */
    TIGHT(R.string.care_swm_status_tight),
    /** Past her time, inside the grace window. */
    GRACE(R.string.care_swm_status_past_her_time),
    /** Her person has been told. */
    ALERTED(R.string.care_swm_status_late),
    ;

    @Composable
    fun tint(): Color = when (this) {
        // iOS draws the word in deep rose when everything is fine, and the dot with it.
        ON_TIME -> RideStyle.rose
        TIGHT, GRACE -> RideStyle.late
        ALERTED -> RideStyle.alert
    }

    /** The track's fill, which stays the walk's own pink while she is on time. */
    @Composable
    fun trackTint(): Color = when (this) {
        ON_TIME -> RideStyle.pink
        TIGHT, GRACE -> RideStyle.late
        ALERTED -> RideStyle.alert
    }
}

/** iOS's `rideStatus`, to the letter. */
internal fun rideWatcherStatus(
    session: StayWithMeSession,
    now: Instant,
    route: WalkRoute?,
): RideWatcherStatus = when (session.phase(now)) {
    StayWithMePhase.LATE -> RideWatcherStatus.ALERTED
    StayWithMePhase.GRACE -> RideWatcherStatus.GRACE
    StayWithMePhase.ENDED -> RideWatcherStatus.ALERTED
    StayWithMePhase.WALKING -> {
        val eta = route?.durationSeconds
        if (eta != null && now.plus(eta, DateTimeUnit.SECOND) > session.expectedArrival) {
            RideWatcherStatus.TIGHT
        } else {
            RideWatcherStatus.ON_TIME
        }
    }
}

/** The big number, its unit and the line under it. iOS's `heroParts`, watching side. */
internal data class RideHeroParts(val value: String, val unit: String, val detail: String)

@Composable
internal fun rideWatcherHero(
    session: StayWithMeSession,
    now: Instant,
    route: WalkRoute?,
): RideHeroParts = when (session.phase(now)) {
    StayWithMePhase.LATE, StayWithMePhase.ENDED -> RideHeroParts(
        value = stringResource(R.string.care_swm_status_late),
        unit = "",
        detail = stringResource(R.string.care_swm_was_due_at, timeOf(session.expectedArrival)),
    )
    StayWithMePhase.GRACE -> RideHeroParts(
        value = minutesUp(session.secondsRemaining(now)).toString(),
        unit = stringResource(R.string.care_swm_min),
        detail = stringResource(R.string.care_swm_until_told),
    )
    StayWithMePhase.WALKING -> {
        val seconds = route?.durationSeconds
        if (seconds != null) {
            val minutes = maxOf(1, ((seconds / 60.0)).roundToInt())
            RideHeroParts(
                value = if (minutes < 60) "$minutes" else "${minutes / 60}h ${minutes % 60}",
                unit = stringResource(R.string.care_swm_min),
                detail = stringResource(R.string.care_swm_away_by_car, rideDistanceText(route.distanceMeters)),
            )
        } else {
            RideHeroParts(
                value = minutesUp(session.secondsRemaining(now)).toString(),
                unit = stringResource(R.string.care_swm_min_left),
                detail = stringResource(R.string.care_swm_reach_by_detail, timeOf(session.expectedArrival)),
            )
        }
    }
}

/**
 * "2.1 km" or "600 m", the way iOS's `distanceParts` writes it: kilometres to one decimal
 * above a kilometre, whole metres below it.
 */
internal fun rideDistanceText(metres: Double): String =
    if (metres >= 1000) {
        val km = (metres / 100).roundToInt() / 10.0
        "$km km"
    } else {
        "${(metres / 10).roundToInt() * 10} m"
    }

/** How far along the line she is, by distance when there is a destination, else by clock. */
internal fun rideProgress(session: StayWithMeSession, now: Instant): Float {
    val total = (session.expectedArrival - session.startedAt).inWholeSeconds.toFloat()
    if (total <= 0f) return 0f
    val gone = (now - session.startedAt).inWholeSeconds.toFloat()
    return (gone / total).coerceIn(0f, 1f)
}

/** The same status, said from her side: iOS's `statusLabel` for the walking role. */
internal val RideWatcherStatus.ownerLabelRes: Int
    get() = when (this) {
        RideWatcherStatus.ON_TIME -> R.string.care_swm_status_on_time
        RideWatcherStatus.TIGHT -> R.string.care_swm_status_add_time
        RideWatcherStatus.GRACE -> R.string.care_swm_status_past_your_time
        RideWatcherStatus.ALERTED -> R.string.care_swm_status_alert_sent
    }

internal fun rideOwnerStatus(
    session: StayWithMeSession,
    now: Instant,
    route: WalkRoute?,
): RideWatcherStatus = rideWatcherStatus(session, now, route)

/** The big number on her own screen. iOS's `heroParts`, walking side. */
@Composable
internal fun rideOwnerHero(
    session: StayWithMeSession,
    now: Instant,
    route: WalkRoute?,
    personName: String,
): RideHeroParts = when (session.phase(now)) {
    StayWithMePhase.LATE, StayWithMePhase.ENDED -> RideHeroParts(
        value = stringResource(R.string.care_swm_status_late),
        unit = "",
        detail = stringResource(R.string.care_swm_person_has_been_told, personName),
    )
    StayWithMePhase.GRACE -> RideHeroParts(
        value = minutesUp(session.secondsRemaining(now)).toString(),
        unit = stringResource(R.string.care_swm_min),
        detail = stringResource(R.string.care_swm_until_person_told, personName),
    )
    StayWithMePhase.WALKING -> {
        val seconds = route?.durationSeconds
        if (seconds != null) {
            val minutes = maxOf(1, ((seconds / 60.0)).roundToInt())
            RideHeroParts(
                value = if (minutes < 60) "$minutes" else "${minutes / 60}h ${minutes % 60}",
                unit = stringResource(R.string.care_swm_min),
                detail = stringResource(R.string.care_swm_away_by_car, rideDistanceText(route.distanceMeters)),
            )
        } else {
            RideHeroParts(
                value = minutesUp(session.secondsRemaining(now)).toString(),
                unit = stringResource(R.string.care_swm_min_left),
                detail = stringResource(R.string.care_swm_reach_by_detail, timeOf(session.expectedArrival)),
            )
        }
    }
}

/**
 * Her two buttons: fifteen more minutes, and "I'm home".
 *
 * iOS's own shape: a 112 wide white capsule with a hairline beside a pink one that takes
 * the rest, both 58 tall, ten apart. The label stays while it works, because a spinner on
 * its own read as a broken button.
 */
@Composable
internal fun RideFooterButtons(
    onExtend: () -> Unit,
    onArrive: () -> Unit,
    enabled: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(112.dp)
                .height(58.dp)
                .background(RideStyle.card, CircleShape)
                .border(0.5.dp, RideStyle.hairline, CircleShape)
                .clickable(enabled = enabled, onClick = onExtend),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.care_swm_extend_15),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
                .background(RideStyle.pink, CircleShape)
                .clickable(enabled = enabled, onClick = onArrive),
            horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = stringResource(R.string.care_swm_im_home),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}
