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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.emergency.EmergencyState
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.NearbySakhi
import team.sakhi.models.TrustLevel

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

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = SakhiSpacing.space5),
    ) {
        EmergencyHeader(
            title = stringResource(R.string.emergency_who_can_help),
            subtitle = stringResource(R.string.emergency_who_can_help_subtitle),
        )

        // What she is asking for and from where, so the request is never a surprise.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = SakhiSpacing.space3,
                    vertical = SakhiSpacing.space1,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            ) {
                Icon(
                    imageVector = step.requirement.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = EmergencyFormatting.requirementShortName(step.requirement),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                step.spotLabel?.takeIf { it.isNotBlank() }?.let { spot ->
                    Text(
                        text = "· $spot",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }

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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(sakhis, key = { it.userId }) { sakhi ->
                    // main: didSelectItemAt opened the profile. The Ask lives there.
                    SakhiCard(sakhi = sakhi, onOpenProfile = { viewModel.openProfile(sakhi.userId) })
                }
            }
        }

        TextButton(
            onClick = viewModel::backToSpot,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.emergency_go_back))
        }

        Spacer(modifier = Modifier.size(SakhiSpacing.space3))
    }

    val profile by viewModel.profileDetail.collectAsStateWithLifecycle()
    if (profile != null) {
        ModalBottomSheet(onDismissRequest = viewModel::closeProfile) {
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
    val trustColor = Color(0xFF000000 or (trust.colorHex.removePrefix("#").toLongOrNull(16) ?: 0))

    Surface(
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().height(100.dp).clickable(onClick = onOpenProfile),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            EmergencyAvatar(name = sakhi.name, photoUrl = sakhi.photoUrl)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sakhi.name ?: stringResource(R.string.emergency_a_sakhi_nearby),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = trust.badgeIcon(),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = trustColor,
                    )
                    Text(
                        text = trust.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = trustColor,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = EmergencyFormatting.etaBadge(sakhi.etaMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            horizontal = SakhiSpacing.space3,
                            vertical = SakhiSpacing.space1,
                        ),
                    )
                }
                if (sakhi.alreadyAsked) {
                    Text(
                        text = stringResource(R.string.emergency_asked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Material equivalents of the original's SF Symbol badges. */
@Composable
internal fun TrustLevel.badgeIcon(): ImageVector = when (this) {
    TrustLevel.CARING -> Icons.Filled.Spa
    TrustLevel.KIND -> Icons.Outlined.FavoriteBorder
    TrustLevel.VERY_KIND -> Icons.Filled.Favorite
    TrustLevel.ANGEL -> Icons.Filled.AutoAwesome
}
