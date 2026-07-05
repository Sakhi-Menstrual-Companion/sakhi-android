package team.sakhi.android.feature.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import team.sakhi.cycle.CycleMath
import team.sakhi.models.CyclePhase
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.RecommendationRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.recommendation.PartnerInsightPolicy
import team.sakhi.session.Permission
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager

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
    val canViewPhaseRecommendations: Boolean = false,
    val canViewConditionRecommendations: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Thin recommendations adapter over shared cycle, profile, and recommendation
 * repositories. Android only orchestrates the existing KMM pipeline and renders
 * the results; recommendation rules remain shared.
 */
class RecommendationsViewModel(
    private val sessionManager: SessionManager,
    private val cycleDataRepository: CycleDataRepository,
    private val userProfileRepository: UserProfileRepository,
    private val recommendationRepository: RecommendationRepository,
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

    private suspend fun refresh(session: SessionContext?) {
        if (session == null) {
            _uiState.value = RecommendationsUiState()
            return
        }

        val canViewPhaseRecommendations = session.isViewingOwnData ||
            session.can(Permission.VIEW_PREDICTIONS) ||
            session.can(Permission.VIEW_CYCLE_HISTORY)
        val canViewConditionRecommendations = session.isViewingOwnData ||
            session.can(Permission.VIEW_SYMPTOMS)

        _uiState.value = RecommendationsUiState(
            session = session,
            phase = CyclePhase.UNKNOWN,
            canViewPhaseRecommendations = canViewPhaseRecommendations,
            canViewConditionRecommendations = canViewConditionRecommendations,
            isLoading = true,
            error = null,
        )

        val requestedTargetUserId = session.targetUserId

        val cycleResult = cycleDataRepository.getLatest(requestedTargetUserId)
        val phase = cycleResult.getOrNull()?.let(CycleMath::currentPhase) ?: CyclePhase.UNKNOWN

        val profile = userProfileRepository.get(requestedTargetUserId).getOrNull()
        val curated = if (canViewPhaseRecommendations) {
            recommendationRepository.getCuratedRecommendations(phase)
        } else {
            recommendationRepository.getCuratedRecommendations(CyclePhase.UNKNOWN)
        }

        val partnerContent = if (canViewPhaseRecommendations) {
            recommendationRepository.getPartnerContent(phase).getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val orderedInsight = orderedInsight(
            phase = phase,
            insights = partnerContent,
        )

        val conditionTips = if (canViewConditionRecommendations) {
            profile?.healthConditions
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
            aiInsight = orderedInsight,
            canViewPhaseRecommendations = canViewPhaseRecommendations,
            canViewConditionRecommendations = canViewConditionRecommendations,
            isLoading = false,
            error = cycleResult.exceptionOrNull()?.message,
        )
    }

    private fun orderedInsight(
        phase: CyclePhase,
        insights: List<team.sakhi.repositories.PartnerContent>,
    ): String? {
        if (insights.isEmpty()) return null
        val order = PartnerInsightPolicy.cardTypeOrder(phase)
        return insights
            .sortedBy { content ->
                val index = order.indexOf(content.category.lowercase())
                if (index == -1) Int.MAX_VALUE else index
            }
            .firstOrNull()
            ?.content
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
                        nutritionLabel = nutrition?.let(::nutritionLabel),
                    )
                }
            }.awaitAll()
        }
    }
}

private fun nutritionLabel(nutrition: team.sakhi.repositories.FoodNutrition): String? {
    return when {
        nutrition.proteinG != null -> "Protein ${nutrition.proteinG} g"
        nutrition.carbsG != null -> "Carbs ${nutrition.carbsG} g"
        nutrition.calories != null -> "${nutrition.calories} kcal"
        else -> null
    }
}
