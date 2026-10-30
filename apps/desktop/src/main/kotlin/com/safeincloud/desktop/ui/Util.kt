package com.safeincloud.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** 将文本复制到系统剪贴板。 */
fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}

/** 显示 2 秒消息后自动清除。 */
@Composable
fun LaunchedMessage(message: String, onClear: () -> Unit) {
    LaunchedEffect(message) {
        delay(2000)
        onClear()
    }
}
