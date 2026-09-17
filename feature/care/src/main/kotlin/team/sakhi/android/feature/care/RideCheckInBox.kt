package team.sakhi.android.feature.care

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import kotlin.math.sin

/**
 * "Are you okay?", asked every so often while she walks, without taking her screen away.
 *
 * Karan settled the shape of this on 2026-09-16, after four goes at it: it comes from the
 * bottom, it floats above the panel rather than inside it, the panel drops to make room so
 * only one thing is asking at a time, and it nudges itself every three seconds until she
 * answers. This is that box, with iOS's own numbers: a 24 corner, 16 of padding, a 44 disc
 * for the glyph, and two 48 tall buttons.
 *
 * "I'm okay" asks for her fingerprint or face first. A box that anyone holding her phone
 * could dismiss would make the question worth nothing. The glyph is a fingerprint rather
 * than iOS's Face ID mark, because that is what Android actually asks most people for.
 */
@Composable
internal fun RideCheckInBox(
    personName: String,
    checking: Boolean,
    failed: Boolean,
    onOkay: () -> Unit,
    onGetHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Every three seconds (Karan, 2026-09-16): often enough that she knows it wants an
    // answer, quiet enough that it is not shouting.
    var shakes by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            shakes += 1
        }
    }
    val shake by animateFloatAsState(targetValue = shakes.toFloat(), label = "checkInShake")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // One shake per tick: a small sway that rests where it started.
                val phase = (shake - shake.toInt()).toDouble()
                translationX = (sin(phase * Math.PI * 4) * 6).toFloat()
            },
        shape = RoundedCornerShape(24.dp),
        color = RideStyle.floating,
        // The same faint shadow the panel under it casts, so the box reads apart from the
        // map it floats on (Karan, 2026-09-16). One of the two shadows Sakhi allows.
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(44.dp).background(RideStyle.soft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = null,
                        tint = RideStyle.rose,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.care_swm_checkin_title),
                        fontSize = 18.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = sakhiLabel(),
                    )
                    Text(
                        text = if (failed) {
                            stringResource(R.string.care_swm_checkin_failed)
                        } else {
                            stringResource(R.string.care_swm_checkin_body, personName)
                        },
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = if (failed) RideStyle.alert else sakhiSecondaryLabel(),
                        maxLines = 2,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(RideStyle.pink, CircleShape)
                        .clickable(enabled = !checking, onClick = onOkay),
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (checking) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.care_swm_checkin_okay),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .background(RideStyle.soft, CircleShape)
                        .clickable(onClick = onGetHelp)
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = null,
                        tint = RideStyle.rose,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = stringResource(R.string.care_swm_checkin_help),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RideStyle.rose,
                    )
                }
            }
        }
    }
}
