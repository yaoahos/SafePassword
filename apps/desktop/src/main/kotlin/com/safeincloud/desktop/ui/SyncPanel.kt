package com.safeincloud.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.safeincloud.core.sync.WebDavConfig
import com.safeincloud.desktop.AppViewModel
import com.safeincloud.desktop.DesktopUiState

@Composable
fun SyncPanel(state: DesktopUiState, viewModel: AppViewModel) {
    val config = state.settings.webDav

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("WebDAV 同步", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "配置坚果云、Nextcloud 等 WebDAV 服务，在多设备间同步密码库。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        SectionCard("服务器") {
            OutlinedTextField(
                value = config.baseUrl,
                onValueChange = { viewModel.updateWebDav(config.copy(baseUrl = it)) },
                label = { Text("服务器地址（含 https://）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = config.username,
                onValueChange = { viewModel.updateWebDav(config.copy(username = it)) },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = config.password,
                onValueChange = { viewModel.updateWebDav(config.copy(password = it)) },
                label = { Text("密码 / 应用专用密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = config.remoteDir,
                onValueChange = { viewModel.updateWebDav(config.copy(remoteDir = it)) },
                label = { Text("远程目录") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("开关") {
            SettingRow(
                title = "启用 WebDAV 同步",
                subtitle = "开启后可手动或自动同步",
                checked = config.enabled,
                onCheckedChange = { checked -> viewModel.updateWebDav(config.copy(enabled = checked)) }
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("操作") {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.testWebDav() }, enabled = !state.syncBusy && config.isConfigured()) {
                    Text("测试连接")
                }
                Button(onClick = { viewModel.syncNow() }, enabled = !state.syncBusy && config.enabled && config.isConfigured()) {
                    Text("立即同步")
                }
                OutlinedButton(onClick = { viewModel.pullRemote() }, enabled = !state.syncBusy && config.enabled && config.isConfigured()) {
                    Text("拉取云端")
                }
            }
            if (state.syncStatus.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(state.syncStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("说明") {
            Text(
                "· 首次同步会把本地数据上传到云端\n" +
                    "· 双端都有修改时自动合并，冲突项会保留为「冲突副本」\n" +
                    "· 云端数据用你的主密码加密，服务商无法读取\n" +
                    "· 主密码不同时会拒绝同步，避免数据损坏",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
