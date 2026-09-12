package team.sakhi.android.feature.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sakhi.auth.AccountState

/**
 * Pins which verified accounts owe her the `PartnerConversionWarning` screen before the
 * partner path deletes anything.
 *
 * This used to read `accountState is AccountState.ExistingComplete`, reusing the flag that
 * decides whether to skip the remaining profile steps. Two real existing-account cases were
 * not `ExistingComplete` and so were never warned at all.
 */
class PartnerConversionAccountStateTest {

    private val userId = "user-1"

    @Test
    fun `a completed account is warned`() {
        assertTrue(AccountState.ExistingComplete(userId).isExistingOwnAccount())
    }

    @Test
    fun `an offline-first completed account is warned, which ExistingComplete never covered`() {
        // She finished onboarding on this device without an account, so her cycle history is
        // definitely on it. This is the case that most needed asking.
        assertTrue(AccountState.LocalOnlyComplete(userId).isExistingOwnAccount())
    }

    @Test
    fun `an incomplete account is warned, because a failed profile lookup also lands here`() {
        // `AccountClassifier.classify` returns `ExistingIncomplete` from its own `catch` when
        // the remote profile lookup throws, so a real completed account on a flaky connection
        // is indistinguishable from a genuinely-unfinished signup. One declinable screen is
        // the cheap side of that; the other side deletes health data without asking.
        assertTrue(AccountState.ExistingIncomplete(userId).isExistingOwnAccount())
    }

    @Test
    fun `a brand-new account is not warned`() {
        assertFalse(
            "a new partner has nothing of her own to delete, so the warning would only confuse her",
            AccountState.NewAccount.isExistingOwnAccount(),
        )
    }

    @Test
    fun `an expired session is not warned here, it re-enters through newUser`() {
        assertFalse(AccountState.SessionExpired(previousUserId = userId).isExistingOwnAccount())
    }
}
