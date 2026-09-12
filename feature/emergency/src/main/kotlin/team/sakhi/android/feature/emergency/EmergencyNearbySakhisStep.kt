package team.sakhi.android.feature.emergency

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.rememberSakhiFlingBehavior
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.android.ui.SakhiModalSheet
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.SakhiUIColors
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.emergency.EmergencyState
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.NearbySakhi
import team.sakhi.models.TrustLevel
import androidx.compose.foundation.layout.WindowInsets

/**
 * Step 3 — who is around, and the screen where she picks one.
 *
 * `nearFriendsViewController` on `main`: a list of `AskProfileCardCollectionViewCell`,
 * each 100dp tall with 20dp between them, carrying a circular avatar, her name, a trust
 * badge tinted by level, and a 10dp-radius time pill that reads "Too Far" past 180
 * minutes. Tapping a card opened her profile, and the Ask button lived there rather than
 * on the row.
 *
 * Nothing has been sent at this point. No one knows she needs help until she chooses a
 * person, which is the whole shape of the feature: **she chooses, and the Sakhi she picks
 * consents.** If this screen ever grows a "notify everyone" button, that is the broadcast
 * model coming back, and migration 036 explains why it was removed.
 */
@Composable
internal fun EmergencyNearbySakhisStep(
    viewModel: EmergencyViewModel,
    step: EmergencyState.ChoosingSakhi,
) {
    val sakhis = step.sakhis

    Column(modifier = Modifier.fillMaxSize()) {
        // iOS: `.navigationTitle("Nearby Friends")` inline with a Back item, and nothing
        // else above the list. Android had a heading with a subtitle plus a chip repeating
        // the requirement and spot she had just chosen on the previous two screens.
        //
        // Through the flow's own `EmergencySheetNavBar` rather than Material's
        // `CenterAlignedTopAppBar`: this screen was the last one still drawing its own
        // header, so its title came out at a different size and its Back had no chevron.
        EmergencySheetNavBar(
            title = stringResource(R.string.emergency_nearby_friends),
            // `backToRequirement`, as iOS. This was `backToSakhis`, which only acts from the
            // spot step, so on this screen Back silently did nothing.
            onBack = viewModel::backToRequirement,
        )

        // Figma `intro`: `pt-4 pb-14 px-20`, 4 between the count and the line under it.
        // The reassurance matters most right here -- this is the screen where she is about
        // to hand a stranger something about herself.
        if (sakhis.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space5)
                    .padding(top = SakhiSpacing.space1, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.emergency_sakhis_around_you,
                        sakhis.size,
                        sakhis.size,
                    ),
                    fontSize = 20.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = sakhiLabel(),
                )
                Text(
                    text = stringResource(R.string.emergency_sakhis_see_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                )
            }
        } else {
            Spacer(modifier = Modifier.size(SakhiSpacing.space3))
        }

        if (sakhis.isEmpty()) {
            // A fixed height, not `weight(1f)`. The sheet's content is measured against the
            // full window while only the 502dp peek is on screen, so centring in the
            // remaining space put this whole state below the fold -- the screen read as
            // blank with a sliver of spinner at the very bottom.
            Box(
                modifier = Modifier.fillMaxWidth().height(EmptyStateHeight),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                ) {
                    if (step.isRefreshing) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(R.string.emergency_looking_for_sakhis),
                            style = MaterialTheme.typography.bodyMedium,
                            color = sakhiSecondaryLabel(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PersonOff,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.emergency_no_nearby_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.emergency_no_nearby_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = sakhiSecondaryLabel(),
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = viewModel::refreshNearbySakhis,
                            shape = CircleShape,
                        ) {
                            Text(stringResource(R.string.emergency_search_again))
                        }
                    }
                }
            }
        } else {
            // iOS wraps the list in `.refreshable { refreshNearbySakhis() }`. Android had no
            // pull-to-refresh, so the only way to re-check was to leave and come back.
            // The indicator tracks the PULL, not the store's refreshing flag.
            //
            // iOS uses `.refreshable`, which only draws a spinner for the gesture. Binding
            // this to `step.isRefreshing` instead meant any background refresh -- the one
            // that runs on arrival, for instance -- parked a spinner in the middle of the
            // list, on top of the first Sakhi's distance line.
            var userRefreshing by remember { mutableStateOf(false) }
            LaunchedEffect(step.isRefreshing) { if (!step.isRefreshing) userRefreshing = false }

            PullToRefreshBox(
                isRefreshing = userRefreshing,
                onRefresh = {
                    userRefreshing = true
                    viewModel.refreshNearbySakhis()
                },
                modifier = Modifier.weight(1f),
            ) {
            // One card holding every Sakhi, split by hairlines -- Figma `EA-06`, and the
            // same shape as every other list in the flow. It was a separate 16dp-radius
            // card per person, which made three people fill the whole sheet.
            LazyColumn(
                flingBehavior = rememberSakhiFlingBehavior(),
                contentPadding = PaddingValues(bottom = SakhiSpacing.space10),
            ) {
                item {
                    EmergencyCard {
                        sakhis.forEachIndexed { index, sakhi ->
                            if (index > 0) SakhiListDivider(startInset = 72.dp)
                            SakhiRow(
                                sakhi = sakhi,
                                onOpenProfile = { viewModel.openProfile(sakhi.userId) },
                                onAsk = { viewModel.ask(sakhi) },
                            )
                        }
                    }
                }
            }
            }
        }

    }

    val profile by viewModel.profileDetail.collectAsStateWithLifecycle()
    if (profile != null) {
        // iOS offers `[.medium, .large]` here. Android keeps the one detent the shared
        // host has, because the bottom of this content is the Ask button -- the one action
        // on this screen -- and a half-open sheet would have opened with it off-screen.
        SakhiModalSheet(onDismissRequest = viewModel::closeProfile) {
            EmergencyProfileDetailSheet(
                viewModel = viewModel,
                profile = profile!!,
                askable = sakhis.firstOrNull { it.userId == profile!!.userId },
                onDismiss = viewModel::closeProfile,
            )
        }
    }
}

/**
 * How much room the "nobody is around" state gets.
 *
 * Sized against the sheet's own peek height rather than the window, so it lands where she
 * is actually looking.
 */
private val EmptyStateHeight = 300.dp

/**
 * One woman in the list.
 *
 * Figma `EA-06` -> `sakhi · …`: `px-16 py-11` with a 12 gap, a 44 face, her name beside a
 * 13dp trust seal, her level and distance under it, and an Ask pill on the right that turns
 * grey and reads "Asked" once the request is out.
 *
 * The face is the illustrated avatar dealt to her user id, never a photograph. That is the
 * whole point of `EmergencyAvatarCatalog`: this list is visible before anyone has accepted
 * anything, where a glance over her shoulder catches every face on it.
 *
 * Tapping the row opens her profile, which is where the decision to trust a stranger is
 * meant to be made; the pill is the shortcut for when she has already decided.
 */
@Composable
private fun SakhiRow(sakhi: NearbySakhi, onOpenProfile: () -> Unit, onAsk: () -> Unit) {
    val context = LocalContext.current
    val trust = EmergencyFormatting.trustLevel(sakhi.ratingCount)
    val trustColor = trust.accentColor()
    val asked = sakhi.alreadyAsked
    val faceIndex = remember(sakhi.userId) { EmergencyAvatarCatalog.dealtIndex(sakhi.userId) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenProfile)
            .padding(horizontal = SakhiSpacing.space4, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Image(
            painter = painterResource(EmergencyAvatarCatalog.drawableAt(faceIndex)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .scale(EmergencyAvatarCatalog.contentScaleAt(faceIndex)),
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = sakhi.name ?: stringResource(R.string.emergency_a_sakhi_nearby),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = sakhiLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    // Always the check seal, as iOS draws it. Android varied the glyph by
                    // level (spa, heart, sparkle), so the same fact looked like four
                    // different facts. The colour still carries the level.
                    imageVector = Icons.Filled.Verified,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = trustColor,
                )
            }
            Text(
                text = "${trust.displayName} · " +
                    EmergencyFormatting.approximateDistance(sakhi.distanceBucketMeters),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = sakhiSecondaryLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Asked is a state, not an action, so it is a small quiet label -- not a second pill
        // the same size and shape as the one she can still press. A grey button beside a
        // pink one reads as a choice she is being offered and is not.
        //
        // Still tappable, though: asking again is exactly what she would want if the first
        // one went unanswered, and a woman who is standing somewhere uncomfortable should
        // not have to work out that the app has silently stopped letting her try.
        // The action and the chevron travel together, closer to each other than either is
        // to the text. The row's own 12 gap sat between them as well, which with the
        // label's padding on top left "Asked" adrift in the middle of nowhere.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (asked) {
                Text(
                    text = stringResource(R.string.emergency_asked),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = sakhiSecondaryLabel(),
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(onClick = onAsk)
                        // Vertical only. The height keeps the tap target real; horizontal
                        // padding here is what pushed it away from the chevron.
                        .padding(vertical = 8.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(SakhiUIColors.BRAND_PINK.toComposeColor())
                        .clickable(onClick = onAsk)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.emergency_ask),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }

            // Always, whichever of the two is beside it. The row pushes to her profile
            // either way, so the disclosure is a fact about the row rather than about the
            // state of the request -- taking it away once she had asked made the row look
            // like it had stopped leading anywhere.
            EmergencyChevron()
        }
    }
}

// `badgeIcon` mapped each trust level to a different glyph (spa, heart, sparkle). iOS
// always draws the check seal and lets the colour carry the level, so the same fact stopped
// looking like four different facts -- and this had no callers left.
