package team.sakhi.android.feature.home

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.ShowChart
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
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import team.sakhi.android.designsystem.LocalSakhiDarkTheme
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.phasePrimaryColor
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.ui.LoadingShimmer
import team.sakhi.android.ui.PhaseBadge
import team.sakhi.android.ui.SakhiBottomActionBar
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
import team.sakhi.design.SakhiColors
import team.sakhi.sync.SyncRuntimeState

/**
 * Phase 1 real home screen: renders shared cycle, permission, and sync state from
 * KMM. Entry points mirror iOS's actual Home top bar (hamburger -> Profile sheet,
 * person icon -> Be Her Sakhi/Care sheet — see `HomeView.swift` ~line 647-668) and
 * bottom action bar (calendar toggle, "Ask Sakhi" search capsule, log button — see
 * `HomeActionBar.swift`'s `SakhiBottomActionBar`). iOS has no tab bar; Android must
 * not invent one — parity means these exact entry points, not a Material bottom
 * nav. Presentation style is now split: Chat and Logging use the shared modal-sheet
 * lane in `HomeNavHost`, while Profile/Care/Calendar still use the nested nav graph
 * until their own sheet-presentation pass lands.
 */
@Composable
fun HomeScreen(
    onOpenProfile: () -> Unit = {},
    onOpenCare: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onQuickLogClick: (LocalDate) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
    recommendationsViewModel: RecommendationsViewModel = koinViewModel(),
    quickLogViewModel: LoggingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recoState by recommendationsViewModel.uiState.collectAsStateWithLifecycle()
    val quickLogUiState by quickLogViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accentColor = phasePrimaryColor(uiState.phase)
    val phasePalette = rememberHomePhasePalette(uiState.phase)
    val hapticManager = koinInject<AndroidHapticManager>()
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val heroScrollProgress by remember(scrollState, density) {
        derivedStateOf {
            with(density) {
                ((scrollState.value.toFloat() - 20.dp.toPx()) / 120.dp.toPx()).coerceIn(0f, 1f)
            }
        }
    }

    // `refresh()`'s own triggers (session/syncState/partnerSnapshot) don't fire
    // on a plain nav pop back from the logging sheet, so
    // `hasLoggedForSelectedDate` would otherwise go stale after a save.
    // Re-check on every resume instead.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSelectedDate()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Port of iOS's `HomeLogButton.isSaving` -- a quick-log flow-level tap (see
    // `QuickLogMenuContent`) saves through the same `LoggingViewModel` the full
    // sheet uses, so `hasLoggedForSelectedDate` needs a refresh once that save
    // actually completes (its own `isSaving` flips true -> false), not just on
    // resume.
    var wasQuickLogSaving by remember { mutableStateOf(false) }
    LaunchedEffect(quickLogUiState.isSaving) {
        if (wasQuickLogSaving && !quickLogUiState.isSaving) {
            viewModel.refreshSelectedDate()
        }
        wasQuickLogSaving = quickLogUiState.isSaving
    }

    // Real feature build (2026-07-16): keeps Home's own quick-log instance
    // (used by the bottom bar's "+"/pencil quick-flow menu) pointed at
    // whichever date is currently selected -- same pattern already built for
    // Calendar's own bottom bar (`CalendarScreen`'s equivalent effect).
    LaunchedEffect(uiState.selectedDate) {
        quickLogViewModel.selectDate(uiState.selectedDate)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(homeBackgroundBrush(phasePalette = phasePalette, hasCycleData = uiState.hasCycleData)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Real bug found on the first-ever signed-in device walkthrough: without
                // this, the top bar's Profile/Care icons render with ~72% of their real
                // touch height sitting under the system status bar's own touch-
                // interceptable window (confirmed via `dumpsys window` -- statusBars
                // inset frame was [0,0][1080,128], the icons' clickable bounds only
                // [48,37][174,163]) -- taps at the icon's visual center silently did
                // nothing because edge-to-edge (`enableEdgeToEdge()` in MainActivity)
                // draws this screen's content behind the status bar with no inset
                // padding to compensate.
                .statusBarsPadding()
                .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        ) {
            HomeTopBar(
                uiState = uiState,
                heroScrollProgress = heroScrollProgress,
                phasePalette = phasePalette,
                onOpenProfile = {
                    hapticManager.selection()
                    onOpenProfile()
                },
                onOpenCare = {
                    hapticManager.selection()
                    onOpenCare()
                },
                onOpenCalendar = {
                    hapticManager.selection()
                    onOpenCalendar()
                },
                onResetToToday = {
                    hapticManager.selection()
                    viewModel.selectDate(DateConverter.today())
                },
            )
            Text(
                text = uiState.session?.let { sessionSummary(context, it) }
                    ?: stringResource(R.string.home_waiting_for_session),
                style = MaterialTheme.typography.bodyMedium,
                color = homeSecondaryTextColor(
                    phasePalette = phasePalette,
                    hasCycleData = uiState.hasCycleData,
                    isMenstrual = uiState.phase == CyclePhase.MENSTRUAL,
                ),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
            ) {
            val canShowHero = uiState.session?.isViewingOwnData == true || uiState.canViewPredictions
            if (!uiState.isLoadingCycle && canShowHero) {
                HeroSection(
                    uiState = uiState,
                    accentColor = accentColor,
                    phasePalette = phasePalette,
                    scrollProgress = heroScrollProgress,
                )
            }

            StateChip(
                label = syncLabel(context, uiState.syncState),
                tint = syncTint(uiState.syncState, accentColor),
            )

            uiState.partnerSnapshotRevision?.let { revision ->
                val revisionText = stringResource(
                    R.string.home_partner_snapshot_revision,
                    revision,
                )
                val revisionAndRefreshText = uiState.partnerSnapshotRefreshedAt?.let {
                    stringResource(
                        R.string.home_partner_snapshot_revision_with_refreshed,
                        revisionText,
                        context.getString(R.string.home_partner_snapshot_refreshed, it),
                    )
                }
                Text(
                    text = revisionAndRefreshText ?: revisionText,
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
                    LaunchedEffect(uiState.phase, uiState.dayInCycle, uiState.daysUntilNextPeriod) {
                        checklistViewModel.loadOrGenerate(
                            cyclePhase = uiState.phase,
                            cycleDay = uiState.dayInCycle ?: 1,
                            daysUntilNextPeriod = uiState.daysUntilNextPeriod,
                        )
                    }
                    PartnerChecklistCard(
                        state = checklistState,
                        onToggle = checklistViewModel::toggle,
                        onRetry = {
                            checklistViewModel.retry(
                                cyclePhase = uiState.phase,
                                cycleDay = uiState.dayInCycle ?: 1,
                                daysUntilNextPeriod = uiState.daysUntilNextPeriod,
                            )
                        },
                        phase = uiState.phase,
                        accentColor = accentColor,
                    )
                    LoggedDetailsCard(
                        log = uiState.selectedLog,
                        isPartnerMode = true,
                        phase = uiState.phase,
                        accentColor = accentColor,
                        onClick = { /* Activity/history sheet -- ActivityLogScreen, reachable from Profile today */ },
                    )
                    if (recoState.canViewPhaseRecommendations) {
                        NutritionCard(
                            phase = uiState.phase,
                            foods = recoState.eatMoreFoods,
                            isLoading = recoState.isLoading,
                            accentColor = accentColor,
                        )
                    }
                    partnerHeadsUpText(context, uiState.phase, uiState.dayInCycle, uiState.daysUntilNextPeriod)?.let { headsUp ->
                        PartnerHeadsUpCard(text = headsUp, accentColor = accentColor)
                    }
                    PhaseInfoCard(phase = uiState.phase, isPartnerMode = true, accentColor = accentColor)
                    // Real gap found in the second parity sweep: iOS's real
                    // `sakhiInsightCard` renders in partner mode too (its own
                    // title branches on `isPartnerMode` -- "How to be there for
                    // her today"), but this card was never called at all in
                    // Android's partner branch, so partners never saw it.
                    SakhiInsightCard(
                        phase = uiState.phase,
                        insight = recoState.aiInsight,
                        isLoading = recoState.isLoading,
                        isPartnerMode = true,
                        accentColor = accentColor,
                        onRefresh = recommendationsViewModel::refreshInsight,
                        isRefreshing = recoState.isRefreshingInsight,
                    )
                } else {
                    // Order matches iOS `HomeDayDetailGlassView.body`'s own-data
                    // branch exactly: loggedDetails -> nutrition -> cycleDetails
                    // -> phaseInfo (`SakhiInsightCard` stays last -- it isn't one
                    // of that exact 4-card list, kept where it already was).
                    LoggedDetailsCard(
                        log = uiState.selectedLog,
                        isPartnerMode = false,
                        phase = uiState.phase,
                        accentColor = accentColor,
                        onClick = { /* Activity/history sheet -- ActivityLogScreen, reachable from Profile today */ },
                    )
                    if (recoState.canViewPhaseRecommendations) {
                        NutritionCard(
                            phase = uiState.phase,
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
                            phase = uiState.phase,
                            accentColor = accentColor,
                            cyclesAnalyzed = uiState.cyclesAnalyzed,
                            shortestCycle = uiState.shortestCycle,
                            longestCycle = uiState.longestCycle,
                        )
                    }
                    PhaseInfoCard(phase = uiState.phase, isPartnerMode = false, accentColor = accentColor)
                    SakhiInsightCard(
                        phase = uiState.phase,
                        insight = recoState.aiInsight,
                        isLoading = recoState.isLoading,
                        isPartnerMode = false,
                        accentColor = accentColor,
                        onRefresh = recommendationsViewModel::refreshInsight,
                        isRefreshing = recoState.isRefreshingInsight,
                    )
                }
            }
            }

            SakhiBottomActionBar(
                phase = uiState.phase,
                accentColor = accentColor,
                isPartnerMode = uiState.session?.isViewingOwnData == false,
                canLog = uiState.canLogPeriod,
                hasLoggedForDate = uiState.hasLoggedForSelectedDate,
                isLogSaving = quickLogUiState.isSaving,
                selectedFlow = quickLogUiState.selectedFlow,
                showCalendarButton = true,
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
                    onQuickLogClick(uiState.selectedDate)
                },
                onQuickLogFlow = { level ->
                    hapticManager.selection()
                    quickLogViewModel.onFlowSelected(level)
                    quickLogViewModel.save()
                },
            )
        }
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

private fun sessionSummary(context: Context, session: team.sakhi.session.SessionContext): String = when {
    session.isViewingOwnData -> context.getString(R.string.home_session_summary_own, session.userName)
    else -> context.getString(
        R.string.home_session_summary_partner,
        session.activeRole.displayName.lowercase(),
        session.targetUserId,
    )
}

private fun syncLabel(context: Context, syncState: SyncRuntimeState): String = when (syncState) {
    SyncRuntimeState.Idle -> context.getString(R.string.home_sync_idle)
    SyncRuntimeState.Syncing -> context.getString(R.string.home_sync_syncing)
    is SyncRuntimeState.Success -> context.getString(R.string.home_sync_synced)
    SyncRuntimeState.Stale -> context.getString(R.string.home_sync_stale)
    is SyncRuntimeState.Failed -> context.getString(R.string.home_sync_failed)
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

private fun heroText(context: Context, uiState: HomeUiState): HeroText {
    val isPartnerMode = uiState.session?.isViewingOwnData == false

    if (!uiState.hasCycleData) {
        return HeroText(
            "",
            context.getString(
                if (isPartnerMode) {
                    R.string.home_hero_not_started_partner
                } else {
                    R.string.home_hero_not_started_self
                },
            ),
        )
    }

    return when (uiState.phase) {
        CyclePhase.MENSTRUAL -> {
            val day = uiState.dayInCycle ?: 1
            HeroText(
                context.resources.getQuantityString(R.plurals.home_day_count, day, day),
                context.getString(
                    if (isPartnerMode) {
                        R.string.home_hero_period_of_her
                    } else {
                        R.string.home_hero_period_of_your
                    },
                ),
            )
        }
        CyclePhase.DELAYED -> {
            val daysDelayed = ((uiState.dayInCycle ?: 0) - (uiState.cycleLength ?: 0)).coerceAtLeast(1)
            HeroText(
                context.resources.getQuantityString(R.plurals.home_day_count, daysDelayed, daysDelayed),
                context.getString(
                    if (isPartnerMode) {
                        R.string.home_hero_period_delayed_her
                    } else {
                        R.string.home_hero_period_delayed_self
                    },
                ),
            )
        }
        else -> {
            val daysUntil = uiState.daysUntilNextPeriod
            if (daysUntil == null) {
                HeroText(
                    "",
                    context.getString(
                        if (isPartnerMode) {
                            R.string.home_hero_not_started_partner
                        } else {
                            R.string.home_hero_not_started_self
                        },
                    ),
                )
            } else {
                val n = daysUntil.coerceAtLeast(0)
                HeroText(
                    context.resources.getQuantityString(R.plurals.home_day_count, n, n),
                    context.getString(
                        if (isPartnerMode) {
                            R.string.home_hero_until_next_her
                        } else {
                            R.string.home_hero_until_next_self
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun HeroSection(
    uiState: HomeUiState,
    accentColor: Color,
    phasePalette: HomePhasePalette,
    scrollProgress: Float,
) {
    val context = LocalContext.current
    val text = heroText(context, uiState)
    val subtitleColor = homeSecondaryTextColor(
        phasePalette = phasePalette,
        hasCycleData = uiState.hasCycleData,
        isMenstrual = uiState.phase == CyclePhase.MENSTRUAL,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = 1f - (0.38f * scrollProgress)
                translationY = -36.dp.toPx() * scrollProgress
                scaleX = 1f - (0.08f * scrollProgress)
                scaleY = 1f - (0.08f * scrollProgress)
            },
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
            color = subtitleColor,
        )
    }
}

// ── Phase info card ──────────────────────────────────────────────────────────
// Ports `HomeDayDetailGlassView+PhaseInfo.swift`'s `phaseInfoCard` +
// `HomeDayDetailGlassView+GlassCard.swift`'s `phaseSnippet` content verbatim
// (local Swift dictionary, not CMS-driven, so this is the real, final copy,
// not a placeholder).

private data class PhaseSnippet(val overviewRes: Int, val bodyChangeResIds: List<Int>)

private fun phaseSnippet(phase: CyclePhase): PhaseSnippet = when (phase) {
    CyclePhase.MENSTRUAL -> PhaseSnippet(
        overviewRes = R.string.home_phase_snippet_menstrual_overview,
        bodyChangeResIds = listOf(
            R.string.home_phase_snippet_menstrual_body_1,
            R.string.home_phase_snippet_menstrual_body_2,
            R.string.home_phase_snippet_menstrual_body_3,
        ),
    )
    CyclePhase.FOLLICULAR -> PhaseSnippet(
        overviewRes = R.string.home_phase_snippet_follicular_overview,
        bodyChangeResIds = listOf(
            R.string.home_phase_snippet_follicular_body_1,
            R.string.home_phase_snippet_follicular_body_2,
            R.string.home_phase_snippet_follicular_body_3,
        ),
    )
    CyclePhase.OVULATION -> PhaseSnippet(
        overviewRes = R.string.home_phase_snippet_ovulation_overview,
        bodyChangeResIds = listOf(
            R.string.home_phase_snippet_ovulation_body_1,
            R.string.home_phase_snippet_ovulation_body_2,
            R.string.home_phase_snippet_ovulation_body_3,
        ),
    )
    CyclePhase.LUTEAL -> PhaseSnippet(
        overviewRes = R.string.home_phase_snippet_luteal_overview,
        bodyChangeResIds = listOf(
            R.string.home_phase_snippet_luteal_body_1,
            R.string.home_phase_snippet_luteal_body_2,
            R.string.home_phase_snippet_luteal_body_3,
        ),
    )
    CyclePhase.DELAYED -> PhaseSnippet(
        overviewRes = R.string.home_phase_snippet_delayed_overview,
        bodyChangeResIds = listOf(
            R.string.home_phase_snippet_delayed_body_1,
            R.string.home_phase_snippet_delayed_body_2,
            R.string.home_phase_snippet_delayed_body_3,
        ),
    )
    CyclePhase.UNKNOWN -> PhaseSnippet(
        overviewRes = R.string.home_phase_snippet_unknown_overview,
        bodyChangeResIds = emptyList(),
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
    phase: CyclePhase = CyclePhase.UNKNOWN,
    accentColor: Color,
    hasCycleData: Boolean,
    icon: ImageVector? = null,
    badge: String? = null,
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val refreshLabel = stringResource(R.string.home_refresh_content_description)
    val palette = rememberHomePhasePalette(phase)
    val isDark = LocalSakhiDarkTheme.current
    val isMenstrual = phase == CyclePhase.MENSTRUAL
    val cardFill = when {
        !hasCycleData -> MaterialTheme.colorScheme.surface
        isMenstrual -> palette.surface
        isDark -> palette.tileFill
        else -> palette.tileFill.copy(alpha = 0.14f)
    }
    val cardStroke = when {
        !hasCycleData -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        isMenstrual -> palette.secondary.copy(alpha = 0.30f)
        else -> palette.tileStroke.copy(alpha = 0.50f)
    }
    val dividerColor = when {
        !hasCycleData -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
        isMenstrual -> palette.primary.copy(alpha = 0.16f)
        else -> palette.tileStroke.copy(alpha = 0.28f)
    }
    val badgeFill = when {
        !hasCycleData -> accentColor.copy(alpha = 0.10f)
        isMenstrual -> palette.secondary.copy(alpha = 0.20f)
        else -> palette.secondary.copy(alpha = 0.09f)
    }
    val badgeStroke = when {
        !hasCycleData -> accentColor.copy(alpha = 0.20f)
        isMenstrual -> palette.secondary.copy(alpha = 0.30f)
        else -> accentColor.copy(alpha = 0.25f)
    }

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
                        .border(0.5.dp, badgeStroke, RoundedCornerShape(8.dp)),
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
                        .border(0.5.dp, accentColor.copy(alpha = 0.10f), RoundedCornerShape(SakhiRadius.full))
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
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = refreshLabel,
                            tint = accentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = dividerColor)
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
    phase: CyclePhase,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val flowLabel = stringResource(R.string.home_log_category_flow)
    val weightLabel = stringResource(R.string.home_log_category_weight)
    val bbtLabel = stringResource(R.string.home_log_category_bbt)
    val symptomLabel = stringResource(R.string.home_log_category_symptoms)
    val moodLabel = stringResource(R.string.home_log_category_mood)
    val weightFormat = stringResource(R.string.home_log_value_weight_kg)
    val bbtFormat = stringResource(R.string.home_log_value_bbt_celsius)
    val notes = log?.symptoms?.joinToString(" ").orEmpty()
    val weightKg = LogTokenEncoder.decodeWeight(notes)
    val bbtCelsius = LogTokenEncoder.decodeBbt(notes)
    val symptoms = log?.symptoms.orEmpty().mapNotNull { Symptom.from(it) }
    val moods = log?.moods.orEmpty().mapNotNull { Mood.from(it) }
    val flow = log?.flowIntensity
    val hasAnyData = log != null && (flow != null || weightKg != null || bbtCelsius != null || symptoms.isNotEmpty() || moods.isNotEmpty())

    HomeGlassCard(
        title = stringResource(
            if (isPartnerMode) {
                R.string.home_logged_partner_title
            } else {
                R.string.home_logged_self_title
            },
        ),
        phase = phase,
        accentColor = accentColor,
        hasCycleData = true,
        icon = Icons.AutoMirrored.Filled.ListAlt,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        if (!hasAnyData) {
            Text(
                text = stringResource(
                    if (isPartnerMode) {
                        R.string.home_logged_partner_empty
                    } else {
                        R.string.home_logged_self_empty
                    },
                ),
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
                    LogChip(icon = Icons.Filled.WaterDrop, value = flow.displayName, category = flowLabel, accentColor = accentColor)
                }
                weightKg?.let {
                    LogChip(icon = Icons.Filled.MonitorWeight, value = weightFormat.format(it), category = weightLabel, accentColor = accentColor)
                }
                bbtCelsius?.let {
                    LogChip(icon = Icons.Filled.Thermostat, value = bbtFormat.format(it), category = bbtLabel, accentColor = accentColor)
                }
                symptoms.take(4).forEach { symptom ->
                    LogChip(icon = Icons.Filled.Healing, value = symptom.displayName, category = symptomLabel, accentColor = accentColor)
                }
                moods.take(2).forEach { mood ->
                    LogChip(icon = Icons.Filled.SentimentSatisfied, value = mood.displayName, category = moodLabel, accentColor = accentColor)
                }
            }
        }
    }
}

@Composable
private fun LogChip(icon: ImageVector, value: String, category: String, accentColor: Color) {
    val contentDescription = stringResource(R.string.home_log_chip_content_description, category, value)
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
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
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
    phase: CyclePhase,
    accentColor: Color,
    cyclesAnalyzed: Int,
    shortestCycle: Int,
    longestCycle: Int,
) {
    val context = LocalContext.current
    val today = DateConverter.today()
    val marks = remember(cycle) {
        val end = DateConverter.addDays(cycle.cycleStartDate, cycleLength - 1)
        CalendarMarker.buildMarks(cycle.cycleStartDate, end, listOf(cycle))
    }
    val days = remember(cycle, cycleLength) {
        (0 until cycleLength).map { DateConverter.addDays(cycle.cycleStartDate, it) }
    }

    HomeGlassCard(
        title = stringResource(R.string.home_current_cycle_title),
        phase = phase,
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
                    text = stringResource(R.string.home_cycle_day_current_number, dayInCycle),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = stringResource(R.string.home_cycle_day_total, cycleLength),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (today == DateConverter.addDays(cycle.cycleStartDate, dayInCycle - 1)) {
                    stringResource(R.string.home_cycle_day_today)
                } else {
                    stringResource(R.string.home_cycle_day_label)
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
                    text = stringResource(
                        R.string.home_cycle_started,
                        DateConverter.formatShort(cycle.cycleStartDate),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
                    CycleLegendDot(color = accentColor, label = stringResource(R.string.home_cycle_legend_period))
                    CycleLegendDot(
                        color = accentColor.copy(alpha = 0.22f),
                        label = stringResource(R.string.home_cycle_legend_ovulation),
                        striped = true,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = SakhiSpacing.space5))

            Column(
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space4),
            ) {
                CycleStatusTile(
                    cyclesAnalyzed = cyclesAnalyzed,
                    shortestCycle = shortestCycle,
                    longestCycle = longestCycle,
                    accentColor = accentColor,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3)) {
                    CycleStatTile(
                        title = stringResource(R.string.home_cycle_length_title),
                        value = context.resources.getQuantityString(
                            R.plurals.home_cycle_length_days,
                            cycleLength,
                            cycleLength,
                        ),
                        icon = Icons.Filled.Autorenew,
                        modifier = Modifier.weight(1f),
                    )
                    CycleStatTile(
                        title = stringResource(R.string.home_period_length_title),
                        value = context.resources.getQuantityString(
                            R.plurals.home_cycle_length_days,
                            cycle.periodLength ?: 5,
                            cycle.periodLength ?: 5,
                        ),
                        icon = Icons.Filled.WaterDrop,
                        modifier = Modifier.weight(1f),
                    )
                }
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
    val context = LocalContext.current
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
    val pillDateLabel = DateConverter.formatShort(date)
    val todayLabel = context.getString(R.string.home_cycle_pill_today)
    val statusLabel = when {
        isPeriod -> context.getString(R.string.home_cycle_pill_period_day)
        isOvulation -> context.getString(R.string.home_cycle_pill_predicted_ovulation)
        else -> null
    }
    val description = when {
        isToday && statusLabel != null -> context.getString(
            R.string.home_cycle_pill_content_description_three_part,
            pillDateLabel,
            todayLabel,
            statusLabel,
        )
        isToday -> context.getString(
            R.string.home_cycle_pill_content_description_two_part,
            pillDateLabel,
            todayLabel,
        )
        statusLabel != null -> context.getString(
            R.string.home_cycle_pill_content_description_two_part,
            pillDateLabel,
            statusLabel,
        )
        else -> pillDateLabel
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
                .semantics(mergeDescendants = true) {
                    contentDescription = title
                    stateDescription = value
                },
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

// Port of iOS `HomeDayDetailGlassView.cycleStatusTile`: a regularity summary
// row inside the Current Cycle card. `isRegular` is judged the same way iOS
// does it -- in this view, not via a shared KMM helper -- since it's a
// different measurement from `CycleMath.profileHealthStatus` (the
// delayed-period algorithm behind Profile's own "Cycle Health" badge).
@Composable
private fun CycleStatusTile(
    cyclesAnalyzed: Int,
    shortestCycle: Int,
    longestCycle: Int,
    accentColor: Color,
) {
    val context = LocalContext.current
    val hasMeasuredStats = cyclesAnalyzed > 0
    val isRegular = (longestCycle - shortestCycle) <= 7
    val detail = if (hasMeasuredStats) {
        context.resources.getQuantityString(R.plurals.home_cycle_status_analysed, cyclesAnalyzed, cyclesAnalyzed)
    } else {
        stringResource(R.string.home_cycle_status_no_data)
    }

    Surface(
        shape = RoundedCornerShape(SakhiRadius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(SakhiSpacing.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(accentColor.copy(alpha = 0.16f), RoundedCornerShape(SakhiRadius.md)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_cycle_status_title),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
            if (hasMeasuredStats) {
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.full),
                    color = if (isRegular) accentColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = SakhiSpacing.space2, vertical = SakhiSpacing.space1),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
                    ) {
                        Icon(
                            imageVector = if (isRegular) Icons.Filled.Check else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (isRegular) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = if (isRegular) {
                                stringResource(R.string.home_cycle_status_regular)
                            } else {
                                stringResource(R.string.home_cycle_status_irregular)
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isRegular) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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
        title = stringResource(
            if (isPartnerMode) {
                R.string.home_phase_info_partner_title
            } else {
                R.string.home_phase_info_self_title
            },
        ),
        phase = phase,
        accentColor = accentColor,
        hasCycleData = true,
        icon = icon,
    ) {
        PhaseBadge(
            phase = phase,
            accentColor = accentColor,
            modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
        )
        Text(
            text = stringResource(snippet.overviewRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
        )
        if (snippet.bodyChangeResIds.isNotEmpty()) {
            Text(
                text = stringResource(R.string.home_phase_info_body_changes),
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
            )
            snippet.bodyChangeResIds.forEach { changeResId ->
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
                        text = stringResource(changeResId),
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
 * per-item toggle. Android now matches iOS's 4-row shimmer loading skeleton
 * more closely too, while still keeping the real documented generation-failure
 * simplification: no on-device fallback-text list. iOS falls back to local
 * canned text; Android's retry button re-runs the same real generation call
 * instead, since there's no local persistence layer to source a fallback from
 * here.
 */
@Composable
private fun PartnerChecklistCard(
    state: PartnerChecklistUiState,
    onToggle: (String) -> Unit,
    onRetry: () -> Unit,
    phase: CyclePhase,
    accentColor: Color,
) {
    HomeGlassCard(
        title = stringResource(R.string.home_partner_checklist_title),
        phase = phase,
        accentColor = accentColor,
        hasCycleData = true,
        icon = Icons.AutoMirrored.Filled.ListAlt,
        badge = if (state.items.isEmpty()) null else "${state.completedCount}/${state.items.size}",
        onRefresh = if (state.items.isEmpty()) null else onRetry,
    ) {
        when {
            state.isGenerating -> PartnerChecklistLoadingState(phase = phase, accentColor = accentColor)
            state.failedToGenerate -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space4),
            ) {
                Text(
                    text = stringResource(R.string.home_partner_checklist_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.home_retry),
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
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PartnerChecklistLoadingState(
    phase: CyclePhase,
    accentColor: Color,
) {
    val rowWidths = listOf(160.dp, 130.dp, 180.dp, 145.dp)
    val shimmerBase = accentColor.copy(alpha = if (phase == CyclePhase.MENSTRUAL) 0.15f else 0.08f)

    Column {
        rowWidths.forEachIndexed { index, width ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space5, vertical = 14.dp),
            ) {
                LoadingShimmer(
                    width = 22.dp,
                    height = 22.dp,
                    baseColor = shimmerBase,
                )
                LoadingShimmer(
                    width = width,
                    height = 13.dp,
                    baseColor = shimmerBase,
                )
            }
            if (index < rowWidths.lastIndex) {
                LearningDivider()
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
                text = stringResource(R.string.home_partner_no_data_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space3),
            )
            Text(
                text = stringResource(R.string.home_partner_no_data_body),
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
private fun PartnerHeadsUpCard(text: PartnerHeadsUpCardText, accentColor: Color) {
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
                    Text(
                        text = stringResource(R.string.home_partner_heads_up_days),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal data class PartnerHeadsUpCardText(val label: String, val days: Int?)

internal enum class PartnerHeadsUpTextKind {
    PERIOD_TOMORROW,
    PERIOD_IN_DAYS,
    LONG_PERIOD_DAY,
    PAST_EXPECTED_DATE,
}

internal data class PartnerHeadsUpText(
    val kind: PartnerHeadsUpTextKind,
    val number: Int? = null,
    val days: Int?,
)

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
internal fun partnerHeadsUpText(
    context: Context,
    phase: CyclePhase,
    dayInCycle: Int?,
    daysUntilNextPeriod: Int?,
): PartnerHeadsUpCardText? {
    val raw = partnerHeadsUpText(phase, dayInCycle, daysUntilNextPeriod) ?: return null
    return when (phase) {
        CyclePhase.FOLLICULAR, CyclePhase.OVULATION, CyclePhase.LUTEAL -> {
            val label = when (raw.kind) {
                PartnerHeadsUpTextKind.PERIOD_TOMORROW -> context.getString(R.string.home_partner_heads_up_tomorrow)
                PartnerHeadsUpTextKind.PERIOD_IN_DAYS -> {
                    val days = raw.number ?: return null
                    context.resources.getQuantityString(
                        R.plurals.home_partner_heads_up_period_in,
                        days,
                        days,
                    )
                }
                else -> return null
            }
            PartnerHeadsUpCardText(label = label, days = raw.days)
        }
        CyclePhase.MENSTRUAL -> {
            val periodDay = raw.number ?: return null
            PartnerHeadsUpCardText(
                label = context.getString(R.string.home_partner_heads_up_long_period, periodDay),
                days = raw.days,
            )
        }
        CyclePhase.DELAYED -> {
            val daysDelayed = raw.number ?: return null
            PartnerHeadsUpCardText(
                label = context.resources.getQuantityString(
                    R.plurals.home_partner_heads_up_past_expected,
                    daysDelayed,
                    daysDelayed,
                ),
                days = raw.days,
            )
        }
        CyclePhase.UNKNOWN -> null
    }
}

internal fun partnerHeadsUpText(
    phase: CyclePhase,
    dayInCycle: Int?,
    daysUntilNextPeriod: Int?,
): PartnerHeadsUpText? {
    return when (phase) {
        CyclePhase.FOLLICULAR, CyclePhase.OVULATION, CyclePhase.LUTEAL -> {
            val days = daysUntilNextPeriod ?: return null
            val maxDays = if (phase == CyclePhase.LUTEAL) 5 else 7
            if (days <= 0 || days > maxDays) return null
            if (days == 1) {
                PartnerHeadsUpText(PartnerHeadsUpTextKind.PERIOD_TOMORROW, days = days)
            } else {
                PartnerHeadsUpText(PartnerHeadsUpTextKind.PERIOD_IN_DAYS, number = days, days = days)
            }
        }
        CyclePhase.MENSTRUAL -> {
            val periodDay = dayInCycle ?: return null
            if (periodDay < 6) return null
            PartnerHeadsUpText(PartnerHeadsUpTextKind.LONG_PERIOD_DAY, number = periodDay, days = null)
        }
        CyclePhase.DELAYED -> {
            val daysDelayed = daysUntilNextPeriod?.let { -it } ?: return null
            if (daysDelayed <= 0) return null
            PartnerHeadsUpText(PartnerHeadsUpTextKind.PAST_EXPECTED_DATE, number = daysDelayed, days = null)
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
            text = stringResource(R.string.home_empty_state_body),
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
        LearningCard(title = stringResource(R.string.home_learning_feel_title), icon = Icons.Filled.Favorite) {
            LearningIntro(stringResource(R.string.home_learning_feel_intro))
            LearningRows(
                listOf(
                    Triple(
                        Icons.Filled.Bolt,
                        stringResource(R.string.home_learning_high_energy_title),
                        stringResource(R.string.home_learning_high_energy_body),
                    ),
                    Triple(
                        Icons.Filled.Bedtime,
                        stringResource(R.string.home_learning_slow_days_title),
                        stringResource(R.string.home_learning_slow_days_body),
                    ),
                    Triple(
                        Icons.Filled.WaterDrop,
                        stringResource(R.string.home_learning_period_cramps_title),
                        stringResource(R.string.home_learning_period_cramps_body),
                    ),
                    Triple(
                        Icons.Filled.Psychology,
                        stringResource(R.string.home_learning_mood_shifts_title),
                        stringResource(R.string.home_learning_mood_shifts_body),
                    ),
                ),
            )
        }

        LearningCard(title = stringResource(R.string.home_learning_cycle_works_title), icon = Icons.Filled.RadioButtonUnchecked) {
            LearningIntro(stringResource(R.string.home_learning_cycle_works_intro))
            listOf(
                Triple(
                    Icons.Filled.WaterDrop,
                    phasePrimaryColor(CyclePhase.MENSTRUAL),
                    stringResource(R.string.home_learning_phase_menstrual_name) to stringResource(R.string.home_learning_phase_menstrual_days),
                ),
                Triple(
                    Icons.Filled.WbSunny,
                    phasePrimaryColor(CyclePhase.FOLLICULAR),
                    stringResource(R.string.home_learning_phase_follicular_name) to stringResource(R.string.home_learning_phase_follicular_days),
                ),
                Triple(
                    Icons.Filled.AutoAwesome,
                    phasePrimaryColor(CyclePhase.OVULATION),
                    stringResource(R.string.home_learning_phase_ovulation_name) to stringResource(R.string.home_learning_phase_ovulation_days),
                ),
                Triple(
                    Icons.Filled.Bedtime,
                    phasePrimaryColor(CyclePhase.LUTEAL),
                    stringResource(R.string.home_learning_phase_luteal_name) to stringResource(R.string.home_learning_phase_luteal_days),
                ),
            ).forEachIndexed { i, (icon, color, nameDays) ->
                if (i > 0) LearningDivider()
                LearningOverviewRow(icon = icon, color = color, name = nameDays.first, days = nameDays.second)
            }
        }

        LearningPhaseDetailCard(
            title = stringResource(R.string.home_phase_menstrual), icon = Icons.Filled.WaterDrop, dayRange = stringResource(R.string.home_learning_phase_menstrual_days),
            phaseColor = phasePrimaryColor(CyclePhase.MENSTRUAL),
            overview = stringResource(R.string.home_learning_detail_menstrual_overview),
            points = listOf(
                stringResource(R.string.home_learning_detail_menstrual_point_1),
                stringResource(R.string.home_learning_detail_menstrual_point_2),
                stringResource(R.string.home_learning_detail_menstrual_point_3),
            ),
        )
        LearningPhaseDetailCard(
            title = stringResource(R.string.home_phase_follicular), icon = Icons.Filled.WbSunny, dayRange = stringResource(R.string.home_learning_phase_follicular_days),
            phaseColor = phasePrimaryColor(CyclePhase.FOLLICULAR),
            overview = stringResource(R.string.home_learning_detail_follicular_overview),
            points = listOf(
                stringResource(R.string.home_learning_detail_follicular_point_1),
                stringResource(R.string.home_learning_detail_follicular_point_2),
                stringResource(R.string.home_learning_detail_follicular_point_3),
            ),
        )
        LearningPhaseDetailCard(
            title = stringResource(R.string.home_learning_phase_ovulation_name), icon = Icons.Filled.AutoAwesome, dayRange = stringResource(R.string.home_learning_phase_ovulation_days),
            phaseColor = phasePrimaryColor(CyclePhase.OVULATION),
            overview = stringResource(R.string.home_learning_detail_ovulation_overview),
            points = listOf(
                stringResource(R.string.home_learning_detail_ovulation_point_1),
                stringResource(R.string.home_learning_detail_ovulation_point_2),
                stringResource(R.string.home_learning_detail_ovulation_point_3),
            ),
        )
        LearningPhaseDetailCard(
            title = stringResource(R.string.home_phase_luteal), icon = Icons.Filled.Bedtime, dayRange = stringResource(R.string.home_learning_phase_luteal_days),
            phaseColor = phasePrimaryColor(CyclePhase.LUTEAL),
            overview = stringResource(R.string.home_learning_detail_luteal_overview),
            points = listOf(
                stringResource(R.string.home_learning_detail_luteal_point_1),
                stringResource(R.string.home_learning_detail_luteal_point_2),
                stringResource(R.string.home_learning_detail_luteal_point_3),
            ),
        )

        LearningCard(title = stringResource(R.string.home_learning_log_title), icon = Icons.AutoMirrored.Filled.ListAlt) {
            LearningTipRow(Icons.Filled.Opacity, stringResource(R.string.home_learning_log_tip_1))
            LearningDivider()
            LearningTipRow(Icons.Filled.BarChart, stringResource(R.string.home_learning_log_tip_2))
            LearningDivider()
            LearningTipRow(Icons.Filled.Favorite, stringResource(R.string.home_learning_log_tip_3))
            LearningDivider()
            LearningTipRow(Icons.Filled.Visibility, stringResource(R.string.home_learning_log_tip_4))
        }

        LearningCard(title = stringResource(R.string.home_learning_hormones_title), icon = Icons.Filled.MonitorHeart) {
            listOf(
                Triple(
                    stringResource(R.string.home_learning_hormone_fsh_abbr),
                    stringResource(R.string.home_learning_hormone_fsh_name),
                    stringResource(R.string.home_learning_hormone_fsh_desc),
                ),
                Triple(
                    stringResource(R.string.home_learning_hormone_lh_abbr),
                    stringResource(R.string.home_learning_hormone_lh_name),
                    stringResource(R.string.home_learning_hormone_lh_desc),
                ),
                Triple(
                    stringResource(R.string.home_learning_hormone_e2_abbr),
                    stringResource(R.string.home_learning_hormone_e2_name),
                    stringResource(R.string.home_learning_hormone_e2_desc),
                ),
                Triple(
                    stringResource(R.string.home_learning_hormone_p4_abbr),
                    stringResource(R.string.home_learning_hormone_p4_name),
                    stringResource(R.string.home_learning_hormone_p4_desc),
                ),
            ).forEachIndexed { i, (abbr, name, desc) ->
                if (i > 0) LearningDivider()
                LearningAbbrRow(abbr = abbr, name = name, description = desc)
            }
        }

        LearningCard(title = stringResource(R.string.home_learning_eat_title), icon = Icons.Filled.Eco) {
            listOf(
                LearningNutritionRowData(Icons.Filled.WaterDrop, phasePrimaryColor(CyclePhase.MENSTRUAL), stringResource(R.string.home_learning_phase_menstrual_name), stringResource(R.string.home_learning_eat_period_desc)),
                LearningNutritionRowData(Icons.Filled.WbSunny, phasePrimaryColor(CyclePhase.FOLLICULAR), stringResource(R.string.home_learning_phase_follicular_name), stringResource(R.string.home_learning_eat_follicular_desc)),
                LearningNutritionRowData(Icons.Filled.AutoAwesome, phasePrimaryColor(CyclePhase.OVULATION), stringResource(R.string.home_learning_phase_ovulation_name), stringResource(R.string.home_learning_eat_ovulation_desc)),
                LearningNutritionRowData(Icons.Filled.Bedtime, phasePrimaryColor(CyclePhase.LUTEAL), stringResource(R.string.home_learning_phase_luteal_name), stringResource(R.string.home_learning_eat_luteal_desc)),
            ).forEachIndexed { i, row ->
                if (i > 0) LearningDivider()
                LearningOverviewRow(icon = row.icon, color = row.color, name = row.title, days = null, subtitle = row.description)
            }
        }

        LearningCard(title = stringResource(R.string.home_learning_tracking_title), icon = Icons.Filled.Lightbulb) {
            LearningTipRow(Icons.Filled.CalendarMonth, stringResource(R.string.home_learning_tracking_tip_1))
            LearningDivider()
            LearningTipRow(Icons.Filled.Schedule, stringResource(R.string.home_learning_tracking_tip_2))
            LearningDivider()
            LearningTipRow(Icons.Filled.Bedtime, stringResource(R.string.home_learning_tracking_tip_3))
            LearningDivider()
            LearningTipRow(Icons.Filled.Autorenew, stringResource(R.string.home_learning_tracking_tip_4))
        }
    }
}

private data class LearningNutritionRowData(val icon: ImageVector, val color: Color, val title: String, val description: String)

@Composable
private fun LearningCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    HomeGlassCard(
        title = title,
        phase = CyclePhase.UNKNOWN,
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
        phase = CyclePhase.UNKNOWN,
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
// TabView paging (4 foods per page + dot indicator) -- Android still renders
// all eat-more foods in a single vertical list. The initial loading shimmer is
// aligned now, but the pager/dot treatment remains a larger deferred gap. The
// "Avoid" section, phase tips, and condition tips stay on the standalone
// Recommendations screen only, matching iOS (which doesn't show them in the
// day-detail card either).

@Composable
private fun NutritionCard(
    phase: CyclePhase,
    foods: List<RecommendationFoodUi>,
    isLoading: Boolean,
    accentColor: Color,
) {
    HomeGlassCard(
        title = stringResource(R.string.home_nutrition_title),
        phase = phase,
        accentColor = accentColor,
        hasCycleData = true,
        icon = Icons.Filled.Eco,
    ) {
        when {
            isLoading && foods.isEmpty() -> NutritionLoadingState(phase = phase, accentColor = accentColor)
            foods.isEmpty() -> {
            Text(
                text = stringResource(R.string.home_nutrition_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
            )
            }
            else -> {
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
}

@Composable
private fun NutritionLoadingState(
    phase: CyclePhase,
    accentColor: Color,
) {
    val shimmerBase = accentColor.copy(alpha = if (phase == CyclePhase.MENSTRUAL) 0.15f else 0.08f)

    Column(
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
    ) {
        repeat(4) { index ->
            if (index > 0) {
                HorizontalDivider(
                    color = shimmerBase.copy(alpha = 0.72f),
                    modifier = Modifier.padding(start = 46.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoadingShimmer(
                    width = 36.dp,
                    height = 36.dp,
                    baseColor = shimmerBase,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LoadingShimmer(
                        width = if (index % 2 == 0) 142.dp else 126.dp,
                        height = 14.dp,
                        baseColor = shimmerBase,
                    )
                    LoadingShimmer(
                        width = if (index % 2 == 0) 104.dp else 88.dp,
                        height = 12.dp,
                        baseColor = shimmerBase,
                    )
                }
                LoadingShimmer(
                    width = 16.dp,
                    height = 16.dp,
                    baseColor = shimmerBase,
                )
            }
        }
    }
}

@Composable
private fun SakhiInsightCard(
    phase: CyclePhase,
    insight: String?,
    isLoading: Boolean,
    isPartnerMode: Boolean,
    accentColor: Color,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
) {
    // `isRefreshing` must keep the card visible too -- `refreshInsight()` clears
    // `insight` back to null while it re-fetches, and without this check the
    // whole card would vanish mid-refresh instead of showing the loading spinner.
    if (insight == null && !isLoading && !isRefreshing) return

    HomeGlassCard(
        title = stringResource(
            if (isPartnerMode) {
                R.string.home_insight_partner_title
            } else {
                R.string.home_insight_self_title
            },
        ),
        phase = phase,
        accentColor = accentColor,
        hasCycleData = true,
        icon = Icons.Filled.AutoAwesome,
        // Matches iOS's real "Refresh" button on this exact card
        // (`HomeDayDetailGlassView+Cards.swift`'s `sakhiInsightCard`) -- hidden
        // only before any insight has ever loaded (the very first spinner-only
        // render); kept visible (as a spinner) through `isRefreshing` even
        // though `refreshInsight()` clears `insight` back to null meanwhile.
        onRefresh = if (insight != null || isRefreshing) onRefresh else null,
        isRefreshing = isRefreshing,
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
                text = stringResource(R.string.home_insight_powered),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
            )
        }
    }
}

private data class HomePhasePalette(
    val primary: Color,
    val secondary: Color,
    val surface: Color,
    val bgTop: Color,
    val bgMid: Color,
    val bgBot: Color,
    val tileFill: Color,
    val tileStroke: Color,
)

@Composable
private fun rememberHomePhasePalette(phase: CyclePhase): HomePhasePalette {
    val isDark = LocalSakhiDarkTheme.current
    return remember(phase, isDark) {
        val resolved = SakhiColors.resolved(isDark).forPhase(
            if (phase == CyclePhase.UNKNOWN) CyclePhase.FOLLICULAR else phase,
        )
        HomePhasePalette(
            primary = resolved.primary.toComposeColor(),
            secondary = resolved.secondary.toComposeColor(),
            surface = resolved.surface.toComposeColor(),
            bgTop = resolved.bgTop.toComposeColor(),
            bgMid = resolved.bgMid.toComposeColor(),
            bgBot = resolved.bgBot.toComposeColor(),
            tileFill = resolved.tileFill.toComposeColor(),
            tileStroke = resolved.tileStroke.toComposeColor(),
        )
    }
}

@Composable
private fun homeBackgroundBrush(phasePalette: HomePhasePalette, hasCycleData: Boolean): Brush {
    return if (!hasCycleData) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surface,
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                phasePalette.bgTop,
                phasePalette.bgMid,
                phasePalette.bgBot,
            ),
        )
    }
}

@Composable
private fun homeSecondaryTextColor(
    phasePalette: HomePhasePalette,
    hasCycleData: Boolean,
    isMenstrual: Boolean,
): Color = when {
    !hasCycleData -> MaterialTheme.colorScheme.onSurfaceVariant
    isMenstrual -> Color.White.copy(alpha = 0.90f)
    else -> phasePalette.primary.copy(alpha = 0.72f)
}

@Composable
private fun HomeTopBar(
    uiState: HomeUiState,
    heroScrollProgress: Float,
    phasePalette: HomePhasePalette,
    onOpenProfile: () -> Unit,
    onOpenCare: () -> Unit,
    onOpenCalendar: () -> Unit,
    onResetToToday: () -> Unit,
) {
    val context = LocalContext.current
    val hasCycleData = uiState.hasCycleData
    val isMenstrual = uiState.phase == CyclePhase.MENSTRUAL
    val foreground = when {
        !hasCycleData -> MaterialTheme.colorScheme.primary
        isMenstrual -> Color.White
        else -> phasePalette.primary
    }
    val iconBackground = if (!hasCycleData) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        phasePalette.secondary.copy(alpha = 0.12f)
    }
    val iconStroke = foreground.copy(alpha = 0.14f)
    val phaseLabel = if (hasCycleData) {
        uiState.phase.displayName(context)
    } else if (uiState.session?.isViewingOwnData == false) {
        stringResource(R.string.home_phase_first_period_partner)
    } else {
        stringResource(R.string.home_phase_first_period_self)
    }
    val heroSummary = heroText(context, uiState)

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val isToday = uiState.selectedDate == DateConverter.today()
            Text(
                text = DateConverter.formatForDisplay(uiState.selectedDate),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = foreground,
                // Matches iOS's real `HomeView.topBar` date label exactly: tapping
                // it opens Calendar when viewing today, or resets straight back
                // to today when viewing any other date -- verified there is no
                // separate day-tap strip in the real iOS source.
                modifier = Modifier.clickable(onClick = if (isToday) onOpenCalendar else onResetToToday),
            )
            HeroTopBarSubtitle(
                phaseName = phaseLabel,
                heroContentBig = heroSummary.big,
                heroContentSub = heroSummary.sub,
                foreground = foreground,
                progress = heroScrollProgress,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopBarIconButton(
                icon = Icons.Filled.Menu,
                contentDescription = stringResource(R.string.home_open_profile_content_description),
                foreground = foreground,
                background = iconBackground,
                stroke = iconStroke,
                onClick = onOpenProfile,
            )
            TopBarIconButton(
                icon = Icons.Filled.People,
                contentDescription = stringResource(R.string.home_open_care_content_description),
                foreground = foreground,
                background = iconBackground,
                stroke = iconStroke,
                onClick = onOpenCare,
            )
        }
    }
}

@Composable
private fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    foreground: Color,
    background: Color,
    stroke: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(background, CircleShape)
            .border(0.5.dp, stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = foreground)
    }
}

@Composable
private fun HeroTopBarSubtitle(
    phaseName: String,
    heroContentBig: String,
    heroContentSub: String,
    foreground: Color,
    progress: Float,
) {
    Box(
        modifier = Modifier.height(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.alpha(1f - progress),
        ) {
            Text(
                text = phaseName,
                style = MaterialTheme.typography.labelMedium,
                color = foreground.copy(alpha = 0.72f),
            )
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = null,
                tint = foreground.copy(alpha = 0.50f),
                modifier = Modifier.size(12.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.alpha(progress),
        ) {
            if (heroContentBig.isNotEmpty()) {
                Text(
                    text = heroContentBig,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = foreground,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground.copy(alpha = 0.45f),
                )
            }
            Text(
                text = heroContentSub,
                style = MaterialTheme.typography.labelMedium,
                color = foreground.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun CyclePhase.displayName(context: Context): String = when (this) {
    CyclePhase.MENSTRUAL -> context.getString(R.string.home_phase_menstrual)
    CyclePhase.FOLLICULAR -> context.getString(R.string.home_phase_follicular)
    CyclePhase.OVULATION -> context.getString(R.string.home_phase_ovulation)
    CyclePhase.LUTEAL -> context.getString(R.string.home_phase_luteal)
    CyclePhase.DELAYED -> context.getString(R.string.home_phase_delayed)
    CyclePhase.UNKNOWN -> context.getString(R.string.home_phase_first_period_self)
}
