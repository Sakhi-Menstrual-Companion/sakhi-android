package team.sakhi.android.feature.care

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
 * The leading control in the bottom bar: her Care Mode person.
 *
 * This slot used to open Emergency Assistance and drew a live map of strangers nearby. It
 * draws a person now, because the thing behind it is a person: the same person who can see
 * what she shares is the one who stays with her on the way home.
 *
 * It is the only place on Home that can turn red, and red here means one thing only: a walk
 * is past its time. Never that somebody else needs something from her.
 */
@Composable
fun CareModeHomeButton(
    onOpen: () -> Unit,
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

    val walk = mine ?: watching
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
    // into the walk she is. Green while she is on time, because they are together and all is
    // well; red, and full, once she is late.
    val ringColor = if (late) AppleSystemColors.red else AppleSystemColors.green

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
            .alpha(if (late) blink else 1f)
            .drawWithContent {
                drawContent()
                if (walk != null) {
                    val stroke = 3.dp.toPx()
                    val arcTopLeft = Offset(stroke / 2, stroke / 2)
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = ringColor.copy(alpha = 0.25f),
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
                        sweepAngle = if (late) 360f else 360f * progress,
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
        when {
            pair != null -> Row(
                horizontalArrangement = Arrangement.spacedBy((-8).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
