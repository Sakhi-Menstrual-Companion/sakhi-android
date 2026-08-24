package team.sakhi.android.feature.emergency

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.sakhiTertiaryLabel
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import team.sakhi.android.designsystem.SakhiSpacing
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
@OptIn(ExperimentalMaterial3Api::class)
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
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.emergency_nearby_friends)) },
            navigationIcon = {
                TextButton(onClick = viewModel::backToSpot) {
                    Text(stringResource(R.string.emergency_back))
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
            ),
            // Same fix as the Location step: Material's TopAppBar reserves the STATUS BAR
            // inset, which inside a bottom sheet is pure dead space between the grabber and
            // the title.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        Spacer(modifier = Modifier.size(SakhiSpacing.space3))

        if (sakhis.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
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
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                contentPadding = PaddingValues(
                    start = SakhiSpacing.space4,
                    end = SakhiSpacing.space4,
                    bottom = SakhiSpacing.space10,
                ),
            ) {
                items(sakhis, key = { it.userId }) { sakhi ->
                    // main: didSelectItemAt opened the profile. The Ask lives there.
                    SakhiCard(sakhi = sakhi, onOpenProfile = { viewModel.openProfile(sakhi.userId) })
                }
            }
            }
        }

    }

    val profile by viewModel.profileDetail.collectAsStateWithLifecycle()
    if (profile != null) {
        // Always fully expanded. iOS offers `[.medium, .large]`, but a partially expanded
        // Compose sheet clips the bottom of its content, which is exactly where the Ask
        // button lives -- the one action on this screen would have been off-screen on open.
        ModalBottomSheet(
            onDismissRequest = viewModel::closeProfile,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            EmergencyProfileDetailSheet(
                viewModel = viewModel,
                profile = profile!!,
                askable = sakhis.firstOrNull { it.userId == profile!!.userId },
                onDismiss = viewModel::closeProfile,
            )
        }
    }
}

@Composable
private fun SakhiCard(sakhi: NearbySakhi, onOpenProfile: () -> Unit) {
    val trust = EmergencyFormatting.trustLevel(sakhi.ratingCount)
    val trustColor = trust.accentColor()
    val asked = sakhi.alreadyAsked

    Surface(
        shape = RoundedCornerShape(SakhiRadius.xl),
        color = sakhiSystemBackground(),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenProfile)
            // Dimmed once asked, as iOS dims the whole card, so the row reads as spent
            // rather than as another option.
            .alpha(if (asked) 0.6f else 1f),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            // The brand mark, not her photograph. See `EmergencyMarkAvatar`.
            EmergencyMarkAvatar()

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = sakhi.name ?: stringResource(R.string.emergency_a_sakhi_nearby),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Trust and distance on one quiet line, rather than a filled chip stacked
                // over a filled pill. Two capsules per row, times four rows, was most of
                // what made this list feel heavy -- and neither was the thing she chooses on.
                // Android was also missing the distance entirely.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        // Always the check seal, as iOS draws it. Android varied the glyph
                        // by level (spa, heart, sparkle), so the same fact looked like four
                        // different facts.
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        // The seal keeps the trust colour, so the level still reads at a
                        // glance without a chip around it.
                        tint = trustColor,
                    )
                    Text(
                        text = trust.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = sakhiSecondaryLabel(),
                        maxLines = 1,
                    )
                    Text("·", style = MaterialTheme.typography.bodySmall, color = sakhiSecondaryLabel())
                    Text(
                        text = EmergencyFormatting.approximateDistance(sakhi.distanceBucketMeters),
                        style = MaterialTheme.typography.bodySmall,
                        color = sakhiSecondaryLabel(),
                        maxLines = 1,
                    )
                }
            }

            if (asked) {
                // Surfaced on the row, not just inside the profile. Without it she taps in,
                // reaches the Ask button, and only then finds out the request is already out.
                Text(
                    text = stringResource(R.string.emergency_asked),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = sakhiSecondaryLabel(),
                )
            } else {
                // Plain pink text, not a filled pill.
                Text(
                    text = EmergencyFormatting.etaBadge(sakhi.etaMinutes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = sakhiTertiaryLabel(),
            )
        }
    }
}

// `badgeIcon` mapped each trust level to a different glyph (spa, heart, sparkle). iOS
// always draws the check seal and lets the colour carry the level, so the same fact stopped
// looking like four different facts -- and this had no callers left.
