package team.sakhi.android.feature.emergency

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.rememberSakhiFlingBehavior
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.design.SakhiUIColors
import team.sakhi.emergency.EmergencySafePlace

/**
 * One place in full. Port of iOS `EmergencyPlaceDetailView.swift`.
 *
 * A fact the place never published is left out, as iOS does. Two of three rows reading
 * "Not published for this place" made the card look broken for most pharmacies, which
 * Google lists without a phone or an address.
 */
@Composable
fun EmergencyPlaceDetail(
    place: EmergencySafePlace,
    onBack: () -> Unit,
    onDirections: (EmergencySafePlace) -> Unit,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        EmergencySheetNavBar(title = "Place", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState(), flingBehavior = rememberSakhiFlingBehavior())
                .padding(bottom = SakhiSpacing.space8),
        ) {
            // Figma `place head`: `pt-6 pb-18 px-20`, a 12 gap, and a 44dp badge, which
            // puts the name at x=76.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space5)
                    .padding(top = 6.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                EmergencyBadgeIcon(icon = place.kind.icon, color = place.kind.tint, size = 44.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 20.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = sakhiLabel(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = place.kindAndDistance,
                        style = MaterialTheme.typography.bodyMedium,
                        color = sakhiSecondaryLabel(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            EmergencySectionHeader(title = "Details")
            EmergencyCard {
                place.address?.let { address ->
                    DetailRow(
                        icon = Icons.Filled.Place,
                        tint = SakhiUIColors.BRAND_PINK.toComposeColor(),
                        title = "Address",
                        value = address,
                    )
                    SakhiListDivider(startInset = EmergencyRowInset)
                }
                DetailRow(
                    icon = Icons.Filled.DirectionsWalk,
                    tint = SakhiUIColors.BRAND_PINK.toComposeColor(),
                    title = "On foot",
                    value = place.onFootDescription,
                )
                place.phoneNumber?.let { number ->
                    SakhiListDivider(startInset = EmergencyRowInset)
                    DetailRow(
                        icon = Icons.Filled.Phone,
                        tint = SakhiUIColors.BRAND_CONFIRM.toComposeColor(),
                        title = "Phone",
                        value = number,
                        onClick = { onCall(number) },
                    )
                }
                place.website?.let { site ->
                    SakhiListDivider(startInset = EmergencyRowInset)
                    DetailRow(
                        icon = Icons.Filled.Language,
                        tint = SakhiUIColors.BRAND_PINK.toComposeColor(),
                        title = "Website",
                        value = site,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space5)
                    .padding(top = SakhiSpacing.space6),
                // Figma `actions`: 10 between the two pills, not 12.
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EmergencyPrimaryButton(title = "Directions") { onDirections(place) }
                place.phoneNumber?.let { number ->
                    EmergencySecondaryButton(title = "Call") { onCall(number) }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    // Through the shared `EmergencyRow`, so the label lands at x=58 like every other card
    // in the flow. These rows had a 16 gap after the badge, which pushed them 4dp right of
    // the ones on the screen she just came from.
    EmergencyRow(
        title = title,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        leading = { EmergencyBadgeIcon(icon = icon, color = tint) },
        accessory = {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = sakhiSecondaryLabel(),
                textAlign = TextAlign.End,
                // Two lines, so an address reads as an address rather than "Plot 12, Hi…".
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
