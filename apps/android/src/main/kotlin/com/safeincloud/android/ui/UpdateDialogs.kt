package com.safeincloud.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.safeincloud.android.update.ReleaseNote
import com.safeincloud.android.update.UpdateInfo

/** 发现新版本时的提示对话框。 */
@Composable
fun UpdateAvailableDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${info.version}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val bullets = conciseNotes(info.notes)
                if (bullets.isEmpty()) {
                    Text("本次更新主要是问题修复与稳定性优化。", style = MaterialTheme.typography.bodySmall)
                } else {
                    bullets.forEach { line ->
                        Text("· $line", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                    }
                }
                if (info.size > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "安装包大小：${formatBytes(info.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) { Text("立即更新") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        }
    )
}

/** 更新包下载完成后提示安装。 */
@Composable
fun UpdateReadyDialog(
    version: String,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (version.isBlank()) "更新已下载" else "v$version 已下载完成") },
        text = { Text("是否立即安装新版本？安装前请允许本应用安装未知来源应用。") },
        confirmButton = {
            TextButton(onClick = onInstall) { Text("安装") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        }
    )
}

/** 更新日志对话框，列出最近若干个正式版本。 */
@Composable
fun ChangelogDialog(
    notes: List<ReleaseNote>,
    checking: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新日志") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (notes.isEmpty()) {
                    Text(
                        if (checking) "正在获取更新日志…" else "暂无更新日志。",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    notes.forEachIndexed { index, note ->
                        if (index > 0) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(Modifier.height(1.dp))
                            Spacer(Modifier.height(12.dp))
                        }
                        val heading = listOfNotNull(
                            note.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                            note.date.takeIf { it.isNotBlank() }
                        ).joinToString("    ")
                        Text(heading, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        val bullets = conciseNotes(note.notes, limit = 5)
                        if (bullets.isEmpty()) {
                            Text(
                                "问题修复与稳定性优化。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            bullets.forEach { line ->
                                Text("· $line", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(3.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/**
 * 把 GitHub 发布说明压缩成「功能介绍 + 问题修复」条目：
 * 丢掉标题、日期、图片、表格，以及安装说明、包名、系统要求、校验值等发布信息。
 */
internal fun conciseNotes(raw: String, limit: Int = 6): List<String> =
    raw.lineSequence()
        .map { it.trim() }
        .map { line ->
            line.removePrefix("- ")
                .removePrefix("* ")
                .removePrefix("+ ")
                .removePrefix("• ")
                .replace("**", "")
                .replace("`", "")
                .trim()
        }
        .filter { it.isNotEmpty() }
        .filterNot { line -> NOISE_PATTERNS.any { pattern -> line.contains(pattern) } }
        .filterNot { line ->
            line.startsWith("#") ||
                line.startsWith(">") ||
                line.startsWith("|") ||
                line.startsWith("![") ||
                line.startsWith("---")
        }
        .filterNot { DATE_ONLY.matches(it) }
        .take(limit)
        .map { if (it.length > 80) it.take(78).trimEnd() + "…" else it }
        .toList()

/** 发布信息类关键词：这些内容不属于功能介绍或问题修复。 */
private val NOISE_PATTERNS = listOf(
    "包名", "versionCode", "versionName", "版本号", "系统要求", "适用系统", "适用版本",
    "安装说明", "安装包", "下载地址", "文件大小", "SHA", "MD5", "签名", "校验值", "更新于"
)

private val DATE_ONLY = Regex("""\d{4}[/-]\d{1,2}[/-]\d{1,2}""")

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "未知"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1) String.format(java.util.Locale.CHINA, "%.1f MB", mb)
    else String.format(java.util.Locale.CHINA, "%.0f KB", bytes / 1024.0)
}
