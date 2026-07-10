package team.sakhi.android.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = if (showSystemDragHandle) {
            { BottomSheetDefaults.DragHandle() }
        } else {
            null
        },
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        content()
    }
}
