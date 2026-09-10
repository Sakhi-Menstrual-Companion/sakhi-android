package team.sakhi.android.feature.emergency

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import team.sakhi.design.SakhiUIColors
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.android.designsystem.sakhiSystemGray5
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import team.sakhi.android.designsystem.SakhiRadius
import androidx.compose.ui.text.style.TextOverflow
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.SakhiTokens
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.EmergencyRequirement
import team.sakhi.android.designsystem.sakhiSystemBackground
import androidx.compose.foundation.layout.WindowInsets
import team.sakhi.android.ui.SakhiAlertKind
import team.sakhi.android.ui.SakhiAlertSheet

// ─────────────────────────────────────────────────────────────────────────────
// Step 1 — what does she need
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmergencyRequirementStep(
    viewModel: EmergencyViewModel,
    /**
     * Opens the safe-places browser, carrying what she said she needs.
     *
     * Every requirement leads here rather than on to the spot field and the Sakhi list,
     * matching iOS. The requirement is passed rather than dropped so the Sakhi chip in that
     * list can still hand it to `chooseRequirement` and take her to the people.
     */
    onShowPlaces: (EmergencyRequirement) -> Unit = {},
    /** Her own face in the header, opening her profile. Null hides it. */
    myUserId: String? = null,
    onOpenMyProfile: () -> Unit = {},
    /**
     * Bumped when she picks a new face in the profile. The choice lives in
     * SharedPreferences, which Compose cannot observe, so without a key to hang a
     * `remember` on, the header kept the old face until the whole screen was rebuilt.
     */
    faceRevision: Int = 0,
) {
    // Opens straight onto the inbox when she arrived from a nearby-request push: she was
    // asked to help, so asking her what *she* needs would be the wrong first screen.

    // No horizontal padding here. Figma's `content` frame is full width and every child
    // insets itself by 20 -- and `EmergencySheetTitle`, `EmergencySectionHeader` and
    // `EmergencyCard` all already do. Padding the column as well doubled it to 40, which
    // is what put the heading and every card an extra 20 off the leading edge.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        // iOS is a `VStack(spacing: 0)`: the title and the first section sit flush, and the
        // only gap is `.padding(.bottom, DS.Spacing.l)` = 24 under the first section.
        // Android's uniform 16 put a gap under the title that iOS does not have and made
        // the two sections read as one run.
    ) {
        // iOS `EmergencyRequirementView`: a question with a line under it, not a label.
        EmergencySheetTitle(
            title = stringResource(R.string.emergency_requirement_title),
            subtitle = stringResource(R.string.emergency_requirement_subtitle),
            trailing = myUserId?.let { id ->
                {
                    val context = LocalContext.current
                    val faceIndex = remember(id, faceRevision) {
                        SakhiAvatarPreference.chosenIndex(context, id)
                            ?: EmergencyAvatarCatalog.dealtIndex(id)
                    }
                    // A plain white disc under the face, and nothing else: the artwork is
                    // light, so on the sheet's blush ground it had nothing to sit on.
                    // Figma `her profile avatar`: a 40 white circle with a 1px pink
                    // hairline at 18%, holding a 32 face inset 4. No drop shadow --
                    // Karan's standing rule for this flow is no shadows inside views.
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0x2EF61887), CircleShape)
                            .clickable(onClick = onOpenMyProfile),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(EmergencyAvatarCatalog.drawableAt(faceIndex)),
                            contentDescription = "Your profile",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .scale(EmergencyAvatarCatalog.contentScaleAt(faceIndex)),
                        )
                    }
                }
            },
        )

        RequirementSection(
            title = stringResource(R.string.emergency_section_right_now),
            items = EmergencyRequirement.urgent,
            onSelect = onShowPlaces,
            // Figma puts a 24 gap between the two sections. 8 here plus the next section
            // header's own 16 above its label is that 24.
            modifier = Modifier.padding(bottom = SakhiSpacing.space2),
        )
        RequirementGridSection(
            title = stringResource(R.string.emergency_section_something_else),
            items = EmergencyRequirement.other,
            onSelect = onShowPlaces,
        )

        // The two secondary pills that used to sit here are gone, matching iOS
        // `EmergencyRequirementView.swift`, whose own comment records the decision: the
        // nearby washroom/hospital/police link and the way into the helper inbox were both
        // removed so this screen is a requirement picker and nothing else.
        //
        // Worth knowing what went with them, and it is the same on both platforms now: the
        // responder inbox has no in-app route at all. A push notification is the only thing
        // that opens it, so a helper who dismisses one cannot get back to the request.
        // The push route into it is `openResponderInbox` on the flow screen.

        Spacer(modifier = Modifier.size(SakhiSpacing.space8))
    }

    // The responder inbox used to be presented from here, which meant a request that landed
    // while she was on any other step -- the places list, her own waiting screen -- had
    // nowhere to appear. `EmergencyFlowScreen` owns it now and can put it over anything.
}

@Composable
private fun RequirementSection(
    modifier: Modifier = Modifier,
    title: String,
    items: List<EmergencyRequirement>,
    onSelect: (EmergencyRequirement) -> Unit,
) {
    Column(modifier = modifier) {
        EmergencySectionHeader(title = title)
        // One card for the whole section, rows split by hairlines -- iOS's `EmergencyCard`
        // wrapping a `ForEach` that inserts an `EmergencyRowDivider` before every row but
        // the first.
        EmergencyCard {
            items.forEachIndexed { index, requirement ->
                if (index > 0) EmergencyRowDivider()
                RequirementRow(requirement = requirement, onClick = { onSelect(requirement) })
            }
        }
    }
}

@Composable
private fun RequirementRow(requirement: EmergencyRequirement, onClick: () -> Unit) {
    // No Surface of its own: the section's `EmergencyCard` is the card, and this is a row
    // inside it. iOS `requirementRow` is likewise just a Button wrapping an `EmergencyRow`.
    //
    // Through the shared `EmergencyRow`/`EmergencyBadgeIcon` rather than a hand-rolled Row
    // and a hand-rolled disc. This screen was still drawing `main`'s
    // RequirementTableViewCell badge -- a 40 circle filled at 0.2 alpha with a tinted glyph
    // -- while every other row in the flow had moved to the flow's own 30dp rounded square
    // with a white glyph. Its 12dp padding also made these rows 46 tall against Figma's 56.
    Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        EmergencyRow(
            title = EmergencyFormatting.requirementShortName(requirement),
            leading = {
                EmergencyBadgeIcon(
                    icon = requirement.icon(),
                    color = requirement.accentColor(),
                )
            },
            accessory = { EmergencyChevron() },
        )
    }
}

/**
 * The second half of the picker: four compact tiles, two to a row.
 *
 * Figma `13 · Emergency Assistance` -> `grid wrap`. Android had these as one more stacked
 * card with hairlines and chevrons, which made the screen a single run of five identical
 * rows and cost the sheet its whole lower half. Each tile is its own card, 12 apart both
 * ways, and carries no chevron -- the urgent row keeps that, so the eye still knows which
 * of the two groups is the one being pushed toward.
 */
@Composable
private fun RequirementGridSection(
    title: String,
    items: List<EmergencyRequirement>,
    onSelect: (EmergencyRequirement) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        EmergencySectionHeader(title = title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(GridGap),
        ) {
            items.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GridGap),
                ) {
                    pair.forEach { requirement ->
                        RequirementTile(
                            requirement = requirement,
                            onClick = { onSelect(requirement) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keeps a lone tile on the last row half-width rather than letting it
                    // stretch across and read as a different kind of control.
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RequirementTile(
    requirement: EmergencyRequirement,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.lg),
        color = sakhiSystemBackground(),
        modifier = modifier,
    ) {
        Row(
            // Figma `tile`: `px-14 py-13` with a 12 gap, so the 30dp badge puts the label
            // at x=56. One less than a card row's 16, because a tile has no chevron to
            // balance against on the far side.
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            EmergencyBadgeIcon(
                icon = requirement.icon(),
                color = requirement.accentColor(),
            )
            Text(
                text = EmergencyFormatting.requirementShortName(requirement),
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Figma `grid wrap`: 12 between tiles, both directions. */
private val GridGap = SakhiSpacing.space3

// ─────────────────────────────────────────────────────────────────────────────
// Step 2 — where exactly she is, in her own words
// ─────────────────────────────────────────────────────────────────────────────

/**
 * GPS gets someone to the building; this gets them to the door.
 *
 * What she types is withheld from everyone until she accepts a specific person — "2nd
 * floor washroom" is a location in its own right, so the server does not return it in
 * discovery results.
 */
// `CenterAlignedTopAppBar` is still an experimental Material3 API. Opted in here rather
// than module-wide, so the annotation stays next to the one call that needs it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmergencySpotStep(
    viewModel: EmergencyViewModel,
    requirement: EmergencyRequirement,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSpotRequired by remember { mutableStateOf(false) }

    // iOS is the only step in the flow that uses the *real* navigation bar: `Location` as
    // an inline title with Back and Next, un-hidden by `EmergencySheetContent` for this
    // step alone (`stepUsesNativeNavBar`). Android's flow lives in a bottom sheet with no
    // nav host per step, so the equivalent is a bar at the top of this step's own content.
    // Next moves up here with it; the bottom Ask button and Go back link are gone.
    Column(modifier = Modifier.fillMaxSize()) {
        // The flow's own header, like every other pushed step. This was the second screen
        // still on Material's `CenterAlignedTopAppBar`, which also carried the Next action
        // -- Figma `EA-05` moves that to a full-width CTA at the bottom labelled with what
        // it does ("Find a Sakhi") rather than where it goes.
        EmergencySheetNavBar(
            title = stringResource(R.string.emergency_spot_name),
            onBack = viewModel::backToRequirement,
        )

    // iOS is `VStack(alignment: .leading, spacing: 0)` with no outer horizontal padding:
    // every element carries `DS.Spacing.ml` = 20 itself. Android had an outer 20 on top of
    // the shared components' own padding, so the headers and the recent-spots card sat 36dp
    // in while the field and the area line stayed at 20. The uniform 12 between children
    // was wrong too -- iOS puts 12 above the area line and 28 above Recent Spots.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Figma `intro`: `pt-4 pb-16 px-20`, 4 between the question and the line under it.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5)
                .padding(top = SakhiSpacing.space1, bottom = SakhiSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        ) {
            Text(
                text = stringResource(R.string.emergency_spot_question),
                fontSize = 20.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
            )
            Text(
                text = stringResource(R.string.emergency_spot_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
            )
        }

        // iOS draws no border here at all: the field is
        // `.background(RoundedRectangle(cornerRadius: DS.Radius.systemCard).fill(DS.Colors.fill.opacity(0.35)))`
        // -- a soft fill and nothing else. Android used an `OutlinedTextField`, whose hard
        // grey outline is the one element on this step that does not exist on iOS.
        TextField(
            value = uiState.spotDraft,
            onValueChange = { value ->
                // `Constants.maxLocationLength` on `main`, enforced by truncation as iOS does.
                viewModel.onSpotDraftChanged(value.take(MAX_SPOT_LENGTH))
            },
            placeholder = { Text(stringResource(R.string.emergency_spot_placeholder)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            maxLines = 4,
            shape = RoundedCornerShape(SakhiRadius.lg),
            colors = TextFieldDefaults.colors(
                // Figma `input · spot`: a white card with a 1.5 brand-pink edge, not iOS's
                // soft grey fill. It is the one thing on this step she has to type into, and
                // on the blush sheet a 35%-grey fill had almost no edge at all.
                unfocusedContainerColor = sakhiSystemBackground(),
                focusedContainerColor = sakhiSystemBackground(),
                // Material's underline indicator has no iOS counterpart either.
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = SakhiUIColors.BRAND_PINK.toComposeColor(),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5)
                .border(
                    width = 1.5.dp,
                    color = SakhiUIColors.BRAND_PINK.toComposeColor(),
                    shape = RoundedCornerShape(SakhiRadius.lg),
                ),
        )

        // main showed "{spot} at {locationName}". The "at" prefix is what makes the two read
        // as one sentence once she has typed her spot. Android showed nothing here at all,
        // so she had no way to tell whether the app had her in the right place.
        uiState.areaDescription?.let { area ->
            Text(
                text = stringResource(R.string.emergency_spot_at_area, area),
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
                // Two lines: the resolved name leads with the building or street, and a
                // truncated address is worse than a short one because she cannot tell
                // whether the app has her in the right place.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = SakhiSpacing.space5)
                    .padding(top = SakhiSpacing.space3),
            )
        }

        val recentSpots by viewModel.recentSpots.collectAsStateWithLifecycle()
        if (recentSpots.isNotEmpty()) {
            // `main`'s "Recent Spots" section: clock icon, the name, and an x to forget it.
            EmergencySectionHeader(
                title = stringResource(R.string.emergency_recent_spots),
                // Figma puts a 22 gap between the field and this label; the header carries
                // 16 of it itself.
                topPadding = 22.dp,
            )
            EmergencyCard {
                recentSpots.forEachIndexed { index, spot ->
                    if (index > 0) EmergencyRowDivider(leadingInset = 58.dp)
                    EmergencyRow(
                        title = spot.replaceFirstChar { it.uppercase() },
                        // The flow's own badge, in Figma's info blue. A bare grey clock in
                        // a 34dp box was the only leading glyph in the whole flow that was
                        // not this shape, so these rows sat 4dp out of line with every
                        // other card.
                        leading = {
                            EmergencyBadgeIcon(
                                icon = Icons.Filled.Schedule,
                                color = SakhiTokens.SectionBlue,
                            )
                        },
                        modifier = Modifier.clickable { viewModel.useRecentSpot(spot) },
                        accessory = {
                            IconButton(onClick = { viewModel.forgetRecentSpot(spot) }) {
                                Icon(
                                    imageVector = Icons.Filled.Cancel,
                                    contentDescription = stringResource(R.string.emergency_forget_spot, spot),
                                    modifier = Modifier.size(18.dp),
                                    tint = sakhiSecondaryLabel().copy(alpha = 0.55f),
                                )
                            }
                        },
                    )
                }
            }
        }

        if (!uiState.hasLocationPermission) {
            Row(horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
                Icon(
                    imageVector = Icons.Filled.LocationOff,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.emergency_location_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = sakhiSecondaryLabel(),
                )
            }
        }

        // Figma `actions`: a 24 gap, then a full-width pink pill, then 10.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5)
                .padding(top = SakhiSpacing.space6, bottom = 10.dp),
        ) {
            EmergencyPrimaryButton(
                title = stringResource(R.string.emergency_find_a_sakhi),
                enabled = !uiState.isSubmitting,
            ) {
                // main: an empty field raised "Spot Name Required" and went no further. A
                // request with no spot label is the one thing GPS cannot make up for, so it
                // stays a hard stop.
                if (uiState.spotDraft.trim().isEmpty()) showSpotRequired = true
                else viewModel.confirmSpot()
            }
        }

        Spacer(modifier = Modifier.size(SakhiSpacing.space10))
    }
    }

    if (showSpotRequired) {
        SakhiAlertSheet(
            kind = SakhiAlertKind.Warning,
            title = stringResource(R.string.emergency_spot_required_title),
            message = stringResource(R.string.emergency_spot_required_body),
            primaryLabel = stringResource(R.string.emergency_ok),
            onPrimaryClick = { showSpotRequired = false },
            onDismissRequest = { showSpotRequired = false },
        )
    }
}

@Composable
internal fun RequirementChip(requirement: EmergencyRequirement) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SakhiSpacing.space3,
                vertical = SakhiSpacing.space2,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        ) {
            Icon(
                imageVector = requirement.icon(),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = EmergencyFormatting.requirementShortName(requirement),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** `Constants.maxLocationLength` on `main`, and `maxLocationLength` on iOS. */
private const val MAX_SPOT_LENGTH = 45
