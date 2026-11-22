package com.novelforge.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

/**
 * 列表错峰入场：每一项比上一项晚一点淡入上浮。
 * 只在首次组合触发，之后重组不会重播。
 */
fun Modifier.staggerEnter(index: Int, step: Long = 40L, durationMs: Int = 340): Modifier = composed {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(14) * step)
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        label = "stagger"
    )
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 18.dp.toPx()
    }
}

/** 数字变化时平滑滚动，避免统计数字硬跳 */
@Composable
fun AnimatedNumber(
    value: Float,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    format: (Float) -> String = { it.toInt().toString() }
) {
    val animated by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(720, easing = FastOutSlowInEasing),
        label = "counter"
    )
    Text(format(animated), modifier = modifier, style = style)
}

/** 骨架屏流光 */
@Composable
fun ShimmerBar(modifier: Modifier = Modifier, corner: Int = 8) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shift"
    )
    val brush = Brush.linearGradient(
        colors = listOf(base, base.copy(alpha = 0.45f), base),
        start = Offset(shift, 0f),
        end = Offset(shift + 320f, 0f)
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(brush)
    )
}

/** 呼吸式缩放，用在"正在生成"的指示上 */
fun Modifier.breathe(periodMs: Int = 1600, minScale: Float = 0.94f): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "breathe")
    val s by transition.animateFloat(
        initialValue = 1f,
        targetValue = minScale,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs / 2, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheScale"
    )
    graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/** 依次起伏的三点，比转圈轻 */
@Composable
fun ThinkingDots(color: Color, dotSize: Int = 6) {
    val transition = rememberInfiniteTransition(label = "dots")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotsT"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(3) { i ->
            val phase = (t - i * 0.33f).coerceIn(0f, 1f)
            val lift = kotlin.math.sin(phase * Math.PI).toFloat().coerceAtLeast(0f)
            Box(
                Modifier
                    .graphicsLayer {
                        alpha = 0.35f + 0.65f * lift
                        translationY = -4.dp.toPx() * lift
                    }
                    .size(dotSize.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** 统一手感的弹簧 */
val SoftSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * 选择项：选中是实心主色 + 白字 + 对勾，未选是浅灰底。
 * 原来的 FilterChip 在浅色主题下选中对比太弱，看不出选没选。
 */
@Composable
fun AppChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        animationSpec = tween(180),
        label = "chipBg"
    )
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.97f,
        animationSpec = tween(180),
        label = "chipScale"
    )
    androidx.compose.foundation.layout.Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(13.dp))
            .background(bg)
            .pressScale(src, 0.93f)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor
            )
            Spacer(Modifier.width(4.dp))
        }
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor
        ) {
            label()
        }
    }
}
