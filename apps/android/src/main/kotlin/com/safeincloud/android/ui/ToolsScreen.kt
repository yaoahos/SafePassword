package com.safeincloud.android.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.safeincloud.android.R
import com.safeincloud.android.security.ClipboardHelper
import kotlinx.coroutines.launch

data class ToolEntry(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(viewModel: AppViewModel, state: AppUiState) {
    val context = LocalContext.current
    var showGenerator by remember { mutableStateOf(false) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val tools = listOf(
        ToolEntry(Icons.Outlined.Password, MaterialTheme.colorScheme.primary, "密码生成器", "生成强随机密码或口令短语") { showGenerator = true },
        ToolEntry(Icons.Outlined.Security, Color(0xFFFF3B30), "安全检查", "${state.security.breached.size + state.security.weak.size + state.security.reused.size} 个风险等待处理") {
            viewModel.navigate(Screen.Security)
        },
        ToolEntry(Icons.Outlined.Cloud, Color(0xFF30B0C7), "WebDAV 同步", if (state.webDav.isConfigured()) "已连接 · 点击配置" else "未配置") {
            viewModel.navigate(Screen.WebDav())
        },
        ToolEntry(Icons.Outlined.Palette, Color(0xFFAF52DE), "主题", "预置品牌色或自定义颜色") {
            viewModel.navigate(Screen.Theme)
        },
        ToolEntry(Icons.Outlined.Upload, Color(0xFF34C759), "导入密码", "从 SafePassword XML / CSV / JSON 导入") {
            importPicker.launch(arrayOf("*/*"))
        },
        ToolEntry(Icons.Outlined.Settings, Color(0xFF6B6B70), "设置", "生物识别、自动锁定、外观与数据") {
            viewModel.navigate(Screen.Settings)
        }
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SafeSidebar(
                vaultName = state.activeMeta?.name ?: "我的密码库",
                itemCount = state.activeVault?.activeItems()?.size ?: 0,
                screen = Screen.Tools,
                favoritesOnly = state.onlyFavorites,
                trashSelected = false,
                onClose = { scope.launch { drawerState.close() } },
                onAllItems = {
                    scope.launch { drawerState.close() }
                    viewModel.setOnlyFavorites(false)
                    viewModel.setFilterLabel(null)
                    viewModel.setSortMode(SortMode.TITLE)
                    viewModel.navigate(Screen.Vault)
                },
                onFavorites = {
                    scope.launch { drawerState.close() }
                    viewModel.setOnlyFavorites(true)
                    viewModel.setFilterLabel(null)
                    viewModel.setSortMode(SortMode.TITLE)
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
                }
            )
        }
    ) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SafeCompactTopBar(
                title = "工具",
                onMenu = { scope.launch { drawerState.open() } }
            )
        },
        bottomBar = {
            SafeBottomBar(selected = Screen.Tools, onNavigate = viewModel::navigate)
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (LocalWideLayout.current) 4 else 2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        "让密码更安全",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "生成、检查、同步与主题",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(tools) { tool ->
                ToolCard(tool)
            }
        }
    }
    }

    if (showGenerator) {
        GeneratorDialog(
            onDismiss = { showGenerator = false },
            onUse = { password ->
                ClipboardHelper.copySensitive(context, password)
                Toast.makeText(context, "密码已复制", Toast.LENGTH_SHORT).show()
                showGenerator = false
            }
        )
    }
}

@Composable
private fun ToolCard(tool: ToolEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = tool.onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(tool.tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(tool.icon, contentDescription = null, tint = tool.tint, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                tool.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tool.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
