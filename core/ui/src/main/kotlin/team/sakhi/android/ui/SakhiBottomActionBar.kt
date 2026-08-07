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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                    .background(logFill ?: accentColor, CircleShape)
                    .then(
                        if (logStrokeColor != null) {
                            Modifier.border(1.dp, logStrokeColor, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .combinedClickable(
                        enabled = (canLog || onLockedLogClick != null) && !isLogSaving,
                        // iOS wraps this button in a `Menu`, so a *tap* opens the quick-log
                        // menu and the sheet is reached from its "Other symptoms" row -- the
                        // `onTap` closure is never called in that branch
                        // (`HomeLogButton.body`). Android opened the sheet on tap and hid the
                        // menu behind a long-press, so the same tap did two different things
                        // on the two platforms and the menu was effectively undiscoverable.
                        // A partner without permission still never reaches the menu.
                        onClick = { if (canLog) showQuickLogMenu = true else onLockedLogClick?.invoke() },
                        onLongClick = { if (canLog) showQuickLogMenu = true },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isLogSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = logIconColor,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (hasLoggedForDate) Icons.Filled.Edit else Icons.Filled.Add,
                        contentDescription = stringResource(R.string.sakhi_action_bar_log_content_description),
                        tint = logIconColor,
                    )
                }
            }

            SakhiQuickLogMenu(
                expanded = showQuickLogMenu,
                onDismiss = { showQuickLogMenu = false },
                selectedDate = selectedDate,
                selectedFlow = selectedFlow,
                onPickFlow = { level ->
                    showQuickLogMenu = false
                    onQuickLogFlow(level)
                },
                onOtherSymptoms = {
                    showQuickLogMenu = false
                    onLogClick()
                },
            )
        }
    }
}


/**
 * The droplet run iOS draws for each flow level (`FlowLevelMenuIcon`): one droplet per
 * point on its scale, outlined normally and filled once the level is selected.
 */
@Composable
private fun QuickLogFlowDrops(level: FlowIntensity, isSelected: Boolean) {
    val count = when (level) {
        FlowIntensity.SPOTTING -> 1
        FlowIntensity.LIGHT -> 2
        FlowIntensity.MEDIUM -> 3
        FlowIntensity.HEAVY -> 4
    }
    // Brand-tinted rather than inheriting the default content colour, so the droplets
    // belong to the Sakhi menu instead of reading as stock dark Material glyphs. Unselected
    // levels sit back at a low alpha; the selected one is solid brand.
    val brand = MaterialTheme.colorScheme.primary
    val dropTint = if (isSelected) brand else brand.copy(alpha = 0.45f)
    Row(horizontalArrangement = Arrangement.spacedBy((-1).dp)) {
        repeat(count) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.WaterDrop else Icons.Outlined.WaterDrop,
                contentDescription = null,
                tint = dropTint,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/** iOS `logMenuDateTitle` — DateFormatter `"d MMM, EEEE"`. */
@Composable
private fun quickLogMenuDateTitle(date: LocalDate): String {
    val pattern = stringResource(R.string.sakhi_action_bar_menu_date_format)
    return remember(date, pattern) {
        java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
            .format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()))
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

/**
 * Quick-log menu chrome. iOS's `Menu` floats as a compact card over the action bar
 * rather than filling the width, so the panel is bounded instead of hugging its
 * longest row exactly, and carries a real shadow so it reads as attached to the
 * button it opened from.
 */
private val QuickLogMenuMinWidth = 220.dp
private val QuickLogMenuMaxWidth = 300.dp
private val QuickLogMenuCornerRadius = 24.dp
private val QuickLogRowInset = SakhiSpacing.space3
private val QuickLogMenuContentPadding = SakhiSpacing.space2
// Room for the shadow to fall outside the surface without the popup window clipping it.
private val QuickLogMenuShadowInset = 20.dp
// Kept small on purpose: bottom inset is what pushes the panel up the screen.
private val QuickLogMenuShadowInsetBottom = 8.dp
private val QuickLogMenuShadowElevation = 12.dp

/**
 * Sakhi's own quick-log menu.
 *
 * Material3's `DropdownMenu` supplies only the plumbing here (anchoring, outside-tap
 * dismissal, back handling). All of the chrome is overridden: brand pink accents, a soft
 * pink rim, generous [SakhiRadius.xxl] corners, and a selected row that fills with the
 * brand tint rather than relying on a tick glyph alone -- the stock Material panel
 * (4dp corners, `surfaceContainer` lavender fill) is what Karan rejected.
 *
 * Order is date-first, top down: the date this menu writes to, then the flow levels,
 * then the way out to the full sheet. iOS puts the date last; Karan asked for it on top
 * so the date you are about to log against is the first thing read, not a footnote
 * discovered after choosing.
 */
@Composable
private fun SakhiQuickLogMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    selectedDate: LocalDate,
    selectedFlow: FlowIntensity?,
    onPickFlow: (FlowIntensity?) -> Unit,
    onOtherSymptoms: () -> Unit,
) {
    val context = LocalContext.current
    val brand = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(QuickLogMenuCornerRadius)

    // Material's `DropdownMenu` is kept ONLY for its plumbing -- anchored positioning,
    // outside-tap dismissal and back handling. A hand-rolled `Popup` was tried first and
    // dismissed itself instantly: with `focusable = true` the ACTION_UP of the very tap
    // that opened it landed outside the popup bounds and triggered
    // dismiss-on-click-outside. `DropdownMenu` already solves that.
    //
    // Its chrome is switched off entirely (transparent container, no border, no
    // elevation) and the visible surface is drawn below instead, because Material's
    // `shadowElevation` renders a hard grey drop shadow that reads as dated next to the
    // rest of the app. `Modifier.shadow` with brand-tinted ambient/spot colours gives a
    // soft pink glow instead -- the menu lifts off the page rather than sitting on a
    // grey slab.
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = shape,
        containerColor = Color.Transparent,
        border = null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.widthIn(min = QuickLogMenuMinWidth, max = QuickLogMenuMaxWidth),
    ) {
        Box(
            modifier = Modifier
                // The inset is what makes the shadow visible at all. A popup window is
                // sized to its content, so anything drawn outside that content -- which
                // is exactly what a shadow is -- gets clipped by the window edge.
                // Verified by sampling pixels around the panel: without this the
                // surrounding pixels were pure #FFFFFF on all four sides, i.e. no shadow
                // reached the screen.
                //
                // Deliberately ASYMMETRIC. This popup is bottom-anchored and grows
                // upward, so every pixel of bottom inset lifts the whole panel away from
                // the button -- a uniform 24dp inset visibly shoved the menu up the
                // screen. `DropdownMenu`'s `offset` cannot claw that back: measured on
                // device, +24dp and -24dp both produced a pixel-identical result,
                // because the position provider ignores it for the flipped placement.
                // So the bottom keeps just enough room to read as a shadow while the
                // sides and top, where the panel meets the white calendar and separation
                // actually matters, get the full spread.
                .padding(
                    start = QuickLogMenuShadowInset,
                    end = QuickLogMenuShadowInset,
                    top = QuickLogMenuShadowInset,
                    bottom = QuickLogMenuShadowInsetBottom,
                )
                .shadow(
                    elevation = QuickLogMenuShadowElevation,
                    shape = shape,
                    clip = false,
                    // Neutral black at low alpha, not brand pink. A pink shadow over a
                    // white sheet is very close to invisible; a soft shadow reads as
                    // soft because of its spread and low opacity, not its hue.
                    ambientColor = Color.Black.copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = 0.22f),
                )
                .background(sakhiSystemBackground(), shape),
        ) {
            Column(modifier = Modifier.padding(QuickLogMenuContentPadding)) {
                // Date first, top down -- the date you are about to log against should be
                // the first thing read, not a footnote after the choice. iOS puts it last.
                QuickLogDateHeader(selectedDate = selectedDate)

                Spacer(modifier = Modifier.height(SakhiSpacing.space2))

                // Lightest first, heaviest last. iOS lists these heaviest-first; Karan
                // asked for the reverse so the scale climbs down the menu towards the
                // button you opened it from.
                listOf(
                    FlowIntensity.SPOTTING,
                    FlowIntensity.LIGHT,
                    FlowIntensity.MEDIUM,
                    FlowIntensity.HEAVY,
                ).forEach { level ->
                    val isSelected = selectedFlow == level
                    QuickLogFlowRow(
                        label = flowDisplayName(context, level),
                        level = level,
                        isSelected = isSelected,
                        brand = brand,
                        // Tapping the selected level again clears it, same as before.
                        onClick = { onPickFlow(if (isSelected) null else level) },
                    )
                }

                // One hairline, only where the menu actually changes purpose (quick flow
                // choice -> the full sheet). The previous design boxed every group in its
                // own divider, which is what made it look like a form rather than a menu.
                QuickLogMenuDivider(brand)

                QuickLogOtherSymptomsRow(brand = brand, onClick = onOtherSymptoms)
            }
        }
    }
}

/** The date these flow taps write to. First thing in the menu, as a soft brand chip. */
@Composable
private fun QuickLogDateHeader(selectedDate: LocalDate) {
    // Quiet grey caption, not a brand chip. This line only says WHICH day the taps
    // below write to -- it is context, not the thing being chosen, so highlighting it
    // in brand pink competed with the actual selection for attention.
    Row(
        modifier = Modifier.padding(
            horizontal = QuickLogRowInset,
            vertical = SakhiSpacing.space2,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        Icon(
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = sakhiSecondaryLabel(),
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = quickLogMenuDateTitle(selectedDate),
            style = MaterialTheme.typography.labelLarge,
            color = sakhiSecondaryLabel(),
        )
    }
}

@Composable
private fun QuickLogMenuDivider(brand: Color) {
    HorizontalDivider(
        thickness = 1.dp,
        color = brand.copy(alpha = 0.08f),
        modifier = Modifier.padding(
            horizontal = QuickLogRowInset,
            vertical = SakhiSpacing.space2,
        ),
    )
}

/**
 * One flow level. Selected rows fill with a soft brand tint and bold the label, rather
 * than depending on a trailing tick to carry the whole state.
 */
@Composable
private fun QuickLogFlowRow(
    label: String,
    level: FlowIntensity,
    isSelected: Boolean,
    brand: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SakhiRadius.lg))
            .background(if (isSelected) brand.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = QuickLogRowInset, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        QuickLogFlowDrops(level = level, isSelected = isSelected)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) brand else sakhiLabel(),
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = brand,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/** Way out to the full logging sheet. */
@Composable
private fun QuickLogOtherSymptomsRow(brand: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SakhiRadius.lg))
            .clickable(onClick = onClick)
            .padding(horizontal = QuickLogRowInset, vertical = SakhiSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
    ) {
        Icon(
            // The real SF Symbol iOS uses here (`circle.hexagonpath`), redrawn as a
            // vector -- Material's plain Hexagon is a different glyph.
            painter = painterResource(R.drawable.ic_circle_hexagonpath),
            contentDescription = null,
            tint = brand,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.sakhi_action_bar_other_symptoms),
            style = MaterialTheme.typography.bodyLarge,
            color = sakhiLabel(),
            modifier = Modifier.weight(1f),
        )
    }
}

