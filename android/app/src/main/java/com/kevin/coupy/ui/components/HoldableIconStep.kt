package com.kevin.coupy.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 可長按連發的圓形 step 按鈕（+/- 數字調整用）。
 *
 * 行為：
 * - 按下立即觸發一次 [onTick]（單擊正常運作）
 * - 持續按住 400ms 後進入連發模式，每 200ms 觸發一次，並逐漸加速到最快每 40ms
 * - 進入連發模式時震一下，讓使用者知道「加速生效了」
 * - enabled = false 時整顆按鈕變灰、不響應
 *
 * 視覺對齊 [androidx.compose.material3.FilledIconButton] 預設規格（40dp 圓形）。
 */
@Composable
fun HoldableIconStep(
    onTick: () -> Unit,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val enabledState by rememberUpdatedState(enabled)
    val onTickState by rememberUpdatedState(onTick)
    val haptics = LocalHapticFeedback.current

    val disabledAlpha = 0.38f
    val finalContainer = if (enabled) containerColor else containerColor.copy(alpha = disabledAlpha)
    val finalContent = if (enabled) contentColor else contentColor.copy(alpha = disabledAlpha)

    Surface(
        shape = CircleShape,
        color = finalContainer,
        contentColor = finalContent,
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (!enabledState) return@awaitEachGesture
                    onTickState()

                    val holdJob = scope.launch {
                        delay(INITIAL_HOLD_DELAY_MS)
                        if (!enabledState) return@launch
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        var interval = REPEAT_START_MS
                        while (isActive && enabledState) {
                            onTickState()
                            delay(interval)
                            interval = (interval * ACCELERATION).toLong()
                                .coerceAtLeast(REPEAT_MIN_MS)
                        }
                    }

                    waitForUpOrCancellation()
                    holdJob.cancel()
                }
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}

// 預設參數提取成常數方便調整節奏
private const val INITIAL_HOLD_DELAY_MS = 400L
private const val REPEAT_START_MS = 200L
private const val REPEAT_MIN_MS = 40L
private const val ACCELERATION = 0.88
