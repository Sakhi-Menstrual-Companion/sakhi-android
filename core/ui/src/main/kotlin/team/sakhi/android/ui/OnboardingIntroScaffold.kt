package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiSecondaryLabel

/**
 * The introduction page used across the app: account onboarding's universal intro, and
 * Emergency Assistance's three-page intro.
 *
 * These lived privately inside `feature:onboarding` until Emergency needed the same
 * screen. Rather than transcribe them a second time they moved here, so there is one
 * definition of what an intro page looks like and the two features cannot drift apart.
 *
 * Layout, top to bottom: a large title, an optional line of subtitle, a scrolling
 * content block, and [SakhiFooter] pinned at the bottom. The footer is what keeps the
 * primary button on the same Y position on every page, and it carries the secondary
 * action (Back, Cancel) rather than each screen floating its own button somewhere.
 */
@Composable
fun OnboardingIntroScaffold(
    title: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    showSecondarySlot: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SakhiSpacing.space6)
                .padding(top = OnboardingTitleTopPadding),
        ) {
            OnboardingStepTitle(text = title)

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiSecondaryLabel(),
                    modifier = Modifier.padding(top = SakhiSpacing.space2),
                )
            }

            Column(
                modifier = Modifier.padding(top = OnboardingHeaderContentGap),
                verticalArrangement = Arrangement.spacedBy(OnboardingBulletSpacing),
                content = content,
            )

            Spacer(modifier = Modifier.height(SakhiSpacing.space6))
        }

        SakhiFooter(
            primaryLabel = primaryLabel,
            onPrimaryClick = onPrimaryClick,
            primaryEnabled = primaryEnabled,
            secondaryLabel = secondaryLabel,
            onSecondaryClick = onSecondaryClick,
            showSecondarySlot = showSecondarySlot,
        )
    }
}

/**
 * Every onboarding step title, rendered a hair bolder than plain `FontWeight.Bold`.
 *
 * Karan reviewed a step title live on a real device and asked for it bolder. Lato only
 * ships a static Light/Regular/Bold trio (no ExtraBold/Black file), and Android's static
 * (non-variable) font rendering does not synthesize extra weight on top of an
 * already-resolved Bold glyph -- confirmed empirically: `FontWeight.ExtraBold`,
 * `FontWeight.Black`, and an explicit `fontSynthesis = FontSynthesis.All` all produced a
 * byte-identical screenshot to plain Bold. The only way to get a visibly heavier stroke
 * out of a single static weight is to draw it twice with a hairline offset between copies
 * -- the standard workaround for this exact limitation. Do not "simplify" this back to a
 * single `Text` with a heavier `FontWeight`; that was tried and does nothing on this font.
 */
@Composable
fun OnboardingStepTitle(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
    Box(modifier = modifier) {
        Text(text = text, style = style, textAlign = textAlign, modifier = Modifier.offset(x = 0.5.dp))
        Text(text = text, style = style, textAlign = textAlign)
    }
}

/**
 * One "here is what this does" line: a glyph in a soft disc, a bold title, a quieter
 * line of detail under it.
 *
 * Port of iOS `FeatureBulletRow` in `PartnerCareComponents.swift`. HStack top-aligned
 * with 16 spacing; 44pt circle; 20pt glyph; VStack spacing 4; 15pt bold title over a
 * 14pt secondary subtitle with lineSpacing 3.
 *
 * The badge fill is `primary` at 12%, not `lightPink`. On Android `lightPink` is bound
 * to `colorScheme.surface`, so using it here would paint the disc the same colour as the
 * page and the glyph would appear to float.
 */
@Composable
fun FeatureBulletRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space4),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space1),
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                // iOS default 14pt line height (~16.8) plus its explicit lineSpacing(3).
                lineHeight = 20.sp,
                color = sakhiSecondaryLabel(),
            )
        }
    }
}

/** iOS `regularShell`'s sticky-header title padding -- `DS.Spacing.xl`, which is 28, not 24. */
val OnboardingTitleTopPadding = 28.dp

/**
 * Minimum height of the [SakhiNavBar] row at the top of an onboarding-style screen.
 *
 * iOS keeps a 44pt row even when there is no button so content never touches the top
 * edge, plus 20pt above it. Android's bar sizes itself from its content, so on a step
 * with no back or close button the constraint resolved as `max(44, 0 + 28)` = 44 and the
 * 20 that should stack on top was swallowed, leaving every step 20dp higher than iOS.
 * Reported on a real device as the intro title sitting too close to the status bar.
 */
val OnboardingNavBarMinHeight = 64.dp

/**
 * DELIBERATE DEVIATION FROM iOS -- do not "restore parity" by deleting this.
 *
 * Extra gap ABOVE the shared onboarding header, so the back/close button clears the status
 * bar. Android-only, at Karan's request after a real-device review. It was first placed
 * below the header, which pushed the title down but left the button jammed at the top; moved
 * above so the same total offset buys button clearance instead.
 */
val OnboardingHeaderTopGap = 32.dp

/**
 * DELIBERATE DEVIATION FROM iOS -- do not "restore parity" by reverting to a smaller token.
 *
 * Gap between a step's header block (title + subtitle) and its content below (list, cards,
 * text field, legal-text box). iOS uses `DS.Spacing.m` (16); Karan asked for more separation
 * after reviewing onboarding on a real device, so the title/subtitle read as a distinct header
 * rather than running straight into the content. Applied to every top-anchored step with this
 * header-then-content shape across the whole onboarding flow, not just one screen -- see
 * `ModeSelectionScreen`, `InvitePermissionsScreen`, `PrivacyScreen`, `TermsScreen`,
 * `UniversalIntroScreen`, `InvitePickContactScreen`, `PartnerRelationScreen`,
 * `BeHerSakhiScreen`, `DataSourceScreen`, and `OnboardingHealthStepScreen`. Centered/hero-style
 * steps (`HeroContentStep`, `InviteWaitingScreen`, etc.) are a different layout shape and
 * are not touched by this. Reduced 40 -> 32 (20%) per Karan's live review: "har view
 * mai header and content ke bich mai space kuch jada he hogaya hai, 20% kam karo."
 *
 * Then 32 -> 24 to match the real iOS value: `OnboardingStep.contentTopSpacing`
 * defaults to `DS.Spacing.l` (24), which `OnboardingFlowView` applies as the gap
 * between the title/subtitle block and the step content. Only the Care steps
 * override it (to `DS.Spacing.m`). Karan reported the excess on the health
 * conditions step, where it also pushed the conditions card down the screen.
 */
val OnboardingHeaderContentGap = 24.dp

/**
 * Gap between two [FeatureBulletRow]s.
 *
 * iOS `UniversalIntroContent` uses `DS.Spacing.xl` (28) between bullets; the Android 4dp
 * token scale skips 28, so it is spelled out here rather than rounded to 24 or 32.
 */
val OnboardingBulletSpacing = 28.dp
