package com.safeincloud.android.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
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
        manager.setPrimaryClip(ClipData.newPlainText("密码", text))
        scope.launch {
            delay(clearAfterMs)
            val current = manager.primaryClip?.getItemAt(0)?.text?.toString()
            if (current == text) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    manager.clearPrimaryClip()
                } else {
                    manager.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }
}
