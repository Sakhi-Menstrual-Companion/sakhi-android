package team.sakhi.android.feature.recommendations

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.Runs
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import team.sakhi.date.DateConverter
import team.sakhi.models.CarePartnership
import team.sakhi.models.CycleData
import team.sakhi.models.CyclePhase
import team.sakhi.models.HealthCondition
import team.sakhi.models.UserCareRole
import team.sakhi.models.UserProfile
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.DailyInsight
import team.sakhi.repositories.FoodItem
import team.sakhi.repositories.FoodNutrition
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.repositories.PhaseRecommendations
import team.sakhi.repositories.RecommendationInsightService
import team.sakhi.repositories.RecommendationRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.session.SessionPermissions

/**
 * State-machine test for `RecommendationsViewModel`. `SessionManager`/`CycleDataRepository`/
 * `UserProfileRepository`/`RecommendationRepository`/`PeriodLogRepository`/
 * `RecommendationInsightService` are concrete, non-open KMM classes (same situation as
 * `HomeViewModelTest`/`CalendarViewModelTest`), so this uses mockk for those boundaries.
 * `CycleMath.currentPhase` runs for real through a real `CycleData` fixture -- a real
 * regression in that shared rule would fail this test too, not just a stubbed value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun permissions(
        canViewPredictions: Boolean = false,
        canViewCycleHistory: Boolean = false,
        canViewSymptoms: Boolean = false,
    ) = SessionPermissions(
        canViewPeriodDates = true,
        canLogPeriod = true,
        canViewSymptoms = canViewSymptoms,
        canViewMoods = true,
        canViewMedications = true,
        canViewPredictions = canViewPredictions,
        canViewCycleHistory = canViewCycleHistory,
        canViewDailyLogs = true,
        canViewOvulationTests = true,
        canViewTemperature = true,
        canViewWeight = true,
        canViewNotes = true,
        canViewDischarge = true,
        canViewSexualActivity = true,
    )

    private fun sessionContext(
        userId: String = "user-1",
        targetUserId: String = "user-1",
        canViewPredictions: Boolean = false,
        canViewCycleHistory: Boolean = false,
        canViewSymptoms: Boolean = false,
        activePartnership: CarePartnership? = null,
    ): SessionContext = SessionContext(
        userId = userId,
        userName = "Test User",
        activeRole = if (userId == targetUserId) UserCareRole.PRIMARY_USER else UserCareRole.PARTNER,
        targetUserId = targetUserId,
        permissions = permissions(canViewPredictions, canViewCycleHistory, canViewSymptoms),
        activePartnership = activePartnership,
    )

    // daysAgo=2/periodLength=5/cycleLength=28 keeps "today" unambiguously inside the
    // real MENSTRUAL window regardless of AppConfig's ovulation-window constants.
    private fun menstrualCycle(userId: String = "user-1"): CycleData = CycleData(
        id = "cycle-1",
        userId = userId,
        cycleStartDate = DateConverter.addDays(DateConverter.today(), -2),
        periodStartDate = DateConverter.addDays(DateConverter.today(), -2),
        periodLength = 5,
        cycleLength = 28,
    )

    private fun profile(
        userId: String = "user-1",
        healthConditions: List<HealthCondition> = emptyList(),
    ) = UserProfile(
        id = userId,
        name = "Test User",
        email = "test@example.com",
        phone = "9990421555",
        healthConditions = healthConditions,
    )

    private fun newViewModel(
        sessionManager: SessionManager,
        cycleDataRepository: CycleDataRepository = mockk(),
        userProfileRepository: UserProfileRepository = mockk<UserProfileRepository>().also {
            // `get` collides with `MockKMatcherScope`'s own dynamic-call `get` operator when
            // called implicitly inside a `coEvery { ... }` block, so it must be referenced on
            // the mock explicitly here rather than via the `mockk<T> { coEvery { get(...) } }`
            // builder-block shorthand used elsewhere in this file.
            coEvery { it.get(any()) } returns Result.success(null)
        },
        recommendationRepository: RecommendationRepository = mockk {
            every { getCuratedRecommendations(any()) } returns PhaseRecommendations(
                CyclePhase.UNKNOWN,
                emptyList(),
                emptyList(),
                emptyList(),
            )
        },
        periodLogRepository: PeriodLogRepository = mockk {
            coEvery { getForDate(any(), any()) } returns Result.success(null)
        },
        recommendationInsightService: RecommendationInsightService = mockk {
            coEvery { getDailyInsight(any(), any(), any(), any(), any()) } returns
                Result.success(DailyInsight("key", "default self insight"))
            coEvery { getPartnerInsight(any(), any(), any(), any()) } returns
                Result.success(DailyInsight("key", "default partner insight"))
            every { clearCache() } just Runs
        },
        appContext: Context = mockk(),
    ) = RecommendationsViewModel(
        sessionManager,
        cycleDataRepository,
        userProfileRepository,
        recommendationRepository,
        periodLogRepository,
        recommendationInsightService,
        appContext,
    )

    @Test
    fun `no session resets to the default empty state`() = runTest {
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val viewModel = newViewModel(sessionManager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CyclePhase.UNKNOWN, state.phase)
        assertFalse(state.isLoading)
        assertFalse(state.canViewPhaseRecommendations)
        assertTrue(state.eatMoreFoods.isEmpty())
    }

    @Test
    fun `own data session sees real phase recommendations and a real personalized daily AI insight`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.success(menstrualCycle())
        }
        val menstrualRecs = PhaseRecommendations(
            phase = CyclePhase.MENSTRUAL,
            eatMore = listOf(FoodItem("Dark chocolate", "snacks", "menstrual")),
            avoid = listOf(FoodItem("Caffeine", "beverages", "menstrual")),
            tips = listOf("Rest more than usual"),
        )
        val recommendationRepository = mockk<RecommendationRepository> {
            every { getCuratedRecommendations(CyclePhase.MENSTRUAL) } returns menstrualRecs
            coEvery { enrichWithUSDA(any()) } returns Result.success(null)
        }
        val periodLogRepository = mockk<PeriodLogRepository> {
            coEvery { getForDate(any(), any()) } returns Result.success(null)
        }
        val recommendationInsightService = mockk<RecommendationInsightService> {
            coEvery {
                getDailyInsight(
                    userId = "user-1",
                    dateString = any(),
                    phase = CyclePhase.MENSTRUAL,
                    symptoms = emptyList(),
                    conditions = emptyList(),
                )
            } returns Result.success(DailyInsight("key", "Rest today, your body is working hard."))
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            recommendationRepository = recommendationRepository,
            periodLogRepository = periodLogRepository,
            recommendationInsightService = recommendationInsightService,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CyclePhase.MENSTRUAL, state.phase)
        assertTrue(state.canViewPhaseRecommendations)
        assertEquals(1, state.eatMoreFoods.size)
        assertEquals("Dark chocolate", state.eatMoreFoods.first().name)
        assertEquals(listOf("Rest more than usual"), state.phaseTips)
        assertEquals("Rest today, your body is working hard.", state.aiInsight)
        coVerify(exactly = 0) { recommendationInsightService.getPartnerInsight(any(), any(), any(), any()) }
    }

    @Test
    fun `partner view resolves the real personalized partner insight via the active partnership`() = runTest {
        val partnership = CarePartnership(
            id = "partnership-1",
            userId = "primary-1",
            partnerId = "partner-1",
            partnerName = "Partner",
        )
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            canViewPredictions = true,
            activePartnership = partnership,
        )
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("primary-1") } returns Result.success(menstrualCycle(userId = "primary-1"))
        }
        val userProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get("primary-1") } returns Result.success(profile(userId = "primary-1"))
        }
        val recommendationRepository = mockk<RecommendationRepository> {
            every { getCuratedRecommendations(CyclePhase.MENSTRUAL) } returns PhaseRecommendations(
                CyclePhase.MENSTRUAL,
                emptyList(),
                emptyList(),
                emptyList(),
            )
            coEvery { enrichWithUSDA(any()) } returns Result.success(null)
        }
        val recommendationInsightService = mockk<RecommendationInsightService> {
            coEvery {
                getPartnerInsight(
                    partnershipId = "partnership-1",
                    dateString = any(),
                    phase = CyclePhase.MENSTRUAL,
                    partnerName = "Test User",
                )
            } returns Result.success(DailyInsight("key", "Bring her a heating pad today.", forPartner = true))
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            userProfileRepository = userProfileRepository,
            recommendationRepository = recommendationRepository,
            recommendationInsightService = recommendationInsightService,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Bring her a heating pad today.", state.aiInsight)
        coVerify(exactly = 0) { recommendationInsightService.getDailyInsight(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `partner with no phase or symptom permissions sees no recommendations or insight`() = runTest {
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            canViewPredictions = false,
            canViewCycleHistory = false,
            canViewSymptoms = false,
        )
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("primary-1") } returns Result.success(menstrualCycle(userId = "primary-1"))
        }
        val recommendationRepository = mockk<RecommendationRepository> {
            // The real implementation still fetches curated data for CyclePhase.UNKNOWN
            // even when the caller can't see it -- documenting that real, if unintuitive,
            // behavior rather than assuming the repository is untouched.
            every { getCuratedRecommendations(CyclePhase.UNKNOWN) } returns PhaseRecommendations(
                CyclePhase.UNKNOWN,
                listOf(FoodItem("Should not surface", "x", "x")),
                emptyList(),
                listOf("Should not surface"),
            )
        }
        val userProfileRepository = mockk<UserProfileRepository>()
        val periodLogRepository = mockk<PeriodLogRepository>()
        val recommendationInsightService = mockk<RecommendationInsightService>()
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            userProfileRepository = userProfileRepository,
            recommendationRepository = recommendationRepository,
            periodLogRepository = periodLogRepository,
            recommendationInsightService = recommendationInsightService,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.canViewPhaseRecommendations)
        assertFalse(state.canViewConditionRecommendations)
        assertTrue(state.eatMoreFoods.isEmpty())
        assertTrue(state.avoidFoods.isEmpty())
        assertTrue(state.phaseTips.isEmpty())
        assertNull(state.aiInsight)
        assertTrue(state.conditionTips.isEmpty())
        coVerify(exactly = 0) { cycleDataRepository.getLatest(any()) }
        coVerify(exactly = 0) { userProfileRepository.get(any()) }
        coVerify(exactly = 0) { periodLogRepository.getForDate(any(), any()) }
        coVerify(exactly = 0) { recommendationInsightService.getDailyInsight(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { recommendationInsightService.getPartnerInsight(any(), any(), any(), any()) }
    }

    @Test
    fun `phase and condition visibility are independent permission axes`() = runTest {
        // Real product rule, mirroring CalendarViewModel's own independent-axes case:
        // VIEW_PREDICTIONS grants phase recommendations without also granting
        // condition tips, which require VIEW_SYMPTOMS specifically.
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            canViewPredictions = true,
            canViewCycleHistory = false,
            canViewSymptoms = false,
        )
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("primary-1") } returns Result.success(menstrualCycle(userId = "primary-1"))
        }
        val userProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get("primary-1") } returns Result.success(
                profile(userId = "primary-1", healthConditions = listOf(HealthCondition.PCOS)),
            )
        }
        val recommendationRepository = mockk<RecommendationRepository> {
            every { getCuratedRecommendations(CyclePhase.MENSTRUAL) } returns PhaseRecommendations(
                CyclePhase.MENSTRUAL,
                listOf(FoodItem("Dark chocolate", "snacks", "menstrual")),
                emptyList(),
                listOf("Rest more"),
            )
            coEvery { enrichWithUSDA(any()) } returns Result.success(null)
            every { getConditionTips(HealthCondition.PCOS) } returns listOf("Low-glycemic foods help")
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            userProfileRepository = userProfileRepository,
            recommendationRepository = recommendationRepository,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.canViewPhaseRecommendations)
        assertFalse(state.canViewConditionRecommendations)
        assertEquals(1, state.eatMoreFoods.size)
        assertTrue(state.conditionTips.isEmpty())
    }

    @Test
    fun `cycle load failure with a real message surfaces that message and an unknown phase`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.failure(RuntimeException("network down"))
        }
        val viewModel = newViewModel(sessionManager, cycleDataRepository = cycleDataRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("network down", state.error)
        assertEquals(CyclePhase.UNKNOWN, state.phase)
        assertFalse(state.isLoading)
    }

    @Test
    fun `a stale cycle response for an already-abandoned target is discarded`() = runTest {
        val sessionA = sessionContext(userId = "user-1", targetUserId = "user-1")
        val sessionB = sessionContext(userId = "user-1", targetUserId = "partner-1", canViewPredictions = true)
        val sessionFlow = MutableStateFlow(sessionA)
        val currentSlot = arrayOf(sessionA)
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } answers { currentSlot[0] }
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } coAnswers { awaitCancellation() }
            coEvery { getLatest("partner-1") } returns Result.success(menstrualCycle(userId = "partner-1"))
        }
        val recommendationRepository = mockk<RecommendationRepository> {
            every { getCuratedRecommendations(any()) } returns PhaseRecommendations(
                CyclePhase.MENSTRUAL,
                listOf(FoodItem("Dark chocolate", "snacks", "menstrual")),
                emptyList(),
                emptyList(),
            )
            coEvery { enrichWithUSDA(any()) } returns Result.success(null)
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            recommendationRepository = recommendationRepository,
        )
        advanceUntilIdle()

        currentSlot[0] = sessionB
        sessionFlow.value = sessionB
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CyclePhase.MENSTRUAL, state.phase)
        assertEquals(1, state.eatMoreFoods.size)
    }

    @Test
    fun `foods are enriched with USDA nutrition concurrently, not sequentially`() = runTest {
        // Same technique as SanityContentViewModelTest's concurrent-fetch proof: gate
        // every enrichWithUSDA call on the same uncompleted CompletableDeferred, then
        // confirm all of them were already invoked before any is allowed to finish. A
        // genuinely sequential enrichFoods() implementation would call the second food
        // only after the first's suspend point resolves, which would fail this test.
        val gate = CompletableDeferred<Unit>()
        val calledNames = mutableListOf<String>()
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.success(menstrualCycle())
        }
        val recommendationRepository = mockk<RecommendationRepository> {
            every { getCuratedRecommendations(CyclePhase.MENSTRUAL) } returns PhaseRecommendations(
                CyclePhase.MENSTRUAL,
                listOf(FoodItem("Dark chocolate", "snacks", "menstrual"), FoodItem("Ginger tea", "beverages", "menstrual")),
                emptyList(),
                emptyList(),
            )
            coEvery { enrichWithUSDA(any()) } coAnswers {
                synchronized(calledNames) { calledNames.add(firstArg()) }
                gate.await()
                Result.success(null)
            }
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            recommendationRepository = recommendationRepository,
        )
        advanceUntilIdle()

        assertEquals(setOf("Dark chocolate", "Ginger tea"), calledNames.toSet())
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.eatMoreFoods.size)
    }

    @Test
    fun `nutrition label prefers protein, then carbs, then calories, resolved through the real string resources`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.success(menstrualCycle())
        }
        val recommendationRepository = mockk<RecommendationRepository> {
            every { getCuratedRecommendations(CyclePhase.MENSTRUAL) } returns PhaseRecommendations(
                CyclePhase.MENSTRUAL,
                listOf(
                    FoodItem("Salmon", "protein", "menstrual"),
                    FoodItem("Bananas", "fruits", "menstrual"),
                ),
                emptyList(),
                emptyList(),
            )
            coEvery { enrichWithUSDA("Salmon") } returns Result.success(
                FoodNutrition("Salmon", calories = 200.0, proteinG = 22.0, carbsG = 0.0),
            )
            coEvery { enrichWithUSDA("Bananas") } returns Result.success(
                FoodNutrition("Bananas", calories = 105.0, proteinG = null, carbsG = 27.0),
            )
        }
        val proteinArg = slot<String>()
        val carbsArg = slot<String>()
        val appContext = mockk<Context> {
            every { getString(R.string.recommendations_nutrition_protein, capture(proteinArg)) } answers {
                "Protein ${proteinArg.captured} g"
            }
            every { getString(R.string.recommendations_nutrition_carbs, capture(carbsArg)) } answers {
                "Carbs ${carbsArg.captured} g"
            }
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            recommendationRepository = recommendationRepository,
            appContext = appContext,
        )

        advanceUntilIdle()

        val foods = viewModel.uiState.value.eatMoreFoods.associateBy { it.name }
        assertEquals("Protein 22.0 g", foods.getValue("Salmon").nutritionLabel)
        assertEquals("Carbs 27.0 g", foods.getValue("Bananas").nutritionLabel)
    }

    @Test
    fun `partner view with no active partnership yields no AI insight and never calls getPartnerInsight`() = runTest {
        // `session.activePartnership?.id?.let { ... }` silently short-circuits to
        // null when there's no active partnership on the session -- a real branch
        // every existing partner-view test avoided by always setting one.
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            canViewPredictions = true,
            activePartnership = null,
        )
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("primary-1") } returns Result.success(menstrualCycle(userId = "primary-1"))
        }
        val recommendationRepository = mockk<RecommendationRepository> {
            every { getCuratedRecommendations(CyclePhase.MENSTRUAL) } returns PhaseRecommendations(
                CyclePhase.MENSTRUAL,
                emptyList(),
                emptyList(),
                emptyList(),
            )
        }
        val recommendationInsightService = mockk<RecommendationInsightService>()
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            recommendationRepository = recommendationRepository,
            recommendationInsightService = recommendationInsightService,
        )

        advanceUntilIdle()

        assertNull(viewModel.uiState.value.aiInsight)
        coVerify(exactly = 0) { recommendationInsightService.getPartnerInsight(any(), any(), any(), any()) }
    }

    @Test
    fun `phase recommendations permission without any real cycle data yet still yields no AI insight`() = runTest {
        // `canViewPhaseRecommendations` and `phase != CyclePhase.UNKNOWN` are two
        // independent gates on the AI-insight branch -- a user who *can* see phase
        // recommendations but hasn't logged enough for a real phase yet (no cycle
        // data at all) must not get a Claude call for an undefined phase.
        val testSession = sessionContext(canViewPredictions = true)
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.success(null)
        }
        val recommendationInsightService = mockk<RecommendationInsightService>()
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            recommendationInsightService = recommendationInsightService,
        )

        advanceUntilIdle()

        assertEquals(CyclePhase.UNKNOWN, viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.aiInsight)
        coVerify(exactly = 0) { recommendationInsightService.getDailyInsight(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getDailyInsight failure gracefully yields no AI insight instead of surfacing an error`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.success(menstrualCycle())
        }
        val recommendationInsightService = mockk<RecommendationInsightService> {
            coEvery {
                getDailyInsight(any(), any(), any(), any(), any())
            } returns Result.failure(RuntimeException("edge function down"))
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            recommendationInsightService = recommendationInsightService,
        )

        advanceUntilIdle()

        assertNull(viewModel.uiState.value.aiInsight)
        // The insight failure is swallowed independently -- the rest of the
        // card (phase/curated data) must not be reported as a real load error.
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `enrichFoods swallows a real thrown USDA exception and still returns the food with no nutrition label`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.success(menstrualCycle())
        }
        val recommendationRepository = mockk<RecommendationRepository> {
            every { getCuratedRecommendations(CyclePhase.MENSTRUAL) } returns PhaseRecommendations(
                CyclePhase.MENSTRUAL,
                listOf(FoodItem("Dark chocolate", "snacks", "menstrual")),
                emptyList(),
                emptyList(),
            )
            coEvery { enrichWithUSDA("Dark chocolate") } throws RuntimeException("USDA API unreachable")
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            recommendationRepository = recommendationRepository,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals(1, state.eatMoreFoods.size)
        assertEquals("Dark chocolate", state.eatMoreFoods.first().name)
        assertNull(state.eatMoreFoods.first().nutritionLabel)
    }

    @Test
    fun `conditionTips dedupes real overlapping tips across multiple health conditions`() = runTest {
        val testSession = sessionContext(canViewSymptoms = true)
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.success(menstrualCycle())
        }
        val userProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get("user-1") } returns Result.success(
                profile(healthConditions = listOf(HealthCondition.PCOS, HealthCondition.DIABETES)),
            )
        }
        val recommendationRepository = mockk<RecommendationRepository> {
            every { getCuratedRecommendations(CyclePhase.MENSTRUAL) } returns PhaseRecommendations(
                CyclePhase.MENSTRUAL,
                emptyList(),
                emptyList(),
                emptyList(),
            )
            every { getConditionTips(HealthCondition.PCOS) } returns listOf("Shared tip", "PCOS-only tip")
            every { getConditionTips(HealthCondition.DIABETES) } returns listOf("Shared tip", "Diabetes-only tip")
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            userProfileRepository = userProfileRepository,
            recommendationRepository = recommendationRepository,
        )

        advanceUntilIdle()

        assertEquals(
            listOf("Shared tip", "PCOS-only tip", "Diabetes-only tip"),
            viewModel.uiState.value.conditionTips,
        )
    }

    @Test
    fun `refreshInsight clears the cache and replaces the AI insight with a fresh one, matching iOS's real refresh button`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("user-1") } returns Result.success(menstrualCycle())
        }
        val recommendationInsightService = mockk<RecommendationInsightService> {
            coEvery {
                getDailyInsight(any(), any(), any(), any(), any())
            } returns Result.success(DailyInsight("key", "stale insight"))
            every { clearCache() } just Runs
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            recommendationInsightService = recommendationInsightService,
        )
        advanceUntilIdle()
        assertEquals("stale insight", viewModel.uiState.value.aiInsight)

        coEvery {
            recommendationInsightService.getDailyInsight(any(), any(), any(), any(), any())
        } returns Result.success(DailyInsight("key", "fresh insight"))

        viewModel.refreshInsight()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("fresh insight", state.aiInsight)
        assertFalse(state.isRefreshingInsight)
        verify(exactly = 1) { recommendationInsightService.clearCache() }
    }

    @Test
    fun `refreshInsight is a no-op when the session cannot view phase recommendations`() = runTest {
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            canViewPredictions = false,
            canViewCycleHistory = false,
        )
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("primary-1") } returns Result.success(menstrualCycle(userId = "primary-1"))
        }
        val recommendationInsightService = mockk<RecommendationInsightService>()
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            recommendationInsightService = recommendationInsightService,
        )
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canViewPhaseRecommendations)

        viewModel.refreshInsight()
        advanceUntilIdle()

        verify(exactly = 0) { recommendationInsightService.clearCache() }
    }

    @Test
    fun `refreshInsight for a partner view fetches through getPartnerInsight, not getDailyInsight`() = runTest {
        val partnership = CarePartnership(
            id = "partnership-1",
            userId = "primary-1",
            partnerId = "partner-1",
            partnerName = "Partner",
        )
        val testSession = sessionContext(
            userId = "partner-1",
            targetUserId = "primary-1",
            canViewPredictions = true,
            activePartnership = partnership,
        )
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getLatest("primary-1") } returns Result.success(menstrualCycle(userId = "primary-1"))
        }
        val userProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get("primary-1") } returns Result.success(profile(userId = "primary-1"))
        }
        val periodLogRepository = mockk<PeriodLogRepository>()
        val recommendationInsightService = mockk<RecommendationInsightService> {
            coEvery {
                getPartnerInsight(any(), any(), any(), any())
            } returns Result.success(DailyInsight("key", "refreshed partner tip", forPartner = true))
            every { clearCache() } just Runs
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            userProfileRepository = userProfileRepository,
            periodLogRepository = periodLogRepository,
            recommendationInsightService = recommendationInsightService,
        )
        advanceUntilIdle()

        viewModel.refreshInsight()
        advanceUntilIdle()

        assertEquals("refreshed partner tip", viewModel.uiState.value.aiInsight)
        coVerify(exactly = 0) { periodLogRepository.getForDate(any(), any()) }
        coVerify(exactly = 0) { recommendationInsightService.getDailyInsight(any(), any(), any(), any(), any()) }
    }
}
