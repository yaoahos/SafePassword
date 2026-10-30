package com.safeincloud.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.safeincloud.desktop.ui.AppRoot

fun main() = application {
    val viewModel = remember { AppViewModel() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "SafePassword",
        state = rememberWindowState(width = 1100.dp, height = 720.dp)
    ) {
        AppTheme {
            AppRoot(viewModel)
        }
    }
}
