package team.sakhi.android.feature.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.WrongLocation
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.SakhiUIColors
import team.sakhi.emergency.EmergencySafePlace
import team.sakhi.emergency.EmergencySafePlaceKind

/**
 * The safe-places browser. Port of iOS `EmergencyNearbyView.swift`.
 *
 * Android had no counterpart at all: `SafePlace` existed only inside the AI chat, so the
 * whole "places you can walk to" half of Emergency Assistance was iOS-only.
 */
@Composable
fun EmergencyNearbyPlaces(
    places: List<EmergencySafePlace>,
    sakhiCount: Int?,
    isSearching: Boolean,
    /**
     * Whether there is a location fix. Without one the search cannot run at all, and the
     * empty state has to say so rather than claim nothing is nearby.
     */
    hasLocation: Boolean,
    onBack: () -> Unit,
    onSelectSakhis: () -> Unit,
    onSelect: (EmergencySafePlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    // `null` means every kind, which is what she lands on.
    var selectedKind by remember { mutableStateOf<EmergencySafePlaceKind?>(null) }
    val visible = remember(places, selectedKind) {
        selectedKind?.let { k -> places.filter { it.kind == k } } ?: places
    }

    Column(modifier = modifier.fillMaxWidth()) {
        EmergencySheetNavBar(title = "Nearby", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = SakhiSpacing.space8),
        ) {
            Chips(
                places = places,
                selectedKind = selectedKind,
                onSelect = { selectedKind = if (selectedKind == it) null else it },
                modifier = Modifier.padding(bottom = SakhiSpacing.space6),
            )

            SakhiCard(
                sakhiCount = sakhiCount,
                onClick = onSelectSakhis,
                modifier = Modifier.padding(bottom = SakhiSpacing.space6),
            )

            when {
                !hasLocation -> NoLocation()
                isSearching && places.isEmpty() -> Loading()
                visible.isEmpty() -> Empty(selectedKind)
                else -> PlaceList(visible = visible, selectedKind = selectedKind, onSelect = onSelect)
            }
        }
    }
}

/**
 * Asking a person, kept out of the filter row and given its own card.
 *
 * It sat in the chip row next to washroom and hospital, which made a person read as another
 * category of place to filter by. It is not a filter: it is the other answer to the same
 * question, so it gets a row of its own above the list.
 */
@Composable
private fun SakhiCard(sakhiCount: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val caption = when (sakhiCount) {
        null -> "Someone nearby may be able to help"
        0 -> "Nobody is around right now"
        1 -> "1 Sakhi is around you"
        else -> "$sakhiCount Sakhis are around you"
    }
    Column(modifier = modifier) {
        EmergencyCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
            ) {
                EmergencyBadgeIcon(
                    icon = Icons.Filled.People,
                    color = SakhiUIColors.BRAND_PINK.toComposeColor(),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ask a Sakhi instead",
                        style = MaterialTheme.typography.bodyLarge,
                        color = sakhiLabel(),
                    )
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelMedium,
                        color = sakhiSecondaryLabel(),
                    )
                }
                EmergencyChevron()
            }
        }
    }
}

@Composable
private fun Chips(
    places: List<EmergencySafePlace>,
    selectedKind: EmergencySafePlaceKind?,
    onSelect: (EmergencySafePlaceKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            // Figma `chips`: `pt-6 pb-14 px-20`, 8 between pills.
            .padding(horizontal = SakhiSpacing.space5)
            .padding(top = 6.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        EmergencySafePlaceKind.entries.forEach { kind ->
            val isSelected = selectedKind == kind
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    // Figma: the chosen pill is filled brand pink with white text; the rest
                    // are plain white with secondary text. It used to be a white pill with a
                    // 2dp ring in the kind's own colour, which put four different accent
                    // colours in one row and read as a legend rather than a filter.
                    .background(
                        if (isSelected) {
                            SakhiUIColors.BRAND_PINK.toComposeColor()
                        } else {
                            sakhiSystemBackground()
                        },
                        RoundedCornerShape(percent = 50),
                    )
                    .clickable { onSelect(kind) }
                    // Figma `chip`: `px-14 py-7`.
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${kind.chipLabel} · ${places.count { it.kind == kind }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) Color.White else sakhiSecondaryLabel(),
                )
            }
        }
    }
}

@Composable
private fun PlaceList(
    visible: List<EmergencySafePlace>,
    selectedKind: EmergencySafePlaceKind?,
    onSelect: (EmergencySafePlace) -> Unit,
) {
    // Counts what is actually listed, so the filter above has a visible effect even when a
    // kind returns only one or two.
    val title = selectedKind?.let { "${visible.size} ${it.chipLabel.lowercase()}" }
        ?: if (visible.size == 1) "1 place" else "${visible.size} places"

    Column {
        EmergencySectionHeader(title = title)
        EmergencyCard {
            visible.forEachIndexed { index, place ->
                if (index > 0) EmergencyRowDivider()
                PlaceRow(place = place, onClick = { onSelect(place) })
            }
        }
    }
}

/** One place: badge, name, and the kind and distance under it. */
@Composable
private fun PlaceRow(place: EmergencySafePlace, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // Figma `place · …`: a 63dp row -- the 30dp badge inset 16 with 16.5 above and
            // below it -- and a 12 gap, which puts the name at x=58 like every other row in
            // the flow. Android had 16/12, and these rows came out 8dp short.
            .padding(horizontal = SakhiSpacing.space4, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        EmergencyBadgeIcon(icon = place.kind.icon, color = place.kind.tint)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = sakhiLabel(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = place.kindAndDistance,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = sakhiSecondaryLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Figma puts the walking time between the name and the chevron. It was only in the
        // detail sheet before, so the list gave her no way to compare two places without
        // opening both.
        Text(
            text = "${place.walkingMinutes} min",
            style = MaterialTheme.typography.labelMedium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = sakhiSecondaryLabel(),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.size(SakhiSpacing.space3))
        EmergencyChevron()
    }
}

@Composable
private fun Loading() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = SakhiSpacing.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        CircularProgressIndicator(color = SakhiUIColors.BRAND_PINK.toComposeColor())
        Text(
            text = "Looking around you…",
            style = MaterialTheme.typography.bodyLarge,
            color = sakhiSecondaryLabel(),
        )
    }
}

/**
 * Says which kind came back empty rather than "no results".
 *
 * "No hospitals close to you right now" is a fact she can act on; "nothing found" reads as
 * the app having failed, and on this screen that difference matters.
 */
@Composable
private fun Empty(selectedKind: EmergencySafePlaceKind?) {
    val message = selectedKind
        ?.let { "No ${it.chipLabel.lowercase()} close to you right now." }
        ?: "Nothing found close to you right now."
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        Icon(
            Icons.Filled.WrongLocation,
            contentDescription = null,
            tint = sakhiSecondaryLabel().copy(alpha = 0.6f),
            modifier = Modifier.size(28.dp),
        )
        Text(message, style = MaterialTheme.typography.bodyLarge, color = sakhiSecondaryLabel())
    }
}

/**
 * The search never ran, which is a different thing from finding nothing.
 *
 * Without this the sheet said "Nothing found close to you right now" whenever there was no
 * fix, which tells her the area is empty when the truth is the app never looked. On a
 * safety screen that is the wrong thing to be confidently wrong about.
 */
@Composable
private fun NoLocation() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        Icon(
            Icons.Filled.LocationOff,
            contentDescription = null,
            tint = sakhiSecondaryLabel().copy(alpha = 0.6f),
            modifier = Modifier.size(28.dp),
        )
        Text("Location is off", style = MaterialTheme.typography.titleMedium, color = sakhiLabel())
        Text(
            text = "Sakhi needs your location to find the places you can walk to. " +
                "Nothing is shared with anyone.",
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
        )
    }
}
