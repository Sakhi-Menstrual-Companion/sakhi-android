package team.sakhi.android.feature.recommendations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.phasePrimaryColor
import team.sakhi.models.CyclePhase

/**
 * Phase 1 recommendations shell: shared curated recommendations, USDA enrichment,
 * condition tips, and partner insight rendered in a simple Compose layout.
 */
@Composable
fun RecommendationsScreen(
    viewModel: RecommendationsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = visibleAccent(uiState.phase)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SakhiSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = if (uiState.session?.isViewingOwnData == false) "Shared recommendations" else "Recommendations",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = recommendationsSummary(uiState),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard(
            title = "Eat more",
            accent = accent,
            items = if (uiState.canViewPhaseRecommendations) {
                uiState.eatMoreFoods.map(::foodLine)
            } else {
                listOf("Phase-based food recommendations are hidden for this care role.")
            },
        )

        SectionCard(
            title = "Avoid",
            accent = accent,
            items = if (uiState.canViewPhaseRecommendations) {
                uiState.avoidFoods.map(::foodLine)
            } else {
                listOf("Avoid-food guidance is hidden for this care role.")
            },
        )

        SectionCard(
            title = "Phase tips",
            accent = accent,
            items = if (uiState.canViewPhaseRecommendations) {
                uiState.phaseTips.ifEmpty { listOf("No phase tips are available right now.") }
            } else {
                listOf("Phase tips are hidden for this care role.")
            },
        )

        SectionCard(
            title = "Condition tips",
            accent = MaterialTheme.colorScheme.secondary,
            items = if (uiState.canViewConditionRecommendations) {
                uiState.conditionTips.ifEmpty { listOf("No condition-specific tips are available right now.") }
            } else {
                listOf("Condition-specific recommendations are hidden for this care role.")
            },
        )

        uiState.aiInsight?.let { insight ->
            Surface(
                shape = RoundedCornerShape(SakhiRadius.xxl),
                color = accent.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(SakhiSpacing.space5),
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                ) {
                    Text(
                        text = "AI insight",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                    Text(
                        text = insight,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        uiState.error?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    accent: Color,
    items: List<String>,
) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xxl),
        tonalElevation = SakhiSpacing.space1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SakhiSpacing.space5),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space3),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
            items.forEach { item ->
                Text(
                    text = "• $item",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun foodLine(food: RecommendationFoodUi): String {
    return food.nutritionLabel?.let { "${food.name}, ${it.lowercase()}" } ?: food.name
}

@Composable
private fun visibleAccent(phase: CyclePhase): Color {
    return if (phase == CyclePhase.UNKNOWN) {
        MaterialTheme.colorScheme.primary
    } else {
        phasePrimaryColor(phase)
    }
}

private fun recommendationsSummary(uiState: RecommendationsUiState): String = when {
    uiState.isLoading -> "Loading shared recommendations"
    uiState.error != null && uiState.phase == CyclePhase.UNKNOWN ->
        "Recommendations are available, but the current phase could not be resolved right now."
    uiState.phase == CyclePhase.UNKNOWN -> "Recommendations are ready, but there is no active phase signal yet."
    uiState.session?.isViewingOwnData == false ->
        "Showing ${uiState.phase.displayName.lowercase()} recommendations with care-role visibility applied."
    else -> "Showing ${uiState.phase.displayName.lowercase()} recommendations for the current phase."
}
