package team.sakhi.android.feature.care

import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
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
            modifier = Modifier.fillMaxSize().scale(CareAvatars.scale(avatarIndex)),
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
    return (fromWalks + fromLogs).sortedByDescending { it.at }
}

/**
 * The list itself: rows on the page, a hairline between them, and no card around any of it.
 * Cards inside cards inside a sheet is what this screen looked like before, and it read as
 * boxes rather than as one quiet page.
 */
@Composable
internal fun CareMoments(moments: List<CareMoment>, emptyText: String, dayLabel: (Instant) -> String) {
    if (moments.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
            modifier = Modifier.padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space3),
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        moments.forEachIndexed { index, moment ->
            if (index > 0) SakhiListDivider(startInset = SakhiSpacing.space6 + 34.dp + SakhiSpacing.space3)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(34.dp).background(sakhiLightPink(), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = moment.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(SakhiSpacing.space3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = moment.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = sakhiLabel(),
                    )
                    Text(
                        text = moment.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = sakhiSecondaryLabel(),
                    )
                }
                Text(
                    text = dayLabel(moment.at),
                    style = MaterialTheme.typography.bodySmall,
                    color = sakhiSecondaryLabel(),
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
            .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space4),
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
