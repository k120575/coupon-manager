package com.kevin.coupy.ui.util

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * 點輸入框以外的空白處時，收起螢幕鍵盤並清除焦點。
 *
 * 套在畫面根容器上。TextField、按鈕等子元件會自行消費點擊事件，
 * 所以這裡的 detectTapGestures 只會在點到空白區域時觸發，不會干擾正常操作。
 */
fun Modifier.clearFocusOnTap(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    pointerInput(Unit) {
        detectTapGestures(onTap = { focusManager.clearFocus() })
    }
}
