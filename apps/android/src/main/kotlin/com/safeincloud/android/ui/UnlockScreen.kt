package com.safeincloud.android.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.safeincloud.android.MainActivity
import com.safeincloud.android.security.BiometricHelper

@Composable
fun UnlockScreen(viewModel: AppViewModel, state: AppUiState, activity: MainActivity) {
    val meta = state.activeMeta
    var password by remember(meta?.id) { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var autoPrompted by remember(meta?.id) { mutableStateOf(false) }

    // 锁定后优先弹出指纹；取消一次后仍可用下方按钮手动重试。
    LaunchedEffect(meta?.id, state.biometricSaved, state.settings.fingerprintEnabled) {
        if (!autoPrompted &&
            state.biometricSaved &&
            state.settings.fingerprintEnabled &&
            state.fingerprintAvailable
        ) {
            autoPrompted = true
            BiometricHelper.authenticate(
                activity,
                onSuccess = { viewModel.unlockWithFingerprint() },
                onError = { message ->
                    if (!message.contains("取消")) viewModel.showError(message)
                }
            )
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 480.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "SafePassword",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "AES-256 加密 · 仅保存在你的设备与自己的云",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        if (state.vaults.isEmpty()) {
            Text(
                "还没有密码库",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "创建一个密码库开始使用。主密码无法找回，请务必牢记。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("创建密码库")
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { showRestore = true }) {
                Text("从本地恢复")
            }
            TextButton(onClick = { viewModel.navigate(Screen.WebDav(restore = true)) }) {
                Text("从云端恢复")
            }
        } else {
            if (state.vaults.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(state.vaults, key = { it.id }) { vault ->
                        FilterChip(
                            selected = vault.id == meta?.id,
                            onClick = { viewModel.selectVault(vault) },
                            label = { Text(vault.name) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                meta?.name ?: "选择密码库",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(16.dp))
            if (state.biometricSaved && state.settings.fingerprintEnabled) {
                Button(
                    onClick = {
                        BiometricHelper.authenticate(
                            activity,
                            onSuccess = { viewModel.unlockWithFingerprint() },
                            onError = { message ->
                                if (!message.contains("取消")) viewModel.showError(message)
                            }
                        )
                    },
                    enabled = state.fingerprintAvailable,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (state.fingerprintAvailable) "用指纹解锁" else "指纹不可用")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "或使用主密码",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
            }
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "主密码",
                visible = visible,
                onToggleVisibility = { visible = !visible }
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    meta?.let { viewModel.unlock(it.id, password) }
                },
                enabled = meta != null && password.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("解锁")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    }

    if (showCreate) {
        CreateVaultDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, pwd ->
                showCreate = false
                viewModel.createVault(name, pwd)
            }
        )
    }

    if (showRestore) {
        RestoreVaultDialog(
            onDismiss = { showRestore = false },
            onRestoreLocal = { uri, pwd, name ->
                showRestore = false
                viewModel.importEncrypted(uri, pwd, name)
            }
        )
    }
}

@Composable
fun CreateVaultDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("我的密码库") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val strength = remember(password) { viewModelStrength(password) }
    val mismatch = confirm.isNotBlank() && password != confirm
    val canCreate = name.isNotBlank() && password.length >= 8 && password == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建密码库") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    colors = safeFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "主密码（至少 8 位）",
                    visible = visible,
                    onToggleVisibility = { visible = !visible }
                )
                Spacer(Modifier.height(6.dp))
                if (password.isNotBlank()) StrengthBar(strength)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("确认主密码") },
                    singleLine = true,
                    isError = mismatch,
                    colors = safeFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                )
                if (mismatch) {
                    Text("两次输入不一致", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "主密码不会上传，也无法找回。忘记主密码将无法解密数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, password) }, enabled = canCreate) {
                Text("创建")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun RestoreVaultDialog(
    onDismiss: () -> Unit,
    onRestoreLocal: (Uri, String, String) -> Unit
) {
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("我的密码库") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            uri = picked
            fileName = queryDisplayName(context, picked)
            if (name == "我的密码库") {
                name = fileName.removeSuffix(".sicv").removeSuffix(".db")
            }
        }
    }
    val enabled = password.length >= 8 && uri != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从本地恢复") },
        text = {
            Column {
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uri == null) "选择本地 .sicv 文件" else "已选择：$fileName")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "仅支持本应用导出的加密 .sicv 密码库；恢复后会作为新的密码库加入，不会覆盖现有密码库。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "该密码库的主密码",
                    visible = visible,
                    onToggleVisibility = { visible = !visible }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { uri?.let { onRestoreLocal(it, password, name.trim()) } },
                enabled = enabled
            ) {
                Text("恢复")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull() ?: uri.lastPathSegment ?: "密码库.sicv"
}

private fun viewModelStrength(password: String) =
    com.safeincloud.core.security.PasswordStrength.evaluate(password)
