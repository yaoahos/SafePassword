package com.safeincloud.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safeincloud.desktop.AppViewModel
import com.safeincloud.desktop.DesktopUiState

@Composable
fun SecurityPanel(state: DesktopUiState, viewModel: AppViewModel) {
    val report = state.security
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("密码安全", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.refreshSecurity() }) { Text("重新检查") }
            OutlinedButton(onClick = { viewModel.checkBreaches() }, enabled = !state.checkingBreaches) {
                Text(if (state.checkingBreaches) "检查中…" else "检查泄露")
            }
        }
        Spacer(Modifier.height(16.dp))

        if (report == null) {
            Text("点击「重新检查」分析密码库中的弱密码、重复密码和长期未更新的条目。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }

        StatRow("条目总数", report.total)
        StatRow("弱密码", report.weak.size)
        StatRow("重复密码", report.reused.size)
        StatRow("已泄露", report.breached.size)
        StatRow("超过 180 天未更新", report.old.size)

        Spacer(Modifier.height(12.dp))
        if (report.weak.isEmpty() && report.reused.isEmpty() && report.breached.isEmpty()) {
            Text("没有发现明显的安全问题。", color = MaterialTheme.colorScheme.primary)
        } else {
            LazyColumn {
                items(report.weak + report.reused + report.breached, key = { it.id }) { item ->
                    Text(
                        "· ${item.title.ifBlank { "未命名" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (count > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
}
