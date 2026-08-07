package team.sakhi.android.feature.recommendations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.phasePrimaryColor
import team.sakhi.android.ui.PhaseBadge
import team.sakhi.models.CyclePhase
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel

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
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SakhiSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
    ) {
        Text(
            text = if (uiState.session?.isViewingOwnData == false) {
                stringResource(R.string.recommendations_shared_title)
            } else {
                stringResource(R.string.recommendations_title)
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = recommendationsSummary(uiState),
            style = MaterialTheme.typography.bodyMedium,
            color = sakhiSecondaryLabel(),
        )
        if (uiState.phase != CyclePhase.UNKNOWN) {
            PhaseBadge(
                phase = uiState.phase,
                accentColor = accent,
            )
        }

        SectionCard(
            title = stringResource(R.string.recommendations_section_eat_more),
            accent = accent,
            items = if (uiState.canViewPhaseRecommendations) {
                uiState.eatMoreFoods.map { foodLine(context, it) }
            } else {
                listOf(stringResource(R.string.recommendations_hidden_phase_foods))
            },
        )

        SectionCard(
            title = stringResource(R.string.recommendations_section_avoid),
            accent = accent,
            items = if (uiState.canViewPhaseRecommendations) {
                uiState.avoidFoods.map { foodLine(context, it) }
            } else {
                listOf(stringResource(R.string.recommendations_hidden_avoid_foods))
            },
        )

        SectionCard(
            title = stringResource(R.string.recommendations_section_phase_tips),
            accent = accent,
            items = if (uiState.canViewPhaseRecommendations) {
                uiState.phaseTips.ifEmpty { listOf(stringResource(R.string.recommendations_no_phase_tips)) }
            } else {
                listOf(stringResource(R.string.recommendations_hidden_phase_tips))
            },
        )

        SectionCard(
            title = stringResource(R.string.recommendations_section_condition_tips),
            accent = MaterialTheme.colorScheme.secondary,
            items = if (uiState.canViewConditionRecommendations) {
                uiState.conditionTips.ifEmpty {
                    listOf(stringResource(R.string.recommendations_no_condition_tips))
                }
            } else {
                listOf(stringResource(R.string.recommendations_hidden_condition_tips))
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
                        text = stringResource(R.string.recommendations_ai_insight),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                    Text(
                        text = insight,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.recommendations_powered_by_ai),
                            style = MaterialTheme.typography.labelSmall,
                            color = sakhiSecondaryLabel(),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (uiState.isRefreshingInsight) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = accent,
                            )
                        } else {
                            IconButton(
                                onClick = viewModel::refreshInsight,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.recommendations_refresh_content_description),
                                    tint = accent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
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
                    text = stringResource(R.string.recommendations_bullet_item, item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun foodLine(context: android.content.Context, food: RecommendationFoodUi): String {
    return food.nutritionLabel?.let { nutrition ->
        context.getString(R.string.recommendations_food_line, food.name, nutrition.lowercase())
    } ?: food.name
}

@Composable
private fun visibleAccent(phase: CyclePhase): Color {
    return if (phase == CyclePhase.UNKNOWN) {
        MaterialTheme.colorScheme.primary
    } else {
        phasePrimaryColor(phase)
    }
}

@Composable
private fun recommendationsSummary(uiState: RecommendationsUiState): String = when {
    uiState.isLoading -> stringResource(R.string.recommendations_loading_summary)
    uiState.error != null && uiState.phase == CyclePhase.UNKNOWN ->
        stringResource(R.string.recommendations_phase_unresolved_summary)
    uiState.phase == CyclePhase.UNKNOWN -> stringResource(R.string.recommendations_no_phase_summary)
    uiState.session?.isViewingOwnData == false ->
        stringResource(R.string.recommendations_shared_phase_summary, uiState.phase.displayName.lowercase())
    else -> stringResource(R.string.recommendations_current_phase_summary, uiState.phase.displayName.lowercase())
}
