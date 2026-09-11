package team.sakhi.android.feature.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import team.sakhi.android.designsystem.sakhiLightPink
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.EmergencyProfileDetail
import team.sakhi.models.NearbySakhi
import androidx.compose.foundation.layout.fillMaxHeight
import team.sakhi.android.ui.SakhiAlertKind
import team.sakhi.android.ui.SakhiAlertSheet
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.SakhiTokens
import team.sakhi.android.designsystem.sakhiLabel

/**
 * Replica of `ProfielDetailViewController` — the card a woman opens before deciding to let
 * someone walk over to her.
 *
 * The original laid this out as a grouped table with 11pt corners: a "Requests" section
 * with "Helped Other" and "Requested Help" (both `person.2.fill`), a "Last Active" section
 * (`clock`), and a "Block" section.
 *
 * Nothing here is a position. Deciding whether to trust someone does not require knowing
 * where she is, so the server does not send it.
 */
@Composable
internal fun EmergencyProfileDetailSheet(
    viewModel: EmergencyViewModel,
    profile: EmergencyProfileDetail,
    /**
     * The Sakhi this profile belongs to, when opened from the nearby list. `null` when
     * there is nobody to ask from here, which is when `main` hid its request button too.
     */
    askable: NearbySakhi? = null,
    /**
     * How to ask, when the caller has to do more than `viewModel.ask`.
     *
     * Opened from the nearby sheet, the flow has not reached `ChoosingSakhi` yet, and
     * `askSakhi` returns early anywhere else -- so the default would have been a button
     * that did nothing. That caller passes `askFromNearby`, which chooses the requirement
     * on the way. Null keeps the picker's own behaviour.
     */
    onAsk: ((NearbySakhi) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showBlockDuringRequest by remember { mutableStateOf(false) }
    val alreadyAsked = askable?.alreadyAsked == true
    val trust = EmergencyFormatting.trustLevel(profile.ratingCount)
    val context = LocalContext.current

    // `fillMaxSize`, not `fillMaxWidth`, and the scroller takes the remaining space with a
    // filling weight.
    //
    // The Ask button was already outside the scroll, but with a wrap-height column and
    // `weight(1f, fill = false)` there was no bottom for it to pin to: it simply sat after
    // the content wherever that ended. iOS pins it with `VStack(spacing: 0) { ScrollView;
    // askButton }` inside a sheet that has a height, so the scroller absorbs the slack and
    // the one action on the screen stays put.
    // A medium detent, as iOS has: `.presentationDetents([.medium, .large])` opens on the
    // first one. Compose has no detents, so the height goes on the CONTENT instead -- the
    // sheet wraps it, so a 60% column produces a 60% sheet with the Ask button pinned to its
    // bottom. `fillMaxSize` gave a full-height sheet; leaving the sheet partially expanded
    // instead would have clipped the content's bottom, which is exactly where that button is.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(MEDIUM_DETENT_FRACTION),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
        ) {
            // Figma `EA-07`: the flow's nav bar, then a centred hero -- her face in an
            // 88dp disc tinted with her own trust colour, her name, and the trust pill.
            // It was a left-aligned row with a 60dp face and a close X of its own, which
            // made this the one sheet in the flow whose header did not match the others.
            EmergencySheetNavBar(
                title = stringResource(R.string.emergency_profile),
                onBack = onDismiss,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space5)
                    .padding(top = SakhiSpacing.space2, bottom = SakhiSpacing.space1),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                val trustColor = trust.accentColor()

                // Her face, the same one on her map pin, from the shared catalogue. The
                // profile was the one screen showing no picture of the person it is
                // entirely about. Deliberately *not* `photoUrl`: a real face has no
                // business on screen before anyone has accepted.
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(trustColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(EmergencyAvatarCatalog.drawableFor(profile.userId)),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                    )
                }

                Text(
                    text = profile.name ?: stringResource(R.string.emergency_a_sakhi_nearby),
                    fontSize = 20.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = sakhiLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // main: ctaButton -- the trust level, tinted by level, opening the site so
                // she can read what it means.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(trustColor.copy(alpha = 0.12f))
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://sakhi.rachna.co/")),
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = trustColor,
                    )
                    Text(
                        text = trust.displayName,
                        fontSize = 17.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = trustColor,
                    )
                }
            }

            // One card, three rows. Figma `EA-07` puts what she has done and when she was
            // last seen together under a single label; Android had them under two headers
            // in two one-row cards.
            EmergencySectionHeader(
                title = stringResource(R.string.emergency_profile_requests),
                topPadding = 20.dp,
            )
            EmergencyCard {
                // "Request Received" is every request addressed to her, answered or not
                // (receivedCount). requestedCount is how many times *she* asked someone
                // else, which is a fact about her own need rather than her reliability.
                ProfileCountRow(stringResource(R.string.emergency_profile_helped), profile.helpedCount)
                EmergencyRowDivider()
                ProfileCountRow(stringResource(R.string.emergency_profile_received), profile.receivedCount)
                EmergencyRowDivider()
                EmergencyRow(
                    title = stringResource(R.string.emergency_profile_last_active),
                    leading = {
                        EmergencyBadgeIcon(Icons.Filled.Schedule, SakhiTokens.SectionBlue)
                    },
                    accessory = {
                        Text(
                            // Android printed the raw ISO string here -- a Postgres
                            // timestamp shown to a woman deciding whether to trust someone.
                            text = lastActiveText(context, profile.lastActiveIso),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = sakhiSecondaryLabel(),
                            maxLines = 1,
                        )
                    },
                )
            }

            // No section header above this one, matching iOS. "BLOCK" over a row that
            // already says Block was Android's own addition.
            EmergencyCard(modifier = Modifier.padding(top = SakhiSpacing.space6)) {
                EmergencyRow(
                    title = stringResource(
                        if (profile.isBlocked) R.string.emergency_unblock else R.string.emergency_block,
                    ),
                    titleColor = MaterialTheme.colorScheme.error,
                    leading = {
                        EmergencyBadgeIcon(Icons.Filled.Block, MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable {
                        // main: performUserBlocking refused while a request was live,
                        // because blocking mid-request strands the woman walking to you.
                        if (alreadyAsked) showBlockDuringRequest = true else showBlockConfirm = true
                    },
                )
            }

            Spacer(modifier = Modifier.size(SakhiSpacing.space10))
        }

        // Outside the scroll, pinned to the bottom, as iOS pins it. Android had this inside
        // the scroll, so on a short sheet the one action on the screen scrolled away.
        if (askable != null) {
            EmergencyAskButton(
                alreadyAsked = alreadyAsked,
                onAsk = { onAsk?.invoke(askable) ?: viewModel.ask(askable) },
                onWindowElapsed = onDismiss,
                modifier = Modifier
                    .padding(horizontal = SakhiSpacing.space4)
                    .padding(top = SakhiSpacing.space3, bottom = SakhiSpacing.space4),
            )
        }
    }

    if (showBlockDuringRequest) {
        SakhiAlertSheet(
            kind = SakhiAlertKind.Warning,
            title = stringResource(R.string.emergency_hold_on),
            message = stringResource(R.string.emergency_end_request_first),
            primaryLabel = stringResource(R.string.emergency_got_it),
            onPrimaryClick = { showBlockDuringRequest = false },
            onDismissRequest = { showBlockDuringRequest = false },
        )
    }

    if (showBlockConfirm) {
        SakhiAlertSheet(
            kind = SakhiAlertKind.Destructive,
            title = if (profile.isBlocked) {
                stringResource(R.string.emergency_unblock)
            } else {
                stringResource(R.string.emergency_block)
            },
            message = if (profile.isBlocked) {
                stringResource(R.string.emergency_unblock_body)
            } else {
                stringResource(R.string.emergency_block_body)
            },
            primaryLabel = if (profile.isBlocked) {
                stringResource(R.string.emergency_unblock)
            } else {
                stringResource(R.string.emergency_block)
            },
            onPrimaryClick = {
                showBlockConfirm = false
                viewModel.setBlocked(profile.userId, !profile.isBlocked)
                if (!profile.isBlocked) onDismiss()
            },
            secondaryLabel = stringResource(R.string.emergency_keep_waiting),
            onSecondaryClick = { showBlockConfirm = false },
            onDismissRequest = { showBlockConfirm = false },
        )
    }
}



/** iOS `countRow`: the shared row with the count as its trailing value. */
@Composable
private fun ProfileCountRow(title: String, value: Int) {
    EmergencyRow(
        title = title,
        leading = { EmergencyBadgeIcon(Icons.Filled.Group, MaterialTheme.colorScheme.primary) },
        accessory = {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = sakhiSecondaryLabel(),
            )
        },
    )
}

/**
 * iOS `lastActiveText`: "Just now" under a minute, matching the original's wording,
 * otherwise a relative phrase. Android was showing the raw ISO-8601 string.
 */
private fun lastActiveText(context: android.content.Context, iso: String?): String {
    val instant = EmergencyIso8601.instant(iso)
        ?: return context.getString(R.string.emergency_profile_unknown)
    val millis = instant.toEpochMilliseconds()
    val elapsed = System.currentTimeMillis() - millis
    if (elapsed < 60_000) return context.getString(R.string.emergency_just_now)
    return DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

// `ProfileSection` and `ProfileRow` lived here. Both are now the shared
// `EmergencySectionHeader` / `EmergencyCard` / `EmergencyRow` from `EmergencyComponents.kt`,
// which is what iOS uses across the whole flow.

/** iOS's `.medium` presentation detent, near enough at this size. */
private const val MEDIUM_DETENT_FRACTION = 0.6f
