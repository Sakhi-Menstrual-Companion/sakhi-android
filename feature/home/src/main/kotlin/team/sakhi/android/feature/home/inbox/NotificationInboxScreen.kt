package team.sakhi.android.feature.home.inbox

import team.sakhi.notifications.NotificationRouting
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.AppleSystemColors
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.rememberSakhiFlingBehavior
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiSystemGray5
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.feature.home.R
import team.sakhi.android.ui.EmptyState
import team.sakhi.android.ui.LoadingShimmer
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.android.ui.SakhiNavBar
import team.sakhi.android.ui.SheetSurface
import team.sakhi.android.ui.sakhiPressFeedback
import team.sakhi.notifications.InAppNotification
import team.sakhi.notifications.NotificationSection
import team.sakhi.notifications.NotificationSectionKey
import team.sakhi.notifications.SakhiNotification

/**
 * The in-app inbox behind Home's bell.
 *
 * Laid out after the design reference: day headings (Today, Yesterday, then dates), and a
 * row per notification with a soft round type icon, a one-line title, a short line under
 * it, the time on the right and an unread dot below it. Rows sit in rounded cards, one per
 * day, the way every other Sakhi list does, and are separated by the shared hairline.
 *
 * A row that asks her something (a partner asking to log for her) carries its answer
 * inline, "Allow" and "Not now", rather than sending her somewhere else to find it.
 *
 * Swipe a row left to delete it. "Mark all as read" floats at the bottom right while
 * anything is unread.
 */
@Composable
fun NotificationInboxScreen(
    onClose: () -> Unit,
    /** Called with a `sakhi://` link for rows that lead somewhere; the host routes it. */
    onOpenLink: (String) -> Unit,
    viewModel: NotificationInboxViewModel = koinViewModel(),
) {
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val answers by viewModel.answers.collectAsStateWithLifecycle()
    val sections = remember(inbox.items) { inbox.sections }

    SheetSurface {
        SakhiNavBar(
            title = stringResource(R.string.inbox_title),
            onClose = onClose,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                sections.isNotEmpty() -> InboxList(
                    sections = sections,
                    answers = answers,
                    onOpen = { item -> viewModel.open(item)?.let(onOpenLink) },
                    onAnswer = viewModel::answer,
                    onDelete = viewModel::delete,
                )
                // Only a first load, never a background refresh, shows the placeholder.
                viewModel.canLoad && !inbox.hasLoaded && !inbox.lastRefreshFailed -> InboxSkeleton()
                inbox.lastRefreshFailed -> InboxMessage(
                    title = stringResource(R.string.inbox_error_title),
                    body = stringResource(R.string.inbox_error_body),
                    onRetry = viewModel::retry,
                )
                else -> InboxMessage(
                    title = stringResource(R.string.inbox_empty_title),
                    body = stringResource(R.string.inbox_empty_body),
                )
            }

            MarkAllReadButton(
                visible = inbox.unreadCount > 0,
                onClick = viewModel::markAllRead,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = SakhiSpacing.space5, bottom = SakhiSpacing.space5),
            )
        }
    }
}

// ── List ─────────────────────────────────────────────────────────────────────

@Composable
private fun InboxList(
    sections: List<NotificationSection>,
    answers: Map<String, RequestAnswerState>,
    onOpen: (InAppNotification) -> Unit,
    onAnswer: (InAppNotification, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SakhiSpacing.space4,
            end = SakhiSpacing.space4,
            // Room for the floating button, so the last row can scroll clear of it.
            bottom = FloatingButtonClearance,
        ),
        flingBehavior = rememberSakhiFlingBehavior(),
    ) {
        sections.forEach { section ->
            sectionItems(section, answers, onOpen, onAnswer, onDelete)
        }
    }
}

private fun LazyListScope.sectionItems(
    section: NotificationSection,
    answers: Map<String, RequestAnswerState>,
    onOpen: (InAppNotification) -> Unit,
    onAnswer: (InAppNotification, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    item(key = "header-${section.key}", contentType = "header") {
        Text(
            text = sectionTitle(section.key),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = sakhiSecondaryLabel(),
            modifier = Modifier
                .animateItem()
                .padding(start = SakhiSpacing.space1, top = SakhiSpacing.space5, bottom = SakhiSpacing.space2),
        )
    }
    val last = section.items.lastIndex
    itemsIndexed(section.items, key = { _, item -> item.id }, contentType = { _, _ -> "row" }) { index, item ->
        // Each row rounds only the corners it owns, so a day's rows read as one card while
        // each can still be swiped away on its own.
        val shape: Shape = when {
            last == 0 -> RoundedCornerShape(CardRadius)
            index == 0 -> RoundedCornerShape(topStart = CardRadius, topEnd = CardRadius)
            index == last -> RoundedCornerShape(bottomStart = CardRadius, bottomEnd = CardRadius)
            else -> RectangleShape
        }
        val swipe = rememberSwipeToDismissBoxState()
        SwipeToDismissBox(
            state = swipe,
            modifier = Modifier.animateItem().clip(shape),
            enableDismissFromStartToEnd = false,
            onDismiss = { onDelete(item.id) },
            backgroundContent = { DeleteBackground() },
        ) {
            Column(modifier = Modifier.background(sakhiSystemBackground())) {
                NotificationRow(
                    item = item,
                    answerState = answers[item.id],
                    onClick = { onOpen(item) },
                    onAnswer = { approved -> onAnswer(item, approved) },
                )
                if (index != last) SakhiListDivider(startInset = RowTextInset)
            }
        }
    }
}

@Composable
private fun DeleteBackground() {
    val description = stringResource(R.string.inbox_delete_content_description)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleSystemColors.red)
            .padding(end = SakhiSpacing.space6),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(Icons.Rounded.Delete, contentDescription = description, tint = Color.White)
    }
}

// ── Row ──────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationRow(
    item: InAppNotification,
    answerState: RequestAnswerState?,
    onClick: () -> Unit,
    onAnswer: (Boolean) -> Unit,
) {
    val copy = rowCopy(item.kind)
    val look = rowLook(item.kind)
    val unread = !item.isRead
    // A request she has not answered is answered with its buttons; tapping the row
    // itself would only mark it read and retire them, so the row is not tappable then.
    val isActionable = unread && item.kind is SakhiNotification.LogRequestReceived && item.actorUserId != null
    // A chevron on every row that actually opens something, so a row reads as a door rather
    // than a notice (Karan, 2026-09-13). A row that leads nowhere does not get one.
    val opensSomething = !isActionable &&
        NotificationRouting.deepLink(notification = item.kind) != null
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val unreadDescription = stringResource(R.string.inbox_unread_content_description)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // iOS list rows grey while pressed rather than rippling.
            .background(if (pressed) sakhiSystemGray5() else Color.Transparent)
            .then(
                if (isActionable) {
                    Modifier
                } else {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                },
            )
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(IconSize)
                .background(look.tint.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(look.icon, contentDescription = null, tint = look.tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(SakhiSpacing.space3))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = copy.title,
                    fontSize = 15.sp,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    color = sakhiLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(SakhiSpacing.space2))
                Text(
                    text = formatTime(item.createdAtEpochMillis),
                    fontSize = 12.sp,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unread) MaterialTheme.colorScheme.primary else sakhiTertiaryLabel(),
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = copy.body,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = sakhiSecondaryLabel(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Spacer(modifier = Modifier.width(SakhiSpacing.space2))
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .semantics { contentDescription = unreadDescription },
                    )
                }
            }
            if (isActionable) {
                Spacer(modifier = Modifier.height(SakhiSpacing.space3))
                RequestActions(state = answerState, onAnswer = onAnswer)
            }
        }
        if (opensSomething) {
            Spacer(modifier = Modifier.width(SakhiSpacing.space2))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = sakhiTertiaryLabel(),
                modifier = Modifier.padding(top = 2.dp).size(20.dp),
            )
        }
    }
}

/** "Allow" (filled) and "Not now" (outlined), the reference's inline action pair. */
@Composable
private fun RequestActions(state: RequestAnswerState?, onAnswer: (Boolean) -> Unit) {
    val sending = state == RequestAnswerState.Sending
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            modifier = Modifier.alpha(if (sending) 0.5f else 1f),
        ) {
            PillButton(
                label = stringResource(R.string.inbox_log_request_allow),
                filled = true,
                enabled = !sending,
                onClick = { onAnswer(true) },
            )
            PillButton(
                label = stringResource(R.string.inbox_log_request_decline),
                filled = false,
                enabled = !sending,
                onClick = { onAnswer(false) },
            )
        }
        if (state == RequestAnswerState.Failed) {
            Spacer(modifier = Modifier.height(SakhiSpacing.space2))
            Text(
                text = stringResource(R.string.inbox_log_request_failed),
                fontSize = 12.sp,
                color = AppleSystemColors.red,
            )
        }
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val pink = MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .sakhiPressFeedback(interaction, pressedAlpha = 0.85f, pressedScale = 0.97f)
            .height(34.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(pink) else Modifier.border(1.dp, pink, CircleShape))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = SakhiSpacing.space5),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) Color.White else pink,
        )
    }
}

// ── Header menu, placeholder, empty and error ────────────────────────────────

/**
 * "Mark all as read", floating at the bottom right over the list (Karan, 2026-09-12). It
 * shows only while something is unread, and scales away once everything is.
 */
@Composable
private fun MarkAllReadButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
    ) {
        val interaction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .sakhiPressFeedback(interaction, pressedScale = 0.96f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .height(48.dp)
                .padding(horizontal = SakhiSpacing.space5),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.inbox_mark_all_read),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun InboxSkeleton() {
    Column(
        modifier = Modifier.padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        repeat(4) { LoadingShimmer(height = 68.dp) }
    }
}

/** The empty and error states, centred in the space under the header. */
@Composable
private fun InboxMessage(title: String, body: String, onRetry: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                title = title,
                subtitle = body,
                icon = {
                    Icon(
                        Icons.Rounded.NotificationsNone,
                        contentDescription = null,
                        tint = sakhiTertiaryLabel(),
                        modifier = Modifier.size(48.dp),
                    )
                },
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.inbox_retry), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ── Words, icons, times ──────────────────────────────────────────────────────

private data class RowCopy(val title: String, val body: String)

/**
 * The row's words, built here from its type and names. The server stores no wording (see
 * migration 056), so the same row reads correctly in her language and never has health
 * details written down as text anywhere.
 */
@Composable
private fun rowCopy(kind: SakhiNotification): RowCopy {
    val someone = stringResource(R.string.inbox_someone)
    fun name(raw: String) = raw.ifBlank { someone }
    return when (kind) {
        is SakhiNotification.PartnerLoggedPeriod -> RowCopy(
            stringResource(R.string.inbox_partner_logged_period_title, name(kind.partnerName)),
            stringResource(R.string.inbox_partner_logged_period_body),
        )
        is SakhiNotification.InvitationAccepted -> RowCopy(
            stringResource(R.string.inbox_invitation_accepted_title, name(kind.partnerName)),
            stringResource(R.string.inbox_invitation_accepted_body),
        )
        is SakhiNotification.InvitationReceived -> RowCopy(
            stringResource(R.string.inbox_invitation_received_title, name(kind.inviterName)),
            stringResource(R.string.inbox_invitation_received_body),
        )
        is SakhiNotification.LogRequestReceived -> RowCopy(
            stringResource(R.string.inbox_log_request_title, name(kind.partnerName)),
            stringResource(R.string.inbox_log_request_body),
        )
        is SakhiNotification.LogRequestResponse -> if (kind.approved) {
            RowCopy(
                stringResource(R.string.inbox_log_response_approved_title, name(kind.partnerName)),
                stringResource(R.string.inbox_log_response_approved_body),
            )
        } else {
            RowCopy(
                stringResource(R.string.inbox_log_response_declined_title, name(kind.partnerName)),
                stringResource(R.string.inbox_log_response_declined_body),
            )
        }
        is SakhiNotification.NewCareMessage -> RowCopy(
            stringResource(R.string.inbox_care_message_title, name(kind.senderName)),
            stringResource(R.string.inbox_care_message_body),
        )
        is SakhiNotification.Sos -> RowCopy(
            stringResource(R.string.inbox_sos_title),
            stringResource(R.string.inbox_sos_body),
        )
        is SakhiNotification.EmergencyRequestReceived -> RowCopy(
            stringResource(R.string.inbox_nearby_title),
            kind.distanceBucketMeters?.let { stringResource(R.string.inbox_nearby_body_distance, formatDistance(it)) }
                ?: stringResource(R.string.inbox_nearby_body),
        )
        is SakhiNotification.FeatureAvailable -> RowCopy(
            if (kind.feature.contains("ai", ignoreCase = true)) {
                stringResource(R.string.inbox_feature_ai_title)
            } else {
                stringResource(R.string.inbox_feature_title)
            },
            stringResource(R.string.inbox_feature_body),
        )
        is SakhiNotification.StayWithMeStarted -> RowCopy(
            stringResource(R.string.inbox_swm_started_title, name(kind.ownerName)),
            stringResource(R.string.inbox_swm_started_body),
        )
        is SakhiNotification.StayWithMeExtended -> RowCopy(
            stringResource(R.string.inbox_swm_extended_title, name(kind.ownerName)),
            stringResource(R.string.inbox_swm_extended_body),
        )
        is SakhiNotification.StayWithMeEnded -> if (kind.arrived) {
            RowCopy(
                stringResource(R.string.inbox_swm_arrived_title, name(kind.ownerName)),
                stringResource(R.string.inbox_swm_arrived_body),
            )
        } else {
            RowCopy(
                stringResource(R.string.inbox_swm_cancelled_title, name(kind.ownerName)),
                stringResource(R.string.inbox_swm_cancelled_body),
            )
        }
        is SakhiNotification.StayWithMeAsk -> RowCopy(
            stringResource(R.string.inbox_swm_ask_title, name(kind.askerName)),
            stringResource(R.string.inbox_swm_ask_body),
        )
        is SakhiNotification.StayWithMeDeclined -> RowCopy(
            stringResource(R.string.inbox_swm_declined_title, name(kind.ownerName)),
            stringResource(R.string.inbox_swm_declined_body),
        )
        is SakhiNotification.StayWithMeLate -> RowCopy(
            stringResource(R.string.inbox_swm_late_title, name(kind.ownerName)),
            stringResource(R.string.inbox_swm_late_body),
        )
        is SakhiNotification.PeriodReminder,
        SakhiNotification.LoggingReminder,
        SakhiNotification.Unknown,
        -> RowCopy(stringResource(R.string.inbox_generic_title), stringResource(R.string.inbox_generic_body))
    }
}

private data class RowLook(val icon: ImageVector, val tint: Color)

/** One soft, tinted circle per kind of thing, so the list can be read at a glance. */
@Composable
private fun rowLook(kind: SakhiNotification): RowLook {
    val pink = MaterialTheme.colorScheme.primary
    return when (kind) {
        is SakhiNotification.PartnerLoggedPeriod -> RowLook(Icons.Rounded.WaterDrop, pink)
        is SakhiNotification.InvitationAccepted -> RowLook(Icons.Rounded.People, AppleSystemColors.purple)
        is SakhiNotification.InvitationReceived -> RowLook(Icons.Rounded.Mail, AppleSystemColors.purple)
        is SakhiNotification.LogRequestReceived -> RowLook(Icons.Rounded.EditCalendar, AppleSystemColors.teal)
        is SakhiNotification.LogRequestResponse -> if (kind.approved) {
            RowLook(Icons.Rounded.CheckCircle, AppleSystemColors.green)
        } else {
            RowLook(Icons.Rounded.Info, AppleSystemColors.indigo)
        }
        is SakhiNotification.NewCareMessage -> RowLook(Icons.Rounded.ChatBubble, pink)
        is SakhiNotification.Sos -> RowLook(Icons.Rounded.Warning, AppleSystemColors.red)
        is SakhiNotification.EmergencyRequestReceived -> RowLook(Icons.Rounded.VolunteerActivism, AppleSystemColors.orange)
        is SakhiNotification.FeatureAvailable -> RowLook(Icons.Rounded.AutoAwesome, AppleSystemColors.indigo)
        is SakhiNotification.StayWithMeStarted,
        is SakhiNotification.StayWithMeExtended,
        -> RowLook(Icons.Rounded.Place, pink)
        is SakhiNotification.StayWithMeEnded -> RowLook(Icons.Rounded.Home, AppleSystemColors.green)
        is SakhiNotification.StayWithMeLate -> RowLook(Icons.Rounded.Warning, AppleSystemColors.red)
        else -> RowLook(Icons.Rounded.Notifications, pink)
    }
}

@Composable
private fun sectionTitle(key: NotificationSectionKey): String = when (key) {
    NotificationSectionKey.Today -> stringResource(R.string.inbox_today)
    NotificationSectionKey.Yesterday -> stringResource(R.string.inbox_yesterday)
    is NotificationSectionKey.Day -> remember(key) {
        // kotlinx's LocalDate prints as ISO yyyy-MM-dd, which java.time reads directly.
        val date = LocalDate.parse(key.date.toString())
        // The year only when it is not this one: "27 November", "27 November 2025".
        val pattern = if (date.year == LocalDate.now().year) "d MMMM" else "d MMMM yyyy"
        date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
}

/** Follows the phone's 12/24-hour setting, which a fixed pattern would ignore. */
@Composable
private fun formatTime(epochMillis: Long): String {
    val context = LocalContext.current
    return remember(epochMillis) { DateFormat.getTimeFormat(context).format(Date(epochMillis)) }
}

private fun formatDistance(meters: Int): String =
    if (meters >= 1000) {
        val km = meters / 1000.0
        if (km % 1.0 == 0.0) "${km.toInt()} km" else String.format(Locale.getDefault(), "%.1f km", km)
    } else {
        "$meters m"
    }

private val CardRadius = 16.dp
private val IconSize = 40.dp

/** Where a row's text starts: its padding, the icon, and the gap after it. */
private val RowTextInset = 16.dp + 40.dp + 12.dp


/** The floating button's height, its margin, and a little air above it. */
private val FloatingButtonClearance = 48.dp + 20.dp + 28.dp
