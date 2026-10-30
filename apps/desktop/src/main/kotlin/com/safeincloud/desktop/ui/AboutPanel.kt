package com.safeincloud.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safeincloud.desktop.AppViewModel
import com.safeincloud.desktop.BuildConfig
import com.safeincloud.desktop.GitHubUpdater

@Composable
fun AboutPanel(viewModel: AppViewModel) {
    val state = viewModel.state.collectAsState().value

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text(
            BuildConfig.APP_NAME,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "版本 ${BuildConfig.APP_VERSION}（${BuildConfig.PLATFORM}）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        SectionCard("检查更新") {
            OutlinedButton(onClick = { viewModel.checkForUpdates() }, enabled = !state.updateChecking) {
                Text(if (state.updateChecking) "检查中…" else "检查更新")
            }
            state.updateInfo?.let { info ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "发现新版本 ${info.version}（${info.publishedAt}）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (info.notes.isNotBlank()) {
                    Text(info.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.openReleasePage() }) { Text("前往下载") }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("更新日志") {
            if (state.changelog.isEmpty()) {
                Text(
                    "点击「检查更新」查看各版本的新增功能与修复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.changelog.forEach { note ->
                    Text(
                        "v${note.version}    ${note.date}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    conciseNotes(note.notes).forEach { line ->
                        Text(
                            "· $line",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("关于") {
            Text(
                "SafePassword 是一款开源密码管理器，数据保存在本机，完全离线可用。" +
                    "支持 WebDAV 多设备同步、SafeInCloud 格式导入导出、密码安全检查。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(GitHubUpdater.RELEASES_PAGE)) }
            }) { Text("项目主页") }
        }
    }
}

private val NOISE = listOf(
    "包名", "versionCode", "versionName", "版本号", "系统要求", "适用系统", "适用版本",
    "安装说明", "安装包", "下载地址", "文件大小", "SHA", "MD5", "签名", "校验值", "更新于"
)

internal fun conciseNotes(raw: String, limit: Int = 8): List<String> =
    raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { line ->
            line.removePrefix("- ").removePrefix("* ").removePrefix("+ ").removePrefix("• ")
                .replace("**", "").replace("`", "")
        }
        .filter { line ->
            val lower = line.lowercase()
            NOISE.none { noise -> lower.contains(noise.lowercase()) } &&
                !line.startsWith("#") && !line.startsWith(">") && !line.startsWith("|") &&
                !line.startsWith("---") && !line.startsWith("![") &&
                !line.matches(Regex("""\d{4}[/-]\d{1,2}[/-]\d{1,2}"""))
        }
        .take(limit)
        .map { if (it.length > 80) it.take(80) + "…" else it }
        .toList()
