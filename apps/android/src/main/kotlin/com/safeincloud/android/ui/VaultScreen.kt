package com.safeincloud.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeincloud.android.R
import com.safeincloud.core.model.Label
import com.safeincloud.core.model.VaultItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(viewModel: AppViewModel, state: AppUiState) {
    var sortMenu by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBulkLabels by remember { mutableStateOf(false) }
    var visibleSyncStatus by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.syncStatus, state.syncStatusAt) {
        val elapsed = System.currentTimeMillis() - state.syncStatusAt
        if (state.syncStatus.isBlank() || elapsed >= 3000L) {
            visibleSyncStatus = ""
        } else {
            visibleSyncStatus = state.syncStatus
            delay(3000L - elapsed)
            visibleSyncStatus = ""
        }
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val itemList = remember(
        state.activeVault,
        state.search,
        state.sortMode,
        state.sortAscending,
        state.filterLabelId,
        state.onlyFavorites,
        state.settings.searchPasswords,
        state.dataVersion
    ) { visibleItems(state) }
    val labels = state.activeVault?.labels.orEmpty()
        .sortedWith(compareBy<Label> { it.sortOrder }.thenBy { it.name })
    val listState = rememberLazyListState()
    val loadIcons = true
    val activeCount = state.activeVault?.activeItems()?.size ?: 0
    val activeLabelName = labels.firstOrNull { it.id == state.filterLabelId }?.name
    val collectionTitle = when {
        state.onlyFavorites -> "收藏"
        !state.filterLabelId.isNullOrBlank() -> activeLabelName ?: "标签"
        else -> "全部"
    }
    val vaultName = state.activeMeta?.name ?: "我的密码库"
    // 标题保持分类名不变，条目数与排序方式统一放在副标题里。
    val collectionSubtitle = "$vaultName · ${itemList.size} 个条目"
    val selectedItems = itemList.filter { it.id in selectedIds }
    val allSelected = itemList.isNotEmpty() && selectedIds.size == itemList.size
    val commonLabelIds = if (selectedItems.isEmpty()) {
        emptySet()
    } else {
        labels.filter { label -> selectedItems.all { label.id in it.labelIds } }
            .map { it.id }
            .toSet()
    }
    val toggleSelection: (String) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        if (selectedIds.isEmpty()) selectionMode = false
    }

    // While the search field is focused, system back clears the query and focus
    // instead of leaving the vault screen.
    BackHandler(enabled = searchFocused) {
        viewModel.setSearch("")
        searchFocused = false
        focusManager.clearFocus()
        keyboard?.hide()
    }

    // While items are selected, system back only clears the selection. It must not
    // fall through to AppRoot and finish the activity / go to the launcher.
    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    // Back while the drawer is open closes the drawer instead of leaving the app.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(state.search, state.filterLabelId, state.onlyFavorites, state.sortMode, state.sortAscending) {
        selectedIds = emptySet()
        selectionMode = false
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SafeSidebar(
                vaultName = state.activeMeta?.name ?: "我的密码库",
                itemCount = activeCount,
                screen = Screen.Vault,
                favoritesOnly = state.onlyFavorites,
                trashSelected = false,
                onClose = { scope.launch { drawerState.close() } },
                onAllItems = {
                    scope.launch { drawerState.close() }
                    viewModel.setOnlyFavorites(false)
                    viewModel.setFilterLabel(null)
                    viewModel.navigate(Screen.Vault)
                },
                onFavorites = {
                    scope.launch { drawerState.close() }
                    viewModel.setOnlyFavorites(true)
                    viewModel.setFilterLabel(null)
                    viewModel.navigate(Screen.Vault)
                },
                onTrash = {
                    scope.launch { drawerState.close() }
                    viewModel.navigate(Screen.Trash)
                },
                onLabels = {
                    scope.launch { drawerState.close() }
                    viewModel.navigate(Screen.LabelManager())
                },
                onSettings = {
                    scope.launch { drawerState.close() }
                    viewModel.navigate(Screen.Settings)
                },
                onLock = {
                    scope.launch { drawerState.close() }
                    viewModel.lock()
                },
                onAppNameClick = {
                    scope.launch { drawerState.close() }
                    viewModel.checkForUpdates()
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                if (selectionMode) {
                    SelectionTopBar(
                        count = selectedIds.size,
                        allSelected = allSelected,
                        onClose = {
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                        onSelectAll = {
                            selectedIds = if (allSelected) emptySet() else itemList.map { it.id }.toSet()
                        }
                    )
                } else {
                    SafeCompactTopBar(
                        title = stringResource(R.string.app_name),
                        onMenu = { scope.launch { drawerState.open() } },
                        onSync = viewModel::syncNow
                    )
                }
            },
            floatingActionButton = {
                if (!selectionMode) {
                    FloatingActionButton(
                        onClick = { viewModel.navigate(Screen.EditItem(null)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = "新建条目",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            },
            bottomBar = {
                if (selectionMode) {
                    SelectionActionsBar(
                        selectedCount = selectedIds.size,
                        onDuplicate = {
                            selectedIds.firstOrNull()?.let(viewModel::duplicateItem)
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                        onLabels = {
                            if (labels.isEmpty()) {
                                viewModel.showMessage("还没有标签，请先在侧边栏的标签管理中新建")
                            } else {
                                showBulkLabels = true
                            }
                        },
                        onDelete = { showDeleteConfirm = true }
                    )
                } else {
                    SafeBottomBar(selected = Screen.Vault, onNavigate = viewModel::navigate)
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(4.dp))
                    VaultSearchField(
                        value = state.search,
                        onValueChange = viewModel::setSearch,
                        searchPasswords = state.settings.searchPasswords,
                        onSearch = { },
                        onFocusChanged = { searchFocused = it }
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                collectionTitle,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.6).sp
                            )
                            Text(
                                collectionSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { sortMenu = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Sort,
                                    contentDescription = "排序",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                SortMode.entries.forEach { mode ->
                                    val active = mode == state.sortMode
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                buildString {
                                                    append(
                                                        when (mode) {
                                                            SortMode.TITLE -> "按标题"
                                                            SortMode.UPDATED -> "按修改时间"
                                                            SortMode.CREATED -> "按创建时间"
                                                        }
                                                    )
                                                    if (active) {
                                                        append(if (state.sortAscending) "  ↑" else "  ↓")
                                                    }
                                                },
                                                color = if (active) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            viewModel.setSortMode(mode)
                                            sortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (labels.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            SafeChip(
                                text = "全部",
                                selected = state.filterLabelId == null,
                                onClick = { viewModel.setFilterLabel(null) }
                            )
                        }
                        items(labels, key = { it.id }) { label ->
                            SafeChip(
                                text = label.name,
                                selected = state.filterLabelId == label.id,
                                onClick = {
                                    viewModel.setFilterLabel(
                                        if (state.filterLabelId == label.id) null else label.id
                                    )
                                }
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                }

                if (visibleSyncStatus.isNotBlank()) {
                    Text(
                        visibleSyncStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }

                val wide = LocalWideLayout.current
                if (itemList.isEmpty()) {
                    EmptyState(
                        icon = if (state.onlyFavorites) Icons.Outlined.Star else Icons.Outlined.Lock,
                        title = if (state.onlyFavorites) "没有收藏条目" else "还没有条目",
                        subtitle = if (state.onlyFavorites) {
                            "点击条目右侧星标即可收藏"
                        } else {
                            "点击右下角 + 添加第一个账号"
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 2.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (wide) {
                            items(
                                itemList.chunked(2),
                                key = { chunk -> chunk.first().id },
                                contentType = { "vault-row-2" }
                            ) { chunk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    chunk.forEach { item ->
                                        ItemCard(
                                            modifier = Modifier.weight(1f),
                                            item = item,
                                            vault = state.activeVault,
                                            selected = item.id in selectedIds,
                                            selectionMode = selectionMode,
                                            loadIcon = loadIcons,
                                            onClick = {
                                                if (selectionMode) {
                                                    toggleSelection(item.id)
                                                } else {
                                                    viewModel.navigate(Screen.ViewItem(item.id))
                                                }
                                            },
                                            onLongClick = {
                                                selectionMode = true
                                                selectedIds = setOf(item.id)
                                            },
                                            onToggleSelection = { toggleSelection(item.id) },
                                            onFavorite = { viewModel.toggleFavorite(item.id) }
                                        )
                                    }
                                    repeat(2 - chunk.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            items(
                                itemList,
                                key = { it.id },
                                contentType = { "vault-item" }
                            ) { item ->
                                ItemCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    item = item,
                                    vault = state.activeVault,
                                    selected = item.id in selectedIds,
                                    selectionMode = selectionMode,
                                    loadIcon = loadIcons,
                                    onClick = {
                                        if (selectionMode) {
                                            toggleSelection(item.id)
                                        } else {
                                            viewModel.navigate(Screen.ViewItem(item.id))
                                        }
                                    },
                                    onLongClick = {
                                        selectionMode = true
                                        selectedIds = setOf(item.id)
                                    },
                                    onToggleSelection = { toggleSelection(item.id) },
                                    onFavorite = { viewModel.toggleFavorite(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "移入回收站",
            text = "确定删除选中的 ${selectedIds.size} 个条目吗？可以在回收站中恢复。",
            confirmText = "删除",
            destructive = true,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                val ids = selectedIds
                selectionMode = false
                selectedIds = emptySet()
                viewModel.deleteItems(ids)
            }
        )
    }

    if (showBulkLabels) {
        BulkLabelDialog(
            labels = labels,
            selected = commonLabelIds,
            onDismiss = { showBulkLabels = false },
            onConfirm = { ids ->
                showBulkLabels = false
                val itemIds = selectedIds
                selectionMode = false
                selectedIds = emptySet()
                viewModel.setLabelsForItems(itemIds, ids)
            }
        )
    }
}

@Composable
private fun CompactVaultTopBar(
    onMenu: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Menu, contentDescription = "菜单", modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("SafePassword", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = "设置",
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                )
            }
        }
    }
}

@Composable
internal fun SelectionTopBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "退出选择")
            }
            Text(
                "$count 已选择",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSelectAll, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (allSelected) Icons.Outlined.Check else Icons.Outlined.SelectAll,
                    contentDescription = if (allSelected) "取消全选" else "全选",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SelectionActionsBar(
    selectedCount: Int,
    onDuplicate: () -> Unit,
    onLabels: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(52.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SelectionAction(
                icon = Icons.Outlined.ContentCopy,
                label = "制作副本",
                enabled = selectedCount == 1,
                onClick = onDuplicate
            )
            SelectionAction(
                icon = Icons.Outlined.Label,
                label = "设置标签",
                enabled = selectedCount > 0,
                onClick = onLabels
            )
            SelectionAction(
                icon = Icons.Outlined.DeleteOutline,
                label = "删除",
                enabled = selectedCount > 0,
                color = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = color)
    }
}

@Composable
private fun SafeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
        shape = RoundedCornerShape(50),
        modifier = Modifier.height(30.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = null
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemCard(
    modifier: Modifier = Modifier,
    item: VaultItem,
    vault: com.safeincloud.core.model.Vault?,
    selected: Boolean,
    selectionMode: Boolean,
    loadIcon: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onFavorite: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(2.dp))
            }
            ItemIcon(item = item, size = 36.dp, corner = 10.dp, load = loadIcon)
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title.ifBlank { "未命名" },
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.favorite) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.Star,
                            contentDescription = "收藏",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                val subtitle = item.subtitle
                Text(
                    subtitle.ifBlank { " " },
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.labelIds.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(13.dp)
                    ) {
                        item.labelIds.take(2).forEach { id ->
                            val label = vault?.label(id) ?: return@forEach
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ColorDot(labelColor(label.color), 5)
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    label.name,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(13.dp))
                }
            }
            if (!selectionMode) {
                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (item.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = "收藏",
                        tint = if (item.favorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BulkLabelDialog(
    labels: List<Label>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var checked by remember(selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置标签") },
        text = {
            if (labels.isEmpty()) {
                Text("还没有标签，请先在标签管理中新建。")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(labels, key = { it.id }) { label ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    checked = if (label.id in checked) {
                                        checked - label.id
                                    } else {
                                        checked + label.id
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = label.id in checked,
                                onCheckedChange = {
                                    checked = if (label.id in checked) {
                                        checked - label.id
                                    } else {
                                        checked + label.id
                                    }
                                }
                            )
                            ColorDot(labelColor(label.color), 9)
                            Spacer(Modifier.width(8.dp))
                            Text(label.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(checked) }) { Text("应用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
