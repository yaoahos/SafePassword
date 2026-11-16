package com.yaoahos.safepassword.android.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.yaoahos.safepassword.android.ui.theme.DynamicAccentState
import com.yaoahos.safepassword.android.ui.theme.LocalDynamicAccentState

/** 主题页底部按色值来源显示不同的回退提示，避免两处文案写歪。 */
private enum class ThemeFallbackHint {
    NONE, PRESET, CUSTOM
}

private val themePresets = listOf(
    0xFF1677FFL, 0xFFFF9500L, 0xFFEF4444L, 0xFFE11D48L, 0xFFDB2777L, 0xFFA855F7L,
    0xFF6366F1L, 0xFF2563EBL, 0xFF0891B2L, 0xFF0D9488L, 0xFF16A34AL,
    0xFF65A30DL, 0xFFCA8A04L, 0xFFEA580CL, 0xFF475569L,
    0xFF0F172AL, 0xFF334155L, 0xFF7C2D12L, 0xFF166534L, 0xFF581C87L
)

/**
 * 主题页：外观模式 → 动态取色 → 预置颜色 → 自定义颜色。
 *
 * 动态取色的主色来自系统对壁纸的取色结果（[SafePasswordTheme] → WallpaperSeed.kt），
 * 只在这一层派生 primary，其余 surface / 容器层继续用中性色：既保证主色真的跟着
 * 壁纸走（不走会卡住的 accent overlay），也不会让弹窗和菜单跟着染色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(viewModel: AppViewModel, state: AppUiState) {
    val settings = state.settings
    // 12 以下没有系统取色接口：入口置灰并写明原因，不给一个点不动的开关
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicNow = settings.dynamicColor && dynamicSupported
    // 只有「真的在跟随壁纸」才算动态生效。开着开关却取不到壁纸色时（FALLBACK），
    // 界面实际用的是自选配色，预置色块要照常高亮正在用的那一块，否则看起来像配色丢了。
    val followingWallpaper = dynamicNow &&
        LocalDynamicAccentState.current == DynamicAccentState.FOLLOWING
    // 当前真正生效的主色：跟随壁纸时是壁纸派生色，否则就是自选色。
    val effective = if (followingWallpaper) MaterialTheme.colorScheme.primary else Color(settings.themeColor)
    val effectiveHex = hexOf(effective)
    // 动态取色开着却取不到壁纸色时，提示要跟着「实际生效的是哪一块选中色」走：
    // 命中预置就显示在预置区下方，否则显示在自定义区下方，不再固定堆在页面末尾。
    val fallbackHint = when {
        !dynamicNow || LocalDynamicAccentState.current != DynamicAccentState.FALLBACK ->
            ThemeFallbackHint.NONE
        themePresets.any { it == settings.themeColor } -> ThemeFallbackHint.PRESET
        else -> ThemeFallbackHint.CUSTOM
    }

    // 主题色一变（选预设、开/关动态取色）输入框就显示「现在生效的颜色」；
    // 用户自己输入时不改它，否则打字到一半会被格式化打断。
    var custom by remember(effectiveHex) { mutableStateOf(effectiveHex) }
    // 用户动过输入框没有：动过之后色块先跟输入走，否则「输一个色看一眼」永远是壁纸色
    var customDirty by remember(effectiveHex) { mutableStateOf(false) }
    var customError by remember { mutableStateOf<String?>(null) }

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
                        val selected = settings.themeMode == mode
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
            Text("动态取色", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = dynamicSupported) {
                                viewModel.setDynamicColor(!settings.dynamicColor)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Colorize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 11.dp)) {
                            Text("跟随桌面壁纸", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (dynamicSupported) "读取系统从壁纸提取的主色"
                                else "需要 Android 12 及以上",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                        Switch(
                            checked = settings.dynamicColor && dynamicSupported,
                            onCheckedChange = { viewModel.setDynamicColor(it) },
                            enabled = dynamicSupported
                        )
                    }
                    Text(
                        "开启后主色跟随桌面壁纸，忽略下方主题颜色；关闭后恢复上次选的颜色。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp)
                    )
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
                    // 只有「真的在跟随壁纸」时才不高亮任何预设——当前颜色并不来自这里。
                    // 开着动态取色却取不到壁纸色时界面用的是自选色，必须把正在用的那块标出来。
                    val selected = !followingWallpaper && value == settings.themeColor
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
            Text(
                when {
                    followingWallpaper -> "动态取色已开启，选择任意颜色会自动关闭动态取色。"
                    fallbackHint == ThemeFallbackHint.PRESET ->
                        "当前壁纸取不到可用的颜色，正在沿用上方选中的预置颜色。"
                    else -> "点选即时生效。"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 10.dp)
            )

            Spacer(Modifier.height(20.dp))
            Text("自定义颜色", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 优先级：输入框里合法的新色值 > 当前真正生效的主色。
                // 输到一半（不足 6 位）退回生效色，不会闪成一块灰；动态取色开着时默认显示壁纸色。
                val typed = if (customDirty) parseHexColor(custom)?.let { Color(it) } else null
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = typed ?: effective,
                    border = null
                ) {}
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { input ->
                        // 只留 # 和最多 6 位十六进制：粘贴一堆脏字符时输入框不会失控
                        val cleaned = input.uppercase(Locale.US)
                            .filter { ch -> ch == '#' || ch in '0'..'9' || ch in 'A'..'F' }
                            .let { s -> if (s.startsWith("#")) s else "#$s" }
                            .take(7)
                        custom = cleaned
                        // 动过输入框 → 色块立刻跟着输入走（动态取色开着时也一样）
                        customDirty = true
                        customError = null
                    },
                    singleLine = true,
                    isError = customError != null,
                    placeholder = { Text("#2563EB") },
                    textStyle = TextStyle(fontSize = 14.sp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        // 原来写死 Color.Black：深色模式（尤其动态取色的深色壁纸配色）下边框等于看不见
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier.weight(1f).height(52.dp)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val parsed = parseHexColor(custom)
                        if (parsed == null) {
                            customError = "请输入 6 位十六进制颜色，例如 #2563EB"
                        } else {
                            customError = null
                            viewModel.setThemeColor(parsed)
                            viewModel.showMessage("主题色已更新")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height(40.dp)
                ) { Text("应用", fontSize = 13.sp) }
            }
            customError?.let { message ->
                Text(
                    message,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Text(
                when {
                    followingWallpaper -> "色块默认显示壁纸提取的主色；输入色值可即时预览，点「应用」后生效并关闭动态取色。"
                    fallbackHint == ThemeFallbackHint.CUSTOM ->
                        "当前壁纸取不到可用的颜色，正在沿用自定义颜色；输入色值可即时预览，点「应用」后生效。"
                    else -> "输入色值即时预览，点「应用」后生效。支持 #RRGGBB 与 RRGGBB。"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
        }
    }
}

private fun hexOf(color: Color): String =
    String.format(Locale.US, "#%06X", color.toArgb() and 0xFFFFFF)

private fun parseHexColor(input: String): Long? {
    val cleaned = input.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    val rgb = cleaned.toLongOrNull(16) ?: return null
    return 0xFF000000L or rgb
}
