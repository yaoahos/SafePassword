package com.safeincloud.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.safeincloud.android.BuildConfig

/** 项目开源地址：点击"关于"页中间的标题即可跳转。 */
private const val PROJECT_URL = "https://github.com/yaoahos/SafePassword"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(viewModel: AppViewModel, state: AppUiState) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("关于") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.height(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "SafePassword",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    runCatching { uriHandler.openUri(PROJECT_URL) }
                }
            )
            Text(
                "版本 ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            SectionCard(title = "功能范围") {
                Text(
                    "离线优先的 Android 密码管理器：多密码库、自定义字段、上传图标、标签、回收站、" +
                        "密码生成器、强度分析、HIBP 泄露检查、WebDAV 同步、主题换色、系统生物识别。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "不包含密码填充/自动填充、浏览器扩展填表、共享协作和 Passkey 登录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(title = "技术栈") {
                Text("Kotlin + Jetpack Compose（Android）")
                Text("Kotlin/JVM core 模块：加密、数据库、同步、导入导出")
                Text("Ktor Client + OkHttp（WebDAV 与 HIBP）")
                Text("AES-256-GCM + PBKDF2-HMAC-SHA256")
            }

            SectionCard(title = "开源致谢") {
                Text(
                    "本项目使用以下开源项目与库；各自遵循其仓库中的开源许可证。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Kotlin" to "https://kotlinlang.org",
                    "Jetpack Compose" to "https://developer.android.com/jetpack/compose",
                    "AndroidX" to "https://developer.android.com/jetpack/androidx",
                    "Ktor" to "https://ktor.io",
                    "OkHttp" to "https://square.github.io/okhttp/",
                    "kotlinx.serialization" to "https://github.com/Kotlin/kotlinx.serialization",
                    "kotlinx.coroutines" to "https://github.com/Kotlin/kotlinx.coroutines",
                    "AndroidX Biometric" to "https://developer.android.com/jetpack/androidx/releases/biometric",
                    "AndroidX Security Crypto" to "https://developer.android.com/jetpack/androidx/releases/security"
                ).forEach { (name, url) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { runCatching { uriHandler.openUri(url) } }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }
            }

            SectionCard(title = "安全说明") {
                Text(
                    "主密码只在本机用于派生密钥，不会上传。数据库文件在设备上加密后再同步。" +
                        "生物识别通过系统 BiometricPrompt 调起，主密码加密存储在 EncryptedSharedPreferences 中。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(title = "更新") {
                SettingRow(
                    title = "检查更新",
                    subtitle = when {
                        state.updateChecking -> "正在检查…"
                        state.updateDownloading -> "正在下载新版本：${state.updateProgress.coerceIn(0, 100)}%"
                        state.updateInfo != null -> "发现新版本 v${state.updateInfo.version}"
                        else -> "从 GitHub Releases 获取最新版本"
                    },
                    onClick = { viewModel.checkForUpdates() },
                    trailing = {
                        if (state.updateChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                        }
                    }
                )
                SettingRow(
                    title = "更新日志",
                    subtitle = "查看各版本的新增功能与修复",
                    onClick = { viewModel.openChangelog() },
                    trailing = { Icon(Icons.Outlined.History, contentDescription = null) }
                )
                if (state.updateDownloading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.updateProgress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "SafePassword 是独立实现，参考 SafeInCloud UI，但与官方无关联。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
