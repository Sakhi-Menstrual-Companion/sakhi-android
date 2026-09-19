package team.sakhi.android.feature.care

import kotlinx.datetime.toInstant
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.Brush
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Clock
import androidx.compose.ui.res.stringResource
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.android.designsystem.sakhiDeepRose
import team.sakhi.android.designsystem.sakhiGroupedBackground
import team.sakhi.android.designsystem.sakhiLightPink
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.ui.SakhiFooter
import team.sakhi.android.ui.SakhiNavBar
import team.sakhi.staywithme.StayWithMeWalkRecord

/**
 * The two of them, at the top of their page. Port of iOS's `CareConnectionArt`.
 *
 * It used to be a scene: a wide arc over the faces with three small lights above it, meant
 * to read as a shelter, and then a soft halo behind them. Both were decoration nobody could
 * name (Karan, 2026-09-14). What is left is the part that means something: the two faces,
 * on the page, with the Sakhi mark where they meet on a white disc, like a small seal on the
 * pair rather than a third face. Who they are to each other is deliberately not drawn: for
 * most women the person she trusts is her mother, her sister or a friend.
 */
@Composable
internal fun CareConnectionArt(
    /** Her own face, from her own id. */
    selfAvatarIndex: Int,
    /** The other person's face, as the server keeps it. */
    otherAvatarIndex: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(100.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-16).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CareFace(avatarIndex = selfAvatarIndex, size = 76)
                CareFace(avatarIndex = otherAvatarIndex, size = 76)
            }
            // iOS: a 24pt mark, 2pt white collar, sitting 5pt below where the faces meet.
            Image(
                painter = painterResource(team.sakhi.android.ui.R.drawable.sakhi_app_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 5.dp)
                    .background(sakhiSystemBackground(), CircleShape)
                    .padding(2.dp)
                    .size(24.dp)
                    .clip(CircleShape),
            )
        }
    }
}

/** One face in the picture: the stand-in artwork on a soft disc, with a white collar. */
@Composable
private fun CareFace(avatarIndex: Int, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(sakhiSystemBackground(), CircleShape)
            .padding(3.dp)
            .background(sakhiLightPink(), CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(CareAvatars.drawable(avatarIndex)),
            contentDescription = null,
            // 64% of the artwork's own fill: 80% on 2026-09-13, then another fifth off on
            // 2026-09-14. The circle keeps its size; only the face inside it gets smaller.
            modifier = Modifier.fillMaxSize().scale(CareAvatars.scale(avatarIndex) * 0.64f),
        )
    }
}

/**
 * One real thing that happened between them, for the list under the picture.
 *
 * Real only: a walk this person stayed for, or a day they logged for her. Nothing the app
 * invents, and nothing like a hug button, which would put a gesture in the list beside
 * things that actually took someone's time.
 */
internal data class CareMoment(
    val at: Instant,
    val title: String,
    val detail: String,
    val icon: ImageVector,
)

/**
 * The walks and the logged days, newest first, in the words each side should read.
 *
 * Strings are resolved here rather than in the screen so the list itself stays a plain
 * value the screen can hold, remember and hand around.
 */
internal fun careMoments(
    context: android.content.Context,
    walks: List<StayWithMeWalkRecord>,
    loggedDays: List<Pair<Instant, String>>,
    isPartnerRole: Boolean,
    otherName: String,
    /**
     * Everything else the other person did, from this phone's inbox: rows they caused and
     * that were addressed here (asked to stay, asked her to log, a message, joining). Only
     * types in [CARE_ACTION_TYPES]; logged days come from the logs themselves, not from here,
     * so a day is never listed twice.
     */
    actions: List<team.sakhi.notifications.InAppNotification> = emptyList(),
): List<CareMoment> {
    val walkTitle = context.getString(
        if (isPartnerRole) R.string.care_moment_walk_partner else R.string.care_moment_walk_owner,
    )
    val logTitle = context.getString(
        if (isPartnerRole) R.string.care_moment_log_partner else R.string.care_moment_log_owner,
    )
    val fromWalks = walks.map { walk ->
        val detail = when {
            walk.wasLate && isPartnerRole -> context.getString(R.string.care_moment_walk_late_partner)
            walk.wasLate -> context.getString(R.string.care_moment_walk_late_owner, otherName)
            !walk.arrived -> context.getString(R.string.care_moment_walk_stopped)
            isPartnerRole -> context.resources.getQuantityString(R.plurals.care_moment_walk_minutes_partner, walk.minutes, walk.minutes)
            else -> context.resources.getQuantityString(R.plurals.care_moment_walk_minutes_owner, walk.minutes, walk.minutes)
        }
        CareMoment(
            at = walk.endedAt,
            title = walkTitle,
            detail = detail,
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
        )
    }
    val fromLogs = loggedDays.map { (at, day) ->
        CareMoment(
            at = at,
            title = logTitle,
            detail = context.getString(R.string.care_moment_log_for, day),
            icon = Icons.Filled.EditCalendar,
        )
    }
    val fromActions = if (isPartnerRole) emptyList() else actions.mapNotNull { row ->
        val (title, icon) = when (row.type) {
            "stay_with_me_ask" -> context.getString(R.string.care_moment_ask_owner) to Icons.Filled.Favorite
            "log_request" -> context.getString(R.string.care_moment_log_request_owner) to Icons.Filled.EditCalendar
            "care_message" -> context.getString(R.string.care_moment_message_owner) to Icons.Filled.Favorite
            "invitation_accepted" -> context.getString(R.string.care_moment_joined_owner, otherName) to Icons.Filled.Favorite
            else -> return@mapNotNull null
        }
        CareMoment(at = row.createdAt, title = title, detail = "", icon = icon)
    }
    return (fromWalks + fromLogs + fromActions).sortedByDescending { it.at }
}

/** The inbox rows that are something the other person did for her. */
internal val CARE_ACTION_TYPES = setOf("stay_with_me_ask", "log_request", "care_message", "invitation_accepted")

/**
 * What happened between them, as a timeline: a thin rail down the left with a dot for each
 * moment, and the words beside it. Nothing here opens anything, so nothing looks like a
 * button: the icon discs and chevrons the rows used to wear made every one of them read as
 * a link.
 *
 * The card shows the newest [limit]; a quiet "Show all" under a hairline opens every one of
 * them as their own screen ([onShowAll]). That screen passes no limit.
 */
@Composable
internal fun CareMoments(
    moments: List<CareMoment>,
    emptyText: String,
    dayLabel: (Instant) -> String,
    limit: Int? = MOMENTS_ON_CARD,
    onShowAll: (() -> Unit)? = null,
) {
    if (moments.isEmpty()) {
        // Nothing yet: the card keeps its size and says so calmly, rather than shrinking to a
        // one-line note that reads like something failed to load.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(56.dp).background(sakhiLightPink(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            // iOS: 16 bold label over a 13 secondary line, 16 and 4 below the disc.
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.care_moments_empty_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
                modifier = Modifier.padding(top = SakhiSpacing.space4),
            )
            Text(
                text = emptyText,
                fontSize = 13.sp,
                color = sakhiSecondaryLabel(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space1),
            )
        }
        return
    }
    val shown = limit?.let { moments.take(it) } ?: moments
    // The card (a limit) closes itself: 4 under the list when "Show all" follows, 12 when it
    // does not. The full page (no limit) is the list alone.
    val hasMore = limit != null && moments.size > limit
    Column(modifier = Modifier.fillMaxWidth()) {
        shown.forEachIndexed { index, moment ->
            TimelineRow(
                moment = moment,
                time = dayLabel(moment.at),
                isFirst = index == 0,
                isLast = index == shown.lastIndex,
            )
        }
        if (limit != null) {
            Spacer(Modifier.height(if (hasMore) 4.dp else 12.dp))
        }
        if (onShowAll != null && hasMore) {
            SakhiListDivider()
            val showAllLabel = androidx.compose.ui.res.stringResource(R.string.care_moments_show_all)
            val showAllA11y = androidx.compose.ui.res.stringResource(R.string.care_moments_show_all_a11y, moments.size)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowAll)
                    .semantics { contentDescription = showAllA11y }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = showAllLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** How many moments the card shows before "Show all". */
private const val MOMENTS_ON_CARD = 3

/** A day her person logged for her, as a moment: noon that day, and "12 Sep". */
internal fun careLoggedDay(date: kotlinx.datetime.LocalDate): Pair<Instant, String> {
    val at = kotlinx.datetime.LocalDateTime(date, kotlinx.datetime.LocalTime(12, 0))
        .toInstant(TimeZone.currentSystemDefault())
    val label = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
    return at to label
}

/** One moment on the rail: the dot, and beside it what happened, how, and when. */
@Composable
private fun TimelineRow(moment: CareMoment, time: String, isFirst: Boolean, isLast: Boolean) {
    val pink = MaterialTheme.colorScheme.primary
    val rail = pink.copy(alpha = 0.18f)
    val collar = sakhiSystemBackground()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .padding(horizontal = SakhiSpacing.space5),
    ) {
        Canvas(modifier = Modifier.width(14.dp).fillMaxHeight()) {
            val x = size.width / 2
            val dotY = 19.dp.toPx()
            val stroke = 2.dp.toPx()
            if (!isFirst) drawLine(rail, Offset(x, 0f), Offset(x, dotY), strokeWidth = stroke)
            if (!isLast) drawLine(rail, Offset(x, dotY), Offset(x, size.height), strokeWidth = stroke)
            drawCircle(collar, radius = 6.5.dp.toPx(), center = Offset(x, dotY))
            drawCircle(pink, radius = 4.5.dp.toPx(), center = Offset(x, dotY))
        }
        Spacer(Modifier.width(SakhiSpacing.space3))
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = moment.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = sakhiLabel(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(SakhiSpacing.space2))
                Text(
                    text = time,
                    fontSize = 12.sp,
                    color = sakhiSecondaryLabel(),
                )
            }
            if (moment.detail.isNotBlank()) {
                Text(
                    text = moment.detail,
                    fontSize = 13.sp,
                    color = sakhiSecondaryLabel(),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** "What she shares with you" and the rest: one row, a glyph, a chevron when it opens. */
@Composable
internal fun CareLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = SakhiSpacing.space5, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(sakhiLightPink(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
            )
            subtitle?.let {
                Text(text = it, fontSize = 13.sp, color = sakhiSecondaryLabel())
            }
        }
        // iOS draws the chevron only when the row opens something; a row that only tells her
        // something (what she shares with him) has none and is not a button.
        if (onClick != null) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = sakhiTertiaryLabel(),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** "Today", "Yesterday", a weekday inside the last week, then a plain date. */
internal fun momentDayLabel(at: Instant, context: android.content.Context): String {
    val zone = TimeZone.currentSystemDefault()
    val day = at.toLocalDateTime(zone).date
    val today = Clock.System.now().toLocalDateTime(zone).date
    val days = (today.toEpochDays() - day.toEpochDays()).toLong()
    return when {
        // Today shows the time instead: three walks home in one evening all saying "Today"
        // tells the reader nothing about which was which.
        days <= 0L -> java.time.LocalTime.of(at.toLocalDateTime(zone).hour, at.toLocalDateTime(zone).minute)
            .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
        days == 1L -> context.getString(R.string.care_moment_yesterday)
        days < 7L -> java.time.LocalDate.of(day.year, day.monthNumber, day.dayOfMonth)
            .format(java.time.format.DateTimeFormatter.ofPattern("EEE"))
        else -> java.time.LocalDate.of(day.year, day.monthNumber, day.dayOfMonth)
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
    }
}

/**
 * One white block on the pink page.
 *
 * The screen reads as a few separate things she can act on -- what this person did, the
 * walk she can start, what they can see -- rather than one long list. Each of them sits on
 * its own white card so the eye can find where one ends and the next begins, which is what
 * every checkout and order screen she already uses does.
 */
@Composable
internal fun CareSection(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    // No outline and no shadow. Sakhi draws no shadows anywhere (Karan, 2026-09-13), and a
    // stroke round every card boxed each section in. White on the pale pink page is the edge.
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space5),
        // iOS `careCard`: continuous 24.
        shape = RoundedCornerShape(24.dp),
        color = sakhiSystemBackground(),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** The small line at the top of a card: a name for the block, and an optional action. */
@Composable
internal fun CareSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SakhiSpacing.space5,
                end = SakhiSpacing.space5,
                top = SakhiSpacing.space5,
                bottom = SakhiSpacing.space2,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            // Sentence case, not capitals (Karan, 2026-09-13), so no wide caps tracking.
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiSecondaryLabel(),
            modifier = Modifier.weight(1f),
        )
        if (actionText != null && onAction != null) {
            Text(
                text = actionText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }
}

/**
 * "Taking care since 12 Sep, 2025", as a small pill rather than a grey sentence.
 *
 * It is the one fact under her name, and a line of secondary text under a bold heading
 * reads as a caption nobody looks at. In a pill it reads as a badge the two of them earned.
 *
 * A deep rose on a faint pink tint: the brand pink at full strength was a third bright pink
 * thing beside the button, and grey sat on the pink page like something switched off.
 */
@Composable
internal fun CareSinceChip(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(SakhiRadius.full))
            .padding(horizontal = SakhiSpacing.space3, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = sakhiDeepRose(),
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = sakhiDeepRose(),
        )
    }
}

/**
 * A finished action, on its own page: a pink disc with a tick, what happened, and Done.
 * Port of iOS's `CareActionCompletionView` (plain symbol hero), used for "Request cancelled".
 * The way out is the X on the left, like every other Sakhi sheet.
 */
@Composable
internal fun CareActionCompletion(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onClose: () -> Unit,
) {
    val pageTop = sakhiLightPink()
    val pageBottom = androidx.compose.ui.graphics.lerp(sakhiLightPink(), sakhiSystemBackground(), 0.82f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to pageTop,
                    0.30f to androidx.compose.ui.graphics.lerp(pageTop, pageBottom, 0.72f),
                    1f to pageBottom,
                ),
            ),
    ) {
        SakhiNavBar(onClose = onClose)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space8, start = SakhiSpacing.space6, end = SakhiSpacing.space6),
            )
            Text(
                text = message,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = sakhiSecondaryLabel(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .padding(top = SakhiSpacing.space3)
                    .padding(horizontal = SakhiSpacing.space8)
                    .widthIn(max = 280.dp),
            )
        }
        SakhiFooter(
            primaryLabel = primaryLabel,
            onPrimaryClick = onPrimary,
            showSecondarySlot = false,
        )
    }
}

/**
 * The footer's button while its action is running: the same pink capsule with a spinner where
 * the label was, as iOS's `isLoading`. [SakhiFooter] has no loading state of its own.
 */
@Composable
internal fun CareLoadingButton() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = SakhiSpacing.space1)
            .clip(RoundedCornerShape(SakhiRadius.full))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(22.dp),
        )
    }
}
