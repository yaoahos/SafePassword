package com.safeincloud.android.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** true when the window is at least 600dp wide (tablet or landscape phone). */
val LocalWideLayout = staticCompositionLocalOf { false }
