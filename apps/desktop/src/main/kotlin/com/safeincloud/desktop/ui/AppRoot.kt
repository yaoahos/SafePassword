package com.safeincloud.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.safeincloud.desktop.AppViewModel
import com.safeincloud.desktop.DesktopScreen
import com.safeincloud.desktop.SidebarSection

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()

    when (state.screen) {
        DesktopScreen.Unlock -> UnlockScreen(state, viewModel)
        DesktopScreen.Vault -> VaultWorkspace(state, viewModel)
    }

    state.error?.let { message ->
        ErrorDialog(message) { viewModel.clearError() }
    }
    state.message?.let { message ->
        LaunchedMessage(message) { viewModel.clearMessage() }
    }
}

@Composable
private fun VaultWorkspace(state: com.safeincloud.desktop.DesktopUiState, viewModel: AppViewModel) {
    Row(Modifier.fillMaxSize()) {
        Sidebar(state, viewModel)
        ItemListPanel(state, viewModel, Modifier.width(320.dp).fillMaxHeight())
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (state.section) {
                SidebarSection.Security -> SecurityPanel(state, viewModel)
                SidebarSection.Tools -> ToolsPanel(state, viewModel)
                SidebarSection.Settings -> SettingsPanel(state, viewModel)
                SidebarSection.Sync -> SyncPanel(state, viewModel)
                SidebarSection.About -> AboutPanel(viewModel)
                else -> ItemDetailPanel(state, viewModel)
            }
        }
    }
}

private data class SectionSpec(val section: SidebarSection, val icon: ImageVector, val label: String)

private val sectionSpecs = listOf(
    SectionSpec(SidebarSection.All, Icons.Outlined.Folder, "所有项目"),
    SectionSpec(SidebarSection.Favorites, Icons.Outlined.Favorite, "收藏夹"),
    SectionSpec(SidebarSection.Archived, Icons.Outlined.Archive, "已归档"),
    SectionSpec(SidebarSection.Trash, Icons.Outlined.Delete, "回收站"),
    SectionSpec(SidebarSection.Security, Icons.Outlined.Security, "密码安全"),
    SectionSpec(SidebarSection.Tools, Icons.Outlined.Build, "工具"),
    SectionSpec(SidebarSection.Sync, Icons.Outlined.Language, "WebDAV 同步"),
    SectionSpec(SidebarSection.Settings, Icons.Outlined.Settings, "设置"),
    SectionSpec(SidebarSection.About, Icons.Outlined.Info, "关于")
)

@Composable
private fun Sidebar(state: com.safeincloud.desktop.DesktopUiState, viewModel: AppViewModel) {
    NavigationRail(modifier = Modifier.width(180.dp).fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "SafePassword",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            state.vault?.name?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        sectionSpecs.forEach { spec ->
            NavigationRailItem(
                selected = state.section == spec.section,
                onClick = { viewModel.selectSection(spec.section) },
                icon = { Icon(spec.icon, contentDescription = spec.label) },
                label = { Text(spec.label) },
                alwaysShowLabel = false
            )
        }
        Spacer(Modifier.weight(1f))
        NavigationRailItem(
            selected = false,
            onClick = { viewModel.lock() },
            icon = {
                Box(
                    Modifier.size(28.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = "锁定",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            },
            label = { Text("锁定") },
            alwaysShowLabel = false
        )
        Spacer(Modifier.height(12.dp))
    }
}
