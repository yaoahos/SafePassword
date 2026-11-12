package com.yaoahos.safepassword.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yaoahos.safepassword.android.R
import com.yaoahos.safepassword.core.model.Label
import kotlinx.coroutines.launch

/**
 * 主标签页壳层：顶栏（菜单 / 标题 / 同步 / 锁定）与底栏固定在壳层里，
 * 切换「密码库 ⇄ 工具」时只有中间内容区做淡入淡出，
 * 避免相同区域跟着整页转场重画产生频闪。
 *
 * 多选模式下由壳层把顶栏/底栏整体替换成 SelectionTopBar / SelectionActionsBar，
 * 多选状态因此上提到壳层持有。
 */
@Composable
fun MainTabShell(
    viewModel: AppViewModel,
    state: AppUiState,
    screenStateHolder: SaveableStateHolder
) {
    // 被下层页面（设置、编辑等）盖住时壳层仍在合成，这里记住最后一个 tab，
    // 返回时内容区无缝回到原标签页。
    var lastTab by remember { mutableStateOf<Screen>(Screen.Vault) }
    val tab: Screen = when (state.screen) {
        Screen.Vault, Screen.Tools -> state.screen
        else -> lastTab
    }
    LaunchedEffect(state.screen) {
        if (state.screen == Screen.Vault || state.screen == Screen.Tools) {
            lastTab = state.screen
        }
    }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBulkLabels by remember { mutableStateOf(false) }

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
    val selectedItems = itemList.filter { it.id in selectedIds }
    val allSelected = itemList.isNotEmpty() && selectedIds.size == itemList.size
    val commonLabelIds = if (selectedItems.isEmpty()) {
        emptySet()
    } else {
        labels.filter { label -> selectedItems.all { label.id in it.labelIds } }
            .map { it.id }
            .toSet()
    }

    val clearSelection: () -> Unit = {
        selectionMode = false
        selectedIds = emptySet()
    }

    // 多选与抽屉打开时，系统返回只退掉这两者，不离开主页面。
    BackHandler(enabled = selectionMode) { clearSelection() }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SafeSidebar(
                vaultName = state.activeMeta?.name ?: "我的密码库",
                itemCount = state.activeVault?.activeItems()?.size ?: 0,
                screen = tab,
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
        Column(modifier = Modifier.fillMaxSize()) {
            // ---- 固定顶栏：切 tab 时只换标题文字，槽位不动 ----
            if (tab == Screen.Vault && selectionMode) {
                SelectionTopBar(
                    count = selectedIds.size,
                    allSelected = allSelected,
                    onClose = clearSelection,
                    onSelectAll = {
                        selectedIds = if (allSelected) emptySet() else itemList.map { it.id }.toSet()
                    }
                )
            } else {
                SafeCompactTopBar(
                    title = if (tab == Screen.Tools) "工具" else stringResource(R.string.app_name),
                    onMenu = { scope.launch { drawerState.open() } },
                    onSync = viewModel::syncNow,
                    onLock = viewModel::lock
                )
            }

            // ---- 中间内容区：唯一随 tab 转场的区域 ----
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (
                            fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                                scaleIn(
                                    initialScale = 0.98f,
                                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                                )
                            ) togetherWith fadeOut(tween(160, easing = FastOutSlowInEasing))
                    },
                    label = "tab-content"
                ) { target ->
                    screenStateHolder.SaveableStateProvider(target.toString()) {
                        when (target) {
                            Screen.Tools -> ToolsContent(viewModel, state)
                            else -> VaultContent(
                                viewModel = viewModel,
                                state = state,
                                selectionMode = selectionMode,
                                selectedIds = selectedIds,
                                onToggleSelection = { id ->
                                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                                    if (selectedIds.isEmpty()) selectionMode = false
                                },
                                onEnterSelection = { id ->
                                    selectionMode = true
                                    selectedIds = setOf(id)
                                },
                                onClearSelection = clearSelection
                            )
                        }
                    }
                }
                if (tab == Screen.Vault && !selectionMode) {
                    FloatingActionButton(
                        onClick = { viewModel.navigate(Screen.EditItem(null)) },
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = "新建条目",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // ---- 固定底栏：多选时整体替换为批量操作条 ----
            if (tab == Screen.Vault && selectionMode) {
                SelectionActionsBar(
                    selectedCount = selectedIds.size,
                    onDuplicate = {
                        selectedIds.firstOrNull()?.let(viewModel::duplicateItem)
                        clearSelection()
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
                SafeBottomBar(selected = tab, onNavigate = viewModel::navigate)
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
                clearSelection()
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
                clearSelection()
                viewModel.setLabelsForItems(itemIds, ids)
            }
        )
    }
}
