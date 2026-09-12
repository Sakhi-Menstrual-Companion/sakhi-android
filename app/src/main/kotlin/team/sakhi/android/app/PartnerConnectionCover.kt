package team.sakhi.android.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import team.sakhi.android.R
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSystemBackground
import team.sakhi.android.ui.sakhiPressFeedback

/**
 * The gate that closes over a care partner's app while the connection is gone.
 *
 * Her own side of Sakhi is offline-first on purpose and has no connectivity banner at all.
 * A partner's side is the opposite: it is a live view of someone else's health, and showing
 * yesterday's numbers as if they were today's is the one thing it must never do. Karan asked
 * for this on 2026-09-13, the same day a partner's Home was found drawing a cached copy of
 * her logs from days earlier.
 *
 * Drawn as a dialog rather than an in-tree overlay so it sits above whatever is open,
 * including a sheet. It cannot be dismissed by back or by tapping outside: it clears itself
 * the moment the connection returns, and nothing else would be honest.
 */
@Composable
fun PartnerConnectionCover(visible: Boolean, onRetry: () -> Unit) {
    if (!visible) return

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        // Entered here rather than on `visible`, so the panel slides up with the dialog
        // instead of appearing already in place.
        var raised by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { raised = true }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = raised,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = SakhiRadius.bottomSheet, topEnd = SakhiRadius.bottomSheet))
                        .background(sakhiSystemBackground())
                        .navigationBarsPadding()
                        .padding(horizontal = SakhiSpacing.space6, vertical = SakhiSpacing.space8),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Box(modifier = Modifier.height(SakhiSpacing.space5))
                    Text(
                        text = stringResource(R.string.partner_offline_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Box(modifier = Modifier.height(SakhiSpacing.space2))
                    Text(
                        text = stringResource(R.string.partner_offline_message),
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = sakhiSecondaryLabel(),
                        textAlign = TextAlign.Center,
                    )
                    Box(modifier = Modifier.height(SakhiSpacing.space6))
                    RetryPill(onClick = onRetry)
                }
            }
        }
    }
}

@Composable
private fun RetryPill(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .sakhiPressFeedback(interaction, pressedAlpha = 0.85f, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.partner_offline_retry),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}
