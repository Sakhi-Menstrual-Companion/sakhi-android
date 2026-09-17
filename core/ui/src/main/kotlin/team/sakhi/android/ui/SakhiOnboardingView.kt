package team.sakhi.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.sakhi.android.designsystem.SakhiFontSize
import team.sakhi.android.designsystem.rememberSakhiFlingBehavior
import team.sakhi.android.designsystem.sakhiLabel
import team.sakhi.android.designsystem.sakhiSecondaryLabel
import team.sakhi.android.designsystem.sakhiSoftPinkPageBrush
import team.sakhi.android.designsystem.sakhiSystemBackground

/**
 * One point in the list: what she gets, in a line.
 *
 * Port of iOS `SakhiOnboardingPoint`. The icon is an [ImageVector] rather than a symbol
 * name, which is the only difference between the two platforms' versions of this type.
 */
@Immutable
data class SakhiOnboardingPoint(
    val icon: ImageVector,
    val title: String,
    val detail: String,
)

/**
 * The template every "here is what this does" screen in the app is built from. One screen,
 * one job: a mark, a heading, a line or two, three points, and one thing to do.
 *
 * Port of iOS `SakhiOnboardingView.swift`, which is the contract. It holds no content of
 * its own: a caller passes the mark, the words, the points and the two actions, and gets
 * the same screen every other feature gets. Care, Stay With Me, Sakhi AI, the update gate
 * and the offline explainer all open on this view, and a new feature should too rather
 * than drawing its own.
 *
 * Why it is shaped this way, from the iOS source:
 *  - One screen, one job. Value first, mechanics later.
 *    https://developer.apple.com/design/human-interface-guidelines/onboarding
 *  - Three points, not six. More choices on a screen means slower decisions and more
 *    people leaving (Hick's law).
 *    https://www.eleken.co/blog-posts/mobile-app-onboarding-best-practices
 *  - One action, named for what it does, and a real way to say no that does not scold.
 *    https://www.useronboard.com/onboarding-ux-patterns/permission-priming/
 *
 * The chrome is the app's own. [SakhiNavBar] draws the close button, on the left, where
 * every other screen keeps it, and [SakhiFooter] draws the two actions at the height every
 * other screen puts them. Inside a flow that already draws its own close button, pass
 * `onClose = null` and the bar disappears, which is what the Care intro does.
 *
 * No shadow anywhere, and no outline on the card. The gradient stops short of the page
 * colour, so the white block separates itself (see `sakhiSoftPinkPageBrush`).
 *
 * @param drawsBackground false when the host already paints the soft pink ground behind
 *   the whole window. The Care intro sits inside `OnboardingShell`, whose chrome is above
 *   this view: painting here would start the gradient below the close button and leave a
 *   visible seam across the top of the screen.
 */
@Composable
fun SakhiOnboardingView(
    icon: ImageVector,
    title: String,
    message: String,
    points: List<SakhiOnboardingPoint>,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    navTitle: String? = null,
    drawsBackground: Boolean = true,
) {
    val background = if (drawsBackground) {
        Modifier.background(sakhiSoftPinkPageBrush())
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // The ground is read before the insets padding so the gradient spans the whole
            // window rather than restarting inside a shorter box under the status bar.
            .then(background)
            // Harmless when a host has already padded and consumed these: Compose resolves
            // them to zero here. `consumeWindowInsets` stops `SakhiFooter`'s own
            // `navigationBarsPadding()` applying the bottom inset a second time.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .consumeWindowInsets(WindowInsets.safeDrawing),
    ) {
        if (onClose != null) {
            SakhiNavBar(onClose = onClose, title = navTitle)
        }

        // Centred in whatever room is left, not pinned under the top bar: on a tall phone a
        // top-aligned block leaves a hole above the button, and the eye reads the hole as
        // something missing. `heightIn(min = maxHeight)` inside the scroll is what makes
        // `Arrangement.Center` mean the viewport rather than the content, and it still
        // scrolls when the words or the text size need more room than the screen has.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState(), flingBehavior = rememberSakhiFlingBehavior())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = PageMargin, vertical = PageMarginV),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // The mark, with nothing behind it. A disc around it made the first thing on
                // the page look like a button waiting to be pressed (Karan, 2026-09-18).
                Box(modifier = Modifier.height(MarkBoxHeight), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(MarkSize),
                    )
                }

                // Both headings shrink to fit rather than clip, the way iOS's
                // `minimumScaleFactor` does: real copy in two lines at a slightly smaller
                // size reads; the same copy cut off mid-word does not. Without this, "the
                // person you choose can see you moving" ended as "can see you mo...".
                BasicText(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = sakhiLabel(),
                        textAlign = TextAlign.Center,
                        lineHeight = HeadingLineHeight,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // iOS `minimumScaleFactor(0.9)` on a 30pt title.
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = HeadingMinSize,
                        maxFontSize = HeadingSize,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = HeadingTopGap),
                )

                BasicText(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = sakhiSecondaryLabel(),
                        textAlign = TextAlign.Center,
                        lineHeight = MessageLineHeight,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // iOS `minimumScaleFactor(0.85)` on a 16pt line.
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = MessageMinSize,
                        maxFontSize = SakhiFontSize.lg,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = MessageTopGap),
                )

                Spacer(modifier = Modifier.height(CardTopGap))

                PointsCard(points = points)
            }
        }

        SakhiFooter(
            primaryLabel = primaryLabel,
            onPrimaryClick = onPrimaryClick,
            secondaryLabel = secondaryLabel,
            onSecondaryClick = onSecondaryClick,
            // The way out is offered on most of these screens. Where it is not, the slot
            // still holds its place so the primary button never moves between screens.
            showSecondarySlot = true,
        )
    }
}

/**
 * One white block, the points inside it. The block is what separates the promises from the
 * page; it needs no outline and no shadow to do that.
 */
@Composable
private fun PointsCard(points: List<SakhiOnboardingPoint>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(sakhiSystemBackground())
            // Breathing room inside the block itself, on top of each row's own padding. The
            // first point was sitting on the rounded top edge without it (Karan, 2026-09-18).
            .padding(vertical = CardPaddingV),
    ) {
        points.forEachIndexed { index, point ->
            if (index > 0) {
                // Starts where the words start, so the column of text reads as one column.
                SakhiListDivider(startInset = RowPaddingH + RowGlyphColumn + RowGap)
            }
            PointRow(point)
        }
    }
}

@Composable
private fun PointRow(point: SakhiOnboardingPoint) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RowPaddingH, vertical = RowPaddingV),
        verticalAlignment = Alignment.Top,
    ) {
        // A fixed column, no disc. This is what keeps the titles in a line under each other
        // now that the icons carry nothing behind them.
        Box(
            modifier = Modifier.width(RowGlyphColumn).height(RowGlyphHeight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = point.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(RowGlyphSize),
            )
        }
        Spacer(modifier = Modifier.width(RowGap))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(RowTextGap),
        ) {
            Text(
                text = point.title,
                fontSize = SakhiFontSize.lg,
                fontWeight = FontWeight.Bold,
                color = sakhiLabel(),
            )
            Text(
                text = point.detail,
                fontSize = SakhiFontSize.base,
                lineHeight = RowDetailLineHeight,
                color = sakhiSecondaryLabel(),
            )
        }
    }
}

// iOS `SakhiOnboardingView.Metric`, one for one.
private val PageMargin = 24.dp
private val PageMarginV = 24.dp
private val MarkSize = 46.dp
private val MarkBoxHeight = 52.dp
private val HeadingTopGap = 28.dp
private val MessageTopGap = 12.dp
private val CardTopGap = 36.dp
private val CardRadius = 28.dp
private val CardPaddingV = 8.dp
private val RowPaddingH = 20.dp
private val RowPaddingV = 18.dp
private val RowGlyphColumn = 26.dp
private val RowGlyphHeight = 22.dp
private val RowGlyphSize = 18.dp
private val RowGap = 14.dp
private val RowTextGap = 4.dp

// 30 and 14-at-20 have no token on the shared scale (it steps 28 -> 32, and 14 -> 16), so
// they are named here rather than rounded to something that is not what iOS draws.
private val HeadingSize = 30.sp
private val HeadingMinSize = 27.sp
private val HeadingLineHeight = 36.sp
private val MessageMinSize = 13.6.sp
private val MessageLineHeight = 22.sp
private val RowDetailLineHeight = 20.sp
