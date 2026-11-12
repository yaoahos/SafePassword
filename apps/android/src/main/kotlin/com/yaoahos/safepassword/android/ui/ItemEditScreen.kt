package com.yaoahos.safepassword.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaoahos.safepassword.android.security.ClipboardHelper
import com.yaoahos.safepassword.core.model.Field
import com.yaoahos.safepassword.core.model.FieldType
import com.yaoahos.safepassword.core.model.MAX_FIELD_FILE_BYTES
import com.yaoahos.safepassword.core.model.TemplateCatalog
import com.yaoahos.safepassword.core.model.VaultItem
import com.yaoahos.safepassword.core.model.newId
import com.yaoahos.safepassword.core.security.GeneratorOptions
import com.yaoahos.safepassword.core.security.PasswordGenerator
import com.yaoahos.safepassword.core.security.PasswordStrength
import com.yaoahos.safepassword.core.security.Totp
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemEditScreen(
    viewModel: AppViewModel,
    state: AppUiState,
    itemId: String?,
    returnToView: Boolean = false
) {
    val existing = remember(itemId) { itemId?.let { state.activeVault?.item(it) } }
    // 当前所在的标签分类（「全部」时为空）。新建条目、以及「保存并继续新建」
    // 都用它作为默认标签，免得每建一条都要重新点一次标签。
    val defaultLabelId = remember(state.filterLabelId, state.activeVault?.labels) {
        state.filterLabelId?.takeIf { id ->
            state.activeVault?.labels?.any { it.id == id } == true
        }
    }
    var item by remember(itemId) {
        val initial = existing?.deepCopy() ?: run {
            val created = TemplateCatalog.createItem(TemplateCatalog.LOGIN)
            if (defaultLabelId != null) {
                created.copy(labelIds = mutableListOf(defaultLabelId))
            } else {
                created
            }
        }
        mutableStateOf(initial.withUrlBeforeAccount())
    }
    var moreMenu by remember { mutableStateOf(false) }
    var showAddField by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var generatorTarget by remember { mutableStateOf(-1) }
    var showDelete by remember { mutableStateOf(false) }
    var showLabel by remember { mutableStateOf(false) }
    var showNotesMeta by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val validateItem: () -> String? = {
        if (item.title.isBlank()) {
            titleError = true
            "请填写标题后再进行操作"
        } else {
            null
        }
    }

    val saveAndClose = {
        val error = validateItem()
        if (error != null) {
            viewModel.showError(error)
        } else {
            viewModel.saveItem(
                item,
                returnScreen = if (returnToView) Screen.ViewItem(item.id) else null
            )
        }
    }

    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            encodeIcon(context, uri)
                .onSuccess { encoded ->
                    item = item.copy(iconKey = "custom", iconData = encoded)
                }
                .onFailure { error ->
                    viewModel.showError(error.message ?: "无法读取图片")
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                },
                title = { Text("密码", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = saveAndClose) {
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                    if (existing != null) {
                        Box {
                            IconButton(onClick = { moreMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("移入回收站") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                    onClick = {
                                        moreMenu = false
                                        showDelete = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Black,
            focusedContainerColor = MaterialTheme.colorScheme.background,
            unfocusedContainerColor = MaterialTheme.colorScheme.background,
            cursorColor = MaterialTheme.colorScheme.primary
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = item.title,
                    onValueChange = {
                        item = item.copy(title = it)
                        if (it.isNotBlank()) titleError = false
                    },
                    label = { Text("标题") },
                    placeholder = { Text("输入标题") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    singleLine = true,
                    isError = titleError,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                )
                Spacer(Modifier.width(14.dp))
                Box(modifier = Modifier.size(64.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { iconPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        ItemIcon(item = item, size = 56.dp, corner = 16.dp)
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(Color(0xFF111113))
                            .clickable { iconPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "更换图标",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            val notesIndex = if (item.notesPosition < 0) {
                item.fields.size
            } else {
                item.notesPosition.coerceIn(0, item.fields.size)
            }
            var fieldIndex = 0
            repeat(item.fields.size + 1) { position ->
                if (position == notesIndex) {
                    if (item.notes.isNotBlank()) {
                        NotesEditorBox(
                            value = item.notes,
                            label = item.notesLabel,
                            type = item.notesType,
                            colors = fieldColors,
                            canMoveUp = notesIndex > 0,
                            canMoveDown = notesIndex < item.fields.size,
                            onValueChange = { item = item.copy(notes = it) },
                            onMoveUp = {
                                if (notesIndex > 0) item = item.copy(notesPosition = notesIndex - 1)
                            },
                            onMoveDown = {
                                if (notesIndex < item.fields.size) {
                                    item = item.copy(notesPosition = notesIndex + 1)
                                }
                            },
                            onEdit = { showNotesMeta = true },
                            onDelete = {
                                item = item.copy(
                                    notes = "",
                                    notesLabel = "备注",
                                    notesType = FieldType.MULTILINE,
                                    notesPosition = -1
                                )
                            }
                        )
                    }
                } else {
                    val index = fieldIndex
                    val field = item.fields[index]
                    EditorFieldBox(
                        field = field,
                        colors = fieldColors,
                        canMoveUp = index > 0,
                        canMoveDown = index < item.fields.lastIndex,
                        onMoveUp = {
                            if (index > 0) {
                                item = item.copy(
                                    fields = item.fields.toMutableList().also {
                                        val previous = it[index - 1]
                                        it[index - 1] = it[index]
                                        it[index] = previous
                                    }
                                )
                            }
                        },
                        onMoveDown = {
                            if (index < item.fields.lastIndex) {
                                item = item.copy(
                                    fields = item.fields.toMutableList().also {
                                        val next = it[index + 1]
                                        it[index + 1] = it[index]
                                        it[index] = next
                                    }
                                )
                            }
                        },
                        onChange = { updated ->
                            item = item.copy(
                                fields = item.fields.toMutableList().also { it[index] = updated }
                            )
                        },
                        onDelete = {
                            item = item.copy(
                                fields = item.fields.toMutableList().also { it.removeAt(index) },
                                notesPosition = if (item.notesPosition > index) {
                                    item.notesPosition - 1
                                } else {
                                    item.notesPosition
                                }
                            )
                        },
                        onGenerate = {
                            generatorTarget = index
                            showGenerator = true
                        }
                    )
                    fieldIndex++
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showAddField = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("添加字段")
            }

            SectionCard(title = "标签") {
                if (state.activeVault?.labels.isNullOrEmpty()) {
                    Text("还没有标签", style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.activeVault?.labels?.forEach { label ->
                            FilterChip(
                                selected = label.id in item.labelIds,
                                onClick = {
                                    item = item.copy(
                                        labelIds = if (label.id in item.labelIds) {
                                            item.labelIds.toMutableList().also { it.remove(label.id) }
                                        } else {
                                            item.labelIds.toMutableList().also { it.add(label.id) }
                                        }
                                    )
                                },
                                label = { Text(label.name) },
                                leadingIcon = { LabelGlyph(labelColor(label.color), 15.dp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
                // 与上方标签 chip 左对齐：chip 的 leadingIcon 从 8dp 起，
                // 这里把按钮的默认 12dp 内边距压到 8dp，图标再收到 18dp，
                // 视觉上「+」的起点与标签圆点在同一条竖线上。
                TextButton(
                    onClick = { showLabel = true },
                    contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 0.dp, bottom = 0.dp)
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("新建标签")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { item = item.copy(favorite = !item.favorite) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (item.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = if (item.favorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text("收藏此条目")
                Spacer(Modifier.weight(1f))
                Switch(checked = item.favorite, onCheckedChange = { item = item.copy(favorite = it) })
            }

            if (existing == null) {
                OutlinedButton(
                    onClick = {
                        val error = validateItem()
                        if (error != null) {
                            viewModel.showError(error)
                        } else {
                            viewModel.saveItem(item, navigateBack = false)
                            val next = TemplateCatalog.createItem(TemplateCatalog.LOGIN).copy(id = newId())
                            item = if (defaultLabelId != null) {
                                next.copy(labelIds = mutableListOf(defaultLabelId))
                            } else {
                                next
                            }
                            titleError = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 34.dp, vertical = 8.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("添加其它条目", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddField) {
        FieldDialog(
            initialLabel = "",
            initialType = FieldType.TEXT,
            title = "添加字段",
            confirmText = "添加",
            onDismiss = { showAddField = false },
            onConfirm = { label, type ->
                item = item.copy(
                    fields = (item.fields + Field(label = label, type = type, sensitive = type.masked)).toMutableList()
                )
                showAddField = false
            }
        )
    }

    if (showGenerator) {
        GeneratorDialog(
            onDismiss = { showGenerator = false },
            onUse = { password ->
                val index = generatorTarget
                    .takeIf { it in item.fields.indices }
                    ?: item.fields.indexOfFirst { it.type.masked }
                item = if (index >= 0) {
                    item.copy(
                        fields = item.fields.toMutableList().also {
                            it[index] = it[index].copy(value = password)
                        }
                    )
                } else {
                    item.copy(
                        fields = (item.fields + Field(label = "密码", type = FieldType.PASSWORD, value = password, sensitive = true)).toMutableList()
                    )
                }
                generatorTarget = -1
                showGenerator = false
            }
        )
    }

    if (showLabel) {
        LabelEditDialog(
            initial = null,
            onDismiss = { showLabel = false },
            onConfirm = { name, color, sortOrder ->
                viewModel.addLabel(name, color, sortOrder)
                showLabel = false
            }
        )
    }

    if (showNotesMeta) {
        FieldDialog(
            initialLabel = item.notesLabel,
            initialType = item.notesType,
            title = "编辑字段",
            confirmText = "保存",
            onDismiss = { showNotesMeta = false },
            onConfirm = { label, type ->
                item = item.copy(notesLabel = label, notesType = type)
                showNotesMeta = false
            }
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = "移入回收站",
            text = "条目会进入回收站，可稍后恢复。确定继续？",
            confirmText = "移入回收站",
            destructive = true,
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                viewModel.deleteItem(item.id)
                viewModel.navigate(Screen.Vault)
            }
        )
    }

}

@Composable
private fun NotesEditorBox(
    value: String,
    label: String,
    type: FieldType,
    colors: TextFieldColors,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onValueChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = type != FieldType.MULTILINE,
            shape = RoundedCornerShape(12.dp),
            colors = colors,
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
        )
        Column(
            modifier = Modifier.width(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "备注菜单",
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("上移") },
                        enabled = canMoveUp,
                        leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null) },
                        onClick = {
                            menu = false
                            onMoveUp()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("下移") },
                        enabled = canMoveDown,
                        leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null) },
                        onClick = {
                            menu = false
                            onMoveDown()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorFieldBox(
    field: Field,
    colors: TextFieldColors,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onChange: (Field) -> Unit,
    onDelete: () -> Unit,
    onGenerate: () -> Unit
) {
    var menu by remember(field.id) { mutableStateOf(false) }
    var visible by remember(field.id) { mutableStateOf(false) }
    var edit by remember(field.id) { mutableStateOf(false) }
    var scan by remember(field.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            readSmallFile(context, uri)
                .onSuccess { (bytes, name, mime) ->
                    onChange(
                        field.copy(
                            value = Base64.encodeToString(bytes, Base64.NO_WRAP),
                            fileName = name,
                            fileMime = mime
                        )
                    )
                }
                .onFailure { error ->
                    Toast.makeText(context, error.message ?: "无法读取文件", Toast.LENGTH_SHORT).show()
                }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (field.type == FieldType.FILE) {
            FileFieldBox(
                field = field,
                onPick = { filePicker.launch(arrayOf("*/*")) },
                onClear = { onChange(field.copy(value = "", fileName = "", fileMime = "")) },
                modifier = Modifier.weight(1f)
            )
        } else {
            // 动态验证码字段多一个扫码按钮：网站上给的密钥通常就是一张二维码。
            val trailing: (@Composable () -> Unit)? = when {
                field.type == FieldType.TOTP -> {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { scan = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.QrCodeScanner,
                                    contentDescription = "扫码录入密钥",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            IconButton(
                                onClick = { visible = !visible },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (visible) "隐藏密钥" else "显示密钥",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
                field.type.masked -> {
                    {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (visible) "隐藏密码" else "显示密码",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                else -> null
            }
            OutlinedTextField(
                value = field.value,
                onValueChange = { onChange(field.copy(value = it)) },
                label = { Text(if (field.label == "用户名") "账号" else field.label) },
                singleLine = field.type != FieldType.MULTILINE,
                minLines = 1,
                shape = RoundedCornerShape(12.dp),
                colors = colors,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                visualTransformation = if (field.type.masked && !visible) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = when {
                    // 密码/秘密/动态验证码密钥不给输入法联想的机会，避免被记进用户词库。
                    field.type.masked -> KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    )
                    field.type == FieldType.EMAIL -> KeyboardOptions(keyboardType = KeyboardType.Email)
                    field.type == FieldType.PHONE -> KeyboardOptions(keyboardType = KeyboardType.Phone)
                    field.type == FieldType.URL -> KeyboardOptions(keyboardType = KeyboardType.Uri)
                    else -> KeyboardOptions.Default
                },
                trailingIcon = trailing
            )
            if (scan) {
                QrScannerDialog(
                    onDismiss = { scan = false },
                    onDecoded = { text ->
                        scan = false
                        // 回填原文：otpauth:// 链接含 period/digits/algorithm 等参数，
                        // 只存 secret 会丢这些信息。
                        if (Totp.parse(text) != null) {
                            onChange(field.copy(value = text.trim()))
                            Toast.makeText(context, "已填入动态验证码密钥", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                context,
                                "这不是动态验证码密钥，换一个二维码试试",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }
        Column(
            modifier = Modifier.width(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                fieldTypeIcon(field.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "字段菜单",
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("上移") },
                        enabled = canMoveUp,
                        leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null) },
                        onClick = {
                            menu = false
                            onMoveUp()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("下移") },
                        enabled = canMoveDown,
                        leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null) },
                        onClick = {
                            menu = false
                            onMoveDown()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menu = false
                            edit = true
                        }
                    )
                    HorizontalDivider()
                    if (field.value.isNotBlank() && field.type != FieldType.FILE) {
                        DropdownMenuItem(
                            text = { Text("复制") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                menu = false
                                ClipboardHelper.copySensitive(context, field.value)
                            }
                        )
                    }
                    if (field.type.masked) {
                        DropdownMenuItem(
                            text = { Text("生成密码") },
                            leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                            onClick = {
                                menu = false
                                onGenerate()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }

    if (edit) {
        FieldDialog(
            initialLabel = field.label,
            initialType = field.type,
            title = "编辑字段",
            confirmText = "保存",
            onDismiss = { edit = false },
            onConfirm = { label, type ->
                onChange(
                    field.copy(
                        label = label,
                        type = type,
                        sensitive = type.masked,
                        // 从「文件」改成其它类型时丢掉文件内容与元数据，避免 base64 裸露成普通文本。
                        value = if (type == FieldType.FILE) field.value else if (field.type == FieldType.FILE) "" else field.value,
                        fileName = if (type == FieldType.FILE) field.fileName else "",
                        fileMime = if (type == FieldType.FILE) field.fileMime else ""
                    )
                )
                edit = false
            }
        )
    }
}

private fun fieldTypeIcon(type: FieldType): ImageVector = when (type) {
    FieldType.PASSWORD -> Icons.Outlined.VpnKey
    FieldType.SECRET -> Icons.Outlined.Lock
    FieldType.TOTP -> Icons.Outlined.Schedule
    FieldType.URL -> Icons.Outlined.Link
    FieldType.EMAIL -> Icons.Outlined.Email
    FieldType.PHONE -> Icons.Outlined.Phone
    FieldType.MULTILINE -> Icons.Outlined.Notes
    FieldType.FILE -> Icons.Outlined.AttachFile
    else -> Icons.Outlined.Person
}

/** 需要遮挡显示的字段类型：密码、秘密与 TOTP 密钥。 */
private val FieldType.masked: Boolean
    get() = this == FieldType.PASSWORD || this == FieldType.SECRET || this == FieldType.TOTP

@Composable
private fun FieldEditor(
    field: Field,
    onChange: (Field) -> Unit,
    onDelete: () -> Unit,
    onGenerate: () -> Unit
) {
    var visible by remember(field.id) { mutableStateOf(false) }
    val context = LocalContext.current
    OutlinedTextField(
        value = field.value,
        onValueChange = { onChange(field.copy(value = it)) },
        label = { Text(field.label) },
        singleLine = field.type != FieldType.MULTILINE,
        minLines = if (field.type == FieldType.MULTILINE) 3 else 1,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (field.type.masked && !visible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = when {
            field.type.masked -> KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false
            )
            field.type == FieldType.EMAIL -> KeyboardOptions(keyboardType = KeyboardType.Email)
            field.type == FieldType.PHONE -> KeyboardOptions(keyboardType = KeyboardType.Phone)
            field.type == FieldType.URL -> KeyboardOptions(keyboardType = KeyboardType.Uri)
            else -> KeyboardOptions.Default
        },
        trailingIcon = {
            Row {
                if (field.value.isNotBlank()) {
                    IconButton(onClick = { ClipboardHelper.copySensitive(context, field.value) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制")
                    }
                }
                if (field.type.masked) {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = "显示/隐藏"
                        )
                    }
                    IconButton(onClick = onGenerate) {
                        Icon(Icons.Outlined.Add, contentDescription = "生成密码")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除字段")
                }
            }
        }
    )
}

/** 文件字段的选择框：未选时点按选文件，已选时显示文件名与大小。 */
@Composable
private fun FileFieldBox(
    field: Field,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bytes = remember(field.value) { field.fileBytes() }
    Row(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color.Black, RoundedCornerShape(12.dp))
            .clickable(onClick = onPick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.AttachFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (bytes == null) "选择文件" else field.fileName.ifBlank { "文件" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (bytes == null) "单个文件，不超过 50KB"
                else "${formatFileSize(bytes.size)} · 点按更换",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (bytes != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "清除文件",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

private fun formatFileSize(size: Int): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
    else -> "%.1f MB".format(size / (1024.0 * 1024.0))
}

/** 读取并限制 ≤50KB 的单个文件：返回 原始字节、显示名、MIME。 */
private fun readSmallFile(context: Context, uri: Uri): Result<Triple<ByteArray, String, String>> =
    runCatching {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法读取文件")
        if (bytes.size > MAX_FIELD_FILE_BYTES) {
            throw IllegalArgumentException("文件 ${formatFileSize(bytes.size)} 超过 50KB，无法存入字段")
        }
        var name = "文件"
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(index)?.ifBlank { name } ?: name
            }
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        Triple(bytes, name, mime)
    }

@Composable
private fun FieldDialog(
    initialLabel: String,
    initialType: FieldType,
    title: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String, FieldType) -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    var type by remember { mutableStateOf(initialType) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                                ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("字段名") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = safeFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "字段类型",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                // 「多行文本」不再作为可选字段类型（备注已经承担长文本场景，
                // 旧数据里的 MULTILINE 字段仍按原样显示与编辑）；动态验证码排最后。
                listOf(
                    FieldType.TEXT, FieldType.PASSWORD,
                    FieldType.SECRET, FieldType.URL,
                    FieldType.EMAIL, FieldType.PHONE,
                    FieldType.FILE, FieldType.TOTP
                ).chunked(2).forEach { rowTypes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTypes.forEach { option ->
                            TypeChoiceCard(
                                type = option,
                                selected = type == option,
                                onClick = { type = option },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowTypes.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (type == FieldType.FILE) {
                    Text(
                        "文件内容会加密存进密码库，单个文件不超过 50KB。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (type == FieldType.TOTP) {
                    Text(
                        "把网站给出的 otpauth:// 链接或 Base32 密钥（密钥文件码）填进字段值，" +
                            "详情页会实时生成 6 位动态码，用于 GitHub 等双因素登录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(label.trim().ifBlank { "自定义字段" }, type) },
                shape = RoundedCornerShape(50)
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 类型选择卡片：图标 + 名称，选中时主色描边，与图标选择控件同一套视觉语言。 */
@Composable
private fun TypeChoiceCard(
    type: FieldType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(13.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            fieldTypeIcon(type),
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            typeLabel(type),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun typeLabel(type: FieldType): String = when (type) {
    FieldType.TEXT -> "文本"
    FieldType.PASSWORD -> "密码"
    FieldType.SECRET -> "秘密"
    FieldType.TOTP -> "动态验证码"
    FieldType.URL -> "网址"
    FieldType.EMAIL -> "邮箱"
    FieldType.PHONE -> "电话"
    FieldType.MULTILINE -> "多行文本"
    FieldType.FILE -> "文件"
}

@Composable
fun GeneratorDialog(onDismiss: () -> Unit, onUse: (String) -> Unit) {
    var length by remember { mutableStateOf(20f) }
    var lowercase by remember { mutableStateOf(true) }
    var uppercase by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var excludeAmbiguous by remember { mutableStateOf(true) }
    var seed by remember { mutableStateOf(0) }
    val options = GeneratorOptions(
        length = length.toInt(),
        lowercase = lowercase,
        uppercase = uppercase,
        digits = digits,
        symbols = symbols,
        excludeAmbiguous = excludeAmbiguous
    )
    val preview = remember(options, seed) { PasswordGenerator.generate(options) }
    val strength = remember(preview) { PasswordStrength.evaluate(preview) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生成密码") },
        text = {
            Column {
                Text(
                    preview,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                StrengthBar(strength)
                Spacer(Modifier.height(8.dp))
                Text("长度：${length.toInt()}")
                Slider(value = length, onValueChange = { length = it }, valueRange = 8f..64f, steps = 55)
                ToggleRow("小写字母", lowercase) { lowercase = it }
                ToggleRow("大写字母", uppercase) { uppercase = it }
                ToggleRow("数字", digits) { digits = it }
                ToggleRow("特殊符号", symbols) { symbols = it }
                ToggleRow("排除易混淆字符", excludeAmbiguous) { excludeAmbiguous = it }
                TextButton(onClick = { seed++ }) { Text("重新生成") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onUse(preview) }) { Text("使用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
private fun IconChoice(
    key: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(13.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun VaultItem.withUrlBeforeAccount(): VaultItem {
    val urlIndex = fields.indexOfFirst { it.type == FieldType.URL }
    if (urlIndex < 0) return this
    val accountIndex = fields.indexOfFirst { field ->
        field.type == FieldType.TEXT || field.type == FieldType.EMAIL ||
            field.label.contains("账号") || field.label.contains("用户名") ||
            field.label.contains("邮箱")
    }
    if (accountIndex < 0 || urlIndex < accountIndex) return this
    val urlField = fields.removeAt(urlIndex)
    fields.add(accountIndex, urlField)
    return this
}

private fun encodeIcon(context: Context, uri: Uri): Result<String> {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return Result.failure(IllegalArgumentException("无法读取图片"))
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return Result.failure(IllegalArgumentException("图片尺寸无效"))
    }
    if (bounds.outWidth != bounds.outHeight) {
        return Result.failure(IllegalArgumentException("图片必须为正方形（宽高像素一致）"))
    }
    if (bounds.outWidth > 256 || bounds.outHeight > 256) {
        return Result.failure(IllegalArgumentException("图片尺寸不能超过 256×256 像素"))
    }
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: return Result.failure(IllegalArgumentException("无法读取图片"))
    val output = ByteArrayOutputStream()
    if (!original.compress(Bitmap.CompressFormat.PNG, 100, output)) {
        return Result.failure(IllegalArgumentException("无法压缩图片"))
    }
    return Result.success(Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP))
}

private fun VaultItem.deepCopy(): VaultItem = copy(
    fields = fields.map { it.copy(history = it.history.toMutableList()) }.toMutableList(),
    labelIds = labelIds.toMutableList()
)

