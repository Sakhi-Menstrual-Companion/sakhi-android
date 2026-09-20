package team.sakhi.android.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.models.CyclePhase
import team.sakhi.models.FlowIntensity
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel

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
    /**
     * Tapped when a care partner without logging permission taps the log button.
     *
     * Without this the button was simply disabled: nothing happened, with no
     * explanation and no way to ask. iOS instead opens `LogPermissionSheet` so the
     * partner can request access from the owner. Null keeps the old inert behaviour for
     * any caller that has no request flow to offer.
     */
    onLockedLogClick: (() -> Unit)? = null,
    hasLoggedForDate: Boolean,
    isLogSaving: Boolean,
    selectedFlow: FlowIntensity?,
    /**
     * The date the quick-log rows write to. iOS spells it out as a disabled row at the
     * foot of the menu (`logMenuDateTitle`) so a quick log made from the calendar can
     * never be mistaken for a log against today.
     */
    selectedDate: LocalDate,
    onAskSakhiClick: () -> Unit,
    onLogClick: () -> Unit,
    onQuickLogFlow: (FlowIntensity?) -> Unit,
    showCalendarButton: Boolean = true,
    onCalendarClick: (() -> Unit)? = null,
    /**
     * Replaces the calendar icon in the leading slot.
     *
     * iOS declares this slot as `@ViewBuilder let calendarButton`, defaulting to
     * `EmptyView()`, and the calendar sheet fills it with the same leading action Home
     * uses for Stay With Me. Android had a hardcoded calendar icon there.
     */
    leadingSlot: (@Composable () -> Unit)? = null,
    /**
     * Fill for the circular log button, kept separate from [accentColor] because iOS
     * keeps them separate: its action bar takes `logFill: cardFill` and
     * `logIconColor: standardAccent` as two independent values.
     *
     * Collapsing both onto [accentColor] broke the most common screen state in the app.
     * On a period day the phase primary is white (iOS forces that so text reads on the
     * saturated background), so the button was drawn white and its glyph was hardcoded
     * white too — the log button rendered as a blank white circle with no icon. iOS fills
     * it with the phase *surface* (`#C7386A`) and puts the white glyph on top.
     *
     * Null keeps the previous `accentColor` fill, so non-phase-tinted callers are
     * unaffected.
     */
    logFill: Color? = null,
    logIconColor: Color = Color.White,
    /**
     * Rim around the log button — iOS's `logStrokeColor: standardAccent.opacity(0.14)`.
     *
     * It matters most exactly where [logFill] matters: on a period day the fill is the
     * phase surface, which is also the page background, so without a rim the button has
     * no edge and reads as a floating glyph rather than a control.
     */
    logStrokeColor: Color? = null,
) {
    val haptics = LocalHapticFeedback.current
    var trayOpen by remember { mutableStateOf(false) }
    // Another day, a saved log or a lost permission ends an open quick log.
    LaunchedEffect(selectedDate, hasLoggedForDate, canLog) { trayOpen = false }
    val tray by animateFloatAsState(
        targetValue = if (trayOpen) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
        label = "quickLogTray",
    )
    BackHandler(enabled = trayOpen) { trayOpen = false }
    val logButtonFill = logFill ?: accentColor

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tray: everything beside the log button slides out to the left while it opens.
            val slideAside = Modifier.graphicsLayer {
                translationX = -tray * (size.width + 24.dp.toPx())
                alpha = (1f - tray * 1.3f).coerceIn(0f, 1f)
            }
            if (leadingSlot != null) {
                Box(modifier = slideAside) { leadingSlot() }
            } else if (showCalendarButton && onCalendarClick != null) {
                IconButton(
                    onClick = onCalendarClick,
                    modifier = slideAside
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
                modifier = Modifier.weight(1f).then(slideAside),
            )

            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(logButtonFill, CircleShape)
                    .then(
                        if (logStrokeColor != null) {
                            Modifier.border(1.dp, logStrokeColor, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .combinedClickable(
                        enabled = (canLog || onLockedLogClick != null) && !isLogSaving,
                        // A tap on "+" opens the quick log, as iOS's `Menu` does; a partner
                        // without permission is offered the request instead, and a day that
                        // already has a log opens the full sheet (the pencil), since the
                        // quick log would hide what is already recorded. While the tray is
                        // open this button is "more symptoms", the way into the full sheet.
                        onClick = {
                            when {
                                trayOpen -> {
                                    trayOpen = false
                                    onLogClick()
                                }
                                !canLog -> onLockedLogClick?.invoke()
                                hasLoggedForDate -> onLogClick()
                                else -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    trayOpen = true
                                }
                            }
                        },
                        onLongClick = { if (canLog && !hasLoggedForDate) trayOpen = true },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val glyph = when {
                    isLogSaving -> LogGlyph.Saving
                    trayOpen -> LogGlyph.More
                    hasLoggedForDate -> LogGlyph.Edit
                    else -> LogGlyph.Add
                }
                AnimatedContent(
                    targetState = glyph,
                    transitionSpec = {
                        (fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.5f)) togetherWith
                            (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.5f))
                    },
                    label = "logButtonGlyph",
                ) { shownGlyph ->
                    when (shownGlyph) {
                        LogGlyph.Saving -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = logIconColor,
                            strokeWidth = 2.dp,
                        )
                        LogGlyph.More -> Icon(
                            painter = painterResource(R.drawable.ic_circle_hexagonpath),
                            contentDescription = stringResource(R.string.sakhi_quick_log_more),
                            tint = logIconColor,
                            modifier = Modifier.size(22.dp),
                        )
                        LogGlyph.Edit -> Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.sakhi_action_bar_log_content_description),
                            tint = logIconColor,
                        )
                        LogGlyph.Add -> Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.sakhi_action_bar_log_content_description),
                            tint = logIconColor,
                        )
                    }
                }
            }
        }

        if (tray > 0.002f) {
            QuickLogTrayLayer(
                progress = tray,
                open = trayOpen,
                accent = accentColor,
                selectedFlow = selectedFlow,
                onPick = onQuickLogFlow,
                onClose = { trayOpen = false },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private enum class LogGlyph { Add, Edit, More, Saving }

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
