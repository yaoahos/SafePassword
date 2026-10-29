package com.safeincloud.android.ui

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.safeincloud.android.BuildConfig
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeincloud.android.R
import com.safeincloud.core.model.TemplateCatalog
import com.safeincloud.core.model.VaultItem
import com.safeincloud.core.security.StrengthResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun safeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color.Black,
    focusedContainerColor = MaterialTheme.colorScheme.background,
    unfocusedContainerColor = MaterialTheme.colorScheme.background,
    cursorColor = MaterialTheme.colorScheme.primary
)

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visible: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        colors = safeFieldColors(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = if (onToggleVisibility != null) {
            {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (visible) "隐藏" else "显示"
                    )
                }
            }
        } else {
            null
        }
    )
}

@Composable
fun StrengthBar(result: StrengthResult) {
    val color = when (result.score) {
        0 -> MaterialTheme.colorScheme.error
        1 -> Color(0xFFF97316)
        2 -> Color(0xFFEAB308)
        3 -> Color(0xFF22C55E)
        else -> MaterialTheme.colorScheme.primary
    }
    Column {
        LinearProgressIndicator(
            progress = { (result.score + 1) / 5f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${result.label} · ${result.entropyBits.toInt()} 位熵 · 约 ${result.crackTime}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "确定",
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmText,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String = "",
    confirmText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = safeFieldColors()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (value.isNotBlank()) onConfirm(value.trim())
                },
                enabled = value.isNotBlank()
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

fun templateIcon(templateId: String): ImageVector = when (templateId) {
    TemplateCatalog.BANK_CARD -> Icons.Outlined.CreditCard
    TemplateCatalog.EMAIL -> Icons.Outlined.Email
    TemplateCatalog.WIFI -> Icons.Outlined.Wifi
    TemplateCatalog.IDENTITY -> Icons.Outlined.Badge
    TemplateCatalog.NOTE -> Icons.Outlined.Notes
    else -> Icons.Outlined.Lock
}

fun templateColor(templateId: String): Color = when (templateId) {
    TemplateCatalog.BANK_CARD -> Color(0xFF2563EB)
    TemplateCatalog.EMAIL -> Color(0xFF0891B2)
    TemplateCatalog.WIFI -> Color(0xFF7C3AED)
    TemplateCatalog.IDENTITY -> Color(0xFFB45309)
    TemplateCatalog.NOTE -> Color(0xFF475569)
    else -> Color(0xFF0F766E)
}

fun labelColor(value: Int): Color = Color(value)

fun formatDate(millis: Long): String {
    if (millis <= 0) return "—"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0)
}

@Composable
fun ColorDot(color: Color, size: Int = 10) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color, CircleShape)
    )
}

@Composable
fun SafeBottomBar(selected: Screen, onNavigate: (Screen) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        NavigationBarItem(
            selected = selected == Screen.Vault,
            onClick = { onNavigate(Screen.Vault) },
            icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            label = { Text("密码库") },
            colors = itemColors
        )
        NavigationBarItem(
            selected = selected == Screen.Tools,
            onClick = { onNavigate(Screen.Tools) },
            icon = { Icon(Icons.Outlined.Build, contentDescription = null) },
            label = { Text("工具") },
            colors = itemColors
        )
    }
}

@Composable
fun SafeSidebar(
    vaultName: String,
    itemCount: Int,
    screen: Screen,
    favoritesOnly: Boolean,
    trashSelected: Boolean,
    onClose: () -> Unit,
    onAllItems: () -> Unit,
    onFavorites: () -> Unit,
    onTrash: () -> Unit,
    onLabels: () -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit
) {
    val appName = stringResource(R.string.app_name)
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(304.dp)
                .padding(horizontal = 18.dp, vertical = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        vaultName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        "$itemCount 个条目 · 已加密",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SidebarItem(
                icon = Icons.Outlined.Shield,
                label = "全部",
                selected = screen == Screen.Vault && !favoritesOnly && !trashSelected,
                onClick = onAllItems
            )
            SidebarItem(
                icon = Icons.Outlined.StarBorder,
                label = "收藏",
                selected = screen == Screen.Vault && favoritesOnly,
                onClick = onFavorites
            )
            SidebarItem(
                icon = Icons.Outlined.Delete,
                label = "回收站",
                selected = screen == Screen.Trash,
                onClick = onTrash
            )
            SidebarItem(
                icon = Icons.Outlined.Label,
                label = "标签管理",
                selected = screen is Screen.LabelManager,
                onClick = onLabels
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))
            SidebarItem(
                icon = Icons.Outlined.Settings,
                label = "设置",
                selected = screen == Screen.Settings,
                onClick = onSettings
            )
            SidebarItem(
                icon = Icons.Outlined.Lock,
                label = "锁定",
                selected = false,
                onClick = onLock
            )
            Spacer(Modifier.weight(1f))
            Text(
                "$appName ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun VaultSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    searchPasswords: Boolean,
    onSearch: () -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val runSearch = {
        focusManager.clearFocus()
        keyboard?.hide()
        onFocusChanged(false)
        onSearch()
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = runSearch,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(2.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocusChanged(it.isFocused) },
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                if (searchPasswords) "搜索密码、账号、网址" else "搜索标题、账号、网址",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ItemIcon(item: VaultItem, size: Dp = 46.dp, corner: Dp = 13.dp, load: Boolean = true) {
    var bitmap by remember(item.id, item.iconData) {
        mutableStateOf(peekIconData(item.iconData))
    }
    var decodeFinished by remember(item.id, item.iconData) { mutableStateOf(false) }
    LaunchedEffect(item.iconData, load) {
        if (item.iconData.isBlank()) {
            decodeFinished = true
        } else if (load && bitmap == null) {
            bitmap = withContext(Dispatchers.Default) {
                decodeIconData(item.iconData)
            }
            decodeFinished = true
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        return
    }

    // Do not flash a letter/symbol while a custom icon is still being decoded.
    if (item.iconData.isNotBlank() && !decodeFinished) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {}
        return
    }

    val symbolIcon = when (item.iconKey.lowercase()) {
        "web_site", "website", "web", "url" -> Icons.Outlined.Public
        "key", "password" -> Icons.Outlined.VpnKey
        "email", "mail" -> Icons.Outlined.Email
        "note", "notes" -> Icons.Outlined.Notes
        else -> null
    }
    if (symbolIcon != null) {
        val symbolColor = when (item.iconKey.lowercase()) {
            "web_site", "website", "web", "url" -> Color(0xFF2563EB)
            "key", "password" -> Color(0xFFEA580C)
            "email", "mail" -> Color(0xFF0891B2)
            else -> Color(0xFF64748B)
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(symbolColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = symbolIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.52f)
            )
        }
        return
    }

    val fallbackPalette = listOf(
        Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFFDB2777),
        Color(0xFF059669), Color(0xFFEA580C), Color(0xFF0891B2),
        Color(0xFF4F46E5), Color(0xFFB45309)
    )
    val fallbackColor = remember(item.id, item.title) {
        fallbackPalette[Math.floorMod((item.id + item.title).hashCode(), fallbackPalette.size)]
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(fallbackColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            item.title.trim().take(1).uppercase().ifBlank { "?" },
            color = Color.White,
            fontSize = (size.value * 0.43f).sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

private val iconBitmapCache = object : LruCache<String, ImageBitmap>(64) {}

private fun iconCacheKey(raw: String): String = "${raw.length}:${raw.hashCode()}"

private fun peekIconData(raw: String): ImageBitmap? =
    if (raw.isBlank()) null else iconBitmapCache.get(iconCacheKey(raw))

private fun decodeIconData(raw: String): ImageBitmap? {
    if (raw.isBlank()) return null
    val cacheKey = iconCacheKey(raw)
    iconBitmapCache.get(cacheKey)?.let { return it }
    val decoded = runCatching {
        val clean = raw
            .substringAfter("base64,", raw)
            .filterNot { it.isWhitespace() }
        if (clean.isBlank()) {
            null
        } else {
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 128 || bounds.outHeight / sample > 128) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        }
    }.getOrNull()
    if (decoded != null) iconBitmapCache.put(cacheKey, decoded)
    return decoded
}

@Composable
fun LabelGlyph(color: Color, size: Dp = 18.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.06f, h * 0.50f)
            lineTo(w * 0.30f, h * 0.10f)
            lineTo(w * 0.94f, h * 0.10f)
            lineTo(w * 0.94f, h * 0.90f)
            lineTo(w * 0.30f, h * 0.90f)
            close()
        }
        drawPath(path, color)
    }
}
