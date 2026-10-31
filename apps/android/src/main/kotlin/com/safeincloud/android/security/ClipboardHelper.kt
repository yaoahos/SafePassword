package com.safeincloud.android.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ClipboardHelper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun copySensitive(context: Context, text: String, clearAfterMs: Long = 30_000) {
        if (text.isBlank()) return
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        manager.setPrimaryClip(ClipData.newPlainText("密码", text).markSensitive())
        scope.launch {
            delay(clearAfterMs)
            val current = manager.primaryClip?.getItemAt(0)?.text?.toString()
            if (current == text) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    manager.clearPrimaryClip()
                } else {
                    manager.setPrimaryClip(ClipData.newPlainText("", "").markSensitive())
                }
            }
        }
    }

    /**
     * Android 13 起剪贴板会弹出预览、并可能进入剪贴板历史；
     * 打上 EXTRA_IS_SENSITIVE 后系统不再显示内容预览，也不会写入历史。
     */
    private fun ClipData.markSensitive(): ClipData {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        return this
    }
}
