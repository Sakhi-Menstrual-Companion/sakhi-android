package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiTertiaryLabel

/**
 * Single pinned-bottom footer, a faithful port of iOS's `SakhiFooter.swift`
 * (`01-iOS/SakhiApp/DesignSystem/SakhiFooter.swift`). iOS's own header comment is
 * the spec this reproduces:
 *
 * ```
 * Layout (always):
 *   [top padding]
 *   [primary button]           <- never moves, regardless of secondary
 *   [secondary OR placeholder] <- real button when present, invisible spacer when absent
 *   [note text, if any]
 *   [bottom padding]
 * ```
 *
 * The point of the reserved slot: the primary button's Y position must be
 * identical whether or not a secondary action exists, so it never jumps between
 * steps. iOS hard-codes a 44pt secondary box for exactly this; so does this.
 * Android previously had no shared footer at all -- every screen hand-rolled its
 * own button placement, which is why the primary button shifted around.
 *
 * Spacing tokens map 1:1 to iOS (both read the same shared `DesignTokens`):
 * `screenHorizontal 24 -> space6`, `ml 20 -> space5`, `xxl 32 -> space8`.
 *
 * @param showSecondarySlot false for genuinely single-action screens, matching
 *   iOS's `showTopSpacer = false` (omits the reserved slot entirely rather than
 *   leaving dead space).
 */
@Composable
fun SakhiFooter(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
    note: String? = null,
    showSecondarySlot: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // No fill of its own. It used to paint `colorScheme.background`, which
            // happens to match the onboarding pages but NOT any host that paints its own
            // surface -- inside the Care sheet that showed as a white band under the
            // buttons while the page above it was pink. iOS's footer is simply part of
            // the page. Every caller lays the footer out below its content rather than
            // over it, so there is nothing to hide behind an opaque fill.
            .navigationBarsPadding()
            .padding(bottom = SakhiSpacing.space5),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space6)
                .padding(top = SakhiSpacing.space5),
        ) {
            PrimaryButton(
                text = primaryLabel,
                onClick = onPrimaryClick,
                enabled = primaryEnabled,
                modifier = Modifier.fillMaxWidth(),
            )

            // Reserved secondary slot -- fixed 44dp box so the primary above it
            // never shifts, exactly as iOS does. When there is no secondary
            // action the box stays empty rather than collapsing.
            if (showSecondarySlot) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SakhiSpacing.space8)
                        .height(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (secondaryLabel != null && onSecondaryClick != null) {
                        TextButton(
                            onClick = onSecondaryClick,
                            enabled = secondaryEnabled,
                        ) {
                            Text(
                                text = secondaryLabel,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = sakhiSecondaryLabel(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space8)
                    .padding(top = SakhiSpacing.space4),
            )
        }
    }
}
