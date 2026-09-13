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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
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
import team.sakhi.staywithme.StayWithMeWalkRecord

/**
 * The picture at the top of a connection: the two of them, under a shelter.
 *
 * Two faces side by side with a soft arc over them, and three small lights above it. It
 * says someone is with you, and nothing about who they are to you. An earlier design had
 * two houses joined by a string of lights with a heart lantern, which reads as a couple
 * living apart; this is Care Mode, and for most women the person she trusts is her mother,
 * her sister or a friend.
 */
@Composable
internal fun CareConnectionArt(
    /** Her own face, from her own id. */
    selfAvatarIndex: Int,
    /** The other person's face, as the server keeps it. */
    otherAvatarIndex: Int,
    modifier: Modifier = Modifier,
) {
    val pink = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.fillMaxWidth().height(190.dp)) {
        // The shelter: a wide, shallow arc over both of them, with three lights above it.
        Canvas(modifier = Modifier.fillMaxWidth().height(190.dp)) {
            val w = size.width
            val h = size.height
            val arc = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.20f, h * 0.52f)
                quadraticBezierTo(w * 0.5f, h * 0.12f, w * 0.80f, h * 0.52f)
            }
            drawPath(arc, color = pink.copy(alpha = 0.28f), style = Stroke(width = 4f, cap = StrokeCap.Round))
            listOf(0.34f, 0.5f, 0.66f).forEachIndexed { index, t ->
                val y = if (index == 1) h * 0.14f else h * 0.22f
                drawCircle(color = pink.copy(alpha = 0.5f), radius = h * 0.016f, center = Offset(w * t, y))
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = SakhiSpacing.space4),
            horizontalArrangement = Arrangement.spacedBy((-14).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CareFace(avatarIndex = selfAvatarIndex, size = 72)
            CareFace(avatarIndex = otherAvatarIndex, size = 72)
        }
    }
}

/** One face in the picture: the stand-in artwork, on a soft disc, with a white collar. */
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
            // 80% of the artwork's own fill, so the face sits inside its circle rather than
            // pressing on the edge (Karan, 2026-09-13).
            modifier = Modifier.fillMaxSize().scale(CareAvatars.scale(avatarIndex) * 0.8f),
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
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.care_moments_empty_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = sakhiLabel(),
                modifier = Modifier.padding(top = SakhiSpacing.space4),
            )
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = sakhiSecondaryLabel(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space1),
            )
        }
        return
    }
    val shown = limit?.let { moments.take(it) } ?: moments
    Column(modifier = Modifier.fillMaxWidth()) {
        shown.forEachIndexed { index, moment ->
            TimelineRow(
                moment = moment,
                time = dayLabel(moment.at),
                isFirst = index == 0,
                isLast = index == shown.lastIndex,
            )
        }
        if (onShowAll != null && limit != null && moments.size > limit) {
            SakhiListDivider(modifier = Modifier.padding(top = SakhiSpacing.space2))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowAll)
                    .padding(vertical = SakhiSpacing.space4),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.care_moments_show_all),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = sakhiLabel(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(SakhiSpacing.space2))
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = sakhiSecondaryLabel(),
                )
            }
            if (moment.detail.isNotBlank()) {
                Text(
                    text = moment.detail,
                    style = MaterialTheme.typography.bodySmall,
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
            .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space4),
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
                modifier = Modifier.size(17.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = sakhiLabel(),
            )
            subtitle?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = sakhiSecondaryLabel())
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = sakhiSecondaryLabel(),
                modifier = Modifier.size(20.dp),
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
        shape = RoundedCornerShape(SakhiRadius.xl),
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
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = sakhiSecondaryLabel(),
            modifier = Modifier.weight(1f),
        )
        if (actionText != null && onAction != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
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
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = sakhiDeepRose(),
        )
    }
}

/** Where the Stay With Me card is: nothing yet, a round trip, an ask out, or a live walk. */
internal enum class CareStayState { Idle, Working, Waiting, Live }

/**
 * The Stay With Me block: a real map, what is happening, and the one button.
 *
 * The map is live the moment a walk is: her dot, the way she has come, the way ahead. Before
 * that it shows where this phone is, so it is a map of somewhere real rather than a drawing.
 * The whole map is a tap target that opens it full screen, and the small button in its corner
 * says so, since a map in a list does not otherwise look like it opens.
 *
 * [mapModifier] is the caller's, so it can carry the shared-bounds link to the full screen map
 * the card grows into.
 */
@Composable
internal fun CareStayCard(
    state: CareStayState,
    title: String,
    line: String,
    idleLabel: String,
    onButton: () -> Unit,
    onExpand: () -> Unit,
    mapModifier: Modifier = Modifier,
    map: @Composable () -> Unit,
) {
    CareSection {
        CareSectionTitle(text = title)
        Box(
            modifier = Modifier
                .padding(horizontal = SakhiSpacing.space4)
                .then(mapModifier)
                .fillMaxWidth()
                .height(184.dp)
                .clip(RoundedCornerShape(SakhiRadius.lg))
                .background(sakhiLightPink()),
        ) {
            map()
            // Over the map, so a tap anywhere on it opens it, and the map itself never takes
            // the drag that should scroll the page.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onExpand),
            )
            if (state == CareStayState.Live) {
                LiveBadge(modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
            }
            Surface(
                shape = CircleShape,
                color = sakhiSystemBackground(),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(34.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().clickable(onClick = onExpand),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.care_stay_expand),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        androidx.compose.animation.AnimatedContent(
            targetState = line,
            label = "careStayLine",
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = SakhiSpacing.space5, end = SakhiSpacing.space5, top = SakhiSpacing.space3),
            )
        }
        CareStayButton(
            state = state,
            idleLabel = idleLabel,
            onClick = if (state == CareStayState.Live) onExpand else onButton,
            modifier = Modifier.padding(
                start = SakhiSpacing.space4,
                end = SakhiSpacing.space4,
                top = SakhiSpacing.space4,
                bottom = SakhiSpacing.space5,
            ),
        )
    }
}

/**
 * The card's one button, through each thing it can be.
 *
 * Asking: a spinner in the pink, so the press is answered at once. Waiting: the pink goes soft
 * and a small dot breathes beside "Waiting for her to start", because nothing is wrong and
 * nothing more is needed from them. Live: it opens the map.
 */
@Composable
internal fun CareStayButton(
    state: CareStayState,
    idleLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pink = MaterialTheme.colorScheme.primary
    val waiting = state == CareStayState.Waiting
    val fill by androidx.compose.animation.animateColorAsState(
        targetValue = if (waiting) pink.copy(alpha = 0.10f) else pink,
        label = "careStayButtonFill",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(SakhiRadius.full))
            .background(fill)
            .clickable(
                enabled = state == CareStayState.Idle || state == CareStayState.Live,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = state,
            label = "careStayButton",
        ) { shown ->
            when (shown) {
                CareStayState.Working -> androidx.compose.material3.CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(22.dp),
                )
                CareStayState.Waiting -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BreathingDot(color = sakhiDeepRose())
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.care_stay_waiting_button),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = sakhiDeepRose(),
                    )
                }
                CareStayState.Live -> Text(
                    text = androidx.compose.ui.res.stringResource(R.string.care_stay_open_live),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                CareStayState.Idle -> Text(
                    text = idleLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
    }
}

/** A small dot that fades in and out, for "waiting" and for "live". */
@Composable
private fun BreathingDot(color: Color, size: Int = 8) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "breathingDot")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "breathingDotAlpha",
    )
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color.copy(alpha = alpha), CircleShape),
    )
}

/** "LIVE", with a breathing dot, on the map while a walk is on. */
@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(sakhiSystemBackground(), RoundedCornerShape(SakhiRadius.full))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BreathingDot(color = MaterialTheme.colorScheme.primary, size = 7)
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.care_stay_live_badge),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
