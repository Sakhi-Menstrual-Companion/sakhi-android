package team.sakhi.android.feature.care

import android.content.Context
import android.location.Geocoder
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.platform.StayWithMeLocationService
import team.sakhi.android.ui.CloseButton
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.android.ui.SakhiTextField
import team.sakhi.care.CareRuntimeState
import team.sakhi.staywithme.StayWithMeDestination
import team.sakhi.staywithme.StayWithMeHomePreference
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

    var editingHome by remember { mutableStateOf(false) }
    val homePreference = koinInject<StayWithMeHomePreference>()
    var home by remember { mutableStateOf(homePreference.get()) }

    Box(modifier = Modifier.fillMaxSize().background(RideStyle.ground)) {
        WalkMap(
            location = here,
            accent = MaterialTheme.colorScheme.primary,
            initial = "",
            modifier = Modifier.fillMaxSize(),
            destination = state.destination,
            bottomPadding = 320.dp,
        )
        RideTopBar(
            onClose = onClose,
            chip = null,
            onCallPolice = { dial(context, "112") },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        // The panel sits at whatever height its content needs, the same shape her own start
        // screen has, never a draggable sheet with a long detent to fall into (Karan, 2026-09-19).
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
            color = RideStyle.ground,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.size(width = 36.dp, height = 5.dp).background(RideStyle.hairline, CircleShape))
                }
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
                    AskDestinationCapsules(
                        state = state,
                        here = here,
                        home = home,
                        onEditHome = { editingHome = true },
                    )
                    Spacer(Modifier.height(14.dp))
                    StayWithMeSettingsSection(state, includeCheckIn = false)
                }
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
            }
        }

        if (editingHome) {
            AskHomeEditorSheet(
                onSave = { destination ->
                    homePreference.set(destination)
                    home = destination
                    state.destination = destination
                    state.note = destination.name
                    editingHome = false
                },
                onClose = { editingHome = false },
            )
        }
    }
}

/**
 * The two ways her person can pick where she is going, in place of typing: her saved home,
 * edited from here, or right where they are standing right now. iOS keeps the free search
 * for her own screen; this one is theirs, and these are the two places they actually mean.
 */
@Composable
private fun AskDestinationCapsules(
    state: StayWithMeStartState,
    here: StayWithMeLocation?,
    home: StayWithMeDestination?,
    onEditHome: () -> Unit,
) {
    val context = LocalContext.current
    var youLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(here?.latitude, here?.longitude) {
        val fix = here ?: return@LaunchedEffect
        youLabel = reverseGeocode(context, fix.latitude, fix.longitude)
        // One of the two is always the plan, never a blank destination she has to notice
        // and fix herself: her saved home if there is one, otherwise right where they are.
        if (state.destination == null) {
            state.destination = home ?: StayWithMeDestination(
                youLabel ?: context.getString(R.string.ride_ask_your_location_fallback),
                fix.latitude,
                fix.longitude,
            )
            state.note = state.destination?.name.orEmpty()
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AskPlaceCapsule(
            icon = Icons.Filled.Home,
            label = stringResource(R.string.ride_ask_her_home_unset),
            place = home?.name,
            selected = home != null && state.destination == home,
            onClick = {
                home?.let {
                    state.destination = it
                    state.note = it.name
                } ?: onEditHome()
            },
            onEdit = onEditHome,
            editDescription = stringResource(R.string.ride_ask_edit_home),
            modifier = Modifier.weight(1f),
        )
        AskPlaceCapsule(
            icon = Icons.Filled.MyLocation,
            label = here?.let { stringResource(R.string.ride_ask_your_location_loading) }
                ?: stringResource(R.string.ride_ask_your_location_fallback),
            place = youLabel,
            selected = here != null && state.destination?.let {
                it.latitude == here?.latitude && it.longitude == here?.longitude
            } == true,
            onClick = {
                here?.let { fix ->
                    val place = StayWithMeDestination(
                        youLabel ?: context.getString(R.string.ride_ask_your_location_fallback),
                        fix.latitude,
                        fix.longitude,
                    )
                    state.destination = place
                    state.note = place.name
                }
            },
            onEdit = null,
            editDescription = null,
            modifier = Modifier.weight(1f),
        )
    }
}

/** One of the two capsules: an icon, a title, what it resolves to, and an edit pencil if it has one. */
@Composable
private fun AskPlaceCapsule(
    icon: ImageVector,
    label: String,
    place: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)?,
    editDescription: String?,
    modifier: Modifier = Modifier,
) {
    // A solid pink pill read as a second and third "Ask to stay with her" (Karan, 2026-09-19).
    // This picks between two places, it does not act, so the card stays the same white every
    // other row on this screen is; only the icon's disc and a hairline ring say which is picked.
    val discColor = if (selected) MaterialTheme.colorScheme.primary else RideStyle.soft
    val iconTint = if (selected) Color.White else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .height(52.dp)
            .background(RideStyle.card, RoundedCornerShape(16.dp))
            .then(
                if (selected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(26.dp).background(discColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(13.dp))
        }
        Text(
            text = place ?: label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiLabel(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onEdit != null && editDescription != null) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = editDescription,
                tint = sakhiSecondaryLabel(),
                modifier = Modifier.size(14.dp).clickable(onClick = onEdit),
            )
        }
    }
}

/**
 * Where her person says her home is, full screen over everything else here (Karan,
 * 2026-09-19): a search bar, the results, and a Save button that commits the one she picked.
 * Saved to [StayWithMeHomePreference], so the next ask starts from the same place.
 */
@Composable
private fun AskHomeEditorSheet(
    onSave: (StayWithMeDestination) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val search = remember { StayWithMeDestinationSearch(context.applicationContext) }
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<StayWithMeDestinationSearch.Suggestion>>(emptyList()) }
    var picked by remember { mutableStateOf<StayWithMeDestination?>(null) }
    LaunchedEffect(query) {
        delay(300)
        suggestions = search.search(query)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = RideStyle.ground,
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(width = 36.dp, height = 5.dp).background(RideStyle.hairline, CircleShape))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CloseButton(onClick = onClose)
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.ride_ask_her_home),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = sakhiLabel(),
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(40.dp))
            }
            Spacer(Modifier.height(16.dp))
            SakhiTextField(
                value = query,
                onValueChange = { query = it; picked = null },
                placeholder = stringResource(R.string.ride_ask_search_home),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                suggestions.forEachIndexed { index, suggestion ->
                    if (index > 0) SakhiListDivider(startInset = 20.dp)
                    val isPicked = picked == suggestion.destination
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { picked = suggestion.destination }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = suggestion.title, fontSize = 16.sp, color = sakhiLabel(), maxLines = 1)
                            suggestion.subtitle?.let {
                                Text(text = it, fontSize = 13.sp, color = sakhiSecondaryLabel(), maxLines = 1)
                            }
                        }
                        if (isPicked) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            SakhiFooter(
                primaryLabel = stringResource(R.string.ride_ask_save_home),
                onPrimaryClick = { picked?.let(onSave) },
                primaryEnabled = picked != null,
                showSecondarySlot = false,
            )
        }
    }
}

/** Her rough address, so "Your Location" reads like a place and not a pair of numbers. */
private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            @Suppress("DEPRECATION")
            val results = Geocoder(context).getFromLocation(lat, lng, 1)
            results?.firstOrNull()?.let { addr ->
                addr.thoroughfare ?: addr.subLocality ?: addr.locality ?: addr.featureName
            }
        }.getOrNull()
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
