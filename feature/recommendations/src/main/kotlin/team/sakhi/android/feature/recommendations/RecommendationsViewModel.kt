package team.sakhi.android.feature.recommendations

import team.sakhi.cycle.CyclePhaseInsight
import team.sakhi.android.common.CycleInsightAdapter
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import team.sakhi.cycle.CycleMath
import team.sakhi.date.DateConverter
import team.sakhi.logging.Symptom
import team.sakhi.models.CyclePhase
import team.sakhi.models.UserProfile
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.repositories.RecommendationInsightService
import team.sakhi.repositories.RecommendationRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.Permission
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.android.common.toSafeUserMessage

data class RecommendationFoodUi(
    val name: String,
    val category: String,
    val nutritionLabel: String? = null,
)

data class RecommendationsUiState(
    val session: SessionContext? = null,
    val phase: CyclePhase = CyclePhase.UNKNOWN,
    val eatMoreFoods: List<RecommendationFoodUi> = emptyList(),
    val avoidFoods: List<RecommendationFoodUi> = emptyList(),
    val phaseTips: List<String> = emptyList(),
    val conditionTips: List<String> = emptyList(),
    val aiInsight: String? = null,
    // Matches iOS `RecommendationViewModel.isLoadingInsight` -- scoped to just the
    // AI insight card's own refresh button/spinner, independent of the
    // whole-screen `isLoading` (iOS's `HomeDayDetailGlassView+Cards.swift` shows a
    // small spinner inside the card and disables its "Refresh" affordance while
    // this is true, without re-showing the full-screen loading state).
    val isRefreshingInsight: Boolean = false,
    val canViewPhaseRecommendations: Boolean = false,
    val canViewConditionRecommendations: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Thin recommendations adapter over shared cycle, profile, and recommendation
 * repositories. Android only orchestrates the existing KMM pipeline and renders
 * the results; recommendation rules remain shared.
 *
 * `aiInsight` (the "Sakhi's tip for today" / "How to be there for her today"
 * card) is a real per-day, per-phase Claude-generated tip via the shared
 * `RecommendationInsightService.getDailyInsight`/`getPartnerInsight` -- this
 * mirrors iOS's actual live `RecommendationViewModel.load()`/
 * `loadPartnerInsight()` (`Features/Recommendations/Repositories/
 * RecommendationInsightService.swift`), not the KMM-shared `getPartnerContent`
 * (`partner_content` Supabase table) this previously used. That table read
 * traces back to iOS's `RecommendationRepository.fetchPartnerCards` and
 * `PartnerInsightPolicy`, both confirmed dead code on iOS itself (no live
 * call site renders them) -- porting them here produced a generic, static,
 * non-personalized string mislabeled as an AI insight instead of the real
 * per-day tip iOS actually shows.
 */
class RecommendationsViewModel(
    private val sessionManager: SessionManager,
    private val cycleDataRepository: CycleDataRepository,
    private val userProfileRepository: UserProfileRepository,
    private val recommendationRepository: RecommendationRepository,
    private val periodLogRepository: PeriodLogRepository,
    private val recommendationInsightService: RecommendationInsightService,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendationsUiState())
    val uiState: StateFlow<RecommendationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.session.collectLatest { session ->
                refresh(session)
            }
        }
    }

    /**
     * Matches iOS's real "Refresh" button on the AI insight card
     * (`HomeDayDetailGlassView+Cards.swift`'s `sakhiInsightCard`, wired to
     * `RecommendationViewModel.refreshInsight()`/`refreshPartnerInsight()`) --
     * clears the cached tip and forces a brand-new Claude-generated one for
     * today, independent of the whole-screen `isLoading` state.
     */
    fun refreshInsight() {
        val session = sessionManager.current ?: return
        val state = _uiState.value
        if (!canViewPhaseRecommendations(session) || state.phase == CyclePhase.UNKNOWN) return
        if (state.isRefreshingInsight) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingInsight = true, aiInsight = null) }
            recommendationInsightService.clearCache()

            val requestedTargetUserId = session.targetUserId
            val profileName = partnerProfileName(session, requestedTargetUserId)
            val insight = fetchInsight(session, state.phase, requestedTargetUserId, profileName)

            if (sessionManager.current?.targetUserId != requestedTargetUserId) return@launch
            _uiState.update { it.copy(isRefreshingInsight = false, aiInsight = insight) }
        }
    }

    private suspend fun fetchInsight(
        session: SessionContext,
        phase: CyclePhase,
        requestedTargetUserId: String,
        partnerProfileName: String?,
    ): String? {
        return if (session.isViewingOwnData) {
            val today = DateConverter.today()
            val todaySymptoms = periodLogRepository.getForDate(requestedTargetUserId, today)
                .getOrNull()
                ?.symptoms
                ?.mapNotNull { Symptom.from(it) }
                .orEmpty()
            recommendationInsightService.getDailyInsight(
                userId = requestedTargetUserId,
                dateString = today.toString(),
                phase = phase,
                symptoms = todaySymptoms.map { it.value },
                conditions = profileForConditionTips(requestedTargetUserId)?.healthConditions.orEmpty(),
            ).getOrNull()?.text
        } else {
            session.activePartnership?.id?.let { partnershipId ->
                recommendationInsightService.getPartnerInsight(
                    partnershipId = partnershipId,
                    dateString = DateConverter.today().toString(),
                    phase = phase,
                    partnerName = partnerProfileName.orEmpty(),
                ).getOrNull()?.text
            }
        }
    }

    private suspend fun refresh(session: SessionContext?) {
        if (session == null) {
            _uiState.value = RecommendationsUiState()
            return
        }

        val canViewPhaseRecommendations = canViewPhaseRecommendations(session)
        val canViewConditionRecommendations = canViewConditionRecommendations(session)

        _uiState.value = RecommendationsUiState(
            session = session,
            phase = CyclePhase.UNKNOWN,
            canViewPhaseRecommendations = canViewPhaseRecommendations,
            canViewConditionRecommendations = canViewConditionRecommendations,
            isLoading = true,
            error = null,
        )

        val requestedTargetUserId = session.targetUserId

        val cycleResult = if (canViewPhaseRecommendations) {
            cycleDataRepository.getLatest(requestedTargetUserId)
        } else {
            Result.success(null)
        }
        // Same shared engine Home's hero uses (`CycleInsightAdapter` ->
        // `CyclePhaseInsight`), NOT `CycleMath.currentPhase`. Home was moved onto the
        // engine on 2026-08-02; leaving Recommendations on CycleMath would let the two
        // disagree on the same day — the food list and tips could say "luteal" while
        // the hero above them says "Day 1 of your period".
        // Reuse the cycle already fetched above rather than issuing a second read --
        // `CycleInsightAdapter` only needs the current cycle's start (iOS passes
        // `currentCycles.first` for the same reason) plus the logged period days.
        val allCycles = listOfNotNull(cycleResult.getOrNull())
        val periodLogDates = runCatching {
            periodLogRepository.getAll(requestedTargetUserId).getOrDefault(emptyList())
        }.getOrDefault(emptyList())
            .filter { it.periodPresent }
            .mapTo(mutableSetOf()) { it.logDate }
        val phase = CycleInsightAdapter.insightFor(
            date = DateConverter.today(),
            cycles = allCycles,
            periodLogDates = periodLogDates,
            stats = CycleMath.computeStatistics(allCycles.filter { it.isComplete }),
        ).phase.kind.let { kind ->
            when (kind) {
                CyclePhaseInsight.PhaseKind.MENSTRUAL -> CyclePhase.MENSTRUAL
                CyclePhaseInsight.PhaseKind.FOLLICULAR -> CyclePhase.FOLLICULAR
                CyclePhaseInsight.PhaseKind.OVULATION -> CyclePhase.OVULATION
                CyclePhaseInsight.PhaseKind.LUTEAL, CyclePhaseInsight.PhaseKind.PMS -> CyclePhase.LUTEAL
                CyclePhaseInsight.PhaseKind.DELAYED -> CyclePhase.DELAYED
                CyclePhaseInsight.PhaseKind.UNKNOWN -> CyclePhase.UNKNOWN
            }
        }

        val curated = if (canViewPhaseRecommendations) {
            recommendationRepository.getCuratedRecommendations(phase)
        } else {
            recommendationRepository.getCuratedRecommendations(CyclePhase.UNKNOWN)
        }

        val aiInsight = if (canViewPhaseRecommendations && phase != CyclePhase.UNKNOWN) {
            fetchInsight(
                session = session,
                phase = phase,
                requestedTargetUserId = requestedTargetUserId,
                partnerProfileName = partnerProfileName(session, requestedTargetUserId),
            )
        } else {
            null
        }

        val conditionTips = if (canViewConditionRecommendations) {
            profileForConditionTips(requestedTargetUserId)?.healthConditions
                ?.flatMap { recommendationRepository.getConditionTips(it) }
                ?.distinct()
                .orEmpty()
        } else {
            emptyList()
        }

        val eatMoreFoods = enrichFoods(curated.eatMore)
        val avoidFoods = enrichFoods(curated.avoid)

        if (sessionManager.current?.targetUserId != requestedTargetUserId) return

        _uiState.value = RecommendationsUiState(
            session = session,
            phase = phase,
            eatMoreFoods = if (canViewPhaseRecommendations) eatMoreFoods else emptyList(),
            avoidFoods = if (canViewPhaseRecommendations) avoidFoods else emptyList(),
            phaseTips = if (canViewPhaseRecommendations) curated.tips else emptyList(),
            conditionTips = conditionTips,
            aiInsight = aiInsight,
            canViewPhaseRecommendations = canViewPhaseRecommendations,
            canViewConditionRecommendations = canViewConditionRecommendations,
            isLoading = false,
            error = cycleResult.exceptionOrNull()
                ?.toSafeUserMessage(appContext, R.string.recommendations_load_failed),
        )
    }

    private fun canViewPhaseRecommendations(session: SessionContext): Boolean {
        return session.isViewingOwnData ||
            session.can(Permission.VIEW_PREDICTIONS) ||
            session.can(Permission.VIEW_CYCLE_HISTORY)
    }

    private fun canViewConditionRecommendations(session: SessionContext): Boolean {
        return session.isViewingOwnData || session.can(Permission.VIEW_SYMPTOMS)
    }

    private suspend fun partnerProfileName(
        session: SessionContext,
        requestedTargetUserId: String,
    ): String? {
        if (session.isViewingOwnData) return null
        if (!canViewPhaseRecommendations(session)) return null
        return userProfileRepository.get(requestedTargetUserId).getOrNull()?.name
    }

    private suspend fun profileForConditionTips(requestedTargetUserId: String): UserProfile? {
        return userProfileRepository.get(requestedTargetUserId).getOrNull()
    }

    private suspend fun enrichFoods(
        foods: List<team.sakhi.repositories.FoodItem>,
    ): List<RecommendationFoodUi> {
        return coroutineScope {
            foods.map { food ->
                async {
                    val nutrition = runCatching {
                        recommendationRepository.enrichWithUSDA(food.name).getOrNull()
                    }.getOrNull()

                    RecommendationFoodUi(
                        name = food.name,
                        category = food.category,
                        nutritionLabel = nutrition?.let { nutritionLabel(appContext, it) },
                    )
                }
            }.awaitAll()
        }
    }
}

private fun nutritionLabel(
    context: Context,
    nutrition: team.sakhi.repositories.FoodNutrition,
): String? {
    return when {
        nutrition.proteinG != null -> context.getString(
            R.string.recommendations_nutrition_protein,
            nutrition.proteinG.toString(),
        )
        nutrition.carbsG != null -> context.getString(
            R.string.recommendations_nutrition_carbs,
            nutrition.carbsG.toString(),
        )
        nutrition.calories != null -> context.getString(
            R.string.recommendations_nutrition_calories,
            nutrition.calories.toString(),
        )
        else -> null
    }
}
