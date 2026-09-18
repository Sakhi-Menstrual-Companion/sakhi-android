package team.sakhi.android.feature.onboarding

import android.content.Context
import co.touchlab.kermit.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.sakhi.android.platform.AndroidHealthConnectManager
import team.sakhi.android.platform.HealthConnectSourceApp
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
import team.sakhi.onboarding.PendingInviteStore
import team.sakhi.platform.PlatformKeyValueStore
import team.sakhi.onboarding.OnboardingFlowStore
import team.sakhi.onboarding.OnboardingNavState
import team.sakhi.onboarding.OnboardingFlowStep
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.sync.DataMigration
import team.sakhi.validation.ValidationRules
import java.time.LocalDate
import java.time.YearMonth
import team.sakhi.repositories.CareInviteException
import team.sakhi.android.common.oneSakhiRefusalMessage
import team.sakhi.android.common.toSafeUserMessage

private const val ONBOARDING_CONTINUE_TRANSITION_GUARD_NANOS = 420_000_000L

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
private val inviteLog = Logger.withTag("SakhiInvite")

class OnboardingViewModel(
    val flowId: String,
    private val appContext: Context,
    private val flowStore: OnboardingFlowStore,
    private val authRepository: AuthRepository,
    private val careStore: CareStore,
    private val periodLogRepository: PeriodLogRepository,
    private val cycleDataRepository: CycleDataRepository,
    private val userProfileRepository: UserProfileRepository,
    private val healthConnectManager: AndroidHealthConnectManager,
    private val hapticManager: AndroidHapticManager,
    // Injected, NOT `PlatformKeyValueStore()`. The bare constructor leaves `prefs` null and
    // every write silently lands in a per-instance HashMap that dies with the object, which
    // is why `PendingInviteStore` never actually remembered anything. Koin's singleton is the
    // one that had `init(androidContext())` called on it (see `platformModule()`).
    private val kvStore: PlatformKeyValueStore,
) : ViewModel() {

    val navState: StateFlow<OnboardingNavState> = flowStore.state
    val completion: SharedFlow<OnboardingFlowCompletion> = flowStore.completion
    private var continueGuardUntilNanos = 0L
    private val _healthUiState = MutableStateFlow(OnboardingHealthUiState())
    val healthUiState: StateFlow<OnboardingHealthUiState> = _healthUiState.asStateFlow()
    private val _careInviteUiState = MutableStateFlow(OnboardingCareInviteUiState())
    val careInviteUiState: StateFlow<OnboardingCareInviteUiState> = _careInviteUiState.asStateFlow()
    private val _dataSourceUiState = MutableStateFlow(
        OnboardingDataSourceUiState(
            availability = healthConnectManager.availability(),
            requiredPermissions = healthConnectManager.onboardingRequiredPermissions,
            sourceApps = healthConnectManager.availableSourceApps(),
        )
    )
    val dataSourceUiState: StateFlow<OnboardingDataSourceUiState> = _dataSourceUiState.asStateFlow()

    /**
     * The last restart token this instance acted on. Survives configuration changes because
     * the view model does, which is exactly what makes a rotation mid-onboarding safe.
     */
    private var lastHandledRestartToken = 0

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

    /**
     * Starts the flow over when the app has re-entered onboarding from a real session,
     * which in practice means she signed out.
     *
     * `koinViewModel(key = flowId)` resolves out of the Activity's ViewModelStore, so
     * signing out hands the "newUser" flow back the SAME instance, still parked wherever
     * the previous run left it. Traced on the QA emulator: after a returning-user sign-in
     * the store sat on `otpVerification`, and re-mounting it there let the retained
     * `AuthViewModel`'s stale verified result fire again. That emitted completion, which
     * for NEW_OWNER appends a `SetupLoading` step, and the app parked on a full-screen
     * spinner waiting for a setup that had already happened and now had no session.
     *
     * Driven by a token from `RootNavHost` rather than by watching for completion. The
     * completion signal arrives asynchronously and lost the race: the log showed this mount
     * check running 23ms BEFORE the previous run's completion was delivered, so a flag set
     * from it was still false exactly when it mattered. The token changes only on a real
     * Home -> SignedOut transition, so a rotation mid-onboarding carries the same token and
     * her progress survives.
     */
    fun restartFlowIfNewRun(restartToken: Int): Boolean {
        if (restartToken == lastHandledRestartToken) return false
        lastHandledRestartToken = restartToken
        flowStore.send(OnboardingFlowIntent.Restart)
        // Cleared with it: these hold the previous user's height, weight, date of birth and
        // care-invite details, and none of that may still be in the form when the next
        // person starts onboarding on this device.
        _healthUiState.value = OnboardingHealthUiState()
        _careInviteUiState.value = OnboardingCareInviteUiState()
        _acceptUiState.value = OnboardingAcceptUiState()
        _conversionUiState.value = OnboardingConversionUiState()
        _setupUiState.value = OnboardingSetupUiState()
        refreshDataSourceCapabilities()
        return true
    }

    fun continueFlow(expectedStep: OnboardingFlowStep? = null) {
        val state = navState.value
        if (expectedStep != null && state.currentStep != expectedStep) return

        val now = System.nanoTime()
        if (now < continueGuardUntilNanos) return

        val error = validationErrorFor(state.currentStep, healthUiState.value)
        flowStore.setFieldError(error)
        if (error == null) {
            continueGuardUntilNanos = now + ONBOARDING_CONTINUE_TRANSITION_GUARD_NANOS
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
        // DO NOT start the local-only session here. An earlier version did, and it was a
        // real regression: `SessionState.LocalOnlyUser` is mapped by `AccountClassifier`
        // straight to `AppRoute.Home`, so the instant "Continue Offline" was tapped the
        // app jumped to Home and the entire rest of onboarding (offline warning, terms,
        // DOB, height, weight, cycle questions) was skipped. The offline session is
        // started at the END of the flow instead -- see `handleSetupLoading`.
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
        // Also remembered outside the flow store. Verifying the OTP hands routing back to
        // AccountClassifier, which decides on account state alone and throws this flow
        // away, so the code has to survive somewhere the app can still find it afterwards.
        // See PendingInviteStore.
        PendingInviteStore.save(kvStore, code)
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
                if (!isStillCurrentUser(userId)) {
                    _conversionUiState.value = _conversionUiState.value.copy(isConverting = false)
                    return@onSuccess
                }
                _conversionUiState.value = _conversionUiState.value.copy(isConverting = false)
                flowStore.send(OnboardingFlowIntent.ContinueTapped)
            }.onFailure { throwable ->
                if (!isStillCurrentUser(userId)) {
                    _conversionUiState.value = _conversionUiState.value.copy(isConverting = false)
                    return@onFailure
                }
                hapticManager.error()
                _conversionUiState.value = _conversionUiState.value.copy(
                    isConverting = false,
                    error = throwable.toSafeUserMessage(appContext, R.string.onboarding_error_generic),
                )
            }
        }
    }

    // User chose to keep their own account instead of converting — matches iOS's
    // secondary "Keep My Account" action, which completes the flow as-is.
    fun keepOwnAccount() {
        // She chose not to join, so stop owing the code.
        PendingInviteStore.clear(kvStore)
        flowStore.send(OnboardingFlowIntent.Complete)
    }

    // Mirrors iOS BeHerAcceptStep's `accept()`: no pre-existing health data to clear
    // here since it either already ran in PartnerConversionWarning (existing
    // account) or there is none to clear (brand-new partner).
    fun acceptBeHerSakhiInvite() {
        val userId = authRepository.currentUserId ?: run {
            hapticManager.error()
            _acceptUiState.value = _acceptUiState.value.copy(error = appContext.getString(R.string.onboarding_error_generic))
            return
        }
        val code = navState.value.pendingInviteCode
        if (code.isBlank()) {
            hapticManager.error()
            _acceptUiState.value = _acceptUiState.value.copy(
                error = appContext.getString(R.string.onboarding_error_code_missing),
                canRetry = false,
            )
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
                if (!isStillCurrentUser(userId)) {
                    _acceptUiState.value = _acceptUiState.value.copy(isAccepting = false)
                    return@onSuccess
                }
                hapticManager.success()
                // The join is done, so the code is no longer owed. Leaving it would drag
                // the next person who opens the app on this device into someone else's
                // invitation.
                PendingInviteStore.clear(kvStore)
                _acceptUiState.value = _acceptUiState.value.copy(isAccepting = false, succeeded = true, error = null)
            }.onFailure { throwable ->
                if (!isStillCurrentUser(userId)) {
                    _acceptUiState.value = _acceptUiState.value.copy(isAccepting = false)
                    return@onFailure
                }
                hapticManager.error()
                // Never surface `throwable.message` here. It is whatever Ktor/kotlinx
                // produced -- an HTTP status line, a serialization complaint, sometimes a
                // URL -- and it went straight onto the screen of someone who has just
                // typed an invite code. iOS never does this: `BeHerAcceptStep` maps each
                // failure to a written sentence and logs only a masked code.
                //
                // The reasons come from `CareInviteException`, which SakhiCore now raises
                // off the edge function's status code, so expired / already-used / wrong
                // phone are finally distinguishable instead of collapsing into one
                // "something went wrong".
                val oneSakhiRefusal = throwable.oneSakhiRefusalMessage(appContext)
                val message = when {
                    throwable.isOfflineFailure() ->
                        appContext.getString(R.string.onboarding_error_offline)
                    oneSakhiRefusal != null -> oneSakhiRefusal
                    else -> when ((throwable as? CareInviteException)?.reason) {
                        CareInviteException.Reason.EXPIRED ->
                            appContext.getString(R.string.onboarding_error_code_expired)
                        // 404 is "not found or already processed" -- the server cannot
                        // separate a wrong code from a spent one, so iOS's wording for
                        // both is the double-check-with-your-partner line.
                        CareInviteException.Reason.NOT_FOUND ->
                            appContext.getString(R.string.onboarding_error_code_missing)
                        CareInviteException.Reason.WRONG_PHONE ->
                            appContext.getString(R.string.onboarding_error_code_wrong_phone)
                        else -> appContext.getString(R.string.onboarding_error_generic)
                    }
                }
                // A wrong or spent code will not start working on retry; iOS drops the
                // retry affordance for exactly this case (`canRetry = false` on notFound).
                val retryable = (throwable as? CareInviteException)?.reason.let {
                    it != CareInviteException.Reason.NOT_FOUND && oneSakhiRefusal == null
                }
                _acceptUiState.value = _acceptUiState.value.copy(
                    isAccepting = false,
                    error = message,
                    canRetry = retryable,
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

        // No `currentUserId` means an OFFLINE user (chose "Continue Offline" at
        // `PrivacyScreen`, never went through Phone/OTP). Mint the local-only session
        // HERE, at the very end of the flow, rather than back at the privacy step:
        // `SessionState.LocalOnlyUser` is mapped by `AccountClassifier` straight to
        // `AppRoute.Home`, so starting it any earlier fast-forwards the app to Home and
        // skips the remaining onboarding steps entirely (a regression this replaced).
        //
        // The offline id it returns is a real user id, so the save below runs normally --
        // the repositories are offline-first and route an `offline_…` id to the shared
        // Room store instead of Supabase, which is how this data now actually persists.
        // Minting a local-only session is only ever correct when she ASKED for one, which
        // the plan records as an `OfflineWarning` step. Without that check this line quietly
        // created an account whenever it ran with no session, and sign-out is exactly that
        // situation: traced on the QA emulator, sign-out went
        // `Unauthenticated -> route SignedOut` and then, 28ms later,
        // `LocalOnlyUser -> route Home`, dropping her straight back into the app she had
        // just left. It is also what left `sakhi_local_only_active=true` in prefs after a
        // perfectly normal phone sign-in. Creating an account is her decision, never a
        // side effect of a loading step finding nobody home.
        val choseOfflineAccount = navState.value.plan.contains(OnboardingFlowStep.OfflineWarning)
        val userId = authRepository.currentUserId
            ?: if (choseOfflineAccount) authRepository.startLocalOnlySession() else return
        if (_setupUiState.value.isSaving) return

        _setupUiState.value = _setupUiState.value.copy(isSaving = true, error = null)
        val health = _healthUiState.value
        val startedAtNanos = System.nanoTime()

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
                    // The confirmed Last Period date, written as an actual period log.
                    //
                    // Without this the calendar renders completely unmarked: every mark
                    // (period, predicted, fertile, ovulation, PMS) comes from
                    // `CycleInsightAdapter.calendarMarks`, which is driven purely by
                    // logged days and short-circuits on `periodLogDates.isEmpty()`.
                    // Writing only `CycleData` above left that set empty, so onboarding
                    // produced a cycle nothing could draw from.
                    //
                    // Matches iOS `OnboardingViewModel.saveHealthData()` step 3, which
                    // writes exactly one authoritative user-logged day for the confirmed
                    // date ("this becomes the only current-period anchor used by
                    // CycleDetectionEngine") and lets the engine derive the rest --
                    // hence one log here, not `periodLength` days.
                    periodLogRepository.upsert(
                        team.sakhi.models.PeriodLog(
                            // The canonical deterministic daily id, NOT a random UUID.
                            // Every other write path derives the id from
                            // (user, source, date) so the same day always resolves to
                            // one row -- and it is uppercase on purpose, matching iOS's
                            // PeriodLogObject.stableDailyId. A random id here meant a
                            // later quick log for this same date could not recognise
                            // onboarding's entry as that day's log and wrote a SECOND
                            // one, leaving the day with two conflicting flow values.
                            id = DataMigration.stablePeriodLogId(
                                userId = userId,
                                logDate = health.lastPeriodDate.toKmmLocalDate().toString(),
                            ),
                            userId = userId,
                            logDate = health.lastPeriodDate.toKmmLocalDate(),
                            periodPresent = true,
                            flowIntensity = team.sakhi.models.FlowIntensity.LIGHT,
                            loggedBy = team.sakhi.models.LogSource.USER,
                            createdByUserId = userId,
                            sourceUserId = userId,
                        )
                    ).getOrThrow()
                }
            }.onSuccess {
                if (!isStillCurrentUser(userId)) {
                    _setupUiState.value = _setupUiState.value.copy(isSaving = false)
                    return@onSuccess
                }
                val elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000L
                val remainingMs = 900L - elapsedMs
                if (remainingMs > 0L) {
                    delay(remainingMs)
                }
                _setupUiState.value = _setupUiState.value.copy(isSaving = false)
                completeOnboarding()
            }.onFailure { throwable ->
                if (!isStillCurrentUser(userId)) {
                    _setupUiState.value = _setupUiState.value.copy(isSaving = false)
                    return@onFailure
                }
                _setupUiState.value = _setupUiState.value.copy(
                    isSaving = false,
                    error = throwable.toSafeUserMessage(appContext, R.string.onboarding_error_generic),
                )
            }
        }
    }

    fun resolveOtp(
        isReturningUser: Boolean,
        pendingInviteCode: String = "",
        hasExistingOwnAccount: Boolean = isReturningUser,
    ) {
        flowStore.send(
            OnboardingFlowIntent.OtpVerified(
                isReturningUser = isReturningUser,
                pendingInviteCode = pendingInviteCode,
                hasExistingOwnAccount = hasExistingOwnAccount,
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
                errorMessage = appContext.getString(R.string.onboarding_error_sign_in_before_invite),
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

        inviteLog.i {
            "createInvitation -> relation='${partnerRelation}', hasContactName=${contactName.isNotBlank()}, " +
                "hasContactPhone=${contactPhone.isNotBlank()}, userId=${userId.takeLast(4)}"
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    careStore.createInvitation(
                        inviterName = appContext.getString(R.string.onboarding_fallback_user),
                        inviteePhone = contactPhone,
                        inviteeName = contactName,
                        relationType = RelationType.PARTNER.value,
                        permissions = _careInviteUiState.value.permissions,
                        partnerRelation = partnerRelation,
                        userId = userId,
                    )
                }
            }.onSuccess { status ->
                if (!isStillCurrentUser(userId)) {
                    _careInviteUiState.value = _careInviteUiState.value.copy(isCreatingInvite = false)
                    return@onSuccess
                }
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
                if (!isStillCurrentUser(userId)) {
                    _careInviteUiState.value = _careInviteUiState.value.copy(isCreatingInvite = false)
                    return@onFailure
                }
                // The user-facing message is deliberately generic, and nothing was recording
                // the real cause — so "Couldn't create invite" was unactionable for anyone
                // debugging it, including from a logcat capture.
                //
                // Type and first line only, never the full message: supabase-kt puts the
                // whole request dump in `message`, including the `Authorization: Bearer <jwt>`
                // header and the apikey. Same rule as `SakhiLogSave` in :feature:logging,
                // which exists because that exact leak reached logcat once.
                inviteLog.e {
                    "createInvitation FAILED: ${throwable::class.simpleName}: " +
                        throwable.message.orEmpty().substringBefore('\n').take(200)
                }
                hapticManager.error()
                _careInviteUiState.value = _careInviteUiState.value.copy(
                    isCreatingInvite = false,
                    errorMessage = throwable.toSafeUserMessage(appContext, R.string.onboarding_error_create_invite),
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
                if (!isStillCurrentUser(userId)) {
                    _careInviteUiState.value = _careInviteUiState.value.copy(isCancellingInvite = false)
                    return@onSuccess
                }
                hapticManager.success()
                _careInviteUiState.value = _careInviteUiState.value.copy(
                    isCancellingInvite = false,
                    invitationId = "",
                    inviteCode = "",
                    pendingInvitation = null,
                    cancelMessage = appContext.getString(R.string.onboarding_info_invite_closed),
                )
                if (closeFlowOnSuccess) {
                    flowStore.send(OnboardingFlowIntent.Complete)
                }
            }.onFailure { throwable ->
                if (!isStillCurrentUser(userId)) {
                    _careInviteUiState.value = _careInviteUiState.value.copy(isCancellingInvite = false)
                    return@onFailure
                }
                hapticManager.error()
                _careInviteUiState.value = _careInviteUiState.value.copy(
                    isCancellingInvite = false,
                    errorMessage = throwable.toSafeUserMessage(appContext, R.string.onboarding_error_cancel_request),
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
            failDataSourceImport(appContext.getString(R.string.onboarding_error_health_connect_permission_denied))
            refreshDataSourceCapabilities()
        }
    }

    fun handleUnavailableHealthConnectSelection() {
        val state = _dataSourceUiState.value
        val message = when (state.availability) {
            HealthConnectAvailability.NotInstalled -> appContext.getString(R.string.onboarding_error_health_connect_unavailable)
            HealthConnectAvailability.NotSupported -> appContext.getString(R.string.onboarding_error_health_connect_unsupported)
            // Health Connect itself is fine, but nothing on this phone feeds it, so
            // there is no source to import from. Availability takes precedence: if
            // Health Connect is missing entirely that is the more fundamental thing to
            // say, and only once it is present does "no health app" become the reason.
            HealthConnectAvailability.Available -> if (state.sourceApps.isEmpty()) {
                appContext.getString(R.string.onboarding_error_health_connect_no_apps)
            } else {
                appContext.getString(R.string.onboarding_error_health_connect_permission_denied)
            }
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
                            result.failureMessage ?: appContext.getString(R.string.onboarding_error_health_connect_missing_details),
                        )
                    }
                }
                .onFailure { throwable ->
                    hapticManager.error()
                    failDataSourceImport(
                        throwable.toSafeUserMessage(appContext, R.string.onboarding_error_health_connect_permission_denied),
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
            if (!ValidationRules.isValidDateOfBirth(state.dateOfBirth.toKmmLocalDate())) {
                appContext.getString(R.string.onboarding_validation_invalid_dob)
            } else null
        OnboardingFlowStep.Height ->
            if (!ValidationRules.isValidHeightCm(state.heightCm)) {
                appContext.getString(R.string.onboarding_validation_invalid_height)
            } else null
        OnboardingFlowStep.Weight ->
            if (!ValidationRules.isValidWeightKg(state.weightKg)) {
                appContext.getString(R.string.onboarding_validation_invalid_weight)
            } else null
        OnboardingFlowStep.LastPeriod ->
            if (!ValidationRules.isValidLogDate(state.lastPeriodDate.toKmmLocalDate())) {
                appContext.getString(R.string.onboarding_validation_invalid_date)
            } else null
        OnboardingFlowStep.CycleLength ->
            if (!ValidationRules.isValidCycleLength(state.cycleLength)) {
                appContext.getString(R.string.onboarding_validation_invalid_cycle_length)
            } else null
        else -> null
    }

    private fun isStillCurrentUser(userId: String): Boolean = authRepository.currentUserId == userId
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
    /**
     * Installed apps that actually integrate with Health Connect. Empty means there is
     * nothing on this device to import FROM, so offering the import option would send
     * the user into Health Connect's setup for no reason.
     */
    val sourceApps: List<HealthConnectSourceApp> = emptyList(),
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
    canGenerateReports = true,
)

/**
 * True when a failure is a lost/absent network rather than a server rejection.
 *
 * `IOException` covers the whole family Ktor surfaces on Android for this —
 * `UnknownHostException`, `ConnectException`, `SocketTimeoutException` — so matching the
 * base type is deliberate, not lazy. The cause chain is walked because Ktor and
 * kotlinx-serialization both wrap the original throwable.
 */
private fun Throwable.isOfflineFailure(): Boolean {
    var t: Throwable? = this
    var depth = 0
    while (t != null && depth < 8) {
        if (t is java.io.IOException) return true
        t = t.cause
        depth++
    }
    return false
}
