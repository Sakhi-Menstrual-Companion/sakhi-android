package team.sakhi.android.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.OtpField
import team.sakhi.android.ui.PrimaryButton
import team.sakhi.auth.AuthResultWithAccount

/**
 * OTP verification shell over the shared auth repository and account classifier.
 * Android only renders state and forwards actions to [AuthViewModel].
 */
@Composable
fun OtpScreen(
    phone: String? = null,
    onOtpVerified: (AuthResultWithAccount) -> Unit = {},
    viewModel: AuthViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    uiState.verifiedAuthResult?.let { result ->
        LaunchedEffect(result.userId, result.accessToken) {
            onOtpVerified(result)
            viewModel.consumeVerifiedAuthResult()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(SakhiSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
    ) {
        Text(
            text = "Our Secret Code",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Enter the code we sent to confirm it's you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OtpField(
            value = uiState.otpDigits,
            onValueChange = viewModel::onOtpChanged,
            isError = uiState.otpError != null,
            errorText = uiState.otpError,
            autoFocus = true,
            cellSpacing = SakhiSpacing.space2,
            cellWidth = SakhiSpacing.space12,
            cellHeight = SakhiSpacing.space12 + SakhiSpacing.space3,
            activeBorderWidth = SakhiSpacing.space1 / 2,
            modifier = Modifier.padding(top = SakhiSpacing.space6),
            onComplete = viewModel::verifyOtp,
        )

        TextButton(
            onClick = viewModel::resendOtp,
            enabled = !uiState.isSendingOtp && !uiState.isVerifyingOtp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SakhiSpacing.space1),
        ) {
            Text(
                text = if (uiState.isSendingOtp) {
                    "Resending..."
                } else {
                    "Didn't get the code? Resend"
                },
            )
        }

        PrimaryButton(
            text = if (uiState.isVerifyingOtp) "Verifying..." else "Continue",
            onClick = { viewModel.verifyOtp(uiState.otpDigits) },
            enabled = !uiState.isSendingOtp && !uiState.isVerifyingOtp,
            modifier = Modifier.padding(top = SakhiSpacing.space3),
        )
    }
}
