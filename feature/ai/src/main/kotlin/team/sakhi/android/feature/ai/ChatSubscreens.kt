package team.sakhi.android.feature.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiLightPink
import team.sakhi.android.designsystem.sakhiSystemGray5
import team.sakhi.android.designsystem.sakhiSystemGray6
import team.sakhi.android.ui.BackButton
import team.sakhi.android.feature.reports.ReportDateRangePreset
import team.sakhi.android.ui.EmptyState
import team.sakhi.android.ui.GlassCard
import team.sakhi.android.ui.SakhiNavBar
import team.sakhi.android.ui.SakhiTextField
import team.sakhi.models.AICardType
import team.sakhi.models.ConversationMessage
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import team.sakhi.android.designsystem.sakhiSecondaryLabel

internal enum class ChatDestination {
    Thread,
    Info,
    Search,
    Media,
    Starred,
}

@Composable
internal fun ChatInfoScreen(
    messages: List<ConversationMessage>,
    starredStore: StarredMessagesStore,
    showClearConfirm: Boolean,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenStarred: () -> Unit,
    onRequestClear: () -> Unit,
    onDismissClearConfirm: () -> Unit,
    onConfirmClear: () -> Unit,
) {
    val mediaCount = remember(messages) { messages.count { it.isAssistant && it.cardType != AICardType.GENERAL } }
    val linkCount = remember(messages) { extractLinkItems(messages).size }
    val starredCount = remember(messages, starredStore.ids) { messages.count { starredStore.isStarred(it.id) } }
    val messageCount = remember(messages) { messages.count { it.sessionId != "welcome" } }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = onDismissClearConfirm,
            title = { Text(stringResource(R.string.chat_info_clear_title)) },
            text = { Text(stringResource(R.string.chat_info_clear_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmClear) {
                    Text(
                        text = stringResource(R.string.chat_info_clear_title),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissClearConfirm) {
                    Text(stringResource(R.string.chat_info_clear_cancel))
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatSubscreenHeader(title = stringResource(R.string.chat_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = SakhiSpacing.space6,
                vertical = SakhiSpacing.space5,
            ),
        ) {
            item(key = "profile") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                ) {
                    // iOS `SakhiAIInfoView` shows the real brand mark here:
                    // `Image("BrandMedia/AppLogo").frame(width: 80, height: 80)`.
                    // The chat header was corrected to use the logo earlier; this screen
                    // kept the generic Material sparkle, so the one screen that exists to
                    // introduce Sakhi was the one still showing a stock glyph.
                    Image(
                        painter = painterResource(team.sakhi.android.ui.R.drawable.sakhi_app_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                    )
                    Text(
                        text = stringResource(R.string.chat_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = stringResource(R.string.chat_info_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = sakhiSecondaryLabel(),
                    )
                }
            }

            item(key = "stats") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                ) {
                    ChatStatTile(
                        value = messageCount.toString(),
                        label = stringResource(R.string.chat_info_stat_messages),
                        modifier = Modifier.weight(1f),
                    )
                    ChatStatTile(
                        value = mediaCount.toString(),
                        label = stringResource(R.string.chat_info_stat_cards),
                        modifier = Modifier.weight(1f),
                    )
                    ChatStatTile(
                        value = starredCount.toString(),
                        label = stringResource(R.string.chat_info_stat_starred),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(key = "search") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    ChatActionRow(
                        icon = Icons.Filled.Search,
                        title = stringResource(R.string.chat_info_search_title),
                        isLast = true,
                        onClick = onOpenSearch,
                    )
                }
            }

            item(key = "content") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    ChatActionRow(
                        icon = Icons.Filled.ViewCarousel,
                        title = stringResource(R.string.chat_info_media_title),
                        badge = (mediaCount + linkCount).takeIf { it > 0 }?.toString(),
                        isLast = false,
                        onClick = onOpenMedia,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = SakhiSpacing.space8),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                    )
                    ChatActionRow(
                        icon = Icons.Filled.Star,
                        title = stringResource(R.string.chat_info_starred_title),
                        badge = starredCount.takeIf { it > 0 }?.toString(),
                        isLast = true,
                        onClick = onOpenStarred,
                    )
                }
            }

            // Real port of iOS `SakhiAIInfoView.swift`'s `dangerCard` -- was missing
            // from Android entirely before this pass.
            item(key = "danger") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    ChatActionRow(
                        icon = Icons.Filled.Delete,
                        title = stringResource(R.string.chat_info_clear_title),
                        isLast = true,
                        destructive = true,
                        onClick = onRequestClear,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChatReportCard(
    session: ChatReportSession,
    onSelectRange: (ReportDateRangePreset) -> Unit,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_report_card_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = stringResource(R.string.chat_report_card_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = sakhiSecondaryLabel(),
                )
            }

            if (!session.isGenerating) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            // iOS `SakhiAIReportCard`: `.frame(width: 26, height: 26)
                            // .background(Circle().fill(DS.Colors.gray6))`. `surfaceVariant`
                            // rendered this lavender.
                            .background(
                                color = sakhiSystemGray6(),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.chat_report_card_close),
                            tint = sakhiSecondaryLabel(),
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = SakhiSpacing.space4),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )

        if (session.isGenerating) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SakhiSpacing.space5),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.chat_report_card_building),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                )
            }
            return@GlassCard
        }

        Text(
            text = stringResource(R.string.chat_report_card_range_prompt),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = SakhiSpacing.space4),
        )

        Column(
            modifier = Modifier.padding(top = SakhiSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            listOf(
                ReportDateRangePreset.LastMonth,
                ReportDateRangePreset.ThreeMonths,
                ReportDateRangePreset.SixMonths,
                ReportDateRangePreset.OneYear,
            ).forEach { preset ->
                val isSelected = session.selectedRange == preset
                val stateDescription = stringResource(
                    if (isSelected) R.string.chat_option_selected else R.string.chat_option_not_selected,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SakhiSpacing.space1)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .semantics {
                            this.selected = isSelected
                            role = Role.RadioButton
                            this.stateDescription = stateDescription
                        }
                        .clickable { onSelectRange(preset) }
                        .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(preset.shortLabelRes),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        Button(
            onClick = onGenerate,
            shape = RoundedCornerShape(SakhiRadius.full),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SakhiSpacing.space4)
                .height(52.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_report_card_generate),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
internal fun ChatSearchScreen(
    messages: List<ConversationMessage>,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val trimmedQuery = query.text.trim()
    val results = remember(messages, trimmedQuery) {
        if (trimmedQuery.isEmpty()) emptyList()
        else messages.filter { it.content.contains(trimmedQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatSubscreenHeader(title = stringResource(R.string.chat_search_title), onBack = onBack)
        SakhiTextField(
            value = query.text,
            onValueChange = { query = query.copy(text = it) },
            placeholder = stringResource(R.string.chat_search_placeholder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space3),
        )

        when {
            trimmedQuery.isEmpty() -> {
                EmptyState(
                    title = stringResource(R.string.chat_search_empty_title),
                    subtitle = stringResource(R.string.chat_search_empty_subtitle),
                    modifier = Modifier.padding(top = SakhiSpacing.space8),
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = sakhiSecondaryLabel(),
                            modifier = Modifier.size(36.dp),
                        )
                    },
                )
            }
            results.isEmpty() -> {
                EmptyState(
                    title = stringResource(R.string.chat_search_no_results_title),
                    subtitle = stringResource(R.string.chat_search_no_results_subtitle, trimmedQuery),
                    modifier = Modifier.padding(top = SakhiSpacing.space8),
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = sakhiSecondaryLabel(),
                            modifier = Modifier.size(36.dp),
                        )
                    },
                )
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = SakhiSpacing.space6,
                        vertical = SakhiSpacing.space4,
                    ),
                ) {
                    items(results, key = { it.id }) { message ->
                        SearchResultRow(
                            message = message,
                            timestampText = formattedTime(message.timestamp),
                            contentMaxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatMediaScreen(
    messages: List<ConversationMessage>,
    onBack: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(MediaTab.Media) }
    val mediaMessages = remember(messages) {
        messages.filter { it.isAssistant && it.cardType != AICardType.GENERAL && it.cardType.isNotBlank() }
    }
    val linkItems = remember(messages) { extractLinkItems(messages) }
    val docMessages = remember(messages) {
        messages.filter {
            it.cardType in setOf(
                AICardType.SYMPTOM_SUMMARY,
                AICardType.CALENDAR_PREVIEW,
                AICardType.CHECK_IN,
                AICardType.DOCTOR_VISIT,
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatSubscreenHeader(title = stringResource(R.string.chat_media_title), onBack = onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2),
        ) {
            MediaTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val stateDescription = stringResource(
                    if (isSelected) R.string.chat_option_selected else R.string.chat_option_not_selected,
                )
                Text(
                    text = stringResource(tab.labelRes),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else sakhiSecondaryLabel(),
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            this.selected = isSelected
                            role = Role.Button
                            this.stateDescription = stateDescription
                        }
                        .clickable { selectedTab = tab }
                        .padding(vertical = SakhiSpacing.space2),
                )
            }
        }

        when (selectedTab) {
            MediaTab.Media -> {
                if (mediaMessages.isEmpty()) {
                    ChatEmptyState(
                        icon = Icons.Filled.ViewCarousel,
                        title = stringResource(R.string.chat_media_empty_cards_title),
                        subtitle = stringResource(R.string.chat_media_empty_cards_subtitle),
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = SakhiSpacing.space6,
                            vertical = SakhiSpacing.space4,
                        ),
                    ) {
                        items(mediaMessages, key = { it.id }) { message ->
                            MediaMessageRow(message = message)
                        }
                    }
                }
            }

            MediaTab.Links -> {
                if (linkItems.isEmpty()) {
                    ChatEmptyState(
                        icon = Icons.Filled.Link,
                        title = stringResource(R.string.chat_media_empty_links_title),
                        subtitle = stringResource(R.string.chat_media_empty_links_subtitle),
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = SakhiSpacing.space6,
                            vertical = SakhiSpacing.space4,
                        ),
                    ) {
                        items(linkItems, key = { it.url + it.messageId }) { item ->
                            LinkItemRow(item = item)
                        }
                    }
                }
            }

            MediaTab.Docs -> {
                if (docMessages.isEmpty()) {
                    ChatEmptyState(
                        icon = Icons.Filled.Description,
                        title = stringResource(R.string.chat_media_empty_docs_title),
                        subtitle = stringResource(R.string.chat_media_empty_docs_subtitle),
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = SakhiSpacing.space6,
                            vertical = SakhiSpacing.space4,
                        ),
                    ) {
                        items(docMessages, key = { it.id }) { message ->
                            DocMessageRow(message = message)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatStarredScreen(
    messages: List<ConversationMessage>,
    starredStore: StarredMessagesStore,
    onBack: () -> Unit,
) {
    val starredMessages = remember(messages, starredStore.ids) {
        messages.filter { starredStore.isStarred(it.id) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatSubscreenHeader(title = stringResource(R.string.chat_starred_title), onBack = onBack)

        if (starredMessages.isEmpty()) {
            ChatEmptyState(
                icon = Icons.Filled.Star,
                title = stringResource(R.string.chat_starred_empty_title),
                subtitle = stringResource(R.string.chat_starred_empty_subtitle),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = SakhiSpacing.space6,
                    vertical = SakhiSpacing.space4,
                ),
            ) {
                items(starredMessages, key = { it.id }) { message ->
                    SearchResultRow(
                        message = message,
                        timestampText = formattedDate(message.timestamp),
                        contentMaxLines = 3,
                        trailing = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { starredStore.toggle(message.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = stringResource(R.string.chat_starred_unstar),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatSubscreenHeader(
    title: String,
    onBack: () -> Unit,
) {
    Column {
        // Shared nav bar, same as every other sheet -- this used to be a bespoke row with
        // its own paddings and its own title size.
        SakhiNavBar(onBack = onBack, title = title)
        HorizontalDivider()
    }
}

@Composable
private fun ChatStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = sakhiSecondaryLabel(),
        )
    }
}

@Composable
private fun ChatActionRow(
    icon: ImageVector,
    title: String,
    badge: String? = null,
    isLast: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .padding(vertical = SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(tint.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) tint else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (!badge.isNullOrBlank()) {
            Text(
                text = badge,
                style = MaterialTheme.typography.bodySmall,
                color = sakhiSecondaryLabel(),
            )
        }
        // iOS's `dangerCard` row (`SakhiAIInfoView.swift`) has no trailing chevron --
        // this is a direct destructive action, not a drill-down navigation row.
        if (!destructive) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = sakhiSecondaryLabel(),
                modifier = Modifier
                    .size(14.dp)
                    .width(14.dp),
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    message: ConversationMessage,
    timestampText: String,
    contentMaxLines: Int,
    trailing: @Composable (() -> Unit)? = null,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    // iOS `SakhiAISearchView`: `Circle().fill(message.isUser ?
                    // DS.Colors.lightPink : DS.Colors.gray5).frame(width: 36, height: 36)`.
                    // Android had `primary.copy(alpha = 0.12f)` for the user side, which is a
                    // different colour from lightPink, and `surfaceVariant` for Sakhi's side,
                    // which is the lavender.
                    .background(
                        if (message.isUser) sakhiLightPink() else sakhiSystemGray5(),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (message.isUser) Icons.Filled.Person else Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = if (message.isUser) MaterialTheme.colorScheme.primary else sakhiSecondaryLabel(),
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (message.isUser) R.string.chat_speaker_you else R.string.chat_speaker_sakhi,
                        ),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = timestampText,
                        style = MaterialTheme.typography.labelSmall,
                        color = sakhiSecondaryLabel(),
                    )
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                    maxLines = contentMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun MediaMessageRow(message: ConversationMessage) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconForCardType(message.cardType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleForCardTypeRes(message.cardType)),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = sakhiSecondaryLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formattedDate(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = sakhiSecondaryLabel(),
            )
        }
    }
}

@Composable
private fun DocMessageRow(message: ConversationMessage) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleForCardTypeRes(message.cardType)),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = formattedDate(message.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = sakhiSecondaryLabel(),
                )
            }
        }
    }
}

@Composable
private fun LinkItemRow(item: LinkItem) {
    val context = LocalContext.current
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable { openUrl(context, item.url) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.host,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = sakhiSecondaryLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formattedTime(item.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = sakhiSecondaryLabel(),
            )
        }
    }
}

@Composable
private fun ChatEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    EmptyState(
        title = title,
        subtitle = subtitle,
        modifier = Modifier.padding(top = SakhiSpacing.space8),
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = sakhiSecondaryLabel(),
                modifier = Modifier.size(36.dp),
            )
        },
    )
}

private enum class MediaTab(@StringRes val labelRes: Int) {
    Media(R.string.chat_media_tab_media),
    Links(R.string.chat_media_tab_links),
    Docs(R.string.chat_media_tab_docs),
}

private data class LinkItem(
    val messageId: String,
    val url: String,
    val host: String,
    val timestamp: String,
    val snippet: String,
)

@Stable
internal class StarredMessagesStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    var ids by mutableStateOf(prefs.getStringSet(KEY, emptySet())?.toSet().orEmpty())
        private set

    fun toggle(id: String) {
        ids = if (ids.contains(id)) ids - id else ids + id
        prefs.edit().putStringSet(KEY, ids).apply()
    }

    fun isStarred(id: String): Boolean = ids.contains(id)

    private companion object {
        const val PREFS_NAME = "sakhi_ai_starred"
        const val KEY = "starred_message_ids"
    }
}

@Composable
internal fun rememberStarredMessagesStore(): StarredMessagesStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { StarredMessagesStore(context) }
}

private fun extractLinkItems(messages: List<ConversationMessage>): List<LinkItem> {
    return messages.flatMap { message ->
        Patterns.WEB_URL.matcher(message.content).run {
            val items = mutableListOf<LinkItem>()
            while (find()) {
                val url = group().orEmpty()
                val host = runCatching { Uri.parse(url).host }.getOrNull().orEmpty().ifBlank { url }
                items += LinkItem(
                    messageId = message.id,
                    url = url,
                    host = host,
                    timestamp = message.timestamp,
                    snippet = message.content.take(80),
                )
            }
            items
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

@StringRes
private fun titleForCardTypeRes(cardType: String): Int = when (cardType) {
    AICardType.CYCLE_STATUS -> R.string.chat_card_cycle_status
    AICardType.PERIOD_PREDICTION -> R.string.chat_card_period_prediction
    AICardType.PHASE_INFO -> R.string.chat_card_phase_info
    AICardType.OVULATION_WINDOW -> R.string.chat_card_ovulation_window
    AICardType.FLOW -> R.string.chat_card_flow
    AICardType.CRAMP_RELIEF -> R.string.chat_card_cramp_relief
    AICardType.SYMPTOM_SUMMARY -> R.string.chat_card_symptom_summary
    AICardType.MOOD -> R.string.chat_card_mood
    AICardType.DOCTOR_VISIT -> R.string.chat_card_doctor_visit
    AICardType.SAFETY -> R.string.chat_card_safety
    AICardType.TIP -> R.string.chat_card_tip
    AICardType.AFFIRMATION -> R.string.chat_card_affirmation
    AICardType.HYDRATION -> R.string.chat_card_hydration
    AICardType.SLEEP -> R.string.chat_card_sleep
    AICardType.STRESS_RELIEF -> R.string.chat_card_stress_relief
    AICardType.MEDICATION -> R.string.chat_card_medication
    AICardType.CHECK_IN -> R.string.chat_card_check_in
    AICardType.TEMPERATURE -> R.string.chat_card_temperature
    AICardType.CALENDAR_PREVIEW -> R.string.chat_card_calendar_preview
    AICardType.PMS -> R.string.chat_card_pms
    AICardType.CARE_ALERT -> R.string.chat_card_care_alert
    AICardType.PLACES -> R.string.chat_card_nearby_places
    else -> R.string.chat_card_default
}

private fun iconForCardType(cardType: String): ImageVector = when (cardType) {
    AICardType.CYCLE_STATUS,
    AICardType.PERIOD_PREDICTION,
    AICardType.CALENDAR_PREVIEW -> Icons.Filled.ViewCarousel
    AICardType.SYMPTOM_SUMMARY,
    AICardType.DOCTOR_VISIT,
    AICardType.CHECK_IN -> Icons.Filled.Description
    AICardType.PLACES -> Icons.Filled.Link
    else -> Icons.Filled.AutoAwesome
}
