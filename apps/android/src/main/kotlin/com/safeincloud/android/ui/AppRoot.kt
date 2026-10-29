package com.safeincloud.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
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
import com.safeincloud.android.MainActivity
import com.safeincloud.android.ui.theme.SafeInCloudTheme
import com.safeincloud.core.sync.ConflictStrategy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppRoot(viewModel: AppViewModel, activity: MainActivity) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val screenStateHolder = rememberSaveableStateHolder()

    LaunchedEffect(state.settings.screenshotAllowed) {
        activity.setSecure(!state.settings.screenshotAllowed)
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

    SafeInCloudTheme(themeColor = Color(state.settings.themeColor)) {
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
                                        val tabChange = targetState == Screen.Vault ||
                                            targetState == Screen.Tools ||
                                            targetState == Screen.Unlock
                                        if (tabChange) {
                                            (
                                                fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                                                    scaleIn(
                                                        initialScale = 0.985f,
                                                        animationSpec = tween(
                                                            240,
                                                            easing = FastOutSlowInEasing
                                                        )
                                                    )
                                                ) togetherWith (
                                                fadeOut(tween(140)) +
                                                    scaleOut(
                                                        targetScale = 0.995f,
                                                        animationSpec = tween(
                                                            180,
                                                            easing = FastOutSlowInEasing
                                                        )
                                                    )
                                                )
                                        } else {
                                            (
                                                fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                                                    slideInHorizontally(
                                                        animationSpec = tween(
                                                            280,
                                                            easing = FastOutSlowInEasing
                                                        )
                                                    ) { it / 14 }
                                                ) togetherWith (
                                                fadeOut(tween(160)) +
                                                    slideOutHorizontally(
                                                        animationSpec = tween(
                                                            220,
                                                            easing = FastOutSlowInEasing
                                                        )
                                                    ) { -it / 24 }
                                                )
                                        }
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
                                            Screen.About -> AboutScreen(viewModel)
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
