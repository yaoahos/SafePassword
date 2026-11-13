package com.yaoahos.safepassword.android.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yaoahos.safepassword.core.security.Totp
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaoahos.safepassword.android.security.ClipboardHelper
import com.yaoahos.safepassword.core.model.Field
import com.yaoahos.safepassword.core.model.FieldType
import com.yaoahos.safepassword.core.model.Label
import com.yaoahos.safepassword.core.model.VaultItem
import com.yaoahos.safepassword.core.security.PasswordStrength
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemViewScreen(viewModel: AppViewModel, state: AppUiState, itemId: String) {
    val item = state.activeVault?.item(itemId)
    if (item == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("条目不存在或已被删除")
        }
        return
    }

    val context = LocalContext.current
    var showPassword by remember(itemId) { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var historyFieldId by remember(itemId) { mutableStateOf<String?>(null) }


    val password = item.primaryPassword
    val username = item.primaryUsername
    val url = item.url.ifBlank {
        item.fields.firstOrNull { it.type == FieldType.URL }?.value.orEmpty()
    }.ifBlank {
        item.fields.firstOrNull {
            it.label in listOf("网址", "地址", "URL", "Website", "网站")
        }?.value.orEmpty()
    }.ifBlank {
        item.fields.firstOrNull {
            it.value.startsWith("http://", ignoreCase = true) ||
                it.value.startsWith("https://", ignoreCase = true)
        }?.value.orEmpty()
    }
    val labels = state.activeVault?.labels.orEmpty()
    val itemLabels = labels.filter { it.id in item.labelIds }
    val detailFields = item.fields

    fun copyValue(value: String) {
        if (value.isBlank()) return
        ClipboardHelper.copySensitive(context, value)
        viewModel.showMessage("已复制")
    }

    fun openUrlValue(raw: String) {
        if (raw.isBlank()) {
            viewModel.showMessage("没有可打开的网址")
            return
        }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(withScheme(raw))))
        }.onFailure {
            viewModel.showMessage("无法打开网址")
        }
    }

    fun openUrl() = openUrlValue(url)

    fun toggleFavorite() {
        viewModel.saveItem(item.copy(favorite = !item.favorite), navigateBack = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {},
                actions = {
                    IconButton(onClick = { toggleFavorite() }) {
                        Icon(
                            if (item.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = "收藏",
                            tint = if (item.favorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showLabels = true }) {
                        Icon(Icons.Outlined.Sell, contentDescription = "标签")
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("编辑") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    moreMenu = false
                                    viewModel.navigate(Screen.EditItem(item.id, fromView = true))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("制作副本") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                onClick = {
                                    moreMenu = false
                                    viewModel.duplicateItem(item.id)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("移入回收站") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = {
                                    moreMenu = false
                                    confirmDelete = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.navigate(Screen.EditItem(item.id, fromView = true)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 2.dp,
                bottom = 84.dp
            )
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 14.dp,
                            shape = RoundedCornerShape(22.dp),
                            clip = false
                        )
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(22.dp)
                        ),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ItemIcon(item = item, size = 56.dp, corner = 15.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            item.title.ifBlank { "未命名" },
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (url.isNotBlank()) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 8.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            )
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    url,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 32.dp)
                                )
                                IconButton(
                                    onClick = { openUrl() },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.OpenInNew,
                                        contentDescription = "打开网址",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (!username.isNullOrBlank()) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 8.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            )
                            Text(
                                username,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!password.isNullOrBlank()) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 8.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (showPassword) password else "•".repeat(password.length.coerceIn(8, 16)),
                                    fontSize = 15.sp,
                                    letterSpacing = if (showPassword) 0.sp else 2.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                IconButton(
                                    onClick = { showPassword = !showPassword },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        if (showPassword) Icons.Outlined.VisibilityOff
                                        else Icons.Outlined.Visibility,
                                        contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (itemLabels.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemLabels.forEach { label ->
                                    Row(
                                        modifier = Modifier
                                            .background(
                                                labelColor(label.color).copy(alpha = 0.12f),
                                                RoundedCornerShape(50)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ColorDot(labelColor(label.color), 7)
                                        Spacer(Modifier.width(5.dp))
                                        Text(label.name, fontSize = 11.sp, color = labelColor(label.color))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            itemsIndexed(detailFields, key = { _, field -> field.id }) { index, field ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    )
                }
                FieldRow(
                    field = field,
                    onCopy = {
                        if (field.type == FieldType.TOTP) {
                            // TOTP 一键复制的是当前动态码，不是密钥本身。
                            val code = Totp.currentCode(field.value)
                            if (code != null) copyValue(code)
                            else viewModel.showError("密钥无效，无法生成动态码")
                        } else {
                            copyValue(field.value)
                        }
                    },
                    onOpenUrl = { openUrlValue(it) },
                    onNotice = { viewModel.showMessage(it) },
                    onOpenHistory = if (field.history.isNotEmpty()) {
                        { historyFieldId = field.id }
                    } else {
                        null
                    }
                )
            }

            if (detailFields.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    )
                }
            }

            if (item.notes.isNotBlank()) {
                item {
                    Text(
                        "备注",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                    Text(item.notes, fontSize = 14.sp, lineHeight = 20.sp)
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 10.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "修改时间：${detailDate(item.updatedAt)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "创建时间：${detailDate(item.createdAt)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showLabels) {
        ItemLabelDialog(
            labels = labels,
            selected = item.labelIds.toSet(),
            onDismiss = { showLabels = false },
            onSave = { ids ->
                showLabels = false
                viewModel.saveItem(
                    item.copy(labelIds = ids.toMutableList()),
                    navigateBack = false
                )
            }
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "移入回收站",
            text = "将“${item.title}”移入回收站？",
            confirmText = "移入",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.deleteItem(item.id)
                viewModel.navigate(Screen.Vault)
            }
        )
    }

    historyFieldId?.let { fieldId ->
        val field = item.fields.firstOrNull { it.id == fieldId }
        if (field == null || field.history.isEmpty()) {
            historyFieldId = null
        } else {
            FieldHistoryDialog(
                field = field,
                onCopy = { copyValue(it) },
                onDelete = { index -> viewModel.deleteFieldHistory(item.id, fieldId, index) },
                onDismiss = { historyFieldId = null }
            )
        }
    }

}

@Composable
private fun FieldRow(
    field: Field,
    onCopy: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onNotice: (String) -> Unit,
    onOpenHistory: (() -> Unit)? = null
) {
    val isPassword = field.type == FieldType.PASSWORD
    val isSecret = field.type == FieldType.SECRET
    val isTotp = field.type == FieldType.TOTP
    // 动态验证码不在遮挡之列：它显示的是实时生成的 6 位码，不是密码，
    // 既不需要眼睛按钮，也不该默认藏起来。
    val masked = isPassword || isSecret
    val isUrl = field.type == FieldType.URL
    val isFile = field.type == FieldType.FILE
    val value = field.value
    var revealed by remember(field.id) { mutableStateOf(false) }
    // TOTP：每秒刷新一次动态码与倒计时。
    var tick by remember(field.id) { mutableStateOf(System.currentTimeMillis()) }
    if (isTotp) {
        LaunchedEffect(field.id) {
            while (true) {
                tick = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }
    val totpParams = remember(isTotp, value) { if (isTotp) Totp.parse(value) else null }
    val totpCode = remember(totpParams, tick) {
        if (totpParams != null) {
            Totp.generate(
                totpParams.secret,
                tick / 1000L / totpParams.period,
                totpParams.digits,
                totpParams.algorithm
            )
        } else {
            null
        }
    }
    val totpRemain = remember(totpParams, tick) {
        if (totpParams != null) Totp.remainingSeconds(tick, totpParams.period) else 0
    }
    val context = LocalContext.current
    val fileBytes = remember(field.value, field.id) { field.fileBytes() }
    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(field.fileMime.ifBlank { "application/octet-stream" })
    ) { uri ->
        val bytes = fileBytes
        if (uri != null && bytes != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }.onFailure {
                onNotice("导出失败：${it.message}")
            }
        }
    }
    val display = when {
        isFile -> {
            val name = field.fileName.ifBlank { "文件" }
            if (fileBytes == null) name
            else "$name · ${formatFileBytes(fileBytes.size)}"
        }
        // 空字段直接留空，不再用一个破折号占位。
        value.isBlank() -> ""
        // 动态验证码直接显示当前 6 位码（中间加空格便于分组阅读）。
        isTotp -> {
            val code = totpCode ?: "密钥无效"
            if (code.length == 6) code.substring(0, 3) + " " + code.substring(3) else code
        }
        masked && !revealed -> "•".repeat(value.length.coerceIn(8, 16))
        else -> value
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!isFile) onCopy() }
            .padding(top = 8.dp, bottom = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                field.label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (isUrl && value.isNotBlank()) {
                IconButton(onClick = { onOpenUrl(field.value) }, modifier = Modifier.size(26.dp)) {
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = "打开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            if (onOpenHistory != null) {
                IconButton(onClick = onOpenHistory, modifier = Modifier.size(26.dp)) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = "历史记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (masked) {
                IconButton(onClick = { revealed = !revealed }, modifier = Modifier.size(26.dp)) {
                    Icon(
                        if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (revealed) "隐藏密码" else "显示密码",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (isFile) {
                if (fileBytes != null) {
                    IconButton(
                        onClick = { exportFile.launch(field.fileName.ifBlank { "文件" }) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = "导出",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                IconButton(onClick = onCopy, modifier = Modifier.size(26.dp)) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "复制",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        Text(
            display,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = if (masked && !revealed) 2.sp else 0.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (value.isBlank() && !isFile) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
        if (isTotp && totpCode != null) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "$totpRemain 秒后更新",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isPassword && value.isNotBlank()) {
            val strength = remember(value) { PasswordStrength.evaluate(value) }
            val strong = strength.score >= 3
            val text = when {
                strength.score >= 3 -> "强的"
                strength.score == 2 -> "一般"
                else -> "弱的"
            }
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(
                        if (strong) Color(0xFFDDF7E5) else Color(0xFFFFE8D6),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (strong) Color(0xFF1D9A57) else Color(0xFFC77700),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (strong) Color(0xFF1D9A57) else Color(0xFFC77700)
                )
            }
        }
    }
}

private fun formatFileBytes(size: Int): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
    else -> "%.1f MB".format(size / (1024.0 * 1024.0))
}

@Composable
private fun ItemLabelDialog(
    labels: List<Label>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var picked by remember(selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标签") },
        text = {
            if (labels.isEmpty()) {
                Text("还没有标签，可在侧边栏的标签管理中新建。")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(labels, key = { it.id }) { label ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    picked = if (label.id in picked) picked - label.id else picked + label.id
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = label.id in picked,
                                onCheckedChange = {
                                    picked = if (label.id in picked) picked - label.id else picked + label.id
                                }
                            )
                            ColorDot(labelColor(label.color), 9)
                            Spacer(Modifier.width(8.dp))
                            Text(label.name, fontSize = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(picked) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun withScheme(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return ""
    return if (trimmed.contains("://")) trimmed else "http://$trimmed"
}

private fun detailDate(millis: Long): String {
    if (millis <= 0) return "—"
    return SimpleDateFormat("yyyy/M/d HH:mm", Locale.CHINA).format(Date(millis))
}

/**
 * 密码 / 秘密字段的历史值列表：可逐个查看、复制、删除，也可整体清空。
 *
 * 旧密码默认仍是掩码，需要点右上角的眼睛才展开，避免在公共场合直接暴露。
 */
@Composable
private fun FieldHistoryDialog(
    field: Field,
    onCopy: (String) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // 每条自己控制显示/隐藏，互不影响。
    var revealedRows by remember(field.id) { mutableStateOf(setOf<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("历史记录")
                Text(
                    "${field.label} · ${field.history.size} 条",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                field.history.forEachIndexed { index, entry ->
                    val rowShown = entry.changedAt in revealedRows
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (rowShown) entry.value
                                else "•".repeat(entry.value.length.coerceIn(8, 16)),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                detailDate(entry.changedAt),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                revealedRows = if (rowShown) revealedRows - entry.changedAt
                                else revealedRows + entry.changedAt
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                if (rowShown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (rowShown) "隐藏这条" else "显示这条",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onCopy(entry.value) }, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.Outlined.ContentCopy,
                                contentDescription = "复制这条",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDelete(index) }, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "删除这条",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (index < field.history.lastIndex) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
