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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val labels = state.vault?.labels.orEmpty().sortedBy { it.sortOrder }
    Column(
        Modifier.width(200.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(top = 16.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            sectionSpecs.forEach { spec ->
                SidebarRow(
                    selected = state.section == spec.section && state.filterLabelId == null,
                    onClick = { viewModel.selectSection(spec.section); viewModel.filterByLabel(null) },
                    label = spec.label,
                    icon = { Icon(spec.icon, contentDescription = spec.label, modifier = Modifier.size(18.dp)) }
                )
            }

            if (labels.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                Text(
                    "标签",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                )
                labels.forEach { label ->
                    SidebarRow(
                        selected = state.filterLabelId == label.id,
                        onClick = { viewModel.filterByLabel(label.id) },
                        label = label.name,
                        dotColor = Color(label.color)
                    )
                }
            }
        }

        SidebarRow(
            selected = false,
            onClick = { viewModel.lock() },
            label = "锁定",
            icon = {
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = "锁定",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SidebarRow(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: (@Composable () -> Unit)? = null,
    dotColor: Color? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(10.dp))
        } else if (dotColor != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
