package team.sakhi.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import team.sakhi.android.designsystem.SakhiRadius
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSystemBackground

/**
 * Shared card shell for the AI subscreens, Care, App Integration and Reports.
 *
 * The name is a leftover: despite it, this is **not** a port of iOS's `glassCard()`
 * modifier. That modifier has zero call sites in the iOS app — `GlassCard.swift` is dead
 * code there — and the real iOS screens behind every one of this composable's callers
 * (`SakhiAIInfoView`, `SakhiAIStarredView`, `SakhiAIMediaView`, the Care partner cards,
 * the report pages) all fill their cards with plain `DS.Colors.systemBackground`. Home's
 * genuinely translucent aurora cards are a separate thing and use `HomeScreen.kt`'s own
 * private `HomeGlassCard`, so they are unaffected by this.
 *
 * The fill used to be `colorScheme.surface.copy(alpha = 0.16f)` — a 16% wash of brand
 * `lightPink`. In light that read as a pale pink tint instead of iOS's white card; in
 * dark it was 16% of `#2C1A22` over a dark page, which is very nearly nothing, so the
 * cards effectively disappeared and their contents floated loose on the background.
 * `sakhiSystemBackground()` is the exact token iOS uses: white in light, `#1C1C1E` in
 * dark.
 *
 * `contentColor` is set from `sakhiLabel()` rather than left to Material's `onSurface`,
 * so text inside a card follows the same label ladder as text outside one.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(SakhiRadius.xxl),
        color = sakhiSystemBackground(),
        contentColor = sakhiLabel(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(SakhiSpacing.space5),
            content = content,
        )
    }
}
