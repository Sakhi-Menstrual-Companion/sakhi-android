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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.sakhiPageBackgroundBrush
import team.sakhi.android.designsystem.SakhiSpacing

/**
 * Shared inner "presented as a sheet" surface. `SakhiModalSheet` owns the
 * outer modal behavior; this wrapper owns the rounded top corners and optional
 * in-surface drag indicator that iOS uses across Home-owned sheets.
 *
 * `showDragHandle` draws the grabber INSIDE this surface, which is where it has to be:
 * `SakhiModalSheet` sets `containerColor = Color.Transparent`, so the sheet's own
 * container is invisible and this composable draws the visible rounded card. Material's
 * `dragHandle` renders in that invisible container, i.e. ABOVE this card, where it reads
 * as a grabber floating on the dimmed background rather than belonging to the sheet.
 *
 * So: sheets pass `showDragHandle = true` here, and the host passes
 * `showSystemDragHandle = false`. Turning both on gives two grabbers, one of them
 * detached from the sheet.
 *
 * The background is `sakhiPageBackgroundBrush()`, the port of iOS's
 * `profileStaticPageBackground()`: flat `DS.Colors.background` in light, the follicular
 * phase gradient in dark. A sheet is presented in its own window above the app, so it
 * does NOT inherit the page background `SakhiTheme` paints at the root -- it has to paint
 * it again itself, which is exactly what iOS does (`SakhiAIChatView`, `HomeLoggingSheet`
 * and every `ProfileSettingsDetailView` each apply a page-background modifier of their
 * own). Before this, sheets fell back to a flat fill and so were pure black in dark.
 */
@Composable
fun SheetSurface(
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = false,
    backgroundBrush: Brush = sakhiPageBackgroundBrush(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = SakhiRadius.bottomSheet, topEnd = SakhiRadius.bottomSheet)

    @Composable
    fun sheetBody() {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showDragHandle) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // iOS `dragHandle`: 38x4 capsule with `.padding(.top, 10)` and
                    // `.padding(.bottom, 10)`. Android used 12 either side, which is
                    // what pushed the header down away from the grabber.
                    Box(
                        modifier = Modifier
                            .padding(vertical = DragHandleVerticalPadding)
                            .width(38.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(SakhiRadius.full)),
                    )
                }
            }
            content()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(backgroundBrush),
    ) {
        sheetBody()
    }
}

/** iOS `HomeLoggingSheet.dragHandle`: 10pt above and below the capsule. */
private val DragHandleVerticalPadding = 10.dp
