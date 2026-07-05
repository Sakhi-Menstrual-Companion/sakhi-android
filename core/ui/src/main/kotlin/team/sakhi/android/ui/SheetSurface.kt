package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing

/**
 * Shared "presented as a sheet" shell — Profile/Care/Chat are each a real
 * `.sheet(...).presentationDetents([.large]).presentationDragIndicator(.hidden)`
 * on iOS (rounded top corners, no drag handle — they rely on their own
 * in-header close button instead). Logging is the one exception: its sheet
 * config uses `.presentationDragIndicator(.visible)` (see `HomeView.swift`
 * `makeLoggingSheetConfiguration`), so it passes `showDragHandle = true`.
 * Android's nav graph currently pushes all of these as plain full-screen
 * destinations (a deliberate, tracked "wiring first" simplification — see
 * `HomeNavHost.kt`'s doc comment); this wrapper gives them the matching
 * rounded-top look without the bigger nav-architecture change a real
 * modal-sheet destination type would need.
 */
@Composable
fun SheetSurface(
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = SakhiRadius.bottomSheet, topEnd = SakhiRadius.bottomSheet),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showDragHandle) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = SakhiSpacing.space3)
                            .width(38.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(SakhiRadius.full)),
                    )
                }
            }
            content()
        }
    }
}
