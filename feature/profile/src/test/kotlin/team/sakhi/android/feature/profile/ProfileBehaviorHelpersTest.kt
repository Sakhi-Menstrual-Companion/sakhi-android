package team.sakhi.android.feature.profile

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sakhi.auth.AuthRepository
import team.sakhi.repositories.AccountRepository

class ProfileBehaviorHelpersTest {

    @Test
    fun `mergeEditedProfileName keeps the existing value when the submitted name is blank`() {
        assertEquals("Asha", mergeEditedProfileName("Asha", "   "))
    }

    @Test
    fun `mergeEditedProfileName trims and uses the submitted value when it is non-blank`() {
        assertEquals("Asha Rao", mergeEditedProfileName("Asha", "  Asha Rao  "))
    }

    @Test
    fun `deleteAccountAndSignOut succeeds only after local sign-out succeeds`() = runTest {
        val accountRepository = mockk<AccountRepository> {
            coEvery { deleteServerAccount() } returns Unit
        }
        val authRepository = mockk<AuthRepository> {
            coEvery { signOut() } returns Result.success(Unit)
        }

        val result = deleteAccountAndSignOut(accountRepository, authRepository)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { accountRepository.deleteServerAccount() }
        coVerify(exactly = 1) { authRepository.signOut() }
    }

    @Test
    fun `deleteAccountAndSignOut returns the sign-out failure after server deletion succeeds`() = runTest {
        val accountRepository = mockk<AccountRepository> {
            coEvery { deleteServerAccount() } returns Unit
        }
        val authRepository = mockk<AuthRepository> {
            coEvery { signOut() } returns Result.failure(RuntimeException("sign out failed"))
        }

        val result = deleteAccountAndSignOut(accountRepository, authRepository)

        assertEquals("sign out failed", result.exceptionOrNull()?.message)
        coVerify(exactly = 1) { accountRepository.deleteServerAccount() }
        coVerify(exactly = 1) { authRepository.signOut() }
    }

    @Test
    fun `deleteAccountAndSignOut does not attempt local sign-out when server deletion fails`() = runTest {
        val accountRepository = mockk<AccountRepository> {
            coEvery { deleteServerAccount() } throws RuntimeException("delete failed")
        }
        val authRepository = mockk<AuthRepository> {
            coEvery { signOut() } returns Result.success(Unit)
        }

        val result = deleteAccountAndSignOut(accountRepository, authRepository)

        assertEquals("delete failed", result.exceptionOrNull()?.message)
        coVerify(exactly = 1) { accountRepository.deleteServerAccount() }
        coVerify(exactly = 0) { authRepository.signOut() }
    }

    @Test
    fun `resetProfileData delegates to AuthRepository signOut`() = runTest {
        val authRepository = mockk<AuthRepository> {
            coEvery { signOut() } returns Result.success(Unit)
        }

        val result = resetProfileData(authRepository)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { authRepository.signOut() }
    }
}
