package com.safeincloud.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One 48dp top bar for both main tabs. Keeping the menu, title and right action
 * in the same slots makes switching between Vault and Tools visually stable.
 */
@Composable
fun SafeCompactTopBar(
    title: String,
    onMenu: () -> Unit,
    onSync: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp)
        ) {
            IconButton(
                onClick = onMenu,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterStart)
            ) {
                Icon(
                    Icons.Outlined.Menu,
                    contentDescription = "菜单",
                    modifier = Modifier.size(21.dp)
                )
            }
            Text(
                title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            if (onSync != null) {
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSync, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Outlined.Sync,
                            contentDescription = "立即同步",
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }
    }
}
