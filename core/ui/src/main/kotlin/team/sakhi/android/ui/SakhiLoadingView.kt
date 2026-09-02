package team.sakhi.android.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import team.sakhi.models.CyclePhase
import team.sakhi.android.designsystem.phasePageBackgroundBrush
import team.sakhi.android.designsystem.rememberPhasePalette
import team.sakhi.android.designsystem.sakhiPageBackgroundBrush
import team.sakhi.android.designsystem.LocalSakhiDarkTheme
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.sakhiDeepRose
import team.sakhi.android.designsystem.sakhiSecondaryLabel

/**
 * Real port of iOS's `SakhiLoadingView` (`SakhiLoadingView.swift`) -- the app's single
 * loading component, not a bare `CircularProgressIndicator`. Three concentric dashed
 * pink rings (180/128/82pt at 0.18/0.30/0.52 opacity, 1.5pt stroke, [4,5] dash) around
 * a gently pulsing 44pt brand mark, on the standard page background.
 *
 * [SakhiLoadingContext] mirrors iOS's enum of the same name: each case carries its own
 * title/subtitle, and the "minimal" cases (app launch, home setup, cycling messages)
 * deliberately show only the mark with no text.
 */
sealed interface SakhiLoadingContext {
    data object AppLaunch : SakhiLoadingContext
    data object HomeSetup : SakhiLoadingContext
    data object Authentication : SakhiLoadingContext
    data object SigningOut : SakhiLoadingContext
    data object Syncing : SakhiLoadingContext
    /** Cycles through the given lines at the bottom -- used by onboarding setup. */
    data class Messages(val messages: List<String>) : SakhiLoadingContext
    data class Custom(val title: String, val subtitle: String? = null) : SakhiLoadingContext
}

private val SakhiLoadingContext.title: String
    get() = when (this) {
        SakhiLoadingContext.AppLaunch -> "Good to see you."
        SakhiLoadingContext.HomeSetup -> "Almost there."
        SakhiLoadingContext.Authentication -> "Signing you in."
        SakhiLoadingContext.SigningOut -> "Signing you out."
        SakhiLoadingContext.Syncing -> "Syncing your data."
        is SakhiLoadingContext.Messages -> ""
        is SakhiLoadingContext.Custom -> title
    }

private val SakhiLoadingContext.subtitle: String?
    get() = when (this) {
        SakhiLoadingContext.AppLaunch -> "Just a moment."
        SakhiLoadingContext.HomeSetup -> "Getting your cycle data ready."
        SakhiLoadingContext.Authentication -> "Just a moment."
        SakhiLoadingContext.SigningOut -> "Taking you back to Sakhi."
        SakhiLoadingContext.Syncing -> "Making sure everything is up to date."
        is SakhiLoadingContext.Messages -> null
        is SakhiLoadingContext.Custom -> subtitle
    }

/** iOS `isMinimal`: silent system states show only the mark, no text. */
private val SakhiLoadingContext.isMinimal: Boolean
    get() = this is SakhiLoadingContext.AppLaunch ||
        this is SakhiLoadingContext.HomeSetup ||
        this is SakhiLoadingContext.Messages

@Composable
fun SakhiLoadingView(
    context: SakhiLoadingContext = SakhiLoadingContext.AppLaunch,
    modifier: Modifier = Modifier,
    /**
     * The phase to paint in, when one is already known.
     *
     * This screen precedes Home, so painting it in the brand pink meant every launch went
     * pink and then changed to whatever phase she is actually in — a colour change she can
     * see, on a screen whose whole job is to look like nothing is happening. Passing the last
     * known phase makes this and Home the same colour, so nothing changes when Home mounts.
     *
     * Null keeps the brand treatment, which is the right look when there is genuinely no
     * phase yet (first launch, or signed out).
     */
    phase: CyclePhase? = null,
) {
    val phasePalette = phase?.let { rememberPhasePalette(it) }
    // Matches Home exactly: same brush, same arguments. Any divergence here reintroduces the
    // colour change this parameter exists to remove.
    val background = if (phase != null) {
        phasePageBackgroundBrush(phase = phase, hasCycleData = true)
    } else {
        sakhiPageBackgroundBrush()
    }
    val pinkColor = phasePalette?.primary ?: MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "sakhi_loading")
    // iOS: `.easeInOut(duration: 1.2).repeatForever(autoreverses: true)` on both scale
    // (0.975 <-> 1.025) and opacity (0.78 <-> 1).
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sakhi_loading_pulse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            // iOS `SakhiLoadingView` ends in `.profileStaticPageBackground()`, so this is
            // the phase gradient in dark, not the flat black `background` role.
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            DashedRing(diameter = 180.dp, alpha = 0.18f, color = pinkColor)
            DashedRing(diameter = 128.dp, alpha = 0.30f, color = pinkColor)
            DashedRing(diameter = 82.dp, alpha = 0.52f, color = pinkColor)
            // Karan: brand mark tinted pink in light theme, white in dark. Uses the
            // app's own Light/Dark choice (`LocalSakhiDarkTheme`), not the OS uiMode --
            // the same distinction other components in this codebase already respect.
            Image(
                painter = painterResource(R.drawable.sakhi_symbol),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    if (LocalSakhiDarkTheme.current) Color.White else MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .size(44.dp)
                    .scale(0.975f + pulse * 0.05f)
                    .alpha(0.78f + pulse * 0.22f),
            )
        }

        if (!context.isMinimal) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space6)
                    .padding(bottom = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            ) {
                Text(
                    text = context.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
                context.subtitle?.let { sub ->
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = sakhiSecondaryLabel(),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                    )
                }
            }
        }

        (context as? SakhiLoadingContext.Messages)?.messages?.takeIf { it.isNotEmpty() }?.let { messages ->
            var messageIndex by remember(messages) { mutableIntStateOf(0) }
            // iOS advances every 700ms.
            LaunchedEffect(messages, messageIndex) {
                delay(700)
                messageIndex = (messageIndex + 1) % messages.size
            }
            // iOS animates each swap:
            //   .transition(.asymmetric(insertion: .opacity.combined(with: .offset(y: 6)),
            //                           removal: .opacity))
            //   .animation(.easeInOut(duration: 0.3), value: messageIndex)
            // Android was replacing the string in place, so the text popped. The
            // `minHeight: 48` is iOS's too -- without it a one-line message followed by
            // a two-line one shifts everything above it.
            AnimatedContent(
                targetState = messageIndex % messages.size,
                // The incoming message waits for the outgoing one to finish leaving. Run
                // together, both strings are drawn in the same centred box at partial
                // opacity, so the two overlap into one unreadable line -- caught on the QA
                // emulator during sign-out, rendering as "Getting Almost there... ready...".
                // At a 700ms cadence that overlap was on screen almost half the time.
                // Total is still the 0.3s iOS animates over, just sequenced rather than
                // simultaneous, which leaves 400ms of a settled, readable message.
                transitionSpec = {
                    (
                        fadeIn(
                            animationSpec = tween(MessageFadeMillis, delayMillis = MessageFadeMillis),
                        ) +
                            slideInVertically(
                                animationSpec = tween(MessageFadeMillis, delayMillis = MessageFadeMillis),
                                initialOffsetY = { MessageRiseOffsetPx },
                            )
                        ) togetherWith fadeOut(animationSpec = tween(MessageFadeMillis))
                },
                label = "loadingMessage",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = SakhiSpacing.space6)
                    .padding(bottom = MessageBottomPadding),
            ) { index ->
                Text(
                    text = messages[index],
                    style = MaterialTheme.typography.bodyMedium,
                    color = sakhiDeepRose().copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MessageMinHeight)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
    }
}

@Composable
private fun DashedRing(
    diameter: androidx.compose.ui.unit.Dp,
    alpha: Float,
    color: androidx.compose.ui.graphics.Color,
) {
    Canvas(modifier = Modifier.size(diameter)) {
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = size.minDimension / 2f,
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(4.dp.toPx(), 5.dp.toPx()),
                ),
            ),
        )
    }
}

// iOS `SakhiLoadingView` cycling-message block.
/** `.frame(minHeight: 48)`. */
private val MessageMinHeight = 48.dp
/** `.padding(.bottom, 64)`. */
private val MessageBottomPadding = 64.dp
/** `.animation(.easeInOut(duration: 0.3))`. */
// Half of iOS's 0.3s each way: out, then in, so the two never share the screen.
private const val MessageFadeMillis = 150
/** `.offset(y: 6)` on insertion. */
private const val MessageRiseOffsetPx = 6
