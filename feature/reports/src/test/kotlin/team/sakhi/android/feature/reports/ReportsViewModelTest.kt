package team.sakhi.android.feature.reports

import android.content.Context
import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.date.DateConverter
import team.sakhi.models.CarePartnership
import team.sakhi.models.CycleData
import team.sakhi.models.FlowIntensity
import team.sakhi.models.PeriodLog
import team.sakhi.models.UserProfile
import team.sakhi.models.UserCareRole
import team.sakhi.report.ReportDataBuilder
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.PeriodLogRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.session.SessionPermissions

/**
 * State-machine test for `ReportsViewModel`. `SessionManager`/`CycleDataRepository`/
 * `PeriodLogRepository`/`AndroidHapticManager`/`ReportPdfExporter` are concrete,
 * non-open KMM/platform classes (same situation as `HomeViewModelTest`), so this
 * uses mockk for those boundaries. `generate()`'s real report is built through the
 * actual shared `ReportDataBuilder.build` from real `CycleData`/`PeriodLog` fixtures
 * -- cross-checked against calling that same real function directly -- so a real
 * regression in the shared report/statistics pipeline would fail this test too, not
 * just a stubbed value. `ReportDateRangePreset.dateRange()` is also real, unstubbed
 * date math.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sessionContext(
        userId: String = "user-1",
        targetUserId: String = userId,
        activePartnership: CarePartnership? = null,
    ) = SessionContext(
        userId = userId,
        userName = "Test User",
        activeRole = if (userId == targetUserId) UserCareRole.PRIMARY_USER else UserCareRole.PARTNER,
        targetUserId = targetUserId,
        permissions = SessionPermissions.primaryUser,
        activePartnership = activePartnership,
    )

    private fun completedCycle(userId: String = "user-1", daysAgo: Int = 60): CycleData = CycleData(
        id = "cycle-1",
        userId = userId,
        cycleStartDate = DateConverter.addDays(DateConverter.today(), -daysAgo),
        periodStartDate = DateConverter.addDays(DateConverter.today(), -daysAgo),
        periodEndDate = DateConverter.addDays(DateConverter.today(), -daysAgo + 5),
        cycleLength = 28,
        periodLength = 5,
        isComplete = true,
    )

    private fun periodLog(userId: String = "user-1", daysAgo: Int = 5): PeriodLog = PeriodLog(
        id = "log-1",
        userId = userId,
        logDate = DateConverter.addDays(DateConverter.today(), -daysAgo),
        periodPresent = true,
        flowIntensity = FlowIntensity.MEDIUM,
        createdByUserId = userId,
        sourceUserId = userId,
        symptoms = listOf("cramps"),
        moods = listOf("irritated"),
    )

    private fun newViewModel(
        sessionManager: SessionManager,
        cycleDataRepository: CycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll(any()) } returns Result.success(emptyList())
        },
        periodLogRepository: PeriodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(emptyList())
        },
        userProfileRepository: UserProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get(any()) } returns Result.success(null)
        },
        reportPdfExporter: ReportPdfExporter = mockk<ReportPdfExporter>().also {
            every { it.export(any(), any()) } returns File("/tmp/SakhiReport_default.pdf")
            every { it.buildShareUri(any()) } returns mockk()
        },
        hapticManager: AndroidHapticManager = mockk(relaxed = true),
        appContext: Context = mockk {
            every { getString(R.string.reports_current_account_error) } returns "No current account"
            every { getString(R.string.reports_load_failed) } returns "Failed to load report data"
            every { getString(R.string.reports_export_before_preview) } returns "Generate a report before exporting"
            every { getString(R.string.reports_export_failed) } returns "Couldn't export PDF"
            every { getString(R.string.reports_subject_default) } returns "Partner"
            every { getString(R.string.reports_permission_denied_error) } returns "No permission to generate reports"
        },
    ) = ReportsViewModel(
        sessionManager,
        cycleDataRepository,
        periodLogRepository,
        userProfileRepository,
        reportPdfExporter,
        hapticManager,
        appContext,
    )

    // `generate()`'s PDF-preparation step uses `withContext(Dispatchers.IO)`, not
    // the virtual test scheduler, so `advanceUntilIdle()` alone can return before that
    // work actually finishes -- it only drains work scheduled on `Dispatchers.Main`
    // (mapped to `testDispatcher` here). A blocking `Thread.sleep` loop doesn't fix this
    // either: `runTest` executes the test body on the same real thread the test
    // dispatcher itself pumps from, so blocking that thread also blocks the eventual
    // Main-dispatcher resumption once the real IO work finishes -- a self-deadlock.
    // Interleaving a real, off-scheduler `delay` (via `Dispatchers.Default`) with
    // `advanceUntilIdle()` lets the IO work actually progress *and* lets the test
    // dispatcher drain the resumption once it lands, without touching production code.
    private suspend fun TestScope.awaitUiState(
        viewModel: ReportsViewModel,
        timeoutMs: Long = 2_000,
        predicate: (ReportsUiState) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate(viewModel.uiState.value) && System.currentTimeMillis() < deadline) {
            withContext(Dispatchers.Default) { delay(5) }
            advanceUntilIdle()
        }
    }

    @Test
    fun `selectPreset applies the real date range math and resets phase and error`() = runTest {
        val sessionManager = mockk<SessionManager> { every { current } returns null }
        val viewModel = newViewModel(sessionManager)

        viewModel.selectPreset(ReportDateRangePreset.OneYear)

        val expected = ReportDateRangePreset.OneYear.dateRange()
        val config = viewModel.uiState.value.config
        assertEquals(ReportDateRangePreset.OneYear, config.preset)
        assertEquals(expected.first, config.startDate)
        assertEquals(expected.second, config.endDate)
        assertEquals(ReportsPhase.Config, viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `toggleSection adds then removes the same section and fires a haptic each time`() = runTest {
        val sessionManager = mockk<SessionManager> { every { current } returns null }
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(sessionManager, hapticManager = hapticManager)
        val allSections = viewModel.uiState.value.config.sections
        assertTrue(ReportSection.Insights in allSections)

        viewModel.toggleSection(ReportSection.Insights)
        assertFalse(ReportSection.Insights in viewModel.uiState.value.config.sections)

        viewModel.toggleSection(ReportSection.Insights)
        assertTrue(ReportSection.Insights in viewModel.uiState.value.config.sections)

        verify(exactly = 2) { hapticManager.selection() }
    }

    @Test
    fun `generate with no current session sets Error phase with the real account-error message`() = runTest {
        val sessionManager = mockk<SessionManager> { every { current } returns null }
        val viewModel = newViewModel(sessionManager)

        viewModel.generate()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReportsPhase.Error, state.phase)
        assertEquals("No current account", state.errorMessage)
    }

    @Test
    fun `generate builds a real report through the actual ReportDataBuilder`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> { every { current } returns testSession }
        val cycles = listOf(completedCycle())
        val logs = listOf(periodLog())
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("user-1") } returns Result.success(cycles)
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(logs)
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
        )
        val configBefore = viewModel.uiState.value.config

        viewModel.generate()
        awaitUiState(viewModel) { it.phase == ReportsPhase.Preview }

        val expectedReport = ReportDataBuilder.build(
            userId = "user-1",
            cycles = cycles,
            logs = logs,
            from = configBefore.startDate,
            to = configBefore.endDate,
        )
        val state = viewModel.uiState.value
        assertEquals(ReportsPhase.Preview, state.phase)
        requireNotNull(state.document)
        assertEquals(expectedReport.cyclesAnalyzed, state.document.report.cyclesAnalyzed)
        assertEquals(expectedReport.topSymptoms, state.document.report.topSymptoms)
        assertEquals(expectedReport.flowTimeline, state.document.report.flowTimeline)
        assertTrue(state.document.report.topSymptoms.isNotEmpty())
    }

    @Test
    fun `generate filters cycle stats to the same two-year lookback window iOS uses`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> { every { current } returns testSession }
        val ancientCycle = completedCycle(daysAgo = 1_300).copy(
            id = "cycle-old",
            cycleLength = 41,
        )
        val recentCycle = completedCycle(daysAgo = 90).copy(
            id = "cycle-recent",
            cycleLength = 27,
        )
        val cycles = listOf(ancientCycle, recentCycle)
        val logs = listOf(periodLog())
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("user-1") } returns Result.success(cycles)
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(logs)
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
        )
        val config = viewModel.uiState.value.config

        viewModel.generate()
        awaitUiState(viewModel) { it.phase == ReportsPhase.Preview }

        val expectedReport = ReportDataBuilder.build(
            userId = "user-1",
            cycles = listOf(recentCycle),
            logs = logs,
            from = config.startDate,
            to = config.endDate,
        )
        val state = viewModel.uiState.value
        requireNotNull(state.document)
        assertEquals(expectedReport.cyclesAnalyzed, state.document.report.cyclesAnalyzed)
        assertEquals(expectedReport.averageCycleLength, state.document.report.averageCycleLength, 0.0)
        assertEquals(expectedReport.longestCycleDays, state.document.report.longestCycleDays)
    }

    @Test
    fun `generate uses targetUserId instead of the signed-in viewer when reviewing partner data`() = runTest {
        val partnerSession = sessionContext(userId = "viewer-1", targetUserId = "partner-1")
        val sessionManager = mockk<SessionManager> { every { current } returns partnerSession }
        val cycles = listOf(completedCycle(userId = "partner-1"))
        val logs = listOf(periodLog(userId = "partner-1"))
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("partner-1") } returns Result.success(cycles)
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange("partner-1", any(), any()) } returns Result.success(logs)
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
        )

        viewModel.generate()
        awaitUiState(viewModel) { it.phase == ReportsPhase.Preview }

        val state = viewModel.uiState.value
        assertEquals(ReportsPhase.Preview, state.phase)
        assertTrue(state.document != null)
        coVerify(exactly = 1) { cycleDataRepository.getAll("partner-1") }
        coVerify(exactly = 1) { periodLogRepository.getForDateRange("partner-1", any(), any()) }
    }

    @Test
    fun `generate blocks a partner without the GENERATE_REPORTS permission granted`() = runTest {
        // Real security fix (2026-07-15, per Karan's explicit direction): partner-mode
        // report generation is legitimate, but only when the primary user has
        // explicitly granted this specific permission -- same authorization model as
        // every granular Logging view permission. Before this fix there was NO gate
        // at all here: ANY connected partner could generate/export a full report
        // regardless of what was actually granted. The sibling test above
        // ("...reviewing partner data") uses `sessionContext()`'s hardcoded
        // `SessionPermissions.primaryUser` for every session including partners --
        // which now grants `canGenerateReports = true` unconditionally -- so it does
        // NOT exercise a genuinely restricted partner at all. This test constructs
        // one directly.
        val restrictedPartnerSession = SessionContext(
            userId = "viewer-1",
            userName = "Test User",
            activeRole = UserCareRole.PARTNER,
            targetUserId = "partner-1",
            permissions = SessionPermissions.primaryUser.copy(canGenerateReports = false),
        )
        val sessionManager = mockk<SessionManager> { every { current } returns restrictedPartnerSession }
        val cycleDataRepository = mockk<CycleDataRepository>()
        val periodLogRepository = mockk<PeriodLogRepository>()
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
        )

        viewModel.generate()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReportsPhase.Error, state.phase)
        assertEquals("No permission to generate reports", state.errorMessage)
        coVerify(exactly = 0) { cycleDataRepository.getAll(any()) }
        coVerify(exactly = 0) { periodLogRepository.getForDateRange(any(), any(), any()) }
    }

    @Test
    fun `generate succeeds for a partner explicitly granted the GENERATE_REPORTS permission`() = runTest {
        // Proves the new gate discriminates on the specific permission, not just
        // "any partner" -- a partner with `canGenerateReports = true` (and every
        // other granular permission denied, to prove independence from them) can
        // still generate/export, matching Karan's "if granted, the partner can
        // generate/export a report" requirement.
        val authorizedPartnerSession = SessionContext(
            userId = "viewer-1",
            userName = "Test User",
            activeRole = UserCareRole.PARTNER,
            targetUserId = "partner-1",
            permissions = SessionPermissions.primaryUser.copy(
                canViewSymptoms = false,
                canViewMoods = false,
                canViewWeight = false,
                canGenerateReports = true,
            ),
        )
        val sessionManager = mockk<SessionManager> { every { current } returns authorizedPartnerSession }
        val cycles = listOf(completedCycle(userId = "partner-1"))
        val logs = listOf(periodLog(userId = "partner-1"))
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("partner-1") } returns Result.success(cycles)
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange("partner-1", any(), any()) } returns Result.success(logs)
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
        )

        viewModel.generate()
        awaitUiState(viewModel) { it.phase == ReportsPhase.Preview }

        val state = viewModel.uiState.value
        assertEquals(ReportsPhase.Preview, state.phase)
        assertTrue(state.document != null)
        coVerify(exactly = 1) { cycleDataRepository.getAll("partner-1") }
    }

    @Test
    fun `generate uses the viewed partner name on the cover when the target profile name is blank`() = runTest {
        val partnership = CarePartnership(
            id = "partnership-1",
            userId = "partner-1",
            partnerId = "viewer-1",
            partnerName = "Riya",
        )
        val partnerSession = sessionContext(
            userId = "viewer-1",
            targetUserId = "partner-1",
            activePartnership = partnership,
        )
        val sessionManager = mockk<SessionManager> { every { current } returns partnerSession }
        val cycles = listOf(completedCycle(userId = "partner-1"))
        val logs = listOf(periodLog(userId = "partner-1"))
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("partner-1") } returns Result.success(cycles)
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange("partner-1", any(), any()) } returns Result.success(logs)
        }
        val userProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get("partner-1") } returns Result.success(
                UserProfile(
                    id = "partner-1",
                    name = "   ",
                    email = "riya@example.com",
                    phone = "9999999999",
                ),
            )
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
            userProfileRepository = userProfileRepository,
        )

        viewModel.generate()
        awaitUiState(viewModel) { it.phase == ReportsPhase.Preview }

        requireNotNull(viewModel.uiState.value.document)
        assertEquals("Riya", viewModel.uiState.value.document?.subjectName)
    }

    @Test
    fun `generate failure with a real message surfaces that message and an Error phase`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> { every { current } returns testSession }
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("user-1") } returns Result.failure(RuntimeException("network down"))
        }
        val viewModel = newViewModel(sessionManager, cycleDataRepository = cycleDataRepository)

        viewModel.generate()
        awaitUiState(viewModel) { it.phase == ReportsPhase.Error }

        val state = viewModel.uiState.value
        assertEquals(ReportsPhase.Error, state.phase)
        assertEquals("network down", state.errorMessage)
    }

    @Test
    fun `generate failure with no message falls back to the real string resource`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> { every { current } returns testSession }
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("user-1") } returns Result.failure(RuntimeException())
        }
        val viewModel = newViewModel(sessionManager, cycleDataRepository = cycleDataRepository)

        viewModel.generate()
        awaitUiState(viewModel) { it.phase == ReportsPhase.Error }

        assertEquals("Failed to load report data", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `generate fetches cycles and period logs concurrently, not sequentially`() = runTest {
        // Same technique as SanityContentViewModelTest/RecommendationsViewModelTest's
        // concurrent-fetch proofs: gate both real `async` calls on the same
        // uncompleted CompletableDeferred, then confirm both were already invoked
        // before either is allowed to finish. A genuinely sequential implementation
        // would only start the logs fetch after the cycles fetch resolves.
        val gate = CompletableDeferred<Unit>()
        val called = mutableListOf<String>()
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> { every { current } returns testSession }
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll(any()) } coAnswers {
                synchronized(called) { called.add("cycles") }
                gate.await()
                Result.success(emptyList())
            }
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } coAnswers {
                synchronized(called) { called.add("logs") }
                gate.await()
                Result.success(emptyList())
            }
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
        )

        viewModel.generate()
        advanceUntilIdle()

        assertEquals(setOf("cycles", "logs"), called.toSet())
        gate.complete(Unit)
        awaitUiState(viewModel) { it.phase == ReportsPhase.Preview }
        assertEquals(ReportsPhase.Preview, viewModel.uiState.value.phase)
    }

    @Test
    fun `a stale generation for an already-abandoned target resets to Config instead of Preview`() = runTest {
        val sessionA = sessionContext(targetUserId = "user-1")
        val sessionB = sessionContext(targetUserId = "user-2")
        val currentSlot = arrayOf<SessionContext?>(sessionA)
        val sessionManager = mockk<SessionManager> { every { current } answers { currentSlot[0] } }
        val gate = CompletableDeferred<Unit>()
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("user-1") } coAnswers {
                gate.await()
                Result.success(emptyList())
            }
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange("user-1", any(), any()) } coAnswers {
                gate.await()
                Result.success(emptyList())
            }
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
        )

        viewModel.generate()
        advanceUntilIdle()
        assertEquals(ReportsPhase.Generating, viewModel.uiState.value.phase)

        currentSlot[0] = sessionB
        gate.complete(Unit)
        awaitUiState(viewModel) { it.phase == ReportsPhase.Config }

        val state = viewModel.uiState.value
        assertEquals(ReportsPhase.Config, state.phase)
        assertNull(state.document)
        assertNull(state.errorMessage)
    }

    @Test
    fun `exportPdf without a generated report sets an export error and never invokes the exporter`() = runTest {
        val sessionManager = mockk<SessionManager> { every { current } returns null }
        val reportPdfExporter = mockk<ReportPdfExporter>()
        val viewModel = newViewModel(sessionManager, reportPdfExporter = reportPdfExporter)

        viewModel.exportPdf()
        advanceUntilIdle()

        assertEquals("Generate a report before exporting", viewModel.uiState.value.exportErrorMessage)
        coVerify(exactly = 0) { reportPdfExporter.export(any(), any()) }
    }

    @Test
    fun `generate prepares the PDF before Preview and exportPdf reuses the cached share uri`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> { every { current } returns testSession }
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("user-1") } returns Result.success(listOf(completedCycle()))
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(listOf(periodLog()))
        }
        val fakeFile = File("/tmp/SakhiReport_test.pdf")
        val fakeUri = mockk<Uri>()
        val reportPdfExporter = mockk<ReportPdfExporter> {
            every { export(any(), any()) } returns fakeFile
            every { buildShareUri(fakeFile) } returns fakeUri
        }
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
            reportPdfExporter = reportPdfExporter,
            hapticManager = hapticManager,
        )
        viewModel.generate()
        awaitUiState(viewModel) { it.phase == ReportsPhase.Preview }

        viewModel.exportPdf()

        val state = viewModel.uiState.value
        assertEquals(fakeUri, state.sharePdfUri)
        assertNull(state.exportErrorMessage)
        verify(exactly = 1) { reportPdfExporter.export(any(), ReportSection.entries.toSet()) }
        verify(exactly = 1) { reportPdfExporter.buildShareUri(fakeFile) }
        verify(exactly = 1) { hapticManager.impact(HapticImpact.MEDIUM) }
    }

    @Test
    fun `a stale same-target session swap during PDF preparation is discarded`() = runTest {
        val sessionA = sessionContext(userId = "user-1", targetUserId = "user-1")
        val sessionB = sessionContext(userId = "partner-1", targetUserId = "user-1")
        val currentSlot = arrayOf<SessionContext?>(sessionA)
        val sessionManager = mockk<SessionManager> { every { current } answers { currentSlot[0] } }
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("user-1") } returns Result.success(listOf(completedCycle()))
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(listOf(periodLog()))
        }
        val fakeFile = File("/tmp/SakhiReport_test.pdf")
        val fakeUri = mockk<Uri>()
        val latch = CountDownLatch(1)
        val reportPdfExporter = mockk<ReportPdfExporter> {
            every { export(any(), any()) } answers {
                latch.await()
                fakeFile
            }
            every { buildShareUri(fakeFile) } returns fakeUri
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
            reportPdfExporter = reportPdfExporter,
        )
        viewModel.generate()
        advanceUntilIdle()

        currentSlot[0] = sessionB
        latch.countDown()
        awaitUiState(viewModel) { it.phase == ReportsPhase.Config }

        val state = viewModel.uiState.value
        assertEquals(ReportsPhase.Config, state.phase)
        assertNull(state.sharePdfUri)
        assertNull(state.exportErrorMessage)
    }

    @Test
    fun `generate PDF failure with a real message surfaces that message`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> { every { current } returns testSession }
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("user-1") } returns Result.success(listOf(completedCycle()))
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(listOf(periodLog()))
        }
        val reportPdfExporter = mockk<ReportPdfExporter> {
            every { export(any(), any()) } throws RuntimeException("disk full")
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
            reportPdfExporter = reportPdfExporter,
        )
        viewModel.generate()
        awaitUiState(viewModel) { it.phase == ReportsPhase.Error }

        val state = viewModel.uiState.value
        assertEquals(ReportsPhase.Error, state.phase)
        assertEquals("disk full", state.errorMessage)
    }

    @Test
    fun `generate PDF failure with no throwable message falls back to the real string resource`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> { every { current } returns testSession }
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("user-1") } returns Result.success(listOf(completedCycle()))
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(listOf(periodLog()))
        }
        val reportPdfExporter = mockk<ReportPdfExporter> {
            every { export(any(), any()) } throws RuntimeException()
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
            reportPdfExporter = reportPdfExporter,
        )
        viewModel.generate()
        awaitUiState(viewModel) { it.phase == ReportsPhase.Error }

        val state = viewModel.uiState.value
        assertEquals(ReportsPhase.Error, state.phase)
        assertEquals("Couldn't export PDF", state.errorMessage)
    }

    @Test
    fun `returnToConfig clears stale export state after a successful generate`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> { every { current } returns testSession }
        val cycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll("user-1") } returns Result.success(listOf(completedCycle()))
        }
        val periodLogRepository = mockk<PeriodLogRepository>().also {
            coEvery { it.getForDateRange(any(), any(), any()) } returns Result.success(listOf(periodLog()))
        }
        val fakeFile = File("/tmp/SakhiReport_test.pdf")
        val fakeUri = mockk<Uri>()
        val reportPdfExporter = mockk<ReportPdfExporter> {
            every { export(any(), any()) } returns fakeFile
            every { buildShareUri(fakeFile) } returns fakeUri
        }
        val viewModel = newViewModel(
            sessionManager,
            cycleDataRepository = cycleDataRepository,
            periodLogRepository = periodLogRepository,
            reportPdfExporter = reportPdfExporter,
        )
        viewModel.generate()
        awaitUiState(viewModel) { it.phase == ReportsPhase.Preview }
        viewModel.exportPdf()

        viewModel.returnToConfig()

        val state = viewModel.uiState.value
        assertEquals(ReportsPhase.Config, state.phase)
        assertNull(state.exportErrorMessage)
        assertNull(state.sharePdfUri)
    }
}
