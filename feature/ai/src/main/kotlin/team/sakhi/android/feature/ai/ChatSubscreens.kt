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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.BackButton
import team.sakhi.android.feature.reports.ReportDateRangePreset
import team.sakhi.android.ui.EmptyState
import team.sakhi.android.ui.GlassCard
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.android.ui.SakhiTextField
import team.sakhi.models.AICardType
import team.sakhi.models.ConversationMessage

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
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenStarred: () -> Unit,
) {
    val mediaCount = remember(messages) { messages.count { it.isAssistant && it.cardType != AICardType.GENERAL } }
    val linkCount = remember(messages) { extractLinkItems(messages).size }
    val starredCount = remember(messages, starredStore.ids) { messages.count { starredStore.isStarred(it.id) } }
    val messageCount = remember(messages) { messages.count { it.sessionId != "welcome" } }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatSubscreenHeader(title = "Sakhi AI", onBack = onBack)
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
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Text(
                        text = "Sakhi AI",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "Your personal health companion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "stats") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                ) {
                    ChatStatTile(value = messageCount.toString(), label = "Messages", modifier = Modifier.weight(1f))
                    ChatStatTile(value = mediaCount.toString(), label = "Cards", modifier = Modifier.weight(1f))
                    ChatStatTile(value = starredCount.toString(), label = "Starred", modifier = Modifier.weight(1f))
                }
            }

            item(key = "search") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    ChatActionRow(
                        icon = Icons.Filled.Search,
                        title = "Search in chat",
                        isLast = true,
                        onClick = onOpenSearch,
                    )
                }
            }

            item(key = "content") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    ChatActionRow(
                        icon = Icons.Filled.ViewCarousel,
                        title = "Media and cards",
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
                        title = "Starred messages",
                        badge = starredCount.takeIf { it > 0 }?.toString(),
                        isLast = true,
                        onClick = onOpenStarred,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!session.isGenerating) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.chat_report_card_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@GlassCard
        }

        Text(
            text = "How far back should I go?",
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .semantics {
                            this.selected = isSelected
                            role = Role.RadioButton
                            stateDescription = if (isSelected) "Selected" else "Not selected"
                        }
                        .clickable { onSelectRange(preset) }
                        .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = preset.shortLabel,
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

        PrimaryButton(
            text = "Generate Report",
            onClick = onGenerate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SakhiSpacing.space4),
        )
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
        ChatSubscreenHeader(title = "Search", onBack = onBack)
        SakhiTextField(
            value = query.text,
            onValueChange = { query = query.copy(text = it) },
            placeholder = "Search in conversation",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space3),
        )

        when {
            trimmedQuery.isEmpty() -> {
                EmptyState(
                    title = "Search messages",
                    subtitle = "Search anything you've said or Sakhi has shared",
                    modifier = Modifier.padding(top = SakhiSpacing.space8),
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                    },
                )
            }
            results.isEmpty() -> {
                EmptyState(
                    title = "No results",
                    subtitle = "Nothing found for '$trimmedQuery'",
                    modifier = Modifier.padding(top = SakhiSpacing.space8),
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        SearchResultRow(message = message)
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
        ChatSubscreenHeader(title = "Media, links and docs", onBack = onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space2),
        ) {
            MediaTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            this.selected = isSelected
                            role = Role.Button
                            stateDescription = if (isSelected) "Selected" else "Not selected"
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
                        title = "No health cards yet",
                        subtitle = "Ask Sakhi about your cycle, mood, or symptoms to see health cards here",
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
                        title = "No links",
                        subtitle = "Links shared in your conversation will appear here",
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
                        title = "No documents",
                        subtitle = "Health summaries and reports shared by Sakhi will appear here",
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
                            MediaMessageRow(message = message)
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
        ChatSubscreenHeader(title = "Starred", onBack = onBack)

        if (starredMessages.isEmpty()) {
            ChatEmptyState(
                icon = Icons.Filled.Star,
                title = "No starred messages",
                subtitle = "Long press any message in the chat to star it. It will appear here.",
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
                        trailing = {
                            IconButton(onClick = { starredStore.toggle(message.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "Unstar",
                                    tint = MaterialTheme.colorScheme.primary,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = SakhiSpacing.space1),
            )
        }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatActionRow(
    icon: ImageVector,
    title: String,
    badge: String? = null,
    isLast: Boolean,
    onClick: () -> Unit,
) {
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (!badge.isNullOrBlank()) {
            Text(
                text = badge,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(14.dp)
                .width(14.dp),
        )
    }
}

@Composable
private fun SearchResultRow(
    message: ConversationMessage,
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
                    .background(
                        if (message.isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (message.isUser) Icons.Filled.Person else Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (message.isUser) "You" else "Sakhi",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formattedTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
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
                    text = titleForCardType(message.cardType),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formattedTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formattedTime(item.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
        },
    )
}

private enum class MediaTab(val label: String) {
    Media("Media"),
    Links("Links"),
    Docs("Docs"),
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

private fun titleForCardType(cardType: String): String = when (cardType) {
    AICardType.CYCLE_STATUS -> "Cycle status"
    AICardType.PERIOD_PREDICTION -> "Period prediction"
    AICardType.PHASE_INFO -> "Phase info"
    AICardType.OVULATION_WINDOW -> "Ovulation window"
    AICardType.FLOW -> "Flow"
    AICardType.CRAMP_RELIEF -> "Cramp relief"
    AICardType.SYMPTOM_SUMMARY -> "Symptom summary"
    AICardType.MOOD -> "Mood"
    AICardType.DOCTOR_VISIT -> "Doctor visit"
    AICardType.SAFETY -> "Safety"
    AICardType.TIP -> "Tip"
    AICardType.AFFIRMATION -> "Affirmation"
    AICardType.HYDRATION -> "Hydration"
    AICardType.SLEEP -> "Sleep"
    AICardType.STRESS_RELIEF -> "Stress relief"
    AICardType.MEDICATION -> "Medication"
    AICardType.CHECK_IN -> "Check-in"
    AICardType.TEMPERATURE -> "Temperature"
    AICardType.CALENDAR_PREVIEW -> "Calendar preview"
    AICardType.PMS -> "PMS"
    AICardType.CARE_ALERT -> "Care alert"
    AICardType.PLACES -> "Nearby places"
    else -> "Sakhi card"
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
