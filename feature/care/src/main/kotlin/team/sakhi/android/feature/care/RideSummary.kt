package team.sakhi.android.feature.care

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.emergency.EmergencySafePlace
import team.sakhi.emergency.EmergencySafePlaceKind
import team.sakhi.platform.BiometricInterface
import team.sakhi.platform.BiometricResult
import team.sakhi.staywithme.StayWithMeLocation
import team.sakhi.staywithme.StayWithMePhase
import team.sakhi.staywithme.StayWithMeSession
import kotlinx.datetime.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    /** The dot and the word: deep rose while all is well, orange or red when it is not. */
    statusTint: Color,
    heroValue: String,
    heroUnit: String,
    heroDetail: String,
    heroTint: Color,
    faceIndex: Int,
    faceCaption: String,
    /** A pink ring and a live dot when she is really there, grey when she is not. */
    facePresent: Boolean,
    /** What a screen reader says for the face, since the ring and the dot are only colour. */
    faceDescription: String,
    progress: Float,
    startedAt: Instant,
    endCaption: String,
    endAt: Instant,
    modifier: Modifier = Modifier,
    /** The track's fill, which stays the walk's own pink while she is on time. */
    trackTint: Color = statusTint,
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
            RideCompanion(
                faceIndex = faceIndex,
                caption = faceCaption,
                present = facePresent,
                description = faceDescription,
            )
        }

        JourneyTrack(
            progress = progress,
            tint = trackTint,
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
private fun RideCompanion(faceIndex: Int, caption: String, present: Boolean, description: String) {
    Column(
        modifier = Modifier
            .width(78.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
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
    val progressLabel = stringResource(R.string.ride_progress_label)
    val progressValue = stringResource(
        R.string.ride_progress_value,
        (progress.coerceIn(0f, 1f) * 100).roundToInt(),
        endCaption.lowercase(),
        timeOf(endAt),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = progressLabel
                stateDescription = progressValue
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
 * The chip beside the way out: who is on the other end, live, in the one place the eye lands
 * first. The dot breathes while they are there, the way a call screen shows a call is still
 * connected.
 */
internal data class RideChip(val text: String, val dot: Color, val live: Boolean)

/**
 * What floats over the map: the way out, who is on the other end, and 112.
 *
 * iOS's own sizes: a 40 circle with a half point hairline, a 40 tall white pill with a
 * breathing dot, and a deep-rose capsule 40 tall with sixteen of padding either side.
 */
@Composable
internal fun RideTopBar(
    onClose: () -> Unit,
    chip: RideChip?,
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
        // A cross, not a back arrow: this screen closes rather than steps back. Closing is
        // not stopping, the walk carries on, and Home's ring shows it.
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

        // The chip takes all the room between the two buttons and only as much of it as its
        // words need. A weighted chip beside a weighted spacer got half, and "Updated just
        // now" was cut to "Updat...".
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (chip != null) {
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .background(RideStyle.floating, CircleShape)
                        .border(0.5.dp, RideStyle.hairline, CircleShape)
                        .padding(start = 8.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RideLiveDot(color = chip.dot, pulsing = chip.live)
                    Text(
                        text = chip.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = sakhiLabel(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        RideCallPoliceButton(onClick = onCallPolice)
    }
}

/**
 * 112, in deep rose so it is never mistaken for the pink "I'm home". On her side because she
 * might need it; on her person's because they might need to make the call for her.
 */
@Composable
internal fun RideCallPoliceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A soft tint with deep rose words, for a quiet page. The solid one is for over a map. */
    light: Boolean = false,
) {
    val fill = if (light) RideStyle.rose.copy(alpha = 0.12f) else RideStyle.rose
    val ink = if (light) RideStyle.rose else Color.White
    val policeLabel = stringResource(R.string.care_swm_alarm_call_police_a11y)
    val policeHint = stringResource(R.string.ride_call_police_hint)
    Row(
        modifier = modifier
            .height(40.dp)
            .background(fill, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$policeLabel. $policeHint" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Phone,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = "112",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** A dot that breathes out a soft ring while the other end is live. Still when it is not. */
@Composable
private fun RideLiveDot(color: Color, pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "rideLiveDot")
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_600, easing = LinearOutSlowInEasing)),
        label = "rideLiveDotRing",
    )
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        val s = 1f + 1.4f * ring
                        scaleX = s
                        scaleY = s
                        alpha = 1f - ring
                    }
                    .background(color.copy(alpha = 0.4f), CircleShape),
            )
        }
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The pulled-up panel: sections, and rows grouped on one card
// ─────────────────────────────────────────────────────────────────────────────

/** A section on Sakhi's background: a title with weight, and at most one grey line under it. */
@Composable
internal fun RideSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    captionTint: Color = sakhiSecondaryLabel(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 30.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = title, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, color = sakhiLabel())
        if (caption != null) {
            Text(text = caption, fontSize = 14.sp, lineHeight = 18.sp, color = captionTint)
        }
    }
}

/** Rows grouped on one white card, the way Settings groups them, on Sakhi's background. */
@Composable
internal fun RideCardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(RideStyle.card, RoundedCornerShape(RideStyle.cardRadius))
            .padding(horizontal = 14.dp),
        content = content,
    )
}

/** A hairline from the text, not the icon, the way iOS lists draw them. */
@Composable
internal fun RideRowDivider() {
    SakhiListDivider(startInset = 54.dp)
}

/**
 * The one row shape inside a card: a soft pink disc with a deep rose glyph, a name, one grey
 * line under it, and whatever it offers on the right.
 */
@Composable
internal fun RideRow(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    tint: Color = RideStyle.rose,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 66.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RideIconDisc(icon = icon, size = 40.dp, tint = tint)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = sakhiSecondaryLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun RideIconDisc(icon: ImageVector, size: Dp, tint: Color) {
    Box(
        modifier = Modifier
            .size(size)
            .background(if (tint == RideStyle.rose) RideStyle.soft else tint.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.4f),
        )
    }
}

/** The small soft disc at the end of a row that tells her what tapping it does. */
@Composable
private fun RideTrailingDisc(icon: ImageVector) {
    Box(
        modifier = Modifier.size(34.dp).background(RideStyle.soft, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = RideStyle.rose, modifier = Modifier.size(14.dp))
    }
}

/**
 * Refresh, with a spinner in its place until the answer is in. Without it a tap did nothing
 * visible for up to ten seconds while the phone found a fresh fix.
 */
@Composable
internal fun RideRefreshButton(onClick: () -> Unit, label: String, refreshing: Boolean) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(RideStyle.soft)
            .clickable(enabled = !refreshing, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (refreshing) {
            CircularProgressIndicator(color = RideStyle.rose, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, tint = RideStyle.rose, modifier = Modifier.size(14.dp))
        }
    }
}

/** A place near her, with the way to it one tap away. iOS's `placeRow`. */
@Composable
internal fun RidePlaceRow(place: EmergencySafePlace, onClick: () -> Unit) {
    val detail = "${place.kind.rowLabel} · ${place.formattedDistance}"
    val hint = stringResource(R.string.ride_directions_hint)
    RideRow(
        icon = rideKindIcon(place.kind),
        title = place.name,
        detail = detail,
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${place.name}, $detail. $hint" },
        trailing = { RideTrailingDisc(Icons.Filled.Directions) },
    )
}

/** What it says while it looks, and when there is nothing to show. */
@Composable
internal fun RideEmptyRow(loading: Boolean, loadingText: String, emptyText: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(color = sakhiSecondaryLabel(), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        }
        Text(
            text = if (loading) loadingText else emptyText,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = sakhiSecondaryLabel(),
        )
    }
}

/** Her person's phone is late: the banner above what her phone is saying. */
@Composable
internal fun RideLateBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(RideStyle.alert.copy(alpha = 0.10f), RoundedCornerShape(RideStyle.cardRadius))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = null,
            tint = RideStyle.alert,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.care_swm_late_banner),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = sakhiLabel(),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * On his side only: what her phone is telling him. On hers it would be telling her what she
 * is holding. Where she is going, how fresh her position is with the way to ask again beside
 * it, and her battery. iOS's `herPhone`.
 */
@Composable
internal fun RideHerPhoneCard(
    destinationName: String?,
    locationTitle: String,
    batteryPercent: Int?,
    charging: Boolean,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    RideCardGroup(modifier = modifier) {
        if (destinationName != null) {
            RideRow(
                icon = Icons.Filled.Home,
                title = destinationName,
                detail = stringResource(R.string.care_swm_fact_home_note),
            )
            RideRowDivider()
        }
        RideRow(
            icon = Icons.Filled.NearMe,
            title = locationTitle,
            detail = stringResource(R.string.care_swm_fact_her_location),
            trailing = {
                RideRefreshButton(
                    onClick = onRefresh,
                    label = stringResource(R.string.care_swm_refresh_hers),
                    refreshing = refreshing,
                )
            },
        )
        if (batteryPercent != null) {
            val low = batteryPercent <= 20 && !charging
            RideRowDivider()
            RideRow(
                icon = when {
                    charging -> Icons.Filled.BatteryChargingFull
                    low -> Icons.Filled.BatteryAlert
                    else -> Icons.Filled.BatteryStd
                },
                tint = if (low) RideStyle.alert else RideStyle.rose,
                title = if (charging) stringResource(R.string.ride_battery_charging, batteryPercent) else "$batteryPercent%",
                detail = stringResource(R.string.care_swm_fact_her_battery),
            )
        }
    }
}

/** Her side's way out of the ride: red words on a card, wide, and never the loudest thing. */
@Composable
internal fun RideStopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(RideStyle.cardRadius))
            .background(RideStyle.card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.care_swm_stop),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = RideStyle.alert,
        )
    }
}

/** The glyph for a kind of place, on the map and in the list. All in deep rose. */
internal fun rideKindIcon(kind: EmergencySafePlaceKind): ImageVector = when (kind) {
    EmergencySafePlaceKind.POLICE -> Icons.Filled.LocalPolice
    EmergencySafePlaceKind.HOSPITAL -> Icons.Filled.LocalHospital
    else -> Icons.Filled.LocalPharmacy
}

/**
 * Help near her as a small white disc on the map, glyph in deep rose: the glyph says what each
 * place is, and the route stays the loudest thing on the map.
 */
@Composable
internal fun RidePlaceDisc(kind: EmergencySafePlaceKind) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(Color.White, CircleShape)
            .border(1.dp, RideStyle.rose.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = rideKindIcon(kind),
            contentDescription = null,
            tint = RideStyle.rose,
            modifier = Modifier.size(13.dp),
        )
    }
}

/**
 * A compass that turns the camera back on, over a button for the whole way. The same stack
 * Apple Maps keeps at the map's edge, and iOS's `mapControls`.
 */
@Composable
internal fun RideMapControls(
    camera: WalkCameraMode,
    bearing: Float,
    onCompass: () -> Unit,
    onOverview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compassLabel = stringResource(
        when (camera) {
            WalkCameraMode.HEADING -> R.string.ride_map_following_heading
            WalkCameraMode.FOLLOW -> R.string.ride_map_following_north
            else -> R.string.ride_map_follow_ride
        },
    )
    val compassHint = stringResource(R.string.ride_map_turns_hint)
    val wholeWay = stringResource(R.string.ride_map_whole_way)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(RideStyle.floating)
            .border(0.5.dp, RideStyle.hairline, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clickable(onClick = onCompass)
                .semantics { contentDescription = "$compassLabel. $compassHint" },
            contentAlignment = Alignment.Center,
        ) {
            if (camera == WalkCameraMode.FOLLOW) {
                Icon(
                    imageVector = Icons.Filled.NearMe,
                    contentDescription = null,
                    tint = RideStyle.pink,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                // A needle that always points north on the map, pink while the camera follows
                // her direction.
                Icon(
                    imageVector = if (camera == WalkCameraMode.HEADING) Icons.Filled.Navigation else Icons.Outlined.Navigation,
                    contentDescription = null,
                    tint = if (camera == WalkCameraMode.HEADING) RideStyle.pink else sakhiLabel(),
                    modifier = Modifier.size(18.dp).rotate(-bearing),
                )
            }
        }
        SakhiListDivider(modifier = Modifier.width(26.dp))
        Box(
            modifier = Modifier
                .size(46.dp)
                .clickable(onClick = onOverview)
                .semantics { contentDescription = wholeWay },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Route,
                contentDescription = null,
                tint = if (camera == WalkCameraMode.OVERVIEW) RideStyle.pink else sakhiLabel(),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Face, or her passcode, before anything that says she is safe: "I'm okay", "I'm home" and
 * stopping the walk. Someone else holding her phone must not be able to tell her person she is
 * fine, and the timer on the server keeps running until she does.
 *
 * iOS's `confirmItIsHer`. Android asks for the fingerprint or face the phone has enrolled. A
 * phone with none has nothing to check against, so it cannot block her, which is what iOS
 * does with no passcode set.
 */
@Composable
internal fun rememberConfirmItIsHer(): suspend (String) -> Boolean {
    val biometrics = koinInject<BiometricInterface>()
    return remember(biometrics) {
        val confirm: suspend (String) -> Boolean = { reason ->
            val available = runCatching { biometrics.canAuthenticate() }.getOrDefault(false)
            if (!available) {
                true
            } else {
                runCatching { biometrics.authenticate(reason) }.getOrNull() is BiometricResult.Success
            }
        }
        confirm
    }
}

/**
 * Which way she is travelling, from the last two points of her line, in degrees clockwise from
 * north. The walk has no compass reading to send, so the direction of travel is what the
 * navigation camera turns to. Null until there is a line.
 */
internal fun travelBearing(trail: List<StayWithMeLocation>): Float? {
    if (trail.size < 2) return null
    val a = trail[trail.size - 2]
    val b = trail.last()
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val y = sin(dLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
    return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
}

/** How long her line is, end to end. Null for a ride too short to be worth a number. */
internal fun trailMetres(trail: List<StayWithMeLocation>): Double? {
    if (trail.size < 2) return null
    var total = 0.0
    for (index in 1 until trail.size) {
        total += StayWithMeRoutes.metres(
            trail[index - 1].latitude to trail[index - 1].longitude,
            trail[index].latitude to trail[index].longitude,
        )
    }
    return if (total >= 50.0) total else null
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

/**
 * From where she started to where she is going, how much of it is done: by distance when there
 * is somewhere to go and a line to measure from, by the clock when there is not. iOS's
 * `rideProgress`.
 */
internal fun rideProgress(
    session: StayWithMeSession,
    now: Instant,
    trail: List<StayWithMeLocation> = emptyList(),
): Float {
    val destination = session.destination
    val start = trail.firstOrNull()
    val here = session.lastLocation
    if (destination != null && start != null && here != null) {
        val there = destination.latitude to destination.longitude
        val total = StayWithMeRoutes.metres(start.latitude to start.longitude, there)
        val left = StayWithMeRoutes.metres(here.latitude to here.longitude, there)
        if (total > 50.0) return (1.0 - left / total).toFloat().coerceIn(0f, 1f)
    }
    return session.progress(now).toFloat().coerceIn(0f, 1f)
}

/**
 * The number's colour: plain while she is on her way, orange once she is past her time, red
 * once her person has been told, because a late ride must never look like one going fine.
 */
@Composable
internal fun rideHeroTint(phase: StayWithMePhase): Color = when (phase) {
    StayWithMePhase.LATE, StayWithMePhase.ENDED -> RideStyle.alert
    StayWithMePhase.GRACE -> RideStyle.late
    StayWithMePhase.WALKING -> sakhiLabel()
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
        val extendLabel = stringResource(R.string.ride_add_15_a11y)
        Box(
            modifier = Modifier
                .width(112.dp)
                .height(58.dp)
                .background(RideStyle.card, CircleShape)
                .border(0.5.dp, RideStyle.hairline, CircleShape)
                .clickable(enabled = enabled, onClick = onExtend)
                .semantics { contentDescription = extendLabel },
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
