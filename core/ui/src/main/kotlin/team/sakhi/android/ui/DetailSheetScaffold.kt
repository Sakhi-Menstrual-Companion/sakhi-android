package team.sakhi.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiFontSize
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel

/**
 * Shared profile/detail sheet shell: drag handle, back header, divider, and a
 * padded scroll body. This matches the reusable profile settings wrappers used
 * on iOS and reduces repeated screen-root boilerplate on Android.
 */
@Composable
fun DetailSheetScaffold(
    title: String? = null,
    subtitle: String? = null,
    headerIcon: ImageVector? = null,
    headerIconTint: Color = Color.Unspecified,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = true,
    scrollable: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(SakhiSpacing.space5),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(SakhiSpacing.space5),
    trailingHeaderContent: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    SheetSurface(
        modifier = modifier,
        showDragHandle = showDragHandle,
        backgroundBrush = profilePageBackgroundBrush(),
    ) {
        // One nav bar for every sheet (iOS `DSNavBar`), replacing the hand-rolled header
        // row this used to draw. That row used its own paddings (24/20/8 here vs the nav
        // bar's 24/20/8 plus proper button sizing), so the back button sat tight against
        // the sheet's rounded top edge and read as clipped -- the same complaint on every
        // sheet that was not Profile.
        //
        // When the body renders a big centred `headerIcon` block, the title belongs to
        // that block, so the bar carries the button alone; iOS's own alignment rule then
        // left-aligns a lone title, which is why passing it here would be wrong.
        SakhiNavBar(
            onBack = onBack,
            title = title?.takeIf { headerIcon == null && it.isNotBlank() },
            trailing = trailingHeaderContent,
        )
        if (headerIcon == null) {
            HorizontalDivider()
        }

        val bodyModifier = Modifier
            .fillMaxSize()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(contentPadding)

        Column(
            modifier = bodyModifier,
            verticalArrangement = verticalArrangement,
        ) {
            if (headerIcon != null) {
                val resolvedTint = if (headerIconTint == Color.Unspecified) {
                    MaterialTheme.colorScheme.primary
                } else {
                    headerIconTint
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SakhiSpacing.space6),
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = resolvedTint,
                        modifier = Modifier.size(52.dp),
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        title?.takeIf { it.isNotBlank() }?.let { titleText ->
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = SakhiFontSize.xxxl,
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        subtitle?.takeIf { it.isNotBlank() }?.let { subtitleText ->
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = SakhiFontSize.base),
                                color = sakhiSecondaryLabel(),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = SakhiSpacing.space4),
                            )
                        }
                    }
                }
            } else {
                subtitle?.takeIf { it.isNotBlank() }?.let { subtitleText ->
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = sakhiSecondaryLabel(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            content()
        }
    }
}
