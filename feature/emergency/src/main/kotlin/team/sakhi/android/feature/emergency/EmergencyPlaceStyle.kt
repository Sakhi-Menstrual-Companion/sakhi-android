package team.sakhi.android.feature.emergency

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import team.sakhi.android.designsystem.AppleSystemColors
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.design.SakhiUIColors
import team.sakhi.models.EmergencyFormatting
import team.sakhi.emergency.EmergencySafePlace
import team.sakhi.emergency.EmergencySafePlaceKind

/**
 * Port of iOS `EmergencyPlaceStyle.swift`.
 *
 * The words and the numbers are SakhiCore's — `chipLabel`, `rowLabel`, the radius, the
 * walking time — so the two apps cannot describe the same place differently. Only the
 * glyph and the tint live here, because those are per-platform icon sets.
 */
val EmergencySafePlaceKind.icon: ImageVector
    get() = when (this) {
        EmergencySafePlaceKind.HOSPITAL -> Icons.Filled.Apartment
        EmergencySafePlaceKind.PHARMACY -> Icons.Filled.MedicalServices
        EmergencySafePlaceKind.POLICE -> Icons.Filled.Shield
        EmergencySafePlaceKind.WASHROOM -> Icons.Filled.Wc
        EmergencySafePlaceKind.VENDING_MACHINE -> Icons.Filled.Inventory2
        else -> Icons.Filled.Place
    }

/**
 * The badge tint, matching iOS. Distinct enough that the list reads as four groups at a
 * glance, which is the whole point of the chips above it.
 */
val EmergencySafePlaceKind.tint: Color
    @Composable get() = when (this) {
        EmergencySafePlaceKind.HOSPITAL -> SakhiUIColors.BRAND_PINK.toComposeColor()
        EmergencySafePlaceKind.PHARMACY -> AppleSystemColors.green
        EmergencySafePlaceKind.POLICE -> AppleSystemColors.blue
        EmergencySafePlaceKind.WASHROOM -> AppleSystemColors.purple
        // Warm, and distinct from the pink hospital badge beside it: this is the row that
        // solves the problem outright, so it should not read as a variant of anything.
        EmergencySafePlaceKind.VENDING_MACHINE -> AppleSystemColors.orange
        else -> SakhiUIColors.BRAND_PINK.toComposeColor()
    }

/** "Hospital · 303 m" — the kind and how far, in one line under the name. */
val EmergencySafePlace.kindAndDistance: String
    get() = "${kind.rowLabel} · $formattedDistance"

/**
 * "About 4 min", formatted by the shared code rather than built here.
 *
 * Never better than "about": a straight-line distance cannot know about the road, and this
 * is not the screen to imply it does.
 */
val EmergencySafePlace.onFootDescription: String
    get() = EmergencyFormatting.walkingTime(walkingMinutes)
