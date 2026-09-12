package team.sakhi.android.platform

import kotlinx.coroutines.runBlocking
import team.sakhi.platform.PlatformKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals

class SakhiFirebaseMessagingServiceTest {

    @Test
    fun cacheAndMaybeRegisterToken_cachesEvenWhenNoUserIsSignedIn() = runBlocking {
        val kvStore = PlatformKeyValueStore()
        var registerCalls = 0

        SakhiFirebaseMessagingService.cacheAndMaybeRegisterToken(
            token = "token-no-user",
            kvStore = kvStore,
            userId = null,
        ) { _, _ ->
            registerCalls += 1
            Result.success(Unit)
        }

        assertEquals("token-no-user", kvStore.get(AndroidNotificationReminderManager.PENDING_FCM_TOKEN))
        assertEquals(0, registerCalls)
    }

    /**
     * The token stays cached after a successful registration. It is this phone's token:
     * the next account to sign in here and sign-out both need it, and clearing it is what
     * left every account after the first one on a phone without pushes.
     */
    @Test
    fun cacheAndMaybeRegisterToken_keepsThisPhonesTokenAfterSuccessfulRegistration() = runBlocking {
        val kvStore = PlatformKeyValueStore()

        SakhiFirebaseMessagingService.cacheAndMaybeRegisterToken(
            token = "token-success",
            kvStore = kvStore,
            userId = "signed-in-user",
        ) { userId, token ->
            assertEquals("signed-in-user", userId)
            assertEquals("token-success", token)
            Result.success(Unit)
        }

        assertEquals("token-success", kvStore.get(AndroidNotificationReminderManager.PENDING_FCM_TOKEN))
    }

    @Test
    fun cacheAndMaybeRegisterToken_keepsPendingTokenAfterRegistrationFailure() = runBlocking {
        val kvStore = PlatformKeyValueStore()

        SakhiFirebaseMessagingService.cacheAndMaybeRegisterToken(
            token = "token-failure",
            kvStore = kvStore,
            userId = "signed-in-user",
        ) { _, _ ->
            Result.failure(IllegalStateException("network unavailable"))
        }

        assertEquals("token-failure", kvStore.get(AndroidNotificationReminderManager.PENDING_FCM_TOKEN))
    }
}
