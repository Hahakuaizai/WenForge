package com.novelforge.app.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 卡片在屏幕上的矩形（像素）。点卡片时记下来，目标页照着它从卡片大小长到全屏。
 */
data class CardRevealRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val cornerPx: Float,
    val color: Color = Color(0xFF8E7CC3)
)

/** 最近一次点击的卡片矩形。跨页面传递，用 CompositionLocal 太绕，直接放全局。 */
object CardRevealStore {
    var last: CardRevealRect? = null
}

/**
 * 给卡片挂上：点击时把自身矩形写进 [CardRevealStore]，
 * 目标页读到它就能从卡片位置长出来。
 */
fun Modifier.cardRevealSource(
    cornerDp: Float = 14f,
    color: Color,
    onClick: () -> Unit
): Modifier = composed {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var lastPos by remember { mutableStateOf(Offset.Zero) }
    var lastSize by remember { mutableStateOf(Size.Zero) }
    this
        .onGloballyPositioned { coords ->
            lastPos = coords.positionInWindow()
            lastSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
        }
        .then(
            Modifier.clickable {
                CardRevealStore.last = CardRevealRect(
                    left = lastPos.x,
                    top = lastPos.y,
                    width = lastSize.width,
                    height = lastSize.height,
                    cornerPx = with(density) { cornerDp.dp.toPx() },
                    color = color
                )
                onClick()
            }
        )
}

/**
 * 卡片 → 整页的转场容器。
 *
 * 视觉：一张和卡片同色同位置的色块从卡片矩形放大到铺满全屏，
 * 圆角从卡片圆角平滑收到 0，卡片外的旧内容同时淡出，
 * 页面内容随后逐行错峰淡入上浮（每行 35ms，弹簧 320ms）。
 */
@Composable
fun CardRevealPage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // 只取一次并立刻清空：下次从别的入口进来（搜索、长按菜单）不该复用旧卡片位置
    val origin = remember { CardRevealStore.last.also { CardRevealStore.last = null } }
    val grow = remember { Animatable(if (origin == null) 1f else 0f) }
    val fade = remember { Animatable(if (origin == null) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (origin != null) {
            // 色块放大 + 圆角收缩，弹簧收尾，总时长约 320ms
            launch {
                grow.animateTo(
                    1f,
                    spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            fade.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        if (origin != null && grow.value < 1f) {
            // 起点矩形 → 全屏矩形，按进度插值
            val t = grow.value
            val l = origin.left + (0f - origin.left) * t
            val tp = origin.top + (0f - origin.top) * t
            val ww = origin.width + (w - origin.width) * t
            val hh = origin.height + (h - origin.height) * t
            val corner = (origin.cornerPx * (1f - t)).coerceAtLeast(0f)
            Box(
                Modifier
                    .offset(x = with(density) { l.toDp() }, y = with(density) { tp.toDp() })
                    .size(
                        width = with(density) { ww.toDp() },
                        height = with(density) { hh.toDp() }
                    )
                    .clip(RoundedCornerShape(with(density) { corner.toDp() }))
                    .background(origin.color)
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fade.value }
        ) {
            content()
        }
    }
}

/**
 * 逐行错峰入场：每项比上一项晚 [step] 毫秒，淡入 + 上浮 [distanceDp]，弹簧缓动。
 * 只在首次组合触发。
 */
fun Modifier.staggerReveal(
    index: Int,
    step: Long = 35L,
    distanceDp: Float = 12f,
    durationMs: Int = 320
): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index.coerceIn(0, 20) * step)
        p.animateTo(
            1f,
            spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
        )
    }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * distanceDp.dp.toPx()
    }
}

/**
 * 预测返回：系统返回手势实时驱动上层页面右移，底层以 0.35 视差同步跟随，
 * 上层左边缘画一道柔和投影。松手超过阈值就完成返回，没到就按距离回弹。
 */
@Composable
fun PredictiveBackContainer(
    enabled: Boolean = true,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }

    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { ev ->
                progress.snapTo(ev.progress.coerceIn(0f, 1f))
            }
            // 手势完成：补到 1 再真正返回
            progress.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
            onBack()
        } catch (e: CancellationException) {
            // 松手没过阈值：按当前距离算回弹时长，短距离快速回弹
            val from = progress.value
            val ms = (170 * from).toInt().coerceIn(80, 240)
            progress.animateTo(0f, tween(ms, easing = FastOutSlowInEasing))
            throw e
        }
    }

    Box(modifier.fillMaxSize()) {
        // 底层：从略微缩小、偏左的位置被「扶正」到原位 —— 0.35 的视差系数，
        // 上层走满一屏时它只走三分之一多一点，看着就有前后层次。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = progress.value * size.width * 0.35f - 20.dp.toPx() * (1f - progress.value)
                    scaleX = 0.94f + 0.06f * progress.value
                    scaleY = 0.94f + 0.06f * progress.value
                }
                .background(MaterialTheme.colorScheme.background)
        )

        // 上层：完全跟手右移，左边缘柔和投影
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = progress.value * size.width
                }
                .drawBehind {
                    val px = progress.value * size.width
                    if (px > 1f) {
                        // 左边缘投影：从边缘向外渐隐的黑色带
                        val band = 22.dp.toPx()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.22f * progress.value),
                                    Color.Transparent
                                ),
                                startX = -band,
                                endX = 0f
                            ),
                            topLeft = Offset(-band, 0f),
                            size = Size(band, size.height)
                        )
                    }
                }
        ) {
            content()
        }
    }
}
