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

/** 阻塞式文件选择对话框，返回选中的文件；取消则返回 null。在 IO 线程调用。 */
fun pickFileDialog(title: String, save: Boolean = false, defaultName: String = ""): java.io.File? {
    val mode = if (save) java.awt.FileDialog.SAVE else java.awt.FileDialog.LOAD
    val dialog = object : java.awt.FileDialog(null as java.awt.Frame?, title, mode) {
        init { if (save) file = defaultName }
    }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    return java.io.File(dir, name)
}

/** 显示 2 秒消息后自动清除。 */
@Composable
fun LaunchedMessage(message: String, onClear: () -> Unit) {
    LaunchedEffect(message) {
        delay(2000)
        onClear()
    }
}
