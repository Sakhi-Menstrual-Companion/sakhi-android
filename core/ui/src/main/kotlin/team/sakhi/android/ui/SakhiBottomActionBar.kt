package team.sakhi.android.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.models.CyclePhase
import team.sakhi.models.FlowIntensity

/**
 * Real port of iOS `SakhiBottomActionBar` (`HomeActionBar.swift`) — shared between
 * Home's own day-detail view and the Calendar sheet on iOS (per that file's own doc
 * comment: "Shared bottom bar used by both the home day-detail view and the calendar
 * sheet"), so this lives in `core:ui` rather than `feature:home` so `feature:calendar`
 * can reuse the exact same component instead of a parallel, drift-prone copy.
 *
 * A circular calendar button (hidden via [showCalendarButton] = false for call sites
 * already inside the calendar, matching iOS's `CalendarBtn == EmptyView` convenience
 * init used by the calendar sheet itself), the [SakhiAskSakhiBar] capsule (sparkle
 * icon + rotating phase/partner-mode-aware placeholder copy, same 4-second interval
 * and the same exact placeholder strings per phase), and a circular log button (`+`
 * when no entry exists for the relevant date, pencil once logged; long-press for
 * iOS's `quickLogMenuContent` flow-level quick menu).
 */
@Composable
fun SakhiBottomActionBar(
    phase: CyclePhase,
    accentColor: Color,
    isPartnerMode: Boolean,
    canLog: Boolean,
    hasLoggedForDate: Boolean,
    isLogSaving: Boolean,
    selectedFlow: FlowIntensity?,
    onAskSakhiClick: () -> Unit,
    onLogClick: () -> Unit,
    onQuickLogFlow: (FlowIntensity?) -> Unit,
    showCalendarButton: Boolean = true,
    onCalendarClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCalendarButton && onCalendarClick != null) {
            IconButton(
                onClick = onCalendarClick,
                modifier = Modifier
                    .size(50.dp)
                    .background(accentColor.copy(alpha = 0.12f), CircleShape),
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = stringResource(R.string.sakhi_action_bar_calendar_content_description),
                    tint = accentColor,
                )
            }
        }

        SakhiAskSakhiBar(
            phase = phase,
            isPartnerMode = isPartnerMode,
            accentColor = accentColor,
            onTap = onAskSakhiClick,
            modifier = Modifier.weight(1f),
        )

        var showQuickLogMenu by remember { mutableStateOf(false) }

        Box {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(accentColor, CircleShape)
                    .combinedClickable(
                        enabled = canLog && !isLogSaving,
                        onClick = onLogClick,
                        onLongClick = { showQuickLogMenu = true },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isLogSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (hasLoggedForDate) Icons.Filled.Edit else Icons.Filled.Add,
                        contentDescription = stringResource(R.string.sakhi_action_bar_log_content_description),
                        tint = Color.White,
                    )
                }
            }

            DropdownMenu(
                expanded = showQuickLogMenu,
                onDismissRequest = { showQuickLogMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sakhi_action_bar_other_symptoms)) },
                    leadingIcon = { Icon(Icons.Filled.MoreHoriz, contentDescription = null) },
                    onClick = {
                        showQuickLogMenu = false
                        onLogClick()
                    },
                )
                HorizontalDivider()
                listOf(
                    FlowIntensity.HEAVY,
                    FlowIntensity.MEDIUM,
                    FlowIntensity.LIGHT,
                    FlowIntensity.SPOTTING,
                ).forEach { level ->
                    val isSelected = selectedFlow == level
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isSelected) {
                                    context.getString(R.string.sakhi_action_bar_flow_selected, level.displayName)
                                } else {
                                    level.displayName
                                },
                            )
                        },
                        onClick = {
                            showQuickLogMenu = false
                            onQuickLogFlow(if (isSelected) null else level)
                        },
                    )
                }
            }
        }
    }
}

/** Android port of iOS `HomeAskSakhiBar` — see [SakhiBottomActionBar] doc comment. */
@Composable
fun SakhiAskSakhiBar(
    phase: CyclePhase,
    isPartnerMode: Boolean,
    accentColor: Color,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val placeholders = remember(phase, isPartnerMode, context) {
        sakhiAskSakhiPlaceholders(context, phase, isPartnerMode)
    }
    var placeholderIndex by remember(placeholders) { mutableIntStateOf(0) }
    var iconQuarterTurns by remember(placeholders) { mutableIntStateOf(0) }
    val iconRotation by animateFloatAsState(
        targetValue = iconQuarterTurns * 90f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 300f),
        label = "askSakhiIconRotation",
    )

    LaunchedEffect(placeholders) {
        while (true) {
            delay(4_000)
            placeholderIndex = (placeholderIndex + 1) % placeholders.size
            iconQuarterTurns += 1
        }
    }

    Surface(
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.08f),
        onClick = onTap,
        modifier = modifier.heightIn(min = 50.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SakhiSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(accentColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = iconRotation },
                )
            }

            AnimatedContent(
                targetState = placeholders[placeholderIndex],
                transitionSpec = {
                    // Real port of iOS's asymmetric transition (HomeActionBar.swift's
                    // HomeAskSakhiBar): incoming text fades+slides in from below,
                    // outgoing text fades+slides out upward -- the two never sit at
                    // full opacity in the same place at once, unlike a plain crossfade.
                    (slideInVertically(animationSpec = tween(durationMillis = 350)) { height -> height / 3 } +
                        fadeIn(animationSpec = tween(durationMillis = 350)))
                        .togetherWith(
                            slideOutVertically(animationSpec = tween(durationMillis = 140)) { height -> -height / 4 } +
                                fadeOut(animationSpec = tween(durationMillis = 140)),
                        )
                },
                contentAlignment = Alignment.CenterStart,
                label = "askSakhiPlaceholder",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentColor.copy(alpha = 0.80f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Same exact copy as iOS's `HomeAskSakhiBar.placeholders(for:isPartnerMode:)` —
 * ported verbatim from `HomeActionBar.swift`, not paraphrased.
 */
fun sakhiAskSakhiPlaceholders(
    context: Context,
    phase: CyclePhase,
    isPartnerMode: Boolean,
): List<String> {
    fun listOfStrings(vararg ids: Int): List<String> = ids.map(context::getString)
    return if (isPartnerMode) {
        when (phase) {
            CyclePhase.MENSTRUAL -> listOfStrings(
                R.string.sakhi_ask_bar_partner_menstrual_1,
                R.string.sakhi_ask_bar_partner_menstrual_2,
                R.string.sakhi_ask_bar_partner_menstrual_3,
                R.string.sakhi_ask_bar_partner_menstrual_4,
            )
            CyclePhase.FOLLICULAR -> listOfStrings(
                R.string.sakhi_ask_bar_partner_follicular_1,
                R.string.sakhi_ask_bar_partner_follicular_2,
                R.string.sakhi_ask_bar_partner_follicular_3,
                R.string.sakhi_ask_bar_partner_follicular_4,
            )
            CyclePhase.OVULATION -> listOfStrings(
                R.string.sakhi_ask_bar_partner_ovulation_1,
                R.string.sakhi_ask_bar_partner_ovulation_2,
                R.string.sakhi_ask_bar_partner_ovulation_3,
                R.string.sakhi_ask_bar_partner_ovulation_4,
            )
            CyclePhase.LUTEAL -> listOfStrings(
                R.string.sakhi_ask_bar_partner_luteal_1,
                R.string.sakhi_ask_bar_partner_luteal_2,
                R.string.sakhi_ask_bar_partner_luteal_3,
                R.string.sakhi_ask_bar_partner_luteal_4,
            )
            CyclePhase.DELAYED -> listOfStrings(
                R.string.sakhi_ask_bar_partner_delayed_1,
                R.string.sakhi_ask_bar_partner_delayed_2,
                R.string.sakhi_ask_bar_partner_delayed_3,
                R.string.sakhi_ask_bar_partner_delayed_4,
            )
            CyclePhase.UNKNOWN -> listOfStrings(
                R.string.sakhi_ask_bar_partner_unknown_1,
                R.string.sakhi_ask_bar_partner_unknown_2,
                R.string.sakhi_ask_bar_partner_unknown_3,
                R.string.sakhi_ask_bar_partner_unknown_4,
            )
        }
    } else {
        when (phase) {
            CyclePhase.MENSTRUAL -> listOfStrings(
                R.string.sakhi_ask_bar_self_menstrual_1,
                R.string.sakhi_ask_bar_self_menstrual_2,
                R.string.sakhi_ask_bar_self_menstrual_3,
                R.string.sakhi_ask_bar_self_menstrual_4,
            )
            CyclePhase.FOLLICULAR -> listOfStrings(
                R.string.sakhi_ask_bar_self_follicular_1,
                R.string.sakhi_ask_bar_self_follicular_2,
                R.string.sakhi_ask_bar_self_follicular_3,
                R.string.sakhi_ask_bar_self_follicular_4,
            )
            CyclePhase.OVULATION -> listOfStrings(
                R.string.sakhi_ask_bar_self_ovulation_1,
                R.string.sakhi_ask_bar_self_ovulation_2,
                R.string.sakhi_ask_bar_self_ovulation_3,
                R.string.sakhi_ask_bar_self_ovulation_4,
            )
            CyclePhase.LUTEAL -> listOfStrings(
                R.string.sakhi_ask_bar_self_luteal_1,
                R.string.sakhi_ask_bar_self_luteal_2,
                R.string.sakhi_ask_bar_self_luteal_3,
                R.string.sakhi_ask_bar_self_luteal_4,
            )
            CyclePhase.DELAYED -> listOfStrings(
                R.string.sakhi_ask_bar_self_delayed_1,
                R.string.sakhi_ask_bar_self_delayed_2,
                R.string.sakhi_ask_bar_self_delayed_3,
                R.string.sakhi_ask_bar_self_delayed_4,
            )
            CyclePhase.UNKNOWN -> listOfStrings(
                R.string.sakhi_ask_bar_self_unknown_1,
                R.string.sakhi_ask_bar_self_unknown_2,
                R.string.sakhi_ask_bar_self_unknown_3,
                R.string.sakhi_ask_bar_self_unknown_4,
            )
        }
    }
}
