package team.sakhi.android.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import kotlinx.coroutines.launch

/**
 * Shared modal-sheet host for Android surfaces that mirror iOS `.sheet(...)`
 * presentation. It standardizes the large-detent-only behavior, transparent
 * outer container, and drag-handle visibility so individual features don't keep
 * hand-rolling slightly different sheet hosts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSakhiModalSheetState(
    skipPartiallyExpanded: Boolean = true,
): SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SakhiModalSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    showSystemDragHandle: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()

    fun dismissWithSheetMotion() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismissRequest()
        }
    }

    ModalBottomSheet(
        onDismissRequest = ::dismissWithSheetMotion,
        sheetState = sheetState,
        dragHandle = if (showSystemDragHandle) {
            { BottomSheetDefaults.DragHandle() }
        } else {
            null
        },
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        // Material's default applies the navigation-bar inset here, so every sheet in the
        // app stopped short of the bottom edge and the Home background showed through as
        // a strip under the content. iOS's sheets run to the edge -- `HomeCalendarSheet`
        // fills its background with `.ignoresSafeArea(edges: .bottom)`.
        //
        // Only the BOTTOM is dropped. Zeroing every side also pulled the sheet under the
        // status bar, where the nav bar's close button collided with the system icons --
        // iOS keeps its sheets below the status bar, so the top inset stays.
        contentWindowInsets = {
            BottomSheetDefaults.windowInsets.only(WindowInsetsSides.Top)
        },
    ) {
        content()
    }
}
