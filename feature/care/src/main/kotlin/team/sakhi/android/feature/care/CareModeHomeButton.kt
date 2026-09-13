package team.sakhi.android.feature.care

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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

    val walk = mine ?: watching
    val phase = walk?.phase(stayWithMeStore.now())
    val late = phase == StayWithMePhase.LATE || phase == StayWithMePhase.GRACE

    val pulse = rememberInfiniteTransition(label = "careButton")
    val blink by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "careButtonBlink",
    )

    val ring = when {
        late -> AppleSystemColors.red
        walk != null -> MaterialTheme.colorScheme.primary
        else -> sakhiSeparator()
    }

    Box(
        modifier = modifier
            .size(46.dp)
            .background(sakhiButtonFill(), CircleShape)
            .alpha(if (late) blink else 1f)
            .border(BorderStroke(if (walk != null) 3.dp else 1.dp, ring), CircleShape)
            .clickable(onClick = onOpen)
            .semantics { contentDescription = name ?: "Care Mode" },
        contentAlignment = Alignment.Center,
    ) {
        if (name != null) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
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
