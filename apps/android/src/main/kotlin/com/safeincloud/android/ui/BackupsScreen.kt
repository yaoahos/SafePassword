package com.safeincloud.android.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safeincloud.core.storage.BackupInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsScreen(viewModel: AppViewModel, state: AppUiState) {
    var restoreTarget by remember { mutableStateOf<BackupInfo?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshBackups() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("本地备份") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("加密快照", fontWeight = FontWeight.SemiBold)
                        Text(
                            "备份是完整的 .sicv 文件，需要对应主密码才能打开。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { viewModel.backupNow() }, modifier = Modifier.fillMaxWidth()) {
                    Text("立即备份")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "自动备份：${if (state.settings.autoBackupDays <= 0) "关闭" else "每 ${state.settings.autoBackupDays} 天"} · " +
                        "上次 ${formatDate(state.settings.lastBackupAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.backups.isEmpty()) {
                EmptyState(Icons.Outlined.Backup, "还没有备份", "点击“立即备份”创建第一份快照")
            } else {
                state.backups.forEach { backup ->
                    SectionCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { restoreTarget = backup },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Restore, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(backup.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "${formatDate(backup.createdAt)} · ${formatSize(backup.size)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { restoreTarget = backup }) {
                                Text("恢复")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    restoreTarget?.let { backup ->
        ConfirmDialog(
            title = "恢复备份",
            text = "当前密码库会被这份备份覆盖。恢复后需要用该备份的主密码解锁，确定继续？",
            confirmText = "恢复",
            destructive = true,
            onDismiss = { restoreTarget = null },
            onConfirm = {
                restoreTarget = null
                viewModel.restoreBackup(backup)
            }
        )
    }
}
