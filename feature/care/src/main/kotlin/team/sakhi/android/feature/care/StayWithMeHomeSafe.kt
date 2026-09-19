package team.sakhi.android.feature.care

import android.provider.Settings
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSeparator
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * A ride that ended with her home, for the screen that says so. iOS's `StayWithMeHomeSafe`.
 *
 * Built on her phone the moment she says she is home, and on her person's once the app has
 * read back that the ride ended as an arrival rather than a cancellation.
 */
internal data class StayWithMeHomeSafe(
    val id: String,
    /** True on her own phone, false on the phone of the person who stayed with her. */
    val isHer: Boolean,
    /** The other person: her Sakhi's name on her phone, hers on theirs. */
    val personName: String,
    val selfFace: Int,
    val otherFace: Int,
    val startedAt: Instant,
    val endedAt: Instant,
    /** How far her line ran, when there was enough of one to measure. */
    val metres: Double?,
) {
    val minutes: Int get() = max(1, ((endedAt - startedAt).inWholeSeconds / 60.0).roundToInt())
}

/**
 * "You're home safe."
 *
 * Android's `StayWithMeHomeSafeView`. The one screen in Stay With Me that exists to feel good:
 * every other screen in this feature is about something that might go wrong, and this one is
 * the ride ending the way it was meant to. So it is the whole screen, it moves, and it says
 * thank you. Both phones get it, because the relief is the thing they were both waiting for.
 *
 * It uses Sakhi's own colours and no shadows: rings rather than a glow, and confetti in the
 * brand pinks. With animations turned off in the system settings it keeps the words and the
 * card and drops the rings and the confetti, the way iOS does for Reduce Motion.
 */
@Composable
internal fun StayWithMeHomeSafeScreen(
    moment: StayWithMeHomeSafe,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val reduceMotion = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    var landed by remember { mutableStateOf(false) }
    var celebrating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        landed = true
        if (!reduceMotion) {
            celebrating = true
            // A second, softer tap as the confetti falls, so the screen has a rhythm rather
            // than one bang.
            delay(750)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val appear by animateFloatAsState(
        targetValue = if (landed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 120f),
        label = "homeSafeAppear",
    )

    Box(modifier = Modifier.fillMaxSize().background(RideStyle.ground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .graphicsLayer {
                    alpha = appear.coerceIn(0f, 1f)
                    translationY = (1f - appear) * 14.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            HomeSafeArt(moment = moment, landed = appear, celebrating = celebrating && !reduceMotion, reduceMotion = reduceMotion)
            Spacer(Modifier.height(34.dp))

            Text(
                text = stringResource(if (moment.isHer) R.string.ride_home_safe_her else R.string.ride_home_safe_partner),
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Text(
                text = stringResource(if (moment.isHer) R.string.ride_home_safe_glad_her else R.string.ride_home_safe_glad_partner),
                fontSize = 17.sp,
                lineHeight = 22.sp,
                color = sakhiSecondaryLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp).padding(horizontal = 32.dp),
            )
            Text(
                text = if (moment.isHer) {
                    stringResource(R.string.ride_home_safe_told, moment.personName)
                } else {
                    stringResource(R.string.ride_home_safe_thanks)
                },
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = sakhiSecondaryLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp).padding(horizontal = 32.dp),
            )

            HomeSafeRideCard(
                moment = moment,
                modifier = Modifier.padding(top = 30.dp).padding(horizontal = 24.dp),
            )

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(RideStyle.pink)
                    .clickable(role = Role.Button, onClick = onDone),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.ride_home_safe_done),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            // The reassurance that belongs with the relief: the sharing is over.
            Text(
                text = stringResource(
                    if (moment.isHer) R.string.ride_home_safe_stopped_her else R.string.ride_home_safe_stopped_partner,
                ),
                fontSize = 13.sp,
                color = sakhiTertiaryLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
        }

        if (!reduceMotion) {
            HomeSafeConfetti(running = celebrating, modifier = Modifier.fillMaxSize())
        }
    }
}

/** The two of them, with home where they meet. */
@Composable
private fun HomeSafeArt(moment: StayWithMeHomeSafe, landed: Float, celebrating: Boolean, reduceMotion: Boolean) {
    val progress = landed.coerceIn(0f, 1f)
    Box(
        modifier = Modifier.fillMaxWidth().height(108.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!reduceMotion) {
            HomeSafeRing(active = celebrating, delayMs = 0)
            HomeSafeRing(active = celebrating, delayMs = 900)
        }
        Box(
            modifier = Modifier.graphicsLayer {
                val s = 0.8f + 0.2f * landed
                scaleX = s
                scaleY = s
            },
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Two 84 faces overlapping by 18, so their centres are 66 apart. They slide in
            // from either side as the screen lands.
            Box(contentAlignment = Alignment.Center) {
                HomeSafeFace(
                    index = moment.selfFace,
                    modifier = Modifier.offset(x = (-33 - 26 * (1f - progress)).dp),
                )
                HomeSafeFace(
                    index = moment.otherFace,
                    modifier = Modifier.offset(x = (33 + 26 * (1f - progress)).dp),
                )
            }
            // Pink, not a system green: home is Sakhi's own moment (Karan, 2026-09-16).
            Box(
                modifier = Modifier
                    .offset(y = 6.dp)
                    .graphicsLayer {
                        val s = 0.4f + 0.6f * landed
                        scaleX = s
                        scaleY = s
                    }
                    .size(34.dp)
                    .background(RideStyle.pink, CircleShape)
                    .border(3.dp, RideStyle.ground, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/** One face on the celebration, drawn like the Care screen's pair. */
@Composable
private fun HomeSafeFace(index: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(84.dp)
            .background(RideStyle.ground, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(CareAvatars.drawable(index)),
            contentDescription = null,
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(RideStyle.soft, CircleShape)
                .scale(CareAvatars.scale(index) * 0.64f),
        )
    }
}

/**
 * A ring breathing out behind the pair. Rings rather than a glow, because Sakhi's surfaces
 * carry no shadows and no gradients.
 */
@Composable
private fun HomeSafeRing(active: Boolean, delayMs: Int) {
    val transition = rememberInfiniteTransition(label = "homeSafeRing")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_800, delayMillis = delayMs, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "homeSafeRingProgress",
    )
    val shown = if (active) progress else 0f
    Box(
        modifier = Modifier
            .size(150.dp)
            .graphicsLayer {
                val s = if (active) 0.8f + 0.9f * shown else 0.8f
                scaleX = s
                scaleY = s
                alpha = if (active) 0.55f * (1f - shown) else 0f
            }
            .background(RideStyle.soft, CircleShape),
    )
}

/** What the ride was, in the three numbers worth keeping. */
@Composable
private fun HomeSafeRideCard(moment: StayWithMeHomeSafe, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(sakhiSystemBackground(), RoundedCornerShape(20.dp))
            .padding(vertical = 18.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeSafeStat(
            value = "${moment.minutes}",
            unit = stringResource(R.string.care_swm_min),
            caption = stringResource(R.string.ride_home_safe_stat_ride),
            modifier = Modifier.weight(1f),
        )
        val metres = moment.metres
        if (metres != null) {
            HomeSafeDivider()
            HomeSafeStat(
                value = if (metres < 1000) "${(metres / 10).roundToInt() * 10}" else String.format("%.1f", metres / 1000),
                unit = stringResource(if (metres < 1000) R.string.ride_unit_m else R.string.ride_unit_km),
                caption = stringResource(R.string.ride_home_safe_stat_distance),
                modifier = Modifier.weight(1f),
            )
        }
        HomeSafeDivider()
        HomeSafeStat(
            value = timeOf(moment.endedAt),
            unit = "",
            caption = stringResource(if (moment.isHer) R.string.ride_home_safe_stat_reached else R.string.ride_home_safe_stat_home_at),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeSafeStat(value: String, unit: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
                maxLines = 1,
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = sakhiSecondaryLabel(),
                    maxLines = 1,
                )
            }
        }
        Text(text = caption, fontSize = 13.sp, color = sakhiSecondaryLabel(), maxLines = 1)
    }
}

@Composable
private fun HomeSafeDivider() {
    Box(modifier = Modifier.width(0.5.dp).height(34.dp).background(sakhiSeparator()))
}

/**
 * A confetti popper, drawn in one Canvas.
 *
 * Each piece's position is worked out from how long the screen has been up rather than stored
 * and stepped, so there is no per-piece state to keep. It stops itself once the last piece has
 * fallen. Sakhi's own colours, nothing borrowed from the system palette.
 */
@Composable
private fun HomeSafeConfetti(running: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val palette = listOf(
        RideStyle.pink,
        RideStyle.rose,
        RideStyle.soft,
        RideStyle.track,
        RideStyle.pink.copy(alpha = 0.65f),
        RideStyle.rose.copy(alpha = 0.55f),
    )
    // Two bursts out of the pair of faces: ninety pieces is enough to fill a phone screen and
    // few enough to draw at sixty frames a second.
    val pieces = remember {
        List(90) { index ->
            val burst = if (index < 60) 0.0 else 0.7
            ConfettiPiece(
                angle = Random.nextDouble(-0.05 * Math.PI, 1.05 * Math.PI),
                speed = Random.nextDouble(180.0, 520.0),
                x = Random.nextDouble(0.35, 0.65),
                y = Random.nextDouble(0.26, 0.34),
                width = Random.nextDouble(6.0, 11.0),
                height = Random.nextDouble(9.0, 16.0),
                spin = Random.nextDouble(-320.0, 320.0),
                delay = burst + Random.nextDouble(0.0, 0.25),
                life = Random.nextDouble(2.2, 3.4),
                colorIndex = index % 6,
                isRound = index % 4 == 0,
            )
        }
    }
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (elapsed < 5f) {
            androidx.compose.runtime.withFrameNanos { now -> elapsed = (now - start) / 1_000_000_000f }
        }
    }
    if (!running) return
    val unit = density.density
    Canvas(modifier = modifier) {
        val time = elapsed.toDouble()
        for (piece in pieces) {
            val t = time - piece.delay
            if (t <= 0.0 || t >= piece.life) continue
            val x = (piece.x * size.width + cos(piece.angle) * piece.speed * t * unit).toFloat()
            // Up first, then gravity wins.
            val y = (piece.y * size.height - sin(piece.angle) * piece.speed * t * unit + 460.0 * t * t * unit).toFloat()
            if (y > size.height + 40f * unit) continue
            val alpha = max(0.0, 1.0 - t / piece.life).toFloat()
            val w = (piece.width * unit).toFloat()
            val h = (piece.height * unit).toFloat()
            translate(left = x, top = y) {
                rotate(degrees = (piece.spin * t).toFloat(), pivot = Offset.Zero) {
                    if (piece.isRound) {
                        drawOval(
                            color = palette[piece.colorIndex],
                            topLeft = Offset(-w / 2f, -h / 2f),
                            size = Size(w, h),
                            alpha = alpha,
                        )
                    } else {
                        drawRoundRect(
                            color = palette[piece.colorIndex],
                            topLeft = Offset(-w / 2f, -h / 2f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(2f * unit),
                            alpha = alpha,
                        )
                    }
                }
            }
        }
    }
}

private data class ConfettiPiece(
    val angle: Double,
    val speed: Double,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val spin: Double,
    val delay: Double,
    val life: Double,
    val colorIndex: Int,
    val isRound: Boolean,
)
