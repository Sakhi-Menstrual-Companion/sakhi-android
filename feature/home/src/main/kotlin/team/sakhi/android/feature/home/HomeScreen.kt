package team.sakhi.android.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.phasePrimaryColor
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.feature.logging.LoggingViewModel
import team.sakhi.android.feature.recommendations.RecommendationFoodUi
import team.sakhi.android.feature.recommendations.RecommendationsViewModel
import team.sakhi.cycle.CalendarMarker
import team.sakhi.date.DateConverter
import team.sakhi.logging.LogTokenEncoder
import team.sakhi.logging.Mood
import team.sakhi.logging.Symptom
import team.sakhi.models.CyclePhase
import team.sakhi.models.FlowIntensity
import team.sakhi.models.PeriodLog
import team.sakhi.sync.SyncRuntimeState

/**
 * Phase 1 real home screen: renders shared cycle, permission, and sync state from
 * KMM. Entry points mirror iOS's actual Home top bar (hamburger -> Profile sheet,
 * person icon -> Be Her Sakhi/Care sheet — see `HomeView.swift` ~line 647-668) and
 * bottom action bar (calendar toggle, "Ask Sakhi" search capsule, log button — see
 * `HomeActionBar.swift`'s `SakhiBottomActionBar`). iOS has no tab bar; Android must
 * not invent one — parity means these exact entry points, not a Material bottom
 * nav. Presentation style (iOS uses sheets) and full visual layout are a later
 * pass; these are plain pushes on the nested Home nav graph for now.
 */
@Composable
fun HomeScreen(
    onOpenProfile: () -> Unit = {},
    onOpenCare: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onQuickLogClick: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
    recommendationsViewModel: RecommendationsViewModel = koinViewModel(),
    quickLogViewModel: LoggingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recoState by recommendationsViewModel.uiState.collectAsStateWithLifecycle()
    val quickLogUiState by quickLogViewModel.uiState.collectAsStateWithLifecycle()
    val accentColor = phasePrimaryColor(uiState.phase)
    val hapticManager = koinInject<AndroidHapticManager>()

    // `refresh()`'s own triggers (session/syncState/partnerSnapshot) don't fire
    // on a plain nav pop back from the logging sheet, so `hasLoggedToday` would
    // otherwise go stale after a save. Re-check on every resume instead.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshToday()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Port of iOS's `HomeLogButton.isSaving` -- a quick-log flow-level tap (see
    // `QuickLogMenuContent`) saves through the same `LoggingViewModel` the full
    // sheet uses, so `hasLoggedToday` needs a refresh once that save actually
    // completes (its own `isSaving` flips true -> false), not just on resume.
    var wasQuickLogSaving by remember { mutableStateOf(false) }
    LaunchedEffect(quickLogUiState.isSaving) {
        if (wasQuickLogSaving && !quickLogUiState.isSaving) {
            viewModel.refreshToday()
        }
        wasQuickLogSaving = quickLogUiState.isSaving
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenProfile) {
                Icon(Icons.Filled.Menu, contentDescription = "Profile")
            }
            Text(
                text = if (uiState.session?.isViewingOwnData == false) "Partner Home" else "Home",
                style = MaterialTheme.typography.headlineMedium,
            )
            IconButton(onClick = onOpenCare) {
                Icon(Icons.Filled.People, contentDescription = "Be Her Sakhi")
            }
        }
        Text(
            text = uiState.session?.let(::sessionSummary) ?: "Waiting for session",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
        ) {
            val canShowHero = uiState.session?.isViewingOwnData == true || uiState.canViewPredictions
            if (!uiState.isLoadingCycle && canShowHero) {
                HeroSection(uiState = uiState, accentColor = accentColor)
            }

            StateChip(
                label = syncLabel(uiState.syncState),
                tint = syncTint(uiState.syncState, accentColor),
            )

            uiState.partnerSnapshotRevision?.let { revision ->
                Text(
                    text = buildString {
                        append("Partner snapshot revision ")
                        append(revision)
                        uiState.partnerSnapshotRefreshedAt?.let {
                            append(" • refreshed ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            uiState.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val isPartnerMode = uiState.session?.isViewingOwnData == false

            // Branching matches iOS `HomeDayDetailGlassView.body` exactly:
            // partner+empty -> partnerNoDataCard; empty (own) -> learningPhaseCards;
            // partner+data -> checklist/loggedDetails/nutrition/headsUp/phaseInfo;
            // own+data -> loggedDetails/nutrition/cycleDetails/phaseInfo.
            if (!uiState.isLoadingCycle) {
                if (isPartnerMode && !uiState.hasCycleData) {
                    PartnerNoDataCard()
                } else if (!uiState.hasCycleData) {
                    EmptyStateCard(accentColor = accentColor)
                    LearningPhaseCards()
                } else if (isPartnerMode) {
                    val checklistViewModel: PartnerChecklistViewModel = koinViewModel()
                    val checklistState by checklistViewModel.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(uiState.phase, uiState.dayInCycle) {
                        checklistViewModel.loadOrGenerate(uiState.phase, uiState.dayInCycle ?: 1)
                    }
                    PartnerChecklistCard(
                        state = checklistState,
                        onToggle = checklistViewModel::toggle,
                        onRetry = { checklistViewModel.retry(uiState.phase, uiState.dayInCycle ?: 1) },
                        accentColor = accentColor,
                    )
                    LoggedDetailsCard(
                        log = uiState.todayLog,
                        isPartnerMode = true,
                        accentColor = accentColor,
                        onClick = { /* Activity/history sheet -- ActivityLogScreen, reachable from Profile today */ },
                    )
                    if (recoState.canViewPhaseRecommendations) {
                        NutritionCard(
                            foods = recoState.eatMoreFoods,
                            isLoading = recoState.isLoading,
                            accentColor = accentColor,
                        )
                    }
                    partnerHeadsUpText(uiState.phase, uiState.dayInCycle, uiState.daysUntilNextPeriod)?.let { headsUp ->
                        PartnerHeadsUpCard(text = headsUp, accentColor = accentColor)
                    }
                    PhaseInfoCard(phase = uiState.phase, isPartnerMode = true, accentColor = accentColor)
                } else {
                    // Order matches iOS `HomeDayDetailGlassView.body`'s own-data
                    // branch exactly: loggedDetails -> nutrition -> cycleDetails
                    // -> phaseInfo (`SakhiInsightCard` stays last -- it isn't one
                    // of that exact 4-card list, kept where it already was).
                    LoggedDetailsCard(
                        log = uiState.todayLog,
                        isPartnerMode = false,
                        accentColor = accentColor,
                        onClick = { /* Activity/history sheet -- ActivityLogScreen, reachable from Profile today */ },
                    )
                    if (recoState.canViewPhaseRecommendations) {
                        NutritionCard(
                            foods = recoState.eatMoreFoods,
                            isLoading = recoState.isLoading,
                            accentColor = accentColor,
                        )
                    }
                    uiState.currentCycle?.let { cycle ->
                        CycleDetailsCard(
                            cycle = cycle,
                            dayInCycle = uiState.dayInCycle ?: 1,
                            cycleLength = uiState.cycleLength ?: 28,
                            accentColor = accentColor,
                        )
                    }
                    PhaseInfoCard(phase = uiState.phase, isPartnerMode = false, accentColor = accentColor)
                    SakhiInsightCard(
                        insight = recoState.aiInsight,
                        isLoading = recoState.isLoading,
                        isPartnerMode = false,
                        accentColor = accentColor,
                    )
                }
            }
        }

        HomeBottomActionBar(
            phase = uiState.phase,
            accentColor = accentColor,
            isPartnerMode = uiState.session?.isViewingOwnData == false,
            canLog = uiState.canLogPeriod,
            hasLoggedToday = uiState.hasLoggedToday,
            isLogSaving = quickLogUiState.isSaving,
            selectedFlow = quickLogUiState.selectedFlow,
            onCalendarClick = {
                hapticManager.selection()
                onOpenCalendar()
            },
            onAskSakhiClick = {
                hapticManager.selection()
                onOpenChat()
            },
            onLogClick = {
                hapticManager.impact(HapticImpact.MEDIUM)
                onQuickLogClick()
            },
            onQuickLogFlow = { level ->
                hapticManager.selection()
                quickLogViewModel.onFlowSelected(level)
                quickLogViewModel.save()
            },
        )
    }
}

/**
 * Real port of iOS `SakhiBottomActionBar` (`HomeActionBar.swift`): a circular
 * calendar button, the `HomeAskSakhiBar` capsule (sparkle icon + rotating
 * phase/partner-mode-aware placeholder copy, same 4-second interval and the same
 * exact placeholder strings per phase), and a circular log button (`+` when no
 * entry exists today, pencil once logged; long-press for iOS's
 * `quickLogMenuContent` flow-level quick menu, see [QuickLogMenuContent]).
 *
 * Known gaps vs iOS, left as comments rather than silently diverged:
 * - iOS uses a spring/easing animation for the placeholder swap and a 90°
 *   sparkle-icon rotation on each change; here it's a plain `AnimatedContent`
 *   fade, which reads calmer but not identical.
 */
@Composable
private fun HomeBottomActionBar(
    phase: CyclePhase,
    accentColor: Color,
    isPartnerMode: Boolean,
    canLog: Boolean,
    hasLoggedToday: Boolean,
    isLogSaving: Boolean,
    selectedFlow: FlowIntensity?,
    onCalendarClick: () -> Unit,
    onAskSakhiClick: () -> Unit,
    onLogClick: () -> Unit,
    onQuickLogFlow: (FlowIntensity?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onCalendarClick,
            modifier = Modifier
                .size(50.dp)
                .background(accentColor.copy(alpha = 0.12f), CircleShape),
        ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = "Calendar", tint = accentColor)
        }

        HomeAskSakhiBar(
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
                        imageVector = if (hasLoggedToday) Icons.Filled.Edit else Icons.Filled.Add,
                        contentDescription = "Log",
                        tint = Color.White,
                    )
                }
            }

            DropdownMenu(
                expanded = showQuickLogMenu,
                onDismissRequest = { showQuickLogMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Other symptoms  ›") },
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
                        text = { Text(if (isSelected) "${level.displayName}  ✓" else level.displayName) },
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

/** Android port of iOS `HomeAskSakhiBar` — see [HomeBottomActionBar] doc comment. */
@Composable
private fun HomeAskSakhiBar(
    phase: CyclePhase,
    isPartnerMode: Boolean,
    accentColor: Color,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val placeholders = remember(phase, isPartnerMode) { askSakhiPlaceholders(phase, isPartnerMode) }
    var placeholderIndex by remember(placeholders) { mutableIntStateOf(0) }

    LaunchedEffect(placeholders) {
        while (true) {
            delay(4_000)
            placeholderIndex = (placeholderIndex + 1) % placeholders.size
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
                    modifier = Modifier.size(16.dp),
                )
            }

            AnimatedContent(
                targetState = placeholders[placeholderIndex],
                transitionSpec = { fadeIn() togetherWith fadeOut() },
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
private fun askSakhiPlaceholders(phase: CyclePhase, isPartnerMode: Boolean): List<String> {
    if (isPartnerMode) {
        return when (phase) {
            CyclePhase.MENSTRUAL -> listOf(
                "How's she feeling today?",
                "What can I do for her right now?",
                "What does she need during her period?",
                "How can we help her through this?",
            )
            CyclePhase.FOLLICULAR -> listOf(
                "How's her energy this week?",
                "What does she need from me now?",
                "What's good for her this phase?",
                "How's she doing this week?",
            )
            CyclePhase.OVULATION -> listOf(
                "How's she feeling right now?",
                "What does she need today?",
                "What's she going through this week?",
                "How can I be there for her today?",
            )
            CyclePhase.LUTEAL -> listOf(
                "Why might she seem off today?",
                "What does she need right now?",
                "How can I help her this week?",
                "What's she going through?",
            )
            CyclePhase.DELAYED -> listOf(
                "How's she doing with the delay?",
                "What does she need from me?",
                "Is she okay?",
                "How can I support her right now?",
            )
            CyclePhase.UNKNOWN -> listOf(
                "How's she doing today?",
                "What does she need?",
                "What can we figure out for her?",
                "Ask about her cycle...",
            )
        }
    }
    return when (phase) {
        CyclePhase.MENSTRUAL -> listOf(
            "How are you managing today?",
            "Ask about cramp relief...",
            "What helps with fatigue?",
            "Talk to Sakhi about your flow...",
        )
        CyclePhase.FOLLICULAR -> listOf(
            "What should I eat this week?",
            "How's your energy level?",
            "Ask about cycle nutrition...",
            "Plan something for your glow-up phase...",
        )
        CyclePhase.OVULATION -> listOf(
            "Am I in my fertile window?",
            "Ask about ovulation signs...",
            "What's my body doing right now?",
            "Tips for your peak energy day...",
        )
        CyclePhase.LUTEAL -> listOf(
            "Why do I feel this way?",
            "Ask about PMS remedies...",
            "What helps with bloating?",
            "Talk to Sakhi about your mood...",
        )
        CyclePhase.DELAYED -> listOf(
            "Why is my period late?",
            "Should I be worried?",
            "Ask Sakhi about irregular cycles...",
            "What could cause a delay?",
        )
        CyclePhase.UNKNOWN -> listOf(
            "Ask Sakhi anything...",
            "How do I start tracking?",
            "Tell me about my cycle phases...",
            "What should I log today?",
        )
    }
}

@Composable
private fun StateChip(
    label: String,
    tint: Color,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        modifier = Modifier
            .background(
                color = tint.copy(alpha = 0.12f),
                shape = RoundedCornerShape(SakhiRadius.full),
            )
            .padding(horizontal = SakhiSpacing.space3, vertical = SakhiSpacing.space2),
    )
}

private fun sessionSummary(session: team.sakhi.session.SessionContext): String = when {
    session.isViewingOwnData -> "Showing ${session.userName}'s cycle"
    else -> "Viewing ${session.activeRole.displayName.lowercase()} access for user ${session.targetUserId}"
}

private fun syncLabel(syncState: SyncRuntimeState): String = when (syncState) {
    SyncRuntimeState.Idle -> "Sync idle"
    SyncRuntimeState.Syncing -> "Syncing"
    is SyncRuntimeState.Success -> "Synced"
    SyncRuntimeState.Stale -> "Data marked stale"
    is SyncRuntimeState.Failed -> "Sync failed"
}

@Composable
private fun syncTint(
    syncState: SyncRuntimeState,
    accentColor: Color,
): Color = when (syncState) {
    SyncRuntimeState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    SyncRuntimeState.Syncing -> accentColor
    is SyncRuntimeState.Success -> MaterialTheme.colorScheme.primary
    SyncRuntimeState.Stale -> MaterialTheme.colorScheme.secondary
    is SyncRuntimeState.Failed -> MaterialTheme.colorScheme.error
}

// ── Hero section ─────────────────────────────────────────────────────────────
// Ports `HomeDayDetailGlassView.heroSection`/`heroText`: the big period-day /
// countdown text below Home's top bar. Built entirely on `CycleMath` primitives
// already computed in `HomeViewModel` (`phase`, `dayInCycle`, `cycleLength`,
// `daysUntilNextPeriod`) -- same engine `CalendarViewModel` already uses, kept
// deliberately separate from the parallel (currently unused anywhere)
// `CyclePhaseInsight` epoch-day engine so Home and Calendar never disagree on
// what phase a given day is in.
//
// Not ported in this pass (documented gaps, not oversights): the sparkle
// "Ask Sakhi" tip button with its pulsing glow animation and phase-tip content
// (needs `RecommendationViewModel`'s tip feed, a separate KMM-wired piece);
// scroll-driven hero-shrink behavior (`HeroScrollState`); the cycle-progress
// ring shown in the empty state.

private data class HeroText(val big: String, val sub: String)

private fun heroText(uiState: HomeUiState): HeroText {
    val isPartnerMode = uiState.session?.isViewingOwnData == false

    if (!uiState.hasCycleData) {
        return HeroText("", if (isPartnerMode) "she hasn't started tracking" else "start tracking today")
    }

    return when (uiState.phase) {
        CyclePhase.MENSTRUAL -> {
            val day = uiState.dayInCycle ?: 1
            HeroText("Day $day", if (isPartnerMode) "of her period" else "of your period")
        }
        CyclePhase.DELAYED -> {
            val daysDelayed = ((uiState.dayInCycle ?: 0) - (uiState.cycleLength ?: 0)).coerceAtLeast(1)
            HeroText(
                if (daysDelayed == 1) "1 Day" else "$daysDelayed Days",
                if (isPartnerMode) "her period is delayed" else "period is delayed",
            )
        }
        else -> {
            val daysUntil = uiState.daysUntilNextPeriod
            if (daysUntil == null) {
                HeroText("", if (isPartnerMode) "she hasn't started tracking" else "start tracking today")
            } else {
                val n = daysUntil.coerceAtLeast(0)
                HeroText(
                    if (n == 1) "1 Day" else "$n Days",
                    if (isPartnerMode) "until her next period" else "until next period",
                )
            }
        }
    }
}

@Composable
private fun HeroSection(uiState: HomeUiState, accentColor: Color) {
    val text = heroText(uiState)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (text.big.isNotEmpty()) {
            Text(
                text = text.big,
                style = MaterialTheme.typography.displayMedium,
                color = accentColor,
                maxLines = 1,
            )
        }
        Text(
            text = text.sub,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Phase info card ──────────────────────────────────────────────────────────
// Ports `HomeDayDetailGlassView+PhaseInfo.swift`'s `phaseInfoCard` +
// `HomeDayDetailGlassView+GlassCard.swift`'s `phaseSnippet` content verbatim
// (local Swift dictionary, not CMS-driven, so this is the real, final copy,
// not a placeholder).

private data class PhaseSnippet(val overview: String, val bodyChanges: List<String>)

private fun phaseSnippet(phase: CyclePhase): PhaseSnippet = when (phase) {
    CyclePhase.MENSTRUAL -> PhaseSnippet(
        overview = "Your period is here. Your body is shedding the lining it built last month, and starting fresh. It's completely normal to feel tired, crampy, or a little low, your hormones are at their lowest point right now.",
        bodyChanges = listOf(
            "Your uterus gently cramps to help shed the lining, that's what causes period pain",
            "Estrogen and progesterone are at their lowest, which can affect your mood and energy",
            "Your body is already quietly preparing for next month's cycle",
        ),
    )
    CyclePhase.FOLLICULAR -> PhaseSnippet(
        overview = "This is often the best week of your cycle. Estrogen is rising and you'll likely notice more energy, a clearer head, and a better mood. Your body is getting ready to ovulate.",
        bodyChanges = listOf(
            "Estrogen is rising, which is why you might feel brighter and more motivated",
            "Your uterine lining is rebuilding itself after your period",
            "One follicle in your ovary is growing and getting ready to release an egg",
        ),
    )
    CyclePhase.OVULATION -> PhaseSnippet(
        overview = "Your body is releasing an egg right now, this is ovulation. Many girls feel their best this week: confident, social, and full of energy. It's a natural peak.",
        bodyChanges = listOf(
            "Your ovary releases a mature egg, which can be fertilised for about 12–24 hours",
            "You might notice your discharge becomes clearer and more slippery, this is normal and healthy",
            "Your body temperature rises very slightly after the egg is released",
        ),
    )
    CyclePhase.LUTEAL -> PhaseSnippet(
        overview = "Your body is now in wind-down mode. Progesterone rises to prepare for a potential pregnancy. If that doesn't happen, your hormones start dropping and PMS can creep in, mood swings, bloating, cravings. All very normal.",
        bodyChanges = listOf(
            "Progesterone rises, which can make you feel heavier, bloated, or more emotional",
            "Your body temperature stays a little higher than usual",
            "Towards the end of this phase you may notice PMS symptoms, your period is coming soon",
        ),
    )
    CyclePhase.DELAYED -> PhaseSnippet(
        overview = "Your period is running a little late. This happens to almost everyone at some point and is usually nothing to worry about. Stress, a change in routine, or hormonal shifts are the most common reasons.",
        bodyChanges = listOf(
            "Stress can raise cortisol, which sometimes delays ovulation and pushes your period back",
            "Changes in sleep, travel, or diet can affect your hormone rhythm",
            "If this happens often, it's worth mentioning to a doctor, just to rule anything out",
        ),
    )
    CyclePhase.UNKNOWN -> PhaseSnippet(
        overview = "Sakhi needs a little more data to figure out your phase. Log your period start date and you'll start seeing personalised insights right here.",
        bodyChanges = emptyList(),
    )
}

/**
 * Port of iOS `HomeDayDetailGlassView+GlassCard.swift`'s `glassCard`/
 * `HomeDayDetailGlassCard` chrome -- the shared header (tinted icon badge +
 * bold title + optional badge pill + optional refresh action), divider, and
 * phase-tinted card fill/stroke every Home card in that file is built on.
 * "Glass" here isn't a blur/translucency effect (there's none in the real
 * iOS source, despite the name) -- it's this specific phase-tinted-surface
 * treatment. Simplified from iOS's `HomePhasePalette` (a distinct
 * primary/secondary/surface/tileStroke color set per phase) to a single
 * `phasePrimaryColor(phase)` tinted at low alpha, since Android doesn't have
 * that fuller per-phase palette ported -- a real, documented visual
 * simplification, not a missing feature.
 */
@Composable
private fun HomeGlassCard(
    title: String,
    accentColor: Color,
    hasCycleData: Boolean,
    icon: ImageVector? = null,
    badge: String? = null,
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardFill = if (hasCycleData) accentColor.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface
    val cardStroke = if (hasCycleData) accentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val badgeFill = accentColor.copy(alpha = 0.10f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cardFill, RoundedCornerShape(18.dp))
            .border(0.5.dp, cardStroke, RoundedCornerShape(18.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
        ) {
            icon?.let {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(badgeFill, RoundedCornerShape(8.dp))
                        .border(0.5.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(it, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            badge?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = accentColor.copy(alpha = 0.7f),
                    modifier = Modifier
                        .background(badgeFill, RoundedCornerShape(SakhiRadius.full))
                        .padding(horizontal = SakhiSpacing.space3, vertical = SakhiSpacing.space1),
                )
            }
            onRefresh?.let { refresh ->
                IconButton(
                    onClick = refresh,
                    enabled = !isRefreshing,
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = accentColor.copy(alpha = 0.7f))
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = accentColor.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        HorizontalDivider(color = cardStroke)
        content()
    }
}

/**
 * Port of iOS `HomeDayDetailGlassView+Cards.swift`'s `loggedDetailsCard` --
 * today's actual logged data (flow/weight/BBT/symptoms/moods) as a horizontal
 * chip row, matching iOS's exact priority order for the "own data" case
 * (flow -> weight -> BBT -> symptoms -> moods). Partner-mode priority
 * (symptoms -> moods -> flow) isn't built yet since Home's partner-mode
 * cards (`partnerChecklistCard`/`partnerHeadsUpCard`) are a separate,
 * larger, not-yet-built slice of this same visual-parity gap.
 */
@Composable
private fun LoggedDetailsCard(
    log: PeriodLog?,
    isPartnerMode: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val notes = log?.symptoms?.joinToString(" ").orEmpty()
    val weightKg = LogTokenEncoder.decodeWeight(notes)
    val bbtCelsius = LogTokenEncoder.decodeBbt(notes)
    val symptoms = log?.symptoms.orEmpty().mapNotNull { Symptom.from(it) }
    val moods = log?.moods.orEmpty().mapNotNull { Mood.from(it) }
    val flow = log?.flowIntensity
    val hasAnyData = log != null && (flow != null || weightKg != null || bbtCelsius != null || symptoms.isNotEmpty() || moods.isNotEmpty())

    HomeGlassCard(
        title = if (isPartnerMode) "Her day" else "Logged today",
        accentColor = accentColor,
        hasCycleData = true,
        icon = Icons.AutoMirrored.Filled.ListAlt,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        if (!hasAnyData) {
            Text(
                text = if (isPartnerMode) "She hasn't logged today yet" else "Log your day",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space4),
            )
        } else {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space3),
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            ) {
                if (flow != null) {
                    LogChip(icon = Icons.Filled.WaterDrop, value = flow.displayName, category = "Flow", accentColor = accentColor)
                }
                weightKg?.let {
                    LogChip(icon = Icons.Filled.MonitorWeight, value = "%.1f kg".format(it), category = "Weight", accentColor = accentColor)
                }
                bbtCelsius?.let {
                    LogChip(icon = Icons.Filled.Thermostat, value = "%.1f °C".format(it), category = "BBT", accentColor = accentColor)
                }
                symptoms.take(4).forEach { symptom ->
                    LogChip(icon = Icons.Filled.Healing, value = symptom.displayName, category = "Symptoms", accentColor = accentColor)
                }
                moods.take(2).forEach { mood ->
                    LogChip(icon = Icons.Filled.SentimentSatisfied, value = mood.displayName, category = "Mood", accentColor = accentColor)
                }
            }
        }
    }
}

@Composable
private fun LogChip(icon: ImageVector, value: String, category: String, accentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 82.dp)
            .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(SakhiRadius.lg))
            .padding(vertical = SakhiSpacing.space3)
            // One TalkBack stop reading "value, category" instead of 2 separate
            // stops for what's really one logical stat -- the icon stays
            // decorative (contentDescription = null below) since this label
            // already conveys the same meaning.
            .semantics(mergeDescendants = true) { contentDescription = "$category: $value" },
    ) {
        Icon(icon, contentDescription = null, tint = accentColor)
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = SakhiSpacing.space1),
        )
        Text(
            text = category,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Port of iOS `HomeDayDetailGlassView+Cards.swift`'s `cycleDetailsCard`: a
 * cycle-day counter, a per-day strip (period days filled, predicted
 * ovulation day marked), a "Started X" label, and 2 stat tiles. Uses the
 * same shared `CalendarMarker.buildMarks` per-day marking Calendar already
 * relies on, not a re-derived local version.
 *
 * Ports iOS's exact `cyclePillStrip` layout: pills always fill the full card
 * width (row width / day count, min 4dp) rather than scrolling, wrapping
 * into a second full-width row once the cycle is longer than 45 days, with
 * the ovulation day marked by a diagonal-hatch `Canvas` overlay instead of a
 * flat tint. One real, documented divergence: iOS derives the marked
 * "ovulation day" locally as `max(cycleLength - 14, 6)`, a quick arithmetic
 * stand-in duplicated in that one view; Android instead reuses
 * `CalendarMarker.buildMarks`'s `isOvulation` (the same properly-computed
 * shared prediction Calendar already renders), so Home and Calendar never
 * disagree about which day is ovulation.
 */
@Composable
private fun CycleDetailsCard(
    cycle: team.sakhi.models.CycleData,
    dayInCycle: Int,
    cycleLength: Int,
    accentColor: Color,
) {
    val today = DateConverter.today()
    val marks = remember(cycle) {
        val end = DateConverter.addDays(cycle.cycleStartDate, cycleLength - 1)
        CalendarMarker.buildMarks(cycle.cycleStartDate, end, listOf(cycle))
    }
    val days = remember(cycle, cycleLength) {
        (0 until cycleLength).map { DateConverter.addDays(cycle.cycleStartDate, it) }
    }

    HomeGlassCard(
        title = "Current Cycle",
        accentColor = accentColor,
        hasCycleData = true,
        icon = Icons.Filled.Autorenew,
    ) {
        Column(modifier = Modifier.padding(vertical = SakhiSpacing.space2)) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
            ) {
                Text(
                    text = "$dayInCycle",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = " / $cycleLength",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (today == DateConverter.addDays(cycle.cycleStartDate, dayInCycle - 1)) {
                    "today's cycle day"
                } else {
                    "cycle day"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SakhiSpacing.space5, end = SakhiSpacing.space5, bottom = SakhiSpacing.space3),
            )

            val rowSpacing = 2.dp
            val row1Size = if (days.size > 45) kotlin.math.ceil(days.size / 2.0).toInt() else days.size
            val row2 = if (days.size > row1Size) days.subList(row1Size, days.size) else emptyList()
            val row1 = days.subList(0, row1Size)

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = SakhiSpacing.space5)) {
                val pillW1 = ((maxWidth - rowSpacing * (row1Size - 1)) / row1Size).coerceAtLeast(4.dp)
                val pillW2 = if (row2.isNotEmpty()) {
                    ((maxWidth - rowSpacing * (row2.size - 1)) / row2.size).coerceAtLeast(4.dp)
                } else 0.dp

                Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                        row1.forEach { date ->
                            CyclePill(date = date, mark = marks[date], isToday = date == today, isFuture = date > today, pillWidth = pillW1, accentColor = accentColor)
                        }
                    }
                    if (row2.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(rowSpacing)) {
                            row2.forEach { date ->
                                CyclePill(date = date, mark = marks[date], isToday = date == today, isFuture = date > today, pillWidth = pillW2, accentColor = accentColor)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Started ${DateConverter.formatShort(cycle.cycleStartDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
                    CycleLegendDot(color = accentColor, label = "Period")
                    CycleLegendDot(color = accentColor.copy(alpha = 0.22f), label = "Ovulation", striped = true)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = SakhiSpacing.space5))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space4),
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            ) {
                CycleStatTile(
                    title = "Cycle Length",
                    value = "$cycleLength days",
                    icon = Icons.Filled.Autorenew,
                    modifier = Modifier.weight(1f),
                )
                CycleStatTile(
                    title = "Period Length",
                    value = "${cycle.periodLength ?: 5} days",
                    icon = Icons.Filled.WaterDrop,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CycleLegendDot(color: Color, label: String, striped: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape)) {
            if (striped) DiagonalHatch(modifier = Modifier.matchParentSize(), clip = CircleShape, lineAlpha = 0.55f, step = 2.5.dp)
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Port of iOS `cyclePillStrip`'s per-day pill: a rounded-rect tinted by
 * period/today/future status, with a diagonal-hatch overlay on the
 * predicted-ovulation day (matching iOS's `Canvas`-drawn hatch exactly,
 * rather than the flat-tint stand-in an earlier pass used).
 *
 * Status here is otherwise color-only (fill/stroke/hatch, no text) --
 * exactly the same real WCAG "use of color" gap found and fixed in
 * `CalendarScreen.kt`'s day cells. Each pill isn't a tap target the way a
 * calendar day is, but it's still a real per-day accessibility node (not a
 * `clickable`, just `Modifier.semantics {}` directly) so a TalkBack user
 * exploring the strip linearly still gets "15 Jun, period day" instead of
 * an undifferentiated colored rectangle.
 */
@Composable
private fun CyclePill(date: kotlinx.datetime.LocalDate, mark: team.sakhi.cycle.CalendarMarker.DayMark?, isToday: Boolean, isFuture: Boolean, pillWidth: Dp, accentColor: Color) {
    val isPeriod = mark?.isPeriod == true
    val isOvulation = mark?.isOvulation == true
    val fill = when {
        isPeriod -> accentColor
        isToday -> accentColor.copy(alpha = 0.15f)
        isFuture -> accentColor.copy(alpha = 0.07f)
        else -> accentColor.copy(alpha = 0.22f)
    }
    val strokeColor = when {
        isToday -> accentColor
        isFuture -> accentColor.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val description = buildString {
        append(DateConverter.formatShort(date))
        if (isToday) append(", today")
        when {
            isPeriod -> append(", period day")
            isOvulation -> append(", predicted ovulation day")
        }
    }
    Box(
        modifier = Modifier
            .width(pillWidth)
            .height(28.dp)
            .background(fill, RoundedCornerShape(3.dp))
            .border(if (isToday) 1.dp else 0.5.dp, strokeColor, RoundedCornerShape(3.dp))
            .semantics { contentDescription = description },
    ) {
        if (isOvulation) {
            DiagonalHatch(
                modifier = Modifier.matchParentSize(),
                clip = RoundedCornerShape(3.dp),
                lineAlpha = if (isFuture) 0.18f else 0.45f,
                color = if (isPeriod) Color.White else accentColor,
            )
        }
    }
}

/** Diagonal-line hatch pattern, matching iOS's `Canvas`-drawn ovulation-day overlay stroke-for-stroke. */
@Composable
private fun DiagonalHatch(modifier: Modifier = Modifier, clip: androidx.compose.ui.graphics.Shape, color: Color = Color.White, lineAlpha: Float = 0.45f, step: Dp = 3.5.dp) {
    Canvas(modifier = modifier.clip(clip)) {
        val stepPx = step.toPx()
        val strokeColor = color.copy(alpha = lineAlpha)
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = strokeColor,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x + size.height, size.height),
                strokeWidth = 0.8.dp.toPx(),
            )
            x += stepPx
        }
    }
}

@Composable
private fun CycleStatTile(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.lg),
        tonalElevation = SakhiSpacing.space1,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(SakhiSpacing.space3)
                .semantics(mergeDescendants = true) { contentDescription = "$title: $value" },
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = SakhiSpacing.space2),
            )
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PhaseInfoCard(phase: CyclePhase, isPartnerMode: Boolean, accentColor: Color) {
    val snippet = phaseSnippet(phase)
    val icon = when (phase) {
        CyclePhase.MENSTRUAL -> Icons.Filled.WaterDrop
        CyclePhase.FOLLICULAR -> Icons.Filled.WbSunny
        CyclePhase.OVULATION -> Icons.Filled.AutoAwesome
        CyclePhase.LUTEAL -> Icons.Filled.Bedtime
        CyclePhase.DELAYED -> Icons.Filled.Schedule
        CyclePhase.UNKNOWN -> Icons.Filled.MonitorHeart
    }

    HomeGlassCard(
        title = if (isPartnerMode) "What's happening to her body?" else "What's happening in your body",
        accentColor = accentColor,
        hasCycleData = true,
        icon = icon,
    ) {
        Text(
            text = snippet.overview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
        )
        if (snippet.bodyChanges.isNotEmpty()) {
            Text(
                text = "BODY CHANGES",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
            )
            snippet.bodyChanges.forEach { change ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = SakhiSpacing.space2)
                            .size(6.dp)
                            .background(accentColor.copy(alpha = 0.4f), CircleShape),
                    )
                    Text(
                        text = change,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ── Partner-mode cards ───────────────────────────────────────────────────────
// Ports `HomeDayDetailGlassView+PartnerCards.swift` (`partnerChecklistCard`,
// `partnerHeadsUpCard`) and `+EmptyState.swift`'s `partnerNoDataCard`.

/**
 * Port of iOS `partnerChecklistCard`: an AI-generated (Claude, via the real
 * shared `AIRepository.generatePartnerChecklist`) daily list of caring
 * things the partner can do today, with a completed-count badge and
 * per-item toggle. Simplified from iOS's shimmer-box loading skeleton to a
 * plain spinner + label (same loading semantics, simpler visual), and there's
 * no on-device fallback-text list on a generation failure (iOS falls back to
 * local canned text; Android's retry button re-runs the same real generation
 * call instead, since there's no local persistence layer to source a fallback
 * from here) -- both real, documented simplifications, not fake stand-ins.
 */
@Composable
private fun PartnerChecklistCard(
    state: PartnerChecklistUiState,
    onToggle: (String) -> Unit,
    onRetry: () -> Unit,
    accentColor: Color,
) {
    HomeGlassCard(
        title = "What You Can Do",
        accentColor = accentColor,
        hasCycleData = true,
        icon = Icons.AutoMirrored.Filled.ListAlt,
        badge = if (state.items.isEmpty()) null else "${state.completedCount}/${state.items.size}",
        onRefresh = if (state.items.isEmpty()) null else onRetry,
    ) {
        when {
            state.isGenerating -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space4),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = accentColor)
                Text(text = "Finding today's ideas...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.failedToGenerate -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space4),
            ) {
                Text(text = "Couldn't load today's list", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = accentColor,
                    modifier = Modifier.clickable(onClick = onRetry),
                )
            }
            else -> state.items.forEachIndexed { i, item ->
                if (i > 0) LearningDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(item.id) }
                        .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(if (item.isCompleted) accentColor else Color.Transparent, RoundedCornerShape(6.dp))
                            .border(1.5.dp, if (item.isCompleted) accentColor else accentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.isCompleted) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Port of iOS `partnerNoDataCard` (`+EmptyState.swift`). */
@Composable
private fun PartnerNoDataCard() {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xxl),
        tonalElevation = SakhiSpacing.space1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(SakhiSpacing.space6),
        ) {
            Icon(Icons.Filled.NightsStay, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            Text(
                text = "She hasn't started tracking yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space3),
            )
            Text(
                text = "Once she logs her first period, her cycle data will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space2),
            )
        }
    }
}

/** Port of iOS `partnerHeadsUpCard`. Shown only when [text] resolves non-null. */
@Composable
private fun PartnerHeadsUpCard(text: PartnerHeadsUpText, accentColor: Color) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xxl),
        tonalElevation = SakhiSpacing.space1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space4),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(accentColor.copy(alpha = 0.13f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
            Text(text = text.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            text.days?.let { days ->
                // The big number here just restates what `text.label` already says in
                // words ("Period in 3 days"), so it's hidden from TalkBack instead of
                // being announced a second time as a disconnected "3, days".
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clearAndSetSemantics {},
                ) {
                    Text(text = "$days", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = accentColor)
                    Text(text = "days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal data class PartnerHeadsUpText(val label: String, val days: Int?)

/**
 * Port of iOS `partnerHeadsUpText`'s full 5-branch switch. iOS reads
 * `prediction.periodDay`/`prediction.daysDelayed` off a dedicated
 * `PredictionInfo` struct; Android derives the same two numbers from what
 * `HomeUiState` already carries instead of adding a parallel prediction
 * type: `CycleMath.dayOfCycle` IS the in-period day count while
 * `CyclePhase.MENSTRUAL` (the cycle starts on the period's first day, so
 * cycle-day and period-day are the same number here), and
 * `CycleMath.daysUntilNextPeriod` goes negative once `CyclePhase.DELAYED`
 * kicks in, so its negation is the days-past-due count.
 *
 * `internal`, not `private` -- this is the one piece of pure business logic
 * in this file, so it's the one thing worth a real JVM unit test (see
 * `src/test/.../PartnerHeadsUpTextTest.kt`) rather than only manual
 * reasoning about 5 branches of date math.
 */
internal fun partnerHeadsUpText(phase: CyclePhase, dayInCycle: Int?, daysUntilNextPeriod: Int?): PartnerHeadsUpText? {
    return when (phase) {
        CyclePhase.FOLLICULAR, CyclePhase.OVULATION, CyclePhase.LUTEAL -> {
            val days = daysUntilNextPeriod ?: return null
            val maxDays = if (phase == CyclePhase.LUTEAL) 5 else 7
            if (days <= 0 || days > maxDays) return null
            val label = if (days == 1) "Period tomorrow" else "Period in $days days"
            PartnerHeadsUpText(label, days)
        }
        CyclePhase.MENSTRUAL -> {
            val periodDay = dayInCycle ?: return null
            if (periodDay < 6) return null
            PartnerHeadsUpText("Long period, day $periodDay", null)
        }
        CyclePhase.DELAYED -> {
            val daysDelayed = daysUntilNextPeriod?.let { -it } ?: return null
            if (daysDelayed <= 0) return null
            val label = if (daysDelayed == 1) "1 day past expected date" else "$daysDelayed days past expected date"
            PartnerHeadsUpText(label, null)
        }
        CyclePhase.UNKNOWN -> null
    }
}

// ── Empty state ──────────────────────────────────────────────────────────────
// Ports `HomeDayDetailGlassView.heroSection`'s `isEmptyState` copy exactly.
// Skips the animated cycle-progress ring iOS shows above this text -- a
// decorative element, not something users would notice missing.

@Composable
private fun EmptyStateCard(accentColor: Color) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xxl),
        color = accentColor.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Your body runs on a 4-phase cycle. Each phase shapes your energy, mood, and how you feel. Log your first period and Sakhi starts tracking from there.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(SakhiSpacing.space5),
        )
    }
}

/**
 * Port of iOS `HomeDayDetailGlassView+LearningPhase.swift`'s `learningPhaseCards`
 * -- shown only in the empty state (no cycle data logged yet), same trigger as
 * iOS. Every string below is copied verbatim from that file, not paraphrased.
 * Skipped: the "Log your first period" CTA button embedded in iOS's hero
 * section (Android's empty state already has its own log entry point via the
 * bottom action bar, so a second CTA here would be redundant, not missing).
 */
@Composable
private fun LearningPhaseCards() {
    Column(verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4)) {
        LearningCard(title = "How you feel through your cycle", icon = Icons.Filled.Favorite) {
            LearningIntro("Your energy, mood, sleep, skin, and appetite are not random, they follow your hormones, and your hormones follow your cycle. Once Sakhi knows your pattern, it can tell you exactly why you feel the way you do on any given day.")
            LearningRows(
                listOf(
                    Triple(Icons.Filled.Bolt, "High energy", "Around ovulation, estrogen peaks and most people feel their sharpest and most social"),
                    Triple(Icons.Filled.Bedtime, "Slow days", "Late in the luteal phase, progesterone drops and tiredness, cravings, and low mood are normal"),
                    Triple(Icons.Filled.WaterDrop, "Period cramps", "Prostaglandins cause the uterus to contract, some days are harder than others, and that is valid"),
                    Triple(Icons.Filled.Psychology, "Mood shifts", "Estrogen and progesterone directly affect serotonin and dopamine, so mood changes are biological, not personal"),
                ),
            )
        }

        LearningCard(title = "How your cycle works", icon = Icons.Filled.RadioButtonUnchecked) {
            LearningIntro("A menstrual cycle is 21–35 days long, 28 days on average. It runs on four hormones: FSH, LH, estrogen, and progesterone. Each phase is shaped by how those hormones rise and fall, and they affect your energy, mood, skin, digestion, and sleep every single day.")
            listOf(
                Triple(Icons.Filled.WaterDrop, phasePrimaryColor(CyclePhase.MENSTRUAL), "Menstrual" to "Days 1–5"),
                Triple(Icons.Filled.WbSunny, phasePrimaryColor(CyclePhase.FOLLICULAR), "Follicular" to "Days 6–13"),
                Triple(Icons.Filled.AutoAwesome, phasePrimaryColor(CyclePhase.OVULATION), "Ovulation" to "~Day 14"),
                Triple(Icons.Filled.Bedtime, phasePrimaryColor(CyclePhase.LUTEAL), "Luteal" to "Days 15–28"),
            ).forEachIndexed { i, (icon, color, nameDays) ->
                if (i > 0) LearningDivider()
                LearningOverviewRow(icon = icon, color = color, name = nameDays.first, days = nameDays.second)
            }
        }

        LearningPhaseDetailCard(
            title = "Menstrual phase", icon = Icons.Filled.WaterDrop, dayRange = "Days 1–5",
            phaseColor = phasePrimaryColor(CyclePhase.MENSTRUAL),
            overview = "Your period. Estrogen and progesterone drop to their lowest, and your uterus sheds its lining. Energy dips, rest is the best thing you can do right now.",
            points = listOf(
                "Cramping, bloating, and lower back pain are common",
                "Iron levels drop as you bleed, iron-rich foods help",
                "Gentle walks and warm packs ease cramps better than staying still",
            ),
        )
        LearningPhaseDetailCard(
            title = "Follicular phase", icon = Icons.Filled.WbSunny, dayRange = "Days 6–13",
            phaseColor = phasePrimaryColor(CyclePhase.FOLLICULAR),
            overview = "FSH rises and a follicle starts growing in your ovary. Estrogen climbs with it, bringing a natural boost in energy, focus, and confidence. Often the best week of the month.",
            points = listOf(
                "Energy and social drive naturally peak",
                "Skin tends to be at its clearest",
                "A great time for new projects, workouts, and big plans",
            ),
        )
        LearningPhaseDetailCard(
            title = "Ovulation", icon = Icons.Filled.AutoAwesome, dayRange = "Around day 14",
            phaseColor = phasePrimaryColor(CyclePhase.OVULATION),
            overview = "An LH surge triggers egg release. Your most fertile window, the egg survives 12–24 hours. Communication, confidence, and charisma tend to peak here.",
            points = listOf(
                "Discharge becomes clear and stretchy, like egg whites",
                "A mild one-sided pelvic ache is normal",
                "Basal body temperature rises slightly after ovulation",
            ),
        )
        LearningPhaseDetailCard(
            title = "Luteal phase", icon = Icons.Filled.Bedtime, dayRange = "Days 15–28",
            phaseColor = phasePrimaryColor(CyclePhase.LUTEAL),
            overview = "Progesterone rises to prepare the uterine lining. If no pregnancy happens, hormone levels drop, and your next period begins. PMS symptoms may appear in the second half.",
            points = listOf(
                "Energy slows as your body works harder internally",
                "Bloating, mood swings, and cravings are hormonal, not weakness",
                "Magnesium and B6 are clinically shown to ease PMS symptoms",
            ),
        )

        LearningCard(title = "What to log with Sakhi", icon = Icons.AutoMirrored.Filled.ListAlt) {
            LearningTipRow(Icons.Filled.Opacity, "Period dates, when it starts and ends each month")
            LearningDivider()
            LearningTipRow(Icons.Filled.BarChart, "Flow level, light, medium, or heavy each day")
            LearningDivider()
            LearningTipRow(Icons.Filled.Favorite, "Symptoms, cramps, mood, energy, sleep, headaches, cravings")
            LearningDivider()
            LearningTipRow(Icons.Filled.Visibility, "Discharge, colour and texture help pinpoint ovulation")
        }

        LearningCard(title = "The hormones behind it all", icon = Icons.Filled.MonitorHeart) {
            listOf(
                Triple("FSH", "Follicle-stimulating hormone", "Rises to kick off follicle development at the start of your cycle"),
                Triple("LH", "Luteinizing hormone", "Surges mid-cycle to trigger ovulation, the key signal Sakhi watches"),
                Triple("E2", "Estrogen", "Builds through follicular phase, lifts mood, energy, and skin glow"),
                Triple("P4", "Progesterone", "Rises after ovulation, prepares the uterus, causes PMS if it drops"),
            ).forEachIndexed { i, (abbr, name, desc) ->
                if (i > 0) LearningDivider()
                LearningAbbrRow(abbr = abbr, name = name, description = desc)
            }
        }

        LearningCard(title = "Eat with your cycle", icon = Icons.Filled.Eco) {
            listOf(
                LearningNutritionRowData(Icons.Filled.WaterDrop, phasePrimaryColor(CyclePhase.MENSTRUAL), "Period", "Spinach, lentils, dark chocolate, ginger tea, replenish iron and ease cramps"),
                LearningNutritionRowData(Icons.Filled.WbSunny, phasePrimaryColor(CyclePhase.FOLLICULAR), "Follicular", "Lean protein, fermented foods, broccoli, support rising estrogen"),
                LearningNutritionRowData(Icons.Filled.AutoAwesome, phasePrimaryColor(CyclePhase.OVULATION), "Ovulation", "Berries, leafy greens, zinc-rich seeds, antioxidants protect the egg"),
                LearningNutritionRowData(Icons.Filled.Bedtime, phasePrimaryColor(CyclePhase.LUTEAL), "Luteal", "Magnesium (nuts, seeds), B6 foods, complex carbs, reduce PMS and cravings"),
            ).forEachIndexed { i, row ->
                if (i > 0) LearningDivider()
                LearningOverviewRow(icon = row.icon, color = row.color, name = row.title, days = null, subtitle = row.description)
            }
        }

        LearningCard(title = "Tips for better tracking", icon = Icons.Filled.Lightbulb) {
            LearningTipRow(Icons.Filled.CalendarMonth, "Log your period on the first day of bleeding, not spotting")
            LearningDivider()
            LearningTipRow(Icons.Filled.Schedule, "Track at the same time each day for the most consistent data")
            LearningDivider()
            LearningTipRow(Icons.Filled.Bedtime, "Sleep quality and stress both affect your cycle length, log them too")
            LearningDivider()
            LearningTipRow(Icons.Filled.Autorenew, "Cycles vary month to month. Three cycles gives Sakhi a reliable baseline")
        }
    }
}

private data class LearningNutritionRowData(val icon: ImageVector, val color: Color, val title: String, val description: String)

@Composable
private fun LearningCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    HomeGlassCard(
        title = title,
        accentColor = MaterialTheme.colorScheme.primary,
        hasCycleData = false,
        icon = icon,
        content = content,
    )
}

@Composable
private fun LearningIntro(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
    )
    LearningDivider()
}

@Composable
private fun LearningRows(rows: List<Triple<ImageVector, String, String>>) {
    rows.forEachIndexed { i, (icon, title, subtitle) ->
        if (i > 0) LearningDivider()
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
            modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LearningOverviewRow(icon: ImageVector, color: Color, name: String, days: String?, subtitle: String? = null) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(color.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (subtitle != null) FontWeight.Bold else FontWeight.Normal))
            subtitle?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        days?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LearningAbbrRow(abbr: String, name: String, description: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = abbr, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        }
        Column {
            Text(text = name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LearningTipRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LearningPhaseDetailCard(
    title: String,
    icon: ImageVector,
    dayRange: String,
    phaseColor: Color,
    overview: String,
    points: List<String>,
) {
    HomeGlassCard(
        title = title,
        accentColor = phaseColor,
        hasCycleData = false,
        icon = icon,
        badge = dayRange,
    ) {
        Text(
            text = overview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
        )
        LearningDivider()
        points.forEachIndexed { i, point ->
            if (i > 0) LearningDivider()
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(5.dp)
                        .background(phaseColor.copy(alpha = 0.5f), CircleShape),
                )
                Text(text = point, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LearningDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

// ── Recommendation cards ─────────────────────────────────────────────────────
// Ports `HomeDayDetailGlassView+Cards.swift`'s `nutritionCard` ("What to Eat")
// and `sakhiInsightCard` ("Sakhi's tip for today") into Home's day-detail, now
// that the day-detail scroll column exists (see `HomeNavHost.kt`'s doc comment:
// Recommendations was intentionally left unmounted as a standalone route until
// this existed). Backed by the same real `RecommendationsViewModel` that already
// powers the (still-unmounted-as-a-route) standalone Recommendations screen —
// curated foods, USDA enrichment, and AI insight all come from the shared KMM
// pipeline, nothing here is placeholder content.
//
// Not ported in this pass (documented gaps, not oversights): iOS's horizontal
// TabView paging (4 foods per page + dot indicator) and shimmer loading state
// -- here all eat-more foods render in a single vertical list and loading shows
// nothing until data arrives. The "Avoid" section, phase tips, and condition
// tips stay on the standalone Recommendations screen only, matching iOS (which
// doesn't show them in the day-detail card either).

@Composable
private fun NutritionCard(
    foods: List<RecommendationFoodUi>,
    isLoading: Boolean,
    accentColor: Color,
) {
    if (isLoading && foods.isEmpty()) return

    HomeGlassCard(
        title = "What to Eat",
        accentColor = accentColor,
        hasCycleData = true,
        icon = Icons.Filled.Eco,
    ) {
        if (foods.isEmpty()) {
            Text(
                text = "No food recommendations are available right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
            )
        } else {
            foods.forEach { food ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = food.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = food.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    food.nutritionLabel?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SakhiInsightCard(
    insight: String?,
    isLoading: Boolean,
    isPartnerMode: Boolean,
    accentColor: Color,
) {
    if (insight == null && !isLoading) return

    HomeGlassCard(
        title = if (isPartnerMode) "How to be there for her today" else "Sakhi's tip for today",
        accentColor = accentColor,
        hasCycleData = true,
        icon = Icons.Filled.AutoAwesome,
    ) {
        if (insight == null) {
            Row(modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = accentColor, strokeWidth = 2.dp)
            }
        } else {
            Text(
                text = insight,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
            )
            Text(
                text = "Powered by Sakhi AI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
            )
        }
    }
}
