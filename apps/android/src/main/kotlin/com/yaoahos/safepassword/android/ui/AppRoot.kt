package com.yaoahos.safepassword.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaoahos.safepassword.android.MainActivity
import com.yaoahos.safepassword.android.security.BiometricHelper
import com.yaoahos.safepassword.android.ui.theme.SafePasswordTheme
import com.yaoahos.safepassword.android.update.ApkInstaller
import com.yaoahos.safepassword.core.sync.ConflictStrategy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.font.FontWeight

@Composable
fun AppRoot(viewModel: AppViewModel, activity: MainActivity) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val screenStateHolder = rememberSaveableStateHolder()

    LaunchedEffect(state.settings.screenshotAllowed) {
        activity.setSecure(!state.settings.screenshotAllowed)
    }

    LaunchedEffect(Unit) {
        // 从通知点安装失败的提示。通知的点击事件比 Compose 首帧先到，
        // 所以先挂在 Activity 上，等界面起来后再交给 snackbar 显示。
        activity.consumePendingInstallError()?.let { viewModel.showMessage(it) }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val showJob = launch {
            snackbar.showSnackbar(message, duration = SnackbarDuration.Short)
        }
        delay(2200)
        snackbar.currentSnackbarData?.dismiss()
        showJob.cancel()
        viewModel.consumeMessage()
    }

    BackHandler(
        enabled = state.screen != Screen.Unlock &&
            state.screen != Screen.Vault &&
            state.screen != Screen.Tools
    ) {
        viewModel.goBack()
    }

    SafePasswordTheme(
        themeColor = Color(state.settings.themeColor),
        darkTheme = when (state.settings.themeMode) {
            "LIGHT" -> false
            "DARK" -> true
            else -> isSystemInDarkTheme()
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            CompositionLocalProvider(LocalWideLayout provides wide) {
                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbar,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) { data ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .wrapContentWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1C1B1F),
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = data.visuals.message,
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 11.dp
                                        ),
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = if (wide) 1100.dp else 760.dp)
                        ) {
                            if (!state.initialized) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else {
                                AnimatedContent(
                                    targetState = state.screen,
                                    transitionSpec = {
                                        // 参考主流密码管理器的层级切换：
                                        // 标签页之间只做淡入淡出，进入下一层从右侧滑入，返回上一层反向滑出。
                                        val tabChange = initialState.isTabScreen() && targetState.isTabScreen()
                                        val transform = when {
                                            tabChange -> (
                                                fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                                                    scaleIn(
                                                        initialScale = 0.98f,
                                                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                                                    )
                                                ) togetherWith fadeOut(tween(160, easing = FastOutSlowInEasing))

                                            state.navForward -> (
                                                slideInHorizontally(
                                                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                                                ) { it / 3 } +
                                                    fadeIn(tween(240, delayMillis = 40))
                                                ) togetherWith (
                                                slideOutHorizontally(
                                                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                                                ) { -it / 3 } +
                                                    fadeOut(tween(150))
                                                )

                                            else -> (
                                                slideInHorizontally(
                                                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                                                ) { -it / 3 } +
                                                    fadeIn(tween(240, delayMillis = 40))
                                                ) togetherWith (
                                                slideOutHorizontally(
                                                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                                                ) { it / 3 } +
                                                    fadeOut(tween(150))
                                                )
                                        }
                                        transform.using(SizeTransform(clip = false))
                                    },
                                    label = "screen"
                                ) { animated ->
                                    screenStateHolder.SaveableStateProvider(animated.toString()) {
                                        when (val screen = animated) {
                                            Screen.Unlock -> UnlockScreen(viewModel, state, activity)
                                            Screen.Vault -> VaultScreen(viewModel, state)
                                            Screen.Tools -> ToolsScreen(viewModel, state)
                                            Screen.Security -> SecurityScreen(viewModel, state)
                                            Screen.Settings -> SettingsScreen(viewModel, state, activity)
                                            is Screen.ViewItem -> ItemViewScreen(viewModel, state, screen.itemId)
                                            is Screen.EditItem -> ItemEditScreen(viewModel, state, screen.itemId, screen.fromView)
                                            is Screen.WebDav -> WebDavScreen(viewModel, state, screen.restore)
                                            Screen.Backups -> BackupsScreen(viewModel, state)
                                            Screen.VaultManager -> VaultManagerScreen(viewModel, state)
                                            is Screen.LabelManager -> LabelManagerScreen(viewModel, state, screen.create)
                                            Screen.Trash -> TrashScreen(viewModel, state)
                                            Screen.Theme -> ThemeScreen(viewModel, state)
                                            Screen.About -> AboutScreen(viewModel, state)
                                        }
                                    }
                                }
                                if (state.busy) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }

                state.error?.let { error ->
                    AlertDialog(
                        onDismissRequest = { viewModel.consumeError() },
                        title = { Text("出错了") },
                        text = { Text(error) },
                        confirmButton = {
                            TextButton(onClick = { viewModel.consumeError() }) { Text("知道了") }
                        }
                    )
                }

                state.updateInfo?.let { info ->
                    if (state.updateDialogVisible) {
                        UpdateAvailableDialog(
                            info = info,
                            onDismiss = { viewModel.dismissUpdateDialog() },
                            onUpdate = {
                                activity.ensureNotificationPermission()
                                viewModel.downloadUpdate()
                            }
                        )
                    }
                }

                state.updateReadyFile?.let { path ->
                    UpdateReadyDialog(
                        version = state.updateInfo?.version.orEmpty(),
                        onInstall = {
                            viewModel.consumeUpdateReady()
                            ApkInstaller.install(activity, File(path))
                        },
                        onDismiss = { viewModel.consumeUpdateReady() }
                    )
                }

                if (state.showChangelog) {
                    ChangelogDialog(
                        notes = state.changelog,
                        checking = state.updateChecking,
                        onDismiss = { viewModel.dismissChangelog() },
                        onOpenReleases = { viewModel.openReleasesPage() }
                    )
                }

                if (state.fingerprintPrompt) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissFingerprintPrompt() },
                        title = { Text("开启指纹解锁？") },
                        text = {
                            Text("以后可以用系统指纹快速解锁这个密码库，主密码仍然可以随时使用。")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val cipher = viewModel.prepareFingerprintEnrollment()
                                viewModel.dismissFingerprintPrompt()
                                if (cipher == null) {
                                    viewModel.showError("系统安全存储不可用，无法开启指纹解锁")
                                } else {
                                    BiometricHelper.authenticate(
                                        activity,
                                        cipher,
                                        onSuccess = { authenticated ->
                                            viewModel.enableFingerprint(authenticated)
                                        },
                                        onFailure = { _, message -> viewModel.showError(message) }
                                    )
                                }
                            }) { Text("开启") }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissFingerprintPrompt() }) { Text("以后再说") }
                        }
                    )
                }

                if (state.nameConflictMessage != null) {
                    val currentName = state.activeMeta?.name ?: ""
                    var newName by remember(state.nameConflictMessage) { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissNameConflict() },
                        title = null,
                        text = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(38.dp)
                                            .background(
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.CloudOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "云端已有同名密码库",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    "云端已经有一个叫「$currentName」的密码库，和本机这个不是同一份数据。" +
                                        "两者会并存，同步和恢复时很难区分。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    label = { Text("给本机这个库换一个名字") },
                                    placeholder = { Text("例如：$currentName（手机）") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "改名后会立即重新同步，云端另存一份。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { viewModel.renameAfterNameConflict(newName) },
                                enabled = newName.isNotBlank()
                            ) { Text("改名并同步", fontWeight = FontWeight.SemiBold) }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissNameConflict() }) { Text("稍后") }
                        },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }

                if (state.remoteDeletedName != null) {
                    val pending = state.syncStatus.contains("未同步的修改")
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("云端已删除该密码库") },
                        text = {
                            Text(
                                "「${state.remoteDeletedName}」在其它设备上被删除了。" +
                                    if (pending) "\n\n本机这份还有未同步的修改，删除本地会一并丢失。" else "" +
                                    "\n\n删除本地：本机这份也一起删掉，两端保持一致。" +
                                    "\n恢复到云端：保留本机这份，重新上传到云端。"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { viewModel.deleteRemoteDeletedVault() }) {
                                Text("删除本地")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.restoreRemoteDeletedToCloud() }) {
                                Text("恢复到云端")
                            }
                        }
                    )
                }

                if (state.syncConflict) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissSyncConflict() },
                        title = { Text("同步冲突") },
                        text = {
                            Text("本地与云端都有新的修改。请选择保留哪一份，或合并两边并保留冲突副本。")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.resolveSyncConflict(ConflictStrategy.MERGE)
                                }
                            ) { Text("合并") }
                        },
                        dismissButton = {
                            Row {
                                TextButton(
                                    onClick = {
                                        viewModel.resolveSyncConflict(ConflictStrategy.KEEP_LOCAL)
                                    }
                                ) { Text("保留本地") }
                                TextButton(
                                    onClick = {
                                        viewModel.resolveSyncConflict(ConflictStrategy.KEEP_REMOTE)
                                    }
                                ) { Text("使用云端") }
                            }
                        }
                    )
                }
            }
        }
    }
}

/** 底部标签页级别的页面：之间切换只做淡入淡出，不做层级滑动。 */
private fun Screen.isTabScreen(): Boolean =
    this == Screen.Vault || this == Screen.Tools || this == Screen.Unlock
