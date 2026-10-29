package com.safeincloud.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.safeincloud.core.model.Label

private val labelPalette = listOf(
    0xFF1E88E5.toInt(), 0xFF3949AB.toInt(), 0xFF00897B.toInt(), 0xFF43A047.toInt(),
    0xFF7CB342.toInt(), 0xFFFDD835.toInt(), 0xFFFB8C00.toInt(), 0xFFF4511E.toInt(),
    0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFF8E24AA.toInt(), 0xFF5E35B1.toInt(),
    0xFF6D4C41.toInt(), 0xFF546E7A.toInt(), 0xFF00ACC1.toInt(), 0xFF26A69A.toInt(),
    0xFF66BB6A.toInt(), 0xFF9CCC65.toInt(), 0xFFFFB300.toInt(), 0xFFFF7043.toInt(),
    0xFFEF5350.toInt(), 0xFFEC407A.toInt(), 0xFFAB47BC.toInt(), 0xFF7E57C2.toInt()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LabelManagerScreen(
    viewModel: AppViewModel,
    state: AppUiState,
    createOnOpen: Boolean = false
) {
    val vault = state.activeVault
    val labels = vault?.labels.orEmpty()
        .sortedWith(compareBy<Label> { it.sortOrder }.thenBy { it.name })
    var create by remember { mutableStateOf(createOnOpen) }
    var editing by remember { mutableStateOf<Label?>(null) }
    var deleting by remember { mutableStateOf<Label?>(null) }
    var help by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var deleteSelected by remember { mutableStateOf(false) }
    val allSelected = labels.isNotEmpty() && selectedIds.size == labels.size

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                        selectedIds = if (allSelected) {
                            emptySet()
                        } else {
                            labels.map { it.id }.toSet()
                        }
                    }
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { viewModel.goBack() }) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    },
                    title = {
                        Text(
                            "标签管理",
                            fontWeight = FontWeight.Bold,
                            fontSize = 21.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = { help = true }) {
                            Icon(Icons.Outlined.HelpOutline, contentDescription = "帮助")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (selectionMode) {
                LabelSelectionActionsBar(
                    selectedCount = selectedIds.size,
                    onDelete = { if (selectedIds.isNotEmpty()) deleteSelected = true }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = { create = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "新建标签", modifier = Modifier.size(26.dp))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 96.dp)
        ) {
            if (labels.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Label,
                        title = "还没有标签",
                        subtitle = "点击右下角 + 新建标签"
                    )
                }
            } else {
                items(labels, key = { it.id }) { label ->
                    val count = vault?.items?.count {
                        it.status == com.safeincloud.core.model.ItemStatus.ACTIVE &&
                            label.id in it.labelIds
                    } ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds = if (label.id in selectedIds) {
                                            selectedIds - label.id
                                        } else {
                                            selectedIds + label.id
                                        }
                                        if (selectedIds.isEmpty()) selectionMode = false
                                    } else {
                                        editing = label
                                    }
                                },
                                onLongClick = {
                                    selectionMode = true
                                    selectedIds = setOf(label.id)
                                }
                            )
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectionMode) {
                            Icon(
                                if (label.id in selectedIds) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (label.id in selectedIds) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        LabelGlyph(
                            color = labelColor(label.color),
                            size = 22.dp
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            label.name,
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            count.toString(),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                }
            }
        }
    }

    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            title = { Text("关于标签") },
            text = { Text("标签用于给条目分组。一个条目可以同时属于多个标签；点击标签可改名、换颜色或删除。") },
            confirmButton = {
                TextButton(onClick = { help = false }) { Text("知道了") }
            }
        )
    }

    if (create) {
        LabelEditDialog(
            initial = null,
            onDismiss = { create = false },
            onConfirm = { name, color, sortOrder ->
                create = false
                viewModel.addLabel(name, color, sortOrder)
            }
        )
    }
    editing?.let { label ->
        LabelEditDialog(
            initial = label,
            onDismiss = { editing = null },
            onConfirm = { name, color, sortOrder ->
                editing = null
                viewModel.updateLabel(
                    label.copy(name = name, color = color, sortOrder = sortOrder)
                )
            },
            onDelete = {
                editing = null
                deleting = label
            }
        )
    }
    deleting?.let { label ->
        ConfirmDialog(
            title = "删除标签",
            text = "删除“${label.name}”后，条目上的这个标签也会被移除。",
            confirmText = "删除",
            destructive = true,
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                viewModel.deleteLabel(label.id)
            }
        )
    }

    if (deleteSelected) {
        ConfirmDialog(
            title = "删除标签",
            text = "将删除选中的 ${selectedIds.size} 个标签，并从所有条目中移除。确定继续？",
            confirmText = "删除",
            destructive = true,
            onDismiss = { deleteSelected = false },
            onConfirm = {
                val ids = selectedIds
                deleteSelected = false
                selectionMode = false
                selectedIds = emptySet()
                viewModel.deleteLabels(ids)
            }
        )
    }
}

@Composable
private fun LabelSelectionActionsBar(
    selectedCount: Int,
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
            TextButton(
                onClick = onDelete,
                enabled = selectedCount > 0
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "删除",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LabelEditDialog(
    initial: Label?,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember(initial?.id) {
        mutableStateOf(initial?.color ?: labelPalette.first())
    }
    var sortOrder by remember(initial?.id) {
        mutableStateOf((initial?.sortOrder ?: 0).toString())
    }
    var showColorPicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Outlined.Close, contentDescription = "关闭")
                            }
                        },
                        title = {
                            Text(
                                if (initial == null) "新建标签" else "编辑标签",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    onConfirm(name.trim(), color, sortOrder.toIntOrNull() ?: 0)
                                },
                                enabled = name.isNotBlank()
                            ) { Text("保存", fontWeight = FontWeight.Bold) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("标签名称") },
                        singleLine = true,
                        colors = safeFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("颜色", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LabelGlyph(color = Color(color), size = 28.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("标签颜色", fontSize = 15.sp)
                            }
                        }
                        TextButton(onClick = { showColorPicker = true }) {
                            Text("选择颜色")
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = sortOrder,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.toIntOrNull() != null) sortOrder = value
                        },
                        label = { Text("排序") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        colors = safeFieldColors(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        supportingText = {
                            Text("填入数字，数字越小越靠前；会同步影响密码库顶部的标签顺序")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (onDelete != null) {
                        Spacer(Modifier.height(28.dp))
                        TextButton(
                            onClick = onDelete,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("删除标签", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        if (showColorPicker) {
            LabelColorPickerDialog(
                selected = color,
                onSelect = {
                    color = it
                    showColorPicker = false
                },
                onDismiss = { showColorPicker = false }
            )
        }
    }
}

@Composable
private fun LabelColorPickerDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(360.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 10.dp)) {
                Text("选择颜色", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(18.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    gridItems(labelPalette) { value ->
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .border(
                                    if (value == selected) 2.dp else 1.dp,
                                    if (value == selected) MaterialTheme.colorScheme.onSurface
                                    else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { onSelect(value) },
                            contentAlignment = Alignment.Center
                        ) {
                            LabelGlyph(color = Color(value), size = 27.dp)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消", fontSize = 17.sp) }
                }
            }
        }
    }
}
