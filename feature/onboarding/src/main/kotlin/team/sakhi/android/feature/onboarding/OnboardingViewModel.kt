package team.sakhi.android.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.sakhi.android.platform.AndroidHealthConnectManager
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HealthConnectAvailability
import team.sakhi.android.platform.HapticImpact
import team.sakhi.android.platform.OnboardingHealthConnectImportResult
import team.sakhi.config.AppConfig
import team.sakhi.auth.AuthRepository
import team.sakhi.care.CareRuntimeState
import team.sakhi.care.CareStore
import team.sakhi.models.HealthCondition
import team.sakhi.models.ParentChildPermissions
import team.sakhi.models.RelationType
import team.sakhi.models.UserProfile
import team.sakhi.onboarding.OnboardingFlowCompletion
import team.sakhi.onboarding.OnboardingFlowIntent
import team.sakhi.onboarding.OnboardingFlowStore
import team.sakhi.onboarding.OnboardingNavState
import team.sakhi.onboarding.OnboardingFlowStep
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.validation.ValidationRules
import java.time.LocalDate
import java.time.YearMonth

/**
 * Thin Android wrapper over KMM onboarding state. All routing and branching stay
 * inside `OnboardingFlowStore`; this ViewModel only exposes the store to Compose
 * and translates UI events into KMM intents.
 *
 * Uses `AuthRepository.currentUserId` (not `SessionManager.current`) for the
 * onboarding-time user id: `SessionManager` is only started once `AppRoute.Home`
 * mounts (see `RootNavHost.HomeSessionGate`), so it is always null throughout
 * onboarding -- a real gap this pass found and fixed at its root (the missing
 * `sessionManager.startAsPrimary` call), not something to route around here.
 */
class OnboardingViewModel(
    val flowId: String,
    private val flowStore: OnboardingFlowStore,
    private val authRepository: AuthRepository,
    private val careStore: CareStore,
    private val periodLogRepository: PeriodLogRepository,
    private val cycleDataRepository: CycleDataRepository,
    private val userProfileRepository: UserProfileRepository,
    private val healthConnectManager: AndroidHealthConnectManager,
    private val hapticManager: AndroidHapticManager,
) : ViewModel() {

    val navState: StateFlow<OnboardingNavState> = flowStore.state
    val completion: SharedFlow<OnboardingFlowCompletion> = flowStore.completion
    private val _healthUiState = MutableStateFlow(OnboardingHealthUiState())
    val healthUiState: StateFlow<OnboardingHealthUiState> = _healthUiState.asStateFlow()
    private val _careInviteUiState = MutableStateFlow(OnboardingCareInviteUiState())
    val careInviteUiState: StateFlow<OnboardingCareInviteUiState> = _careInviteUiState.asStateFlow()
    private val _dataSourceUiState = MutableStateFlow(
        OnboardingDataSourceUiState(
            availability = healthConnectManager.availability(),
            requiredPermissions = healthConnectManager.onboardingRequiredPermissions,
        )
    )
    val dataSourceUiState: StateFlow<OnboardingDataSourceUiState> = _dataSourceUiState.asStateFlow()

    init {
        viewModelScope.launch {
            careStore.careState.collectLatest { careState ->
                val pendingInvitation = (careState as? CareRuntimeState.PendingInvitation)?.invitation
                _careInviteUiState.value = _careInviteUiState.value.copy(
                    pendingInvitation = pendingInvitation,
                    inviteCode = pendingInvitation?.inviteCode ?: _careInviteUiState.value.inviteCode,
                    invitationId = pendingInvitation?.id ?: _careInviteUiState.value.invitationId,
                    isConnected = careState is CareRuntimeState.OwnerConnected,
                )
            }
        }

        refreshDataSourceCapabilities()
    }

    fun continueFlow() {
        val error = validationErrorFor(navState.value.currentStep, healthUiState.value)
        flowStore.setFieldError(error)
        if (error == null) {
            flowStore.send(OnboardingFlowIntent.ContinueTapped)
        } else {
            hapticManager.error()
        }
    }

    fun goBack() {
        flowStore.send(OnboardingFlowIntent.BackTapped)
    }

    fun selectMode(partner: Boolean) {
        flowStore.send(OnboardingFlowIntent.ModeSelected(partner = partner))
    }

    fun resolvePrivacy(authenticated: Boolean, offline: Boolean) {
        flowStore.send(
            OnboardingFlowIntent.PrivacyDecided(
                authenticated = authenticated,
                offline = offline,
            )
        )
    }

    fun resolveTerms(accepted: Boolean) {
        if (!accepted) {
            hapticManager.error()
        }
        flowStore.send(OnboardingFlowIntent.TermsDecided(accepted = accepted))
    }

    fun selectPartnerRelation(relation: String) {
        hapticManager.impact(HapticImpact.LIGHT)
        flowStore.send(OnboardingFlowIntent.PartnerRelationSelected(relation = relation))
    }

    fun submitBeHerSakhiCode(code: String) {
        flowStore.send(OnboardingFlowIntent.BeHerSakhiCodeEntered(code = code))
    }

    private val _acceptUiState = MutableStateFlow(OnboardingAcceptUiState())
    val acceptUiState: StateFlow<OnboardingAcceptUiState> = _acceptUiState.asStateFlow()

    private val _conversionUiState = MutableStateFlow(OnboardingConversionUiState())
    val conversionUiState: StateFlow<OnboardingConversionUiState> = _conversionUiState.asStateFlow()

    // Mirrors iOS PartnerConversionWarningView.convertAndProceed(): deletes the
    // existing account's own health data before becoming a partner (using the
    // already-shared KMM PeriodLogRepository/CycleDataRepository.deleteAll, not a
    // new Android-local deletion path), then advances to BeHerAccept.
    fun convertAccountToPartnerAndProceed() {
        val userId = authRepository.currentUserId ?: return
        if (_conversionUiState.value.isConverting) return

        _conversionUiState.value = _conversionUiState.value.copy(isConverting = true, error = null)

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    periodLogRepository.deleteAll(userId)
                    cycleDataRepository.deleteAll(userId)
                }
            }.onSuccess {
                _conversionUiState.value = _conversionUiState.value.copy(isConverting = false)
                flowStore.send(OnboardingFlowIntent.ContinueTapped)
            }.onFailure { throwable ->
                hapticManager.error()
                _conversionUiState.value = _conversionUiState.value.copy(
                    isConverting = false,
                    error = throwable.message ?: "Something went wrong. Please try again.",
                )
            }
        }
    }

    // User chose to keep their own account instead of converting — matches iOS's
    // secondary "Keep My Account" action, which completes the flow as-is.
    fun keepOwnAccount() {
        flowStore.send(OnboardingFlowIntent.Complete)
    }

    // Mirrors iOS BeHerAcceptStep's `accept()`: no pre-existing health data to clear
    // here since it either already ran in PartnerConversionWarning (existing
    // account) or there is none to clear (brand-new partner).
    fun acceptBeHerSakhiInvite() {
        val userId = authRepository.currentUserId ?: run {
            hapticManager.error()
            _acceptUiState.value = _acceptUiState.value.copy(error = "Something went wrong. Please try again.")
            return
        }
        val code = navState.value.pendingInviteCode
        if (code.isBlank()) {
            hapticManager.error()
            _acceptUiState.value = _acceptUiState.value.copy(error = "This code doesn't exist. Please double-check with your partner.", canRetry = false)
            return
        }
        if (_acceptUiState.value.isAccepting) return

        _acceptUiState.value = _acceptUiState.value.copy(isAccepting = true, error = null)

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    careStore.acceptInvitation(inviteCode = code, acceptorUserId = userId)
                }
            }.onSuccess {
                hapticManager.success()
                _acceptUiState.value = _acceptUiState.value.copy(isAccepting = false, succeeded = true, error = null)
            }.onFailure { throwable ->
                hapticManager.error()
                _acceptUiState.value = _acceptUiState.value.copy(
                    isAccepting = false,
                    error = throwable.message ?: "Something went wrong. Please try again.",
                    canRetry = true,
                )
            }
        }
    }

    fun completeOnboarding() {
        flowStore.send(OnboardingFlowIntent.Complete)
    }

    private val _setupUiState = MutableStateFlow(OnboardingSetupUiState())
    val setupUiState: StateFlow<OnboardingSetupUiState> = _setupUiState.asStateFlow()

    // Mirrors iOS SakhiSetupLoadingView.runSetup(): saves everything collected in
    // the health steps (DOB/height/weight/last period/lengths/conditions) via the
    // already-shared KMM UserProfileRepository/CycleDataRepository, the "final
    // save" this whole onboarding module was missing -- without this, every
    // health-step screen was pure UI with nothing persisted at the end.
    // Only runs the save when this flow's plan actually included the health
    // steps: SetupLoading is also reached directly for returning users
    // (`handleOtpVerified`'s isReturningUser branch, `RETURNING_SYNC` flow) who
    // already have their own profile -- overwriting it with onboarding's
    // placeholder defaults would be a real data-loss bug, not a shortcut.
    fun handleSetupLoading() {
        if (!navState.value.plan.contains(OnboardingFlowStep.HealthConditions)) {
            completeOnboarding()
            return
        }

        val userId = authRepository.currentUserId ?: run {
            _setupUiState.value = _setupUiState.value.copy(error = "Something went wrong. Please try again.")
            return
        }
        if (_setupUiState.value.isSaving) return

        _setupUiState.value = _setupUiState.value.copy(isSaving = true, error = null)
        val health = _healthUiState.value

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    userProfileRepository.upsert(
                        UserProfile(
                            id = userId,
                            name = "",
                            email = "",
                            phone = "",
                            heightCm = health.heightCm,
                            weightKg = health.weightKg,
                            dateOfBirth = health.dateOfBirth.toKmmLocalDate().toString(),
                            healthConditions = health.selectedConditions.toList(),
                        )
                    ).getOrThrow()
                    cycleDataRepository.upsert(
                        team.sakhi.models.CycleData(
                            id = java.util.UUID.randomUUID().toString(),
                            userId = userId,
                            cycleStartDate = health.lastPeriodDate.toKmmLocalDate(),
                            periodStartDate = health.lastPeriodDate.toKmmLocalDate(),
                            cycleLength = health.cycleLength,
                            periodLength = health.periodLength,
                        )
                    ).getOrThrow()
                }
            }.onSuccess {
                _setupUiState.value = _setupUiState.value.copy(isSaving = false)
                completeOnboarding()
            }.onFailure { throwable ->
                _setupUiState.value = _setupUiState.value.copy(
                    isSaving = false,
                    error = throwable.message ?: "Something went wrong. Please try again.",
                )
            }
        }
    }

    fun resolveOtp(isReturningUser: Boolean, pendingInviteCode: String = "") {
        flowStore.send(
            OnboardingFlowIntent.OtpVerified(
                isReturningUser = isReturningUser,
                pendingInviteCode = pendingInviteCode,
            )
        )
    }

    fun startCareInviteUpgrade() {
        flowStore.send(OnboardingFlowIntent.PartnerInviteUpgradeRequired)
    }

    fun setInvitePermissions(permissions: ParentChildPermissions) {
        _careInviteUiState.value = _careInviteUiState.value.copy(
            permissions = permissions,
            errorMessage = null,
        )
    }

    fun onContactSelected(name: String, phone: String) {
        _careInviteUiState.value = _careInviteUiState.value.copy(
            selectedContactName = name,
            selectedContactPhone = phone,
        )
    }

    fun createCareInvitationAndContinue() {
        val userId = authRepository.currentUserId ?: run {
            hapticManager.error()
            _careInviteUiState.value = _careInviteUiState.value.copy(
                errorMessage = "Please sign in before creating a care invite.",
            )
            return
        }
        if (_careInviteUiState.value.isCreatingInvite) return

        val pendingInvitation = (careStore.careState.value as? CareRuntimeState.PendingInvitation)?.invitation
        if (pendingInvitation != null && pendingInvitation.inviteCode.isNotBlank()) {
            _careInviteUiState.value = _careInviteUiState.value.copy(
                inviteCode = pendingInvitation.inviteCode,
                invitationId = pendingInvitation.id,
                errorMessage = null,
            )
            flowStore.send(OnboardingFlowIntent.ContinueTapped)
            return
        }

        _careInviteUiState.value = _careInviteUiState.value.copy(
            isCreatingInvite = true,
            errorMessage = null,
        )
        hapticManager.impact(HapticImpact.MEDIUM)

        // `pendingPartnerRelation` (set by `PartnerRelationScreen`, reused by both
        // the manual and contact-based invite paths) and the contact-picked
        // name/phone (blank for the manual path -- that invitee identifies
        // themselves by entering the code) were previously never threaded
        // through here at all; fixed alongside building the contact-invite steps.
        val partnerRelation = flowStore.state.value.pendingPartnerRelation
        val contactName = _careInviteUiState.value.selectedContactName
        val contactPhone = _careInviteUiState.value.selectedContactPhone

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    careStore.createInvitation(
                        inviterName = "User",
                        inviteePhone = contactPhone,
                        inviteeName = contactName,
                        relationType = RelationType.PARTNER.value,
                        permissions = _careInviteUiState.value.permissions,
                        partnerRelation = partnerRelation,
                        userId = userId,
                    )
                }
            }.onSuccess { status ->
                val invitation = status.invitation
                _careInviteUiState.value = _careInviteUiState.value.copy(
                    isCreatingInvite = false,
                    inviteCode = invitation?.inviteCode.orEmpty(),
                    invitationId = invitation?.id.orEmpty(),
                    errorMessage = null,
                )
                hapticManager.success()
                flowStore.send(OnboardingFlowIntent.ContinueTapped)
            }.onFailure { throwable ->
                hapticManager.error()
                _careInviteUiState.value = _careInviteUiState.value.copy(
                    isCreatingInvite = false,
                    errorMessage = throwable.message ?: "Couldn't create invite right now. Please try again.",
                )
            }
        }
    }

    fun cancelCareInvitation(closeFlowOnSuccess: Boolean = false) {
        val userId = authRepository.currentUserId ?: return
        val invitationId = _careInviteUiState.value.invitationId
            .ifBlank { (_careInviteUiState.value.pendingInvitation ?: return).id }
        if (_careInviteUiState.value.isCancellingInvite) return

        _careInviteUiState.value = _careInviteUiState.value.copy(
            isCancellingInvite = true,
            errorMessage = null,
            cancelMessage = null,
        )

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    careStore.cancelInvitation(invitationId = invitationId, userId = userId)
                }
            }.onSuccess {
                hapticManager.success()
                _careInviteUiState.value = _careInviteUiState.value.copy(
                    isCancellingInvite = false,
                    invitationId = "",
                    inviteCode = "",
                    pendingInvitation = null,
                    cancelMessage = "That invite has been closed. Nothing was shared.",
                )
                if (closeFlowOnSuccess) {
                    flowStore.send(OnboardingFlowIntent.Complete)
                }
            }.onFailure { throwable ->
                hapticManager.error()
                _careInviteUiState.value = _careInviteUiState.value.copy(
                    isCancellingInvite = false,
                    errorMessage = throwable.message ?: "Could not cancel request.",
                )
            }
        }
    }

    fun continueToInviteWaiting() {
        flowStore.send(OnboardingFlowIntent.ContinueTapped)
    }

    fun dismissCareInviteError() {
        _careInviteUiState.value = _careInviteUiState.value.copy(errorMessage = null)
    }

    fun selectHealthConnectDataSource() {
        _dataSourceUiState.value = _dataSourceUiState.value.copy(
            selectedChoice = OnboardingDataSourceChoice.HealthConnect,
            showFailureAlert = false,
        )
        flowStore.setFieldError(null)
    }

    fun selectManualDataSource() {
        _dataSourceUiState.value = _dataSourceUiState.value.copy(
            selectedChoice = OnboardingDataSourceChoice.Manual,
            showFailureAlert = false,
        )
        flowStore.setFieldError(null)
    }

    fun reopenDataSourceFailureAlert() {
        if (_dataSourceUiState.value.importFailed) {
            _dataSourceUiState.value = _dataSourceUiState.value.copy(showFailureAlert = true)
        }
    }

    fun acknowledgeDataSourceFailureAndSwitchToManual() {
        _dataSourceUiState.value = _dataSourceUiState.value.copy(
            selectedChoice = OnboardingDataSourceChoice.Manual,
            showFailureAlert = false,
        )
    }

    fun continueManualDataSource() {
        flowStore.send(OnboardingFlowIntent.ReplaceRemaining(allManualHealthSteps()))
        flowStore.send(OnboardingFlowIntent.ContinueTapped)
    }

    fun onDataSourcePermissionsResult(grantedPermissions: Set<String>) {
        if (grantedPermissions.containsAll(healthConnectManager.onboardingRequiredPermissions)) {
            _dataSourceUiState.value = _dataSourceUiState.value.copy(hasPermissions = true)
            importFromHealthConnect()
        } else {
            hapticManager.error()
            failDataSourceImport("Health Connect access was not granted.")
            refreshDataSourceCapabilities()
        }
    }

    fun handleUnavailableHealthConnectSelection() {
        val message = when (_dataSourceUiState.value.availability) {
            HealthConnectAvailability.NotInstalled -> "Health Connect is not available on this device."
            HealthConnectAvailability.NotSupported -> "Health Connect is not supported on this device."
            HealthConnectAvailability.Available -> "Health Connect access was not granted."
        }
        hapticManager.error()
        failDataSourceImport(message)
    }

    fun importFromHealthConnect() {
        if (_dataSourceUiState.value.isImporting) return

        _dataSourceUiState.value = _dataSourceUiState.value.copy(
            isImporting = true,
            showFailureAlert = false,
            failureMessage = null,
        )

        viewModelScope.launch {
            runCatching { healthConnectManager.importOnboardingSnapshot() }
                .onSuccess { result ->
                    if (result.hasImportedData) {
                        applyHealthConnectImport(result)
                        _dataSourceUiState.value = _dataSourceUiState.value.copy(
                            hasPermissions = true,
                            isImporting = false,
                            importFailed = false,
                            failureMessage = null,
                            showFailureAlert = false,
                            selectedChoice = OnboardingDataSourceChoice.HealthConnect,
                        )
                        flowStore.send(
                            OnboardingFlowIntent.ReplaceRemaining(
                                remainingHealthStepsAfterImport(result),
                            )
                        )
                        flowStore.send(OnboardingFlowIntent.ContinueTapped)
                    } else {
                        hapticManager.error()
                        failDataSourceImport(
                            result.failureMessage ?: "Health Connect doesn't have the details needed for onboarding.",
                        )
                    }
                }
                .onFailure { throwable ->
                    hapticManager.error()
                    failDataSourceImport(
                        throwable.message ?: "Health Connect access was not granted.",
                    )
                }
        }
    }

    private fun refreshDataSourceCapabilities() {
        viewModelScope.launch {
            val availability = healthConnectManager.availability()
            val hasPermissions = runCatching {
                healthConnectManager.hasAllOnboardingPermissions()
            }.getOrDefault(false)

            _dataSourceUiState.value = _dataSourceUiState.value.copy(
                availability = availability,
                requiredPermissions = healthConnectManager.onboardingRequiredPermissions,
                hasPermissions = hasPermissions,
            )
        }
    }

    private fun applyHealthConnectImport(result: OnboardingHealthConnectImportResult) {
        var next = _healthUiState.value

        result.dateOfBirth?.let { next = next.copy(dateOfBirth = it) }
        result.heightCm?.let { next = next.copy(heightCm = it, useImperialHeight = false) }
        result.weightKg?.let { next = next.copy(weightKg = it, useMetricWeight = true) }
        result.lastPeriodDate?.let {
            next = next.copy(
                lastPeriodDate = it,
                displayedLastPeriodMonth = YearMonth.from(it),
            )
        }
        result.periodLength?.let {
            next = next.copy(
                periodLength = it,
                periodLengthText = it.toString(),
            )
        }
        result.cycleLength?.let {
            next = next.copy(
                cycleLength = it,
                cycleLengthText = it.toString(),
            )
        }

        _healthUiState.value = next
        flowStore.setFieldError(null)
    }

    private fun failDataSourceImport(message: String) {
        _dataSourceUiState.value = _dataSourceUiState.value.copy(
            isImporting = false,
            importFailed = true,
            failureMessage = message,
            showFailureAlert = true,
        )
    }

    fun updateDateOfBirth(date: LocalDate) {
        _healthUiState.value = _healthUiState.value.copy(dateOfBirth = date)
        flowStore.setFieldError(null)
    }

    fun setHeightUnit(useImperial: Boolean) {
        _healthUiState.value = _healthUiState.value.copy(useImperialHeight = useImperial)
        flowStore.setFieldError(null)
    }

    fun setHeightCm(heightCm: Double) {
        _healthUiState.value = _healthUiState.value.copy(heightCm = heightCm.coerceIn(100.0, 220.0))
        flowStore.setFieldError(null)
    }

    fun setWeightUnit(useMetric: Boolean) {
        _healthUiState.value = _healthUiState.value.copy(useMetricWeight = useMetric)
        flowStore.setFieldError(null)
    }

    fun setWeightKg(weightKg: Double) {
        _healthUiState.value = _healthUiState.value.copy(weightKg = weightKg.coerceIn(30.0, 150.0))
        flowStore.setFieldError(null)
    }

    fun updateLastPeriodDate(date: LocalDate) {
        val clamped = if (date.isAfter(LocalDate.now())) LocalDate.now() else date
        _healthUiState.value = _healthUiState.value.copy(
            lastPeriodDate = clamped,
            displayedLastPeriodMonth = YearMonth.from(clamped),
        )
        flowStore.setFieldError(null)
    }

    fun showPreviousLastPeriodMonth() {
        _healthUiState.value = _healthUiState.value.copy(
            displayedLastPeriodMonth = _healthUiState.value.displayedLastPeriodMonth.minusMonths(1),
        )
    }

    fun showNextLastPeriodMonth() {
        val currentMonth = YearMonth.now()
        val nextMonth = _healthUiState.value.displayedLastPeriodMonth.plusMonths(1)
        _healthUiState.value = _healthUiState.value.copy(
            displayedLastPeriodMonth = minOf(nextMonth, currentMonth),
        )
    }

    fun updatePeriodLengthText(raw: String) {
        val digits = raw.filter(Char::isDigit).take(2)
        val nextLength = digits.toIntOrNull()?.takeIf { it in 1..14 } ?: _healthUiState.value.periodLength
        _healthUiState.value = _healthUiState.value.copy(
            periodLengthText = digits,
            periodLength = nextLength,
        )
        flowStore.setFieldError(null)
    }

    fun updateCycleLengthText(raw: String) {
        val digits = raw.filter(Char::isDigit).take(2)
        val nextLength = digits.toIntOrNull()?.takeIf { it in 21..45 } ?: _healthUiState.value.cycleLength
        _healthUiState.value = _healthUiState.value.copy(
            cycleLengthText = digits,
            cycleLength = nextLength,
        )
        flowStore.setFieldError(null)
    }

    fun toggleCondition(condition: HealthCondition) {
        val selected = _healthUiState.value.selectedConditions.toMutableSet()
        if (!selected.add(condition)) {
            selected.remove(condition)
        }
        _healthUiState.value = _healthUiState.value.copy(selectedConditions = selected)
        flowStore.setFieldError(null)
    }

    private fun validationErrorFor(
        step: OnboardingFlowStep,
        state: OnboardingHealthUiState,
    ): String? = when (step) {
        OnboardingFlowStep.DateOfBirth ->
            if (!ValidationRules.isValidDateOfBirth(state.dateOfBirth.toKmmLocalDate())) "Choose a valid date of birth" else null
        OnboardingFlowStep.Height ->
            if (!ValidationRules.isValidHeightCm(state.heightCm)) "Choose a valid height" else null
        OnboardingFlowStep.Weight ->
            if (!ValidationRules.isValidWeightKg(state.weightKg)) "Choose a valid weight" else null
        OnboardingFlowStep.LastPeriod ->
            if (!ValidationRules.isValidLogDate(state.lastPeriodDate.toKmmLocalDate())) "Choose a valid date" else null
        OnboardingFlowStep.CycleLength ->
            if (!ValidationRules.isValidCycleLength(state.cycleLength)) "Choose a valid cycle length" else null
        else -> null
    }
}

data class OnboardingAcceptUiState(
    val isAccepting: Boolean = false,
    val succeeded: Boolean = false,
    val error: String? = null,
    val canRetry: Boolean = true,
)

data class OnboardingConversionUiState(
    val isConverting: Boolean = false,
    val error: String? = null,
)

data class OnboardingSetupUiState(
    val isSaving: Boolean = false,
    val error: String? = null,
)

data class OnboardingCareInviteUiState(
    val permissions: ParentChildPermissions = allEnabledCareInvitePermissions(),
    val inviteCode: String = "",
    val invitationId: String = "",
    val pendingInvitation: team.sakhi.models.PartnerInvitation? = null,
    val isConnected: Boolean = false,
    val isCreatingInvite: Boolean = false,
    val isCancellingInvite: Boolean = false,
    val errorMessage: String? = null,
    val cancelMessage: String? = null,
    // Picked via the contact-based invite flow (InvitePickContact step). Empty
    // for the manual-code invite path, which never collects these -- the
    // invitee identifies themselves by entering the code on their own device.
    val selectedContactName: String = "",
    val selectedContactPhone: String = "",
)

enum class OnboardingDataSourceChoice {
    HealthConnect,
    Manual,
}

data class OnboardingDataSourceUiState(
    val selectedChoice: OnboardingDataSourceChoice = OnboardingDataSourceChoice.HealthConnect,
    val availability: HealthConnectAvailability = HealthConnectAvailability.NotSupported,
    val requiredPermissions: Set<String> = emptySet(),
    val hasPermissions: Boolean = false,
    val isImporting: Boolean = false,
    val importFailed: Boolean = false,
    val failureMessage: String? = null,
    val showFailureAlert: Boolean = false,
)

data class OnboardingHealthUiState(
    val dateOfBirth: LocalDate = LocalDate.now().minusYears(22),
    val heightCm: Double = 157.48,
    val weightKg: Double = 50.0,
    val lastPeriodDate: LocalDate = LocalDate.now(),
    val cycleLength: Int = AppConfig.DEFAULT_CYCLE_LENGTH,
    val cycleLengthText: String = AppConfig.DEFAULT_CYCLE_LENGTH.toString(),
    val periodLength: Int = 3,
    val periodLengthText: String = "3",
    val selectedConditions: Set<HealthCondition> = emptySet(),
    val useImperialHeight: Boolean = true,
    val useMetricWeight: Boolean = true,
    val displayedLastPeriodMonth: YearMonth = YearMonth.now(),
)

private fun LocalDate.toKmmLocalDate(): kotlinx.datetime.LocalDate =
    kotlinx.datetime.LocalDate(year, monthValue, dayOfMonth)

private fun allManualHealthSteps(): List<OnboardingFlowStep> = listOf(
    OnboardingFlowStep.DateOfBirth,
    OnboardingFlowStep.Height,
    OnboardingFlowStep.Weight,
    OnboardingFlowStep.LastPeriod,
    OnboardingFlowStep.PeriodLength,
    OnboardingFlowStep.CycleLength,
    OnboardingFlowStep.HealthConditions,
    OnboardingFlowStep.Terms,
    OnboardingFlowStep.SetupLoading,
)

private fun remainingHealthStepsAfterImport(
    result: OnboardingHealthConnectImportResult,
): List<OnboardingFlowStep> = buildList {
    if (result.dateOfBirth == null) add(OnboardingFlowStep.DateOfBirth)
    if (result.heightCm == null) add(OnboardingFlowStep.Height)
    if (result.weightKg == null) add(OnboardingFlowStep.Weight)
    if (result.lastPeriodDate == null) add(OnboardingFlowStep.LastPeriod)
    if (result.periodLength == null) add(OnboardingFlowStep.PeriodLength)
    if (result.cycleLength == null) add(OnboardingFlowStep.CycleLength)
    add(OnboardingFlowStep.HealthConditions)
    add(OnboardingFlowStep.Terms)
    add(OnboardingFlowStep.SetupLoading)
}

private fun allEnabledCareInvitePermissions(): ParentChildPermissions = ParentChildPermissions(
    canViewPeriodDates = true,
    canViewSymptoms = true,
    canViewMoods = true,
    canViewMedications = true,
    canViewPredictions = true,
    canLogPeriods = true,
    canViewCycleHistory = true,
    canViewDailyLogs = true,
    canViewOvulationTests = true,
    canViewTemperature = true,
    canViewWeight = true,
    canViewNotes = true,
    canViewDischarge = true,
    canViewSexualActivity = false,
)
