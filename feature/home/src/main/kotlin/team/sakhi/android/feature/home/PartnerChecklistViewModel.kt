package team.sakhi.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.date.DateConverter
import team.sakhi.models.CyclePhase
import team.sakhi.models.PartnerChecklistItem
import team.sakhi.repositories.AIRepository
import team.sakhi.session.SessionManager

data class PartnerChecklistUiState(
    val items: List<PartnerChecklistItem> = emptyList(),
    val completedCount: Int = 0,
    val isGenerating: Boolean = false,
    val failedToGenerate: Boolean = false,
)

/**
 * Android port of iOS `PartnerChecklistViewModel`: an AI-generated (Claude,
 * via the shared `AIRepository`) daily list of caring things a partner can do
 * today, persisted server-side (`app_update_policies`-style single-row-per-
 * day upsert in Supabase's `partner_checklists` table -- no on-device
 * Realm/Room object needed, unlike iOS's local-first version of this same
 * feature, since `AIRepository.getChecklist`/`generatePartnerChecklist`
 * already read/write straight to Supabase).
 */
class PartnerChecklistViewModel(
    private val sessionManager: SessionManager,
    private val aiRepository: AIRepository,
    private val hapticManager: AndroidHapticManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerChecklistUiState())
    val uiState: StateFlow<PartnerChecklistUiState> = _uiState.asStateFlow()

    private var lastLoadedKey: String? = null

    fun loadOrGenerate(cyclePhase: CyclePhase, cycleDay: Int) {
        val session = sessionManager.current ?: return
        if (session.isViewingOwnData) return
        val partnerUserId = session.userId
        val primaryUserId = session.targetUserId
        val partnershipId = session.activePartnership?.id ?: return
        val dateString = DateConverter.today().toString()

        val key = "$partnerUserId|$dateString"
        if (key == lastLoadedKey) return
        lastLoadedKey = key

        _uiState.update { it.copy(isGenerating = true, failedToGenerate = false) }

        viewModelScope.launch {
            val existing = aiRepository.getChecklist(partnerUserId, dateString).getOrNull()
            if (existing != null) {
                _uiState.update {
                    it.copy(items = existing.items, completedCount = existing.completedCount, isGenerating = false)
                }
                return@launch
            }

            aiRepository.generatePartnerChecklist(
                partnerUserId = partnerUserId,
                primaryUserId = primaryUserId,
                partnershipId = partnershipId,
                dateString = dateString,
                cyclePhase = cyclePhase.value,
                cycleDay = cycleDay,
                partnerName = session.userName,
            ).onSuccess { checklist ->
                _uiState.update {
                    it.copy(items = checklist.items, completedCount = checklist.completedCount, isGenerating = false)
                }
            }.onFailure {
                // No on-device fallback list here (unlike iOS's local Realm
                // fallback texts) -- the retry button re-runs generation
                // instead, since there's no local persistence layer to fall
                // back to on Android for this feature.
                _uiState.update { it.copy(isGenerating = false, failedToGenerate = true) }
                lastLoadedKey = null
            }
        }
    }

    fun retry(cyclePhase: CyclePhase, cycleDay: Int) {
        lastLoadedKey = null
        loadOrGenerate(cyclePhase, cycleDay)
    }

    fun toggle(itemId: String) {
        val session = sessionManager.current ?: return
        if (session.isViewingOwnData) return
        hapticManager.impact(HapticImpact.LIGHT)

        val dateString = DateConverter.today().toString()
        _uiState.update { state ->
            val updated = state.items.map { item ->
                if (item.id == itemId) item.copy(isCompleted = !item.isCompleted) else item
            }
            state.copy(items = updated, completedCount = updated.count { it.isCompleted })
        }

        viewModelScope.launch {
            aiRepository.toggleChecklistItem(session.userId, dateString, itemId)
        }
    }
}
