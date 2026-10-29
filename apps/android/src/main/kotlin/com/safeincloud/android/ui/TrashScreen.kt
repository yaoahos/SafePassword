package com.safeincloud.android.ui

import java.text.DateFormat
import java.util.Date

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeincloud.core.model.VaultItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(viewModel: AppViewModel, state: AppUiState) {
    val items = trashedItems(state)
    var confirmEmpty by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<VaultItem?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("回收站", fontWeight = FontWeight.Bold, fontSize = 21.sp) },
                actions = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) {
                            Text("清空", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 32.dp)
        ) {
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Delete,
                        title = "回收站是空的",
                        subtitle = "移入回收站的条目会出现在这里"
                    )
                }
            } else {
                items(items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigate(Screen.ViewItem(item.id)) }
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ItemIcon(item = item, size = 42.dp, corner = 12.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title.ifBlank { "未命名" }, fontWeight = FontWeight.SemiBold)
                            Text(
                                "删除于 " + DateFormat.getDateTimeInstance(
                                    DateFormat.SHORT,
                                    DateFormat.SHORT
                                ).format(Date(item.deletedAt ?: item.updatedAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.restoreItem(item.id) }) {
                            Icon(Icons.Outlined.Restore, contentDescription = "恢复")
                        }
                        IconButton(onClick = { deleting = item }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "永久删除")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                }
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = "清空回收站",
            text = "回收站中的 ${items.size} 个条目将被永久删除，无法恢复。",
            confirmText = "永久删除",
            destructive = true,
            onDismiss = { confirmEmpty = false },
            onConfirm = {
                confirmEmpty = false
                viewModel.emptyTrash()
            }
        )
    }

    deleting?.let { item ->
        ConfirmDialog(
            title = "永久删除",
            text = "“${item.title}”将被永久删除，无法恢复。",
            confirmText = "永久删除",
            destructive = true,
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                viewModel.permanentlyDeleteItem(item.id)
            }
        )
    }
}
