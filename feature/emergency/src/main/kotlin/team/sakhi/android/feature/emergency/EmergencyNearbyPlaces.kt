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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import team.sakhi.android.ui.LoadingShimmer
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
import androidx.compose.ui.text.style.TextAlign
import team.sakhi.android.designsystem.sakhiLightPink
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Surface
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.sakhiDeepRose
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.NearbySakhi
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The safe-places browser. Port of iOS `EmergencyNearbyView.swift`.
 *
 * Android had no counterpart at all: `SafePlace` existed only inside the AI chat, so the
 * whole "places you can walk to" half of Emergency Assistance was iOS-only.
 */
@Composable
fun EmergencyNearbyPlaces(
    places: List<EmergencySafePlace>,
    /** The women around her, listed beside the places rather than behind a push. */
    sakhis: List<NearbySakhi>,
    isSearching: Boolean,
    /** True while her own refresh is in flight, which spins the glyph in the nav bar. */
    isRefreshing: Boolean = false,
    /** Re-asks who and what is around her. */
    onRefresh: () -> Unit = {},
    /**
     * Whether there is a location fix. Without one the search cannot run at all, and the
     * empty state has to say so rather than claim nothing is nearby.
     */
    hasLocation: Boolean,
    onBack: () -> Unit,
    /** Brings the Sakhi section into view from its chip. */
    onScrollToSakhis: () -> Unit = {},
    onAskSakhi: (NearbySakhi) -> Unit,
    onOpenSakhiProfile: (NearbySakhi) -> Unit,
    onSelect: (EmergencySafePlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    // `null` means every kind, which is what she lands on.
    var selectedKind by remember { mutableStateOf<EmergencySafePlaceKind?>(null) }
    val visible = remember(places, selectedKind) {
        selectedKind?.let { k -> places.filter { it.kind == k } } ?: places
    }

    Column(modifier = modifier.fillMaxWidth()) {
        EmergencySheetNavBar(
            title = "Nearby",
            onBack = onBack,
            // Asking again is the whole point of this screen on a quiet night: nobody was
            // online a minute ago, somebody may be now. Nothing else re-ran the search
            // short of closing the flow and opening it again.
            trailing = { NearbyRefreshButton(isRefreshing = isRefreshing, onClick = onRefresh) },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = SakhiSpacing.space8),
        ) {
            Chips(
                places = places,
                sakhiCount = sakhis.size,
                selectedKind = selectedKind,
                onSelect = { selectedKind = if (selectedKind == it) null else it },
                // Sakhis are already the first section on this sheet, so the chip only has
                // to clear any place filter that is hiding nothing she wants.
                onSelectSakhis = { selectedKind = null },
                // The chips carry their own 14 below, matching the frame.
                modifier = Modifier,
            )

            SakhiSection(
                sakhis = sakhis,
                isRefreshing = isRefreshing,
                onAsk = onAskSakhi,
                onOpenProfile = onOpenSakhiProfile,
                modifier = Modifier.padding(bottom = SakhiSpacing.space2),
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
 * Asking a person, kept out of the filter row and given a section of its own.
 *
 * It sat in the chip row next to washroom and hospital, which made a person read as another
 * category of place to filter by. It is not a filter: it is the other answer to the same
 * question, so it gets a section above the list.
 *
 * The section is always here, whether or not anyone is around. Karan asked for that
 * directly, and it is the right call: a person is the thing this feature is for, and having
 * the option vanish on the nights nobody is online tells her the app has nothing to offer
 * when it is exactly the moment she needs to know somebody could turn up.
 */
@Composable
private fun SakhiSection(
    sakhis: List<NearbySakhi>,
    isRefreshing: Boolean,
    onAsk: (NearbySakhi) -> Unit,
    onOpenProfile: (NearbySakhi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        EmergencySectionHeader(title = stringResource(R.string.emergency_section_sakhis))
        if (isRefreshing && sakhis.isEmpty()) {
            // While she is asking again, "No Sakhis nearby right now" is not yet true.
            EmergencyCard {
                repeat(SakhiSkeletonRows) { index ->
                    if (index > 0) EmergencyRowDivider(leadingInset = 72.dp)
                    NearbyRowSkeleton(avatarSize = 44.dp, titleWidth = if (index == 0) 132.dp else 108.dp)
                }
            }
        } else if (sakhis.isEmpty()) {
            // A calm placeholder, not an error and not a dead end. She keeps the list of
            // places below either way, so this says what is true and gets out of the way.
            //
            // Laid out like every other row in the flow -- glyph, then the line, then the
            // detail under it -- and filled with the brand's light pink instead of white. A
            // white card with centred text read as a hole where the Sakhis should have been;
            // this reads as a card that happens to have nothing in it yet.
            Surface(
                shape = RoundedCornerShape(SakhiRadius.lg),
                color = sakhiLightPink(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space5),
            ) {
                Row(
                    modifier = Modifier.padding(SakhiSpacing.space4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                ) {
                    // The glyph on its own, no disc behind it. On a card that is already
                    // pink a second pink-on-white circle was one container too many.
                    Icon(
                        imageVector = Icons.Filled.People,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = sakhiDeepRose(),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        // Both lines stay in the pink family, and the contrast between them
                        // is carried by depth rather than by going grey: deep rose for the
                        // line that matters, softened for the one under it.
                        Text(
                            text = stringResource(R.string.emergency_no_sakhis_nearby),
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = sakhiDeepRose(),
                        )
                        Text(
                            text = stringResource(R.string.emergency_no_sakhis_nearby_body),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = sakhiDeepRose().copy(alpha = 0.62f),
                        )
                    }
                }
            }
        } else {
            // The women themselves, in the same list as the places. There used to be one
            // row here that pushed a whole separate screen; a person and a pharmacy are two
            // answers to the same question, so they belong on the same sheet.
            EmergencyCard {
                sakhis.forEachIndexed { index, sakhi ->
                    if (index > 0) EmergencyRowDivider(leadingInset = 72.dp)
                    NearbySakhiRow(
                        sakhi = sakhi,
                        onOpenProfile = { onOpenProfile(sakhi) },
                    )
                }
            }
        }
    }
}

/**
 * One woman in the safe-places sheet.
 *
 * The same row the Sakhi list uses: her face, her name beside a trust seal, her level and
 * distance, and an Ask pill that turns grey once the request is out. Tapping the row opens
 * her profile, which is where the decision to trust a stranger is meant to be made; the pill
 * is the shortcut for when she has already decided.
 *
 * The face is the illustrated avatar dealt to her user id, never a photograph -- this list
 * is visible before anyone has accepted anything.
 */
@Composable
private fun NearbySakhiRow(sakhi: NearbySakhi, onOpenProfile: () -> Unit) {
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

        // "Asked" stays, because it is a fact about this row she needs to see. The pink
        // Ask pill is gone: the whole row already leads to her profile, where the ask
        // button lives, and deciding to trust a stranger belongs on that screen rather
        // than behind a button she can hit from a list. Karan asked for the row itself to
        // be the target.
        if (asked) {
            Text(
                text = stringResource(R.string.emergency_asked),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = sakhiSecondaryLabel(),
            )
            Spacer(modifier = Modifier.width(2.dp))
        }

        EmergencyChevron()
    }
}

@Composable
private fun Chips(
    places: List<EmergencySafePlace>,
    sakhiCount: Int,
    selectedKind: EmergencySafePlaceKind?,
    onSelect: (EmergencySafePlaceKind) -> Unit,
    onSelectSakhis: () -> Unit,
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
        // Sakhi first, and it counts people rather than places. iOS's own comment is the
        // rule: it is the only chip that does not filter the list below -- tapping it is how
        // she gets to the people. Android had dropped it entirely.
        FilterChip(
            icon = Icons.Filled.People,
            tint = SakhiUIColors.BRAND_PINK.toComposeColor(),
            label = stringResource(R.string.emergency_section_sakhis),
            count = sakhiCount,
            isSelected = false,
            onClick = onSelectSakhis,
        )

        EmergencySafePlaceKind.entries.forEach { kind ->
            FilterChip(
                icon = kind.icon,
                tint = kind.tint,
                label = kind.chipLabel,
                count = places.count { it.kind == kind },
                isSelected = selectedKind == kind,
                onClick = { onSelect(kind) },
            )
        }
    }
}

/** One pill in the filter row. Figma `chip`: `px-14 py-7`, a 14dp glyph and a 6 gap. */
@Composable
private fun FilterChip(
    icon: ImageVector,
    tint: Color,
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            // Figma: the chosen pill is filled brand pink with white text; the rest are
            // plain white with secondary text. It used to be a white pill with a 2dp ring in
            // the kind's own colour, which put four different accent colours in one row and
            // read as a legend rather than a filter.
            .background(
                if (isSelected) {
                    SakhiUIColors.BRAND_PINK.toComposeColor()
                } else {
                    sakhiSystemBackground()
                },
                RoundedCornerShape(percent = 50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The kind's own glyph, so the row reads at a glance rather than by reading four
        // words. Selected takes white with the pill; unselected keeps the kind's colour,
        // which is the only place that colour appears now the pills are brand pink.
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (isSelected) Color.White else tint,
        )
        Text(
            text = "$label · $count",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) Color.White else sakhiSecondaryLabel(),
        )
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
        ?: stringResource(R.string.emergency_section_safe_places)

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

/** One place: badge, name, and the kind and distance under it. iOS `EmergencyPlaceRow`. */
@Composable
private fun PlaceRow(place: EmergencySafePlace, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // iOS: `.padding(.horizontal, DS.Spacing.m)` (16), `.vertical, .s` (12).
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
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
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = sakhiLabel(),
                // Two lines, as iOS allows. Real names run long -- "MEDICARE Pharmacy And
                // More" -- and one line turned most of this list into ellipses.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = place.kindAndDistance,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = sakhiSecondaryLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // No walking time. That was mine, from the Figma frame, and it was the thing
        // squeezing every long name into "MEDICARE Pharmacy And More…" on a two-line row.
        // iOS keeps the minutes for the detail screen, where there is room to say "11 min
        // walk" rather than a bare number.
        EmergencyChevron()
    }
}

/**
 * The list she is about to get, greyed out, rather than a spinner over empty space.
 *
 * A spinner says only "wait"; rows in the shape of the real ones say what is coming and
 * keep the sheet from jumping when they arrive. Karan asked for this directly.
 */
@Composable
private fun Loading() {
    Column {
        EmergencySectionHeader(title = stringResource(R.string.emergency_section_safe_places))
        EmergencyCard {
            repeat(PlaceSkeletonRows) { index ->
                if (index > 0) EmergencyRowDivider()
                NearbyRowSkeleton(titleWidth = PlaceSkeletonTitleWidths[index % PlaceSkeletonTitleWidths.size])
            }
        }
    }
}

/**
 * One placeholder row: the badge, the name, and the line under it.
 *
 * Laid out to the same numbers as [PlaceRow] -- a 30dp badge, 16/12 padding, 12 between --
 * so the real rows land exactly where these stood.
 */
@Composable
private fun NearbyRowSkeleton(titleWidth: Dp, avatarSize: Dp = 30.dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        LoadingShimmer(
            height = avatarSize,
            width = avatarSize,
            // The badge's own corner, so the placeholder is the badge's shape, not a pill.
            shape = RoundedCornerShape(avatarSize * 0.25f),
        )
        Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
            LoadingShimmer(height = 13.dp, width = titleWidth, shape = RoundedCornerShape(4.dp))
            LoadingShimmer(height = 11.dp, width = 92.dp, shape = RoundedCornerShape(4.dp))
        }
    }
}

/**
 * Ask again, from the nav bar's trailing slot.
 *
 * Spins while the answer is on its way and refuses a second tap until it lands, so an
 * impatient double tap cannot fire two searches.
 */
@Composable
private fun NearbyRefreshButton(isRefreshing: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "nearby-refresh")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "nearby-refresh-spin",
    )

    IconButton(
        onClick = onClick,
        enabled = !isRefreshing,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = stringResource(R.string.emergency_refresh_nearby),
            // Tinted here rather than left to `IconButton`, which greys a disabled glyph --
            // and this one is "disabled" exactly while it is spinning, which is when it most
            // needs to look alive.
            tint = SakhiUIColors.BRAND_PINK.toComposeColor(),
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = if (isRefreshing) spin else 0f },
        )
    }
}

/** Enough rows to fill the sheet at its resting height, and no more. */
private const val PlaceSkeletonRows = 5
private const val SakhiSkeletonRows = 2

/** Uneven on purpose: equal bars read as a table, not as names. */
private val PlaceSkeletonTitleWidths = listOf(196.dp, 148.dp, 214.dp, 132.dp, 178.dp)

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
