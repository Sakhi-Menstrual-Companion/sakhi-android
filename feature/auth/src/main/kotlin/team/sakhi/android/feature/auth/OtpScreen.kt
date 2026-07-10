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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.ui.KeyboardSafeScaffold
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

    KeyboardSafeScaffold(
        body = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SakhiSpacing.space6),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            ) {
                Text(
                    text = stringResource(R.string.auth_otp_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.auth_otp_subtitle),
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
            }
        },
        footer = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space3),
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
            ) {
                TextButton(
                    onClick = viewModel::resendOtp,
                    enabled = !uiState.isSendingOtp && !uiState.isVerifyingOtp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (uiState.isSendingOtp) {
                            stringResource(R.string.auth_otp_resending)
                        } else {
                            stringResource(R.string.auth_otp_resend_cta)
                        },
                    )
                }

                PrimaryButton(
                    text = if (uiState.isVerifyingOtp) {
                        stringResource(R.string.auth_otp_verifying)
                    } else {
                        stringResource(R.string.auth_phone_continue)
                    },
                    onClick = { viewModel.verifyOtp(uiState.otpDigits) },
                    enabled = !uiState.isSendingOtp && !uiState.isVerifyingOtp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
