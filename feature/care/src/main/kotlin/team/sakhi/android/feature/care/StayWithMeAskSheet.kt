package team.sakhi.android.feature.care

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiLightPink
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.ui.CloseButton
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.android.ui.SheetSurface
import team.sakhi.care.CareRuntimeState

/**
 * Her person's Stay With Me while she is not on a ride: a sheet over Home, where they can ask
 * her to stay with them, with 112 one tap away in the corner.
 *
 * It is a sheet, not a full screen (Karan, 2026-09-19): there is no map to show until she
 * starts, so it would have been a mostly empty page. The moment she does start, it hands over
 * to the live watch by itself. Asking sends a question to her phone and nothing else; no
 * location is shared until she says yes, and no ride starts from here.
 */
@Composable
fun StayWithMeAskSheet(
    onClose: () -> Unit,
    /** Her ride has begun: swap this sheet for the full-screen watch. */
    onOpenLiveWalk: () -> Unit,
    careViewModel: CareViewModel = koinViewModel(),
    viewModel: StayWithMeViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val care by careViewModel.uiState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val partnerCard by viewModel.partnerCard.collectAsStateWithLifecycle()
    val askedAt by viewModel.askedAt.collectAsStateWithLifecycle()
    val askResult by viewModel.askResult.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.onVisible()
        onDispose { viewModel.onHidden() }
    }

    val partner = care.careState as? CareRuntimeState.PartnerConnected
    val partnershipId = partner?.partnership?.id
    LaunchedEffect(partnershipId) {
        if (partnershipId != null) viewModel.loadPartnerCard(partnershipId)
    }
    LaunchedEffect(state.watching != null) {
        if (state.watching != null) onOpenLiveWalk()
    }

    val herId = partner?.partnership?.userId.orEmpty()
    val selfId = partner?.partnership?.partnerId.orEmpty()
    val herFace = partnerCard?.takeIf { it.userId.equals(herId, ignoreCase = true) }?.avatarIndex
        ?: CareAvatars.indexFor(herId)
    // The button turns to waiting the moment it is tapped, not when the server has answered,
    // which took a few seconds and left the tap looking as if it had done nothing.
    var asking by remember { mutableStateOf(false) }
    LaunchedEffect(askResult) { if (askResult != null) asking = false }
    val failed = askResult != null
    val asked = (askedAt != null || asking) && !failed

    SheetSurface(showDragHandle = true) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The way out is the X, on the left, as on every screen.
            // 112 opposite it, the one call kept on this screen: her person may need to make it
            // for her.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CloseButton(onClick = onClose)
                Spacer(Modifier.weight(1f))
                RideCallPoliceButton(onClick = { dial(context, "112") }, light = true)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.size(20.dp))
                CareConnectionArt(
                    selfAvatarIndex = CareAvatars.selfIndex(context, selfId),
                    otherAvatarIndex = herFace,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(R.string.ride_partner_idle_title),
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = sakhiLabel(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    text = when {
                        failed -> stringResource(R.string.care_ask_failed)
                        asked -> stringResource(R.string.ride_partner_idle_asked)
                        else -> stringResource(R.string.ride_partner_idle_message)
                    },
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = sakhiSecondaryLabel(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
                )

                Row(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .fillMaxWidth()
                        .background(sakhiSystemBackground(), RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(sakhiLightPink(), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.ride_partner_idle_point_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = sakhiLabel(),
                        )
                        Text(
                            text = stringResource(R.string.ride_partner_idle_point_detail),
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            color = sakhiSecondaryLabel(),
                        )
                    }
                }
                Spacer(Modifier.size(24.dp))
            }

            SakhiFooter(
                primaryLabel = stringResource(
                    if (asked) R.string.ride_partner_idle_waiting else R.string.care_ask_to_stay_with_her,
                ),
                onPrimaryClick = {
                    if (!asked && partnershipId != null) {
                        asking = true
                        viewModel.clearAskResult()
                        viewModel.askToStay(partnershipId)
                    }
                },
                primaryEnabled = partnershipId != null,
                // Waiting is the button itself, dimmed and breathing with a spinner, not a grey
                // disabled one: it is something happening, not something switched off.
                primarySlot = if (asked) {
                    { AskWaitingButton(label = stringResource(R.string.ride_partner_idle_waiting)) }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * The ask button once the ask is out: the pink capsule, softened, with a light sweeping across
 * it and a spinner beside the words. Something is happening, so it is not greyed out.
 */
@Composable
private fun AskWaitingButton(label: String) {
    val transition = rememberInfiniteTransition(label = "askWaiting")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "askWaitingSweep",
    )
    val base = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = SakhiSpacing.space1)
            .clip(RoundedCornerShape(SakhiRadius.full))
            .drawBehind {
                drawRect(base)
                val w = size.width
                val centre = -w * 0.3f + w * 1.6f * sweep
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
                        startX = centre - w * 0.28f,
                        endX = centre + w * 0.28f,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}
