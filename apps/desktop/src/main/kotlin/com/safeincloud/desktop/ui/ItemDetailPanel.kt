package com.safeincloud.desktop.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.safeincloud.core.model.FieldType
import com.safeincloud.core.model.ItemStatus
import com.safeincloud.core.model.VaultItem
import com.safeincloud.desktop.AppViewModel
import com.safeincloud.desktop.DesktopUiState
import com.safeincloud.core.security.PasswordStrength
import kotlinx.coroutines.launch

@Composable
fun ItemDetailPanel(state: DesktopUiState, viewModel: AppViewModel) {
    if (state.editingItem != null) {
        ItemEditor(state, viewModel)
        return
    }
    val item = viewModel.selected()
    if (item == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("选择左侧的项目查看详情", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title.ifBlank { "未命名" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    com.safeincloud.core.model.TemplateCatalog.displayName(item.templateId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { viewModel.toggleFavorite(item) }) {
                Icon(
                    if (item.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (item.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { viewModel.startEdit() }) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑")
            }
            if (item.status == ItemStatus.TRASHED) {
                IconButton(onClick = { viewModel.restoreItem(item) }) {
                    Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "恢复")
                }
                IconButton(onClick = { viewModel.purgeItem(item) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "彻底删除", tint = MaterialTheme.colorScheme.error)
                }
            } else {
                IconButton(onClick = { viewModel.archiveItem(item) }) {
                    Icon(Icons.Outlined.Archive, contentDescription = "归档")
                }
                IconButton(onClick = { viewModel.deleteItem(item) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            item.fields.filter { it.value.isNotBlank() || it.type == FieldType.PASSWORD }.forEach { field ->
                FieldRow(
                    label = field.label,
                    value = field.value,
                    type = field.type,
                    onCopy = {
                        scope.launch {
                            copyToClipboard(field.value)
                            snackbar.showSnackbar("已复制${field.label}")
                        }
                    }
                )
            }
            if (item.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("备注", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.notes, style = MaterialTheme.typography.bodyMedium)
            }
            val labelNames = item.labelIds.mapNotNull { id -> state.vault?.label(id)?.name }
            if (labelNames.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("标签", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(labelNames.joinToString("、"), style = MaterialTheme.typography.bodyMedium)
            }
        }

        SnackbarHost(snackbar)
    }
}

@Composable
private fun FieldRow(label: String, value: String, type: FieldType, onCopy: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val sensitive = type == FieldType.PASSWORD || type == FieldType.SECRET
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val transformation: VisualTransformation =
                    if (sensitive && !visible) PasswordVisualTransformation() else VisualTransformation.None
                Text(
                    if (sensitive && value.isEmpty()) "（空）" else value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (sensitive && !visible && value.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 3
                )
                if (type == FieldType.PASSWORD && value.isNotBlank()) {
                    val strength = PasswordStrength.evaluate(value)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        strength.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (strength.score) {
                            0, 1 -> MaterialTheme.colorScheme.error
                            2 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }
        if (sensitive && value.isNotBlank()) {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.width(36.dp)) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = "显示/隐藏",
                    modifier = Modifier.width(18.dp)
                )
            }
        }
        IconButton(onClick = onCopy, modifier = Modifier.width(36.dp)) {
            Icon(Icons.Outlined.CopyAll, contentDescription = "复制", modifier = Modifier.width(18.dp))
        }
        if (type == FieldType.URL && value.isNotBlank()) {
            IconButton(onClick = {
                runCatching {
                    java.awt.Desktop.getDesktop().browse(java.net.URI(value))
                }
            }, modifier = Modifier.width(36.dp)) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = "打开", modifier = Modifier.width(18.dp))
            }
        }
    }
}
