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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safeincloud.desktop.AppViewModel
import com.safeincloud.desktop.DesktopUiState

@Composable
fun SettingsPanel(state: DesktopUiState, viewModel: AppViewModel) {
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))

        SectionCard("搜索") {
            SettingRow(
                title = "搜索时匹配密码和秘密字段",
                subtitle = "关闭后搜索不会命中密码/秘密的值",
                checked = state.settings.searchPasswords,
                onCheckedChange = { checked -> viewModel.updateSettings { it.copy(searchPasswords = checked) } }
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("同步") {
            SettingRow(
                title = "保存后自动同步",
                subtitle = "仅在已配置并启用 WebDAV 时生效",
                checked = state.settings.autoSync,
                onCheckedChange = { checked -> viewModel.updateSettings { it.copy(autoSync = checked) } }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.selectSection(com.safeincloud.desktop.SidebarSection.Sync) }) {
                Text("配置 WebDAV 同步")
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("数据") {
            Text("数据目录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(viewModel.dataDirText(), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                runCatching { java.awt.Desktop.getDesktop().open(java.io.File(viewModel.dataDirText())) }
            }) { Text("打开数据目录") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.lock() }) {
                Text("立即锁定", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
