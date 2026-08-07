package team.sakhi.android.ui

import android.content.Context
import team.sakhi.models.FlowIntensity

/**
 * The flow-level wording Sakhi actually shows, everywhere it shows it.
 *
 * iOS names these from its own local `FlowLevel.displayName`
 * (`Features/Logging/Models/LoggingModels.swift`) — "Spotting", "Slight", "Moderate",
 * "Heavy" — which is deliberately *not* the shared KMM `FlowIntensity.displayName`
 * ("Light", "Medium"). Android had three separate copies of this decision: the logging
 * sheet used the iOS wording, while the quick-log menu and Home's "How you feel" chip
 * both fell back to the KMM property, so the same logged value read "Moderate" on one
 * screen and "Medium" on the next. One source now, so they cannot drift again.
 */
fun flowDisplayName(context: Context, level: FlowIntensity): String =
    context.getString(flowDisplayNameRes(level))

/** [flowDisplayName] as a resource id, for call sites that already have a `Context`. */
fun flowDisplayNameRes(level: FlowIntensity): Int = when (level) {
    FlowIntensity.SPOTTING -> R.string.sakhi_flow_spotting
    FlowIntensity.LIGHT -> R.string.sakhi_flow_light
    FlowIntensity.MEDIUM -> R.string.sakhi_flow_medium
    FlowIntensity.HEAVY -> R.string.sakhi_flow_heavy
}
