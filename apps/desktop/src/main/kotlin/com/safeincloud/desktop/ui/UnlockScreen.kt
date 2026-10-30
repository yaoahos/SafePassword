package com.safeincloud.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.safeincloud.desktop.AppViewModel
import com.safeincloud.desktop.BuildConfig
import com.safeincloud.desktop.DesktopUiState

@Composable
fun UnlockScreen(state: DesktopUiState, viewModel: AppViewModel) {
    var password by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(state.vaults.isEmpty()) }
    var selectedVaultId by remember { mutableStateOf(state.settings.lastVaultId.ifBlank { state.vaults.firstOrNull()?.id.orEmpty() }) }
    val selectedVault = state.vaults.firstOrNull { it.id == selectedVaultId } ?: state.vaults.firstOrNull()

    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            BuildConfig.APP_NAME,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "安全地存储你的密码与私密信息",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (state.vaults.isNotEmpty()) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("主密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(0.4f).padding(bottom = 16.dp)
            )
            Button(
                onClick = {
                    selectedVault?.let { vault ->
                        viewModel.unlock(vault, password.toCharArray()) { password = "" }
                    }
                },
                enabled = !state.busy && password.isNotEmpty() && selectedVault != null,
                modifier = Modifier.fillMaxWidth(0.4f)
            ) {
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("解锁")
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                if (state.vaults.size > 1) {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Text(selectedVault?.name ?: "选择密码库")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = { showCreate = true }) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新建密码库")
                }
            }
        } else {
            Text(
                "还没有密码库，先创建一个开始使用",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp),
                textAlign = TextAlign.Center
            )
            Button(onClick = { showCreate = true }) {
                Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("创建密码库")
            }
        }

        Spacer(Modifier.height(40.dp))
        Text(
            "数据保存在本机 ${viewModel.dataDirText()}，完全离线可用",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showCreate) {
        CreateVaultDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, pw ->
                viewModel.createVault(name, pw) { ok ->
                    if (ok) showCreate = false
                }
            }
        )
    }
}

@Composable
private fun CreateVaultDialog(onDismiss: () -> Unit, onCreate: (String, CharArray) -> Unit) {
    var name by remember { mutableStateOf("我的密码库") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建新密码库") },
        text = {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("主密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("确认主密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    password.length < 4 -> error = "主密码至少 4 位"
                    password != confirm -> error = "两次输入的主密码不一致"
                    else -> onCreate(name.ifBlank { "我的密码库" }, password.toCharArray())
                }
            }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
