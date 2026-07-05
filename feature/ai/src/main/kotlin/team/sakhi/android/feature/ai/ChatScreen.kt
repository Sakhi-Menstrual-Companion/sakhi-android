package team.sakhi.android.feature.ai

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.ui.SheetSurface
import team.sakhi.models.ConversationMessage

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
 * - The nearby-places button and `SakhiAIPlacesCard` (Google Places integration).
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
    val messages = uiState.messages
    val hapticManager = koinInject<AndroidHapticManager>()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val starredStore = rememberStarredMessagesStore()
    var destination by rememberSaveable { mutableStateOf(ChatDestination.Thread) }
    var expandedPlaces by remember { mutableStateOf<List<team.sakhi.models.SafePlace>?>(null) }
    val activeSessionKey = uiState.session?.let {
        "${it.userId}|${it.targetUserId}|${it.isViewingOwnData}"
    }

    LaunchedEffect(activeSessionKey) {
        destination = ChatDestination.Thread
        expandedPlaces = null
    }

    LaunchedEffect(uiState.messages.size, uiState.isSending, uiState.reportSession != null) {
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
        context.startActivity(Intent.createChooser(shareIntent, "Share health report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
    SheetSurface {
        when (destination) {
            ChatDestination.Thread -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    ChatHeader(
                        uiState = uiState,
                        onInfoClick = { destination = ChatDestination.Info },
                        onClose = onClose,
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        if (messages.isEmpty() && !uiState.isSending) {
                            Column {
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
                                item(key = "today") { TodaySeparator() }

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

                                if (uiState.isSending) {
                                    item(key = "typing") { TypingIndicator() }
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    ChatInputBar(
                        text = uiState.inputText,
                        isPartnerMode = uiState.session?.isViewingOwnData == false,
                        isSending = uiState.isSending,
                        isLocked = uiState.reportSession != null,
                        onTextChanged = viewModel::onInputChanged,
                        onSend = viewModel::sendCurrentMessage,
                    )
                }
            }

            ChatDestination.Info -> ChatInfoScreen(
                messages = uiState.messages,
                starredStore = starredStore,
                onBack = { destination = ChatDestination.Thread },
                onOpenSearch = { destination = ChatDestination.Search },
                onOpenMedia = { destination = ChatDestination.Media },
                onOpenStarred = { destination = ChatDestination.Starred },
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

    expandedPlaces?.let { places ->
        PlacesDetailScreen(places = places, onBack = { expandedPlaces = null })
    }
}

/**
 * Full list version of the compact `PlacesCard` -- ports the list portion of
 * iOS `SakhiAIPlacesDetailSheet` (all places, name/distance/rating, a "Go"
 * directions action), not its interactive map + distance-radius filter chips
 * (needs Google Maps Compose SDK, a separate larger dependency; documented as
 * a deferred visual gap in the Nearby Places Live Status entry).
 */
@Composable
private fun PlacesDetailScreen(places: List<team.sakhi.models.SafePlace>, onBack: () -> Unit) {
    SheetSurface(showDragHandle = true) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Nearby Places",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            places.forEach { place ->
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.lg),
                    tonalElevation = SakhiSpacing.space1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SakhiSpacing.space4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = place.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                            Row(horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                                Text(
                                    text = "${place.formattedDistance} away",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                place.rating?.let { rating ->
                                    Text(
                                        text = "★ ${"%.1f".format(rating)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        val context = LocalContext.current
                        TextButton(onClick = {
                            val uri = android.net.Uri.parse(
                                "google.navigation:q=${place.latitude},${place.longitude}&mode=w",
                            )
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                setPackage("com.google.android.apps.maps")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    val fallback = Intent(
                                        Intent.ACTION_VIEW,
                                        android.net.Uri.parse("geo:${place.latitude},${place.longitude}"),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { context.startActivity(fallback) }
                                }
                        }) {
                            Text("Go")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(uiState: ChatUiState, onInfoClick: () -> Unit, onClose: () -> Unit) {
    val isOnline = uiState.isSending
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onInfoClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sakhi AI",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isOnline) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                            )
                            Text(
                                text = "online",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    } else {
                        Text(
                            text = "Ask me anything",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun TodaySeparator() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = SakhiSpacing.space4), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(SakhiRadius.full),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Text(
                text = "Today",
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
        tonalElevation = SakhiSpacing.space1,
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space2),
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
                    contentDescription = buildString {
                        append(if (message.isUser) "You" else "Sakhi")
                        append(", ")
                        append(message.content)
                        formattedTime(message.timestamp).takeIf { it.isNotBlank() }?.let {
                            append(", ")
                            append(it)
                        }
                        if (isStarred) {
                            append(", starred")
                        }
                        if (message.isUser) {
                            append(", ")
                            append(
                                when {
                                    message.isFailed -> "Failed to send"
                                    !message.isSynced -> "Sending"
                                    showReadTick -> "Read"
                                    else -> "Sent"
                                },
                            )
                        }
                    }
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .clickable(onClick = onExpandPlaces),
            )
        }
    }
}

/**
 * Ports iOS `SakhiAIPlacesCard.swift`'s compact card (title + up to 3 rows +
 * "view N more") without its interactive MapKit-equivalent map/detail sheet --
 * that needs the Google Maps Compose SDK, a separate, larger dependency;
 * documented as a deferred visual gap, not a functional one. Real data
 * throughout: `message.places` comes from the shared `SafePlaceRanker`, not a
 * placeholder.
 */
@Composable
private fun PlacesCard(places: List<team.sakhi.models.SafePlace>, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xl),
        tonalElevation = SakhiSpacing.space1,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(modifier = Modifier.padding(SakhiSpacing.space3)) {
            Text(
                text = "NEARBY",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (places.size > 3) {
                Text(
                    text = "View ${places.size - 3} more",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = SakhiSpacing.space1),
                )
            }
        }
    }
}

@Composable
private fun MessageTick(message: ConversationMessage, showRead: Boolean) {
    val (icon, tint) = when {
        message.isFailed -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        !message.isSynced -> Icons.Filled.AccessTime to MaterialTheme.colorScheme.onSurfaceVariant
        showRead -> Icons.Filled.DoneAll to MaterialTheme.colorScheme.primary
        else -> Icons.Filled.Done to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
}

@Composable
private fun TypingIndicator() {
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
            .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2),
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
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                color = if (index == phase) {
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
    "Ask Sakhi anything...",
    "How are you feeling today?",
    "What's on your mind?",
    "Ask about your cycle...",
    "Something worrying you?",
)
private val chatPartnerPlaceholders = listOf(
    "How's she doing today?",
    "What does she need right now?",
    "What's she going through?",
    "How can we help her today?",
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
    var placeholderIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        if (text.isNotEmpty()) return@LaunchedEffect
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
                placeholder = {
                    AnimatedContent(
                        targetState = placeholderIndex,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "chat-placeholder",
                    ) { index ->
                        Text(text = placeholders[index % placeholders.size])
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
            color = if (hasText && !isSending && !isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(36.dp)
                .clickable(enabled = hasText && !isSending && !isLocked, onClick = onSend),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (hasText) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

internal fun formattedTime(timestampIso: String): String {
    val instant = runCatching { Instant.parse(timestampIso) }.getOrNull() ?: return ""
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour24 = local.hour
    val amPm = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    val minute = local.minute.toString().padStart(2, '0')
    return "$hour12:$minute $amPm"
}
