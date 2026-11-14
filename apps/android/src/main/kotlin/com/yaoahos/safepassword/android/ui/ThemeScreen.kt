package com.yaoahos.safepassword.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val themePresets = listOf(
    0xFF1677FFL, 0xFFFF9500L, 0xFFEF4444L, 0xFFE11D48L, 0xFFDB2777L, 0xFFA855F7L,
    0xFF6366F1L, 0xFF2563EBL, 0xFF0891B2L, 0xFF0D9488L, 0xFF16A34AL,
    0xFF65A30DL, 0xFFCA8A04L, 0xFFEA580CL, 0xFF475569L,
    0xFF0F172AL, 0xFF334155L, 0xFF7C2D12L, 0xFF166534L, 0xFF581C87L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(viewModel: AppViewModel, state: AppUiState) {
    val current = state.settings.themeColor
    var custom by remember { mutableStateOf(hexOf(current)) }

    // 主题色被预设圆点或壁纸取色改动时，输入框跟着显示新色号；
    // 由输入框自己触发的变化不重写，否则打字到一半会被格式化打断光标。
    LaunchedEffect(current) {
        if (parseHexColor(custom) != current) custom = hexOf(current)
    }

    // 上次用的是「跟随桌面背景」时，进页面按当前壁纸静默校正一次（换了壁纸不必再点一次）。
    LaunchedEffect(Unit) {
        if (state.settings.themeColorAuto) viewModel.applyWallpaperAccent(silent = true)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("主题", fontWeight = FontWeight.Bold, fontSize = 21.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberEntryScrollState())
                                .padding(horizontal = 20.dp)
        ) {
            Text("外观模式", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(Modifier.fillMaxWidth().padding(4.dp)) {
                    listOf(
                        "SYSTEM" to "跟随系统",
                        "LIGHT" to "日间",
                        "DARK" to "夜间"
                    ).forEach { (mode, label) ->
                        val selected = state.settings.themeMode == mode
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (selected) 1.dp else 0.dp,
                            modifier = Modifier.weight(1f).clickable { viewModel.setThemeMode(mode) }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.applyWallpaperAccent() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 用一圈色相环表示「从画面里取色」，比放一个具体图标更贴这个动作
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color(0xFFFF3B30), Color(0xFFFFCC00), Color(0xFF34C759),
                                        Color(0xFF32ADE6), Color(0xFF5856D6), Color(0xFFFF3B30)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("跟随桌面背景", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "取当前桌面壁纸的主色作为主题色",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.settings.themeColorAuto) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "当前使用壁纸取色",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("预置颜色", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.height(250.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                gridItems(themePresets) { value ->
                    val selected = value == current
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .sizeIn(maxWidth = 44.dp, maxHeight = 44.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(Color(value), CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    viewModel.setThemeColor(value)
                                    viewModel.showMessage("主题色已更新")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = "当前主题色",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("自定义颜色", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 左侧色块就是输入值的即时预览：输入未凑满 #RRGGBB 时先显示当前主题色
                Surface(
                    modifier = Modifier
                        .size(44.dp),
                    shape = CircleShape,
                    color = Color(parseHexColor(custom) ?: current),
                    border = null
                ) {}
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { input ->
                        val cleaned = sanitizeHexInput(input)
                        custom = cleaned
                        // 凑满 6 位合法十六进制立刻生效，不用再点「应用」
                        parseHexColor(cleaned)?.let { value ->
                            if (value != current) viewModel.setThemeColor(value)
                        }
                    },
                    placeholder = { Text("#2563EB") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Black,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun hexOf(color: Long): String = String.format(Locale.US, "#%06X", color and 0xFFFFFFL)

/** 只留合法输入：# 只能在首位，十六进制统一大写，最多 7 位（# + 6 位）。 */
private fun sanitizeHexInput(input: String): String {
    val builder = StringBuilder()
    input.trim().forEachIndexed { index, ch ->
        val c = ch.uppercaseChar()
        when {
            index == 0 && c == '#' -> builder.append('#')
            c in '0'..'9' || c in 'A'..'F' -> builder.append(c)
        }
    }
    return if (builder.length > 7) builder.substring(0, 7) else builder.toString()
}

private fun parseHexColor(input: String): Long? {
    val cleaned = input.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    val rgb = cleaned.toLongOrNull(16) ?: return null
    return 0xFF000000L or rgb
}
