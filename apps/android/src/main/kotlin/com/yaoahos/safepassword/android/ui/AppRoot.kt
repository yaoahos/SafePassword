package com.yaoahos.safepassword.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.font.FontWeight

@Composable
fun AppRoot(viewModel: AppViewModel, activity: MainActivity) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 顶部彩色提示条：message 走成功态、error 走错误态。
    // 用队列而不是单值：连续两次操作（例如保存后立刻同步）时前一条不会被顶掉，
    // 后一条排队等前一条自动消失或用户点掉。
    var noticeQueue by remember { mutableStateOf<List<NoticeItem>>(emptyList()) }
    var noticeSeq by remember { mutableStateOf(0L) }
    // 退出动画播放期间 queue 可能已经空了，这里留一份最后显示的内容，
    // 否则 AnimatedVisibility 会直接消失而不是滑出。
    var lastShown by remember { mutableStateOf<NoticeItem?>(null) }
    val screenStateHolder = rememberSaveableStateHolder()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 超时锁定：解锁后（或改了超时时间）启动前台闲置计时。
    LaunchedEffect(state.unlocked, state.settings.autoLockSeconds) {
        if (state.unlocked) viewModel.restartIdleWatch()
    }

    // 进入详情/编辑/设置等覆盖页时收起输入法。
    // 以前在搜索结果里点条目打开详情，搜索框仍持有焦点，键盘会一直挂在详情页下面。
    LaunchedEffect(state.screen) {
        if (state.screen != Screen.Vault && state.screen != Screen.Tools) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    LaunchedEffect(state.settings.screenshotAllowed) {
        activity.setSecure(!state.settings.screenshotAllowed)
    }

    LaunchedEffect(Unit) {
        // 从通知点安装失败的提示。通知的点击事件比 Compose 首帧先到，
        // 所以先挂在 Activity 上，等界面起来后再交给顶部提示条显示。
        activity.consumePendingInstallError()?.let { viewModel.showMessage(it) }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        noticeSeq += 1
        noticeQueue = noticeQueue + NoticeItem(noticeSeq, message, NoticeStyle.SUCCESS)
        viewModel.consumeMessage()
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        noticeSeq += 1
        noticeQueue = noticeQueue + NoticeItem(noticeSeq, error, NoticeStyle.ERROR)
        viewModel.consumeError()
    }

    val currentNotice = noticeQueue.firstOrNull()
    LaunchedEffect(currentNotice) {
        if (currentNotice != null) lastShown = currentNotice
    }

    // 解锁页不注册 BackHandler：系统返回（含屏幕左缘右滑手势）走 Activity 默认行为 = 退出到桌面。
    // 1.0.13 时这里注册了一个空的 BackHandler 来「防误触」，结果返回键与侧滑手势全部变成无反应，
    // 解锁页只剩 Home 键能离开。锁定状态下密码库已加密落盘、内存密钥已清除，退出应用不会泄密，
    // 重新打开仍要解锁——所以这里放行，让侧滑返回和系统返回键都能正常退出。
    // 注意：解锁页的「返回上一页」语义不存在（没有上一页），自定义边缘手势也不接管（见 canEdgeBack）。
    if (state.screen != Screen.Unlock) {
        BackHandler(
            enabled = state.screen != Screen.Vault &&
                state.screen != Screen.Tools
        ) {
            viewModel.goBack()
        }
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
            // 左边缘右滑返回：只在「覆盖层页面」上生效，标签页不拦截。
            // 判定放在根节点、并在手指按下那一刻就判起手位置——以前放在 AnimatedContent 上、
            // 用 detectHorizontalDragGestures 的 onDragStart（越过 touch slop 之后才回调），
            // 拿到的 x 已经往右偏了一个 slop，20dp 的边条实际只剩几个 dp 可用，
            // 于是「第一次滑没反应、再来一次才回去」。
            val canEdgeBack = state.screen != Screen.Vault &&
                state.screen != Screen.Tools &&
                state.screen != Screen.Unlock
            val canEdgeBackState = rememberUpdatedState(canEdgeBack)
            CompositionLocalProvider(LocalWideLayout provides wide) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // 任何触摸都算「用户还在用」，前台闲置超时据此重新计时。
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    viewModel.onUserInteraction()
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            val edgePx = PageMotion.EDGE_DP.dp.toPx()
                            val commitPx = size.width * PageMotion.COMMIT_RATIO
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (!canEdgeBackState.value || down.position.x > edgePx) {
                                    return@awaitEachGesture
                                }
                                val slop = viewConfiguration.touchSlop
                                var dragged = 0f
                                var claimed = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes
                                        .firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    dragged = change.position.x - down.position.x
                                    val dy = change.position.y - down.position.y
                                    // 横向位移明显大于纵向才算「右滑」，纵向滑动交给列表滚动。
                                    if (!claimed && dragged > slop && dragged > kotlin.math.abs(dy)) {
                                        claimed = true
                                    }
                                    if (claimed) change.consume()
                                }
                                if (claimed && dragged >= commitPx) viewModel.goBack()
                            }
                        }
                ) {
                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    snackbarHost = {}
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
                            } else if (state.screen == Screen.Unlock) {
                                // 直接切换不用 AnimatedContent：转场动画期间窗口大面积重绘，
                                // MIUI 可能误判为应用卡死/泄漏而杀进程（表现为「锁定后闪到桌面」）。
                                screenStateHolder.SaveableStateProvider("Unlock") {
                                    UnlockScreen(viewModel, state, activity)
                                }
                            } else {
                                Box(Modifier.fillMaxSize()) {
                                    MainTabShell(viewModel, state, screenStateHolder)
                                            AnimatedContent(
                                                targetState = state.screen
                                                    .takeIf { it != Screen.Vault && it != Screen.Tools },
                                                transitionSpec = {
                                                    // 从 tab 层进来一定是前进；回到 tab 层（target 为 null）
                                                    // 一定是返回。其余情况看 ViewModel 记录的方向。
                                                    val forward = targetState != null && state.navForward
                                                    pageTransition(forward)
                                                },
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clipToBounds(),
                                                label = "overlay"
                                            ) { target ->
                                                // 每页铺一层不透明底色：页面根节点不一定自带背景，
                                                // 转场那 300ms 里透明部分会透出下层内容，出现残影。
                                                //
                                                // target 为 null（回到标签页）时这里也要占满一屏：
                                                // AnimatedContent 会对「目标内容的尺寸」做 SizeTransform
                                                // 动画，目标内容为空就会被从全屏压到 0×0，
                                                // 视觉上正是「返回时整页朝中间缩小一下」。尺寸恒定就只剩平移。
                                                // 但空页不能带底色，否则会把下面的标签页盖住。
                                                Box(
                                                    Modifier
                                                        .fillMaxSize()
                                                        .then(
                                                            if (target != null) {
                                                                Modifier.background(MaterialTheme.colorScheme.background)
                                                            } else {
                                                                Modifier
                                                            }
                                                        )
                                                ) {
                                                    if (target != null) {
                                                        screenStateHolder.SaveableStateProvider(target.toString()) {
                                                            when (val screen = target) {
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
                                                                else -> {}
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                if (state.busy) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                    }
                }

                // 顶部彩色提示条（替代旧底部黑底 snackbar）：
                // 圆角卡片限宽居中、顶部滑入、自动消失、点一下即关。
                AnimatedVisibility(
                    visible = currentNotice != null,
                    enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(tween(220)),
                    exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(tween(150)),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    val shown = currentNotice ?: lastShown
                    if (shown != null) {
                        TopNotice(
                            message = shown.text,
                            style = shown.style,
                            onDismiss = {
                                noticeQueue = if (noticeQueue.isNotEmpty()) {
                                    noticeQueue.drop(1)
                                } else {
                                    noticeQueue
                                }
                            },
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(top = 6.dp, start = 12.dp, end = 12.dp),
                            seq = shown.seq
                        )
                    }
                }
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
                    InputDialog(
                        title = "云端已有同名密码库",
                        onDismiss = { viewModel.dismissNameConflict() },
                        confirmText = "改名并同步",
                        confirmEnabled = newName.isNotBlank(),
                        dismissText = "稍后",
                        onConfirm = { viewModel.renameAfterNameConflict(newName) }
                    ) {
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


/** 顶部提示队列里的一条。[seq] 用来区分文案相同的连续提示（否则计时器不会重开）。 */
private data class NoticeItem(val seq: Long, val text: String, val style: NoticeStyle)
