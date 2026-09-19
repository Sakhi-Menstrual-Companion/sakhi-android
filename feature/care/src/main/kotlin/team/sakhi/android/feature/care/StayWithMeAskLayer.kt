package team.sakhi.android.feature.care

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.platform.StayWithMeLocationService
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.care.CareRuntimeState
import team.sakhi.staywithme.StayWithMeLocation

/**
 * Her person's Stay With Me while she is not on a ride: the map, and under it the panel where
 * they say where she is going and by when, then ask her to stay with them. The same shape her
 * own start screen has (Karan, 2026-09-19).
 *
 * What they choose travels with the ask, so the screen she opens is already filled in. Asking
 * sends a question to her phone and nothing else: no location is shared until she says yes,
 * and no ride starts from here. The moment she starts, this hands over to the live watch.
 */
@Composable
fun StayWithMeAskLayer(
    onClose: () -> Unit,
    /** Her ride has begun: swap this for the full-screen watch. */
    onOpenLiveWalk: () -> Unit,
    careViewModel: CareViewModel = koinViewModel(),
    viewModel: StayWithMeViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val care by careViewModel.uiState.collectAsStateWithLifecycle()
    val walk by viewModel.uiState.collectAsStateWithLifecycle()
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
    LaunchedEffect(walk.watching != null) {
        if (walk.watching != null) onOpenLiveWalk()
    }

    val herId = partner?.partnership?.userId.orEmpty()
    val herFace = partnerCard?.takeIf { it.userId.equals(herId, ignoreCase = true) }?.avatarIndex
        ?: CareAvatars.indexFor(herId)

    // The button turns to waiting the moment it is tapped, not when the server has answered,
    // which took a few seconds and left the tap looking as if it had done nothing.
    var asking by remember { mutableStateOf(false) }
    LaunchedEffect(askResult) { if (askResult != null) asking = false }
    val failed = askResult != null
    val asked = (askedAt != null || asking) && !failed

    val state = rememberStayWithMeStartState()
    // Where this phone is, so the map opens somewhere real. Their own fix, never a request.
    var here by remember { mutableStateOf<StayWithMeLocation?>(null) }
    LaunchedEffect(Unit) {
        here = StayWithMeLocationService.lastKnownLatLng(context)?.let { (lat, lng) ->
            StayWithMeLocation(lat, lng, null, null, null, kotlinx.datetime.Clock.System.now())
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(RideStyle.ground)) {
        val restingHeight = maxHeight * 0.5f
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        WalkMap(
            location = here,
            accent = MaterialTheme.colorScheme.primary,
            initial = "",
            modifier = Modifier.fillMaxSize(),
            destination = state.destination,
            bottomPadding = restingHeight + bottomInset,
        )
        RideTopBar(
            onClose = onClose,
            chip = null,
            onCallPolice = { dial(context, "112") },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        RideBottomPanel(
            restingHeight = restingHeight,
            fullHeight = maxHeight * 0.9f,
            modifier = Modifier.align(Alignment.BottomCenter),
            footer = {
                SakhiFooter(
                    primaryLabel = stringResource(R.string.care_ask_to_stay_with_her),
                    onPrimaryClick = {
                        if (!asked && partnershipId != null) {
                            asking = true
                            viewModel.clearAskResult()
                            viewModel.askToStay(partnershipId, state.destination, state.minutes)
                        }
                    },
                    primaryEnabled = partnershipId != null,
                    showSecondarySlot = false,
                    primarySlot = if (asked) {
                        { AskWaitingButton(label = stringResource(R.string.ride_partner_idle_waiting)) }
                    } else {
                        null
                    },
                )
            },
        ) {
            item(key = "form") {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Image(
                            painter = painterResource(CareAvatars.drawable(herFace)),
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(RideStyle.soft, CircleShape),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.ride_partner_idle_title),
                                fontSize = 19.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = sakhiLabel(),
                            )
                            Text(
                                text = when {
                                    failed -> stringResource(R.string.care_ask_failed)
                                    asked -> stringResource(R.string.ride_partner_idle_asked)
                                    else -> stringResource(R.string.ride_partner_idle_message)
                                },
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                                color = sakhiSecondaryLabel(),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    StayWithMeDestinationSection(state)
                    Spacer(Modifier.height(14.dp))
                    StayWithMeSettingsSection(state, includeCheckIn = false)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/** The ask button once the ask is out: the same solid pink capsule, with a spinner beside the label. */
@Composable
private fun AskWaitingButton(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = SakhiSpacing.space1)
            .clip(RoundedCornerShape(SakhiRadius.full))
            .background(MaterialTheme.colorScheme.primary),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
        Spacer(Modifier.size(10.dp))
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
        )
    }
}
