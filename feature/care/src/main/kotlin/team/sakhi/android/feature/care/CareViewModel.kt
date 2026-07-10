package team.sakhi.android.feature.care

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.care.CareRuntimeState
import team.sakhi.care.CareStore
import team.sakhi.models.ParentChildPermissions
import team.sakhi.models.PartnerInvitation
import team.sakhi.models.RelationType
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager

data class CareUiState(
    val session: SessionContext? = null,
    val careState: CareRuntimeState = CareRuntimeState.Loading,
    val inviteeName: String = "",
    val partnerRelation: String = "",
    val acceptInviteCode: String = "",
    val latestInvitation: PartnerInvitation? = null,
    val isRefreshing: Boolean = false,
    val isCreatingInvite: Boolean = false,
    val isAcceptingInvite: Boolean = false,
    val isCancellingInvite: Boolean = false,
    val isRemovingPartnership: Boolean = false,
    val isSavingPermissions: Boolean = false,
    val infoMessage: String? = null,
    val error: String? = null,
)

/**
 * Thin care-state adapter over shared `CareStore` plus session state.
 * Android owns only local form fields and delegates every care mutation to KMM.
 */
class CareViewModel(
    private val appContext: Context,
    private val sessionManager: SessionManager,
    private val careStore: CareStore,
    private val hapticManager: AndroidHapticManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CareUiState())
    val uiState: StateFlow<CareUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.session.collectLatest { session ->
                if (session == null) {
                    _uiState.value = CareUiState(careState = CareRuntimeState.Disconnected)
                    return@collectLatest
                }

                _uiState.update {
                    it.copy(
                        session = session,
                        isRefreshing = true,
                        error = null,
                    )
                }

                runCatching { careStore.refresh(session.userId) }
                    .onSuccess {
                        _uiState.update { state ->
                            state.copy(isRefreshing = false, error = null)
                        }
                    }
                    .onFailure { throwable ->
                        _uiState.update { state ->
                            state.copy(
                                isRefreshing = false,
                                error = throwable.message ?: appContext.getString(R.string.care_error_load_status),
                            )
                        }
                    }
            }
        }

        viewModelScope.launch {
            combine(
                sessionManager.session,
                careStore.careState,
            ) { session, careState ->
                session to careState
            }.collectLatest { (session, careState) ->
                _uiState.update { state ->
                    state.copy(
                        session = session,
                        careState = careState,
                        latestInvitation = latestInvitation(session, careState),
                    )
                }
            }
        }
    }

    fun onInviteeNameChanged(value: String) {
        _uiState.update {
            it.copy(
                inviteeName = value,
                error = null,
                infoMessage = null,
            )
        }
    }

    fun onPartnerRelationChanged(value: String) {
        _uiState.update {
            it.copy(
                partnerRelation = value,
                error = null,
                infoMessage = null,
            )
        }
    }

    fun onAcceptInviteCodeChanged(value: String) {
        _uiState.update {
            it.copy(
                acceptInviteCode = value.uppercase(),
                error = null,
                infoMessage = null,
            )
        }
    }

    fun refresh() {
        val session = sessionManager.current ?: return
        _uiState.update { it.copy(isRefreshing = true, error = null, infoMessage = null) }

        viewModelScope.launch {
            runCatching { careStore.refresh(session.userId) }
                .onSuccess {
                    _uiState.update { it.copy(isRefreshing = false, error = null) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            error = throwable.message ?: appContext.getString(R.string.care_error_refresh_status),
                        )
                    }
                }
        }
    }

    fun createInvitation() {
        val session = sessionManager.current ?: return
        val state = _uiState.value
        if (state.isCreatingInvite) return

        _uiState.update { it.copy(isCreatingInvite = true, error = null, infoMessage = null) }

        viewModelScope.launch {
            runCatching {
                careStore.createInvitation(
                    inviterName = session.userName.ifBlank { appContext.getString(R.string.care_fallback_user) },
                    inviteePhone = "",
                    inviteeName = state.inviteeName.trim(),
                    relationType = RelationType.PARTNER.value,
                    permissions = ParentChildPermissions.default,
                    partnerRelation = state.partnerRelation.trim(),
                    userId = session.userId,
                )
            }.onSuccess { status ->
                _uiState.update {
                    it.copy(
                        isCreatingInvite = false,
                        infoMessage = if (status.invitation?.inviteCode.isNullOrBlank()) {
                            appContext.getString(R.string.care_info_invite_ready)
                        } else {
                            appContext.getString(R.string.care_info_invite_code_ready)
                        },
                        error = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isCreatingInvite = false,
                        error = throwable.message ?: appContext.getString(R.string.care_error_create_invite),
                    )
                }
            }
        }
    }

    fun acceptInvitation() {
        val session = sessionManager.current ?: return
        val inviteCode = _uiState.value.acceptInviteCode.trim().uppercase()
        if (inviteCode.isBlank()) {
            hapticManager.error()
            _uiState.update { it.copy(error = appContext.getString(R.string.care_error_enter_invite_code)) }
            return
        }
        if (_uiState.value.isAcceptingInvite) return

        _uiState.update { it.copy(isAcceptingInvite = true, error = null, infoMessage = null) }

        viewModelScope.launch {
            runCatching {
                careStore.acceptInvitation(
                    inviteCode = inviteCode,
                    acceptorUserId = session.userId,
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        acceptInviteCode = "",
                        isAcceptingInvite = false,
                        infoMessage = appContext.getString(R.string.care_info_invite_accepted),
                        error = null,
                    )
                }
            }.onFailure { throwable ->
                hapticManager.error()
                _uiState.update {
                    it.copy(
                        isAcceptingInvite = false,
                        error = throwable.message ?: appContext.getString(R.string.care_error_accept_invite),
                    )
                }
            }
        }
    }

    fun cancelInvitation() {
        val session = sessionManager.current ?: return
        val invitation = (_uiState.value.careState as? CareRuntimeState.PendingInvitation)?.invitation ?: return
        if (_uiState.value.isCancellingInvite) return

        _uiState.update { it.copy(isCancellingInvite = true, error = null, infoMessage = null) }

        viewModelScope.launch {
            runCatching { careStore.cancelInvitation(invitationId = invitation.id, userId = session.userId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isCancellingInvite = false,
                            infoMessage = appContext.getString(R.string.care_info_invite_closed),
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isCancellingInvite = false,
                            error = throwable.message ?: appContext.getString(R.string.care_error_cancel_invite),
                        )
                    }
                }
        }
    }

    fun removePartnership(partnershipId: String) {
        val session = sessionManager.current ?: return
        if (_uiState.value.isRemovingPartnership) return

        hapticManager.impact(HapticImpact.MEDIUM)
        _uiState.update { it.copy(isRemovingPartnership = true, error = null, infoMessage = null) }

        viewModelScope.launch {
            runCatching { careStore.leavePartnership(partnershipId = partnershipId, userId = session.userId) }
                .onSuccess {
                    _uiState.update { it.copy(isRemovingPartnership = false, infoMessage = null) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isRemovingPartnership = false,
                            error = throwable.message ?: appContext.getString(R.string.care_error_complete_action),
                        )
                    }
                }
        }
    }

    fun updatePermissions(partnershipId: String, permissions: ParentChildPermissions, onComplete: (Boolean) -> Unit) {
        val session = sessionManager.current ?: return
        if (_uiState.value.isSavingPermissions) return

        _uiState.update { it.copy(isSavingPermissions = true, error = null, infoMessage = null) }

        viewModelScope.launch {
            runCatching {
                careStore.updatePermissions(
                    partnershipId = partnershipId,
                    permissions = permissions,
                    canMarkPeriodPresence = permissions.canLogPeriods,
                    userId = session.userId,
                )
            }.onSuccess {
                hapticManager.success()
                _uiState.update { it.copy(isSavingPermissions = false) }
                onComplete(true)
            }.onFailure { throwable ->
                hapticManager.error()
                    _uiState.update {
                        it.copy(
                            isSavingPermissions = false,
                            error = throwable.message ?: appContext.getString(R.string.care_error_permission_change_not_saved),
                        )
                    }
                onComplete(false)
            }
        }
    }

    private fun latestInvitation(
        session: SessionContext?,
        careState: CareRuntimeState,
    ): PartnerInvitation? {
        val stateInvitation = (careState as? CareRuntimeState.PendingInvitation)?.invitation
        if (stateInvitation != null) return stateInvitation
        return session?.sentInvitations?.firstOrNull()
    }
}
