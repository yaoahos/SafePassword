package com.safeincloud.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safeincloud.desktop.AppViewModel
import com.safeincloud.desktop.DesktopUiState
import com.safeincloud.desktop.ExportKind
import com.safeincloud.desktop.ImportKind
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Files

@Composable
fun ToolsPanel(state: DesktopUiState, viewModel: AppViewModel) {
    var status by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("工具", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))

        SectionCard("导入") {
            Text("支持 SafeInCloud XML、CSV、JSON 格式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val text = pickFile("导入 SafeInCloud XML", "xml")
                    if (text != null) { viewModel.importItemsFromText(text, ImportKind.SafeInCloudXml); status = "已导入 XML" }
                    else status = "已取消"
                }) { Text("导入 XML") }
                OutlinedButton(onClick = {
                    val text = pickFile("导入 CSV", "csv")
                    if (text != null) { viewModel.importItemsFromText(text, ImportKind.Csv); status = "已导入 CSV" }
                    else status = "已取消"
                }) { Text("导入 CSV") }
                OutlinedButton(onClick = {
                    val text = pickFile("导入 JSON", "json")
                    if (text != null) { viewModel.importItemsFromText(text, ImportKind.Json); status = "已导入 JSON" }
                    else status = "已取消"
                }) { Text("导入 JSON") }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("导出") {
            Text("导出明文数据，请妥善保管导出文件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val content = viewModel.exportItems(ExportKind.SafeInCloudXml)
                    if (content != null) { saveFile("导出 XML", "export.xml", content); status = "已导出 XML" }
                }) { Text("导出 XML") }
                OutlinedButton(onClick = {
                    val content = viewModel.exportItems(ExportKind.Csv)
                    if (content != null) { saveFile("导出 CSV", "export.csv", content); status = "已导出 CSV" }
                }) { Text("导出 CSV") }
                OutlinedButton(onClick = {
                    val content = viewModel.exportItems(ExportKind.Json)
                    if (content != null) { saveFile("导出 JSON", "export.json", content); status = "已导出 JSON" }
                }) { Text("导出 JSON") }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("备份") {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.backupNow() }) { Text("立即备份") }
                OutlinedButton(onClick = { viewModel.refreshBackups() }) { Text("刷新列表") }
            }
            Spacer(Modifier.height(8.dp))
            if (state.backups.isEmpty()) {
                Text("暂无备份", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.backups.forEach { backup ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(backup.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton("恢复") { viewModel.restoreBackup(backup) }
                        TextButton("删除") { viewModel.deleteBackup(backup) }
                    }
                }
            }
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TextButton(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text(text) }
}

private fun pickFile(title: String, extension: String): String? {
    val dialog = object : FileDialog(null as Frame?, title, FileDialog.LOAD) {
        init { setFilenameFilter { _, name -> name.lowercase().endsWith(".$extension") } }
    }
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return runCatching { Files.readString(java.nio.file.Path.of(dialog.directory, file)) }.getOrNull()
}

private fun saveFile(title: String, defaultName: String, content: String) {
    val dialog = object : FileDialog(null as Frame?, title, FileDialog.SAVE) {
        init { file = defaultName }
    }
    dialog.isVisible = true
    val name = dialog.file ?: return
    runCatching { Files.writeString(java.nio.file.Path.of(dialog.directory, name), content) }
}
