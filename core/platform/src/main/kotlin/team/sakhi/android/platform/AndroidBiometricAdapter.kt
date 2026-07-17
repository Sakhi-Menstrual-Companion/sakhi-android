package team.sakhi.android.platform

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import team.sakhi.platform.BiometricInterface
import team.sakhi.platform.BiometricResult
import kotlin.coroutines.resume

/**
 * Android actual for the shared KMM `BiometricInterface` port. Uses
 * [CurrentActivityHolder] because `BiometricPrompt` must be constructed against a
 * live `FragmentActivity`, and the shared interface's `authenticate(reason)` takes
 * no context/activity parameter (kept platform-agnostic on purpose).
 */
class AndroidBiometricAdapter(
    private val activityHolder: CurrentActivityHolder,
    private val appContext: Context,
) : BiometricInterface {

    override suspend fun canAuthenticate(): Boolean {
        val activity = activityHolder.current ?: return false
        val manager = BiometricManager.from(activity)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    override suspend fun authenticate(reason: String): BiometricResult {
        val activity = activityHolder.current
            ?: return BiometricResult.Failure(appContext.getString(R.string.platform_biometric_prompt_unavailable))

        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(BiometricResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!continuation.isActive) return
                    val result = if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        BiometricResult.Cancelled
                    } else {
                        BiometricResult.Failure(errString.toString())
                    }
                    continuation.resume(result)
                }

                override fun onAuthenticationFailed() {
                    // A single failed match (wrong finger) — the prompt stays open for
                    // retry, so this is not resolved here, only terminal outcomes are.
                }
            }

            val prompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(reason)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(activity.getString(R.string.platform_biometric_cancel))
                .build()

            prompt.authenticate(promptInfo)
        }
    }
}
