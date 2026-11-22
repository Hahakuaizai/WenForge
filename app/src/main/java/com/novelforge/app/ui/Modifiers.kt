package com.novelforge.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 按下时轻微内缩，松手回弹。
 * 用 interactionSource 而不是 pointerInput，这样能和 clickable 的涟漪共存。
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.965f,
    haptic: Boolean = true
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(pressed) {
        if (pressed && haptic) Haptics.tap(appCtx)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** 卡片按下 / 聚焦时边框微微亮起，给一点"可点"的暗示 */
fun Modifier.focusBorder(
    interactionSource: MutableInteractionSource,
    idle: Color,
    active: Color
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val color by animateColorAsState(
        targetValue = if (pressed || focused) active else idle,
        label = "focusBorder"
    )
    border(1.dp, color, RoundedCornerShape(16.dp))
}
