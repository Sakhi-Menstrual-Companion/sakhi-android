package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch

/**
 * Shared modal-sheet host for Android surfaces that mirror iOS `.sheet(...)` presentation.
 *
 * It used to wrap Material3's `ModalBottomSheet`. It now wraps [SakhiSheetLayer], so every
 * sheet in the app presents, drags, resists and dismisses with the SAME motion Home's
 * calendar has -- iOS's own sheet spring, iOS's release rules and iOS's rubber band. See
 * `SakhiSheetPresentation.kt` for what that motion is and why it is built the way it is.
 *
 * The call shape is unchanged on purpose: `onDismissRequest` plus a `sheetState` is all any
 * existing call site passes, so none of them had to change. The extra parameters exist for
 * the emergency flow, which was reaching past this host to a raw `ModalBottomSheet` to get
 * its own colour, corners, grabber and "do not dim the map" scrim.
 *
 * ── Why this is still presented in a window ────────────────────────────────────────
 *
 * The motion itself is in-tree, and Home's calendar uses it that way. This host is not,
 * because the sheets it raises come from deep inside `Column`s and scroll containers --
 * seventeen `SakhiAlertSheet` call sites among them -- where a full-size in-tree overlay
 * would take layout space from its siblings and be drawn under anything composed after it.
 * Material's `ModalBottomSheet` was a window for exactly that reason. Keeping that one
 * property is what lets every call site stay exactly where it is.
 */
@Composable
fun SakhiModalSheet(
    onDismissRequest: () -> Unit,
    sheetState: SakhiSheetState = rememberSakhiModalSheetState(),
    showSystemDragHandle: Boolean = false,
    /**
     * Left transparent by default, because the sheets in this app paint their own card
     * (`SheetSurface`) inside the content. See `SheetSurface`'s own note on why.
     */
    containerColor: Color = Color.Transparent,
    shape: Shape = RectangleShape,
    /**
     * Null for a sheet that must not dim what is behind it -- the emergency flow, where
     * Karan's standing note is that the map never dims behind a sheet. A null scrim still
     * takes taps outside the sheet, exactly as Material's transparent scrim did.
     */
    scrimColor: Color? = MaterialTheme.colorScheme.scrim.copy(alpha = ScrimOpacity),
    dismissOnScrimTap: Boolean = true,
    dragHandle: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)

    // Back, and anything else the host closes the sheet with. The sheet animates out first
    // and the caller is told once it has landed, so dropping this out of composition in
    // response cannot cut the exit off at its first frame.
    fun dismissWithSheetMotion() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            sheetState.hide()
            currentOnDismissRequest()
        }
    }

    Dialog(
        onDismissRequest = ::dismissWithSheetMotion,
        properties = DialogProperties(
            // Fills the window, and picks Compose's `DialogWindowTheme`, which deliberately
            // does not dim -- the scrim inside the layer is ours and fades with the sheet.
            usePlatformDefaultWidth = false,
            // Edge to edge, and the reason `imePadding()` inside the sheet sees real IME
            // insets. Material3's own `ModalBottomSheetDialogWrapper` sets the same pair,
            // and the sheets with a text field in them (Chat, the country picker's search,
            // the emergency thread) depend on it.
            decorFitsSystemWindows = false,
            // Routed through the animated dismiss above rather than closing on the spot. A
            // `BackHandler` composed inside `content` still wins over this, because it
            // registers later on the same dispatcher -- which is what keeps Profile's
            // sub-screen back behaviour working.
            dismissOnBackPress = true,
            // The scrim inside the layer owns this, so the tap can run the same exit
            // animation and so a sheet with no scrim colour still takes the tap.
            dismissOnClickOutside = false,
        ),
    ) {
        // Without this the window fades itself in and out underneath our own motion, which
        // reads as the sheet arriving twice.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { window ->
                if (window.attributes.windowAnimations != 0) {
                    // 0 is the platform's "this window has no animation style".
                    window.attributes = window.attributes.apply { windowAnimations = 0 }
                }
            }
        }

        SakhiSheetLayer(
            // Already animated out by the time this runs, so the caller is free to unmount.
            onDismiss = {
                dismissing = true
                currentOnDismissRequest()
            },
            state = sheetState,
            scrimColor = scrimColor,
            dismissOnScrimTap = dismissOnScrimTap,
            sheetModifier = Modifier
                .clip(shape)
                .background(containerColor),
        ) {
            when {
                dragHandle != null -> dragHandle()
                showSystemDragHandle -> SakhiSheetGrabber()
                else -> Unit
            }
            content()
        }
    }
}

/**
 * The grabber Material used to draw in the sheet's own container, kept for the
 * `showSystemDragHandle` flag. Sheets that draw their own (`SheetSurface`, the emergency
 * flow's `EmergencySheetGrabber`) pass `dragHandle` instead.
 */
@Composable
private fun SakhiSheetGrabber() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(GrabberRowHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(top = GrabberTopPadding)
                .size(width = GrabberWidth, height = GrabberHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/** Material's own scrim opacity, so migrated sheets dim by exactly as much as before. */
private const val ScrimOpacity = 0.32f

private val GrabberRowHeight = 22.dp
private val GrabberTopPadding = 10.dp
private val GrabberWidth = 36.dp
private val GrabberHeight = 4.dp
