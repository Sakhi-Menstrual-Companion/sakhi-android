package team.sakhi.android.feature.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import team.sakhi.android.designsystem.LocalSakhiDarkTheme
import team.sakhi.android.designsystem.SakhiPhasePalette
import team.sakhi.android.designsystem.SakhiSpacing
import team.sakhi.android.designsystem.rememberPhasePalette
import team.sakhi.models.CyclePhase

// Home's loading skeleton, the Android side of iOS's `HomeDayDetailGlassView+Skeleton.swift`
// (Karan, 2026-09-13). Every bone sits where the real element will land: the phase line under
// the date, the hero's sub line and tip pill, then one bone card per real card in the same
// order. The big countdown is not a bone: it counts down by itself until the real number
// arrives. All of it is drawn in the follicular palette, the one Home already paints while
// it loads, so the text, cards and background keep their colour.

/** The skeleton's colours, from the follicular palette so they match the page behind them. */
internal class HomeSkeletonStyle(val palette: SakhiPhasePalette, isDark: Boolean) {
    val bone: Color = palette.primary.copy(alpha = if (isDark) 0.22f else 0.12f)
    val highlight: Color = Color.White.copy(alpha = if (isDark) 0.14f else 0.62f)
    // Same recipe as `phaseCardFill` / `phaseCardStroke` for a non-period phase.
    val cardFill: Color = if (isDark) palette.tileFill else palette.tileFill.copy(alpha = 0.14f)
    val cardStroke: Color = palette.tileStroke.copy(alpha = 0.50f)
    val divider: Color = palette.tileStroke.copy(alpha = 0.16f)
}

@Composable
internal fun rememberHomeSkeletonStyle(): HomeSkeletonStyle {
    val palette = rememberPhasePalette(CyclePhase.FOLLICULAR)
    val isDark = LocalSakhiDarkTheme.current
    return remember(palette, isDark) { HomeSkeletonStyle(palette, isDark) }
}

/**
 * One light sweep for every bone on screen. Each bone draws its slice of the same band from
 * its own position on screen, so the bones read as one surface with a single wave passing
 * over it, slightly diagonal, rather than each block pulsing on its own. The progress is read
 * only in the draw phase: the sweep redraws, it never recomposes Home.
 */
private class SkeletonSweep(val progress: State<Float>, val screenWidthPx: Float, val style: HomeSkeletonStyle)

private val LocalSkeletonSweep = staticCompositionLocalOf<SkeletonSweep?> { null }

@Composable
internal fun HomeSkeletonSweepProvider(style: HomeSkeletonStyle, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "home_skeleton_sweep")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "home_skeleton_sweep_progress",
    )
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val sweep = remember(progress, widthPx, style) { SkeletonSweep(progress, widthPx, style) }
    CompositionLocalProvider(LocalSkeletonSweep provides sweep, content = content)
}

/** One bone. `width` null fills the row. `alpha` scales the bone colour for softer detail. */
@Composable
internal fun Bone(
    height: Dp,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    shape: Shape = RoundedCornerShape(percent = 50),
    alpha: Float = 1f,
) {
    val sweep = LocalSkeletonSweep.current
    var left by remember { mutableFloatStateOf(0f) }
    var top by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .onGloballyPositioned {
                val p = it.positionInRoot()
                left = p.x
                top = p.y
            }
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                onDrawBehind {
                    val style = sweep?.style ?: return@onDrawBehind
                    drawOutline(outline, style.bone.copy(alpha = style.bone.alpha * alpha))
                    val screen = sweep.screenWidthPx
                    val band = screen * 0.42f
                    // Travels from off the left edge to off the right; lower bones lag a
                    // little behind, which tilts the wave.
                    val bandStart = -band + (screen + band * 2f) * sweep.progress.value - top * 0.18f
                    val x = bandStart - left
                    drawOutline(
                        outline,
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, style.highlight, Color.Transparent),
                            startX = x,
                            endX = x + band,
                        ),
                    )
                }
            },
    )
}

// ── Hero ─────────────────────────────────────────────────────────────────────

/**
 * The hero while Home loads: a calm spinner where the day figure will land, then bones for
 * the sub line and the tip pill, at the sizes and spacing `HeroSection` uses. When the real
 * data lands the real hero takes its place.
 *
 * The spinner replaced a self-running "28 Days, 27 Days…" countdown the same day (Karan,
 * 2026-09-13): a number that is not hers, however lively, still reads as a number.
 */
@Composable
internal fun HomeHeroSkeleton(style: HomeSkeletonStyle) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = context.getString(R.string.home_loading_content_description) }
            .padding(horizontal = SakhiSpacing.space6)
            .padding(top = SakhiSpacing.space4, bottom = SakhiSpacing.space3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space2 + SakhiSpacing.space1 / 2),
    ) {
        // The figure's own 76sp line, so nothing below moves when the real one replaces it.
        Box(modifier = Modifier.height(76.dp), contentAlignment = Alignment.Center) {
            HomeHeroSpinner(color = style.palette.primary)
        }
        // Sub line: 18sp in a 24sp line.
        Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            Bone(height = 14.dp, width = 150.dp)
        }
        // Tip pill: 13sp text with 9dp vertical padding, 6dp further down.
        Bone(height = 34.dp, width = 218.dp, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * The loading mark: a soft ring with one arc gliding round it, its length breathing in and
 * out, the tail fading into the ring. First built slower (1.8 s a turn), which Karan found
 * too slow; an iOS-style petal ring replaced it briefly and read as dull, so it is back at
 * about a turn a second (2026-09-13). Both animations are read in the draw phase, so it
 * never recomposes.
 */
@Composable
internal fun HomeHeroSpinner(color: Color, diameter: Dp = 56.dp) {
    val transition = rememberInfiniteTransition(label = "home_hero_spinner")
    val turn = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1050, easing = LinearEasing)),
        label = "home_hero_spinner_turn",
    )
    val breath = transition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "home_hero_spinner_breath",
    )
    Canvas(modifier = Modifier.size(diameter)) {
        val stroke = 5.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawCircle(
            color = color.copy(alpha = 0.14f),
            radius = size.minDimension / 2f - inset,
            style = Stroke(width = stroke),
        )
        // The arc starts a few degrees in, so its round tail cap sits inside the gradient's
        // transparent start instead of showing as a dot where the sweep wraps round.
        val tailDegrees = 8f
        val fraction = breath.value
        rotate(turn.value) {
            drawArc(
                brush = Brush.sweepGradient(
                    0f to color.copy(alpha = 0f),
                    tailDegrees / 360f to color.copy(alpha = 0f),
                    fraction to color,
                    center = center,
                ),
                startAngle = tailDegrees,
                sweepAngle = 360f * fraction - tailDegrees,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

// ── Cards ────────────────────────────────────────────────────────────────────

/** One bone card per real card Home shows, in the same order. */
@Composable
internal fun HomeSkeletonCards(isPartnerMode: Boolean, style: HomeSkeletonStyle) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = context.getString(R.string.home_loading_content_description)
        },
        verticalArrangement = Arrangement.spacedBy(SakhiSpacing.space5),
    ) {
        if (isPartnerMode) {
            ChecklistBones(style)
            LoggedBones(style, isPartnerMode = true)
            NutritionBones(style)
            PhaseInfoBones(style, isPartnerMode = true)
        } else {
            LoggedBones(style, isPartnerMode = false)
            NutritionBones(style)
            CycleBones(style)
            PhaseInfoBones(style, isPartnerMode = false)
        }
    }
}

/** The real `HomeGlassCard`'s shell: 18dp corners, fill, hairline, header row and divider. */
@Composable
private fun BoneCard(
    style: HomeSkeletonStyle,
    titleWidth: Dp,
    icon: Boolean = false,
    badgeWidth: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(style.cardFill, RoundedCornerShape(18.dp))
            .border(0.5.dp, style.cardStroke, RoundedCornerShape(18.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SakhiSpacing.space2),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SakhiSpacing.space5, vertical = SakhiSpacing.space3),
        ) {
            if (icon) Bone(height = 30.dp, width = 30.dp, shape = RoundedCornerShape(8.dp))
            // Title: 16sp bold in a 24sp line.
            Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
                Bone(height = 13.dp, width = titleWidth)
            }
            Spacer(modifier = Modifier.weight(1f))
            badgeWidth?.let { Bone(height = 26.dp, width = it) }
        }
        BoneDivider(style)
        content()
    }
}

@Composable
private fun BoneDivider(style: HomeSkeletonStyle) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(style.divider),
    )
}

@Composable
private fun LoggedBones(style: HomeSkeletonStyle, isPartnerMode: Boolean) {
    BoneCard(style, titleWidth = if (isPartnerMode) 64.dp else 104.dp, badgeWidth = if (isPartnerMode) null else 88.dp) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            listOf(50.dp to 38.dp, 42.dp to 44.dp, 54.dp to 34.dp).forEach { (valueW, labelW) ->
                Box(
                    modifier = Modifier
                        .width(82.dp)
                        .height(103.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Bone(height = 103.dp, width = 82.dp, shape = RoundedCornerShape(12.dp), alpha = 0.55f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Bone(height = 26.dp, width = 26.dp, shape = CircleShape)
                        Bone(height = 10.dp, width = valueW)
                        Bone(height = 8.dp, width = labelW, alpha = 0.7f)
                    }
                }
            }
        }
    }
}

@Composable
private fun NutritionBones(style: HomeSkeletonStyle) {
    // Four rows at a 51dp pitch, 18dp in, two page dots: the geometry measured against the
    // real iOS card on 2026-09-13, which Android's card ports.
    BoneCard(style, titleWidth = 92.dp, icon = true) {
        Column(modifier = Modifier.height(214.dp)) {
            listOf(118.dp to 186.dp, 96.dp to 160.dp, 132.dp to 172.dp, 108.dp to 150.dp).forEach { (titleW, subW) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(51.dp)
                        .padding(horizontal = 18.dp),
                ) {
                    Bone(height = 36.dp, width = 36.dp, shape = RoundedCornerShape(11.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Bone(height = 12.dp, width = titleW)
                        Bone(height = 9.dp, width = subW, alpha = 0.7f)
                    }
                    Bone(
                        height = 14.dp,
                        width = 10.dp,
                        shape = RoundedCornerShape(3.dp),
                        alpha = 0.7f,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp, bottom = 10.dp),
        ) {
            Bone(height = 4.dp, width = 10.dp)
            Bone(height = 4.dp, width = 4.dp)
        }
    }
}

@Composable
private fun CycleBones(style: HomeSkeletonStyle) {
    BoneCard(style, titleWidth = 112.dp) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.height(38.dp),
            ) {
                Bone(height = 30.dp, width = 22.dp, shape = RoundedCornerShape(7.dp))
                Bone(height = 12.dp, width = 30.dp, alpha = 0.7f, modifier = Modifier.padding(bottom = 5.dp))
            }
            Bone(height = 9.dp, width = 92.dp, alpha = 0.7f)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            repeat(28) {
                Bone(height = 28.dp, modifier = Modifier.weight(1f), shape = RoundedCornerShape(3.dp))
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        ) {
            Bone(height = 9.dp, width = 96.dp, alpha = 0.7f)
            Spacer(modifier = Modifier.weight(1f))
            Bone(height = 9.dp, width = 52.dp, alpha = 0.7f)
            Bone(height = 9.dp, width = 66.dp, alpha = 0.7f)
        }
        BoneDivider(style)
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Bone(height = 64.dp, shape = RoundedCornerShape(12.dp), alpha = 0.55f)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Bone(height = 86.dp, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), alpha = 0.55f)
                Bone(height = 86.dp, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), alpha = 0.55f)
            }
        }
    }
}

@Composable
private fun PhaseInfoBones(style: HomeSkeletonStyle, isPartnerMode: Boolean) {
    BoneCard(style, titleWidth = if (isPartnerMode) 200.dp else 176.dp) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Bone(height = 10.dp)
            Bone(height = 10.dp)
            Bone(height = 10.dp)
            Bone(height = 10.dp, width = 170.dp)
        }
        BoneDivider(style)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        ) {
            Bone(height = 12.dp, width = 12.dp, shape = RoundedCornerShape(3.dp))
            Bone(height = 10.dp, width = 96.dp)
        }
        listOf(230.dp, 196.dp, 214.dp).forEach { w ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Bone(height = 6.dp, width = 6.dp, shape = CircleShape)
                Bone(height = 10.dp, width = w, alpha = 0.8f)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun ChecklistBones(style: HomeSkeletonStyle) {
    BoneCard(style, titleWidth = 124.dp, icon = true) {
        listOf(196.dp, 164.dp, 212.dp, 150.dp).forEach { w ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Bone(height = 22.dp, width = 22.dp, shape = RoundedCornerShape(6.dp))
                Bone(height = 11.dp, width = w, alpha = 0.8f)
            }
        }
    }
}


/** See the note where Home reads it. Always 0 in a release build. */
@Composable
internal fun rememberDebugSkeletonHoldSeconds(): Int {
    val context = LocalContext.current
    return remember(context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return@remember 0
        var c: Context? = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        (c as? Activity)?.intent?.getIntExtra("home_skeleton_hold", 0) ?: 0
    }
}
