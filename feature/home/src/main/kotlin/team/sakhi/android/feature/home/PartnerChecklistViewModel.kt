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
import team.sakhi.session.Permission
import team.sakhi.session.SessionContext
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
    private val appContext: android.content.Context,
    private val sessionManager: SessionManager,
    private val aiRepository: AIRepository,
    private val hapticManager: AndroidHapticManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartnerChecklistUiState())
    val uiState: StateFlow<PartnerChecklistUiState> = _uiState.asStateFlow()

    private var lastLoadedKey: String? = null

    fun loadOrGenerate(
        cyclePhase: CyclePhase,
        cycleDay: Int,
        daysUntilNextPeriod: Int? = null,
    ) {
        val session = sessionManager.current ?: return
        if (session.isViewingOwnData) return
        if (!canAccessChecklist(session)) return
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
                if (discardStaleLoad(session, key)) return@launch
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
                // `session.userName` is empty when the name is genuinely unknown (the
                // shared resolver no longer substitutes the word "User"), and this value
                // is interpolated straight into an AI prompt -- "Suggest the caring things
                // $partnerName can do today" -- so a blank would produce a malformed
                // sentence for the model.
                partnerName = session.userName.ifBlank {
                    appContext.getString(R.string.home_partner_checklist_fallback_name)
                },
            ).onSuccess { checklist ->
                if (discardStaleLoad(session, key)) return@onSuccess
                _uiState.update {
                    it.copy(items = checklist.items, completedCount = checklist.completedCount, isGenerating = false)
                }
            }.onFailure {
                if (discardStaleLoad(session, key)) return@onFailure
                // No on-device fallback list here (unlike iOS's local Realm
                // fallback texts) -- the retry button re-runs generation
                // instead, since there's no local persistence layer to fall
                // back to on Android for this feature.
                val fallbackItems = fallbackChecklistItems(
                    cyclePhase = cyclePhase,
                    cycleDay = cycleDay,
                    daysUntilNextPeriod = daysUntilNextPeriod,
                )
                _uiState.update {
                    it.copy(
                        items = fallbackItems,
                        completedCount = 0,
                        isGenerating = false,
                        failedToGenerate = false,
                    )
                }
                lastLoadedKey = null
            }
        }
    }

    fun retry(
        cyclePhase: CyclePhase,
        cycleDay: Int,
        daysUntilNextPeriod: Int? = null,
    ) {
        lastLoadedKey = null
        loadOrGenerate(cyclePhase, cycleDay, daysUntilNextPeriod)
    }

    fun toggle(itemId: String) {
        val session = sessionManager.current ?: return
        if (session.isViewingOwnData) return
        if (!canAccessChecklist(session)) return
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

    private fun discardStaleLoad(
        requestedSession: SessionContext,
        requestKey: String,
    ): Boolean {
        if (sessionManager.current == requestedSession) return false
        if (lastLoadedKey == requestKey) {
            lastLoadedKey = null
        }
        return true
    }

    private fun canAccessChecklist(session: SessionContext): Boolean {
        return session.can(Permission.VIEW_PREDICTIONS) ||
            session.can(Permission.VIEW_CYCLE_HISTORY)
    }

    private fun fallbackChecklistItems(
        cyclePhase: CyclePhase,
        cycleDay: Int,
        daysUntilNextPeriod: Int?,
    ): List<PartnerChecklistItem> {
        val texts = when (cyclePhase) {
            CyclePhase.MENSTRUAL -> {
                if (cycleDay <= 2) {
                    listOf(
                        "Keep a heat pad ready",
                        "Offer warm food or tea",
                        "Keep plans light today",
                        "Ask comfort or space",
                    )
                } else {
                    listOf(
                        "Let her rest longer",
                        "Check pain gently once",
                        "Avoid surprise plans today",
                        "Handle one small chore",
                    )
                }
            }
            CyclePhase.FOLLICULAR -> listOf(
                "Suggest one light plan",
                "Celebrate one small win",
                "Match her fresh energy",
                "Ask what she wants next",
            )
            CyclePhase.OVULATION -> listOf(
                "Give a specific compliment",
                "Plan quality time together",
                "Be fully present today",
                "Say something real",
            )
            CyclePhase.LUTEAL -> {
                if (daysUntilNextPeriod in 1..3) {
                    listOf(
                        "Stock her comfort snack",
                        "Keep evening plans calm",
                        "Listen without fixing",
                        "Avoid unnecessary arguments",
                    )
                } else {
                    listOf(
                        "Give her extra patience",
                        "Lower pressure around plans",
                        "Check in softly",
                        "Let small things pass",
                    )
                }
            }
            CyclePhase.DELAYED -> listOf(
                "Keep things normal",
                "Avoid repeated date questions",
                "Offer calm reassurance",
                "Let her set the pace",
            )
            CyclePhase.UNKNOWN -> listOf(
                "Ask how she feels",
                "Send a gentle voice note",
                "Offer help without pressure",
                "Respect what stays private",
            )
        }
        return texts.mapIndexed { index, text ->
            PartnerChecklistItem(
                id = "fallback-${cyclePhase.value.lowercase()}-$index",
                text = text,
                isCompleted = false,
            )
        }
    }
}
