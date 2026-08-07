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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing

/**
 * Shared inner "presented as a sheet" surface. `SakhiModalSheet` owns the
 * outer modal behavior; this wrapper owns the rounded top corners and optional
 * in-surface drag indicator that iOS uses across Home-owned sheets. Logging is
 * the one exception that wants the indicator visible (`HomeView.swift`
 * `makeLoggingSheetConfiguration`), so it passes `showDragHandle = true`.
 *
 * `backgroundBrush` is an escape hatch for the one real caller that needs a
 * non-solid fill (`DetailSheetScaffold`'s profile-family phase gradient,
 * matching iOS's `profileStaticPageBackground()`) without touching every
 * other caller's plain `colorScheme.surface` fill (Chat, Logging, the Home
 * overlay sheet). Defaults to `null`, which reproduces the exact prior
 * behavior byte for byte.
 */
@Composable
fun SheetSurface(
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = false,
    /**
     * Solid fill, when [backgroundBrush] is not used.
     *
     * `colorScheme.background` is `#F8F2F4`, the same value every one of these pages
     * resolves to on iOS in light mode. Both of iOS's page-background modifiers --
     * `phasePageBackground()` (Logging, Activity, the AI sheets) and
     * `profileStaticPageBackground()` (Profile, Chat, Care, the onboarding steps) -- fall
     * back to `DS.Colors.background` in light mode and differ only in dark, where they
     * paint a phase gradient. See `PhaseBackground.swift`, whose header states exactly
     * that.
     *
     * This defaulted to `colorScheme.surface` (brand `lightPink`, `#F8E5EC`) on the belief
     * that iOS filled these sheets that way. It does not: the Logging sheet in particular
     * read visibly pinker than iOS's, and white cards on it barely separated from the
     * page. Profile had already been given the correct value by hand; making it the
     * default fixes Logging and Chat too.
     */
    color: Color = MaterialTheme.colorScheme.background,
    backgroundBrush: Brush? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = SakhiRadius.bottomSheet, topEnd = SakhiRadius.bottomSheet)

    @Composable
    fun sheetBody() {
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

    if (backgroundBrush != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(shape)
                .background(backgroundBrush),
        ) {
            sheetBody()
        }
    } else {
        Surface(
            modifier = modifier.fillMaxSize(),
            shape = shape,
            color = color,
        ) {
            sheetBody()
        }
    }
}
