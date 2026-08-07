package team.sakhi.android.feature.profile

import android.content.Context
import android.content.res.Resources
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import team.sakhi.android.platform.AndroidHealthConnectManager
import team.sakhi.android.platform.DailyHealthValue
import team.sakhi.android.platform.HealthConnectAvailability
import team.sakhi.android.platform.HealthConnectInsights
import team.sakhi.android.platform.HealthConnectSyncResult
import team.sakhi.models.UserCareRole
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.session.SessionPermissions

/**
 * State-machine test for `AppIntegrationViewModel`. `SessionManager`/
 * `AndroidHealthConnectManager` are concrete, non-open platform classes (same
 * situation as `HomeViewModelTest`), so this uses mockk for those boundaries.
 * The last untested ViewModel in this thread -- mostly a thin Android/Health
 * Connect SDK adapter rather than shared KMM logic, but the own-data-vs-partner
 * gating rule and the real elapsed-time label formatting are still worth
 * proving for real rather than assumed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppIntegrationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sessionContext(userId: String = "user-1", targetUserId: String = userId) = SessionContext(
        userId = userId,
        userName = "Test User",
        activeRole = if (userId == targetUserId) UserCareRole.PRIMARY_USER else UserCareRole.PARTNER,
        targetUserId = targetUserId,
        permissions = SessionPermissions.primaryUser,
    )

    /** Stubs both `session` and `current` -- the init block always subscribes to `session`. */
    private fun mockSessionManager(session: SessionContext?) = mockk<SessionManager> {
        every { this@mockk.session } returns MutableStateFlow(session)
        every { current } returns session
    }

    private fun mockSessionManager(
        sessionFlow: MutableStateFlow<SessionContext?>,
        currentProvider: () -> SessionContext?,
    ) = mockk<SessionManager> {
        every { this@mockk.session } returns sessionFlow
        every { current } answers { currentProvider() }
    }

    private fun mockContext(): Context {
        val resources = mockk<Resources> {
            every { getQuantityString(R.plurals.profile_app_integration_last_synced_minutes_ago, any(), any()) } answers {
                "${secondArg<Int>()} minutes ago"
            }
            every { getQuantityString(R.plurals.profile_app_integration_last_synced_hours_ago, any(), any()) } answers {
                "${secondArg<Int>()} hours ago"
            }
        }
        return mockk {
            every { this@mockk.resources } returns resources
            every { getString(R.string.profile_app_integration_permission_denied) } returns "Health Connect access was not granted yet."
            every { getString(R.string.profile_app_integration_sync_failed) } returns "Health Connect sync failed."
            every { getString(R.string.profile_app_integration_last_synced_just_now) } returns "just now"
            every { getString(R.string.profile_app_integration_last_synced_yesterday) } returns "Yesterday"
            // `getString(id, vararg formatArgs)` is a vararg call -- `secondArg()` would
            // return the whole `Array<*>`, not the element inside it, so the vararg must
            // be unwrapped from `invocation.args` explicitly instead.
            every { getString(R.string.profile_app_integration_last_synced, any()) } answers {
                val label = (invocation.args[1] as Array<*>).first()
                "Last synced $label"
            }
        }
    }

    private fun mockHealthConnectManager(
        availability: HealthConnectAvailability = HealthConnectAvailability.Available,
        hasAllPermissions: Boolean = true,
        isEnabled: Boolean = true,
        lastSyncedAtIso: String? = null,
        insights: HealthConnectInsights = HealthConnectInsights(),
        requiredPermissions: Set<String> = setOf("perm.a", "perm.b"),
    ) = mockk<AndroidHealthConnectManager> {
        every { this@mockk.requiredPermissions } returns requiredPermissions
        every { this@mockk.availability() } returns availability
        coEvery { hasAllPermissions() } returns hasAllPermissions
        every { this@mockk.isEnabled() } returns isEnabled
        every { lastSyncedAtIso() } returns lastSyncedAtIso
        coEvery { loadInsights() } returns insights
    }

    private fun newViewModel(
        sessionManager: SessionManager,
        healthConnectManager: AndroidHealthConnectManager = mockHealthConnectManager(),
        appContext: Context = mockContext(),
    ) = AppIntegrationViewModel(sessionManager, healthConnectManager, appContext)

    @Test
    fun `no session resets to a stopped-loading state with the real availability passed through`() = runTest {
        val sessionManager = mockSessionManager(null)
        val healthConnectManager = mockHealthConnectManager(availability = HealthConnectAvailability.NotInstalled)
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.session)
        assertFalse(state.isLoading)
        assertEquals(HealthConnectAvailability.NotInstalled, state.availability)
    }

    @Test
    fun `own-data session with Health Connect unavailable shows the unavailable state without fetching insights`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val healthConnectManager = mockHealthConnectManager(availability = HealthConnectAvailability.NotSupported)
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasPermissions)
        assertTrue(state.sleepEntries.isEmpty())
        coVerify(exactly = 0) { healthConnectManager.hasAllPermissions() }
    }

    @Test
    fun `partner session shows the unavailable-style state regardless of real Health Connect availability`() = runTest {
        val testSession = sessionContext(userId = "partner-1", targetUserId = "primary-1")
        val sessionManager = mockSessionManager(testSession)
        val healthConnectManager = mockHealthConnectManager(availability = HealthConnectAvailability.Available)
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasPermissions)
        assertFalse(state.isEnabled)
        coVerify(exactly = 0) { healthConnectManager.hasAllPermissions() }
    }

    @Test
    fun `own-data session available but not enabled skips fetching insights`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val healthConnectManager = mockHealthConnectManager(hasAllPermissions = true, isEnabled = false)
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.sleepEntries.isEmpty())
        coVerify(exactly = 0) { healthConnectManager.loadInsights() }
    }

    @Test
    fun `own-data session with permissions and enabled loads real insights`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val insights = HealthConnectInsights(
            sleepEntries = listOf(DailyHealthValue(java.time.LocalDate.now().let { kotlinx.datetime.LocalDate(it.year, it.monthValue, it.dayOfMonth) }, 7.5)),
        )
        val healthConnectManager = mockHealthConnectManager(hasAllPermissions = true, isEnabled = true, insights = insights)
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasPermissions)
        assertTrue(state.isEnabled)
        assertEquals(insights.sleepEntries, state.sleepEntries)
    }

    @Test
    fun `onPermissionsResult with all required permissions granted triggers a real sync`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val healthConnectManager = mockHealthConnectManager(requiredPermissions = setOf("perm.a", "perm.b"))
        coEvery { healthConnectManager.syncNow() } returns HealthConnectSyncResult(0, 0, 0, 0, "2026-07-14T00:00:00Z")
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)
        advanceUntilIdle()

        viewModel.onPermissionsResult(setOf("perm.a", "perm.b", "perm.c"))
        advanceUntilIdle()

        coVerify(exactly = 1) { healthConnectManager.syncNow() }
    }

    @Test
    fun `onPermissionsResult with missing permissions surfaces a real denial message and refreshes`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val healthConnectManager = mockHealthConnectManager(requiredPermissions = setOf("perm.a", "perm.b"))
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)
        advanceUntilIdle()

        viewModel.onPermissionsResult(setOf("perm.a"))
        advanceUntilIdle()

        assertEquals("Health Connect access was not granted yet.", viewModel.uiState.value.error)
        coVerify(exactly = 0) { healthConnectManager.syncNow() }
    }

    @Test
    fun `syncNow success stores the real result and computes a real just-now label`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val nowIso = java.time.Instant.now().toString()
        val syncResult = HealthConnectSyncResult(3, 1, 2, 0, nowIso)
        val healthConnectManager = mockHealthConnectManager()
        coEvery { healthConnectManager.syncNow() } returns syncResult
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)
        advanceUntilIdle()

        viewModel.syncNow()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSyncing)
        assertEquals(syncResult, state.latestSync)
        assertEquals("Last synced just now", state.lastSyncedAtLabel)
    }

    @Test
    fun `syncNow success computes a real minutes-ago label through the real quantity string`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val fiveMinutesAgo = java.time.Instant.now().minusSeconds(5 * 60).toString()
        val syncResult = HealthConnectSyncResult(0, 0, 0, 0, fiveMinutesAgo)
        val healthConnectManager = mockHealthConnectManager()
        coEvery { healthConnectManager.syncNow() } returns syncResult
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)
        advanceUntilIdle()

        viewModel.syncNow()
        advanceUntilIdle()

        assertEquals("Last synced 5 minutes ago", viewModel.uiState.value.lastSyncedAtLabel)
    }

    @Test
    fun `syncNow failure surfaces a real message`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val healthConnectManager = mockHealthConnectManager()
        coEvery { healthConnectManager.syncNow() } throws RuntimeException("permission revoked")
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)
        advanceUntilIdle()

        viewModel.syncNow()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSyncing)
        // Was asserting the RAW exception message. That pinned a real defect:
        // backend exception text embeds the request URL and auth headers and was
        // rendering as user-visible copy. UI shows app copy; cause is logged only.
        assertEquals("Health Connect sync failed.", state.error)
    }

    @Test
    fun `syncNow success followed by a thrown loadInsights surfaces the error and clears syncing`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val healthConnectManager = mockHealthConnectManager()
        coEvery {
            healthConnectManager.syncNow()
        } returns HealthConnectSyncResult(1, 0, 0, 0, "2026-07-14T00:00:00Z")
        coEvery { healthConnectManager.loadInsights() } throws RuntimeException("insights unavailable")
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)
        advanceUntilIdle()

        viewModel.syncNow()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSyncing)
        // Was asserting the RAW exception message. That pinned a real defect:
        // backend exception text embeds the request URL and auth headers and was
        // rendering as user-visible copy. UI shows app copy; cause is logged only.
        assertEquals("Health Connect sync failed.", state.error)
        assertNull(state.latestSync)
    }

    @Test
    fun `stale own-data refresh result is discarded after the current session is cleared`() = runTest {
        val ownSession = sessionContext()
        val currentSlot = arrayOf<SessionContext?>(null)
        val sessionManager = mockSessionManager(
            sessionFlow = MutableStateFlow<SessionContext?>(null),
            currentProvider = { currentSlot[0] },
        )
        val gate = CompletableDeferred<Unit>()
        val insights = HealthConnectInsights(
            sleepEntries = listOf(
                DailyHealthValue(
                    date = kotlinx.datetime.LocalDate(2026, 7, 14),
                    value = 8.0,
                ),
            ),
        )
        val healthConnectManager = mockHealthConnectManager(insights = insights)
        coEvery { healthConnectManager.hasAllPermissions() } coAnswers {
            gate.await()
            true
        }
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)
        advanceUntilIdle()

        currentSlot[0] = ownSession
        viewModel.refresh()
        advanceUntilIdle()

        currentSlot[0] = null
        viewModel.refresh()
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.session)
        assertTrue(state.sleepEntries.isEmpty())
        assertFalse(state.hasPermissions)
    }

    @Test
    fun `stale sync result is discarded after the current session is cleared`() = runTest {
        val ownSession = sessionContext()
        val sessionFlow = MutableStateFlow<SessionContext?>(ownSession)
        val currentSlot = arrayOf<SessionContext?>(ownSession)
        val sessionManager = mockSessionManager(
            sessionFlow = sessionFlow,
            currentProvider = { currentSlot[0] },
        )
        val syncGate = CompletableDeferred<Unit>()
        val insights = HealthConnectInsights(
            sleepEntries = listOf(
                DailyHealthValue(
                    date = kotlinx.datetime.LocalDate(2026, 7, 14),
                    value = 7.5,
                ),
            ),
        )
        val healthConnectManager = mockHealthConnectManager(insights = insights)
        val syncResult = HealthConnectSyncResult(2, 1, 0, 0, "2026-07-14T00:00:00Z")
        coEvery { healthConnectManager.syncNow() } coAnswers {
            syncGate.await()
            syncResult
        }
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)
        advanceUntilIdle()

        viewModel.syncNow()
        advanceUntilIdle()

        currentSlot[0] = null
        sessionFlow.value = null
        advanceUntilIdle()

        syncGate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.session)
        assertNull(state.latestSync)
        assertTrue(state.sleepEntries.isEmpty())
        assertFalse(state.isSyncing)
    }

    @Test
    fun `disconnect calls the real disableIntegration, clears latestSync, and refreshes`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val healthConnectManager = mockHealthConnectManager()
        every { healthConnectManager.disableIntegration() } returns Unit
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)
        advanceUntilIdle()

        viewModel.disconnect()
        advanceUntilIdle()

        verify(exactly = 1) { healthConnectManager.disableIntegration() }
        assertNull(viewModel.uiState.value.latestSync)
    }

    @Test
    fun `clearError resets the error field`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val healthConnectManager = mockHealthConnectManager()
        coEvery { healthConnectManager.syncNow() } throws RuntimeException("boom")
        val viewModel = newViewModel(sessionManager, healthConnectManager = healthConnectManager)
        advanceUntilIdle()
        viewModel.syncNow()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.error != null)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.error)
    }
}
