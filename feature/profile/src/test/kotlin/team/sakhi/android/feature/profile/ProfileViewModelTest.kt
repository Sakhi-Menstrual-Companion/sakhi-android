package team.sakhi.android.feature.profile

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import team.sakhi.android.testing.MainDispatcherRule
import org.junit.Test
import team.sakhi.access.FeatureAccessState
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.appstate.AppStateInputBridge
import team.sakhi.auth.AuthRepository
import team.sakhi.models.CycleData
import team.sakhi.models.CycleHealthStatus
import team.sakhi.models.UserProfile
import team.sakhi.repositories.CycleDataRepository
import team.sakhi.repositories.UserProfileRepository
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.session.SessionPermissions
import team.sakhi.state.SessionState

/**
 * State-machine test for `ProfileViewModel`. `SessionManager`/`UserProfileRepository`/
 * `CycleDataRepository`/`AuthRepository`/`AndroidHapticManager` are concrete, non-open
 * KMM/platform classes (same situation as `HomeViewModelTest`), so this uses mockk for
 * those boundaries. `FeatureAccessState` and `AppStateInputBridge` are plain real state
 * holders with no external side effects, so real instances are used instead of mocks --
 * this actually proves `isOfflineUser`/sign-out routing against the real class, not a
 * stubbed value. `cycleHealthStatus` is computed through the real
 * `CycleMath.profileHealthStatus`, so a real regression in that shared rule would fail
 * this test too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val testDispatcher get() = mainDispatcherRule.testDispatcher



    private fun sessionContext(userId: String = "user-1") = SessionContext(
        userId = userId,
        userName = "Test User",
        activeRole = team.sakhi.models.UserCareRole.PRIMARY_USER,
        targetUserId = userId,
        permissions = SessionPermissions.primaryUser,
    )

    private fun profile(userId: String = "user-1") = UserProfile(
        id = userId,
        name = "Test User",
        email = "test@example.com",
        phone = "9990421555",
    )

    private fun newViewModel(
        sessionManager: SessionManager,
        userProfileRepository: UserProfileRepository = mockk(),
        cycleDataRepository: CycleDataRepository = mockk<CycleDataRepository>().also {
            coEvery { it.getAll(any()) } returns Result.success(emptyList())
        },
        authRepository: AuthRepository = mockk(),
        appStateInputBridge: AppStateInputBridge = AppStateInputBridge(),
        featureAccessState: FeatureAccessState = FeatureAccessState(),
        hapticManager: AndroidHapticManager = mockk(relaxed = true),
        appContext: Context = mockk {
            every { getString(R.string.profile_load_failed) } returns "Failed to load profile"
            every { getString(R.string.profile_sign_out_failed) } returns "Couldn't sign out. Please try again."
        },
    ) = ProfileViewModel(
        sessionManager,
        userProfileRepository,
        cycleDataRepository,
        authRepository,
        appStateInputBridge,
        featureAccessState,
        hapticManager,
        appContext,
    )

    @Test
    fun `no session resets the profile but preserves the independently-driven isOfflineUser flag`() = runTest {
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val featureAccessState = FeatureAccessState()
        featureAccessState.setGuest(true)
        val viewModel = newViewModel(sessionManager, featureAccessState = featureAccessState)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.session)
        assertNull(state.profile)
        assertTrue(state.isOfflineUser)
    }

    @Test
    fun `own session loads the real profile and computes cycle health status through the actual CycleMath`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        // `get` collides with `MockKMatcherScope`'s own dynamic-call `get` operator when
        // called implicitly inside a `coEvery { ... }` block nested in a
        // `mockk<UserProfileRepository> { ... }` builder, so it's referenced on the mock
        // explicitly (`it.get(...)`) everywhere in this file instead.
        val userProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get("user-1") } returns Result.success(profile())
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            // Empty cycle history is a real, deterministic input to CycleMath.profileHealthStatus:
            // fewer than 2 recorded lengths always resolves to REGULAR, confirmed by reading
            // the real function rather than assuming it.
            coEvery { getAll("user-1") } returns Result.success(emptyList<CycleData>())
        }
        val viewModel = newViewModel(
            sessionManager,
            userProfileRepository = userProfileRepository,
            cycleDataRepository = cycleDataRepository,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(profile(), state.profile)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(CycleHealthStatus.REGULAR, state.cycleHealthStatus)
    }

    @Test
    fun `profile load failure with a real message surfaces that message`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val userProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get("user-1") } returns Result.failure(RuntimeException("network down"))
        }
        val viewModel = newViewModel(sessionManager, userProfileRepository = userProfileRepository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Was asserting the RAW exception message. That pinned a real defect:
        // backend exception text embeds the request URL and auth headers and was
        // rendering as user-visible copy. UI shows app copy; cause is logged only.
        assertEquals("Failed to load profile", state.error)
        assertNull(state.profile)
        assertFalse(state.isLoading)
    }

    @Test
    fun `profile load failure with no message falls back to the real string resource`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow<SessionContext?>(testSession)
            every { current } returns testSession
        }
        val userProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get("user-1") } returns Result.failure(RuntimeException())
        }
        val viewModel = newViewModel(sessionManager, userProfileRepository = userProfileRepository)

        advanceUntilIdle()

        assertEquals("Failed to load profile", viewModel.uiState.value.error)
    }

    @Test
    fun `a stale success response for an already-abandoned user id is discarded`() = runTest {
        val sessionA = sessionContext(userId = "user-1")
        val sessionB = sessionContext(userId = "user-2")
        val sessionFlow = MutableStateFlow(sessionA)
        val currentSlot = arrayOf(sessionA)
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } answers { currentSlot[0] }
        }
        val userProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get("user-1") } coAnswers { awaitCancellation() }
            coEvery { it.get("user-2") } returns Result.success(profile(userId = "user-2"))
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getAll(any()) } returns Result.success(emptyList())
        }
        val viewModel = newViewModel(
            sessionManager,
            userProfileRepository = userProfileRepository,
            cycleDataRepository = cycleDataRepository,
        )
        advanceUntilIdle()

        currentSlot[0] = sessionB
        sessionFlow.value = sessionB
        advanceUntilIdle()

        assertEquals("user-2", viewModel.uiState.value.profile?.id)
    }

    @Test
    fun `a stale failure response for an already-abandoned user id is also discarded`() = runTest {
        // The success and failure branches guard independently in the real source
        // (two separate `if (sessionManager.current?.userId != requestedUserId) return`
        // checks) -- proving both, not just the success path, catches a regression
        // where only one guard is accidentally removed. The success-path test above
        // relies on `sessionManager.session`'s `collectLatest` cancelling the abandoned
        // coroutine outright, which never actually reaches either guard -- so it can't
        // tell the guard apart from `collectLatest` doing all the work. This test keeps
        // `session` unchanged (so the user-1 coroutine is never cancelled and genuinely
        // runs `.onFailure`) and instead moves only `sessionManager.current` -- a
        // separate mockable property in this fixture, exactly mirroring how a real
        // in-memory `current` snapshot can advance slightly ahead of the collected
        // `session` StateFlow's own emission -- so the guard itself, not cancellation,
        // is what has to catch the stale failure.
        val sessionA = sessionContext(userId = "user-1")
        val sessionB = sessionContext(userId = "user-2")
        val currentSlot = arrayOf(sessionA)
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(sessionA)
            every { current } answers { currentSlot[0] }
        }
        val gate = CompletableDeferred<Unit>()
        val userProfileRepository = mockk<UserProfileRepository>().also {
            coEvery { it.get("user-1") } coAnswers {
                gate.await()
                Result.failure(RuntimeException("stale network error"))
            }
        }
        val cycleDataRepository = mockk<CycleDataRepository> {
            coEvery { getAll(any()) } returns Result.success(emptyList())
        }
        val viewModel = newViewModel(
            sessionManager,
            userProfileRepository = userProfileRepository,
            cycleDataRepository = cycleDataRepository,
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)

        // Advance `current` past user-1 without touching the collected `session` flow,
        // then let user-1's real failure land.
        currentSlot[0] = sessionB
        gate.complete(Unit)
        advanceUntilIdle()

        // The guard must have caught this itself: still mid-load, no stale error applied.
        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertNull(state.error)
        assertNull(state.profile)
    }

    @Test
    fun `confirmSignOut success clears the confirmation state and flips the real AppStateInputBridge to Unauthenticated`() = runTest {
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val authRepository = mockk<AuthRepository> {
            coEvery { signOut() } returns Result.success(Unit)
        }
        val appStateInputBridge = AppStateInputBridge()
        val viewModel = newViewModel(
            sessionManager,
            authRepository = authRepository,
            appStateInputBridge = appStateInputBridge,
        )
        advanceUntilIdle()
        viewModel.requestSignOut()
        assertTrue(viewModel.uiState.value.showSignOutConfirm)

        viewModel.confirmSignOut()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSigningOut)
        assertFalse(state.showSignOutConfirm)
        assertNull(state.signOutError)
        assertTrue(appStateInputBridge.sessionState.value is SessionState.Unauthenticated)
    }

    @Test
    fun `confirmSignOut failure with a real message surfaces that message and triggers a haptic error`() = runTest {
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val authRepository = mockk<AuthRepository> {
            coEvery { signOut() } returns Result.failure(RuntimeException("sign out failed"))
        }
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(sessionManager, authRepository = authRepository, hapticManager = hapticManager)
        advanceUntilIdle()

        viewModel.confirmSignOut()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSigningOut)
        // Was asserting the RAW exception message. That pinned a real defect:
        // backend exception text embeds the request URL and auth headers and was
        // rendering as user-visible copy. UI shows app copy; cause is logged only.
        assertEquals("Couldn't sign out. Please try again.", state.signOutError)
        verify(exactly = 1) { hapticManager.error() }
    }

    @Test
    fun `confirmSignOut failure with no message falls back to the real string resource`() = runTest {
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val authRepository = mockk<AuthRepository> {
            coEvery { signOut() } returns Result.failure(RuntimeException())
        }
        val viewModel = newViewModel(sessionManager, authRepository = authRepository)
        advanceUntilIdle()

        viewModel.confirmSignOut()
        advanceUntilIdle()

        assertEquals("Couldn't sign out. Please try again.", viewModel.uiState.value.signOutError)
    }

    @Test
    fun `confirmSignOut is a no-op re-entry guard while a sign-out is already in flight`() = runTest {
        val sessionManager = mockk<SessionManager> {
            every { session } returns MutableStateFlow(null)
            every { current } returns null
        }
        val gate = CompletableDeferred<Unit>()
        val authRepository = mockk<AuthRepository> {
            coEvery { signOut() } coAnswers {
                gate.await()
                Result.success(Unit)
            }
        }
        val viewModel = newViewModel(sessionManager, authRepository = authRepository)
        advanceUntilIdle()

        viewModel.confirmSignOut()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSigningOut)

        viewModel.confirmSignOut()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.signOut() }
    }
}
