package team.sakhi.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.delay
import team.sakhi.android.designsystem.SakhiSpacing

/**
 * Renders `ToastManager.current` as a floating capsule pinned to the top of the
 * window -- port of iOS `ToastView`/`ToastCapsule`. Mount exactly once, at the
 * app root (`RootNavHost`), same as iOS mounts one `ToastView` window-level.
 */
@Composable
fun ToastHost() {
    val toast by ToastManager.current.collectAsState()

    LaunchedEffect(toast) {
        val current = toast ?: return@LaunchedEffect
        delay(current.durationMs)
        if (ToastManager.current.value == current) {
            ToastManager.dismiss()
        }
    }

    val topOffsetProvider = remember { TopCenterPositionProvider }

    toast?.let { message ->
        Popup(popupPositionProvider = topOffsetProvider) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it },
            ) {
                ToastCapsule(message = message)
            }
        }
    }
}

private object TopCenterPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): androidx.compose.ui.unit.IntOffset {
        val x = (windowSize.width - popupContentSize.width) / 2
        val y = 56
        return androidx.compose.ui.unit.IntOffset(x, y)
    }
}

@Composable
private fun ToastCapsule(message: ToastMessage) {
    val accent = when (message.type) {
        ToastType.SUCCESS -> Color(0xFF34C759)
        ToastType.ERROR -> Color(0xFFFF3B30)
        ToastType.WARNING -> Color(0xFFFF9F0A)
        ToastType.INFO -> Color(0xFFFF5A8A)
    }
    val icon = when (message.type) {
        ToastType.SUCCESS -> Icons.Filled.CheckCircle
        ToastType.ERROR -> Icons.Filled.Error
        ToastType.WARNING -> Icons.Filled.Warning
        ToastType.INFO -> Icons.Filled.Info
    }

    Box(
        modifier = Modifier
            .wrapContentSize()
            .background(Color(0xFF1C1C1E), CircleShape)
            .padding(horizontal = SakhiSpacing.space4, vertical = SakhiSpacing.space2)
            .pointerInput(Unit) { detectTapGestures(onTap = { ToastManager.dismiss() }) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.width(16.dp))
            Text(message.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            if (message.actionLabel != null && message.onAction != null) {
                Text(
                    text = message.actionLabel,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            message.onAction.invoke()
                            ToastManager.dismiss()
                        })
                    },
                )
            }
        }
    }
}
