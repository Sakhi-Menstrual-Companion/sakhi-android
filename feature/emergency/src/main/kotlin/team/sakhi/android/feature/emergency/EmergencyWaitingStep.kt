package team.sakhi.android.feature.emergency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.emergency.EmergencyState
import team.sakhi.models.EmergencyFormatting
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import team.sakhi.models.EmergencyOffer
import team.sakhi.models.TrustLevel

/**
 * Step 3 — her request is live and offers arrive here.
 *
 * Offers land over Realtime, because she owns the request row and so can be pushed its
 * offers. Each card shows a name, a rating, and an approximate distance, never a position.
 * She is choosing who gets to walk up to her, so the identity matters and the geography
 * does not, yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmergencyWaitingStep(
    viewModel: EmergencyViewModel,
    step: EmergencyState.WaitingForHelp,
) {
    var showCancelConfirm by remember { mutableStateOf(false) }
    val offers = step.offers

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = SakhiSpacing.space5),
    ) {
        EmergencyHeader(
            title = if (offers.isEmpty()) {
                stringResource(R.string.emergency_asking_nearby)
            } else {
                stringResource(R.string.emergency_someone_can_help)
            },
            subtitle = if (offers.isEmpty()) {
                stringResource(R.string.emergency_asking_nearby_subtitle)
            } else {
                stringResource(R.string.emergency_someone_can_help_subtitle)
            },
        )

        if (offers.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                ) {
                    EmergencyPulse()
                    Text(
                        text = EmergencyFormatting.requirementLabelForRequester(step.requirement),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    step.spotLabel?.takeIf { it.isNotBlank() }?.let { spot ->
                        Text(
                            text = spot,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            ) {
                items(offers, key = { it.offerId }) { offer ->
                    OfferCard(
                        offer = offer,
                        onAccept = { viewModel.acceptOffer(offer) },
                        // The original's card hinted "Tap to view {name} profile".
                        onOpenProfile = { viewModel.openProfile(offer.responderId) },
                    )
                }
            }
        }

        TextButton(
            onClick = { showCancelConfirm = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.emergency_cancel_request),
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.size(SakhiSpacing.space3))
    }

    val profile by viewModel.profileDetail.collectAsStateWithLifecycle()
    if (profile != null) {
        ModalBottomSheet(onDismissRequest = viewModel::closeProfile) {
            EmergencyProfileDetailSheet(
                viewModel = viewModel,
                profile = profile!!,
                onDismiss = viewModel::closeProfile,
            )
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(stringResource(R.string.emergency_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.emergency_cancel_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    viewModel.cancelRequest(step.requestId)
                }) {
                    Text(
                        text = stringResource(R.string.emergency_cancel_request),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text(stringResource(R.string.emergency_keep_waiting))
                }
            },
        )
    }
}

/**
 * Replica of `AskProfileCardCollectionViewCell`: an 11dp-radius card with a circular
 * avatar, her name, a trust badge tinted by level, and a 10dp-radius time pill that reads
 * "Too Far" past 180 minutes rather than printing a walk nobody would make.
 */
@Composable
private fun OfferCard(offer: EmergencyOffer, onAccept: () -> Unit, onOpenProfile: () -> Unit) {
    val trust = EmergencyFormatting.trustLevel(offer.ratingCount)
    val trustColor = Color(0xFF000000 or (trust.colorHex.removePrefix("#").toLongOrNull(16) ?: 0))

    Surface(
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Box(modifier = Modifier.clickable(onClick = onOpenProfile)) {
                EmergencyAvatar(name = offer.responderName, photoUrl = offer.responderPhotoUrl)
            }

            Column(modifier = Modifier.weight(1f).clickable(onClick = onOpenProfile)) {
                Text(
                    text = offer.responderName ?: stringResource(R.string.emergency_a_sakhi_nearby),
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
                Text(
                    // Approximate by design — the bucket is coarsened server-side.
                    text = EmergencyFormatting.approximateDistance(offer.distanceBucketMeters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = EmergencyFormatting.etaBadge(offer.etaMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            horizontal = SakhiSpacing.space3,
                            vertical = SakhiSpacing.space1,
                        ),
                    )
                }
                Button(onClick = onAccept, shape = CircleShape) {
                    Text(stringResource(R.string.emergency_choose))
                }
            }
        }
    }
}

/** Material equivalents of the original's SF Symbol badges. */
@Composable
private fun TrustLevel.badgeIcon(): ImageVector = when (this) {
    TrustLevel.CARING -> Icons.Filled.Spa
    TrustLevel.KIND -> Icons.Outlined.FavoriteBorder
    TrustLevel.VERY_KIND -> Icons.Filled.Favorite
    TrustLevel.ANGEL -> Icons.Filled.AutoAwesome
}
