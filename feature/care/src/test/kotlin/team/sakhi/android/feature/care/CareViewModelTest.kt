package team.sakhi.android.feature.care

import android.content.Context
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
import team.sakhi.android.platform.AndroidHapticManager
import team.sakhi.android.platform.HapticImpact
import team.sakhi.care.CareRuntimeState
import team.sakhi.care.CareStatusResponse
import team.sakhi.care.CareStore
import team.sakhi.models.CarePartnership
import team.sakhi.models.ParentChildPermissions
import team.sakhi.models.PartnerInvitation
import team.sakhi.models.UserCareRole
import team.sakhi.session.SessionContext
import team.sakhi.session.SessionManager
import team.sakhi.session.SessionPermissions

/**
 * State-machine test for `CareViewModel`. `SessionManager`/`CareStore`/
 * `AndroidHapticManager` are concrete, non-open KMM/platform classes (same
 * situation as `HomeViewModelTest`), so this uses mockk for those boundaries.
 * `latestInvitation()`'s real precedence rule (careState's own pending invitation
 * wins over the session's `sentInvitations`) is exercised through the actual
 * private logic via observable state, not asserted from reading the source alone.
 * The init block always subscribes to both `sessionManager.session` and
 * `careStore.careState`/`refresh(...)` regardless of which public method a given
 * test exercises, so every mock here stubs all of those up front.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CareViewModelTest {

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
        sentInvitations: List<PartnerInvitation> = emptyList(),
    ) = SessionContext(
        userId = userId,
        userName = "Test User",
        activeRole = UserCareRole.PRIMARY_USER,
        targetUserId = userId,
        permissions = SessionPermissions.primaryUser,
        sentInvitations = sentInvitations,
    )

    private fun invitation(id: String = "invite-1", inviteCode: String = "ABC123") = PartnerInvitation(
        id = id,
        inviterId = "user-1",
        inviterName = "Test User",
        inviteePhone = "",
        inviteCode = inviteCode,
        expiresAt = "2026-08-01T00:00:00Z",
    )

    private fun careStatusResponse(inviteCode: String? = "ABC123") = CareStatusResponse(
        state = "pending_invitation",
        invitation = CareStatusResponse.InvitationPayload(
            id = "invite-1",
            inviteCode = inviteCode.orEmpty(),
            expiresAt = "2026-08-01T00:00:00Z",
        ),
    )

    /** Stubs both `session` and `current` -- the init block always subscribes to `session`. */
    private fun mockSessionManager(session: SessionContext?) = mockk<SessionManager> {
        every { this@mockk.session } returns MutableStateFlow(session)
        every { current } returns session
    }

    /**
     * Stubs `careState` and a default successful `refresh(...)` -- the init block
     * always calls it for a non-null session. `careState` is backed by a real
     * mutable flow (not a fresh one returned per property access) so a stubbed
     * `reset()` can actually flip what subsequent collectors observe, the same
     * way the real `CareStore.reset()` mutates its own backing `_careState`.
     */
    private fun mockCareStore(
        careState: CareRuntimeState = CareRuntimeState.Disconnected,
        refreshResult: Result<CareStatusResponse> = Result.success(careStatusResponse()),
        block: CareStore.() -> Unit = {},
    ): CareStore {
        val careStateFlow = MutableStateFlow(careState)
        return mockk<CareStore> {
            every { this@mockk.careState } returns careStateFlow
            coEvery { refresh(any()) } answers { refreshResult.getOrThrow() }
            every { reset() } answers { careStateFlow.value = CareRuntimeState.Disconnected }
            block()
        }
    }

    private fun newViewModel(
        sessionManager: SessionManager,
        careStore: CareStore = mockCareStore(),
        hapticManager: AndroidHapticManager = mockk(relaxed = true),
        appContext: Context = mockk {
            every { getString(R.string.care_error_load_status) } returns "Unable to load care status right now."
            every { getString(R.string.care_error_refresh_status) } returns "Unable to refresh care status right now."
            every { getString(R.string.care_fallback_user) } returns "User"
            every { getString(R.string.care_info_invite_ready) } returns "The invite is ready whenever you would like to share it."
            every { getString(R.string.care_info_invite_code_ready) } returns "Your invite code is ready to share whenever it feels right."
            every { getString(R.string.care_error_create_invite) } returns "Unable to create an invite right now."
            every { getString(R.string.care_error_enter_invite_code) } returns "Please enter the full 6-character code."
            every { getString(R.string.care_info_invite_accepted) } returns "The invite was accepted."
            every { getString(R.string.care_error_accept_invite) } returns "Unable to accept this invite right now."
            every { getString(R.string.care_info_invite_closed) } returns "That invite has been closed. Nothing was shared."
            every { getString(R.string.care_error_cancel_invite) } returns "Unable to cancel this invite right now."
            every { getString(R.string.care_error_complete_action) } returns "Unable to complete this right now."
            every { getString(R.string.care_error_permission_change_not_saved) } returns "The latest permission change was not saved. Please try again."
        },
    ) = CareViewModel(appContext, sessionManager, careStore, hapticManager)

    @Test
    fun `no session resets to Disconnected and never touches the store`() = runTest {
        val sessionManager = mockSessionManager(null)
        val careStore = mockCareStore()
        val viewModel = newViewModel(sessionManager, careStore = careStore)

        advanceUntilIdle()

        assertEquals(CareRuntimeState.Disconnected, viewModel.uiState.value.careState)
        coVerify(exactly = 0) { careStore.refresh(any()) }
    }

    @Test
    fun `signing out resets to Disconnected even when CareStore itself still retains stale connected state`() = runTest {
        // CareStore is a Koin singleton retained for the whole app process, and
        // nothing in it resets `_careState` back to Loading/Disconnected on
        // sign-out (only an explicit `leavePartnership()` call does) -- the
        // same "retained across sign-out" shape as the real Auth bug found
        // earlier this session, just at the store layer instead of the
        // ViewModel layer. `CareViewModel.init` runs *two* independent
        // `collectLatest` blocks off the same `sessionManager.session`: one
        // resets to a clean `CareUiState(careState = Disconnected)`, the other
        // `combine`s with `careStore.careState` and could re-apply whatever
        // stale value the store still holds. This proves which one wins.
        val stalePartnership = CarePartnership(
            id = "partnership-1",
            userId = "user-1",
            partnerId = "partner-1",
            partnerName = "Partner",
        )
        val sessionFlow = MutableStateFlow<SessionContext?>(sessionContext())
        val currentSlot = arrayOf<SessionContext?>(sessionContext())
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } answers { currentSlot[0] }
        }
        val careStore = mockCareStore(careState = CareRuntimeState.OwnerConnected(stalePartnership))
        val viewModel = newViewModel(sessionManager, careStore = careStore)
        advanceUntilIdle()
        assertEquals(CareRuntimeState.OwnerConnected(stalePartnership), viewModel.uiState.value.careState)

        currentSlot[0] = null
        sessionFlow.value = null
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CareRuntimeState.Disconnected, state.careState)
        assertNull(state.session)
    }

    @Test
    fun `own session refresh succeeds through the real CareStore call`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val careStore = mockCareStore()
        val viewModel = newViewModel(sessionManager, careStore = careStore)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertNull(state.error)
        coVerify(exactly = 1) { careStore.refresh("user-1") }
    }

    @Test
    fun `session refresh failure with a real message surfaces that message`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val careStore = mockCareStore(refreshResult = Result.failure(RuntimeException("network down")))
        val viewModel = newViewModel(sessionManager, careStore = careStore)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        // Was asserting the RAW exception message. That assertion pinned a real
        // defect: Supabase/Ktor messages embed the request URL, the
        // `Authorization: Bearer ...` header and the apikey, and they rendered
        // verbatim as user-visible error text (seen on a real device). The UI must
        // show app copy; the raw cause is logged only.
        assertEquals("Unable to load care status right now.", state.error)
    }

    @Test
    fun `session refresh failure with no message falls back to the real string resource`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val careStore = mockCareStore(refreshResult = Result.failure(RuntimeException()))
        val viewModel = newViewModel(sessionManager, careStore = careStore)

        advanceUntilIdle()

        assertEquals("Unable to load care status right now.", viewModel.uiState.value.error)
    }

    @Test
    fun `latestInvitation prefers the real careState pending invitation over the session's sentInvitations`() = runTest {
        val careInvitation = invitation(id = "care-invite")
        val sessionInvitation = invitation(id = "session-invite")
        val testSession = sessionContext(sentInvitations = listOf(sessionInvitation))
        val sessionManager = mockSessionManager(testSession)
        val careStore = mockCareStore(careState = CareRuntimeState.PendingInvitation(careInvitation))
        val viewModel = newViewModel(sessionManager, careStore = careStore)

        advanceUntilIdle()

        assertEquals("care-invite", viewModel.uiState.value.latestInvitation?.id)
    }

    @Test
    fun `latestInvitation falls back to the session's sentInvitations when careState has no pending invitation`() = runTest {
        val sessionInvitation = invitation(id = "session-invite")
        val testSession = sessionContext(sentInvitations = listOf(sessionInvitation))
        val sessionManager = mockSessionManager(testSession)
        val careStore = mockCareStore(careState = CareRuntimeState.Disconnected)
        val viewModel = newViewModel(sessionManager, careStore = careStore)

        advanceUntilIdle()

        assertEquals("session-invite", viewModel.uiState.value.latestInvitation?.id)
    }

    @Test
    fun `createInvitation is a no-op re-entry guard while a create is already in flight`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val gate = CompletableDeferred<Unit>()
        val careStore = mockCareStore {
            coEvery {
                createInvitation(any(), any(), any(), any(), any(), any(), any())
            } coAnswers {
                gate.await()
                careStatusResponse()
            }
        }
        val viewModel = newViewModel(sessionManager, careStore = careStore)
        advanceUntilIdle()

        viewModel.createInvitation()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCreatingInvite)

        viewModel.createInvitation()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { careStore.createInvitation(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `createInvitation success with a real invite code surfaces the code-ready message`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val careStore = mockCareStore {
            coEvery {
                createInvitation(any(), any(), any(), any(), any(), any(), any())
            } returns careStatusResponse(inviteCode = "ABC123")
        }
        val viewModel = newViewModel(sessionManager, careStore = careStore)
        advanceUntilIdle()

        viewModel.createInvitation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isCreatingInvite)
        assertEquals("Your invite code is ready to share whenever it feels right.", state.infoMessage)
    }

    @Test
    fun `createInvitation success with a blank invite code surfaces the plain ready message`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val careStore = mockCareStore {
            coEvery {
                createInvitation(any(), any(), any(), any(), any(), any(), any())
            } returns careStatusResponse(inviteCode = "")
        }
        val viewModel = newViewModel(sessionManager, careStore = careStore)
        advanceUntilIdle()

        viewModel.createInvitation()
        advanceUntilIdle()

        assertEquals(
            "The invite is ready whenever you would like to share it.",
            viewModel.uiState.value.infoMessage,
        )
    }

    @Test
    fun `a createInvitation completion after sign out is discarded instead of reviving stale invite state`() = runTest {
        val activeSession = sessionContext()
        val sessionFlow = MutableStateFlow<SessionContext?>(activeSession)
        val currentSlot = arrayOf<SessionContext?>(activeSession)
        val sessionManager = mockk<SessionManager> {
            every { session } returns sessionFlow
            every { current } answers { currentSlot[0] }
        }
        val gate = CompletableDeferred<Unit>()
        val careStore = mockCareStore {
            coEvery {
                createInvitation(any(), any(), any(), any(), any(), any(), any())
            } coAnswers {
                gate.await()
                careStatusResponse(inviteCode = "ABC123")
            }
        }
        val viewModel = newViewModel(sessionManager, careStore = careStore)
        advanceUntilIdle()

        viewModel.createInvitation()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCreatingInvite)

        currentSlot[0] = null
        sessionFlow.value = null
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.session)
        assertEquals(CareRuntimeState.Disconnected, state.careState)
        assertFalse(state.isCreatingInvite)
        assertNull(state.infoMessage)
    }

    @Test
    fun `createInvitation failure surfaces a real message`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val careStore = mockCareStore {
            coEvery {
                createInvitation(any(), any(), any(), any(), any(), any(), any())
            } throws RuntimeException("server error")
        }
        val viewModel = newViewModel(sessionManager, careStore = careStore)
        advanceUntilIdle()

        viewModel.createInvitation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isCreatingInvite)
        // Was asserting the RAW exception message. That assertion pinned a real
        // defect: Supabase/Ktor messages embed the request URL, the
        // `Authorization: Bearer ...` header and the apikey, and they rendered
        // verbatim as user-visible error text (seen on a real device). The UI must
        // show app copy; the raw cause is logged only.
        assertEquals("Unable to create an invite right now.", state.error)
    }

    @Test
    fun `acceptInvitation with a blank code sets a local error and fires a haptic error without calling the store`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val careStore = mockCareStore()
        val viewModel = newViewModel(sessionManager, careStore = careStore, hapticManager = hapticManager)
        advanceUntilIdle()

        viewModel.acceptInvitation()
        advanceUntilIdle()

        assertEquals("Please enter the full 6-character code.", viewModel.uiState.value.error)
        verify(exactly = 1) { hapticManager.error() }
        coVerify(exactly = 0) { careStore.acceptInvitation(any(), any()) }
    }

    @Test
    fun `acceptInvitation success clears the code field and surfaces the real accepted message`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val careStore = mockCareStore {
            coEvery { acceptInvitation("ABC123", "user-1") } returns careStatusResponse()
        }
        val viewModel = newViewModel(sessionManager, careStore = careStore)
        advanceUntilIdle()
        viewModel.onAcceptInviteCodeChanged("abc123")

        viewModel.acceptInvitation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.acceptInviteCode)
        assertEquals("The invite was accepted.", state.infoMessage)
    }

    @Test
    fun `acceptInvitation failure fires a haptic error and surfaces a real message`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val careStore = mockCareStore {
            coEvery { acceptInvitation("ABC123", "user-1") } throws RuntimeException("invalid code")
        }
        val viewModel = newViewModel(sessionManager, careStore = careStore, hapticManager = hapticManager)
        advanceUntilIdle()
        viewModel.onAcceptInviteCodeChanged("abc123")

        viewModel.acceptInvitation()
        advanceUntilIdle()

        // Was asserting the RAW exception message. That assertion pinned a real
        // defect: Supabase/Ktor messages embed the request URL, the
        // `Authorization: Bearer ...` header and the apikey, and they rendered
        // verbatim as user-visible error text (seen on a real device). The UI must
        // show app copy; the raw cause is logged only.
        assertEquals("Unable to accept this invite right now.", viewModel.uiState.value.error)
        verify(exactly = 1) { hapticManager.error() }
    }

    @Test
    fun `cancelInvitation is a no-op when the current careState is not a pending invitation`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val careStore = mockCareStore(careState = CareRuntimeState.Disconnected)
        val viewModel = newViewModel(sessionManager, careStore = careStore)
        advanceUntilIdle()

        viewModel.cancelInvitation()
        advanceUntilIdle()

        coVerify(exactly = 0) { careStore.cancelInvitation(any(), any()) }
        assertFalse(viewModel.uiState.value.isCancellingInvite)
    }

    @Test
    fun `cancelInvitation succeeds through the real store call when careState is a pending invitation`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val careStore = mockCareStore(careState = CareRuntimeState.PendingInvitation(invitation(id = "invite-1"))) {
            coEvery { cancelInvitation("invite-1", "user-1") } returns careStatusResponse(inviteCode = null)
        }
        val viewModel = newViewModel(sessionManager, careStore = careStore)
        advanceUntilIdle()

        viewModel.cancelInvitation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isCancellingInvite)
        assertEquals("That invite has been closed. Nothing was shared.", state.infoMessage)
    }

    @Test
    fun `removePartnership fires a medium haptic impact and clears its busy flag on success`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val careStore = mockCareStore {
            coEvery { leavePartnership("partnership-1", "user-1") } returns careStatusResponse(inviteCode = null)
        }
        val viewModel = newViewModel(sessionManager, careStore = careStore, hapticManager = hapticManager)
        advanceUntilIdle()

        viewModel.removePartnership("partnership-1")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRemovingPartnership)
        verify(exactly = 1) { hapticManager.impact(HapticImpact.MEDIUM) }
    }

    @Test
    fun `updatePermissions success fires a haptic success and invokes onComplete true`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val careStore = mockCareStore {
            coEvery {
                updatePermissions("partnership-1", ParentChildPermissions.default, any(), "user-1")
            } returns careStatusResponse(inviteCode = null)
        }
        val viewModel = newViewModel(sessionManager, careStore = careStore, hapticManager = hapticManager)
        advanceUntilIdle()
        var completedWith: Boolean? = null

        viewModel.updatePermissions("partnership-1", ParentChildPermissions.default) { completedWith = it }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSavingPermissions)
        assertEquals(true, completedWith)
        verify(exactly = 1) { hapticManager.success() }
    }

    @Test
    fun `updatePermissions failure fires a haptic error, surfaces a real message, and invokes onComplete false`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val careStore = mockCareStore {
            coEvery {
                updatePermissions("partnership-1", ParentChildPermissions.default, any(), "user-1")
            } throws RuntimeException()
        }
        val viewModel = newViewModel(sessionManager, careStore = careStore, hapticManager = hapticManager)
        advanceUntilIdle()
        var completedWith: Boolean? = null

        viewModel.updatePermissions("partnership-1", ParentChildPermissions.default) { completedWith = it }
        advanceUntilIdle()

        assertEquals(
            "The latest permission change was not saved. Please try again.",
            viewModel.uiState.value.error,
        )
        assertEquals(false, completedWith)
        verify(exactly = 1) { hapticManager.error() }
    }

    @Test
    fun `onAcceptInviteCodeChanged truncates to 6 characters, uppercases, and clears any prior error or info message`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val hapticManager = mockk<AndroidHapticManager>(relaxed = true)
        val viewModel = newViewModel(sessionManager, hapticManager = hapticManager)
        advanceUntilIdle()
        // Get a real error and infoMessage onto the state first so we can prove
        // typing actually clears both, not just that the code field updates.
        viewModel.acceptInvitation()
        advanceUntilIdle()
        assertEquals("Please enter the full 6-character code.", viewModel.uiState.value.error)

        viewModel.onAcceptInviteCodeChanged("abc1234xyz")

        val state = viewModel.uiState.value
        assertEquals("ABC123", state.acceptInviteCode)
        assertNull(state.error)
        assertNull(state.infoMessage)
    }

    @Test
    fun `every care action is a silent no-op with no session, matching the store never being touched`() = runTest {
        val sessionManager = mockSessionManager(null)
        val careStore = mockCareStore()
        val viewModel = newViewModel(sessionManager, careStore = careStore)
        advanceUntilIdle()

        viewModel.refresh()
        viewModel.createInvitation()
        viewModel.onAcceptInviteCodeChanged("ABC123")
        viewModel.acceptInvitation()
        viewModel.cancelInvitation()
        viewModel.removePartnership("partnership-1")
        viewModel.updatePermissions("partnership-1", ParentChildPermissions.default) {}
        advanceUntilIdle()

        // `refresh()`'s own null-session guard (`sessionManager.current ?: return`)
        // still lets the ui state field update happen for `onAcceptInviteCodeChanged`
        // (a pure local field, no session needed) but every real mutation on the
        // shared store must be skipped entirely -- there's no signed-in user id to
        // attribute the change to.
        coVerify(exactly = 0) { careStore.refresh(any()) }
        coVerify(exactly = 0) { careStore.createInvitation(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { careStore.acceptInvitation(any(), any()) }
        coVerify(exactly = 0) { careStore.cancelInvitation(any(), any()) }
        coVerify(exactly = 0) { careStore.leavePartnership(any(), any()) }
        coVerify(exactly = 0) { careStore.updatePermissions(any(), any(), any(), any()) }
        assertEquals(CareRuntimeState.Disconnected, viewModel.uiState.value.careState)
    }

    @Test
    fun `removePartnership is a no-op re-entry guard while a removal is already in flight`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val gate = CompletableDeferred<Unit>()
        val careStore = mockCareStore {
            coEvery { leavePartnership(any(), any()) } coAnswers {
                gate.await()
                careStatusResponse(inviteCode = null)
            }
        }
        val viewModel = newViewModel(sessionManager, careStore = careStore)
        advanceUntilIdle()

        viewModel.removePartnership("partnership-1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRemovingPartnership)

        viewModel.removePartnership("partnership-1")
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { careStore.leavePartnership(any(), any()) }
    }

    @Test
    fun `updatePermissions is a no-op re-entry guard while a save is already in flight`() = runTest {
        val testSession = sessionContext()
        val sessionManager = mockSessionManager(testSession)
        val gate = CompletableDeferred<Unit>()
        val careStore = mockCareStore {
            coEvery { updatePermissions(any(), any(), any(), any()) } coAnswers {
                gate.await()
                careStatusResponse(inviteCode = null)
            }
        }
        val viewModel = newViewModel(sessionManager, careStore = careStore)
        advanceUntilIdle()

        viewModel.updatePermissions("partnership-1", ParentChildPermissions.default) {}
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSavingPermissions)

        var secondCompletion: Boolean? = null
        viewModel.updatePermissions("partnership-1", ParentChildPermissions.default) { secondCompletion = it }
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { careStore.updatePermissions(any(), any(), any(), any()) }
        // The guard returns before ever invoking the callback for the second
        // (ignored) call -- proving it's a silent no-op, not a queued retry.
        assertNull(secondCompletion)
    }
}
