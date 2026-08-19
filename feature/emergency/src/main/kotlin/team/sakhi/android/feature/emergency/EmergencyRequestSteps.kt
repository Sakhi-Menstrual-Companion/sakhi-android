package team.sakhi.android.feature.emergency

import androidx.compose.foundation.background
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import team.sakhi.android.designsystem.SakhiRadius
import androidx.compose.ui.text.style.TextOverflow
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.models.EmergencyFormatting
import team.sakhi.models.EmergencyRequirement

// ─────────────────────────────────────────────────────────────────────────────
// Step 1 — what does she need
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmergencyRequirementStep(
    viewModel: EmergencyViewModel,
    startOnResponderInbox: Boolean = false,
) {
    // Opens straight onto the inbox when she arrived from a nearby-request push: she was
    // asked to help, so asking her what *she* needs would be the wrong first screen.
    var showResponderInbox by remember { mutableStateOf(startOnResponderInbox) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        // iOS: `EmergencySheetTitle(title: "Select Requirement")` -- centred, no subtitle.
        EmergencySheetTitle(title = stringResource(R.string.emergency_select_requirement))

        RequirementSection(
            title = stringResource(R.string.emergency_section_right_now),
            items = EmergencyRequirement.urgent,
            onSelect = viewModel::chooseRequirement,
        )
        RequirementSection(
            title = stringResource(R.string.emergency_section_something_else),
            items = EmergencyRequirement.other,
            onSelect = viewModel::chooseRequirement,
        )

        // The two secondary pills that used to sit here are gone, matching iOS
        // `EmergencyRequirementView.swift`, whose own comment records the decision: the
        // nearby washroom/hospital/police link and the way into the helper inbox were both
        // removed so this screen is a requirement picker and nothing else.
        //
        // Worth knowing what went with them, and it is the same on both platforms now: the
        // responder inbox has no in-app route at all. A push notification is the only thing
        // that opens it, so a helper who dismisses one cannot get back to the request.
        // `startOnResponderInbox` is that push route, and it still works.

        Spacer(modifier = Modifier.size(SakhiSpacing.space8))
    }

    if (showResponderInbox) {
        ModalBottomSheet(
            onDismissRequest = { showResponderInbox = false },
            sheetState = sheetState,
        ) {
            EmergencyResponderInbox(viewModel = viewModel)
        }
    }
}

@Composable
private fun RequirementSection(
    title: String,
    items: List<EmergencyRequirement>,
    onSelect: (EmergencyRequirement) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        items.forEach { requirement ->
            RequirementRow(requirement = requirement, onClick = { onSelect(requirement) })
        }
    }
}

@Composable
private fun RequirementRow(requirement: EmergencyRequirement, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            // `main`'s RequirementTableViewCell: a CIRCULAR badge filled with the
            // requirement's own colour at 0.2 alpha, glyph in that colour at full strength.
            val accent = requirement.accentColor()
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = requirement.icon(),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = EmergencyFormatting.requirementShortName(requirement),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.emergency_location_title)) },
            navigationIcon = {
                TextButton(onClick = viewModel::backToRequirement) {
                    Text(stringResource(R.string.emergency_back))
                }
            },
            actions = {
                TextButton(
                    onClick = {
                        // main: an empty field raised "Spot Name Required" and went no
                        // further. A request with no spot label is the one thing GPS cannot
                        // make up for, so it stays a hard stop.
                        if (uiState.spotDraft.trim().isEmpty()) showSpotRequired = true
                        else viewModel.confirmSpot()
                    },
                    enabled = !uiState.isSubmitting,
                ) {
                    Text(
                        text = stringResource(R.string.emergency_next),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
            ),
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        EmergencySectionHeader(title = stringResource(R.string.emergency_spot_name))

        OutlinedTextField(
            value = uiState.spotDraft,
            onValueChange = { value ->
                // `Constants.maxLocationLength` on `main`, enforced by truncation as iOS does.
                viewModel.onSpotDraftChanged(value.take(MAX_SPOT_LENGTH))
            },
            placeholder = { Text(stringResource(R.string.emergency_spot_placeholder)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            maxLines = 4,
            shape = RoundedCornerShape(SakhiRadius.lg),
            modifier = Modifier.fillMaxWidth(),
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
            )
        }

        val recentSpots by viewModel.recentSpots.collectAsStateWithLifecycle()
        if (recentSpots.isNotEmpty()) {
            // `main`'s "Recent Spots" section: clock icon, the name, and an x to forget it.
            EmergencySectionHeader(title = stringResource(R.string.emergency_recent_spots))
            EmergencyCard {
                recentSpots.forEachIndexed { index, spot ->
                    if (index > 0) EmergencyRowDivider(leadingInset = 58.dp)
                    EmergencyRow(
                        title = spot.replaceFirstChar { it.uppercase() },
                        leading = {
                            Box(
                                modifier = Modifier.size(34.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = sakhiSecondaryLabel(),
                                )
                            }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.size(SakhiSpacing.space10))
    }
    }

    if (showSpotRequired) {
        AlertDialog(
            onDismissRequest = { showSpotRequired = false },
            title = { Text(stringResource(R.string.emergency_spot_required_title)) },
            text = { Text(stringResource(R.string.emergency_spot_required_body)) },
            confirmButton = {
                TextButton(onClick = { showSpotRequired = false }) {
                    Text(stringResource(R.string.emergency_ok))
                }
            },
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
