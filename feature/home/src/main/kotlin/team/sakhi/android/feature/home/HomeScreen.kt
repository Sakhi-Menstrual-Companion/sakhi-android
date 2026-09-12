package team.sakhi.android.feature.home

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import team.sakhi.android.common.CycleInsightAdapter
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material.icons.rounded.Refresh
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
import team.sakhi.android.designsystem.SakhiFontSize
import team.sakhi.android.designsystem.SakhiMotion
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.rememberSakhiFlingBehavior
import team.sakhi.android.designsystem.sakhiSeparator
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.designsystem.SakhiPhasePalette
import team.sakhi.android.designsystem.rememberPhasePalette
import team.sakhi.android.designsystem.phaseCardFill
import team.sakhi.android.designsystem.phaseCardStroke
import team.sakhi.android.designsystem.phasePageBackgroundBrush
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.phasePrimaryColor
import team.sakhi.android.designsystem.toComposeColor
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.ui.LoadingShimmer
import team.sakhi.android.ui.PhaseBadge
import team.sakhi.android.ui.SakhiAnimatedValue
import team.sakhi.android.ui.SakhiListDivider
import team.sakhi.android.ui.flowDisplayName
import team.sakhi.android.ui.SakhiBottomActionBar
import team.sakhi.android.feature.logging.LoggingViewModel
import team.sakhi.android.feature.recommendations.RecommendationFoodUi
import team.sakhi.android.feature.recommendations.RecommendationsViewModel
import team.sakhi.cycle.CalendarMarker
import team.sakhi.date.DateConverter
import team.sakhi.logging.LogTokenEncoder
import team.sakhi.logging.Mood
import team.sakhi.logging.Symptom
import team.sakhi.cycle.CyclePhaseInsight
import team.sakhi.models.CyclePhase
import team.sakhi.models.FlowIntensity
import team.sakhi.models.PeriodLog
import team.sakhi.design.SakhiColors
import team.sakhi.sync.SyncRuntimeState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Spacer
import team.sakhi.recommendations.FoodEmoji
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import team.sakhi.android.designsystem.sakhiGroupedBackground
import team.sakhi.android.designsystem.sakhiSystemGray5
import androidx.compose.foundation.layout.navigationBarsPadding

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
    /**
     * Opens the activity/history view. iOS's "How you feel" card is tappable across its
     * whole surface (`.contentShape(...).onTapGesture { presentActivitySheet() }` in
     * `HomeDayDetailGlassView+Cards.swift`); the "History" capsule is only the visual
     * affordance for it. Android drew the capsule, chevron and all, but both call sites
     * passed an empty lambda, so the card did nothing when tapped.
     */
    onOpenLogHistory: () -> Unit = {},
    onOpenCare: () -> Unit = {},
    /** Opens the in-app inbox behind the bell. */
    onOpenNotifications: () -> Unit = {},
    /** Drives the bell's badge; zero hides it. */
    unreadNotificationCount: Int = 0,
    onOpenCalendar: () -> Unit = {},
    /** Counterpart to [onOpenCalendar] — see the `onPhaseTap` call site. */
    onCloseCalendar: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onQuickLogClick: (LocalDate) -> Unit = {},
    onLockedLogClick: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
    recommendationsViewModel: RecommendationsViewModel = koinViewModel(),
    quickLogViewModel: LoggingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recoState by recommendationsViewModel.uiState.collectAsStateWithLifecycle()
    val quickLogUiState by quickLogViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val phasePalette = rememberPhasePalette(uiState.phase)
    // Taken from the Home phase palette, NOT `phasePrimaryColor(...)`. The latter
    // returns the raw `PhaseVisualStyle.colorHex` (#E85787 for menstrual), which
    // bypasses the white-in-period-mode override iOS applies when it builds this same
    // palette (`PhaseColorManager.swift:94`). Using the raw token here put pink text
    // and icons on the pink menstrual background.
    val accentColor = phasePalette.primary
    val hapticManager = koinInject<AndroidHapticManager>()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // iOS's phase label scrolls the day-detail to the "phaseInfo" card
    // (`proxyReader.scrollTo("phaseInfo", anchor: .top)`). A lazy list scrolls by item
    // index rather than by pixel offset, so the list below records the phase card's index
    // as it declares its items. A plain holder, not state: it is written while the item
    // list is being built, and writing snapshot state there would invalidate that build.
    val phaseCardIndex = remember { intArrayOf(-1) }
    val density = LocalDensity.current
    // Deliberately NOT `by` (a delegated read). This value changes continuously across
    // 120dp of scroll, so reading it here — in `HomeScreen`'s own composition scope —
    // invalidated this whole 3000-line composable on essentially every scroll frame, and
    // every card below it re-ran with it. That was the single largest source of scroll jank
    // on this screen.
    //
    // Kept as a `State` and handed down as a `() -> Float` instead, so the read is recorded
    // by whichever `graphicsLayer` block actually consumes it. That moves the invalidation
    // from the composition phase to the draw phase: scrolling now re-draws the hero without
    // recomposing anything at all.
    val heroScrollProgressState = remember(listState, density) {
        derivedStateOf {
            with(density) {
                // The hero IS the first item, so while it is still the first visible one its
                // own scroll offset is the distance scrolled. Once it is gone past, the
                // progress is simply finished.
                val scrolled = if (listState.firstVisibleItemIndex > 0) {
                    Float.MAX_VALUE
                } else {
                    listState.firstVisibleItemScrollOffset.toFloat()
                }
                ((scrolled - 20.dp.toPx()) / 120.dp.toPx()).coerceIn(0f, 1f)
            }
        }
    }
    // Remembered so the lambda identity is stable across recompositions — an allocated-fresh
    // lambda would be a new instance each time and would defeat skipping in the children it
    // is passed to, which is the same trap this change exists to close.
    val heroScrollProgress: () -> Float = remember(heroScrollProgressState) {
        { heroScrollProgressState.value }
    }

    // The eight fields the hero and top bar actually render, projected out of the 24-field
    // `uiState`. Recomputed whenever `uiState` changes, but it compares equal unless one of
    // those eight moved, so a sync tick or a cycle reload no longer recomposes either of them.
    val heroState = remember(uiState) { uiState.toHeroState() }

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
            .background(
                phasePageBackgroundBrush(
                    phase = uiState.phase,
                    // `|| isLoadingCycle` on purpose. The no-data branch of this brush paints
                    // the neutral brand gradient, which is right for a woman who genuinely has
                    // nothing logged — but it was also being used for the moment BEFORE her
                    // data arrives, so every launch started on the brand colour and then
                    // changed to her phase. Treating "still loading" as the phase path takes
                    // the FOLLICULAR palette instead (rememberPhasePalette maps UNKNOWN to it),
                    // which is the same neutral-cycle colour Home settles on, so nothing
                    // changes underneath her.
                    hasCycleData = uiState.hasCycleData || uiState.isLoadingCycle,
                ),
            ),
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
                hero = heroState,
                heroScrollProgress = heroScrollProgress,
                phasePalette = phasePalette,
                onOpenProfile = {
                    hapticManager.selection()
                    onOpenProfile()
                },
                onOpenNotifications = {
                    hapticManager.selection()
                    onOpenNotifications()
                },
                unreadNotificationCount = unreadNotificationCount,
                onOpenCalendar = {
                    hapticManager.selection()
                    onOpenCalendar()
                },
                onResetToToday = {
                    hapticManager.selection()
                    viewModel.selectDate(DateConverter.today())
                },
                onPhaseTap = {
                    hapticManager.selection()
                    // iOS's `onPhaseTap` is `showCalendar = false` *then*
                    // `phaseScrollTrigger += 1` -- the phase card it scrolls to lives
                    // under the calendar sheet, so without closing the calendar first
                    // the scroll happened behind it and the tap looked inert.
                    onCloseCalendar()
                    scope.launch {
                        // iOS: withAnimation(.easeInOut(duration: 0.42)) then
                        // scrollTo("phaseInfo", anchor: .top).
                        val index = phaseCardIndex[0]
                        if (index >= 0) {
                            val onScreen = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == index }
                            if (onScreen != null) {
                                // Already visible: scroll by exactly its distance from the
                                // top, so iOS's 0.42s ease can be matched. `animateScrollToItem`
                                // takes no animation spec.
                                listState.animateScrollBy(
                                    onScreen.offset.toFloat(),
                                    animationSpec = tween(
                                        durationMillis = 420,
                                        easing = SakhiMotion.IosEaseInOut,
                                    ),
                                )
                            } else {
                                listState.animateScrollToItem(index)
                            }
                        }
                    }
                },
            )
            // A LazyColumn, not a Column + verticalScroll.
            //
            // Every card below used to be composed and measured before Home's first frame
            // could be drawn, including the ones under the fold, on the single most
            // important screen in the app and on the cold-start path. Lazily, only the cards
            // on screen are built.
            //
            // One item per CARD, each with a stable key, so a card arriving or leaving (a log
            // lands, the empty state goes) moves only itself and its neighbours.
            //
            // This replaces an `animateContentSize` that sat on the scrolling container. That
            // could never have done anything: the container is `weight(1f)`, so its height is
            // fixed by the parent and there was no size change to animate. `Modifier
            // .animateItem()` on each card is the real version of what that was reaching for,
            // it animates the cards' PLACEMENT as the list above them changes height.
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
                flingBehavior = rememberSakhiFlingBehavior(),
            ) {
            // Counts items as they are declared, so the phase card's index is known without
            // having to guess it from the branches below. A plain holder, not snapshot
            // state: writing state from inside this lambda would re-invalidate it.
            var itemIndex = 0
            fun nextIndex(): Int = itemIndex++

            val canShowHero = uiState.session?.isViewingOwnData == true || uiState.canViewPredictions
            // Hidden ONLY when there is genuinely nothing to show. It used to be hidden for
            // the whole of `isLoadingCycle`, so the hero unmounted and remounted on every
            // load — the content visibly disappearing and reappearing — and the values
            // restored from the last session were seeded but never drawn, because loading was
            // still true when the first frame went out.
            //
            // With data present the hero stays mounted and its values cross-fade underneath,
            // which is what the `AnimatedContent` below is for.
            if (canShowHero && (!uiState.isLoadingCycle || uiState.hasCycleData)) {
                nextIndex()
                item(key = "hero", contentType = "hero") {
                    // iOS springs the hero when the day or phase changes rather than
                    // swapping it instantly -- `homePhaseTransition` is
                    // `.spring(response: 0.5, dampingFraction: 0.88, blendDuration: 0.14)`
                    // applied to `snapshot.displayPhase` (HomeView.swift). Android had no
                    // date-change animation at all: `AnimatedContent` and
                    // `animateFloatAsState` were imported here but never used.
                    //
                    // Keyed on the selected date AND the phase, because either can change
                    // the hero's content on its own (paging to another day of the same
                    // phase, or a phase boundary on the same day).
                    AnimatedContent(
                        // Keyed on what the hero actually RENDERS, not just the date and phase.
                        // Keyed on the pair alone, a sync that landed a new cycle day or a new
                        // countdown swapped the numbers instantly while the fade sat unused,
                        // because neither key had changed.
                        targetState = heroState,
                        transitionSpec = {
                            fadeIn(animationSpec = spring(stiffness = HomeHeroSpringStiffness)) togetherWith
                                fadeOut(animationSpec = spring(stiffness = HomeHeroSpringStiffness))
                        },
                        label = "home_hero",
                        modifier = Modifier.animateItem(),
                    ) { _ ->
                        HeroSection(
                            hero = heroState,
                            accentColor = accentColor,
                            phasePalette = phasePalette,
                            scrollProgress = heroScrollProgress,
                        )
                    }
                }
            }

            // Sync state is shown only while something is actually happening, matching
            // iOS's `topStatusBanner`, which renders solely for
            // `isRefreshingWithStaleData` / `hasStaleFailure` and is invisible the rest
            // of the time. Android used to pin a permanent chip here that read
            // "Sync idle" on a healthy screen — a bring-up debug affordance, not
            // product UI, and iOS has no equivalent. Idle and Success now render
            // nothing, so the chip only ever appears when it is telling the user
            // something real.
            if (uiState.syncState.isWorthShowing()) {
                nextIndex()
                item(key = "sync-chip", contentType = "chip") {
                    Box(modifier = Modifier.animateItem()) {
                        StateChip(
                            label = syncLabel(context, uiState.syncState),
                            tint = syncTint(uiState.syncState, accentColor),
                        )
                    }
                }
            }

            uiState.error?.let { error ->
                nextIndex()
                item(key = "error", contentType = "error") {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            val isPartnerMode = uiState.session?.isViewingOwnData == false

            // Branching matches iOS `HomeDayDetailGlassView.body` exactly:
            // partner+empty -> partnerNoDataCard; empty (own) -> learningPhaseCards;
            // partner+data -> checklist/loggedDetails/nutrition/headsUp/phaseInfo;
            // own+data -> loggedDetails/nutrition/cycleDetails/phaseInfo.
            // Same rule as the hero above, and this is the gate that actually produced the
            // blank screen: it hid EVERY card on Home for the whole of `isLoadingCycle`, so
            // the values restored from the last session were seeded, the hero drew them, and
            // then nothing else did. Loading is not a reason to show her an empty page when
            // there is real data to show.
            if (!uiState.isLoadingCycle || uiState.hasCycleData) {
                if (isPartnerMode && !uiState.hasCycleData) {
                    nextIndex()
                    item(key = "partner-no-data", contentType = "card") {
                        Box(modifier = Modifier.animateItem()) {
                            PartnerNoDataCard()
                        }
                    }
                } else if (!uiState.hasCycleData) {
                    nextIndex()
                    item(key = "empty-state", contentType = "card") {
                        Box(modifier = Modifier.animateItem()) {
                            EmptyStateCard(accentColor = accentColor)
                        }
                    }
                    nextIndex()
                    item(key = "learning-phases", contentType = "card") {
                        Box(modifier = Modifier.animateItem()) {
                            LearningPhaseCards()
                        }
                    }
                } else if (isPartnerMode) {
                    nextIndex()
                    item(key = "partner-checklist", contentType = "card") {
                        val checklistViewModel: PartnerChecklistViewModel = koinViewModel()
                        val checklistState by checklistViewModel.uiState.collectAsStateWithLifecycle()
                        LaunchedEffect(uiState.phase, uiState.dayInCycle, uiState.daysUntilNextPeriod) {
                            checklistViewModel.loadOrGenerate(
                                cyclePhase = uiState.phase,
                                cycleDay = uiState.dayInCycle ?: 1,
                                daysUntilNextPeriod = uiState.daysUntilNextPeriod,
                            )
                        }
                        Box(modifier = Modifier.animateItem()) {
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
                        }
                    }
                    nextIndex()
                    item(key = "logged-details", contentType = "card") {
                        Box(modifier = Modifier.animateItem()) {
                            LoggedDetailsCard(
                                log = uiState.selectedLog,
                                isPartnerMode = true,
                                phase = uiState.phase,
                                accentColor = accentColor,
                                onClick = onOpenLogHistory,
                            )
                        }
                    }
                    if (recoState.canViewPhaseRecommendations) {
                        nextIndex()
                        item(key = "nutrition", contentType = "card") {
                            Box(modifier = Modifier.animateItem()) {
                                NutritionCard(
                                    phase = uiState.phase,
                                    foods = recoState.eatMoreFoods,
                                    isLoading = recoState.isLoading,
                                    accentColor = accentColor,
                                )
                            }
                        }
                    }
                    partnerHeadsUpText(context, uiState.phase, uiState.dayInCycle, uiState.daysUntilNextPeriod)?.let { headsUp ->
                        nextIndex()
                        item(key = "partner-heads-up", contentType = "card") {
                            Box(modifier = Modifier.animateItem()) {
                                PartnerHeadsUpCard(
                                    text = headsUp,
                                    accentColor = accentColor,
                                    phase = uiState.phase,
                                )
                            }
                        }
                    }
                    phaseCardIndex[0] = nextIndex()
                    item(key = "phase-info", contentType = "card") {
                        PhaseInfoCard(
                            phase = uiState.phase,
                            isPartnerMode = true,
                            accentColor = accentColor,
                            modifier = Modifier.animateItem(),
                        )
                    }
                    // Real gap found in the second parity sweep: iOS's real
                    // `sakhiInsightCard` renders in partner mode too (its own
                    // title branches on `isPartnerMode` -- "How to be there for
                    // her today"), but this card was never called at all in
                    // Android's partner branch, so partners never saw it.
                    nextIndex()
                    item(key = "insight", contentType = "card") {
                        Box(modifier = Modifier.animateItem()) {
                            SakhiInsightCard(
                                phase = uiState.phase,
                                insight = recoState.aiInsight,
                                isLoading = recoState.isLoading,
                                isPartnerMode = true,
                                accentColor = accentColor,
                                onRefresh = recommendationsViewModel::refreshInsight,
                                isRefreshing = recoState.isRefreshingInsight,
                            )
                        }
                    }
                } else {
                    // Order matches iOS `HomeDayDetailGlassView.body`'s own-data
                    // branch exactly: loggedDetails -> nutrition -> cycleDetails
                    // -> phaseInfo (`SakhiInsightCard` stays last -- it isn't one
                    // of that exact 4-card list, kept where it already was).
                    nextIndex()
                    item(key = "logged-details", contentType = "card") {
                        Box(modifier = Modifier.animateItem()) {
                            LoggedDetailsCard(
                                log = uiState.selectedLog,
                                isPartnerMode = false,
                                phase = uiState.phase,
                                accentColor = accentColor,
                                onClick = onOpenLogHistory,
                            )
                        }
                    }
                    if (recoState.canViewPhaseRecommendations) {
                        nextIndex()
                        item(key = "nutrition", contentType = "card") {
                            Box(modifier = Modifier.animateItem()) {
                                NutritionCard(
                                    phase = uiState.phase,
                                    foods = recoState.eatMoreFoods,
                                    isLoading = recoState.isLoading,
                                    accentColor = accentColor,
                                )
                            }
                        }
                    }
                    uiState.currentCycle?.let { cycle ->
                        nextIndex()
                        item(key = "cycle-details", contentType = "card") {
                            Box(modifier = Modifier.animateItem()) {
                                CycleDetailsCard(
                                    cycle = cycle,
                                    periodLogDates = uiState.periodLogDates,
                                    dayInCycle = uiState.dayInCycle ?: 1,
                                    cycleLength = uiState.cycleLength ?: 28,
                                    phase = uiState.phase,
                                    accentColor = accentColor,
                                    cyclesAnalyzed = uiState.cyclesAnalyzed,
                                    shortestCycle = uiState.shortestCycle,
                                    longestCycle = uiState.longestCycle,
                                )
                            }
                        }
                    }
                    phaseCardIndex[0] = nextIndex()
                    item(key = "phase-info", contentType = "card") {
                        PhaseInfoCard(
                            phase = uiState.phase,
                            isPartnerMode = false,
                            accentColor = accentColor,
                            modifier = Modifier.animateItem(),
                        )
                    }
                    nextIndex()
                    item(key = "insight", contentType = "card") {
                        Box(modifier = Modifier.animateItem()) {
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
            }
            }

            // `MainActivity` now draws a fully transparent system navigation bar (was an
            // opaque AndroidX safety scrim on 3-button-nav devices), so this bar's own content
            // extends behind the OS back/home/recents buttons unless it claims that inset
            // itself. The calendar sheet's copy of this same bar now claims the same
            // inset directly: it used to rely on being inside a `ModalBottomSheet`, which
            // reserved the inset for it, but that stopped being true when the calendar
            // became an in-tree overlay drawn over Home.
            Box(modifier = Modifier.navigationBarsPadding()) {
            SakhiBottomActionBar(
                phase = uiState.phase,
                accentColor = accentColor,
                // iOS passes `logFill: cardFill` and `logIconColor: standardAccent`
                // separately. On a period day `accentColor` is white (the primary
                // override that keeps text readable on the saturated background), so
                // using it as the button fill produced a white glyph on a white circle —
                // the log button, the screen's main action, rendered blank. The fill is
                // the phase surface, exactly as `cardFill` resolves on iOS.
                // iOS passes `logFill: cardFill` for EVERY phase, not just the period
                // one. Special-casing menstrual (as this did) left the non-period branch
                // on `accentColor`, which in dark resolves to the near-white Rose primary
                // (#FAF4F8) — so the white glyph sat on a near-white circle and the log
                // button rendered completely blank. Same defect as the period-day case,
                // just in the cell nothing had rendered until the follicular/dark
                // screenshot existed.
                logFill = phaseCardFill(
                    phase = uiState.phase,
                    hasCycleData = uiState.hasCycleData,
                ),
                // Third and final instance of the same bug. iOS is
                // `logIconColor: standardAccent` (`HomeDayDetailGlassView+ActionBar:42`) --
                // Android hardcoded white, which the comment above already described
                // correctly and the code then ignored. The fill was corrected twice (period
                // white-on-white, then dark near-white-on-near-white) but the *glyph* stayed
                // white, so off a period day it sat on the light `cardFill` at almost no
                // contrast -- on the screen's primary action, for most of the cycle.
                // `accentColor` is Android's `standardAccent`: it is already passed as
                // `accent` above, and it resolves to white on a period day (keeping the
                // glyph readable on the saturated fill) and to the phase accent otherwise.
                logIconColor = accentColor,
                // iOS: logStrokeColor = standardAccent.opacity(0.14).
                logStrokeColor = accentColor.copy(alpha = 0.14f),
                isPartnerMode = uiState.session?.isViewingOwnData == false,
                canLog = uiState.canLogPeriod,
                onLockedLogClick = onLockedLogClick.takeIf { uiState.session?.isViewingOwnData == false },
                hasLoggedForDate = uiState.hasLoggedForSelectedDate,
                isLogSaving = quickLogUiState.isSaving,
                selectedFlow = quickLogUiState.selectedFlow,
                selectedDate = uiState.selectedDate,
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

/**
 * Whether this sync state is worth putting on screen at all.
 *
 * Idle and Success are the steady, healthy states — surfacing them permanently just
 * adds noise ("Sync idle" sitting under the hero on a perfectly fine screen). Only
 * in-progress and problem states earn space, mirroring iOS's `topStatusBanner`.
 */
private fun SyncRuntimeState.isWorthShowing(): Boolean = when (this) {
    SyncRuntimeState.Idle, is SyncRuntimeState.Success -> false
    // Routine syncing is reported next to the phase name in the top bar, with the rotating
    // icon. Showing it here too put a second "Syncing" capsule on screen for the same event.
    // Stale and Failed stay: those are problems she may need to act on, not routine progress.
    SyncRuntimeState.Syncing -> false
    else -> true
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

/**
 * Hero headline + subtitle, a direct port of iOS's `HomeView.heroContent(snapshot:)`.
 *
 * Switches on the shared engine's own `PredictionStatusKind` rather than re-deriving
 * anything from the phase, so both platforms render the same case for the same data.
 * The previous version branched on `CyclePhase` and read `daysUntilNextPeriod`, which
 * could not express "in period", "expected but not logged", or "since expected" at
 * all — that is why Android showed a countdown on a day iOS calls "Day 1 of your
 * period".
 *
 * Note in-period reads "Day 3", not "3 Days"; only the countdown cases are plural.
 */
private fun heroText(context: Context, hero: HomeHeroState): HeroText {
    val her = hero.session?.isViewingOwnData == false
    val prediction = hero.prediction

    fun notStarted() = HeroText(
        "",
        context.getString(
            if (her) R.string.home_hero_not_started_partner else R.string.home_hero_not_started_self,
        ),
    )

    // Say nothing rather than something wrong. Until the first read lands we do not know
    // whether she has tracked before, and "start tracking today" is a confident claim we have
    // not earned yet. The top bar's rotating icon already says work is in progress.
    if (hero.isLoading && !hero.hasCycleData) return HeroText("", "")

    if (prediction == null || !hero.hasCycleData) return notStarted()

    fun dayLabel(day: Int) = context.getString(R.string.home_hero_day_number, day)
    fun daysLabel(n: Int) = context.resources.getQuantityString(R.plurals.home_day_count, n, n)

    return when (prediction.status) {
        CyclePhaseInsight.PredictionStatusKind.IN_PERIOD -> HeroText(
            dayLabel(prediction.periodDay),
            // Inside the predicted window but this day has no log yet -> "expected".
            if (hero.hasLoggedForSelectedDate) {
                context.getString(
                    if (her) R.string.home_hero_period_of_her else R.string.home_hero_period_of_your,
                )
            } else {
                context.getString(
                    if (her) {
                        R.string.home_hero_period_expected_of_her
                    } else {
                        R.string.home_hero_period_expected_of_your
                    },
                )
            },
        )

        CyclePhaseInsight.PredictionStatusKind.TODAY -> HeroText(
            dayLabel(prediction.periodDay),
            context.getString(
                if (her) {
                    R.string.home_hero_period_expected_of_her
                } else {
                    R.string.home_hero_period_expected_of_your
                },
            ),
        )

        CyclePhaseInsight.PredictionStatusKind.UPCOMING ->
            if (prediction.confidence == 0) {
                notStarted()
            } else {
                HeroText(
                    daysLabel(prediction.daysUntil),
                    context.getString(
                        if (her) R.string.home_hero_until_next_her else R.string.home_hero_until_next_self,
                    ),
                )
            }

        CyclePhaseInsight.PredictionStatusKind.DELAYED -> HeroText(
            daysLabel(prediction.daysDelayed),
            context.getString(
                if (her) R.string.home_hero_period_delayed_her else R.string.home_hero_period_delayed_self,
            ),
        )

        CyclePhaseInsight.PredictionStatusKind.NOT_LOGGED -> HeroText(
            daysLabel(prediction.daysDelayed),
            context.getString(
                if (her) R.string.home_hero_since_expected_her else R.string.home_hero_since_expected_self,
            ),
        )

        CyclePhaseInsight.PredictionStatusKind.NO_RECENT_DATA -> notStarted()
    }
}

@Composable
private fun HeroSection(
    hero: HomeHeroState,
    accentColor: Color,
    phasePalette: SakhiPhasePalette,
    // A provider, not a `Float`. Taking the value would mean the caller has to read the
    // scroll state during composition to pass it, which is exactly what used to recompose
    // all of Home on every frame. Read inside the `graphicsLayer` block below instead.
    scrollProgress: () -> Float,
    onTipClick: () -> Unit = {},
) {
    val context = LocalContext.current
    // Builds several localised strings; remembered so it only re-runs when its inputs
    // actually change, not on every recomposition of the hero.
    val text = remember(context, hero) { heroText(context, hero) }
    val isPeriodMode = hero.phase == CyclePhase.MENSTRUAL

    // iOS `HomeDayDetailGlassView`: the hero sits on a saturated background in period
    // mode, so its text flips to white there. Android previously used the phase's
    // primary for the big number unconditionally, which on the menstrual background is
    // pink-on-pink and effectively invisible -- the "Day 1" that could barely be read.
    // `accentColor` is the phase primary, which iOS already forces to white in period
    // mode (see rememberPhasePalette), so no special case is needed here.
    val bigColor = accentColor
    val subColor = homeSecondaryTextColor(
        phasePalette = phasePalette,
        hasCycleData = hero.hasCycleData,
        isMenstrual = isPeriodMode,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Single read per draw, reused for all four properties.
                val progress = scrollProgress()
                alpha = 1f - (0.38f * progress)
                translationY = -36.dp.toPx() * progress
                scaleX = 1f - (0.08f * progress)
                scaleY = 1f - (0.08f * progress)
            }
            // iOS `heroSection`: .padding(.horizontal, 24) / .padding(.top, .m = 16) /
            // .padding(.bottom, 32). Android had none of these, so the countdown sat
            // flush against the top bar and the first card with no breathing room.
            //
            // The bottom is 12 rather than 32 on purpose: iOS's hero sits in a
            // `VStack(spacing: 0)` so its own 32 is the entire hero-to-first-card gap,
            // whereas this Column's parent already contributes `space5` (20) between
            // every child. 12 + 20 = the same 32 iOS ends up with. Card-to-card spacing
            // is untouched and already matches iOS's `DS.Spacing.ml` (20).
            .padding(horizontal = SakhiSpacing.space6)
            .padding(top = SakhiSpacing.space4, bottom = SakhiSpacing.space3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2 + SakhiSpacing.space1 / 2),
    ) {
        if (text.big.isNotEmpty()) {
            // Changes INSTANTLY, on purpose. iOS rolls this figure
            // (`.contentTransition(.numericText())` with a spring); both that and a plain
            // fade were tried here and rejected by Karan on 2026-09-12, "fade bhi na ho,
            // ekdum se badle, sirf numbers". The reading she is watching should simply be
            // the new reading; the things around it are what transition. See the header of
            // SakhiAnimatedValue.
            Text(
                text = text.big,
                // iOS: `.font(.lato(68, .regular))`, single line, shrink-to-fit at 0.65.
                fontSize = 68.sp,
                lineHeight = 76.sp,
                fontWeight = FontWeight.Normal,
                color = bigColor,
                maxLines = 1,
            )
        }
        // The wording under the figure DOES transition, on iOS's own spring for it
        // (`.spring(response: 0.44, dampingFraction: 0.76)` on `text.sub`).
        SakhiAnimatedValue(
            text = text.sub,
            // iOS: `.font(.lato(18))`.
            style = LocalTextStyle.current.copy(fontSize = 18.sp, lineHeight = 24.sp),
            color = subColor,
        )

        // iOS's hero tip pill: sparkles + one-line phase tip, tappable straight into
        // Ask Sakhi. Copy comes from the shared engine's `shortTip`, not written here.
        hero.heroTip?.let { tip ->
            // iOS adds `.padding(.top, 6)` to the capsule on top of the stack's 10.
            HeroTipPill(
                tip = tip,
                contentColor = bigColor,
                isPeriodMode = isPeriodMode,
                onClick = onTipClick,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Port of iOS's hero tip capsule (`HomeDayDetailGlassView`): sparkles icon + a single
 * line of phase copy, 16dp horizontal / 9dp vertical padding inside a pill.
 */
@Composable
private fun HeroTipPill(
    tip: String,
    contentColor: Color,
    isPeriodMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (isPeriodMode) {
                    Color.White.copy(alpha = 0.18f)
                } else {
                    contentColor.copy(alpha = 0.10f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Centred, and the row wraps its content rather than stretching, so the pill
        // hugs the tip instead of running the full width of the hero.
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp),
        )
        // iOS: `.font(.lato(13))` with `.lineLimit(1)`. It ellipsizes when the tip is
        // longer than the pill, and with the hero's 24pt horizontal inset now applied
        // (iteration 43) some real tips did exactly that. Deliberate divergence, at
        // Karan's request: keep iOS's single line and its 13sp ceiling, but step the size
        // down instead of cutting the sentence off, so the whole tip is always readable.
        // Floor is 11sp so it never becomes smaller than the pill's own caption scale.
        BasicText(
            text = tip,
            style = LocalTextStyle.current.copy(
                color = contentColor,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            // Range raised from 11..13 on Karan's ask -- the tip was reading small in the
            // hero. The step-down behaviour stays: a long tip shrinks within this range
            // rather than being ellipsised, which is the earlier decision this pill was
            // built around.
            autoSize = TextAutoSize.StepBased(
                minFontSize = 12.sp,
                maxFontSize = 16.sp,
                stepSize = 0.5.sp,
            ),
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
    val palette = rememberPhasePalette(phase)
    val isMenstrual = phase == CyclePhase.MENSTRUAL
    // Both of these used to be recomputed inline here, duplicating `phaseCardFill` /
    // `phaseCardStroke` (which the log button already used via iOS's `logFill: cardFill`
    // rule). Two copies of one iOS value is exactly how the two drifted apart before.
    val cardFill = phaseCardFill(phase = phase, hasCycleData = hasCycleData)
    val cardStroke = phaseCardStroke(phase = phase, hasCycleData = hasCycleData)
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
    // Everything inside a card inherits the phase-aware primary. iOS does the equivalent
    // by reading `textPrimary` in each subview; providing it here fixes the card title and
    // every unstyled label in one place instead of threading a colour through each row.
    val cardText = rememberHomeCardTextColors(phase = phase, hasCycleData = hasCycleData)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cardFill, RoundedCornerShape(18.dp))
            .border(0.5.dp, cardStroke, RoundedCornerShape(18.dp)),
    ) {
      CompositionLocalProvider(
          LocalContentColor provides cardText.primary,
          LocalHomeCardText provides cardText,
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
                // iOS renders this as an HStack(spacing: 4) of
                // `chart.bar.fill` (10pt bold) + text (12pt bold) + `chevron.right`
                // (9pt bold) inside a capsule. Android drew the text alone, so the
                // "History" affordance on "How you feel" did not read as one.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(badgeFill, RoundedCornerShape(SakhiRadius.full))
                        .border(0.5.dp, accentColor.copy(alpha = 0.10f), RoundedCornerShape(SakhiRadius.full))
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(10.dp),
                    )
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor.copy(alpha = 0.7f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(9.dp),
                    )
                }
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
        SakhiListDivider(color = dividerColor)
        content()
      }
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
    val context = LocalContext.current
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
        // iOS: `badge: isPartnerMode ? nil : L("home.activity.log")` -- the History
        // affordance is owner-only. Android passed no badge at all.
        badge = if (isPartnerMode) null else stringResource(R.string.home_logged_history_badge),
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
                // iOS `cardInnerPlaceholder` renders this at `iconAccent.opacity(0.60)`,
                // not a ladder level -- the placeholder is accent-tinted on both platforms.
                color = accentColor.copy(alpha = 0.60f),
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
                    // iOS's `logChip(... value: flow.displayName ...)` reads that off its
                    // own FlowLevel ("Moderate"), not KMM's ("Medium") -- see flowDisplayName.
                    LogChip(icon = Icons.Filled.WaterDrop, value = flowDisplayName(context, flow), category = flowLabel, accentColor = accentColor)
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
            color = LocalHomeCardText.current.secondary,
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
    periodLogDates: Set<kotlinx.datetime.LocalDate>,
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
    // Engine-built, matching the calendar. `CalendarMarker.buildMarks(listOf(cycle))`
    // cannot mark predicted/fertile/ovulation days for a cycle still in progress,
    // because that cycle's length is null until it closes -- so this strip showed only
    // logged days while the calendar beside it showed predictions too.
    val marks = remember(cycle, periodLogDates, cycleLength) {
        val end = DateConverter.addDays(cycle.cycleStartDate, cycleLength - 1)
        CycleInsightAdapter.calendarMarks(
            from = cycle.cycleStartDate,
            to = end,
            periodLogDates = periodLogDates,
            today = DateConverter.today(),
            // The cycle's own owner, so a care partner viewing her strip resolves her
            // edited lengths rather than his.
            userId = cycle.userId,
        )
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
                    color = LocalHomeCardText.current.secondary,
                )
            }
            Text(
                text = if (today == DateConverter.addDays(cycle.cycleStartDate, dayInCycle - 1)) {
                    stringResource(R.string.home_cycle_day_today)
                } else {
                    stringResource(R.string.home_cycle_day_label)
                },
                style = MaterialTheme.typography.labelSmall,
                color = LocalHomeCardText.current.tertiary,
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
                    color = LocalHomeCardText.current.tertiary,
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

            SakhiListDivider(modifier = Modifier.padding(horizontal = SakhiSpacing.space5))

            Column(
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space4),
            ) {
                CycleStatusTile(
                    phase = phase,
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
                        phase = phase,
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
                        phase = phase,
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
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = LocalHomeCardText.current.tertiary)
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
private fun CycleStatTile(
    title: String,
    value: String,
    icon: ImageVector,
    phase: CyclePhase,
    modifier: Modifier = Modifier,
) {
    // Port of iOS `cycleInfoTile`. Android drew a plain themed `Surface` with
    // `tonalElevation`, which ignores the phase entirely -- on a period day that left a
    // pale tile with dark text sitting on the saturated card. iOS tints it from the phase
    // secondary and uses the same text ladder as everything else in the card.
    val palette = rememberPhasePalette(phase)
    val cardText = LocalHomeCardText.current
    val isPeriodMode = phase == CyclePhase.MENSTRUAL
    val softFill = palette.secondary.copy(alpha = if (isPeriodMode) 0.20f else 0.08f)
    val softStroke = palette.secondary.copy(alpha = if (isPeriodMode) 0.35f else 0.28f)

    Column(
        modifier = modifier
            .background(softFill, RoundedCornerShape(SakhiRadius.lg))
            .border(0.5.dp, softStroke, RoundedCornerShape(SakhiRadius.lg))
            // iOS: .padding(.horizontal, .m) / .padding(.vertical, .ml)
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space5)
            .semantics(mergeDescendants = true) {
                contentDescription = title
                stateDescription = value
            },
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        // iOS puts the icon and title on one row, with the value beneath — Android had
        // stacked icon / value / title.
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = palette.secondary,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = cardText.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = cardText.primary,
            maxLines = 1,
        )
    }
}

// Port of iOS `HomeDayDetailGlassView.cycleStatusTile`: a regularity summary
// row inside the Current Cycle card. `isRegular` is judged the same way iOS
// does it -- in this view, not via a shared KMM helper -- since it's a
// different measurement from `CycleMath.profileHealthStatus` (the
// delayed-period algorithm behind Profile's own "Cycle Health" badge).
@Composable
private fun CycleStatusTile(
    phase: CyclePhase,
    cyclesAnalyzed: Int,
    shortestCycle: Int,
    longestCycle: Int,
    accentColor: Color,
) {
    val phasePalette = rememberPhasePalette(phase)
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
        color = sakhiGroupedBackground().copy(alpha = 0.32f),
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
                    color = LocalHomeCardText.current.secondary,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalHomeCardText.current.tertiary,
                )
            }
            if (hasMeasuredStats) {
                // iOS: badgeBg = isRegular ? cycleCardAccent@(period ? 0.22 : 0.14)
                //                          : Color.white@0.10
                //      badgeFg = isRegular ? cycleCardAccent : textSecondary
                // `cycleCardAccent` is the phase *secondary* (`warmAccent`), not the
                // primary Android was using. The irregular pill was the worse half:
                // `onSurface@0.08` is near-black in light theme, so it read as a grey
                // chip with dark text instead of a translucent light one.
                val badgeAccent = phasePalette.secondary
                val badgeFg = if (isRegular) badgeAccent else LocalHomeCardText.current.secondary
                Surface(
                    shape = RoundedCornerShape(SakhiRadius.full),
                    color = if (isRegular) {
                        badgeAccent.copy(alpha = if (phase == CyclePhase.MENSTRUAL) 0.22f else 0.14f)
                    } else {
                        Color.White.copy(alpha = 0.10f)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = SakhiSpacing.space2, vertical = SakhiSpacing.space1),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
                    ) {
                        Icon(
                            imageVector = if (isRegular) Icons.Filled.Check else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = if (isRegular) {
                                stringResource(R.string.home_cycle_status_regular)
                            } else {
                                stringResource(R.string.home_cycle_status_irregular)
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeFg,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseInfoCard(
    phase: CyclePhase,
    isPartnerMode: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
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
        modifier = modifier,
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
            color = LocalHomeCardText.current.secondary,
            modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
        )
        if (snippet.bodyChangeResIds.isNotEmpty()) {
            Text(
                text = stringResource(R.string.home_phase_info_body_changes),
                style = MaterialTheme.typography.labelSmall,
                // iOS `textSection` — white at 0.58 on a period day, phase primary at
                // 0.56 otherwise. Was full-strength accent, which over-weighted a caption.
                color = LocalHomeCardText.current.section,
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
                        color = LocalHomeCardText.current.secondary,
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
        // No refresh affordance, matching the same removal on iOS
        // (HomeDayDetailGlassView+PartnerCards.swift). Karan's call, 2026-09-12.
        onRefresh = null,
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
                    color = sakhiSecondaryLabel(),
                )
                Text(
                    text = stringResource(R.string.home_retry),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = accentColor,
                    modifier = Modifier.clickable(onClick = onRetry),
                )
            }
            else -> state.items.forEachIndexed { i, item ->
                if (i > 0) SakhiListDivider(color = learningDividerColor())
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
                        // The card's OWN text colours, not the plain label colour.
                        // `sakhiLabel()` is near-black, and on a period day this card sits on
                        // a saturated pink hero, so every item read as black-on-pink and was
                        // barely legible (reported live: "text period ke din black he reh
                        // jaa raha hai"). Every other card on this screen already reads
                        // `LocalHomeCardText`, which resolves to white on a period day.
                        // iOS does exactly this: `item.isCompleted ? textTertiary : textPrimary`.
                        color = if (item.isCompleted) {
                            LocalHomeCardText.current.tertiary
                        } else {
                            LocalHomeCardText.current.primary
                        },
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
                SakhiListDivider(color = learningDividerColor())
            }
        }
    }
}

/**
 * Port of iOS `partnerNoDataCard` (`+EmptyState.swift`).
 *
 * This `Surface` carried a `tonalElevation` and **no `color`**, so it fell through to
 * `colorScheme.surface` and was then tinted further toward `primary` by the elevation --
 * in dark that is brand `lightPink` (#2C1A22) pulled pink, floating on a dark page.
 * iOS passes `fill: cardFill`, and this card only ever renders on the
 * `!hasPeriodData` branch, where `cardFill` returns `DS.Colors.systemBackground`
 * (white / #1C1C1E) with `stroke: cardStroke` = `DS.Colors.separator.opacity(0.25)`.
 */
@Composable
private fun PartnerNoDataCard() {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xxl),
        color = sakhiSystemBackground(),
        contentColor = sakhiLabel(),
        border = BorderStroke(0.5.dp, sakhiSeparator().copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(SakhiSpacing.space6),
        ) {
            Icon(Icons.Filled.NightsStay, contentDescription = null, tint = sakhiSecondaryLabel(), modifier = Modifier.size(32.dp))
            Text(
                text = stringResource(R.string.home_partner_no_data_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space3),
            )
            Text(
                text = stringResource(R.string.home_partner_no_data_body),
                style = MaterialTheme.typography.bodyMedium,
                color = sakhiSecondaryLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SakhiSpacing.space2),
            )
        }
    }
}

/**
 * Port of iOS `partnerHeadsUpCard`. Shown only when [text] resolves non-null.
 *
 * Same defect as [PartnerNoDataCard]: `tonalElevation` with no `color`. iOS backs this one
 * with `.background(cardFill)` and a `0.5` stroke of `cardStroke` at radius 22, and it only
 * renders on the branch where there IS cycle data -- so it takes the phase-tinted fill,
 * which is what [phaseCardFill] already computes for every other Home card. [phase] is
 * threaded in for that; it cannot be derived from `accentColor` alone.
 */
@Composable
private fun PartnerHeadsUpCard(text: PartnerHeadsUpCardText, accentColor: Color, phase: CyclePhase) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xxl),
        color = phaseCardFill(phase = phase, hasCycleData = true),
        contentColor = rememberHomeCardTextColors(phase = phase, hasCycleData = true).primary,
        border = BorderStroke(0.5.dp, phaseCardStroke(phase = phase, hasCycleData = true)),
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
                        color = sakhiSecondaryLabel(),
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
            color = sakhiSecondaryLabel(),
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
                if (i > 0) SakhiListDivider(color = learningDividerColor())
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
            SakhiListDivider(color = learningDividerColor())
            LearningTipRow(Icons.Filled.BarChart, stringResource(R.string.home_learning_log_tip_2))
            SakhiListDivider(color = learningDividerColor())
            LearningTipRow(Icons.Filled.Favorite, stringResource(R.string.home_learning_log_tip_3))
            SakhiListDivider(color = learningDividerColor())
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
                if (i > 0) SakhiListDivider(color = learningDividerColor())
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
                if (i > 0) SakhiListDivider(color = learningDividerColor())
                LearningOverviewRow(icon = row.icon, color = row.color, name = row.title, days = null, subtitle = row.description)
            }
        }

        LearningCard(title = stringResource(R.string.home_learning_tracking_title), icon = Icons.Filled.Lightbulb) {
            LearningTipRow(Icons.Filled.CalendarMonth, stringResource(R.string.home_learning_tracking_tip_1))
            SakhiListDivider(color = learningDividerColor())
            LearningTipRow(Icons.Filled.Schedule, stringResource(R.string.home_learning_tracking_tip_2))
            SakhiListDivider(color = learningDividerColor())
            LearningTipRow(Icons.Filled.Bedtime, stringResource(R.string.home_learning_tracking_tip_3))
            SakhiListDivider(color = learningDividerColor())
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
        color = sakhiSecondaryLabel(),
        modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
    )
    SakhiListDivider(color = learningDividerColor())
}

@Composable
private fun LearningRows(rows: List<Triple<ImageVector, String, String>>) {
    rows.forEachIndexed { i, (icon, title, subtitle) ->
        if (i > 0) SakhiListDivider(color = learningDividerColor())
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
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = sakhiSecondaryLabel())
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
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = sakhiSecondaryLabel())
            }
        }
        days?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = sakhiSecondaryLabel())
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
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = sakhiSecondaryLabel())
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
            color = sakhiSecondaryLabel(),
            modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
        )
        SakhiListDivider(color = learningDividerColor())
        points.forEachIndexed { i, point ->
            if (i > 0) SakhiListDivider(color = learningDividerColor())
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
                Text(text = point, style = MaterialTheme.typography.bodySmall, color = sakhiSecondaryLabel())
            }
        }
    }
}

/**
 * The rule between rows of the empty-state learning cards: lighter than the standard
 * separator, because those cards sit on the phase tint and a full-strength line read heavy.
 * A colour, not a component; the line itself is `SakhiListDivider`.
 */
@Composable
private fun learningDividerColor(): Color =
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

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
                // iOS has no empty state for this card at all (it shows a shimmer while
                // loading and always has foods afterwards), so there is no colour to copy.
                // The ladder keeps it consistent with the rest of the card rather than
                // inventing a third treatment.
                color = LocalHomeCardText.current.secondary,
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
            )
            }
            else -> {
                // iOS `nutritionCard`: foods are chunked **4 per page** into a swipeable
                // TabView pinned to a fixed 214pt, with a capsule page indicator below
                // when there is more than one page. Android rendered every food in one
                // flat column, so the card grew with the data and had no paging at all.
                // Hoisted out of the row loop on purpose: `remember` inside an
                // unkeyed `forEach` is a recomposition hazard, and the palette is
                // constant for the whole card anyway.
                val foodTilePalette = rememberPhasePalette(phase)
                val cardContext = LocalContext.current
                val pages = remember(foods) { foods.chunked(4) }
                val pagerState = rememberPagerState(pageCount = { pages.size })
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(214.dp),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                  Column(modifier = Modifier.fillMaxWidth()) {
                    pages[page].forEachIndexed { rowIndex, food ->
                    // iOS: 0.5pt rule at textTertiary@0.10, inset 46pt, between rows only.
                    if (rowIndex > 0) {
                        SakhiListDivider(
                            startInset = 46.dp,
                            color = LocalHomeCardText.current.tertiary.copy(alpha = 0.10f),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // iOS `foodRecommendationRow`: .padding(.horizontal, 14)
                            // .padding(.vertical, 7). The 8dp Android used pushed four
                            // rows past iOS's fixed 214pt page, clipping the fourth.
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // iOS `foodRecommendationRow`: a 36x36 r11 tile filled
                        // `secondary@(period ? 0.22 : 0.14)` holding the food's emoji at
                        // 19pt. The emoji is derived, not stored -- `FoodItem` carries no
                        // emoji field, which is exactly why iOS wrote `FoodEmoji.resolve`.
                        // That resolver is now ported to SakhiCore and used here.
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    foodTilePalette.secondary.copy(
                                        alpha = if (phase == CyclePhase.MENSTRUAL) 0.22f else 0.14f,
                                    ),
                                    RoundedCornerShape(11.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = FoodEmoji.resolve(food.name, food.category),
                                fontSize = 19.sp,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            // iOS `foodRecommendationRow`: name lato(14,.bold) in
                            // textPrimary, sub-line lato(12) in textTertiary, VStack
                            // spacing 2. Android's bodyLarge/bodySmall rows were tall
                            // enough that four no longer fit iOS's fixed 214pt page.
                            //
                            // The nutrient highlight belongs on this line, in an
                            // `HStack(spacing: 5)` beside the name -- Android had it in
                            // the row's trailing slot, which is where iOS puts the
                            // source chevron.
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                            Text(
                                text = food.name,
                                fontSize = 14.sp,
                                // iOS lato(14) leads at ~16.8pt; Compose's default 1.4x
                                // leading made the row tall enough that the fourth item
                                // overflowed the 214pt page.
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = LocalHomeCardText.current.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            // iOS: `.font(.lato(10, .bold))` in `iconAccent`.
                            food.nutritionLabel?.let { label ->
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            }
                            Text(
                                text = food.category,
                                fontSize = 12.sp,
                                // iOS lato(12) leads at ~14.4pt.
                                lineHeight = 14.sp,
                                color = LocalHomeCardText.current.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        // iOS ends the row with `Link(destination: source.url)` around a
                        // `chevron.right` (12pt semibold, textTertiary@0.55, 24x28 frame).
                        // Android had no chevron and no way to reach the source at all --
                        // the URL is derived locally, so this needs no backend data.
                        // See `FoodSourceLinks` in SakhiCore.
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 28.dp)
                                .clickable {
                                    openFoodSource(cardContext, food.name)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = cardContext.getString(
                                    R.string.home_nutrition_open_source,
                                    food.name,
                                ),
                                tint = LocalHomeCardText.current.tertiary.copy(alpha = 0.55f),
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                    }
                    // iOS keeps rows pinned to the top: a page with fewer than 4 items
                    // shows blank space beneath rather than centring them.
                    Spacer(modifier = Modifier.weight(1f))
                  }
                }
                if (pages.size > 1) {
                    // iOS: capsule dots, active 10x4 at standardAccent@0.72,
                    // inactive 4x4 at textTertiary@0.20, 4pt apart.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // iOS pads the whole card body `.top, 4` / `.bottom, 10`;
                            // Android had only the top gap, so the indicator sat hard
                            // against the card's bottom edge and read as clipped.
                            .padding(top = 4.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        pages.indices.forEach { index ->
                            val isActive = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(
                                        width = if (isActive) 10.dp else 4.dp,
                                        height = 4.dp,
                                    )
                                    .background(
                                        if (isActive) {
                                            accentColor.copy(alpha = 0.72f)
                                        } else {
                                            LocalHomeCardText.current.tertiary.copy(alpha = 0.20f)
                                        },
                                        RoundedCornerShape(SakhiRadius.full),
                                    ),
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
                SakhiListDivider(
                    startInset = 46.dp,
                    color = shimmerBase.copy(alpha = 0.72f),
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
                color = LocalHomeCardText.current.secondary,
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space2),
            )
            Text(
                text = stringResource(R.string.home_insight_powered),
                style = MaterialTheme.typography.labelSmall,
                color = LocalHomeCardText.current.tertiary,
                modifier = Modifier.padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
            )
        }
    }
}

/**
 * Card text colours, ported from iOS's `HomeDayDetailGlassView+GlassCard.swift` ladder
 * (`textPrimary` / `textSecondary` / `textSection` / `textTertiary`).
 *
 * On a period day the cards are filled with the saturated phase surface, so iOS switches
 * every label to **white** at four fixed opacities. Android was leaving these to
 * `MaterialTheme.colorScheme.onSurface`/`onSurfaceVariant`, which stays near-black in
 * light theme -- so "How you feel", "What to Eat" and their rows rendered dark text on a
 * dark pink card. Off a period day iOS tints text with the phase primary rather than the
 * theme's neutral, which this reproduces too.
 */
/**
 * The active card text ladder. Rows inside a card read this instead of
 * `MaterialTheme.colorScheme.onSurfaceVariant`, which stays near-black in light theme and
 * is unreadable on the saturated period-day card fill.
 */
private val LocalHomeCardText = compositionLocalOf {
    HomeCardTextColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
}

private data class HomeCardTextColors(
    val primary: Color,
    val secondary: Color,
    val section: Color,
    val tertiary: Color,
)

@Composable
private fun rememberHomeCardTextColors(
    phase: CyclePhase,
    hasCycleData: Boolean,
): HomeCardTextColors {
    val palette = rememberPhasePalette(phase)
    val neutralPrimary = sakhiLabel()
    val neutralSecondary = sakhiSecondaryLabel()
    val isPeriodMode = phase == CyclePhase.MENSTRUAL
    // iOS uses the *raw* phase primary here (`PhaseColors(phase).primary`), not the
    // menstrual white override -- in period mode the ladder is white anyway, and off it
    // the override does not apply.
    val phaseText = palette.primary
    return remember(phase, hasCycleData, isPeriodMode, phaseText, neutralPrimary, neutralSecondary) {
        when {
            !hasCycleData -> HomeCardTextColors(
                primary = neutralPrimary,
                secondary = neutralSecondary,
                section = neutralSecondary,
                tertiary = neutralSecondary.copy(alpha = 0.6f),
            )
            isPeriodMode -> HomeCardTextColors(
                primary = Color.White,
                secondary = Color.White.copy(alpha = 0.75f),
                section = Color.White.copy(alpha = 0.58f),
                tertiary = Color.White.copy(alpha = 0.50f),
            )
            else -> HomeCardTextColors(
                primary = phaseText,
                secondary = phaseText.copy(alpha = 0.72f),
                section = phaseText.copy(alpha = 0.56f),
                tertiary = phaseText.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun homeSecondaryTextColor(
    phasePalette: SakhiPhasePalette,
    hasCycleData: Boolean,
    isMenstrual: Boolean,
): Color = when {
    !hasCycleData -> sakhiSecondaryLabel()
    isMenstrual -> Color.White.copy(alpha = 0.90f)
    else -> phasePalette.primary.copy(alpha = 0.72f)
}

@Composable
private fun HomeTopBar(
    hero: HomeHeroState,
    // Provider, not a value — see the note on `HeroSection.scrollProgress`.
    heroScrollProgress: () -> Float,
    phasePalette: SakhiPhasePalette,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    unreadNotificationCount: Int,
    onOpenCalendar: () -> Unit,
    onResetToToday: () -> Unit,
    onPhaseTap: () -> Unit = {},
) {
    val context = LocalContext.current
    val hasCycleData = hero.hasCycleData
    val isMenstrual = hero.phase == CyclePhase.MENSTRUAL
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
    // While the app is catching up in the background, the top bar says so rather than showing
    // a phase name derived from data that is still landing. Home is already fully usable
    // underneath: this is a status line, not a gate.
    // "Syncing" NEVER replaces a value she already had. Once there is cycle data, the phase
    // name stays put and the rotating icon beside it carries the syncing signal instead —
    // blanking a real value on every launch is a worse experience than a slightly stale one.
    // The word is only used when there is genuinely nothing yet to show in its place.
    val phaseLabel = if (hasCycleData) {
        hero.phaseKind.displayName(context)
    } else if (hero.isSyncing) {
        stringResource(R.string.home_sync_syncing)
    } else if (hero.session?.isViewingOwnData == false) {
        stringResource(R.string.home_phase_first_period_partner)
    } else {
        stringResource(R.string.home_phase_first_period_self)
    }
    val heroSummary = remember(context, hero) { heroText(context, hero) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val isToday = hero.selectedDate == DateConverter.today()
            // A reading, so it changes instantly like the countdown does. iOS eases this one
            // (`.animation(.easeInOut(duration: 0.35), value: snapshot.formattedDate)`), and
            // that is the divergence Karan asked for.
            Text(
                text = DateConverter.formatForDisplay(hero.selectedDate),
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
                isSyncing = hero.isSyncing,
                onPhaseTap = onPhaseTap,
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
            // The bell takes the place the Care (people) button used to hold: Karan's call on
            // 2026-09-12, so Home's top right is the inbox on both platforms.
            TopBarIconButton(
                icon = Icons.Filled.Notifications,
                contentDescription = if (unreadNotificationCount > 0) {
                    stringResource(R.string.home_notifications_unread_content_description, unreadNotificationCount)
                } else {
                    stringResource(R.string.home_open_notifications_content_description)
                },
                foreground = foreground,
                background = iconBackground,
                stroke = iconStroke,
                onClick = onOpenNotifications,
                badgeCount = unreadNotificationCount,
                // Brand pink on every phase except the period, where the whole hero is
                // already that pink and a pink badge would vanish into it.
                badgeFill = if (isMenstrual) Color.White else MaterialTheme.colorScheme.primary,
                badgeContent = if (isMenstrual) MaterialTheme.colorScheme.primary else Color.White,
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
    /** Zero (the default) draws no badge at all. */
    badgeCount: Int = 0,
    badgeFill: Color = Color.Unspecified,
    badgeContent: Color = Color.White,
) {
    // The outer Box is unclipped so the badge can sit over the circle's edge, the way an
    // iOS badge does, instead of being squeezed inside it.
    Box {
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
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .defaultMinSize(minWidth = TopBarBadgeSize, minHeight = TopBarBadgeSize)
                    // Ringed in the button's own fill so the badge reads as sitting ON the
                    // circle rather than merging into its outline.
                    .border(1.5.dp, background, CircleShape)
                    .background(badgeFill, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // Past nine the exact number stops being information; "9+" is what
                    // every inbox she uses shows.
                    text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                    color = badgeContent,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = SakhiFontSize.xs,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

private val TopBarBadgeSize = 18.dp

@Composable
private fun HeroTopBarSubtitle(
    phaseName: String,
    heroContentBig: String,
    heroContentSub: String,
    foreground: Color,
    // Provider, not a value — see the note on `HeroSection.scrollProgress`.
    progress: () -> Float,
    isSyncing: Boolean = false,
    onPhaseTap: () -> Unit = {},
) {
    // The one thing on this screen that genuinely cannot be deferred to the draw phase:
    // `clickable`'s `enabled` has to be a real Boolean at composition time. Wrapping it in
    // `derivedStateOf` means this recomposes only on the frame the value actually crosses
    // 0.5, rather than on all ~120 frames of the fade, which is what reading the raw float
    // here would cost.
    val phaseTapEnabled by remember(progress) { derivedStateOf { progress() < 0.5f } }

    Box(
        modifier = Modifier.height(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                // `graphicsLayer`, not `Modifier.alpha`: alpha takes the value at
                // composition time, graphicsLayer reads it at draw time.
                .graphicsLayer { alpha = 1f - progress() }
                // iOS gates the tap the same way: `.allowsHitTesting(progress < 0.5)`,
                // so the label stops responding once it has faded into the collapsed
                // "Day 1 · of your period" summary.
                .clickable(enabled = phaseTapEnabled && !isSyncing, onClick = onPhaseTap),
        ) {
            Text(
                text = phaseName,
                style = MaterialTheme.typography.labelMedium,
                color = foreground.copy(alpha = 0.72f),
            )
            if (isSyncing) {
                // Rotation is driven entirely in the draw phase: `rotationZ` reads the
                // animation inside `graphicsLayer`, so the spinner never recomposes anything.
                // A spinner that recomposed its parent every frame would be the same mistake
                // the hero scroll progress used to make.
                val spin = rememberInfiniteTransition(label = "sync_spin")
                val angle by spin.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(SYNC_SPIN_PERIOD_MS, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "sync_spin_angle",
                )
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = foreground.copy(alpha = 0.50f),
                    modifier = Modifier
                        .size(12.dp)
                        .graphicsLayer { rotationZ = angle },
                )
            } else {
                // iOS: `Image(systemName: "chevron.down").font(.lato(8, .bold))`. Android was
                // drawing a `MoreHoriz` ellipsis, which reads as "more options" rather than
                // "this expands downward" -- and nothing happened when it was tapped.
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = foreground.copy(alpha = 0.50f),
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.graphicsLayer { alpha = progress() },
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

/**
 * The name shown under the date in the top bar, matching iOS's
 * `SakhiCycleInsightEngine.present(_:)`.
 *
 * Keyed on the engine's [CyclePhaseInsight.PhaseKind] rather than [CyclePhase]: PMS is a
 * distinct kind but reports `CyclePhase.LUTEAL`, so going through the app-wide enum
 * silently relabelled every PMS day as "Luteal Phase" while iOS said "PMS Phase".
 */
private fun CyclePhaseInsight.PhaseKind.displayName(context: Context): String = when (this) {
    CyclePhaseInsight.PhaseKind.MENSTRUAL -> context.getString(R.string.home_phase_name_menstrual)
    CyclePhaseInsight.PhaseKind.FOLLICULAR -> context.getString(R.string.home_phase_name_follicular)
    CyclePhaseInsight.PhaseKind.OVULATION -> context.getString(R.string.home_phase_name_ovulation)
    CyclePhaseInsight.PhaseKind.LUTEAL -> context.getString(R.string.home_phase_name_luteal)
    CyclePhaseInsight.PhaseKind.PMS -> context.getString(R.string.home_phase_name_pms)
    CyclePhaseInsight.PhaseKind.DELAYED -> context.getString(R.string.home_phase_name_delayed)
    CyclePhaseInsight.PhaseKind.UNKNOWN -> context.getString(R.string.home_phase_name_unknown)
}

/**
 * Opens the USDA source page for a food, matching iOS's `Link(destination:
 * item.resolvedSource.url)` on each "What to Eat" row. The URL itself is derived in
 * SakhiCore so both platforms resolve the same page for the same food.
 */
private fun openFoodSource(context: android.content.Context, foodName: String) {
    val url = team.sakhi.repositories.FoodSourceLinks.searchUrl(foodName)
    // Plain ACTION_VIEW: `feature:home` does not depend on androidx.browser, and a
    // source link is a genuine hand-off out of the app rather than in-app content.
    // `runCatching` because a device with no browser would otherwise crash here.
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * iOS `homePhaseTransition` is `.spring(response: 0.5, dampingFraction: 0.88)`.
 *
 * SwiftUI's `response` is the spring's natural period, so the Compose equivalent is
 * `stiffness = (2*pi / response)^2` -- (2*pi / 0.5)^2 which is about 158.
 */
/**
 * How long Home takes to settle into a new content height. Matched to the phase colour
 * transition (`PHASE_COLOR_TRANSITION_MS`) so the layout and the colour finish together
 * rather than the screen changing twice.
 */
private const val HOME_CONTENT_RESIZE_MS = 400

private const val SYNC_SPIN_PERIOD_MS = 1_100

private const val HomeHeroSpringStiffness = 158f
