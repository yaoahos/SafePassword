package com.safeincloud.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val themePresets = listOf(
    0xFF1677FFL, 0xFFFF9500L, 0xFFEF4444L, 0xFFE11D48L, 0xFFDB2777L, 0xFFA855F7L,
    0xFF6366F1L, 0xFF2563EBL, 0xFF0891B2L, 0xFF0D9488L, 0xFF16A34AL,
    0xFF65A30DL, 0xFFCA8A04L, 0xFFEA580CL, 0xFF78350FL, 0xFF475569L,
    0xFF0F172AL, 0xFF334155L, 0xFF7C2D12L, 0xFF166534L, 0xFF581C87L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(viewModel: AppViewModel, state: AppUiState) {
    var custom by remember { mutableStateOf("") }
    val current = state.settings.themeColor

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
                .padding(horizontal = 20.dp)
        ) {
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
                Surface(
                    modifier = Modifier
                        .size(44.dp),
                    shape = CircleShape,
                    color = Color(current),
                    border = null
                ) {}
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.take(7) },
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
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val value = parseHexColor(custom)
                        if (value == null) {
                            viewModel.showError("请输入 #RRGGBB 格式的颜色")
                        } else {
                            viewModel.setThemeColor(value)
                            viewModel.showMessage("主题色已更新")
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("应用") }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "当前：" + String.format(Locale.US, "#%06X", current and 0xFFFFFFL),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun parseHexColor(input: String): Long? {
    val cleaned = input.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    val rgb = cleaned.toLongOrNull(16) ?: return null
    return 0xFF000000L or rgb
}
