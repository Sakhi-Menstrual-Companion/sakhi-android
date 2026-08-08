package team.sakhi.android.feature.ai

import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import team.sakhi.android.ui.CloseButton
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSystemGray5
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.ui.KeyboardSafeScaffold
import team.sakhi.android.ui.SakhiNavBar
import team.sakhi.android.ui.SheetSurface
import team.sakhi.design.SakhiColors
import team.sakhi.design.SakhiUIColors
import team.sakhi.models.ConversationMessage
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import team.sakhi.android.designsystem.sakhiSecondaryLabel

/**
 * Sakhi AI chat — ports iOS `SakhiAIChatView.swift` (370 lines) plus
 * `SakhiAIMessageBubble.swift`, `SakhiAIInputBar.swift`, `SakhiAISuggestedChips.swift`,
 * and `SakhiAITypingIndicator.swift`: header with online/last-seen presence,
 * suggested chips on the empty state, WhatsApp-style tail bubbles with
 * delivered/read tick receipts, 3-dot typing indicator, and a rotating-placeholder
 * input bar with an animated send button.
 *
 * Still not ported, each due to separate missing platform infra rather than UI
 * oversight:
 * - The full long-press context menu (star/copy) -- Compose has no direct SwiftUI
 *   `.contextMenu` equivalent; Android currently uses long-press-to-star only.
 * - The header app logo image (no Android drawable asset ported yet; a plain
 *   icon badge stands in for it).
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = koinViewModel(),
    onClose: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages = uiState.displayMessages
    val hapticManager = koinInject<AndroidHapticManager>()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val starredStore = rememberStarredMessagesStore()
    var destination by rememberSaveable { mutableStateOf(ChatDestination.Thread) }
    var expandedPlaces by remember { mutableStateOf<List<team.sakhi.models.SafePlace>?>(null) }
    val activeSessionKey = uiState.session?.let {
        "${it.userId}|${it.targetUserId}|${it.isViewingOwnData}"
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(activeSessionKey) {
        destination = ChatDestination.Thread
        expandedPlaces = null
    }

    // Same gap as elsewhere in the app: without this, system back from Info/Search/
    // Media/Starred (or the Nearby Places detail overlay) skipped straight past this
    // sub-navigation and closed the whole Chat sheet. Mirrors each screen's own
    // on-screen `onBack` exactly. The two handlers are mutually exclusive by their
    // `enabled` conditions, so there's no ordering ambiguity between them.
    BackHandler(enabled = expandedPlaces != null) { expandedPlaces = null }
    BackHandler(enabled = expandedPlaces == null && destination != ChatDestination.Thread) {
        destination = when (destination) {
            ChatDestination.Search, ChatDestination.Media, ChatDestination.Starred -> ChatDestination.Info
            ChatDestination.Info, ChatDestination.Thread -> ChatDestination.Thread
        }
    }

    DisposableEffect(lifecycleOwner, activeSessionKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.retryPendingMessages()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(messages.size, uiState.isSending, uiState.reportSession != null) {
        val targetIndex = when {
            uiState.reportSession != null -> messages.size + 1
            uiState.isSending -> messages.size + 1
            messages.isNotEmpty() -> messages.size
            else -> null
        } ?: return@LaunchedEffect
        listState.animateScrollToItem(targetIndex)
    }

    LaunchedEffect(uiState.sharePdfUri) {
        val shareUri = uiState.sharePdfUri ?: return@LaunchedEffect
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(
                shareIntent,
                context.getString(R.string.chat_share_health_report),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        viewModel.consumeSharePdf()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onLocationPermissionResult,
    )
    LaunchedEffect(uiState.needsLocationPermission) {
        if (uiState.needsLocationPermission) {
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    // iOS presents this as `.sheet(...).presentationDragIndicator(.hidden)` --
    // no drag handle, relying on the in-header close button instead.
    Box(modifier = Modifier.fillMaxSize()) {
    SheetSurface {
        when (destination) {
            ChatDestination.Thread -> {
                KeyboardSafeScaffold(
                    topBar = {
                        ChatHeader(
                            uiState = uiState,
                            onInfoClick = { destination = ChatDestination.Info },
                            onClose = onClose,
                        )
                    },
                    body = {
                        if (messages.isEmpty() && !uiState.isSending) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Spacer(modifier = Modifier.weight(1f))
                                SuggestedChipsRow(
                                    chips = uiState.suggestionChips,
                                    onChipClick = viewModel::sendSuggestedChip,
                                )
                                Spacer(modifier = Modifier.weight(2f))
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = SakhiSpacing.space4),
                            ) {
                                if (messages.isNotEmpty()) {
                                    item(key = "today") { TodaySeparator() }
                                }

                                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                                    val previous = messages.getOrNull(index - 1)
                                    val next = messages.getOrNull(index + 1)
                                    MessageBubble(
                                        message = message,
                                        isStarred = starredStore.isStarred(message.id),
                                        onToggleStar = {
                                            hapticManager.impact(HapticImpact.MEDIUM)
                                            starredStore.toggle(message.id)
                                        },
                                        isLastInGroup = next?.isUser != message.isUser,
                                        groupTopPadding = if (previous?.isUser == message.isUser) SakhiSpacing.space1 else SakhiSpacing.space3,
                                        showReadTick = index < messages.lastIndex || uiState.isSending,
                                        onExpandPlaces = {
                                            hapticManager.impact(HapticImpact.LIGHT)
                                            expandedPlaces = message.places
                                        },
                                    )
                                }

                                if (uiState.isSending) {
                                    item(key = "typing") { TypingIndicator() }
                                }

                                uiState.reportSession?.let { reportSession ->
                                    item(key = "report-card") {
                                        ChatReportCard(
                                            session = reportSession,
                                            onSelectRange = viewModel::selectReportRange,
                                            onGenerate = viewModel::generateReport,
                                            onDismiss = viewModel::dismissReportCard,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    footer = {
                        HorizontalDivider()
                        ChatInputBar(
                            text = uiState.inputText,
                            isPartnerMode = uiState.session?.isViewingOwnData == false,
                            isSending = uiState.isSending,
                            isLocked = uiState.reportSession != null,
                            onTextChanged = viewModel::onInputChanged,
                            onSend = viewModel::sendCurrentMessage,
                        )
                    },
                )
            }

            ChatDestination.Info -> ChatInfoScreen(
                messages = uiState.messages,
                starredStore = starredStore,
                showClearConfirm = uiState.showClearConfirm,
                onBack = { destination = ChatDestination.Thread },
                onOpenSearch = { destination = ChatDestination.Search },
                onOpenMedia = { destination = ChatDestination.Media },
                onOpenStarred = { destination = ChatDestination.Starred },
                onRequestClear = viewModel::requestClearConversation,
                onDismissClearConfirm = viewModel::dismissClearConfirm,
                onConfirmClear = {
                    viewModel.confirmClearConversation()
                    // Real port of iOS `SakhiAIInfoView`'s `dismiss()` after a
                    // successful clear -- returns to the (now-empty) thread.
                    destination = ChatDestination.Thread
                },
            )

            ChatDestination.Search -> ChatSearchScreen(
                messages = uiState.messages,
                onBack = { destination = ChatDestination.Info },
            )

            ChatDestination.Media -> ChatMediaScreen(
                messages = uiState.messages,
                onBack = { destination = ChatDestination.Info },
            )

            ChatDestination.Starred -> ChatStarredScreen(
                messages = uiState.messages,
                starredStore = starredStore,
                onBack = { destination = ChatDestination.Info },
            )
        }
    }

        // Real port of iOS `SakhiAIChatView`'s `.overlay(alignment: .top) { errorToast(...) }`
        // -- this state was already tracked in `ChatUiState.error` (history-load and
        // send-message failures both set it) but nothing ever rendered it, so a real
        // failure silently produced zero feedback. Only shown on the root thread, same
        // as iOS (the overlay sits on the NavigationStack's root view, not a pushed
        // destination).
        AnimatedVisibility(
            visible = uiState.error != null && destination == ChatDestination.Thread,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = SakhiSpacing.space10),
        ) {
            uiState.error?.let { errorMessage ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                    modifier = Modifier
                        .padding(horizontal = SakhiSpacing.space6)
                        .background(
                            color = SakhiColors.groupF.toastError.toComposeColor().copy(alpha = 0.85f),
                            shape = RoundedCornerShape(SakhiRadius.full),
                        )
                        .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White,
                    )
                    Text(
                        text = errorMessage,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    expandedPlaces?.let { places ->
        PlacesDetailScreen(places = places, onBack = { expandedPlaces = null })
    }
}

private val nearbyPlacesDistanceStopsKm = listOf(0.5, 1.0, 2.0, 5.0)

@Composable
private fun PlacesDetailScreen(places: List<team.sakhi.models.SafePlace>, onBack: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val cameraPositionState = rememberCameraPositionState()
    var mapLoaded by remember(places) { mutableStateOf(false) }
    var maxDistanceKm by remember(places) { mutableStateOf(defaultNearbyRadiusKm(places)) }
    var selectedPlaceId by remember(places) { mutableStateOf<String?>(null) }
    val filteredPlaces = remember(places, maxDistanceKm) {
        places.filter { it.distanceMeters <= maxDistanceKm * 1000.0 }
    }
    val visiblePlaces = if (filteredPlaces.isEmpty()) places else filteredPlaces
    val mapPaddingPx = with(density) { 40.dp.roundToPx() }

    LaunchedEffect(mapLoaded, visiblePlaces, maxDistanceKm) {
        if (!mapLoaded) return@LaunchedEffect
        cameraPositionState.move(
            cameraUpdateForPlaces(
                places = visiblePlaces,
                paddingPx = mapPaddingPx,
                radiusKm = maxDistanceKm,
            ),
        )
    }

    SheetSurface(showDragHandle = true) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_nearby_places),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = stringResource(
                            R.string.chat_places_within_radius,
                            filteredPlaces.size,
                            formatNearbyRadius(maxDistanceKm),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = sakhiSecondaryLabel(),
                    )
                }
            }
            HorizontalDivider()

            Surface(
                shape = RoundedCornerShape(SakhiRadius.lg),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(256.dp)
                    .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        mapToolbarEnabled = false,
                    ),
                    properties = MapProperties(),
                    onMapLoaded = { mapLoaded = true },
                    onMapClick = { selectedPlaceId = null },
                ) {
                    visiblePlaces.forEach { place ->
                        val selected = selectedPlaceId == place.placeId
                        Marker(
                            state = rememberMarkerState(position = place.toLatLng()),
                            title = place.name,
                            snippet = buildString {
                                append(place.formattedDistance)
                                append(" away")
                                place.rating?.let { append(" • %.1f★".format(Locale.getDefault(), it)) }
                            },
                            icon = BitmapDescriptorFactory.defaultMarker(
                                if (selected) BitmapDescriptorFactory.HUE_ROSE else BitmapDescriptorFactory.HUE_RED,
                            ),
                            onClick = {
                                selectedPlaceId = if (selected) null else place.placeId
                                false
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space1)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            ) {
                nearbyPlacesDistanceStopsKm.forEach { stop ->
                    val selected = maxDistanceKm == stop
                    // iOS `SakhiAIPlacesCard.radiusChip`:
                    //   .background(Capsule().fill(isSelected ? DS.Colors.pink : DS.Colors.background))
                    //   .overlay(Capsule().stroke(isSelected ? DS.Colors.pink
                    //                                        : DS.Colors.pink.opacity(0.12), lineWidth: 1))
                    // Android filled the unselected chip with `surfaceVariant` (lavender) and
                    // had no stroke at all, so the row read as grey-purple pills instead of
                    // pink-outlined ones.
                    Surface(
                        shape = RoundedCornerShape(SakhiRadius.full),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.background
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            },
                        ),
                        modifier = Modifier.clickable {
                            maxDistanceKm = stop
                            selectedPlaceId = null
                        },
                    ) {
                        Text(
                            text = formatNearbyRadius(stop),
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = SakhiSpacing.space3, vertical = SakhiSpacing.space2),
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.chat_search_radius),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = sakhiSecondaryLabel(),
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space1),
            )

            HorizontalDivider(modifier = Modifier.padding(top = SakhiSpacing.space2))

            if (filteredPlaces.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.chat_no_places_within, formatNearbyRadius(maxDistanceKm)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = sakhiSecondaryLabel(),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = SakhiSpacing.space5,
                        vertical = SakhiSpacing.space3,
                    ),
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                ) {
                    itemsIndexed(filteredPlaces, key = { _, place -> place.placeId }) { _, place ->
                        val selected = selectedPlaceId == place.placeId
                        Surface(
                            shape = RoundedCornerShape(SakhiRadius.lg),
                            tonalElevation = SakhiSpacing.space1,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPlaceId = if (selected) null else place.placeId
                                    val update = CameraUpdateFactory.newLatLngZoom(
                                        place.toLatLng(),
                                        if (maxDistanceKm <= 1.0) 15f else 14f,
                                    )
                                    if (mapLoaded) {
                                        scope.launch { cameraPositionState.animate(update) }
                                    } else {
                                        cameraPositionState.move(update)
                                    }
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(SakhiSpacing.space4),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = place.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                                        Text(
                                            text = stringResource(R.string.chat_place_distance_away, place.formattedDistance),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = sakhiSecondaryLabel(),
                                        )
                                        place.rating?.let { rating ->
                                            Text(
                                                text = stringResource(R.string.chat_place_rating, rating),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = sakhiSecondaryLabel(),
                                            )
                                        }
                                    }
                                }
                                TextButton(onClick = { openWalkingDirections(context, place) }) {
                                    Text(stringResource(R.string.chat_go))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun defaultNearbyRadiusKm(places: List<team.sakhi.models.SafePlace>): Double {
    val nearestKm = places.minOfOrNull { it.distanceMeters / 1000.0 } ?: return nearbyPlacesDistanceStopsKm.last()
    return nearbyPlacesDistanceStopsKm.firstOrNull { it >= nearestKm } ?: nearbyPlacesDistanceStopsKm.last()
}

private fun formatNearbyRadius(radiusKm: Double): String {
    return if (radiusKm < 1.0) {
        "${(radiusKm * 1000).toInt()}m"
    } else {
        "${radiusKm.toInt()}km"
    }
}

private fun cameraUpdateForPlaces(
    places: List<team.sakhi.models.SafePlace>,
    paddingPx: Int,
    radiusKm: Double,
) = when {
    places.isEmpty() -> CameraUpdateFactory.newLatLngZoom(LatLng(20.5937, 78.9629), 4.5f)
    places.size == 1 -> CameraUpdateFactory.newLatLngZoom(
        places.first().toLatLng(),
        if (radiusKm <= 1.0) 15f else 14f,
    )
    else -> {
        val bounds = LatLngBounds.builder().apply {
            places.forEach { include(it.toLatLng()) }
        }.build()
        CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)
    }
}

private fun team.sakhi.models.SafePlace.toLatLng(): LatLng = LatLng(latitude, longitude)

private fun openWalkingDirections(
    context: android.content.Context,
    place: team.sakhi.models.SafePlace,
) {
    val uri = android.net.Uri.parse("google.navigation:q=${place.latitude},${place.longitude}&mode=w")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        val fallback = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("geo:${place.latitude},${place.longitude}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(fallback) }
    }
}

@Composable
private fun ChatHeader(uiState: ChatUiState, onInfoClick: () -> Unit, onClose: () -> Unit) {
    val isOnline = uiState.isSending
    val headerAccessibilityLabel = stringResource(R.string.chat_header_accessibility_label)
    val headerAccessibilityState = if (isOnline) {
        stringResource(R.string.chat_header_accessibility_online)
    } else {
        stringResource(R.string.chat_header_accessibility_last_seen_today_at, chatHeaderLastSeenTime())
    }
    Column {
        // Same nav bar as every other sheet; Chat's identity block goes in its `leading`
        // slot rather than the bar being rebuilt around it, which is what left this close
        // button sized and inset differently from Profile's.
        SakhiNavBar(
            onClose = onClose,
            leading = {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onInfoClick)
                    .semantics(mergeDescendants = true) {
                        contentDescription = headerAccessibilityLabel
                        stateDescription = headerAccessibilityState
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                // The real Sakhi mark, matching iOS's
                // `Image("BrandMedia/AppLogo").frame(40, 40).clipShape(Circle())`.
                // Android was drawing a generic Material `AutoAwesome` sparkle here, so
                // the assistant Karan's users see had no brand identity at all — the one
                // place in Chat where the product actually introduces itself.
                Image(
                    painter = painterResource(team.sakhi.android.ui.R.drawable.sakhi_app_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isOnline) {
                        // iOS `presenceLabel`: `Circle().fill(DS.Colors.permissionSuccess)
                        // .frame(width: 7, height: 7)` and the same colour on the label.
                        // Android used `colorScheme.tertiary`, a slot this theme never sets,
                        // so the "online" indicator rendered in Material's default
                        // purple-brown instead of green.
                        // `PERMISSION_SUCCESS` (#33B770) deliberately differs from
                        // `COLOR_CONFIRM` (#34C759) — SakhiUIColors says so on the line
                        // itself — so the nearer-looking `brand.confirm` is not a substitute.
                        val onlineColor = SakhiUIColors.PERMISSION_SUCCESS.toComposeColor()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(onlineColor, CircleShape),
                            )
                            Text(
                                text = stringResource(R.string.chat_online),
                                style = MaterialTheme.typography.labelSmall,
                                color = onlineColor,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.chat_last_seen_today_at, chatHeaderLastSeenTime()),
                            // `labelSmall` is 10sp here, below iOS's own `.lato(11)` for
                            // this line and hard to read under the name.
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = ChatLastSeenFontSize),
                            color = sakhiSecondaryLabel(),
                        )
                    }
                }
            }

            },
        )
        HorizontalDivider()
    }
}

@Composable
private fun chatHeaderLastSeenTime(): String =
    DateTimeFormatter.ofPattern(stringResource(R.string.chat_last_seen_time_format), Locale.getDefault())
        .format(java.time.Instant.now().minusSeconds(60).atZone(ZoneId.systemDefault()))

@Composable
private fun TodaySeparator() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = SakhiSpacing.space4), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(SakhiRadius.full),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Text(
                text = stringResource(R.string.chat_today),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = SakhiSpacing.space3, vertical = SakhiSpacing.space1),
            )
        }
    }
}

@Composable
private fun SuggestedChipsRow(chips: List<String>, onChipClick: (String) -> Unit) {
    if (chips.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        chips.forEach { chip ->
            SuggestedChip(label = chip, onClick = { onChipClick(chip) })
        }
    }
}

@Composable
private fun SuggestedChip(label: String, onClick: () -> Unit) {
    val icon = iconForChip(label)
    Surface(
        shape = RoundedCornerShape(SakhiRadius.full),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(SakhiRadius.full),
                )
                .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun iconForChip(chip: String): androidx.compose.ui.graphics.vector.ImageVector {
    val l = chip.lowercase()
    return when {
        l.contains("period") || l.contains("cycle") -> Icons.Filled.WaterDrop
        l.contains("washroom") || l.contains("find") -> Icons.Filled.Place
        l.contains("unsafe") || l.contains("help") -> Icons.Filled.Shield
        l.contains("low") || l.contains("feel") -> Icons.Filled.Favorite
        l.contains("phase") || l.contains("today") -> Icons.Filled.AutoAwesome
        l.contains("how is") || l.contains("support") -> Icons.Filled.Handshake
        l.contains("partner") || l.contains("care") -> Icons.Filled.People
        else -> Icons.Filled.Spa
    }
}

@Composable
private fun MessageBubble(
    message: ConversationMessage,
    isStarred: Boolean,
    onToggleStar: () -> Unit,
    isLastInGroup: Boolean,
    groupTopPadding: androidx.compose.ui.unit.Dp,
    showReadTick: Boolean,
    onExpandPlaces: () -> Unit,
) {
    val bubbleShape = if (message.isUser) {
        RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = 18.dp,
            bottomEnd = if (isLastInGroup) 3.dp else 18.dp,
        )
    } else {
        RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomEnd = 18.dp,
            bottomStart = if (isLastInGroup) 3.dp else 18.dp,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space6, vertical = groupTopPadding / 4),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        val speakerYou = stringResource(R.string.chat_speaker_you)
        val speakerSakhi = stringResource(R.string.chat_speaker_sakhi)
        val messageAccessibilityLabel = stringResource(
            R.string.chat_message_content_description,
            if (message.isUser) speakerYou else speakerSakhi,
            message.content,
        )
        Surface(
            shape = bubbleShape,
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            tonalElevation = if (message.isUser) 0.dp else SakhiSpacing.space1,
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .semantics(mergeDescendants = true) {
                    contentDescription = messageAccessibilityLabel
                }
                .combinedClickable(
                    onClick = {},
                    onLongClick = onToggleStar,
                ),
        ) {
            Column(modifier = Modifier.padding(horizontal = SakhiSpacing.space3, vertical = SakhiSpacing.space2)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isStarred) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(SakhiSpacing.space1))
                    }
                    Text(
                        text = formattedTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = sakhiSecondaryLabel(),
                    )
                    if (message.isUser) {
                        Spacer(modifier = Modifier.width(SakhiSpacing.space1))
                        MessageTick(message = message, showRead = showReadTick)
                    }
                }
            }
        }
    }

    if (message.places.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space1),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        ) {
            PlacesCard(
                places = message.places,
                onClick = onExpandPlaces,
                modifier = Modifier
                    .fillMaxWidth(0.78f),
            )
        }
    }
}

@Composable
private fun PlacesCard(
    places: List<team.sakhi.models.SafePlace>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openNearbyPlacesLabel = stringResource(R.string.chat_open_nearby_places)
    val density = LocalDensity.current
    val cameraPositionState = rememberCameraPositionState()
    var mapLoaded by remember(places) { mutableStateOf(false) }
    val nearestPlace = remember(places) { places.minByOrNull { it.distanceMeters } }
    val mapPaddingPx = with(density) { 28.dp.roundToPx() }

    LaunchedEffect(mapLoaded, places) {
        if (!mapLoaded) return@LaunchedEffect
        cameraPositionState.move(
            cameraUpdateForPlaces(
                places = places,
                paddingPx = mapPaddingPx,
                radiusKm = defaultNearbyRadiusKm(places),
            ),
        )
    }

    Surface(
        shape = RoundedCornerShape(SakhiRadius.xl),
        tonalElevation = SakhiSpacing.space1,
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = openNearbyPlacesLabel
            }
            .clickable(onClick = onClick),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = SakhiSpacing.space3, top = SakhiSpacing.space3, end = SakhiSpacing.space3, bottom = SakhiSpacing.space2),
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                // iOS `SakhiAIPlacesCard`: `Capsule().fill(DS.Colors.background)`.
                                color = MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(SakhiRadius.full),
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(SakhiRadius.full),
                            )
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = stringResource(R.string.chat_nearby_label),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.chat_nearby_places),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            // iOS `SakhiAIPlacesCard`: `Capsule().fill(DS.Colors.background)`,
                            // the brand background -- not Material's lavender surfaceVariant.
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(SakhiRadius.full),
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(SakhiRadius.full),
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = places.size.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(SakhiRadius.lg),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .padding(horizontal = SakhiSpacing.space3),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(
                            scrollGesturesEnabled = false,
                            zoomGesturesEnabled = false,
                            tiltGesturesEnabled = false,
                            rotationGesturesEnabled = false,
                            zoomControlsEnabled = false,
                            mapToolbarEnabled = false,
                        ),
                        properties = MapProperties(),
                        onMapLoaded = { mapLoaded = true },
                    ) {
                        places.forEach { place ->
                            Marker(
                                state = rememberMarkerState(position = place.toLatLng()),
                                title = place.name,
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClick,
                            ),
                    )

                    nearestPlace?.let { place ->
                        Surface(
                            shape = RoundedCornerShape(SakhiRadius.full),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            tonalElevation = SakhiSpacing.space1,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = SakhiSpacing.space3, vertical = SakhiSpacing.space2),
                                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = place.formattedDistance,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = place.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sakhiSecondaryLabel(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(
                    start = SakhiSpacing.space3,
                    top = SakhiSpacing.space2,
                    end = SakhiSpacing.space3,
                    bottom = SakhiSpacing.space3,
                ),
            ) {
                places.take(3).forEach { place ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SakhiSpacing.space2),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = place.formattedDistance,
                            style = MaterialTheme.typography.labelSmall,
                            color = sakhiSecondaryLabel(),
                        )
                    }
                }
                if (places.size > 3) {
                    Text(
                        text = pluralStringResource(R.plurals.chat_view_more_places, places.size - 3, places.size - 3),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = SakhiSpacing.space1),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageTick(message: ConversationMessage, showRead: Boolean) {
    val (icon, tint) = when {
        message.isFailed -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        !message.isSynced -> Icons.Filled.AccessTime to sakhiSecondaryLabel()
        showRead -> Icons.Filled.DoneAll to MaterialTheme.colorScheme.primary
        else -> Icons.Filled.Done to sakhiSecondaryLabel()
    }
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
}

@Composable
private fun TypingIndicator() {
    val typingLabel = stringResource(R.string.chat_typing)
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(360)
            phase = (phase + 1) % 3
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2)
            .semantics(mergeDescendants = true) {
                contentDescription = typingLabel
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 3.dp),
            tonalElevation = SakhiSpacing.space1,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = SakhiSpacing.space3, vertical = SakhiSpacing.space3),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(3) { index ->
                    val isActive = index == phase
                    val dotOffsetY by animateDpAsState(
                        targetValue = if (isActive) (-5).dp else 0.dp,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
                        label = "typingDotOffset$index",
                    )
                    val dotScale by animateFloatAsState(
                        targetValue = if (isActive) 1.2f else 0.85f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
                        label = "typingDotScale$index",
                    )
                    Box(
                        modifier = Modifier
                            .offset(y = dotOffsetY)
                            .size(7.dp)
                            .graphicsLayer {
                                scaleX = dotScale
                                scaleY = dotScale
                            }
                            .background(
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

private val chatPlaceholders = listOf(
    R.string.chat_placeholder_ask_anything,
    R.string.chat_placeholder_feeling_today,
    R.string.chat_placeholder_on_your_mind,
    R.string.chat_placeholder_about_cycle,
    R.string.chat_placeholder_worrying,
)
private val chatPartnerPlaceholders = listOf(
    R.string.chat_partner_placeholder_hows_she,
    R.string.chat_partner_placeholder_what_needs,
    R.string.chat_partner_placeholder_going_through,
    R.string.chat_partner_placeholder_help_today,
)

@Composable
private fun ChatInputBar(
    text: String,
    isPartnerMode: Boolean,
    isSending: Boolean,
    isLocked: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    val placeholders = if (isPartnerMode) chatPartnerPlaceholders else chatPlaceholders
    val focusedPlaceholder = stringResource(
        if (isPartnerMode) {
            R.string.chat_partner_placeholder_focused
        } else {
            R.string.chat_placeholder_focused
        },
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val density = LocalDensity.current
    val placeholderEnterOffsetPx = remember(density) { with(density) { 5.dp.roundToPx() } }
    val placeholderExitOffsetPx = remember(density) { with(density) { (-4).dp.roundToPx() } }
    var placeholderIndex by remember(placeholders) { mutableIntStateOf(0) }
    LaunchedEffect(text, isFocused, placeholders) {
        if (text.isNotEmpty() || isFocused) return@LaunchedEffect
        while (true) {
            delay(4_000)
            placeholderIndex = (placeholderIndex + 1) % placeholders.size
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            modifier = Modifier.weight(1f),
        ) {
            TextField(
                value = text,
                onValueChange = onTextChanged,
                enabled = !isLocked,
                modifier = Modifier.fillMaxWidth(),
                interactionSource = interactionSource,
                placeholder = {
                    when {
                        text.isEmpty() && isFocused -> {
                            Text(text = focusedPlaceholder)
                        }
                        text.isEmpty() -> {
                            AnimatedContent(
                                targetState = placeholderIndex,
                                transitionSpec = {
                                    (
                                        slideInVertically(
                                            initialOffsetY = { placeholderEnterOffsetPx },
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                                        ) + fadeIn(animationSpec = tween(durationMillis = 350, delayMillis = 80))
                                        ).togetherWith(
                                        slideOutVertically(
                                            targetOffsetY = { placeholderExitOffsetPx },
                                            animationSpec = tween(durationMillis = 100),
                                        ) + fadeOut(animationSpec = tween(durationMillis = 100)),
                                    )
                                },
                                contentAlignment = Alignment.CenterStart,
                                label = "chat-placeholder",
                            ) { index ->
                                Text(text = stringResource(placeholders[index % placeholders.size]))
                            }
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                ),
                maxLines = 5,
            )
        }

        val hasText = text.isNotBlank()
        Surface(
            shape = CircleShape,
            // iOS `SakhiAIInputBar`: `Circle().fill(canSend ? DS.Colors.pink : DS.Colors.gray5)`.
            // `surfaceVariant` made the idle send button read lavender.
            // Karan: the send button should never read as disabled. It stays on the brand
            // fill whenever the input is usable, so it never greys out mid-conversation.
            // The tap is still guarded on there being text -- an always-enabled button
            // that posts an empty message would be worse than a grey one -- but the guard
            // is now invisible rather than a dead grey circle sitting next to the field.
            // A send in flight and a locked input are still shown, because those are real
            // states the user needs to see.
            color = if (isSending || isLocked) sakhiSystemGray5() else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(36.dp)
                .clickable(enabled = !isSending && !isLocked) { if (hasText) onSend() },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = stringResource(R.string.chat_send),
                        // iOS `SakhiAIInputBar`: the capsule is
                        // `canSend ? DS.Colors.pink : DS.Colors.gray5`, but the arrow itself
                        // is `.foregroundColor(.white)` **unconditionally** -- white on both
                        // fills. Android tinted it grey when there was nothing to send, which
                        // put a grey glyph on the grey idle capsule.
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

internal fun formattedTime(timestampIso: String): String {
    val instant = runCatching { Instant.parse(timestampIso) }.getOrNull() ?: return ""
    return runCatching {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .format(java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()).atZone(ZoneId.systemDefault()))
    }.getOrDefault("")
}

internal fun formattedDate(timestampIso: String): String {
    val instant = runCatching { Instant.parse(timestampIso) }.getOrNull() ?: return ""
    return runCatching {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .format(java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()).atZone(ZoneId.systemDefault()))
    }.getOrDefault("")
}

/** iOS uses `.lato(11)` for the header's last-seen line; 10sp read too small. */
private val ChatLastSeenFontSize = 12.sp
