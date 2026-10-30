package com.safeincloud.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.safeincloud.desktop.AppViewModel
import com.safeincloud.desktop.DesktopUiState
import com.safeincloud.desktop.SidebarSection
import com.safeincloud.core.model.VaultItem
import com.safeincloud.core.model.TemplateCatalog
import com.safeincloud.core.model.now

@Composable
fun ItemListPanel(state: DesktopUiState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    var templateMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.search,
                onValueChange = { viewModel.setSearch(it) },
                placeholder = { Text("搜索…", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (state.search.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearch("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Box {
                FilledTonalButton(onClick = { templateMenuExpanded = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建")
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = templateMenuExpanded,
                    onDismissRequest = { templateMenuExpanded = false }
                ) {
                    TemplateCatalog.templates.forEach { template ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(template.name) },
                            onClick = {
                                templateMenuExpanded = false
                                viewModel.startCreate(template.id)
                            }
                        )
                    }
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("空白条目") },
                        onClick = {
                            templateMenuExpanded = false
                            viewModel.startCreate(null)
                        }
                    )
                }
            }
        }

        val items = viewModel.visibleItems()
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        state.search.isNotBlank() -> "没有匹配的项目"
                        state.section == SidebarSection.Trash -> "回收站是空的"
                        state.section == SidebarSection.Favorites -> "还没有收藏的项目"
                        else -> "还没有项目，点右上角「新建」添加"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        selected = item.id == state.selectedItemId,
                        onClick = { viewModel.selectItem(item.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(item) },
                        labelNames = item.labelIds.mapNotNull { id -> state.vault?.label(id)?.name }
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: VaultItem,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    labelNames: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp).let {
            if (selected) it else it
        },
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 1.dp else 0.dp),
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title.ifBlank { "未命名" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (item.subtitle.isNotBlank()) {
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (labelNames.isNotEmpty()) {
                    Text(
                        labelNames.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (item.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    contentDescription = "收藏",
                    tint = if (item.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
