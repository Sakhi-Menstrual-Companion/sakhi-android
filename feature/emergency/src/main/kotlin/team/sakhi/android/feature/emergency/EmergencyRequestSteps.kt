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
        EmergencyHeader(
            title = stringResource(R.string.emergency_what_do_you_need),
            subtitle = stringResource(R.string.emergency_what_do_you_need_subtitle),
        )

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
@Composable
internal fun EmergencySpotStep(
    viewModel: EmergencyViewModel,
    requirement: EmergencyRequirement,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = SakhiSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        EmergencyHeader(
            title = stringResource(R.string.emergency_where_should_she_come),
            subtitle = stringResource(R.string.emergency_where_should_she_come_subtitle),
        )

        RequirementChip(requirement)

        OutlinedTextField(
            value = uiState.spotDraft,
            onValueChange = viewModel::onSpotDraftChanged,
            placeholder = { Text(stringResource(R.string.emergency_spot_placeholder)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            maxLines = 4,
            shape = RoundedCornerShape(SakhiRadius.lg),
            modifier = Modifier.fillMaxWidth(),
        )

        val recentSpots by viewModel.recentSpots.collectAsStateWithLifecycle()
        if (recentSpots.isNotEmpty()) {
            // `main`'s "Recent Spots" section: clock icon, the name, and an x to forget it.
            Text(
                text = stringResource(R.string.emergency_recent_spots).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            recentSpots.forEach { spot ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clickable { viewModel.useRecentSpot(spot) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = spot.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.forgetRecentSpot(spot) }) {
                        Icon(
                            imageVector = Icons.Filled.Cancel,
                            contentDescription = stringResource(R.string.emergency_forget_spot, spot),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
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

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = viewModel::confirmSpot,
            enabled = !uiState.isSubmitting,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.emergency_ask_for_help))
            }
        }

        TextButton(
            onClick = viewModel::backToRequirement,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.emergency_go_back))
        }

        Spacer(modifier = Modifier.size(SakhiSpacing.space4))
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
