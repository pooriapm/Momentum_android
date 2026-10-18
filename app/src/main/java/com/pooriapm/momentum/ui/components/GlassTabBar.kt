// Built on LiquidBottomTabs from Kyant0/AndroidLiquidGlass (catalog/components), Apache License 2.0.
// https://github.com/Kyant0/AndroidLiquidGlass
package com.pooriapm.momentum.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.pooriapm.momentum.ui.components.liquid.DampedDragAnimation
import com.pooriapm.momentum.ui.components.liquid.InteractiveHighlight
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

data class GlassTab(val label: String, val icon: ImageVector)

/** Height of the floating bar plus its bottom margin; scrolling content pads by at least this much. */
val GlassTabBarClearance: Dp = 64.dp + 12.dp

/** Real backdrop blur needs Android 12; below it the glass gets a denser tint so text behind stays unreadable. */
private val hasBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** A light frost only: enough to keep labels readable, not enough to hide what is behind. */
private val GlassBlur = 2.dp

private val LocalTabScale = staticCompositionLocalOf { { 1f } }

/** Floating Liquid Glass tab bar (Apple iOS 26 style), a [LiquidSelector] over the screen's content. */
@Composable
fun GlassTabBar(
    tabs: List<GlassTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    LiquidSelector(
        count = tabs.size,
        selected = selected,
        onSelect = onSelect,
        backdrop = backdrop,
        modifier = modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp),
    ) { index ->
        val color = MaterialTheme.colorScheme.onSurface
        Icon(tabs[index].icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(tabs[index].label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/**
 * Liquid Glass segmented control: clear glass that blurs, saturates and refracts the content behind
 * it ([backdrop] must be recorded from that content with `layerBackdrop`), and a selection lens you
 * can tap or drag between options, which stretches with velocity and bends light at its edges while
 * pressed. Right-to-left aware. Used for the tab bar and for 1–5 scores.
 */
@Composable
fun LiquidSelector(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    role: Role = Role.Tab,
    item: @Composable ColumnScope.(index: Int) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val accent = MaterialTheme.colorScheme.primary
    val glass = when {
        !hasBlur -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        // Lightly tinted: the content behind stays visible, as through real glass.
        dark -> Color(0xFF1D1B18).copy(alpha = 0.3f)
        else -> Color(0xFFFFFFFF).copy(alpha = 0.3f)
    }
    val haptics = LocalHapticFeedback.current
    val onSelectState by rememberUpdatedState(onSelect)
    val tabsBackdrop = rememberLayerBackdrop()
    val lensHeight = height - 8.dp

    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8.dp.toPx()) / count }

        // Dragging past the ends nudges the whole bar a little, like rubber-banding on iOS.
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        // Gesture callbacks are created once; read direction and width live. A sheet's window first
        // composes left-to-right and only then picks up RTL, so a captured value would be stale.
        val ltr by rememberUpdatedState(isLtr)
        val slot by rememberUpdatedState(tabWidth)
        val scope = rememberCoroutineScope()
        var currentIndex by remember { mutableIntStateOf(selected) }
        val lens = remember(scope, count) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = selected.toFloat(),
                valueRange = 0f..(count - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, count - 1)
                    currentIndex = target
                    animateToValue(target.toFloat())
                    scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    // Indices grow to the left in RTL, so a drag to the right lowers the index there.
                    updateValue(
                        (targetValue + dragAmount.x / slot * if (ltr) 1f else -1f)
                            .fastCoerceIn(0f, (count - 1).toFloat()),
                    )
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                },
            )
        }
        // Selection from outside (state restored after rotation, or another screen switching tabs).
        LaunchedEffect(selected) { currentIndex = selected }
        LaunchedEffect(lens) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    lens.animateToValue(index.toFloat())
                    onSelectState(index)
                }
        }

        val highlight = remember(scope) {
            InteractiveHighlight(
                animationScope = scope,
                position = { size, _ ->
                    Offset(
                        if (ltr) (lens.value + 0.5f) * slot + panelOffset
                        else size.width - (lens.value + 0.5f) * slot + panelOffset,
                        size.height / 2f,
                    )
                },
            )
        }

        val items: @Composable RowScope.() -> Unit = {
            repeat(count) { index ->
                LiquidItem(index == currentIndex, role, { currentIndex = index }) { item(index) }
            }
        }

        // 1. The glass panel with the tabs on it.
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(GlassBlur.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    layerBlock = {
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, lens.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(glass) },
                )
                .then(highlight.modifier)
                .height(height)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = items,
        )

        // 2. An invisible copy of the tabs in the accent color, recorded so the lens can show
        //    the selected tab tinted through the glass.
        CompositionLocalProvider(LocalTabScale provides { lerp(1f, 1.2f, lens.pressProgress) }) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = lens.pressProgress
                            vibrancy()
                            blur(GlassBlur.toPx())
                            lens(24.dp.toPx() * progress, 24.dp.toPx() * progress)
                        },
                        highlight = { Highlight.Default.copy(alpha = lens.pressProgress) },
                        onDrawSurface = { drawRect(glass) },
                    )
                    .then(highlight.modifier)
                    .height(lensHeight)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
                verticalAlignment = Alignment.CenterVertically,
                content = items,
            )
        }

        // 3. The selection lens: a glass bead that refracts (with chromatic aberration) while held.
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) lens.value * tabWidth + panelOffset
                        else size.width - (lens.value + 1f) * tabWidth + panelOffset
                }
                .then(highlight.gestureModifier)
                .then(lens.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = lens.pressProgress
                        lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = lens.pressProgress) },
                    shadow = { Shadow(alpha = lens.pressProgress) },
                    innerShadow = {
                        val progress = lens.pressProgress
                        InnerShadow(radius = 8.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = lens.scaleX
                        scaleY = lens.scaleY
                        val velocity = lens.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = lens.pressProgress
                        drawRect(accent.copy(alpha = if (dark) 0.22f else 0.14f), alpha = 1f - progress)
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                )
                .height(lensHeight)
                .fillMaxWidth(1f / count),
        )
    }
}

@Composable
private fun RowScope.LiquidItem(selected: Boolean, role: Role, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val scale = LocalTabScale.current
    Column(
        Modifier
            .clip(Capsule())
            .clickable(interactionSource = null, indication = null, role = role, onClick = onClick)
            .semantics { this.selected = selected }
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
