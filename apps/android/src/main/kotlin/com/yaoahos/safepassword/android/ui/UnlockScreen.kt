package com.yaoahos.safepassword.android.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.yaoahos.safepassword.android.MainActivity
import com.yaoahos.safepassword.android.security.BiometricHelper
import kotlinx.coroutines.delay

@Composable
fun UnlockScreen(viewModel: AppViewModel, state: AppUiState, activity: MainActivity) {
    // 只有一个库时直接选中它；删库后 activeMeta 会为空，否则按钮一直是灰的。
    val meta = state.activeMeta ?: state.vaults.singleOrNull()
    val focusRequester = remember { FocusRequester() }
    var passwordFocused by remember { mutableStateOf(false) }
    var password by remember(meta?.id) { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var autoPrompted by remember(meta?.id) { mutableStateOf(false) }
    var autoRetries by remember(meta?.id) { mutableStateOf(0) }

    fun startFingerprint(auto: Boolean = false) {
        val cipher = viewModel.prepareBiometricUnlock()
        if (cipher == null) {
            // 凭据已失效（指纹被重录等）：再怎么弹指纹也解不开，
            // 直接关掉指纹解锁，回到主密码解锁，避免反复报错。
            viewModel.disableFingerprint("指纹凭据已失效（可能指纹被重新录入），已关闭指纹解锁，请用主密码解锁")
            return
        }
        BiometricHelper.authenticate(
            activity,
            cipher,
            onSuccess = { authenticated -> viewModel.unlockWithBiometricCipher(authenticated) },
            onFailure = { code, message ->
                when {
                    // 用户自己点了"使用主密码"或返回键：属于预期操作，保持安静。
                    BiometricHelper.isUserCancelled(code) -> Unit
                    // 界面刚回到前台时，系统可能直接丢弃这次请求（ERROR_CANCELED）。
                    // 自动弹出的场景下补一次重试，避免"什么都没发生"。
                    auto && code == BiometricPrompt.ERROR_CANCELED && autoRetries < 1 -> {
                        autoRetries++
                        activity.window?.decorView?.postDelayed({
                            if (!activity.isFinishing &&
                                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                            ) {
                                startFingerprint(auto = true)
                            }
                        }, 450L)
                    }
                    code == BiometricPrompt.ERROR_CANCELED -> Unit
                    // 传感器损坏/无法处理/永久锁定：指纹已经不可用，自动关掉开关，
                    // 否则设置页还会要求"先验证指纹才能关"，形成死锁。
                    BiometricHelper.isHardwareError(code) ->
                        viewModel.disableFingerprint("指纹识别不可用，已自动关闭指纹解锁：$message")
                    else -> viewModel.showError(message)
                }
            }
        )
    }

    // 锁定后优先弹出指纹。必须等 Activity 真正回到 RESUMED、窗口稳定之后再请求，
    // 否则系统会静默丢弃 BiometricPrompt，表现为"偶尔不弹指纹"。
    LaunchedEffect(
        meta?.id,
        state.biometricSaved,
        state.settings.fingerprintEnabled,
        state.fingerprintAvailable
    ) {
        if (autoPrompted ||
            !state.biometricSaved ||
            !state.settings.fingerprintEnabled ||
            !state.fingerprintAvailable
        ) {
            return@LaunchedEffect
        }
        autoPrompted = true
        var waited = 0L
        while (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && waited < 2_000L) {
            delay(40L)
            waited += 40L
        }
        delay(180L)
        startFingerprint(auto = true)
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
                        val selected = vault.id == meta?.id
                        val primary = MaterialTheme.colorScheme.primary
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            onClick = { viewModel.selectVault(vault) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    vault.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) primary else Color(0xFF30343B)
                                )
                            }
                        }
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
                val fpButtonColors = if (passwordFocused) {
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
                val fpButtonBorder = if (passwordFocused) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                } else {
                    null
                }
                if (passwordFocused) {
                    OutlinedButton(
                        onClick = { startFingerprint() },
                        enabled = state.fingerprintAvailable,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fpButtonColors,
                        border = fpButtonBorder
                    ) {
                        Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (state.fingerprintAvailable) "用指纹解锁" else "指纹不可用")
                    }
                } else {
                    Button(
                        onClick = { startFingerprint() },
                        enabled = state.fingerprintAvailable,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fpButtonColors
                    ) {
                        Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (state.fingerprintAvailable) "用指纹解锁" else "指纹不可用")
                    }
                }
                LaunchedEffect(passwordFocused) {
                    if (passwordFocused) focusRequester.requestFocus()
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
                onToggleVisibility = { visible = !visible },
                modifier = Modifier
                    .then(
                        if (state.biometricSaved && state.settings.fingerprintEnabled) {
                            Modifier
                                .focusRequester(focusRequester)
                                .onFocusChanged { passwordFocused = it.isFocused }
                        } else {
                            Modifier
                        }
                    )
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
                PasswordField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "确认主密码",
                    visible = visible,
                    onToggleVisibility = { visible = !visible }
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
                name = fileName.removeSuffix(".spv")
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
                    Text(if (uri == null) "选择本地 .spv 文件" else "已选择：$fileName")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "仅支持本应用导出的加密 .spv 密码库；恢复后会作为新的密码库加入，不会覆盖现有密码库。",
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
    }.getOrNull() ?: uri.lastPathSegment ?: "密码库.spv"
}

private fun viewModelStrength(password: String) =
    com.yaoahos.safepassword.core.security.PasswordStrength.evaluate(password)
